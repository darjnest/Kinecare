package com.darjnest.kinecare.core.common.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

data class Disponibilidad(
    val diaSemana: DayOfWeek,
    val horaInicio: LocalTime,
    val horaFin: LocalTime,
    val activo: Boolean,
)
