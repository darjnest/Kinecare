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
- [ ] Storage: **bloqueado** — Firebase Storage ya no se puede inicializar
      en proyectos nuevos con plan Spark (Google lo restringió a Blaze).
      Hay que subir a Blaze antes de la Fase 6 (fotos de perfil,
      credenciales de verificación)
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
- [ ] `:feature:professional-profile`: perfil con insignias expandibles
- [ ] `:feature:reviews`: listado y creación de reseñas

## Fase 4 — Flujo de reserva
- [ ] `:feature:booking`: 4 pasos (modalidad → fecha/hora → dirección →
      revisión)
- [ ] Cloud Function `crearReserva`
- [ ] Historial de reservas, confirmación, reportar problema

## Fase 5 — Pago
- [ ] Elegir pasarela (Transbank Webpay Plus / Flow / Mercado Pago)
- [ ] `:feature:payment`
- [ ] Cloud Functions `iniciarPago`, `estadoPago` + webhook
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
      todavía) y ruta registrada en `professional_panelGraph()`. Pendiente:
      conectar cada pantalla a Firestore/Cloud Functions y enlazar la
      navegación desde los accesos de gestión y la barra inferior del
      dashboard hacia estas rutas.

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
