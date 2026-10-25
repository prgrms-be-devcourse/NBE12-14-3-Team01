# G03-D-03 ErrorCode 체계와 정정 기준

| 축 | 상태 |
|---|---|
| 결정 단계 | **사용자 잠정 결정** (2026-10-25). 팀 승인 전 |
| ② 정책 | 카드 `PROPOSED`. `DECISION-LOG` G03-D-03은 `PENDING` 그대로 |
| ③ 문서 | `DRAFT` |
| 구현 | 없음. Kotlin Migration은 기존 ErrorCode·HTTP·Message를 그대로 옮긴다 |
| ④ / ⑤ | 신규·변경 Test 전부 `SPECIFIED` / `NOT_RUN` |
| 기준 코드 | 3차 `develop` `a148a62` (Production Java = `cdaaa13`). 정적 확인 |
| 관련 ID | G03-D-03 (주), G03-D-05 (경고 응답 형식), G03-D-19·21·22, AUTH-1·ML-2·ML-4·ML-5 (로컬 Draft의 새 코드 후보), CB-COM-02·04 |
| 주관 / 리뷰 | 공통 설정 Owner (주관), 모든 Domain Owner, Frontend |

> 진행 절차는 [`PROCESS-CHARTER`](../PROCESS-CHARTER.md)를 따른다.

---

## 1. 왜 지금 정해야 하나

- 새 기능이 ErrorCode를 8~10개 새로 만든다 (§4). 규칙 없이 만들면 지금의 불일치가 늘어난다.
- 기존 코드 중 상황에 맞지 않는 안내가 있다 (§3). 고칠 기준이 없으면 고칠 때마다 방식이 달라진다.
- 여러 Domain 담당자가 동시에 개발하므로 "같은 code 재사용 금지·이미 있는 오류 재사용"을 확인할 공통 기준이 필요하다.

## 2. Java AS-IS (① `VERIFIED_AS_IS`, 정적 확인)

| 항목 | 현재 |
|---|---|
| 응답 형식 | `ApiResponse { success, code, message, data }` (`global/dto/ApiResponse` L14-17). 업무 오류는 `BusinessException` → `GlobalExceptionHandler` L20. 그 밖의 예외는 L118 `Exception.class` → `COM-500` |
| 개수 | **87개.** AUTH 6, WPL 5, SFT 19, SHIFT 9 (정기 근무 Pattern), SCH 12, UNA 7, SUB 23, NOT 2, COM 4 |
| 형식 | 대부분 `{DOMAIN}-{NNN}`. 예외: `UNA-403`, `UNA-404`, `COM-500` (HTTP 번호), `"OFFICIAL_SHIFT_CONFLICT"` (번호 없음) |
| HTTP 분포 | 400 33, 409 21, 404 18, 403 9, 401 5, 500 1 |
| Frontend 의존 | `AUTH-006` (Token 재발급 판단, `lib/api.ts` L60), `SFT-015` (경고 후 확인, `schedule/page.tsx`), `"OFFICIAL_SHIFT_CONFLICT"` (`unavailable/page.tsx`). HTTP 409를 일괄 처리하는 공통 분기는 없다 |
| Test 의존 | `jsonPath("$.code")` 문자열 확인 수십 곳, ErrorCode Enum 참조 152곳 |
| Lock 실패 | Deadlock·Lock 대기 초과는 `Exception.class`로 잡혀 `COM-500` (ML-CONCURRENCY-01-R §5) |

## 3. 기존 코드의 문제 분류

전체 87개를 "던지는 위치 + 문구가 상황에 맞는가"로 점검한 적은 아직 없다. 아래는 다른 작업 중에 확인한 것이며, **이것이 전부라고 확정하지 않는다.** 전체 점검은 S2의 ErrorCode 목록 작성에서 한다.

### 3-1. T1 잘못된 안내 (상황에 맞지 않는 code·문구)

| ID | 상황 | 현재 | 사용처 (정적) | 문제 성격 | 정정 방향 (PROPOSED) |
|---|---|---|---|---|---|
| T1-01 | 시작된 근무의 대타 **승인** | `SFT-006` 400 "시간 형식이나 값이 올바르지 않습니다." | **사용처 1곳뿐**: `SubstituteRequestService` L356 (`validateNotStarted`). Shift Domain에서는 쓰이지 않는다 | **code 자체가 틀림** (입력 형식 오류 code를 업무 상태 충돌에 사용) | 호출 지점만 바꾼다. 맞는 기존 SUB code가 없다 (`SUB-012`는 "요청", `SUB-018`은 "종료"). 새 SUB code 후보 (예: "근무가 이미 시작되어 승인할 수 없습니다", 409). `SFT-006` 정의는 남겨 둔다 (지금은 미사용이 된다) |
| T1-02 | 시작된 근무의 대타 **수락·거절** | `SUB-012` 400 "이미 시작된 근무는 대체근무를 요청할 수 없습니다" | 3곳: 요청 생성 `SubstituteRequestService` L149 (**문구가 맞음**), 수락·거절 공통 검사 `SubstituteCandidateService` L169-171 (**문구가 틀림**) | 같은 code를 의미가 다른 두 상황에 사용 | **문구만 바꾸면 요청 생성 쪽이 틀려지므로 안 된다.** 수락·거절 호출 지점을 분리한다. 기존 `SUB-023` 409 "더 이상 응답할 수 없는 대타 요청입니다"가 의미와 다음 행동이 같아 재사용 후보다 (§5-2 재사용 기준). HTTP가 400 → 409로 바뀌므로 Test 기대값 변경 |

T1의 기본 등급은 **P1**이다. S2 점검에서 잘못된 code 때문에 Frontend가 재인증하거나, 재확인을 반복하거나, 사용자가 오류에서 회복할 수 없는 경우가 나오면 **그 기능의 P0 출시 조건으로 올린다.** T1-01·02는 현재 알려진 범위에서 경계 시점에만 생기고 상태를 망가뜨리지 않으므로 P1이다.

### 3-2. T2 HTTP 분류 불일치 (기록, 기회가 있을 때 정정)

| 사례 | 현재 |
|---|---|
| 근무 시간 겹침 | `SFT-009`·`SFT-010` 400, `SCH-010`·`SCH-011`·`SUB-005`·`SHIFT-007`·`UNA-003` 409 |
| 상태인데 404 | `SFT-012` (확정 안 된 근무), `SFT-013` (취소된 근무) |
| 없음인데 409 | `SUB-008` (수락한 후보자를 찾을 수 없음) |
| 같은 성격, 다른 상태 | 시작된 근무: `SUB-012`·`SUB-018` 400, `SUB-023` 409 |

정정은 그 API를 Improvement에서 어차피 고칠 때만, 승인을 받아 함께 한다.

### 3-3. T3 형식·중복 (유지)

`UNA-403`·`UNA-404`·`COM-500`의 HTTP 번호 형식, 번호 없는 `OFFICIAL_SHIFT_CONFLICT`(Frontend 의존), 중복 정의(근무지 없음 `WPL-005`·`SHIFT-001`·`SFT-019`, 관리자 필요 `WPL-004`·`SHIFT-009`). 사용자 피해가 없고 Frontend 의존이 있어 바꾸지 않는다.

## 4. 새 ErrorCode 후보 (로컬 Draft에서 수집, 번호 미정)

| 출처 | 상황 | HTTP | 종류 | 비고 |
|---|---|---|---|---|
| AUTH-1 | 로그아웃 요청 출처 검증 실패 | 403 | PERMISSION | Draft 후보 `AUTH-007` |
| ML-4 | 퇴사 대상이 MANAGER | 409 | STATE_CONFLICT | 호출자가 관리자가 아니면 기존 `WPL-004` 403 |
| ML-4 | 이미 종료된 소속 | 409 | STATE_CONFLICT | |
| ML-4 | 진행 중 근무가 있음 | 409 | STATE_CONFLICT | `data`에 종료 시각 |
| ML-4·ML-2·D-21 | 미리보기 이후 상태가 바뀜, 확인한 불가능 시간 충돌 목록과 다름 | 409 | STALE | 하나로 합칠지 S2에서 결정 |
| ML-2 | 공개 후 추가·취소·담당자 변경: 시작한 근무 | 409 | STATE_CONFLICT | |
| ML-2 | 공개 후 변경의 불가능 시간 경고 | 409 | CONFIRM_REQUIRED | 기존 `SFT-015` 재사용 여부 S2 |
| ML-5 | Deadlock·Lock 대기 초과 (검증된 경우만) | 503 | TRANSIENT | COM 다음 순번 `COM-004` 후보 |
| T1-01 | 시작된 근무의 대타 승인 | 409 | STATE_CONFLICT | 새 SUB code 후보 |
| ML-H2 | 동시 가입 | 409 | DUPLICATE | **기존 `WPL-002` 재사용** (새 code 아님) |

번호는 S2에서 각 Enum의 **실제 최대 번호**를 다시 확인한 뒤 붙인다 (현재: AUTH 006, WPL 005, SFT 019, SCH 012, SUB 023, COM 003 + 500).

## 5. 사용자 잠정 결정 (2026-10-25)

### 5-1. 결정 문장

| ID | 결정 | 이유 |
|---|---|---|
| D03-1 | code 형식은 `{DOMAIN}-{NNN}`을 유지한다. **code에 HTTP 번호를 넣지 않는다.** code는 바뀌지 않는 업무 식별자이고 HTTP 상태는 별도 속성이다 | HTTP 분류를 바로잡을 때 code가 바뀌면 Frontend·Test가 깨진다. 기존 87개와 형식이 섞이지 않는다 |
| D03-2 | 오류의 HTTP·종류·의미·사용처·사용자 문구·다음 행동·Frontend 대응은 **공통 ErrorCode 목록 문서** (`docs/contract/ERROR-CODES.md`, S2)에서 관리한다. 형식은 §6 | 비슷한 오류끼리 묶어 보는 것은 목록의 "종류"로 한다 |
| D03-3 | "종류"는 **문서상 분류**다. API 응답에 `category` Field를 추가하지 않는다. Frontend는 실제 HTTP 상태와 code로 판단한다 | 응답 형식 변경 없이 Migration·Improvement 범위를 키우지 않는다 |
| D03-4 | Kotlin Migration은 기존 code·HTTP·Message를 그대로 옮긴다 | AI-RULES §2 |
| D03-5 | 기존 코드 정정: **T1은 정정** (기본 P1, 사용자 오류 복구를 막으면 P0). **T2는 기록**하고 그 API를 Improvement에서 고칠 때만 승인을 받아 정정. **T3는 유지** | 사용자에게 틀린 안내를 주는 것만 고치고 계약 변경을 최소로 한다 |
| D03-6 | 정정 방법: 먼저 그 code의 **모든 사용처**를 확인한다. 의미가 다른 사용처가 섞여 있으면 문구만 바꾸지 않고 호출 지점을 분리한다 (T1-02). 맞는 기존 code가 있으면 재사용하고, 없으면 새 code를 만든다 (T1-01). 기존 code의 정의는 지우지 않는다 | 한 곳을 고치다가 다른 곳의 안내가 틀려지지 않게 한다 |
| D03-7 | 변경한 code는 목록 문서에 **변경 전 값**(HTTP·문구·사용처)과 변경 근거·Decision ID·영향 API·Test를 남긴다 | 나중에 바뀐 이유를 알 수 있게 한다 |

### 5-2. 새 code 규칙

| 규칙 | 내용 |
|---|---|
| Prefix | 그 결정을 가진 Domain의 기존 Prefix (퇴사·재가입·초대 코드 WPL, 공개 후 근무 변경 SFT, Publish SCH, 대타 SUB, 인증 AUTH, 일시 실패 COM). **새 Prefix를 만들지 않는다.** `SHIFT`는 정기 근무 Pattern 전용 |
| 번호 | 그 Prefix의 다음 미사용 순번. 재사용하지 않는다. HTTP 번호 형식을 쓰지 않는다 |
| 400 | 요청 값 자체가 틀림 (형식, 범위). 현재 상태와 무관 |
| 401 | 인증 실패 |
| 403 | **행위자**의 권한 부족 |
| 404 | 대상이 없거나, 존재를 드러내면 안 되는 대상 |
| 409 | 요청은 올바르지만 **현재 업무 상태와 충돌**. 대상의 상태 때문에 불가한 경우 포함 (예: 호출자는 관리자이지만 대상이 MANAGER라서 퇴사 불가 → 409, 호출자가 관리자가 아님 → 403) |
| 503 | **검증된** 일시적 처리 실패 (Deadlock, Lock 대기 초과로 Transaction 전체가 Rollback된 것이 확인된 경우). ML-5 실험 전까지 후보 |
| 재사용 | 업무 의미, HTTP 분류, 사용자의 다음 행동이 **모두 같으면** 기존 code를 다른 API에서도 쓴다. 하나라도 다르면 새 code |
| 문구 | 상황과 다음 행동을 말한다. 예: "근무 상태가 바뀌었습니다. 다시 확인해 주세요." |
| 경고 응답 형식 | **[G03-D-05](G03-D-05-confirmation-response.md) (사용자 잠정 결정 2026-10-25, 이전 문장은 결정 전 기록).** 현재 경고 응답은 `ConfirmationRequiredResponse(true)`뿐이라 충돌 목록을 담지 못한다. 추천 방향: 기존 Migration API는 현재 형식 유지, 새 퇴사·인계 Command는 충돌 대상 목록을 담는 응답, 확정 시 다시 계산해 다르면 409 (STALE), Publish 개선은 별도 영향 평가 후 등급 결정. 새 ErrorCode를 늘리기보다 응답 `data` 구조와 STALE 판단 기준을 함께 정한다 |

### 5-3. 409와 503의 Frontend 대응

HTTP 상태는 큰 분류이고, 화면별 정확한 대응은 code로 정한다. **409를 일괄 "재시도"로 처리하지 않는다.**

| 종류 | 예 | 사용자의 다음 행동 | 확인으로 넘어갈 수 있나 |
|---|---|---|---|
| DUPLICATE | 이미 현재 소속 (`WPL-002`) | 요청 중단 | 아니오 |
| STATE_CONFLICT | 이미 퇴사한 대상, 시작된 근무 | 현재 상태 확인 | 아니오 |
| STALE | 미리보기 이후 변경 | 최신 상태 다시 조회 후 다시 확인 | 다시 조회하면 가능 |
| CONFIRM_REQUIRED | 불가능 시간 경고 (`SFT-015`) | 충돌 내용 확인 후 확인 Flag로 다시 요청 | 예 (불가능 시간만, G03-D-21 D21-5) |
| FORBIDDEN_CONFLICT | 공식 근무 중복 (`SFT-010`, `SCH-011`) | 담당자·시간 수정 | 아니오 |

503 (TRANSIENT):

- **변경 요청을 자동으로 다시 보내지 않는다.** 결과가 Commit됐는지 클라이언트가 모를 수 있어 중복 처리가 생길 수 있다.
- **Deadlock·Lock 대기 초과의 503 매핑은 기술 후보다.** 실제 MySQL·Spring Test로 그 오류가 났을 때 Transaction 전체가 안전하게 실패하는지 확인한 뒤 적용한다. 구분할 것: ① MySQL Lock 대기 초과는 기본 설정에서 실패한 문장 하나만 Rollback한다 ② 예외가 `@Transactional` 밖으로 전파되면 Spring이 Transaction 전체를 Rollback할 수 있다 ③ 예외를 안에서 잡아 처리하면 결과가 달라질 수 있다. Spring이 어떤 예외 Class로 변환하는지도 미확인이다 (ML-5 실험 ML-14).
- Frontend는 "잠시 후 다시 시도해 주세요"와 함께 **최신 상태를 다시 조회**하게 하고, 사용자가 직접 다시 시도한다.
- 중복 실행 방지(Idempotency Key 등)는 후속 (G03-D-22 D22-5와 같은 후속 범위).

## 6. 공통 ErrorCode 목록 문서 형식 (S2에서 작성)

87개 전체를 이 형식으로 나열한다. 깊은 검증은 T1이 의심되는 것과 새 기능에 직접 쓰이는 것부터 한다.

| 칸 | 내용 |
|---|---|
| Code | 예: `SUB-012` |
| Enum | `SubstituteRequestErrorCode.SHIFT_ALREADY_STARTED` |
| HTTP | 현재 값 |
| 종류 | INPUT / AUTH / PERMISSION / NOT_FOUND / DUPLICATE / STATE_CONFLICT / STALE / CONFIRM_REQUIRED / FORBIDDEN_CONFLICT / TRANSIENT / INTERNAL |
| Message | 현재 문구 |
| 사용처 | `파일:줄` 전부 |
| 의미 | 어떤 업무 규칙 위반인가 |
| 다음 행동 | 사용자가 할 일 |
| Frontend 의존 | code 문자열로 분기하는 화면 |
| Test 의존 | code를 확인하는 Test |
| 점검 결과 | 정상 / T1 / T2 / T3 + 설명 |
| 개선 방향 | 문구 정정 / 호출 지점 분리 / 새 code / 유지 |
| 변경 전 값 | 변경했을 때만 (HTTP·Message·사용처) |
| 상태 | AS-IS / 정정 후보 (PROPOSED) / 신규 (PROPOSED) / 승인 / 구현 |
| 근거 | Decision ID, EV |

**번호 충돌 검증:** 새 code를 등록할 때 목록과 실제 Enum의 최대 번호를 함께 확인한다. 여러 담당자가 동시에 같은 Prefix의 번호를 쓰지 않도록, 새 code는 **먼저 목록 문서에 번호를 예약한 뒤** 구현한다.

## 7. 검증 계약 (전부 ④ `SPECIFIED` / ⑤ `NOT_RUN`)

번호는 S2에서 부여한다.

| 임시 ID | 내용 | 구분 |
|---|---|---|
| D03-T1 | Migration 후 모든 기존 code·HTTP·Message가 Java와 같다 (Controller Slice로 대표 API 확인, V4-N-01 방식) | MIGRATION |
| D03-T2 | T1-01 정정 후: 시작된 근무 승인 → 새 SUB code 409, `Shift.member` 불변 | PROPOSED_IMPROVEMENT (기존 Test L556·L569 기대값 변경) |
| D03-T3 | T1-02 정정 후: 시작된 근무 수락·거절 → `SUB-023` 409. 요청 생성은 `SUB-012` 400 그대로 | PROPOSED_IMPROVEMENT (기존 Test `SubstituteCandidateServiceTest` L435·L452·L631 기대값 변경) |
| D03-T4 | Lock 대기 초과 주입 → 503, Transaction 전체 Rollback | H6 (ML-5 ML-14) |
| D03-T5 | 새 code마다: HTTP·code·문구·DB 상태 불변 | 각 기능 카드 Test |

## 8. 보장 범위와 제외 범위

| 보장 | 제외 |
|---|---|
| 새 code가 하나의 규칙을 따름 | 기존 87개 형식 통일 |
| 잘못된 안내(T1)의 정정 경로 | API 응답에 `category` Field 추가 |
| 409 종류별 Frontend 대응 구분 | 503 자동 재시도, 중복 실행 방지 |
| 변경 이력 보존 | |

## 9. 재검토 조건

- S2 전체 점검에서 사용자 오류 복구를 막는 T1이 나오면 그 항목을 P0로 올린다.
- Frontend가 종류별 공통 처리를 필요로 하면 응답 `category` Field 추가를 별도 Decision으로 검토한다.
- ML-5 실험에서 Lock 대기 초과가 Transaction 전체 Rollback을 보장하지 않으면 503 매핑을 다시 정한다.

## 10. 변경 이력

| 일자 | 내용 | 이유 |
|---|---|---|
| 2026-10-25 | 초안. 사용자 잠정 결정 D03-1~7, 새 code 규칙, 409·503 대응, 목록 문서 형식 | 사용자 선택: 질문 1 (A), 질문 2 (가). 교차 검토 의견(사용처 전체 확인 후 정정, T1 P0 승격 조건, 재사용 기준, 409 세분, 503 자동 재시도 금지, 변경 전 값 보존, category는 문서 분류) 반영. 코드 정적 확인: `SFT-006` 사용처 1곳, `SUB-012` 사용처 3곳 |
