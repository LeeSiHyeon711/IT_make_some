# FEAT-01 — 프로젝트 골격 + 설정/프로필 로더 + CLI 스켈레톤

- 매칭 이슈: #1
- 작성일: 2026-06-21
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
이후 모든 FEAT가 올라설 토대를 만든다. 폴더 구조, 의존성, 설정 파일(config.yaml / sources.yaml / sources.example.yaml / operator_profile.md / .env.example), 설정·소스·프로필·비밀값 로더, CLI 옵션 파싱 스켈레톤을 확정한다. operator_profile.md를 Claude 분석의 핵심 입력으로 다루기 위한 로딩·부재 정책, 소스 목록 파일 분리(+예시 fallback), 그리고 **테스트 격리를 위한 경로 env override**도 이 FEAT에서 정의한다.

## 2. 범위
### 구현할 것
- `05-개발/` 폴더 골격: `requirements.txt`, `.gitignore`, `main.py`, `src/__init__.py`, `config/`, `data/`, `reports/`, `tests/fixtures/`, `scripts/` (빈 폴더는 `.gitkeep`).
- `config/config.yaml` (설계서 4-1 내용 — **소스 목록 없음**, paths에 env override 주석 포함).
- `config/sources.example.yaml` (설계서 4-2 내용, enabled 필드 포함).
- `config/operator_profile.md` (설계서 6장 기본 템플릿 내용).
- `config/.env.example` (설계서 4-3 내용).
- `src/config_loader.py`: `load_config`(+ 경로 env override), `load_sources`, `load_secrets`, `load_operator_profile`, `DEFAULT_PROFILE`.
- `main.py`: argparse로 `--test --dry-run --no-discord --force-fallback --from --to --check-sources` 정의 후 `pipeline.run(args)` 호출(pipeline 미구현 시 ImportError를 잡아 옵션 출력 — 7장).
- `.gitignore`: `data/`, `reports/`, `.env`, `config/sources.yaml`, `__pycache__/`.

### 구현하지 않을 것
- 실제 수집/저장/분석/리포트/전송/진단 로직 (FEAT-02~07).
- `config/sources.yaml` 실파일 자체는 커밋하지 않는다(운영자가 example을 복사). 로더만 만든다.
- pipeline.run 본문 (FEAT-07). 여기서는 호출 배선만.

## 3. 입력 / 출력
### 입력
- 없음 (신규 골격). 설계서의 설정 파일 명세. 선택적으로 환경변수 `MORNINGBRIEF_DB` / `MORNINGBRIEF_RAW_DIR` / `MORNINGBRIEF_REPORTS_DIR`.
### 출력
- 위 파일·폴더 일체. `python main.py --help`가 7개 옵션을 보여준다.
- `config_loader`가 dict/list/str/secrets를 반환. `load_config`는 env override가 적용된 paths를 가진 dict를 반환.

## 4. 동작 흐름
1. 폴더·파일 골격 생성.
2. `load_config(path)`: YAML 파싱 → dict → `_apply_path_overrides(cfg)`로 env override 적용 → 반환.
3. `load_sources(path)`: `sources.yaml` 읽기; 없으면 `sources.example.yaml` 읽고 WARNING; 둘 다 없으면 빈 리스트 + ERROR 로그. `sources` 키 아래 리스트 반환. `enabled` 기본 True.
4. `load_secrets()`: `.env`가 있으면 KEY=VALUE 라인 파싱해 os.environ 보강 후, 환경변수에서 읽음. 키 없으면 None.
5. `load_operator_profile(path)`: 파일 읽어 `(text, is_default)` 반환. 없거나 빈 문자열 → WARNING + `(DEFAULT_PROFILE, True)`.
6. `main.py`: argparse 구성 → `args` → `pipeline.run(args)` (없으면 안내 출력).

## 5. 수정 예상 파일
- `05-개발/requirements.txt` (신규)
- `05-개발/.gitignore` (신규)
- `05-개발/main.py` (신규)
- `05-개발/src/__init__.py` (신규)
- `05-개발/src/config_loader.py` (신규)
- `05-개발/config/config.yaml` (신규)
- `05-개발/config/sources.example.yaml` (신규)
- `05-개발/config/operator_profile.md` (신규)
- `05-개발/config/.env.example` (신규)
- `05-개발/data/.gitkeep`, `reports/.gitkeep`, `tests/fixtures/.gitkeep`, `scripts/.gitkeep`

## 6. 데이터 구조 / 함수 / 클래스
```python
# requirements.txt
# feedparser>=6.0
# requests>=2.31
# PyYAML>=6.0
# anthropic>=0.40
# python-dateutil>=2.9

# src/config_loader.py
import os, yaml, logging

DEFAULT_PROFILE: str = """# 운영자 프로필 — IT상상공방 (기본값)
... (설계서 6장 기본 템플릿 본문) ..."""

# 경로 env override: 테스트가 운영 데이터를 건드리지 않게 하는 격리 수단
_PATH_ENV = {
    "db": "MORNINGBRIEF_DB",
    "raw_dir": "MORNINGBRIEF_RAW_DIR",
    "reports_dir": "MORNINGBRIEF_REPORTS_DIR",
}

def _apply_path_overrides(cfg: dict) -> dict:
    paths = cfg.setdefault("paths", {})
    for key, env in _PATH_ENV.items():
        val = os.environ.get(env)
        if val:
            paths[key] = val
            logging.info("경로 override: %s = %s (env %s)", key, val, env)
    return cfg

def load_config(path: str = "config/config.yaml") -> dict:
    with open(path, "r", encoding="utf-8") as f:
        cfg = yaml.safe_load(f)
    return _apply_path_overrides(cfg)

def load_sources(path: str = "config/sources.yaml") -> list[dict]:
    """sources.yaml 우선, 없으면 sources.example.yaml fallback(+WARNING). 둘 다 없으면 [] + ERROR."""
    candidates = [path, "config/sources.example.yaml"]
    for i, p in enumerate(candidates):
        if os.path.exists(p):
            if i > 0:
                logging.warning("sources.yaml 없음 — %s 사용. cp config/sources.example.yaml config/sources.yaml 권장", p)
            with open(p, "r", encoding="utf-8") as f:
                data = yaml.safe_load(f) or {}
            srcs = data.get("sources", []) or []
            for s in srcs:
                s.setdefault("enabled", True)
            return srcs
    logging.error("소스 설정 파일이 없습니다 (%s)", candidates)
    return []

def load_secrets() -> dict:
    if os.path.exists(".env"):
        for line in open(".env", encoding="utf-8"):
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1); os.environ.setdefault(k.strip(), v.strip())
    return {
        "ANTHROPIC_API_KEY": os.environ.get("ANTHROPIC_API_KEY"),
        "DISCORD_WEBHOOK_URL": os.environ.get("DISCORD_WEBHOOK_URL"),
    }

def load_operator_profile(path: str = "config/operator_profile.md") -> tuple[str, bool]:
    try:
        text = open(path, "r", encoding="utf-8").read().strip()
        if not text:
            logging.warning("operator_profile 비어있음 — 기본 프로필 사용")
            return DEFAULT_PROFILE, True
        return text, False
    except FileNotFoundError:
        logging.warning("operator_profile 없음 — 기본 프로필 사용")
        return DEFAULT_PROFILE, True
```
```python
# main.py
import argparse, logging
def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="AI-Morning-Brief 파이프라인")
    p.add_argument("--test", action="store_true")
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--no-discord", action="store_true")
    p.add_argument("--force-fallback", action="store_true")
    p.add_argument("--from", dest="from_dt", default=None)
    p.add_argument("--to", dest="to_dt", default=None)
    p.add_argument("--check-sources", action="store_true",
                   help="RSS 소스 접근성 진단만 실행하고 종료")
    return p

def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    args = build_parser().parse_args()
    try:
        from src.pipeline import run
    except ImportError:
        print("pipeline 미구현 (FEAT-07 예정). 파싱된 옵션:", vars(args)); return 0
    return run(args)

if __name__ == "__main__":
    raise SystemExit(main())
```

## 7. 예외 처리
- config.yaml 없음 → 명확한 FileNotFoundError 메시지로 즉시 종료(설정은 필수).
- sources.yaml/example 둘 다 없음 → 빈 리스트 + ERROR 로그(파이프라인은 수집 0건으로 진행 가능, 중단 아님).
- operator_profile 없음 → 중단하지 않고 DEFAULT_PROFILE + WARNING.
- env override 미설정 → config.yaml 기본 경로 사용(정상).
- pipeline 미구현 단계 → main.py가 ImportError를 잡아 옵션만 출력(이 FEAT 단독 실행 가능).

## 8. 완료 조건
- `python main.py --help`가 7개 옵션(`--check-sources` 포함)을 출력한다.
- `python main.py --test`가 (pipeline 없어도) ImportError 없이 옵션 dict를 출력하고 종료코드 0.
- `python -c "from src.config_loader import load_config; print(load_config())"`가 dict를 출력.
- `MORNINGBRIEF_DB=/tmp/x.db python -c "from src.config_loader import load_config; print(load_config()['paths']['db'])"` 가 `/tmp/x.db`를 출력(override 동작).
- `load_sources()`가 sources.yaml 없을 때 example을 읽고 enabled 필드가 채워진 리스트 반환 + WARNING.
- operator_profile.md 삭제 시 `load_operator_profile()`이 `(DEFAULT_PROFILE, True)` 반환 + WARNING.

## 9. 테스트 방법
```bash
cd projects/AI-Morning-Brief/05-개발
pip install -r requirements.txt
python main.py --help
python -c "from src.config_loader import *; print(type(load_config())); print(len(load_sources())); print(load_operator_profile()[1]); print(load_secrets().keys())"
MORNINGBRIEF_DB=/tmp/x.db python -c "from src.config_loader import load_config; print(load_config()['paths']['db'])"  # /tmp/x.db
```

## 10. 금지 사항
- 수집/저장/분석/리포트/전송/진단 로직 선구현 금지(다른 FEAT 범위).
- 비밀값을 config.yaml이나 코드에 하드코딩 금지.
- `config/sources.yaml` 실파일을 커밋 금지(example만 커밋).
- 불필요한 프레임워크(Flask/FastAPI 등) 추가 금지 — 헤드리스 CLI다.
- 이 이슈 범위를 벗어나는 리팩터링 금지.
