package dev.local.timoreply;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** User-started foreground inbox pass; enabling accessibility alone never sends. */
public final class UnreadService extends AccessibilityService {
    private static UnreadService instance;
    private static final String PREFIX = Store.TARGET + ":id/";
    private enum Stage { INBOX, SELECT_UNREAD, SCAN, OPEN, COMPOSE, SEND, VERIFY, RETURN }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> pages = new HashSet<>();
    private final Set<String> visited = new HashSet<>();
    private Stage stage;
    private long stageAt;
    private long diagnosticAt;
    private int sent;
    private int skipped;
    private int emptyChecks;
    private String expectedName;
    private String reply;
    private final Runnable step = this::tick;

    static boolean available() { return instance != null; }
    static boolean running() { return instance != null && instance.stage != null; }
    static boolean start(Context context) {
        if (instance == null || running()) return false;
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(Store.TARGET);
        if (launch == null) return false;
        instance.begin();
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { context.startActivity(launch); }
        catch (RuntimeException e) { instance.finish("Could not open Timo."); return false; }
        return true;
    }
    static void stop() {
        if (instance != null) instance.finish("Stopped by you.");
    }

    @Override protected void onServiceConnected() {
        instance = this;
        Store.prefs(this).edit().putBoolean("unread_running", false).apply();
        status("Unread access ready. Tap Reply unread chats to start.");
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { /* Poll fresh nodes between actions. */ }
    @Override public void onInterrupt() { finish("Accessibility interrupted. No automatic retry."); }
    @Override public void onDestroy() {
        if (stage != null) finish("Unread session disconnected. No automatic retry.");
        handler.removeCallbacks(step);
        if (instance == this) instance = null;
        super.onDestroy();
    }

    private void begin() {
        handler.removeCallbacks(step);
        pages.clear(); visited.clear(); sent = 0; skipped = 0; emptyChecks = 0;
        reply = Store.prefs(this).getString("reply", Store.DEFAULT_REPLY).trim();
        // Do not let notification replies run alongside the foreground inbox pass.
        Store.prefs(this).edit().putBoolean("enabled", false).putLong("armed_at", 0)
                .putBoolean("unread_running", true).putInt("unread_sent", 0).apply();
        setStage(Stage.INBOX);
        status("Opening Timo. Keep the phone unlocked and Timo visible.");
        handler.postDelayed(step, 1200);
    }
    private void setStage(Stage next) { stage = next; stageAt = SystemClock.elapsedRealtime(); }
    private void status(String message) {
        String value = "Replies shown in Timo: " + sent + ". " + message;
        Store.prefs(this).edit().putString("unread_status", value).putInt("unread_sent", sent).apply();
        Log.i("TimoUnread", value);
    }
    private void finish(String message) {
        stage = null;
        handler.removeCallbacks(step);
        Store.prefs(this).edit().putBoolean("unread_running", false).apply();
        status(message);
    }
    private void complete() {
        finish("Diamond-reward unread pass finished. Switching to notification auto-replies.");
        if (!Store.hasAccess(this)) {
            status("Unread pass finished. Enable notification access to handle new messages."); return;
        }
        Store.prefs(this).edit().putBoolean("enabled", true).putLong("armed_at", System.currentTimeMillis()).apply();
        Store.status(this, "Unread pass finished. Notification auto-replies armed for fresh messages; waiting for a usable Timo reply action.");
        if (!performGlobalAction(GLOBAL_ACTION_HOME)) status("Unread pass finished; press Home so Timo can show new-message notifications.");
    }
    private void tick() {
        if (stage == null) return;
        try {
            if (SystemClock.elapsedRealtime() - stageAt > 20_000) {
                finish("Stopped: expected Timo screen did not appear. Check the last chat; no retry."); return;
            }
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (SystemClock.elapsedRealtime() - diagnosticAt > 5000) {
                diagnosticAt = SystemClock.elapsedRealtime();
                Log.i("TimoUnread", "Stage=" + stage + ", Timo visible=" + (root != null && Store.TARGET.equals(String.valueOf(root.getPackageName())))
                        + ", inbox control=" + (inbox(root) != null)
                        + ", Unread control=" + (root != null && textNode(root, "titleView", "Unread") != null));
            }
            if (root != null && root.getPackageName() != null && Store.TARGET.contentEquals(root.getPackageName())) advance(root);
        } catch (RuntimeException e) {
            finish("Stopped: " + e.getClass().getSimpleName() + ". Check the last chat; no retry.");
        }
        if (stage != null) handler.postDelayed(step, 1200);
    }
    private void advance(AccessibilityNodeInfo root) {
        switch (stage) {
            case INBOX: {
                if (inbox(root) != null && textNode(root, "titleView", "Unread") != null) {
                    setStage(Stage.SELECT_UNREAD); return;
                }
                AccessibilityNodeInfo back = find(root, "ivBack");
                if (back != null) {
                    // An explicit new start can finish the app's own pre-send draft in the open chat.
                    // Never retry anything that may already have reached Send.
                    String name = text(find(root, "tvTitle"));
                    String key = Store.hash(name);
                    long reservedAt = Store.map(this, "unread_reserved").optLong(key);
                    boolean knownPending = key.equals(Store.prefs(this).getString("unread_pending_key", ""));
                    boolean legacyPreSend = reservedAt > 0 && System.currentTimeMillis() - reservedAt < 15 * 60 * 1000L
                            && Store.map(this, "threads").optLong(key) < reservedAt;
                    boolean dispatched = Store.prefs(this).getBoolean("unread_pending_dispatched", false);
                    if (directChat(root) && !name.isEmpty() && (knownPending || legacyPreSend) && !dispatched
                            && UnreadPolicy.sameText(reply, text(find(root, "editContent")))) {
                        expectedName = name; visited.add(key); setStage(Stage.SEND);
                        status("Continuing the bot's previously entered reply."); return;
                    }
                    click(back); return;
                }
                AccessibilityNodeInfo messages = textNode(root, "mTvLabel", "Messages");
                if (messages != null) click(messages);
                return;
            }
            case SELECT_UNREAD: {
                AccessibilityNodeInfo unread = textNode(root, "titleView", "Unread");
                if (unread != null && click(unread)) setStage(Stage.SCAN);
                return;
            }
            case SCAN: scan(root); return;
            case OPEN: {
                if (!recipientMatches(root)) return;
                AccessibilityNodeInfo edit = find(root, "editContent");
                if (edit == null) return;
                if (!directChat(root)) { finish("Stopped: opened chat was not a recognized direct chat."); return; }
                if (!emptyDraft(edit)) { finish("Stopped: this chat already has a draft. Your draft was kept."); return; }
                if (outgoingCount(root) > 0) {
                    skipped++; setStage(Stage.RETURN); return;
                }
                setStage(Stage.COMPOSE); return;
            }
            case COMPOSE: {
                if (!recipientMatches(root) || !directChat(root)) { finish("Stopped: chat changed before composing."); return; }
                AccessibilityNodeInfo edit = find(root, "editContent");
                if (edit == null || !emptyDraft(edit)) { finish("Stopped: a draft appeared before composing."); return; }
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply);
                if (!edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    finish("Stopped: Timo did not allow entering the reply."); return;
                }
                Store.prefs(this).edit().putString("unread_pending_key", Store.hash(expectedName))
                        .putBoolean("unread_pending_dispatched", false).apply();
                setStage(Stage.SEND); return;
            }
            case SEND: {
                AccessibilityNodeInfo title = find(root, "tvTitle");
                AccessibilityNodeInfo edit = find(root, "editContent");
                AccessibilityNodeInfo send = find(root, "ivSend");
                if (!UnreadPolicy.canSend(expectedName, text(title), reply, text(edit), directChat(root),
                        send != null && send.isEnabled() && send.isVisibleToUser())) {
                    finish("Stopped: recipient, reply or Send button changed."); return;
                }
                // Enter VERIFY before dispatch; no repeat tap even if UI delivery is slow.
                long now = System.currentTimeMillis();
                Store.remember(this, "threads", Store.hash(expectedName), now);
                if (!Store.prefs(this).edit().putLong("global_reply_at", now)
                        .putString("unread_pending_key", Store.hash(expectedName))
                        .putBoolean("unread_pending_dispatched", true).commit())
                    throw new IllegalStateException("Could not reserve notification cooldown");
                setStage(Stage.VERIFY);
                if (!click(send)) finish("Stopped: Timo did not accept Send. No retry.");
                return;
            }
            case VERIFY: {
                if (!recipientMatches(root)) { finish("Stopped: chat changed after Send. No retry."); return; }
                if (hasFailure(root)) { finish("Stopped: Timo shows a message failure. No retry."); return; }
                AccessibilityNodeInfo edit = find(root, "editContent");
                if (outgoingCount(root) == 1 && edit != null && emptyDraft(edit)) {
                    sent++;
                    Store.prefs(this).edit().remove("unread_pending_key").remove("unread_pending_dispatched").apply();
                    status("Outgoing reply appeared. Continuing through Unread.");
                    setStage(Stage.RETURN);
                }
                return;
            }
            case RETURN: {
                if (inbox(root) != null) {
                    // Selecting Unread can restore the first page; each pass must scan to the bottom again.
                    pages.clear(); emptyChecks = 0; setStage(Stage.SELECT_UNREAD); return;
                }
                if (!recipientMatches(root)) { finish("Stopped: a different chat is open."); return; }
                AccessibilityNodeInfo back = find(root, "ivBack");
                if (back != null) click(back);
                return;
            }
        }
    }

    private void scan(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo list = inbox(root);
        if (list == null) { finish("Stopped: Unread inbox is no longer visible."); return; }
        Rect listBounds = new Rect(); list.getBoundsInScreen(listBounds);
        StringBuilder page = new StringBuilder();
        List<AccessibilityNodeInfo> rows = root.findAccessibilityNodeInfosByViewId(PREFIX + "bodyView");
        for (AccessibilityNodeInfo row : rows) {
            AccessibilityNodeInfo title = find(row, "title");
            AccessibilityNodeInfo badge = find(row, "tvBadge");
            if (title == null || badge == null || !row.isClickable() || !row.isVisibleToUser()) continue;
            String name = text(title);
            String key = Store.hash(name);
            page.append(key).append(':').append(text(badge)).append(';');
            boolean special = find(row, "myFamilyView") != null || containsText(row, "Tribe");
            long reservedAt = Store.map(this, "unread_reserved").optLong(key);
            boolean reserved = visited.contains(key) || (reservedAt > 0 && System.currentTimeMillis() - reservedAt < 24 * 60 * 60 * 1000L);
            // VIP and other tags have separate nodes; only title is the recipient name.
            if (!UnreadPolicy.eligible(name, text(badge), text(find(row, "desc")), special, reserved)) continue;
            Rect bounds = new Rect(); row.getBoundsInScreen(bounds);
            if (!listBounds.contains(bounds) || bounds.height() < 120) continue;
            // Opening consumes the unread flag. Persist before opening; interrupted chats never retry silently.
            Store.remember(this, "unread_reserved", key, System.currentTimeMillis());
            visited.add(key);
            expectedName = name;
            setStage(Stage.OPEN);
            status("Opening an unread direct chat.");
            if (!click(row)) finish("Stopped: Timo did not open the unread chat.");
            return;
        }
        String signature = page.toString();
        if (signature.isEmpty()) {
            // A freshly selected tab can briefly be empty while it loads.
            if (++emptyChecks < 3) return;
            complete(); return;
        }
        emptyChecks = 0;
        if (!pages.add(signature)) {
            complete(); return;
        }
        // Timo's Unread RecyclerView has no resource ID on the tested phone.
        AccessibilityNodeInfo scroll = inboxRecycler(list, listBounds);
        if (scroll == null) {
            if (page.length() == 0) complete();
            else finish("Stopped: unread list could not be identified for scrolling.");
            return;
        }
        if (!scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) { complete(); return; }
        stageAt = SystemClock.elapsedRealtime();
    }
    private boolean recipientMatches(AccessibilityNodeInfo root) { return expectedName != null && expectedName.equals(text(find(root, "tvTitle"))); }
    private boolean directChat(AccessibilityNodeInfo root) { return find(root, "ivLoverTree") != null && find(root, "editContent") != null; }
    private static boolean emptyDraft(AccessibilityNodeInfo edit) {
        String value = text(edit);
        CharSequence hint = edit.getHintText();
        return value.isEmpty() || value.equals("Say something...") || (hint != null && value.contentEquals(hint));
    }
    private int outgoingCount(AccessibilityNodeInfo root) {
        int count = 0;
        for (AccessibilityNodeInfo content : root.findAccessibilityNodeInfosByViewId(PREFIX + "content")) {
            if (!UnreadPolicy.sameText(reply, text(content))) continue;
            AccessibilityNodeInfo body = content.getParent();
            for (int i = 0; body != null && i < 5 && !(PREFIX + "body").equals(body.getViewIdResourceName()); i++) body = body.getParent();
            if (body == null || !(PREFIX + "body").equals(body.getViewIdResourceName())) continue;
            AccessibilityNodeInfo avatar = find(body, "logo");
            if (avatar == null) continue;
            Rect avatarBounds = new Rect(); avatar.getBoundsInScreen(avatarBounds);
            Rect bodyBounds = new Rect(); body.getBoundsInScreen(bodyBounds);
            if (avatarBounds.left > bodyBounds.centerX()) count++;
        }
        return count;
    }
    private static boolean hasFailure(AccessibilityNodeInfo node) {
        String id = node.getViewIdResourceName();
        if (node.isVisibleToUser() && id != null && (id.contains("Fail") || id.contains("fail"))) return true;
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo child = node.getChild(i); if (child != null && hasFailure(child)) return true; }
        return false;
    }
    private static AccessibilityNodeInfo inboxRecycler(AccessibilityNodeInfo node, Rect listBounds) {
        // ViewPager2 exposes a horizontal RecyclerView around the actual vertical inbox.
        // Prefer the deepest matching list so Scroll cannot change the inbox category.
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo result = inboxRecycler(child, listBounds);
            if (result != null) return result;
        }
        CharSequence cls = node.getClassName();
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        if (node.isVisibleToUser() && cls != null && cls.toString().endsWith("RecyclerView")
                && bounds.top >= listBounds.top && bounds.bottom <= listBounds.bottom + 2
                && bounds.width() >= listBounds.width() * 0.8 && bounds.height() >= listBounds.height() * 0.5)
            return node;
        return null;
    }
    private static AccessibilityNodeInfo inbox(AccessibilityNodeInfo root) {
        if (root == null) return null;
        AccessibilityNodeInfo unread = textNode(root, "titleView", "Unread");
        AccessibilityNodeInfo tabs = find(root, "tabbar");
        if (unread == null || tabs == null) return null;
        AccessibilityNodeInfo named = find(root, "conversationListContainer");
        if (named != null) return named;
        Rect anchor = new Rect(); unread.getBoundsInScreen(anchor);
        Rect bottom = new Rect(); tabs.getBoundsInScreen(bottom);
        Rect screen = new Rect(); root.getBoundsInScreen(screen);
        Rect region = new Rect(screen.left, anchor.bottom, screen.right, bottom.top);
        return inboxRecycler(root, region);
    }
    private static AccessibilityNodeInfo find(AccessibilityNodeInfo root, String id) {
        if (root == null) return null;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(PREFIX + id);
        for (AccessibilityNodeInfo node : nodes) if (node.isVisibleToUser()) return node;
        return null;
    }
    private static AccessibilityNodeInfo textNode(AccessibilityNodeInfo root, String id, String value) {
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(PREFIX + id))
            if (node.isVisibleToUser() && value.equals(text(node))) return node;
        return null;
    }
    private static boolean containsText(AccessibilityNodeInfo root, String value) {
        if (value.equals(text(root))) return true;
        for (int i = 0; i < root.getChildCount(); i++) { AccessibilityNodeInfo child = root.getChild(i); if (child != null && containsText(child, value)) return true; }
        return false;
    }
    private static String text(AccessibilityNodeInfo node) { return node == null || node.getText() == null ? "" : node.getText().toString(); }
    private static boolean click(AccessibilityNodeInfo node) {
        for (int i = 0; node != null && i < 5; i++, node = node.getParent())
            if (node.isEnabled() && node.isClickable() && node.isVisibleToUser()) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        return false;
    }
}
