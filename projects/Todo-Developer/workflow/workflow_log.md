# Workflow Log — Todo-Developer (v3 Workflow 검증)

> 실행 주체 Sonnet이 **각 단계 종료 즉시** 채운다. 10개 항목을 모두 기록(없으면 N/A).
> 아래는 예정된 단계를 미리 펼친 골격이다. ③↔④ 반복이 생기면 라운드를 추가한다.

- 실험 시작: 2026-07-02 18:01
- 실험 종료: 2026-07-02 18:40
- 총 라운드(Review↔Build 반복 횟수): 3라운드(로컬 LLM 개입) + 1라운드(Sonnet 실행테스트 기반, 로컬 LLM 미개입)

---

## 단계 기록 양식 (복사해서 사용)

```
### [단계번호] 단계명
1. 담당 모델:
2. 입력(핵심 요청):
3. 출력(핵심 응답 요약):
4. 반영 여부: Accepted / Modified / Rejected — (이유)
5. Reviewer 의견:
6. Architect 의견:
7. Claude(Sonnet) 최종 판단: (왜 반영/미반영했는지 근거)
8. 시간(소요):
9. 토큰(Input / Output):
10. 비고:
```

---

## ① Planning
- 담당 모델: Sonnet
- 입력(핵심 요청): 01-상담/요구사항-정의서.md (Todo 5기능·단일페이지·LocalStorage MVP)
- 출력(핵심 응답 요약): 단일 파일 index.html 구조 확정, 데이터모델 `{id,text,completed,createdAt}`, LocalStorage 키 `todo-developer:items`, 필터는 표시 전용(all/active/completed) → `02-기획/PRD.md`에 반영
- 반영 여부: Accepted
- Reviewer 의견: N/A
- Architect 의견: N/A
- Sonnet 최종 판단: 요구사항-정의서 §2 범위를 그대로 옮긴 계획이라 임의 확장 없음. 확정.
- 시간: 즉시 (계획 수립, 로컬 LLM 호출 없음)
- 토큰(In/Out): N/A (자체 계획 단계, 별도 측정 없음)

## ② Build (초안)
- 담당 모델: local_builder (qwen2.5:14b)
- 입력: PRD.md의 Builder 전달용 요청 초안(5기능+데이터모델+LocalStorage키+범위제한) 그대로 `ask_builder`에 투입
- 출력: 단일 index.html 생성 완료(요청 형식 준수). 단, Sonnet 육안 점검에서 의심 지점 발견 — `onclick="addTask()"` 등 HTML 인라인 핸들러가 참조하는 함수들이 `DOMContentLoaded` 콜백 스코프 안에 정의돼 전역에서 안 보일 가능성, `renderTasks(taskList)` 매개변수명이 바깥 DOM 요소 `taskList`를 가려 배열에 `.innerHTML`/`.append` 호출하는 모순 발견
- 반영 여부: Modified 예정 (Review 단계에서 확정 후 Build 수정에 반영)
- Reviewer 의견: N/A (다음 단계)
- Architect 의견: N/A
- Sonnet 최종 판단: 초안은 05-개발/index.html로 우선 저장. 의심되는 버그 2건을 Reviewer에게 명시적으로 짚어 검증 요청 예정.
- 시간: 18:01:49 ~ 18:05:14 (약 3분 25초)
- 토큰(In/Out): 로컬 LLM 호출 — Claude 토큰 소모 없음(프롬프트 작성 비용만 발생)

## ③ Review
- 담당 모델: local_reviewer (deepseek-coder-v2:16b)
- 입력(넘긴 코드): 전체 index.html 첨부 시도 → 400 Bad Request 반복 재현. 파일 크기를 단계적으로 줄여 이분 탐색한 결과, **deepseek-coder-v2:16b는 system prompt+첨부파일+질문 합계가 약 2~3KB(모델별 상이, qwen2.5:14b는 동일 파일 정상 처리)를 넘으면 Ollama가 400을 반환**하는 것으로 확인. 최종적으로 의심 함수 2개만 뽑은 스니펫(300자)+짧은 질문으로 우회해 리뷰 진행.
- 출력(발견 문제): (1) `renderTasks(taskList)` 매개변수명이 바깥 DOM 변수와 겹치는 문제 — "중간" 심각도로 지적(혼동 가능성). (2) `onclick="addTask()"` 등 인라인 핸들러가 `DOMContentLoaded` 클로저 안에 갇힌 함수를 호출하는 문제 — **"문제 없습니다"로 오판**
- 반영 여부: Modified — Reviewer 판단 일부만 수용, 나머지는 Sonnet이 직접 재확인해 재정의
- Reviewer 의견: [중간] taskList 이름 충돌 — 혼동 가능성 지적(실제로는 배열에 .innerHTML/.append() 호출로 크래시하는 치명 버그인데 심각도 과소평가). [오판] onclick 인라인 핸들러 스코프 문제를 "문제 없음"으로 답함(실제로는 인라인 핸들러가 전역 스코프에서 함수를 찾으므로 addTask is not defined로 깨지는 치명 버그)
- Architect 의견: N/A
- Sonnet 최종 판단: Reviewer의 오판(onclick 스코프)과 과소평가(taskList 섀도잉)를 Sonnet이 직접 코드 재검증으로 정정. 두 건 모두 **치명**으로 재분류하고 Build 수정에 명시적으로 지시. 컨텍스트 길이 제약은 improvement.md에 "로컬 LLM 한계"로 기록.
- 시간: 18:05:47 ~ 18:11:23 (약 5분 36초, 400 에러 재현·이분탐색 포함)
- 토큰(In/Out): 로컬 LLM 호출 — Claude 토큰은 디버깅 과정에서의 Bash/Read 호출분만 소모(정확한 수치 미측정)

## ④ Build 수정 (Review 반영) — 총 4라운드 진행
- 담당 모델: local_builder (qwen2.5:14b), 라운드4는 Sonnet 직접 수정
- 입력/출력/반영여부(라운드별):
  - **R1**: 입력 — ③에서 확정한 치명 버그 2건(onclick 인라인 핸들러 스코프, renderTasks 매개변수명 섀도잉) 수정 지시. 출력 — onclick 제거하고 addEventListener로 전환, 매개변수명 `items`로 변경. 반영 — Accepted, 두 버그 모두 해결 확인.
  - **R2**: 입력 — R1 결과물을 Sonnet이 직접 재검토하다 발견한 **새 치명 버그**(필터 버튼 "active" 클래스가 필터 식별자와 선택 하이라이트 두 용도로 겹쳐 써서, 클릭 시 `querySelector` null → TypeError로 크래시) 수정 지시. 출력 — `data-filter` 속성 도입 + "selected" 클래스로 식별/하이라이트 역할 분리. 반영 — Accepted.
  - **R3**: 입력 — Sonnet이 추가로 발견한 사소한 불일치 2건(기본 필터 버튼 "선택됨" 표시가 "진행중"으로 돼있는데 실제 화면은 "전체" 표시라 불일치 / PRD에 있던 엔터키 추가 기능 누락) 수정 지시. 출력 — 기본 selected를 "전체" 버튼으로 이동, 엔터키 이벤트 추가하되 **다시 `onkeydown="handleKeyDown(event)"` 인라인 속성으로 구현 — R1에서 고친 것과 같은 스코프 버그를 재도입**(handleKeyDown도 DOMContentLoaded 클로저 안에 있어 인라인 호출 시 "not defined" 에러). 반영 — Modified(부분 반려): 필터 기본값·엔터키 로직 자체는 Accepted, inline onkeydown 구현 방식만 Rejected.
  - **R4**: 담당 — Sonnet 직접 수정(로컬 LLM 재호출 대신). 사유 — 프로토콜 §2 "같은 지적이 2회 반복되면 종료" 기준에 해당하는 반복 패턴(인라인 핸들러 스코프 버그가 2번째로 재발)이라 라운드를 더 늘리지 않고 한 줄짜리 수정(onkeydown 속성 제거 + `newTaskInput.addEventListener('keydown', handleKeyDown)` 추가)을 직접 반영.
- Reviewer 의견: (③ 참조, R1에만 반영)
- Architect 의견: N/A (다음 단계)
- Sonnet 최종 판단: 로컬 builder가 "인라인 이벤트 핸들러 스코프 버그"를 지시로 한 번 고쳐도 새 기능 추가 시 같은 패턴을 무의식적으로 재사용하는 경향을 확인 — 반복 회귀(regression) 위험으로 improvement.md에 기록. 4라운드 만에 기능·버그 모두 해결로 판단하고 ⑤ Architecture Review로 진행.
- 시간: 18:11:56 ~ 18:29:34 (약 17분 38초, builder 1회 타임아웃(300s) 재시도 포함)
- 토큰(In/Out): 로컬 LLM 호출 3회(R1~R3) — Claude 토큰은 프롬프트 작성·코드 재검증·R4 직접 수정 분만 소모(정확한 수치 미측정)

## ⑤ Architecture Review
- 담당 모델: local_architect_reviewer (qwen3:30b-a3b)
- 입력(넘긴 코드): 최종 index.html(6.5KB, R4 수정 반영본) 첨부. 타임아웃 없이 1회 호출로 응답 수신(우려했던 300초 타임아웃 미발생).
- 출력: (1) 단일 파일 구조는 MVP로 적절 (2) `crypto.randomUUID` 지원 여부 분기 로직이 MVP치고 약간 과한 구현이나 핵심 기능엔 영향 없음(경미) (3) 범위 초과 기능 없음 — 요구사항 5개 기능만 구현 확인
- 반영 여부: Rejected(수정 불필요) — 지적된 과설계가 "경미"하고 기능에 영향 없다고 스스로 밝혔으므로 MVP 철학(속도>완벽) 상 그대로 유지
- Reviewer 의견: N/A
- Architect 의견: 구조 적절 / 경미한 과설계(ID 생성 fallback) / 범위 초과 없음
- Sonnet 최종 판단: crypto.randomUUID fallback은 MVP 요구사항(§3 "과설계 금지")에 저촉될 만큼 크지 않고 오히려 브라우저 호환성을 위한 최소한의 안전장치라 판단 — 제거하지 않고 유지. ⑥ Build 수정은 불필요.
- 시간: 18:30:27 ~ 18:33:37 (약 3분 10초, 타임아웃 없음)
- 토큰(In/Out): 로컬 LLM 호출 1회 — Claude 토큰은 프롬프트 작성분만 소모

## ⑥ Build 수정 (Architect 반영 + Sonnet 실행 테스트로 발견한 버그)
- 담당 모델: N/A(Architect 지적 없음, 수정 불필요) → 그러나 **Sonnet이 실제 헤드리스 브라우저(jsdom) 실행 테스트를 돌리다 로컬 LLM 3종(Builder/Reviewer/Architect) 전원이 놓친 치명 버그 2건을 추가 발견**하여 Sonnet이 직접 수정
- 입력: 최종 승인 전 "실행해서 확인" 원칙에 따라 claude-in-chrome으로 file:// 실제 브라우저 테스트를 시도했으나 브라우저 확장 미연결로 불가 → 대안으로 jsdom 기반 헤드리스 스모크테스트 작성(추가/엔터키추가/완료토글/필터3종/LocalStorage저장/삭제 8개 시나리오)
- 출력(발견한 신규 치명 버그, 코드 리뷰·구조검토 양쪽 모두 놓쳤던 것):
  1. `li.classList.add('todo-item', task.completed ? 'completed' : '')` — 완료 안 된 항목일 때 빈 문자열을 클래스 토큰으로 넘겨 `DOMTokenList.add()`가 `SyntaxError: The token provided must not be empty`를 던짐 → **미완료 항목이 하나라도 있으면 렌더링 전체가 중단**돼 목록이 통째로 안 보임(추가 자체가 화면에 안 나타나는 것처럼 보임)
  2. `label.innerHTML += task.text;` — `innerHTML +=`는 label의 자식 전체를 문자열로 직렬화한 뒤 재파싱해서 새로 만드는 연산이라, 직전에 `checkbox.addEventListener('change', ...)`로 붙여둔 리스너가 달린 체크박스 객체가 통째로 파괴되고 리스너 없는 새 체크박스로 교체됨 → **완료 체크박스를 눌러도 아무 반응이 없음(toggleCompleted가 영원히 호출 안 됨)**, 실제 앱의 핵심 기능(완료 토글) 자체가 작동 불능
- 반영 여부: Accepted — 둘 다 즉시 수정(①은 `classList.add` 호출을 조건부 2줄로 분리, ②는 `label.appendChild(document.createTextNode(task.text))`로 교체)
- Reviewer 의견: 해당 없음(③ 리뷰 범위 밖 — 코드를 읽기만 하고 실행하지 않아 발견 못함)
- Architect 의견: 해당 없음(⑤ 검토 범위 밖 — 마찬가지로 실행 없이 구조만 봄)
- Sonnet 최종 판단: **정적 코드 리뷰(로컬 LLM 2종 포함)로는 절대 못 잡는 버그였고, 실제로 실행해봐야만 드러났다.** "타입체크·리뷰는 코드 정확성 검증이지 기능 정확성 검증이 아니다"라는 원칙이 이 실험에서 그대로 증명됨. 수정 후 8개 시나리오 전부 PASS 확인.
- 시간: 18:33:37 ~ 18:39:11 (약 5분 34초, 디버깅 포함)
- 토큰(In/Out): 로컬 LLM 호출 없음(Sonnet 단독 실행·디버깅) — 이 구간은 로컬 LLM 위임이 아예 불가능한 영역이었음(실행 환경 필요)
- 추가 검증(사용자 제보로 Playwright MCP 존재 확인 후 진행): jsdom 헤드리스 테스트는 실제 브라우저가 아니므로, 로컬 HTTP 서버(`python3 -m http.server`)로 index.html을 띄우고 **Playwright로 실제 Chromium 브라우저에서 재검증**함 — 추가(버튼) / 추가(엔터키) / 완료토글+취소선 표시 / 필터(진행중) 클릭 크래시 없음 / 새로고침 후 완료상태·목록 유지(LocalStorage) / 삭제, 총 6개 시나리오를 스크린샷으로 육안 확인. 전부 정상 동작(콘솔 에러는 favicon 404 1건뿐, 앱 로직과 무관). jsdom 결과와 실제 브라우저 결과가 일치함을 확인.

## ⑦ Final Approval
- 담당 모델: Sonnet
- 입력: 전체 리뷰 이력(③~⑥) + 최종 코드(`05-개발/index.html`) + jsdom 실행 테스트 8개 시나리오 결과
- 출력(최종 판단): **승인**. 요구사항-정의서 §2의 5개 기능(추가/삭제/완료토글/필터/LocalStorage) + PRD에 명시한 엔터키 추가까지 전부 실행 테스트로 확인됨. MVP 범위 초과 없음(Architect 확인).
- 반영 여부: Accepted
- Reviewer 의견 종합: local_reviewer는 컨텍스트 길이 제약(2~3KB 초과 시 400 에러)으로 전체 파일을 못 보고 스니펫만 리뷰했으며, 그 결과 치명 버그 1건을 "문제없음"으로 오판하고 1건은 심각도를 과소평가함. 로컬 리뷰어의 판단은 참고용으로만 채택하고 Sonnet이 직접 재검증함.
- Architect 의견 종합: 단일 파일 구조 적절, 경미한 과설계(ID생성 fallback) 외 문제 없음, 범위 초과 없음 — 그대로 수용.
- Sonnet 최종 판단: 승인 근거 — (1) 8개 실행 시나리오 전부 PASS (2) MVP 범위 준수 (3) 로컬 LLM이 놓친 치명 버그 2건(빈 문자열 classList 크래시, innerHTML+= 리스너 파괴)은 Sonnet의 실행 테스트로 직접 잡아 수정 완료. 최종 미반영 항목: 없음(모든 지적사항 반영 또는 정당한 사유로 기각 완료).
- 시간: 18:39:11 ~ 18:40:05 (약 54초, 판단만)
- 토큰(In/Out): 로컬 LLM 호출 없음 — Sonnet 자체 판단

---

## 합계 / 요약
- 로컬 LLM 총 호출 수: 9회 (Builder 5회: 초안1+수정3+재시도1포함, Reviewer 4회: 실패한 400에러 재현 시도 다수 제외하고 성공응답 기준, Architect 1회) — 정확히는 Builder 성공 4회(초안·R1·R2·R3)+실패1(타임아웃 재시도), Reviewer 성공 3회(작은파일 테스트 2회+실제스니펫리뷰 1회)+실패 다수(400에러), Architect 성공 1회
- Sonnet 총 토큰(In/Out): 정확한 수치 미측정(도구가 토큰 사용량을 반환하지 않음) — 정성 평가는 아래 참조
- 로컬 LLM으로 위임해 Sonnet이 아낀 것으로 추정되는 작업량(정성): 초기 HTML/CSS/JS 전체 타이핑, 4라운드의 버그수정 코드 재작성(각 6KB 내외)을 Sonnet이 직접 타이핑하지 않고 위임함 — 코드 생성량 기준으로는 상당한 절감. 다만 버그를 찾아내는 "판단" 작업(치명 버그 4건 중 3건)은 결국 Sonnet이 직접 수행해야 했음.
- Review↔Build 반복 총 횟수: 3라운드(③↔④) + 실행테스트 기반 추가 수정 1회(⑥, 로컬 LLM 미개입) = 사실상 4라운드
