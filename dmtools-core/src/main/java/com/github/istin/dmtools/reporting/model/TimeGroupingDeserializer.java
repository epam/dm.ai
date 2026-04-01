// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.reporting.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom Jackson deserializer that handles both single-object and array forms of timeGrouping.
 * <p>
 * Single object: {"type": "bi-weekly"} -> List with 1 element
 * Array: [{"type": "bi-weekly"}, {"type": "monthly"}] -> List with N elements
 */
public class TimeGroupingDeserializer extends JsonDeserializer<List<TimeGroupingConfig>> {

    @Override
    public List<TimeGroupingConfig> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        List<TimeGroupingConfig> result = new ArrayList<>();
        ObjectMapper mapper = (ObjectMapper) p.getCodec();

        if (p.currentToken() == JsonToken.START_ARRAY) {
            // Array form: [{"type": "bi-weekly"}, {"type": "monthly"}]
            while (p.nextToken() != JsonToken.END_ARRAY) {
                TimeGroupingConfig config = mapper.readValue(p, TimeGroupingConfig.class);
                result.add(config);
            }
        } else if (p.currentToken() == JsonToken.START_OBJECT) {
            // Single object form: {"type": "bi-weekly"}
            TimeGroupingConfig config = mapper.readValue(p, TimeGroupingConfig.class);
            result.add(config);
        }

        return result;
    }
}
