package com.simplelauncher.ui.focus

import android.view.View

class FocusController {
    private val regionFirstViews = mutableMapOf<String, View>()
    private var lastFocusedView: View? = null

    fun registerRegionFirstView(region: String, view: View) {
        regionFirstViews[region] = view
    }

    fun onViewFocused(view: View) {
        lastFocusedView = view
    }

    fun focusRegion(region: String): Boolean {
        val target = regionFirstViews[region] ?: return false
        return target.requestFocus()
    }

    fun restoreLastFocus(fallback: View?): Boolean {
        val target = lastFocusedView ?: fallback
        return target?.requestFocus() ?: false
    }
}
