<!--
3단계 설계자 산출물. AI-Morning-Brief 증분 기능 `--monthly` 2/3.
분해 사유(FEAT-11 서두 참조): FEAT-11=신호·필터 엔진(순수 계산), FEAT-12=그 신호를 소비해
Claude로 합성(신뢰도 라벨) → 전문 .md 저장 → run_monthly 오케스트레이션(트리거/커서/경로).
FEAT-12 단독 완료 시 `python main.py --monthly --no-discord`로 reports/<YYYY>/monthly/M##.md 생성.
Discord 전송·스케줄러·엔드투엔드 통합테스트는 FEAT-13.
seam: FEAT-12는 "전문 리포트 파일을 만든다"까지(Discord 미전송). #12의 요구 2(신뢰도 라벨)·6(노이즈 상단 명시 포맷)이 여기서 최종 렌더된다.
-->

# FEAT-12 — 월간보고 합성·전문 생성·오케스트레이션 (`--monthly` 엔진 · 신뢰도 라벨)

- 매칭 이슈: #12 (확정 시 4단계에서 번호 일치 등록 — GitHub 이슈 #12(개선요구)와 별개의 개발 이슈)
- 작성일: 2026-07-02
- 상위 설계서: `03-설계/설계서.md` (하단 "(증분) 월간 흐름 리포트 `--monthly` 설계 — 애드덤 (#12)")
- 의존: **FEAT-11** (`src/monthly.py` 신호·필터 함수), 기존 `analyzer._call_claude`, `reporter.save_bucket_report`(주간에서 이미 일반화됨).

## 1. 목적
FEAT-11이 정제한 월간 신호(대표 기사·정량 신호·2단계 필터 결과·노이즈 분류·소스 편중)를 **Claude로 합성**해 각 핵심 주장에 **신뢰도 라벨 `[확정]/[추정]/[주의]`(#12-2)** 를 붙이고, **노이즈 제외 기준·건수를 상단에 명시(#12-7 포맷)** 한 월간 전문 `.md`를 생성한다. 트리거는 **시각/날짜 + `meta.last_monthly_ym` 커서(멱등)** 로 판정하고, 경로는 주간이 예약한 `reports/<YYYY>/monthly/M##.md`(달력연도)에 `save_bucket_report` 재사용으로 저장한다. 6/21 실험본을 "재현 가능·신뢰도 표기·경계 명시된 정식 리포트"로 형식화한다. Discord 전송은 FEAT-13이 덧붙인다.

## 2. 범위
### 구현할 것
- `src/monthly.py` (수정) — `run_monthly(args)` 오케스트레이션 추가(트리거→경계분리→1차필터→노이즈분류→집계→대표선별→아이디어→합성→전문저장→커서갱신). FEAT-13 Discord hook 자리는 주석으로 비워 둠.
- `src/analyzer.py` (수정) — 하단에 `synthesize_monthly(...)` + `_build_monthly_prompt(...)` + `_parse_monthly_response(...)` + `_fallback_monthly(...)` 추가. 기존 `_call_claude` **재사용**.
- `src/reporter.py` (수정) — 하단에 `build_monthly_report(...)` 추가. `save_bucket_report`는 **이미 존재(주간)** — 재사용만, 수정 금지.
- `main.py` (수정) — `--monthly`(store_true)·`--month YYYY-MM`(수동 강제) 인자 + `run_monthly` 디스패치(주간 분기 다음, 일간 분기 앞).
### 구현하지 않을 것
- FEAT-11 순수 함수(집계·필터·편중·대표선별) 로직 — **소비만**, 재구현 금지.
- `notifier.build_monthly_message`, run_monthly의 Discord 전송, 월간 plist, `tests/test_monthly.py` 엔드투엔드, README → **전부 FEAT-13**.
- 주간/일간 코드(`run_weekly`/`build_weekly_report`/`analyze`/`build_report`/`pipeline.run`) 수정 금지 — **추가만**.
- DB 스키마 변경 금지(커서는 `meta` key-value row `last_monthly_ym` — 새 컬럼/테이블 금지).

## 3. 입력 / 출력
### 입력
- CLI: `python main.py --monthly [--month YYYY-MM] [--no-discord] [--force-fallback]`.
- 설정: `load_config()`(`paths.db`/`paths.reports_dir`, `claude.model`, `claude.max_tokens`=**20000**), `load_secrets()`(ANTHROPIC_API_KEY), `load_operator_profile()`.
  - **max_tokens=20000 근거**: 월간은 30일치(주간의 ~4배 기간) 다수 테마·주장별 신뢰도 라벨·편중/노이즈 표까지 합성 출력이 길어져, 주간 16000으로는 한국어 요약이 중간 절삭될 위험이 있어 여유값으로 상향(일간·주간 truncation 경험 기반).
- 데이터: `articles`(대상 월 collected_at 넓은 범위로 조회 후 FEAT-11 partition으로 경계 확정), 일간 `.md`(아이디어), `meta.last_monthly_ym`(커서).
### 출력
- 월간 전문: `reports/<YYYY>/monthly/M##.md` (예 `reports/2026/monthly/M06.md`). **달력연도·M##**.
- `meta.last_monthly_ym` 갱신(예 `"2026-06"`).
- 종료코드 0(정상/skip). DB 쓰기 오류만 예외 전파 → 1.

## 4. 동작 흐름 (`run_monthly`)
1. `load_config`/`load_secrets`/`load_operator_profile`, `init_db(cfg["paths"]["db"])`, `now=datetime.now()`.
2. **대상 월 산정**: `--month` → `parse_month_arg`, `forced=True`. 미지정 → `target_month(now)`(직전 달), `forced=False`. `ym = month_key(y,m)`.
3. **트리거(멱등)**: `last = get_meta(conn, "last_monthly_ym")`. `forced` 아니고 `last == ym`면 `logging.info("월간보고 skip ...")` 후 **return 0**.
4. **조회+경계**: `first, last = month_bounds(y, m)`. `raw = get_articles_by_range(conn, f"{first}T00:00:00", f"{last}T23:59:59")` (collected_at 범위, 넓게). `in_p, out_p = partition_by_period(raw, first, last)` (#12-1, published_at 경계 확정).
5. **1차 필터(#12-3)**: `passed, rejected = first_stage_filter(in_p)`. `noise = classify_noise(rejected)` (#12-7).
6. **집계·대표·아이디어**: `signals = aggregate_month(passed, first, last)` / `top = select_top_articles_monthly(passed, TOP_LIMIT)` (#12-4) / `ideas = collect_monthly_ideas(cfg["paths"]["reports_dir"], first, last)`.
7. **기반 메타 구성**: `basis = {"collected": len(raw), "in_period": len(in_p), "out_of_period": len(out_p), "first_stage_passed": len(passed), "noise": noise, "represented": len(top)}` — 상단 명시·다이제스트 재료.
8. **Claude 합성(#12-2)**: `result = synthesize_monthly(signals, ideas, top, basis, profile, api_key, model, max_tokens, force_fallback)`. 각 핵심 주장 앞에 신뢰도 라벨. 실패/키없음/강제 → `_fallback_monthly`(코드 규칙 라벨).
9. **전문 빌드/저장**: `md = build_monthly_report(ym, first, last, signals, ideas, top, out_p, basis, result["synthesis"], result["mode"])` → `path = save_bucket_report(cfg["paths"]["reports_dir"], y, "monthly", f"M{m:02d}", md)`.
10. **(FEAT-13 hook)** Discord 전송 자리 — FEAT-12에서는 주석으로 비워 둔다.
11. **커서 갱신**: `set_meta(conn, "last_monthly_ym", ym)`. 로그 후 return 0.

## 5. 수정 예상 파일
- `05-개발/src/monthly.py` (수정 — `run_monthly` 추가. FEAT-11의 순수 함수 import·호출)
- `05-개발/src/analyzer.py` (수정 — 월간 합성 4함수 하단 추가, 기존 코드 미변경)
- `05-개발/src/reporter.py` (수정 — `build_monthly_report` 하단 추가, `save_bucket_report` 재사용)
- `05-개발/main.py` (수정 — `--monthly`/`--month` 인자 + 디스패치)

## 6. 데이터 구조 / 함수 / 클래스

### 6-1. `src/monthly.py` — `run_monthly` (FEAT-11 함수 소비)
```python
import logging, datetime
from src.config_loader import load_config, load_secrets, load_operator_profile
from src.storage import init_db, get_articles_by_range, get_meta, set_meta
from src.analyzer import synthesize_monthly
from src.reporter import build_monthly_report, save_bucket_report
# FEAT-11 순수 함수 (동일 모듈에 이미 존재)
# target_month, parse_month_arg, month_bounds, month_key, partition_by_period,
# first_stage_filter, classify_noise, aggregate_month, select_top_articles_monthly,
# collect_monthly_ideas, TOP_LIMIT


def run_monthly(args) -> int:
    """월간보고 엔진 진입점. 트리거→경계→2단계필터→집계→합성(신뢰도 라벨)→전문 저장→커서.

    ★ Discord 전송은 FEAT-13이 10번 단계(hook)에 추가한다. FEAT-12는 전송하지 않는다.
    """
    cfg = load_config()
    secrets = load_secrets()
    profile, _is_default = load_operator_profile(
        cfg.get("paths", {}).get("operator_profile", "config/operator_profile.md"))
    conn = init_db(cfg["paths"]["db"])
    now = datetime.datetime.now()

    if getattr(args, "month", None):
        y, m = parse_month_arg(args.month)
        forced = True
    else:
        y, m = target_month(now)
        forced = False
    ym = month_key(y, m)

    if not forced and get_meta(conn, "last_monthly_ym") == ym:
        logging.info("월간보고 skip — 이미 생성됨: %s", ym)
        return 0

    first, last = month_bounds(y, m)
    raw = get_articles_by_range(conn, f"{first}T00:00:00", f"{last}T23:59:59")
    in_p, out_p = partition_by_period(raw, first, last)          # #12-1
    passed, rejected = first_stage_filter(in_p)                  # #12-3
    noise = classify_noise(rejected)                             # #12-7
    signals = aggregate_month(passed, first, last)              # 정량 신호 + 편중(#12-5,6)
    top = select_top_articles_monthly(passed, TOP_LIMIT)        # #12-4 대표 압축
    ideas = collect_monthly_ideas(cfg["paths"]["reports_dir"], first, last)
    basis = {"collected": len(raw), "in_period": len(in_p), "out_of_period": len(out_p),
             "first_stage_passed": len(passed), "noise": noise, "represented": len(top)}

    claude = cfg.get("claude", {})
    result = synthesize_monthly(
        signals, ideas, top, basis, profile,
        secrets.get("ANTHROPIC_API_KEY"),
        claude.get("model", "claude-sonnet-4-6"),
        claude.get("max_tokens", 20000),
        force_fallback=getattr(args, "force_fallback", False),
    )
    md = build_monthly_report(ym, first, last, signals, ideas, top, out_p, basis,
                              result["synthesis"], result["mode"])
    path = save_bucket_report(cfg["paths"]["reports_dir"], y, "monthly", f"M{m:02d}", md)

    # ── (FEAT-13 hook) Discord 월간 다이제스트 전송 자리. FEAT-12에서는 비워 둔다. ──

    set_meta(conn, "last_monthly_ym", ym)
    logging.info("월간보고 생성: %s → %s (mode=%s)", ym, path, result["mode"])
    return 0
```

### 6-2. `src/analyzer.py` 추가 함수 (신뢰도 라벨 프롬프트, `_call_claude` 재사용)
```python
def synthesize_monthly(signals, ideas, top_articles, basis, operator_profile,
                       api_key=None, model="claude-sonnet-4-6",
                       max_output_tokens=20000, force_fallback=False) -> dict:
    """월간 흐름 합성. 각 핵심 주장에 신뢰도 라벨을 부여(#12-2). 2차 필터(공방 적용성)를 랭킹으로 수행.

    반환: {"mode": "claude"|"fallback", "synthesis": {...}}.
    synthesis 스키마(라벨은 문자열 앞에 '[확정] '/'[추정] '/'[주의] ' 접두):
      {"one_line_summary": str,
       "flow_themes": [str,...<=4],       # 각 항목 라벨 접두
       "notable_events": [str,...<=3],    # 각 항목 라벨 접두
       "workshop_actions": [str,...<=3],  # 2차 필터 = 공방 적용성 상위
       "next_month_watch": [str,...<=3],
       "narrative": str}
    모든 예외(키없음·모델오류·API실패·JSON파싱실패)는 _fallback_monthly로 흡수.
    """
    if force_fallback or not api_key:
        return {"mode": "fallback",
                "synthesis": _fallback_monthly(signals, ideas, top_articles, basis)}
    try:
        prompt = _build_monthly_prompt(signals, ideas, top_articles, basis, operator_profile)
        text = _call_claude(prompt, api_key, model, max_output_tokens)  # 기존 호출 재사용
        data = _parse_monthly_response(text)
        return {"mode": "claude", "synthesis": data}
    except Exception as ex:
        logging.error("월간 합성 실패 → fallback: %s", ex)
        return {"mode": "fallback",
                "synthesis": _fallback_monthly(signals, ideas, top_articles, basis)}


def _build_monthly_prompt(signals, ideas, top_articles, basis, operator_profile) -> dict:
    """월간 합성 system/user. 신뢰도 라벨 규칙 + 2단계 필터(2차=공방 적용성) + 정량 신호 근거 투입."""
    import json
    sig = {
        "total": signals["total"], "span_days": signals["span_days"],
        "peak_day": signals["peak_day"], "sparse_days": signals["sparse_days"],
        "persistent_stems": [{"tag": t["tag"], "count": t["count"], "day_span": t["day_span"]}
                             for t in signals["persistent_stems"][:14]],
        "bursts": [{"tag": t["tag"], "count": t["count"], "day_span": t["day_span"]}
                   for t in signals["bursts"][:8]],
        "sources_top": signals["sources"][:8],       # 편중도 (#12-5)
        "noise_sources": signals["noise_sources"],    # (#12-6)
    }
    # 각 대표 기사에 교차출처수 동봉 → 신뢰도 라벨 판정 근거 (#12-2)
    arts = [{"title": a["title"], "source": a["source"], "summary": a.get("summary", ""),
             "tags": a.get("tags", []), "importance": a.get("importance", 0),
             "relevance": a.get("relevance", 0),
             "cross_source_count": a.get("cross_source_count", 1)} for a in top_articles]
    system = (
        "당신은 IT상상공방 운영자의 'AI 월간 흐름 분석가'다. 아래 <운영자 프로필>을 렌즈로,"
        " <정량 신호>와 <대표 기사>를 반드시 근거로 삼아(서술이 숫자·출처와 모순되지 않게)"
        " 한 달의 구조적 흐름을 합성하라.\n"
        "■ 2단계 필터: 1차 관련성 통과분만 받았다. 당신은 2차로 '상상공방 적용 가능성'을 기준으로"
        " 흐름·액션의 우선순위를 정하라(무관한 것은 뒤로 미루되 삭제하지 말 것).\n"
        "■ 신뢰도 라벨(필수): flow_themes·notable_events의 각 항목과 narrative의 각 핵심 주장 앞에"
        " 다음 규칙으로 라벨을 접두하라 —\n"
        "  [확정] : 교차출처(cross_source_count)≥2 이거나 공식 릴리스/공식 블로그로 확인된 사실.\n"
        "  [추정] : 단일 출처·해석·전망(추론이 포함된 서술).\n"
        "  [주의] : 미확인·논란·반박 가능성이 있는 주장.\n"
        " 지정된 JSON만 출력하라.\n\n"
        "<운영자 프로필>\n" + operator_profile + "\n</운영자 프로필>")
    user = (
        "<분석 기반>\n" + json.dumps(basis, ensure_ascii=False) + "\n</분석 기반>\n\n"
        "<정량 신호 (JSON)>\n" + json.dumps(sig, ensure_ascii=False) + "\n</정량 신호>\n\n"
        "<일간 아이디어 통합 (반복일수 days 포함)>\n" + json.dumps(ideas, ensure_ascii=False) + "\n</일간 아이디어>\n\n"
        "<대표 기사 (JSON, cross_source_count 포함)>\n" + json.dumps(arts, ensure_ascii=False) + "\n</대표 기사>\n\n"
        "출력 스키마:\n"
        '{"one_line_summary":"<이번 달 한 줄 요약>",'
        '"flow_themes":["[라벨] 주요 흐름 테마 최대 4개"],'
        '"notable_events":["[라벨] 주목 사건 최대 3개"],'
        '"workshop_actions":["공방 즉시 착수 액션 최대 3개(2차=공방 적용성 상위, 반복 아이디어 우선)"],'
        '"next_month_watch":["다음 달 관전 포인트 최대 3개"],'
        '"narrative":"<월간 흐름 서술, 한국어, 핵심 주장마다 [라벨] 접두, 정량 신호에 정합>"}')
    return {"system": system, "user": user}


def _parse_monthly_response(text: str) -> dict:
    """첫 { ~ 마지막 } 슬라이스 후 json.loads + 누락 필드 기본값 보정."""
    import json
    s, e = text.find("{"), text.rfind("}")
    if s == -1 or e == -1:
        raise ValueError("월간 응답에 JSON 객체가 없음")
    data = json.loads(text[s:e + 1])
    data.setdefault("one_line_summary", "")
    for k in ("flow_themes", "notable_events", "workshop_actions", "next_month_watch"):
        data.setdefault(k, [])
    data.setdefault("narrative", "")
    return data


def _label_by_cross_source(article) -> str:
    """fallback용 규칙 라벨: 교차출처≥2 → [확정], ==1 → [추정]. (코드 기계 부여)"""
    return "[확정]" if int(article.get("cross_source_count", 1)) >= 2 else "[추정]"


def _fallback_monthly(signals, ideas, top_articles, basis) -> dict:
    """규칙기반 월간 합성(Claude 없이). 신뢰도 라벨을 교차출처수로 기계 부여(#12-2)."""
    stems = [t["tag"] for t in signals["persistent_stems"][:6]]
    one = (f"이번 달 대표 {basis.get('represented', 0)}건(수집 {basis.get('collected', 0)}·"
           f"1차통과 {basis.get('first_stage_passed', 0)}). 지속 줄기: "
           + (", ".join(stems) if stems else "없음"))
    themes = [f"{_label_by_cross_source(a)} {a['title']}" for a in top_articles[:4]]
    events = [f"{_label_by_cross_source(a)} {a['title']}" for a in top_articles[:3]]
    actions = [i["text"] for i in ideas[:3]] or [a["title"] for a in signals["workshop_picks"][:3]]
    watch = [t["tag"] for t in signals["persistent_stems"][:3]]
    noise = basis.get("noise", {})
    narr = ("[확정] [기본 월간 리포트] " + one
            + (f" / 노이즈 제외 {noise.get('_total', 0)}건" if noise else "")
            + (f" / 범위 외 {basis.get('out_of_period', 0)}건" if basis.get("out_of_period") else ""))
    return {"one_line_summary": one, "flow_themes": themes, "notable_events": events,
            "workshop_actions": actions, "next_month_watch": watch, "narrative": narr}
```
> ⚠ 4함수는 **analyzer.py 하단에 추가**. 기존 `analyze`/`synthesize_weekly`/`_call_claude`/`_build_prompt` 등은 **수정 금지**(회귀 방지). `_call_claude`만 호출 재사용.

### 6-3. `src/reporter.py` — `build_monthly_report` (노이즈 상단 명시 #12-7)
```python
def build_monthly_report(ym, first, last, signals, ideas, top, out_of_period, basis,
                         synthesis, mode) -> str:
    """월간 전문 Markdown. 표(강도/태그/소스편중/노이즈유형)는 전문에만. 상단에 분석 기반·노이즈 명시.

    섹션 순서:
      헤더(월·범위·모드) →
      > 분석 기반 한 줄: 수집N·1차통과M·대표K·범위외P·노이즈Q · 신뢰도(확정/추정/주의 카운트) →   # #12-7 상단
      ## 0. 분석 기반과 제외 내역 (노이즈 유형별 건수 표 + 범위 외 참고 항목 건수) →              # #12-7
      ## 1. 이번 달 한 줄 요약 →
      ## 2. 월간 핵심 흐름 테마 (각 항목 [라벨]) →                                              # #12-2
      ## 3. 주목 사건 (각 항목 [라벨]) →
      ## 4. 강도 추이 표(일자·건수·고중요·평균) →
      ## 5. 지속 줄기 vs 단발 버스트 표 →
      ## 6. 소스 편중도 표(소스·건수·점유율%·평균중요도·노이즈여부) →                            # #12-5,6
      ## 7. 공방 관련 픽 / 공방 즉시 착수 액션 →
      ## 8. 일간 아이디어 통합 →
      ## 9. 다음 달 관전 포인트 →
      ## 10. 월간 서술(narrative, [라벨] 포함) →
      ## 11. 대표 원문 링크(top) →
      ## 12. 범위 외 참고 항목(out_of_period 제목·published_at, 건수 상한 20)                    # #12-1
    """
    # (builder 구현: 아래 표 포맷 고정)
    # 상단 배지(mode=="fallback"이면): > **AI 합성 실패 / 기본 월간 리포트**
    # 분석 기반 한 줄 예:
    #   > 📊 수집 287 · 1차통과 188 · 대표 40 · 범위외 11 · 노이즈 88 · 신뢰도 [확정 a/추정 b/주의 c]
    # 노이즈 유형 표: | 유형 | 건수 |   (basis["noise"] 항목, _total 제외 후 나열, 합계행)
    # 소스 편중 표:   | 소스 | 건수 | 점유율 | 평균중요도 | 노이즈 |   (share*100 반올림 %)
    # 강도 표:        | 일자 | 건수 | 고중요 | 평균중요도 |   (sparse=True 행 끝 " ⚠️ 데이터 빈약")
    # 태그 표:        | 태그 | 등장(정규화) | 일수 | 구분(지속/버스트) |
    # 신뢰도 카운트:  synthesis의 flow_themes+notable_events+narrative에서 '[확정]/[추정]/[주의]' 등장 수 집계
    ...
```
> `save_bucket_report`는 **주간(FEAT-09)에서 이미 월간 확장 예약으로 일반화**돼 있다. `bucket="monthly", name="M06"`만 넘기면 `reports/2026/monthly/M06.md`로 저장된다 — reporter에 새 저장 헬퍼 추가 금지, 재사용만.

### 6-4. `main.py` 수정
```python
# build_parser()에 추가:
p.add_argument("--monthly", action="store_true",
               help="월간 흐름 리포트를 생성(트리거: meta 커서로 멱등). launchd 월간 잡이 호출")
p.add_argument("--month", default=None, metavar="YYYY-MM",
               help="월간보고 대상 달을 명시(수동 강제 생성). 예: 2026-06")

# main()에서 --weekly 분기 다음, 일간 pipeline.run 앞에 분기:
if getattr(args, "monthly", False):
    from src.monthly import run_monthly
    return run_monthly(args)
```
> 기존 `--weekly/--week/--test/--dry-run/--no-discord/--force-fallback/--from/--to/--check-sources` 변경 금지. `--monthly`는 일간 분기보다 먼저, `--weekly` 분기 뒤에 둔다.

## 7. 예외 처리
- **빈 월(기사 0건)**: FEAT-11 함수가 빈 신호 반환 → `build_monthly_report`가 "데이터 없음" 톤 리포트 생성, basis 전부 0, 커서 갱신(정상 경계).
- **Claude 실패/키없음/모델오류/JSON파싱실패/강제**: `synthesize_monthly`가 `_fallback_monthly`로 흡수(mode="fallback", 라벨은 교차출처수로 기계 부여). 파이프라인 중단 없음(일간·주간 정책 동형).
- **일간 `.md` 일부 없음**: `collect_monthly_ideas`(=weekly) 해당 일 skip.
- **DB 쓰기 오류(set_meta)**: 예외 전파 → 종료코드 1(커서 미갱신 → 다음 실행 재생성).
- **`--month` 형식/범위 오류**: `parse_month_arg` ValueError → 비정상 종료(사용자 입력 오류).
- **신뢰도 라벨 누락(Claude가 접두 안 함)**: 리포트가 라벨 없는 원문을 그대로 렌더(깨지지 않음). fallback 경로는 항상 라벨 포함.

## 8. 완료 조건
- `python main.py --monthly --no-discord --force-fallback` 실행 시 `reports/<YYYY>/monthly/M##.md` 생성 + 종료코드 0.
- 같은 달에 `--monthly`(--month 없이) 재실행 시 `meta.last_monthly_ym` 일치로 **skip**(중복 없음).
- `--month 2026-06` 지정 시 해당 달로 강제 생성(커서 일치여도 재생성).
- 전문 상단에 **분석 기반 한 줄(수집·1차통과·대표·범위외·노이즈·신뢰도 카운트)** 과 **## 0 노이즈 유형별 건수 표**가 존재(#12-7).
- 흐름 테마·주목 사건 항목에 **`[확정]/[추정]/[주의]` 라벨**이 붙는다(#12-2, fallback도 포함).
- **소스 편중도 표**(점유율%)와 **범위 외 참고 항목 섹션**이 존재(#12-1,5,6).
- 기존 `pytest -q tests/test_weekly.py tests/test_pipeline.py` 회귀 없음. FEAT-11 순수 함수 테스트도 유지.

## 9. 테스트 방법
```bash
cd projects/AI-Morning-Brief/05-개발
pytest -q tests/test_weekly.py tests/test_pipeline.py   # 회귀
# 격리 DB에 6월 데이터 시드 후 강제 생성 (fallback, 외부 의존 없음)
export MORNINGBRIEF_DB=$(mktemp -u).db
export MORNINGBRIEF_REPORTS_DIR=$(mktemp -d)
python -c "
import datetime
from src.storage import init_db, insert_article, update_analysis
c=init_db('$MORNINGBRIEF_DB')
first=datetime.date(2026,6,1)
for i in range(28):
    d=(first+datetime.timedelta(days=i)).isoformat()
    for j in range(4):
        u=f'u{i}-{j}'
        src='VentureBeat AI' if j==3 else ('OpenAI' if j==0 else 'Google')
        insert_article(c,{'url':u,'title':f'MCP agent update {i}-{j}','source':src,
            'published_at':d,'collected_at':d+'T08:00:00','raw_excerpt':'x'})
        update_analysis(c,u,'요약',['mcp','agent'],9 if j==0 else 2, 8 if j==0 else 1, 1)
# 범위 밖(4월) 1건 + 무관(정치) 1건
insert_article(c,{'url':'old','title':'old news','source':'X','published_at':'2026-04-01','collected_at':'2026-06-02T08:00:00','raw_excerpt':'x'}); update_analysis(c,'old','s',['ai'],2,1,1)
insert_article(c,{'url':'pol','title':'city election policy','source':'Y','published_at':'2026-06-05','collected_at':'2026-06-05T08:00:00','raw_excerpt':'x'}); update_analysis(c,'pol','s',[],0,0,1)
print('seeded')
"
python main.py --monthly --month 2026-06 --no-discord --force-fallback ; echo exit=$?
P="$MORNINGBRIEF_REPORTS_DIR"/2026/monthly/M06.md
ls "$P"
grep -q "분석 기반\|수집" "$P" && echo "기반 명시 OK"      # #12-7
grep -q "\[확정\]\|\[추정\]" "$P" && echo "신뢰도 라벨 OK"   # #12-2
grep -q "점유율\|편중" "$P" && echo "소스 편중 표 OK"        # #12-5
grep -q "범위 외" "$P" && echo "범위 외 항목 OK"             # #12-1
```
> 엔드투엔드 통합 자동화 테스트(`tests/test_monthly.py` 월간 파트)와 Discord 검증은 FEAT-13. FEAT-12는 위 수동 절차로 확인.

## 10. 금지 사항
- `notifier.build_monthly_message`·run_monthly Discord 전송·월간 plist·README·엔드투엔드 통합테스트 작성 금지(전부 FEAT-13).
- FEAT-11 순수 함수(집계·필터·편중·대표선별) 재구현·수정 금지 — **import 소비만**.
- 기존 일간·주간 코드(`analyze`/`synthesize_weekly`/`build_report`/`build_weekly_report`/`run_weekly`/`pipeline.run`) 수정 금지 — **추가만**.
- `save_bucket_report` 수정·새 저장 헬퍼 추가 금지 — 주간이 일반화한 것을 재사용.
- DB 스키마 변경 금지(커서는 `meta.last_monthly_ym` key-value row).
- 불필요한 라이브러리 추가 금지(표준 라이브러리 + 기존 의존성만).
- 이 이슈 범위를 벗어나는 리팩터링 금지.

> ⚠️ **기존 코드 보호 원칙**: 기존 weekly/daily 코드의 **동작 변경은 금지**한다. 공통 함수 **추출**은 허용하지만(= 기존 코드에서 로직을 그대로 빼내 재사용만, 재작성·시그니처 변경 불가), 기존 테스트가 **하나라도 실패하면 리팩토링을 되돌린다**.
