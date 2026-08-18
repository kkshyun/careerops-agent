package com.careerops.backend.pkbimport.extraction;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class DocxTextExtractor implements DocumentTextExtractor {
    @Override
    public boolean supports(String lowerCaseExtension) {
        return "docx".equals(lowerCaseExtension);
    }

    @Override
    public String extract(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            List<String> elements = new ArrayList<>();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    elements.add(paragraph.getText());
                } else if (element instanceof XWPFTable table) {
                    elements.add(tableText(table));
                }
            }
            return String.join("\n", elements);
        } catch (IOException | RuntimeException exception) {
            throw new DocumentExtractionException("DOCX extraction failed", exception);
        }
    }

    private String tableText(XWPFTable table) {
        List<String> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            rows.add(row.getTableCells().stream().map(cell -> cell.getText()).reduce((a, b) -> a + "\t" + b).orElse(""));
        }
        return String.join("\n", rows);
    }
}
