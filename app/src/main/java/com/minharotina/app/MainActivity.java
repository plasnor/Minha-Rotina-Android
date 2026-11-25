
package com.minharotina.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.JsPromptResult;
import android.widget.Toast;
import java.io.IOException;
import java.io.OutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    private static final int FILE_REQUEST = 1001;

    private static final int SAVE_BACKUP = 1002;
    private static final String APP_URL = "https://plasnor.github.io/Minha-Rotina/";
    private File pendingBackup;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingBackup != null) outState.putString("pendingBackup", pendingBackup.getName());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        settings.setUserAgentString(settings.getUserAgentString() + " MinhaRotinaBundled/4");
        if (savedInstanceState != null && savedInstanceState.containsKey("pendingBackup"))
            pendingBackup = new File(getCacheDir(), savedInstanceState.getString("pendingBackup"));

        // Keep the installed app's existing web origin and localStorage while serving the bundled UI.
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request.isForMainFrame() && APP_URL.equals(request.getUrl().toString())) {
                    try {
                        return new WebResourceResponse("text/html", "UTF-8", getAssets().open("index.html"));
                    } catch (IOException ignored) { return null; }
                }
                return null;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String json, JsPromptResult result) {
                if (!APP_URL.equals(url) || !"minha-rotina:export".equals(message))
                    return super.onJsPrompt(view, url, message, json, result);
                result.confirm("");
                if (pendingBackup != null) return true;
                try {
                    pendingBackup = new File(getCacheDir(), "backup-" + UUID.randomUUID() + ".json");
                    try (FileOutputStream out = new FileOutputStream(pendingBackup)) {
                        out.write(json.getBytes("UTF-8"));
                    }
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_TITLE, "minha-rotina-backup.json");
                    startActivityForResult(intent, SAVE_BACKUP);
                } catch (Exception error) { finishBackup("Falha ao preparar backup."); }
                return true;
            }

            @Override
            public boolean onShowFileChooser(
                WebView webView,
                ValueCallback<Uri[]> callback,
                FileChooserParams params
            ) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }

                fileCallback = callback;

                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent, FILE_REQUEST);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
            }
        });

        setContentView(webView);

        webView.loadUrl(APP_URL);
    }

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SAVE_BACKUP) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                finishBackup(null);
            } else {
                try (InputStream input = new FileInputStream(pendingBackup);
                     OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) {
                    if (output == null) throw new IOException("Destino indisponível");
                    byte[] buffer = new byte[8192];
                    int bytes;
                    while ((bytes = input.read(buffer)) != -1) output.write(buffer, 0, bytes);
                } catch (Exception error) { finishBackup("Falha ao salvar backup."); return; }
                finishBackup("Backup salvo.");
            }
            return;
        }
        if (requestCode == FILE_REQUEST) {
            if (fileCallback != null) {
                Uri[] result =
                    WebChromeClient.FileChooserParams
                        .parseResult(resultCode, data);

                fileCallback.onReceiveValue(result);
                fileCallback = null;
            }
        }
    }

    private void finishBackup(String message) {
        if (pendingBackup != null) pendingBackup.delete();
        pendingBackup = null;
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
