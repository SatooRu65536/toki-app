package dev.satooru.toki.alarm

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator

/** バイブレーションのパターン定義と再生 */
object Vibration {
    class Pattern(val name: String, val timings: LongArray)

    /** 配列の位置が Alarm.vibrationPattern の値。並びは変更しないこと */
    val patterns = listOf(
        Pattern("標準", longArrayOf(0, 800, 800)),
        Pattern("短く連続", longArrayOf(0, 200, 200)),
        Pattern("長く", longArrayOf(0, 1500, 500)),
        Pattern("ハートビート", longArrayOf(0, 120, 120, 120, 800)),
        Pattern("トントントン", longArrayOf(0, 300, 150, 300, 150, 300, 900)),
        Pattern("SOS", longArrayOf(0, 200, 200, 200, 200, 200, 600, 600, 200, 600, 200, 600, 600, 200, 200, 200, 200, 200, 1400)),
    )

    fun patternName(index: Int) = patterns.getOrElse(index) { patterns[0] }.name

    /** loop=true で停止まで繰り返す(鳴動用)、false で1回だけ(試し振動用) */
    fun start(context: Context, patternIndex: Int, loop: Boolean) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        val pattern = patterns.getOrElse(patternIndex) { patterns[0] }
        val effect = VibrationEffect.createWaveform(pattern.timings, if (loop) 0 else -1)
        // 用途を「アラーム」と明示しないと、バックグラウンドからの振動はOSに無視される
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
        }
    }

    fun stop(context: Context) {
        context.getSystemService(Vibrator::class.java)?.cancel()
    }
}
