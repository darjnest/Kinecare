package com.darjnest.kinecare.feature.client_panel.presentation.view

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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.theme.LoginFondo
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisClaro
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisTexto
import com.darjnest.kinecare.core.designsystem.theme.LoginMenta
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimario
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimarioOscuro
import com.darjnest.kinecare.core.designsystem.theme.TextoPrincipal
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.InformacionPersonalClienteAction
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.InformacionPersonalClienteState
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.InformacionPersonalClienteViewModel

@Composable
fun InformacionPersonalClienteRoot(
    modifier: Modifier = Modifier,
    viewModel: InformacionPersonalClienteViewModel = hiltViewModel(),
    onVolver: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Guardar Cambios es asincronico (escribe en `usuarios/{uid}`): solo
    // volvemos atras cuando el ViewModel confirma el guardado exitoso, en
    // vez de navegar de inmediato. Mismo patron que ya usa `AuthViewModel`
    // (expone el resultado en el estado y el Root reacciona con
    // `LaunchedEffect`; no hay un `Flow<Event>` separado en el proyecto).
    LaunchedEffect(state.guardadoExitoso) {
        if (state.guardadoExitoso) onVolver()
    }

    InformacionPersonalClienteScreen(
        state = state,
        onAction = { accion ->
            // Volver atras es navegacion pura (nada que guardar): el Root
            // la resuelve directo contra el NavGraph (ver
            // ClientPanelNavGraph). Guardar Cambios si pasa por el
            // ViewModel para escribir sobre el `Usuario` real antes de
            // volver (ver el `LaunchedEffect` de arriba).
            if (accion == InformacionPersonalClienteAction.VolverAtras) {
                onVolver()
            } else {
                viewModel.onAction(accion)
            }
        },
        modifier = modifier,
    )
}

@Composable
fun InformacionPersonalClienteScreen(
    state: InformacionPersonalClienteState,
    onAction: (InformacionPersonalClienteAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = LoginFondo,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LoginFondo)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            EncabezadoInformacionPersonal(onAction = onAction)

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TarjetaFotoPerfil(nombre = state.nombre, onAction = onAction)
                TarjetaDatosPersonales(state = state, onAction = onAction)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(LoginPrimarioOscuro)
                        .clickable { onAction(InformacionPersonalClienteAction.GuardarCambios) }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Guardar Cambios", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EncabezadoInformacionPersonal(onAction: (InformacionPersonalClienteAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { onAction(InformacionPersonalClienteAction.VolverAtras) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = TextoPrincipal)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Información Personal",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextoPrincipal,
        )
    }
}

@Composable
private fun TarjetaFotoPerfil(
    nombre: String,
    onAction: (InformacionPersonalClienteAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(LoginMenta),
                    contentAlignment = Alignment.Center,
                ) {
                    val iniciales = inicialesDeNombre(nombre)
                    if (iniciales.isNotBlank()) {
                        Text(
                            text = iniciales,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = LoginPrimarioOscuro,
                        )
                    } else {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(40.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(LoginPrimarioOscuro)
                        .clickable { onAction(InformacionPersonalClienteAction.CambiarFoto) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Cambiar foto", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Cambiar foto de perfil",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = LoginPrimarioOscuro,
                modifier = Modifier.clickable { onAction(InformacionPersonalClienteAction.CambiarFoto) },
            )
        }
    }
}

private fun inicialesDeNombre(nombre: String): String =
    nombre.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

@Composable
private fun TarjetaDatosPersonales(
    state: InformacionPersonalClienteState,
    onAction: (InformacionPersonalClienteAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Datos Personales",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
            )

            Spacer(modifier = Modifier.height(16.dp))
            EtiquetaCampoInfoPersonal(texto = "Nombre y Apellido")
            OutlinedTextField(
                value = state.nombre,
                onValueChange = { onAction(InformacionPersonalClienteAction.CambiarNombre(it)) },
                placeholder = { Text("Ej. Francisca Silva Méndez") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = LoginGrisTexto) },
                shape = RoundedCornerShape(16.dp),
                colors = coloresCampoInfoPersonal(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))
            EtiquetaCampoInfoPersonal(texto = "RUT")
            OutlinedTextField(
                value = state.rut,
                onValueChange = {},
                enabled = false,
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null, tint = LoginGrisTexto) },
                shape = RoundedCornerShape(16.dp),
                colors = coloresCampoInfoPersonal(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Text(
                text = "Tu RUT es tu identificador de acceso y no se puede modificar",
                style = MaterialTheme.typography.labelSmall,
                color = LoginGrisTexto,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))
            EtiquetaCampoInfoPersonal(texto = "Teléfono / WhatsApp")
            OutlinedTextField(
                value = state.telefono,
                onValueChange = { onAction(InformacionPersonalClienteAction.CambiarTelefono(it)) },
                placeholder = { Text("+56 9 8765 4321") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null, tint = LoginGrisTexto) },
                shape = RoundedCornerShape(16.dp),
                colors = coloresCampoInfoPersonal(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))
            EtiquetaCampoInfoPersonal(texto = "Correo de Contacto")
            OutlinedTextField(
                value = state.correoContacto,
                onValueChange = { onAction(InformacionPersonalClienteAction.CambiarCorreoContacto(it)) },
                placeholder = { Text("nombre@correo.cl") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = LoginGrisTexto) },
                shape = RoundedCornerShape(16.dp),
                colors = coloresCampoInfoPersonal(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun coloresCampoInfoPersonal() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = LoginPrimario,
    unfocusedBorderColor = Color.Transparent,
    disabledContainerColor = LoginGrisClaro,
    disabledBorderColor = Color.Transparent,
    disabledTextColor = LoginGrisTexto,
    disabledLeadingIconColor = LoginGrisTexto,
)

@Composable
private fun EtiquetaCampoInfoPersonal(texto: String) {
    Text(text = texto, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = TextoPrincipal)
}

@Preview(showBackground = true, heightDp = 1500)
@Composable
private fun InformacionPersonalClienteScreenPreview() {
    KineCareTheme {
        InformacionPersonalClienteScreen(
            state = InformacionPersonalClienteState(
                nombre = "Francisca Silva Méndez",
                rut = "12.345.678-9",
                telefono = "+56 9 8765 4321",
                correoContacto = "francisca.silva@correo.cl",
            ),
        )
    }
}
