package com.jayr91.vdr.ui

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jayr91.vdr.billing.BillingManager
import com.jayr91.vdr.billing.ProEntitlement

private fun android.content.Context.isDebuggableApp(): Boolean =
    (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

@Composable
fun rememberBillingManager(): BillingManager {
    val context = LocalContext.current
    val manager = remember { BillingManager(context) }
    DisposableEffect(manager) {
        manager.start()
        onDispose { manager.end() }
    }
    return manager
}

@Composable
fun ProUpgradeDialog(
    billing: BillingManager,
    isPro: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val error by billing.lastError.collectAsState()
    val details by billing.productDetails.collectAsState()
    val priceLabel = details?.oneTimePurchaseOfferDetails?.formattedPrice
        ?: "₹1"

    LaunchedEffect(isPro) {
        if (isPro) onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unlock Pro for $priceLabel") },
        text = {
            Column {
                Text(
                    "Pro unlocks page media scan, multi-URL batch queue, more download segments, and Focus Guard. " +
                        "One-time purchase — restore anytime on this Google account.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val activity = context as? Activity
                        if (activity == null) {
                            Toast.makeText(context, "Open VDR from the launcher to buy", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!billing.launchPurchase(activity)) {
                            Toast.makeText(
                                context,
                                billing.lastError.value ?: "Billing unavailable",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Buy") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        billing.restorePurchases()
                        Toast.makeText(context, "Checking purchases…", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Restore") }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Not now") }
            }
        },
        dismissButton = {},
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProTitle(
    isPro: Boolean,
    onDebugToggle: () -> Unit,
) {
    val context = LocalContext.current
    Text(
        if (isPro) "VDR Pro" else "VDR",
        modifier = Modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                if (context.isDebuggableApp()) onDebugToggle()
            },
        ),
    )
}

suspend fun toggleDebugPro(context: android.content.Context): Boolean {
    if (!context.isDebuggableApp()) return false
    val currently = ProEntitlement.isPro(context)
    ProEntitlement.setDebugUnlocked(context, !currently)
    return !currently
}
