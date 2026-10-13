import { getMessaging } from "firebase-admin/messaging";
import type { Mensajeria } from "./notificaciones.js";

/** Codigos de FCM que indican que el token ya no sirve y hay que borrarlo. */
const CODIGOS_TOKEN_INVALIDO = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

/** Implementacion real sobre Firebase Admin Messaging. Los tests no la usan: inyectan un doble. */
export function crearMensajeriaAdmin(): Mensajeria {
  return {
    async enviar(tokens, mensaje) {
      const lote = await getMessaging().sendEachForMulticast({
        tokens,
        notification: { title: mensaje.titulo, body: mensaje.cuerpo },
        data: mensaje.data,
        android: { priority: "high" },
      });
      const tokensInvalidos: string[] = [];
      lote.responses.forEach((r, i) => {
        if (!r.success && r.error !== undefined && CODIGOS_TOKEN_INVALIDO.has(r.error.code)) {
          tokensInvalidos.push(tokens[i]);
        }
      });
      return { tokensInvalidos };
    },
  };
}
