package com.github.istin.dmtools.diagram;

import com.github.istin.dmtools.ai.Diagram;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IDiagramDrawerTest {

    @Test
    void testIDiagramDrawerInterface() {
        IDiagramDrawer drawer = new IDiagramDrawer() {
            @Override
            public void drawDiagram(Diagram diagram, String outputPath) throws Exception {
                assertNotNull(diagram);
                assertNotNull(outputPath);
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String getDrawerType() {
                return "TestDrawer";
            }
        };

        assertNotNull(drawer);
        assertTrue(drawer.isAvailable());
        assertEquals("TestDrawer", drawer.getDrawerType());
    }

    @Test
    void testDrawDiagram() throws Exception {
        final boolean[] called = {false};
        
        IDiagramDrawer drawer = new IDiagramDrawer() {
            @Override
            public void drawDiagram(Diagram diagram, String outputPath) throws Exception {
                called[0] = true;
                assertEquals("output.png", outputPath);
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String getDrawerType() {
                return "TestDrawer";
            }
        };

        Diagram mockDiagram = new Diagram();
        drawer.drawDiagram(mockDiagram, "output.png");
        assertTrue(called[0]);
    }

    @Test
    void testIsAvailable_False() {
        IDiagramDrawer drawer = new IDiagramDrawer() {
            @Override
            public void drawDiagram(Diagram diagram, String outputPath) throws Exception {
                // Not implemented
            }

            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public String getDrawerType() {
                return "UnavailableDrawer";
            }
        };

        assertFalse(drawer.isAvailable());
    }

    @Test
    void testGetDrawerType_Different() {
        IDiagramDrawer drawer1 = new IDiagramDrawer() {
            @Override
            public void drawDiagram(Diagram diagram, String outputPath) throws Exception {}

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String getDrawerType() {
                return "Type1";
            }
        };

        IDiagramDrawer drawer2 = new IDiagramDrawer() {
            @Override
            public void drawDiagram(Diagram diagram, String outputPath) throws Exception {}

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String getDrawerType() {
                return "Type2";
            }
        };

        assertNotEquals(drawer1.getDrawerType(), drawer2.getDrawerType());
    }

    @Test
    void testDrawDiagram_WithException() {
        IDiagramDrawer drawer = new IDiagramDrawer() {
            @Override
            public void drawDiagram(Diagram diagram, String outputPath) throws Exception {
                throw new Exception("Drawing failed");
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String getDrawerType() {
                return "ErrorDrawer";
            }
        };

        Diagram mockDiagram = new Diagram();
        Exception exception = assertThrows(Exception.class, () -> {
            drawer.drawDiagram(mockDiagram, "output.png");
        });
        assertEquals("Drawing failed", exception.getMessage());
    }

    @Test
    void testDrawDiagram_NullDiagram() {
        IDiagramDrawer drawer = new IDiagramDrawer() {
            @Override
            public void drawDiagram(Diagram diagram, String outputPath) throws Exception {
                assertNull(diagram);
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String getDrawerType() {
                return "TestDrawer";
            }
        };

        assertDoesNotThrow(() -> drawer.drawDiagram(null, "output.png"));
    }

    @Test
    void testDrawDiagram_NullOutputPath() {
        IDiagramDrawer drawer = new IDiagramDrawer() {
            @Override
            public void drawDiagram(Diagram diagram, String outputPath) throws Exception {
                assertNull(outputPath);
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String getDrawerType() {
                return "TestDrawer";
            }
        };

        Diagram mockDiagram = new Diagram();
        assertDoesNotThrow(() -> drawer.drawDiagram(mockDiagram, null));
    }
}
