package com.example.tts

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object AudioDecoder {
    private const val TAG = "AudioDecoder"

    data class DecodedAudioResult(val pcmData: ByteArray, val sampleRate: Int, val channelCount: Int)

    fun decodeToPcm(audioBytes: ByteArray, cacheDir: File): DecodedAudioResult? {
        var tempFile: File? = null
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        val pcmStream = ByteArrayOutputStream()
        try {
            tempFile = File.createTempFile("gemini_voice_decode", ".aac", cacheDir)
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            extractor = MediaExtractor()
            extractor.setDataSource(tempFile.absolutePath)

            if (extractor.trackCount == 0) return null
            val format = extractor.getTrackFormat(0)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            extractor.selectTrack(0)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            val timeoutUs = 12000L

            var sampleRate = 24000
            var channelCount = 1

            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            }
            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, timeoutUs)
                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && info.size > 0) {
                        val chunk = ByteArray(info.size)
                        outputBuffer.position(info.offset)
                        outputBuffer.get(chunk)
                        pcmStream.write(chunk)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
            return DecodedAudioResult(pcmStream.toByteArray(), sampleRate, channelCount)
        } catch (e: Exception) {
            Log.e(TAG, "Audio decoding error: ${e.message}", e)
            return null
        } finally {
            try { codec?.stop() } catch (ignored: Exception) {}
            try { codec?.release() } catch (ignored: Exception) {}
            try { extractor?.release() } catch (ignored: Exception) {}
            try { tempFile?.delete() } catch (ignored: Exception) {}
        }
    }
}
