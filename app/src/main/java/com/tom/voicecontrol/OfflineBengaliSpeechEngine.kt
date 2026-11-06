package com.tom.voicecontrol

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.max

/**
 * Bengali streaming ASR. The model is downloaded once (about 88 MB compressed)
 * and all subsequent recognition runs locally on the device.
 */
class OfflineBengaliSpeechEngine(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onDownloadProgress: (Int) -> Unit,
    private val onText: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val MODEL_DIR_NAME = "sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09"
        private const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09.tar.bz2"
    }

    @Volatile private var recording = false
    @Volatile private var downloadInProgress = false
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private var recognizer: OnlineRecognizer? = null

    fun isModelInstalled(): Boolean = requiredFilesExist()

    fun downloadModel() {
        if (downloadInProgress) return
        if (requiredFilesExist()) {
            onStatus("বাংলা Offline Model প্রস্তুত • Ready")
            return
        }
        downloadInProgress = true
        thread(name = "Tom-BN-Model-Download") {
            val archive = File(context.cacheDir, "tom-bn-asr.tar.bz2")
            var connection: HttpURLConnection? = null
            try {
                archive.delete()
                onDownloadProgress(0)
                onStatus("বাংলা মডেল সার্ভারের সঙ্গে সংযোগ হচ্ছে…")
                connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 120_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Tom-Android/1.0")
                    setRequestProperty("Accept-Encoding", "identity")
                }
                connection.connect()
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("সার্ভার HTTP $responseCode দিয়েছে")
                }
                val total = connection.contentLengthLong
                if (total <= 0L) {
                    throw IllegalStateException("সার্ভার ফাইলের সাইজ জানায়নি; নেটওয়ার্ক বা GitHub mirror পরীক্ষা করুন")
                }
                val usable = context.filesDir.usableSpace
                if (usable < total + 120L * 1024L * 1024L) {
                    throw IllegalStateException("ফোনে পর্যাপ্ত জায়গা নেই। অন্তত ${((total + 120L * 1024L * 1024L) / (1024L * 1024L))} MB ফাঁকা করুন")
                }
                connection.inputStream.use { input ->
                    FileOutputStream(archive).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var received = 0L
                        var lastReported = -1
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            received += count
                            val percent = ((received * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastReported) {
                                lastReported = percent
                                onDownloadProgress(percent)
                                onStatus("বাংলা মডেল ডাউনলোড হচ্ছে: $percent%")
                            }
                        }
                        output.fd.sync()
                        if (received != total) {
                            throw IllegalStateException("ডাউনলোড অসম্পূর্ণ: ${received / (1024 * 1024)} MB / ${total / (1024 * 1024)} MB")
                        }
                    }
                }
                connection.disconnect()
                connection = null
                onStatus("ডাউনলোড সম্পন্ন। মডেল Extract ও যাচাই হচ্ছে…")
                extractModelArchive(archive)
                if (!requiredFilesExist()) {
                    throw IllegalStateException("Model files are incomplete after extraction")
                }
                archive.delete()
                onStatus("বাংলা Offline Model ইনস্টল সম্পন্ন • Ready")
            } catch (e: Exception) {
                archive.delete()
                onDownloadProgress(0)
                onError("বাংলা মডেল ইনস্টল হয়নি: ${e.message ?: "Unknown error"}")
            } finally {
                connection?.disconnect()
                downloadInProgress = false
            }
        }
    }

    private fun modelDirectory(): File = File(context.filesDir, MODEL_DIR_NAME)

    private fun requiredFilesExist(): Boolean {
        val dir = modelDirectory()
        return listOf("encoder.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt")
            .all { File(dir, it).length() > 0L }
    }

    private fun extractModelArchive(archive: File) {
        val root = context.filesDir.canonicalFile
        val destination = modelDirectory()
        destination.deleteRecursively()
        destination.mkdirs()
        BufferedInputStream(FileInputStream(archive)).use { buffered ->
            BZip2CompressorInputStream(buffered).use { bzip ->
                TarArchiveInputStream(bzip).use { tar ->
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        if (!entry.isFile) continue
                        val entryFile = File(root, entry.name).canonicalFile
                        if (!entryFile.path.startsWith(root.path + File.separator)) {
                            throw SecurityException("Unsafe model archive entry")
                        }
                        if (!entry.name.startsWith("$MODEL_DIR_NAME/")) continue
                        if (entry.name.contains("/test_wavs/")) continue
                        entryFile.parentFile?.mkdirs()
                        FileOutputStream(entryFile).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var remaining = entry.size
                            while (remaining > 0L) {
                                val count = tar.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                if (count < 0) {
                                    throw IllegalStateException("Unexpected end of model archive entry: ${entry.name}")
                                }
                                output.write(buffer, 0, count)
                                remaining -= count
                            }
                        }
                    }
                }
            }
        }
    }

    fun startListening() {
        if (recording) return
        if (!requiredFilesExist()) {
            onError("আগে বাংলা Offline Model ডাউনলোড করুন")
            return
        }
        onStatus("বাংলা Offline Engine চালু হচ্ছে…")
        recording = true
        worker = thread(name = "Tom-BN-ASR") {
            var activeStream: com.k2fsa.sherpa.onnx.OnlineStream? = null
            try {
                val dir = modelDirectory()
                val config = OnlineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = File(dir, "encoder.onnx").absolutePath,
                            decoder = File(dir, "decoder.onnx").absolutePath,
                            joiner = File(dir, "joiner.onnx").absolutePath
                        ),
                        tokens = File(dir, "tokens.txt").absolutePath,
                        numThreads = 2,
                        modelType = "zipformer2",
                        modelingUnit = "cjkchar"
                    ),
                    endpointConfig = EndpointConfig(
                        rule1 = EndpointRule(false, 2.4f, 0.0f),
                        rule2 = EndpointRule(true, 1.2f, 0.0f),
                        rule3 = EndpointRule(false, 0.0f, 20.0f)
                    ),
                    enableEndpoint = true
                )
                recognizer = OnlineRecognizer(config = config)
                // Re-check immediately before opening the microphone. Permission can be
                // revoked while the activity is alive, so never assume the earlier UI check.
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                    PackageManager.PERMISSION_GRANTED) {
                    throw SecurityException("Microphone permission not granted")
                }
                val minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuffer <= 0) throw IllegalStateException("Microphone buffer unavailable")
                val bufferBytes = max(minBuffer, SAMPLE_RATE * 2 / 2)
                val audio = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes
                )
                if (audio.state != AudioRecord.STATE_INITIALIZED) {
                    audio.release()
                    throw IllegalStateException("Microphone could not be initialized")
                }
                if (!recording) {
                    audio.release()
                    return@thread
                }
                recorder = audio
                val stream = recognizer!!.createStream()
                activeStream = stream
                val samples = ShortArray(bufferBytes / 2)
                audio.startRecording()
                onStatus("শুনছি • সম্পূর্ণ Offline বাংলা Recognition")
                while (recording) {
                    val count = audio.read(samples, 0, samples.size)
                    if (count > 0) {
                        val floats = FloatArray(count) { samples[it] / 32768.0f }
                        stream.acceptWaveform(floats, sampleRate = SAMPLE_RATE)
                        while (recognizer!!.isReady(stream)) recognizer!!.decode(stream)
                        if (recognizer!!.isEndpoint(stream)) {
                            val result = recognizer!!.getResult(stream).text.trim()
                            if (result.isNotBlank()) onText(result)
                            recognizer!!.reset(stream)
                        }
                    } else if (count < 0 && recording) {
                        throw IllegalStateException("Microphone read error: $count")
                    }
                }
                try { audio.stop() } catch (_: Exception) {}
                audio.release()
                recorder = null
                onStatus("শোনা বন্ধ • Offline Engine প্রস্তুত")
            } catch (e: Exception) {
                recording = false
                try { recorder?.release() } catch (_: Exception) {}
                recorder = null
                onError("Offline Engine error: ${e.message ?: "Unknown error"}")
            } finally {
                try { activeStream?.release() } catch (_: Exception) {}
                try { recorder?.release() } catch (_: Exception) {}
                recorder = null
                try { recognizer?.release() } catch (_: Exception) {}
                recognizer = null
            }
        }
    }

    fun stopListening() {
        recording = false
        try { recorder?.stop() } catch (_: Exception) {}
    }

    fun release() {
        stopListening()
    }
}
