package com.xiaohei.tv.data

import com.xiaohei.tv.model.Channel

object M3uParser {

    fun parse(text: String): List<Channel> {
        val lines = text.lines()
        val map = LinkedHashMap<String, Channel>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF")) {
                val name = line.substringAfterLast(",").trim().ifEmpty { "未知频道" }
                val logo = Regex("""tvg-logo="([^"]*)"""").find(line)?.groupValues?.get(1) ?: ""
                val group = Regex("""group-title="([^"]*)"""").find(line)?.groupValues?.get(1)?.ifBlank { null } ?: "其他"
                val urls = mutableListOf<String>()
                var j = i + 1
                while (j < lines.size) {
                    val u = lines[j].trim()
                    if (u.startsWith("#")) break
                    if (u.isNotEmpty() && (u.startsWith("http://") || u.startsWith("https://"))) urls.add(u)
                    j++
                }
                if (urls.isNotEmpty()) {
                    val existing = map[name]
                    if (existing == null) {
                        map[name] = Channel(name, group, logo, urls.distinct())
                    } else {
                        map[name] = existing.copy(urls = (existing.urls + urls).distinct())
                    }
                }
                i = j
            } else {
                i++
            }
        }
        return map.values.toList()
    }
}
