package com.vaibhav.moneytracker.csv

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.util.regex.Pattern

data class PDFExtractResult(
    val isEncrypted: Boolean = false,
    val isScanned: Boolean = false,
    val rawLines: List<String> = emptyList(),
    val parsedRows: List<PDFTransactionCandidate> = emptyList(),
    val totalDetectedBlocks: Int = 0,
    val errorMessage: String? = null
)

data class PDFTransactionCandidate(
    val rawDate: String,
    val narration: String,
    val amountPaise: Long?,
    val isExpense: Boolean,
    val isAmbiguous: Boolean = false,
    val isSummaryRow: Boolean = false,
    val summaryReason: String? = null
)

object PDFStatementParser {

    // Regex matching statement date prefixes e.g., 04-Sep-2026, 04-Sep-26, 04/09/2026, 04.09.26, 04 Sep 2026, 2026-09-04
    // Supports leading serial numbers e.g. "1  04/09/2026"
    private val datePrefixPattern = Pattern.compile(
        "^\\s*(?:\\d{1,4}\\s+)?(\\d{1,2}[/\\.\\-\\s]+(?:[A-Za-z]{3,9}|\\d{1,2})[/\\.\\-\\s]+\\d{2,4}|\\d{4}[/\\.\\-\\s]+\\d{1,2}[/\\.\\-\\s]+\\d{1,2}).*",
        Pattern.CASE_INSENSITIVE
    )

    // Regex matching monetary amount tokens (e.g. 500.00, 6,318.31, 37,182.00, ₹1,200.00, (500.00), 500.00 DR)
    // Requires word/whitespace boundary so reference numbers/slashes like UPI/658015506835/CR/VAIB are NOT matched as amounts
    private val decimalAmountRegex = Regex(
        "(?:^|\\s)[₹\$€]*[+-]?\\(?\\d+(?:[,.]\\d{2,3})*[,.]\\d{2}\\)?(?:\\s*(?:DR|CR|Dr|Cr))?(?=\\s|\$)",
        RegexOption.IGNORE_CASE
    )

    /**
     * Extracts structured text lines and parsed transaction candidates from a PDF input stream.
     * Processes ALL pages independently to support multi-page bank statements.
     */
    fun parsePdf(context: Context, inputStream: InputStream, password: String? = null): PDFExtractResult {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (e: Exception) {
            // Resource loader initialized
        }

        var document: PDDocument? = null
        try {
            document = if (!password.isNullOrBlank()) {
                PDDocument.load(inputStream, password)
            } else {
                PDDocument.load(inputStream)
            }

            val pageCount = document.numberOfPages
            val allRawLines = mutableListOf<String>()
            val allCandidates = mutableListOf<PDFTransactionCandidate>()
            var totalExtractedTextLength = 0

            for (pageIdx in 1..pageCount) {
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                stripper.startPage = pageIdx
                stripper.endPage = pageIdx

                val pageText = stripper.getText(document) ?: ""
                totalExtractedTextLength += pageText.replace(Regex("\\s+"), "").length

                val pageLines = pageText.lines().map { it.trim() }.filter { it.isNotBlank() }

                // Reconstruct multi-line statement blocks for this page
                val pageBlocks = groupLinesIntoBlocks(pageLines)
                allRawLines.addAll(pageBlocks)

                for (block in pageBlocks) {
                    val candidate = parseBlockToCandidate(block)
                    if (candidate != null) {
                        allCandidates.add(candidate)
                    }
                }
            }

            if (totalExtractedTextLength < 30 || (allCandidates.isEmpty() && isScannedText(allRawLines.joinToString(" ")))) {
                return PDFExtractResult(
                    isScanned = true,
                    errorMessage = "This PDF appears to be scanned/image-based and does not contain selectable transaction text. OCR is required to import it reliably."
                )
            }

            if (allCandidates.isEmpty() && allRawLines.isEmpty()) {
                return PDFExtractResult(
                    errorMessage = "No transaction rows could be detected from this PDF statement."
                )
            }

            return PDFExtractResult(
                isEncrypted = false,
                isScanned = false,
                rawLines = allRawLines,
                parsedRows = allCandidates,
                totalDetectedBlocks = allRawLines.size
            )

        } catch (e: InvalidPasswordException) {
            val msg = if (password.isNullOrBlank()) null else "Incorrect PDF password. Please try again."
            return PDFExtractResult(isEncrypted = true, errorMessage = msg)
        } catch (e: Exception) {
            val isEncryptedException = e.message?.contains("password", ignoreCase = true) == true ||
                    e.message?.contains("encrypted", ignoreCase = true) == true
            if (isEncryptedException) {
                val msg = if (password.isNullOrBlank()) null else "Incorrect PDF password. Please try again."
                return PDFExtractResult(isEncrypted = true, errorMessage = msg)
            }
            return PDFExtractResult(
                errorMessage = "Unable to read this PDF statement: ${e.localizedMessage ?: "Unknown error"}"
            )
        } finally {
            try {
                document?.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
        }
    }

    /**
     * Groups multi-line PDF statement text lines into complete transaction block strings.
     * Each transaction block starts with a line containing a transaction date.
     */
    fun groupLinesIntoBlocks(lines: List<String>): List<String> {
        val blocks = mutableListOf<String>()
        var currentBlock = StringBuilder()

        for (line in lines) {
            val matcher = datePrefixPattern.matcher(line)
            if (matcher.matches()) {
                if (currentBlock.isNotEmpty()) {
                    blocks.add(currentBlock.toString().trim())
                }
                currentBlock = StringBuilder(line)
            } else {
                if (currentBlock.isNotEmpty()) {
                    currentBlock.append(" ").append(line)
                }
            }
        }
        if (currentBlock.isNotEmpty()) {
            blocks.add(currentBlock.toString().trim())
        }

        return blocks
    }

    /**
     * Parses a single multi-line or single-line transaction block string.
     * Accurately isolates date, narration, transaction amount, and excludes running balance.
     */
    fun parseBlockToCandidate(blockStr: String): PDFTransactionCandidate? {
        val matcher = datePrefixPattern.matcher(blockStr)
        val (rawDate, cleanRemainder) = if (matcher.matches()) {
            val date = matcher.group(1)?.trim() ?: return null
            val dateEndIndex = matcher.end(1)
            val remainder = blockStr.substring(dateEndIndex).trim()
            if (remainder.isBlank()) return null

            // Strip duplicate Value Date if present immediately after Txn Date
            val strictSecondDatePattern = Pattern.compile("^(\\d{1,2}[/\\.\\-]\\d{1,2}[/\\.\\-]\\d{2,4}|\\d{1,2}-[A-Za-z]{3,9}-[0-9]{2,4})\\s+.*")
            val secondDateMatcher = strictSecondDatePattern.matcher(remainder)
            val cleanRem = if (secondDateMatcher.matches()) {
                val secondDateEnd = secondDateMatcher.end(1)
                remainder.substring(secondDateEnd).trim()
            } else {
                remainder
            }
            date to cleanRem
        } else {
            // Check for date embedded inside the narration string e.g., "Interest Cr. for 28-Aug-2026 0.01"
            val embeddedDate = CSVParser.findEmbeddedDate(blockStr) ?: return null
            embeddedDate to blockStr
        }

        if (cleanRemainder.isBlank()) return null

        // Find all valid monetary decimal amount tokens in the block
        val amountMatches = decimalAmountRegex.findAll(cleanRemainder).toList()
        if (amountMatches.isEmpty()) return null

        // Extract Narration (all text preceding the monetary amount tokens)
        val firstAmountStart = amountMatches.first().range.first
        var narration = cleanRemainder.substring(0, firstAmountStart).trim()
        if (narration.isBlank()) {
            narration = "Imported Transaction"
        }

        // Exclude non-transaction summary / balance rows (e.g. Brought Forward, Opening Balance)
        if (StatementFormatDetector.isNonTransactionRow(narration) || StatementFormatDetector.isNonTransactionRow(blockStr)) {
            return PDFTransactionCandidate(
                rawDate = rawDate,
                narration = narration,
                amountPaise = null,
                isExpense = false,
                isSummaryRow = true,
                summaryReason = "Opening / Summary Balance Row"
            )
        }

        val amountToken = if (amountMatches.size >= 2) {
            amountMatches[amountMatches.size - 2].value
        } else {
            amountMatches.last().value
        }

        val parsedPaise = StatementFormatDetector.normalizeAmount(
            debitVal = null,
            creditVal = null,
            amountVal = amountToken,
            typeVal = null,
            title = narration
        ) ?: CSVParser.parseAmountPaise(amountToken)

        if (parsedPaise == null || Math.abs(parsedPaise) == 0L) {
            return null
        }

        val isExpense = parsedPaise < 0L || narration.contains("/DR/", ignoreCase = true) || narration.contains(" DR", ignoreCase = true)
        val isAmbiguous = (amountMatches.size == 1 && !amountToken.contains("-") && !amountToken.contains("DR", ignoreCase = true) && !amountToken.contains("CR", ignoreCase = true))

        return PDFTransactionCandidate(
            rawDate = rawDate,
            narration = narration,
            amountPaise = Math.abs(parsedPaise),
            isExpense = isExpense,
            isAmbiguous = isAmbiguous
        )
    }

    private fun isScannedText(text: String): Boolean {
        val clean = text.replace(Regex("\\s+"), "")
        if (clean.length < 30) return true
        val digitCount = clean.count { it.isDigit() }
        return digitCount < 10
    }
}
