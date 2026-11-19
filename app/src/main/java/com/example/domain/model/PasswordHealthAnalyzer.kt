package com.example.domain.model

data class PasswordHealthReport(
    val totalLogins: Int,
    val strongItems: List<VaultItem>,
    val fairItems: List<VaultItem>,
    val weakItems: List<VaultItem>,
    val reusedItems: List<VaultItem>,
    val shortItems: List<VaultItem>,
    val overallScorePercent: Int,
) {
    val strongFraction: Float
        get() = if (totalLogins == 0) 1f else strongItems.size.toFloat() / totalLogins.toFloat()
    val fairFraction: Float
        get() = if (totalLogins == 0) 0f else fairItems.size.toFloat() / totalLogins.toFloat()
    val weakFraction: Float
        get() = if (totalLogins == 0) 0f else weakItems.size.toFloat() / totalLogins.toFloat()
}

/**
 * Pure offline local analyzer computing real Password Health metrics from unlocked login items.
 * Zero network calls, zero third-party breach APIs, zero plaintext logging.
 */
object PasswordHealthAnalyzer {

    fun analyze(items: List<VaultItem>): PasswordHealthReport {
        val logins = items.filter { it.type == VaultItemType.LOGIN }
        if (logins.isEmpty()) {
            return PasswordHealthReport(
                totalLogins = 0,
                strongItems = emptyList(),
                fairItems = emptyList(),
                weakItems = emptyList(),
                reusedItems = emptyList(),
                shortItems = emptyList(),
                overallScorePercent = 100,
            )
        }

        val strong = mutableListOf<VaultItem>()
        val fair = mutableListOf<VaultItem>()
        val weak = mutableListOf<VaultItem>()
        val short = mutableListOf<VaultItem>()

        val secretCounts = logins
            .filter { it.secret.isNotEmpty() }
            .groupingBy { it.secret }
            .eachCount()

        val reused = logins.filter { (secretCounts[it.secret] ?: 0) > 1 }

        for (item in logins) {
            if (item.secret.length < 12) {
                short.add(item)
            }
            val evaluation = PasswordEngine.evaluateMasterPassword(item.secret)
            when (evaluation.level) {
                StrengthLevel.WEAK -> weak.add(item)
                StrengthLevel.FAIR -> fair.add(item)
                StrengthLevel.STRONG, StrengthLevel.VERY_STRONG -> strong.add(item)
            }
        }

        val basePoints = (strong.size * 100 + fair.size * 65 + weak.size * 20).toDouble() / logins.size
        val reusePenalty = (reused.size * 12.0).coerceAtMost(35.0)
        val score = (basePoints - reusePenalty).toInt().coerceIn(0, 100)

        return PasswordHealthReport(
            totalLogins = logins.size,
            strongItems = strong,
            fairItems = fair,
            weakItems = weak,
            reusedItems = reused,
            shortItems = short,
            overallScorePercent = score,
        )
    }
}
