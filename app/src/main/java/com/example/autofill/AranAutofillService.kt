package com.example.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.example.R
import com.example.data.VaultRuntime
import com.example.domain.model.VaultItem
import com.example.domain.model.VaultItemType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Phase 5 Android [AutofillService].
 *
 * Security invariants:
 * - Parses only relevant visible login fields via [AutofillStructureParser].
 * - Strictly enforces [OriginMatcher] (HTTPS host/registrable domain + APK SHA-256 certificate
 *   verification + default insecure HTTP blocking + mixed-origin rejection).
 * - Never reveals credentials while the vault is locked: returns an authenticated PendingIntent
 *   to [AutofillAuthActivity] requiring BiometricPrompt CryptoObject or Master Password unlock.
 * - Never logs usernames, passwords, or form contents.
 */
@RequiresApi(Build.VERSION_CODES.O)
class AranAutofillService : AutofillService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        val parsed = AutofillStructureParser.parseAssistStructure(
            structure = structure,
            fallbackPackageName = structure.activityComponent?.packageName.orEmpty(),
        )
        if (!parsed.hasFillableFields) {
            callback.onSuccess(null)
            return
        }

        // Never autofill into Aran itself.
        if (parsed.packageName.equals(packageName, ignoreCase = true)) {
            callback.onSuccess(null)
            return
        }

        val signingDigests = if (!parsed.form.webDomain.isNullOrBlank()) {
            emptySet()
        } else {
            AppSignatureVerifier.getSigningCertSha256HexDigests(this, parsed.packageName)
        }

        val targetIdentity = AutofillTargetIdentity(
            packageName = parsed.packageName,
            webDomain = parsed.form.webDomain,
            webScheme = parsed.form.webScheme,
            signingCertSha256Digests = signingDigests,
            hasConflictingWebOrigins = parsed.form.hasConflictingWebOrigins,
        )

        val repository = VaultRuntime.getRepository(this)
        serviceScope.launch {
            if (!repository.isStorageInitialized.value) {
                repository.initializeFromDisk()
            }
            if (cancellationSignal.isCanceled) return@launch

            if (!repository.hasVault.value) {
                callback.onSuccess(null)
                return@launch
            }

            val allowHttp = repository.settings.value.allowInsecureHttpAutofill
            // Pre-check security violations & HTTP blocks even before offering unlock prompt.
            if (targetIdentity.hasConflictingWebOrigins) {
                callback.onSuccess(null)
                return@launch
            }
            if (targetIdentity.isWebContext &&
                targetIdentity.webScheme.equals("http", ignoreCase = true) &&
                !allowHttp
            ) {
                callback.onSuccess(null)
                return@launch
            }

            val responseBuilder = FillResponse.Builder()
            val allIds = listOfNotNull(parsed.usernameId, parsed.passwordId).toTypedArray()

            if (!repository.isUnlocked.value) {
                // Vault is locked: require explicit BiometricPrompt / Master Password authentication.
                val authIntent = AutofillAuthActivity.createIntent(
                    context = this@AranAutofillService,
                    packageName = parsed.packageName,
                    webDomain = parsed.form.webDomain,
                    webScheme = parsed.form.webScheme,
                    usernameId = parsed.usernameId,
                    passwordId = parsed.passwordId,
                    hasConflictingOrigins = parsed.form.hasConflictingWebOrigins,
                )
                val pendingIntent = PendingIntent.getActivity(
                    this@AranAutofillService,
                    REQUEST_CODE_AUTH_UNLOCK,
                    authIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                val presentation = buildPresentation(
                    title = "Unlock Aran Vault",
                    subtitle = parsed.form.webDomain ?: parsed.packageName,
                )
                responseBuilder.setAuthentication(allIds, pendingIntent.intentSender, presentation)
                addSaveInfoIfApplicable(responseBuilder, parsed.usernameId, parsed.passwordId)
                callback.onSuccess(responseBuilder.build())
                return@launch
            }

            // Vault is unlocked: apply strict OriginMatcher.
            val decision = OriginMatcher.matchCredentials(
                target = targetIdentity,
                items = repository.items.value,
                ownPackageName = packageName,
                allowInsecureHttp = allowHttp,
            )

            when (decision) {
                is AutofillMatchDecision.BlockedInsecureHttp,
                is AutofillMatchDecision.BlockedSecurityViolation -> {
                    callback.onSuccess(null)
                    return@launch
                }

                is AutofillMatchDecision.Allowed -> {
                    for (match in decision.matches.take(MAX_DIRECT_DATASETS)) {
                        val subtitle = if (match.requiresExplicitConfirmation) {
                            "${match.item.username.ifBlank { match.item.title }} • Confirm domain"
                        } else {
                            match.item.username.ifBlank { match.item.normalizedDomain }
                        }
                        val dataset = buildCredentialDataset(
                            item = match.item,
                            subtitle = subtitle,
                            usernameId = parsed.usernameId,
                            passwordId = parsed.passwordId,
                        )
                        if (dataset != null) {
                            responseBuilder.addDataset(dataset)
                        }
                    }

                    // Always provide an explicit Aran picker dataset when no exact match or user wants another entry.
                    if (decision.matches.isEmpty() || decision.requiresExplicitSelection) {
                        val pickerIntent = AutofillAuthActivity.createIntent(
                            context = this@AranAutofillService,
                            packageName = parsed.packageName,
                            webDomain = parsed.form.webDomain,
                            webScheme = parsed.form.webScheme,
                            usernameId = parsed.usernameId,
                            passwordId = parsed.passwordId,
                            hasConflictingOrigins = parsed.form.hasConflictingWebOrigins,
                        )
                        val pickerPending = PendingIntent.getActivity(
                            this@AranAutofillService,
                            REQUEST_CODE_PICKER,
                            pickerIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                        )
                        val pickerPresentation = buildPresentation(
                            title = if (decision.matches.isEmpty()) "Open Aran Vault…" else "Review & Confirm in Aran…",
                            subtitle = decision.verifiedOriginLabel,
                        )
                        val pickerDataset = Dataset.Builder(pickerPresentation)
                            .setAuthentication(pickerPending.intentSender)
                            .apply {
                                parsed.usernameId?.let { setValue(it, null) }
                                parsed.passwordId?.let { setValue(it, null) }
                            }
                            .build()
                        responseBuilder.addDataset(pickerDataset)
                    }

                    addSaveInfoIfApplicable(responseBuilder, parsed.usernameId, parsed.passwordId)
                    callback.onSuccess(responseBuilder.build())
                }
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess()
            return
        }
        val parsed = AutofillStructureParser.parseAssistStructure(
            structure = structure,
            fallbackPackageName = structure.activityComponent?.packageName.orEmpty(),
        )
        val password = parsed.form.passwordValue
        if (password.isNullOrEmpty() || parsed.packageName.equals(packageName, ignoreCase = true)) {
            callback.onSuccess()
            return
        }

        val repository = VaultRuntime.getRepository(this)
        serviceScope.launch {
            if (!repository.isStorageInitialized.value) {
                repository.initializeFromDisk()
            }
            if (!repository.isUnlocked.value) {
                callback.onFailure("Unlock Aran before saving new logins.")
                return@launch
            }

            val domain = parsed.form.webDomain
            val website = if (!domain.isNullOrBlank()) {
                val scheme = parsed.form.webScheme?.takeIf { it == "https" || it == "http" } ?: "https"
                "$scheme://$domain"
            } else {
                val cert = AppSignatureVerifier.getSigningCertSha256HexDigests(
                    this@AranAutofillService,
                    parsed.packageName,
                ).firstOrNull()
                if (cert != null) "androidapp://${parsed.packageName}?sha256=$cert"
                else "androidapp://${parsed.packageName}"
            }

            val title = domain ?: parsed.packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            val username = parsed.form.usernameValue.orEmpty()
            repository.upsertItem(
                VaultItem(
                    id = "",
                    type = VaultItemType.LOGIN,
                    title = title,
                    username = username,
                    secret = password,
                    website = website,
                    normalizedDomain = domain.orEmpty(),
                    notes = "Saved via Aran Autofill (encrypted locally with AES-256-GCM).",
                    isFavorite = false,
                    createdAtLabel = "Just now",
                    updatedAtLabel = "Just now",
                    lastUsedEpochMs = System.currentTimeMillis(),
                )
            )
            callback.onSuccess()
        }
    }

    private fun buildCredentialDataset(
        item: VaultItem,
        subtitle: String,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
    ): Dataset? {
        if (usernameId == null && passwordId == null) return null
        val presentation = buildPresentation(title = item.title, subtitle = subtitle)
        val builder = Dataset.Builder(presentation)
        if (usernameId != null && item.username.isNotBlank()) {
            builder.setValue(usernameId, AutofillValue.forText(item.username), presentation)
        }
        if (passwordId != null && item.secret.isNotEmpty()) {
            builder.setValue(passwordId, AutofillValue.forText(item.secret), presentation)
        }
        return builder.build()
    }

    private fun buildPresentation(title: String, subtitle: String): RemoteViews {
        return RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
            setTextViewText(android.R.id.text1, title)
            setTextViewText(android.R.id.text2, subtitle)
        }
    }

    private fun addSaveInfoIfApplicable(
        responseBuilder: FillResponse.Builder,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
    ) {
        val requiredIds = listOfNotNull(usernameId, passwordId).toTypedArray()
        if (requiredIds.isEmpty()) return
        val saveTypes = when {
            usernameId != null && passwordId != null ->
                SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
            passwordId != null -> SaveInfo.SAVE_DATA_TYPE_PASSWORD
            else -> SaveInfo.SAVE_DATA_TYPE_USERNAME
        }
        responseBuilder.setSaveInfo(SaveInfo.Builder(saveTypes, requiredIds).build())
    }

    companion object {
        private const val MAX_DIRECT_DATASETS = 5
        private const val REQUEST_CODE_AUTH_UNLOCK = 5101
        private const val REQUEST_CODE_PICKER = 5102
    }
}
