// Integracion de notificarCambioReserva contra el Firestore Emulator, con FCM simulado
// (no se envia nada real).
import assert from "node:assert/strict";
import { before, beforeEach, describe, it } from "node:test";
import { initializeApp } from "firebase-admin/app";
import { FieldValue, Timestamp, getFirestore, type Firestore } from "firebase-admin/firestore";
import { notificarCambioReserva, type Mensajeria, type MensajePush } from "../../src/notificaciones.js";

const PROJECT_ID = "demo-kinecare";
const emulador = process.env.FIRESTORE_EMULATOR_HOST;
if (emulador === undefined) {
  throw new Error(
    "FIRESTORE_EMULATOR_HOST no esta definido: corre `npm run test:emulator` (o `npm run test:unit` para omitir estos tests).",
  );
}

let db: Firestore;

interface Envio {
  tokens: string[];
  mensaje: MensajePush;
}

function mensajeriaFalsa(invalidos: string[] = [], falla = false) {
  const envios: Envio[] = [];
  const mensajeria: Mensajeria = {
    async enviar(tokens, mensaje) {
      if (falla) throw new Error("FCM caido");
      envios.push({ tokens, mensaje });
      return { tokensInvalidos: invalidos };
    },
  };
  return { envios, mensajeria };
}

async function limpiarEmulador() {
  const r = await fetch(`http://${emulador}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, {
    method: "DELETE",
  });
  assert.equal(r.status, 200);
}

async function sembrarToken(uid: string, token: string) {
  await db
    .collection("usuarios")
    .doc(uid)
    .collection("dispositivos")
    .doc(token)
    .set({ plataforma: "ANDROID", actualizadoEn: FieldValue.serverTimestamp() });
}

const tokensDe = async (uid: string) =>
  (await db.collection("usuarios").doc(uid).collection("dispositivos").get()).docs.map((d) => d.id).sort();

const reserva = (estado: string, extra: Record<string, unknown> = {}) => ({
  clienteId: "cli",
  profesionalId: "pro",
  estado,
  fechaHora: Timestamp.fromDate(new Date("2026-10-16T18:00:00Z")),
  ...extra,
});

before(() => {
  initializeApp({ projectId: PROJECT_ID });
  db = getFirestore();
});
beforeEach(limpiarEmulador);

describe("notificarCambioReserva", () => {
  it("una reserva nueva envia 'Nueva solicitud' a todos los dispositivos del profesional, y solo a ellos", async () => {
    await sembrarToken("pro", "tok-pro-1");
    await sembrarToken("pro", "tok-pro-2");
    await sembrarToken("cli", "tok-cli");
    const { envios, mensajeria } = mensajeriaFalsa();
    const r = await notificarCambioReserva(db, mensajeria, "r1", undefined, reserva("SOLICITADA"));
    assert.deepEqual(r, { destinos: 2, tokensBorrados: 0 });
    assert.equal(envios.length, 1);
    assert.deepEqual([...envios[0].tokens].sort(), ["tok-pro-1", "tok-pro-2"]);
    assert.equal(envios[0].mensaje.titulo, "Nueva solicitud");
    assert.deepEqual(envios[0].mensaje.data, { tipo: "NUEVA_SOLICITUD", reservaId: "r1" });
  });

  it("CONFIRMADA envia al cliente", async () => {
    await sembrarToken("pro", "tok-pro");
    await sembrarToken("cli", "tok-cli");
    const { envios, mensajeria } = mensajeriaFalsa();
    await notificarCambioReserva(db, mensajeria, "r1", reserva("SOLICITADA"), reserva("CONFIRMADA"));
    assert.deepEqual(envios.map((e) => e.tokens), [["tok-cli"]]);
    assert.equal(envios[0].mensaje.data.tipo, "RESERVA_CONFIRMADA");
  });

  it("borra los tokens que FCM declara invalidos y conserva los demas", async () => {
    await sembrarToken("cli", "tok-vivo");
    await sembrarToken("cli", "tok-muerto");
    const { mensajeria } = mensajeriaFalsa(["tok-muerto", "token-de-otro-usuario"]);
    const r = await notificarCambioReserva(db, mensajeria, "r1", reserva("SOLICITADA"), reserva("COMPLETADA"));
    assert.deepEqual(r, { destinos: 2, tokensBorrados: 1 });
    assert.deepEqual(await tokensDe("cli"), ["tok-vivo"]);
  });

  it("no envia si el destinatario no tiene dispositivos", async () => {
    const { envios, mensajeria } = mensajeriaFalsa();
    const r = await notificarCambioReserva(db, mensajeria, "r1", undefined, reserva("SOLICITADA"));
    assert.deepEqual(r, { destinos: 0, tokensBorrados: 0 });
    assert.equal(envios.length, 0);
  });

  it("no envia cuando el cambio no es de estado", async () => {
    await sembrarToken("cli", "tok-cli");
    const { envios, mensajeria } = mensajeriaFalsa();
    await notificarCambioReserva(db, mensajeria, "r1", reserva("CONFIRMADA"), reserva("CONFIRMADA", { pago: {} }));
    await notificarCambioReserva(db, mensajeria, "r1", reserva("CONFIRMADA"), undefined);
    assert.equal(envios.length, 0);
  });

  it("si FCM falla no lanza (el trigger no debe reintentar) y no borra tokens", async () => {
    await sembrarToken("cli", "tok-cli");
    const { mensajeria } = mensajeriaFalsa([], true);
    const r = await notificarCambioReserva(db, mensajeria, "r1", reserva("CONFIRMADA"), reserva("COMPLETADA"));
    assert.deepEqual(r, { destinos: 1, tokensBorrados: 0 });
    assert.deepEqual(await tokensDe("cli"), ["tok-cli"]);
  });

  it("una solicitud expirada avisa al cliente con otro tipo", async () => {
    await sembrarToken("cli", "tok-cli");
    const { envios, mensajeria } = mensajeriaFalsa();
    await notificarCambioReserva(db, mensajeria, "r1", reserva("SOLICITADA"), reserva("RECHAZADA", { expirada: true }));
    assert.equal(envios[0].mensaje.data.tipo, "SOLICITUD_EXPIRADA");
  });
});
