function calculate(metrics) {
    var score = 0;
    score += metrics.stories_moved_to_testing * 5;
    score += metrics.bugs_moved_to_testing * 3;
    score += metrics.pull_requests * 2;
    return score;
}
