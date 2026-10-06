// Integracion del flujo OAuth de Mercado Pago contra el Firestore Emulator (Admin SDK real) con
// un `fetch` falso para el canje del token. Ejecutar con: npm run test:emulator.
import assert from "node:assert/strict";
import { before, beforeEach, describe, it } from "node:test";
import { initializeApp } from "firebase-admin/app";
import { Timestamp, getFirestore, type Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import {
  desconectarMercadoPagoHandler,
  iniciarConexionMercadoPagoHandler,
  mercadoPagoCallbackHandler,
  type ConfigMercadoPago,
  type FetchMp,
} from "../../src/mercadoPago.js";
import { esperarError } from "../helpers.js";

const PROJECT_ID = "demo-kinecare";
const emulador = process.env.FIRESTORE_EMULATOR_HOST;
if (emulador === undefined) {
  throw new Error(
    "FIRESTORE_EMULATOR_HOST no esta definido: corre `npm run test:emulator` (o `npm run test:unit` para omitir estos tests).",
  );
}

const AHORA = new Date("2026-10-01T12:00:00Z");
const CLIENTE = "cliente1";
const PRO = "pro1";
const OTRO_PRO = "pro2";

const CLIENT_SECRET = "SECRETO-DE-LA-APP-MP";
const ACCESS_TOKEN = "APP_USR-token-de-acceso-1234";
const REFRESH_TOKEN = "TG-token-de-refresco-5678";
const CODE = "TG-codigo-de-un-solo-uso-9999";
const CONFIG: ConfigMercadoPago = {
  clientId: "CLIENT123",
  clientSecret: CLIENT_SECRET,
  redirectUri: "https://us-central1-demo-kinecare.cloudfunctions.net/mercadoPagoCallback",
};
const VALORES_SENSIBLES = [CLIENT_SECRET, ACCESS_TOKEN, REFRESH_TOKEN, CODE];

const TOKEN_MP = {
  access_token: ACCESS_TOKEN,
  refresh_token: REFRESH_TOKEN,
  user_id: 987654321,
  expires_in: 15552000,
  scope: "offline_access read write",
  public_key: "APP_USR-publica",
  live_mode: true,
};

let db: Firestore;
let contador = 0;

interface LlamadaFetch {
  url: string;
  init: Parameters<FetchMp>[1];
}

/** Fetch falso: registra las llamadas y responde `status`/`cuerpo`, o lanza si `fallaRed`. */
function fetchFalso(opciones: { status?: number; cuerpo?: unknown; fallaRed?: boolean } = {}) {
  const llamadas: LlamadaFetch[] = [];
  const fetchFn: FetchMp = async (url, init) => {
    llamadas.push({ url, init });
    if (opciones.fallaRed) throw new Error(`ECONNRESET ${CLIENT_SECRET} ${CODE}`);
    const status = opciones.status ?? 200;
    return { ok: status >= 200 && status < 300, status, json: async () => ("cuerpo" in opciones ? opciones.cuerpo : TOKEN_MP) };
  };
  return { fetchFn, llamadas };
}

function generarState(): string {
  contador += 1;
  return contador.toString(16).padStart(64, "0");
}

async function limpiarEmulador() {
  const r = await fetch(`http://${emulador}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, {
    method: "DELETE",
  });
  assert.equal(r.status, 200);
}

async function sembrarUsuarios() {
  await db.collection("usuarios").doc(CLIENTE).set({ rol: "CLIENTE", nombre: "Cata Cliente" });
  await db.collection("usuarios").doc(PRO).set({ rol: "PROFESIONAL", nombre: "Pro Uno" });
  await db.collection("usuarios").doc(OTRO_PRO).set({ rol: "PROFESIONAL", nombre: "Pro Dos" });
}

function iniciar(uid: string | undefined = PRO, ahora = AHORA) {
  return iniciarConexionMercadoPagoHandler(db, uid, { config: CONFIG, ahora, generarState });
}

/** Inicia el flujo y devuelve el state de la URL de autorizacion. */
async function iniciarYObtenerState(uid = PRO, ahora = AHORA): Promise<string> {
  const { urlAutorizacion } = await iniciar(uid, ahora);
  const state = new URL(urlAutorizacion).searchParams.get("state");
  assert.ok(state);
  return state;
}

function callback(
  query: Record<string, unknown>,
  fetchFn: FetchMp,
  ahora = AHORA,
  log: (m: string, d?: Record<string, string | number>) => void = () => {},
) {
  return mercadoPagoCallbackHandler(db, query, { config: CONFIG, ahora, fetch: fetchFn, log });
}

async function datos(coleccion: string, id: string) {
  return (await db.collection(coleccion).doc(id).get()).data();
}

async function contar(coleccion: string): Promise<number> {
  return (await db.collection(coleccion).get()).size;
}

function sinSecretos(texto: string) {
  for (const valor of VALORES_SENSIBLES) assert.ok(!texto.includes(valor), `aparece un valor sensible: ${valor}`);
}

before(() => {
  initializeApp({ projectId: PROJECT_ID });
  db = getFirestore();
});

beforeEach(async () => {
  await limpiarEmulador();
  await sembrarUsuarios();
});

describe("iniciarConexionMercadoPago", () => {
  it("devuelve la URL de autorizacion y guarda el state con TTL de 10 minutos", async () => {
    const { urlAutorizacion } = await iniciar();
    const url = new URL(urlAutorizacion);
    assert.equal(`${url.origin}${url.pathname}`, "https://auth.mercadopago.cl/authorization");
    assert.equal(url.searchParams.get("client_id"), "CLIENT123");
    assert.equal(url.searchParams.get("response_type"), "code");
    assert.equal(url.searchParams.get("platform_id"), "mp");
    assert.equal(url.searchParams.get("redirect_uri"), CONFIG.redirectUri);

    const state = url.searchParams.get("state")!;
    const doc = await datos("mercadoPagoOAuthStates", state);
    assert.ok(doc);
    assert.equal(doc.profesionalId, PRO);
    assert.ok(doc.creadoEn instanceof Timestamp);
    assert.equal(doc.creadoEn.toMillis(), AHORA.getTime());
    assert.equal(doc.expiraEn.toMillis(), AHORA.getTime() + 10 * 60_000);
    assert.ok(!urlAutorizacion.includes(CLIENT_SECRET));
  });

  it("un segundo iniciar borra el state anterior del mismo profesional", async () => {
    const primero = await iniciarYObtenerState();
    const segundo = await iniciarYObtenerState();
    assert.notEqual(primero, segundo);
    assert.equal(await datos("mercadoPagoOAuthStates", primero), undefined);
    assert.ok(await datos("mercadoPagoOAuthStates", segundo));
    assert.equal(await contar("mercadoPagoOAuthStates"), 1);
  });

  it("no toca los states de otros profesionales", async () => {
    const deOtro = await iniciarYObtenerState(OTRO_PRO);
    await iniciarYObtenerState(PRO);
    await iniciarYObtenerState(PRO);
    assert.ok(await datos("mercadoPagoOAuthStates", deOtro));
    assert.equal(await contar("mercadoPagoOAuthStates"), 2);
  });

  it("SIN_SESION sin uid", async () => {
    // Directo al handler: `iniciar(undefined)` tomaria el uid por defecto.
    await esperarError(
      () => iniciarConexionMercadoPagoHandler(db, undefined, { config: CONFIG, ahora: AHORA, generarState }),
      "unauthenticated",
      "SIN_SESION",
    );
    await esperarError(() => iniciar(""), "unauthenticated", "SIN_SESION");
    assert.equal(await contar("mercadoPagoOAuthStates"), 0);
  });

  it("ROL_INVALIDO si es cliente o no tiene usuario", async () => {
    await esperarError(() => iniciar(CLIENTE), "permission-denied", "ROL_INVALIDO");
    await esperarError(() => iniciar("fantasma"), "permission-denied", "ROL_INVALIDO");
    assert.equal(await contar("mercadoPagoOAuthStates"), 0);
  });
});

describe("mercadoPagoCallback: camino feliz", () => {
  it("iniciar -> callback guarda tokens y estado, consume el state y responde 200", async () => {
    const state = await iniciarYObtenerState();
    const { fetchFn, llamadas } = fetchFalso();
    const r = await callback({ code: CODE, state }, fetchFn);

    assert.equal(r.status, 200);
    assert.ok(r.html.includes("Cuenta conectada"));
    sinSecretos(r.html);

    // Canje: endpoint, metodo y cuerpo segun el contrato.
    assert.equal(llamadas.length, 1);
    assert.equal(llamadas[0].url, "https://api.mercadopago.com/oauth/token");
    assert.equal(llamadas[0].init.method, "POST");
    assert.equal(llamadas[0].init.headers.Accept, "application/json");
    assert.deepEqual(JSON.parse(llamadas[0].init.body), {
      client_id: "CLIENT123",
      client_secret: CLIENT_SECRET,
      grant_type: "authorization_code",
      code: CODE,
      redirect_uri: CONFIG.redirectUri,
    });

    const tokens = (await datos("mercadoPagoTokens", PRO))!;
    assert.equal(tokens.accessToken, ACCESS_TOKEN);
    assert.equal(tokens.refreshToken, REFRESH_TOKEN);
    assert.equal(tokens.mpUserId, "987654321");
    assert.equal(tokens.publicKey, "APP_USR-publica");
    assert.equal(tokens.scope, "offline_access read write");
    assert.equal(tokens.liveMode, true);
    assert.ok(tokens.expiraEn instanceof Timestamp);
    assert.equal(tokens.expiraEn.toMillis(), AHORA.getTime() + 15552000 * 1000);
    assert.ok(tokens.actualizadoEn instanceof Timestamp);

    const estado = (await datos("mercadoPagoEstados", PRO))!;
    assert.equal(estado.conectado, true);
    assert.equal(estado.mpUserId, "987654321");
    assert.ok(estado.conectadoEn instanceof Timestamp);
    // El estado publico no lleva ningun token.
    assert.deepEqual(Object.keys(estado).sort(), ["conectadoEn", "conectado", "mpUserId"].sort());

    assert.equal(await datos("mercadoPagoOAuthStates", state), undefined);
  });

  it("reconectar reemplaza los tokens anteriores", async () => {
    const s1 = await iniciarYObtenerState();
    await callback({ code: CODE, state: s1 }, fetchFalso().fetchFn);
    const s2 = await iniciarYObtenerState();
    const nuevo = { ...TOKEN_MP, access_token: "APP_USR-nuevo", user_id: 555 };
    await callback({ code: "otro", state: s2 }, fetchFalso({ cuerpo: nuevo }).fetchFn);
    assert.equal((await datos("mercadoPagoTokens", PRO))!.accessToken, "APP_USR-nuevo");
    assert.equal((await datos("mercadoPagoEstados", PRO))!.mpUserId, "555");
  });

  it("acepta user_id como texto y lo guarda como string", async () => {
    const state = await iniciarYObtenerState();
    await callback({ code: CODE, state }, fetchFalso({ cuerpo: { ...TOKEN_MP, user_id: "123" } }).fetchFn);
    assert.equal((await datos("mercadoPagoTokens", PRO))!.mpUserId, "123");
  });
});

describe("mercadoPagoCallback: state", () => {
  it("state reutilizado: el segundo callback falla y no pisa nada", async () => {
    const state = await iniciarYObtenerState();
    const ok = await callback({ code: CODE, state }, fetchFalso().fetchFn);
    assert.equal(ok.status, 200);
    const antes = await datos("mercadoPagoTokens", PRO);

    const distinto = fetchFalso({ cuerpo: { ...TOKEN_MP, access_token: "OTRO-TOKEN", user_id: 1 } });
    const repetido = await callback({ code: "otro-code", state }, distinto.fetchFn);
    assert.equal(repetido.status, 400);
    assert.equal(distinto.llamadas.length, 0, "no debe llamar a Mercado Pago con un state ya usado");
    assert.deepEqual(await datos("mercadoPagoTokens", PRO), antes);
    assert.equal((await datos("mercadoPagoEstados", PRO))!.mpUserId, "987654321");
  });

  it("state expirado: 400, no llama a MP, no escribe y el state queda borrado", async () => {
    const state = await iniciarYObtenerState();
    const { fetchFn, llamadas } = fetchFalso();
    const pasadoElTTL = new Date(AHORA.getTime() + 10 * 60_000);
    const r = await callback({ code: CODE, state }, fetchFn, pasadoElTTL);
    assert.equal(r.status, 400);
    assert.ok(r.html.includes("No pudimos conectar"));
    assert.equal(llamadas.length, 0);
    assert.equal(await contar("mercadoPagoTokens"), 0);
    assert.equal(await contar("mercadoPagoEstados"), 0);
    assert.equal(await datos("mercadoPagoOAuthStates", state), undefined);
  });

  it("state justo antes de expirar todavia sirve", async () => {
    const state = await iniciarYObtenerState();
    const r = await callback({ code: CODE, state }, fetchFalso().fetchFn, new Date(AHORA.getTime() + 10 * 60_000 - 1));
    assert.equal(r.status, 200);
  });

  it("state inexistente: 400 y no llama a MP", async () => {
    const { fetchFn, llamadas } = fetchFalso();
    const r = await callback({ code: CODE, state: "f".repeat(64) }, fetchFn);
    assert.equal(r.status, 400);
    assert.equal(llamadas.length, 0);
    assert.equal(await contar("mercadoPagoTokens"), 0);
  });

  it("parametros ausentes o mal formados: 400 sin llamar a MP", async () => {
    const state = await iniciarYObtenerState();
    const { fetchFn, llamadas } = fetchFalso();
    const casos: Array<Record<string, unknown>> = [
      {},
      { code: CODE },
      { state },
      { code: "", state },
      { code: CODE, state: "../usuarios/pro1" },
      { code: CODE, state: "no-es-hex" },
      { code: [CODE], state },
      { code: { a: 1 }, state },
      { code: CODE, state: [state] },
      { code: "x".repeat(2000), state },
    ];
    for (const q of casos) assert.equal((await callback(q, fetchFn)).status, 400);
    assert.equal(llamadas.length, 0);
    // Ninguno de esos intentos consumio el state valido.
    assert.ok(await datos("mercadoPagoOAuthStates", state));
  });

  it("si el dueno del state ya no es profesional: 400 y no se escribe nada", async () => {
    const state = await iniciarYObtenerState();
    await db.collection("usuarios").doc(PRO).update({ rol: "CLIENTE" });
    const { fetchFn, llamadas } = fetchFalso();
    const r = await callback({ code: CODE, state }, fetchFn);
    assert.equal(r.status, 400);
    assert.equal(llamadas.length, 0);
    assert.equal(await contar("mercadoPagoTokens"), 0);
    assert.equal(await datos("mercadoPagoOAuthStates", state), undefined);
  });

  it("callbacks simultaneos con el mismo state: solo uno canjea", async () => {
    const state = await iniciarYObtenerState();
    const { fetchFn, llamadas } = fetchFalso();
    const rs = await Promise.all(Array.from({ length: 5 }, () => callback({ code: CODE, state }, fetchFn)));
    assert.equal(rs.filter((r) => r.status === 200).length, 1);
    assert.equal(llamadas.length, 1);
  });
});

describe("mercadoPagoCallback: errores", () => {
  it("error=access_denied: 400 'cancelaste', no llama a MP y consume el state", async () => {
    const state = await iniciarYObtenerState();
    const { fetchFn, llamadas } = fetchFalso();
    const r = await callback({ error: "access_denied", state }, fetchFn);
    assert.equal(r.status, 400);
    assert.ok(r.html.includes("Cancelaste"));
    assert.equal(llamadas.length, 0);
    assert.equal(await contar("mercadoPagoTokens"), 0);
    assert.equal(await datos("mercadoPagoOAuthStates", state), undefined);
  });

  it("error=access_denied sin state tambien responde 'cancelaste'", async () => {
    const r = await callback({ error: "access_denied" }, fetchFalso().fetchFn);
    assert.equal(r.status, 400);
    assert.ok(r.html.includes("Cancelaste"));
  });

  it("otro error de MP: 400 'no pudimos conectar' aunque venga un code", async () => {
    const state = await iniciarYObtenerState();
    const { fetchFn, llamadas } = fetchFalso();
    const r = await callback({ error: "server_error", code: CODE, state }, fetchFn);
    assert.equal(r.status, 400);
    assert.ok(r.html.includes("No pudimos conectar"));
    assert.equal(llamadas.length, 0);
    assert.equal(await contar("mercadoPagoTokens"), 0);
  });

  it("el valor de error del request no se refleja en el HTML", async () => {
    const r = await callback({ error: "<script>alert(1)</script>" }, fetchFalso().fetchFn);
    assert.ok(!r.html.includes("alert(1)"));
  });

  it("canje rechazado por MP: 400, nada escrito y el state queda consumido", async () => {
    const state = await iniciarYObtenerState();
    const registro: string[] = [];
    const { fetchFn, llamadas } = fetchFalso({
      status: 400,
      cuerpo: { error: "invalid_grant", detalle: `${CODE} ${CLIENT_SECRET}` },
    });
    const r = await callback({ code: CODE, state }, fetchFn, AHORA, (m, d) => registro.push(m + JSON.stringify(d ?? {})));
    assert.equal(r.status, 400);
    assert.ok(r.html.includes("No pudimos conectar"));
    assert.equal(llamadas.length, 1);
    assert.equal(await contar("mercadoPagoTokens"), 0);
    assert.equal(await contar("mercadoPagoEstados"), 0);
    assert.equal(await datos("mercadoPagoOAuthStates", state), undefined);
    sinSecretos(r.html);
    sinSecretos(registro.join("\n"));
  });

  it("fallo de red: 400, nada escrito y ni la respuesta ni los logs filtran secretos", async () => {
    const state = await iniciarYObtenerState();
    const registro: string[] = [];
    const r = await callback({ code: CODE, state }, fetchFalso({ fallaRed: true }).fetchFn, AHORA, (m, d) =>
      registro.push(m + JSON.stringify(d ?? {})),
    );
    assert.equal(r.status, 400);
    assert.equal(await contar("mercadoPagoTokens"), 0);
    assert.equal(await datos("mercadoPagoOAuthStates", state), undefined);
    sinSecretos(r.html);
    sinSecretos(registro.join("\n"));
  });

  it("respuesta de token malformada: 400 y no se guarda nada", async () => {
    const invalidas: unknown[] = [
      { ...TOKEN_MP, access_token: undefined },
      { ...TOKEN_MP, refresh_token: 5 },
      { ...TOKEN_MP, user_id: "abc" },
      { ...TOKEN_MP, expires_in: "mucho" },
      "no es objeto",
      null,
    ];
    for (const cuerpo of invalidas) {
      const state = await iniciarYObtenerState();
      const registro: string[] = [];
      const r = await callback({ code: CODE, state }, fetchFalso({ cuerpo }).fetchFn, AHORA, (m, d) =>
        registro.push(m + JSON.stringify(d ?? {})),
      );
      assert.equal(r.status, 400);
      assert.equal(await contar("mercadoPagoTokens"), 0);
      assert.equal(await contar("mercadoPagoEstados"), 0);
      sinSecretos(registro.join("\n"));
    }
  });

  it("MP responde 200 con JSON ilegible: 400 y no se guarda nada", async () => {
    const state = await iniciarYObtenerState();
    const fetchFn: FetchMp = async () => ({
      ok: true,
      status: 200,
      json: async () => {
        throw new SyntaxError(`Unexpected token ${ACCESS_TOKEN}`);
      },
    });
    const r = await callback({ code: CODE, state }, fetchFn);
    assert.equal(r.status, 400);
    assert.equal(await contar("mercadoPagoTokens"), 0);
    sinSecretos(r.html);
  });
});

describe("desconectarMercadoPago", () => {
  async function conectar(uid = PRO) {
    const state = await iniciarYObtenerState(uid);
    const r = await callback({ code: CODE, state }, fetchFalso().fetchFn);
    assert.equal(r.status, 200);
  }

  it("borra tokens y estado del profesional y devuelve { desconectado: true }", async () => {
    await conectar();
    assert.deepEqual(await desconectarMercadoPagoHandler(db, PRO), { desconectado: true });
    assert.equal(await datos("mercadoPagoTokens", PRO), undefined);
    assert.equal(await datos("mercadoPagoEstados", PRO), undefined);
  });

  it("es idempotente: sin conexion previa tambien responde ok, y se puede repetir", async () => {
    assert.deepEqual(await desconectarMercadoPagoHandler(db, PRO), { desconectado: true });
    assert.deepEqual(await desconectarMercadoPagoHandler(db, PRO), { desconectado: true });
  });

  it("no toca la conexion de otro profesional", async () => {
    await conectar(PRO);
    await conectar(OTRO_PRO);
    await desconectarMercadoPagoHandler(db, PRO);
    assert.ok(await datos("mercadoPagoTokens", OTRO_PRO));
    assert.ok(await datos("mercadoPagoEstados", OTRO_PRO));
  });

  it("descarta un state pendiente: despues de desconectar no se puede completar ese flujo", async () => {
    const state = await iniciarYObtenerState();
    await desconectarMercadoPagoHandler(db, PRO);
    const r = await callback({ code: CODE, state }, fetchFalso().fetchFn);
    assert.equal(r.status, 400);
    assert.equal(await contar("mercadoPagoTokens"), 0);
  });

  it("SIN_SESION sin uid", async () => {
    await esperarError(() => desconectarMercadoPagoHandler(db, undefined), "unauthenticated", "SIN_SESION");
    await esperarError(() => desconectarMercadoPagoHandler(db, ""), "unauthenticated", "SIN_SESION");
  });

  it("ROL_INVALIDO si es cliente y no borra nada", async () => {
    await conectar();
    await esperarError(() => desconectarMercadoPagoHandler(db, CLIENTE), "permission-denied", "ROL_INVALIDO");
    await esperarError(() => desconectarMercadoPagoHandler(db, "fantasma"), "permission-denied", "ROL_INVALIDO");
    assert.ok(await datos("mercadoPagoTokens", PRO));
  });
});

describe("secretos fuera de errores y respuestas", () => {
  it("los errores de iniciar/desconectar son HttpsError sin tokens ni secret", async () => {
    for (const fn of [() => iniciar(CLIENTE), () => desconectarMercadoPagoHandler(db, CLIENTE)]) {
      try {
        await fn();
        assert.fail("debio fallar");
      } catch (e) {
        assert.ok(e instanceof HttpsError);
        sinSecretos(JSON.stringify({ message: e.message, details: e.details }));
      }
    }
  });
});
