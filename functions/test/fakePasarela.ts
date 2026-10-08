import type { DatosPreferencia, PagoMP, Pasarela, TokensVendedor } from "../src/mercadopago/pasarela.js";

/** Pasarela en memoria: registra las llamadas y devuelve lo que el test configure. */
export class FakePasarela implements Pasarela {
  preferencias: Array<{ accessToken: string; datos: DatosPreferencia }> = [];
  codigosCanjeados: string[] = [];
  refrescos: string[] = [];
  pagoPorId = new Map<string, PagoMP>();
  pagoPorReferencia = new Map<string, PagoMP>();
  tokensAlCanjear: TokensVendedor = tokens("access-canjeado", "refresh-canjeado");
  tokensAlRefrescar: TokensVendedor | Error = tokens("access-renovado", "refresh-renovado");
  falla = false;

  urlAutorizacion(state: string): string {
    return `https://auth.test/authorization?state=${state}`;
  }

  async canjearCodigo(codigo: string): Promise<TokensVendedor> {
    this.codigosCanjeados.push(codigo);
    if (this.falla) throw new Error("fallo simulado");
    return this.tokensAlCanjear;
  }

  async refrescar(refreshToken: string): Promise<TokensVendedor> {
    this.refrescos.push(refreshToken);
    if (this.tokensAlRefrescar instanceof Error) throw this.tokensAlRefrescar;
    return this.tokensAlRefrescar;
  }

  async crearPreferencia(accessToken: string, datos: DatosPreferencia) {
    if (this.falla) throw new Error("fallo simulado");
    this.preferencias.push({ accessToken, datos });
    return { preferenceId: `pref-${datos.referenciaExterna}`, initPoint: `https://mp.test/checkout?pref=${datos.referenciaExterna}` };
  }

  async obtenerPago(_accessToken: string, paymentId: string): Promise<PagoMP> {
    if (this.falla) throw new Error("fallo simulado");
    const pago = this.pagoPorId.get(paymentId);
    if (pago === undefined) throw new Error(`pago ${paymentId} desconocido`);
    return pago;
  }

  async buscarPagoPorReferencia(_accessToken: string, referencia: string): Promise<PagoMP | null> {
    return this.pagoPorReferencia.get(referencia) ?? null;
  }
}

export function tokens(accessToken: string, refreshToken: string, expiraEn = new Date("2027-01-01T00:00:00Z")): TokensVendedor {
  return { accessToken, refreshToken, expiraEn, userId: "777", scope: "offline_access read write" };
}

export function pagoMP(sobrescribe: Partial<PagoMP> & { referenciaExterna: string }): PagoMP {
  return {
    id: "9001",
    status: "approved",
    monto: 25000,
    tipo: "credit_card",
    ultimosDigitos: "4321",
    ...sobrescribe,
  };
}
