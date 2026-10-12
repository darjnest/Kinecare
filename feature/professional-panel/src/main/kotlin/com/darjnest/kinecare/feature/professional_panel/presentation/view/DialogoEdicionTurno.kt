package com.darjnest.kinecare.feature.professional_panel.presentation.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.DisponibilidadYHorariosAction
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.TurnoHorario
import kotlinx.datetime.LocalTime

/** Dialogo para cambiar la hora de inicio y de término de un turno de atención. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DialogoEdicionTurno(
    turno: TurnoHorario,
    error: String?,
    onAction: (DisponibilidadYHorariosAction) -> Unit,
) {
    val inicio = rememberTimePickerState(
        initialHour = turno.horaInicio.substringBefore(':').toIntOrNull() ?: 9,
        initialMinute = turno.horaInicio.substringAfter(':').toIntOrNull() ?: 0,
        is24Hour = true,
    )
    val fin = rememberTimePickerState(
        initialHour = turno.horaFin.substringBefore(':').toIntOrNull() ?: 13,
        initialMinute = turno.horaFin.substringAfter(':').toIntOrNull() ?: 0,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = { onAction(DisponibilidadYHorariosAction.CancelarEdicionTurno) },
        containerColor = Color.White,
        title = { Text(turno.titulo) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Desde", style = MaterialTheme.typography.labelLarge)
                TimeInput(state = inicio)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Hasta", style = MaterialTheme.typography.labelLarge)
                TimeInput(state = fin)
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAction(
                        DisponibilidadYHorariosAction.ConfirmarEdicionTurno(
                            turnoId = turno.id,
                            horaInicio = LocalTime(inicio.hour, inicio.minute),
                            horaFin = LocalTime(fin.hour, fin.minute),
                        ),
                    )
                },
            ) { Text("Aplicar") }
        },
        dismissButton = {
            TextButton(onClick = { onAction(DisponibilidadYHorariosAction.CancelarEdicionTurno) }) { Text("Cancelar") }
        },
    )
}
