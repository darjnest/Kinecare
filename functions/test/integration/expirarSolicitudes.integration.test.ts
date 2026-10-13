// Integracion de expirarSolicitudesHandler contra el Firestore Emulator.
import assert from "node:assert/strict";
import { before, beforeEach, describe, it } from "node:test";
import { initializeApp } from "firebase-admin/app";
import { Timestamp, getFirestore, type Firestore } from "firebase-admin/firestore";
import { ESTADO_AL_EXPIRAR, expirarSolicitudesHandler } from "../../src/expirarSolicitudes.js";

const PROJECT_ID = "demo-kinecare";
const emulador = process.env.FIRESTORE_EMULATOR_HOST;
if (emulador === undefined) {
  throw new Error(
    "FIRESTORE_EMULATOR_HOST no esta definido: corre `npm run test:emulator` (o `npm run test:unit` para omitir estos tests).",
  );
}

const AHORA = new Date("2026-10-01T12:00:00Z");
let db: Firestore;

async function limpiarEmulador() {
  const r = await fetch(`http://${emulador}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, {
    method: "DELETE",
  });
  assert.equal(r.status, 200);
}

async function sembrar(estado: string, fechaHora: string | null, extra: Record<string, unknown> = {}) {
  const doc: Record<string, unknown> = {
    clienteId: "cli",
    profesionalId: "pro",
    estado,
    pago: { id: null, monto: 1000, estado: "PENDIENTE" },
    actualizadoEn: Timestamp.fromDate(new Date("2026-09-29T12:00:00Z")),
    ...extra,
  };
  if (fechaHora !== null) doc.fechaHora = Timestamp.fromDate(new Date(fechaHora));
  return (await db.collection("reservas").add(doc)).id;
}

const leer = async (id: string) => (await db.collection("reservas").doc(id).get()).data()!;

before(() => {
  initializeApp({ projectId: PROJECT_ID });
  db = getFirestore();
});
beforeEach(limpiarEmulador);

describe("expirarSolicitudes", () => {
  it("expira solo las SOLICITADA con la cita ya pasada", async () => {
    const vencida = await sembrar("SOLICITADA", "2026-10-01T11:59:00Z");
    const futura = await sembrar("SOLICITADA", "2026-10-01T12:01:00Z");
    const justoAhora = await sembrar("SOLICITADA", AHORA.toISOString());
    const confirmada = await sembrar("CONFIRMADA", "2026-09-30T10:00:00Z");
    const cancelada = await sembrar("CANCELADA_CLIENTE", "2026-09-30T10:00:00Z");
    const sinFecha = await sembrar("SOLICITADA", null);

    const r = await expirarSolicitudesHandler(db, AHORA);
    assert.deepEqual(r, { expiradas: 1, lotes: 1 });

    const doc = await leer(vencida);
    assert.equal(doc.estado, ESTADO_AL_EXPIRAR);
    assert.equal(doc.expirada, true);
    assert.ok(doc.expiradaEn instanceof Timestamp);
    assert.ok(doc.actualizadoEn.toMillis() > new Date("2026-09-29T12:00:00Z").getTime());
    assert.equal(doc.respondidaEn, undefined, "no fue una respuesta del profesional");

    assert.equal((await leer(futura)).estado, "SOLICITADA");
    assert.equal((await leer(justoAhora)).estado, "SOLICITADA");
    assert.equal((await leer(confirmada)).estado, "CONFIRMADA");
    assert.equal((await leer(cancelada)).estado, "CANCELADA_CLIENTE");
    assert.equal((await leer(sinFecha)).estado, "SOLICITADA");
  });

  it("es idempotente: una segunda ejecucion no cambia nada", async () => {
    const id = await sembrar("SOLICITADA", "2026-09-30T10:00:00Z");
    assert.equal((await expirarSolicitudesHandler(db, AHORA)).expiradas, 1);
    const despuesDeLaPrimera = await leer(id);
    const r = await expirarSolicitudesHandler(db, new Date(AHORA.getTime() + 15 * 60_000));
    assert.deepEqual(r, { expiradas: 0, lotes: 0 });
    assert.deepEqual(await leer(id), despuesDeLaPrimera);
  });

  it("procesa por lotes (7 vencidas, lotes de 3) y respeta el tope de lotes", async () => {
    for (let i = 0; i < 7; i++) await sembrar("SOLICITADA", `2026-09-30T1${i}:00:00Z`);
    const r = await expirarSolicitudesHandler(db, AHORA, 3, 20);
    assert.deepEqual(r, { expiradas: 7, lotes: 3 });
    assert.equal((await db.collection("reservas").where("estado", "==", "SOLICITADA").get()).size, 0);

    await limpiarEmulador();
    for (let i = 0; i < 7; i++) await sembrar("SOLICITADA", `2026-09-30T1${i}:00:00Z`);
    const acotado = await expirarSolicitudesHandler(db, AHORA, 3, 2);
    assert.deepEqual(acotado, { expiradas: 6, lotes: 2 });
    // La siguiente ejecucion termina el trabajo.
    assert.equal((await expirarSolicitudesHandler(db, AHORA, 3, 2)).expiradas, 1);
  });

  it("sin candidatas no hace nada", async () => {
    assert.deepEqual(await expirarSolicitudesHandler(db, AHORA), { expiradas: 0, lotes: 0 });
  });
});
