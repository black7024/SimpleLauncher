package com.simplelauncher.data

import android.content.Context
import android.util.Base64
import com.simplelauncher.model.AppOverride
import com.simplelauncher.model.FolderEntry
import com.simplelauncher.model.LayoutConfig
import com.simplelauncher.model.LauncherArea
import com.simplelauncher.model.ShortcutEntry
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LauncherStateRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getAppOverride(appKey: String): AppOverride {
        val all = loadAppOverrideMap()
        return all[appKey] ?: AppOverride()
    }

    fun getAllAppOverrides(): Map<String, AppOverride> {
        return loadAppOverrideMap()
    }

    fun updateAppOverride(appKey: String, block: (AppOverride) -> Unit) {
        val all = loadAppOverrideMap().toMutableMap()
        val override = all[appKey] ?: AppOverride()
        block(override)
        all[appKey] = override
        saveAppOverrideMap(all)
    }

    fun setAppOrder(appKey: String, order: Int) {
        updateAppOverride(appKey) { it.sortOrder = order }
    }

    fun getDockAppKeys(): MutableList<String> {
        val array = safeJsonArray(prefs.getString(KEY_DOCK_KEYS, "[]"), "[]")
        val result = mutableListOf<String>()
        for (index in 0 until array.length()) {
            result.add(array.optString(index))
        }
        return result
    }

    fun saveDockAppKeys(keys: List<String>) {
        val array = JSONArray()
        keys.forEach { array.put(it) }
        prefs.edit().putString(KEY_DOCK_KEYS, array.toString()).apply()
    }

    fun getHomeManualOrder(): MutableList<String> {
        val array = safeJsonArray(prefs.getString(KEY_HOME_ORDER, "[]"), "[]")
        val result = mutableListOf<String>()
        for (index in 0 until array.length()) {
            result.add(array.optString(index))
        }
        return result
    }

    fun saveHomeManualOrder(keys: List<String>) {
        val array = JSONArray()
        keys.forEach { array.put(it) }
        prefs.edit().putString(KEY_HOME_ORDER, array.toString()).apply()
    }

    fun getShortcuts(): MutableList<ShortcutEntry> {
        val array = safeJsonArray(prefs.getString(KEY_SHORTCUTS, "[]"), "[]")
        val result = mutableListOf<ShortcutEntry>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val area = safeArea(item.optString("area"))
            result.add(
                ShortcutEntry(
                    id = item.optString("id"),
                    title = item.optString("title"),
                    packageName = item.optString("pkg"),
                    activityName = item.optString("act"),
                    area = area
                )
            )
        }
        return result
    }

    fun saveShortcuts(shortcuts: List<ShortcutEntry>) {
        val array = JSONArray()
        shortcuts.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("pkg", it.packageName)
                    .put("act", it.activityName)
                    .put("area", it.area.name)
            )
        }
        prefs.edit().putString(KEY_SHORTCUTS, array.toString()).apply()
    }

    fun ensurePresetShortcuts() {
        val shortcuts = getShortcuts()
        if (shortcuts.any { it.id.startsWith("preset_") }) {
            return
        }
        val preset = listOf(
            ShortcutEntry("preset_settings", "系统设置", "com.android.settings", "com.android.settings.Settings", LauncherArea.TOP_BAR),
            ShortcutEntry("preset_wifi", "WIFI", "com.android.settings", "com.android.settings.Settings\$WifiSettingsActivity", LauncherArea.TOP_BAR),
            ShortcutEntry("preset_bluetooth", "蓝牙", "com.android.settings", "com.android.settings.bluetooth.BluetoothSettings", LauncherArea.TOP_BAR),
            ShortcutEntry("preset_display", "显示", "com.android.settings", "com.android.settings.DisplaySettings", LauncherArea.TOP_BAR)
        )
        saveShortcuts(shortcuts + preset)
    }

    fun getFolders(): MutableList<FolderEntry> {
        val array = safeJsonArray(prefs.getString(KEY_FOLDERS, "[]"), "[]")
        val result = mutableListOf<FolderEntry>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val members = mutableListOf<String>()
            val memberArray = item.optJSONArray("members") ?: JSONArray()
            for (memberIndex in 0 until memberArray.length()) {
                members.add(memberArray.optString(memberIndex))
            }
            result.add(
                FolderEntry(
                    id = item.optString("id"),
                    title = item.optString("title"),
                    area = safeArea(item.optString("area")),
                    memberAppKeys = members
                )
            )
        }
        return result
    }

    fun saveFolders(folders: List<FolderEntry>) {
        val array = JSONArray()
        folders.forEach {
            val members = JSONArray()
            it.memberAppKeys.forEach { key -> members.put(key) }
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("area", it.area.name)
                    .put("members", members)
            )
        }
        prefs.edit().putString(KEY_FOLDERS, array.toString()).apply()
    }

    fun createFolder(title: String, area: LauncherArea, members: List<String>): FolderEntry {
        val folder = FolderEntry(
            id = "folder_${UUID.randomUUID().toString().replace("-", "")}",
            title = title,
            area = area,
            memberAppKeys = members.toMutableList()
        )
        val all = getFolders()
        all.add(folder)
        saveFolders(all)
        return folder
    }

    fun getLayoutConfig(): LayoutConfig {
        val rows = prefs.getInt(KEY_LAYOUT_ROWS, 4)
        val columns = prefs.getInt(KEY_LAYOUT_COLUMNS, 6)
        return LayoutConfig(rows = rows, columns = columns)
    }

    fun saveLayoutConfig(rows: Int, columns: Int) {
        prefs.edit()
            .putInt(KEY_LAYOUT_ROWS, rows.coerceAtLeast(1))
            .putInt(KEY_LAYOUT_COLUMNS, columns.coerceAtLeast(1))
            .apply()
    }

    fun getRotationMode(): Int {
        return prefs.getInt(KEY_ROTATION_MODE, 0)
    }

    fun saveRotationMode(mode: Int) {
        prefs.edit().putInt(KEY_ROTATION_MODE, mode).apply()
    }

    fun getWallpaperBase64(): String? {
        return prefs.getString(KEY_WALLPAPER, null)
    }

    fun saveWallpaperBase64(base64: String?) {
        prefs.edit().putString(KEY_WALLPAPER, base64).apply()
    }

    fun createShortcut(title: String, packageName: String, activityName: String, area: LauncherArea): ShortcutEntry {
        val entry = ShortcutEntry(
            id = "shortcut_${UUID.randomUUID().toString().replace("-", "")}",
            title = title,
            packageName = packageName,
            activityName = activityName,
            area = area
        )
        val list = getShortcuts()
        list.add(entry)
        saveShortcuts(list)
        return entry
    }

    private fun loadAppOverrideMap(): Map<String, AppOverride> {
        val objectMap = safeJsonObject(prefs.getString(KEY_APP_OVERRIDES, "{}"), "{}")
        val iterator = objectMap.keys()
        val result = mutableMapOf<String, AppOverride>()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val value = objectMap.optJSONObject(key) ?: continue
            result[key] = AppOverride(
                customName = value.optString("name", null),
                customIconBase64 = value.optString("icon", null),
                hidden = value.optBoolean("hidden", false),
                sortOrder = value.optInt("order", Int.MAX_VALUE)
            )
        }
        return result
    }

    private fun saveAppOverrideMap(data: Map<String, AppOverride>) {
        val objectMap = JSONObject()
        data.forEach { (key, value) ->
            objectMap.put(
                key,
                JSONObject()
                    .put("name", value.customName)
                    .put("icon", value.customIconBase64)
                    .put("hidden", value.hidden)
                    .put("order", value.sortOrder)
            )
        }
        prefs.edit().putString(KEY_APP_OVERRIDES, objectMap.toString()).apply()
    }

    private fun safeJsonArray(raw: String?, fallback: String): JSONArray {
        return try {
            JSONArray(raw ?: fallback)
        } catch (_: Throwable) {
            JSONArray(fallback)
        }
    }

    private fun safeJsonObject(raw: String?, fallback: String): JSONObject {
        return try {
            JSONObject(raw ?: fallback)
        } catch (_: Throwable) {
            JSONObject(fallback)
        }
    }

    private fun safeArea(value: String): LauncherArea {
        return try {
            LauncherArea.valueOf(value)
        } catch (_: Throwable) {
            LauncherArea.HOME
        }
    }

    companion object {
        private const val PREF_NAME = "launcher_state"
        private const val KEY_APP_OVERRIDES = "app_overrides"
        private const val KEY_DOCK_KEYS = "dock_keys"
        private const val KEY_HOME_ORDER = "home_order"
        private const val KEY_SHORTCUTS = "shortcuts"
        private const val KEY_FOLDERS = "folders"
        private const val KEY_LAYOUT_ROWS = "layout_rows"
        private const val KEY_LAYOUT_COLUMNS = "layout_columns"
        private const val KEY_ROTATION_MODE = "rotation_mode"
        private const val KEY_WALLPAPER = "wallpaper_base64"

        fun encodeBytes(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

        fun decodeBytes(value: String): ByteArray = Base64.decode(value, Base64.DEFAULT)
    }
}
