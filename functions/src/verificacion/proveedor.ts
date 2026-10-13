import { logger } from "firebase-functions/v2";
import type { HttpsError } from "firebase-functions/v2/https";
import { errorDeNegocio } from "../errores.js";

export interface DatosSesion {
  workflowId: string;
  /** uid del profesional: el proveedor lo devuelve tal cual y permite conciliar la sesion. */
  vendorData: string;
  /** A donde vuelve el usuario al terminar (HTTPS; luego salta a la app por deep link). */
  callbackUrl: string;
}

export interface SesionVerificacion {
  sessionId: string;
  /** URL que se abre en el navegador/webview para hacer la verificacion. */
  url: string;
}

/**
 * Veredicto del proveedor. NUNCA lleva ni persiste datos personales ni biometria: `rut` existe solo
 * para compararlo en memoria con `usuarios/{uid}.rut` y se descarta; jamas se escribe ni se loguea.
 */
export interface DecisionProveedor {
  /** `status` crudo de Didit ("Approved", "In Review", ...). */
  estado: string;
  /** `personal_number` de la primera verificacion de documento, o null si no viene. */
  rut: string | null;
}

/**
 * Frontera con el proveedor de verificacion. Los handlers dependen de esta interfaz (no de `fetch`)
 * para probarse contra el emulador de Firestore con un proveedor falso.
 */
export interface ProveedorVerificacion {
  crearSesion(datos: DatosSesion): Promise<SesionVerificacion>;
  obtenerDecision(sessionId: string): Promise<DecisionProveedor>;
}

export type CausaErrorProveedor =
  /** 400: validacion, workflow desconocido o creditos insuficientes (Didit no los distingue por codigo). */
  | "SOLICITUD_RECHAZADA"
  /** 401/403: API key invalida o sin permiso. */
  | "NO_AUTORIZADO"
  /** 404: sesion inexistente (o id mal formado: Didit responde HTML). */
  | "NO_ENCONTRADO"
  /** 429: hay que esperar `reintentarDespuesSegundos`. */
  | "LIMITE"
  | "SERVIDOR"
  | "TIMEOUT"
  | "RED"
  /** 2xx con un cuerpo que no tiene la forma documentada. */
  | "RESPUESTA_INVALIDA";

/**
 * Fallo al hablar con el proveedor. El mensaje solo lleva la causa y el codigo HTTP: nunca el cuerpo
 * de la respuesta (una decision trae datos personales) ni la API key.
 */
export class ProveedorVerificacionError extends Error {
  constructor(
    readonly causa: CausaErrorProveedor,
    readonly estadoHttp?: number,
    /** Valor de `Retry-After` en segundos (solo con 429, si el proveedor lo informo). */
    readonly reintentarDespuesSegundos?: number,
  ) {
    super(`proveedor de verificacion: ${causa}${estadoHttp === undefined ? "" : ` (HTTP ${estadoHttp})`}`);
    this.name = "ProveedorVerificacionError";
  }
}

/**
 * Registra el fallo (sin datos sensibles) y lo traduce al error de negocio que ve el cliente. Un 400 o
 * 401 en produccion suele significar creditos agotados, workflow mal configurado o API key invalida:
 * es un problema operativo, no del usuario, y el log es lo que permite diagnosticarlo.
 */
export function proveedorNoDisponible(operacion: string, e: unknown): HttpsError {
  if (e instanceof ProveedorVerificacionError) {
    logger.error(`Verificacion: fallo ${operacion}`, {
      causa: e.causa,
      estadoHttp: e.estadoHttp,
      reintentarDespuesSegundos: e.reintentarDespuesSegundos,
    });
  } else {
    logger.error(`Verificacion: fallo ${operacion}`, { mensaje: e instanceof Error ? e.message : String(e) });
  }
  return errorDeNegocio("unavailable", "PROVEEDOR_NO_DISPONIBLE", "No pudimos comunicarnos con el servicio de verificación. Intenta de nuevo.");
}
