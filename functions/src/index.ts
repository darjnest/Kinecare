import { randomBytes } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/logger";
import { defineSecret, defineString } from "firebase-functions/params";
import { onCall, onRequest } from "firebase-functions/v2/https";
import { REGION_FUNCTIONS, urlCallbackMercadoPago } from "./constantes.js";
import { crearReservaHandler } from "./crearReserva.js";
import {
  desconectarMercadoPagoHandler,
  iniciarConexionMercadoPagoHandler,
  mercadoPagoCallbackHandler,
} from "./mercadoPago.js";
import { CABECERAS_HTML, paginaDeResultado } from "./paginaMercadoPago.js";
import { responderReservaHandler } from "./responderReserva.js";

initializeApp();

// Wrapper delgado: toda la logica vive en crearReservaHandler (probado contra el emulador).
export const crearReserva = onCall({ region: "us-central1" }, (request) =>
  crearReservaHandler(getFirestore(), request.auth?.uid, request.data, new Date()),
);

export const responderReserva = onCall({ region: "us-central1" }, (request) =>
  responderReservaHandler(getFirestore(), request.auth?.uid, request.data, new Date()),
);

// --- Mercado Pago (OAuth del profesional) --------------------------------------------------
// MP_CLIENT_ID es un parametro (functions:config / .env); MP_CLIENT_SECRET es un secreto de
// Secret Manager (`firebase functions:secrets:set MP_CLIENT_SECRET`). Sus valores solo se leen
// dentro de los handlers, nunca al cargar el modulo.
const MP_CLIENT_ID = defineString("MP_CLIENT_ID");
const MP_CLIENT_SECRET = defineSecret("MP_CLIENT_SECRET");

function redirectUri(): string {
  const projectId = process.env.GCLOUD_PROJECT;
  if (projectId === undefined || projectId === "") throw new Error("GCLOUD_PROJECT no esta definido");
  return urlCallbackMercadoPago(projectId);
}

export const iniciarConexionMercadoPago = onCall({ region: REGION_FUNCTIONS }, (request) =>
  iniciarConexionMercadoPagoHandler(getFirestore(), request.auth?.uid, {
    config: { clientId: MP_CLIENT_ID.value(), redirectUri: redirectUri() },
    ahora: new Date(),
    generarState: () => randomBytes(32).toString("hex"),
  }),
);

export const mercadoPagoCallback = onRequest(
  { region: REGION_FUNCTIONS, secrets: [MP_CLIENT_SECRET] },
  async (req, res) => {
    res.set(CABECERAS_HTML);
    if (req.method !== "GET") {
      res.set("Allow", "GET");
      res.status(405).send("Método no permitido");
      return;
    }
    let respuesta;
    try {
      respuesta = await mercadoPagoCallbackHandler(getFirestore(), req.query, {
        config: { clientId: MP_CLIENT_ID.value(), clientSecret: MP_CLIENT_SECRET.value(), redirectUri: redirectUri() },
        ahora: new Date(),
        fetch,
        log: (mensaje, datos) => logger.warn(mensaje, datos ?? {}),
      });
    } catch (e) {
      // Configuracion ausente u otro fallo inesperado: solo el tipo, sin detalles.
      logger.error("mercadoPagoCallback fallo", { tipo: e instanceof Error ? e.name : "desconocido" });
      respuesta = paginaDeResultado("FALLIDO");
    }
    res.status(respuesta.status).send(respuesta.html);
  },
);

export const desconectarMercadoPago = onCall({ region: REGION_FUNCTIONS }, (request) =>
  desconectarMercadoPagoHandler(getFirestore(), request.auth?.uid),
);
