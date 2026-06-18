# 17 · 제출용 전용 repo 분리 절차 (준비 — 실행 보류)

> KC PlayMCP Git 소스 빌드 UI에 **build context/source directory 지정 옵션이 없을 경우**를 대비한 분리 절차.
> `projects/sangsang-lite/prototype/mcp-server/` 만 떼어 **자체 repo(루트 = mcp-server)**로 만든다 → context=root라 **현재 Dockerfile 무변경 동작**.
> 작성일: 2026-06-19 · 관련: [`16`](16-cloud-run-deployment.md)
>
> ⚠️ **이 문서는 절차 준비다. 실제 파일 이동·repo 생성·push는 사용자 승인 후에만.** 키(.env)는 절대 포함/출력하지 않는다.

---

## 1. 전제

- KC 빌드가 컨텍스트 지정을 지원하면 → 분리 불필요(현행 monorepo + context=mcp-server). [docs/16 옵션 A]
- 지원하지 않고 context가 repo root로 고정이면 → 본 분리(옵션 C)로 "repo 루트 = mcp-server"를 만들어 해결.
- 분리 후 현재 `Dockerfile`은 **수정 없이** 동작한다(루트가 곧 빌드 컨텍스트이므로 `COPY requirements.txt .` / `COPY src/ ./src/` 정상).

---

## 2. 새 repo에 포함할 파일 (현재 git 추적 15개 그대로)

```
Dockerfile
requirements.txt
README.md
.env.example                # 예시(키 없음) — 포함 OK
.dockerignore               # 이미 .env 제외 — 함께 이동(빌드 컨텍스트 보호)
scripts/verify_mcp.py
scripts/measure_latency.py
src/sangsang_lite_mcp/__init__.py
src/sangsang_lite_mcp/server.py
src/sangsang_lite_mcp/schemas.py
src/sangsang_lite_mcp/llm.py
src/sangsang_lite_mcp/tools/__init__.py
src/sangsang_lite_mcp/tools/prepare_intake.py
src/sangsang_lite_mcp/tools/diagnose_idea.py
src/sangsang_lite_mcp/tools/design_first_experiment.py
```

## 3. 반드시 제외할 파일

| 제외 | 이유 |
|------|------|
| **`.env`** | ★ ANTHROPIC_API_KEY 포함 — **절대 커밋 금지.** 새 repo에서도 env/Secret로만 주입 |
| `__pycache__/`, `*.pyc` | 파생물 |
| `.venv/`, `venv/` | 로컬 가상환경 |

> 현재 `mcp-server/`에는 **자체 `.gitignore`가 없다**(repo 루트 `.gitignore`에 의존). 분리 시 그 보호막이 사라지므로 **새 repo에 `.gitignore`를 반드시 먼저 추가**한다(아래 4-2).

## 4. 분리 절차 (★ 승인 후 실행)

### 4-1. 새 작업 폴더로 추적 파일만 복사
키·캐시가 섞이지 않게 **git 추적 파일만** 복사한다(아래는 안전한 방법 예):
```bash
SRC=~/Desktop/IT_make_some/projects/sangsang-lite/prototype/mcp-server
DST=~/Desktop/sangsang-lite-mcp           # 새 repo 작업 폴더
mkdir -p "$DST"
# git 추적 파일만 추출(미추적 .env/__pycache__ 자동 제외)
( cd "$SRC" && git ls-files -z | rsync -0 --files-from=- ./ "$DST/" )
```

### 4-2. 새 repo용 .gitignore 추가 (필수, 키 보호)
```bash
cat > "$DST/.gitignore" <<'EOF'
__pycache__/
*.py[cod]
.venv/
venv/
.env
*.egg-info/
.pytest_cache/
.mypy_cache/
EOF
```

### 4-3. .env 부재 확인 게이트 (커밋 전 필수)
```bash
test -f "$DST/.env" && echo "❌ .env 존재 — 삭제 후 진행" || echo "✅ .env 없음"
grep -rl "sk-ant" "$DST" && echo "❌ 키 문자열 발견 — 중단" || echo "✅ 키 문자열 없음"
```

### 4-4. git init + 커밋 + 원격 생성/푸시
```bash
cd "$DST"
git init -q && git add -A
git status --short    # .env 가 없는지 눈으로 재확인
git commit -q -m "init: 상상공방 Lite MCP 서버 (제출용 standalone)"
# 전용 private repo 생성 후 push (공방 정책: 프로젝트별 repo)
gh repo create LeeSiHyeon711/sangsang-lite-mcp --private --source=. --remote=origin --push
```

## 5. 새 repo에서 검증 (분리 후)

```bash
cd ~/Desktop/sangsang-lite-mcp

# (1) 루트가 곧 컨텍스트 — Dockerfile 무변경 빌드
docker build --platform linux/amd64 -t sangsang-lite-mcp:latest .

# (2) 컨테이너 (키는 env/secret로만, 출력 금지)
docker run -d --name sslite -p 8080:8000 \
  -e LLM_ENABLED=true -e MODEL_NAME=claude-haiku-4-5 -e LLM_TIMEOUT_SECONDS=6 \
  -e ANTHROPIC_API_KEY="$ANTHROPIC_API_KEY" \
  sangsang-lite-mcp:latest

# (3) verify 스크립트 (mcp 설치된 venv에서)
python scripts/verify_mcp.py http://127.0.0.1:8080/mcp

# (4) MCP Inspector
npx @modelcontextprotocol/inspector --cli http://127.0.0.1:8080/mcp --transport http --method tools/list
npx @modelcontextprotocol/inspector --cli http://127.0.0.1:8080/mcp --transport http \
  --method tools/call --tool-name prepare_intake \
  --tool-arg idea_text="독서모임 일정 자동 정리" --tool-arg time_budget=TWO_DAYS

docker rm -f sslite
```

기대: tools/list 3개 + annotations 5종, tools/call `isError:false`, 키 주입 시 `meta.source=llm`.
→ 통과하면 docs/16의 Cloud Run 배포를 **이 repo 루트 기준**으로 진행(KC Git 빌드도 이 repo 지정).

## 6. 분리 후 유지 (동기화 주의)

- 분리본은 monorepo의 **복사본**이다. 이후 코드 변경 시 **둘 중 하나를 단일 출처(source of truth)로 정한다.**
  - 권장: **분리 repo를 제출/배포의 단일 출처**로 삼고, monorepo에는 "여기로 이전됨" 포인터만 남김(또는 monorepo 사본 제거).
- monorepo의 `docs/`(13~17 기획·결정 기록)는 그대로 두고, 코드만 분리 repo로 이관하는 방식도 가능.

## 7. 체크리스트

- [ ] KC 빌드 UI에 컨텍스트/소스 디렉토리 옵션 **없음** 확인(있으면 분리 불필요)
- [ ] 추적 15개 파일만 복사(§2)
- [ ] 새 repo `.gitignore` 추가(§4-2)
- [ ] `.env`·키 문자열 부재 게이트 통과(§4-3)
- [ ] private repo 생성·push
- [ ] `docker build`(루트 컨텍스트) 성공
- [ ] verify_mcp.py + Inspector 통과(`meta.source=llm`)
- [ ] 단일 출처 정책 결정(§6)
- [ ] docs/16 Cloud Run 배포를 분리 repo 기준으로 연결
