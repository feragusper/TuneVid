package com.feragusper.tunevid.domain

data class TuneVidConfig(
    var sampleRate: Int,
    var soundDuration: Int,
    var startFrequency: Float,
    var endFrequency: Float
) {
    var numSamples = sampleRate * soundDuration
    var sample = DoubleArray(numSamples)
    var frequencyRange = startFrequency - endFrequency
    var numSamplesByFreq = numSamples / frequencyRange
    var generatedSnd = ByteArray(2 * numSamples)
}
