import assert from "node:assert/strict";
import { afterEach, beforeEach, describe, it } from "node:test";
import { crearPasarelaMP, TokenRechazadoError } from "../../src/mercadopago/pasarela.js";
import { esperarError } from "../helpers.js";

const AHORA = new Date("2026-10-01T12:00:00Z");
const config = { appId: "123", clientSecret: "secreto", redirectUri: "https://f.test/callback" };

interface Llamada {
  url: string;
  metodo: string;
  headers: Headers;
  cuerpo: string;
}

let llamadas: Llamada[];
let responder: (llamada: Llamada) => Response;
const fetchOriginal = globalThis.fetch;

beforeEach(() => {
  llamadas = [];
  responder = () => new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } });
  globalThis.fetch = (async (entrada: string | URL | Request, init?: RequestInit) => {
    const llamada: Llamada = {
      url: String(entrada),
      metodo: init?.method ?? "GET",
      headers: new Headers(init?.headers),
      cuerpo: init?.body === undefined || init.body === null ? "" : String(init.body),
    };
    llamadas.push(llamada);
    return responder(llamada);
  }) as typeof fetch;
});

afterEach(() => {
  globalThis.fetch = fetchOriginal;
});

const json = (cuerpo: unknown, status = 200) =>
  new Response(JSON.stringify(cuerpo), { status, headers: { "Content-Type": "application/json" } });

const tokenOk = { access_token: "acc", refresh_token: "ref", expires_in: 21600, user_id: 777, scope: "read write" };

describe("urlAutorizacion", () => {
  it("lleva client_id, response_type=code, platform_id=mp, la redirect_uri exacta y el state", () => {
    const url = new URL(crearPasarelaMP(config, () => AHORA).urlAutorizacion("estado1"));
    assert.equal(url.origin + url.pathname, "https://auth.mercadopago.com/authorization");
    assert.equal(url.searchParams.get("client_id"), "123");
    assert.equal(url.searchParams.get("response_type"), "code");
    assert.equal(url.searchParams.get("platform_id"), "mp");
    assert.equal(url.searchParams.get("redirect_uri"), "https://f.test/callback");
    assert.equal(url.searchParams.get("state"), "estado1");
  });

  it("usa el host configurado (por si Chile exige el dominio del pais)", () => {
    const pasarela = crearPasarelaMP({ ...config, hostAutorizacion: "https://auth.mercadopago.cl" }, () => AHORA);
    assert.equal(new URL(pasarela.urlAutorizacion("s")).origin, "https://auth.mercadopago.cl");
  });
});

describe("OAuth /oauth/token", () => {
  it("canjearCodigo envia form-urlencoded con el contrato de Marketplace y calcula el vencimiento", async () => {
    responder = () => json(tokenOk);
    const tokens = await crearPasarelaMP(config, () => AHORA).canjearCodigo("codigo-1");

    const [llamada] = llamadas;
    assert.equal(llamada!.url, "https://api.mercadopago.com/oauth/token");
    assert.equal(llamada!.metodo, "POST");
    assert.equal(llamada!.headers.get("content-type"), "application/x-www-form-urlencoded");
    const form = new URLSearchParams(llamada!.cuerpo);
    assert.equal(form.get("grant_type"), "authorization_code");
    assert.equal(form.get("client_id"), "123");
    assert.equal(form.get("client_secret"), "secreto");
    assert.equal(form.get("code"), "codigo-1");
    assert.equal(form.get("redirect_uri"), "https://f.test/callback");

    assert.deepEqual(tokens, {
      accessToken: "acc",
      refreshToken: "ref",
      expiraEn: new Date(AHORA.getTime() + 21600 * 1000),
      userId: "777",
      scope: "read write",
    });
  });

  it("refrescar usa grant_type=refresh_token sin code ni redirect_uri", async () => {
    responder = () => json(tokenOk);
    await crearPasarelaMP(config, () => AHORA).refrescar("ref-viejo");
    const form = new URLSearchParams(llamadas[0]!.cuerpo);
    assert.equal(form.get("grant_type"), "refresh_token");
    assert.equal(form.get("refresh_token"), "ref-viejo");
    assert.equal(form.get("code"), null);
    assert.equal(form.get("redirect_uri"), null);
  });

  it("un 400/401 al refrescar es TokenRechazadoError (hay que reautorizar)", async () => {
    for (const status of [400, 401]) {
      responder = () => json({ error: "invalid_grant" }, status);
      await assert.rejects(crearPasarelaMP(config, () => AHORA).refrescar("r"), TokenRechazadoError);
    }
  });

  it("un 5xx al refrescar es un error comun: NO se confunde con un token rechazado", async () => {
    responder = () => json({}, 503);
    await assert.rejects(crearPasarelaMP(config, () => AHORA).refrescar("r"), (e: unknown) => {
      assert.ok(e instanceof Error && !(e instanceof TokenRechazadoError));
      return true;
    });
  });

  it("una respuesta de token incompleta falla", async () => {
    responder = () => json({ access_token: "acc" });
    await assert.rejects(crearPasarelaMP(config, () => AHORA).refrescar("r"));
  });

  it("canjearCodigo traduce cualquier fallo a PASARELA_NO_DISPONIBLE", async () => {
    responder = () => json({}, 500);
    await esperarError(() => crearPasarelaMP(config, () => AHORA).canjearCodigo("c"), "unavailable", "PASARELA_NO_DISPONIBLE");
  });
});

describe("preferencias y pagos", () => {
  const datos = {
    titulo: "Kinesiología",
    reservaId: "res1",
    monto: 25000,
    comision: 2500,
    referenciaExterna: "pago1",
    notificationUrl: "https://f.test/webhookMercadoPago?pagoId=pago1",
    backUrl: "https://f.test/retornoPago",
    claveIdempotencia: "pago1",
  };

  it("crearPreferencia llama a /checkout/preferences con el token del vendedor, marketplace_fee e idempotencia", async () => {
    responder = () => json({ id: "pref-1", init_point: "https://www.mercadopago.cl/checkout/v1/redirect?pref_id=pref-1" }, 201);
    const resultado = await crearPasarelaMP(config, () => AHORA).crearPreferencia("token-vendedor", datos);

    assert.deepEqual(resultado, {
      preferenceId: "pref-1",
      initPoint: "https://www.mercadopago.cl/checkout/v1/redirect?pref_id=pref-1",
    });
    const [llamada] = llamadas;
    // El SDK oficial agrega una barra final; lo que importa es host, ruta y que NO lleve `v1`.
    const destino = new URL(llamada!.url);
    assert.equal(destino.origin, "https://api.mercadopago.com");
    assert.equal(destino.pathname.replace(/\/$/, ""), "/checkout/preferences");
    assert.equal(llamada!.metodo, "POST");
    assert.equal(llamada!.headers.get("authorization"), "Bearer token-vendedor");
    assert.equal(llamada!.headers.get("x-idempotency-key"), "pago1");
    const cuerpo = JSON.parse(llamada!.cuerpo);
    assert.deepEqual(cuerpo.items, [{ id: "res1", title: "Kinesiología", quantity: 1, unit_price: 25000, currency_id: "CLP" }]);
    assert.equal(cuerpo.marketplace_fee, 2500);
    assert.equal(cuerpo.external_reference, "pago1");
    assert.equal(cuerpo.notification_url, datos.notificationUrl);
    assert.deepEqual(cuerpo.back_urls, { success: datos.backUrl, failure: datos.backUrl, pending: datos.backUrl });
    assert.equal(cuerpo.auto_return, "approved");
    assert.ok(!JSON.stringify(cuerpo).includes("sandbox"), "nunca sandbox_init_point");
  });

  it("crearPreferencia falla con PASARELA_NO_DISPONIBLE si Mercado Pago no devuelve init_point", async () => {
    responder = () => json({ id: "pref-1" }, 201);
    await esperarError(() => crearPasarelaMP(config, () => AHORA).crearPreferencia("t", datos), "unavailable", "PASARELA_NO_DISPONIBLE");
  });

  it("obtenerPago lee /v1/payments/{id} con el token del vendedor y mapea los campos", async () => {
    responder = () =>
      json({ id: 9001, status: "approved", external_reference: "pago1", transaction_amount: 25000, payment_type_id: "credit_card", card: { last_four_digits: "4321" } });
    const pago = await crearPasarelaMP(config, () => AHORA).obtenerPago("token-vendedor", "9001");

    assert.equal(llamadas[0]!.url, "https://api.mercadopago.com/v1/payments/9001");
    assert.equal(llamadas[0]!.headers.get("authorization"), "Bearer token-vendedor");
    assert.deepEqual(pago, {
      id: "9001",
      status: "approved",
      referenciaExterna: "pago1",
      monto: 25000,
      tipo: "credit_card",
      ultimosDigitos: "4321",
    });
  });

  it("buscarPagoPorReferencia filtra por external_reference y devuelve el mas reciente, o null", async () => {
    responder = () => json({ results: [{ id: 9002, status: "pending", external_reference: "pago1", transaction_amount: 25000 }] });
    const pago = await crearPasarelaMP(config, () => AHORA).buscarPagoPorReferencia("t", "pago1");
    assert.ok(llamadas[0]!.url.includes("external_reference=pago1"));
    assert.equal(pago?.id, "9002");

    responder = () => json({ results: [] });
    assert.equal(await crearPasarelaMP(config, () => AHORA).buscarPagoPorReferencia("t", "pago1"), null);
  });
});
