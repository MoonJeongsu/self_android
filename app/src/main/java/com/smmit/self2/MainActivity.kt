package com.smmit.self2

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
    private var hasLoadedInitialUrl = false

    /** 퍼미션 목록 */
    private val requiredPermissions: Array<String> by lazy {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.READ_MEDIA_IMAGES
            list += Manifest.permission.READ_MEDIA_VIDEO
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
            val grantedAll = results.all { it.value }
            loadUrl(if (grantedAll) "/" else "/intro")
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
            databaseEnabled = true
            setSupportMultipleWindows(true)
            allowFileAccess = true
            // 시스템 글자 크기와 무관하게 UI 배율 고정 (Z Fold / Z Flip 레이아웃 깨짐 방지)
            textZoom = 100
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true && !isNetworkAvailable()) {
                    showNetworkDialog()
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

        loadUrl("/splash")
    }

    override fun onResume() {
        super.onResume()
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
    }

    private fun loadUrl(path: String) {
        pendingPath = path
        if (!isNetworkAvailable()) {
            showNetworkDialog()
            return
        }
        dismissNetworkDialog()
        val base = "https://smartjaga.gongjak.dev"
        webView.loadUrl("$base$path")
        hasLoadedInitialUrl = true
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

    override fun onBackPressed() {
        val now = System.currentTimeMillis()
        if (now - lastBackPressed > 2000) {
            lastBackPressed = now
            Toast.makeText(
                this,
                "한 번 더 누르시면 앱이 종료됩니다",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            super.onBackPressed()
            finish()
        }
    }
}
