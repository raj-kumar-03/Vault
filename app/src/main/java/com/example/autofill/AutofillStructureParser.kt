package com.example.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * Pure-data representation of an inspectable form node, decoupling Android [AssistStructure]
 * traversal from security classification logic so adversarial forms can be unit-tested cleanly.
 */
data class InspectableFormNode(
    val nodeKey: String,
    val isVisible: Boolean,
    val autofillHints: List<String> = emptyList(),
    val htmlTag: String? = null,
    val htmlInputType: String? = null,
    val htmlAutocomplete: String? = null,
    val htmlName: String? = null,
    val inputType: Int = 0,
    val idEntry: String? = null,
    val hint: String? = null,
    val webDomain: String? = null,
    val webScheme: String? = null,
    val textValue: String? = null,
)

data class ParsedLoginForm(
    val usernameNodeKey: String?,
    val passwordNodeKey: String?,
    val isNewPasswordForm: Boolean,
    val usernameValue: String?,
    val passwordValue: String?,
    val webDomain: String?,
    val webScheme: String?,
    val hasConflictingWebOrigins: Boolean,
    val ignoredHiddenFieldCount: Int,
) {
    val hasFillableFields: Boolean
        get() = usernameNodeKey != null || passwordNodeKey != null
}

@RequiresApi(Build.VERSION_CODES.O)
data class ParsedAutofillStructure(
    val packageName: String,
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    val form: ParsedLoginForm,
) {
    val hasFillableFields: Boolean
        get() = usernameId != null || passwordId != null
}

/**
 * Minimal, security-hardened AssistStructure parser for Phase 5.
 *
 * Security properties:
 * - Parses ONLY relevant credential fields (username/email/password); ignores unrelated fields.
 * - Rejects invisible / hidden fields (`visibility != View.VISIBLE` or `type="hidden"`) so
 *   malicious pages cannot siphon credentials into off-screen inputs.
 * - Tracks all distinct web domains across the window tree; if conflicting domains appear (e.g.,
 *   cross-origin embedded iframe), flags `hasConflictingWebOrigins = true`.
 */
object AutofillStructureParser {

    @RequiresApi(Build.VERSION_CODES.O)
    fun parseAssistStructure(
        structure: AssistStructure,
        fallbackPackageName: String,
    ): ParsedAutofillStructure {
        val nodes = ArrayList<InspectableFormNode>()
        val idByKey = HashMap<String, AutofillId>()
        var counter = 0

        val activityPackage = structure.activityComponent?.packageName?.takeIf { it.isNotBlank() }
            ?: fallbackPackageName

        fun traverse(node: AssistStructure.ViewNode, inheritedDomain: String?, inheritedScheme: String?) {
            val nodeDomain = node.webDomain?.takeIf { it.isNotBlank() } ?: inheritedDomain
            val nodeScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                node.webScheme?.takeIf { it.isNotBlank() } ?: inheritedScheme
            } else {
                inheritedScheme
            }

            val afId = node.autofillId
            if (afId != null) {
                val key = "n-${counter++}"
                idByKey[key] = afId
                val htmlAttrs = node.htmlInfo?.attributes
                    ?.associate { it.first.lowercase(Locale.US) to it.second }
                    .orEmpty()
                nodes.add(
                    InspectableFormNode(
                        nodeKey = key,
                        isVisible = node.visibility == View.VISIBLE,
                        autofillHints = node.autofillHints?.toList().orEmpty(),
                        htmlTag = node.htmlInfo?.tag?.lowercase(Locale.US),
                        htmlInputType = htmlAttrs["type"]?.lowercase(Locale.US),
                        htmlAutocomplete = htmlAttrs["autocomplete"]?.lowercase(Locale.US),
                        htmlName = htmlAttrs["name"]?.lowercase(Locale.US),
                        inputType = node.inputType,
                        idEntry = node.idEntry?.lowercase(Locale.US),
                        hint = node.hint?.lowercase(Locale.US),
                        webDomain = nodeDomain,
                        webScheme = nodeScheme,
                        textValue = node.autofillValue?.takeIf { it.isText }?.textValue?.toString()
                            ?: node.text?.toString(),
                    )
                )
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChildAt(i), nodeDomain, nodeScheme)
            }
        }

        for (i in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(i).rootViewNode
            if (root != null) {
                traverse(root, null, null)
            }
        }

        val form = parseNodes(nodes)
        return ParsedAutofillStructure(
            packageName = activityPackage,
            usernameId = form.usernameNodeKey?.let { idByKey[it] },
            passwordId = form.passwordNodeKey?.let { idByKey[it] },
            form = form,
        )
    }

    fun parseNodes(nodes: List<InspectableFormNode>): ParsedLoginForm {
        var usernameNode: InspectableFormNode? = null
        var passwordNode: InspectableFormNode? = null
        var isNewPassword = false
        var ignoredHiddenCount = 0

        val observedDomains = LinkedHashSet<String>()
        var observedScheme: String? = null

        for (node in nodes) {
            val normDomain = OriginMatcher.normalizeHost(node.webDomain)
            if (normDomain != null) {
                observedDomains.add(normDomain)
                if (observedScheme == null && !node.webScheme.isNullOrBlank()) {
                    observedScheme = node.webScheme.lowercase(Locale.US)
                }
            }

            val isHidden = !node.isVisible || node.htmlInputType == "hidden"
            val classification = classifyField(node)
            if (classification != FieldKind.NONE && isHidden) {
                ignoredHiddenCount++
                continue
            }

            when (classification) {
                FieldKind.PASSWORD -> {
                    if (passwordNode == null) passwordNode = node
                }

                FieldKind.NEW_PASSWORD -> {
                    if (passwordNode == null) {
                        passwordNode = node
                        isNewPassword = true
                    }
                }

                FieldKind.USERNAME -> {
                    if (usernameNode == null) usernameNode = node
                }

                FieldKind.NONE -> Unit
            }
        }

        val conflictingOrigins = observedDomains.size > 1
        return ParsedLoginForm(
            usernameNodeKey = usernameNode?.nodeKey,
            passwordNodeKey = passwordNode?.nodeKey,
            isNewPasswordForm = isNewPassword,
            usernameValue = usernameNode?.textValue?.takeIf { it.isNotBlank() },
            passwordValue = passwordNode?.textValue?.takeIf { it.isNotEmpty() },
            webDomain = observedDomains.firstOrNull(),
            webScheme = observedScheme,
            hasConflictingWebOrigins = conflictingOrigins,
            ignoredHiddenFieldCount = ignoredHiddenCount,
        )
    }

    private enum class FieldKind { NONE, USERNAME, PASSWORD, NEW_PASSWORD }

    private fun classifyField(node: InspectableFormNode): FieldKind {
        val hints = node.autofillHints.map { it.lowercase(Locale.US) }
        val ac = node.htmlAutocomplete.orEmpty()

        // 1. Explicit Autofill hints & HTML autocomplete attributes (highest confidence)
        if ("newpassword" in hints || ac.contains("new-password")) return FieldKind.NEW_PASSWORD
        if (View.AUTOFILL_HINT_PASSWORD in hints || ac.contains("current-password")) return FieldKind.PASSWORD
        if (
            View.AUTOFILL_HINT_USERNAME in hints ||
            View.AUTOFILL_HINT_EMAIL_ADDRESS in hints ||
            ac.contains("username") ||
            ac == "email"
        ) {
            return FieldKind.USERNAME
        }

        // 2. HTML input types
        if (node.htmlInputType == "password") return FieldKind.PASSWORD
        if (node.htmlInputType == "email") return FieldKind.USERNAME

        // 3. Android InputType password variations
        val variation = node.inputType and InputType.TYPE_MASK_VARIATION
        val isPasswordInput = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        if (isPasswordInput) return FieldKind.PASSWORD

        // 4. Conservative heuristic on idEntry / htmlName / hint (only for text-like inputs)
        val isTextLike = node.htmlTag == "input" || (node.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        if (!isTextLike) return FieldKind.NONE

        val combinedMeta = listOfNotNull(node.idEntry, node.htmlName, node.hint).joinToString(" ")
        return when {
            combinedMeta.contains("password") || combinedMeta.contains("passphrase") -> FieldKind.PASSWORD
            combinedMeta.contains("email") ||
                combinedMeta.contains("username") ||
                combinedMeta.contains("login") ||
                combinedMeta.contains("account") -> FieldKind.USERNAME
            else -> FieldKind.NONE
        }
    }
}
