package com.flowpilot.documents.internal.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
class PdfTextExtractor {

    static final int MAX_PAGES = 500;

    record PageText(int page, String text) {
    }

    record ExtractedDocument(int pageCount, List<PageText> pages) {
    }

    /** The caller closes the stream. */
    ExtractedDocument extract(InputStream content) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(content))) {
            int pageCount = document.getNumberOfPages();
            if (pageCount > MAX_PAGES) {
                throw new PermanentIngestionException("The PDF has more than " + MAX_PAGES + " pages.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            List<PageText> pages = new ArrayList<>(pageCount);
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(new PageText(page, stripper.getText(document).strip()));
            }
            return new ExtractedDocument(pageCount, pages);
        } catch (IOException e) {
            throw new PermanentIngestionException(
                    "The PDF could not be read. It may be corrupted or password-protected.", e);
        }
    }
}
