package com.github.istin.dmtools.context;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UriToObjectTest {

    private static class TestUriToObject implements UriToObject {
        private final Set<String> uris;
        private final Object result;

        TestUriToObject(Set<String> uris, Object result) {
            this.uris = uris;
            this.result = result;
        }

        @Override
        public Set<String> parseUris(String object) throws Exception {
            return uris;
        }

        @Override
        public Object uriToObject(String uri) throws Exception {
            return result;
        }
    }

    @Test
    void testParseUris_ReturnsUris() throws Exception {
        Set<String> expectedUris = Set.of("https://example.com/1", "https://example.com/2");
        TestUriToObject processor = new TestUriToObject(expectedUris, "test");
        
        Set<String> result = processor.parseUris("some text with uris");
        
        assertNotNull(result);
        assertEquals(expectedUris, result);
    }

    @Test
    void testParseUris_EmptySet() throws Exception {
        Set<String> emptyUris = new HashSet<>();
        TestUriToObject processor = new TestUriToObject(emptyUris, "test");
        
        Set<String> result = processor.parseUris("no uris here");
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testUriToObject() throws Exception {
        String expectedResult = "parsed_result";
        TestUriToObject processor = new TestUriToObject(Set.of(), expectedResult);
        
        Object result = processor.uriToObject("https://example.com/page");
        
        assertEquals(expectedResult, result);
    }

    @Test
    void testUriToObject_ReturnsNull() throws Exception {
        TestUriToObject processor = new TestUriToObject(Set.of(), null);
        
        Object result = processor.uriToObject("https://example.com/page");
        
        assertNull(result);
    }

    @Test
    void testParseUris_ThrowsException() {
        UriToObject processor = new UriToObject() {
            @Override
            public Set<String> parseUris(String object) throws Exception {
                throw new Exception("Parse error");
            }

            @Override
            public Object uriToObject(String uri) throws Exception {
                return null;
            }
        };

        assertThrows(Exception.class, () -> {
            processor.parseUris("test");
        });
    }

    @Test
    void testUriToObject_ThrowsException() {
        UriToObject processor = new UriToObject() {
            @Override
            public Set<String> parseUris(String object) throws Exception {
                return Set.of();
            }

            @Override
            public Object uriToObject(String uri) throws Exception {
                throw new Exception("URI processing error");
            }
        };

        assertThrows(Exception.class, () -> {
            processor.uriToObject("https://example.com");
        });
    }

    @Test
    void testParseUris_MultipleUris() throws Exception {
        Set<String> multipleUris = Set.of(
            "https://example.com/1",
            "https://example.com/2",
            "https://example.com/3"
        );
        TestUriToObject processor = new TestUriToObject(multipleUris, "test");
        
        Set<String> result = processor.parseUris("text");
        
        assertEquals(3, result.size());
        assertTrue(result.containsAll(multipleUris));
    }
}