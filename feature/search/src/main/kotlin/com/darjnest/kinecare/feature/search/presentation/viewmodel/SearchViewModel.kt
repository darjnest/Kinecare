package com.darjnest.kinecare.feature.search.presentation.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Waves
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.designsystem.theme.LoginAzulSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginMenta
import com.darjnest.kinecare.core.designsystem.theme.LoginMentaSuave
import com.darjnest.kinecare.feature.search.data.repository.ProfesionalRepository
import com.darjnest.kinecare.feature.search.data.repository.UbicacionRepository
import com.darjnest.kinecare.feature.search.domain.UbicacionError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Tipo de atencion que el Cliente busca (selector segmentado de la tarjeta de filtros). */
enum class TipoAtencion { KINESIOLOGIA, MASOTERAPIA }

/** Modalidad de entrega del servicio (pestanas de la seccion "Servicios cerca de ti"). */
enum class ModalidadAtencion { A_DOMICILIO, EN_CONSULTA }

/**
 * Categoria de servicio mostrada en la grilla de inicio. Modelo de
 * presentacion propio de esta feature (no el `Servicio` de `:core:common`):
 * agrupa varios servicios reales bajo una misma vitrina visual con icono y
 * color, algo que el dominio de negocio no necesita conocer.
 */
data class CategoriaServicio(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val profesionalesActivos: Int,
    val precioDesde: Long,
    val icono: ImageVector,
    val colorFondo: Color,
)

/**
 * Profesional destacado en la comuna del Cliente. Modelo de presentacion
 * reducido para la tarjeta de inicio (no el `Profesional` completo de
 * dominio, que trae servicios, disponibilidad e insignias completas).
 */
data class ProfesionalDestacado(
    val id: String,
    val nombre: String,
    val rnpi: String,
    val especialidad: String,
    val calificacion: Double,
    val totalResenas: Int,
    val precioDesde: Long,
    val verificado: Boolean,
)

data class SearchState(
    val cargando: Boolean = false,
    val tipoAtencion: TipoAtencion = TipoAtencion.KINESIOLOGIA,
    val modalidad: ModalidadAtencion = ModalidadAtencion.A_DOMICILIO,
    val soloVerificados: Boolean = true,
    val ubicacion: String = "Providencia, Región Metropolitana",
    val obteniendoUbicacion: Boolean = false,
    val errorUbicacion: String? = null,
    val categorias: List<CategoriaServicio> = categoriasPara(TipoAtencion.KINESIOLOGIA),
    val profesionalesDestacados: List<ProfesionalDestacado> = emptyList(),
    val cargandoProfesionales: Boolean = false,
)

sealed interface SearchAction {
    data class CambiarTipoAtencion(val tipo: TipoAtencion) : SearchAction
    data class CambiarModalidad(val modalidad: ModalidadAtencion) : SearchAction
    data class CambiarSoloVerificados(val activo: Boolean) : SearchAction
    data object ObtenerUbicacionActual : SearchAction
    data object Buscar : SearchAction
    data object VerTodasLasCategorias : SearchAction
    data class SeleccionarCategoria(val categoriaId: String) : SearchAction
    data class SeleccionarProfesional(val profesionalId: String) : SearchAction
}

/**
 * Categorias de vitrina de muestra (mock local, puramente de presentacion —
 * no vive en Firestore), separadas por [TipoAtencion] para que el selector
 * segmentado de la tarjeta de filtros cambie de verdad lo que se muestra.
 */
private fun categoriasPara(tipo: TipoAtencion): List<CategoriaServicio> = when (tipo) {
    TipoAtencion.KINESIOLOGIA -> listOf(
        CategoriaServicio(
            id = "kine-deportiva",
            nombre = "Kinesiología deportiva",
            descripcion = "Recuperación de lesiones y vuelta a la actividad física",
            profesionalesActivos = 18,
            precioDesde = 18000,
            icono = Icons.Filled.FitnessCenter,
            colorFondo = LoginMenta,
        ),
        CategoriaServicio(
            id = "kine-traumatologica",
            nombre = "Rehabilitación traumatológica",
            descripcion = "Post-quirúrgica y de fracturas",
            profesionalesActivos = 12,
            precioDesde = 20000,
            icono = Icons.AutoMirrored.Filled.Accessible,
            colorFondo = LoginAzulSuave,
        ),
        CategoriaServicio(
            id = "kine-respiratoria",
            nombre = "Kinesiología respiratoria",
            descripcion = "Para niños y adultos",
            profesionalesActivos = 9,
            precioDesde = 17000,
            icono = Icons.Filled.Air,
            colorFondo = LoginMentaSuave,
        ),
        CategoriaServicio(
            id = "kine-neurologica",
            nombre = "Kinesiología neurológica",
            descripcion = "ACV y enfermedades neuromusculares",
            profesionalesActivos = 6,
            precioDesde = 22000,
            icono = Icons.Filled.Psychology,
            colorFondo = LoginAzulSuave,
        ),
    )
    TipoAtencion.MASOTERAPIA -> listOf(
        CategoriaServicio(
            id = "maso-descontracturante",
            nombre = "Masaje descontracturante",
            descripcion = "Alivio de tensión muscular y contracturas",
            profesionalesActivos = 15,
            precioDesde = 16000,
            icono = Icons.Filled.SelfImprovement,
            colorFondo = LoginMentaSuave,
        ),
        CategoriaServicio(
            id = "maso-drenaje",
            nombre = "Drenaje linfático",
            descripcion = "Reducción de retención de líquidos",
            profesionalesActivos = 8,
            precioDesde = 15000,
            icono = Icons.Filled.Waves,
            colorFondo = LoginAzulSuave,
        ),
        CategoriaServicio(
            id = "maso-deportivo",
            nombre = "Masaje deportivo",
            descripcion = "Preparación y recuperación de deportistas",
            profesionalesActivos = 11,
            precioDesde = 17000,
            icono = Icons.Filled.FitnessCenter,
            colorFondo = LoginMenta,
        ),
        CategoriaServicio(
            id = "maso-reflexologia",
            nombre = "Reflexología podal",
            descripcion = "Terapia de relajación a través de los pies",
            profesionalesActivos = 5,
            precioDesde = 14000,
            icono = Icons.Filled.Spa,
            colorFondo = LoginMentaSuave,
        ),
    )
}

/**
 * Nombre de categoria legible para el segundo tag (no general) de
 * [Profesional.especialidades] — busca el mismo catalogo de vitrina usado en
 * [categoriasPara] para no duplicar textos. Si no hay match (dato sembrado
 * fuera del catalogo), cae a la descripcion libre del profesional.
 */
private fun Profesional.especialidadLegible(): String {
    val categoriasConocidas = categoriasPara(TipoAtencion.KINESIOLOGIA) + categoriasPara(TipoAtencion.MASOTERAPIA)
    val tagCategoria = especialidades.firstOrNull { tag -> TipoAtencion.entries.none { it.name == tag } }
    val nombreCategoria = categoriasConocidas.firstOrNull { it.id == tagCategoria }?.nombre
    return nombreCategoria ?: descripcion.ifBlank { especialidades.firstOrNull().orEmpty() }
}

/** Mapea el `Profesional` de dominio al modelo reducido de la tarjeta de inicio. */
private fun Profesional.aProfesionalDestacado(): ProfesionalDestacado = ProfesionalDestacado(
    id = usuario.id,
    nombre = usuario.nombre,
    rnpi = rnpi,
    especialidad = especialidadLegible(),
    calificacion = calificacionPromedio,
    totalResenas = totalResenas,
    precioDesde = servicios.minOfOrNull { it.precio } ?: 0L,
    verificado = estadoVerificacionGeneral == EstadoVerificacion.APROBADO,
)

private fun UbicacionError.aMensaje(): String = when (this) {
    UbicacionError.PERMISO_DENEGADO -> "Necesitamos el permiso de ubicación para mostrarte lo más cercano."
    UbicacionError.UBICACION_NO_DISPONIBLE -> "No pudimos obtener tu ubicación. Intenta nuevamente."
    UbicacionError.DESCONOCIDO -> "Algo salió mal al buscar tu ubicación."
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val ubicacionRepository: UbicacionRepository,
    private val profesionalRepository: ProfesionalRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    init {
        cargarProfesionalesDestacados(SearchState().tipoAtencion)
    }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.CambiarTipoAtencion -> {
                _state.update {
                    it.copy(
                        tipoAtencion = action.tipo,
                        categorias = categoriasPara(action.tipo),
                    )
                }
                cargarProfesionalesDestacados(action.tipo)
            }
            is SearchAction.CambiarModalidad -> _state.update { it.copy(modalidad = action.modalidad) }
            is SearchAction.CambiarSoloVerificados -> _state.update { it.copy(soloVerificados = action.activo) }
            SearchAction.ObtenerUbicacionActual -> obtenerUbicacionActual()
            // Buscar, ver todos, seleccionar categoria/profesional: sin backend ni
            // navegacion todavia (no hay datos reales en Firestore) — se conectan
            // cuando la feature salga de esta fase.
            SearchAction.Buscar,
            SearchAction.VerTodasLasCategorias,
            is SearchAction.SeleccionarCategoria,
            is SearchAction.SeleccionarProfesional,
            -> Unit
        }
    }

    private fun cargarProfesionalesDestacados(tipo: TipoAtencion) {
        viewModelScope.launch {
            _state.update { it.copy(cargandoProfesionales = true) }
            when (val resultado = profesionalRepository.buscarPorEspecialidad(tipo.name)) {
                is Result.Success -> _state.update {
                    it.copy(
                        cargandoProfesionales = false,
                        profesionalesDestacados = resultado.data.map { profesional -> profesional.aProfesionalDestacado() },
                    )
                }
                is Result.Error -> _state.update {
                    it.copy(cargandoProfesionales = false, profesionalesDestacados = emptyList())
                }
            }
        }
    }

    private fun obtenerUbicacionActual() {
        if (_state.value.obteniendoUbicacion) return
        viewModelScope.launch {
            _state.update { it.copy(obteniendoUbicacion = true, errorUbicacion = null) }
            when (val resultado = ubicacionRepository.obtenerUbicacionActual()) {
                is Result.Success -> _state.update {
                    it.copy(obteniendoUbicacion = false, ubicacion = resultado.data)
                }
                is Result.Error -> _state.update {
                    it.copy(obteniendoUbicacion = false, errorUbicacion = resultado.error.aMensaje())
                }
            }
        }
    }
}
