# SimpleLauncher

纯 Kotlin 安卓桌面启动器（Launcher）框架，兼容 Android 4.1（minSdk 16）并面向 TV 低配设备。

## 已实现模块

- 应用扫描：获取包名 / Activity / 名称 / 原始图标
- 自定义编辑：单应用改名、替换本地图标并压缩到 128×128
- 应用操作：拖拽排序、手动排序、拼音/首字母搜索、隐藏应用与抽屉过滤
- 文件夹：支持在主页 / Dock / 抽屉创建、重命名、成员移入移出
- 快捷方式：包名+Activity 跳转校验，内置 WIFI/蓝牙/显示/设置预设
- 布局显示：主页网格 + Dock 单行 + 全屏抽屉（淡入淡出）
- 布局自定义：4×6 / 5×6 / 2×3 / 自定义行列
- 壁纸管理：本地壁纸异步压缩与切换动画（可关闭）
- 方向切换：跟随系统 / 强制横屏 / 强制竖屏，`configChanges` 实时适配
- 内置组件：时间、日期、天气（Open-Meteo 免费 API）自绘 View
- 交互控制：遥控方向键、顶部快捷栏（上键呼出）、全局 FocusController
- TTS：仅系统引擎，焦点朗读，切换焦点自动中断上一次朗读
- 性能模式：低性能（全效果）/ 高性能（极简）/ 自定义，SharedPreferences 永久存储

## 代码结构

- `app/src/main/kotlin/com/simplelauncher/ui/LauncherActivity.kt`
- `app/src/main/kotlin/com/simplelauncher/ui/AppListAdapter.kt`
- `app/src/main/kotlin/com/simplelauncher/data/LauncherStateRepository.kt`
- `app/src/main/kotlin/com/simplelauncher/perf/PerformanceConfig.kt`
- `app/src/main/kotlin/com/simplelauncher/ui/widget/`
- `app/src/main/kotlin/com/simplelauncher/ui/tts/TtsController.kt`
- `app/src/main/kotlin/com/simplelauncher/ui/focus/FocusController.kt`

## 依赖

- AndroidX RecyclerView / DynamicAnimation
- TinyPinyin（本地拼音搜索）
