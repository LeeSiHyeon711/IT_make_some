# 11 · MCP Tool 스펙 (Tool Specification)

> 상상공방 Lite를 PlayMCP에 노출하기 위한 **MCP 서버·tool 상세 스펙**.
> 노출 전략·등록 절차는 `10-playmcp-registration-plan.md` 참조. 본 문서는 **구현 기준 스펙**이다.
> MVP는 **3개 tool**: `prepare_intake` → `diagnose_idea` → `design_first_experiment` (순차, stateless).

---

## 1. MCP 서버 이름

### 1-1. 서버 이름 후보 (택1)

| 후보 | 비고 |
|------|------|
| `sangsang-lite` | 짧고 명확. 권장 1순위 |
| `sangsang-workshop-lite` | 공방(workshop) 의미 강조 |
| `idea-validation-workshop` | 영문 기능 설명형(검색 노출 유리) |
| `imagination-workshop-lite` | 브랜드 직역형 |

> 표시명(한글): **상상공방 Lite** / 한 줄 설명은 10 문서 5-1.

### 1-2. 금지어 주의 — "kakao" 포함 금지 ⚠️
- **tool name·server name 어디에도 `kakao` 문자열을 포함하지 않는다.** (대소문자·부분 문자열 모두 — `Kakao`, `KAKAO`, `kakaotalk` 등도 금지)
- 위 서버 이름 후보·아래 tool 이름(`prepare_intake`/`diagnose_idea`/`design_first_experiment`)은 모두 `kakao`를 포함하지 않음 → 안전.
- 설명·태그·아이콘 등 메타데이터에서도 카카오 상표를 사칭/오인할 표현을 피한다.

---

## 2. 공통 설계 규약

- **전송**: Streamable HTTP. **서버는 stateless** — 세션을 들지 않고, 앞 tool 출력을 다음 tool 입력으로 되돌려 받는다.
- **런타임·배포**: Docker 컨테이너로 실행되며 HTTP 포트를 연다(포트 규칙은 12 문서에서 확인 예정). PlayMCP in KC **Git 소스 빌드 방식**으로 배포하므로 레포에 **Dockerfile이 필수**다. Apple Silicon에서 직접 빌드 시 `linux/amd64`. 상세는 **`12-deployment-plan.md`**.
- **result 최소화**: 각 tool은 `display_text`(정제된 마크다운 카드/요약) + **라벨화된 최소 구조화 객체**만 반환. API 원문·장문 로그·내부 enum 코드(`SELF`/`crack_point`/`RAW` 등) **비노출**.
- **`next_action`**: 모든 tool 응답에 포함. 클라이언트가 다음에 부를 tool을 안내해 3-tool 파이프라인을 잇는다.
- **언어**: 사용자 노출 텍스트(`display_text`)는 **한글**. tool `description`은 PlayMCP 노출·검색을 위해 **영문**(아래 스펙).
- **LLM 호출**: tool당 **1회 이하** (p99 3000ms, 5장).

### 2-1. Transport — Streamable HTTP (MCP 공식 Transport 반영)

- **Streamable HTTP만 구현한다.** PlayMCP는 Streamable HTTP만 지원하므로 **stdio transport는 공모전용 구현 범위에서 제외**한다.
- **단일 HTTP endpoint**를 제공한다. 기본 후보: **`/mcp`**.
- 클라이언트는 **JSON-RPC 메시지를 HTTP `POST`** 로 `/mcp`에 전송한다. 서버는 이 POST를 받아 JSON-RPC 요청(`tools/list`, `tools/call` 등)을 처리한다.
- **응답은 `application/json` 단일 응답을 우선**한다. 상상공방 Lite MVP는 **streaming이 필수가 아닌** 서비스다 — 각 tool은 카드/결과를 한 번에 완성해 돌려준다.
- **SSE(`text/event-stream`) 응답은 후속 고도화 후보**로 둔다(부분 토큰 스트리밍·진행 표시가 필요해질 때).
- **HTTP `GET` 기반 SSE stream은 MVP에서 적극 제공하지 않는다.** 필요 시 **MCP SDK 기본 동작에 맡긴다**(별도 구현하지 않음).
- **바인딩**: 로컬 개발은 보안상 **localhost 바인딩 우선**(외부 노출 금지). 배포본은 **PlayMCP in KC가 발급한 공개 Endpoint URL**로 접근한다(12 문서).

> 정리: `POST /mcp` + `application/json` 단일 응답 + stdio 제외 + GET SSE는 SDK 기본값. 이 조합이 MVP의 가장 단순하고 안정적인 표면이다.

### 2-2. Stateless input chaining (서버 세션 없음)

PlayMCP 가이드의 **stateless 권장**에 맞춰 **서버는 어떤 세션도 저장하지 않는다.** 단계 연결은 전적으로 **입력 체이닝**으로 한다.

```
prepare_intake(idea_text)            →  intake            (서버는 기억하지 않음)
        │  클라이언트가 intake를 들고 있다가
        ▼
diagnose_idea(confirmed_intake=intake) →  diagnosis        (서버는 기억하지 않음)
        │  클라이언트가 intake + diagnosis를 들고 있다가
        ▼
design_first_experiment(confirmed_intake=intake, diagnosis=diagnosis) → result_card
```

- `prepare_intake` 결과(`intake`)는 **서버가 기억하지 않고**, 이후 `diagnose_idea` 호출 시 **`confirmed_intake` 입력으로 다시 전달받는다**.
- `diagnose_idea` 결과(`diagnosis`)도 **서버가 기억하지 않고**, `design_first_experiment` 호출 시 **`confirmed_intake`와 `diagnosis`를 함께 입력**받는다.
- 따라서 각 HTTP 요청은 완전히 독립적이며(서버 메모리·세션 ID 불필요), 수평 확장·재시작에 안전하다.

---

## 3. Tool 1 — `prepare_intake`

### 3-1. name
`prepare_intake`

### 3-2. description (영문)
> Takes the user's free-form idea description and produces a structured "workshop intake confirmation" card. Summarizes the idea, the problem it addresses, the first likely user, the source of the pain (self-experienced / observed / assumed / imagined), the idea's maturity, and the time budget available for validation. Returns a human-readable confirmation card for the user to approve or correct before diagnosis. Does not evaluate or criticize the idea.

### 3-3. inputSchema

```json
{
  "type": "object",
  "properties": {
    "idea_text": {
      "type": "string",
      "description": "User's free-form description of the service/app/automation idea, the inconvenience, target users, and any reactions from others. May be unstructured."
    },
    "validation_time_budget": {
      "type": "string",
      "enum": ["30_MIN", "TODAY", "TWO_DAYS", "ONE_WEEK", "TWO_WEEKS_PLUS", "UNKNOWN"],
      "default": "UNKNOWN",
      "description": "Time the user can invest in validation. Drives later mission difficulty. UNKNOWN if not stated."
    }
  },
  "required": ["idea_text"]
}
```

### 3-4. output 구조 (정제)

```json
{
  "type": "object",
  "properties": {
    "display_text": {
      "type": "string",
      "description": "Markdown 접수 확인 카드 (04 문서 양식). 사용자에게 그대로 노출. 내부 enum 비노출."
    },
    "intake": {
      "type": "object",
      "description": "다음 tool(diagnose_idea)에 confirmed_intake로 되돌려 보낼 구조화 접수 내용. 라벨화.",
      "properties": {
        "input_summary":      { "type": "string" },
        "problem":            { "type": "string" },
        "target_user":        { "type": "string" },
        "pain_source_label":  { "type": "string", "description": "직접 경험 / 주변인 관찰 / 시장 추정 / 상상 확장" },
        "maturity_label":     { "type": "string", "description": "막연함 / 문제 정의됨 / 해결책까지 있음" },
        "time_budget_label":  { "type": "string", "description": "30분 이내 / 오늘 안에 / 2일 이내 / 1주일 이내 / 2주 이상 / (미정)" },
        "needs_clarification":{ "type": "boolean" },
        "clarifying_question":{ "type": "string", "description": "needs_clarification=true일 때 최대 1~2개 질문" }
      }
    },
    "next_action": {
      "type": "object",
      "properties": {
        "type":        { "type": "string", "enum": ["CONFIRM_INTAKE"] },
        "next_tool":   { "type": "string", "enum": ["diagnose_idea"] },
        "user_prompt": { "type": "string", "description": "예: '이대로 진행할까요? 고칠 부분이 있으면 알려주세요'" }
      },
      "required": ["type", "next_tool"]
    }
  },
  "required": ["display_text", "intake", "next_action"]
}
```

### 3-5. annotations

```json
{
  "title": "공방 접수 확인 (Prepare Intake)",
  "readOnlyHint": true,
  "destructiveHint": false,
  "openWorldHint": false,
  "idempotentHint": false
}
```

> `readOnlyHint: true` — 외부 상태를 바꾸지 않고 입력을 구조화만 한다.
> `openWorldHint: false` — 웹/외부 API를 조회하지 않고 제공된 입력만 다룬다.
> `idempotentHint: false` — LLM 생성이라 동일 입력에도 출력이 달라질 수 있어 보수적으로 false.

---

## 4. Tool 2 — `diagnose_idea`

### 4-1. name
`diagnose_idea`

### 4-2. description (영문)
> Takes the user-approved intake and finds the single assumption most likely to break first if the idea fails (the "crack point"). Returns one crack point, up to two possible misreadings, and up to two positive signals. Frames findings as "the first thing to verify," not as criticism. Requires the confirmed intake as input so it never diagnoses an idea the user has not approved.

### 4-3. inputSchema

```json
{
  "type": "object",
  "properties": {
    "confirmed_intake": {
      "type": "object",
      "description": "User-approved intake returned by prepare_intake. Stateless session linkage.",
      "properties": {
        "input_summary":     { "type": "string" },
        "problem":           { "type": "string" },
        "target_user":       { "type": "string" },
        "pain_source_label": { "type": "string", "description": "직접 경험 / 주변인 관찰 / 시장 추정 / 상상 확장" },
        "maturity_label":    { "type": "string" },
        "time_budget_label": { "type": "string" }
      },
      "required": ["input_summary", "problem", "target_user", "pain_source_label"]
    },
    "correction_text": {
      "type": "string",
      "description": "Optional. User's edits to the intake card. Only the changed parts are applied (04 문서 부분 갱신)."
    }
  },
  "required": ["confirmed_intake"]
}
```

### 4-4. output 구조 (정제)

```json
{
  "type": "object",
  "properties": {
    "display_text": {
      "type": "string",
      "description": "균열점 요약(짧게). 진단은 결과카드의 일부이므로 여기서는 최소 노출. 내부 enum 비노출."
    },
    "diagnosis": {
      "type": "object",
      "description": "다음 tool(design_first_experiment)에 그대로 전달.",
      "properties": {
        "crack_point":         { "type": "string", "description": "가장 먼저 깨질 전제 1개 (사용자 노출 핵심)" },
        "misread_risks":       { "type": "array", "items": { "type": "string" }, "maxItems": 2, "description": "착각 가능성 최대 2개" },
        "positive_signals":    { "type": "array", "items": { "type": "string" }, "maxItems": 2, "description": "좋은 신호 최대 2개" },
        "diagnosis_focus_label": { "type": "string", "description": "이번 진단이 집중한 관점(라벨). 내부 enum 비노출" }
      },
      "required": ["crack_point"]
    },
    "next_action": {
      "type": "object",
      "properties": {
        "type":      { "type": "string", "enum": ["DESIGN_EXPERIMENT"] },
        "next_tool": { "type": "string", "enum": ["design_first_experiment"] }
      },
      "required": ["type", "next_tool"]
    }
  },
  "required": ["diagnosis", "next_action"]
}
```

### 4-5. annotations

```json
{
  "title": "균열점 진단 (Diagnose Idea)",
  "readOnlyHint": true,
  "destructiveHint": false,
  "openWorldHint": false,
  "idempotentHint": false
}
```

> 진단은 사용자를 비난하지 않고 "먼저 확인할 지점"으로 표현(05 문서). `pain_source_label`에 따라 진단 각도가 달라진다(05 문서 4번). 읽기 전용 생성이므로 위 힌트는 tool 1과 동일.

---

## 5. Tool 3 — `design_first_experiment`

### 5-1. name
`design_first_experiment`

### 5-2. description (영문)
> Takes the crack point and the user's time budget and designs the smallest possible validation mission to test that crack point. Prefers no-build / manual ways to verify before any development. Returns the mission title, 2-3 action steps, why the experiment is intentionally small, one success criterion, what not to build yet, and the next step if it passes. A deterministic formatter step composes the final "workshop result card" markdown — no extra LLM call.

### 5-3. inputSchema

```json
{
  "type": "object",
  "properties": {
    "confirmed_intake": {
      "type": "object",
      "description": "User-approved intake (from prepare_intake). Provides idea/user/time context for the card.",
      "properties": {
        "input_summary":     { "type": "string" },
        "target_user":       { "type": "string" },
        "time_budget_label": { "type": "string" }
      },
      "required": ["input_summary"]
    },
    "diagnosis": {
      "type": "object",
      "description": "Diagnosis from diagnose_idea. The crack_point is the target of the experiment.",
      "properties": {
        "crack_point": { "type": "string" }
      },
      "required": ["crack_point"]
    },
    "validation_time_budget": {
      "type": "string",
      "enum": ["30_MIN", "TODAY", "TWO_DAYS", "ONE_WEEK", "TWO_WEEKS_PLUS", "UNKNOWN"],
      "default": "UNKNOWN",
      "description": "Time budget; drives mission difficulty (06 문서 표). UNKNOWN → 가장 가벼운 미션."
    }
  },
  "required": ["confirmed_intake", "diagnosis"]
}
```

### 5-4. output 구조 (정제 — 결과카드 포함)

```json
{
  "type": "object",
  "properties": {
    "display_text": {
      "type": "string",
      "description": "완성된 공방 결과카드(08 문서 양식) 마크다운. formatter가 LLM 없이 렌더. 사용자 노출 최종 산출물."
    },
    "result_card": {
      "type": "object",
      "description": "결과카드의 구조화 버전(렌더·검증·내보내기용).",
      "properties": {
        "idea":                { "type": "string", "description": "💡 아이디어 요약" },
        "first_user":          { "type": "string", "description": "👤 처음 쓸 사람" },
        "crack_point":         { "type": "string", "description": "🔍 균열점 (diagnosis에서 전달)" },
        "time_budget":         { "type": "string", "description": "투자 가능 시간(사용자 언어)" },
        "mission_title":       { "type": "string", "description": "🧪 첫 검증 미션 제목/한 문장" },
        "mission_steps":       { "type": "array", "items": { "type": "string" }, "description": "해야 할 일 2~3개" },
        "why_this_experiment": { "type": "string", "description": "🧷 미션이 작은 이유" },
        "success_criteria":    { "type": "string", "description": "🎯 성공 기준 1개" },
        "do_not_build_yet":    { "type": "array", "items": { "type": "string" }, "description": "✂️ 지금 만들지 않아도 되는 것 2~3개" },
        "next_step_if_passed": { "type": "string", "description": "➡️ 성공 시 다음 행동" }
      },
      "required": ["crack_point", "mission_title", "mission_steps", "success_criteria"]
    },
    "next_action": {
      "type": "object",
      "properties": {
        "type":      { "type": "string", "enum": ["DONE"] },
        "next_tool": { "type": ["string", "null"], "enum": ["record_validation_result", null], "description": "MVP는 null. 후속 재진단 루프 도입 시 record_validation_result." }
      },
      "required": ["type"]
    }
  },
  "required": ["display_text", "result_card", "next_action"]
}
```

### 5-5. annotations

```json
{
  "title": "첫 실험 설계 + 결과카드 (Design First Experiment)",
  "readOnlyHint": true,
  "destructiveHint": false,
  "openWorldHint": false,
  "idempotentHint": false
}
```

> **출력 에이전트(07)의 결과카드 작성 책임을 이 tool의 formatter 단계로 흡수**한다(별도 tool·LLM 호출 없음). LLM은 미션 설계 1회만 호출하고, 카드 렌더는 결정적 템플릿.

---

## 6. p99 3000ms 대응 전략

| 전략 | 내용 |
|------|------|
| **tool당 LLM 1회 이하** | `prepare_intake`(구조화 1회) / `diagnose_idea`(진단 1회) / `design_first_experiment`(미션 1회 + formatter는 LLM 없음). 한 tool 안 다단계 LLM 호출 금지 |
| **formatter는 결정적 코드** | 결과카드 렌더는 템플릿 문자열 조립. LLM 미사용 → 지연 0에 수렴 |
| **빠른 모델 우선** | 응답 지연이 낮은 모델 배치. 무거운 모델은 품질이 꼭 필요한 단계에만 |
| **프롬프트·출력 토큰 절감** | 시스템 프롬프트 간결화, 출력은 정해진 JSON 필드만(자유 장문 금지) → 생성 시간 단축 |
| **result 최소화** | API 원문·로그 비반환. `display_text` + 라벨 최소 객체만 → 직렬화·전송 지연 감소 |
| **입력 검증은 코드로** | enum/필수값 검증, 분기(시간 예산별 난이도)는 LLM이 아니라 결정적 로직 |
| **타임아웃·폴백** | LLM 호출에 타임아웃을 걸고, 초과 시 축약 응답/재시도 1회로 p99 꼬리 차단 |
| **스트리밍 주의** | Streamable HTTP라도 최종 구조화 결과가 3000ms 내 완료되도록 출력 길이를 통제 |

> 측정: MCP Inspector / 부하 측정으로 **각 tool p99 < 3000ms**를 배포 전 확인(7장).

---

## 7. MCP Inspector 점검 체크리스트

> PlayMCP in KC 배포·콘솔 등록 **전에** 로컬(또는 배포본)을 MCP Inspector로 점검한다.

### 연결·전송
- [ ] Inspector가 **Streamable HTTP**로 서버에 연결되는가.
- [ ] 서버가 **stateless**로 동작하는가 (세션 의존 없이 매 호출 독립 처리).

### 도구 목록·스키마
- [ ] `tools/list`에 **정확히 3개**(`prepare_intake`/`diagnose_idea`/`design_first_experiment`)가 보이는가.
- [ ] tool/server name에 **"kakao" 문자열이 없는가**.
- [ ] 각 tool에 **name·description(영문)·inputSchema·annotations**가 모두 있는가.
- [ ] annotations에 **title·readOnlyHint·destructiveHint·openWorldHint·idempotentHint 5개**가 모두 지정됐는가.
- [ ] inputSchema의 required·enum·default가 본 문서와 일치하는가.

### 호출 동작 (순차)
- [ ] `prepare_intake(idea_text, [time_budget])` → **접수 확인 카드** + `intake` + `next_action.next_tool="diagnose_idea"`.
- [ ] `diagnose_idea(confirmed_intake=위 intake)` → **균열점 1 + 착각 ≤2 + 신호 ≤2** + `next_action.next_tool="design_first_experiment"`.
- [ ] `correction_text`를 넣으면 해당 필드만 갱신되어 진단되는가.
- [ ] `design_first_experiment(confirmed_intake, diagnosis, [time_budget])` → **완성된 결과카드** + `result_card` + `next_action.type="DONE"`.

### 결과 품질·정책
- [ ] 응답에 **내부 enum 코드(`SELF`/`crack_point`/`RAW`/`PAIN_INTENSITY` 등)가 새지 않는가** (라벨만).
- [ ] result가 **최소화**되어 있는가 (API 원문·장문 로그 미포함).
- [ ] `display_text`가 마크다운으로 깔끔히 렌더되는가 (접수 카드/결과카드 양식 04·08 문서).
- [ ] 균열점은 1개, 착각·신호는 각 최대 2개를 지키는가.

### 성능
- [ ] 각 tool **p99 응답시간 < 3000ms**인가 (반복 호출로 확인).
- [ ] 한 tool 호출에서 **LLM 호출이 1회 이하**인가.

> 위 항목이 모두 통과해야 PlayMCP in KC 배포 → 콘솔 등록 단계로 넘어간다(10 문서 6장).
