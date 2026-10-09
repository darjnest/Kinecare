export type EstadoSolicitud = "PENDIENTE" | "APROBADO" | "RECHAZADO";
export type EstadoInsignia = EstadoSolicitud | "NO_SOLICITADO";

/** Codigos gruesos que ve la app. Nunca texto del proveedor. */
export type MotivoVerificacion = "DECLINED" | "RUT_NO_COINCIDE" | "EXPIRADA" | "ABANDONADA" | "KYC_VENCIDO";

/** Lo que un estado de Didit significa para KineCare. */
export interface Resolucion {
  solicitud: EstadoSolicitud;
  insignia: EstadoInsignia;
  motivo: MotivoVerificacion | null;
  /** null = no comparable (o no aplica para este estado). */
  rutCoincide: boolean | null;
}

/** Texto PUBLICO de la insignia: neutro, sin el motivo fino (ese queda solo en la solicitud privada). */
export const DETALLE_INSIGNIA: Partial<Record<EstadoInsignia, string>> = {
  APROBADO: "Identidad verificada con documento y prueba de vida",
  RECHAZADO: "No se pudo verificar la identidad",
  PENDIENTE: "Verificación en curso",
};

const PENDIENTE: Resolucion = { solicitud: "PENDIENTE", insignia: "PENDIENTE", motivo: null, rutCoincide: null };

/**
 * Traduce el `status` de una sesion de Didit (sensible a mayusculas) al dominio. `rutCoincide` solo
 * importa con `Approved`: `false` convierte la aprobacion del proveedor en rechazo propio. Devuelve
 * `null` para un estado desconocido (el llamador no cambia nada y lo registra).
 *
 * `Expired`/`Abandoned`/`Kyc Expired` devuelven la insignia a `NO_SOLICITADO`: quien solo no termino
 * (o cuyo KYC vencio) no debe quedar marcado publicamente como rechazado.
 */
export function resolucionDesdeDidit(estadoDidit: string, rutCoincide: boolean | null): Resolucion | null {
  switch (estadoDidit) {
    case "Approved":
      return rutCoincide === false
        ? { solicitud: "RECHAZADO", insignia: "RECHAZADO", motivo: "RUT_NO_COINCIDE", rutCoincide: false }
        : { solicitud: "APROBADO", insignia: "APROBADO", motivo: null, rutCoincide };
    case "Declined":
      return { solicitud: "RECHAZADO", insignia: "RECHAZADO", motivo: "DECLINED", rutCoincide: null };
    case "In Review":
    case "Not Started":
    case "In Progress":
    case "Resubmitted":
    case "Awaiting User":
      return PENDIENTE;
    case "Expired":
      return { solicitud: "RECHAZADO", insignia: "NO_SOLICITADO", motivo: "EXPIRADA", rutCoincide: null };
    case "Abandoned":
      return { solicitud: "RECHAZADO", insignia: "NO_SOLICITADO", motivo: "ABANDONADA", rutCoincide: null };
    case "Kyc Expired":
      return { solicitud: "RECHAZADO", insignia: "NO_SOLICITADO", motivo: "KYC_VENCIDO", rutCoincide: null };
    default:
      return null;
  }
}

/** `Approved` es el unico estado que obliga a comparar el RUT. */
export const requiereComparacionDeRut = (estadoDidit: string): boolean => estadoDidit === "Approved";

/**
 * Los estados finales no retroceden: un evento tardio o desordenado no devuelve un `APROBADO` o
 * `RECHAZADO` a `PENDIENTE` ni cambia su resultado. Unica excepcion: `Kyc Expired` sobre un aprobado.
 */
export function puedeAplicarse(actual: EstadoSolicitud, nueva: Resolucion): boolean {
  if (actual === "PENDIENTE") return true;
  return actual === "APROBADO" && nueva.motivo === "KYC_VENCIDO";
}
