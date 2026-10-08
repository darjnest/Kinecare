import { HttpsError, type FunctionsErrorCode } from "firebase-functions/v2/https";

/** Motivos de negocio que el cliente Android mapea a mensajes/acciones propias. */
export type Motivo =
  | "SIN_SESION"
  | "ROL_INVALIDO"
  | "DATOS_INVALIDOS"
  | "DIRECCION_REQUERIDA"
  | "SERVICIO_NO_DISPONIBLE"
  | "ANTICIPACION_INSUFICIENTE"
  | "FUERA_DE_HORARIO"
  | "HORARIO_OCUPADO"
  // responderReserva
  | "RESERVA_NO_ENCONTRADA"
  | "RESERVA_YA_RESPONDIDA"
  | "RESERVA_VENCIDA"
  // pagos
  | "RESERVA_NO_PAGABLE"
  | "PAGO_YA_REALIZADO"
  | "PAGO_NO_ENCONTRADO"
  | "PROFESIONAL_SIN_CUENTA_MP"
  | "MONTO_INVALIDO"
  | "PASARELA_NO_DISPONIBLE"
  // verificacion de identidad
  | "NO_ES_PROFESIONAL"
  | "TIPO_NO_SOPORTADO"
  | "PERFIL_NO_ENCONTRADO"
  | "YA_VERIFICADO"
  | "PROVEEDOR_NO_CONFIGURADO"
  | "PROVEEDOR_NO_DISPONIBLE"
  | "SOLICITUD_NO_ENCONTRADA";

/** Crea el HttpsError con `details: { motivo }`, el contrato con el cliente. */
export function errorDeNegocio(code: FunctionsErrorCode, motivo: Motivo, message: string): HttpsError {
  return new HttpsError(code, message, { motivo });
}
