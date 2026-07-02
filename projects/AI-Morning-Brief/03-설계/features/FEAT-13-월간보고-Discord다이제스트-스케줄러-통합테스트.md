<!--
3단계 설계자 산출물. AI-Morning-Brief 증분 기능 `--monthly` 3/3.
분해 사유: FEAT-11=신호·필터 엔진, FEAT-12=합성·전문 생성·오케스트레이션(전문 .md까지),
FEAT-13=배달·예약·검증. 주간 FEAT-10과 동형이며, FEAT-12가 만든 run_monthly/전문/신호를 소비만 한다.
① Discord 월간 9블록 다이제스트(신뢰도 카운트·편중 반영) ② run_monthly 전송 hook 연결
③ 매월 1일 04:50 launchd 월간 plist ④ 통합테스트 tests/test_monthly.py 엔드투엔드 ⑤ README 월간 운영 안내.
-->

# FEAT-13 — 월간보고 Discord 다이제스트 + 매월1일 스케줄러 + 통합테스트

- 매칭 이슈: #13 (확정 시 4단계에서 번호 일치 등록)
- 작성일: 2026-07-02
- 상위 설계서: `03-설계/설계서.md` (하단 "(증분) 월간 흐름 리포트 `--monthly` 설계 — 애드덤 (#12)")
- 의존: **FEAT-12**(`src/monthly.run_monthly`, `aggregate_month` 신호 구조, `basis` 메타, `reports/<YYYY>/monthly/M##.md`), **FEAT-11**(순수 함수). 기존 `notifier.send_discord` 재사용.

## 1. 목적
FEAT-12가 만든 월간 전문(.md)과 정량 신호·분석 기반(basis)·합성 결과를 **일간·주간과 동일한 Discord 웹훅**으로 2000자 이내 9블록 다이제스트(#12-7 분석 기반·#12-2 신뢰도 라벨·#12-5 편중 반영)로 전송하고, **매월 1일 04:50 launchd 월간 잡**으로 무인 실행을 예약하며, 트리거 멱등·직전 달 산정·경계 분리·2단계 필터·편중 정규화·전문 경로를 **통합테스트로 자동 검증**한다. 이 FEAT 완료로 `--monthly` 증분이 "배달·예약·검증되는 자동화"로 마감된다.

## 2. 범위
### 구현할 것
- `src/notifier.py` (수정): `build_monthly_message(...)` 추가 — 9블록·2000자 이내·plain content(절삭 아닌 의도적 요약). 기존 `send_discord` 재사용.
- `src/monthly.py` (수정): `run_monthly`의 "(FEAT-13 hook)" 자리에 Discord 전송 블록 연결(`--no-discord`면 생략). `send_discord` import 추가.
- `scripts/com.itsangsang.morningbrief.monthly.plist` (신규): 매월 1일 04:50, `main.py --monthly` 호출.
- `tests/test_monthly.py` (수정/보강): 엔드투엔드(전문 생성·경로·멱등 커서) + 다이제스트 길이·블록 검증 추가(FEAT-11이 만든 순수 함수 테스트 위에 이어 붙임). tmp_path 격리·env override·`--force-fallback` hermetic.
- `05-개발/README.md` (수정): 월간 plist 등록 + `~/AI-Morning-Brief-run` rsync 운영 절차 추가(일간·주간과 동일 패턴).
### 구현하지 않을 것
- 월간 집계·필터·편중·대표선별(FEAT-11), 합성·전문 빌더·run_monthly 오케스트레이션(FEAT-12) — 여기서는 **소비만**.
- 임베드/분할 전송(FEAT-06 "분할 없음" 정책 유지 — plain content 단일 POST).
- 일간·주간 plist·테스트(`tests/test_pipeline.py`·`tests/test_weekly.py`) 수정.
- DB 스키마 변경(커서는 FEAT-12가 둔 `meta.last_monthly_ym` 사용).

## 3. 입력 / 출력
### 입력
- FEAT-12 산출: `run_monthly` 내부의 `ym`, `first`/`last`, `signals`(aggregate_month), `basis`(분석 기반 dict), `synthesis`(synthesize_monthly의 synthesis), `path`(전문 경로).
- `load_secrets()["DISCORD_WEBHOOK_URL"]`.
### 출력
- Discord 채널에 9블록 월간 다이제스트 1건(plain content, ≤2000자).
- `~/Library/LaunchAgents/com.itsangsang.morningbrief.monthly.plist`(운영자 등록).
- `pytest -q tests/test_monthly.py` 전부 통과.

## 4. 동작 흐름
1. **다이제스트 빌더**: `build_monthly_message(ym, first, last, signals, basis, synthesis, report_path)`가 9블록 문자열 조립(6장 포맷). 표 없이 인라인 한 줄 압축, 2000자 이내 완결.
2. **전송 hook 연결**: `run_monthly`의 `save_bucket_report` 직후·`set_meta` 직전에 추가:
   ```python
   if not getattr(args, "no_discord", False):
       from src.notifier import build_monthly_message
       msg = build_monthly_message(ym, first, last, signals, basis,
                                   result["synthesis"], path)
       send_discord(secrets.get("DISCORD_WEBHOOK_URL"), msg)
   ```
   (`send_discord`는 monthly.py 상단 import 추가. 전송 실패는 기존 정책대로 로그만 — 파이프라인 지속.)
3. **launchd 월간 잡**: 매월 1일(Day 1) 04:50에 `python main.py --monthly` 실행. 트리거 멱등은 FEAT-12 커서가 보장 → plist는 단순 호출.
4. **통합테스트**: tmp_path + env override로 격리, 특정 달 데이터 시드 후 `--monthly --month`/직전 달 산정·멱등·빈 월·경로·다이제스트 길이 검증(`--force-fallback` hermetic).
5. **README**: 월간 plist 등록 + `~/AI-Morning-Brief-run` 동기화 절차.

## 5. 수정 예상 파일
- `05-개발/src/notifier.py` (수정 — `build_monthly_message` 추가, 기존 함수 미변경)
- `05-개발/src/monthly.py` (수정 — Discord hook 연결 + `send_discord` import)
- `05-개발/scripts/com.itsangsang.morningbrief.monthly.plist` (신규)
- `05-개발/tests/test_monthly.py` (수정 — 엔드투엔드·다이제스트 테스트 보강)
- `05-개발/README.md` (수정 — 월간 운영 섹션 추가)

## 6. 데이터 구조 / 함수 / 클래스

### 6-1. `src/notifier.py` — `build_monthly_message`
```python
def build_monthly_message(ym, first, last, signals, basis, synthesis,
                          report_path, char_limit=2000) -> str:
    """월간 다이제스트(9블록, plain content, char_limit 이내 완결). 절삭 아닌 의도적 요약.

    블록 순서(스펙 M-J):
      1) 📅 월(YYYY-MM) + 한 줄 요약(synthesis['one_line_summary'])
      2) 🔎 분석 기반(#12-7): 수집·1차통과·대표·범위외·노이즈 + 신뢰도 카운트(확정/추정/주의)
      3) 🌊 월간 핵심 흐름 테마 Top4(synthesis['flow_themes'][:4], [라벨] 그대로)
      4) ⭐ 주목 사건 Top3(synthesis['notable_events'][:3], [라벨] 그대로)
      5) 🌳 지속 줄기 태그 Top6("태그 N·M일" 인라인, count=정규화)
      6) ⚠️ 편중/노이즈(#12-5,6): 최다 소스 점유율 + 노이즈 소스 Top1
      7) 🎯 공방 즉시 착수 액션 Top3(synthesis['workshop_actions'][:3])
      8) 🔭 다음 달 관전(synthesis['next_month_watch'])
      9) 📄 전체 리포트 경로(report_path)
    """
    import re
    peak = signals.get("peak_day") or {"date": "-", "count": 0}
    stems = signals.get("persistent_stems", [])[:6]
    srcs = signals.get("sources", [])
    noise = signals.get("noise_sources", [])
    noise_basis = basis.get("noise", {}) or {}

    # 신뢰도 카운트: flow_themes+notable_events+narrative의 라벨 등장 수 집계 (#12-2 요약)
    joined = " ".join(synthesis.get("flow_themes", []) + synthesis.get("notable_events", [])
                      + [synthesis.get("narrative", "")])
    conf = {lab: len(re.findall(re.escape(f"[{lab}]"), joined)) for lab in ("확정", "추정", "주의")}

    L = [f"📅 **AI Morning Brief 월간 — {ym} ({first.month}/{first.day}~{last.month}/{last.day})**",
         (synthesis.get("one_line_summary") or "").strip()]
    L.append(f"\n🔎 분석 기반: 수집 {basis.get('collected',0)} · 1차통과 {basis.get('first_stage_passed',0)}"
             f" · 대표 {basis.get('represented',0)} · 범위외 {basis.get('out_of_period',0)}"
             f" · 노이즈 {noise_basis.get('_total',0)}"
             f" · 신뢰도 [확정 {conf['확정']}/추정 {conf['추정']}/주의 {conf['주의']}]")
    if synthesis.get("flow_themes"):
        L.append("🌊 월간 흐름:\n" + "\n".join(f"• {x}" for x in synthesis["flow_themes"][:4]))
    if synthesis.get("notable_events"):
        L.append("⭐ 주목 사건:\n" + "\n".join(f"{i+1}. {x}" for i, x in enumerate(synthesis["notable_events"][:3])))
    if stems:
        L.append("🌳 지속 줄기: " + " · ".join(f"{t['tag']} {t['count']}·{t['day_span']}일" for t in stems))
    if srcs or noise:
        top_src = srcs[0] if srcs else None
        parts = []
        if top_src:
            parts.append(f"최다 {top_src['source']} {round(top_src['share']*100)}%")
        if noise:
            parts.append(f"노이즈 {noise[0]['source']} {noise[0]['count']}건·평균 {noise[0]['avg_importance']}")
        if parts:
            L.append("⚠️ 편중/노이즈: " + " · ".join(parts))
    if synthesis.get("workshop_actions"):
        L.append("🎯 공방 즉시 착수:\n" + "\n".join(f"{i+1}. {x}" for i, x in enumerate(synthesis["workshop_actions"][:3])))
    if synthesis.get("next_month_watch"):
        L.append("🔭 다음 달 관전: " + " / ".join(synthesis["next_month_watch"][:3]))
    L.append(f"📄 전체 리포트: {report_path}")

    msg = "\n".join(s for s in L if s)
    if len(msg) > char_limit:              # 안전망(설계상 9블록은 2000자 내 완결)
        msg = msg[:char_limit - 1] + "…"
    return msg
```

### 6-2. 실제 렌더 예시 (M06 mock — builder 포맷 기준)
```
📅 **AI Morning Brief 월간 — 2026-06 (6/1~6/30)**
이번 달 핵심: '에이전트 인프라 전쟁'이 열리고 MCP가 통합 표준으로 굳어짐.

🔎 분석 기반: 수집 287 · 1차통과 188 · 대표 40 · 범위외 11 · 노이즈 88 · 신뢰도 [확정 5/추정 3/주의 1]
🌊 월간 흐름:
• [확정] MCP가 모바일·엣지·결제로 확산되며 통합 레이어 표준화
• [확정] Codex가 '지식 노동 도구'로 재정의, 전 직군 확산
• [추정] 에이전트 대량 배포에 따른 비용 가시성 도구가 필수로 부상
• [주의] Anthropic Fable 5 배포 중단 — 브랜드 영향은 미확정
⭐ 주목 사건:
1. [확정] MCP 2026-07-28 RC 릴리즈
2. [확정] Google I/O 2026: 독립 에이전트 전환 선언
3. [추정] OpenAI S-1 비밀 제출 + IPO 준비
🌳 지속 줄기: mcp 34·12일 · agent 29·11일 · codex 18·9일 · gemini 15·8일 · claude 14·8일 · eval 9·8일
⚠️ 편중/노이즈: 최다 VentureBeat AI 21% · 노이즈 VentureBeat AI 41건·평균 2.3
🎯 공방 즉시 착수:
1. MCP 서버 1개 직접 구축(노션/GitHub 연동)
2. Vercel AI Gateway 예산 한도 설정 후 워크플로우 테스트
3. AGENTS.md 주요 저장소에 작성
🔭 다음 달 관전: MCP 최종 스펙 확정 / 에이전트 비용 통제 표준 / 온디바이스 에이전트
📄 전체 리포트: reports/2026/monthly/M06.md
```
(위 예시 ≈ 900자. 9블록 모두 채워도 2000자 한도 내 완결.)

### 6-3. `scripts/com.itsangsang.morningbrief.monthly.plist`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>com.itsangsang.morningbrief.monthly</string>
  <!-- ProgramArguments / WorkingDirectory 의 /ABS/PATH 는 README 절차대로 절대경로 치환. -->
  <key>ProgramArguments</key>
  <array>
    <string>/Library/Frameworks/Python.framework/Versions/3.14/bin/python3</string>
    <string>/ABS/PATH/TO/projects/AI-Morning-Brief/05-개발/main.py</string>
    <string>--monthly</string>
  </array>
  <key>WorkingDirectory</key>
  <string>/ABS/PATH/TO/projects/AI-Morning-Brief/05-개발</string>
  <!-- 매월 1일 04:50 (Day=1). 일간 04:30·주간 일요일 04:40 이후로 배치해 DB 경합 최소화 -->
  <key>StartCalendarInterval</key>
  <dict>
    <key>Day</key><integer>1</integer>
    <key>Hour</key><integer>4</integer>
    <key>Minute</key><integer>50</integer>
  </dict>
  <key>StandardOutPath</key><string>/tmp/morningbrief.monthly.out.log</string>
  <key>StandardErrorPath</key><string>/tmp/morningbrief.monthly.err.log</string>
  <key>RunAtLoad</key><false/>
  <key>KeepAlive</key><false/>
</dict>
</plist>
```
> **경합·순서**: 매월 1일에는 일간(04:30)·(1일이 일요일이면)주간(04:40)·월간(04:50)이 순차 실행될 수 있다. 월간 잡은 `articles`를 **읽기만** 하고 자체 커넥션을 열며 sqlite 동시 읽기를 허용하므로 안전하다. 전월 말일 데이터는 그 날 04:30 일간이 이미 저장한 상태다. **자가치유**: 1일을 sleep으로 놓쳐도 wake 시 launchd가 보완 실행하고, FEAT-11 `target_month(now)`가 그 달 내내 직전 달을 가리켜 같은 월간보고를 생성한다(커서 `last_monthly_ym`가 중복 방지).

### 6-4. `tests/test_monthly.py` 엔드투엔드 보강 (FEAT-11 순수 테스트 위에 이어 붙임)
```python
# (상단 import·fixture는 FEAT-11이 만든 것 재사용: tmp_path env override, --force-fallback hermetic)
import datetime, os, subprocess, sys, sqlite3

def _seed_month(db, y=2026, m=6):
    from src.storage import init_db, insert_article, update_analysis
    import datetime as dt
    c = init_db(db)
    first = dt.date(y, m, 1)
    for i in range(28):
        d = (first + dt.timedelta(days=i)).isoformat()
        for j in range(4):
            u = f"u{i}-{j}"
            src = "VentureBeat AI" if j == 3 else ("OpenAI" if j == 0 else "Google")
            insert_article(c, {"url": u, "title": f"MCP agent update {i}-{j}", "source": src,
                               "published_at": d, "collected_at": d + "T08:00:00", "raw_excerpt": "x"})
            update_analysis(c, u, "요약", ["mcp", "agent"], 9 if j == 0 else 2, 8 if j == 0 else 1, 1)
    # 범위 밖(4월) + 무관(정치)
    insert_article(c, {"url": "old", "title": "old news", "source": "X",
                       "published_at": "2026-04-01", "collected_at": "2026-06-02T08:00:00", "raw_excerpt": "x"})
    update_analysis(c, "old", "s", ["ai"], 2, 1, 1)
    insert_article(c, {"url": "pol", "title": "city election policy", "source": "Y",
                       "published_at": "2026-06-05", "collected_at": "2026-06-05T08:00:00", "raw_excerpt": "x"})
    update_analysis(c, "pol", "s", [], 0, 0, 1)
    return c

def _run_monthly(*flags):
    return subprocess.run([sys.executable, "main.py", "--monthly", "--no-discord", "--force-fallback", *flags],
                          cwd=ROOT, capture_output=True, text=True, env=os.environ.copy())

def test_target_month_and_parse():
    from src.monthly import target_month, parse_month_arg
    assert target_month(datetime.datetime(2026, 7, 1, 4, 50)) == (2026, 6)
    assert target_month(datetime.datetime(2026, 7, 5, 9, 0)) == (2026, 6)   # 지연도 직전 달
    assert target_month(datetime.datetime(2026, 1, 1, 4, 50)) == (2025, 12) # 연초 경계
    assert parse_month_arg("2026-06") == (2026, 6)

def test_period_and_two_stage_filter():
    from src.monthly import month_bounds, partition_by_period, first_stage_filter, classify_noise
    from src.storage import get_articles_by_range
    c = _seed_month(os.environ["MORNINGBRIEF_DB"])
    first, last = month_bounds(2026, 6)
    raw = get_articles_by_range(c, f"{first}T00:00:00", f"{last}T23:59:59")
    in_p, out_p = partition_by_period(raw, first, last)
    assert any(a["url"] == "old" for a in out_p)     # 4월 published_at = 범위 외
    passed, rej = first_stage_filter(in_p)
    assert any(a["url"] == "pol" for a in rej)         # 정치 = 1차 탈락
    assert classify_noise(rej)["_total"] >= 1

def test_source_bias_cap_and_top():
    from src.monthly import month_bounds, partition_by_period, first_stage_filter, select_top_articles_monthly, SOURCE_CAP_RATIO
    from src.storage import get_articles_by_range
    c = _seed_month(os.environ["MORNINGBRIEF_DB"])
    first, last = month_bounds(2026, 6)
    raw = get_articles_by_range(c, f"{first}T00:00:00", f"{last}T23:59:59")
    passed, _ = first_stage_filter(partition_by_period(raw, first, last)[0])
    top = select_top_articles_monthly(passed)
    from collections import Counter
    cnt = Counter(a["source"] for a in top)
    cap = max(3, round(len(passed) * SOURCE_CAP_RATIO))
    assert all(v <= cap for v in cnt.values())         # 소스 지배 방지

def test_report_created_and_sections():
    _seed_month(os.environ["MORNINGBRIEF_DB"])
    r = _run_monthly("--month", "2026-06")
    assert r.returncode == 0, r.stderr
    p = os.path.join(os.environ["MORNINGBRIEF_REPORTS_DIR"], "2026", "monthly", "M06.md")
    assert os.path.exists(p)
    md = open(p, encoding="utf-8").read()
    assert "수집" in md and ("[확정]" in md or "[추정]" in md)   # #12-7 기반 + #12-2 라벨
    assert "점유율" in md or "편중" in md                        # #12-5
    assert "범위 외" in md                                       # #12-1

def test_cursor_idempotent():
    _seed_month(os.environ["MORNINGBRIEF_DB"])
    _run_monthly("--month", "2026-06")
    db = os.environ["MORNINGBRIEF_DB"]
    v = sqlite3.connect(db).execute("SELECT value FROM meta WHERE key='last_monthly_ym'").fetchone()
    assert v and v[0] == "2026-06"

def test_digest_length():
    import datetime as dt
    from src.notifier import build_monthly_message
    sig = {"peak_day": {"date": "2026-06-24", "count": 31},
           "persistent_stems": [{"tag": "mcp", "count": 34, "day_span": 12}],
           "sources": [{"source": "VentureBeat AI", "share": 0.21, "count": 60, "avg_importance": 2.3}],
           "noise_sources": [{"source": "VentureBeat AI", "count": 41, "avg_importance": 2.3}]}
    basis = {"collected": 287, "first_stage_passed": 188, "represented": 40,
             "out_of_period": 11, "noise": {"_total": 88}}
    syn = {"one_line_summary": "에이전트 인프라 전쟁 개막.",
           "flow_themes": ["[확정] MCP 표준화"], "notable_events": ["[확정] MCP RC"],
           "workshop_actions": ["MCP 서버 구축"], "next_month_watch": ["MCP 최종 스펙"],
           "narrative": "[확정] ... [추정] ..."}
    m = build_monthly_message("2026-06", dt.date(2026, 6, 1), dt.date(2026, 6, 30), sig, basis, syn, "reports/2026/monthly/M06.md")
    assert len(m) <= 2000 and "분석 기반" in m and "신뢰도" in m
```
> 통합 테스트는 `--force-fallback`로 Claude API를 호출하지 않는다(과금·네트워크 없음, 수 초 내 완료).

### 6-5. README 추가 섹션 (운영)
```
## 월간 흐름 리포트(--monthly) 운영
1) 수동 생성: `python main.py --monthly`  (강제 특정 달: `--monthly --month 2026-06`)
2) 스케줄 등록(매월 1일 04:50):
   - scripts/com.itsangsang.morningbrief.monthly.plist 의 /ABS/PATH 를 절대경로로 치환
   - cp scripts/com.itsangsang.morningbrief.monthly.plist ~/Library/LaunchAgents/
   - launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.itsangsang.morningbrief.monthly.plist
3) 운영 배포본 동기화(보호폴더 밖): rsync -a --delete <repo>/05-개발/ ~/AI-Morning-Brief-run/
4) 산출물: reports/<YYYY>/monthly/M##.md (전문, 표·신뢰도 라벨·노이즈 상단 명시) + 동일 Discord 웹훅 9블록 다이제스트.
5) 실행 순서: 일간 04:30 → (일요일)주간 04:40 → (매월1일)월간 04:50. 모두 멱등(커서), 중복 실행 무해.
```

## 7. 예외 처리
- **Discord 전송 실패/웹훅 없음**: `send_discord`가 로그만 남기고 False 반환(기존 정책) — `run_monthly`는 커서 갱신·종료코드 0 유지.
- **2000자 초과(이상 케이스)**: `build_monthly_message`가 마지막에 `…`로 안전 절단(설계상 9블록은 한도 내 완결).
- **synthesis가 fallback(빈약)**: 블록 항목이 비면 해당 블록 생략(키 존재·비어있음 가드). 신뢰도 카운트는 fallback 라벨로 0 이상.
- **launchd 절대경로 오치환**: README에 `pwd`/`which python3` 치환 안내. plist는 `plutil -lint`로 검증.
- **테스트 격리**: 운영 `data/morning_brief.db`·`reports/`를 절대 건드리지 않음(tmp_path + env override). 일간·주간 테스트와 독립.

## 8. 완료 조건
- `python main.py --monthly --no-discord --force-fallback` 후 `reports/<YYYY>/monthly/M##.md` 생성(FEAT-12 회귀 확인) — `--no-discord` 없이 실행하면 웹훅 설정 시 Discord에 9블록 다이제스트 1건 게시.
- `build_monthly_message` 출력이 9블록 순서를 따르고 길이 ≤ 2000, **② 분석 기반·신뢰도 카운트 블록** 포함(#12-7,2).
- `scripts/com.itsangsang.morningbrief.monthly.plist`가 유효 XML(`plutil -lint` OK)이고 매월 1일(Day 1) 04:50로 설정.
- `pytest -q tests/test_monthly.py` 전부 통과(직전 달 산정·경계 분리·2단계 필터·소스 상한·멱등 커서·전문 섹션·다이제스트 길이).
- 기존 `pytest -q tests/test_weekly.py tests/test_pipeline.py` 회귀 없음.
- README에 월간 plist 등록 + `~/AI-Morning-Brief-run` 동기화 절차 포함.

## 9. 테스트 방법
```bash
cd projects/AI-Morning-Brief/05-개발
pytest -q tests/test_monthly.py        # 월간 통합 (hermetic)
pytest -q tests/test_weekly.py tests/test_pipeline.py   # 주간·일간 회귀
plutil -lint scripts/com.itsangsang.morningbrief.monthly.plist   # XML 유효성
# 다이제스트 포맷 눈으로 확인 (fallback, 외부 의존 없음)
python -c "
import datetime
from src.notifier import build_monthly_message
sig={'peak_day':{'date':'2026-06-24','count':31},
 'persistent_stems':[{'tag':'mcp','count':34,'day_span':12},{'tag':'agent','count':29,'day_span':11}],
 'sources':[{'source':'VentureBeat AI','share':0.21,'count':60,'avg_importance':2.3}],
 'noise_sources':[{'source':'VentureBeat AI','count':41,'avg_importance':2.3}]}
basis={'collected':287,'first_stage_passed':188,'represented':40,'out_of_period':11,'noise':{'_total':88}}
syn={'one_line_summary':'에이전트 인프라 전쟁 개막.','flow_themes':['[확정] MCP 표준화','[추정] 비용 가시성 부상'],
 'notable_events':['[확정] MCP RC 릴리즈'],'workshop_actions':['MCP 서버 구축'],'next_month_watch':['MCP 최종 스펙']}
m=build_monthly_message('2026-06',datetime.date(2026,6,1),datetime.date(2026,6,30),sig,basis,syn,'reports/2026/monthly/M06.md')
print(m); print('len=',len(m),'ok=',len(m)<=2000)
"
```

## 10. 금지 사항
- 월간 집계·필터·편중·대표선별(FEAT-11)·합성·전문 빌더·run_monthly 오케스트레이션(FEAT-12) 로직 추가·수정 금지 — 산출물을 소비만.
- 임베드/분할 전송 금지(plain content 단일 POST, FEAT-06 정책 유지).
- 일간·주간 plist·테스트(`tests/test_pipeline.py`·`tests/test_weekly.py`) 수정 금지.
- DB 스키마 변경 금지(커서는 FEAT-12가 둔 `meta.last_monthly_ym`).
- 불필요한 라이브러리 추가 금지(기존 의존성 + 표준 라이브러리).
- 이 이슈 범위를 벗어나는 리팩터링 금지.

> ⚠️ **기존 코드 보호 원칙**: 기존 weekly/daily 코드의 **동작 변경은 금지**한다. 공통 함수 **추출**은 허용하지만(= 기존 코드에서 로직을 그대로 빼내 재사용만, 재작성·시그니처 변경 불가), 기존 테스트가 **하나라도 실패하면 리팩토링을 되돌린다**.
