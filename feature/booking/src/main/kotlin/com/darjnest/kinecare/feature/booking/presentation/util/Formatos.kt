@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.booking.presentation.util

import com.darjnest.kinecare.core.common.data.error.CrearReservaError
import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.domain.model.Direccion
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.util.ZonaHorariaChile
import com.darjnest.kinecare.feature.booking.presentation.viewmodel.PasoReserva
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

private val localeChile: Locale = Locale.Builder().setLanguage("es").setRegion("CL").build()

private val MESES_CORTOS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")
private val MESES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

fun tituloPaso(paso: PasoReserva): String = when (paso) {
    PasoReserva.MODALIDAD -> "Modalidad y servicio"
    PasoReserva.FECHA_HORA -> "Fecha y hora"
    PasoReserva.DIRECCION -> "Dirección de atención"
    PasoReserva.REVISION -> "Revisa tu reserva"
}

fun etiquetaModalidad(modalidad: ModalidadServicio): String = when (modalidad) {
    ModalidadServicio.DOMICILIO -> "A domicilio"
    ModalidadServicio.CONSULTA -> "En consulta"
    ModalidadServicio.ONLINE -> "Online"
}

fun nombreDiaCorto(dia: DayOfWeek): String = when (dia) {
    DayOfWeek.MONDAY -> "Lun"
    DayOfWeek.TUESDAY -> "Mar"
    DayOfWeek.WEDNESDAY -> "Mié"
    DayOfWeek.THURSDAY -> "Jue"
    DayOfWeek.FRIDAY -> "Vie"
    DayOfWeek.SATURDAY -> "Sáb"
    DayOfWeek.SUNDAY -> "Dom"
}

private fun nombreDia(dia: DayOfWeek): String = when (dia) {
    DayOfWeek.MONDAY -> "lunes"
    DayOfWeek.TUESDAY -> "martes"
    DayOfWeek.WEDNESDAY -> "miércoles"
    DayOfWeek.THURSDAY -> "jueves"
    DayOfWeek.FRIDAY -> "viernes"
    DayOfWeek.SATURDAY -> "sábado"
    DayOfWeek.SUNDAY -> "domingo"
}

/** "5 oct". */
fun formatearDiaMesCorto(fecha: LocalDate): String = "${fecha.day} ${MESES_CORTOS[fecha.month.ordinal]}"

/** "lunes 5 de octubre". */
fun formatearFechaLarga(fecha: LocalDate): String =
    "${nombreDia(fecha.dayOfWeek)} ${fecha.day} de ${MESES[fecha.month.ordinal]}"

/** "09:30", siempre en hora de Chile (la de la disponibilidad del profesional). */
fun formatearHora(instante: Instant): String {
    val local = instante.toLocalDateTime(ZonaHorariaChile)
    return String.format(localeChile, "%02d:%02d", local.hour, local.minute)
}

/** "lunes 5 de octubre, 09:30". */
fun formatearFechaHora(instante: Instant): String =
    "${formatearFechaLarga(instante.toLocalDateTime(ZonaHorariaChile).date)}, ${formatearHora(instante)}"

fun textoDuracion(minutos: Int): String = "$minutos min"

/** "Av. Providencia 1234, Providencia, Santiago". */
fun formatearDireccion(direccion: Direccion): String =
    listOf("${direccion.calle} ${direccion.numero}".trim(), direccion.comuna, direccion.ciudad)
        .filter { it.isNotBlank() }
        .joinToString(", ")

fun mensajeErrorCarga(error: ProfesionalError): String = when (error) {
    ProfesionalError.SIN_INTERNET -> "Sin conexión a internet. Revisa tu red e inténtalo de nuevo."
    ProfesionalError.NO_ENCONTRADO -> "No encontramos a este profesional. Puede que ya no esté disponible."
    ProfesionalError.DESCONOCIDO -> "Ocurrió un error inesperado al cargar los servicios."
}

fun mensajeErrorEnvio(error: CrearReservaError): String = when (error) {
    CrearReservaError.SIN_SESION -> "Inicia sesión para reservar."
    CrearReservaError.SIN_INTERNET -> "Sin conexión a internet. Revisa tu red e inténtalo de nuevo."
    CrearReservaError.ROL_INVALIDO -> "Solo una cuenta de cliente puede reservar con este profesional."
    CrearReservaError.DATOS_INVALIDOS -> "Revisa los datos de la reserva e inténtalo de nuevo."
    CrearReservaError.DIRECCION_REQUERIDA -> "Este servicio es a domicilio: completa calle, número y comuna."
    CrearReservaError.SERVICIO_NO_DISPONIBLE -> "Este servicio ya no está disponible para reservar."
    CrearReservaError.ANTICIPACION_INSUFICIENTE -> "Ese horario ya no se puede reservar. Elige otro con al menos una hora de anticipación."
    CrearReservaError.FUERA_DE_HORARIO -> "Ese horario ya no está dentro de la agenda del profesional. Elige otro."
    CrearReservaError.HORARIO_OCUPADO -> "Alguien acaba de reservar ese horario. Elige otro."
    CrearReservaError.DESCONOCIDO -> "No pudimos crear la reserva. Inténtalo de nuevo en unos minutos."
}
