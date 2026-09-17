package com.vaibhav.moneytracker.intelligence

import java.util.Locale

object MerchantNormalizer {

    private val PREFERRED_MERCHANT_NAMES = mapOf(
        "swiggy" to "Swiggy",
        "zomato" to "Zomato",
        "uber" to "Uber",
        "ola" to "Ola Cabs",
        "amazon" to "Amazon",
        "flipkart" to "Flipkart",
        "netflix" to "Netflix",
        "spotify" to "Spotify",
        "airtel" to "Airtel",
        "jio" to "Jio",
        "apollo" to "Apollo Pharmacy",
        "pharmeasy" to "PharmEasy",
        "decathlon" to "Decathlon",
        "starbucks" to "Starbucks",
        "mcdonalds" to "McDonald's",
        "dominos" to "Domino's Pizza"
    )

    fun normalize(rawTitle: String?): String {
        if (rawTitle.isNullOrBlank()) return "Unclassified"

        var clean = rawTitle.trim()

        // 1. Remove common UPI / Card / Gateway prefixes
        val prefixRegex = Regex("(?i)^(upi[/_\\-]|pos[/_\\-]|neft[/_\\-]|imps[/_\\-]|card[/_\\-]|paytm[/_\\-]|phonepe[/_\\-]|razorpay[*_\\-]|billdesk[*_\\-])\\s*")
        clean = clean.replace(prefixRegex, "")

        // 2. Remove UPI handles e.g. @ybl, @okaxis, @paytm
        clean = clean.replace(Regex("@[a-zA-Z]+"), "")

        // 3. Remove trailing transaction reference numbers or gateway suffixes e.g. /12345/PAYTM or *ORDER123
        clean = clean.replace(Regex("(?i)[/*_\\-]\\d+.*$"), "")
        clean = clean.replace(Regex("(?i)\\*(order|ride|pay|bill)\\w*"), "")

        clean = clean.trim()
        if (clean.isBlank()) return rawTitle.trim()

        // 4. Match against preferred merchant names
        val lowerClean = clean.lowercase(Locale.getDefault())
        PREFERRED_MERCHANT_NAMES.forEach { (key, preferred) ->
            if (lowerClean.contains(key)) {
                return preferred
            }
        }

        // 5. Title-case words for clean display
        return clean.split("\\s+".toRegex()).joinToString(" ") { word ->
            if (word.length <= 3 && word.all { it.isUpperCase() }) {
                word
            } else {
                word.lowercase(Locale.getDefault()).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }
    }
}
