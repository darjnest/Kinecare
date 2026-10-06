import { errorDeNegocio } from "../errores.js";

export type EstadoPago = "PENDIENTE" | "AUTORIZADO" | "RECHAZADO" | "REEMBOLSADO";

/** Traduce el `status` de un pago de Mercado Pago al estado de dominio de KineCare. */
export function estadoDesdeMP(status: string | undefined): EstadoPago {
  switch (status) {
    case "approved":
      return "AUTORIZADO";
    case "rejected":
    case "cancelled":
      return "RECHAZADO";
    case "refunded":
    case "charged_back":
      return "REEMBOLSADO";
    default:
      // pending, in_process, in_mediation, authorized, undefined...
      return "PENDIENTE";
  }
}

/**
 * Las notificaciones pueden llegar repetidas y desordenadas, y una misma preferencia admite
 * varios intentos (uno rechazado y luego uno aprobado). Un pago autorizado solo puede pasar a
 * reembolsado; reembolsado es terminal.
 */
export function transicionValida(actual: EstadoPago, nuevo: EstadoPago): boolean {
  if (actual === nuevo) return false;
  switch (actual) {
    case "PENDIENTE":
      return true;
    case "RECHAZADO":
      return nuevo === "AUTORIZADO" || nuevo === "PENDIENTE";
    case "AUTORIZADO":
      return nuevo === "REEMBOLSADO";
    case "REEMBOLSADO":
      return false;
  }
}

/**
 * Comision del marketplace en CLP enteros. El monto sale del servicio (backend) y la comision
 * debe quedar estrictamente bajo el total: Mercado Pago rechaza `marketplace_fee >= total`.
 */
export function calcularComision(monto: number, porcentaje: number): number {
  if (!Number.isInteger(monto) || monto <= 0) {
    throw errorDeNegocio("failed-precondition", "MONTO_INVALIDO", "El monto de la reserva no es válido para cobrar.");
  }
  if (!Number.isFinite(porcentaje) || porcentaje < 0 || porcentaje >= 1) {
    throw errorDeNegocio("failed-precondition", "MONTO_INVALIDO", "La comisión configurada no es válida.");
  }
  const comision = Math.round(monto * porcentaje);
  if (comision >= monto) {
    throw errorDeNegocio("failed-precondition", "MONTO_INVALIDO", "La comisión no puede igualar o superar el monto.");
  }
  return comision;
}
