package com.darjnest.kinecare.core.common.domain.model

data class Direccion(
    val calle: String,
    val numero: String,
    val comuna: String,
    val ciudad: String,
    val lat: Double?,
    val lng: Double?,
    val indicaciones: String?,
)
