# 16 · Cloud Run 배포 설정 (일반/적합성)

> ▶ **실제 배포는 [`19`](19-cloud-run-deployment.md)(standalone repo `sangsang-lite-mcp` 기준) 참조.** 본 문서는 일반 설정·적합성 점검 기록.
> 상상공방 Lite MCP 서버(3-tool)를 Google Cloud Run에 배포하기 위한 설정·절차.
> 작성일: 2026-06-19 · 관련: [`14`](14-playmcp-submission.md), [`15`](15-tool-structure-decision.md), 서버: [`../prototype/mcp-server/`](../prototype/mcp-server/)
>
> ⚠️ **이 문서는 준비 자료다. 실제 배포 명령은 사용자 승인 후에만 실행한다.** API 키 값은 어디에도 출력/기록하지 않는다.

---

## 1. 적합성 점검 (현재 코드 그대로 Cloud Run 호환 — 코드 변경 불필요)

| # | 확인 | 상태 | 근거 |
|---|------|------|------|
| 1 | Dockerfile이 Cloud Run에서 동작 | ✅ | `python:3.12-slim` 단일 스테이지, `CMD ["python","-m","sangsang_lite_mcp.server"]`. Cloud Run은 linux/amd64 — 우리 빌드도 `--platform linux/amd64`. |
| 2 | PORT 환경변수 사용 | ✅ | `server.py`가 `int(os.environ.get("PORT","8000"))`. Cloud Run이 런타임에 `PORT`(보통 8080) 주입 → 그대로 바인딩. Dockerfile `ENV PORT=8000`은 로컬 기본값(런타임에 덮어써짐). |
| 3 | HOST=0.0.0.0 바인딩 | ✅ | `server.py`가 `os.environ.get("HOST","0.0.0.0")`. Cloud Run 필수 조건 충족. |
| 4 | `/mcp`가 외부 HTTPS에서 동작 | ✅ | Cloud Run이 HTTPS 종단 제공 → 내부 HTTP로 전달. FastMCP Streamable HTTP 기본 경로 `/mcp`. stateless라 Cloud Run 수평 확장과 정합. |
| 5 | 환경변수/시크릿 주입 | ✅ | 비밀: `ANTHROPIC_API_KEY` → **Secret Manager**. 비-비밀: `LLM_ENABLED`/`MODEL_NAME`/`LLM_TIMEOUT_SECONDS` → `--set-env-vars`. (PORT는 설정 금지) |
| 6 | health check / 루트 경로 | ✅ 불필요 | Cloud Run 기본 **startup probe는 TCP**(포트 열림만 확인). HTTP 헬스 엔드포인트 불필요. `GET /`는 404지만 무관(앱이 PORT에 리스닝하면 healthy). 루트 추가 = 불필요한 구조 변경이라 안 함. |
| 7 | 배포 후 verify_mcp.py 검증 | ✅ | 원격 URL 인자 지원: `python scripts/verify_mcp.py https://<url>/mcp` |
| 8 | MCP Inspector 원격 검증 | ✅ | `npx @modelcontextprotocol/inspector --cli https://<url>/mcp --transport http` |

> **결론: Dockerfile·server.py 변경 없이 그대로 배포 가능.**

---

## 2. 사전 준비 (1회)

```bash
# gcloud CLI 설치/로그인은 사용자 환경에서 (대화에 키/토큰 노출 금지)
gcloud auth login
gcloud config set project <PROJECT_ID>
gcloud config set run/region asia-northeast3   # 서울 — PlayMCP 지연 유리

# 필요한 API 활성화
gcloud services enable run.googleapis.com cloudbuild.googleapis.com \
  artifactregistry.googleapis.com secretmanager.googleapis.com
```

---

## 3. API 키 — Secret Manager (키 값 비노출)

> 키를 `--set-env-vars`로 평문 주입하지 말 것. **Secret Manager** 사용.
> 아래는 **사용자가 본인 터미널에서** 실행(키가 대화에 안 들어오게).

```bash
# 이미 셸 env에 ANTHROPIC_API_KEY가 있다고 가정(값 입력/출력 없이 파이프로만)
printf '%s' "$ANTHROPIC_API_KEY" | gcloud secrets create anthropic-api-key --data-file=-
# (이미 있으면 새 버전 추가)
# printf '%s' "$ANTHROPIC_API_KEY" | gcloud secrets versions add anthropic-api-key --data-file=-
```

Cloud Run 런타임 서비스계정에 시크릿 접근 권한 부여(프로젝트에 따라 자동이거나 아래 수동):
```bash
PROJECT_NUMBER=$(gcloud projects describe <PROJECT_ID> --format='value(projectNumber)')
gcloud secrets add-iam-policy-binding anthropic-api-key \
  --member="serviceAccount:${PROJECT_NUMBER}-compute@developer.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
```

---

## 4. 배포 명령 (★ 승인 후 실행)

소스(Dockerfile) 기반 배포 — Cloud Build가 Dockerfile로 이미지 빌드:

```bash
cd projects/sangsang-lite/prototype/mcp-server

gcloud run deploy sangsang-lite-mcp \
  --source . \
  --region asia-northeast3 \
  --allow-unauthenticated \
  --set-env-vars LLM_ENABLED=true,MODEL_NAME=claude-haiku-4-5,LLM_TIMEOUT_SECONDS=6 \
  --set-secrets ANTHROPIC_API_KEY=anthropic-api-key:latest \
  --memory 512Mi \
  --cpu 1 \
  --timeout 60 \
  --concurrency 80 \
  --min-instances 0 \
  --max-instances 4
```

주의:
- **`PORT`는 설정하지 않는다**(Cloud Run 예약 — 자동 주입).
- `--allow-unauthenticated`: PlayMCP/Inspector가 인증 없이 접근해야 하므로 필요(공개 엔드포인트). OAuth는 이번 범위 밖.
- `--min-instances 0`: 비용 0이지만 **콜드 스타트**(첫 요청에 수 초 추가). PlayMCP 데모 반응성이 중요하면 `--min-instances 1`(상시 1개, 과금 발생) 고려.
- `--timeout 60`: 요청 타임아웃(LLM ~6s라 충분). 너무 낮추지 말 것.
- 배포 후 서비스 URL 확인:
  ```bash
  gcloud run services describe sangsang-lite-mcp --region asia-northeast3 --format='value(status.url)'
  # 예: https://sangsang-lite-mcp-xxxxx-an.a.run.app  → endpoint: <URL>/mcp
  ```

---

## 5. 배포 후 검증 (원격 URL)

```bash
URL=https://<service-url>   # 위 describe로 확인
# (1) 내부 verify 스크립트 — tools/list 3개 + annotations 5종 + tools/call 체이닝
python scripts/verify_mcp.py "$URL/mcp"

# (2) MCP Inspector — tools/list
npx @modelcontextprotocol/inspector --cli "$URL/mcp" --transport http --method tools/list

# (3) Inspector — tools/call (실제 LLM 경로 → meta.source=llm 기대)
npx @modelcontextprotocol/inspector --cli "$URL/mcp" --transport http \
  --method tools/call --tool-name prepare_intake \
  --tool-arg idea_text="독서모임 날짜를 카톡에서 자동 정리해주는 서비스" --tool-arg time_budget=TWO_DAYS
```

확인 포인트:
- tools/list에 `prepare_intake`/`diagnose_idea`/`design_first_experiment` 3개 + 각 annotations 5종.
- tools/call `isError: false`, **`meta.source: "llm"`**(시크릿 주입 정상 + 크레딧 정상).
- 콜드 스타트 직후 첫 호출은 느릴 수 있음 → 한 번 더 호출해 정상 지연 확인.

---

## 6. 운영 옵션 메모

- **stateless**(`stateless_http=True`)라 Cloud Run 다중 인스턴스/동시성(기본 80)과 안전.
- **지연**: design ~5s + 콜드 스타트 가능 → `LLM_TIMEOUT_SECONDS=6`(ADR 15 결정). p99 3000ms 미달 시 graceful stub fallback이 안전망.
- **메모리**: 이미지 ~245MB + 런타임 → 512Mi 적정(부족하면 1Gi).
- **리전**: 서울(asia-northeast3) 권장(국내 사용자/PlayMCP 지연).

---

## 7. PlayMCP 등록 전 체크리스트

- [ ] Cloud Run 배포 성공 + 서비스 URL 확보(HTTPS)
- [ ] `--allow-unauthenticated`로 외부 접근 가능
- [ ] `verify_mcp.py "$URL/mcp"` 통과
- [ ] Inspector `--transport http`로 tools/list(3) + annotations(5) 확인
- [ ] Inspector tools/call `meta.source=llm`(시크릿/크레딧 정상)
- [ ] 서버/도구 이름에 `kakao` 미포함 (✅ `sangsang-lite`)
- [ ] endpoint 경로 `/mcp` 확정(trailing slash 여부 Inspector로 확인)
- [ ] (선택) `--min-instances 1`로 콜드 스타트 제거 여부 결정
- [ ] PlayMCP 개발가이드 원문과 인증/헤더/헬스 요건 대조([`14`](14-playmcp-submission.md))
- [ ] PlayMCP 등록 → 심사 → 전체 공개

---

## 8. 주의

- **공개 엔드포인트**: `--allow-unauthenticated`는 누구나 호출 가능. MCP 인증/OAuth는 이번 범위 밖(PlayMCP remote 요건상 공개 필요).
- **PORT 미설정**: Cloud Run 예약. `--set-env-vars`에 넣지 말 것.
- **키 비노출**: `ANTHROPIC_API_KEY`는 Secret Manager로만. 로그/명령/문서/커밋에 평문 금지.
- **빌드 아키텍처**: Cloud Build는 linux/amd64로 빌드 — 호환 OK. (로컬 Apple Silicon에서 미리 빌드해 push할 경우에도 `--platform linux/amd64` 유지.)
- **코드 변경 없음**: Dockerfile/server.py 그대로 배포 가능(구조 변경 최소화 원칙 준수).
