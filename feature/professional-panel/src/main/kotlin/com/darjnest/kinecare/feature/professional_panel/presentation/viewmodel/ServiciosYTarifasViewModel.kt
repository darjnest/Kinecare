package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Resumen del catalogo mostrado en las 2 tarjetas superiores. */
data class ResumenCatalogoServicios(
    val serviciosActivos: Int,
    val tarifaPromedio: Long,
)

/**
 * Servicio ofrecido por el profesional con su tarifa, mostrado en la lista
 * de "Servicios y Tarifas". Modelo de presentacion reducido (no reemplaza
 * `Servicio` de dominio; cuando esta pantalla se conecte a Firestore, se
 * mapea desde los `Servicio` reales del `Profesional`).
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

@HiltViewModel
class ServiciosYTarifasViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(ServiciosYTarifasState())
    val state: StateFlow<ServiciosYTarifasState> = _state.asStateFlow()

    fun onAction(action: ServiciosYTarifasAction) {
        when (action) {
            is ServiciosYTarifasAction.SeleccionarFiltro ->
                _state.update { it.copy(filtroSeleccionado = action.filtro) }
            is ServiciosYTarifasAction.CambiarActivoServicio ->
                _state.update { estado ->
                    estado.copy(
                        servicios = estado.servicios.map { servicio ->
                            if (servicio.id == action.servicioId) servicio.copy(activo = action.activo) else servicio
                        },
                    )
                }
            // Volver atras, agregar un nuevo servicio y editar un servicio
            // existente: requieren navegacion y escritura sobre el catalogo
            // real del Profesional, sin backend de servicios conectado
            // todavia — se conectan cuando la feature salga de esta fase
            // (docs/TASKS.md, Fase 7).
            ServiciosYTarifasAction.VolverAtras,
            ServiciosYTarifasAction.AgregarNuevoServicio,
            is ServiciosYTarifasAction.EditarServicio,
            -> Unit
        }
    }
}
