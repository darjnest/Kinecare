# Pendientes — revisión del 2026-10-08

Complemento de [TASKS.md](TASKS.md): ese archivo es el plan por fases; este
es el corte "qué falta de verdad para seguir mañana", contrastado con el
código, los PR abiertos y las ramas remotas. Cuando un ítem se cierre, se
marca en TASKS.md (si pertenece a una fase) y se borra de aquí.

## 0. Primero mañana (desbloquea todo lo demás)

- [ ] **PR #41 (verificación de identidad con Didit) tiene el CI en rojo.**
      Job *Cloud Functions*: 292 pasan, 0 fallan, **9 "cancelled"**. El primero
      es `didit.test.ts:87` "timeout (la petición no responde) → TIMEOUT" con
      `Promise resolution is still pending but the event loop has already
      resolved`; los otros 8 son sus hermanos (`cancelledByParent`). Causa
      probable: ese test espera un timer `unref`-eado / una petición colgada, y
      en el runner (Node 24 forzado) el event loop se vacía antes. Localmente
      pasa. Arreglar el test (mantener el handle vivo o usar timers falsos) y
      volver a correr; sin eso el check `CI` no se puede exigir ni mergear.
- [ ] **Decidir qué hacer con el PR #41 antes de mergearlo.** Es +4.861 líneas,
      sin probar contra Didit real (no hay cuenta ni sandbox). Riesgo principal
      documentado en el PR: `personal_number` ≠ RUN en cédulas chilenas reales
      (aprueba sin comparar, o rechaza a un profesional legítimo). Opciones:
      mergear a `QA` tras el arreglo del CI (queda inerte sin secretos) y probar
      con una cédula real, o esperar a tener cuenta Didit.
- [ ] **Rama huérfana `origin/claude/revision-tareas-6fe105`** (3 commits:
      conectar Mercado Pago por OAuth + `storage.rules`). Es una versión
      anterior, superada por el PR #36 ya mergeado (mismo trabajo, otra
      implementación). **No mergear**; revisar que no traiga nada que falte y
      borrarla. Hay otras ~14 ramas `claude/*` remotas ya integradas que se
      pueden limpiar.

## 1. Acciones manuales (no las puede hacer el código)

Ordenadas por lo que más bloquea.

- [ ] **Mercado Pago**: crear la app en el panel (Chile), cargar
      `MP_CLIENT_SECRET`, `MP_WEBHOOK_SECRET`, `MP_TOKEN_ENCRYPTION_KEY`,
      `MP_APP_ID`, registrar Redirect URI y webhook, y desplegar las 6
      funciones de pago en QA (ya está en Blaze). Probar con usuarios de prueba
      (vendedor + comprador). *Hoy el flujo de pago existe en código y tests
      pero nunca se ejecutó contra Mercado Pago.*
- [ ] **Storage en QA**: crear el bucket desde la consola de Firebase y correr
      `firebase deploy --only storage -P qa`. Desbloquea "Cambiar foto"
      (cliente y profesional) y los documentos de credenciales.
- [ ] **Cuenta de profesional de prueba con Firebase Auth** (hoy
      `usuarios/88154428` no tiene cuenta en Auth, nadie puede iniciar sesión
      como él). Sin ella no se puede probar en dispositivo aceptar/rechazar
      reservas, el dashboard del profesional ni "Conectar Mercado Pago".
- [ ] **Activar el interruptor remoto del pinning**:
      `scripts/pinning-override.sh generar-clave <archivo fuera del repo>`,
      pegar la pública en `CLAVE_PUBLICA_OVERRIDE_PINNING`, respaldar la
      privada, publicar versión. Hoy está inerte (clave vacía).
- [ ] **Didit** (si se sigue con el PR #41): abrir cuenta, workflow documento +
      prueba de vida, webhook a `webhookDidit`, secretos `DIDIT_API_KEY` /
      `DIDIT_WEBHOOK_SECRET`, `DIDIT_WORKFLOW_ID` en `functions/.env.qa`.
- [ ] **Exigir el check `CI` en las rulesets de `QA` y `PRD`** (ya hubo
      corridas, ahora se puede elegir). Hacerlo *después* de arreglar el rojo
      del PR #41.
- [ ] Registrar SHA-1/SHA-256 del keystore de **release** en Firebase.
- [ ] Producción (`kinecare-cl`) sigue en Spark y sin funciones: al promover
      QA → PRD hay que subirla a Blaze y desplegar reglas, índices,
      `tiposAtencion` y funciones.

## 2. Pruebas en dispositivo/emulador (todo lo "no verificado")

Casi todo el código de las fases 3–7 compila y pasa tests JVM, pero **se vio en
pantalla muy poco**. Una sola sesión de emulador (Pixel 7a, flavor QA) cubre
casi todo esto, y varias casillas se cierran a la vez:

- [ ] Reservar con cuenta de cliente → ver en "Mis Citas" → **Reportar
      problema** (Fase 4, último ítem).
- [ ] Con la cuenta de profesional: aceptar/rechazar, y ver el cambio en "Mis
      Citas" del cliente (Fase 7, solicitudes).
- [ ] Completar una cita y **dejar una reseña real** → comprobar que
      `recalcularCalificacion` actualiza el perfil (Fase 3).
- [ ] Confirmar que **el certificate pinning no rompe** reservar / estado de
      pago / verificación sobre el stack TLS de Android (Fase 5). Después,
      probar el interruptor remoto con un override de prueba en Remote Config.
- [ ] Revisión visual de lo marcado "no verificado visualmente": perfil
      público, reseñas, "Mis Citas", editar `tiposAtencion`, dashboard del
      profesional.

## 3. Trabajo de código que falta

### Funcionalidad con huecos reales

- [ ] **Completar una reserva (`CONFIRMADA` → `COMPLETADA`)**: no existe la
      función ni la acción. Es la pieza que falta para que funcionen: dejar
      reseña, "Por liquidar" del dashboard, liquidaciones y el reembolso. Hoy
      nada pasa una reserva a `COMPLETADA`.
- [ ] **Cancelar una cita confirmada** (`CANCELADA_CLIENTE` /
      `CANCELADA_PROFESIONAL`) con su política de reembolso.
- [ ] **Expirar solicitudes sin respuesta** (función programada; requiere
      Blaze).
- [ ] **Notificaciones push (FCM)**: la dependencia está en el catálogo pero no
      hay servicio, token ni triggers. El cliente solo ve el cambio de estado
      al abrir la app.
- [ ] **Recuperar contraseña**: el diálogo de `AuthScreen` ("¿Olvidaste tu
      contraseña?") solo dice "contáctanos". `FirebaseAuth.sendPasswordResetEmail`
      resuelve esto en pocas líneas.
- [ ] **Liquidaciones y finanzas** y **Documentos y validación**
      (`:feature:professional-panel`): siguen con datos vacíos, sin conexión a
      nada, y **sin tests** (son los únicos 2 ViewModels de feature sin test,
      más `VerificationViewModel` mientras el PR #41 no entre). Liquidaciones
      depende de `COMPLETADA` + `pagos`; documentos depende de Storage.
- [ ] **Fase 6 completa** (verificación): hoy `VerificationScreen` en `QA` es
      un placeholder "próximamente" y la función `solicitarVerificacion` no
      existe en la rama principal. El PR #41 cubre solo `IDENTIDAD`;
      `CREDENCIALES`, `AUTENTICIDAD` y `HISTORIAL` no tienen flujo.
- [ ] **Alta de servicios y edición de turnos** en el panel profesional: hoy
      solo se pausa/activa un servicio y no se puede crear ni editar uno, ni
      cambiar la hora de un turno (faltan en los mockups; requieren diseño).
- [ ] Reservas inmediatas, traslado, radio y comunas de atención del
      profesional son solo estado local de la pantalla (sin campos en
      Firestore); "perfil público" on/off también.
- [ ] Cliente — "Mi perfil": previsión, tratamiento, método de pago,
      facturación y avisos son mock/vacíos; las acciones `GestionarPrevision…`,
      `AgregarNuevaDireccion`, `EditarFacturacion`, `VerDerechosDelPaciente`,
      `VerPauta…` y `VerHistorialDeEvoluciones` son no-op. *Agregar nueva
      dirección* es la más útil (la reserva a domicilio ya usa direcciones).
- [ ] Botón "Ayuda" de la pantalla de "completar perfil" (`AuthScreen.kt:413`)
      con `onClick = {}`.
- [ ] Búsqueda: solo filtra por Kinesiología/Masoterapia y ubicación (sin
      texto libre, ordenamiento ni mapa); la vitrina de categorías
      (`categoriasPara()`) es mock local.
- [ ] Reseñas: responder (el profesional), editar/borrar, paginar más de 100.
- [ ] Reportes de problema: adjuntar fotos (Storage), aviso a soporte,
      reportar desde el panel profesional.
- [ ] Un cliente puede ver como disponible un cupo ya tomado (no ve reservas
      ajenas por Security Rules); se detecta recién al confirmar. Evaluar una
      función `obtenerCuposOcupados` si molesta en pruebas.

### Arquitectura / deuda técnica

- [ ] **Room no se usa**: `:core:database` tiene entidades y DAOs
      (`FavoritoDao`, `BusquedaCacheDao`, `BorradorReservaDao`) pero ninguna
      feature ni `:core:network` los inyecta. La "caché offline" y el borrador
      de reserva de ARCHITECTURE.md no existen. Decidir: conectarlos o sacarlos
      del alcance del lanzamiento (y actualizar el doc).
- [ ] `build-logic` con convention plugins sigue diferido (cada módulo repite
      su `build.gradle.kts`).
- [ ] Un índice viejo de `especialidades` sigue en QA sin usar (el deploy no
      lo borra sin `--force`).

## 4. Calidad y publicación (Fase 8)

- [ ] **Release**: `app/build.gradle.kts` tiene `optimization { enable = false }`
      en `release` (R8/ProGuard apagado) y no hay `signingConfig`. Habilitar
      R8, escribir reglas keep (Retrofit/Kotlinx Serialization/Hilt/Firebase),
      probar el APK release y configurar la firma (keystore + secretos de CI).
- [ ] **Tests de UI**: `app/src/androidTest` solo tiene `ExampleInstrumentedTest`;
      no hay un solo test de Compose UI. Cubrir al menos reserva y pago, que son
      los flujos críticos. Los tests de ViewModel/repositorio sí están bien
      (≈ 55 archivos), pero `:core:common`, `:core:designsystem` y
      `:core:database` casi no tienen.
- [ ] **Revisión de seguridad**: pasar `firebase-security-rules-auditor` sobre
      `firestore.rules` / `storage.rules` (las reglas crecieron mucho desde que
      se escribieron) y la skill `security-review` sobre la app.
- [ ] Crashlytics/Analytics: no están en el catálogo; decidir si entran antes
      de publicar.
- [ ] CI: revisar el tiempo de la corrida y si conviene separar el job de
      Android; los runners ya avisan de que Node 20 está deprecado en las
      actions.
- [ ] Play Store: ficha, política de privacidad, declaración de datos y de
      permisos (ubicación), prueba cerrada.

## 5. Sugerencia de orden para mañana

1. Arreglar el test de Didit y dejar el PR #41 en verde; decidir si se mergea.
2. Crear la cuenta de profesional de prueba en Auth (5 min) y hacer **una
   sesión de emulador** con las verificaciones de la sección 2.
3. Mientras tanto, en paralelo: recuperar contraseña, "Agregar dirección" y la
   función de **completar reserva** (destraba reseñas, liquidaciones y
   reembolsos).
4. Si hay tiempo: abrir la cuenta de Mercado Pago de pruebas y desplegar pagos
   a QA.
