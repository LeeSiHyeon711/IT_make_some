# 15 · ADR — 도구 구조 결정 (3-tool 유지)

> ADR(Architecture Decision Record). 상상공방 Lite MCP 서버의 도구 구조를 3-tool로 유지하고 배포를 우선한다.
> 작성일: 2026-06-19 · 상태: **채택(Accepted)** · 관련: [`13`](13-streamable-http-examples-research.md), [`14`](14-playmcp-submission.md)

---

## 배경 (Context)

`design_first_experiment`가 도구당 지연의 병목이다(실측 ~5s). PlayMCP 권장 기준은 **도구당 p99 3000ms**.
이를 맞추기 위해 4-tool 분할(`prepare_intake` / `diagnose_idea` / `plan_validation_mission` / `compose_result_card`)을 검토했다.

### 측정 근거
- **LLM 호출당 floor ≈ 1.25s** (최소 출력 호출 실측, TTFT+왕복+모델기본 — 출력량 무관).
- 그 위에 **출력량 비례 생성시간**이 더해짐: prepare ~2.9s / diagnose ~3.4s / design ~4.9s.
- design 초과분 ~3.65s = **미션 본문 토큰 생성**(쪼갤 수 없는 단일 응집 생성).
- 모델: `claude-haiku-4-5`, 한국어 출력(토큰당 글자수가 적어 생성시간↑).

### 4-tool 분할 효과 추정
| 시나리오 | plan_validation_mission | compose_result_card | 병목 도구 p99 |
|----------|------------------------|---------------------|---------------|
| compose 비-LLM(순수 조립) | ~1.25 floor + 미션생성 ~3.0 ≈ **~4.2s** | ~10ms | **여전히 >3000ms** |
| compose LLM(톤/why 생성) | ~4.2s | ~3.3s | plan >3000ms + 총지연 악화 |

→ 두 경우 모두 병목 도구가 3000ms를 못 맞춘다.

---

## 결정 (Decision)

**A. 3-tool 유지 후 배포 우선.** 4-tool 분할은 보류한다.

### 이유
1. **병목은 tool 개수가 아니라 LLM 호출 floor(~1.25s) + 출력량이다.** 미션 본문 생성(~3.5s)이 핵심이며, 도구를 쪼갠다고 사라지지 않는다.
2. **4-tool 분할은 floor를 한 번 더 추가해 총 지연을 악화시킬 수 있다.** LLM 호출이 2회가 되면 floor(~1.25s)가 중복되고, 무거운 미션 생성은 여전히 한 도구(plan)에 남는다.
3. **PlayMCP tool 3~10개 조건은 3-tool로도 충족한다.** 도구 개수는 쟁점이 아니다.
4. **현재 3-tool 구조는 Docker / MCP Inspector / 실제 LLM smoke test까지 이미 검증됐다.** 재검증 1사이클을 새로 치를 이유가 약하다.
5. **`design_first_experiment`는 구조 분리보다 출력 축소 + timeout/fallback으로 관리한다.** (출력 축소는 3-tool 안에서도 동일하게 가능 — 분할 고유의 이득이 없다.) graceful stub fallback이 도구 성공(`isError: false`)을 보장하므로 3000ms 미달이 배포 차단 사유가 아니다.
6. **향후 실제 사용자 흐름에서 필요해지면 `compose_result_card` 분리를 재검토한다.** (결과카드 편집자 역할 = docs/07 철학과 정합. 단 그때의 트리거는 "지연"이 아니라 "사용자 흐름/책임 분리 필요".)

---

## 영향 (3-tool 유지 시)
- schema / verify_mcp.py / README / docs / Docker·Inspector: **변경 없음** (이미 검증된 상태 유지).
- 지연 관리: 출력 축소(이미 적용: success_criteria 1, do_not_build_yet ≤2, why 1~2문장, max_tokens 500) + `max_retries=0`(지연 상한 고정) + `LLM_TIMEOUT_SECONDS` 5~6.

## 폐기/재검토 트리거 (Reconsider when)
- 실제 사용자 테스트에서 결과카드 편집/톤 보정이 독립 단계로 필요해질 때 → `compose_result_card` 분리 재검토.
- 더 빠른 모델 도입 또는 출력 영어화 등으로 미션 생성이 충분히 빨라질 때 → 구조 단순성 유지가 더 유리.

## 대안 (기각)
- **4-tool 분할**: 위 이유 1·2로 기각(병목 미해소 + 비용↑).
- **3000ms 강제 달성(분할 외)**: 미션 출력 추가 축소·영어화·프롬프트 캐싱·더 빠른 모델 — 모델/언어 레벨 결정으로 이번 범위 밖. 필요 시 별도 ADR.
