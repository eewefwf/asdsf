package com.dsh.callorder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import java.io.File;
import java.io.IOException;

/**
 * 订单一键拨号 —— WebView 外壳
 * - 页面通过 https://appassets.androidplatform.net/ 提供（安全上下文，getUserMedia 才可用）
 * - 摄像头：CAMERA 权限 + onPermissionRequest 授权 RESOURCE_VIDEO_CAPTURE
 * - tel: 链接交给系统拨号盘
 * - <input type="file" capture> 调系统相机
 */
public class MainActivity extends AppCompatActivity {

    private static final String START_URL = "https://appassets.androidplatform.net/assets/index.html";

    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private ValueCallback<Uri[]> fileCallback;
    private Uri pendingCameraUri;

    /** 等用户授予 CAMERA 后要回应的网页权限请求 */
    private PermissionRequest pendingPermRequest;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && pendingPermRequest != null) {
                    pendingPermRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                } else if (!granted) {
                    Toast.makeText(MainActivity.this, "没有相机权限，无法扫码", Toast.LENGTH_LONG).show();
                }
                pendingPermRequest = null;
            });

    private final ActivityResultLauncher<Intent> filePicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Uri[] uris = null;
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK) {
                    if (data != null && data.getData() != null) {
                        uris = new Uri[]{data.getData()};
                    } else if (pendingCameraUri != null) {
                        uris = new Uri[]{pendingCameraUri};
                    }
                }
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(uris);
                    fileCallback = null;
                }
                pendingCameraUri = null;
            });

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);      // IndexedDB / localStorage 必须
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);

        webView.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request) {
                Uri url = request.getUrl();
                String host = url.getHost();
                String scheme = url.getScheme() == null ? "" : url.getScheme();

                if ("appassets.androidplatform.net".equals(host)) return false;

                if (scheme.equals("tel")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_DIAL, url));
                    } catch (ActivityNotFoundException ignored) {
                        Toast.makeText(MainActivity.this, "没有拨号应用", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                if (scheme.equals("sms") || scheme.equals("mailto")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, url));
                    } catch (ActivityNotFoundException ignored) { }
                    return true;
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Toast.makeText(MainActivity.this, "页面加载失败：" + description, Toast.LENGTH_LONG).show();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            /** 网页要摄像头时走这里（getUserMedia） */
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean wantsVideo = false;
                    for (String res : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)) wantsVideo = true;
                    }
                    if (!wantsVideo) {
                        request.deny();
                        return;
                    }
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        pendingPermRequest = request;
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView view,
                                             ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;

                boolean wantCamera = params != null && params.isCaptureEnabled();
                Intent intent;
                if (wantCamera) {
                    try {
                        File photo = createImageFile();
                        pendingCameraUri = FileProvider.getUriForFile(
                                MainActivity.this, getPackageName() + ".fileprovider", photo);
                        intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
                        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (IOException | IllegalArgumentException e) {
                        pendingCameraUri = null;
                        intent = buildGalleryIntent();
                    }
                } else {
                    intent = buildGalleryIntent();
                }

                try {
                    filePicker.launch(intent);
                    return true;
                } catch (ActivityNotFoundException e) {
                    if (fileCallback != null) {
                        fileCallback.onReceiveValue(null);
                        fileCallback = null;
                    }
                    pendingCameraUri = null;
                    Toast.makeText(MainActivity.this, "没有可用的相机/相册", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.loadUrl(START_URL);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private Intent buildGalleryIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    private File createImageFile() throws IOException {
        File dir = new File(getCacheDir(), "camera");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("无法创建缓存目录");
        return File.createTempFile("shot_", ".jpg", dir);
    }

    @Override
    protected void onDestroy() {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
