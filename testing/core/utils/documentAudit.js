const fs = require('node:fs');

function readText(filePath) {
  return fs.readFileSync(filePath, 'utf8');
}

function splitLines(text) {
  return text.split(/\r?\n/);
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function findLineIndexesContaining(lines, token) {
  const indexes = [];

  for (let index = 0; index < lines.length; index += 1) {
    if (lines[index].includes(token)) {
      indexes.push(index);
    }
  }

  return indexes;
}

function buildWindows(lines, indexes, radius) {
  return indexes.map((index) => {
    const start = Math.max(0, index - radius);
    const end = Math.min(lines.length, index + radius + 1);

    return {
      lineNumber: index + 1,
      line: lines[index],
      text: lines.slice(start, end).join('\n'),
    };
  });
}

function formatWindows(windows) {
  return windows
    .map((window) => `- line ${window.lineNumber}: ${window.line.trim()}`)
    .join('\n');
}

function hasMigrationPointer(text, target, relatedPaths) {
  const escapedTarget = escapeRegExp(target);
  const pointerPatterns = [
    new RegExp(`(?:->|→)\\s*(?:\\[)?\`?${escapedTarget}\`?`, 'i'),
    new RegExp(`(?:use|replace with|migrate to|see|switch to|public name|preferred name|canonical name|replacement)\\s+(?:the\\s+)?(?:\\[)?\`?${escapedTarget}\`?`, 'i'),
    new RegExp(`\\[${escapedTarget}\\]\\([^)]*\\)`, 'i'),
  ];

  const linkPatterns = relatedPaths.map(
    (relatedPath) => new RegExp(`\\([^)]*${escapeRegExp(relatedPath)}[^)]*\\)`, 'i'),
  );

  return (
    pointerPatterns.some((pattern) => pattern.test(text)) &&
    (text.includes(`\`${target}\``) || text.includes(target) || linkPatterns.some((pattern) => pattern.test(text)))
  );
}

module.exports = {
  buildWindows,
  findLineIndexesContaining,
  formatWindows,
  hasMigrationPointer,
  readText,
  splitLines,
};
