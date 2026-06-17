# PickUpMemo v1 Log Collector 결과 보고서

## 1. 문서 목적

본 문서는 PickUpMemo v1 Log Collector를 통해 실제 Android 기기에서 수집한 접근성 서비스(Accessibility Service) 및 알림(Notification Listener) 로그를 분석한 결과를 정리한 문서이다.

PickUpMemo v1의 목적은 배민커넥트 앱에서 배차 화면에 표시되는 업체 상호, 지점명, 배차 상태, 배달 관련 텍스트가 Android 접근성 API 또는 알림 데이터로 수집 가능한지 확인하는 것이었다.

본 문서는 v2 개발 지시서가 아니다.

이 문서는 v1에서 확보한 수확물, 즉 실제 로그 형태와 그 로그에서 얻은 인사이트를 기록하기 위한 참고 문서이다.

---

## 2. v1 검증 목표

PickUpMemo v1에서 검증하고자 한 핵심 질문은 다음과 같다.

> 배민커넥트 배차 화면에 표시되는 업체 상호를 Android 접근성 로그 또는 알림 로그로 읽어올 수 있는가?

이를 위해 v1에서는 아래 데이터를 수집했다.

- 화면에 표시되는 텍스트
- ContentDescription
- View ID
- Package Name
- Activity 정보
- Notification Title
- Notification Body
- 로그 발생 시각

---

## 3. 수집 환경 요약

실제 테스트 중 수집된 주요 앱 패키지는 다음과 같다.

| 구분 | 패키지명 | 설명 |
|---|---|---|
| 배민커넥트 | `com.woowahan.bros` | 핵심 분석 대상 |
| Android System UI | `com.android.systemui` | 상태바, 잠금화면, 알림 영역 |
| LG 런처 | `com.lge.launcher3` | 홈 화면, 최근 앱 화면 |
| LG Signboard | `com.lge.signboard` | 보조 화면/알림 표시 영역 |
| 카카오맵 | `net.daum.android.map` | 배차 수락 후 길안내 |
| 카카오톡 | `com.kakao.talk` | 일반 알림 노이즈 |
| Android 공유 화면 | `android` | 로그 공유 Intent 화면 |
| PickUpMemo | 앱 자체 패키지 | 테스트 알림 및 검증 도구 화면 |

핵심 분석 대상은 `com.woowahan.bros`이다.

---

## 4. 로그 형식

v1에서 수집한 로그는 다음과 같은 형태로 저장되었다.

```text
[2026-06-16 19:53:33]
Package: com.woowahan.bros
Type: Accessibility
Text: ...
```

Notification 로그는 다음과 같은 형태로 저장되었다.

```text
[2026-06-16 19:53:26]
Package: com.woowahan.bros
Type: Notification
Title: 배민커넥트
Body: 이용건 라이더님, 오늘도 안전운행하세요!
```

Accessibility 로그는 하나의 화면에서 수집 가능한 텍스트, ContentDescription, View ID가 `/` 구분자로 이어진 긴 문자열 형태로 저장되었다.

예시:

```text
Text: desc=지도 / desc=NAVER | id=navermap_icon / desc=신규배차_끄기버튼 | id=신규배차_끄기버튼 / 신규배차 | id=bros-textview
```

---

## 5. 핵심 결론 요약

v1 로그 분석 결과, 배민커넥트 신규 배차 화면에서 업체 상호를 접근성 로그로 확인할 수 있었다.

가장 중요한 실제 수집 결과는 다음이다.

```text
픽업지 / 푸라닭 신림점 / 전달지
```

또한 배차 수락 이후 상세 화면에서도 업체 상호가 다시 수집되었다.

```text
가게전화 / 푸라닭 신림점 / 신림동 1433-92 (신림동)
```

따라서 PickUpMemo v2는 우선 OCR 없이 접근성 로그 기반으로 업체명을 추출하고, 저장된 메모와 매칭하는 방향으로 진행할 수 있다.

---

## 6. 배민커넥트 패키지 식별 결과

실제 배민커넥트 앱은 아래 패키지명으로 수집되었다.

```text
Package: com.woowahan.bros
```

따라서 v2의 메모 매칭 로직은 우선적으로 `com.woowahan.bros` 패키지에서 발생한 Accessibility 로그를 중심으로 처리하는 것이 타당하다.

단, v1 로그 수집 도구 자체는 모든 패키지 로그를 계속 수집할 수 있어야 한다.

---

## 7. 배차 대기 화면 로그 분석

배민커넥트 운행 대기 또는 배차 대기 상태에서는 아래와 같은 로그가 반복적으로 수집되었다.

```text
[2026-06-16 19:53:16]
Package: com.woowahan.bros
Type: Accessibility
Text: desc=지도 / desc=NAVER | id=navermap_icon / desc=신규배차_끄기버튼 | id=신규배차_끄기버튼 / 신규배차 | id=bros-textview / desc=배차대기중_상단_마이페이지_버튼 | id=배차대기중_상단_마이페이지_버튼 / desc=button-base | id=button-base / 배달이 많은 곳으로 이동해 보세요! | id=bros-textview / 고객 요청사항이 변경되었어요 | id=bros-textview / 배달이 많은 지역을 볼수 있어요 | id=bros-textview / desc=지도앱으로 검색하기 / 지도앱으로 검색하기 | id=bros-textview
```

비슷한 상태 문구는 아래처럼 변화했다.

```text
바쁨 지역으로 이동해 배차를 받아보세요!
현재 위치와 가까운 배차를 찾고 있어요
배달이 많은 곳으로 이동해 보세요!
신규배달 1건을 수락해주세요
```

### 인사이트

이 단계에서는 배민커넥트 운행 상태와 배차 대기 상태를 확인할 수 있다.

다만 업체 상호가 항상 포함되는 것은 아니다.

따라서 이 로그들은 “배민커넥트가 운행/배차 상태에 있다”는 보조 신호로 활용할 수 있지만, 메모 팝업을 띄우는 직접 근거로 사용하기에는 부족하다.

---

## 8. 신규 배차 발생 로그 분석

신규 배차가 실제로 발생했을 때 아래와 같은 로그가 수집되었다.

```text
[2026-06-16 19:53:33]
Package: com.woowahan.bros
Type: Accessibility
Text: desc=지도 / desc=NAVER | id=navermap_icon / desc=신규배차_끄기버튼 | id=신규배차_끄기버튼 / 신규배차 | id=bros-textview / desc=배민배달, 조리완료, 픽업지, 푸라닭 신림점, 전달지, 서울 관악구 남부순환로161나길 13 **** (신림동), 포인트, 12.6P | id=신규배차_카드 / 배민배달 | id=bros-textview / 조리완료 | id=bros-textview / 픽업지 | id=bros-textview / 푸라닭 신림점 | id=bros-textview / 전달지 | id=bros-textview / 서울 관악구 남부순환로161나길 13 **** (신림동) | id=bros-textview / 포인트 | id=bros-textview / 12.6P | id=bros-textview / 배달료 | id=bros-textview / 2,410원 | id=bros-textview / desc=touchable-image-container | id=touchable-image-container / desc=신규배차_거절버튼 | id=신규배차_거절버튼 / 거절 | id=bros-textview / desc=신규배차_수락버튼 | id=신규배차_수락버튼 / 배차수락 | id=bros-textview / 38초 | id=bros-textview
```

이 로그에서 핵심 정보는 다음과 같다.

| 항목 | 실제 로그 값 |
|---|---|
| 패키지명 | `com.woowahan.bros` |
| 배차 카드 ID | `id=신규배차_카드` |
| 배달 유형 | `배민배달` |
| 조리 상태 | `조리완료` |
| 픽업지 키워드 | `픽업지` |
| 업체 상호 | `푸라닭 신림점` |
| 전달지 키워드 | `전달지` |
| 전달지 주소 | `서울 관악구 남부순환로161나길 13 **** (신림동)` |
| 배달료 | `2,410원` |
| 거절 버튼 ID | `id=신규배차_거절버튼` |
| 수락 버튼 ID | `id=신규배차_수락버튼` |
| 남은 시간 | `38초`, `37초` 등 |

### 인사이트

신규 배차 카드에서는 업체 상호가 접근성 텍스트에 명확하게 포함된다.

가장 중요한 패턴은 다음이다.

```text
픽업지 / 푸라닭 신림점 / 전달지
```

또는 ContentDescription 안에서 아래와 같이 확인된다.

```text
desc=배민배달, 조리완료, 픽업지, 푸라닭 신림점, 전달지, 서울 관악구 ...
```

따라서 업체명 추출 기준은 다음과 같이 잡을 수 있다.

1. `id=신규배차_카드`가 포함된 로그인지 확인한다.
2. `픽업지` 키워드를 찾는다.
3. `픽업지` 다음 텍스트를 업체명 후보로 본다.
4. `전달지` 이전까지의 텍스트를 업체명 범위로 본다.

실제 예시 기준 추출 결과는 다음과 같다.

```text
storeFullName = "푸라닭 신림점"
```

---

## 9. 배차 수락 이후 상세 화면 로그 분석

배차 수락 이후에는 배달 상세 화면 또는 픽업 상세 화면에서 아래와 같은 로그가 수집되었다.

```text
[2026-06-16 19:53:40]
Package: com.woowahan.bros
Type: Accessibility
Text: desc=지도 / desc=NAVER | id=navermap_icon / desc=신규배차_끄기버튼 | id=신규배차_끄기버튼 / 신규배차 | id=bros-textview / desc=배차대기중_상단_마이페이지_버튼 | id=배차대기중_상단_마이페이지_버튼 / desc=button-base | id=button-base / desc=Bottom Sheet / desc=ai-mode-delivery-status-cta-button | id=ai-mode-delivery-status-cta-button / 가게 도착 | id=bros-textview / desc=ai-mode-bottom-sheet-top-layout | id=ai-mode-bottom-sheet-top-layout / 조리완료 | id=bros-textview / desc=가게전화 / 가게전화 | id=bros-textview / 푸라닭 신림점 | id=bros-textview / 신림동 1433-92 (신림동) | id=bros-textview / desc=도움요청 / 도움요청 | id=bros-textview / desc=길찾기 / 길찾기 | id=bros-textview / 주문정보 | id=bros-textview / T2DS0001 | id=bros-textview / 87DD | id=bros-textview / 메뉴금액 | id=bros-textview / 38,800원 | id=bros-textview / 가게명 | id=bros-textview / [대표] 고추마요 치킨 | id=bros-textview / 1개 | id=bros-textview / • | id=bros-textview / 기본음료 미제공 | id=bros-textview / 순살 [100% 국내산 통다리살] | id=bros-textview / 치킨무 제공 | id=bros-textview / 웨지감자 추가 | id=bros-textview / 기본 소스 미제공 | id=bros-textview / 꼬친 새우 | id=bros-textview / 베이컨 감자볼 (5구) | id=bros-textview / 가게정보 | id=bros-textview / 찾아오는 길 | id=bros-textview / 피크시간에는 매장 앞 도로가 혼잡하니 많은 라이더분들이 주차 하실수있도록 대각선으로 주차부탁드립니다! | id=bros-textview / 고객 요청사항 | id=bros-textview / 문 앞에 두고 초인종 눌러주세요. | id=bros-textview
```

상세 화면에서 확인된 핵심 정보는 다음과 같다.

| 항목 | 실제 로그 값 |
|---|---|
| 상태 버튼 | `가게 도착` |
| 조리 상태 | `조리완료` |
| 업체명 앞 키워드 | `가게전화` |
| 업체 상호 | `푸라닭 신림점` |
| 업체 주소 | `신림동 1433-92 (신림동)` |
| 주문정보 | `T2DS0001`, `87DD` |
| 메뉴금액 | `38,800원` |
| 메뉴명 | `[대표] 고추마요 치킨` 등 |
| 가게정보 | `찾아오는 길` 포함 |
| 매장 안내 | `피크시간에는 매장 앞 도로가 혼잡하니...` |
| 고객 요청사항 | `문 앞에 두고 초인종 눌러주세요.` |

### 인사이트

배차 수락 이후 상세 화면에서도 업체 상호를 안정적으로 확인할 수 있다.

중요한 패턴은 다음이다.

```text
가게전화 / 푸라닭 신림점 / 신림동 1433-92 (신림동)
```

따라서 상세 화면에서는 `가게전화` 다음에 등장하는 텍스트를 업체명 후보로 볼 수 있다.

실제 예시 기준 추출 결과는 다음과 같다.

```text
storeFullName = "푸라닭 신림점"
```

---

## 10. 업체명 추출 가능성 평가

v1 로그 기준으로 업체명 추출 가능성은 높다.

확인된 업체명 노출 위치는 크게 두 곳이다.

### 10.1 신규 배차 카드

```text
픽업지 / 푸라닭 신림점 / 전달지
```

신규 배차 수락 전 단계에서 업체명을 확인할 수 있다.

이 시점에서 메모 팝업을 띄울 수 있다면 배달기사가 수락 여부를 판단하기 전에 참고 정보를 볼 수 있다.

### 10.2 배차 수락 이후 상세 화면

```text
가게전화 / 푸라닭 신림점 / 신림동 1433-92 (신림동)
```

배차 수락 후에도 업체명을 확인할 수 있다.

이 시점에서는 수락 여부 판단보다는 픽업 준비, 주차, 매장 진입 방법 등을 확인하는 용도로 유용하다.

---

## 11. Notification 로그 분석

배민커넥트 Notification 로그도 수집되었지만, 업체 상호가 항상 포함되지는 않았다.

실제 수집 예시는 다음과 같다.

```text
[2026-06-16 19:53:26]
Package: com.woowahan.bros
Type: Notification
Title: 배민커넥트
Body: 이용건 라이더님, 오늘도 안전운행하세요!
```

System UI에는 아래처럼 알림 요약이 표시되었다.

```text
desc=배민커넥트 알림: 이용건 라이더님, 오늘도 안전운행하세요!
```

### 인사이트

Notification 로그만으로는 업체명을 안정적으로 얻기 어렵다.

v2에서 업체명 매칭은 Notification보다 Accessibility 로그를 우선해야 한다.

권장 우선순위는 다음과 같다.

1. Accessibility 로그의 신규 배차 카드
2. Accessibility 로그의 픽업지 패턴
3. Accessibility 로그의 가게전화 패턴
4. Notification 로그

Notification 로그는 보조 정보로 취급하는 것이 적절하다.

---

## 12. 노이즈 로그 분석

v1 로그에는 핵심 데이터 외에도 많은 노이즈가 포함되었다.

대표적인 노이즈 패키지는 다음과 같다.

```text
android
com.android.systemui
com.lge.launcher3
com.lge.signboard
com.kakao.talk
net.daum.android.map
```

### 12.1 Android 공유 화면 노이즈

로그 내보내기 과정에서 Android 공유 화면 로그가 대량으로 수집되었다.

```text
Package: android
Type: Accessibility
Text: 공유 | id=title / pickupmemo_log_20260616_195111.txt | id=content_preview_filename / Quick Share | id=chooser_nearby_button
```

### 12.2 System UI 노이즈

상태바, 배터리, 잠금화면, 알림 영역 정보가 반복 수집되었다.

```text
Package: com.android.systemui
Type: Accessibility
Text: desc=디스플레이 밝기 | id=brightness_slider / desc=카카오톡 알림: / desc=PickUpMemo 알림: 신규 배달 요청 / desc=캐시 라이더 알림: 골드 수집 중 / desc=배터리 89퍼센트
```

### 12.3 LG 런처 노이즈

홈 화면 앱 목록과 최근 앱 화면 정보가 수집되었다.

```text
Package: com.lge.launcher3
Type: Accessibility
Text: desc=Google 검색 / Instagram / YouTube / 배민커넥트 / PickUpMemo / 카카오맵 / 모두 지우기
```

### 12.4 카카오톡 알림 노이즈

일반 메신저 알림도 Notification 로그로 수집되었다.

```text
Package: com.kakao.talk
Type: Notification
Title: 쥬니
Body: 사진을 보냈습니다.
```

### 12.5 카카오맵 길안내 노이즈

배차 수락 후 카카오맵 길안내를 실행하면 카카오맵 접근성 로그가 대량 발생했다.

```text
Package: net.daum.android.map
Type: Accessibility
Text: 자전거도로 우선 / 2분 / 서울 관악구 신림동 1472-3 / 서울 관악구 신림동 1433-92 / 주행시작
```

### 인사이트

v1 Log Collector는 의도대로 모든 접근성/알림 로그를 수집했다.

하지만 v2의 메모 매칭 로직은 모든 로그를 대상으로 하면 오탐과 중복 처리가 많아질 가능성이 높다.

따라서 메모 매칭 로직은 우선 `com.woowahan.bros` 패키지로 제한하는 것이 적절하다.

---

## 13. 중복 로그 발생 특성

Accessibility 로그는 같은 화면이 유지되는 동안 짧은 시간 안에 매우 반복적으로 발생했다.

예를 들어 신규 배차 카드에서는 남은 시간이 줄어들면서 아래처럼 반복 로그가 발생했다.

```text
38초
37초
36초
35초
34초
33초
32초
31초
```

또한 같은 신규 배차 카드 내용이 1초 내외로 여러 번 반복 수집되었다.

```text
픽업지 / 푸라닭 신림점 / 전달지 / ... / 배차수락 / 37초
픽업지 / 푸라닭 신림점 / 전달지 / ... / 배차수락 / 37초
픽업지 / 푸라닭 신림점 / 전달지 / ... / 배차수락 / 37초
```

### 인사이트

v2에서 메모 팝업을 구현할 경우 중복 팝업 방지는 필수이다.

같은 업체명에 대해 매초 팝업이 반복되면 실사용이 불가능하다.

기본 정책은 다음처럼 잡는 것이 적절하다.

```text
동일 업체명 또는 동일 상호명+지점명에 대해서는 30초 이내 중복 팝업을 띄우지 않는다.
```

---

## 14. 업체명과 지점명 분리 가능성

실제 로그에서는 업체명이 하나의 문자열로 수집되었다.

```text
푸라닭 신림점
```

PickUpMemo v2에서 메모를 저장할 때는 상호명과 지점명을 분리하는 구조가 유리하다.

예시:

```text
상호명: 푸라닭
지점명: 신림점
전체 감지명: 푸라닭 신림점
```

매칭 방식은 다음과 같이 단순하게 시작할 수 있다.

```text
감지 텍스트에 상호명이 포함되어 있는가?
감지 텍스트에 지점명이 포함되어 있는가?
둘 다 포함되면 강한 매칭으로 본다.
```

예시:

```text
detectedText = "푸라닭 신림점"
storeName = "푸라닭"
branchName = "신림점"

결과:
상호명 포함 = true
지점명 포함 = true
강한 매칭
```

### 인사이트

v2에서 처음부터 복잡한 자연어 처리나 OCR을 사용할 필요는 없다.

우선은 `contains` 기반 문자열 매칭으로도 충분히 MVP를 검증할 수 있다.

---

## 15. 가게정보/찾아오는 길 데이터 수집 가능성

배차 수락 이후 상세 화면에서는 가게정보와 찾아오는 길 안내도 접근성 로그로 수집되었다.

실제 예시는 다음과 같다.

```text
가게정보 / 찾아오는 길 / 피크시간에는 매장 앞 도로가 혼잡하니 많은 라이더분들이 주차 하실수있도록 대각선으로 주차부탁드립니다!
```

### 인사이트

이 데이터는 v2의 필수 기능은 아니지만, 향후 확장 가능성이 있다.

예를 들어 향후 버전에서는 다음 기능을 검토할 수 있다.

- 가게별 주차 안내 자동 추출
- 자주 나오는 매장 안내 문구 저장
- 라이더 개인 메모와 실제 앱 내 안내 문구 비교
- 픽업 난이도 태그 추천

다만 v2에서는 자동 분석이나 AI 기능을 넣지 않고, 사용자가 직접 등록한 메모를 보여주는 수준으로 제한하는 것이 적절하다.

---

## 16. 배차 수락 후 지도 앱 전환 흐름

배차 수락 후 카카오맵이 실행되면서 아래 로그가 수집되었다.

```text
Package: net.daum.android.map
Type: Accessibility
Text: 서울 관악구 신림동 1472-3 | desc=출발지 입력, 서울 관악구 신림동 1472-3 | id=start_point / 서울 관악구 신림동 1433-92 | desc=도착지 입력, 서울 관악구 신림동 1433-92 | id=end_point
```

카카오맵 Notification도 수집되었다.

```text
Package: net.daum.android.map
Type: Notification
Title: 길안내 주행중
Body: 목적지: 서울 관악구 신림동 1433-92
```

### 인사이트

지도 앱에서는 주소와 길안내 정보는 수집되지만, PickUpMemo v2의 핵심인 업체 상호 매칭에는 직접적으로 필요하지 않다.

따라서 v2에서는 카카오맵 로그를 메모 매칭 대상에서 제외하는 것이 적절하다.

---

## 17. PickUpMemo v1 자체 로그 확인

v1 앱 자체 화면도 접근성 로그로 수집되었다.

```text
Package: com.lge.launcher3
Type: Accessibility
Text: PickUpMemo (검증 도구) | id=tvTitle / 접근성 서비스 : 실행 중 | id=tvAccessibilityServiceStatus / 접근성 권한 : 허가 | id=tvAccessibilityPermissionStatus / 알림 접근 권한 : 허가 | id=tvNotificationAccessStatus / 접근성 설정 열기 | id=btnAccessibilitySettings / 알림 접근 설정 열기 | id=btnNotificationSettings / 테스트 알림 생성 | id=btnTestNotification / 접근성 테스트 화면 | id=btnAccessibilityTest / 로그 보기 | id=btnLogView / 로그 내보내기 | id=btnLogExport
```

### 인사이트

v1 앱은 접근성 권한 상태, 알림 접근 권한 상태, 테스트 알림, 접근성 테스트 화면, 로그 보기, 로그 내보내기 기능을 정상적으로 제공했다.

또한 앱 자체 UI도 접근성 로그에 잡히므로, v2 개발 시 PickUpMemo 앱 자신의 로그가 메모 매칭 로직에 들어가지 않도록 주의해야 한다.

---

## 18. v1 검증 결과

v1의 기술 검증 결과는 성공으로 판단할 수 있다.

이유는 다음과 같다.

1. 배민커넥트 패키지명 `com.woowahan.bros`를 식별했다.
2. 신규 배차 카드의 접근성 로그를 수집했다.
3. 신규 배차 카드에서 업체 상호 `푸라닭 신림점`을 확인했다.
4. 업체 상호가 `픽업지`와 `전달지` 사이에 위치한다는 패턴을 확인했다.
5. 배차 수락 이후 상세 화면에서도 업체 상호를 다시 확인했다.
6. 상세 화면에서는 `가게전화` 다음에 업체 상호가 위치한다는 패턴을 확인했다.
7. Notification 로그만으로는 업체명 확보가 어렵다는 한계를 확인했다.
8. 접근성 로그가 반복적으로 발생하므로 중복 팝업 방지가 필요하다는 점을 확인했다.
9. System UI, 런처, 카카오톡, 카카오맵 등 노이즈 로그가 많아 패키지 필터링이 필요하다는 점을 확인했다.

---

## 19. v2 설계에 반영할 주요 인사이트

본 문서는 작업 지시서가 아니지만, v1 결과로부터 v2 설계에 반영할 수 있는 인사이트는 다음과 같다.

### 19.1 Accessibility 로그 우선

업체명은 Notification보다 Accessibility 로그에서 더 명확하게 확인되었다.

### 19.2 배민커넥트 패키지 우선

메모 매칭은 우선 `com.woowahan.bros` 로그만 대상으로 삼는 것이 안전하다.

### 19.3 신규 배차 카드 패턴 활용

신규 배차 카드에서는 다음 패턴이 핵심이다.

```text
id=신규배차_카드
픽업지 / 업체명 / 전달지
```

### 19.4 상세 화면 패턴 활용

배차 수락 이후 상세 화면에서는 다음 패턴이 핵심이다.

```text
가게전화 / 업체명 / 주소
```

### 19.5 중복 방지 필수

Accessibility 로그는 반복 발생하므로 동일 업체에 대한 중복 팝업 방지가 필수이다.

### 19.6 노이즈 필터링 필요

System UI, 런처, 카카오톡, 카카오맵, Android 공유 화면 로그는 메모 매칭 대상에서 제외하거나 낮은 우선순위로 처리해야 한다.

### 19.7 OCR은 아직 필요하지 않음

현재 수집 결과 기준으로 업체명이 접근성 로그에 명확히 포함되므로, v2 MVP 단계에서 OCR은 필요하지 않다.

---

## 20. v2 테스트용 기준 데이터

v1에서 실제 수집된 데이터를 기준으로 v2 테스트 데이터를 구성할 수 있다.

### 등록 메모 예시

```text
상호명: 푸라닭
지점명: 신림점
메모: 피크시간에는 매장 앞 도로가 혼잡할 수 있음. 대각선 주차 요청 문구 있음.
태그: 주차주의
```

### 신규 배차 카드 테스트 텍스트

```text
desc=배민배달, 조리완료, 픽업지, 푸라닭 신림점, 전달지, 서울 관악구 남부순환로161나길 13 **** (신림동), 포인트, 12.6P | id=신규배차_카드 / 배민배달 | 조리완료 | 픽업지 | 푸라닭 신림점 | 전달지 | 서울 관악구 남부순환로161나길 13 **** (신림동) | 배달료 | 2,410원 | 거절 | 배차수락 | 38초
```

### 상세 화면 테스트 텍스트

```text
가게 도착 | 조리완료 | 가게전화 | 푸라닭 신림점 | 신림동 1433-92 (신림동) | 주문정보 | 메뉴금액 | 가게정보 | 찾아오는 길 | 피크시간에는 매장 앞 도로가 혼잡하니 많은 라이더분들이 주차 하실수있도록 대각선으로 주차부탁드립니다!
```

### 기대 결과

```text
푸라닭 신림점 감지
상호명 "푸라닭" 포함 확인
지점명 "신림점" 포함 확인
저장된 메모와 강한 매칭
메모 팝업 표시 가능
```

---

## 21. 최종 결론

PickUpMemo v1 Log Collector를 통한 기술 검증 결과, 배민커넥트의 실제 신규 배차 화면에서 업체 상호를 접근성 로그로 수집할 수 있음을 확인했다.

특히 신규 배차 카드에서는 아래 패턴이 확인되었다.

```text
픽업지 / 푸라닭 신림점 / 전달지
```

배차 수락 이후 상세 화면에서는 아래 패턴이 확인되었다.

```text
가게전화 / 푸라닭 신림점 / 주소
```

따라서 PickUpMemo v2는 OCR이나 서버 기능 없이, 우선 Android Accessibility 로그 기반으로 업체명을 추출하고, 사용자가 저장한 상호명 + 지점명 메모와 매칭하여 팝업을 표시하는 MVP로 진행할 근거가 충분하다.

v1의 가장 큰 수확은 다음 한 문장으로 정리할 수 있다.

> 배민커넥트 신규 배차 화면의 업체명은 접근성 로그에서 확인 가능하며, `픽업지`와 `전달지` 사이에 업체명이 노출된다.