package com.example.movieflux.view.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.movieflux.view.components.ButtonSize
import com.example.movieflux.view.components.LogoutConfirmDialog
import com.example.movieflux.view.components.PrimaryButton
import com.example.movieflux.view.components.ProfileHeader
import com.example.movieflux.view.components.SettingsSwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val vm: ProfileViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is ProfileUiEvent.LogoutComplete -> onLogout()
                is ProfileUiEvent.BiometricUnavailable -> snackbarHostState.showSnackbar(event.reason)
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Profile") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ProfileHeader(
                username = uiState.username,
                email = uiState.email
            )

            Text(
                text = "Security",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            SettingsSwitchRow(
                icon = Icons.Default.Lock,
                title = "Biometric login",
                subtitle = "Use your fingerprint to sign in faster",
                checked = uiState.biometricEnabled,
                onCheckedChange = vm::setBiometricEnabled
            )

            Text(
                text = "About",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ListItem(
                headlineContent = { Text("Version") },
                trailingContent = { Text(uiState.appVersion) }
            )
            ListItem(
                headlineContent = { Text("About MovieFlux") },
                supportingContent = { Text("Discover and track your favourite movies.") }
            )

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = "Log out",
                onClick = vm::requestLogout,
                size = ButtonSize.LARGE,
                modifier = Modifier.padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
        }
    }

    if (uiState.showLogoutDialog) {
        LogoutConfirmDialog(
            onConfirm = vm::confirmLogout,
            onDismiss = vm::dismissLogoutDialog
        )
    }
}
