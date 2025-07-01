package com.smmit.self

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    /** 새 토큰 수신 → 서버로 전송(로그인 이후에도 갱신 필요) */
    override fun onNewToken(token: String) {
        // ex) token을 SharedPreferences에 저장
        getSharedPreferences("fcm", MODE_PRIVATE)
            .edit().putString("deviceToken", token).apply()
        // 서버 전송 로직 필요 시 호출
    }

    /** 메시지 수신 → 알림 표시 */
    override fun onMessageReceived(msg: RemoteMessage) {
        val title = msg.notification?.title ?: ""
        val body  = msg.notification?.body  ?: "오늘 자가진단을 아직 진행하지 않았습니다."

        /* 1) 터치 시 열릴 인텐트 */
        val intent = Intent(this, MainActivity::class.java).apply {
            // 이미 떠 있는 스택을 모두 지우고 새로 실행 (선택)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            // 필요하면 메시지 내용을 엑스트라로 전달
            putExtra("fromPush", true)
        }

        /* 2) PendingIntent – API 31+ FLAG_IMMUTABLE 필수 */
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /* 3) 알림 채널(>=O) 만들기 – 기존 코드 그대로 */

        /* 4) 알림 빌드 */
        val notif = NotificationCompat.Builder(this, "default_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)          // 터치 시 자동 사라짐
            .setContentIntent(pending)    // ★ 여기!
            .build()

        /* 5) 표시 */
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        NotificationManagerCompat.from(this)
            .notify(System.currentTimeMillis().toInt(), notif)
    }
}
