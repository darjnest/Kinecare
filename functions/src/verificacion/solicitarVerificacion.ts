import { Timestamp, type Firestore } from "firebase-admin/firestore";
import { COLECCION_PROFESIONALES, COLECCION_SOLICITUDES_VERIFICACION, COLECCION_USUARIOS, PROVEEDOR_VERIFICACION } from "../constantes.js";
import { errorDeNegocio } from "../errores.js";
import { esObjeto, invalido } from "../validacion.js";
import type { EstadoSolicitud } from "./estados.js";
import { camposInsigniaIdentidad, estadoInsigniaIdentidad, TIPO_INSIGNIA_IDENTIDAD } from "./insignias.js";
import { proveedorNoDisponible, type ProveedorVerificacion } from "./proveedor.js";

export interface DepsVerificacion {
  proveedor: ProveedorVerificacion;
  /** UUID del workflow de Didit (`DIDIT_WORKFLOW_ID`); vacio si no esta configurado. */
  workflowId: string;
  /** Origen publico HTTPS de las funciones, ej. `https://us-central1-<proyecto>.cloudfunctions.net`. */
  urlBase: string;
}

export interface ResultadoSolicitar {
  solicitudId: string;
  url: string;
  estado: EstadoSolicitud;
}

/** Valida la forma del payload. Hoy solo existe la verificacion de IDENTIDAD. */
export function parseSolicitarVerificacion(data: unknown): { tipo: "IDENTIDAD" } {
  if (!esObjeto(data)) throw invalido("el cuerpo debe ser un objeto");
  if (typeof data.tipo !== "string") throw invalido("tipo debe ser texto");
  if (data.tipo !== TIPO_INSIGNIA_IDENTIDAD) {
    throw errorDeNegocio("invalid-argument", "TIPO_NO_SOPORTADO", "Ese tipo de verificación aún no está disponible.");
  }
  return { tipo: "IDENTIDAD" };
}

const yaVerificado = () => errorDeNegocio("failed-precondition", "YA_VERIFICADO", "Tu identidad ya está verificada.");
const perfilNoEncontrado = () => errorDeNegocio("not-found", "PERFIL_NO_ENCONTRADO", "No encontramos tu perfil profesional.");

/**
 * `solicitarVerificacion`: el profesional inicia (o retoma) la verificacion de identidad con Didit.
 * El cliente solo recibe una URL; el veredicto lo escribe el webhook (o `estadoVerificacion`) con
 * Admin SDK. Didit es idempotente por `vendor_data`: mientras haya una sesion sin terminar devuelve la
 * misma, asi que volver a llamar equivale a "continuar verificacion" y no duplica solicitudes.
 */
export async function solicitarVerificacionHandler(
  db: Firestore,
  deps: DepsVerificacion,
  uid: string | undefined,
  data: unknown,
  ahora: Date,
): Promise<ResultadoSolicitar> {
  if (uid === undefined || uid === "") {
    throw errorDeNegocio("unauthenticated", "SIN_SESION", "Debes iniciar sesión para verificar tu identidad.");
  }
  parseSolicitarVerificacion(data);

  const usuario = await db.collection(COLECCION_USUARIOS).doc(uid).get();
  if (!usuario.exists || usuario.get("rol") !== "PROFESIONAL") {
    throw errorDeNegocio("permission-denied", "NO_ES_PROFESIONAL", "Solo un profesional puede verificar su identidad.");
  }

  const profesionalRef = db.collection(COLECCION_PROFESIONALES).doc(uid);
  const perfil = await profesionalRef.get();
  if (!perfil.exists) throw perfilNoEncontrado();
  if (estadoInsigniaIdentidad(perfil.get("insignias")) === "APROBADO") throw yaVerificado();

  if (deps.workflowId.trim() === "") {
    throw errorDeNegocio("failed-precondition", "PROVEEDOR_NO_CONFIGURADO", "La verificación de identidad aún no está disponible.");
  }

  let sesion;
  try {
    sesion = await deps.proveedor.crearSesion({
      workflowId: deps.workflowId.trim(),
      vendorData: uid,
      callbackUrl: `${deps.urlBase}/retornoVerificacion`,
    });
  } catch (e) {
    throw proveedorNoDisponible("crear sesion", e);
  }

  const solicitudRef = db.collection(COLECCION_SOLICITUDES_VERIFICACION).doc(sesion.sessionId);
  const estado = await db.runTransaction<EstadoSolicitud>(async (tx) => {
    const solicitud = await tx.get(solicitudRef);
    const profesional = await tx.get(profesionalRef);
    if (!profesional.exists) throw perfilNoEncontrado();
    if (estadoInsigniaIdentidad(profesional.get("insignias")) === "APROBADO") throw yaVerificado();

    // Un documento ya resuelto no se reabre (Didit no devuelve sesiones terminadas; es solo defensa).
    if (solicitud.exists && solicitud.get("estado") !== "PENDIENTE") return solicitud.get("estado") as EstadoSolicitud;

    if (!solicitud.exists) {
      tx.set(solicitudRef, {
        profesionalId: uid,
        tipo: TIPO_INSIGNIA_IDENTIDAD,
        estado: "PENDIENTE" satisfies EstadoSolicitud,
        proveedorExterno: PROVEEDOR_VERIFICACION,
        estadoProveedor: "Not Started",
        fechaSolicitud: Timestamp.fromDate(ahora),
      });
    }
    tx.update(profesionalRef, camposInsigniaIdentidad(profesional.get("insignias"), "PENDIENTE", ahora));
    return "PENDIENTE";
  });

  return { solicitudId: sesion.sessionId, url: sesion.url, estado };
}
