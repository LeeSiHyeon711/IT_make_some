<!--
3단계 설계자 산출물. AI-Morning-Brief 증분 기능 `--weekly` 2/2.
분해 사유(FEAT-09 서두와 동일): FEAT-09=엔진(전문 .md 생성)까지, FEAT-10=배달·예약·검증.
FEAT-10은 FEAT-09가 완료되어 reports/<연도>/weekly/W##.md 와 run_weekly가 존재한다는 전제 위에서
① Discord 9블록 다이제스트 ② run_weekly에 전송 hook 연결 ③ 일요일 launchd 주간 plist
④ 통합테스트 tests/test_weekly.py ⑤ README 주간 운영 안내 를 추가한다.
-->

# FEAT-10 — 주간보고 Discord 다이제스트 + 일요일 스케줄러 + 통합테스트

- 매칭 이슈: #10 (확정 시 4단계에서 번호 일치 등록)
- 작성일: 2026-06-28
- 상위 설계서: `03-설계/설계서.md` (하단 "(증분) 주간보고 `--weekly` 설계" 섹션)
- 의존: **FEAT-09**(`src/weekly.run_weekly`, `aggregate_week` 신호 구조, `reports/<연도>/weekly/W##.md`).

## 1. 목적
FEAT-09가 만든 주간 전문(.md)과 정량 신호를 **일간과 동일한 Discord 웹훅**으로 2000자 이내 9블록 다이제스트로 전송하고, **일요일 04:40 launchd 주간 잡**으로 무인 실행을 예약하며, 트리거 멱등·ISO 주차 산정·빈 주·전문 경로를 **통합테스트로 자동 검증**한다. 이 FEAT 완료로 `--weekly` 증분이 "배달·예약·검증되는 자동화"로 마감된다.

## 2. 범위
### 구현할 것
- `src/notifier.py` (수정): `build_weekly_message(...)` 추가 — 9블록·2000자 이내·plain content(절삭 아님, 의도적 요약). 기존 `send_discord` 재사용.
- `src/weekly.py` (수정): `run_weekly`의 "(FEAT-10 hook)" 자리에 Discord 전송 블록 연결(`--no-discord`면 생략).
- `scripts/com.itsangsang.morningbrief.weekly.plist` (신규): 일요일 04:40, `main.py --weekly` 호출.
- `tests/test_weekly.py` (신규): 트리거 멱등·ISO 주차 산정·빈 주·전문 경로·신호 집계 검증(tmp_path 격리, env override, hermetic).
- `05-개발/README.md` (수정): 주간 plist 등록 + `~/AI-Morning-Brief-run` rsync 운영 절차 추가.
### 구현하지 않을 것
- 주간 집계·합성·전문 빌더 로직(FEAT-09). 여기서는 그 산출물을 **소비**만 한다.
- 임베드/분할 전송(FEAT-06 "분할 없음" 정책 유지 — plain content 단일 POST).
- 일간 plist(`com.itsangsang.morningbrief.plist`)·일간 테스트(`tests/test_pipeline.py`) 수정.
- 월간(#12)·노이즈 자동필터.

## 3. 입력 / 출력
### 입력
- FEAT-09 산출: `run_weekly` 내부의 `week_key`, `signals`(aggregate_week 반환), `synthesis`(synthesize_weekly 반환의 synthesis dict), `report_path`.
- `load_secrets()["DISCORD_WEBHOOK_URL"]`.
### 출력
- Discord 채널에 9블록 다이제스트 1건(plain content, ≤2000자).
- `~/Library/LaunchAgents/com.itsangsang.morningbrief.weekly.plist`(운영자가 등록).
- `pytest -q tests/test_weekly.py` 통과.

## 4. 동작 흐름
1. **다이제스트 빌더**: `build_weekly_message(week_key, monday, sunday, signals, synthesis, report_path)`가 9블록 문자열을 조립한다(6장 포맷). 표는 넣지 않고(태그/소스는 인라인 한 줄 압축), 2000자 이내가 되도록 각 블록 항목 수를 제한한다(절삭 마커 없이 완결).
2. **전송 hook 연결**: `run_weekly`의 `save_bucket_report` 직후·`set_meta` 직전에 추가:
   ```python
   if not getattr(args, "no_discord", False):
       from src.notifier import build_weekly_message
       msg = build_weekly_message(week_key, monday, sunday, signals,
                                  result["synthesis"], path)
       send_discord(secrets.get("DISCORD_WEBHOOK_URL"), msg)
   ```
   (`send_discord`는 weekly.py 상단 import에 추가. 전송 실패는 기존 정책대로 로그만 — 파이프라인 지속.)
3. **launchd 주간 잡**: 일요일(Weekday 0) 04:40에 `python main.py --weekly` 실행. 트리거 멱등은 FEAT-09 커서가 보장하므로 plist는 단순 호출만 한다.
4. **통합테스트**: tmp_path + env override로 격리, DB에 특정 ISO 주차 데이터를 시드하고 `--weekly --week`/정시 산정·멱등·빈 주·경로·신호를 검증(`--force-fallback`로 hermetic).
5. **README**: 주간 plist 등록 + `~/AI-Morning-Brief-run` 동기화 운영 절차 추가.

## 5. 수정 예상 파일
- `05-개발/src/notifier.py` (수정 — `build_weekly_message` 추가, 기존 함수 미변경)
- `05-개발/src/weekly.py` (수정 — Discord hook 연결 + `send_discord` import)
- `05-개발/scripts/com.itsangsang.morningbrief.weekly.plist` (신규)
- `05-개발/tests/test_weekly.py` (신규)
- `05-개발/README.md` (수정 — 주간 운영 섹션 추가)

## 6. 데이터 구조 / 함수 / 클래스

### 6-1. `src/notifier.py` — `build_weekly_message`
```python
def build_weekly_message(week_key, monday, sunday, signals, synthesis,
                         report_path, char_limit=2000) -> str:
    """주간 다이제스트(9블록, plain content, char_limit 이내 완결). 절삭이 아니라 의도적 요약.

    블록 순서(스펙 E):
      1) 📅 주차 + 한 줄 요약(synthesis['one_line_summary'])
      2) 📊 강도: 총 건수 · 피크일 · ⚠️ 단절일(sparse_days)
      3) 🌳 지속 줄기 태그 Top6  ("태그 N회·M일" 인라인)
      4) ⚠️ 노이즈 소스 1줄(noise_sources Top1: "소스 N건·평균 X")
      5) 🧵 주요 흐름 테마 제목 4줄(synthesis['flow_themes'][:4])
      6) ⭐ 주목 사건 Top3(synthesis['notable_events'][:3])
      7) 🎯 공방 즉시 착수 액션 Top3(synthesis['workshop_actions'][:3])
      8) 🔭 다음 주 관전 포인트(synthesis['next_week_watch'])
      9) 📄 전체 리포트 경로(report_path)
    """
    di = signals["daily_intensity"]
    peak = signals.get("peak_day") or {"date": "-", "count": 0}
    sparse = signals.get("sparse_days") or []
    stems = signals["persistent_stems"][:6]
    noise = signals.get("noise_sources") or []

    L = [f"📅 **AI Morning Brief 주간 — {week_key} ({monday.month}/{monday.day}~{sunday.month}/{sunday.day})**",
         (synthesis.get("one_line_summary") or "").strip()]
    L.append(f"\n📊 강도: 총 {signals['total']}건 · 피크 {peak['date'][5:]} {peak['count']}건"
             + (f" · ⚠️ 단절일 {len(sparse)}일" if sparse else ""))
    if stems:
        L.append("🌳 지속 줄기: " + " · ".join(f"{t['tag']} {t['count']}회·{t['day_span']}일" for t in stems))
    if noise:
        n = noise[0]
        L.append(f"⚠️ 노이즈 소스: {n['source']} {n['count']}건·평균 {n['avg_importance']}")
    if synthesis.get("flow_themes"):
        L.append("🧵 주요 흐름:\n" + "\n".join(f"• {x}" for x in synthesis["flow_themes"][:4]))
    if synthesis.get("notable_events"):
        L.append("⭐ 주목 사건:\n" + "\n".join(f"{i+1}. {x}" for i, x in enumerate(synthesis["notable_events"][:3])))
    if synthesis.get("workshop_actions"):
        L.append("🎯 공방 즉시 착수:\n" + "\n".join(f"{i+1}. {x}" for i, x in enumerate(synthesis["workshop_actions"][:3])))
    if synthesis.get("next_week_watch"):
        L.append("🔭 다음 주 관전: " + " / ".join(synthesis["next_week_watch"][:3]))
    L.append(f"📄 전체 리포트: {report_path}")

    msg = "\n".join(s for s in L if s)
    if len(msg) > char_limit:               # 안전망(설계상 9블록은 2000자 내 완결)
        msg = msg[:char_limit - 1] + "…"
    return msg
```

### 6-2. 실제 렌더 예시 (W26 mock — builder 포맷 기준)
```
📅 **AI Morning Brief 주간 — 2026-W26 (6/22~6/28)**
이번 주 핵심: 에이전트 평가·MCP 생태계가 '지속 줄기'로 굳어지고, 모델 릴리스 노이즈는 잦아듦.

📊 강도: 총 142건 · 피크 6-24 31건 · ⚠️ 단절일 1일
🌳 지속 줄기: mcp 28회·6일 · agent 24회·6일 · claude 19회·5일 · openai 15회·5일 · eval 11회·5일 · rag 9회·5일
⚠️ 노이즈 소스: VentureBeat AI 38건·평균 2.4
🧵 주요 흐름:
• MCP 서버 표준화가 IDE·CI로 확산
• 에이전트 신뢰성 평가(eval) 프레임워크 경쟁
• 코드 리뷰 자동화 실전 도입 사례 증가
• 온디바이스 소형모델 추론 비용 급락
⭐ 주목 사건:
1. Anthropic MCP 레지스트리 공개
2. OpenAI 에이전트 SDK 안정화 릴리스
3. GitHub Actions용 AI 리뷰 봇 GA
🎯 공방 즉시 착수:
1. MCP 레지스트리로 사내 도구 연결 PoC
2. 주간보고 파이프라인에 eval 지표 추가
3. PR 자동 리뷰봇 시범 도입
🔭 다음 주 관전: MCP 레지스트리 생태계 반응 / 에이전트 eval 표준화
📄 전체 리포트: reports/2026/weekly/W26.md
```
(위 예시 ≈ 700자. 9블록 모두 채워도 2000자 한도 내 완결.)

### 6-3. `scripts/com.itsangsang.morningbrief.weekly.plist`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>com.itsangsang.morningbrief.weekly</string>
  <!-- ProgramArguments / WorkingDirectory 의 /ABS/PATH 는 README 절차대로 절대경로 치환. -->
  <key>ProgramArguments</key>
  <array>
    <string>/Library/Frameworks/Python.framework/Versions/3.14/bin/python3</string>
    <string>/ABS/PATH/TO/projects/AI-Morning-Brief/05-개발/main.py</string>
    <string>--weekly</string>
  </array>
  <key>WorkingDirectory</key>
  <string>/ABS/PATH/TO/projects/AI-Morning-Brief/05-개발</string>
  <!-- 일요일(Weekday 0) 04:40 -->
  <key>StartCalendarInterval</key>
  <dict>
    <key>Weekday</key><integer>0</integer>
    <key>Hour</key><integer>4</integer>
    <key>Minute</key><integer>40</integer>
  </dict>
  <key>StandardOutPath</key><string>/tmp/morningbrief.weekly.out.log</string>
  <key>StandardErrorPath</key><string>/tmp/morningbrief.weekly.err.log</string>
  <key>RunAtLoad</key><false/>
  <key>KeepAlive</key><false/>
</dict>
</plist>
```
> **04:30 일간과의 경합**: 일간(04:30)이 04:40에도 실행 중일 수 있으나, 주간 잡은 `articles`를 **읽기만** 하고 자체 커넥션을 열며 sqlite는 동시 읽기를 허용하므로 안전하다. 주간 집계의 핵심 데이터(직전 토요일까지)는 04:30 일간이 이미 저장한 상태다. 경합이 우려되면 README에서 Minute를 50으로 늘리는 옵션을 안내한다(잡 자체는 멱등이라 중복 실행돼도 커서가 막는다). **자가치유**: Mac sleep으로 일요일을 놓쳐도 wake 시 launchd가 보완 실행하고, FEAT-09의 `target_iso_week(now-2일)`이 같은 주를 가리켜 같은 주간보고가 생성된다(커서가 중복 방지).

### 6-4. `tests/test_weekly.py` (격리·hermetic, 기존 test_pipeline.py 패턴)
```python
"""주간보고 통합 테스트 (FEAT-10). tmp_path 격리 + env override + --force-fallback hermetic."""
import datetime, os, subprocess, sys, sqlite3
import pytest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)
from src.weekly import target_iso_week, parse_week_arg, iso_week_bounds, aggregate_week, select_top_articles  # noqa: E402
from src.storage import init_db, insert_article, update_analysis  # noqa: E402


@pytest.fixture(autouse=True)
def env(tmp_path, monkeypatch):
    monkeypatch.setenv("MORNINGBRIEF_DB", str(tmp_path / "wk.db"))
    monkeypatch.setenv("MORNINGBRIEF_RAW_DIR", str(tmp_path / "raw"))
    monkeypatch.setenv("MORNINGBRIEF_REPORTS_DIR", str(tmp_path / "reports"))
    yield


def _seed(db, iso_year=2026, iso_week=26):
    """W26 7일에 걸쳐 기사 시드 — mcp/agent는 6일 지속, 'VentureBeat AI'는 고볼륨·저중요."""
    c = init_db(db)
    mon = datetime.date.fromisocalendar(iso_year, iso_week, 1)
    for i in range(7):
        d = (mon + datetime.timedelta(days=i)).isoformat()
        for j in range(3):
            u = f"u{i}-{j}"
            insert_article(c, {"url": u, "title": f"MCP agent {i}-{j}", "source": "VentureBeat AI",
                               "published_at": d, "collected_at": d + "T08:00:00", "raw_excerpt": "x"})
            update_analysis(c, u, "요약", ["mcp", "agent"], 9 if j == 0 else 2, 8 if j == 0 else 1, 1)
    return c


def _run_weekly(*flags):
    return subprocess.run([sys.executable, "main.py", "--weekly", "--no-discord", "--force-fallback", *flags],
                          cwd=ROOT, capture_output=True, text=True, env=os.environ.copy())


# --- 순수 함수 ---
def test_iso_week_on_time_and_late():
    # 일요일 정시(04:40)와 +1/+2일 지연 모두 같은 주(W26)를 가리킨다.
    sun = datetime.datetime(2026, 6, 28, 4, 40)
    assert target_iso_week(sun) == (2026, 26)
    assert target_iso_week(sun + datetime.timedelta(days=1)) == (2026, 26)   # Mon
    assert target_iso_week(sun + datetime.timedelta(days=2)) == (2026, 26)   # Tue

def test_parse_week_arg():
    assert parse_week_arg("2026-W26") == (2026, 26)
    with pytest.raises(ValueError):
        parse_week_arg("2026/26")

def test_iso_week_bounds():
    mon, sun = iso_week_bounds(2026, 26)
    assert mon.isoweekday() == 1 and sun.isoweekday() == 7 and (sun - mon).days == 6

def test_aggregate_signals():
    c = _seed(os.environ["MORNINGBRIEF_DB"])
    from src.storage import get_articles_by_range
    mon, sun = iso_week_bounds(2026, 26)
    arts = get_articles_by_range(c, f"{mon}T00:00:00", f"{sun}T23:59:59")
    sig = aggregate_week(arts, mon, sun)
    assert sig["total"] == 21
    assert any(t["tag"] == "mcp" and t["persistent"] for t in sig["persistent_stems"])  # 6일 지속
    assert any(s["source"] == "VentureBeat AI" and s["noise_candidate"] for s in sig["noise_sources"])
    assert len(sig["workshop_picks"]) == 7   # relevance 8 (j==0) × 7일

def test_empty_week_safe():
    init_db(os.environ["MORNINGBRIEF_DB"])
    sig = aggregate_week([], *iso_week_bounds(2026, 26))
    assert sig["total"] == 0 and len(sig["sparse_days"]) == 7

# --- 통합(엔드투엔드, hermetic) ---
def test_report_created_and_path():
    _seed(os.environ["MORNINGBRIEF_DB"])
    r = _run_weekly("--week", "2026-W26")
    assert r.returncode == 0, r.stderr
    p = os.path.join(os.environ["MORNINGBRIEF_REPORTS_DIR"], "2026", "weekly", "W26.md")
    assert os.path.exists(p)

def test_cursor_idempotent():
    _seed(os.environ["MORNINGBRIEF_DB"])
    _run_weekly("--week", "2026-W26")             # 생성 + 커서=2026-W26
    db = os.environ["MORNINGBRIEF_DB"]
    v = sqlite3.connect(db).execute("SELECT value FROM meta WHERE key='last_weekly_iso_week'").fetchone()
    assert v and v[0] == "2026-W26"
    # --week 없이 정시 산정 주차가 커서와 같으면 skip (다르면 신규 생성). 커서 자체는 유지된다.
```
> 통합 테스트는 `--force-fallback`로 Claude API를 호출하지 않는다(과금·네트워크 없음, 수 초 내 완료).

### 6-5. README 추가 섹션 (운영)
```
## 주간 흐름 리포트(--weekly) 운영
1) 수동 생성: `python main.py --weekly`  (강제 특정 주: `--weekly --week 2026-W26`)
2) 스케줄 등록(일요일 04:40):
   - scripts/com.itsangsang.morningbrief.weekly.plist 의 /ABS/PATH 를 절대경로로 치환
     (python3 경로 기본값: /Library/Frameworks/Python.framework/Versions/3.14/bin/python3)
   - cp scripts/com.itsangsang.morningbrief.weekly.plist ~/Library/LaunchAgents/
   - launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.itsangsang.morningbrief.weekly.plist
3) 운영 배포본 동기화(보호폴더 밖):
   - rsync -a --delete <repo>/05-개발/ ~/AI-Morning-Brief-run/   (TCC 회피, 일간과 동일 패턴)
   - plist 의 경로는 ~/AI-Morning-Brief-run/main.py 기준으로 둔다.
4) 산출물: reports/<ISO연도>/weekly/W##.md (전문, 표 포함) + 일간과 동일 Discord 웹훅으로 9블록 다이제스트.
```

## 7. 예외 처리
- **Discord 전송 실패/웹훅 없음**: `send_discord`가 로그만 남기고 False 반환(기존 정책) — `run_weekly`는 커서 갱신·종료코드 0 유지.
- **2000자 초과(이상 케이스)**: `build_weekly_message`가 마지막에 `…`로 안전 절단(설계상 9블록은 한도 내 완결이라 정상 경로에선 미발동).
- **synthesis가 fallback(빈약)**: 블록 항목이 비면 해당 블록을 생략(키 존재·비어있음 모두 가드).
- **launchd 절대경로 오치환**: README에 `pwd`/`which python3` 치환 안내. plist는 `plutil -lint`로 검증.
- **테스트 격리**: 운영 `data/morning_brief.db`·`reports/`를 절대 건드리지 않음(tmp_path + env override). 일간 `tests/test_pipeline.py`와 독립.

## 8. 완료 조건
- `python main.py --weekly --no-discord --force-fallback` 후 `reports/<ISO연도>/weekly/W##.md` 생성(FEAT-09 회귀 확인) — `--no-discord` 없이 실행하면 웹훅 설정 시 Discord에 9블록 다이제스트 1건 게시.
- `build_weekly_message` 출력이 9블록 순서를 따르고 길이 ≤ 2000.
- `scripts/com.itsangsang.morningbrief.weekly.plist`가 유효 XML(`plutil -lint` OK)이고 일요일(Weekday 0) 04:40로 설정.
- `pytest -q tests/test_weekly.py` 전부 통과(ISO 주차 산정·멱등 커서·빈 주·전문 경로·신호 집계).
- 기존 `pytest -q tests/test_pipeline.py` 회귀 없음.
- README에 주간 plist 등록 + `~/AI-Morning-Brief-run` 동기화 절차 포함.

## 9. 테스트 방법
```bash
cd projects/AI-Morning-Brief/05-개발
pytest -q tests/test_weekly.py          # 주간 통합 (hermetic)
pytest -q tests/test_pipeline.py        # 일간 회귀
plutil -lint scripts/com.itsangsang.morningbrief.weekly.plist   # XML 유효성
# 다이제스트 포맷 눈으로 확인 (fallback, 외부 의존 없음)
python -c "
import datetime
from src.notifier import build_weekly_message
sig={'total':142,'daily_intensity':[],'peak_day':{'date':'2026-06-24','count':31},
 'sparse_days':['2026-06-28'],'persistent_stems':[{'tag':'mcp','count':28,'day_span':6},
 {'tag':'agent','count':24,'day_span':6}],'noise_sources':[{'source':'VentureBeat AI','count':38,'avg_importance':2.4}]}
syn={'one_line_summary':'에이전트·MCP가 지속 줄기로.','flow_themes':['MCP 표준화','eval 경쟁'],
 'notable_events':['MCP 레지스트리 공개'],'workshop_actions':['MCP 연결 PoC'],'next_week_watch':['eval 표준화']}
m=build_weekly_message('2026-W26',datetime.date(2026,6,22),datetime.date(2026,6,28),sig,syn,'reports/2026/weekly/W26.md')
print(m); print('len=',len(m), 'ok=', len(m)<=2000)
"
```

## 10. 금지 사항
- 주간 집계·합성·전문 빌더(FEAT-09 범위) 로직 추가·수정 금지 — 이미 만든 산출물을 소비만 한다.
- 임베드/분할 전송 금지(plain content 단일 POST, FEAT-06 정책 유지).
- 일간 plist·일간 테스트(`tests/test_pipeline.py`) 수정 금지.
- DB 스키마 변경 금지(커서는 FEAT-09가 둔 `meta.last_weekly_iso_week` 사용).
- 불필요한 라이브러리 추가 금지(기존 의존성 + 표준 라이브러리).
- 이 이슈 범위를 벗어나는 리팩터링 금지.
