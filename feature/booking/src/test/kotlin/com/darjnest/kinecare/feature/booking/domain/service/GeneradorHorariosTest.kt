@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.booking.domain.service

import com.darjnest.kinecare.core.common.domain.model.CupoOcupado
import com.darjnest.kinecare.core.common.domain.model.Disponibilidad
import com.darjnest.kinecare.core.common.util.ZonaHorariaChile
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Octubre en Chile continental es horario de verano (UTC-3): 09:00 local =
 * 12:00Z. 2026-10-05 es lunes.
 */
class GeneradorHorariosTest {

    private fun turno(dia: DayOfWeek, inicio: String, fin: String, activo: Boolean = true) =
        Disponibilidad(dia, LocalTime.parse(inicio), LocalTime.parse(fin), activo)

    private fun generar(
        disponibilidad: List<Disponibilidad>,
        duracion: Int = 60,
        ahora: String = "2026-10-05T11:00:00Z",
    ) = generarDiasConHorarios(disponibilidad, duracion, Instant.parse(ahora), ZonaHorariaChile)

    @Test
    fun `parte cada turno en cupos de la duracion del servicio, en hora de Chile`() {
        val dias = generar(listOf(turno(DayOfWeek.MONDAY, "09:00", "13:00")))

        val lunes = dias.first()
        assertEquals(LocalDate(2026, 10, 5), lunes.fecha)
        assertEquals(
            listOf("12:00", "13:00", "14:00", "15:00").map { Instant.parse("2026-10-05T$it:00Z") },
            lunes.horarios,
        )
    }

    @Test
    fun `descarta el cupo que no alcanza a terminar antes del fin del turno`() {
        val dias = generar(listOf(turno(DayOfWeek.MONDAY, "09:00", "13:00")), duracion = 90)

        assertEquals(
            listOf(Instant.parse("2026-10-05T12:00:00Z"), Instant.parse("2026-10-05T13:30:00Z")),
            dias.first().horarios,
        )
    }

    @Test
    fun `excluye los cupos dentro de la anticipacion minima`() {
        // 09:30 local: el limite es 10:30, asi que 09:00 y 10:00 quedan fuera.
        val dias = generar(listOf(turno(DayOfWeek.MONDAY, "09:00", "13:00")), ahora = "2026-10-05T12:30:00Z")

        assertEquals(
            listOf(Instant.parse("2026-10-05T14:00:00Z"), Instant.parse("2026-10-05T15:00:00Z")),
            dias.first().horarios,
        )
    }

    @Test
    fun `un dia sin cupos futuros no aparece`() {
        // 12:30 local: el turno de hoy ya no tiene cupos con 60 min de anticipacion.
        val dias = generar(listOf(turno(DayOfWeek.MONDAY, "09:00", "13:00")), ahora = "2026-10-05T15:30:00Z")

        assertEquals(LocalDate(2026, 10, 12), dias.first().fecha)
    }

    @Test
    fun `solo cubre el horizonte de dias contando hoy`() {
        val dias = generar(listOf(turno(DayOfWeek.MONDAY, "09:00", "10:00")))

        // Hoy (5) + 14 dias = hasta el 18; el lunes 19 queda fuera.
        assertEquals(listOf(LocalDate(2026, 10, 5), LocalDate(2026, 10, 12)), dias.map { it.fecha })
    }

    @Test
    fun `ignora turnos inactivos y dias sin turnos`() {
        val dias = generar(
            listOf(
                turno(DayOfWeek.MONDAY, "09:00", "10:00", activo = false),
                turno(DayOfWeek.WEDNESDAY, "15:00", "16:00"),
            ),
        )

        assertTrue(dias.all { it.fecha.dayOfWeek == DayOfWeek.WEDNESDAY })
        assertEquals(LocalDate(2026, 10, 7), dias.first().fecha)
    }

    @Test
    fun `turnos superpuestos del mismo dia no duplican cupos y quedan ordenados`() {
        val dias = generar(
            listOf(
                turno(DayOfWeek.MONDAY, "11:00", "12:00"),
                turno(DayOfWeek.MONDAY, "09:00", "12:00"),
            ),
        )

        assertEquals(
            listOf("12:00", "13:00", "14:00").map { Instant.parse("2026-10-05T$it:00Z") },
            dias.first().horarios,
        )
    }

    @Test
    fun `respeta el cambio de horario de abril`() {
        // El 4 de abril de 2027 Chile vuelve a UTC-4: el mismo 09:00 local
        // pasa de 12:00Z a 13:00Z.
        val dias = generar(
            listOf(turno(DayOfWeek.SUNDAY, "09:00", "10:00")),
            ahora = "2027-03-27T12:00:00Z",
        )

        assertEquals(Instant.parse("2027-03-28T12:00:00Z"), dias[0].horarios.single())
        assertEquals(Instant.parse("2027-04-04T13:00:00Z"), dias[1].horarios.single())
    }

    @Test
    fun `duracion invalida no genera cupos`() {
        assertTrue(generar(listOf(turno(DayOfWeek.MONDAY, "09:00", "13:00")), duracion = 0).isEmpty())
    }

    // --- descontarCuposOcupados ---

    private fun dia(vararg horas: String) = DiaConHorarios(
        LocalDate(2026, 10, 5),
        horas.map { Instant.parse("2026-10-05T$it:00Z") },
    )

    private fun ocupado(inicio: String, fin: String) =
        CupoOcupado(Instant.parse("2026-10-05T$inicio:00Z"), Instant.parse("2026-10-05T$fin:00Z"))

    @Test
    fun `sin tramos ocupados devuelve los mismos dias`() {
        val dias = listOf(dia("12:00", "13:00"))

        assertEquals(dias, descontarCuposOcupados(dias, 60, emptyList()))
    }

    @Test
    fun `quita el cupo que coincide con un tramo ocupado`() {
        val resultado = descontarCuposOcupados(listOf(dia("12:00", "13:00", "14:00")), 60, listOf(ocupado("13:00", "14:00")))

        assertEquals(listOf(dia("12:00", "14:00")), resultado)
    }

    @Test
    fun `quita los cupos que se solapan parcialmente por cualquiera de los dos lados`() {
        // Ocupado 13:30-14:30 con cupos de 60 min: caen 13:00 (termina 14:00) y 14:00 (empieza 14:00 < 14:30).
        val resultado = descontarCuposOcupados(
            listOf(dia("12:00", "13:00", "14:00", "15:00")),
            60,
            listOf(ocupado("13:30", "14:30")),
        )

        assertEquals(listOf(dia("12:00", "15:00")), resultado)
    }

    @Test
    fun `un cupo que empieza justo cuando termina la otra reserva no se descarta`() {
        val resultado = descontarCuposOcupados(listOf(dia("12:00", "13:00")), 60, listOf(ocupado("11:00", "12:00")))

        assertEquals(listOf(dia("12:00", "13:00")), resultado)
    }

    @Test
    fun `la duracion del servicio cuenta para el solape`() {
        // Cupo de 120 min a las 12:00 llega hasta las 14:00 y choca con una reserva a las 13:00.
        val resultado = descontarCuposOcupados(listOf(dia("12:00", "14:00")), 120, listOf(ocupado("13:00", "14:00")))

        assertEquals(listOf(dia("14:00")), resultado)
    }

    @Test
    fun `un dia sin cupos libres se elimina`() {
        val resultado = descontarCuposOcupados(
            listOf(dia("12:00", "13:00")),
            60,
            listOf(ocupado("11:30", "14:00")),
        )

        assertTrue(resultado.isEmpty())
    }
}
