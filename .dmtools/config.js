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
        parentTicket: 'DMC-101',
        questions: {
            fetchJql: 'parent = {ticketKey} AND issuetype = Subtask ORDER BY created ASC',
            answerField: 'Answer'
        },
        fields: {
            acceptanceCriteria: 'Acceptance Criteria'
        }
    },

    git: {
        baseBranch: 'main'
    },

    agentConfigsDir: 'agents',

    cliPrompts: {
        story_development: [
            './.dmtools/instructions/architecture/dmtools_core_scope.md',
            './.dmtools/prompts/development_focus.md'
        ],
        bug_development: [
            './.dmtools/instructions/architecture/dmtools_core_scope.md',
            './.dmtools/prompts/development_focus.md'
        ],
        bug_rca: [
            './.dmtools/instructions/architecture/dmtools_core_scope.md'
        ],
        pr_review: [
            './.dmtools/instructions/architecture/dmtools_core_scope.md',
            './.dmtools/prompts/review_focus.md'
        ],
        pr_rework: [
            './.dmtools/instructions/architecture/dmtools_core_scope.md',
            './.dmtools/prompts/rework_focus.md'
        ]
    },

    additionalInstructions: {
        po_refinement: [
            './agents/instructions/common/investigate_before_answer.md',
            './.dmtools/instructions/product/po_domain_knowledge.md'
        ],
        story_description: [
            './.dmtools/instructions/product/po_domain_knowledge.md',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/11665485/Template+Story'
        ],
        story_acceptance_criteria: [
            './.dmtools/instructions/product/po_domain_knowledge.md',
            './agents/instructions/common/investigate_before_answer.md',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/11665485/Template+Story'
        ],
        story_acceptance_criterias: [
            './.dmtools/instructions/product/po_domain_knowledge.md',
            './agents/instructions/common/investigate_before_answer.md',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/11665485/Template+Story'
        ],
        story_questions: [
            './.dmtools/instructions/product/po_domain_knowledge.md',
            './agents/instructions/common/investigate_before_answer.md',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/11665581/Template+Q',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/18186241/Template+Jira+Markdown'
        ],
        story_solution: [
            './.dmtools/instructions/product/po_domain_knowledge.md',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/56754177/Template+Solution+Design',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/18186241/Template+Jira+Markdown'
        ],
        solution_description: [
            './.dmtools/instructions/product/po_domain_knowledge.md',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/56754177/Template+Solution+Design',
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/18186241/Template+Jira+Markdown'
        ],
        bug_creation: [
            'https://dmtools.atlassian.net/wiki/spaces/AINA/pages/18186241/Template+Jira+Markdown'
        ]
    },

    jobParamPatches: {
        test_cases_generator: {
            confluencePages: [
                './agents/instructions/test_cases/test_case_creation_rules.md',
                './.dmtools/instructions/test_cases/dm_ai_functional_test_case_rules.md'
            ]
        }
    }
};
