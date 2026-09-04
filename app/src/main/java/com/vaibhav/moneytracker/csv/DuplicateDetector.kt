package com.vaibhav.moneytracker.csv

import com.vaibhav.moneytracker.TransactionEntity
import java.util.Calendar
import java.util.Locale

enum class DuplicateConfidence {
    UNIQUE,
    POSSIBLE_DUPLICATE,
    HIGH_CONFIDENCE_DUPLICATE
}

data class DuplicateMatchResult(
    val confidence: DuplicateConfidence,
    val matchedTransaction: TransactionEntity? = null,
    val matchReason: String? = null,
    val fingerprint: String = ""
)

object DuplicateDetector {

    // Regex matching 10-18 digit reference numbers / UPI IDs / UTR / RRN
    private val referenceNumberRegex = Regex("(?:UPI[/-]|RRN[/:-]|UTR[/:-]|REF[/:-]|/|\\b)(\\d{10,18})(?:\\b|/|\\s|\$)", RegexOption.IGNORE_CASE)

    /**
     * Extracts reference/RRN/UTR number from narration if present.
     */
    fun extractReferenceNumber(narration: String): String? {
        val match = referenceNumberRegex.find(narration)
        return match?.groupValues?.getOrNull(1)
    }

    private val commonPrefixes = listOf("upi", "neft", "imps", "ach", "pos", "atm", "txn", "chg", "paid to", "transfer to", "payment to", "online order")

    /**
     * Normalizes a transaction narration string for fuzzy/similarity comparison.
     * Removes reference numbers, punctuation, bank prefix tags, and converts to lowercase.
     */
    fun normalizeTitle(title: String): String {
        val ref = extractReferenceNumber(title)
        var clean = title.lowercase(Locale.getDefault())
        if (ref != null) {
            clean = clean.replace(ref, "")
        }
        clean = clean.replace(Regex("[^a-z0-9\\s]"), " ")
        for (prefix in commonPrefixes) {
            clean = clean.replace(prefix, "")
        }
        return clean.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Generates a deterministic transaction fingerprint string.
     * Format: accountId_type_amountPaise_dayStartMs_normalizedRefOrTitle
     * Reusable across Statement Import, Transaction Capture (SMS), and Sheets Sync.
     */
    fun calculateFingerprint(transaction: TransactionEntity): String {
        val dayStart = getCalendarDayStart(transaction.createdAt)
        val ref = extractReferenceNumber(transaction.title)
        val titleNorm = if (!ref.isNullOrBlank()) "ref:$ref" else normalizeTitle(transaction.title)
        val accId = transaction.accountId ?: 0L
        return "${accId}_${transaction.type}_${transaction.amountPaise}_${dayStart}_$titleNorm"
    }

    fun getCalendarDayStart(timestampMs: Long): Long {
        if (timestampMs <= 0L) return 0L
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestampMs
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Detects duplicates between a candidate transaction and existing database transactions.
     * Confidence scoring:
     * - HIGH_CONFIDENCE_DUPLICATE: Same account + same amount + same day + matching reference number OR fingerprint match.
     * - POSSIBLE_DUPLICATE: Same account + same amount + within +/- 1 day posting window OR matching manual transaction on same day.
     * - UNIQUE: No convincing match found.
     */
    fun detectDuplicate(
        candidate: TransactionEntity,
        existingTransactions: List<TransactionEntity>
    ): DuplicateMatchResult {
        if (candidate.amountPaise <= 0L || candidate.createdAt <= 0L) {
            return DuplicateMatchResult(DuplicateConfidence.UNIQUE)
        }

        val candidateDay = getCalendarDayStart(candidate.createdAt)
        val candidateRef = extractReferenceNumber(candidate.title)
        val candidateNormTitle = normalizeTitle(candidate.title)
        val candidateFingerprint = calculateFingerprint(candidate)

        val oneDayMs = 24 * 60 * 60 * 1000L

        var bestMatch: TransactionEntity? = null
        var bestConfidence = DuplicateConfidence.UNIQUE
        var bestReason: String? = null

        for (existing in existingTransactions) {
            // Must belong to same account (or matching legacy account name)
            val sameAccount = (candidate.accountId != null && existing.accountId == candidate.accountId) ||
                    (candidate.account.equals(existing.account, ignoreCase = true))
            if (!sameAccount) continue

            // Must match exact amount in paise
            if (candidate.amountPaise != existing.amountPaise) continue

            val existingDay = getCalendarDayStart(existing.createdAt)
            val dayDiffMs = Math.abs(candidateDay - existingDay)

            // Outside +/- 1 day posting window -> Not a duplicate
            if (dayDiffMs > oneDayMs) continue

            val existingRef = extractReferenceNumber(existing.title)
            val existingNormTitle = normalizeTitle(existing.title)
            val existingFingerprint = calculateFingerprint(existing)

            // Check 1: Exact Fingerprint Match or Reference Number Match on same day
            val sameRef = !candidateRef.isNullOrBlank() && candidateRef == existingRef
            val sameFingerprint = candidateFingerprint == existingFingerprint

            if (sameRef || sameFingerprint) {
                return DuplicateMatchResult(
                    confidence = DuplicateConfidence.HIGH_CONFIDENCE_DUPLICATE,
                    matchedTransaction = existing,
                    matchReason = if (sameRef) "Matching bank reference number ($candidateRef)" else "Matching transaction fingerprint",
                    fingerprint = candidateFingerprint
                )
            }

            // Check 2: Same day + Exact title match
            if (dayDiffMs == 0L && candidateNormTitle == existingNormTitle && candidateNormTitle.isNotBlank()) {
                return DuplicateMatchResult(
                    confidence = DuplicateConfidence.HIGH_CONFIDENCE_DUPLICATE,
                    matchedTransaction = existing,
                    matchReason = "Matching description on same date",
                    fingerprint = candidateFingerprint
                )
            }

            // Check 3: Possible Duplicate (Same amount + same/near day, title similarity or manual entry match)
            val titleOverlap = computeTitleSimilarity(candidateNormTitle, existingNormTitle)
            if (titleOverlap >= 0.3 || dayDiffMs == 0L) {
                bestMatch = existing
                bestConfidence = DuplicateConfidence.POSSIBLE_DUPLICATE
                bestReason = if (dayDiffMs == 0L) "Same amount & account on same day (${existing.title})"
                             else "Same amount & account within 1 day posting window (${existing.title})"
            }
        }

        return DuplicateMatchResult(
            confidence = bestConfidence,
            matchedTransaction = bestMatch,
            matchReason = bestReason,
            fingerprint = candidateFingerprint
        )
    }

    /**
     * Simple token Jaccard similarity for title comparison.
     */
    private fun computeTitleSimilarity(str1: String, str2: String): Double {
        if (str1 == str2) return 1.0
        val tokens1 = str1.split(" ").filter { it.length > 2 }.toSet()
        val tokens2 = str2.split(" ").filter { it.length > 2 }.toSet()
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0
        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        return intersection.toDouble() / union.toDouble()
    }
}
