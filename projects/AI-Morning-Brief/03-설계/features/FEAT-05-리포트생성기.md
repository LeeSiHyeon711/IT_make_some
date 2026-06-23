# FEAT-05 — Markdown 리포트 생성기

- 매칭 이슈: #5
- 작성일: 2026-06-21
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
분석 결과(또는 fallback)와 메타 정보를 받아 섹션 구성 Markdown 일일 리포트를 만들고 날짜별 폴더에 저장한다. 모드(claude/fallback), catch-up/보완 수집, 실패 소스, 수집 범위, 운영자 프로필 부재 등을 리포트 상단에 명시한다.

## 2. 범위
### 구현할 것
- `src/reporter.py`: `build_report`(문자열 반환), `save_report`(파일 저장).
- 요구사항-정의서 5-4 섹션 구성 반영.
### 구현하지 않을 것
- 수집/분석/전송. Discord용 2000자 메시지(FEAT-06).
- HTML/PDF 변환(범위 외).

## 3. 입력 / 출력
### 입력
- `date: str`(YYYY-MM-DD), `range_start`, `range_end`, `articles: list[dict]`(DB 형태 + 분석 병합: url/title/source/published_at/summary/tags/importance/relevance), `briefing: dict`(FEAT-04 형태), `mode: str`, `catchup: bool`, `failed_sources: list[dict]`, `new_count: int`, `profile_is_default: bool`.
### 출력
- `build_report` → Markdown 문자열.
- `save_report` → 저장 경로(`reports/<date>/report.md`).

## 4. 동작 흐름
1. 헤더 블록: 제목, 날짜, 모드 배지, 수집 범위, 신규/중요 건수, catch-up·실패소스·프로필부재 경고.
   - mode=="fallback" → 상단에 `> **AI 분석 실패 / 기본 리포트 생성**`.
   - catchup True → `> 보완 수집(catch-up): {start} ~ {end}`.
   - failed_sources 있으면 → `> 수집 실패 소스: [..]`.
   - profile_is_default True → `> ⚠ 운영자 프로필 없음 — 기본 프로필로 분석`.
2. 섹션 구성(요구사항 5-4):
   - 오늘의 핵심 변화 (briefing.headline_changes)
   - Claude / Claude Code 관련 (tags에 claude/anthropic/mcp 포함 기사)
   - OpenAI / Codex / GPT 관련 (tags에 openai/gpt/codex)
   - MCP / Agent / 자동화 관련 (tags에 mcp/agent/automation/n8n/github actions)
   - 상상공방에 적용할 수 있는 아이디어 (briefing.sangsang_ideas)
   - 나중에 실험해볼 액션 아이템 (briefing.action_items)
   - 저장해둘 원문 링크 (전체 기사 url 목록, importance 내림차순)
3. `save_report`: `reports/<date>/` 생성 후 `report.md` 기록(같은 날 재실행 시 덮어씀).

## 5. 수정 예상 파일
- `05-개발/src/reporter.py` (신규)

## 6. 데이터 구조 / 함수 / 클래스
```python
# src/reporter.py
import os

def _section(title, items):
    if not items: return f"## {title}\n\n_해당 없음_\n"
    return f"## {title}\n\n" + "\n".join(f"- {x}" for x in items) + "\n"

def _by_tags(articles, keys):
    return [f"[{a['title']}]({a['url']}) — {a.get('summary','')}"
            for a in articles if set(k.lower() for k in a.get('tags',[])) & set(keys)]

def build_report(date, range_start, range_end, articles, briefing, mode,
                 catchup=False, failed_sources=None, new_count=0,
                 profile_is_default=False) -> str:
    failed_sources = failed_sources or []
    important = sum(1 for a in articles if a.get("importance",0) >= 4)
    head = [f"# AI Morning Brief — {date}", ""]
    if mode == "fallback":
        head.append("> **AI 분석 실패 / 기본 리포트 생성**")
    if catchup:
        head.append(f"> 보완 수집(catch-up): {range_start} ~ {range_end}")
    if profile_is_default:
        head.append("> ⚠ 운영자 프로필 없음 — 기본 프로필로 분석")
    if failed_sources:
        head.append(f"> 수집 실패 소스: {[f['source'] for f in failed_sources]}")
    head += ["",
        f"- 수집 범위: {range_start} ~ {range_end}",
        f"- 신규 수집: {new_count}건 / 중요(≥4): {important}건",
        f"- 분석 모드: {mode}", ""]
    body = [
        _section("오늘의 핵심 변화", briefing.get("headline_changes", [])),
        _section("Claude / Claude Code 관련 소식", _by_tags(articles, ["claude","anthropic","mcp"])),
        _section("OpenAI / Codex / GPT 관련 소식", _by_tags(articles, ["openai","gpt","codex"])),
        _section("MCP / Agent / 자동화 관련 소식", _by_tags(articles, ["mcp","agent","automation","n8n","github actions"])),
        _section("상상공방에 적용할 수 있는 아이디어", briefing.get("sangsang_ideas", [])),
        _section("나중에 실험해볼 액션 아이템", briefing.get("action_items", [])),
        _section("저장해둘 원문 링크",
                 [f"[{a['title']}]({a['url']})" for a in
                  sorted(articles, key=lambda x: x.get("importance",0), reverse=True)]),
    ]
    return "\n".join(head) + "\n" + "\n".join(body)

def save_report(reports_dir, date, content) -> str:
    d = os.path.join(reports_dir, date); os.makedirs(d, exist_ok=True)
    path = os.path.join(d, "report.md")
    with open(path, "w", encoding="utf-8") as f: f.write(content)
    return path
```

## 7. 예외 처리
- articles/briefing 빈 값 → 각 섹션 "_해당 없음_"으로 안전 출력.
- 디렉토리 없음 → `save_report`가 생성.
- tags None → `a.get('tags',[])`로 보호.

## 8. 완료 조건
- mode="fallback"이면 리포트 상단에 정확히 `AI 분석 실패 / 기본 리포트 생성` 문구 포함(QA-5).
- catchup=True면 수집 범위 문자열(예 2026-06-01)이 본문에 등장(QA-6).
- `save_report`가 `reports/<date>/report.md`를 생성(QA-3).
- 7개 섹션이 모두 존재.

## 9. 테스트 방법
```bash
cd projects/AI-Morning-Brief/05-개발
python -c "
from src.reporter import build_report, save_report
arts=[{'url':'u1','title':'Claude Code','summary':'요약','tags':['claude'],'importance':5}]
b={'headline_changes':['x'],'sangsang_ideas':[],'action_items':[],'summary_text':'s'}
md=build_report('2026-06-21','2026-06-01T00:00:00','2026-06-21T00:00:00',arts,b,'fallback',catchup=True,new_count=1)
print('AI 분석 실패 / 기본 리포트 생성' in md, '2026-06-01' in md)   # True True
print(save_report('reports','2026-06-21',md))
"
```

## 10. 금지 사항
- 수집/분석/전송 로직 포함 금지.
- Discord 2000자 메시지 생성 금지(FEAT-06).
- HTML/PDF 등 다른 출력 형식 추가 금지.
- 이 이슈 범위를 벗어나는 리팩터링 금지.
