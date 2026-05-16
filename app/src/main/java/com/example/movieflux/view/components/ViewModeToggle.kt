package com.example.movieflux.view.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ViewModeToggle(
    current: ViewMode,
    onToggle: (ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val next = if (current == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
    val (icon, label) = when (next) {
        ViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList to "Switch to list view"
        ViewMode.GRID -> Icons.Default.GridView to "Switch to grid view"
    }
    IconButton(onClick = { onToggle(next) }, modifier = modifier) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onBackground)
    }
}
