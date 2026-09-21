package com.darjnest.kinecare.feature.auth.presentation.view

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.designsystem.components.button.KineCarePrimaryButton
import com.darjnest.kinecare.core.designsystem.components.card.KineCareCard
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.theme.LoginAzulSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginFondo
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisClaro
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisTexto
import com.darjnest.kinecare.core.designsystem.theme.LoginMenta
import com.darjnest.kinecare.core.designsystem.theme.LoginMentaSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimario
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimarioOscuro
import com.darjnest.kinecare.core.designsystem.theme.LoginSecundario
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
            .background(LoginFondo)
            .verticalScroll(rememberScrollState()),
    ) {
        if (usuario != null) {
            Column(modifier = Modifier.padding(24.dp)) {
                SesionIniciadaContenido(nombre = usuario.nombre, rol = usuario.rol, onCerrarSesion = { onAction(AuthAction.CerrarSesion) })
            }
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

@Composable
private fun FormularioAuthContenido(
    state: AuthState,
    onAction: (AuthAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LoginMenta),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        LogoKineCare()
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Bienvenido de nuevo",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF16241C),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Ingresa a tu cuenta para gestionar tus atenciones kinésicas y terapias activas.",
            style = MaterialTheme.typography.bodyMedium,
            color = LoginGrisTexto,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Spacer(modifier = Modifier.height(20.dp))

        SelectorRolPildora(seleccionado = state.rolSeleccionado, onAction = onAction)

        if (state.modo == ModoAuth.REGISTRO) {
            Spacer(modifier = Modifier.height(20.dp))
            EtiquetaCampo(texto = "Nombre")
            OutlinedTextField(
                value = state.nombre,
                onValueChange = { onAction(AuthAction.CambiarNombre(it)) },
                placeholder = { Text("Tu nombre completo") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = coloresCampo(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            EtiquetaCampo(texto = "RUT o Correo Electrónico")
            Text(text = "Chile", style = MaterialTheme.typography.bodyMedium, color = LoginGrisTexto)
        }
        OutlinedTextField(
            value = state.email,
            onValueChange = { onAction(AuthAction.CambiarEmail(it)) },
            placeholder = { Text("ej. 12.345.678-k o juan@email.com") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null, tint = LoginGrisTexto) },
            shape = RoundedCornerShape(16.dp),
            colors = coloresCampo(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EtiquetaCampo(texto = "Contraseña")
            Text(
                text = "¿Olvidaste tu contraseña?",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = LoginPrimario,
                modifier = Modifier.clickable {},
            )
        }
        var passwordVisible by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = state.password,
            onValueChange = { onAction(AuthAction.CambiarPassword(it)) },
            placeholder = { Text("••••••••••") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = LoginGrisTexto) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                        tint = LoginGrisTexto,
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp),
            colors = coloresCampo(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        if (state.mensajeError != null) {
            Text(
                text = state.mensajeError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        var recordarCuenta by remember { mutableStateOf(true) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = recordarCuenta,
                onCheckedChange = { recordarCuenta = it },
                colors = SwitchDefaults.colors(checkedTrackColor = LoginPrimarioOscuro),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Recordar mi cuenta en este dispositivo",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        if (state.cargando) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Button(
                onClick = { onAction(AuthAction.Enviar) },
                enabled = state.puedeEnviar,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LoginPrimarioOscuro, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = (if (state.modo == ModoAuth.LOGIN) "Iniciar sesión" else "Crear cuenta").uppercase(),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }

        if (state.modo == ModoAuth.LOGIN) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {},
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LoginMentaSuave, contentColor = LoginPrimarioOscuro),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Ingresar con Biometría / Huella", fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = "O INGRESA CON",
                style = MaterialTheme.typography.labelSmall,
                color = LoginGrisTexto,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {},
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LoginAzulSuave, contentColor = LoginSecundario),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(LoginSecundario),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "CU", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Continuar con ClaveÚnica", fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {},
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, LoginGrisClaro),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1A1C1B)),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(text = "G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Continuar con Google", fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            val textoPregunta = if (state.modo == ModoAuth.LOGIN) "¿Aún no tienes cuenta? " else "¿Ya tienes cuenta? "
            val textoAccion = if (state.modo == ModoAuth.LOGIN) "Regístrate aquí" else "Inicia sesión"
            Text(
                text = buildAnnotatedString {
                    append(textoPregunta)
                    withStyle(SpanStyle(color = LoginPrimario, fontWeight = FontWeight.Bold)) {
                        append(textoAccion)
                    }
                },
                modifier = Modifier.clickable {
                    val nuevoModo = if (state.modo == ModoAuth.LOGIN) ModoAuth.REGISTRO else ModoAuth.LOGIN
                    onAction(AuthAction.CambiarModo(nuevoModo))
                },
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = LoginGrisTexto,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Conexión cifrada de grado médico TLS 256 bits",
                style = MaterialTheme.typography.labelSmall,
                color = LoginGrisTexto,
            )
        }
    }
}

@Composable
private fun coloresCampo() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = LoginPrimario,
    unfocusedBorderColor = Color.Transparent,
)

@Composable
private fun EtiquetaCampo(texto: String) {
    Text(text = texto, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
}

@Composable
private fun LogoKineCare() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LoginPrimario),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "K", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = LoginSecundario, fontWeight = FontWeight.Bold)) { append("Kine") }
                withStyle(SpanStyle(color = LoginPrimario, fontWeight = FontWeight.Bold)) { append("Care") }
            },
            fontSize = 24.sp,
        )
    }
}

@Composable
private fun SelectorRolPildora(seleccionado: RolUsuario, onAction: (AuthAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(LoginGrisClaro)
            .padding(4.dp),
    ) {
        OpcionRol(
            texto = "Soy Paciente",
            icono = Icons.Filled.Person,
            seleccionado = seleccionado == RolUsuario.CLIENTE,
            onClick = { onAction(AuthAction.SeleccionarRol(RolUsuario.CLIENTE)) },
            modifier = Modifier.weight(1f),
        )
        OpcionRol(
            texto = "Soy Kinesiólogo",
            icono = Icons.Filled.Work,
            seleccionado = seleccionado == RolUsuario.PROFESIONAL,
            onClick = { onAction(AuthAction.SeleccionarRol(RolUsuario.PROFESIONAL)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OpcionRol(
    texto: String,
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (seleccionado) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icono,
            contentDescription = null,
            tint = if (seleccionado) Color(0xFF1A1C1B) else LoginGrisTexto,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = texto,
            color = if (seleccionado) Color(0xFF1A1C1B) else LoginGrisTexto,
            fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal,
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
