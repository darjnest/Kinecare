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
  | "HORARIO_OCUPADO";

/** Crea el HttpsError con `details: { motivo }`, el contrato con el cliente. */
export function errorDeNegocio(code: FunctionsErrorCode, motivo: Motivo, message: string): HttpsError {
  return new HttpsError(code, message, { motivo });
}
