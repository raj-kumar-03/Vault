package com.example.data

import android.content.Context
import com.example.data.local.VaultDatabase
import com.example.security.AndroidBiometricKeystoreManager

/**
 * Process-scoped holder sharing a single [PersistentVaultRepository] instance across
 * [com.example.MainActivity], [com.example.autofill.AranAutofillService],
 * [com.example.autofill.AutofillAuthActivity], [com.example.passkey.AranCredentialProviderService],
 * and [com.example.passkey.PasskeyAuthActivity].
 *
 * Ensures that locking the vault wipes the in-memory VEK everywhere immediately, and unlocking via
 * BiometricPrompt or master password in any entry point keeps session state strictly coherent.
 */
object VaultRuntime {

    @Volatile
    private var sharedRepository: PersistentVaultRepository? = null

    fun getRepository(context: Context): PersistentVaultRepository {
        return sharedRepository ?: synchronized(this) {
            sharedRepository ?: createRepository(context.applicationContext).also {
                sharedRepository = it
            }
        }
    }

    private fun createRepository(appContext: Context): PersistentVaultRepository {
        val db = VaultDatabase.getInstance(appContext)
        val keystore = AndroidBiometricKeystoreManager(appContext)
        return PersistentVaultRepository(
            vaultDao = db.vaultDao(),
            keystoreBridge = keystore,
        )
    }
}
