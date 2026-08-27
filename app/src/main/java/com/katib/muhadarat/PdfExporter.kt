package com.katib.muhadarat

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    fun exportAndSharePdf(context: Context, text: String) {
        if (text.isBlank()) {
            Toast.makeText(context, "لا يوجد نص لتصديره", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pdfFile = generatePdf(context, text)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "تفريغ محاضرة — كاتب المحاضرات")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "مشاركة / حفظ ملف PDF")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            Toast.makeText(context, "تم إنشاء ملف PDF بنجاح", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "فشل إنشاء PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun generatePdf(context: Context, text: String): File {
        val doc = PdfDocument()

        // مقاس صفحة A4 القياسي بالنقاط (595x842)
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40
        val contentWidth = pageWidth - (margin * 2)

        val titlePaint = Paint().apply {
            color = Color.rgb(15, 23, 42) // Slate 900
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val metaPaint = Paint().apply {
            color = Color.rgb(100, 116, 139) // Slate 500
            textSize = 10f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.rgb(203, 213, 225) // Slate 300
            strokeWidth = 1f
            isAntiAlias = true
        }

        val textPaint = TextPaint().apply {
            color = Color.rgb(30, 41, 59) // Slate 800
            textSize = 13f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val dateStr = SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale("ar")).format(Date())

        // قياس وتخطيط النص العربي بالكامل
        val staticLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, textPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(4f, 1.15f)
                .setIncludePad(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                textPaint,
                contentWidth,
                Layout.Alignment.ALIGN_NORMAL,
                1.15f,
                4f,
                true
            )
        }

        val lineCount = staticLayout.lineCount
        val headerHeight = 70
        val footerHeight = 40
        val printableHeight = pageHeight - margin - headerHeight - footerHeight

        var currentLine = 0
        var pageNumber = 1

        while (currentLine < lineCount) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            // ترويسة الصفحة الأولى
            var yOffset = margin.toFloat()
            if (pageNumber == 1) {
                canvas.drawText("كاتب المحاضرات 🎙️", margin.toFloat(), yOffset + 20, titlePaint)
                canvas.drawText("تاريخ الاستخراج: $dateStr", margin.toFloat(), yOffset + 38, metaPaint)
                canvas.drawLine(
                    margin.toFloat(),
                    yOffset + 48,
                    (pageWidth - margin).toFloat(),
                    yOffset + 48,
                    linePaint
                )
                yOffset += headerHeight
            } else {
                canvas.drawText("كاتب المحاضرات", margin.toFloat(), yOffset + 14, metaPaint)
                canvas.drawLine(
                    margin.toFloat(),
                    yOffset + 20,
                    (pageWidth - margin).toFloat(),
                    yOffset + 20,
                    linePaint
                )
                yOffset += 30
            }

            // رسم أسطر النص لهذه الصفحة
            val startLineForPage = currentLine
            var pageAccumulatedHeight = 0f

            while (currentLine < lineCount) {
                val lineHeight = staticLayout.getLineBottom(currentLine) - staticLayout.getLineTop(currentLine)
                if (pageAccumulatedHeight + lineHeight > printableHeight && currentLine > startLineForPage) {
                    break
                }
                pageAccumulatedHeight += lineHeight
                currentLine++
            }

            canvas.save()
            canvas.translate(margin.toFloat(), yOffset)
            
            // رسم الشريحة المحددة من الأسطر
            canvas.clipRect(0, 0, contentWidth, pageAccumulatedHeight.toInt() + 10)
            val topOffset = staticLayout.getLineTop(startLineForPage)
            canvas.translate(0f, -topOffset.toFloat())
            staticLayout.draw(canvas)
            canvas.restore()

            // تذييل الصفحة مع رقم الصفحة
            canvas.drawText(
                "صفحة $pageNumber",
                (pageWidth / 2 - 20).toFloat(),
                (pageHeight - 20).toFloat(),
                metaPaint
            )

            doc.finishPage(page)
            pageNumber++
        }

        val pdfDir = File(context.cacheDir, "exports")
        if (!pdfDir.exists()) pdfDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(pdfDir, "محاضرة_مفرغة_$timeStamp.pdf")

        FileOutputStream(outFile).use { fos ->
            doc.writeTo(fos)
        }
        doc.close()

        return outFile
    }
}
