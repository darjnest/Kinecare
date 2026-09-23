package com.darjnest.kinecare.feature.client_panel.presentation.view

import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.designsystem.components.bar.KineCareBottomNavBar
import com.darjnest.kinecare.core.designsystem.components.bar.PestanaClienteInferior
import com.darjnest.kinecare.core.designsystem.theme.InicioDorado
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.theme.LoginFondo
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisClaro
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisTexto
import com.darjnest.kinecare.core.designsystem.theme.LoginMenta
import com.darjnest.kinecare.core.designsystem.theme.LoginMentaSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimario
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimarioOscuro
import com.darjnest.kinecare.core.designsystem.theme.TextoPrincipal
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.FavoritosAction
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.FavoritosState
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.FavoritosViewModel
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.FiltroFavoritos
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.ProfesionalFavorito

@Composable
fun FavoritosRoot(
    modifier: Modifier = Modifier,
    viewModel: FavoritosViewModel = hiltViewModel(),
    onIrAExplorar: () -> Unit = {},
    onIrAMisCitas: () -> Unit = {},
    onIrAMiPerfil: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FavoritosScreen(
        state = state,
        onAction = viewModel::onAction,
        onIrAExplorar = onIrAExplorar,
        onIrAMisCitas = onIrAMisCitas,
        onIrAMiPerfil = onIrAMiPerfil,
        modifier = modifier,
    )
}

@Composable
fun FavoritosScreen(
    state: FavoritosState,
    onAction: (FavoritosAction) -> Unit = {},
    onIrAExplorar: () -> Unit = {},
    onIrAMisCitas: () -> Unit = {},
    onIrAMiPerfil: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val favoritosFiltrados = state.favoritos.filter { favorito ->
        when (state.filtroSeleccionado) {
            FiltroFavoritos.TODOS -> true
            FiltroFavoritos.DOMICILIO -> favorito.modalidad == ModalidadServicio.DOMICILIO
            FiltroFavoritos.CONSULTA -> favorito.modalidad == ModalidadServicio.CONSULTA
            FiltroFavoritos.KINESIOLOGIA -> favorito.especialidad.contains("kinesi", ignoreCase = true)
            FiltroFavoritos.MASOTERAPIA -> favorito.especialidad.contains("masoter", ignoreCase = true)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = LoginFondo,
        bottomBar = {
            KineCareBottomNavBar(
                pestanaActiva = PestanaClienteInferior.FAVORITOS,
                onExplorar = onIrAExplorar,
                onMisCitas = onIrAMisCitas,
                onFavoritos = {},
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
            EncabezadoFavoritos()

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FilaRedPreferente(totalFavoritos = state.favoritos.size)

                Column {
                    Text(
                        text = "Mis Kinesiólogos Guardados",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                    )
                    Text(
                        text = "Acceso directo a tus terapeutas de cabecera con disponibilidad prioritaria.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LoginGrisTexto,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                FilaFiltrosFavoritos(
                    filtroSeleccionado = state.filtroSeleccionado,
                    totalFavoritos = state.favoritos.size,
                    onAction = onAction,
                )

                if (favoritosFiltrados.isEmpty()) {
                    EstadoVacioFavoritos()
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        favoritosFiltrados.forEach { favorito ->
                            if (favorito.esTerapeutaPrincipal) {
                                TarjetaFavoritoDestacado(favorito = favorito, onAction = onAction)
                            } else {
                                TarjetaFavoritoSecundario(favorito = favorito, onAction = onAction)
                            }
                        }
                    }
                }

                TarjetaEquipoHabitual()

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EncabezadoFavoritos() {
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
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "KINECARE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = LoginPrimario,
                )
                Text(
                    text = "Favoritos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsNone, contentDescription = "Notificaciones", tint = TextoPrincipal)
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(LoginGrisClaro),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, contentDescription = "Mi perfil", tint = LoginGrisTexto, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun FilaRedPreferente(totalFavoritos: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(LoginMentaSuave)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Red Preferente",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = LoginPrimarioOscuro,
            )
        }
        Text(
            text = "$totalFavoritos profesionales",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = LoginGrisTexto,
        )
    }
}

@Composable
private fun FilaFiltrosFavoritos(
    filtroSeleccionado: FiltroFavoritos,
    totalFavoritos: Int,
    onAction: (FavoritosAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChipFiltroFavoritos(
            texto = "Todos ($totalFavoritos)",
            icono = Icons.Filled.Verified,
            seleccionado = filtroSeleccionado == FiltroFavoritos.TODOS,
            onClick = { onAction(FavoritosAction.SeleccionarFiltro(FiltroFavoritos.TODOS)) },
        )
        ChipFiltroFavoritos(
            texto = "A Domicilio",
            icono = Icons.Filled.Home,
            seleccionado = filtroSeleccionado == FiltroFavoritos.DOMICILIO,
            onClick = { onAction(FavoritosAction.SeleccionarFiltro(FiltroFavoritos.DOMICILIO)) },
        )
        ChipFiltroFavoritos(
            texto = "En Consulta",
            icono = Icons.Filled.Apartment,
            seleccionado = filtroSeleccionado == FiltroFavoritos.CONSULTA,
            onClick = { onAction(FavoritosAction.SeleccionarFiltro(FiltroFavoritos.CONSULTA)) },
        )
        ChipFiltroFavoritos(
            texto = "Kinesiología",
            icono = Icons.Filled.LocalHospital,
            seleccionado = filtroSeleccionado == FiltroFavoritos.KINESIOLOGIA,
            onClick = { onAction(FavoritosAction.SeleccionarFiltro(FiltroFavoritos.KINESIOLOGIA)) },
        )
        ChipFiltroFavoritos(
            texto = "Masoterapia",
            icono = Icons.Filled.Spa,
            seleccionado = filtroSeleccionado == FiltroFavoritos.MASOTERAPIA,
            onClick = { onAction(FavoritosAction.SeleccionarFiltro(FiltroFavoritos.MASOTERAPIA)) },
        )
    }
}

@Composable
private fun ChipFiltroFavoritos(
    texto: String,
    icono: ImageVector,
    seleccionado: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (seleccionado) LoginPrimarioOscuro else LoginGrisClaro)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (seleccionado) Color.White else LoginGrisTexto,
        )
    }
}

@Composable
private fun EstadoVacioFavoritos() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.HeartBroken, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Aún no tienes profesionales guardados",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = TextoPrincipal,
        )
        Text(
            text = "Guarda a tus kinesiólogos de confianza para agendar más rápido.",
            style = MaterialTheme.typography.labelSmall,
            color = LoginGrisTexto,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun TarjetaFavoritoDestacado(
    favorito: ProfesionalFavorito,
    onAction: (FavoritosAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(LoginMenta),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(32.dp))
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(LoginPrimarioOscuro),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Verified, contentDescription = "Verificado", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = InicioDorado, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Terapeuta Principal",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = InicioDorado,
                        )
                    }
                    Text(
                        text = favorito.nombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = favorito.especialidad,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LoginGrisTexto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = favorito.universidad,
                        style = MaterialTheme.typography.labelSmall,
                        color = LoginGrisTexto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LoginMentaSuave)
                        .clickable { onAction(FavoritosAction.QuitarDeFavoritos(favorito.id)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = "Quitar de favoritos", tint = LoginPrimarioOscuro, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (favorito.identidadVerificada) {
                    EtiquetaInsignia(texto = "Identidad Verificada", icono = Icons.Filled.Shield)
                }
                if (favorito.numeroRegistroSis.isNotBlank()) {
                    EtiquetaInsignia(
                        texto = "SIS N° ${favorito.numeroRegistroSis}",
                        icono = Icons.Filled.Sell,
                        fuenteMonoespaciada = true,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = InicioDorado, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${favorito.calificacion} (${favorito.totalResenas} reseñas)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextoPrincipal,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Valor sesión", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
                    Text(
                        text = formatearClp(favorito.precioSesion),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimarioOscuro,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PinDrop, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${etiquetaModalidad(favorito.modalidad)} (${favorito.lugarAtencion})",
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            favorito.ultimaAtencionTexto?.let { ultimaAtencion ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(LoginGrisClaro)
                        .padding(10.dp),
                ) {
                    Icon(Icons.Filled.EventNote, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Última atención: $ultimaAtencion",
                        style = MaterialTheme.typography.labelSmall,
                        color = LoginGrisTexto,
                    )
                }
            }

            favorito.proximoCupoTexto?.let { proximoCupo ->
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LoginMentaSuave)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(LoginPrimario))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Próximo cupo disponible: $proximoCupo",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LoginPrimarioOscuro,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(LoginPrimarioOscuro)
                    .clickable { onAction(FavoritosAction.AgendarConProfesional(favorito.id)) }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Agendar Ahora", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun EtiquetaInsignia(
    texto: String,
    icono: ImageVector,
    fuenteMonoespaciada: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(LoginGrisClaro)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = texto,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontFamily = if (fuenteMonoespaciada) FontFamily.Monospace else FontFamily.Default,
            color = TextoPrincipal,
        )
    }
}

@Composable
private fun TarjetaFavoritoSecundario(
    favorito: ProfesionalFavorito,
    onAction: (FavoritosAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(LoginMenta),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = favorito.nombre,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = favorito.especialidad,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LoginGrisTexto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = favorito.universidad,
                        style = MaterialTheme.typography.labelSmall,
                        color = LoginGrisTexto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(LoginGrisClaro)
                        .clickable { onAction(FavoritosAction.QuitarDeFavoritos(favorito.id)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.FavoriteBorder, contentDescription = "Quitar de favoritos", tint = LoginPrimarioOscuro, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EtiquetaInsignia(
                    texto = if (favorito.identidadVerificada) "Credenciales Validadas" else "SIS Verificado",
                    icono = Icons.Filled.VerifiedUser,
                )
                favorito.insigniaSecundariaTexto?.let { insignia ->
                    EtiquetaInsignia(texto = insignia, icono = Icons.Filled.Shield)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = InicioDorado, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${favorito.calificacion} (${favorito.totalResenas})",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextoPrincipal,
                    )
                }
                Text(
                    text = "Tarifa base ${formatearClp(favorito.precioSesion)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = LoginPrimarioOscuro,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (favorito.modalidad == ModalidadServicio.DOMICILIO) Icons.Filled.Home else Icons.Filled.Apartment,
                    contentDescription = null,
                    tint = LoginGrisTexto,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${etiquetaModalidad(favorito.modalidad)} (${favorito.lugarAtencion})",
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = favorito.proximoCupoTexto ?: "Sin cupos próximos",
                    style = MaterialTheme.typography.labelSmall,
                    color = LoginGrisTexto,
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LoginGrisClaro)
                        .clickable { onAction(FavoritosAction.AgendarConProfesional(favorito.id)) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(text = "Agendar Cita", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextoPrincipal)
                }
            }
        }
    }
}

@Composable
private fun TarjetaEquipoHabitual() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LoginGrisClaro)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "Tu equipo de salud habitual",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
            )
            Text(
                text = "Tus datos de salud y preferencias terapéuticas están protegidos conforme a la Ley 20.584 de Deberes y Derechos de los Pacientes en Chile.",
                style = MaterialTheme.typography.labelSmall,
                color = LoginGrisTexto,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun etiquetaModalidad(modalidad: ModalidadServicio): String = when (modalidad) {
    ModalidadServicio.DOMICILIO -> "A Domicilio"
    ModalidadServicio.CONSULTA -> "En Consulta"
    ModalidadServicio.ONLINE -> "Online"
}

private fun formatearClp(monto: Long): String {
    val agrupado = monto.toString()
        .reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()
    return "CLP $$agrupado"
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun FavoritosScreenPreview() {
    KineCareTheme {
        FavoritosScreen(state = FavoritosState())
    }
}
