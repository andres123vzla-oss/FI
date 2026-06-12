package com.example.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object IconMapper {

    fun mapToIcon(name: String): ImageVector {
        return when (name) {
            "Home" -> Icons.Default.Home
            "Power" -> Icons.Default.Bolt
            "Restaurant" -> Icons.Default.Restaurant
            "DirectionsCar" -> Icons.Default.DirectionsCar
            "Celebration" -> Icons.Default.Celebration
            "MedicalServices" -> Icons.Default.MedicalServices
            "School" -> Icons.Default.School
            "Checkroom" -> Icons.Default.Checkroom
            "Savings" -> Icons.Default.Savings
            "Category" -> Icons.Default.Category
            "DirectionsBus" -> Icons.Default.DirectionsBus
            "LocalTaxi" -> Icons.Default.LocalTaxi
            "Subscriptions" -> Icons.Default.Subscriptions
            "CardGiftcard" -> Icons.Default.CardGiftcard
            "SportsEsports" -> Icons.Default.SportsEsports
            "AttachMoney" -> Icons.Default.AttachMoney
            "Work" -> Icons.Default.Work
            "TrendingUp" -> Icons.AutoMirrored.Filled.TrendingUp
            "Storefront" -> Icons.Default.Storefront
            "EmojiEvents" -> Icons.Default.EmojiEvents
            "MoreHoriz" -> Icons.Default.MoreHoriz
            "AccountBalanceWallet" -> Icons.Default.AccountBalanceWallet
            "Handshake" -> Icons.Default.Handshake
            else -> Icons.Default.Category
        }
    }

    val availableIcons = listOf(
        "AttachMoney" to Icons.Default.AttachMoney,
        "Work" to Icons.Default.Work,
        "TrendingUp" to Icons.AutoMirrored.Filled.TrendingUp,
        "Storefront" to Icons.Default.Storefront,
        "EmojiEvents" to Icons.Default.EmojiEvents,
        "AccountBalanceWallet" to Icons.Default.AccountBalanceWallet,
        "Handshake" to Icons.Default.Handshake,
        "Home" to Icons.Default.Home,
        "Power" to Icons.Default.Bolt,
        "Restaurant" to Icons.Default.Restaurant,
        "DirectionsCar" to Icons.Default.DirectionsCar,
        "Celebration" to Icons.Default.Celebration,
        "MedicalServices" to Icons.Default.MedicalServices,
        "School" to Icons.Default.School,
        "Checkroom" to Icons.Default.Checkroom,
        "Savings" to Icons.Default.Savings,
        "DirectionsBus" to Icons.Default.DirectionsBus,
        "LocalTaxi" to Icons.Default.LocalTaxi,
        "Subscriptions" to Icons.Default.Subscriptions,
        "CardGiftcard" to Icons.Default.CardGiftcard,
        "SportsEsports" to Icons.Default.SportsEsports,
        "Category" to Icons.Default.Category,
        "MoreHoriz" to Icons.Default.MoreHoriz,
    )
}
