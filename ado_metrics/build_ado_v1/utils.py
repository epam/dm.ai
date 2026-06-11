# utils.py

from datetime import datetime, timedelta
from dateutil import parser
from config import logger, TARGET_TIMEZONE


def parse_date(date_input):
    """
    Parse date strings into a datetime object.
    - Handles ISO 8601 formats (e.g., '2025-06-06T15:32:59.000Z', '2025-06-06T15:32:59+00:00').
    - Converts to TARGET_TIMEZONE and returns a naive datetime (no tzinfo).
    - Works for both JIRA Cloud and Server.
    """
    if isinstance(date_input, datetime):
        date = date_input
    else:
        try:
            # dateutil.parser.isoparse handles most ISO 8601 formats
            date = parser.isoparse(date_input)
        except Exception:
            try:
                date = parser.parse(date_input)
            except Exception as e:
                logger.error(f"Could not parse date: {date_input} ({e})")
                raise ValueError(f"time data '{date_input}' does not match any expected format")
    # Convert to target timezone and remove tzinfo
    date = date.astimezone(TARGET_TIMEZONE).replace(tzinfo=None)
    return date

def calculate_days_excluding_weekends(start_date, end_date):
    """
    Calculate the number of hours (as a decimal) between two dates, excluding weekends.
    - Accepts strings or datetime objects.
    - Returns 0 if start_date > end_date.
    - Excludes ALL hours on weekends (Saturday and Sunday).
    """
    # Ensure start_date and end_date are datetime objects
    if isinstance(start_date, str):
        start_date = parse_date(start_date)
    if isinstance(end_date, str):
        end_date = parse_date(end_date)

    if start_date > end_date:
        logger.debug("During calculation of difference between dates - Start date is after end date. Returning 0.")
        return 0.0  # Avoid negative or erroneous calculations

    total_hours = 0.0
    current_date = start_date

    while current_date < end_date:
        # If it's a weekday (Monday=0 to Friday=4)
        if current_date.weekday() < 5:
            # Calculate the end of the current day or the end_date, whichever is earlier
            next_day = (current_date + timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)
            period_end = min(next_day, end_date)
            hours = (period_end - current_date).total_seconds() / 3600
            total_hours += hours
            
        # Move to the start of the next day (whether weekday or weekend)
        current_date = (current_date + timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)

    return round(total_hours, 2)