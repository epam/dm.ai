// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.teammate;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.annotations.JsonAdapter;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Structured representation of {@code cliPrompts} that preserves the original array order
 * while allowing named sections to be merged independently.
 * <p>
 * Each element of the JSON array can be either:
 * <ul>
 *   <li>a plain string — an unnamed prompt, kept in place;</li>
 *   <li>an object {@code {"id": "section-id", "prompts": ["...", "..."]}} — a named section
 *       that can be merged by id when configs are inherited.</li>
 * </ul>
 * <p>
 * Backward compatible: a JSON array of plain strings is deserialized into the same structure
 * where every string becomes an unnamed item, and serialization of unnamed items back to JSON
 * yields plain strings again.
 */
@Getter
@ToString
@EqualsAndHashCode
@JsonAdapter(CliPromptsConfig.Adapter.class)
public class CliPromptsConfig {

    private final List<Item> items;

    public CliPromptsConfig() {
        this.items = Collections.emptyList();
    }

    public CliPromptsConfig(List<Item> items) {
        this.items = items != null ? Collections.unmodifiableList(new ArrayList<>(items)) : Collections.emptyList();
    }

    /**
     * Convenience factory from a plain string array (backward compatibility).
     */
    public static CliPromptsConfig fromStrings(String[] prompts) {
        if (prompts == null || prompts.length == 0) {
            return new CliPromptsConfig();
        }
        List<Item> result = new ArrayList<>();
        for (String prompt : prompts) {
            result.add(Item.unnamed(prompt));
        }
        return new CliPromptsConfig(result);
    }

    /**
     * Flattens the config back into a plain string array, preserving the original order.
     * Unnamed items contribute their prompt directly; named sections contribute their prompts
     * in the section's position.
     */
    public String[] toStringArray() {
        List<String> result = new ArrayList<>();
        for (Item item : items) {
            if (item.isUnnamed()) {
                result.add(item.getPrompt());
            } else {
                result.addAll(item.getSection().getPrompts());
            }
        }
        return result.toArray(new String[0]);
    }

    /**
     * Merges this config (base/parent) with another config (override/child).
     * <p>
     * Merge rules:
     * <ul>
     *   <li>Items are processed left-to-right.</li>
     *   <li>Unnamed prompts are copied as-is; they never merge with named sections.</li>
     *   <li>Named sections with the same id are merged using the override's merge strategy
     *       (default: append).</li>
     *   <li>New items from the override are appended to the end.</li>
     * </ul>
     */
    public CliPromptsConfig merge(CliPromptsConfig override) {
        if (override == null || override.items.isEmpty()) {
            return this;
        }
        if (items.isEmpty()) {
            return override;
        }

        List<Item> merged = new ArrayList<>();
        for (Item baseItem : items) {
            if (baseItem.isUnnamed()) {
                merged.add(baseItem);
                continue;
            }
            String id = baseItem.getSection().getId();
            Item overrideMatch = override.findSectionById(id);
            if (overrideMatch == null) {
                merged.add(baseItem);
            } else {
                merged.add(baseItem.merge(overrideMatch));
            }
        }
        for (Item overrideItem : override.items) {
            if (overrideItem.isUnnamed()) {
                merged.add(overrideItem);
                continue;
            }
            String id = overrideItem.getSection().getId();
            if (findSectionById(id) == null) {
                merged.add(overrideItem);
            }
        }
        return new CliPromptsConfig(merged);
    }

    private Item findSectionById(String id) {
        if (id == null) {
            return null;
        }
        for (Item item : items) {
            if (!item.isUnnamed() && id.equals(item.getSection().getId())) {
                return item;
            }
        }
        return null;
    }

    /**
     * A single element of the {@code cliPrompts} array.
     */
    @Getter
    @ToString
    @EqualsAndHashCode
    public static class Item {
        private final String prompt;   // non-null for unnamed items
        private final Section section; // non-null for named items

        private Item(String prompt, Section section) {
            this.prompt = prompt;
            this.section = section;
        }

        public static Item unnamed(String prompt) {
            return new Item(Objects.requireNonNull(prompt, "prompt"), null);
        }

        public static Item named(Section section) {
            return new Item(null, Objects.requireNonNull(section, "section"));
        }

        public boolean isUnnamed() {
            return section == null;
        }

        /**
         * Merges this section item with an override section item.
         * Only valid for named items; unnamed items are never merged.
         */
        public Item merge(Item override) {
            if (isUnnamed() || override == null || override.isUnnamed()) {
                return this;
            }
            Section base = this.section;
            Section over = override.section;
            if (!Objects.equals(base.getId(), over.getId())) {
                return this;
            }
            MergeStrategy strategy = MergeStrategy.fromString(over.getMergeStrategy());
            List<String> mergedPrompts = strategy.merge(base.getPrompts(), over.getPrompts());
            return Item.named(new Section(base.getId(), mergedPrompts, base.getMergeStrategy()));
        }
    }

    /**
     * A named section of prompts.
     */
    @Getter
    @ToString
    @EqualsAndHashCode
    public static class Section {
        private final String id;
        private final List<String> prompts;
        private final String mergeStrategy;

        public Section(String id, List<String> prompts, String mergeStrategy) {
            this.id = Objects.requireNonNull(id, "section id");
            this.prompts = prompts != null ? Collections.unmodifiableList(new ArrayList<>(prompts)) : Collections.emptyList();
            this.mergeStrategy = mergeStrategy != null ? mergeStrategy : MergeStrategy.APPEND.getValue();
        }
    }

    /**
     * Merge strategy for sections with the same id.
     */
    public enum MergeStrategy {
        APPEND("append"),
        PREPEND("prepend"),
        REPLACE("replace");

        @Getter
        private final String value;

        MergeStrategy(String value) {
            this.value = value;
        }

        public static MergeStrategy fromString(String value) {
            if (value == null) {
                return APPEND;
            }
            for (MergeStrategy s : values()) {
                if (s.value.equalsIgnoreCase(value)) {
                    return s;
                }
            }
            return APPEND;
        }

        public List<String> merge(List<String> base, List<String> override) {
            List<String> result = new ArrayList<>();
            switch (this) {
                case PREPEND:
                    if (override != null) result.addAll(override);
                    if (base != null) result.addAll(base);
                    break;
                case REPLACE:
                    if (override != null) result.addAll(override);
                    break;
                case APPEND:
                default:
                    if (base != null) result.addAll(base);
                    if (override != null) result.addAll(override);
                    break;
            }
            return result;
        }
    }

    /**
     * Gson TypeAdapter for {@link CliPromptsConfig}.
     */
    public static class Adapter implements JsonSerializer<CliPromptsConfig>, JsonDeserializer<CliPromptsConfig> {

        @Override
        public JsonElement serialize(CliPromptsConfig src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray array = new JsonArray();
            for (Item item : src.items) {
                if (item.isUnnamed()) {
                    array.add(new JsonPrimitive(item.getPrompt()));
                } else {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", item.getSection().getId());
                    JsonArray promptsArray = new JsonArray();
                    for (String p : item.getSection().getPrompts()) {
                        promptsArray.add(p);
                    }
                    obj.add("prompts", promptsArray);
                    if (item.getSection().getMergeStrategy() != null
                            && !MergeStrategy.APPEND.getValue().equals(item.getSection().getMergeStrategy())) {
                        obj.addProperty("mergeStrategy", item.getSection().getMergeStrategy());
                    }
                    array.add(obj);
                }
            }
            return array;
        }

        @Override
        public CliPromptsConfig deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return new CliPromptsConfig();
            }
            if (!json.isJsonArray()) {
                throw new JsonParseException("cliPrompts must be a JSON array of strings and/or section objects");
            }
            JsonArray array = json.getAsJsonArray();
            List<Item> items = new ArrayList<>();
            for (JsonElement element : array) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    items.add(Item.unnamed(element.getAsString()));
                } else if (element.isJsonObject()) {
                    JsonObject obj = element.getAsJsonObject();
                    String id = obj.has("id") ? obj.get("id").getAsString() : null;
                    if (id == null || id.isBlank()) {
                        throw new JsonParseException("cliPrompts section object must have a non-empty 'id' field");
                    }
                    List<String> prompts = new ArrayList<>();
                    if (obj.has("prompts") && obj.get("prompts").isJsonArray()) {
                        for (JsonElement p : obj.get("prompts").getAsJsonArray()) {
                            prompts.add(p.getAsString());
                        }
                    }
                    String mergeStrategy = obj.has("mergeStrategy") ? obj.get("mergeStrategy").getAsString() : null;
                    items.add(Item.named(new Section(id, prompts, mergeStrategy)));
                } else {
                    throw new JsonParseException(
                            "cliPrompts array elements must be strings or objects with 'id' and 'prompts'");
                }
            }
            return new CliPromptsConfig(items);
        }
    }
}
