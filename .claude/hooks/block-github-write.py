#!/usr/bin/env python3
"""
IT상상공방 GitHub 쓰기 차단 가드 (PreToolUse 훅).

builder(5단계)·reviewer(6단계) 에이전트에만 스코프된다.
이 에이전트들은 GitHub 쓰기 권한이 없다 — git push / gh 명령을 물리적으로 차단한다.
GitHub 상태 변경은 오직 repo-manager(4단계)만 수행한다.

차단 시 exit 2 + stderr 메시지로 도구 호출을 막고 모델에 사유를 되돌린다.
"""
import json
import re
import sys

try:
    data = json.load(sys.stdin)
except Exception:
    # 입력 파싱 실패 시 통과 (가드는 안전 측 실패 대신 비차단)
    sys.exit(0)

cmd = (data.get("tool_input") or {}).get("command", "") or ""

# git push (모든 형태) 또는 gh 명령(모든 서브커맨드) 차단
blocked = re.search(r"\bgit\s+push\b", cmd) or re.search(r"\bgh\b", cmd)

if blocked:
    sys.stderr.write(
        "⛔ 이 에이전트는 GitHub 쓰기 권한이 없습니다.\n"
        "git push / gh 명령은 4단계 GitHub 관리자(repo-manager)만 수행합니다.\n"
        "진행 상태는 직접 push/close 하지 말고 GitHub 관리자에게 '보고'하세요. "
        "(공방 헌법 2장: GitHub 쓰기 단독 관리)\n"
    )
    sys.exit(2)

sys.exit(0)
