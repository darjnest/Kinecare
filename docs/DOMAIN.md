# Dominio — KineCare

Modelos de dominio del negocio (nombres en español, viven en `:core:common`
salvo que se indique lo contrario). Son `data class` inmutables en Kotlin
puro — sin anotaciones de Firestore/Room en `domain`; el mapeo DTO ↔ dominio
ocurre en la capa `data` de cada feature.

## Actores

### Usuario
Identidad base compartida por ambos roles.
- `id: String` (uid de Firebase Auth)
- `nombre: String`
- `email: String`
- `telefono: String?`
- `rol: RolUsuario` (`CLIENTE` | `PROFESIONAL`)
- `fotoUrl: String?`
- `fechaRegistro: Instant`

### Cliente
Extiende `Usuario`. Datos propios del rol cliente.
- `direcciones: List<Direccion>`
- `metodosPago: List<MetodoPago>`
- `favoritos: List<String>` (ids de `Profesional`)

### Profesional
Extiende `Usuario`. Es la entidad más rica del dominio.
- `especialidades: List<String>` (kinesiología, masoterapia, etc.)
- `servicios: List<Servicio>`
- `insignias: List<Insignia>`
- `disponibilidad: List<Disponibilidad>`
- `calificacionPromedio: Double`
- `totalResenas: Int`
- `descripcion: String`
- `estadoVerificacionGeneral: EstadoVerificacion`

## Insignia
El diferenciador de negocio del producto: qué está verificado y qué no.
- `tipo: TipoInsignia` (`IDENTIDAD`, `CREDENCIALES`, `AUTENTICIDAD`,
  `HISTORIAL`)
- `estado: EstadoVerificacion` (`PENDIENTE`, `APROBADO`, `RECHAZADO`,
  `NO_SOLICITADO`)
- `detalle: String?` (ej. "Título de Kinesiólogo verificado con [institución]")
- `fechaActualizacion: Instant`

Cada `Insignia` se resuelve mediante una `SolicitudVerificacion`; el cliente
Android solo lee el `estado` final, nunca calcula ni asume verificación por
su cuenta.

## Servicio
Ofrecido por un `Profesional`.
- `id: String`
- `nombre: String`
- `descripcion: String`
- `modalidad: ModalidadServicio` (`DOMICILIO`, `CONSULTA`, `ONLINE`)
- `duracionMinutos: Int`
- `precio: Long` (CLP, sin decimales)

## Reserva
Núcleo transaccional del marketplace.
- `id: String`
- `clienteId: String`
- `profesionalId: String`
- `servicioId: String`
- `modalidad: ModalidadServicio`
- `fechaHora: Instant`
- `direccion: Direccion?` (requerida si `modalidad == DOMICILIO`)
- `estado: EstadoReserva`
- `pago: Pago`
- `comisionPorcentaje: Double` (ej. 0.10 — fijado por Cloud Function, no editable por el cliente)

### EstadoReserva
`SOLICITADA → CONFIRMADA → EN_CURSO → COMPLETADA` con ramas
`CANCELADA_CLIENTE`, `CANCELADA_PROFESIONAL`, `RECHAZADA`.

## Pago
- `id: String`
- `reservaId: String`
- `monto: Long`
- `metodo: MetodoPago`
- `estado: EstadoPago` (`PENDIENTE`, `AUTORIZADO`, `RECHAZADO`, `REEMBOLSADO`)
- `idTransaccionPasarela: String?`

Resuelto exclusivamente por las Cloud Functions `iniciarPago` / `estadoPago`
(ver [ARCHITECTURE.md](ARCHITECTURE.md#backend-firebase)).

### MetodoPago
- `tipo: TipoMetodoPago` (`TARJETA`, `TRANSFERENCIA`, según pasarela elegida)
- `ultimosDigitos: String?`
- `tokenPasarela: String` (nunca el número de tarjeta completo)

## Resena
- `id: String`
- `reservaId: String`
- `clienteId: String`
- `profesionalId: String`
- `calificacion: Int` (1–5)
- `comentario: String?`
- `fecha: Instant`
- `respuestaProfesional: String?`

## SolicitudVerificacion
- `id: String`
- `profesionalId: String`
- `tipo: TipoInsignia`
- `estado: EstadoVerificacion`
- `proveedorExterno: String?` (ej. "Truora")
- `fechaSolicitud: Instant`
- `fechaResolucion: Instant?`

## Direccion
- `calle: String`
- `numero: String`
- `comuna: String`
- `ciudad: String`
- `lat: Double?`
- `lng: Double?`
- `indicaciones: String?`

## Disponibilidad
- `diaSemana: DayOfWeek`
- `horaInicio: LocalTime`
- `horaFin: LocalTime`
- `activo: Boolean`

## Reglas de negocio clave
1. La comisión del marketplace (ej. 10%) se calcula y aplica **solo** en
   backend (Cloud Function `crearReserva` / `iniciarPago`); el cliente la
   muestra de forma informativa.
2. Un `Profesional` puede recibir reservas solo para servicios y horarios
   donde `Disponibilidad.activo == true`.
3. Las `Insignia` no verificadas (`NO_SOLICITADO` o `RECHAZADO`) deben
   mostrarse de forma visible pero no bloquean necesariamente la reserva —
   es información de confianza para el `Cliente`, no un gate técnico
   (salvo que el negocio decida lo contrario más adelante).
4. Un `Cliente` solo puede dejar `Resena` sobre una `Reserva` en estado
   `COMPLETADA` y de la cual es el `clienteId`.
