@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.booking.domain.service

import com.darjnest.kinecare.core.common.domain.model.Disponibilidad
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

/**
 * Anticipacion minima de una reserva. La Cloud Function `crearReserva`
 * aplica la misma regla; aqui solo evita ofrecer horarios que el backend
 * rechazaria.
 */
val ANTICIPACION_MINIMA = 60.minutes

/** Cuantos dias hacia adelante (contando hoy) se ofrecen horarios. */
const val DIAS_HORIZONTE = 14

/** Un dia con al menos un horario reservable. */
data class DiaConHorarios(
    val fecha: LocalDate,
    /** Inicio de cada cupo, ordenados. */
    val horarios: List<Instant>,
)

/**
 * Cupos de [duracionMinutos] dentro de los turnos activos de
 * [disponibilidad], expresados en [zona] (hora de Chile, ver
 * `ZonaHorariaChile`). Cada turno se parte desde `horaInicio` en pasos de la
 * duracion del servicio; un cupo que no termina antes de `horaFin` se
 * descarta. Solo se incluyen dias con algun cupo posterior a
 * `ahora + ANTICIPACION_MINIMA`.
 *
 * No conoce las reservas de otros clientes (firestore.rules solo deja leer
 * las propias): un cupo ya tomado se detecta al confirmar, cuando
 * `crearReserva` responde `HORARIO_OCUPADO`.
 */
fun generarDiasConHorarios(
    disponibilidad: List<Disponibilidad>,
    duracionMinutos: Int,
    ahora: Instant,
    zona: TimeZone,
    dias: Int = DIAS_HORIZONTE,
): List<DiaConHorarios> {
    if (duracionMinutos <= 0) return emptyList()
    val hoy = ahora.toLocalDateTime(zona).date
    val limite = ahora + ANTICIPACION_MINIMA
    val turnosPorDia = disponibilidad.filter { it.activo }.groupBy { it.diaSemana }

    return (0 until dias).mapNotNull { desplazamiento ->
        val fecha = hoy.plus(DatePeriod(days = desplazamiento))
        val horarios = turnosPorDia[fecha.dayOfWeek].orEmpty()
            .flatMap { turno -> cuposDelTurno(turno.horaInicio, turno.horaFin, duracionMinutos) }
            .map { LocalDateTime(fecha, it).toInstant(zona) }
            .filter { it >= limite }
            .distinct()
            .sorted()
        horarios.takeIf { it.isNotEmpty() }?.let { DiaConHorarios(fecha, it) }
    }
}

private fun cuposDelTurno(
    inicio: LocalTime,
    fin: LocalTime,
    duracionMinutos: Int,
): List<LocalTime> {
    val finMinutos = fin.hour * 60 + fin.minute
    var minutos = inicio.hour * 60 + inicio.minute
    val cupos = mutableListOf<LocalTime>()
    while (minutos + duracionMinutos <= finMinutos) {
        cupos += LocalTime(minutos / 60, minutos % 60)
        minutos += duracionMinutos
    }
    return cupos
}
