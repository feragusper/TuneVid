package com.feragusper.tunevid.domain

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.experimental.and
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
        val generatedSnd = ByteArray(2 * numSamples)

        var currentFreqOfTone = startFrequency
        Log.i("MainActivity", "currentFreqOfTone $currentFreqOfTone")
        for (i in 0 until numSamples) {
            val angle = 2 * Math.PI * i / (sampleRate / currentFreqOfTone)
            sample[i] = sin(angle)

            if (i % numSamplesByFreq.toInt() == 0) {
                currentFreqOfTone++
                Log.i("MainActivity", "currentFreqOfTone $currentFreqOfTone")
            }
        }

        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
        var idx = 0
        for (value in sample) {
            // scale to maximum amplitude
            val ampMaxValue = (value * 32767).toShort()
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (ampMaxValue and 0x00ff).toByte()
            generatedSnd[idx++] = (ampMaxValue.toInt() and 0xff00).ushr(8).toByte()
        }

        val audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate, AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT, numSamples * 2,
            AudioTrack.MODE_STATIC
        )

        audioTrack.write(generatedSnd, 0, generatedSnd.size)

        audioTrack.play()
    }
}
