package dev.local.timoreply;

import java.util.Locale;

/** Checks shared by inbox inspection and every stage before sending. */
public final class UnreadPolicy {
    public static boolean eligible(String name, String badge, boolean specialRow, boolean reserved) {
        if (name == null || name.trim().isEmpty() || specialRow || reserved) return false;
        if (badge == null || !badge.matches("[1-9][0-9]*\\+?")) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return !lower.matches(".*\\b(assistant|mentor|tribe|family|system|official)\\b.*");
    }

    public static boolean canSend(String expectedName, String actualName, String reply,
            String draft, boolean directChat, boolean sendEnabled) {
        return expectedName != null && !expectedName.isEmpty() && expectedName.equals(actualName)
                && reply != null && !reply.trim().isEmpty() && reply.equals(draft)
                && directChat && sendEnabled;
    }

    private UnreadPolicy() { }
}
