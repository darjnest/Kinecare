# Pendientes — revisión del 2026-10-12

Complemento de [TASKS.md](TASKS.md): ese archivo es el plan por fases; este
es el corte "qué falta de verdad para seguir mañana", contrastado con el
código, los PR abiertos y el estado real de QA. Cuando un ítem se cierre, se
marca en TASKS.md (si pertenece a una fase) y se borra de aquí.

## Estado verificado contra QA (`kinecare-cl-qa`, 2026-10-12)

Comprobado con el MCP de Firebase (solo lectura):

- **Funciones desplegadas (14):** `crearReserva`, `responderReserva`,
  `completarReserva`, `cancelarReserva`, `obtenerCuposOcupados`,
  `expirarSolicitudes` (programada), `notificarReserva` (trigger de `reservas`),
  `recalcularCalificacion` (trigger de `resenas`) y las 6 de Mercado Pago
  (`conectarMercadoPago`, `mercadoPagoOAuthCallback`, `iniciarPago`,
  `estadoPago`, `webhookMercadoPago`, `retornoPago`). Node 22, `us-central1`.
  **No existe `solicitarVerificacion`** (Fase 6 / PR #41).
- **Reglas de Firestore:** el texto desplegado contiene todo lo reciente:
  `dispositivos` (push), `servicioValido` (límites de servicios), respuesta
  del profesional en `resenas`, `reportesProblema`, `bloqueosAgenda`,
  `solicitudesVerificacion`, `tiposAtencionValidos` y el bloqueo de
  `mercadoPagoConectado`. Comparación por marcadores, no un diff byte a byte.
- **Reglas de Storage:** desplegadas, con la ruta del cliente
  (`clientes/{uid}/foto-perfil.jpg`) y la del profesional.
- **Índices:** los 8 de `firestore.indexes.json` están en `READY`
  (incluido `reservas(estado, fechaHora)`). Sigue el índice viejo de
  `especialidades` sin usar.
- **Pago tardío tras cancelar (PR #51):** `webhookMercadoPago` y `estadoPago`
  redesplegadas en QA el 2026-10-12 desde `QA` (76cb497); sin firma/sesión
  responden 401. Para las demás funciones el MCP no muestra la versión del código
  desplegado: si hay dudas, redesplegar.
- **Producción (`kinecare-cl`)** no se tocó: sigue en Spark, sin funciones.

## 0. Primero mañana

- [ ] **PR #41 (verificación de identidad con Didit), único PR abierto, con el
      CI en rojo desde el 2026-10-08.** Job *Cloud Functions*: 292 pasan, 0
      fallan, **9 "cancelled"**; el primero es `didit.test.ts:87` "timeout (la
      petición no responde) → TIMEOUT" con `Promise resolution is still pending
      but the event loop has already resolved` (los otros 8 son sus hermanos).
      Causa probable: el test espera un timer `unref`-eado y en el runner
      (Node 24 forzado) el event loop se vacía antes. Localmente pasa. Arreglar
      el test (mantener el handle vivo o usar timers falsos). El job Android sí
      pasa. Además, la rama está atrás de `QA` (varias funciones y reglas nuevas
      desde entonces): hay que actualizarla antes de mergear.
- [ ] **Decidir qué hacer con el PR #41.** +4.861 líneas, sin probar contra
      Didit real (no hay cuenta ni sandbox). Riesgo principal documentado en el
      PR: `personal_number` ≠ RUN en cédulas chilenas reales (aprueba sin
      comparar, o rechaza a un profesional legítimo). Opciones: mergear a `QA`
      tras arreglar el CI (queda inerte sin secretos) o esperar a tener cuenta.
- [ ] **Limpiar ramas remotas.** `origin/claude/revision-tareas-6fe105` es una
      versión anterior de Mercado Pago/Storage superada por el PR #36: **no
      mergear**, revisar que no traiga nada y borrarla. Hay más ramas `claude/*`
      remotas ya integradas que se pueden borrar.

## 1. Acciones manuales (no las puede hacer el código)

Ordenadas por lo que más bloquea.

- [ ] **Cuenta de profesional de prueba con Firebase Auth** (hoy
      `usuarios/88154428` no tiene cuenta en Auth, nadie puede iniciar sesión
      como él; se puede crear una registrándose desde la app con rol
      Profesional). Sin ella no se puede probar en dispositivo aceptar/rechazar,
      completar, cancelar, el dashboard ni "Conectar Mercado Pago".
- [ ] **Mercado Pago de punta a punta en QA** (la app, los secretos, el webhook y
      los usuarios de prueba ya están; las 6 funciones están desplegadas). Falta:
      Conectar con el vendedor de prueba y pagar con el comprador de prueba.
      `MP_CLIENT_SECRET` es el Access Token de prueba y **no se ha verificado que
      el OAuth lo acepte**; si lo rechaza hay que activar credenciales de
      producción de la app (piden una URL de sitio web que KineCare aún no tiene).
- [ ] **Respaldar la clave privada del interruptor del pinning**
      (`~/.kinecare-secrets/pinning-override-qa.pem`) en la bóveda/gestor de
      contraseñas y publicar una versión con la clave pública embebida (ya está
      en el código). Después, probar el interruptor con Remote Config real.
- [ ] **Didit** (si se sigue con el PR #41): abrir cuenta, workflow documento +
      prueba de vida, webhook a `webhookDidit`, secretos `DIDIT_API_KEY` /
      `DIDIT_WEBHOOK_SECRET`, `DIDIT_WORKFLOW_ID` en `functions/.env.qa`.
- [ ] Registrar SHA-1/SHA-256 del keystore de **release** en Firebase.
- [ ] Producción (`kinecare-cl`) sigue en Spark y sin funciones: al promover
      QA → PRD hay que subirla a Blaze y desplegar reglas, índices, Storage
      (bucket + reglas), `tiposAtencion` y funciones; configurar Mercado Pago
      productivo (Redirect URL, webhook, secretos).

## 2. Pruebas en dispositivo/emulador (todo lo "no verificado")

Casi todo el código de las fases 3–7 compila y pasa tests JVM, pero **se vio en
pantalla muy poco**. Una sola sesión de emulador (Pixel 7a, flavor QA) cubre
casi todo esto:

- [ ] Reservar con cuenta de cliente → ver en "Mis Citas" → **Reportar
      problema**; comprobar que los cupos ocupados se descuentan.
- [ ] Con la cuenta de profesional: aceptar/rechazar, **marcar como completada**,
      cancelar, y ver el cambio en "Mis Citas" del cliente.
- [ ] Cancelar una cita como cliente y como profesional (aviso de reembolso y
      `reembolsoPendiente` en el pago).
- [ ] Completar una cita y **dejar una reseña real** → comprobar que
      `recalcularCalificacion` actualiza el perfil; responder la reseña como
      profesional.
- [ ] **Push**: token en `usuarios/{uid}/dispositivos` tras login y borrado al
      salir; notificación en primer plano, en segundo plano y con la app cerrada;
      toque → Mis Citas / Solicitudes; permiso en Android 13+. `notificarReserva`
      está desplegada pero la mensajería real nunca se probó.
- [ ] **Foto de perfil**: subida real a QA, foto en el perfil público, reemplazo
      sin foto cacheada.
- [ ] Confirmar que **el certificate pinning no rompe** reservar / estado de
      pago / verificación sobre el stack TLS de Android.
- [ ] Revisión visual de lo marcado "no verificado visualmente": perfil público,
      reseñas, "Mis Citas", editar `tiposAtencion`, dashboard del profesional,
      pantallas posteriores al login.

## 3. Trabajo de código que falta

### Funcionalidad con huecos reales

- [ ] **Reembolso real de Mercado Pago**: no hay cliente de reembolso; hoy solo
      queda `pagos/{id}.reembolsoPendiente` (incluye el pago tardío tras
      cancelar, `reembolsoMotivo: "PAGO_TARDIO"`) para procesarlo a mano. La
      preferencia de Checkout Pro sigue abierta tras cancelar.
- [ ] **Aviso a profesionales sin Mercado Pago en el backend**: `crearReserva` y
      `responderReserva` los aceptan (decisión: solo aviso en la app).
- [ ] **`EstadoReserva.EXPIRADA`**: `expirarSolicitudes` hoy deja las solicitudes
      vencidas como `RECHAZADA` con `expirada: true`; cuando el enum lo incorpore
      basta cambiar `ESTADO_AL_EXPIRAR`.
- [ ] **Recuperar contraseña** (necesita decisión): el login es por RUT y el
      correo de Firebase Auth es sintético (`<rut>@rut.kinecare.cl`), así que
      `sendPasswordResetEmail` no sirve tal cual; hace falta una Cloud Function
      que busque `correoContacto` y envíe el enlace con un proveedor de correo
      (SendGrid, Trigger Email, etc.). Hoy el diálogo solo dice "contáctanos" y
      no hay canal de soporte definido.
- [ ] **Documentos y validación** (`:feature:professional-panel`): sin
      conexión a nada y sin tests (los documentos los maneja el backend, Fase 6).
      Liquidaciones ya está conectada y probada.
- [ ] **Fase 6 completa** (verificación): `VerificationScreen` en `QA` es un
      placeholder "próximamente" y `solicitarVerificacion` no existe en `QA`.
      El PR #41 cubre solo `IDENTIDAD`; `CREDENCIALES`, `AUTENTICIDAD` y
      `HISTORIAL` no tienen flujo.
- [ ] Reservas inmediatas, traslado, radio y comunas de atención del
      profesional, y "perfil público" on/off: sin campos en Firestore (se quitaron
      o quedan como estado local).
- [ ] **Modo oscuro real**: el tema está fijo en claro (`KineCareTheme`,
      `darkTheme = false`) porque ~1.000 usos de la paleta `Login*`/`Color.White`
      están pensados para fondo claro. Requiere diseño oscuro y repasar cada
      pantalla.
- [ ] Cliente — "Mi perfil": previsión, tratamiento, método de pago y
      facturación siguen ocultos (sin datos en Firestore).
- [ ] Búsqueda: sin mapa ni comuna del profesional en Firestore (el texto solo la
      encuentra si está en la descripción); la vitrina de categorías
      (`categoriasPara()`) es mock local.
- [ ] Reseñas: editar/borrar la respuesta del profesional, paginar más de 100.
- [ ] Reportes de problema: adjuntar fotos, aviso a soporte, reportar desde el
      panel profesional.
- [ ] Boletas del SII, "Comprobante" y "Certificado anual" de Liquidaciones
      (avisan "Disponible próximamente").

### Arquitectura / deuda técnica

- [ ] **Room no se usa**: `:core:database` tiene entidades y DAOs
      (`FavoritoDao`, `BusquedaCacheDao`, `BorradorReservaDao`) pero ninguna
      feature ni `:core:network` los inyecta. La "caché offline" y el borrador
      de reserva de ARCHITECTURE.md no existen. Decidir: conectarlos o sacarlos
      del alcance del lanzamiento (y actualizar el doc).
- [ ] `build-logic` con convention plugins sigue diferido (cada módulo repite
      su `build.gradle.kts`).
- [ ] Borrar el índice viejo de `especialidades` en QA (el deploy no lo borra
      sin `--force`).

## 4. Calidad y publicación (Fase 8)

- [ ] **Release**: `app/build.gradle.kts` tiene `optimization { enable = false }`
      en `release` (R8/ProGuard apagado) y no hay `signingConfig`. Habilitar
      R8, escribir reglas keep (Retrofit/Kotlinx Serialization/Hilt/Firebase),
      probar el APK release y configurar la firma (keystore + secretos de CI).
- [ ] **Tests de UI**: `app/src/androidTest` solo tiene `ExampleInstrumentedTest`;
      no hay un solo test de Compose UI. Cubrir al menos reserva y pago, que son
      los flujos críticos. Los tests de ViewModel/repositorio sí están bien,
      pero `:core:common`, `:core:designsystem` y `:core:database` casi no tienen.
- [ ] **Desplegar la separación público/privado de `usuarios`** (código hecho,
      ver TASKS.md Fase 8). Orden: (1) `node scripts/migrar-perfiles-publicos-qa.mjs`
      (dry-run) y luego `--aplicar`; si avisa de un **RUT repetido** entre dos
      usuarios, resolverlo a mano antes de seguir; (2) desplegar
      `firestore.rules` a QA; (3) instalar la app nueva. Verificar después con la
      cuenta de profesional de prueba que Solicitudes muestra el nombre del
      paciente y que "Cambiar foto"/"Información personal" siguen guardando. Aún
      falta de aquel hallazgo el tope de `servicios` por profesional.
- [ ] **Revisión de seguridad**: pasar `firebase-security-rules-auditor` sobre
      `firestore.rules` / `storage.rules` (las reglas crecieron mucho) y la skill
      `security-review` sobre la app.
- [ ] Crashlytics/Analytics: no están en el catálogo; decidir si entran antes
      de publicar.
- [ ] CI: revisar el tiempo de la corrida y si conviene separar el job de
      Android; los runners avisan de que Node 20 está deprecado en las actions.
- [ ] Play Store: ficha, política de privacidad, declaración de datos y de
      permisos (ubicación, notificaciones), prueba cerrada.

## 5. Sugerencia de orden

1. Crear la cuenta de profesional de prueba (5 min) y hacer **una sesión de
   emulador** con las verificaciones de la sección 2.
2. Redesplegar `webhookMercadoPago` y `estadoPago` (arreglo del pago tardío).
3. Arreglar el test de Didit y dejar el PR #41 en verde; decidir si se mergea.
4. Probar Mercado Pago de punta a punta con los usuarios de prueba.
5. Decidir recuperar contraseña y Room; luego R8/firma y revisión de seguridad.
