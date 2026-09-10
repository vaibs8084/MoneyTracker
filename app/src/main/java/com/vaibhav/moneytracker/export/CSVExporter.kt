package com.vaibhav.moneytracker.export

import android.content.Context
import android.net.Uri
import com.vaibhav.moneytracker.TransactionUiModel
import com.vaibhav.moneytracker.formatDate
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale

data class ExportResult(
    val isSuccess: Boolean,
    val exportedCount: Int = 0,
    val errorMessage: String? = null
)

object CSVExporter {

    val CSV_HEADER = listOf("ID", "Date", "Title", "Type", "Amount", "Category", "Account", "Note", "Tags")

    fun formatCSVField(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    fun formatPaiseToDecimalString(amountPaise: Long): String {
        val isNegative = amountPaise < 0L
        val absPaise = Math.abs(amountPaise)
        val major = absPaise / 100L
        val minor = absPaise % 100L
        val sign = if (isNegative) "-" else ""
        return "$sign$major.${String.format(Locale.US, "%02d", minor)}"
    }

    fun exportToCSV(context: Context, uri: Uri, transactions: List<TransactionUiModel>): ExportResult {
        return try {
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: return ExportResult(isSuccess = false, errorMessage = "Unable to open target destination file stream")

            BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8)).use { writer ->
                // Write CSV Header
                writer.write(CSV_HEADER.joinToString(","))
                writer.newLine()

                // Stream rows
                transactions.forEach { tx ->
                    val tagsStr = tx.tags.joinToString("; ") { it.name }
                    val row = listOf(
                        tx.id.toString(),
                        formatCSVField(formatDate(tx.createdAt)),
                        formatCSVField(tx.title),
                        formatCSVField(tx.type),
                        formatPaiseToDecimalString(tx.amountPaise),
                        formatCSVField(tx.category),
                        formatCSVField(tx.account),
                        formatCSVField(tx.note),
                        formatCSVField(tagsStr)
                    )
                    writer.write(row.joinToString(","))
                    writer.newLine()
                }
                writer.flush()
            }

            ExportResult(isSuccess = true, exportedCount = transactions.size)
        } catch (e: Exception) {
            ExportResult(isSuccess = false, errorMessage = "Export failed: ${e.localizedMessage ?: "File write error"}")
        }
    }
}
