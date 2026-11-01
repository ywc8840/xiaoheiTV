package com.xiaohei.tv.ui

import android.content.Context

/**
 * 打包进 APK 的高清台标（来自 fanmingming/live 台标库）。
 *
 * 为兼容老电视（尤其国产固件）的 APK 解析器，台标文件已改为纯 ASCII 文件名
 * （l001.png …），并用 assets/logos/map.txt 记录「频道名 -> 文件名」的映射，
 * 避免 APK 压缩包里出现非 ASCII 条目名导致「解析软件包时出现问题」。
 */
object LogoAssets {

    private var available: Set<String>? = null
    private var nameMap: Map<String, String>? = null

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

    /** 频道名 -> 台标文件名（ASCII）。键为原始频道名（如「湖南卫视」）。 */
    private fun nameMap(ctx: Context): Map<String, String> {
        nameMap?.let { return it }
        val map = mutableMapOf<String, String>()
        try {
            ctx.assets.open("logos/map.txt").bufferedReader().forEachLine { line ->
                val idx = line.indexOf('=')
                if (idx > 0) {
                    map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
                }
            }
        } catch (_: Exception) {
            // map.txt 缺失时退化为仅 CCTV/CETV 匹配
        }
        nameMap = map
        return map
    }

    fun logoPath(ctx: Context, channelName: String): String? {
        val file = fileNameFor(ctx, channelName) ?: return null
        return if (file in available(ctx)) "file:///android_asset/logos/$file" else null
    }

    private fun fileNameFor(ctx: Context, name: String): String? {
        val n = name.trim()
        Regex("""^CCTV-?(\d+)(\+)?$""").matchEntire(n)?.let {
            return "CCTV${it.groupValues[1]}${if (it.groupValues[2].isNotEmpty()) "+" else ""}.png"
        }
        Regex("""^CETV-?(\d+)$""").matchEntire(n)?.let {
            return "CETV${it.groupValues[1]}.png"
        }
        // 卫视等：查映射表得到 ASCII 文件名
        return nameMap(ctx)[n]
    }
}
