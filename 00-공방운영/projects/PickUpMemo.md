# PickUpMemo — 진행상황 (v1 → v2 → v3)

> 작성일: 2026-06-24 · 최종 갱신: 2026-06-24
> 문서 목적: Android 앱 PickUpMemo의 **버전별 전 과정**을 생략 없이 기록한다. 인덱스: [README.md](README.md)
> 한 줄 정의: 배달 라이더가 **배민커넥트 신규 배차**를 받을 때, 저장한 **가게 메모**(+v3: **경로 거리·예상시간**)를 화면 위 **단일 오버레이 팝업**으로 자동 표시하는 개인용 기술검증 앱.
> 성격: 실제 사용자(배달) 문제 기반 **기술검증**. 완성 상용 앱이 아님.

---

## 버전 요약
| 버전 | 핵심 | repo | 상태 |
|------|------|------|------|
| v1 | 배차 화면 텍스트 **수집 가능성** 검증(로그 수집기) | `LeeSiHyeon711/PickUpMemo` | 검증 완료 |
| v2 | 접근성 단독 전환 + 메모 CRUD + 오버레이 팝업 + 배민 로그 보관함 | `LeeSiHyeon711/PickUpMemo_v2` | 개발·빌드·실기기·납품 완료 |
| v3 | 같은 팝업에 **픽업→전달 경로 거리·예상시간 통합**(카카오 Local+모빌리티) | `LeeSiHyeon711/PickUpMemo_v2` | 빌드(Debug/Release)·실기기 테스트 완료 |

---

## v1 — Log Collector (기술검증)
- **목적**: "배민커넥트 배차 화면의 상호/지점/배차상태/텍스트를 Android **접근성 API 또는 알림**으로 읽어올 수 있는가?"를 검증.
- **방식**: 접근성 서비스 + 알림 리스너로 화면 텍스트·ContentDescription·View ID·Package·Activity·알림 제목/본문·발생시각을 수집.
- **산출물**: `projects/PickUpMemo/PickUpMemo_v1_LogCollector_결과보고서.md`(실제 로그 형태·인사이트 기록). 7단계 폴더 보유.
- **결과**: 수집 가능성 확인 → **v2의 기반**. (이 문서는 v2 지시서가 아니라 참고용 수확물 정리.)
- **상태**: ✅ 검증 완료.

---

## v2 — 메모 오버레이 앱 (풀사이클)
- **변경 핵심**: v1의 알림 리스너 기반 → **접근성 서비스 단독**으로 전환 + Room DB + 메모 CRUD UI + 오버레이 팝업 신규 구현.
- **7단계 진행**:
  - 1 상담(consultant·Sonnet): 요구사항-정의서 — 게이트 승인.
  - 2 기획(planner·Sonnet): PRD — 게이트 승인.
  - 3 설계(architect·Opus): 설계서 + **FEAT 13개**(1차 FEAT-01~09 승인, 1차 납품 후 실기기 피드백으로 v2.1 확장 FEAT-10~13 추가 작성·승인).
  - 4 GitHub(repo-manager·Sonnet): 이슈 #1~#13 + #14(버그수정) 등록. repo `PickUpMemo_v2`(v1과 별개 독립 repo).
  - 5 개발(builder·Sonnet): 이슈 14개 구현 + 빌드노트. *Bash 도구 차단 환경 → 공방장이 JDK21 수동 빌드(BUILD SUCCESSFUL, APK 5.8MB).*
  - 6 검수(reviewer·Sonnet): 정적 검증(AC 위반 0). 실기기 항목은 별도 체크리스트로 분리.
  - 수동 테스트(사람): **실기기 게이트 통과**.
  - 7 납품(delivery·Sonnet): 실행안내 + 고객전달문 + 증거자료체크리스트.
- **구현 기능**: 메모 CRUD(Room `memos`), 접근성 서비스(배민커넥트 `com.woowahan.bros` 단독), StoreExtractor(픽업지~전달지 상호 추출), MemoMatcher(상호+지점 동시 포함), DedupGuard(30초 중복억제), 오버레이 팝업, 배민 로그 보관함(`baemin_logs` 1000건 링버퍼)+내보내기(FileProvider), 권한 안내 허브, 테스트 화면.
- **생산성 메모**: v1→v2에서 builder Opus→**Sonnet** + FEAT 1:1 + 최소 문서로 **Builder 호출당 토큰 보수적 −27%**(근거 `docs/research/PickUpMemo_v1_v2_생산성비교.md`). 무거운 판단은 설계(architect)로 이동.
- **상태**: ✅ 개발·빌드·실기기·납품 완료, repo 반영.

---

## v3 — 경로 거리·시간 통합 (진행 완료, 일부 문서 미반영)
- **변경 핵심**: v2의 **단일 메모 팝업**에 **픽업→전달 실제 경로 거리 + 예상 소요시간**을 통합(별도 팝업을 만들지 않고 같은 팝업의 경로 영역만 비동기 갱신). 카카오 **Local API**(좌표화) + **카카오모빌리티 길찾기 API**.
- **버전업 보존**: 모든 산출물을 `_v3`로 별도 작성하여 v2 원본(설계서·FEAT-01~13) **보존**. v2 3대 기능(로그수집/메모CRUD/메모팝업) **회귀 0**을 절대 조건(AC-8)으로 명시. 설계서_v3에 **기존 코드 영향 분석표(위험도別)**를 두고 `MemoPopupController`·`PickupAccessibilityService`를 위험도 高로 지정, 기존 시그니처 위임 유지 + 회귀 검증 필수화.
- **7단계 진행 (토큰 실측)**:
  - 1 상담(Sonnet): 요구사항-정의서_v3 — 22.8k.
  - 2 기획(Sonnet): PRD_v3 — 34.9k.
  - 3 설계(Opus): 설계서_v3 + **FEAT-14~23(10개)** — 109.1k (엔드포인트 위험 격리·timeout 정책 등 무거운 판단 집중).
  - 4 GitHub: 이슈 **#15~#24** 등록. *Bash 차단으로 repo-manager 대신 공방장이 gh 실행.* **이슈 오프셋: 이슈 #N = FEAT-(N−1)**(v2 #14 점유 때문).
  - 5 개발:
    - FEAT-14(#15) 인프라(INTERNET·OkHttp 4.12·`KAKAO_REST_API_KEY` BuildConfig 주입) — builder Sonnet 53.5k, 빌드 성공.
    - FEAT-15(#16) 경로 모델 + RouteProvider 인터페이스(`GeoPoint`/`RouteInfo`/`GeoQueryType`) — builder Sonnet 46.8k, 빌드 성공.
    - **FEAT-16~23(#17~#24)** — usage 절약을 위해 **인수인계 문서 작성 후 Cursor(외부 AI)에 위임**, 구현 + 실기기 테스트까지 수행(본 세션 토큰 미집계).
  - 검수/마감(공방장·Opus): FEAT별 검증·가드레일 전수 검증·이슈 close·commit/push·README/요약 갱신.
- **신규 구현(v3)**: AddressExtractor(픽업/전달 주소 2소스 fallback), RouteService(캐싱 + 전체 5초 `withTimeoutOrNull` + 전달지 **4단계 geocode fallback** + 실패 원인 분류), KakaoRouteProvider(픽업=키워드/전달=주소검색), MemoPopupController 경로 영역 + `AtomicLong` 토큰 세대 경합 방지, 키 부재 시 무크래시("거리 정보 확인 불가"), debug/release 표시 분기. **v3.1 오르막 주의 = 골격만(동작 없음)**.
- **빌드/테스트**: **Debug + Release 모두 성공(JBR 21)**, APK 2종. *머신 기본 JDK가 Java 25면 Kotlin 플러그인 비호환 → JBR 21 지정 필수.* **실기기 테스트 완료.**
- **개발 분담**: Claude Code = 상담·기획·설계·FEAT-14·15 + 전 공정 오케스트레이션/검수. Cursor = FEAT-16~23 구현 + 실기기.
- **상태**: ✅ 코드·이슈 = PickUpMemo_v2 repo 반영 완료(이슈 #15~#24 closed, working tree clean). ✅ **공방 repo(IT_make_some)에도 v3 공정 산출물 반영 완료**(2026-06-24, commit `7ace2a6` — `_v3` 문서·FEAT-14~23·인수인계).

---

## 현재 상태 / 다음
- v3 기능 개발·빌드·실기기까지 **완료**. 공정 산출물도 공방 repo 반영 완료.
- v3.1(오르막 주의 실구현)은 **보류**(다음 버전 후보).

## 비고 / 주의
- 배민커넥트 인식은 **본인 단말 개인용 기술검증** 목적. 화면 텍스트/로그는 기기 로컬 Room에만 저장(서버 전송 없음). v3에서 추가된 외부 통신은 **지도 API 호출(거리/시간)뿐**.
- 개발 코드/`.env`·`local.properties`는 PickUpMemo / PickUpMemo_v2 **자체 repo**에 있음. 공방 repo로 가져오지 않는다.
