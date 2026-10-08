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

/** Colecciones del flujo de pago (ver DATA_MODEL.md). Todas se escriben solo con Admin SDK. */
export const COLECCION_PAGOS = "pagos";
export const COLECCION_CUENTAS_MP = "cuentasMercadoPago";
export const COLECCION_ESTADOS_OAUTH = "oauthEstados";

/** Moneda de todos los cobros (Mercado Pago Chile). El CLP no tiene decimales. */
export const MONEDA = "CLP";

/** Vigencia del `state` de OAuth: el profesional tiene este tiempo para autorizar en Mercado Pago. */
export const OAUTH_STATE_VIGENCIA_MINUTOS = 10;

/** Se renueva el token del vendedor si vence en menos que esto. */
export const REFRESCO_ANTICIPADO_MINUTOS = 5;

/** Esquema de deep link con el que Android recupera el control tras salir a Mercado Pago. */
export const ESQUEMA_APP = "kinecare";
