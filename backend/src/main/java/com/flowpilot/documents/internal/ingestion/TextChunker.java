package com.flowpilot.documents.internal.ingestion;

import java.util.ArrayList;
import java.util.List;

/** Splits page texts into overlapping passages, remembering which pages each passage covers. */
final class TextChunker {

    record Chunk(int index, int pageFrom, int pageTo, String content, int tokenCount) {
    }

    private TextChunker() {
    }

    static List<Chunk> chunk(List<PdfTextExtractor.PageText> pages, int maxChars, int overlapChars) {
        if (overlapChars < 0 || maxChars < overlapChars * 2 + 10) {
            throw new IllegalArgumentException("maxChars must be much larger than overlapChars.");
        }
        // a passage = overlap tail + separator + one piece, so pieces are limited to keep it under maxChars
        int pieceLimit = maxChars - overlapChars - 2;
        List<Chunk> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int from = 0;
        int to = 0;
        for (PdfTextExtractor.PageText page : pages) {
            for (String piece : pieces(page.text(), pieceLimit)) {
                if (!buffer.isEmpty() && buffer.length() + 2 + piece.length() > maxChars) {
                    chunks.add(newChunk(chunks.size(), from, to, buffer));
                    String tail = overlapTail(buffer, overlapChars);
                    buffer.setLength(0);
                    buffer.append(tail);
                    from = to;   // the overlap comes from the last page of the previous passage
                }
                if (buffer.isEmpty()) {
                    from = page.page();
                } else {
                    buffer.append("\n\n");
                }
                buffer.append(piece);
                to = page.page();
            }
        }
        if (!buffer.isEmpty()) {
            chunks.add(newChunk(chunks.size(), from, to, buffer));
        }
        return chunks;
    }

    private static Chunk newChunk(int index, int from, int to, StringBuilder buffer) {
        String content = buffer.toString().strip();
        // rough estimate: one token is about four characters
        return new Chunk(index, from, to, content, Math.max(1, (content.length() + 3) / 4));
    }

    private static List<String> pieces(String text, int limit) {
        List<String> result = new ArrayList<>();
        for (String paragraph : text.split("\\n\\s*\\n")) {
            String rest = paragraph.replaceAll("\\s+", " ").strip();
            while (rest.length() > limit) {
                int cut = breakPoint(rest, limit);
                result.add(rest.substring(0, cut).strip());
                rest = rest.substring(cut).strip();
            }
            if (!rest.isEmpty()) {
                result.add(rest);
            }
        }
        return result;
    }

    /** Prefers the end of a sentence, then a space, and only cuts inside a word as a last resort. */
    private static int breakPoint(String text, int limit) {
        int cut = text.lastIndexOf(". ", limit - 1);
        if (cut < limit / 2) {
            cut = text.lastIndexOf(' ', limit - 1);
        }
        return cut < limit / 2 ? limit : cut + 1;
    }

    private static String overlapTail(StringBuilder buffer, int overlapChars) {
        if (overlapChars == 0 || buffer.length() <= overlapChars) {
            return "";
        }
        String tail = buffer.substring(buffer.length() - overlapChars);
        int space = tail.indexOf(' ');
        return space >= 0 ? tail.substring(space + 1) : "";   // never start inside a word
    }
}
