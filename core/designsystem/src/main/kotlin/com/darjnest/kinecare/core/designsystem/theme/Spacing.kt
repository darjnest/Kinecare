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

    /** Lado del avatar grande de encabezado de perfil. */
    val avatarGrande: Dp = 72.dp

    /** Tamano tactil minimo de un icono clickeable (barra superior). */
    val iconoTactil: Dp = 48.dp

    /** Tamano de icono decorativo junto a texto. */
    val icono: Dp = 20.dp
}
