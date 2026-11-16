package com.example.data.remote

import com.example.data.model.FinanceGoal
import com.example.data.model.FinanceScope
import java.text.Normalizer
import java.util.Locale

object GoalMatcher {

    private val STOPWORDS = setOf(
        "meta", "metas", "reserva", "objetivo", "para", "pra", "pro", "na", "no",
        "da", "do", "das", "dos", "de", "em", "meu", "minha", "a", "o", "as", "os"
    )

    fun findBestMatch(
        queryTitle: String,
        goals: List<FinanceGoal>,
        preferredScope: FinanceScope,
    ): FinanceGoal? {
        if (goals.isEmpty()) return null
        val cleanedQuery = cleanGoalTokens(queryTitle)
        if (cleanedQuery.isEmpty()) return null
        val joinedQuery = cleanedQuery.joinToString(" ")

        var bestGoal: FinanceGoal? = null
        var bestScore = 0

        for (goal in goals) {
            val goalNorm = normalize(goal.title)
            val goalTokens = cleanGoalTokens(goal.title)
            if (goalNorm.isBlank()) continue

            var score = 0
            when {
                goalNorm == joinedQuery -> score += 100
                goalNorm.contains(joinedQuery) || joinedQuery.contains(goalNorm) -> score += 70
                else -> {
                    val exactOverlap = cleanedQuery.count { q -> goalTokens.any { g -> g == q } }
                    val partialOverlap = cleanedQuery.count { q ->
                        q.length >= 3 && goalTokens.any { g -> g.contains(q) || q.contains(g) }
                    }
                    score += exactOverlap * 25 + partialOverlap * 12
                }
            }

            if (score > 0 && goal.scope == preferredScope.wireValue) {
                score += 5
            }

            if (score > bestScore) {
                bestScore = score
                bestGoal = goal
            }
        }

        return if (bestScore > 0) bestGoal else null
    }

    private fun cleanGoalTokens(input: String): List<String> =
        normalize(input)
            .split(Regex("""[\s,.;:!?()/\-]+"""))
            .filter { it.isNotBlank() && it !in STOPWORDS }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
            .trim()
}
