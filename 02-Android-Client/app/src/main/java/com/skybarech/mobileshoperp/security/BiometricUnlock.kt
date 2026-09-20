package com.skybarech.mobileshoperp.security

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.skybarech.mobileshoperp.ui.i18n.tr
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The account proof can only be decrypted after a strong system biometric.
 * No PIN, token or biometric template is saved here. A new fingerprint, new
 * account or changed credential invalidates this device's enrollment. */
object BiometricUnlock {
    private const val KEY = "skybarech.biometric.account.v1"
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("skybarech_biometric", Context.MODE_PRIVATE)
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    fun available(context: Context) = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    fun enabled(context: Context, binding: String): Boolean = binding.isNotBlank() && prefs(context).getString("binding", "") == binding && prefs(context).contains("ciphertext")
    fun shouldOffer(context: Context, binding: String) = available(context) && !enabled(context, binding) && prefs(context).getString("dismissed", "") != binding
    fun dismiss(context: Context, binding: String) { prefs(context).edit().putString("dismissed", binding).apply() }
    fun disable(context: Context) {
        prefs(context).edit().clear().apply()
        runCatching { keyStore().deleteEntry(KEY) }
    }
    tailrec fun activity(context: Context): FragmentActivity? = when (context) {
        is FragmentActivity -> context
        is ContextWrapper -> if (context.baseContext !== context) activity(context.baseContext) else null
        else -> null
    }
    fun authenticate(activity: FragmentActivity, binding: String, enroll: Boolean, success: () -> Unit, error: (String) -> Unit) {
        if (binding.isBlank() || !available(activity)) { error(tr("Set up fingerprint in Android settings, then try again.")); return }
        try {
            val store = prefs(activity)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            if (enroll) {
                disable(activity)
                val builder = KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true).setInvalidatedByBiometricEnrollment(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                else { @Suppress("DEPRECATION") builder.setUserAuthenticationValidityDurationSeconds(-1) }
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                generator.init(builder.build())
                cipher.init(Cipher.ENCRYPT_MODE, generator.generateKey())
            } else {
                if (!enabled(activity, binding)) { error(tr("Sign in with your PIN to enable fingerprint.")); return }
                val key = keyStore().getKey(KEY, null) as? SecretKey ?: throw IllegalStateException("Biometric key unavailable")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(store.getString("iv", ""), Base64.NO_WRAP)))
            }
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    if (code !in setOf(BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_CANCELED)) error(message.toString())
                }
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    try {
                        val authenticatedCipher = result.cryptoObject?.cipher ?: throw IllegalStateException("Missing authenticated cipher")
                        if (enroll) {
                            val encrypted = authenticatedCipher.doFinal(binding.toByteArray(Charsets.UTF_8))
                            store.edit().putString("binding", binding).putString("iv", Base64.encodeToString(authenticatedCipher.iv, Base64.NO_WRAP))
                                .putString("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
                        } else {
                            val proof = authenticatedCipher.doFinal(Base64.decode(store.getString("ciphertext", ""), Base64.NO_WRAP))
                            check(MessageDigest.isEqual(proof, binding.toByteArray(Charsets.UTF_8)))
                        }
                        success()
                    } catch (_: Exception) {
                        disable(activity); error(tr("Fingerprint changed. Sign in with your PIN and enable it again."))
                    }
                }
            }
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(
                BiometricPrompt.PromptInfo.Builder().setTitle(tr(if (enroll) "Enable fingerprint" else "Unlock your shop"))
                    .setSubtitle(tr("Verify with your device fingerprint"))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .setNegativeButtonText(tr("Use PIN")).build(), BiometricPrompt.CryptoObject(cipher))
        } catch (_: Exception) {
            disable(activity); error(tr("Fingerprint unavailable. Sign in with your PIN and enable it again."))
        }
    }
}
