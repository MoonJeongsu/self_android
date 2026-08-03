package com.smmit.self2

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var lastBackPressed: Long = 0
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoUri: Uri? = null

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

        webView.webViewClient = WebViewClient()
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
        val base = "https://smartjaga.gongjak.dev"
        webView.loadUrl("$base$path")
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
