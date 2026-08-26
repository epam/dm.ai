/**
 * Rewrites links inside the agents documentation (agents submodule) so they
 * resolve once rendered on the site:
 *
 *   agents/snapshots/<name>.md  →  <base>agents/<name>/prompt/
 *
 * The Markdown stays repository-correct — on GitHub the link opens the
 * snapshot file; on the site it opens the rendered prompt page.
 */
export function remarkAgentLinks({ base, agentsRepoUrl }) {
  const snapshotRe = /^(?:\.\.\/)*agents\/snapshots\/([a-zA-Z0-9_]+)\.md$/;
  // Any other repo-relative link into the agents submodule (human docs, JS
  // sources) — these are repository paths, so they go to the agents repo on
  // GitHub; on the site they would 404.
  const agentsRepoRe = /^(?:\.\.\/)*agents\/(.+)$/;

  return () => (tree) => {
    const visit = (node) => {
      if (node.type === 'link' && typeof node.url === 'string') {
        const snapshot = node.url.match(snapshotRe);
        if (snapshot) {
          node.url = `${base}agents/${snapshot[1]}/prompt/`;
        } else {
          const repoPath = node.url.match(agentsRepoRe);
          if (repoPath) {
            node.url = `${agentsRepoUrl}/blob/main/${repoPath[1]}`;
          }
        }
      }
      if (node.children) node.children.forEach(visit);
    };
    visit(tree);
  };
}
