# Estructura de la solución — KineCare

## Árbol de módulos objetivo

```
Kinecare/
├── build-logic/                      # Convention plugins de Gradle
│   └── convention/
│       └── src/main/kotlin/
│           ├── AndroidApplicationConventionPlugin.kt
│           ├── AndroidFeatureConventionPlugin.kt   (Android lib + Compose + Hilt)
│           ├── AndroidLibraryConventionPlugin.kt
│           ├── HiltConventionPlugin.kt
│           └── RoomConventionPlugin.kt
├── app/                               # Ensambla NavGraph, Hilt Application, MainActivity
├── core/
│   ├── common/                        # Modelos de dominio, Result/DataError, extensiones
│   ├── network/                       # Retrofit/OkHttp hacia Cloud Functions, wrapper Firebase SDK
│   ├── database/                      # Room: caché, favoritos, borrador de reserva
│   └── designsystem/                  # Colores, tipografía, componentes Compose, tema Material 3
├── feature/
│   ├── auth/
│   ├── search/
│   ├── professional-profile/
│   ├── booking/
│   ├── payment/
│   ├── verification/
│   ├── professional-panel/
│   └── reviews/
└── docs/                              # Este set de documentos de contexto
```

## Estructura interna de un módulo `:feature:*`

Cada feature es **un solo módulo Gradle** (no se divide en submódulos
`data`/`domain`/`presentation` como módulos separados — eso vive como
paquetes dentro del mismo módulo, según el brief del proyecto):

```
feature/booking/src/main/java/com/darjnest/kinecare/feature/booking/
├── data/
│   ├── dto/                # DTOs de Firestore/Cloud Functions
│   ├── mapper/              # DTO ↔ modelo de dominio
│   └── BookingRepositoryImpl.kt
├── domain/
│   ├── BookingRepository.kt # interfaz
│   └── usecase/             # casos de uso puros, si se justifican
├── presentation/
│   ├── BookingViewModel.kt
│   ├── BookingState.kt / BookingAction.kt / BookingEvent.kt
│   └── screens/              # composables (Root + Screen, ver convención MVI)
└── navigation/
    └── BookingNavGraph.kt    # NavGraphBuilder.bookingGraph(...)
```

`:app` importa cada `xxxNavGraph.kt` y los compone en `clienteGraph` /
`profesionalGraph` según corresponda.

## Paquete base
`com.darjnest.kinecare` (namespace ya definido en el proyecto generado por
Android Studio). Cada módulo usa
`com.darjnest.kinecare.<core|feature>.<nombre>` como namespace.

## Convenciones de nombres
- Modelos de dominio, estados de UI y nombres de negocio: **en español**
  (`Reserva`, `EstadoReserva`, `Insignia`), consistente con
  [DOMAIN.md](DOMAIN.md).
- Nombres técnicos genéricos (ViewModel, Repository, UseCase, DTO, Mapper):
  en inglés, siguiendo convención Android estándar.
- Rutas de navegación: `data class` `@Serializable` con sufijo `Route`.

## Gradle
- Version catalog único: `gradle/libs.versions.toml`.
- Sin versiones hardcodeadas en `build.gradle.kts` de ningún módulo.
- Convention plugins en `build-logic` para: app Android, librería Android,
  feature Android (lib + Compose + Hilt), Hilt, Room. Evita repetir
  `compileSdk`, `minSdk`, `compose { }`, `kapt`/`ksp` en cada módulo.

## CI (GitHub Actions, a configurar en fase 8)
- `build.yml`: `./gradlew build` + `./gradlew testDebugUnitTest` +
  `./gradlew lintDebug` en cada PR contra `main`.

Ver también: [ARCHITECTURE.md](ARCHITECTURE.md), [DOMAIN.md](DOMAIN.md),
[DATA_MODEL.md](DATA_MODEL.md), [TASKS.md](TASKS.md).
