package com.example

import com.example.domain.model.GeneratorConfig
import com.example.domain.model.GeneratorMode
import com.example.domain.model.PasswordEngine
import com.example.domain.model.StrengthLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordEngineTest {

    @Test
    fun `random password respects requested length and excludes ambiguous characters when enabled`() {
        val config = GeneratorConfig(
            mode = GeneratorMode.PASSWORD,
            length = 24,
            includeUppercase = true,
            includeLowercase = true,
            includeNumbers = true,
            includeSymbols = false,
            excludeAmbiguous = true,
        )
        val generated = PasswordEngine.generate(config)
        assertEquals(24, generated.length)
        val ambiguous = setOf('I', 'l', '1', 'O', '0', '|')
        assertTrue(generated.none { it in ambiguous })
    }

    @Test
    fun `passphrase respects word count and separator`() {
        val config = GeneratorConfig(
            mode = GeneratorMode.PASSPHRASE,
            wordCount = 5,
            wordSeparator = ".",
            capitalizeWords = true,
            includePassphraseNumber = false,
        )
        val phrase = PasswordEngine.generate(config)
        val parts = phrase.split(".")
        assertEquals(5, parts.size)
        assertTrue(parts.all { it.first().isUpperCase() })
    }

    @Test
    fun `entropy math reports exact formula for generator`() {
        val config = GeneratorConfig(
            mode = GeneratorMode.PASSWORD,
            length = 20,
            includeUppercase = true,
            includeLowercase = true,
            includeNumbers = true,
            includeSymbols = true,
        )
        val strength = PasswordEngine.calculateGeneratorStrength(config)
        assertTrue(strength.isExactFormula)
        assertEquals(StrengthLevel.VERY_STRONG, strength.level)
        assertTrue(strength.entropyBits > 120.0)
    }

    @Test
    fun `domain normalizer strips scheme www and path cleanly`() {
        val result = PasswordEngine.normalizeDomain("https://www.Example.COM/login?next=1")
        assertTrue(result.isValid)
        assertEquals("example.com", result.normalizedDomain)
    }

    @Test
    fun `domain normalizer rejects malformed hostnames`() {
        val result = PasswordEngine.normalizeDomain("not a domain")
        assertFalse(result.isValid)
    }
}
