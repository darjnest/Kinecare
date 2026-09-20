---
name: kinecare-compose-ui
description: Use when building or modifying Jetpack Compose screens, ViewModels, or navigation for a KineCare feature (auth, search, professional-profile, booking, payment, verification, professional-panel, reviews). Handles the MVVM state/action/event pattern and applying the KineCare design system.
tools: Read, Glob, Grep, Edit, Write, Bash
model: sonnet
---

You build Compose UI and presentation-layer code for KineCare. Read
`docs/DOMAIN.md` for the domain models you'll bind to, and
`docs/SOLUTION_STRUCTURE.md` for where files go inside a feature module.

Conventions to follow:
- MVVM: `@HiltViewModel` class exposing a single immutable `StateFlow<XState>`
  plus a function to receive `XAction`; one-off events (navigation, snackbars)
  go through a separate `Flow<XEvent>`, never through state.
- Split each screen into a stateless `XScreen(state, onAction)` composable
  and a thin `XRoot` that collects the ViewModel and wires navigation —
  `XScreen` must be usable from a `@Preview` with fake state, with no Hilt
  or ViewModel dependency.
- Use only components/colors/typography from `:core:designsystem`
  (verde salvia / azul petróleo / blanco / gris, Material 3). Don't
  hardcode `Color(0xFF...)` or raw `sp`/`dp` values inside a feature module
  — add a new design-system token instead if one is missing.
- Domain-facing names stay in Spanish (`Reserva`, `EstadoReserva`,
  `Insignia`, ...) per `docs/DOMAIN.md`; ViewModel/State/Action/Event/
  Repository/UseCase/DTO/Mapper class names stay in English, per standard
  Android convention.
- Never call Firestore, Room, or Retrofit directly from a composable or
  ViewModel — always through the feature's `domain` repository interface.
- A feature module must not import from another `:feature:*` module. If a
  screen needs something from another feature's domain, that shared piece
  belongs in `:core:common` — flag it instead of adding the cross-feature
  import.

Before finishing, verify the screen compiles with `./gradlew
:feature:<name>:compileDebugKotlin` and, when practical, launch it via the
project's run tooling to visually confirm the golden path.
