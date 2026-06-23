# FEAT-06 — Discord Webhook 전송기

- 매칭 이슈: #6
- 작성일: 2026-06-21
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
Claude(또는 fallback) 브리핑과 메타 정보를 2000자 이내 Discord 메시지로 조립해 Webhook으로 단방향 전송한다. Webhook 없음/전송 실패는 파이프라인을 중단시키지 않고 로그만 남긴다(설계서 7·8장).

## 2. 범위
### 구현할 것
- `src/notifier.py`: `build_briefing_message`(≤2000자 문자열), `send_discord`(POST, bool 반환).
### 구현하지 않을 것
- 양방향 Bot, 파일 첨부, 분할 전송(V0.2). 2000자 초과는 절삭 마커로 처리.
- 리포트 본문 생성(FEAT-05).

## 3. 입력 / 출력
### 입력
- `build_briefing_message`: `date`, `range_start`, `range_end`, `new_count`, `briefing`(FEAT-04), `mode`, `catchup`, `failed_sources`, `report_path`, `profile_is_default`, `char_limit`(기본 2000), `truncate_marker`.
- `send_discord`: `webhook_url: str|None`, `message: str`.
### 출력
- `build_briefing_message` → 문자열(길이 ≤ char_limit).
- `send_discord` → bool(성공 True / 실패·webhook없음 False).

## 4. 동작 흐름
1. 메시지 조립(설계서 7장): 날짜 / 수집범위 / 신규건수 / 핵심변화 3~5 / 상상공방 적용 유무 / catch-up·fallback·프로필부재 안내 / 로컬 리포트 경로.
2. 길이 > char_limit → `char_limit - len(marker)`에서 잘라 marker 부착.
3. `send_discord`: webhook 없으면 WARNING 후 False. 있으면 `requests.post(url, json={"content":message}, timeout)`; 2xx면 True, 아니면 ERROR 로그 후 False.

## 5. 수정 예상 파일
- `05-개발/src/notifier.py` (신규)

## 6. 데이터 구조 / 함수 / 클래스
```python
# src/notifier.py
import logging, requests

def build_briefing_message(date, range_start, range_end, new_count, briefing,
                           mode="claude", catchup=False, failed_sources=None,
                           report_path="", profile_is_default=False,
                           char_limit=2000,
                           truncate_marker="…(이하 생략, 전체는 로컬 리포트 참조)") -> str:
    failed_sources = failed_sources or []
    lines = [f"**AI Morning Brief — {date}**"]
    if mode == "fallback":
        lines.append("⚠ AI 분석 실패 / 기본 리포트 생성")
    if catchup:
        lines.append(f"↻ 보완 수집(catch-up): {range_start} ~ {range_end}")
    if profile_is_default:
        lines.append("⚠ 운영자 프로필 없음 — 기본 프로필로 분석")
    lines.append(f"수집 범위: {range_start} ~ {range_end}")
    lines.append(f"신규 수집: {new_count}건")
    hc = briefing.get("headline_changes", [])[:5]
    if hc:
        lines.append("\n__오늘의 핵심 변화__")
        lines += [f"• {x}" for x in hc]
    ideas = briefing.get("sangsang_ideas", [])
    lines.append("\n__상상공방 적용__: " + ("\n• " + "\n• ".join(ideas) if ideas else "해당 없음"))
    if failed_sources:
        lines.append(f"\n수집 실패 소스: {[f['source'] for f in failed_sources]}")
    if report_path:
        lines.append(f"\n📄 리포트: {report_path}")
    msg = "\n".join(lines)
    if len(msg) > char_limit:
        msg = msg[: char_limit - len(truncate_marker)] + truncate_marker
    return msg

def send_discord(webhook_url, message) -> bool:
    if not webhook_url:
        logging.warning("DISCORD_WEBHOOK_URL 없음 — 전송 생략"); return False
    try:
        r = requests.post(webhook_url, json={"content": message}, timeout=15)
        if r.status_code // 100 == 2:
            return True
        logging.error("Discord 전송 실패 HTTP %s: %s", r.status_code, r.text[:200])
        return False
    except Exception as ex:
        logging.error("Discord 전송 예외: %s", ex); return False
```

## 7. 예외 처리
- webhook_url None/빈값 → WARNING + False(중단 없음).
- POST 예외/비2xx → ERROR 로그 + False. 파이프라인 성공 판정에 영향 없음(설계서 8장).
- 메시지 2000자 초과 → 절삭 마커.

## 8. 완료 조건
- `build_briefing_message`가 항상 길이 ≤ char_limit 문자열 반환.
- mode="fallback"이면 메시지에 AI 분석 실패 안내 포함.
- webhook None일 때 `send_discord` False 반환하고 예외를 던지지 않음.
- catch-up/실패소스/리포트 경로가 메시지에 반영.

## 9. 테스트 방법
```bash
cd projects/AI-Morning-Brief/05-개발
python -c "
from src.notifier import build_briefing_message, send_discord
b={'headline_changes':['a','b','c'],'sangsang_ideas':['아이디어'],'action_items':[],'summary_text':''}
m=build_briefing_message('2026-06-21','s','e',3,b,mode='fallback',report_path='reports/2026-06-21/report.md')
print(len(m)<=2000, 'AI 분석 실패' in m)        # True True
print(send_discord(None, m))                     # False (예외 없음)
"
```

## 10. 금지 사항
- 전송 실패 시 예외를 위로 던져 파이프라인을 중단시키지 않는다.
- Webhook URL을 코드/문서에 하드코딩 금지(환경변수만).
- 분할 전송·파일 첨부·양방향 Bot 추가 금지(V0.2).
- 이 이슈 범위를 벗어나는 리팩터링 금지.
