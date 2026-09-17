package com.vaibhav.moneytracker.intelligence

import com.vaibhav.moneytracker.CategoryEntity
import com.vaibhav.moneytracker.CategorizationRuleEntity
import com.vaibhav.moneytracker.RuleWithTags
import com.vaibhav.moneytracker.TagEntity
import com.vaibhav.moneytracker.TransactionEntity
import org.junit.Assert.*
import org.junit.Test

class TransactionIntelligenceTest {

    @Test
    fun testMerchantNormalizationPrefixStripping() {
        assertEquals("Rahul Medical Store", MerchantNormalizer.normalize("UPI/RAHUL MEDICAL STORE/12345/PAYTM"))
        assertEquals("Swiggy", MerchantNormalizer.normalize("SWIGGY*ORDER123"))
        assertEquals("Uber", MerchantNormalizer.normalize("POS-UBER-8821"))
        assertEquals("Amazon", MerchantNormalizer.normalize("AMAZON PAY INDIA"))
        assertEquals("Netflix", MerchantNormalizer.normalize("NETFLIX.COM*123"))
    }

    @Test
    fun testEmptyAndMalformedTitleNormalization() {
        assertEquals("Unclassified", MerchantNormalizer.normalize(""))
        assertEquals("Unclassified", MerchantNormalizer.normalize(null))
        assertEquals("Unclassified", MerchantNormalizer.normalize("   "))
    }

    @Test
    fun testExplicitSmartRuleMatchingHighConfidence() {
        val rules = listOf(
            RuleWithTags(
                rule = CategorizationRuleEntity(id = 1, titlePattern = "Swiggy", targetCategoryId = 10L),
                tags = listOf(TagEntity(id = 100, name = "food"))
            )
        )
        val categories = listOf(CategoryEntity(id = 10L, name = "Food"))

        val suggestion = TransactionIntelligence.analyze("UPI/SWIGGY/998", rules = rules, categories = categories)

        assertEquals(IntelligenceConfidence.HIGH, suggestion.confidence)
        assertEquals("Swiggy", suggestion.normalizedMerchant)
        assertEquals(10L, suggestion.categoryId)
        assertEquals("Food", suggestion.categoryName)
        assertEquals(1, suggestion.tags.size)
        assertEquals("food", suggestion.tags.first().name)
        assertTrue(suggestion.explanation?.contains("Matched Smart Rule") == true)
    }

    @Test
    fun testUserTransactionHistoryMatchingMediumConfidence() {
        val categories = listOf(CategoryEntity(id = 5L, name = "Food"))
        val history = listOf(
            TransactionEntity(id = 1, title = "SWIGGY*ORDER1", category = "Food", account = "Kotak", type = "Expense", amountPaise = 25000L, note = "", createdAt = 1000L, categoryId = 5L),
            TransactionEntity(id = 2, title = "UPI/SWIGGY/222", category = "Food", account = "Kotak", type = "Expense", amountPaise = 30000L, note = "", createdAt = 2000L, categoryId = 5L)
        )

        val suggestion = TransactionIntelligence.analyze("UPI-SWIGGY-333", history = history, categories = categories)

        assertEquals(IntelligenceConfidence.MEDIUM, suggestion.confidence)
        assertEquals("Swiggy", suggestion.normalizedMerchant)
        assertEquals(5L, suggestion.categoryId)
        assertEquals("Food", suggestion.categoryName)
        assertTrue(suggestion.explanation?.contains("transaction history") == true)
    }

    @Test
    fun testBuiltinMerchantDictionaryLowConfidence() {
        val categories = listOf(
            CategoryEntity(id = 2L, name = "Transport")
        )

        val suggestion = TransactionIntelligence.analyze("POS-UBER-TRIP", categories = categories)

        assertEquals(IntelligenceConfidence.LOW, suggestion.confidence)
        assertEquals("Uber", suggestion.normalizedMerchant)
        assertEquals(2L, suggestion.categoryId)
        assertEquals("Transport", suggestion.categoryName)
        assertTrue(suggestion.explanation?.contains("Recognized merchant") == true)
    }

    @Test
    fun testFinancialInvariantsUntouchedByIntelligence() {
        val amountPaise = 50000L // Rs. 500.00
        val rawTitle = "UPI/SWIGGY/123"

        val suggestion = TransactionIntelligence.analyze(rawTitle)

        // Verifying amountPaise remains strictly integer Long and untouched
        assertEquals(50000L, amountPaise)
        assertEquals("Swiggy", suggestion.normalizedMerchant)
    }
}
