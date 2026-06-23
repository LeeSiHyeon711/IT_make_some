# FEAT-04 — Claude 분석기 + fallback + 프로필 프롬프트

- 매칭 이슈: #4
- 작성일: 2026-06-21
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
수집된 기사를 Claude API로 분석해 기사별 중요도·상상공방 관련도·요약·태그와 전체 브리핑을 얻는다. operator_profile.md를 프롬프트의 핵심 입력(렌즈)으로 주입한다. Claude 실패/키 없음/**모델명 오류**/강제 시 규칙 기반 fallback로 강등해 파이프라인이 끊기지 않게 한다.

## 2. 범위
### 구현할 것
- `src/analyzer.py`: `analyze`, `_build_prompt`, `_call_claude`, `_parse_response`, `_fallback_analyze`.
- 모델명 오류·미존재 모델로 인한 API 예외를 잡아 fallback로 강등.
### 구현하지 않을 것
- DB 갱신(분석 결과를 dict로 반환만, 저장은 pipeline이 FEAT-02 호출).
- 리포트/Discord 텍스트 조립(FEAT-05/06).
- Claude Code headless 연동(V0.2).

## 3. 입력 / 출력
### 입력
- `articles: list[dict]` (설계서 3장 표준 형태), `operator_profile: str`, `api_key: str|None`, `model`, `max_output_tokens`, `max_articles`, `force_fallback: bool`.
### 출력
- `dict`:
```python
{
  "mode": "claude" | "fallback",
  "articles": [{"url","summary","tags":list,"importance":int,"relevance":int}],
  "briefing": {"headline_changes":[...], "sangsang_ideas":[...],
               "action_items":[...], "summary_text": str}
}
```

## 4. 동작 흐름
1. `force_fallback` 또는 `api_key` 없음 → 즉시 `_fallback_analyze` 반환(mode="fallback").
2. 아니면 `_build_prompt(articles[:max_articles], operator_profile)` (설계서 5-1).
3. `_call_claude`로 anthropic SDK 호출 → 응답 텍스트. **모델명이 잘못되면 SDK가 예외(NotFoundError/BadRequest 등)를 던지며, 이는 try/except에서 잡혀 fallback로 강등된다.**
4. `_parse_response`: 첫 `{`~마지막 `}` 슬라이스 후 `json.loads`. 누락 필드 기본값 보정.
5. 성공 → mode="claude" dict. 예외(API/네트워크/타임아웃/**모델명 오류**/파싱 실패) → `_fallback_analyze`(mode="fallback") + ERROR 로그.

## 5. 수정 예상 파일
- `05-개발/src/analyzer.py` (신규)

## 6. 데이터 구조 / 함수 / 클래스
```python
# src/analyzer.py
import json, logging, re

FALLBACK_KEYWORDS = ["claude","anthropic","openai","gpt","codex","mcp","agent",
                     "gemini","cursor","automation","github actions","n8n"]

def analyze(articles, operator_profile, api_key=None, model="claude-sonnet-4-5",
            max_output_tokens=4000, max_articles=40, force_fallback=False) -> dict:
    if force_fallback or not api_key:
        logging.warning("fallback 분석 사용 (force=%s, key=%s)", force_fallback, bool(api_key))
        return _fallback_analyze(articles)
    try:
        prompt = _build_prompt(articles[:max_articles], operator_profile)
        text = _call_claude(prompt, api_key, model, max_output_tokens)
        result = _parse_response(text, articles)
        result["mode"] = "claude"
        return result
    except Exception as ex:
        # API 오류/네트워크/타임아웃/모델명 오류(미존재 모델)/JSON 파싱 실패 모두 여기로
        logging.error("Claude 분석 실패 → fallback: %s", ex)
        return _fallback_analyze(articles)

def _build_prompt(articles, operator_profile) -> dict:
    """설계서 5-1. system/user 메시지 dict 반환."""
    items = [{"idx":i,"title":a["title"],"source":a["source"],
              "published_at":a.get("published_at"),"url":a["url"],
              "excerpt":(a.get("raw_excerpt") or "")[:800]} for i,a in enumerate(articles)]
    system = ("당신은 IT상상공방 운영자의 'AI 동향 분석가'다. 아래 <운영자 프로필>을 분석의 "
              "렌즈로 삼아 각 기사의 중요도와 상상공방 적용 가능성을 평가하라. 반드시 지정된 "
              "JSON 스키마만 출력하라.\n\n<운영자 프로필>\n" + operator_profile + "\n</운영자 프로필>")
    user = ("다음 기사들을 평가하고 전체 브리핑을 작성하라.\n<기사 목록 (JSON)>\n"
            + json.dumps(items, ensure_ascii=False)
            + "\n</기사 목록>\n\n출력 스키마:\n"
            '{"articles":[{"url":"<원본>","summary":"<한국어 2~3문장>","tags":[..],'
            '"importance":0,"relevance":0}],'
            '"briefing":{"headline_changes":[],"sangsang_ideas":[],"action_items":[],'
            '"summary_text":"<1800자 이내 한국어>"}}')
    return {"system": system, "user": user}

def _call_claude(prompt, api_key, model, max_tokens) -> str:
    # 모델명이 잘못되면 이 호출에서 예외 발생 → analyze의 except가 fallback로 강등
    from anthropic import Anthropic
    client = Anthropic(api_key=api_key)
    msg = client.messages.create(model=model, max_tokens=max_tokens,
        system=prompt["system"], messages=[{"role":"user","content":prompt["user"]}])
    return "".join(getattr(b,"text","") for b in msg.content)

def _parse_response(text, articles) -> dict:
    s, e = text.find("{"), text.rfind("}")
    data = json.loads(text[s:e+1])
    data.setdefault("articles", [])
    b = data.setdefault("briefing", {})
    for k in ("headline_changes","sangsang_ideas","action_items"):
        b.setdefault(k, [])
    b.setdefault("summary_text", "")
    for a in data["articles"]:
        a.setdefault("summary",""); a.setdefault("tags",[])
        a["importance"]=int(a.get("importance",0)); a["relevance"]=int(a.get("relevance",0))
    return data

def _fallback_analyze(articles) -> dict:
    out_articles = []
    for a in articles:
        ex = (a.get("raw_excerpt") or "").strip()
        sents = re.split(r'(?<=[.!?。])\s+', ex)
        summary = " ".join(sents[:2]) if ex else a["title"]
        text = (a["title"]+" "+ex).lower()
        tags = [k for k in FALLBACK_KEYWORDS if k in text]
        out_articles.append({"url":a["url"],"summary":summary,"tags":tags,
                             "importance":0,"relevance":0})
    hot = [a["title"] for a in articles
           if any(k in (a["title"]+" "+(a.get("raw_excerpt") or "")).lower()
                  for k in FALLBACK_KEYWORDS)][:5]
    briefing = {"headline_changes":hot,"sangsang_ideas":[],"action_items":[],
                "summary_text":("[기본 리포트] 신규 %d건. 키워드 매칭 주요 제목:\n- " % len(articles))
                               + "\n- ".join(hot) if hot else "[기본 리포트] 키워드 매칭 없음"}
    return {"mode":"fallback","articles":out_articles,"briefing":briefing}
```

## 7. 예외 처리
- api_key 없음/force_fallback → fallback(예외 아님).
- anthropic SDK 미설치/네트워크/타임아웃/**모델명 오류·미존재 모델(NotFound/BadRequest)**/JSON 파싱 실패 → 예외 잡아 fallback + ERROR 로그.
- 응답에 `{` 없거나 깨진 JSON → `_parse_response`에서 예외 → analyze가 잡아 fallback.
- 부분 누락 필드 → 기본값 보정(중단 없음).

## 8. 완료 조건
- 키 없이 `analyze(articles, profile, api_key=None)` 호출 시 mode="fallback", articles에 각 url별 summary/tags 채워짐.
- `force_fallback=True`면 무조건 fallback.
- **잘못된 model명으로 호출 시(키가 있더라도) 예외가 fallback로 흡수되어 mode="fallback" 반환, 파이프라인 중단 없음.**
- 정상 JSON 응답을 흉내낸 텍스트를 `_parse_response`에 넣으면 구조화 dict 반환.
- 반환 dict는 항상 mode/articles/briefing 키를 가진다.

## 9. 테스트 방법
```bash
cd projects/AI-Morning-Brief/05-개발
python -c "
from src.analyzer import analyze, _parse_response
arts=[{'url':'u1','title':'Claude Code 업데이트','source':'s','published_at':None,'raw_excerpt':'MCP 지원 강화. 두번째 문장.'}]
r=analyze(arts,'프로필',api_key=None)
print(r['mode'], r['articles'][0]['tags'])   # fallback ['claude','mcp']
# 잘못된 모델명 + 가짜 키 → 예외가 fallback로 흡수 (네트워크 시도 후 except)
r2=analyze(arts,'프로필',api_key='sk-fake',model='no-such-model-xyz')
print(r2['mode'])                             # fallback (중단 없음)
fake='{\"articles\":[{\"url\":\"u1\",\"summary\":\"요약\",\"tags\":[\"x\"],\"importance\":4,\"relevance\":3}],\"briefing\":{\"summary_text\":\"hi\"}}'
print(_parse_response(fake,arts)['briefing']['headline_changes'])   # [] (보정)
"
```

## 10. 금지 사항
- DB/파일/Discord 쓰기 금지(분석 결과 dict 반환만).
- 실제 API 키를 코드/문서에 적지 않는다.
- 모델명 오류를 치명 오류로 처리해 파이프라인을 중단시키지 않는다(반드시 fallback).
- Claude Code headless 등 다른 연동 방식 선구현 금지(V0.2).
- 이 이슈 범위를 벗어나는 리팩터링 금지.
