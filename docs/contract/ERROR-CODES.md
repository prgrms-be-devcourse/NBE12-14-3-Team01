# ErrorCode 공통 목록

| 축 | 상태 |
|---|---|
| ③ 문서 | `DRAFT` (S2-4A 초안, 2026-10-25) |
| ② 정책 | 기존 code는 **AS-IS 기록**이다. 점검 결과의 정정 방향과 새 code 예약은 [G03-D-03](../decisions/proposals/G03-D-03-error-code.md)에 따른 **제안 (`PROPOSED`)**이다. 팀 승인 전 |
| 구현 | 없음. Enum을 바꾸지 않았다. Kotlin Migration은 기존 code·HTTP·Message를 그대로 옮긴다 |
| ④ / ⑤ | 관련 Test 전부 `SPECIFIED` / `NOT_RUN` |
| 기준 코드 | 3차 `develop` `a148a62` (Production Java = `cdaaa13`). 정적 확인 |

새 code를 쓰기 전에 **이 목록에 먼저 번호를 예약**한다. 여러 담당자가 같은 번호를 쓰지 않게 하기 위해서다 (G03-D-03 §6).

---

## 1. 만든 방법과 한계

- `implements ErrorCode`인 Enum 9개에서 선언을 모두 뽑았다: **87개** (AUTH 6, WPL 5, SHIFT 9, SCH 12, SFT 19, UNA 7, SUB 23, NOT 2, COM 4). code 문자열 중복 0.
- 각 Enum 상수의 **실제 호출 위치**를 Production Java 전체에서 찾았다 (`클래스.상수` 참조). 선언만 있고 쓰이지 않는 code는 **0개**다. 사용처가 2곳 이상인 code는 23개다.
- 사용처마다 문구가 그 상황에 맞는지 **직접 읽고 판단**했다. 사용처가 여러 곳인 code와 이전에 의심된 code는 코드 문맥까지 확인했다.
- "Test 참조 수"는 Test 코드에서 Enum 상수 참조와 code 문자열 등장 횟수를 합친 **근사값**이다. 그 code를 실제로 검증하는 Test 수와 같지 않다.
- 메서드 이름은 자동 추출이라 틀릴 수 있다. **줄 번호가 기준**이다.
- 정적 확인이다. 실행해서 응답을 확인하지 않았다.

## 2. 점검 결과 요약

### 2-1. T1 잘못된 안내 (기본 P1, 사용자 흐름을 막으면 P0로 재평가)

| ID | Code | 상황 | 문제 | 정정 방향 (제안) |
|---|---|---|---|---|
| T1-01 | `SFT-006` | 시작된 근무의 대타 **승인** | 입력 형식 오류 code를 업무 상태 충돌에 사용 | 승인 호출 지점만 새 code `SUB-024` (§4) |
| T1-02 | `SUB-012` | 시작된 근무의 **후보 응답** | "요청할 수 없습니다" | 응답 호출 지점만 기존 `SUB-023`("더 이상 응답할 수 없는 대타 요청입니다") 재사용 |
| **T1-03 (새로 발견)** | `SUB-014` | 취소된 근무의 **후보 응답** | "취소된 근무에는 대체근무를 **요청할 수 없습니다**" | T1-02와 같이 `SUB-023` 재사용 (의미·HTTP·다음 행동이 같음: 응답 불가, 409) |
| **T1-04 (새로 발견)** | `SUB-006` | 대타 **승인**에서 수락자의 불가능 시간 충돌 | 관리자에게 "**등록한** 불가능 시간과 중복됩니다" | **문구만 "불가능 시간과 겹칩니다"로 중립화 (사용자 잠정 결정 2026-10-25, §6-2)** |
| **T1-05 (새로 발견)** | `SFT-005` | 취소된 근무의 **대타 종료** | "잘못된 상태입니다" (같은 상황의 승인은 `SFT-013` "취소되었습니다") | 종료 호출 지점을 `SFT-013`과 같은 의미의 code로. ML-3 적용 후에는 도달하기 어려워 **P1 낮음** |

### 2-2. 한 code가 두 의미 (M) — 새로 발견

| ID | Code | 의미 1 | 의미 2 | 영향 |
|---|---|---|---|---|
| M-01 | `SHIFT-004` (400) | 대상 직원이 그 근무지 소속이 아님 (`getActiveMember` L159) | **실행자**가 그 근무지 소속이 아님 (`requireManager` L245) | Pattern API는 자체 관리자 검사를 쓴다. 그래서 실행자 권한 실패가 **400 `SHIFT-004`·`SHIFT-005`, 403 `SHIFT-009`**로 나가고 다른 API의 `WPL-003`·`WPL-004`와 다르다. Frontend는 code만 보고 "실행자 권한 실패"와 "대상 입력 오류"를 구별할 수 없다 |
| M-01 | `SHIFT-005` (400) | 대상 직원이 퇴사함 (L165) | **실행자**가 퇴사함 (L251) | 같음 |

**FRONTEND-CONTRACT §9-3과의 관계:** 그 문서는 "403 `WPL-003`만 소속을 잃은 것으로 해석"한다. Pattern API는 `WPL-003`을 쓰지 않는다. 그러나 Pattern API는 관리자 전용이고 MANAGER는 이번 범위에서 퇴사 대상이 아니므로(ML-H2 H2-9), **퇴사로 인해 이 경로를 만나는 경우는 지금 없다.** 정정은 P1 후보로 기록한다: Pattern API의 실행자 검사를 공통 `requireManager`(`WPL-003`·`WPL-004`)로 맞추고 `SHIFT-004`·`SHIFT-005`는 대상 검증에만 남긴다 (기존 응답 변경, 승인 대상).

### 2-3. 잠재 문제 (L, 지금은 도달 불가)

| ID | Code | 내용 |
|---|---|---|
| L-01 | `AUTH-004` (401 "로그인 후 이용해주세요") | `UserService.getById`·`getByIdForUpdate`가 User를 못 찾으면 이 code를 던진다. 대타 승인(L290)은 **수락자** User를 이 메서드로 잠근다. 수락자 User가 없으면 관리자가 401을 받는다. 계정 삭제 기능이 없어 지금은 도달 불가. 계정 삭제를 도입하면 다시 본다 |

### 2-4. T2 HTTP 분류 불일치 (기록, 그 API를 고칠 때만 승인 받아 정정)

`SFT-009`·`SFT-010` 겹침인데 400 / `SFT-012`·`SFT-013` 상태인데 404 / `SUB-008` 없음인데 409 / `SFT-004` 승인의 수락자 자격 실패에 Shift code 400 / `SUB-015`·`SUB-016` 내부 불변식 위반을 업무 오류로 응답 / 시작된 근무 `SUB-012`·`SUB-018` 400 vs `SUB-023` 409 / Pattern API 실행자 권한 (M-01).

### 2-5. T3 형식·중복 (유지)

`UNA-403`·`UNA-404`·`COM-500` HTTP 번호 형식, 번호 없는 `OFFICIAL_SHIFT_CONFLICT`(Frontend 의존), 근무지 없음 중복(`WPL-005`·`SHIFT-001`·`SFT-019`), 관리자 필요 중복(`WPL-004`·`SHIFT-009`).

### 2-6. Test 참조가 없는 code (0건)

`SFT-002`, `SFT-007`, `SFT-008`, `SFT-010`, `SFT-011`, `SFT-016`, `SFT-017`, `SFT-019`, `SCH-003`, `SCH-008`, `SUB-003`. Migration 전에 현재 동작을 고정할 Test가 필요한지 S2-5에서 V3·V4 담당과 확인한다. → S2-5B 정적 판정과 Migration 명세: [TEST-INVENTORY](../evidence/TEST-INVENTORY.md) §5-3 (V3-N-24~32, V4-N-48). 담당 확인은 아직이다.

### 2-7. 정정 범위와 우선순위 (모두 Improvement, Kotlin Migration은 기존 그대로)

| ID | 등급 | 바뀌는 계약 | 영향 Test |
|---|---|---|---|
| T1-01 `SFT-006` → `SUB-024` | P1 (CHG-05) | 승인 응답 code·HTTP (400 → 409) | `SubstituteRequestServiceTest` L556·L569 |
| T1-02 `SUB-012` → `SUB-023` (응답 지점만) | P1 (CHG-05) | 후보 응답 code·HTTP (400 → 409) | `SubstituteCandidateServiceTest` L435·L452·L631 |
| T1-03 `SUB-014` → `SUB-023` (응답 지점만) | P1 | 후보 응답 code·HTTP (400 → 409) | 취소 근무 응답 Test (S2-5에서 위치 확인) |
| T1-04 `SUB-006` 문구 | P1 | 수락·승인 응답 message | message를 확인하는 Test가 있으면 기대값 변경 (S2-5) |
| T1-05 `SFT-005` (대타 종료 지점) | P1 낮음 | 대타 종료 응답 code | — |
| M-01 Pattern 실행자 검사 | P1 | Pattern API의 권한 실패 code·HTTP (400 → 403) | Pattern Controller Test의 `SHIFT-004`·`SHIFT-005` 기대값 (`jsonPath` 확인 다수) |
| L-01 `AUTH-004` | 기록만 | — (계정 삭제 도입 시 다시 봄) | — |

사용자 흐름을 막는 사례가 S3에서 나오면 해당 항목을 P0로 다시 평가한다 (G03-D-03 D03-5).

## 3. 전체 목록 (87개, AS-IS)

"종류"는 G03-D-03 §5-3의 **문서상 분류**다. API 응답 Field가 아니다. 현재 HTTP와 종류가 어긋나는 code는 T2로 표시했다.

| Code | HTTP | 종류 | Message | 사용처 (`클래스.메서드 L줄`) | 점검 | Test 참조 수 | Frontend 의존 |
|---|---|---|---|---|---|---|---|
| `AUTH-001` | 409 | DUPLICATE | 이미 사용중인 이메일입니다 | UserService.join L23 | 정상 | 1 | — |
| `AUTH-002` | 401 | AUTH | 이메일 또는 비밀번호가 올바르지 않습니다 | UserService.login L32<br>UserService.login L35 | 정상 | 2 | — |
| `AUTH-003` | 401 | AUTH | 유효하지 않은 인증 정보입니다 | UserService.refreshAccessToken L73<br>UserService.refreshAccessToken L77<br>UserService.refreshAccessToken L80 | 정상 | 4 | — |
| `AUTH-004` | 401 | AUTH | 로그인 후 이용해주세요. | UserService.getById L44<br>UserService.getByIdForUpdate L52<br>SecurityConfig.? L78 | **L-01 (잠재)** `getById`·`getByIdForUpdate`의 User 없음도 이 code(401 "로그인 후 이용해주세요"). 승인(L290)은 **수락자** User를 잠글 때 이 메서드를 씀 → 수락자 User가 없으면 관리자에게 401. 계정 삭제 기능이 없어 지금은 도달 불가 | 2 | — |
| `AUTH-005` | 401 | AUTH | Authorization 헤더가 Bearer 형식이 아닙니다. | CustomAuthenticationFilter.doFilterInternal L62 | 정상 | 1 | — |
| `AUTH-006` | 401 | AUTH | Access Token이 유효하지 않습니다. | CustomAuthenticationFilter.doFilterInternal L70 | 정상 | 3 | `lib/api.ts` 재발급 판단 |
| `WPL-001` | 404 | NOT_FOUND | 유효하지 않은 초대 코드입니다. | WorkplaceService.joinWorkplace L62 | 정상 | 3 | — |
| `WPL-002` | 409 | DUPLICATE | 이미 참여 중인 근무지입니다. | WorkplaceService.joinWorkplace L65 | 정상 | 4 | — |
| `WPL-003` | 403 | PERMISSION | 해당 근무지의 현재 구성원이 아닙니다. | WorkplaceMemberService.requireManager L22 | 정상 | 10 | — |
| `WPL-004` | 403 | PERMISSION | 관리자 권한이 필요합니다. | WorkplaceMemberService.requireManager L28 | 정상 | 15 | — |
| `WPL-005` | 404 | NOT_FOUND | 존재하지 않는 근무지입니다. | WorkplaceService.getWorkplace L101<br>ScheduleService.createDraftSchedule L77<br>SubstituteRequestService.list L164<br>SubstituteRequestService.approve L265 | 정상. 승인(L265)·목록(L164)의 근무지 조회는 방어 검사 | 5 | — |
| `SHIFT-001` | 404 | NOT_FOUND | 존재하지 않는 근무지입니다. | RegularShiftPatternService.validateWorkplaceExists L143 | T3 근무지 없음 중복 정의 (`WPL-005`·`SFT-019`) | 2 | — |
| `SHIFT-002` | 404 | NOT_FOUND | 존재하지 않는 근로자입니다. | RegularShiftPatternService.getActiveMember L153 | 정상 | 1 | — |
| `SHIFT-003` | 404 | NOT_FOUND | 존재하지 않는 정기 근무입니다. | RegularShiftPatternService.getPatternInWorkplace L180 | 정상 | 3 | — |
| `SHIFT-004` | 400 | PERMISSION / INPUT | 해당 근무지의 구성원이 아닙니다. | RegularShiftPatternService.getActiveMember L159<br>RegularShiftPatternService.requireManager L245 | **M-01 (한 code 두 의미, 새로 발견)** L159는 **대상 직원**이 그 근무지 소속이 아님, L245는 **실행자**가 그 근무지 소속이 아님. 실행자 권한 실패가 400으로 나가고 `WPL-003`과 다름 (§2-3) | 2 | — |
| `SHIFT-005` | 400 | STATE_CONFLICT | 현재 근무 중인 구성원이 아닙니다. | RegularShiftPatternService.getActiveMember L165<br>RegularShiftPatternService.requireManager L251 | **M-01 (같은 문제)** L165 대상 직원 퇴사, L251 실행자 퇴사. 실행자 권한 실패가 400 | 2 | — |
| `SHIFT-006` | 400 | INPUT | 시작 시간은 종료 시간보다 빨라야 합니다. | RegularShiftPatternService.validateTime L203 | 정상 | 2 | — |
| `SHIFT-007` | 409 | FORBIDDEN_CONFLICT | 기존 정기 근무 시간과 겹칩니다. | RegularShiftPatternService.validateOverlap L232 | 정상 | 6 | — |
| `SHIFT-008` | 400 | INPUT | 해당 근무지의 정기 근무가 아닙니다. | RegularShiftPatternService.getPatternInWorkplace L190 | 정상 | 2 | — |
| `SHIFT-009` | 403 | PERMISSION | 관리자 권한이 필요합니다. | RegularShiftPatternService.requireManager L257 | T2 Pattern API의 관리자 검사가 `WPL-004`가 아니라 자체 code (§2-3) | 4 | — |
| `SCH-001` | 400 | INPUT | 주 시작일은 월요일이어야 합니다. | ScheduleService.validateWeekStartDate L247 | 정상 | 2 | — |
| `SCH-002` | 409 | DUPLICATE | 해당 주차의 근무표가 이미 존재합니다. | ScheduleService.validateScheduleNotExists L265 | 정상 | 2 | — |
| `SCH-003` | 404 | NOT_FOUND | 근무표를 찾을 수 없습니다. | ScheduleService.getScheduleForUpdateOrThrow L276<br>ScheduleService.validateScheduleWorkplace L289 | 정상 | 0 | — |
| `SCH-004` | 409 | STATE_CONFLICT | DRAFT 상태의 근무표만 공개할 수 있습니다. | ScheduleService.validateDraftSchedule L322 | 정상 | 2 | — |
| `SCH-005` | 409 | STATE_CONFLICT | 근무가 없는 근무표는 공개할 수 없습니다. | ScheduleService.getPublishTargetShifts L338 | 정상 | 2 | — |
| `SCH-006` | 409 | STATE_CONFLICT | 현재 구성원이 아닌 담당자가 포함되어 있어 근무표를 공개할 수 없습니다. | ScheduleService.validateCurrentShiftMembers L359 | 정상 | 2 | — |
| `SCH-007` | 409 | STATE_CONFLICT | 유효하지 않은 근무 시간이 포함되어 있어 근무표를 공개할 수 없습니다. | ScheduleService.validateShiftTimes L375 | 정상 | 1 | — |
| `SCH-008` | 409 | STATE_CONFLICT | 자정을 넘는 근무가 포함되어 있어 근무표를 공개할 수 없습니다. | ScheduleService.validateShiftTimes L387 | 정상 | 0 | — |
| `SCH-009` | 409 | STATE_CONFLICT | 근무표 주차 범위를 벗어난 근무가 포함되어 있습니다. | ScheduleService.validateShiftWeekRange L413 | 정상 | 1 | — |
| `SCH-010` | 409 | FORBIDDEN_CONFLICT | 같은 담당자의 근무 시간이 겹쳐 근무표를 공개할 수 없습니다. | ScheduleService.validateInternalShiftOverlap L445 | 정상 | 1 | — |
| `SCH-011` | 409 | FORBIDDEN_CONFLICT | 다른 공식 근무와 시간이 겹쳐 근무표를 공개할 수 없습니다. | ScheduleService.validateOfficialShiftConflicts L473 | 정상 | 2 | — |
| `SCH-012` | 400 | STATE_CONFLICT | 해당 요청의 스케줄은 공개되지 않았습니다. | SubstituteRequestService.approve L251<br>SubstituteRequestService.close L388 | 정상 | 2 | — |
| `SFT-001` | 400 | INPUT | 입력값이 올바르지 않습니다. | ShiftService.validWeekStartDate L332 | 정상 | 1 | — |
| `SFT-002` | 400 | INPUT | 스케줄이 해당 workplace 소속이 아닙니다. | ShiftService.validSchedule L207 | 정상 | 0 | — |
| `SFT-003` | 400 | INPUT | 업무가 해당 스케줄 소속이 아닙니다. | ShiftService.validShift L223 | 정상 | 1 | — |
| `SFT-004` | 400 | STATE_CONFLICT | 멤버가 해당 workplace 소속이 아닙니다. | ShiftService.validWorkplaceMember L238<br>SubstituteRequestService.approve L282 | T2 승인(L282)에서 수락자가 퇴사·다른 근무지·EMPLOYEE 아님일 때도 이 code (Shift 쪽 code, 400). ML-3 적용 후에는 수락자 퇴사 시 요청이 닫혀 도달하기 어려움 | 4 | — |
| `SFT-005` | 400 | STATE_CONFLICT | 잘못된 상태입니다. | ShiftService.validSchedule L211<br>SubstituteRequestService.close L391 | **T1-05 (새로 발견)** 대타 종료(L391)에서 취소된 근무를 "잘못된 상태입니다"로 응답. 같은 상황의 승인은 `SFT-013` "취소되었습니다". DRAFT 검사(L211)와 같은 code. ML-3 적용 후에는 취소된 근무의 활성 요청이 남지 않아 도달하기 어려움 | 4 | — |
| `SFT-006` | 400 | STATE_CONFLICT | 시간 형식이나 값이 올바르지 않습니다. | SubstituteRequestService.validateNotStarted L356 | **T1-01** 승인에서 "근무 시작 후 승인 불가"를 입력 형식 오류 code로 응답 (사용처 1곳) | 2 | — |
| `SFT-007` | 400 | INPUT | 근무 시작 시간은 종료 시간보다 이전이어야 합니다. | ShiftService.validShiftTime L258 | 정상 | 0 | — |
| `SFT-008` | 400 | INPUT | 설정한 근무시간이 스케줄 주간에서 벗어나있습니다. | ShiftService.validateShiftInScheduleWeek L301 | 정상 | 0 | — |
| `SFT-009` | 400 | FORBIDDEN_CONFLICT | 동일인물의 근무는 중복될 수 없습니다. | ShiftService.validateShiftOverlap L318 | T2 겹침인데 400 (같은 성격의 `SCH-010` 등은 409) | 2 | — |
| `SFT-010` | 400 | FORBIDDEN_CONFLICT | 다른 근무지에서 중복되는 근무가 있습니다. | ShiftService.validateShiftOverlap L325 | T2 같음 | 0 | — |
| `SFT-011` | 400 | INPUT | 근무 시작일과 종료일은 같아야 합니다. | ShiftService.validShiftTime L250 | 정상 | 0 | — |
| `SFT-012` | 404 | STATE_CONFLICT | 해당 근무는 확정되지 않았습니다. | ShiftService.detail L62 | T2 상태인데 404 (근무 상세) | 1 | — |
| `SFT-013` | 404 | STATE_CONFLICT | 해당 근무는 취소되었습니다. | ShiftService.detail L66<br>SubstituteRequestService.approve L255 | T2 상태인데 404 (근무 상세, 승인 L255) | 2 | — |
| `SFT-014` | 403 | PERMISSION | 잘못된 권한입니다. | ShiftService.detail L72 | 정상 (문구 "잘못된 권한입니다"는 모호함, 근무 상세 M3 선검사 CHG-07에서 404로 바뀔 예정) | 1 | — |
| `SFT-015` | 409 | CONFIRM_REQUIRED | 근무 불가능 일정과 충돌하는 Shift가 있습니다. | ScheduleService.validateUnavailableConflictConfirmation L517<br>ShiftService.validateUnavailableConflictConfirmation L279 | 정상 | 3 | `schedule/page.tsx` 경고 후 확인 |
| `SFT-016` | 404 | NOT_FOUND | 대상을 찾을 수 없습니다. | ShiftService.validShift L219<br>ShiftService.validWorkplaceMember L233 | 정상 (문구 "대상을 찾을 수 없습니다"는 모호함: 근무·담당 소속 공통) | 0 | — |
| `SFT-017` | 404 | NOT_FOUND | 스케줄이 없거나 잘못된 스케줄 ID입니다. | ShiftService.validSchedule L203 | 정상 | 0 | — |
| `SFT-018` | 404 | NOT_FOUND | 해당 근무를 찾을 수 없습니다. | ShiftService.detail L58 | 정상 | 1 | — |
| `SFT-019` | 404 | NOT_FOUND | 해당 근무지를 찾을 수 없습니다. | ShiftService.delete L187 | T3 근무지 없음 중복 정의 | 0 | — |
| `UNA-001` | 400 | INPUT | 시작 시간은 종료 시간보다 빨라야 합니다. | UnavailableTimeService.validateTimeRange L232 | 정상 | 4 | — |
| `UNA-002` | 400 | INPUT | 근무 불가능 일정은 미래 시간만 등록할 수 있습니다. | UnavailableTimeService.validateFutureTime L207 | 정상 | 4 | — |
| `UNA-003` | 409 | FORBIDDEN_CONFLICT | 기존 근무 불가능 일정과 시간이 겹칩니다. | UnavailableTimeService.validateUnavailableTimeOverlap L271 | 정상 | 4 | — |
| `UNA-004` | 400 | STATE_CONFLICT | 이미 시작되었거나 지난 일정은 수정할 수 없습니다. | UnavailableTimeService.validateExistingFutureTime L220 | 정상 | 2 | — |
| `UNA-403` | 403 | PERMISSION | 본인의 근무 불가능 일정만 수정할 수 있습니다. | UnavailableTimeService.validateOwner L195 | T3 HTTP 번호 형식 | 2 | — |
| `UNA-404` | 404 | NOT_FOUND | 근무 불가능 일정을 찾을 수 없습니다. | UnavailableTimeService.update L133<br>UnavailableTimeService.delete L178 | T3 HTTP 번호 형식 | 2 | — |
| `OFFICIAL_SHIFT_CONFLICT` | 400 | CONFIRM_REQUIRED | 이미 확정된 근무와 겹치는 시간입니다. | UnavailableTimeService.validateOfficialShiftConflict L296 | T3 번호 없는 code, 경고인데 400 (Frontend 의존) | 4 | `unavailable/page.tsx` 경고 후 확인 |
| `SUB-001` | 404 | NOT_FOUND | 존재하지 않는 대체근무 요청입니다. | SubstituteCandidateService.validateRespondableCandidate L119<br>SubstituteRequestService.approve L238 | 정상 | 3 | — |
| `SUB-002` | 400 | STATE_CONFLICT | 후보자의 수락이 필요합니다. | SubstituteRequestService.approve L242 | 정상 | 2 | — |
| `SUB-003` | 400 | STATE_CONFLICT | 해당 요청 이미 수락되거나 승인되었습니다. | SubstituteRequest.validateOpening L57 | 정상 (Entity 방어 검사). 서비스가 먼저 `SUB-023`으로 막아 실제로는 도달하기 어려움 (Test 0) | 0 | — |
| `SUB-004` | 400 | STATE_CONFLICT | 대체 요청자와 실제 근무 담당자가 같지 않습니다. | SubstituteRequestService.approve L261 | 정상 | 1 | — |
| `SUB-005` | 409 | FORBIDDEN_CONFLICT | 해당 근무 시간에 중복되는 근무가 있습니다. | SubstituteCandidateService.acceptCandidate L214<br>SubstituteRequestService.approve L310 | 정상 | 2 | — |
| `SUB-006` | 409 | FORBIDDEN_CONFLICT | 등록한 불가능 시간과 중복됩니다. | SubstituteCandidateService.acceptCandidate L225<br>SubstituteRequestService.approve L319 | **T1-04 (새로 발견)** 승인(L319)에서 관리자에게 "**등록한** 불가능 시간과 중복됩니다" (관리자가 등록한 것이 아님). 수락(L225)에는 맞음 | 3 | — |
| `SUB-007` | 409 | FORBIDDEN_CONFLICT | 이미 수락한 대체 근무 시간과 중복됩니다. | SubstituteCandidateService.acceptCandidate L237<br>SubstituteRequestService.approve L326 | 정상 | 5 | — |
| `SUB-008` | 409 | NOT_FOUND | 수락한 후보자를 찾을 수 없습니다. | SubstituteRequestService.approve L270 | T2 "없음"인데 409 | 1 | — |
| `SUB-009` | 404 | NOT_FOUND | 해당 shift를 찾을 수 없습니다. | SubstituteRequestService.validateSubstituteRequest L134 | 정상 | 1 | — |
| `SUB-010` | 400 | STATE_CONFLICT | 아직 공개되지 않은 근무입니다. | SubstituteCandidateService.validateRespondableCandidate L154<br>SubstituteRequestService.validateSubstituteRequest L137 | 정상 | 2 | — |
| `SUB-011` | 403 | PERMISSION | 본인 근무에 대해서만 대체근무 요청을 할 수 있습니다 | SubstituteRequestService.validateSubstituteRequest L145 | 정상 | 2 | — |
| `SUB-012` | 400 | STATE_CONFLICT | 이미 시작된 근무는 대체근무를 요청할 수 없습니다 | SubstituteCandidateService.validateShiftNotStarted L171<br>SubstituteRequestService.validateSubstituteRequest L149 | **T1-02** 후보 응답(Candidate L171)에서 "요청할 수 없습니다" 문구. 요청 생성(L149)에는 맞음 | 4 | — |
| `SUB-013` | 409 | DUPLICATE | 이미 진행중인 대체근무 요청이 있습니다 | SubstituteRequestService.validateSubstituteRequest L154 | 정상 | 3 | — |
| `SUB-014` | 400 | STATE_CONFLICT | 취소된 근무에는 대체근무를 요청할 수 없습니다 | SubstituteCandidateService.validateRespondableCandidate L159<br>SubstituteRequestService.validateSubstituteRequest L141 | **T1-03 (새로 발견)** 후보 응답(Candidate L159)에서 "취소된 근무에는 대체근무를 요청할 수 없습니다" 문구. 요청 생성(L141)에는 맞음. T1-02와 같은 패턴 | 2 | — |
| `SUB-015` | 404 | INTERNAL | ACCEPTED 상태인 요청의 수락자 정보를 찾을 수 없습니다. | SubstituteRequestService.getAcceptedCandidates L224 | T2 내부 불변식 위반(수락자 없음)을 업무 오류로 응답 (관리자 진행 중 목록) | 1 | — |
| `SUB-016` | 400 | INTERNAL | 하나의 요청에 대해 수락자가 여러 명 조회되었습니다. | SubstituteRequestService.getAcceptedCandidates L210 | T2 내부 불변식 위반(수락자 여러 명)을 업무 오류로 응답 | 1 | — |
| `SUB-017` | 400 | STATE_CONFLICT | 이미 종료되었거나 승인된 요청은 종료할 수 없습니다. | SubstituteRequestService.close L382 | 정상 | 2 | — |
| `SUB-018` | 400 | STATE_CONFLICT | 근무 시작 이후에는 요청을 종료할 수 없습니다. | SubstituteRequestService.close L397 | 정상 | 2 | — |
| `SUB-019` | 404 | NOT_FOUND | 해당 대타 후보를 찾을 수 없습니다. | SubstituteCandidateService.validateRespondableCandidate L111<br>SubstituteCandidateService.validateRespondableCandidate L125 | 정상 | 1 | — |
| `SUB-020` | 403 | PERMISSION | 본인의 대타 요청에만 응답할 수 있습니다. | SubstituteCandidateService.validateRespondableCandidate L132 | 정상 | 2 | — |
| `SUB-021` | 403 | PERMISSION | 현재 유효한 직원만 대타 요청에 응답할 수 있습니다. | SubstituteCandidateService.validateRespondableCandidate L137 | 정상 | 3 | — |
| `SUB-022` | 409 | STATE_CONFLICT | 이미 응답한 대타 요청입니다. | SubstituteCandidateService.validateRespondableCandidate L142 | 정상 | 2 | — |
| `SUB-023` | 409 | STATE_CONFLICT | 더 이상 응답할 수 없는 대타 요청입니다. | SubstituteCandidateService.validateRespondableCandidate L147 | 정상 | 4 | — |
| `NOT-001` | 404 | NOT_FOUND | 알림을 찾을 수 없습니다. | NotificationService.markAsRead L306 | 정상 | 1 | — |
| `NOT-002` | 403 | PERMISSION | 해당 알림에 접근할 권한이 없습니다. | NotificationService.markAsRead L317 | 정상 | 2 | — |
| `COM-001` | 400 | INPUT | 요청 값이 올바르지 않습니다. | GlobalExceptionHandler.handleMethodArgumentNotValidException L63 | 정상 | 11 | — |
| `COM-002` | 400 | INPUT | 요청 형식이 올바르지 않습니다. | GlobalExceptionHandler.handleHttpMessageNotReadableException L44 | 정상 | 9 | — |
| `COM-003` | 400 | INPUT | 요청 파라미터가 올바르지 않습니다. | GlobalExceptionHandler.handleMethodArgumentTypeMismatchException L95<br>GlobalExceptionHandler.handleMissingServletRequestParameterException L109 | 정상 | 4 | — |
| `COM-500` | 500 | INTERNAL | 서버 내부 오류가 발생했습니다. | GlobalExceptionHandler.handleException L120 | T3 HTTP 번호 형식 | 1 | — |

## 4. 새 code 예약 (`PROPOSED`, Enum 미적용)

현재 최대 번호: AUTH 006, WPL 005, SFT 019, SCH 012, SUB 023, COM 003 (+500). 아래는 그다음 번호다. 문구는 후보다.

| 예약 | HTTP | 종류 | 상황 | 사용 API | 근거 |
|---|---|---|---|---|---|
| `AUTH-007` | 403 | PERMISSION | 로그아웃 요청의 출처(`Origin`·`Referer`) 검증 실패 | API-AU-01 | AUTH-1 S2 |
| `WPL-006` | 404 | NOT_FOUND | 퇴사 대상·인계 대상 소속이 없거나 다른 근무지 (구별하지 않음) | API-LV-01·02 | API-CATALOG §1 |
| `WPL-007` | 409 | STATE_CONFLICT | 퇴사 대상이 MANAGER | API-LV-01·02 | ML-H2 H2-9 |
| `WPL-008` | 409 | STATE_CONFLICT | 퇴사 대상이 이미 종료된 소속 | API-LV-01·02 | ML-4 §5 |
| `WPL-009` | 409 | STATE_CONFLICT | 진행 중 근무가 있어 퇴사 불가 (`data`에 종료 시각) | API-LV-01·02 | ML-4-7 |
| `WPL-010` | 409 | STALE | 퇴사 계획이 지금 상태와 맞지 않음 (P1~P5) | API-LV-02 | G03-D-05 §4-2 |
| `WPL-011` | 409 | CONFIRM_REQUIRED | 퇴사 인계의 불가능 시간 충돌 확인 필요 (목록 형식) | API-LV-02 | G03-D-05 §5 — §6 질문 1 |
| `SFT-020` | 409 | STATE_CONFLICT | 공개 후 변경 불가 상태 (근무표가 PUBLISHED 아님, 근무가 SCHEDULED 아님, 시작한 근무) | API-PS-01·02·03 | ML-2 D27 |
| `SFT-021` | 409 | CONFIRM_REQUIRED | 공개 후 추가·담당자 변경의 불가능 시간 충돌 확인 필요 (목록 형식) | API-PS-01·03 | G03-D-05 — §6 질문 1 |
| `SFT-022` | 409 | STATE_CONFLICT | 담당자 변경 대상이 현재 담당자와 같음 | API-PS-03 | API-CATALOG §3-5 |
| `SUB-024` | 409 | STATE_CONFLICT | 근무가 시작되어 승인할 수 없음 (T1-01 정정) | 대타 승인 | G03-D-03 T1-01 |
| `COM-004` | 503 | TRANSIENT | Deadlock·Lock 대기 초과로 Transaction 전체가 Rollback된 일시 실패. **기술 후보: MySQL·Spring 실험(ML-5 ML-14)으로 전체 Rollback을 확인하기 전까지 적용하지 않는다.** 임의의 DB 예외를 503으로 매핑하지 않는다 | 공통 | G03-D-03 §5-3 |

**새 code 없이 기존 code를 재사용하는 것 (재사용 기준: 의미·HTTP·다음 행동이 모두 같음):**

| 상황 | 재사용 | 이유 |
|---|---|---|
| 동시 가입으로 현재 소속이 이미 있음 | `WPL-002` | 같은 의미 (ML-H2 H2-7) |
| 후보 응답에서 시작·취소된 근무 (T1-02·03) | `SUB-023` | "더 이상 응답할 수 없음" |
| M3 선검사의 범위 밖 ID (CHG-06·07) | `SUB-001`(요청), `SUB-019`(후보), `SFT-018`(근무), `NOT-001`(알림) | 사용자에게는 "찾을 수 없음"과 같아야 하므로 (존재를 드러내지 않음). 새 code를 만들면 오히려 범위 밖임이 드러난다. **응답이 정말 같은지 (정적 확인):** 같은 Enum 상수를 `data` 없이 던지면 `GlobalExceptionHandler`가 `ApiResponse.error(code, message, null)`로 만든다 (L20-35, `ApiResponse` L49-68). 그래서 없는 ID와 범위 밖 ID의 HTTP·`code`·`message`·`data`가 같다. **구현 조건:** 범위 밖을 처리할 때 다른 문구·`data`를 붙이지 않는다. 범위 확인 전에 Lock을 잡지 않아 응답 시간 차이도 줄인다 (M3 선검사). 서버 Log에는 구분해서 남겨도 된다 (응답에는 안 나감) |
| 공개 후 변경의 Path 범위 밖 근무표·근무·담당 소속 | `SFT-017`, `SFT-018`, `SFT-016` | 같음 |
| 실행자 권한 (Path에 근무지가 있는 새 API) | `WPL-003`, `WPL-004` | 기존 `requireManager` |

## 5. Frontend 분기와의 연결

**지금 Frontend가 code로 분기하는 것은 3개뿐이다:** `AUTH-006`(재발급), `SFT-015`(DRAFT·Publish 경고), `OFFICIAL_SHIFT_CONFLICT`(불가능 시간 등록 경고). T1·M·T2 정정 후보 중 이 3개를 바꾸는 것은 없다. 나머지 화면은 `message`를 그대로 보여 주므로, 문구 정정은 화면 문구가 바뀌는 것 외에 분기 영향이 없다.

| 종류 ([FRONTEND-CONTRACT](FRONTEND-CONTRACT.md) §2) | 새 API에서 쓰는 code | 화면 행동 |
|---|---|---|
| CONFIRM_REQUIRED | `WPL-011`, `SFT-021` (§6 질문 1) | 충돌 목록 확인 → 확인 목록과 함께 같은 API 재요청 |
| STALE | `WPL-010` | 미리보기 다시 조회 |
| STATE_CONFLICT | `WPL-007`·`WPL-008`·`WPL-009`, `SFT-020`·`SFT-022`, `SUB-024`, (재사용) `SUB-023` | 안내 |
| FORBIDDEN_CONFLICT | (재사용) `SFT-009`·`SFT-010`·`SCH-011` 계열 | 다른 직원·시간 선택 |
| DUPLICATE | (재사용) `WPL-002` | 중단 |
| NOT_FOUND | `WPL-006`, (재사용) `SUB-001`·`SUB-019`·`SFT-016~018`·`NOT-001` | "찾을 수 없거나 접근할 수 없습니다". **소속 종료로 해석하지 않음** |
| PERMISSION | `WPL-003` (→ 근무지 목록으로 이동), `WPL-004`, `AUTH-007` | — |
| TRANSIENT | `COM-004` (실험 후) | 최신 상태 조회 후 사용자가 직접 재시도 |

## 6. S2-4A 결정 (사용자 잠정 결정 2026-10-25, 팀 승인 전)

### 6-1. 목록 형식 확인 경고는 새 code (`WPL-011`, `SFT-021`)

- 기존 `SFT-015`(`boolean` 확인)는 DRAFT·Publish에서 그대로 유지한다.
- 새 API의 경고는 퇴사 `WPL-011`, 공개 후 변경 `SFT-021`. 응답 `data`에 충돌 목록을 담고, 클라이언트는 확인한 목록을 다시 보낸다 ([G03-D-05](../decisions/proposals/G03-D-05-confirmation-response.md) §5).
- 이유: 의미는 같지만 **다시 보내는 방식이 다르다** (`boolean` vs 확인 목록). 재사용 기준(의미·HTTP·다음 행동이 모두 같음)을 만족하지 않는다.
- **code는 둘이지만 형식은 하나로:** `WPL-011`과 `SFT-021`의 `data` 구조와 재요청 Field는 같게 정한다. 그래서 Server의 응답 DTO와 Frontend의 충돌 확인 화면은 **하나를 같이 쓸 수 있다** (구현 설계에서 확인). code를 나눈 것은 Domain 구분과 발생 조건 추적을 위한 것이다.

| code | 발생 API | 발생 조건 |
|---|---|---|
| `WPL-011` | API-LV-02 퇴사 확정 | 인계하는 근무 중 하나 이상이 인계 대상의 불가능 시간과 겹치고, 확인 목록이 그 겹침을 모두 포함하지 않음 |
| `SFT-021` | API-PS-01 공개 후 추가, API-PS-03 담당자 변경 | 그 근무가 새 담당의 불가능 시간과 겹치고, 확인 목록이 그 겹침을 포함하지 않음 |

### 6-2. T1-04 `SUB-006`은 code를 유지하고 문구만 중립화

- 새 문구 후보: "불가능 시간과 겹칩니다".
- **두 사용처 대조 (정적):**

| | 수락 (`SubstituteCandidateService` L218-226) | 승인 (`SubstituteRequestService` L313-319) |
|---|---|---|
| 검사 | 후보 User의 불가능 시간 × 그 근무 시간 | 같음 (수락자 User의 불가능 시간 × 그 근무 시간) |
| HTTP | 409 | 409 |
| `data` | 없음 | 없음 |
| 업무 의미 | 그 후보의 불가능 시간 때문에 이 대타를 진행할 수 없음 | 같음 |
| 사용자의 다음 행동 | (직원) 불가능 시간을 고치거나 수락하지 않음 | (관리자) 승인할 수 없음. 요청을 닫거나 직접 담당자 변경 (G03-D-21 D21-9) |

- 다음 행동은 역할에 따라 다르지만, 어느 API를 불렀는지(직원 응답 / 관리자 승인)로 화면이 이미 구분되므로 같은 code로 충분하다. 재사용을 막을 차이는 없다.
- 문구 변경도 기존 응답 계약 변경이라 **Improvement P1**이다. Migration에는 적용하지 않는다.

## 7. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-25 | 초안 (S2-4A). 87개 전체 목록(사용처·Test 참조·Frontend 의존), T1 3건 추가 발견(T1-03~05), 한 code 두 의미 M-01, 잠재 L-01, Test 참조 0인 code 11개, 새 code 12개 예약과 재사용 표, Frontend 분기 연결, 질문 2개 |
| 2026-10-25 | 질문 2개 사용자 잠정 결정 반영 (§6). 정정 범위·우선순위·영향 Test 표(§2-7), 범위 밖 404 응답이 없는 ID와 같은지 정적 확인, `WPL-011`·`SFT-021`의 공통 형식, `SUB-006` 두 사용처 대조 |
| 2026-10-25 | S2-5B: Test 0 code 11개를 TEST-INVENTORY §5-3에 연결 |
