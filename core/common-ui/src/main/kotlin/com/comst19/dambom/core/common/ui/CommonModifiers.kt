package com.comst19.dambom.core.common.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

@Composable
fun Modifier.appScaffoldPadding(): Modifier {
    val appPadding = LocalAppScaffoldPadding.current
    return padding(appPadding).consumeWindowInsets(appPadding)
}

@Composable
fun Modifier.throttledClickable(
    enabled: Boolean = true,
    throttleMillis: Long = DEFAULT_THROTTLE_MILLIS,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier {
    require(throttleMillis >= 0L)
    var lastClickTime by remember { mutableLongStateOf(NO_CLICK_TIME) }

    return clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
    ) {
        val currentTime = SystemClock.elapsedRealtime()
        if (lastClickTime == NO_CLICK_TIME || currentTime - lastClickTime >= throttleMillis) {
            lastClickTime = currentTime
            onClick()
        }
    }
}

private const val DEFAULT_THROTTLE_MILLIS = 500L
private const val NO_CLICK_TIME = Long.MIN_VALUE
