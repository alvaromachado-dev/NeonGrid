package dev.alvaro.neongrid

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlin.math.min

/**
 * Busca "esperta": aceita começo do nome, começo de qualquer palavra, iniciais
 * ("gps" → Google Play Store), letras salteadas ("wpp" → WhatsApp) e um errinho
 * de digitação. Os apps que você mais abre sobem no ranking.
 */
object SmartSearch {

    fun search(apps: List<AppEntry>, rawQuery: String, usage: Map<String, Int>): List<AppEntry> {
        val q = rawQuery.normalized().trim()
        if (q.isEmpty()) return apps
        return apps
            .map { it to score(it, q) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<AppEntry, Int>> { it.second + boost(usage[it.first.key] ?: 0) }
                    .thenBy { it.first.searchKey }
            )
            .map { it.first }
    }

    private fun boost(count: Int) = min(count * 4, 80)

    private fun score(app: AppEntry, q: String): Int {
        val name = app.searchKey
        val words = name.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        val qc = q.replace(" ", "")
        val compact = name.filter { it.isLetterOrDigit() }

        if (name.startsWith(q)) return 1000
        if (words.any { it.startsWith(q) }) return 900
        if (qc.length >= 2 && words.size >= 2 && words.joinToString("") { it.take(1) }.startsWith(qc)) return 850
        if (name.contains(q)) return 700
        subsequenceSpread(qc, compact)?.let { spread -> return (500 - spread * 10).coerceAtLeast(320) }
        if (qc.length >= 3) {
            val maxErrors = if (qc.length >= 6) 2 else 1
            val candidates = words + compact
            if (candidates.any { levenshtein(qc, it.take(qc.length)) <= maxErrors }) return 300
        }
        return 0
    }

    /** Se as letras de [q] aparecem em ordem dentro de [text], retorna o "espalhamento"; senão null. */
    private fun subsequenceSpread(q: String, text: String): Int? {
        if (q.length < 2) return null
        var i = 0
        var first = -1
        var last = -1
        for ((pos, ch) in text.withIndex()) {
            if (i < q.length && ch == q[i]) {
                if (first < 0) first = pos
                last = pos
                i++
            }
        }
        if (i < q.length || first != 0) return null // precisa começar pela 1ª letra do nome
        return (last - first + 1) - q.length
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    /** Abre a busca na web com o texto digitado. */
    fun webSearch(context: Context, query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_WEB_SEARCH)
                    .putExtra(SearchManager.QUERY, q)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
