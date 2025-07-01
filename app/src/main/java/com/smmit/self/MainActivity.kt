package com.smmit.self

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

import android.webkit.JavascriptInterface
import android.widget.Toast
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var lastBackPressed: Long = 0

    /** 퍼미션 목록 */
    private val requiredPermissions: Array<String> by lazy {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ : READ_MEDIA_*
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
            // 모든 퍼미션이 허용되었는지 확인
            val grantedAll = results.all { it.value }



            loadUrl(if (grantedAll) "/" else "/intro")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.myWebView)

        // WebView 기본 설정
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled   = true
            setSupportMultipleWindows(true)
        }

        webView.webViewClient  = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            /** 위치 정보 요청 허용 */
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                val allowed = hasAllPermissions()
                callback.invoke(origin, allowed, false)
            }
        }

        webView.addJavascriptInterface(JSBridge(), "AndroidBridge")

        loadUrl("/splash")
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
                // 최초 퍼미션 체크 → 없으면 요청
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
                    // 원하는 시점에 WebView로 pushToken 전달
                }
            }

            return FCMTokenStore.fcmToken
        }
    }

    /** WebView 로드 경로 결정 */
    private fun loadUrl(path: String) {
        // 베이스 URL만 바꿔도 되고, 절대 경로여도 무방
        val base = "http://210.120.6.98:7080"
        webView.loadUrl("$base$path")
    }


    /** 모든 필수 퍼미션 보유 여부 */
    private fun hasAllPermissions(): Boolean =
        requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) ==
                    PackageManager.PERMISSION_GRANTED
        }

    override fun onBackPressed() {
        // 3) 히스토리가 없을 때는 두 번 눌러 종료 로직
        val now = System.currentTimeMillis()
        if (now - lastBackPressed > 2000) {        // 2초 초과 = 첫 번째 눌림
            lastBackPressed = now
            Toast.makeText(
                this,
                "한 번 더 누르시면 앱이 종료됩니다",
                Toast.LENGTH_SHORT
            ).show()
        } else {                                   // 2초 이내 = 두 번째 눌림
            super.onBackPressed()                  // 또는 super.onBackPressed()
            finish()
        }
    }
}
