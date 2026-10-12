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
- `rut: String` (normalizado, sin puntos ni guion; identificador de login)
- `email: String` (el correo de Firebase Auth: para login por RUT es un
  correo interno sintético derivado del RUT — ver `RutUtils.emailFirebase`
  en `:core:common`, nunca se muestra al usuario ni se usa fuera del SDK de
  Auth —; para cuentas creadas con Google Sign-In es el correo real de la
  cuenta de Google)
- `correoContacto: String?` (correo real ingresado por el usuario al
  registrarse, solo para contacto/notificaciones — no se usa para Auth)
- `telefono: String?`
- `rol: RolUsuario` (`CLIENTE` | `PROFESIONAL`)
- `fotoUrl: String?`
- `fechaRegistro: Instant`

El login usa **RUT + contraseña** (Firebase Auth exige un correo, así que
se deriva uno sintético y estable a partir del RUT normalizado —
`RutUtils`, en `:core:common`, valida el dígito verificador módulo 11 antes
de intentar autenticar) **o Google Sign-In**. El RUT sigue siendo
obligatorio y único como identificador de negocio (verificación,
facturación) aunque la cuenta se haya creado con Google: si
`GoogleAuthProvider` autentica un `uid` que todavía no tiene documento en
`usuarios/{uid}`, la UI pide RUT, teléfono y rol antes de crearlo
(`AuthRepository.completarRegistroGoogle`), validando que el RUT no esté
ya en uso por otra cuenta.

### Cliente
Extiende `Usuario`. Datos propios del rol cliente.
- `direcciones: List<Direccion>`
- `metodosPago: List<MetodoPago>`
- `favoritos: List<String>` (ids de `Profesional`)

### Profesional
Extiende `Usuario`. Es la entidad más rica del dominio.
- `tiposAtencion: List<TipoAtencion>` (`KINESIOLOGIA`, `MASOTERAPIA`):
  disciplinas que ofrece. Es lo **único** por lo que filtra la búsqueda del
  Cliente, así que se pide al registrarse (al menos una) y el perfil
  profesional se crea junto con la cuenta. `TipoAtencion` vive en
  `:core:common` (lo usan `:feature:auth` y `:feature:search`).
- `especialidades: List<String>` (texto libre para mostrar: "Kinesiología
  deportiva", ids de categoría de vitrina, etc.). No se usa para filtrar;
  la tarjeta de búsqueda (`especialidadLegible()`) ignora las etiquetas de
  tipo que tengan perfiles antiguos.
- `rnpi: String`
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
- `activo: Boolean` (el profesional puede pausar un servicio sin borrarlo; por
  defecto `true`)

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
- `expirada: Boolean` (opcional, por defecto `false`: `true` si `expirarSolicitudes` cerró
  una solicitud sin respuesta; el `estado` queda `RECHAZADA` y la UI la muestra como
  "vencida sin respuesta")

### EstadoReserva
`SOLICITADA → CONFIRMADA → EN_CURSO → COMPLETADA` con ramas
`CANCELADA_CLIENTE`, `CANCELADA_PROFESIONAL`, `RECHAZADA`.

`SOLICITADA → CONFIRMADA | RECHAZADA` lo decide el profesional dueño con
`RespuestaReserva` (`ACEPTAR`, `RECHAZAR`) vía la Cloud Function
`responderReserva`; solo se puede aceptar antes de la hora de la cita. Una
`SOLICITADA` cuya hora ya pasó no cambia de estado en Firestore (no hay
plazo de respuesta todavía), pero el panel profesional la muestra como
vencida. La función programada `expirarSolicitudes` (pendiente de deploy) la
cierra: como `EstadoReserva` no tiene `EXPIRADA`, pasa a `RECHAZADA` con
`expirada = true` (ver DATA_MODEL.md); si el enum incorpora `EXPIRADA` se
cambia ahí.

### Cancelación
El cliente o el profesional dueño cancelan una reserva `SOLICITADA` o
`CONFIRMADA` antes de la hora de la cita (`ReservaRepository.cancelar`, Cloud
Function `cancelarReserva`). Resultado: `ResultadoCancelacion(estado:
EstadoReserva, reembolso: ReembolsoCancelacion?)`, con `ReembolsoCancelacion` =
`NO_APLICA` (sin pago autorizado) | `TOTAL` | `NINGUNO`. Política: cancela el
profesional → reembolso total; el cliente con 24 h o más de anticipación →
total; con menos → sin reembolso automático.

### CupoOcupado
Tramo `[inicio, fin)` de la agenda de un profesional ya tomado por una reserva
activa, sin datos de quién la hizo (`ReservaRepository.obtenerCuposOcupados`).

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
- `respuestaProfesional: String?` (la escribe el profesional dueño una sola vez, 1–500
  caracteres, directo en `resenas/{id}`; no se edita ni se borra)

## ReporteProblema
Vive en `:feature:client-panel` (`domain/`), no en `:core:common`: solo esa
feature lo usa.
- `reservaId: String` (también es el id del documento: un reporte por reserva)
- `clienteId: String`
- `profesionalId: String`
- `motivo: MotivoReporte` (`PROFESIONAL_NO_LLEGO`, `ATRASO`,
  `COBRO_INCORRECTO`, `CONDUCTA_INAPROPIADA`, `CALIDAD_ATENCION`, `OTRO`)
- `descripcion: String` (10–1000 caracteres)
- `estado: EstadoReporte` (`ABIERTO` → `EN_REVISION` → `RESUELTO`; el cliente
  solo crea `ABIERTO`, el resto lo escribe soporte)
- `fecha: Instant`

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
5. Un `Cliente` puede reportar un problema sobre cualquier `Reserva` suya,
   en cualquier estado, una sola vez por reserva. El `Profesional` no ve el
   reporte.
