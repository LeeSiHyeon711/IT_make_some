"""LLM 호출 추상화 — 현재는 STUB(규칙기반, 결정적).

⚠️ 이 골격은 LLM을 호출하지 않는다. Inspector 통과를 우선하기 위해
   결정적(deterministic) 규칙기반 더미 결과를 반환한다.
   후속 단계에서 이 함수 3개의 본문만 Anthropic 호출로 교체하면 된다(시그니처 고정).
"""

from __future__ import annotations

from .schemas import Diagnosis, FirstExperiment, IntakeData, TimeBudget

_VALID_BUDGETS = {"30_MIN", "TODAY", "TWO_DAYS", "ONE_WEEK", "TWO_WEEKS_PLUS", "UNKNOWN"}

_BUDGET_LABEL = {
    "30_MIN": "30분 이내",
    "TODAY": "오늘 안에",
    "TWO_DAYS": "2일 이내",
    "ONE_WEEK": "1주일 이내",
    "TWO_WEEKS_PLUS": "2주 이상",
    "UNKNOWN": "미정",
}

# pain_source → 진단 각도(docs/05) 매핑
_FOCUS_BY_SOURCE = {
    "SELF": "SOLUTION_FIT",
    "OBSERVED": "PAIN_INTENSITY",
    "ASSUMED": "PROBLEM_EXISTENCE",
    "IMAGINED": "CONTEXT_OF_USE",
}

_CRACK_BY_SOURCE = {
    "SELF": "문제 존재가 아니라 '이 해결 방식이 빈도·강도에 맞는지'가 먼저 확인할 지점이다.",
    "OBSERVED": "관찰자의 해석과 당사자의 실제 불편이 일치하는지가 먼저 확인할 지점이다.",
    "ASSUMED": "그 문제를 실제로 겪는 사람이 존재하는지가 먼저 확인할 지점이다.",
    "IMAGINED": "사용 순간과 첫 사용자가 구체적으로 성립하는지가 먼저 확인할 지점이다.",
}


def prepare_intake(idea_text: str, time_budget: str = "UNKNOWN") -> IntakeData:
    """[STUB] 자유 서술을 접수 데이터로 구조화한다(규칙기반)."""
    text = (idea_text or "").strip()
    summary = (text[:120] + "…") if len(text) > 120 else (text or "(입력 없음)")

    lowered = text.lower()
    if "앱" in text or "app" in lowered:
        service_type = "앱"
    elif "웹" in text or "web" in lowered or "사이트" in text:
        service_type = "웹"
    elif "자동화" in text or "automat" in lowered:
        service_type = "자동화 도구"
    else:
        service_type = "기타"

    # 1인칭 단서가 있으면 직접 경험(SELF)로 추정 (그 외 IMAGINED 기본)
    pain_source = "SELF" if ("내가" in text or "제가" in text or "나는" in text) else "IMAGINED"

    budget = time_budget if time_budget in _VALID_BUDGETS else "UNKNOWN"

    return IntakeData(
        input_summary=summary,
        service_type=service_type,
        problem="(stub) 자유 서술에서 추출 예정",
        target_user="(stub) 자유 서술에서 추출 예정",
        pain_source=pain_source,  # type: ignore[arg-type]
        maturity="RAW",
        validation_time_budget=budget,  # type: ignore[arg-type]
        needs_clarification=False,
    )


def diagnose(intake: IntakeData) -> Diagnosis:
    """[STUB] 접수 데이터로 균열점 1개를 진단한다(규칙기반)."""
    source = intake.pain_source
    return Diagnosis(
        problem_statement=intake.problem or "(stub) 문제 정의 예정",
        target_user_assumption=f"'{intake.target_user or '미정'}'이(가) 이 도구를 실제로 쓸 것이다",
        context_of_use="(stub) 실제 사용 순간 정의 예정",
        crack_point=_CRACK_BY_SOURCE.get(source, _CRACK_BY_SOURCE["IMAGINED"]),
        misread_risks=["(stub) 착각 가능성 1", "(stub) 착각 가능성 2"],
        positive_signals=["(stub) 좋은 신호 1"],
        diagnosis_focus=_FOCUS_BY_SOURCE.get(source, "CONTEXT_OF_USE"),  # type: ignore[arg-type]
    )


def design(intake: IntakeData, diagnosis: Diagnosis) -> FirstExperiment:
    """[STUB] 균열점을 시간 예산에 맞춰 첫 검증 미션으로 설계한다(규칙기반)."""
    budget: TimeBudget = intake.validation_time_budget
    label = _BUDGET_LABEL.get(budget, "미정")

    if budget in ("30_MIN", "UNKNOWN"):
        steps = ["자가 점검 1가지", "주변 1~2명에게 질문", "기존 사례 1건 확인"]
    elif budget == "TODAY":
        steps = ["짧은 메시지로 3명에게 질문", "응답 소규모 수집", "수동 정리"]
    elif budget == "TWO_DAYS":
        steps = ["대상 사용자 3~10명 인터뷰/구글폼", "반응 수집", "수동 테스트"]
    elif budget == "ONE_WEEK":
        steps = ["작은 파일럿 운영", "반복 사용 확인", "결과 기록"]
    else:  # TWO_WEEKS_PLUS
        steps = ["노코드/수동 운영 파일럿", "반복 행동 확인", "지불 의향 확인"]

    return FirstExperiment(
        time_budget=label,
        mission_title=f"[STUB] '{diagnosis.crack_point[:24]}…'을(를) 가장 작게 확인하기",
        mission_steps=steps,
        success_criteria=["(stub) 핵심 전제가 사실이라는 신호가 보인다"],
        failure_signals=["(stub) 아무도 반응하지 않는다"],
        do_not_build_yet=["로그인/회원", "서버/DB", "예쁜 UI"],
        next_step_if_passed="(stub) 신호가 보이면 더 작은 다음 미션 또는 본 공방 1단계로",
    )
