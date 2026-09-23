# Estructura de la solución — KineCare

## Árbol de módulos objetivo

```
Kinecare/
├── app/                               # Ensambla NavGraph, Hilt Application, MainActivity
├── core/
│   ├── common/                        # Modelos de dominio (Kotlin puro), Result/DataError
│   ├── network/                       # Retrofit/OkHttp hacia Cloud Functions (Hilt di/)
│   ├── database/                      # Room: caché, favoritos, borrador de reserva
│   └── designsystem/                  # Tema Material 3 + componentes Compose reutilizables
├── feature/
│   ├── auth/
│   ├── search/
│   ├── professional-profile/
│   ├── booking/
│   ├── payment/
│   ├── verification/
│   ├── professional-panel/
│   ├── reviews/
│   └── client-panel/
└── docs/                              # Este set de documentos de contexto
```

`build-logic` (convention plugins) queda pendiente como mejora de corto
plazo: por ahora cada módulo declara su propio `build.gradle.kts`
consistente. Se extraerá a convention plugins una vez que el patrón esté
estable en 2-3 módulos (evita fijar una abstracción prematura sobre un DSL
de AGP todavía nuevo — AGP 9.4.1 con sintaxis declarativa
`compileSdk { version = release(37) }`).

## Estructura interna de un módulo `:core:*` o `:feature:*`

Convención adoptada (alineada con otros proyectos de StarConsulting, ej.
`bf_reserva`): cada módulo organiza su código por **capa técnica**
(`data` / `di` / `domain` / `presentation`), y dentro de `presentation` por
**tipo de artefacto Compose** (`navigation`, `view`, `viewmodel`, `util`).
No se crean carpetas vacías "por si acaso" — cada una aparece cuando tiene
contenido real.

```
feature/booking/src/main/kotlin/com/darjnest/kinecare/feature/booking/
├── data/
│   ├── repository/           # interfaces de repositorio
│   ├── repository_impl/       # implementaciones (Firestore/Cloud Functions)
│   └── service/                # mappers/adaptadores sobre Firebase o Retrofit
├── di/
│   └── BookingModule.kt        # @Module @InstallIn(SingletonComponent::class)
├── domain/
│   ├── model/                   # modelos exclusivos de la feature (si los hay)
│   └── service/                  # logica de negocio pura (equivalente a use cases)
└── presentation/
    ├── navigation/                # Route (@Serializable) + NavGraphBuilder.xxxGraph()
    ├── view/                       # Root (conecta ViewModel) + Screen (stateless, @Preview)
    ├── viewmodel/                   # ViewModel + State/Action/Event
    └── util/                        # helpers de UI exclusivos de la feature (raro; si es
                                       # generico va a :core:designsystem en vez de aqui)
```

`:core:designsystem` sigue la misma logica para sus propios componentes:

```
core/designsystem/src/main/kotlin/com/darjnest/kinecare/core/designsystem/
├── theme/            # Color.kt, Type.kt, Theme.kt (paleta + Material 3)
└── components/
    ├── button/
    ├── card/
    ├── label/         # badges/insignias (tono semantico, sin conocer modelos de dominio)
    ├── loading/
    ├── dialog/
    └── ...             # bar/, form/, icon/, tooltip/, etc. se agregan cuando una
                          # pantalla real los necesite, no antes
```

`:core:database` sigue `entity/`, `dao/`, `di/` (sin `presentation`, no
tiene UI). `:core:network` sigue `di/` (y sumará `service/`,
`dto/`, `firebase/` cuando existan endpoints reales — ver
docs/TASKS.md Fase 2 en adelante).

`:feature:client-panel` agrupa las pantallas de cuenta del rol Cliente
(Mis Citas, Favoritos, Mi Perfil) bajo un solo módulo, igual que
`:feature:professional-panel` agrupa las del rol Profesional — no son
`:feature:*` separados porque comparten la misma barra de navegación
inferior y no tienen entidad propia fuera de "cuenta del cliente".
`:feature:search` sigue siendo el módulo de "Explorar" (home del rol
Cliente); las 4 pestañas de la barra inferior viven repartidas entre
`:feature:search` y `:feature:client-panel`, conectadas por callbacks
desde el `NavHost` de `:app` (nunca entre sí, por la regla de
dependencia de abajo). El componente `KineCareBottomNavBar` compartido
por ambos vive en `:core:designsystem/components/bar/`.

## Reglas de dependencia (sin cambios respecto a antes)

| Módulo | Puede depender de |
|---|---|
| `:feature:*` | `:core:common`, `:core:network`, `:core:database`, `:core:designsystem` |
| `:core:network` | `:core:common` |
| `:core:database` | `:core:common` |
| `:core:designsystem` | (nada de negocio; solo Compose/Material3) |
| `:app` | todos los módulos (ensambla NavGraph + Hilt) |

Las features **nunca se dependen entre sí**. Un módulo declara solo las
dependencias que usa de verdad — no se agrega `:core:network` o
`:core:database` a una feature "por si se necesita después".

## Paquete base
`com.darjnest.kinecare` (namespace del proyecto). Cada módulo usa
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

## CI (GitHub Actions, a configurar en fase 8)
- `build.yml`: `./gradlew build` + `./gradlew testDebugUnitTest` +
  `./gradlew lintDebug` en cada PR contra `QA` y `PRD`.

Ver también: [ARCHITECTURE.md](ARCHITECTURE.md), [DOMAIN.md](DOMAIN.md),
[DATA_MODEL.md](DATA_MODEL.md), [TASKS.md](TASKS.md).
