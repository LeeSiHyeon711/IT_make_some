# FEAT-02 — 저장 계층 (SQLite)

- 매칭 이슈: #2
- 작성일: 2026-06-21
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
기사·메타 데이터를 SQLite에 저장하는 계층을 만든다. URL 기준 중복 제거, 분석 결과 갱신, catch-up 기준 시각(meta) 관리를 담당한다. Claude/Discord 성공 여부와 무관하게 데이터가 남도록 저장을 책임진다.

## 2. 범위
### 구현할 것
- `src/storage.py`: 스키마 init, 기사 삽입(중복 무시), 분석 결과 갱신, 범위 조회, meta get/set.
### 구현하지 않을 것
- 기사 수집 자체 (FEAT-03), 분석 (FEAT-04), 리포트/전송.

## 3. 입력 / 출력
### 입력
- 기사 dict (설계서 3장 표준 형태), 분석 결과 dict, meta 키/값.
- db 경로 (config.yaml: paths.db).
### 출력
- `data/morning_brief.db` 파일 + articles/meta 테이블.
- 삽입 성공 여부(bool), 조회 결과(list[dict]), meta 값(str|None).

## 4. 동작 흐름
1. `init_db(db_path)`: 디렉토리 없으면 생성 → 연결 → 설계서 3장 DDL 실행(CREATE IF NOT EXISTS) → conn 반환.
2. `insert_article(conn, article)`: `INSERT OR IGNORE` → `rowcount==1`이면 True(신규), 0이면 False(중복).
3. `update_analysis(conn, url, summary, tags, importance, relevance, analyzed)`: UPDATE. tags는 list → `json.dumps`.
4. `get_articles_by_range(conn, start, end)`: collected_at 또는 published_at 기준 조회 → dict 리스트(tags는 `json.loads`).
5. `get_meta/set_meta`: meta 테이블 key-value (`INSERT ... ON CONFLICT DO UPDATE`).

## 5. 수정 예상 파일
- `05-개발/src/storage.py` (신규)

## 6. 데이터 구조 / 함수 / 클래스
```python
# src/storage.py
import os, json, sqlite3
from typing import Optional

SCHEMA = """ ... 설계서 3장 DDL 전체 (articles, meta, 인덱스) ... """

def init_db(db_path: str) -> sqlite3.Connection:
    os.makedirs(os.path.dirname(db_path) or ".", exist_ok=True)
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    conn.executescript(SCHEMA)
    conn.commit()
    return conn

def insert_article(conn, a: dict) -> bool:
    cur = conn.execute(
        """INSERT OR IGNORE INTO articles
           (url,title,published_at,source,collected_at,raw_excerpt)
           VALUES(?,?,?,?,?,?)""",
        (a["url"], a["title"], a.get("published_at"), a["source"],
         a["collected_at"], a.get("raw_excerpt","")))
    conn.commit()
    return cur.rowcount == 1

def update_analysis(conn, url, summary, tags, importance, relevance, analyzed) -> None:
    conn.execute(
        """UPDATE articles SET summary=?,tags=?,importance=?,relevance=?,analyzed=?
           WHERE url=?""",
        (summary, json.dumps(tags, ensure_ascii=False),
         int(importance), int(relevance), int(analyzed), url))
    conn.commit()

def get_articles_by_range(conn, start: str, end: str) -> list[dict]:
    rows = conn.execute(
        """SELECT * FROM articles WHERE collected_at BETWEEN ? AND ?
           ORDER BY importance DESC, published_at DESC""", (start, end)).fetchall()
    out = []
    for r in rows:
        d = dict(r)
        d["tags"] = json.loads(d["tags"]) if d.get("tags") else []
        out.append(d)
    return out

def get_meta(conn, key: str, default=None) -> Optional[str]:
    row = conn.execute("SELECT value FROM meta WHERE key=?", (key,)).fetchone()
    return row["value"] if row else default

def set_meta(conn, key: str, value: str) -> None:
    conn.execute(
        "INSERT INTO meta(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
        (key, str(value)))
    conn.commit()
```

## 7. 예외 처리
- DB 쓰기 오류(디스크/락) → 예외를 그대로 올려 pipeline이 중단·로그·종료코드 1 처리(설계서 8장: last_success 미갱신 → 다음 실행 catch-up).
- 중복 URL → 예외 아님, `insert_article`이 False 반환.
- tags None/빈 문자열 조회 → 빈 리스트로 정규화.

## 8. 완료 조건
- `init_db`가 articles/meta 테이블과 인덱스를 만든다.
- 같은 URL 두 번 insert → 첫 번째 True, 두 번째 False, 테이블에는 1행만.
- `set_meta`/`get_meta` 왕복이 일치.
- `get_articles_by_range`가 dict 리스트(tags는 list) 반환.

## 9. 테스트 방법
```bash
cd projects/AI-Morning-Brief/05-개발
python -c "
from src.storage import *
c=init_db('data/test.db')
a={'url':'u1','title':'t','published_at':'2026-06-21T00:00:00','source':'s','collected_at':'2026-06-21T01:00:00','raw_excerpt':'x'}
print(insert_article(c,a), insert_article(c,a))   # True False
set_meta(c,'last_success_at','2026-06-20T04:30:00'); print(get_meta(c,'last_success_at'))
print(len(get_articles_by_range(c,'2026-06-01T00:00:00','2026-06-30T00:00:00')))
"
rm -f data/test.db
```

## 10. 금지 사항
- ORM(SQLAlchemy 등) 도입 금지 — 표준 sqlite3로 충분.
- 다른 FEAT 로직(수집/분석/리포트) 선구현 금지.
- 보관 기간 삭제 로직 추가 금지(V0.1은 전량 보관).
- 이 이슈 범위를 벗어나는 리팩터링 금지.
