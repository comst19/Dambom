package com.comst19.dambom.feature.library.vault

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.comst19.dambom.feature.library.R

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun rememberPrivateVaultAuthentication(onAuthenticated: () -> Unit): (() -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? AppCompatActivity
    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsScreenLock by remember { mutableStateOf(false) }
    val title = stringResource(R.string.vault_title)
    val subtitle = stringResource(R.string.vault_auth_subtitle)
    val credentialLabel = stringResource(R.string.vault_use_device_lock)
    val unavailable = stringResource(R.string.vault_auth_unavailable)
    val credentialLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val action = pending
            pending = null
            if (result.resultCode == android.app.Activity.RESULT_OK) action?.invoke()
        }

    @Suppress("DEPRECATION")
    fun authenticateWithCredential() {
        val intent = keyguard.createConfirmDeviceCredentialIntent(title, subtitle)
        if (intent == null) {
            pending = null
            needsScreenLock = true
        } else {
            credentialLauncher.launch(intent)
        }
    }
    val prompt =
        remember(activity) {
            activity?.let {
                BiometricPrompt(
                    it,
                    ContextCompat.getMainExecutor(context),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            val action = pending
                            pending = null
                            action?.invoke()
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && errorCode in
                                setOf(
                                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                                    BiometricPrompt.ERROR_LOCKOUT,
                                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
                                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                                )
                            ) {
                                authenticateWithCredential()
                            } else {
                                pending = null
                                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                    errorCode != BiometricPrompt.ERROR_CANCELED
                                ) {
                                    error = errString.toString()
                                }
                            }
                        }
                    },
                )
            }
        }
    DisposableEffect(prompt) {
        onDispose {
            pending = null
            prompt?.cancelAuthentication()
        }
    }
    if (error != null || needsScreenLock) {
        AlertDialog(
            onDismissRequest = {
                error = null
                needsScreenLock = false
            },
            title = { Text(title) },
            text = { Text(if (needsScreenLock) stringResource(R.string.vault_setup_lock) else error.orEmpty()) },
            confirmButton = {
                TextButton(onClick = {
                    if (needsScreenLock) context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                    error = null
                    needsScreenLock = false
                }) {
                    Text(stringResource(if (needsScreenLock) R.string.vault_open_settings else android.R.string.ok))
                }
            },
            dismissButton = {
                if (needsScreenLock) {
                    TextButton(onClick = { needsScreenLock = false }) { Text(stringResource(android.R.string.cancel)) }
                }
            },
        )
    }
    return {
        if (pending == null) {
            when {
                !keyguard.isDeviceSecure -> {
                    needsScreenLock = true
                }

                prompt == null -> {
                    error = unavailable
                }

                else -> {
                    pending = onAuthenticated
                    val strong = BiometricManager.Authenticators.BIOMETRIC_STRONG
                    val promptInfo =
                        BiometricPrompt.PromptInfo
                            .Builder()
                            .setTitle(title)
                            .setSubtitle(subtitle)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        prompt.authenticate(
                            promptInfo
                                .setAllowedAuthenticators(strong or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                                .build(),
                        )
                    } else if (
                        BiometricManager.from(context).canAuthenticate(strong) == BiometricManager.BIOMETRIC_SUCCESS
                    ) {
                        prompt.authenticate(
                            promptInfo
                                .setAllowedAuthenticators(strong)
                                .setNegativeButtonText(credentialLabel)
                                .build(),
                        )
                    } else {
                        authenticateWithCredential()
                    }
                }
            }
        }
    }
}
