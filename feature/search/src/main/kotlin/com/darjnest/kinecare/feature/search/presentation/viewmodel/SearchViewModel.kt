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
import com.darjnest.kinecare.core.common.data.repository.ClienteRepository
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.domain.model.Coordenada
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.TipoAtencion
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.designsystem.theme.LoginAzulSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginMenta
import com.darjnest.kinecare.core.designsystem.theme.LoginMentaSuave
import com.darjnest.kinecare.feature.search.data.repository.UbicacionRepository
import com.darjnest.kinecare.feature.search.domain.UbicacionError
import com.darjnest.kinecare.feature.search.presentation.util.coincideConBusqueda
import com.darjnest.kinecare.feature.search.presentation.util.normalizarParaBusqueda
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Orden de la lista de profesionales. */
enum class OrdenBusqueda { MEJOR_CALIFICADOS, MENOR_PRECIO, MAS_RESENAS }

/** Como se muestran los profesionales: tarjetas en una lista o pines en un mapa. */
enum class VistaBusqueda { LISTA, MAPA }

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
    /** Modalidades de sus servicios activos; filtra por "A domicilio" / "En consulta". */
    val modalidades: Set<ModalidadServicio> = emptySet(),
    /** Ids de categoria de vitrina (tags de `especialidades`) a los que pertenece. */
    val categoriaIds: Set<String> = emptySet(),
    /**
     * Texto en que busca el cuadro de busqueda (nombre, especialidad y descripcion), ya normalizado
     * con `normalizarParaBusqueda`. Los profesionales no tienen comuna en Firestore, asi que la
     * comuna solo calza si la descripcion la menciona.
     */
    val indiceBusqueda: String = "",
    /** Zona aproximada (celda de ~1 km) donde atiende; `null` = no aparece en el mapa. */
    val zona: Coordenada? = null,
)

data class SearchState(
    val cargando: Boolean = false,
    val tipoAtencion: TipoAtencion = TipoAtencion.KINESIOLOGIA,
    val modalidad: ModalidadAtencion = ModalidadAtencion.A_DOMICILIO,
    // Apagado por defecto: aun no existe el flujo que aprueba verificaciones (Fase 6), asi que
    // encendido dejaria el inicio vacio. El cliente lo activa cuando quiera.
    val soloVerificados: Boolean = false,
    val ubicacion: String = "Providencia, Región Metropolitana",
    val obteniendoUbicacion: Boolean = false,
    val errorUbicacion: String? = null,
    val categorias: List<CategoriaServicio> = categoriasPara(TipoAtencion.KINESIOLOGIA),
    /** Categoria de la vitrina por la que se filtra la lista; `null` = todas. */
    val categoriaSeleccionadaId: String? = null,
    /** Texto libre del cuadro de busqueda; se aplica en vivo sobre los profesionales cargados. */
    val textoBusqueda: String = "",
    val orden: OrdenBusqueda = OrdenBusqueda.MEJOR_CALIFICADOS,
    /** Profesionales ya filtrados por texto, modalidad, verificacion y categoria, y ordenados por [orden]. */
    val profesionalesDestacados: List<ProfesionalDestacado> = emptyList(),
    /** Cuantos profesionales trajo la busqueda antes de filtrar (distingue "no hay" de "los filtros no calzan"). */
    val totalProfesionalesCargados: Int = 0,
    val cargandoProfesionales: Boolean = false,
    /** Ids de los profesionales que el cliente tiene en favoritos (corazon lleno en la tarjeta). */
    val favoritos: Set<String> = emptySet(),
    /** Aviso puntual para un Snackbar; se consume con [SearchAction.MensajeMostrado]. */
    val mensaje: String? = null,
    val vista: VistaBusqueda = VistaBusqueda.LISTA,
    /** Pin tocado en el mapa; su tarjeta aparece sobre el mapa. Siempre es uno de [profesionalesDestacados]. */
    val pinSeleccionadoId: String? = null,
)

sealed interface SearchAction {
    data class CambiarTipoAtencion(val tipo: TipoAtencion) : SearchAction
    data class CambiarModalidad(val modalidad: ModalidadAtencion) : SearchAction
    data class CambiarSoloVerificados(val activo: Boolean) : SearchAction
    data class CambiarTextoBusqueda(val texto: String) : SearchAction
    data class CambiarOrden(val orden: OrdenBusqueda) : SearchAction
    data object ObtenerUbicacionActual : SearchAction
    data class CambiarUbicacionManual(val texto: String) : SearchAction
    data object Buscar : SearchAction
    data object VerTodasLasCategorias : SearchAction
    data class SeleccionarCategoria(val categoriaId: String) : SearchAction
    data class SeleccionarProfesional(val profesionalId: String) : SearchAction
    data class AlternarFavorito(val profesionalId: String) : SearchAction
    data object MensajeMostrado : SearchAction
    data class CambiarVista(val vista: VistaBusqueda) : SearchAction
    /** `null` quita la seleccion (se toco el mapa fuera de un pin). */
    data class SeleccionarPin(val profesionalId: String?) : SearchAction
}

/**
 * Categorias de la vitrina (nombre, descripcion, icono y color son locales,
 * no viven en Firestore), separadas por [TipoAtencion] para que el selector
 * segmentado cambie de verdad lo que se muestra. `profesionalesActivos` y
 * `precioDesde` salen de los profesionales cargados ([conConteos]); aca
 * quedan en 0 hasta que lleguen.
 */
private fun categoriasPara(tipo: TipoAtencion): List<CategoriaServicio> = when (tipo) {
    TipoAtencion.KINESIOLOGIA -> listOf(
        CategoriaServicio(
            id = "kine-deportiva",
            nombre = "Kinesiología deportiva",
            descripcion = "Recuperación de lesiones y vuelta a la actividad física",
            profesionalesActivos = 0,
            precioDesde = 0,
            icono = Icons.Filled.FitnessCenter,
            colorFondo = LoginMenta,
        ),
        CategoriaServicio(
            id = "kine-traumatologica",
            nombre = "Rehabilitación traumatológica",
            descripcion = "Post-quirúrgica y de fracturas",
            profesionalesActivos = 0,
            precioDesde = 0,
            icono = Icons.AutoMirrored.Filled.Accessible,
            colorFondo = LoginAzulSuave,
        ),
        CategoriaServicio(
            id = "kine-respiratoria",
            nombre = "Kinesiología respiratoria",
            descripcion = "Para niños y adultos",
            profesionalesActivos = 0,
            precioDesde = 0,
            icono = Icons.Filled.Air,
            colorFondo = LoginMentaSuave,
        ),
        CategoriaServicio(
            id = "kine-neurologica",
            nombre = "Kinesiología neurológica",
            descripcion = "ACV y enfermedades neuromusculares",
            profesionalesActivos = 0,
            precioDesde = 0,
            icono = Icons.Filled.Psychology,
            colorFondo = LoginAzulSuave,
        ),
    )
    TipoAtencion.MASOTERAPIA -> listOf(
        CategoriaServicio(
            id = "maso-descontracturante",
            nombre = "Masaje descontracturante",
            descripcion = "Alivio de tensión muscular y contracturas",
            profesionalesActivos = 0,
            precioDesde = 0,
            icono = Icons.Filled.SelfImprovement,
            colorFondo = LoginMentaSuave,
        ),
        CategoriaServicio(
            id = "maso-drenaje",
            nombre = "Drenaje linfático",
            descripcion = "Reducción de retención de líquidos",
            profesionalesActivos = 0,
            precioDesde = 0,
            icono = Icons.Filled.Waves,
            colorFondo = LoginAzulSuave,
        ),
        CategoriaServicio(
            id = "maso-deportivo",
            nombre = "Masaje deportivo",
            descripcion = "Preparación y recuperación de deportistas",
            profesionalesActivos = 0,
            precioDesde = 0,
            icono = Icons.Filled.FitnessCenter,
            colorFondo = LoginMenta,
        ),
        CategoriaServicio(
            id = "maso-reflexologia",
            nombre = "Reflexología podal",
            descripcion = "Terapia de relajación a través de los pies",
            profesionalesActivos = 0,
            precioDesde = 0,
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
private fun Profesional.aProfesionalDestacado(): ProfesionalDestacado {
    val activos = servicios.filter { it.activo }
    val especialidad = especialidadLegible()
    return ProfesionalDestacado(
        id = perfil.id,
        nombre = perfil.nombre,
        rnpi = rnpi,
        especialidad = especialidad,
        indiceBusqueda = normalizarParaBusqueda("${perfil.nombre} $especialidad $descripcion"),
        calificacion = calificacionPromedio,
        totalResenas = totalResenas,
        precioDesde = (activos.ifEmpty { servicios }).minOfOrNull { it.precio } ?: 0L,
        verificado = estadoVerificacionGeneral == EstadoVerificacion.APROBADO,
        modalidades = activos.map { it.modalidad }.toSet(),
        categoriaIds = especialidades.toSet(),
        zona = zonaAproximada,
    )
}

private fun ModalidadAtencion.aModalidadServicio(): ModalidadServicio = when (this) {
    ModalidadAtencion.A_DOMICILIO -> ModalidadServicio.DOMICILIO
    ModalidadAtencion.EN_CONSULTA -> ModalidadServicio.CONSULTA
}

/** Aplica los filtros de la pantalla (el texto libre es opcional); el orden va aparte, en [ordenarPor]. */
internal fun List<ProfesionalDestacado>.filtrarPor(
    modalidad: ModalidadAtencion,
    soloVerificados: Boolean,
    categoriaId: String?,
    texto: String = "",
): List<ProfesionalDestacado> = filter { profesional ->
    modalidad.aModalidadServicio() in profesional.modalidades &&
        (!soloVerificados || profesional.verificado) &&
        (categoriaId == null || categoriaId in profesional.categoriaIds) &&
        coincideConBusqueda(profesional.indiceBusqueda, texto)
}

/**
 * Ordena por [orden]; los empates se desempatan con el otro criterio de calidad y luego por nombre
 * para que el orden sea estable. En "menor precio" un profesional sin precio (0) va al final.
 */
internal fun List<ProfesionalDestacado>.ordenarPor(orden: OrdenBusqueda): List<ProfesionalDestacado> = when (orden) {
    OrdenBusqueda.MEJOR_CALIFICADOS -> sortedWith(
        compareByDescending<ProfesionalDestacado> { it.calificacion }
            .thenByDescending { it.totalResenas }
            .thenBy { it.nombre },
    )
    OrdenBusqueda.MENOR_PRECIO -> sortedWith(
        compareBy<ProfesionalDestacado> { it.precioDesde <= 0 }
            .thenBy { it.precioDesde }
            .thenByDescending { it.calificacion }
            .thenBy { it.nombre },
    )
    OrdenBusqueda.MAS_RESENAS -> sortedWith(
        compareByDescending<ProfesionalDestacado> { it.totalResenas }
            .thenByDescending { it.calificacion }
            .thenBy { it.nombre },
    )
}

/**
 * Cuenta, por categoria, los profesionales (de los [cargados], ya filtrados por
 * modalidad/verificacion pero no por categoria) que la ofrecen y su precio mas bajo.
 */
internal fun List<CategoriaServicio>.conConteos(cargados: List<ProfesionalDestacado>): List<CategoriaServicio> =
    map { categoria ->
        val delGrupo = cargados.filter { categoria.id in it.categoriaIds }
        categoria.copy(
            profesionalesActivos = delGrupo.size,
            precioDesde = delGrupo.map { it.precioDesde }.filter { it > 0 }.minOrNull() ?: 0L,
        )
    }

private fun UbicacionError.aMensaje(): String = when (this) {
    UbicacionError.PERMISO_DENEGADO -> "Necesitamos el permiso de ubicación para mostrarte lo más cercano."
    UbicacionError.UBICACION_NO_DISPONIBLE -> "No pudimos obtener tu ubicación. Intenta nuevamente."
    UbicacionError.DESCONOCIDO -> "Algo salió mal al buscar tu ubicación."
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val ubicacionRepository: UbicacionRepository,
    private val profesionalRepository: ProfesionalRepository,
    private val clienteRepository: ClienteRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    /** Lo que trajo la ultima consulta, sin filtrar; los filtros de la pantalla se aplican encima. */
    private var cargados: List<ProfesionalDestacado> = emptyList()

    init {
        cargarProfesionalesDestacados(SearchState().tipoAtencion)
        cargarFavoritos()
    }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.CambiarTipoAtencion -> {
                cargados = emptyList()
                _state.update {
                    it.copy(
                        tipoAtencion = action.tipo,
                        categorias = categoriasPara(action.tipo),
                        categoriaSeleccionadaId = null,
                    )
                }
                cargarProfesionalesDestacados(action.tipo)
            }
            is SearchAction.CambiarModalidad -> _state.update { aplicarFiltros(it.copy(modalidad = action.modalidad)) }
            is SearchAction.CambiarTextoBusqueda ->
                _state.update { aplicarFiltros(it.copy(textoBusqueda = action.texto)) }
            is SearchAction.CambiarOrden -> _state.update { aplicarFiltros(it.copy(orden = action.orden)) }
            is SearchAction.CambiarSoloVerificados ->
                _state.update { aplicarFiltros(it.copy(soloVerificados = action.activo)) }
            SearchAction.ObtenerUbicacionActual -> obtenerUbicacionActual()
            is SearchAction.CambiarUbicacionManual -> _state.update {
                it.copy(ubicacion = action.texto, errorUbicacion = null)
            }
            // Buscar vuelve a consultar con el tipo de atencion elegido (los demas filtros se
            // aplican en vivo sobre lo cargado).
            SearchAction.Buscar -> cargarProfesionalesDestacados(_state.value.tipoAtencion)
            // Tocar la categoria elegida otra vez la quita; "Ver todos" quita cualquiera.
            SearchAction.VerTodasLasCategorias -> _state.update { aplicarFiltros(it.copy(categoriaSeleccionadaId = null)) }
            is SearchAction.SeleccionarCategoria -> _state.update {
                val nueva = action.categoriaId.takeUnless { id -> id == it.categoriaSeleccionadaId }
                aplicarFiltros(it.copy(categoriaSeleccionadaId = nueva))
            }
            // La navegacion al perfil la resuelve el Root de la pantalla contra el NavGraph.
            is SearchAction.SeleccionarProfesional -> Unit
            is SearchAction.AlternarFavorito -> alternarFavorito(action.profesionalId)
            SearchAction.MensajeMostrado -> _state.update { it.copy(mensaje = null) }
            is SearchAction.CambiarVista -> _state.update { it.copy(vista = action.vista, pinSeleccionadoId = null) }
            is SearchAction.SeleccionarPin -> _state.update { estado ->
                estado.copy(pinSeleccionadoId = action.profesionalId?.takeIf { id -> estado.profesionalesDestacados.any { it.id == id } })
            }
        }
    }

    /** Favoritos del cliente; si no se pueden leer (sin documento o sin red) el corazon parte vacio. */
    private fun cargarFavoritos() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            val favoritos = (clienteRepository.obtenerPorId(uid) as? Result.Success)?.data?.favoritos.orEmpty()
            _state.update { it.copy(favoritos = favoritos.toSet()) }
        }
    }

    /** UI optimista: se aplica ya y se revierte (con aviso) si Firestore lo rechaza. */
    private fun alternarFavorito(profesionalId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val agregar = profesionalId !in _state.value.favoritos
        _state.update { it.copy(favoritos = if (agregar) it.favoritos + profesionalId else it.favoritos - profesionalId) }
        viewModelScope.launch {
            val resultado = if (agregar) {
                clienteRepository.agregarFavorito(uid, profesionalId)
            } else {
                clienteRepository.quitarFavorito(uid, profesionalId)
            }
            if (resultado is Result.Error) {
                _state.update {
                    it.copy(
                        favoritos = if (agregar) it.favoritos - profesionalId else it.favoritos + profesionalId,
                        mensaje = "No pudimos actualizar tus favoritos. Inténtalo de nuevo.",
                    )
                }
            }
        }
    }

    /** Recalcula la lista visible y los conteos de la vitrina con los filtros actuales de [estado]. */
    private fun aplicarFiltros(estado: SearchState): SearchState {
        // Los conteos de cada categoria respetan texto, modalidad y verificacion, pero no la categoria elegida.
        val sinCategoria = cargados.filtrarPor(
            estado.modalidad,
            estado.soloVerificados,
            categoriaId = null,
            texto = estado.textoBusqueda,
        )
        val visibles = cargados.filtrarPor(
            estado.modalidad,
            estado.soloVerificados,
            estado.categoriaSeleccionadaId,
            estado.textoBusqueda,
        ).ordenarPor(estado.orden)
        return estado.copy(
            categorias = categoriasPara(estado.tipoAtencion).conConteos(sinCategoria),
            profesionalesDestacados = visibles,
            totalProfesionalesCargados = cargados.size,
            // Un filtro puede sacar de la lista al profesional cuyo pin estaba seleccionado.
            pinSeleccionadoId = estado.pinSeleccionadoId?.takeIf { id -> visibles.any { it.id == id } },
        )
    }

    private fun cargarProfesionalesDestacados(tipo: TipoAtencion) {
        viewModelScope.launch {
            _state.update { it.copy(cargandoProfesionales = true) }
            when (val resultado = profesionalRepository.buscarPorTipoAtencion(tipo)) {
                is Result.Success -> {
                    cargados = resultado.data.map { profesional -> profesional.aProfesionalDestacado() }
                    _state.update { aplicarFiltros(it).copy(cargandoProfesionales = false) }
                }
                is Result.Error -> {
                    cargados = emptyList()
                    _state.update { aplicarFiltros(it).copy(cargandoProfesionales = false) }
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
