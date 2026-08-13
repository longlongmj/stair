# 📱 휴대폰만으로 계단모임 APK 만들기

이 프로젝트는 GitHub Actions가 대신 Android 앱을 컴파일하도록 설정되어 있습니다.
Android Studio가 없어도 됩니다.

## 1. ZIP 압축 풀기
`계단모임_GitHub_자동APK.zip`을 휴대폰에 다운로드한 뒤 압축을 풉니다.

## 2. GitHub 가입
휴대폰 브라우저에서 github.com에 접속해서 무료 계정을 만듭니다.

## 3. 새 저장소 만들기
GitHub 오른쪽 위 + → New repository

Repository name 예시:
`stairs-club`

Public 또는 Private 중 아무거나 선택하고 Create repository를 누릅니다.

## 4. 프로젝트 파일 업로드
중요: ZIP 파일 한 개만 올리는 것이 아니라 **압축을 푼 안쪽 파일/폴더 전체**가 저장소 최상단에 들어가야 합니다.

최상단에서 아래가 보여야 정상입니다:
- `.github`
- `app`
- `build.gradle.kts`
- `settings.gradle.kts`
- `firestore.rules`

GitHub 모바일 웹에서 Add file → Upload files를 이용합니다.

### 휴대폰에서 폴더 업로드가 불편한 경우
GitHub 앱보다 Chrome/Samsung Internet의 "데스크톱 사이트" 보기를 사용하는 편이 쉽습니다.

## 5. APK 자동 생성
파일이 main 브랜치에 올라가면 자동 빌드가 시작됩니다.

GitHub 저장소 → Actions → Build Android APK

초록색 체크가 뜨면 완료입니다.

화면 아래쪽 Artifacts에서:
`stairs-club-apk`

를 누르면 APK가 들어있는 ZIP을 받을 수 있습니다.
압축을 풀면:
`app-debug.apk`

가 있습니다.

## 6. 휴대폰에 APK 설치
`app-debug.apk`를 누릅니다.

Android가 "알 수 없는 앱 설치" 권한을 묻는다면,
현재 사용 중인 브라우저/파일 앱에 한해 허용한 뒤 설치합니다.

---

# 🔥 실시간 랭킹까지 활성화하기

Firebase 연결 없이도 APK 자체는 빌드되지만, 실시간 데이터 기능은 Firebase 연결이 필요합니다.

## A. Firebase 프로젝트 만들기
Firebase Console에서 프로젝트를 만들고 Android 앱을 추가합니다.

패키지명:
`com.stairsclub.app`

## B. 익명 로그인 켜기
Firebase → Authentication → Sign-in method → Anonymous 활성화

## C. Firestore 만들기
Firebase → Firestore Database → Create database

그 후 `firestore.rules` 내용을 Rules 화면에 붙여 넣고 게시합니다.

## D. google-services.json 내용을 GitHub Secret으로 넣기
Firebase Android 앱 설정에서 `google-services.json`을 다운로드합니다.

휴대폰 파일 앱에서 해당 파일을 텍스트로 열어 **내용 전체를 복사**합니다.

GitHub 저장소:
Settings → Secrets and variables → Actions → New repository secret

Name:
`GOOGLE_SERVICES_JSON`

Secret:
방금 복사한 google-services.json 전체 내용

Add secret을 누릅니다.

## E. 다시 빌드
GitHub 저장소 → Actions → Build Android APK → Run workflow

새 APK를 받아 설치하면 Firebase 실시간 기능이 연결됩니다.

---

## 꼭 기억할 것
`google-services.json` 파일 자체를 공개 GitHub 저장소에 직접 올리지 마세요.
이 프로젝트는 `.gitignore`에 해당 파일을 제외하도록 설정했고,
GitHub Secret에서 빌드할 때만 임시로 생성하도록 만들었습니다.
