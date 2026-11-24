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

public class MainActivity extends Activity {
    private WebView webView;
    private SecureStoreBridge secureStore;
    private RemoteAiClient remoteAiClient;

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
        remoteAiClient = new RemoteAiClient(new RemoteAiClient.Listener() {
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
        public boolean setServerUrl(String url) {
            return remoteAiClient.setBaseUrl(url);
        }

        @JavascriptInterface
        public void checkServer() {
            remoteAiClient.checkConnection();
        }

        @JavascriptInterface
        public void startCharacter(String conversationId, String systemInstruction, String historyJson) {
            remoteAiClient.startConversation(conversationId, systemInstruction, historyJson);
        }

        @JavascriptInterface
        public void sendMessage(String prompt) {
            remoteAiClient.sendMessage(prompt);
        }
    }

    @Override
    protected void onDestroy() {
        if (remoteAiClient != null) {
            remoteAiClient.close();
            remoteAiClient = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("SoulmateNative");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
