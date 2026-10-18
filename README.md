# 이어폰 알람

Kotlin + Jetpack Compose Android 앱. `quizquiz/earphone-alarm/`의 Phase 1에서 분리하고 알람·타이머 기능을 구현했습니다. Android 9(API 28) 이상, compile/target SDK 35.

## 구현한 기능

- 알람 추가·수정·삭제·활성화, 한 번/반복 요일, 메모, 개별 알람음·음량
- 시스템 AlarmManager 예약, 재부팅·시간대 변경·앱 업데이트 시 일반 알람 재예약
- 타이머 시작·일시정지·재개·취소. 화면 종료/프로세스 종료 후에도 예약 유지. 재부팅 시 타이머와 다시 울림 예약 초기화
- 이어폰 연결 감지, 미연결 시 무음 알림/진동/스피커 선택(기본 무음)
- 포그라운드 재생 서비스, 음량 0에서 실제 출력 경로 확인, 연결 해제/경로 변경 시 중단
- 다시 울림, 페이드 인, 자동 종료, 진동, 잠금 화면 알람, 알림의 종료 버튼
- 직접 합성한 기본 알람음 4개, 시스템 알람음, SAF를 통한 내 음악 추가·삭제
- 전역 설정 저장, 12/24시간 표시, 시간순 정렬

## 빌드

JDK 17, Android SDK 35, Gradle 8.9, AGP 8.7.3을 사용합니다.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Android Studio에서는 저장소 루트를 엽니다.
GitHub Actions의 **Android APK** 워크플로는 테스트·Lint·APK 생성을 수행하고 APK를 아티팩트로 보관합니다.
디버그 APK는 개인 테스트용입니다. CI의 임시 디버그 서명키는 실행마다 달라질 수 있어 기존 설치 위 업데이트가 안 될 수 있습니다. 지속 배포에는 별도 고정 서명키가 필요합니다.

## 처음 실행할 때

1. 설정에서 알림·블루투스 권한을 허용합니다.
2. Android 12에서는 정확한 알람 권한을 확인합니다. Android 13 이상은 핵심 알람 앱 용도의 USE_EXACT_ALARM을 선언했습니다. Play 배포 시 해당 정책 적합성은 별도로 검토해야 합니다.
3. Android 14 이상에서는 잠금 화면 알람 허용을 확인합니다. 미허용 시 알림에서 알람을 엽니다.
4. 미디어 음량이 0이 아닌지 확인합니다. 앱은 휴대폰 전체 미디어 음량을 변경하지 않습니다.
5. 이어폰으로 짧은 타이머를 실행해 실제 기기에서 확인합니다.

## 동작 범위와 한계

- 원본의 minSdk 26을 28로 조정했습니다. MediaPlayer의 실제 출력 경로 확인·지정 API가 Android 9부터 제공되기 때문입니다.

- `setPreferredDevice`는 출력 경로를 보장하는 API가 아닙니다. 이 앱은 음소거 상태에서 실제 경로를 확인하고, 재생 중 경로 변경과 `AUDIO_BECOMING_NOISY`에 대응합니다. OS/제조사 지연 때문에 이어폰 해제 순간의 짧은 스피커 누출을 절대 방지한다고 보장하지 않습니다.
- 블루투스 스피커·차량 오디오·일부 USB DAC도 이어폰으로 분류됩니다. 특정 장치 허용 목록은 없습니다.
- 이어폰 미연결 기본 동작은 무음 알림이며 연결될 때까지 기다렸다가 다시 재생하지 않습니다.
- 통화 또는 오디오 포커스를 잃으면 소리를 중단하고 무음 알림을 남깁니다.
- 동시에 울리는 알람은 가장 최근 알람으로 교체됩니다.
- 절전 정책, 앱 강제 종료, 권한 해제, 기기 전원 꺼짐에서는 알람 전달이 제한될 수 있습니다. 앱 강제 종료 후에는 다시 앱을 열어야 합니다.
- DST로 존재하지 않는 시각은 해당 시간대의 다음 유효 시각으로 이동합니다. 중복 시각은 이른 오프셋을 사용합니다.
- 재부팅 전 실행 중이던 타이머/스누즈는 복원하지 않습니다. 일반 알람만 다음 시각으로 예약합니다.
- 실제 유선·블루투스 기기, 잠금/절전/재부팅 동작은 기기 테스트가 필요합니다.

## 출처

원본: `ggumtak/quizquiz`, 브랜치 `ccr-c07af233-1awl11`, 커밋 `cc313664f4e1b151bf0f3cddc157dc40fcb913cd`, `earphone-alarm/` 폴더.
퀴즈 소스와 원본 저장소의 다른 이력은 별도 프로젝트에 포함하지 않습니다.

Android API 참고:
- https://developer.android.com/reference/android/media/MediaPlayer#setPreferredDevice(android.media.AudioDeviceInfo)
- https://developer.android.com/media/optimize/audio-focus
- https://developer.android.com/about/versions/15/changes/foreground-service-types
