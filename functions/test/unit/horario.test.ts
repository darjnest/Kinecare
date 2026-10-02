import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { aHoraLocal, parseHoraMinutos, slotDentroDeDisponibilidad } from "../../src/horario.js";

const dia = (diaSemana: string, horaInicio: string, horaFin: string, activo = true) => ({
  diaSemana,
  horaInicio,
  horaFin,
  activo,
});
const utc = (s: string) => new Date(s);

describe("aHoraLocal (America/Santiago)", () => {
  it("usa UTC-3 en horario de verano y UTC-4 en invierno", () => {
    // 2026-10-05 (Lunes) 13:00Z -> 10:00 local con DST.
    assert.deepEqual(aHoraLocal(utc("2026-10-05T13:00:00Z")), { fecha: "2026-10-05", minutos: 600, diaSemana: "MONDAY" });
    // 2026-07-06 (Lunes) 13:00Z -> 09:00 local sin DST.
    assert.deepEqual(aHoraLocal(utc("2026-07-06T13:00:00Z")), { fecha: "2026-07-06", minutos: 540, diaSemana: "MONDAY" });
  });

  it("el dia de la semana es el local, no el UTC", () => {
    // Lunes 23:30 en Chile ya es martes 02:30Z.
    const l = aHoraLocal(utc("2026-10-06T02:30:00Z"));
    assert.equal(l.diaSemana, "MONDAY");
    assert.equal(l.fecha, "2026-10-05");
    assert.equal(l.minutos, 23 * 60 + 30);
    // Y a la inversa: martes 00:30 Chile es martes 03:30Z (sigue siendo martes en ambos);
    // domingo 21:00 Chile es lunes 00:00Z.
    assert.equal(aHoraLocal(utc("2026-10-05T00:00:00Z")).diaSemana, "SUNDAY");
  });

  it("transicion a horario de verano: 2026-09-06 04:00Z salta de 23:59 a 01:00", () => {
    const antes = aHoraLocal(utc("2026-09-06T03:59:00Z"));
    assert.equal(antes.fecha, "2026-09-05");
    assert.equal(antes.minutos, 23 * 60 + 59);
    const despues = aHoraLocal(utc("2026-09-06T04:00:00Z"));
    assert.equal(despues.fecha, "2026-09-06");
    assert.equal(despues.minutos, 60);
    assert.equal(despues.diaSemana, "SUNDAY");
  });

  it("transicion a horario de invierno: 2026-04-05 03:00Z retrocede de 23:59 a 23:00", () => {
    assert.equal(aHoraLocal(utc("2026-04-05T02:59:00Z")).minutos, 23 * 60 + 59);
    assert.equal(aHoraLocal(utc("2026-04-05T03:00:00Z")).minutos, 23 * 60);
  });
});

describe("parseHoraMinutos", () => {
  it("parsea HH:mm y 24:00; rechaza lo demas", () => {
    assert.equal(parseHoraMinutos("00:00"), 0);
    assert.equal(parseHoraMinutos("09:30"), 570);
    assert.equal(parseHoraMinutos("23:59"), 1439);
    assert.equal(parseHoraMinutos("24:00"), 1440);
    for (const v of ["9:30", "24:01", "12:60", "12:5", "", "abc", null, undefined, 900]) {
      assert.equal(parseHoraMinutos(v), null, String(v));
    }
  });

  it("tolera segundos (HH:mm:ss), como LocalTime.parse del cliente Android", () => {
    assert.equal(parseHoraMinutos("09:00:00"), 540);
    assert.equal(parseHoraMinutos("13:00:00"), 780);
    assert.equal(parseHoraMinutos("24:00:00"), 1440);
    assert.equal(parseHoraMinutos("09:00:30"), 540.5);
    for (const v of ["09:00:60", "09:00:", "09:00:0", "24:00:01", "09:00:00.000"]) {
      assert.equal(parseHoraMinutos(v), null, v);
    }
  });
});

describe("slotDentroDeDisponibilidad", () => {
  const lunes = [dia("MONDAY", "09:00", "18:00")];

  it("acepta disponibilidad guardada con segundos (\"09:00:00\")", () => {
    const conSegundos = [dia("MONDAY", "09:00:00", "13:00:00")];
    // Lunes 2026-10-05 10:00 local (13:00Z), 60 min.
    assert.equal(slotDentroDeDisponibilidad(conSegundos, utc("2026-10-05T13:00:00Z"), 60), true);
    // 12:30 local + 60 min se pasa de las 13:00.
    assert.equal(slotDentroDeDisponibilidad(conSegundos, utc("2026-10-05T15:30:00Z"), 60), false);
  });

  it("acepta un slot completamente dentro, incluidos los bordes", () => {
    assert.equal(slotDentroDeDisponibilidad(lunes, utc("2026-10-05T13:00:00Z"), 60), true); // 10:00-11:00
    assert.equal(slotDentroDeDisponibilidad(lunes, utc("2026-10-05T12:00:00Z"), 60), true); // 09:00-10:00
    assert.equal(slotDentroDeDisponibilidad(lunes, utc("2026-10-05T20:00:00Z"), 60), true); // 17:00-18:00
  });

  it("rechaza slots que empiezan antes o terminan despues", () => {
    assert.equal(slotDentroDeDisponibilidad(lunes, utc("2026-10-05T11:59:00Z"), 60), false); // 08:59
    assert.equal(slotDentroDeDisponibilidad(lunes, utc("2026-10-05T20:01:00Z"), 60), false); // termina 18:01
    assert.equal(slotDentroDeDisponibilidad(lunes, utc("2026-10-05T13:00:00Z"), 9 * 60), false); // 10:00-19:00
  });

  it("respeta el dia de la semana y el flag activo", () => {
    assert.equal(slotDentroDeDisponibilidad(lunes, utc("2026-10-06T13:00:00Z"), 60), false); // martes
    assert.equal(slotDentroDeDisponibilidad([dia("MONDAY", "09:00", "18:00", false)], utc("2026-10-05T13:00:00Z"), 60), false);
  });

  it("usa cualquiera de varios turnos del mismo dia, pero el slot no puede abarcar dos", () => {
    const partido = [dia("MONDAY", "09:00", "13:00"), dia("MONDAY", "15:00", "19:00")];
    assert.equal(slotDentroDeDisponibilidad(partido, utc("2026-10-05T18:00:00Z"), 60), true); // 15:00-16:00
    assert.equal(slotDentroDeDisponibilidad(partido, utc("2026-10-05T16:30:00Z"), 60), false); // 13:30-14:30
    assert.equal(slotDentroDeDisponibilidad(partido, utc("2026-10-05T15:30:00Z"), 120), false); // 12:30-14:30
  });

  it("DST: la misma hora de pared cae en distinto instante UTC segun la estacion", () => {
    const l = [dia("MONDAY", "09:00", "10:00")];
    // Invierno (UTC-4): 09:00 local = 13:00Z.
    assert.equal(slotDentroDeDisponibilidad(l, utc("2026-07-06T13:00:00Z"), 60), true);
    assert.equal(slotDentroDeDisponibilidad(l, utc("2026-07-06T12:00:00Z"), 60), false); // 08:00 local
    // Verano (UTC-3): 09:00 local = 12:00Z.
    assert.equal(slotDentroDeDisponibilidad(l, utc("2026-10-05T12:00:00Z"), 60), true);
    assert.equal(slotDentroDeDisponibilidad(l, utc("2026-10-05T13:00:00Z"), 60), false); // 10:00 local
  });

  it("DST: slot justo despues del salto de reloj (domingo 01:00 local)", () => {
    const l = [dia("SUNDAY", "01:00", "02:00")];
    assert.equal(slotDentroDeDisponibilidad(l, utc("2026-09-06T04:00:00Z"), 60), true);
  });

  it("DST: un slot sabado 23:00 +60min cruza el salto y por tanto la medianoche local", () => {
    const l = [dia("SATURDAY", "22:00", "24:00"), dia("SUNDAY", "00:00", "24:00")];
    // 03:00Z = sab 23:00 (UTC-4); +60 min = 04:00Z = dom 01:00 (UTC-3): cruza.
    assert.equal(slotDentroDeDisponibilidad(l, utc("2026-09-06T03:00:00Z"), 60), false);
    // 02:00Z = sab 22:00; termina 03:00Z = 23:00 local: dentro.
    assert.equal(slotDentroDeDisponibilidad(l, utc("2026-09-06T02:00:00Z"), 60), true);
  });

  it("DST invierno: sabado 23:00 local ocurre una sola vez a 03:00Z y cabe en 23:00-23:59", () => {
    const l = [dia("SATURDAY", "23:00", "23:59")];
    assert.equal(slotDentroDeDisponibilidad(l, utc("2026-04-05T03:00:00Z"), 30), true);
  });

  it("limite de dia: usa el dia local, no el UTC", () => {
    // Lunes 23:00-23:30 local = martes 02:00Z-02:30Z.
    const l = [dia("MONDAY", "22:00", "23:59")];
    assert.equal(slotDentroDeDisponibilidad(l, utc("2026-10-06T02:00:00Z"), 30), true);
    // Si solo hay horario el martes, ese instante UTC "martes" no vale.
    assert.equal(slotDentroDeDisponibilidad([dia("TUESDAY", "00:00", "24:00")], utc("2026-10-06T02:00:00Z"), 30), false);
  });

  it("no cruza la medianoche; terminar justo a las 24:00 solo vale con horaFin 24:00", () => {
    const hasta2359 = [dia("MONDAY", "20:00", "23:59")];
    const hasta2400 = [dia("MONDAY", "20:00", "24:00")];
    // Lunes 23:00-24:00 local = martes 02:00Z-03:00Z.
    assert.equal(slotDentroDeDisponibilidad(hasta2400, utc("2026-10-06T02:00:00Z"), 60), true);
    assert.equal(slotDentroDeDisponibilidad(hasta2359, utc("2026-10-06T02:00:00Z"), 60), false);
    // Lunes 23:30 + 60 min = martes 00:30: cruza.
    assert.equal(slotDentroDeDisponibilidad(hasta2400, utc("2026-10-06T02:30:00Z"), 60), false);
    const todoElDia = [dia("MONDAY", "00:00", "24:00"), dia("TUESDAY", "00:00", "24:00")];
    assert.equal(slotDentroDeDisponibilidad(todoElDia, utc("2026-10-06T02:30:00Z"), 60), false);
  });

  it("ignora entradas mal formadas y entradas no-arreglo", () => {
    const sucio = [null, 5, "x", {}, dia("MONDAY", "xx", "18:00"), dia("MONDAY", "09:00", "18:00")];
    assert.equal(slotDentroDeDisponibilidad(sucio, utc("2026-10-05T13:00:00Z"), 60), true);
    assert.equal(slotDentroDeDisponibilidad(undefined, utc("2026-10-05T13:00:00Z"), 60), false);
    assert.equal(slotDentroDeDisponibilidad("MONDAY", utc("2026-10-05T13:00:00Z"), 60), false);
    assert.equal(slotDentroDeDisponibilidad([dia("MONDAY", "09:00", "10:00")], utc("2026-10-05T13:00:00Z"), 0), false);
    assert.equal(slotDentroDeDisponibilidad([{ ...dia("MONDAY", "09:00", "18:00"), activo: "true" }], utc("2026-10-05T13:00:00Z"), 60), false);
  });
});
