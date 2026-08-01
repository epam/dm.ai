import { REPO, DOCS } from './site';

export interface NavLink {
  label: string;
  href: string;
  /** Off-site links open in a new tab and skip the scroll spy. */
  external?: boolean;
}

/**
 * The header list. In-page entries drive the scroll spy — it pairs each
 * `#`-link with the section of that id — so an anchor that names no section
 * simply goes unmarked rather than breaking.
 */
export const navLinks: NavLink[] = [
  { label: 'Why', href: '#problem' },
  { label: 'Platform', href: '#layer' },
  { label: 'Architecture', href: '#architecture' },
  { label: 'Adoption', href: '#paths' },
  { label: 'FAQ', href: '#faq' },
  { label: 'Docs', href: `${DOCS}`, external: true },
];

export interface FooterColumn {
  title: string;
  links: { label: string; href: string }[];
}

export const footerColumns: FooterColumn[] = [
  {
    title: 'Docs',
    links: [
      { label: 'Installation', href: `${DOCS}/references/installation` },
      { label: 'Configuration', href: `${DOCS}/references/configuration` },
      { label: 'MCP tools', href: `${DOCS}/references/mcp-tools` },
      { label: 'Jobs and agents', href: `${DOCS}/references/jobs` },
    ],
  },
  {
    title: 'Community',
    links: [
      { label: 'GitHub', href: REPO },
      { label: 'Releases', href: `${REPO}/releases` },
      { label: 'Contributing', href: `${REPO}/blob/main/CONTRIBUTING.md` },
      { label: 'Issues', href: `${REPO}/issues` },
    ],
  },
  {
    title: 'EPAM',
    links: [
      { label: 'epam.com', href: 'https://www.epam.com' },
      { label: 'SolutionsHub', href: 'https://solutionshub.epam.com' },
      { label: 'Licence', href: `${REPO}/blob/main/LICENSE` },
      { label: 'Security', href: `${REPO}/blob/main/SECURITY.md` },
    ],
  },
];
