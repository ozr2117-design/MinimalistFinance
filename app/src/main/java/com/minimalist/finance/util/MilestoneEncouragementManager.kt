package com.minimalist.finance.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.minimalist.finance.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object MilestoneEncouragementManager {
    private const val PREF_NAME = "shubu_milestone_prefs"
    private const val KEY_ENABLED = "milestone_encouragement_enabled"
    private const val KEY_LAST_CELEBRATED = "last_celebrated_milestone_count"

    fun isMilestoneEnabled(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_ENABLED, true) // 默认开启
    }

    fun setMilestoneEnabled(context: Context, enabled: Boolean) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private fun getLastCelebrated(context: Context): Int {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sp.getInt(KEY_LAST_CELEBRATED, 0)
    }

    private fun setLastCelebrated(context: Context, count: Int) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit().putInt(KEY_LAST_CELEBRATED, count).apply()
    }

    /**
     * 风格 A (掌控底气型) 里程碑正向激励提示语库
     */
    fun getMilestoneMessage(count: Int): String? {
        return when (count) {
            5 -> "🌱 累计记录 5 笔！最难的开始你已经做到了，财务掌控始于清晰。"
            10 -> "🎯 累计记录 10 笔！每一笔心里有数，日子就更有底气 ✨"
            20 -> "📊 累计记录 20 笔！清晰是最好的省钱良药，自律正在发生 👏"
            30 -> "💼 累计记录 30 笔！掌控消费节奏，给未来的自己攒一份安全感 🎯"
            50 -> "⭐ 累计记录 50 笔！钱花去哪儿了不再是谜，生活井井有条 🌟"
            100 -> "🏆 累计记录 100 笔！不知不觉破百笔，财务掌控大师就是你 👑"
            else -> {
                if (count > 0 && count % 10 == 0) {
                    val messages = listOf(
                        "🌱 累计记录 $count 笔！每一笔心里有数，日子就更有底气 ✨",
                        "🎯 累计记录 $count 笔！清晰是最好的省钱良药，自律正在发生 👏",
                        "💼 累计记录 $count 笔！掌控消费节奏，给未来的自己攒一份安全感 🎯",
                        "⭐ 累计记录 $count 笔！钱花去哪儿了不再是谜，继续保持好习惯 🌟"
                    )
                    messages[(count / 10) % messages.size]
                } else null
            }
        }
    }

    /**
     * 检查并触发正向鼓励
     */
    fun checkAndCelebrate(context: Context, scope: CoroutineScope) {
        if (!isMilestoneEnabled(context)) return

        scope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context, scope)
                val totalCount = db.recordDao().getTotalRecordCount()
                val lastCelebrated = getLastCelebrated(context)

                val message = getMilestoneMessage(totalCount)
                if (message != null && totalCount != lastCelebrated) {
                    setLastCelebrated(context, totalCount)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
