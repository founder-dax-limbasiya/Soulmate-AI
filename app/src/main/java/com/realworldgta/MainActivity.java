package com.realworldgta;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.File;

public class MainActivity extends Activity {
    private static final String MODEL_FILE = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm";
    private static final String MODEL_URL =
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/"
                    + MODEL_FILE + "?download=true";
    // The selected model card lists the artifact at 328 MiB. Reject incomplete downloads.
    private static final long MIN_MODEL_BYTES = 328L * 1024L * 1024L;

    private WebView webView;
    private DownloadManager downloadManager;
    private SecureStoreBridge secureStore;
    private LocalAiEngine localAiEngine;
    private long activeDownloadId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(244, 241, 234));
        getWindow().setNavigationBarColor(Color.rgb(244, 241, 234));
        int systemUiFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            systemUiFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(systemUiFlags);

        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        secureStore = new SecureStoreBridge(this);
        localAiEngine = new LocalAiEngine(this, new LocalAiEngine.Listener() {
            @Override public void onStatus(String status, String detail) {
                callJs("onAiStatus", status, detail);
            }
            @Override public void onToken(String token) {
                callJs("onAiToken", token);
            }
            @Override public void onComplete() {
                callJs("onAiComplete");
            }
            @Override public void onError(String message) {
                callJs("onAiError", message);
            }
        });

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(10, 14, 28));
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // The app is a self-contained local UI. Remote pages are never opened in this WebView.
                return !request.getUrl().toString().startsWith("file:///android_asset/");
            }
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        webView.addJavascriptInterface(new NativeBridge(), "SoulmateNative");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private File getModelFile() {
        File directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null) return null;
        return new File(directory, MODEL_FILE);
    }

    private void callJs(String function, String... args) {
        if (webView == null) return;
        StringBuilder script = new StringBuilder("window.")
                .append(function).append(" && window.")
                .append(function).append("(");
        for (int index = 0; index < args.length; index++) {
            if (index > 0) script.append(",");
            script.append(JSONObject.quote(args[index] == null ? "" : args[index]));
        }
        script.append(");");
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(script.toString(), null);
        });
    }

    private final class NativeBridge {
        @JavascriptInterface
        public String loadEncryptedState() {
            return secureStore.loadEncryptedState();
        }

        @JavascriptInterface
        public boolean saveEncryptedState(String json) {
            return secureStore.saveEncryptedState(json);
        }

        @JavascriptInterface
        public boolean clearEncryptedState() {
            return secureStore.clearEncryptedState();
        }

        @JavascriptInterface
        public boolean hasModel() {
            File file = getModelFile();
            return file != null && file.isFile() && file.length() >= MIN_MODEL_BYTES;
        }

        @JavascriptInterface
        public String downloadModel() {
            try {
                File file = getModelFile();
                if (file == null) return new JSONObject().put("status", "storage_unavailable").toString();
                if (file.isFile() && file.length() >= MIN_MODEL_BYTES) {
                    return new JSONObject().put("status", "already_downloaded").toString();
                }

                if (activeDownloadId != -1L) {
                    JSONObject current = queryDownload(activeDownloadId);
                    if ("downloading".equals(current.optString("status"))
                            || "queued".equals(current.optString("status"))) {
                        return current.toString();
                    }
                }

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(MODEL_URL));
                request.setTitle("Soulmate AI model");
                request.setDescription("Downloading the on-device Qwen3 model (about 328 MiB)");
                request.setAllowedOverMetered(true);
                request.setAllowedOverRoaming(false);
                request.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalFilesDir(
                        MainActivity.this, Environment.DIRECTORY_DOWNLOADS, MODEL_FILE);
                activeDownloadId = downloadManager.enqueue(request);
                return new JSONObject()
                        .put("status", "queued")
                        .put("id", String.valueOf(activeDownloadId))
                        .toString();
            } catch (Exception error) {
                return safeJson("error", error.getMessage());
            }
        }

        @JavascriptInterface
        public String getDownloadStatus(String id) {
            try {
                long requestId = Long.parseLong(id);
                return queryDownload(requestId).toString();
            } catch (Exception error) {
                return safeJson("error", error.getMessage());
            }
        }

        @JavascriptInterface
        public void loadModel(String systemInstruction, String historyJson) {
            File file = getModelFile();
            if (file == null || !file.isFile() || file.length() < MIN_MODEL_BYTES) {
                callJs("onAiError", "Download the complete local model first.");
                return;
            }
            localAiEngine.loadModel(file.getAbsolutePath(), systemInstruction, historyJson);
        }

        @JavascriptInterface
        public void startCharacter(String systemInstruction, String historyJson) {
            localAiEngine.startConversation(systemInstruction, historyJson);
        }

        @JavascriptInterface
        public void sendMessage(String prompt) {
            localAiEngine.sendMessage(prompt);
        }

        private JSONObject queryDownload(long requestId) {
            JSONObject result = new JSONObject();
            Cursor cursor = null;
            try {
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(requestId);
                cursor = downloadManager.query(query);
                if (cursor == null || !cursor.moveToFirst()) {
                    File file = getModelFile();
                    return result.put("status",
                            file != null && file.isFile() && file.length() >= MIN_MODEL_BYTES
                                    ? "complete" : "not_found");
                }
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_STATUS));
                long downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long total = cursor.getLong(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                int progress = total > 0 ? (int) Math.min(100, (downloaded * 100L) / total) : 0;
                String label;
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    File file = getModelFile();
                    label = file != null && file.isFile() && file.length() >= MIN_MODEL_BYTES
                            ? "complete" : "invalid_file";
                } else if (status == DownloadManager.STATUS_RUNNING) label = "downloading";
                else if (status == DownloadManager.STATUS_PENDING) label = "queued";
                else if (status == DownloadManager.STATUS_PAUSED) label = "paused";
                else label = "failed";

                result.put("status", label);
                result.put("progress", progress);
                result.put("downloadedBytes", downloaded);
                result.put("totalBytes", total);
                if (status == DownloadManager.STATUS_FAILED) {
                    result.put("reason", cursor.getInt(cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_REASON)));
                }
                return result;
            } catch (Exception error) {
                return safeJsonObject("error", error.getMessage());
            } finally {
                if (cursor != null) cursor.close();
            }
        }

        private JSONObject safeJsonObject(String key, String value) {
            try {
                return new JSONObject().put(key, value == null ? "" : value);
            } catch (Exception ignored) {
                return new JSONObject();
            }
        }

        private String safeJson(String key, String value) {
            try {
                return new JSONObject().put(key, value == null ? "" : value).toString();
            } catch (Exception ignored) {
                return "{}";
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (localAiEngine != null) {
            localAiEngine.close();
            localAiEngine = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("SoulmateNative");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
