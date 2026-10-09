import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { puedeAplicarse, requiereComparacionDeRut, resolucionDesdeDidit, type Resolucion } from "../../src/verificacion/estados.js";
import { camposInsigniaIdentidad, conInsigniaIdentidad, estadoInsigniaIdentidad } from "../../src/verificacion/insignias.js";
import { compararRut, normalizarRut, tieneFormaDeRun } from "../../src/verificacion/rut.js";

describe("normalizarRut / tieneFormaDeRun", () => {
  it("quita puntos, guion y espacios, pasa a mayusculas y quita ceros a la izquierda", () => {
    assert.equal(normalizarRut("12.345.678-5"), "123456785");
    assert.equal(normalizarRut(" 12345678-k "), "12345678K");
    assert.equal(normalizarRut("0012345678-5"), "123456785");
    assert.equal(normalizarRut("7.654.321-K"), "7654321K");
    assert.equal(normalizarRut(""), "");
  });

  it("reconoce la forma de un RUN (7-8 digitos + DV 0-9/K) y rechaza el resto", () => {
    for (const ok of ["123456785", "12345678K", "76543210", "7654321K"]) assert.ok(tieneFormaDeRun(ok), ok);
    // 8 caracteres (7 digitos de cuerpo + DV) tambien vale; 6 y 10 no.
    for (const mal of ["", "12345", "1234567890", "A12345678", "12345678X", "ABCDEFGHI", "1234567-"]) assert.ok(!tieneFormaDeRun(mal), mal);
  });
});

describe("compararRut", () => {
  it("coincide ignorando formato", () => {
    assert.equal(compararRut("12.345.678-5", "123456785"), true);
    assert.equal(compararRut("12345678-5", "12.345.678-5"), true);
    assert.equal(compararRut("7654321-k", "7654321K"), true);
    assert.equal(compararRut("012345678-5", "12345678-5"), true);
  });

  it("no coincide con un RUN distinto", () => {
    assert.equal(compararRut("12.345.678-5", "98765432-1"), false);
    assert.equal(compararRut("12345678-5", "12345678-K"), false);
  });

  it("no es comparable si el proveedor no entrega nada o no tiene forma de RUN", () => {
    assert.equal(compararRut(null, "123456785"), null);
    assert.equal(compararRut("", "123456785"), null);
    assert.equal(compararRut("A1234", "123456785"), null);
    assert.equal(compararRut("P123456789012", "123456785"), null);
  });

  it("no es comparable si el usuario no tiene rut", () => {
    assert.equal(compararRut("12345678-5", undefined), null);
    assert.equal(compararRut("12345678-5", null), null);
    assert.equal(compararRut("12345678-5", 123456785), null);
    assert.equal(compararRut("12345678-5", "  "), null);
  });
});

describe("resolucionDesdeDidit (tabla)", () => {
  const casos: Array<[string, boolean | null, Resolucion | null]> = [
    ["Approved", true, { solicitud: "APROBADO", insignia: "APROBADO", motivo: null, rutCoincide: true }],
    ["Approved", null, { solicitud: "APROBADO", insignia: "APROBADO", motivo: null, rutCoincide: null }],
    ["Approved", false, { solicitud: "RECHAZADO", insignia: "RECHAZADO", motivo: "RUT_NO_COINCIDE", rutCoincide: false }],
    ["Declined", null, { solicitud: "RECHAZADO", insignia: "RECHAZADO", motivo: "DECLINED", rutCoincide: null }],
    ["In Review", null, { solicitud: "PENDIENTE", insignia: "PENDIENTE", motivo: null, rutCoincide: null }],
    ["Not Started", null, { solicitud: "PENDIENTE", insignia: "PENDIENTE", motivo: null, rutCoincide: null }],
    ["In Progress", null, { solicitud: "PENDIENTE", insignia: "PENDIENTE", motivo: null, rutCoincide: null }],
    ["Resubmitted", null, { solicitud: "PENDIENTE", insignia: "PENDIENTE", motivo: null, rutCoincide: null }],
    ["Awaiting User", null, { solicitud: "PENDIENTE", insignia: "PENDIENTE", motivo: null, rutCoincide: null }],
    ["Expired", null, { solicitud: "RECHAZADO", insignia: "NO_SOLICITADO", motivo: "EXPIRADA", rutCoincide: null }],
    ["Abandoned", null, { solicitud: "RECHAZADO", insignia: "NO_SOLICITADO", motivo: "ABANDONADA", rutCoincide: null }],
    ["Kyc Expired", null, { solicitud: "RECHAZADO", insignia: "NO_SOLICITADO", motivo: "KYC_VENCIDO", rutCoincide: null }],
    ["approved", null, null], // sensible a mayusculas
    ["Cualquier cosa", null, null],
    ["", null, null],
  ];
  for (const [estado, rut, esperado] of casos) {
    it(`${JSON.stringify(estado)} (rutCoincide=${rut}) -> ${esperado === null ? "desconocido" : `${esperado.solicitud}/${esperado.insignia}/${esperado.motivo}`}`, () => {
      assert.deepEqual(resolucionDesdeDidit(estado, rut), esperado);
    });
  }

  it("solo Approved exige comparar el RUT", () => {
    assert.ok(requiereComparacionDeRut("Approved"));
    for (const e of ["Declined", "In Review", "Expired", "Kyc Expired"]) assert.ok(!requiereComparacionDeRut(e), e);
  });
});

describe("puedeAplicarse (estados finales no retroceden)", () => {
  const r = (estado: string) => resolucionDesdeDidit(estado, null)!;

  it("un PENDIENTE acepta cualquier resolucion", () => {
    for (const e of ["Approved", "Declined", "In Review", "Expired", "Abandoned", "Kyc Expired"]) assert.ok(puedeAplicarse("PENDIENTE", r(e)), e);
  });

  it("un APROBADO solo cambia por Kyc Expired", () => {
    for (const e of ["Approved", "Declined", "In Review", "In Progress", "Expired", "Abandoned"]) assert.ok(!puedeAplicarse("APROBADO", r(e)), e);
    assert.ok(puedeAplicarse("APROBADO", r("Kyc Expired")));
  });

  it("un RECHAZADO no cambia por nada, ni siquiera Kyc Expired", () => {
    for (const e of ["Approved", "Declined", "In Review", "Not Started", "Expired", "Kyc Expired"]) assert.ok(!puedeAplicarse("RECHAZADO", r(e)), e);
  });
});

describe("insignias IDENTIDAD", () => {
  const AHORA = new Date("2026-10-01T12:00:00Z");
  const otra = { tipo: "CREDENCIALES", estado: "APROBADO", detalle: "RNPI vigente" };

  it("agrega la entrada IDENTIDAD conservando las demas", () => {
    const r = conInsigniaIdentidad([otra], "PENDIENTE", AHORA) as Array<Record<string, unknown>>;
    assert.equal(r.length, 2);
    assert.deepEqual(r[0], otra);
    assert.equal(r[1].tipo, "IDENTIDAD");
    assert.equal(r[1].estado, "PENDIENTE");
    assert.equal(r[1].detalle, "Verificación en curso");
  });

  it("reemplaza en su lugar la entrada IDENTIDAD existente", () => {
    const previa = { tipo: "IDENTIDAD", estado: "PENDIENTE", detalle: "Verificación en curso" };
    const r = conInsigniaIdentidad([previa, otra], "APROBADO", AHORA) as Array<Record<string, unknown>>;
    assert.equal(r.length, 2);
    assert.equal(r[0].estado, "APROBADO");
    assert.equal(r[0].detalle, "Identidad verificada con documento y prueba de vida");
    assert.deepEqual(r[1], otra);
  });

  it("detalle publico y neutro; NO_SOLICITADO no lleva detalle", () => {
    const rechazado = conInsigniaIdentidad([], "RECHAZADO", AHORA)[0] as Record<string, unknown>;
    assert.equal(rechazado.detalle, "No se pudo verificar la identidad");
    const no = conInsigniaIdentidad([], "NO_SOLICITADO", AHORA)[0] as Record<string, unknown>;
    assert.ok(!("detalle" in no));
  });

  it("tolera insignias ausente o mal formada", () => {
    assert.equal(conInsigniaIdentidad(undefined, "PENDIENTE", AHORA).length, 1);
    assert.equal(estadoInsigniaIdentidad(undefined), undefined);
    assert.equal(estadoInsigniaIdentidad([otra]), undefined);
    assert.equal(estadoInsigniaIdentidad(["x", { tipo: "IDENTIDAD", estado: "APROBADO" }]), "APROBADO");
  });

  it("estadoVerificacionGeneral replica el estado de la insignia IDENTIDAD", () => {
    assert.equal(camposInsigniaIdentidad([], "RECHAZADO", AHORA).estadoVerificacionGeneral, "RECHAZADO");
  });
});
