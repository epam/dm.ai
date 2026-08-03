// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.cliagent;

import com.google.gson.*;

import java.lang.reflect.Type;

/**
 * Gson adapter that allows {@link InputParams} to be deserialized from either
 * a plain ticket-key string or a full JSON object.
 */
public class InputParamsTypeAdapter implements JsonDeserializer<InputParams>, JsonSerializer<InputParams> {

    @Override
    public InputParams deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (json.isJsonPrimitive()) {
            JsonPrimitive primitive = json.getAsJsonPrimitive();
            if (primitive.isString()) {
                return new InputParams(primitive.getAsString());
            }
            throw new JsonParseException("InputParams must be a string ticket key or an object");
        }
        if (!json.isJsonObject()) {
            throw new JsonParseException("InputParams must be a string ticket key or an object");
        }

        JsonObject obj = json.getAsJsonObject();
        InputParams params = new InputParams();
        if (obj.has("ticket") && !obj.get("ticket").isJsonNull()) {
            params.setTicket(obj.get("ticket").getAsString());
        }
        if (obj.has("jql") && !obj.get("jql").isJsonNull()) {
            params.setJql(obj.get("jql").getAsString());
        }
        if (obj.has("smart") && !obj.get("smart").isJsonNull()) {
            params.setSmart(obj.get("smart").getAsBoolean());
        }
        if (obj.has("sources") && !obj.get("sources").isJsonNull()) {
            params.setSources(context.deserialize(obj.get("sources"), String[].class));
        }
        if (obj.has("depth") && !obj.get("depth").isJsonNull()) {
            params.setDepth(obj.get("depth").getAsInt());
        }
        if (obj.has("includeComments") && !obj.get("includeComments").isJsonNull()) {
            params.setIncludeComments(obj.get("includeComments").getAsBoolean());
        }
        if (obj.has("includeAttachments") && !obj.get("includeAttachments").isJsonNull()) {
            params.setIncludeAttachments(obj.get("includeAttachments").getAsBoolean());
        }
        if (obj.has("skipVideoAttachments") && !obj.get("skipVideoAttachments").isJsonNull()) {
            params.setSkipVideoAttachments(obj.get("skipVideoAttachments").getAsBoolean());
        }
        if (obj.has("skipAllAttachments") && !obj.get("skipAllAttachments").isJsonNull()) {
            params.setSkipAllAttachments(obj.get("skipAllAttachments").getAsBoolean());
        }
        if (obj.has("ignoreClonedByRelationship") && !obj.get("ignoreClonedByRelationship").isJsonNull()) {
            params.setIgnoreClonedByRelationship(obj.get("ignoreClonedByRelationship").getAsBoolean());
        }
        return params;
    }

    @Override
    public JsonElement serialize(InputParams src, Type typeOfSrc, JsonSerializationContext context) {
        if (src == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject obj = new JsonObject();
        if (src.getTicket() != null) {
            obj.addProperty("ticket", src.getTicket());
        }
        if (src.getJql() != null) {
            obj.addProperty("jql", src.getJql());
        }
        obj.addProperty("smart", src.isSmart());
        if (src.getSources() != null) {
            obj.add("sources", context.serialize(src.getSources()));
        }
        obj.addProperty("depth", src.getDepth());
        obj.addProperty("includeComments", src.isIncludeComments());
        obj.addProperty("includeAttachments", src.isIncludeAttachments());
        obj.addProperty("skipVideoAttachments", src.isSkipVideoAttachments());
        obj.addProperty("skipAllAttachments", src.isSkipAllAttachments());
        obj.addProperty("ignoreClonedByRelationship", src.isIgnoreClonedByRelationship());
        return obj;
    }
}
