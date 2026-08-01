/**
 * Objection-led, in the order a buyer raises them: what is it, where does our
 * data go, who has to run it, what if the model is wrong. Answers open with a
 * self-contained sentence naming DMTools rather than "it" — the rest of the
 * page speaks in metaphor, which reads well and extracts badly, and this is the
 * section an assistant can quote.
 *
 * This list renders twice: as the visible <details> accordion, and as the
 * FAQPage structured data in the <head>. Both come from here, so the two can no
 * longer disagree — which they previously could, and only a comment prevented.
 */
export interface FaqEntry {
  question: string;
  answer: string;
  /** Opens the section on the definitional answer rather than a wall of closed rows. */
  open?: boolean;
}

export const faq: FaqEntry[] = [
  {
    question: 'What is DMTools?',
    answer:
      'DMTools is an open-source command-line orchestrator that connects AI agents to enterprise delivery systems. It exposes 320+ tools across 20+ integrations — Jira, Azure DevOps, GitHub, GitLab, Confluence, Figma, Microsoft Teams and every major AI provider — behind a single CLI. DMTools is built and maintained by EPAM Systems and released under the Apache 2.0 licence.',
    open: true,
  },
  {
    question: 'Is our data sent to EPAM or to a DMTools service?',
    answer:
      'No. DMTools is a CLI you run inside your own CI pipeline or on your own machine, with your own credentials and your own choice of AI provider. There is no DMTools-operated backend in between, and no telemetry path back to EPAM.',
  },
  {
    question: 'Do I need to be a developer to benefit from DMTools?',
    answer:
      'No. One technical teammate installs and configures DMTools once per project — Java 17 or newer plus a set of environment variables. After that the output reaches delivery managers, QA and platform teams as processed tickets, drafted pull requests and generated reports, not as a tool anyone has to operate.',
  },
  {
    question: 'What happens if the AI gets something wrong?',
    answer:
      'Nothing merges on its own. Every branch and pull request DMTools produces stops for human review before it ships — the model drafts the work, your engineers approve and merge it. Because each run installs fresh and leaves its trail in CI, a wrong answer is auditable rather than mysterious.',
  },
  {
    question: 'Does DMTools only work with GitHub?',
    answer:
      'No. DMTools integrates with Jira, Azure DevOps, GitHub, GitLab, Bitbucket, Confluence, SharePoint, Figma, Microsoft Teams, TestRail, Jenkins and Bitrise — more than twenty systems in total. GitHub is one of them, not a requirement.',
  },
  {
    question: 'Which AI providers can we use?',
    answer:
      'DMTools supports Anthropic Claude, OpenAI, Google Gemini, Google Vertex AI, AWS Bedrock, the EPAM DIAL enterprise gateway, and fully local models through Ollama when code and tickets must never leave your network. The provider is chosen at runtime through environment variables, so switching takes no code change.',
  },
  {
    question: 'What does DMTools cost?',
    answer:
      'DMTools itself is free and open source under Apache 2.0 — no account, no licence key, no paid tier. You pay only the AI provider and the delivery tools your organisation already uses.',
  },
  {
    question: 'How is DMTools different from a single MCP server?',
    answer:
      'A typical MCP server wraps one system; DMTools covers more than twenty behind one install, and exposes them four ways — direct CLI calls, MCP for assistants such as Claude and Cursor, plain JavaScript functions inside jobs, and CI pipelines. Every tool is generated from annotated Java at compile time, so the CLI surface, the MCP schema and the JavaScript functions cannot drift apart.',
  },
];
