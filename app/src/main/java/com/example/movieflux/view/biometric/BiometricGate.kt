package com.example.movieflux.view.biometric

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.movieflux.R
import com.example.movieflux.view.components.ButtonSize
import com.example.movieflux.view.components.PrimaryButton
import com.example.movieflux.view.components.SecondaryButton

@Composable
fun BiometricGate(
    state: BiometricGateState,
    onRetry: () -> Unit,
    onUsePassword: () -> Unit,
    onResolved: (BiometricGateState) -> Unit,
    authenticate: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    content: @Composable () -> Unit
) {
    when (state) {
        is BiometricGateState.Checking -> {
            LaunchedEffect(state) {
                authenticate(
                    { onResolved(BiometricGateState.Passed) },
                    { errString -> onResolved(BiometricGateState.Failed(errString)) }
                )
            }
        }
        is BiometricGateState.Passed -> content()
        is BiometricGateState.Failed -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.biometric_failed_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                PrimaryButton(
                    text = stringResource(R.string.biometric_use_password),
                    onClick = onUsePassword,
                    size = ButtonSize.LARGE
                )
                Spacer(modifier = Modifier.height(12.dp))
                SecondaryButton(
                    text = stringResource(R.string.biometric_retry),
                    onClick = onRetry,
                    size = ButtonSize.LARGE
                )
            }
        }
    }
}
