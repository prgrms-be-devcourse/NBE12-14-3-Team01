# Canonical Service Contract Baseline

> 작성 기준: 2차 `origin/develop` `243f77c`, 2026-10-22 (D0-B). D0-D (2026-10-23)에서 3차 저장소로 이관했고 행 내용은 바꾸지 않았다.
> `243f77c` → 2차 `8da2bec` (= 3차 `main`) → 3차 `develop` `cdaaa13` 사이 `backend/src/main/java` 변경은 0이다 (`git diff`, 2026-10-23). 행의 Test 수·EV-ID는 `243f77c` 실행 결과다.
> 상태 축 대응: `AS_IS_OBSERVED` = `docs/STATUS-AXES.md` ① `VERIFIED_AS_IS`. `관련 ID`의 G-03 C-ID·G03-D-ID 원문은 참고 공유본 [`docs/references/G-03-contract-review.md`](../references/G-03-contract-review.md)에 있다 (Original `9858612` 기준 AI 분석. `REVIEW_ACCEPTED`는 승인이 아니며 이 문서의 `APPROVED`로 옮기지 않는다).
> **현재 `APPROVED` 행: 0건.** 아래 행은 모두 코드에서 확인한 현재 동작(`AS_IS_OBSERVED`) 또는 불일치(`DISCREPANCY`) 기록이며 승인된 계약이 아니다.
> D1에서 Domain Owner가 행을 채우고 팀이 승인하면 `승인` 칸에 `APPROVED (승인자, 일자, DECISION-LOG ID)`를 적는다. AI는 `APPROVED`를 표시하지 않는다.

## 행 작성 규칙

| 칸 | 내용 |
|---|---|
| ID | `CB-<영역>-<번호>`. 외부 ID(G-03 C-ID 등)는 `관련 ID`에 |
| 구분 | 공식 요구 / 2차 P0 합의 / Java 구현 / 실행 Evidence / 신규 제안 중 무엇에서 왔는가 |
| 현재 동작 | develop 코드·Test로 확인한 사실 + 근거 위치 |
| 명세 | 2차 명세(PDF)의 내용. 대조하지 않았으면 `미대조 (D1)` |
| 상태 | `AS_IS_OBSERVED` / `DISCREPANCY` / `APPROVED` |
| Migration 처리 | 보통 "현행 보존". 정답 판정이 아님 |

## 1. 공통 HTTP / 응답 규약

| ID | 항목 | 구분 | 현재 동작 (근거) | 명세 | 상태 | Migration 처리 | 관련 ID | 승인 |
|---|---|---|---|---|---|---|---|---|
| CB-COM-01 | Base Path | Java 구현 | `server.servlet.context-path: /api/v1` (`application.yml`) | 미대조 (D1) | AS_IS_OBSERVED | 현행 보존 | — | — |
| CB-COM-02 | 응답 Envelope | Java 구현 + 실행 Evidence | `ApiResponse<T>` = `{ success: boolean, code: String, message: String, data: T }`. 성공 `success(code, message, data)`, 메시지 없음 시 `message = ""`, 실패 `error(code, message[, data])` (`global/dto/ApiResponse.java`, `ApiResponseTest` 4 PASS — EV-002). **2026-10-26 추가 확인 (EV-009):** ① JSON Field 순서는 Jackson 3.1.5 기본값(`SORT_PROPERTIES_ALPHABETICALLY = true`)으로 알파벳 순(`code, data, message, success`)이었다. 공통 Owner(최세현)가 이 행의 순서가 맞다고 판단해 `@JsonPropertyOrder({"success", "code", "message", "data"})`로 고정했다. ② `data`가 없으면 `"data": null`로 Field가 남는다. ③ 두 번째 인자가 String인 `success(code, "…")`는 `data`가 아니라 `message`로 쓰인다. ④ springdoc Schema의 `ApiResponse*`는 같은 4개 Field 순서이고 `required`가 없다. 근거 Test: `ApiResponseJsonContractTest` (Field 집합·타입), `ApiResponseJsonFormatTest` (순서·`""`·overload·HTTP 본문), `ApiResponseHttpContractTest`, `ApiResponseOpenApiSchemaTest` | 미대조 (D1) | AS_IS_OBSERVED | Field 이름·순서·`""` 처리 보존. Kotlin 전환 시 `@JsonPropertyOrder` 유지 | G03-D-01 | — |
| CB-COM-03 | ErrorCode 구조 | Java 구현 | `ErrorCode` 인터페이스: `getHttpStatus()`, `getCode()`, `getMessage()`. Domain별 enum이 구현 (`ShiftErrorCode` 등) | 미대조 (D1) | AS_IS_OBSERVED | 인터페이스·enum 이름 보존 | G03-D-03 | — |
| CB-COM-04 | 예외 → 응답 매핑 | Java 구현 + 실행 Evidence | `GlobalExceptionHandler`: `BusinessException` → 해당 ErrorCode / `HttpMessageNotReadableException` → `COM-002` 400 / `MethodArgumentNotValidException` → `COM-001` 400 + Field 오류 `data` / `MethodArgumentTypeMismatchException`·`MissingServletRequestParameterException` → `COM-003` 400 / 그 외 → `COM-500` 500 (`GlobalExceptionHandlerTest` 7 PASS — EV-002). **2026-10-26 추가 (EV-009):** 실제 HTTP 흐름에서 처리하지 않은 예외가 500 `COM-500` + `"data": null`로 나가고 예외 메시지는 노출되지 않음 (`UnhandledExceptionHttpContractTest`). `BusinessException`·`COM-001`의 HTTP 응답은 `ApiResponseHttpContractTest` | 미대조 (D1) | AS_IS_OBSERVED | 보존. Kotlin DTO Nullability로 `COM-001`이 `COM-002`로 바뀌지 않게 할 것 (AI-RULES §5, M0-6) | — | — |

## 2. Enum / 상태 / DB Schema

| ID | 항목 | 구분 | 현재 동작 (근거) | 명세 | 상태 | Migration 처리 | 관련 ID | 승인 |
|---|---|---|---|---|---|---|---|---|
| CB-ENUM-01 | `ScheduleStatus` | Java 구현 + 실행 Evidence | `DRAFT`, `PUBLISHED`, `@Enumerated(STRING)`. test Profile MySQL Schema에서 `schedule.status`가 MySQL 네이티브 `enum('DRAFT','PUBLISHED')`로 생성됨 (EV-005) | 미대조 (D1) | AS_IS_OBSERVED | 이름·순서 보존. 값 추가 시 `ddl-auto: update`의 ENUM 변경 위험 → Decision 필요 | — | — |
| CB-ENUM-02 | 그 밖의 Enum | Java 구현 | `ShiftStatus` {SCHEDULED, CANCELLED}, `RequestStatus` {OPEN, ACCEPTED, APPROVED, CLOSED}, `CandidateStatus` {PENDING, REJECTED, ACCEPTED}, `RequestCloseReason` {ALL_CANDIDATES_REJECTED, EXPIRED, MANAGER_CLOSED}, `WorkplaceRole` {MANAGER, EMPLOYEE}, `NotificationType` 8종 | 미대조 (D1) | AS_IS_OBSERVED | 보존 | G03-D-17, G03-D-19, G03-D-26 | — |
| CB-DB-01 | Unique 제약 | 실행 Evidence | `workplace_member(workplace_id,user_id)`, `schedule(workplace_id,week_start_date)`, `substitute_candidate(request_id,member_id)`, `users(email)`, `workplace(invite_code)` — test Profile MySQL에서 확인 (EV-005). 운영 Schema와 같다는 근거는 아님 | 미대조 (D1) | AS_IS_OBSERVED | Entity Annotation 보존 | — | — |

## 3. Transaction / Lock / Event

| ID | 항목 | 구분 | 현재 동작 (근거) | 명세 | 상태 | Migration 처리 | 관련 ID | 승인 |
|---|---|---|---|---|---|---|---|---|
| CB-TX-01 | 전역 Isolation | Java 구현 (develop 설정) | develop: `spring.datasource.hikari.transaction-isolation: TRANSACTION_READ_COMMITTED`, 모든 Profile. Original `9858612`에는 없음 (DB 기본값) | 미대조 | **DISCREPANCY** (Original ↔ develop, 근거 Evidence 없음) | develop 설정 보존. 유지 여부는 H6 결과로 결정 | DL-002 | — |
| CB-TX-02 | 비관적 Lock | Java 구현 | `@Lock` 7곳 (Repository). 위치·순서는 G-0x 참조 | 미대조 | AS_IS_OBSERVED | 문자열·순서 그대로 이식 | D-NOW-04 | — |
| CB-EVT-01 | 알림 Event 처리 | Java 구현 | `@TransactionalEventListener(phase = AFTER_COMMIT)` 7개. 예: `SchedulePublishedEventListener`는 알림 생성 실패를 `try/catch`로 잡고 `log.error`만 남김 (원 Transaction은 이미 Commit) | 미대조 | AS_IS_OBSERVED | phase·예외 처리 보존 | G03-D-22 | — |

## 4. Security

| ID | 항목 | 구분 | 현재 동작 (근거) | 명세 | 상태 | Migration 처리 | 관련 ID | 승인 |
|---|---|---|---|---|---|---|---|---|
| CB-SEC-01 | 무인증 허용 경로 | Java 구현 | `SecurityConfig` `permitAll`: POST `/auth/signup`·`/auth/login`·`/auth/refresh`, POST `/api/v1/auth/signup`, `/h2-console/**`, `/api/v1/workplaces/**` (주석 "PAT-01 테스트용 임시"), `/swagger-ui/**`, `/v3/api-docs/**`. 그 외 `authenticated()` | 미대조 | **DISCREPANCY 후보**: context-path가 `/api/v1`이므로 `/api/v1/...` Matcher의 실제 적용 여부는 Runtime 미확인 | 그대로 이식, 정리 금지 | G03-OQ-01, AUTH-EV-01, G03-D-28 | — |

## 5. Domain 계약 — 사례: Cross-Workplace 충돌 (V2 / V3)

| ID | 단계 | 구분 | 현재 동작 (근거, 정적 확인) | 명세 | 상태 | Migration 처리 | 관련 ID | 승인 |
|---|---|---|---|---|---|---|---|---|
| CB-SCH-01 | Pattern 등록·변경 | Java 구현 | 다른 근무지 공식 Shift와 겹침 검사 없음 (`RegularShiftPatternService`는 Shift를 조회하지 않음) | 미대조 | AS_IS_OBSERVED | 보존 | PAT-MIG-05, DL-001 | — |
| CB-SCH-02 | Pattern → DRAFT 생성 | Java 구현 | 검사 없음 (`ShiftService.createFromValidatedPattern`) → 충돌 DRAFT 허용 | 미대조 | AS_IS_OBSERVED | 보존 | SCH-MIG-08, DL-001 | — |
| CB-SCH-03 | DRAFT Shift API 생성·수정 | Java 구현 | 같은 근무표 안 겹침 `SFT-009` 400 (`DUPLICATE_LOCAL_SCHEDULE_TIME`), 다른 근무지 공식 Shift 겹침 `SFT-010` 400 "다른 근무지에서 중복되는 근무가 있습니다." (`DUPLICATE_GLOBAL_SCHEDULE_TIME`) | 미대조 | AS_IS_OBSERVED | 보존 | SCH-MIG-06, DL-001 | — |
| CB-SCH-04 | Publish | Java 구현 | 공식 Shift 충돌 시 `SCH-011` 409 "다른 공식 근무와 시간이 겹쳐 근무표를 공개할 수 없습니다." (`OFFICIAL_SHIFT_CONFLICT`) | 미대조 | AS_IS_OBSERVED | 보존 | SCH-MIG-10, SCH-MIG-13, DL-001 | — |
| CB-SCH-05 | 서로 다른 근무지 동시 Publish | AI 분석 (정적) | 중복 공식 Shift 생성 가능성 (Runtime 미검증) | — | UNKNOWN | 보존, H6에서 검증 | SCH-DC-02, SCH-EV-07 | — |

## 6. D1에서 채울 영역

V1 Auth (C-01~07), V2 Workplace/Member/Pattern (C-08~17), V3 Schedule/Shift/Unavailable (C-18~39), V4 Substitute (C-40~50), V5 Notification (C-51~56). 각 Domain Owner가 위 형식으로 API별 행을 만들고 2차 명세와 대조한다. 원천 분석은 G-03 (참고 공유본, ID 참조). V 배정은 내용 기준이며 G-03 절과 경계가 다르다: C-18·C-19는 G-03 A-03 절이지만 V3, C-40은 G-03 A-05 절이지만 V4 (`docs/references/README.md` §5). C-40(`CONDITIONAL`)과 C-41(`DECISION_PENDING`)은 `REVIEW_ACCEPTED`가 아니다.
