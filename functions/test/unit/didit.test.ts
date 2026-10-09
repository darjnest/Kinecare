import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { crearProveedorDidit } from "../../src/verificacion/didit.js";
import { ProveedorVerificacionError } from "../../src/verificacion/proveedor.js";

interface Llamada {
  url: string;
  metodo: string;
  headers: Headers;
  cuerpo: unknown;
}

function proveedorCon(responder: (llamada: Llamada) => Response | Promise<Response>, timeoutMs = 1000) {
  const llamadas: Llamada[] = [];
  const fetchFn = (async (entrada: string | URL | Request, init?: RequestInit) => {
    const llamada: Llamada = {
      url: String(entrada),
      metodo: init?.method ?? "GET",
      headers: new Headers(init?.headers),
      cuerpo: typeof init?.body === "string" ? JSON.parse(init.body) : undefined,
    };
    llamadas.push(llamada);
    return responder(llamada);
  }) as typeof fetch;
  return { llamadas, proveedor: crearProveedorDidit({ apiKey: "clave-secreta", fetchFn, timeoutMs }) };
}

const json = (cuerpo: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(cuerpo), { status, headers: { "Content-Type": "application/json", ...headers } });

const DATOS = { workflowId: "11111111-2222-3333-4444-555555555555", vendorData: "pro1", callbackUrl: "https://f.test/retornoVerificacion" };

async function causaDe(promesa: Promise<unknown>) {
  try {
    await promesa;
  } catch (e) {
    assert.ok(e instanceof ProveedorVerificacionError, `se esperaba ProveedorVerificacionError, llego ${String(e)}`);
    return e;
  }
  assert.fail("se esperaba un error");
}

describe("crearSesion (Didit)", () => {
  it("hace POST a /v3/session/ con x-api-key y el cuerpo documentado, y devuelve sessionId y url", async () => {
    const { llamadas, proveedor } = proveedorCon(() =>
      json({ session_id: "sess-1", session_token: "tok", url: "https://verify.didit.me/session/tok", status: "Not Started" }, 201),
    );
    const sesion = await proveedor.crearSesion(DATOS);
    assert.deepEqual(sesion, { sessionId: "sess-1", url: "https://verify.didit.me/session/tok" });
    assert.equal(llamadas.length, 1);
    assert.equal(llamadas[0].url, "https://verification.didit.me/v3/session/");
    assert.equal(llamadas[0].metodo, "POST");
    assert.equal(llamadas[0].headers.get("x-api-key"), "clave-secreta");
    assert.equal(llamadas[0].headers.get("content-type"), "application/json");
    assert.deepEqual(llamadas[0].cuerpo, {
      workflow_id: DATOS.workflowId,
      vendor_data: "pro1",
      callback: DATOS.callbackUrl,
      callback_method: "both",
      language: "es",
    });
  });

  it("400 (validacion, workflow desconocido o creditos insuficientes) -> SOLICITUD_RECHAZADA", async () => {
    const { proveedor } = proveedorCon(() => json({ detail: "Insufficient credits" }, 400));
    const e = await causaDe(proveedor.crearSesion(DATOS));
    assert.equal(e.causa, "SOLICITUD_RECHAZADA");
    assert.equal(e.estadoHttp, 400);
    assert.ok(!e.message.includes("credits"), "el mensaje no debe copiar el cuerpo del proveedor");
  });

  it("401 y 403 -> NO_AUTORIZADO", async () => {
    for (const status of [401, 403]) {
      const { proveedor } = proveedorCon(() => json({}, status));
      assert.equal((await causaDe(proveedor.crearSesion(DATOS))).causa, "NO_AUTORIZADO");
    }
  });

  it("429 -> LIMITE con el Retry-After en segundos", async () => {
    const { proveedor } = proveedorCon(() => json({}, 429, { "Retry-After": "17" }));
    const e = await causaDe(proveedor.crearSesion(DATOS));
    assert.equal(e.causa, "LIMITE");
    assert.equal(e.reintentarDespuesSegundos, 17);
  });

  it("429 sin Retry-After, o con fecha HTTP, no informa espera; uno enorme se acota", async () => {
    const sin = await causaDe(proveedorCon(() => json({}, 429)).proveedor.crearSesion(DATOS));
    assert.equal(sin.reintentarDespuesSegundos, undefined);
    const fecha = await causaDe(proveedorCon(() => json({}, 429, { "Retry-After": "Wed, 21 Oct 2026 07:28:00 GMT" })).proveedor.crearSesion(DATOS));
    assert.equal(fecha.reintentarDespuesSegundos, undefined);
    const enorme = await causaDe(proveedorCon(() => json({}, 429, { "Retry-After": "999999" })).proveedor.crearSesion(DATOS));
    assert.equal(enorme.reintentarDespuesSegundos, 3600);
  });

  it("5xx -> SERVIDOR", async () => {
    for (const status of [500, 502, 503]) {
      const { proveedor } = proveedorCon(() => json({}, status));
      assert.equal((await causaDe(proveedor.crearSesion(DATOS))).causa, "SERVIDOR");
    }
  });

  it("timeout (la peticion no responde) -> TIMEOUT", async () => {
    // Igual que el fetch real: ante la senal de aborto rechaza con su `reason` (TimeoutError).
    const fetchFn = (async (_e: unknown, init?: RequestInit) =>
      new Promise<Response>((_res, rej) => init?.signal?.addEventListener("abort", () => rej(init.signal?.reason)))) as typeof fetch;
    const proveedor = crearProveedorDidit({ apiKey: "k", fetchFn, timeoutMs: 20 });
    assert.equal((await causaDe(proveedor.crearSesion(DATOS))).causa, "TIMEOUT");
    assert.equal((await causaDe(proveedor.obtenerDecision("s"))).causa, "TIMEOUT");
  });

  it("error de red -> RED", async () => {
    const { proveedor } = proveedorCon(() => Promise.reject(new TypeError("fetch failed")));
    assert.equal((await causaDe(proveedor.crearSesion(DATOS))).causa, "RED");
  });

  it("2xx con cuerpo incompleto o no JSON -> RESPUESTA_INVALIDA", async () => {
    assert.equal((await causaDe(proveedorCon(() => json({ session_id: "s" }, 201)).proveedor.crearSesion(DATOS))).causa, "RESPUESTA_INVALIDA");
    assert.equal((await causaDe(proveedorCon(() => new Response("<html>", { status: 200 })).proveedor.crearSesion(DATOS))).causa, "RESPUESTA_INVALIDA");
  });
});

describe("obtenerDecision (Didit)", () => {
  const decision = (extra: Record<string, unknown> = {}) => ({
    session_id: "sess-1",
    status: "Approved",
    vendor_data: "pro1",
    id_verifications: [
      { status: "Approved", document_type: "ID", document_number: "A123", personal_number: "12.345.678-5", first_name: "X", last_name: "Y", date_of_birth: "1990-01-01" },
    ],
    liveness_checks: [{ status: "Approved", score: 99 }],
    face_matches: [{ status: "Approved", score: 98 }],
    ...extra,
  });

  it("hace GET a /v3/session/{id}/decision/ y devuelve solo estado y RUN (descarta el resto)", async () => {
    const { llamadas, proveedor } = proveedorCon(() => json(decision()));
    const r = await proveedor.obtenerDecision("sess-1");
    assert.deepEqual(r, { estado: "Approved", rut: "12.345.678-5" });
    assert.equal(llamadas[0].url, "https://verification.didit.me/v3/session/sess-1/decision/");
    assert.equal(llamadas[0].metodo, "GET");
    assert.equal(llamadas[0].headers.get("x-api-key"), "clave-secreta");
    assert.ok(!JSON.stringify(r).includes("X"), "no debe filtrarse ningun otro dato de la decision");
  });

  it("codifica el id en la URL", async () => {
    const { llamadas, proveedor } = proveedorCon(() => json(decision()));
    await proveedor.obtenerDecision("a/b?c");
    assert.equal(llamadas[0].url, "https://verification.didit.me/v3/session/a%2Fb%3Fc/decision/");
  });

  it("bloques nulos, ausentes o vacios -> rut null", async () => {
    for (const extra of [{ id_verifications: null }, { id_verifications: [] }, { id_verifications: undefined }, { id_verifications: [{ personal_number: null }] }, { id_verifications: [{ personal_number: "  " }] }]) {
      const { proveedor } = proveedorCon(() => json(decision(extra)));
      assert.deepEqual(await proveedor.obtenerDecision("s"), { estado: "Approved", rut: null });
    }
  });

  it("toma personal_number de la primera id_verification, nunca document_number", async () => {
    const { proveedor } = proveedorCon(() =>
      json(decision({ id_verifications: [{ document_number: "999999999", personal_number: null }, { personal_number: "1-9" }] })),
    );
    assert.equal((await proveedor.obtenerDecision("s")).rut, null);
  });

  it("404 con HTML (id mal formado) -> NO_ENCONTRADO sin intentar parsear", async () => {
    const { proveedor } = proveedorCon(() => new Response("<html><body>Not Found</body></html>", { status: 404, headers: { "Content-Type": "text/html" } }));
    assert.equal((await causaDe(proveedor.obtenerDecision("???"))).causa, "NO_ENCONTRADO");
  });

  it("401, 429, 5xx y respuesta sin status se clasifican", async () => {
    assert.equal((await causaDe(proveedorCon(() => json({}, 401)).proveedor.obtenerDecision("s"))).causa, "NO_AUTORIZADO");
    assert.equal((await causaDe(proveedorCon(() => json({}, 429, { "Retry-After": "2" })).proveedor.obtenerDecision("s"))).reintentarDespuesSegundos, 2);
    assert.equal((await causaDe(proveedorCon(() => json({}, 503)).proveedor.obtenerDecision("s"))).causa, "SERVIDOR");
    assert.equal((await causaDe(proveedorCon(() => json({ session_id: "s" })).proveedor.obtenerDecision("s"))).causa, "RESPUESTA_INVALIDA");
  });
});
