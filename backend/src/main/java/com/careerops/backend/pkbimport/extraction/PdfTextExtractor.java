package com.careerops.backend.pkbimport.extraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class PdfTextExtractor implements DocumentTextExtractor {
    @Override
    public boolean supports(String lowerCaseExtension) {
        return "pdf".equals(lowerCaseExtension);
    }

    @Override
    public String extract(InputStream inputStream) {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            if (document.isEncrypted()) {
                throw new DocumentExtractionException("password protected");
            }
            return new PDFTextStripper().getText(document);
        } catch (DocumentExtractionException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DocumentExtractionException("PDF extraction failed", exception);
        }
    }
}
