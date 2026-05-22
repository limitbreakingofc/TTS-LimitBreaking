package com.example

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import com.example.tts.AudioDecoder
import com.example.tts.GeminiApiClient
import com.example.tts.ProceduralRobotSynth
import com.example.tts.SpeechLogItem
import com.example.tts.TtsSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize().testTag("main_scaffold"),
                    containerColor = Color(0xFFFDFBFF)
                ) { innerPadding ->
                    TtsSettingsScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Config states
    var useGemini by remember { mutableStateOf(TtsSettingsManager.isUseGemini(context)) }
    var robotPitch by remember { mutableStateOf(TtsSettingsManager.getRobotPitch(context)) }
    var robotSpeed by remember { mutableStateOf(TtsSettingsManager.getRobotSpeed(context)) }
    var robotStyle by remember { mutableStateOf(TtsSettingsManager.getRobotStyle(context)) }
    var geminiVoice by remember { mutableStateOf(TtsSettingsManager.getVoiceName(context)) }
    var customApiKey by remember { mutableStateOf(TtsSettingsManager.getGeminiApiKey(context)) }

    // Test Voice States
    var testText by remember { mutableStateOf("Olá! Eu sou o sintetizador de voz do sistema. Configure-me nas opções do seu Android para iniciar.") }
    var isPlaying by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(TtsSettingsManager.getSpeechLogs(context)) }

    // Audio tracking
    var activeAudioTrack by remember { mutableStateOf<AudioTrack?>(null) }

    // Style drop-downs
    var styleDropdownExpanded by remember { mutableStateOf(false) }
    var voiceDropdownExpanded by remember { mutableStateOf(false) }

    val styles = listOf("Robô Clássico", "Onda Pura", "Ciborgue Distorcido", "Rádio Antigo")
    val geminiVoices = listOf("Kore", "Puck", "Charon", "Fenrir", "Aoede")

    // Loader effect to sync logs periodically
    LaunchedEffect(isPlaying) {
        logs = TtsSettingsManager.getSpeechLogs(context)
    }

    // Function to launch system TTS Settings
    val openSystemTtsSettings = {
        try {
            val intent = Intent().apply {
                action = "com.android.settings.TTS_SETTINGS"
            }
            context.startActivity(intent)
            Toast.makeText(context, "Selecione 'Sintetizador TTS' como mecanismo padrão.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(fallbackIntent)
                Toast.makeText(context, "Vá em Texto para Voz nas Configurações.", Toast.LENGTH_LONG).show()
            } catch (ex: Exception) {
                Toast.makeText(context, "Erro ao abrir configurações: ${ex.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Function to speak preview locally in-app
    val speakPreview = {
        if (isPlaying) {
            // Stop current playback
            try {
                activeAudioTrack?.stop()
                activeAudioTrack?.release()
                activeAudioTrack = null
            } catch (e: Exception) {}
            isPlaying = false
        } else {
            coroutineScope.launch {
                val apiKey = customApiKey.trim()
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    Toast.makeText(context, "Chave de API necessária! Por favor, cole sua chave do Google AI Studio nas configurações abaixo.", Toast.LENGTH_LONG).show()
                } else {
                    isGenerating = true
                    try {
                        TtsSettingsManager.addSpeechLog(context, testText, "Preview: Gemini ($geminiVoice)")
                        logs = TtsSettingsManager.getSpeechLogs(context)
                        
                        val audioBytes = withContext(Dispatchers.IO) {
                            GeminiApiClient.fetchSpeech(testText, apiKey, geminiVoice)
                        }
                        
                        if (audioBytes != null) {
                            val decoded = withContext(Dispatchers.IO) {
                                AudioDecoder.decodeToPcm(audioBytes, context.cacheDir)
                            }
                            isGenerating = false
                            if (decoded != null) {
                                isPlaying = true
                                playPcmBuffer(
                                    decoded.pcmData,
                                    decoded.sampleRate,
                                    decoded.channelCount,
                                    onStart = { track -> activeAudioTrack = track },
                                    onComplete = { isPlaying = false }
                                )
                            } else {
                                Toast.makeText(context, "Erro decodificando o áudio neural obtido.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            isGenerating = false
                            Toast.makeText(context, "Erro na API: Verifique sua chave API do AI Studio ou sua conexão de internet.", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        isGenerating = false
                        Toast.makeText(context, "Erro ao gerar: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .background(Color(0xFFFDFBFF))
            .padding(16.dp)
    ) {
        // App Header Title
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp, top = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MOTOR DO SISTEMA",
                    color = Color(0xFF005AC1),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFE3E2E6))
                        .clickable { openSystemTtsSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configurações",
                        tint = Color(0xFF44474E),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "VOZ\nVIVA",
                color = Color(0xFF1B1B1F),
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 42.sp,
                letterSpacing = (-1.5).sp,
                fontFamily = FontFamily.SansSerif
            )
        }

        // Animated Spectrum Visualizer card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .height(180.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFD8E2FF))
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                // Wave visualizer in the background of the card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    VoiceVisualizer(isPlaying = isPlaying)
                }

                // Foreground labels
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(Color(0xFF005AC1))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Ativo Agora",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF005AC1),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (isGenerating) "Processando voz..." else "Voz Selecionada",
                            color = Color(0xFF005AC1).copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Neural Gemini AI\n($geminiVoice)",
                            color = Color(0xFF001B3E),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
        }

        // Lazy scroll parameters & options
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // STEP 1: Enable system instructions
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("activation_instructions_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F3F7)),
                    border = BorderStroke(1.dp, Color(0xFFE3E2E6))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF005AC1)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Como ativar no Android?",
                                color = Color(0xFF1B1B1F),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Para que qualquer aplicativo possa usar este sintetizador, ative-o nas configurações do sistema do aparelho:",
                            color = Color(0xFF44474E),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        InstructionStep(step = "1", text = "Toque no botão abaixo para abrir as Configurações.")
                        InstructionStep(step = "2", text = "Selecione 'Sintetizador TTS' na lista de mecanismos.")
                        InstructionStep(step = "3", text = "Ative-o e ajuste a velocidade global do sistema.")

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { openSystemTtsSettings() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_open_settings"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005AC1)),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Abrir Configurações do Sistema",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Test Lab Inputs
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F3F7)),
                    border = BorderStroke(1.dp, Color(0xFFE3E2E6))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Laboratório de Teste de Fala",
                            color = Color(0xFF1B1B1F),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = testText,
                            onValueChange = { testText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(95.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1B1B1F),
                                unfocusedTextColor = Color(0xFF1B1B1F),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = Color(0xFF005AC1),
                                unfocusedBorderColor = Color(0xFFD3D2D6)
                            ),
                            placeholder = { Text("Insira o texto para teste...", color = Color(0xFF8E9099)) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Beautiful contrast button card matching the theme
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF1B1B1F))
                                .clickable(enabled = !isGenerating && testText.isNotBlank()) { speakPreview() }
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(if (isPlaying) Color(0xFFF43F5E) else Color(0xFF005AC1)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isGenerating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Refresh else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "TESTE DE ÁUDIO",
                                        color = Color(0xFFBEC6DC),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp
                                    )
                                    Text(
                                        text = if (isGenerating) "Gerando voz..." else if (isPlaying) "Interromper Áudio" else "Sintetizar / Falar Localmente",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // CORE SETTINGS MODULE
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F3F7)),
                    border = BorderStroke(1.dp, Color(0xFFE3E2E6))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF005AC1)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Configuração da Voz por IA",
                                color = Color(0xFF1B1B1F),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(14.dp))

                        // Model selection dropdown
                        Text(
                            text = "Voz Neural do Gemini",
                            color = Color(0xFF44474E),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFD3D2D6), RoundedCornerShape(12.dp))
                                .clickable { voiceDropdownExpanded = true }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = geminiVoice,
                                    color = Color(0xFF1B1B1F),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Selecionar Voz",
                                    tint = Color(0xFF005AC1),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = voiceDropdownExpanded,
                                onDismissRequest = { voiceDropdownExpanded = false },
                                modifier = Modifier.background(Color.White)
                            ) {
                                geminiVoices.forEach { voice ->
                                    DropdownMenuItem(
                                        text = { Text(text = voice, color = Color(0xFF1B1B1F)) },
                                        onClick = {
                                            geminiVoice = voice
                                            TtsSettingsManager.setVoiceName(context, voice)
                                            voiceDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Visual indicator about the user's API Key being needed or configured
                        val isKeySetup = customApiKey.trim().isNotEmpty() && customApiKey.trim() != "MY_GEMINI_API_KEY"
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isKeySetup) Color(0xFFD1E7DD) else Color(0xFFF8D7DA))
                                .border(
                                    BorderStroke(1.dp, if (isKeySetup) Color(0xFFA3CFBB) else Color(0xFFF5C2C7)),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isKeySetup) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (isKeySetup) Color(0xFF0F5132) else Color(0xFF842029),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isKeySetup) "Chave de API Configurada" else "Chave API Necessária",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isKeySetup) Color(0xFF0F5132) else Color(0xFF842029)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isKeySetup) 
                                        "Seu sintetizador neural está pronto para operar! A chave informada abaixo será usada para gerar as vozes por IA."
                                        else "Este aplicativo requer uma chave de API para funcionar. Nenhuma chave vem pré-instalada por segurança. Cole a sua chave do Google AI Studio no campo abaixo para começar.",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = if (isKeySetup) Color(0xFF155724) else Color(0xFF721C24)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Outlined Text Field for key
                        Text(
                            text = "Chave API do Google AI Studio",
                            color = Color(0xFF44474E),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = customApiKey,
                            onValueChange = {
                                customApiKey = it
                                TtsSettingsManager.setGeminiApiKey(context, it)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("custom_api_key_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1B1B1F),
                                unfocusedTextColor = Color(0xFF1B1B1F),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = Color(0xFF005AC1),
                                unfocusedBorderColor = Color(0xFFD3D2D6)
                            ),
                            placeholder = { Text("Cole sua chave livre do AI Studio aqui (AIzaSy...)", color = Color(0xFF8E9099), fontSize = 12.sp) }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Direct button to open Google AI Studio and generate an API key
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/app/apikey"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Erro ao abrir site", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF005AC1),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier.align(Alignment.End).testTag("get_free_key_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Obter Chave Grátis",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Criar Chave API Gratuita no Google AI Studio", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // AUDIT Synthesis Logs list
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Histórico de Síntese",
                        color = Color(0xFF1B1B1F),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (logs.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                TtsSettingsManager.clearSpeechLogs(context)
                                logs = TtsSettingsManager.getSpeechLogs(context)
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF005AC1))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Limpar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (logs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color(0xFFF3F3F7), RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFFE3E2E6), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhuma sintetização registrada ainda.\nUse o laboratório ou outros apps para testar.",
                            color = Color(0xFF44474E),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                items(logs) { log ->
                    LogItemCard(log)
                }
            }
        }
    }
}

@Composable
fun VoiceVisualizer(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val amplitudeMultiplier by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.05f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "amplitude"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .padding(vertical = 8.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val points = 90
        val step = width / points

        val path1 = androidx.compose.ui.graphics.Path()
        path1.moveTo(0f, centerY)

        for (i in 0..points) {
            val x = i * step
            val angle = (i.toFloat() / points) * 4f * PI.toFloat() + phase
            val wave = sin(angle) * 0.61f + cos(angle * 2.3f) * 0.28f + sin(angle * 0.5f) * 0.11f
            val y = centerY + wave * (height * 0.38f) * amplitudeMultiplier
            path1.lineTo(x, y)
        }

        drawPath(
            path = path1,
            color = Color(0xFF005AC1),
            style = Stroke(
                width = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )

        val path2 = androidx.compose.ui.graphics.Path()
        path2.moveTo(0f, centerY)

        for (i in 0..points) {
            val x = i * step
            val angle = (i.toFloat() / points) * 4f * PI.toFloat() - phase * 0.85f
            val wave = cos(angle * 1.6f) * 0.45f + sin(angle * 3.3f) * 0.25f
            val y = centerY + wave * (height * 0.28f) * amplitudeMultiplier
            path2.lineTo(x, y)
        }

        drawPath(
            path = path2,
            color = Color(0xFF005AC1).copy(alpha = 0.35f),
            style = Stroke(
                width = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
    }
}

@Composable
fun InstructionStep(step: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFD8E2FF)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                color = Color(0xFF005AC1),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = Color(0xFF44474E),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun LogItemCard(log: SpeechLogItem) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F3F7)),
        border = BorderStroke(1.dp, Color(0xFFE3E2E6))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(
                            if (log.engine.contains("Gemini")) Color(0xFFD8E2FF)
                            else Color(0xFFE3E2E6)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = log.engine,
                        color = if (log.engine.contains("Gemini")) Color(0xFF005AC1) else Color(0xFF1B1B1F),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                
                // Formatted simple time
                val formattedTime = remember(log.timestamp) {
                    val timeInstance = java.util.Calendar.getInstance().apply { timeInMillis = log.timestamp }
                    String.format("%02d:%02d:%02d", timeInstance.get(java.util.Calendar.HOUR_OF_DAY), timeInstance.get(java.util.Calendar.MINUTE), timeInstance.get(java.util.Calendar.SECOND))
                }
                Text(
                    text = formattedTime,
                    color = Color(0xFF44474E),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = log.text,
                color = Color(0xFF1B1B1F),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Low-level PCM direct media playing structure using AudioTrack.write()
private suspend fun playPcmBuffer(
    pcmData: ByteArray,
    sampleRate: Int,
    channelCount: Int,
    onStart: (AudioTrack) -> Unit,
    onComplete: () -> Unit
) = withContext(Dispatchers.IO) {
    var audioTrack: AudioTrack? = null
    try {
        val channelConfig = if (channelCount == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val sampleSize = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, sampleSize)
        val bufferSize = Math.max(minBufferSize, pcmData.size)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(sampleSize)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(pcmData, 0, pcmData.size)
        
        withContext(Dispatchers.Main) {
            onStart(audioTrack)
        }
        
        audioTrack.play()

        // Wait play end duration to discharge resources
        val durationMs = (pcmData.size.toDouble() / (sampleRate * channelCount * 2) * 1000).toLong()
        delay(durationMs + 150)
        
    } catch (e: Exception) {
        Log.e("AudioTrack", "In-app playback failed: ${e.message}")
    } finally {
        try {
            audioTrack?.stop()
        } catch (e: Exception) {}
        try {
            audioTrack?.release()
        } catch (e: Exception) {}
        withContext(Dispatchers.Main) {
            onComplete()
        }
    }
}
