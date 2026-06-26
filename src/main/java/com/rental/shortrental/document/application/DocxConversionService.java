package com.rental.shortrental.document.application;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class DocxConversionService {

    public String convertToHtml(MultipartFile file) throws IOException {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            StringBuilder html = new StringBuilder();
            html.append("""
                <!DOCTYPE html>
                <html xmlns:th="http://www.thymeleaf.org">
                <head><meta charset="UTF-8"/><style>
                    body { font-family: 'DejaVu Serif', serif; font-size: 12pt; line-height: 1.5; color: #000; margin: 40px; }
                    h1 { text-align: center; font-size: 16pt; margin-bottom: 24px; }
                    h2 { font-size: 13pt; margin-top: 20px; margin-bottom: 10px; }
                    p { margin: 6px 0; }
                    table { width: 100%; border-collapse: collapse; margin: 12px 0; }
                    td, th { border: 1px solid #000; padding: 6px 10px; text-align: left; font-size: 11pt; }
                </style></head><body>
                """);

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String style = paragraph.getStyle();
                String text = paragraph.getText();
                if (text == null || text.isBlank()) continue;

                boolean isBold = false;
                boolean isItalic = false;
                int fontSize = 12;
                String alignment = "left";

                List<XWPFRun> runs = paragraph.getRuns();
                if (runs != null && !runs.isEmpty()) {
                    XWPFRun run = runs.get(0);
                    isBold = run.isBold();
                    isItalic = run.isItalic();
                    Double runFontSize = run.getFontSizeAsDouble();
                    if (runFontSize != null && runFontSize > 0) {
                        fontSize = runFontSize.intValue();
                    }
                }

                org.apache.poi.xwpf.usermodel.ParagraphAlignment align = paragraph.getAlignment();
                if (align != null) {
                    switch (align) {
                        case CENTER -> alignment = "center";
                        case RIGHT -> alignment = "right";
                        case BOTH -> alignment = "justify";
                    }
                }

                boolean isPlaceholder = text.contains("{{") || text.contains("${");

                if (isPlaceholder) {
                    html.append("<p").append(" style=\"text-align:").append(alignment).append(";");
                    if (isBold) html.append("font-weight:bold;");
                    html.append("\">").append(convertPlaceholders(text)).append("</p>\n");
                } else if (isBold && fontSize >= 14) {
                    html.append("<h2>").append(escapeHtml(text)).append("</h2>\n");
                } else {
                    html.append("<p").append(" style=\"text-align:").append(alignment).append(";");
                    if (isBold) html.append("font-weight:bold;");
                    html.append("\">").append(escapeHtml(text)).append("</p>\n");
                }
            }

            html.append("</body></html>");
            return html.toString();
        }
    }

    private String convertPlaceholders(String text) {
        return text
                .replace("{{landlordName}}", "<span th:text=\"${landlordName}\">________</span>")
                .replace("{{landlordPhone}}", "<span th:text=\"${landlordPhone}\">________</span>")
                .replace("{{guestName}}", "<span th:text=\"${guestName}\">________</span>")
                .replace("{{passportSeries}}", "<span th:text=\"${passportSeries}\">________</span>")
                .replace("{{passportNumber}}", "<span th:text=\"${passportNumber}\">________</span>")
                .replace("{{passportIssuedBy}}", "<span th:text=\"${passportIssuedBy}\">________</span>")
                .replace("{{passportIssueDate}}", "<span th:text=\"${passportIssueDate}\">________</span>")
                .replace("{{birthDate}}", "<span th:text=\"${birthDate}\">________</span>")
                .replace("{{guestPhone}}", "<span th:text=\"${guestPhone}\">________</span>")
                .replace("{{guestEmail}}", "<span th:text=\"${guestEmail}\">________</span>")
                .replace("{{propertyName}}", "<span th:text=\"${propertyName}\">________</span>")
                .replace("{{propertyAddress}}", "<span th:text=\"${propertyAddress}\">________</span>")
                .replace("{{startDate}}", "<span th:text=\"${startDate}\">________</span>")
                .replace("{{endDate}}", "<span th:text=\"${endDate}\">________</span>")
                .replace("{{advancePayment}}", "<span th:text=\"${advancePayment}\">________</span>")
                .replace("{{additionalTerms}}", "<span th:text=\"${additionalTerms}\">________</span>");
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
