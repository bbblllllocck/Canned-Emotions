package com.bbblllllocck.canned_emotions.ui.components

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun BackNavButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "<"//靠北哦得改成正常的箭头啦
) {
    Button(
        onClick = onClick,
        modifier = modifier
    ) {
        Text(label)
    }
}

