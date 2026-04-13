package com.bbblllllocck.canned_emotions

import android.app.Application
import com.bbblllllocck.canned_emotions.core.api.AppContextProvider
import com.bbblllllocck.canned_emotions.core.api.ApiManager
import com.bbblllllocck.canned_emotions.core.database.geminiRequestCall.ClientManager
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.DatabaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CannedEmotionsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextProvider.init(this)//这个奇怪的傻逼名字是哪来的？唉copilot，让它在原有的基础上改就会这样，又是一个技术债！
        DatabaseManager.init(this)


        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        ClientManager.initialize(ApiManager.apisFlow, applicationScope)

    }
}

