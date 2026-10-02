package dev.local.timoreply;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ReplyPolicyTest {
    private static final long NOW = 2_000_000L;
    private String check(boolean enabled, boolean reply, boolean summary, boolean ongoing,
            boolean group, boolean outgoing, boolean message, boolean seen,
            long armed, long posted, long messageAt, long threadAt, long globalAt) {
        return ReplyPolicy.skipReason(enabled, true, reply, summary, ongoing, group, outgoing, message,
                seen, armed, posted, messageAt, NOW, threadAt, globalAt);
    }
    @Test public void freshMessageCanReply() {
        assertNull(check(true,true,false,false,false,false,true,false,NOW-10_000,NOW-1000,NOW-1000,0,0));
    }
    @Test public void defaultPreviewNeverReplies() {
        assertNotNull(check(false,true,false,false,false,false,true,false,NOW-10_000,NOW-1000,NOW-1000,0,0));
    }
    @Test public void oldMessageRepostedAsNewIsRejected() {
        assertNotNull(check(true,true,false,false,false,false,true,false,NOW-10_000,NOW-1000,NOW-20_000,0,0));
    }
    @Test public void previouslyObservedMessageNeverReplies() {
        assertNotNull(check(true,true,false,false,false,false,true,true,NOW-10_000,NOW-1000,NOW-1000,0,0));
    }
    @Test public void missingReplyAndGroupsAndOutgoingAreRejected() {
        assertNotNull(check(true,false,false,false,false,false,true,false,NOW-10_000,NOW-1000,NOW-1000,0,0));
        assertNotNull(check(true,true,false,false,true,false,true,false,NOW-10_000,NOW-1000,NOW-1000,0,0));
        assertNotNull(check(true,true,false,false,false,true,true,false,NOW-10_000,NOW-1000,NOW-1000,0,0));
    }
    @Test public void cooldownsSurviveFreshMessages() {
        assertNotNull(check(true,true,false,false,false,false,true,false,NOW-10_000,NOW-1000,NOW-1000,NOW-599_999,0));
        assertNotNull(check(true,true,false,false,false,false,true,false,NOW-10_000,NOW-1000,NOW-1000,0,NOW-29_999));
        assertNull(check(true,true,false,false,false,false,true,false,NOW-10_000,NOW-1000,NOW-1000,NOW-600_000,NOW-30_000));
    }
    @Test public void summariesOngoingAndEmptyAreRejected() {
        assertNotNull(check(true,true,true,false,false,false,true,false,NOW-10_000,NOW-1000,NOW-1000,0,0));
        assertNotNull(check(true,true,false,true,false,false,true,false,NOW-10_000,NOW-1000,NOW-1000,0,0));
        assertNotNull(check(true,true,false,false,false,false,false,false,NOW-10_000,NOW-1000,NOW-1000,0,0));
    }
    @Test public void warmupAndFutureTimestampsAreRejected() {
        assertNotNull(check(true,true,false,false,false,false,true,false,NOW-4000,NOW-1000,NOW-1000,0,0));
        assertNotNull(check(true,true,false,false,false,false,true,false,NOW-10_000,NOW+10_000,NOW+10_000,0,0));
    }
    @Test public void queuedCallbackCannotSendAfterDisconnect() {
        assertNotNull(ReplyPolicy.skipReason(true, false, true, false, false, false, false,
                true, false, NOW-10_000, NOW-1000, NOW-1000, NOW, 0, 0));
    }
    @Test public void missingMessageTimestampCannotProveFreshness() {
        assertNotNull(check(true,true,false,false,false,false,true,false,NOW-10_000,NOW-1000,0,0,0));
    }
}
