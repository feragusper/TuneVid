package com.feragusper.tunevid

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.feragusper.tunevid.domain.TuneVidConfig
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import kotlin.experimental.and
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    companion object {
        const val RECORDER_BPP = 16
        const val AUDIO_RECORDER_FILE_EXT_WAV = ".wav"
        const val AUDIO_RECORDER_FOLDER = "AudioRecorder"
        const val AUDIO_RECORDER_TEMP_FILE = "record_temp.raw"
        const val RECORDER_SAMPLE_RATE = 44100
        const val RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO
        const val RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    // originally from http://marblemice.blogspot.com/2010/04/generate-and-play-tone-in-android.html
    // and modified by Steve Pomeroy <steve@staticfree.info>
    private var handler: Handler = Handler()

    private var recorder: AudioRecord? = null
    private var bufferSize = 0
    private var recordingThread: Thread? = null
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO_PERMISSION
        )

        startTestButton.setOnClickListener {
            val thread = Thread(Runnable {
                val tuneVidConfig = TuneVidConfig(
                    sampleRate = sampleRateInput.text.toString().toInt(),
                    soundDuration = soundDurationInput.text.toString().toInt(),
                    startFrequency = startFrequencyInput.text.toString().toFloat(),
                    endFrequency = endFrequencyInput.text.toString().toFloat()
                )

                handler.post {
                    playSoundAndRecord(
                        sampleRate = tuneVidConfig.sampleRate,
                        generatedSnd = generateTone(tuneVidConfig),
                        numSamples = tuneVidConfig.numSamples
                    )
                }
            })
            thread.start()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onPermissionToRecordGranted()
            } else {
                onPermissionToRecordNotGranted()
            }
        } else {
            onPermissionToRecordNotGranted()
        }
    }

    private fun onPermissionToRecordGranted() {
        startTestButton.isEnabled = true
        stateText.text = getString(R.string.state_idle)

    }

    private fun onPermissionToRecordNotGranted() {
        startTestButton.isEnabled = false
        stateText.text = getString(R.string.state_recording_permissions_not_granted)
    }

    private fun stopRecording() {
        Toast.makeText(this, "Recording completed", Toast.LENGTH_SHORT).show()
        stateText.text = getString(R.string.state_idle)
        if (null != recorder) {
            isRecording = false
            val i = recorder?.state
            if (i == 1) recorder?.stop()
            recorder?.release()
            recorder = null
            recordingThread = null
        }
        copyWaveFile(getTempFilename(), getFilename())
        deleteTempFile()
    }

    private fun deleteTempFile() {
        val file = File(getTempFilename())
        file.delete()
    }

    private fun copyWaveFile(
        inFilename: String,
        outFilename: String
    ) {
        var fileInputStream: FileInputStream?
        var fileOutputStream: FileOutputStream?
        var totalAudioLen: Long = 0
        var totalDataLen = totalAudioLen + 36
        val longSampleRate = RECORDER_SAMPLE_RATE.toLong()
        val channels = 2
        val byteRate = RECORDER_BPP * RECORDER_SAMPLE_RATE * channels / 8.toLong()
        val data = ByteArray(bufferSize)
        try {
            fileInputStream = FileInputStream(inFilename)
            fileOutputStream = FileOutputStream(outFilename)
            totalAudioLen = fileInputStream.channel.size()
            totalDataLen = totalAudioLen + 36
            WriteWaveFileHeader(
                fileOutputStream, totalAudioLen, totalDataLen,
                longSampleRate, channels, byteRate
            )
            while (fileInputStream.read(data) != -1) {
                fileOutputStream.write(data)
            }
            fileInputStream.close()
            fileOutputStream.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun getFilename(): String {
        val filepath = getExternalFilesDir(null)
        val file = File(filepath, AUDIO_RECORDER_FOLDER)
        if (!file.exists()) {
            file.mkdirs()
        }
        return file.absolutePath + "/" + System.currentTimeMillis() + AUDIO_RECORDER_FILE_EXT_WAV
    }

    private fun getTempFilename(): String {
        val filepath = getExternalFilesDir(null)
        val file = File(filepath, AUDIO_RECORDER_FOLDER)
        if (!file.exists()) {
            file.mkdirs()
        }
        val tempFile = File(file, AUDIO_RECORDER_TEMP_FILE)
        return tempFile.absolutePath
    }

    private fun startRecording() {
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        stateText.text = getString(R.string.state_test_in_progress)
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDER_SAMPLE_RATE,
            RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING,
            bufferSize
        )
        val i: Int = recorder!!.state
        if (i == 1) recorder?.startRecording()
        isRecording = true
        recordingThread = Thread(Runnable { writeAudioDataToFile() }, "AudioRecorder Thread")
        recordingThread?.start()
    }

    private fun writeAudioDataToFile() {
        val data = ByteArray(bufferSize)
        val filename = getTempFilename()
        var os: FileOutputStream? = null
        try {
            os = FileOutputStream(filename)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        var read: Int
        if (null != os) {
            while (isRecording) {
                read = recorder!!.read(data, 0, bufferSize)
                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                    try {
                        os.write(data)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
            try {
                os.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    @Throws(IOException::class)
    private fun WriteWaveFileHeader(
        out: FileOutputStream, totalAudioLen: Long,
        totalDataLen: Long, longSampleRate: Long, channels: Int,
        byteRate: Long
    ) {
        val header = ByteArray(44)
        header[0] = 'R'.toByte() // RIFF/WAVE header
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.toByte()
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        header[12] = 'f'.toByte() // 'fmt ' chunk
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte()
        header[27] = (longSampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (2 * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = RECORDER_BPP.toByte() // bits per sample
        header[35] = 0
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()
        out.write(header, 0, 44)
    }

    private fun generateTone(tuneVidConfig: TuneVidConfig): ByteArray {

        var currentFreqOfTone = tuneVidConfig.startFrequency
        Log.i("MainActivity", "currentFreqOfTone $currentFreqOfTone")
        for (i in 0 until tuneVidConfig.numSamples) {
            val angle = 2 * Math.PI * i / (tuneVidConfig.sampleRate / currentFreqOfTone)
            tuneVidConfig.sample[i] = sin(angle)

            if (i % tuneVidConfig.numSamplesByFreq.toInt() == 0) {
                currentFreqOfTone++
                Log.i("MainActivity", "currentFreqOfTone $currentFreqOfTone")
            }
        }

        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
        var idx = 0
        for (value in tuneVidConfig.sample) {
            // scale to maximum amplitude
            val ampMaxValue = (value * 32767).toShort()
            // in 16 bit wav PCM, first byte is the low order byte
            tuneVidConfig.generatedSnd[idx++] = (ampMaxValue and 0x00ff).toByte()
            tuneVidConfig.generatedSnd[idx++] = (ampMaxValue.toInt() and 0xff00).ushr(8).toByte()
        }

        return tuneVidConfig.generatedSnd;
    }

    fun playSoundAndRecord(sampleRate: Int, generatedSnd: ByteArray, numSamples: Int) {
        val audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate, AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT, numSamples * 2,
            AudioTrack.MODE_STATIC
        )

        bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_CONFIGURATION_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack.write(generatedSnd, 0, generatedSnd.size)
        startRecording()
        audioTrack.play()

        Handler().postDelayed({
            stopRecording()
        }, 25000)
    }
}