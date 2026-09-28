package com.darjnest.kinecare.feature.professional_profile.presentation.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.domain.model.TipoInsignia
import com.darjnest.kinecare.core.common.util.formatComoClp
import com.darjnest.kinecare.core.designsystem.components.button.KineCarePrimaryButton
import com.darjnest.kinecare.core.designsystem.components.card.KineCareCard
import com.darjnest.kinecare.core.designsystem.components.label.KineCareBadge
import com.darjnest.kinecare.core.designsystem.components.loading.KineCareFullScreenLoading
import com.darjnest.kinecare.core.designsystem.theme.InicioDorado
import com.darjnest.kinecare.core.designsystem.theme.KineCareSpacing
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.feature.professional_profile.presentation.util.etiquetaModalidad
import com.darjnest.kinecare.feature.professional_profile.presentation.util.explicacionEstado
import com.darjnest.kinecare.feature.professional_profile.presentation.util.formatearCalificacion
import com.darjnest.kinecare.feature.professional_profile.presentation.util.formatearFecha
import com.darjnest.kinecare.feature.professional_profile.presentation.util.formatearHora
import com.darjnest.kinecare.feature.professional_profile.presentation.util.nombreDia
import com.darjnest.kinecare.feature.professional_profile.presentation.util.textoDuracion
import com.darjnest.kinecare.feature.professional_profile.presentation.util.textoEstado
import com.darjnest.kinecare.feature.professional_profile.presentation.util.textoResenas
import com.darjnest.kinecare.feature.professional_profile.presentation.util.tituloInsignia
import com.darjnest.kinecare.feature.professional_profile.presentation.util.tonoEstado
import com.darjnest.kinecare.feature.professional_profile.presentation.viewmodel.HorarioDia
import com.darjnest.kinecare.feature.professional_profile.presentation.viewmodel.InsigniaPerfil
import com.darjnest.kinecare.feature.professional_profile.presentation.viewmodel.PerfilProfesional
import com.darjnest.kinecare.feature.professional_profile.presentation.viewmodel.ProfessionalProfileAction
import com.darjnest.kinecare.feature.professional_profile.presentation.viewmodel.ProfessionalProfileState
import com.darjnest.kinecare.feature.professional_profile.presentation.viewmodel.ProfessionalProfileViewModel
import com.darjnest.kinecare.feature.professional_profile.presentation.viewmodel.TurnoHorario
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

@Composable
fun ProfessionalProfileRoot(
    modifier: Modifier = Modifier,
    viewModel: ProfessionalProfileViewModel = hiltViewModel(),
    onVolver: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfessionalProfileScreen(
        state = state,
        onAction = { accion ->
            // Volver atras es navegacion, no estado del ViewModel: el Root la
            // resuelve directo contra el NavGraph.
            if (accion is ProfessionalProfileAction.VolverAtras) {
                onVolver()
            } else {
                viewModel.onAction(accion)
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalProfileScreen(
    state: ProfessionalProfileState,
    onAction: (ProfessionalProfileAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Perfil profesional") },
                navigationIcon = {
                    IconButton(onClick = { onAction(ProfessionalProfileAction.VolverAtras) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { innerPadding ->
        val contenidoModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when {
            state.cargando -> KineCareFullScreenLoading(modifier = contenidoModifier)
            state.error != null -> EstadoError(
                error = state.error,
                onReintentar = { onAction(ProfessionalProfileAction.Reintentar) },
                modifier = contenidoModifier,
            )
            state.perfil != null -> ContenidoPerfil(
                state = state,
                perfil = state.perfil,
                onAction = onAction,
                modifier = contenidoModifier,
            )
        }
    }
}

@Composable
private fun EstadoError(
    error: ProfesionalError,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(KineCareSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = mensajeDeError(error),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.padding(top = KineCareSpacing.l)) {
            KineCarePrimaryButton(text = "Reintentar", onClick = onReintentar)
        }
    }
}

internal fun mensajeDeError(error: ProfesionalError): String = when (error) {
    ProfesionalError.SIN_INTERNET -> "Sin conexión a internet. Revisa tu red e inténtalo de nuevo."
    ProfesionalError.NO_ENCONTRADO -> "No encontramos este perfil. Puede que ya no esté disponible."
    ProfesionalError.DESCONOCIDO -> "Ocurrió un error inesperado al cargar el perfil."
}

@Composable
private fun ContenidoPerfil(
    state: ProfessionalProfileState,
    perfil: PerfilProfesional,
    onAction: (ProfessionalProfileAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(KineCareSpacing.l),
        verticalArrangement = Arrangement.spacedBy(KineCareSpacing.l),
    ) {
        EncabezadoPerfil(perfil = perfil)

        SeccionInsignias(
            insignias = perfil.insignias,
            expandidas = state.insigniasExpandidas,
            onAlternar = { onAction(ProfessionalProfileAction.AlternarInsignia(it)) },
        )

        if (perfil.descripcion.isNotBlank()) {
            Seccion(titulo = "Acerca de") {
                Text(text = perfil.descripcion, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (perfil.servicios.isNotEmpty()) {
            Seccion(titulo = "Servicios") {
                perfil.servicios.forEachIndexed { indice, servicio ->
                    if (indice > 0) Box(modifier = Modifier.padding(top = KineCareSpacing.m))
                    FilaServicio(servicio = servicio)
                }
            }
        }

        if (perfil.horario.isNotEmpty()) {
            Seccion(titulo = "Horario de atención") {
                perfil.horario.forEach { FilaHorario(horario = it) }
            }
        }
    }
}

@Composable
private fun EncabezadoPerfil(perfil: PerfilProfesional) {
    KineCareCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(KineCareSpacing.avatarGrande)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = perfil.iniciales,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = KineCareSpacing.l),
                verticalArrangement = Arrangement.spacedBy(KineCareSpacing.xs),
            ) {
                Text(text = perfil.nombre, style = MaterialTheme.typography.titleLarge)
                if (perfil.especialidades.isNotEmpty()) {
                    Text(
                        text = perfil.especialidades.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (perfil.rnpi.isNotBlank()) {
                    Text(
                        text = "RNPI ${perfil.rnpi}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (perfil.calificacionPromedio != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = InicioDorado,
                            modifier = Modifier.size(KineCareSpacing.icono),
                        )
                        Text(
                            text = "${formatearCalificacion(perfil.calificacionPromedio)} " +
                                "(${textoResenas(perfil.totalResenas)})",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = KineCareSpacing.xs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Seccion(
    titulo: String,
    content: @Composable () -> Unit,
) {
    KineCareCard {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = KineCareSpacing.m),
        )
        content()
    }
}

@Composable
private fun SeccionInsignias(
    insignias: List<InsigniaPerfil>,
    expandidas: Set<TipoInsignia>,
    onAlternar: (TipoInsignia) -> Unit,
) {
    Seccion(titulo = "Verificaciones") {
        insignias.forEach { insignia ->
            FilaInsignia(
                insignia = insignia,
                expandida = insignia.tipo in expandidas,
                onAlternar = { onAlternar(insignia.tipo) },
            )
        }
    }
}

@Composable
private fun FilaInsignia(
    insignia: InsigniaPerfil,
    expandida: Boolean,
    onAlternar: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expandida) "Ocultar detalle" else "Ver detalle",
                    onClick = onAlternar,
                )
                .padding(vertical = KineCareSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tituloInsignia(insignia.tipo),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            KineCareBadge(texto = textoEstado(insignia.estado), tono = tonoEstado(insignia.estado))
            Icon(
                imageVector = if (expandida) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.padding(start = KineCareSpacing.s),
            )
        }
        AnimatedVisibility(visible = expandida) {
            Column(
                modifier = Modifier.padding(bottom = KineCareSpacing.m),
                verticalArrangement = Arrangement.spacedBy(KineCareSpacing.xs),
            ) {
                Text(
                    text = insignia.detalle ?: explicacionEstado(insignia.estado),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (insignia.fechaActualizacionMillis != null) {
                    Text(
                        text = "Actualizado el ${formatearFecha(insignia.fechaActualizacionMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilaServicio(servicio: Servicio) {
    Row(verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(KineCareSpacing.xs),
        ) {
            Text(
                text = servicio.nombre,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${etiquetaModalidad(servicio.modalidad)} · ${textoDuracion(servicio.duracionMinutos)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = servicio.precio.formatComoClp(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = KineCareSpacing.m),
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilaHorario(horario: HorarioDia) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KineCareSpacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = nombreDia(horario.dia),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(KineCareSpacing.s)) {
            horario.turnos.forEach { turno ->
                Text(
                    text = "${formatearHora(turno.inicio)} - ${formatearHora(turno.fin)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private val perfilDePrueba = PerfilProfesional(
    id = "prof-1",
    nombre = "Ana Soto Rojas",
    especialidades = listOf("Kinesiología", "Masoterapia"),
    rnpi = "123456",
    calificacionPromedio = 4.8,
    totalResenas = 32,
    descripcion = "Kinesióloga deportiva con 8 años de experiencia en rehabilitación de lesiones.",
    insignias = listOf(
        InsigniaPerfil(
            TipoInsignia.IDENTIDAD,
            EstadoVerificacion.APROBADO,
            "Identidad verificada con cédula.",
            1_772_000_000_000L,
        ),
        InsigniaPerfil(TipoInsignia.CREDENCIALES, EstadoVerificacion.PENDIENTE, null, 1_772_000_000_000L),
        InsigniaPerfil(TipoInsignia.AUTENTICIDAD, EstadoVerificacion.RECHAZADO, null, 1_772_000_000_000L),
        InsigniaPerfil(TipoInsignia.HISTORIAL, EstadoVerificacion.NO_SOLICITADO, null, null),
    ),
    servicios = listOf(
        Servicio("s1", "Kinesiología deportiva", "", ModalidadServicio.DOMICILIO, 60, 25_000L),
        Servicio("s2", "Masaje descontracturante", "", ModalidadServicio.CONSULTA, 45, 18_000L),
    ),
    horario = listOf(
        HorarioDia(DayOfWeek.MONDAY, listOf(TurnoHorario(LocalTime(9, 0), LocalTime(13, 0)))),
        HorarioDia(
            DayOfWeek.WEDNESDAY,
            listOf(
                TurnoHorario(LocalTime(9, 0), LocalTime(12, 0)),
                TurnoHorario(LocalTime(15, 0), LocalTime(18, 0)),
            ),
        ),
    ),
)

@Preview(showBackground = true)
@Composable
private fun ProfessionalProfileScreenPreview() {
    KineCareTheme {
        ProfessionalProfileScreen(
            state = ProfessionalProfileState(
                cargando = false,
                perfil = perfilDePrueba,
                insigniasExpandidas = setOf(TipoInsignia.IDENTIDAD, TipoInsignia.HISTORIAL),
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfessionalProfileScreenLoadingPreview() {
    KineCareTheme {
        ProfessionalProfileScreen(state = ProfessionalProfileState(cargando = true))
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfessionalProfileScreenErrorPreview() {
    KineCareTheme {
        ProfessionalProfileScreen(
            state = ProfessionalProfileState(cargando = false, error = ProfesionalError.SIN_INTERNET),
        )
    }
}
