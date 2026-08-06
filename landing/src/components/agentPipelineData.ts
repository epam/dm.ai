export const GRAPH_WIDTH = 2040;
export const GRAPH_HEIGHT = 460;

export type PipelinePhase = {
  id: string;
  label: string;
  summary: string;
};

export type Point = {
  x: number;
  y: number;
};

export type PipelineAgent = {
  id: string;
  label: string;
  phaseIndex: number;
  x: number;
  y: number;
  mode: 'dispatch' | 'local' | 'review';
  summary: string;
  kind?: 'agent' | 'status';
};

export type PipelineEdge = {
  id: string;
  from: string;
  to: string;
  points: Point[];
  kind?: 'main' | 'branch' | 'loop';
};

export type TicketStep =
  | { type: 'agent'; agentId: string; durationMs: number }
  | { type: 'edge'; edgeId: string; durationMs: number };

export type PipelineTicket = {
  id: string;
  offsetMs: number;
  routeId: 'direct' | 'rework' | 'test-rework' | 'bug-loop';
  steps: TicketStep[];
};

export const pipelinePhases: PipelinePhase[] = [
  { id: 'backlog', label: 'Backlog / To Do', summary: 'Story is waiting for clarification and shaping.' },
  { id: 'po-review', label: 'PO Review', summary: 'Question subtasks are checked before BA work starts.' },
  { id: 'ba-analysis', label: 'BA Analysis', summary: 'Questions are answered and requirements become precise.' },
  { id: 'solution', label: 'Solution Architecture', summary: 'DMTools prepares implementation direction.' },
  { id: 'ready-dev', label: 'Ready For Development', summary: 'Story is ready for code generation.' },
  { id: 'review', label: 'In Review', summary: 'A GitHub PR exists and review agents inspect it.' },
  { id: 'rework', label: 'In Rework', summary: 'Requested changes loop back into the same PR.' },
  { id: 'merged', label: 'Merged', summary: 'Approved implementation is merged.' },
  { id: 'ready-testing', label: 'Ready For Testing', summary: 'Generated tests are ready to automate and run.' },
  { id: 'testing', label: 'In Testing', summary: 'Linked test cases are executing or being checked.' },
  { id: 'test-rework', label: 'Test Rework', summary: 'Automation or test evidence needs another pass.' },
  { id: 'bugs-created', label: 'Bugs Created', summary: 'Failed testing creates linked defect tickets.' },
  { id: 'bug-development', label: 'Bug Development', summary: 'Bug tickets move through implementation.' },
  { id: 'bug-review', label: 'Bug Review', summary: 'Bug fixes are reviewed before retesting.' },
  { id: 'bug-rework', label: 'Bug Rework', summary: 'Bug fixes loop back when review asks for changes.' },
  { id: 'retest', label: 'In Testing / Retest', summary: 'The story is checked again after fixes land.' },
  { id: 'done', label: 'Done', summary: 'Linked test cases passed and story can close.' },
];

export const pipelineAgents: PipelineAgent[] = [
  {
    id: 'story_questions',
    label: 'story_questions',
    phaseIndex: 0,
    x: 60,
    y: 185,
    mode: 'dispatch',
    summary: 'Creates clarification subtasks and applies the question label.',
  },
  {
    id: 'po_refinement',
    label: 'po_refinement',
    phaseIndex: 0,
    x: 60,
    y: 315,
    mode: 'dispatch',
    summary: 'AI PO answers question subtasks from parent story context.',
  },
  {
    id: 'story_ba_check',
    label: 'story_ba_check',
    phaseIndex: 1,
    x: 180,
    y: 315,
    mode: 'local',
    summary: 'Checks that all question subtasks are done before BA Analysis.',
  },
  {
    id: 'story_acceptance_criteria',
    label: 'story_acceptance_criteria',
    phaseIndex: 2,
    x: 300,
    y: 215,
    mode: 'dispatch',
    summary: 'Writes acceptance criteria from collected Q and A context.',
  },
  {
    id: 'story_solution',
    label: 'story_solution',
    phaseIndex: 3,
    x: 420,
    y: 215,
    mode: 'dispatch',
    summary: 'Produces solution design and diagrams before development.',
  },
  {
    id: 'story_development',
    label: 'story_development',
    phaseIndex: 4,
    x: 540,
    y: 215,
    mode: 'dispatch',
    summary: 'Writes code, creates a branch, commits, and opens the PR.',
  },
  {
    id: 'pr_review',
    label: 'pr_review',
    phaseIndex: 5,
    x: 660,
    y: 215,
    mode: 'review',
    summary: 'Reviews the existing PR and either approves or requests changes.',
  },
  {
    id: 'retry_merge',
    label: 'retry_merge',
    phaseIndex: 5,
    x: 660,
    y: 95,
    mode: 'local',
    summary: 'Squash-merges an approved PR and clears the approval label.',
  },
  {
    id: 'pr_rework',
    label: 'pr_rework',
    phaseIndex: 6,
    x: 780,
    y: 315,
    mode: 'dispatch',
    summary: 'Pushes fixup commits and returns the PR to review.',
  },
  {
    id: 'test_cases_generator',
    label: 'test_cases_generator',
    phaseIndex: 7,
    x: 900,
    y: 215,
    mode: 'dispatch',
    summary: 'Creates linked test case tickets and moves the story to Ready For Testing.',
  },
  {
    id: 'story_test_automation',
    label: 'story_test_automation',
    phaseIndex: 8,
    x: 1020,
    y: 215,
    mode: 'dispatch',
    summary: 'Automates linked test cases for stories that are ready for testing.',
  },
  {
    id: 'story_done_check',
    label: 'story_done_check',
    phaseIndex: 15,
    x: 1860,
    y: 215,
    mode: 'local',
    summary: 'Checks linked test cases, bug status, and closes the story when all signals pass.',
  },
  {
    id: 'done_marker',
    label: 'Done',
    phaseIndex: 16,
    x: 1980,
    y: 215,
    mode: 'local',
    kind: 'status',
    summary: 'The story reaches the final tracking status.',
  },
  {
    id: 'test_review',
    label: 'test_review',
    phaseIndex: 9,
    x: 1140,
    y: 215,
    mode: 'review',
    summary: 'Reviews automated test output and decides whether the story can close, needs test rework, or produced a bug.',
  },
  {
    id: 'test_automation_rework',
    label: 'test_automation_rework',
    phaseIndex: 10,
    x: 1260,
    y: 315,
    mode: 'dispatch',
    summary: 'Updates flaky, incomplete, or failed automation before sending it back to test review.',
  },
  {
    id: 'bug_creation',
    label: 'bug_creation',
    phaseIndex: 11,
    x: 1380,
    y: 95,
    mode: 'dispatch',
    summary: 'Creates linked bug tickets when testing finds a product defect.',
  },
  {
    id: 'bug_development',
    label: 'bug_development',
    phaseIndex: 12,
    x: 1500,
    y: 95,
    mode: 'dispatch',
    summary: 'Implements the defect fix and opens or updates the bug-fix pull request.',
  },
  {
    id: 'bug_review',
    label: 'bug_review',
    phaseIndex: 13,
    x: 1620,
    y: 95,
    mode: 'review',
    summary: 'Reviews the bug-fix pull request before the story can return to testing.',
  },
  {
    id: 'bug_rework',
    label: 'bug_rework',
    phaseIndex: 14,
    x: 1740,
    y: 315,
    mode: 'dispatch',
    summary: 'Applies requested changes to the bug fix and returns it to review.',
  },
];

export const pipelineEdges: PipelineEdge[] = [
  { id: 'story_questions_to_po_refinement', from: 'story_questions', to: 'po_refinement', kind: 'main', points: [{ x: 60, y: 223 }, { x: 60, y: 277 }] },
  { id: 'po_refinement_to_ba_check', from: 'po_refinement', to: 'story_ba_check', kind: 'main', points: [{ x: 112, y: 315 }, { x: 128, y: 315 }] },
  { id: 'ba_check_to_acceptance', from: 'story_ba_check', to: 'story_acceptance_criteria', kind: 'main', points: [{ x: 232, y: 315 }, { x: 240, y: 315 }, { x: 240, y: 215 }, { x: 248, y: 215 }] },
  { id: 'acceptance_to_solution', from: 'story_acceptance_criteria', to: 'story_solution', kind: 'main', points: [{ x: 352, y: 215 }, { x: 368, y: 215 }] },
  { id: 'solution_to_development', from: 'story_solution', to: 'story_development', kind: 'main', points: [{ x: 472, y: 215 }, { x: 488, y: 215 }] },
  { id: 'development_to_review', from: 'story_development', to: 'pr_review', kind: 'main', points: [{ x: 592, y: 215 }, { x: 608, y: 215 }] },
  { id: 'review_to_retry_merge', from: 'pr_review', to: 'retry_merge', kind: 'branch', points: [{ x: 660, y: 177 }, { x: 660, y: 133 }] },
  { id: 'review_to_rework', from: 'pr_review', to: 'pr_rework', kind: 'branch', points: [{ x: 660, y: 253 }, { x: 660, y: 315 }, { x: 728, y: 315 }] },
  { id: 'rework_to_review', from: 'pr_rework', to: 'pr_review', kind: 'loop', points: [{ x: 780, y: 277 }, { x: 780, y: 258 }, { x: 712, y: 258 }, { x: 712, y: 215 }] },
  { id: 'retry_merge_to_tests', from: 'retry_merge', to: 'test_cases_generator', kind: 'branch', points: [{ x: 712, y: 95 }, { x: 820, y: 95 }, { x: 820, y: 215 }, { x: 848, y: 215 }] },
  { id: 'tests_to_automation', from: 'test_cases_generator', to: 'story_test_automation', kind: 'main', points: [{ x: 952, y: 215 }, { x: 968, y: 215 }] },
  { id: 'automation_to_test_review', from: 'story_test_automation', to: 'test_review', kind: 'main', points: [{ x: 1072, y: 215 }, { x: 1088, y: 215 }] },
  { id: 'test_review_to_done_check', from: 'test_review', to: 'story_done_check', kind: 'main', points: [{ x: 1192, y: 215 }, { x: 1808, y: 215 }] },
  { id: 'test_review_to_test_rework', from: 'test_review', to: 'test_automation_rework', kind: 'branch', points: [{ x: 1140, y: 253 }, { x: 1140, y: 315 }, { x: 1208, y: 315 }] },
  { id: 'test_rework_to_test_review', from: 'test_automation_rework', to: 'test_review', kind: 'loop', points: [{ x: 1260, y: 277 }, { x: 1260, y: 258 }, { x: 1192, y: 258 }, { x: 1192, y: 215 }] },
  { id: 'test_review_to_bug_creation', from: 'test_review', to: 'bug_creation', kind: 'branch', points: [{ x: 1140, y: 177 }, { x: 1140, y: 95 }, { x: 1328, y: 95 }] },
  { id: 'bug_creation_to_bug_development', from: 'bug_creation', to: 'bug_development', kind: 'main', points: [{ x: 1432, y: 95 }, { x: 1448, y: 95 }] },
  { id: 'bug_development_to_bug_review', from: 'bug_development', to: 'bug_review', kind: 'main', points: [{ x: 1552, y: 95 }, { x: 1568, y: 95 }] },
  { id: 'bug_review_to_bug_rework', from: 'bug_review', to: 'bug_rework', kind: 'branch', points: [{ x: 1620, y: 133 }, { x: 1620, y: 315 }, { x: 1688, y: 315 }] },
  { id: 'bug_rework_to_bug_review', from: 'bug_rework', to: 'bug_review', kind: 'loop', points: [{ x: 1740, y: 277 }, { x: 1740, y: 258 }, { x: 1672, y: 258 }, { x: 1672, y: 95 }] },
  { id: 'bug_review_to_done_check', from: 'bug_review', to: 'story_done_check', kind: 'main', points: [{ x: 1672, y: 95 }, { x: 1788, y: 95 }, { x: 1788, y: 215 }, { x: 1808, y: 215 }] },
  { id: 'done_check_to_done', from: 'story_done_check', to: 'done_marker', kind: 'main', points: [{ x: 1912, y: 215 }, { x: 1928, y: 215 }] },
];

const directSteps: TicketStep[] = [
  { type: 'agent', agentId: 'story_questions', durationMs: 900 },
  { type: 'edge', edgeId: 'story_questions_to_po_refinement', durationMs: 450 },
  { type: 'agent', agentId: 'po_refinement', durationMs: 900 },
  { type: 'edge', edgeId: 'po_refinement_to_ba_check', durationMs: 450 },
  { type: 'agent', agentId: 'story_ba_check', durationMs: 700 },
  { type: 'edge', edgeId: 'ba_check_to_acceptance', durationMs: 650 },
  { type: 'agent', agentId: 'story_acceptance_criteria', durationMs: 950 },
  { type: 'edge', edgeId: 'acceptance_to_solution', durationMs: 500 },
  { type: 'agent', agentId: 'story_solution', durationMs: 950 },
  { type: 'edge', edgeId: 'solution_to_development', durationMs: 500 },
  { type: 'agent', agentId: 'story_development', durationMs: 1200 },
  { type: 'edge', edgeId: 'development_to_review', durationMs: 500 },
  { type: 'agent', agentId: 'pr_review', durationMs: 900 },
  { type: 'edge', edgeId: 'review_to_retry_merge', durationMs: 500 },
  { type: 'agent', agentId: 'retry_merge', durationMs: 700 },
  { type: 'edge', edgeId: 'retry_merge_to_tests', durationMs: 850 },
  { type: 'agent', agentId: 'test_cases_generator', durationMs: 1000 },
  { type: 'edge', edgeId: 'tests_to_automation', durationMs: 550 },
  { type: 'agent', agentId: 'story_test_automation', durationMs: 900 },
  { type: 'edge', edgeId: 'automation_to_test_review', durationMs: 550 },
  { type: 'agent', agentId: 'test_review', durationMs: 800 },
  { type: 'edge', edgeId: 'test_review_to_done_check', durationMs: 1300 },
  { type: 'agent', agentId: 'story_done_check', durationMs: 800 },
  { type: 'edge', edgeId: 'done_check_to_done', durationMs: 450 },
  { type: 'agent', agentId: 'done_marker', durationMs: 650 },
];

const reworkSteps: TicketStep[] = [
  ...directSteps.slice(0, 13),
  { type: 'edge', edgeId: 'review_to_rework', durationMs: 700 },
  { type: 'agent', agentId: 'pr_rework', durationMs: 1100 },
  { type: 'edge', edgeId: 'rework_to_review', durationMs: 700 },
  { type: 'agent', agentId: 'pr_review', durationMs: 850 },
  ...directSteps.slice(13),
];

const testReworkSteps: TicketStep[] = [
  ...directSteps.slice(0, 21),
  { type: 'edge', edgeId: 'test_review_to_test_rework', durationMs: 700 },
  { type: 'agent', agentId: 'test_automation_rework', durationMs: 1000 },
  { type: 'edge', edgeId: 'test_rework_to_test_review', durationMs: 700 },
  { type: 'agent', agentId: 'test_review', durationMs: 760 },
  ...directSteps.slice(21),
];

const bugLoopSteps: TicketStep[] = [
  ...directSteps.slice(0, 21),
  { type: 'edge', edgeId: 'test_review_to_bug_creation', durationMs: 900 },
  { type: 'agent', agentId: 'bug_creation', durationMs: 900 },
  { type: 'edge', edgeId: 'bug_creation_to_bug_development', durationMs: 500 },
  { type: 'agent', agentId: 'bug_development', durationMs: 1100 },
  { type: 'edge', edgeId: 'bug_development_to_bug_review', durationMs: 500 },
  { type: 'agent', agentId: 'bug_review', durationMs: 850 },
  { type: 'edge', edgeId: 'bug_review_to_bug_rework', durationMs: 850 },
  { type: 'agent', agentId: 'bug_rework', durationMs: 950 },
  { type: 'edge', edgeId: 'bug_rework_to_bug_review', durationMs: 850 },
  { type: 'agent', agentId: 'bug_review', durationMs: 760 },
  { type: 'edge', edgeId: 'bug_review_to_done_check', durationMs: 900 },
  ...directSteps.slice(22),
];

export const pipelineTickets: PipelineTicket[] = [
  { id: 'ST-142', offsetMs: 0, routeId: 'direct', steps: directSteps },
  { id: 'ST-188', offsetMs: 4300, routeId: 'rework', steps: reworkSteps },
  { id: 'ST-205', offsetMs: 8700, routeId: 'test-rework', steps: testReworkSteps },
  { id: 'BUG-77', offsetMs: 12500, routeId: 'bug-loop', steps: bugLoopSteps },
];
