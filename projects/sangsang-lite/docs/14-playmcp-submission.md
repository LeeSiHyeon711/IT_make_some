# 14 · PlayMCP 제출 절차 + 준비 상태 점검

> 상상공방 Lite MCP 서버(`prototype/mcp-server/`)를 카카오 PlayMCP에 등록하기 위한 절차와 준비 현황.
> 정리일: 2026-06-19. 서버 구조 근거: [`13-streamable-http-examples-research.md`](13-streamable-http-examples-research.md).

---

## 1. 현재 준비 상태 (검증 완료)

| 항목 | 상태 | 근거 |
|------|------|------|
| Streamable HTTP `/mcp` (stateless) | ✅ | `POST /mcp 200 OK`, `Terminating session: None` |
| tools/list 3개 | ✅ | prepare_intake / diagnose_idea / design_first_experiment |
| annotations 5종 | ✅ | title + readOnly/destructive/idempotent/openWorld |
| tools/call (체이닝) | ✅ | verify_mcp.py + Inspector CLI 통과 |
| Docker build (linux/amd64) | ✅ | `sangsang-lite-mcp:latest` (anthropic 0.111.0 + mcp 1.28.0) |
| LLM 호출 껍데기 + stub fallback | ✅ | 키/크레딧 문제 시 graceful fallback(meta.api_error), 도구는 성공 |
| 서버 자체 지연(p99) | ✅ 충분 | **stub 베이스라인 도구당 p99 < 17ms** (MCP 오버헤드 무시 가능) |

### 지연 측정 결과 (`scripts/measure_latency.py`)

```
순수 stub (LLM 호출 없음, n=20):
  prepare_intake          p99 ≈ 16.7ms
  diagnose_idea           p99 ≈  8.8ms
  design_first_experiment p99 ≈  9.2ms
  → MCP 전송+서버 오버헤드는 사실상 무시 가능(<20ms).

LLM env on + 크레딧 부족(실패 API 왕복 포함, n=15):
  도구당 p99 ≈ 640~894ms  (Anthropic 연결 왕복 오버헤드)
```

**해석**: PlayMCP p99 3000ms(도구당) 기준에서 **프레임워크 비용은 무시 가능**하고, 위험 요인은 오직 LLM 호출 지연이다.

> ⚠️ **정정(2026-06-20, 실측 후)**: 위 "스텁/실패 왕복" 수치는 LLM 호출이 **성공하기 전** 측정값이다.
> 실제 LLM 성공 호출을 측정한 결과(claude-haiku-4-5, 한국어 출력):
> - **prepare_intake ≈ 3s, diagnose_idea ≈ 3s** → 3000ms 권장 기준 경계선(목표로 유지).
> - **design_first_experiment ≈ 5~7s** → **3000ms를 초과하는 것이 정상**(LLM 출력 특성상). 초기 "3000ms 안에 들 가능성 높다"는 낙관은 **현실과 어긋났다.**
>
> **3000ms 권장 기준은 그대로 기록·유지하되**, design은 [`15 ADR`](15-tool-structure-decision.md)에서 결정한 대로 **출력 축소 + timeout/fallback 관리 전략**을 따른다.
> - prepare/diagnose: 3000ms 권장 기준을 **목표로 유지**.
> - design: **5~7s 실측 가능성을 인정**하고, `LLM_TIMEOUT_SECONDS=10`으로 **stub 대신 실제 LLM 결과를 받는 전략**을 채택(타임아웃은 지연을 늘리는 게 아니라 ~5~7s 호출을 끝까지 받을지/6s에서 stub로 깎을지를 정할 뿐).
> - PlayMCP는 실측 테스트에서 **~6s 응답을 정상 수용**(하드 컷 아님). graceful fallback이 `isError:false`를 보장.
> - **결론: design의 3000ms 초과는 등록 차단 사유가 아니다.**

---

## 2. 제출 전 미결 항목 (사람/외부 필요)

1. **Anthropic 크레딧 충전** — 현재 키는 인증은 되나 크레딧 부족(400 credit balance too low). LLM 경로 성공 스모크(`meta.source=llm`)와 LLM 지연 실측이 이 이후 가능.
   - 충전 전까지는 stub fallback으로 동작(도구 호출은 정상). 데모는 가능하나 "진짜 진단 품질"은 안 나옴.
2. **공개 배포 URL 확보** — PlayMCP는 **Remote MCP**라 외부에서 접근 가능한 HTTPS URL이 필요하다. (로컬 `localhost:8080`은 등록 불가)
   - 후보: Koyeb / Fly.io / Render / Cloud Run 등에 `sangsang-lite-mcp:latest` 배포 → `https://<host>/mcp`.
   - 배포 시 env: `PORT`(플랫폼 주입), `LLM_ENABLED=true`, `ANTHROPIC_API_KEY`, `MODEL_NAME`.
3. **PlayMCP 개발가이드 원문 확인** — 인증 방식·허용 헤더·헬스체크·필수 메타 등 세부는 공식 가이드 기준으로 최종 확인. (출처: tech.kakao.com/posts/734, playmcp.kakao.com)
4. **카카오 계정** — 등록은 카카오 계정 보유자가 수행.

---

## 3. 제출 절차 (PlayMCP)

> 카카오 계정이 있으면 누구나 등록 가능. 등록 → 심사 → 통과 시 최초 '나에게만 공개' → **전체 공개 필수**.

1. **공개 배포**: 이미지를 외부 플랫폼에 올려 `https://<host>/mcp`를 연다.
   - 배포 후 원격 URL로 동일 검증:
     ```bash
     python scripts/verify_mcp.py https://<host>/mcp
     npx @modelcontextprotocol/inspector --cli https://<host>/mcp --transport http --method tools/list
     ```
2. **PlayMCP 접속**: https://playmcp.kakao.com/ 에 카카오 계정으로 로그인.
3. **MCP 서버 등록**: 서버 이름·설명·**원격 URL(`/mcp`)** 입력.
   - 이름/도구명에 **`kakao` 문자열 금지** → `sangsang-lite` 등 사용(이미 준수).
   - Transport는 **Streamable HTTP**.
4. **AI 채팅 환경에서 테스트**: 등록 직후 PlayMCP 채팅에서 도구 호출이 보이는지 확인.
5. **심사 제출**: 등록 심사 요청 → 통과 시 최초 '나에게만 공개'.
6. **전체 공개**: 심사 통과 후 전체 공개로 전환(공모전 제출 시 필수).
7. (공모전) **MCP Player 10** 제출 — 심사 기준: 창의성·편의성·기술적 안정성.

---

## 4. 제출 직전 체크리스트

- [ ] Anthropic 크레딧 충전 + LLM 경로 성공 스모크(`meta.source=llm`) 1회
- [ ] LLM 경로 지연 측정(도구당 p99 < 3000ms 확인)
- [ ] 공개 HTTPS 배포 + 원격 URL로 verify_mcp.py / Inspector 통과
- [ ] 서버/도구 이름에 `kakao` 미포함 (✅ 이미 준수)
- [ ] PlayMCP 개발가이드 원문과 대조(인증/헤더/헬스체크)
- [ ] PlayMCP 등록 → 심사 → 전체 공개
- [ ] (선택) MCP Player 10 공모전 제출

---

## 5. 참고 출처

- PlayMCP 공식: https://playmcp.kakao.com/
- 카카오 기술블로그(PlayMCP 개발기): https://tech.kakao.com/posts/734
- MCP Player 10 공모전: https://www.kakaocorp.com/page/detail/11855
