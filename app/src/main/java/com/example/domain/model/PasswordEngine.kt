package com.example.domain.model

import java.security.SecureRandom
import kotlin.math.log2

/**
 * Pure domain utility for password/passphrase generation, honest entropy estimation,
 * and URL/domain normalization.
 *
 * Phase 1 note: Uses java.security.SecureRandom for prototype generation, and calculates
 * entropy directly from the active character pool or wordlist size without inflated claims.
 */
object PasswordEngine {

    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val NUMBERS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?"
    private val AMBIGUOUS_CHARS = setOf('I', 'l', '1', 'O', '0', '|')

    // Curated 128-word readable prototype wordlist (7.0 bits per word exact).
    // Full EFF 7,776-word list (12.92 bits/word) will ship in Phase 3.
    internal val PASSPHRASE_WORDS = listOf(
        "acorn", "alder", "amber", "anchor", "anise", "apple", "arbor", "aspen",
        "atlas", "badger", "basalt", "beacon", "birch", "bison", "bloom", "boulder",
        "branch", "breeze", "bridge", "brook", "cairn", "canopy", "canyon", "cedar",
        "chalk", "cinder", "citron", "cliff", "clover", "cobalt", "compass", "copper",
        "coral", "crane", "creek", "crest", "cypress", "dawn", "delta", "dewdrop",
        "drift", "dune", "echo", "elm", "ember", "emerald", "falcon", "fern",
        "field", "finch", "fjord", "flint", "forest", "fossil", "frost", "garden",
        "garnet", "glacier", "glade", "granite", "grove", "harbor", "hazel", "hearth",
        "heather", "heron", "hollow", "horizon", "indigo", "island", "ivory", "jasper",
        "juniper", "kelp", "kestrel", "kindle", "lagoon", "lantern", "larch", "laurel",
        "lichen", "linden", "lotus", "maple", "marble", "marsh", "meadow", "mesa",
        "mica", "mist", "moss", "myrtle", "nebula", "nectar", "oak", "obsidian",
        "ochre", "olive", "onyx", "orchard", "osprey", "otter", "pebble", "pelican",
        "pine", "plover", "poplar", "prairie", "quartz", "raven", "redwood", "reef",
        "ridge", "river", "rowan", "saffron", "sage", "sequoia", "slate", "solstice",
        "spruce", "summit", "thistle", "timber", "topaz", "valley", "willow", "zephyr",
    )

    private val secureRandom = SecureRandom()

    fun generate(
        config: GeneratorConfig,
        nextInt: (Int) -> Int = { bound -> secureRandom.nextInt(bound) },
    ): String {
        return when (config.mode) {
            GeneratorMode.PASSWORD -> generateRandomPassword(config, nextInt)
            GeneratorMode.PASSPHRASE -> generatePassphrase(config, nextInt)
        }
    }

    private fun generateRandomPassword(
        config: GeneratorConfig,
        nextInt: (Int) -> Int,
    ): String {
        val activeSets = buildActiveCharSets(config)
        val combinedPool = activeSets.joinToString("")
        if (combinedPool.isEmpty()) return ""

        val targetLength = config.length.coerceIn(8, 64)
        val chars = CharArray(targetLength)

        // Guarantee at least one character from each enabled set when length permits.
        val guaranteedCount = activeSets.size.coerceAtMost(targetLength)
        for (i in 0 until guaranteedCount) {
            val set = activeSets[i]
            chars[i] = set[nextInt(set.length)]
        }
        for (i in guaranteedCount until targetLength) {
            chars[i] = combinedPool[nextInt(combinedPool.length)]
        }

        // Fisher-Yates shuffle so guaranteed characters are uniformly distributed.
        for (i in chars.lastIndex downTo 1) {
            val j = nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }

        return String(chars)
    }

    private fun generatePassphrase(
        config: GeneratorConfig,
        nextInt: (Int) -> Int,
    ): String {
        val count = config.wordCount.coerceIn(3, 8)
        val words = MutableList(count) {
            val raw = PASSPHRASE_WORDS[nextInt(PASSPHRASE_WORDS.size)]
            if (config.capitalizeWords) {
                raw.replaceFirstChar { it.uppercaseChar() }
            } else {
                raw
            }
        }
        if (config.includePassphraseNumber && words.isNotEmpty()) {
            val digitIndex = nextInt(words.size)
            val digit = nextInt(90) + 10 // two-digit suffix 10..99
            words[digitIndex] = "${words[digitIndex]}$digit"
        }
        return words.joinToString(config.wordSeparator)
    }

    fun calculateGeneratorStrength(config: GeneratorConfig): PasswordStrength {
        return when (config.mode) {
            GeneratorMode.PASSWORD -> {
                val poolSize = buildActiveCharSets(config).sumOf { it.length }
                val bits = if (poolSize > 1) config.length * log2(poolSize.toDouble()) else 0.0
                val level = strengthLevelForEntropy(bits)
                PasswordStrength(
                    level = level,
                    entropyBits = bits,
                    explanation = "Calculated from ${config.length} chars × log₂($poolSize) uniform pool size.",
                    isExactFormula = true,
                )
            }

            GeneratorMode.PASSPHRASE -> {
                val wordBits = config.wordCount * log2(PASSPHRASE_WORDS.size.toDouble())
                val numberBits = if (config.includePassphraseNumber) log2(90.0 * config.wordCount) else 0.0
                val totalBits = wordBits + numberBits
                val level = strengthLevelForEntropy(totalBits)
                PasswordStrength(
                    level = level,
                    entropyBits = totalBits,
                    explanation = "Prototype ${PASSPHRASE_WORDS.size}-word list (${config.wordCount} words). Full 7,776-word list ships in Phase 3.",
                    isExactFormula = true,
                )
            }
        }
    }

    /**
     * Heuristic strength estimate for user-entered master passwords.
     * Deliberately avoids arbitrary composition rules and rewards length and variety.
     */
    fun evaluateMasterPassword(password: String): PasswordStrength {
        if (password.isEmpty()) {
            return PasswordStrength(
                level = StrengthLevel.WEAK,
                entropyBits = 0.0,
                explanation = "Choose a long, memorable passphrase (12+ characters or 4+ unique words).",
                isExactFormula = false,
            )
        }
        var poolEstimate = 0
        if (password.any { it.isLowerCase() }) poolEstimate += 26
        if (password.any { it.isUpperCase() }) poolEstimate += 26
        if (password.any { it.isDigit() }) poolEstimate += 10
        if (password.any { !it.isLetterOrDigit() }) poolEstimate += 32
        val uniqueChars = password.toSet().size
        val repetitionFactor = (uniqueChars.toDouble() / password.length.toDouble()).coerceIn(0.35, 1.0)
        val estimatedBits = if (poolEstimate > 1) {
            password.length * log2(poolEstimate.toDouble()) * repetitionFactor
        } else {
            0.0
        }

        val level = when {
            password.length < 8 || estimatedBits < 36.0 -> StrengthLevel.WEAK
            password.length < 12 || estimatedBits < 56.0 -> StrengthLevel.FAIR
            estimatedBits < 80.0 -> StrengthLevel.STRONG
            else -> StrengthLevel.VERY_STRONG
        }
        val guidance = when (level) {
            StrengthLevel.WEAK -> "Estimate only: add length or extra unrelated words."
            StrengthLevel.FAIR -> "Estimate only: lengthening to 14+ characters improves resistance."
            StrengthLevel.STRONG -> "Estimate only: solid length. Make sure it is unique to this vault."
            StrengthLevel.VERY_STRONG -> "Estimate only: high length and variety. Store your recovery plan safely."
        }
        return PasswordStrength(
            level = level,
            entropyBits = estimatedBits,
            explanation = guidance,
            isExactFormula = false,
        )
    }

    fun normalizeDomain(rawInput: String): DomainNormalizationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return DomainNormalizationResult(
                rawInput = rawInput,
                normalizedDomain = "",
                isValid = true,
                guidance = "Optional. Used to match websites cleanly (e.g. example.com).",
            )
        }
        val withoutScheme = trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("HTTPS://")
            .removePrefix("HTTP://")
        val hostPortion = withoutScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
            .lowercase()
            .removePrefix("www.")

        val validHostRegex = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
        val isValid = validHostRegex.matches(hostPortion)
        return if (isValid) {
            DomainNormalizationResult(
                rawInput = rawInput,
                normalizedDomain = hostPortion,
                isValid = true,
                guidance = "Normalized domain: $hostPortion",
            )
        } else {
            DomainNormalizationResult(
                rawInput = rawInput,
                normalizedDomain = hostPortion,
                isValid = false,
                guidance = "Enter a valid website host such as example.com or https://app.example.org.",
            )
        }
    }

    private fun buildActiveCharSets(config: GeneratorConfig): List<String> {
        val sets = mutableListOf<String>()
        if (config.includeUppercase) sets.add(filterAmbiguous(UPPERCASE, config.excludeAmbiguous))
        if (config.includeLowercase) sets.add(filterAmbiguous(LOWERCASE, config.excludeAmbiguous))
        if (config.includeNumbers) sets.add(filterAmbiguous(NUMBERS, config.excludeAmbiguous))
        if (config.includeSymbols) sets.add(filterAmbiguous(SYMBOLS, config.excludeAmbiguous))
        if (sets.isEmpty()) {
            sets.add(filterAmbiguous(LOWERCASE, config.excludeAmbiguous))
        }
        return sets.filter { it.isNotEmpty() }
    }

    private fun filterAmbiguous(source: String, excludeAmbiguous: Boolean): String {
        return if (excludeAmbiguous) {
            source.filterNot { it in AMBIGUOUS_CHARS }
        } else {
            source
        }
    }

    private fun strengthLevelForEntropy(bits: Double): StrengthLevel = when {
        bits < 45.0 -> StrengthLevel.WEAK
        bits < 68.0 -> StrengthLevel.FAIR
        bits < 95.0 -> StrengthLevel.STRONG
        else -> StrengthLevel.VERY_STRONG
    }
}
