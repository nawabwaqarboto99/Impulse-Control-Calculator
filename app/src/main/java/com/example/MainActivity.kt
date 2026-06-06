package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ImpulseCalculatorApp()
            }
        }
    }
}

// Data models
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val calculationDetails: CalculationResult? = null
)

data class CalculationResult(
    val price: Double,
    val wage: Double,
    val hours: Double
)

// UI State class maintaining local session
class ImpulseCalculatorState(
    initialWage: Double? = null,
    private val onActionTriggered: (Int) -> Unit = {}
) {
    var messages by mutableStateOf<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "👋 Welcome to the **Impulse Calculator**.\n\nPlease state your hourly wage to get started (e.g., **20** or **35**).",
                isUser = false
            )
        )
    )
        private set

    var hourlyWage by mutableStateOf<Double?>(initialWage)
        private set

    var isTyping by mutableStateOf(false)
        private set

    fun handleInput(text: String, scope: kotlinx.coroutines.CoroutineScope) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // 1. Add User Input Bubble
        messages = messages + ChatMessage(text = trimmed, isUser = true)
        onActionTriggered(messages.size)

        val activeWage = hourlyWage

        scope.launch {
            isTyping = true
            delay(400) // Brief calculation latency animation for premium feel
            isTyping = false

            // Check if user requested changing / resetting hourly wage
            if (trimmed.lowercase(Locale.ROOT) == "change wage") {
                hourlyWage = null
                messages = messages + ChatMessage(
                    text = "Wage cleared. Please state your hourly wage.",
                    isUser = false
                )
                onActionTriggered(messages.size)
                return@launch
            }

            // Scenario 1: Hourly wage is not configured yet
            if (activeWage == null) {
                val parsedWage = parseNumber(trimmed)
                if (parsedWage == null || parsedWage <= 0.0) {
                    messages = messages + ChatMessage(
                        text = "I couldn't parse that wage. Please provide a valid hourly wage (e.g., **$20** or **35.50**).",
                        isUser = false
                    )
                } else {
                    hourlyWage = parsedWage
                    messages = messages + ChatMessage(
                        text = "Got it! Your hourly wage is saved at **$${String.format("%.2f", parsedWage)}/hr**.\n\nNow, enter any product price (e.g., **120**) to calculate its work-hours cost.",
                        isUser = false
                    )
                }
                onActionTriggered(messages.size)
                return@launch
            }

            // Scenario 2: Wage is configured, parsing price
            val parsedPrice = parseNumber(trimmed)
            if (parsedPrice == null || parsedPrice <= 0.0) {
                messages = messages + ChatMessage(
                    text = "Please enter a valid product price (e.g., **120** or **49.99**) so I can evaluate its cost.",
                    isUser = false
                )
            } else {
                val hours = parsedPrice / activeWage
                val hoursText = if (hours % 1.0 == 0.0) {
                    "${hours.toInt()} hours"
                } else {
                    "${String.format("%.1f", hours)} hours"
                }

                // Strictly aligned bold format with under 2 sentences
                val responseMsg = "⚠️ This item costs **$hoursText** of your life/work. Is it really worth it?"

                messages = messages + ChatMessage(
                    text = responseMsg,
                    isUser = false,
                    calculationDetails = CalculationResult(
                        price = parsedPrice,
                        wage = activeWage,
                        hours = hours
                    )
                )
            }
            onActionTriggered(messages.size)
        }
    }

    fun triggerReset(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            hourlyWage = null
            messages = listOf(
                ChatMessage(
                    text = "👋 Welcome. Please enter your hourly wage (e.g., **20**).",
                    isUser = false
                )
            )
            onActionTriggered(messages.size)
        }
    }

    private fun parseNumber(text: String): Double? {
        val pattern = """\d+(\.\d+)?""".toRegex()
        val match = pattern.find(text)
        return match?.value?.toDoubleOrNull()
    }
}

// Markdown parser translates **bold** phrases into formatted RichText in Compose
fun parseMarkdownToAnnotatedString(text: String, boldColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            val start = text.indexOf("**", cursor)
            if (start == -1) {
                append(text.substring(cursor))
                break
            }
            append(text.substring(cursor, start))
            val end = text.indexOf("**", start + 2)
            if (end == -1) {
                append(text.substring(start))
                break
            }
            pushStyle(SpanStyle(fontWeight = FontWeight.Black, color = boldColor))
            append(text.substring(start + 2, end))
            pop()
            cursor = end + 2
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImpulseCalculatorApp() {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Initialize state
    val appState = remember {
        ImpulseCalculatorState { messageCount ->
            // Callback to scroll to bottom upon updates
            coroutineScope.launch {
                delay(100)
                if (messageCount > 0) {
                    scrollState.animateScrollToItem(messageCount - 1)
                }
            }
        }
    }

    var textInput by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianDark),
        containerColor = ObsidianDark,
        contentWindowInsets = WindowInsets.safeDrawing // Ensure edge-to-edge + notch + keyboard handling
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Dashboard Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalSurface)
                    .border(width = 1.dp, color = Color(0x1F9E9E9E))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "IMPULSE COST GUARD",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AmberGold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "Real-time life hours translation",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateGray
                        )
                    }

                    // Interactive Status Badge representing Hourly Wage
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (appState.hourlyWage != null) Color(0x19FFA000) else Color(0x119E9E9E))
                            .clickable(enabled = appState.hourlyWage != null) {
                                appState.handleInput("change wage", coroutineScope)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("change_wage_indicator")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (appState.hourlyWage != null) AmberGold else SlateGray)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (appState.hourlyWage != null) {
                                "$${String.format("%.2f", appState.hourlyWage)}/hr"
                            } else {
                                "Wage unset"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (appState.hourlyWage != null) AmberGold else SlateGray
                        )
                        if (appState.hourlyWage != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Change Wage",
                                tint = AmberGold,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // 2. Chat Log Stream
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("message_list"),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(appState.messages, key = { it.id }) { message ->
                    val alignment = if (message.isUser) Alignment.End else Alignment.Start
                    val bubbleColor = if (message.isUser) Color(0xFF24242A) else CharcoalSurface
                    val textAlignment = if (message.isUser) TextAlign.End else TextAlign.Start

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("message_card_${message.id}"),
                        horizontalAlignment = alignment
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!message.isUser) {
                                // Guard Avatar Icon
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Brush.radialGradient(listOf(AmberGold, AmberWarning)))
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Guard Logo",
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                            }

                            // Message Bubble
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .widthIn(max = 280.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (message.isUser) 16.dp else 2.dp,
                                            bottomEnd = if (message.isUser) 2.dp else 16.dp
                                        )
                                    )
                                    .background(bubbleColor)
                                    .border(
                                        width = 1.dp,
                                        color = if (!message.isUser && message.calculationDetails != null) AmberGold.copy(alpha = 0.4f) else Color(0x339E9E9E),
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (message.isUser) 16.dp else 2.dp,
                                            bottomEnd = if (message.isUser) 2.dp else 16.dp
                                        )
                                    )
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Text(
                                        text = parseMarkdownToAnnotatedString(
                                            message.text,
                                            boldColor = if (message.isUser) Color.White else AmberGold
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (message.isUser) Color.White else Color(0xFFECEFF1),
                                        textAlign = textAlignment,
                                        lineHeight = 20.sp
                                    )

                                    // Render custom high-impact visual WorkdayCostGauge if calculation details exist
                                    if (message.calculationDetails != null) {
                                        WorkdayCostGauge(
                                            hours = message.calculationDetails.hours,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }

                            if (message.isUser) {
                                Spacer(modifier = Modifier.width(10.dp))
                                // User Avatar Icon
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF37474F)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "U",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // Simulated chat calculating pulse
                if (appState.isTyping) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 42.dp)
                        ) {
                            Text(
                                text = "Evaluating true cost...",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = AmberGold
                            )
                        }
                    }
                }
            }

            // 3. Horizontal Recommendation / Suggestion Chips
            val suggestions = if (appState.hourlyWage == null) {
                listOf("$15/hr", "$25/hr", "$40/hr", "$60/hr")
            } else {
                listOf("$12", "$55", "$140", "$599", "$1200", "Change Wage")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x0A000000))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { chipText ->
                    AssistChip(
                        onClick = {
                            keyboardController?.hide()
                            val cleanMsg = chipText.replace("/hr", "")
                            appState.handleInput(cleanMsg, coroutineScope)
                        },
                        label = {
                            Text(
                                text = chipText,
                                color = if (chipText == "Change Wage") AmberWarning else Color.White
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = CharcoalSurface,
                            labelColor = Color.White
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (chipText == "Change Wage") AmberWarning.copy(alpha = 0.5f) else Color(0x409E9E9E)
                        ),
                        modifier = Modifier.testTag("suggestion_chip_${chipText}")
                    )
                }
            }

            // 4. Action Input Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalSurface)
                    .border(width = 1.dp, color = Color(0x1F9E9E9E))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = {
                            Text(
                                text = if (appState.hourlyWage == null) {
                                    "State your wage (e.g. 20)..."
                                } else {
                                    "Enter price (e.g. 120)..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateGray
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianDark,
                            unfocusedContainerColor = ObsidianDark,
                            focusedBorderColor = AmberGold,
                            unfocusedBorderColor = Color(0x339E9E9E),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (appState.hourlyWage == null) KeyboardType.Number else KeyboardType.Number,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (textInput.trim().isNotEmpty()) {
                                    appState.handleInput(textInput, coroutineScope)
                                    textInput = ""
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (textInput.trim().isNotEmpty()) {
                                appState.handleInput(textInput, coroutineScope)
                                textInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(AmberGold, AmberWarning)))
                            .testTag("send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Calculate",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorkdayCostGauge(hours: Double, modifier: Modifier = Modifier) {
    val totalWorkdayHours = 8.0
    val fullDays = (hours / totalWorkdayHours).toInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(Color(0x1F000000), shape = RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Work hours impact",
                    tint = AmberGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "WORKDAY EQUIVALENT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = SlateGray,
                    letterSpacing = 1.sp
                )
            }
            
            val pct = (hours / totalWorkdayHours * 100).toInt()
            Text(
                text = if (fullDays > 0) "${String.format("%.1f", hours / totalWorkdayHours)} Days" else "$pct% of a Day",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (hours >= 8.0) AmberWarning else AmberGold,
                modifier = Modifier
                    .background(
                        if (hours >= 8.0) Color(0x33FF3D00) else Color(0x33FFB300),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 1..8) {
                val blockHourValue = i.toDouble()
                val isFilled = hours >= blockHourValue
                val isFractional = !isFilled && hours > (blockHourValue - 1.0)
                val fractionFill = if (isFractional) (hours - (blockHourValue - 1.0)) else 0.0

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x339E9E9E))
                ) {
                    if (isFilled) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(AmberGold, AmberWarning)
                                    )
                                )
                        )
                    } else if (isFractional) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fractionFill.toFloat())
                                .background(AmberGold)
                        )
                    }
                }
            }
        }
        
        if (hours > 8.0) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning Indicator",
                    tint = AmberWarning,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                val workdaysCount = String.format("%.1f", hours / 8.0)
                Text(
                    text = "Requires $workdaysCount 8h work shift energies.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateGray
                )
            }
        }
    }
}
