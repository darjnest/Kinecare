package com.darjnest.kinecare.feature.professional_profile.presentation.util

import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.TipoInsignia
import com.darjnest.kinecare.core.designsystem.components.label.BadgeTono
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val localeChile: Locale = Locale.Builder().setLanguage("es").setRegion("CL").build()

/** "15 de marzo de 2026". */
fun formatearFecha(epochMillis: Long): String =
    SimpleDateFormat("d 'de' MMMM 'de' yyyy", localeChile).format(Date(epochMillis))

/** "09:00". */
fun formatearHora(hora: LocalTime): String =
    String.format(localeChile, "%02d:%02d", hora.hour, hora.minute)

/** "4,8" (coma decimal, es-CL). */
fun formatearCalificacion(calificacion: Double): String =
    String.format(localeChile, "%.1f", calificacion)

fun textoResenas(total: Int): String = if (total == 1) "1 reseña" else "$total reseñas"

fun textoDuracion(minutos: Int): String = "$minutos min"

fun tituloInsignia(tipo: TipoInsignia): String = when (tipo) {
    TipoInsignia.IDENTIDAD -> "Identidad"
    TipoInsignia.CREDENCIALES -> "Credenciales profesionales"
    TipoInsignia.AUTENTICIDAD -> "Autenticidad"
    TipoInsignia.HISTORIAL -> "Historial"
}

fun textoEstado(estado: EstadoVerificacion): String = when (estado) {
    EstadoVerificacion.APROBADO -> "Verificado"
    EstadoVerificacion.PENDIENTE -> "En revisión"
    EstadoVerificacion.RECHAZADO -> "Rechazado"
    EstadoVerificacion.NO_SOLICITADO -> "No solicitado"
}

fun tonoEstado(estado: EstadoVerificacion): BadgeTono = when (estado) {
    EstadoVerificacion.APROBADO -> BadgeTono.EXITO
    EstadoVerificacion.PENDIENTE -> BadgeTono.INFO
    EstadoVerificacion.RECHAZADO -> BadgeTono.ERROR
    EstadoVerificacion.NO_SOLICITADO -> BadgeTono.NEUTRO
}

/** Explicacion neutra basada solo en el estado, para cuando la insignia no trae `detalle`. */
fun explicacionEstado(estado: EstadoVerificacion): String = when (estado) {
    EstadoVerificacion.APROBADO -> "Esta verificación fue aprobada."
    EstadoVerificacion.PENDIENTE -> "Esta verificación está en revisión."
    EstadoVerificacion.RECHAZADO -> "Esta verificación no fue aprobada."
    EstadoVerificacion.NO_SOLICITADO -> "El profesional aún no ha solicitado esta verificación."
}

fun etiquetaModalidad(modalidad: ModalidadServicio): String = when (modalidad) {
    ModalidadServicio.DOMICILIO -> "A domicilio"
    ModalidadServicio.CONSULTA -> "En consulta"
    ModalidadServicio.ONLINE -> "Online"
}

fun nombreDia(dia: DayOfWeek): String = when (dia) {
    DayOfWeek.MONDAY -> "Lunes"
    DayOfWeek.TUESDAY -> "Martes"
    DayOfWeek.WEDNESDAY -> "Miércoles"
    DayOfWeek.THURSDAY -> "Jueves"
    DayOfWeek.FRIDAY -> "Viernes"
    DayOfWeek.SATURDAY -> "Sábado"
    DayOfWeek.SUNDAY -> "Domingo"
}
