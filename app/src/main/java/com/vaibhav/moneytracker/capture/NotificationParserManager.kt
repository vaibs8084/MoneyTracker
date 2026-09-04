package com.vaibhav.moneytracker.capture

import com.vaibhav.moneytracker.csv.CSVParser
import com.vaibhav.moneytracker.csv.DuplicateDetector
import java.util.Locale

interface NotificationParser {
    val providerName: String
    fun canParse(packageName: String, title: String, text: String): Boolean
    fun parse(packageName: String, title: String, text: String, timestamp: Long): ParsedNotificationCandidate?
}

object NotificationParserManager {

    private val otpOrSecurityPatterns = listOf(
        "otp", "secret code", "verification code", "do not share",
        "login attempt", "password reset", "security alert", "offer", "discount", "cashback of up to"
    )

    private val ignoreStatusPatterns = listOf(
        "failed", "declined", "cancelled", "unsuccessful", "pending", "initiated"
    )

    private val monetaryAmountRegex = Regex("(?:Rs\\.?|INR|₹)\\s*([0-9]+(?:[,.][0-9]{2,3})*(?:[,.][0-9]{2})?)", RegexOption.IGNORE_CASE)

    fun extractNotificationAmountPaise(text: String): Long? {
        val match = monetaryAmountRegex.find(text) ?: return null
        val amountStr = match.groupValues.getOrNull(1) ?: return null
        return CSVParser.parseAmountPaise(amountStr)
    }

    fun extractMerchant(text: String, title: String): String {
        val cleanText = text.trim()
        val extracted = when {
            cleanText.contains(" to ", ignoreCase = true) -> cleanText.substringAfter(" to ")
            cleanText.contains(" from ", ignoreCase = true) -> cleanText.substringAfter(" from ")
            cleanText.contains(" at ", ignoreCase = true) -> cleanText.substringAfter(" at ")
            cleanText.contains(" towards ", ignoreCase = true) -> cleanText.substringAfter(" towards ")
            else -> ""
        }.substringBefore(".").substringBefore(" using").substringBefore(" Txn").substringBefore(" Ref").substringBefore(" UTR").trim()

        if (extracted.isNotBlank() && extracted.length >= 2) {
            return extracted.take(40)
        }
        return if (title.isNotBlank() && !title.contains("Pay", ignoreCase = true) && !title.contains("Bank", ignoreCase = true)) title else "Merchant"
    }

    private val parsers = listOf(
        PhonePeNotificationParser,
        PaytmNotificationParser,
        GooglePayNotificationParser,
        BankNotificationParser
    )

    /**
     * Inspects notification text and package name to parse a financial transaction candidate.
     * Ignores OTPs, security alerts, promotional texts, and failed transactions.
     */
    fun parseNotification(
        packageName: String,
        titleText: String,
        bodyText: String,
        timestamp: Long
    ): ParsedNotificationCandidate? {
        val fullText = "$titleText $bodyText".trim()
        val lowerText = fullText.lowercase(Locale.getDefault())

        if (otpOrSecurityPatterns.any { lowerText.contains(it) }) {
            return null
        }

        if (ignoreStatusPatterns.any { lowerText.contains(it) }) {
            return ParsedNotificationCandidate(
                title = titleText.ifBlank { "Failed/Pending Transaction" },
                amountPaise = null,
                type = "Expense",
                timestamp = timestamp,
                sourceApp = getAppNameFromPackage(packageName),
                isFinancial = true,
                isIgnoredOrFailed = true,
                ignoreReason = "Transaction failed, pending, or cancelled"
            )
        }

        for (parser in parsers) {
            if (parser.canParse(packageName, titleText, bodyText)) {
                val candidate = parser.parse(packageName, titleText, bodyText, timestamp)
                if (candidate != null && candidate.amountPaise != null && candidate.amountPaise > 0L) {
                    return candidate
                }
            }
        }

        return null
    }

    fun getAppNameFromPackage(pkg: String): String {
        return when {
            pkg.contains("phonepe") -> "PhonePe"
            pkg.contains("paytm") -> "Paytm"
            pkg.contains("paisa") || pkg.contains("gpay") || pkg.contains("google.android.apps.nbu") -> "Google Pay"
            pkg.contains("kotak") -> "Kotak Bank"
            pkg.contains("hdfc") || pkg.contains("snapwork") -> "HDFC Bank"
            pkg.contains("icici") || pkg.contains("imobile") -> "ICICI Bank"
            pkg.contains("sbi") || pkg.contains("lotusintouch") -> "SBI"
            pkg.contains("axis") -> "Axis Bank"
            else -> "Bank / Payment App"
        }
    }
}

/* =====================================================
   PROVIDER PARSERS
===================================================== */

object PhonePeNotificationParser : NotificationParser {
    override val providerName = "PhonePe"

    override fun canParse(packageName: String, title: String, text: String): Boolean {
        return packageName.contains("phonepe") || title.contains("PhonePe", ignoreCase = true) || text.contains("PhonePe", ignoreCase = true)
    }

    override fun parse(packageName: String, title: String, text: String, timestamp: Long): ParsedNotificationCandidate? {
        val combined = "$title $text"
        val lower = combined.lowercase(Locale.getDefault())

        val isCredit = lower.contains("received") || lower.contains("credited")
        val type = if (isCredit) "Income" else "Expense"

        val amountPaise = NotificationParserManager.extractNotificationAmountPaise(combined) ?: return null
        val refNumber = DuplicateDetector.extractReferenceNumber(combined)
        val merchant = NotificationParserManager.extractMerchant(text, title)

        return ParsedNotificationCandidate(
            title = merchant,
            amountPaise = Math.abs(amountPaise),
            type = type,
            timestamp = timestamp,
            sourceApp = "PhonePe",
            referenceNumber = refNumber
        )
    }
}

object PaytmNotificationParser : NotificationParser {
    override val providerName = "Paytm"

    override fun canParse(packageName: String, title: String, text: String): Boolean {
        return packageName.contains("paytm") || title.contains("Paytm", ignoreCase = true) || text.contains("Paytm", ignoreCase = true)
    }

    override fun parse(packageName: String, title: String, text: String, timestamp: Long): ParsedNotificationCandidate? {
        val combined = "$title $text"
        val lower = combined.lowercase(Locale.getDefault())

        val isCredit = lower.contains("received") || lower.contains("credited") || lower.contains("added")
        val type = if (isCredit) "Income" else "Expense"

        val amountPaise = NotificationParserManager.extractNotificationAmountPaise(combined) ?: return null
        val refNumber = DuplicateDetector.extractReferenceNumber(combined)
        val merchant = NotificationParserManager.extractMerchant(text, title)

        return ParsedNotificationCandidate(
            title = merchant,
            amountPaise = Math.abs(amountPaise),
            type = type,
            timestamp = timestamp,
            sourceApp = "Paytm",
            referenceNumber = refNumber
        )
    }
}

object GooglePayNotificationParser : NotificationParser {
    override val providerName = "Google Pay"

    override fun canParse(packageName: String, title: String, text: String): Boolean {
        return packageName.contains("paisa") || packageName.contains("gpay") || title.contains("Google Pay", ignoreCase = true) || text.contains("Google Pay", ignoreCase = true)
    }

    override fun parse(packageName: String, title: String, text: String, timestamp: Long): ParsedNotificationCandidate? {
        val combined = "$title $text"
        val lower = combined.lowercase(Locale.getDefault())

        val isCredit = lower.contains("received") || lower.contains("credited") || lower.contains("got")
        val type = if (isCredit) "Income" else "Expense"

        val amountPaise = NotificationParserManager.extractNotificationAmountPaise(combined) ?: return null
        val refNumber = DuplicateDetector.extractReferenceNumber(combined)
        val merchant = NotificationParserManager.extractMerchant(text, title)

        return ParsedNotificationCandidate(
            title = merchant,
            amountPaise = Math.abs(amountPaise),
            type = type,
            timestamp = timestamp,
            sourceApp = "Google Pay",
            referenceNumber = refNumber
        )
    }
}

object BankNotificationParser : NotificationParser {
    override val providerName = "Bank Notification"

    override fun canParse(packageName: String, title: String, text: String): Boolean {
        val combined = "$title $text".lowercase(Locale.getDefault())
        return combined.contains("debited") || combined.contains("credited") || combined.contains("spent") || combined.contains("vpa") || combined.contains("ac ") || combined.contains("a/c")
    }

    override fun parse(packageName: String, title: String, text: String, timestamp: Long): ParsedNotificationCandidate? {
        val combined = "$title $text"
        val lower = combined.lowercase(Locale.getDefault())

        val isCredit = lower.contains("credited") || lower.contains("received") || lower.contains("deposited") || lower.contains("refund")
        val type = if (isCredit) "Income" else "Expense"

        val amountPaise = NotificationParserManager.extractNotificationAmountPaise(combined) ?: return null
        val refNumber = DuplicateDetector.extractReferenceNumber(combined)
        val merchant = NotificationParserManager.extractMerchant(text, title)
        val appName = NotificationParserManager.getAppNameFromPackage(packageName)

        return ParsedNotificationCandidate(
            title = merchant,
            amountPaise = Math.abs(amountPaise),
            type = type,
            timestamp = timestamp,
            sourceApp = appName,
            referenceNumber = refNumber
        )
    }
}
