package com.example.passkey

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CreatePasswordResponse
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autofill.AppSignatureVerifier
import com.example.data.VaultRuntime
import com.example.domain.model.VaultItem
import com.example.domain.model.VaultItemType
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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Authenticated Android 14+ Credential Provider confirmation & unlock activity (`FragmentActivity`).
 *
 * Enforces:
 * - `FLAG_SECURE` + no recents thumbnail.
 * - Automatic `BiometricPrompt` (Fingerprint / Face Unlock via Keystore CryptoObject) when bound,
 *   with Master Password fallback.
 * - Explicit Relying Party (`rpId`) and calling-app identity display before generating or signing
 *   an ECDSA P-256 passkey.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyAuthActivity : FragmentActivity() {

    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setRecentsScreenshotEnabled(false)
        enableEdgeToEdge()

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_UNLOCK_FOR_GET
        val selectedVaultItemId = intent.getStringExtra(EXTRA_VAULT_ITEM_ID)
        val repository = VaultRuntime.getRepository(this)

        val requestSummary = inspectRequestSummary(mode)

        setContent {
            val settings by repository.settings.collectAsStateWithLifecycle()
            val isInitialized by repository.isStorageInitialized.collectAsStateWithLifecycle()
            val isUnlocked by repository.isUnlocked.collectAsStateWithLifecycle()
            val biometricState by repository.biometricState.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                if (!repository.isStorageInitialized.value) {
                    repository.initializeFromDisk()
                }
                repository.refreshBiometricState()
            }

            // If we were invoked solely to unlock a locked vault for BeginGet, immediately return
            // the unlocked BeginGetCredentialResponse once unlocked.
            LaunchedEffect(isUnlocked, mode) {
                if (isUnlocked && mode == MODE_UNLOCK_FOR_GET) {
                    completeUnlockForGet()
                }
            }

            SecureVaultTheme(themePreference = settings.themePreference) {
                PasskeyAuthScreen(
                    mode = mode,
                    title = requestSummary.title,
                    subtitle = requestSummary.subtitle,
                    detailLine = requestSummary.detailLine,
                    isInitialized = isInitialized,
                    isUnlocked = isUnlocked,
                    biometricCanUnlock = biometricState.canUnlockWithBiometrics,
                    autoPromptBiometric = settings.autoPromptBiometricOnLaunch,
                    onAttemptBiometric = { onError -> launchBiometricUnlock(onError) },
                    onAttemptPassword = { candidate, onResult ->
                        scope.launch {
                            val ok = repository.unlockWithMasterPassword(candidate.toCharArray())
                            onResult(ok)
                        }
                    },
                    onConfirmOperation = { onError ->
                        executeConfirmedOperation(mode, selectedVaultItemId, onError)
                    },
                    onCancel = { cancelWithCredentialException(mode) },
                )
            }
        }
    }

    private fun launchBiometricUnlock(onError: (String) -> Unit) {
        val repository = VaultRuntime.getRepository(this)
        scope.launch {
            when (val prep = repository.prepareBiometricUnlockCipher()) {
                is CipherPreparationResult.Ready -> {
                    BiometricPromptController.authenticateForCipher(
                        activity = this@PasskeyAuthActivity,
                        cipher = prep.cipher,
                        title = "Verify Identity for Passkey",
                        subtitle = "Confirm fingerprint or face to unlock Aran's Keystore key.",
                        negativeButtonText = "Use Master Password",
                    ) { outcome ->
                        when (outcome) {
                            is BiometricAuthOutcome.Succeeded -> {
                                scope.launch {
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

                is CipherPreparationResult.Unavailable -> onError(prep.reason)
            }
        }
    }

    private fun completeUnlockForGet() {
        val repository = VaultRuntime.getRepository(this)
        val beginRequest = PendingIntentHandler.retrieveBeginGetCredentialRequest(intent)
        if (beginRequest == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val response = AranCredentialProviderService.buildUnlockedBeginGetResponse(
            context = this,
            request = beginRequest,
            items = repository.items.value,
            allowInsecureHttp = repository.settings.value.allowInsecureHttpAutofill,
        )
        val resultIntent = Intent()
        PendingIntentHandler.setBeginGetCredentialResponse(resultIntent, response)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun executeConfirmedOperation(
        mode: String,
        selectedVaultItemId: String?,
        onError: (String) -> Unit,
    ) {
        val repository = VaultRuntime.getRepository(this)
        scope.launch {
            when (mode) {
                MODE_CREATE_PASSKEY -> {
                    val req = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
                    val createReq = req?.callingRequest as? CreatePublicKeyCredentialRequest
                    if (req == null || createReq == null) {
                        failCreate("Invalid passkey creation request.")
                        return@launch
                    }
                    val origin = resolveVerifiedOrigin(req.callingAppInfo)
                    if (origin == null) {
                        onError("Could not verify calling app signing certificate.")
                        return@launch
                    }
                    val existingPasskeys = repository.items.value
                        .filter { it.type == VaultItemType.PASSKEY }
                        .mapNotNull { PasskeyRecordPayload.fromJsonOrNull(it.secret) }

                    try {
                        val registration = OfflinePasskeyEngine.createPasskeyRegistration(
                            requestJson = createReq.requestJson,
                            verifiedCallingOrigin = origin,
                            clientDataHash = createReq.clientDataHash,
                            existingPasskeys = existingPasskeys,
                        )
                        repository.upsertItem(registration.record.toVaultItem())
                        val resultIntent = Intent()
                        PendingIntentHandler.setCreateCredentialResponse(
                            resultIntent,
                            CreatePublicKeyCredentialResponse(registration.registrationResponseJson),
                        )
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    } catch (e: PasskeySecurityException) {
                        onError(e.message ?: "Passkey security verification failed.")
                    } catch (_: Exception) {
                        failCreate("Unable to generate ES256 passkey.")
                    }
                }

                MODE_CREATE_PASSWORD -> {
                    val req = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
                    val passReq = req?.callingRequest as? CreatePasswordRequest
                    if (req == null || passReq == null) {
                        failCreate("Invalid password save request.")
                        return@launch
                    }
                    val pkg = req.callingAppInfo.packageName
                    val cert = AppSignatureVerifier.getSigningCertSha256HexDigests(this@PasskeyAuthActivity, pkg)
                        .firstOrNull()
                    val website = if (cert != null) "androidapp://$pkg?sha256=$cert" else "androidapp://$pkg"
                    repository.upsertItem(
                        VaultItem(
                            id = "",
                            type = VaultItemType.LOGIN,
                            title = pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                            username = passReq.id,
                            secret = passReq.password,
                            website = website,
                            normalizedDomain = "",
                            notes = "Saved via Android Credential Manager (encrypted locally).",
                            isFavorite = false,
                            createdAtLabel = "Just now",
                            updatedAtLabel = "Just now",
                            lastUsedEpochMs = System.currentTimeMillis(),
                        )
                    )
                    val resultIntent = Intent()
                    PendingIntentHandler.setCreateCredentialResponse(resultIntent, CreatePasswordResponse())
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }

                MODE_GET_PASSKEY -> {
                    val req = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
                    val option = req?.credentialOptions?.filterIsInstance<GetPublicKeyCredentialOption>()
                        ?.firstOrNull()
                    val item = repository.items.value.firstOrNull { it.id == selectedVaultItemId }
                    val payload = item?.let { PasskeyRecordPayload.fromJsonOrNull(it.secret) }
                    if (req == null || option == null || item == null || payload == null) {
                        failGet("Selected passkey could not be found in vault.")
                        return@launch
                    }
                    val origin = resolveVerifiedOrigin(req.callingAppInfo)
                    if (origin == null) {
                        onError("Could not verify calling app origin.")
                        return@launch
                    }
                    try {
                        val assertion = OfflinePasskeyEngine.signPasskeyAssertion(
                            requestJson = option.requestJson,
                            passkey = payload,
                            verifiedCallingOrigin = origin,
                            clientDataHash = option.clientDataHash,
                        )
                        // Persist incremented signCount back to encrypted SQLite vault.
                        repository.upsertItem(assertion.updatedRecord.toVaultItem(existingId = item.id))
                        val resultIntent = Intent()
                        PendingIntentHandler.setGetCredentialResponse(
                            resultIntent,
                            GetCredentialResponse(PublicKeyCredential(assertion.authenticationResponseJson)),
                        )
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    } catch (e: PasskeySecurityException) {
                        onError(e.message ?: "Passkey assertion blocked.")
                    } catch (_: Exception) {
                        failGet("Unable to sign WebAuthn assertion.")
                    }
                }

                MODE_GET_PASSWORD -> {
                    val item = repository.items.value.firstOrNull { it.id == selectedVaultItemId }
                    if (item == null) {
                        failGet("Selected password could not be found.")
                        return@launch
                    }
                    repository.markItemUsed(item.id)
                    val resultIntent = Intent()
                    PendingIntentHandler.setGetCredentialResponse(
                        resultIntent,
                        GetCredentialResponse(PasswordCredential(item.username, item.secret)),
                    )
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }

                else -> completeUnlockForGet()
            }
        }
    }

    private fun resolveVerifiedOrigin(callingAppInfo: CallingAppInfo): String? {
        return AppSignatureVerifier.getPrimaryApkKeyHashOrigin(this, callingAppInfo.packageName)
    }

    private fun failCreate(message: String) {
        val resultIntent = Intent()
        PendingIntentHandler.setCreateCredentialException(
            resultIntent,
            CreateCredentialUnknownException(message),
        )
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun failGet(message: String) {
        val resultIntent = Intent()
        PendingIntentHandler.setGetCredentialException(
            resultIntent,
            GetCredentialUnknownException(message),
        )
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun cancelWithCredentialException(mode: String) {
        val resultIntent = Intent()
        if (mode == MODE_CREATE_PASSKEY || mode == MODE_CREATE_PASSWORD) {
            PendingIntentHandler.setCreateCredentialException(
                resultIntent,
                CreateCredentialCancellationException("User cancelled credential creation."),
            )
            setResult(Activity.RESULT_OK, resultIntent)
        } else if (mode == MODE_GET_PASSKEY || mode == MODE_GET_PASSWORD) {
            PendingIntentHandler.setGetCredentialException(
                resultIntent,
                GetCredentialCancellationException("User cancelled credential selection."),
            )
            setResult(Activity.RESULT_OK, resultIntent)
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    private fun inspectRequestSummary(mode: String): RequestSummary {
        return when (mode) {
            MODE_CREATE_PASSKEY -> {
                val req = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
                val pkReq = req?.callingRequest as? CreatePublicKeyCredentialRequest
                val json = pkReq?.requestJson?.let { runCatching { JSONObject(it) }.getOrNull() }
                val rpId = json?.optJSONObject("rp")?.optString("id").orEmpty().ifBlank { "Relying Party" }
                val userName = json?.optJSONObject("user")?.optString("name").orEmpty()
                RequestSummary(
                    title = "Create Offline Passkey",
                    subtitle = rpId,
                    detailLine = "Account: ${userName.ifBlank { "Default" }} • Algorithm: ES256 (P-256) • Stored locally with AES-256-GCM",
                )
            }

            MODE_CREATE_PASSWORD -> {
                val req = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
                val pkg = req?.callingAppInfo?.packageName.orEmpty()
                RequestSummary(
                    title = "Save Password Offline",
                    subtitle = pkg.ifBlank { "Android App" },
                    detailLine = "Encrypted locally inside your Aran SQLite vault with APK SHA-256 pinning.",
                )
            }

            MODE_GET_PASSKEY -> {
                val req = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
                val opt = req?.credentialOptions?.filterIsInstance<GetPublicKeyCredentialOption>()?.firstOrNull()
                val rpId = opt?.requestJson?.let {
                    runCatching { JSONObject(it).optString("rpId") }.getOrNull()
                }.orEmpty().ifBlank { "Relying Party" }
                RequestSummary(
                    title = "Sign In with Passkey",
                    subtitle = rpId,
                    detailLine = "Confirm to sign WebAuthn assertion using your locally encrypted ES256 key.",
                )
            }

            MODE_GET_PASSWORD -> RequestSummary(
                title = "Sign In with Aran",
                subtitle = "Verified App Credential",
                detailLine = "Confirm to release this decrypted credential to the requesting app.",
            )

            else -> RequestSummary(
                title = "Unlock Aran Offline Vault",
                subtitle = "Passkeys & Passwords",
                detailLine = "Authenticate with fingerprint, face, or master password to search local credentials.",
            )
        }
    }

    private data class RequestSummary(
        val title: String,
        val subtitle: String,
        val detailLine: String,
    )

    companion object {
        private const val EXTRA_MODE = "extra_passkey_mode"
        private const val EXTRA_VAULT_ITEM_ID = "extra_passkey_vault_item_id"
        private const val EXTRA_CREDENTIAL_ID = "extra_passkey_credential_id"

        const val MODE_CREATE_PASSKEY = "create_passkey"
        const val MODE_CREATE_PASSWORD = "create_password"
        const val MODE_GET_PASSKEY = "get_passkey"
        const val MODE_GET_PASSWORD = "get_password"
        const val MODE_UNLOCK_FOR_GET = "unlock_for_get"

        fun createPasskeyRegistrationIntent(context: Context): Intent =
            Intent(context, PasskeyAuthActivity::class.java).putExtra(EXTRA_MODE, MODE_CREATE_PASSKEY)

        fun createPasswordSaveIntent(context: Context): Intent =
            Intent(context, PasskeyAuthActivity::class.java).putExtra(EXTRA_MODE, MODE_CREATE_PASSWORD)

        fun createPasskeyAssertionIntent(
            context: Context,
            vaultItemId: String,
            credentialIdB64Url: String,
        ): Intent = Intent(context, PasskeyAuthActivity::class.java).apply {
            putExtra(EXTRA_MODE, MODE_GET_PASSKEY)
            putExtra(EXTRA_VAULT_ITEM_ID, vaultItemId)
            putExtra(EXTRA_CREDENTIAL_ID, credentialIdB64Url)
        }

        fun createPasswordGetIntent(context: Context, vaultItemId: String): Intent =
            Intent(context, PasskeyAuthActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_GET_PASSWORD)
                putExtra(EXTRA_VAULT_ITEM_ID, vaultItemId)
            }

        fun createUnlockForGetIntent(context: Context): Intent =
            Intent(context, PasskeyAuthActivity::class.java).putExtra(EXTRA_MODE, MODE_UNLOCK_FOR_GET)
    }
}

@Composable
private fun PasskeyAuthScreen(
    mode: String,
    title: String,
    subtitle: String,
    detailLine: String,
    isInitialized: Boolean,
    isUnlocked: Boolean,
    biometricCanUnlock: Boolean,
    autoPromptBiometric: Boolean,
    onAttemptBiometric: (onError: (String) -> Unit) -> Unit,
    onAttemptPassword: (candidate: String, onResult: (Boolean) -> Unit) -> Unit,
    onConfirmOperation: (onError: (String) -> Unit) -> Unit,
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
        contentAlignment = Alignment.Center,
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
                .widthIn(max = 460.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ShieldEmblem(size = 44.dp, locked = !isUnlocked)
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.primary,
                        )
                    }
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outlineSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = detailLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(14.dp),
                )
            }

            if (errorMessage != null) {
                InlineError(message = errorMessage!!, testTag = "passkey_auth_error")
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
                            .testTag("passkey_biometric_button"),
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
                                    text = "Authenticate with Fingerprint or Face",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = "Hardware-backed Android Keystore CryptoObject",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
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
                    testTag = "passkey_password_input",
                    toggleTestTag = "passkey_password_toggle",
                )

                PrimaryButton(
                    text = if (busy) "Deriving Key…" else "Unlock Vault",
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
                    testTag = "passkey_unlock_submit",
                )
            } else if (mode != PasskeyAuthActivity.MODE_UNLOCK_FOR_GET) {
                val ctaLabel = when (mode) {
                    PasskeyAuthActivity.MODE_CREATE_PASSKEY -> "Generate & Encrypt Passkey"
                    PasskeyAuthActivity.MODE_CREATE_PASSWORD -> "Save Encrypted Password"
                    PasskeyAuthActivity.MODE_GET_PASSKEY -> "Confirm & Sign Passkey"
                    else -> "Confirm & Fill"
                }
                PrimaryButton(
                    text = ctaLabel,
                    enabled = !busy,
                    onClick = {
                        errorMessage = null
                        busy = true
                        onConfirmOperation { msg ->
                            busy = false
                            errorMessage = msg
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "passkey_confirm_button",
                )
            }
        }
    }
}
