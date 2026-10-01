@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.booking.domain.service

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
}
