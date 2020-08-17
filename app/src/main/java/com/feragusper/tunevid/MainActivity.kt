package com.feragusper.tunevid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.feragusper.tunevid.domain.AudioPlayer
import com.feragusper.tunevid.domain.AudioRecorder
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.TimeUnit


// originally from http://marblemice.blogspot.com/2010/04/generate-and-play-tone-in-android.html
// and modified by Steve Pomeroy <steve@staticfree.info>
class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO_PERMISSION
        )

        val handler = Handler()

        startTestButton.setOnClickListener {
            Thread(Runnable {
                handler.post {
                    startTest()
                }
            }).start()
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
        enableViews()
        stateText.text = getString(R.string.state_idle)
    }

    private fun onPermissionToRecordNotGranted() {
        disableViews()
        stateText.text = getString(R.string.state_recording_permissions_not_granted)
    }

    private fun enableViews() {
        setViewsIsEnabled(true)
    }

    private fun disableViews() {
        setViewsIsEnabled(false)
    }

    private fun setViewsIsEnabled(isEnabled: Boolean) {
        startTestButton.isEnabled = isEnabled
        soundDurationInput.isEnabled = isEnabled
        sampleRateInput.isEnabled = isEnabled
        startFrequencyInput.isEnabled = isEnabled
        endFrequencyInput.isEnabled = isEnabled
        recordDurationInput.isEnabled = isEnabled
    }

    private fun startTest() {
        stateText.text = getString(R.string.state_test_in_progress)
        disableViews()

        playSound()
        startRecording()
    }

    private fun playSound() {
        AudioPlayer(
            sampleRate = sampleRateInput.text.toString().toInt(),
            soundDuration = soundDurationInput.text.toString().toInt(),
            startFrequency = startFrequencyInput.text.toString().toFloat(),
            endFrequency = endFrequencyInput.text.toString().toFloat()
        ).play()
    }

    private fun startRecording() {
        Toast.makeText(this, getString(R.string.record_started), Toast.LENGTH_SHORT).show()

        AudioRecorder(
            context = this,
            sampleRate = sampleRateInput.text.toString().toInt()
        ).record(
            duration = TimeUnit.SECONDS.toMillis(recordDurationInput.text.toString().toLong())
        ) { recordFileName ->
            Toast.makeText(this, getString(R.string.record_complete), Toast.LENGTH_SHORT).show()
            stateText.text = getString(R.string.test_complete, recordFileName)
            enableViews()
//            FIXME Make this "open folder" code work :)
//            openRecordFolder.visibility = VISIBLE
//            openRecordFolder.setOnClickListener {
//                getExternalFilesDir(null)?.absolutePath?.let { folderPath ->
//                    val location = FileProvider.getUriForFile(
//                        this,
//                        applicationContext.packageName + ".provider",
//                        File(folderPath)
//                    )
//                    openFolder(location)
//                }
//            }
        }
    }

    private fun openFolder(location: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.parse("content://$location/"), "*/*")
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        startActivity(intent)
    }

}