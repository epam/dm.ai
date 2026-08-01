/**
 * What happens between a ticket changing state and a human reviewing a pull
 * request. The step numbers are positional — the rail in architecture.css draws
 * them — so reordering the array reorders the diagram.
 *
 * `code` marks the one fragment that renders as <code> inside its description.
 */
export interface FlowStep {
  title: string;
  desc: string;
  code?: string;
  descAfter?: string;
}

export const flow: FlowStep[] = [
  {
    title: 'Ticket state change',
    desc: 'Jira or Azure DevOps fires a service hook on update.',
  },
  {
    title: 'Bridge function',
    desc: 'A small function triggers ',
    code: 'workflow_dispatch',
    descAfter: '.',
  },
  {
    title: 'Clean runner',
    desc: 'CI installs the DMTools CLI fresh for this run.',
  },
  {
    title: 'Build context',
    desc: 'A pre-action pulls the ticket, wiki, repository and knowledge base.',
  },
  {
    title: 'Model reasons',
    desc: 'Your chosen provider works with the full picture, not a snippet.',
  },
  {
    title: 'Publish results',
    desc: 'A post-action opens a branch and a pull request.',
  },
  {
    title: 'Human in the loop',
    desc: 'An engineer reviews and approves. Always.',
  },
];
