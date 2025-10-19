package com.github.istin.dmtools.common.kb.utils;

import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Utility responsible for rolling back files created during KB processing.
 */
public class KBRollbackManager {

    /**
     * Deletes the provided files if they exist, logging successes and failures.
     *
     * @param createdFiles files created during processing that should be removed on failure
     * @param logger logger for diagnostic messages
     */
    public void rollbackCreatedFiles(List<Path> createdFiles, Logger logger) {
        int deletedCount = 0;
        int failedCount = 0;

        for (Path file : createdFiles) {
            try {
                if (Files.exists(file)) {
                    Files.delete(file);
                    deletedCount++;
                    if (logger != null) {
                        logger.debug("Rolled back: {}", file.getFileName());
                    }
                }
            } catch (IOException e) {
                failedCount++;
                if (logger != null) {
                    logger.warn("Failed to rollback file: {}", file, e);
                }
            }
        }

        if (logger != null) {
            logger.info("Rollback complete: deleted {}, failed {} (out of {} total)",
                    deletedCount, failedCount, createdFiles.size());
        }
    }
}
