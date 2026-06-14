package com.nitshift.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nitshift.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.roundToInt

enum class UiStyle {
    NEUMORPHIC_SLATE,
    MIDNIGHT_OLED
}

class MainActivity : ComponentActivity() {

    private val checkPermissionFlow = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = Color.Transparent // Controlled dynamically by Selected Style
                ) { innerPadding ->
                    BrightnessDashboard(
                        modifier = Modifier,
                        innerPadding = innerPadding,
                        checkPermissionFlow = checkPermissionFlow
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("BrightnessPrefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("service_enabled", false)
        val applyBg = prefs.getBoolean("apply_in_background", true)
        
        BrightnessState.setServiceEnabled(enabled)
        BrightnessState.setApplyInBackground(applyBg)

        if (enabled && Settings.System.canWrite(this)) {
            val serviceIntent = Intent(this, BrightnessService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val prefs = getSharedPreferences("BrightnessPrefs", Context.MODE_PRIVATE)
        val applyBg = prefs.getBoolean("apply_in_background", true)
        if (!applyBg) {
            val serviceIntent = Intent(this, BrightnessService::class.java)
            stopService(serviceIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissionFlow.value = !checkPermissionFlow.value
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrightnessDashboard(
    modifier: Modifier = Modifier,
    innerPadding: PaddingValues = PaddingValues(),
    checkPermissionFlow: StateFlow<Boolean>
) {
    val context = LocalContext.current
    
    // Persist and load selected style preference
    val stylePrefs = remember { context.getSharedPreferences("UiStylePrefs", Context.MODE_PRIVATE) }
    var currentStyle by remember {
        mutableStateOf(
            run {
                val saved = stylePrefs.getString("SELECTED_STYLE", UiStyle.NEUMORPHIC_SLATE.name) ?: UiStyle.NEUMORPHIC_SLATE.name
                try {
                    UiStyle.valueOf(saved)
                } catch (e: Exception) {
                    UiStyle.NEUMORPHIC_SLATE
                }
            }
        )
    }

    // Permission check flow
    val checkTrigger by checkPermissionFlow.collectAsStateWithLifecycle(initialValue = false)
    var hasWritePermission by remember { mutableStateOf(false) }

    LaunchedEffect(checkTrigger) {
        hasWritePermission = Settings.System.canWrite(context)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Brightness Service Data states
    val currentLux by BrightnessState.currentLux.collectAsStateWithLifecycle()
    val appliedBrightness by BrightnessState.appliedBrightness.collectAsStateWithLifecycle()
    val userOffset by BrightnessState.userOffset.collectAsStateWithLifecycle()
    val isRunning by BrightnessState.isServiceRunning.collectAsStateWithLifecycle()
    val applyInBackground by BrightnessState.applyInBackground.collectAsStateWithLifecycle()
    val isServiceEnabled by BrightnessState.isServiceEnabled.collectAsStateWithLifecycle()

    // ----------------------------------------------------
    // STYLE PALETTES (Dynamic Mapping based on UiStyle)
    // ----------------------------------------------------
    val styleMainGradient = when (currentStyle) {
        UiStyle.NEUMORPHIC_SLATE -> Brush.verticalGradient(listOf(Color(0xFF636F82), Color(0xFF3B4453)))
        UiStyle.MIDNIGHT_OLED -> Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF0B0F13)))
    }
    
    val cardBgColor = when (currentStyle) {
        UiStyle.NEUMORPHIC_SLATE -> Color(0xFF4C586B)
        UiStyle.MIDNIGHT_OLED -> Color(0xFF090C0E)
    }

    val labelTextCol = when (currentStyle) {
        UiStyle.NEUMORPHIC_SLATE -> Color(0xFFCAD1DC)
        UiStyle.MIDNIGHT_OLED -> Color(0xFF5A635F)
    }

    val mainTextCol = when (currentStyle) {
        UiStyle.NEUMORPHIC_SLATE -> Color.White
        UiStyle.MIDNIGHT_OLED -> Color(0xFF00FF88) // Cyberpunk laser emerald
    }

    val headerTextCol = when (currentStyle) {
        UiStyle.NEUMORPHIC_SLATE -> Color.White
        UiStyle.MIDNIGHT_OLED -> Color(0xFF00FF88).copy(alpha = 0.85f)
    }

    val accentElementCol = when (currentStyle) {
        UiStyle.NEUMORPHIC_SLATE -> Color(0xFFFFB74D) // Radiant golden sun
        UiStyle.MIDNIGHT_OLED -> Color(0xFFFFB703)     // Warning orange glow
    }

    val accentContainerCol = when (currentStyle) {
        UiStyle.NEUMORPHIC_SLATE -> Color(0x22FFFFFF)
        UiStyle.MIDNIGHT_OLED -> Color(0xFF14241B)
    }

    val uiBorderColor = when (currentStyle) {
        UiStyle.NEUMORPHIC_SLATE -> Color(0x1AFFFFFF)
        UiStyle.MIDNIGHT_OLED -> Color(0x3D00FF88)  // Neon green border glow
    }

    val uiFontFamily = when (currentStyle) {
        UiStyle.NEUMORPHIC_SLATE -> FontFamily.SansSerif
        UiStyle.MIDNIGHT_OLED -> FontFamily.Monospace
    }

    val uiCardShape = when (currentStyle) {
        UiStyle.NEUMORPHIC_SLATE -> RoundedCornerShape(26.dp)
        UiStyle.MIDNIGHT_OLED -> RoundedCornerShape(8.dp) // Industrial hard edges
    }

    // Interactive Service controller helpers
    val startBrightnessService = {
        val serviceIntent = Intent(context, BrightnessService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    val stopBrightnessService = {
        val serviceIntent = Intent(context, BrightnessService::class.java)
        context.stopService(serviceIntent)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(styleMainGradient)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 36.dp,
                bottom = innerPadding.calculateBottomPadding() + 48.dp
            )
        ) {

            // Android settings permission warning placard
            if (!hasWritePermission) {
                item {
                    PermissionExplanationCard(
                        onGrantClicked = {
                            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        },
                        uiFontFamily = uiFontFamily,
                        uiCardShape = uiCardShape
                    )
                }
            }

            // SECTION 1: EXACTLY 2 TOP CARDS (Lux Sensor & Screen Nits)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Card 1: Ambient light sensor reading
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        shape = uiCardShape,
                        border = BorderStroke(1.dp, uiBorderColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 22.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "SENSOR LUX",
                                fontFamily = uiFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = labelTextCol,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (isRunning) String.format(Locale.getDefault(), "%.0f", currentLux) else "0",
                                    fontFamily = uiFontFamily,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = mainTextCol
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "flux",
                                    fontFamily = uiFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = labelTextCol,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                    }

                    // Card 2: Calibrated output nits estimation
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        shape = uiCardShape,
                        border = BorderStroke(1.dp, uiBorderColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 22.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "SCREEN NITS",
                                fontFamily = uiFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = labelTextCol,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                val nits = if (isRunning && appliedBrightness > 0) {
                                    val ratio = (appliedBrightness - 10).toFloat() / (255f - 10f)
                                    (2.0f + ratio.coerceIn(0f, 1f) * 798.0f).roundToInt()
                                } else {
                                    0
                                }
                                Text(
                                    text = nits.toString(),
                                    fontFamily = uiFontFamily,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = mainTextCol
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "nits",
                                    fontFamily = uiFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = labelTextCol,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 2: CLEAN MINIMALISTIC PERCENTAGE SLIDER CONTROL WITH STEP BUTTONS SIDE-BY-SIDE
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    shape = uiCardShape,
                    border = BorderStroke(1.dp, uiBorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Slider title block
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Offset",
                                fontFamily = uiFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = mainTextCol
                            )
                            Text(
                                text = if (userOffset > 0) "+$userOffset%" else if (userOffset < 0) "$userOffset%" else "0%",
                                fontFamily = uiFontFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = accentElementCol
                            )
                        }

                        // Flex Slider Container wrapping step buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Left Digital Decremer Button (-5%) - Tap without dragging
                            IconButton(
                                onClick = {
                                    val nextOffset = (userOffset - 5).coerceIn(-100, 100)
                                    BrightnessState.setUserOffset(nextOffset)
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(accentContainerCol, CircleShape)
                                    .border(1.dp, uiBorderColor, CircleShape)
                                    .testTag("decrease_btn_left")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Remove,
                                    contentDescription = "Step Down Offset",
                                    tint = accentElementCol,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Horizontal minimalist custom track
                            Slider(
                                value = userOffset.toFloat(),
                                onValueChange = { newValue ->
                                    val rounded = (newValue / 5f).roundToInt() * 5
                                    BrightnessState.setUserOffset(rounded)
                                },
                                valueRange = -100f..100f,
                                steps = 39, // Discrete increments of 5%
                                colors = SliderDefaults.colors(
                                    activeTrackColor = accentElementCol,
                                    inactiveTrackColor = labelTextCol.copy(alpha = 0.18f),
                                    thumbColor = accentElementCol,
                                    activeTickColor = Color.Transparent,
                                    inactiveTickColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                                    .testTag("custom_sleek_slider")
                            )

                            // Right Digital Incremer Button (+5%) - Tap without dragging
                            IconButton(
                                onClick = {
                                    val nextOffset = (userOffset + 5).coerceIn(-100, 100)
                                    BrightnessState.setUserOffset(nextOffset)
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(accentContainerCol, CircleShape)
                                    .border(1.dp, uiBorderColor, CircleShape)
                                    .testTag("increase_btn_right")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Step Up Offset",
                                    tint = accentElementCol,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 3: GROUPED TOGGLES (Service Active & Apply Background)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    shape = uiCardShape,
                    border = BorderStroke(1.dp, uiBorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Toggle 1: Service Active
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(accentContainerCol, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.WbSunny,
                                        contentDescription = "Service Active Status Icon",
                                        tint = accentElementCol,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Service Active",
                                        fontFamily = uiFontFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = mainTextCol
                                    )
                                    Text(
                                        text = "Regulate auto lumens",
                                        fontFamily = uiFontFamily,
                                        fontSize = 11.sp,
                                        color = labelTextCol
                                    )
                                }
                            }

                            Switch(
                                checked = isServiceEnabled,
                                onCheckedChange = { checked ->
                                    if (!hasWritePermission) {
                                        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } else {
                                        BrightnessState.setServiceEnabled(checked)
                                        context.getSharedPreferences("BrightnessPrefs", Context.MODE_PRIVATE)
                                            .edit()
                                            .putBoolean("service_enabled", checked)
                                            .apply()
                                        if (checked) {
                                            startBrightnessService()
                                        } else {
                                            stopBrightnessService()
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = if (currentStyle == UiStyle.NEUMORPHIC_SLATE) Color.White else Color.Black,
                                    checkedTrackColor = accentElementCol,
                                    checkedBorderColor = if (currentStyle == UiStyle.NEUMORPHIC_SLATE) accentElementCol else Color.Transparent,
                                    uncheckedThumbColor = labelTextCol,
                                    uncheckedTrackColor = cardBgColor.copy(alpha = 0.5f),
                                    uncheckedBorderColor = labelTextCol.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.testTag("master_service_switch")
                            )
                        }

                        // Minimal, elegant matching separator
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(uiBorderColor)
                        )

                        // Toggle 2: Apply in Background
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(accentContainerCol, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Cached,
                                        contentDescription = "Background Execution Status Icon",
                                        tint = accentElementCol,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Apply in Background",
                                        fontFamily = uiFontFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = mainTextCol
                                    )
                                    Text(
                                        text = "Remain active off-screen",
                                        fontFamily = uiFontFamily,
                                        fontSize = 11.sp,
                                        color = labelTextCol
                                    )
                                }
                            }

                            Switch(
                                checked = applyInBackground,
                                onCheckedChange = { checked ->
                                    BrightnessState.setApplyInBackground(checked)
                                    context.getSharedPreferences("BrightnessPrefs", Context.MODE_PRIVATE)
                                        .edit()
                                        .putBoolean("apply_in_background", checked)
                                        .apply()
                                    if (BrightnessState.isServiceEnabled.value) {
                                        if (checked) {
                                            startBrightnessService()
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = if (currentStyle == UiStyle.NEUMORPHIC_SLATE) Color.White else Color.Black,
                                    checkedTrackColor = accentElementCol,
                                    checkedBorderColor = if (currentStyle == UiStyle.NEUMORPHIC_SLATE) accentElementCol else Color.Transparent,
                                    uncheckedThumbColor = labelTextCol,
                                    uncheckedTrackColor = cardBgColor.copy(alpha = 0.5f),
                                    uncheckedBorderColor = labelTextCol.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.testTag("apply_background_switch")
                            )
                        }
                    }
                }
            }

            // SECTION 4: THEME SELECTION CARD (at the bottom)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor.copy(alpha = 0.8f)),
                    shape = uiCardShape,
                    border = BorderStroke(1.dp, uiBorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "THEME",
                            fontFamily = uiFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = labelTextCol,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UiStyle.values().forEach { style ->
                                val isSelected = currentStyle == style
                                val btnBg = if (isSelected) accentElementCol else Color.Transparent
                                val btnText = if (isSelected) Color.Black else labelTextCol
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(btnBg)
                                        .border(
                                            1.dp,
                                            if (isSelected) Color.Transparent else uiBorderColor,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            currentStyle = style
                                            stylePrefs
                                                .edit()
                                                .putString("SELECTED_STYLE", style.name)
                                                .apply()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (style) {
                                            UiStyle.NEUMORPHIC_SLATE -> "Slate"
                                            UiStyle.MIDNIGHT_OLED -> "Midnight"
                                        },
                                        fontFamily = uiFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = btnText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionExplanationCard(
    onGrantClicked: () -> Unit,
    uiFontFamily: FontFamily,
    uiCardShape: RoundedCornerShape
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0x33FF0000)),
        shape = uiCardShape,
        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Missing write permission warning icon",
                    tint = Color.Red,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "System Permission Required",
                    fontFamily = uiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.Red
                )
            }
            Text(
                text = "Screen dynamic dimming requires setting modification privileges. Please grant below to activate system automation:",
                fontFamily = uiFontFamily,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
            Button(
                onClick = onGrantClicked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("grant_settings_btn")
            ) {
                Text(
                    text = "Grant Permission",
                    fontFamily = uiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
