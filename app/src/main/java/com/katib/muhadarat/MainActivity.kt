package com.katib.muhadarat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.katib.muhadarat.ui.TranscribeScreen

class MainActivity : ComponentActivity() {
    private lateinit var helper: WhisperHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        helper = WhisperHelper(this)
        setContent {
            MaterialTheme {
                Surface { TranscribeScreen(helper) }
            }
        }
    }

    override fun onDestroy() {
        try { helper.freeModel() } catch (_: Exception) {}
        super.onDestroy()
    }
}
