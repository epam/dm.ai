// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.pack;

import java.nio.file.Path;

/**
 * The outcome of resolving a pack: the unpacked cache root and the entry config.
 *
 * <p>Java backport of the Dart {@code ResolvedPack}
 * (dmtools-dart {@code lib/src/pack/agent_pack_resolver.dart}).
 */
public class ResolvedPack {

    /** The unpacked pack cache directory ({@code ~/.dmtools/packs/<agent>-<version>}). */
    private final Path packRoot;

    /** The entry config file inside the cache. */
    private final Path entryFile;

    /** The agent name from the manifest. */
    private final String agent;

    /** The pack version from the manifest. */
    private final String version;

    public ResolvedPack(Path packRoot, Path entryFile, String agent, String version) {
        this.packRoot = packRoot;
        this.entryFile = entryFile;
        this.agent = agent;
        this.version = version;
    }

    public Path getPackRoot() {
        return packRoot;
    }

    public Path getEntryFile() {
        return entryFile;
    }

    public String getAgent() {
        return agent;
    }

    public String getVersion() {
        return version;
    }
}
