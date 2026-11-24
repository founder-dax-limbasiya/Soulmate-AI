package com.realworldgta;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String MODEL_FILE = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm";
    // Pin the model to the known CPU-compatible LiteRT export so users get reproducible weights.
    private static final String MODEL_URL = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/e6bb3e4a1eb3f663153f0c1c12b735d64be657a7/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm?download=true";
    private static final long MIN_MODEL_BYTES = 320L * 1024L * 1024L;
    private static final long MAX_MODEL_BYTES = 500L * 1024L * 1024L;
    private static final long EXPECTED_MODEL_BYTES = 328L * 1024L * 1024L;

    private WebView webView;
    private SecureStoreBridge secureStore;
    private LocalAiEngine localAiEngine;
    private final ExecutorService modelInstaller = Executors.newSingleThreadExecutor();
    private volatile boolean modelInstallRunning = false;

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
        webView.setBackgroundColor(Color.rgb(244, 241, 234));
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
        File directory = new File(getFilesDir(), "models");
        if (!directory.exists() && !directory.mkdirs()) return null;
        return new File(directory, MODEL_FILE);
    }

    private boolean isLocalModelReady() {
        File model = getModelFile();
        if (model == null || !model.isFile()
                || model.length() < MIN_MODEL_BYTES || model.length() > MAX_MODEL_BYTES) return false;
        File marker = new File(model.getParentFile(), MODEL_FILE + ".sha256");
        if (!marker.isFile()) return false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.io.FileInputStream(marker), StandardCharsets.US_ASCII))) {
            String value = reader.readLine();
            return value != null && value.matches("(?i)[0-9a-f]{64}");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
        return value.toString();
    }

    /**
     * Downloads the open-source model into app-private storage. The APK itself never
     * contains model weights. The model is fetched once and then runs entirely offline.
     */
    private void prepareBundledModel() {
        if (isLocalModelReady()) {
            callJs("onBundledModelReady");
            return;
        }

        synchronized (this) {
            if (modelInstallRunning) return;
            modelInstallRunning = true;
        }

        modelInstaller.execute(() -> {
            File destination = getModelFile();
            File partial = destination == null ? null : new File(destination.getParentFile(), MODEL_FILE + ".partial");
            HttpURLConnection connection = null;
            try {
                if (destination == null || partial == null) {
                    throw new IllegalStateException("App storage is unavailable.");
                }
                if (partial.exists() && !partial.delete()) {
                    throw new IllegalStateException("Could not clear an incomplete model download.");
                }
                long minimumFreeBytes = EXPECTED_MODEL_BYTES + 80L * 1024L * 1024L;
                if (destination.getParentFile().getUsableSpace() < minimumFreeBytes) {
                    throw new IllegalStateException("Free at least 410 MiB of phone storage, then retry the model download.");
                }

                callJs("onBundledModelProgress", "0");
                connection = (HttpURLConnection) new URL(MODEL_URL).openConnection();
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(90000);
                connection.setInstanceFollowRedirects(true);
                connection.setUseCaches(false);
                connection.setRequestProperty("User-Agent", "SoulmateAI-Android/0.6.0");
                connection.setRequestProperty("Accept", "application/octet-stream");

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("Model download failed (HTTP " + responseCode + "). Check your internet and retry.");
                }

                long contentLength = connection.getContentLengthLong();
                if (contentLength > 0 && (contentLength < MIN_MODEL_BYTES || contentLength > MAX_MODEL_BYTES)) {
                    throw new IllegalStateException("The model download has an unexpected file size. Please retry later.");
                }
                long progressTotal = contentLength > 0 ? contentLength : EXPECTED_MODEL_BYTES;
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long downloaded = 0L;
                int lastProgressBucket = -1;
                byte[] buffer = new byte[256 * 1024];

                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(partial, false)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException("Download paused. Open Settings and tap Download model again.");
                        }
                        downloaded += count;
                        if (downloaded > MAX_MODEL_BYTES) {
                            throw new IllegalStateException("The downloaded model is larger than expected.");
                        }
                        output.write(buffer, 0, count);
                        digest.update(buffer, 0, count);
                        int percent = (int) Math.min(100L, downloaded * 100L / progressTotal);
                        int bucket = percent / 5;
                        if (bucket > lastProgressBucket) {
                            lastProgressBucket = bucket;
                            callJs("onBundledModelProgress", String.valueOf(percent));
                        }
                    }
                    output.flush();
                    output.getFD().sync();
                }

                if (downloaded < MIN_MODEL_BYTES) {
                    throw new IllegalStateException("The model download was incomplete. Check your connection and retry.");
                }
                String checksum = toHex(digest.digest());

                if (destination.exists() && !destination.delete()) {
                    throw new IllegalStateException("Could not replace the old model file.");
                }
                if (!partial.renameTo(destination)) {
                    throw new IllegalStateException("Could not save the downloaded model to phone storage.");
                }

                File marker = new File(destination.getParentFile(), MODEL_FILE + ".sha256");
                try (FileOutputStream output = new FileOutputStream(marker, false)) {
                    output.write(checksum.getBytes(StandardCharsets.US_ASCII));
                    output.write('\n');
                    output.getFD().sync();
                }

                runOnUiThread(() -> {
                    modelInstallRunning = false;
                    callJs("onBundledModelReady");
                });
            } catch (Exception error) {
                if (partial != null && partial.exists()) partial.delete();
                runOnUiThread(() -> {
                    modelInstallRunning = false;
                    callJs("onBundledModelError", error.getMessage() == null
                            ? "Could not download the on-device AI model." : error.getMessage());
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
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
            try {
                return isLocalModelReady();
            } catch (Exception ignored) {
                return false;
            }
        }

        @JavascriptInterface
        public void prepareBundledModel() {
            MainActivity.this.prepareBundledModel();
        }

        @JavascriptInterface
        public void loadModel(String systemInstruction, String historyJson) {
            File file = getModelFile();
            if (file == null || !hasModel()) {
                callJs("onAiError", "The on-device AI model is not downloaded yet. Open Settings to download it.");
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

    }

    @Override
    protected void onDestroy() {
        modelInstaller.shutdownNow();
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
