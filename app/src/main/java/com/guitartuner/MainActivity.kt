package com.guitartuner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.app.Activity
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.round

class MainActivity : Activity() {

    private lateinit var noteTextView: TextView
    private lateinit var freqTextView: TextView
    private lateinit var centsTextView: TextView
    private lateinit var tuningIndicator: View
    private lateinit var tuningBar: FrameLayout

    private var pitchDetector: PitchDetector? = null

    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
    }

    private fun buildLayout(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        // Note name (large)
        noteTextView = TextView(this).apply {
            text = "--"
            textSize = 96f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }

        // Frequency
        freqTextView = TextView(this).apply {
            text = "--- Hz"
            textSize = 24f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }

        // Cents deviation
        centsTextView = TextView(this).apply {
            text = ""
            textSize = 20f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            lp.topMargin = 8.dp
            layoutParams = lp
        }

        // Tuning bar container
        val barContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            lp.topMargin = 40.dp
            lp.leftMargin = 32.dp
            lp.rightMargin = 32.dp
            layoutParams = lp
        }

        // Labels row
        val labelsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        val flatLabel = TextView(this).apply {
            text = "FLAT"
            textSize = 14f
            setTextColor(Color.parseColor("#FF6B6B"))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            gravity = Gravity.START
        }
        val inTuneLabel = TextView(this).apply {
            text = "IN TUNE"
            textSize = 14f
            setTextColor(Color.parseColor("#6BCB77"))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
        }
        val sharpLabel = TextView(this).apply {
            text = "SHARP"
            textSize = 14f
            setTextColor(Color.parseColor("#FF6B6B"))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            gravity = Gravity.END
        }
        labelsRow.addView(flatLabel)
        labelsRow.addView(inTuneLabel)
        labelsRow.addView(sharpLabel)

        // Tuning bar track
        tuningBar = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#2D2D4E"))
            val lp = LinearLayout.LayoutParams(MATCH_PARENT, 12.dp)
            lp.topMargin = 8.dp
            layoutParams = lp
        }

        // Center line marker
        val centerLine = View(this).apply {
            setBackgroundColor(Color.parseColor("#6BCB77"))
            val lp = FrameLayout.LayoutParams(3.dp, 28.dp)
            lp.gravity = Gravity.CENTER
            layoutParams = lp
        }

        // Moving indicator
        tuningIndicator = View(this).apply {
            setBackgroundColor(Color.WHITE)
            val lp = FrameLayout.LayoutParams(16.dp, 28.dp)
            lp.gravity = Gravity.CENTER
            layoutParams = lp
        }

        tuningBar.addView(centerLine)
        tuningBar.addView(tuningIndicator)

        barContainer.addView(labelsRow)
        barContainer.addView(tuningBar)

        root.addView(noteTextView)
        root.addView(freqTextView)
        root.addView(centsTextView)
        root.addView(barContainer)

        return root
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startTuner()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        }
    }

    override fun onPause() {
        super.onPause()
        stopTuner()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_MIC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startTuner()
        }
    }

    private fun startTuner() {
        pitchDetector = PitchDetector(
            onPitch = { hz -> runOnUiThread { updatePitch(hz) } },
            onSilence = { runOnUiThread { showSilence() } }
        )
        pitchDetector?.start()
    }

    private fun stopTuner() {
        pitchDetector?.stop()
        pitchDetector = null
    }

    private fun updatePitch(hz: Float) {
        val midi = hzToMidi(hz)
        val note = midiToNoteName(midi)
        val cents = centDeviation(hz, midi)

        noteTextView.text = note
        freqTextView.text = "%.1f Hz".format(hz)

        val absC = abs(cents)
        val color = when {
            absC <= 5 -> Color.parseColor("#6BCB77")   // green
            absC <= 15 -> Color.parseColor("#FFD93D")  // yellow
            else -> Color.parseColor("#FF6B6B")        // red
        }
        noteTextView.setTextColor(color)

        val sign = if (cents > 0) "+" else ""
        centsTextView.text = "${sign}%.0f cents".format(cents)
        centsTextView.setTextColor(color)

        updateIndicator(cents, color)
    }

    private fun showSilence() {
        noteTextView.text = "--"
        noteTextView.setTextColor(Color.WHITE)
        freqTextView.text = "--- Hz"
        centsTextView.text = ""
        centerIndicator()
    }

    private fun updateIndicator(cents: Float, color: Int) {
        tuningBar.post {
            val barWidth = tuningBar.width.toFloat()
            if (barWidth == 0f) return@post
            // Map cents (-50..+50) to bar position (0..barWidth)
            val clamped = cents.coerceIn(-50f, 50f)
            val fraction = (clamped + 50f) / 100f
            val indicatorWidth = tuningIndicator.width.toFloat()
            val x = fraction * (barWidth - indicatorWidth)
            tuningIndicator.x = x
            tuningIndicator.setBackgroundColor(color)
        }
    }

    private fun centerIndicator() {
        tuningBar.post {
            val barWidth = tuningBar.width.toFloat()
            val indicatorWidth = tuningIndicator.width.toFloat()
            tuningIndicator.x = (barWidth - indicatorWidth) / 2f
            tuningIndicator.setBackgroundColor(Color.WHITE)
        }
    }

    private fun hzToMidi(hz: Float): Int {
        return round(69.0 + 12.0 * log2(hz / 440.0)).toInt()
    }

    private fun midiToNoteName(midi: Int): String {
        val noteIndex = ((midi % 12) + 12) % 12
        val octave = (midi / 12) - 1
        return "${noteNames[noteIndex]}$octave"
    }

    private fun centDeviation(hz: Float, midi: Int): Float {
        val refHz = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
        return (1200.0 * log2(hz / refHz)).toFloat()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_MIC = 1001
    }
}
