package tony26.bountiesPlus.utils;

/**
 * Utility class for formatting time values consistently across the plugin
 */
public class TimeFormatter {

    /**
     * Formats time in seconds to a short format (e.g., "5s", "2m", "1h")
     * Used for displaying time ago or remaining time in compact format
     */
    public static String formatTimeAgo(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m";
        } else {
            long hours = seconds / 3600;
            return hours + "h";
        }
    }

    /**
     * Formats time in seconds to a detailed format with seconds included
     * Used for countdowns and remaining time displays
     */
    public static String formatTimeRemaining(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
    }

    /**
     * Formats time in minutes to a human-readable format
     * Used for bounty duration settings (handles minutes, hours, days)
     */
    public static String formatMinutesToReadable(int timeMinutes, boolean permanent) {
        if (timeMinutes == 0 || permanent) {
            return "Permanent";
        } else if (timeMinutes < 60) {
            return timeMinutes + " minute" + (timeMinutes != 1 ? "s" : "");
        } else if (timeMinutes < 1440) {
            int hours = timeMinutes / 60;
            int remainingMinutes = timeMinutes % 60;
            if (remainingMinutes == 0) {
                return hours + " hour" + (hours > 1 ? "s" : "");
            } else {
                return hours + " hour" + (hours > 1 ? "s" : "") + " " + remainingMinutes + " minute" + (remainingMinutes > 1 ? "s" : "");
            }
        } else {
            int days = timeMinutes / 1440;
            int remainingHours = (timeMinutes % 1440) / 60;
            int remainingMinutes = timeMinutes % 60;

            StringBuilder result = new StringBuilder();
            result.append(days).append(" day").append(days > 1 ? "s" : "");

            if (remainingHours > 0) {
                result.append(" ").append(remainingHours).append(" hour").append(remainingHours > 1 ? "s" : "");
            }
            if (remainingMinutes > 0) {
                result.append(" ").append(remainingMinutes).append(" minute").append(remainingMinutes > 1 ? "s" : "");
            }

            return result.toString();
        }
    }

    /**
     * Formats a timestamp difference from current time to "time ago" format
     */
    public static String formatTimestampToAgo(long timestamp) {
        long currentTime = System.currentTimeMillis() / 1000;
        long timeDiff = currentTime - timestamp;
        return formatTimeAgo(timeDiff);
    }
}