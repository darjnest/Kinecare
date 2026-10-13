package com.darjnest.kinecare.feature.verification.domain

/** Por que una verificacion de identidad quedo `RECHAZADO`; 1:1 con el `motivo` de `estadoVerificacion`. */
enum class MotivoRechazoVerificacion {
    /** El proveedor no pudo verificar la identidad (documento o prueba de vida no validos). */
    DECLINED,

    /** El RUT del documento no coincide con el de la cuenta del profesional. */
    RUT_NO_COINCIDE,

    /** La sesion de verificacion vencio sin completarse. */
    EXPIRADA,

    /** El profesional dejo la verificacion a medias. */
    ABANDONADA,

    /** La verificacion aprobada ya vencio y debe repetirse. */
    KYC_VENCIDO,
}
