# 계단모임 — GitHub 자동 APK 빌드 버전

휴대폰만으로 APK를 만들 수 있게 GitHub Actions 자동 빌드가 포함된 Android 네이티브 프로젝트입니다.

## 사용 순서
가장 먼저 `모바일_APK_만들기_가이드.md`를 읽으세요.

GitHub에 프로젝트 파일을 올리면 `.github/workflows/build-apk.yml`이 자동으로 실행되어
`app-debug.apk`를 Artifact로 생성합니다.

Android 공식 Gradle 빌드 방식과 GitHub Actions의 Gradle/Artifact 기능을 사용하도록 구성했습니다.

## Firebase
Firebase 설정이 없으면 APK는 빌드되지만 앱에서 Firebase 연결 안내 화면이 표시됩니다.
실시간 랭킹을 사용하려면 GitHub Secret `GOOGLE_SERVICES_JSON`을 설정하고 다시 빌드합니다.

패키지명: `com.stairsclub.app`
