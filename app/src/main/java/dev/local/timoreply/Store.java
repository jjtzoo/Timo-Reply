package dev.local.timoreply;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONObject;
import java.util.Iterator;

final class Store {
    static final String TARGET = "com.hwsj.club";
    static final String DEFAULT_REPLY = "Thanks for your message! I'm away right now and will reply when I'm back.";
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("timo_reply", Context.MODE_PRIVATE); }
    static void status(Context c, String value) {
        prefs(c).edit().putString("status", value).putLong("status_at", System.currentTimeMillis()).apply();
        Log.i("TimoReply", value);
    }
    static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : digest) b.append(String.format(java.util.Locale.ROOT, "%02x", x & 255));
            return b.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static JSONObject map(Context c, String name) {
        try { return new JSONObject(prefs(c).getString(name, "{}")); }
        catch (org.json.JSONException e) { return new JSONObject(); }
    }
    static void remember(Context c, String name, String key, long now) {
        JSONObject data = map(c, name);
        try {
            Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (now - data.optLong(k) > 24 * 60 * 60 * 1000L) keys.remove();
            }
            while (data.length() >= 256) {
                String oldest = null;
                long time = Long.MAX_VALUE;
                keys = data.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    if (data.optLong(k) < time) { oldest = k; time = data.optLong(k); }
                }
                if (oldest == null) break;
                data.remove(oldest);
            }
            data.put(key, now);
            // Persist the reservation before dispatching, including across process restarts.
            if (!prefs(c).edit().putString(name, data.toString()).commit())
                throw new IllegalStateException("Could not persist reply reservation");
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
    }
    private Store() { }
}
