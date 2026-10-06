package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import com.example.ui.theme.MyApplicationTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    hideSystemBars()

    setContent {
      MyApplicationTheme {
        CityGameScreen()
      }
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      hideSystemBars()
    }
  }

  private fun hideSystemBars() {
    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
    windowInsetsController.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
  }
}

// Telemetry state shared with Compose
data class GameTelemetry(
  val x: String = "-150.0",
  val y: String = "18.0",
  val z: String = "136.0",
  val speedKmh: Int = 0,
  val headingDeg: Int = 0,
  val cameraMode: String = "street"
)

data class CityStats(
  val isLoaded: Boolean = false,
  val meshCount: Int = 0,
  val width: Int = 0,
  val depth: Int = 0
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CityGameScreen() {
  val context = LocalContextNullable()
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var telemetry by remember { mutableStateOf(GameTelemetry()) }
  var cityStats by remember { mutableStateOf(CityStats()) }

  var isSprinting by remember { mutableStateOf(false) }
  var currentCameraMode by remember { mutableStateOf("thirdperson") }
  var currentTimePreset by remember { mutableStateOf("day") }
  var showTeleportMenu by remember { mutableStateOf(false) }
  var showTimeMenu by remember { mutableStateOf(false) }

  // Game UI Root Box
  Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    // 3D Engine WebView
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { ctx ->
        WebView(ctx).apply {
          layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
          )
          setLayerType(View.LAYER_TYPE_HARDWARE, null)

          settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mediaPlaybackRequiresUserGesture = false
          }

          val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
            .build()

          webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
              view: WebView,
              request: WebResourceRequest
            ): WebResourceResponse? {
              return assetLoader.shouldInterceptRequest(request.url)
            }
          }

          addJavascriptInterface(object {
            @JavascriptInterface
            fun onCityLoaded(meshes: Int, w: Int, d: Int) {
              post {
                cityStats = CityStats(isLoaded = true, meshCount = meshes, width = w, depth = d)
              }
            }

            @JavascriptInterface
            fun onTelemetry(x: String, y: String, z: String, speed: Int, heading: Int, mode: String) {
              post {
                telemetry = GameTelemetry(
                  x = x,
                  y = y,
                  z = z,
                  speedKmh = speed,
                  headingDeg = heading,
                  cameraMode = mode
                )
              }
            }
          }, "AndroidBridge")

          loadUrl("https://appassets.androidplatform.net/assets/city.html")
          webViewRef = this
        }
      },
      update = { webView ->
        webViewRef = webView
      }
    )

    // Right-side Look Touch Area (rotates camera when dragging right half of screen)
    Box(
      modifier = Modifier
        .align(Alignment.CenterEnd)
        .fillMaxWidth(0.55f)
        .fillMaxSize()
        .pointerInput(Unit) {
          detectDragGestures { change, dragAmount ->
            change.consume()
            val deltaYaw = -dragAmount.x * 0.005f
            val deltaPitch = -dragAmount.y * 0.005f
            webViewRef?.evaluateJavascript(
              "window.gameAPI.addLookRotation($deltaYaw, $deltaPitch);",
              null
            )
          }
        }
    )

    // HUD Top Bar (Mini-map, Telemetry, Controls)
    TopHUDBar(
      telemetry = telemetry,
      cityStats = cityStats,
      currentCameraMode = currentCameraMode,
      currentTimePreset = currentTimePreset,
      onCameraModeSelected = { mode ->
        currentCameraMode = mode
        webViewRef?.evaluateJavascript("window.gameAPI.setCameraMode('$mode');", null)
      },
      onTimePresetSelected = { preset ->
        currentTimePreset = preset
        webViewRef?.evaluateJavascript("window.gameAPI.setTimeOfDay('$preset');", null)
      },
      onTeleport = { spawnKey ->
        webViewRef?.evaluateJavascript("window.gameAPI.teleportTo('$spawnKey');", null)
      }
    )

    // Bottom Controls: Virtual Joystick (Left) and Action Buttons (Right)
    Row(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Bottom
    ) {
      // Virtual Joystick (Left Thumb)
      VirtualAnalogJoystick(
        modifier = Modifier.testTag("virtual_joystick"),
        onMove = { normX, normY ->
          webViewRef?.evaluateJavascript(
            "window.gameAPI.setJoystick($normX, $normY);",
            null
          )
        }
      )

      // Right Action Buttons
      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Drone vertical flight controls (visible only in Drone mode)
        if (currentCameraMode == "drone") {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameActionButton(
              icon = Icons.Default.KeyboardArrowUp,
              label = "ASCEND",
              color = Color(0xFF0284C7),
              testTag = "drone_ascend_button",
              onClick = {
                webViewRef?.evaluateJavascript("window.gameAPI.setFlyVertical(1.0);", null)
              }
            )
            GameActionButton(
              icon = Icons.Default.KeyboardArrowDown,
              label = "DESCEND",
              color = Color(0xFF0284C7),
              testTag = "drone_descend_button",
              onClick = {
                webViewRef?.evaluateJavascript("window.gameAPI.setFlyVertical(-1.0);", null)
              }
            )
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          // Reset / Unstuck Button
          GameActionButton(
            icon = Icons.Default.Refresh,
            label = "RESPAWN",
            color = Color(0xFF475569),
            testTag = "respawn_button",
            onClick = {
              webViewRef?.evaluateJavascript("window.gameAPI.teleportTo('center');", null)
            }
          )

          // Jump Button (in Third-person / First-person mode)
          if (currentCameraMode == "thirdperson" || currentCameraMode == "firstperson") {
            GameActionButton(
              icon = Icons.Default.KeyboardArrowUp,
              label = "JUMP",
              color = Color(0xFF10B981),
              testTag = "jump_button",
              onClick = {
                webViewRef?.evaluateJavascript("window.gameAPI.jump();", null)
              }
            )
          }

          // Sprint Booster Button
          GameActionButton(
            icon = Icons.Default.Flight,
            label = if (isSprinting) "SPRINTING" else "SPRINT",
            color = if (isSprinting) Color(0xFFEA580C) else Color(0xFF64748B),
            isActive = isSprinting,
            testTag = "sprint_button",
            onClick = {
              isSprinting = !isSprinting
              webViewRef?.evaluateJavascript("window.gameAPI.setSprint($isSprinting);", null)
            }
          )
        }
      }
    }
  }
}

@Composable
fun TopHUDBar(
  telemetry: GameTelemetry,
  cityStats: CityStats,
  currentCameraMode: String,
  currentTimePreset: String,
  onCameraModeSelected: (String) -> Unit,
  onTimePresetSelected: (String) -> Unit,
  onTeleport: (String) -> Unit
) {
  var showCamMenu by remember { mutableStateOf(false) }
  var showTimeMenu by remember { mutableStateOf(false) }
  var showTeleportMenu by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top
  ) {
    // Mini-map & Coordinates Card
    Surface(
      color = Color(0xCC0B132B),
      shape = RoundedCornerShape(12.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x40FFFFFF)),
      shadowElevation = 8.dp
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Radar Compass Icon rotated by heading
        Box(
          modifier = Modifier
            .size(38.dp)
            .background(Color(0xFF1E293B), CircleShape)
            .border(1.5.dp, Color(0xFF38BDF8), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Navigation,
            contentDescription = "Compass",
            tint = Color(0xFF38BDF8),
            modifier = Modifier
              .size(20.dp)
              .rotate(telemetry.headingDeg.toFloat())
          )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
          Text(
            text = "POS: X ${telemetry.x} | Z ${telemetry.z}",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = "ALT: ${telemetry.y}m | DIR: ${telemetry.headingDeg}°",
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }

    // Center Speedometer & Game Badge
    Surface(
      color = Color(0xCC0F172A),
      shape = RoundedCornerShape(12.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33F97316))
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "${telemetry.speedKmh}",
            color = if (telemetry.speedKmh > 0) Color(0xFFF97316) else Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = "KM/H",
            color = Color(0xFF64748B),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
          modifier = Modifier
            .height(28.dp)
            .width(1.dp)
            .background(Color(0x33FFFFFF))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
          Text(
            text = "CITY 3D",
            color = Color(0xFFF97316),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
          )
          Text(
            text = if (cityStats.isLoaded) "WORLD & PROPS READY" else "LOADING CITY & PROPS...",
            color = if (cityStats.isLoaded) Color(0xFF10B981) else Color(0xFFEAB308),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }

    // Top-Right Control Buttons (Camera, Time of Day, Teleport)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      // Teleport Locations Button
      Box {
        HUDIconButton(
          icon = Icons.Default.Place,
          text = "SPAWN",
          testTag = "spawn_locations_button",
          onClick = { showTeleportMenu = true }
        )
        DropdownMenu(
          expanded = showTeleportMenu,
          onDismissRequest = { showTeleportMenu = false }
        ) {
          DropdownMenuItem(
            text = { Text("🏙️ Downtown Boulevard") },
            onClick = {
              onTeleport("center")
              showTeleportMenu = false
            }
          )
          DropdownMenuItem(
            text = { Text("💡 Street Light Poles") },
            onClick = {
              onTeleport("poles")
              showTeleportMenu = false
            }
          )
          DropdownMenuItem(
            text = { Text("🌳 Tree Boulevard") },
            onClick = {
              onTeleport("trees")
              showTeleportMenu = false
            }
          )
          DropdownMenuItem(
            text = { Text("🚦 Traffic Signals") },
            onClick = {
              onTeleport("traffic")
              showTeleportMenu = false
            }
          )
          DropdownMenuItem(
            text = { Text("🌉 Canal Bridge") },
            onClick = {
              onTeleport("bridge")
              showTeleportMenu = false
            }
          )
          DropdownMenuItem(
            text = { Text("🛣️ Elevated Highway") },
            onClick = {
              onTeleport("highway")
              showTeleportMenu = false
            }
          )
          DropdownMenuItem(
            text = { Text("🚀 Skyscraper Rooftop") },
            onClick = {
              onTeleport("rooftop")
              showTeleportMenu = false
            }
          )
        }
      }

      // Time of Day Selector
      Box {
        HUDIconButton(
          icon = Icons.Default.Brightness4,
          text = currentTimePreset.uppercase(),
          testTag = "time_preset_button",
          onClick = { showTimeMenu = true }
        )
        DropdownMenu(
          expanded = showTimeMenu,
          onDismissRequest = { showTimeMenu = false }
        ) {
          DropdownMenuItem(
            text = { Text("☀️ Day (Sunny)") },
            onClick = {
              onTimePresetSelected("day")
              showTimeMenu = false
            }
          )
          DropdownMenuItem(
            text = { Text("🌅 Sunset (Golden Hour)") },
            onClick = {
              onTimePresetSelected("sunset")
              showTimeMenu = false
            }
          )
          DropdownMenuItem(
            text = { Text("🌙 Night (Street Lights)") },
            onClick = {
              onTimePresetSelected("night")
              showTimeMenu = false
            }
          )
          DropdownMenuItem(
            text = { Text("🏙️ Cyberpunk Neon") },
            onClick = {
              onTimePresetSelected("cyberpunk")
              showTimeMenu = false
            }
          )
        }
      }

      // Camera Mode Selector
      Box {
        HUDIconButton(
          icon = Icons.Default.Visibility,
          text = currentCameraMode.uppercase(),
          testTag = "camera_mode_button",
          onClick = { showCamMenu = true }
        )
        DropdownMenu(
          expanded = showCamMenu,
          onDismissRequest = { showCamMenu = false }
        ) {
          DropdownMenuItem(
            text = { Text("🚶 3rd Person View (GTA)") },
            onClick = {
              onCameraModeSelected("thirdperson")
              showCamMenu = false
            }
          )
          DropdownMenuItem(
            text = { Text("👀 1st Person View") },
            onClick = {
              onCameraModeSelected("firstperson")
              showCamMenu = false
            }
          )
          DropdownMenuItem(
            text = { Text("🛸 Drone / Free Fly") },
            onClick = {
              onCameraModeSelected("drone")
              showCamMenu = false
            }
          )
          DropdownMenuItem(
            text = { Text("🌐 Cinematic Orbit") },
            onClick = {
              onCameraModeSelected("orbit")
              showCamMenu = false
            }
          )
          DropdownMenuItem(
            text = { Text("🗺️ Top-Down Map View") },
            onClick = {
              onCameraModeSelected("topdown")
              showCamMenu = false
            }
          )
        }
      }
    }
  }
}

@Composable
fun HUDIconButton(
  icon: ImageVector,
  text: String,
  testTag: String,
  onClick: () -> Unit
) {
  Surface(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .testTag(testTag),
    color = Color(0xCC0F172A),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x40FFFFFF)),
    shape = RoundedCornerShape(8.dp)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = icon,
        contentDescription = text,
        tint = Color(0xFFF97316),
        modifier = Modifier.size(16.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = text,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

// Virtual Joystick (Custom Jetpack Compose Analog Touch Stick)
@Composable
fun VirtualAnalogJoystick(
  modifier: Modifier = Modifier,
  sizeDp: Float = 140f,
  knobDp: Float = 54f,
  onMove: (normX: Float, normY: Float) -> Unit
) {
  val maxRadiusPx = (sizeDp - knobDp) / 2f
  var knobOffset by remember { mutableStateOf(Offset.Zero) }

  Box(
    modifier = modifier
      .size(sizeDp.dp)
      .clip(CircleShape)
      .background(
        brush = Brush.radialGradient(
          colors = listOf(Color(0x771E293B), Color(0x990F172A))
        )
      )
      .border(2.dp, Color(0x55F97316), CircleShape)
      .pointerInput(Unit) {
        detectDragGestures(
          onDragStart = { offset ->
            val center = Offset(size.width / 2f, size.height / 2f)
            val diff = offset - center
            val dist = diff.getDistance()
            val clampedDist = dist.coerceAtMost(maxRadiusPx * density)
            val angle = atan2(diff.y, diff.x)
            val newOffset = Offset(cos(angle) * clampedDist, sin(angle) * clampedDist)
            knobOffset = newOffset

            val normX = (newOffset.x / (maxRadiusPx * density)).coerceIn(-1f, 1f)
            val normY = (newOffset.y / (maxRadiusPx * density)).coerceIn(-1f, 1f)
            onMove(normX, normY)
          },
          onDrag = { change, dragAmount ->
            change.consume()
            val candidate = knobOffset + dragAmount
            val dist = candidate.getDistance()
            val maxPx = maxRadiusPx * density
            val newOffset = if (dist > maxPx) {
              val angle = atan2(candidate.y, candidate.x)
              Offset(cos(angle) * maxPx, sin(angle) * maxPx)
            } else {
              candidate
            }
            knobOffset = newOffset

            val normX = (newOffset.x / maxPx).coerceIn(-1f, 1f)
            val normY = (newOffset.y / maxPx).coerceIn(-1f, 1f)
            onMove(normX, normY)
          },
          onDragEnd = {
            knobOffset = Offset.Zero
            onMove(0f, 0f)
          },
          onDragCancel = {
            knobOffset = Offset.Zero
            onMove(0f, 0f)
          }
        )
      },
    contentAlignment = Alignment.Center
  ) {
    // Center Guide Crosshair
    Box(
      modifier = Modifier
        .size(16.dp)
        .background(Color(0x33FFFFFF), CircleShape)
    )

    // Joystick Movable Knob
    Box(
      modifier = Modifier
        .offset { IntOffset(knobOffset.x.roundToInt(), knobOffset.y.roundToInt()) }
        .size(knobDp.dp)
        .shadow(6.dp, CircleShape)
        .clip(CircleShape)
        .background(
          brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFB923C), Color(0xFFC2410C))
          )
        )
        .border(2.dp, Color(0xFFFED7AA), CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Box(
        modifier = Modifier
          .size(14.dp)
          .background(Color(0x66FFFFFF), CircleShape)
      )
    }
  }
}

@Composable
fun GameActionButton(
  icon: ImageVector,
  label: String,
  color: Color,
  isActive: Boolean = false,
  testTag: String,
  onClick: () -> Unit
) {
  Surface(
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .testTag(testTag),
    color = if (isActive) color else Color(0xCC0F172A),
    border = androidx.compose.foundation.BorderStroke(
      2.dp,
      if (isActive) Color.White else color.copy(alpha = 0.6f)
    ),
    shape = RoundedCornerShape(12.dp),
    shadowElevation = 6.dp
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (isActive) Color.White else color,
        modifier = Modifier.size(20.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black
      )
    }
  }
}

@Composable
fun LocalContextNullable(): Context {
  return LocalContext.current
}
