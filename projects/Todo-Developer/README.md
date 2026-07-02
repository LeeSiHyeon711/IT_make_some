# Todo-Developer — IT상상공방 v3 Workflow 검증 실험

> **이 프로젝트는 Todo 페이지를 만드는 것이 목적이 아니다.**
> IT상상공방 **v3 Workflow**(Sonnet + 로컬 LLM 협업 구조)를 실제 프로젝트에 적용해 검증하는 **첫 번째 실험 프로젝트**다.
> 기능보다 워크플로우를, 개발 결과보다 협업 구조를 우선 평가한다.

---

## 역할 분담 (중요)

| 구분 | 모델 | 이 프로젝트에서의 역할 |
|------|------|------------------------|
| 틀 제작자 | **Opus 4.8** | 이 실험의 골격·프로토콜·템플릿을 만든다. **실행에는 참여하지 않는다.** 종료 후 사용자 호출 시 **평가자**로 복귀한다. |
| 오케스트레이터 (요청서의 "Claude") | **Sonnet** | 실험을 실제로 진행한다. Planning·의사결정·의견 병합·최종 승인을 수행한다. |
| Builder | **local_builder** (qwen2.5:14b) | 기능 구현 |
| Reviewer | **local_reviewer** (deepseek-coder-v2:16b) | 버그·요구사항 누락·품질 리뷰 |
| Architect Reviewer | **local_architect_reviewer** (qwen3:30b-a3b) | 구조·과설계·MVP 범위·확장성 검토 |

## 산출물 형태 (MVP 고정)

- **단일 웹페이지(Single Page)**. 웹앱 수준 아님.
- 기능: Todo 추가 / 삭제 / 완료 체크 / 진행중·완료 필터 / LocalStorage 저장. **여기까지만.**
- 디자인은 심플한 MVP 수준. 과잉 구현 금지.

## 폴더 안내

```
Todo-Developer/
├── README.md              # 이 파일
├── 실험프로토콜.md         # ★ Sonnet이 그대로 따라 실행하는 구동 지침 (실행 전 필독)
├── progress.md            # 진행/중단·재개 추적
├── 01-상담/요구사항-정의서.md   # 실험 요구사항 (확정)
├── 02-기획/PRD.md          # Planning 단계에서 Sonnet이 채움
├── 03-설계/               # 설계 산출물 (필요 시)
├── 05-개발/               # 실제 코드 (Sonnet+로컬 LLM 협업 결과물) — 원격 repo: Todo-Developer
└── workflow/              # ★ 이 실험의 핵심 산출물
    ├── workflow_log.md    # 전 단계 기록 (10개 항목)
    ├── retrospective.md   # 회고
    └── improvement.md     # 워크플로우 개선 아이디어
```

> 원격 Git 저장소 `Todo-Developer`는 이미 생성되어 있다. 실제 코드는 `05-개발/`에서 작업 후 해당 repo에 push한다.

## 성공 조건

- [ ] Builder가 기능 구현 가능
- [ ] Reviewer가 실제 문제를 발견
- [ ] Architect가 구조적 문제를 발견
- [ ] Sonnet(Claude)이 최종 의사결정을 수행
- [ ] 로컬 LLM이 실제 Claude 토큰 절감에 기여
- [ ] Workflow가 중단 없이 완료

위 6개를 만족하면 실험 성공으로 간주한다.
