# 19 · Cloud Run 직접 배포 (standalone repo 기준)

> KC PlayMCP Git 빌드는 배포·MCP 검증은 됐으나 **런타임 env/secret 주입 UI가 없어** `LLM_ENABLED` 꺼진 stub만 반환됨(`meta.source=stub, fallback_reason=disabled`).
> → **Cloud Run 직접 배포**로 전환해 키를 Secret Manager로 주입하고, 그 URL을 PlayMCP에 원격 MCP로 등록한다.
> 작성일: 2026-06-19 · 배포 소스: **standalone repo `sangsang-lite-mcp`** (GitHub: `LeeSiHyeon711/sangsang-lite-mcp`, 로컬 `~/Desktop/sangsang-lite-mcp`)
> 관련: [`16`](16-cloud-run-deployment.md)(일반 설정·적합성). KC env 주입 한계 분석은 직전 대화 기록 참조.
>
> ⚠️ **실제 배포 명령은 사용자 승인 후에만 실행.** API 키 값은 출력/기록 금지(Secret Manager로만).

---

## 0. 전제 (코드 변경 없음)

- 배포 소스 = standalone repo 루트(`~/Desktop/sangsang-lite-mcp`). KC 빌드에 쓰던 그 repo.
- **Dockerfile/server.py 변경 불필요**: `server.py`가 `PORT`/`HOST` env를 읽고, repo 루트가 곧 빌드 컨텍스트라 `COPY` 정상(로컬 검증 완료).
- `.env`는 `.gitignore`로 제외 → `gcloud run deploy --source .`(`.gitignore` 적용) 업로드에 **키 미포함**. repo에 `.env` 자체도 없음.

---

## 1. 사전 준비 (1회, 사용자 본인 터미널)

```bash
gcloud auth login
gcloud config set project <PROJECT_ID>
gcloud config set run/region asia-northeast3        # 서울

gcloud services enable run.googleapis.com cloudbuild.googleapis.com \
  artifactregistry.googleapis.com secretmanager.googleapis.com
```

---

## 2. API 키 → Secret Manager (키 비노출)

> 키를 `--set-env-vars`에 평문으로 넣지 말 것. Secret Manager 사용. **사용자 본인 터미널에서** 실행(키가 대화에 안 들어오게).

```bash
# 셸 env의 ANTHROPIC_API_KEY를 파이프로만 전달(에코 없음)
printf '%s' "$ANTHROPIC_API_KEY" | gcloud secrets create anthropic-api-key --data-file=-
# 이미 있으면 새 버전:
# printf '%s' "$ANTHROPIC_API_KEY" | gcloud secrets versions add anthropic-api-key --data-file=-
```

Cloud Run 런타임 서비스계정에 접근 권한(필요 시):
```bash
PROJECT_NUMBER=$(gcloud projects describe <PROJECT_ID> --format='value(projectNumber)')
gcloud secrets add-iam-policy-binding anthropic-api-key \
  --member="serviceAccount:${PROJECT_NUMBER}-compute@developer.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
```

---

## 3. 배포 (★ 승인 후 실행)

```bash
cd ~/Desktop/sangsang-lite-mcp            # 배포 소스 = standalone repo 루트

gcloud run deploy sangsang-lite-mcp \
  --source . \
  --region asia-northeast3 \
  --allow-unauthenticated \
  --set-env-vars LLM_ENABLED=true,MODEL_NAME=claude-haiku-4-5,LLM_TIMEOUT_SECONDS=6 \
  --set-secrets ANTHROPIC_API_KEY=anthropic-api-key:latest \
  --memory 512Mi \
  --cpu 1 \
  --timeout 60 \
  --min-instances 0 \
  --max-instances 4
```

핵심 규칙:
- **`PORT`는 넣지 않는다** — Cloud Run 예약, 런타임 자동 주입(보통 8080). `server.py`가 그 값을 읽어 바인딩.
- 일반 env: `LLM_ENABLED=true`, `MODEL_NAME=claude-haiku-4-5`, `LLM_TIMEOUT_SECONDS=6`.
- 비밀: `ANTHROPIC_API_KEY`는 `--set-secrets`(Secret Manager)로만.
- `--allow-unauthenticated`: PlayMCP/Inspector가 인증 없이 접근(원격 MCP 등록 요건). OAuth는 범위 밖.
- `--timeout 60`: LLM ~5s + 콜드스타트 여유.

배포 후 URL:
```bash
gcloud run services describe sangsang-lite-mcp --region asia-northeast3 --format='value(status.url)'
# 예: https://sangsang-lite-mcp-xxxxx-an.a.run.app  → endpoint: <URL>/mcp
```

---

## 4. 배포 후 원격 검증

> `~/Desktop/sangsang-lite-mcp`의 `scripts/verify_mcp.py` 사용(mcp 설치된 venv에서).

```bash
URL=https://<cloud-run-url>     # describe로 확인

# 1) verify 스크립트 — tools/list 3 + annotations 5 + tools/call 체이닝
python scripts/verify_mcp.py "$URL/mcp"

# 2) Inspector — tools/list
npx @modelcontextprotocol/inspector --cli "$URL/mcp" --transport http --method tools/list

# 3) Inspector — tools/call prepare_intake → meta.source=llm 확인 (★ 핵심)
npx @modelcontextprotocol/inspector --cli "$URL/mcp" --transport http \
  --method tools/call --tool-name prepare_intake \
  --tool-arg idea_text="독서모임 날짜를 카톡에서 자동 정리해주는 서비스" --tool-arg time_budget=TWO_DAYS
```

기대 결과:
- tools/list: `prepare_intake`/`diagnose_idea`/`design_first_experiment` 3개 + 각 annotations 5종.
- tools/call: `isError:false`, **`meta.source:"llm"`, `fallback_reason:null`** ← Secret 주입 + 크레딧 정상 확인.
- 콜드 스타트 직후 첫 호출은 느릴 수 있음 → 한 번 더 호출해 정상 지연 확인.
- 만약 여전히 `meta.source=stub`이면:
  - `fallback_reason=disabled` → `LLM_ENABLED` env 미반영(배포 플래그 확인)
  - `=missing_api_key` → 시크릿 마운트/권한 확인
  - `=api_error`/`=timeout` → 모델명/크레딧/타임아웃 확인

---

## 5. 다음: PlayMCP 등록 (KC 빌드 대신 원격 URL)

- PlayMCP에 **Cloud Run URL(`<URL>/mcp`)을 원격 MCP 서버로 등록** → KC Git 빌드 env 한계 우회.
- 등록 후 PlayMCP 채팅에서 도구 호출 시 **실제 LLM 응답(meta.source=llm)** 확인.
- "나에게만 공개" → 심사 → 전체 공개(공모전 제출 필수). 서버/도구명에 `kakao` 미포함(✅).

---

## 6. 운영 메모

- **콜드 스타트**: `--min-instances 0`(비용 0, 첫 호출 지연) vs `1`(상시 과금). PlayMCP 심사 반응성 중요하면 1 고려.
- **stateless**: `stateless_http=True`라 Cloud Run 다중 인스턴스/동시성(기본 80) 안전.
- **비용**: 사용량 기반. 심사 기간만 `--min-instances 1` 두고 이후 0으로 내리는 방법도.
- **키 회전**: 새 키 발급 시 `gcloud secrets versions add` 후 재배포(또는 `:latest` 참조라 다음 인스턴스부터 반영).

## 7. 체크리스트
- [ ] gcloud 로그인 + 프로젝트 + region asia-northeast3 + API 활성화
- [ ] Secret Manager `anthropic-api-key` 생성 + 런타임 SA 권한
- [ ] `gcloud run deploy --source .`(PORT 미설정, env 3종, secret 1종, allow-unauthenticated)
- [ ] 서비스 URL 확보
- [ ] `verify_mcp.py "$URL/mcp"` 통과
- [ ] Inspector tools/list(3+annotations 5)
- [ ] Inspector tools/call **meta.source=llm**
- [ ] PlayMCP에 Cloud Run URL 원격 등록 → 채팅 테스트 → 심사 → 전체 공개
