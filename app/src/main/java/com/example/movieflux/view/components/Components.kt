@file:Suppress("MatchingDeclarationName")

package com.example.movieflux.view.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.movieflux.ui.theme.MovieFluxTheme

enum class ButtonSize {
    SMALL,
    MEDIUM,
    LARGE
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.MEDIUM,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
    )
) {
    val (buttonHeight, textStyle, buttonModifier) = when (size) {
        ButtonSize.SMALL -> {
            Triple(
                40.dp,
                MaterialTheme.typography.bodySmall,
                modifier
                    .height(40.dp)
                    .padding(horizontal = 16.dp)
            )
        }

        ButtonSize.MEDIUM -> {
            Triple(
                48.dp,
                MaterialTheme.typography.bodyMedium,
                modifier
                    .height(48.dp)
                    .padding(horizontal = 16.dp)
            )
        }

        ButtonSize.LARGE -> {
            Triple(
                56.dp,
                MaterialTheme.typography.titleSmall,
                modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
            )
        }
    }

    Button(
        onClick = onClick,
        modifier = buttonModifier,
        enabled = enabled && !isLoading,
        colors = colors,
        shape = MaterialTheme.shapes.medium
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.Black,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                style = textStyle
            )
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.MEDIUM,
    enabled: Boolean = true
) {
    val (buttonHeight, textStyle, buttonModifier) = when (size) {
        ButtonSize.SMALL -> {
            Triple(
                40.dp,
                MaterialTheme.typography.bodySmall,
                modifier
                    .height(40.dp)
                    .padding(horizontal = 16.dp)
            )
        }

        ButtonSize.MEDIUM -> {
            Triple(
                48.dp,
                MaterialTheme.typography.bodyMedium,
                modifier
                    .height(48.dp)
                    .padding(horizontal = 16.dp)
            )
        }

        ButtonSize.LARGE -> {
            Triple(
                56.dp,
                MaterialTheme.typography.titleSmall,
                modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
            )
        }
    }

    Button(
        onClick = onClick,
        modifier = buttonModifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = text,
            style = textStyle
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PrimaryButtonEnabledPreview() {
    MovieFluxTheme {
        Column {
            PrimaryButton(
                text = "Primary Button Small",
                onClick = {},
                size = ButtonSize.SMALL
            )
            PrimaryButton(
                text = "Primary Button Medium",
                onClick = {},
                size = ButtonSize.MEDIUM
            )
            PrimaryButton(
                text = "Primary Button Large",
                onClick = {},
                size = ButtonSize.LARGE
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PrimaryButtonDisabledPreview() {
    MovieFluxTheme {
        Column {
            PrimaryButton(
                text = "Primary Button Small",
                onClick = {},
                size = ButtonSize.SMALL,
                enabled = false
            )
            PrimaryButton(
                text = "Primary Button Medium",
                onClick = {},
                size = ButtonSize.MEDIUM,
                enabled = false
            )
            PrimaryButton(
                text = "Primary Button Large",
                onClick = {},
                size = ButtonSize.LARGE,
                enabled = false
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SecondaryButtonDisabledPreview() {
    MovieFluxTheme {
        Column {
            SecondaryButton(
                text = "Primary Button Small",
                onClick = {},
                size = ButtonSize.SMALL,
                enabled = false
            )
            SecondaryButton(
                text = "Primary Button Medium",
                onClick = {},
                size = ButtonSize.MEDIUM,
                enabled = false
            )
            SecondaryButton(
                text = "Primary Button Large",
                onClick = {},
                size = ButtonSize.LARGE,
                enabled = false
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SecondaryButtonEnabledPreview() {
    MovieFluxTheme {
        Column {
            SecondaryButton(
                text = "Primary Button Small",
                onClick = {},
                size = ButtonSize.SMALL,
                enabled = true
            )
            SecondaryButton(
                text = "Primary Button Medium",
                onClick = {},
                size = ButtonSize.MEDIUM,
                enabled = true
            )
            SecondaryButton(
                text = "Primary Button Large",
                onClick = {},
                size = ButtonSize.LARGE,
                enabled = true
            )
        }
    }
}
