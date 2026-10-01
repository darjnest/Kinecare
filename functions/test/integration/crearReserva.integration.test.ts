// Integracion de crearReservaHandler contra el Firestore Emulator (Admin SDK real).
// Ejecutar con: npm run test:emulator  (levanta el emulador y corre npm test).
import assert from "node:assert/strict";
import { before, beforeEach, describe, it } from "node:test";
import { initializeApp } from "firebase-admin/app";
import { Timestamp, getFirestore, type Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { crearReservaHandler } from "../../src/crearReserva.js";
import { esperarError } from "../helpers.js";

const PROJECT_ID = "demo-kinecare";
const emulador = process.env.FIRESTORE_EMULATOR_HOST;
if (emulador === undefined) {
  throw new Error(
    "FIRESTORE_EMULATOR_HOST no esta definido: corre `npm run test:emulator` (o `npm run test:unit` para omitir estos tests).",
  );
}

// "Hoy" fijo: jueves 2026-10-01 12:00Z (09:00 en Chile, horario de verano).
const AHORA = new Date("2026-10-01T12:00:00Z");
// Lunes 2026-10-05 10:00 hora de Chile (UTC-3).
const LUNES_10 = "2026-10-05T13:00:00Z";

const CLIENTE = "cliente1";
const PRO = "pro1";
const SRV_CONSULTA = "srvConsulta"; // CONSULTA, 60 min, $25.000
const SRV_DOMICILIO = "srvDomicilio"; // DOMICILIO, 45 min, $35.000
const SRV_INACTIVO = "srvInactivo";

let db: Firestore;

const dirOk = { calle: "  Av. Providencia ", numero: " 1234 ", comuna: "Providencia ", ciudad: "Santiago" };

function pedido(extra: Record<string, unknown> = {}) {
  return { profesionalId: PRO, servicioId: SRV_CONSULTA, fechaHora: LUNES_10, ...extra };
}

function reservar(data: unknown, uid: string | undefined = CLIENTE, ahora = AHORA) {
  return crearReservaHandler(db, uid, data, ahora);
}

async function limpiarEmulador() {
  const r = await fetch(`http://${emulador}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, {
    method: "DELETE",
  });
  assert.equal(r.status, 200);
}

async function sembrar() {
  await db.collection("usuarios").doc(CLIENTE).set({ rol: "CLIENTE", nombre: "Cata Cliente" });
  await db.collection("usuarios").doc("cliente2").set({ rol: "CLIENTE", nombre: "Otro Cliente" });
  await db.collection("usuarios").doc(PRO).set({ rol: "PROFESIONAL", nombre: "Pro Uno" });
  await db.collection("usuarios").doc("pro2").set({ rol: "PROFESIONAL", nombre: "Pro Dos" });
  await db.collection("profesionales").doc(PRO).set({
    especialidades: ["Kinesiologia"],
    disponibilidad: [
      { diaSemana: "MONDAY", horaInicio: "09:00", horaFin: "18:00", activo: true },
      { diaSemana: "TUESDAY", horaInicio: "09:00", horaFin: "18:00", activo: false },
    ],
  });
  await db.collection("profesionales").doc("pro2").set({
    disponibilidad: [{ diaSemana: "MONDAY", horaInicio: "09:00", horaFin: "18:00", activo: true }],
  });
  const servicios = db.collection("profesionales").doc(PRO).collection("servicios");
  await servicios.doc(SRV_CONSULTA).set({
    nombre: "Kinesiologia general",
    modalidad: "CONSULTA",
    duracionMinutos: 60,
    precio: 25000,
    activo: true,
  });
  await servicios.doc(SRV_DOMICILIO).set({
    nombre: "Masoterapia a domicilio",
    modalidad: "DOMICILIO",
    duracionMinutos: 45,
    precio: 35000,
    activo: true,
  });
  await servicios.doc(SRV_INACTIVO).set({
    nombre: "Descontinuado",
    modalidad: "CONSULTA",
    duracionMinutos: 30,
    precio: 10000,
    activo: false,
  });
}

/** Inserta una reserva ya existente (como si otra la hubiera creado) para probar solapes. */
async function sembrarReserva(
  fechaHora: string,
  opciones: { estado?: string; duracionMinutos?: number | null; profesionalId?: string } = {},
) {
  const doc: Record<string, unknown> = {
    clienteId: "cliente2",
    profesionalId: opciones.profesionalId ?? PRO,
    servicioId: SRV_CONSULTA,
    modalidad: "CONSULTA",
    fechaHora: Timestamp.fromDate(new Date(fechaHora)),
    estado: opciones.estado ?? "CONFIRMADA",
  };
  if (opciones.duracionMinutos !== null) doc.duracionMinutos = opciones.duracionMinutos ?? 60;
  await db.collection("reservas").add(doc);
}

async function contarReservas() {
  return (await db.collection("reservas").get()).size;
}

before(() => {
  initializeApp({ projectId: PROJECT_ID });
  db = getFirestore();
});

beforeEach(async () => {
  await limpiarEmulador();
  await sembrar();
});

describe("crearReserva: camino feliz", () => {
  it("crea la reserva CONSULTA con los campos del contrato", async () => {
    const { reservaId } = await reservar(pedido());
    assert.match(reservaId, /^[A-Za-z0-9]{20}$/);

    const doc = await db.collection("reservas").doc(reservaId).get();
    assert.ok(doc.exists);
    const d = doc.data()!;
    assert.equal(d.clienteId, CLIENTE);
    assert.equal(d.profesionalId, PRO);
    assert.equal(d.servicioId, SRV_CONSULTA);
    assert.equal(d.modalidad, "CONSULTA");
    assert.ok(d.fechaHora instanceof Timestamp);
    assert.equal(d.fechaHora.toDate().toISOString(), "2026-10-05T13:00:00.000Z");
    assert.equal(d.duracionMinutos, 60);
    assert.equal(d.direccion, null);
    assert.equal(d.estado, "SOLICITADA");
    assert.deepEqual(d.pago, { id: null, monto: 25000, estado: "PENDIENTE" });
    assert.equal(d.comisionPorcentaje, 0.1);
    assert.ok(d.creadoEn instanceof Timestamp);
    assert.ok(d.actualizadoEn instanceof Timestamp);
    assert.deepEqual(Object.keys(d).sort(), [
      "actualizadoEn", "clienteId", "comisionPorcentaje", "creadoEn", "direccion", "duracionMinutos",
      "estado", "fechaHora", "modalidad", "pago", "profesionalId", "servicioId",
    ]);
  });

  it("DOMICILIO guarda la direccion normalizada", async () => {
    const { reservaId } = await reservar(
      pedido({
        servicioId: SRV_DOMICILIO,
        direccion: { ...dirOk, lat: -33.43, lng: -70.61, indicaciones: " tocar timbre " },
      }),
    );
    const d = (await db.collection("reservas").doc(reservaId).get()).data()!;
    assert.equal(d.modalidad, "DOMICILIO");
    assert.equal(d.duracionMinutos, 45);
    assert.deepEqual(d.direccion, {
      calle: "Av. Providencia",
      numero: "1234",
      comuna: "Providencia",
      ciudad: "Santiago",
      lat: -33.43,
      lng: -70.61,
      indicaciones: "tocar timbre",
    });
    assert.deepEqual(d.pago, { id: null, monto: 35000, estado: "PENDIENTE" });
  });

  it("modalidad no domicilio descarta la direccion enviada", async () => {
    const { reservaId } = await reservar(pedido({ direccion: dirOk }));
    assert.equal((await db.collection("reservas").doc(reservaId).get()).get("direccion"), null);
  });

  it("ignora precio, comision y modalidad enviados por el cliente", async () => {
    const { reservaId } = await reservar(
      pedido({ precio: 1, monto: 1, comisionPorcentaje: 0.99, modalidad: "ONLINE", estado: "CONFIRMADA", clienteId: "otro" }),
    );
    const d = (await db.collection("reservas").doc(reservaId).get()).data()!;
    assert.equal(d.pago.monto, 25000);
    assert.equal(d.comisionPorcentaje, 0.1);
    assert.equal(d.modalidad, "CONSULTA");
    assert.equal(d.estado, "SOLICITADA");
    assert.equal(d.clienteId, CLIENTE);
  });

  it("acepta exactamente 60 minutos de anticipacion y exactamente 60 dias", async () => {
    const justo = new Date("2026-10-05T12:00:00Z"); // ahora; el slot es 13:00Z
    await reservar(pedido(), CLIENTE, justo);
    // 60 dias despues de AHORA = 2026-11-30 (lunes) 12:00Z = 09:00 local.
    await reservar(pedido({ fechaHora: "2026-11-30T12:00:00Z" }));
    assert.equal(await contarReservas(), 2);
  });

  it("acepta fechaHora con offset (mismo instante)", async () => {
    const { reservaId } = await reservar(pedido({ fechaHora: "2026-10-05T10:00:00-03:00" }));
    const d = (await db.collection("reservas").doc(reservaId).get()).data()!;
    assert.equal(d.fechaHora.toDate().toISOString(), LUNES_10.replace(":00Z", ":00.000Z"));
  });
});

describe("crearReserva: errores por motivo", () => {
  it("unauthenticated / SIN_SESION", async () => {
    await esperarError(() => crearReservaHandler(db, undefined, pedido(), AHORA), "unauthenticated", "SIN_SESION");
    await esperarError(() => reservar(pedido(), ""), "unauthenticated", "SIN_SESION");
    assert.equal(await contarReservas(), 0);
  });

  it("permission-denied / ROL_INVALIDO: profesional, sin documento de usuario y auto-reserva", async () => {
    await esperarError(() => reservar(pedido(), "pro2"), "permission-denied", "ROL_INVALIDO");
    await esperarError(() => reservar(pedido(), "fantasma"), "permission-denied", "ROL_INVALIDO");
    await db.collection("usuarios").doc(PRO).update({ rol: "CLIENTE" });
    await esperarError(() => reservar(pedido(), PRO), "permission-denied", "ROL_INVALIDO");
    assert.equal(await contarReservas(), 0);
  });

  it("el rol se lee del documento: un campo `rol` en el payload no sirve", async () => {
    await esperarError(() => reservar(pedido({ rol: "CLIENTE" }), "pro2"), "permission-denied", "ROL_INVALIDO");
  });

  it("invalid-argument / DATOS_INVALIDOS", async () => {
    const malos: unknown[] = [
      undefined,
      null,
      "x",
      pedido({ profesionalId: "" }),
      pedido({ profesionalId: 3 }),
      pedido({ servicioId: "  " }),
      pedido({ servicioId: undefined }),
      pedido({ fechaHora: "no es fecha" }),
      pedido({ fechaHora: "2026-10-05T13:00:00" }),
      pedido({ fechaHora: 12345 }),
      pedido({ direccion: "calle" }),
      pedido({ direccion: { ...dirOk, calle: 5 } }),
      pedido({ direccion: { ...dirOk, calle: "x".repeat(121) } }),
      pedido({ direccion: { ...dirOk, indicaciones: "x".repeat(301) } }),
      pedido({ direccion: { ...dirOk, lat: "1" } }),
    ];
    for (const m of malos) await esperarError(() => reservar(m), "invalid-argument", "DATOS_INVALIDOS");
    assert.equal(await contarReservas(), 0);
  });

  it("invalid-argument / DIRECCION_REQUERIDA para DOMICILIO sin direccion o incompleta", async () => {
    const dom = { servicioId: SRV_DOMICILIO };
    const incompletos: unknown[] = [
      pedido(dom),
      pedido({ ...dom, direccion: null }),
      pedido({ ...dom, direccion: { ...dirOk, calle: "   " } }),
      pedido({ ...dom, direccion: { ...dirOk, numero: "" } }),
      pedido({ ...dom, direccion: { ...dirOk, comuna: null } }),
      pedido({ ...dom, direccion: { ciudad: "Santiago" } }),
    ];
    for (const m of incompletos) await esperarError(() => reservar(m), "invalid-argument", "DIRECCION_REQUERIDA");
    assert.equal(await contarReservas(), 0);
  });

  it("not-found / SERVICIO_NO_DISPONIBLE: profesional o servicio inexistentes", async () => {
    await esperarError(() => reservar(pedido({ profesionalId: "noExiste" })), "not-found", "SERVICIO_NO_DISPONIBLE");
    await esperarError(() => reservar(pedido({ servicioId: "noExiste" })), "not-found", "SERVICIO_NO_DISPONIBLE");
    // Un servicio de OTRO profesional tampoco vale.
    await esperarError(() => reservar(pedido({ profesionalId: "pro2" })), "not-found", "SERVICIO_NO_DISPONIBLE");
  });

  it("failed-precondition / SERVICIO_NO_DISPONIBLE: servicio inactivo o con datos corruptos", async () => {
    await esperarError(() => reservar(pedido({ servicioId: SRV_INACTIVO })), "failed-precondition", "SERVICIO_NO_DISPONIBLE");
    const servicios = db.collection("profesionales").doc(PRO).collection("servicios");
    await servicios.doc("roto1").set({ modalidad: "CONSULTA", duracionMinutos: "60", precio: 1000, activo: true });
    await servicios.doc("roto2").set({ modalidad: "FANTASMA", duracionMinutos: 60, precio: 1000, activo: true });
    await servicios.doc("roto3").set({ modalidad: "CONSULTA", duracionMinutos: 60, activo: true });
    for (const id of ["roto1", "roto2", "roto3"]) {
      await esperarError(() => reservar(pedido({ servicioId: id })), "failed-precondition", "SERVICIO_NO_DISPONIBLE");
    }
  });

  it("failed-precondition / ANTICIPACION_INSUFICIENTE: menos de 60 min, pasado y mas de 60 dias", async () => {
    const ahora = new Date("2026-10-05T12:00:01Z"); // el slot de 13:00Z queda a 59m59s
    await esperarError(() => reservar(pedido(), CLIENTE, ahora), "failed-precondition", "ANTICIPACION_INSUFICIENTE");
    await esperarError(
      () => reservar(pedido({ fechaHora: "2026-09-28T13:00:00Z" })),
      "failed-precondition",
      "ANTICIPACION_INSUFICIENTE",
    );
    // 2026-11-30 12:00:01Z es 60 dias y 1 s despues de AHORA.
    await esperarError(
      () => reservar(pedido({ fechaHora: "2026-11-30T12:00:01Z" })),
      "failed-precondition",
      "ANTICIPACION_INSUFICIENTE",
    );
    await esperarError(
      () => reservar(pedido({ fechaHora: "2026-12-07T13:00:00Z" })),
      "failed-precondition",
      "ANTICIPACION_INSUFICIENTE",
    );
  });

  it("failed-precondition / FUERA_DE_HORARIO", async () => {
    const fuera = [
      "2026-10-05T11:59:00Z", // 08:59 local, antes de abrir
      "2026-10-05T20:30:00Z", // 17:30-18:30 local, termina despues del cierre
      "2026-10-06T13:00:00Z", // martes: dia inactivo
      "2026-10-07T13:00:00Z", // miercoles: sin disponibilidad
    ];
    for (const f of fuera) {
      await esperarError(() => reservar(pedido({ fechaHora: f })), "failed-precondition", "FUERA_DE_HORARIO");
    }
  });

  it("FUERA_DE_HORARIO: convierte a hora de Chile (la hora UTC no importa)", async () => {
    // 09:00Z = 06:00 local un lunes: dentro del horario si se leyera como UTC, fuera en Chile.
    await esperarError(
      () => reservar(pedido({ fechaHora: "2026-10-05T09:00:00Z" })),
      "failed-precondition",
      "FUERA_DE_HORARIO",
    );
    // 12:00Z = 09:00 local: ok.
    await reservar(pedido({ fechaHora: "2026-10-05T12:00:00Z" }));
  });

  it("FUERA_DE_HORARIO: profesional sin disponibilidad", async () => {
    await db.collection("profesionales").doc(PRO).update({ disponibilidad: [] });
    await esperarError(() => reservar(pedido()), "failed-precondition", "FUERA_DE_HORARIO");
    await db.collection("profesionales").doc(PRO).update({ disponibilidad: null });
    await esperarError(() => reservar(pedido()), "failed-precondition", "FUERA_DE_HORARIO");
  });

  it("already-exists / HORARIO_OCUPADO por solape exacto, parcial y contenido", async () => {
    await sembrarReserva(LUNES_10); // 10:00-11:00 local
    for (const f of [
      LUNES_10,
      "2026-10-05T13:30:00Z", // empieza durante
      "2026-10-05T12:30:00Z", // termina durante
    ]) {
      await esperarError(() => reservar(pedido({ fechaHora: f })), "already-exists", "HORARIO_OCUPADO");
    }
    assert.equal(await contarReservas(), 1);
  });

  it("HORARIO_OCUPADO considera la duracion de la reserva existente (larga que empezo antes)", async () => {
    await sembrarReserva("2026-10-05T12:00:00Z", { duracionMinutos: 180 }); // 09:00-12:00 local
    await esperarError(() => reservar(pedido()), "already-exists", "HORARIO_OCUPADO");
    await reservar(pedido({ fechaHora: "2026-10-05T15:00:00Z" })); // 12:00 local: justo despues
  });

  it("HORARIO_OCUPADO: reserva legada sin duracionMinutos se asume de 60 min", async () => {
    await sembrarReserva("2026-10-05T12:30:00Z", { duracionMinutos: null }); // 09:30-10:30 local
    await esperarError(() => reservar(pedido()), "already-exists", "HORARIO_OCUPADO");
  });

  it("citas contiguas no chocan", async () => {
    await sembrarReserva(LUNES_10); // 10:00-11:00
    await reservar(pedido({ fechaHora: "2026-10-05T14:00:00Z" })); // 11:00-12:00
    await reservar(pedido({ fechaHora: "2026-10-05T12:00:00Z" })); // 09:00-10:00
    assert.equal(await contarReservas(), 3);
  });

  it("solo SOLICITADA, CONFIRMADA y EN_CURSO ocupan agenda", async () => {
    for (const estado of ["COMPLETADA", "CANCELADA_CLIENTE", "CANCELADA_PROFESIONAL", "RECHAZADA"]) {
      await sembrarReserva(LUNES_10, { estado });
    }
    await reservar(pedido());
    for (const estado of ["SOLICITADA", "CONFIRMADA", "EN_CURSO"]) {
      await limpiarEmulador();
      await sembrar();
      await sembrarReserva(LUNES_10, { estado });
      await esperarError(() => reservar(pedido()), "already-exists", "HORARIO_OCUPADO");
    }
  });

  it("la reserva de otro profesional no bloquea", async () => {
    await sembrarReserva(LUNES_10, { profesionalId: "pro2" });
    await reservar(pedido());
  });

  it("el mismo cliente tampoco puede reservar dos veces el mismo slot", async () => {
    await reservar(pedido());
    await esperarError(() => reservar(pedido()), "already-exists", "HORARIO_OCUPADO");
  });

  it("todos los errores son HttpsError con details.motivo", async () => {
    try {
      await reservar(pedido({ fechaHora: "x" }));
      assert.fail("debio fallar");
    } catch (e) {
      assert.ok(e instanceof HttpsError);
      assert.deepEqual(e.details, { motivo: "DATOS_INVALIDOS" });
    }
  });
});

describe("crearReserva: concurrencia", () => {
  it("N llamadas en paralelo por el mismo slot (clientes distintos): exactamente una gana", async () => {
    const clientes = Array.from({ length: 8 }, (_, i) => `par${i}`);
    for (const c of clientes) await db.collection("usuarios").doc(c).set({ rol: "CLIENTE" });

    const resultados = await Promise.allSettled(clientes.map((c) => reservar(pedido(), c)));

    const ok = resultados.filter((r) => r.status === "fulfilled");
    const fallos = resultados.filter((r): r is PromiseRejectedResult => r.status === "rejected");
    assert.equal(ok.length, 1, `ganadoras: ${ok.length}`);
    assert.equal(fallos.length, clientes.length - 1);
    for (const f of fallos) {
      assert.ok(f.reason instanceof HttpsError, String(f.reason));
      assert.equal(f.reason.code, "already-exists");
      assert.deepEqual(f.reason.details, { motivo: "HORARIO_OCUPADO" });
    }
    assert.equal(await contarReservas(), 1);
  });

  it("repetido varias rondas (slots distintos) para descartar suerte de timing", async () => {
    const clientes = Array.from({ length: 6 }, (_, i) => `ronda${i}`);
    for (const c of clientes) await db.collection("usuarios").doc(c).set({ rol: "CLIENTE" });
    const slots = ["2026-10-05T12:00:00Z", "2026-10-05T14:00:00Z", "2026-10-05T16:00:00Z", "2026-10-05T18:00:00Z"];
    for (const [i, slot] of slots.entries()) {
      const rs = await Promise.allSettled(clientes.map((c) => reservar(pedido({ fechaHora: slot }), c)));
      assert.equal(rs.filter((r) => r.status === "fulfilled").length, 1, `ronda ${i}`);
    }
    assert.equal(await contarReservas(), slots.length);
  });

  it("solapes parciales en paralelo (10:00 y 10:30 y 10:45): solo una sobrevive", async () => {
    const clientes = ["pa", "pb", "pc"];
    for (const c of clientes) await db.collection("usuarios").doc(c).set({ rol: "CLIENTE" });
    const slots = [LUNES_10, "2026-10-05T13:30:00Z", "2026-10-05T13:45:00Z"];
    const rs = await Promise.allSettled(slots.map((s, i) => reservar(pedido({ fechaHora: s }), clientes[i])));
    assert.equal(rs.filter((r) => r.status === "fulfilled").length, 1);
    assert.equal(await contarReservas(), 1);
  });

  it("slots distintos y sin solape en paralelo: todos se crean", async () => {
    const clientes = ["qa", "qb", "qc", "qd"];
    for (const c of clientes) await db.collection("usuarios").doc(c).set({ rol: "CLIENTE" });
    const slots = ["2026-10-05T12:00:00Z", "2026-10-05T14:00:00Z", "2026-10-05T16:00:00Z", "2026-10-05T18:00:00Z"];
    const rs = await Promise.allSettled(slots.map((s, i) => reservar(pedido({ fechaHora: s }), clientes[i])));
    assert.equal(rs.filter((r) => r.status === "fulfilled").length, slots.length, JSON.stringify(rs));
    assert.equal(await contarReservas(), slots.length);
  });

  it("profesionales distintos en paralelo no se bloquean entre si", async () => {
    await db.collection("profesionales").doc("pro2").collection("servicios").doc(SRV_CONSULTA).set({
      modalidad: "CONSULTA",
      duracionMinutos: 60,
      precio: 20000,
      activo: true,
    });
    const rs = await Promise.allSettled([
      reservar(pedido(), CLIENTE),
      reservar(pedido({ profesionalId: "pro2" }), "cliente2"),
    ]);
    assert.equal(rs.filter((r) => r.status === "fulfilled").length, 2);
  });
});
