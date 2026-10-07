import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { defineSecret, defineString } from "firebase-functions/params";
import { onCall, onRequest } from "firebase-functions/v2/https";
import { conectarMercadoPagoHandler, oauthCallbackHandler, type RespuestaHttp } from "./conectarMercadoPago.js";
import { crearReservaHandler } from "./crearReserva.js";
import { iniciarPagoHandler, type DepsPago } from "./iniciarPago.js";
import { parseClave } from "./mercadopago/cifrado.js";
import { crearPasarelaMP } from "./mercadopago/pasarela.js";
import { estadoPagoHandler, retornoPagoHandler, webhookHandler } from "./pagos.js";
import { responderReservaHandler } from "./responderReserva.js";

initializeApp();

// Wrapper delgado: toda la logica vive en crearReservaHandler (probado contra el emulador).
export const crearReserva = onCall({ region: "us-central1" }, (request) =>
  crearReservaHandler(getFirestore(), request.auth?.uid, request.data, new Date()),
);

export const responderReserva = onCall({ region: "us-central1" }, (request) =>
  responderReservaHandler(getFirestore(), request.auth?.uid, request.data, new Date()),
);

// ── Mercado Pago (Marketplace + Checkout Pro) ────────────────────────────────────────────────
// Secretos: `firebase functions:secrets:set <NOMBRE>`. MP_APP_ID no es secreto (parametro).
const MP_APP_ID = defineString("MP_APP_ID");
// Pagina de autorizacion OAuth. Por defecto la del SDK; si Chile exige `.cl`, se cambia sin tocar codigo.
const MP_AUTH_HOST = defineString("MP_AUTH_HOST", { default: "https://auth.mercadopago.com" });
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
      hostAutorizacion: MP_AUTH_HOST.value(),
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
