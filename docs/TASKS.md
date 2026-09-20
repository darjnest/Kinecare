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

## Fase 2 — Auth + búsqueda
- [ ] Firebase Authentication (email/password)
- [ ] Pantalla login/registro, selección de rol
- [ ] `:feature:search`: pantalla inicio con filtros, resultados
- [ ] Firestore: colección `profesionales` + índices de búsqueda

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
- [ ] `:feature:professional-panel`: dashboard, servicios, calendario/
      disponibilidad, reservas, pagos/ingresos, verificaciones,
      configuración

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
