package de.baumann.browser.view;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.ContentValues.TAG;
import static android.content.Context.NOTIFICATION_SERVICE;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import de.baumann.browser.R;
import de.baumann.browser.activity.BrowserActivity;
import de.baumann.browser.browser.AlbumController;
import de.baumann.browser.browser.AndroidEthereum;
import de.baumann.browser.browser.BrowserController;
import de.baumann.browser.browser.List_protected;
import de.baumann.browser.browser.List_standard;
import de.baumann.browser.browser.List_trusted;
import de.baumann.browser.browser.NinjaDownloadListener;
import de.baumann.browser.browser.NinjaWebChromeClient;
import de.baumann.browser.browser.NinjaWebViewClient;
import de.baumann.browser.database.FaviconHelper;
import de.baumann.browser.database.Record;
import de.baumann.browser.database.RecordAction;
import de.baumann.browser.unit.BrowserUnit;
import de.baumann.browser.unit.HelperUnit;

public class NinjaWebView extends WebView implements AlbumController {

    public boolean fingerPrintProtection;
    public boolean history;
    public boolean adBlock;
    public boolean saveData;
    public boolean camera;
    public boolean isBackPressed;
    private OnScrollChangeListener onScrollChangeListener;
    private Context context;
    private boolean desktopMode;
    private boolean stopped;
    private AdapterTabs album;
    private AlbumController predecessor = null;
    private NinjaWebViewClient webViewClient;
    private NinjaWebChromeClient webChromeClient;
    private NinjaDownloadListener downloadListener;
    private String profile;
    private List_trusted listTrusted;
    private List_standard listStandard;
    private List_protected listProtected;
    private Bitmap favicon;
    private SharedPreferences sp;
    private boolean foreground;
    private static BrowserController browserController = null;

    public NinjaWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NinjaWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    public NinjaWebView(Context context) {
        super(context);
        sp = PreferenceManager.getDefaultSharedPreferences(context);
        String profile = sp.getString("profile", "standard");
        this.context = context;
        this.foreground = false;
        this.desktopMode = false;
        this.isBackPressed = false;
        this.fingerPrintProtection = sp.getBoolean(profile + "_fingerPrintProtection", true);
        this.history = sp.getBoolean(profile + "_history", true);
        this.adBlock = sp.getBoolean(profile + "_adBlock", false);
        this.saveData = sp.getBoolean(profile + "_saveData", false);
        this.camera = sp.getBoolean(profile + "_camera", false);

        this.stopped = false;
        this.listTrusted = new List_trusted(this.context);
        this.listStandard = new List_standard(this.context);
        this.listProtected = new List_protected(this.context);
        this.album = new AdapterTabs(this.context, this, browserController);
        this.webViewClient = new NinjaWebViewClient(this) {
            @Override
            public void onReceivedError(WebView webview, WebResourceRequest request, WebResourceError error) {
                Context context = webview.getContext();
                String description = error.getDescription().toString();
                String failingUrl = request.getUrl().toString();
                String urlToLoad = sp.getString("urlToLoad", "");
                String htmlData = getErrorHTML(context, description, urlToLoad);
                if (failingUrl.contains(urlToLoad)) {
                    webview.loadDataWithBaseURL(urlToLoad, htmlData, "text/html", "UTF-8",urlToLoad);
                    webview.invalidate();
                }
            }
        };
        this.webChromeClient = new NinjaWebChromeClient(this);
        this.downloadListener = new NinjaDownloadListener(this.context, this);

        initWebView();
        initAlbum();

        this.addJavascriptInterface(new AndroidEthereum(context, this), "AndroidEthereum");
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static String getErrorHTML(Context context, String description, String failingUrl) {
        int primary = MaterialColors.getColor(context, R.attr.colorPrimary, Color.GREEN);
        int background = MaterialColors.getColor(context, android.R.attr.colorBackground, Color.BLACK);
        String primaryHex = String.format("#%06X", (0xFFFFFF & primary));
        String backgroundHex = String.format("#%06X", (0xFFFFFF & background));
        String errorSvgPath = "";
        try {
            InputStream inputStream = context.getResources().openRawResource(R.raw.error);
            byte[] b = new byte[inputStream.available()];
            inputStream.read(b);
            errorSvgPath = new String(b);
        } catch (Exception ignored) {}

        String s = context.getString(R.string.app_error) + ": " +failingUrl;
        return "<html><body>" +
                errorSvgPath +
                "<div align=\"center\">" +
                description +
                "<hr style=\"height: 1rem; visibility:hidden;\" />" +
                s +
                "\n</div>" +
                "<a href=\"" + failingUrl + "\">" + context.getString(R.string.menu_reload) + "</a>" +
                "</body></html>" +
                "<style>" +
                "html { background: " + backgroundHex + ";" + "color: " + primaryHex + "; }" +
                "body { min-height: 100vh; display: flex; flex-direction: column; justify-content: center; align-items: center }" +
                "svg { transform: scale(3); margin-bottom: 4rem; fill: " + primaryHex + "; }" +
                "a { margin-top: 1rem; text-decoration: none; padding: 0.7rem 1rem; border-radius: 1rem; background: " + primaryHex + ";" + "color: " + backgroundHex + "; }" +
                "p { line-height: 150%; }" +
                "</style>";
    }

    @Override
    public void onScrollChanged(int l, int t, int old_l, int old_t) {
        super.onScrollChanged(l, t, old_l, old_t);
        if (onScrollChangeListener != null) onScrollChangeListener.onScrollChange(t, old_t);
    }

    public void setOnScrollChangeListener(OnScrollChangeListener onScrollChangeListener) {
        this.onScrollChangeListener = onScrollChangeListener;
    }

    public void setIsBackPressed(Boolean isBackPressed) {
        this.isBackPressed = isBackPressed;
    }

    public boolean isForeground() {
        return foreground;
    }

    public static BrowserController getBrowserController() {
        return browserController;
    }

    public void setBrowserController(BrowserController browserController) {
        NinjaWebView.browserController = browserController;
        this.album.setBrowserController(browserController);
    }

    private synchronized void initWebView() {
        setWebViewClient(webViewClient);
        setWebChromeClient(webChromeClient);
        setDownloadListener(downloadListener);
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @TargetApi(Build.VERSION_CODES.O)
    public synchronized void initPreferences(String url) {

        sp = PreferenceManager.getDefaultSharedPreferences(context);
        profile = sp.getString("profile", "profileStandard");
        String profileOriginal = profile;
        WebSettings webSettings = getSettings();

        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if ((nightModeFlags == Configuration.UI_MODE_NIGHT_YES) || sp.getString("sp_theme", "1").equals("3")) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                boolean allowed = sp.getBoolean("setAlgorithmicDarkeningAllowed", true);
                if (!allowed) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, false);
                    sp.edit().putBoolean("setAlgorithmicDarkeningAllowed", false).apply();
                } else {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, true);
                    sp.edit().putBoolean("setAlgorithmicDarkeningAllowed", true).apply();
                }
            }
        }

        String userAgent = getUserAgent(desktopMode);
        webSettings.setUserAgentString(userAgent);
        if (android.os.Build.VERSION.SDK_INT >= 26) webSettings.setSafeBrowsingEnabled(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportMultipleWindows(true);
        webSettings.setTextZoom(Integer.parseInt(Objects.requireNonNull(sp.getString("sp_fontSize", "100"))));

        if (sp.getBoolean("sp_autofill", true)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                this.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
            else webSettings.setSaveFormData(true);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                this.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            else webSettings.setSaveFormData(false);
        }

        if (listTrusted.isWhite(url)) profile = "profileTrusted";
        else if (listStandard.isWhite(url)) profile = "profileStandard";
        else if (listProtected.isWhite(url)) profile = "profileProtected";

        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webSettings.setMediaPlaybackRequiresUserGesture(sp.getBoolean(profile + "_saveData", true));
        webSettings.setBlockNetworkImage(!sp.getBoolean(profile + "_images", true));
        webSettings.setGeolocationEnabled(sp.getBoolean(profile + "_location", false));
        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(sp.getBoolean(profile + "_javascriptPopUp", false));
        webSettings.setDomStorageEnabled(true);
        fingerPrintProtection = sp.getBoolean(profile + "_fingerPrintProtection", true);
        history = sp.getBoolean(profile + "_saveHistory", true);
        adBlock = sp.getBoolean(profile + "_adBlock", true);
        saveData = sp.getBoolean(profile + "_saveData", true);
        camera = sp.getBoolean(profile + "_camera", true);
        initCookieManager(url);
        profile = profileOriginal;

        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);

        fingerPrintProtection = sp.getBoolean(profile + "_fingerPrintProtection", true);
        history = sp.getBoolean(profile + "_saveHistory", true);
        adBlock = sp.getBoolean(profile + "_adBlock", true);
        saveData = sp.getBoolean(profile + "_saveData", true);
        camera = sp.getBoolean(profile + "_camera", true);

        CookieManager manager = CookieManager.getInstance();
        if (sp.getBoolean(profile + "_cookies", false)) {
            manager.setAcceptCookie(true);
            manager.getCookie(url);
        } else manager.setAcceptCookie(false);
        manager.setAcceptThirdPartyCookies(this, true);
        profile = profileOriginal;
    }

    public void setProfileIcon(FloatingActionButton omniBox_tab) {
        String url = this.getUrl();
        String profile = sp.getString("profile", "profileStandard");
        assert url != null;
        switch (profile) {
            case "profileTrusted":
                omniBox_tab.setImageResource(R.drawable.icon_profile_trusted);
                break;
            case "profileStandard":
                omniBox_tab.setImageResource(R.drawable.icon_profile_standard);
                break;
            case "profileProtected":
                omniBox_tab.setImageResource(R.drawable.icon_profile_protected);
                break;
            default:
                omniBox_tab.setImageResource(R.drawable.icon_profile_changed);
                break;
        }

        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(R.attr.colorError, typedValue, true);
        int color = typedValue.data;

        if (listTrusted.isWhite(url)) {
            omniBox_tab.setImageResource(R.drawable.icon_profile_trusted);
            omniBox_tab.getDrawable().mutate().setTint(color);
        } else if (listStandard.isWhite(url)) {
            omniBox_tab.setImageResource(R.drawable.icon_profile_standard);
            omniBox_tab.getDrawable().mutate().setTint(color);
        } else if (listProtected.isWhite(url)) {
            omniBox_tab.setImageResource(R.drawable.icon_profile_protected);
            omniBox_tab.getDrawable().mutate().setTint(color);
        }
    }

    public void setProfileDefaultValues() {
        sp.edit()
                .putBoolean("profileTrusted_saveData", true)
                .putBoolean("profileTrusted_images", true)
                .putBoolean("profileTrusted_adBlock", true)
                .putBoolean("profileTrusted_location", false)
                .putBoolean("profileTrusted_fingerPrintProtection", false)
                .putBoolean("profileTrusted_cookies", true)
                .putBoolean("profileTrusted_javascript", true)
                .putBoolean("profileTrusted_javascriptPopUp", true)
                .putBoolean("profileTrusted_saveHistory", true)
                .putBoolean("profileTrusted_camera", false)
                .putBoolean("profileTrusted_microphone", false)
                .putBoolean("profileTrusted_dom", true)

                .putBoolean("profileStandard_saveData", true)
                .putBoolean("profileStandard_images", true)
                .putBoolean("profileStandard_adBlock", true)
                .putBoolean("profileStandard_location", false)
                .putBoolean("profileStandard_fingerPrintProtection", true)
                .putBoolean("profileStandard_cookies", false)
                .putBoolean("profileStandard_javascript", true)
                .putBoolean("profileStandard_javascriptPopUp", false)
                .putBoolean("profileStandard_saveHistory", true)
                .putBoolean("profileStandard_camera", false)
                .putBoolean("profileStandard_microphone", false)
                .putBoolean("profileStandard_dom", false)

                .putBoolean("profileProtected_saveData", true)
                .putBoolean("profileProtected_images", true)
                .putBoolean("profileProtected_adBlock", true)
                .putBoolean("profileProtected_location", false)
                .putBoolean("profileProtected_fingerPrintProtection", true)
                .putBoolean("profileProtected_cookies", false)
                .putBoolean("profileProtected_javascript", false)
                .putBoolean("profileProtected_javascriptPopUp", false)
                .putBoolean("profileProtected_saveHistory", true)
                .putBoolean("profileProtected_camera", false)
                .putBoolean("profileProtected_microphone", false)
                .putBoolean("profileProtected_dom", false).apply();
    }

    public void setProfileChanged() {
        sp.edit().putBoolean("profileChanged_saveData", sp.getBoolean(profile + "_saveData", true))
                .putBoolean("profileChanged_images", sp.getBoolean(profile + "_images", true))
                .putBoolean("profileChanged_adBlock", sp.getBoolean(profile + "_adBlock", true))
                .putBoolean("profileChanged_location", sp.getBoolean(profile + "_location", false))
                .putBoolean("profileChanged_fingerPrintProtection", sp.getBoolean(profile + "_fingerPrintProtection", true))
                .putBoolean("profileChanged_cookies", sp.getBoolean(profile + "_cookies", false))
                .putBoolean("profileChanged_javascript", sp.getBoolean(profile + "_javascript", true))
                .putBoolean("profileChanged_javascriptPopUp", sp.getBoolean(profile + "_javascriptPopUp", false))
                .putBoolean("profileChanged_saveHistory", sp.getBoolean(profile + "_saveHistory", true))
                .putBoolean("profileChanged_camera", sp.getBoolean(profile + "_camera", false))
                .putBoolean("profileChanged_microphone", sp.getBoolean(profile + "_microphone", false))
                .putBoolean("profileChanged_dom", sp.getBoolean(profile + "_dom", false))
                .putString("profile", "profileChanged").apply();
    }

    public void putProfileBoolean(String string, TextView dialog_titleProfile, Chip chip_profile_trusted,
                                  Chip chip_profile_standard, Chip chip_profile_protected, Chip chip_profile_changed) {
        switch (string) {
            case "_images":
                sp.edit().putBoolean("profileChanged_images", !sp.getBoolean("profileChanged_images", true)).apply();
                break;
            case "_javascript":
                sp.edit().putBoolean("profileChanged_javascript", !sp.getBoolean("profileChanged_javascript", true)).apply();
                break;
            case "_javascriptPopUp":
                sp.edit().putBoolean("profileChanged_javascriptPopUp", !sp.getBoolean("profileChanged_javascriptPopUp", false)).apply();
                break;
            case "_cookies":
                sp.edit().putBoolean("profileChanged_cookies", !sp.getBoolean("profileChanged_cookies", false)).apply();
                break;
            case "_fingerPrintProtection":
                sp.edit().putBoolean("profileChanged_fingerPrintProtection", !sp.getBoolean("profileChanged_fingerPrintProtection", true)).apply();
                break;
            case "_adBlock":
                sp.edit().putBoolean("profileChanged_adBlock", !sp.getBoolean("profileChanged_adBlock", true)).apply();
                break;
            case "_saveData":
                sp.edit().putBoolean("profileChanged_saveData", !sp.getBoolean("profileChanged_saveData", true)).apply();
                break;
            case "_saveHistory":
                sp.edit().putBoolean("profileChanged_saveHistory", !sp.getBoolean("profileChanged_saveHistory", true)).apply();
                break;
            case "_location":
                sp.edit().putBoolean("profileChanged_location", !sp.getBoolean("profileChanged_location", false)).apply();
                break;
            case "_camera":
                sp.edit().putBoolean("profileChanged_camera", !sp.getBoolean("profileChanged_camera", false)).apply();
                break;
            case "_microphone":
                sp.edit().putBoolean("profileChanged_microphone", !sp.getBoolean("profileChanged_microphone", false)).apply();
                break;
            case "_dom":
                sp.edit().putBoolean("profileChanged_dom", !sp.getBoolean("profileChanged_dom", false)).apply();
                break;
        }
        this.initPreferences("");

        String textTitle;
        switch (Objects.requireNonNull(profile)) {
            case "profileTrusted":
                chip_profile_trusted.setChecked(true);
                chip_profile_standard.setChecked(false);
                chip_profile_protected.setChecked(false);
                chip_profile_changed.setChecked(false);
                textTitle = this.context.getString(R.string.setting_title_profiles_trusted);
                break;
            case "profileStandard":
                chip_profile_trusted.setChecked(false);
                chip_profile_standard.setChecked(true);
                chip_profile_protected.setChecked(false);
                chip_profile_changed.setChecked(false);
                textTitle = this.context.getString(R.string.setting_title_profiles_standard);
                break;
            case "profileProtected":
                chip_profile_trusted.setChecked(false);
                chip_profile_standard.setChecked(false);
                chip_profile_protected.setChecked(true);
                chip_profile_changed.setChecked(false);
                textTitle = this.context.getString(R.string.setting_title_profiles_protected);
                break;
            default:
                chip_profile_trusted.setChecked(false);
                chip_profile_standard.setChecked(false);
                chip_profile_protected.setChecked(false);
                chip_profile_changed.setChecked(true);
                textTitle = this.context.getString(R.string.setting_title_profiles_changed);
                break;
        }
        dialog_titleProfile.setText(textTitle);
    }

    public boolean getBoolean(String string) {
        switch (string) {
            case "_images":
                return sp.getBoolean(profile + "_images", true);
            case "_javascript":
                return sp.getBoolean(profile + "_javascript", true);
            case "_javascriptPopUp":
                return sp.getBoolean(profile + "_javascriptPopUp", false);
            case "_cookies":
                return sp.getBoolean(profile + "_cookies", false);
            case "_fingerPrintProtection":
                return sp.getBoolean(profile + "_fingerPrintProtection", true);
            case "_adBlock":
                return sp.getBoolean(profile + "_adBlock", true);
            case "_saveData":
                return sp.getBoolean(profile + "_saveData", true);
            case "_saveHistory":
                return sp.getBoolean(profile + "_saveHistory", true);
            case "_location":
                return sp.getBoolean(profile + "_location", false);
            case "_camera":
                return sp.getBoolean(profile + "_camera", false);
            case "_microphone":
                return sp.getBoolean(profile + "_microphone", false);
            case "_dom":
                return sp.getBoolean(profile + "_dom", false);
            default:
                return false;
        }
    }

    private synchronized void initAlbum() {
        album.setBrowserController(browserController);
    }

    public synchronized HashMap<String, String> getRequestHeaders() {
        HashMap<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("DNT", "1");
        //  Server-side detection for GlobalPrivacyControl
        requestHeaders.put("Sec-GPC", "1");
        requestHeaders.put("X-Requested-With", "com.duckduckgo.mobile.android");

        profile = sp.getString("profile", "profileStandard");
        if (sp.getBoolean(profile + "_saveData", false)) requestHeaders.put("Save-Data", "on");
        return requestHeaders;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        if (sp.getBoolean("sp_audioBackground", false)) {
            NotificationManager mNotifyMgr = (NotificationManager) this.context.getSystemService(NOTIFICATION_SERVICE);
            if (visibility == View.GONE) {

                Intent intentP = new Intent(this.context, BrowserActivity.class);
                PendingIntent pendingIntent = PendingIntent.getActivity(this.context, 0, intentP, FLAG_IMMUTABLE);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    String name = "Audio background";
                    String description = "Play audio on background -> click to open";
                    int importance = NotificationManager.IMPORTANCE_LOW; //Important for heads-up notification
                    NotificationChannel channel = new NotificationChannel("2", name, importance);
                    channel.setDescription(description);
                    channel.setShowBadge(true);
                    channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                    NotificationManager notificationManager = this.context.getSystemService(NotificationManager.class);
                    notificationManager.createNotificationChannel(channel);
                }

                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this.context, "2")
                        .setSmallIcon(R.drawable.icon_audio)
                        .setAutoCancel(true)
                        .setContentTitle(this.getTitle())
                        .setContentText(this.context.getString(R.string.setting_title_audioBackground))
                        .setContentIntent(pendingIntent); //Set the intent that will fire when the user taps the notification
                Notification buildNotification = mBuilder.build();
                mNotifyMgr.notify(2, buildNotification);
            } else mNotifyMgr.cancel(2);
            super.onWindowVisibilityChanged(View.VISIBLE);
        } else super.onWindowVisibilityChanged(visibility);
    }

    @Override
    public synchronized void stopLoading() {
        stopped = true;
        super.stopLoading();
    }

    public synchronized void reloadWithoutInit() {  //needed for camera usage without deactivating "save_data"
        stopped = false;
        super.reload();
    }

    @Override
    public synchronized void reload() {
        stopped = false;
        this.initPreferences(this.getUrl());
        try {
            this.loadUrl(Objects.requireNonNull(this.getUrl()));
            super.reload();
        } catch (Exception e) {
            Log.i(TAG, "shouldOverrideUrlLoading Exception:" + e);
            NinjaToast.show(context, R.string.app_error);
        }
    }

    @Override
    public synchronized void loadUrl(@NonNull String url) {
        InputMethodManager imm = (InputMethodManager) this.context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(this.getWindowToken(), 0);
        favicon = null;
        stopped = false;

        if (url.startsWith("http://")) {

            GridItem item_01 = new GridItem("https://", R.drawable.icon_https);
            GridItem item_02 = new GridItem( "http://", R.drawable.icon_http);
            GridItem item_03 = new GridItem( context.getString(R.string.app_cancel), R.drawable.icon_close);

            View dialogView = View.inflate(context, R.layout.dialog_menu, null);
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);

            LinearLayout textGroup = dialogView.findViewById(R.id.textGroup);
            TextView menuURL = dialogView.findViewById(R.id.menuURL);
            menuURL.setText(url);
            menuURL.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            menuURL.setSingleLine(true);
            menuURL.setMarqueeRepeatLimit(1);
            menuURL.setSelected(true);
            textGroup.setOnClickListener(v -> {
                menuURL.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                menuURL.setSingleLine(true);
                menuURL.setMarqueeRepeatLimit(1);
                menuURL.setSelected(true);
            });
            TextView menuTitle = dialogView.findViewById(R.id.menuTitle);
            menuTitle.setText(HelperUnit.domain(url));
            TextView message = dialogView.findViewById(R.id.message);
            message.setVisibility(View.VISIBLE);
            message.setText(R.string.toast_unsecured);
            FaviconHelper.setFavicon(context, dialogView, null, R.id.menu_icon, R.drawable.icon_alert);
            builder.setView(dialogView);

            AlertDialog dialog = builder.create();
            dialog.show();
            HelperUnit.setupDialog(context, dialog);

            GridView menu_grid = dialogView.findViewById(R.id.menu_grid);
            final List<GridItem> gridList = new LinkedList<>();
            gridList.add(gridList.size(), item_01);
            gridList.add(gridList.size(), item_02);
            gridList.add(gridList.size(), item_03);
            GridAdapter gridAdapter = new GridAdapter(context, gridList);
            menu_grid.setAdapter(gridAdapter);
            gridAdapter.notifyDataSetChanged();
            menu_grid.setOnItemClickListener((parent, view, position, id) -> {
                switch (position) {
                    case 0:
                        dialog.cancel();
                        String finalURL = url.replace("http://", "https://");
                        sp.edit().putString("urlToLoad", finalURL).apply();
                        initPreferences(BrowserUnit.queryWrapper(context, finalURL));
                        super.loadUrl(BrowserUnit.queryWrapper(context, finalURL), getRequestHeaders());
                        break;
                    case 1:
                        dialog.cancel();
                        sp.edit().putString("urlToLoad", url).apply();
                        initPreferences(BrowserUnit.queryWrapper(context, url));
                        super.loadUrl(BrowserUnit.queryWrapper(context, url), getRequestHeaders());
                        break;
                    case 2:
                        dialog.cancel();
                        super.loadUrl(BrowserUnit.queryWrapper(context, "about:blank"), getRequestHeaders());
                        break;
                }
            });
        } else {
            sp.edit().putString("urlToLoad", url).apply();
            initPreferences(BrowserUnit.queryWrapper(context, url));
            super.loadUrl(BrowserUnit.queryWrapper(context, url), getRequestHeaders());
        }

    }

    @Override
    public View getAlbumView() {
        return album.getAlbumView();
    }

    public void setAlbumTitle(String title, String url) {
        album.setAlbumTitle(title, url);
        FaviconHelper.setFavicon(context, getAlbumView(), url, R.id.faviconView, R.drawable.icon_image_broken);
    }

    @Override
    public synchronized void activate() {
        requestFocus();
        foreground = true;
        album.activate();
    }

    @Override
    public synchronized void deactivate() {
        clearFocus();
        foreground = false;
        album.deactivate();
    }

    public synchronized void updateTitle(int progress) {
        if (foreground && !stopped) browserController.updateProgress(progress);
        else if (foreground) browserController.updateProgress(BrowserUnit.LOADING_STOPPED);
    }

    public synchronized void updateTitle(String title, String url) {
        album.setAlbumTitle(title, url);
    }

    public synchronized void updateFavicon(String url) {
        FaviconHelper.setFavicon(context, getAlbumView(), url, R.id.faviconView, R.drawable.icon_image_broken);
    }

    @Override
    public synchronized void destroy() {
        stopLoading();
        onPause();
        clearHistory();
        setVisibility(GONE);
        removeAllViews();
        super.destroy();
    }

    public boolean isDesktopMode() {
        return desktopMode;
    }

    public boolean isFingerPrintProtection() {
        return fingerPrintProtection;
    }

    public boolean isHistory() {
        return history;
    }

    public boolean isAdBlock() {
        return adBlock;
    }

    public boolean isSaveData() {
        return saveData;
    }

    public boolean isCamera() {
        return camera;
    }

    public String getUserAgent(boolean desktopMode) {
        String mobilePrefix = "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + ")";
        String desktopPrefix = "Mozilla/5.0 (X11; Linux " + System.getProperty("os.arch") + ")";

        String newUserAgent = WebSettings.getDefaultUserAgent(context);
        String prefix = newUserAgent.substring(0, newUserAgent.indexOf(")") + 1);

        if (desktopMode) {
            try {
                newUserAgent = newUserAgent.replace(prefix, desktopPrefix);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            try {
                newUserAgent = newUserAgent.replace(prefix, mobilePrefix);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        //Override UserAgent if own UserAgent is defined
        if (!sp.contains("userAgentSwitch")) {  //if new switch_text_preference has never been used initialize the switch
            if (Objects.requireNonNull(sp.getString("sp_userAgent", "")).equals("")) {
                sp.edit().putBoolean("userAgentSwitch", false).apply();
            } else sp.edit().putBoolean("userAgentSwitch", true).apply();
        }

        String ownUserAgent = sp.getString("sp_userAgent", "");
        if (!ownUserAgent.equals("") && (sp.getBoolean("userAgentSwitch", false)))
            newUserAgent = ownUserAgent;
        return newUserAgent;
    }

    public void toggleDesktopMode(boolean reload) {
        desktopMode = !desktopMode;
        String newUserAgent = getUserAgent(desktopMode);
        getSettings().setUserAgentString(newUserAgent);
        getSettings().setUseWideViewPort(desktopMode);
        getSettings().setLoadWithOverviewMode(desktopMode);
        if (reload) reload();
    }

    public void toggleNightMode() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettings s = this.getSettings();
            boolean allowed = sp.getBoolean("setAlgorithmicDarkeningAllowed", true);
            if (allowed) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, false);
                sp.edit().putBoolean("setAlgorithmicDarkeningAllowed", false).apply();
            } else {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, true);
                sp.edit().putBoolean("setAlgorithmicDarkeningAllowed", true).apply();
            }
        }
    }

    public void resetFavicon() {
        this.favicon = null;
    }

    @Nullable
    @Override
    public Bitmap getFavicon() {
        return favicon;
    }

    public void setFavicon(Bitmap favicon) {
        this.favicon = favicon;
        //Save faviconView for existing bookmarks or start site entries
        FaviconHelper faviconHelper = new FaviconHelper(context);
        RecordAction action = new RecordAction(context);
        action.open(false);
        List<Record> list;
        list = action.listEntries((Activity) context);
        action.close();
        for (Record listItem : list) {
            if (listItem.getURL().equals(getUrl()) && faviconHelper.getFavicon(listItem.getURL()) == null)
                faviconHelper.addFavicon(this.context, getUrl(), getFavicon());
        }
    }

    public void setStopped(boolean stopped) {
        this.stopped = stopped;
    }

    public String getProfile() {
        return profile;
    }

    public AlbumController getPredecessor() {
        return predecessor;
    }

    public void setPredecessor(AlbumController predecessor) {
        this.predecessor = predecessor;
    }

    public interface OnScrollChangeListener {
        /**
         * Called when the scroll position of a view changes.
         *
         * @param scrollY    Current vertical scroll origin.
         * @param oldScrollY Previous vertical scroll origin.
         */
        void onScrollChange(int scrollY, int oldScrollY);
    }
}