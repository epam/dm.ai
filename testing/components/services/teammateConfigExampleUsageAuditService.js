const { AuditedAgentEntry } = require('../../core/models/auditedAgentEntry');
const {
  extractMarkdownLink,
  extractSection,
  parseMarkdownTable,
} = require('../../core/utils/markdownTableParser');

class TeammateConfigExampleUsageAuditService {
  constructor({ config, repository }) {
    this.config = config;
    this.repository = repository;
  }

  findCommonJobReferenceEntryByAcceptedName(entries, acceptedName) {
    return entries.find((entry) => entry.acceptedNames.includes(acceptedName)) ?? null;
  }

  resolveExampleUsagePath(entry) {
    if (!entry.exampleUsage) {
      return null;
    }

    return this.repository.resolveRelative(this.config.teammateConfigsPath, entry.exampleUsage.target);
  }

  resolveExampleUsageRelativePath(entry) {
    const resolvedPath = this.resolveExampleUsagePath(entry);

    if (!resolvedPath) {
      return null;
    }

    return this.repository.relativeToRoot(this.config.repoRoot, resolvedPath);
  }

  auditCommonJobReference() {
    const teammateConfigs = this.repository.readText(this.config.teammateConfigsPath);
    const section = extractSection(teammateConfigs, '🧭 Common job reference');

    if (!section) {
      throw new Error('Could not find the "Common job reference" section in teammate-configs.md');
    }

    const entries = parseMarkdownTable(section).map((row) => this.toAuditedAgentEntry(row));
    const issues = [];

    for (const entry of entries) {
      const link = entry.exampleUsage;

      if (!link) {
        issues.push(`[${entry.job}] missing markdown link in the Example column.`);
        continue;
      }

      const resolvedPath = this.repository.resolveRelative(this.config.teammateConfigsPath, link.target);
      const isAllowedPath = this.config.allowedExampleRoots.some((rootPath) =>
        this.repository.isWithin(rootPath, resolvedPath),
      );

      if (!isAllowedPath) {
        issues.push(
          `[${entry.job}] link target "${link.target}" resolves outside the allowed agents/ or dmtools-ai-docs/ directories.`,
        );
        continue;
      }

      if (!this.repository.exists(resolvedPath)) {
        issues.push(`[${entry.job}] link target "${link.target}" resolves to a missing file.`);
        continue;
      }

      if (!this.repository.isFile(resolvedPath)) {
        issues.push(`[${entry.job}] link target "${link.target}" does not resolve to a file.`);
        continue;
      }

      const targetContent = this.repository.readText(resolvedPath);
      if (!this.hasRelevantExampleContent(entry, targetContent)) {
        issues.push(
          `[${entry.job}] link target "${this.repository.relativeToRoot(
            this.config.repoRoot,
            resolvedPath,
          )}" does not contain content relevant to ${entry.acceptedNames.join(' / ')}.`,
        );
      }
    }

    return {
      entries,
      issues,
    };
  }

  toAuditedAgentEntry(row) {
    return new AuditedAgentEntry({
      job: stripInlineCode(row.Job),
      summary: row.Summary,
      acceptedNames: extractInlineCodeValues(row['Accepted `name`']),
      exampleUsage: extractMarkdownLink(row.Example),
    });
  }

  hasRelevantExampleContent(entry, targetContent) {
    return entry.acceptedNames.some((acceptedName) => {
      const escapedName = escapeForRegExp(acceptedName);
      const directNameMatch = new RegExp(`"name"\\s*:\\s*"${escapedName}"`).test(targetContent);
      const plainTextMatch = new RegExp(`\\b${escapedName}\\b`).test(targetContent);
      return directNameMatch || plainTextMatch;
    });
  }
}

function extractInlineCodeValues(value) {
  const matches = [...value.matchAll(/`([^`]+)`/g)].map((match) => match[1].trim());
  return matches.length > 0 ? matches : [stripInlineCode(value)];
}

function stripInlineCode(value) {
  return value.replace(/`/g, '').trim();
}

function escapeForRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

module.exports = { TeammateConfigExampleUsageAuditService };
