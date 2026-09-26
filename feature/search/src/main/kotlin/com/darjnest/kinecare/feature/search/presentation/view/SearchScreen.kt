package com.darjnest.kinecare.feature.search.presentation.view

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.designsystem.components.bar.KineCareBottomNavBar
import com.darjnest.kinecare.core.designsystem.components.bar.PestanaClienteInferior
import com.darjnest.kinecare.core.designsystem.components.label.BadgeTono
import com.darjnest.kinecare.core.designsystem.components.label.KineCareBadge
import com.darjnest.kinecare.core.designsystem.theme.InicioDorado
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.theme.LoginAzulSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginFondo
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisClaro
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisTexto
import com.darjnest.kinecare.core.designsystem.theme.LoginMenta
import com.darjnest.kinecare.core.designsystem.theme.LoginMentaSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimario
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimarioOscuro
import com.darjnest.kinecare.feature.search.presentation.viewmodel.CategoriaServicio
import com.darjnest.kinecare.feature.search.presentation.viewmodel.ModalidadAtencion
import com.darjnest.kinecare.feature.search.presentation.viewmodel.ProfesionalDestacado
import com.darjnest.kinecare.feature.search.presentation.viewmodel.SearchAction
import com.darjnest.kinecare.feature.search.presentation.viewmodel.SearchState
import com.darjnest.kinecare.feature.search.presentation.viewmodel.SearchViewModel
import com.darjnest.kinecare.feature.search.presentation.viewmodel.TipoAtencion

@Composable
fun SearchRoot(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    onCerrarSesion: () -> Unit = {},
    onIrAMisCitas: () -> Unit = {},
    onIrAFavoritos: () -> Unit = {},
    onIrAMiPerfil: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        onAction = viewModel::onAction,
        onCerrarSesion = onCerrarSesion,
        onIrAMisCitas = onIrAMisCitas,
        onIrAFavoritos = onIrAFavoritos,
        onIrAMiPerfil = onIrAMiPerfil,
        modifier = modifier,
    )
}

@Composable
fun SearchScreen(
    state: SearchState,
    onAction: (SearchAction) -> Unit = {},
    onCerrarSesion: () -> Unit = {},
    onIrAMisCitas: () -> Unit = {},
    onIrAFavoritos: () -> Unit = {},
    onIrAMiPerfil: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = LoginFondo,
        bottomBar = {
            KineCareBottomNavBar(
                pestanaActiva = PestanaClienteInferior.EXPLORAR,
                onExplorar = {},
                onMisCitas = onIrAMisCitas,
                onFavoritos = onIrAFavoritos,
                onMiPerfil = onIrAMiPerfil,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LoginFondo)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            EncabezadoInicio(onCerrarSesion = onCerrarSesion)

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(modifier = Modifier.height(16.dp))
                ChipCertificacion()

                Spacer(modifier = Modifier.height(12.dp))
                FilaTituloEnVivo()

                Text(
                    text = "Tu bienestar, en buenas manos.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LoginGrisTexto,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(modifier = Modifier.height(20.dp))
                TarjetaFiltros(state = state, onAction = onAction)

                Spacer(modifier = Modifier.height(28.dp))
                FilaServiciosCercaDeTi(ubicacion = state.ubicacion, onAction = onAction)

                Spacer(modifier = Modifier.height(16.dp))
                FilaModalidad(modalidad = state.modalidad, onAction = onAction)

                Spacer(modifier = Modifier.height(16.dp))
                GrillaCategorias(categorias = state.categorias, onAction = onAction)

                Spacer(modifier = Modifier.height(28.dp))
                FilaDestacadosTitulo()

                Spacer(modifier = Modifier.height(12.dp))
                if (state.cargandoProfesionales) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LoginPrimarioOscuro)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.profesionalesDestacados.forEach { profesional ->
                            TarjetaProfesionalDestacado(profesional = profesional, onAction = onAction)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                BannerCompromisoClinico()

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EncabezadoInicio(onCerrarSesion: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LoginPrimarioOscuro),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "K", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "KineCare",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF16241C),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.NotificationsNone,
                contentDescription = "Notificaciones",
                tint = Color(0xFF16241C),
            )
            Spacer(modifier = Modifier.width(12.dp))
            MenuPerfil(onCerrarSesion = onCerrarSesion)
        }
    }
}

@Composable
private fun MenuPerfil(onCerrarSesion: () -> Unit) {
    var menuExpandido by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(LoginGrisClaro)
                .clickable { menuExpandido = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, contentDescription = "Mi perfil", tint = LoginGrisTexto, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = menuExpandido, onDismissRequest = { menuExpandido = false }) {
            DropdownMenuItem(
                text = { Text("Cerrar sesión") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                onClick = {
                    menuExpandido = false
                    onCerrarSesion()
                },
            )
        }
    }
}

@Composable
private fun ChipCertificacion() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(LoginMentaSuave)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "ATENCIÓN KINÉSICA CERTIFICADA",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = LoginPrimarioOscuro,
        )
    }
}

@Composable
private fun FilaTituloEnVivo() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "KineCare",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF16241C),
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(LoginMenta)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(LoginPrimario),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "En vivo hoy",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = LoginPrimarioOscuro,
            )
        }
    }
}

@Composable
private fun TarjetaFiltros(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
) {
    val context = LocalContext.current
    val lanzadorPermisoUbicacion = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        if (concedido) onAction(SearchAction.ObtenerUbicacionActual)
    }
    val solicitarUbicacionActual = {
        val tienePermiso = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (tienePermiso) {
            onAction(SearchAction.ObtenerUbicacionActual)
        } else {
            lanzadorPermisoUbicacion.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "¿Qué atención necesitas hoy?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF16241C),
            )
            Text(
                text = "Kinesiólogos y terapeutas a un toque",
                style = MaterialTheme.typography.bodyMedium,
                color = LoginGrisTexto,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(LoginGrisClaro)
                    .padding(4.dp),
            ) {
                OpcionTipoAtencion(
                    texto = "Kinesiología",
                    icono = Icons.AutoMirrored.Filled.DirectionsRun,
                    seleccionado = state.tipoAtencion == TipoAtencion.KINESIOLOGIA,
                    onClick = { onAction(SearchAction.CambiarTipoAtencion(TipoAtencion.KINESIOLOGIA)) },
                    modifier = Modifier.weight(1f),
                )
                OpcionTipoAtencion(
                    texto = "Masoterapia",
                    icono = Icons.Filled.Spa,
                    seleccionado = state.tipoAtencion == TipoAtencion.MASOTERAPIA,
                    onClick = { onAction(SearchAction.CambiarTipoAtencion(TipoAtencion.MASOTERAPIA)) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "UBICACIÓN",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = LoginGrisTexto,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(LoginGrisClaro)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = state.ubicacion,
                        onValueChange = { onAction(SearchAction.CambiarUbicacionManual(it)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF16241C)),
                        cursorBrush = SolidColor(LoginPrimarioOscuro),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(LoginAzulSuave)
                        .clickable(enabled = !state.obteniendoUbicacion) { solicitarUbicacionActual() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.obteniendoUbicacion) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = LoginPrimarioOscuro,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Filled.MyLocation, contentDescription = "Usar mi ubicación", tint = LoginPrimarioOscuro)
                    }
                }
            }
            if (state.errorUbicacion != null) {
                Text(
                    text = state.errorUbicacion,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Solo perfiles verificados",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16241C),
                        )
                        Text(
                            text = "Identidad y título acreditado",
                            style = MaterialTheme.typography.labelSmall,
                            color = LoginGrisTexto,
                        )
                    }
                }
                Switch(
                    checked = state.soloVerificados,
                    onCheckedChange = { onAction(SearchAction.CambiarSoloVerificados(it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = LoginPrimarioOscuro),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(LoginPrimarioOscuro)
                    .clickable { onAction(SearchAction.Buscar) }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "BUSCAR ATENCIÓN",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun OpcionTipoAtencion(
    texto: String,
    icono: ImageVector,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (seleccionado) LoginPrimarioOscuro else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icono,
            contentDescription = null,
            tint = if (seleccionado) Color.White else LoginGrisTexto,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = texto,
            color = if (seleccionado) Color.White else LoginGrisTexto,
            fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FilaServiciosCercaDeTi(ubicacion: String, onAction: (SearchAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Servicios cerca de ti",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF16241C),
            )
            Text(
                text = "Disponibilidad en ${ubicacion.substringBefore(",")} y alrededores",
                style = MaterialTheme.typography.bodyMedium,
                color = LoginGrisTexto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Ver todos",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = LoginPrimario,
            modifier = Modifier.clickable { onAction(SearchAction.VerTodasLasCategorias) },
        )
    }
}

@Composable
private fun FilaModalidad(
    modalidad: ModalidadAtencion,
    onAction: (SearchAction) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OpcionModalidad(
            texto = "A domicilio",
            icono = Icons.Filled.Home,
            seleccionado = modalidad == ModalidadAtencion.A_DOMICILIO,
            onClick = { onAction(SearchAction.CambiarModalidad(ModalidadAtencion.A_DOMICILIO)) },
        )
        OpcionModalidad(
            texto = "En consulta",
            icono = Icons.Filled.LocalHospital,
            seleccionado = modalidad == ModalidadAtencion.EN_CONSULTA,
            onClick = { onAction(SearchAction.CambiarModalidad(ModalidadAtencion.EN_CONSULTA)) },
        )
    }
}

@Composable
private fun OpcionModalidad(
    texto: String,
    icono: ImageVector,
    seleccionado: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .then(
                if (seleccionado) {
                    Modifier.border(1.dp, LoginGrisClaro, RoundedCornerShape(50))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icono,
            contentDescription = null,
            tint = if (seleccionado) LoginPrimarioOscuro else LoginGrisTexto,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = texto,
            color = if (seleccionado) Color(0xFF16241C) else LoginGrisTexto,
            fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun GrillaCategorias(
    categorias: List<CategoriaServicio>,
    onAction: (SearchAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        categorias.chunked(2).forEach { fila ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                fila.forEach { categoria ->
                    TarjetaCategoria(
                        categoria = categoria,
                        onClick = { onAction(SearchAction.SeleccionarCategoria(categoria.id)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (fila.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TarjetaCategoria(
    categoria: CategoriaServicio,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(categoria.colorFondo),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(categoria.icono, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(20.dp))
                }
                KineCareBadge(texto = "${categoria.profesionalesActivos} activos", tono = BadgeTono.NEUTRO)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = categoria.nombre,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF16241C),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = categoria.descripcion,
                style = MaterialTheme.typography.labelSmall,
                color = LoginGrisTexto,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Desde ${formatearClp(categoria.precioDesde)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = LoginPrimarioOscuro,
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = LoginPrimarioOscuro,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun FilaDestacadosTitulo() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "Destacados en tu comuna",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF16241C),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "SUPERINTENDENCIA", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
            Text(text = "VERIFICADA", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
        }
    }
}

@Composable
private fun TarjetaProfesionalDestacado(
    profesional: ProfesionalDestacado,
    onAction: (SearchAction) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAction(SearchAction.SeleccionarProfesional(profesional.id)) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(52.dp)) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(LoginMenta),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(28.dp))
                }
                if (profesional.verificado) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Verificado",
                            tint = LoginPrimario,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profesional.nombre,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF16241C),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    KineCareBadge(texto = profesional.rnpi, tono = BadgeTono.EXITO)
                }
                Text(
                    text = profesional.especialidad,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LoginGrisTexto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = InicioDorado, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${profesional.calificacion} (${profesional.totalResenas})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF16241C),
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.labelSmall,
                        color = LoginGrisTexto,
                    )
                    Text(
                        text = formatearClp(profesional.precioDesde),
                        style = MaterialTheme.typography.labelSmall,
                        color = LoginGrisTexto,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(LoginGrisClaro),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Ver perfil",
                    tint = Color(0xFF16241C),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun BannerCompromisoClinico() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LoginMentaSuave)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(LoginMenta),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "COMPROMISO CLÍNICO • CHILE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = LoginPrimarioOscuro,
            )
            Text(
                text = "100% de profesionales acreditados en Superintendencia de Salud y Registro Civil.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF16241C),
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "Cada kinesiólogo cuenta con registro prestador individual verificado antes de su primera atención.",
                style = MaterialTheme.typography.bodySmall,
                color = LoginGrisTexto,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun formatearClp(monto: Long): String {
    val agrupado = monto.toString()
        .reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()
    return "CLP $$agrupado"
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun SearchScreenPreview() {
    KineCareTheme {
        SearchScreen(state = SearchState())
    }
}
