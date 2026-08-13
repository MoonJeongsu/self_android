# iOS 브릿지 산출물 — 앱 버전 전달

self-web 마이페이지에 앱 버전을 표시하기 위해, Android와 동일하게 JS 브릿스를 노출해 주세요.

## Android 구현 (참고)

- 브릿지 객체명: `AndroidBridge` (`webview.addJavascriptInterface(..., "AndroidBridge")`)
- 메서드: `getAppVersion(): String`
- 반환값: 앱 `versionName` 문자열 (예: `"1.0.2"`)
- 실패/없으면: `""`

## iOS 요청 사항

웹은 아래를 호출합니다.

```js
window.AndroidBridge.getAppVersion()
```

| 항목 | 내용 |
|------|------|
| 객체명 | `AndroidBridge` (웹 호환을 위해 Android와 동일 권장) |
| 메서드 | `getAppVersion()` |
| 반환 타입 | `string` (동기 반환) |
| 반환 예시 | `"1.0.2"` |
| 미지원/오류 시 | `""` |

### 표시 UI (self-web)

- 화면: 마이페이지 하단
- 문구: `앱 버전 {versionName}`
- 버전이 빈 문자열이면 영역을 숨김

### 참고

- FCM용 `getFcmToken()`과 별개입니다.
- Firebase `GoogleService-Info.plist`와 무관하며, 앱 번들 버전(`CFBundleShortVersionString`)을 반환하면 됩니다.
