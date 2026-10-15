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

/** Colecciones que lee/escribe el recalculo de calificación (ver calificacion.ts). */
export const COLECCION_PROFESIONALES = "profesionales";
export const COLECCION_RESENAS = "resenas";

/** Colecciones del flujo de pago (ver DATA_MODEL.md). Todas se escriben solo con Admin SDK. */
export const COLECCION_PAGOS = "pagos";
export const COLECCION_CUENTAS_MP = "cuentasMercadoPago";
export const COLECCION_ESTADOS_OAUTH = "oauthEstados";

/**
 * Comision de atenciones que no se pagaron por la app (efectivo): la plataforma no puede retener ese
 * dinero, asi que queda como saldo adeudado del profesional (`comisionesPendientes/{reservaId}`) y se
 * cobra descontandolo de un pago con tarjeta o con un pago directo (`cobrosComision`).
 */
export const COLECCION_COMISIONES = "comisionesPendientes";
export const COLECCION_COBROS_COMISION = "cobrosComision";

/** Con este saldo adeudado (CLP) el profesional no puede aceptar ni recibir solicitudes nuevas. */
export const TOPE_SALDO_COMISION_CLP = 30_000;

/**
 * Tope del `marketplace_fee` total de un pago (comision + saldo descontado) como fraccion del monto:
 * Mercado Pago rechaza `fee >= monto` y el profesional no puede quedarse sin ingreso por una cita.
 */
export const MAX_FEE_PORCENTAJE_DEL_MONTO = 0.5;

/** Una entrada de comision reservada por un pago/cobro sin resolver vuelve a quedar libre pasado este tiempo. */
export const RESERVA_COMISION_VIGENCIA_HORAS = 24;

/** Moneda de todos los cobros (Mercado Pago Chile). El CLP no tiene decimales. */
export const MONEDA = "CLP";

/** Vigencia del `state` de OAuth: el profesional tiene este tiempo para autorizar en Mercado Pago. */
export const OAUTH_STATE_VIGENCIA_MINUTOS = 10;

/** Se renueva el token del vendedor si vence en menos que esto. */
export const REFRESCO_ANTICIPADO_MINUTOS = 5;

/** Esquema de deep link con el que Android recupera el control tras salir a Mercado Pago. */
export const ESQUEMA_APP = "kinecare";

/** Verificacion de identidad (ver DATA_MODEL.md). Se escribe solo con Admin SDK. */
export const COLECCION_SOLICITUDES_VERIFICACION = "solicitudesVerificacion";
export const COLECCION_USUARIOS = "usuarios";
export const PROVEEDOR_VERIFICACION = "Didit";

/** Ventana maxima entre `X-Timestamp` del webhook de Didit y el reloj del servidor. */
export const TOLERANCIA_FIRMA_DIDIT_SEGUNDOS = 300;

/** Tope de solicitudes que se leen por profesional al buscar la mas reciente (se ordena en memoria). */
export const MAX_SOLICITUDES_POR_PROFESIONAL = 100;
/** Cancelacion (ver cancelarReserva.ts). */
export const MAX_LARGO_MOTIVO_CANCELACION = 300;
/** Anticipacion minima con que un cliente cancela para tener reembolso total. */
export const HORAS_REEMBOLSO_TOTAL = 24;

/**
 * Estados de reserva que ya no se atienden: un cobro que se aprueba sobre una reserva asi
 * (preferencia de Checkout Pro que siguio abierta tras cancelar) debe devolverse.
 */
export const ESTADOS_RESERVA_SIN_ATENCION = ["CANCELADA_CLIENTE", "CANCELADA_PROFESIONAL", "RECHAZADA"] as const;

/** Subcoleccion `usuarios/{uid}/dispositivos/{token}` con los tokens FCM del usuario. */
export const SUBCOLECCION_DISPOSITIVOS = "dispositivos";

/** Ventana maxima que puede pedir obtenerCuposOcupados. */
export const VENTANA_MAXIMA_CUPOS_DIAS = 15;

/**
 * Limite de frecuencia de `solicitarVerificacion` por profesional. Cada sesion nueva tras un
 * rechazo/expiracion/abandono puede gastar creditos del proveedor, asi que en la ventana se permiten
 * pocos intentos ya resueltos, y un tope mas alto de sesiones en total (cubre abrir sesiones nuevas
 * mientras otra queda `In Review`, que Didit ya no devuelve como la misma).
 */
export const VERIFICACION_VENTANA_HORAS = 24;
export const VERIFICACION_MAX_INTENTOS_RESUELTOS = 3;
export const VERIFICACION_MAX_SOLICITUDES = 6;
