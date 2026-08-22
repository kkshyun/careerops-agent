package com.careerops.backend.pkbimport.extraction.llm;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DocumentChunker {
    static final int CHUNK_THRESHOLD = 20_000;
    static final int TARGET_CHUNK_SIZE = 12_000;
    private static final Pattern PARAGRAPH_END = Pattern.compile("(?:\\R[\\t ]*){2,}");

    public List<String> chunk(String rawText) {
        if (rawText.length() < CHUNK_THRESHOLD) return List.of(rawText);

        List<String> units = paragraphUnits(rawText);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            if (unit.length() > TARGET_CHUNK_SIZE) {
                flush(current, chunks);
                for (int start = 0; start < unit.length(); start += TARGET_CHUNK_SIZE) {
                    chunks.add(unit.substring(start, Math.min(start + TARGET_CHUNK_SIZE, unit.length())));
                }
            } else {
                if (!current.isEmpty() && current.length() + unit.length() > TARGET_CHUNK_SIZE) {
                    flush(current, chunks);
                }
                current.append(unit);
            }
        }
        flush(current, chunks);
        return List.copyOf(chunks);
    }

    private List<String> paragraphUnits(String rawText) {
        List<String> units = new ArrayList<>();
        Matcher matcher = PARAGRAPH_END.matcher(rawText);
        int start = 0;
        while (matcher.find()) {
            units.add(rawText.substring(start, matcher.end()));
            start = matcher.end();
        }
        if (start < rawText.length()) units.add(rawText.substring(start));
        return units;
    }

    private void flush(StringBuilder current, List<String> chunks) {
        if (!current.isEmpty()) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }
}
