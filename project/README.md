# Meal Planner - Android Native App

네이티브 Android 앱으로 Java와 XML로만 작성되었습니다.

## 프로젝트 구조

```
android-native/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/mealplanner/
│   │       │   ├── MainActivity.java
│   │       │   ├── PantryActivity.java
│   │       │   ├── ShoppingActivity.java
│   │       │   ├── RecipesActivity.java
│   │       │   ├── MealPlanActivity.java
│   │       │   ├── StatsActivity.java
│   │       │   └── SupabaseClient.java
│   │       ├── res/
│   │       │   ├── layout/
│   │       │   │   ├── activity_main.xml
│   │       │   │   ├── activity_pantry.xml
│   │       │   │   ├── activity_shopping.xml
│   │       │   │   ├── activity_recipes.xml
│   │       │   │   ├── activity_meal_plan.xml
│   │       │   │   ├── activity_stats.xml
│   │       │   │   └── nav_header.xml
│   │       │   ├── menu/
│   │       │   │   └── drawer_menu.xml
│   │       │   ├── values/
│   │       │   │   ├── colors.xml
│   │       │   │   ├── strings.xml
│   │       │   │   └── themes.xml
│   │       │   └── drawable/
│   │       │       └── circle_green_light.xml
│   │       └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Android Studio에서 실행하는 방법

### 1. Android Studio 설치
- [Android Studio](https://developer.android.com/studio) 다운로드 및 설치

### 2. 프로젝트 열기
1. Android Studio 실행
2. "Open an Existing Project" 선택
3. `android-native` 폴더 선택

### 3. Gradle 동기화
- 프로젝트가 열리면 자동으로 Gradle 동기화가 시작됩니다
- "Sync Project with Gradle Files" 클릭 (필요시)

### 4. 에뮬레이터 설정
1. Tools > Device Manager
2. "Create Device" 클릭
3. Pixel 4 이상 선택 (권장)
4. API Level 24 이상 선택
5. Finish

### 5. 앱 실행
1. 상단의 Run 버튼 (▶️) 클릭
2. 또는 Shift + F10 (Windows/Linux) / Control + R (Mac)

## 주요 기능

### 1. 홈 화면 (MainActivity)
- 오늘의 메뉴 카드
- Pantry와 Shopping 빠른 실행 버튼
- 사이드바 메뉴 (왼쪽 위 햄버거 아이콘)

### 2. Pantry 화면 (PantryActivity)
- 식료품 목록 조회
- 수량 증가/감소 기능

### 3. Shopping 화면 (ShoppingActivity)
- 쇼핑 리스트 관리
- 체크박스로 완료 표시

### 4. Recipes 화면 (RecipesActivity)
- 레시피 목록
- 검색 기능

### 5. Meal Plan 화면 (MealPlanActivity)
- 주간 식사 계획
- 아침/점심/저녁 분류

### 6. Stats 화면 (StatsActivity)
- 음식 낭비 통계
- 주간/월간 리포트

## 내비게이션

### 사이드바 메뉴
- 왼쪽 위 햄버거 아이콘 클릭
- 또는 왼쪽에서 오른쪽으로 스와이프
- 메뉴 항목:
  - Home
  - Meal Plan
  - Shopping
  - Recipes
  - Pantry
  - Stats

### 빠른 실행
- 홈 화면의 Pantry 버튼 → Pantry 화면으로 이동
- 홈 화면의 Shopping 버튼 → Shopping 화면으로 이동

## Supabase 연동

앱은 Supabase 데이터베이스와 연동되어 있습니다:
- URL: https://tryjfibwdoquqgtvwrpr.supabase.co
- SupabaseClient.java에서 클라이언트 초기화

## 기술 스택

- **언어**: Java
- **UI**: XML Layouts
- **최소 SDK**: API 24 (Android 7.0)
- **타겟 SDK**: API 34 (Android 14)
- **라이브러리**:
  - AndroidX AppCompat
  - Material Components
  - ConstraintLayout
  - DrawerLayout
  - RecyclerView
  - CardView
  - Supabase Kotlin SDK

## 커스터마이징

### 색상 변경
`app/src/main/res/values/colors.xml`에서 색상 수정:
```xml
<color name="green_primary">#4CAF50</color>
<color name="text_primary">#212121</color>
```

### 문자열 변경
`app/src/main/res/values/strings.xml`에서 텍스트 수정

### 레이아웃 수정
`app/src/main/res/layout/` 폴더의 XML 파일 수정

## 문제 해결

### Gradle 동기화 실패
```bash
File > Invalidate Caches / Restart
```

### 에뮬레이터 느림
- AVD Manager에서 RAM 증가
- Hardware acceleration 활성화

### 앱 크래시
- Logcat 확인 (Android Studio 하단)
- 인터넷 권한 확인 (AndroidManifest.xml)

## 빌드 APK

릴리즈 APK 생성:
```
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

## 라이센스

이 프로젝트는 교육 목적으로 생성되었습니다.
