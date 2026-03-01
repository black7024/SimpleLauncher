package com.simplelauncher.model

enum class LauncherArea {
    HOME,
    DOCK,
    DRAWER,
    TOP_BAR
}

enum class LauncherItemType {
    APP,
    SHORTCUT,
    FOLDER
}

data class ShortcutEntry(
    val id: String,
    var title: String,
    var packageName: String,
    var activityName: String,
    var area: LauncherArea
)

data class FolderEntry(
    val id: String,
    var title: String,
    var area: LauncherArea,
    val memberAppKeys: MutableList<String>
)

data class AppOverride(
    var customName: String? = null,
    var customIconBase64: String? = null,
    var hidden: Boolean = false,
    var sortOrder: Int = Int.MAX_VALUE
)

data class LayoutConfig(
    var rows: Int,
    var columns: Int
)

data class DisplayItem(
    val id: String,
    val type: LauncherItemType,
    val title: String,
    val appKey: String? = null,
    val shortcutId: String? = null,
    val folderId: String? = null
)
