# Plan de implementación — KineCare

Estimación total ≈ 940 horas a tiempo completo (~24 semanas). Orden
sugerido por dependencias técnicas y de negocio.

## Fase 1 — Arquitectura base ✅
- [x] Inicializar repositorio git (github.com/darjnest/Kinecare, ramas QA/PRD)
- [ ] `build-logic` con convention plugins — diferido: cada módulo declara
      su propio `build.gradle.kts` por ahora (ver docs/SOLUTION_STRUCTURE.md,
      motivo: AGP 9.4.1 built-in Kotlin todavía es muy nuevo para fijar una
      abstracción encima sin haber visto el patrón estabilizarse)
- [x] Version catalog completo (Hilt, Navigation Compose, Retrofit, OkHttp,
      Kotlinx Serialization, Room, Coroutines, Coil, DataStore,
      security-crypto, JUnit5, MockK, Turbine) — Maps Compose queda para
      cuando `:feature:search` lo necesite de verdad
- [x] Módulos `:core:common`, `:core:network`, `:core:database`,
      `:core:designsystem`
- [x] Módulos `:feature:*` (esqueleto vacío, sin lógica: presentation con
      navigation/view/viewmodel, wireados en el NavHost de `:app`)
- [x] Hilt Application + `MainActivity` con `NavHost` raíz
- [x] Design system: paleta verde salvia / azul petróleo / blanco / gris,
      tema Material 3, componentes base (button, card, label/badge,
      loading, dialog)
- [x] Modelos de dominio en `:core:common` (ver [DOMAIN.md](DOMAIN.md))
- [x] CLAUDE.md + docs de contexto (arquitectura, dominio, datos, tareas)

Verificado con `./gradlew build` (debug+release, lint, unit tests) en
verde para los 13 módulos. Pendiente real: Firebase todavía no está
creado, así que `core:network` usa una `BASE_URL` placeholder y ninguna
feature tiene datos de verdad — eso arranca en la Fase 2.

## Fase 2 — Auth + búsqueda 🔧 *(en progreso)*
- [x] Proyecto Firebase creado (`kinecare-cl`, plan Spark, cuenta
      crasd69@gmail.com) y app Android registrada
      (`com.darjnest.kinecare`, `app/google-services.json`)
- [x] Firestore inicializado con Security Rules reales (pagos/verificación
      denegados desde el cliente, perfiles públicos de solo lectura,
      reseñas solo sobre reservas completadas) + índices compuestos
      (`firestore.rules`, `firestore.indexes.json`) — desplegados
- [x] Firebase Authentication (email/password) habilitado
- [x] Firebase Authentication: Google Sign-In habilitado (`firebase.json`
      `auth.providers.googleSignIn`, SHA-1/SHA-256 del debug keystore
      registrados, `app/google-services.json` regenerado con el Web Client
      ID real)
- [x] `:feature:auth`: `AuthRepository`/`AuthRepositoryImpl` reales contra
      Firebase Auth + Firestore (`usuarios/{uid}`), pantalla de
      login/registro con selección de rol, `AuthViewModel` con estado real
- [x] Login/registro real con Google (Credential Manager +
      `GoogleAuthProvider`); cuentas nuevas sin RUT pasan por una pantalla
      de "completar perfil" (RUT/teléfono/rol) antes de crear `usuarios/{uid}`
- [ ] Registrar SHA-1/SHA-256 del **keystore de release** en Firebase antes
      de publicar (Fase 5) — hoy solo está el del debug keystore local
- [x] `:feature:search`: pantalla inicio con filtros, resultados (UI fiel
      al mockup de producto). Selector Kinesiología/Masoterapia filtra de
      verdad categorías y profesionales destacados (datos de muestra
      locales en el `ViewModel`, agrupados por `TipoAtencion` — sin
      conexión a Firestore todavía, ver ítem siguiente). Ubicación real:
      botón "usar mi ubicación" pide permiso runtime
      (`ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`, primer precedente
      de permisos en tiempo de ejecución del proyecto) y resuelve
      comuna/región con Fused Location Provider + `Geocoder` de Android
      (`UbicacionRepository`/`UbicacionRepositoryImpl`, sin Google Maps
      SDK — ver nota de la Fase 1 sobre Maps Compose)
- [x] Firestore: colección `profesionales` real con datos — 6 profesionales
      de muestra sembrados en el proyecto QA (`kinecare-cl-qa`: 3
      Kinesiología + 3 Masoterapia, con `usuarios/{id}` + `profesionales/{id}`
      + subcolección `servicios`), usando los índices/Security Rules ya
      desplegados. Nuevo campo de dominio `Profesional.rnpi` (Registro
      Nacional de Prestadores Individuales de Salud, documentado en
      [DATA_MODEL.md](DATA_MODEL.md) y [DOMAIN.md](DOMAIN.md)).
      `:feature:search` ya no usa datos de muestra locales para
      `profesionalesDestacados`: `ProfesionalRepository`/
      `ProfesionalRepositoryImpl` (patrón igual a `AuthRepositoryImpl`,
      sin capa DTO) consulta `profesionales` por `especialidades`
      (array-contains) ordenado por `calificacionPromedio`, y
      `SearchViewModel` carga los resultados de forma async al iniciar y al
      cambiar el selector Kinesiología/Masoterapia (`cargandoProfesionales`
      + spinner en `SearchScreen`). `categoriasPara()` (vitrina con
      ícono/color) sigue siendo mock local — es un concepto de presentación
      que no vive en Firestore. Primeros tests de repositorio y de
      ViewModel del proyecto (JUnit5 + MockK + Turbine, precedente de
      convención para el resto de `:feature:*`).
- [ ] Storage: Firebase Storage ya no se puede inicializar en proyectos
      nuevos con plan Spark (Google lo restringió a Blaze). **QA
      (`kinecare-cl-qa`) ya está en Blaze**, así que se puede inicializar
      ahí; producción (`kinecare-cl`) sigue en Spark. Necesario antes de la
      Fase 6 (fotos de perfil, credenciales de verificación)
- [x] Cliente: al iniciar sesión o registrarse (rol `CLIENTE`), el `NavHost`
      de `:app` navega automáticamente a `SearchRoute` y saca `AuthRoute`
      del back stack (`KineCareNavHost.kt`, callback
      `onSesionIniciada` expuesto por `authGraph`)
- [x] Profesional: al iniciar sesión o registrarse (rol `PROFESIONAL`), el
      mismo `NavHost` navega automáticamente a `ProfessionalPanelRoute` y
      saca `AuthRoute` del back stack (`KineCareNavHost.kt`)
- [x] `:feature:client-panel` (módulo nuevo, agrupa la cuenta del rol
      Cliente igual que `:feature:professional-panel` agrupa la del rol
      Profesional): pantallas Mis Citas, Favoritos y Mi Perfil fieles a
      los mockups de producto — cada una con su propio `State`/`Action`/
      `ViewModel` (datos parten vacíos/nulos, sin conexión a Firestore
      todavía) y estado vacío propio cuando no hay datos. Barra de
      navegación inferior extraída a `KineCareBottomNavBar`
      (`:core:designsystem/components/bar/`), compartida entre
      `:feature:search` y `:feature:client-panel` y conectada de verdad
      en el `NavHost` de `:app`.
- [x] `:feature:client-panel` — capa de datos (repositorios) conectada a
      Firestore real, sin tocar los `ViewModel` todavía (eso sigue
      pendiente, ver ítem siguiente). Decisión de arquitectura: como
      `:feature:search` también necesita resolver un profesional por id
      (favoritos) y el documento propio de `usuarios/{uid}`, los
      repositorios que 2+ features comparten se movieron/crearon en
      `:core:common` (interfaces `UsuarioRepository`, `ClienteRepository`,
      `ProfesionalRepository`, `ReservaRepository` + sus enums de error en
      `data/error`) con implementación Firestore en `:core:network`
      (`firebase/repository` + `FirestoreRepositoryModule`) — `:feature:search`
      ya no tiene su propio `ProfesionalRepositoryImpl` (ver
      docs/ARCHITECTURE.md). `UbicacionRepository` de `:feature:search` no
      se movió: no usa Firestore y ninguna otra feature lo necesita todavía.
      Nuevo índice compuesto `clienteId` + `fechaHora` (desc) en
      `firestore.indexes.json` para `ReservaRepository.obtenerPorCliente`
      (documentado en docs/DATA_MODEL.md) — **declarado pero no verificado
      como desplegado** (sin acceso a Firebase MCP en este cambio). Tests
      JUnit5/MockK/Turbine para los 4 repositorios nuevos/movidos en
      `:core:network`, mismo patrón que `ProfesionalRepositoryImplTest`
      original de `:feature:search`.
- [x] `:feature:client-panel` — los 4 `ViewModel` conectados a los
      repositorios de arriba (via `FirebaseAuth.currentUser?.uid`, mismo
      patrón usado por `AuthRepositoryImpl`/`ProfesionalRepositoryImpl`):
      `InformacionPersonalClienteViewModel` carga y actualiza
      `usuarios/{uid}` (`UsuarioRepository.obtenerPorId`/
      `actualizarDatosPersonales`); `GuardarCambios` ahora escribe de verdad
      y solo navega hacia atrás cuando el `ViewModel` confirma éxito
      (`state.guardadoExitoso` + `LaunchedEffect` en el `Root`, mismo patrón
      que `AuthViewModel.usuarioAutenticado`/`AuthScreen` — no hay
      `Flow<Event>` separado en el proyecto todavía); `CambiarFoto` sigue
      no-op (Storage bloqueado, plan Spark). `FavoritosViewModel` resuelve
      `Cliente.favoritos` a `Profesional` real vía
      `ProfesionalRepository.obtenerPorId` por cada id, y `QuitarDeFavoritos`
      actualiza `ClienteRepository.quitarFavorito` con UI optimista
      (revierte si falla). `MisCitasViewModel` carga
      `ReservaRepository.obtenerPorCliente` y categoriza cada `Reserva` por
      `estado` en curso/próximas/historial/canceladas, resolviendo el
      nombre del profesional una vez por id; la colección está vacía en la
      práctica hasta que exista el flujo de reserva de la Fase 4, por lo que
      hoy solo ejercita el estado vacío. `MiPerfilClienteViewModel` conecta
      `resumen` y `direcciones` (`ClienteRepository`/`UsuarioRepository`).
      Decisiones de mapeo notables (dominio sin esos campos, ver
      docs/DOMAIN.md): `verificadoClaveUnica` fijo en `false` (sin dato en
      Firestore); la ciudad del resumen y la dirección "predeterminada" usan
      la primera de `Cliente.direcciones` (sin flag de predeterminada en el
      dominio); `ProfesionalFavorito.universidad`/`lugarAtencion` quedan
      vacíos (sin casa de estudios ni dirección/ubicación legible en
      `Profesional`); `esTerapeutaPrincipal` marca el primer favorito de la
      lista (sin ese flag en el dominio tampoco). **Fuera de alcance de
      Firestore hoy** (no hay colección/campo definido, y no se debe
      inventar uno sin rediseño de producto): `prevision` (previsión de
      salud), `tratamiento` (clínico), `metodoPago`, `facturacion` y
      `ajustes` (avisos WhatsApp, ingreso biométrico) de
      `MiPerfilClienteState` siguen mock/vacíos.
- [x] `:feature:client-panel` — tests JUnit5/MockK/Turbine para los 4
      `ViewModel` de arriba (16 tests en total,
      `./gradlew :feature:client-panel:testDebugUnitTest` en verde),
      mockeando los repositorios de `:core:common` (nunca Firestore
      directo): estado de carga inicial, éxito con datos reales mapeados,
      error del repositorio sin dejar `cargando`/`guardando` colgado, y para
      `FavoritosViewModel` en particular la reversión de la actualización
      optimista cuando `ClienteRepository.quitarFavorito` falla. Se agregó
      `MainDispatcherExtension` propia de este módulo (copia de la de
      `:feature:search`, sin dependencia cruzada entre features) y las
      dependencias de test (`junit-jupiter`, `mockk`, `turbine`,
      `kotlinx-coroutines-test`) al `build.gradle.kts` del módulo, que no
      las tenía todavía.

## Fase 3 — Perfil profesional + reseñas
- [x] `:feature:professional-profile`: perfil público del profesional
      (vista del Cliente) con insignias expandibles, conectado a
      `ProfesionalRepository.obtenerPorId` (`:core:common`, impl Firestore
      en `:core:network`; la feature solo depende de `:core:common` y
      `:core:designsystem`). `ProfessionalProfileRoute(profesionalId)` es la
      primera ruta con argumento del proyecto: el `ViewModel` lo lee del
      `SavedStateHandle` (clave `ARG_PROFESIONAL_ID`, con un test que la
      ata al nombre de la propiedad de la ruta) en vez de `toRoute`, que
      necesita un `Bundle` real y no corre en tests JVM. Estados de
      carga/error/contenido; los tres `ProfesionalError` tienen mensaje
      propio y acción "Reintentar". Entradas: tocar una tarjeta de
      profesional destacado en `:feature:search` y una tarjeta de favorito
      en `:feature:client-panel` (nuevo `FavoritosAction.VerPerfil`),
      ambas por callback `onProfesionalClick` resuelto en el `Root` y
      cableado en el `NavHost` de `:app` (el corazón de favoritos sigue
      funcionando). Decisiones de mapeo: solo se muestran servicios con
      `activo == true`; los cuatro `TipoInsignia` siempre aparecen (los que
      faltan en `Profesional.insignias` van como `NO_SOLICITADO`); si una
      insignia no trae `detalle`, la fila expandida muestra una explicación
      neutra según el estado; la fila de calificación se oculta con
      `totalResenas == 0`; "Acerca de", "Servicios" y "Horario de atención"
      se ocultan si no hay datos; el horario agrupa solo los turnos
      `Disponibilidad.activo` por día (lunes a domingo). Avatar con
      iniciales (Storage bloqueado). Nuevos tokens `KineCareSpacing` en
      `:core:designsystem`. 11 tests JUnit5/MockK/Turbine. **Fuera de
      alcance:** botón "Reservar" (Fase 4), listado de reseñas (siguiente
      ítem), mapa/zonas de atención. **No verificado visualmente** (sin
      emulador/dispositivo disponible en el cambio); compila y pasa
      `:app:assembleDebug`.
- [x] `:feature:reviews`: listado y creación de reseñas
      **Capa de datos.** `ResenaRepository`
      (`:core:common`, errores `ResenaError`: `SIN_INTERNET`, `SIN_PERMISO`,
      `DESCONOCIDO`) con impl Firestore `ResenaRepositoryImpl` en
      `:core:network`, enlazada en `FirestoreRepositoryModule`:
      `obtenerPorProfesional` (`fecha` desc, límite 100, usa el índice
      `profesionalId`+`fecha` ya declarado), `obtenerPorReserva` (lectura
      directa de `resenas/{reservaId}`, `null` si no existe) y `crear`
      (id del documento = `reservaId`, así una segunda reseña es un update
      denegado por las reglas; `fecha` con server timestamp;
      `PERMISSION_DENIED` → `SIN_PERMISO`). 15 tests JUnit5/MockK. Regla
      `create` de `resenas` endurecida en `firestore.rules` (rango 1..5,
      `fecha == request.time`, `comentario` ≤ 500, claves permitidas,
      `profesionalId` = el de la reserva): **desplegada en QA**; probada
      con 22 casos contra el Firestore Emulator local.
      **UI.** `ReviewsRoute(profesionalId)`: listado con encabezado de
      resumen (promedio, total y distribución 5→1 estrellas) **calculado
      localmente** sobre las reseñas cargadas (máx. 100), no con
      `profesionales.calificacionPromedio`, que nada mantiene sincronizado
      (ver limitación). Cada reseña muestra al autor como "Nombre A."
      (`usuarios/{id}` resuelto una vez por cliente distinto vía
      `UsuarioRepository`; "Cliente" si falla; nunca email/RUT/teléfono),
      estrellas, comentario (oculto si es nulo/en blanco), fecha es-CL y, si
      existe, `respuestaProfesional` como bloque indentado. Estados de
      carga/vacío/error (mensaje propio por `ResenaError` + "Reintentar").
      `CrearResenaRoute(reservaId, profesionalId)`: selector accesible de
      1–5 estrellas, comentario opcional (500 caracteres, con contador) y
      "Enviar reseña" deshabilitado hasta elegir calificación y mientras se
      envía; al abrir llama `obtenerPorReserva` y, si ya existe, muestra
      "Ya reseñaste esta atención" de solo lectura. `clienteId` sale de
      `FirebaseAuth.currentUser` (error "Inicia sesión" si es nulo); un
      éxito vuelve atrás (`enviada` + `LaunchedEffect`), un fallo conserva el
      formulario con un mensaje por error (`SIN_PERMISO` = reserva no
      completada, ajena o ya reseñada). Los argumentos de ambas rutas se
      leen del `SavedStateHandle` (`ARG_PROFESIONAL_ID`, `ARG_RESERVA_ID`,
      con test que los ata a los nombres de las propiedades). Puntos de
      entrada, todos por callbacks desde `KineCareNavHost` (sin
      dependencias feature→feature): fila "Ver todas las reseñas (N)" en el
      perfil público (`:feature:professional-profile`, solo si
      `totalResenas > 0`), acción "Dejar reseña" en las citas completadas de
      "Mis Citas" (`:feature:client-panel`; se oculta si
      `ResenaRepository.obtenerPorReserva` ya devuelve una reseña — si esa
      lectura falla se ofrece igual — y la lista se recarga al volver a la
      pantalla; la calificación ya dada reemplaza el `0` provisional) y
      "Ver todas las reseñas" en "Mi perfil profesional"
      (`:feature:professional-panel`, con el uid propio). Nuevos tokens
      `KineCareSpacing` (`barraDistribucion`, `etiquetaDistribucion`).
      35 tests en `:feature:reviews` + los de los módulos tocados.
      **Fuera de alcance:** responder reseñas (el bloque de respuesta solo
      se muestra), editar/borrar reseñas, paginación de más de 100.
      **Limitación conocida:** `profesionales.calificacionPromedio`/
      `totalResenas` no se actualizan al crear una reseña (recalcular
      requiere una Cloud Function → plan Blaze, mismo bloqueo que Storage):
      el encabezado del perfil público y "Mi perfil profesional" siguen
      mostrando los valores guardados, que pueden no coincidir con el
      listado. **No verificado visualmente** (sin emulador/dispositivo
      disponible en el cambio); compila y pasa `:app:assembleDebug`. Hoy la
      colección `reservas` está vacía en la práctica (la reserva es Fase 4),
      así que "Dejar reseña" no aparece con datos reales todavía.

## Fase 4 — Flujo de reserva 🔧 *(en progreso)*
- [x] `:feature:booking`: 4 pasos (modalidad → fecha/hora → dirección →
      revisión). `BookingRoute(profesionalId, servicioId?)`, entradas desde
      el perfil público (`:feature:professional-profile`): botón fijo
      "Reservar hora" (oculto si no hay servicios activos) y "Reservar" en
      cada servicio (llega preseleccionado), por callback `onReservar`
      cableado en `KineCareNavHost`. **Paso 1** modalidad (solo las que
      tienen servicios activos; se autoselecciona si hay una sola, igual que
      el servicio si la modalidad tiene uno). **Paso 2** días/horas: cupos
      generados en `domain/service/GeneradorHorarios.kt` a partir de
      `Disponibilidad.activo` en hora de Chile (`ZonaHorariaChile`, nuevo
      en `:core:common/util`, no la zona del dispositivo), en pasos de
      `duracionMinutos` del servicio, 14 días hacia adelante, mínimo 60 min
      de anticipación (mismas reglas que valida la función). **Paso 3**
      dirección, solo para `DOMICILIO` (los demás servicios tienen 3 pasos):
      direcciones guardadas de `clientes/{uid}` (si fallan, formulario vacío
      sin bloquear) o una nueva; calle/número/comuna obligatorios.
      **Paso 4** revisión con total. Back del sistema y de la barra
      retroceden un paso. Tras confirmar, pantalla "¡Reserva solicitada!"
      con "Ver mis citas" (saca el flujo del back stack) y "Listo". Si la
      función rechaza el horario (`HORARIO_OCUPADO`/`FUERA_DE_HORARIO`/
      `ANTICIPACION_INSUFICIENTE`) vuelve al paso 2 sin ese cupo;
      `DIRECCION_REQUERIDA` vuelve al 3; cada `CrearReservaError` tiene su
      mensaje. Resuelve el pendiente de la Fase 7: los servicios pausados
      se filtran en el perfil y en el flujo, y la función los rechaza.
      **Limitación:** el cliente no ve reservas ajenas (Security Rules), así
      que puede ofrecer un cupo ya tomado; se detecta al confirmar.
      Clock inyectado (`di/BookingModule.kt`) para tests deterministas.
      64 tests (`BookingViewModelTest` 53, `GeneradorHorariosTest` 9 con
      cambio de horario de abril, `BookingRouteTest` 2) + 1 en
      `ProfessionalProfileViewModelTest`. **Verificado en emulador**
      (Pixel 7a, flavor QA, sesión de Cliente existente): pasos 1→4 con
      datos reales de `kinecare-cl-qa`; "Confirmar" llega a
      `us-central1-kinecare-cl-qa.cloudfunctions.net/crearReserva`, recibe
      404 (todavía no desplegada) y muestra el error genérico sin romperse.
      La confirmación exitosa no se pudo ver en vivo (pendiente, ver último
      ítem de esta fase).
- [x] Cloud Function `crearReserva` — **desplegada en QA**
      (`kinecare-cl-qa`, Blaze); producción (`kinecare-cl`) sigue en Spark
      y sin funciones. `functions/` (TypeScript,
      Node 22, `firebase-functions` v2 `onCall`, `us-central1`); contrato,
      motivos de error y documento escrito en
      [DATA_MODEL.md](DATA_MODEL.md#cloud-functions). Precio, modalidad y
      comisión (`COMISION_PORCENTAJE = 0.10`) salen del backend, nunca del
      cliente. Valida rol `CLIENTE`, servicio activo, dirección para
      domicilio, anticipación (60 min – 60 días), que el cupo caiga entero
      en un turno activo en `America/Santiago` y que no se superponga con
      otra reserva activa del profesional (transacción + candado
      `bloqueosAgenda/{profesionalId}`, nuevo en `firestore.rules` con
      todo denegado al cliente). Nuevo campo `reservas.duracionMinutos` e
      índice `profesionalId`+`fechaHora` (desplegado en QA).
      68 tests (`npm run test:emulator`: 38 unitarios + 30 de integración
      contra el Firestore Emulator, incluida una carrera de 8 llamadas al
      mismo cupo → 1 gana). Cliente Android: `ReservaRepository.crear`
      (`:core:network`, Retrofit `CloudFunctionsApi`, 10 tests nuevos).
      Desplegada con `firebase deploy --only functions,firestore -P qa`
      (función + reglas + índices) y política de limpieza de Artifact
      Registry de 1 día. Verificado: sin sesión responde 401 `SIN_SESION`.
      **Limitación:** con contención alta la transacción puede agotar
      reintentos y responder sin `motivo` (el cliente lo muestra como error
      genérico).
- [x] Historial de reservas y confirmación: la confirmación es la pantalla
      final del flujo de reserva y el historial es "Mis Citas" de
      `:feature:client-panel` (Fase 2).
- [x] **Reportar problema** (`:feature:client-panel`). Nueva colección
      `reportesProblema/{reservaId}` (un reporte por reserva, mismo truco
      de id que `resenas`) escrita directo por el cliente, con reglas de
      `create` estrictas (reserva propia, `profesionalId` de la reserva,
      motivo del enum, descripción 10–1000, `estado == ABIERTO`, fecha del
      servidor, claves exactas); el profesional no lo lee y nadie lo puede
      editar desde la app (ver [DATA_MODEL.md](DATA_MODEL.md)). **Reglas
      desplegadas en QA**; probadas con 29 casos contra el
      Firestore Emulator. Repositorio propio de la feature
      (`ReporteProblemaRepository` + impl Firestore en `data/`, binding en
      `di/ClientPanelModule.kt`; no sube a `:core:*` porque solo lo usa esta
      feature). Cada tarjeta de "Mis Citas" (en curso, próximas, historial,
      canceladas) tiene "Reportar un problema" o, si ya existe, el estado
      del reporte ("Reporte recibido / en revisión / resuelto") + "Ver
      reporte"; los reportes se leen con una sola consulta por `clienteId`
      (si falla, se ofrece reportar igual y la pantalla vuelve a comprobar).
      `ReportarProblemaRoute(reservaId, profesionalId)`, navegación interna
      de la feature: motivo (radio), descripción con contador y mínimo,
      errores por tipo, y al enviar muestra "Recibimos tu reporte"; si ya
      había uno lo muestra de solo lectura. 22 tests nuevos
      (`ReporteProblemaRepositoryImplTest` 10, `ReportarProblemaViewModelTest`
      11, `ClientPanelRouteTest` 1) + 4 en `MisCitasViewModelTest`.
      **Fuera de alcance:** adjuntar fotos (Storage bloqueado), avisar a
      soporte al llegar un reporte (trigger → Blaze), reportar desde el
      panel profesional. **No verificado visualmente** (sin reservas reales
      en QA); compila y pasa `:app:assembleDebug` y `testDebugUnitTest`.
- [ ] Revisar "Mis Citas" y "Reportar problema" con reservas reales creadas
      por `crearReserva` en QA (función, reglas e índices ya desplegados
      en `kinecare-cl-qa`; falta la prueba de punta a punta en
      dispositivo).

## Fase 5 — Pago
- [x] Elegir pasarela: **Mercado Pago, Marketplace + Checkout Pro** (OAuth
      por profesional, `marketplace_fee` 10 %, pago de reservas `CONFIRMADA`)
- [x] Cloud Functions `conectarMercadoPago`, `mercadoPagoOAuthCallback`,
      `iniciarPago`, `estadoPago`, `webhookMercadoPago`, `retornoPago`
      (`functions/`, 147 tests incl. emulador; contrato en DATA_MODEL.md).
      **Sin desplegar ni probar contra Mercado Pago real.**
- [ ] Crear la app en el panel de Mercado Pago (Chile), cargar secretos
      (`MP_CLIENT_SECRET`, `MP_WEBHOOK_SECRET`, `MP_TOKEN_ENCRYPTION_KEY`) y
      `MP_APP_ID`, registrar Redirect URI y webhook, subir QA a Blaze y
      desplegar. Probar con usuarios de prueba (vendedor + comprador).
- [x] `:feature:payment` (UI/navegación Android; **no probado contra Mercado
      Pago real ni con una sesión real, solo en emulador sin sesión**):
      pantalla **Pagar** (`PaymentRoute(reservaId, titulo, montoClp)`: total
      en CLP sobre el botón, abre `urlPago` en Custom Tab por un
      `Flow<PaymentEvent>`, un mensaje por cada `IniciarPagoError`),
      **Resultado del pago** (`PagoResultadoRoute(pagoId)`, deep link
      `kinecare://pago/resultado`: consulta siempre `consultarEstado`, nunca el
      deep link; si sigue `PENDIENTE` reintenta 5 veces cada 3 s y luego "Tu
      pago está en proceso") y **Conectar Mercado Pago** del profesional
      (`ConectarMercadoPagoRoute` + `MercadoPagoConectadoRoute`/
      `MercadoPagoErrorRoute`, deep links `kinecare://mp/conectado` y
      `kinecare://mp/error`; dos rutas distintas para que un error sin
      `motivo` no pase por éxito, y "conectada" se confirma leyendo
      `profesionales/{uid}.mercadoPagoConectado`, no el deep link).
      `Profesional.mercadoPagoConectado` nuevo en dominio y en
      `ProfesionalRepositoryImpl`. "Mis Citas" ofrece "Pagar con Mercado Pago"
      en reservas `CONFIRMADA` con pago `PENDIENTE`/`RECHAZADO` e indica
      "Pagada"/"Reembolsada"; el panel profesional muestra "Cobros con
      Mercado Pago" (Conectada/No conectada). `:app`: `MainActivity` es
      `singleTask` con intent-filters `kinecare://pago` y `kinecare://mp`, y
      `KineCareNavHost` entrega `onNewIntent` a Navigation (la librería solo
      procesa el deep link del arranque). Dependencia nueva
      `androidx.browser` (Custom Tabs). Componentes nuevos en
      `:core:designsystem`: `KineCareTopBar`, `KineCareStatusMessage`.
      Pendiente: verificar el flujo completo con sesión y Mercado Pago real.
- [ ] Certificate pinning en llamadas de pago

## Fase 6 — Verificación de identidad
- [ ] Elegir proveedor (Truora / Metamap / Didit)
- [ ] `:feature:verification`
- [ ] Cloud Functions `solicitarVerificacion`, `estadoVerificacion`

## Fase 7 — Panel profesional
- [x] `:feature:professional-panel`: dashboard/home fiel al mockup; resumen
      del día, próxima cita, banner de verificación y accesos de gestión
      parten vacíos/nulos en `ProfessionalPanelViewModel` (cada sección se
      oculta con gracia sin datos) — sin conexión a Firestore todavía
- [x] `:feature:professional-panel`: pantallas de servicios y tarifas,
      disponibilidad/horarios, solicitudes de atención, liquidaciones y
      finanzas, documentos y validación, y mi perfil profesional — fieles a
      los mockups de producto; cada una con su propio `State`/`Action`/
      `ViewModel` (datos parten vacíos/nulos, sin conexión a Firestore
      todavía) y ruta registrada en `professional_panelGraph()`. La
      navegación desde los accesos de gestión y la barra inferior del
      dashboard hacia estas rutas ya está enlazada. Pendiente: conectar a
      Firestore/Cloud Functions las pantallas de solicitudes,
      liquidaciones, documentos y el dashboard (solicitudes y
      liquidaciones dependen de `reservas`/`pagos`, que solo escriben las
      Cloud Functions; documentos depende de Storage, bloqueado en Spark).
- [x] `:feature:professional-panel` — **Servicios y tarifas** conectada a
      Firestore (`profesionales/{uid}/servicios`): nuevo
      `ServicioRepository` (`obtenerPorProfesional`, `actualizarActivo`;
      interfaz en `:core:common`, impl en `:core:network`, 4 tests) y
      `ServiciosYTarifasViewModel` que carga el catálogo real, calcula el
      resumen (servicios activos y tarifa promedio, solo con los activos) y
      persiste el interruptor activo/pausado con UI optimista que revierte
      si falla (5 tests). `Servicio` de dominio gana `activo: Boolean =
      true` (ya existía en Firestore, ver DATA_MODEL.md). Decisiones de
      mapeo: `modalidades` con un solo elemento (el dominio tiene una
      `modalidad`), `reembolsableIsapreFonasa`/`notaInferior` fijos en
      `false`/`null` y `region` vacío (sin esos datos en Firestore). Fuera
      de alcance: agregar/editar un servicio (falta el formulario en los
      mockups). ~~Pendiente a decidir~~ resuelto en la Fase 4:
      `ProfesionalRepository.obtenerPorId` sigue trayendo los pausados,
      pero el perfil público y `:feature:booking` los filtran y
      `crearReserva` los rechaza.
- [x] `:feature:professional-panel` — **Disponibilidad y horarios**
      conectada a Firestore (`profesionales/{uid}.disponibilidad`):
      `ProfesionalRepository.actualizarDisponibilidad` (reemplaza el
      arreglo; 2 tests) y `DisponibilidadYHorariosViewModel` que carga el
      horario real (`obtenerPorId`), deriva los 7 días y los turnos del día
      seleccionado, implementa **Replicar** (copia los turnos del día
      seleccionado a los demás días ya habilitados, sin habilitar
      ninguno nuevo) y **Guardar** (7 tests). Guardar queda bloqueado si la
      carga falló, para no pisar el horario real con una lista vacía; el
      resultado se avisa con un Snackbar. Solo el horario semanal se
      persiste: reservas inmediatas, traslado, radio, comunas y recargo
      siguen siendo estado local de la pantalla (sin campos en Firestore).
      `cupos`/duración por cupo tampoco existen en Firestore: la vista
      oculta esa línea cuando valen 0. Fuera de alcance: editar la hora de
      un turno (falta el selector de hora en los mockups).
- [x] `:feature:professional-panel` — **Mi perfil profesional**
      conectada a Firestore (`profesionales/{uid}`):
      `ProfesionalRepository.actualizarDescripcion` (2 tests) y
      `MiPerfilProfesionalViewModel` que mapea el `Profesional` real a
      identidad (nombre, especialidad principal, RNPI, "credenciales al día"
      = insignia `CREDENCIALES` aprobada), calificación/opiniones,
      biografía y especialidades (7 tests). **Editar biografía** abre un
      diálogo (máx. 500 caracteres), escribe `descripcion` y, si falla,
      deja el diálogo abierto con el borrador. Los datos que el dominio no
      tiene (universidad, año de titulación, entidad de registro,
      habilitación Isapre/Fonasa, atenciones completadas, puntualidad) son
      nulos y la vista oculta esa fila/tarjeta en vez de mostrar un valor
      falso; la insignia de verificado del avatar ahora solo aparece con
      credenciales aprobadas y la nota "Excelente" solo con calificación
      ≥ 4.5. El interruptor de perfil público sigue siendo estado local (no
      hay campo en Firestore). Zonas de atención y reseña destacada quedan
      vacías hasta Fase 4; editar credenciales y previsualizar siguen sin
      destino ("Ver todas las reseñas" ya abre `:feature:reviews`).
- [x] `:feature:professional-panel` — **Solicitudes de atención**
      conectada a reservas reales y con respuesta del profesional. Nueva
      Cloud Function `responderReserva` (`ACEPTAR` → `CONFIRMADA`,
      `RECHAZAR` → `RECHAZADA`; contrato y motivos en
      [DATA_MODEL.md](DATA_MODEL.md#cloud-functions)): solo el profesional
      dueño, solo desde `SOLICITADA`, aceptar exige que la cita no haya
      empezado; transacción para respuestas simultáneas; nuevo campo
      `reservas.respondidaEn`. 22 tests nuevos en `functions/` (5 unitarios
      + 17 de integración contra el Firestore Emulator; total 90 con
      `npm run test:emulator`). Android: `ReservaRepository` gana
      `obtenerPorProfesional` (índice `profesionalId` + `fechaHora` ASC, ya
      desplegado en QA) y `responder` (Retrofit; el manejo de token/errores
      de callable se extrajo a un helper común con `crear`), nuevo
      `ResponderReservaError` y `RespuestaReserva` en `:core:common`.
      `SolicitudesDeAtencionViewModel`: pendientes = `SOLICITADA` futuras
      (la más próxima primero, urgente si empieza en < 24 h, fecha/hora en
      hora de Chile), nombre del paciente desde `usuarios/{clienteId}` y del
      servicio desde el catálogo propio (textos genéricos si fallan),
      honorario neto = monto − comisión; "Historial / Resueltas" lista
      confirmadas, en curso, completadas, rechazadas, canceladas y
      "vencidas sin respuesta" (`SOLICITADA` con hora pasada, derivado), y
      cuenta las completadas de la semana. Aceptar muestra progreso en la
      tarjeta; rechazar pide confirmación; tras responder la tarjeta pasa al
      historial sin recargar y se avisa con un Snackbar; si la reserva ya
      fue respondida/cancelada/venció, avisa y recarga. Calificación y
      verificación del paciente, motivo de consulta, custodia de pago
      (solo con pago `AUTORIZADO`, Fase 5) y plazo de respuesta no tienen
      datos todavía: la tarjeta los oculta. "Mis Citas" del cliente ahora
      distingue "Esperando confirmación del profesional" de "Confirmada".
      El `Clock` inyectado pasó de `:feature:booking` (`BookingModule`,
      eliminado) a `:app` (`di/ClockModule.kt`) porque ahora lo usan dos
      features. 37 tests Android nuevos (`SolicitudesDeAtencionViewModelTest`
      19, `ReservaRepositoryImplResponderTest` 13, `ReservaRepositoryImplTest`
      +2, `MisCitasViewModelTest` +2 aserciones). `responderReserva` **desplegada en QA**
      (sin sesión responde 401 `SIN_SESION`). Bug encontrado al preparar la
      prueba: `crearReserva` solo aceptaba `horaInicio`/`horaFin` como
      `HH:mm` y había disponibilidades en QA guardadas como `HH:mm:ss`, que
      la app sí lee; ahora acepta ambos (2 tests nuevos, redesplegada en
      QA). **Verificado en emulador** (Pixel 7a,
      flavor QA, sesión de Cliente existente): primera reserva confirmada
      en vivo con `crearReserva` (antes solo se había visto el 404) y "Mis
      Citas" mostrando "Esperando confirmación del profesional"; de paso se
      corrigió el encabezado "N confirmadas", que contaba también las
      solicitudes. **Pendiente:** el lado Profesional en dispositivo
      (aceptar/rechazar y ver el cambio en "Mis Citas"): falta una cuenta
      de profesional con Firebase Auth y credenciales conocidas —
      `usuarios/88154428` (Carlos Silva Araneda) es un perfil cargado a
      mano, sin cuenta en Auth, así que nadie puede iniciar sesión como él. **Fuera de alcance:** cancelar
      una cita confirmada (`CANCELADA_PROFESIONAL`/`CANCELADA_CLIENTE`),
      expirar solicitudes sin respuesta (función programada), notificar al
      cliente (FCM) y el contador de pendientes en el dashboard.
- [x] Un profesional registrado desde la app no aparece en la búsqueda:
      `:feature:search` filtra `especialidades` por la etiqueta
      `KINESIOLOGIA`/`MASOTERAPIA` (`TipoAtencion`), que hoy solo tienen los
      perfiles sembrados. Detectado con `profesionales/88154428`, al que se
      le agregó la etiqueta a mano en QA para la prueba. **Causa real:** el
      registro solo creaba `usuarios/{uid}`, nunca `profesionales/{uid}`; y
      en QA `especialidades` mezclaba `KINESIOLOGIA`, `Kinesiologia` y
      `Kinesiología deportiva` (6 perfiles sembrados no aparecían).
      **Arreglo:** campo nuevo `tiposAtencion` (enum `TipoAtencion`, movido a
      `:core:common`), el único que filtra la búsqueda
      (`ProfesionalRepository.buscarPorTipoAtencion`); el registro pide
      "¿Qué atenciones ofreces?" (al menos una) y crea `profesionales/{uid}`
      en el mismo batch que `usuarios/{uid}` (`perfilProfesionalInicial`).
      Security Rules de `profesionales` endurecidas (el `create` exige
      estado inicial y rol `PROFESIONAL`; el dueño ya no puede escribir
      `calificacionPromedio`/`totalResenas`) e índice
      `tiposAtencion` + `calificacionPromedio` en vez del de
      `especialidades`. Detalle en [DATA_MODEL.md](DATA_MODEL.md). 15 tests
      nuevos (`AuthRepositoryImplTest` 4 y `AuthViewModelTest` 5 — primeros
      de `:feature:auth` —, `PerfilProfesionalInicialTest` 3,
      `ProfesionalRepositoryImplTest` +2, `SearchViewModelTest` +1).
- [ ] Desplegar en QA reglas e índice de `tiposAtencion` y rellenar
      `tiposAtencion` en los 13 `profesionales` existentes (derivado de
      `especialidades`); sin eso la búsqueda queda vacía en QA. Falta
      también editar `tiposAtencion` desde el panel profesional.
      **Rellenado hecho en QA:** los 13 perfiles ya tienen `tiposAtencion`
      (8 `KINESIOLOGIA`, 5 `MASOTERAPIA`). **Falta desplegar** reglas e
      índice (`firebase deploy --only firestore -P qa`): hasta entonces la
      consulta de búsqueda falla con `FAILED_PRECONDITION` (índice
      `tiposAtencion` + `calificacionPromedio` inexistente). Al desplegar,
      verificar con una consulta `array-contains` sobre `tiposAtencion`.

## Fase 8 — QA, pulido y publicación
- [ ] Cobertura de tests (JUnit5, MockK, Turbine, Compose UI Testing)
- [ ] GitHub Actions CI
- [ ] R8/ProGuard, revisión de seguridad
- [ ] Publicación en Play Store

## Fuera de alcance (futuro)
- iOS nativo (SwiftUI) sobre el mismo backend Firebase
- Panel web de administración
- Landing page informativa

---
*Este archivo se actualiza a medida que avanza el desarrollo — marcar
casillas conforme se completen tareas, no reescribir el plan salvo cambio
de alcance real.*
