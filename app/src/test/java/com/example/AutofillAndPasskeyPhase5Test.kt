package com.example

import android.content.Context
import android.view.View
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.autofill.AutofillMatchDecision
import com.example.autofill.AutofillStructureParser
import com.example.autofill.AutofillTargetIdentity
import com.example.autofill.InspectableFormNode
import com.example.autofill.OriginMatchStrength
import com.example.autofill.OriginMatcher
import com.example.crypto.KdfParameters
import com.example.data.PersistentVaultRepository
import com.example.data.local.VaultDatabase
import com.example.domain.model.VaultItem
import com.example.domain.model.VaultItemType
import com.example.passkey.OfflinePasskeyEngine
import com.example.passkey.PasskeyRecordPayload
import com.example.passkey.PasskeySecurityException
import com.example.security.BiometricAvailability
import com.example.security.BiometricKeyState
import com.example.security.LockReason
import com.example.ui.screens.UnlockScreen
import com.example.ui.theme.SecureVaultTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 5 Gate Verification Suite:
 * 1. Strict HTTPS origin/hostname matching, public-suffix isolation, insecure HTTP blocking,
 *    and lookalike/cross-origin rejection.
 * 2. Native Android app APK SHA-256 signing certificate verification & mismatch blocking.
 * 3. AssistStructure parser hidden-field filtering and conflicting iframe origin detection.
 * 4. Offline FIDO2/WebAuthn ES256 passkey registration, encrypted SQLite roundtrip,
 *    assertion signing, ECDSA signature verification, and cross-origin RP ID rejection.
 * 5. BiometricPrompt application entry-point auto-trigger behavior on UnlockScreen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutofillAndPasskeyPhase5Test {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: VaultDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = VaultDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `origin matcher enforces strict HTTPS host matching blocks insecure HTTP and prevents cross origin leakage`() {
        val bankUkItem = sampleLogin(
            id = "login-bank-uk",
            title = "Albion Bank",
            website = "https://online.albionbank.co.uk",
        )
        val gitItem = sampleLogin(
            id = "login-git",
            title = "CodeForge",
            website = "https://codeforge.example.com",
        )
        val items = listOf(bankUkItem, gitItem)
        val ownPkg = "com.aistudio.securevault.qvxkdm"

        // 1. Exact HTTPS host match succeeds cleanly without ambiguity
        val exact = OriginMatcher.matchCredentials(
            target = AutofillTargetIdentity(
                packageName = "com.android.chrome",
                webDomain = "codeforge.example.com",
                webScheme = "https",
            ),
            items = items,
            ownPackageName = ownPkg,
        )
        assertTrue(exact is AutofillMatchDecision.Allowed)
        exact as AutofillMatchDecision.Allowed
        assertEquals(1, exact.matches.size)
        assertEquals(OriginMatchStrength.EXACT_HOST, exact.matches.first().strength)
        assertFalse(exact.matches.first().requiresExplicitConfirmation)

        // 2. Subdomain under same registrable domain requires explicit confirmation
        val subdomain = OriginMatcher.matchCredentials(
            target = AutofillTargetIdentity(
                packageName = "com.android.chrome",
                webDomain = "auth.example.com",
                webScheme = "https",
            ),
            items = items,
            ownPackageName = ownPkg,
        )
        assertTrue(subdomain is AutofillMatchDecision.Allowed)
        subdomain as AutofillMatchDecision.Allowed
        assertEquals(1, subdomain.matches.size)
        assertEquals(OriginMatchStrength.SAME_REGISTRABLE_DOMAIN, subdomain.matches.first().strength)
        assertTrue(subdomain.matches.first().requiresExplicitConfirmation)
        assertTrue(subdomain.requiresExplicitSelection)

        // 3. Insecure HTTP is blocked by default
        val blockedHttp = OriginMatcher.matchCredentials(
            target = AutofillTargetIdentity(
                packageName = "com.android.chrome",
                webDomain = "codeforge.example.com",
                webScheme = "http",
            ),
            items = items,
            ownPackageName = ownPkg,
            allowInsecureHttp = false,
        )
        assertTrue(blockedHttp is AutofillMatchDecision.BlockedInsecureHttp)

        // Explicit exception permits HTTP when opted in
        val allowedHttp = OriginMatcher.matchCredentials(
            target = AutofillTargetIdentity(
                packageName = "com.android.chrome",
                webDomain = "codeforge.example.com",
                webScheme = "http",
            ),
            items = items,
            ownPackageName = ownPkg,
            allowInsecureHttp = true,
        )
        assertTrue(allowedHttp is AutofillMatchDecision.Allowed)

        // 4. Malicious lookalikes & multi-label public suffix attacks yield ZERO matches
        val evilDomains = listOf(
            "evil.co.uk",                // shares .co.uk suffix with albionbank.co.uk
            "notexample.com",            // suffix string trick
            "codeforge.example.com.evil.org", // subdomain impersonation
        )
        for (evil in evilDomains) {
            val res = OriginMatcher.matchCredentials(
                target = AutofillTargetIdentity(
                    packageName = "com.android.chrome",
                    webDomain = evil,
                    webScheme = "https",
                ),
                items = items,
                ownPackageName = ownPkg,
            )
            assertTrue("Expected Allowed(empty) for $evil", res is AutofillMatchDecision.Allowed)
            assertTrue("Expected zero matches for $evil", (res as AutofillMatchDecision.Allowed).matches.isEmpty())
        }

        // 5. Userinfo spoofing URL is rejected completely
        assertNull(OriginMatcher.normalizeHost("https://codeforge.example.com@evil.com"))

        // 6. Refuses to autofill into Aran's own package
        val selfFill = OriginMatcher.matchCredentials(
            target = AutofillTargetIdentity(packageName = ownPkg),
            items = items,
            ownPackageName = ownPkg,
        )
        assertTrue(selfFill is AutofillMatchDecision.BlockedSecurityViolation)
    }

    @Test
    fun `native app autofill verifies APK signing certificate SHA256 and blocks impersonators`() {
        val legitimateCert = "AA11BB22CC33DD44EE55FF6600112233445566778899AABBCCDDEEFF00112233"
        val attackerCert = "9999999999999999999999999999999999999999999999999999999999999999"

        val pinnedItem = sampleLogin(
            id = "login-native-bank",
            title = "Evergreen Mobile",
            website = "androidapp://com.evergreen.mobile?sha256=$legitimateCert",
        )

        // Matching signing certificate succeeds
        val ok = OriginMatcher.matchCredentials(
            target = AutofillTargetIdentity(
                packageName = "com.evergreen.mobile",
                signingCertSha256Digests = setOf(legitimateCert),
            ),
            items = listOf(pinnedItem),
            ownPackageName = "com.aistudio.securevault.qvxkdm",
        )
        assertTrue(ok is AutofillMatchDecision.Allowed)
        assertEquals(1, (ok as AutofillMatchDecision.Allowed).matches.size)
        assertEquals(OriginMatchStrength.VERIFIED_NATIVE_APP, ok.matches.first().strength)

        // Repackaged APK with mismatched certificate is blocked
        val blocked = OriginMatcher.matchCredentials(
            target = AutofillTargetIdentity(
                packageName = "com.evergreen.mobile",
                signingCertSha256Digests = setOf(attackerCert),
            ),
            items = listOf(pinnedItem),
            ownPackageName = "com.aistudio.securevault.qvxkdm",
        )
        assertTrue(blocked is AutofillMatchDecision.BlockedSecurityViolation)
    }

    @Test
    fun `autofill structure parser ignores hidden fields and flags conflicting embedded web origins`() {
        val nodes = listOf(
            // Hidden trap password field must be ignored
            InspectableFormNode(
                nodeKey = "hidden-trap",
                isVisible = false,
                autofillHints = listOf(View.AUTOFILL_HINT_PASSWORD),
                webDomain = "trusted.example.com",
                webScheme = "https",
            ),
            InspectableFormNode(
                nodeKey = "html-hidden-user",
                isVisible = true,
                htmlTag = "input",
                htmlInputType = "hidden",
                htmlAutocomplete = "username",
                webDomain = "trusted.example.com",
                webScheme = "https",
            ),
            InspectableFormNode(
                nodeKey = "visible-user",
                isVisible = true,
                autofillHints = listOf(View.AUTOFILL_HINT_USERNAME),
                webDomain = "trusted.example.com",
                webScheme = "https",
            ),
            InspectableFormNode(
                nodeKey = "visible-pass",
                isVisible = true,
                autofillHints = listOf(View.AUTOFILL_HINT_PASSWORD),
                webDomain = "trusted.example.com",
                webScheme = "https",
            ),
        )

        val parsedClean = AutofillStructureParser.parseNodes(nodes)
        assertEquals("visible-user", parsedClean.usernameNodeKey)
        assertEquals("visible-pass", parsedClean.passwordNodeKey)
        assertEquals(2, parsedClean.ignoredHiddenFieldCount)
        assertFalse(parsedClean.hasConflictingWebOrigins)

        // Add an embedded cross-origin iframe node -> must flag conflicting origins
        val mixedNodes = nodes + InspectableFormNode(
            nodeKey = "evil-iframe-input",
            isVisible = true,
            webDomain = "attacker.example.net",
            webScheme = "https",
        )
        val parsedMixed = AutofillStructureParser.parseNodes(mixedNodes)
        assertTrue(parsedMixed.hasConflictingWebOrigins)

        val decision = OriginMatcher.matchCredentials(
            target = AutofillTargetIdentity(
                packageName = "com.android.chrome",
                webDomain = parsedMixed.webDomain,
                webScheme = parsedMixed.webScheme,
                hasConflictingWebOrigins = parsedMixed.hasConflictingWebOrigins,
            ),
            items = listOf(sampleLogin("id1", "Trusted", "https://trusted.example.com")),
            ownPackageName = "com.aistudio.securevault.qvxkdm",
        )
        assertTrue(decision is AutofillMatchDecision.BlockedSecurityViolation)
    }

    @Test
    fun `offline ES256 passkey registers persists encrypted in SQLite signs valid WebAuthn assertion and blocks cross origin`() = runTest {
        val repo = PersistentVaultRepository(
            vaultDao = database.vaultDao(),
            kdfParameters = KdfParameters.TEST_FAST,
            ioDispatcher = Dispatchers.Unconfined,
            cryptoDispatcher = Dispatchers.Unconfined,
        )
        repo.initializeFromDisk()
        val masterPass = "phase5-offline-passkey-master"
        repo.createVault(masterPass.toCharArray())

        val createOptionsJson = JSONObject().apply {
            put("rp", JSONObject().apply {
                put("id", "codeforge.example.com")
                put("name", "CodeForge")
            })
            put("user", JSONObject().apply {
                put("id", OfflinePasskeyEngine.b64UrlEncode("user-42".toByteArray()))
                put("name", "alex@example.com")
                put("displayName", "Alex Chen")
            })
            put("challenge", OfflinePasskeyEngine.b64UrlEncode("reg-challenge-123456".toByteArray()))
            put("pubKeyCredParams", JSONArray().put(JSONObject().apply {
                put("type", "public-key")
                put("alg", -7)
            }))
        }.toString()

        // 1. Cross-origin registration MUST be blocked
        try {
            OfflinePasskeyEngine.createPasskeyRegistration(
                requestJson = createOptionsJson,
                verifiedCallingOrigin = "https://phishing.evil.org",
            )
            fail("Expected PasskeySecurityException for cross-origin registration")
        } catch (_: PasskeySecurityException) {
            // Expected
        }

        // 2. Valid HTTPS origin registration succeeds and persists encrypted into local SQLite
        val registration = OfflinePasskeyEngine.createPasskeyRegistration(
            requestJson = createOptionsJson,
            verifiedCallingOrigin = "https://login.codeforge.example.com",
        )
        val savedItem = repo.upsertItem(registration.record.toVaultItem())
        assertEquals(VaultItemType.PASSKEY, savedItem.type)

        // Lock & unlock vault to verify encrypted SQLite persistence roundtrip
        repo.lockVault()
        assertTrue(repo.items.value.isEmpty())
        assertTrue(repo.unlockWithMasterPassword(masterPass.toCharArray()))

        val matched = OfflinePasskeyEngine.extractMatchingPasskeys(
            items = repo.items.value,
            rpId = "codeforge.example.com",
        )
        assertEquals(1, matched.size)
        val (restoredItem, restoredPasskey) = matched.first()
        assertEquals(registration.record.credentialIdB64Url, restoredPasskey.credentialIdB64Url)
        assertEquals(0L, restoredPasskey.signCount)

        // 3. Duplicate registration with excludeCredentials MUST be rejected
        val dupOptionsJson = JSONObject(createOptionsJson).apply {
            put("excludeCredentials", JSONArray().put(JSONObject().apply {
                put("type", "public-key")
                put("id", restoredPasskey.credentialIdB64Url)
            }))
        }.toString()
        try {
            OfflinePasskeyEngine.createPasskeyRegistration(
                requestJson = dupOptionsJson,
                verifiedCallingOrigin = "https://codeforge.example.com",
                existingPasskeys = listOf(restoredPasskey),
            )
            fail("Expected PasskeySecurityException for excluded credential")
        } catch (_: PasskeySecurityException) {
            // Expected
        }

        // 4. Sign WebAuthn assertion and verify ECDSA P-256 signature cryptographically
        val assertOptionsJson = JSONObject().apply {
            put("rpId", "codeforge.example.com")
            put("challenge", OfflinePasskeyEngine.b64UrlEncode("login-challenge-987654".toByteArray()))
            put("allowCredentials", JSONArray().put(JSONObject().apply {
                put("type", "public-key")
                put("id", restoredPasskey.credentialIdB64Url)
            }))
        }.toString()

        val assertion = OfflinePasskeyEngine.signPasskeyAssertion(
            requestJson = assertOptionsJson,
            passkey = restoredPasskey,
            verifiedCallingOrigin = "https://codeforge.example.com",
        )
        assertEquals(1L, assertion.updatedRecord.signCount)
        assertTrue(
            OfflinePasskeyEngine.verifyAssertionSignature(
                authenticationResponseJson = assertion.authenticationResponseJson,
                publicKeySpkiB64Url = restoredPasskey.publicKeySpkiB64Url,
            )
        )

        // Persist incremented signCount and confirm stored payload updated
        repo.upsertItem(assertion.updatedRecord.toVaultItem(existingId = restoredItem.id))
        val updatedPayload = PasskeyRecordPayload.fromJsonOrNull(repo.items.value.first().secret)
        assertNotNull(updatedPayload)
        assertEquals(1L, updatedPayload!!.signCount)

        // 5. Cross-origin or insecure HTTP assertion MUST be rejected
        try {
            OfflinePasskeyEngine.signPasskeyAssertion(
                requestJson = assertOptionsJson,
                passkey = restoredPasskey,
                verifiedCallingOrigin = "http://codeforge.example.com",
            )
            fail("Expected PasskeySecurityException for insecure HTTP assertion")
        } catch (_: PasskeySecurityException) {
            // Expected
        }
    }

    @Test
    fun `unlock screen automatically triggers BiometricPrompt on app entry when bound and enabled`() {
        var biometricTriggerCount = 0
        val boundState = BiometricKeyState(
            availability = BiometricAvailability.READY,
            hasBoundEnvelope = true,
            isDeviceSecure = true,
            statusMessage = "Ready",
        )

        composeRule.setContent {
            SecureVaultTheme {
                UnlockScreen(
                    biometricState = boundState,
                    lockReason = LockReason.COLD_START,
                    isBusy = false,
                    autoPromptBiometricOnLaunch = true,
                    onUnlockWithPassword = { _, cb -> cb(false) },
                    onUnlockWithBiometric = { biometricTriggerCount++ },
                    onRecoverWithKey = { _, _, cb -> cb(false) },
                    onReturnToWelcome = {},
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, biometricTriggerCount)
    }

    private fun sampleLogin(id: String, title: String, website: String): VaultItem = VaultItem(
        id = id,
        type = VaultItemType.LOGIN,
        title = title,
        username = "alex@example.com",
        secret = "SuperStrong#Pass99",
        website = website,
        normalizedDomain = OriginMatcher.normalizeHost(website).orEmpty(),
        notes = "",
        isFavorite = false,
        createdAtLabel = "Now",
        updatedAtLabel = "Now",
        lastUsedEpochMs = 1_000L,
    )
}
