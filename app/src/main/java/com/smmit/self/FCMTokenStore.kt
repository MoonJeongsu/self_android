package com.smmit.self

object FCMTokenStore {
    @Volatile
    var fcmToken: String? = null
}
