package com.darjnest.kinecare.feature.search.presentation.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import com.darjnest.kinecare.core.designsystem.theme.AzulPetroleo95
import com.darjnest.kinecare.core.designsystem.theme.InicioPastelVerde
import com.darjnest.kinecare.core.designsystem.theme.LoginMenta
import com.darjnest.kinecare.core.designsystem.theme.VerdeSalvia95
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

private val categoriasDeEjemplo = listOf(
    CategoriaServicio(
        id = "kine-deportiva",
        nombre = "Kinesiología Deportiva",
        descripcion = "Lesiones, readaptación y vuelta al entrenamiento...",
        profesionalesActivos = 14,
        precioDesde = 32_000,
        icono = Icons.Filled.OpenInFull,
        colorFondo = LoginMenta,
    ),
    CategoriaServicio(
        id = "rehab-columna",
        nombre = "Rehabilitación Columna",
        descripcion = "Hernias, lumbalgias, postura y alivio del dolor...",
        profesionalesActivos = 9,
        precioDesde = 35_000,
        icono = Icons.AutoMirrored.Filled.Accessible,
        colorFondo = AzulPetroleo95,
    ),
    CategoriaServicio(
        id = "adulto-mayor",
        nombre = "Adulto Mayor",
        descripcion = "Movilidad, equilibrio y prevención integral de...",
        profesionalesActivos = 11,
        precioDesde = 30_000,
        icono = Icons.Filled.Elderly,
        colorFondo = InicioPastelVerde,
    ),
    CategoriaServicio(
        id = "descontracturante",
        nombre = "Descontracturante",
        descripcion = "Liberación miofascial profunda y alivio de nudo...",
        profesionalesActivos = 8,
        precioDesde = 28_000,
        icono = Icons.Filled.BackHand,
        colorFondo = VerdeSalvia95,
    ),
)

private val profesionalesDeEjemplo = listOf(
    ProfesionalDestacado(
        id = "prof-matias",
        nombre = "Klgo. Matías Fernández",
        rnpi = "RNPI N° 48102",
        especialidad = "Especialista en Columna & Deportivo",
        calificacion = 4.9,
        totalResenas = 124,
        precioDesde = 35_000,
        verificado = true,
    ),
    ProfesionalDestacado(
        id = "prof-camila",
        nombre = "Klga. Camila Muñoz",
        rnpi = "RNPI N° 62391",
        especialidad = "Neuro-rehabilitación & Adulto Mayor",
        calificacion = 5.0,
        totalResenas = 98,
        precioDesde = 32_000,
        verificado = true,
    ),
)

data class SearchState(
    val cargando: Boolean = false,
    val tipoAtencion: TipoAtencion = TipoAtencion.KINESIOLOGIA,
    val modalidad: ModalidadAtencion = ModalidadAtencion.A_DOMICILIO,
    val soloVerificados: Boolean = true,
    val ubicacion: String = "Providencia, Región Metropolitana",
    val categorias: List<CategoriaServicio> = categoriasDeEjemplo,
    val profesionalesDestacados: List<ProfesionalDestacado> = profesionalesDeEjemplo,
)

sealed interface SearchAction {
    data class CambiarTipoAtencion(val tipo: TipoAtencion) : SearchAction
    data class CambiarModalidad(val modalidad: ModalidadAtencion) : SearchAction
    data class CambiarSoloVerificados(val activo: Boolean) : SearchAction
    data object Buscar : SearchAction
    data object VerTodasLasCategorias : SearchAction
    data class SeleccionarCategoria(val categoriaId: String) : SearchAction
    data class SeleccionarProfesional(val profesionalId: String) : SearchAction
}

@HiltViewModel
class SearchViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.CambiarTipoAtencion -> _state.update { it.copy(tipoAtencion = action.tipo) }
            is SearchAction.CambiarModalidad -> _state.update { it.copy(modalidad = action.modalidad) }
            is SearchAction.CambiarSoloVerificados -> _state.update { it.copy(soloVerificados = action.activo) }
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
}
