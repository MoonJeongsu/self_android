# 세션 기록: 관리자 진단키트 등록 기능 및 회의용 시안

> 작성일: 2026-07-01  
> 대상 프로젝트: self-android / self-web / self-admin

---

## 1. 배경 및 목표

스마트 자가진단 시스템에 **진단키트 사진 업로드** 기능을 확장하면서, 관리자 페이지(어드민)에도 다음을 구현·시안화하는 것이 목표였다.

1. **관리자 내 '사용자 현황' 메뉴 영역**에 **진단키트 등록** 테이블 추가
   - 리스트에서 등록 내역 확인
   - 상세 정보 페이지에서 업로드된 사진 열람
2. **Figma 톤앤매너**를 유지한 **회의용 PNG 시안** 제작

---

## 2. 사전 분석 결과

### 2.1 가능 여부

| 항목 | 가능 여부 | 비고 |
|------|-----------|------|
| Admin 진단키트 등록 기능 | ✅ 가능 | `사용자 현황` 패턴 복제 |
| 회의용 PNG 시안 | ✅ 가능 | Figma `사용자 현황`/`상세` 템플릿 기반 |
| Figma 기존 관리자 화면 | ✅ 있음 | 로그인, 사용자 현황, 상세 |
| Figma 진단키트 화면 | ❌ 없음 | 신규 설계 대상 |
| 코드 기반 UI 골격 | ✅ 있음 | `self-admin` mock UI |

### 2.2 확정 사항 (분석 기준)

| # | 항목 | 결정 |
|---|------|------|
| 1 | **메뉴 위치** | `자가진단 현황` 하위 **별도 메뉴** (지역 현황, 사용자 현황과 동급) |
| 2 | **리스트 컬럼** | No, 성명, 성별, 생년월일, 등록일시, 진단 결과, 상세정보(확인) |
| 3 | **상세 사진** | 여러 장 지원, 썸네일 + 클릭 시 라이트박스 확대 |
| 4 | **필터** | 성별, 등록 일자(기간), 성명 직접 검색 |

### 2.3 Figma 참고 스펙 (관리자)

| 항목 | 값 |
|------|-----|
| 화면 폭 | 1920px |
| 배경 | `#FAFAFA` |
| Primary | `#3EB4EB` |
| 폰트 | Spoqa Han Sans Neo |
| 테이블 | 흰 배경, 헤더 66px, 행 52px, zebra `#F0FAFF` |
| 필터 영역 | 흰 박스 128px 높이 |

Figma 파일: `Wp6fWkNm1kgGnHSkWqTeSK` (스마트-자가진단_진행)

---

## 3. 구현 내용 (self-admin)

### 3.1 메뉴 구조

```
자가진단 현황 (GNB)
├── 지역 현황        (기존)
├── 사용자 현황      (기존)
└── 진단키트 등록    ★ 신규
     ├── 리스트 페이지  /situation/kit
     └── 상세 페이지    /situation/kit/detail
```

### 3.2 신규·수정 파일

| 파일 | 설명 |
|------|------|
| `components/app/Header.vue` | 서브메뉴 `진단키트 등록` 추가 |
| `pages/situation/kit/index.vue` | 진단키트 등록 리스트 |
| `pages/situation/kit/detail.vue` | 상세 페이지 |
| `components/kit/Info.vue` | 등록 정보 테이블 |
| `components/kit/PhotoGallery.vue` | 사진 썸네일 + 라이트박스 |
| `utils/sample.ts` | `kits` mock 데이터 10건 |
| `public/kit-samples/kit-photo-1.svg` | 샘플 사진 1 |
| `public/kit-samples/kit-photo-2.svg` | 샘플 사진 2 |
| `components/table/List.vue` | 상세 버튼 클릭 시 `key1` ID 전달 수정 |

### 3.3 리스트 화면 (`/situation/kit`)

- **필터**: 성별, 등록 일자, 성명 검색
- **컬럼**: No, 성명, 성별, 생년월일, 등록일시, 진단 결과, 상세정보(확인)
- `사용자 현황`과 동일한 레이아웃·스타일 (`TableContainer`, `FormSelect`, `FormInput` 등)

### 3.4 상세 화면 (`/situation/kit/detail?id={id}`)

- **등록 정보**: 성명, 키트 번호, 성별, 생년월일, 등록일시, 진단 결과, 증상
- **진단키트 사진**: 썸네일 목록 → 클릭 시 라이트박스 확대 (여러 장일 경우 이전/다음 탐색)
- **돌아가기** 버튼 → 리스트로 이동

### 3.5 Mock 데이터 구조

```typescript
{
  key1: '1',              // ID
  key2: '홍길동',          // 성명
  key3: '남성',            // 성별
  key4: '1980-12-01',     // 생년월일
  key5: '2025-06-15 14:32', // 등록일시
  key6: '건강 이상',       // 진단 결과
  kitNumber: 'KIT-2025-0001',
  symptoms: '발열, 기침',
  photos: ['/kit-samples/kit-photo-1.svg', '/kit-samples/kit-photo-2.svg']
}
```

### 3.6 로컬 실행

```powershell
cd c:\projects\self\self-admin\self-admin
npm install
npm run dev
```

- 리스트: `http://localhost:3000/situation/kit`
- 상세: `http://localhost:3000/situation/kit/detail?id=1`

> 현재 mock 데이터 기반. 실제 API 연동은 백엔드 스펙 확정 후 진행 필요.

---

## 4. 회의용 PNG 시안

### 4.1 산출물 위치

`c:\projects\self\self-android\design\mockups\admin\`

| 파일 | 내용 |
|------|------|
| `kit-list.png` | 진단키트 등록 리스트 화면 |
| `kit-detail.png` | 상세 + 사진 열람 화면 |
| `kit-flow.png` | GNB → 리스트 → 상세 흐름도 |
| `kit-list.html` | 리스트 HTML 소스 |
| `kit-detail.html` | 상세 HTML 소스 |
| `kit-flow.html` | 흐름도 HTML 소스 |
| `admin-styles.css` | 공통 스타일 (Figma 토큰 반영) |
| `export-admin-png.mjs` | Playwright PNG export 스크립트 |

### 4.2 PNG 재생성 방법

```powershell
cd c:\projects\self\self-android\design\mockups
npm install   # 최초 1회 (playwright)
node admin/export-admin-png.mjs
```

### 4.3 시안 제작 방식

- 앱 시안과 동일: **HTML/CSS 목업 → Playwright screenshot**
- Figma 관리자 `사용자 현황` 화면 톤앤매너 유지
- 1920×1080, `deviceScaleFactor: 2`

---

## 5. 관련 맥락 (동일 세션·이전 작업)

### 5.1 Android (self-android)

- 패키지명: `com.smmit.self` → `com.smmit.self2`
- Debug/Release 빌드 성공
- Release AAB: `app/build/outputs/bundle/release/app-release.aab`

### 5.2 앱 UI (self-web)

- 진단키트 업로드 삽입 지점: 세부질문(Step 4) 이후, 처리 중 이전 (이상 증상 경로만)
- 앱 회의용 시안: `self-android/design/mockups/`
  - `kit-upload-empty.png`, `kit-upload-filled.png`, `flow.png`

### 5.3 Figma

- 앱: `Wp6fWkNm1kgGnHSkWqTeSK` — 진단키트 업로드 화면 없음 (신규 설계)
- 관리자: 동일 파일 내 `사용자 현황`, `사용자 현황 상세` 프레임 존재

---

## 6. 향후 작업 (미완료)

| # | 작업 | 상태 |
|---|------|------|
| 1 | 백엔드 API (진단키트 목록·상세·사진 URL) | ❌ 미구현 |
| 2 | 앱 진단키트 업로드 UI (`diagnosis.vue`) | ❌ 미구현 |
| 3 | WebView 카메라 브릿지 (Android) | ❌ 미구현 |
| 4 | Admin API 연동 (mock → 실데이터) | ❌ 미구현 |
| 5 | Firebase `com.smmit.self2` 등록 (프로덕션 FCM) | ⚠️ 사용자 작업 필요 |

---

## 7. 주요 파일 경로 요약

| 영역 | 경로 |
|------|------|
| Admin 리스트 | `self-admin/pages/situation/kit/index.vue` |
| Admin 상세 | `self-admin/pages/situation/kit/detail.vue` |
| Admin 메뉴 | `self-admin/components/app/Header.vue` |
| Admin 사진 갤러리 | `self-admin/components/kit/PhotoGallery.vue` |
| Admin mock 데이터 | `self-admin/utils/sample.ts` |
| Admin PNG 시안 | `self-android/design/mockups/admin/` |
| 앱 진단 플로우 | `self-web/components/modal/diagnosis.vue` |
| Android MainActivity | `self-android/app/src/main/java/com/smmit/self2/MainActivity.kt` |
