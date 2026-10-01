import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { fin, hayReservaSolapada, intervalosSeSolapan, ventanaDeConsulta } from "../../src/solapamiento.js";

const t = (s: string) => new Date(`2026-10-05T${s}:00Z`);

describe("intervalosSeSolapan", () => {
  it("detecta solape parcial, contencion e igualdad", () => {
    assert.equal(intervalosSeSolapan(t("10:00"), t("11:00"), t("10:30"), t("11:30")), true);
    assert.equal(intervalosSeSolapan(t("10:30"), t("11:30"), t("10:00"), t("11:00")), true);
    assert.equal(intervalosSeSolapan(t("10:00"), t("12:00"), t("10:30"), t("11:00")), true);
    assert.equal(intervalosSeSolapan(t("10:00"), t("11:00"), t("10:00"), t("11:00")), true);
  });
  it("citas contiguas y separadas no se solapan", () => {
    assert.equal(intervalosSeSolapan(t("10:00"), t("11:00"), t("11:00"), t("12:00")), false);
    assert.equal(intervalosSeSolapan(t("11:00"), t("12:00"), t("10:00"), t("11:00")), false);
    assert.equal(intervalosSeSolapan(t("10:00"), t("11:00"), t("13:00"), t("14:00")), false);
  });
});

describe("hayReservaSolapada", () => {
  const nueva = { inicio: t("10:00"), fin: t("11:00") };
  const reserva = (estado: unknown, inicio: string, duracionMinutos: unknown = 60) => ({
    estado,
    inicio: t(inicio),
    duracionMinutos,
  });
  const choca = (r: ReturnType<typeof reserva>) => hayReservaSolapada([r], nueva.inicio, nueva.fin);

  it("estados SOLICITADA, CONFIRMADA y EN_CURSO ocupan agenda", () => {
    for (const estado of ["SOLICITADA", "CONFIRMADA", "EN_CURSO"]) assert.equal(choca(reserva(estado, "10:30")), true, estado);
  });
  it("estados terminales o cancelados no ocupan agenda", () => {
    for (const estado of ["COMPLETADA", "CANCELADA_CLIENTE", "CANCELADA_PROFESIONAL", "RECHAZADA", undefined, 7]) {
      assert.equal(choca(reserva(estado, "10:30")), false, String(estado));
    }
  });
  it("usa la duracion de la reserva existente: 08:30 + 120 min llega al slot; 08:30 + 90 min termina justo antes", () => {
    assert.equal(choca(reserva("CONFIRMADA", "08:30", 120)), true);
    assert.equal(choca(reserva("CONFIRMADA", "08:30", 90)), false);
  });
  it("sin duracion valida asume 60 minutos", () => {
    assert.equal(choca(reserva("CONFIRMADA", "09:30", undefined)), true); // 09:30-10:30
    assert.equal(choca(reserva("CONFIRMADA", "09:00", undefined)), false); // 09:00-10:00
    assert.equal(choca(reserva("CONFIRMADA", "09:30", "no")), true);
    assert.equal(choca(reserva("CONFIRMADA", "09:30", -5)), true);
  });
  it("lista vacia no choca", () => {
    assert.equal(hayReservaSolapada([], nueva.inicio, nueva.fin), false);
  });
});

describe("ventanaDeConsulta / fin", () => {
  it("la ventana empieza 24h antes del inicio y termina en el fin del slot", () => {
    const f = fin(t("10:00"), 45);
    assert.equal(f.toISOString(), "2026-10-05T10:45:00.000Z");
    const v = ventanaDeConsulta(t("10:00"), f);
    assert.equal(v.desde.toISOString(), "2026-10-04T10:00:00.000Z");
    assert.equal(v.hasta.toISOString(), "2026-10-05T10:45:00.000Z");
  });
});
