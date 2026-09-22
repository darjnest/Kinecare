package com.darjnest.kinecare.feature.professional_panel.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.designsystem.theme.AzulPetroleo30
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.theme.LoginAzulSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginFondo
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisClaro
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisTexto
import com.darjnest.kinecare.core.designsystem.theme.LoginMenta
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimario
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimarioOscuro
import com.darjnest.kinecare.core.designsystem.theme.TextoPrincipal
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.AcreditacionOficial
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.DocumentoClinico
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.DocumentosYValidacionAction
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.DocumentosYValidacionState
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.DocumentosYValidacionViewModel
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.EstadoDocumento
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ResumenDocumental

@Composable
fun DocumentosYValidacionRoot(
    modifier: Modifier = Modifier,
    viewModel: DocumentosYValidacionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DocumentosYValidacionScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun DocumentosYValidacionScreen(
    state: DocumentosYValidacionState,
    onAction: (DocumentosYValidacionAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = LoginFondo,
        topBar = { EncabezadoDocumentos() },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LoginFondo)
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            state.acreditacion?.let { acreditacion ->
                TarjetaAcreditacionOficial(acreditacion = acreditacion, onAction = onAction)
                Spacer(modifier = Modifier.height(12.dp))
            }

            state.resumen?.let { resumen ->
                FilaMetricasResumen(resumen = resumen)
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (state.documentos.isNotEmpty()) {
                FilaExpedienteTitulo(totalValidados = state.documentos.size)
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.documentos.forEach { documento ->
                        TarjetaDocumentoClinico(
                            documento = documento,
                            onClick = { onAction(DocumentosYValidacionAction.VerDocumento(documento.id)) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            TarjetaNuevaCertificacion(onClick = { onAction(DocumentosYValidacionAction.SubirNuevaCertificacion) })

            Spacer(modifier = Modifier.height(16.dp))
            TarjetaMarcoJuridico()

            Spacer(modifier = Modifier.height(16.dp))
            FilaMesaLegal(onClick = { onAction(DocumentosYValidacionAction.ContactarMesaLegal) })

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EncabezadoDocumentos() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LoginFondo)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = TextoPrincipal)
        }
        Text(
            text = "Documentos y Validación",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextoPrincipal,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones", tint = LoginGrisTexto)
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(LoginGrisClaro),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, contentDescription = "Mi perfil", tint = LoginGrisTexto, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TarjetaAcreditacionOficial(
    acreditacion: AcreditacionOficial,
    onAction: (DocumentosYValidacionAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LoginPrimarioOscuro),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LoginMenta, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ACREDITACIÓN OFICIAL VIGENTE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.HealthAndSafety, contentDescription = null, tint = LoginMenta, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Registro Clínico Validado",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Habilitación profesional certificada ante la Superintendencia de Salud y Ministerio de Salud de Chile.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
            )

            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Registro RNPI Oficial",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LoginMenta, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (acreditacion.registroActivo) "Activo" else "Inactivo",
                            style = MaterialTheme.typography.labelSmall,
                            color = LoginMenta,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "SIS N° ${acreditacion.numeroSis} — ${acreditacion.especialidad}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.clickable { onAction(DocumentosYValidacionAction.ConsultarEnSuperSalud) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Consultar en supersalud.gob.cl",
                            style = MaterialTheme.typography.labelMedium,
                            color = LoginMenta,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = LoginMenta, modifier = Modifier.size(13.dp))
                    }
                    Row(
                        modifier = Modifier.clickable { onAction(DocumentosYValidacionAction.CopiarNumeroSis) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Copiar", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaMetricasResumen(resumen: ResumenDocumental) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TarjetaMetrica(
            modifier = Modifier.weight(1f),
            etiqueta = "Documentos",
            valor = "${resumen.documentosValidados} / ${resumen.documentosTotales}",
            valorColor = LoginPrimario,
            icono = Icons.Filled.DoneAll,
            subtitulo = if (resumen.documentosValidados == resumen.documentosTotales) "100% OK" else "En revisión",
        )
        TarjetaMetrica(
            modifier = Modifier.weight(1f),
            etiqueta = "Auditoría",
            valor = resumen.fechaAuditoria,
            valorColor = AzulPetroleo30,
            subtitulo = if (resumen.auditoriaSinReparos) "Sin reparos" else "Con observaciones",
        )
        TarjetaMetrica(
            modifier = Modifier.weight(1f),
            etiqueta = "Nivel KineCare",
            valor = resumen.nivelKineCare,
            valorColor = LoginPrimarioOscuro,
            subtitulo = resumen.nivelDescripcion,
        )
    }
}

@Composable
private fun TarjetaMetrica(
    etiqueta: String,
    valor: String,
    valorColor: Color,
    subtitulo: String,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LoginGrisClaro),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = etiqueta, style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = valor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valorColor)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                icono?.let {
                    Icon(it, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                }
                Text(text = subtitulo, style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
            }
        }
    }
}

@Composable
private fun FilaExpedienteTitulo(totalValidados: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FolderShared, contentDescription = null, tint = LoginPrimario, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Expediente Clínico & Legal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
            )
        }
        Text(
            text = "$totalValidados validados",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = LoginPrimario,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(LoginMenta)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun iconoPara(nombre: String): ImageVector = when {
    nombre.contains("Título", ignoreCase = true) -> Icons.Filled.School
    nombre.contains("Inscripción", ignoreCase = true) -> Icons.Filled.Apartment
    nombre.contains("Cédula", ignoreCase = true) -> Icons.Filled.Badge
    nombre.contains("Antecedentes", ignoreCase = true) -> Icons.Filled.Gavel
    else -> Icons.Filled.Shield
}

private fun colorEstado(estado: EstadoDocumento): Color = when (estado) {
    EstadoDocumento.VERIFICADO, EstadoDocumento.VIGENTE, EstadoDocumento.BIOMETRIA_OK -> LoginPrimario
    EstadoDocumento.AL_DIA -> AzulPetroleo30
}

private fun textoEstado(estado: EstadoDocumento): String = when (estado) {
    EstadoDocumento.VERIFICADO -> "Verificado"
    EstadoDocumento.BIOMETRIA_OK -> "Biometría OK"
    EstadoDocumento.AL_DIA -> "Al día"
    EstadoDocumento.VIGENTE -> "Vigente"
}

private fun iconoEstado(estado: EstadoDocumento): ImageVector = when (estado) {
    EstadoDocumento.BIOMETRIA_OK -> Icons.Filled.Fingerprint
    EstadoDocumento.AL_DIA -> Icons.Filled.EventAvailable
    else -> Icons.Filled.CheckCircle
}

@Composable
private fun TarjetaDocumentoClinico(
    documento: DocumentoClinico,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(modifier = Modifier.weight(1f, fill = false)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(LoginMenta),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(iconoPara(documento.nombre), contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = documento.nombre,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextoPrincipal,
                        )
                        Text(
                            text = documento.entidad,
                            style = MaterialTheme.typography.labelSmall,
                            color = LoginGrisTexto,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LoginMenta)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(iconoEstado(documento.estado), contentDescription = null, tint = colorEstado(documento.estado), modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = textoEstado(documento.estado),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorEstado(documento.estado),
                    )
                }
            }

            documento.notaTexto?.let { nota ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = nota, style = MaterialTheme.typography.bodyMedium, color = LoginGrisTexto)
            }

            if (documento.metadatoTexto != null && documento.accionTexto != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LoginGrisClaro)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Update, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = documento.metadatoTexto, style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                    }
                    Row(
                        modifier = Modifier.clickable(onClick = onClick),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = documento.accionTexto, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = LoginPrimario)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(iconoAccion(documento.accionTexto), contentDescription = null, tint = LoginPrimario, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

private fun iconoAccion(accionTexto: String): ImageVector = when {
    accionTexto.contains("Renovar", ignoreCase = true) -> Icons.Filled.Sync
    accionTexto.contains("Póliza", ignoreCase = true) -> Icons.Filled.Security
    accionTexto.contains("Constancia", ignoreCase = true) -> Icons.Filled.Description
    else -> Icons.Filled.Visibility
}

@Composable
private fun TarjetaNuevaCertificacion(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LoginGrisClaro),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(LoginPrimarioOscuro),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PostAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "¿Nueva Especialidad o Postítulo?",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                    )
                    Text(
                        text = "Diplomados, certificaciones MINSAL o cursos kinésicos.",
                        style = MaterialTheme.typography.labelSmall,
                        color = LoginGrisTexto,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(LoginPrimarioOscuro)
                    .clickable(onClick = onClick)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Subir Nueva Certificación", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun TarjetaMarcoJuridico() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LoginAzulSuave),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Filled.Gavel, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "MARCO JURÍDICO DE SALUD",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AzulPetroleo30,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Cumplimiento estricto con la Ley N° 20.584 de Derechos y Deberes de las Personas en Salud, normativa de ficha clínica electrónica confidencial y resguardo de datos sensibles en Chile.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LoginGrisTexto,
                )
            }
        }
    }
}

@Composable
private fun FilaMesaLegal(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(LoginAzulSuave),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = AzulPetroleo30, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "Mesa Legal & Auditoría", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                    Text(text = "Soporte clínico KineCare Chile", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                }
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(LoginGrisClaro)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(text = "Contactar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextoPrincipal)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun DocumentosYValidacionScreenPreview() {
    KineCareTheme {
        DocumentosYValidacionScreen(state = DocumentosYValidacionState())
    }
}
