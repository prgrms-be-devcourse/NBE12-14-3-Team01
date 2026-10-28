# Test 공백 분석 — 기존 Test가 사용자 경로와 불변식을 어디까지 보호하는가

| 축 | 상태 |
|---|---|
| 기준 | `a148a62` Java 기준선 실행 결과 (EV-010·011: 394/394 PASS × 2, `LOCAL_ONLY`) + Test 코드 정적 분석 + [`USER-PATH-REVIEW`](../contract/USER-PATH-REVIEW.md) UP-01~07 |
| ③ 문서 | `DRAFT` (2026-10-25) |
| 정책 | 인용 정책은 모두 사용자 잠정 결정 / 팀 `PENDING`. 이 문서는 정책을 정하지 않는다 |
| 원칙 | **Test가 있고 통과한다 ≠ 그 사용자 경로가 검증됐다.** 아래 판단은 Test가 실제로 무엇을 확인하는지(Assertion)를 기준으로 했다. "정적 확인"과 "추정"을 구분해 표시한다 |

## 1. 검증 깊이 기준

| 수준 | 뜻 | 예 |
|---|---|---|
| L0 | Test 없음 | — |
| L1 | 업무 규칙 (Unit·Mock) | 상태 전이, ErrorCode 반환. DB·HTTP·Transaction 없음 |
| L2 | HTTP 계약 | Status, `code`, JSON Field (실제 Security Filter 포함 여부를 따로 표시) |
| L3 | DB 최종 상태 | 실제 DB에 저장·재조회, Rollback, 제약 |
| L4 | 부작용 | Commit 후 알림 Row·Event가 실제로 생기는지 |
| L5 | 경로 전체 | 여러 Command를 이어서 최종 상태까지 |

## 2. 기존 Test의 구조 (정적 확인, `a148a62`)

| 사실 | 수치 | 의미 |
|---|---|---|
| DB 사용 분포 | DB 없음 258 Method (20 Class) / MySQL Testcontainers 106 (16 Class) / H2 9 (2 Class) | 69%는 JPA·Transaction·SQL을 거치지 않는다 |
| HTTP Test가 있는 Controller | 10개 중 4개 (`ApiV1User`, `RegularShiftPattern`, `Schedule`, `Workplace`) | **HTTP Test 없음: Substitute 3개(`Substitute`·`SubstituteRequest`·`SubstituteCandidate`), `Notification`, `Shift`, `UnavailableTime`** |
| 실제 인증 경계를 확인하는 HTTP Test | `ApiV1UserControllerTest`, `WorkplaceSecurityIntegrationTest`, `CustomAuthenticationFilterTest` — 실제 요청 경로·토큰 조건·Assertion별로 증명 범위를 읽는다 | `@AutoConfigureMockMvc`만으로 토큰 검사 실행을 단정하지 않는다. 잘못된 토큰/형식 오류 등은 `CustomAuthenticationFilterTest`의 실제 응답을 참조 |
| Security 설정은 포함하지만 실제 토큰 인증 근거가 아닌 HTTP Test | `RegularShiftPatternControllerTest`: `/workplaces/...`, `@WithMockUser`, `@MockitoBean Rq` | URI가 `/api/`로 시작하지 않아 CustomAuthenticationFilter의 토큰 검사가 생략된다. Controller·응답/업무 계약 근거로만 사용 (DOMAIN-DEVELOPMENT-GUIDE §4) |
| Filter를 제외하거나 실제 Chain을 구성하지 않는 HTTP Test | `WorkplaceControllerTest`는 `addFilters = false`, `ScheduleControllerTest`는 Mockito 단독 | 실제 Security Filter 경계 미검증 |
| 알림 Listener | 7개 모두 `@TransactionalEventListener(AFTER_COMMIT)` + 예외를 잡아 로그만 남김. 저장은 `NotificationService`의 `REQUIRES_NEW` | **Commit 후 실제 알림 Row까지 확인하는 Test는 근무표 공개 1개뿐** (`SchedulePublishedEventListenerIntegrationTest`). 대타 6개 Event는 Unit(Mock)만 |
| 동시성 Test | 대타 8 (동시 4 + 순서 강제 4), 근무 4, 불가능 시간 3, 알림 읽음 1, 근무 수정 Lock 2 | 순서 강제(`CountDownLatch`)는 대타 4개뿐 |
| 공유 MySQL DB | `jdbc:tc:mysql:9.7.2:///db_test?TC_DAEMON=true` — JVM 동안 Container 1개 재사용. MySQL Test 16 Class 중 **Class 단위 `@Transactional` 롤백이 없는 Class 11개, 데이터 정리(`deleteAll`·`TRUNCATE`·`@Sql`) 0개** | Commit된 Test 데이터가 다음 Test까지 남는다 |
| 공유 DB의 실제 영향 | 전체 Table 조회·건수 Assertion은 `WorkplaceServiceIntegrationTest.rollbackWorkplaceCreation`의 `count()` 1곳뿐이고 **같은 Test 안의 전후 비교**다. 다른 Test는 고유 값(이메일·초대 코드)으로 충돌을 피한다 (`SubstituteRequestConcurrencyTest` 주석) | **현재 순서 의존 Assertion은 발견하지 못함** (정적 확인). Test가 순차 실행이라 전후 비교가 안전. 그러나 고유 데이터만으로는 충분하지 않다 — 남은 데이터가 **조회 결과·업무 판정**에 끼어들 수 있다: 앞 Test의 활성 대타 요청이 같은 사용자 기준 목록에 섞임, 끝나지 않은 공식 근무가 시간 충돌 판정에 걸림, 페이지네이션 결과가 달라짐, 동시성 Test가 예상 밖 상태의 데이터를 만남. → 원칙: **Test 데이터는 구분할 수 있어야 하고, 조회 범위와 정리 전략을 명시한다** (DOMAIN-DEVELOPMENT-GUIDE §3-2). 정리 방식(Test별 고유 근무지·사용자, 명시적 Cleanup, 격리 DB, 공용 Fixture) 선택은 E4·E5 MySQL 검증 후 팀이 정한다 |

## 3. 경로별 검증 수준

"도달한 최고 수준"은 **그 경로의 어딘가에서 한 번이라도** 그 수준까지 확인했다는 뜻이다. 아래 수준이 모두 채워졌다는 뜻이 아니다 (예: UP-02의 L4는 공개 → 알림 한 구간뿐이고 Shift API의 HTTP는 L0). 실제로 검증되는 단계와 안 되는 단계는 오른쪽 칸들을 본다.

| 경로 | 기존 Test (대표) | 도달한 최고 수준 | 정상 | 예외·권한 | HTTP 계약 | DB 최종 상태 | 부작용 (알림) | 소속 격리 | 경로 전체 (L5) |
|---|---|---|---|---|---|---|---|---|---|
| UP-01 소속·재가입 | `WorkplaceServiceTest`, `WorkplaceServiceIntegrationTest`, `WorkplaceControllerTest`, `WorkplaceSecurityIntegrationTest`, `WorkplaceMemberRepositoryTest` | L3 (가입·Unique) | 가입 | 중복 가입 409, 비인증 (Filter) | 일부 (`WorkplaceController`는 Filter 끔) | 있음 (Unique 제약 MySQL) | 없음 (가입 알림은 새 기능) | 알림 목록·후보 응답·공개 검사는 L1 (Mock) | **없음** — 재가입은 기능 없음 (AS-IS 거부) |
| UP-02 DRAFT·공개 | `ScheduleServiceTest`, `ShiftServiceTest`, `ShiftModifyIntegrationTest`, `ScheduleRollbackIntegrationTest`, `ShiftScheduleConcurrencyTest`, `SchedulePublishedEventListenerIntegrationTest`, `RegularShiftPatternControllerTest` | **L4** (공개 → 알림 Row) | 있음 | 공개 실패 사유 다수 (L1), Rollback (L3) | Pattern은 HTTP 계약만 (토큰 필터 검사 생략), Schedule은 Mockito 단독, **Shift HTTP 없음** | 있음 | 공개 알림만 | 공개 시 현재 구성원 검사 (L1) | 부분 (공개 → 알림) |
| UP-03 공개 근무 변경 | 없음 | **L0** | — | — | — | — | — | — | 새 기능 |
| UP-04 불가능 시간 | `UnavailableTimeServiceTest` (37), `UnavailableTimeRepositoryTest` (**H2**), `UnavailableTimeConcurrencyTest`, V4-T-29·66·91 | L3 | 있음 | 겹침·과거 시간 (L1) | **없음** | MySQL 동시성 일부, Repository는 H2 | 없음 | — | 대타 수락과의 연결은 V4-T-91 (L3) |
| UP-05 대타 | Candidate/Request Service (69, L1), `SubstituteFlowIntegrationTest` (6, L3), `SubstituteRequestConcurrencyTest` (8), `SubstituteCandidateRepositoryTest` (**H2**) | L3 + 경합 | 있음 | 매우 많음 (L1) | **없음** (Controller 3개) | Flow 6개 (`flushAndClear` 재조회) | **Unit만** (6개 Event, Mock) | 퇴사 후보·수락자 (L1~L3, 5-인자 생성자 Fixture) | 부분 (V4-T-88~93, 알림 미확인) |
| UP-06 퇴사 | 퇴사 기능 없음. 퇴사 상태 Fixture로 확인하는 Test는 있음 (V4-T-59·69·79·93, 공개 검사) | L1~L3 (퇴사 **상태**에 대한 반응만) | — | — | — | — | — | — | 새 기능 |
| UP-07 알림 | `NotificationServiceTest` (14, L1), `NotificationTest` (3), `NotificationReadConcurrencyTest` (1, L3), 공개 알림 통합 (1, L4) | L4 (공개만) | 생성·목록·읽음 | 남의 알림·없는 알림·과거 소속 (L1) | **없음** | 읽음 동시성만 | 공개만 | 목록 현재 소속 (L1, Query는 MySQL Test 없음) | 없음 |

## 4. 공백 목록 (A~E)

ID 규칙: `G-<분류><번호>`는 **공백 ID**다. Test를 만들 때는 TEST-INVENTORY 규칙대로 정식 Test ID를 붙인다 (이미 명세된 것은 그 ID를 적음). 우선순위는 **Test 보강 우선순위**이며 기능 P0/P1과 다르다.

### A. Kotlin 전환 전에 Java에서 먼저 고정할 계약·회귀 Test

| ID | 공백 | 근거 | Domain | 우선 | 시점 | 관련 Test ID |
|---|---|---|---|---|---|---|
| G-A1 | **HTTP 계약 Snapshot** (Status, `ApiResponse` 형식, Field 이름·자료형, null 포함 여부, 날짜 형식, ErrorCode·문구) — HTTP Test가 없는 Controller 6개. **한 번에 전부 만들지 않는다:** 순서 = G-A2 공통 → 알림 핵심 → 대타 핵심 → Shift·불가능 시간 (각 Domain 전환 직전). Endpoint마다 대표 성공·대표 실패·Validation 실패부터 | §2: Controller 10개 중 6개 HTTP 0. DTO record → `data class` 전환에서 JSON이 바뀌어도 지금 Test는 모른다 (KOTLIN-REVIEW-CHECKLIST §1) | V4 (3), V5, V3 (Shift·UnavailableTime) | **최우선** | 그 Domain 전환 **전** | V4-N-01 (명세됨). V5-N-21~30은 PR #16에서 추가·병합됐고 PR #60에서 Kotlin으로 전환됐다. CI는 Suite 성공, 개별 XML 근거는 보존되지 않음. V3는 등록 필요 |
| G-A2 | **공통 응답·예외 형식** (`ApiResponse`의 `success` Field 이름, `GlobalExceptionHandler` 매핑, Validation 실패 `COM-001`). **→ Java 기준선 계약은 2026-10-26 PR #15에서 고정·병합 (CM-N-01~06, LOCAL_PASSED·Suite CI 성공). Kotlin 대응 Test는 PR #79에서 전환 중이며 현재 Head CI는 Suite 성공, 개별 XML 근거는 없음. 남은 공백은 §4-1** | `ApiResponse.success`의 Java 접근자 `isSuccess()`와 JSON 이름이 전환에서 갈릴 수 있음 (S2-6B) | 공통 | **최우선** | `global` 전환 전 | `ApiResponseTest`·`GlobalExceptionHandlerTest` 보강 (JSON 수준) |
| G-A3 | **Commit 후 알림 생성 통합 Test (현재 Java 동작 고정)** — **지금 Java에 있는** 대타 6개 Event (요청 생성 `SUBSTITUTE_REQUEST_RECEIVED`, 후보 없음 `SUBSTITUTE_NO_CANDIDATE`, 수락 `SUBSTITUTE_ACCEPTED`, 전원 거절 `SUBSTITUTE_ALL_REJECTED`, 승인 `SUBSTITUTE_APPROVED`, 관리자 종료 `SUBSTITUTE_MANAGER_CLOSED`). 확인할 경우: ① 업무 Commit → 받는 사람·Type·Row 수 ② **업무 Rollback → 그 업무의 알림 0건** ③ **알림 저장 실패 → 업무 Commit은 유지** (AFTER_COMMIT 이후라 되돌릴 수 없음, Listener가 예외를 잡아 로그만) ④ 받는 사람 조회 실패 시 그 사건 알림 전체 누락 (AS-IS 한계). V3 새 Type(`SUBSTITUTE_SHIFT_CHANGED` 등)·받는 사람 규칙(R1~R5, X-4)·퇴사자 필터는 **C (Improvement)**이며 여기 기대값으로 넣지 않는다 | §2: Unit(Mock)만. Kotlin 전환에서 Listener Proxy·`REQUIRES_NEW`·phase가 바뀌어도 Unit Test는 모른다 | V5 (+V4 Event) | **최우선** | V4·V5 전환 전 | V4-N-04 (명세됨). **범위 확인 필요 (2026-10-26):** Listener는 7개(대타 6 + 근무표 공개 1). 근무표 공개 알림의 Rollback·알림 실패 경우를 이 항목에 넣을지는 V5-J3 분석에서 정한다 — 기존 `SchedulePublishedEventListenerIntegrationTest`는 Service를 Mock으로 둬 Commit 후 호출 시점만, V3 `ShiftScheduleConcurrencyTest.publishAndModifyKeepAssigneeAndNotificationConsistent`가 공개 알림 Row 수를 일부 확인 (중복 작성 주의) |
| G-A4 | **Transaction Rollback·원자성** — 대타 승인 실패 시 Request·Candidate·Shift 불변 | 근무표·근무지는 Rollback Test 있음, 대타는 없음 | V4 | 높음 | V4 전환 전 | V4-N-02 (명세됨) |
| G-A5 | **실제 Security Filter 경계** — 대타·알림·근무 API의 무토큰 401, 다른 근무지 403/404 (현재 값 그대로) | §2의 실제 인증 경계 근거를 참조. Pattern은 토큰 검사 근거에서 제외. G03-OQ-01 (`/workplaces/**` permitAll) | 각 Domain | 높음 | 그 Domain 전환 전 | AUTH-EV-01 (IMP-01) |
| G-A6 | **Test 없는 ErrorCode 11개** | TEST-INVENTORY §5-3 | V3, V4 | 중간 | 전환 전 | V3-N-24~32, V4-N-48 (명세됨) |
| G-A7 | **H2로만 검증되는 Query를 MySQL에서** | Repository 2 Class가 H2. 받은 요청·수락 대기·겹침 Query가 MySQL에서 같은지 모름 | V4, V3 | 중간 | 전환 전 | V4-N-03 (명세됨). `UnavailableTimeRepositoryTest`는 등록 필요 |
| G-A8 | **알림 목록 현재 소속 Query를 MySQL에서** (`rm.leftAt is null`) | 지금은 Service Mock으로만 확인 | V5 | 중간 | V5 전환 전 | 등록 필요 (NT-T3 = V5-N-03은 재가입 후 경우로 Improvement) |

### B. 현재 Java의 결함 또는 재현이 필요한 문제

| ID | 내용 | 근거 | Domain | 처리 |
|---|---|---|---|---|
| G-B1 | 대타 승인이 권한 확인 전에 Request를 잠금·상태 노출 | API-CATALOG CHG-06 (정적 확인) | V4 | 재현 = E5 (M3-T2 = V4-N-45). 수정은 Improvement (W안, 팀 승인 후) |
| G-B2 | 알림 읽음이 권한 확인 전에 Row를 잠금 | USER-PATH-REVIEW §0-1 | V5 | 재현 = E5. 수정은 CHG-07 (응답 code는 G03-D-10 대기) |
| G-B3 | 관리자 화면에서 만료 요청이 흔적 없이 사라짐 | SUB-06 `startAt > now`, 만료 알림 없음 | V4 | AS-IS로 기록. 해결은 API-SB-02 (P0 후보) |
| G-B4 | 받는 사람 조회에 퇴사 필터 없음, 한 명이라도 조회 실패 시 그 사건 알림 전체 누락 | NOTIFICATION-TYPES §1, NT-T12 | V5 | AS-IS 고정 (V5-N-12, MIGRATION). 개선은 R1~R5 |
| G-B5 | 의미가 틀린 ErrorCode 5건 (T1) | ERROR-CODES §2 | V3, V4 | P1 Improvement |

**원칙:** B는 Kotlin 전환 PR에서 고치지 않는다 (전환 결과와 개선 결과가 섞임). 보안 문제나 데이터 손실이 확인되면 별도 우선 Task로 분리한다.

### C. V3 Improvement 신규 정책 Test

이미 명세됨: V2-N-06~54, V3-N-01~23·33, V4-N-08~47·49~54, V5-N-01~20 (TEST-INVENTORY §5-1). 경로 전체 Test: V2-N-53 (퇴사), V2-N-54 (재가입), V3-N-33 (공개 근무 변경), V5-N-19 (알림 ↔ 대체 화면). 모두 정책 승인 후 작성.

### D. MySQL·Transaction·Lock·동시성 기술 검증

| ID | 내용 | 시점 |
|---|---|---|
| G-D1 | 순서 강제 Test가 대타 4개뿐 — 근무·불가능 시간·알림 읽음은 동시 실행(우연)에만 의존 | E5 이후 정식 회귀 후보 |
| G-D2 | ML-5 Lock 순서 반례 (R1~R4), H6-C1~C8 | E5 (DL-009) |
| G-D3 | Hibernate Schema (Enum Column 형식), U2' 제약, Deadlock·Lock 대기 예외와 Rollback | E4 |
| G-D4 | API-SB-02 Query (경계·정렬·페이지·실행 계획) | E6 |
| G-D5 | 동시성 Test의 반복 안정성 (2회만 확인) | 선택: 동시성 Class 반복 실행 |

### E. Frontend·사용자 경험·실패 후 복구

Frontend에 Test 체계가 없다 (`eslint`만). **이 공백은 해소되지 않았다.** 지금은 아래 항목을 **경로별 수동 확인 목록**으로 관리한다 (확인 결과는 TEST-EVIDENCE에 수동 확인으로 기록). 자동화 후보: Playwright 등 E2E 도구로 G-E1~E5 — 도구 도입·담당은 팀이 정한다.

| ID | 확인할 것 | 근거 |
|---|---|---|
| G-E1 | 알림 읽음 실패 시 403·404는 목록 재조회, 네트워크·5xx는 유지 | FRONTEND-CONTRACT §2 (G-1) |
| G-E2 | 열린 받은 요청 화면에서 `SUB-023` 후 목록 재조회 | FRONTEND §4-2 |
| G-E3 | 퇴사 직후 열린 화면에서 `WPL-003` → 근무지 목록 이동 | FRONTEND §6 |
| G-E4 | 관리자 끝난 요청 탭의 "조치 필요"·"확인 필요" 표시 규칙 | FRONTEND §3-4 |
| G-E5 | 확인 필요(`CONFIRM_REQUIRED`) 목록 재전송, STALE 후 미리보기 재조회 | FRONTEND §3-1·3-2 |
| G-E6 | 내 근무 상태 철자 `CANCELED` ↔ `CANCELLED` | FRONTEND §3-3 |

### 4-1. 진행 현황 (2026-10-26)

**G-A2 — 현재 Java 공통 응답의 대표 계약 고정 완료** (모든 API 계약을 검증한 것이 아님. Kotlin 전환 후 같은 Test로 재검증 필요)

| 해결 (Test로 고정) | Test ID | 근거 |
|---|---|---|
| 공통 응답 key가 정확히 `success`·`code`·`message`·`data`, `success` boolean | CM-N-01·04·05·06 | EV-013·014 |
| `data: null`이어도 key 유지 (직렬화·HTTP 모두) | CM-N-02·03·05 | EV-013·014 |
| 회원가입 성공 HTTP 계약 (201, `data` = `id`·`email`·`name`) | CM-N-04 | EV-013·014 |
| 업무 오류 HTTP 계약 (`AUTH-002` → 401) | CM-N-05 | EV-013·014 |
| Validation 실패 `COM-001`, `data.fieldErrors[].{field, message}` | CM-N-06 | EV-013·014 |

| 아직 검증하지 않음 | 다음 |
|---|---|
| ~~인증 Filter·Security가 직접 쓰는 응답 `AUTH-004`·`005`·`006`과 정상 Token 경로~~ → 아래 V5-J2 표로 분리 | — |
| 도메인 DTO의 날짜·시간 형식 (`LocalDateTime` 직렬화) — **알림 DTO는 V5-J2로 고정, 다른 Domain DTO는 남음** | 각 Domain G-A1 |
| Commit 후 알림 생성 (AFTER_COMMIT), Rollback·알림 실패 | V5-J3 (G-A3) |
| `COM-002`(요청 본문 오류), `COM-003`, `COM-500` 경로 | 필요 시 G-A2 추가 |
| 각 Domain API의 HTTP 계약 | G-A1 (Domain별, 전환 직전) — G-A2와 별개 |
| Kotlin Migration 이후 계약 유지 | 전환 PR에서 CM-N-01~06 재실행 |

**V5-J2 — Java 기준선 알림 HTTP 계약 고정 (2026-10-26; Java Test PR #16 병합, Kotlin 전환 PR #60 병합)**

V5-N-21~30의 Java 기준선 로컬 실행은 EV-017·018, PR #16 CI Suite는 EV-020이다. PR #60은 해당 HTTP Test Class를 Kotlin으로 전환했고 Backend/Docker CI Suite가 성공했다. Kotlin 개별 Test XML은 보존되지 않아 Test별 실행 상태는 `UNKNOWN`이다.

| 해결 (Test로 고정) | Test ID | 근거 |
|---|---|---|
| 알림 API의 인증 필수: 무토큰 → Security entry point 401 `AUTH-004` | V5-N-21·22 | EV-017·018 |
| 정상 Token이 실제 `CustomAuthenticationFilter`를 통과해 인증됨 (정상 Token Test 7개) | V5-N-23~29 | EV-017·018 |
| 목록 범위·정렬·항목 key, 읽음·다시 읽음, 없는 ID 404, 남의 알림·퇴사한 소속 403 (현재 값) | V5-N-23~29 | EV-017·018 |
| 알림 DTO 날짜 문자열 (시간대 없음, 소수 초 0·6자리·끝 0 생략), `readAt: null` key 유지 | V5-N-30 | EV-017·018 |

| 아직 검증하지 않음 | 다음 |
|---|---|
| `AUTH-005`(Bearer 형식 아님)·`AUTH-006`(잘못된 Token) — 기존 `CustomAuthenticationFilterTest`가 `/auth/me`에서 보호. 알림 API에서의 확인은 P2 (V5-N-32) | 필요 시 |
| 다른 Domain DTO의 날짜 형식 | 각 Domain G-A1 |
| Commit 후 알림·Rollback·알림 실패 | V5-J3 (G-A3) |
| 목록 Query·소속 격리를 MySQL에서 (Query 단위) | V5-J4 (G-A8) |

**Known Limitation과 미실행 (2026-10-26):**

| ID | 내용 | 상태 |
|---|---|---|
| R1 | 현재 Java MockMvc에서 **Controller가 없는 URL** 요청이 404가 아니라 **500 `COM-500`**으로 처리되는 것을 관찰 (`NoResourceFoundException` 처리 없음 → 마지막 `Exception` 처리). 의도된 최종 오류 계약이 아니며 **Test로 고정하지 않는다.** 올바른 Status·ErrorCode는 별도 결정 후 수정 (MIGRATION-MATRIX §4 공통, ERROR-CODES `COM-500`). 지원하지 않는 HTTP Method(405 예상) 경로가 500이 되는지는 **미검증 (추정)** | Known Limitation |
| R2 | 날짜 문자열의 소수 초 길이가 매번 다를 수 있다 (`…:00`, `….5`, `….123456`). Frontend의 해석 방식은 Node v24(V8)에서만 세 형식 모두 올바름을 확인. **Safari 등 다른 브라우저는 미검증** (ECMAScript 표준 형식은 소수 초 3자리) | 미검증 (Frontend 통합 과제) |
| C5 | G-A2(PR #15)와 V5-J2(PR #16)는 병합됐고, PR #79 검증 Head `2af320c…`에서 둘을 포함한 Backend/Docker CI Suite가 성공했다. 최신 develop `0aec24f…` 기준 임시 통합 `clean check`도 514/514 PASS였다. GitHub CI Artifact에 JUnit XML이 없어 개별 Test 결과는 확인할 수 없다. 과거 예상 410건은 현재 실행 건수로 사용하지 않는다. | Suite 통합 확인 완료; Test별 결과 `UNKNOWN` |

## 5. Kotlin 전환 전 최우선 회귀 Test (요약)

1. **G-A1 HTTP 계약 Snapshot** — 대타·알림부터 (HTTP Test 0인 Domain)
2. **G-A2 공통 응답·예외 형식** — 모든 Domain의 기반. 대표 Java 계약은 PR #15에서 고정·병합됐고, Kotlin 대응 Test는 PR #79에서 전환 중이다. Suite는 성공했으나 개별 Test 결과는 `UNKNOWN`; 남은 계약 공백은 §4-1
3. **G-A3 Commit 후 알림 생성 통합** — 대타 6개 Event
4. **G-A4 대타 승인 Rollback**, **G-A5 Security Filter 경계**
5. G-A6~A8

각 Domain은 자기 Domain을 Kotlin으로 바꾸기 **전에** 해당 A 항목을 Java 상태에서 작성·통과시키고 EV로 남긴다 ([`DOMAIN-DEVELOPMENT-GUIDE`](../delivery/DOMAIN-DEVELOPMENT-GUIDE.md) §2).

## 6. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-25 | 초안. E1 기준선과 정적 분석 기반 검증 수준 기준(L0~L5), 기존 Test 구조(DB 분포, HTTP Controller 공백, 알림 Listener, 공유 MySQL DB 정리 방식), 경로별 수준, 공백 A8·B5·D5·E6, Kotlin 전환 전 최우선 회귀 Test |
| 2026-10-25 | Commit 전 보완: §3 "최고 수준"의 뜻 (누적 아님), G-A1 작성 순서 (공통 → 알림 → 대타 → 근무), G-A3를 현재 Java Event 6개 고정으로 한정하고 Commit·Rollback·알림 실패·묶음 누락 4경우 명시 (V3 규칙은 C), 공유 DB에서 고유 데이터만으로 부족한 경우와 정리 전략 결정 시점, Frontend 공백 미해소·자동화 후보 구분 |
| 2026-10-26 | V5-J2 진행 반영: §4-1 AUTH 행 분리 (V5-J2 = `AUTH-004`·정상 Token, `005`·`006`은 기존 Filter Test), 날짜 행을 "알림 완료·다른 DTO 남음"으로, V5-J2 해결·미검증 표, R1·R2 Known Limitation, C5 결합 미실행. G-A1 관련 ID에 V5-N-21~30, G-A3에 근무표 공개 포함 여부와 기존 V3 Test 메모 |
| 2026-10-26 | G-A2 진행 반영: G-A2 행 상태, §4-1 진행 현황 (해결한 계약 5종과 남은 6종), §5 요약. Test 코드는 PR #15 (Draft, `develop` 미반영) |
| 2026-10-26 | PR #14 피드백 반영안: Pattern Test의 실제 인증 경계 분류를 §2·UP-02·G-A5에서 정정. 기존 테스트 코드/기대값 변경 없음 |
