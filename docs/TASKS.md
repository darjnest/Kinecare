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
- [ ] Firestore: colección `profesionales` real con datos (hoy no hay
      ningún profesional cargado, ni pantalla que los liste)
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
      en el `NavHost` de `:app`. Pendiente: conectar cada pantalla a
      Firestore/Cloud Functions (favoritos, historial de reservas, datos
      de perfil/previsión/direcciones/pagos del cliente).

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
