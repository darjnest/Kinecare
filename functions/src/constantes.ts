// Constantes de negocio de las Cloud Functions de reservas. Viven en un solo lugar para que
// tests y handler no dupliquen numeros magicos.

/** Comision del marketplace. Solo el backend la fija; el cliente nunca la envia. */
export const COMISION_PORCENTAJE = 0.1;

/** Minimo de anticipacion para reservar (la cita debe empezar en >= ahora + esto). */
export const ANTICIPACION_MINIMA_MINUTOS = 60;

/** Maximo de anticipacion para reservar (la cita debe empezar en <= ahora + esto). */
export const ANTICIPACION_MAXIMA_DIAS = 60;

/** Zona horaria en la que los profesionales declaran su Disponibilidad. */
export const ZONA_HORARIA = "America/Santiago";

/** Estados de una reserva que ocupan agenda (bloquean el horario). */
export const ESTADOS_QUE_OCUPAN_AGENDA = ["SOLICITADA", "CONFIRMADA", "EN_CURSO"] as const;

/**
 * Duracion asumida para una reserva existente sin `duracionMinutos`
 * (reservas anteriores a este campo).
 */
export const DURACION_POR_DEFECTO_MINUTOS = 60;

/**
 * Una reserva nunca cruza la medianoche local, asi que dura como maximo 24 h.
 * Se usa para ensanchar hacia atras la ventana de la consulta de solapes:
 * una reserva que empieza antes de `inicio - 24h` no puede llegar a `inicio`.
 */
export const DURACION_MAXIMA_MINUTOS = 24 * 60;

export const MAX_LARGO_DIRECCION = 120;
export const MAX_LARGO_INDICACIONES = 300;
export const MAX_LARGO_ID = 128;

/** Coleccion de documentos-candado por profesional (ver crearReserva.ts). */
export const COLECCION_BLOQUEOS_AGENDA = "bloqueosAgenda";

// --- Mercado Pago (OAuth del profesional) -------------------------------------------------
// OJO: las URLs y el formato de la respuesta salen de memoria y NO estan verificados contra la
// documentacion oficial de Mercado Pago. Si el flujo falla en QA, corregirlos aqui (un solo lugar).

/** Pagina de autorizacion a la que se redirige al profesional (dominio de Chile). */
export const MP_URL_AUTORIZACION = "https://auth.mercadopago.cl/authorization";

/** Endpoint donde se canjea el `code` (y mas adelante se refresca el token). */
export const MP_URL_TOKEN = "https://api.mercadopago.com/oauth/token";

/** Vigencia del `state` del flujo OAuth: es de un solo uso y expira rapido. */
export const OAUTH_STATE_TTL_MINUTOS = 10;

/** Tiempo maximo de espera al canjear el `code` en Mercado Pago. */
export const MP_TIMEOUT_CANJE_MS = 10_000;

/** Region de todas las Cloud Functions de KineCare. */
export const REGION_FUNCTIONS = "us-central1";

export const COLECCION_MP_OAUTH_STATES = "mercadoPagoOAuthStates";
export const COLECCION_MP_TOKENS = "mercadoPagoTokens";
export const COLECCION_MP_ESTADOS = "mercadoPagoEstados";

/**
 * Redirect URI de la funcion `mercadoPagoCallback`. Debe registrarse tal cual en la aplicacion
 * de Mercado Pago (panel de desarrolladores) y coincidir byte a byte entre la autorizacion y
 * el canje del `code`.
 */
export function urlCallbackMercadoPago(projectId: string): string {
  return `https://${REGION_FUNCTIONS}-${projectId}.cloudfunctions.net/mercadoPagoCallback`;
}
