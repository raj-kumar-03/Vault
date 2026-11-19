package com.example.ui.navigation

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.domain.model.ClipboardClearInterval
import com.example.ui.components.AppScaffold
import com.example.ui.components.PrimaryDestination
import com.example.ui.screens.AddEditItemScreen
import com.example.ui.screens.CreateVaultScreen
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.GeneratorScreen
import com.example.ui.screens.ItemDetailScreen
import com.example.ui.screens.PasswordHealthScreen
import com.example.ui.screens.RecoveryKeyCreatedSheet
import com.example.ui.screens.RestoreBackupSheet
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UnlockScreen
import com.example.ui.screens.VaultHomeScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.state.VaultViewModel
import com.example.ui.theme.SecureVaultTheme
import com.example.ui.theme.VaultTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VaultRoutes {
    const val ROUTE_WELCOME = "welcome"
    const val ROUTE_CREATE_VAULT = "create_vault"
    const val ROUTE_UNLOCK = "unlock"
    const val ROUTE_MAIN = "main"
    const val ROUTE_HEALTH = "health"
    const val ROUTE_DETAIL = "detail/{itemId}"
    const val ROUTE_EDITOR_NEW = "editor/new"
    const val ROUTE_EDITOR_EDIT = "editor/{itemId}"

    fun detail(itemId: String): String = "detail/$itemId"
    fun edit(itemId: String): String = "editor/$itemId"
}

private const val MAX_IMPORT_BACKUP_BYTES = 16 * 1024 * 1024

@Composable
fun SecureVaultApp(
    viewModel: VaultViewModel? = null,
) {
    val context = LocalContext.current
    val resolvedViewModel: VaultViewModel = viewModel ?: viewModel(
        factory = VaultViewModel.factory(context.applicationContext),
    )

    val state by resolvedViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var activeTab by remember { mutableStateOf(PrimaryDestination.VAULT) }
    var clipboardClearJob by remember { mutableStateOf<Job?>(null) }
    var stagedExportBytes by remember { mutableStateOf<ByteArray?>(null) }

    DisposableEffect(state.settings.hideAppPreviewEnabled, context) {
        val window = (context as? Activity)?.window
        if (window != null) {
            if (state.settings.hideAppPreviewEnabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        onDispose {}
    }

    fun showTransientMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
        }
    }

    // Zero-permission Android Storage Access Framework (SAF) launcher for .svbk export
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val bytesToWrite = stagedExportBytes
        stagedExportBytes = null
        if (uri == null || bytesToWrite == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val wroteOk = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(bytesToWrite)
                        out.flush()
                    } ?: error("Output stream unavailable")
                }.isSuccess
            }
            bytesToWrite.fill(0)
            showTransientMessage(
                if (wroteOk) "Encrypted backup (.svbk) saved offline"
                else "Unable to save encrypted backup file"
            )
        }
    }

    // Zero-permission Android Storage Access Framework (SAF) launcher for .svbk restore
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val raw = input.readBytes()
                        if (raw.size > MAX_IMPORT_BACKUP_BYTES) null else raw
                    }
                }.getOrNull()
            }
            if (bytes == null) {
                showTransientMessage("Unable to read selected backup file")
            } else {
                resolvedViewModel.inspectBackupFile(bytes) { errorMsg ->
                    showTransientMessage(errorMsg)
                }
            }
        }
    }

    fun startBackupExport() {
        resolvedViewModel.exportEncryptedBackup { bytes ->
            if (bytes == null) {
                showTransientMessage("Unable to prepare encrypted backup")
                return@exportEncryptedBackup
            }
            stagedExportBytes = bytes
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            exportBackupLauncher.launch("aran-backup-$stamp.svbk")
        }
    }

    fun startBackupImport() {
        importBackupLauncher.launch(arrayOf("application/octet-stream", "*/*"))
    }

    fun copyToClipboardSafely(label: String, value: String, isSecret: Boolean) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            val clip = ClipData.newPlainText(label, value)
            if (isSecret && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            clipboard.setPrimaryClip(clip)
        }

        val clearInterval = state.settings.clipboardClearInterval
        clipboardClearJob?.cancel()
        if (isSecret && clearInterval != ClipboardClearInterval.OFF) {
            val delayMs = when (clearInterval) {
                ClipboardClearInterval.THIRTY_SECONDS -> 30_000L
                ClipboardClearInterval.ONE_MINUTE -> 60_000L
                ClipboardClearInterval.OFF -> 0L
            }
            if (delayMs > 0L) {
                clipboardClearJob = coroutineScope.launch {
                    delay(delayMs)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard?.clearPrimaryClip()
                    } else {
                        clipboard?.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            }
        }

        val message = if (isSecret && clearInterval != ClipboardClearInterval.OFF) {
            "$label copied • Best-effort clear in ${clearInterval.label}"
        } else {
            "$label copied"
        }
        showTransientMessage(message)
    }

    fun lockAndNavigate() {
        resolvedViewModel.lockVault()
        navController.navigate(VaultRoutes.ROUTE_UNLOCK) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    SecureVaultTheme(themePreference = state.settings.themePreference) {
        if (!state.isStorageInitialized) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VaultTheme.colors.background),
            )
            return@SecureVaultTheme
        }

        val initialDestination = remember {
            if (state.hasVault) VaultRoutes.ROUTE_UNLOCK else VaultRoutes.ROUTE_WELCOME
        }

        NavHost(
            navController = navController,
            startDestination = initialDestination,
        ) {
            composable(VaultRoutes.ROUTE_WELCOME) {
                WelcomeScreen(
                    isBusy = state.isCryptoBusy,
                    onCreateVault = {
                        navController.navigate(VaultRoutes.ROUTE_CREATE_VAULT)
                    },
                    onExploreDemoVault = {
                        resolvedViewModel.openDemoVault {
                            activeTab = PrimaryDestination.VAULT
                            navController.navigate(VaultRoutes.ROUTE_MAIN) {
                                popUpTo(VaultRoutes.ROUTE_WELCOME) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onRestoreBackup = { startBackupImport() },
                )
            }

            composable(VaultRoutes.ROUTE_CREATE_VAULT) {
                CreateVaultScreen(
                    isBusy = state.isCryptoBusy,
                    onBack = { navController.popBackStack() },
                    onCreateVaultConfirmed = { masterPassword ->
                        resolvedViewModel.createVault(masterPassword) {
                            activeTab = PrimaryDestination.VAULT
                            navController.navigate(VaultRoutes.ROUTE_MAIN) {
                                popUpTo(VaultRoutes.ROUTE_WELCOME) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }

            composable(VaultRoutes.ROUTE_UNLOCK) {
                UnlockScreen(
                    biometricEnabled = state.settings.biometricUnlockEnabled,
                    isBusy = state.isCryptoBusy,
                    onUnlockWithPassword = { candidate, onResult ->
                        resolvedViewModel.attemptUnlock(candidate) { success ->
                            if (success) {
                                navController.navigate(VaultRoutes.ROUTE_MAIN) {
                                    popUpTo(VaultRoutes.ROUTE_UNLOCK) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                            onResult(success)
                        }
                    },
                    onUnlockWithBiometric = { onResult ->
                        resolvedViewModel.attemptBiometricUnlock { success ->
                            if (success) {
                                navController.navigate(VaultRoutes.ROUTE_MAIN) {
                                    popUpTo(VaultRoutes.ROUTE_UNLOCK) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                            onResult(success)
                        }
                    },
                    onRecoverWithKey = { recoveryKey, newMasterPassword, onResult ->
                        resolvedViewModel.recoverWithKey(recoveryKey, newMasterPassword) { success ->
                            if (success) {
                                navController.navigate(VaultRoutes.ROUTE_MAIN) {
                                    popUpTo(VaultRoutes.ROUTE_UNLOCK) { inclusive = true }
                                    launchSingleTop = true
                                }
                                showTransientMessage("Vault recovered • VEK re-wrapped under new master password")
                            }
                            onResult(success)
                        }
                    },
                    onReturnToWelcome = {
                        navController.navigate(VaultRoutes.ROUTE_WELCOME) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(VaultRoutes.ROUTE_MAIN) {
                val colors = VaultTheme.colors
                AppScaffold(
                    currentDestination = activeTab,
                    onSelectDestination = { destination -> activeTab = destination },
                    onLockVault = { lockAndNavigate() },
                    snackbarHostState = snackbarHostState,
                    floatingActionButton = {
                        if (activeTab == PrimaryDestination.VAULT) {
                            FloatingActionButton(
                                onClick = { navController.navigate(VaultRoutes.ROUTE_EDITOR_NEW) },
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("vault_fab_add"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = stringResource(R.string.cd_add_item),
                                )
                            }
                        }
                    },
                ) { isExpandedWidth ->
                    when (activeTab) {
                        PrimaryDestination.VAULT -> {
                            VaultHomeScreen(
                                state = state,
                                isExpandedWidth = isExpandedWidth,
                                onSearchQueryChange = resolvedViewModel::updateSearchQuery,
                                onSelectCategory = resolvedViewModel::selectCategory,
                                onOpenItem = { item ->
                                    resolvedViewModel.selectItem(item.id)
                                    if (!isExpandedWidth) {
                                        navController.navigate(VaultRoutes.detail(item.id))
                                    }
                                },
                                onToggleFavorite = resolvedViewModel::toggleFavorite,
                                onAddItem = { navController.navigate(VaultRoutes.ROUTE_EDITOR_NEW) },
                                onOpenGenerator = { activeTab = PrimaryDestination.GENERATOR },
                                onOpenHealth = { navController.navigate(VaultRoutes.ROUTE_HEALTH) },
                                onLockVault = { lockAndNavigate() },
                                onEditItem = { itemId ->
                                    navController.navigate(VaultRoutes.edit(itemId))
                                },
                                onDeleteItem = { itemId ->
                                    resolvedViewModel.deleteItem(itemId)
                                    showTransientMessage("Encrypted record removed from SQLite vault")
                                },
                                onCopyField = { label, value, isSecret ->
                                    copyToClipboardSafely(label, value, isSecret)
                                },
                            )
                        }

                        PrimaryDestination.GENERATOR -> {
                            GeneratorScreen(
                                state = state.generator,
                                onModeChange = resolvedViewModel::updateGeneratorMode,
                                onConfigChange = resolvedViewModel::updateGeneratorConfig,
                                onRegenerate = resolvedViewModel::regeneratePassword,
                                onCopyGenerated = { generated ->
                                    copyToClipboardSafely("Generated secret", generated, true)
                                },
                                onUseInNewLogin = { generated ->
                                    resolvedViewModel.stageGeneratedPasswordForNewItem(generated)
                                    navController.navigate(VaultRoutes.ROUTE_EDITOR_NEW)
                                },
                            )
                        }

                        PrimaryDestination.DEVICES -> {
                            DevicesScreen(
                                devices = state.devices,
                                onRevokeDevice = { deviceId ->
                                    resolvedViewModel.revokeDevice(deviceId)
                                    showTransientMessage("Mock device marked revoked")
                                },
                            )
                        }

                        PrimaryDestination.SETTINGS -> {
                            SettingsScreen(
                                settings = state.settings,
                                cryptoMetadata = state.cryptoMetadata,
                                isCryptoBusy = state.isCryptoBusy,
                                onAutoLockChange = resolvedViewModel::updateAutoLock,
                                onBiometricChange = resolvedViewModel::updateBiometricUnlock,
                                onClipboardClearChange = resolvedViewModel::updateClipboardClear,
                                onHideAppPreviewChange = resolvedViewModel::updateHideAppPreview,
                                onThemeChange = resolvedViewModel::updateThemePreference,
                                onChangeMasterPassword = { current, newPw, onResult ->
                                    resolvedViewModel.changeMasterPassword(current, newPw) { ok ->
                                        if (ok) {
                                            showTransientMessage("Master password updated • VEK re-wrapped (0 records re-encrypted)")
                                        }
                                        onResult(ok)
                                    }
                                },
                                onRunKdfBenchmark = {
                                    resolvedViewModel.runKdfBenchmark { result ->
                                        showTransientMessage("Argon2id measured: ${result.durationMs} ms")
                                    }
                                },
                                onExportEncryptedBackup = { startBackupExport() },
                                onRestoreEncryptedBackup = { startBackupImport() },
                                onLockVaultNow = { lockAndNavigate() },
                            )
                        }
                    }
                }

                // Show one-time independent Recovery Key sheet immediately after Create Vault.
                state.pendingRecoveryKeyReveal?.let { recoveryKey ->
                    RecoveryKeyCreatedSheet(
                        recoveryKey = recoveryKey,
                        onCopyRecoveryKey = { key ->
                            copyToClipboardSafely("Recovery key", key, true)
                        },
                        onConfirmedSaved = resolvedViewModel::dismissRecoveryKeyReveal,
                    )
                }
            }

            composable(VaultRoutes.ROUTE_HEALTH) {
                PasswordHealthScreen(
                    items = state.items,
                    cryptoMetadata = state.cryptoMetadata,
                    onBack = { navController.popBackStack() },
                    onOpenItem = { item ->
                        resolvedViewModel.selectItem(item.id)
                        navController.navigate(VaultRoutes.detail(item.id))
                    },
                )
            }

            composable(
                route = VaultRoutes.ROUTE_DETAIL,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId")
                val item = state.items.firstOrNull { it.id == itemId }
                ItemDetailScreen(
                    item = item,
                    onBack = { navController.popBackStack() },
                    onToggleFavorite = {
                        if (item != null) resolvedViewModel.toggleFavorite(item.id)
                    },
                    onEdit = {
                        if (item != null) navController.navigate(VaultRoutes.edit(item.id))
                    },
                    onDeleteConfirmed = {
                        if (item != null) {
                            resolvedViewModel.deleteItem(item.id)
                            navController.popBackStack()
                            showTransientMessage("Encrypted record removed from SQLite vault")
                        }
                    },
                    onCopyField = { label, value, isSecret ->
                        copyToClipboardSafely(label, value, isSecret)
                    },
                )
            }

            composable(VaultRoutes.ROUTE_EDITOR_NEW) {
                val stagedPassword = remember { resolvedViewModel.consumePendingGeneratedPassword() }
                AddEditItemScreen(
                    existingItem = null,
                    initialGeneratedPassword = stagedPassword,
                    onCancel = { navController.popBackStack() },
                    onSave = { newItem ->
                        resolvedViewModel.saveItem(newItem) {
                            activeTab = PrimaryDestination.VAULT
                            navController.popBackStack()
                            showTransientMessage("Encrypted and persisted to local SQLite")
                        }
                    },
                )
            }

            composable(
                route = VaultRoutes.ROUTE_EDITOR_EDIT,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId")
                val existing = remember(itemId) { resolvedViewModel.findItemById(itemId) }
                AddEditItemScreen(
                    existingItem = existing,
                    initialGeneratedPassword = null,
                    onCancel = { navController.popBackStack() },
                    onSave = { updated ->
                        resolvedViewModel.saveItem(updated) {
                            navController.popBackStack()
                            showTransientMessage("Encrypted changes persisted")
                        }
                    },
                )
            }
        }

        // Global SHA-256 verified encrypted backup restore sheet (accessible from Welcome & Settings)
        state.pendingBackupRestore?.let { pending ->
            RestoreBackupSheet(
                summary = pending.summary,
                willReplaceExistingVault = state.hasVault,
                isBusy = state.isCryptoBusy,
                onDismiss = resolvedViewModel::dismissBackupRestore,
                onRestoreWithPassword = { masterPassword, onResult ->
                    resolvedViewModel.restorePendingBackupWithPassword(masterPassword) { ok ->
                        if (ok) {
                            activeTab = PrimaryDestination.VAULT
                            navController.navigate(VaultRoutes.ROUTE_MAIN) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                            showTransientMessage("Encrypted backup verified and restored to SQLite")
                        }
                        onResult(ok)
                    }
                },
                onRestoreWithRecoveryKey = { recoveryKey, newPassword, onResult ->
                    resolvedViewModel.restorePendingBackupWithRecoveryKey(recoveryKey, newPassword) { ok ->
                        if (ok) {
                            activeTab = PrimaryDestination.VAULT
                            navController.navigate(VaultRoutes.ROUTE_MAIN) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                            showTransientMessage("Backup recovered and restored under new master password")
                        }
                        onResult(ok)
                    }
                },
            )
        }
    }
}
