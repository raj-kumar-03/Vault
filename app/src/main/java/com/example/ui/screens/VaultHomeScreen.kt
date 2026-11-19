package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.VaultCategoryFilter
import com.example.domain.model.VaultItem
import com.example.ui.components.CategoryChip
import com.example.ui.components.EmptyState
import com.example.ui.components.SearchField
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SecurityStatusRow
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
                            icon = Icons.Outlined.Shield,
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
    onLockVault: () -> Unit,
) {
    val colors = VaultTheme.colors
    val isDefaultUnfilteredView =
        state.searchQuery.isBlank() && state.selectedCategory == VaultCategoryFilter.ALL
    val filtered = state.filteredItems

    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
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
        item(key = "header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "Local-first vault • ${state.items.size} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface,
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

        item(key = "status") {
            SecurityStatusRow(
                title = "Mock Vault Unlocked",
                subtitle = "Offline only • No real secrets stored in Phase 1",
                badgeLabel = "Phase 1",
            )
        }

        item(key = "search") {
            SearchField(
                query = state.searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = "Search titles, usernames, or domains…",
                testTag = "vault_search_input",
            )
        }

        item(key = "quick_actions") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SecondaryButton(
                    text = "Add Item",
                    leadingIcon = Icons.Outlined.Add,
                    onClick = onAddItem,
                    modifier = Modifier.weight(1f),
                    testTag = "vault_quick_add_button",
                )
                SecondaryButton(
                    text = "Generate",
                    leadingIcon = Icons.Outlined.Key,
                    onClick = onOpenGenerator,
                    modifier = Modifier.weight(1f),
                    testTag = "vault_quick_generate_button",
                )
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
                Spacer(modifier = Modifier.height(8.dp))
                EmptyState(
                    icon = Icons.Outlined.Shield,
                    title = "Your vault is empty",
                    description = "Add a mock login, secure note, or identity to test retrieval and masked reveal flows.",
                    actionLabel = "Add First Item",
                    onAction = onAddItem,
                    actionTestTag = "empty_vault_add_button",
                )
            }
        } else if (filtered.isEmpty()) {
            item(key = "empty_search") {
                Spacer(modifier = Modifier.height(8.dp))
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
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
                    SectionLabel(
                        title = "Favorites",
                        count = favorites.size,
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
                    SectionLabel(
                        title = "Recently Used & All Items",
                        count = nonFavorites.size,
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
                SectionLabel(
                    title = if (state.searchQuery.isNotBlank()) "Search Results" else state.selectedCategory.label,
                    count = filtered.size,
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
private fun SectionLabel(
    title: String,
    count: Int,
) {
    val colors = VaultTheme.colors
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, start = 4.dp, end = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textSecondary,
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
    }
}
