function calculate(metrics) {
    var score = 0;
    score += metrics.created_features * 5;
    score += metrics.created_stories * 3;
    score += metrics.tasks_moved_to_done * 4;
    return score;
}
