package com.github.istin.dmtools.di;

import com.github.istin.dmtools.common.kb.tool.KBTools;
import dagger.Component;

import javax.inject.Singleton;

/**
 * Dagger component for Knowledge Base dependencies
 */
@Singleton
@Component(modules = {KnowledgeBaseModule.class, AIComponentsModule.class, ConfigurationModule.class})
public interface KnowledgeBaseComponent {
    KBTools kbTools();
    com.github.istin.dmtools.common.kb.agent.KBOrchestrator kbOrchestrator();
}

