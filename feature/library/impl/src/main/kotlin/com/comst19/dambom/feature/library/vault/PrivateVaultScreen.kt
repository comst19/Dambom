package com.comst19.dambom.feature.library.vault

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.FormFactorPreviews
import com.comst19.dambom.core.designsystem.previewNoOp
import com.comst19.dambom.feature.library.R
import java.util.WeakHashMap

@Composable
internal fun PrivateVideoWindowProtection() {
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        window?.let { PrivateVideoWindows.acquire(it) }
        onDispose { window?.let { PrivateVideoWindows.release(it) } }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PrivateVaultLockedScreen(
    onUnlock: () -> Unit,
    onBack: () -> Unit,
) {
    AppScreen(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.vault_title), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.player_back))
                }
            },
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Icons.Outlined.Lock,
                        null,
                        Modifier.padding(28.dp).size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    stringResource(R.string.vault_locked_title),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.vault_locked_description),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(64.dp))
                Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Outlined.Fingerprint, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.vault_unlock))
                }
                Text(
                    stringResource(R.string.vault_auth_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Korean locked vault",
    locale = "ko",
    widthDp = 393,
    heightDp = 852,
)
@FormFactorPreviews
@Composable
private fun PrivateVaultLockedPreview() {
    DambomTheme { PrivateVaultLockedScreen(::previewNoOp, ::previewNoOp) }
}

private object PrivateVideoWindows {
    private val owners = WeakHashMap<Window, Int>()
    private val originallySecure = WeakHashMap<Window, Boolean>()

    fun acquire(window: Window) {
        if (owners[window] == null) {
            originallySecure[window] = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        }
        owners[window] = (owners[window] ?: 0) + 1
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(window: Window) {
        val remaining = (owners[window] ?: 1) - 1
        if (remaining > 0) {
            owners[window] = remaining
        } else {
            owners.remove(window)
            if (originallySecure.remove(window) != true) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
