# FEAT-09 — 문자열 정리 + APK 빌드 + 빌드노트

- 매칭 이슈: #9
- 작성일: 2026-06-17
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
모든 기능 구현 후 문자열 리소스를 마감하고, JDK 21 환경에서 디버그 APK를 빌드해 산출물을 만들고, 실행/설치/검증 절차를 빌드노트에 기록한다. QA 진입 전 산출물(APK) 존재 조건을 충족한다.

## 2. 범위
### 구현할 것
- `res/values/strings.xml` 최종 정리(하드코딩 문자열 제거, 한글 라벨 정합)
- 디버그 APK 빌드(`assembleDebug`) → `app/build/outputs/apk/debug/app-debug.apk`
- `05-개발/빌드노트.md` 작성(`templates/빌드노트.md` 양식): 빌드 환경(JDK 21), 명령, 설치/권한 설정/검증 절차, 변경 파일 요약
### 구현하지 않을 것
- 신규 기능 추가/로직 변경 → 해당 FEAT에서 이미 완료. 여기서는 마감·빌드만.

## 3. 입력 / 출력
### 입력
- FEAT-01~08 완료된 코드베이스
### 출력
- `app-debug.apk` 산출물 + `05-개발/빌드노트.md`

## 4. 동작 흐름
1. 전체 문자열 하드코딩 점검 → strings.xml로 이동/정리(과한 리팩터링 금지, 명백한 것만).
2. `JAVA_HOME=<JDK21> ./gradlew clean :app:assembleDebug` 실행.
3. BUILD SUCCESSFUL + APK 생성 확인.
4. 빌드노트 작성: 환경/명령/설치(adb install 또는 파일 전송)/권한 설정 순서(접근성 ON → 오버레이 허용)/핵심 검증 시나리오(푸라닭 신림점) 기록.

## 5. 수정 예상 파일
- 수정: `res/values/strings.xml` (필요 시 소폭)
- 신규: `projects/PickUpMemo_v2/05-개발/빌드노트.md`
- 산출물: `app/build/outputs/apk/debug/app-debug.apk`

## 6. 데이터 구조 / 함수 / 클래스
- 코드 구조 변경 없음. 빌드노트 필수 항목:
  - 빌드 환경: AGP 8.5.2 / Gradle 8.7 / Kotlin 1.9.24 / **JDK 21**(기본 JDK 25면 실패 → JAVA_HOME 지정), Room 2.6.1 + KSP 1.9.24-1.0.20, minSdk 26 / targetSdk 34.
  - 빌드 명령: `JAVA_HOME=<JDK21경로> ./gradlew clean :app:assembleDebug`.
  - 설치: `adb install -r app-debug.apk` 또는 기기 직접 설치.
  - 최초 설정 순서: ① 접근성 설정에서 PickUpMemo_v2 ON ② 오버레이 권한 허용 ③ 메모 등록(푸라닭/신림점/...).
  - 검증 시나리오: 배민커넥트 신규 배차 카드 → 팝업 표시 → 6초 자동 닫힘.
  - 패키지명: `com.itmakesome.pickupmemo2`.

## 7. 예외 처리
- 빌드가 JDK 25로 실패하면 JAVA_HOME을 JDK 21로 지정해 재시도(빌드노트에 명시).
- KSP/Room 버전 불일치 빌드 오류 시 FEAT-01 명시 버전으로 고정 확인.

## 8. 완료 조건
- `assembleDebug` BUILD SUCCESSFUL.
- `app-debug.apk` 존재.
- `05-개발/빌드노트.md`에 환경·명령·설치·권한·검증 절차가 기록됨.
- 하드코딩 문자열로 인한 경고/누락이 정리됨.

## 9. 테스트 방법
1. clean 빌드 실행 → SUCCESSFUL + APK 경로 확인.
2. 기기 설치 → 빌드노트 절차대로 권한 설정 → 푸라닭 신림점 팝업 표시 1회 통과.

## 10. 금지 사항
- 신규 기능/로직 추가 금지(마감·빌드 전용).
- 광범위 리팩터링/스타일 변경 금지.
- release 서명/스토어 배포 작업 금지(디버그 APK까지).
- `05-개발/` 폴더 밖에 파일 쓰기 금지(빌드노트는 `05-개발/`).
