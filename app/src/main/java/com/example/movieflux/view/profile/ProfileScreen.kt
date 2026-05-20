package com.example.movieflux.view.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.movieflux.R
import com.example.movieflux.performance.LogRecompositions
import com.example.movieflux.ui.theme.LocalBrandColors
import com.example.movieflux.view.components.ButtonSize
import com.example.movieflux.view.components.LogoutConfirmDialog
import com.example.movieflux.view.components.PrimaryButton
import com.example.movieflux.view.components.ProfileHeader
import com.example.movieflux.view.components.SettingsSwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val vm: ProfileViewModel = hiltViewModel()
    val uiState by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val brand = LocalBrandColors.current
    val context = LocalContext.current

    LogRecompositions("ProfileScreen")

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is ProfileUiEvent.LogoutComplete -> onLogout()
                is ProfileUiEvent.BiometricUnavailable ->
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = brand.teal,
                    titleContentColor = brand.onTeal
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            ProfileHeader(
                username = uiState.username,
                email = uiState.email
            )

            Text(
                text = stringResource(R.string.profile_security_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            SettingsSwitchRow(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.profile_biometric_title),
                subtitle = stringResource(R.string.profile_biometric_subtitle),
                checked = uiState.biometricEnabled,
                onCheckedChange = vm::setBiometricEnabled
            )

            Text(
                text = stringResource(R.string.profile_theme_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ThemeSelector(
                themeMode = uiState.themeMode,
                onModeChange = vm::setThemeMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Text(
                text = stringResource(R.string.profile_about_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_version)) },
                trailingContent = { Text(uiState.appVersion) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_about_app_title)) },
                supportingContent = { Text(stringResource(R.string.profile_about_app_subtitle)) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            PrimaryButton(
                text = stringResource(R.string.profile_logout),
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
