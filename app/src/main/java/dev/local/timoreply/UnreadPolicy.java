package dev.local.timoreply;

import java.util.Locale;

/** Checks shared by inbox inspection and every stage before sending. */
public final class UnreadPolicy {
    public static boolean eligible(String name, String badge, String invitation, boolean specialRow, boolean reserved) {
        if (name == null || name.trim().isEmpty() || specialRow || reserved) return false;
        if (badge == null || !badge.matches("[1-9][0-9]*\\+?")) return false;
        if (invitation == null || !invitation.trim().replaceAll("\\s+", " ")
                .equalsIgnoreCase("Reply to messages to earn diamonds")) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return !lower.matches(".*\\b(assistant|mentor|tribe|family|system|official)\\b.*");
    }

    public static boolean canSend(String expectedName, String actualName, String reply,
            String draft, boolean directChat, boolean sendEnabled) {
        return expectedName != null && !expectedName.isEmpty() && expectedName.equals(actualName)
                && reply != null && !reply.trim().isEmpty() && sameText(reply, draft)
                && directChat && sendEnabled;
    }
    public static boolean sameText(String expected, String actual) {
        return expected != null && actual != null && timoText(expected).equals(timoText(actual));
    }
    public static String timoText(String value) {
        // Timo exposes this emoji as a shortcode in its composer and message bubbles.
        // Do not ignore arbitrary emoji/text changes; only this observed mapping is accepted.
        return value.replace("\uD83D\uDE0A", "[blush]");
    }

    private UnreadPolicy() { }
}
