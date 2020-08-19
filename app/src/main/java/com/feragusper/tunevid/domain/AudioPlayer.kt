package com.feragusper.tunevid.domain

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.math.sin

data class AudioPlayer(
    val sampleRate: Int,
    val soundDuration: Int,
    val startFrequency: Float,
    val endFrequency: Float
) {
    fun play() {

        val numSamples = sampleRate * soundDuration
        val sample = DoubleArray(numSamples)
        val frequencyRange = startFrequency - endFrequency
        val numSamplesByFreq = numSamples / frequencyRange
        val generatedSnd = ShortArray(2 * numSamples)

        var currentFreqOfTone = startFrequency
        var sinePhase = 0.0
        Log.i("MainActivity", "currentFreqOfTone $currentFreqOfTone")
        for (i in 0 until numSamples) {
            sinePhase += sin(2 * Math.PI * currentFreqOfTone / numSamples)
            if (i % numSamplesByFreq.toInt() == 0) {
                currentFreqOfTone++
                Log.i("MainActivity", "currentFreqOfTone $currentFreqOfTone")
            }
            sample[i] = sinePhase
        }

        for ((idx, value) in sample.withIndex()) {
            generatedSnd[idx] = (value * Short.MAX_VALUE).toInt().toShort()
        }

        @Suppress("DEPRECATION") val audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate, AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT, numSamples * 2,
            AudioTrack.MODE_STATIC
        )

        audioTrack.write(generatedSnd, 0, generatedSnd.size)

        audioTrack.play()
    }
}
