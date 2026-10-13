@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.auth.data.repository_impl

import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.TipoAtencion
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.domain.util.RutUtils
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.firebase.perfilProfesionalInicial
import com.darjnest.kinecare.feature.auth.data.repository.AuthRepository
import com.darjnest.kinecare.feature.auth.domain.AuthError
import com.darjnest.kinecare.feature.auth.domain.ResultadoGoogle
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Instant
import javax.inject.Inject
import kotlin.time.Clock

private const val COLECCION_USUARIOS = "usuarios"
private const val COLECCION_PERFILES_PUBLICOS = "perfilesPublicos"
private const val COLECCION_RUTS_REGISTRADOS = "rutsRegistrados"
private const val COLECCION_PROFESIONALES = "profesionales"
private const val COLECCION_CLIENTES = "clientes"

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {

    override suspend fun iniciarSesion(rut: String, password: String): Result<Usuario, AuthError> {
        return try {
            val resultado = firebaseAuth.signInWithEmailAndPassword(RutUtils.emailFirebase(rut), password).await()
            val uid = resultado.user?.uid ?: return Result.Error(AuthError.DESCONOCIDO)
            obtenerUsuario(uid)
        } catch (e: FirebaseAuthInvalidUserException) {
            Result.Error(AuthError.USUARIO_NO_ENCONTRADO)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.Error(AuthError.CREDENCIALES_INVALIDAS)
        } catch (e: FirebaseNetworkException) {
            Result.Error(AuthError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(AuthError.DESCONOCIDO)
        }
    }

    override suspend fun registrar(
        nombre: String,
        rut: String,
        password: String,
        rol: RolUsuario,
        telefono: String,
        correoContacto: String,
        tiposAtencion: Set<TipoAtencion>,
    ): Result<Usuario, AuthError> {
        return try {
            val rutNormalizado = RutUtils.normalizar(rut)
            val resultado = firebaseAuth.createUserWithEmailAndPassword(RutUtils.emailFirebase(rutNormalizado), password).await()
            val uid = resultado.user?.uid ?: return Result.Error(AuthError.DESCONOCIDO)

            val datosUsuario = mapOf(
                "nombre" to nombre,
                "rut" to rutNormalizado,
                "email" to RutUtils.emailFirebase(rutNormalizado),
                "correoContacto" to correoContacto,
                "telefono" to telefono,
                "rol" to rol.name,
                "fotoUrl" to null,
                "fechaRegistro" to FieldValue.serverTimestamp(),
            )
            try {
                crearDocumentosDeCuenta(uid, rutNormalizado, datosUsuario, rol, tiposAtencion)
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
                // El RUT ya lo reclamo otra cuenta (p. ej. una de Google): se deshace la cuenta de Auth
                // recien creada para no dejarla huerfana, sin usuario ni perfil.
                runCatching { resultado.user?.delete()?.await() }
                firebaseAuth.signOut()
                return Result.Error(AuthError.RUT_YA_REGISTRADO)
            }

            val usuarioCreado = obtenerUsuario(uid)
            // createUserWithEmailAndPassword deja la sesión iniciada automáticamente;
            // se cierra para que el usuario inicie sesión explícitamente después de registrarse.
            firebaseAuth.signOut()
            usuarioCreado
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.Error(AuthError.RUT_YA_REGISTRADO)
        } catch (e: FirebaseAuthWeakPasswordException) {
            Result.Error(AuthError.PASSWORD_DEBIL)
        } catch (e: FirebaseNetworkException) {
            Result.Error(AuthError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(AuthError.DESCONOCIDO)
        }
    }

    override suspend fun iniciarSesionConGoogle(idToken: String): Result<ResultadoGoogle, AuthError> {
        return try {
            val credencial = GoogleAuthProvider.getCredential(idToken, null)
            val resultado = firebaseAuth.signInWithCredential(credencial).await()
            val user = resultado.user ?: return Result.Error(AuthError.DESCONOCIDO)

            val doc = firestore.collection(COLECCION_USUARIOS).document(user.uid).get().await()
            val usuarioExistente = doc.aUsuarioONull(user.uid)
            if (usuarioExistente != null) {
                Result.Success(ResultadoGoogle.SesionIniciada(usuarioExistente))
            } else {
                Result.Success(
                    ResultadoGoogle.RequiereCompletarPerfil(
                        uid = user.uid,
                        nombreSugerido = user.displayName.orEmpty(),
                        correoGoogle = user.email.orEmpty(),
                        fotoUrl = user.photoUrl?.toString(),
                    ),
                )
            }
        } catch (e: FirebaseNetworkException) {
            Result.Error(AuthError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(AuthError.DESCONOCIDO)
        }
    }

    override suspend fun completarRegistroGoogle(
        uid: String,
        nombre: String,
        rut: String,
        telefono: String,
        correoContacto: String,
        rol: RolUsuario,
        fotoUrl: String?,
        tiposAtencion: Set<TipoAtencion>,
    ): Result<Usuario, AuthError> {
        return try {
            val rutNormalizado = RutUtils.normalizar(rut)
            val datosUsuario = mapOf(
                "nombre" to nombre,
                "rut" to rutNormalizado,
                "email" to (firebaseAuth.currentUser?.email ?: ""),
                "correoContacto" to correoContacto,
                "telefono" to telefono,
                "rol" to rol.name,
                "fotoUrl" to fotoUrl,
                "fechaRegistro" to FieldValue.serverTimestamp(),
            )
            // La unicidad del RUT ya no se consulta en `usuarios` (privado): reclamar
            // `rutsRegistrados/{rut}` en el batch falla con PERMISSION_DENIED si otra cuenta lo tiene.
            crearDocumentosDeCuenta(uid, rutNormalizado, datosUsuario, rol, tiposAtencion)
            obtenerUsuario(uid)
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Result.Error(AuthError.RUT_YA_REGISTRADO)
            } else {
                Result.Error(AuthError.DESCONOCIDO)
            }
        } catch (e: FirebaseNetworkException) {
            Result.Error(AuthError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(AuthError.DESCONOCIDO)
        }
    }

    override fun observarUsuarioActual(): Flow<Usuario?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid == null) {
                trySend(null)
            } else {
                firestore.collection(COLECCION_USUARIOS).document(uid).get()
                    .addOnSuccessListener { doc -> trySend(doc.aUsuarioONull(uid)) }
                    .addOnFailureListener { trySend(null) }
            }
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override suspend fun cerrarSesion() {
        firebaseAuth.signOut()
    }

    /**
     * Escribe `rutsRegistrados/{rut}` (reclama el RUT: si ya existe el batch completo falla),
     * `usuarios/{uid}` (privado), `perfilesPublicos/{uid}` (nombre y foto, lo unico que ven los
     * demas) y, para un Profesional, `profesionales/{uid}`
     * en un solo batch: si el perfil profesional fallara por separado
     * quedaria una cuenta PROFESIONAL sin documento, invisible en la
     * busqueda y sin donde guardar disponibilidad ni biografia. Para un
     * Cliente crea `clientes/{uid}` vacio (direcciones, favoritos): sin el,
     * `ClienteRepository.obtenerPorId` responde `NO_ENCONTRADO`.
     */
    private suspend fun crearDocumentosDeCuenta(
        uid: String,
        rutNormalizado: String,
        datosUsuario: Map<String, Any?>,
        rol: RolUsuario,
        tiposAtencion: Set<TipoAtencion>,
    ) {
        val batch = firestore.batch()
        batch.set(firestore.collection(COLECCION_RUTS_REGISTRADOS).document(rutNormalizado), mapOf("uid" to uid))
        batch.set(firestore.collection(COLECCION_USUARIOS).document(uid), datosUsuario)
        batch.set(
            firestore.collection(COLECCION_PERFILES_PUBLICOS).document(uid),
            mapOf("nombre" to datosUsuario["nombre"], "fotoUrl" to datosUsuario["fotoUrl"]),
        )
        if (rol == RolUsuario.PROFESIONAL) {
            batch.set(
                firestore.collection(COLECCION_PROFESIONALES).document(uid),
                perfilProfesionalInicial(tiposAtencion),
            )
        }
        if (rol == RolUsuario.CLIENTE) {
            batch.set(
                firestore.collection(COLECCION_CLIENTES).document(uid),
                mapOf(
                    "direcciones" to emptyList<Any>(),
                    "metodosPago" to emptyList<Any>(),
                    "favoritos" to emptyList<String>(),
                ),
            )
        }
        batch.commit().await()
    }

    private suspend fun obtenerUsuario(uid: String): Result<Usuario, AuthError> {
        return try {
            val doc = firestore.collection(COLECCION_USUARIOS).document(uid).get().await()
            val usuario = doc.aUsuarioONull(uid) ?: return Result.Error(AuthError.USUARIO_NO_ENCONTRADO)
            Result.Success(usuario)
        } catch (e: Exception) {
            Result.Error(AuthError.DESCONOCIDO)
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.aUsuarioONull(uid: String): Usuario? {
        if (!exists()) return null
        return Usuario(
            id = uid,
            nombre = getString("nombre") ?: "",
            rut = getString("rut") ?: "",
            email = getString("email") ?: "",
            correoContacto = getString("correoContacto"),
            telefono = getString("telefono"),
            rol = runCatching { RolUsuario.valueOf(getString("rol") ?: "") }.getOrDefault(RolUsuario.CLIENTE),
            fotoUrl = getString("fotoUrl"),
            fechaRegistro = getTimestamp("fechaRegistro")?.let { Instant.fromEpochMilliseconds(it.toDate().time) }
                ?: Clock.System.now(),
        )
    }
}
