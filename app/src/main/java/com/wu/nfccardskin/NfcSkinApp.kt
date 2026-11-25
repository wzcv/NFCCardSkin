package com.wu.nfccardskin

import android.app.Application
import com.wu.nfccardskin.core.RemoteBridge
import com.wu.nfccardskin.core.Repository

class NfcSkinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repository.init(this)
        RemoteBridge.init { Repository.get().publish() }
    }
}
