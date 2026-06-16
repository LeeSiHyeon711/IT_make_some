// [2026-06-14 핫픽스] 이 파일 내용은 index.html의 <script>로 인라인됨(더블클릭 file:// 호환). 더 이상 index.html이 참조하지 않음 — 원본 보존용.
// script.js — D-Day 로직
// 이슈 #3: calcDday 구현 / 이슈 #4: formatLabel 구현 / 이슈 #5: render() + 즉시 갱신 바인딩

/**
 * 두 날짜의 일수 차를 정수로 반환한다.
 * 로컬 연·월·일(getFullYear/getMonth/getDate)만 취해 자정으로 정규화한 뒤
 * 차이를 계산하므로 타임존 경계로 인한 ±1일 오차가 발생하지 않는다.
 *
 * @param {Date} targetDate 목표 날짜 (Date 객체)
 * @param {Date} today       기준이 되는 오늘 날짜 (Date 객체)
 * @returns {number} 남은 일수. 미래>0, 당일=0, 과거<0
 */
function calcDday(targetDate, today) {
  const MS_PER_DAY = 24 * 60 * 60 * 1000;
  // 각 날짜의 로컬 연/월/일만으로 자정 기준 시각을 재구성 (시·분·초·ms = 0)
  const target = new Date(
    targetDate.getFullYear(),
    targetDate.getMonth(),
    targetDate.getDate()
  );
  const base = new Date(
    today.getFullYear(),
    today.getMonth(),
    today.getDate()
  );
  // 두 자정 시각의 차를 일 단위로 환산해 반올림(서머타임 등 1시간 오차 흡수)
  return Math.round((target - base) / MS_PER_DAY);
}

/**
 * 일수 차(diff)와 이벤트명을 받아 화면에 표시할 문자열을 만든다.
 * 라벨 규칙(설계서 4장):
 *   diff > 0  → "D-{diff}"     (미래, 남은 날)
 *   diff === 0 → "D-0"          (당일)
 *   diff < 0  → "D+{|diff|}"    (과거, 지난 날)
 * 이벤트명이 있으면 "{이벤트명} {라벨}"로 접두를 붙이고, 없으면 라벨만 반환한다.
 *
 * @param {number} diff       calcDday가 반환한 일수 차(정수)
 * @param {string} [eventName] 이벤트 이름 (선택, 비어 있으면 라벨만)
 * @returns {string} 표시 문자열
 */
function formatLabel(diff, eventName) {
  let label;
  if (diff > 0) {
    label = "D-" + diff;          // 미래
  } else if (diff === 0) {
    label = "D-0";                // 당일
  } else {
    label = "D+" + Math.abs(diff); // 과거
  }

  // 이벤트명 앞뒤 공백 제거 후 값이 있으면 접두로 붙인다.
  const name = (eventName || "").trim();
  return name ? name + " " + label : label;
}

// ── 이슈 #5: render() + 즉시 갱신 바인딩 ─────────────────────────────
// 설계서 3장: 버튼 없이 input/change 이벤트로 실시간 갱신.
// 설계서 4장: render()는 입력값을 읽어 calcDday → formatLabel을 호출하고
//             결과 영역(#result)에 출력한다. 날짜 미입력 시 안내 문구.

// 안내 문구(날짜 미입력 시 표시) — index.html 초기 텍스트와 동일
const PLACEHOLDER_TEXT = "날짜를 선택하세요";

/**
 * 현재 입력값을 읽어 결과 영역을 갱신한다.
 * - 목표 날짜가 비어 있으면 안내 문구를 표시한다.
 * - 날짜가 있으면 오늘 기준으로 calcDday → formatLabel을 호출해 라벨을 출력한다.
 * - input type=date의 value는 "YYYY-MM-DD" 문자열이므로 연/월/일로 분해해
 *   로컬 타임존 Date로 구성한다(new Date("YYYY-MM-DD")의 UTC 파싱 오차 방지).
 */
function render() {
  const eventNameEl = document.getElementById("eventName");
  const targetDateEl = document.getElementById("targetDate");
  const resultEl = document.getElementById("result");

  const dateValue = targetDateEl.value; // "" 또는 "YYYY-MM-DD"

  // 날짜 미입력 → 안내 문구로 복귀
  if (!dateValue) {
    resultEl.textContent = PLACEHOLDER_TEXT;
    return;
  }

  // "YYYY-MM-DD" → 로컬 자정 Date (UTC 파싱 오차 방지를 위해 직접 분해)
  const [year, month, day] = dateValue.split("-").map(Number);
  const targetDate = new Date(year, month - 1, day);

  const diff = calcDday(targetDate, new Date());
  resultEl.textContent = formatLabel(diff, eventNameEl.value);
}

// 입력 즉시 갱신: 이름은 타이핑(input), 날짜는 선택(change)+input 모두 바인딩.
function bindEvents() {
  const eventNameEl = document.getElementById("eventName");
  const targetDateEl = document.getElementById("targetDate");

  eventNameEl.addEventListener("input", render);
  targetDateEl.addEventListener("input", render);
  targetDateEl.addEventListener("change", render);

  // 초기 1회 렌더(미입력 상태 안내 문구 보장)
  render();
}

// DOM 준비 후 바인딩 (script가 body 끝에 있어 즉시 실행도 안전하지만 방어적으로 처리)
if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", bindEvents);
} else {
  bindEvents();
}
