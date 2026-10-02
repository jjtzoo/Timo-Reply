package dev.local.timoreply;

import org.junit.Test;
import static org.junit.Assert.*;

public final class UnreadPolicyTest {
    private static final String PROMPT = "Reply to messages to earn diamonds";
    @Test public void directUnreadChatsAreEligibleWithoutNotificationSupport() {
        assertTrue(UnreadPolicy.eligible("Mark", "1", PROMPT, false, false));
        assertTrue(UnreadPolicy.eligible("A friend", "99+", PROMPT, false, false));
    }
    @Test public void readRowsGroupsAndSpecialAccountsAreSkipped() {
        assertFalse(UnreadPolicy.eligible("Mark", "0", PROMPT, false, false));
        assertFalse(UnreadPolicy.eligible("Mark", null, PROMPT, false, false));
        assertFalse(UnreadPolicy.eligible("Official Assistant", "1", PROMPT, false, false));
        assertFalse(UnreadPolicy.eligible("MENTOR GEMELLA ANN", "7", PROMPT, false, false));
        assertFalse(UnreadPolicy.eligible("A private group", "2", PROMPT, true, false));
    }
    @Test public void interruptedOrPreviouslyRepliedChatIsNotRetried() {
        assertFalse(UnreadPolicy.eligible("Mark", "1", PROMPT, false, true));
    }
    @Test public void onlyDiamondReplyInvitationsQualify() {
        assertFalse(UnreadPolicy.eligible("Mark", "1", "Hi", false, false));
        assertFalse(UnreadPolicy.eligible("Mark", "1", null, false, false));
        assertFalse(UnreadPolicy.eligible("Mark", "1", "Click to receive your graduation certificate", false, false));
        assertTrue(UnreadPolicy.eligible("Mark", "1", " Reply to messages  to earn diamonds ", false, false));
    }
    @Test public void changedRecipientOrDraftStopsSending() {
        String reply = "Hey you 😊 nice to hear from you.";
        assertTrue(UnreadPolicy.canSend("Mark", "Mark", reply, reply, true, true));
        assertFalse(UnreadPolicy.canSend("Mark", "Someone else", reply, reply, true, true));
        assertFalse(UnreadPolicy.canSend("Mark", "Mark", reply, "An existing draft", true, true));
        assertFalse(UnreadPolicy.canSend("Mark", "Mark", reply, reply, false, true));
        assertFalse(UnreadPolicy.canSend("Mark", "Mark", reply, reply, true, false));
    }
    @Test public void knownTimoEmojiFormatIsEquivalentButOtherChangesAreRejected() {
        String reply = "Hey you 😊 nice to hear from you.";
        assertTrue(UnreadPolicy.canSend("Mark", "Mark", reply, "Hey you [blush] nice to hear from you.", true, true));
        assertFalse(UnreadPolicy.canSend("Mark", "Mark", reply, "Hey you [heart] nice to hear from you.", true, true));
        assertFalse(UnreadPolicy.canSend("Mark", "Mark", reply, "Hey you [blush] nice to hear from you. Send money", true, true));
    }
}
