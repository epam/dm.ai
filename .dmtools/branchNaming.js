/**
 * Branch naming hook for tickets that live in GitHub Issues.
 *
 * The GitHub tracker keys a ticket with the composite "owner/repo#12". Appended to the
 * branch prefix that gives "ai/owner/repo#12", which the agents reject as an unsafe ref
 * name ('#' is not allowed). Use the router key convention "gh-12" instead, which gives
 * "ai/gh-12" and is the name the machine loop already uses for GitHub-backed tickets.
 *
 * Other key shapes (Jira, ADO) keep the default "<prefix>/<key>" naming.
 *
 * Loaded through customParams.branchNamingFnPath; module.exports must be the function.
 */
module.exports = function (ticket, branchRole, config) {
    var prefixes = (config && config.git && config.git.branchPrefix) || {};
    var prefix = prefixes[branchRole] || prefixes.development || 'ai';
    var key = String((ticket && ticket.key) || '');
    var github = /^[\w.-]+\/[\w.-]+#(\d+)$/.exec(key);
    return prefix + '/' + (github ? 'gh-' + github[1] : key);
};
