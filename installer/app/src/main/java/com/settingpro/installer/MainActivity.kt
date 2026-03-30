package com.settingpro.installer

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        const val TARGET_PACKAGE = "com.settingpro.camera"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fix #11: Use non-deprecated getPackageInfo for Android 13+
        val isInstalled = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(TARGET_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(TARGET_PACKAGE, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        if (isInstalled) {
            val launchIntent = packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(launchIntent)
                finish()
                return
            }
        }

        createUI()
    }

    private fun createUI() {
        val layout = androidx.constraintlayout.widget.ConstraintLayout(this).apply {
            layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT,
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(48, 48, 48, 48)
        }

        val titleText = TextView(this).apply {
            id = android.R.id.text1
            text = "Camera App Installer"
            textSize = 24f
            setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Large)
        }

        val descText = TextView(this).apply {
            id = android.R.id.text2
            text = "Click below to install the camera app"
            textSize = 16f
            setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Medium)
        }

        val installButton = Button(this).apply {
            id = android.R.id.button1
            text = "Install App"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, InstallerActivity::class.java))
            }
        }

        layout.addView(titleText)
        layout.addView(descText)
        layout.addView(installButton)

        androidx.constraintlayout.widget.ConstraintSet().apply {
            clone(layout)
            connect(titleText.id, androidx.constraintlayout.widget.ConstraintSet.TOP, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.TOP, 150)
            connect(titleText.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
            connect(titleText.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)

            connect(descText.id, androidx.constraintlayout.widget.ConstraintSet.TOP, titleText.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, 32)
            connect(descText.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
            connect(descText.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)

            connect(installButton.id, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, 100)
            connect(installButton.id, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
            connect(installButton.id, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
            applyTo(layout)
        }

        setContentView(layout)
    }
}