import type { Firestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { COLECCION_SOLICITUDES_VERIFICACION, ESQUEMA_APP } from "../constantes.js";
import type { RespuestaHttp } from "../conectarMercadoPago.js";
import { ID_SEGURO, esObjeto } from "../validacion.js";
import { aplicarDecision } from "./aplicar.js";
import { firmaDiditValida } from "./firmaDidit.js";
import type { ProveedorVerificacion } from "./proveedor.js";

export interface PeticionWebhookDidit {
  headers: Record<string, string | string[] | undefined>;
  /** Bytes exactos del cuerpo (`req.rawBody`): la firma se calcula sobre ellos. */
  rawBody: Buffer | string | undefined;
}

function cabecera(headers: PeticionWebhookDidit["headers"], nombre: string): string | undefined {
  const valor = headers[nombre];
  return Array.isArray(valor) ? valor[0] : valor;
}

function parsear(rawBody: Buffer | string): Record<string, unknown> | null {
  try {
    const cuerpo: unknown = JSON.parse(typeof rawBody === "string" ? rawBody : rawBody.toString("utf8"));
    return esObjeto(cuerpo) ? cuerpo : null;
  } catch {
    return null;
  }
}

/**
 * `webhookDidit`. Orden: (1) validar la firma ANTES de mirar el contenido; (2) ignorar lo que no sea
 * `status.updated`; (3) releer la decision a Didit con el `session_id` (fuente de verdad: del payload
 * no se toma nada salvo el id de sesion y el id de evento); (4) aplicarla en una transaccion
 * idempotente. En Cloud Functions la CPU se congela al responder, asi que se procesa ANTES de
 * contestar (Didit corta a los 5 s: el proveedor de este handler debe tener un timeout corto). Falla
 * transitoria -> 500 para que Didit reintente (solo 2 veces); `estadoVerificacion` cubre lo que quede.
 */
export async function webhookDiditHandler(
  db: Firestore,
  deps: { proveedor: ProveedorVerificacion },
  secreto: string,
  peticion: PeticionWebhookDidit,
  ahora: Date,
): Promise<RespuestaHttp> {
  const valida = firmaDiditValida({
    rawBody: peticion.rawBody,
    xSignature: cabecera(peticion.headers, "x-signature"),
    xSignatureV2: cabecera(peticion.headers, "x-signature-v2"),
    xTimestamp: cabecera(peticion.headers, "x-timestamp"),
    secreto,
    ahora,
  });
  if (!valida || peticion.rawBody === undefined) {
    // Diagnostico sin datos sensibles: que cabeceras llegaron y si hay un secreto cargado (nunca su valor ni su largo).
    logger.warn("Didit: firma de webhook invalida", {
      tieneFirma: cabecera(peticion.headers, "x-signature") !== undefined,
      tieneFirmaV2: cabecera(peticion.headers, "x-signature-v2") !== undefined,
      tieneTimestamp: cabecera(peticion.headers, "x-timestamp") !== undefined,
      secretoConfigurado: secreto !== "",
    });
    return { status: 401 };
  }

  const cuerpo = parsear(peticion.rawBody);
  if (cuerpo === null) return { status: 400 };
  if (cuerpo.webhook_type !== "status.updated") return { status: 200 };

  const sessionId = cuerpo.session_id;
  if (typeof sessionId !== "string" || !ID_SEGURO.test(sessionId)) return { status: 400 };
  const eventoId = typeof cuerpo.event_id === "string" && cuerpo.event_id !== "" ? cuerpo.event_id : undefined;

  try {
    const solicitud = await db.collection(COLECCION_SOLICITUDES_VERIFICACION).doc(sessionId).get();
    if (!solicitud.exists) {
      // Sesion de otro ambiente o ajena a KineCare: se acusa recibo para que Didit no reintente.
      logger.warn("Didit: webhook de una sesion desconocida", { solicitudId: sessionId });
      return { status: 200 };
    }
    if (eventoId !== undefined && solicitud.get("ultimoEventoId") === eventoId) return { status: 200 }; // reintento ya procesado

    const decision = await deps.proveedor.obtenerDecision(sessionId);
    await aplicarDecision(db, { solicitudId: sessionId, decision, eventoId, ahora });
    return { status: 200 };
  } catch (e) {
    logger.error("Didit: fallo procesar webhook", { solicitudId: sessionId, mensaje: e instanceof Error ? e.message : String(e) });
    return { status: 500 };
  }
}

/**
 * `retornoVerificacion`: Didit vuelve aqui al terminar (`callback`, con `verificationSessionId` y
 * `status` en la query) y esta funcion salta a la app por deep link. Solo reenvia el id de sesion
 * (validado); el resultado real lo consulta la app a `estadoVerificacion`, jamas se confia en `status`.
 */
export function retornoVerificacionHandler(query: Record<string, unknown>): RespuestaHttp {
  const id = query.verificationSessionId;
  const destino =
    typeof id === "string" && ID_SEGURO.test(id)
      ? `${ESQUEMA_APP}://verificacion/resultado?solicitudId=${id}`
      : `${ESQUEMA_APP}://verificacion/resultado`;
  return { status: 302, redirect: destino };
}
