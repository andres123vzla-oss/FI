package com.example.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Wrapper sobre la API oficial [androidx.biometric.BiometricPrompt].
 *
 * La biometría es solo un acceso de conveniencia: siempre hay fallback al PIN mediante el
 * botón negativo del prompt.
 */
object BiometricHelper {

    /** ¿El dispositivo tiene biometría utilizable y registrada? */
    fun isAvailable(context: Context): Boolean {
        return BiometricManager.from(context)
            .canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Desbloquear Mi Panel Financiero",
        subtitle: String = "Usa tu biometría para continuar",
        onSuccess: () -> Unit,
        onError: (message: String) -> Unit,
        onFallbackToPin: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
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
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(info)
    }
}
