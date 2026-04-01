// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.report.productivity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * Represents an interaction event with date and ticket key information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InteractionInfo implements Serializable {
    private Date date;
    private String ticketKey;
}

