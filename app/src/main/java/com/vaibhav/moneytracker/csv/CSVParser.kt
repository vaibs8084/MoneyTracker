package com.vaibhav.moneytracker.csv

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

data class ParsedCSV(
    val headers: List<String>,
    val rows: List<List<String>>,
    val delimiter: Char,
    val headerRowIndex: Int = 0
)

object CSVParser {

    private val delimiters = listOf(',', ';', '\t')

    // Regex matching dates embedded anywhere inside narration/text
    // Matches formats: 28-Aug-2026, 28/08/2026, 28.08.2026, 2026-08-28, 28-Aug-26, 28 Aug 2026
    private val embeddedDateRegex = Regex(
        "(?:^|\\s|\\b)(\\d{1,2}[/\\.\\-][A-Za-z]{3,9}[/\\.\\-]\\d{2,4}|\\d{1,2}[/\\.\\-]\\d{1,2}[/\\.\\-]\\d{2,4}|\\d{4}[/\\.\\-]\\d{1,2}[/\\.\\-]\\d{1,2}|\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{2,4})(?:\\b|\\s|\$)",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses a CSV string into a list of rows.
     * Automatically detects delimiter and locates the actual table header row.
     */
    fun parse(content: String): ParsedCSV {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return ParsedCSV(emptyList(), emptyList(), ',')

        val delimiter = detectDelimiter(lines)
        val allRows = lines.map { parseLine(it, delimiter) }

        // Detect header row index by searching for bank column terms
        val headerRowIdx = findHeaderRowIndex(allRows)
        val headers = allRows.getOrNull(headerRowIdx) ?: emptyList()
        val dataRows = if (allRows.size > headerRowIdx + 1) allRows.drop(headerRowIdx + 1) else emptyList()

        return ParsedCSV(headers, dataRows, delimiter, headerRowIdx)
    }

    private fun detectDelimiter(lines: List<String>): Char {
        val sample = lines.take(5)
        return delimiters.maxByOrNull { delimiter ->
            sample.sumOf { line -> line.count { it == delimiter } }
        } ?: ','
    }

    private fun findHeaderRowIndex(allRows: List<List<String>>): Int {
        val headerKeywords = listOf("date", "txn date", "narration", "description", "particulars", "debit", "credit", "withdrawal", "deposit", "amount", "balance")

        for (i in 0 until minOf(allRows.size, 15)) {
            val rowStr = allRows[i].joinToString(" ").lowercase(Locale.getDefault())
            val matchCount = headerKeywords.count { rowStr.contains(it) }
            if (matchCount >= 2) {
                return i
            }
        }
        return 0
    }

    private fun parseLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        var currentField = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    currentField.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == delimiter && !inQuotes) {
                result.add(currentField.toString().trim())
                currentField = StringBuilder()
            } else {
                currentField.append(c)
            }
            i++
        }
        result.add(currentField.toString().trim())
        return result
    }

    /**
     * Extracts an embedded date token from a string if present.
     */
    fun findEmbeddedDate(value: String): String? {
        val match = embeddedDateRegex.find(value)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    /**
     * Attempts to parse a string as a date.
     * Supports case-insensitive month names, 2-digit/4-digit years, and dates embedded inside narrations.
     */
    fun parseDate(value: String): Long? {
        val raw = value.trim()
        if (raw.isBlank()) return null

        // Try direct parse first
        val directParse = tryParseDirect(raw)
        if (directParse != null) return directParse

        // Try extracting embedded date inside string e.g. "Interest Cr. for 28-Aug-2026"
        val embeddedToken = findEmbeddedDate(raw)
        if (embeddedToken != null && embeddedToken != raw) {
            val embeddedParse = tryParseDirect(embeddedToken)
            if (embeddedParse != null) return embeddedParse
        }

        return null
    }

    private fun tryParseDirect(cleanValue: String): Long? {
        val dateOnly = cleanValue.split(" ").firstOrNull() ?: cleanValue

        val formats = listOf(
            // 4-digit year
            "dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "MM/dd/yyyy",
            "dd MMM yyyy", "dd MMMM yyyy", "yyyy/MM/dd", "dd.MM.yyyy", "dd-MMM-yyyy",
            // 2-digit year
            "dd/MM/yy", "dd-MM-yy", "dd-MMM-yy", "dd MMM yy", "MM/dd/yy", "yy-MM-dd",
            // Full Date-Times
            "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm", "yyyy-MM-dd'T'HH:mm:ss"
        )

        // Title-case cleanValue for month parsing e.g., "28-AUG-2026" -> "28-Aug-2026"
        val formattedValue = normalizeMonthCasing(cleanValue)
        val formattedDateOnly = normalizeMonthCasing(dateOnly)

        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.ENGLISH)
                sdf.isLenient = false
                val parsed = sdf.parse(formattedValue) ?: sdf.parse(formattedDateOnly)
                if (parsed != null) return parsed.time
            } catch (e: Exception) {
                // Continue
            }
        }

        return cleanValue.toLongOrNull()
    }

    private fun normalizeMonthCasing(str: String): String {
        val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        var result = str
        for (m in months) {
            val idx = result.indexOf(m, ignoreCase = true)
            if (idx != -1) {
                val found = result.substring(idx, idx + m.length)
                val titleCased = found.lowercase(Locale.ENGLISH).replaceFirstChar { it.uppercase() }
                result = result.replace(found, titleCased)
            }
        }
        return result
    }

    /**
     * Parses an amount string into paise (Long).
     * Handles currency symbols, thousands separators (both . and ,).
     */
    fun parseAmountPaise(value: String): Long? {
        val raw = value.trim()
        if (raw.isBlank() || raw == "-" || raw == "--" || raw == "N/A") return null

        val hasParens = raw.contains('(') && raw.contains(')')

        val clean = raw.replace(Regex("[^0-9.,\\-]"), "")
        if (clean.isBlank()) return null

        val lastComma = clean.lastIndexOf(',')
        val lastDot = clean.lastIndexOf('.')

        val (normalized, isNegative) = when {
            clean.startsWith("-") -> clean.substring(1) to true
            hasParens -> clean to true
            else -> clean to false
        }

        val parsedValue: Double = try {
            if (lastComma > lastDot && lastComma != -1) {
                val s = normalized.replace(".", "").replace(',', '.')
                s.toDouble()
            } else {
                val s = normalized.replace(",", "")
                s.toDouble()
            }
        } catch (e: Exception) {
            return null
        }

        val paise = Math.round(parsedValue * 100.0)
        return if (isNegative) -paise else paise
    }
}
