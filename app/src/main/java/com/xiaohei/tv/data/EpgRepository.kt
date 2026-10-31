package com.xiaohei.tv.data

import android.content.Context
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.TimeZone

/** 某频道的一条节目（当前正在播 / 下一档） */
data class ProgramSlot(val title: String, val start: Long, val stop: Long)

/** 某频道的 EPG 结果 */
data class EpgEntry(val current: ProgramSlot?, val next: ProgramSlot?)

/**
 * 节目单（XMLTV）仓库：
 *  - 多源回退：fanmingming(.com/.cn) → 112114 → 51zmt，取第一个能下到的
 *  - 流式解析，只保留每个频道「正在播 + 下一档」，内存占用极小
 *  - 同时用 channel id 和 display-name 两套键做匹配，兼容不同 EPG 源的命名
 *  - 本地缓存 6 小时
 */
object EpgRepository {

    private const val TAG = "XiaoHeiEpg"
    private const val CACHE_TTL_MS = 6L * 60 * 60 * 1000
    private val SOURCES = listOf(
        "http://epg.51zmt.top:8000/e.xml",
        "https://epg.112114.xyz/pp.xml",
        "https://live.fanmingming.com/e.xml",
        "https://live.fanmingming.cn/e.xml"
    )

    fun normalize(name: String): String =
        name.trim().replace("-", "").replace(" ", "").replace("\u3000", "").uppercase()

    private fun epgFile(ctx: Context) = File(ctx.filesDir, "epg.xml")

    fun ensureXml(ctx: Context): File? {
        val f = epgFile(ctx)
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < CACHE_TTL_MS
        if (fresh) {
            Log.i(TAG, "use cached epg.xml (${f.length()} bytes)")
            return f
        }
        for (src in SOURCES) {
            var current = src
            var hop = 0
            while (hop < 6) {
                try {
                    val conn = URL(current).openConnection() as HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 30000
                    conn.instanceFollowRedirects = false // 手动跟随，兼容 http->https 跳转
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) XiaoHeiTV")
                    conn.connect()
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val loc = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (loc.isNullOrEmpty()) {
                            Log.w(TAG, "$current redirect without Location")
                            break
                        }
                        current = URL(URL(current), loc).toString()
                        hop++
                        continue
                    }
                    if (code == HttpURLConnection.HTTP_OK) {
                        val tmp = File(f.parentFile, "epg.xml.tmp")
                        conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                        conn.disconnect()
                        if (tmp.length() > 300_000) {
                            tmp.renameTo(f)
                            Log.i(TAG, "downloaded $src (final=$current) size=${f.length()}")
                            return f
                        } else {
                            Log.w(TAG, "$src too small size=${tmp.length()}")
                            tmp.delete()
                        }
                    } else {
                        Log.w(TAG, "$current http=$code")
                        conn.disconnect()
                    }
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "$current fail: ${e.javaClass.simpleName} ${e.message}")
                    break
                }
            }
        }
        return if (f.exists()) f else null
    }

    fun load(ctx: Context, now: Long = System.currentTimeMillis()): Map<String, EpgEntry> {
        val f = ensureXml(ctx) ?: run {
            Log.w(TAG, "no epg xml available")
            return emptyMap()
        }
        return try {
            parse(f, now)
        } catch (e: Exception) {
            Log.w(TAG, "parse fail: ${e.javaClass.simpleName} ${e.message}")
            emptyMap()
        }
    }

    private fun parse(f: File, now: Long): Map<String, EpgEntry> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        try {
            parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false)
        } catch (_: Exception) {
        }

        val cur = HashMap<String, ProgramSlot>()
        val nxt = HashMap<String, ProgramSlot>()
        val idToName = HashMap<String, String>()
        val keyCache = HashMap<String, List<String>>()

        BufferedInputStream(FileInputStream(f)).use { raw ->
            raw.mark(3)
            val b0 = raw.read(); val b1 = raw.read(); val b2 = raw.read()
            if (!(b0 == 0xEF && b1 == 0xBB && b2 == 0xBF)) raw.reset()
            parser.setInput(raw, "UTF-8")

            var event = parser.eventType
            var inChannel = false
            var inDisplayName = false
            var curId: String? = null
            var chRawId: String? = null
            var chKeys: List<String> = emptyList()
            var start = 0L
            var stop = 0L
            var inProgramme = false
            var inTitle = false

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "channel" -> {
                            inChannel = true
                            curId = parser.getAttributeValue(null, "id")
                        }
                        "display-name" -> if (inChannel) inDisplayName = true
                        "programme" -> {
                            val id = parser.getAttributeValue(null, "channel").orEmpty()
                            if (id.isNotEmpty()) {
                                chRawId = id
                                chKeys = keyCache.getOrPut(id) {
                                    val list = ArrayList<String>(2)
                                    list.add(normalize(id))
                                    idToName[id]?.let { dn ->
                                        val n = normalize(dn)
                                        if (n != list[0]) list.add(n)
                                    }
                                    list
                                }
                                start = parseTime(parser.getAttributeValue(null, "start"))
                                stop = parseTime(parser.getAttributeValue(null, "stop"))
                                inProgramme = stop > start
                            } else {
                                inProgramme = false
                            }
                        }
                        "title" -> if (inProgramme) inTitle = true
                    }

                    XmlPullParser.TEXT -> {
                        if (inDisplayName && inChannel && curId != null && !inProgramme) {
                            val dn = parser.text?.trim().orEmpty()
                            if (dn.isNotEmpty()) idToName[curId!!] = dn
                        } else if (inTitle && inProgramme) {
                            val title = parser.text?.trim().orEmpty()
                            if (title.isNotEmpty()) {
                                val slot = ProgramSlot(title, start, stop)
                                if (start <= now && now < stop) {
                                    for (k in chKeys) {
                                        val prev = cur[k]
                                        if (prev == null || start > prev.start) cur[k] = slot
                                    }
                                } else if (start > now) {
                                    for (k in chKeys) {
                                        val prev = nxt[k]
                                        if (prev == null || start < prev.start) nxt[k] = slot
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> when (parser.name) {
                        "display-name" -> inDisplayName = false
                        "channel" -> {
                            inChannel = false
                            inDisplayName = false
                            curId = null
                        }
                        "title" -> inTitle = false
                        "programme" -> {
                            inProgramme = false
                            inTitle = false
                            chRawId = null
                        }
                    }
                }
                event = parser.next()
            }
        }

        val out = HashMap<String, EpgEntry>(cur.size + nxt.size)
        for (k in cur.keys + nxt.keys) out[k] = EpgEntry(cur[k], nxt[k])
        Log.i(TAG, "parsed slots=${out.size} (channels with epg)")
        return out
    }

    private fun parseTime(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        val day = s.trim().substringBefore(' ')
        if (day.length < 14) return 0L
        return try {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
            cal.clear()
            cal.set(
                day.substring(0, 4).toInt(),
                day.substring(4, 6).toInt() - 1,
                day.substring(6, 8).toInt(),
                day.substring(8, 10).toInt(),
                day.substring(10, 12).toInt(),
                day.substring(12, 14).toInt()
            )
            cal.timeInMillis
        } catch (_: Exception) {
            0L
        }
    }
}
