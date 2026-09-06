package com.vaibhav.moneytracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.moneytracker.auth.OnboardingState
import com.vaibhav.moneytracker.auth.OnboardingStatus

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onGoogleSignInClick: () -> Unit,
    onContinueAsGuestClick: () -> Unit,
    onRequestSheetsScopeClick: () -> Unit,
    onRetryClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "💰", fontSize = 72.sp)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Money Tracker",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Personal Finance with Private Cloud Backup",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(36.dp))

                when (state.status) {
                    OnboardingStatus.IDLE_CHECKING -> {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Verifying session...", style = MaterialTheme.typography.bodySmall)
                    }

                    OnboardingStatus.SIGNED_OUT -> {
                        Button(
                            onClick = onGoogleSignInClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Sign in with Google", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = onContinueAsGuestClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Continue as Guest", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Use Money Tracker locally on this device without cloud backup.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    OnboardingStatus.SIGNING_IN -> {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Signing in with Google...", style = MaterialTheme.typography.bodySmall)
                    }

                    OnboardingStatus.AUTHENTICATED_PENDING_SCOPES -> {
                        if (state.user != null) {
                            Text(
                                text = "Signed in as ${state.user.email}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Button(
                            onClick = onRequestSheetsScopeClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Authorize Google Sheets Backup", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(onClick = onSignOutClick) {
                            Text("Sign Out", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    OnboardingStatus.ERROR -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Authentication Notice",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.errorMessage ?: "Sign in or authorization failed. Please try again.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onRetryClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Try Again", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = onContinueAsGuestClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Continue as Guest", fontWeight = FontWeight.SemiBold)
                        }

                        if (state.user != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onSignOutClick) {
                                Text("Sign Out", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    OnboardingStatus.CLOUD_SETUP_IN_PROGRESS -> {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Setting up your Money Tracker cloud backup...", style = MaterialTheme.typography.bodySmall)
                    }

                    OnboardingStatus.AUTHENTICATED_AUTHORIZED,
                    OnboardingStatus.CLOUD_READY,
                    OnboardingStatus.GUEST,
                    OnboardingStatus.COMPLETE -> {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Entering Money Tracker...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
