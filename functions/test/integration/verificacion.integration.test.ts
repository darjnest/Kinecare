// Integracion de los handlers de verificacion de identidad contra el Firestore Emulator, con un
// proveedor falso. Ejecutar con: npm run test:emulator
import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import { before, beforeEach, describe, it } from "node:test";
import { initializeApp } from "firebase-admin/app";
import { Timestamp, getFirestore, type Firestore } from "firebase-admin/firestore";
import { estadoVerificacionHandler } from "../../src/verificacion/estadoVerificacion.js";
import { ProveedorVerificacionError, type ProveedorVerificacion } from "../../src/verificacion/proveedor.js";
import { solicitarVerificacionHandler, type DepsVerificacion } from "../../src/verificacion/solicitarVerificacion.js";
import { retornoVerificacionHandler, webhookDiditHandler } from "../../src/verificacion/webhookDidit.js";
import { FakeProveedorVerificacion } from "../fakeProveedorVerificacion.js";
import { esperarError } from "../helpers.js";

const PROJECT_ID = "demo-kinecare";
const emulador = process.env.FIRESTORE_EMULATOR_HOST;
if (emulador === undefined) {
  throw new Error(
    "FIRESTORE_EMULATOR_HOST no esta definido: corre `npm run test:emulator` (o `npm run test:unit` para omitir estos tests).",
  );
}

const AHORA = new Date("2026-10-01T12:00:00Z");
const TS = String(Math.floor(AHORA.getTime() / 1000));
const PRO = "pro1";
const OTRO_PRO = "pro2";
const CLIENTE = "cliente1";
const SECRETO = "secreto-webhook-didit";
const WORKFLOW = "11111111-2222-3333-4444-555555555555";
const URL_BASE = "https://us-central1-demo-kinecare.cloudfunctions.net";
const RUT = "123456785";
const CREDENCIAL = { tipo: "CREDENCIALES", estado: "APROBADO", detalle: "RNPI vigente", fechaActualizacion: Timestamp.fromDate(new Date("2026-01-01T00:00:00Z")) };

let db: Firestore;
let proveedor: FakeProveedorVerificacion;
let deps: DepsVerificacion;

async function limpiarEmulador() {
  const r = await fetch(`http://${emulador}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, { method: "DELETE" });
  assert.equal(r.status, 200);
}

async function sembrarBase(opciones: { insignias?: unknown[]; rut?: string | null } = {}) {
  await db.collection("usuarios").doc(PRO).set({ rol: "PROFESIONAL", ...(opciones.rut === null ? {} : { rut: opciones.rut ?? RUT }) });
  await db.collection("usuarios").doc(OTRO_PRO).set({ rol: "PROFESIONAL", rut: "987654321" });
  await db.collection("usuarios").doc(CLIENTE).set({ rol: "CLIENTE" });
  await db
    .collection("profesionales")
    .doc(PRO)
    .set({ especialidades: [], estadoVerificacionGeneral: "NO_SOLICITADO", insignias: opciones.insignias ?? [CREDENCIAL] });
  await db.collection("profesionales").doc(OTRO_PRO).set({ especialidades: [], estadoVerificacionGeneral: "NO_SOLICITADO", insignias: [] });
}

// `null` = llamada sin sesion (un `undefined` activaria el valor por defecto del parametro).
const solicitar = (uid: string | null = PRO, data: unknown = { tipo: "IDENTIDAD" }) =>
  solicitarVerificacionHandler(db, deps, uid ?? undefined, data, AHORA);
const consultar = (data: unknown = {}, uid: string | null = PRO, d: { proveedor: ProveedorVerificacion } = deps) =>
  estadoVerificacionHandler(db, d, uid ?? undefined, data, AHORA);
const leerSolicitud = async (id: string) => (await db.collection("solicitudesVerificacion").doc(id).get()).data()!;
const leerPerfil = async (uid = PRO) => (await db.collection("profesionales").doc(uid).get()).data()!;
const identidad = async (uid = PRO) => ((await leerPerfil(uid)).insignias as Array<Record<string, unknown>>).find((i) => i.tipo === "IDENTIDAD");

/** Siembra una solicitud ya creada (como la deja solicitarVerificacion). */
async function sembrarSolicitud(id: string, extra: Record<string, unknown> = {}, profesionalId = PRO) {
  await db.collection("solicitudesVerificacion").doc(id).set({
    profesionalId,
    tipo: "IDENTIDAD",
    estado: "PENDIENTE",
    proveedorExterno: "Didit",
    estadoProveedor: "Not Started",
    fechaSolicitud: Timestamp.fromDate(new Date("2026-10-01T10:00:00Z")),
    ...extra,
  });
}

function evento(sessionId: string, opciones: { eventoId?: string; tipo?: string; extra?: Record<string, unknown> } = {}) {
  return JSON.stringify({
    event_id: opciones.eventoId ?? "evt-1",
    webhook_type: opciones.tipo ?? "status.updated",
    timestamp: Number(TS),
    session_id: sessionId,
    status: "Approved",
    vendor_data: PRO,
    decision: { id_verifications: [{ personal_number: "NO-SE-CONFIA-EN-ESTO" }] },
    ...opciones.extra,
  });
}

function firmado(raw: string, opciones: { ts?: string; secreto?: string } = {}) {
  return {
    "x-signature": createHmac("sha256", opciones.secreto ?? SECRETO).update(raw).digest("hex"),
    "x-timestamp": opciones.ts ?? TS,
  };
}

const notificar = (raw: string, headers: Record<string, string> = firmado(raw), d: { proveedor: ProveedorVerificacion } = deps) =>
  webhookDiditHandler(db, d, SECRETO, { headers, rawBody: Buffer.from(raw) }, AHORA);

/** Registra la decision del proveedor y entrega el webhook firmado. */
async function entregar(sessionId: string, estado: string, rut: string | null = "12.345.678-5", eventoId = "evt-1") {
  proveedor.decisiones.set(sessionId, { estado, rut });
  return notificar(evento(sessionId, { eventoId }));
}

before(() => {
  initializeApp({ projectId: PROJECT_ID });
  db = getFirestore();
});

beforeEach(async () => {
  await limpiarEmulador();
  await sembrarBase();
  proveedor = new FakeProveedorVerificacion();
  deps = { proveedor, workflowId: WORKFLOW, urlBase: URL_BASE };
});

describe("solicitarVerificacion: rechazos", () => {
  it("sin sesion -> SIN_SESION", async () => {
    await esperarError(() => solicitar(null), "unauthenticated", "SIN_SESION");
    await esperarError(() => solicitar(""), "unauthenticated", "SIN_SESION");
  });

  it("payload invalido -> DATOS_INVALIDOS; tipo distinto de IDENTIDAD -> TIPO_NO_SOPORTADO", async () => {
    await esperarError(() => solicitar(PRO, null), "invalid-argument", "DATOS_INVALIDOS");
    await esperarError(() => solicitar(PRO, {}), "invalid-argument", "DATOS_INVALIDOS");
    await esperarError(() => solicitar(PRO, { tipo: 5 }), "invalid-argument", "DATOS_INVALIDOS");
    await esperarError(() => solicitar(PRO, { tipo: "CREDENCIALES" }), "invalid-argument", "TIPO_NO_SOPORTADO");
    await esperarError(() => solicitar(PRO, { tipo: "identidad" }), "invalid-argument", "TIPO_NO_SOPORTADO");
    assert.equal(proveedor.sesionesCreadas.length, 0);
  });

  it("un cliente o un usuario sin documento -> NO_ES_PROFESIONAL", async () => {
    await esperarError(() => solicitar(CLIENTE), "permission-denied", "NO_ES_PROFESIONAL");
    await esperarError(() => solicitar("fantasma"), "permission-denied", "NO_ES_PROFESIONAL");
    assert.equal(proveedor.sesionesCreadas.length, 0);
  });

  it("profesional sin documento de perfil -> PERFIL_NO_ENCONTRADO", async () => {
    await db.collection("usuarios").doc("sinperfil").set({ rol: "PROFESIONAL" });
    await esperarError(() => solicitar("sinperfil"), "not-found", "PERFIL_NO_ENCONTRADO");
  });

  it("identidad ya APROBADA -> YA_VERIFICADO, sin gastar una sesion del proveedor", async () => {
    await db.collection("profesionales").doc(PRO).update({ insignias: [{ tipo: "IDENTIDAD", estado: "APROBADO" }] });
    await esperarError(() => solicitar(), "failed-precondition", "YA_VERIFICADO");
    assert.equal(proveedor.sesionesCreadas.length, 0);
  });

  it("sin DIDIT_WORKFLOW_ID -> PROVEEDOR_NO_CONFIGURADO", async () => {
    deps = { ...deps, workflowId: "  " };
    await esperarError(() => solicitar(), "failed-precondition", "PROVEEDOR_NO_CONFIGURADO");
    assert.equal(proveedor.sesionesCreadas.length, 0);
  });

  it("proveedor caido (cualquier causa) -> PROVEEDOR_NO_DISPONIBLE y no se escribe nada", async () => {
    for (const causa of ["SOLICITUD_RECHAZADA", "NO_AUTORIZADO", "LIMITE", "SERVIDOR", "TIMEOUT"] as const) {
      proveedor.fallaCrear = new ProveedorVerificacionError(causa, 500);
      await esperarError(() => solicitar(), "unavailable", "PROVEEDOR_NO_DISPONIBLE");
    }
    proveedor.fallaCrear = new Error("explotó algo inesperado");
    await esperarError(() => solicitar(), "unavailable", "PROVEEDOR_NO_DISPONIBLE");
    assert.equal((await db.collection("solicitudesVerificacion").get()).size, 0);
    assert.equal(await identidad(), undefined);
  });
});

describe("solicitarVerificacion: exito", () => {
  it("crea la sesion con uid, workflow y callback del servidor; guarda solicitud PENDIENTE e insignia PENDIENTE", async () => {
    const r = await solicitar();
    assert.deepEqual(r, { solicitudId: "sesion-1", url: "https://verify.test/session/sesion-1", estado: "PENDIENTE" });
    assert.deepEqual(proveedor.sesionesCreadas, [
      { workflowId: WORKFLOW, vendorData: PRO, callbackUrl: `${URL_BASE}/retornoVerificacion` },
    ]);

    const s = await leerSolicitud("sesion-1");
    assert.equal(s.profesionalId, PRO);
    assert.equal(s.tipo, "IDENTIDAD");
    assert.equal(s.estado, "PENDIENTE");
    assert.equal(s.proveedorExterno, "Didit");
    assert.equal(s.estadoProveedor, "Not Started");
    assert.deepEqual((s.fechaSolicitud as Timestamp).toDate(), AHORA);
    // Nada mas que lo documentado: sin datos personales ni urls de media.
    assert.deepEqual(Object.keys(s).sort(), ["estado", "estadoProveedor", "fechaSolicitud", "profesionalId", "proveedorExterno", "tipo"]);

    const perfil = await leerPerfil();
    assert.equal(perfil.estadoVerificacionGeneral, "PENDIENTE");
    const insignias = perfil.insignias as Array<Record<string, unknown>>;
    assert.equal(insignias.length, 2);
    assert.deepEqual(insignias[0], CREDENCIAL); // las demas se conservan intactas
    assert.equal(insignias[1].tipo, "IDENTIDAD");
    assert.equal(insignias[1].estado, "PENDIENTE");
    assert.equal(insignias[1].detalle, "Verificación en curso");
    assert.deepEqual((insignias[1].fechaActualizacion as Timestamp).toDate(), AHORA);
  });

  it("es idempotente: volver a llamar con la sesion abierta devuelve la misma y no duplica", async () => {
    const a = await solicitar();
    const b = await solicitar();
    assert.deepEqual(b, a);
    assert.equal((await db.collection("solicitudesVerificacion").get()).size, 1);
    assert.equal(((await leerPerfil()).insignias as unknown[]).length, 2);
  });

  it("tras un intento rechazado se puede reintentar: nueva sesion, nueva solicitud, insignia vuelve a PENDIENTE", async () => {
    const a = await solicitar();
    await entregar(a.solicitudId, "Declined");
    assert.equal((await identidad())!.estado, "RECHAZADO");

    proveedor.cerrarSesion(PRO);
    const b = await solicitar();
    assert.notEqual(b.solicitudId, a.solicitudId);
    assert.equal((await identidad())!.estado, "PENDIENTE");
    assert.equal((await leerSolicitud(a.solicitudId)).estado, "RECHAZADO");
    assert.equal((await db.collection("solicitudesVerificacion").get()).size, 2);
  });

  it("un perfil sin campo insignias tambien funciona", async () => {
    await db.collection("profesionales").doc(PRO).set({ especialidades: [] });
    await solicitar();
    assert.equal((await identidad())!.estado, "PENDIENTE");
  });
});

describe("estadoVerificacion", () => {
  it("sin sesion -> SIN_SESION; cuerpo que no es objeto -> DATOS_INVALIDOS; id con barra -> DATOS_INVALIDOS", async () => {
    await esperarError(() => consultar({}, null), "unauthenticated", "SIN_SESION");
    await esperarError(() => consultar("hola"), "invalid-argument", "DATOS_INVALIDOS");
    await esperarError(() => consultar({ solicitudId: "a/b" }), "invalid-argument", "DATOS_INVALIDOS");
    await esperarError(() => consultar({ solicitudId: 7 }), "invalid-argument", "DATOS_INVALIDOS");
  });

  it("sin solicitudes -> NO_SOLICITADO (con data vacia, null o ausente)", async () => {
    assert.deepEqual(await consultar({}), { estado: "NO_SOLICITADO" });
    assert.deepEqual(await consultar(null), { estado: "NO_SOLICITADO" });
    assert.deepEqual(await estadoVerificacionHandler(db, deps, PRO, undefined, AHORA), { estado: "NO_SOLICITADO" });
  });

  it("una solicitud ajena responde igual que una inexistente", async () => {
    await sembrarSolicitud("ajena", {}, OTRO_PRO);
    await esperarError(() => consultar({ solicitudId: "ajena" }), "not-found", "SOLICITUD_NO_ENCONTRADA");
    await esperarError(() => consultar({ solicitudId: "no-existe" }), "not-found", "SOLICITUD_NO_ENCONTRADA");
    assert.equal(proveedor.consultas.length, 0);
  });

  it("sin id toma la solicitud IDENTIDAD mas reciente de quien llama (ignora ajenas y otros tipos)", async () => {
    await sembrarSolicitud("vieja", { estado: "RECHAZADO", motivo: "DECLINED", fechaSolicitud: Timestamp.fromDate(new Date("2026-09-01T00:00:00Z")) });
    await sembrarSolicitud("nueva", { estado: "APROBADO", fechaSolicitud: Timestamp.fromDate(new Date("2026-09-15T00:00:00Z")) });
    await sembrarSolicitud("de-otro-tipo", { tipo: "CREDENCIALES", estado: "RECHAZADO", fechaSolicitud: Timestamp.fromDate(new Date("2026-09-30T00:00:00Z")) });
    await sembrarSolicitud("ajena", { estado: "RECHAZADO", fechaSolicitud: Timestamp.fromDate(new Date("2026-09-30T00:00:00Z")) }, OTRO_PRO);
    assert.deepEqual(await consultar({}), { estado: "APROBADO", solicitudId: "nueva" });
  });

  it("devuelve estado y motivo grueso de una solicitud resuelta, sin releer al proveedor", async () => {
    await sembrarSolicitud("s1", { estado: "RECHAZADO", motivo: "RUT_NO_COINCIDE", rutCoincide: false });
    assert.deepEqual(await consultar({ solicitudId: "s1" }), { estado: "RECHAZADO", solicitudId: "s1", motivo: "RUT_NO_COINCIDE" });
    assert.equal(proveedor.consultas.length, 0);
  });

  it("una solicitud PENDIENTE relee a Didit y se autocura si el webhook se perdio", async () => {
    const { solicitudId } = await solicitar();
    proveedor.decisiones.set(solicitudId, { estado: "Approved", rut: "12.345.678-5" });
    assert.deepEqual(await consultar({ solicitudId }), { estado: "APROBADO", solicitudId });
    assert.equal((await leerSolicitud(solicitudId)).estado, "APROBADO");
    assert.equal((await leerSolicitud(solicitudId)).rutCoincide, true);
    assert.equal((await identidad())!.estado, "APROBADO");
    assert.equal((await leerPerfil()).estadoVerificacionGeneral, "APROBADO");
  });

  it("autocuracion sin id explicito y con rechazo por RUT: devuelve el motivo grueso", async () => {
    const { solicitudId } = await solicitar();
    proveedor.decisiones.set(solicitudId, { estado: "Approved", rut: "98.765.432-1" });
    assert.deepEqual(await consultar({}), { estado: "RECHAZADO", solicitudId, motivo: "RUT_NO_COINCIDE" });
  });

  it("si Didit sigue en curso, queda PENDIENTE y se actualiza estadoProveedor", async () => {
    const { solicitudId } = await solicitar();
    proveedor.decisiones.set(solicitudId, { estado: "In Review", rut: null });
    assert.deepEqual(await consultar({}), { estado: "PENDIENTE", solicitudId });
    assert.equal((await leerSolicitud(solicitudId)).estadoProveedor, "In Review");
  });

  it("si la relectura falla devuelve el estado guardado, sin error", async () => {
    const { solicitudId } = await solicitar();
    for (const falla of [new ProveedorVerificacionError("TIMEOUT"), new Error("otra cosa")]) {
      proveedor.fallaDecision = falla;
      assert.deepEqual(await consultar({ solicitudId }), { estado: "PENDIENTE", solicitudId });
    }
    assert.equal((await leerSolicitud(solicitudId)).estado, "PENDIENTE");
  });

  it("si Firestore falla al aplicar tambien devuelve el estado guardado", async () => {
    const { solicitudId } = await solicitar();
    proveedor.decisiones.set(solicitudId, { estado: "Approved", rut: null });
    const dbRoto = {
      collection: (nombre: string) => {
        if (nombre === "solicitudesVerificacion") return db.collection(nombre);
        throw new Error("firestore caído");
      },
      runTransaction: () => Promise.reject(new Error("firestore caído")),
    } as unknown as Firestore;
    const r = await estadoVerificacionHandler(dbRoto, deps, PRO, { solicitudId }, AHORA);
    assert.deepEqual(r, { estado: "PENDIENTE", solicitudId });
  });
});

describe("webhookDidit: autenticidad", () => {
  it("firma invalida -> 401 y no se toca nada", async () => {
    await sembrarSolicitud("s1");
    proveedor.decisiones.set("s1", { estado: "Approved", rut: null });
    const raw = evento("s1");
    const r = await notificar(raw, firmado(raw, { secreto: "otro-secreto" }));
    assert.equal(r.status, 401);
    assert.equal((await leerSolicitud("s1")).estado, "PENDIENTE");
    assert.equal(proveedor.consultas.length, 0);
  });

  it("sin cabeceras de firma o sin cuerpo crudo -> 401", async () => {
    const raw = evento("s1");
    assert.equal((await notificar(raw, {})).status, 401);
    const r = await webhookDiditHandler(db, deps, SECRETO, { headers: firmado(raw), rawBody: undefined }, AHORA);
    assert.equal(r.status, 401);
  });

  it("secreto vacio -> 401 aunque la firma se calcule con secreto vacio", async () => {
    const raw = evento("s1");
    const r = await webhookDiditHandler(db, deps, "", { headers: firmado(raw, { secreto: "" }), rawBody: raw }, AHORA);
    assert.equal(r.status, 401);
  });

  it("timestamp viejo (replay) -> 401 aunque la firma sea correcta", async () => {
    await sembrarSolicitud("s1");
    const raw = evento("s1");
    const viejo = String(Number(TS) - 600);
    const r = await notificar(raw, firmado(raw, { ts: viejo }));
    assert.equal(r.status, 401);
    assert.equal(proveedor.consultas.length, 0);
  });

  it("acepta X-Signature-V2 (JSON canonico) cuando X-Signature no coincide", async () => {
    await sembrarSolicitud("s1");
    proveedor.decisiones.set("s1", { estado: "Declined", rut: null });
    // Cuerpo con espacios y orden de claves distintos al canonico.
    const raw = `{ "webhook_type": "status.updated", "session_id": "s1", "event_id": "e9" }`;
    const canonico = `{"event_id":"e9","session_id":"s1","webhook_type":"status.updated"}`;
    const headers = {
      "x-signature": "00".repeat(32),
      "x-signature-v2": createHmac("sha256", SECRETO).update(canonico).digest("hex"),
      "x-timestamp": TS,
    };
    assert.equal((await notificar(raw, headers)).status, 200);
    assert.equal((await leerSolicitud("s1")).estado, "RECHAZADO");
  });

  it("cuerpo firmado pero no JSON -> 400; session_id invalido -> 400", async () => {
    const malo = "esto no es json";
    assert.equal((await notificar(malo)).status, 400);
    assert.equal((await notificar(evento("../otra/ruta"))).status, 400);
    assert.equal((await notificar(JSON.stringify({ webhook_type: "status.updated" }))).status, 400);
  });
});

describe("webhookDidit: procesamiento", () => {
  it("un tipo de evento distinto de status.updated se acusa sin hacer nada", async () => {
    await sembrarSolicitud("s1");
    const r = await notificar(evento("s1", { tipo: "data.updated" }));
    assert.equal(r.status, 200);
    assert.equal(proveedor.consultas.length, 0);
    assert.equal((await leerSolicitud("s1")).estado, "PENDIENTE");
  });

  it("sesion desconocida -> 200 sin consultar al proveedor (no se reintenta)", async () => {
    proveedor.decisiones.set("fantasma", { estado: "Approved", rut: null });
    const r = await notificar(evento("fantasma"));
    assert.equal(r.status, 200);
    assert.equal(proveedor.consultas.length, 0);
    assert.equal((await db.collection("solicitudesVerificacion").get()).size, 0);
  });

  it("Approved con RUT coincidente: APROBADO, insignia APROBADA publica y neutra, las demas insignias se conservan", async () => {
    const { solicitudId } = await solicitar();
    const r = await entregar(solicitudId, "Approved", "12.345.678-5", "evt-A");
    assert.equal(r.status, 200);

    const s = await leerSolicitud(solicitudId);
    assert.equal(s.estado, "APROBADO");
    assert.equal(s.estadoProveedor, "Approved");
    assert.equal(s.rutCoincide, true);
    assert.equal(s.ultimoEventoId, "evt-A");
    assert.deepEqual((s.fechaResolucion as Timestamp).toDate(), AHORA);
    assert.ok(!("motivo" in s));

    const perfil = await leerPerfil();
    assert.equal(perfil.estadoVerificacionGeneral, "APROBADO");
    const insignias = perfil.insignias as Array<Record<string, unknown>>;
    assert.equal(insignias.length, 2);
    assert.deepEqual(insignias[0], CREDENCIAL);
    assert.equal(insignias[1].estado, "APROBADO");
    assert.equal(insignias[1].detalle, "Identidad verificada con documento y prueba de vida");
  });

  it("NUNCA persiste el RUN ni nada del payload: ni en la solicitud ni en el perfil", async () => {
    const { solicitudId } = await solicitar();
    await entregar(solicitudId, "Approved", "12.345.678-5");
    const todo = JSON.stringify([await leerSolicitud(solicitudId), await leerPerfil()]);
    for (const secreto of ["12.345.678-5", "123456785", "NO-SE-CONFIA-EN-ESTO"]) assert.ok(!todo.includes(secreto), secreto);
  });

  it("no confia en el payload: el estado sale de la relectura al proveedor", async () => {
    const { solicitudId } = await solicitar();
    // El payload dice Approved (ver `evento`), pero Didit responde Declined.
    const r = await entregar(solicitudId, "Declined");
    assert.equal(r.status, 200);
    const s = await leerSolicitud(solicitudId);
    assert.equal(s.estado, "RECHAZADO");
    assert.equal(s.motivo, "DECLINED");
    assert.equal(s.estadoProveedor, "Declined");
    const i = (await identidad())!;
    assert.equal(i.estado, "RECHAZADO");
    assert.equal(i.detalle, "No se pudo verificar la identidad");
    assert.equal((await leerPerfil()).estadoVerificacionGeneral, "RECHAZADO");
  });

  it("Approved con RUT distinto: RECHAZADO/RUT_NO_COINCIDE, insignia RECHAZADA con texto neutro (el motivo no es publico)", async () => {
    const { solicitudId } = await solicitar();
    await entregar(solicitudId, "Approved", "98.765.432-1");
    const s = await leerSolicitud(solicitudId);
    assert.equal(s.estado, "RECHAZADO");
    assert.equal(s.motivo, "RUT_NO_COINCIDE");
    assert.equal(s.rutCoincide, false);
    const i = (await identidad())!;
    assert.equal(i.estado, "RECHAZADO");
    assert.equal(i.detalle, "No se pudo verificar la identidad");
    assert.ok(!JSON.stringify(await leerPerfil()).includes("RUT"));
  });

  it("el formato del RUT no importa al comparar (ceros, puntos, guion, K)", async () => {
    await db.collection("usuarios").doc(PRO).update({ rut: "7654321K" });
    const { solicitudId } = await solicitar();
    await entregar(solicitudId, "Approved", "07.654.321-k");
    assert.equal((await leerSolicitud(solicitudId)).rutCoincide, true);
  });

  it("RUT no comparable (proveedor sin RUN con forma valida) -> APROBADO con rutCoincide null", async () => {
    for (const [i, rut] of [null, "A12345", "P-9999999999"].entries()) {
      await limpiarEmulador();
      await sembrarBase();
      proveedor = new FakeProveedorVerificacion();
      deps = { ...deps, proveedor };
      const { solicitudId } = await solicitar();
      await entregar(solicitudId, "Approved", rut, `evt-${i}`);
      const s = await leerSolicitud(solicitudId);
      assert.equal(s.estado, "APROBADO", String(rut));
      assert.equal(s.rutCoincide, null, String(rut));
    }
  });

  it("RUT no comparable (usuario sin rut) -> APROBADO con rutCoincide null", async () => {
    await limpiarEmulador();
    await sembrarBase({ rut: null });
    const { solicitudId } = await solicitar();
    await entregar(solicitudId, "Approved", "12.345.678-5");
    const s = await leerSolicitud(solicitudId);
    assert.equal(s.estado, "APROBADO");
    assert.equal(s.rutCoincide, null);
  });

  it("estados en curso mantienen PENDIENTE y registran estadoProveedor", async () => {
    const { solicitudId } = await solicitar();
    for (const e of ["In Progress", "Awaiting User", "Resubmitted", "In Review"]) {
      assert.equal((await entregar(solicitudId, e, null, `evt-${e}`)).status, 200);
      const s = await leerSolicitud(solicitudId);
      assert.equal(s.estado, "PENDIENTE");
      assert.equal(s.estadoProveedor, e);
      assert.equal((await identidad())!.estado, "PENDIENTE");
    }
  });

  it("Expired y Abandoned: solicitud RECHAZADA con motivo, pero la insignia vuelve a NO_SOLICITADO (sin detalle)", async () => {
    for (const [estado, motivo] of [["Expired", "EXPIRADA"], ["Abandoned", "ABANDONADA"]] as const) {
      await limpiarEmulador();
      await sembrarBase();
      proveedor = new FakeProveedorVerificacion();
      deps = { ...deps, proveedor };
      const { solicitudId } = await solicitar();
      assert.equal((await identidad())!.estado, "PENDIENTE");
      await entregar(solicitudId, estado, null);
      const s = await leerSolicitud(solicitudId);
      assert.equal(s.estado, "RECHAZADO", estado);
      assert.equal(s.motivo, motivo);
      const i = (await identidad())!;
      assert.equal(i.estado, "NO_SOLICITADO", estado);
      assert.ok(!("detalle" in i));
      assert.equal((await leerPerfil()).estadoVerificacionGeneral, "NO_SOLICITADO");
      assert.equal(((await leerPerfil()).insignias as unknown[]).length, 2);
      assert.deepEqual(await consultar({}), { estado: "RECHAZADO", solicitudId, motivo });
    }
  });

  it("Kyc Expired sobre un aprobado: es la unica regresion permitida; la insignia vuelve a NO_SOLICITADO", async () => {
    const { solicitudId } = await solicitar();
    await entregar(solicitudId, "Approved", "12.345.678-5", "evt-1");
    assert.equal((await identidad())!.estado, "APROBADO");
    await entregar(solicitudId, "Kyc Expired", null, "evt-2");
    const s = await leerSolicitud(solicitudId);
    assert.equal(s.estado, "RECHAZADO");
    assert.equal(s.motivo, "KYC_VENCIDO");
    assert.equal((await identidad())!.estado, "NO_SOLICITADO");
    // Y quien perdio la verificacion puede volver a pedirla.
    proveedor.cerrarSesion(PRO);
    assert.equal((await solicitar()).estado, "PENDIENTE");
  });

  it("evento repetido (mismo event_id): no vuelve a consultar al proveedor ni cambia nada", async () => {
    const { solicitudId } = await solicitar();
    await entregar(solicitudId, "Approved", "12.345.678-5", "evt-X");
    assert.equal(proveedor.consultas.length, 1);
    const antes = await leerSolicitud(solicitudId);
    assert.equal((await notificar(evento(solicitudId, { eventoId: "evt-X" }))).status, 200);
    assert.equal(proveedor.consultas.length, 1);
    assert.deepEqual(await leerSolicitud(solicitudId), antes);
  });

  it("evento desordenado/tardio: un APROBADO no vuelve a PENDIENTE ni a otro estado", async () => {
    const { solicitudId } = await solicitar();
    await entregar(solicitudId, "Approved", "12.345.678-5", "evt-2");
    for (const [i, e] of ["In Review", "In Progress", "Declined", "Expired", "Abandoned", "Approved"].entries()) {
      assert.equal((await entregar(solicitudId, e, "98.765.432-1", `tarde-${i}`)).status, 200);
      const s = await leerSolicitud(solicitudId);
      assert.equal(s.estado, "APROBADO", e);
      assert.equal(s.estadoProveedor, "Approved", e);
      assert.equal((await identidad())!.estado, "APROBADO", e);
    }
  });

  it("evento desordenado/tardio: un RECHAZADO no se revierte (ni siquiera por Approved ni Kyc Expired)", async () => {
    const { solicitudId } = await solicitar();
    await entregar(solicitudId, "Declined", null, "evt-2");
    for (const [i, e] of ["In Review", "Approved", "Kyc Expired", "Not Started"].entries()) {
      assert.equal((await entregar(solicitudId, e, "12.345.678-5", `tarde-${i}`)).status, 200);
      const s = await leerSolicitud(solicitudId);
      assert.equal(s.estado, "RECHAZADO", e);
      assert.equal(s.motivo, "DECLINED", e);
      assert.equal((await identidad())!.estado, "RECHAZADO", e);
    }
  });

  it("estado de sesion desconocido: 200 y no cambia nada", async () => {
    const { solicitudId } = await solicitar();
    assert.equal((await entregar(solicitudId, "Estado Nuevo De Didit", null)).status, 200);
    const s = await leerSolicitud(solicitudId);
    assert.equal(s.estado, "PENDIENTE");
    assert.equal(s.estadoProveedor, "Not Started");
  });

  it("con otro intento aun PENDIENTE, un Declined/Expired no pisa la insignia 'en curso'", async () => {
    await sembrarSolicitud("en-revision", { estadoProveedor: "In Review" });
    await sembrarSolicitud("segundo", { estadoProveedor: "In Progress" });
    await db.collection("profesionales").doc(PRO).update({ insignias: [{ tipo: "IDENTIDAD", estado: "PENDIENTE" }] });
    await entregar("segundo", "Expired", null);
    assert.equal((await leerSolicitud("segundo")).estado, "RECHAZADO");
    assert.equal((await identidad())!.estado, "PENDIENTE");
    // Y cuando el que seguia en revision se aprueba, la insignia queda APROBADA.
    await entregar("en-revision", "Approved", "12.345.678-5", "evt-2");
    assert.equal((await identidad())!.estado, "APROBADO");
  });

  it("una sesion vieja que falla no le quita la insignia APROBADA a quien ya se verifico", async () => {
    await sembrarSolicitud("aprobada", { estado: "APROBADO" });
    await sembrarSolicitud("vieja");
    await db.collection("profesionales").doc(PRO).update({ insignias: [{ tipo: "IDENTIDAD", estado: "APROBADO", detalle: "x" }] });
    await entregar("vieja", "Declined", null);
    assert.equal((await leerSolicitud("vieja")).estado, "RECHAZADO");
    assert.equal((await identidad())!.estado, "APROBADO");
  });

  it("solo afecta al profesional dueno de la sesion", async () => {
    const { solicitudId } = await solicitar();
    await entregar(solicitudId, "Approved", "12.345.678-5");
    assert.deepEqual((await leerPerfil(OTRO_PRO)).insignias, []);
    assert.equal((await leerPerfil(OTRO_PRO)).estadoVerificacionGeneral, "NO_SOLICITADO");
  });

  it("falla transitoria del proveedor -> 500 (Didit reintenta) y el reintento posterior si aplica", async () => {
    const { solicitudId } = await solicitar();
    proveedor.decisiones.set(solicitudId, { estado: "Approved", rut: "12.345.678-5" });
    for (const falla of [new ProveedorVerificacionError("SERVIDOR", 503), new ProveedorVerificacionError("TIMEOUT"), new Error("x")]) {
      proveedor.fallaDecision = falla;
      assert.equal((await notificar(evento(solicitudId))).status, 500);
      assert.equal((await leerSolicitud(solicitudId)).estado, "PENDIENTE");
    }
    proveedor.fallaDecision = null;
    assert.equal((await notificar(evento(solicitudId))).status, 200);
    assert.equal((await leerSolicitud(solicitudId)).estado, "APROBADO");
  });

  it("falla de Firestore -> 500", async () => {
    const dbRoto = {
      collection: () => {
        throw new Error("firestore caído");
      },
    } as unknown as Firestore;
    const raw = evento("s1");
    const r = await webhookDiditHandler(dbRoto, deps, SECRETO, { headers: firmado(raw), rawBody: raw }, AHORA);
    assert.equal(r.status, 500);
  });
});

describe("retornoVerificacion", () => {
  it("con verificationSessionId valido redirige al deep link con solicitudId", () => {
    assert.deepEqual(retornoVerificacionHandler({ verificationSessionId: "abc-123_X", status: "Approved" }), {
      status: 302,
      redirect: "kinecare://verificacion/resultado?solicitudId=abc-123_X",
    });
  });

  it("sin id, o con id malicioso o de otro tipo, redirige sin parametros", () => {
    const sinId = { status: 302, redirect: "kinecare://verificacion/resultado" };
    assert.deepEqual(retornoVerificacionHandler({}), sinId);
    assert.deepEqual(retornoVerificacionHandler({ status: "Approved" }), sinId);
    for (const malo of ["../x", "a&b=c", "a?b", "x#y", "a b", "https://evil.test", "a".repeat(129), "", ["a", "b"], 5]) {
      assert.deepEqual(retornoVerificacionHandler({ verificationSessionId: malo }), sinId, String(malo));
    }
  });

  it("nunca refleja el status de la query", () => {
    const r = retornoVerificacionHandler({ verificationSessionId: "s1", status: "Approved" });
    assert.ok(!r.redirect!.includes("Approved"));
  });
});
