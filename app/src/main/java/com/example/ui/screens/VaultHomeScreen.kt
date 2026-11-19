package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.PasswordHealthAnalyzer
import com.example.domain.model.PasswordHealthReport
import com.example.domain.model.VaultCategoryFilter
import com.example.domain.model.VaultItem
import com.example.ui.components.AranIcons
import com.example.ui.components.CategoryChip
import com.example.ui.components.EmptyState
import com.example.ui.components.InlineAccentPill
import com.example.ui.components.InlineError
import com.example.ui.components.SearchField
import com.example.ui.components.ShieldEmblem
import com.example.ui.components.VaultItemRow
import com.example.ui.state.VaultUiState
import com.example.ui.theme.VaultTheme

@Composable
fun VaultHomeScreen(
    state: VaultUiState,
    isExpandedWidth: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (VaultCategoryFilter) -> Unit,
    onOpenItem: (VaultItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddItem: () -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenHealth: () -> Unit,
    onLockVault: () -> Unit,
    onEditItem: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onCopyField: (label: String, value: String, isSecret: Boolean) -> Unit,
) {
    val colors = VaultTheme.colors

    if (isExpandedWidth) {
        val selectedItem = state.items.firstOrNull { it.id == state.selectedItemId }
            ?: state.filteredItems.firstOrNull()
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
        ) {
            Box(
                modifier = Modifier
                    .weight(0.46f)
                    .fillMaxHeight(),
            ) {
                VaultListContent(
                    state = state,
                    selectedItemId = selectedItem?.id,
                    onSearchQueryChange = onSearchQueryChange,
                    onSelectCategory = onSelectCategory,
                    onOpenItem = onOpenItem,
                    onToggleFavorite = onToggleFavorite,
                    onAddItem = onAddItem,
                    onOpenGenerator = onOpenGenerator,
                    onOpenHealth = onOpenHealth,
                    onLockVault = onLockVault,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(colors.outlineSubtle),
            )

            Box(
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxHeight(),
            ) {
                if (selectedItem != null) {
                    ItemDetailContent(
                        item = selectedItem,
                        showBackButton = false,
                        onBack = {},
                        onToggleFavorite = { onToggleFavorite(selectedItem.id) },
                        onEdit = { onEditItem(selectedItem.id) },
                        onDeleteConfirmed = { onDeleteItem(selectedItem.id) },
                        onCopyField = onCopyField,
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                    ) {
                        EmptyState(
                            icon = AranIcons.Shield,
                            title = "Select an item",
                            description = "Choose a credential on the left to inspect or copy fields safely.",
                            actionLabel = "Add Item",
                            onAction = onAddItem,
                        )
                    }
                }
            }
        }
    } else {
        VaultListContent(
            state = state,
            selectedItemId = null,
            onSearchQueryChange = onSearchQueryChange,
            onSelectCategory = onSelectCategory,
            onOpenItem = onOpenItem,
            onToggleFavorite = onToggleFavorite,
            onAddItem = onAddItem,
            onOpenGenerator = onOpenGenerator,
            onOpenHealth = onOpenHealth,
            onLockVault = onLockVault,
        )
    }
}

@Composable
private fun VaultListContent(
    state: VaultUiState,
    selectedItemId: String?,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (VaultCategoryFilter) -> Unit,
    onOpenItem: (VaultItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddItem: () -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenHealth: () -> Unit,
    onLockVault: () -> Unit,
) {
    val colors = VaultTheme.colors
    val isDefaultUnfilteredView =
        state.searchQuery.isBlank() && state.selectedCategory == VaultCategoryFilter.ALL
    val filtered = state.filteredItems
    val healthReport = remember(state.items) { PasswordHealthAnalyzer.analyze(state.items) }
    var carouselIndex by remember { mutableIntStateOf(0) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 12.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .testTag("vault_item_list"),
    ) {
        // Aran Top App Bar (Brand icon + Aran name on left, Lock action on right)
        item(key = "header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ShieldEmblem(size = 36.dp, locked = false)
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "${state.items.size} encrypted items • Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = colors.surfaceElevated,
                    border = BorderStroke(1.dp, colors.outline),
                ) {
                    IconButton(
                        onClick = onLockVault,
                        modifier = Modifier.testTag("vault_lock_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = stringResource(R.string.cd_lock_vault),
                            tint = colors.primary,
                        )
                    }
                }
            }
        }

        // Aran Search Bar paired with circular lime accent utility button (+)
        item(key = "search") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SearchField(
                    query = state.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = "Search vault…",
                    modifier = Modifier.weight(1f),
                    testTag = "vault_search_input",
                )
                Surface(
                    onClick = onAddItem,
                    shape = CircleShape,
                    color = colors.primary,
                    contentColor = colors.onPrimary,
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("vault_quick_add_button"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.cd_add_item),
                            tint = colors.onPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }

        if (state.cryptoMetadata.corruptedRecordIds.isNotEmpty()) {
            item(key = "corruption_warning") {
                InlineError(
                    message = "${state.cryptoMetadata.corruptedRecordIds.size} encrypted record(s) failed authentication on disk and were quarantined without deleting intact items. Restore from a verified .svbk backup if needed.",
                    testTag = "vault_corruption_warning",
                )
            }
        }

        // Featured Aran Security Carousel Card with interactive pagination dots
        item(key = "featured_carousel") {
            FeaturedSecurityCarouselCard(
                activeIndex = carouselIndex,
                onSelectIndex = { carouselIndex = it },
                report = healthReport,
                formatVersion = state.cryptoMetadata.formatVersion,
                encryptedCount = state.cryptoMetadata.encryptedRecordCount,
                onInspectHealth = onOpenHealth,
            )
        }

        // Aran Quick Actions List Card
        item(key = "quick_actions") {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.surfaceElevated,
                border = BorderStroke(1.dp, colors.outlineSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    QuickActionTile(
                        icon = AranIcons.HealthPulse,
                        title = "Password Health",
                        subtitle = if (healthReport.weakItems.isEmpty() && healthReport.reusedItems.isEmpty()) {
                            "Score ${healthReport.overallScorePercent}% • All logins look strong"
                        } else {
                            "Score ${healthReport.overallScorePercent}% • ${healthReport.weakItems.size} weak, ${healthReport.reusedItems.size} reused"
                        },
                        onClick = onOpenHealth,
                        testTag = "vault_quick_health_row",
                    )
                    HorizontalDivider(
                        color = colors.outlineSubtle,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                    QuickActionTile(
                        icon = AranIcons.Key,
                        title = "Password Generator",
                        subtitle = "Create high-entropy passwords or passphrases offline",
                        onClick = onOpenGenerator,
                        testTag = "vault_quick_generate_button",
                    )
                }
            }
        }

        item(key = "categories") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(
                    items = VaultCategoryFilter.entries,
                    key = { it.name },
                ) { filter ->
                    CategoryChip(
                        label = filter.label,
                        count = state.countForFilter(filter),
                        selected = state.selectedCategory == filter,
                        onClick = { onSelectCategory(filter) },
                        testTag = "category_chip_${filter.name.lowercase()}",
                    )
                }
            }
        }

        if (state.items.isEmpty()) {
            item(key = "empty_vault") {
                Spacer(modifier = Modifier.height(6.dp))
                EmptyState(
                    icon = AranIcons.Shield,
                    title = "Your vault is empty",
                    description = "Add your first login, secure note, or payment card to encrypt it locally.",
                    actionLabel = "Add First Item",
                    onAction = onAddItem,
                    actionTestTag = "empty_vault_add_button",
                )
            }
        } else if (filtered.isEmpty()) {
            item(key = "empty_search") {
                Spacer(modifier = Modifier.height(6.dp))
                EmptyState(
                    icon = Icons.Outlined.Search,
                    title = "No matching items",
                    description = "Try a different title, username, or domain filter.",
                    actionLabel = "Clear Filters",
                    onAction = {
                        onSearchQueryChange("")
                        onSelectCategory(VaultCategoryFilter.ALL)
                    },
                    actionTestTag = "empty_search_clear_button",
                )
            }
        } else if (isDefaultUnfilteredView) {
            val favorites = state.favoriteItems
            if (favorites.isNotEmpty()) {
                item(key = "section_favorites") {
                    SectionHeaderRow(
                        title = "Favorites",
                        trailingLabel = "${favorites.size}",
                        onTrailingClick = null,
                    )
                }
                items(
                    items = favorites,
                    key = { "fav_${it.id}" },
                ) { item ->
                    VaultItemRow(
                        item = item,
                        selected = item.id == selectedItemId,
                        onClick = { onOpenItem(item) },
                        onToggleFavorite = { onToggleFavorite(item.id) },
                    )
                }
            }

            val nonFavorites = filtered.filterNot { it.isFavorite }
            if (nonFavorites.isNotEmpty()) {
                item(key = "section_all") {
                    SectionHeaderRow(
                        title = "Today & Recent",
                        trailingLabel = "See All (${state.items.size})",
                        onTrailingClick = { onSelectCategory(VaultCategoryFilter.LOGINS) },
                    )
                }
                items(
                    items = nonFavorites,
                    key = { "all_${it.id}" },
                ) { item ->
                    VaultItemRow(
                        item = item,
                        selected = item.id == selectedItemId,
                        onClick = { onOpenItem(item) },
                        onToggleFavorite = { onToggleFavorite(item.id) },
                    )
                }
            }
        } else {
            item(key = "section_filtered") {
                SectionHeaderRow(
                    title = if (state.searchQuery.isNotBlank()) "Search Results" else state.selectedCategory.label,
                    trailingLabel = "See All",
                    onTrailingClick = {
                        onSearchQueryChange("")
                        onSelectCategory(VaultCategoryFilter.ALL)
                    },
                )
            }
            items(
                items = filtered,
                key = { "filtered_${it.id}" },
            ) { item ->
                VaultItemRow(
                    item = item,
                    selected = item.id == selectedItemId,
                    onClick = { onOpenItem(item) },
                    onToggleFavorite = { onToggleFavorite(item.id) },
                )
            }
        }
    }
}

@Composable
private fun FeaturedSecurityCarouselCard(
    activeIndex: Int,
    onSelectIndex: (Int) -> Unit,
    report: PasswordHealthReport,
    formatVersion: Int,
    encryptedCount: Int,
    onInspectHealth: () -> Unit,
) {
    val colors = VaultTheme.colors

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.34f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            colors.primaryMuted.copy(alpha = 0.5f),
                            colors.surfaceElevated,
                        )
                    )
                )
                .padding(18.dp),
        ) {
            when (activeIndex) {
                0 -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "Keeping Your Passwords SECURE",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "Password Health ${report.overallScorePercent}% • ${report.totalLogins} login(s) audited locally",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        InlineAccentPill(
                            text = "Inspect",
                            onClick = onInspectHealth,
                            testTag = "vault_inspect_health_button",
                        )
                    }
                    PasswordHealthSegmentedBar(report = report)
                }

                1 -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "Encrypted SQLite Vault (v$formatVersion)",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "$encryptedCount AES-256-GCM records protected with canonical AAD",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        InlineAccentPill(
                            text = "Audit",
                            onClick = onInspectHealth,
                        )
                    }
                }

                else -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "Zero-Cloud Offline Storage",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "Export SHA-256 verified .svbk backups anytime in Settings",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        InlineAccentPill(
                            text = "Health",
                            onClick = onInspectHealth,
                        )
                    }
                }
            }

            // Interactive pagination indicator dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(3) { idx ->
                    val isActive = idx == activeIndex
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (isActive) 20.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (isActive) colors.primary else colors.outline)
                            .clickable { onSelectIndex(idx) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = VaultTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .testTag(testTag),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.primaryMuted),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    trailingLabel: String,
    onTrailingClick: (() -> Unit)?,
) {
    val colors = VaultTheme.colors
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 4.dp, end = 2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )
        if (onTrailingClick != null) {
            TextButton(onClick = onTrailingClick) {
                Text(
                    text = trailingLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                )
            }
        } else {
            Text(
                text = trailingLabel,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}
