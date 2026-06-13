package com.example.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Wrapper sobre la API oficial [androidx.biometric.BiometricPrompt].
 *
 * La biometría es solo un acceso de conveniencia: siempre hay fallback al PIN mediante el
 * botón negativo del prompt.
 *
 * Seguridad (SEC-01): se exige BIOMETRIC_STRONG (Clase 3). El reconocimiento facial 2D
 * suplantable y otras modalidades débiles (BIOMETRIC_WEAK) NO se aceptan en una app de
 * finanzas. Si el dispositivo solo ofrece biometría débil, [isAvailable] devuelve false y
 * la UI degrada a solo-PIN (no se muestra el acceso biométrico), conservando el PIN como
 * único factor en esos equipos.
 */
object BiometricHelper {

    /**
     * ¿El dispositivo tiene biometría FUERTE (Clase 3) utilizable y registrada?
     *
     * Se comprueba explícitamente BIOMETRIC_STRONG: si el sensor solo es WEAK, se devuelve
     * false para degradar a solo-PIN en lugar de ofrecer un factor suplantable.
     */
    fun isAvailable(context: Context): Boolean {
        return BiometricManager.from(context)
            .canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Muestra el prompt biométrico.
     *
     * SEC2-04: si se provee [cryptoObject], el prompt queda vinculado criptográficamente a la
     * clave del gate ([BiometricGate]) y [onSuccess] recibe el resultado con el cipher
     * autenticado; el caller DEBE verificar el token con él antes de desbloquear. Sin
     * [cryptoObject] el éxito es solo informativo (se usa únicamente en flujos de confirmación
     * de UI, nunca para conceder el desbloqueo).
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Desbloquear Mi Panel Financiero",
        subtitle: String = "Usa tu biometría para continuar",
        cryptoObject: BiometricPrompt.CryptoObject? = null,
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (message: String) -> Unit,
        onFallbackToPin: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess(result)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // El usuario eligió el botón negativo (usar PIN) o canceló.
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        onFallbackToPin()
                    } else {
                        onError(errString.toString())
                    }
                }
                // onAuthenticationFailed: huella no reconocida; el prompt sigue abierto, no hacemos nada.
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Usar PIN")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()

        if (cryptoObject != null) {
            prompt.authenticate(info, cryptoObject)
        } else {
            prompt.authenticate(info)
        }
    }
}
