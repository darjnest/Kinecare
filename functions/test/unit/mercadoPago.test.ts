import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { Timestamp } from "firebase-admin/firestore";
import { MP_URL_AUTORIZACION, urlCallbackMercadoPago } from "../../src/constantes.js";
import {
  RespuestaTokenInvalida,
  construirUrlAutorizacion,
  parseRespuestaToken,
  stateVigente,
} from "../../src/mercadoPago.js";

const AHORA = new Date("2026-10-01T12:00:00Z");
const STATE = "ab".repeat(32);

const TOKEN_OK = {
  access_token: "APP_USR-acceso-secreto",
  refresh_token: "TG-refresco-secreto",
  user_id: 123456789,
  expires_in: 15552000,
  scope: "offline_access read write",
  public_key: "APP_USR-publica",
  live_mode: true,
};

describe("urlCallbackMercadoPago", () => {
  it("apunta a la funcion mercadoPagoCallback del proyecto en us-central1", () => {
    assert.equal(
      urlCallbackMercadoPago("kinecare-cl-qa"),
      "https://us-central1-kinecare-cl-qa.cloudfunctions.net/mercadoPagoCallback",
    );
  });
});

describe("construirUrlAutorizacion", () => {
  const redirect = "https://us-central1-kinecare-cl-qa.cloudfunctions.net/mercadoPagoCallback";

  it("arma la URL con todos los parametros y la redirect_uri codificada", () => {
    const url = construirUrlAutorizacion("CLIENT123", redirect, STATE);
    assert.ok(url.startsWith(`${MP_URL_AUTORIZACION}?`));
    assert.equal(
      url,
      `https://auth.mercadopago.cl/authorization?client_id=CLIENT123&response_type=code&platform_id=mp&state=${STATE}` +
        `&redirect_uri=https%3A%2F%2Fus-central1-kinecare-cl-qa.cloudfunctions.net%2FmercadoPagoCallback`,
    );
  });

  it("los parametros se recuperan intactos al parsear la URL", () => {
    const params = new URL(construirUrlAutorizacion("CLIENT123", redirect, STATE)).searchParams;
    assert.equal(params.get("client_id"), "CLIENT123");
    assert.equal(params.get("response_type"), "code");
    assert.equal(params.get("platform_id"), "mp");
    assert.equal(params.get("state"), STATE);
    assert.equal(params.get("redirect_uri"), redirect);
  });

  it("codifica caracteres especiales (no se puede inyectar otro parametro)", () => {
    const url = construirUrlAutorizacion("a&b=c d", "https://x.cl/cb?z=1&y=2", STATE);
    const params = new URL(url).searchParams;
    assert.equal(params.get("client_id"), "a&b=c d");
    assert.equal(params.get("redirect_uri"), "https://x.cl/cb?z=1&y=2");
    assert.equal([...params.keys()].length, 5);
  });
});

describe("stateVigente", () => {
  const en = (ms: number) => Timestamp.fromMillis(AHORA.getTime() + ms);

  it("vigente si expira despues de ahora", () => {
    assert.equal(stateVigente(en(1), AHORA), true);
    assert.equal(stateVigente(en(10 * 60_000), AHORA), true);
  });

  it("vencido exactamente en el instante de expiracion y despues", () => {
    assert.equal(stateVigente(en(0), AHORA), false);
    assert.equal(stateVigente(en(-1), AHORA), false);
  });

  it("dato corrupto (no Timestamp) se trata como vencido", () => {
    assert.equal(stateVigente(undefined, AHORA), false);
    assert.equal(stateVigente(null, AHORA), false);
    assert.equal(stateVigente(AHORA.getTime() + 1000, AHORA), false);
    assert.equal(stateVigente("2099-01-01", AHORA), false);
  });
});

describe("parseRespuestaToken", () => {
  it("acepta la respuesta completa y calcula expiraEn = ahora + expires_in", () => {
    const t = parseRespuestaToken(TOKEN_OK, AHORA);
    assert.deepEqual(t, {
      accessToken: "APP_USR-acceso-secreto",
      refreshToken: "TG-refresco-secreto",
      mpUserId: "123456789",
      publicKey: "APP_USR-publica",
      scope: "offline_access read write",
      liveMode: true,
      expiraEn: new Date(AHORA.getTime() + 15552000 * 1000),
    });
  });

  it("user_id numerico o texto numerico se guardan como string", () => {
    assert.equal(parseRespuestaToken({ ...TOKEN_OK, user_id: 42 }, AHORA).mpUserId, "42");
    assert.equal(parseRespuestaToken({ ...TOKEN_OK, user_id: "42" }, AHORA).mpUserId, "42");
  });

  it("public_key, scope y live_mode son opcionales con valores por defecto", () => {
    const { public_key: _p, scope: _s, live_mode: _l, ...minimo } = TOKEN_OK;
    const t = parseRespuestaToken(minimo, AHORA);
    assert.equal(t.publicKey, "");
    assert.equal(t.scope, "");
    assert.equal(t.liveMode, false);
  });

  for (const campo of ["access_token", "refresh_token", "user_id", "expires_in"] as const) {
    it(`rechaza si falta ${campo}`, () => {
      const cuerpo: Record<string, unknown> = { ...TOKEN_OK };
      delete cuerpo[campo];
      assert.throws(() => parseRespuestaToken(cuerpo, AHORA), RespuestaTokenInvalida);
    });
  }

  const raros: Array<[string, Record<string, unknown>]> = [
    ["access_token numerico", { access_token: 123 }],
    ["access_token vacio", { access_token: "  " }],
    ["refresh_token objeto", { refresh_token: {} }],
    ["user_id con texto", { user_id: "abc" }],
    ["user_id cero", { user_id: 0 }],
    ["user_id negativo", { user_id: -5 }],
    ["user_id decimal", { user_id: 1.5 }],
    ["user_id demasiado grande", { user_id: 1e30 }],
    ["user_id null", { user_id: null }],
    ["expires_in texto", { expires_in: "3600" }],
    ["expires_in cero", { expires_in: 0 }],
    ["expires_in negativo", { expires_in: -1 }],
    ["expires_in infinito", { expires_in: Infinity }],
    ["expires_in NaN", { expires_in: NaN }],
    ["public_key numerica", { public_key: 5 }],
    ["scope lista", { scope: ["read"] }],
    ["live_mode texto", { live_mode: "true" }],
  ];
  for (const [nombre, cambio] of raros) {
    it(`rechaza ${nombre}`, () => {
      assert.throws(() => parseRespuestaToken({ ...TOKEN_OK, ...cambio }, AHORA), RespuestaTokenInvalida);
    });
  }

  it("rechaza cuerpos que no son objeto", () => {
    for (const cuerpo of [null, undefined, "texto", 5, [], [TOKEN_OK]]) {
      assert.throws(() => parseRespuestaToken(cuerpo, AHORA), RespuestaTokenInvalida);
    }
  });

  it("el mensaje de error no incluye valores recibidos", () => {
    try {
      parseRespuestaToken({ ...TOKEN_OK, expires_in: "SECRETO-RARO" }, AHORA);
      assert.fail("debio lanzar");
    } catch (e) {
      assert.ok(e instanceof RespuestaTokenInvalida);
      assert.ok(!e.message.includes("SECRETO-RARO"));
    }
    try {
      parseRespuestaToken({ ...TOKEN_OK, user_id: "SECRETO-RARO" }, AHORA);
      assert.fail("debio lanzar");
    } catch (e) {
      assert.ok(e instanceof Error && !e.message.includes("SECRETO-RARO"));
    }
  });
});
