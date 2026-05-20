package com.example.movieflux.view.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.movieflux.R
import com.example.movieflux.performance.LogRecompositions
import com.example.movieflux.ui.theme.LocalBrandColors

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LogRecompositions("SearchBar")
    val brand = LocalBrandColors.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_movies_placeholder)) },
        leadingIcon = {
            Icon(imageVector = Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear_search))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = brand.teal,
            unfocusedContainerColor = brand.teal,
            focusedTextColor = brand.onTeal,
            unfocusedTextColor = brand.onTeal,
            cursorColor = brand.onTeal,
            focusedBorderColor = brand.onTeal.copy(alpha = 0.5f),
            unfocusedBorderColor = brand.onTeal.copy(alpha = 0.25f),
            focusedLeadingIconColor = brand.onTeal,
            unfocusedLeadingIconColor = brand.onTeal.copy(alpha = 0.7f),
            focusedTrailingIconColor = brand.onTeal,
            unfocusedTrailingIconColor = brand.onTeal,
            focusedPlaceholderColor = brand.onTeal.copy(alpha = 0.6f),
            unfocusedPlaceholderColor = brand.onTeal.copy(alpha = 0.6f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
