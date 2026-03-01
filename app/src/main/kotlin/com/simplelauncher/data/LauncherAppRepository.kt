package com.simplelauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import com.simplelauncher.model.AppEntry
import java.util.Locale

class LauncherAppRepository(private val context: Context) {

    fun loadApps(): List<AppEntry> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val activities = pm.queryIntentActivities(launcherIntent, 0)
        return activities
            .asSequence()
            .filter { info -> info.activityInfo.packageName != context.packageName }
            .map { info -> toEntry(pm, info) }
            .sortedBy { app -> app.label.toLowerCase(Locale.getDefault()) }
            .toList()
    }

    private fun toEntry(pm: android.content.pm.PackageManager, info: ResolveInfo): AppEntry {
        val label = info.loadLabel(pm)?.toString() ?: info.activityInfo.name
        val componentName = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
        val icon = info.loadIcon(pm)
        return AppEntry(label = label, componentName = componentName, icon = icon)
    }
}
