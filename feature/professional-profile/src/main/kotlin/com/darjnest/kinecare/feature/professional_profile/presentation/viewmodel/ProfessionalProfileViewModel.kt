@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.professional_profile.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.domain.model.TipoInsignia
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.professional_profile.presentation.navigation.ARG_PROFESIONAL_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import javax.inject.Inject

/** Fila de insignia del perfil: siempre hay una por cada [TipoInsignia]. */
data class InsigniaPerfil(
    val tipo: TipoInsignia,
    val estado: EstadoVerificacion,
    val detalle: String?,
    /** Epoch millis de `Insignia.fechaActualizacion`; nulo si el profesional nunca la solicito. */
    val fechaActualizacionMillis: Long?,
)

data class TurnoHorario(val inicio: LocalTime, val fin: LocalTime)

data class HorarioDia(val dia: DayOfWeek, val turnos: List<TurnoHorario>)

/** Modelo de presentacion del perfil publico (reducido a lo que la pantalla muestra). */
data class PerfilProfesional(
    val id: String,
    val nombre: String,
    val especialidades: List<String>,
    val rnpi: String,
    /** Nulo cuando no hay resenas: la pantalla oculta la fila de calificacion. */
    val calificacionPromedio: Double?,
    val totalResenas: Int,
    val descripcion: String,
    val insignias: List<InsigniaPerfil>,
    /** Solo servicios con `activo == true`. */
    val servicios: List<Servicio>,
    /** Solo dias con al menos un turno activo, ordenados lunes a domingo. */
    val horario: List<HorarioDia>,
) {
    /** Hasta dos iniciales del nombre, para el avatar (no hay fotos: Storage esta bloqueado). */
    val iniciales: String
        get() = nombre.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
}

data class ProfessionalProfileState(
    val cargando: Boolean = true,
    val error: ProfesionalError? = null,
    val perfil: PerfilProfesional? = null,
    val insigniasExpandidas: Set<TipoInsignia> = emptySet(),
)

sealed interface ProfessionalProfileAction {
    data object Reintentar : ProfessionalProfileAction
    data class AlternarInsignia(val tipo: TipoInsignia) : ProfessionalProfileAction

    /** Navegacion: la resuelve el `Root` contra el NavGraph, no el ViewModel. */
    data object VolverAtras : ProfessionalProfileAction
}

@HiltViewModel
class ProfessionalProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profesionalRepository: ProfesionalRepository,
) : ViewModel() {

    private val profesionalId: String? = savedStateHandle[ARG_PROFESIONAL_ID]

    private val _state = MutableStateFlow(ProfessionalProfileState())
    val state: StateFlow<ProfessionalProfileState> = _state.asStateFlow()

    init {
        cargar()
    }

    fun onAction(action: ProfessionalProfileAction) {
        when (action) {
            ProfessionalProfileAction.Reintentar -> cargar()
            is ProfessionalProfileAction.AlternarInsignia -> _state.update {
                val expandidas = if (action.tipo in it.insigniasExpandidas) {
                    it.insigniasExpandidas - action.tipo
                } else {
                    it.insigniasExpandidas + action.tipo
                }
                it.copy(insigniasExpandidas = expandidas)
            }
            ProfessionalProfileAction.VolverAtras -> Unit
        }
    }

    private fun cargar() {
        val id = profesionalId
        if (id.isNullOrBlank()) {
            _state.update { it.copy(cargando = false, error = ProfesionalError.NO_ENCONTRADO) }
            return
        }
        _state.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val resultado = profesionalRepository.obtenerPorId(id)) {
                is Result.Success -> _state.update {
                    it.copy(cargando = false, error = null, perfil = resultado.data.aPerfil())
                }
                is Result.Error -> _state.update {
                    it.copy(cargando = false, error = resultado.error, perfil = null)
                }
            }
        }
    }
}

/**
 * Mapea el `Profesional` real al perfil publico. Decisiones: los servicios
 * pausados (`activo == false`) no se exponen (el cliente no debe ver lo que
 * no puede contratar); cada [TipoInsignia] ausente se muestra como
 * `NO_SOLICITADO` en vez de omitirse (docs/DOMAIN.md, regla 3: lo no
 * verificado es informacion de confianza visible); y solo los turnos de
 * `Disponibilidad.activo` cuentan como horario de atencion.
 */
internal fun Profesional.aPerfil(): PerfilProfesional {
    val insigniasPorTipo = insignias.associateBy { it.tipo }
    return PerfilProfesional(
        id = usuario.id,
        nombre = usuario.nombre,
        especialidades = especialidades,
        rnpi = rnpi,
        calificacionPromedio = calificacionPromedio.takeIf { totalResenas > 0 },
        totalResenas = totalResenas,
        descripcion = descripcion.trim(),
        insignias = TipoInsignia.entries.map { tipo ->
            val insignia = insigniasPorTipo[tipo]
            InsigniaPerfil(
                tipo = tipo,
                estado = insignia?.estado ?: EstadoVerificacion.NO_SOLICITADO,
                detalle = insignia?.detalle?.takeIf { it.isNotBlank() },
                fechaActualizacionMillis = insignia?.fechaActualizacion?.toEpochMilliseconds(),
            )
        },
        servicios = servicios.filter { it.activo },
        horario = disponibilidad
            .filter { it.activo }
            .groupBy { it.diaSemana }
            .toSortedMap()
            .map { (dia, turnos) ->
                HorarioDia(
                    dia = dia,
                    turnos = turnos.sortedBy { it.horaInicio }.map { TurnoHorario(it.horaInicio, it.horaFin) },
                )
            },
    )
}
