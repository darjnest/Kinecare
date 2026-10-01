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
especialidades: array<string>
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
```
Índices compuestos sugeridos: `especialidades` (array-contains) +
`calificacionPromedio` (desc); `especialidades` + `ubicacion` (geoquery).

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
```
`pago` lo inicializa `crearReserva` como `{ id: null, monto: servicio.precio,
estado: "PENDIENTE" }`: **`pago.id` es `null` hasta la Fase 5**, cuando
`iniciarPago` crea el documento en `pagos` y escribe su id aquí. El `monto`
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
completa); declarado en `firestore.indexes.json` en este cambio pero
**no confirmado como desplegado** — no se verificó contra el proyecto
Firebase real (sin acceso a Firebase MCP en esta sesión).

`profesionalId` (asc) + `fechaHora` (asc) — lo usa `crearReserva` para buscar
reservas del profesional en una ventana de tiempo y detectar
`HORARIO_OCUPADO` (igualdad en `profesionalId` + rango en `fechaHora`; el
filtro por `estado` se hace en memoria). **Declarado en
`firestore.indexes.json`, no desplegado**: el Firestore Emulator no exige
índices, así que la consulta no está probada contra uno real.

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
monto: number
metodo: MetodoPagoRef
estado: "PENDIENTE" | "AUTORIZADO" | "RECHAZADO" | "REEMBOLSADO"
idTransaccionPasarela: string?
creadoEn: timestamp
actualizadoEn: timestamp
```
Escrito **solo** por Cloud Functions (`iniciarPago`, webhook de la
pasarela). El cliente Android tiene permiso de lectura únicamente
(Security Rules).

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
> no lo permiten al dueño de forma segura y crear una reseña no toca ese
> documento), así que seguirán en su valor sembrado aunque se creen reseñas.
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

**Security Rules** (`firestore.rules`; **declaradas, no desplegadas**):
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
región `us-central1`). Ninguna está desplegada todavía: los proyectos están
en plan Spark y Cloud Functions exige Blaze.

### `crearReserva` (callable) — escrita y probada en emulador, **no desplegada**
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
  zona (vía `Intl`) para elegir el día de la semana y comparar `HH:mm`. El
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
