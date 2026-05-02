package com.guitartuner

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

class PitchDetector(
    private val onPitch: (hz: Float) -> Unit,
    private val onSilence: () -> Unit
) {
    private val sampleRate = 44100
    private val bufferSize = 8192
    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    fun start() {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val recordBufferSize = maxOf(minBuffer, bufferSize * 2)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            recordBufferSize
        )
        audioRecord?.startRecording()
        running = true
        thread = Thread(::processAudio, "PitchDetector").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun processAudio() {
        val buffer = ShortArray(bufferSize)
        val samples = DoubleArray(bufferSize)

        while (running) {
            val read = audioRecord?.read(buffer, 0, bufferSize) ?: break
            if (read <= 0) continue

            // Convert to doubles and check RMS for silence
            var rms = 0.0
            for (i in 0 until read) {
                samples[i] = buffer[i].toDouble() / 32768.0
                rms += samples[i] * samples[i]
            }
            rms = Math.sqrt(rms / read)

            if (rms < 0.01) {
                onSilence()
                continue
            }

            val pitch = yin(samples, read)
            if (pitch > 0) {
                onPitch(pitch.toFloat())
            } else {
                onSilence()
            }
        }
    }

    // YIN pitch detection algorithm
    // Reference: de Cheveigné & Kawahara (2002)
    private fun yin(buffer: DoubleArray, size: Int): Double {
        val halfSize = size / 2
        val yinBuffer = DoubleArray(halfSize)

        // Step 1 & 2: Difference function
        yinBuffer[0] = 1.0
        var runningSum = 0.0
        for (tau in 1 until halfSize) {
            var diff = 0.0
            for (i in 0 until halfSize) {
                val delta = buffer[i] - buffer[i + tau]
                diff += delta * delta
            }
            runningSum += diff
            // Step 3: Cumulative mean normalized difference
            yinBuffer[tau] = diff * tau / runningSum
        }

        // Step 4: Absolute threshold
        val threshold = 0.15
        var tau = 2
        while (tau < halfSize) {
            if (yinBuffer[tau] < threshold) {
                // Find local minimum
                while (tau + 1 < halfSize && yinBuffer[tau + 1] < yinBuffer[tau]) {
                    tau++
                }
                // Step 5: Parabolic interpolation
                val betterTau = parabolicInterpolation(yinBuffer, tau, halfSize)
                return sampleRate.toDouble() / betterTau
            }
            tau++
        }
        return -1.0
    }

    private fun parabolicInterpolation(yinBuffer: DoubleArray, tau: Int, size: Int): Double {
        if (tau == 0 || tau >= size - 1) return tau.toDouble()
        val s0 = yinBuffer[tau - 1]
        val s1 = yinBuffer[tau]
        val s2 = yinBuffer[tau + 1]
        val denom = 2.0 * (2.0 * s1 - s2 - s0)
        return if (denom == 0.0) tau.toDouble()
        else tau + (s2 - s0) / denom
    }
}
