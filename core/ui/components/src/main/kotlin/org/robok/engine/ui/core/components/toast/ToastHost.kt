package org.robok.engine.ui.core.components.toast

/*
 *  This file is part of T8RIN © 2023.
 *
 *  T8RIN is free software: you can redistribute it and/or modify
 *  it under the terms of the Apache-2.0 License as published by
 *  the Open Source Initiative, either version [Version] of the License, or
 *  (at your option) any later version.
 *
 *  T8RIN is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  Apache-2.0 License for more details.
 *
 *  You should have received a copy of the Apache-2.0 License
 *  along with T8RIN. If not, see <https://opensource.org/licenses/Apache-2.0>.
 */

import androidx.annotation.FloatRange
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.core.graphics.ColorUtils
import kotlin.coroutines.resume
import kotlin.math.min
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

val LocalToastHostState = compositionLocalOf<ToastHostState> { error("ToastHostState not present") }

@Composable fun rememberToastHostState() = remember { ToastHostState() }

@Composable
fun ToastHost(
  modifier: Modifier = Modifier,
  hostState: ToastHostState = LocalToastHostState.current,
  alignment: Alignment = Alignment.BottomCenter,
  toast: @Composable (ToastData) -> Unit = { Toast(it) },
) {
  val currentToastData = hostState.currentToastData
  val accessibilityManager = LocalAccessibilityManager.current
  LaunchedEffect(currentToastData) {
    if (currentToastData != null) {
      val duration = currentToastData.visuals.duration.toMillis(accessibilityManager)
      delay(duration)
      currentToastData.dismiss()
    }
  }

  AnimatedContent(
    targetState = currentToastData,
    transitionSpec = { ToastDefaults.transition },
    label = "",
  ) {
    Box(modifier = modifier.fillMaxSize()) {
      Box(modifier = Modifier.align(alignment)) { it?.let { toast(it) } }
    }
  }
}

@Composable
fun Toast(
  toastData: ToastData,
  modifier: Modifier = Modifier,
  shape: Shape = ToastDefaults.shape,
  containerColor: Color = ToastDefaults.color,
  contentColor: Color = ToastDefaults.contentColor,
) {
  val configuration = LocalConfiguration.current
  val sizeMin = min(configuration.screenWidthDp, configuration.screenHeightDp).dp

  Card(
    colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    modifier =
      if (modifier != Modifier) modifier
      else
        Modifier.heightIn(min = 48.dp)
          .widthIn(min = 0.dp, max = (sizeMin * 0.7f))
          .padding(bottom = sizeMin * 0.2f, top = 24.dp, start = 12.dp, end = 12.dp)
          .imePadding()
          .systemBarsPadding()
          .alpha(0.95f),
    shape = shape,
  ) {
    Row(
      Modifier.padding(15.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      toastData.visuals.icon?.let { Icon(it, null) }
        ?: toastData.visuals.painter?.let { Icon(painter = it, contentDescription = null) }
      Spacer(modifier = Modifier.size(8.dp))
      Text(
        style = MaterialTheme.typography.bodySmall,
        text = toastData.visuals.message,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(end = 5.dp),
      )
    }
  }
}

@Stable
class ToastHostState {
  private val mutex = Mutex()

  var currentToastData by mutableStateOf<ToastData?>(null)
    private set

  @OptIn(ExperimentalMaterial3Api::class)
  suspend fun showToast(
    message: String,
    icon: ImageVector? = null,
    painter: Painter? = null,
    duration: ToastDuration = ToastDuration.Short,
  ) = showToast(ToastVisualsImpl(message, icon, painter, duration))

  @ExperimentalMaterial3Api
  suspend fun showToast(visuals: ToastVisuals) =
    mutex.withLock {
      try {
        suspendCancellableCoroutine { continuation ->
          currentToastData = ToastDataImpl(visuals, continuation)
        }
      } finally {
        currentToastData = null
      }
    }

  private class ToastVisualsImpl(
    override val message: String,
    override val icon: ImageVector? = null,
    override val painter: Painter? = null,
    override val duration: ToastDuration,
  ) : ToastVisuals {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || this::class != other::class) return false

      other as ToastVisualsImpl

      if (message != other.message) return false
      if (icon != other.icon) return false
      if (duration != other.duration) return false
      return true
    }

    override fun hashCode(): Int {
      var result = message.hashCode()
      result = 31 * result + icon.hashCode()
      result = 31 * result + duration.hashCode()
      return result
    }
  }

  private class ToastDataImpl(
    override val visuals: ToastVisuals,
    private val continuation: CancellableContinuation<Unit>,
  ) : ToastData {

    override fun dismiss() {
      if (continuation.isActive) continuation.resume(Unit)
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || this::class != other::class) return false

      other as ToastDataImpl

      if (visuals != other.visuals) return false
      if (continuation != other.continuation) return false

      return true
    }

    override fun hashCode(): Int {
      var result = visuals.hashCode()
      result = 31 * result + continuation.hashCode()
      return result
    }
  }
}

@Stable
interface ToastData {
  val visuals: ToastVisuals

  fun dismiss()
}

@Stable
interface ToastVisuals {
  val message: String
  val icon: ImageVector?
  val duration: ToastDuration
  val painter: Painter?
}

enum class ToastDuration {
  Short,
  Long,
}

object ToastDefaults {
  val transition: ContentTransform
    get() =
      (fadeIn(tween(300)) +
          scaleIn(tween(500), transformOrigin = TransformOrigin(0.5f, 1f)) +
          slideInVertically(tween(500)) { it / 2 })
        .togetherWith(
          fadeOut(tween(250)) +
            slideOutVertically(tween(500)) { it / 2 } +
            scaleOut(tween(750), transformOrigin = TransformOrigin(0.5f, 1f))
        )

  val contentColor: Color
    @Composable get() = MaterialTheme.colorScheme.inverseOnSurface.harmonizeWithPrimary()

  val color: Color
    @Composable get() = MaterialTheme.colorScheme.inverseSurface.harmonizeWithPrimary()

  val shape: Shape
    @Composable get() = MaterialTheme.shapes.extraLarge
}

private fun ToastDuration.toMillis(accessibilityManager: AccessibilityManager?): Long {
  val original =
    when (this) {
      ToastDuration.Long -> 6500L
      ToastDuration.Short -> 3500L
    }
  return accessibilityManager?.calculateRecommendedTimeoutMillis(
    original,
    containsIcons = false,
    containsText = true,
  ) ?: original
}

private fun Color.blend(
  color: Color,
  @FloatRange(from = 0.0, to = 1.0) fraction: Float = 0.2f,
): Color = Color(ColorUtils.blendARGB(this.toArgb(), color.toArgb(), fraction))

@Composable
private fun Color.harmonizeWithPrimary(
  @FloatRange(from = 0.0, to = 1.0) fraction: Float = 0.2f
): Color = blend(MaterialTheme.colorScheme.primary, fraction)
