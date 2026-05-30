'''package com.alabeed.pharmacy;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    
    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILECHOOSER_RESULTCODE = 1;
    private static final String PREFS_NAME = "PharmacyPrefs";
    private SharedPreferences prefs;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Full screen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        setContentView(R.layout.activity_main);
        
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        setupWebView();
        
        // Load the pharmacy HTML from assets
        webView.loadUrl("file:///android_asset/index.html");
    }
    
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        
        // Enable JavaScript
        settings.setJavaScriptEnabled(true);
        
        // ✅ Enable DOM storage for localStorage support
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        
        // Enable file access for CSV import
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        
        // Enable zoom
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        
        // Performance
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        
        // Enable media playback
        settings.setMediaPlaybackRequiresUserGesture(false);
        
        // Cookie manager
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        
        // Add JavaScript interface for native functions
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                
                // Inject stored data after page loads
                injectStoredData();
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Handle WhatsApp and SMS links
                if (url.startsWith("https://wa.me/") || url.startsWith("whatsapp://")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "WhatsApp غير مثبت", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }
                if (url.startsWith("sms:")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "لا يمكن فتح الرسائل", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }
                if (url.startsWith("tel:")) {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    view.loadUrl(url);
                    return true;
                }
                return false;
            }
        });
        
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }
            
            // Handle file input for CSV upload
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;
                
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILECHOOSER_RESULTCODE);
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    Toast.makeText(MainActivity.this, "لا يمكن فتح اختيار الملفات", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });
        
        // Handle downloads
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimeType, long contentLength) {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription("جاري التحميل...");
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, 
                    URLUtil.guessFileName(url, contentDisposition, mimeType));
                
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(MainActivity.this, "جاري تحميل الملف...", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    // Inject stored data from SharedPreferences into WebView
    private void injectStoredData() {
        try {
            // Get all stored data
            String customers = prefs.getString("customers", "[]");
            String password = prefs.getString("password", "1234");
            String firstRun = prefs.getString("first_run", String.valueOf(System.currentTimeMillis()));
            String activated = prefs.getString("activated", "false");
            
            // Create JSON object with all data
            JSONObject data = new JSONObject();
            data.put("customers", new JSONArray(customers));
            data.put("password", password);
            data.put("first_run", firstRun);
            data.put("activated", activated);
            
            // Inject into WebView localStorage via JavaScript
            String js = "javascript:(function() {" +
                "var data = " + data.toString() + ";" +
                "for (var key in data) {" +
                "    localStorage.setItem('pharmacy_' + key, JSON.stringify(data[key]));" +
                "}" +
                "if (typeof initApp === 'function') initApp();" +
                "})()";
            
            webView.evaluateJavascript(js, null);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    String dataString = data.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }
    
    // Handle back button
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
    
    // JavaScript Interface for native functions
    public class WebAppInterface {
        Context mContext;
        
        WebAppInterface(Context c) {
            mContext = c;
        }
        
        @JavascriptInterface
        public void showToast(String message) {
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
            });
        }
        
        @JavascriptInterface
        public void saveData(String key, String value) {
            SharedPreferences.Editor editor = prefs.edit();
            // Remove 'pharmacy_' prefix if present
            if (key.startsWith("pharmacy_")) {
                key = key.substring(9);
            }
            editor.putString(key, value);
            editor.apply();
        }
        
        @JavascriptInterface
        public String loadData(String key) {
            if (key.startsWith("pharmacy_")) {
                key = key.substring(9);
            }
            return prefs.getString(key, "");
        }
        
        @JavascriptInterface
        public void openWhatsApp(String phone, String message) {
            try {
                String url = "https://wa.me/" + phone + "?text=" + Uri.encode(message);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                mContext.startActivity(intent);
            } catch (Exception e) {
                showToast("WhatsApp غير مثبت");
            }
        }
        
        @JavascriptInterface
        public void openSMS(String phone, String message) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("sms:" + phone));
                intent.putExtra("sms_body", message);
                mContext.startActivity(intent);
            } catch (Exception e) {
                showToast("لا يمكن فتح الرسائل");
            }
        }
        
        @JavascriptInterface
        public void showAlert(String title, String message) {
            new Handler(Looper.getMainLooper()).post(() -> {
                new AlertDialog.Builder(mContext)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("موافق", null)
                    .show();
            });
        }
    }
}'''

with open("/mnt/agents/output/MainActivity_updated.java", "w", encoding="utf-8") as f:
    f.write(main_activity_updated)

print("✅ Updated MainActivity.java created!")
print("\n🔧 Key changes:")
print("1. Added SharedPreferences for native Android storage")
print("2. Added injectStoredData() to inject data into WebView localStorage")
print("3. Added saveData() and loadData() JavaScript interfaces")
print("4. Data persists using Android native storage instead of HTML5 localStorage")
