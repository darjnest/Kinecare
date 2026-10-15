// Integracion de la comision adeudada por atenciones en efectivo contra el Firestore Emulator.
// Ejecutar con: npm run test:emulator
import assert from "node:assert/strict";
import { createHmac, randomBytes } from "node:crypto";
import { before, beforeEach, describe, it } from "node:test";
import { initializeApp } from "firebase-admin/app";
import { Timestamp, getFirestore, type Firestore } from "firebase-admin/firestore";
import { pagarSaldoComisionHandler } from "../../src/cobrosComision.js";
import { saldoComisionPendiente } from "../../src/comisiones.js";
import { completarReservaHandler } from "../../src/completarReserva.js";
import { TOPE_SALDO_COMISION_CLP } from "../../src/constantes.js";
import { crearReservaHandler } from "../../src/crearReserva.js";
import { iniciarPagoHandler, type DepsPago } from "../../src/iniciarPago.js";
import { guardarCuenta } from "../../src/mercadopago/cuentas.js";
import { estadoDesdeMP } from "../../src/mercadopago/estados.js";
import { aplicarPago, webhookHandler } from "../../src/pagos.js";
import { responderReservaHandler } from "../../src/responderReserva.js";
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
const PASADA = new Date("2026-10-01T11:00:00Z");
const FUTURA = new Date("2026-10-05T13:00:00Z");
const CLIENTE = "cliente1";
const PRO = "pro1";
const OTRO_PRO = "pro2";
const SECRETO = "secreto-webhook";
const URL_BASE = "https://us-central1-demo-kinecare.cloudfunctions.net";
const CLAVE = randomBytes(32);
const TOKEN_PLATAFORMA = "access-plataforma";

let db: Firestore;
let pasarela: FakePasarela;
let deps: DepsPago;

async function limpiarEmulador() {
  const r = await fetch(`http://${emulador}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`, { method: "DELETE" });
  assert.equal(r.status, 200);
}

async function sembrarBase() {
  await db.collection("usuarios").doc(CLIENTE).set({ rol: "CLIENTE" });
  await db.collection("usuarios").doc(PRO).set({ rol: "PROFESIONAL" });
  await db.collection("usuarios").doc(OTRO_PRO).set({ rol: "PROFESIONAL" });
  await db.collection("profesionales").doc(PRO).set({ especialidades: [] });
  await db.collection("profesionales").doc(PRO).collection("servicios").doc("srv1").set({ nombre: "Kinesiología deportiva", precio: 25000 });
}

async function sembrarCuenta() {
  await guardarCuenta(db, CLAVE, PRO, tokens("access-vendedor", "refresh-vendedor"));
}

async function sembrarReserva(opciones: { estado?: string; fechaHora?: Date; pago?: Record<string, unknown>; profesionalId?: string } = {}) {
  const ref = await db.collection("reservas").add({
    clienteId: CLIENTE,
    profesionalId: opciones.profesionalId ?? PRO,
    servicioId: "srv1",
    estado: opciones.estado ?? "CONFIRMADA",
    fechaHora: Timestamp.fromDate(opciones.fechaHora ?? FUTURA),
    pago: opciones.pago ?? { id: null, monto: 25000, estado: "PENDIENTE" },
    comisionPorcentaje: 0.1,
  });
  return ref.id;
}

/** Comision adeudada ya registrada (como la deja completarReserva), sin pasar por el handler. */
async function sembrarComision(reservaId: string, monto: number, extra: Record<string, unknown> = {}, profesionalId = PRO) {
  await db.collection("comisionesPendientes").doc(reservaId).set({
    profesionalId,
    reservaId,
    monto,
    estado: "PENDIENTE",
    origen: "EFECTIVO",
    reservadaPor: null,
    reservadaEn: null,
    creadoEn: Timestamp.fromDate(new Date("2026-09-20T12:00:00Z")),
    ...extra,
  });
}

const completar = (reservaId: string) => completarReservaHandler(db, PRO, { reservaId }, AHORA);
const pagar = (reservaId: string) => iniciarPagoHandler(db, deps, CLIENTE, { reservaId }, AHORA);
const leer = async (coleccion: string, id: string) => (await db.collection(coleccion).doc(id).get()).data();

function firmada(dataId: string) {
  const v1 = createHmac("sha256", SECRETO).update(`id:${dataId};request-id:req-1;ts:1700000000;`).digest("hex");
  return { "x-signature": `ts=1700000000,v1=${v1}`, "x-request-id": "req-1" };
}

function notificarCobro(cobroId: string, dataId: string) {
  return webhookHandler(
    db,
    deps,
    SECRETO,
    {
      headers: firmada(dataId),
      query: { cobroId, "data.id": dataId, type: "payment" },
      body: { type: "payment", data: { id: dataId } },
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
  deps = { pasarela, clave: CLAVE, urlBase: URL_BASE, tokenPlataforma: TOKEN_PLATAFORMA };
});

describe("completarReserva: comision de atenciones en efectivo", () => {
  it("sin pago autorizado deja la comision como adeudada, con id = reservaId", async () => {
    const id = await sembrarReserva({ fechaHora: PASADA });
    await completar(id);

    const entrada = await leer("comisionesPendientes", id);
    assert.ok(entrada);
    assert.equal(entrada.profesionalId, PRO);
    assert.equal(entrada.monto, 2500);
    assert.equal(entrada.estado, "PENDIENTE");
    assert.equal(entrada.origen, "EFECTIVO");
    assert.equal(entrada.reservadaPor, null);
    assert.equal(await saldoComisionPendiente(db, PRO), 2500);
  });

  it("con pago AUTORIZADO no hay comision adeudada: ya viajo como marketplace_fee", async () => {
    const id = await sembrarReserva({ fechaHora: PASADA, pago: { id: "p1", monto: 25000, estado: "AUTORIZADO" } });
    await completar(id);
    assert.equal((await db.collection("comisionesPendientes").get()).size, 0);
  });

  it("con un pago PENDIENTE (Checkout abierto y sin terminar) igual se registra", async () => {
    const id = await sembrarReserva({ fechaHora: PASADA, pago: { id: "p1", monto: 25000, estado: "PENDIENTE" } });
    await completar(id);
    assert.equal((await leer("comisionesPendientes", id))?.monto, 2500);
  });

  it("un monto no cobrable no impide completar ni crea comision", async () => {
    const id = await sembrarReserva({ fechaHora: PASADA, pago: { id: null, monto: 0, estado: "PENDIENTE" } });
    await completar(id);
    assert.equal((await leer("reservas", id))?.estado, "COMPLETADA");
    assert.equal((await db.collection("comisionesPendientes").get()).size, 0);
  });
});

describe("iniciarPago: descuento del saldo adeudado", () => {
  it("suma la comision adeudada al marketplace_fee y reserva la entrada", async () => {
    await sembrarCuenta();
    await sembrarComision("antigua1", 2500);
    const reservaId = await sembrarReserva();
    const { pagoId } = await pagar(reservaId);

    const { datos } = pasarela.preferencias[0]!;
    assert.equal(datos.comision, 5000, "2.500 de esta cita + 2.500 adeudados");
    const pago = await leer("pagos", pagoId);
    assert.equal(pago?.comision, 2500, "pagos.comision sigue siendo solo la de esta cita");
    assert.equal(pago?.comisionSaldoDescontada, 2500);
    assert.deepEqual(pago?.entradasSaldo, ["antigua1"]);
    assert.equal((await leer("comisionesPendientes", "antigua1"))?.reservadaPor, pagoId);
  });

  it("nunca pasa de la mitad del monto: descuenta solo las entradas que caben, de la mas antigua", async () => {
    await sembrarCuenta();
    // Tope de fee = 12.500; base 2.500 -> margen 10.000.
    await sembrarComision("a", 6000, { creadoEn: Timestamp.fromDate(new Date("2026-09-01T00:00:00Z")) });
    await sembrarComision("b", 6000, { creadoEn: Timestamp.fromDate(new Date("2026-09-02T00:00:00Z")) });
    await sembrarComision("c", 3000, { creadoEn: Timestamp.fromDate(new Date("2026-09-03T00:00:00Z")) });
    const reservaId = await sembrarReserva();
    const { pagoId } = await pagar(reservaId);

    const pago = await leer("pagos", pagoId);
    assert.deepEqual(pago?.entradasSaldo, ["a", "c"], "b no cabe entera y no se parte");
    assert.equal(pasarela.preferencias[0]!.datos.comision, 2500 + 9000);
    assert.equal((await leer("comisionesPendientes", "b"))?.reservadaPor, null);
  });

  it("no toca las comisiones de otro profesional", async () => {
    await sembrarCuenta();
    await sembrarComision("ajena", 2500, {}, OTRO_PRO);
    const reservaId = await sembrarReserva();
    await pagar(reservaId);
    assert.equal(pasarela.preferencias[0]!.datos.comision, 2500);
  });

  it("una entrada reservada por otro pago vigente no se vuelve a descontar", async () => {
    await sembrarCuenta();
    await sembrarComision("a", 2500, { reservadaPor: "otro-pago", reservadaEn: Timestamp.fromDate(AHORA) });
    const reservaId = await sembrarReserva();
    await pagar(reservaId);
    assert.equal(pasarela.preferencias[0]!.datos.comision, 2500);
  });

  it("una reserva vencida (>24 h) sin resolver se libera y se vuelve a descontar", async () => {
    await sembrarCuenta();
    await sembrarComision("a", 2500, {
      reservadaPor: "otro-pago",
      reservadaEn: Timestamp.fromDate(new Date("2026-09-29T12:00:00Z")),
    });
    const reservaId = await sembrarReserva();
    const { pagoId } = await pagar(reservaId);
    assert.equal(pasarela.preferencias[0]!.datos.comision, 5000);
    assert.equal((await leer("comisionesPendientes", "a"))?.reservadaPor, pagoId);
  });

  it("al autorizarse el pago, las entradas descontadas pasan a PAGADA", async () => {
    await sembrarCuenta();
    await sembrarComision("a", 2500);
    const reservaId = await sembrarReserva();
    const { pagoId } = await pagar(reservaId);

    await aplicarPago(db, pagoId, pagoMP({ referenciaExterna: pagoId, status: "approved" }));

    const entrada = await leer("comisionesPendientes", "a");
    assert.equal(entrada?.estado, "PAGADA");
    assert.equal(entrada?.pagadaVia, "DESCUENTO");
    assert.equal(entrada?.pagadaPor, pagoId);
    assert.equal(await saldoComisionPendiente(db, PRO), 0);
  });

  it("si el pago se rechaza, las entradas se liberan y siguen adeudadas", async () => {
    await sembrarCuenta();
    await sembrarComision("a", 2500);
    const reservaId = await sembrarReserva();
    const { pagoId } = await pagar(reservaId);

    await aplicarPago(db, pagoId, pagoMP({ referenciaExterna: pagoId, status: "rejected" }));

    const entrada = await leer("comisionesPendientes", "a");
    assert.equal(entrada?.estado, "PENDIENTE");
    assert.equal(entrada?.reservadaPor, null);
  });

  it("un reembolso devuelve a PENDIENTE lo que ese pago habia saldado", async () => {
    await sembrarCuenta();
    await sembrarComision("a", 2500);
    const reservaId = await sembrarReserva();
    const { pagoId } = await pagar(reservaId);
    await aplicarPago(db, pagoId, pagoMP({ referenciaExterna: pagoId, status: "approved" }));
    await aplicarPago(db, pagoId, pagoMP({ referenciaExterna: pagoId, status: "refunded" }));

    const entrada = await leer("comisionesPendientes", "a");
    assert.equal(entrada?.estado, "PENDIENTE");
    assert.equal(entrada?.pagadaVia, undefined);
  });

  it("pago con tarjeta tardio sobre una atencion ya completada: la entrada de esa reserva se anula", async () => {
    await sembrarCuenta();
    const reservaId = await sembrarReserva();
    const { pagoId } = await pagar(reservaId);
    // La cita se completa antes de que el cliente termine el Checkout abierto.
    await db.collection("reservas").doc(reservaId).update({ fechaHora: Timestamp.fromDate(PASADA) });
    await completar(reservaId);
    assert.equal((await leer("comisionesPendientes", reservaId))?.estado, "PENDIENTE");

    await aplicarPago(db, pagoId, pagoMP({ referenciaExterna: pagoId, status: "approved" }));

    const entrada = await leer("comisionesPendientes", reservaId);
    assert.equal(entrada?.estado, "ANULADA");
    assert.equal(await saldoComisionPendiente(db, PRO), 0);
  });

  it("dos pagos seguidos no reservan la misma entrada", async () => {
    await sembrarCuenta();
    await sembrarComision("a", 2500);
    const r1 = await sembrarReserva();
    const r2 = await sembrarReserva();
    await pagar(r1);
    await pagar(r2);
    assert.equal(pasarela.preferencias[0]!.datos.comision, 5000);
    assert.equal(pasarela.preferencias[1]!.datos.comision, 2500);
  });
});

describe("pagarSaldoComision", () => {
  const pagarSaldoComo = (uid: string | undefined) => pagarSaldoComisionHandler(db, deps, uid, AHORA);
  const pagarSaldo = () => pagarSaldoComo(PRO);

  it("cobra con el token de la plataforma, sin marketplace_fee, por la suma de lo adeudado", async () => {
    await sembrarComision("a", 2500);
    await sembrarComision("b", 4000);
    const { cobroId, initPoint, monto } = await pagarSaldo();

    assert.equal(monto, 6500);
    assert.equal(initPoint, `https://mp.test/checkout?pref=${cobroId}`);
    const { accessToken, datos } = pasarela.preferencias[0]!;
    assert.equal(accessToken, TOKEN_PLATAFORMA);
    assert.equal(datos.monto, 6500);
    assert.equal(datos.comision, undefined);
    assert.equal(datos.referenciaExterna, cobroId);
    assert.equal(datos.notificationUrl, `${URL_BASE}/webhookMercadoPago?cobroId=${cobroId}`);
    assert.equal(datos.backUrl, `${URL_BASE}/retornoPago?tipo=comision`);
    const cobro = await leer("cobrosComision", cobroId);
    assert.deepEqual([...(cobro?.entradas as string[])].sort(), ["a", "b"]);
    assert.equal((await leer("comisionesPendientes", "a"))?.reservadaPor, cobroId);
  });

  it("un reintento reutiliza el cobro vigente y no crea otra preferencia", async () => {
    await sembrarComision("a", 2500);
    const primero = await pagarSaldo();
    const segundo = await pagarSaldo();
    assert.equal(segundo.cobroId, primero.cobroId);
    assert.equal(segundo.initPoint, primero.initPoint);
    assert.equal(pasarela.preferencias.length, 1);
    assert.equal((await db.collection("cobrosComision").get()).size, 1);
  });

  it("sin saldo pendiente responde SIN_SALDO_PENDIENTE", async () => {
    await esperarError(() => pagarSaldo(), "failed-precondition", "SIN_SALDO_PENDIENTE");
  });

  it("solo un profesional puede pagar, y con sesion", async () => {
    await esperarError(() => pagarSaldoComo(undefined), "unauthenticated", "SIN_SESION");
    await esperarError(() => pagarSaldoComo(CLIENTE), "permission-denied", "ROL_INVALIDO");
  });

  it("sin Access Token de la plataforma falla como pasarela no disponible", async () => {
    await sembrarComision("a", 2500);
    const sinToken: DepsPago = { pasarela, clave: CLAVE, urlBase: URL_BASE };
    await esperarError(() => pagarSaldoComisionHandler(db, sinToken, PRO, AHORA), "unavailable", "PASARELA_NO_DISPONIBLE");
  });

  it("no cobra lo que un pago con tarjeta en curso ya tiene reservado", async () => {
    await sembrarCuenta();
    await sembrarComision("a", 2500);
    await pagar(await sembrarReserva());
    await esperarError(() => pagarSaldo(), "failed-precondition", "SIN_SALDO_PENDIENTE");
  });

  describe("webhook del cobro", () => {
    async function cobroPendiente() {
      await sembrarComision("a", 2500);
      await sembrarComision("b", 4000);
      return pagarSaldo();
    }

    it("aprobado: las comisiones pasan a PAGADA por pago directo", async () => {
      const { cobroId, monto } = await cobroPendiente();
      pasarela.pagoPorId.set("9001", pagoMP({ referenciaExterna: cobroId, monto }));

      const r = await notificarCobro(cobroId, "9001");

      assert.equal(r.status, 200);
      assert.equal((await leer("cobrosComision", cobroId))?.estado, "AUTORIZADO");
      for (const id of ["a", "b"]) {
        const entrada = await leer("comisionesPendientes", id);
        assert.equal(entrada?.estado, "PAGADA");
        assert.equal(entrada?.pagadaVia, "PAGO_DIRECTO");
      }
      assert.equal(await saldoComisionPendiente(db, PRO), 0);
    });

    it("es idempotente: una notificacion repetida no cambia nada", async () => {
      const { cobroId, monto } = await cobroPendiente();
      pasarela.pagoPorId.set("9001", pagoMP({ referenciaExterna: cobroId, monto }));
      await notificarCobro(cobroId, "9001");
      const r = await notificarCobro(cobroId, "9001");
      assert.equal(r.status, 200);
      assert.equal((await leer("cobrosComision", cobroId))?.estado, "AUTORIZADO");
    });

    it("rechazado: las comisiones se liberan y siguen adeudadas", async () => {
      const { cobroId, monto } = await cobroPendiente();
      pasarela.pagoPorId.set("9001", pagoMP({ referenciaExterna: cobroId, monto, status: "rejected" }));
      await notificarCobro(cobroId, "9001");

      assert.equal((await leer("cobrosComision", cobroId))?.estado, "RECHAZADO");
      const entrada = await leer("comisionesPendientes", "a");
      assert.equal(entrada?.estado, "PENDIENTE");
      assert.equal(entrada?.reservadaPor, null);
    });

    it("monto distinto al cobrado: no se autoriza", async () => {
      const { cobroId } = await cobroPendiente();
      pasarela.pagoPorId.set("9001", pagoMP({ referenciaExterna: cobroId, monto: 100 }));
      await notificarCobro(cobroId, "9001");
      assert.equal((await leer("cobrosComision", cobroId))?.estado, "PENDIENTE");
      assert.equal((await leer("comisionesPendientes", "a"))?.estado, "PENDIENTE");
    });

    it("external_reference distinta: no se autoriza", async () => {
      const { cobroId, monto } = await cobroPendiente();
      pasarela.pagoPorId.set("9001", pagoMP({ referenciaExterna: "otra", monto }));
      await notificarCobro(cobroId, "9001");
      assert.equal((await leer("cobrosComision", cobroId))?.estado, "PENDIENTE");
    });

    it("firma invalida: 401 sin tocar nada", async () => {
      const { cobroId } = await cobroPendiente();
      const r = await webhookHandler(
        db,
        deps,
        SECRETO,
        { headers: firmada("9001"), query: { cobroId, "data.id": "9002", type: "payment" }, body: {} },
        AHORA,
      );
      assert.equal(r.status, 401);
    });

    it("sin Access Token de la plataforma responde 500 para que Mercado Pago reintente", async () => {
      const { cobroId } = await cobroPendiente();
      const r = await webhookHandler(
        db,
        { pasarela, clave: CLAVE, urlBase: URL_BASE },
        SECRETO,
        {
          headers: firmada("9001"),
          query: { cobroId, "data.id": "9001", type: "payment" },
          body: { type: "payment", data: { id: "9001" } },
        },
        AHORA,
      );
      assert.equal(r.status, 500);
    });

    it("un cobro desconocido se acusa con 200", async () => {
      const r = await notificarCobro("noexiste", "9001");
      assert.equal(r.status, 200);
    });
  });
});

describe("bloqueo por saldo de comision", () => {
  async function conSaldo(monto: number) {
    await sembrarComision("deuda", monto);
  }

  async function solicitada() {
    return sembrarReserva({ estado: "SOLICITADA" });
  }

  it("al llegar al tope no puede aceptar solicitudes nuevas", async () => {
    await conSaldo(TOPE_SALDO_COMISION_CLP);
    const id = await solicitada();
    await esperarError(
      () => responderReservaHandler(db, PRO, { reservaId: id, respuesta: "ACEPTAR" }, AHORA),
      "failed-precondition",
      "SALDO_COMISION_PENDIENTE",
    );
    assert.equal((await leer("reservas", id))?.estado, "SOLICITADA");
  });

  it("bajo el tope si puede aceptar", async () => {
    await conSaldo(TOPE_SALDO_COMISION_CLP - 1);
    const id = await solicitada();
    const r = await responderReservaHandler(db, PRO, { reservaId: id, respuesta: "ACEPTAR" }, AHORA);
    assert.equal(r.estado, "CONFIRMADA");
  });

  it("aun bloqueado puede rechazar", async () => {
    await conSaldo(TOPE_SALDO_COMISION_CLP);
    const id = await solicitada();
    const r = await responderReservaHandler(db, PRO, { reservaId: id, respuesta: "RECHAZAR" }, AHORA);
    assert.equal(r.estado, "RECHAZADA");
  });

  it("pagar el saldo lo desbloquea", async () => {
    await conSaldo(TOPE_SALDO_COMISION_CLP);
    const { cobroId, monto } = await pagarSaldoComisionHandler(db, deps, PRO, AHORA);
    pasarela.pagoPorId.set("9001", pagoMP({ referenciaExterna: cobroId, monto }));
    await notificarCobro(cobroId, "9001");

    const id = await solicitada();
    const r = await responderReservaHandler(db, PRO, { reservaId: id, respuesta: "ACEPTAR" }, AHORA);
    assert.equal(r.estado, "CONFIRMADA");
  });

  it("las reservas ya confirmadas se pueden completar aunque este bloqueado", async () => {
    await conSaldo(TOPE_SALDO_COMISION_CLP);
    const id = await sembrarReserva({ fechaHora: PASADA });
    await completar(id);
    assert.equal((await leer("reservas", id))?.estado, "COMPLETADA");
  });

  it("un cliente no puede pedirle una cita a un profesional bloqueado", async () => {
    await conSaldo(TOPE_SALDO_COMISION_CLP);
    await esperarError(
      () =>
        crearReservaHandler(
          db,
          CLIENTE,
          { profesionalId: PRO, servicioId: "srv1", fechaHora: FUTURA.toISOString() },
          AHORA,
        ),
      "failed-precondition",
      "PROFESIONAL_NO_DISPONIBLE",
    );
  });

  it("el saldo de otro profesional no bloquea", async () => {
    await sembrarComision("ajena", TOPE_SALDO_COMISION_CLP * 2, {}, OTRO_PRO);
    const id = await solicitada();
    const r = await responderReservaHandler(db, PRO, { reservaId: id, respuesta: "ACEPTAR" }, AHORA);
    assert.equal(r.estado, "CONFIRMADA");
  });
});

describe("estadoDesdeMP (referencia usada por los cobros de comision)", () => {
  it("approved es AUTORIZADO", () => {
    assert.equal(estadoDesdeMP("approved"), "AUTORIZADO");
  });
});
