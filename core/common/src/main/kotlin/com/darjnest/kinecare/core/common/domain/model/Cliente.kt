package com.darjnest.kinecare.core.common.domain.model

data class Cliente(
    val usuario: Usuario,
    val direcciones: List<Direccion>,
    val metodosPago: List<MetodoPago>,
    val favoritos: List<String>,
)
