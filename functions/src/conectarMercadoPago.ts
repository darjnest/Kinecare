import { randomBytes } from "node:crypto";
import { Timestamp, type Firestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { COLECCION_ESTADOS_OAUTH, ESQUEMA_APP, OAUTH_STATE_VIGENCIA_MINUTOS } from "./constantes.js";
import { errorDeNegocio } from "./errores.js";
import { guardarCuenta } from "./mercadopago/cuentas.js";
import type { Pasarela } from "./mercadopago/pasarela.js";

const MS_MINUTO = 60_000;

export interface DepsConexion {
  pasarela: Pasarela;
  /** Clave AES-256 para cifrar los tokens del vendedor. */
  clave: Buffer;
}

export interface ResultadoConectar {
  authorizationUrl: string;
}

/** Respuesta HTTP en forma neutra, para probar los handlers `onRequest` sin Express. */
export interface RespuestaHttp {
  status: number;
  /** Si viene, se responde 302 a esta URL. */
  redirect?: string;
  body?: string;
}

/**
 * `conectarMercadoPago`: un profesional pide la URL para vincular su cuenta. Crea un `state`
 * aleatorio de un solo uso, atado a su uid y con vigencia corta, en Firestore (no en memoria:
 * las funciones corren en varias instancias).
 */
export async function conectarMercadoPagoHandler(
  db: Firestore,
  deps: DepsConexion,
  uid: string | undefined,
  ahora: Date,
): Promise<ResultadoConectar> {
  if (uid === undefined || uid === "") {
    throw errorDeNegocio("unauthenticated", "SIN_SESION", "Debes iniciar sesión para conectar Mercado Pago.");
  }
  const usuario = await db.collection("usuarios").doc(uid).get();
  if (!usuario.exists || usuario.get("rol") !== "PROFESIONAL") {
    throw errorDeNegocio("permission-denied", "ROL_INVALIDO", "Solo un profesional puede conectar una cuenta para cobrar.");
  }

  const state = randomBytes(24).toString("hex");
  await db.collection(COLECCION_ESTADOS_OAUTH).doc(state).set({
    profesionalId: uid,
    creadoEn: Timestamp.fromDate(ahora),
    expiraEn: Timestamp.fromDate(new Date(ahora.getTime() + OAUTH_STATE_VIGENCIA_MINUTOS * MS_MINUTO)),
  });
  return { authorizationUrl: deps.pasarela.urlAutorizacion(state) };
}

export interface ParametrosCallback {
  code?: string;
  state?: string;
  /** Presente si el profesional rechazo la autorizacion en Mercado Pago. */
  error?: string;
}

const redirigir = (resultado: "conectado" | "error", motivo?: string): RespuestaHttp => ({
  status: 302,
  redirect: `${ESQUEMA_APP}://mp/${resultado}${motivo === undefined ? "" : `?motivo=${motivo}`}`,
});

/** Consume el `state` exactamente una vez (lo borra en la misma transaccion que lo lee). */
async function consumirState(db: Firestore, state: string, ahora: Date): Promise<string | null> {
  const ref = db.collection(COLECCION_ESTADOS_OAUTH).doc(state);
  return db.runTransaction(async (tx) => {
    const doc = await tx.get(ref);
    if (!doc.exists) return null;
    tx.delete(ref);
    const expiraEn = doc.get("expiraEn");
    if (!(expiraEn instanceof Timestamp) || expiraEn.toMillis() <= ahora.getTime()) return null;
    return doc.get("profesionalId") as string;
  });
}

/**
 * `mercadoPagoOAuthCallback`: Mercado Pago redirige aqui con `code` y `state`. Es una
 * peticion del navegador, sin sesion de Firebase: la identidad sale del `state` que emitimos.
 * El resultado vuelve a la app por deep link (`kinecare://mp/conectado` o `.../error`).
 */
export async function oauthCallbackHandler(
  db: Firestore,
  deps: DepsConexion,
  params: ParametrosCallback,
  ahora: Date,
): Promise<RespuestaHttp> {
  const { code, state, error } = params;
  if (typeof state !== "string" || !/^[0-9a-f]{48}$/.test(state)) return redirigir("error", "estado_invalido");

  // El state se consume SIEMPRE (tambien si el profesional cancelo): un state no se reutiliza.
  const profesionalId = await consumirState(db, state, ahora);
  if (profesionalId === null) return redirigir("error", "estado_invalido");
  if (error !== undefined) return redirigir("error", "cancelado");
  if (typeof code !== "string" || code === "") return redirigir("error", "sin_codigo");

  try {
    const tokens = await deps.pasarela.canjearCodigo(code);
    await guardarCuenta(db, deps.clave, profesionalId, tokens);
  } catch (e) {
    logger.error("Mercado Pago: fallo la conexion de la cuenta", {
      profesionalId,
      mensaje: e instanceof Error ? e.message : String(e),
    });
    return redirigir("error", "pasarela");
  }
  return redirigir("conectado");
}
