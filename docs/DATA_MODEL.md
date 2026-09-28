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
modalidad: "DOMICILIO" | "CONSULTA" | "ONLINE"
fechaHora: timestamp
direccion: Direccion?
estado: "SOLICITADA" | "CONFIRMADA" | "EN_CURSO" | "COMPLETADA" |
        "CANCELADA_CLIENTE" | "CANCELADA_PROFESIONAL" | "RECHAZADA"
pago: PagoRef                        // { id, monto, estado } — detalle en `pagos/{pagoId}`
comisionPorcentaje: number           // escrito solo por Cloud Function
creadoEn: timestamp
actualizadoEn: timestamp
```
Índices compuestos sugeridos: `clienteId` + `estado` + `fechaHora` (desc);
`profesionalId` + `estado` + `fechaHora` (desc) — ya desplegados (Fase 2).
Además `clienteId` (asc) + `fechaHora` (desc) sin filtro de `estado` — lo
usa `ReservaRepository.obtenerPorCliente` (`:core:network`) para "Mis
Citas" del Cliente, que hoy no filtra por estado en la consulta (la pestaña
Próximas/Historial/Canceladas se resuelve en el `ViewModel` sobre la lista
completa); declarado en `firestore.indexes.json` en este cambio pero
**no confirmado como desplegado** — no se verificó contra el proyecto
Firebase real (sin acceso a Firebase MCP en esta sesión).

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
