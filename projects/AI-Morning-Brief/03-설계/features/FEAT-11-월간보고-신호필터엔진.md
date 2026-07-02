<!--
3단계 설계자 산출물. AI-Morning-Brief 증분 기능 `--monthly` 1/3 (월간 흐름 리포트).
분해 사유: 월간은 주간(FEAT-09/10) 패턴을 재사용하되 #12의 7개 신뢰도/노이즈 개선이
"필터·신호 정제 레이어"라는 새 무게중심을 추가한다. 그래서 주간의 2분할이 아니라 3분할한다.
  FEAT-11 = 신호·필터 엔진 (순수 함수 모듈: 기간경계·2단계필터·소스편중정규화·노이즈분류·집계·대표선별)
  FEAT-12 = 합성·전문 생성·오케스트레이션 (신뢰도 라벨 프롬프트 + 전문 .md + run_monthly, Discord 미전송)
  FEAT-13 = Discord 월간 다이제스트 + 매월1일 스케줄러 + 통합테스트
FEAT-11은 외부 I/O·Claude 없이 pytest로 단독 검증 가능한 "월간의 뇌"다. 7개 요구 중 5개(1,3,4,5,6)의 코어 알고리즘이 여기 모인다.
-->

# FEAT-11 — 월간보고 신호·필터 엔진 (`--monthly` 코어 · #12 신뢰도/노이즈)

- 매칭 이슈: #11 (확정 시 4단계에서 번호 일치 등록)
- 작성일: 2026-07-02
- 상위 설계서: `03-설계/설계서.md` (하단 "(증분) 월간 흐름 리포트 `--monthly` 설계 — 애드덤 (#12)")
- 근거 이슈: #12 (월간 흐름 리포트 신뢰도/노이즈 필터 개선 — 7개 요구)

## 1. 목적
30일치 일간 데이터(SQLite `articles` + 일간 리포트 `.md`)에서 **월간 흐름의 근거 신호를 정제·계산하는 순수 함수 모듈** `src/monthly.py`를 만든다. 6/21 1회 실험본(`reports/2026/06/monthly-flow-...md`)이 노출한 신뢰도·노이즈 문제(#12)를 코드로 해소한다: ① 기간 경계 확정 ② 2단계 필터(관련성→적용성) ③ 소스 편중 정규화(지배 방지) ④ 대표 기사 압축 ⑤ 노이즈 유형·건수 분류. 이 FEAT는 **외부 의존(Claude/네트워크/파일쓰기) 없이 pytest로 단독 검증**되는 계산 계층이며, Claude 합성·전문 저장·오케스트레이션은 FEAT-12, 배달·스케줄은 FEAT-13이 소비한다.

## 2. 범위
### 구현할 것
- `src/monthly.py` (신규) — **순수 함수·상수만.** 파일 쓰기·Claude 호출·Discord 전송·`run_monthly` 없음(전부 FEAT-12/13).
  - 상수: `HIGH_IMPORTANCE`, `WORKSHOP_RELEVANCE`, `PERSIST_DAYS`, `BURST_MAX_DAYS`, `NOISE_MIN_COUNT`, `NOISE_AVG_IMPORTANCE`, `SOURCE_CAP_RATIO`, `TOP_LIMIT`, `FIRST_STAGE_KEYWORDS`, `FIRST_STAGE_MIN_SIGNAL`, `NOISE_TYPE_KEYWORDS`.
  - 기간: `target_month(now)`, `parse_month_arg(s)`, `month_bounds(y,m)`, `partition_by_period(articles, first, last)`.
  - 필터: `first_stage_filter(articles)` (1차 관련성), `classify_noise(rejected)` (노이즈 유형 집계).
  - 편중: `source_stats(articles)`, `cap_source_bias(articles, cap_ratio)` (지배 방지).
  - 집계: `aggregate_month(articles, first, last)` (정량 신호 + 편중·신뢰도 입력).
  - 대표/아이디어: `select_top_articles_monthly(articles, limit)`, `collect_monthly_ideas(reports_dir, first, last)`.
- `src/weekly.py`에서 **공통 헬퍼를 import 재사용**(중복 구현 금지): `_day_of`, `_norm_title`, `_extract_section_bullets`, `collect_daily_ideas`. (weekly는 이미 존재)
- `tests/test_monthly.py`에 **이 FEAT의 순수 함수 단위 테스트만** 추가(엔드투엔드·Discord·스케줄러는 FEAT-13).

### 구현하지 않을 것
- `run_monthly` 오케스트레이션, `analyzer.synthesize_monthly`, `reporter.build_monthly_report`, `main.py --monthly` 분기 → **전부 FEAT-12**.
- `notifier.build_monthly_message`, 월간 plist, README, 엔드투엔드 통합테스트 → **전부 FEAT-13**.
- 기존 주간/일간 로직(`aggregate_week`/`run_weekly`/`analyze`/`pipeline.run`) 수정 금지 — **추가·import만**.
- DB 스키마 변경 금지(커서는 FEAT-12가 `meta.last_monthly_ym`로 둔다 — 이 FEAT는 DB에 쓰지 않음).

## 3. 입력 / 출력
### 입력
- `partition_by_period`/`aggregate_month` 등은 **기사 dict 리스트**(storage.get_articles_by_range 반환 형태: `url,title,source,published_at,collected_at,summary,tags(list),importance,relevance,analyzed`)를 받는다.
- `collect_monthly_ideas`는 `reports_dir`와 월 경계 date 2개를 받아 일간 `.md`를 읽는다.
- `now`(datetime), `--month` 문자열("YYYY-MM").
### 출력
- 각 함수는 **자료구조(dict/list/tuple)만 반환**한다. 파일·DB·네트워크 부작용 없음.
- `aggregate_month` 반환 `signals` dict(6장 스키마) — FEAT-12가 소비.

## 4. 동작 흐름 (이 FEAT가 제공하는 계산 파이프라인)
1. **대상 월 산정**: `target_month(now)` = now 기준 **직전 달**(now.month의 한 달 전). `--month`면 `parse_month_arg`.
2. **월 경계**: `month_bounds(y,m)` → (1일, 말일) date. (`calendar.monthrange`로 말일 계산)
3. **기간 경계 분리(#12-1)**: `partition_by_period(articles, first, last)` → `(in_period, out_of_period)`. published_at이 월 범위면 채택, 범위 밖이면 out_of_period, published_at NULL/파싱불가면 collected_at이 월내이면 채택(폴백).
4. **1차 필터(#12-3)**: `first_stage_filter(in_period)` → `(passed, rejected)`. AI/개발/에이전트 키워드 화이트리스트 또는 최소 신호(importance/relevance) 통과분만 passed.
5. **노이즈 분류(#12-7)**: `classify_noise(rejected)` → 유형별 건수 dict(정치사회/인프라투자/비AI소비자/단순릴리스/홍보성/반도체지정학/윤리논평/기타).
6. **편중 정규화(#12-5,6)**: `cap_source_bias(passed, SOURCE_CAP_RATIO)` → 소스당 상한 적용된 대표 후보 + `source_stats`(소스별 건수·점유율·평균중요도·노이즈여부).
7. **집계**: `aggregate_month(passed, first, last)` → 강도추이·태그(정규화 count)·소스편중·노이즈소스·공방픽·지속줄기·버스트·신뢰도입력. 내부에서 5·6 결과를 담는다.
8. **대표 압축(#12-4)**: `select_top_articles_monthly(passed, TOP_LIMIT=40)` — 제목 정규화 중복제거 + 소스 상한 + importance·relevance·교차출처수 정렬 상위 40.
9. **아이디어**: `collect_monthly_ideas` — 월 일자별 일간 `.md`의 아이디어 섹션 불릿을 `weekly.collect_daily_ideas` 로직으로 통합.

## 5. 수정 예상 파일
- `05-개발/src/monthly.py` (신규 — 순수 함수·상수)
- `05-개발/tests/test_monthly.py` (신규 — 이 FEAT의 순수 함수 단위 테스트 부분만. FEAT-13이 같은 파일에 엔드투엔드 추가)

## 6. 데이터 구조 / 함수 / 클래스

### 6-1. `src/monthly.py` — 상수 + 기간
```python
"""월간 흐름 리포트 신호·필터 엔진 (FEAT-11, #12). 순수 함수만 — I/O·Claude 없음."""
import calendar, datetime, re
from collections import defaultdict
from datetime import timedelta

# 주간과 공유하는 헬퍼는 재구현하지 않고 import 재사용 (최소 변경 원칙)
from src.weekly import _day_of, _norm_title, collect_daily_ideas as _collect_daily_ideas

# ── 임계값(월간 스케일). 스케일 변경 시 이 상수만 조정 (집계 로직 불변) ──
HIGH_IMPORTANCE = 8         # 고중요 기준 (importance 0~10 스케일)
WORKSHOP_RELEVANCE = 7      # 공방 픽 기준
PERSIST_DAYS = 8           # 월간 지속 줄기: 서로 다른 8일 이상 등장 (주간은 5)
BURST_MAX_DAYS = 2         # 단발 버스트 상한
NOISE_MIN_COUNT = 15       # 노이즈 소스 후보 최소 볼륨 (월 스케일, 주간 5보다 상향)
NOISE_AVG_IMPORTANCE = 3.0 # 노이즈 소스 평균중요도 상한(미만)
SOURCE_CAP_RATIO = 0.15    # 대표 풀에서 한 소스가 차지할 상한 비율 (#12-6 지배 방지)
TOP_LIMIT = 40             # Claude 합성에 투입할 대표 기사 수 (#12-4 압축)
FIRST_STAGE_MIN_SIGNAL = 1 # 1차 통과 최소 신호: importance 또는 relevance 가 이 값 이상

# 1차 필터(#12-3) 관련성 화이트리스트 — 제목/태그/요약에 하나라도 있으면 AI/개발/에이전트 관련
FIRST_STAGE_KEYWORDS = (
    "ai", "llm", "gpt", "claude", "anthropic", "openai", "gemini", "google ai",
    "mcp", "agent", "에이전트", "codex", "copilot", "cursor", "rag", "vibe",
    "바이브", "코딩", "코드", "developer", "sdk", "api", "model", "모델",
    "automation", "자동화", "prompt", "프롬프트", "eval", "fine-tun", "инференс",
)
# 노이즈 유형 분류(#12-7) — 1차 탈락 기사를 유형별 건수로 집계 (best-effort 규칙)
NOISE_TYPE_KEYWORDS = {
    "정치·사회": ("election", "policy", "regulat", "정치", "선거", "규제", "정책", "safety act"),
    "인프라투자·부동산": ("data center", "데이터센터", "stargate", "investment", "투자", "billion", "campus"),
    "비AI소비자": ("shopping", "dating", "wellness", "쇼핑", "데이팅", "웰니스", "recipe"),
    "단순릴리스노트": ("alpha", "nightly", "changelog only", "no changes", "버전 태그"),
    "홍보성·파트너십": ("partnership", "partner with", "제휴", "도입 사례", "case study", "announces support"),
    "반도체·지정학": ("chip", "asml", "export control", "반도체", "수출 통제", "geopolit"),
    "AI윤리·논평": ("opinion", "essay", "칼럼", "논평", "philosophy", "친구가 아니다"),
}


def target_month(now: datetime.datetime) -> tuple[int, int]:
    """now 기준 '직전 달'(막 끝난 달)을 (year, month)로. 매월 1일 실행 → 전월.

    그 달 어느 날에 실행해도 now.month의 한 달 전을 가리켜, 지연 실행(1~며칠)에도
    같은 대상을 반환한다(자가치유). 커서 last_monthly_ym 가 중복 생성을 막는다.
    """
    y, m = now.year, now.month
    return (y - 1, 12) if m == 1 else (y, m - 1)


def parse_month_arg(s: str) -> tuple[int, int]:
    """'2026-06' → (2026, 6). 형식 오류 시 ValueError."""
    m = re.fullmatch(r"(\d{4})-(\d{1,2})", s.strip())
    if not m:
        raise ValueError(f"--month 형식은 YYYY-MM 이어야 함: {s}")
    y, mo = int(m.group(1)), int(m.group(2))
    if not 1 <= mo <= 12:
        raise ValueError(f"월 범위 오류(1~12): {s}")
    return y, mo


def month_bounds(y: int, m: int) -> tuple[datetime.date, datetime.date]:
    """달력월의 1일·말일 date. (calendar.monthrange 로 말일 계산)"""
    last_day = calendar.monthrange(y, m)[1]
    return datetime.date(y, m, 1), datetime.date(y, m, last_day)


def month_key(y: int, m: int) -> str:
    """커서/파일명용. (커서: 'YYYY-MM', 파일 bucket name: 'M##')"""
    return f"{y:04d}-{m:02d}"
```

### 6-2. 기간 경계 분리 (#12-1)
```python
def _pub_or_collect_day(a: dict) -> str | None:
    """경계 판정용 대표 일자: published_at 우선(앞 10자), 없으면 collected_at 폴백."""
    for key in ("published_at", "collected_at"):
        v = (a.get(key) or "")[:10]
        if re.fullmatch(r"\d{4}-\d{2}-\d{2}", v):
            return v
    return None


def partition_by_period(articles: list, first: datetime.date, last: datetime.date) -> tuple[list, list]:
    """월 경계로 (in_period, out_of_period) 분리. #12-1.

    - published_at 이 [first,last] 이면 채택.
    - published_at 범위 밖이면 out_of_period('범위 외 참고 항목').
    - published_at NULL/파싱불가 → collected_at 이 월내이면 채택(폴백), 아니면 out.
    """
    lo, hi = first.isoformat(), last.isoformat()
    in_p, out_p = [], []
    for a in articles:
        d = _pub_or_collect_day(a)
        (in_p if (d is not None and lo <= d <= hi) else out_p).append(a)
    return in_p, out_p
```

### 6-3. 2단계 필터 1차 + 노이즈 분류 (#12-3, #12-7)
```python
def _text_blob(a: dict) -> str:
    return " ".join([
        str(a.get("title", "")), str(a.get("summary", "")),
        " ".join(a.get("tags") or []),
    ]).lower()


def first_stage_filter(articles: list) -> tuple[list, list]:
    """1차: AI/개발/에이전트 관련성. #12-3.

    통과 조건(OR): ① 키워드 화이트리스트 매칭 ② importance≥MIN 또는 relevance≥MIN.
    반환 (passed, rejected). 2차(공방 적용성)는 코드가 아니라 FEAT-12 Claude 합성이 랭킹으로 수행.
    """
    passed, rejected = [], []
    for a in articles:
        blob = _text_blob(a)
        kw = any(k in blob for k in FIRST_STAGE_KEYWORDS)
        sig = (int(a.get("importance", 0) or 0) >= FIRST_STAGE_MIN_SIGNAL
               or int(a.get("relevance", 0) or 0) >= FIRST_STAGE_MIN_SIGNAL)
        (passed if (kw or sig) else rejected).append(a)
    return passed, rejected


def classify_noise(rejected: list) -> dict:
    """1차 탈락 기사를 노이즈 유형별 건수로 집계. #12-7 상단 명시 재료.

    반환: {"정치·사회": n, ..., "기타": m, "_total": 총건수}. 매칭 안 되면 '기타'.
    """
    counts = defaultdict(int)
    for a in rejected:
        blob = _text_blob(a)
        hit = next((label for label, kws in NOISE_TYPE_KEYWORDS.items()
                    if any(k in blob for k in kws)), "기타")
        counts[hit] += 1
    counts["_total"] = len(rejected)
    return dict(counts)
```

### 6-4. 소스 편중 표시 + 지배 방지 (#12-5, #12-6)
```python
def source_stats(articles: list) -> list:
    """소스별 (건수·점유율·평균중요도·노이즈여부). #12-5 편중도 표시.
    반환은 count desc 정렬 리스트. 점유율은 0~1 실수(리포트에서 %로 렌더).
    """
    total = len(articles) or 1
    cnt = defaultdict(int)
    imp = defaultdict(list)
    for a in articles:
        s = a.get("source", "?")
        cnt[s] += 1
        imp[s].append(int(a.get("importance", 0) or 0))
    out = []
    for s in cnt:
        avg = round(sum(imp[s]) / len(imp[s]), 2) if imp[s] else 0.0
        out.append({
            "source": s, "count": cnt[s], "share": round(cnt[s] / total, 3),
            "avg_importance": avg,
            "noise_candidate": cnt[s] >= NOISE_MIN_COUNT and avg < NOISE_AVG_IMPORTANCE,
        })
    out.sort(key=lambda x: x["count"], reverse=True)
    return out


def cap_source_bias(articles: list, cap_ratio: float = SOURCE_CAP_RATIO) -> list:
    """소스 지배 방지: 한 소스가 대표 후보의 상한(cap)을 넘으면 초과분을 후순위로 제외. #12-6.

    cap = max(3, round(len(articles) * cap_ratio)). 각 소스 내부는 importance desc 로
    상위 cap 건만 남긴다(초과분은 대표 풀에서 제외 — 전문 부록엔 FEAT-12가 별도 표기).
    """
    cap = max(3, round(len(articles) * cap_ratio))
    by_src = defaultdict(list)
    for a in articles:
        by_src[a.get("source", "?")].append(a)
    kept = []
    for s, items in by_src.items():
        items.sort(key=lambda x: int(x.get("importance", 0) or 0), reverse=True)
        kept.extend(items[:cap])
    return kept
```

### 6-5. 집계 (정량 신호 + 편중·신뢰도 입력)
```python
def _cross_source_count(articles: list) -> dict:
    """제목 정규화 키 → 그 사건을 다룬 '고유 소스 수'(신뢰도 라벨 근거). #12-2 입력."""
    by_title = defaultdict(set)
    for a in articles:
        by_title[_norm_title(a.get("title"))].add(a.get("source", "?"))
    return {k: len(v) for k, v in by_title.items()}


def aggregate_month(articles: list, first: datetime.date, last: datetime.date) -> dict:
    """월간 정량 신호. 주간 aggregate_week 를 월 스케일로 확장 + 편중 정규화 태그.

    반환 signals(dict):
      total, span_days, daily_intensity[], peak_day, sparse_days[],
      tags[], persistent_stems[], bursts[],       # 태그 count = 고유 (source,day) 조합 (편중 정규화)
      sources[](=source_stats), noise_sources[],  # #12-5,6
      workshop_picks[], cross_source[]             # #12-2 신뢰도 입력
    """
    span = (last - first).days + 1
    days = [(first + timedelta(days=i)).isoformat() for i in range(span)]
    by_day = defaultdict(list)
    for a in articles:
        by_day[_day_of(a.get("collected_at"))].append(a)
    daily = []
    for d in days:
        items = by_day.get(d, [])
        imps = [int(x.get("importance", 0) or 0) for x in items]
        daily.append({"date": d, "count": len(items),
                      "high_count": sum(1 for v in imps if v >= HIGH_IMPORTANCE),
                      "avg_importance": round(sum(imps) / len(imps), 2) if imps else 0.0,
                      "sparse": len(items) <= 1})
    peak = max(daily, key=lambda r: r["count"]) if daily else None
    sparse_days = [r["date"] for r in daily if r["sparse"]]

    # 태그: count = 고유 (source, day) 조합 수 → 한 소스가 하루 도배해도 1로 정규화 (#12-6)
    tag_pairs = defaultdict(set)   # tag -> {(source, day)}
    tag_days = defaultdict(set)    # tag -> {day}
    for a in articles:
        d = _day_of(a.get("collected_at"))
        s = a.get("source", "?")
        for t in (a.get("tags") or []):
            k = str(t).lower().strip()
            if k:
                tag_pairs[k].add((s, d))
                tag_days[k].add(d)
    tags = []
    for k in tag_pairs:
        span_days = len(tag_days[k])
        cnt = len(tag_pairs[k])
        tags.append({"tag": k, "count": cnt, "day_span": span_days,
                     "persistent": span_days >= PERSIST_DAYS,
                     "burst": cnt >= NOISE_MIN_COUNT // 3 and span_days <= BURST_MAX_DAYS})
    tags.sort(key=lambda x: (x["day_span"], x["count"]), reverse=True)

    srcs = source_stats(articles)
    picks = sorted([a for a in articles if int(a.get("relevance", 0) or 0) >= WORKSHOP_RELEVANCE],
                   key=lambda x: (int(x.get("relevance", 0) or 0), int(x.get("importance", 0) or 0)),
                   reverse=True)
    return {
        "total": len(articles), "span_days": span, "daily_intensity": daily,
        "peak_day": peak, "sparse_days": sparse_days,
        "tags": tags,
        "persistent_stems": [t for t in tags if t["persistent"]],
        "bursts": [t for t in tags if t["burst"]],
        "sources": srcs,
        "noise_sources": [s for s in srcs if s["noise_candidate"]],
        "workshop_picks": picks,
        "cross_source": _cross_source_count(articles),
    }
```

### 6-6. 대표 압축 + 아이디어 (#12-4)
```python
def select_top_articles_monthly(articles: list, limit: int = TOP_LIMIT) -> list:
    """제목 정규화 중복제거 → 소스 상한(cap_source_bias) → 정렬 상위 limit. #12-4.

    정렬키: importance desc, relevance desc, 교차출처수 desc(신뢰 높은 사건 우선).
    각 반환 항목에 'cross_source_count' 키를 부가(FEAT-12 신뢰도 라벨 근거).
    """
    xsrc = _cross_source_count(articles)
    best = {}
    for a in articles:
        k = _norm_title(a.get("title"))
        cur = best.get(k)
        if cur is None or int(a.get("importance", 0) or 0) > int(cur.get("importance", 0) or 0):
            best[k] = a
    capped = cap_source_bias(list(best.values()), SOURCE_CAP_RATIO)
    for a in capped:
        a2 = a  # dict 참조; 부가 필드만 세팅
        a2["cross_source_count"] = xsrc.get(_norm_title(a.get("title")), 1)
    capped.sort(key=lambda x: (int(x.get("importance", 0) or 0),
                               int(x.get("relevance", 0) or 0),
                               int(x.get("cross_source_count", 1))), reverse=True)
    return capped[:limit]


def collect_monthly_ideas(reports_dir: str, first: datetime.date, last: datetime.date) -> list:
    """월 일자별 일간 .md의 '상상공방 적용 아이디어'를 통합. weekly.collect_daily_ideas 재사용.

    반환: [{"text": str, "days": int}, ...] 반복일수 desc. (파일 없음/섹션 없음은 skip)
    """
    return _collect_daily_ideas(reports_dir, first, last)
```
> ⚠ `_collect_daily_ideas`(=weekly.collect_daily_ideas)는 이미 월 임의 구간(first~last)을 순회하도록 구현돼 있다(일수 = `(sunday-monday).days+1` 일반식). 월 경계를 그대로 넘기면 재사용된다. 새로 구현 금지.

## 7. 예외 처리
- **빈 월(기사 0건)**: 모든 함수가 빈 리스트/0을 안전 반환. `aggregate_month`는 `daily_intensity`를 해당 월의 전 일자(count=0 포함)로 채우므로 `daily`가 비지 않고, `peak = max(daily, key=count)` 연산은 **첫 번째 일자를 peak_day로 선택하며 그 count=0**이 된다(peak_day는 None이 되지 않는다). sparse_days에도 전 일자가 표기된다. (달력월이 아닌 빈 리스트를 직접 넘기는 경계 케이스에서만 `daily`가 비어 peak_day=None.)
- **published_at·collected_at 모두 파싱불가**: `partition_by_period`가 out_of_period로 분류(집계 제외). 유실 아님(원 데이터 DB 보존).
- **tags가 str/None**: `(a.get("tags") or [])` 가드. storage는 list로 역직렬화하므로 정상 경로는 list.
- **소스 1개 지배(전부 동일 소스)**: `cap_source_bias`가 cap 이내로 잘라 대표 다양성 확보. 태그도 (source,day) 정규화로 완화.
- **`--month` 형식 오류**: `parse_month_arg` ValueError → FEAT-12 run_monthly에서 비정상 종료(사용자 입력 오류).
- 이 FEAT는 DB/파일에 쓰지 않으므로 부작용 실패 없음.

## 8. 완료 조건
- `src/monthly.py`가 6장 함수·상수를 모두 제공하고 `python -c "import src.monthly"` 성공.
- `partition_by_period`가 published_at 범위 밖/NULL 기사를 규칙대로 in/out 분리.
- `first_stage_filter`가 무관 기사를 rejected로, `classify_noise`가 유형별 건수(+_total)로 집계.
- `cap_source_bias`가 소스당 `max(3, round(N*0.15))` 상한을 적용, `aggregate_month.tags[].count`가 (source,day) 고유조합 수.
- `select_top_articles_monthly`가 상한·정렬 적용 상위 40 반환(각 항목 cross_source_count 포함).
- `pytest -q tests/test_monthly.py` 중 **이 FEAT 순수 함수 테스트 전부 통과**, 기존 `tests/test_weekly.py`·`tests/test_pipeline.py` 회귀 없음.

## 9. 테스트 방법
```bash
cd projects/AI-Morning-Brief/05-개발
# 회귀(주간·일간 미파손)
pytest -q tests/test_weekly.py tests/test_pipeline.py
# 이 FEAT 순수 함수 검증 (외부 의존 없음)
pytest -q tests/test_monthly.py
python -c "
import datetime as dt
from src.monthly import (target_month, parse_month_arg, month_bounds,
    partition_by_period, first_stage_filter, classify_noise, cap_source_bias,
    aggregate_month, select_top_articles_monthly)
first, last = month_bounds(2026, 6)
assert (first.day, last.day) == (1, 30)
assert target_month(dt.datetime(2026,7,1,4,50)) == (2026, 6)
assert parse_month_arg('2026-06') == (2026, 6)
arts=[{'title':'MCP agent release','source':'A','published_at':'2026-06-10',
       'collected_at':'2026-06-10T08:00:00','tags':['mcp','agent'],'importance':9,'relevance':8},
      {'title':'city council election','source':'B','published_at':'2026-06-11',
       'collected_at':'2026-06-11T08:00:00','tags':[],'importance':0,'relevance':0},
      {'title':'old news','source':'A','published_at':'2026-04-01',
       'collected_at':'2026-06-01T08:00:00','tags':['ai'],'importance':2,'relevance':1}]
inp, outp = partition_by_period(arts, first, last)
assert len(outp) == 1                       # 4월 기사 = 범위 외
passed, rej = first_stage_filter(inp)
assert any('election' in a['title'] for a in rej)   # 정치 탈락
print('noise', classify_noise(rej))
print('agg total', aggregate_month(passed, first, last)['total'])
print('OK')
"
```
> Claude/네트워크/Discord 없이 수 초 내 완료. 단위 테스트만으로 #12의 1·3·4·5·6 로직이 검증된다.

## 10. 금지 사항
- `run_monthly`·`synthesize_monthly`·`build_monthly_report`·`build_monthly_message`·`main --monthly`·plist·README 작성 금지(전부 FEAT-12/13).
- 파일 쓰기·DB 쓰기·Claude 호출·네트워크 접근 금지 — 이 FEAT는 **순수 계산 계층**이다.
- 주간/일간 코드(`weekly.aggregate_week`/`run_weekly`/`analyze`/`pipeline.run`) 수정 금지 — 공통 헬퍼는 **import 재사용**만.
- 공통 헬퍼(`_day_of`/`_norm_title`/`collect_daily_ideas`) 재구현 금지(weekly에서 import).
- 불필요한 라이브러리 추가 금지(표준 라이브러리 + 기존 의존성만).
- 이 이슈 범위를 벗어나는 리팩터링 금지.

> ⚠️ **기존 코드 보호 원칙**: 기존 weekly/daily 코드의 **동작 변경은 금지**한다. 공통 함수 **추출**은 허용하지만(= 기존 코드에서 로직을 그대로 빼내 재사용만, 재작성·시그니처 변경 불가), 기존 테스트가 **하나라도 실패하면 리팩토링을 되돌린다**.
