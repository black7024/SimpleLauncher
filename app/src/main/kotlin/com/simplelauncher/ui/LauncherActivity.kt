package com.simplelauncher.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Process
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.promeg.pinyinhelper.Pinyin
import com.simplelauncher.data.LauncherAppRepository
import com.simplelauncher.data.LauncherStateRepository
import com.simplelauncher.model.AppEntry
import com.simplelauncher.model.DisplayItem
import com.simplelauncher.model.LauncherArea
import com.simplelauncher.model.LauncherItemType
import com.simplelauncher.model.ShortcutEntry
import com.simplelauncher.perf.PerformanceConfig
import com.simplelauncher.ui.draw.DrawableFactory
import com.simplelauncher.ui.focus.FocusController
import com.simplelauncher.ui.theme.ThemeConfig
import com.simplelauncher.ui.tts.TtsController
import com.simplelauncher.ui.util.ImageTools
import com.simplelauncher.ui.widget.DateWidgetView
import com.simplelauncher.ui.widget.TimeWidgetView
import com.simplelauncher.ui.widget.WeatherWidgetView
import java.util.Locale

class LauncherActivity : Activity(), PerformanceConfig.Listener {
    private lateinit var root: FrameLayout
    private lateinit var wallpaperLayer: FrameLayout
    private lateinit var topShortcutBar: LinearLayout
    private lateinit var homeRecycler: RecyclerView
    private lateinit var dockRecycler: RecyclerView
    private lateinit var drawerPanel: FrameLayout
    private lateinit var drawerRecycler: RecyclerView
    private lateinit var drawerSearch: EditText
    private lateinit var drawerHiddenToggle: TextView
    private lateinit var emptyText: TextView

    private lateinit var homeAdapter: AppListAdapter
    private lateinit var dockAdapter: AppListAdapter
    private lateinit var drawerAdapter: AppListAdapter
    private lateinit var topAdapter: AppListAdapter
    private lateinit var topShortcutRecycler: RecyclerView

    private lateinit var appRepository: LauncherAppRepository
    private lateinit var stateRepository: LauncherStateRepository

    private val focusController = FocusController()
    private lateinit var ttsController: TtsController

    private val allApps = mutableListOf<AppEntry>()
    private val appMap = mutableMapOf<String, AppEntry>()
    private val appSortKeys = mutableListOf<String>()

    private var themeColor: Int = 0
    private var drawerVisible = false
    private var drawerShowHidden = false
    private var pendingIconAppKey: String? = null
    private var wallpaperBitmap: Bitmap? = null
    private var activeCompressTask: CompressTask? = null

    private val iconCache = object : LruCache<String, Drawable>(180) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Drawable?, newValue: Drawable?) {
            val bitmapDrawable = oldValue as? BitmapDrawable ?: return
            val bitmap = bitmapDrawable.bitmap ?: return
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeColor = ThemeConfig.getThemeColor(this)
        appRepository = LauncherAppRepository(applicationContext)
        stateRepository = LauncherStateRepository(this)
        stateRepository.ensurePresetShortcuts()
        ttsController = TtsController(this)
        ttsController.init()

        initViews()
        initAdapters()
        bindDrawerSearch()
        loadApps()
        applyRotationMode()
        applyVisualState(true)
        refreshAllLists()

        PerformanceConfig.addListener(this)
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        applyRotationMode()
        applyVisualState(false)
        refreshAllLists()
    }

    override fun onDestroy() {
        PerformanceConfig.removeListener(this)
        activeCompressTask?.cancel(true)
        activeCompressTask = null
        iconCache.evictAll()
        recycleWallpaper()
        ttsController.release()
        super.onDestroy()
    }

    override fun onPerformanceChanged() {
        applyVisualState(false)
        refreshAllLists()
    }

    private fun initViews() {
        root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        wallpaperLayer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
        }

        topShortcutBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(130f))
            visibility = View.GONE
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
        }

        val topTitle = TextView(this).apply {
            text = "顶部快捷栏"
            setTextColor(Color.argb(230, 245, 245, 245))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }

        topShortcutRecycler = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutManager = LinearLayoutManager(this@LauncherActivity, LinearLayoutManager.HORIZONTAL, false)
            clipToPadding = false
            setPadding(0, dp(4f), 0, dp(4f))
        }

        topShortcutBar.addView(topTitle)
        topShortcutBar.addView(topShortcutRecycler)

        val widgetStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, dp(4f), 0, dp(8f))
        }

        val timeWidget = TimeWidgetView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(200f), dp(56f))
        }
        val dateWidget = DateWidgetView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(280f), dp(42f)).apply {
                leftMargin = dp(12f)
            }
        }
        val weatherWidget = WeatherWidgetView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(180f), dp(42f)).apply {
                leftMargin = dp(12f)
            }
        }
        widgetStrip.addView(timeWidget)
        widgetStrip.addView(dateWidget)
        widgetStrip.addView(weatherWidget)

        homeRecycler = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, dp(4f), 0, dp(4f))
        }

        val dockTitle = TextView(this).apply {
            text = "Dock"
            setTextColor(Color.argb(180, 218, 218, 218))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }

        dockRecycler = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170f))
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutManager = LinearLayoutManager(this@LauncherActivity, LinearLayoutManager.HORIZONTAL, false)
            clipToPadding = false
            setPadding(0, dp(4f), 0, dp(4f))
        }

        val actionBar = createBottomActionBar()

        mainContainer.addView(topShortcutBar)
        mainContainer.addView(widgetStrip)
        mainContainer.addView(homeRecycler)
        mainContainer.addView(dockTitle)
        mainContainer.addView(dockRecycler)
        mainContainer.addView(actionBar)

        drawerPanel = createDrawerPanel()

        emptyText = TextView(this).apply {
            text = "没有可显示的应用"
            setTextColor(Color.argb(210, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        root.addView(wallpaperLayer)
        root.addView(mainContainer)
        root.addView(drawerPanel)
        root.addView(emptyText)
        setContentView(root)
    }

    private fun createDrawerPanel(): FrameLayout {
        val panel = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            visibility = View.GONE
            alpha = 0f
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(dp(28f), dp(18f), dp(28f), dp(18f))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER_VERTICAL
        }

        drawerSearch = EditText(this).apply {
            hint = "抽屉搜索（支持拼音/首字母）"
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(140, 255, 255, 255))
        }

        drawerHiddenToggle = TextView(this).apply {
            text = "隐藏过滤:关"
            gravity = Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = true
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(10f)
            }
            setOnClickListener {
                drawerShowHidden = !drawerShowHidden
                updateDrawerToggleText()
                refreshAllLists()
            }
        }

        topRow.addView(drawerSearch)
        topRow.addView(drawerHiddenToggle)

        drawerRecycler = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, dp(10f), 0, dp(0f))
        }

        content.addView(topRow)
        content.addView(drawerRecycler)
        panel.addView(content)
        return panel
    }

    private fun createBottomActionBar(): LinearLayout {
        val actionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.END
            setPadding(0, dp(8f), 0, 0)
        }

        val actions = listOf(
            "抽屉" to { toggleDrawer(true) },
            "布局" to { showLayoutDialog() },
            "快捷方式" to { showShortcutDialog(LauncherArea.HOME) },
            "文件夹" to { showCreateFolderDialog(LauncherArea.HOME) },
            "壁纸" to { pickWallpaper() },
            "性能" to { showPerformanceDialog() },
            "方向" to { showRotationDialog() },
            "主题" to { showThemeDialog() }
        )

        actions.forEach { (title, action) ->
            actionBar.addView(createActionButton(title, action))
        }
        return actionBar
    }

    private fun createActionButton(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            isFocusable = true
            isFocusableInTouchMode = true
            gravity = Gravity.CENTER
            setSingleLine(true)
            setTextColor(Color.argb(236, 245, 245, 245))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            val horizontalPadding = dp(12f)
            val verticalPadding = dp(7f)
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(8f)
            }
            background = DrawableFactory.createActionButtonDrawable(this@LauncherActivity, themeColor, false)
            setOnClickListener { onClick() }
            onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                view.background = DrawableFactory.createActionButtonDrawable(this@LauncherActivity, themeColor, hasFocus)
            }
        }
    }

    private fun initAdapters() {
        homeAdapter = AppListAdapter(
            context = this,
            onResolveIcon = { resolveIconForDisplay(it) },
            onItemClick = { handleItemClick(it) },
            onItemLongClick = { handleItemLongClick(it, LauncherArea.HOME) },
            onItemFocused = { handleItemFocused(it) }
        )

        dockAdapter = AppListAdapter(
            context = this,
            onResolveIcon = { resolveIconForDisplay(it) },
            onItemClick = { handleItemClick(it) },
            onItemLongClick = { handleItemLongClick(it, LauncherArea.DOCK) },
            onItemFocused = { handleItemFocused(it) }
        )

        drawerAdapter = AppListAdapter(
            context = this,
            onResolveIcon = { resolveIconForDisplay(it) },
            onItemClick = { handleItemClick(it) },
            onItemLongClick = { handleItemLongClick(it, LauncherArea.DRAWER) },
            onItemFocused = { handleItemFocused(it) }
        )

        topAdapter = AppListAdapter(
            context = this,
            onResolveIcon = { resolveIconForDisplay(it) },
            onItemClick = { handleItemClick(it) },
            onItemLongClick = { handleItemLongClick(it, LauncherArea.TOP_BAR) },
            onItemFocused = { handleItemFocused(it) }
        )

        homeRecycler.adapter = homeAdapter
        dockRecycler.adapter = dockAdapter
        drawerRecycler.adapter = drawerAdapter
        topShortcutRecycler.adapter = topAdapter

        homeRecycler.layoutManager = GridLayoutManager(this, resolveHomeSpanCount())
        drawerRecycler.layoutManager = GridLayoutManager(this, resolveHomeSpanCount())

        attachDragSorter()
        focusController.registerRegionFirstView("HOME", homeRecycler)
        focusController.registerRegionFirstView("DOCK", dockRecycler)
        focusController.registerRegionFirstView("DRAWER", drawerRecycler)
        focusController.registerRegionFirstView("TOP", topShortcutRecycler)
    }

    private fun attachDragSorter() {
        val callback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                homeAdapter.swapItems(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val orderKeys = homeAdapter.snapshotItems().mapNotNull { it.appKey }
                stateRepository.saveHomeManualOrder(orderKeys)
                appSortKeys.clear()
                appSortKeys.addAll(orderKeys)
            }

            override fun isLongPressDragEnabled(): Boolean = true
        }
        ItemTouchHelper(callback).attachToRecyclerView(homeRecycler)
    }

    private fun bindDrawerSearch() {
        drawerSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshAllLists()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun loadApps() {
        allApps.clear()
        allApps.addAll(appRepository.loadApps())
        appMap.clear()
        allApps.forEach { entry -> appMap[appKey(entry)] = entry }

        appSortKeys.clear()
        appSortKeys.addAll(stateRepository.getHomeManualOrder())

        emptyText.visibility = if (allApps.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshAllLists() {
        val homeItems = buildItemsForArea(LauncherArea.HOME)
        val dockItems = buildItemsForArea(LauncherArea.DOCK)
        val drawerItems = buildItemsForArea(LauncherArea.DRAWER)
        val topItems = buildItemsForArea(LauncherArea.TOP_BAR)

        homeAdapter.replaceItems(homeItems)
        dockAdapter.replaceItems(dockItems)
        drawerAdapter.replaceItems(drawerItems)
        topAdapter.replaceItems(topItems)

        updateDrawerToggleText()
        homeAdapter.updateThemeColor(themeColor)
        dockAdapter.updateThemeColor(themeColor)
        drawerAdapter.updateThemeColor(themeColor)
        topAdapter.updateThemeColor(themeColor)
    }

    private fun buildItemsForArea(area: LauncherArea): List<DisplayItem> {
        val overrides = stateRepository.getAllAppOverrides()
        val dockKeys = stateRepository.getDockAppKeys()
        val dockSet = dockKeys.toHashSet()

        val shortcuts = stateRepository.getShortcuts().filter { it.area == area }
        val folders = stateRepository.getFolders().filter { it.area == area }

        val appItems = mutableListOf<DisplayItem>()
        allApps.forEach { app ->
            val key = appKey(app)
            val override = overrides[key] ?: AppOverrideFallback
            val isHidden = override.hidden
            if (area != LauncherArea.DRAWER && isHidden) {
                return@forEach
            }
            if (area == LauncherArea.DRAWER && !drawerShowHidden && isHidden) {
                return@forEach
            }
            if (area == LauncherArea.DOCK) {
                if (!dockSet.contains(key)) {
                    return@forEach
                }
            }

            val title = override.customName ?: app.label
            if (area == LauncherArea.DRAWER && !matchQuery(title, drawerSearch.text?.toString().orEmpty())) {
                return@forEach
            }

            appItems.add(
                DisplayItem(
                    id = "app_$key",
                    type = LauncherItemType.APP,
                    title = title,
                    appKey = key
                )
            )
        }

        val orderedApps = when (area) {
            LauncherArea.HOME -> orderByManual(appItems)
            LauncherArea.DOCK -> {
                appItems.sortedBy { item -> dockKeys.indexOf(item.appKey).let { if (it == -1) Int.MAX_VALUE else it } }
            }
            else -> appItems.sortedBy { it.title.toLowerCase(Locale.getDefault()) }
        }.toMutableList()

        val folderItems = folders.map {
            DisplayItem(
                id = it.id,
                type = LauncherItemType.FOLDER,
                title = it.title,
                folderId = it.id
            )
        }

        val shortcutItems = shortcuts.map {
            DisplayItem(
                id = it.id,
                type = LauncherItemType.SHORTCUT,
                title = it.title,
                shortcutId = it.id
            )
        }

        orderedApps.addAll(folderItems)
        orderedApps.addAll(shortcutItems)
        return orderedApps
    }

    private fun orderByManual(items: List<DisplayItem>): List<DisplayItem> {
        val mutable = items.toMutableList()
        val ordered = mutableListOf<DisplayItem>()
        appSortKeys.forEach { key ->
            val found = mutable.firstOrNull { it.appKey == key } ?: return@forEach
            ordered.add(found)
            mutable.remove(found)
        }
        ordered.addAll(mutable.sortedBy { it.title.toLowerCase(Locale.getDefault()) })
        return ordered
    }

    private fun resolveIconForDisplay(item: DisplayItem): Drawable {
        val cacheKey = when (item.type) {
            LauncherItemType.APP -> {
                val key = item.appKey.orEmpty()
                val custom = stateRepository.getAppOverride(key).customIconBase64
                if (custom.isNullOrEmpty()) "APP:$key" else "APP:$key:${custom.hashCode()}"
            }
            LauncherItemType.FOLDER -> "FOLDER:${item.id}:$themeColor"
            LauncherItemType.SHORTCUT -> "SHORTCUT:${item.id}:$themeColor"
        }
        iconCache.get(cacheKey)?.let { return it }

        return when (item.type) {
            LauncherItemType.APP -> {
                val key = item.appKey.orEmpty()
                val override = stateRepository.getAppOverride(key)
                val iconBase64 = override.customIconBase64
                if (!iconBase64.isNullOrEmpty()) {
                    try {
                        val bitmap = ImageTools.decodeBase64(iconBase64)
                        BitmapDrawable(resources, bitmap).also { iconCache.put(cacheKey, it) }
                    } catch (_: Throwable) {
                        (appMap[key]?.icon ?: DrawableFactory.createMonogramIconDrawable(item.title, themeColor)).also {
                            iconCache.put(cacheKey, it)
                        }
                    }
                } else {
                    (appMap[key]?.icon ?: DrawableFactory.createMonogramIconDrawable(item.title, themeColor)).also {
                        iconCache.put(cacheKey, it)
                    }
                }
            }
            LauncherItemType.FOLDER -> DrawableFactory.createFolderIconDrawable(themeColor).also {
                iconCache.put(cacheKey, it)
            }
            LauncherItemType.SHORTCUT -> DrawableFactory.createShortcutIconDrawable(themeColor).also {
                iconCache.put(cacheKey, it)
            }
        }
    }

    private fun handleItemClick(item: DisplayItem) {
        when (item.type) {
            LauncherItemType.APP -> launchApp(item.appKey ?: return)
            LauncherItemType.FOLDER -> openFolder(item.folderId ?: return)
            LauncherItemType.SHORTCUT -> launchShortcut(item.shortcutId ?: return)
        }
    }

    private fun handleItemLongClick(item: DisplayItem, area: LauncherArea) {
        when (item.type) {
            LauncherItemType.APP -> showAppActionDialog(item.appKey ?: return)
            LauncherItemType.FOLDER -> showFolderActionDialog(item.folderId ?: return)
            LauncherItemType.SHORTCUT -> showShortcutActionDialog(item.shortcutId ?: return, area)
        }
    }

    private fun handleItemFocused(item: DisplayItem) {
        ttsController.speak(item.title)
        focusController.onViewFocused(currentFocus ?: return)
    }

    private fun showAppActionDialog(appKey: String) {
        val app = appMap[appKey] ?: return
        val override = stateRepository.getAppOverride(appKey)
        val options = arrayOf(
            "重命名",
            "替换图标(本地128x128)",
            if (override.hidden) "取消隐藏" else "隐藏应用",
            "加入Dock",
            "从Dock移除",
            "创建文件夹(主页)",
            "创建文件夹(Dock)",
            "创建文件夹(抽屉)"
        )

        AlertDialog.Builder(this)
            .setTitle(override.customName ?: app.label)
            .setItems(options) { _, index ->
                when (index) {
                    0 -> renameAppDialog(appKey)
                    1 -> pickIconForApp(appKey)
                    2 -> {
                        stateRepository.updateAppOverride(appKey) { it.hidden = !it.hidden }
                        refreshAllLists()
                    }
                    3 -> {
                        val dock = stateRepository.getDockAppKeys()
                        if (!dock.contains(appKey)) {
                            dock.add(appKey)
                            stateRepository.saveDockAppKeys(dock)
                        }
                        refreshAllLists()
                    }
                    4 -> {
                        val dock = stateRepository.getDockAppKeys()
                        dock.remove(appKey)
                        stateRepository.saveDockAppKeys(dock)
                        refreshAllLists()
                    }
                    5 -> showCreateFolderDialog(LauncherArea.HOME, appKey)
                    6 -> showCreateFolderDialog(LauncherArea.DOCK, appKey)
                    7 -> showCreateFolderDialog(LauncherArea.DRAWER, appKey)
                }
            }
            .show()
    }

    private fun renameAppDialog(appKey: String) {
        val override = stateRepository.getAppOverride(appKey)
        val app = appMap[appKey] ?: return
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(override.customName ?: app.label)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("修改显示名称")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                stateRepository.updateAppOverride(appKey) {
                    it.customName = input.text.toString().trim()
                }
                refreshAllLists()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pickIconForApp(appKey: String) {
        pendingIconAppKey = appKey
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "选择图标图片"), REQUEST_ICON_PICK)
        } catch (_: Throwable) {
            Toast.makeText(this, "无法打开图片选择器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCreateFolderDialog(area: LauncherArea, initialAppKey: String? = null) {
        val input = EditText(this).apply {
            setSingleLine(true)
            setText("新建文件夹")
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("创建文件夹")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val title = input.text.toString().trim().ifEmpty { "文件夹" }
                val members = mutableListOf<String>()
                if (!initialAppKey.isNullOrEmpty()) {
                    members.add(initialAppKey)
                }
                stateRepository.createFolder(title, area, members)
                refreshAllLists()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showFolderActionDialog(folderId: String) {
        val folders = stateRepository.getFolders()
        val folder = folders.firstOrNull { it.id == folderId } ?: return
        val options = arrayOf("重命名", "管理成员", "删除文件夹")
        AlertDialog.Builder(this)
            .setTitle(folder.title)
            .setItems(options) { _, index ->
                when (index) {
                    0 -> {
                        val input = EditText(this).apply {
                            setSingleLine(true)
                            setText(folder.title)
                            setSelection(text.length)
                        }
                        AlertDialog.Builder(this)
                            .setTitle("重命名文件夹")
                            .setView(input)
                            .setPositiveButton("保存") { _, _ ->
                                folder.title = input.text.toString().trim().ifEmpty { folder.title }
                                stateRepository.saveFolders(folders)
                                refreshAllLists()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                    1 -> manageFolderMembers(folderId)
                    2 -> {
                        folders.remove(folder)
                        stateRepository.saveFolders(folders)
                        refreshAllLists()
                    }
                }
            }
            .show()
    }

    private fun manageFolderMembers(folderId: String) {
        val folders = stateRepository.getFolders()
        val folder = folders.firstOrNull { it.id == folderId } ?: return
        val appLabels = allApps.map { app ->
            val key = appKey(app)
            key to (stateRepository.getAppOverride(key).customName ?: app.label)
        }

        val labels = appLabels.map { it.second }.toTypedArray()
        val checked = appLabels.map { folder.memberAppKeys.contains(it.first) }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("成员管理")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val key = appLabels[which].first
                if (isChecked && !folder.memberAppKeys.contains(key)) {
                    folder.memberAppKeys.add(key)
                }
                if (!isChecked) {
                    folder.memberAppKeys.remove(key)
                }
            }
            .setPositiveButton("保存") { _, _ ->
                stateRepository.saveFolders(folders)
                refreshAllLists()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openFolder(folderId: String) {
        val folder = stateRepository.getFolders().firstOrNull { it.id == folderId } ?: return
        val members = folder.memberAppKeys.mapNotNull { key ->
            appMap[key]?.let { key to (stateRepository.getAppOverride(key).customName ?: it.label) }
        }
        if (members.isEmpty()) {
            Toast.makeText(this, "文件夹为空", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = members.map { it.second }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(folder.title)
            .setItems(labels) { _, which ->
                launchApp(members[which].first)
            }
            .show()
    }

    private fun showShortcutDialog(area: LauncherArea) {
        val actions = arrayOf("新增自定义快捷方式", "添加预设快捷方式")
        AlertDialog.Builder(this)
            .setTitle("快捷方式管理")
            .setItems(actions) { _, index ->
                when (index) {
                    0 -> createCustomShortcut(area)
                    1 -> createPresetShortcut(area)
                }
            }
            .show()
    }

    private fun createCustomShortcut(area: LauncherArea) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        }
        val titleInput = EditText(this).apply { hint = "显示名称" }
        val packageInput = EditText(this).apply { hint = "包名" }
        val activityInput = EditText(this).apply { hint = "Activity名" }
        container.addView(titleInput)
        container.addView(packageInput)
        container.addView(activityInput)

        AlertDialog.Builder(this)
            .setTitle("新增快捷方式")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val title = titleInput.text.toString().trim()
                val pkg = packageInput.text.toString().trim()
                val activityName = activityInput.text.toString().trim()
                if (!validateShortcut(pkg, activityName)) {
                    Toast.makeText(this, "包名或Activity不存在", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                stateRepository.createShortcut(
                    title = if (title.isEmpty()) "快捷方式" else title,
                    packageName = pkg,
                    activityName = activityName,
                    area = area
                )
                refreshAllLists()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createPresetShortcut(area: LauncherArea) {
        val presets = listOf(
            ShortcutEntry("preset_wifi_add", "WIFI", "com.android.settings", "com.android.settings.Settings\$WifiSettingsActivity", area),
            ShortcutEntry("preset_bt_add", "蓝牙", "com.android.settings", "com.android.settings.bluetooth.BluetoothSettings", area),
            ShortcutEntry("preset_display_add", "显示", "com.android.settings", "com.android.settings.DisplaySettings", area),
            ShortcutEntry("preset_settings_add", "设置", "com.android.settings", "com.android.settings.Settings", area)
        )
        val labels = presets.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("预设快捷方式")
            .setItems(labels) { _, which ->
                val selected = presets[which]
                if (!validateShortcut(selected.packageName, selected.activityName)) {
                    Toast.makeText(this, "当前设备不支持该页面", Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                stateRepository.createShortcut(
                    title = selected.title,
                    packageName = selected.packageName,
                    activityName = selected.activityName,
                    area = area
                )
                refreshAllLists()
            }
            .show()
    }

    private fun showShortcutActionDialog(shortcutId: String, area: LauncherArea) {
        val list = stateRepository.getShortcuts()
        val entry = list.firstOrNull { it.id == shortcutId } ?: return
        val options = arrayOf("重命名", "移到主页", "移到Dock", "移到抽屉", "移到顶部栏", "删除")
        AlertDialog.Builder(this)
            .setTitle(entry.title)
            .setItems(options) { _, index ->
                when (index) {
                    0 -> {
                        val input = EditText(this).apply {
                            setText(entry.title)
                            setSelection(text.length)
                        }
                        AlertDialog.Builder(this)
                            .setTitle("重命名")
                            .setView(input)
                            .setPositiveButton("保存") { _, _ ->
                                entry.title = input.text.toString().trim().ifEmpty { entry.title }
                                stateRepository.saveShortcuts(list)
                                refreshAllLists()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                    1 -> moveShortcutArea(entry, list, LauncherArea.HOME)
                    2 -> moveShortcutArea(entry, list, LauncherArea.DOCK)
                    3 -> moveShortcutArea(entry, list, LauncherArea.DRAWER)
                    4 -> moveShortcutArea(entry, list, LauncherArea.TOP_BAR)
                    5 -> {
                        list.remove(entry)
                        stateRepository.saveShortcuts(list)
                        refreshAllLists()
                    }
                }
            }
            .show()
    }

    private fun moveShortcutArea(entry: ShortcutEntry, all: MutableList<ShortcutEntry>, targetArea: LauncherArea) {
        entry.area = targetArea
        stateRepository.saveShortcuts(all)
        refreshAllLists()
    }

    private fun launchShortcut(shortcutId: String) {
        val entry = stateRepository.getShortcuts().firstOrNull { it.id == shortcutId } ?: return
        if (!validateShortcut(entry.packageName, entry.activityName)) {
            Toast.makeText(this, "目标页面不存在", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(entry.packageName, entry.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "无法跳转: ${entry.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateShortcut(packageName: String, activityName: String): Boolean {
        return try {
            packageManager.getActivityInfo(android.content.ComponentName(packageName, activityName), 0)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun launchApp(appKey: String) {
        val app = appMap[appKey] ?: return
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = app.componentName
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }

        try {
            startActivity(launchIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "无法启动: ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLayoutDialog() {
        val current = stateRepository.getLayoutConfig()
        val options = arrayOf("4×6", "5×6", "2×3", "自定义")
        AlertDialog.Builder(this)
            .setTitle("桌面布局")
            .setItems(options) { _, index ->
                when (index) {
                    0 -> stateRepository.saveLayoutConfig(4, 6)
                    1 -> stateRepository.saveLayoutConfig(5, 6)
                    2 -> stateRepository.saveLayoutConfig(2, 3)
                    3 -> {
                        val rowInput = EditText(this).apply {
                            setSingleLine(true)
                            setText(current.rows.toString())
                            hint = "行数"
                        }
                        val colInput = EditText(this).apply {
                            setSingleLine(true)
                            setText(current.columns.toString())
                            hint = "列数"
                        }
                        val container = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(rowInput)
                            addView(colInput)
                        }
                        AlertDialog.Builder(this)
                            .setTitle("自定义布局")
                            .setView(container)
                            .setPositiveButton("保存") { _, _ ->
                                val rows = rowInput.text.toString().toIntOrNull() ?: current.rows
                                val columns = colInput.text.toString().toIntOrNull() ?: current.columns
                                stateRepository.saveLayoutConfig(rows, columns)
                                updateGridSpans()
                                refreshAllLists()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                        return@setItems
                    }
                }
                updateGridSpans()
                refreshAllLists()
            }
            .show()
    }

    private fun showRotationDialog() {
        val options = arrayOf("跟随系统", "强制横屏", "强制竖屏")
        AlertDialog.Builder(this)
            .setTitle("屏幕方向")
            .setItems(options) { _, index ->
                stateRepository.saveRotationMode(index)
                applyRotationMode()
            }
            .show()
    }

    private fun applyRotationMode() {
        when (stateRepository.getRotationMode()) {
            1 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            2 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        updateGridSpans()
    }

    private fun showPerformanceDialog() {
        val modeOptions = arrayOf("低性能模式(全效果)", "高性能模式(极简)", "自定义")
        val current = PerformanceConfig.mode(this)
        AlertDialog.Builder(this)
            .setTitle("性能模式")
            .setSingleChoiceItems(modeOptions, current.ordinal) { dialog, which ->
                when (which) {
                    0 -> PerformanceConfig.setMode(this, PerformanceConfig.PerfMode.LOW)
                    1 -> PerformanceConfig.setMode(this, PerformanceConfig.PerfMode.HIGH)
                    2 -> {
                        PerformanceConfig.setMode(this, PerformanceConfig.PerfMode.CUSTOM)
                        showCustomPerformanceDialog()
                    }
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun showCustomPerformanceDialog() {
        val labels = mutableListOf("毛玻璃", "焦点缩放", "弹簧动画", "图标阴影")
        val checked = mutableListOf(
            PerformanceConfig.glassmorphism(this),
            PerformanceConfig.focusScale(this),
            PerformanceConfig.springAnimation(this),
            PerformanceConfig.iconShadow(this)
        )

        if (ttsController.isSupported()) {
            labels.add("TTS朗读")
            checked.add(PerformanceConfig.ttsEnabled(this))
        } else {
            PerformanceConfig.setTtsEnabled(this, false)
        }

        labels.add("壁纸切换动画")
        checked.add(PerformanceConfig.wallpaperAnimation(this))

        val labelArray = labels.toTypedArray()
        val checkedArray = checked.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("自定义性能项")
            .setMultiChoiceItems(labelArray, checkedArray) { _, which, isChecked ->
                when (labelArray[which]) {
                    "毛玻璃" -> PerformanceConfig.setGlassmorphism(this, isChecked)
                    "焦点缩放" -> PerformanceConfig.setFocusScale(this, isChecked)
                    "弹簧动画" -> PerformanceConfig.setSpringAnimation(this, isChecked)
                    "图标阴影" -> PerformanceConfig.setIconShadow(this, isChecked)
                    "TTS朗读" -> PerformanceConfig.setTtsEnabled(this, isChecked)
                    "壁纸切换动画" -> PerformanceConfig.setWallpaperAnimation(this, isChecked)
                }
            }
            .setPositiveButton("完成", null)
            .show()
    }

    private fun showThemeDialog() {
        val input = EditText(this).apply {
            hint = "例如 #FF7FA6FF"
            setSingleLine(true)
            setText(ThemeConfig.asArgbHex(themeColor))
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("输入主题色 ARGB")
            .setMessage("支持 6 位 RGB 或 8 位 ARGB")
            .setView(input)
            .setPositiveButton("应用") { _, _ ->
                val parsed = ThemeConfig.parseArgbHex(input.text.toString())
                if (parsed == null) {
                    Toast.makeText(this, "格式无效", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                ThemeConfig.setThemeColor(this, parsed)
                applyVisualState(false)
                refreshAllLists()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pickWallpaper() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "选择壁纸"), REQUEST_WALLPAPER_PICK)
        } catch (_: Throwable) {
            Toast.makeText(this, "无法打开壁纸选择器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyVisualState(init: Boolean) {
        themeColor = ThemeConfig.getThemeColor(this)
        root.background = DrawableFactory.createRootBackground(this, themeColor)
        drawerPanel.background = DrawableFactory.createRootBackground(this, themeColor)
        topShortcutBar.background = DrawableFactory.createCardDrawable(this, themeColor, false)
        drawerSearch.background = DrawableFactory.createActionButtonDrawable(this, themeColor, false)
        drawerHiddenToggle.background = DrawableFactory.createActionButtonDrawable(this, themeColor, drawerHiddenToggle.isFocused)
        iconCache.evictAll()

        if (init) {
            val wallpaperRaw = stateRepository.getWallpaperBase64()
            if (!wallpaperRaw.isNullOrEmpty()) {
                try {
                    val bytes = LauncherStateRepository.decodeBytes(wallpaperRaw)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    applyWallpaper(bitmap)
                } catch (_: Throwable) {
                    recycleWallpaper()
                    wallpaperLayer.background = null
                    wallpaperLayer.visibility = View.GONE
                }
            } else {
                recycleWallpaper()
                wallpaperLayer.background = null
                wallpaperLayer.visibility = View.GONE
            }
        }
    }

    private fun toggleDrawer(show: Boolean) {
        if (show == drawerVisible) return
        drawerVisible = show

        if (show) {
            drawerPanel.visibility = View.VISIBLE
            if (!PerformanceConfig.reduceAnimation(this)) {
                drawerPanel.animate().alpha(1f).setDuration(150L).start()
            } else {
                drawerPanel.alpha = 1f
            }
            drawerRecycler.requestFocus()
        } else {
            if (!PerformanceConfig.reduceAnimation(this)) {
                drawerPanel.animate().alpha(0f).setDuration(120L).withEndAction {
                    drawerPanel.visibility = View.GONE
                }.start()
            } else {
                drawerPanel.alpha = 0f
                drawerPanel.visibility = View.GONE
            }
            focusController.restoreLastFocus(homeRecycler)
        }
    }

    private fun updateDrawerToggleText() {
        drawerHiddenToggle.text = if (drawerShowHidden) "隐藏过滤:开" else "隐藏过滤:关"
    }

    private fun updateGridSpans() {
        val config = stateRepository.getLayoutConfig()
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val span = if (isLandscape) config.columns else config.rows
        (homeRecycler.layoutManager as? GridLayoutManager)?.spanCount = span.coerceAtLeast(1)
        (drawerRecycler.layoutManager as? GridLayoutManager)?.spanCount = span.coerceAtLeast(1)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateGridSpans()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (!drawerVisible && topShortcutBar.visibility != View.VISIBLE && homeRecycler.hasFocus()) {
                    topShortcutBar.visibility = View.VISIBLE
                    topShortcutRecycler.requestFocus()
                    return true
                }
            }
            KeyEvent.KEYCODE_BACK -> {
                if (drawerVisible) {
                    toggleDrawer(false)
                    return true
                }
                if (topShortcutBar.visibility == View.VISIBLE) {
                    topShortcutBar.visibility = View.GONE
                    focusController.restoreLastFocus(homeRecycler)
                    return true
                }
                moveTaskToBack(true)
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                showPerformanceDialog()
                return true
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                Process.killProcess(Process.myPid())
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_ICON_PICK -> handleIconPicked(uri)
            REQUEST_WALLPAPER_PICK -> handleWallpaperPicked(uri)
        }
    }

    private fun handleIconPicked(uri: Uri) {
        val appKey = pendingIconAppKey ?: return
        pendingIconAppKey = null
        activeCompressTask?.cancel(true)
        activeCompressTask = CompressTask(uri, isWallpaper = false, onDone = { base64 ->
            if (base64.isNullOrEmpty()) {
                Toast.makeText(this, "图标处理失败", Toast.LENGTH_SHORT).show()
                return@CompressTask
            }
            stateRepository.updateAppOverride(appKey) {
                it.customIconBase64 = base64
            }
            iconCache.evictAll()
            refreshAllLists()
        }).also { it.execute() }
    }

    private fun handleWallpaperPicked(uri: Uri) {
        activeCompressTask?.cancel(true)
        activeCompressTask = CompressTask(uri, isWallpaper = true, onDone = { base64 ->
            if (base64.isNullOrEmpty()) {
                Toast.makeText(this, "壁纸处理失败", Toast.LENGTH_SHORT).show()
                return@CompressTask
            }
            stateRepository.saveWallpaperBase64(base64)
            try {
                val bytes = LauncherStateRepository.decodeBytes(base64)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    applyWallpaper(bitmap)
                }
            } catch (_: Throwable) {
                Toast.makeText(this, "壁纸应用失败", Toast.LENGTH_SHORT).show()
            }
        }).also { it.execute() }
    }

    private fun applyWallpaper(bitmap: Bitmap) {
        recycleWallpaper()
        wallpaperBitmap = bitmap
        val drawable = BitmapDrawable(resources, bitmap)
        wallpaperLayer.visibility = View.VISIBLE
        if (PerformanceConfig.wallpaperAnimation(this)) {
            wallpaperLayer.alpha = 0f
            wallpaperLayer.background = drawable
            wallpaperLayer.animate().alpha(1f).setDuration(180L).start()
        } else {
            wallpaperLayer.alpha = 1f
            wallpaperLayer.background = drawable
        }
    }

    private fun recycleWallpaper() {
        val old = wallpaperBitmap
        wallpaperBitmap = null
        if (old != null && !old.isRecycled) {
            old.recycle()
        }
    }

    private inner class CompressTask(
        private val uri: Uri,
        private val isWallpaper: Boolean,
        private val onDone: (String?) -> Unit
    ) : AsyncTask<Unit, Unit, String?>() {

        override fun doInBackground(vararg params: Unit?): String? {
            val target = if (isWallpaper) 1280 else 128
            return ImageTools.compressUriToBase64(contentResolver, uri, target)
        }

        override fun onPostExecute(result: String?) {
            activeCompressTask = null
            onDone(result)
        }

        override fun onCancelled() {
            activeCompressTask = null
        }
    }

    private fun appKey(app: AppEntry): String {
        return "${app.componentName.packageName}/${app.componentName.className}"
    }

    private fun matchQuery(title: String, queryRaw: String): Boolean {
        val query = queryRaw.trim().toLowerCase(Locale.getDefault())
        if (query.isEmpty()) return true

        val label = title.toLowerCase(Locale.getDefault())
        if (label.contains(query)) return true

        val pinyin = Pinyin.toPinyin(title, "")
        if (pinyin.toLowerCase(Locale.getDefault()).contains(query)) return true

        val initials = StringBuilder()
        title.forEach { ch ->
            val py = Pinyin.toPinyin(ch)
            if (py.isNotEmpty()) {
                initials.append(py[0])
            }
        }
        return initials.toString().toLowerCase(Locale.getDefault()).contains(query)
    }

    private fun resolveHomeSpanCount(): Int {
        val config = stateRepository.getLayoutConfig()
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return if (isLandscape) config.columns.coerceAtLeast(1) else config.rows.coerceAtLeast(1)
    }

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
    }

    companion object {
        private const val REQUEST_ICON_PICK = 1001
        private const val REQUEST_WALLPAPER_PICK = 1002
        private val AppOverrideFallback = com.simplelauncher.model.AppOverride()
    }
}
