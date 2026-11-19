package com.example.autofill

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.VaultRuntime
import com.example.domain.model.VaultItem
import com.example.security.BiometricAuthOutcome
import com.example.security.BiometricPromptController
import com.example.security.CipherPreparationResult
import com.example.ui.components.AranIcons
import com.example.ui.components.InlineError
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecretField
import com.example.ui.components.ShieldEmblem
import com.example.ui.theme.SecureVaultTheme
import com.example.ui.theme.VaultTheme
import kotlinx.coroutines.launch

/**
 * Authenticated Autofill trampoline activity (`FragmentActivity`) launched via PendingIntent
 * when the vault is locked or requires explicit user credential confirmation.
 *
 * Automatically invokes [BiometricPromptController] (fingerprint or face unlock bound to
 * Android Keystore CryptoObject) when enrolled, with master-password fallback.
 */
@RequiresApi(Build.VERSION_CODES.O)
class AutofillAuthActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        enableEdgeToEdge()

        val targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val webDomain = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        val webScheme = intent.getStringExtra(EXTRA_WEB_SCHEME)
        val hasConflictingOrigins = intent.getBooleanExtra(EXTRA_CONFLICTING_ORIGINS, false)
        val usernameId = readParcelableAutofillId(intent, EXTRA_USERNAME_ID)
        val passwordId = readParcelableAutofillId(intent, EXTRA_PASSWORD_ID)

        val signingDigests = if (!webDomain.isNullOrBlank()) {
            emptySet()
        } else {
            AppSignatureVerifier.getSigningCertSha256HexDigests(this, targetPackage)
        }
        val targetIdentity = AutofillTargetIdentity(
            packageName = targetPackage,
            webDomain = webDomain,
            webScheme = webScheme,
            signingCertSha256Digests = signingDigests,
            hasConflictingWebOrigins = hasConflictingOrigins,
        )

        val repository = VaultRuntime.getRepository(this)

        setContent {
            val settings by repository.settings.collectAsStateWithLifecycle()
            val isInitialized by repository.isStorageInitialized.collectAsStateWithLifecycle()
            val isUnlocked by repository.isUnlocked.collectAsStateWithLifecycle()
            val biometricState by repository.biometricState.collectAsStateWithLifecycle()
            val items by repository.items.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                if (!repository.isStorageInitialized.value) {
                    repository.initializeFromDisk()
                }
                repository.refreshBiometricState()
            }

            SecureVaultTheme(themePreference = settings.themePreference) {
                AutofillAuthScreen(
                    isInitialized = isInitialized,
                    isUnlocked = isUnlocked,
                    biometricCanUnlock = biometricState.canUnlockWithBiometrics,
                    autoPromptBiometric = settings.autoPromptBiometricOnLaunch,
                    targetIdentity = targetIdentity,
                    matchDecision = if (isUnlocked) {
                        OriginMatcher.matchCredentials(
                            target = targetIdentity,
                            items = items,
                            ownPackageName = packageName,
                            allowInsecureHttp = settings.allowInsecureHttpAutofill,
                        )
                    } else null,
                    onAttemptBiometric = { onError ->
                        launchBiometricUnlock(onError)
                    },
                    onAttemptPassword = { candidate, onResult ->
                        kotlinx.coroutines.MainScope().launch {
                            val ok = repository.unlockWithMasterPassword(candidate.toCharArray())
                            onResult(ok)
                        }
                    },
                    onSelectCredential = { item ->
                        returnFilledDataset(item, usernameId, passwordId)
                    },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }

    private fun launchBiometricUnlock(onError: (String) -> Unit) {
        val repository = VaultRuntime.getRepository(this)
        kotlinx.coroutines.MainScope().launch {
            when (val prep = repository.prepareBiometricUnlockCipher()) {
                is CipherPreparationResult.Ready -> {
                    BiometricPromptController.authenticateForCipher(
                        activity = this@AutofillAuthActivity,
                        cipher = prep.cipher,
                        title = "Unlock Aran for Autofill",
                        subtitle = "Verify fingerprint or face to release matching credentials.",
                        negativeButtonText = "Use Master Password",
                    ) { outcome ->
                        when (outcome) {
                            is BiometricAuthOutcome.Succeeded -> {
                                kotlinx.coroutines.MainScope().launch {
                                    val ok = repository.completeBiometricUnlock(outcome.authenticatedCipher)
                                    if (!ok) {
                                        onError("Unable to unwrap Keystore key. Use your master password.")
                                    }
                                }
                            }
                            is BiometricAuthOutcome.Cancelled -> Unit
                            is BiometricAuthOutcome.Error -> onError(outcome.message)
                        }
                    }
                }

                is CipherPreparationResult.KeyInvalidated -> {
                    onError("Biometric enrollment changed. Unlock with your master password.")
                }

                is CipherPreparationResult.Unavailable -> {
                    onError(prep.reason)
                }
            }
        }
    }

    private fun returnFilledDataset(
        item: VaultItem,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
    ) {
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
            setTextViewText(android.R.id.text1, item.title)
            setTextViewText(android.R.id.text2, item.username.ifBlank { item.normalizedDomain })
        }
        val builder = Dataset.Builder(presentation)
        if (usernameId != null && item.username.isNotBlank()) {
            builder.setValue(usernameId, AutofillValue.forText(item.username), presentation)
        }
        if (passwordId != null && item.secret.isNotEmpty()) {
            builder.setValue(passwordId, AutofillValue.forText(item.secret), presentation)
        }
        val dataset = builder.build()
        val response = FillResponse.Builder().addDataset(dataset).build()
        val reply = Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response)
        }
        setResult(Activity.RESULT_OK, reply)
        finish()
    }

    @Suppress("DEPRECATION")
    private fun readParcelableAutofillId(intent: Intent, key: String): AutofillId? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, AutofillId::class.java)
        } else {
            intent.getParcelableExtra(key)
        }
    }

    companion object {
        private const val EXTRA_PACKAGE_NAME = "extra_autofill_package_name"
        private const val EXTRA_WEB_DOMAIN = "extra_autofill_web_domain"
        private const val EXTRA_WEB_SCHEME = "extra_autofill_web_scheme"
        private const val EXTRA_USERNAME_ID = "extra_autofill_username_id"
        private const val EXTRA_PASSWORD_ID = "extra_autofill_password_id"
        private const val EXTRA_CONFLICTING_ORIGINS = "extra_autofill_conflicting_origins"

        fun createIntent(
            context: Context,
            packageName: String,
            webDomain: String?,
            webScheme: String?,
            usernameId: AutofillId?,
            passwordId: AutofillId?,
            hasConflictingOrigins: Boolean,
        ): Intent = Intent(context, AutofillAuthActivity::class.java).apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_WEB_DOMAIN, webDomain)
            putExtra(EXTRA_WEB_SCHEME, webScheme)
            putExtra(EXTRA_USERNAME_ID, usernameId)
            putExtra(EXTRA_PASSWORD_ID, passwordId)
            putExtra(EXTRA_CONFLICTING_ORIGINS, hasConflictingOrigins)
        }
    }
}

@Composable
private fun AutofillAuthScreen(
    isInitialized: Boolean,
    isUnlocked: Boolean,
    biometricCanUnlock: Boolean,
    autoPromptBiometric: Boolean,
    targetIdentity: AutofillTargetIdentity,
    matchDecision: AutofillMatchDecision?,
    onAttemptBiometric: (onError: (String) -> Unit) -> Unit,
    onAttemptPassword: (candidate: String, onResult: (Boolean) -> Unit) -> Unit,
    onSelectCredential: (VaultItem) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = VaultTheme.colors
    var password by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var autoPrompted by remember { mutableStateOf(false) }

    LaunchedEffect(isInitialized, isUnlocked, biometricCanUnlock, autoPromptBiometric) {
        if (isInitialized && !isUnlocked && biometricCanUnlock && autoPromptBiometric && !autoPrompted) {
            autoPrompted = true
            onAttemptBiometric { msg -> errorMessage = msg }
        }
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ShieldEmblem(size = 40.dp, locked = !isUnlocked)
                    Column {
                        Text(
                            text = if (isUnlocked) "Select Credential" else "Unlock Aran Autofill",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = targetIdentity.webDomain ?: targetIdentity.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.primary,
                        )
                    }
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }

            if (!isUnlocked) {
                if (biometricCanUnlock) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = !busy) {
                                errorMessage = null
                                onAttemptBiometric { msg -> errorMessage = msg }
                            }
                            .testTag("autofill_biometric_button"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(colors.surfaceElevated),
                            ) {
                                Icon(
                                    imageVector = AranIcons.Fingerprint,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = "Unlock with Fingerprint or Face",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = "Android Keystore hardware CryptoObject",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    InlineError(message = errorMessage!!, testTag = "autofill_error")
                }

                SecretField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = "Master Password",
                    revealed = revealed,
                    onToggleReveal = { revealed = !revealed },
                    leadingIcon = Icons.Outlined.Lock,
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        if (!busy && password.isNotEmpty()) {
                            busy = true
                            onAttemptPassword(password) { ok ->
                                busy = false
                                if (!ok) errorMessage = "Unable to unlock vault. Check your master password."
                                else password = ""
                            }
                        }
                    },
                    isError = errorMessage != null,
                    testTag = "autofill_password_input",
                    toggleTestTag = "autofill_password_toggle",
                )

                PrimaryButton(
                    text = if (busy) "Deriving Key…" else "Unlock & Match",
                    enabled = !busy && password.isNotEmpty(),
                    onClick = {
                        busy = true
                        onAttemptPassword(password) { ok ->
                            busy = false
                            if (!ok) errorMessage = "Unable to unlock vault. Check your master password."
                            else password = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "autofill_unlock_submit",
                )
            } else {
                when (matchDecision) {
                    is AutofillMatchDecision.BlockedInsecureHttp -> {
                        SecurityBlockedBanner(
                            title = "Insecure HTTP Blocked",
                            body = matchDecision.reason,
                        )
                    }

                    is AutofillMatchDecision.BlockedSecurityViolation -> {
                        SecurityBlockedBanner(
                            title = "Autofill Blocked",
                            body = matchDecision.reason,
                        )
                    }

                    is AutofillMatchDecision.Allowed -> {
                        if (matchDecision.matches.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = colors.surface,
                                border = BorderStroke(1.dp, colors.outlineSubtle),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(16.dp),
                                ) {
                                    Text(
                                        text = "No Verified Matches",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.textPrimary,
                                    )
                                    Text(
                                        text = "No logins in your vault strictly match ${matchDecision.verifiedOriginLabel}. Aran does not fill mismatched domains.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(matchDecision.matches, key = { it.item.id }) { match ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = colors.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (match.requiresExplicitConfirmation) colors.warning.copy(alpha = 0.5f)
                                            else colors.outlineSubtle,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { onSelectCredential(match.item) },
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(16.dp),
                                        ) {
                                            Text(
                                                text = match.item.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = colors.textPrimary,
                                            )
                                            Text(
                                                text = match.item.username.ifBlank { match.item.normalizedDomain },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.textSecondary,
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = match.matchReason,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (match.requiresExplicitConfirmation) colors.warning else colors.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun SecurityBlockedBanner(title: String, body: String) {
    val colors = VaultTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.warningContainer,
        border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
