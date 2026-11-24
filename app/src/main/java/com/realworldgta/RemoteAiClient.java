package com.realworldgta;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.MediaType;
import okio.BufferedSource;

/**
 * Streams chat completions from the configured HTTPS Soulmate AI gateway.
 * Conversation history remains on-device; only the active prompt and bounded
 * recent context are sent to the configured server for inference.
 */
public final class RemoteAiClient {
    public interface Listener {
        void onStatus(String status, String detail);
        void onToken(String token);
        void onComplete();
        void onError(String message);
    }

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int MAX_HISTORY_MESSAGES = 12;
    private static final int MAX_MESSAGE_CHARS = 4000;
    private static final int MAX_HISTORY_CHARS = 5000;

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build();
    private final List<JSONObject> history = new ArrayList<>();
    private final AtomicBoolean generating = new AtomicBoolean(false);
    private volatile String baseUrl = "";
    private volatile String systemInstruction = "";\n    private volatile String activeConversationId = "";
    private volatile Call activeCall;

    public RemoteAiClient(Listener listener) {
        this.listener = listener;
    }

    public boolean setBaseUrl(String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isEmpty()) {
            baseUrl = "";
            postStatus("offline", "Add your HTTPS AI server address in Settings.");
            return true;
        }
        HttpUrl parsed = HttpUrl.parse(candidate);
        if (parsed == null || !parsed.isHttps() || parsed.host().isEmpty()
                || parsed.username().length() > 0 || parsed.password().length() > 0
                || parsed.query() != null || parsed.fragment() != null) {
            postError("Use a valid HTTPS server address, for example https://api.example.com");
            return false;
        }
        String normalized = candidate.endsWith("/")
                ? candidate.substring(0, candidate.length() - 1) : candidate;
        baseUrl = normalized;
        return true;
    }

    public void checkConnection() {
        String base = baseUrl;
        if (base.isEmpty()) {
            postStatus("offline", "Add your HTTPS AI server address in Settings.");
            return;
        }
        Request request = new Request.Builder().url(base + "/health").get().build();
        Call healthCall = http.newCall(request);
        healthCall.timeout().timeout(12, TimeUnit.SECONDS);
        healthCall.enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException error) {
                postStatus("offline", "AI server is unreachable. Check your internet or server address.");
            }
            @Override public void onResponse(Call call, Response response) {
                try (Response ignored = response) {
                    if (!response.isSuccessful()) {
                        postStatus("offline", "AI server is starting or temporarily unavailable.");
                        return;
                    }
                    postStatus("ready", "Cloud AI connected.");
                }
            }
        });
    }

    public void startConversation(String instruction, String historyJson) {
        systemInstruction = instruction == null ? "" : instruction;
        synchronized (history) {
            history.clear();
            ArrayList<JSONObject> newestFirst = new ArrayList<>();
            int totalChars = 0;
            try {
                JSONArray items = new JSONArray(historyJson == null ? "[]" : historyJson);
                for (int i = items.length() - 1; i >= 0 && newestFirst.size() < MAX_HISTORY_MESSAGES; i--) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;
                    String role = item.optString("role", "");
                    String text = item.optString("content", "");
                    if (!("user".equals(role) || "assistant".equals(role)) || text.trim().isEmpty()) continue;
                    if (text.length() > MAX_MESSAGE_CHARS) text = text.substring(text.length() - MAX_MESSAGE_CHARS);
                    if (totalChars + text.length() > MAX_HISTORY_CHARS) break;
                    newestFirst.add(new JSONObject().put("role", role).put("content", text));
                    totalChars += text.length();
                }
                for (int i = newestFirst.size() - 1; i >= 0; i--) history.add(newestFirst.get(i));
            } catch (Exception ignored) {
                history.clear();
            }
        }
        if (baseUrl.isEmpty()) postStatus("offline", "Add your HTTPS AI server address in Settings.");
        else postStatus("ready", "Character ready.");
    }

    private void trimHistoryLocked() {
        while (history.size() > MAX_HISTORY_MESSAGES) history.remove(0);
        int totalChars = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            int length = history.get(i).optString("content", "").length();
            if (totalChars + length > MAX_HISTORY_CHARS) {
                history.subList(0, i + 1).clear();
                break;
            }
            totalChars += length;
        }
    }

    public void sendMessage(String prompt) {
        String text = prompt == null ? "" : prompt.trim();
        if (text.isEmpty() || text.length() > MAX_MESSAGE_CHARS) {
            postError("Message must contain 1–4,000 characters.");
            return;
        }
        if (baseUrl.isEmpty()) {
            postError("Connect an HTTPS AI server in Settings before chatting.");
            return;
        }
        if (!generating.compareAndSet(false, true)) {
            postError("One reply is still being generated. Please wait a moment.");
            return;
        }

        final String conversationIdAtSend = activeConversationId;\n        final JSONArray messages = new JSONArray();
        if (!systemInstruction.isEmpty()) {
            messages.put(new JSONObject().put("role", "system").put("content", systemInstruction));
        }
        synchronized (history) {
            for (JSONObject item : history) messages.put(item);
        }
        messages.put(new JSONObject().put("role", "user").put("content", text));

        final StringBuilder assistantText = new StringBuilder();
        try {
            JSONObject payload = new JSONObject()
                    .put("model", "soulmate")
                    .put("messages", messages)
                    .put("temperature", 0.85)
                    .put("top_p", 0.92)
                    .put("max_tokens", 420)
                    .put("stream", true);

            Request request = new Request.Builder()
                    .url(baseUrl + "/v1/chat/completions")
                    .post(RequestBody.create(payload.toString(), JSON))
                    .header("Accept", "text/event-stream")
                    .build();

            Call call = http.newCall(request);
            activeCall = call;
            call.enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException error) {
                    generating.set(false);
                    postError("Could not reach the AI server. Your local chat is still saved.");
                }

                @Override public void onResponse(Call call, Response response) {
                    try (Response closeable = response) {
                        if (!response.isSuccessful()) {
                            ResponseBody body = response.body();
                            String detail = body == null ? "" : body.string();
                            throw new IOException("AI server error (" + response.code() + "). "
                                    + (detail.length() > 240 ? detail.substring(0, 240) : detail));
                        }
                        ResponseBody body = response.body();
                        if (body == null) throw new IOException("AI server returned an empty response.");
                        BufferedSource source = body.source();
                        String line;
                        while ((line = source.readUtf8Line()) != null) {
                            if (!line.startsWith("data:")) continue;
                            String data = line.substring(5).trim();
                            if ("[DONE]".equals(data)) break;
                            if (data.isEmpty()) continue;
                            JSONObject event = new JSONObject(data);
                            JSONArray choices = event.optJSONArray("choices");
                            if (choices == null || choices.length() == 0) continue;
                            JSONObject delta = choices.optJSONObject(0);
                            if (delta == null) continue;
                            JSONObject contentDelta = delta.optJSONObject("delta");
                            if (contentDelta == null) continue;
                            String token = contentDelta.optString("content", "");
                            if (!token.isEmpty()) {
                                assistantText.append(token);
                                main.post(() -> listener.onToken(token));
                            }
                        }
                        String answer = assistantText.toString().trim();
                        if (answer.isEmpty()) throw new IOException("The AI server returned an empty reply.");
                        synchronized (history) {
                            history.add(new JSONObject().put("role", "user").put("content", text));
                            history.add(new JSONObject().put("role", "assistant").put("content", answer));
                            trimHistoryLocked();
                        }
                        main.post(() -> {
                            listener.onComplete();
                            listener.onStatus("ready", "Cloud AI ready.");
                        });
                    } catch (Exception error) {
                        postError(error.getMessage() == null ? "Cloud AI generation failed." : error.getMessage());
                    } finally {
                        generating.set(false);
                        activeCall = null;
                    }
                }
            });
        } catch (Exception error) {
            generating.set(false);
            postError(error.getMessage() == null ? "Could not prepare your message." : error.getMessage());
        }
    }

    private void postStatus(String status, String detail) {
        main.post(() -> listener.onStatus(status, detail));
    }

    private void postError(String message) {
        main.post(() -> listener.onError(message));
    }

    public void close() {
        Call call = activeCall;
        if (call != null) call.cancel();
        http.dispatcher().cancelAll();
        http.connectionPool().evictAll();
    }
}
