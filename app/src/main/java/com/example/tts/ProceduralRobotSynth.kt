package com.example.tts

import java.io.ByteArrayOutputStream
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

object ProceduralRobotSynth {

    fun synthesize(text: String, speed: Float, pitchFactor: Float, style: String): ByteArray {
        val sampleRate = 16000
        
        // Coerce inputs to safe boundaries
        val speedCoerced = speed.coerceIn(0.5f, 3.0f)
        val pitchCoerced = pitchFactor.coerceIn(0.4f, 2.5f)
        
        // Time parameters (e.g. 100ms per character at speed = 1.0)
        val durationPerCharMs = (100 / speedCoerced).toInt()
        val samplesPerChar = (sampleRate * (durationPerCharMs / 1000.0)).toInt()
        
        val cleanText = text.lowercase()
        val outputStream = ByteArrayOutputStream()
        
        val baseF0 = 95.0 * pitchCoerced
        
        for (i in cleanText.indices) {
            val char = cleanText[i]
            val isVowel = char in "aeiouáéíóúâêôãõ"
            val isSpace = char == ' ' || char == ',' || char == '.' || char == '!' || char == '?' || char == ';' || char == ':' || char == '-' || char == '\n'
            
            for (s in 0 until samplesPerChar) {
                val t = s.toDouble() / sampleRate
                val progress = s.toDouble() / samplesPerChar
                
                // Volume envelope to prevent popping at boundaries
                val envelope = if (progress < 0.12) {
                    progress / 0.12
                } else if (progress > 0.88) {
                    (1.0 - progress) / 0.12
                } else {
                    1.0
                }
                
                val sampleValue: Short = if (isSpace) {
                    0
                } else if (isVowel) {
                    // Retrieve formant frequency markers (F1, F2, F3) for each vowel
                    val f1: Double
                    val f2: Double
                    val f3: Double
                    when (char) {
                        'a', 'á', 'â', 'ã' -> { f1 = 730.0; f2 = 1090.0; f3 = 2440.0 }
                        'e', 'é', 'ê' -> { f1 = 530.0; f2 = 1840.0; f3 = 2480.0 }
                        'i', 'í' -> { f1 = 270.0; f2 = 2290.0; f3 = 3010.0 }
                        'o', 'ó', 'ô', 'õ' -> { f1 = 570.0; f2 = 840.0; f3 = 2410.0 }
                        'u', 'ú' -> { f1 = 300.0; f2 = 870.0; f3 = 2240.0 }
                        else -> { f1 = 600.0; f2 = 1200.0; f3 = 2500.0 }
                    }
                    
                    // Modulate pitch with slight vibrato for organic movement (5.5 Hz)
                    val currentF0 = baseF0 + (sin(2.0 * PI * 5.5 * t) * (baseF0 * 0.05))
                    
                    var waveSum = 0.0
                    when (style) {
                        "Onda Pura" -> {
                            // Pure smooth sinusoids
                            waveSum = sin(2.0 * PI * currentF0 * t) * 0.6 +
                                      sin(2.0 * PI * f1 * t) * 0.3 +
                                      sin(2.0 * PI * f2 * t) * 0.1
                        }
                        "Ciborgue Distorcido" -> {
                            // Ring modulation with a low-frequency hum (28 Hz) for a deep cyborg sound
                            val carrier = sin(2.0 * PI * currentF0 * t)
                            val modulator = sin(2.0 * PI * 28.0 * t)
                            val formants = sin(2.0 * PI * f1 * t) * 0.4 + sin(2.0 * PI * f2 * t) * 0.2
                            waveSum = (carrier * (0.6 + 0.4 * modulator)) + formants
                        }
                        "Rádio Antigo" -> {
                            // High bandwidth speech components + simulation of air crackles
                            val basic = sin(2.0 * PI * currentF0 * t) * 0.5 + sin(2.0 * PI * f1 * t) * 0.3
                            val radioNoise = (Math.random() * 2.0 - 1.0) * 0.15
                            val crackle = if (Math.random() < 0.003) (Math.random() * 2.0 - 1.0) * 0.6 else 0.0
                            waveSum = basic + radioNoise + crackle
                        }
                        else -> {
                            // "Robô Clássico"
                            // Pulse/buzz generator overlayed with resonant wave peaks
                            val baseWave = sin(2.0 * PI * currentF0 * t)
                            val formant1 = sin(2.0 * PI * f1 * t) * 0.4
                            val formant2 = sin(2.0 * PI * f2 * t) * 0.2
                            val pulse = if ((t * currentF0) % 1.0 < 0.25) 0.25 else -0.25
                            waveSum = (0.7 * (baseWave + formant1 + formant2)) + (0.3 * pulse)
                        }
                    }
                    
                    val combined = waveSum.coerceIn(-1.0, 1.0) * envelope * 24000
                    combined.toInt().toShort()
                } else {
                    // Consonants (s, z, t, d, r, l, m, p...)
                    var consonantValue = 0.0
                    when (char) {
                        's', 'x', 'f' -> {
                            // White noise for fricatives (sibilance)
                            consonantValue = (Math.random() * 2.0 - 1.0) * 0.35
                        }
                        'z', 'v' -> {
                            // Voice-buzz + white noise
                            val noise = (Math.random() * 2.0 - 1.0) * 0.15
                            val buzz = sin(2.0 * PI * baseF0 * t) * 0.25
                            consonantValue = noise + buzz
                        }
                        'p', 't', 'k', 'c' -> {
                            // Plosive stops: silence, then a brief burst
                            consonantValue = if (progress < 0.25) {
                                (Math.random() * 2.0 - 1.0) * 0.5 * (1.0 - progress / 0.25)
                            } else {
                                0.0
                            }
                        }
                        'b', 'd', 'g' -> {
                            // Voiced stops: low rumble burst
                            consonantValue = if (progress < 0.3) {
                                sin(2.0 * PI * (baseF0 * 0.8) * t) * 0.4 * (1.0 - progress / 0.3)
                            } else {
                                0.0
                            }
                        }
                        'm', 'n', 'r', 'l', 'h' -> {
                            // Nasal and glide resonances - low harmonics of the fundamental
                            consonantValue = sin(2.0 * PI * baseF0 * t) * 0.45 +
                                             sin(2.0 * PI * baseF0 * 2.0 * t) * 0.2
                        }
                        else -> {
                            // General consonant: default pitch carrier
                            consonantValue = sin(2.0 * PI * baseF0 * t) * 0.3
                        }
                    }
                    
                    val combined = consonantValue.coerceIn(-1.0, 1.0) * envelope * 22000
                    combined.toInt().toShort()
                }
                
                // Write little-endian bytes for 16-bit Mono PCM
                outputStream.write(sampleValue.toInt() and 0xFF)
                outputStream.write((sampleValue.toInt() shr 8) and 0xFF)
            }
        }
        
        return outputStream.toByteArray()
    }
}
