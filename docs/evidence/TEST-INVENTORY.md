# Test Inventory

> ③ 문서 리뷰: **DRAFT** (D0-C-R 2026-10-23, D0-D 이관). 상태값은 `docs/STATUS-AXES.md`의 ④ 준비, ⑤ 실행 축을 쓴다.
> 실제 실행 결과는 `docs/evidence/TEST-EVIDENCE.md`(Append-only)에 남기고, 이 Inventory는 EV-ID로 연결만 한다.
> 구성: §1 필드, §2 규칙, §3 이후 Domain 구역 (V4 시범, V2는 ML-H1 카드 관련 부분만), §5 Cross-Domain 카드의 임시 Test ID.

## 1. 필드

| 필드 | 값 / 규칙 |
|---|---|
| Test ID | `{V}-T-{nn}` 기존 Test, `{V}-N-{nn}` 신규 제안. `{V}`는 Domain 코드 V1~V5, **공통 `global`은 `CM`** (2026-10-26 G-A2에서 도입, 기존 ID 사용 0건 확인. ErrorCode 접두사 `COM-`과 다름) |
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

### 3-1. ⑤ 실행 근거 (PR #5 이전 BASELINE 52개)

| SHA | 상태 | 근거 |
|---|---|---|
| 2차 `243f77c` | `PASSED` (52/52, skipped 0, failures 0) | 작성자 로컬 Test Report `NBE12-wt/develop-243f77c/backend/build/test-results/test/TEST-com.merge.backend.domain.substitute.*.xml` (3차 저장소에 없음, EV-002, 2026-10-22) |
| 2차 `8da2bec` (= 3차 `main`) | `UNKNOWN` (Suite success만) | 2차 push CI Backend Run `39729371810` success. 개별 결과 미보존 |
| 3차 `develop` `a148a62` | **`PASSED`** (394/394 × 2회, `LOCAL_ONLY`) | 로컬 macOS·GraalVM 25·Testcontainers MySQL 9.7.2. EV-010·011 (XML 원본은 저장소 밖), check는 EV-012 |
| 3차 `develop` `cdaaa13` | `UNKNOWN` (Suite success만) | 3차 push CI Backend Run `39748913867` success (EV-008). Gradle 9.3.0 + Kotlin Plugin 환경 |

`243f77c → 8da2bec` 사이 Substitute Production·Test 파일 변경 0. CI는 `./gradlew check --no-daemon`, Test Filter·`@Disabled`·`@Tag` 없음.
공통: 구분 `BASELINE`, ④ 준비 `IMPLEMENTED`. 실행 명령 `./gradlew test --tests 'com.merge.backend.domain.substitute.*'`.

**PR #5 이후 (S2-5B, 2026-10-25 정적 확인):** PR #5 (`c0427c2` → `31ecaee`)가 기존 Test 본문 다수와 이름 3개를 바꾸고 41개를 추가했다. 위 `243f77c`의 `PASSED`는 **PR #5 이전 본문** 기준이라 현재 본문으로 옮기지 않는다. 3차 `develop` `a148a62` 기준 대타 Test 93개의 ⑤는 S4-E1 이전에는 `UNKNOWN`이었다. **S4-E1 (2026-10-25): `a148a62`에서 전체 394건 PASS 2회 (EV-010·011, `LOCAL_ONLY`)** → V4-T-01~93 ⑤ = `PASSED @a148a62 (LOCAL_ONLY, EV-010·011)`. 개별 결과는 저장소 밖 XML (`~/lec/Project3/s4-evidence/E1-run1·2/xml/`). CI 결과가 아니며, 새 Test(`SPECIFIED`)는 여전히 `NOT_RUN`이다.

#### 3-1-1. 대타 Test 집계 (정적 확인, Test 실행 아님)

세 수는 같을 필요가 없다. Test ID는 **Method** 단위로 붙인다.

| 기준 | 수 | 근거 |
|---|---|---|
| Test Method (`@Test`·`@RepeatedTest`·`@ParameterizedTest`가 붙은 Method) | `c0427c2` 52 → `31ecaee` 93 = `a148a62` 93 | `git show <SHA>:<파일>` 정적 집계. `31ecaee` 이후 대타 Test·Production 변경 0 |
| 그중 PR #5 변화 | 삭제 0, 이름 변경 3 (V4-T-09·11·12), 신규 41 (V4-T-53~93) | Method 목록 비교 |
| 정적 계산 실행 사례 (Parameterized 입력 수·Repeated 횟수로 펼친 예상값, **실제 실행 아님**, S3 표현 정정) | 114 = 일반 86 + `@ParameterizedTest` 3개 Method 8건 (V4-T-11 2, V4-T-24 3, V4-T-61 3) + `@RepeatedTest(5)` 4개 Method 20건 (V4-T-49~52) | `@EnumSource` names, `REPEAT_COUNT = 5`. 스레드 수(10)는 실행 사례에 넣지 않는다 |
| 문서 시나리오 | 카드마다 다름 | 한 Method가 여러 시나리오를, 한 시나리오가 여러 Method를 가질 수 있다 (1:1 강제 안 함) |
| PR #5 Delta 초안의 "94" | 원인 확인: 초안의 분류별 신규 수 합계가 41 (FindCandidates 2, Accept 13, Reject 5, Approve 9, Repository 2, ForcedOrder 4, Flow 6)인데 신규를 42로 적어 52 + 42 = 94가 됐다. 분류 안의 개별 수도 실제와 다르다 (Accept 신규는 12, `FindRequestsTest.findAcceptedPendingRequests_Success` 1개 누락). 두 차이가 상쇄돼 합은 41 | 초안 원문은 수정하지 않음 |

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
| V4-T-07 | C-45 (SUB-02) | `SubstituteCandidateServiceTest$FindRequestsTest.findReceivedRequests_Success` | UNIT · MOCK | NORMAL | 목록 | SUFFICIENT (Query는 T-13) |
| V4-T-08 | V4-P05, C-46 | `$AcceptTest.acceptCandidate_Success` | UNIT · MOCK | NORMAL | ACCEPTED | STRENGTHEN: DB 최종 상태 |
| V4-T-09 | V4-P06, C-47 | `$RejectTest.rejectCandidate_OtherPendingCandidateExists` (PR #5 전 이름 `rejectCandidate_Success_OtherPendingCandidateExists`) | UNIT · MOCK | NORMAL | OPEN 유지 | SUFFICIENT |
| V4-T-10 | V4-P06, C-47 | `$RejectTest.rejectCandidate_LastCandidate_ClosesRequest` | UNIT · MOCK | NORMAL | `ALL_CANDIDATES_REJECTED` | SUFFICIENT |
| V4-T-11 | V4-P05 | `$AcceptTest.acceptCandidate_AlreadyResponded` (PR #5 전 이름 `…_Fail`, `@ParameterizedTest` 2건: ACCEPTED·REJECTED) | UNIT · MOCK | FAILURE | SUB-022 | SUFFICIENT. 단 PR #5에서 "일찍 실패하면 User Lock을 잡지 않는다"(`verify(userService, never()).getByIdForUpdate`) 확인이 빠짐 (공통 확인 `assertFailsWithoutSideEffect`에 없음, 정적 확인). 필수 불변조건인지 ML-5에서 판단 |
| V4-T-12 | V4-P05, C-46, C-30 | `$AcceptTest.acceptCandidate_OfficialShiftConflict` (PR #5 전 이름 `…_Fail`) | UNIT · MOCK | FAILURE | SUB-005 | SUFFICIENT |
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
| V4-T-24 | V4-P07 | `.approve_fail_invalid_request_status` (PR #5에서 `@ParameterizedTest` 3건: OPEN·APPROVED·CLOSED) | UNIT · MOCK | FAILURE | SUB-002 | SUFFICIENT |
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
| V4-T-49 | V4-P01 | `SubstituteRequestConcurrencyTest$SimultaneousTest.concurrentCreateAllowsOnlyOneActiveRequest` | CONCURRENCY (`@RepeatedTest(5)`, 스레드 10) · MYSQL_TC RC | RACE | 활성 Request 1 | STRENGTHEN: 경쟁 순서 결정성 (H6) |
| V4-T-50 | V4-P05 | `.concurrentAcceptAllowsOnlyOneCandidate` | CONCURRENCY (`@RepeatedTest(5)`, 스레드 10) · MYSQL_TC RC | RACE | ACCEPTED 1 | STRENGTHEN: RR 대조 (SUB-DC-01) |
| V4-T-51 | V4-P06 | `.concurrentRejectClosesRequestWhenNoPendingCandidateRemains` | CONCURRENCY (`@RepeatedTest(5)`, 스레드 10) · MYSQL_TC RC | RACE | CLOSED | STRENGTHEN: 결정성 |
| V4-T-52 | V4-P05 | `.concurrentAcceptOnDifferentRequestsAllowsOnlyOneForSameUser` | CONCURRENCY (`@RepeatedTest(5)`, 스레드 10) · MYSQL_TC RC | RACE | ACCEPTED 1 | STRENGTHEN: 결정성 |

### 3-2-1. PR #5 이후 추가된 Test (V4-T-53~93, S2-5B 2026-10-25 정적 등록)

공통: 구분 `BASELINE`, ④ `IMPLEMENTED`, ⑤ `UNKNOWN` (§3-1). 줄 번호는 `a148a62` 기준. "기대 결과"는 Test가 확인하는 ErrorCode를 정적으로 읽은 것이다. Policy 연결과 검증 충분성은 S2-6에서 판단한다 (`미판정`). `SubstituteFlowIntegrationTest`의 MYSQL_TC는 `@ActiveProfiles("test")` 설정 근거이며 실제 연결은 실행 Log로 확인 전이다.

| Test ID | Policy | Class · Method | 종류 · 환경 | 유형 | 기대 결과 | 검증 충분성 |
|---|---|---|---|---|---|---|
| V4-T-53 | S2-6 연결 | `SubstituteCandidateServiceTest$FindCandidatesTest.findCandidates_FiltersIneligibleMembers` L166 (요청자/MANAGER/공식 근무 충돌/근무 불가 시간/다른 대타 약속이 있는 멤버는 제외된다) | UNIT · MOCK | NORMAL | 상태·목록 (본문 참조) | 미판정 |
| V4-T-54 | S2-6 연결 | `SubstituteCandidateServiceTest$FindCandidatesTest.findCandidates_NoEligibleMember_ReturnsEmpty` L206 (조건을 만족하는 멤버가 없으면 빈 리스트를 반환한다) | UNIT · MOCK | NORMAL | 상태·목록 (본문 참조) | 미판정 |
| V4-T-55 | S2-6 연결 | `SubstituteCandidateServiceTest$FindRequestsTest.findAcceptedPendingRequests_Success` L246 (SUB-07 - 현재 사용자 ID와 현재 시각으로 승인 대기 요청을 조회한다) | UNIT · MOCK | NORMAL | 상태·목록 (본문 참조) | 미판정 |
| V4-T-56 | S2-6 연결 | `SubstituteCandidateServiceTest$AcceptTest.acceptCandidate_CandidateNotFound` L321 (실패 - 존재하지 않는 Candidate) | UNIT · MOCK | FAILURE | SUB-019 (`CANDIDATE_NOT_FOUND`) | 미판정 |
| V4-T-57 | S2-6 연결 | `SubstituteCandidateServiceTest$AcceptTest.acceptCandidate_RequestNotFound` L334 (실패 - Candidate가 가리키는 Request가 존재하지 않음) | UNIT · MOCK | FAILURE | SUB-001 (`SUBSTITUTE_REQUEST_NOT_FOUND`) | 미판정 |
| V4-T-58 | S2-6 연결 | `SubstituteCandidateServiceTest$AcceptTest.acceptCandidate_NotOwnCandidate` L348 (실패 - 다른 사람의 Candidate를 수락할 수 없다) | UNIT · MOCK | FAILURE | SUB-020 (`NOT_OWN_CANDIDATE`) | 미판정 |
| V4-T-59 | S2-6 연결 | `SubstituteCandidateServiceTest$AcceptTest.acceptCandidate_LeftMember` L362 (실패 - 퇴사한 후보자는 수락할 수 없다) | UNIT · MOCK | FAILURE | SUB-021 (`INVALID_CANDIDATE_MEMBER`) | 미판정 |
| V4-T-60 | S2-6 연결 | `SubstituteCandidateServiceTest$AcceptTest.acceptCandidate_NotEmployee` L374 (실패 - 후보자가 더 이상 EMPLOYEE가 아니면 수락할 수 없다) | UNIT · MOCK | FAILURE | SUB-021 (`INVALID_CANDIDATE_MEMBER`) | 미판정 |
| V4-T-61 | S2-6 연결 | `SubstituteCandidateServiceTest$AcceptTest.acceptCandidate_RequestNotOpen` L400 (실패 - Request가 OPEN이 아니면 수락할 수 없다) | UNIT · MOCK | FAILURE | SUB-023 (`REQUEST_NOT_OPEN`) | 미판정 |
| V4-T-62 | S2-6 연결 | `SubstituteCandidateServiceTest$AcceptTest.acceptCandidate_ScheduleNotPublished` L412 (실패 - 공개되지 않은 근무표의 Shift) | UNIT · MOCK | FAILURE | SUB-010 (`SHIFT_NOT_PUBLISHED`) | 미판정 |
| V4-T-63 | S2-6 연결 | `SubstituteCandidateServiceTest$AcceptTest.acceptCandidate_ShiftCancelled` L424 (실패 - 취소된 Shift) | UNIT · MOCK | FAILURE | SUB-014 (`SHIFT_CANCELLED`) | 미판정 |
| V4-T-64 | S2-6 연결 | `SubstituteCandidateServiceTest$AcceptTest.acceptCandidate_ShiftAlreadyStarted` L436 (실패 - 이미 시작된 Shift (startAt < now)) | UNIT · MOCK | FAILURE | SUB-012 (`SHIFT_ALREADY_STARTED`) | 미판정 |
| V4-T-65 | S2-6 연결 | `SubstituteCandidateServiceTest$AcceptTest.acceptCandidate_ShiftStartsExactlyNow` L448 (실패 - Shift 시작 시각이 정확히 now (경계값)) | UNIT · MOCK | BOUNDARY | SUB-012 (`SHIFT_ALREADY_STARTED`) | 미판정 |
| V4-T-66 | S2-6 연결 | `SubstituteCandidateServiceTest$AcceptTest.acceptCandidate_UnavailableTimeConflict` L475 (실패 - 근무 불가 시간과 겹침) | UNIT · MOCK | FAILURE | SUB-006 (`CONFLICT_UNAVAILABLE_TIME`) | 미판정 |
| V4-T-67 | S2-6 연결 | `SubstituteCandidateServiceTest$AcceptTest.acceptCandidate_ActiveSubstituteConflict` L489 (실패 - 이미 수락한 다른 대타 약속과 겹침) | UNIT · MOCK | FAILURE | SUB-007 (`CONFLICT_ACTIVE_SUBSTITUTE`) | 미판정 |
| V4-T-68 | S2-6 연결 | `SubstituteCandidateServiceTest$RejectTest.rejectCandidate_NotOwnCandidate` L577 (실패 - 다른 사람의 Candidate를 거절할 수 없다) | UNIT · MOCK | FAILURE | SUB-020 (`NOT_OWN_CANDIDATE`) | 미판정 |
| V4-T-69 | S2-6 연결 | `SubstituteCandidateServiceTest$RejectTest.rejectCandidate_LeftMember` L591 (실패 - 퇴사한 후보자는 거절할 수 없다) | UNIT · MOCK | FAILURE | SUB-021 (`INVALID_CANDIDATE_MEMBER`) | 미판정 |
| V4-T-70 | S2-6 연결 | `SubstituteCandidateServiceTest$RejectTest.rejectCandidate_AlreadyResponded` L603 (실패 - 이미 응답한 Candidate는 다시 거절할 수 없다) | UNIT · MOCK | FAILURE | SUB-022 (`CANDIDATE_ALREADY_RESPONDED`) | 미판정 |
| V4-T-71 | S2-6 연결 | `SubstituteCandidateServiceTest$RejectTest.rejectCandidate_RequestNotOpen` L615 (실패 - 다른 후보가 이미 수락한 Request는 거절할 수 없다) | UNIT · MOCK | FAILURE | SUB-023 (`REQUEST_NOT_OPEN`) | 미판정 |
| V4-T-72 | S2-6 연결 | `SubstituteCandidateServiceTest$RejectTest.rejectCandidate_ShiftAlreadyStarted` L627 (실패 - 이미 시작된 Shift는 거절할 수 없다) | UNIT · MOCK | FAILURE | SUB-012 (`SHIFT_ALREADY_STARTED`) | 미판정 |
| V4-T-73 | S2-6 연결 | `SubstituteRequestServiceTest$ApproveTest.approve_fail_request_not_found` L381 (실패: 존재하지 않는 요청이면 예외가 발생한다) | UNIT · MOCK | FAILURE | SUB-001 (`SUBSTITUTE_REQUEST_NOT_FOUND`) | 미판정 |
| V4-T-74 | S2-6 연결 | `SubstituteRequestServiceTest$ApproveTest.approve_fail_actor_not_manager` L428 (실패: 승인 요청자가 MANAGER가 아니면 예외가 발생한다) | UNIT · MOCK | FAILURE | WPL-004 (`MANAGER_REQUIRED`) | 미판정 |
| V4-T-75 | S2-6 연결 | `SubstituteRequestServiceTest$ApproveTest.approve_fail_schedule_not_published` L443 (실패: 근무표가 공개 전(DRAFT)이면 승인할 수 없다) | UNIT · MOCK | FAILURE | SCH-012 (`NOT_PUBLISHED`) | 미판정 |
| V4-T-76 | S2-6 연결 | `SubstituteRequestServiceTest$ApproveTest.approve_fail_shift_cancelled` L456 (실패: Shift가 취소됐으면 승인할 수 없다) | UNIT · MOCK | FAILURE | SFT-013 (`IS_CANCELED_SHIFT`) | 미판정 |
| V4-T-77 | S2-6 연결 | `SubstituteRequestServiceTest$ApproveTest.approve_fail_shift_member_changed` L470 (실패: 요청 이후 Shift 담당자가 바뀌었으면 승인할 수 없다) | UNIT · MOCK | FAILURE | SUB-004 (`INVALID_MEMBER`) | 미판정 |
| V4-T-78 | S2-6 연결 | `SubstituteRequestServiceTest$ApproveTest.approve_fail_accepted_candidate_not_found` L492 (실패: Request는 ACCEPTED인데 ACCEPTED Candidate가 없으면 예외가 발생한다) | UNIT · MOCK | FAILURE | SUB-008 (`NOT_FOUND_CANDIDATE`) | 미판정 |
| V4-T-79 | S2-6 연결 | `SubstituteRequestServiceTest$ApproveTest.approve_fail_accepted_member_left` L509 (실패: 수락한 사람이 퇴사했으면 승인으로 이어지지 않는다) | UNIT · MOCK | FAILURE | SFT-004 (`INVALID_WORKPLACE_MEMBER_VALUE`) | 미판정 |
| V4-T-80 | S2-6 연결 | `SubstituteRequestServiceTest$ApproveTest.approve_fail_accepted_member_other_workplace` L523 (실패: 수락한 사람이 다른 근무지 소속이면 승인할 수 없다) | UNIT · MOCK | FAILURE | SFT-004 (`INVALID_WORKPLACE_MEMBER_VALUE`) | 미판정 |
| V4-T-81 | S2-6 연결 | `SubstituteRequestServiceTest$ApproveTest.approve_fail_shift_starts_exactly_now` L570 (실패: 근무 시작 시각이 정확히 now(경계값)이면 승인할 수 없다) | UNIT · MOCK | BOUNDARY | SFT-006 (`INVALID_TIME_VALUE`) | 미판정 |
| V4-T-82 | S2-6 연결 | `SubstituteCandidateRepositoryTest.existsOverlappingAcceptedSubstitute` L404 (SUB-01/03 - 이미 수락한 활성 대타 약속과 시간이 겹치는지 확인한다) | SLICE · **H2** | NORMAL | 상태·목록 (본문 참조) | 미판정 |
| V4-T-83 | S2-6 연결 | `SubstituteCandidateRepositoryTest.existsConflictingActiveSubstitute` L447 (SUB-04 - 승인 시 현재 Request를 제외한 다른 활성 대타 약속과의 충돌만 확인한다) | SLICE · **H2** | NORMAL | 상태·목록 (본문 참조) | 미판정 |
| V4-T-84 | S2-6 연결 | `SubstituteRequestConcurrencyTest$ForcedOrderTest.secondCreateWaitsForFirstCreate` L261 (Shift Lock - 먼저 생성 중인 요청이 커밋될 때까지 두 번째 생성은 대기하고 …) | CONCURRENCY (순서 강제, CountDownLatch) · MYSQL_TC RC | RACE | SUB-013 (`ACTIVE_REQUEST_EXISTS`) | 미판정 |
| V4-T-85 | S2-6 연결 | `SubstituteRequestConcurrencyTest$ForcedOrderTest.secondAcceptWaitsForFirstAccept` L284 (Request Lock - 첫 번째 후보의 수락이 커밋될 때까지 두 번째 후보는 대기하고 …) | CONCURRENCY (순서 강제, CountDownLatch) · MYSQL_TC RC | RACE | SUB-023 (`REQUEST_NOT_OPEN`) | 미판정 |
| V4-T-86 | S2-6 연결 | `SubstituteRequestConcurrencyTest$ForcedOrderTest.lastRejectSeesCommittedFirstReject` L313 (Request Lock - 첫 번째 거절이 커밋된 뒤 두 번째(마지막) 거절이 실행되어 Request가 CLOSED 된다) | CONCURRENCY (순서 강제, CountDownLatch) · MYSQL_TC RC | RACE | 상태·목록 (본문 참조) | 미판정 |
| V4-T-87 | S2-6 연결 | `SubstituteRequestConcurrencyTest$ForcedOrderTest.sameUserSecondAcceptWaitsForFirstAccept` L343 (User Lock - 같은 User가 다른 Request를 수락하는 중이면 대기하고 …) | CONCURRENCY (순서 강제, CountDownLatch) · MYSQL_TC RC | RACE | SUB-007 (`CONFLICT_ACTIVE_SUBSTITUTE`) | 미판정 |
| V4-T-88 | S2-6 연결 | `SubstituteFlowIntegrationTest$FullFlowTest.requestAcceptApprove_ChangesShiftMember` L146 (요청 → 수락 → 승인 → Shift 담당자가 수락자로 변경된다) | INTEGRATION (`@Transactional`) · MYSQL_TC 설정 | NORMAL | 확인 code: SUB-011 (`NOT_OWN_SHIFT`). DB 재조회 (`flushAndClear`) | 미판정 |
| V4-T-89 | S2-6 연결 | `SubstituteFlowIntegrationTest$FullFlowTest.allCandidatesReject_ClosesRequest_ThenCanRequestAgain` L227 (후보 전원이 거절하면 CLOSED 되고, 같은 Shift로 다시 요청할 수 있다) | INTEGRATION (`@Transactional`) · MYSQL_TC 설정 | NORMAL | DB 재조회 상태 (`flushAndClear`) | 미판정 |
| V4-T-90 | S2-6 연결 | `SubstituteFlowIntegrationTest$FullFlowTest.acceptThenManagerClose_ShiftUnchanged_CannotApprove` L266 (수락 후 MANAGER가 종료하면 Shift 담당자는 그대로이고, 이후 승인할 수 없다) | INTEGRATION (`@Transactional`) · MYSQL_TC 설정 | NORMAL | 확인 code: SUB-002 (`INVALID_REQUEST`). DB 재조회 (`flushAndClear`) | 미판정 |
| V4-T-91 | S2-6 연결 | `SubstituteFlowIntegrationTest$RespondValidationTest.cannotAcceptWhenUnavailableTimeOverlaps` L300 (요청 이후 등록한 근무 불가 시간과 겹치면 수락할 수 없다) | INTEGRATION (`@Transactional`) · MYSQL_TC 설정 | FAILURE | 확인 code: SUB-006 (`CONFLICT_UNAVAILABLE_TIME`). DB 재조회 (`flushAndClear`) | 미판정 |
| V4-T-92 | S2-6 연결 | `SubstituteFlowIntegrationTest$RespondValidationTest.cannotAcceptWhenOtherSubstituteOverlaps` L324 (이미 수락한 다른 대타 약속과 시간이 겹치면 수락할 수 없다) | INTEGRATION (`@Transactional`) · MYSQL_TC 설정 | FAILURE | 확인 code: SUB-007 (`CONFLICT_ACTIVE_SUBSTITUTE`). DB 재조회 (`flushAndClear`) | 미판정 |
| V4-T-93 | S2-6 연결 | `SubstituteFlowIntegrationTest$ApproveValidationTest.cannotApproveWhenAcceptedMemberLeft` L363 (수락한 사람이 퇴사하면 승인으로 이어지지 않는다) | INTEGRATION (`@Transactional`) · MYSQL_TC 설정 | FAILURE | 확인 code: SFT-004 (`INVALID_WORKPLACE_MEMBER_VALUE`). DB 재조회 (`flushAndClear`) | 미판정 |

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
| V4-N-23 | G03-D-19 D19-1 | 같은 만료 요청을 서로 다른 두 시각(`startAt` 이후)에 변환 → 두 응답의 `closedAt`이 같고 `== startAt` | UNIT · NONE | PROPOSED_IMPROVEMENT | **PROPOSED** | SPECIFIED (작성 금지) | 승인 후 가능 |
| V4-N-24 | G03-D-19 D19-2 | 만료 요청을 목록 조회한 뒤 DB Row 확인 → status OPEN/ACCEPTED, DB `closedAt` = null 그대로 | INTEGRATION · MYSQL_TC | PROPOSED_IMPROVEMENT (현재 동작 고정을 겸함) | **PROPOSED** | SPECIFIED (작성 금지) | 승인 후 가능 |
| V4-N-25 | G03-D-19 D19-1 | `getSentRequests_ReturnsLatestFirst_MarksExpired` (L1633)에 만료 응답 `closedAt == startAt` 확인 추가 (기존 기대값 유지) | UNIT · MOCK | PROPOSED_IMPROVEMENT | **PROPOSED** | SPECIFIED (작성 금지) | 승인 후 가능 |
| V4-N-26 | G03-D-19 D19-1, D19-4 | OPEN이고 `now == startAt` → CLOSED / EXPIRED, `closedAt == startAt` | UNIT · NONE | PROPOSED_IMPROVEMENT | **PROPOSED** | SPECIFIED (작성 금지) | 승인 후 가능 |

G03-D-19 관련 기존 Test 변경 후보 (승인 전 변경 금지, `PROCESS-CHARTER` §6-2): V4-T-02 `closedAt` 기대값 `now` → `SHIFT_START` (**기대값 변경**), V4-T-03에 `closedAt == SHIFT_START` 확인 추가. V4-N-08~22는 로컬 ML-3 Draft가 예약한 번호라 G03-D-19는 23부터 쓴다.

신규 행의 선행 조건은 종류가 다르다.

| 행 | 성격 | 막고 있는 것 |
|---|---|---|
| V4-N-01~04 | Kotlin 전환 전 현재 Java 동작 고정 (`MIGRATION`, 동작 변경 0) | 정책 승인 대상 아님. V4 Owner 확정과 Task 승인 (`docs/ai/AI-RULES.md` §1) |
| V4-N-05 | 현재 동작의 동시성 재현 (Production 변경 0) | 정책 승인이 아니라 DL-009 (Harness 공유)·DL-010 (검증 지위) 결정. Harness가 `LOCAL_ONLY`라 ④ `BLOCKED` |
| V4-N-06·07 | 새 정책(② `PROPOSED`) 구현 검증 | `DECISION-LOG`의 `APPROVED` 전 작성 금지 (④ `SPECIFIED`까지) |
| V4-N-23~26 | 새 정책(G03-D-19, ② `PROPOSED`) 구현 검증 | 위와 같음. V4-N-24는 현재 동작 고정을 겸하지만 같은 카드에 묶여 있어 함께 승인 대상 |

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
| V2-N-04 (ML-H1-T4) — `SUPERSEDED` → V2-N-06 ([ML-H2](../decisions/proposals/ML-H2-rejoin-and-active-membership.md), 팀 승인 전) | ML-H1 H1-d | 종료 Row가 있는 User가 같은 초대 코드로 참여 → 새 ErrorCode (후보 WPL-006, 409), 새 Row 0 | UNIT · MOCK + SLICE (WebMvc) + INTEGRATION | PROPOSED_IMPROVEMENT | **PROPOSED** (ErrorCode 추가는 AI-RULES §2 승인 대상) | SPECIFIED (작성 금지) | 승인 후 가능 (퇴사 Command 없이 종료 Row 준비 가능) |
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

## 5. Cross-Domain 정책 카드의 임시 Test ID (S2에서 정식 번호 부여)

아래 카드의 Test는 여러 Domain에 걸쳐 있어 카드 안에 임시 ID로만 적었다. 로컬 Draft가 예약한 신규 번호(V1-N-01~12, V2-N-06~40, V3-N-01~19, V4-N-08~22)와 겹치지 않도록 S2(Draft 이관)에서 Domain별 번호를 부여해 이 Inventory로 옮긴다. 전부 ④ `SPECIFIED` / ⑤ `NOT_RUN`.

| 카드 | 임시 ID | 개수 | 비고 |
|---|---|---|---|
| [`G03-D-21`](../decisions/proposals/G03-D-21-unavailable-time.md) §9 | D21-T1~T9, D21-R1~R5 | 9 + 경합 5 | D21-R은 H6 (ML-5 실험) |
| [`G03-D-22`](../decisions/proposals/G03-D-22-notification-delivery.md) §9 | D22-T1~T12, D22-T4b | 13 | D22-T4b는 D22-T4의 후속 상태(승인 후 인계)를 확인하는 별도 Test. 정식 번호는 §5-1 |
| [`G03-D-03`](../decisions/proposals/G03-D-03-error-code.md) §7 | D03-T1~T5 | 5 | D03-T2·T3는 기존 Test 기대값 변경을 포함 (승인 전 변경 금지) |

그 밖의 임시 ID: [`G03-D-05`](../decisions/proposals/G03-D-05-confirmation-response.md) D05-T1~T11·T5b·T5c·R1~R3, [`API-CATALOG`](../contract/API-CATALOG.md) §5-5 M3-T1~T6, [`NOTIFICATION-TYPES`](../contract/NOTIFICATION-TYPES.md) §5 NT-T1~T16, [`ML-5`](../decisions/proposals/ML-5-concurrency-and-lock-order.md) ML5-T1~T15·R1~R4, [`ML-3`](../decisions/proposals/ML-3-substitute-on-leave-and-shift-change.md) H6-C1~C8.

### 5-1. 정식 Test ID 대응표 (S2-5B, 2026-10-25)

규칙:
- 이 표가 임시 ID → 정식 ID 대응의 원본이다. **카드 본문의 임시 ID와 내용은 그대로 둔다** (내용 원본은 카드, 번호 원본은 이 표).
- 기존 ID와 예약 범위(V1-N-01~12, V2-N-01~40, V3-N-01~19, V4-N-01~26)는 바꾸지 않는다. 새 번호는 각 범위 다음부터 쓴다.
- **소유 Domain**은 그 Test가 지키는 불변식의 Domain이다. 한 시나리오가 두 Domain에 걸치면 한 번만 작성하고 다른 쪽은 "관련"으로 연결한다 (예: 대타 종료는 V4, 그 종료의 받는 사람별 알림은 V5).
- 이미 있는 Test가 같은 내용을 확인하면 새 번호를 주지 않고 기존 ID를 참조한다.
- 실험(H6) ID는 정식 번호를 보류한다: ML5-T·R, H6-C, D21-R, D05-R, D03-T4. H6 Harness가 `LOCAL_ONLY`이고 DL-009 결정 전이라 ④ `BLOCKED`. 원본은 ML-5 카드다 (H6-C ↔ ML5-T 대응은 ML-5 §표에 이미 있음).
- 새 행은 모두 ④ `SPECIFIED` / ⑤ `NOT_RUN`. `PROPOSED_IMPROVEMENT`는 연결 정책이 `APPROVED`되기 전 작성 금지.

| 임시 ID | 정식 ID / 참조 | 소유 | 구분 | 비고 |
|---|---|---|---|---|
| D19 (G03-D-19) | V4-N-23~26 (기존) | V4 | PROPOSED_IMPROVEMENT | 변경 없음 |
| D21-T1 | 참조: `ShiftServiceTest` L412·L493 (V3-T 번호는 V3 Delivery Sheet 때) | V3 | BASELINE | 회귀 |
| D21-T2 | **V3-N-20** | V3 | MIGRATION | 기존 Test 없음 확인 (`SFT-010` Test 0, §5-3). 관련: V3-N-24 |
| D21-T3 | 참조: **V4-T-29** | V4 | BASELINE | 회귀 |
| D21-T4 | 참조: **V4-T-66** (관련: V4-T-91 통합 흐름) | V4 | BASELINE | 회귀 |
| D21-T5 | **V3-N-21** | V3 | MIGRATION | |
| D21-T6 | **V4-N-39** | V4 | PROPOSED_IMPROVEMENT | ML-2·ML-3 승인 후 |
| D21-T7 = D05-T2 | **V2-N-41** | V2 | PROPOSED_IMPROVEMENT | §5-2 실제 중복 → 하나로 |
| D21-T8 = D05-T3 | **V2-N-42** | V2 | PROPOSED_IMPROVEMENT | §5-2 실제 중복 (D05-T3의 "목록 2건"까지 확인) |
| D21-T9 = D05-T6 | **V2-N-43** | V2 | PROPOSED_IMPROVEMENT | §5-2 실제 중복 (D05-T6의 "DB 변경 0"까지 확인) |
| D21-R1~R5 | 보류 (H6) | ML-5 | H6 | `BLOCKED` |
| D22-T1 | **V5-N-17** | V5 | MIGRATION | 알림 저장 실패가 업무에 영향 없음 (현재 동작 고정) |
| D22-T2·T3·T4·T4b·T5·T6·T7·T8·T9·T10·T11·T12 | **V4-N-27·28·29·30·31·32·33·34·35·36·37·38** (순서대로) | V4 | PROPOSED_IMPROVEMENT | 종료된 대타 조회. D22-T4b는 독립 번호 V4-N-30 |
| D03-T1 | 참조: V4-N-01 (V4) + 각 Domain의 Migration Controller Slice | 각 Domain | MIGRATION | Domain마다 V4-N-01 방식으로 작성 |
| D03-T2 | **V4-N-40** | V4 | PROPOSED_IMPROVEMENT | 기존 V4-T-25·V4-T-81 기대값 변경 (§5-4) |
| D03-T3 | **V4-N-41** | V4 | PROPOSED_IMPROVEMENT | 기존 V4-T-64·65·72 기대값 변경 (§5-4) |
| D03-T6 (S2-5B 추가) | **V4-N-42** | V4 | PROPOSED_IMPROVEMENT | T1-03. 기존 V4-T-63 기대값 변경 |
| D03-T7 (S2-5B 추가) | **V4-N-43** | V4 | PROPOSED_IMPROVEMENT | T1-05. 기존 V4-T-43 기대값 변경 |
| D03-T4 | 참조: ML5-T12 (보류, H6) | ML-5 | H6 | 같은 내용 (503 매핑 + Rollback) |
| D03-T5 | 참조: 각 기능 카드 Test | — | — | 일반 규칙이라 번호 없음 |
| D05-T1 | **V2-N-44** | V2 | PROPOSED_IMPROVEMENT | |
| D05-T4·T5·T5b·T5c·T7·T8·T9·T11 | **V2-N-45·46·47·48·49·50·51·52** (순서대로) | V2 | PROPOSED_IMPROVEMENT | 퇴사 확정(API-LV-02)의 확인 응답 |
| D05-T10 | **V3-N-22** | V3 | MIGRATION | 기존 DRAFT 생성·Publish·불가능 시간 경고 형식 회귀 |
| D05-R1~R3 | 보류 (H6) | ML-5 | H6 | `BLOCKED` |
| M3-T1 | **V4-N-44** | V4 | PROPOSED_IMPROVEMENT | 승인 범위 밖 = 없는 ID와 같은 404 |
| M3-T2·T3 | **V4-N-45·46** | V4 | PROPOSED_IMPROVEMENT · H6 | ④ `BLOCKED` (H6 Harness) |
| M3-T4 | **V3-N-23** | V3 | PROPOSED_IMPROVEMENT | 근무 상세 |
| M3-T5 | **V4-N-47** (후보 응답·대타 종료) + **V5-N-18** (알림 읽음) | V4 / V5 | PROPOSED_IMPROVEMENT | Domain별로 나눔 |
| M3-T6 | 참조: 기존 BASELINE (V4-T-23·36 등, 각 API의 정상 경로 Test) | 각 Domain | BASELINE | 회귀 |
| NT-T1~T16 | **V5-N-01~16** (번호 그대로) | V5 | T3·T8·T12는 MIGRATION, 나머지 PROPOSED_IMPROVEMENT | §6 |
| ML5-T1~T15, ML5-R1~R4, H6-C1~C8 | 보류 (H6). ML5-T10 = V2-N-07, ML5-T11 = V2-N-12 (기존) | ML-5 | H6 | `BLOCKED` |
| ErrorCode Test 0 (11개) | **V3-N-24~32**, **V4-N-48** | V3 / V4 | MIGRATION | §5-3 |

번호 사용 결과: V2-N-41~52, V3-N-20~32, V4-N-27~48, V5-N-01~18 (신설), V4-T-53~93 (기존 Test).

**S3 추가 (2026-10-25, [USER-PATH-REVIEW](../contract/USER-PATH-REVIEW.md) §11):** 새 통합 시나리오 Test는 임시 ID 없이 바로 정식 ID를 준다. 모두 `PROPOSED_IMPROVEMENT`, ④ `SPECIFIED` / ⑤ `NOT_RUN`.

| 정식 ID | 경로 | 소유 | 내용 원본 |
|---|---|---|---|
| V2-N-53 | UP-06 퇴사 전체 흐름 | V2 | USER-PATH-REVIEW §7 |
| V2-N-54 | UP-01 재가입 후 이전 소속 격리 | V2 | USER-PATH-REVIEW §2 |
| V3-N-33 | UP-03 공개 근무 취소·담당자 변경 + 대타 종료 + 알림 | V3 | USER-PATH-REVIEW §4 |
| V5-N-19 | UP-07 알림 ↔ 대체 화면 일치 | V5 | USER-PATH-REVIEW §8 (S3-A: 관리자 행은 API-SB-02 포함) |
| V4-N-49 | API-SB-02 권한: 현재 MANAGER만. 직원 403 `WPL-004`, 비구성원 403 `WPL-003`, 다른 근무지 요청 미포함 | V4 | API-CATALOG API-SB-02 (S3-A) |
| V4-N-50 | API-SB-02 대상: CLOSED 모든 사유 + 계산된 만료(`startAt ≤ now`, 경계 `startAt == now` 포함) 포함, APPROVED·진행 중 제외, SUB-06 목록과 겹침 0 | V4 | 같음 |
| V4-N-51 | API-SB-02 기간·정렬·페이지: 기본 7일, `from`으로 7일 이전도 조회, 효력 종료 시각 내림차순 + ID 동률 처리, 페이지 경계 | V4 | 같음 |
| V4-N-52 | API-SB-02 사실 Field: `requesterStillAssigned`·`shiftStarted`·`hasNewerActiveRequest` 각 참·거짓 경우 | V4 | 같음 |
| V4-N-53 | API-SB-02 소속 경계: 요청자가 퇴사·재가입해도 `requesterMembershipEnded = true`, 새 소속과 합치지 않음. 거절 후보·다른 근무지 정보 없음 | V4 | 같음 |
| V4-N-54 | API-SB-02 Query를 MySQL에서 (효력 종료 시각 `COALESCE` 정렬, 만료 계산) | V4 | 같음 (SLICE · MYSQL_TC) |
| V5-N-20 | 실행한 관리자가 요청자이기도 한 경우: 관리자 대상 알림 0, 요청자 알림 1 | V5 | NOTIFICATION-TYPES §5 (S3-A) |

### 5-2. 중복 의심 대조 (사전조건·검증 대상·기대값이 모두 같을 때만 통합)

| 쌍 | 사전조건 | 검증 대상 | 판정 | 처리 |
|---|---|---|---|---|
| V4-N-20 / NT-T5 | 같음 (`SHIFT_CHANGED` 종료) | 둘 다 알림 (V4-N-20: `MANAGER_CLOSED` 0건, NT-T5: 그것 + 받는 사람별 규칙) | **실제 중복** (V4-N-20 ⊂ NT-T5) | V5-N-05 하나로 작성. V4-N-20은 ID를 지우지 않고 "V5-N-05로 작성, 별도 Test 없음" |
| D22-T8 / NT-T13 | 비슷함 (PENDING 후보의 요청 종료) | 다름: D22-T8 = 종료된 대타 조회 결과 0건, NT-T13 = 알림 0건 + 수락 시도 `SUB-023` + 받은 요청 목록 | **다른 불변식** | 통합 안 함. V4-N-34 ↔ V5-N-13 관련 연결 |
| NT-T15 / NT-T16 | 다름 | NT-T15 = 응답에 수가 있고 실제와 같음 (PS-02·03·LV-02), NT-T16 = 세는 규칙의 경계 경우 | **부분 겹침** | 통합 안 함. V5-N-15 ↔ V5-N-16 관련 |
| D21-T7 / D05-T2 (S2-5B 발견) | 같음 (미리보기 충돌 1건, 같은 목록으로 확정) | 같음 (성공, 근무 인계) | **실제 중복** | V2-N-41 |
| D21-T8 / D05-T3 (S2-5B 발견) | 같음 (미리보기 후 다른 인계 대상에 새 충돌) | 같음 (409, 변경 0). D05-T3이 목록 2건까지 확인 | **실제 중복** | V2-N-42 (D05-T3 기대값 사용) |
| D21-T9 / D05-T6 (S2-5B 발견) | 같음 (인계 대상이 같은 시간 공식 근무 보유) | 같음 (확인과 무관하게 거부). D05-T6이 DB 변경 0까지 확인 | **실제 중복** | V2-N-43 (D05-T6 기대값 사용) |

### 5-3. Test 참조가 없는 ErrorCode 11개 (S2-5B 정적 확인)

판정 기준: ① 응답 code까지 확인하는 Test 있음 / ② 그 경로를 지나는 Test는 있지만 이 code를 확인하지 않음 / ③ 이 실패 분기 Test 없음 / ④ 일반 API로 도달하기 어려워 별도 Unit Test 필요.
조사 방법: `src/test` 전체에서 Enum 상수 이름·code 문자열 검색 + Shift·Schedule Test의 DisplayName과 예외 확인 방식 확인. **11개 중 ②는 0개**였다 (같은 Method의 정상 경로 Test만 있음).

| 정식 ID | code | 호출 지점 (`a148a62`) | 판정 | Migration 회귀 명세 (현재 Java 동작 고정, 기대값은 현재 값) |
|---|---|---|---|---|
| V3-N-24 | `SFT-010` | `ShiftService.validateShiftOverlap` L325 | ③ (같은 Method의 `SFT-009`는 `ShiftModifyIntegrationTest` L265에서 확인) | 다른 근무지의 공개 근무와 겹치는 담당 지정 → 400 `SFT-010`, 저장 0. 관련: V3-N-20 |
| V3-N-25 | `SFT-016` | `ShiftService.validShift` L219, `validWorkplaceMember` L233 | ③ | 없는 근무 ID / 없는 소속 ID → 404 `SFT-016`. API-CATALOG M3·CHG의 범위 밖 404 판정의 기준값 |
| V3-N-26 | `SFT-017` | `ShiftService.validSchedule` L203 | ③ | 없는 근무표 ID → 404 `SFT-017` |
| V3-N-27 | `SFT-019` | `ShiftService.delete` L187 | ③ | 없는 근무지 ID로 삭제 → 404 `SFT-019` |
| V3-N-28 | `SCH-003` | `ScheduleService` L276·L289 | ③ | 없는 근무표 / 다른 근무지의 근무표 → 둘 다 404 `SCH-003` (같은 응답) |
| V3-N-29 | `SFT-011`, `SFT-007` | `ShiftService.validShiftTime` L250·L258 | ③ | 검사 순서 고정: 날짜가 다르면 끝이 시작보다 앞서도 `SFT-011`, 같은 날이고 시작 ≥ 끝이면 `SFT-007` |
| V3-N-30 | `SFT-002` | `ShiftService.validSchedule` L207 | ③ | 다른 근무지의 근무표 ID → 400 `SFT-002` |
| V3-N-31 | `SFT-008` | `ShiftService.validateShiftInScheduleWeek` L301 | ③ | 근무표 주간 밖 시간 → 400 `SFT-008` (Publish의 같은 검사는 SCH code, `ScheduleServiceTest` L1572) |
| V3-N-32 | `SCH-008` | `ScheduleService.validateShiftTimes` L387 | ③ | 자정을 넘는 근무가 있는 근무표 공개 → 409 `SCH-008`, 상태 DRAFT 유지 |
| V4-N-48 | `SUB-003` | `SubstituteRequest.validateOpening` L57 (`accept()` L64) | ④ (Service가 먼저 `SUB-023`으로 막음) | Entity Unit: OPEN이 아닌 Request의 `accept()` → `SUB-003`, 상태 불변 |

### 5-4. 기존 Test 기대값: Migration 보존 / Improvement 변경 (S2-5B 연결)

Kotlin Migration PR에서는 아래 **모든** 기존 Test의 기대값을 현재 값 그대로 둔다 (MIGRATION-MATRIX). "변경 시점"의 PR에서만, `DECISION-LOG` `APPROVED` 행을 근거로 바꾼다 (PROCESS-CHARTER §6-2). 새 정책으로 조용히 덮어쓰지 않는다.

| 기존 Test | 현재 기대값 (Migration 보존) | Improvement 기대값 | 근거 | 변경 시점 |
|---|---|---|---|---|
| V4-T-02 `test2` L53 | `closedAt == now` | `closedAt == startAt` | G03-D-19 (V4-N-23~26) | D-19 승인 후 Improvement PR |
| V4-T-25 (L556), V4-T-81 (L570) | `SFT-006` | 새 SUB code 409 | G03-D-03 T1-01 (V4-N-40) | D-03 승인 후 |
| V4-T-64·65·72 | `SUB-012` | `SUB-023` 409 | T1-02 (V4-N-41) | D-03 승인 후 |
| V4-T-63 `acceptCandidate_ShiftCancelled` | `SUB-014` | `SUB-023` 409 | T1-03 (V4-N-42) | D-03 승인 후 |
| V4-T-43 `close_ShiftNotScheduled_ThrowsException` L1173 | `SFT-005` | `SFT-013` 계열 | T1-05 (V4-N-43), P1 낮음 | D-03 승인 후 |
| `WorkplaceMemberRepositoryTest.rejectDuplicateMembershipAtDatabaseLevel` | `DataIntegrityViolationException` | ML-H2 재가입 구조에 따름 | ML-H2 (V2-N-06~) | ML-H2 승인 + DB Schema 변경 PR |
| SUB-05·`ShiftService.detail` 관련 (대상 Method는 ML-4 §8 승인 때 특정) | 현재 소속 확인 없음 | 현재 소속 기준 | ML-4 §8 | ML-4 승인 후 |
| SUB-006 문구 (T1-04) | — | — | message를 확인하는 Test 없음 → 기대값 변경 0 | — |
| Pattern `SHIFT-004`·`SHIFT-005` 4개 (`RegularShiftPatternControllerTest` L812·857·1449·1505) | 대상 직원 의미 | 그대로 (실행자 쪽 M-01 정정은 새 Test만) | ERROR-CODES M-01 | — |
| V4-T-26 `approve_fail_actor_not_in_workplace` (L413) (S2-6A 발견). 실행자 = 그 근무지 구성원 아님 → **범위 밖** | `WPL-003` 403 | **S3-A (W안) 갱신:** 실행자가 어디서도 MANAGER가 아니면 403 `WPL-004` (Request 조회 전, 없는 ID와 같은 응답), 다른 근무지 MANAGER면 404 `SUB-001` "존재하지 않는 대체근무 요청입니다." (없는 ID와 Status·code·message·`data` 동일). Unit Fixture를 실행자의 MANAGER 소속 유무로 나눈다. (S2-6A 안: 404 일괄) | API-CATALOG §5-5 "역할 먼저 검사", M3-T1 (V4-N-44) | CHG-06 승인 후 |
| V4-T-39 `close_NotManager_ThrowsException` (L1099) (S2-6A 발견). 이름과 달리 Mock은 `NOT_WORKPLACE_MEMBER` → **범위 밖** | `WPL-003` 403 | **S3-A (W안) 갱신:** 위 V4-T-26과 같음 (어디서도 MANAGER 아님 → 403 `WPL-004`, 다른 근무지 MANAGER → 404 `SUB-001`) | 같음, M3-T5 (V4-N-47) | CHG-07 승인 후 |
| V4-T-58 `acceptCandidate_NotOwnCandidate` (L348), V4-T-68 `rejectCandidate_NotOwnCandidate` (L577) (S2-6A 발견). Candidate는 본인만 볼 수 있음 → **범위 밖** | `SUB-020` 403 | 404 `SUB-019` "해당 대타 후보를 찾을 수 없습니다." (없는 Candidate ID와 동일) | M3-T5 (V4-N-47) | CHG-07 승인 후 |
| V4-T-74 `approve_fail_actor_not_manager` (L428) (S2-6A 발견). 실행자 = 같은 근무지 현재 구성원, **역할만 부족** | `WPL-004` 403 | **S3-A 결정 (W안, 사용자 잠정 결정 2026-10-25, 팀 `PENDING`): 어디서도 MANAGER가 아니면 403 `WPL-004` — 지금 code와 같고 검사 순서만 바뀐다 (Request 조회·Lock 전). 다른 근무지 MANAGER면 404 `SUB-001`.** (S3 검토 기록:) 현재 잠정 규칙(§5-5 "근무지 ID가 Path에 없는 API에서 관리자 아님 → 404", M3-T1 "같은 근무지 직원")대로면 404 `SUB-001`. 그러나 실행자가 그 요청의 요청자·후보라 이미 볼 수 있는 자원이면 §5-5의 "볼 수 있는 Resource → 상태에 맞는 응답" 원칙과 어긋나 403이 일관될 수 있다. 관계없는 직원이면 다른 사람 요청의 존재·상태를 알아내지 못하게 하는 404가 의미 있다 | API-CATALOG §5-5 "역할 먼저 검사", M3-T1 | CHG-06 승인 후 |
| `NotificationServiceTest` "다른 사용자의 알림은 읽음 처리할 수 없다" (L187, 확인 L232) (S2-6A 발견) | `NOT-002` 403 | M3-T5를 따르면 404. **단 G03-D-10 (`DEFER`)과 같은 질문이라 팀 결정 전에는 변경 대상으로 확정하지 않는다.** S3-A: 권한 확인 전 Lock 제거(CHG-07)는 응답 code와 별도로 진행 가능 | M3-T5 (V5-N-18), G03-D-10 | 팀 결정 후 |
| V4-T-35 `list_NotManager_ThrowsException` | `WPL-004` | 그대로 (근무지 ID가 Path에 있음) | API-CATALOG §5-5 | — |
| `NotificationServiceTest` "퇴사한 근무지의 알림" (L240) — B-2 내 과거 소속 알림 (S3-B 분리) | `NOT-002` 403 | **미정 — G03-D-16 (`PENDING`) 결정 대기.** 현재 잠정 규칙(§5-5 "실행자 자신의 소속 종료 → 403")대로면 그대로, USER-PATH-REVIEW §9-2 선택지 B'면 404. 결정 전에는 변경 대상으로 확정하지 않는다 | G03-D-16, USER-PATH-REVIEW §9-2 | 팀 결정 후 |
| ML-3 기존 거절 종료 판정 (V4-T-10) | `ALL_CANDIDATES_REJECTED` | 그대로 (INELIGIBLE 후보가 없는 Fixture) | ML-3 V4-N-22는 새 경우만 확인 | — |
| **V5-N-28** `NotificationHttpContractTest.markAsReadFailsWhenNotificationBelongsToAnotherUser` (PR #16, V5-J2) | `NOT-002` 403, DB `readAt` 불변 | **변경 후보.** 위 L187 행과 같은 질문 (M3-T5를 따르면 404). 팀 결정 전에는 변경 대상으로 확정하지 않는다 | G03-D-10, M3-T5 (V5-N-18) | 팀 결정 후 |
| **V5-N-29** `…markAsReadFailsWhenRecipientMembershipLeft` (PR #16) | `NOT-002` 403, DB `readAt` 불변 | **변경 후보.** 위 L240 행과 같은 질문 (G03-D-16 결정 대기) | G03-D-16, USER-PATH-REVIEW §9-2 | 팀 결정 후 |
| **V5-N-23** `…getNotificationsReturnsOnlyOwnActiveMembershipNotifications`의 "퇴사한 소속 알림 제외" 부분 (PR #16) | 목록에서 제외 | **변경 후보.** 퇴사했거나 다시 가입한 사람에게 이전 소속 알림을 보여 줄지 | G03-D-16, G03-D-11, ML-H2 | 팀 결정 후 |
| V5-N-27 `…markAsReadFailsWhenNotificationNotFound` (PR #16) | `NOT-001` 404 | **연관만 (유지 예상).** 없는 ID의 404는 G03-D-10이 어떻게 정해져도 바뀔 가능성이 낮다 | G03-D-10 | — |
| V5-N-23~26의 성공 `code` `"200"` (PR #16) | `"200"` | **연관만 (유지 예상).** G03-D-10 제목에 Success code가 포함됨 (G-03 P-38) | G03-D-10 | — |
| `NotificationReadConcurrencyTest` | 동시 읽음 후 첫 `readAt` 유지 | **연관만 (기대값 유지 예상).** CHG-07은 Lock과 권한 확인의 **순서**만 바꾼다 | CHG-07 | CHG-07 승인 후 (순서 변경만) |

**구분:** "변경 후보" = 그 결정에 따라 기대값이 바뀔 수 있음. "연관만" = 같은 결정과 관련 있지만 기대값은 유지될 것으로 예상.

**운영 규칙 (제안, 2026-10-26):** 정책 결정으로 Test 기대값을 바꿀 때는 **같은 PR에서 이 §5-4 행과 `DECISION-LOG`의 해당 ID를 함께 갱신**한다. 그래야 Test가 실패했을 때 의도한 정책 변경인지, Kotlin 전환 실수인지 구분할 수 있다. 결정이 `PENDING`인 동안 이 표의 Improvement 기대값은 확정이 아니다.

## 6. V5 Notification (S2-5B 신설)

알림 고유 불변식(받는 사람, Type, 합치기, 보내지 않음, 저장 실패 격리)을 확인하는 Test의 구역이다. 업무 결과(대타 종료, 근무 인계)는 각 Domain이 소유하고, V5는 같은 시나리오의 알림 결과만 확인한다. 한 통합 Test가 둘 다 확인할 수 있으면 한 번만 작성하고 서로 참조한다.

| 정식 ID | 원본 | 구분 | 비고 |
|---|---|---|---|
| V5-N-01~16 | NOTIFICATION-TYPES §5 NT-T1~T16 (번호 그대로) | NT-T3·T8·T12 MIGRATION, 나머지 PROPOSED_IMPROVEMENT | V5-N-05에 V4-N-20 통합 (§5-2) |
| V5-N-17 | G03-D-22 D22-T1 | MIGRATION | |
| V5-N-18 | API-CATALOG M3-T5의 알림 읽음 부분 | PROPOSED_IMPROVEMENT | S3-A: Lock 전 확인은 진행 가능, 응답 code 부분은 G03-D-10 결정 대기 |
| V5-N-19 | USER-PATH-REVIEW §8 알림 ↔ 대체 화면 | PROPOSED_IMPROVEMENT | S3 추가 |
| V5-N-20 | NOTIFICATION-TYPES §5 (실행자 = 요청자) | PROPOSED_IMPROVEMENT | S3-A 추가. NT-T 번호 없음 |

V5 기존 Test(`NotificationServiceTest` 14개 등)의 V5-T 번호는 V5 Delivery Sheet 때 붙인다. 기존 V4-N-04(Substitute Event별 알림 Row)는 V4에 두고 V5-N-01~ 와 관련 연결만 한다.

### 6-1. V5-J2 알림 HTTP 계약 (V5-N-21~30)

[DOMAIN-DEVELOPMENT-GUIDE](../delivery/DOMAIN-DEVELOPMENT-GUIDE.md) §7-3 V5-J2. **현재 Java 알림 API의 HTTP 계약을 고정**한 Test다. 구분 `MIGRATION` (현재 동작 고정, 새 계약을 정하는 Test 아님). ④ `IMPLEMENTED` — Test PR #16 (**Draft, `develop` 미반영**). Class: `domain.notification.controller.NotificationHttpContractTest` (`@SpringBootTest` + `@AutoConfigureMockMvc`, `/api/v1/...` + `.contextPath("/api/v1")`로 실제 `CustomAuthenticationFilter`·Security 설정 통과, `TestClock`, MYSQL_TC, `@Transactional` Rollback, DB 확인은 `flush()`·`clear()` 후 재조회).

| Test ID | Method | 확인하는 현재 Java 동작 | ⑤ 실행 |
|---|---|---|---|
| V5-N-21 | `getNotificationsRequiresAuthentication` | Token 없이 `GET /api/v1/notifications` → 401 `AUTH-004`, `data: null` | LOCAL_PASSED |
| V5-N-22 | `markAsReadRequiresAuthentication` | Token 없이 `PATCH …/{id}/read` → 401 `AUTH-004`, DB `readAt` 불변 | LOCAL_PASSED |
| V5-N-23 | `getNotificationsReturnsOnlyOwnActiveMembershipNotifications` | 현재 소속의 내 알림만 (다른 사용자·퇴사한 소속 제외), `createdAt` 내림차순 → id 내림차순, 항목 key 7개와 자료형, 안 읽은 알림 `readAt: null` key 유지 | LOCAL_PASSED |
| V5-N-24 | `getNotificationsReturnsEmptyArrayWhenNoNotification` | 알림이 없으면 `data: []` | LOCAL_PASSED |
| V5-N-25 | `markAsReadUpdatesOnlyTargetNotification` | 200, `data` = {`notificationId`, `readAt`}, 응답과 DB `readAt` 같음, 다른 알림 불변 | LOCAL_PASSED |
| V5-N-26 | `markAsReadTwiceKeepsFirstReadAt` | 다시 읽음 처리해도 200, 최초 `readAt` 유지 (응답·DB) | LOCAL_PASSED |
| V5-N-27 | `markAsReadFailsWhenNotificationNotFound` | 없는 ID → 404 `NOT-001` | LOCAL_PASSED |
| V5-N-28 | `markAsReadFailsWhenNotificationBelongsToAnotherUser` | 남의 알림 → 403 `NOT-002`, DB 불변 (§5-4 변경 후보) | LOCAL_PASSED |
| V5-N-29 | `markAsReadFailsWhenRecipientMembershipLeft` | 퇴사한 소속의 내 알림 → 403 `NOT-002`, DB 불변 (§5-4 변경 후보) | LOCAL_PASSED |
| V5-N-30 | `dateTimeWithFractionalSecondsContract` + V5-N-23·25·26 안의 날짜 검사 | `LocalDateTime` JSON 문자열: 시간대 표기 없음, 초 표시, 소수 초 끝 0 생략 (`…T10:00:00`, `…T10:00:00.123456`, `…T12:00:00.5`), DB 재조회 값과 같음. 나노초(7자리 이상)는 범위 밖 | LOCAL_PASSED |

**⑤ 상태의 뜻 (섞지 않는다):**
- `LOCAL_PASSED`: 로컬 macOS·GraalVM 25에서 신규 10/10 (EV-017), 전체 404/404 (EV-018). 개별 결과 근거는 로컬 JUnit XML (저장소 밖).
- CI: PR #16의 `Backend CI`·`Docker Build CI Test` 성공 (EV-020). **Workflow 단위 성공**이며 개별 Test 결과 근거가 아니다.
- `develop` 미반영: PR #16 Merge 전까지 `develop`에는 이 Test가 없다. G-A2(PR #15)와 **합쳐 실행한 적 없음** (예상 410건, TEST-GAP-ANALYSIS §4-1 C5).
- Kotlin Migration 이후: `NOT_RUN`.
- 범위 밖 (이 Test가 보장하지 않음): Commit 후 알림 생성·Rollback·알림 실패 (V5-J3), `AUTH-005`·`006` 자체 (기존 `CustomAuthenticationFilterTest`), Frontend 화면·다른 브라우저의 날짜 해석. P2로 미룬 것: V5-N-31 (잘못된 ID 형식 `COM-003`), V5-N-32 (알림 API의 `AUTH-006`).

## 7. 공통 `global` (CM) — G-A2 공통 응답 계약

[TEST-GAP-ANALYSIS](TEST-GAP-ANALYSIS.md) G-A2. **Java 기준선에서 관찰한 공통 응답 계약**을 고정하는 Test다 (모든 API 계약을 검증한 것이 아님). 구분 `MIGRATION` (기존 동작 보존). Java 원본 테스트는 develop에 존재했고, Kotlin 대응 테스트는 PR #79에서 전환 중이다.

| Test ID | Class · Method | Java 원본에서 확인한 보존 계약 | 종류 · 환경 | ⑤ 실행 (Java 기준선) |
|---|---|---|---|---|
| CM-N-01 | `global.dto.ApiResponseJsonContractTest.successResponseSerializesFourFields` | 응답 key가 정확히 `success`·`code`·`message`·`data`, `success` boolean, `code`·`message` 문자열 | SPRINGBOOT (애플리케이션 Jackson Mapper) · MYSQL_TC 설정 | LOCAL_PASSED |
| CM-N-02 | `…ApiResponseJsonContractTest.successResponseWithoutDataKeepsNullDataField` | data 없는 성공 응답도 `data: null` key 유지 | 같음 | LOCAL_PASSED |
| CM-N-03 | `…ApiResponseJsonContractTest.errorResponseWithoutDataKeepsNullDataField` | 오류 응답 `success: false` + `data: null` 유지 | 같음 | LOCAL_PASSED |
| CM-N-04 | `global.exception.ApiResponseHttpContractTest.signupSuccessResponseContract` | `POST /api/v1/auth/signup` → 201, `code "201"`, `data` key 정확히 `id`(number)·`email`·`name` | HTTP (MockMvc, `/api/v1` Context Path, Security Chain·전역 예외 처리기 포함) · MYSQL_TC, `@Transactional` Rollback | LOCAL_PASSED |
| CM-N-05 | `…ApiResponseHttpContractTest.businessErrorResponseContract` | 가입하지 않은 이메일 로그인 → 401 `AUTH-002`, 메시지 고정, `data: null` | 같음 | LOCAL_PASSED |
| CM-N-06 | `…ApiResponseHttpContractTest.validationErrorResponseContract` | 값이 빈 회원가입 → 400 `COM-001`, `data.fieldErrors[].{field, message}`, field 집합 {email, password, name} (문구·순서는 고정 안 함) | 같음 | LOCAL_PASSED |

**⑤ 상태의 뜻 (섞지 않는다):**
- `LOCAL_PASSED`: 로컬 macOS·GraalVM 25에서 신규 6/6 (EV-013), 전체 400/400 (EV-014). 개별 결과 근거는 로컬 JUnit XML (저장소 밖).
- Java 기준선 CI: PR #15의 `Backend CI`(`./gradlew check`, ubuntu·Temurin 25)·`Docker Build CI Test` 성공 (EV-016). **Workflow·Job 단위의 역사적 근거**이며 Kotlin 개별 Test 결과가 아니다.
- Kotlin 전환 상태 (2026-10-28, PR #79 Head `f86f0777d4cc4ec33d133d65683892ff23ee0e9a`): CM-N-01~06 대응 테스트가 Kotlin으로 전환됐다. 직전 Head `baf1acab63894895d7d7797083c065a2cf701647`와의 차이는 문서 두 파일뿐이다. PR #79의 기존 [Backend Check](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/actions/runs/40651240531)와 [Docker Build CI Test](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/actions/runs/40651240485)는 이전 Head에서 성공했으며, 새 Head에는 확인 시점에 체크가 보고되지 않았다. Backend Check Run 메타데이터의 Base는 `933fd88cf4c79072a5ae067070e03f8b3dd977c4`로 당시 develop `eeef2c2155c9b80f2827ee0ba2a111691fabad20`보다 이전이었다. 별도 임시 통합 검증(2026-10-28)은 최신 develop `0aec24f367066b88093f94ab05557c224959696c`과 PR Head 내용을 합친 트리에서 문서 충돌을 임시 해결한 뒤 `./gradlew clean check`를 실행해 514/514 PASS, 실패·오류·Skip 0을 확인했다. 이 결과는 PR 브랜치 자체의 CI나 병합 가능 상태를 의미하지 않는다. 개별 JUnit XML은 이 증거에 연결해 보관하지 않았으므로 Test별 ⑤ 상태는 `UNKNOWN`이며 `PASSED`로 올리지 않는다.
- develop 상태 (조회 당시 `0aec24f367066b88093f94ab05557c224959696c`): PR #79 미병합, `backend/src/test/java` 추적 파일 17개가 남아 있다. PR #79 병합 후 최신 develop에서 다시 확인한다.

## 8. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-25 | S2-5B: PR #5 이후 대타 Test 집계(§3-1-1, 정적), V4-T-07~12·24·49~52 경로 갱신(ID 유지), V4-T-53~93 등록, 정식 ID 대응표(§5-1), 중복 대조(§5-2), ErrorCode Test 0 판정·Migration 명세(§5-3), 기대값 보존/변경 연결(§5-4), V5 구역 신설(§6). Test 실행 0 |
| 2026-10-25 | S2-6A: §5-4에 M3 응답 규칙으로 기대값이 바뀌는 기존 권한 실패 Test 추가 — 범위 밖 4개 (V4-T-26·39·58·68, Improvement 기대값 = 없는 ID와 같은 code·message), 역할만 부족한 V4-T-74는 S3 결정, 알림 읽음 1개는 G03-D-10 결정 대기 |
| 2026-10-25 | S3: §3-1-1 "실행 사례"를 정적 계산값으로 표현 정정, §5-1에 통합 시나리오 Test 4개 (V2-N-53·54, V3-N-33, V5-N-19) |
| 2026-10-25 | S3-A: §5-4 V4-T-26·39·74를 W안으로 갱신 (V4-T-74는 code 그대로·순서만 변경), 알림 읽음 행에 Lock 분리 표시, §5-1에 V4-N-49~54 (API-SB-02)·V5-N-20, §6에 V5-N-19·20 |
| 2026-10-25 | S3 Commit 전: §5-4에서 V4-T-35와 알림 L240 행 분리, L240(B-2)을 G03-D-16 결정 대기로 표시 (기존 "그대로"는 미결 질문을 확정한 것처럼 읽힘) |
| 2026-10-25 | S4-E1: §3-1에 `a148a62` 실행 근거 (394/394 × 2, EV-010·011, `LOCAL_ONLY`) 추가, V4-T-01~93 ⑤ 갱신. 새 Test는 `NOT_RUN` 유지 |
| 2026-10-26 | V5-J2: §5-4에 알림 Test 6행 (V5-N-28·29·23 변경 후보, V5-N-27·성공 code·읽음 동시성 연관만)과 구분·운영 규칙, §6-1에 V5-N-21~30 (PR #16 Draft·`develop` 미반영, LOCAL_PASSED·CI 성공·Kotlin 이후 `NOT_RUN`) |
| 2026-10-26 | G-A2: §1에 공통 Domain 코드 `CM` 추가, §7 공통 `global` (CM-N-01~06, Test PR #15 Draft·`develop` 미반영, LOCAL_PASSED·CI 성공·Kotlin 이후 `NOT_RUN` 구분). 변경 이력을 §8로 이동 |

## 미리보기 STALE 정책의 후속 검증 명세 (2026-10-26)

이번 변경은 테스트 코드·실행이 아니라 명세다. 기존 D05-T8/V2-N-50와 관련 ML4/ML5 항목에 연결하고, 추가 검증의 정식 숫자 ID는 구현 Task에서 중복 확인 후 배정한다. 아래 이름은 시나리오 식별자이며 정식 Test ID가 아니다.

- PREVIEW-SAME: 대상·효과와 공식 근무 계획 동일 → 권한/충돌 재검증 후 성공, 실제 변경 결과와 응답 일치.
- PREVIEW-SHRINK: 자동 효과만 부분집합으로 감소 → 남은 대상 처리. 사라진 대상에 재변경/알림 없음.
- PREVIEW-ADD: 미리보기 뒤 DRAFT 또는 대타 영향 추가 →409 STALE, 이번 업무 변경/발행 알림0.
- PREVIEW-REPLACE: 건수 동일 또는 감소여도 다른 ID/효과 유입 →STALE, 업무 변경0.
- PREVIEW-EFFECT: 같은 후보 PENDING→ACCEPTED로 요청 종료 효과 발생 →STALE. 상태가 바뀌어도 효과가 정말 줄었는지는 명시적 효과 집합으로 판정.
- PREVIEW-PLAN: 공식 근무 ID/담당/상태/시각 변경 →계획 재확인. 자동 대상 감소 규칙으로 우회 불가.
- PREVIEW-INTEGRITY: 변조·다른 실행자/근무지/재가입 소속·유효하지 않거나 만료된 비교 정보 →거부, 변경0. 구체400/409 분기는 DTO 계약 확정 시 검사.
- PREVIEW-WAIT: 확정이 Lock을 기다리는 동안 영향 증가 →STALE. 비교 통과 이후 동시 DRAFT/후보 생성도 Guard로 직렬화되거나 거부되어 퇴사 후 부적격 Row가 남지 않음.
- PREVIEW-ROLLBACK: 처리 중 마지막 단계 실패 →소속·Shift·DRAFT·Request·Candidate·이력 모두 Rollback, AFTER_COMMIT 알림0.
- PREVIEW-REPLAY: 첫 성공 뒤 동일 요청 반복/두 관리자 동시 확정 →정의된 STATE_CONFLICT 등으로 추가 변경0. 성공 응답 유실은 조회로 확인; 자동 재전송/성공 재생을 가정하지 않음.
- PREVIEW-QUERY: 미리보기의 여러 조회 사이 경합을 강제해 표시와 비교 원본의 일관성을 확인. CONFIRM_REQUIRED 재요청 중 영향 확대에는 STALE이 우선.
- NEW-SHIFT-KEY: 신규 추가 첫409 저장0; 동일계획 확인 후생성, 키 변조/대상·시간변경에 이전확인 재사용불가, 동시추가는 기존충돌검사/Lock으로 중복정합성확인. 키 자체의 멱등성은 주장하지 않음.

경합은 실제 MySQL 별도 트랜잭션·Latch/Barrier로 순서를 강제하고 timeout/정리 및 worker 예외 회수를 포함한다. sleep이나 단순 반복 PASS로 동시성 안전을 주장하지 않는다. 완료 판정은 성공/거부 각각의 새 트랜잭션 DB 결과와 부작용을 확인한 실행 SHA·XML·로그로 한다. 운영 DB에서는 실행하지 않는다.

### V5-J4 다중 현재 소속 Query 보호 (제출안, 2026-10-26)

- 정식 ID 제안: **V5-N-33** (이번 Draft 리뷰에서 배정 확인, 아직 팀 확정 아님). 기존 예약31·32를 사용하지 않는다. J3의 작업 ID/정식 숫자 매핑과 별개로 중복 확인한다.
- 시나리오: J4-MULTI-ACTIVE. Class/Method: `NotificationQueryIntegrationTest.getNotificationsReturnsOwnNotificationsAcrossAllActiveWorkplaces`. ID 등록 때문에 JUnit 이름/테스트 소스를 변경하지 않았다.
- 구분: MIGRATION / AS-IS 보호, Java Notification + develop6793552의 Kotlin Workplace/V4.
- 계약: 같은 User의 두 현재 소속 AX/AY 알림만 정확히 반환하고 종료 AZ·다른 User BX/BW를 제외한다. 알림 ID와 근무지 ID·이름의 대응을 검사한다. 제외용까지5건을 실제 MySQL에 Commit한 뒤 Service 새 Transaction으로 조회한다.
- 근거: EV-021 최신 기준선400, EV-022 단독1, EV-023 전체401·기준400보존·Checkstyle/Gate. 전부 LOCAL_ONLY 개별 XML 근거.
- J2 V5-N-23의 정렬·HTTP 계약은 재사용하되 PR16은 미병합이다. 이 테스트는 다중 현재소속 합집합을 추가 보호한다. 재가입 새Row 격리·새 퇴사정책·AFTER_COMMIT·전체 동시성·운영은 완료 판정 범위 밖이다. J3/J5와 혼동하지 않는다.
