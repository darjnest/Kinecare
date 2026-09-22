package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Momento del turno de atencion, define icono y color en [TarjetaTurno]. */
enum class TonoTurno { MATUTINO, VESPERTINO }

/**
 * Dia de la semana seleccionable en "Dias Laborales", con su fecha
 * calendario asociada (numero de dia del mes que corresponde a esa semana).
 */
data class DiaLaboral(
    val id: String,
    val nombreCorto: String,
    val nombreCompleto: String,
    val numeroDia: Int,
    val habilitado: Boolean,
    val seleccionado: Boolean,
)

/** Turno de atencion (manana/tarde) configurado para el dia seleccionado. */
data class TurnoHorario(
    val id: String,
    val titulo: String,
    val tono: TonoTurno,
    val cupos: Int,
    val duracionMinutos: Int,
    val horaInicio: String,
    val horaFin: String,
    val activo: Boolean,
)

/** Comuna de la Region Metropolitana habilitada (o no) para atencion a domicilio. */
data class ComunaCobertura(
    val nombre: String,
    val habilitada: Boolean,
)

data class DisponibilidadYHorariosState(
    val cargando: Boolean = false,
    val reservasInmediatasActivas: Boolean = false,
    val diasLaborales: List<DiaLaboral> = emptyList(),
    val turnos: List<TurnoHorario> = emptyList(),
    val minutosTrasladoSeleccionado: Int = 0,
    val regionTexto: String = "",
    val direccionBase: String = "",
    val radioKm: Int = 0,
    val comunasHabilitadas: List<ComunaCobertura> = emptyList(),
    val recargoZonaLejanaActivo: Boolean = false,
    val tarifaRecargoClp: Long = 0,
)

sealed interface DisponibilidadYHorariosAction {
    data class CambiarReservasInmediatas(val activo: Boolean) : DisponibilidadYHorariosAction
    data class SeleccionarDia(val diaId: String) : DisponibilidadYHorariosAction
    data class EditarTurno(val turnoId: String) : DisponibilidadYHorariosAction
    data object Replicar : DisponibilidadYHorariosAction
    data class SeleccionarMinutosTraslado(val minutos: Int) : DisponibilidadYHorariosAction
    data class CambiarRadioKm(val km: Int) : DisponibilidadYHorariosAction
    data class ToggleComuna(val nombre: String) : DisponibilidadYHorariosAction
    data class CambiarRecargoZonaLejana(val activo: Boolean) : DisponibilidadYHorariosAction
    data object GuardarDisponibilidad : DisponibilidadYHorariosAction
}

@HiltViewModel
class DisponibilidadYHorariosViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(DisponibilidadYHorariosState())
    val state: StateFlow<DisponibilidadYHorariosState> = _state.asStateFlow()

    fun onAction(action: DisponibilidadYHorariosAction) {
        when (action) {
            is DisponibilidadYHorariosAction.CambiarReservasInmediatas ->
                _state.update { it.copy(reservasInmediatasActivas = action.activo) }

            is DisponibilidadYHorariosAction.SeleccionarDia ->
                _state.update { estado ->
                    estado.copy(
                        diasLaborales = estado.diasLaborales.map { dia ->
                            dia.copy(seleccionado = dia.id == action.diaId)
                        },
                    )
                }

            is DisponibilidadYHorariosAction.SeleccionarMinutosTraslado ->
                _state.update { it.copy(minutosTrasladoSeleccionado = action.minutos) }

            is DisponibilidadYHorariosAction.CambiarRadioKm ->
                _state.update { it.copy(radioKm = action.km) }

            is DisponibilidadYHorariosAction.ToggleComuna ->
                _state.update { estado ->
                    estado.copy(
                        comunasHabilitadas = estado.comunasHabilitadas.map { comuna ->
                            if (comuna.nombre == action.nombre) {
                                comuna.copy(habilitada = !comuna.habilitada)
                            } else {
                                comuna
                            }
                        },
                    )
                }

            is DisponibilidadYHorariosAction.CambiarRecargoZonaLejana ->
                _state.update { it.copy(recargoZonaLejanaActivo = action.activo) }

            // Editar un turno, replicar el horario a otros dias y guardar la
            // disponibilidad requieren el repositorio de disponibilidad del
            // profesional en Firestore, que todavia no esta conectado a esta
            // pantalla (docs/TASKS.md, Fase 7).
            is DisponibilidadYHorariosAction.EditarTurno,
            DisponibilidadYHorariosAction.Replicar,
            DisponibilidadYHorariosAction.GuardarDisponibilidad,
            -> Unit
        }
    }
}
