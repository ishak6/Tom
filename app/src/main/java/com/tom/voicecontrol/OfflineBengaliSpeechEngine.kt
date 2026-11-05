package com.tom.voicecontrol

import android.content.Context
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
            try {
                onStatus("বাংলা Offline Model ডাউনলোড হচ্ছে… প্রায় 88 MB")
                val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Tom-Android")
                }
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Download failed: HTTP ${connection.responseCode}")
                }
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    FileOutputStream(archive).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var received = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            received += count
                            if (total > 0L) {
                                val percent = (received * 100 / total).toInt()
                                onStatus("বাংলা মডেল ডাউনলোড: $percent%")
                            }
                        }
                    }
                }
                connection.disconnect()
                onStatus("মডেল Extract ও যাচাই হচ্ছে…")
                extractModelArchive(archive)
                if (!requiredFilesExist()) {
                    throw IllegalStateException("Model files are incomplete after extraction")
                }
                archive.delete()
                onStatus("বাংলা Offline Model ইনস্টল সম্পন্ন • Ready")
            } catch (e: Exception) {
                archive.delete()
                onError("মডেল ইনস্টল হয়নি: ${e.message ?: "Unknown error"}")
            } finally {
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
                            tar.copyTo(output, 64 * 1024)
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
        worker = thread(name = "Tom-BN-ASR") {
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
                recorder = audio
                val stream = recognizer!!.createStream()
                val samples = ShortArray(bufferBytes / 2)
                recording = true
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
                    } else if (count < 0) {
                        throw IllegalStateException("Microphone read error: $count")
                    }
                }
                stream.release()
                audio.stop()
                audio.release()
                recorder = null
                onStatus("শোনা বন্ধ • Offline Engine প্রস্তুত")
            } catch (e: Exception) {
                recording = false
                try { recorder?.release() } catch (_: Exception) {}
                recorder = null
                onError("Offline Engine error: ${e.message ?: "Unknown error"}")
            } finally {
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
        try { recognizer?.release() } catch (_: Exception) {}
        recognizer = null
    }
}
