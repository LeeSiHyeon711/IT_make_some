"""상상공방 Lite 도구 입출력 모델 (pydantic).

FastMCP는 이 타입에서 tools/list의 inputSchema / outputSchema를 자동 생성한다.
기획 근거: docs/03(소통)·05(진단)·06(첫실험).
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

# --- enum 류 (Literal로 두면 schema에 enum으로 노출) ---
ServiceType = Literal["웹", "앱", "자동화 도구", "업무 개선 도구", "기타"]
PainSource = Literal["SELF", "OBSERVED", "ASSUMED", "IMAGINED"]
Maturity = Literal["RAW", "SITUATION", "PROBLEM", "SOLUTION"]
TimeBudget = Literal["30_MIN", "TODAY", "TWO_DAYS", "ONE_WEEK", "TWO_WEEKS_PLUS", "UNKNOWN"]
DiagnosisFocus = Literal[
    "PROBLEM_EXISTENCE",
    "PAIN_INTENSITY",
    "SOLUTION_FIT",
    "WILLINGNESS",
    "FEASIBILITY",
    "CONTEXT_OF_USE",
    "OPERATION_FIT",
    "PROBLEM_CAUSE_FIT",
]


class IntakeData(BaseModel):
    """소통 에이전트 접수 결과 (docs/03)."""

    input_summary: str = Field(description="아이디어를 한두 문장으로 요약")
    service_type: ServiceType = Field(default="기타", description="결과물 형태 분류")
    problem: str = Field(default="", description="해결하려는 문제")
    target_user: str = Field(default="", description="처음 쓸 사람")
    pain_source: PainSource = Field(default="IMAGINED", description="불편함의 출처")
    maturity: Maturity = Field(default="RAW", description="아이디어 성숙도")
    validation_time_budget: TimeBudget = Field(default="UNKNOWN", description="검증 투자 가능 시간")
    needs_clarification: bool = Field(default=False, description="추가 질문 필요 여부")


class Diagnosis(BaseModel):
    """아이디어 진단 결과 — 균열점 1개 (docs/05)."""

    problem_statement: str
    target_user_assumption: str
    context_of_use: str
    crack_point: str = Field(description="가장 먼저 깨질 전제 1개")
    misread_risks: list[str] = Field(default_factory=list, description="착각 가능성 (최대 2개)")
    positive_signals: list[str] = Field(default_factory=list, description="좋은 신호 (최대 2개)")
    diagnosis_focus: DiagnosisFocus


class FirstExperiment(BaseModel):
    """첫 검증 미션 (docs/06)."""

    time_budget: str = Field(description="사용자가 선택한 시간(표시용)")
    mission_title: str
    mission_steps: list[str]
    success_criteria: list[str]
    failure_signals: list[str]
    do_not_build_yet: list[str] = Field(description="지금 만들지 않아도 되는 것")
    next_step_if_passed: str
