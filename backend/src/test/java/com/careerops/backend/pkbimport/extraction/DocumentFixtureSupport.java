package com.careerops.backend.pkbimport.extraction;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class DocumentFixtureSupport {
    private DocumentFixtureSupport() {}

    public static byte[] pdf(String... pages) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (text != null) {
                    try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        content.newLineAtOffset(50, 700);
                        content.showText(text);
                        content.endText();
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    public static byte[] protectedPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.showText(text);
                content.endText();
            }
            document.protect(new StandardProtectionPolicy("owner-password", "user-password", new AccessPermission()));
            document.save(output);
            return output.toByteArray();
        }
    }

    public static byte[] docx(String before, boolean table, String after) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (before != null) document.createParagraph().createRun().setText(before);
            if (table) {
                XWPFTable xwpfTable = document.createTable(1, 2);
                xwpfTable.getRow(0).getCell(0).setText("left-cell");
                xwpfTable.getRow(0).getCell(1).setText("right-cell");
            }
            if (after != null) document.createParagraph().createRun().setText(after);
            document.write(output);
            return output.toByteArray();
        }
    }
}
