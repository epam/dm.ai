/**
 * DM.AI Project Configuration for DMTools Agents
 *
 * This file configures the agents module for the DM.AI project.
 * Agents discover this file automatically when used as a git submodule:
 *   agents/ (submodule) looks for ../.dmtools/config.js
 *
 * See agents/README.md for full documentation of all configuration options.
 */
module.exports = {
    repository: {
        owner: 'epam',
        repo: 'dm.ai'
    },

    jira: {
        project: 'DMC',
        parentTicket: 'DMC-101'
    },

    git: {
        baseBranch: 'main'
    },

    agentConfigsDir: 'agents',

    customInstructions: `
IMPORTANT SCOPE CONSTRAINTS:
- This repository is the dmtools CLI module (dmtools-core) ONLY.
- API module (dmtools-server) and UI module are OUT OF SCOPE - do NOT implement, modify, or create tickets for them.
- All implementation work must target dmtools-core/src/main/java only.
- When creating subtasks, only create [CORE] subtasks. Do NOT create [API], [UI], or [SD API] subtasks.
- All new MCP tools must follow the @MCPTool / @MCPParam annotation pattern in dmtools-core.
- Do NOT suggest or reference Spring Boot, REST endpoints, or frontend changes.
`,

    additionalInstructions: {
        po_refinement: [
            'agents/instructions/common/investigate_before_answer.md',
            'agents/instructions/product/po_domain_knowledge.md'
        ],
        story_description: [
            'agents/instructions/product/po_domain_knowledge.md',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/11665485/Template+Story'
        ],
        story_acceptance_criterias: [
            'agents/instructions/product/po_domain_knowledge.md',
            'agents/instructions/common/investigate_before_answer.md',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/11665485/Template+Story'
        ],
        story_questions: [
            'agents/instructions/product/po_domain_knowledge.md',
            'agents/instructions/common/investigate_before_answer.md',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/11665581/Template+Q',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/18186241/Template+Jira+Markdown'
        ],
        story_solution: [
            'agents/instructions/product/po_domain_knowledge.md',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/56754177/Template+Solution+Design',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/18186241/Template+Jira+Markdown'
        ],
        solution_description: [
            'agents/instructions/product/po_domain_knowledge.md',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/56754177/Template+Solution+Design',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/18186241/Template+Jira+Markdown'
        ],
        bug_creation: [
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/18186241/Template+Jira+Markdown'
        ]
    }
};
