// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.diagram;

import com.github.istin.dmtools.ai.Diagram;

/**
 * Interface for diagram drawing implementations.
 * 
 * This interface allows DiagramsCreator to optionally use diagram drawing
 * capabilities without having a hard dependency on automation libraries.
 * 
 * Implementations may use different rendering engines (headless browsers, 
 * image generation libraries, etc.)
 */
public interface IDiagramDrawer {
    
    /**
     * Draw a diagram and save it as an image file.
     * 
     * @param diagram The diagram to draw
     * @param outputPath The path where the image should be saved
     * @throws Exception if diagram drawing fails
     */
    void drawDiagram(Diagram diagram, String outputPath) throws Exception;
    
    /**
     * Check if the diagram drawer is available and ready to use.
     * 
     * @return true if the drawer can be used, false otherwise
     */
    boolean isAvailable();
    
    /**
     * Get the name/type of this diagram drawer implementation.
     * 
     * @return Human-readable name of the drawer
     */
    String getDrawerType();
}
