class AuditedAgentEntry {
  constructor({ job, summary, acceptedNames, exampleUsage }) {
    this.job = job;
    this.summary = summary;
    this.acceptedNames = acceptedNames;
    this.exampleUsage = exampleUsage;
  }
}

module.exports = { AuditedAgentEntry };
