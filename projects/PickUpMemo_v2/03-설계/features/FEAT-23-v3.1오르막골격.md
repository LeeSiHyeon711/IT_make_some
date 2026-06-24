> 문서 버전: v3.0 | 기준 베이스: PickUpMemo_v2 | 작성 단계: 3단계 설계 | 입력: PRD_v3.md | 이전 문서: 설계서.md(v2)·FEAT-01~13 보존

# FEAT-23 — v3.1 오르막 주의 골격 (UphillDetector + HillRoadList, 동작 없음)

- 매칭 이슈: #23
- 작성일: 2026-06-24
- 상위 설계서: `03-설계/설계서_v3.md` (10장 확장 포인트)

## 1. 목적
v3.1 "오르막 주의" 기능을 위한 **확장 가능 골격만** 준비한다. 인터페이스/데이터 모델/no-op 구현체를 두되 **실제 동작은 없다**. 팝업 내 빈 자리(`tvPopupHill`)는 FEAT-19에서 이미 마련됨. (PRD 8장, AC-9)

## 2. 범위
### 구현할 것
- 신규 패키지 `uphill/`: `UphillDetector.kt`(인터페이스 + `HillAlert` 데이터 + `NoopUphillDetector` stub), `HillRoadList.kt`(`HillRoad` 모델 + 빈 리스트).

### 구현하지 않을 것
- 실제 감지/리스트 매칭/고도·경사 API → v3.1.
- 팝업에 힐 텍스트 표시 → v3.1(여기선 호출 안 함, 자리만 GONE 유지).
- 서비스/팝업 결선 → 하지 않음.

## 3. 입력 / 출력
### 입력
- 없음(골격 타입 정의). `GeoPoint`(FEAT-15) 참조.
### 출력
- `uphill` 패키지 2개 파일. 컴파일만 통과, 호출자 없음.

## 4. 동작 흐름
1. `uphill/` 패키지 생성.
2. `UphillDetector` 인터페이스 + `HillAlert` + `NoopUphillDetector`(항상 null) 정의.
3. `HillRoad` 모델 + `HillRoadList`(빈 리스트) 정의.
4. 빌드 통과 확인.

## 5. 수정 예상 파일
- 신규: `app/src/main/java/com/itmakesome/pickupmemo2/uphill/UphillDetector.kt`
- 신규: `app/src/main/java/com/itmakesome/pickupmemo2/uphill/HillRoadList.kt`

## 6. 데이터 구조 / 함수 / 클래스
```kotlin
package com.itmakesome.pickupmemo2.uphill

import com.itmakesome.pickupmemo2.route.GeoPoint

// UphillDetector.kt
data class HillAlert(val roadName: String, val message: String)

interface UphillDetector {
    /** v3.1에서 구현. v3 골격에서는 항상 null. */
    fun detect(pickup: GeoPoint?, dest: GeoPoint?): HillAlert?
}

object NoopUphillDetector : UphillDetector {
    override fun detect(pickup: GeoPoint?, dest: GeoPoint?): HillAlert? = null
}

// HillRoadList.kt
data class HillRoad(val roadName: String, val note: String)

object HillRoadList {
    /** v3.1에서 위험 도로명을 채운다. v3에서는 빈 리스트. */
    val roads: List<HillRoad> = emptyList()
}
```

## 7. 예외 처리
- 골격이라 런타임 동작 없음. `detect`는 항상 null. 호출자 없음.

## 8. 완료 조건
- 빌드 성공.
- `uphill` 패키지에 `UphillDetector`·`HillAlert`·`NoopUphillDetector`·`HillRoad`·`HillRoadList` 존재.
- `NoopUphillDetector.detect(...)`가 null 반환.
- 팝업 `tvPopupHill`은 GONE 유지(표시 로직 없음).

## 9. 테스트 방법
1. 빌드 통과.
2. (선택) 임시 호출 `NoopUphillDetector.detect(null, null) == null` 확인 후 제거.
3. 기존 앱/팝업 동작 회귀 없음(골격만 추가, 결선 없음).

## 10. 금지 사항
- 실제 감지/리스트 매칭/고도·경사 API 구현 금지(v3.1).
- 팝업/서비스에 힐 결선 금지(자리만 GONE 유지).
- `HillRoadList`에 실제 데이터 채우기 금지(빈 리스트 유지).
- 불필요한 라이브러리 추가 금지.
