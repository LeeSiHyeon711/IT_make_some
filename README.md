# IT상상공방 (IT Make Some)

> 고객의 상상을 **빠르게 확인 가능한 MVP**로 만들어주는 AI 자동 생산 공방.

막연한 아이디어가 들어오면, **8종의 전담 AI 에이전트**가 단계별로 협업해
**웹 / 앱 / 자동화 프로세스** 형태의 MVP로 만들어 *"이 가설이 맞나요?"* 를 가장 빠르게 검증한다.
목표는 "완벽한 제품"이 아니라 **잘못된 가설에 쏟을 시간을 아끼는 것**이다.

---

## 🏗️ 공방의 3계층 구조

에이전트는 서로 직접 호출하지 않는다. **인계는 항상 공방장을 거치고, 산출물 파일이 단계 간 유일한 통신 수단**이다.

```mermaid
flowchart TD
    Human([👤 사람]) -->|커맨드| Orchestrator
    Orchestrator["🎩 공방장 (오케스트레이터)<br/>커맨드로 구현 · 진행/인계/게이트 관리"]
    Orchestrator -->|Agent 호출| Agents
    subgraph Agents["전담 에이전트 8종 (단계별 1책임)"]
        direction LR
        A1[consultant] ~~~ A2[planner] ~~~ A3[architect] ~~~ A4[repo-manager]
        A5[builder] ~~~ A6[reviewer] ~~~ A7[triage] ~~~ A8[delivery]
    end
    Agents -->|공유 기능| Tools["🔧 스킬 · MCP<br/>템플릿 채우기 · Playwright(브라우저 검증) · gh"]
    Agents -.산출물 파일.-> Files[("📁 projects/&lt;고객&gt;/<br/>01~07 단계 폴더")]
    Files -.다음 단계 입력.-> Agents
```

---

## 🔄 생산 라인 — 7단계 파이프라인

```mermaid
flowchart LR
    C([💡 고객<br/>아이디어]) --> S1

    subgraph G1["▣ 방향 결정 · 사람 승인"]
        direction LR
        S1["1 상담<br/>consultant · Sonnet"] --> S2["2 기획<br/>planner · Sonnet"] --> S3["3 설계+FEAT<br/>architect · Opus"]
    end

    S3 -->|"/개발착수"| AUTO

    subgraph AUTO["⚙ 자동 구간"]
        direction LR
        S4["4 이슈등록<br/>repo-manager · Sonnet<br/>(FEAT 1:1)"] --> S5["5 개발<br/>builder · Sonnet<br/>(이슈 1개씩)"] --> GIT["🔐 Git commit/push 검증<br/>repo-manager"]
    end

    GIT -->|"▣ /승인"| S6["6 자동 QA<br/>reviewer · Sonnet<br/>(타입별 검증)"]
    S6 --> MT{"▣ 사람<br/>수동 테스트"}
    MT -->|"증상 보고 /증상"| TRI["↻ 증상분석<br/>triage · Opus"]
    TRI --> S5
    MT -->|"이상 없음 /납품"| S7["7 납품<br/>delivery · Sonnet"]
    S7 --> OUT([📦 고객 전달<br/>+ 포트폴리오 증거])

    classDef gate fill:#ffe8cc,stroke:#e8830c,color:#5a3500;
    classDef auto fill:#d3f0ff,stroke:#1f8fd0,color:#06425f;
    class G1,MT,GIT gate;
    class AUTO auto;
```

- **휴먼 게이트는 세 곳**: ① 방향 결정(상담·기획·설계+FEAT) ② **QA 진입 승인**(Git 원격 반영 확인 후 `/승인`) ③ 실사용 검증(사람 수동 테스트). 그 사이는 모두 자동.
- 4단계 GitHub 관리자가 설계를 **이슈로 박제**하고, 이슈를 닫기 전 **코드를 원격에 commit/push**해 흐름을 잃지 않는다.
- 수동 테스트에서 나온 증상은 증상분석가(triage)가 재현·분석 → 자동 수정 순환으로 처리한다.

---

## 🤖 에이전트 8종

| 단계 | 에이전트 | 모델 | 한 줄 책임 | 특수 권한 |
|------|----------|------|-----------|-----------|
| 1 | **consultant** | Sonnet | 고객 요구사항을 듣고 구조화 (형태 단정 금지) | — |
| 2 | **planner** | Sonnet | 요구사항 → PRD, MVP 스코프 컷 | — |
| 3 | **architect** | **Opus** | 형태·스택 결정 + **FEAT 문서 생성** (가장 중요한 분기점) | WebFetch/Search |
| 4 | **repo-manager** | Sonnet | 이슈 등록(FEAT 1:1) + **commit/push/close** (공방 내 유일한 Git 쓰기) | `gh` · `git push` |
| 5 | **builder** | Sonnet | FEAT 문서대로 MVP 구현 (이슈 1개씩, **최소 문서만 읽음**) | push 차단(훅) |
| 6 | **reviewer** | Sonnet | **프로젝트 타입별** 실행 검증 (웹/앱/API/문서) | Playwright MCP |
| ↻ | **triage** | **Opus** | 증상 재현·분석 → 수정 이슈 초안 | Playwright MCP |
| 7 | **delivery** | Sonnet | 실행 안내·고객 전달문 + 증거·생산성 요약 패키징 | — |

> **모델 배치 원칙**: 무거운 판단(설계·증상)만 **Opus**, 나머지는 **Sonnet**. builder를 Sonnet으로 두는 대신 **FEAT 문서 품질·승인 게이트를 강화**한다.

---

## 🎛️ 운영 흐름 (공방장 커맨드)

```
/신규프로젝트 <이름>          # _template 복제 → 상담 시작
   └─ /다음단계 <이름>  ×3    # 상담 → 기획 → 설계+FEAT (한 단계씩, 사람 승인)
/개발착수 <이름>              # 이슈등록 → 개발 루프 → Git 반영 검증 (QA 진입 전 정지)
   └─ /승인 <이름>           # ▣ Git 원격 반영 확인 후 자동 QA 시작
        └─ (사람 수동 테스트)
            ├─ /증상 <이름> "증상"   # 버그 → triage 분석 → 자동 수정 순환
            └─ /납품 <이름>          # 이상 없음 → 납품 + 증거·생산성 요약
보조: /개발재개 <이름> (중단 복원) · /되돌리기 · /이슈동기화
```

---

## 🆕 v2 공정 개선 (PickUpMemo v1 실기기 검증 후 반영)

v1을 실제로 돌리며 확인된 병목·빈틈을 바탕으로 **공정을 최적화**했다. (상세: [`docs/공정개선-v2.md`](docs/공정개선-v2.md))

| # | v1에서 확인된 문제 | v2 해법 |
|---|------------------|---------|
| 1 | Builder 토큰 소모가 컸음 | builder **Opus→Sonnet** + **최소 문서**(이슈+FEAT+헌법 3개)만 읽기 |
| 2 | 이슈마다 참조 문서 범위가 넓어 탐색 비용 발생 | **FEAT 문서 ↔ 이슈 1:1 매칭** (이슈 #N ↔ `FEAT-NN`), 이슈 본문은 FEAT만 참조 |
| 3 | 이슈는 닫혔는데 코드가 원격에 push 안 된 빈틈 | repo-manager **commit/push 먼저 → close 나중** + **QA 진입 Git 검증 게이트(`/승인`)** |
| 4 | 웹과 Android의 QA 방식이 같았음 | reviewer **프로젝트 타입별 QA 분기** (웹/앱/API/문서) |
| 5 | 포트폴리오 증거를 수동 수집 | delivery가 **증거자료 체크리스트 자동 생성** |
| 6 | 토큰/시간/중단 기록 체계 없음 | **생산성요약.md** + **progress.md**(중단/재개 프로토콜 `/개발재개`) 정식화 |

**게이트 변화**: 2곳(방향 결정·실사용 검증) → **3곳**(+ QA 진입 승인).
**핵심 전환**: 비싼 판단(설계/FEAT)은 앞에서 한 번 무겁게, 싼 실행(개발)은 뒤에서 여러 번 가볍게.

---

## 📚 핵심 문서

| 문서 | 내용 |
|------|------|
| [`CLAUDE.md`](CLAUDE.md) | 공방 헌법 — 모든 에이전트가 따르는 최상위 규칙 (절대 원칙 9개) |
| [`docs/architecture.md`](docs/architecture.md) | 설계도 — 오케스트레이션 구조, 에이전트 명세, 범위 강제 메커니즘 |
| [`docs/pipeline.md`](docs/pipeline.md) | 생산 라인 7단계 상세 (입출력·완료조건) |
| [`docs/공정개선-v2.md`](docs/공정개선-v2.md) | v2 변경사항 요약 |

---

## 📁 디렉토리

```
IT_make_some/
├── CLAUDE.md          # 공방 헌법
├── docs/              # 설계도 · 파이프라인 · v2 개선 요약
├── .claude/
│   ├── agents/        # 전담 에이전트 8종
│   ├── commands/      # 공방장 커맨드 9종
│   ├── rules/         # 단계별 작업 규칙 7종 (paths 스코프)
│   └── hooks/         # push/gh 차단 훅
├── templates/         # 산출물 템플릿 (FEAT·이슈본문·progress·증거·생산성 등)
└── projects/
    ├── _template/     # 신규 프로젝트 골격 (7단계 폴더 + features/ + progress.md)
    ├── PickUpMemo/    # 사례: Android 앱 (실기기 검증 완료)
    └── dday-test/     # 사례: 웹
```

---

## ✅ 현재 상태: v2 가동

- [x] 공방 헌법 / 설계도 / 파이프라인 / **v2 개선 문서**
- [x] 단계별 규칙 7종 · 전담 에이전트 8종 · 공방장 커맨드 9종 · 산출물 템플릿 14종
- [x] 권한 강화 (settings.json + push/gh 차단 훅, repo-manager만 Git 쓰기)
- [x] 사례 검증: **dday-test**(웹) · **PickUpMemo**(앱, 실기기 검증 완료)
- [x] **v2 공정**: FEAT 1:1 매칭 · 최소 문서 · Git 검증 게이트 · 타입별 QA · 증거/생산성 기록 · 중단/재개

**다음 프로젝트부터** v2 공정이 자동 적용된다. 기존 사례 산출물은 그대로 보존.
