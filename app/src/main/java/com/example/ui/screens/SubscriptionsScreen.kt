package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.billing.BillingManager
import com.example.billing.BillingUiState
import com.example.ui.components.NexoraGlowCard
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.TextSecondary

@Composable
fun SubscriptionsScreen(
    activePlanTier: String,
    billingState: BillingUiState,
    onSubscribe: (Activity, String) -> Unit,
    onRestorePurchases: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activity = LocalContext.current as? Activity

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1226), ObsidianBlack)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
            // Hero Banner using generated cyber-shield asset
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_nexora_shield_1794917751682),
                    contentDescription = stringResource(R.string.plans_title),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, ObsidianBlack.copy(alpha = 0.88f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.plans_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.plans_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Active Entitlement Banner
            NexoraGlowCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (activePlanTier != "free") StatusEmerald else ObsidianBorder
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = stringResource(R.string.current_plan_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = when (activePlanTier) {
                                "pro_monthly" -> stringResource(R.string.plan_pro_monthly_title)
                                "pro_annual" -> stringResource(R.string.plan_pro_annual_title)
                                "pro_premium" -> stringResource(R.string.plan_pro_premium_title)
                                else -> stringResource(R.string.plan_free_title)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = if (activePlanTier != "free") StatusEmerald else MaterialTheme.colorScheme.onBackground
                        )
                    }
                    OutlinedButton(
                        onClick = onRestorePurchases,
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("restore_purchases_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_restore_purchases), color = NeonCyan)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Billing availability notice
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (billingState.isPlayConnected) StatusEmerald.copy(alpha = 0.1f) else StatusAmber.copy(alpha = 0.12f),
                border = BorderStroke(
                    1.dp,
                    if (billingState.isPlayConnected) StatusEmerald.copy(alpha = 0.4f) else StatusAmber.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = billingState.statusMessage ?: if (billingState.isPlayConnected) {
                        stringResource(R.string.billing_status_ready)
                    } else {
                        stringResource(R.string.billing_status_unavailable)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (billingState.isPlayConnected) StatusEmerald else StatusAmber,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Features
            NexoraGlowCard(modifier = Modifier.fillMaxWidth()) {
                val features = listOf(
                    stringResource(R.string.feature_wireguard),
                    stringResource(R.string.feature_no_logs),
                    stringResource(R.string.feature_split_tunnel),
                    stringResource(R.string.feature_multi_device)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    features.forEach { feature ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(feature, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val monthlyProduct = billingState.products[BillingManager.PRODUCT_PRO_MONTHLY]
            PlanPurchaseCard(
                title = stringResource(R.string.plan_pro_monthly_title),
                description = stringResource(R.string.plan_pro_monthly_desc),
                priceText = monthlyProduct?.formattedPrice
                    ?: stringResource(R.string.billing_product_unconfigured, BillingManager.PRODUCT_PRO_MONTHLY),
                isCurrent = activePlanTier == "pro_monthly",
                highlighted = false,
                enabled = activity != null && monthlyProduct?.productDetails != null && !billingState.isPurchasing,
                testTag = "subscribe_monthly_button",
                onClick = {
                    if (activity != null) onSubscribe(activity, BillingManager.PRODUCT_PRO_MONTHLY)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            val annualProduct = billingState.products[BillingManager.PRODUCT_PRO_ANNUAL]
            PlanPurchaseCard(
                title = stringResource(R.string.plan_pro_annual_title),
                description = stringResource(R.string.plan_pro_annual_desc),
                priceText = annualProduct?.formattedPrice
                    ?: stringResource(R.string.billing_product_unconfigured, BillingManager.PRODUCT_PRO_ANNUAL),
                isCurrent = activePlanTier == "pro_annual",
                highlighted = true,
                enabled = activity != null && annualProduct?.productDetails != null && !billingState.isPurchasing,
                testTag = "subscribe_annual_button",
                onClick = {
                    if (activity != null) onSubscribe(activity, BillingManager.PRODUCT_PRO_ANNUAL)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            val premiumProduct = billingState.products[BillingManager.PRODUCT_PRO_PREMIUM]
            PlanPurchaseCard(
                title = stringResource(R.string.plan_pro_premium_title),
                description = stringResource(R.string.plan_pro_premium_desc),
                priceText = premiumProduct?.formattedPrice
                    ?: stringResource(R.string.billing_product_unconfigured, BillingManager.PRODUCT_PRO_PREMIUM),
                isCurrent = activePlanTier == "pro_premium",
                highlighted = false,
                enabled = activity != null && premiumProduct?.productDetails != null && !billingState.isPurchasing,
                testTag = "subscribe_premium_button",
                onClick = {
                    if (activity != null) onSubscribe(activity, BillingManager.PRODUCT_PRO_PREMIUM)
                }
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PlanPurchaseCard(
    title: String,
    description: String,
    priceText: String,
    isCurrent: Boolean,
    highlighted: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    NexoraGlowCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (highlighted) NeonCyan else ObsidianBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = if (highlighted) NeonCyan else ElectricBlue
                )
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = priceText,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlighted) NeonCyan else MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onClick,
            enabled = enabled && !isCurrent,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (highlighted) ElectricBlue else ObsidianBorder
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        ) {
            Text(
                text = if (isCurrent) stringResource(R.string.action_connected_node) else stringResource(R.string.action_subscribe),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
