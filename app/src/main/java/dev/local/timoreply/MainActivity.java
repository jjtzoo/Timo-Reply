package dev.local.timoreply;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.Date;

public final class MainActivity extends Activity {
    private TextView status;
    private Switch enabled;
    private EditText replyText;
    private TextView unreadStatus;
    private boolean refreshing;
    private final Handler handler = new Handler();
    private final Runnable refresh = new Runnable() {
        @Override public void run() { update(); handler.postDelayed(this, 1500); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(245, 247, 244));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content);
        // Android 15+ draws behind system bars; keep the form clear of them.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        text(content, "Timo Reply", 30);
        text(content, "Reply to unread chats first, then handle new notifications.", 16);
        text(content, "Reply message", 18);
        replyText = new EditText(this);
        replyText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        replyText.setMinLines(2);
        replyText.setText(Store.prefs(this).getString("reply", Store.DEFAULT_REPLY));
        replyText.setContentDescription("Automatic reply message");
        content.addView(replyText);
        Button save = button(content, "Save reply");
        save.setOnClickListener(v -> {
            if (saveReply()) Toast.makeText(this, "Reply saved", Toast.LENGTH_SHORT).show();
        });
        text(content, "Unread chats", 20);
        Button unreadAccess = button(content, "Enable unread access");
        unreadAccess.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        Button unreadStart = button(content, "Reply unread chats, then auto-reply");
        unreadStart.setOnClickListener(v -> {
            if (!saveReply()) return;
            if (!UnreadService.available()) {
                Toast.makeText(this, "Enable unread access first.", Toast.LENGTH_LONG).show(); return;
            }
            if (!UnreadService.start(this)) Toast.makeText(this, "Already running, or Timo could not open.", Toast.LENGTH_LONG).show();
        });
        Button unreadStop = button(content, "Stop unread replies");
        unreadStop.setOnClickListener(v -> { UnreadService.stop(); update(); });
        text(content, "Keep Timo visible and the phone unlocked. Only unread direct chats showing ‘Reply to messages to earn diamonds’ get a reply. When finished, Timo moves to the background and notification auto-replies turn on.", 14);
        unreadStatus = text(content, "Ready when you are.", 15);
        text(content, "New message notifications", 20);
        Button access = button(content, "Enable notification access");
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        enabled = new Switch(this);
        enabled.setText("Auto-send replies");
        content.addView(enabled);
        enabled.setOnCheckedChangeListener((view, checked) -> {
            if (refreshing) return;
            SharedPreferences p = Store.prefs(this);
            if (checked && (!Store.hasAccess(this) || !p.getBoolean("connected", false) || UnreadService.running())) {
                Toast.makeText(this, "Connect notification access and finish the unread pass first.", Toast.LENGTH_LONG).show();
                update(); return;
            }
            if (checked && !saveReply()) { update(); return; }
            p.edit().putBoolean("enabled", checked).putLong("armed_at", checked ? System.currentTimeMillis() : 0).apply();
            Store.status(this, checked ? "Auto-send enabled. Wait 5 seconds, then receive a new message."
                    : "Auto-send off. Preview mode.");
            update();
        });
        text(content, "At most one reply per conversation every 10 minutes. Group conversations are skipped when identified. No messages are sent during preview.", 14);
        text(content, "Notification replies resume after Android reconnects. Old notifications are skipped; use the unread pass for the inbox.", 14);
        text(content, "Latest diagnostic", 18);
        status = text(content, "Waiting for a Timo notification.", 15);
        status.setTextIsSelectable(true);
        text(content, "No cloud connection. Message text is used locally; diagnostics contain action labels and decisions, not message bodies.", 14);
        setContentView(scroll);
    }

    private boolean saveReply() {
        String value = replyText.getText().toString().trim();
        if (value.isEmpty() || value.length() > 500) {
            replyText.setError("Enter a reply from 1 to 500 characters."); return false;
        }
        Store.prefs(this).edit().putString("reply", value).apply(); return true;
    }

    private void update() {
        SharedPreferences p = Store.prefs(this);
        boolean access = Store.hasAccess(this);
        if (!access) p.edit().putBoolean("enabled", false).putBoolean("connected", false).apply();
        refreshing = true;
        enabled.setChecked(access && p.getBoolean("enabled", false));
        refreshing = false;
        long at = p.getLong("status_at", 0);
        String mode = !p.getBoolean("enabled", false) ? "Preview" : p.getBoolean("connected", false) ? "Auto-send ON" : "Auto-send PAUSED";
        status.setText("Mode: " + mode + "\nNotification access: " + (access ? "ON" : "OFF") + "\n"
                + "Listener: " + (p.getBoolean("connected", false) ? "Connected" : "Disconnected") + "\n"
                + (at > 0 ? DateFormat.getTimeInstance().format(new Date(at)) + "\n" : "")
                + p.getString("status", "Waiting for a Timo notification."));
        unreadStatus.setText("Unread access: " + (UnreadService.available() ? "ON" : "OFF") + "\n"
                + p.getString("unread_status", "Ready when you are."));
    }

    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }

    private TextView text(LinearLayout parent, String value, int size) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(Color.rgb(26, 45, 38));
        view.setPadding(0, 12, 0, 12); parent.addView(view); return view;
    }
    private Button button(LinearLayout parent, String value) {
        Button b = new Button(this); b.setText(value); parent.addView(b); return b;
    }
}
