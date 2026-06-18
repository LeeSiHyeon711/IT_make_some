# 13 · Streamable HTTP MCP 서버 예제 조사 (PlayMCP 제출용)

> 목적: 외부 MCP 서버 설치가 아니라, **우리가 직접 구현할 `projects/sangsang-lite/prototype/mcp-server/`의 구조를 결정**하기 위한 참고 자료 조사.
> 대상 도구 3개: `prepare_intake` · `diagnose_idea` · `design_first_experiment` (각각 소통/진단/첫실험 에이전트에 대응 — `docs/03,05,06` 참조).
> 조사일: 2026-06-18 · 조사 방식: 공식 SDK 문서/예제 웹 확인 (설치 없음).

---

## 1. 조사 요약 (결론 먼저)

### 추천 스택: **Python — 공식 MCP Python SDK의 FastMCP (Streamable HTTP, stateless)**

```python
from mcp.server.fastmcp import FastMCP
mcp = FastMCP("sangsang-lite", stateless_http=True, json_response=True)
```

### 추천 이유 (PlayMCP 우선 기준에 매핑)

| PlayMCP/우선 기준 | Python FastMCP가 유리한 점 |
|-------------------|----------------------------|
| Streamable HTTP만 지원 | `transport="streamable-http"`, 기본 endpoint `/mcp` 자동 제공 |
| Stateless 권장 | `stateless_http=True` 한 줄. 세션 헤더 관리 불필요 |
| result 최소화 / application/json | `json_response=True` → SSE 대신 단일 JSON 응답 |
| annotations 5종 필수 | `@mcp.tool(annotations=ToolAnnotations(...))`로 데코레이터에서 직접 작성 |
| tool 3개 깔끔 등록 | 데코레이터 3개. inputSchema는 타입힌트+docstring/Field로 자동 생성 |
| Dockerfile 쉬울 것 | 빌드 단계(tsc) 없음 → `pip install` + `CMD python -m ...` 로 끝 |
| Inspector 점검 쉬울 것 | `npx @modelcontextprotocol/inspector --cli <url>/mcp --transport http` 동일 |
| LLM 호출 나중에 붙이기 | 도구 본문이 평범한 파이썬 함수 → Anthropic SDK 등 호출부만 추가 |
| 구조 단순 | 빌드 산출물·tsconfig 없이 `src/` 한 곳 |

> TypeScript SDK도 Streamable HTTP를 완전히 지원하지만(`StreamableHTTPServerTransport`, `sessionIdGenerator: undefined`, `enableJsonResponse: true`), Express 결선·`tsc` 빌드·`build/` 산출물 때문에 **파일 수와 Dockerfile이 더 무겁다.** 우리 도구는 외부 쓰기 없는 순수 분석 3개라 Python의 간결함 이점이 크다.

---

## 2. 후보 예제 목록

### A. 공식 MCP Python SDK — `simple-streamablehttp-stateless` (★ 1순위 참고)
- 출처: https://github.com/modelcontextprotocol/python-sdk (`examples/servers/simple-streamablehttp-stateless/`)
- 언어: Python · Streamable HTTP: ✅ (stateless) · Dockerfile: ❌ (예제엔 없음)
- 참고할 부분: stateless 저수준 서버 패턴, `/mcp` 마운트, `mcp.run(transport="streamable-http")`.
- 그대로 쓰면 안 되는 부분: 저수준 Server API 예제라 장황. 우리는 **FastMCP 고수준 API**로 간다.

### B. FastMCP 공식 문서 (Tools / Running Server) (★ 도구·실행 패턴 근거)
- 출처: https://gofastmcp.com/servers/tools , https://gofastmcp.com/deployment/running-server
- 언어: Python · Streamable HTTP: ✅ · Dockerfile: ❌
- 참고할 부분: `@mcp.tool(annotations=ToolAnnotations(title, readOnlyHint, destructiveHint, idempotentHint, openWorldHint))`, 파라미터 설명 3가지 방식(docstring / Annotated / Field), `mcp.run(transport=..., host, port)`.
- 그대로 쓰면 안 되는 부분: gofastmcp는 **standalone `fastmcp` v2** 문서. 우리는 공식 `mcp` 패키지의 `mcp.server.fastmcp.FastMCP`를 기준으로 한다(둘은 거의 호환이나 `run()` 인자 표면이 조금 다를 수 있음 — §5 리스크).

### C. invariantlabs-ai/mcp-streamable-http (양 언어 비교 참고)
- 출처: https://github.com/invariantlabs-ai/mcp-streamable-http
- 언어: Python + TypeScript 양쪽 · Streamable HTTP: ✅ · Dockerfile: ❌
- 참고할 부분: `server/` + `client/` 분리 구조, 기본 포트(8123)·`--port` 플래그 패턴, 같은 도구를 양 언어로 구현해 비교 가능.
- 그대로 쓰면 안 되는 부분: 데모(weather) 도메인. Dockerfile 없음. 클라이언트 코드는 우리에게 불필요.

### D. 공식 MCP TypeScript SDK — server 문서 / `simpleStreamableHttp.ts` (대안 스택 참고)
- 출처: https://github.com/modelcontextprotocol/typescript-sdk/blob/main/docs/server.md , `examples/server/src/simpleStreamableHttp.ts`
- 언어: TypeScript · Streamable HTTP: ✅ · Dockerfile: ❌
- 참고할 부분: `StreamableHTTPServerTransport({ sessionIdGenerator: undefined, enableJsonResponse: true })`(=stateless+JSON), `registerTool`의 `annotations`(title/destructiveHint/idempotentHint 등), Express `/mcp` POST/GET 결선.
- 그대로 쓰면 안 되는 부분: Express 결선·`tsc` 빌드·CORS/세션 예제가 우리 MVP엔 과함. **채택 안 함(대안 보관).**

### E. Koyeb — Remote MCP(Streamable HTTP) 배포 튜토리얼 (배포/PORT 참고)
- 출처: https://www.koyeb.com/tutorials/deploy-remote-mcp-servers-to-koyeb-using-streamable-http-transport
- 언어: TypeScript · Streamable HTTP: ✅ · Dockerfile: ❌(CLI 배포 중심)
- 참고할 부분: `const PORT = process.env.PORT || 3000` 패턴 = **배포 환경에서 PORT 주입** 사고방식. (우리는 Python에서 동일 개념 적용)
- 그대로 쓰면 안 되는 부분: TS·Koyeb 전용 절차. 0.0.0.0 바인딩 명시 없음.

### F. MCP Inspector (검증 도구 — 설치 아님, 점검용)
- 출처: https://github.com/modelcontextprotocol/inspector
- 참고할 부분: 원격 Streamable HTTP 점검 CLI:
  ```bash
  # UI 모드 (브라우저 http://localhost:6274)
  npx @modelcontextprotocol/inspector
  # CLI 모드 — tools/list 자동 점검
  npx @modelcontextprotocol/inspector --cli http://localhost:8000/mcp --transport http --method tools/list
  # 특정 tool 호출
  npx @modelcontextprotocol/inspector --cli http://localhost:8000/mcp --transport http \
    --method tools/call --tool-name prepare_intake --tool-arg idea_text="..."
  ```
- 주의: `--transport http`가 Streamable HTTP를 의미(기본 SSE 아님).

### G. (참고만) LobeHub Marketplace 항목들
- 출처: lobehub.com/mcp/... (예: `douglasqsantos-mcp-server-http-streamable`)
- 용도: **구현 패턴 눈으로만 참고.** ⚠️ Marketplace 설치 절차·특정 서비스 서버(Docebo 등)는 **우리 프로젝트에 설치/도입하지 않는다.**

---

## 3. 추천 구현 구조 (Python)

```
projects/sangsang-lite/prototype/mcp-server/
├─ Dockerfile
├─ requirements.txt            # mcp[cli], (나중에) anthropic
├─ .env.example               # ANTHROPIC_API_KEY, PORT 등 (값은 미포함)
├─ README.md                  # 실행/Inspector 점검/배포 메모
└─ src/
   └─ sangsang_lite_mcp/
      ├─ __init__.py
      ├─ server.py            # FastMCP 인스턴스 + 도구 등록 + run()
      ├─ tools/
      │  ├─ __init__.py
      │  ├─ prepare_intake.py          # prepare_intake
      │  ├─ diagnose_idea.py           # diagnose_idea
      │  └─ design_first_experiment.py # design_first_experiment
      ├─ schemas.py           # pydantic 입출력 모델 (intake/diagnosis/experiment)
      └─ llm.py               # LLM 호출 추상화 (초기엔 규칙기반 stub, 나중에 Anthropic 결선)
```

### server.py (골격 — 확정된 패턴 기반)
```python
import os
from mcp.server.fastmcp import FastMCP

mcp = FastMCP(
    "sangsang-lite",                 # ⚠ name에 'kakao' 금지 (PlayMCP 규칙) — 준수
    stateless_http=True,             # PlayMCP: stateless 권장
    json_response=True,              # SSE 대신 application/json 단일 응답
)

# 도구 등록 (tools/ 에서 import해 데코레이터 적용)
from .tools import register_all
register_all(mcp)

if __name__ == "__main__":
    # 배포 환경에서 PORT 주입. 0.0.0.0 바인딩(컨테이너 외부 노출).
    mcp.settings.host = "0.0.0.0"
    mcp.settings.port = int(os.environ.get("PORT", "8000"))
    mcp.run(transport="streamable-http")   # 기본 endpoint: /mcp
```

### tools/diagnose_idea.py (annotations 5종 + inputSchema 자동 — 예시)
```python
from mcp.types import ToolAnnotations
from ..schemas import IntakeData, Diagnosis
from ..llm import diagnose

def register(mcp):
    @mcp.tool(
        annotations=ToolAnnotations(
            title="아이디어 균열점 진단",
            readOnlyHint=True,      # 외부 상태 변경 없음(순수 분석)
            destructiveHint=False,
            idempotentHint=True,    # 부수효과 없음
            openWorldHint=False,    # 외부 시스템과 상호작용 안 함
        )
    )
    def diagnose_idea(intake: IntakeData) -> Diagnosis:
        """접수 데이터를 받아 가장 먼저 깨질 전제(균열점) 1개를 진단한다."""
        return diagnose(intake)   # llm.py 가 규칙기반→LLM 으로 교체 가능
```
> `IntakeData`/`Diagnosis`(pydantic)가 곧 **inputSchema/outputSchema**가 된다(타입에서 자동 생성). 세 도구 모두 동일 패턴.

### Dockerfile (빌드 단계 없음 — 단순)
```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY src/ ./src/
ENV PORT=8000
EXPOSE 8000
CMD ["python", "-m", "sangsang_lite_mcp.server"]
```
> `requirements.txt` 예: `mcp[cli]` (나중에 `anthropic` 추가). API key는 이미지에 굽지 않고 **배포 환경 변수로 주입**.

### (대안) TypeScript 구조 — 채택 안 함, 참고용
`Dockerfile / package.json / tsconfig.json / src/{index,server}.ts / src/tools/*.ts / src/schemas/common.ts` + `tsc` 빌드 → `build/`. 도구 등록은 `server.registerTool(name, { inputSchema: zod, annotations })`. Python 대비 파일·빌드 단계가 많아 MVP 속도에서 불리.

---

## 4. 상상공방 Lite에 적용할 구현 방침

- **/mcp endpoint 사용**: 예. FastMCP Streamable HTTP 기본값이 `/mcp`. PlayMCP의 "단일 endpoint(POST/GET)" 요구와 일치.
- **SSE 사용**: 아니오(원칙). `json_response=True`로 **application/json 단일 응답** 우선. (스트리밍 불필요 — 결과가 짧은 카드 데이터)
- **application/json 응답 우선**: 예. result를 최소 JSON으로 — PlayMCP "result 최소화" 및 p99 3000ms에 유리.
- **stateless input chaining**: 서버는 세션을 안 가짐. 단계 상태는 **클라이언트(오케스트레이터)가 이전 도구 출력을 다음 도구 입력으로 전달**해 잇는다.
  - `prepare_intake(idea_text, answers?) → intake`
  - `diagnose_idea(intake) → diagnosis`
  - `design_first_experiment(intake, diagnosis) → first_experiment`
  - 즉 6단계 순차 워크플로(`docs/02`)를 **서버 상태 없이** 도구 인자 체이닝으로 구현.
- **tool당 LLM 호출 최소화**: 도구 1개당 **LLM 호출 1회 이하**를 목표. `prepare_intake`는 질문 분기 때문에 가벼운 1회, `diagnose_idea`/`design_first_experiment`는 각 1회. 초기에는 `llm.py`를 규칙기반 stub으로 두고 인터페이스만 고정 → 나중에 Anthropic 호출로 교체.
- **result 최소화 전략**: 결과는 결과카드에 필요한 필드만(JSON). 내부 추론·중간 텍스트는 result에 싣지 않음. 긴 설명은 출력 에이전트(`docs/07`)가 카드로 편집.
- **도구 개수**: 3개 (PlayMCP 권장 3~10 충족). 필요 시 `render_result_card`(출력 에이전트) 1개 추가 여지.
- **네이밍 규칙 준수**: server/tool name에 **`kakao` 문자열 포함 금지** → `sangsang-lite`, `prepare_intake` 등으로 확정.

---

## 5. 구현 전 확인 필요 사항 (리스크)

1. **FastMCP host/port 인자 표면 차이**: 공식 `mcp.server.fastmcp.FastMCP`와 standalone `fastmcp`(v2)의 `run()`/settings 인자가 버전마다 다를 수 있음. `mcp.settings.host/port` vs 생성자 인자 vs 환경변수(`FASTMCP_HOST`/`FASTMCP_PORT`) 중 **설치 버전에서 실제로 동작하는 방식**을 1차 구현 때 확정.
2. **stateless + json_response 동작 검증**: `stateless_http=True, json_response=True` 조합이 Inspector `--transport http`로 `tools/list`·`tools/call`이 통과하는지 실측 필요(특히 SSE 비활성 응답).
3. **endpoint 경로 trailing slash**: 기본이 `/mcp`인지 `/mcp/`인지(문서상 혼재). PlayMCP 등록 URL과 일치시켜야 함 — Inspector로 양쪽 확인.
4. **PlayMCP 개발가이드 원문 미확보**: 본 조사의 PlayMCP 제약은 사용자가 제시한 요건 기준. **실제 제출 스펙(인증 방식, 허용 헤더, timeout, 헬스체크 경로, annotations 필수성)은 PlayMCP 공식 가이드 원문으로 재확인** 필요.
5. **annotations 필수 5종 직렬화**: `ToolAnnotations`에 5종(title/readOnlyHint/destructiveHint/openWorldHint/idempotentHint)을 모두 넣었을 때 `tools/list` 응답에 그대로 노출되는지 Inspector로 확인.
6. **p99 3000ms vs LLM 지연**: 도구가 LLM을 호출하면 3000ms 초과 위험. 모델/프롬프트 경량화 또는 일부 단계 규칙기반 처리로 지연 예산 관리 필요(설계 시점에 측정).
7. **Docker 베이스 이미지/포트**: PlayMCP가 요구하는 포트·헬스체크가 있으면 `EXPOSE`/`CMD`를 맞춰야 함(가이드 원문 확인 후 확정).

---

## 6. 참고 출처

- 공식 MCP Python SDK: https://github.com/modelcontextprotocol/python-sdk
- FastMCP 문서(Tools/Running): https://gofastmcp.com/servers/tools , https://gofastmcp.com/deployment/running-server
- 공식 MCP TypeScript SDK server 문서: https://github.com/modelcontextprotocol/typescript-sdk/blob/main/docs/server.md
- invariantlabs mcp-streamable-http(양 언어): https://github.com/invariantlabs-ai/mcp-streamable-http
- Koyeb 원격 MCP 배포 튜토리얼: https://www.koyeb.com/tutorials/deploy-remote-mcp-servers-to-koyeb-using-streamable-http-transport
- MCP Inspector: https://github.com/modelcontextprotocol/inspector
- MCP Transports 스펙(2025-03-26): https://modelcontextprotocol.io/specification/2025-03-26/basic/transports
