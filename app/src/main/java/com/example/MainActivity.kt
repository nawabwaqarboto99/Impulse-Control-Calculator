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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Check
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.OkHttpClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Gemini API REST response classes
@com.squareup.moshi.JsonClass(generateAdapter = false)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)

@com.squareup.moshi.JsonClass(generateAdapter = false)
data class GeminiCandidate(val content: GeminiContent?)

@com.squareup.moshi.JsonClass(generateAdapter = false)
data class GeminiContent(val parts: List<GeminiPart>?)

@com.squareup.moshi.JsonClass(generateAdapter = false)
data class GeminiPart(val text: String?)

fun generateMathematicalFallback(
    wage: Double,
    itemPrice: Double,
    goalName: String,
    goalCost: Double,
    currencySymbol: String = "$"
): String {
    val impulseHours = itemPrice / wage
    val goalHours = goalCost / wage
    val setbackHours = impulseHours

    val impulseHoursStr = if (impulseHours % 1.0 == 0.0) "${impulseHours.toInt()}" else String.format(Locale.US, "%.1f", impulseHours)
    val goalHoursStr = if (goalHours % 1.0 == 0.0) "${goalHours.toInt()}" else String.format(Locale.US, "%.1f", goalHours)
    val setbackHoursStr = if (setbackHours % 1.0 == 0.0) "${setbackHours.toInt()}" else String.format(Locale.US, "%.1f", setbackHours)

    return """
        ⚠️ This item costs **$impulseHoursStr hours** of your life (${currencySymbol}${String.format(Locale.US, "%.2f", itemPrice)}).
        🎯 Your goal ($goalName) requires **$goalHoursStr total hours** of work (${currencySymbol}${String.format(Locale.US, "%.2f", goalCost)}).
        📉 Buying this item delays your target by adding **$setbackHoursStr hours** more work toward your goal.
    """.trimIndent()
}

suspend fun callGeminiOpportunityCostEngine(
    wage: Double,
    itemPrice: Double,
    goalName: String,
    goalCost: Double,
    persona: String = "🛡️ Defensive Guardian",
    currencySymbol: String = "$",
    useAI: Boolean = true
): String = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.GEMINI_API_KEY
    if (!useAI || apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
        return@withContext generateMathematicalFallback(wage, itemPrice, goalName, goalCost, currencySymbol)
    }

    val personaDirective = when {
        persona.contains("Tough") || persona.contains("💀") -> "Adopt a brutally honest, severe, and direct tone. Critically shame the user for wasting hard-worked money on junk instead of saving."
        persona.contains("Philosopher") || persona.contains("🎓") -> "Adopt a deep, thoughtful, philosophical tone. Discuss time as capital, human temporal expenditure, and long-term serenity."
        persona.contains("Hype") || persona.contains("🎉") -> "Adopt a high-energy, encouraging, celebratory cheerleading attitude. Show how close they are to their goal if they resist!"
        else -> "Adopt a defensive, steel-clad, logical protective guardian tone. Emphasize work-shifts depletion and guarding their lifepower."
    }

    val systemInstructionText = """
        You are a real-time, database-free "Financial Opportunity Cost Engine" designed for an Android app interface. Your job is to process live input numbers and provide instantaneous, high-impact psychological spending assessments.

        Persona Directive:
        $personaDirective

        Operational Rules:
        1. NEVER expect, query, or attempt to write to a database. You process everything dynamically on a per-session request basis.
        2. Expect the front-end user interface to pass a single packet containing the inputs: [Hourly Wage]: $wage, [Impulse Item Price]: $itemPrice, [Primary Saving Goal Name]: $goalName, and [Primary Saving Goal Cost]: $goalCost using currency symbol: $currencySymbol.
        3. For every incoming calculation request, compute and output exactly three things in a punchy, ultra-scannable format:
           - **True Hourly Cost:** The hours of work needed to buy the impulse item.
           - **Goal Payoff Progress:** The total hours of work needed to clear the primary saving goal entirely.
           - **The Opportunity Cost Impact:** A direct, psychological comparison showing how much buying the impulse item sets back their primary goal.

        Strict Output Format Constraint:
        Do not include conversational filler, introductory text, or closing advice. Respond strictly within this three-bullet structure, but colored perfectly with your assigned personality tone!

        ⚠️ This item costs **[Calculated Hours] hours** of your life.
        🎯 Your goal ([Goal Name]) requires **[Calculated Goal Hours] total hours** of work.
        📉 Buying this item delays your target by adding **[Calculated Setback Hours] hours** more work toward your goal.
    """.trimIndent()

    val promptText = "[Hourly Wage]: $wage, [Impulse Item Price]: $itemPrice, [Primary Saving Goal Name]: $goalName, [Primary Saving Goal Cost]: $goalCost, [Currency Symbol]: $currencySymbol"

    // Escape characters safely for JSON
    val escapedInstruction = systemInstructionText.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    val escapedPrompt = promptText.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    val jsonRequest = """
        {
          "contents": [{
            "parts": [{
              "text": "$escapedPrompt"
            }]
          }],
          "systemInstruction": {
            "parts": [{
              "text": "$escapedInstruction"
            }]
          }
        }
    """.trimIndent()

    val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val mediaType = "application/json; charset=utf-8".toMediaType()
    val body = jsonRequest.toRequestBody(mediaType)
    val request = Request.Builder()
        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
        .post(body)
        .build()

    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext generateMathematicalFallback(wage, itemPrice, goalName, goalCost, currencySymbol)
            }
            val responseStr = response.body?.string() ?: ""
            val moshi = Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(GeminiResponse::class.java)
            val responseObj = adapter.fromJson(responseStr)
            val text = responseObj?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (text != null && text.isNotBlank()) {
                text.trim()
            } else {
                generateMathematicalFallback(wage, itemPrice, goalName, goalCost, currencySymbol)
            }
        }
    } catch (e: Exception) {
        generateMathematicalFallback(wage, itemPrice, goalName, goalCost, currencySymbol)
    }
}

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
enum class PurchaseDecision {
    PENDING,
    RESISTED,
    BYPASSED
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val calculationDetails: CalculationResult? = null,
    val decision: PurchaseDecision = PurchaseDecision.PENDING
)

data class CalculationResult(
    val price: Double,
    val wage: Double,
    val hours: Double,
    val itemName: String? = null
)

data class ParsedProduct(
    val name: String?,
    val price: Double?
)

fun parseInput(text: String): ParsedProduct {
    val decimalPattern = """[\$€£¥₹₩]?\s*(\d+(?:\.\d+)?)""".toRegex()
    val match = decimalPattern.find(text)
    val price = match?.groupValues?.get(1)?.toDoubleOrNull()
    
    if (price == null) {
        return ParsedProduct(null, null)
    }
    
    val priceStr = match.value
    var cleanName = text.replace(priceStr, "")
        .replace("$", "")
        .replace("€", "")
        .replace("£", "")
        .replace("¥", "")
        .replace("₹", "")
        .replace("₩", "")
        .replace("for", "")
        .replace("a ", "")
        .replace("an ", "")
        .replace("costs", "")
        .replace("cost", "")
        .trim()
        
    cleanName = cleanName.replace("\\s+".toRegex(), " ")
    
    val nameToShow = if (cleanName.isNotBlank() && cleanName.length > 1) {
        cleanName.split(" ").joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    } else {
        null
    }
    
    return ParsedProduct(nameToShow, price)
}

// UI State class maintaining local session
class ImpulseCalculatorState(
    initialWage: Double? = null,
    initialGoalName: String = "Emergency Fund",
    initialGoalCost: Double = 1000.0,
    private val onActionTriggered: (Int) -> Unit = {}
) {
    var savingGoalName by mutableStateOf(initialGoalName)
    var savingGoalCost by mutableStateOf(initialGoalCost)

    var currencySymbol by mutableStateOf("$")
    var currencyCode by mutableStateOf("USD")
    var aiPersona by mutableStateOf("🛡️ Defensive Guardian")
    var useAI by mutableStateOf(true)
    var hoursPerWorkday by mutableStateOf(8.0)

    var messages by mutableStateOf<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "👋 Welcome to the **Impulse Calculator**.\n\nPlease state your hourly wage to get started (e.g., **20** or **35**).",
                isUser = false
            )
        )
    )

    var hourlyWage by mutableStateOf<Double?>(initialWage)

    var isTyping by mutableStateOf(false)

    var totalSavedDollars by mutableStateOf(0.0)
    var totalSavedHours by mutableStateOf(0.0)
    var totalSpentDollars by mutableStateOf(0.0)
    var totalSpentHours by mutableStateOf(0.0)
    var totalResistedCount by mutableStateOf(0)
    var totalBypassedCount by mutableStateOf(0)

    fun registerDecision(
        messageId: String,
        decision: PurchaseDecision,
        scope: kotlinx.coroutines.CoroutineScope
    ) {
        var purchaseTitle = "This item"
        var savedAmt = 0.0
        var savedH = 0.0
        var foundDetails = false

        val list = messages.map { msg ->
            if (msg.id == messageId) {
                val oldDecision = msg.decision
                if (oldDecision == PurchaseDecision.PENDING) {
                    val calc = msg.calculationDetails
                    if (calc != null) {
                        purchaseTitle = calc.itemName ?: "This item"
                        savedAmt = calc.price
                        savedH = calc.hours
                        foundDetails = true
                    }
                    if (decision == PurchaseDecision.RESISTED) {
                        if (calc != null) {
                            totalSavedDollars += calc.price
                            totalSavedHours += calc.hours
                        }
                        totalResistedCount += 1
                    } else if (decision == PurchaseDecision.BYPASSED) {
                        if (calc != null) {
                            totalSpentDollars += calc.price
                            totalSpentHours += calc.hours
                        }
                        totalBypassedCount += 1
                    }
                }
                msg.copy(decision = decision)
            } else {
                msg
            }
        }
        messages = list
        onActionTriggered(messages.size)

        if (foundDetails) {
            scope.launch {
                delay(300)
                isTyping = true
                delay(600)
                isTyping = false

                val feedbackText = if (decision == PurchaseDecision.RESISTED) {
                    listOf(
                        "⚔️ **VICTORY!** You successfully resisted buying **$purchaseTitle**. That extra **${currencySymbol}${String.format(Locale.US, "%.2f", savedAmt)}** stays in your pocket, saving you **${String.format(Locale.US, "%.1f", savedH)} hours** of labor toward your **$savingGoalName**!",
                        "🛡️ **Impulse Blocked!** Brilliant discipline, Squire! By skipping **$purchaseTitle**, you just rescued **${String.format(Locale.US, "%.1f", savedH)} hours** of your precious work-life.",
                        "💎 **Financial Power Unleashed!** You said NO to **$purchaseTitle**. That's **${currencySymbol}${String.format(Locale.US, "%.2f", savedAmt)}** closer to unlocking your **$savingGoalName**!"
                    ).random()
                } else {
                    listOf(
                        "💸 **Decision logged.** You chose to acquire **$purchaseTitle** for **${currencySymbol}${String.format(Locale.US, "%.2f", savedAmt)}**. That amounts to **${String.format(Locale.US, "%.1f", savedH)} hours** of your working life. No regrets, but let's stay disciplined on the next one!",
                        "🔌 **Impulse bypassed.** You bought **$purchaseTitle**. We've updated the ledger to reflect this choice. Keep an eye on your **$savingGoalName** progress!",
                        "💸 **Slipped through!** " + if (savedH > hoursPerWorkday) "Buying **$purchaseTitle** sets back your schedule by over a whole day of hard labor!" else "The cost is logged. Let's make sure the next purchase is an absolute necessity!"
                    ).random()
                }

                messages = messages + ChatMessage(text = feedbackText, isUser = false)
                onActionTriggered(messages.size)
            }
        }
    }

    fun resetStats() {
        totalSavedDollars = 0.0
        totalSavedHours = 0.0
        totalSpentDollars = 0.0
        totalSpentHours = 0.0
        totalResistedCount = 0
        totalBypassedCount = 0
    }

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
                        text = "I couldn't parse that wage. Please provide a valid hourly wage (e.g., **${currencySymbol}20** or **35.50**).",
                        isUser = false
                    )
                } else {
                    hourlyWage = parsedWage
                    messages = messages + ChatMessage(
                        text = "Got it! Your hourly wage is saved at **${currencySymbol}${String.format("%.2f", parsedWage)}/hr**.\n\nYour current primary saving goal is set to **$savingGoalName** (${currencySymbol}${String.format(Locale.US, "%.2f", savingGoalCost)}). You can modify it anytime in the Settings tab!\n\nNow, enter any product price (e.g., **120**) to calculate its work-hours cost.",
                        isUser = false
                    )
                }
                onActionTriggered(messages.size)
                return@launch
            }

            // Scenario 2: Wage is configured, parsing price with product name detection
            val parsedResult = parseInput(trimmed)
            val parsedPrice = parsedResult.price
            val itemName = parsedResult.name

            if (parsedPrice == null || parsedPrice <= 0.0) {
                messages = messages + ChatMessage(
                    text = "Please enter a valid product price (e.g., **120** or **49.99**) so I can evaluate its cost.",
                    isUser = false
                )
            } else {
                val hours = parsedPrice / activeWage
                isTyping = true
                val responseMsg = callGeminiOpportunityCostEngine(
                    wage = activeWage,
                    itemPrice = parsedPrice,
                    goalName = savingGoalName,
                    goalCost = savingGoalCost,
                    persona = aiPersona,
                    currencySymbol = currencySymbol,
                    useAI = useAI
                )
                isTyping = false

                messages = messages + ChatMessage(
                    text = responseMsg,
                    isUser = false,
                    calculationDetails = CalculationResult(
                        price = parsedPrice,
                        wage = activeWage,
                        hours = hours,
                        itemName = itemName
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
    var selectedTab by remember { mutableStateOf(0) }
    val isKeyboardVisible = WindowInsets.isImeVisible

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianDark),
        containerColor = ObsidianDark,
        contentWindowInsets = WindowInsets.safeDrawing, // Ensure edge-to-edge + notch + keyboard handling
        bottomBar = {
            if (!isKeyboardVisible) {
                NavigationBar(
                    containerColor = CharcoalSurface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.border(width = 1.dp, color = Color(0x1F9E9E9E))
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Chat Mode") },
                        label = { Text("Cost Guard", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = AmberGold,
                            unselectedIconColor = SlateGray,
                            unselectedTextColor = SlateGray,
                            indicatorColor = AmberGold
                        ),
                        modifier = Modifier.testTag("nav_tab_chat")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(imageVector = Icons.Default.List, contentDescription = "Ledger Mode") },
                        label = { Text("Redemption Ledger", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = AmberGold,
                            unselectedIconColor = SlateGray,
                            unselectedTextColor = SlateGray,
                            indicatorColor = AmberGold
                        ),
                        modifier = Modifier.testTag("nav_tab_ledger")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings Mode") },
                        label = { Text("Settings Console", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = AmberGold,
                            unselectedIconColor = SlateGray,
                            unselectedTextColor = SlateGray,
                            indicatorColor = AmberGold
                        ),
                        modifier = Modifier.testTag("nav_tab_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Top Branding Header (Visible globally for consistent visual identity)
            if (!isKeyboardVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CharcoalSurface)
                        .border(width = 1.dp, color = Color(0x1F9E9E9E))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
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
                                text = when (selectedTab) {
                                    0 -> "Calculate life hours in real-time"
                                    1 -> "Realized discipline stashes"
                                    else -> "Manage currencies & AI systems"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateGray
                            )
                        }

                        // Dynamic Mini Status Indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (appState.hourlyWage != null) Color(0x19FFA000) else Color(0x119E9E9E))
                                .clickable { selectedTab = 2 } // Routes directly to Settings tab
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (appState.hourlyWage != null) AmberGold else SlateGray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (appState.hourlyWage != null) {
                                    "${appState.currencySymbol}${String.format("%.2f", appState.hourlyWage)}/hr"
                                } else {
                                    "Set Wage"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (appState.hourlyWage != null) AmberGold else SlateGray
                            )
                        }
                    }
                }
            } else {
                // A super compact row header when typing to save maximum screen space
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CharcoalSurface)
                        .border(width = 1.dp, color = Color(0x1F9E9E9E))
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🛡️ IMPULSE COST GUARD",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AmberGold
                        )
                        if (appState.hourlyWage != null) {
                            Text(
                                text = "${appState.currencySymbol}${String.format("%.2f", appState.hourlyWage)}/hr",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Tab router content logic
            when (selectedTab) {
                // TAB 0: COST GUARD CHAT LOG ENGINE
                0 -> {
                    // Minified Target Progress Banner under header (only show when keyboard is closed to preserve space)
                    if (!isKeyboardVisible) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x0AFFFFFF))
                                .border(width = 0.5.dp, color = Color(0x0FFFFFFF))
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🎯 ACTIVE GOAL: ",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateGray,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = "${appState.savingGoalName} (${appState.currencySymbol}${String.format(Locale.US, "%.0f", appState.savingGoalCost)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = "Modify Goal",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { selectedTab = 2 }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("route_modify_goal")
                            )
                        }
                    }

                    // Chat Log Stream
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("message_list"),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                                    price = message.calculationDetails.price,
                                                    hours = message.calculationDetails.hours,
                                                    currencySymbol = appState.currencySymbol,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                                
                                                Spacer(modifier = Modifier.height(10.dp))
                                                
                                                if (message.decision == PurchaseDecision.PENDING) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Button(
                                                            onClick = {
                                                                appState.registerDecision(message.id, PurchaseDecision.RESISTED, coroutineScope)
                                                            },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = Color(0xFF2E7D32)
                                                            ),
                                                            shape = RoundedCornerShape(10.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                                            modifier = Modifier
                                                                .height(34.dp)
                                                                .weight(1.1f)
                                                                .testTag("skip_purchase_btn_${message.id}")
                                                        ) {
                                                            Text(
                                                                text = "✅ RESIST!",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Black,
                                                                color = Color.White
                                                            )
                                                        }

                                                        OutlinedButton(
                                                            onClick = {
                                                                appState.registerDecision(message.id, PurchaseDecision.BYPASSED, coroutineScope)
                                                            },
                                                            colors = ButtonDefaults.outlinedButtonColors(
                                                                contentColor = SlateGray
                                                            ),
                                                            border = BorderStroke(0.5.dp, Color(0x339E9E9E)),
                                                            shape = RoundedCornerShape(10.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                                            modifier = Modifier
                                                                .height(34.dp)
                                                                .weight(0.9f)
                                                                .testTag("bought_item_btn_${message.id}")
                                                        ) {
                                                            Text(
                                                                text = "💸 BOUGHT",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = SlateGray
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    val badgeLabel = if (message.decision == PurchaseDecision.RESISTED) "✅ DEFEATED IMPULSE" else "💸 BYPASSED"
                                                    val badgeColor = if (message.decision == PurchaseDecision.RESISTED) Color(0xFF81C784) else SlateGray
                                                    
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 4.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(badgeColor.copy(alpha = 0.15f))
                                                            .border(0.5.dp, badgeColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                            .padding(vertical = 6.dp, horizontal = 10.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = badgeLabel,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Black,
                                                            color = badgeColor,
                                                            letterSpacing = 1.sp
                                                        )
                                                    }
                                                }
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

                    // Suggestion / Recommendation Chips
                    val suggestions = if (appState.hourlyWage == null) {
                        listOf("${appState.currencySymbol}15/hr", "${appState.currencySymbol}25/hr", "${appState.currencySymbol}45/hr", "${appState.currencySymbol}65/hr")
                    } else {
                        listOf("${appState.currencySymbol}12", "${appState.currencySymbol}48", "${appState.currencySymbol}150", "${appState.currencySymbol}499", "${appState.currencySymbol}1000", "Change Wage")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x0A000000))
                            .padding(horizontal = 16.dp, vertical = if (isKeyboardVisible) 2.dp else 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.take(4).forEach { chipText ->
                            AssistChip(
                                onClick = {
                                    keyboardController?.hide()
                                    val cleanMsg = chipText.replace("/hr", "").replace(appState.currencySymbol, "")
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

                    // Action Input Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CharcoalSurface)
                            .border(width = 1.dp, color = Color(0x1F9E9E9E))
                            .padding(horizontal = 16.dp, vertical = if (isKeyboardVisible) 6.dp else 12.dp)
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
                                    keyboardType = if (appState.hourlyWage == null) KeyboardType.Number else KeyboardType.Text,
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

                // TAB 1: STANDALONE REDEMPTION LEDGER DASHBOARD SCREEN
                1 -> {
                    val scrollStateStats = rememberLazyListState()
                    LazyColumn(
                        state = scrollStateStats,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(
                                text = "🛡️ REDEMPTION LEDGER",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                            Text(
                                text = "Gamified tracker displaying your accumulated stashes rescued vs slush expenses bypassing limits.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateGray,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }

                        // Detailed Stats Card Hub
                        item {
                            if (appState.totalResistedCount == 0 && appState.totalBypassedCount == 0) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().testTag("empty_stats_splash"),
                                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "No Activities Logged Yet 🛡️",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = AmberGold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Navigate back to the 'Cost Guard' chat tab and evaluate any impulse options by typing their prices. Your discipline stats will render here!",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.LightGray,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { selectedTab = 0 },
                                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                                        ) {
                                            Text("GO TO CHAT CALCULATOR", color = Color.Black, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            } else {
                                ImpulseLedgerDashboard(
                                    savedDollars = appState.totalSavedDollars,
                                    savedHours = appState.totalSavedHours,
                                    spentDollars = appState.totalSpentDollars,
                                    spentHours = appState.totalSpentHours,
                                    resistedCount = appState.totalResistedCount,
                                    bypassedCount = appState.totalBypassedCount,
                                    goalName = appState.savingGoalName,
                                    goalCost = appState.savingGoalCost,
                                    currencySymbol = appState.currencySymbol,
                                    onResetClick = {
                                        appState.resetStats()
                                    }
                                )
                            }
                        }

                        // Added gamified discipline milestone cards
                        if (appState.totalResistedCount > 0 || appState.totalBypassedCount > 0) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                                    border = BorderStroke(0.5.dp, Color(0x229E9E9E))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "🔥 DISCIPLINE MILESTONES",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AmberGold,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        
                                        val totalAttempted = appState.totalSavedDollars + appState.totalSpentDollars
                                        val ratio = if (totalAttempted > 0.0) (appState.totalSavedDollars / totalAttempted * 100) else 100.0

                                        val (text, color) = when {
                                            ratio >= 80.0 -> "Elite Vault Shield! Your current discipline matches top-tier sovereign savers." to Color(0xFF81C784)
                                            ratio >= 50.0 -> "Progress detected. You are steadily shifting raw impulses into rescued goal power." to AmberGold
                                            else -> "Alert: Impulse leak is substantial. Challenge yourself to resist the very next 3 items to rebuild labor mastery!" to AmberWarning
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(color.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("🎯", fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = text,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.LightGray,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 2: ADVANCED CONTROL SETTINGS CONSOLE PAGE
                2 -> {
                    val scrollStateSettings = rememberLazyListState()
                    LazyColumn(
                        state = scrollStateSettings,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(
                                text = "⚙️ OPTION CONSOLE",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                            Text(
                                text = "Adjust dynamic parameters, currency symbols, custom wage rules, and AI guard behavior.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateGray,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }

                        // Currency Selector section
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("settings_currency_card"),
                                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.5.dp, Color(0x339E9E9E))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "SELECT GLOBAL CURRENCY",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberGold
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    val currencies = listOf(
                                        "$" to "USD (US Dollar)",
                                        "€" to "EUR (Euro)",
                                        "£" to "GBP (Brit Pound)",
                                        "¥" to "JPY/CNY (Yen)",
                                        "₹" to "INR (Rupee)",
                                        "₩" to "KRW (Won)",
                                        "₱" to "PHP (Peso)"
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        currencies.forEach { (symbol, description) ->
                                            val isSelected = appState.currencySymbol == symbol
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) Color(0x19FFC107) else Color.Transparent)
                                                    .clickable {
                                                        appState.currencySymbol = symbol
                                                        appState.currencyCode = symbol
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isSelected) AmberGold else Color(0x339E9E9E)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = symbol,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSelected) Color.Black else Color.White
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        text = description,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (isSelected) Color.White else Color.LightGray
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = AmberGold,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Target Saving Goals & Wage Parameters
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("settings_values_card"),
                                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.5.dp, Color(0x339E9E9E))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text(
                                        text = "HOURLY INCOME & SAVINGS TARGET",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberGold
                                    )

                                    // Hourly Income input
                                    var txtWage by remember(appState.hourlyWage) { mutableStateOf(appState.hourlyWage?.toString() ?: "") }
                                    OutlinedTextField(
                                        value = txtWage,
                                        onValueChange = {
                                            txtWage = it
                                            val parsed = it.toDoubleOrNull()
                                            if (parsed != null && parsed > 0) {
                                                appState.hourlyWage = parsed
                                            } else if (it.isEmpty()) {
                                                appState.hourlyWage = null
                                            }
                                        },
                                        placeholder = { Text("Unconfigured", color = Color.Gray) },
                                        label = { Text("Your Hourly Payrate (${appState.currencySymbol})", color = SlateGray) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = AmberGold,
                                            unfocusedBorderColor = Color(0x339E9E9E)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Saving Goal Name input
                                    OutlinedTextField(
                                        value = appState.savingGoalName,
                                        onValueChange = { appState.savingGoalName = it },
                                        label = { Text("Primary Saving Goal Target", color = SlateGray) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = AmberGold,
                                            unfocusedBorderColor = Color(0x339E9E9E)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                        )

                                    // Saving Goal Cost input
                                    var txtGoalCost by remember(appState.savingGoalCost) { mutableStateOf(appState.savingGoalCost.toInt().toString()) }
                                    OutlinedTextField(
                                        value = txtGoalCost,
                                        onValueChange = {
                                            txtGoalCost = it
                                            val parsedVal = it.toDoubleOrNull()
                                            if (parsedVal != null && parsedVal > 0.0) {
                                                appState.savingGoalCost = parsedVal
                                            }
                                        },
                                        label = { Text("Target Goal Value (${appState.currencySymbol})", color = SlateGray) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = AmberGold,
                                            unfocusedBorderColor = Color(0x339E9E9E)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Dynamic workday shift duration selector
                                    Column {
                                        Text(
                                            text = "Workday Labour Shift: ${appState.hoursPerWorkday.toInt()} hours",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.LightGray
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(6.0, 8.0, 10.0, 12.0).forEach { hs ->
                                                val active = appState.hoursPerWorkday == hs
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (active) AmberGold else Color(0x11FFFFFF))
                                                        .clickable { appState.hoursPerWorkday = hs }
                                                        .padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "${hs.toInt()}h",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = if (active) Color.Black else Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // AI COGNITIVE AGENT CONTROLS
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("settings_ai_card"),
                                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.5.dp, Color(0x339E9E9E))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "GEMINI AI OPPORTUNITY COSTS",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = AmberGold
                                            )
                                            Text(
                                                text = "Turn AI on/off dynamically",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SlateGray
                                            )
                                        }

                                        Switch(
                                            checked = appState.useAI,
                                            onCheckedChange = { appState.useAI = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.Black,
                                                checkedTrackColor = AmberGold
                                            ),
                                            modifier = Modifier.testTag("ai_toggle_switch")
                                        )
                                    }

                                    if (appState.useAI) {
                                        HorizontalDivider(color = Color(0x1CFFFFFF), thickness = 0.5.dp)

                                        Column {
                                            Text(
                                                text = "AI AGENT PERSONA",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Change the behavior tone used during calculation",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SlateGray
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))

                                            val personas = listOf(
                                                "🛡️ Defensive Guardian",
                                                "⚔️ Stoic Spartan",
                                                "🔥 Sarcastic Drill Sergeant",
                                                "🌟 Supportive Oracle"
                                            )

                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                personas.forEach { persona ->
                                                    val active = appState.aiPersona == persona
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(if (active) Color(0x14FFC107) else Color.Transparent)
                                                            .clickable { appState.aiPersona = persona }
                                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = persona,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = if (active) AmberGold else Color.LightGray,
                                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                                        )
                                                        if (active) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(6.dp)
                                                                    .clip(CircleShape)
                                                                    .background(AmberGold)
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

                        // Danger Reset functions
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("settings_danger_card"),
                                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.5.dp, Color(0x55FF5722))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "SYSTEM CONTROLS & ERASURES",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberWarning
                                    )

                                    Button(
                                        onClick = {
                                            appState.resetStats()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x1FFF5722)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("btn_wipe_stats")
                                    ) {
                                        Text(
                                            text = "RESET LEDGER STATISTICS",
                                            fontWeight = FontWeight.Bold,
                                            color = AmberWarning,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            appState.triggerReset(coroutineScope)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF5722)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("btn_wipe_conversations")
                                    ) {
                                        Text(
                                            text = "WIPE CHAT CONVERSATIONS",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 11.sp
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
}

@Composable
fun EquivalenceItem(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0x0EFFFFFF), shape = RoundedCornerShape(6.dp))
            .border(width = 0.5.dp, color = Color(0x1F9E9E9E), shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = SlateGray,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun ImpulseLedgerDashboard(
    savedDollars: Double,
    savedHours: Double,
    spentDollars: Double,
    spentHours: Double,
    resistedCount: Int,
    bypassedCount: Int,
    goalName: String,
    goalCost: Double,
    currencySymbol: String,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (resistedCount == 0 && bypassedCount == 0) return

    val levelText = when {
        savedDollars < 50.0 -> "Asset Squire (Lvl 1)"
        savedDollars < 200.0 -> "Impulse Sentinel (Lvl 2)"
        savedDollars < 600.0 -> "Stoic Chancellor (Lvl 3)"
        else -> "Sovereign of Capital (Lvl 4)"
    }
    
    val badgeColor = when {
        savedDollars < 50.0 -> Color(0xFF80DEEA)
        savedDollars < 200.0 -> Color(0xFF81C784)
        savedDollars < 600.0 -> AmberGold
        else -> AmberWarning
    }

    val progressPercent = if (goalCost > 0.0) (savedDollars / goalCost) else 0.0
    val displayProgress = progressPercent.coerceIn(0.0, 1.0)

    val totalAttempted = savedDollars + spentDollars
    val retentionPercent = if (totalAttempted > 0.0) (savedDollars / totalAttempted * 100.0) else 100.0
    val retentionLabel = when {
        retentionPercent == 100.0 -> "🛡️ Vault Master (100%)"
        retentionPercent >= 80.0 -> "🏰 Guard Commander (${String.format(Locale.US, "%.0f", retentionPercent)}%)"
        retentionPercent >= 50.0 -> "⚔️ Stash Warrior (${String.format(Locale.US, "%.0f", retentionPercent)}%)"
        else -> "💸 Spend-happy Merchant (${String.format(Locale.US, "%.0f", retentionPercent)}%)"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("ledger_dashboard_stash"),
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x339E9E9E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(badgeColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SAVED STASH LEDGER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = SlateGray,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = levelText,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Double Column Stats layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Column: Saved Assets info
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "🛡️ TOTAL RESISTED Saved",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF81C784),
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${currencySymbol}${String.format(Locale.US, "%.2f", savedDollars)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.testTag("ledger_saved_dollars")
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", savedHours)}h labor redeemed",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateGray,
                        fontSize = 11.sp
                    )
                }

                // Middle Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(55.dp)
                        .background(Color(0x1F9E9E9E))
                )
                Spacer(modifier = Modifier.width(12.dp))

                // Right Column: Bypassed/Spent Slush info
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "💸 BYPASSED Bought",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberWarning,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${currencySymbol}${String.format(Locale.US, "%.2f", spentDollars)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.LightGray
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", spentHours)}h labor forfeited",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateGray,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0x1F9E9E9E), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Savings Mastery Retention Progress Meter
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x0EFFFFFF), shape = RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔥 SAVINGS RETENTION Mastery",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp
                    )
                    Text(
                        text = retentionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (retentionPercent >= 80.0) Color(0xFF81C784) else AmberGold,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (retentionPercent / 100.0).toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (retentionPercent >= 80.0) Color(0xFF81C784) else AmberGold,
                    trackColor = Color(0x1F9E9E9E)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Active Goal Progress
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x0EFFFFFF), shape = RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎯 TARGET PROGRESS: $goalName",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", progressPercent * 100)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberGold,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { displayProgress.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = AmberGold,
                    trackColor = Color(0x1F9E9E9E)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${currencySymbol}${String.format(Locale.US, "%.2f", savedDollars)} of ${currencySymbol}${String.format(Locale.US, "%.2f", goalCost)} funded so far!",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0x1F9E9E9E), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Defeating impulses keeps your lifepower.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9E9E9E),
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "Clear Stats",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberWarning,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onResetClick() }
                        .padding(4.dp)
                        .testTag("ledger_clear_stats")
                )
            }
        }
    }
}

@Composable
fun WorkdayCostGauge(price: Double, hours: Double, currencySymbol: String = "$", modifier: Modifier = Modifier) {
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
                    text = "WORKSHIFT COST",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = SlateGray,
                    letterSpacing = 1.sp
                )
            }
            
            val pct = (hours / totalWorkdayHours * 100).toInt()
            Text(
                text = if (fullDays > 0) "${String.format(Locale.US, "%.1f", hours / totalWorkdayHours)} Days" else "$pct% of a Day",
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
                val workdaysCount = String.format(Locale.US, "%.1f", hours / 8.0)
                Text(
                    text = "Requires $workdaysCount 8h work shift energies.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateGray
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = Color(0x11FFFFFF), thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "OPPORTUNITY MATRIX",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = SlateGray,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        
        val coffeeCount = (price / 5.0).toInt()
        val streamingCount = (price / 15.0).toInt()
        val groceryCount = (price / 60.0).toInt()
        val foodMealsCount = (price / 12.0).toInt()
        
        val subscriptionLabel = if (streamingCount > 0) "$streamingCount Months" else "${String.format(Locale.US, "%.1f", price/15.0)} Mo"
        val groceryLabel = if (groceryCount > 0) "$groceryCount Weeks" else "${String.format(Locale.US, "%.1f", price/60.0)} Wk"

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EquivalenceItem(
                label = "☕️ Espresso Cups",
                value = "$coffeeCount Cups (${currencySymbol}5/ea)",
                modifier = Modifier.weight(1f)
            )
            EquivalenceItem(
                label = "🍿 Streaming",
                value = "$subscriptionLabel (${currencySymbol}15/mo)",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EquivalenceItem(
                label = "🍕 Grocery Staples",
                value = "$groceryLabel (${currencySymbol}60/wk)",
                modifier = Modifier.weight(1f)
            )
            EquivalenceItem(
                label = "🍟 Fast Food",
                value = "$foodMealsCount Meals (${currencySymbol}12/ea)",
                modifier = Modifier.weight(1f)
            )
        }
    }
}
