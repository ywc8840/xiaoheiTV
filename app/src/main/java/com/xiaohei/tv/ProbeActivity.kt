package com.xiaohei.tv

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * 隐藏的播放源自检页（无桌面图标，仅用于开发期批量验证线路是否真能出画）。
 *
 * 启动方式：
 *   adb shell am start -n com.xiaohei.tv/.ProbeActivity
 *
 * 读取 assets/probe.m3u（标准 m3u 格式），逐条用 ExoPlayer 试播；
 * 以「是否真的渲染出第一帧」为唯一判定标准，结果写入
 *   /sdcard/Android/data/com.xiaohei.tv/files/probe_result.txt
 */
@OptIn(UnstableApi::class)
class ProbeActivity : ComponentActivity() {

    private companion object {
        const val TAG = "XiaoHeiProbe"
        const val TIMEOUT_MS = 14_000L   // 单条最长等待
        const val SETTLE_MS = 1_200L     // 切下一条前的间隔
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var queue: List<Pair<String, String>> = emptyList()
    private var index = 0
    private var out: File? = null
    private val results = StringBuilder()

    private var recorded = false

    private lateinit var status: TextView

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this)
        val pv = PlayerView(this).apply { useController = false }
        root.addView(
            pv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        status = TextView(this).apply {
            setTextColor(0xFFFFEB3B.toInt())
            textSize = 11f
            setBackgroundColor(0x99000000.toInt())
            setPadding(16, 8, 16, 8)
        }
        root.addView(
            status,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.TOP
            )
        )
        setContentView(root)

        queue = loadQueue()
        out = File(getExternalFilesDir(null) ?: filesDir, "probe_result.txt").also {
            it.writeText("")
        }
        Log.i(TAG, "probe start, items=${queue.size}, out=${out?.absolutePath}")
        if (queue.isEmpty()) {
            finish()
            return
        }

        player = ExoPlayer.Builder(this).build().also {
            pv.player = it
            it.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    mark(true, "rendered")
                }

                override fun onPlayerError(error: PlaybackException) {
                    val cause = error.cause?.javaClass?.simpleName
                    mark(false, "${error.errorCodeName}/${cause ?: "-"}")
                }
            })
        }
        playNext()
    }

    /** 读取 assets/probe.m3u：#EXTINF 行取频道名，下一行取 URL */
    private fun loadQueue(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        runCatching {
            assets.open("probe.m3u").bufferedReader().useLines { lines ->
                var pending: String? = null
                lines.forEach { raw ->
                    val line = raw.trim()
                    when {
                        line.startsWith("#EXTINF") ->
                            pending = line.substringAfterLast(',', "").trim()
                        line.startsWith("http") -> {
                            list += (pending ?: line) to line
                            pending = null
                        }
                    }
                }
            }
        }.onFailure { Log.e(TAG, "read probe.m3u failed: ${it.message}") }
        return list
    }

    private fun playNext() {
        val p = player ?: return
        if (index >= queue.size) {
            finishProbe()
            return
        }
        val (name, url) = queue[index]
        recorded = false
        status.text = "[${index + 1}/${queue.size}] $name\n$url"
        handler.postDelayed({ mark(false, "timeout") }, TIMEOUT_MS)
        runCatching {
            p.setMediaItem(MediaItem.fromUri(url))
            p.prepare()
            p.playWhenReady = true
        }.onFailure { mark(false, "prepareFail:${it.javaClass.simpleName}") }
    }

    private fun mark(ok: Boolean, how: String) {
        if (recorded) return
        recorded = true
        handler.removeCallbacksAndMessages(null)
        val (name, url) = queue[index]
        val line = "${if (ok) "OK" else "FAIL"}|$name|$how|$url"
        results.appendLine(line)
        Log.i(TAG, line)
        out?.appendText(line + "\n")
        index++
        handler.postDelayed({ playNext() }, SETTLE_MS)
    }

    private fun finishProbe() {
        val okCount = results.lines().count { it.startsWith("OK|") }
        val tail = "SUMMARY|ok=$okCount|fail=${queue.size - okCount}|total=${queue.size}"
        Log.i(TAG, tail)
        out?.appendText(tail + "\n")
        status.text = "自检完成：$okCount / ${queue.size} 可播\n结果已写入 probe_result.txt"
        handler.postDelayed({ finish() }, 2500)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDestroy()
    }
}
