package com.darjnest.kinecare.core.common.util

import java.text.NumberFormat
import java.util.Locale

private val clpFormatter: NumberFormat by lazy {
    val localeChile = Locale.Builder().setLanguage("es").setRegion("CL").build()
    NumberFormat.getNumberInstance(localeChile)
}

fun Long.formatComoClp(): String = "$${clpFormatter.format(this)}"
