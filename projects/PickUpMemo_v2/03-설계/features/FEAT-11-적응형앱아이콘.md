# FEAT-11 — 적응형 앱 아이콘 (말풍선 심볼)

- 매칭 이슈: #11
- 작성일: 2026-06-17
- 상위 설계서: `03-설계/설계서.md` (v2.1 개선 추가 설계 — 개선요청 2)

## 1. 목적
현재 런처 아이콘이 흰 배경/투명만 보이는 문제를 해결한다. FEAT-01에서 placeholder(전경=흰색, 배경=보라색)로만 있던 adaptive-icon을, **외부 에셋 없이 vector drawable로 그린 메모 앱다운 심볼(말풍선 + 메모 줄)** 로 교체한다. 사용자가 런처에서 앱을 식별할 수 있게 한다.

## 2. 범위
### 구현할 것
- 신규 `res/drawable/ic_launcher_foreground.xml`: 흰 말풍선 + 보라색 메모 줄 2개를 그린 vector (adaptive-icon foreground, 108x108 viewport, 안전영역 내 배치)
- 기존 mipmap adaptive-icon XML 4개의 `<foreground>`가 위 vector를 가리키도록 교체:
  - `res/mipmap-anydpi-v26/ic_launcher.xml`, `res/mipmap-anydpi-v26/ic_launcher_round.xml`
  - `res/mipmap-hdpi/ic_launcher.xml`, `res/mipmap-hdpi/ic_launcher_round.xml`
- 배경은 기존 `@color/purple_500` 유지(별도 background drawable 불필요)

### 구현하지 않을 것
- 비트맵(PNG) 아이콘 추가/외부 이미지 사용 금지 (vector만)
- `<monochrome>`(테마 아이콘) 추가 — MVP 범위 밖
- 매니페스트의 `android:icon`/`android:roundIcon` 참조 변경 불필요(이미 `@mipmap/ic_launcher`/`_round` 사용 중)
- 색상 팔레트(colors.xml)·테마 변경 금지

## 3. 입력 / 출력
### 입력
- 기존 `@color/purple_500`(#FF6200EE), `@color/white`(#FFFFFFFF), `@color/purple_700`(#FF3700B3)
### 출력
- 런처에 보라색 배경 + 흰 말풍선(메모 줄 2개) 아이콘이 표시됨

## 4. 동작 흐름
1. `ic_launcher_foreground.xml` vector 생성(아래 6장 pathData 사용).
2. mipmap adaptive-icon 4개 파일에서 `<foreground android:drawable="@color/white" />` → `<foreground android:drawable="@drawable/ic_launcher_foreground" />` 로 교체. `<background>`는 `@color/purple_500` 유지.
3. 빌드 후 런처/설정 아이콘이 말풍선 심볼로 보이는지 확인.

## 5. 수정 예상 파일
- 신규: `res/drawable/ic_launcher_foreground.xml`
- 수정: `res/mipmap-anydpi-v26/ic_launcher.xml`, `res/mipmap-anydpi-v26/ic_launcher_round.xml`, `res/mipmap-hdpi/ic_launcher.xml`, `res/mipmap-hdpi/ic_launcher_round.xml`

## 6. 데이터 구조 / 함수 / 클래스
**`res/drawable/ic_launcher_foreground.xml`** (그대로 사용 가능한 vector):
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- 흰 말풍선 본체 (안전영역 약 24~84 내부, 좌하단 꼬리) -->
    <path
        android:fillColor="@color/white"
        android:pathData="M38,30 L78,30 A8,8 0 0 1 86,38 L86,60 A8,8 0 0 1 78,68 L52,68 L42,80 L42,68 L38,68 A8,8 0 0 1 30,60 L30,38 A8,8 0 0 1 38,30 Z" />
    <!-- 메모 줄 1 -->
    <path
        android:fillColor="@color/purple_700"
        android:pathData="M40,42 L76,42 L76,46 L40,46 Z" />
    <!-- 메모 줄 2 (조금 짧게) -->
    <path
        android:fillColor="@color/purple_700"
        android:pathData="M40,52 L66,52 L66,56 L40,56 Z" />
</vector>
```
**mipmap adaptive-icon 4개 파일 공통 형태**(교체 후):
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/purple_500" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```
- 좌표는 108x108 뷰포트 기준. 콘텐츠는 중앙 안전영역(약 18~90)에 들어가 런처가 원/스쿼클로 잘라도 핵심 심볼이 보존된다.
- minSdk 26이라 모든 기기에서 `mipmap-anydpi-v26`가 우선 적용되지만, 일관성을 위해 `mipmap-hdpi`의 adaptive-icon XML도 동일하게 교체한다.

## 7. 예외 처리
- vector pathData 오타 시 빌드 단계(aapt)에서 에러 → pathData를 위 예시 그대로 사용해 회피.
- `@color/purple_700` 부재 시 빌드 실패 → colors.xml에 이미 존재함(확인됨). 새 색 추가 불필요.

## 8. 완료 조건
- 빌드 성공.
- 기기 런처/앱 목록에서 보라색 배경 + 흰 말풍선(메모 줄 2개) 아이콘이 보인다(흰/투명 사각형이 아님).
- 둥근 아이콘(`ic_launcher_round`) 사용 런처에서도 동일 심볼이 보인다.

## 9. 테스트 방법
1. 앱 설치/재설치 후 런처에서 아이콘 확인 → 말풍선 심볼이 보이는지 확인.
2. 설정 > 앱 > PickUpMemo 아이콘, 최근 앱 카드 아이콘 확인.
3. (선택) Android Studio Resource Manager에서 미리보기로 전경/배경 합성 확인.

## 10. 금지 사항
- PNG/외부 이미지 에셋 추가 금지(vector만).
- colors.xml·themes.xml·매니페스트 아이콘 참조 변경 금지.
- 다른 화면/기능 변경 금지.
- 이 이슈 범위를 벗어나는 리팩터링 금지.
