@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.common.util.ZonaHorariaChile
import com.darjnest.kinecare.feature.client_panel.data.repository.ReporteProblemaRepository
import com.darjnest.kinecare.feature.client_panel.domain.EstadoReporte
import com.darjnest.kinecare.feature.client_panel.domain.MotivoReporte
import com.darjnest.kinecare.feature.client_panel.domain.ReporteProblema
import com.darjnest.kinecare.feature.client_panel.domain.ReporteProblemaError
import com.darjnest.kinecare.feature.client_panel.presentation.navigation.ARG_PROFESIONAL_ID
import com.darjnest.kinecare.feature.client_panel.presentation.navigation.ARG_RESERVA_ID
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/** Limites de `descripcion` (los aplica el ViewModel y `firestore.rules`). */
const val LARGO_MINIMO_DESCRIPCION = 10
const val LARGO_MAXIMO_DESCRIPCION = 1000

/** Por que fallo el envio; cada valor tiene su propio mensaje en la vista. */
enum class ErrorEnvioReporte {
    /** No hay usuario autenticado. */
    SIN_SESION,
    SIN_INTERNET,

    /** Reserva ajena o ya reportada (PERMISSION_DENIED). */
    SIN_PERMISO,
    DESCONOCIDO,
}

/** Reporte ya enviado para esta reserva (se muestra de solo lectura). */
data class ReportePropio(
    val motivo: MotivoReporte,
    val descripcion: String,
    val estado: EstadoReporte,
    /** `null` justo despues de enviarlo (la fecha real la pone el servidor). */
    val fechaTexto: String?,
)

data class ReportarProblemaState(
    /** Comprobando con `obtenerPorReserva` si ya existe un reporte. */
    val verificando: Boolean = true,
    /** Fallo la comprobacion inicial: no se muestra el formulario hasta poder reintentar. */
    val errorVerificacion: ReporteProblemaError? = null,
    /** No nulo si la reserva ya tiene reporte: la vista lo muestra en vez del formulario. */
    val reporteExistente: ReportePropio? = null,
    /** `true` si [reporteExistente] se acaba de enviar en esta pantalla (cambia el titulo de la confirmacion). */
    val recienEnviado: Boolean = false,
    val motivo: MotivoReporte? = null,
    val descripcion: String = "",
    val enviando: Boolean = false,
    val error: ErrorEnvioReporte? = null,
) {
    val descripcionValida: Boolean
        get() = descripcion.trim().length >= LARGO_MINIMO_DESCRIPCION

    val puedeEnviar: Boolean
        get() = motivo != null && descripcionValida && !enviando && !verificando &&
            errorVerificacion == null && reporteExistente == null
}

sealed interface ReportarProblemaAction {
    data class SeleccionarMotivo(val motivo: MotivoReporte) : ReportarProblemaAction
    data class CambiarDescripcion(val texto: String) : ReportarProblemaAction
    data object Enviar : ReportarProblemaAction
    data object Reintentar : ReportarProblemaAction

    /** Navegacion: la resuelve el `Root` contra el NavGraph, no el ViewModel. */
    data object VolverAtras : ReportarProblemaAction
}

@HiltViewModel
class ReportarProblemaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reporteProblemaRepository: ReporteProblemaRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val reservaId: String? = savedStateHandle[ARG_RESERVA_ID]
    private val profesionalId: String? = savedStateHandle[ARG_PROFESIONAL_ID]

    private val _state = MutableStateFlow(ReportarProblemaState())
    val state: StateFlow<ReportarProblemaState> = _state.asStateFlow()

    init {
        verificarReporteExistente()
    }

    fun onAction(action: ReportarProblemaAction) {
        when (action) {
            is ReportarProblemaAction.SeleccionarMotivo ->
                _state.update { it.copy(motivo = action.motivo, error = null) }
            is ReportarProblemaAction.CambiarDescripcion ->
                _state.update { it.copy(descripcion = action.texto.take(LARGO_MAXIMO_DESCRIPCION), error = null) }
            ReportarProblemaAction.Enviar -> enviar()
            ReportarProblemaAction.Reintentar -> verificarReporteExistente()
            ReportarProblemaAction.VolverAtras -> Unit
        }
    }

    private fun verificarReporteExistente() {
        val id = reservaId
        if (id.isNullOrBlank()) {
            _state.update { it.copy(verificando = false, errorVerificacion = ReporteProblemaError.DESCONOCIDO) }
            return
        }
        _state.update { it.copy(verificando = true, errorVerificacion = null) }
        viewModelScope.launch {
            when (val resultado = reporteProblemaRepository.obtenerPorReserva(id)) {
                is Result.Success -> _state.update {
                    it.copy(
                        verificando = false,
                        reporteExistente = resultado.data?.let { reporte ->
                            ReportePropio(
                                motivo = reporte.motivo,
                                descripcion = reporte.descripcion,
                                estado = reporte.estado,
                                fechaTexto = reporte.fecha.aFechaTexto(),
                            )
                        },
                    )
                }
                is Result.Error -> _state.update {
                    it.copy(verificando = false, errorVerificacion = resultado.error)
                }
            }
        }
    }

    private fun enviar() {
        val estado = _state.value
        if (!estado.puedeEnviar) return
        val motivo = estado.motivo ?: return
        val reserva = reservaId
        val profesional = profesionalId
        if (reserva.isNullOrBlank() || profesional.isNullOrBlank()) {
            _state.update { it.copy(error = ErrorEnvioReporte.DESCONOCIDO) }
            return
        }
        val clienteId = firebaseAuth.currentUser?.uid
        if (clienteId == null) {
            _state.update { it.copy(error = ErrorEnvioReporte.SIN_SESION) }
            return
        }
        val descripcion = estado.descripcion.trim()
        val reporte = ReporteProblema(
            reservaId = reserva,
            clienteId = clienteId,
            profesionalId = profesional,
            motivo = motivo,
            descripcion = descripcion,
            // Marcadores: `ReporteProblemaRepository.crear` escribe ABIERTO y server timestamp.
            estado = EstadoReporte.ABIERTO,
            fecha = Instant.fromEpochMilliseconds(0),
        )
        _state.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            when (val resultado = reporteProblemaRepository.crear(reporte)) {
                is Result.Success -> _state.update {
                    it.copy(
                        enviando = false,
                        recienEnviado = true,
                        reporteExistente = ReportePropio(
                            motivo = motivo,
                            descripcion = descripcion,
                            estado = EstadoReporte.ABIERTO,
                            fechaTexto = null,
                        ),
                    )
                }
                is Result.Error -> _state.update {
                    it.copy(enviando = false, error = resultado.error.aErrorEnvio())
                }
            }
        }
    }
}

private fun Instant.aFechaTexto(): String {
    val local = toLocalDateTime(ZonaHorariaChile)
    return "%02d/%02d/%04d".format(local.dayOfMonth, local.monthNumber, local.year)
}

private fun ReporteProblemaError.aErrorEnvio(): ErrorEnvioReporte = when (this) {
    ReporteProblemaError.SIN_INTERNET -> ErrorEnvioReporte.SIN_INTERNET
    ReporteProblemaError.SIN_PERMISO -> ErrorEnvioReporte.SIN_PERMISO
    ReporteProblemaError.DESCONOCIDO -> ErrorEnvioReporte.DESCONOCIDO
}
