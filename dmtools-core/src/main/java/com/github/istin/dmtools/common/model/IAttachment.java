// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import java.util.List;

public interface IAttachment {

    String getName();

    String getUrl();

    String getContentType();

    class Utils {

        public static String generateUniqueFileName(String originalFileName, List<? extends IAttachment> attachments) {
            if (!isFileNameExists(originalFileName, attachments)) {
                return originalFileName;
            }

            FileNameParts parts = splitFileName(originalFileName);
            return findUniqueFileName(parts, attachments);
        }

        private static boolean isFileNameExists(String fileName, List<? extends IAttachment> attachments) {
            return attachments.stream()
                    .anyMatch(attachment -> attachment.getName().equalsIgnoreCase(fileName));
        }

        private static FileNameParts splitFileName(String fileName) {
            String baseName = fileName;
            String extension = "";
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = fileName.substring(0, dotIndex);
                extension = fileName.substring(dotIndex);
            }
            return new FileNameParts(baseName, extension);
        }

        private static String findUniqueFileName(FileNameParts parts, List<? extends IAttachment> attachments) {
            int counter = 1;
            String newFileName;
            do {
                newFileName = parts.baseName + "_" + counter + parts.extension;
                counter++;
            } while (isFileNameExists(newFileName, attachments));

            return newFileName;
        }

        private static class FileNameParts {
            final String baseName;
            final String extension;

            FileNameParts(String baseName, String extension) {
                this.baseName = baseName;
                this.extension = extension;
            }
        }
    }
}
