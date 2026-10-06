import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { COLECCION_CUENTAS_MP, REFRESCO_ANTICIPADO_MINUTOS } from "../constantes.js";
import { errorDeNegocio } from "../errores.js";
import { cifrar, descifrar } from "./cifrado.js";
import type { Pasarela, TokensVendedor } from "./pasarela.js";

const MS_MINUTO = 60_000;

function sinCuenta() {
  return errorDeNegocio(
    "failed-precondition",
    "PROFESIONAL_SIN_CUENTA_MP",
    "El profesional aún no conecta su cuenta de Mercado Pago, así que no puede recibir pagos.",
  );
}

/** Guarda (o reemplaza) la cuenta conectada de un profesional. Los tokens van cifrados. */
export async function guardarCuenta(
  db: Firestore,
  clave: Buffer,
  profesionalId: string,
  tokens: TokensVendedor,
): Promise<void> {
  const cuentaRef = db.collection(COLECCION_CUENTAS_MP).doc(profesionalId);
  const profesionalRef = db.collection("profesionales").doc(profesionalId);
  const batch = db.batch();
  batch.set(cuentaRef, {
    userId: tokens.userId,
    scope: tokens.scope,
    accessTokenCifrado: cifrar(tokens.accessToken, clave, profesionalId),
    refreshTokenCifrado: cifrar(tokens.refreshToken, clave, profesionalId),
    expiraEn: Timestamp.fromDate(tokens.expiraEn),
    version: 1,
    requiereReautorizacion: false,
    conectadaEn: FieldValue.serverTimestamp(),
    actualizadaEn: FieldValue.serverTimestamp(),
  });
  // Bandera publica de solo lectura para la UI (las Security Rules impiden que el dueno la escriba).
  batch.set(profesionalRef, { mercadoPagoConectado: true }, { merge: true });
  await batch.commit();
}

/**
 * Devuelve un access token vigente del profesional, renovandolo si vence pronto. Nunca cae al
 * token de la plataforma: sin cuenta conectada (o con la renovacion rota) falla con
 * PROFESIONAL_SIN_CUENTA_MP. El refresh rota ambos tokens, asi que se guarda con un
 * compare-and-set sobre `version`: si otra instancia renovo primero, se usa su resultado.
 */
export async function tokenVigente(
  db: Firestore,
  pasarela: Pasarela,
  clave: Buffer,
  profesionalId: string,
  ahora: Date,
): Promise<string> {
  const cuentaRef = db.collection(COLECCION_CUENTAS_MP).doc(profesionalId);
  const cuenta = await cuentaRef.get();
  if (!cuenta.exists || cuenta.get("requiereReautorizacion") === true) throw sinCuenta();

  const expiraEn = cuenta.get("expiraEn");
  if (!(expiraEn instanceof Timestamp)) throw sinCuenta();
  if (expiraEn.toMillis() - REFRESCO_ANTICIPADO_MINUTOS * MS_MINUTO > ahora.getTime()) {
    return descifrar(cuenta.get("accessTokenCifrado") as string, clave, profesionalId);
  }

  const version = cuenta.get("version") as number;
  let nuevos: TokensVendedor;
  try {
    nuevos = await pasarela.refrescar(descifrar(cuenta.get("refreshTokenCifrado") as string, clave, profesionalId));
  } catch (e) {
    logger.error("Mercado Pago: fallo renovar token del vendedor", {
      profesionalId,
      mensaje: e instanceof Error ? e.message : String(e),
    });
    await db.batch()
      .update(cuentaRef, { requiereReautorizacion: true, actualizadaEn: FieldValue.serverTimestamp() })
      .set(db.collection("profesionales").doc(profesionalId), { mercadoPagoConectado: false }, { merge: true })
      .commit();
    throw sinCuenta();
  }

  return db.runTransaction(async (tx) => {
    const actual = await tx.get(cuentaRef);
    if (actual.get("version") !== version) {
      return descifrar(actual.get("accessTokenCifrado") as string, clave, profesionalId);
    }
    tx.update(cuentaRef, {
      accessTokenCifrado: cifrar(nuevos.accessToken, clave, profesionalId),
      refreshTokenCifrado: cifrar(nuevos.refreshToken, clave, profesionalId),
      expiraEn: Timestamp.fromDate(nuevos.expiraEn),
      version: version + 1,
      actualizadaEn: FieldValue.serverTimestamp(),
    });
    return nuevos.accessToken;
  });
}
