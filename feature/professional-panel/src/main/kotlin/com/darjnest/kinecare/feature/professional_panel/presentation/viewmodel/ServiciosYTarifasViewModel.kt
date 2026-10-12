package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.repository.ServicioRepository
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Resumen del catalogo mostrado en las 2 tarjetas superiores. */
data class ResumenCatalogoServicios(
    val serviciosActivos: Int,
    val tarifaPromedio: Long,
)

/**
 * Servicio ofrecido por el profesional con su tarifa, mostrado en la lista
 * de "Servicios y Tarifas". Modelo de presentacion reducido (no reemplaza
 * `Servicio` de dominio; se mapea desde los `Servicio` reales del
 * profesional, ver [aServicioProfesional]).
 */
data class ServicioProfesional(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val modalidades: List<ModalidadServicio>,
    val duracionMinutos: Int,
    val precio: Long,
    val reembolsableIsapreFonasa: Boolean,
    val notaInferior: String? = null,
    val activo: Boolean = true,
)

/** Filtro de la lista de servicios segun su modalidad de atencion. */
enum class FiltroModalidadServicio { TODOS, DOMICILIO, CONSULTA }

/** Campos de texto del formulario de servicio. */
enum class CampoServicio { NOMBRE, DESCRIPCION, DURACION, PRECIO }

/**
 * Borrador del servicio que el profesional crea (`servicioId == null`) o
 * edita. Duracion y precio se guardan como texto para no perder lo que el
 * usuario va escribiendo; los limites coinciden con la Security Rule
 * `servicioValido` de `profesionales/{id}/servicios`.
 */
data class FormularioServicio(
    val servicioId: String? = null,
    val nombre: String = "",
    val descripcion: String = "",
    val modalidad: ModalidadServicio = ModalidadServicio.DOMICILIO,
    val duracionTexto: String = "",
    val precioTexto: String = "",
    val guardando: Boolean = false,
    val error: String? = null,
) {
    val duracionMinutos: Int? get() = duracionTexto.trim().toIntOrNull()?.takeIf { it in DURACION_MIN..DURACION_MAX }
    val precio: Long? get() = precioTexto.trim().toLongOrNull()?.takeIf { it in PRECIO_MIN..PRECIO_MAX }

    val puedeGuardar: Boolean
        get() = !guardando && nombre.trim().length in 3..NOMBRE_MAX && descripcion.trim().length <= DESCRIPCION_MAX &&
            duracionMinutos != null && precio != null

    companion object {
        const val NOMBRE_MAX = 80
        const val DESCRIPCION_MAX = 300
        const val DURACION_MIN = 15
        const val DURACION_MAX = 480
        const val PRECIO_MIN = 1_000L
        const val PRECIO_MAX = 1_000_000L
    }
}

data class ServiciosYTarifasState(
    val cargando: Boolean = false,
    val region: String = "",
    val resumenCatalogo: ResumenCatalogoServicios? = null,
    val servicios: List<ServicioProfesional> = emptyList(),
    val filtroSeleccionado: FiltroModalidadServicio = FiltroModalidadServicio.TODOS,
    /** No nulo mientras el formulario de crear/editar servicio esta abierto. */
    val formulario: FormularioServicio? = null,
    /** Aviso corto (Snackbar) tras guardar. */
    val mensaje: String? = null,
)

sealed interface ServiciosYTarifasAction {
    data object VolverAtras : ServiciosYTarifasAction
    data class SeleccionarFiltro(val filtro: FiltroModalidadServicio) : ServiciosYTarifasAction
    data class CambiarActivoServicio(val servicioId: String, val activo: Boolean) : ServiciosYTarifasAction
    data object AgregarNuevoServicio : ServiciosYTarifasAction
    data class EditarServicio(val servicioId: String) : ServiciosYTarifasAction
    data class CambiarCampoServicio(val campo: CampoServicio, val valor: String) : ServiciosYTarifasAction
    data class CambiarModalidadServicio(val modalidad: ModalidadServicio) : ServiciosYTarifasAction
    data object GuardarServicio : ServiciosYTarifasAction
    data object CancelarServicio : ServiciosYTarifasAction
    data object MensajeMostrado : ServiciosYTarifasAction
}

/**
 * Mapea el `Servicio` real al modelo de la tarjeta. `Servicio` tiene una
 * sola `modalidad`, asi que `modalidades` queda con un elemento.
 * `reembolsableIsapreFonasa` y `notaInferior` quedan en `false`/`null`: el
 * dominio (docs/DOMAIN.md) no trae esos datos y no se inventan campos para
 * llenarlos.
 */
private fun Servicio.aServicioProfesional(): ServicioProfesional = ServicioProfesional(
    id = id,
    nombre = nombre,
    descripcion = descripcion,
    modalidades = listOf(modalidad),
    duracionMinutos = duracionMinutos,
    precio = precio,
    reembolsableIsapreFonasa = false,
    notaInferior = null,
    activo = activo,
)

/** Resumen de las tarjetas superiores; `null` (se oculta) si no hay servicios. */
private fun List<ServicioProfesional>.aResumenCatalogo(): ResumenCatalogoServicios? {
    if (isEmpty()) return null
    val activos = filter { it.activo }
    return ResumenCatalogoServicios(
        serviciosActivos = activos.size,
        tarifaPromedio = if (activos.isEmpty()) 0L else activos.sumOf { it.precio } / activos.size,
    )
}

@HiltViewModel
class ServiciosYTarifasViewModel @Inject constructor(
    private val servicioRepository: ServicioRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _state = MutableStateFlow(ServiciosYTarifasState())
    val state: StateFlow<ServiciosYTarifasState> = _state.asStateFlow()

    init {
        cargarServicios()
    }

    fun onAction(action: ServiciosYTarifasAction) {
        when (action) {
            is ServiciosYTarifasAction.SeleccionarFiltro ->
                _state.update { it.copy(filtroSeleccionado = action.filtro) }
            is ServiciosYTarifasAction.CambiarActivoServicio ->
                cambiarActivo(action.servicioId, action.activo)
            ServiciosYTarifasAction.AgregarNuevoServicio ->
                _state.update { it.copy(formulario = FormularioServicio()) }
            is ServiciosYTarifasAction.EditarServicio -> {
                val servicio = _state.value.servicios.firstOrNull { it.id == action.servicioId } ?: return
                _state.update {
                    it.copy(
                        formulario = FormularioServicio(
                            servicioId = servicio.id,
                            nombre = servicio.nombre,
                            descripcion = servicio.descripcion,
                            modalidad = servicio.modalidades.firstOrNull() ?: ModalidadServicio.CONSULTA,
                            duracionTexto = servicio.duracionMinutos.toString(),
                            precioTexto = servicio.precio.toString(),
                        ),
                    )
                }
            }
            is ServiciosYTarifasAction.CambiarCampoServicio -> actualizarFormulario { f ->
                when (action.campo) {
                    CampoServicio.NOMBRE -> f.copy(nombre = action.valor.take(FormularioServicio.NOMBRE_MAX))
                    CampoServicio.DESCRIPCION -> f.copy(descripcion = action.valor.take(FormularioServicio.DESCRIPCION_MAX))
                    CampoServicio.DURACION -> f.copy(duracionTexto = action.valor.filter(Char::isDigit).take(3))
                    CampoServicio.PRECIO -> f.copy(precioTexto = action.valor.filter(Char::isDigit).take(7))
                }
            }
            is ServiciosYTarifasAction.CambiarModalidadServicio -> actualizarFormulario { it.copy(modalidad = action.modalidad) }
            ServiciosYTarifasAction.GuardarServicio -> guardarServicio()
            ServiciosYTarifasAction.CancelarServicio -> _state.update { it.copy(formulario = null) }
            ServiciosYTarifasAction.MensajeMostrado -> _state.update { it.copy(mensaje = null) }
            // Volver atras lo resuelve el Root contra el NavGraph.
            ServiciosYTarifasAction.VolverAtras -> Unit
        }
    }

    private fun actualizarFormulario(transformar: (FormularioServicio) -> FormularioServicio) {
        _state.update { estado ->
            val formulario = estado.formulario ?: return@update estado
            estado.copy(formulario = transformar(formulario).copy(error = null))
        }
    }

    private fun guardarServicio() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val formulario = _state.value.formulario ?: return
        if (!formulario.puedeGuardar) return
        val activoActual = _state.value.servicios.firstOrNull { it.id == formulario.servicioId }?.activo ?: true
        val servicio = Servicio(
            id = formulario.servicioId.orEmpty(),
            nombre = formulario.nombre.trim(),
            descripcion = formulario.descripcion.trim(),
            modalidad = formulario.modalidad,
            duracionMinutos = requireNotNull(formulario.duracionMinutos),
            precio = requireNotNull(formulario.precio),
            activo = activoActual,
        )
        viewModelScope.launch {
            _state.update { it.copy(formulario = formulario.copy(guardando = true, error = null)) }
            when (val resultado = servicioRepository.guardar(uid, servicio)) {
                is Result.Success -> {
                    val guardado = resultado.data.aServicioProfesional()
                    _state.update { estado ->
                        val servicios = if (estado.servicios.any { it.id == guardado.id }) {
                            estado.servicios.map { if (it.id == guardado.id) guardado else it }
                        } else {
                            estado.servicios + guardado
                        }
                        estado.copy(
                            formulario = null,
                            servicios = servicios,
                            resumenCatalogo = servicios.aResumenCatalogo(),
                            mensaje = if (formulario.servicioId == null) "Servicio agregado." else "Servicio actualizado.",
                        )
                    }
                }
                is Result.Error -> _state.update {
                    it.copy(
                        formulario = formulario.copy(
                            guardando = false,
                            error = "No pudimos guardar el servicio. Intenta de nuevo.",
                        ),
                    )
                }
            }
        }
    }

    private fun cargarServicios() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.update { it.copy(cargando = true) }
            when (val resultado = servicioRepository.obtenerPorProfesional(uid)) {
                is Result.Success -> {
                    val servicios = resultado.data.map { it.aServicioProfesional() }
                    _state.update {
                        it.copy(cargando = false, servicios = servicios, resumenCatalogo = servicios.aResumenCatalogo())
                    }
                }
                is Result.Error -> _state.update { it.copy(cargando = false) }
            }
        }
    }

    /** UI optimista: se aplica el cambio ya y se revierte si Firestore lo rechaza. */
    private fun cambiarActivo(servicioId: String, activo: Boolean) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val anterior = _state.value.servicios.firstOrNull { it.id == servicioId }?.activo ?: return
        aplicarActivo(servicioId, activo)
        viewModelScope.launch {
            if (servicioRepository.actualizarActivo(uid, servicioId, activo) is Result.Error) {
                aplicarActivo(servicioId, anterior)
            }
        }
    }

    private fun aplicarActivo(servicioId: String, activo: Boolean) {
        _state.update { estado ->
            val servicios = estado.servicios.map { if (it.id == servicioId) it.copy(activo = activo) else it }
            estado.copy(servicios = servicios, resumenCatalogo = servicios.aResumenCatalogo())
        }
    }
}
