/**
 * The two columns of the bridge diagram, and the bar chart under Numbers.
 *
 * `shown` are the pills drawn in the SVG — the diagram has room for that many
 * and no more. `also` are the rest: they appear in the text fallback and in the
 * line under the diagram, which is what makes "20+ integrations" checkable
 * rather than a claim.
 */
export interface Column {
  label: string;
  shown: string[];
  also: string[];
}

export const aiProviders: Column = {
  label: 'AI providers',
  shown: ['Anthropic Claude', 'OpenAI', 'Google Gemini', 'AWS Bedrock', 'GitHub Copilot', 'Ollama'],
  also: ['Vertex AI', 'DIAL'],
};

export const deliverySystems: Column = {
  label: 'Delivery & collaboration',
  shown: ['Jira', 'Azure DevOps', 'GitHub', 'GitLab', 'Confluence', 'Figma', 'Microsoft Teams', 'TestRail'],
  also: ['Bitrise', 'Jenkins', 'SharePoint', 'Bitbucket'],
};

/**
 * Tools per integration, widest first. `width` is the fraction of the track the
 * bar fills; reveal.ts applies it as a scaleX once the row scrolls into view.
 */
export interface Bar {
  name: string;
  tools: number;
  width: number;
}

export const bars: Bar[] = [
  { name: 'Jira', tools: 69, width: 1 },
  { name: 'Azure DevOps', tools: 38, width: 0.55 },
  { name: 'GitHub', tools: 35, width: 0.51 },
  { name: 'Teams', tools: 31, width: 0.45 },
  { name: 'GitLab', tools: 30, width: 0.43 },
  { name: 'Bitrise', tools: 23, width: 0.33 },
  { name: 'Figma', tools: 22, width: 0.32 },
  { name: 'Confluence', tools: 19, width: 0.28 },
  { name: 'TestRail', tools: 17, width: 0.25 },
  { name: 'Jenkins', tools: 5, width: 0.07 },
];
