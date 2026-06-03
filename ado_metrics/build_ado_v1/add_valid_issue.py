# add_valid_issue.py

from config import logger
import re

def add_valid_issue_columns(history_df, logic_expression):
    """
    Adds VI_1 to VI_9 based on logic_expression and computes the final valid_issue_flag column using the provided logic expression. Only rules referenced in the logic_expression are applied.
    """

    # Extract used rules from logic expression
    used_rules = sorted(set(re.findall(r'rule_(\d+)', logic_expression)))
    active_rules = []

    # Initialize VI columns only for used rules
    for rule_num_str in used_rules:
        rule_num = int(rule_num_str)
        col = f"VI_{rule_num}"

        if rule_num == 1:
            history_df[col] = history_df["is_ai_enabled_flag"] == True
            active_rules.append("Rule 1: is AI developer == True")

        elif rule_num == 2:
            history_df[col] = history_df["issue_type"] == "Story"
            active_rules.append("Rule 2: issue_type == 'Story'")

        elif rule_num == 3:
            history_df[col] = history_df["issue_type"] == "Task"
            active_rules.append("Rule 3: issue_type == 'Task'")

        elif rule_num == 4:
            history_df[col] = history_df["issue_type"] == "Subtask"
            active_rules.append("Rule 4: issue_type == 'Subtask'")

        elif rule_num == 5:
            history_df[col] = history_df["issue_type"] == "Sub-task"
            active_rules.append("Rule 5: issue_type == 'Sub-task'")

        elif rule_num == 6:
            history_df[col] = history_df["issue_type"] == "Feature"
            active_rules.append("Rule 6: issue_type == 'Feature'")

        elif rule_num == 7:
            history_df[col] = history_df["issue_type"] == "Defect"
            active_rules.append("Rule 7: issue_type == 'Defect'")

        elif rule_num == 8:
            history_df[col] = history_df["issue_no_of_subtasks"] == 0
            active_rules.append("Rule 8: issue_no_of_subtasks == 0")

        elif rule_num == 9:
            history_df[col] = history_df["parent_issue_type"] == "Feature"
            active_rules.append("Rule 9: parent_issue_type == 'Feature'")

        elif rule_num == 10:
            history_df[col] = history_df["parent_issue_type"] == "Story"
            active_rules.append("Rule 10: parent_issue_type == 'Story'")

        elif rule_num == 11:
            history_df[col] = history_df["issue_vendor"] == "EPAM"
            active_rules.append("Rule 11: issue_vendor == 'EPAM'")

        else:
            logger.warning(f"Unknown rule number: {rule_num}")

    # Replace rule_X with VI_X in logic expression
    try:
        expression = logic_expression
        for i in range(1, 12):
            expression = expression.replace(f"rule_{i}", f"VI_{i}")

        history_df["valid_issue_flag"] = history_df.eval(expression)
        logger.info(f"Step 17: - ✅ Applied logic expression: {logic_expression}")

    except Exception as e:
        logger.warning(f"Step 17: - ❌ Error evaluating logic expression '{logic_expression}': {e}")
        history_df["valid_issue_flag"] = False

    # Log summary
    if active_rules:
        logger.info("Step 17: - ℹ️  Active validation rules:")
        for rule in active_rules:
            logger.info(f"         - ℹ️  {rule}")
        logger.info("Step 17: - ✅ Added columns: VI_1 to VI_12 and valid_issue_flag based on logic expression.")
    else:
        logger.warning("Step 17: - ⚠️ No rules used in logic_expression.")

    # Log total rows and valid_issue_flag True count
    total_rows = len(history_df)
    valid_rows = history_df["valid_issue_flag"].sum()
    logger.info(f"Step 17: - ✅ Total rows in history_df: {total_rows}, and rows with valid_issue_flag=True: {valid_rows}")

    return history_df