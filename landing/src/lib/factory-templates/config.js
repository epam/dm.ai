// Machine loop wiring for THIS repository (see dmtools-agents
// docs/machine-factory-integration.md §5a). The factory resolves leg →
// runner here; runner paths are repo-relative.
module.exports = {
  // Whose login is "the machine" — auto rework and pr_approved arming
  // fire only on PRs authored by this login.
  machineAuthor: '@@MACHINE@@',
  sm: {
    runners: {
      bug: '.dmtools/runners/fa-bug-dev.json',
      story: '.dmtools/runners/fa-story-dev.json',
      review: '.dmtools/runners/fa-review.json',
      rework: '.dmtools/runners/fa-rework.json'
    }
  }
};
