# Test Inventory

> ③ 문서 리뷰: **DRAFT** (D0-C-R 2026-10-23, D0-D 이관). 상태값은 `docs/STATUS-AXES.md`의 ④ 준비, ⑤ 실행 축을 쓴다.
> 실제 실행 결과는 `docs/evidence/TEST-EVIDENCE.md`(Append-only)에 남기고, 이 Inventory는 EV-ID로 연결만 한다.
> 구성: §1 필드, §2 규칙, §3 이후 Domain 구역 (V4 시범, V2는 ML-H1 카드 관련 부분만).

## 1. 필드

| 필드 | 값 / 규칙 |
|---|---|
| Test ID | `{V}-T-{nn}` 기존 Test, `{V}-N-{nn}` 신규 제안 |
| Domain / Owner | |
| Policy ID | 계약 C-ID / CB-ID / Decision ID |
| Class · Method | 실제 Class 이름과 **Method 이름**. 요약은 DisplayName |
| 종류 | `UNIT` / `SLICE`(WebMvc·DataJpa) / `INTEGRATION`(SpringBoot) / `CONCURRENCY` / `H6`(결정적 경합) / `API`(실제 Filter 포함 HTTP) / `SMOKE` |
| 구분 | `BASELINE`(Java 기존) / `MIGRATION`(Kotlin 회귀용 보강) / `IMPROVEMENT`(승인된 개선) / `PROPOSED_IMPROVEMENT`(승인 전 개선) |
| 환경 | `NONE`(순수 Unit) / `MOCK` / `H2` / `MYSQL_TC`(Testcontainers MySQL 9.7.2) + Session Isolation / `HTTP`. 근거(설정 또는 Log)를 함께 적는다 |
| 유형 | `NORMAL` / `FAILURE` / `BOUNDARY` / `ROLLBACK` / `RACE` |
| 기대 결과 | HTTP Status·code 또는 Exception·ErrorCode, **DB 최종 상태** |
| ④ 준비 | `SPECIFIED` / `IMPLEMENTED` / `BLOCKED` |
| ⑤ 실행 | SHA별 `PASSED` / `FAILED` / `NOT_RUN` / `UNKNOWN` + 근거 |
| 실행 명령 | `./gradlew test --tests '…'` |
| 검증 충분성 | `SUFFICIENT` / `STRENGTHEN`(기존 Test 보강 필요, 이유) / `NEW`(Test 없음). **이 행의 Test Case가 "기대 결과" 칸의 목적을 검증하기에 충분한지**만 판단한다. 연결된 정책·계약 전체가 충분히 검증된다는 뜻이 아니다 |
| 신규 행 추가 칸 | 개선 승인 상태(② 축) / 명세 작성 여부 / 구현 가능 여부(선행조건) |

## 2. 규칙

- Test가 있다(④ `IMPLEMENTED`), CI가 성공했다(Suite), 이 Test가 통과했다(⑤ `PASSED`), 이 Test Case가 자기 목적을 충분히 검증한다(`SUFFICIENT`)는 **네 가지 다른 사실**이다.
- 이미 있는 Test를 새로 쓰라고 권하지 않는다. 보강은 기존 Test 기대값을 바꾸지 않는 범위에서 한다.
- 실행하지 않은 SHA에는 `PASSED`를 쓰지 않는다.

## 3. V4 Substitute (시범)

Domain 요약은 [`docs/delivery/V4-substitute.md`](../delivery/V4-substitute.md) §D. C-ID 원문은 참고 공유본 [`docs/references/G-03-contract-review.md`](../references/G-03-contract-review.md) (승인된 계약 아님). Policy 칸의 C-ID는 D0-E-2B (2026-10-23)에 원문과 대조해 정정했다.

### 3-1. ⑤ 실행 근거 (BASELINE 52개 공통)

| SHA | 상태 | 근거 |
|---|---|---|
| 2차 `243f77c` | `PASSED` (52/52, skipped 0, failures 0) | 작성자 로컬 Test Report `NBE12-wt/develop-243f77c/backend/build/test-results/test/TEST-com.merge.backend.domain.substitute.*.xml` (3차 저장소에 없음, EV-002, 2026-10-22) |
| 2차 `8da2bec` (= 3차 `main`) | `UNKNOWN` (Suite success만) | 2차 push CI Backend Run `39729371810` success. 개별 결과 미보존 |
| 3차 `develop` `cdaaa13` | `UNKNOWN` (Suite success만) | 3차 push CI Backend Run `39748913867` success (EV-008). Gradle 9.3.0 + Kotlin Plugin 환경 |

`243f77c → 8da2bec` 사이 Substitute Production·Test 파일 변경 0. CI는 `./gradlew check --no-daemon`, Test Filter·`@Disabled`·`@Tag` 없음.
공통: 구분 `BASELINE`, ④ 준비 `IMPLEMENTED`. 실행 명령 `./gradlew test --tests 'com.merge.backend.domain.substitute.*'`.

### 3-2. 기존 Test (52개)

환경 근거: NONE·MOCK은 Class 선언(`MockitoExtension`)에서 확인. H2·MYSQL_TC는 §3-3 참조.

| Test ID | Policy | Class · Method | 종류 · 환경 | 유형 | 기대 결과 | 검증 충분성 |
|---|---|---|---|---|---|---|
| V4-T-01 | V4-P10, G03-D-19 | `SentSubstituteRequestResponseTest.test1` (OPEN·시작 전 → OPEN) | UNIT · NONE | NORMAL | OPEN | SUFFICIENT |
| V4-T-02 | V4-P10 | `.test2` (OPEN·시작 지남 → CLOSED/EXPIRED) | UNIT · NONE | BOUNDARY | CLOSED/EXPIRED | SUFFICIENT |
| V4-T-03 | V4-P10, G03-D-19 | `.test3` (ACCEPTED·`now == startAt` → 만료) | UNIT · NONE | BOUNDARY | CLOSED/EXPIRED | SUFFICIENT |
| V4-T-04 | V4-P10 | `.test4` (APPROVED 유지) | UNIT · NONE | NORMAL | APPROVED | SUFFICIENT |
| V4-T-05 | V4-P10 | `.test5` (CLOSED 저장값 유지) | UNIT · NONE | NORMAL | 저장된 사유·`closedAt` | SUFFICIENT |
| V4-T-06 | — | `.test6` (식별자·근무지 Mapping) | UNIT · NONE | NORMAL | Field 일치 | SUFFICIENT |
| V4-T-07 | C-45 (SUB-02) | `SubstituteCandidateServiceTest.findReceivedRequests_Success` | UNIT · MOCK | NORMAL | 목록 | SUFFICIENT (Query는 T-13) |
| V4-T-08 | V4-P05, C-46 | `.acceptCandidate_Success` | UNIT · MOCK | NORMAL | ACCEPTED | STRENGTHEN: DB 최종 상태 |
| V4-T-09 | V4-P06, C-47 | `.rejectCandidate_Success_OtherPendingCandidateExists` | UNIT · MOCK | NORMAL | OPEN 유지 | SUFFICIENT |
| V4-T-10 | V4-P06, C-47 | `.rejectCandidate_LastCandidate_ClosesRequest` | UNIT · MOCK | NORMAL | `ALL_CANDIDATES_REJECTED` | SUFFICIENT |
| V4-T-11 | V4-P05 | `.acceptCandidate_AlreadyResponded_Fail` | UNIT · MOCK | FAILURE | SUB-022 | SUFFICIENT |
| V4-T-12 | V4-P05, C-46, C-30 | `.acceptCandidate_OfficialShiftConflict_Fail` | UNIT · MOCK | FAILURE | SUB-005 | SUFFICIENT |
| V4-T-13 | C-45 (SUB-02) | `SubstituteCandidateRepositoryTest.findReceivedRequests_Success` | SLICE · **H2** | NORMAL | 필터 결과 | STRENGTHEN: MySQL (V4-N-03) |
| V4-T-14 | C-45 (SUB-07) | `.findAcceptedPendingRequests_Success` | SLICE · **H2** | NORMAL | 필터 결과 | STRENGTHEN: MySQL |
| V4-T-15 | V4-P02, P04, C-43, C-44 | `SubstituteRequestServiceTest$CreateTest.create_Success_SavesAsOpen` | UNIT · MOCK | NORMAL | OPEN + Candidate | STRENGTHEN: 후보 필터 조건별 (EMPLOYEE·요청자 제외·충돌 3종) |
| V4-T-16 | V4-P03 | `.create_NoCandidate_DoesNotCreateRequest` | UNIT · MOCK | NORMAL | `requestCreated=false` | SUFFICIENT |
| V4-T-17 | V4-P01 | `.create_Fail_ShiftNotFound` | UNIT · MOCK | FAILURE | SUB-009 | SUFFICIENT |
| V4-T-18 | V4-P01 | `.create_Fail_ScheduleNotPublished` | UNIT · MOCK | FAILURE | SUB-010 | SUFFICIENT |
| V4-T-19 | V4-P01 | `.create_Fail_ShiftCancelled` | UNIT · MOCK | FAILURE | SUB-014 | SUFFICIENT |
| V4-T-20 | V4-P01, C-42 | `.create_Fail_NotOwnShift` | UNIT · MOCK | FAILURE | SUB-011 | SUFFICIENT |
| V4-T-21 | V4-P01, P09 | `.create_Fail_ShiftAlreadyStarted` | UNIT · MOCK | BOUNDARY | SUB-012 | STRENGTHEN: `startAt == now` 경계 없음 |
| V4-T-22 | V4-P01 | `.create_Fail_ActiveRequestExists` | UNIT · MOCK | FAILURE | SUB-013 | SUFFICIENT |
| V4-T-23 | V4-P07, C-48 | `$ApproveTest.approve_success` | UNIT · MOCK | NORMAL | APPROVED | **STRENGTHEN: DB에서 `Shift.member` 재조회 (V4-N-02)** |
| V4-T-24 | V4-P07 | `.approve_fail_invalid_request_status` | UNIT · MOCK | FAILURE | SUB-002 | SUFFICIENT |
| V4-T-25 | V4-P07, P09 | `.approve_fail_past_shift_start_time` | UNIT · MOCK | BOUNDARY | 예외 | SUFFICIENT |
| V4-T-26 | V4-P07 | `.approve_fail_actor_not_in_workplace` | UNIT · MOCK | FAILURE | 예외 | SUFFICIENT |
| V4-T-27 | V4-P07 | `.approve_fail_candidate_not_employee` | UNIT · MOCK | FAILURE | SFT-004 | SUFFICIENT |
| V4-T-28 | V4-P07, C-48, C-30 | `.approve_fail_conflicting_shift` | UNIT · MOCK | FAILURE | SUB-005 | SUFFICIENT |
| V4-T-29 | V4-P07, C-40 | `.approve_fail_conflicting_unavailable_time` | UNIT · MOCK | FAILURE | SUB-006 | SUFFICIENT |
| V4-T-30 | V4-P07 | `.approve_fail_conflicting_active_substitute` | UNIT · MOCK | FAILURE | SUB-007 | SUFFICIENT |
| V4-T-31 | C-45 (SUB-06) | `$ListTest.list_MixedStatusRequests_ReturnsCorrectDtoList` | UNIT · MOCK | NORMAL | 목록 | SUFFICIENT |
| V4-T-32 | SUB-06 | `.list_AcceptedWithoutCandidate_ThrowsException` | UNIT · MOCK | FAILURE | SUB-015 | SUFFICIENT |
| V4-T-33 | SUB-06 | `.list_AcceptedWithDuplicateCandidates_ThrowsException` | UNIT · MOCK | FAILURE | SUB-016 | SUFFICIENT |
| V4-T-34 | SUB-06 | `.list_NotFoundWorkplace_ThrowsException` | UNIT · MOCK | FAILURE | WORKPLACE_NOT_FOUND | SUFFICIENT |
| V4-T-35 | SUB-06, C-45 | `.list_NotManager_ThrowsException` | UNIT · MOCK | FAILURE | 예외 | SUFFICIENT |
| V4-T-36 | V4-P08, C-49 | `$CloseTest.close_OpenRequest_Success` | UNIT · MOCK | NORMAL | `MANAGER_CLOSED` | SUFFICIENT |
| V4-T-37 | V4-P08 | `.close_AcceptedRequest_Success` | UNIT · MOCK | NORMAL | `MANAGER_CLOSED` | SUFFICIENT |
| V4-T-38 | V4-P08 | `.close_RequestNotFound_ThrowsException` | UNIT · MOCK | FAILURE | SUB-001 | SUFFICIENT |
| V4-T-39 | V4-P08 | `.close_NotManager_ThrowsException` | UNIT · MOCK | FAILURE | 예외 | SUFFICIENT |
| V4-T-40 | V4-P08 | `.close_AlreadyApproved_ThrowsException` | UNIT · MOCK | FAILURE | SUB-017 | SUFFICIENT |
| V4-T-41 | V4-P08 | `.close_AlreadyClosed_ThrowsException` | UNIT · MOCK | FAILURE | SUB-017 | SUFFICIENT |
| V4-T-42 | V4-P08 | `.close_ScheduleNotPublished_ThrowsException` | UNIT · MOCK | FAILURE | 예외 | SUFFICIENT |
| V4-T-43 | V4-P08 | `.close_ShiftNotScheduled_ThrowsException` | UNIT · MOCK | FAILURE | 예외 | SUFFICIENT |
| V4-T-44 | V4-P08, P09 | `.close_ShiftAlreadyStarted_ThrowsException` | UNIT · MOCK | BOUNDARY | SUB-018 | SUFFICIENT |
| V4-T-45 | V4-P09 | `.close_ShiftStartsExactlyNow_ThrowsException` | UNIT · MOCK | BOUNDARY | SUB-018 | SUFFICIENT |
| V4-T-46 | V4-P08, C-49 | `.close_DoesNotModifyCandidateStatus` | UNIT · MOCK | NORMAL | Candidate 불변 | SUFFICIENT |
| V4-T-47 | C-45 (SUB-05), V4-P10 | `$SentRequestsTest.getSentRequests_ReturnsLatestFirst_MarksExpired` | UNIT · MOCK | NORMAL | 최신순, 만료 표시 | SUFFICIENT |
| V4-T-48 | SUB-05 | `.getSentRequests_NoRequests_ReturnsEmptyList` | UNIT · MOCK | NORMAL | 빈 목록 | SUFFICIENT |
| V4-T-49 | V4-P01 | `SubstituteRequestConcurrencyTest.concurrentCreateAllowsOnlyOneActiveRequest` | CONCURRENCY · MYSQL_TC RC | RACE | 활성 Request 1 | STRENGTHEN: 경쟁 순서 결정성 (H6) |
| V4-T-50 | V4-P05 | `.concurrentAcceptAllowsOnlyOneCandidate` | CONCURRENCY · MYSQL_TC RC | RACE | ACCEPTED 1 | STRENGTHEN: RR 대조 (SUB-DC-01) |
| V4-T-51 | V4-P06 | `.concurrentRejectClosesRequestWhenNoPendingCandidateRemains` | CONCURRENCY · MYSQL_TC RC | RACE | CLOSED | STRENGTHEN: 결정성 |
| V4-T-52 | V4-P05 | `.concurrentAcceptOnDifferentRequestsAllowsOnlyOneForSameUser` | CONCURRENCY · MYSQL_TC RC | RACE | ACCEPTED 1 | STRENGTHEN: 결정성 |

### 3-3. 환경 근거

| Class | 선언 | DB | 근거 상태 |
|---|---|---|---|
| `SubstituteCandidateRepositoryTest` | `@DataJpaTest`, `@Import(TimeConfig)`. `@ActiveProfiles`·`@AutoConfigureTestDatabase` 없음 | **H2**: `@DataJpaTest` 기본 동작은 내장 DB로 대체, `com.h2database:h2`가 `runtimeOnly` (`build.gradle.kts` L35) | 설정은 VERIFIED_AS_IS, 실제 연결 Log는 미확인 |
| `SubstituteRequestConcurrencyTest` | `@SpringBootTest`, `@ActiveProfiles("test")` | **MySQL 9.7.2 (Testcontainers)**: `application-test.yml` `jdbc:tc:mysql:9.7.2`. Session RC: `application.yml` Hikari `TRANSACTION_READ_COMMITTED` | 설정은 VERIFIED_AS_IS, 실행 Log는 미확인 (같은 설정의 EV-002 Hibernate Log에서 RC 확인) |

### 3-4. 빠진 Test (신규 제안, ⑤ 전부 `NOT_RUN`)

| Test ID | Policy | 내용 | 종류 · 환경 | 구분 | ② 개선 승인 | ④ 준비 | 구현 가능 여부 |
|---|---|---|---|---|---|---|---|
| V4-N-01 | CB-COM-02·04, B1 | 8개 Endpoint의 Status·`code`·Envelope. SUB-01 201/200 분기, SUB-03 응답값 누락 → COM-001 | SLICE (WebMvc) · HTTP | MIGRATION (현재 Java 동작 고정) | 해당 없음 (동작 변경 아님) | SPECIFIED | 가능 (Java에서 먼저 작성) |
| V4-N-02 | C-48 | APPROVE 후 DB의 `Shift.member`·APPROVED·`approvedAt`. 실패 시 세 Entity 불변 | INTEGRATION · MYSQL_TC RC | MIGRATION | 해당 없음 | SPECIFIED | 가능 |
| V4-N-03 | C-45 | Candidate Repository Query를 MySQL에서 (`@ActiveProfiles("test")` + `@AutoConfigureTestDatabase(replace = NONE)`, PR #78 방식) | SLICE · MYSQL_TC | MIGRATION | 해당 없음 | SPECIFIED | 가능 |
| V4-N-04 | C-54 | Substitute Event별 알림 Row 수신자·Type (Commit 후) | INTEGRATION · MYSQL_TC | MIGRATION | 해당 없음 | SPECIFIED | 가능 |
| V4-N-05 | SUB-EV-05 / SUB-DC-01 | 동시 ACCEPT 결정적 재현, RC·RR 대조 | H6 · MYSQL_TC RC+RR | PROPOSED_IMPROVEMENT | PENDING (DL-009, DL-010) | **BLOCKED** (Harness LOCAL_ONLY) | Harness 공유 후 가능 |
| V4-N-06 | ML-SUB-STATE-01 (B+) | 퇴사 후보 `INELIGIBLE` / `CANDIDATE_INELIGIBLE` | — | PROPOSED_IMPROVEMENT | **PROPOSED** | SPECIFIED (작성 금지) | 불가 (기능 없음) |
| V4-N-07 | ML-CONCURRENCY-01-R | 퇴사 vs 후보 생성 경합 (H6-ML-01~03) | H6 · MYSQL_TC | PROPOSED_IMPROVEMENT | **PROPOSED** | SPECIFIED (작성 금지) | 불가 (퇴사 기능 없음). AS-IS 재현만 Test 전용 `left_at` 갱신으로 가능 |

신규 행의 선행 조건은 종류가 다르다.

| 행 | 성격 | 막고 있는 것 |
|---|---|---|
| V4-N-01~04 | Kotlin 전환 전 현재 Java 동작 고정 (`MIGRATION`, 동작 변경 0) | 정책 승인 대상 아님. V4 Owner 확정과 Task 승인 (`docs/ai/AI-RULES.md` §1) |
| V4-N-05 | 현재 동작의 동시성 재현 (Production 변경 0) | 정책 승인이 아니라 DL-009 (Harness 공유)·DL-010 (검증 지위) 결정. Harness가 `LOCAL_ONLY`라 ④ `BLOCKED` |
| V4-N-06·07 | 새 정책(② `PROPOSED`) 구현 검증 | `DECISION-LOG`의 `APPROVED` 전 작성 금지 (④ `SPECIFIED`까지) |

V4-N-07의 "AS-IS 재현"은 새 정책 구현 Test가 아니라 별도 후속 항목(ML-CONCURRENCY-02, 아직 이 Inventory에 없음)이며, V4-N-05와 같이 DL-009가 먼저 결정돼야 한다.
현재 동작 재현 Test 전반에 대한 일반 승인 규칙은 아직 문서화돼 있지 않다 (위 표는 각 행의 기존 기록을 모은 것이다).

## 4. V2 Workplace — ML-H1 관련 부분

V2 구역 전체 목록이 아니다. 정책 카드 [`ML-H1`](../decisions/proposals/ML-H1-membership-history.md)에 연결된 Test만 적었다. V2 전체 Inventory(`V2-T-*` 기존 Test 번호 부여)는 V2 Delivery Sheet를 쓸 때 만든다.

### 4-1. 신규 제안 (⑤ 전부 `NOT_RUN`)

| Test ID | Policy | 내용 (Given → Then) | 종류 · 환경 | 구분 | ② 개선 승인 | ④ 준비 | 구현 가능 여부 |
|---|---|---|---|---|---|---|---|
| V2-N-01 (ML-H1-T1) | ML-H1 H1-a, ML-H1-INV-02 | 퇴사 처리 → 같은 Member id 유지, `leftAt = Clock`, role·`joinedAt` 불변, Row 수 불변 | INTEGRATION · MYSQL_TC | PROPOSED_IMPROVEMENT | **PROPOSED** (G03-D-11·13 `PENDING`) | SPECIFIED (작성 금지) | 불가 (퇴사 Command 없음, ML-4) |
| V2-N-02 (ML-H1-T2) | ML-H1 H1-a, ML-H1-INV-01 | 퇴사 전 Shift·Request·Candidate·Notification → 퇴사 후 Row 유지, `member_id` 불변 | INTEGRATION · MYSQL_TC | PROPOSED_IMPROVEMENT | **PROPOSED** | SPECIFIED (작성 금지) | 불가 (ML-4, 보존 범위는 ML-2) |
| V2-N-03 (ML-H1-T3) | G03-INV-03, C-11, C-12 | 종료 Row(5-인자 생성자로 준비) → `requireManager`·구성원 목록·내 근무지 목록에서 제외, 관리자 행위는 WPL-003 / WPL-004 | SLICE (DataJpa) + UNIT · H2 또는 MYSQL_TC (미정) | MIGRATION (현재 Java 동작 고정) | 해당 없음 (동작 변경 아님) | SPECIFIED | 가능. Task 승인 필요 (`docs/ai/AI-RULES.md` §1) |
| V2-N-04 (ML-H1-T4) | ML-H1 H1-d | 종료 Row가 있는 User가 같은 초대 코드로 참여 → 새 ErrorCode (후보 WPL-006, 409), 새 Row 0 | UNIT · MOCK + SLICE (WebMvc) + INTEGRATION | PROPOSED_IMPROVEMENT | **PROPOSED** (ErrorCode 추가는 AI-RULES §2 승인 대상) | SPECIFIED (작성 금지) | 승인 후 가능 (퇴사 Command 없이 종료 Row 준비 가능) |
| V2-N-05 (ML-H1-T5) | ML-H1-INV-02 | 이미 퇴사한 Member에 다시 퇴사 → `leftAt` 불변. 응답은 ML-4에서 결정 | INTEGRATION · MYSQL_TC | PROPOSED_IMPROVEMENT | **PROPOSED** | SPECIFIED (작성 금지) | 불가 (ML-4) |

### 4-2. 회귀 대상 기존 Test (ID 미부여, 기대값 변경 0)

H1-d를 구현할 때 기대값이 그대로여야 하는 기존 Test다. ⑤ 실행 상태는 이 구역에서 확인하지 않았다 (V2 구역을 만들 때 기록).
경로 기준: `backend/src/test/java/com/merge/backend/domain/workplace/`

| Class · Method | 업무 상황 | 기대 결과 (불변) | 비고 |
|---|---|---|---|
| `service/WorkplaceServiceTest.rejectDuplicateJoin` | 현재 소속자 재가입 | `ALREADY_JOINED_WORKPLACE`, `save` 0회 | **Arrange(Mock 준비) 변경 가능성 있음** (Strict Stubs). 절차: `docs/decisions/PROCESS-CHARTER.md` §6-3 |
| `service/WorkplaceServiceTest.joinAsEmployee` | 이력 없는 사용자 가입 | EMPLOYEE, `leftAt` null, `save` 1회 | — |
| `service/WorkplaceServiceIntegrationTest.rejectDuplicateJoin` | 현재 소속자 재가입 (DB) | `ALREADY_JOINED_WORKPLACE` | — |
| `service/WorkplaceServiceIntegrationTest.joinWorkplace` | 정상 가입 (DB) | Row 생성, `leftAt` null | — |
| `controller/WorkplaceControllerTest.rejectDuplicateJoin` | Service가 WPL-002를 던짐 | 409, `code = WPL-002` | — |
| `repository/WorkplaceMemberRepositoryTest.rejectDuplicateMembershipAtDatabaseLevel` | 같은 조합 두 번 저장 | `DataIntegrityViolationException` | 재가입 구현(ML-H2) 시 기대값 변경 대상. 그때 별도 승인 |
