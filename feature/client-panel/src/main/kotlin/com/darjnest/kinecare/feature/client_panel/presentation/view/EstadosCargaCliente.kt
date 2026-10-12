package com.darjnest.kinecare.feature.client_panel.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.darjnest.kinecare.core.designsystem.theme.KineCareSpacing
import com.darjnest.kinecare.core.designsystem.theme.LoginGrisTexto
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimarioOscuro
import com.darjnest.kinecare.core.designsystem.theme.TextoPrincipal

/**
 * Spinner de carga inicial de una lista del panel del cliente. Se muestra solo
 * mientras no hay datos que pintar: sin esto, la carga se veia igual que el
 * estado vacio ("Aun no tienes ...").
 */
@Composable
internal fun CargandoListaCliente(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = KineCareSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = LoginPrimarioOscuro)
    }
}

/**
 * Falla al cargar una lista, con boton "Reintentar". Reemplaza al estado vacio:
 * un error de red no debe leerse como "no tienes nada".
 */
@Composable
internal fun ErrorCargaCliente(
    mensaje: String,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, tint = LoginGrisTexto, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = TextoPrincipal,
        )
        Text(
            text = "Revisa tu conexión e inténtalo de nuevo.",
            style = MaterialTheme.typography.labelSmall,
            color = LoginGrisTexto,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(LoginPrimarioOscuro)
                .clickable(role = Role.Button, onClick = onReintentar)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(text = "Reintentar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
