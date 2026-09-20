package com.darjnest.kinecare.core.designsystem.components.label

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darjnest.kinecare.core.designsystem.theme.AzulPetroleo10
import com.darjnest.kinecare.core.designsystem.theme.AzulPetroleo95
import com.darjnest.kinecare.core.designsystem.theme.Gris10
import com.darjnest.kinecare.core.designsystem.theme.Gris95
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.theme.RojoError40
import com.darjnest.kinecare.core.designsystem.theme.RojoError90
import com.darjnest.kinecare.core.designsystem.theme.VerdeSalvia10
import com.darjnest.kinecare.core.designsystem.theme.VerdeSalvia95

/**
 * Tono semantico generico. Cada feature mapea su propio estado de dominio
 * (ej. EstadoVerificacion) a un [BadgeTono] - designsystem no conoce modelos
 * de negocio.
 */
enum class BadgeTono {
    EXITO,
    INFO,
    NEUTRO,
    ERROR,
}

private data class BadgeColores(val fondo: Color, val texto: Color)

private fun coloresPara(tono: BadgeTono): BadgeColores = when (tono) {
    BadgeTono.EXITO -> BadgeColores(fondo = VerdeSalvia95, texto = VerdeSalvia10)
    BadgeTono.INFO -> BadgeColores(fondo = AzulPetroleo95, texto = AzulPetroleo10)
    BadgeTono.NEUTRO -> BadgeColores(fondo = Gris95, texto = Gris10)
    BadgeTono.ERROR -> BadgeColores(fondo = RojoError90, texto = RojoError40)
}

@Composable
fun KineCareBadge(
    texto: String,
    tono: BadgeTono,
    modifier: Modifier = Modifier,
) {
    val colores = coloresPara(tono)
    Text(
        text = texto,
        color = colores.texto,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(color = colores.fondo, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun KineCareBadgePreview() {
    KineCareTheme {
        KineCareBadge(texto = "Identidad verificada", tono = BadgeTono.EXITO)
    }
}
