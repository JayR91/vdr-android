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
import com.jayr91.vdr.billing.ProGates

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
    val offer = details?.oneTimePurchaseOfferDetails

    // Play has to tell us the product exists and what it costs before we can
    // offer it. Until vdr_pro is activated -- which is gated on merchant
    // onboarding, not on this app -- there is nothing to sell, and every path
    // through launchBillingFlow() ends in an error toast.
    //
    // So do not advertise a purchase that cannot complete: no price in the
    // title (the old "₹1" was a hardcoded fallback shown even when nothing was
    // for sale) and no Buy button whose only outcome is failure. The features
    // stay gated either way; this only changes what we claim about them.
    val purchasable = offer != null

    LaunchedEffect(isPro) {
        if (isPro) onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (purchasable) "Unlock Pro for ${offer!!.formattedPrice}" else "Pro is coming soon",
            )
        },
        text = {
            Column {
                Text(
                    // Do not list a feature as Pro while it is shipping free;
                    // page media scan is free for the launch release (see
                    // ProGates.SCAN_PAGE_IS_FREE) and naming it here would be
                    // selling something the user already has.
                    buildString {
                        val perks = buildList {
                            if (!ProGates.SCAN_PAGE_IS_FREE) add("page media scan")
                            add("multi-URL batch queue")
                            add("more download segments")
                            add("Focus Guard")
                        }.joinToString(", ")
                        if (purchasable) {
                            append("Pro unlocks $perks. ")
                            append("One-time purchase — restore anytime on this Google account.")
                        } else {
                            append(perks.replaceFirstChar { it.uppercase() })
                            append(" will arrive as a one-time Pro purchase. They are not on sale ")
                            append("yet — everything else in VDR is free and stays that way.")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Only surface an error next to a purchase the user could
                // actually have attempted; "not for sale yet" is already the
                // message above, not a fault to report.
                if (purchasable && !error.isNullOrBlank()) {
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
                if (purchasable) {
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
                }
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
