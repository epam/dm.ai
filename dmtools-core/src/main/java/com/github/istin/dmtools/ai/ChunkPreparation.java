package com.github.istin.dmtools.ai;

import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.utils.PropertyReader;
import lombok.Data;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChunkPreparation {
    // Configuration fields
    @Getter
    private final int tokenLimit;
    private final long maxSingleFileSize;
    private final long maxTotalFilesSize;
    private final int maxFilesPerChunk;

    private final TokenCounter tokenCounter;

    public ChunkPreparation() {
        this(new Claude35TokenCounter());
    }

    public ChunkPreparation(TokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter;
        PropertyReader propertyReader = new PropertyReader();
        // Initialize configuration fields from PropertyReader
        this.tokenLimit = propertyReader.getPromptChunkTokenLimit();
        this.maxSingleFileSize = propertyReader.getPromptChunkMaxSingleFileSize();
        this.maxTotalFilesSize = propertyReader.getPromptChunkMaxTotalFilesSize();
        this.maxFilesPerChunk = propertyReader.getPromptChunkMaxFiles();
    }

    /**
     * Represents a chunk of content including both text and files
     */
    @Data
    public static class Chunk {
        private final String text;
        private List<File> files;
        private final long totalFilesSize;

        public Chunk(String text, List<File> files, long totalFilesSize) {
            this.text = text;
            if (files != null) {
                this.files = Collections.unmodifiableList(files);
            }
            this.totalFilesSize = totalFilesSize;
        }

        public String getText() {
            return text;
        }

        public List<File> getFiles() {
            return files;
        }

        public long getTotalFilesSize() {
            return totalFilesSize;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append(text);
            if (files != null && !files.isEmpty()) {
                result.append("\nfiles:\n").append(files);
            }
            return result.toString();
        }

    }

    private static class ChunkAccumulator {
        StringBuilder text = new StringBuilder();
        List<File> files = new ArrayList<>();
        int tokenCount = 0;
        long filesSize = 0;

        void reset() {
            text.setLength(0);
            files.clear();
            tokenCount = 0;
            filesSize = 0;
        }

        Chunk toChunk() {
            return new Chunk(text.toString(), new ArrayList<>(files), filesSize);
        }

        boolean isEmpty() {
            return text.isEmpty() && files.isEmpty();
        }
    }

    /**
     * Attempts to add a file to the current chunk accumulator
     * @return true if the file was added, false if a new chunk needs to be created
     */
    private boolean tryAddFile(File file, ChunkAccumulator current) {
        if (file.length() > maxSingleFileSize) {
            return true; // Skip this file but don't create new chunk
        }

        if (current.files.size() >= maxFilesPerChunk ||
                current.filesSize + file.length() > maxTotalFilesSize) {
            return false; // Need new chunk
        }

        current.files.add(file);
        current.filesSize += file.length();
        return true;
    }

    /**
     * Prepares a collection of objects (Strings, Files, and Map.Entries) into chunks
     */
    public List<Chunk> prepareChunks(Collection<?> objects, int tokenLimit) throws IOException {
        List<Chunk> chunks = new ArrayList<>();
        ChunkAccumulator current = new ChunkAccumulator();

        for (Object obj : objects) {
            if (obj instanceof Map.Entry<?, ?> entry) {
                Object value = entry.getValue();

                if (value instanceof File file) {
                    String keyText = entry.getKey().toString();
                    int keyTokens = tokenCounter.countTokens(keyText);

                    // Check if adding the key text would exceed token limit
                    if (current.tokenCount + keyTokens > tokenLimit) {
                        if (!current.isEmpty()) {
                            chunks.add(current.toChunk());
                            current.reset();
                        }
                    }

                    // Try to add the file
                    if (!tryAddFile(file, current)) {
                        if (!current.isEmpty()) {
                            chunks.add(current.toChunk());
                            current.reset();
                        }
                        tryAddFile(file, current); // Now it should succeed with empty chunk
                    }

                    // Add key text
                    if (!current.text.isEmpty()) {
                        current.text.append(",\n");
                    }
                    current.text.append(keyText);
                    current.tokenCount += keyTokens;
                } else {
                    // Handle non-File Map.Entry as regular text
                    processText(entry.toString(), chunks, current, tokenLimit);
                }
            } else if (obj instanceof File file) {
                if (!tryAddFile(file, current)) {
                    if (!current.isEmpty()) {
                        chunks.add(current.toChunk());
                        current.reset();
                    }
                    tryAddFile(file, current); // Now it should succeed with empty chunk
                }
            } else if (obj instanceof ToText) {
                processText(((ToText)obj).toText(), chunks, current, tokenLimit);
            } else {
                processText(obj.toString(), chunks, current, tokenLimit);
            }
        }

        // Add the last chunk if it's not empty
        if (!current.isEmpty()) {
            chunks.add(current.toChunk());
        }

        return chunks;
    }

    private void processText(String text, List<Chunk> chunks, ChunkAccumulator current, int tokenLimit) {
        int textTokens = tokenCounter.countTokens(text);

        // If single text exceeds token limit, split it
        if (textTokens > tokenLimit) {
            if (!current.isEmpty()) {
                chunks.add(current.toChunk());
                current.reset();
            }
            chunks.addAll(splitLargeObject(text, tokenLimit));
            return;
        }

        // Check if adding this text would exceed the token limit
        if (current.tokenCount + textTokens > tokenLimit) {
            chunks.add(current.toChunk());
            current.reset();
        }

        // Add text to current chunk
        if (!current.text.isEmpty()) {
            current.text.append("\n");
        }
        current.text.append(text);
        current.tokenCount += textTokens;
    }

    /**
     * Overloaded method using default token limit
     */
    public List<Chunk> prepareChunks(Collection<?> objects) throws IOException {
        return prepareChunks(objects, tokenLimit);
    }

    /**
     * Splits a large text that exceeds token limit into smaller chunks
     * with improved splitting at natural boundaries like commas, closing brackets, and newlines
     */
    private List<Chunk> splitLargeObject(String largeString, int tokenLimit) {
        List<Chunk> chunks = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        int currentTokens = 0;

        // Define a pattern for natural break points
        // Priority: commas/brackets > newlines > spaces
        Pattern breakPattern = Pattern.compile("([,}\\]]|\\n|\\s)");
        Matcher matcher = breakPattern.matcher(largeString);

        int lastEnd = 0;
        int breakPriority = 0; // 0=none, 1=space, 2=newline, 3=comma/bracket

        while (lastEnd < largeString.length()) {
            // Find the next potential break point
            boolean foundBreak = matcher.find(lastEnd);
            int breakPoint = foundBreak ? matcher.end() : largeString.length();
            String segment = largeString.substring(lastEnd, breakPoint);

            int segmentTokens = tokenCounter.countTokens(segment);

            // Determine break priority
            if (foundBreak) {
                char breakChar = largeString.charAt(matcher.start());
                if (breakChar == ',' || breakChar == '}' || breakChar == ']') {
                    breakPriority = 3; // Highest priority
                } else if (breakChar == '\n') {
                    breakPriority = 2; // Second priority
                } else {
                    breakPriority = 1; // Lowest priority (space)
                }
            }

            // If adding this segment would exceed the token limit
            if (currentTokens + segmentTokens > tokenLimit) {
                // If we're at a good break point or the segment is too large
                if (breakPriority >= 2 || segmentTokens > tokenLimit / 2) {
                    // Finalize current chunk
                    if (!chunk.isEmpty()) {
                        chunks.add(new Chunk(chunk.toString(), new ArrayList<>(), 0));
                        chunk = new StringBuilder();
                        currentTokens = 0;
                    }

                    // If the segment itself is too large, we need to split it further
                    if (segmentTokens > tokenLimit) {
                        // Look for better break points within this segment
                        int bestBreakPos = findBestBreakPosition(segment, tokenLimit - currentTokens);

                        if (bestBreakPos > 0) {
                            // Add partial segment up to the best break point
                            chunk.append(segment.substring(0, bestBreakPos));
                            chunks.add(new Chunk(chunk.toString(), new ArrayList<>(), 0));

                            // Process the rest of the segment in the next iteration
                            lastEnd += bestBreakPos;
                            chunk = new StringBuilder();
                            currentTokens = 0;
                            breakPriority = 0;
                            continue;
                        } else {
                            // Split by characters as a last resort
                            int charsToAdd = Math.max(1, (tokenLimit - currentTokens) / 2);
                            chunk.append(segment.substring(0, charsToAdd));
                            chunks.add(new Chunk(chunk.toString(), new ArrayList<>(), 0));

                            lastEnd += charsToAdd;
                            chunk = new StringBuilder();
                            currentTokens = 0;
                            breakPriority = 0;
                            continue;
                        }
                    }
                } else {
                    // Try to find a better break point
                    int lookAhead = lastEnd;
                    int bestBreak = lastEnd;
                    int bestPriority = 0;

                    // Look ahead for a better break point within a reasonable distance
                    while (lookAhead < lastEnd + 200 && lookAhead < largeString.length()) {
                        char c = largeString.charAt(lookAhead);
                        int priority = 0;

                        if (c == ',' || c == '}' || c == ']') {
                            priority = 3;
                        } else if (c == '\n') {
                            priority = 2;
                        } else if (Character.isWhitespace(c)) {
                            priority = 1;
                        }

                        if (priority > bestPriority) {
                            bestBreak = lookAhead + 1; // Include the break character
                            bestPriority = priority;

                            // If we found a high-priority break, we can stop looking
                            if (priority >= 2) break;
                        }

                        lookAhead++;
                    }

                    // If we found a better break point, use it
                    if (bestBreak > lastEnd) {
                        segment = largeString.substring(lastEnd, bestBreak);
                        segmentTokens = tokenCounter.countTokens(segment);
                        breakPoint = bestBreak;
                        breakPriority = bestPriority;
                    }

                    // If the segment is still too large, finalize current chunk
                    if (currentTokens + segmentTokens > tokenLimit) {
                        chunks.add(new Chunk(chunk.toString(), new ArrayList<>(), 0));
                        chunk = new StringBuilder();
                        currentTokens = 0;
                    }
                }
            }

            // Add the segment to the current chunk
            chunk.append(segment);
            currentTokens += segmentTokens;
            lastEnd = breakPoint;
        }

        // Add the final chunk if not empty
        if (!chunk.isEmpty()) {
            chunks.add(new Chunk(chunk.toString(), new ArrayList<>(), 0));
        }

        return chunks;
    }

    /**
     * Helper method to find the best position to break a segment
     * Prioritizes commas/brackets > newlines > spaces
     */
    private int findBestBreakPosition(String segment, int tokenBudget) {
        int bestPos = 0;
        int bestPriority = 0;
        int currentTokens = 0;

        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            String charStr = String.valueOf(c);
            currentTokens += tokenCounter.countTokens(charStr);

            if (currentTokens > tokenBudget) {
                break;
            }

            int priority = 0;
            if (c == ',' || c == '}' || c == ']') {
                priority = 3;
            } else if (c == '\n') {
                priority = 2;
            } else if (Character.isWhitespace(c)) {
                priority = 1;
            }

            if (priority > 0) {
                bestPos = i + 1; // Include the break character
                bestPriority = priority;
            }
        }

        return bestPos;
    }
}

/**
 * Interface for token counting implementation
 */
interface TokenCounter {
    int countTokens(String text);
}

