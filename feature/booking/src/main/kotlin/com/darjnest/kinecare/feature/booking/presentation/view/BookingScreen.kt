@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.booking.presentation.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.domain.model.Direccion
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.util.formatComoClp
import com.darjnest.kinecare.core.designsystem.components.button.KineCarePrimaryButton
import com.darjnest.kinecare.core.designsystem.components.button.KineCareSecondaryButton
import com.darjnest.kinecare.core.designsystem.components.card.KineCareCard
import com.darjnest.kinecare.core.designsystem.components.label.BadgeTono
import com.darjnest.kinecare.core.designsystem.components.label.KineCareBadge
import com.darjnest.kinecare.core.designsystem.components.loading.KineCareFullScreenLoading
import com.darjnest.kinecare.core.designsystem.theme.KineCareSpacing
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.feature.booking.domain.service.DIAS_HORIZONTE
import com.darjnest.kinecare.feature.booking.domain.service.DiaConHorarios
import com.darjnest.kinecare.feature.booking.presentation.util.etiquetaModalidad
import com.darjnest.kinecare.feature.booking.presentation.util.formatearDiaMesCorto
import com.darjnest.kinecare.feature.booking.presentation.util.formatearDireccion
import com.darjnest.kinecare.feature.booking.presentation.util.formatearFechaHora
import com.darjnest.kinecare.feature.booking.presentation.util.formatearFechaLarga
import com.darjnest.kinecare.feature.booking.presentation.util.formatearHora
import com.darjnest.kinecare.feature.booking.presentation.util.mensajeErrorCarga
import com.darjnest.kinecare.feature.booking.presentation.util.mensajeErrorEnvio
import com.darjnest.kinecare.feature.booking.presentation.util.nombreDiaCorto
import com.darjnest.kinecare.feature.booking.presentation.util.textoDuracion
import com.darjnest.kinecare.feature.booking.presentation.util.tituloPaso
import com.darjnest.kinecare.feature.booking.presentation.viewmodel.BookingAction
import com.darjnest.kinecare.feature.booking.presentation.viewmodel.BookingState
import com.darjnest.kinecare.feature.booking.presentation.viewmodel.BookingViewModel
import com.darjnest.kinecare.feature.booking.presentation.viewmodel.CampoDireccion
import com.darjnest.kinecare.feature.booking.presentation.viewmodel.FormularioDireccion
import com.darjnest.kinecare.feature.booking.presentation.viewmodel.LARGO_MAXIMO_INDICACIONES
import com.darjnest.kinecare.feature.booking.presentation.viewmodel.PasoReserva
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

@Composable
fun BookingRoot(
    modifier: Modifier = Modifier,
    viewModel: BookingViewModel = hiltViewModel(),
    onVolver: () -> Unit = {},
    onIrAMisCitas: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BookingScreen(
        state = state,
        onAction = { accion ->
            // Salir del flujo es navegacion: el Root la resuelve contra el NavGraph.
            when (accion) {
                BookingAction.VolverAtras, BookingAction.Finalizar -> onVolver()
                BookingAction.IrAMisCitas -> onIrAMisCitas()
                else -> viewModel.onAction(accion)
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingScreen(
    state: BookingState,
    onAction: (BookingAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val enFlujo = !state.cargando && state.errorCarga == null && state.reservaCreadaId == null
    val enPrimerPaso = state.paso == state.pasos.first()
    // El back del sistema retrocede un paso dentro del flujo; en el primero
    // (o en la confirmacion) sale de la pantalla como siempre.
    BackHandler(enabled = enFlujo && !enPrimerPaso) { onAction(BookingAction.PasoAnterior) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Reservar hora")
                        if (state.profesionalNombre.isNotBlank()) {
                            Text(
                                text = state.profesionalNombre,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                state.reservaCreadaId != null -> onAction(BookingAction.Finalizar)
                                enFlujo && !enPrimerPaso -> onAction(BookingAction.PasoAnterior)
                                else -> onAction(BookingAction.VolverAtras)
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        bottomBar = {
            if (enFlujo && state.servicios.isNotEmpty()) {
                BarraAccion(state = state, onAction = onAction)
            }
        },
    ) { innerPadding ->
        val contenidoModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when {
            state.cargando -> KineCareFullScreenLoading(modifier = contenidoModifier)
            state.errorCarga != null -> EstadoMensaje(
                mensaje = mensajeErrorCarga(state.errorCarga),
                accion = "Reintentar",
                onAccion = { onAction(BookingAction.Reintentar) },
                modifier = contenidoModifier,
            )
            state.reservaCreadaId != null -> Confirmacion(state = state, onAction = onAction, modifier = contenidoModifier)
            state.servicios.isEmpty() -> EstadoMensaje(
                mensaje = "Este profesional no tiene servicios disponibles para reservar en este momento.",
                accion = "Volver",
                onAccion = { onAction(BookingAction.VolverAtras) },
                modifier = contenidoModifier,
            )
            else -> ContenidoPaso(state = state, onAction = onAction, modifier = contenidoModifier)
        }
    }
}

@Composable
private fun EstadoMensaje(
    mensaje: String,
    accion: String,
    onAccion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(KineCareSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = mensaje, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Box(modifier = Modifier.padding(top = KineCareSpacing.l)) {
            KineCarePrimaryButton(text = accion, onClick = onAccion)
        }
    }
}

@Composable
private fun ContenidoPaso(
    state: BookingState,
    onAction: (BookingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(KineCareSpacing.l),
        verticalArrangement = Arrangement.spacedBy(KineCareSpacing.l),
    ) {
        IndicadorPaso(state = state)
        state.errorEnvio?.let { MensajeError(texto = mensajeErrorEnvio(it)) }
        when (state.paso) {
            PasoReserva.MODALIDAD -> PasoModalidad(state = state, onAction = onAction)
            PasoReserva.FECHA_HORA -> PasoFechaHora(state = state, onAction = onAction)
            PasoReserva.DIRECCION -> PasoDireccion(state = state, onAction = onAction)
            PasoReserva.REVISION -> PasoRevision(state = state)
        }
    }
}

@Composable
private fun IndicadorPaso(state: BookingState) {
    Column(verticalArrangement = Arrangement.spacedBy(KineCareSpacing.s)) {
        Text(
            text = "Paso ${state.numeroPaso} de ${state.pasos.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = tituloPaso(state.paso),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        LinearProgressIndicator(
            progress = { state.numeroPaso.toFloat() / state.pasos.size },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MensajeError(texto: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(KineCareSpacing.m),
        )
    }
}

@Composable
private fun TituloSeccion(texto: String) {
    Text(text = texto, style = MaterialTheme.typography.titleMedium)
}

// ---- Paso 1: modalidad y servicio ----

@Composable
private fun PasoModalidad(state: BookingState, onAction: (BookingAction) -> Unit) {
    TituloSeccion("¿Cómo quieres atenderte?")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(KineCareSpacing.s)) {
        state.modalidades.forEach { modalidad ->
            FilterChip(
                selected = modalidad == state.modalidadSeleccionada,
                onClick = { onAction(BookingAction.SeleccionarModalidad(modalidad)) },
                label = { Text(etiquetaModalidad(modalidad)) },
            )
        }
    }
    if (state.modalidadSeleccionada != null) {
        TituloSeccion("Elige el servicio")
        state.serviciosDeModalidad.forEach { servicio ->
            TarjetaServicio(
                servicio = servicio,
                seleccionado = servicio.id == state.servicioSeleccionado?.id,
                onClick = { onAction(BookingAction.SeleccionarServicio(servicio.id)) },
            )
        }
    }
}

@Composable
private fun TarjetaServicio(
    servicio: Servicio,
    seleccionado: Boolean,
    onClick: () -> Unit,
) {
    KineCareCard(
        modifier = Modifier.selectable(selected = seleccionado, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = seleccionado, onClick = null)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = KineCareSpacing.m),
                verticalArrangement = Arrangement.spacedBy(KineCareSpacing.xs),
            ) {
                Text(text = servicio.nombre, style = MaterialTheme.typography.titleMedium)
                if (servicio.descripcion.isNotBlank()) {
                    Text(
                        text = servicio.descripcion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${textoDuracion(servicio.duracionMinutos)} · ${servicio.precio.formatComoClp()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ---- Paso 2: fecha y hora ----

@Composable
private fun PasoFechaHora(state: BookingState, onAction: (BookingAction) -> Unit) {
    if (state.dias.isEmpty()) {
        Text(
            text = "No hay horarios disponibles para este servicio en los próximos $DIAS_HORIZONTE días. " +
                "Prueba con otro servicio o vuelve más tarde.",
            style = MaterialTheme.typography.bodyLarge,
        )
        return
    }
    TituloSeccion("Día")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(KineCareSpacing.s)) {
        items(state.dias, key = { it.fecha.toString() }) { dia ->
            ChipDia(
                dia = dia,
                seleccionado = dia.fecha == state.fechaSeleccionada,
                onClick = { onAction(BookingAction.SeleccionarFecha(dia.fecha)) },
            )
        }
    }
    val fecha = state.fechaSeleccionada ?: return
    TituloSeccion("Hora · ${formatearFechaLarga(fecha)}")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(KineCareSpacing.s),
        verticalArrangement = Arrangement.spacedBy(KineCareSpacing.s),
    ) {
        state.horariosDelDia.forEach { horario ->
            FilterChip(
                selected = horario == state.horarioSeleccionado,
                onClick = { onAction(BookingAction.SeleccionarHorario(horario)) },
                label = { Text(formatearHora(horario)) },
            )
        }
    }
    Text(
        text = "Horarios en hora de Chile continental.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChipDia(
    dia: DiaConHorarios,
    seleccionado: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = seleccionado,
        onClick = onClick,
        label = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = KineCareSpacing.xs),
            ) {
                Text(nombreDiaCorto(dia.fecha.dayOfWeek), style = MaterialTheme.typography.labelMedium)
                Text(formatearDiaMesCorto(dia.fecha), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

// ---- Paso 3: direccion (solo DOMICILIO) ----

@Composable
private fun PasoDireccion(state: BookingState, onAction: (BookingAction) -> Unit) {
    if (state.direccionesGuardadas.isNotEmpty()) {
        TituloSeccion("Tus direcciones")
        state.direccionesGuardadas.forEach { direccion ->
            DireccionGuardada(
                direccion = direccion,
                onClick = { onAction(BookingAction.UsarDireccionGuardada(direccion)) },
            )
        }
        TituloSeccion("O ingresa otra")
    }
    FormularioDireccionCampos(
        formulario = state.direccion,
        onCambiar = { campo, valor -> onAction(BookingAction.CambiarCampoDireccion(campo, valor)) },
    )
}

@Composable
private fun DireccionGuardada(direccion: Direccion, onClick: () -> Unit) {
    KineCareCard(modifier = Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Text(text = formatearDireccion(direccion), style = MaterialTheme.typography.bodyLarge)
        direccion.indicaciones?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FormularioDireccionCampos(
    formulario: FormularioDireccion,
    onCambiar: (CampoDireccion, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KineCareSpacing.s)) {
        CampoTexto("Calle *", formulario.calle) { onCambiar(CampoDireccion.CALLE, it) }
        CampoTexto("Número *", formulario.numero) { onCambiar(CampoDireccion.NUMERO, it) }
        CampoTexto("Comuna *", formulario.comuna) { onCambiar(CampoDireccion.COMUNA, it) }
        CampoTexto("Ciudad", formulario.ciudad) { onCambiar(CampoDireccion.CIUDAD, it) }
        OutlinedTextField(
            value = formulario.indicaciones,
            onValueChange = { onCambiar(CampoDireccion.INDICACIONES, it) },
            label = { Text("Indicaciones (opcional)") },
            placeholder = { Text("Depto, timbre, referencias") },
            supportingText = { Text("${formulario.indicaciones.length}/$LARGO_MAXIMO_INDICACIONES") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "* Obligatorio",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CampoTexto(etiqueta: String, valor: String, onCambiar: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---- Paso 4: revision ----

@Composable
private fun PasoRevision(state: BookingState) {
    val servicio = state.servicioSeleccionado ?: return
    val horario = state.horarioSeleccionado ?: return
    ResumenReserva(state = state, servicio = servicio, horario = horario)
}

@Composable
private fun ResumenReserva(state: BookingState, servicio: Servicio, horario: Instant) {
    KineCareCard {
        Column(verticalArrangement = Arrangement.spacedBy(KineCareSpacing.m)) {
            FilaResumen("Profesional", state.profesionalNombre)
            FilaResumen("Servicio", servicio.nombre)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Modalidad",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                KineCareBadge(texto = etiquetaModalidad(servicio.modalidad), tono = BadgeTono.INFO)
            }
            FilaResumen("Fecha y hora", formatearFechaHora(horario))
            FilaResumen("Duración", textoDuracion(servicio.duracionMinutos))
            if (state.requiereDireccion) {
                FilaResumen("Dirección", formatearDireccion(state.direccion.aDireccion()))
                state.direccion.aDireccion().indicaciones?.let { FilaResumen("Indicaciones", it) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    text = servicio.precio.formatComoClp(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun FilaResumen(etiqueta: String, valor: String) {
    Column(verticalArrangement = Arrangement.spacedBy(KineCareSpacing.xs)) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = valor, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BarraAccion(state: BookingState, onAction: (BookingAction) -> Unit) {
    Surface(tonalElevation = KineCareSpacing.xs) {
        Box(modifier = Modifier.padding(KineCareSpacing.l)) {
            if (state.paso == PasoReserva.REVISION) {
                KineCarePrimaryButton(
                    text = if (state.enviando) "Enviando…" else "Confirmar reserva",
                    onClick = { onAction(BookingAction.Confirmar) },
                    enabled = state.puedeContinuar,
                )
            } else {
                KineCarePrimaryButton(
                    text = "Continuar",
                    onClick = { onAction(BookingAction.Continuar) },
                    enabled = state.puedeContinuar,
                )
            }
        }
    }
}

// ---- Confirmacion ----

@Composable
private fun Confirmacion(
    state: BookingState,
    onAction: (BookingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(KineCareSpacing.l),
        verticalArrangement = Arrangement.spacedBy(KineCareSpacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(KineCareSpacing.avatarGrande),
        )
        Text(
            text = "¡Reserva solicitada!",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "${state.profesionalNombre} debe confirmarla. Puedes seguir su estado en Mis Citas.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        val servicio = state.servicioSeleccionado
        val horario = state.horarioSeleccionado
        if (servicio != null && horario != null) {
            ResumenReserva(state = state, servicio = servicio, horario = horario)
        }
        KineCarePrimaryButton(text = "Ver mis citas", onClick = { onAction(BookingAction.IrAMisCitas) })
        KineCareSecondaryButton(text = "Listo", onClick = { onAction(BookingAction.Finalizar) })
    }
}

// ---- Previews ----

private val servicioPreview = Servicio(
    id = "s1",
    nombre = "Kinesiología musculoesquelética",
    descripcion = "Evaluación y tratamiento de lesiones.",
    modalidad = ModalidadServicio.DOMICILIO,
    duracionMinutos = 60,
    precio = 35000,
)

private val horarioPreview = Instant.parse("2026-10-05T13:00:00Z")

@Preview(showBackground = true)
@Composable
private fun BookingModalidadPreview() {
    KineCareTheme {
        BookingScreen(
            state = BookingState(
                cargando = false,
                profesionalNombre = "Camila Rojas",
                servicios = listOf(servicioPreview, servicioPreview.copy(id = "s2", modalidad = ModalidadServicio.CONSULTA)),
                modalidadSeleccionada = ModalidadServicio.DOMICILIO,
                servicioSeleccionado = servicioPreview,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BookingFechaHoraPreview() {
    val fecha = LocalDate(2026, 10, 5)
    KineCareTheme {
        BookingScreen(
            state = BookingState(
                cargando = false,
                profesionalNombre = "Camila Rojas",
                servicios = listOf(servicioPreview),
                paso = PasoReserva.FECHA_HORA,
                modalidadSeleccionada = ModalidadServicio.DOMICILIO,
                servicioSeleccionado = servicioPreview,
                dias = listOf(DiaConHorarios(fecha, listOf(horarioPreview, Instant.parse("2026-10-05T14:00:00Z")))),
                fechaSeleccionada = fecha,
                horarioSeleccionado = horarioPreview,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BookingRevisionPreview() {
    KineCareTheme {
        BookingScreen(
            state = BookingState(
                cargando = false,
                profesionalNombre = "Camila Rojas",
                servicios = listOf(servicioPreview),
                paso = PasoReserva.REVISION,
                modalidadSeleccionada = ModalidadServicio.DOMICILIO,
                servicioSeleccionado = servicioPreview,
                horarioSeleccionado = horarioPreview,
                direccion = FormularioDireccion(calle = "Av. Providencia", numero = "1234", comuna = "Providencia", ciudad = "Santiago"),
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BookingErrorPreview() {
    KineCareTheme {
        BookingScreen(state = BookingState(cargando = false, errorCarga = ProfesionalError.SIN_INTERNET))
    }
}
