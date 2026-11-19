package com.example.passkey

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.AuthenticationAction
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePasswordCredentialRequest
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.PasswordCredentialEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.credentials.provider.PublicKeyCredentialEntry
import com.example.autofill.AppSignatureVerifier
import com.example.autofill.AutofillMatchDecision
import com.example.autofill.AutofillTargetIdentity
import com.example.autofill.OriginMatcher
import com.example.data.VaultRuntime
import com.example.domain.model.VaultItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Phase 5 Offline Android Passkey & Password Provider ([CredentialProviderService], Android 14+ / API 34+).
 *
 * Security invariants:
 * - Zero cloud servers: all ECDSA P-256 passkey private keys live encrypted inside Aran's local
 *   AES-256-GCM SQLite vault.
 * - When the vault is locked, returns an [AuthenticationAction] launching [PasskeyAuthActivity]
 *   (requiring BiometricPrompt CryptoObject or Master Password unlock before inspecting records).
 * - When unlocked, matches passkeys strictly by `rpId` and passwords strictly via [OriginMatcher],
 *   always requiring explicit user confirmation inside [PasskeyAuthActivity] before signing.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AranCredentialProviderService : CredentialProviderService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        val repository = VaultRuntime.getRepository(this)
        serviceScope.launch {
            if (!repository.isStorageInitialized.value) {
                repository.initializeFromDisk()
            }
            if (cancellationSignal.isCanceled) return@launch

            if (!repository.hasVault.value) {
                callback.onError(CreateCredentialUnknownException("Create an Aran vault first to save passkeys offline."))
                return@launch
            }

            val response = when (request) {
                is BeginCreatePublicKeyCredentialRequest -> buildCreatePasskeyResponse(request)
                is BeginCreatePasswordCredentialRequest -> buildCreatePasswordResponse()
                else -> null
            }

            if (response != null) {
                callback.onResult(response)
            } else {
                callback.onError(CreateCredentialUnknownException("Unsupported credential creation type."))
            }
        }
    }

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        val repository = VaultRuntime.getRepository(this)
        serviceScope.launch {
            if (!repository.isStorageInitialized.value) {
                repository.initializeFromDisk()
            }
            if (cancellationSignal.isCanceled) return@launch

            if (!repository.hasVault.value) {
                callback.onResult(BeginGetCredentialResponse.Builder().build())
                return@launch
            }

            if (!repository.isUnlocked.value) {
                val unlockIntent = PasskeyAuthActivity.createUnlockForGetIntent(this@AranCredentialProviderService)
                val pendingIntent = PendingIntent.getActivity(
                    this@AranCredentialProviderService,
                    REQUEST_CODE_UNLOCK_FOR_GET,
                    unlockIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                val authAction = AuthenticationAction(
                    title = "Unlock Aran Offline Vault",
                    pendingIntent = pendingIntent,
                )
                callback.onResult(
                    BeginGetCredentialResponse.Builder()
                        .setAuthenticationActions(listOf(authAction))
                        .build()
                )
                return@launch
            }

            val response = buildUnlockedBeginGetResponse(
                context = this@AranCredentialProviderService,
                request = request,
                items = repository.items.value,
                allowInsecureHttp = repository.settings.value.allowInsecureHttpAutofill,
            )
            callback.onResult(response)
        }
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        // Aran keeps no sticky per-RP selection state.
        callback.onResult(null)
    }

    private fun buildCreatePasskeyResponse(
        request: BeginCreatePublicKeyCredentialRequest,
    ): BeginCreateCredentialResponse {
        val rpName = runCatching {
            val json = JSONObject(request.requestJson)
            val rp = json.optJSONObject("rp")
            rp?.optString("name")?.takeIf { it.isNotBlank() }
                ?: rp?.optString("id")?.takeIf { it.isNotBlank() }
                ?: "Offline Passkey"
        }.getOrDefault("Offline Passkey")

        val intent = PasskeyAuthActivity.createPasskeyRegistrationIntent(this)
        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_CREATE_PASSKEY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val entry = CreateEntry.Builder(
            accountName = "Aran Offline Vault",
            pendingIntent = pendingIntent,
        )
            .setDescription("Save ES256 passkey for $rpName locally (AES-256-GCM)")
            .build()

        return BeginCreateCredentialResponse.Builder()
            .setCreateEntries(listOf(entry))
            .build()
    }

    private fun buildCreatePasswordResponse(): BeginCreateCredentialResponse {
        val intent = PasskeyAuthActivity.createPasswordSaveIntent(this)
        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_CREATE_PASSWORD,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val entry = CreateEntry.Builder(
            accountName = "Aran Offline Vault",
            pendingIntent = pendingIntent,
        )
            .setDescription("Encrypt password locally in Aran SQLite vault")
            .build()

        return BeginCreateCredentialResponse.Builder()
            .setCreateEntries(listOf(entry))
            .build()
    }

    companion object {
        private const val REQUEST_CODE_CREATE_PASSKEY = 5201
        private const val REQUEST_CODE_CREATE_PASSWORD = 5202
        private const val REQUEST_CODE_UNLOCK_FOR_GET = 5203
        private const val REQUEST_CODE_GET_BASE = 5300

        /**
         * Shared helper used by both [AranCredentialProviderService] (when already unlocked)
         * and [PasskeyAuthActivity] (immediately after unlocking via [AuthenticationAction]).
         */
        fun buildUnlockedBeginGetResponse(
            context: Context,
            request: BeginGetCredentialRequest,
            items: List<VaultItem>,
            allowInsecureHttp: Boolean,
        ): BeginGetCredentialResponse {
            val entries = ArrayList<CredentialEntry>()
            val callingPkg = request.callingAppInfo?.packageName.orEmpty()
            var nextReqCode = REQUEST_CODE_GET_BASE

            for (option in request.beginGetCredentialOptions) {
                when (option) {
                    is BeginGetPublicKeyCredentialOption -> {
                        val parsed = runCatching { JSONObject(option.requestJson) }.getOrNull()
                        val rpId = parsed?.optString("rpId").orEmpty()
                        val allowIds = HashSet<String>()
                        val allowArray = parsed?.optJSONArray("allowCredentials")
                        if (allowArray != null) {
                            for (i in 0 until allowArray.length()) {
                                val id = allowArray.optJSONObject(i)?.optString("id").orEmpty()
                                if (id.isNotBlank()) allowIds.add(id)
                            }
                        }

                        val matchingPasskeys = OfflinePasskeyEngine.extractMatchingPasskeys(
                            items = items,
                            rpId = rpId,
                            allowCredentialIds = allowIds,
                        )
                        for ((item, passkey) in matchingPasskeys) {
                            val intent = PasskeyAuthActivity.createPasskeyAssertionIntent(
                                context = context,
                                vaultItemId = item.id,
                                credentialIdB64Url = passkey.credentialIdB64Url,
                            )
                            val pendingIntent = PendingIntent.getActivity(
                                context,
                                nextReqCode++,
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                            )
                            val entry = PublicKeyCredentialEntry.Builder(
                                context = context,
                                username = passkey.userName.ifBlank { item.title },
                                pendingIntent = pendingIntent,
                                beginGetPublicKeyCredentialOption = option,
                            )
                                .setDisplayName(passkey.userDisplayName.ifBlank { passkey.rpName })
                                .build()
                            entries.add(entry)
                        }
                    }

                    is BeginGetPasswordOption -> {
                        if (callingPkg.isNotBlank()) {
                            val digests = AppSignatureVerifier.getSigningCertSha256HexDigests(context, callingPkg)
                            val decision = OriginMatcher.matchCredentials(
                                target = AutofillTargetIdentity(
                                    packageName = callingPkg,
                                    signingCertSha256Digests = digests,
                                ),
                                items = items,
                                ownPackageName = context.packageName,
                                allowInsecureHttp = allowInsecureHttp,
                            )
                            if (decision is AutofillMatchDecision.Allowed) {
                                for (match in decision.matches) {
                                    if (option.allowedUserIds.isNotEmpty() &&
                                        match.item.username !in option.allowedUserIds
                                    ) {
                                        continue
                                    }
                                    val intent = PasskeyAuthActivity.createPasswordGetIntent(
                                        context = context,
                                        vaultItemId = match.item.id,
                                    )
                                    val pendingIntent = PendingIntent.getActivity(
                                        context,
                                        nextReqCode++,
                                        intent,
                                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                                    )
                                    val entry = PasswordCredentialEntry.Builder(
                                        context = context,
                                        username = match.item.username.ifBlank { match.item.title },
                                        pendingIntent = pendingIntent,
                                        beginGetPasswordOption = option,
                                    )
                                        .setDisplayName(match.item.title)
                                        .build()
                                    entries.add(entry)
                                }
                            }
                        }
                    }
                }
            }

            return BeginGetCredentialResponse.Builder()
                .setCredentialEntries(entries)
                .build()
        }
    }
}
