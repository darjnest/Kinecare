# KineCare — Contexto del proyecto

App Android nativa (Kotlin + Jetpack Compose) para un marketplace de
servicios de kinesiología y masoterapia en Chile. Dos roles en una sola
app: **Cliente** (busca, reserva, paga) y **Profesional** (gestiona perfil,
servicios, disponibilidad, reservas, pagos, verificación). Diferenciador de
negocio: mostrar qué aspectos de identidad y credenciales de cada
profesional están verificados (insignias). Marketplace con comisión por
reserva.

Este archivo es el punto de entrada. El detalle vive en `docs/`:
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — stack, capas, módulos,
  reglas de dependencia, DI, navegación, seguridad, backend Firebase.
- [docs/DOMAIN.md](docs/DOMAIN.md) — modelos de dominio y reglas de negocio.
- [docs/DATA_MODEL.md](docs/DATA_MODEL.md) — colecciones Firestore, Storage,
  entidades Room.
- [docs/SOLUTION_STRUCTURE.md](docs/SOLUTION_STRUCTURE.md) — árbol de
  módulos, estructura interna de una feature, convenciones de nombres.
- [docs/TASKS.md](docs/TASKS.md) — plan de implementación por fases y
  estado actual.

## Stack técnico (resumen)
Kotlin, Jetpack Compose, Material 3, MVVM + Clean Architecture, Hilt,
Firebase (Firestore, Auth, Cloud Functions, FCM, Storage), Retrofit +
OkHttp + Kotlinx Serialization (solo hacia Cloud Functions / servicios
externos), Room (caché offline), Coroutines + Flow, Navigation Compose,
Coil, Google Maps Compose + Fused Location, DataStore +
EncryptedSharedPreferences/Keystore, JUnit5 + MockK + Turbine + Compose UI
Testing, GitHub Actions.

## Reglas que no se negocian
- Pagos y verificación de identidad se resuelven **siempre** en Cloud
  Functions, nunca en el cliente. Ver
  [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#seguridad).
- Nunca se guarda biometría ni documentos de identidad en el dispositivo.
- Los módulos `:feature:*` nunca se dependen entre sí — lo compartido va a
  `:core:*`. Ver [docs/SOLUTION_STRUCTURE.md](docs/SOLUTION_STRUCTURE.md).
- Sin versiones hardcodeadas en Gradle: todo vía `gradle/libs.versions.toml`.
- Modelos y estados de negocio en español (`Reserva`, `Insignia`,
  `EstadoReserva`); nombres técnicos (ViewModel, Repository, DTO) en inglés.

## Agentes especializados
Este proyecto define subagentes en `.claude/agents/` — úsalos para el tipo
de tarea correspondiente en vez de trabajar genéricamente:
- `kinecare-architect` — módulos Gradle, Hilt, Navigation, capas Clean Architecture.
- `kinecare-compose-ui` — pantallas Compose, ViewModels, design system.
- `kinecare-firebase` — Firestore, Security Rules, Cloud Functions, Storage.
- `kinecare-qa` — tests unitarios y de UI.

## Estado actual
Ver [docs/TASKS.md](docs/TASKS.md) para la fase en curso y qué falta. Al
completar trabajo de una fase, marca las casillas correspondientes ahí en
vez de duplicar el estado en este archivo.

## Mantenimiento de este contexto
Cuando una decisión de arquitectura, dominio o modelo de datos cambie,
actualiza el doc correspondiente en `docs/` en el mismo cambio de código
que la introduce — no dejes que estos archivos queden desincronizados del
código real.
