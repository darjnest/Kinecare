package com.darjnest.kinecare.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Espaciados y tamanos base del design system, para que las features no
 * hardcodeen `dp` sueltos (ver docs/SOLUTION_STRUCTURE.md). Se agregan
 * valores nuevos aqui cuando una pantalla real los necesita.
 */
object KineCareSpacing {
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 24.dp

    /** Diametro del circulo de color que enmarca el icono de un mensaje de estado a pantalla completa. */
    val circuloEstado: Dp = 96.dp

    /** Tamano del icono dentro de ese circulo. */
    val iconoEstado: Dp = 48.dp

    /** Lado del avatar grande de encabezado de perfil. */
    val avatarGrande: Dp = 72.dp

    /** Tamano tactil minimo de un icono clickeable (barra superior). */
    val iconoTactil: Dp = 48.dp

    /** Tamano de icono decorativo junto a texto. */
    val icono: Dp = 20.dp

    /** Alto de una barra de progreso fina (distribucion de calificaciones). */
    val barraDistribucion: Dp = 8.dp

    /** Ancho fijo de la etiqueta "5 ★" de cada fila de la distribucion, para alinear las barras. */
    val etiquetaDistribucion: Dp = 36.dp
}
