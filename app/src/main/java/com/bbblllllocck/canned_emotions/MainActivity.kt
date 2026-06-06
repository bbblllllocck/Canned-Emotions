package com.bbblllllocck.canned_emotions

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.bbblllllocck.canned_emotions.ui.features.drawerMenu.DrawerMenu
import com.bbblllllocck.canned_emotions.ui.theme.听点什么Theme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            听点什么Theme {
                DrawerMenu()
            }
        }
    }
}



