import { ProveedorVerificacionError, type DecisionProveedor, type ProveedorVerificacion, type SesionVerificacion } from "./proveedor.js";

export interface ConfigDidit {
  apiKey: string;
  /** Inyectable para pruebas; por defecto el `fetch` global (resuelto en cada llamada). */
  fetchFn?: typeof fetch;
  /** Tiempo maximo por peticion. El webhook usa uno corto: Didit corta a los 5 s. */
  timeoutMs?: number;
  baseUrl?: string;
}

const BASE_POR_DEFECTO = "https://verification.didit.me";
const TIMEOUT_POR_DEFECTO_MS = 8000;
/** Tope de espera que se informa de un `Retry-After`, por si el proveedor manda un valor absurdo. */
const MAX_RETRY_AFTER_SEGUNDOS = 3600;

function esObjeto(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === "object" && valor !== null && !Array.isArray(valor);
}

function segundosRetryAfter(valor: string | null): number | undefined {
  if (valor === null || !/^\d{1,6}$/.test(valor.trim())) return undefined; // la forma "fecha HTTP" no se interpreta
  return Math.min(Number(valor.trim()), MAX_RETRY_AFTER_SEGUNDOS);
}

function errorPorEstado(respuesta: Response): ProveedorVerificacionError {
  const s = respuesta.status;
  if (s === 429) return new ProveedorVerificacionError("LIMITE", s, segundosRetryAfter(respuesta.headers.get("Retry-After")));
  if (s === 401 || s === 403) return new ProveedorVerificacionError("NO_AUTORIZADO", s);
  if (s === 404) return new ProveedorVerificacionError("NO_ENCONTRADO", s);
  if (s >= 500) return new ProveedorVerificacionError("SERVIDOR", s);
  return new ProveedorVerificacionError("SOLICITUD_RECHAZADA", s);
}

/**
 * Adaptador de Didit (verification.didit.me, API v3). Autenticacion con `x-api-key`. Nunca registra
 * ni propaga cuerpos de respuesta: una decision contiene nombre, documento y fecha de nacimiento.
 */
export function crearProveedorDidit(config: ConfigDidit): ProveedorVerificacion {
  const base = config.baseUrl ?? BASE_POR_DEFECTO;
  const timeoutMs = config.timeoutMs ?? TIMEOUT_POR_DEFECTO_MS;

  /** Hace la peticion y devuelve el JSON de una respuesta 2xx; todo lo demas es un `ProveedorVerificacionError`. */
  async function pedir(metodo: "GET" | "POST", ruta: string, cuerpo?: unknown): Promise<unknown> {
    const fetchFn: typeof fetch = config.fetchFn ?? ((entrada, init) => globalThis.fetch(entrada, init));
    let respuesta: Response;
    try {
      respuesta = await fetchFn(`${base}${ruta}`, {
        method: metodo,
        headers: {
          "x-api-key": config.apiKey,
          Accept: "application/json",
          ...(cuerpo === undefined ? {} : { "Content-Type": "application/json" }),
        },
        body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
        signal: AbortSignal.timeout(timeoutMs),
      });
    } catch (e) {
      const nombre = e instanceof Error ? e.name : "";
      throw new ProveedorVerificacionError(nombre === "TimeoutError" || nombre === "AbortError" ? "TIMEOUT" : "RED");
    }
    if (!respuesta.ok) {
      const error = errorPorEstado(respuesta);
      void respuesta.body?.cancel().catch(() => undefined); // el cuerpo no se lee (puede ser HTML o traer datos)
      throw error;
    }
    try {
      return await respuesta.json();
    } catch (e) {
      const nombre = e instanceof Error ? e.name : "";
      throw new ProveedorVerificacionError(nombre === "TimeoutError" || nombre === "AbortError" ? "TIMEOUT" : "RESPUESTA_INVALIDA", respuesta.status);
    }
  }

  return {
    async crearSesion(datos): Promise<SesionVerificacion> {
      // Didit es idempotente por `vendor_data`: con una sesion sin terminar devuelve esa misma.
      const r = await pedir("POST", "/v3/session/", {
        workflow_id: datos.workflowId,
        vendor_data: datos.vendorData,
        callback: datos.callbackUrl,
        callback_method: "both",
        language: "es",
      });
      if (!esObjeto(r) || typeof r.session_id !== "string" || r.session_id === "" || typeof r.url !== "string" || r.url === "") {
        throw new ProveedorVerificacionError("RESPUESTA_INVALIDA");
      }
      return { sessionId: r.session_id, url: r.url };
    },

    async obtenerDecision(sessionId): Promise<DecisionProveedor> {
      const r = await pedir("GET", `/v3/session/${encodeURIComponent(sessionId)}/decision/`);
      if (!esObjeto(r) || typeof r.status !== "string" || r.status === "") {
        throw new ProveedorVerificacionError("RESPUESTA_INVALIDA");
      }
      // Solo se extrae el RUN para compararlo en memoria; el resto de la decision se descarta aqui.
      const primera = Array.isArray(r.id_verifications) ? (r.id_verifications as unknown[])[0] : undefined;
      const numero = esObjeto(primera) ? primera.personal_number : undefined;
      return { estado: r.status, rut: typeof numero === "string" && numero.trim() !== "" ? numero : null };
    },
  };
}
