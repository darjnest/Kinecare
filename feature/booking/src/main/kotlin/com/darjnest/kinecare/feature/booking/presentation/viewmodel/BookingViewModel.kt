@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.booking.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.error.CrearReservaError
import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.data.repository.ClienteRepository
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.data.repository.ReservaRepository
import com.darjnest.kinecare.core.common.domain.model.Direccion
import com.darjnest.kinecare.core.common.domain.model.Disponibilidad
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.domain.model.SolicitudReserva
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.common.util.ZonaHorariaChile
import com.darjnest.kinecare.feature.booking.domain.service.DiaConHorarios
import com.darjnest.kinecare.feature.booking.domain.service.generarDiasConHorarios
import com.darjnest.kinecare.feature.booking.presentation.navigation.ARG_PROFESIONAL_ID
import com.darjnest.kinecare.feature.booking.presentation.navigation.ARG_SERVICIO_ID
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import kotlin.time.Clock

/** Largo maximo de las indicaciones de la direccion (la Cloud Function aplica el mismo tope). */
const val LARGO_MAXIMO_INDICACIONES = 300

/** Largo maximo de calle/numero/comuna/ciudad (la Cloud Function aplica el mismo tope). */
const val LARGO_MAXIMO_CAMPO_DIRECCION = 120

/**
 * Pasos del flujo, en orden. [DIRECCION] solo aplica a servicios
 * `DOMICILIO`: para el resto se salta (ver [BookingState.pasos]).
 */
enum class PasoReserva { MODALIDAD, FECHA_HORA, DIRECCION, REVISION }

enum class CampoDireccion { CALLE, NUMERO, COMUNA, CIUDAD, INDICACIONES }

data class FormularioDireccion(
    val calle: String = "",
    val numero: String = "",
    val comuna: String = "",
    val ciudad: String = "",
    val indicaciones: String = "",
    /** Solo se conservan si la direccion viene de una guardada sin editar. */
    val lat: Double? = null,
    val lng: Double? = null,
) {
    /** Mismos campos obligatorios que exige `crearReserva` (`DIRECCION_REQUERIDA`). */
    val esValido: Boolean
        get() = calle.isNotBlank() && numero.isNotBlank() && comuna.isNotBlank()

    fun aDireccion(): Direccion = Direccion(
        calle = calle.trim(),
        numero = numero.trim(),
        comuna = comuna.trim(),
        ciudad = ciudad.trim(),
        lat = lat,
        lng = lng,
        indicaciones = indicaciones.trim().takeIf { it.isNotEmpty() },
    )
}

data class BookingState(
    val cargando: Boolean = true,
    val errorCarga: ProfesionalError? = null,
    val profesionalNombre: String = "",
    /** Solo servicios con `activo == true`: un servicio pausado no se puede reservar. */
    val servicios: List<Servicio> = emptyList(),
    val paso: PasoReserva = PasoReserva.MODALIDAD,
    val modalidadSeleccionada: ModalidadServicio? = null,
    val servicioSeleccionado: Servicio? = null,
    val dias: List<DiaConHorarios> = emptyList(),
    val fechaSeleccionada: LocalDate? = null,
    val horarioSeleccionado: Instant? = null,
    val direccionesGuardadas: List<Direccion> = emptyList(),
    val direccion: FormularioDireccion = FormularioDireccion(),
    val enviando: Boolean = false,
    val errorEnvio: CrearReservaError? = null,
    /** No nulo cuando `crearReserva` respondio OK: la vista muestra la confirmacion. */
    val reservaCreadaId: String? = null,
) {
    /** Modalidades con al menos un servicio activo, en el orden del enum. */
    val modalidades: List<ModalidadServicio>
        get() = servicios.map { it.modalidad }.distinct().sortedBy { it.ordinal }

    val serviciosDeModalidad: List<Servicio>
        get() = servicios.filter { it.modalidad == modalidadSeleccionada }

    val requiereDireccion: Boolean
        get() = servicioSeleccionado?.modalidad == ModalidadServicio.DOMICILIO

    val pasos: List<PasoReserva>
        get() = if (requiereDireccion) PasoReserva.entries else PasoReserva.entries - PasoReserva.DIRECCION

    /** 1-based, para el indicador "Paso 2 de 4". */
    val numeroPaso: Int
        get() = pasos.indexOf(paso) + 1

    val horariosDelDia: List<Instant>
        get() = dias.firstOrNull { it.fecha == fechaSeleccionada }?.horarios.orEmpty()

    val puedeContinuar: Boolean
        get() = when (paso) {
            PasoReserva.MODALIDAD -> servicioSeleccionado != null
            PasoReserva.FECHA_HORA -> horarioSeleccionado != null
            PasoReserva.DIRECCION -> direccion.esValido
            PasoReserva.REVISION -> !enviando && reservaCreadaId == null
        }
}

sealed interface BookingAction {
    data class SeleccionarModalidad(val modalidad: ModalidadServicio) : BookingAction
    data class SeleccionarServicio(val servicioId: String) : BookingAction
    data class SeleccionarFecha(val fecha: LocalDate) : BookingAction
    data class SeleccionarHorario(val horario: Instant) : BookingAction
    data class UsarDireccionGuardada(val direccion: Direccion) : BookingAction
    data class CambiarCampoDireccion(val campo: CampoDireccion, val valor: String) : BookingAction

    /** Avanza al paso siguiente si el actual esta completo. */
    data object Continuar : BookingAction

    /** Vuelve al paso anterior; en el primero no hace nada (la vista emite [VolverAtras]). */
    data object PasoAnterior : BookingAction

    /** Envia la reserva a `crearReserva` (solo en [PasoReserva.REVISION]). */
    data object Confirmar : BookingAction
    data object Reintentar : BookingAction

    /** Navegacion: las resuelve el `Root` contra el NavGraph, no el ViewModel. */
    data object VolverAtras : BookingAction
    data object IrAMisCitas : BookingAction
    data object Finalizar : BookingAction
}

@HiltViewModel
class BookingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profesionalRepository: ProfesionalRepository,
    private val clienteRepository: ClienteRepository,
    private val reservaRepository: ReservaRepository,
    private val firebaseAuth: FirebaseAuth,
    private val clock: Clock,
) : ViewModel() {

    private val profesionalId: String? = savedStateHandle[ARG_PROFESIONAL_ID]
    private val servicioPreseleccionadoId: String? = savedStateHandle[ARG_SERVICIO_ID]

    /** Horario semanal del profesional; no va en el estado porque la vista solo usa los cupos derivados. */
    private var disponibilidad: List<Disponibilidad> = emptyList()

    private val _state = MutableStateFlow(BookingState())
    val state: StateFlow<BookingState> = _state.asStateFlow()

    init {
        cargar()
        cargarDireccionesGuardadas()
    }

    fun onAction(action: BookingAction) {
        when (action) {
            is BookingAction.SeleccionarModalidad -> seleccionarModalidad(action.modalidad)
            is BookingAction.SeleccionarServicio -> seleccionarServicio(action.servicioId)
            is BookingAction.SeleccionarFecha -> _state.update {
                if (it.dias.none { dia -> dia.fecha == action.fecha }) return
                it.copy(fechaSeleccionada = action.fecha, horarioSeleccionado = null, errorEnvio = null)
            }
            is BookingAction.SeleccionarHorario -> _state.update {
                if (action.horario !in it.horariosDelDia) return
                it.copy(horarioSeleccionado = action.horario, errorEnvio = null)
            }
            is BookingAction.UsarDireccionGuardada -> _state.update {
                it.copy(direccion = action.direccion.aFormulario(), errorEnvio = null)
            }
            is BookingAction.CambiarCampoDireccion -> _state.update {
                it.copy(direccion = it.direccion.con(action.campo, action.valor), errorEnvio = null)
            }
            BookingAction.Continuar -> continuar()
            BookingAction.PasoAnterior -> _state.update {
                val indice = it.pasos.indexOf(it.paso)
                if (indice <= 0 || it.enviando) return
                it.copy(paso = it.pasos[indice - 1], errorEnvio = null)
            }
            BookingAction.Confirmar -> confirmar()
            BookingAction.Reintentar -> cargar()
            BookingAction.VolverAtras,
            BookingAction.IrAMisCitas,
            BookingAction.Finalizar,
            -> Unit
        }
    }

    private fun cargar() {
        val id = profesionalId
        if (id.isNullOrBlank()) {
            _state.update { it.copy(cargando = false, errorCarga = ProfesionalError.NO_ENCONTRADO) }
            return
        }
        _state.update { it.copy(cargando = true, errorCarga = null) }
        viewModelScope.launch {
            when (val resultado = profesionalRepository.obtenerPorId(id)) {
                is Result.Success -> {
                    val profesional = resultado.data
                    disponibilidad = profesional.disponibilidad
                    val activos = profesional.servicios.filter { it.activo }
                    _state.update {
                        it.copy(
                            cargando = false,
                            profesionalNombre = profesional.usuario.nombre,
                            servicios = activos,
                        )
                    }
                    val preseleccionado = activos.firstOrNull { it.id == servicioPreseleccionadoId }
                    when {
                        preseleccionado != null -> seleccionarServicio(preseleccionado.id)
                        _state.value.modalidades.size == 1 -> seleccionarModalidad(_state.value.modalidades.single())
                    }
                }
                is Result.Error -> _state.update { it.copy(cargando = false, errorCarga = resultado.error) }
            }
        }
    }

    /** Si falla (o no hay sesion) el formulario de direccion queda vacio: no bloquea la reserva. */
    private fun cargarDireccionesGuardadas() {
        val clienteId = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            val resultado = clienteRepository.obtenerPorId(clienteId)
            if (resultado is Result.Success) {
                _state.update { it.copy(direccionesGuardadas = resultado.data.direcciones) }
            }
        }
    }

    private fun seleccionarModalidad(modalidad: ModalidadServicio) {
        _state.update {
            if (modalidad !in it.modalidades) return
            val servicios = it.servicios.filter { s -> s.modalidad == modalidad }
            val servicio = it.servicioSeleccionado?.takeIf { s -> s.modalidad == modalidad }
                ?: servicios.singleOrNull()
            it.conServicio(servicio).copy(modalidadSeleccionada = modalidad)
        }
    }

    private fun seleccionarServicio(servicioId: String) {
        _state.update {
            val servicio = it.servicios.firstOrNull { s -> s.id == servicioId } ?: return
            it.conServicio(servicio).copy(modalidadSeleccionada = servicio.modalidad)
        }
    }

    /** Cambiar de servicio cambia la duracion de los cupos: fecha y hora se vuelven a elegir. */
    private fun BookingState.conServicio(servicio: Servicio?): BookingState {
        if (servicio?.id == servicioSeleccionado?.id) return this
        return copy(
            servicioSeleccionado = servicio,
            dias = servicio?.let { diasPara(it) }.orEmpty(),
            fechaSeleccionada = null,
            horarioSeleccionado = null,
            errorEnvio = null,
        )
    }

    private fun diasPara(servicio: Servicio): List<DiaConHorarios> = generarDiasConHorarios(
        disponibilidad = disponibilidad,
        duracionMinutos = servicio.duracionMinutos,
        ahora = clock.now(),
        zona = ZonaHorariaChile,
    )

    private fun continuar() {
        _state.update {
            if (!it.puedeContinuar || it.paso == PasoReserva.REVISION) return
            val siguiente = it.pasos[it.pasos.indexOf(it.paso) + 1]
            if (siguiente == PasoReserva.FECHA_HORA) {
                // Recalcula con la hora actual: los cupos que ya quedaron dentro
                // de la anticipacion minima desaparecen. Se conserva la eleccion
                // si sigue siendo valida.
                val dias = it.servicioSeleccionado?.let { s -> diasPara(s) }.orEmpty()
                val fecha = it.fechaSeleccionada?.takeIf { f -> dias.any { d -> d.fecha == f } }
                    ?: dias.firstOrNull()?.fecha
                val horario = it.horarioSeleccionado
                    ?.takeIf { h -> dias.firstOrNull { d -> d.fecha == fecha }?.horarios.orEmpty().contains(h) }
                it.copy(paso = siguiente, dias = dias, fechaSeleccionada = fecha, horarioSeleccionado = horario)
            } else {
                it.copy(paso = siguiente)
            }
        }
    }

    private fun confirmar() {
        val estado = _state.value
        if (estado.paso != PasoReserva.REVISION || !estado.puedeContinuar) return
        val profesional = profesionalId
        val servicio = estado.servicioSeleccionado
        val horario = estado.horarioSeleccionado
        if (profesional.isNullOrBlank() || servicio == null || horario == null) return

        val solicitud = SolicitudReserva(
            profesionalId = profesional,
            servicioId = servicio.id,
            fechaHora = horario,
            direccion = estado.direccion.aDireccion().takeIf { estado.requiereDireccion },
        )
        _state.update { it.copy(enviando = true, errorEnvio = null) }
        viewModelScope.launch {
            when (val resultado = reservaRepository.crear(solicitud)) {
                is Result.Success -> _state.update { it.copy(enviando = false, reservaCreadaId = resultado.data) }
                is Result.Error -> _state.update { it.conErrorDeEnvio(resultado.error, horario) }
            }
        }
    }

    /**
     * Lleva al paso donde el cliente puede corregir el problema. Un horario
     * rechazado por el backend se quita de la lista para no volver a
     * ofrecerlo.
     */
    private fun BookingState.conErrorDeEnvio(error: CrearReservaError, horario: Instant): BookingState {
        val base = copy(enviando = false, errorEnvio = error)
        return when (error) {
            CrearReservaError.HORARIO_OCUPADO,
            CrearReservaError.FUERA_DE_HORARIO,
            CrearReservaError.ANTICIPACION_INSUFICIENTE,
            -> {
                val restantes = dias.map { it.copy(horarios = it.horarios - horario) }.filter { it.horarios.isNotEmpty() }
                base.copy(
                    paso = PasoReserva.FECHA_HORA,
                    dias = restantes,
                    fechaSeleccionada = fechaSeleccionada?.takeIf { f -> restantes.any { it.fecha == f } }
                        ?: restantes.firstOrNull()?.fecha,
                    horarioSeleccionado = null,
                )
            }
            CrearReservaError.DIRECCION_REQUERIDA -> base.copy(paso = PasoReserva.DIRECCION)
            else -> base
        }
    }
}

private fun Direccion.aFormulario() = FormularioDireccion(
    calle = calle,
    numero = numero,
    comuna = comuna,
    ciudad = ciudad,
    indicaciones = indicaciones.orEmpty(),
    lat = lat,
    lng = lng,
)

/** Editar calle/numero/comuna/ciudad descarta las coordenadas de la direccion guardada (ya no corresponden). */
private fun FormularioDireccion.con(campo: CampoDireccion, valor: String): FormularioDireccion = when (campo) {
    CampoDireccion.CALLE -> copy(calle = valor.take(LARGO_MAXIMO_CAMPO_DIRECCION), lat = null, lng = null)
    CampoDireccion.NUMERO -> copy(numero = valor.take(LARGO_MAXIMO_CAMPO_DIRECCION), lat = null, lng = null)
    CampoDireccion.COMUNA -> copy(comuna = valor.take(LARGO_MAXIMO_CAMPO_DIRECCION), lat = null, lng = null)
    CampoDireccion.CIUDAD -> copy(ciudad = valor.take(LARGO_MAXIMO_CAMPO_DIRECCION), lat = null, lng = null)
    CampoDireccion.INDICACIONES -> copy(indicaciones = valor.take(LARGO_MAXIMO_INDICACIONES))
}
