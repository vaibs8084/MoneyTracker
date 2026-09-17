package com.vaibhav.moneytracker.intelligence

import com.vaibhav.moneytracker.CategoryEntity
import com.vaibhav.moneytracker.RuleWithTags
import com.vaibhav.moneytracker.TagEntity
import com.vaibhav.moneytracker.TransactionEntity
import java.util.Locale

enum class IntelligenceConfidence {
    HIGH,    // Matched explicit user Smart Rule
    MEDIUM,  // Matched normalized merchant history from user's previous transactions
    LOW,     // Matched built-in merchant dictionary
    NONE     // No match
}

data class IntelligenceSuggestion(
    val normalizedMerchant: String,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val tags: List<TagEntity> = emptyList(),
    val confidence: IntelligenceConfidence = IntelligenceConfidence.NONE,
    val explanation: String? = null
)

object TransactionIntelligence {

    private val BUILTIN_MERCHANT_CATEGORIES = mapOf(
        "Swiggy" to "Food",
        "Zomato" to "Food",
        "McDonald's" to "Food",
        "Domino's Pizza" to "Food",
        "Starbucks" to "Food",
        "Uber" to "Transport",
        "Ola Cabs" to "Transport",
        "Amazon" to "Shopping",
        "Flipkart" to "Shopping",
        "Decathlon" to "Shopping",
        "Netflix" to "Subscriptions",
        "Spotify" to "Subscriptions",
        "Airtel" to "Bills",
        "Jio" to "Bills",
        "Apollo Pharmacy" to "Health",
        "PharmEasy" to "Health"
    )

    fun analyze(
        rawTitle: String,
        rules: List<RuleWithTags> = emptyList(),
        history: List<TransactionEntity> = emptyList(),
        categories: List<CategoryEntity> = emptyList()
    ): IntelligenceSuggestion {
        val normalized = MerchantNormalizer.normalize(rawTitle)
        if (rawTitle.isBlank()) {
            return IntelligenceSuggestion(normalizedMerchant = "")
        }

        // 1. Check Explicit User Smart Rules (HIGH Confidence)
        val lowerRaw = rawTitle.lowercase(Locale.getDefault())
        val matchedRule = rules.find {
            lowerRaw.contains(it.rule.titlePattern.lowercase(Locale.getDefault()))
        }

        if (matchedRule != null) {
            val catName = categories.find { it.id == matchedRule.rule.targetCategoryId }?.name
            return IntelligenceSuggestion(
                normalizedMerchant = normalized,
                categoryId = matchedRule.rule.targetCategoryId,
                categoryName = catName,
                tags = matchedRule.tags,
                confidence = IntelligenceConfidence.HIGH,
                explanation = "Matched Smart Rule: ${matchedRule.rule.titlePattern}"
            )
        }

        // 2. Check User Transaction History by Normalized Merchant (MEDIUM Confidence)
        val lowerNormalized = normalized.lowercase(Locale.getDefault())
        val matchingHistory = history.filter {
            MerchantNormalizer.normalize(it.title).lowercase(Locale.getDefault()) == lowerNormalized &&
                    it.categoryId != null
        }

        if (matchingHistory.size >= 2) {
            val topCategoryGroup = matchingHistory.groupBy { it.categoryId }.maxByOrNull { it.value.size }
            if (topCategoryGroup != null) {
                val catId = topCategoryGroup.key
                val catName = categories.find { it.id == catId }?.name ?: topCategoryGroup.value.first().category
                return IntelligenceSuggestion(
                    normalizedMerchant = normalized,
                    categoryId = catId,
                    categoryName = catName,
                    confidence = IntelligenceConfidence.MEDIUM,
                    explanation = "Based on $normalized transaction history (${topCategoryGroup.value.size} times)"
                )
            }
        }

        // 3. Check Built-in Merchant Keyword Dictionary (LOW Confidence)
        val defaultCategoryName = BUILTIN_MERCHANT_CATEGORIES[normalized]
        if (defaultCategoryName != null) {
            val matchedCategory = categories.find { it.name.equals(defaultCategoryName, ignoreCase = true) }
            return IntelligenceSuggestion(
                normalizedMerchant = normalized,
                categoryId = matchedCategory?.id,
                categoryName = matchedCategory?.name ?: defaultCategoryName,
                confidence = IntelligenceConfidence.LOW,
                explanation = "Recognized merchant: $normalized"
            )
        }

        // 4. Fallback (NONE Confidence)
        return IntelligenceSuggestion(
            normalizedMerchant = normalized,
            confidence = IntelligenceConfidence.NONE
        )
    }
}
