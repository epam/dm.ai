package com.github.istin.dmtools.common.kb;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for formatSourceTag method logic
 */
public class KBStructureBuilderSourceTagTest {

    @Test
    public void testFormatSourceTag() throws Exception {
        KBStructureBuilder builder = new KBStructureBuilder();
        
        // Use reflection to access private method
        Method method = KBStructureBuilder.class.getDeclaredMethod("formatSourceTag", String.class);
        method.setAccessible(true);
        
        // Test source already has prefix
        String result1 = (String) method.invoke(builder, "source_simple_test");
        assertEquals("#source_simple_test", result1, "Should add # only, not duplicate prefix");
        
        // Test source without prefix
        String result2 = (String) method.invoke(builder, "simple_test");
        assertEquals("#source_simple_test", result2, "Should add both # and source_ prefix");
        
        // Test source with source_ prefix (incremental test case)
        String result3 = (String) method.invoke(builder, "source_incremental_test");
        assertEquals("#source_incremental_test", result3, "Should add # only");
        
        System.out.println("✅ All formatSourceTag tests passed!");
        System.out.println("  - source_simple_test → " + result1);
        System.out.println("  - simple_test → " + result2);
        System.out.println("  - source_incremental_test → " + result3);
    }
}

