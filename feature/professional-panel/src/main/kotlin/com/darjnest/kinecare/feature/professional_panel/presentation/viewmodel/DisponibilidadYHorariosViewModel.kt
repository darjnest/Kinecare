package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.domain.model.Disponibilidad
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
    val guardando: Boolean = false,
    /** Aviso puntual para un Snackbar; la vista lo consume con [DisponibilidadYHorariosAction.MensajeMostrado]. */
    val mensaje: String? = null,
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
    data object MensajeMostrado : DisponibilidadYHorariosAction
}

private val DIAS_SEMANA = listOf(
    DayOfWeek.MONDAY to ("Lun" to "Lunes"),
    DayOfWeek.TUESDAY to ("Mar" to "Martes"),
    DayOfWeek.WEDNESDAY to ("Mié" to "Miércoles"),
    DayOfWeek.THURSDAY to ("Jue" to "Jueves"),
    DayOfWeek.FRIDAY to ("Vie" to "Viernes"),
    DayOfWeek.SATURDAY to ("Sáb" to "Sábado"),
    DayOfWeek.SUNDAY to ("Dom" to "Domingo"),
)

/** Dia del mes que le corresponde a [dia] dentro de la semana (lunes a domingo) de [hoy]. */
internal fun numeroDiaDeLaSemana(hoy: LocalDate, dia: DayOfWeek): Int {
    val lunes = hoy.plus(-hoy.dayOfWeek.ordinal, DateTimeUnit.DAY)
    return lunes.plus(dia.ordinal, DateTimeUnit.DAY).day
}

private fun LocalTime.aTexto(): String = "%02d:%02d".format(hour, minute)

@OptIn(ExperimentalTime::class)
@HiltViewModel
class DisponibilidadYHorariosViewModel @Inject constructor(
    private val profesionalRepository: ProfesionalRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _state = MutableStateFlow(DisponibilidadYHorariosState())
    val state: StateFlow<DisponibilidadYHorariosState> = _state.asStateFlow()

    /**
     * Fuente de verdad del horario semanal (lo unico de esta pantalla que
     * existe en Firestore, `profesionales/{uid}.disponibilidad`); `diasLaborales`
     * y `turnos` del estado se derivan de aca. Solo se puede guardar si la
     * carga fue exitosa: guardar sobre una lista vacia por un fallo de red
     * borraria el horario real del profesional.
     */
    private var horarios: List<Disponibilidad> = emptyList()
    private var horariosCargados = false
    private var diaSeleccionado: DayOfWeek = DayOfWeek.MONDAY

    init {
        cargarDisponibilidad()
    }

    fun onAction(action: DisponibilidadYHorariosAction) {
        when (action) {
            is DisponibilidadYHorariosAction.CambiarReservasInmediatas ->
                _state.update { it.copy(reservasInmediatasActivas = action.activo) }

            is DisponibilidadYHorariosAction.SeleccionarDia -> {
                diaSeleccionado = DayOfWeek.valueOf(action.diaId)
                refrescarHorarios()
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

            DisponibilidadYHorariosAction.Replicar -> replicarDiaSeleccionado()
            DisponibilidadYHorariosAction.GuardarDisponibilidad -> guardar()
            DisponibilidadYHorariosAction.MensajeMostrado -> _state.update { it.copy(mensaje = null) }

            // Editar un turno requiere un selector de hora que los mockups
            // no definen todavia (docs/TASKS.md, Fase 7).
            is DisponibilidadYHorariosAction.EditarTurno -> Unit
        }
    }

    private fun cargarDisponibilidad() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.update { it.copy(cargando = true) }
            when (val resultado = profesionalRepository.obtenerPorId(uid)) {
                is Result.Success -> {
                    horarios = resultado.data.disponibilidad
                    horariosCargados = true
                    diaSeleccionado = DIAS_SEMANA.map { it.first }.firstOrNull { dia ->
                        horarios.any { it.diaSemana == dia && it.activo }
                    } ?: DayOfWeek.MONDAY
                    _state.update { it.copy(cargando = false) }
                    refrescarHorarios()
                }
                is Result.Error -> _state.update {
                    it.copy(cargando = false, mensaje = "No pudimos cargar tu disponibilidad")
                }
            }
        }
    }

    /** Copia los turnos del dia seleccionado a los demas dias ya habilitados, reemplazando los suyos. */
    private fun replicarDiaSeleccionado() {
        val origen = horarios.filter { it.diaSemana == diaSeleccionado }
        if (origen.isEmpty()) return
        val destinos = DIAS_SEMANA.map { it.first }
            .filter { dia -> dia != diaSeleccionado && horarios.any { it.diaSemana == dia && it.activo } }
        if (destinos.isEmpty()) return
        horarios = horarios.filter { it.diaSemana !in destinos } +
            destinos.flatMap { destino -> origen.map { it.copy(diaSemana = destino) } }
        refrescarHorarios()
    }

    private fun guardar() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (!horariosCargados || _state.value.guardando) return
        viewModelScope.launch {
            _state.update { it.copy(guardando = true) }
            val mensaje = when (profesionalRepository.actualizarDisponibilidad(uid, horarios)) {
                is Result.Success -> "Horarios guardados"
                is Result.Error -> "No pudimos guardar tus horarios"
            }
            _state.update { it.copy(guardando = false, mensaje = mensaje) }
        }
    }

    private fun refrescarHorarios() {
        val hoy = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val dias = DIAS_SEMANA.map { (dia, nombres) ->
            DiaLaboral(
                id = dia.name,
                nombreCorto = nombres.first,
                nombreCompleto = nombres.second,
                numeroDia = numeroDiaDeLaSemana(hoy, dia),
                habilitado = horarios.any { it.diaSemana == dia && it.activo },
                seleccionado = dia == diaSeleccionado,
            )
        }
        val turnos = horarios
            .filter { it.diaSemana == diaSeleccionado }
            .sortedBy { it.horaInicio }
            .mapIndexed { indice, horario ->
                val matutino = horario.horaInicio.hour < 12
                TurnoHorario(
                    id = "${diaSeleccionado.name}-$indice",
                    titulo = if (matutino) "Turno mañana" else "Turno tarde",
                    tono = if (matutino) TonoTurno.MATUTINO else TonoTurno.VESPERTINO,
                    // Cupos y duracion por cupo no existen en Firestore; la
                    // vista oculta esa linea cuando `cupos` es 0.
                    cupos = 0,
                    duracionMinutos = 0,
                    horaInicio = horario.horaInicio.aTexto(),
                    horaFin = horario.horaFin.aTexto(),
                    activo = horario.activo,
                )
            }
        _state.update { it.copy(diasLaborales = dias, turnos = turnos) }
    }
}
