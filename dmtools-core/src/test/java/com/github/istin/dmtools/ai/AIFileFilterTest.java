// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AIFileFilterTest {

    @TempDir
    Path tempDir;

    private File createFile(String name, int sizeBytes) throws IOException {
        File f = tempDir.resolve(name).toFile();
        try (FileWriter fw = new FileWriter(f)) {
            for (int i = 0; i < sizeBytes; i++) fw.write('x');
        }
        return f;
    }

    // -------------------------------------------------------------------------
    // shouldInclude — basic cases
    // -------------------------------------------------------------------------

    @Test
    void shouldInclude_nullFile_returnsFalse() {
        AIFileFilter filter = new AIFileFilter(0, Collections.emptySet());
        assertFalse(filter.shouldInclude(null));
    }

    @Test
    void shouldInclude_missingFile_returnsFalse() {
        AIFileFilter filter = new AIFileFilter(0, Collections.emptySet());
        assertFalse(filter.shouldInclude(new File("/non/existent/file.jpg")));
    }

    @Test
    void shouldInclude_noLimitsSet_alwaysTrue() throws IOException {
        File f = createFile("image.jpg", 100);
        AIFileFilter filter = new AIFileFilter(0, Collections.emptySet());
        assertTrue(filter.shouldInclude(f));
    }

    // -------------------------------------------------------------------------
    // Size limit
    // -------------------------------------------------------------------------

    @Test
    void shouldInclude_fileBelowSizeLimit_returnsTrue() throws IOException {
        File f = createFile("small.png", 512);
        AIFileFilter filter = new AIFileFilter(1024, Collections.emptySet()); // 1 KB limit
        assertTrue(filter.shouldInclude(f));
    }

    @Test
    void shouldInclude_fileExceedsSizeLimit_returnsFalse() throws IOException {
        File f = createFile("big.png", 2048);
        AIFileFilter filter = new AIFileFilter(1024, Collections.emptySet()); // 1 KB limit
        assertFalse(filter.shouldInclude(f));
    }

    @Test
    void shouldInclude_fileExactlyAtSizeLimit_returnsTrue() throws IOException {
        File f = createFile("exact.png", 1024);
        AIFileFilter filter = new AIFileFilter(1024, Collections.emptySet()); // 1 KB limit
        assertTrue(filter.shouldInclude(f));
    }

    @Test
    void shouldInclude_zeroSizeLimit_disablesCheck() throws IOException {
        File f = createFile("verylarge.bin", 10_000);
        AIFileFilter filter = new AIFileFilter(0, Collections.emptySet()); // 0 = disabled
        assertTrue(filter.shouldInclude(f));
    }

    // -------------------------------------------------------------------------
    // Extension whitelist
    // -------------------------------------------------------------------------

    @Test
    void shouldInclude_extensionInWhitelist_returnsTrue() throws IOException {
        File f = createFile("photo.jpg", 100);
        Set<String> allowed = new HashSet<>(Arrays.asList("jpg", "png", "pdf"));
        AIFileFilter filter = new AIFileFilter(0, allowed);
        assertTrue(filter.shouldInclude(f));
    }

    @Test
    void shouldInclude_extensionNotInWhitelist_returnsFalse() throws IOException {
        File f = createFile("clip.mp4", 100);
        Set<String> allowed = new HashSet<>(Arrays.asList("jpg", "png", "pdf"));
        AIFileFilter filter = new AIFileFilter(0, allowed);
        assertFalse(filter.shouldInclude(f));
    }

    @Test
    void shouldInclude_extensionCaseInsensitive_returnsTrue() throws IOException {
        File f = createFile("photo.JPG", 100);
        Set<String> allowed = new HashSet<>(Collections.singletonList("jpg"));
        AIFileFilter filter = new AIFileFilter(0, allowed);
        assertTrue(filter.shouldInclude(f));
    }

    @Test
    void shouldInclude_emptyWhitelist_allowsAll() throws IOException {
        File f = createFile("anything.xyz", 100);
        AIFileFilter filter = new AIFileFilter(0, Collections.emptySet());
        assertTrue(filter.shouldInclude(f));
    }

    @Test
    void shouldInclude_fileNoExtension_notInWhitelist_returnsFalse() throws IOException {
        File f = createFile("noext", 100);
        Set<String> allowed = new HashSet<>(Collections.singletonList("jpg"));
        AIFileFilter filter = new AIFileFilter(0, allowed);
        assertFalse(filter.shouldInclude(f));
    }

    // -------------------------------------------------------------------------
    // Combined filters
    // -------------------------------------------------------------------------

    @Test
    void shouldInclude_failsBothFilters_returnsFalse() throws IOException {
        File f = createFile("video.mp4", 5000);
        Set<String> allowed = new HashSet<>(Collections.singletonList("jpg"));
        AIFileFilter filter = new AIFileFilter(1024, allowed);
        assertFalse(filter.shouldInclude(f));
    }

    @Test
    void shouldInclude_passesExtensionButFailsSize_returnsFalse() throws IOException {
        File f = createFile("large.jpg", 5000);
        Set<String> allowed = new HashSet<>(Collections.singletonList("jpg"));
        AIFileFilter filter = new AIFileFilter(1024, allowed);
        assertFalse(filter.shouldInclude(f));
    }

    @Test
    void shouldInclude_passesSizeButFailsExtension_returnsFalse() throws IOException {
        File f = createFile("small.mp4", 100);
        Set<String> allowed = new HashSet<>(Collections.singletonList("jpg"));
        AIFileFilter filter = new AIFileFilter(1024, allowed);
        assertFalse(filter.shouldInclude(f));
    }

    @Test
    void shouldInclude_passesBothFilters_returnsTrue() throws IOException {
        File f = createFile("small.jpg", 100);
        Set<String> allowed = new HashSet<>(Collections.singletonList("jpg"));
        AIFileFilter filter = new AIFileFilter(1024, allowed);
        assertTrue(filter.shouldInclude(f));
    }

    // -------------------------------------------------------------------------
    // filter(List<File>)
    // -------------------------------------------------------------------------

    @Test
    void filter_nullList_returnsEmptyList() {
        AIFileFilter filter = new AIFileFilter(0, Collections.emptySet());
        List<File> result = filter.filter(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void filter_emptyList_returnsEmptyList() {
        AIFileFilter filter = new AIFileFilter(0, Collections.emptySet());
        assertTrue(filter.filter(Collections.emptyList()).isEmpty());
    }

    @Test
    void filter_removesFilesFailingExtension() throws IOException {
        File jpg = createFile("photo.jpg", 100);
        File mp4 = createFile("video.mp4", 100);
        File png = createFile("image.png", 100);
        Set<String> allowed = new HashSet<>(Arrays.asList("jpg", "png"));
        AIFileFilter filter = new AIFileFilter(0, allowed);

        List<File> result = filter.filter(Arrays.asList(jpg, mp4, png));
        assertEquals(2, result.size());
        assertTrue(result.contains(jpg));
        assertTrue(result.contains(png));
        assertFalse(result.contains(mp4));
    }

    @Test
    void filter_removesFilesExceedingSize() throws IOException {
        File small = createFile("s.jpg", 100);
        File large = createFile("l.jpg", 5000);
        AIFileFilter filter = new AIFileFilter(1024, Collections.emptySet());

        List<File> result = filter.filter(Arrays.asList(small, large));
        assertEquals(1, result.size());
        assertTrue(result.contains(small));
    }

    @Test
    void filter_allFilesPass_returnsSameContent() throws IOException {
        File a = createFile("a.jpg", 100);
        File b = createFile("b.png", 200);
        AIFileFilter filter = new AIFileFilter(0, Collections.emptySet());

        List<File> result = filter.filter(Arrays.asList(a, b));
        assertEquals(2, result.size());
    }

    @Test
    void filter_noLimits_allFilesPass() throws IOException {
        File f1 = createFile("f1.mp4", 999_999);
        File f2 = createFile("f2.avi", 1);
        AIFileFilter filter = new AIFileFilter(0, Collections.emptySet());

        List<File> result = filter.filter(Arrays.asList(f1, f2));
        assertEquals(2, result.size());
    }
}
