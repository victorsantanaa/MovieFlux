package com.example.movieflux.view.login

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.example.movieflux.R

@Composable
fun BiometricOptInDialog(onEnable: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.biometric_opt_in_title)) },
        text = { Text(stringResource(R.string.biometric_opt_in_message)) },
        confirmButton = {
            TextButton(onClick = onEnable) {
                Text(stringResource(R.string.biometric_opt_in_enable))
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.biometric_opt_in_skip))
            }
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
}
