@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sell
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.data.repository.ReservaRepository
import com.darjnest.kinecare.core.common.data.repository.ServicioRepository
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoPago
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.Reserva
import com.darjnest.kinecare.core.common.domain.model.TipoInsignia
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.common.util.ZonaHorariaChile
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Clock

/**
 * Identificadores estables de cada [AccesoGestion] de la grilla "Gestion
 * Profesional". Se usan para enrutar la navegacion (ver
 * ProfessionalPanelNavGraph) sin acoplar el ViewModel a Navigation Compose.
 */
object AccesoGestionId {
    const val MI_PERFIL = "mi_perfil"
    const val SERVICIOS = "servicios"
    const val DISPONIBILIDAD = "disponibilidad"
    const val SOLICITUDES = "solicitudes"
    const val LIQUIDACIONES = "liquidaciones"
    const val DOCUMENTOS = "documentos"
}

/**
 * Estado de verificacion de identidad/credenciales que se muestra en el
 * banner de la parte superior del panel. Modelo de presentacion reducido
 * (no reemplaza `Insignia`/`EstadoVerificacion` de dominio). Solo se arma
 * con credenciales aprobadas y numero de registro (RNPI) informado; si no,
 * el banner se oculta en vez de afirmar una verificacion que no existe.
 */
data class VerificacionPanel(
    val numeroSis: String,
    /** El dominio no guarda la entidad de registro todavia: `null` la omite del texto. */
    val entidad: String? = null,
    val credencialesAlDia: Boolean,
)

/** Resumen de KPIs del dia mostrado en las 3 tarjetas superiores. */
data class ResumenHoy(
    /** Citas `CONFIRMADA` que aun no empiezan. */
    val proximasCitas: Int,
    /** Citas confirmadas o en curso cuya fecha (hora de Chile) es hoy. */
    val citasHoyEnAgenda: Int,
    /**
     * Honorario neto de las atenciones `COMPLETADA` con pago autorizado. Aun no
     * existe un registro de liquidaciones, asi que todo lo cobrado cuenta como
     * pendiente de liquidar (ver Fase 7, "Liquidaciones y finanzas").
     */
    val porLiquidar: Long,
    val calificacion: Double,
    val totalResenas: Int,
    /** Hora (Chile) de la ultima lectura; no se actualiza sola. */
    val actualizadoHaceTexto: String,
)

/**
 * Siguiente cita confirmada, destacada en su propia tarjeta. Distancia y
 * piso no existen en el dominio: `comuna` y `complemento` (las indicaciones de
 * la direccion) son nulos cuando no aplican y la vista oculta esa fila.
 */
data class ProximaCita(
    val pacienteNombre: String,
    val servicio: String,
    /** "En 45 min", "En 3 h", "En 2 días". */
    val tiempoRestanteTexto: String,
    val modalidad: String,
    val horaTexto: String,
    val comuna: String? = null,
    val direccion: String,
    val complemento: String? = null,
    /** Direccion para abrir en Maps; solo en atenciones a domicilio con direccion informada. */
    val direccionRuta: String? = null,
)

/** Tono de color del icono/circulo de cada [AccesoGestion]. */
enum class ColorAcceso { AZUL, VERDE, GRIS, ROJO }

/**
 * Acceso rapido de la grilla "Gestion Profesional" (Mi Perfil, Servicios,
 * Disponibilidad, Solicitudes, Liquidaciones, Documentos).
 */
data class AccesoGestion(
    val id: String,
    val titulo: String,
    val subtitulo: String,
    val icono: ImageVector,
    val color: ColorAcceso,
    val badgeTexto: String? = null,
    val badgeNumero: Int? = null,
    val badgeCheck: Boolean = false,
    val subtituloEnAlerta: Boolean = false,
    val mostrarChevron: Boolean = false,
)

data class ProfessionalPanelState(
    val cargando: Boolean = false,
    val nombreProfesional: String = "",
    val disponible: Boolean = true,
    val verificacion: VerificacionPanel? = null,
    val resumenHoy: ResumenHoy? = null,
    val proximaCita: ProximaCita? = null,
    val accesosGestion: List<AccesoGestion> = emptyList(),
    /**
     * Si el profesional vinculo su cuenta de Mercado Pago
     * (`profesionales/{id}.mercadoPagoConectado`, solo lo escribe el backend);
     * `null` mientras no se pudo comprobar: la tarjeta "Cobros" no afirma nada.
     */
    val mercadoPagoConectado: Boolean? = null,
)

sealed interface ProfessionalPanelAction {
    data class CambiarDisponibilidad(val disponible: Boolean) : ProfessionalPanelAction

    /** Abrir la direccion de la proxima cita en Maps: necesita un `Context`, la resuelve el `Root`. */
    data object AbrirRuta : ProfessionalPanelAction
    data class SeleccionarAccesoGestion(val accesoId: String) : ProfessionalPanelAction
    data object AbrirConfiguracionCuenta : ProfessionalPanelAction

    /**
     * Vuelve a leer perfil y reservas (p. ej. al volver a la pantalla tras
     * aceptar una solicitud o vincular Mercado Pago). Ignorada si ya hay una
     * lectura en curso.
     */
    data object Actualizar : ProfessionalPanelAction

    /** Navegacion a `:feature:payment` (vincular Mercado Pago): la resuelve el `Root` via callback. */
    data object ConectarMercadoPago : ProfessionalPanelAction
}

/**
 * Accesos de la grilla "Gestion Profesional": son navegacion fija del panel
 * (como la barra inferior), no datos de negocio, asi que se muestran desde
 * el primer render aunque todavia no haya conexion a Firestore. Los
 * badges de conteo (ej. solicitudes pendientes) si dependen de datos reales
 * y quedan sin fijar hasta que esta pantalla se conecte (Fase 7).
 */
private val accesosGestionFijos = listOf(
    AccesoGestion(
        id = AccesoGestionId.MI_PERFIL,
        titulo = "Mi Perfil",
        subtitulo = "Credenciales y especialidades",
        icono = Icons.Filled.Person,
        color = ColorAcceso.VERDE,
        mostrarChevron = true,
    ),
    AccesoGestion(
        id = AccesoGestionId.SERVICIOS,
        titulo = "Servicios",
        subtitulo = "Catálogo y tarifas",
        icono = Icons.Filled.Sell,
        color = ColorAcceso.AZUL,
        mostrarChevron = true,
    ),
    AccesoGestion(
        id = AccesoGestionId.DISPONIBILIDAD,
        titulo = "Disponibilidad",
        subtitulo = "Horarios y cobertura",
        icono = Icons.Filled.CalendarMonth,
        color = ColorAcceso.AZUL,
        mostrarChevron = true,
    ),
    AccesoGestion(
        id = AccesoGestionId.SOLICITUDES,
        titulo = "Solicitudes",
        subtitulo = "Citas por confirmar",
        icono = Icons.Filled.NotificationsActive,
        color = ColorAcceso.ROJO,
        mostrarChevron = true,
    ),
    AccesoGestion(
        id = AccesoGestionId.LIQUIDACIONES,
        titulo = "Liquidaciones",
        subtitulo = "Pagos e ingresos",
        icono = Icons.Filled.Payments,
        color = ColorAcceso.VERDE,
        mostrarChevron = true,
    ),
    AccesoGestion(
        id = AccesoGestionId.DOCUMENTOS,
        titulo = "Documentos",
        subtitulo = "Verificación y credenciales",
        icono = Icons.Filled.Folder,
        color = ColorAcceso.GRIS,
        mostrarChevron = true,
    ),
)

/** "En 45 min" / "En 3 h" / "En 2 días": reutiliza el redondeo de [textoTiempoRestante]. */
private fun textoEnTiempo(restante: kotlin.time.Duration): String =
    "En " + textoTiempoRestante(restante).removePrefix("Cita en ")

private fun Profesional.aVerificacionPanel(): VerificacionPanel? {
    val credencialesAprobadas = insignias.any {
        it.tipo == TipoInsignia.CREDENCIALES && it.estado == EstadoVerificacion.APROBADO
    }
    return if (credencialesAprobadas && rnpi.isNotBlank()) {
        VerificacionPanel(numeroSis = rnpi, credencialesAlDia = true)
    } else {
        null
    }
}

private fun ModalidadServicio.aTextoPanel(): String = when (this) {
    ModalidadServicio.DOMICILIO -> "A Domicilio"
    ModalidadServicio.CONSULTA -> "En Consulta"
    ModalidadServicio.ONLINE -> "Online"
}

private fun Reserva.aProximaCita(ahora: kotlinx.datetime.Instant, paciente: String, servicio: String): ProximaCita {
    val domicilio = modalidad == ModalidadServicio.DOMICILIO
    val direccionInformada = direccion?.takeIf { domicilio && it.calle.isNotBlank() }
    return ProximaCita(
        pacienteNombre = paciente,
        servicio = servicio,
        tiempoRestanteTexto = textoEnTiempo(fechaHora - ahora),
        modalidad = modalidad.aTextoPanel(),
        horaTexto = fechaHora.aFechaHoraTexto(ahora),
        comuna = direccionInformada?.comuna?.takeIf { it.isNotBlank() },
        direccion = ubicacionTexto(),
        complemento = direccionInformada?.indicaciones?.takeIf { it.isNotBlank() },
        direccionRuta = direccionInformada?.let { d ->
            listOf("${d.calle} ${d.numero}".trim(), d.comuna, d.ciudad).filter { it.isNotBlank() }.joinToString(", ")
        },
    )
}

@HiltViewModel
class ProfessionalPanelViewModel @Inject constructor(
    private val profesionalRepository: ProfesionalRepository,
    private val reservaRepository: ReservaRepository,
    private val usuarioRepository: UsuarioRepository,
    private val servicioRepository: ServicioRepository,
    private val firebaseAuth: FirebaseAuth,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfessionalPanelState(accesosGestion = accesosGestionFijos))
    val state: StateFlow<ProfessionalPanelState> = _state.asStateFlow()

    /**
     * Ultimos datos leidos. Cada lectura falla por separado y un fallo conserva
     * lo ultimo conocido: un corte de red no debe mostrar "No conectada" a quien
     * si vinculo Mercado Pago, ni vaciar la agenda.
     */
    private var perfil: Profesional? = null
    private var reservas: List<Reserva>? = null

    /** Nombres de la proxima cita (paciente por `clienteId`, servicio por `servicioId`). */
    private val pacientes = mutableMapOf<String, String>()
    private var servicios: Map<String, String> = emptyMap()

    private var cargaJob: Job? = null

    init {
        cargar()
    }

    fun onAction(action: ProfessionalPanelAction) {
        when (action) {
            is ProfessionalPanelAction.CambiarDisponibilidad ->
                _state.update { it.copy(disponible = action.disponible) }
            ProfessionalPanelAction.Actualizar -> cargar()
            // Navegacion y apertura de Maps: el Root las resuelve (necesitan NavGraph/Context).
            ProfessionalPanelAction.ConectarMercadoPago,
            ProfessionalPanelAction.AbrirRuta,
            is ProfessionalPanelAction.SeleccionarAccesoGestion,
            // Sin pantalla de configuracion de cuenta todavia.
            ProfessionalPanelAction.AbrirConfiguracionCuenta,
            -> Unit
        }
    }

    private fun cargar() {
        if (cargaJob?.isActive == true) return
        val uid = firebaseAuth.currentUser?.uid ?: return
        cargaJob = viewModelScope.launch {
            _state.update { it.copy(cargando = true) }
            (profesionalRepository.obtenerPorId(uid) as? Result.Success)?.let { perfil = it.data }
            (reservaRepository.obtenerPorProfesional(uid) as? Result.Success)?.let { reservas = it.data }
            resolverNombresProximaCita(uid)
            _state.update { derivar(it).copy(cargando = false) }
        }
    }

    /** `null` si no hay cita proxima: la tarjeta se oculta. */
    private fun proximaReserva(ahora: kotlinx.datetime.Instant): Reserva? = reservas.orEmpty()
        .filter { it.estado == EstadoReserva.CONFIRMADA && it.fechaHora > ahora }
        .minByOrNull { it.fechaHora }

    private suspend fun resolverNombresProximaCita(uid: String) {
        val proxima = proximaReserva(clock.now()) ?: return
        if (servicios.isEmpty()) {
            val resultado = servicioRepository.obtenerPorProfesional(uid)
            if (resultado is Result.Success) servicios = resultado.data.associate { it.id to it.nombre }
        }
        if (proxima.clienteId !in pacientes) {
            val resultado = usuarioRepository.obtenerPorId(proxima.clienteId)
            if (resultado is Result.Success) pacientes[proxima.clienteId] = resultado.data.nombre
        }
    }

    private fun derivar(estado: ProfessionalPanelState): ProfessionalPanelState {
        val ahora = clock.now()
        val perfil = perfil
        val reservas = reservas
        val hoy = ahora.toLocalDateTime(ZonaHorariaChile).date

        val proxima = proximaReserva(ahora)
        val pendientes = reservas.orEmpty().count { it.estadoResuelto(ahora) == null }

        val resumen = if (perfil != null && reservas != null) {
            val local = ahora.toLocalDateTime(ZonaHorariaChile)
            ResumenHoy(
                proximasCitas = reservas.count { it.estado == EstadoReserva.CONFIRMADA && it.fechaHora > ahora },
                citasHoyEnAgenda = reservas.count {
                    (it.estado == EstadoReserva.CONFIRMADA || it.estado == EstadoReserva.EN_CURSO) &&
                        it.fechaHora.toLocalDateTime(ZonaHorariaChile).date == hoy
                },
                porLiquidar = reservas
                    .filter { it.estado == EstadoReserva.COMPLETADA && it.pago.estado == EstadoPago.AUTORIZADO }
                    .sumOf { it.honorarioNeto() },
                calificacion = perfil.calificacionPromedio,
                totalResenas = perfil.totalResenas,
                actualizadoHaceTexto = "Actualizado %02d:%02d".format(local.hour, local.minute),
            )
        } else {
            null
        }

        return estado.copy(
            nombreProfesional = perfil?.usuario?.nombre?.trim()?.substringBefore(' ').orEmpty(),
            verificacion = perfil?.aVerificacionPanel(),
            mercadoPagoConectado = perfil?.mercadoPagoConectado,
            resumenHoy = resumen,
            proximaCita = proxima?.aProximaCita(
                ahora = ahora,
                paciente = pacientes[proxima.clienteId] ?: "Paciente",
                servicio = servicios[proxima.servicioId] ?: "Sesión",
            ),
            accesosGestion = accesosGestionFijos.map { acceso ->
                if (acceso.id == AccesoGestionId.SOLICITUDES && pendientes > 0) {
                    acceso.copy(badgeNumero = pendientes)
                } else {
                    acceso
                }
            },
        )
    }
}
