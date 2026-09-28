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

data class ServiciosYTarifasState(
    val cargando: Boolean = false,
    val region: String = "",
    val resumenCatalogo: ResumenCatalogoServicios? = null,
    val servicios: List<ServicioProfesional> = emptyList(),
    val filtroSeleccionado: FiltroModalidadServicio = FiltroModalidadServicio.TODOS,
)

sealed interface ServiciosYTarifasAction {
    data object VolverAtras : ServiciosYTarifasAction
    data class SeleccionarFiltro(val filtro: FiltroModalidadServicio) : ServiciosYTarifasAction
    data class CambiarActivoServicio(val servicioId: String, val activo: Boolean) : ServiciosYTarifasAction
    data object AgregarNuevoServicio : ServiciosYTarifasAction
    data class EditarServicio(val servicioId: String) : ServiciosYTarifasAction
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
            // Volver atras lo resuelve el Root contra el NavGraph. Agregar y
            // editar un servicio necesitan un formulario que los mockups no
            // definen todavia — se conectan cuando exista esa pantalla
            // (docs/TASKS.md, Fase 7).
            ServiciosYTarifasAction.VolverAtras,
            ServiciosYTarifasAction.AgregarNuevoServicio,
            is ServiciosYTarifasAction.EditarServicio,
            -> Unit
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
