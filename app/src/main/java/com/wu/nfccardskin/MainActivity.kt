package com.wu.nfccardskin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wu.nfccardskin.core.RemoteBridge
import com.wu.nfccardskin.core.Repository
import com.wu.nfccardskin.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }

    override fun onResume() {
        super.onResume()
        // Scope / hook status may have changed in LSPosed or the wallet while we were away.
        RemoteBridge.refreshState()
        Repository.get().refreshImages()
    }
}
