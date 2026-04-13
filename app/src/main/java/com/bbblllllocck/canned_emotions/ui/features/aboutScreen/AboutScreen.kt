package com.bbblllllocck.canned_emotions.ui.features.aboutScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler

@Composable
fun AboutScreen() {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = { uriHandler.openUri("https://github.com/bbblllllocck/Canned-Emotions?tab=readme-ov-file") }) {
            Text("打开 GitHub 查看使用教程或跟进项目")
        }
    }
}
