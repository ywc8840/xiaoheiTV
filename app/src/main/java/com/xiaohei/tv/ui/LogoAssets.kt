package com.xiaohei.tv.ui

import android.content.Context

/**
 * 打包进 APK 的高清台标（来自 fanmingming/live 台标库）。
 * 按频道名推导文件名，命中 assets/logos/ 则返回 android_asset URI，否则返回 null（回退 M3U 里的 tvg-logo）。
 */
object LogoAssets {

    private var available: Set<String>? = null

    private fun available(ctx: Context): Set<String> {
        available?.let { return it }
        val set = try {
            ctx.assets.list("logos")?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
        available = set
        return set
    }

    fun logoPath(ctx: Context, channelName: String): String? {
        val file = fileNameFor(channelName) ?: return null
        return if (file in available(ctx)) "file:///android_asset/logos/$file" else null
    }

    private fun fileNameFor(name: String): String? {
        val n = name.trim()
        Regex("""^CCTV-?(\d+)(\+)?$""").matchEntire(n)?.let {
            return "CCTV${it.groupValues[1]}${if (it.groupValues[2].isNotEmpty()) "+" else ""}.png"
        }
        Regex("""^CETV-?(\d+)$""").matchEntire(n)?.let {
            return "CETV${it.groupValues[1]}.png"
        }
        // 卫视等直接同名（找不到时由 available 校验）
        return "$n.png"
    }
}
