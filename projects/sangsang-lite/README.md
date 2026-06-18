# 상상공방 Lite

> 아이디어를 **"만들 것"으로 바로 바꾸지 않고, 먼저 "확인할 것"으로 바꾸는** AI 공방.

상상공방 Lite는 IT상상공방(MVP 제작 공방)에서 갈라져 나온 **실험용 경량 서비스**다.
카카오 PlayMCP 공모전을 계기로 기획됐지만, **수상 자체가 목적이 아니다.**
핵심 목적은 **사람들이 아이디어를 바로 만들기 전에, 먼저 확인해야 할 부분을 놓치지 않도록 돕는 것**이다.

---

## 한 줄 정의

> 상상공방 Lite는 막연한 아이디어를 받아 **공방 접수 확인 → 균열점 진단 → 첫 검증 미션**을 거쳐,
> **오늘 할 수 있는 가장 작은 검증 행동**으로 바꿔주는 도구다.

```
막연한 아이디어
  → 공방 접수 확인 (잘못 이해 방지)
  → 균열점 진단 (가장 먼저 깨질 전제 1개)
  → 첫 검증 미션 (시간 예산에 맞춘 최소 실험)
  → 오늘 할 수 있는 작은 행동 (공방 결과카드)
```

---

## 프로젝트 구조

```
projects/sangsang-lite/
├─ README.md          # 이 문서 (인덱스)
├─ docs/              # 기획 문서 (01~09)
├─ prompts/           # (예정) 에이전트 프롬프트 보관
└─ prototype/         # (예정) PlayMCP/MCP 프로토타입·데모 구현
```

## 기획 문서 (docs/)

| 문서 | 내용 |
|------|------|
| [`docs/01-overview.md`](docs/01-overview.md) | 핵심 정의 · 서비스 철학 · 존재 이유 |
| [`docs/02-workflow.md`](docs/02-workflow.md) | 전체 워크플로 (MVP=순차 실행) |
| [`docs/03-communication-agent.md`](docs/03-communication-agent.md) | 소통 에이전트 (자유 서술 → 구조화) |
| [`docs/04-intake-confirmation.md`](docs/04-intake-confirmation.md) | 공방 접수 확인 (승인 게이트) |
| [`docs/05-idea-diagnosis-agent.md`](docs/05-idea-diagnosis-agent.md) | 아이디어 진단 에이전트 (균열점 진단) |
| [`docs/06-first-experiment-agent.md`](docs/06-first-experiment-agent.md) | 첫 실험 설계 에이전트 (첫 검증 미션) |
| [`docs/07-output-agent.md`](docs/07-output-agent.md) | 출력 에이전트 (결과카드 편집) |
| [`docs/08-result-card-format.md`](docs/08-result-card-format.md) | 공방 결과카드 기본 양식 |
| [`docs/09-demo-scenarios.md`](docs/09-demo-scenarios.md) | 데모 시나리오 (엔드투엔드 예시) |
| [`docs/13-streamable-http-examples-research.md`](docs/13-streamable-http-examples-research.md) | PlayMCP 제출용 Streamable HTTP MCP 서버 예제 조사 + 구현 구조 결정 |
| [`docs/14-playmcp-submission.md`](docs/14-playmcp-submission.md) | PlayMCP 제출 절차 + 준비 상태 점검(지연 측정 포함) |
| [`docs/15-tool-structure-decision.md`](docs/15-tool-structure-decision.md) | ADR — 도구 구조 결정(3-tool 유지, 4-tool 분할 보류) |
| [`docs/16-cloud-run-deployment.md`](docs/16-cloud-run-deployment.md) | Cloud Run 배포 설정·절차 + 적합성 점검 + 등록 전 체크리스트 |
| [`docs/17-standalone-repo-extraction.md`](docs/17-standalone-repo-extraction.md) | 제출용 전용 repo 분리 절차(준비 — KC 컨텍스트 미지원 대비) |
| [`docs/19-cloud-run-deployment.md`](docs/19-cloud-run-deployment.md) | Cloud Run 직접 배포(standalone repo 기준) — KC env 한계 전환 |

## 작업 공간 (예정)

| 폴더 | 용도 |
|------|------|
| [`prompts/`](prompts/README.md) | 각 에이전트(소통·진단·첫실험·출력)의 실제 프롬프트 보관 (구현 단계) |
| [`prototype/`](prototype/README.md) | PlayMCP/MCP 프로토타입 또는 데모 구현 보관 (구현 단계) |

---

## 용어 구분 (사용자 노출 vs 내부)

| 내부명 (구현자용) | 사용자 노출명 |
|-------------------|---------------|
| 소통 에이전트 | (접수 담당자 — 대화로만 노출) |
| 공방 접수 확인 | 🧾 공방 접수 확인 |
| 아이디어 진단 에이전트 | 🔍 균열점 진단 |
| 첫 실험 설계 에이전트 (구 MVP Cutter) | 🧪 첫 검증 미션 / 첫 실험 설계 |
| 출력 에이전트 | (결과카드로만 노출) |
| 최종 산출물 | 🧾 공방 결과카드 |

---

## MVP 원칙 (요약 — 상세는 각 문서)

1. **순차 실행만** 구현한다. 병렬 실행/오케스트레이션은 후속 고도화.
2. 질문은 **최대 1~2개**. 사용자가 이미 말한 건 다시 묻지 않는다.
3. **공방 접수 확인을 거친 뒤에만** 진단으로 넘어간다.
4. **균열점은 1개만** 보여준다.
5. 첫 검증 미션은 **사용자의 시간 예산에 맞춘다.**
6. 바로 개발하지 않고 **수동 실험 / 노코드 / 질문**으로 먼저 확인한다.
7. 결과카드는 **짧고 행동 가능**해야 한다.

> 공모전 수상이 목적이 아니라, **사람들이 만들기 전에 덜 헤매게 하는 것**이 목적이다.
