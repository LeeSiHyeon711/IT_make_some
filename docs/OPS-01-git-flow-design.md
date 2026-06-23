# OPS-01 · repo-manager ↔ 공방장 Git 권한 분리 구조 — 설계(방향 승인)

> 상태: **방향 승인됨 / 기본 자동화 레벨 = L1.5 확정**. 적용은 아직 안 함 — 이 문서는 설계만 갱신하며,
> 기존 헌법·`docs/공정개선-v2.md`·커맨드/에이전트/훅 정의는 변경하지 않는다. git commit/push도 하지 않는다.
> 등록 이슈: [OPS-01](./운영개선-issues.md) · 작성일: 2026-06-21 · 갱신: 2026-06-21(L1.5 반영)

## 확정된 방향 (검토 승인)
1. **repo-manager가 git/GitHub write 단일 주체** 원칙 유지.
2. **공방장이 직접 git/gh 명령을 실행하는 구조로 바꾸지 않는다**(공방장은 호출·승인 전달만).
3. builder/reviewer/delivery의 **GitHub write 차단 구조 유지**.
4. **기본 운영 레벨 = L1.5**(L2는 승격 목표 상태로 보류).

---

## 0. 목적
repo-manager와 공방장의 git 권한 분리 구조를 정리하고, **안전성**과 **자동화 편의성**의 균형을 잡는다.
- 권한 분리의 안전성은 유지한다.
- 반복적인 git 명령 실행 동선을 줄인다.
- `/개발착수`·(검수=`/승인`)·`/납품` 단계에서 git 작업 흐름을 명확히 한다.

---

## 1. 현행 구조 사실 정리 (코드/정의 기준)

| 주체 | git/gh 권한 | 근거 |
|------|-------------|------|
| **repo-manager** | `gh`/git **push 가능한 유일 에이전트**. 모드 2에서 commit→push→원격확인→issue close를 **직접 실행** | `agents/repo-manager.md`(tools: Bash, "유일 에이전트", 모드 2) |
| **builder** | Bash 있으나 **GitHub 쓰기 차단**(`block-github-write.py` PreToolUse 훅). 코드 수정·테스트·로컬 작업까지 | `agents/builder.md` 훅 |
| **reviewer** | Bash 있으나 **GitHub 쓰기 차단** 훅. 검증·보고만, 코드/ git 변경 안 함 | `agents/reviewer.md` 훅 |
| **delivery** | **Bash 없음**(tools: Read/Write/Glob). git 무관 | `agents/delivery.md` |
| **공방장(오케스트레이터)** | 직접 `gh`/push 하지 않음. 에이전트를 순서 호출만. `/이슈동기화` 원칙에 명시 | `commands/*`, `이슈동기화.md` |

### ⚠ 배경 진술과의 차이(검토 전 합의 필요)
OPS-01 배경에는 "repo-manager가 계획을 보고하면 **공방장이 실제 명령을 실행**"이라 적혀 있으나,
**실제 정의상 commit/push/close를 실행하는 주체는 repo-manager**다(공방장은 호출만).
→ 따라서 이 설계의 진짜 분기점은 *"실행을 공방장으로 옮길까"*가 아니라
**"단일 쓰기 주체(repo-manager)를 유지한 채 동선·자동화 수준을 어떻게 둘까"** 이다.

### 현행 안전장치
- 쓰기 주체 단일화(repo-manager만 `gh`/push).
- builder/reviewer push 차단 훅.
- **QA 진입 Git 게이트**: 모든 이슈 close + push 원격 반영 + 사람 `/승인` 전까지 QA 미진입(헌법 2장 9).
- commit/push **먼저**, 이슈 close **나중**(v1 빈틈 차단).

---

## 2. 핵심 질문 5개 — 검토와 답안

### Q1. repo-manager가 실제 commit/push 권한을 단독으로 가져야 하는가?
**답: 그렇다(권장).** 쓰기 주체를 하나로 묶는 것이 사고 표면(attack/실수 surface)을 최소화한다.
복수 주체가 push할 수 있으면 "누가 무엇을 언제 밀었는가"의 추적성이 깨지고, 위험 명령 통제 지점이 분산된다.

### Q2. repo-manager는 명령 생성, 공방장이 승인 후 실행하는 구조가 맞는가?
**답: 부분만 채택.** "계획·명령어·커밋 메시지 **생성**은 repo-manager"가 맞다.
그러나 **실행 주체를 공방장으로 분리하는 것은 권장하지 않는다** — 단일 쓰기 주체 원칙(Q1)과 충돌하고,
공방장(메인 세션)이 직접 git을 쥐면 위험 명령 통제 훅을 우회할 여지가 생긴다.
**권장 절충**: *계획·실행 모두 repo-manager가 하되, "위험 등급" 명령만 공방장이 사람 승인을 받아 repo-manager에 실행 위임*(승인 신호를 받은 repo-manager 재호출). 동선은 자동화 레벨(3장)로 줄인다.

### Q3. `/개발착수`·검수(`/승인`)·`/납품`에서 git 작업은 언제 발생해야 하는가?
| 단계(커맨드) | git 발생 시점 | 실행 | 게이트 |
|---|---|---|---|
| **4 GitHub 관리**(`/개발착수` 1) | repo 생성·git init·remote 연결·이슈 등록 | repo-manager | 자동(3단계 승인 직후) |
| **5 개발 루프**(`/개발착수` 2, `/개발재개`) | 이슈 1개 완료마다 commit→push→원격확인→close | repo-manager | 자동(이슈 단위) |
| **5→6 진입**(`/개발착수` 3) | push 검증 체크리스트 출력(쓰기 없음, 읽기만) | repo-manager | **사람 `/승인`** |
| **6 검수**(`/승인`) | 보류 시: 발견 이슈 등록→수정→commit/push/close→재QA | repo-manager(쓰기), builder(코드) | 자동 수렴(순환) |
| **7 납품**(`/납품`) | **git 쓰기 없음**(문서 패키징만). 필요 시 태그/릴리스는 별도 승인 | delivery(git 무관) | 사람 수동 테스트 통과 신호 |

원칙: **쓰기성 git은 4·5·6에 집중**, 7은 읽기/무관. 검증(status/log/remote)은 어느 단계서나 읽기 전용으로 자유.

### Q4. 하위 에이전트(builder/reviewer/delivery)는 git 권한을 어디까지?
**답: 로컬까지만, 원격 쓰기 0.**
- builder: 코드 수정·테스트·로컬 빌드까지. **commit/push/close 금지**(현행 훅 유지). 필요 시 `git status/diff`(읽기)만 허용 검토.
- reviewer: 실행 검증·보고만. git 쓰기 0, 코드 수정 0(현행 유지).
- delivery: git 무관(Bash 없음 유지).
→ 변경 권장 없음. 현행이 이미 올바른 분리.

### Q5. dangerous/bypass permission 모드 사용 시 안전장치는?
1. **git 쓰기에 `bypassPermissions` 금지.** repo-manager도 위험 명령은 사람 승인 경유.
2. **위험 명령 deny-list**(force push, history 변경, 강제 reset/clean, remote 변경 등 — 5장)를 훅/권한에서 차단.
3. **dry-run 우선**: push 전 `git push --dry-run`, 파괴적 작업 전 `--dry-run`/`-n` 결과를 사람에게 보고 후 실행.
4. **실행 로그 박제**: 모든 쓰기 명령은 `progress.md`/`이슈등록내역.md`에 commit 해시·push 여부 기록(현행 유지·강화).
5. **단일 주체 + 단일 자격**: `gh api user --jq .login`으로 소유자 확인 후에만 쓰기(현행 유지).

---

## 3. 자동화 레벨 정의 (L0 / L1 / L1.5 / L2)

### L0 — 전 수동
| 항목 | 내용 |
|------|------|
| 권한 주체 | 사람이 모든 git 명령을 직접 입력·실행 |
| 허용 명령 | (사람 판단) 전부 |
| 금지 명령 | 없음(단, 사람 책임) |
| 승인 필요 지점 | 모든 명령 |
| 장점 | 통제 최대, 사고 시 책임·원인 명확 |
| 위험 | 동선 최악·자동화 흐름 단절·휴먼에러(반복 명령 오타) |
| 추천 상황 | 신규 repo 첫 연결, 히스토리 손상 복구 등 **고위험 1회성 작업** |

### L1 — 제안+승인 실행 (repo-manager 제안, 사람 승인, repo-manager 실행)
| 항목 | 내용 |
|------|------|
| 권한 주체 | 계획·명령어·커밋 메시지 생성=repo-manager / 실행=repo-manager(사람 승인 후) |
| 허용 명령 | 읽기 전체(status/diff/log/remote) + 승인된 commit/push/issue close |
| 금지 명령 | 위험 명령(5장) 전부, 미승인 push |
| 승인 필요 지점 | **commit/push/close 실행 직전 1회**(묶음 승인 허용) |
| 장점 | 안전성 유지 + 사람은 "검토→승인"만, 명령 작성/실행은 위임 → 동선 절감 |
| 위험 | 승인 피로(매 이슈마다 물으면 번거로움) → 묶음 승인으로 완화 |
| 추천 상황 | **민감 프로젝트·외부 고객 repo·초기 도입기 기본값** |

### L1.5 — 읽기/검증 자동 + 모든 쓰기 승인 (★ 현재 기본값)
| 항목 | 내용 |
|------|------|
| 권한 주체 | repo-manager 단일. **읽기/검증 명령은 자동 실행**, **모든 쓰기(add 포함)는 사람 승인 후 repo-manager가 실행** |
| 허용 명령(자동) | `git status`·`git diff`·`git diff --stat`·`git log`·`git remote -v`·`gh issue list/view`·`gh repo view`·`git push --dry-run` |
| 승인 후 허용 | `git add`·`git commit`·`git push`·`gh issue close/reopen`·`gh repo create` |
| 금지 명령 | 위험 명령(5장) 전부 — 레벨 무관 항상 차단 또는 별도 강승인 |
| 승인 필요 지점 | **add/commit/push/close 등 상태를 바꾸는 모든 명령 직전**(아래 commit/push 전 필수 보고 → 승인 → 실행) |
| 장점 | 검증·진단(diff/dry-run)은 사람 개입 없이 즉시 → 동선 절감. 쓰기는 전부 승인 → L1과 동등한 안전성 |
| 위험 | 쓰기마다 승인 필요(완화: push는 2~3개 이슈 묶음 승인 허용 — 4장) |
| 추천 상황 | **상상공방 v2 전 프로젝트 기본값.** 외부 고객·민감 프로젝트는 push를 이슈별 승인으로 강등 |

> L1과의 차이: L1은 읽기 명령도 제안·승인 대상으로 볼 수 있으나, **L1.5는 읽기/검증을 자동화**해 진단 동선을 줄인다. 쓰기 통제는 L1과 동일.

### L2 — 안전명령 자동 + 쓰기만 승인 (★ 승격 목표 상태, 현재 미적용)
| 항목 | 내용 |
|------|------|
| 권한 주체 | repo-manager가 **로컬 commit까지 자동**, **원격 쓰기(push)·issue close만 승인 후 실행** |
| 허용 명령(자동) | L1.5 자동 명령 + `git add`·`git commit`(로컬) |
| 승인 후 허용 | `git push`(실 반영), `gh issue close/reopen`, `gh repo create` |
| 금지 명령 | 위험 명령(5장) 전부. `bypassPermissions`로 git 쓰기 금지 |
| 승인 필요 지점 | **원격 반영(push)과 이슈 상태 변경 직전**. 로컬 commit까진 자동 |
| 장점 | 반복 동선 최소(로컬 정리·커밋 자동), 원격 반영만 사람이 통제 |
| 위험 | 로컬 commit 자동 → 잘못된 스테이징 가능(완화: commit 전 `diff --stat` 자동 보고) |
| **승격 조건** | **L1.5가 내부 저위험 프로젝트에서 개발 루프 최소 3회 이상 문제없이 동작한 뒤** L2 승격을 검토한다. 그 전까지 L2는 적용하지 않는다. |
| 추천 상황 | 내부/저위험 프로젝트, 동선 최우선, 위 승격 조건 충족 후 |

> 공통 불변: 어떤 레벨에서도 **위험 명령(5장)은 항상 사람 승인**, **QA 진입은 항상 사람 `/승인`**, **쓰기 주체는 repo-manager 단일**, **공방장은 직접 git/gh 실행 안 함**.

---

## 4. 현재 상상공방 v2 추천 기본안 (= L1.5)

**기본값 = L1.5(읽기/검증 자동 + 모든 쓰기 승인) + 위험 명령은 항상 L0 취급.**

근거:
- 현행이 "repo-manager 단일 쓰기 + 사람 `/승인` QA 게이트"라 구조 변경 없이 적용 가능하다.
- 읽기/검증(diff·dry-run·issue list)을 자동화해 **진단 동선만 줄이고**, 상태를 바꾸는 쓰기는 전부 사람 승인 → 안전성은 L1과 동일.
- L2(로컬 commit 자동)는 안정성 검증(개발 루프 3회+) 후 승격 목표로 보류.

### 4-1. commit/push 전 repo-manager 필수 보고 항목 (승인 요청 시 반드시 포함)
repo-manager는 add/commit/push 승인을 요청할 때 아래 7가지를 **한 번에** 보고한다. 사람은 이걸 보고 승인/거부한다.
1. **변경 파일 목록** (`git status --short`)
2. **`git diff --stat`** (규모·라인 증감)
3. **이슈 번호 매핑** (이 변경이 어느 이슈 #N / FEAT-NN에 해당하는지)
4. **secrets 포함 가능 파일 여부** (`.env`/키/토큰/자격증명 패턴 스캔 결과 — 의심 파일 있으면 ⚠ 표시 후 제외 권고)
5. **테스트 실행 결과** (builder가 남긴 테스트/빌드 통과 여부 요약)
6. **제안 커밋 메시지** (`#N <작업명>` 형식)
7. **실행 예정 명령어** (그대로 실행될 `git add/commit/push`·`gh issue close` 전체 명령 문자열)

> secrets 의심(4번)이 ✗이 아니면 push를 진행하지 않는다. 의심 파일은 `.gitignore`/제외 처리 후 재보고.

### 4-2. push 승인 단위
- **commit은 기본 이슈 1개당 1 commit** (`#N <작업명>`).
- **push는 최대 2~3개 이슈까지 묶음 승인** 가능(개발 루프에서 여러 이슈가 연속 완료된 경우).
- **외부 고객/민감 프로젝트는 이슈별 push 승인으로 강등**(묶음 금지).

### 4-3. 운영 형태(권장 흐름)
1. builder 완료 → repo-manager가 **읽기/검증 자동 실행**(status·diff·dry-run) → **4-1 필수 보고**로 승인 요청.
2. 사람 승인 → repo-manager가 `add→commit→push→원격확인→issue close` 실행(승인 단위 4-2 준수).
3. 위험 명령(5장)은 레벨 무관 항상 차단/강승인.

### 4-4. QA 진입 게이트 (헌법 2장 9 유지)
- **모든 개발 이슈가 commit → push → 원격확인 → close 까지 완료**되어야 한다.
- 그 다음 **사람의 `/승인` 이후에만 QA 단계(검수)로 진입**한다. (자동 QA를 임의로 호출하지 않는다.)
- **검수 중 발견된 신규 이슈**는 repo-manager가 등록 → builder 수정 → repo-manager가 commit/push/close(4-1·4-2 절차) → reviewer 재QA. 이 순환은 보류가 해소될 때까지 자동 수렴한다.

---

## 5. 위험 명령 deny-list (레벨 무관 항상 사람 승인/차단)

- `git push --force` / `--force-with-lease`
- `git reset --hard`, `git checkout -- .`(대량 폐기)
- `git clean -fdx`
- `git branch -D`, 원격 브랜치 삭제(`git push origin --delete`)
- 히스토리 변경: `git rebase`(공유 브랜치), `git commit --amend`(push 후), `git filter-branch`, `git filter-repo`
- `git remote set-url` / `remote remove`(원격 대상 변경)
- `gh repo delete`, `gh repo edit --visibility`
- 임의 토큰/자격 노출 위험 명령(`gh auth`, env 출력)

→ 이 목록은 **권한 deny + 훅 차단** 두 겹으로 막는 것을 권장(5장은 구현 시 `block-github-write.py` 확장 또는 신규 `block-dangerous-git.py`로).

---

## 6. 추후 공정문서에 반영해야 할 변경점 목록 (이번엔 미적용)

> 아래는 **결정 확정 후** 반영할 대상 목록일 뿐, 이번 단계에서는 건드리지 않는다.

1. `docs/공정개선-v2.md` — 자동화 레벨(L0/L1/**L1.5**/L2) 개념과 **기본값 L1.5**, L2 승격 조건(루프 3회+) 절 추가.
2. 루트 `CLAUDE.md`(헌법) — "쓰기 주체 단일(repo-manager)·공방장 직접 git 실행 금지" 명문화 + 위험 명령 deny-list 참조 + 기본 레벨 L1.5.
3. `.claude/agents/repo-manager.md` — 모드 2에 "읽기/검증 자동 / add·commit·push·close 승인(L1.5)" 절차 + **commit/push 전 7항목 필수 보고**(4-1) + 위험 명령 금지 목록.
4. `commands/개발착수.md`·`개발재개.md` — 개발 루프 2단계에 **commit/push 전 필수 보고 → 승인 → 실행** 지점과 **push 2~3개 묶음 승인** 명시.
5. `commands/승인.md` — push 검증과 이슈 close 승인의 관계 재정리(중복 게이트 정합성).
6. `.claude/rules/04-깃허브.md` — 레벨별 허용/금지 명령표, dry-run·로그 박제 규칙.
7. `.claude/hooks/` — `block-dangerous-git.py`(deny-list) 추가 또는 `block-github-write.py` 확장. repo-manager에는 "쓰기 허용+위험 차단" 훅 적용.
8. `.claude/settings*.json` — git 쓰기 관련 allow/deny 권한 항목 정리(`bypassPermissions`에서 git 쓰기 제외).
9. (선택) `/git반영`·`/push승인` 같은 **명시적 push 승인 커맨드** 신설 검토.

---

## 7. 아직 결정이 필요한 체크리스트

- [x] **C1. 실행 주체** — **결정**: repo-manager 단일 실행 유지. 공방장 실행 분리하지 않음.
- [x] **C2. 기본 자동화 레벨** — **결정**: **L1.5**. L2는 개발 루프 3회+ 안정 검증 후 승격 검토.
- [x] **C3. push 승인 단위** — **결정**: commit 이슈 1개당, push 최대 2~3개 묶음, 외부/민감은 이슈별 강등.
- [x] **C4. 로컬 commit 자동화 허용 여부** — **결정**: L1.5에서는 commit도 승인 대상. L2 승격 시 재검토.
- [ ] **C5. 위험 명령 차단 방식**: 권한 deny만 vs 훅까지 두 겹. 신규 훅(`block-dangerous-git.py`) 만들지 여부.
- [ ] **C6. 7단계 태그/릴리스**: 납품 시 git tag/release를 둘지, 둔다면 누가·어느 게이트에서.
- [ ] **C7. `bypassPermissions`/dangerous 모드**: 어떤 상황에서도 git 쓰기에 허용 안 함으로 못박을지.
- [ ] **C8. 검수 커맨드 명칭**: 현재 검수는 `/승인` 내부. OPS 문서의 "/검수" 표현을 `/승인`으로 통일할지, 별도 `/검수` 신설할지.
- [ ] **C9. 적용 순서**: 문서(헌법/공정)부터 vs 훅/권한부터. 회귀 위험 낮은 순서.
- [ ] **C10. L2 승격 판정 기준**: "개발 루프 3회 무사고"의 무사고 정의(롤백·force 사용·secrets 누출 0 등) 구체화.

---

## 8. 다음 단계(제안)
1. C1~C4 결정 완료(설계 승인). 남은 미결정 C5~C10은 적용 이슈 착수 전 순차 확정.
2. 6장 변경점을 아래 9장의 **적용 이슈로 분해**해 순차 적용(이번 OPS-01 설계 범위 밖, 아직 미등록).
3. 적용 시 회귀 확인: 개발 루프 1바퀴를 더미 프로젝트로 돌려 push 승인 동선·필수 보고(4-1)·위험 명령 차단을 검증.

---

## 9. 적용 이슈 분해(준비 — 아직 미등록·미적용)

> 설계 승인(L1.5) 후 실제 적용을 위한 **이슈 분해 초안**이다. 이번 단계에서는 등록하지 않는다.
> 위험 낮은 순서(문서 → 정의 → 훅/권한 → 검증)로 배열했다. 선행 결정 필요한 미결정 항목을 함께 표기.

| 적용이슈 | 제목 | 대상 파일 | 선행 결정 | 위험도 |
|---------|------|----------|-----------|--------|
| OPS-01-A | 자동화 레벨(L0/L1/L1.5/L2) 개념·기본값 L1.5·L2 승격조건 문서화 | `docs/공정개선-v2.md` | C10 | 낮음 |
| OPS-01-B | 헌법에 "쓰기 주체 단일·공방장 직접 git 금지·기본 L1.5·위험명령 deny 참조" 명문화 | `CLAUDE.md` | — | 낮음 |
| OPS-01-C | repo-manager 모드 2에 L1.5 절차 + commit/push 전 7항목 필수 보고(4-1) + 위험명령 금지 | `.claude/agents/repo-manager.md` | C4(완료) | 중간 |
| OPS-01-D | 개발 루프 커맨드에 "필수보고→승인→실행" + push 2~3 묶음 승인 지점 명시 | `commands/개발착수.md`·`개발재개.md` | C3(완료) | 중간 |
| OPS-01-E | `/승인`의 push 검증·이슈 close 게이트 정합성 재정리(중복 게이트 정리) | `commands/승인.md` | C8 | 중간 |
| OPS-01-F | 04-깃허브 규칙에 레벨별 허용/금지 명령표·dry-run·로그 박제 규칙 추가 | `.claude/rules/04-깃허브.md` | — | 낮음 |
| OPS-01-G | 위험명령 deny-list 차단 훅 신설/확장(`block-dangerous-git.py` 또는 기존 확장), repo-manager에 "쓰기 허용+위험 차단" 적용 | `.claude/hooks/`, 에이전트 훅 | C5 | **높음** |
| OPS-01-H | git 쓰기 권한 allow/deny 정리 + `bypassPermissions`에서 git 쓰기 제외 | `.claude/settings*.json` | C7 | 중간 |
| OPS-01-I | (선택) 명시적 push 승인 커맨드 `/git반영` 신설 검토 | `commands/` | C3·C8 | 낮음 |
| OPS-01-J | 회귀 검증: 더미 프로젝트 개발 루프 1바퀴로 동선·필수보고·차단 확인 | — | A~H | 낮음 |

권장 적용 순서: **A → B → F → C → D → E → H → G → (I) → J**
(문서·규칙로 합의를 먼저 박제하고, 정의·권한을 바꾼 뒤, 가장 위험한 훅 변경을 마지막 직전에, 끝으로 회귀 검증.)

선행 미결정 매핑: C5→G / C6→(납품 태그, 별도) / C7→H / C8→E·I / C9→적용순서(본 9장이 답) / C10→A·L2 승격.
