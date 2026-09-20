package com.darjnest.kinecare.feature.auth.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.designsystem.components.button.KineCarePrimaryButton
import com.darjnest.kinecare.core.designsystem.components.card.KineCareCard
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.feature.auth.presentation.viewmodel.AuthAction
import com.darjnest.kinecare.feature.auth.presentation.viewmodel.AuthState
import com.darjnest.kinecare.feature.auth.presentation.viewmodel.AuthViewModel
import com.darjnest.kinecare.feature.auth.presentation.viewmodel.ModoAuth

@Composable
fun AuthRoot(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScreen(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun AuthScreen(
    state: AuthState,
    onAction: (AuthAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val usuario = state.usuarioAutenticado
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (usuario != null) {
            SesionIniciadaContenido(nombre = usuario.nombre, rol = usuario.rol, onCerrarSesion = { onAction(AuthAction.CerrarSesion) })
        } else {
            FormularioAuthContenido(state = state, onAction = onAction)
        }
    }
}

@Composable
private fun SesionIniciadaContenido(
    nombre: String,
    rol: RolUsuario,
    onCerrarSesion: () -> Unit,
) {
    KineCareCard {
        Text("Sesión iniciada", style = MaterialTheme.typography.titleMedium)
        Text("$nombre — ${if (rol == RolUsuario.CLIENTE) "Cliente" else "Profesional"}")
    }
    KineCarePrimaryButton(
        text = "Cerrar sesión",
        onClick = onCerrarSesion,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormularioAuthContenido(
    state: AuthState,
    onAction: (AuthAction) -> Unit,
) {
    Text(
        text = if (state.modo == ModoAuth.LOGIN) "Iniciar sesión" else "Crear cuenta",
        style = MaterialTheme.typography.titleLarge,
    )

    if (state.modo == ModoAuth.REGISTRO) {
        OutlinedTextField(
            value = state.nombre,
            onValueChange = { onAction(AuthAction.CambiarNombre(it)) },
            label = { Text("Nombre") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        )

        SelectorRol(seleccionado = state.rolSeleccionado, onAction = onAction)
    }

    OutlinedTextField(
        value = state.email,
        onValueChange = { onAction(AuthAction.CambiarEmail(it)) },
        label = { Text("Email") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    )

    OutlinedTextField(
        value = state.password,
        onValueChange = { onAction(AuthAction.CambiarPassword(it)) },
        label = { Text("Contraseña") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    )

    if (state.mensajeError != null) {
        Text(
            text = state.mensajeError,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    if (state.cargando) {
        CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
    } else {
        KineCarePrimaryButton(
            text = if (state.modo == ModoAuth.LOGIN) "Iniciar sesión" else "Crear cuenta",
            onClick = { onAction(AuthAction.Enviar) },
            enabled = state.puedeEnviar,
            modifier = Modifier.padding(top = 24.dp),
        )
    }

    TextButton(
        onClick = {
            val nuevoModo = if (state.modo == ModoAuth.LOGIN) ModoAuth.REGISTRO else ModoAuth.LOGIN
            onAction(AuthAction.CambiarModo(nuevoModo))
        },
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(
            if (state.modo == ModoAuth.LOGIN) "¿No tienes cuenta? Regístrate" else "¿Ya tienes cuenta? Inicia sesión",
        )
    }
}

@Composable
private fun SelectorRol(seleccionado: RolUsuario, onAction: (AuthAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = seleccionado == RolUsuario.CLIENTE,
            onClick = { onAction(AuthAction.SeleccionarRol(RolUsuario.CLIENTE)) },
            label = { Text("Cliente") },
        )
        FilterChip(
            selected = seleccionado == RolUsuario.PROFESIONAL,
            onClick = { onAction(AuthAction.SeleccionarRol(RolUsuario.PROFESIONAL)) },
            label = { Text("Profesional") },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthScreenLoginPreview() {
    KineCareTheme {
        AuthScreen(state = AuthState(), onAction = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthScreenRegistroPreview() {
    KineCareTheme {
        AuthScreen(state = AuthState(modo = ModoAuth.REGISTRO), onAction = {})
    }
}
