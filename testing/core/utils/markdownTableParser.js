function extractSection(markdown, headingTitle) {
  const lines = markdown.split(/\r?\n/);
  const sectionLines = [];
  let isInSection = false;
  let sectionLevel = 0;

  for (const line of lines) {
    const headingMatch = line.match(/^(#+)\s+(.*)$/);

    if (headingMatch) {
      const [, hashes, rawTitle] = headingMatch;
      const title = rawTitle.trim();

      if (!isInSection && title === headingTitle) {
        isInSection = true;
        sectionLevel = hashes.length;
        continue;
      }

      if (isInSection && hashes.length <= sectionLevel) {
        break;
      }
    }

    if (isInSection) {
      sectionLines.push(line);
    }
  }

  return sectionLines.join('\n').trim();
}

function parseMarkdownTable(sectionMarkdown) {
  const tableLines = sectionMarkdown
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.startsWith('|'));

  if (tableLines.length < 3) {
    throw new Error('Expected a markdown table with a header, separator, and at least one data row');
  }

  const headers = parseMarkdownRow(tableLines[0]);
  const rows = tableLines.slice(2).map((line) => {
    const values = parseMarkdownRow(line);
    return headers.reduce((record, header, index) => {
      record[header] = values[index] ?? '';
      return record;
    }, {});
  });

  return rows;
}

function parseMarkdownRow(row) {
  return row
    .split('|')
    .slice(1, -1)
    .map((cell) => cell.trim());
}

function extractMarkdownLink(cell) {
  const match = cell.match(/\[([^\]]+)\]\(([^)]+)\)/);
  if (!match) {
    return null;
  }

  return {
    text: match[1].trim(),
    target: match[2].trim(),
  };
}

module.exports = {
  extractMarkdownLink,
  extractSection,
  parseMarkdownTable,
};
