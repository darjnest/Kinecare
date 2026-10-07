// Integracion de los handlers de pago contra el Firestore Emulator, con una pasarela falsa.
// Ejecutar con: npm run test:emulator
import assert from "node:assert/strict";
import { createHmac, randomBytes } from "node:crypto";
import { before, beforeEach, describe, it } from "node:test";
import { initializeApp } from "firebase-admin/app";
import { Timestamp, getFirestore, type Firestore } from "firebase-admin/firestore";
import { conectarMercadoPagoHandler, oauthCallbackHandler } from "../../src/conectarMercadoPago.js";
import { iniciarPagoHandler, type DepsPago } from "../../src/iniciarPago.js";
import { descifrar } from "../../src/mercadopago/cifrado.js";
import { guardarCuenta } from "../../src/mercadopago/cuentas.js";
import { TokenRechazadoError } from "../../src/mercadopago/pasarela.js";
import { estadoPagoHandler, webhookHandler } from "../../src/pagos.js";
import { FakePasarela, pagoMP, tokens } from "../fakePasarela.js";
import { esperarError } from "../helpers.js";

const PROJECT_ID = "demo-kinecare";
const emulador = process.env.FIRESTORE_EMULATOR_HOST;
if (emulador === undefined) {
  throw new Error(
    "FIRESTORE_EMULATOR_HOST no esta definido: corre `npm run test:emulator` (o `npm run test:unit` para omitir estos tests).",
  );
}

const AHORA = new Date("2026-10-01T12:00:00Z");
const CLIENTE = "cliente1";
const OTRO_CLIENTE = "cliente2";
const PRO = "pro1";
const SECRETO = "secreto-webhook";
const URL_BASE = "https://us-central1-demo-kinecare.cloudfunctions.net";
const CLAVE = randomBytes(32);

let db: Firestore;
let pasarela: FakePasarela;
let deps: DepsPago;

async function limpiarEmulador() {
  const r = await fetch(`http://${emulador}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, { method: "DELETE" });
  assert.equal(r.status, 200);
}

async function sembrarBase() {
  await db.collection("usuarios").doc(CLIENTE).set({ rol: "CLIENTE" });
  await db.collection("usuarios").doc(OTRO_CLIENTE).set({ rol: "CLIENTE" });
  await db.collection("usuarios").doc(PRO).set({ rol: "PROFESIONAL" });
  await db.collection("profesionales").doc(PRO).set({ especialidades: [] });
  await db.collection("profesionales").doc(PRO).collection("servicios").doc("srv1").set({ nombre: "Kinesiología deportiva", precio: 25000 });
}

/** Cuenta de Mercado Pago ya conectada del profesional, con tokens cifrados como los deja guardarCuenta. */
async function sembrarCuenta(opciones: { expiraEn?: Date; requiereReautorizacion?: boolean } = {}) {
  const { cifrar } = await import("../../src/mercadopago/cifrado.js");
  await db.collection("cuentasMercadoPago").doc(PRO).set({
    userId: "777",
    scope: "offline_access",
    accessTokenCifrado: cifrar("access-vendedor", CLAVE, PRO),
    refreshTokenCifrado: cifrar("refresh-vendedor", CLAVE, PRO),
    expiraEn: Timestamp.fromDate(opciones.expiraEn ?? new Date("2027-01-01T00:00:00Z")),
    version: 1,
    requiereReautorizacion: opciones.requiereReautorizacion ?? false,
  });
}

async function sembrarReserva(opciones: { estado?: string; clienteId?: string; pago?: Record<string, unknown> } = {}) {
  const ref = await db.collection("reservas").add({
    clienteId: opciones.clienteId ?? CLIENTE,
    profesionalId: PRO,
    servicioId: "srv1",
    estado: opciones.estado ?? "CONFIRMADA",
    fechaHora: Timestamp.fromDate(new Date("2026-10-05T13:00:00Z")),
    pago: opciones.pago ?? { id: null, monto: 25000, estado: "PENDIENTE" },
    comisionPorcentaje: 0.1,
  });
  return ref.id;
}

const pagar = (reservaId: string, uid: string | undefined = CLIENTE) => iniciarPagoHandler(db, deps, uid, { reservaId }, AHORA);
const leerReserva = async (id: string) => (await db.collection("reservas").doc(id).get()).data()!;
const leerPago = async (id: string) => (await db.collection("pagos").doc(id).get()).data()!;

function firmada(dataId: string, ts = "1700000000", secreto = SECRETO) {
  const requestId = "req-1";
  const v1 = createHmac("sha256", secreto).update(`id:${dataId};request-id:${requestId};ts:${ts};`).digest("hex");
  return { "x-signature": `ts=${ts},v1=${v1}`, "x-request-id": requestId };
}

function notificar(pagoId: string, dataId: string, opciones: { headers?: Record<string, string>; type?: string } = {}) {
  return webhookHandler(
    db,
    deps,
    SECRETO,
    {
      headers: opciones.headers ?? firmada(dataId),
      query: { pagoId, "data.id": dataId, type: opciones.type ?? "payment" },
      body: { type: opciones.type ?? "payment", data: { id: dataId } },
    },
    AHORA,
  );
}

before(() => {
  initializeApp({ projectId: PROJECT_ID });
  db = getFirestore();
});

beforeEach(async () => {
  await limpiarEmulador();
  await sembrarBase();
  pasarela = new FakePasarela();
  deps = { pasarela, clave: CLAVE, urlBase: URL_BASE };
});

describe("iniciarPago: camino feliz", () => {
  it("crea el pago, la preferencia con el token del vendedor y la comision calculada en el servidor", async () => {
    await sembrarCuenta();
    const reservaId = await sembrarReserva();
    const { pagoId, initPoint } = await pagar(reservaId);

    assert.equal(initPoint, `https://mp.test/checkout?pref=${pagoId}`);
    assert.equal(pasarela.preferencias.length, 1);
    const { accessToken, datos } = pasarela.preferencias[0]!;
    assert.equal(accessToken, "access-vendedor", "se cobra con el token del profesional, no el de la plataforma");
    assert.equal(datos.monto, 25000);
    assert.equal(datos.comision, 2500);
    assert.equal(datos.titulo, "Kinesiología deportiva");
    assert.equal(datos.referenciaExterna, pagoId);
    assert.equal(datos.claveIdempotencia, pagoId);
    assert.equal(datos.notificationUrl, `${URL_BASE}/webhookMercadoPago?pagoId=${pagoId}`);
    assert.equal(datos.backUrl, `${URL_BASE}/retornoPago`);

    const pago = await leerPago(pagoId);
    assert.equal(pago.estado, "PENDIENTE");
    assert.equal(pago.reservaId, reservaId);
    assert.equal(pago.clienteId, CLIENTE);
    assert.equal(pago.profesionalId, PRO);
    assert.equal(pago.monto, 25000);
    assert.equal(pago.comision, 2500);
    assert.equal(pago.initPoint, initPoint);
    assert.equal(pago.preferenceId, `pref-${pagoId}`);
    assert.deepEqual((await leerReserva(reservaId)).pago, { id: pagoId, monto: 25000, estado: "PENDIENTE" });
  });

  it("un reintento con el pago PENDIENTE reutiliza el documento y no crea otra preferencia", async () => {
    await sembrarCuenta();
    const reservaId = await sembrarReserva();
    const primero = await pagar(reservaId);
    const segundo = await pagar(reservaId);
    assert.deepEqual(segundo, primero);
    assert.equal(pasarela.preferencias.length, 1);
    assert.equal((await db.collection("pagos").get()).size, 1);
  });

  it("tras un intento RECHAZADO se crea un pago nuevo y la reserva apunta a el", async () => {
    await sembrarCuenta();
    const reservaId = await sembrarReserva();
    const { pagoId: primero } = await pagar(reservaId);
    await db.collection("pagos").doc(primero).update({ estado: "RECHAZADO" });
    await db.collection("reservas").doc(reservaId).update({ "pago.estado": "RECHAZADO" });

    const { pagoId: segundo } = await pagar(reservaId);
    assert.notEqual(segundo, primero);
    assert.equal((await leerReserva(reservaId)).pago.id, segundo);
  });

  it("renueva el token del vendedor si vence pronto y guarda los nuevos cifrados", async () => {
    await sembrarCuenta({ expiraEn: new Date(AHORA.getTime() + 60_000) });
    const reservaId = await sembrarReserva();
    await pagar(reservaId);

    assert.deepEqual(pasarela.refrescos, ["refresh-vendedor"]);
    assert.equal(pasarela.preferencias[0]!.accessToken, "access-renovado");
    const cuenta = (await db.collection("cuentasMercadoPago").doc(PRO).get()).data()!;
    assert.equal(cuenta.version, 2);
    assert.equal(descifrar(cuenta.accessTokenCifrado, CLAVE, PRO), "access-renovado");
    assert.equal(descifrar(cuenta.refreshTokenCifrado, CLAVE, PRO), "refresh-renovado");
  });
});

describe("iniciarPago: errores", () => {
  it("SIN_SESION y ROL_INVALIDO", async () => {
    await sembrarCuenta();
    const reservaId = await sembrarReserva();
    // Directo al handler: `pagar(id, undefined)` tomaria el uid por defecto.
    await esperarError(() => iniciarPagoHandler(db, deps, undefined, { reservaId }, AHORA), "unauthenticated", "SIN_SESION");
    await esperarError(() => pagar(reservaId, ""), "unauthenticated", "SIN_SESION");
    await esperarError(() => pagar(reservaId, PRO), "permission-denied", "ROL_INVALIDO");
    await esperarError(() => pagar(reservaId, "fantasma"), "permission-denied", "ROL_INVALIDO");
  });

  it("DATOS_INVALIDOS con payload mal formado", async () => {
    await esperarError(() => iniciarPagoHandler(db, deps, CLIENTE, { reservaId: "" }, AHORA), "invalid-argument", "DATOS_INVALIDOS");
  });

  it("una reserva ajena o inexistente responde igual: RESERVA_NO_ENCONTRADA", async () => {
    await sembrarCuenta();
    const reservaId = await sembrarReserva();
    await esperarError(() => pagar(reservaId, OTRO_CLIENTE), "not-found", "RESERVA_NO_ENCONTRADA");
    await esperarError(() => pagar("no-existe"), "not-found", "RESERVA_NO_ENCONTRADA");
  });

  it("solo se paga una reserva CONFIRMADA", async () => {
    await sembrarCuenta();
    for (const estado of ["SOLICITADA", "RECHAZADA", "CANCELADA_CLIENTE", "COMPLETADA"]) {
      const reservaId = await sembrarReserva({ estado });
      await esperarError(() => pagar(reservaId), "failed-precondition", "RESERVA_NO_PAGABLE");
    }
  });

  it("RESERVA_NO_PAGABLE si la hora de la cita ya paso (no se cobra algo que nadie atendio)", async () => {
    await sembrarCuenta();
    const reservaId = await sembrarReserva();
    await db.collection("reservas").doc(reservaId).update({ fechaHora: Timestamp.fromDate(new Date("2026-09-30T13:00:00Z")) });
    await esperarError(() => pagar(reservaId), "failed-precondition", "RESERVA_NO_PAGABLE");
    assert.equal((await db.collection("pagos").get()).size, 0);
    assert.equal(pasarela.preferencias.length, 0);
  });

  it("PAGO_YA_REALIZADO si ya esta autorizado o reembolsado", async () => {
    await sembrarCuenta();
    for (const estado of ["AUTORIZADO", "REEMBOLSADO"]) {
      const reservaId = await sembrarReserva({ pago: { id: "p1", monto: 25000, estado } });
      await esperarError(() => pagar(reservaId), "failed-precondition", "PAGO_YA_REALIZADO");
    }
  });

  it("PROFESIONAL_SIN_CUENTA_MP sin cuenta conectada o con reautorizacion pendiente, sin cobrar a la plataforma", async () => {
    const reservaId = await sembrarReserva();
    await esperarError(() => pagar(reservaId), "failed-precondition", "PROFESIONAL_SIN_CUENTA_MP");
    await sembrarCuenta({ requiereReautorizacion: true });
    await esperarError(() => pagar(reservaId), "failed-precondition", "PROFESIONAL_SIN_CUENTA_MP");
    assert.equal(pasarela.preferencias.length, 0);
  });

  it("si la renovacion del token falla, marca la cuenta para reautorizar", async () => {
    await sembrarCuenta({ expiraEn: new Date(AHORA.getTime() - 1000) });
    pasarela.tokensAlRefrescar = new TokenRechazadoError("invalid_grant");
    const reservaId = await sembrarReserva();
    await esperarError(() => pagar(reservaId), "failed-precondition", "PROFESIONAL_SIN_CUENTA_MP");
    assert.equal((await db.collection("cuentasMercadoPago").doc(PRO).get()).get("requiereReautorizacion"), true);
    assert.equal((await db.collection("profesionales").doc(PRO).get()).get("mercadoPagoConectado"), false);
  });

  it("un fallo TRANSITORIO al renovar el token (timeout/5xx) NO desconecta al profesional", async () => {
    await sembrarCuenta({ expiraEn: new Date(AHORA.getTime() - 1000) });
    pasarela.tokensAlRefrescar = new Error("/oauth/token respondio 503");
    const reservaId = await sembrarReserva();
    await esperarError(() => pagar(reservaId), "unavailable", "PASARELA_NO_DISPONIBLE");
    const cuenta = (await db.collection("cuentasMercadoPago").doc(PRO).get()).data()!;
    assert.equal(cuenta.requiereReautorizacion, false);
    assert.equal(cuenta.version, 1);
    assert.equal(pasarela.preferencias.length, 0);
  });

  it("si el refresh se rechaza porque OTRA instancia ya roto el token, se usa el token nuevo sin desconectar", async () => {
    await sembrarCuenta({ expiraEn: new Date(AHORA.getTime() - 1000) });
    const { cifrar } = await import("../../src/mercadopago/cifrado.js");
    // Simula la carrera: mientras esta instancia refresca, la otra ya guardo su resultado (version 2).
    pasarela.tokensAlRefrescar = new TokenRechazadoError("invalid_grant");
    const refrescar = pasarela.refrescar.bind(pasarela);
    pasarela.refrescar = async (refresh: string) => {
      await db.collection("cuentasMercadoPago").doc(PRO).update({
        accessTokenCifrado: cifrar("access-de-la-otra-instancia", CLAVE, PRO),
        expiraEn: Timestamp.fromDate(new Date("2027-01-01T00:00:00Z")),
        version: 2,
      });
      return refrescar(refresh);
    };
    const reservaId = await sembrarReserva();
    await pagar(reservaId);

    assert.equal(pasarela.preferencias[0]!.accessToken, "access-de-la-otra-instancia");
    assert.equal((await db.collection("cuentasMercadoPago").doc(PRO).get()).get("requiereReautorizacion"), false);
  });

  it("MONTO_INVALIDO si el monto de la reserva no es un entero positivo", async () => {
    await sembrarCuenta();
    const reservaId = await sembrarReserva({ pago: { id: null, monto: 0, estado: "PENDIENTE" } });
    await esperarError(() => pagar(reservaId), "failed-precondition", "MONTO_INVALIDO");
    assert.equal((await db.collection("pagos").get()).size, 0);
  });
});

describe("webhookMercadoPago", () => {
  async function pagoPendiente() {
    await sembrarCuenta();
    const reservaId = await sembrarReserva();
    const { pagoId } = await pagar(reservaId);
    return { reservaId, pagoId };
  }

  it("rechaza con 401 una firma invalida, sin tocar nada ni consultar a Mercado Pago", async () => {
    const { pagoId } = await pagoPendiente();
    pasarela.pagoPorId.set("9001", pagoMP({ referenciaExterna: pagoId }));
    const r = await notificar(pagoId, "9001", { headers: firmada("9001", "1", "otro-secreto") });
    assert.equal(r.status, 401);
    assert.equal((await leerPago(pagoId)).estado, "PENDIENTE");
  });

  it("autoriza el pago y la reserva cuando Mercado Pago confirma el cobro", async () => {
    const { reservaId, pagoId } = await pagoPendiente();
    pasarela.pagoPorId.set("9001", pagoMP({ referenciaExterna: pagoId }));
    assert.equal((await notificar(pagoId, "9001")).status, 200);

    const pago = await leerPago(pagoId);
    assert.equal(pago.estado, "AUTORIZADO");
    assert.equal(pago.idTransaccionPasarela, "9001");
    assert.deepEqual(pago.metodo, { tipo: "TARJETA", ultimosDigitos: "4321" });
    assert.equal((await leerReserva(reservaId)).pago.estado, "AUTORIZADO");
  });

  it("es idempotente: la misma notificacion repetida no cambia nada", async () => {
    const { pagoId } = await pagoPendiente();
    pasarela.pagoPorId.set("9001", pagoMP({ referenciaExterna: pagoId }));
    await notificar(pagoId, "9001");
    const antes = await leerPago(pagoId);
    assert.equal((await notificar(pagoId, "9001")).status, 200);
    const despues = await leerPago(pagoId);
    assert.equal(despues.estado, "AUTORIZADO");
    assert.equal(despues.actualizadoEn.toMillis(), antes.actualizadoEn.toMillis());
  });

  it("una notificacion tardia de un intento rechazado no revierte un pago ya autorizado", async () => {
    const { pagoId } = await pagoPendiente();
    pasarela.pagoPorId.set("9001", pagoMP({ referenciaExterna: pagoId }));
    pasarela.pagoPorId.set("9000", pagoMP({ id: "9000", status: "rejected", referenciaExterna: pagoId }));
    await notificar(pagoId, "9001");
    await notificar(pagoId, "9000");
    const pago = await leerPago(pagoId);
    assert.equal(pago.estado, "AUTORIZADO");
    assert.equal(pago.idTransaccionPasarela, "9001");
  });

  it("un rechazo seguido de un reintento aprobado termina AUTORIZADO", async () => {
    const { pagoId } = await pagoPendiente();
    pasarela.pagoPorId.set("9000", pagoMP({ id: "9000", status: "rejected", referenciaExterna: pagoId }));
    pasarela.pagoPorId.set("9001", pagoMP({ referenciaExterna: pagoId }));
    await notificar(pagoId, "9000");
    assert.equal((await leerPago(pagoId)).estado, "RECHAZADO");
    await notificar(pagoId, "9001");
    assert.equal((await leerPago(pagoId)).estado, "AUTORIZADO");
  });

  it("registra el reembolso", async () => {
    const { reservaId, pagoId } = await pagoPendiente();
    pasarela.pagoPorId.set("9001", pagoMP({ referenciaExterna: pagoId }));
    await notificar(pagoId, "9001");
    pasarela.pagoPorId.set("9001", pagoMP({ status: "refunded", referenciaExterna: pagoId }));
    await notificar(pagoId, "9001");
    assert.equal((await leerPago(pagoId)).estado, "REEMBOLSADO");
    assert.equal((await leerReserva(reservaId)).pago.estado, "REEMBOLSADO");
  });

  it("NO autoriza si el monto aprobado difiere del cobrado", async () => {
    const { pagoId } = await pagoPendiente();
    pasarela.pagoPorId.set("9001", pagoMP({ referenciaExterna: pagoId, monto: 100 }));
    assert.equal((await notificar(pagoId, "9001")).status, 200);
    assert.equal((await leerPago(pagoId)).estado, "PENDIENTE");
  });

  it("NO autoriza si external_reference no corresponde a este pago", async () => {
    const { pagoId } = await pagoPendiente();
    pasarela.pagoPorId.set("9001", pagoMP({ referenciaExterna: "otro-pago" }));
    await notificar(pagoId, "9001");
    assert.equal((await leerPago(pagoId)).estado, "PENDIENTE");
  });

  it("un intento viejo no pisa el estado de la reserva cuando ya apunta a otro pago", async () => {
    const { reservaId, pagoId } = await pagoPendiente();
    await db.collection("reservas").doc(reservaId).update({ "pago.id": "pago-nuevo", "pago.estado": "PENDIENTE" });
    pasarela.pagoPorId.set("9001", pagoMP({ referenciaExterna: pagoId }));
    await notificar(pagoId, "9001");
    assert.equal((await leerPago(pagoId)).estado, "AUTORIZADO");
    assert.deepEqual((await leerReserva(reservaId)).pago, { id: "pago-nuevo", monto: 25000, estado: "PENDIENTE" });
  });

  it("acusa recibo (200) de temas que no son pagos y de pagos desconocidos, sin consultar a Mercado Pago", async () => {
    assert.equal((await notificar("x", "55", { type: "merchant_order" })).status, 200);
    assert.equal((await notificar("no-existe", "55")).status, 200);
  });

  it("responde 500 ante una falla transitoria para que Mercado Pago reintente", async () => {
    const { pagoId } = await pagoPendiente();
    pasarela.falla = true;
    assert.equal((await notificar(pagoId, "9001")).status, 500);
    assert.equal((await leerPago(pagoId)).estado, "PENDIENTE");
  });
});

describe("estadoPago", () => {
  it("relee Mercado Pago cuando el pago sigue pendiente y lo actualiza", async () => {
    await sembrarCuenta();
    const reservaId = await sembrarReserva();
    const { pagoId } = await pagar(reservaId);
    pasarela.pagoPorReferencia.set(pagoId, pagoMP({ referenciaExterna: pagoId }));

    assert.deepEqual(await estadoPagoHandler(db, deps, CLIENTE, { pagoId }, AHORA), { estado: "AUTORIZADO" });
    assert.equal((await leerReserva(reservaId)).pago.estado, "AUTORIZADO");
  });

  it("sigue PENDIENTE si Mercado Pago aun no registra el pago", async () => {
    await sembrarCuenta();
    const { pagoId } = await pagar(await sembrarReserva());
    assert.deepEqual(await estadoPagoHandler(db, deps, CLIENTE, { pagoId }, AHORA), { estado: "PENDIENTE" });
  });

  it("lo pueden consultar el cliente y el profesional, nadie mas", async () => {
    await sembrarCuenta();
    const { pagoId } = await pagar(await sembrarReserva());
    assert.deepEqual(await estadoPagoHandler(db, deps, PRO, { pagoId }, AHORA), { estado: "PENDIENTE" });
    await esperarError(() => estadoPagoHandler(db, deps, OTRO_CLIENTE, { pagoId }, AHORA), "not-found", "PAGO_NO_ENCONTRADO");
    await esperarError(() => estadoPagoHandler(db, deps, undefined, { pagoId }, AHORA), "unauthenticated", "SIN_SESION");
    await esperarError(() => estadoPagoHandler(db, deps, CLIENTE, { pagoId: "no-existe" }, AHORA), "not-found", "PAGO_NO_ENCONTRADO");
  });
});

describe("conexion OAuth del profesional", () => {
  const conexion = () => ({ pasarela, clave: CLAVE });

  async function pedirState(): Promise<string> {
    const { authorizationUrl } = await conectarMercadoPagoHandler(db, conexion(), PRO, AHORA);
    return new URL(authorizationUrl).searchParams.get("state")!;
  }

  it("solo un profesional con sesion puede pedir la URL", async () => {
    await esperarError(() => conectarMercadoPagoHandler(db, conexion(), undefined, AHORA), "unauthenticated", "SIN_SESION");
    await esperarError(() => conectarMercadoPagoHandler(db, conexion(), CLIENTE, AHORA), "permission-denied", "ROL_INVALIDO");
  });

  it("el state es aleatorio, queda atado al profesional y vence a los 10 minutos", async () => {
    const a = await pedirState();
    assert.match(a, /^[0-9a-f]{48}$/);
    const doc = (await db.collection("oauthEstados").doc(a).get()).data()!;
    assert.equal(doc.profesionalId, PRO);
    assert.equal(doc.expiraEn.toMillis() - doc.creadoEn.toMillis(), 10 * 60_000);
    assert.notEqual(a, await pedirState());
  });

  it("pedir otra URL reemplaza el state anterior del profesional (no se acumulan)", async () => {
    const primero = await pedirState();
    const segundo = await pedirState();
    const tercero = await pedirState();
    assert.notEqual(segundo, tercero);
    const vigentes = await db.collection("oauthEstados").where("profesionalId", "==", PRO).get();
    assert.deepEqual(vigentes.docs.map((d) => d.id), [tercero]);
    assert.equal((await db.collection("oauthEstados").doc(primero).get()).exists, false);
  });

  it("si el perfil profesional no existe NO se crea uno fantasma ni se guarda la cuenta", async () => {
    await db.collection("profesionales").doc(PRO).delete();
    await assert.rejects(guardarCuenta(db, CLAVE, PRO, tokens("a", "r")));
    assert.equal((await db.collection("profesionales").doc(PRO).get()).exists, false);
    assert.equal((await db.collection("cuentasMercadoPago").doc(PRO).get()).exists, false);

    const state = await pedirState();
    const r = await oauthCallbackHandler(db, conexion(), { code: "c", state }, AHORA);
    assert.equal(r.redirect, "kinecare://mp/error?motivo=pasarela");
    assert.equal((await db.collection("profesionales").doc(PRO).get()).exists, false);
  });

  it("el callback canjea el codigo, guarda los tokens CIFRADOS y marca al profesional como conectado", async () => {
    const state = await pedirState();
    pasarela.tokensAlCanjear = tokens("access-nuevo", "refresh-nuevo");
    const r = await oauthCallbackHandler(db, conexion(), { code: "codigo-1", state }, AHORA);

    assert.deepEqual(r, { status: 302, redirect: "kinecare://mp/conectado" });
    assert.deepEqual(pasarela.codigosCanjeados, ["codigo-1"]);
    const cuenta = (await db.collection("cuentasMercadoPago").doc(PRO).get()).data()!;
    assert.ok(!JSON.stringify(cuenta).includes("access-nuevo"), "el token no se guarda en claro");
    assert.equal(descifrar(cuenta.accessTokenCifrado, CLAVE, PRO), "access-nuevo");
    assert.equal(descifrar(cuenta.refreshTokenCifrado, CLAVE, PRO), "refresh-nuevo");
    assert.equal(cuenta.userId, "777");
    assert.equal((await db.collection("profesionales").doc(PRO).get()).get("mercadoPagoConectado"), true);
  });

  it("el state se consume una sola vez", async () => {
    const state = await pedirState();
    await oauthCallbackHandler(db, conexion(), { code: "c", state }, AHORA);
    const repetido = await oauthCallbackHandler(db, conexion(), { code: "c", state }, AHORA);
    assert.equal(repetido.redirect, "kinecare://mp/error?motivo=estado_invalido");
    assert.equal(pasarela.codigosCanjeados.length, 1);
  });

  it("rechaza states ausentes, mal formados, desconocidos o vencidos", async () => {
    const malo = "kinecare://mp/error?motivo=estado_invalido";
    assert.equal((await oauthCallbackHandler(db, conexion(), { code: "c" }, AHORA)).redirect, malo);
    assert.equal((await oauthCallbackHandler(db, conexion(), { code: "c", state: "../x" }, AHORA)).redirect, malo);
    assert.equal((await oauthCallbackHandler(db, conexion(), { code: "c", state: "a".repeat(48) }, AHORA)).redirect, malo);

    const state = await pedirState();
    const mas = new Date(AHORA.getTime() + 11 * 60_000);
    assert.equal((await oauthCallbackHandler(db, conexion(), { code: "c", state }, mas)).redirect, malo);
    assert.equal(pasarela.codigosCanjeados.length, 0);
  });

  it("si el profesional cancela, no se canjea nada y el state igual queda consumido", async () => {
    const state = await pedirState();
    const r = await oauthCallbackHandler(db, conexion(), { state, error: "access_denied" }, AHORA);
    assert.equal(r.redirect, "kinecare://mp/error?motivo=cancelado");
    assert.equal(pasarela.codigosCanjeados.length, 0);
    assert.equal((await db.collection("oauthEstados").doc(state).get()).exists, false);
  });

  it("sin code responde error; si Mercado Pago falla no guarda cuenta", async () => {
    const s1 = await pedirState();
    assert.equal((await oauthCallbackHandler(db, conexion(), { state: s1 }, AHORA)).redirect, "kinecare://mp/error?motivo=sin_codigo");
    const s2 = await pedirState();
    pasarela.falla = true;
    assert.equal((await oauthCallbackHandler(db, conexion(), { code: "c", state: s2 }, AHORA)).redirect, "kinecare://mp/error?motivo=pasarela");
    assert.equal((await db.collection("cuentasMercadoPago").doc(PRO).get()).exists, false);
  });
});
