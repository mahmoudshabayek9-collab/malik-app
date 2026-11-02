package com.malik.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.util.Locale;

/**
 * يعرض التطبيق (assets/www/index.html) داخل WebView بدون إنترنت،
 * ويوفّر له جسراً لمحرك النطق في الهاتف (NativeTTS) لأن WebView لا يدعم speechSynthesis.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final int FILE_REQ = 41;

    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private ValueCallback<Uri[]> fileCb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        setContentView(web);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setTextZoom(100);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                // الروابط الخارجية (مثل quran.com) تُفتح في المتصفح
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("audio/*");
                Intent chooser = Intent.createChooser(pick, "سجّل الآية أو اختر ملفاً صوتياً");
                Intent rec = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
                if (rec.resolveActivity(getPackageManager()) != null) {
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{rec});
                }
                try {
                    startActivityForResult(chooser, FILE_REQ);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new TtsBridge(), "NativeTTS");

        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (!ttsReady) return;
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String id) {
                }

                @Override
                public void onDone(String id) {
                    done(id);
                }

                @Override
                public void onError(String id) {
                    done(id);
                }
            });
            runOnUiThread(() -> web.evaluateJavascript("window.__ttsReady&&window.__ttsReady()", null));
        });

        web.loadUrl("https://" + HOST + "/assets/www/index.html");
    }

    private void done(String id) {
        final String safe = id == null ? "" : id.replaceAll("[^0-9]", "");
        runOnUiThread(() -> web.evaluateJavascript("window.__ttsDone&&window.__ttsDone('" + safe + "')", null));
    }

    private static Locale locale(String lang) {
        if ("en".equals(lang)) return Locale.US;
        if ("de".equals(lang)) return Locale.GERMANY;
        return new Locale("ar");
    }

    /** الواجهة التي تستدعيها صفحة التطبيق: NativeTTS.has / speak / stop */
    class TtsBridge {
        @JavascriptInterface
        public boolean has(String lang) {
            if (!ttsReady) return false;
            return tts.isLanguageAvailable(locale(lang)) >= TextToSpeech.LANG_AVAILABLE;
        }

        @JavascriptInterface
        public void speak(String text, String lang, float rate, String id) {
            if (!ttsReady) {
                done(id);
                return;
            }
            tts.setLanguage(locale(lang));
            tts.setSpeechRate(rate);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), id);
        }

        @JavascriptInterface
        public void stop() {
            if (ttsReady) tts.stop();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQ && fileCb != null) {
            Uri u = (resultCode == RESULT_OK && data != null) ? data.getData() : null;
            fileCb.onReceiveValue(u != null ? new Uri[]{u} : null);
            fileCb = null;
        }
    }

    @Override
    public void onBackPressed() {
        // نطلب من الصفحة الرجوع خطوة؛ إن كانت في الرئيسية يُغلق التطبيق
        web.evaluateJavascript("window.__back?window.__back():false", v -> {
            if (!"true".equals(v)) exitApp();
        });
    }

    private void exitApp() {
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ttsReady) tts.stop();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        web.destroy();
        super.onDestroy();
    }
}
