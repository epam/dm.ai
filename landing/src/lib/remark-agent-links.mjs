/**
 * Rewrites links inside the agents documentation (agents submodule) so they
 * resolve once rendered on the site:
 *
 *   agents/snapshots/<name>.md  →  <base>agents/<name>/prompt/
 *
 * The Markdown stays repository-correct — on GitHub the link opens the
 * snapshot file; on the site it opens the rendered prompt page.
 */
export function remarkAgentLinks({ base }) {
  const snapshotRe = /^(?:\.\.\/)*agents\/snapshots\/([a-zA-Z0-9_]+)\.md$/;

  return () => (tree) => {
    const visit = (node) => {
      if (node.type === 'link' && typeof node.url === 'string') {
        const match = node.url.match(snapshotRe);
        if (match) {
          node.url = `${base}agents/${match[1]}/prompt/`;
        }
      }
      if (node.children) node.children.forEach(visit);
    };
    visit(tree);
  };
}
