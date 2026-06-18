# 상상공방 Lite — MCP 서버 (골격)

PlayMCP 제출용 **Streamable HTTP / stateless** MCP 서버 골격.
설계 근거: [`../../docs/13-streamable-http-examples-research.md`](../../docs/13-streamable-http-examples-research.md)

> ⚠️ 이 골격의 목표는 **고품질 진단이 아니라 "통과"**다:
> 서버 실행 → `/mcp` 열림 → `tools/list` 3개 → annotations 5종 → `tools/call` → Docker build → MCP Inspector 통과.
> LLM 호출은 `src/sangsang_lite_mcp/llm.py`에 **결정적 stub(규칙기반)**으로 둔다.

---

## 도구 3개

| tool | 입력 | 출력 | 대응 에이전트 |
|------|------|------|----------------|
| `prepare_intake` | `idea_text`, `time_budget?` | `IntakeData` | 소통 (docs/03) |
| `diagnose_idea` | `intake` | `Diagnosis` | 진단 (docs/05) |
| `design_first_experiment` | `intake`, `diagnosis` | `FirstExperiment` | 첫실험 (docs/06) |

- 모두 **읽기 전용 분석** → annotations: `readOnlyHint=true, destructiveHint=false, idempotentHint=true, openWorldHint=false` (+ 한글 `title`).
- **stateless 입력 체이닝**: 서버에 세션 없음. 앞 도구 출력을 다음 도구 입력으로 클라이언트가 전달한다.

---

## 로컬 실행

```bash
cd projects/sangsang-lite/prototype/mcp-server
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
PYTHONPATH=src PORT=8000 python -m sangsang_lite_mcp.server
# → Streamable HTTP endpoint: http://localhost:8000/mcp
```

## Docker

```bash
docker build -t sangsang-lite-mcp .
docker run -p 8000:8000 sangsang-lite-mcp
# → http://localhost:8000/mcp
```

## MCP Inspector 점검

```bash
# 1) tools/list — 3개 도구 + annotations 노출 확인
npx @modelcontextprotocol/inspector --cli http://localhost:8000/mcp --transport http --method tools/list

# 2) tools/call — prepare_intake 스모크 테스트 (스칼라 인자라 CLI로 가장 쉬움)
npx @modelcontextprotocol/inspector --cli http://localhost:8000/mcp --transport http \
  --method tools/call --tool-name prepare_intake \
  --tool-arg idea_text="배달 라이더용 식당 메모 앱" --tool-arg time_budget=TWO_DAYS

# 3) UI 모드 (object 입력인 diagnose_idea/design_first_experiment 점검에 편함)
npx @modelcontextprotocol/inspector   # http://localhost:6274 → transport=Streamable HTTP, URL=/mcp
```

> `--transport http` 가 Streamable HTTP를 의미(기본 SSE 아님).

---

## 구조

```
mcp-server/
├─ Dockerfile
├─ requirements.txt        # mcp>=1.9.0
├─ .env.example
├─ .dockerignore
├─ README.md
└─ src/sangsang_lite_mcp/
   ├─ server.py            # FastMCP(stateless_http, json_response) + run(streamable-http)
   ├─ schemas.py           # IntakeData / Diagnosis / FirstExperiment (→ inputSchema 자동)
   ├─ llm.py               # ★ STUB (규칙기반). 후속에 Anthropic으로 본문만 교체
   └─ tools/
      ├─ prepare_intake.py
      ├─ diagnose_idea.py
      └─ design_first_experiment.py
```

## 확인 필요 (구현 메모)

- `FastMCP(..., host=, port=)` 인자 표면은 설치된 `mcp` 버전에 따라 다를 수 있다(연구문서 §5.1). 동작 안 하면 `mcp.settings.host/port` 또는 env `FASTMCP_HOST/FASTMCP_PORT`로 대체.
- endpoint가 `/mcp`인지 `/mcp/`인지 Inspector로 확정 후 PlayMCP 등록 URL과 일치시킬 것.
- `result 최소화`: stub 출력은 짧게 유지. LLM 결선 시 p99 3000ms 예산 주의.
