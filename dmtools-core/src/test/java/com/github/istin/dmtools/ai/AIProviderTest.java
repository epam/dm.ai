package com.github.istin.dmtools.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class AIProviderTest {

    @BeforeEach
    void setUp() {
        AIProvider.reset();
    }

    @Test
    void testGetCustomAI_InitiallyNull() {
        assertNull(AIProvider.getCustomAI());
    }

    @Test
    void testSetCustomAI() {
        AI mockAI = Mockito.mock(AI.class);
        AIProvider.setCustomAI(mockAI);
        
        assertNotNull(AIProvider.getCustomAI());
        assertEquals(mockAI, AIProvider.getCustomAI());
    }

    @Test
    void testReset() {
        AI mockAI = Mockito.mock(AI.class);
        AIProvider.setCustomAI(mockAI);
        assertNotNull(AIProvider.getCustomAI());
        
        AIProvider.reset();
        assertNull(AIProvider.getCustomAI());
    }

    @Test
    void testSetCustomAI_Null() {
        AI mockAI = Mockito.mock(AI.class);
        AIProvider.setCustomAI(mockAI);
        assertNotNull(AIProvider.getCustomAI());
        
        AIProvider.setCustomAI(null);
        assertNull(AIProvider.getCustomAI());
    }

    @Test
    void testSetCustomAI_Replace() {
        AI mockAI1 = Mockito.mock(AI.class);
        AI mockAI2 = Mockito.mock(AI.class);
        
        AIProvider.setCustomAI(mockAI1);
        assertEquals(mockAI1, AIProvider.getCustomAI());
        
        AIProvider.setCustomAI(mockAI2);
        assertEquals(mockAI2, AIProvider.getCustomAI());
        assertNotEquals(mockAI1, AIProvider.getCustomAI());
    }
}
