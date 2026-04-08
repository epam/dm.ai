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

    additionalInstructions: {
        story_description: [
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/11665485/Template+Story'
        ],
        story_acceptance_criterias: [
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/11665485/Template+Story'
        ],
        story_questions: [
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/11665581/Template+Q',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/18186241/Template+Jira+Markdown'
        ],
        story_solution: [
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/56754177/Template+Solution+Design',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/18186241/Template+Jira+Markdown'
        ],
        solution_description: [
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/56754177/Template+Solution+Design',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/18186241/Template+Jira+Markdown'
        ],
        bug_creation: [
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/18186241/Template+Jira+Markdown'
        ]
    }
};
