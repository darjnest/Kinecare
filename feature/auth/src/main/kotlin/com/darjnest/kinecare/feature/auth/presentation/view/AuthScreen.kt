package com.darjnest.kinecare.feature.auth.presentation.view

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.designsystem.components.button.KineCarePrimaryButton
import com.darjnest.kinecare.core.designsystem.components.card.KineCareCard
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.theme.LoginFondo
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisClaro
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisTexto
import com.darjnest.kinecare.core.designsystem.theme.LoginMenta
import com.darjnest.kinecare.core.designsystem.theme.LoginMentaSuave
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimario
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimarioOscuro
import com.darjnest.kinecare.core.designsystem.theme.LoginSecundario
import com.darjnest.kinecare.feature.auth.data.google.GoogleAuthManager
import com.darjnest.kinecare.feature.auth.presentation.viewmodel.AuthAction
import com.darjnest.kinecare.feature.auth.presentation.viewmodel.AuthState
import com.darjnest.kinecare.feature.auth.presentation.viewmodel.AuthViewModel
import com.darjnest.kinecare.feature.auth.presentation.viewmodel.ModoAuth
import com.darjnest.kinecare.feature.auth.presentation.viewmodel.PerfilGooglePendiente
import kotlinx.coroutines.launch

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
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        if (usuario != null) {
            Column(modifier = Modifier.padding(24.dp)) {
                SesionIniciadaContenido(nombre = usuario.nombre, rol = usuario.rol, onCerrarSesion = { onAction(AuthAction.CerrarSesion) })
            }
        } else if (state.perfilGooglePendiente != null) {
            FormularioCompletarPerfilGoogle(state = state, onAction = onAction)
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleAuthManager = remember { GoogleAuthManager() }

    EncabezadoAuth(modo = state.modo, onAction = onAction)

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Spacer(modifier = Modifier.height(20.dp))
        SelectorRol(seleccionado = state.rolSeleccionado, modo = state.modo, onAction = onAction)

        CamposFormulario(state = state, onAction = onAction)

        Spacer(modifier = Modifier.height(20.dp))
        BotonEnviar(state = state, onAction = onAction)

        Spacer(modifier = Modifier.height(20.dp))
        DivisorOIngresaCon(modo = state.modo)

        Spacer(modifier = Modifier.height(20.dp))
        BotonGoogle(
            modo = state.modo,
            habilitado = !state.cargando,
            onClick = {
                onAction(AuthAction.IniciandoGoogle)
                scope.launch {
                    try {
                        val idToken = googleAuthManager.obtenerIdToken(context)
                        onAction(AuthAction.IniciarSesionConGoogle(idToken))
                    } catch (e: GetCredentialCancellationException) {
                        onAction(AuthAction.ErrorGenerico(null))
                    } catch (e: Exception) {
                        onAction(AuthAction.ErrorGenerico("No pudimos continuar con Google. Intenta de nuevo."))
                    }
                }
            },
        )

        if (state.modo == ModoAuth.REGISTRO) {
            Spacer(modifier = Modifier.height(20.dp))
            BannerLey20584()

            Spacer(modifier = Modifier.height(16.dp))
            FooterRegistro(state = state, onAction = onAction)
        } else {
            Spacer(modifier = Modifier.height(24.dp))
            FooterRegistro(state = state, onAction = onAction)

            Spacer(modifier = Modifier.height(20.dp))
            FooterSeguridad()
        }
    }
}

@Composable
private fun FormularioCompletarPerfilGoogle(
    state: AuthState,
    onAction: (AuthAction) -> Unit,
) {
    val correoGoogle = state.perfilGooglePendiente?.correoGoogle.orEmpty()

    EncabezadoRegistro(
        titulo = "Completa tu perfil",
        subtitulo = "Ya validamos tu cuenta de Google ($correoGoogle). Solo nos faltan estos datos.",
        onVolver = { onAction(AuthAction.CancelarPerfilGoogle) },
    )

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Spacer(modifier = Modifier.height(20.dp))
        SelectorRolTarjetas(seleccionado = state.rolSeleccionado, onAction = onAction)

        Spacer(modifier = Modifier.height(20.dp))
        EtiquetaCampo(texto = "Nombre y Apellido")
        OutlinedTextField(
            value = state.nombre,
            onValueChange = { onAction(AuthAction.CambiarNombre(it)) },
            placeholder = { Text("Ej. Francisca Silva Méndez") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = LoginGrisTexto) },
            shape = RoundedCornerShape(16.dp),
            colors = coloresCampo(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))
        EtiquetaCampo(texto = "RUT Chileno")
        OutlinedTextField(
            value = state.rut,
            onValueChange = { onAction(AuthAction.CambiarRut(it)) },
            placeholder = { Text("12.345.678-K") },
            singleLine = true,
            isError = state.rutInvalido,
            leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null, tint = LoginGrisTexto) },
            shape = RoundedCornerShape(16.dp),
            colors = coloresCampo(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        if (state.rutInvalido) {
            Text(
                text = "RUT inválido",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        EtiquetaCampo(texto = "Teléfono / WhatsApp")
        OutlinedTextField(
            value = state.telefono,
            onValueChange = { onAction(AuthAction.CambiarTelefono(it)) },
            placeholder = { Text("8765 4321") },
            singleLine = true,
            leadingIcon = { PrefijoTelefono() },
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

        Spacer(modifier = Modifier.height(16.dp))
        FilaTerminos(
            aceptado = state.aceptaTerminos,
            onCambiar = { onAction(AuthAction.CambiarAceptaTerminos(it)) },
        )

        Spacer(modifier = Modifier.height(20.dp))
        BannerLey20584()

        Spacer(modifier = Modifier.height(20.dp))
        if (state.cargando) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Button(
                onClick = { onAction(AuthAction.ConfirmarPerfilGoogle) },
                enabled = state.puedeConfirmarPerfilGoogle,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LoginPrimarioOscuro, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(text = "CREAR MI CUENTA", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun EncabezadoAuth(modo: ModoAuth, onAction: (AuthAction) -> Unit) {
    if (modo == ModoAuth.REGISTRO) {
        EncabezadoRegistro(
            titulo = "Crea tu cuenta",
            subtitulo = "Tu salud y bienestar en manos de kinesiólogos certificados",
            onVolver = { onAction(AuthAction.CambiarModo(ModoAuth.LOGIN)) },
        )
        return
    }

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
}

@Composable
private fun EncabezadoRegistro(titulo: String, subtitulo: String, onVolver: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LoginFondo),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onVolver) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color(0xFF1A1C1B))
            }
            Spacer(modifier = Modifier.weight(1f))
            LogoKineCare(tamanoLogo = 32.dp, tamanoTexto = 18.sp)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {}) {
                Icon(Icons.Filled.HelpOutline, contentDescription = "Ayuda", tint = LoginGrisTexto)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier.size(76.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(LoginMenta),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.SelfImprovement,
                    contentDescription = null,
                    tint = LoginPrimarioOscuro,
                    modifier = Modifier.size(36.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(LoginSecundario),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = titulo,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF16241C),
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitulo,
            style = MaterialTheme.typography.bodyMedium,
            color = LoginGrisTexto,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CamposFormulario(
    state: AuthState,
    onAction: (AuthAction) -> Unit,
) {
    if (state.modo == ModoAuth.REGISTRO) {
        CamposRegistro(state = state, onAction = onAction)
    } else {
        CamposLogin(state = state, onAction = onAction)
    }
}

@Composable
private fun CamposLogin(
    state: AuthState,
    onAction: (AuthAction) -> Unit,
) {
    Spacer(modifier = Modifier.height(20.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        EtiquetaCampo(texto = "RUT")
        Text(text = "Chile", style = MaterialTheme.typography.bodyMedium, color = LoginGrisTexto)
    }
    OutlinedTextField(
        value = state.rut,
        onValueChange = { onAction(AuthAction.CambiarRut(it)) },
        placeholder = { Text("ej. 12.345.678-9") },
        singleLine = true,
        isError = state.rutInvalido,
        leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null, tint = LoginGrisTexto) },
        shape = RoundedCornerShape(16.dp),
        colors = coloresCampo(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
    if (state.rutInvalido) {
        Text(
            text = "RUT inválido",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    Spacer(modifier = Modifier.height(20.dp))
    var mostrarDialogoOlvidoPassword by remember { mutableStateOf(false) }
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
            modifier = Modifier.clickable { mostrarDialogoOlvidoPassword = true },
        )
    }
    if (mostrarDialogoOlvidoPassword) {
        DialogoOlvidoPassword(onCerrar = { mostrarDialogoOlvidoPassword = false })
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(
            checked = state.recordarCuenta,
            onCheckedChange = { onAction(AuthAction.CambiarRecordarCuenta(it)) },
            colors = SwitchDefaults.colors(checkedTrackColor = LoginPrimarioOscuro),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Recordar mi cuenta en este dispositivo",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DialogoOlvidoPassword(onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Recupera tu cuenta") },
        text = {
            Text(
                "Por ahora no podemos restablecer tu contraseña automáticamente. " +
                    "Contáctanos y te ayudamos a recuperar el acceso a tu cuenta.",
            )
        },
        confirmButton = {
            TextButton(onClick = onCerrar) { Text("Entendido") }
        },
    )
}

@Composable
private fun CamposRegistro(
    state: AuthState,
    onAction: (AuthAction) -> Unit,
) {
    Spacer(modifier = Modifier.height(20.dp))
    EtiquetaCampo(texto = "Nombre y Apellido")
    OutlinedTextField(
        value = state.nombre,
        onValueChange = { onAction(AuthAction.CambiarNombre(it)) },
        placeholder = { Text("Ej. Francisca Silva Méndez") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = LoginGrisTexto) },
        shape = RoundedCornerShape(16.dp),
        colors = coloresCampo(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )

    Spacer(modifier = Modifier.height(20.dp))
    EtiquetaCampo(texto = "RUT Chileno")
    OutlinedTextField(
        value = state.rut,
        onValueChange = { onAction(AuthAction.CambiarRut(it)) },
        placeholder = { Text("12.345.678-K") },
        singleLine = true,
        isError = state.rutInvalido,
        leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null, tint = LoginGrisTexto) },
        shape = RoundedCornerShape(16.dp),
        colors = coloresCampo(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
    if (state.rutInvalido) {
        Text(
            text = "RUT inválido",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    Spacer(modifier = Modifier.height(20.dp))
    EtiquetaCampo(texto = "Teléfono / WhatsApp")
    OutlinedTextField(
        value = state.telefono,
        onValueChange = { onAction(AuthAction.CambiarTelefono(it)) },
        placeholder = { Text("8765 4321") },
        singleLine = true,
        leadingIcon = { PrefijoTelefono() },
        shape = RoundedCornerShape(16.dp),
        colors = coloresCampo(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )

    Spacer(modifier = Modifier.height(20.dp))
    EtiquetaCampo(texto = "Correo electrónico")
    OutlinedTextField(
        value = state.correo,
        onValueChange = { onAction(AuthAction.CambiarCorreo(it)) },
        placeholder = { Text("nombre@ejemplo.cl") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = LoginGrisTexto) },
        shape = RoundedCornerShape(16.dp),
        colors = coloresCampo(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )

    Spacer(modifier = Modifier.height(20.dp))
    EtiquetaCampo(texto = "Crear contraseña")
    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = state.password,
        onValueChange = { onAction(AuthAction.CambiarPassword(it)) },
        placeholder = { Text("••••••••••••") },
        singleLine = true,
        isError = state.passwordDebil,
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
    if (state.passwordDebil) {
        Text(
            text = "Mínimo 6 caracteres",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    } else if (state.password.isNotEmpty()) {
        IndicadorFortalezaPassword(password = state.password)
    }

    if (state.mensajeError != null) {
        Text(
            text = state.mensajeError,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
    FilaTerminos(
        aceptado = state.aceptaTerminos,
        onCambiar = { onAction(AuthAction.CambiarAceptaTerminos(it)) },
    )
}

@Composable
private fun PrefijoTelefono() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Phone, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "+56", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF1A1C1B))
            Text(text = "9", style = MaterialTheme.typography.labelSmall, color = LoginGrisTexto)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(LoginGrisClaro),
        )
        Spacer(modifier = Modifier.width(4.dp))
    }
}

@Composable
private fun IndicadorFortalezaPassword(password: String) {
    val nivel = calcularFortalezaPassword(password)
    val (etiqueta, color) = when (nivel) {
        0, 1 -> "Débil" to MaterialTheme.colorScheme.error
        2 -> "Regular" to Color(0xFFB08900)
        3 -> "Buena" to LoginPrimario
        else -> "Segura" to LoginPrimarioOscuro
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(4) { indice ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (indice < nivel) color else LoginGrisClaro),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (nivel >= 4) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(2.dp))
        }
        Text(text = etiqueta, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

private fun calcularFortalezaPassword(password: String): Int {
    var puntaje = 0
    if (password.length >= 8) puntaje++
    if (password.any { it.isDigit() }) puntaje++
    if (password.any { it.isUpperCase() }) puntaje++
    if (password.any { !it.isLetterOrDigit() }) puntaje++
    return puntaje
}

@Composable
private fun FilaTerminos(aceptado: Boolean, onCambiar: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCambiar(!aceptado) },
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = aceptado,
            onCheckedChange = onCambiar,
            colors = CheckboxDefaults.colors(checkedColor = LoginPrimarioOscuro),
        )
        Text(
            text = buildAnnotatedString {
                append("Acepto los ")
                withStyle(SpanStyle(color = LoginPrimario, fontWeight = FontWeight.Bold)) {
                    append("Términos y Condiciones")
                }
                append(" y la ")
                withStyle(SpanStyle(color = LoginPrimario, fontWeight = FontWeight.Bold)) {
                    append("Política de Privacidad")
                }
                append(" de KineCare.")
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp, end = 4.dp),
        )
    }
}

@Composable
private fun BotonEnviar(
    state: AuthState,
    onAction: (AuthAction) -> Unit,
) {
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
                text = (if (state.modo == ModoAuth.LOGIN) "Iniciar sesión" else "Crear mi cuenta").uppercase(),
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun DivisorOIngresaCon(modo: ModoAuth) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = if (modo == ModoAuth.LOGIN) "O INGRESA CON" else "O CONTINÚA CON",
            style = MaterialTheme.typography.labelSmall,
            color = LoginGrisTexto,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BotonGoogle(modo: ModoAuth, habilitado: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = habilitado,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, LoginGrisClaro),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1A1C1B)),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(text = "G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (modo == ModoAuth.LOGIN) "Continuar con Google" else "Registrarme con Google",
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BannerLey20584() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LoginMentaSuave)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = LoginPrimarioOscuro, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Datos clínicos protegidos bajo Ley 20.584 de Derechos y Deberes del Paciente.",
            style = MaterialTheme.typography.bodySmall,
            color = LoginPrimarioOscuro,
        )
    }
}

@Composable
private fun FooterRegistro(
    state: AuthState,
    onAction: (AuthAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        val textoPregunta = if (state.modo == ModoAuth.LOGIN) "¿Aún no tienes cuenta? " else "¿Ya tienes una cuenta? "
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
}

@Composable
private fun FooterSeguridad() {
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
private fun LogoKineCare(tamanoLogo: Dp = 48.dp, tamanoTexto: TextUnit = 24.sp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(tamanoLogo)
                .clip(RoundedCornerShape(14.dp))
                .background(LoginPrimario),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "K", color = Color.White, fontSize = (tamanoLogo.value * 0.5f).sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = LoginSecundario, fontWeight = FontWeight.Bold)) { append("Kine") }
                withStyle(SpanStyle(color = LoginPrimario, fontWeight = FontWeight.Bold)) { append("Care") }
            },
            fontSize = tamanoTexto,
        )
    }
}

@Composable
private fun SelectorRol(seleccionado: RolUsuario, modo: ModoAuth, onAction: (AuthAction) -> Unit) {
    if (modo == ModoAuth.REGISTRO) {
        SelectorRolTarjetas(seleccionado = seleccionado, onAction = onAction)
    } else {
        SelectorRolPildora(seleccionado = seleccionado, onAction = onAction)
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

@Composable
private fun SelectorRolTarjetas(seleccionado: RolUsuario, onAction: (AuthAction) -> Unit) {
    Column {
        Text(
            text = "¿CÓMO USARÁS KINECARE?",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = LoginGrisTexto,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(LoginGrisClaro)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TarjetaRol(
                titulo = "Paciente",
                descripcion = "Atención a domicilio o en consulta",
                icono = Icons.Filled.Person,
                seleccionado = seleccionado == RolUsuario.CLIENTE,
                onClick = { onAction(AuthAction.SeleccionarRol(RolUsuario.CLIENTE)) },
                modifier = Modifier.weight(1f),
            )
            TarjetaRol(
                titulo = "Kinesiólogo(a)",
                descripcion = "Ofrecer servicios con registro SIS",
                icono = Icons.Filled.Work,
                seleccionado = seleccionado == RolUsuario.PROFESIONAL,
                onClick = { onAction(AuthAction.SeleccionarRol(RolUsuario.PROFESIONAL)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TarjetaRol(
    titulo: String,
    descripcion: String,
    icono: ImageVector,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (seleccionado) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (seleccionado) LoginMenta else Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icono,
                    contentDescription = null,
                    tint = if (seleccionado) LoginPrimarioOscuro else LoginGrisTexto,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (seleccionado) LoginPrimarioOscuro else Color.Transparent)
                    .then(
                        if (!seleccionado) Modifier.border(1.dp, LoginGrisClaro, CircleShape) else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (seleccionado) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = titulo,
            fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Medium,
            color = Color(0xFF1A1C1B),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = descripcion, style = MaterialTheme.typography.bodySmall, color = LoginGrisTexto)
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

@Preview(showBackground = true)
@Composable
private fun AuthScreenCompletarPerfilGooglePreview() {
    KineCareTheme {
        AuthScreen(
            state = AuthState(
                modo = ModoAuth.REGISTRO,
                nombre = "Francisca Silva",
                perfilGooglePendiente = PerfilGooglePendiente(
                    uid = "uid-preview",
                    correoGoogle = "francisca.silva@gmail.com",
                    fotoUrl = null,
                ),
            ),
            onAction = {},
        )
    }
}
