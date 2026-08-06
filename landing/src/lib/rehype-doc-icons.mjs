const EMOJI_PATTERN = /([☕⚙⚠⚪✅✓✗❌🆘🎛🎨🎭🎯🏗🏷🐛🐳💡💬📊📋📏📚📝📦🔀🔄🔌🔍🔐🔑🔒🔗🔢🔧🔴🖥🗂🗑🚀🛡🤖🤝🧩🧪🧬🧭]\uFE0F?)/gu;

const iconByEmoji = new Map([
  ['🚀', 'rocket'],
  ['🔧', 'tool'],
  ['⚙', 'tool'],
  ['📚', 'book'],
  ['📖', 'book'],
  ['⚠', 'alert'],
  ['🆘', 'alert'],
  ['🤖', 'bot'],
  ['🎯', 'target'],
  ['📦', 'package'],
  ['🐳', 'package'],
  ['🔄', 'refresh'],
  ['🔗', 'link'],
  ['🔑', 'key'],
  ['🔐', 'lock'],
  ['🔒', 'lock'],
  ['🛡', 'shield'],
  ['🐛', 'bug'],
  ['✅', 'check'],
  ['✓', 'check'],
  ['❌', 'x'],
  ['✗', 'x'],
  ['📋', 'clipboard'],
  ['📝', 'clipboard'],
  ['📊', 'chart'],
  ['💡', 'bulb'],
  ['💬', 'message'],
  ['🎨', 'palette'],
  ['🎭', 'palette'],
  ['🏷', 'tag'],
  ['🧪', 'flask'],
  ['🧬', 'flask'],
  ['🏗', 'layers'],
  ['🧩', 'layers'],
  ['🧭', 'compass'],
  ['🖥', 'monitor'],
  ['🗑', 'trash'],
  ['☕', 'coffee'],
  ['🎛', 'sliders'],
  ['🤝', 'users'],
  ['🔌', 'plug'],
  ['🔍', 'search'],
  ['🔢', 'hash'],
  ['🔀', 'shuffle'],
  ['📏', 'ruler'],
  ['🗂', 'folder'],
  ['🔴', 'circle'],
  ['⚪', 'circle'],
]);

const paths = {
  rocket: 'M14 4c3-2 6-2 6-2s0 3-2 6l-7 7-4-4 7-7ZM7 11l-3 1-2 4 5-1m4 0-1 5 4-2 1-3M5 19l-2 2',
  tool: 'M14.7 6.3a4 4 0 0 0-5-5L12 3.6 9.6 6 7.3 3.7a4 4 0 0 0 5 5L4 17l3 3 8.3-8.3a4 4 0 0 0 5-5L18 9l-3-3 2.3-2.3',
  book: 'M4 4h6a3 3 0 0 1 3 3v13a3 3 0 0 0-3-3H4Zm16 0h-4a3 3 0 0 0-3 3v13a3 3 0 0 1 3-3h4Z',
  alert: 'M12 3 2 21h20ZM12 9v5m0 3h.01',
  bot: 'M9 3h6m-3 0v3M5 8h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2Zm3 5h.01M16 13h.01M8 17h8',
  target: 'M12 2v3m0 14v3M2 12h3m14 0h3M18.4 5.6A9 9 0 1 1 5.6 18.4 9 9 0 0 1 18.4 5.6ZM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z',
  package: 'm3 7 9-4 9 4-9 4Zm0 0v10l9 4 9-4V7M12 11v10',
  refresh: 'M20 7v5h-5M4 17v-5h5m10.5-3A8 8 0 0 0 6 6l-2 3m16 6-2 3a8 8 0 0 1-13.5-3',
  link: 'M10 13a5 5 0 0 0 7.5.5l2-2a5 5 0 0 0-7-7l-1.2 1.2M14 11a5 5 0 0 0-7.5-.5l-2 2a5 5 0 0 0 7 7l1.2-1.2',
  key: 'M15 7a5 5 0 1 1-4.6 7H3v-3h3V8h4.4A5 5 0 0 1 15 7Zm0 0h.01',
  lock: 'M6 10V7a6 6 0 0 1 12 0v3M5 10h14v11H5Zm7 4v3',
  shield: 'M12 2 4 5v6c0 5 3.5 8.5 8 11 4.5-2.5 8-6 8-11V5Zm-3 10 2 2 4-5',
  bug: 'M8 8h8v8a4 4 0 0 1-8 0Zm4-5v5M4 13h4m8 0h4M5 7l3 3m11-3-3 3M5 19l3-3m11 3-3-3',
  check: 'M20 6 9 17l-5-5',
  x: 'm6 6 12 12M18 6 6 18',
  clipboard: 'M9 4h6l1 3H8Zm-3 2H4v15h16V6h-2M8 12h8m-8 4h6',
  chart: 'M4 20V10m6 10V4m6 16v-7m4 7H2',
  bulb: 'M9 18h6m-5 3h4m-2-19a7 7 0 0 0-4 12c1 1 1 2 1 4h6c0-2 0-3 1-4a7 7 0 0 0-4-12Z',
  message: 'M21 15a4 4 0 0 1-4 4H8l-5 3 1.5-5A7 7 0 0 1 3 13V8a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4Z',
  palette: 'M12 3a9 9 0 0 0 0 18h1a2 2 0 0 0 0 0-4h-1a2 2 0 0 1 0-4h5a4 4 0 0 0 4-4c0-3-4-6-9-6ZM7 10h.01m3-3h.01m4 0h.01m3 3h.01',
  tag: 'M3 12V4h8l10 10-7 7Zm5-4h.01',
  flask: 'M9 3h6m-5 0v6L4 19a2 2 0 0 0 2 3h12a2 2 0 0 0 2-3L14 9V3M7 17h10',
  layers: 'm12 2 10 5-10 5L2 7Zm10 10-10 5-10-5m20 5-10 5-10-5',
  compass: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm3 7-2 5-5 2 2-5Z',
  monitor: 'M3 4h18v13H3Zm5 17h8m-4-4v4',
  trash: 'M4 7h16M9 3h6l1 4H8Zm-2 4 1 14h10l1-14M10 11v6m4-6v6',
  coffee: 'M4 8h13v7a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5Zm13 2h2a3 3 0 0 1 0 6h-2M7 2v3m4-3v3',
  sliders: 'M4 5h10m4 0h2M4 12h3m4 0h9M4 19h8m4 0h4M14 3v4M7 10v4m5 3v4',
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm8-7a4 4 0 0 1 0 8m5 9v-2a4 4 0 0 0-3-3.9',
  plug: 'm12 22 4-4-4-4 4-4M9 7l8 8m-3-11 3 3m1-6 3 3M7 9l-4 4 8 8 4-4',
  search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14Zm5 12 5 5',
  hash: 'M5 9h14M4 15h14M10 3 8 21m8-18-2 18',
  shuffle: 'M4 7h3l10 10h3m0 0-3-3m3 3-3 3M4 17h3l3-3m4-4 3-3h3m0 0-3-3m3 3-3 3',
  ruler: 'm4 17 13-13 3 3L7 20Zm9-9 3 3m-6 0 2 2m-5 1 3 3',
  folder: 'M3 6h7l2 2h9v11H3Z',
  circle: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Z',
};

export const stripDocEmoji = (value) => value.replace(EMOJI_PATTERN, '').replace(/\s{2,}/g, ' ').trim();

const iconNode = (emoji) => {
  const key = iconByEmoji.get(emoji.replace(/\uFE0F/g, '')) ?? 'circle';
  return {
    type: 'element',
    tagName: 'span',
    properties: {
      className: ['doc-icon'],
      ariaHidden: 'true',
      dataIcon: key,
    },
    children: [
      {
        type: 'element',
        tagName: 'svg',
        properties: {
          viewBox: '0 0 24 24',
          fill: 'none',
          stroke: 'currentColor',
          strokeWidth: 1.8,
          strokeLinecap: 'round',
          strokeLinejoin: 'round',
          focusable: 'false',
        },
        children: [
          {
            type: 'element',
            tagName: 'path',
            properties: { d: paths[key] ?? paths.circle },
            children: [],
          },
        ],
      },
    ],
  };
};

export function rehypeDocIcons() {
  return (tree) => {
    const walk = (node, blocked = false) => {
      const skip = blocked || ['code', 'pre', 'script', 'style'].includes(node.tagName);
      if (!skip && Array.isArray(node.children)) {
        node.children = node.children.flatMap((child) => {
          if (child.type !== 'text' || !EMOJI_PATTERN.test(child.value)) {
            EMOJI_PATTERN.lastIndex = 0;
            return child;
          }

          EMOJI_PATTERN.lastIndex = 0;
          const parts = child.value.split(EMOJI_PATTERN);
          return parts.filter(Boolean).map((part) => {
            EMOJI_PATTERN.lastIndex = 0;
            return EMOJI_PATTERN.test(part) ? iconNode(part) : { type: 'text', value: part };
          });
        });
        node.children.forEach((child) => walk(child, skip));
      }
    };

    walk(tree);
  };
}
