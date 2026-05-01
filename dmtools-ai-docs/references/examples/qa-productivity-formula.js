function calculate(metrics) {
    var score = 0;
    score += metrics.created_tests * 3;
    score += metrics.created_bugs * 2;
    score += metrics.stories_done * 5;
    return score;
}
