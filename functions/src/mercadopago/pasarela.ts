import { MercadoPagoConfig, Payment, Preference } from "mercadopago";
import { logger } from "firebase-functions/v2";
import { MONEDA } from "../constantes.js";
import { errorDeNegocio } from "../errores.js";

/** Tokens OAuth de un profesional ya conectado. */
export interface TokensVendedor {
  accessToken: string;
  refreshToken: string;
  /** Instante en que vence `accessToken`. */
  expiraEn: Date;
  /** `user_id` de Mercado Pago del vendedor. */
  userId: string;
  scope: string;
}

export interface DatosPreferencia {
  titulo: string;
  reservaId: string;
  monto: number;
  comision: number;
  /** Referencia de conciliacion: el id del documento `pagos/{id}`. */
  referenciaExterna: string;
  notificationUrl: string;
  backUrl: string;
  /** Misma clave en reintentos: no se duplican preferencias. */
  claveIdempotencia: string;
}

export interface PagoMP {
  id: string;
  status: string;
  referenciaExterna: string | null;
  monto: number | null;
  /** `credit_card`, `debit_card`, `bank_transfer`, ... */
  tipo: string | null;
  ultimosDigitos: string | null;
}

/**
 * Frontera con Mercado Pago. Los handlers dependen de esta interfaz (no del SDK) para poder
 * probarse contra el emulador de Firestore con una pasarela falsa.
 */
export interface Pasarela {
  urlAutorizacion(state: string): string;
  canjearCodigo(codigo: string): Promise<TokensVendedor>;
  refrescar(refreshToken: string): Promise<TokensVendedor>;
  crearPreferencia(accessToken: string, datos: DatosPreferencia): Promise<{ preferenceId: string; initPoint: string }>;
  obtenerPago(accessToken: string, paymentId: string): Promise<PagoMP>;
  buscarPagoPorReferencia(accessToken: string, referenciaExterna: string): Promise<PagoMP | null>;
}

export interface ConfigPasarela {
  appId: string;
  clientSecret: string;
  /** Debe coincidir EXACTO con la "Redirect URI" configurada en el panel de Mercado Pago. */
  redirectUri: string;
}

const API = "https://api.mercadopago.com";
const AUTH = "https://auth.mercadopago.com/authorization";

function pasarelaNoDisponible(): never {
  throw errorDeNegocio("unavailable", "PASARELA_NO_DISPONIBLE", "No pudimos comunicarnos con Mercado Pago. Intenta de nuevo.");
}

/** Registra solo el mensaje: nunca el cuerpo de la peticion (lleva tokens). */
function registrar(operacion: string, error: unknown): void {
  logger.error(`Mercado Pago: fallo ${operacion}`, { mensaje: error instanceof Error ? error.message : String(error) });
}

interface RespuestaToken {
  access_token?: string;
  refresh_token?: string;
  expires_in?: number;
  user_id?: number | string;
  scope?: string;
}

function aTokens(r: RespuestaToken, ahora: Date): TokensVendedor {
  if (!r.access_token || !r.refresh_token || typeof r.expires_in !== "number" || r.user_id === undefined) {
    throw new Error("respuesta de /oauth/token incompleta");
  }
  return {
    accessToken: r.access_token,
    refreshToken: r.refresh_token,
    expiraEn: new Date(ahora.getTime() + r.expires_in * 1000),
    userId: String(r.user_id),
    scope: r.scope ?? "",
  };
}

function aPagoMP(p: {
  id?: number | string;
  status?: string;
  external_reference?: string;
  transaction_amount?: number;
  payment_type_id?: string;
  card?: { last_four_digits?: string };
}): PagoMP {
  return {
    id: String(p.id),
    status: p.status ?? "",
    referenciaExterna: p.external_reference ?? null,
    monto: p.transaction_amount ?? null,
    tipo: p.payment_type_id ?? null,
    ultimosDigitos: p.card?.last_four_digits ?? null,
  };
}

export function crearPasarelaMP(config: ConfigPasarela, ahora: () => Date = () => new Date()): Pasarela {
  const cliente = (accessToken: string) => new MercadoPagoConfig({ accessToken, options: { timeout: 8000 } });

  /** /oauth/token va como application/x-www-form-urlencoded, segun el contrato de Marketplace. */
  async function pedirToken(parametros: Record<string, string>): Promise<TokensVendedor> {
    const respuesta = await fetch(`${API}/oauth/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
      body: new URLSearchParams({ client_id: config.appId, client_secret: config.clientSecret, ...parametros }),
      signal: AbortSignal.timeout(8000),
    });
    if (!respuesta.ok) throw new Error(`/oauth/token respondio ${respuesta.status}`);
    return aTokens((await respuesta.json()) as RespuestaToken, ahora());
  }

  return {
    urlAutorizacion(state) {
      const url = new URL(AUTH);
      url.search = new URLSearchParams({
        client_id: config.appId,
        response_type: "code",
        platform_id: "mp",
        redirect_uri: config.redirectUri,
        state,
      }).toString();
      return url.toString();
    },

    async canjearCodigo(codigo) {
      try {
        return await pedirToken({ grant_type: "authorization_code", code: codigo, redirect_uri: config.redirectUri });
      } catch (e) {
        registrar("canjear codigo OAuth", e);
        return pasarelaNoDisponible();
      }
    },

    async refrescar(refreshToken) {
      return pedirToken({ grant_type: "refresh_token", refresh_token: refreshToken });
    },

    async crearPreferencia(accessToken, d) {
      try {
        const r = await new Preference(cliente(accessToken)).create({
          body: {
            items: [{ id: d.reservaId, title: d.titulo, quantity: 1, unit_price: d.monto, currency_id: MONEDA }],
            marketplace_fee: d.comision,
            external_reference: d.referenciaExterna,
            notification_url: d.notificationUrl,
            back_urls: { success: d.backUrl, failure: d.backUrl, pending: d.backUrl },
            auto_return: "approved",
            statement_descriptor: "KINECARE",
          },
          requestOptions: { idempotencyKey: d.claveIdempotencia },
        });
        if (!r.id || !r.init_point) throw new Error("preferencia sin id o init_point");
        // Siempre init_point: Mercado Pago ya no tiene sandbox.
        return { preferenceId: r.id, initPoint: r.init_point };
      } catch (e) {
        registrar("crear preferencia", e);
        return pasarelaNoDisponible();
      }
    },

    async obtenerPago(accessToken, paymentId) {
      try {
        return aPagoMP(await new Payment(cliente(accessToken)).get({ id: paymentId }));
      } catch (e) {
        registrar("obtener pago", e);
        return pasarelaNoDisponible();
      }
    },

    async buscarPagoPorReferencia(accessToken, referenciaExterna) {
      try {
        const r = await new Payment(cliente(accessToken)).search({
          options: { external_reference: referenciaExterna, sort: "date_created", criteria: "desc" },
        });
        const ultimo = r.results?.[0];
        return ultimo === undefined ? null : aPagoMP(ultimo);
      } catch (e) {
        registrar("buscar pago", e);
        return pasarelaNoDisponible();
      }
    },
  };
}
