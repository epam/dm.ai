// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.ai.TicketContext;

public class TicketCreationPrompt extends TicketBasedPrompt {

    private String priorities;

    public TicketCreationPrompt(String basePath, TicketContext ticketContext, String priorities) {
        super(basePath, ticketContext);
        this.priorities = priorities;
    }

    public String getPriorities() {
        return priorities;
    }
}
