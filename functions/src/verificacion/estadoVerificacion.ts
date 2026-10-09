import type { Firestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { COLECCION_SOLICITUDES_VERIFICACION, MAX_SOLICITUDES_POR_PROFESIONAL } from "../constantes.js";
import { errorDeNegocio } from "../errores.js";
import { esObjeto, idObligatorio, invalido } from "../validacion.js";
import { aplicarDecision } from "./aplicar.js";
import type { EstadoSolicitud, MotivoVerificacion } from "./estados.js";
import { TIPO_INSIGNIA_IDENTIDAD } from "./insignias.js";
import type { ProveedorVerificacion } from "./proveedor.js";

export interface ResultadoEstadoVerificacion {
  estado: EstadoSolicitud | "NO_SOLICITADO";
  solicitudId?: string;
  /** Codigo grueso (`DECLINED`, `RUT_NO_COINCIDE`, ...); nunca texto del proveedor. */
  motivo?: MotivoVerificacion;
}

const solicitudNoEncontrada = () => errorDeNegocio("not-found", "SOLICITUD_NO_ENCONTRADA", "No encontramos esa verificación.");

function resultado(solicitudId: string, estado: EstadoSolicitud, motivo: MotivoVerificacion | null): ResultadoEstadoVerificacion {
  return { estado, solicitudId, ...(motivo === null ? {} : { motivo }) };
}

/**
 * `estadoVerificacion`: la app lo llama al volver de Didit (o al abrir la pantalla). Sin
 * `solicitudId` toma la solicitud de IDENTIDAD mas reciente del que llama. Si esta `PENDIENTE`
 * relee la decision a Didit y la aplica (autocuracion si el webhook se perdio o tardo); si esa
 * relectura falla devuelve el estado guardado, sin error. Una solicitud ajena responde igual que una
 * inexistente.
 */
export async function estadoVerificacionHandler(
  db: Firestore,
  deps: { proveedor: ProveedorVerificacion },
  uid: string | undefined,
  data: unknown,
  ahora: Date,
): Promise<ResultadoEstadoVerificacion> {
  if (uid === undefined || uid === "") {
    throw errorDeNegocio("unauthenticated", "SIN_SESION", "Debes iniciar sesión para consultar tu verificación.");
  }
  if (data !== undefined && data !== null && !esObjeto(data)) throw invalido("el cuerpo debe ser un objeto");
  const pedido = data === undefined || data === null ? undefined : data.solicitudId;
  const solicitudes = db.collection(COLECCION_SOLICITUDES_VERIFICACION);

  let solicitud;
  if (pedido !== undefined && pedido !== null) {
    solicitud = await solicitudes.doc(idObligatorio(pedido, "solicitudId")).get();
    if (!solicitud.exists || solicitud.get("profesionalId") !== uid) throw solicitudNoEncontrada();
  } else {
    // Solo igualdad: ordenar en Firestore pediria un indice compuesto (que el emulador no exige y QA si).
    const propias = await solicitudes.where("profesionalId", "==", uid).limit(MAX_SOLICITUDES_POR_PROFESIONAL).get();
    const masReciente = propias.docs
      .filter((d) => d.get("tipo") === TIPO_INSIGNIA_IDENTIDAD)
      .sort((a, b) => fechaMs(b.get("fechaSolicitud")) - fechaMs(a.get("fechaSolicitud")))[0];
    if (masReciente === undefined) return { estado: "NO_SOLICITADO" };
    solicitud = masReciente;
  }

  const estado = solicitud.get("estado") as EstadoSolicitud;
  const motivo = (solicitud.get("motivo") as MotivoVerificacion | undefined) ?? null;
  if (estado !== "PENDIENTE") return resultado(solicitud.id, estado, motivo);

  try {
    const decision = await deps.proveedor.obtenerDecision(solicitud.id);
    const aplicado = await aplicarDecision(db, { solicitudId: solicitud.id, decision, ahora });
    return aplicado === null ? resultado(solicitud.id, estado, motivo) : resultado(solicitud.id, aplicado.estado, aplicado.motivo);
  } catch (e) {
    logger.warn("Verificacion: no se pudo releer la decision; se devuelve el estado guardado", {
      solicitudId: solicitud.id,
      mensaje: e instanceof Error ? e.message : String(e),
    });
    return resultado(solicitud.id, estado, motivo);
  }
}

function fechaMs(valor: unknown): number {
  return typeof valor === "object" && valor !== null && "toMillis" in valor && typeof valor.toMillis === "function"
    ? (valor.toMillis() as number)
    : 0;
}
