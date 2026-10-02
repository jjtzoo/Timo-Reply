package dev.local.timoreply;

import org.junit.Test;
import static org.junit.Assert.*;

public final class UnreadPolicyTest {
    @Test public void directUnreadChatsAreEligibleWithoutNotificationSupport() {
        assertTrue(UnreadPolicy.eligible("Mark", "1", false, false));
        assertTrue(UnreadPolicy.eligible("A friend", "99+", false, false));
    }
    @Test public void readRowsGroupsAndSpecialAccountsAreSkipped() {
        assertFalse(UnreadPolicy.eligible("Mark", "0", false, false));
        assertFalse(UnreadPolicy.eligible("Mark", null, false, false));
        assertFalse(UnreadPolicy.eligible("Official Assistant", "1", false, false));
        assertFalse(UnreadPolicy.eligible("MENTOR GEMELLA ANN", "7", false, false));
        assertFalse(UnreadPolicy.eligible("A private group", "2", true, false));
    }
    @Test public void interruptedOrPreviouslyRepliedChatIsNotRetried() {
        assertFalse(UnreadPolicy.eligible("Mark", "1", false, true));
    }
    @Test public void changedRecipientOrDraftStopsSending() {
        String reply = "Hey you 😊 nice to hear from you.";
        assertTrue(UnreadPolicy.canSend("Mark", "Mark", reply, reply, true, true));
        assertFalse(UnreadPolicy.canSend("Mark", "Someone else", reply, reply, true, true));
        assertFalse(UnreadPolicy.canSend("Mark", "Mark", reply, "An existing draft", true, true));
        assertFalse(UnreadPolicy.canSend("Mark", "Mark", reply, reply, false, true));
        assertFalse(UnreadPolicy.canSend("Mark", "Mark", reply, reply, true, false));
    }
}
