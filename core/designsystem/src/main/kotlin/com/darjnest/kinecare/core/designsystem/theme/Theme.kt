package com.darjnest.kinecare.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Sin dynamic color: la identidad de marca (verde salvia / azul petroleo)
// debe verse igual en todos los dispositivos.
private val LightColorScheme = lightColorScheme(
    primary = VerdeSalvia50,
    onPrimary = Blanco,
    primaryContainer = VerdeSalvia95,
    onPrimaryContainer = VerdeSalvia10,
    secondary = AzulPetroleo50,
    onSecondary = Blanco,
    secondaryContainer = AzulPetroleo95,
    onSecondaryContainer = AzulPetroleo10,
    background = Blanco,
    onBackground = Gris10,
    surface = Blanco,
    onSurface = Gris10,
    surfaceVariant = Gris95,
    onSurfaceVariant = Gris30,
    outline = Gris50,
    error = RojoError40,
    onError = Blanco,
    errorContainer = RojoError90,
)

private val DarkColorScheme = darkColorScheme(
    primary = VerdeSalvia80,
    onPrimary = VerdeSalvia10,
    primaryContainer = VerdeSalvia30,
    onPrimaryContainer = VerdeSalvia95,
    secondary = AzulPetroleo80,
    onSecondary = AzulPetroleo10,
    secondaryContainer = AzulPetroleo30,
    onSecondaryContainer = AzulPetroleo95,
    background = Gris10,
    onBackground = Gris95,
    surface = Gris10,
    onSurface = Gris95,
    surfaceVariant = Gris30,
    onSurfaceVariant = Gris80,
    outline = Gris50,
    error = RojoError40,
    onError = Blanco,
)

@Composable
fun KineCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KineCareTypography,
        content = content,
    )
}
