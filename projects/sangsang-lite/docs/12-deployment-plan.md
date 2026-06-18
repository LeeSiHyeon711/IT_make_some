# 12 · 배포 전략 (Deployment Plan)

> 상상공방 Lite MCP 서버를 **PlayMCP in KC**에 배포하기 위한 전략 문서.
> MCP tool 스펙은 `11-mcp-tool-spec.md`, 등록·공모전 절차는 `10-playmcp-registration-plan.md` 참조.
> **기본 전략: Git 소스 빌드 방식(Dockerfile 필수).**

---

## 1. PlayMCP in KC 이용 조건 요약 (이용가이드 반영)

| 항목 | 내용 |
|------|------|
| **무상 지원** | 공모전 **참가작에 한해 예선 접수 기간 동안** MCP 서버 배포를 무상 지원 |
| **등록 기준** | PlayMCP in KC에서 발급받은 **MCP Endpoint URL**로 PlayMCP에 등록 (이 URL이 예선 참가 기준) |
| **서버 수 제한** | **계정당 최대 2대**까지 MCP 서버 등록 가능 |
| **배포 방식** | ① 컨테이너 이미지 등록 / ② Git 소스 빌드 — **둘 다 Docker 기반** |
| **Git 소스 빌드 조건** | Git 소스 빌드 방식도 **Dockerfile이 반드시 필요** |
| **아키텍처 주의** | Apple Silicon Mac에서 직접 이미지 빌드 시 **`linux/amd64`** 로 빌드 |
| **전송 방식** | **Streamable HTTP만**. stdio 제외. 단일 endpoint `POST /mcp` + `application/json` 단일 응답 우선 (11 문서 2-1) |

> 무상 지원은 **예선 접수 기간 한정**이다. 기간 종료 후 운영을 이어가려면 별도 비용/호스팅을 고려해야 한다(후속).

### 1-1. Transport·바인딩 요약 (MCP 공식 Transport 반영)
- **stdio transport 제외** — PlayMCP는 Streamable HTTP만 지원하므로 공모전용 구현에서 stdio를 만들지 않는다.
- **단일 endpoint `/mcp`** 에 클라이언트가 **JSON-RPC를 HTTP POST**로 전송. 응답은 **`application/json` 단일 응답** 우선(MVP는 streaming 불필요). SSE는 후속 후보, GET SSE는 SDK 기본값에 위임. (스펙: 11 문서 2-1)
- **바인딩**: **로컬 개발 = localhost 바인딩 우선**(보안상 외부 노출 금지) / **배포 = PlayMCP in KC 발급 공개 Endpoint URL** 사용. 컨테이너는 외부에서 KC가 라우팅하므로 `0.0.0.0:$PORT` 바인딩이 필요할 수 있다(포트 규칙은 8장 확인 항목).

---

## 2. Git 소스 빌드 방식 선택 이유 (기본 전략)

상상공방 Lite는 **② Git 소스 빌드 방식**을 기본으로 한다.

1. **GitHub 레포와 문서/코드 관리가 자연스럽다.** — 공방 산출물(docs/)·코드·배포가 한 레포에서 추적된다. 레포를 연결만 하면 빌드되므로 인계가 단순하다.
2. **별도 이미지 레지스트리 준비 부담이 적다.** — 이미지를 따로 빌드·푸시·태그 관리할 레지스트리(Docker Hub/사설 레지스트리)를 운영하지 않아도 된다.
3. **공모전 MVP 개발 속도에 유리하다.** — 코드 수정 → push → 재빌드 흐름이 짧다. 반복 배포(수정→재배포)가 잦은 예선 단계에 맞다.

> 컨테이너 이미지 방식은 **후속 안정화/운영 배포 후보**로 남긴다(6장).

---

## 3. 컨테이너 이미지 방식과의 비교

| 비교 항목 | ② Git 소스 빌드 (기본) | ① 컨테이너 이미지 등록 (후속) |
|-----------|------------------------|-------------------------------|
| Dockerfile | **필수** | 필수(빌드 시) |
| 빌드 위치 | PlayMCP in KC(레포 연결 후 빌드) | 로컬/CI에서 미리 빌드 |
| 레지스트리 | 불필요 | **필요**(이미지 푸시) |
| 반복 배포 | push → 재빌드 (빠름) | 재빌드 → 재푸시 → 재등록 |
| 아키텍처 통제 | 빌드 환경에 의존 | 로컬 빌드 시 **`linux/amd64` 명시 필요**(Apple Silicon 주의) |
| 적합 단계 | **예선 MVP**(개발 속도 우선) | 운영/안정화(이미지 고정·재현성 우선) |
| 단점 | 빌드 환경·빌드 시간 의존 | 레지스트리·이미지 관리 부담 |

> 두 방식 모두 **Docker 기반**이라 Dockerfile 품질이 곧 배포 성공률이다. 기본은 Git 소스 빌드지만, 운영 안정화 단계에서 재현성이 중요해지면 이미지 방식으로 전환할 수 있도록 **Dockerfile은 두 방식 공용으로 작성**한다.

---

## 4. 예상 프로젝트 구조

구현은 **`projects/sangsang-lite/prototype/mcp-server/`** 아래에 둔다(예상 경로 후보).

```
projects/sangsang-lite/prototype/mcp-server/
├─ Dockerfile               # Git 소스 빌드·이미지 방식 공용 (필수)
├─ README.md                # 실행/배포 방법, 환경변수, 포트
├─ package.json             # (Node 택1) 또는
│  └─ pyproject.toml        # (Python 택1)
└─ src/                     # MCP 서버 + 3개 tool 구현
   ├─ server.(ts|py)        # Streamable HTTP 진입점, tools/list 등록
   └─ tools/
      ├─ prepare_intake.*
      ├─ diagnose_idea.*
      └─ design_first_experiment.*   # 미션 설계 + 결과카드 formatter
```

> 언어는 Node(TypeScript) 또는 Python 중 **MCP Streamable HTTP 예제·SDK 지원이 확실한 쪽**으로 택1한다(7장 확인 항목). 어느 쪽이든 구조는 동일하게 유지한다.

---

## 5. Dockerfile 필요성 & Apple Silicon 주의

### 5-1. Dockerfile 필요성
- **Git 소스 빌드 방식도 Dockerfile이 반드시 필요**하다(1장). PlayMCP in KC가 레포를 받아 Dockerfile로 이미지를 빌드해 실행한다.
- Dockerfile이 책임지는 것: 런타임 베이스 이미지, 의존성 설치, 소스 복사, **HTTP 포트 노출(EXPOSE)**, 컨테이너 시작 명령(`CMD`로 Streamable HTTP 서버 기동).
- 멀티스테이지 빌드로 이미지 크기를 줄이고, 시작 시간을 단축해 **p99 3000ms**(11 문서)에 여유를 둔다.

### 5-2. Apple Silicon `linux/amd64` 주의
- 개발자가 **Apple Silicon Mac(arm64)** 에서 직접 이미지를 빌드하면 기본이 `arm64`가 되어, KC 실행 환경(amd64)과 불일치로 **실행 실패**할 수 있다.
- 로컬에서 직접 빌드/검증할 때는 반드시 **`linux/amd64`** 로 빌드한다.

```bash
# 로컬에서 직접 이미지 빌드/점검 시 (Apple Silicon)
docker buildx build --platform linux/amd64 -t sangsang-lite-mcp .
```

> Git 소스 빌드 방식은 KC에서 빌드되므로 이 이슈가 줄지만, **로컬 검증·이미지 방식 전환 시**를 대비해 규칙으로 못 박는다.

---

## 6. 계정당 2대 제한에 따른 서버 운용 전략

PlayMCP in KC는 **계정당 최대 2대**까지 등록 가능하다. 2대를 이렇게 나눈다.

| 슬롯 | 용도 | 비고 |
|------|------|------|
| **서버 A — 개발/스테이징** | 수정→재배포 반복, Inspector·내부 테스트용 | 자주 갈아끼움. 비공개 유지 |
| **서버 B — 제출용(공개 후보)** | 검증 끝난 버전만 올려 심사 요청·공개 | 안정 버전만. 공모전 비즈폼에 쓸 **공개 MCP 상세 URL** 출처 |

- 원칙: **제출용(B)에는 Inspector·채팅 테스트(11 문서)를 통과한 버전만 올린다.** 개발 중 불안정 버전이 공개 슬롯을 차지하지 않게 한다.
- 2대를 다 쓰기 부담되면 **B 한 대만 운용**하고 A는 로컬+Inspector로 대체할 수도 있다(슬롯 절약).
- 후속 운영 단계에서 이미지 방식으로 전환하면, B를 안정 이미지로 고정하고 A에서 차기 버전을 빌드·검증한다.

---

## 7. 배포 전 체크리스트

> 7장 통과 후 PlayMCP in KC 배포(10 문서 6장 #3)로 넘어간다.

### 코드·런타임
- [ ] `prototype/mcp-server/`에 **Dockerfile**이 있고 빌드가 성공하는가.
- [ ] 컨테이너가 **Streamable HTTP 서버**로 기동하고 `tools/list`에 3개 tool이 보이는가.
- [ ] 단일 endpoint **`POST /mcp`** 가 JSON-RPC를 받고 **`application/json` 단일 응답**을 돌려주는가 (11 문서 2-1).
- [ ] **stdio transport를 구현하지 않았는가**(범위 제외).
- [ ] 로컬은 **localhost 바인딩**, 배포본은 **공개 Endpoint**로 접근되는가(로컬에서 외부 노출 안 함).
- [ ] **stateless**로 동작하는가(세션 의존 없음, 입력 체이닝, 11 문서 2-2).
- [ ] **MCP Inspector 점검**(11 문서 7장)을 통과했는가.
- [ ] 각 tool 응답 **p99 < 3000ms**인가.

### 이미지·아키텍처
- [ ] 로컬에서 직접 빌드 시 **`linux/amd64`** 로 빌드했는가.
- [ ] 이미지 크기·시작 시간이 과하지 않은가(멀티스테이지 등).

### 네이밍·정책
- [ ] 서버명·tool명에 **"kakao" 미포함**(10·11 문서).
- [ ] 비밀값(API 키 등)이 **코드/레포에 하드코딩되지 않았는가**(환경변수로, 7장 확인 항목).

### 레포·배포
- [ ] Git 레포가 PlayMCP in KC에서 접근 가능한 상태인가(공개/연결 권한).
- [ ] 배포 후 **MCP Endpoint URL**을 발급받아 외부 HTTPS로 접근되는가.
- [ ] **계정당 2대 제한**을 고려해 어느 슬롯에 배포할지 정했는가(6장).

---

## 8. 아직 확인해야 할 정보 (PlayMCP in KC 가이드/콘솔에서 확정 필요)

> 아래는 **현재 미확정**이라 구현 전 PlayMCP in KC 문서·콘솔에서 확인해야 하는 항목이다. 확인되는 대로 본 문서·11 문서에 반영한다.

| # | 확인 항목 | 왜 필요한가 | 잠정 가정 |
|---|-----------|-------------|-----------|
| 1 | **Streamable HTTP MCP 예제** | 서버 진입점·라우팅·SDK 선택의 기준 | Node/Python 공식 MCP SDK의 Streamable HTTP 서버 예제 사용 |
| 2 | **환경변수 등록 가능 여부** | LLM API 키 등 비밀값 주입 방법 | 콘솔에서 env 주입 가능 가정. 불가 시 대체 비밀 관리 필요 |
| 3 | **실행 포트 / `PORT` 환경변수 규칙** | 컨테이너가 어느 포트를 열고 어디에 바인딩할지 | 플랫폼이 `PORT` 주입 → 서버가 `0.0.0.0:$PORT`에 바인딩하고 `/mcp`를 노출한다고 가정 |
| 4 | **health check 필요 여부** | 기동 판정·헬스 엔드포인트(`/health` 등) 구현 여부 | 헬스 엔드포인트 1개 준비(있어도 무해) |
| 5 | **로그 확인 방법** | 배포 후 디버깅·p99 측정 | 콘솔 로그 뷰 또는 stdout 수집 가정 |

> 1~3은 **구현 시작 전 반드시 확정**해야 한다(서버 골격·Dockerfile에 직접 영향). 4~5는 배포·운영 단계에서 확인한다.

---

## 9. 핵심 결정 요약

1. **기본 배포 방식 = Git 소스 빌드**(Dockerfile 필수). 레포·문서 관리 자연스럽고 레지스트리 부담↓, MVP 속도↑.
2. **컨테이너 이미지 방식은 후속 운영 후보.** Dockerfile은 두 방식 공용으로 작성.
3. **Apple Silicon 직접 빌드 시 `linux/amd64`** 강제(arm64 불일치 방지).
4. **계정당 2대** → 개발/스테이징(A) + 제출용 공개(B)로 분리, 검증 통과분만 B에 게시.
5. **구현 경로**: `projects/sangsang-lite/prototype/mcp-server/`(Dockerfile·README·package.json|pyproject.toml·src/).
6. **선확정 필요**: Streamable HTTP 예제·환경변수 주입·`PORT` 규칙·health check·로그 확인 (8장).
