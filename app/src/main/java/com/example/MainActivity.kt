package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.example.ui.navigation.SecureVaultApp

/**
 * Extends [FragmentActivity] so Jetpack [androidx.biometric.BiometricPrompt] can bind hardware
 * Android Keystore CryptoObject authorization directly to the activity window.
 *
 * Applies [WindowManager.LayoutParams.FLAG_SECURE] immediately on creation (before content renders)
 * to prevent sensitive app-switcher thumbnails and screenshots by default.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyScreenPrivacy(enabled = true)
        enableEdgeToEdge()
        setContent {
            SecureVaultApp(
                onScreenPrivacyChanged = { hidePreview ->
                    applyScreenPrivacy(hidePreview)
                },
            )
        }
    }

    private fun applyScreenPrivacy(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!enabled)
        }
    }
}
