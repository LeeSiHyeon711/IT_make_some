<!--
3단계 설계자 산출물. AI-Morning-Brief 증분 기능 `--weekly` (주간 흐름 리포트).
분해 사유: 확정 스펙(중형)을 builder 1회 호출에 완결 가능한 2개로 나눈다.
  FEAT-09 = 집계·생성 엔진(트리거 판정 + 정량신호 집계 + Claude 합성 + 전문 .md 저장)
  FEAT-10 = Discord 다이제스트 전송 + 일요일 launchd 스케줄러 + 통합테스트
FEAT-09는 "전문 리포트 파일을 만든다"까지, FEAT-10은 "그 결과를 배달·예약·검증한다"까지로
seam(이음매)을 명확히 분리한다. FEAT-09 단독 완료 시 `python main.py --weekly --no-discord`로
reports/<연도>/weekly/W##.md 가 생성되어야 한다. Discord 전송은 FEAT-10이 run_weekly에 덧붙인다.
-->

# FEAT-09 — 주간보고 집계·생성 엔진 (`--weekly` 코어)

- 매칭 이슈: #9 (확정 시 4단계에서 번호 일치 등록)
- 작성일: 2026-06-28
- 상위 설계서: `03-설계/설계서.md` (하단 "(증분) 주간보고 `--weekly` 설계" 섹션)

## 1. 목적
7일치 일간 데이터(SQLite `articles` + 일간 리포트 `.md`)를 종합해 "이번 주 AI 흐름" 주간보고 **전문(.md)** 을 생성한다. 단순 합본이 아니라 **집계에서만 나오는 정량 신호**(강도 추이·태그 지속성·소스 신호품질·공방 픽·아이디어 통합)를 코드로 계산해 Claude 합성의 근거로 투입하고, 그 결과를 `reports/<ISO연도>/weekly/W##.md`에 저장한다. **트리거는 시간/날짜 + meta 커서(멱등)** 로 판정한다. 실데이터 2회 생성으로 개념 검증 완료된 스펙을 코드로 형식화한다.

## 2. 범위
### 구현할 것
- `src/weekly.py` (신규): ISO 주차 산정·트리거/커서 판정, 정량 신호 집계, 일간 아이디어 통합, 상위기사 선별, `run_weekly(args)` 오케스트레이션(트리거→집계→합성→전문 저장→커서 갱신).
- `src/analyzer.py` (수정): 주간 합성 함수 `synthesize_weekly(...)` + 규칙기반 `_fallback_weekly(...)` + `_build_weekly_prompt(...)` 추가. 기존 `_call_claude(...)`를 **그대로 재사용**.
- `src/reporter.py` (수정): 경로 일반화 헬퍼 `save_bucket_report(...)` + 주간 전문 빌더 `build_weekly_report(...)` 추가.
- `main.py` (수정): `--weekly`(store_true)·`--week YYYY-W##`(수동 강제/명시 주차) 인자 추가 + `run_weekly` 디스패치.
### 구현하지 않을 것
- **Discord 다이제스트 전송**(FEAT-10). FEAT-09의 `run_weekly`는 전문 저장·커서 갱신까지만 하고 Discord는 보내지 않는다.
- **launchd 주간 plist**(FEAT-10), **통합테스트 `tests/test_weekly.py`**(FEAT-10), README 운영 안내(FEAT-10).
- 월간 리포트(#12, 범위 외). 단 경로 헬퍼는 월간 확장 가능하게 일반화만 한다.
- 기존 일간 파이프라인(`pipeline.run`)·일간 프롬프트·DB 스키마 변경 금지(meta는 기존 key-value row만 사용).

## 3. 입력 / 출력
### 입력
- CLI: `python main.py --weekly [--week YYYY-W##] [--no-discord] [--force-fallback]`.
- 설정: `load_config()`(`paths.db`/`paths.reports_dir`, `claude.model`=claude-sonnet-4-6, `claude.max_tokens`=16000), `load_secrets()`(ANTHROPIC_API_KEY), `load_operator_profile()`.
- 데이터: `articles` 테이블(대상 ISO 주차의 `collected_at` 범위), 일간 리포트 `reports/<YYYY>/<MM>/<DD>.md` 7개(아이디어 섹션 추출용), `meta.last_weekly_iso_week`(커서).
### 출력
- 주간 전문: `reports/<ISO연도>/weekly/W##.md` (예: `reports/2026/weekly/W26.md`).
- `meta.last_weekly_iso_week` 갱신(예: `"2026-W26"`).
- 종료코드 0(정상/skip). DB 쓰기 오류만 예외 전파 → 1.

## 4. 동작 흐름
1. `run_weekly(args)` 시작: `load_config`/`load_secrets`/`load_operator_profile`, `init_db(cfg["paths"]["db"])`.
2. **대상 주차 산정**:
   - `--week` 지정 시 `parse_week_arg("2026-W26")` → `(iso_year, iso_week)`, `forced=True`.
   - 미지정 시 `target_iso_week(now)` → `now-2일`을 ISO 캘린더로 환산해 `(iso_year, iso_week)`, `forced=False`.
   - `week_key = f"{iso_year}-W{iso_week:02d}"`.
3. **트리거(멱등)**: `last = get_meta(conn, "last_weekly_iso_week")`. `forced`가 아니고 `last == week_key`면 `logging.info("주간보고 skip ...")` 후 **return 0**. (커서 방식 채택 근거: 7장)
4. **수집 범위**: `monday, sunday = iso_week_bounds(iso_year, iso_week)` (`date.fromisocalendar`). `articles = get_articles_by_range(conn, f"{monday}T00:00:00", f"{sunday}T23:59:59")` — tags는 이미 list로 역직렬화됨.
5. **정량 신호 집계**: `signals = aggregate_week(articles, monday, sunday)` (6장 5개 신호).
6. **일간 아이디어 통합**: `ideas = collect_daily_ideas(cfg["paths"]["reports_dir"], monday, sunday)` — 7개 일간 .md의 "## 상상공방에 적용할 수 있는 아이디어" 섹션 불릿을 추출·빈도 집계.
7. **상위기사 선별**: `top = select_top_articles(articles, limit=35)` — 제목 정규화 중복제거 후 importance desc, relevance desc 상위 35.
8. **Claude 합성**: `synthesis = synthesize_weekly(signals, ideas, top, profile, api_key, model, max_tokens, force_fallback=getattr(args,"force_fallback",False))`. 실패/키없음/강제 → `_fallback_weekly`(mode="fallback").
9. **전문 빌드/저장**: `md = build_weekly_report(week_key, monday, sunday, signals, ideas, top, synthesis, mode)` → `path = save_bucket_report(cfg["paths"]["reports_dir"], iso_year, "weekly", f"W{iso_week:02d}", md)`.
10. **(FEAT-10 자리)** Discord 전송 hook — FEAT-09에서는 비워 둔다(주석으로 표시).
11. **커서 갱신**: `set_meta(conn, "last_weekly_iso_week", week_key)`. `logging.info("주간보고 생성: %s → %s", week_key, path)`. return 0.

## 5. 수정 예상 파일
- `05-개발/src/weekly.py` (신규)
- `05-개발/src/analyzer.py` (수정 — 주간 합성 함수 추가, 기존 일간 코드 미변경)
- `05-개발/src/reporter.py` (수정 — 경로 헬퍼 + 주간 빌더 추가, 기존 일간 코드 미변경)
- `05-개발/main.py` (수정 — `--weekly`/`--week` 인자 + 디스패치)

## 6. 데이터 구조 / 함수 / 클래스

### 6-1. `src/weekly.py`
```python
"""주간 흐름 리포트 엔진 (FEAT-09). 일간 데이터 → 정량 신호 → Claude 합성 → 전문 저장."""
import logging, re, datetime
from collections import defaultdict
from datetime import timedelta

from src.config_loader import load_config, load_secrets, load_operator_profile
from src.storage import init_db, get_articles_by_range, get_meta, set_meta
from src.analyzer import synthesize_weekly
from src.reporter import build_weekly_report, save_bucket_report

# 정량 신호 임계값 — 라이브 시스템은 importance/relevance 0~10 스케일(스펙 확정).
# 0~5 스케일로 회귀하면 아래 상수만 조정하면 된다(집계 로직 불변).
HIGH_IMPORTANCE = 8        # 고중요 기준
WORKSHOP_RELEVANCE = 7     # 공방 관련 픽 기준
PERSIST_DAYS = 5           # 5일 이상 등장 = 지속 줄기
BURST_MAX_DAYS = 2         # 저지속(단발 버스트) 상한
NOISE_MIN_COUNT = 5        # 노이즈 후보 최소 볼륨
NOISE_AVG_IMPORTANCE = 3.0 # 노이즈 후보 평균 중요도 상한(미만)
SPARSE_DAY_MAX = 1         # 표본 빈약일(수집 ≤1건 = 전소스 실패 의심, 이슈 #13)


def target_iso_week(now: datetime.datetime) -> tuple[int, int]:
    """막 끝난(=이번 일요일에 닫히는) ISO 주차를 산정한다.

    anchor = now - 2일. 금요일(anchor)과 그 주 일요일은 같은 ISO 주(월~일)에 속하므로
    일요일 04:40 정시 실행은 그 주(W##)를 가리킨다. 늦게 깨어나도 자가치유:
      Sun  on-time : anchor=Fri → 같은 주
      Mon  +1일    : anchor=Sat → 같은 주
      Tue  +2일    : anchor=Sun → 같은 주
    (Wed 이후 = ~3일 초과 지연 시 다음 주로 넘어가며, 커서가 중복을 막는다.)
    반환: (iso_year, iso_week) — isocalendar의 ISO 주차연도(연말연초 어긋남 대응).
    """
    iso = (now - timedelta(days=2)).isocalendar()
    return iso[0], iso[1]


def parse_week_arg(s: str) -> tuple[int, int]:
    """'2026-W26' → (2026, 26). 형식 오류 시 ValueError."""
    m = re.fullmatch(r"(\d{4})-W(\d{1,2})", s.strip())
    if not m:
        raise ValueError(f"--week 형식은 YYYY-W## 이어야 함: {s}")
    return int(m.group(1)), int(m.group(2))


def iso_week_bounds(iso_year: int, iso_week: int) -> tuple[datetime.date, datetime.date]:
    """ISO 주차의 월요일·일요일 date를 반환(date.fromisocalendar, Py3.8+)."""
    monday = datetime.date.fromisocalendar(iso_year, iso_week, 1)
    sunday = datetime.date.fromisocalendar(iso_year, iso_week, 7)
    return monday, sunday


def _day_of(collected_at: str) -> str:
    """collected_at(ISO8601 문자열) → 'YYYY-MM-DD' (앞 10자)."""
    return (collected_at or "")[:10]


def aggregate_week(articles: list, monday: datetime.date, sunday: datetime.date) -> dict:
    """정량 신호 5종을 계산한다(스펙 C). Claude 합성 근거 + 전문 표 재료."""
    # 1) 일자별 강도 추이 (월~일 7행 고정)
    days = [(monday + timedelta(days=i)).isoformat() for i in range(7)]
    by_day = defaultdict(list)
    for a in articles:
        by_day[_day_of(a.get("collected_at"))].append(a)
    daily_intensity = []
    for d in days:
        items = by_day.get(d, [])
        imps = [int(x.get("importance", 0) or 0) for x in items]
        daily_intensity.append({
            "date": d,
            "count": len(items),
            "high_count": sum(1 for v in imps if v >= HIGH_IMPORTANCE),
            "avg_importance": round(sum(imps) / len(imps), 2) if imps else 0.0,
            "sparse": len(items) <= SPARSE_DAY_MAX,  # ⚠️ 데이터 품질 경고(전소스 실패 의심)
        })
    total = len(articles)
    peak = max(daily_intensity, key=lambda r: r["count"]) if daily_intensity else None
    sparse_days = [r["date"] for r in daily_intensity if r["sparse"]]

    # 2) 태그 빈도 × 지속성
    tag_total = defaultdict(int)
    tag_days = defaultdict(set)
    for a in articles:
        d = _day_of(a.get("collected_at"))
        for t in (a.get("tags") or []):
            key = str(t).lower().strip()
            if key:
                tag_total[key] += 1
                tag_days[key].add(d)
    tags = []
    for k in tag_total:
        span = len(tag_days[k])
        tags.append({
            "tag": k, "count": tag_total[k], "day_span": span,
            "persistent": span >= PERSIST_DAYS,                       # 지속 줄기
            "burst": tag_total[k] >= PERSIST_DAYS and span <= BURST_MAX_DAYS,  # 단발 버스트
        })
    tags.sort(key=lambda x: (x["day_span"], x["count"]), reverse=True)
    persistent_stems = [t for t in tags if t["persistent"]]
    bursts = [t for t in tags if t["burst"]]

    # 3) 소스 신호 품질
    src_count = defaultdict(int)
    src_imp = defaultdict(list)
    for a in articles:
        s = a.get("source", "?")
        src_count[s] += 1
        src_imp[s].append(int(a.get("importance", 0) or 0))
    sources = []
    for s in src_count:
        avg = round(sum(src_imp[s]) / len(src_imp[s]), 2) if src_imp[s] else 0.0
        sources.append({
            "source": s, "count": src_count[s], "avg_importance": avg,
            "noise_candidate": src_count[s] >= NOISE_MIN_COUNT and avg < NOISE_AVG_IMPORTANCE,
        })
    sources.sort(key=lambda x: x["count"], reverse=True)
    noise_sources = [s for s in sources if s["noise_candidate"]]

    # 4) 공방 관련 픽 (relevance >= 7)
    workshop_picks = sorted(
        [a for a in articles if int(a.get("relevance", 0) or 0) >= WORKSHOP_RELEVANCE],
        key=lambda x: (int(x.get("relevance", 0) or 0), int(x.get("importance", 0) or 0)),
        reverse=True,
    )

    return {
        "total": total,
        "daily_intensity": daily_intensity,
        "peak_day": peak,
        "sparse_days": sparse_days,
        "tags": tags,
        "persistent_stems": persistent_stems,
        "bursts": bursts,
        "sources": sources,
        "noise_sources": noise_sources,
        "workshop_picks": workshop_picks,
    }


def collect_daily_ideas(reports_dir: str, monday: datetime.date, sunday: datetime.date) -> list:
    """7개 일간 .md의 '## 상상공방에 적용할 수 있는 아이디어' 불릿을 추출·빈도 집계.

    반환: [{"text": str, "days": int}, ...] — 여러 날 반복(days>=2)을 우선(=즉시 착수),
          1회성은 하위. 파일 없음/섹션 없음은 조용히 skip.
    """
    section = "상상공방에 적용할 수 있는 아이디어"
    norm_count = defaultdict(int)
    norm_text = {}
    n = (sunday - monday).days + 1
    for i in range(n):
        d = monday + timedelta(days=i)
        path = f"{reports_dir}/{d.year:04d}/{d.month:02d}/{d.day:02d}.md"
        try:
            md = open(path, encoding="utf-8").read()
        except OSError:
            continue
        bullets = _extract_section_bullets(md, section)
        seen = set()
        for b in bullets:
            key = re.sub(r"\s+", " ", b.lower()).strip()
            if not key or key == "_해당 없음_" or key in seen:
                continue
            seen.add(key)
            norm_count[key] += 1
            norm_text.setdefault(key, b)
    out = [{"text": norm_text[k], "days": norm_count[k]} for k in norm_count]
    out.sort(key=lambda x: x["days"], reverse=True)
    return out


def _extract_section_bullets(md: str, title: str) -> list:
    """'## {title}' 헤더 ~ 다음 '## ' 사이의 '- ' 불릿 텍스트 리스트."""
    lines = md.splitlines()
    out, capture = [], False
    for ln in lines:
        if ln.startswith("## "):
            capture = (ln[3:].strip() == title)
            continue
        if capture and ln.lstrip().startswith("- "):
            out.append(ln.lstrip()[2:].strip())
    return out


def _norm_title(t: str) -> str:
    return re.sub(r"\s+", " ", (t or "").lower()).strip()


def select_top_articles(articles: list, limit: int = 35) -> list:
    """제목 정규화 중복제거 후 importance desc, relevance desc 상위 N."""
    best = {}
    for a in articles:
        k = _norm_title(a.get("title"))
        cur = best.get(k)
        if cur is None or int(a.get("importance", 0) or 0) > int(cur.get("importance", 0) or 0):
            best[k] = a
    uniq = list(best.values())
    uniq.sort(key=lambda x: (int(x.get("importance", 0) or 0), int(x.get("relevance", 0) or 0)),
              reverse=True)
    return uniq[:limit]


def run_weekly(args) -> int:
    """주간보고 엔진 진입점. 트리거→집계→합성→전문 저장→커서 갱신. 종료코드 반환.

    ★ Discord 전송은 FEAT-10이 10번 단계(hook)에 추가한다. FEAT-09는 전송하지 않는다.
    """
    cfg = load_config()
    secrets = load_secrets()
    profile, _is_default = load_operator_profile(
        cfg.get("paths", {}).get("operator_profile", "config/operator_profile.md"))
    conn = init_db(cfg["paths"]["db"])
    now = datetime.datetime.now()

    if getattr(args, "week", None):
        iso_year, iso_week = parse_week_arg(args.week)
        forced = True
    else:
        iso_year, iso_week = target_iso_week(now)
        forced = False
    week_key = f"{iso_year}-W{iso_week:02d}"

    if not forced and get_meta(conn, "last_weekly_iso_week") == week_key:
        logging.info("주간보고 skip — 이미 생성됨: %s", week_key)
        return 0

    monday, sunday = iso_week_bounds(iso_year, iso_week)
    articles = get_articles_by_range(conn, f"{monday}T00:00:00", f"{sunday}T23:59:59")
    signals = aggregate_week(articles, monday, sunday)
    ideas = collect_daily_ideas(cfg["paths"]["reports_dir"], monday, sunday)
    top = select_top_articles(articles, 35)

    claude = cfg.get("claude", {})
    result = synthesize_weekly(
        signals, ideas, top, profile,
        secrets.get("ANTHROPIC_API_KEY"),
        claude.get("model", "claude-sonnet-4-6"),
        claude.get("max_tokens", 16000),
        force_fallback=getattr(args, "force_fallback", False),
    )
    md = build_weekly_report(week_key, monday, sunday, signals, ideas, top,
                             result["synthesis"], result["mode"])
    path = save_bucket_report(cfg["paths"]["reports_dir"], iso_year, "weekly",
                              f"W{iso_week:02d}", md)

    # ── (FEAT-10 hook) Discord 다이제스트 전송 자리. FEAT-09에서는 비워 둔다. ──

    set_meta(conn, "last_weekly_iso_week", week_key)
    logging.info("주간보고 생성: %s → %s (mode=%s)", week_key, path, result["mode"])
    return 0
```

### 6-2. `src/analyzer.py` 추가 함수 (기존 `_call_claude` 재사용)
```python
def synthesize_weekly(signals, ideas, top_articles, operator_profile,
                      api_key=None, model="claude-sonnet-4-6",
                      max_output_tokens=16000, force_fallback=False) -> dict:
    """주간 흐름을 합성한다. 반환: {"mode": "claude"|"fallback", "synthesis": {...}}.

    synthesis 스키마:
      {"one_line_summary": str, "flow_themes": [str,...<=4],
       "notable_events": [str,...<=3], "workshop_actions": [str,...<=3],
       "next_week_watch": [str,...], "narrative": str}
    모든 예외(키 없음·모델명 오류·API 실패·JSON 파싱 실패)는 _fallback_weekly로 흡수.
    """
    if force_fallback or not api_key:
        return {"mode": "fallback", "synthesis": _fallback_weekly(signals, ideas, top_articles)}
    try:
        prompt = _build_weekly_prompt(signals, ideas, top_articles, operator_profile)
        text = _call_claude(prompt, api_key, model, max_output_tokens)  # 기존 일간 호출 재사용
        data = _parse_weekly_response(text)
        return {"mode": "claude", "synthesis": data}
    except Exception as ex:
        logging.error("주간 합성 실패 → fallback: %s", ex)
        return {"mode": "fallback", "synthesis": _fallback_weekly(signals, ideas, top_articles)}


def _build_weekly_prompt(signals, ideas, top_articles, operator_profile) -> dict:
    """주간 합성용 system/user dict. 정량 신호를 '근거'로 명시 투입(서술이 숫자에 정합하도록)."""
    import json
    sig = {
        "total": signals["total"],
        "daily_intensity": signals["daily_intensity"],
        "peak_day": signals["peak_day"],
        "sparse_days": signals["sparse_days"],
        "persistent_stems": [{"tag": t["tag"], "count": t["count"], "day_span": t["day_span"]}
                             for t in signals["persistent_stems"][:12]],
        "bursts": [{"tag": t["tag"], "count": t["count"], "day_span": t["day_span"]}
                   for t in signals["bursts"][:8]],
        "noise_sources": signals["noise_sources"],
    }
    arts = [{"title": a["title"], "source": a["source"], "summary": a.get("summary", ""),
             "tags": a.get("tags", []), "importance": a.get("importance", 0),
             "relevance": a.get("relevance", 0)} for a in top_articles]
    system = (
        "당신은 IT상상공방 운영자의 'AI 주간 흐름 분석가'다. 아래 <운영자 프로필>을 렌즈로,"
        " 제공된 <정량 신호>를 반드시 근거로 삼아(서술이 숫자와 모순되지 않게) 한 주의 흐름을"
        " 합성하라. 단발 버스트(노이즈)와 지속 줄기(구조적 흐름)를 구분하라. 지정된 JSON만 출력하라.\n\n"
        "<운영자 프로필>\n" + operator_profile + "\n</운영자 프로필>")
    user = (
        "<정량 신호 (JSON)>\n" + json.dumps(sig, ensure_ascii=False) + "\n</정량 신호>\n\n"
        "<일간 아이디어 통합 (반복일수 days 포함)>\n" + json.dumps(ideas, ensure_ascii=False) + "\n</일간 아이디어>\n\n"
        "<상위 기사 (JSON)>\n" + json.dumps(arts, ensure_ascii=False) + "\n</상위 기사>\n\n"
        "출력 스키마:\n"
        '{"one_line_summary":"<이번 주 한 줄 요약>",'
        '"flow_themes":["주요 흐름 테마 제목 최대 4개"],'
        '"notable_events":["주목 사건 최대 3개"],'
        '"workshop_actions":["공방 즉시 착수 액션 최대 3개(반복 등장 아이디어 우선)"],'
        '"next_week_watch":["다음 주 관전 포인트"],'
        '"narrative":"<주간 흐름 서술, 한국어, 정량 신호에 정합>"}')
    return {"system": system, "user": user}


def _parse_weekly_response(text: str) -> dict:
    """첫 { ~ 마지막 } 슬라이스 후 json.loads + 누락 필드 기본값 보정."""
    import json
    s, e = text.find("{"), text.rfind("}")
    if s == -1 or e == -1:
        raise ValueError("주간 응답에 JSON 객체가 없음")
    data = json.loads(text[s:e + 1])
    data.setdefault("one_line_summary", "")
    for k in ("flow_themes", "notable_events", "workshop_actions", "next_week_watch"):
        data.setdefault(k, [])
    data.setdefault("narrative", "")
    return data


def _fallback_weekly(signals, ideas, top_articles) -> dict:
    """규칙기반 주간 합성(Claude 없이). 정량 신호에서 직접 문장을 만든다."""
    stems = [t["tag"] for t in signals["persistent_stems"][:6]]
    one = f"이번 주 총 {signals['total']}건. 지속 줄기: " + (", ".join(stems) if stems else "없음")
    themes = [a["title"] for a in top_articles[:4]]
    events = [a["title"] for a in top_articles[:3]]
    actions = [i["text"] for i in ideas[:3]] or [a["title"] for a in signals["workshop_picks"][:3]]
    watch = [t["tag"] for t in signals["persistent_stems"][:2]]
    narr = ("[기본 주간 리포트] " + one
            + (f" / 노이즈 후보: {[s['source'] for s in signals['noise_sources']]}"
               if signals["noise_sources"] else ""))
    return {"one_line_summary": one, "flow_themes": themes, "notable_events": events,
            "workshop_actions": actions, "next_week_watch": watch, "narrative": narr}
```
> ⚠ `synthesize_weekly`/`_fallback_weekly`/`_build_weekly_prompt`/`_parse_weekly_response`는 **analyzer.py 하단에 추가**한다. 기존 `analyze()`·`_build_prompt()`·`_call_claude()`·`_parse_response()`·`_fallback_analyze()`는 **수정 금지**(일간 회귀 방지). `_call_claude`만 호출해 재사용한다.

### 6-3. `src/reporter.py` 추가 함수 (기존 save_report 패턴 일반화)
```python
def save_bucket_report(reports_dir, year, bucket, name, content) -> str:
    """reports/<year>/<bucket>/<name>.md 로 저장(makedirs + 경로 조립).

    주간: bucket="weekly", name="W26" → reports/2026/weekly/W26.md
    (월간 #12 확장 예약: bucket="monthly", name="M06")
    기존 save_report(일간)와 동일 패턴이며 일간 경로(<YYYY>/<MM>/<DD>.md)는 변경하지 않는다.
    """
    d = os.path.join(reports_dir, f"{int(year):04d}", bucket)
    os.makedirs(d, exist_ok=True)
    path = os.path.join(d, f"{name}.md")
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    return path


def build_weekly_report(week_key, monday, sunday, signals, ideas, top, synthesis, mode) -> str:
    """주간 전문 Markdown. 표(강도/태그/소스)는 전문에만 둔다(Discord는 표 미렌더).

    섹션: 헤더(주차·범위·모드) → 한 줄 요약 → 강도 추이 표 → 지속 줄기 vs 단발 버스트 표
         → 소스 신호 품질 표(노이즈 ⚠️) → 공방 관련 픽 → 주요 흐름 테마 → 주목 사건
         → 공방 즉시 착수 → 다음 주 관전 → 일간 아이디어 통합 → 주간 서술 → 상위 원문 링크.
    """
    # (builder 구현: 아래 표 형식을 그대로 사용)
    # | 일자 | 건수 | 고중요 | 평균중요도 |  (sparse=True 행은 끝에 " ⚠️ 데이터 빈약" 표기)
    # | 태그 | 등장 | 일수 | 구분(지속/버스트) |
    # | 소스 | 건수 | 평균중요도 | 노이즈후보 |
    ...
```
> 표 헤더/행 포맷은 위 주석대로 고정한다. `mode=="fallback"`이면 헤더에 `> **AI 합성 실패 / 기본 주간 리포트**` 한 줄을 넣는다(일간 fallback 배지와 동형). `sparse` 일자와 `noise_sources`는 본문 표에서 ⚠️로 시각화한다.

### 6-4. `main.py` 수정
```python
# build_parser()에 추가:
p.add_argument("--weekly", action="store_true",
               help="주간 흐름 리포트를 생성(트리거: meta 커서로 멱등). launchd 주간 잡이 호출")
p.add_argument("--week", default=None, metavar="YYYY-W##",
               help="주간보고 대상 ISO 주차를 명시(수동 강제 생성). 예: 2026-W26")

# main()에서 pipeline.run 호출 전에 분기:
if getattr(args, "weekly", False):
    from src.weekly import run_weekly
    return run_weekly(args)
```
> 기존 `--test/--dry-run/--no-discord/--force-fallback/--from/--to/--check-sources`는 변경하지 않는다. `--weekly`는 일간 분기보다 먼저 디스패치한다.

## 7. 예외 처리
- **빈 주(기사 0건)**: `aggregate_week`가 모든 신호를 빈/0으로 안전 반환, `build_weekly_report`가 "데이터 없음" 톤의 리포트를 생성하고 커서를 갱신(정상 주 경계). sparse_days에 7일 모두 표기.
- **Claude 합성 실패/키 없음/모델명 오류/JSON 파싱 실패**: `synthesize_weekly`가 `_fallback_weekly`로 흡수, mode="fallback", 파이프라인 중단 없음(일간 정책과 동형).
- **일간 .md 일부 없음**: `collect_daily_ideas`가 해당 일 skip(부분 데이터로 진행).
- **DB 쓰기 오류(set_meta)**: 예외 전파 → 종료코드 1(일간과 동일 — 커서 미갱신 시 다음 실행이 재생성).
- **`--week` 형식 오류**: `parse_week_arg`가 ValueError → 비정상 종료(사용자 입력 오류).
- **카운터가 아니라 커서인 이유(스펙 근거)**: 카운터(일간 7회==7)는 ① Mac sleep으로 일간 미실행 ② 전소스 실패로 빈 리포트일 ③ `--from` 재실행 시 일요일 정렬이 깨지고 자가치유 불가. 시간/날짜 + meta 커서는 늦게 깨어나도 같은 주(월~일)를 가리키고(자가치유), `last_weekly_iso_week`가 중복 생성을 막는다.

## 8. 완료 조건
- `python main.py --weekly --no-discord --force-fallback` 실행 시 `reports/<ISO연도>/weekly/W##.md`가 생성되고 종료코드 0.
- 같은 주에 `--weekly`(--week 없이) 재실행 시 `meta.last_weekly_iso_week` 일치로 **skip**(중복 생성 없음).
- `--week 2026-W26` 지정 시 해당 주차로 강제 생성(커서 일치여도 재생성).
- 전문에 강도 추이·태그 지속성·소스 신호품질·공방 픽·일간 아이디어 통합 표/섹션이 모두 존재.
- `aggregate_week`가 지속 줄기(day_span≥5)·단발 버스트(count≥5 & day_span≤2)·노이즈 소스(count≥5 & avg<3.0)·공방 픽(relevance≥7)을 올바르게 분류.
- 기존 일간 QA(`pytest -q tests/test_pipeline.py`) 전부 통과(회귀 없음).

## 9. 테스트 방법
```bash
cd projects/AI-Morning-Brief/05-개발
# 회귀(일간 미파손) 확인
pytest -q tests/test_pipeline.py

# 격리된 임시 DB에 주차 데이터 시드 후 강제 생성 (외부 의존 없이 fallback)
export MORNINGBRIEF_DB=$(mktemp -u).db
export MORNINGBRIEF_REPORTS_DIR=$(mktemp -d)
python -c "
import datetime, json
from src.storage import init_db, insert_article, update_analysis
c = init_db('$MORNINGBRIEF_DB')
mon = datetime.date.fromisocalendar(2026,26,1)
for i in range(7):
    d=(mon+datetime.timedelta(days=i)).isoformat()
    for j in range(3):
        u=f'u{i}-{j}'
        insert_article(c,{'url':u,'title':f'MCP agent {i}-{j}','source':'VentureBeat AI',
            'published_at':d,'collected_at':d+'T08:00:00','raw_excerpt':'x'})
        update_analysis(c,u,'요약',['mcp','agent'],9 if j==0 else 2, 8 if j==0 else 1, 1)
print('seeded')
"
python main.py --weekly --week 2026-W26 --no-discord --force-fallback ; echo exit=$?
ls "$MORNINGBRIEF_REPORTS_DIR"/2026/weekly/W26.md   # 존재
grep -q "지속" "$MORNINGBRIEF_REPORTS_DIR"/2026/weekly/W26.md && echo "지속줄기 표 OK"
# 멱등: --week 없이 재실행하면 skip 로그 (커서가 2026-W26 이므로 정시 산정 주차와 다르면 신규, 같으면 skip)
```
> 통합 자동화 테스트(`tests/test_weekly.py`)는 FEAT-10에서 작성한다. FEAT-09는 위 수동 절차로 확인한다.

## 10. 금지 사항
- Discord 다이제스트 전송·`build_weekly_message`·주간 plist·`tests/test_weekly.py`·README 운영 안내 작성 금지(전부 FEAT-10).
- 기존 일간 코드(`analyze`/`_build_prompt`/`_call_claude`/`build_report`/`save_report`/`pipeline.run`) 수정 금지 — **추가만** 한다.
- DB 스키마 변경 금지: 커서는 `meta` 테이블 key-value row(`last_weekly_iso_week`)로만 둔다(새 컬럼·새 테이블 금지).
- 월간(#12)·노이즈 자동필터 선구현 금지(경로 헬퍼 일반화까지만).
- 불필요한 라이브러리 추가 금지(표준 라이브러리 + 기존 의존성만).
- 이 이슈 범위를 벗어나는 리팩터링 금지.
