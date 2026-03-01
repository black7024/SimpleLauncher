package com.simplelauncher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import com.simplelauncher.model.DisplayItem
import com.simplelauncher.model.LauncherItemType
import com.simplelauncher.perf.PerformanceConfig
import com.simplelauncher.ui.draw.DrawableFactory
import com.simplelauncher.ui.theme.ThemeConfig

class AppListAdapter(
    private val context: Context,
    private val onResolveIcon: (DisplayItem) -> Drawable,
    private val onItemClick: (DisplayItem) -> Unit,
    private val onItemLongClick: (DisplayItem) -> Unit,
    private val onItemFocused: (DisplayItem) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    private val items = mutableListOf<DisplayItem>()
    private var themeColor: Int = ThemeConfig.getThemeColor(context)

    fun replaceItems(newItems: List<DisplayItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun swapItems(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return
        val src = items[from]
        items.removeAt(from)
        items.add(to, src)
        notifyItemMoved(from, to)
    }

    fun snapshotItems(): List<DisplayItem> = items.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val root = FrameLayout(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 160f)
            ).apply {
                val margin = dp(context, 7f)
                setMargins(margin, margin, margin, margin)
            }
            setPadding(dp(context, 10f), dp(context, 10f), dp(context, 10f), dp(context, 10f))
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(context, 10f), dp(context, 12f), dp(context, 10f), dp(context, 10f))
        }

        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(context, 72f), dp(context, 72f))
        }

        val label = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 10f)
            }
            gravity = Gravity.CENTER
            setSingleLine(true)
            setTextColor(Color.argb(236, 245, 245, 245))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }

        val typeHint = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            setSingleLine(true)
            setTextColor(Color.argb(150, 212, 212, 212))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }

        content.addView(icon)
        content.addView(label)
        content.addView(typeHint)
        root.addView(content)

        return AppViewHolder(root, icon, label, typeHint)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val item = items[position]
        holder.label.text = item.title
        holder.icon.setImageDrawable(onResolveIcon(item))
        holder.typeHint.text = when (item.type) {
            LauncherItemType.APP -> "应用"
            LauncherItemType.FOLDER -> "文件夹"
            LauncherItemType.SHORTCUT -> "快捷方式"
        }
        applyFocusState(holder.root, holder.root.isFocused)

        holder.root.setOnClickListener {
            onItemClick(item)
        }
        holder.root.setOnLongClickListener {
            onItemLongClick(item)
            true
        }
        holder.root.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
            applyFocusState(view, hasFocus)
            animateFocusScale(view, hasFocus)
            if (hasFocus) {
                onItemFocused(item)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateThemeColor(color: Int) {
        themeColor = color
        notifyDataSetChanged()
    }

    private fun applyFocusState(view: View, focused: Boolean) {
        view.background = DrawableFactory.createCardDrawable(context, themeColor, focused)
    }

    private fun animateFocusScale(view: View, focused: Boolean) {
        if (!PerformanceConfig.focusScale(context)) {
            view.animate().cancel()
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }

        val targetScale = if (focused) 1.1f else 1f
        if (PerformanceConfig.springAnimation(context)) {
            view.animate().cancel()
            SpringAnimation(view, DynamicAnimation.SCALE_X, targetScale).apply {
                spring = SpringForce(targetScale).apply {
                    dampingRatio = 0.72f
                    stiffness = 420f
                }
            }.start()
            SpringAnimation(view, DynamicAnimation.SCALE_Y, targetScale).apply {
                spring = SpringForce(targetScale).apply {
                    dampingRatio = 0.72f
                    stiffness = 420f
                }
            }.start()
        } else {
            view.animate().scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(150L)
                .setInterpolator(LinearInterpolator())
                .start()
        }
    }

    private fun dp(context: Context, value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics
        ).toInt()
    }

    class AppViewHolder(
        val root: View,
        val icon: ImageView,
        val label: TextView,
        val typeHint: TextView
    ) : RecyclerView.ViewHolder(root)
}
