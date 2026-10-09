import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { defineSecret, defineString } from "firebase-functions/params";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { onCall, onRequest } from "firebase-functions/v2/https";
import { calificacionTriggerHandler } from "./calificacion.js";
import { conectarMercadoPagoHandler, oauthCallbackHandler, type RespuestaHttp } from "./conectarMercadoPago.js";
import { crearReservaHandler } from "./crearReserva.js";
import { iniciarPagoHandler, type DepsPago } from "./iniciarPago.js";
import { parseClave } from "./mercadopago/cifrado.js";
import { crearPasarelaMP } from "./mercadopago/pasarela.js";
import { estadoPagoHandler, retornoPagoHandler, webhookHandler } from "./pagos.js";
import { responderReservaHandler } from "./responderReserva.js";
import { crearProveedorDidit } from "./verificacion/didit.js";
import { estadoVerificacionHandler } from "./verificacion/estadoVerificacion.js";
import { solicitarVerificacionHandler, type DepsVerificacion } from "./verificacion/solicitarVerificacion.js";
import { retornoVerificacionHandler, webhookDiditHandler } from "./verificacion/webhookDidit.js";

initializeApp();

// Wrapper delgado: toda la logica vive en crearReservaHandler (probado contra el emulador).
export const crearReserva = onCall({ region: "us-central1" }, (request) =>
  crearReservaHandler(getFirestore(), request.auth?.uid, request.data, new Date()),
);

export const responderReserva = onCall({ region: "us-central1" }, (request) =>
  responderReservaHandler(getFirestore(), request.auth?.uid, request.data, new Date()),
);

// Mantiene `profesionales.calificacionPromedio`/`totalResenas` al día cuando cambia una reseña (el
// cliente no puede escribirlos: lo prohíben las reglas). `retry`: el recálculo es idempotente, asi
// que reintentar ante una falla transitoria es seguro; un perfil inexistente no lanza.
export const recalcularCalificacion = onDocumentWritten(
  { document: "resenas/{resenaId}", region: "us-central1", retry: true },
  async (event) => {
    await calificacionTriggerHandler(getFirestore(), event.data?.before.data(), event.data?.after.data());
  },
);

// ── Mercado Pago (Marketplace + Checkout Pro) ────────────────────────────────────────────────
// Secretos: `firebase functions:secrets:set <NOMBRE>`. MP_APP_ID no es secreto (parametro).
const MP_APP_ID = defineString("MP_APP_ID");
// Pagina de autorizacion OAuth (opcional). Se lee del entorno (functions/.env.<proyecto>) y no con
// `defineString`: el CLI exige un valor para todo parametro declarado aunque tenga default.
// Probado en QA: `auth.mercadopago.com` muestra primero "Seleccione el país" (pagina intermedia) y
// `auth.mercadopago.cl` va directo a la pantalla de autorizacion de la app. KineCare opera en Chile.
const HOST_AUTORIZACION_POR_DEFECTO = "https://auth.mercadopago.cl";
const MP_CLIENT_SECRET = defineSecret("MP_CLIENT_SECRET");
const MP_WEBHOOK_SECRET = defineSecret("MP_WEBHOOK_SECRET");
const MP_TOKEN_ENCRYPTION_KEY = defineSecret("MP_TOKEN_ENCRYPTION_KEY");

const REGION = "us-central1";
const SECRETOS_MP = [MP_CLIENT_SECRET, MP_WEBHOOK_SECRET, MP_TOKEN_ENCRYPTION_KEY];

/** Origen HTTPS publico de las funciones; el proyecto sale del entorno, asi QA nunca apunta a PRD. */
function urlBase(): string {
  return `https://${REGION}-${process.env.GCLOUD_PROJECT ?? ""}.cloudfunctions.net`;
}

function depsMercadoPago(): DepsPago {
  const base = urlBase();
  return {
    // La Redirect URI debe coincidir EXACTO con la configurada en el panel de Mercado Pago.
    pasarela: crearPasarelaMP({
      appId: MP_APP_ID.value(),
      clientSecret: MP_CLIENT_SECRET.value(),
      redirectUri: `${base}/mercadoPagoOAuthCallback`,
      hostAutorizacion: process.env.MP_AUTH_HOST?.trim() || HOST_AUTORIZACION_POR_DEFECTO,
    }),
    clave: parseClave(MP_TOKEN_ENCRYPTION_KEY.value()),
    urlBase: base,
  };
}

function responder(res: { status(c: number): { send(b?: string): void }; redirect(c: number, u: string): void }, r: RespuestaHttp) {
  if (r.redirect !== undefined) res.redirect(r.status, r.redirect);
  else res.status(r.status).send(r.body);
}

export const conectarMercadoPago = onCall({ region: REGION, secrets: SECRETOS_MP }, (request) =>
  conectarMercadoPagoHandler(getFirestore(), depsMercadoPago(), request.auth?.uid, new Date()),
);

export const mercadoPagoOAuthCallback = onRequest({ region: REGION, secrets: SECRETOS_MP }, async (req, res) => {
  const q = req.query;
  responder(
    res,
    await oauthCallbackHandler(
      getFirestore(),
      depsMercadoPago(),
      {
        code: typeof q.code === "string" ? q.code : undefined,
        state: typeof q.state === "string" ? q.state : undefined,
        error: typeof q.error === "string" ? q.error : undefined,
      },
      new Date(),
    ),
  );
});

export const iniciarPago = onCall({ region: REGION, secrets: SECRETOS_MP }, (request) =>
  iniciarPagoHandler(getFirestore(), depsMercadoPago(), request.auth?.uid, request.data, new Date()),
);

export const estadoPago = onCall({ region: REGION, secrets: SECRETOS_MP }, (request) =>
  estadoPagoHandler(getFirestore(), depsMercadoPago(), request.auth?.uid, request.data, new Date()),
);

export const retornoPago = onRequest({ region: REGION }, (req, res) => {
  responder(res, retornoPagoHandler(req.query));
});

export const webhookMercadoPago = onRequest({ region: REGION, secrets: SECRETOS_MP }, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send();
    return;
  }
  responder(
    res,
    await webhookHandler(
      getFirestore(),
      depsMercadoPago(),
      MP_WEBHOOK_SECRET.value(),
      { headers: req.headers, query: req.query, body: req.body },
      new Date(),
    ),
  );
});

// ── Verificacion de identidad (Didit) ────────────────────────────────────────────────────────
// Secretos: `firebase functions:secrets:set DIDIT_API_KEY` y `DIDIT_WEBHOOK_SECRET` (el
// `secret_shared_key` del destino de webhook en la consola de Didit). El workflow id NO es secreto y
// se lee de `process.env.DIDIT_WORKFLOW_ID` (functions/.env.<proyecto>), no con `defineString`: el CLI
// exigiria un valor en todo deploy aunque la funcion no lo use (ya paso con MP_APP_ID).
const DIDIT_API_KEY = defineSecret("DIDIT_API_KEY");
const DIDIT_WEBHOOK_SECRET = defineSecret("DIDIT_WEBHOOK_SECRET");

/** Didit corta el webhook a los 5 s: la relectura de la decision debe caber con holgura. */
const TIMEOUT_WEBHOOK_DIDIT_MS = 3000;

function depsVerificacion(timeoutMs?: number): DepsVerificacion {
  return {
    proveedor: crearProveedorDidit({ apiKey: DIDIT_API_KEY.value(), timeoutMs }),
    workflowId: process.env.DIDIT_WORKFLOW_ID?.trim() ?? "",
    urlBase: urlBase(),
  };
}

export const solicitarVerificacion = onCall({ region: REGION, secrets: [DIDIT_API_KEY] }, (request) =>
  solicitarVerificacionHandler(getFirestore(), depsVerificacion(), request.auth?.uid, request.data, new Date()),
);

export const estadoVerificacion = onCall({ region: REGION, secrets: [DIDIT_API_KEY] }, (request) =>
  estadoVerificacionHandler(getFirestore(), depsVerificacion(), request.auth?.uid, request.data, new Date()),
);

export const retornoVerificacion = onRequest({ region: REGION }, (req, res) => {
  responder(res, retornoVerificacionHandler(req.query));
});

export const webhookDidit = onRequest({ region: REGION, secrets: [DIDIT_API_KEY, DIDIT_WEBHOOK_SECRET] }, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send();
    return;
  }
  responder(
    res,
    await webhookDiditHandler(
      getFirestore(),
      depsVerificacion(TIMEOUT_WEBHOOK_DIDIT_MS),
      DIDIT_WEBHOOK_SECRET.value(),
      { headers: req.headers, rawBody: req.rawBody },
      new Date(),
    ),
  );
});
