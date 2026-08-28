package com.comst19.dambom.feature.library.component
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun FullscreenVideoPlayer(
    task: DownloadTask,
    player: Player,
    fileActions: LibraryFileActions,
    onDismiss: () -> Unit,
    onRotate: () -> Unit,
    showRotationControl: Boolean,
    isPipContentOnly: Boolean,
    onVideoBoundsChanged: (IntRect?) -> Unit,
) {
    val playPauseState = rememberPlayPauseButtonState(player)
    val progressState = rememberProgressStateWithTickInterval(player, PROGRESS_TICK_MILLIS)
    val surfaceDescription = stringResource(R.string.player_surface_description, task.title)
    val toggleControlsLabel = stringResource(R.string.player_toggle_controls)
    var selectedContentMode by remember(task.id) { mutableStateOf(FullscreenContentMode.Fit) }
    var controlsVisible by remember(task.id) { mutableStateOf(true) }
    var controlsInteracting by remember(task.id) { mutableStateOf(false) }
    var controlsInteractionRevision by remember(task.id) { mutableStateOf(0) }
    val contentMode = fullscreenContentModeFor(selectedContentMode, isPipContentOnly)
    LaunchedEffect(isPipContentOnly) { if (isPipContentOnly) selectedContentMode = FullscreenContentMode.Fit }
    LaunchedEffect(controlsVisible, playPauseState.showPlay, controlsInteracting, controlsInteractionRevision) {
        if (shouldAutoHideFullscreenControls(controlsVisible, !playPauseState.showPlay, controlsInteracting)) {
            delay(FULLSCREEN_CONTROLS_AUTO_HIDE_MILLIS)
            controlsVisible = false
        }
    }
    FullscreenSystemBars(enabled = !isPipContentOnly)
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
        if (isPipContentOnly) {
            FullscreenContentFrame(player, surfaceDescription, Modifier.fillMaxSize(), onVideoBoundsChanged, contentMode)
        } else {
            Box(Modifier.fillMaxSize()) {
                FullscreenContentFrame(player, surfaceDescription, Modifier.fillMaxSize(), onVideoBoundsChanged, contentMode)
                Box(
                    Modifier
                        .fillMaxSize()
                        .fullscreenPlayerGestures(
                            toggleControlsLabel = toggleControlsLabel,
                            onToggleControls = { controlsVisible = !controlsVisible },
                            onContentModeChanged = { selectedContentMode = it },
                        ),
                )
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(FULLSCREEN_CONTROLS_FADE_MILLIS)),
                    exit = fadeOut(tween(FULLSCREEN_CONTROLS_FADE_MILLIS)),
                    modifier = Modifier.matchParentSize(),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        FullscreenOverlayControls(
                            task,
                            player,
                            fileActions,
                            onDismiss,
                            onRotate,
                            showRotationControl,
                            contentMode,
                            { selectedContentMode = contentMode.toggled() },
                            playPauseState.showPlay,
                            playPauseState.isEnabled,
                            playPauseState::onClick,
                            progressState.currentPositionMs,
                            progressState.durationMs,
                            {
                                controlsVisible = true
                                controlsInteractionRevision++
                            },
                            { controlsInteracting = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.FullscreenOverlayControls(
    task: DownloadTask,
    player: Player,
    fileActions: LibraryFileActions,
    onDismiss: () -> Unit,
    onRotate: () -> Unit,
    showRotationControl: Boolean,
    contentMode: FullscreenContentMode,
    onContentModeToggle: () -> Unit,
    showPlay: Boolean,
    playEnabled: Boolean,
    onPlayPause: () -> Unit,
    progressPositionMillis: Long,
    progressDurationMillis: Long,
    onInteraction: () -> Unit,
    onInteractionChanged: (Boolean) -> Unit,
) {
    Surface(
        modifier =
            Modifier.align(Alignment.TopStart).fillMaxWidth().windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
        color = Color.Black.copy(alpha = FULLSCREEN_CONTROLS_SCRIM_ALPHA),
        contentColor = Color.White,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.player_exit_fullscreen)) }
            Text(
                text = task.title,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onContentModeToggle) {
                Icon(
                    if (contentMode == FullscreenContentMode.Fit) Icons.Outlined.CropFree else Icons.Outlined.ZoomOutMap,
                    stringResource(
                        if (contentMode ==
                            FullscreenContentMode.Fit
                        ) {
                            R.string.player_expand_crop
                        } else {
                            R.string.player_fit_video
                        },
                    ),
                )
            }
            if (showRotationControl) IconButton(onRotate) { Icon(Icons.Outlined.Rotate90DegreesCw, stringResource(R.string.player_rotate)) }
            VideoActionsButton(task = task, actions = fileActions)
        }
    }
    PlayerControls(
        player = player,
        showPlay = showPlay,
        playEnabled = playEnabled,
        onPlayPause = onPlayPause,
        positionMillis = progressPositionMillis,
        durationMillis = progressDurationMillis,
        contentColor = Color.White,
        onInteraction = onInteraction,
        onInteractionChanged = onInteractionChanged,
        modifier = Modifier.fillMaxSize(),
        timelineModifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.displayCutout.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal).union(
                        WindowInsets.mandatorySystemGestures.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    ),
                ).background(Color.Black.copy(alpha = FULLSCREEN_CONTROLS_SCRIM_ALPHA))
                .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun FullscreenContentFrame(
    player: Player,
    surfaceDescription: String,
    modifier: Modifier,
    onVideoBoundsChanged: (IntRect?) -> Unit,
    contentMode: FullscreenContentMode,
) = ContentFrame(
    player = player,
    shutter = {},
    contentScale = contentMode.contentScale,
    modifier =
        modifier
            .background(Color.Black)
            .onGloballyPositioned { coordinates ->
                onVideoBoundsChanged(coordinates.boundsInWindow().toIntRect())
            }.semantics {
                contentDescription = surfaceDescription
            },
)

private fun androidx.compose.ui.geometry.Rect.toIntRect() =
    IntRect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())

@Composable
private fun FullscreenSystemBars(enabled: Boolean) {
    val view = LocalView.current
    val window = (view.context as? android.app.Activity)?.window
    DisposableEffect(enabled, view, window) {
        if (!enabled) return@DisposableEffect onDispose {}
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private const val FULLSCREEN_CONTROLS_AUTO_HIDE_MILLIS = 3_000L
private const val FULLSCREEN_CONTROLS_FADE_MILLIS = 180
private const val FULLSCREEN_CONTROLS_SCRIM_ALPHA = 0.72f
