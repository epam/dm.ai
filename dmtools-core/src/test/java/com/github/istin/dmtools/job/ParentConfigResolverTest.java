// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.job;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ParentConfigResolverTest {

    private ParentConfigResolver resolver;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        resolver = new ParentConfigResolver();
    }

    // -------------------------------------------------------------------------
    // No parent block → passthrough
    // -------------------------------------------------------------------------

    @Test
    void testResolve_NoParentBlock_ReturnsUnchanged() {
        JSONObject config = new JSONObject("{\"name\":\"Teammate\",\"params\":{\"inputJql\":\"key=A-1\"}}");
        JSONObject result = resolver.resolve(config, null);
        assertEquals("Teammate", result.getString("name"));
        assertEquals("key=A-1", result.getJSONObject("params").getString("inputJql"));
        assertFalse(result.has(ParentConfigResolver.PARENT));
    }

    // -------------------------------------------------------------------------
    // Simple inheritance
    // -------------------------------------------------------------------------

    @Test
    void testResolve_SimpleInheritance_ChildInheritsParentFields() throws Exception {
        Path parentFile = tempDir.resolve("base.json");
        Files.writeString(parentFile, """
                {
                  "name": "Teammate",
                  "params": {
                    "inputJql": "project=BASE",
                    "outputType": "comment"
                  }
                }
                """);

        JSONObject child = new JSONObject("""
                {
                  "name": "Teammate",
                  "parent": { "path": "base.json" }
                }
                """);

        JSONObject result = resolver.resolve(child, tempDir.resolve("child.json"));

        assertFalse(result.has(ParentConfigResolver.PARENT));
        assertEquals("project=BASE", result.getJSONObject("params").getString("inputJql"));
        assertEquals("comment", result.getJSONObject("params").getString("outputType"));
    }

    // -------------------------------------------------------------------------
    // Child overrides parent (default deep-merge)
    // -------------------------------------------------------------------------

    @Test
    void testResolve_ChildOverridesParentScalar() throws Exception {
        Path parentFile = tempDir.resolve("base.json");
        Files.writeString(parentFile, """
                {"params":{"inputJql":"project=BASE","outputType":"comment"}}
                """);

        JSONObject child = new JSONObject("""
                {
                  "parent":{"path":"base.json"},
                  "params":{"inputJql":"key=CHILD-1"}
                }
                """);

        JSONObject result = resolver.resolve(child, tempDir.resolve("child.json"));
        // child's inputJql wins; parent's outputType is inherited
        assertEquals("key=CHILD-1", result.getJSONObject("params").getString("inputJql"));
        assertEquals("comment", result.getJSONObject("params").getString("outputType"));
    }

    @Test
    void testResolve_ChildAddsNewField() throws Exception {
        Path parentFile = tempDir.resolve("base.json");
        Files.writeString(parentFile, """
                {"params":{"outputType":"comment"}}
                """);

        JSONObject child = new JSONObject("""
                {
                  "parent":{"path":"base.json"},
                  "params":{"newField":"hello"}
                }
                """);

        JSONObject result = resolver.resolve(child, tempDir.resolve("child.json"));
        assertEquals("comment",  result.getJSONObject("params").getString("outputType"));
        assertEquals("hello",    result.getJSONObject("params").getString("newField"));
    }

    // -------------------------------------------------------------------------
    // override paths
    // -------------------------------------------------------------------------

    @Test
    void testResolve_OverrideReplacesObjectWithoutRecursiveMerge() throws Exception {
        Path parentFile = tempDir.resolve("base.json");
        Files.writeString(parentFile, """
                {"params":{"agentParams":{"aiRole":"Engineer","extra":"parentExtra"}}}
                """);

        JSONObject child = new JSONObject("""
                {
                  "parent":{
                    "path":"base.json",
                    "override":["params.agentParams"]
                  },
                  "params":{"agentParams":{"aiRole":"QA"}}
                }
                """);

        JSONObject result = resolver.resolve(child, tempDir.resolve("child.json"));
        JSONObject agentParams = result.getJSONObject("params").getJSONObject("agentParams");

        // aiRole comes from child
        assertEquals("QA", agentParams.getString("aiRole"));
        // extra from parent is gone (override replaced the whole object, no deep merge)
        assertFalse(agentParams.has("extra"), "override should prevent deep-merge of parent.extra");
    }

    @Test
    void testResolve_OverridePath_ChildValueAbsent_NoEffect() throws Exception {
        Path parentFile = tempDir.resolve("base.json");
        Files.writeString(parentFile, """
                {"params":{"agentParams":{"aiRole":"Engineer"}}}
                """);

        JSONObject child = new JSONObject("""
                {
                  "parent":{
                    "path":"base.json",
                    "override":["params.agentParams"]
                  },
                  "params":{}
                }
                """);

        JSONObject result = resolver.resolve(child, tempDir.resolve("child.json"));
        // Child doesn't have agentParams, so override has no effect; parent value is kept
        assertTrue(result.getJSONObject("params").has("agentParams"));
        assertEquals("Engineer",
                result.getJSONObject("params").getJSONObject("agentParams").getString("aiRole"));
    }

    // -------------------------------------------------------------------------
    // merge paths
    // -------------------------------------------------------------------------

    @Test
    void testResolve_MergePrependsParentArrayBeforeChildArray() throws Exception {
        Path parentFile = tempDir.resolve("base.json");
        Files.writeString(parentFile, """
                {"params":{"agentParams":{"instructions":["be thorough","check security"]}}}
                """);

        JSONObject child = new JSONObject("""
                {
                  "parent":{
                    "path":"base.json",
                    "merge":["params.agentParams.instructions"]
                  },
                  "params":{"agentParams":{"instructions":["also check perf"]}}
                }
                """);

        JSONObject result = resolver.resolve(child, tempDir.resolve("child.json"));
        JSONArray instructions = result
                .getJSONObject("params")
                .getJSONObject("agentParams")
                .getJSONArray("instructions");

        assertEquals(3, instructions.length());
        assertEquals("be thorough",     instructions.getString(0)); // parent first
        assertEquals("check security",  instructions.getString(1));
        assertEquals("also check perf", instructions.getString(2)); // child last
    }

    @Test
    void testResolve_Merge_ParentArrayAbsent_UsesChildArrayOnly() throws Exception {
        Path parentFile = tempDir.resolve("base.json");
        Files.writeString(parentFile, """
                {"params":{"agentParams":{}}}
                """);

        JSONObject child = new JSONObject("""
                {
                  "parent":{
                    "path":"base.json",
                    "merge":["params.agentParams.instructions"]
                  },
                  "params":{"agentParams":{"instructions":["only child"]}}
                }
                """);

        JSONObject result = resolver.resolve(child, tempDir.resolve("child.json"));
        JSONArray instructions = result
                .getJSONObject("params")
                .getJSONObject("agentParams")
                .getJSONArray("instructions");

        assertEquals(1, instructions.length());
        assertEquals("only child", instructions.getString(0));
    }

    @Test
    void testResolve_Merge_ChildArrayAbsent_UsesParentArrayOnly() throws Exception {
        Path parentFile = tempDir.resolve("base.json");
        Files.writeString(parentFile, """
                {"params":{"agentParams":{"instructions":["parent item"]}}}
                """);

        JSONObject child = new JSONObject("""
                {
                  "parent":{
                    "path":"base.json",
                    "merge":["params.agentParams.instructions"]
                  },
                  "params":{"agentParams":{}}
                }
                """);

        JSONObject result = resolver.resolve(child, tempDir.resolve("child.json"));
        JSONArray instructions = result
                .getJSONObject("params")
                .getJSONObject("agentParams")
                .getJSONArray("instructions");

        assertEquals(1, instructions.length());
        assertEquals("parent item", instructions.getString(0));
    }

    // -------------------------------------------------------------------------
    // Recursive parent (grandparent → parent → child)
    // -------------------------------------------------------------------------

    @Test
    void testResolve_RecursiveParent_GrandparentFieldsInherited() throws Exception {
        Path grandparentFile = tempDir.resolve("grandparent.json");
        Files.writeString(grandparentFile, """
                {"params":{"grandparentField":"gpValue","shared":"fromGP"}}
                """);

        Path parentFile = tempDir.resolve("parent.json");
        Files.writeString(parentFile, """
                {
                  "parent":{"path":"grandparent.json"},
                  "params":{"parentField":"pValue","shared":"fromParent"}
                }
                """);

        JSONObject child = new JSONObject("""
                {
                  "parent":{"path":"parent.json"},
                  "params":{"childField":"cValue"}
                }
                """);

        JSONObject result = resolver.resolve(child, tempDir.resolve("child.json"));
        JSONObject params = result.getJSONObject("params");

        assertEquals("gpValue",     params.getString("grandparentField")); // from grandparent
        assertEquals("fromParent",  params.getString("shared"));           // parent overrides grandparent
        assertEquals("pValue",      params.getString("parentField"));       // from parent
        assertEquals("cValue",      params.getString("childField"));        // from child
        assertFalse(result.has(ParentConfigResolver.PARENT));
    }

    // -------------------------------------------------------------------------
    // Missing parent file → clear error
    // -------------------------------------------------------------------------

    @Test
    void testResolve_MissingParentFile_ThrowsIllegalArgumentException() {
        JSONObject child = new JSONObject("""
                {"parent":{"path":"nonexistent.json"}}
                """);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(child, tempDir.resolve("child.json"))
        );
        assertTrue(ex.getMessage().contains("does not exist") || ex.getMessage().contains("nonexistent"),
                "Exception should mention the missing file");
    }

    // -------------------------------------------------------------------------
    // parent block present but path empty → ignored gracefully
    // -------------------------------------------------------------------------

    @Test
    void testResolve_ParentPathEmpty_IgnoredGracefully() {
        JSONObject child = new JSONObject("""
                {"name":"Teammate","parent":{"path":""},"params":{"x":"1"}}
                """);
        JSONObject result = resolver.resolve(child, null);
        assertFalse(result.has(ParentConfigResolver.PARENT));
        assertEquals("1", result.getJSONObject("params").getString("x"));
    }

    // -------------------------------------------------------------------------
    // getValueAtPath / setValueAtPath helpers
    // -------------------------------------------------------------------------

    @Test
    void testGetValueAtPath_NestedPath() {
        JSONObject obj = new JSONObject("{\"a\":{\"b\":{\"c\":42}}}");
        Object val = resolver.getValueAtPath(obj, "a.b.c");
        assertEquals(42, val);
    }

    @Test
    void testGetValueAtPath_MissingSegment_ReturnsNull() {
        JSONObject obj = new JSONObject("{\"a\":{}}");
        assertNull(resolver.getValueAtPath(obj, "a.b.c"));
    }

    @Test
    void testSetValueAtPath_CreatesIntermediateObjects() {
        JSONObject obj = new JSONObject();
        resolver.setValueAtPath(obj, "a.b.c", "hello");
        assertEquals("hello", obj.getJSONObject("a").getJSONObject("b").getString("c"));
    }

    @Test
    void testSetValueAtPath_UpdatesExistingLeaf() {
        JSONObject obj = new JSONObject("{\"a\":{\"b\":\"old\"}}");
        resolver.setValueAtPath(obj, "a.b", "new");
        assertEquals("new", obj.getJSONObject("a").getString("b"));
    }
}
