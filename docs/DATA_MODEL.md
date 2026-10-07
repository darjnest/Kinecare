# Modelo de datos — KineCare

Fuente de verdad: **Firestore**. Room es solo caché local (ver
[ARCHITECTURE.md](ARCHITECTURE.md#persistencia-local)). Este documento
describe las colecciones de Firestore y las entidades Room de caché.

> Los nombres de campo en Firestore van en `camelCase` en español para que
> coincidan 1:1 con las propiedades de los DTO (`data/dto`), que luego se
> mapean a los modelos de [DOMAIN.md](DOMAIN.md).

## Colecciones Firestore

### `usuarios/{usuarioId}`
```
nombre: string
rut: string          // normalizado (sin puntos ni guion), único; identificador de negocio
email: string        // correo de Firebase Auth: sintético (derivado del RUT) si el login es
                     // RUT+contraseña, o el correo real si la cuenta es de Google Sign-In
correoContacto: string?  // correo real de contacto (= correo de Google cuando aplica)
telefono: string?
rol: "CLIENTE" | "PROFESIONAL"
fotoUrl: string?
fechaRegistro: timestamp
```

### `clientes/{usuarioId}` (doc 1:1 con `usuarios`)
```
direcciones: array<Direccion>
metodosPago: array<MetodoPagoRef>   // solo tokens, nunca datos de tarjeta
favoritos: array<string>            // ids de profesionales
```

### `profesionales/{usuarioId}` (doc 1:1 con `usuarios`)
```
tiposAtencion: array<"KINESIOLOGIA" | "MASOTERAPIA">  // lo único por lo que filtra la búsqueda
especialidades: array<string>        // texto libre para mostrar; sin vocabulario fijo
rnpi: string                         // Registro Nacional de Prestadores Individuales de Salud
descripcion: string
calificacionPromedio: number
totalResenas: number
estadoVerificacionGeneral: "PENDIENTE" | "APROBADO" | "RECHAZADO" | "NO_SOLICITADO"
insignias: array<Insignia>           // subdocumento embebido (se lee junto al perfil)
disponibilidad: array<Disponibilidad>  // { diaSemana: "MONDAY".."SUNDAY", horaInicio: "HH:mm",
                                       //   horaFin: "HH:mm", activo: boolean } — el dueno lo
                                       //   reemplaza completo desde el panel profesional
ubicacion: geopoint                  // para búsqueda por cercanía
mercadoPagoConectado: boolean?       // true si vinculó su cuenta de Mercado Pago (puede cobrar);
                                     // lo escribe SOLO la Cloud Function del callback OAuth
                                     // (las Security Rules impiden que el dueño lo toque)
```
**Creación.** `AuthRepositoryImpl` (`:feature:auth`) crea el documento al
registrarse un Profesional (RUT+contraseña o "completar perfil" de Google),
en el **mismo `WriteBatch`** que `usuarios/{uid}`, con el contenido de
`perfilProfesionalInicial()` (`:core:network/firebase`): `tiposAtencion`
elegidos en el formulario (al menos uno), `especialidades`/`insignias`/
`disponibilidad` vacíos, `rnpi`/`descripcion` en `""`,
`calificacionPromedio: 0`, `totalResenas: 0` y
`estadoVerificacionGeneral: "NO_SOLICITADO"`. Antes de esto la app solo
creaba `usuarios/{uid}`: un profesional registrado desde la app no tenía
este documento y nunca aparecía en la búsqueda.

**Búsqueda.** `ProfesionalRepository.buscarPorTipoAtencion` filtra
`tiposAtencion` (array-contains del `name` de `TipoAtencion`) ordenado por
`calificacionPromedio` desc. No filtra `especialidades`: ahí convivían
etiquetas de tipo (`KINESIOLOGIA`), variantes escritas a mano
(`Kinesiologia`, `Kinesiología deportiva`) e ids de categoría
(`kine-deportiva`), y solo el valor exacto matcheaba. Un documento sin
`calificacionPromedio` tampoco aparece (Firestore excluye del `orderBy` los
documentos sin ese campo), por eso el perfil inicial lo escribe en 0.

**Security Rules.** `create`: solo el dueño, solo si `usuarios/{uid}.rol`
queda en `PROFESIONAL` (`getAfter`, por el batch), claves exactamente las
del perfil inicial, `tiposAtencion` no vacío y dentro del enum, y estado
inicial obligatorio (sin insignias, `NO_SOLICITADO`, reputación en 0).
`update`: el dueño no puede tocar `insignias`, `estadoVerificacionGeneral`,
`calificacionPromedio` ni `totalResenas` (estas dos ordenan la búsqueda);
si cambia `tiposAtencion` debe quedar válido. Perfiles legados sin
`tiposAtencion` se pueden seguir actualizando en los demás campos.

Índices compuestos: `tiposAtencion` (array-contains) +
`calificacionPromedio` (desc) — reemplaza al de `especialidades`, que ya
ninguna consulta usa. Futuro: `tiposAtencion` + `ubicacion` (geoquery).

**Migración de datos existentes.** Los perfiles creados antes de este
campo (sembrados o cargados a mano) no tienen `tiposAtencion` y dejan de
aparecer en la búsqueda hasta rellenarlo (derivándolo de lo que diga
`especialidades`: `KINESIOLOGIA`/`Kinesiologia`/`Kinesiología …` →
`KINESIOLOGIA`; `MASOTERAPIA`/`Masoterapia` → `MASOTERAPIA`).

#### Subcolección `profesionales/{id}/servicios/{servicioId}`
```
nombre: string
descripcion: string
modalidad: "DOMICILIO" | "CONSULTA" | "ONLINE"
duracionMinutos: number
precio: number
activo: boolean
```

### `reservas/{reservaId}`
```
clienteId: string
profesionalId: string
servicioId: string
modalidad: "DOMICILIO" | "CONSULTA" | "ONLINE"   // copiada del servicio, no la envía el cliente
fechaHora: timestamp                              // inicio de la cita (instante UTC)
duracionMinutos: number                           // copiada de servicio.duracionMinutos al reservar;
                                                  // necesaria para detectar solapes entre reservas
direccion: Direccion?                             // solo si modalidad == DOMICILIO; null en otro caso
estado: "SOLICITADA" | "CONFIRMADA" | "EN_CURSO" | "COMPLETADA" |
        "CANCELADA_CLIENTE" | "CANCELADA_PROFESIONAL" | "RECHAZADA"
pago: PagoRef                        // { id, monto, estado } — detalle en `pagos/{pagoId}`
comisionPorcentaje: number           // escrito solo por Cloud Function
creadoEn: timestamp
actualizadoEn: timestamp
respondidaEn: timestamp?             // lo escribe responderReserva al aceptar/rechazar;
                                     // ausente mientras la reserva sigue SOLICITADA
```
`pago` lo inicializa `crearReserva` como `{ id: null, monto: servicio.precio,
estado: "PENDIENTE" }`: **`pago.id` es `null` hasta que el cliente paga**:
`iniciarPago` crea el documento en `pagos` y escribe su id aquí, y el webhook
mantiene `pago.estado`. El `monto`
siempre sale del documento del servicio, nunca del cliente.

Estados que **ocupan agenda** (bloquean el horario): `SOLICITADA`,
`CONFIRMADA`, `EN_CURSO`. Una reserva en cualquier otro estado libera su
horario. Las reservas anteriores a `duracionMinutos` (si existieran) se
tratan como de 60 minutos al buscar solapes.

Índices compuestos sugeridos: `clienteId` + `estado` + `fechaHora` (desc);
`profesionalId` + `estado` + `fechaHora` (desc) — ya desplegados (Fase 2).
Además `clienteId` (asc) + `fechaHora` (desc) sin filtro de `estado` — lo
usa `ReservaRepository.obtenerPorCliente` (`:core:network`) para "Mis
Citas" del Cliente, que hoy no filtra por estado en la consulta (la pestaña
Próximas/Historial/Canceladas se resuelve en el `ViewModel` sobre la lista
completa); declarado en `firestore.indexes.json` y **desplegado en QA**
(`kinecare-cl-qa`, verificado con `firebase firestore:indexes -P qa`).

`profesionalId` (asc) + `fechaHora` (asc) — lo usa `crearReserva` para buscar
reservas del profesional en una ventana de tiempo y detectar
`HORARIO_OCUPADO` (igualdad en `profesionalId` + rango en `fechaHora`; el
filtro por `estado` se hace en memoria). También
`ReservaRepository.obtenerPorProfesional` (`:core:network`) para
"Solicitudes de Atención" del panel profesional: todas las reservas del
profesional en orden ascendente; pendientes e historial se separan en el
`ViewModel`. **Desplegado en QA**
(`kinecare-cl-qa`); el Firestore Emulator no exige índices, así que la
consulta todavía no se ha ejercitado contra uno real.

### `bloqueosAgenda/{profesionalId}`
```
profesionalId: string
ultimaReservaId: string
actualizadoEn: timestamp
```
Documento-candado, uno por profesional, que **solo `crearReserva` lee y
escribe** (Admin SDK; las Security Rules deniegan todo acceso al cliente).
No es un dato de negocio: existe para serializar las reservas de un mismo
profesional (ver [Cloud Functions](#cloud-functions) → concurrencia).

### `pagos/{pagoId}`
```
reservaId: string
clienteId: string                // copiado de la reserva
profesionalId: string            // vendedor que recibe el pago
monto: number                    // CLP enteros; copiado de reservas.pago.monto (nunca del cliente)
comision: number                 // CLP; = round(monto × comisionPorcentaje). Es el marketplace_fee
metodo: { tipo: "TARJETA" | "TRANSFERENCIA", ultimosDigitos: string? }?  // null hasta que MP informa el pago
estado: "PENDIENTE" | "AUTORIZADO" | "RECHAZADO" | "REEMBOLSADO"
idTransaccionPasarela: string?   // id del pago en Mercado Pago (payment_id)
preferenceId: string?            // preferencia de Checkout Pro
initPoint: string?               // URL de pago de Mercado Pago (siempre init_point, nunca sandbox)
creadoEn: timestamp
actualizadoEn: timestamp
```
Escrito **solo** por Cloud Functions (`iniciarPago`, `webhookMercadoPago`,
`estadoPago`). El cliente Android y el profesional de la reserva tienen
permiso de lectura únicamente (Security Rules). Un mismo `pagos/{id}` es un
intento de cobro: si Mercado Pago lo rechaza, un nuevo `iniciarPago` crea otro
documento y `reservas.pago.id` pasa a apuntar al nuevo. `external_reference`
de la preferencia es este id.

### `cuentasMercadoPago/{profesionalId}`
```
userId: string                   // user_id de Mercado Pago del vendedor
scope: string
accessTokenCifrado: string       // AES-256-GCM, AAD = profesionalId; formato v1.<iv>.<tag>.<ct>
refreshTokenCifrado: string
expiraEn: timestamp              // vencimiento del access token
version: number                  // compare-and-set del refresh (el refresh rota ambos tokens)
requiereReautorizacion: boolean  // true si el refresh falló: el profesional debe reconectar
conectadaEn / actualizadaEn: timestamp
```
Tokens OAuth del vendedor. **Solo Admin SDK**: las Security Rules deniegan
todo acceso (no hay `match`, default-deny). Nunca se loguean ni salen en una
respuesta. La clave de cifrado es el secreto `MP_TOKEN_ENCRYPTION_KEY`.

### `oauthEstados/{state}`
```
profesionalId: string
creadoEn / expiraEn: timestamp   // vigencia 10 min
```
`state` OAuth (48 hex aleatorios) de un solo uso: lo crea `conectarMercadoPago`
y lo consume (borra) `mercadoPagoOAuthCallback` en una transacción. Solo Admin SDK.
Hay **un solo `state` vigente por profesional**: pedir otra URL borra los anteriores
(así los abandonados no se acumulan), y `firestore.indexes.json` declara una política
TTL sobre `expiraEn` (se aplica con `firebase deploy --only firestore:indexes`).

### `resenas/{resenaId}`
```
reservaId: string
clienteId: string
profesionalId: string
calificacion: number   // 1..5
comentario: string?
fecha: timestamp
respuestaProfesional: string?
```
Índice compuesto: `profesionalId` + `fecha` (desc) — ya declarado en
`firestore.indexes.json`; lo usa `ResenaRepository.obtenerPorProfesional`
(`:core:network`, `orderBy fecha DESC`, `limit 100`).

**Convención de id:** el id del documento es **siempre el `reservaId`** de la
reserva reseñada (`resenas/{reservaId}`). Una reserva admite a lo más una
reseña: un segundo intento de reseñar la misma reserva sería un `update`
sobre un documento existente, que las Security Rules deniegan al cliente —
así se evitan duplicados sin Cloud Functions. Por eso
`ResenaRepository.obtenerPorReserva` es una lectura directa por id (`null`
si no existe).

**Reglas de `create` endurecidas** (`firestore.rules`; **declaradas, no
desplegadas**): además de auth (`clienteId == request.auth.uid`), reserva
`COMPLETADA` y perteneciente al llamador, exigen `resenaId == reservaId`,
`profesionalId` igual al de la reserva, `calificacion` entero 1..5,
`fecha == request.time` (el cliente escribe `FieldValue.serverTimestamp()`),
`comentario` ausente/null o string de hasta 500 caracteres,
`respuestaProfesional` ausente, y claves restringidas a `reservaId`,
`clienteId`, `profesionalId`, `calificacion`, `comentario`, `fecha`.
`update` solo permite al profesional dueño modificar `respuestaProfesional`;
`delete` está denegado. Validadas contra el Firestore Emulator local (no
contra el proyecto Firebase real).

> **`profesionales.calificacionPromedio` y `totalResenas` NO los actualiza
> nada hoy.** El cliente no puede escribirlos (las reglas de `profesionales`
> se lo prohíben al dueño y crear una reseña no toca ese documento), así que seguirán en su valor sembrado aunque se creen reseñas.
> Recalcularlos requiere una Cloud Function (trigger `onCreate` de
> `resenas`), que exige plan Blaze — el mismo bloqueo que Firebase Storage.
> Hasta entonces el promedio y el conteo mostrados en el perfil pueden no
> coincidir con el listado real de reseñas.

### `reportesProblema/{reporteId}`
```
reservaId: string
clienteId: string
profesionalId: string
motivo: "PROFESIONAL_NO_LLEGO" | "ATRASO" | "COBRO_INCORRECTO" |
        "CONDUCTA_INAPROPIADA" | "CALIDAD_ATENCION" | "OTRO"
descripcion: string    // 10..1000 caracteres (sin contar espacios al borde)
estado: "ABIERTO" | "EN_REVISION" | "RESUELTO"
fecha: timestamp
```
Problema que un Cliente reporta sobre una de sus reservas, desde "Mis Citas"
(`:feature:client-panel`, `ReporteProblemaRepository`). Lo escribe el
cliente directo (no es pago ni verificación, así que no requiere Cloud
Function).

**Convención de id:** igual que `resenas`, el id es **siempre el
`reservaId`**: una reserva admite a lo más un reporte, y un segundo intento
sería un `update`, denegado.

**Security Rules** (`firestore.rules`; **desplegadas en QA**):
- `create`: `clienteId == request.auth.uid`, `reporteId == reservaId`, la
  reserva existe y es del llamador, `profesionalId` igual al de la reserva,
  `motivo` dentro del enum, `descripcion` string de 10 a 1000 caracteres
  (`trim()` para el mínimo), `estado == "ABIERTO"`, `fecha ==
  request.time` (server timestamp) y claves exactamente las 7 de arriba.
  Se acepta cualquier `estado` de la reserva: "el profesional no llegó"
  suele quedar en `CONFIRMADA` porque nada la cierra automáticamente.
- `read`: solo el cliente dueño, más `resource == null` para que el cliente
  pueda comprobar si ya reportó una reserva (leer un id que no existe). El
  **profesional no lo lee**: el reporte puede ser sobre él, lo media soporte.
  "Mis Citas" consulta `where clienteId == uid` (igualdad sobre un solo
  campo, sin índice compuesto).
- `update`/`delete`: denegados al cliente. `EN_REVISION`/`RESUELTO` los
  escribe el equipo de soporte desde la consola de Firebase.

Probadas con 29 casos contra el Firestore Emulator local (no contra el
proyecto real). **Limitación:** nadie recibe un aviso cuando llega un
reporte; soporte tiene que revisar la colección a mano. Notificar (correo o
FCM) requiere un trigger `onCreate` → plan Blaze.

### `solicitudesVerificacion/{solicitudId}`
```
profesionalId: string
tipo: "IDENTIDAD" | "CREDENCIALES" | "AUTENTICIDAD" | "HISTORIAL"
estado: "PENDIENTE" | "APROBADO" | "RECHAZADO"
proveedorExterno: string?
fechaSolicitud: timestamp
fechaResolucion: timestamp?
```
Escrito por Cloud Functions (`solicitarVerificacion`, `estadoVerificacion`).
Nunca contiene documentos de identidad ni biometría — esos se suben a
Storage y el proveedor externo los procesa fuera de Firestore.

## Cloud Functions

Código en `functions/` (TypeScript, Node 22, `firebase-functions` v2,
región `us-central1`). Cloud Functions exige plan Blaze: QA
(`kinecare-cl-qa`) ya está en Blaze, con política de limpieza de imágenes
de 1 día en Artifact Registry (`gcf-artifacts`, `us-central1`); producción
(`kinecare-cl`) sigue en Spark y sin funciones.

### `crearReserva` (callable) — **desplegada en QA**, no en producción
Protocolo callable sobre HTTP (lo consume el cliente Android con Retrofit):
`POST https://us-central1-<projectId>.cloudfunctions.net/crearReserva`,
header `Authorization: Bearer <ID token de Firebase>`, cuerpo
`{"data": {...}}`. Éxito: `{"result": {...}}`. Error:
`{"error": {"status": "<CODE>", "message": "...", "details": {"motivo": "<MOTIVO>"}}}`.

Request `data`:
```
profesionalId: string
servicioId: string
fechaHora: string        // ISO-8601 con zona, p. ej. "2026-10-05T13:00:00Z"
direccion?: { calle, numero, comuna, ciudad: string, lat?: number|null,
              lng?: number|null, indicaciones?: string|null } | null
```
`modalidad`, `precio` y `comisionPorcentaje` **no** se envían; si llegan, se
ignoran. La modalidad, la duración y el precio salen de
`profesionales/{id}/servicios/{servicioId}`; la comisión es la constante
`COMISION_PORCENTAJE = 0.10` del servidor. Response `result`:
`{ reservaId: string }`. Escribe `reservas/{reservaId}` en estado
`SOLICITADA` con `pago = { id: null, monto, estado: "PENDIENTE" }`.

| `status` (HTTP) | `motivo` | Cuándo |
|---|---|---|
| `UNAUTHENTICATED` (401) | `SIN_SESION` | Sin sesión |
| `PERMISSION_DENIED` (403) | `ROL_INVALIDO` | `usuarios/{uid}.rol != "CLIENTE"` (o sin documento), o `uid == profesionalId` |
| `INVALID_ARGUMENT` (400) | `DATOS_INVALIDOS` | Payload mal formado: ids vacíos/no texto, `fechaHora` no parseable, tipos incorrectos, calle/numero/comuna/ciudad > 120 caracteres o indicaciones > 300 |
| `INVALID_ARGUMENT` (400) | `DIRECCION_REQUERIDA` | Servicio `DOMICILIO` y `direccion` ausente o con calle/numero/comuna en blanco |
| `NOT_FOUND` (404) | `SERVICIO_NO_DISPONIBLE` | El profesional o el servicio no existen |
| `FAILED_PRECONDITION` (400) | `SERVICIO_NO_DISPONIBLE` | `servicio.activo == false` (o servicio con datos corruptos) |
| `FAILED_PRECONDITION` (400) | `ANTICIPACION_INSUFICIENTE` | `fechaHora` < ahora + 60 min, o > ahora + 60 días |
| `FAILED_PRECONDITION` (400) | `FUERA_DE_HORARIO` | El tramo `[fechaHora, fechaHora + duracionMinutos)` no cabe completo en una `Disponibilidad` con `activo == true` de ese día |
| `ALREADY_EXISTS` (409) | `HORARIO_OCUPADO` | Se solapa con otra reserva del profesional en `SOLICITADA`/`CONFIRMADA`/`EN_CURSO` |

Reglas de detalle:
- **Zona horaria.** `Disponibilidad` está en hora de Chile
  (`America/Santiago`, con horario de verano): `fechaHora` se convierte a esa
  zona (vía `Intl`) para elegir el día de la semana y comparar `HH:mm`
  (también acepta `HH:mm:ss`, como `LocalTime.parse` del cliente: hay datos
  en QA guardados con segundos y, sin esto, la app ofrecía horarios que la
  función rechazaba con `FUERA_DE_HORARIO`). El
  tramo no puede cruzar la medianoche local; terminar exactamente a las 00:00
  solo es válido si `horaFin` es `"24:00"`.
- **Rol** se lee siempre de `usuarios/{uid}`, nunca del payload ni de claims.
- **Concurrencia (`HORARIO_OCUPADO`).** El chequeo y la escritura van en una
  transacción de Firestore (Admin SDK). Dentro de ella se lee primero el
  documento-candado `bloqueosAgenda/{profesionalId}`, luego se consultan las
  reservas con `fechaHora` en `[inicio - 24 h, fin)` (una reserva nunca
  cruza la medianoche, así que dura ≤ 24 h) y se filtran estado y solape en
  memoria; al crear la reserva se reescribe el candado. El candado existe
  porque la documentación de Firestore no garantiza que una consulta de
  rango bloquee inserciones "fantasma" en el rango; con él, dos reservas del
  mismo profesional siempre escriben el mismo documento y se serializan.
  En el emulador la prueba de concurrencia (8 llamadas paralelas al mismo
  tramo → exactamente 1 éxito) también pasa sin el candado, por lo que
  **no se pudo demostrar que el candado sea necesario en Firestore real**;
  se mantiene como defensa a un costo mínimo (1 lectura + 1 escritura por
  reserva; las reservas de un mismo profesional se serializan).
- Si hay contención extrema la transacción reintenta hasta 10 veces; agotado,
  el error sale como `aborted`/`internal` **sin** `motivo` del contrato.

Pruebas (`functions/`): `npm run test:unit` (validación, conversión
`America/Santiago` incluidos cambios de horario, solapes) y
`npm run test:emulator` (levanta el Firestore Emulator y corre además la
integración del handler: todos los `motivo`, camino feliz y reservas
simultáneas). No se probó contra un proyecto Firebase real.

### `responderReserva` (callable) — **desplegada en QA**, no en producción
Mismo protocolo que `crearReserva` (`POST .../responderReserva`). El
profesional dueño acepta o rechaza una reserva en `SOLICITADA`.

Request `data`:
```
reservaId: string
respuesta: "ACEPTAR" | "RECHAZAR"
```
Cualquier otra clave se ignora (el estado nunca lo elige el cliente).
Response `result`: `{ estado: "CONFIRMADA" | "RECHAZADA" }`. Actualiza
`reservas/{reservaId}`: `estado`, `respondidaEn` y `actualizadoEn` (hora
del servidor); el resto del documento no cambia.

| `status` (HTTP) | `motivo` | Cuándo |
|---|---|---|
| `UNAUTHENTICATED` (401) | `SIN_SESION` | Sin sesión |
| `INVALID_ARGUMENT` (400) | `DATOS_INVALIDOS` | `reservaId` vacío/no texto/con `/`/> 128 caracteres, o `respuesta` fuera del enum |
| `PERMISSION_DENIED` (403) | `ROL_INVALIDO` | `usuarios/{uid}.rol != "PROFESIONAL"` (o sin documento) |
| `NOT_FOUND` (404) | `RESERVA_NO_ENCONTRADA` | La reserva no existe **o es de otro profesional** (no se revela que existe) |
| `FAILED_PRECONDITION` (400) | `RESERVA_YA_RESPONDIDA` | `estado != "SOLICITADA"` (ya aceptada/rechazada, cancelada, etc.) |
| `FAILED_PRECONDITION` (400) | `RESERVA_VENCIDA` | `ACEPTAR` con `fechaHora <= ahora` (o sin `fechaHora`). Rechazar una vencida sí se permite |

Reglas de detalle:
- Lectura y escritura en una transacción: dos respuestas simultáneas a la
  misma reserva → una gana y la otra recibe `RESERVA_YA_RESPONDIDA`
  (probado con 6 llamadas paralelas en el emulador).
- No toca `bloqueosAgenda`: `SOLICITADA` y `CONFIRMADA` ocupan agenda por
  igual, y `RECHAZADA` solo la libera.
- **Sin plazo de respuesta.** Una solicitud no respondida no expira sola;
  cuando su hora pasa, el panel profesional la muestra como "Vencida sin
  respuesta" (derivado, no se escribe) y ya no se puede aceptar. Expirarlas
  en el backend requiere una función programada.
- No notifica al cliente (FCM sin configurar): el cliente ve el cambio al
  recargar "Mis Citas".

Pruebas: `test/unit/responderReserva.test.ts` (validación del payload) y
`test/integration/responderReserva.integration.test.ts` (17 casos contra el
Firestore Emulator: camino feliz, cada `motivo`, reserva ajena, concurrencia).
Desplegada con `firebase deploy --only functions:responderReserva -P qa`;
verificado que sin sesión responde 401 `SIN_SESION`.

### Pagos con Mercado Pago (Marketplace + Checkout Pro) — **sin desplegar**
Modelo: cada profesional vincula su cuenta de Mercado Pago por OAuth; el cliente
paga en la página de Mercado Pago (Custom Tabs) y el dinero llega **directo al
profesional**, que cede la comisión (`marketplace_fee`, 10 %) a la plataforma.
Ningún dato de tarjeta pasa por la app ni por el backend. Se paga una reserva
`CONFIRMADA` (después de que el profesional acepta), no antes.

| Función | Tipo | Quién | Qué hace |
|---|---|---|---|
| `conectarMercadoPago` | callable | profesional | Devuelve `{ authorizationUrl }` con un `state` de un solo uso (10 min) |
| `mercadoPagoOAuthCallback` | HTTP GET | navegador (Mercado Pago) | Consume `state`, canjea `code`, guarda tokens cifrados, 302 a `kinecare://mp/conectado` o `kinecare://mp/error?motivo=` (`estado_invalido`, `cancelado`, `sin_codigo`, `pasarela`) |
| `iniciarPago` | callable | cliente | `{ reservaId }` → `{ pagoId, initPoint }` |
| `estadoPago` | callable | cliente o profesional de la reserva | `{ pagoId }` → `{ estado }`; si sigue `PENDIENTE` relee Mercado Pago |
| `webhookMercadoPago` | HTTP POST | Mercado Pago | Valida `x-signature` (HMAC-SHA256), relee el pago con el token del vendedor y lo aplica. 401 firma inválida, 500 falla transitoria (MP reintenta), 200 el resto |
| `retornoPago` | HTTP GET | navegador | `back_urls` (MP exige HTTPS) → 302 a `kinecare://pago/resultado?pagoId=` |

`iniciarPago`: el cliente **solo** envía `reservaId`. Monto = `reservas.pago.monto`,
comisión = `reservas.comisionPorcentaje`, vendedor = `reservas.profesionalId`; se
cobra con el token OAuth del profesional (sin cuenta conectada falla; jamás cae al
token de la plataforma). Un reintento con el pago `PENDIENTE` reutiliza el mismo
`pagos/{id}` y la misma preferencia (`idempotencyKey = pagoId`).

| `status` (HTTP) | `motivo` | Cuándo |
|---|---|---|
| `UNAUTHENTICATED` (401) | `SIN_SESION` | Sin sesión |
| `INVALID_ARGUMENT` (400) | `DATOS_INVALIDOS` | Payload mal formado |
| `PERMISSION_DENIED` (403) | `ROL_INVALIDO` | `iniciarPago`: rol != CLIENTE; `conectarMercadoPago`: rol != PROFESIONAL |
| `NOT_FOUND` (404) | `RESERVA_NO_ENCONTRADA` | La reserva no existe o es de otro cliente |
| `NOT_FOUND` (404) | `PAGO_NO_ENCONTRADO` | `estadoPago`: el pago no existe o no es suyo |
| `FAILED_PRECONDITION` (400) | `RESERVA_NO_PAGABLE` | `estado != "CONFIRMADA"`, o la hora de la cita (`fechaHora`) ya pasó: no se cobra algo que nadie atendió (no hay flujo de reembolso todavía) |
| `FAILED_PRECONDITION` (400) | `PAGO_YA_REALIZADO` | `pago.estado` `AUTORIZADO` o `REEMBOLSADO` |
| `FAILED_PRECONDITION` (400) | `PROFESIONAL_SIN_CUENTA_MP` | El profesional no conectó Mercado Pago, o hay que reautorizar |
| `FAILED_PRECONDITION` (400) | `MONTO_INVALIDO` | Monto no entero/positivo o comisión ≥ monto |
| `UNAVAILABLE` (503) | `PASARELA_NO_DISPONIBLE` | Mercado Pago no respondió |

Reglas de detalle:
- **Estados.** `approved → AUTORIZADO`, `rejected|cancelled → RECHAZADO`,
  `refunded|charged_back → REEMBOLSADO`, el resto `PENDIENTE`. Las
  notificaciones llegan repetidas y desordenadas: `AUTORIZADO` solo avanza a
  `REEMBOLSADO`; `RECHAZADO` puede pasar a `AUTORIZADO` (reintento sobre la misma
  preferencia). Un intento viejo no pisa `reservas.pago.estado` si la reserva ya
  apunta a otro `pagoId`.
- **Defensas del webhook.** Nada de la notificación se toma como verdad: solo
  trae un id y se relee el pago a Mercado Pago. No se autoriza si
  `external_reference != pagoId` o si el monto aprobado difiere del cobrado
  (queda en el log). La URL de notificación lleva `?pagoId=` para saber qué
  vendedor/token usar.
- **Procesar antes de responder.** En Cloud Functions la CPU se congela al
  responder, así que (a diferencia de la guía genérica "responde 200 primero") el
  webhook procesa y luego responde; ante falla transitoria responde 500.
- **Refresh del token.** Vence-pronto (< 5 min) → se renueva con compare-and-set
  sobre `version`. Solo un **400/401** de `/oauth/token` (token revocado o ya rotado)
  marca `requiereReautorizacion` y `profesionales.mercadoPagoConectado = false`; un
  timeout o 5xx es transitorio y responde `PASARELA_NO_DISPONIBLE` **sin desconectar**.
  Si el refresh se rechaza porque otra instancia ya lo rotó (`version` distinta), se
  usa el token que esa instancia guardó.
- **Perfil ausente.** Marcar `mercadoPagoConectado` usa `update`: si
  `profesionales/{id}` no existe la vinculación falla en vez de crear un perfil fantasma.
- **Configuración** (`firebase functions:secrets:set`): `MP_CLIENT_SECRET`,
  `MP_WEBHOOK_SECRET`, `MP_TOKEN_ENCRYPTION_KEY` (32 bytes en base64); parámetro
  `MP_APP_ID` (`functions/.env.<proyecto>`, ver `.env.example`); opcional `MP_AUTH_HOST`
  (por defecto `https://auth.mercadopago.com`, el del SDK oficial; si Chile exige
  `https://auth.mercadopago.cl` se cambia aquí sin tocar código). La *Redirect URI*
  del panel de Mercado Pago debe ser exactamente
  `https://us-central1-<projectId>.cloudfunctions.net/mercadoPagoOAuthCallback`, y el
  webhook (tema `payment`) `…/webhookMercadoPago`. Requiere plan **Blaze**.
- Pruebas: `test/unit/{cifrado,firma,estados,pagos}.test.ts` y
  `test/unit/pasarela.test.ts` (la pasarela real contra un `fetch` simulado: URL de
  autorización, form de `/oauth/token`, payload de la preferencia con
  `marketplace_fee`/`back_urls`/idempotencia) y
  `test/integration/pagos.integration.test.ts` (contra el Firestore Emulator con
  una pasarela falsa, `test/fakePasarela.ts`). **No probado contra Mercado Pago
  real**: falta la prueba con usuarios de prueba (vendedor y comprador).

## Firebase Storage

```
/profesionales/{usuarioId}/foto-perfil.jpg
/profesionales/{usuarioId}/credenciales/{archivo}     // acceso restringido, solo backend/proveedor
```

## Entidades Room (`:core:database`, solo caché offline)

### `BusquedaCacheEntity`
Resultado de búsqueda cacheado para modo offline (TTL corto).
```
id: String (query hash)
resultadosJson: String
timestamp: Long
```

### `FavoritoEntity`
```
usuarioId: String
profesionalId: String
```

### `BorradorReservaEntity`
Único borrador en progreso por usuario, para no perder el flujo de 4 pasos
si la app se cierra.
```
usuarioId: String (PK)
profesionalId: String
servicioId: String?
modalidad: String?
fechaHoraEpoch: Long?
direccionJson: String?
pasoActual: Int
```

## Notas de sincronización
- Las pantallas leen Firestore vía `Flow` (listeners `addSnapshotListener`
  envueltos en `callbackFlow`) para tiempo real donde aplica (estado de
  reserva, notificaciones de nuevas reservas para el profesional).
- Room nunca es la fuente de verdad de `Reserva`/`Pago`/`Insignia`: solo
  acelera la UI mientras Firestore resuelve, y se limpia al invalidar caché.
