package com.smmit.self

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : AppCompatActivity() {

    private lateinit var myWebView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // XML 레이아웃 적용

        myWebView = findViewById(R.id.myWebView)

        // WebView 설정
        myWebView.webViewClient = WebViewClient()
        myWebView.settings.javaScriptEnabled = true
        myWebView.settings.domStorageEnabled = true   // 로컬 스토리지 허용
        myWebView.settings.databaseEnabled = true
        myWebView.settings.setSupportMultipleWindows(true)

        // 페이지 로드
        myWebView.loadUrl("http://summit-jaga.devduke.co.kr/intro")
    }

    // 뒤로가기 시, WebView 내부에서 뒤로가기
    override fun onBackPressed() {
        if (myWebView.canGoBack()) {
            myWebView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
