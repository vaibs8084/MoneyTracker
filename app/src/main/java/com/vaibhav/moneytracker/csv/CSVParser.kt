package com.vaibhav.moneytracker.csv

import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

data class ParsedCSV(
    val headers: List<String>,
    val rows: List<List<String>>,
    val delimiter: Char
)

object CSVParser {

    private val delimiters = listOf(',', ';', '\t')

    /**
     * Parses a CSV string into a list of rows.
     * Auto-detects delimiter based on the first line.
     */
    fun parse(content: String): ParsedCSV {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return ParsedCSV(emptyList(), emptyList(), ',')

        val delimiter = detectDelimiter(lines[0])
        val allRows = lines.map { parseLine(it, delimiter) }
        
        val headers = allRows.firstOrNull() ?: emptyList()
        val dataRows = if (allRows.size > 1) allRows.drop(1) else emptyList()

        return ParsedCSV(headers, dataRows, delimiter)
    }

    private fun detectDelimiter(line: String): Char {
        return delimiters.maxByOrNull { delimiter ->
            line.count { it == delimiter }
        } ?: ','
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
                    // Escaped quote
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
     * Attempts to parse a string as a date.
     */
    fun parseDate(value: String): Long? {
        val formats = listOf(
            "dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "MM/dd/yyyy",
            "dd MMM yyyy", "dd MMMM yyyy", "yyyy/MM/dd", "dd.MM.yyyy"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                sdf.isLenient = false
                return sdf.parse(value)?.time
            } catch (e: Exception) {
                // Continue
            }
        }
        // Try parsing as timestamp
        return value.toLongOrNull()
    }

    /**
     * Parses an amount string into paise (Long).
     * Handles currency symbols, thousands separators (both . and ,).
     */
    fun parseAmountPaise(value: String): Long? {
        // Handle negative parentheses: (1,000) -> -1,000
        val hasParens = value.contains('(') && value.contains(')')
        
        // Remove everything except digits, decimal point, comma, and minus sign
        val clean = value.replace(Regex("[^0-9.,\\-]"), "")
        if (clean.isBlank()) return null

        // Decide if it's European style (1.234,56) or US/Indian style (1,234.56)
        val lastComma = clean.lastIndexOf(',')
        val lastDot = clean.lastIndexOf('.')
        
        val (normalized, isNegative) = when {
            clean.startsWith("-") -> clean.substring(1) to true
            hasParens -> clean to true
            else -> clean to false
        }

        val parsedValue: Double = try {
            if (lastComma > lastDot) {
                // Likely European: 1.234,56 -> 1234.56
                val s = normalized.replace(".", "").replace(',', '.')
                s.toDouble()
            } else {
                // Likely US: 1,234.56 -> 1234.56
                val s = normalized.replace(",", "")
                s.toDouble()
            }
        } catch (e: Exception) {
            return null
        }

        val paise = (parsedValue * 100.0).toLong()
        return if (isNegative) -paise else paise
    }
}
