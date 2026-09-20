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

## Repositorio y flujo de ramas
Repo: [github.com/darjnest/Kinecare](https://github.com/darjnest/Kinecare)
(privado, cuenta personal). Modelo de ramas por ambiente:

```
feature/* fix/* chore/*  ──PR──►  QA  ──PR (merge commit)──►  PRD
     ▲                                                          │
     └────────────── se crean desde QA ─────────────────────────┘
     hotfix/*  ◄──── sale de PRD ──────────────────────────────
        └──PR──► PRD  ──back-merge inmediato──►  QA
```

- **`QA`** es la rama de integración y la rama por defecto del repo (así los
  PR nuevos no apuntan a producción por descuido).
- **`PRD`** es la rama de producción (equivalente a `main`, renombrada a
  propósito).
- `QA → PRD` siempre con **merge commit**, nunca squash ni rebase — un
  squash le da un SHA distinto a cada commit y producción deja de contener
  la base común con integración, generando conflictos fantasma en cada
  promoción siguiente.
- `PRD → QA` solo existe para back-merge de un hotfix, y se hace de
  inmediato tras el merge a `PRD`.
- `QA` no es un estacionamiento: todo lo que entra ahí está comprometido a
  llegar a `PRD`.
- Repo config aplicada: rama por defecto `QA`, borrado automático de ramas
  al mergear, "Allow rebase merging" desactivado a nivel de repo.
- Repo **público** (decisión explícita: GitHub Free no permite branch
  protection en repos privados de cuenta personal, solo en públicos o con
  Pro). Protección activa vía repository rulesets:
  - `QA`: PR obligatorio (0 aprobaciones), sin force-push, sin borrado.
  - `PRD`: PR obligatorio (1 aprobación), sin force-push, sin borrado,
    **método de merge restringido a "merge commit"** (squash/rebase
    deshabilitados para esta rama — es la regla no negociable de arriba,
    forzada técnicamente, no solo por disciplina).
  - Verificado con `gh api repos/darjnest/Kinecare/rules/branches/<rama>`.

**PENDIENTE:** CI (Fase 4) y release firmado (Fase 5) del estándar del
equipo, hasta que exista código funcional que compilar/firmar. También
pendiente: exigir un check de CI como obligatorio en ambas ramas (recién
se puede una vez que el workflow haya corrido al menos una vez).

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
