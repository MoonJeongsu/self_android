package com.smmit.self2

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.ViewTreeObserver
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var lastBackPressed: Long = 0
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoUri: Uri? = null
    private var pendingPath: String = "/splash"
    private var networkDialog: AlertDialog? = null
    private var permissionGuidanceDialog: AlertDialog? = null
    private var hasLoadedInitialUrl = false
    private var awaitingReturnFromAppSettings = false
    private var hasReceivedPage = false
    private var lastLoadWidth = 0
    private var lastLoadHeight = 0
    private var layoutListenerAttached = false
    private var loadErrorDialog: AlertDialog? = null

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (!isWebViewLaidOut()) return@OnGlobalLayoutListener
        detachLayoutListener()
        loadUrl(pendingPath)
    }

    /** 퍼미션 목록 */
    private val requiredPermissions: Array<String> by lazy {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        } else {
            list += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        list.toTypedArray()
    }

    /** 퍼미션 런처 */
    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            if (results.all { it.value }) {
                loadUrl("/")
                return@registerForActivityResult
            }

            val deniedPermissions = results
                .filter { !it.value }
                .map { it.key }

            showPermissionGuidanceDialog(deniedPermissions)
        }

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback
            filePathCallback = null

            if (callback == null) return@registerForActivityResult

            if (result.resultCode == Activity.RESULT_OK) {
                val dataUri = result.data?.data
                val uri = dataUri ?: cameraPhotoUri
                if (uri != null) {
                    callback.onReceiveValue(arrayOf(uri))
                } else {
                    callback.onReceiveValue(null)
                }
            } else {
                callback.onReceiveValue(null)
            }
            cameraPhotoUri = null
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchFileChooser()
            } else {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 시스템 상태바/네비바와 WebView 콘텐츠가 겹치지 않도록 처리
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.myWebView)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(true)
            allowFileAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            // 시스템 글자 크기와 무관하게 UI 배율 고정 (Z Fold / Z Flip 레이아웃 깨짐 방지)
            textZoom = 100
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                hasReceivedPage = true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame != true) return
                hasReceivedPage = false
                if (!isNetworkAvailable()) {
                    showNetworkDialog()
                } else {
                    showLoadErrorDialog()
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                val allowed = hasAllPermissions()
                callback.invoke(origin, allowed, false)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.CAMERA
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    return true
                }

                launchFileChooser(fileChooserParams)
                return true
            }
        }

        webView.addJavascriptInterface(JSBridge(), "AndroidBridge")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = System.currentTimeMillis()
                if (now - lastBackPressed > 2000) {
                    lastBackPressed = now
                    Toast.makeText(
                        this@MainActivity,
                        "한 번 더 누르시면 앱이 종료됩니다",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    finish()
                }
            }
        })

        val restored = savedInstanceState?.let { webView.restoreState(it) }
        if (restored == null) {
            loadUrl("/splash")
        } else {
            pendingPath = savedInstanceState.getString(STATE_PENDING_PATH, "/splash") ?: "/splash"
            hasLoadedInitialUrl = true
            hasReceivedPage = true
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        webView.post { reloadIfPaintMissing() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) {
            webView.saveState(outState)
            outState.putString(STATE_PENDING_PATH, pendingPath)
        }
    }

    override fun onDestroy() {
        detachLayoutListener()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (permissionGuidanceDialog?.isShowing == true) return

        if (awaitingReturnFromAppSettings) {
            awaitingReturnFromAppSettings = false
            if (hasAllPermissions()) {
                loadUrl("/")
            } else {
                showPermissionGuidanceDialog(deniedRequiredPermissions())
            }
            return
        }

        if (isNetworkAvailable() && (networkDialog?.isShowing == true || !hasLoadedInitialUrl)) {
            dismissNetworkDialog()
            loadUrl(pendingPath)
        }
    }

    private fun launchFileChooser(params: WebChromeClient.FileChooserParams? = null) {
        val contentIntent = params?.createIntent()?.apply {
            if (type.isNullOrEmpty()) type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        } ?: Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }

        val intentList = mutableListOf<Intent>()
        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (captureIntent.resolveActivity(packageManager) != null) {
            val photoFile = File(cacheDir, "kit_${System.currentTimeMillis()}.jpg")
            cameraPhotoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri)
            intentList.add(captureIntent)
        }

        val chooserIntent = Intent.createChooser(contentIntent, "사진 선택")
        if (intentList.isNotEmpty()) {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentList.toTypedArray())
        }
        fileChooserLauncher.launch(chooserIntent)
    }

    inner class JSBridge {
        @JavascriptInterface
        fun grantPermission() {
            runOnUiThread {
                if (!hasAllPermissions()) {
                    permissionLauncher.launch(requiredPermissions)
                } else {
                    loadUrl("/")
                }
            }
        }

        @JavascriptInterface
        fun checkPermission() {
            runOnUiThread {
                if (hasAllPermissions()) {
                    loadUrl("/")
                } else {
                    loadUrl("/intro")
                }
            }
        }

        @JavascriptInterface
        fun getFcmToken(): String? {
            FCMTokenStore.fcmToken = getSharedPreferences("fcm", MODE_PRIVATE)
                .getString("deviceToken", null)

            if (FCMTokenStore.fcmToken == null) {
                FirebaseMessaging.getInstance().token.addOnSuccessListener {
                    FCMTokenStore.fcmToken = it
                    getSharedPreferences("fcm", MODE_PRIVATE)
                        .edit().putString("deviceToken", it).apply()
                }
            }

            return FCMTokenStore.fcmToken
        }

        /** 웹 마이페이지 앱 버전 표시용 (versionName, 예: 1.0.2) */
        @JavascriptInterface
        fun getAppVersion(): String {
            return try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: ""
            } catch (_: Exception) {
                ""
            }
        }
    }

    private fun loadUrl(path: String) {
        pendingPath = path
        if (!isNetworkAvailable()) {
            showNetworkDialog()
            return
        }
        dismissNetworkDialog()
        dismissLoadErrorDialog()
        if (!isWebViewLaidOut()) {
            attachLayoutListener()
            return
        }
        val base = "http://43.201.9.18:8082"
        hasReceivedPage = false
        lastLoadWidth = webView.width
        lastLoadHeight = webView.height
        webView.loadUrl("$base$path")
        hasLoadedInitialUrl = true
    }

    private fun isWebViewLaidOut(): Boolean =
        ::webView.isInitialized && webView.width > 0 && webView.height > 0

    private fun attachLayoutListener() {
        if (!::webView.isInitialized || layoutListenerAttached) return
        layoutListenerAttached = true
        webView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    private fun detachLayoutListener() {
        if (!::webView.isInitialized || !layoutListenerAttached) return
        webView.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        layoutListenerAttached = false
    }

    private fun reloadIfPaintMissing() {
        if (!::webView.isInitialized) return
        if (!isWebViewLaidOut()) {
            attachLayoutListener()
            return
        }
        val loadedAtZeroSize = lastLoadWidth <= 0 || lastLoadHeight <= 0
        if (!hasReceivedPage || loadedAtZeroSize) {
            loadUrl(pendingPath)
        }
    }

    private fun showLoadErrorDialog() {
        if (isFinishing || isDestroyed) return
        if (loadErrorDialog?.isShowing == true || networkDialog?.isShowing == true) return

        loadErrorDialog = AlertDialog.Builder(this)
            .setTitle(R.string.webview_load_error_title)
            .setMessage(R.string.webview_load_error_message)
            .setCancelable(false)
            .setPositiveButton(R.string.webview_load_error_retry) { _, _ ->
                loadUrl(pendingPath)
            }
            .setOnDismissListener {
                loadErrorDialog = null
            }
            .create()
            .also { it.show() }
    }

    private fun dismissLoadErrorDialog() {
        loadErrorDialog?.dismiss()
        loadErrorDialog = null
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                )
    }

    private fun showNetworkDialog() {
        if (isFinishing || isDestroyed) return
        if (networkDialog?.isShowing == true) return

        networkDialog = AlertDialog.Builder(this)
            .setTitle("네트워크 연결 필요")
            .setMessage("네트워크에 연결되어 있지 않습니다.\n연결 후 다시 시도해 주세요.")
            .setCancelable(false)
            .setPositiveButton("다시 시도") { _, _ ->
                if (isNetworkAvailable()) {
                    loadUrl(pendingPath)
                } else {
                    showNetworkDialog()
                }
            }
            .setNeutralButton("설정") { _, _ ->
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
            .setOnDismissListener {
                networkDialog = null
            }
            .create()
            .also { it.show() }
    }

    private fun dismissNetworkDialog() {
        networkDialog?.dismiss()
        networkDialog = null
    }

    private fun hasAllPermissions(): Boolean =
        requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) ==
                    PackageManager.PERMISSION_GRANTED
        }

    private fun deniedRequiredPermissions(): List<String> =
        requiredPermissions.filter { perm ->
            ContextCompat.checkSelfPermission(this, perm) !=
                    PackageManager.PERMISSION_GRANTED
        }

    private fun koreanPermissionName(permission: String): String? {
        return when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION ->
                getString(R.string.permission_name_location)
            Manifest.permission.CAMERA ->
                getString(R.string.permission_name_camera)
            Manifest.permission.POST_NOTIFICATIONS ->
                getString(R.string.permission_name_notifications)
            Manifest.permission.READ_EXTERNAL_STORAGE ->
                getString(R.string.permission_name_storage)
            else -> null
        }
    }

    private fun showPermissionGuidanceDialog(permissions: Collection<String>) {
        if (isFinishing || isDestroyed) return
        if (permissionGuidanceDialog?.isShowing == true) return

        val names = permissions
            .mapNotNull(::koreanPermissionName)
            .distinct()
            .joinToString(", ")
            .ifBlank { getString(R.string.permission_name_storage) }

        permissionGuidanceDialog = AlertDialog.Builder(this)
            .setTitle(R.string.permission_guidance_title)
            .setMessage(getString(R.string.permission_guidance_message, names))
            .setCancelable(false)
            .setPositiveButton(R.string.permission_guidance_confirm) { _, _ ->
                openAppSettings()
            }
            .setOnDismissListener {
                permissionGuidanceDialog = null
            }
            .create()
            .also { it.show() }
    }

    private fun openAppSettings() {
        awaitingReturnFromAppSettings = true
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        startActivity(intent)
    }

    companion object {
        private const val STATE_PENDING_PATH = "pending_path"
    }

}
