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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String MODEL_FILE = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm";
    // The selected model card lists the artifact at 328 MiB. Reject incomplete downloads.
    private static final long MIN_MODEL_BYTES = 328L * 1024L * 1024L;

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

    private String readBundledModelChecksum() throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open("models/model.sha256"), StandardCharsets.US_ASCII))) {
            String value = reader.readLine();
            if (value == null || !value.matches("(?i)[0-9a-f]{64}")) {
                throw new IllegalStateException("Bundled model checksum is missing or invalid.");
            }
            return value.toLowerCase(java.util.Locale.ROOT);
        }
    }

    private boolean isBundledModelReady(String expectedChecksum) {
        File model = getModelFile();
        if (model == null || !model.isFile() || model.length() < MIN_MODEL_BYTES) return false;
        File marker = new File(model.getParentFile(), MODEL_FILE + ".sha256");
        if (!marker.isFile()) return false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.io.FileInputStream(marker), StandardCharsets.US_ASCII))) {
            String installedChecksum = reader.readLine();
            return expectedChecksum.equals(installedChecksum);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
        return value.toString();
    }

    private void prepareBundledModel() {
        final String expectedChecksum;
        try {
            expectedChecksum = readBundledModelChecksum();
        } catch (Exception error) {
            callJs("onBundledModelError",
                    "This APK does not contain the complete AI model. Install the full bundled-model APK.");
            return;
        }

        if (isBundledModelReady(expectedChecksum)) {
            callJs("onBundledModelReady");
            return;
        }

        synchronized (this) {
            if (modelInstallRunning) return;
            modelInstallRunning = true;
        }

        modelInstaller.execute(() -> {
            File destination = getModelFile();
            File partial = destination == null ? null
                    : new File(destination.getParentFile(), MODEL_FILE + "."
                            + Integer.toHexString(System.identityHashCode(MainActivity.this)) + ".partial");
            try {
                if (destination == null || partial == null) {
                    throw new IllegalStateException("App storage is unavailable.");
                }
                if (partial.exists()) partial.delete();
                long minimumFreeBytes = MIN_MODEL_BYTES + 64L * 1024L * 1024L;
                if (destination.getParentFile().getUsableSpace() < minimumFreeBytes) {
                    throw new IllegalStateException("Free at least 400 MiB of phone storage, then tap Retry model setup.");
                }

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long copied = 0L;
                byte[] buffer = new byte[1024 * 1024];
                callJs("onBundledModelProgress", "0");

                try (InputStream input = getAssets().open(
                            "models/" + MODEL_FILE, android.content.res.AssetManager.ACCESS_STREAMING);
                     FileOutputStream output = new FileOutputStream(partial)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException("Model setup was paused. Open the app to retry.");
                        }
                        output.write(buffer, 0, count);
                        digest.update(buffer, 0, count);
                        copied += count;
                        int percent = (int) Math.min(100L, copied * 100L / MIN_MODEL_BYTES);
                        callJs("onBundledModelProgress", String.valueOf(percent));
                    }
                    output.getFD().sync();
                }

                if (copied < MIN_MODEL_BYTES) {
                    throw new IllegalStateException("The bundled AI model is incomplete.");
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Model setup was paused. Open the app to retry.");
                }
                String actualChecksum = toHex(digest.digest());
                if (!expectedChecksum.equals(actualChecksum)) {
                    throw new IllegalStateException("The bundled AI model failed its integrity check.");
                }

                if (destination.exists() && !destination.delete()) {
                    throw new IllegalStateException("Could not replace the previous model file.");
                }
                if (!partial.renameTo(destination)) {
                    throw new IllegalStateException("Could not finish preparing the bundled AI model.");
                }

                File marker = new File(destination.getParentFile(), MODEL_FILE + ".sha256");
                try (FileOutputStream output = new FileOutputStream(marker, false)) {
                    output.write(expectedChecksum.getBytes(StandardCharsets.US_ASCII));
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
                    callJs("onBundledModelError",
                            error.getMessage() == null ? "Could not prepare the bundled AI model." : error.getMessage());
                });
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
                return isBundledModelReady(readBundledModelChecksum());
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
            if (file == null || !file.isFile() || file.length() < MIN_MODEL_BYTES) {
                callJs("onAiError", "The built-in AI model is not ready yet. Open Settings and retry model setup.");
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
