package com.darjnest.kinecare.core.common.domain.model

enum class TipoMetodoPago {
    /** Tarjeta sin distinguir crédito/débito: pagos anteriores a que se separaran. */
    TARJETA,
    TARJETA_CREDITO,
    TARJETA_DEBITO,
    TRANSFERENCIA,
    /** Saldo de la cuenta de Mercado Pago del cliente. */
    DINERO_EN_CUENTA,
}

data class MetodoPago(
    val tipo: TipoMetodoPago,
    val ultimosDigitos: String?,
    val tokenPasarela: String,
)

/**
 * Medio que el cliente elige al pagar. [idBackend] es el valor que viaja a la Cloud Function
 * `iniciarPago` (debe coincidir con `MEDIOS_PAGO` en `functions/src/mercadopago/pasarela.ts`).
 */
enum class MedioPagoElegible(val idBackend: String) {
    CREDITO("CREDITO"),
    DEBITO("DEBITO"),
    TRANSFERENCIA("TRANSFERENCIA"),
    DINERO_EN_CUENTA("DINERO_EN_CUENTA"),
}
