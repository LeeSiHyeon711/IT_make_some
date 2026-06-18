# 10 · PlayMCP 등록 전략 (Registration Plan)

> 상상공방 Lite를 **Agentic Player 10 공모전**에 출품하기 위한 MCP 노출·배포 전략 문서.
> 예선 참가 조건: **PlayMCP in KC로 Streamable HTTP Remote MCP 서버를 배포**하고, 그 **Endpoint URL을 PlayMCP 개발자 콘솔에 등록**해야 한다.
> **노출 전략(변경)**: 기존 단일 tool 안을 폐기하고, MVP에서 **3개 tool**(`prepare_intake` / `diagnose_idea` / `design_first_experiment`)을 노출한다.
> tool 3개의 **전체 스펙(name·description·inputSchema·output·annotations)은 `11-mcp-tool-spec.md`** 에 둔다. 본 문서는 등록 조건·노출 전략·진행 순서·체크리스트를 다룬다.

---

## 1. PlayMCP 등록을 위해 확인해야 할 항목

### 1-1. PlayMCP 기술 제약 (개발가이드 반영 — 반드시 준수)

| # | 제약 | 상상공방 Lite 대응 |
|---|------|---------------------|
| 1 | **Streamable HTTP 방식 Remote MCP만 지원** | stdio/SSE 아님. Streamable HTTP 엔드포인트로 구현·배포 |
| 2 | **Stateless MCP 서버 권장** | 서버는 세션을 들지 않는다. 각 tool은 앞 tool의 출력을 **입력으로 되돌려 받아** 잇는다(2-3 참조) |
| 3 | **공개 URL 접근 가능해야 함** | PlayMCP in KC가 발급한 **공개 HTTPS Endpoint** 사용 (localhost 불가) |
| 4 | **MCP Inspector로 사전 점검** | 콘솔 등록 전에 Inspector로 `tools/list`·호출·스키마 검증 (체크리스트는 11 문서) |
| 5 | **Tool 개수 3~10개 권장** | MVP는 **3개**(`prepare_intake`/`diagnose_idea`/`design_first_experiment`) — 권장 범위 하한 충족 |
| 6 | **tool name / server name에 "kakao" 포함 금지** | 서버명·tool명에 `kakao` 문자열을 쓰지 않는다(11 문서 금지어 주의) |
| 7 | **각 tool에 name·description·inputSchema·annotations 필수** | 3개 tool 모두 4요소를 갖춘다(11 문서 스펙) |
| 8 | **annotations에 title·readOnlyHint·destructiveHint·openWorldHint·idempotentHint 모두 지정** | 5개 힌트를 명시(11 문서) |
| 9 | **tool result 최소화 — API 원문 그대로 반환 금지, 정제된 텍스트/JSON/마크다운** | 각 tool은 `display_text`(정제 카드) + 최소 구조화 객체만 반환. 내부 enum·원문 비노출 |
| 10 | **응답속도 p99 3000ms 필수** | **tool당 LLM 호출 1회 이하**로 설계. formatter는 LLM 없이 템플릿 렌더(2-4 참조) |

### 1-2. 카카오 계정 로그인
- PlayMCP 개발자 콘솔은 **카카오 계정 로그인** 기반이다.
- 출품에 쓸 카카오 계정을 확정하고(응모자 본인 명의 권장), 프로필 정보(닉네임·이메일·연락처)가 응모자 정보와 일치하는지 확인한다.

### 1-3. Remote MCP 서버 등록 방식 — PlayMCP in KC 배포 필수
- **예선 조건**: 상상공방 Lite를 **PlayMCP in KC(카카오클라우드 기반 PlayMCP 배포 환경)** 로 배포하고, 거기서 발급받은 **MCP Endpoint URL을 PlayMCP에 등록**한다. 임의 외부 호스팅이 아니라 **PlayMCP in KC가 발급한 Endpoint가 기준**이다.
- 전송 방식은 **Streamable HTTP 고정**(1-1 #1).
- 흐름: 로컬 개발 → MCP Inspector 점검 → PlayMCP in KC 배포 → Endpoint URL 획득 → 콘솔 임시 등록 → 정보 불러오기 성공 확인 (상세는 6장).

#### PlayMCP in KC 이용 조건 (이용가이드 반영)
- **무상 지원**: 공모전 **참가작에 한해 예선 접수 기간 동안** MCP 서버 배포를 무상 지원한다.
- **계정당 최대 2대**까지 MCP 서버 등록 가능 → 운용 전략은 12 문서(개발/스테이징 1 + 제출용 1 분리 권장).
- **배포 방식 2가지** (둘 다 **Docker 기반**):
  - ① **컨테이너 이미지 등록** — 미리 빌드한 이미지를 레지스트리로 등록.
  - ② **Git 소스 빌드** — Git 레포를 연결해 빌드. **이 방식도 Dockerfile이 반드시 필요**하다.
- **상상공방 Lite 기본 전략 = ② Git 소스 빌드.** (레포·문서·코드 관리 자연스러움, 레지스트리 준비 부담↓, MVP 개발 속도↑) 컨테이너 이미지 방식은 후속 운영 배포 후보로 남긴다.
- **Apple Silicon Mac에서 직접 이미지를 빌드할 경우 `linux/amd64` 아키텍처로 빌드**해야 한다.
- 상세 배포 전략·프로젝트 구조·Dockerfile·체크리스트는 **→ `12-deployment-plan.md`** 참조.

### 1-4. 서버 URL · 메타데이터 · 도구 스키마 입력 여부
- **서버 URL**: PlayMCP in KC Endpoint (예: `https://<배포도메인>/mcp`).
- **서버 메타데이터**: 서버 이름(**"kakao" 금지**, 후보는 11 문서), 한 줄 설명, 아이콘, 카테고리/태그.
- **도구 스키마**: 콘솔이 Endpoint의 `tools/list`로 **자동 불러오기(import)** 하는지 확인. 3개 tool과 각 inputSchema·annotations가 의도대로 인식되어야 한다(11 문서).

### 1-5. 공개 / 비공개 전환 방식
- 콘솔 등록 직후 **임시(draft)** 상태 → 채팅 테스트 → **심사 요청 → 승인 → 전체 공개(published)** 흐름이다(6장).
- 공모전 비즈폼 접수에는 **공개 MCP 상세 URL**이 필요하므로 공개 전환을 마감 전에 끝낸다.

### 1-6. 테스트 채팅에서 도구 호출 확인
- PlayMCP AI 채팅에서 3개 tool이 노출·호출되는지 확인한다.
- 확인 포인트: 자유 서술 → `prepare_intake`가 접수 확인 카드 반환 → (사용자 승인) → `diagnose_idea` 균열점 → `design_first_experiment` 결과카드. 내부 코드값(`SELF`, `crack_point` 등) 비노출.

### 1-7. 공모전 응모 페이지(비즈폼)에서 요구하는 정보
- 서비스명/한 줄 소개, 카테고리·태그, **공개 MCP 상세 URL**, 사용 시나리오·데모, 차별점, 추가 개발 계획, 응모자 정보·연락처, 라이선스/동의. (문안은 5장)

---

## 2. 상상공방 Lite MCP 노출 전략 (3 tool)

### 2-1. 원칙 — "안은 파이프라인, 밖은 3개의 깔끔한 도구"
- 내부 5단계(소통 → 공방 접수 확인 → 균열점 진단 → 첫 실험 설계 → 결과카드, 02 문서)를 **PlayMCP 권장(3~10개)에 맞춰 3개 tool**로 노출한다.
- 3개로 쪼개되, 각 tool은 **앞 tool의 출력을 입력으로 받는 순차 파이프라인**을 유지한다(stateless).

### 2-2. tool 3개 ↔ 내부 단계 매핑

| MCP tool | 내부 단계 | 한 줄 책임 |
|----------|-----------|------------|
| `prepare_intake` | 소통 + 공방 접수 확인 | 자유 서술 → 접수 확인 카드 생성(아이디어 요약·문제·처음 쓸 사람·출처·성숙도·검증 시간) |
| `diagnose_idea` | 아이디어 진단 | 승인된 접수 → 균열점 1개 + 착각 가능성(≤2) + 좋은 신호(≤2) |
| `design_first_experiment` | 첫 실험 설계 **+ 출력(결과카드)** | 균열점·시간 예산 → 첫 검증 미션·해야 할 일·왜 이 실험·성공 기준·안 만들어도 되는 것·다음 단계 → **결과카드까지 formatter로 완성** |

> **출력 에이전트(07)의 결과카드 작성 책임은 `design_first_experiment`의 formatter 단계로 흡수**한다. 별도 4번째 tool을 만들지 않는다(tool 수·LLM 호출 절약). formatter는 LLM 없이 템플릿 렌더(2-4).

### 2-3. 유일한 사람 게이트 — tool 사이에서 보존 (stateless)
- 워크플로의 유일한 승인 게이트는 **공방 접수 확인**(04 문서)이다. 3-tool 구조에서 이 게이트는 **`prepare_intake`와 `diagnose_idea` 사이**에 산다.
- 흐름:
  1. 클라이언트(PlayMCP AI 채팅)가 `prepare_intake` 호출 → **접수 확인 카드**(`display_text`) + 구조화 `intake` 객체를 받는다.
  2. 사용자에게 카드를 보여주고 **승인/수정**을 받는다 (사람 게이트).
  3. 클라이언트가 `diagnose_idea`를 호출하며 **1단계의 `intake` 객체를 `confirmed_intake`로 되돌려 전달**(+ 선택 `correction_text`). → 서버는 세션 없이 이어간다(stateless).
  4. 이어서 `design_first_experiment`에 `confirmed_intake` + `diagnosis`를 전달 → 결과카드.
- 각 tool 응답의 **`next_action`** 이 클라이언트에게 "다음에 어떤 tool을 부를지"를 안내해 파이프라인을 정확히 잇는다.

### 2-4. p99 3000ms 대응 — tool당 LLM 호출 최소화
- **tool당 LLM 호출 1회 이하**가 원칙이다.
  - `prepare_intake`: 자유 서술 구조화 LLM 1회.
  - `diagnose_idea`: 균열점 진단 LLM 1회.
  - `design_first_experiment`: 미션 설계 LLM 1회 + **formatter(결과카드 렌더)는 LLM 없이 템플릿**으로 처리.
- 다단계 체이닝(한 tool 안에서 LLM 여러 번 호출)을 금지한다. 분기·검증은 가능한 한 **결정적 코드**로 처리한다.
- 모델은 **응답이 빠른 모델**을 우선 배치하고, 프롬프트·출력 토큰을 줄여 지연을 낮춘다. (상세 전략은 11 문서)
- tool result는 **최소화**(1-1 #9): 정제된 `display_text` + 라벨화된 최소 구조화 객체만. API 원문/장문 비노출 → 전송·렌더 지연도 감소.

### 2-5. 왜 단일 tool에서 3 tool로 바꿨나
- PlayMCP가 **3~10개 tool을 권장**(1-1 #5)하고, **각 tool에 annotations를 요구**(#7·#8)한다 — 단일 tool은 권장 범위 하한에 못 미치고, 단계별 의미(read-only·non-destructive 등)를 힌트로 드러내기 어렵다.
- 3개로 나누면 **단계별 책임이 명확**해지고, 후속 고도화(tool 추가)도 자연스럽다(4장).
- 그래도 **차별점인 "절차"는 유지**된다 — `diagnose_idea`는 `confirmed_intake`를 입력으로 강제해, 접수 확인을 건너뛴 진단을 막는다.

---

## 3. tool 스펙 위치

3개 tool의 **전체 스펙**(name / 영문 description / inputSchema / output 구조 / annotations / p99 전략 / Inspector 체크리스트)은 별도 문서로 분리했다.

> **→ `11-mcp-tool-spec.md` 참조.**

요약만 옮기면:

| tool | name | 입력 핵심 | 출력 핵심 |
|------|------|-----------|-----------|
| 접수 확인 | `prepare_intake` | `idea_text`, `validation_time_budget?` | `intake`(요약·문제·사용자·출처·성숙도·시간) + 접수 확인 카드 |
| 균열점 진단 | `diagnose_idea` | `confirmed_intake`, `correction_text?` | `diagnosis`(균열점 1 + 착각 ≤2 + 신호 ≤2) |
| 첫 실험 설계 | `design_first_experiment` | `confirmed_intake`, `diagnosis`, `validation_time_budget?` | `result_card`(미션·성공기준·안 만들 것·다음 단계) + 완성 결과카드 |

모든 tool: result 최소화 + 내부 enum 비노출 + `next_action`으로 다음 단계 안내(11 문서).

---

## 4. 후속 고도화 시 tool 추가/확장 후보

> MVP 3 tool이 안정화되면 PlayMCP 권장 상한(10개) 안에서 확장한다.

| 추가 후보 tool | 역할 | 비고 |
|----------------|------|------|
| `record_validation_result` | 미션 수행 결과를 받아 재진단 입력으로 (재진단 루프) | 02 문서 후속 후보 |
| `compose_result_card` | 결과카드 렌더를 별도 tool로 분리(현재는 `design_first_experiment` formatter 내장) | 내보내기(PDF/이미지)와 함께 |
| `connect_to_workshop` | 검증 통과 아이디어를 IT상상공방 1단계(상담)로 연결 | 01 문서 후속 후보 |

> 확장해도 **3~10개 권장 범위**를 지키고, 단계 순서 의존성(입력 강제)을 유지한다.

---

## 5. 공모전 제출용 설명 초안

### 5-1. 서비스 한 줄 소개
> **상상공방 Lite — 막연한 아이디어를 '만들 것'이 아니라 '먼저 확인할 것'으로 바꿔주는 AI 공방.**
> 아이디어를 접수 확인하고, 가장 먼저 깨질 전제(균열점)를 진단해, 당신의 시간 안에서 오늘 할 수 있는 첫 검증 미션을 결과카드로 돌려줍니다.

### 5-2. 사용자 시나리오
1. 사용자가 "배달 라이더용 동선 최적화 앱을 만들고 싶다"를 **자유롭게 서술**한다.
2. `prepare_intake` → AI가 이해한 내용을 **🧾 공방 접수 확인 카드**로 보여주고 "이대로 시작할까요?"를 묻는다 (유일한 사람 게이트).
3. 승인하면 → `diagnose_idea`가 **🔍 균열점**(예: "라이더가 정말 동선 때문에 손해를 본다고 느끼는가?") 1개로 압축한다.
4. `design_first_experiment`가 시간 예산(예: 2일)에 맞춰 **🧪 첫 검증 미션**(라이더 5명 인터뷰)을 설계하고 **🧾 공방 결과카드** 한 장으로 완성한다.
5. 사용자는 앱을 짓기 전에 **오늘 할 수 있는 가장 작은 검증 행동**을 손에 쥔다.

### 5-3. 차별점
- 칭찬기/기획서 생성기가 아니다 — **가장 먼저 깨질 전제 1개**로 좁힌다.
- **만들지 않는 방법을 먼저** 제안한다(06 문서).
- 사용자의 **시간 안에서** 미션 난이도를 맞춘다(30분~2주).
- **절차가 차별점이다** — 접수 확인 → 진단 → 미션을 거치는 과정이 가치(단순 프롬프트 래퍼와의 차이).
- 결과는 보고서가 아니라 **한 화면 행동 카드**.

### 5-4. 추가 개발 계획
- **재진단 루프**(`record_validation_result`), **결과카드 내보내기**(`compose_result_card` + PDF/이미지), **본 공방 연결**(`connect_to_workshop`). (4장)
- 병렬 진단(여러 `diagnosis_focus` 동시) — 02 문서 후속 후보.

---

## 6. 공모전 진행 순서 (Agentic Player 10 예선)

| # | 단계 | 무엇을 / 산출물 | 검증 포인트 |
|---|------|------------------|-------------|
| 1 | **로컬 MCP 서버 개발** | 3개 tool(`prepare_intake`/`diagnose_idea`/`design_first_experiment`) Streamable HTTP로 구현 (11 문서 스펙) | 로컬에서 `tools/list`·3-tool 순차 호출 동작 |
| 2 | **MCP Inspector 사전 점검** | Inspector로 연결·도구 목록·스키마·호출·annotations 검증 (11 문서 체크리스트) | 3개 tool 모두 정상 호출, 응답 p99 3000ms 내 |
| 3 | **PlayMCP in KC 배포** | Dockerfile 포함 레포를 **Git 소스 빌드** 방식으로 PlayMCP in KC에 배포 (12 문서) | 빌드·배포 성공·서버 기동 |
| 4 | **Endpoint URL 획득** | PlayMCP in KC 발급 **공개 HTTPS Endpoint** 확보 | 외부 HTTPS 접근됨(localhost 아님) |
| 5 | **PlayMCP 개발자 콘솔 임시 등록** | 콘솔에 Endpoint URL **임시(draft) 등록** | 카카오 로그인 + URL 입력 완료 |
| 6 | **정보 불러오기 성공 확인** | 콘솔이 Endpoint에서 서버 메타·도구 스키마 **불러오기(import)** | 3개 tool + inputSchema·annotations 인식 |
| 7 | **도구함 추가 후 AI 채팅 테스트** | 도구함 추가 후 PlayMCP AI 채팅에서 호출 | 접수 확인 카드→진단→결과카드 흐름 재현 |
| 8 | **심사 요청** | 검증 끝난 MCP에 **심사(승인) 요청** | 심사 요청 상태 전환 |
| 9 | **승인 후 전체 공개** | 심사 승인 → **전체 공개(published)** | 공개 + 공개 MCP 상세 페이지 생성 |
| 10 | **공모전 비즈폼 접수** | **공개 MCP 상세 URL**로 공모전 **비즈폼** 제출 | 5장 문안·상세 URL 포함 접수 완료 |

> 핵심 의존성: **2(Inspector) 통과 없이 3 배포로 넘어가지 않는다.** **7 테스트 통과 없이 8 심사 요청을 하지 않는다.** 10 비즈폼 접수에는 **9에서 생성된 공개 MCP 상세 URL이 반드시 필요**하다.

---

## 7. 사람이 직접 확인해야 할 체크리스트

> 자동화로 대체할 수 없는, 사람이 직접 눈으로 대조·클릭해야 하는 항목. (6장과 1:1 대응. Inspector 세부 항목은 11 문서.)

### 계정·접근
- [ ] 개발자 콘솔에 **카카오 계정으로 로그인**되는가 (출품에 쓸 계정).
- [ ] 프로필 정보(닉네임·이메일·연락처)가 응모자 정보와 일치하는가.

### 1~2 · 로컬 개발 → Inspector 점검
- [ ] 3개 tool이 로컬 Streamable HTTP에서 동작하는가.
- [ ] **MCP Inspector**로 `tools/list`·3-tool 호출·스키마·annotations가 통과하는가 (11 문서 체크리스트).
- [ ] tool/server name에 **"kakao" 문자열이 없는가**.
- [ ] 각 tool 응답이 **p99 3000ms 내**인가 (tool당 LLM 호출 1회 이하 확인).

### 3~6 · KC 배포 → Endpoint → 콘솔 등록 → 불러오기
- [ ] **PlayMCP in KC 배포가 성공**했는가.
- [ ] **Endpoint URL을 확보**했고 외부 HTTPS로 접근되는가.
- [ ] 콘솔에 Endpoint URL을 **임시 등록**했는가.
- [ ] 콘솔 **정보 불러오기(import)가 성공**하는가.
- [ ] 불러온 도구가 **3개**(`prepare_intake`/`diagnose_idea`/`design_first_experiment`)로 보이는가.
- [ ] 각 tool의 description·inputSchema·annotations(11 문서)가 정확히 인식되는가.

### 7 · 도구함 추가 → AI 채팅 테스트
- [ ] 도구를 **도구함에 추가**했는가.
- [ ] 채팅에서 `prepare_intake` → **접수 확인 카드**가 돌아오는가.
- [ ] 승인/수정 후 `diagnose_idea` → **균열점**이 나오는가.
- [ ] `design_first_experiment` → **공방 결과카드**까지 완성되는가.
- [ ] `next_action` 안내대로 클라이언트가 3-tool을 자연스럽게 잇는가.
- [ ] 응답에 **내부 코드값(`SELF`, `crack_point`, `RAW` 등)이 새지 않는가**.
- [ ] 결과카드가 한 화면 분량인가 (보고서가 아닌 행동 카드).

### 8~10 · 심사 → 공개 → 비즈폼 접수
- [ ] 채팅 테스트 통과 후 **심사(승인) 요청**을 제출했는가.
- [ ] 승인 후 **전체 공개(published)** 로 전환했는가.
- [ ] **공개 MCP 상세 URL**을 확보했는가.
- [ ] 공모전 **비즈폼**에 5장 문안 + 공개 MCP 상세 URL을 넣어 **접수 완료**했는가.
- [ ] 데모 영상·캡처가 요구되면 준비했는가.
- [ ] 제출 마감 **전에** 9(공개)·10(비즈폼 접수)를 마쳤는가.
- [ ] 라이선스/저작권·개인정보 동의 항목을 확인했는가.

---

## 8. 핵심 결정 요약

1. **노출 형태(변경)**: 단일 tool 안을 폐기하고 **3개 tool**(`prepare_intake`/`diagnose_idea`/`design_first_experiment`)로 노출 — PlayMCP 3~10개 권장 충족. 출력 에이전트의 결과카드 책임은 `design_first_experiment` formatter로 흡수.
2. **게이트 처리**: 유일한 사람 게이트(공방 접수 확인)는 **`prepare_intake`↔`diagnose_idea` 사이**에서 보존. `diagnose_idea`가 `confirmed_intake`를 입력으로 강제해 접수 확인 생략을 차단.
3. **stateless 연결**: 서버는 세션을 안 든다. 앞 tool 출력(`intake`/`diagnosis`)을 다음 tool 입력으로 되돌려 받아 잇고, `next_action`으로 다음 호출을 안내.
4. **p99 3000ms**: **tool당 LLM 호출 1회 이하**, formatter는 LLM 없이 템플릿 렌더, result 최소화(원문 비노출).
5. **PlayMCP 제약 준수**: Streamable HTTP·stateless·공개 URL·**MCP Inspector 사전 점검**·tool당 name/description/inputSchema/annotations(5힌트 전부)·**"kakao" 금지**.
6. **배포 경로**: 로컬 개발 → Inspector 점검 → KC 배포 → Endpoint → 콘솔 임시 등록 → 불러오기 → 도구함·채팅 테스트 → 심사 요청 → 공개 → 공개 MCP 상세 URL로 비즈폼 접수.
7. **배포 방식(신규)**: PlayMCP in KC를 **Git 소스 빌드 방식**으로 기본 채택(Dockerfile 필수). 컨테이너 이미지 방식은 후속 운영 후보. 무상 지원·계정당 2대 제한. Apple Silicon은 `linux/amd64` 빌드. 상세는 `12-deployment-plan.md`.
8. **상세 스펙 분리**: 3개 tool 전체 스펙은 `11-mcp-tool-spec.md`, 배포 전략은 `12-deployment-plan.md`.
