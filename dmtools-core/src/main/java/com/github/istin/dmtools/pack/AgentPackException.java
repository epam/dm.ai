// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.pack;

/**
 * Thrown when agent-pack resolution fails (download, verification, zip-slip, ...).
 *
 * <p>Java backport of the Dart {@code AgentPackException}
 * (dmtools-dart {@code lib/src/pack/agent_pack_resolver.dart}); message wording
 * mirrors the Dart side so CI logs compare 1:1.
 */
public class AgentPackException extends RuntimeException {

    public AgentPackException(String message) {
        super(message);
    }

    public AgentPackException(String message, Throwable cause) {
        super(message, cause);
    }
}
