package com.vaibhav.moneytracker.capture

import org.junit.Assert.*
import org.junit.Test

class NotificationParserTest {

    @Test
    fun testPhonePeExpenseNotificationParsing() {
        val title = "PhonePe"
        val text = "Paid Rs. 500 to Swiggy. Txn ID: 623493409656"
        val candidate = NotificationParserManager.parseNotification("com.phonepe.app", title, text, System.currentTimeMillis())

        assertNotNull("PhonePe expense candidate should be parsed", candidate)
        assertEquals("Swiggy", candidate!!.title)
        assertEquals(50000L, candidate.amountPaise) // Rs. 500 = 50000 paise
        assertEquals("Expense", candidate.type)
        assertEquals("PhonePe", candidate.sourceApp)
        assertEquals("623493409656", candidate.referenceNumber)
    }

    @Test
    fun testPaytmCreditNotificationParsing() {
        val title = "Paytm"
        val text = "Received Rs. 1,250.50 from Ramesh. UTR: 987654321012"
        val candidate = NotificationParserManager.parseNotification("net.one97.paytm", title, text, System.currentTimeMillis())

        assertNotNull("Paytm credit candidate should be parsed", candidate)
        assertEquals("Ramesh", candidate!!.title)
        assertEquals(125050L, candidate.amountPaise) // 1250.50 = 125050 paise
        assertEquals("Income", candidate.type)
        assertEquals("Paytm", candidate.sourceApp)
        assertEquals("987654321012", candidate.referenceNumber)
    }

    @Test
    fun testGooglePayExpenseParsing() {
        val title = "Google Pay"
        val text = "Paid Rs 350.00 to Starbucks using HDFC Bank"
        val candidate = NotificationParserManager.parseNotification("com.google.android.apps.nbu.paisa.user", title, text, System.currentTimeMillis())

        assertNotNull(candidate)
        assertEquals("Starbucks", candidate!!.title)
        assertEquals(35000L, candidate.amountPaise)
        assertEquals("Expense", candidate.type)
        assertEquals("Google Pay", candidate.sourceApp)
    }

    @Test
    fun testBankDebitedNotificationParsing() {
        val title = "Kotak Bank Alert"
        val text = "Your A/c 1234 is debited by Rs. 2,000.00 at Amazon. Ref: 624116734625"
        val candidate = NotificationParserManager.parseNotification("com.kotak.mobilebanking", title, text, System.currentTimeMillis())

        assertNotNull(candidate)
        assertEquals("Amazon", candidate!!.title)
        assertEquals(200000L, candidate.amountPaise)
        assertEquals("Expense", candidate.type)
        assertEquals("Kotak Bank", candidate.sourceApp)
        assertEquals("624116734625", candidate.referenceNumber)
    }

    @Test
    fun testBankCreditedNotificationParsing() {
        val title = "HDFC Bank Alert"
        val text = "Your A/c 5678 is credited with Rs. 50,000.00 towards Salary"
        val candidate = NotificationParserManager.parseNotification("com.snapwork.hdfc", title, text, System.currentTimeMillis())

        assertNotNull(candidate)
        assertEquals("Salary", candidate!!.title)
        assertEquals(5000000L, candidate.amountPaise)
        assertEquals("Income", candidate.type)
        assertEquals("HDFC Bank", candidate.sourceApp)
    }

    @Test
    fun testOTPNotificationIsIgnored() {
        val title = "Bank OTP"
        val text = "Your secret OTP for transaction is 123456. Do not share with anyone."
        val candidate = NotificationParserManager.parseNotification("com.bank.app", title, text, System.currentTimeMillis())

        assertNull("OTP notifications must be strictly ignored", candidate)
    }

    @Test
    fun testPromotionalNotificationIsIgnored() {
        val title = "Special Offer"
        val text = "Get up to 50% discount and cashback on your next order. Order now!"
        val candidate = NotificationParserManager.parseNotification("com.phonepe.app", title, text, System.currentTimeMillis())

        assertNull("Promotional offer notifications must be strictly ignored", candidate)
    }

    @Test
    fun testFailedTransactionNotificationIsIgnoredOrFlagged() {
        val title = "Transaction Failed"
        val text = "Your payment of Rs. 500 to Swiggy failed due to technical error."
        val candidate = NotificationParserManager.parseNotification("net.one97.paytm", title, text, System.currentTimeMillis())

        assertTrue("Failed transaction should be marked isIgnoredOrFailed", candidate == null || candidate.isIgnoredOrFailed)
    }
}
