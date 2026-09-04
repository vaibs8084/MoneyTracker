package com.vaibhav.moneytracker.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.vaibhav.moneytracker.IntelligenceEngine
import com.vaibhav.moneytracker.MoneyTrackerDatabase
import com.vaibhav.moneytracker.csv.DuplicateDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TransactionNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val notification = sbn?.notification ?: return
        val packageName = sbn.packageName ?: ""

        val title = notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val timestamp = sbn.postTime

        if (title.isBlank() && text.isBlank()) return

        serviceScope.launch {
            try {
                val candidate = NotificationParserManager.parseNotification(packageName, title, text, timestamp)
                if (candidate != null && !candidate.isIgnoredOrFailed && candidate.amountPaise != null && candidate.amountPaise > 0L) {
                    processAndStoreCandidate(candidate)
                }
            } catch (e: Exception) {
                // Safeguard: Listener service must never crash the application
            }
        }
    }

    private suspend fun processAndStoreCandidate(candidate: ParsedNotificationCandidate) {
        val db = MoneyTrackerDatabase.getInstance(applicationContext)

        // Pre-check for duplicate pending capture in inbox by reference number
        if (!candidate.referenceNumber.isNullOrBlank()) {
            val existingPending = db.capturedTransactionDao().getByReferenceNumber(candidate.referenceNumber)
            if (existingPending != null) {
                // Already captured in pending inbox
                return
            }
        }

        // Match accounts
        val accounts = db.accountDao().getAllIncludingInactive()
        val defaultAccount = accounts.firstOrNull { acc ->
            candidate.sourceApp.contains(acc.name, ignoreCase = true) || acc.name.contains(candidate.sourceApp, ignoreCase = true)
        } ?: accounts.firstOrNull()

        // Match Smart Rules for category
        val rules = db.categorizationRuleDao().getAllActiveWithTags()
        val (matchedCatId, _) = IntelligenceEngine.matchRules(candidate.title, rules)

        val capturedEntity = CapturedTransactionEntity(
            title = candidate.title,
            amountPaise = candidate.amountPaise!!,
            type = candidate.type,
            createdAt = candidate.timestamp,
            sourceApp = candidate.sourceApp,
            referenceNumber = candidate.referenceNumber,
            suggestedAccountId = defaultAccount?.id,
            suggestedCategoryId = matchedCatId,
            status = "PENDING"
        )

        db.capturedTransactionDao().insert(capturedEntity)
    }
}
