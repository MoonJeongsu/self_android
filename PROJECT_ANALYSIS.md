# 스마트 자가진단 (self-android) 프로젝트 분석

> 작성일: 2026-05-20  
> 패키지: `com.smmit.self`  
> 앱 이름: **스마트 자가진단**

---

## 1. 프로젝트 개요

| 항목 | 내용 |
|------|------|
| 유형 | Android 네이티브 **하이브리드(WebView) 앱** |
| 목적 | 원격 웹 서비스(`http://210.120.6.98:7080`)를 WebView로 감싸 자가진단 서비스 제공 |
| 모듈 | 단일 모듈 `:app` |
| UI 방식 | XML 레이아웃 + `WebView` (Jetpack Compose는 빌드만 활성화, 실제 화면 미사용) |
| 푸시 | Firebase Cloud Messaging (FCM) |
| 백엔드 연동 | 웹 URL 하드코딩, JS ↔ 네이티브 브릿지로 권한·FCM 토큰 연동 |

앱의 핵심 역할은 **웹 앱 컨테이너**입니다. 화면·비즈니스 로직의 대부분은 서버 측 웹 애플리케이션에 있고, Android 쪽은 권한 요청, 위치(WebView Geolocation), FCM 알림, 뒤로가기 UX 등 **플랫폼 기능**을 담당합니다.

---

## 2. 디렉터리 구조

```
self-android/
├── app/                              # 애플리케이션 모듈
│   ├── build.gradle.kts              # 앱 빌드·의존성
│   ├── google-services.json          # Firebase 프로젝트 설정
│   ├── proguard-rules.pro            # 릴리스 난독화 규칙 (JSBridge 보존)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/smmit/self/
│       │   │   ├── MainActivity.kt           # 진입점, WebView, JSBridge
│       │   │   ├── MyFirebaseMessagingService.kt
│       │   │   ├── FCMTokenStore.kt
│       │   │   └── ui/theme/                 # Compose 테마 (미사용)
│       │   │       ├── Color.kt
│       │   │       ├── Theme.kt
│       │   │       └── Type.kt
│       │   └── res/
│       │       ├── layout/activity_main.xml
│       │       ├── values/ (strings, colors, themes)
│       │       ├── drawable/ (런처 아이콘)
│       │       ├── mipmap-anydpi-v26/
│       │       └── xml/ (backup, data extraction)
│       ├── test/                     # JUnit 단위 테스트 (템플릿)
│       └── androidTest/              # Instrumented 테스트 (템플릿)
├── build.gradle.kts                  # 루트 플러그인 버전
├── settings.gradle.kts               # 프로젝트명, 모듈 include
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew / gradlew.bat
├── local.properties                  # SDK 경로 (gitignore, 로컬 전용)
└── .idea/                            # Android Studio IDE 설정
```

---

## 3. 빌드·환경 설정

### 3.1 툴체인 (최근 업데이트 기준)

| 구성 | 버전 |
|------|------|
| Gradle | 8.11.1 |
| Android Gradle Plugin | 8.8.2 |
| Kotlin | 2.1.10 |
| Compose Compiler | Kotlin Compose Plugin 2.1.10 |
| compileSdk / targetSdk | 35 |
| minSdk | 24 (Android 7.0+) |
| JVM | 17 |

### 3.2 주요 의존성

- **UI**: AppCompat, Compose BOM `2025.08.01` (Compose UI는 의존성만 존재)
- **Firebase**: BOM `33.12.0` — `firebase-messaging-ktx`, `firebase-analytics-ktx`
- **테스트**: JUnit 4, AndroidX Test, Espresso, Compose UI Test

### 3.3 빌드 명령

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (서명 설정 필요 시 별도 구성)
./gradlew assembleRelease
```

출력 APK: `app/build/outputs/apk/debug/app-debug.apk`

### 3.4 로컬 요구사항

- JDK 17
- Android SDK (compileSdk 35)
- `local.properties`에 `sdk.dir` 설정 (저장소에 포함되지 않음)

---

## 4. 아키텍처

### 4.1 고수준 구조

```
┌─────────────────────────────────────────────────────────┐
│                    MainActivity                          │
│  ┌───────────────────────────────────────────────────┐  │
│  │              WebView (전체 화면)                    │  │
│  │   http://210.120.6.98:7080/{path}                  │  │
│  │                                                     │  │
│  │   JS: AndroidBridge.grantPermission()              │  │
│  │       AndroidBridge.checkPermission()              │  │
│  │       AndroidBridge.getFcmToken()                  │  │
│  └───────────────────────────────────────────────────┘  │
│         ▲ Geolocation (WebChromeClient)                  │
│         ▲ 권한 (Activity Result API)                   │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│           MyFirebaseMessagingService                     │
│   onNewToken → SharedPreferences("fcm")                  │
│   onMessageReceived → 시스템 알림 → MainActivity           │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
              Firebase (smart-self-df2ca)
```

### 4.2 레이어 분류

| 레이어 | 구현 | 비고 |
|--------|------|------|
| Presentation | `MainActivity` + `activity_main.xml` | WebView 단일 화면 |
| Bridge | `JSBridge` (`@JavascriptInterface`) | 웹↔네이티브 계약 |
| Platform | 권한, Geolocation, FCM, 알림 | Android API |
| Domain / Data | **없음** | Room, Repository, ViewModel 미사용 |
| Remote | 웹 서버 | REST 네이티브 클라이언트 없음 |

전형적인 **Thin Native Shell** 패턴입니다. MVVM, Hilt/Dagger, Navigation Component 등은 사용하지 않습니다.

---

## 5. 핵심 컴포넌트

### 5.1 MainActivity

**역할**: 앱 진입점, WebView 호스팅, 권한·브릿지 처리.

**시작 흐름**

1. `onCreate` → `activity_main.xml` 로드
2. WebView 설정: JavaScript, DOM Storage, Geolocation, 멀티 윈도우
3. `AndroidBridge`를 `window.AndroidBridge`로 등록
4. `loadUrl("/splash")` → `http://210.120.6.98:7080/splash`

**권한 (`requiredPermissions`)**

| API 레벨 | 권한 |
|----------|------|
| 공통 | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` |
| Android 13+ (Tiramisu) | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `POST_NOTIFICATIONS` |
| Android 12 이하 | `READ_EXTERNAL_STORAGE` |

**웹 라우팅 (권한 상태에 따라)**

| 조건 | 로드 경로 |
|------|-----------|
| 앱 시작 | `/splash` |
| `checkPermission()` + 전체 허용 | `/` |
| `checkPermission()` + 미허용 | `/intro` |
| `grantPermission()` 결과 전체 허용 | `/` |
| `grantPermission()` 결과 일부 거부 | `/intro` |

**뒤로가기**: 2초 이내 두 번 누르면 종료 (WebView 히스토리 back 미구현).

**Geolocation**: `hasAllPermissions()`가 true일 때만 WebView 위치 요청 허용.

### 5.2 JSBridge (JavascriptInterface)

웹에서 `AndroidBridge` 객체로 호출합니다.

| 메서드 | 설명 | 반환 |
|--------|------|------|
| `grantPermission()` | 미허용 시 시스템 권한 다이얼로그 표시 | void |
| `checkPermission()` | 권한 여부에 따라 `/` 또는 `/intro` 로드 | void |
| `getFcmToken()` | SharedPreferences 또는 FCM에서 토큰 조회 | `String?` |

토큰이 없으면 `FirebaseMessaging.getInstance().token`을 비동기로 요청하지만, **콜백 완료 전에는 `null`을 반환**할 수 있습니다. 웹 측에서 재시도 로직이 필요할 수 있습니다.

### 5.3 FCMTokenStore

```kotlin
object FCMTokenStore {
    @Volatile var fcmToken: String? = null
}
```

메모리 캐시용 싱글톤. `SharedPreferences("fcm")`의 `deviceToken`과 동기화됩니다.

### 5.4 MyFirebaseMessagingService

| 콜백 | 동작 |
|------|------|
| `onNewToken` | `SharedPreferences("fcm").deviceToken` 저장 (서버 전송 로직은 TODO) |
| `onMessageReceived` | 알림 표시, 탭 시 `MainActivity` 실행 (`fromPush=true` extra) |

기본 알림 문구: *"오늘 자가진단을 아직 진행하지 않았습니다."*

### 5.5 ui/theme (Compose)

`스마트자가진단Theme`, Material3 색상·타이포그래피가 정의되어 있으나, **MainActivity 및 레이아웃에서 참조하지 않습니다.** Android Studio 템플릿 잔존 코드로 보이며, `buildFeatures.compose = true`만 유지된 상태입니다.

---

## 6. AndroidManifest 요약

### 6.1 권한

- `INTERNET`
- `POST_NOTIFICATIONS` (API 33+)
- 위치: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- 미디어: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE` (maxSdk 32)

### 6.2 Application 속성

- `usesCleartextTraffic="true"` — **HTTP(비암호화) 통신 허용** (현재 베이스 URL이 `http://`)
- `allowBackup="true"`
- FCM 서비스: `MyFirebaseMessagingService` (`exported=false`)

### 6.3 Activity

- `MainActivity`: LAUNCHER, `Theme.AppCompat.NoActionBar`

---

## 7. 리소스

| 리소스 | 용도 |
|--------|------|
| `activity_main.xml` | 전체 화면 `WebView` |
| `strings.xml` | `app_name`: 스마트 자가진단 |
| `themes.xml` | `Theme.MyApp` (AppCompat Light) |
| `ic_launcher_*` | 앱 아이콘 (adaptive icon) |
| `backup_rules.xml`, `data_extraction_rules.xml` | 백업 규칙 (기본 템플릿) |

---

## 8. Firebase 연동

- **프로젝트 ID**: `smart-self-df2ca`
- **패키지명**: `com.smmit.self` (`google-services.json`과 일치)
- **기능**: Messaging, Analytics
- **토큰 저장소**: `SharedPreferences` 파일명 `fcm`, 키 `deviceToken`

---

## 9. 보안·운영 관련 사항

| 항목 | 현재 상태 | 권장 |
|------|-----------|------|
| 통신 | HTTP + cleartext 허용 | HTTPS 전환, `usesCleartextTraffic` 제거 또는 Network Security Config |
| 서버 URL | `MainActivity`에 하드코딩 | `BuildConfig` / flavors / remote config |
| JSBridge | `@JavascriptInterface` 공개 | ProGuard keep 규칙 있음; 웹 도메인 화이트리스트(`@JavascriptInterface` + URL 검증) 검토 |
| 릴리스 난독화 | `isMinifyEnabled = false` | 스토어 배포 시 R8 활성화 검토 |
| API 키 | `google-services.json`에 포함 | 저장소 공개 시 Firebase 콘솔 제한 설정 |

---

## 10. 알려진 이슈·개선 포인트

### 10.1 누락/불완전 구현

1. **`R.drawable.ic_notification` 미존재**  
   `MyFirebaseMessagingService`에서 알림 small icon으로 참조하나, `res/drawable/`에 해당 리소스 파일이 없습니다. 푸시 수신 시 런타임 오류 가능성이 있습니다.

2. **NotificationChannel 미생성**  
   Android 8.0+에서는 `default_channel` 채널을 `NotificationManager`에 등록해야 알림이 정상 표시됩니다. 채널 생성 코드가 주석 처리·누락된 것으로 보입니다.

3. **FCM 토큰 서버 전송**  
   `onNewToken`에 "서버 전송 로직 필요 시 호출" TODO만 존재합니다.

### 10.2 Deprecated API

- `onBackPressed()` → `OnBackPressedDispatcher` 권장
- `WebSettings.databaseEnabled` deprecated
- Compose `Theme.kt`의 `statusBarColor` deprecated

### 10.3 UX / 동작

- 뒤로가기 시 WebView `canGoBack()` 미처리 → 웹 내비게이션 대신 앱 종료만 동작
- `getFcmToken()` 비동기 토큰 fetch 시 즉시 `null` 반환 가능

### 10.4 테스트

- `ExampleUnitTest`, `ExampleInstrumentedTest`만 존재 (템플릿 수준)
- WebView·JSBridge·FCM에 대한 실질적 테스트 없음

### 10.5 미사용 코드

- `ui/theme/*` (Compose)
- `import android.util.Log` (MainActivity, 미사용)

---

## 11. 웹 연동 계약 (요약)

웹 애플리케이션은 다음을 가정합니다.

```javascript
// 권한 요청 UI에서
AndroidBridge.grantPermission();

// 앱 시작/복귀 시 권한 확인
AndroidBridge.checkPermission();

// 서버에 디바이스 등록 시
const token = AndroidBridge.getFcmToken();
```

**베이스 URL**: `http://210.120.6.98:7080`

| 경로 | 용도 (추정) |
|------|-------------|
| `/splash` | 스플래시 |
| `/intro` | 권한 안내 |
| `/` | 메인(권한 허용 후) |

---

## 12. 데이터 저장

| 저장소 | 키 | 내용 |
|--------|-----|------|
| SharedPreferences `fcm` | `deviceToken` | FCM 등록 토큰 |
| `FCMTokenStore` (메모리) | `fcmToken` | 런타임 캐시 |

그 외 로컬 DB, 파일 캐시, EncryptedSharedPreferences는 사용하지 않습니다.

---

## 13. 배포 정보

| 항목 | 값 |
|------|-----|
| applicationId | `com.smmit.self` |
| versionCode | 1 |
| versionName | 1.0 |

---

## 14. 요약

**스마트 자가진단** Android 앱은 단일 `WebView` 기반 하이브리드 앱으로, 자가진단 웹 서비스를 감싸며 **위치·미디어·알림 권한**과 **FCM 푸시**를 네이티브에서 처리합니다. 코드베이스는 소규모(핵심 Kotlin 파일 3개 + Compose 템플릿 잔여)이며, 별도 아키텍처 레이어 없이 Activity 중심으로 구성되어 있습니다.

운영·품질 측면에서는 **HTTPS 전환**, **알림 리소스·채널 보완**, **FCM 토큰 서버 연동**, **WebView 뒤로가기**, **미사용 Compose 제거 또는 실제 UI 전환** 등이 우선 검토 대상입니다.
