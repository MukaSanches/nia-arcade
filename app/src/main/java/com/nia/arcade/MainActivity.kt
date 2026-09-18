package com.nia.arcade

import android.os.Build
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nia.arcade.ui.NiaApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestLowLatency(window)
        setContent { NiaApp() }
    }

    private fun requestLowLatency(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { window.setPreferMinimalPostProcessing(true) }
        }
    }
}
