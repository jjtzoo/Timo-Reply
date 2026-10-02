package dev.local.timoreply;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.List;
import java.util.ArrayList;

public final class TimoListener extends NotificationListenerService {
    @Override public void onListenerConnected() {
        // Keep the user's opt-in, but establish a fresh session before inspecting the backlog.
        SharedPreferences p = Store.prefs(this);
        boolean enabled = Store.hasAccess(this) && p.getBoolean("enabled", false);
        p.edit().putBoolean("enabled", enabled).putBoolean("connected", true)
                .putBoolean("reply_supported", false)
                .putLong("armed_at", enabled ? System.currentTimeMillis() : 0).apply();
        Store.status(this, enabled ? "Auto-send resumed. Waiting for a fresh message after reconnect."
                : "Notification access connected. Preview mode; auto-send is off.");
        StatusBarNotification[] active = getActiveNotifications();
        if (active != null) for (StatusBarNotification s : active) inspect(s, true);
    }

    @Override public void onListenerDisconnected() {
        boolean access = Store.hasAccess(this);
        SharedPreferences p = Store.prefs(this);
        SharedPreferences.Editor change = p.edit().putBoolean("connected", false);
        if (!access) change.putBoolean("enabled", false).putLong("armed_at", 0).putBoolean("reply_supported", false);
        change.apply();
        Store.status(this, !access ? "Notification access removed. Auto-send is off."
                : p.getBoolean("enabled", false) ? "Auto-send paused; it will resume after Android reconnects."
                : "Notification access disconnected. Preview mode.");
    }

    @Override public void onNotificationPosted(StatusBarNotification s) { inspect(s, false); }

    @Override public void onDestroy() {
        Store.prefs(this).edit().putBoolean("connected", false).apply();
        super.onDestroy();
    }

    private void inspect(StatusBarNotification s, boolean baseline) {
        if (s == null || !Store.TARGET.equals(s.getPackageName())) return;
        try { inspectTimo(s, baseline); }
        catch (RuntimeException e) {
            Store.prefs(this).edit().putBoolean("enabled", false).apply();
            Store.status(this, "Auto-send stopped: " + e.getClass().getSimpleName());
        }
    }

    private void inspectTimo(StatusBarNotification s, boolean baseline) {
        long now = System.currentTimeMillis();
        Notification n = s.getNotification();
        SharedPreferences p = Store.prefs(this);
        ArrayList<Notification.Action> actions = new ArrayList<>();
        if (n.actions != null) java.util.Collections.addAll(actions, n.actions);
        actions.addAll(new Notification.WearableExtender(n).getActions());
        Notification.Action reply = null;
        Notification.Action fallback = null;
        int fallbackCount = 0;
        java.util.HashSet<PendingIntent> explicitIntents = new java.util.HashSet<>();
        java.util.HashSet<PendingIntent> fallbackIntents = new java.util.HashSet<>();
        StringBuilder labels = new StringBuilder();
        for (Notification.Action a : actions) {
            if (labels.length() > 0) labels.append(", ");
            labels.append(a.title == null ? "unnamed" : a.title);
            if (a.actionIntent == null || a.getRemoteInputs() == null) continue;
            if (Build.VERSION.SDK_INT >= 31 && a.isAuthenticationRequired()) continue;
            int semantic = Build.VERSION.SDK_INT >= 28 ? a.getSemanticAction() : 0;
            if (semantic != 0 && semantic != Notification.Action.SEMANTIC_ACTION_REPLY) continue;
            boolean acceptsText = false;
            for (RemoteInput input : a.getRemoteInputs()) {
                if (input.getAllowFreeFormInput()) { acceptsText = true; break; }
            }
            if (!acceptsText) continue;
            if (semantic == Notification.Action.SEMANTIC_ACTION_REPLY) {
                explicitIntents.add(a.actionIntent); reply = a;
            }
            else {
                // Wearable/direct actions can duplicate the same PendingIntent.
                if (fallbackIntents.add(a.actionIntent)) {
                    fallback = a; fallbackCount++;
                }
            }
        }
        if (explicitIntents.size() > 1) reply = null;
        else if (reply == null && fallbackCount == 1) reply = fallback;
        String title = string(n.extras.getCharSequence(Notification.EXTRA_TITLE));
        String body = string(n.extras.getCharSequence(Notification.EXTRA_TEXT));
        String conversation = string(n.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        long messageAt = n.when;
        boolean group = n.extras.getBoolean("android.isGroupConversation", false);
        boolean outgoing = false;
        android.os.Parcelable[] messageBundles = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messageBundles != null) {
            List<Notification.MessagingStyle.Message> messages;
            if (Build.VERSION.SDK_INT >= 30) {
                messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(messageBundles);
            } else if (Build.VERSION.SDK_INT >= 28) {
                Notification.Style recovered = Notification.Builder.recoverBuilder(this, n).getStyle();
                messages = recovered instanceof Notification.MessagingStyle
                        ? ((Notification.MessagingStyle) recovered).getMessages() : java.util.Collections.emptyList();
            } else {
                // Older Android versions have no public style recovery API; do not guess the sender.
                messages = java.util.Collections.emptyList();
            }
            if (!messages.isEmpty()) {
                Notification.MessagingStyle.Message latest = messages.get(messages.size() - 1);
                body = string(latest.getText());
                messageAt = latest.getTimestamp();
                // In MessagingStyle, a null sender represents the current user.
                if (Build.VERSION.SDK_INT >= 28) {
                    android.app.Person sender = latest.getSenderPerson();
                    outgoing = sender == null;
                } else { outgoing = latest.getSender() == null; }
            } else {
                body = "";
                messageAt = 0;
            }
        }
        boolean summary = (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        boolean ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        String shortcut = n.getShortcutId();
        String identity = shortcut != null && !shortcut.isEmpty() ? shortcut : conversation.isEmpty() ? title : conversation;
        String thread = Store.hash(identity);
        // Do not include postTime: notification updates can repost the same message.
        String fingerprint = Store.hash(thread + "\n" + body + "\n" + messageAt);
        boolean seen = Store.map(this, "seen").has(fingerprint);
        boolean hasMessage = !body.trim().isEmpty() && !identity.trim().isEmpty();
        SharedPreferences.Editor observed = p.edit().putBoolean("timo_seen", true);
        // A later summary without a reply action must not erase a successful support observation.
        if (reply != null) observed.putBoolean("reply_supported", true);
        observed.apply();
        String metadata = "Timo detected. Reply action: " + (reply != null ? "YES" : "NO")
                + ". Actions: " + (labels.length() == 0 ? "none" : labels) + ". ";
        String reason = baseline ? "Existing notification inspected; no reply sent."
                : ReplyPolicy.skipReason(p.getBoolean("enabled", false), p.getBoolean("connected", false), reply != null, summary,
                    ongoing, group, outgoing, hasMessage, seen, p.getLong("armed_at", 0),
                    s.getPostTime(), messageAt, now, Store.map(this, "threads").optLong(thread),
                    p.getLong("global_reply_at", 0));
        // Every observation is consumed, even in preview or cooldown, so updates cannot trigger a late reply.
        Store.remember(this, "seen", fingerprint, now);
        if (reason != null) { Store.status(this, metadata + reason); return; }
        String text = p.getString("reply", Store.DEFAULT_REPLY).trim();
        if (text.isEmpty()) { Store.status(this, metadata + "Reply text is empty."); return; }
        Store.remember(this, "threads", thread, now);
        if (!p.edit().putLong("global_reply_at", now).commit())
            throw new IllegalStateException("Could not save global cooldown");
        Bundle results = new Bundle();
        for (RemoteInput input : reply.getRemoteInputs())
            if (input.getAllowFreeFormInput()) results.putCharSequence(input.getResultKey(), text);
        Intent fillIn = new Intent();
        RemoteInput.addResultsToIntent(reply.getRemoteInputs(), fillIn, results);
        if (Build.VERSION.SDK_INT >= 28) RemoteInput.setResultsSource(fillIn, RemoteInput.SOURCE_FREE_FORM_INPUT);
        try {
            reply.actionIntent.send(this, 0, fillIn);
            Store.status(this, metadata + "Reply dispatched; check the Timo chat to confirm delivery.");
        } catch (PendingIntent.CanceledException | SecurityException e) {
            Store.status(this, metadata + "Reply dispatch failed: " + e.getClass().getSimpleName());
        }
    }

    private static String string(CharSequence value) { return value == null ? "" : value.toString(); }
}
