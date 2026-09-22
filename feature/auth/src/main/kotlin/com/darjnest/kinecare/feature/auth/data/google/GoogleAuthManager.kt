package com.darjnest.kinecare.feature.auth.data.google

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.darjnest.kinecare.feature.auth.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

/**
 * Envuelve Credential Manager para obtener un ID token de Google. Se
 * instancia directo en la UI (no vía Hilt): `getCredential` necesita el
 * Context de la Activity para mostrar el selector de cuenta, no el de la
 * aplicación.
 */
class GoogleAuthManager {

    suspend fun obtenerIdToken(context: Context): String {
        val credentialManager = CredentialManager.create(context)
        val opcionGoogle = GetGoogleIdOption.Builder()
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setNonce(generarNonce())
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(opcionGoogle)
            .build()

        val credencial = credentialManager.getCredential(context, request).credential
        check(credencial is CustomCredential && credencial.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Credencial de Google inesperada"
        }
        return GoogleIdTokenCredential.createFrom(credencial.data).idToken
    }

    private fun generarNonce(): String {
        val bytes = UUID.randomUUID().toString().toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
