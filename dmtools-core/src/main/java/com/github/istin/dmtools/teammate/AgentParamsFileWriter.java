// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.ai.agent.RequestDecompositionAgent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Writes Teammate agent params (aiRole, instructions, knownInfo, formattingRules, fewShots)
 * as individual files into the CLI input folder when {@code writeAgentParamsToFiles} is enabled.
 *
 * <p>This allows CLI agents (Cursor, Claude CLI, etc.) to read large context files
 * directly from the {@code input/[TICKET]/} folder rather than receiving everything
 * embedded inside one huge {@code request.md}.</p>
 *
 * <h3>Writing rules per field</h3>
 * <table>
 *   <tr><th>Value type</th><th>Action</th></tr>
 *   <tr><td>URL (https:// or GitHub)</td><td>Fetch content → write to file</td></tr>
 *   <tr><td>File path (/, ./, ../)</td><td>Read content → copy to file</td></tr>
 *   <tr><td>Plain text</td><td>For arrays: collect all into one combined file.
 *       For single fields: leave as-is (not written to a file).</td></tr>
 * </table>
 *
 * <h3>Output structure</h3>
 * <pre>
 * input/[TICKET]/
 *   instructions/
 *     instruction_001.md     ← each URL or file-path entry
 *     instruction_002.md
 *     instructions_text.md   ← all plain-text entries combined
 *   ai_role.md               ← if aiRole was a URL/path
 *   known_info.md            ← if knownInfo was a URL/path
 *   formatting_rules.md      ← if formattingRules was a URL/path
 *   few_shots.md             ← if fewShots was a URL/path
 * </pre>
 */
public class AgentParamsFileWriter {

    private static final Logger logger = LogManager.getLogger(AgentParamsFileWriter.class);

    static final String INSTRUCTIONS_SUBFOLDER = "instructions";
    static final String INSTRUCTIONS_TEXT_FILE  = "instructions_text.md";

    private final InstructionProcessor instructionProcessor;

    public AgentParamsFileWriter(InstructionProcessor instructionProcessor) {
        this.instructionProcessor = instructionProcessor;
    }

    /**
     * Writes agent params to individual files inside {@code inputFolderPath}.
     *
     * @param inputFolderPath Path to the already-created {@code input/[TICKET]/} folder
     * @param originalParams  The agent params snapshot taken <em>before</em>
     *                        {@link InstructionProcessor} resolves URLs/paths to content
     * @throws IOException if any file write fails
     */
    public void writeToInputFolder(Path inputFolderPath,
                                   RequestDecompositionAgent.Result originalParams) throws IOException {
        if (inputFolderPath == null || originalParams == null) return;

        writeInstructions(inputFolderPath, originalParams.getInstructions());
        writeSingleFieldIfExtractable(inputFolderPath, "ai_role.md",         originalParams.getAiRole());
        writeSingleFieldIfExtractable(inputFolderPath, "known_info.md",      originalParams.getKnownInfo());
        writeSingleFieldIfExtractable(inputFolderPath, "formatting_rules.md",originalParams.getFormattingRules());
        writeSingleFieldIfExtractable(inputFolderPath, "few_shots.md",       originalParams.getFewShots());
    }

    /**
     * Processes the {@code instructions} array:
     * <ul>
     *   <li>URLs and file paths → extracted and written as separate numbered files</li>
     *   <li>Plain-text entries → combined into one {@code instructions_text.md}</li>
     * </ul>
     */
    void writeInstructions(Path inputFolderPath, String[] instructions) throws IOException {
        if (instructions == null || instructions.length == 0) return;

        Path instrFolder = inputFolderPath.resolve(INSTRUCTIONS_SUBFOLDER);
        Files.createDirectories(instrFolder);

        StringBuilder textParts = new StringBuilder();
        int fileCounter = 0;

        for (String instr : instructions) {
            if (instr == null || instr.trim().isEmpty()) continue;

            if (instructionProcessor.isExtractable(instr)) {
                fileCounter++;
                String content = extractContent(instr);
                String filename = deriveFilename(instr, fileCounter);
                Path target = instrFolder.resolve(filename);
                Files.writeString(target, content);
                logger.info("Written instruction file: {} ({} chars)", target, content.length());
            } else {
                if (textParts.length() > 0) textParts.append("\n\n");
                textParts.append(instr.trim());
            }
        }

        if (textParts.length() > 0) {
            Path textFile = instrFolder.resolve(INSTRUCTIONS_TEXT_FILE);
            Files.writeString(textFile, textParts.toString());
            logger.info("Written combined text instructions: {} ({} chars)", textFile, textParts.length());
        }
    }

    /**
     * For a single-value field: if the value is a URL or file path, fetches its content
     * and writes it to {@code filename} inside {@code inputFolderPath}.
     * Plain-text values are left untouched (not written to a file).
     */
    void writeSingleFieldIfExtractable(Path inputFolderPath,
                                       String filename,
                                       String value) throws IOException {
        if (value == null || value.trim().isEmpty()) return;
        if (!instructionProcessor.isExtractable(value)) {
            logger.debug("Skipping '{}' — plain-text value is not written to a file", filename);
            return;
        }

        String content = extractContent(value);
        Path target = inputFolderPath.resolve(filename);
        Files.writeString(target, content);
        logger.info("Written agent param file: {} ({} chars)", target, content.length());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String extractContent(String value) {
        try {
            String[] result = instructionProcessor.extractIfNeeded(value);
            return (result.length > 0 && result[0] != null) ? result[0] : value;
        } catch (IOException e) {
            logger.warn("Failed to extract content from '{}': {}. Using original value.", value, e.getMessage());
            return value;
        }
    }

    /**
     * Derives a human-readable filename for a URL or file-path instruction entry.
     * <ul>
     *   <li>For URLs: last path segment of the URL, sanitised, with {@code .md} extension.</li>
     *   <li>For file paths: the filename component.</li>
     *   <li>Fallback: {@code instruction_NNN.md} using {@code counter}.</li>
     * </ul>
     */
    static String deriveFilename(String source, int counter) {
        try {
            String segment;
            if (source.startsWith("https://") || source.startsWith("http://")) {
                // Use the last non-empty path segment of the URL
                String path = URI.create(source).getPath();
                String[] parts = path.split("/");
                segment = "";
                for (int i = parts.length - 1; i >= 0; i--) {
                    if (!parts[i].isEmpty()) { segment = parts[i]; break; }
                }
            } else {
                // Local file path — use the filename
                segment = Paths.get(source).getFileName().toString();
            }

            if (!segment.isEmpty()) {
                // Sanitise: replace non-alphanumeric (except -_.) with _
                String safe = segment.replaceAll("[^a-zA-Z0-9._\\-]", "_");
                // Ensure .md extension
                if (!safe.toLowerCase().endsWith(".md")) safe = safe + ".md";
                return safe;
            }
        } catch (Exception e) {
            logger.debug("Could not derive filename from '{}': {}", source, e.getMessage());
        }
        return String.format("instruction_%03d.md", counter);
    }
}
