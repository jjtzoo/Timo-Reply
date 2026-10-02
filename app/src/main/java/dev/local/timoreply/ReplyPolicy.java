package dev.local.timoreply;

/** Pure decision logic, independent of Android notification delivery. */
public final class ReplyPolicy {
    public static final long COOLDOWN_MS = 10 * 60 * 1000L;
    public static final long GLOBAL_GAP_MS = 30 * 1000L;
    public static final long MAX_AGE_MS = 60 * 1000L;
    public static final long WARMUP_MS = 5 * 1000L;

    public static String skipReason(boolean enabled, boolean connected, boolean replyAvailable, boolean summary,
            boolean ongoing, boolean group, boolean outgoing, boolean hasMessage,
            boolean seen, long armedAt, long postedAt, long messageAt,
            long now, long lastThreadReply, long lastGlobalReply) {
        if (!enabled) return "Preview only: auto-send is off.";
        if (!connected) return "Auto-send paused: notification listener disconnected.";
        if (!replyAvailable) return "No usable notification reply action.";
        if (summary || ongoing) return "Skipped summary or ongoing notification.";
        if (group) return "Skipped group conversation.";
        if (outgoing) return "Skipped your own message.";
        if (!hasMessage) return "No readable message or conversation identity.";
        if (seen) return "Skipped previously observed message.";
        if (armedAt <= 0 || now < armedAt + WARMUP_MS) return "Waiting for a new message after enabling.";
        if (postedAt <= armedAt || postedAt > now + 5000L || now - postedAt > MAX_AGE_MS)
            return "Skipped old notification.";
        if (messageAt <= 0) return "Skipped message with unknown age.";
        if (messageAt <= armedAt || messageAt > now + 5000L || now - messageAt > MAX_AGE_MS)
            return "Skipped old message.";
        if (lastThreadReply > 0 && now - lastThreadReply < COOLDOWN_MS)
            return "Conversation cooldown: 10 minutes.";
        if (lastGlobalReply > 0 && now - lastGlobalReply < GLOBAL_GAP_MS)
            return "Global cooldown: 30 seconds.";
        return null;
    }

    private ReplyPolicy() { }
}
