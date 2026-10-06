import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";
import {
  COLECCION_MP_ESTADOS,
  COLECCION_MP_OAUTH_STATES,
  COLECCION_MP_TOKENS,
  MP_TIMEOUT_CANJE_MS,
  MP_URL_AUTORIZACION,
  MP_URL_TOKEN,
  OAUTH_STATE_TTL_MINUTOS,
} from "./constantes.js";
import { errorDeNegocio } from "./errores.js";
import { paginaDeResultado, type RespuestaHtml, type ResultadoCallback } from "./paginaMercadoPago.js";

// Conexion OAuth del profesional con Mercado Pago. Los tokens viven SOLO en
// `mercadoPagoTokens/{profesionalId}` (Admin SDK); el cliente nunca los ve ni los envia.
// Ningun log ni mensaje de error de este archivo incluye `code`, tokens, el client_secret ni el
// cuerpo de las respuestas de Mercado Pago.

export interface ConfigMercadoPago {
  clientId: string;
  clientSecret: string;
  /** Debe coincidir con la Redirect URI registrada en la aplicacion de Mercado Pago. */
  redirectUri: string;
}

/** Subconjunto de `fetch` que se usa (el `fetch` global de Node lo cumple; los tests inyectan uno falso). */
export type FetchMp = (
  url: string,
  init: { method: "POST"; headers: Record<string, string>; body: string; signal?: AbortSignal },
) => Promise<{ ok: boolean; status: number; json(): Promise<unknown> }>;

/** Largo del `state` hex (32 bytes). Tambien se usa para validar el que llega al callback. */
const STATE_REGEX = /^[0-9a-f]{64}$/;
const MAX_LARGO_CODE = 512;

// ---------------------------------------------------------------------------------------
// Piezas puras
// ---------------------------------------------------------------------------------------

export function construirUrlAutorizacion(clientId: string, redirectUri: string, state: string): string {
  const query = new URLSearchParams({
    client_id: clientId,
    response_type: "code",
    platform_id: "mp",
    state,
    redirect_uri: redirectUri,
  });
  return `${MP_URL_AUTORIZACION}?${query.toString()}`;
}

/** Un state sirve solo mientras `expiraEn` sea estrictamente posterior a `ahora`. */
export function stateVigente(expiraEn: unknown, ahora: Date): boolean {
  return expiraEn instanceof Timestamp && expiraEn.toMillis() > ahora.getTime();
}

export interface TokensMercadoPago {
  accessToken: string;
  refreshToken: string;
  /** Siempre string (MP lo entrega como numero). */
  mpUserId: string;
  publicKey: string;
  scope: string;
  liveMode: boolean;
  expiraEn: Date;
}

/** La respuesta de MP no tiene la forma esperada. El mensaje nunca incluye valores recibidos. */
export class RespuestaTokenInvalida extends Error {
  constructor(detalle: string) {
    super(`Respuesta de token de Mercado Pago inválida: ${detalle}.`);
    this.name = "RespuestaTokenInvalida";
  }
}

function textoNoVacio(valor: unknown, campo: string): string {
  if (typeof valor !== "string" || valor.trim() === "") throw new RespuestaTokenInvalida(`${campo} ausente o no es texto`);
  return valor;
}

function textoOpcional(valor: unknown, campo: string): string {
  if (valor === undefined || valor === null) return "";
  if (typeof valor !== "string") throw new RespuestaTokenInvalida(`${campo} no es texto`);
  return valor;
}

/**
 * Valida la respuesta de `POST /oauth/token`. Exige `access_token`, `refresh_token`, `user_id`
 * y `expires_in`; `public_key`, `scope` y `live_mode` son opcionales (si vienen, con su tipo).
 * Cualquier otra cosa lanza `RespuestaTokenInvalida` y no se guarda nada.
 */
export function parseRespuestaToken(cuerpo: unknown, ahora: Date): TokensMercadoPago {
  if (typeof cuerpo !== "object" || cuerpo === null || Array.isArray(cuerpo)) {
    throw new RespuestaTokenInvalida("el cuerpo no es un objeto");
  }
  const c = cuerpo as Record<string, unknown>;

  let mpUserId: string;
  if (typeof c.user_id === "number" && Number.isSafeInteger(c.user_id) && c.user_id > 0) {
    mpUserId = String(c.user_id);
  } else if (typeof c.user_id === "string" && /^\d{1,20}$/.test(c.user_id)) {
    mpUserId = c.user_id;
  } else {
    throw new RespuestaTokenInvalida("user_id ausente o inválido");
  }

  const expiresIn = c.expires_in;
  if (typeof expiresIn !== "number" || !Number.isFinite(expiresIn) || expiresIn <= 0) {
    throw new RespuestaTokenInvalida("expires_in ausente o inválido");
  }

  if (c.live_mode !== undefined && c.live_mode !== null && typeof c.live_mode !== "boolean") {
    throw new RespuestaTokenInvalida("live_mode no es booleano");
  }

  return {
    accessToken: textoNoVacio(c.access_token, "access_token"),
    refreshToken: textoNoVacio(c.refresh_token, "refresh_token"),
    mpUserId,
    publicKey: textoOpcional(c.public_key, "public_key"),
    scope: textoOpcional(c.scope, "scope"),
    liveMode: c.live_mode === true,
    expiraEn: new Date(ahora.getTime() + expiresIn * 1000),
  };
}

function sinSesion() {
  return errorDeNegocio("unauthenticated", "SIN_SESION", "Debes iniciar sesión para conectar Mercado Pago.");
}

/** El rol sale de `usuarios/{uid}`, nunca del payload ni de claims del cliente. */
async function esProfesional(db: Firestore, uid: string): Promise<boolean> {
  const usuario = await db.collection("usuarios").doc(uid).get();
  return usuario.exists && usuario.get("rol") === "PROFESIONAL";
}

async function exigirProfesional(db: Firestore, uid: string | undefined): Promise<string> {
  if (uid === undefined || uid === "") throw sinSesion();
  if (!(await esProfesional(db, uid))) {
    throw errorDeNegocio(
      "permission-denied",
      "ROL_INVALIDO",
      "Solo un profesional puede conectar una cuenta de Mercado Pago.",
    );
  }
  return uid;
}

// ---------------------------------------------------------------------------------------
// iniciarConexionMercadoPago
// ---------------------------------------------------------------------------------------

export interface DependenciasIniciar {
  config: Pick<ConfigMercadoPago, "clientId" | "redirectUri">;
  ahora: Date;
  /** 32 bytes aleatorios en hex (en produccion `crypto.randomBytes(32).toString("hex")`). */
  generarState: () => string;
}

/**
 * Crea un `state` de un solo uso para el profesional y devuelve la URL de autorizacion de
 * Mercado Pago. Borra antes los states pendientes del mismo profesional (en la misma
 * transaccion) para que no se acumulen ni haya dos flujos validos a la vez.
 */
export async function iniciarConexionMercadoPagoHandler(
  db: Firestore,
  uid: string | undefined,
  deps: DependenciasIniciar,
): Promise<{ urlAutorizacion: string }> {
  const profesionalId = await exigirProfesional(db, uid);
  const state = deps.generarState();
  if (!STATE_REGEX.test(state)) throw new Error("generarState debe devolver 32 bytes en hex");

  const estados = db.collection(COLECCION_MP_OAUTH_STATES);
  await db.runTransaction(async (tx) => {
    const pendientes = await tx.get(estados.where("profesionalId", "==", profesionalId));
    pendientes.docs.forEach((d) => tx.delete(d.ref));
    tx.set(estados.doc(state), {
      profesionalId,
      creadoEn: Timestamp.fromDate(deps.ahora),
      expiraEn: Timestamp.fromMillis(deps.ahora.getTime() + OAUTH_STATE_TTL_MINUTOS * 60_000),
    });
  });

  return { urlAutorizacion: construirUrlAutorizacion(deps.config.clientId, deps.config.redirectUri, state) };
}

// ---------------------------------------------------------------------------------------
// mercadoPagoCallback
// ---------------------------------------------------------------------------------------

export interface DependenciasCallback {
  config: ConfigMercadoPago;
  ahora: Date;
  fetch: FetchMp;
  /** Solo recibe mensajes fijos y datos no sensibles (nunca code, tokens ni secret). */
  log?: (mensaje: string, datos?: Record<string, string | number>) => void;
}

function unico(valor: unknown): string | undefined {
  return typeof valor === "string" ? valor : undefined;
}

type ConsumoState = { ok: true; profesionalId: string } | { ok: false };

/** Lee y BORRA el state en una transaccion (uso unico), sea o no valido. */
async function consumirState(db: Firestore, state: string, ahora: Date): Promise<ConsumoState> {
  const ref = db.collection(COLECCION_MP_OAUTH_STATES).doc(state);
  return db.runTransaction(async (tx): Promise<ConsumoState> => {
    const doc = await tx.get(ref);
    if (!doc.exists) return { ok: false };
    // Se devuelve (no se lanza) para que el borrado se confirme tambien cuando esta vencido.
    tx.delete(ref);
    const profesionalId = doc.get("profesionalId");
    if (typeof profesionalId !== "string" || profesionalId === "" || !stateVigente(doc.get("expiraEn"), ahora)) {
      return { ok: false };
    }
    return { ok: true, profesionalId };
  });
}

async function canjearCode(code: string, deps: DependenciasCallback): Promise<TokensMercadoPago> {
  const { config } = deps;
  const control = new AbortController();
  const timer = setTimeout(() => control.abort(), MP_TIMEOUT_CANJE_MS);
  try {
    const respuesta = await deps.fetch(MP_URL_TOKEN, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({
        client_id: config.clientId,
        client_secret: config.clientSecret,
        grant_type: "authorization_code",
        code,
        redirect_uri: config.redirectUri,
      }),
      signal: control.signal,
    });
    if (!respuesta.ok) {
      deps.log?.("Mercado Pago rechazó el canje del code", { status: respuesta.status });
      throw new RespuestaTokenInvalida(`MP respondió HTTP ${respuesta.status}`);
    }
    return parseRespuestaToken(await respuesta.json(), deps.ahora);
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Logica de `mercadoPagoCallback` (GET con `?code=&state=` o `?error=`). Siempre devuelve una
 * pagina HTML: 200 al conectar, 400 en cualquier fallo, sin revelar el motivo exacto. No lanza.
 * `query` son los parametros ya parseados; cualquier valor que no sea string se ignora.
 */
export async function mercadoPagoCallbackHandler(
  db: Firestore,
  query: Record<string, unknown>,
  deps: DependenciasCallback,
): Promise<RespuestaHtml> {
  const resultado = await procesarCallback(db, query, deps);
  return paginaDeResultado(resultado);
}

async function procesarCallback(
  db: Firestore,
  query: Record<string, unknown>,
  deps: DependenciasCallback,
): Promise<ResultadoCallback> {
  const state = unico(query.state);
  const code = unico(query.code);
  const error = unico(query.error);
  const stateBienFormado = state !== undefined && STATE_REGEX.test(state);

  try {
    // El usuario cancelo (o MP informo un error): se consume el state para que no quede vivo.
    if (error !== undefined) {
      if (stateBienFormado) await consumirState(db, state, deps.ahora);
      return error === "access_denied" ? "CANCELADO" : "FALLIDO";
    }

    if (!stateBienFormado || code === undefined || code === "" || code.length > MAX_LARGO_CODE) return "FALLIDO";

    const consumo = await consumirState(db, state, deps.ahora);
    if (!consumo.ok) {
      deps.log?.("State de Mercado Pago inexistente, vencido o ya usado");
      return "FALLIDO";
    }
    const { profesionalId } = consumo;

    if (!(await esProfesional(db, profesionalId))) {
      deps.log?.("El dueño del state ya no es profesional");
      return "FALLIDO";
    }

    const tokens = await canjearCode(code, deps);

    const batch = db.batch();
    batch.set(db.collection(COLECCION_MP_TOKENS).doc(profesionalId), {
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      mpUserId: tokens.mpUserId,
      publicKey: tokens.publicKey,
      scope: tokens.scope,
      liveMode: tokens.liveMode,
      expiraEn: Timestamp.fromDate(tokens.expiraEn),
      actualizadoEn: FieldValue.serverTimestamp(),
    });
    batch.set(db.collection(COLECCION_MP_ESTADOS).doc(profesionalId), {
      conectado: true,
      mpUserId: tokens.mpUserId,
      conectadoEn: FieldValue.serverTimestamp(),
    });
    await batch.commit();
    return "CONECTADO";
  } catch (e) {
    // Solo el tipo de error: el mensaje podria arrastrar datos del request o de la respuesta.
    deps.log?.("Fallo la conexion con Mercado Pago", { tipo: e instanceof Error ? e.name : "desconocido" });
    return "FALLIDO";
  }
}

// ---------------------------------------------------------------------------------------
// desconectarMercadoPago
// ---------------------------------------------------------------------------------------

/**
 * Borra los tokens y el estado de conexion del profesional (idempotente: borrar lo que no
 * existe no falla), y cualquier state de autorizacion todavia pendiente. No revoca la
 * autorizacion en Mercado Pago (fuera de alcance por ahora).
 */
export async function desconectarMercadoPagoHandler(
  db: Firestore,
  uid: string | undefined,
): Promise<{ desconectado: true }> {
  const profesionalId = await exigirProfesional(db, uid);
  const pendientes = await db
    .collection(COLECCION_MP_OAUTH_STATES)
    .where("profesionalId", "==", profesionalId)
    .get();

  const batch = db.batch();
  batch.delete(db.collection(COLECCION_MP_TOKENS).doc(profesionalId));
  batch.delete(db.collection(COLECCION_MP_ESTADOS).doc(profesionalId));
  pendientes.docs.forEach((d) => batch.delete(d.ref));
  await batch.commit();
  return { desconectado: true };
}
