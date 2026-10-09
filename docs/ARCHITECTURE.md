# Arquitectura — KineCare Android

## Visión general
Cliente Android nativo (Kotlin + Jetpack Compose) para un marketplace de
servicios de kinesiología y masoterapia. Dos roles en una sola app: **Cliente**
(busca, reserva, paga) y **Profesional** (gestiona perfil, servicios,
disponibilidad, reservas, pagos, verificación).

## Patrón arquitectónico
- **MVVM + Clean Architecture**, con capas `presentation → domain ← data`
  dentro de cada módulo.
- `domain` no depende de `data` ni de `presentation`, ni de Android
  framework — solo Kotlin puro + modelos + interfaces de repositorio.
- `data` implementa las interfaces definidas en `domain`.
- `presentation` expone `ViewModel` (Hilt) + estado inmutable (`StateFlow`) +
  composables que solo leen estado y emiten acciones.
- Las features **nunca se dependen entre sí**. Todo lo compartido por 2+
  features vive en `:core:*`.

## Módulos y reglas de dependencia

```
:app
:core:common        (utilidades, Result/DataError, extensiones, modelos de dominio compartidos)
:core:network        (Retrofit + OkHttp + Kotlinx Serialization hacia Cloud Functions,
                       wrapper del SDK de Firebase)
:core:database        (Room: caché offline, favoritos, borrador de reserva)
:core:designsystem    (colores, tipografía, componentes Compose reutilizables, Material 3)

:feature:auth
:feature:search
:feature:professional-profile
:feature:booking
:feature:payment
:feature:verification
:feature:professional-panel
:feature:client-panel
:feature:reviews
```

| Módulo | Puede depender de |
|---|---|
| `:feature:*` | `:core:common`, `:core:network`, `:core:database`, `:core:designsystem` |
| `:core:network` | `:core:common` |
| `:core:database` | `:core:common` |
| `:core:designsystem` | (nada de negocio; solo Compose/Material3) |
| `:app` | todos los módulos (ensambla NavGraph + Hilt) |

`:build-logic` contiene los convention plugins de Gradle (ver
[SOLUTION_STRUCTURE.md](SOLUTION_STRUCTURE.md)) para evitar configuración
duplicada entre módulos.

## Inyección de dependencias
- **Hilt** en todo el proyecto. `@HiltAndroidApp` en la `Application`,
  `@AndroidEntryPoint` en Activities, `@HiltViewModel` en ViewModels.
- Cada módulo expone sus bindings vía `@Module` + `@InstallIn(SingletonComponent::class)`
  (o `ViewModelComponent` cuando aplica).

## Navegación
- **Navigation Compose**, grafo raíz dividido en dos subgrafos según rol:
  `clienteGraph` y `profesionalGraph`, seleccionados tras login según el rol
  del `Usuario` autenticado.
- Rutas tipadas (`@Serializable data class` por destino) definidas junto a
  cada feature; el grafo de cada feature se expone como una función
  `NavGraphBuilder.xxxGraph(...)` que `:app` ensambla.
- Argumentos de navegación: van como propiedades de la ruta
  (`ProfessionalProfileRoute(val profesionalId: String)`) y el `ViewModel`
  los lee del `SavedStateHandle` por nombre de propiedad (constante junto a
  la ruta), no con `toRoute`, para poder probarlo en tests JVM. Navegar
  entre features nunca importa la ruta de otra feature desde una feature:
  el `Root` expone un callback (`onProfesionalClick: (String) -> Unit`) y
  `:app` hace el `navController.navigate(...)`.

### Deep links y Custom Tabs (Mercado Pago)
- El pago y el OAuth del profesional se abren en **Custom Tabs**
  (`androidx.browser`), nunca en un WebView; la URL llega por un
  `Flow<Event>` del `ViewModel` y no se registra en logs. Solo se abre si es
  `https` y su host es de Mercado Pago (`mercadopago.cl|com`,
  `mercadolibre.cl|com` y subdominios; `esUrlDeMercadoPago` parsea la URL y
  rechaza userinfo y otros hosts).
- El retorno es un deep link `kinecare://pago/resultado?pagoId=`,
  `kinecare://mp/conectado` o `kinecare://mp/error?motivo=`.
  `MainActivity` es `singleTask` con sus `intent-filter`, y como Navigation
  Compose solo procesa el deep link del arranque en frío (no `onNewIntent`),
  `KineCareNavHost` entrega los nuevos intents `kinecare://` con
  `navController.navigate(uri)` (conserva el back stack; `handleDeepLink`
  con `NEW_TASK` lo vaciaría). Los parámetros del deep link solo identifican
  el recurso: el estado del pago y de la cuenta vinculada se consulta siempre
  al backend/Firestore.

## Backend: Firebase
Dos proyectos Firebase separados (cuenta crasd69@gmail.com, plan Spark por
ahora — ver [TASKS.md](TASKS.md#fase-2--auth--búsqueda)), cada uno con su
propio Auth/Firestore/Storage/Cloud Functions — aislamiento total, nunca se
comparten usuarios ni datos entre ambos:

- **`kinecare-cl`** — producción (PRD).
- **`kinecare-cl-qa`** — QA, para pruebas manuales/datos de ejemplo sin tocar
  datos reales.

La app Android usa **product flavors** de Gradle (`flavorDimensions +=
"environment"`, definidos en `app/build.gradle.kts`) para elegir el backend:
- `qa` → `applicationId` con sufijo `.qa` (`com.darjnest.kinecare.qa`),
  `google-services.json` propio en `app/src/qa/`. Al tener un
  `applicationId` distinto se puede instalar **junto** a la build de
  producción en el mismo dispositivo para comparar.
- `prod` → `applicationId` `com.darjnest.kinecare`,
  `google-services.json` propio en `app/src/prod/`.

Cada flavor registra su propia app Android en Firebase (con el mismo
certificado SHA-1 de debug) y su propio cliente OAuth para Google Sign-In.
`.firebaserc` define los alias `qa` y `prod`/`default` para apuntar el CLI
de Firebase (`firebase use qa` / `firebase use prod`) al proyecto correcto.

- **Firestore**: base de datos principal (usuarios, profesionales, servicios,
  reservas, reseñas). Security Rules reales desplegadas (`firestore.rules`).
- **Firebase Authentication**: login email/password (RUT sintético) y Google
  Sign-In, ambos habilitados. Google Sign-In usa Credential Manager
  (`androidx.credentials` + `googleid`) en el cliente; el ID token se
  intercambia por credencial de Firebase con `GoogleAuthProvider`. Una cuenta
  de Google sin RUT/teléfono/rol todavía no tiene `usuarios/{uid}`: la UI pide
  esos datos antes de crear el documento (ver `AuthViewModel`/`AuthScreen` en
  `:feature:auth`).
- **Cloud Functions**: toda la lógica sensible — pagos, cálculo de comisión,
  verificación de identidad. El cliente Android **nunca** resuelve estos
  estados localmente, solo los lee tras la respuesta de la función. El código
  vive en `functions/` (TypeScript, Node 22, `firebase-functions` v2,
  región `us-central1`; `firebase.json` declara `functions` y `emulators`).
  Las funciones son *callable* y el cliente las invoca por HTTP con Retrofit
  (`POST .../<nombre>`, `Authorization: Bearer <ID token>`, cuerpo
  `{"data": ...}`) en vez de usar el SDK `firebase-functions`. Hoy existen
  `crearReserva`, `responderReserva` y, para pagos con Mercado Pago,
  `conectarMercadoPago`, `iniciarPago`, `estadoPago` y los HTTP
  `mercadoPagoOAuthCallback`, `webhookMercadoPago`, `retornoPago` (contratos en
  [DATA_MODEL.md](DATA_MODEL.md#cloud-functions)), probadas contra el
  Firestore Emulator; desplegadas solo en QA (producción sigue en Spark,
  ver DATA_MODEL.md). Lado Android: interfaz
  Retrofit `CloudFunctionsApi` + DTOs del sobre callable en
  `:core:network` (`functions/`); la URL base se arma con el `projectId` de
  `google-services.json` del flavor (`https://us-central1-<projectId>.cloudfunctions.net/`),
  así QA nunca llama a las funciones de producción. El `motivo` de cada
  `HttpsError` se mapea 1:1 a un enum de error de `:core:common`
  (`CrearReservaError`, `ResponderReservaError`).
- **Cloud Messaging**: notificaciones, tópicos separados por reserva y por rol.
  Sin configurar todavía.
- **Storage**: fotos de perfil y credenciales de profesionales. **Bloqueado
  en plan Spark** — Google ya no permite inicializar Storage en proyectos
  nuevos sin plan Blaze. QA (Blaze) ya tiene `storage.rules` en el repo; falta
  crear el bucket desde la consola. Producción debe subir a Blaze antes de la
  Fase 6.
- Acceso a Firestore/Storage/Auth vía SDK directo; acceso a Cloud Functions
  HTTP vía Retrofit (`:core:network`).
- **Llamadas a Cloud Functions `onCall`**: el helper público
  `llamarCallable` (`:core:network`, `functions/CallableCall.kt`) agrega el ID
  token, mapea `IOException`/`FirebaseNetworkException` a `SIN_INTERNET` y
  parsea el cuerpo de error; lo usan `ReservaRepositoryImpl` y
  `PagoRepositoryImpl` (cada uno traduce el error a su propio enum).
- **Repositorios de Firestore compartidos por 2+ features**: interfaz en
  `:core:common` (`data/repository`, junto a su enum de error en
  `data/error`) + implementación con el SDK de Firebase en `:core:network`
  (`firebase/repository`), bindeados en `FirestoreRepositoryModule`
  (`firebase/di`). Primer precedente: `UsuarioRepository` (`usuarios/{uid}`),
  `ClienteRepository` (`clientes/{uid}`: direcciones/favoritos),
  `ProfesionalRepository` (`profesionales/{uid}`, movido desde
  `:feature:search` cuando `:feature:client-panel` también necesitó
  resolver un profesional por id) y `ReservaRepository` (lectura de
  `reservas` por cliente). Un repositorio que solo usa **una** feature
  (ej. `UbicacionRepository` de `:feature:search`, que no toca Firestore, o
  `ReporteProblemaRepository` de `:feature:client-panel`, que sí: interfaz
  en `data/repository`, impl Firestore en `data/repository_impl` y binding
  en el `di/` de la feature, igual que `AuthRepository`; o `PagoRepository`
  de `:feature:payment`, que habla con Cloud Functions: sus errores e
  `IntentoPago` van en `domain/`, y de `:core:network` solo usa
  `CloudFunctionsApi`, sus DTOs y el helper `llamarCallable`) se queda dentro de
  esa feature — no todo repositorio sube a `:core:*`,
  solo el que 2+ features necesitan de verdad (sin abstracciones para
  casos hipotéticos que no existen hoy).

## Seguridad
- Pagos y cambios de estado de verificación se resuelven **siempre** en una
  Cloud Function, nunca en el cliente.
- No se persiste biometría ni documentos de identidad en el dispositivo,
  solo el resultado (`SolicitudVerificacion.estado`).
- **Certificate pinning** (OkHttp `CertificatePinner`) en el cliente que llama a
  las Cloud Functions (`:core:network`, `security/CertificatePins.kt`): pagos,
  verificación de identidad y reservas comparten ese cliente y host
  (`*.cloudfunctions.net`, QA y producción).
  - **Qué se pinea:** las cuatro raíces de Google Trust Services (R1 y R2 RSA,
    R3 y R4 ECC; vencen en 2036), no el certificado del servidor ni un
    intermedio. El hoja es un certificado compartido de Google que se renueva
    cada ~3 meses y Google rota los intermedios (WR1..WR5, WE1..WE4) sin avisar.
    Las cuatro raíces son además el respaldo mutuo: un pin único sin respaldo
    bloquea la app en una rotación. Hoy coincide la raíz R1 (el servidor envía
    la copia con firma cruzada de GlobalSign, que comparte su clave).
  - **Qué no cubre:** Firestore, Auth y el resto del SDK de Firebase (usan su
    propio stack, no este cliente), ni el Custom Tab de Mercado Pago o de Didit
    (es el navegador del sistema). El tráfico con Mercado Pago y Didit sale del
    backend, no de la app.
  - **Riesgo operativo:** un pin que deje de coincidir bloquea a **todas** las
    versiones instaladas hasta que se actualicen. Por eso
    `scripts/verificar-pins.sh` compara los pins del código con la cadena real
    que sirve Google y corre cada semana y en cada PR que toca los pins
    (`.github/workflows/pins.yml`). **Si Google cambia de CA raíz** (no solo de
    intermedio): agregar su pin, publicar una versión de la app y esperar a que
    se adopte **antes** de que el servidor cambie. Si se llegara tarde, existe
    el interruptor remoto de abajo.
  - **Interruptor remoto (Firebase Remote Config), firmado.** Parámetro
    `cf_pinning_override` (String) con un JSON `{"v":1,"desde":…,"hasta":…,"firma":"…"}`
    (segundos Unix). Un booleano simple sería una puerta falsa: Remote Config se
    descarga de `firebaseremoteconfig.googleapis.com`, que **no** está pineado, así
    que quien tuviera una CA falsa en el camino (justo la amenaza que frena el
    pinning) podría responder "desactiva el pinning". Por eso la app **solo**
    respeta un override con firma ECDSA P-256 válida contra la clave pública
    embebida (`CLAVE_PUBLICA_OVERRIDE_PINNING`), y ante cualquier duda (vacío,
    basura, firma ajena, valor alterado, vencido, aún no vigente) el pinning
    sigue activo. La ventana va firmada y no puede pasar de **30 días**, así que
    un override no deja el pinning apagado indefinidamente ni puede extenderse
    sin la clave; al vencer, el pinning vuelve solo. Un `Call.Factory` decide
    por llamada entre el cliente con pinning y uno idéntico sin él, así que
    surte efecto en cuanto Remote Config activa el valor, sin reiniciar. Es un
    canal independiente: llega aunque el pinning bloquee las funciones.
    - **Estado de fábrica: inactivo.** La clave pública embebida está vacía, así
      que nadie puede apagar el pinning hasta que se configure (paso 1).
    - **1. Una sola vez:** `scripts/pinning-override.sh generar-clave <archivo.pem>`
      (se niega a escribir dentro del repo). Pegar la clave pública que imprime en
      `CLAVE_PUBLICA_OVERRIDE_PINNING` y publicar una versión. **Guardar la clave
      privada fuera del repo y respaldada**: quien la tenga puede apagar el
      pinning de todas las apps hasta 30 días seguidos; si se pierde, el
      interruptor queda inutilizable hasta publicar una versión con otra clave.
    - **2. En una emergencia** (los pins ya no coinciden con lo que sirve Google,
      p. ej. falla `pins.yml` o llegan reportes de que nada llama a las
      funciones): `scripts/pinning-override.sh firmar <archivo.pem> <días ≤ 30>`
      imprime el JSON; crear en la consola de Firebase → Remote Config el
      parámetro `cf_pinning_override` (String) con ese valor y **publicar**. Las
      apps lo toman en su próxima descarga (intervalo mínimo de 1 h, al abrir la
      app) y lo aplican de inmediato.
    - **3. Mientras tanto:** agregar el pin nuevo, publicar una versión y esperar
      la adopción. Después retirar el parámetro (o dejar que venza).
    - **Límites:** no ayuda a una app que nunca descargó Remote Config (instalada
      sin red) ni a quien no la abra dentro de la ventana. Mientras está activo, el
      tráfico a las Cloud Functions queda protegido solo por la validación normal
      del sistema (sin pinning). La app registra en logcat (etiqueta `KineCarePinning`) cuándo cambia el
      estado. Si el parámetro se publica mal (valor inválido) no pasa nada: sigue
      el pinning.
  - **Efecto secundario:** un proxy con CA instalada (Charles, mitmproxy) no
    puede inspeccionar estas llamadas, ni en debug.
- Tokens en Android Keystore / `EncryptedSharedPreferences`; nunca en
  `SharedPreferences` planas ni en `DataStore` sin cifrar.
- R8/ProGuard habilitado en build de release.
- Firestore Security Rules y Storage Rules por rol (`cliente` / `profesional`),
  nunca confiar en el rol que declara el cliente sin validarlo contra el
  documento de usuario.

## Permisos en tiempo de ejecución
- Cada permiso runtime se declara y solicita desde la feature que lo
  necesita (no en `:app`), siguiendo la regla de "cada módulo declara solo
  lo que usa de verdad".
- Primer precedente: `:feature:search` declara
  `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` en su propio
  `AndroidManifest.xml` y los solicita con
  `rememberLauncherForActivityResult` desde el composable que los usa. La
  ubicación se resuelve con Fused Location Provider (`play-services-location`)
  + `Geocoder` de Android — no se agregó el SDK de Google Maps (ver
  [TASKS.md](TASKS.md#fase-1--arquitectura-base) sobre Maps Compose, que
  sigue diferido hasta que se necesite mostrar un mapa real).

## Persistencia local
- **Room** (`:core:database`) solo para: caché offline de resultados de
  búsqueda, favoritos, borrador de reserva en progreso. No es la fuente de
  verdad — Firestore lo es.
- **DataStore** para preferencias simples (tema, filtros recordados).

## Testing
- JUnit5 + MockK + Turbine para `domain`/`data`/`presentation` (ViewModels).
- Compose UI Testing para pantallas críticas (flujo de reserva, pago).
- CI en GitHub Actions: build + lint + tests en cada PR.

## Integraciones externas (pendientes de decidir/implementar)
- Pasarela de pago: **Mercado Pago** (decidido): Marketplace + Checkout Pro.
  Cada profesional vincula su cuenta por OAuth; el cliente paga en la página
  de Mercado Pago (Custom Tabs) y el dinero llega directo al profesional con
  `marketplace_fee` para la plataforma. Todo el backend vive en Cloud
  Functions (ver [DATA_MODEL.md](DATA_MODEL.md#pagos-con-mercado-pago-marketplace--checkout-pro--sin-desplegar)).
- Verificación de identidad: proveedor tipo Truora/Metamap/Didit — la app
  solo consume el estado (pendiente/aprobado/rechazado) vía Cloud Function.

## Alcance futuro (no en este repo todavía)
- iOS nativo (SwiftUI) reutilizando el mismo backend Firebase.
- Panel web de administración (React o similar).
- Landing page informativa.

Ver también: [DOMAIN.md](DOMAIN.md), [DATA_MODEL.md](DATA_MODEL.md),
[SOLUTION_STRUCTURE.md](SOLUTION_STRUCTURE.md), [TASKS.md](TASKS.md).
