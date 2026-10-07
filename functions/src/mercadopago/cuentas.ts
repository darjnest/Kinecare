import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { COLECCION_CUENTAS_MP, REFRESCO_ANTICIPADO_MINUTOS } from "../constantes.js";
import { errorDeNegocio } from "../errores.js";
import { cifrar, descifrar } from "./cifrado.js";
import { TokenRechazadoError, type Pasarela, type TokensVendedor } from "./pasarela.js";

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
  // `update` y no `set merge`: si el perfil no existe falla (y el batch entero se descarta) en vez
  // de crear un documento `profesionales/{id}` incompleto que aparecería en la búsqueda.
  batch.update(profesionalRef, { mercadoPagoConectado: true });
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
    const mensaje = e instanceof Error ? e.message : String(e);
    if (!(e instanceof TokenRechazadoError)) {
      // Timeout o 5xx de Mercado Pago: transitorio. NO se desconecta al profesional.
      logger.error("Mercado Pago: fallo transitorio al renovar el token del vendedor", { profesionalId, mensaje });
      throw errorDeNegocio("unavailable", "PASARELA_NO_DISPONIBLE", "No pudimos comunicarnos con Mercado Pago. Intenta de nuevo.");
    }
    // El refresh token se rechazo. Si otra instancia ya lo roto (version distinta) su token es
    // valido y se usa; si no, de verdad hay que reautorizar.
    const actual = await cuentaRef.get();
    if (actual.get("version") !== version && actual.get("requiereReautorizacion") !== true) {
      return descifrar(actual.get("accessTokenCifrado") as string, clave, profesionalId);
    }
    logger.error("Mercado Pago: refresh token rechazado; la cuenta requiere reautorizar", { profesionalId, mensaje });
    await cuentaRef.update({ requiereReautorizacion: true, actualizadaEn: FieldValue.serverTimestamp() });
    // Si el perfil ya no existe no hay bandera que bajar: se ignora, no se crea un perfil vacio.
    await db.collection("profesionales").doc(profesionalId).update({ mercadoPagoConectado: false }).catch(() => undefined);
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
