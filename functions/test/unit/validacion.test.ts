import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { direccionCompleta, parseFechaHora, parseSolicitud } from "../../src/validacion.js";
import { esperarErrorSync } from "../helpers.js";

const base = { profesionalId: "pro1", servicioId: "srv1", fechaHora: "2026-10-05T13:00:00Z" };

function invalido(data: unknown) {
  esperarErrorSync(() => parseSolicitud(data), "invalid-argument", "DATOS_INVALIDOS");
}

describe("parseSolicitud", () => {
  it("acepta el payload minimo y normaliza ids", () => {
    const s = parseSolicitud({ ...base, profesionalId: "  pro1 " });
    assert.equal(s.profesionalId, "pro1");
    assert.equal(s.servicioId, "srv1");
    assert.equal(s.fechaHora.toISOString(), "2026-10-05T13:00:00.000Z");
    assert.equal(s.direccion, null);
  });

  it("direccion null o ausente da null; ignora claves desconocidas", () => {
    assert.equal(parseSolicitud({ ...base, direccion: null, precio: 1, modalidad: "ONLINE" }).direccion, null);
  });

  it("recorta la direccion y completa opcionales con null", () => {
    const s = parseSolicitud({
      ...base,
      direccion: { calle: " Av. Siempre Viva ", numero: " 742", comuna: "Providencia ", ciudad: "Santiago" },
    });
    assert.deepEqual(s.direccion, {
      calle: "Av. Siempre Viva",
      numero: "742",
      comuna: "Providencia",
      ciudad: "Santiago",
      lat: null,
      lng: null,
      indicaciones: null,
    });
  });

  it("conserva lat/lng/indicaciones validos", () => {
    const s = parseSolicitud({
      ...base,
      direccion: { calle: "a", numero: "1", comuna: "c", ciudad: "d", lat: -33.4, lng: -70.6, indicaciones: " depto 3 " },
    });
    assert.equal(s.direccion?.lat, -33.4);
    assert.equal(s.direccion?.lng, -70.6);
    assert.equal(s.direccion?.indicaciones, "depto 3");
  });

  it("rechaza cuerpos que no son objeto", () => {
    for (const d of [undefined, null, "x", 3, [], [base]]) invalido(d);
  });

  it("rechaza ids ausentes, en blanco, de otro tipo, largos o con barra", () => {
    for (const v of [undefined, null, "", "   ", 5, {}, "a".repeat(129), "otro/doc"]) {
      invalido({ ...base, profesionalId: v });
      invalido({ ...base, servicioId: v });
    }
  });

  it("rechaza fechaHora mal formada", () => {
    for (const v of [
      undefined,
      null,
      "",
      "manana",
      1759669200000,
      "2026-10-05",
      "2026-10-05T13:00:00", // sin zona
      "2026-13-05T13:00:00Z",
      "2026-02-30T10:00:00Z", // Date lo "corrige" a marzo
      "2026-10-05T25:00:00Z",
      {},
    ]) {
      invalido({ ...base, fechaHora: v });
    }
  });

  it("acepta fechaHora con offset y milisegundos", () => {
    assert.equal(parseFechaHora("2026-10-05T10:00:00-03:00").toISOString(), "2026-10-05T13:00:00.000Z");
    assert.equal(parseFechaHora("2026-10-05T13:00:00.500Z").getUTCMilliseconds(), 500);
    assert.equal(parseFechaHora("2028-02-29T10:00Z").toISOString(), "2028-02-29T10:00:00.000Z");
  });

  it("rechaza direccion de tipo incorrecto", () => {
    for (const v of ["calle", 3, [], true]) invalido({ ...base, direccion: v });
  });

  it("rechaza campos de direccion que no son texto", () => {
    for (const campo of ["calle", "numero", "comuna", "ciudad", "indicaciones"]) {
      invalido({ ...base, direccion: { calle: "a", numero: "1", comuna: "c", ciudad: "d", [campo]: 12 } });
      invalido({ ...base, direccion: { calle: "a", numero: "1", comuna: "c", ciudad: "d", [campo]: {} } });
    }
  });

  it("aplica los topes de largo (120 y 300) tras recortar", () => {
    const dir = { calle: "a", numero: "1", comuna: "c", ciudad: "d" };
    for (const campo of ["calle", "numero", "comuna", "ciudad"]) {
      parseSolicitud({ ...base, direccion: { ...dir, [campo]: "x".repeat(120) } });
      parseSolicitud({ ...base, direccion: { ...dir, [campo]: ` ${"x".repeat(120)} ` } });
      invalido({ ...base, direccion: { ...dir, [campo]: "x".repeat(121) } });
    }
    parseSolicitud({ ...base, direccion: { ...dir, indicaciones: "x".repeat(300) } });
    invalido({ ...base, direccion: { ...dir, indicaciones: "x".repeat(301) } });
  });

  it("rechaza lat/lng no numericas, infinitas o fuera de rango", () => {
    const dir = { calle: "a", numero: "1", comuna: "c", ciudad: "d" };
    for (const v of ["1", NaN, Infinity, 91, -91, {}]) invalido({ ...base, direccion: { ...dir, lat: v } });
    for (const v of ["1", NaN, Infinity, 181, -181]) invalido({ ...base, direccion: { ...dir, lng: v } });
  });

  it("una direccion con campos en blanco NO es DATOS_INVALIDOS (decide el handler segun modalidad)", () => {
    const s = parseSolicitud({ ...base, direccion: { calle: " ", numero: "", comuna: null, ciudad: "x" } });
    assert.equal(direccionCompleta(s.direccion), false);
  });
});

describe("direccionCompleta", () => {
  const ok = { calle: "a", numero: "1", comuna: "c", ciudad: "", lat: null, lng: null, indicaciones: null };
  it("exige calle, numero y comuna; ciudad puede estar vacia", () => {
    assert.equal(direccionCompleta(ok), true);
    assert.equal(direccionCompleta(null), false);
    for (const campo of ["calle", "numero", "comuna"] as const) {
      assert.equal(direccionCompleta({ ...ok, [campo]: "" }), false);
    }
  });
});
