# 이어폰 알람 작업 규칙

## 버전 관리
- 푸시할 때마다 `app/build.gradle.kts`의 `versionCode`를 1 올리고 `versionName`도 올린다.
- 기능·화면 변경은 마이너(1.1 → 1.2), 버그 수정만 있으면 패치(1.2 → 1.2.1).
- 커밋 메시지 첫 줄 끝에 `(vX.Y.Z)` 형태로 버전을 적는다.
- 예외: CI 봇의 스크린샷 커밋(`[skip ci]`)은 버전을 올리지 않는다.

## 빌드 확인
- 이 저장소의 Android 빌드는 GitHub Actions(`android.yml`)로 검증한다.
- UI 스크린샷은 브랜치 push 시 `screenshots.yml`이 `docs/screenshots/`에 커밋한다.
