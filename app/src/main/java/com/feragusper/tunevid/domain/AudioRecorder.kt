package com.feragusper.tunevid.domain

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import java.io.*

data class AudioRecorder(
    val context: Context,
    val sampleRate: Int
) {

    companion object {
        const val RECORDER_BPP = 16
        const val AUDIO_RECORDER_FILE_EXT_WAV = ".wav"
        const val AUDIO_RECORDER_FOLDER = "AudioRecorder"
        const val AUDIO_RECORDER_TEMP_FILE = "record_temp.raw"
        const val RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO
        const val RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private var recorder: AudioRecord? = null
    private var bufferSize = 0
    private var recordingThread: Thread? = null
    private var isRecording = false

    fun record(duration: Long, onStopRecording: (String) -> Unit) {
        bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_CONFIGURATION_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING,
            bufferSize
        )
        if (recorder!!.state == 1) {
            recorder?.startRecording()
        }
        isRecording = true

        Thread(
            Runnable {
                writeAudioDataToFile()
            }).start()

        Handler().postDelayed({
            stopRecording(onStopRecording)
        }, duration)
    }

    private fun stopRecording(onStopRecording: (String) -> Unit) {
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
        onStopRecording(getFilename())
    }

    private fun deleteTempFile() {
        val file = File(getTempFilename())
        file.delete()
    }

    private fun copyWaveFile(
        inFilename: String,
        outFilename: String
    ) {
        val fileInputStream: FileInputStream?
        val fileOutputStream: FileOutputStream?
        val totalAudioLen: Long
        val totalDataLen: Long
        val longSampleRate = sampleRate.toLong()
        val channels = 2
        val byteRate = RECORDER_BPP * sampleRate * channels / 8.toLong()
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
        val filepath = context.getExternalFilesDir(null)
        val file = File(filepath, AUDIO_RECORDER_FOLDER)
        if (!file.exists()) {
            file.mkdirs()
        }
        return file.absolutePath + "/" + System.currentTimeMillis() + AUDIO_RECORDER_FILE_EXT_WAV
    }

    private fun getTempFilename(): String {
        val filepath = context.getExternalFilesDir(null)
        val file = File(filepath, AUDIO_RECORDER_FOLDER)
        if (!file.exists()) {
            file.mkdirs()
        }
        val tempFile = File(file, AUDIO_RECORDER_TEMP_FILE)
        return tempFile.absolutePath
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
}
