package com.bbblllllocck.canned_emotions.core.api

import android.content.Context

object AppContextProvider {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext != null) return
        synchronized(this) {
            if (appContext == null) {
                appContext = context.applicationContext
            }
        }
    }

    fun get(): Context {
        return appContext
            ?: error("AppContextProvider is not initialized. Call AppContextProvider.init(...) in Application.onCreate().")
    }
}

