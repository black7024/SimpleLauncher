package com.simplelauncher.model

import android.content.ComponentName
import android.graphics.drawable.Drawable

data class AppEntry(
    val label: String,
    val componentName: ComponentName,
    val icon: Drawable?
)
