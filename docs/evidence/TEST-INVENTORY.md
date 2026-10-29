# Test Inventory

> ③ 문서 리뷰: **DRAFT** (D0-C-R 2026-10-23, D0-D 이관). 상태값은 `docs/STATUS-AXES.md`의 ④ 준비, ⑤ 실행 축을 쓴다.
> 실제 실행 결과는 `docs/evidence/TEST-EVIDENCE.md`(Append-only)에 남기고, 이 Inventory는 EV-ID로 연결만 한다.
> 구성: §1 필드, §2 규칙, §3 이후 Domain 구역 (V4 시범, V2는 ML-H1 및 API-LV-01 관련 부분), §5 Cross-Domain 카드의 임시 Test ID.

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
| V4-T-02 | V4-P10, G03-D-19 | `.test2` (OPEN·시작 지남 → CLOSED/EXPIRED) | UNIT · NONE | BOUNDARY | CLOSED/EXPIRED, `closedAt == startAt` (2026-10-28 G03-D-19, 전: `now`) | SUFFICIENT |
| V4-T-03 | V4-P10, G03-D-19 | `.test3` (ACCEPTED·`now == startAt` → 만료) | UNIT · NONE | BOUNDARY | CLOSED/EXPIRED, `closedAt == startAt` 확인 추가 (2026-10-28) | SUFFICIENT |
| V4-T-04 | V4-P10 | `.test4` (APPROVED 유지) | UNIT · NONE | NORMAL | APPROVED | SUFFICIENT |
| V4-T-05 | V4-P10 | `.test5` (CLOSED 저장값 유지) | UNIT · NONE | NORMAL | 저장된 사유·`closedAt` | SUFFICIENT |
| V4-T-06 | — | `.test6` (식별자·근무지 Mapping) | UNIT · NONE | NORMAL | Field 일치 | SUFFICIENT |
| V4-T-07 | C-45 (SUB-02) | `SubstituteCandidateServiceTest$FindRequestsTest.findReceivedRequests_Success` | UNIT · MOCK | NORMAL | 목록 | SUFFICIENT (Query는 T-13) |
| V4-T-08 | V4-P05, C-46 | `$AcceptTest.acceptCandidate_Success` | UNIT · MOCK | NORMAL | ACCEPTED | STRENGTHEN: DB 최종 상태 |
| V4-T-09 | V4-P06, C-47 | `$RejectTest.rejectCandidate_OtherPendingCandidateExists` (PR #5 전 이름 `rejectCandidate_Success_OtherPendingCandidateExists`) | UNIT · MOCK | NORMAL | OPEN 유지 | SUFFICIENT |
| V4-T-10 | V4-P06, C-47 | `$RejectTest.rejectCandidate_LastCandidate_ClosesRequest` | UNIT · MOCK | NORMAL | `ALL_CANDIDATES_REJECTED` | SUFFICIENT |
| V4-T-11 | V4-P05 | `$AcceptTest.acceptCandidate_AlreadyResponded` (PR #5 전 이름 `…_Fail`, `@ParameterizedTest` 2건: ACCEPTED·REJECTED) | UNIT · MOCK | FAILURE | SUB-022 | SUFFICIENT. 단 PR #5에서 "일찍 실패하면 User Lock을 잡지 않는다"(`verify(userService, never()).getByIdForUpdate`) 확인이 빠짐 (공통 확인 `assertFailsWithoutSideEffect`에 없음, 정적 확인). 필수 불변조건인지 ML-5에서 판단 |
| V4-T-12 | V4-P05, C-46, C-30 | `$AcceptTest.acceptCandidate_OfficialShiftConflict` (PR #5 전 이름 `…_Fail`) | UNIT · MOCK | FAILURE | SUB-005 | SUFFICIENT |
| V4-T-13 | C-45 (SUB-02) | `SubstituteCandidateRepositoryTest.findReceivedRequests_Success` | SLICE · MYSQL_TC (V4-N-03) | NORMAL | 필터 결과 | SUFFICIENT (V4-N-03에서 MySQL로 전환, EV-041) |
| V4-T-14 | C-45 (SUB-07) | `.findAcceptedPendingRequests_Success` | SLICE · MYSQL_TC (V4-N-03) | NORMAL | 필터 결과 | SUFFICIENT (V4-N-03에서 MySQL로 전환, EV-041) |
| V4-T-15 | V4-P02, P04, C-43, C-44 | `SubstituteRequestServiceTest$CreateTest.create_Success_SavesAsOpen` | UNIT · MOCK | NORMAL | OPEN + Candidate | STRENGTHEN: 후보 필터 조건별 (EMPLOYEE·요청자 제외·충돌 3종) |
| V4-T-16 | V4-P03 | `.create_NoCandidate_DoesNotCreateRequest` | UNIT · MOCK | NORMAL | `requestCreated=false` | SUFFICIENT |
| V4-T-17 | V4-P01 | `.create_Fail_ShiftNotFound` | UNIT · MOCK | FAILURE | SUB-009 | SUFFICIENT |
| V4-T-18 | V4-P01 | `.create_Fail_ScheduleNotPublished` | UNIT · MOCK | FAILURE | SUB-010 | SUFFICIENT |
| V4-T-19 | V4-P01 | `.create_Fail_ShiftCancelled` | UNIT · MOCK | FAILURE | SUB-014 | SUFFICIENT |
| V4-T-20 | V4-P01, C-42 | `.create_Fail_NotOwnShift` | UNIT · MOCK | FAILURE | SUB-011 | SUFFICIENT |
| V4-T-21 | V4-P01, P09 | `.create_Fail_ShiftAlreadyStarted` | UNIT · MOCK | BOUNDARY | SUB-012 | STRENGTHEN: `startAt == now` 경계 없음 |
| V4-T-22 | V4-P01 | `.create_Fail_ActiveRequestExists` | UNIT · MOCK | FAILURE | SUB-013 | SUFFICIENT |
| V4-T-23 | V4-P07, C-48 | `$ApproveTest.approve_success` | UNIT · MOCK | NORMAL | APPROVED | SUFFICIENT (DB 재조회는 V4-T-88, 실패 불변은 V4-N-02, EV-041) |
| V4-T-24 | V4-P07 | `.approve_fail_invalid_request_status` (PR #5에서 `@ParameterizedTest` 3건: OPEN·APPROVED·CLOSED) | UNIT · MOCK | FAILURE | SUB-002 | SUFFICIENT |
| V4-T-25 | V4-P07, P09 | `.approve_fail_past_shift_start_time` | UNIT · MOCK | BOUNDARY | 예외 | SUFFICIENT |
| V4-T-26 | V4-P07 | `.approve_fail_actor_not_in_workplace` (PR #83에서 둘로 나눔: 어디서도 MANAGER 아님 → 이 Method, 다른 근무지 MANAGER → `.approve_fail_actor_manager_of_other_workplace`) | UNIT · MOCK | FAILURE | WPL-004 / SUB-001 (PR #83, 전: WPL-003) | SUFFICIENT |
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
| V4-T-74 | S2-6 연결 | `SubstituteRequestServiceTest$ApproveTest.approve_fail_actor_not_manager` L371 (실패: 승인 요청자가 MANAGER가 아니면 요청을 조회하기 전에 예외가 발생한다, PR #83에서 이름·조회 전 거절 확인 추가) | UNIT · MOCK | FAILURE | WPL-004 (`MANAGER_REQUIRED`) | 미판정 |
| V4-T-75 | S2-6 연결 | `SubstituteRequestServiceTest$ApproveTest.approve_fail_schedule_not_published` L443 (실패: 근무표가 공개 전(DRAFT)이면 승인할 수 없다) | UNIT · MOCK | FAILURE | SCH-012 (`NOT_PUBLISHED`) | 미판정 |
| V4-T-76 | S2-6 연결 | `SubstituteRequestServiceTest$ApproveTest.approve_fail_shift_cancelled` L456 (실패: Shift가 취소됐으면 승인할 수 없다) | UNIT · MOCK | FAILURE | SFT-013 (`IS_CANCELED_SHIFT`) | 미판정 |
| V4-T-77 | S2-6 연결 | `SubstituteRequestServiceTest$ApproveTest.approve_fail_shift_member_changed` L470 (실패: 요청 이후 Shift 담당자가 바뀌었으면 승인할 수 없다) | UNIT · MOCK | FAILURE | SUB-004 (`INVALID_MEMBER`) | 미판정 |
| V4-T-78 | S2-6 연결 | `SubstituteRequestServiceTest$ApproveTest.approve_fail_accepted_candidate_not_found` L492 (실패: Request는 ACCEPTED인데 ACCEPTED Candidate가 없으면 예외가 발생한다) | UNIT · MOCK | FAILURE | SUB-008 (`NOT_FOUND_CANDIDATE`) | 미판정 |
| V4-T-79 | S2-6 연결 | `SubstituteRequestServiceTest$ApproveTest.approve_fail_accepted_member_left` L509 (실패: 수락한 사람이 퇴사했으면 승인으로 이어지지 않는다) | UNIT · MOCK | FAILURE | SFT-004 (`INVALID_WORKPLACE_MEMBER_VALUE`) | 미판정 |
| V4-T-80 | S2-6 연결 | `SubstituteRequestServiceTest$ApproveTest.approve_fail_accepted_member_other_workplace` L523 (실패: 수락한 사람이 다른 근무지 소속이면 승인할 수 없다) | UNIT · MOCK | FAILURE | SFT-004 (`INVALID_WORKPLACE_MEMBER_VALUE`) | 미판정 |
| V4-T-81 | S2-6 연결 | `SubstituteRequestServiceTest$ApproveTest.approve_fail_shift_starts_exactly_now` L570 (실패: 근무 시작 시각이 정확히 now(경계값)이면 승인할 수 없다) | UNIT · MOCK | BOUNDARY | SFT-006 (`INVALID_TIME_VALUE`) | 미판정 |
| V4-T-82 | S2-6 연결 | `SubstituteCandidateRepositoryTest.existsOverlappingAcceptedSubstitute` L404 (SUB-01/03 - 이미 수락한 활성 대타 약속과 시간이 겹치는지 확인한다) | SLICE · MYSQL_TC (V4-N-03) | NORMAL | 상태·목록 (본문 참조) | 미판정 |
| V4-T-83 | S2-6 연결 | `SubstituteCandidateRepositoryTest.existsConflictingActiveSubstitute` L447 (SUB-04 - 승인 시 현재 Request를 제외한 다른 활성 대타 약속과의 충돌만 확인한다) | SLICE · MYSQL_TC (V4-N-03) | NORMAL | 상태·목록 (본문 참조) | 미판정 |
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
| `SubstituteCandidateRepositoryTest` | `@DataJpaTest`, `@Import(TimeConfig)`, `@ActiveProfiles("test")`, `@AutoConfigureTestDatabase(replace = NONE)` (2026-10-29 V4-N-03에서 추가) | **MySQL 9.7.2 (Testcontainers)**. 이전에는 H2 (`@DataJpaTest` 기본 내장 DB 대체) | 실행 Log `MySQLDialect` 확인 (EV-041) |
| `SubstituteRequestConcurrencyTest` | `@SpringBootTest`, `@ActiveProfiles("test")` | **MySQL 9.7.2 (Testcontainers)**: `application-test.yml` `jdbc:tc:mysql:9.7.2`. Session RC: `application.yml` Hikari `TRANSACTION_READ_COMMITTED` | 설정은 VERIFIED_AS_IS, 실행 Log는 미확인 (같은 설정의 EV-002 Hibernate Log에서 RC 확인) |

### 3-4. 빠진 Test (신규 제안, ⑤ 전부 `NOT_RUN`)

| Test ID | Policy | 내용 | 종류 · 환경 | 구분 | ② 개선 승인 | ④ 준비 | 구현 가능 여부 |
|---|---|---|---|---|---|---|---|
| V4-N-01 | CB-COM-02·04, B1 | 8개 Endpoint의 Status·`code`·Envelope. SUB-01 201/200 분기, SUB-03 응답값 누락 → COM-001 | SLICE (WebMvc) · HTTP | MIGRATION (현재 Java 동작 고정) | 해당 없음 (동작 변경 아님) | SPECIFIED | 가능 (Java에서 먼저 작성) → 2026-10-29 `SubstituteControllerContractTest` `LOCAL_PASSED` (EV-041). Endpoint 9개 (문서의 8개 + #89 API-SB-01). 인증 Filter(401)는 범위 밖 |
| V4-N-02 | C-48 | APPROVE 후 DB의 `Shift.member`·APPROVED·`approvedAt`. 실패 시 세 Entity 불변 | INTEGRATION · MYSQL_TC RC | MIGRATION | 해당 없음 | SPECIFIED | 가능 → 2026-10-29 실패 불변(Candidate 포함, 실제 Commit)은 `SubstituteApproveFailureAtomicityTest` `LOCAL_PASSED` (EV-041). 성공 뒤 DB 재조회는 기존 V4-T-88 `SubstituteFlowIntegrationTest.requestAcceptApprove_ChangesShiftMember`, 실제 Rollback은 `SubstituteNotificationAfterCommitIntegrationTest.approveInRolledBackTransaction_keepsRequestAndShiftAndSavesNoNotification`로 충족 |
| V4-N-03 | C-45 | Candidate Repository Query를 MySQL에서 (`@ActiveProfiles("test")` + `@AutoConfigureTestDatabase(replace = NONE)`, PR #78 방식) | SLICE · MYSQL_TC | MIGRATION | 해당 없음 | SPECIFIED | 가능 → 2026-10-29 기존 `SubstituteCandidateRepositoryTest`를 MySQL로 전환 (기대값 변경 0), `LOCAL_PASSED` (EV-041) |
| V4-N-04 | C-54 | Substitute Event별 알림 Row 수신자·Type (Commit 후) | INTEGRATION · MYSQL_TC | MIGRATION | 해당 없음 | SPECIFIED | 가능 → 2026-10-29 기존 `SubstituteNotificationAfterCommitIntegrationTest`(V5-J3)로 충족 (새 Test 없음, §5-1 "같은 내용이면 기존 ID 참조") |
| V4-N-05 | SUB-EV-05 / SUB-DC-01 | 동시 ACCEPT 결정적 재현, RC·RR 대조 | H6 · MYSQL_TC RC+RR | PROPOSED_IMPROVEMENT | PENDING (DL-009, DL-010) | **BLOCKED** (Harness LOCAL_ONLY) | Harness 공유 후 가능 |
| V4-N-06 | ML-SUB-STATE-01 (B+) | 퇴사 후보 `INELIGIBLE` / `CANDIDATE_INELIGIBLE` | — | PROPOSED_IMPROVEMENT | **PROPOSED** | SPECIFIED (작성 금지) | 불가 (기능 없음). → ML-3 V4-N-13~16·22로 구체화, 2026-10-29 구현·`LOCAL_PASSED` ("V4 퇴사자 후보 처리" 절) |
| V4-N-07 | ML-CONCURRENCY-01-R | 퇴사 vs 후보 생성 경합 (H6-ML-01~03) | H6 · MYSQL_TC | PROPOSED_IMPROVEMENT | **PROPOSED** | SPECIFIED (작성 금지) | 불가 (퇴사 기능 없음). AS-IS 재현만 Test 전용 `left_at` 갱신으로 가능 |
| V4-N-23 | G03-D-19 D19-1 | 같은 만료 요청을 서로 다른 두 시각(`startAt` 이후)에 변환 → 두 응답의 `closedAt`이 같고 `== startAt` | UNIT · NONE | PROPOSED_IMPROVEMENT | APPROVED (DECISION-LOG §4-2) | `SentSubstituteRequestResponseTest``.test7` | `LOCAL_PASSED` (EV-036) |
| V4-N-24 | G03-D-19 D19-2 | 만료 요청을 목록 조회한 뒤 DB Row 확인 → status OPEN/ACCEPTED, DB `closedAt` = null 그대로 | INTEGRATION · MYSQL_TC | PROPOSED_IMPROVEMENT (현재 동작 고정을 겸함) | APPROVED (DECISION-LOG §4-2) | `SentSubstituteRequestExpiryIntegrationTest.listingExpiredRequestsKeepsDbRows` | `LOCAL_PASSED` (EV-036) |
| V4-N-25 | G03-D-19 D19-1 | `getSentRequests_ReturnsLatestFirst_MarksExpired` (L1633, 지금 L1258)에 만료 응답 `closedAt == startAt` 확인 추가 (기존 기대값 유지) | UNIT · MOCK | PROPOSED_IMPROVEMENT | APPROVED (DECISION-LOG §4-2) | 확인 1줄 추가함 | `LOCAL_PASSED` (EV-036) |
| V4-N-26 | G03-D-19 D19-1, D19-4 | OPEN이고 `now == startAt` → CLOSED / EXPIRED, `closedAt == startAt` | UNIT · NONE | PROPOSED_IMPROVEMENT | APPROVED (DECISION-LOG §4-2) | `SentSubstituteRequestResponseTest``.test8` | `LOCAL_PASSED` (EV-036) |

G03-D-19 관련 기존 Test 변경 후보 (승인 전 변경 금지, `PROCESS-CHARTER` §6-2): V4-T-02 `closedAt` 기대값 `now` → `SHIFT_START` (**기대값 변경**), V4-T-03에 `closedAt == SHIFT_START` 확인 추가. **2026-10-28 변경함** (G03-D-19 APPROVED, `feat/v4-expiry-closed-at` `0dab46a`, EV-036). V4-N-08~22는 로컬 ML-3 Draft가 예약한 번호라 G03-D-19는 23부터 쓴다.

신규 행의 선행 조건은 종류가 다르다.

| 행 | 성격 | 막고 있는 것 |
|---|---|---|
| V4-N-01~04 | Kotlin 전환 전 현재 Java 동작 고정 (`MIGRATION`, 동작 변경 0) | 정책 승인 대상 아님. V4 Owner 확정과 Task 승인 (`docs/ai/AI-RULES.md` §1) |
| V4-N-05 | 현재 동작의 동시성 재현 (Production 변경 0) | 정책 승인이 아니라 DL-009 (Harness 공유)·DL-010 (검증 지위) 결정. Harness가 `LOCAL_ONLY`라 ④ `BLOCKED` |
| V4-N-06·07 | 새 정책(② `PROPOSED`) 구현 검증 | `DECISION-LOG`의 `APPROVED` 전 작성 금지 (④ `SPECIFIED`까지) |
| V4-N-23~26 | 새 정책(G03-D-19, ② `PROPOSED`) 구현 검증 | 위와 같음. V4-N-24는 현재 동작 고정을 겸하지만 같은 카드에 묶여 있어 함께 승인 대상. **2026-10-28 해소** (G03-D-19 APPROVED, 작성·실행 EV-036) |

V4-N-07의 "AS-IS 재현"은 새 정책 구현 Test가 아니라 별도 후속 항목(ML-CONCURRENCY-02, 아직 이 Inventory에 없음)이며, V4-N-05와 같이 DL-009가 먼저 결정돼야 한다.
현재 동작 재현 Test 전반에 대한 일반 승인 규칙은 아직 문서화돼 있지 않다 (위 표는 각 행의 기존 기록을 모은 것이다).

## 4. V2 Workplace — ML-H1·퇴사 미리보기 관련 부분

V2 구역 전체 목록이 아니다. §4-1·4-2는 정책 카드 [`ML-H1`](../decisions/proposals/ML-H1-membership-history.md), §4-3은 API-LV-01에 연결된 Test다. V2 전체 Inventory(`V2-T-*` 기존 Test 번호 부여)는 V2 Delivery Sheet를 쓸 때 만든다.

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

### 4-3. API-LV-01 퇴사 미리보기 (PR #90)

**정적 확인 (2026-10-29):** [PR #90](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/90) 병합 SHA `e039ccf3627c89bab8c7d00ee02e0e65cb90bde6`의 Test 5개 파일을 기준으로 연결했다. 작업 기준 `cade4495635e4dc855cc769b4a4c658438ab6814`까지 해당 파일 변경 없음. V2 Delivery Sheet는 아직 없으며 API 범위는 [API-CATALOG §3-1](../contract/API-CATALOG.md#3-1-api-lv-01-퇴사-미리보기), 명세 ID 원문은 [ML-4 §10](../decisions/proposals/ML-4-leave-command-and-access.md#10-검증-계약-모두-specified--not_run)과 [ML-3 §11](../decisions/proposals/ML-3-substitute-on-leave-and-shift-change.md)을 따른다.

공통: 구분 `IMPROVEMENT`, ① `VERIFIED_AS_IS` (소스 확인), 아래 **조회 Test의 ④는 `IMPLEMENTED`**, **⑤는 `UNKNOWN`** (해당 병합 SHA의 개별 Test Report·EV-ID를 이 작업에서 확인하지 않음). 이번 문서 작업에서는 사용자 지시에 따라 Gradle·Test를 실행하지 않았다. PR 본문·리뷰의 실행 보고를 병합 SHA의 개별 `PASSED`로 옮기지 않는다. 정책 승인 범위는 Decision Log §4-2·4-4를 따르며 이 연결로 확대하지 않는다.

경로 기준: `backend/src/test/kotlin/com/merge/backend/domain/workplace/`. 아래 `Service`는 `service/LeavePreviewServiceIntegrationTest`, `Query`는 `repository/LeavePreviewQueryRepositoryTest`, `Controller`는 `controller/LeavePreviewControllerTest`, `Context`는 `service/LeavePreviewContextServiceTest`, `Transaction`은 `service/LeavePreviewTransactionTest`다.

| 명세 ID / 관련 계약 | Class · Method | 종류 · 환경 (소스 설정 기준) | 실제 확인하는 기대값 / 범위 |
|---|---|---|---|
| V2-N-23 | `Service.emptyPreview` | INTEGRATION · MYSQL_TC | 대상 소속 정보와 빈 영향 목록 |
| V2-N-23, V2-N-27 시간 경계 부분 | `Service.classifyShiftsAndPreserveComparisonFacts` | INTEGRATION · MYSQL_TC / REPEATABLE_READ | 진행 중·미래 공식 근무·DRAFT 분리, `startAt == now` 포함·`endAt == now` 제외, 취소 근무 제외, 비교용 식별·상태·시각 보존 |
| V2-N-23, ML-3 요청자 영향 (V4-N-08·12 관련) | `Service.previewOwnActiveRequestsWithoutChangingDatabase` | INTEGRATION · MYSQL_TC / REPEATABLE_READ | 본인 공식 근무의 OPEN·ACCEPTED 요청에 `wouldCloseRequest=true`. flush·clear 후 DB 상태 불변. 실제 요청 종료·알림은 검증하지 않음 |
| V2-N-23, ML-3-P·P1 (V4-N-13·15 관련) | `Service.countOnlyCurrentEmployeePendingCandidates` | INTEGRATION · MYSQL_TC / REPEATABLE_READ | 다른 현재 EMPLOYEE PENDING 후보만 집계. 남은 후보 있음/없음에 따른 `wouldCloseRequest=false/true`, `wouldBecomeIneligible=true`, DB 상태 불변 |
| V2-N-23, ML-3-A·Q1a (V4-N-16 관련) | `Service.distinguishPendingAndAcceptedEffectsOnAcceptedRequests` | INTEGRATION · MYSQL_TC / REPEATABLE_READ | ACCEPTED 요청의 수락 후보는 이력 유지·종료 예상, PENDING 후보는 자격 상실 예상·요청 종료 없음. 실제 DB 전이 없음 |
| V2-N-23, ML-3 활성 영향 범위 | `Service.excludeInactiveAndForeignCandidacies` | INTEGRATION · MYSQL_TC | CLOSED·APPROVED, 시작한 근무·DRAFT·다른 근무지 후보는 영향에서 제외 |
| V2-N-24 권한 규칙 관련 (조회 한정) | `Service.rejectOutsiderBeforeLookingUpTarget` | INTEGRATION · MYSQL_TC | 대상 조회보다 먼저 비소속자 `WPL-003` |
| V2-N-24 권한 규칙 관련 (조회 한정) | `Service.rejectEmployeeActor` | INTEGRATION · MYSQL_TC | 현재 EMPLOYEE 실행자 `WPL-004` |
| V2-N-24 권한 규칙 관련 (조회 한정) | `Service.rejectEndedManagerActor` | INTEGRATION · MYSQL_TC | 종료된 관리자 소속 `WPL-003` |
| ML-4 §5·API-LV-01 대상 범위 | `Service.rejectMissingOrForeignTarget` | INTEGRATION · MYSQL_TC | 없는 소속과 다른 근무지 소속 모두 `WPL-006` |
| V2-N-25 (= V2-N-18) 대상 규칙 관련 (조회 한정) | `Service.rejectManagerTarget` | INTEGRATION · MYSQL_TC | 관리자 퇴사 대상 `WPL-007` |
| V2-N-26 (V2-N-05 관련) 대상 규칙 관련 (조회 한정) | `Service.rejectEndedTarget` | INTEGRATION · MYSQL_TC | 이미 종료된 퇴사 대상 `WPL-008` |
| V2-N-23, V2-N-27 시간 경계 부분 | `Query.selectOngoingFutureAndAllDraftShiftsAtTimeBoundaries` | SLICE (DataJpa) · MYSQL_TC | 진행 중·미래 공식 근무와 과거/미래 DRAFT 조회, 종료·취소·다른 담당 제외 |
| V2-N-23, ML-3 활성 영향 범위 | `Query.selectOnlyTargetAssignmentsOrActiveCandidaciesInThisWorkplace` | SLICE (DataJpa) · MYSQL_TC | 대상 담당 근무 또는 대상의 PENDING·ACCEPTED 후보 요청만 조회, 근무지·상태·시각 제한 및 to-one 조회 확인 |
| V2-N-23, ML-3 후보 영향 범위 | `Query.fetchPendingAndAcceptedCandidatesWithoutRejectedOrUnrelatedRows` | SLICE (DataJpa) · MYSQL_TC | 지정 요청의 PENDING·ACCEPTED 후보만, REJECTED·무관한 요청 제외 |
| V2-N-23, V2-N-27 진행 중 표시 관련 | `Controller.serializePreviewWithOngoingShiftAndAllAffectedLists` | SLICE (WebMvc) · MOCK / HTTP (Filter 꺼짐) | 200 응답·`canLeave=false`·`blockingReason=ONGOING_SHIFT`·가장 늦게 끝나는 `ongoingShift` 및 영향 목록·Context 직렬화 |
| ML-4 §5, V2-N-24·25·26 오류 응답 관련 (조회 한정) | `Controller.preserveErrorEnvelope` | SLICE (WebMvc) · MOCK / HTTP (Filter 꺼짐) | `@EnumSource` 5입력: `WPL-003·004·006·007·008`의 HTTP·code·message·빈 data. 실제 인증 Filter 검증 아님 |
| V2-N-23 비교 정보, G03-D-05 §5-4 발급 부분 | `Context.bindIdentityOriginalFactsAndFifteenMinuteValidityToPurposeDerivedSignature` | UNIT · NONE | `lv1` 형식, 실행자·근무지·대상·원본·발급/만료 시각(900초), 용도 분리 HMAC 서명. ID가 달라지면 문자열 변경 |
| V2-N-23 비교 정보, G03-D-05 §5-4 정규화 부분 | `Context.normalizeCandidateOrderAndBindCandidateHistoryAndCurrentMembership` | UNIT · NONE | 후보 순서 변경 시 문자열 동일, 후보 상태·소속 종료 시각 변경 시 문자열 달라짐 |
| V2-N-23 조회 일관성, G03-D-05 §5-4 원본 범위 | `Transaction.useReadOnlyRepeatableReadAndRestoreDefaultIsolation` | INTEGRATION · MYSQL_TC | 외부 Transaction 없는 proxy 호출에서 read-only·REPEATABLE_READ 확인, 종료 후 READ_COMMITTED·read-only 해제, 빈 영향의 `canLeave=true` |

**정적 집계:** Method 20개 = Service 12 + Query 3 + Controller 2 + Context 2 + Transaction 1. Controller의 매개변수 5입력을 펼치면 예상 사례 24개이며 **실행 건수가 아니다**. MYSQL_TC는 `test` Profile의 Testcontainers MySQL 9.7.2 설정 기준이다. Service 클래스에는 `@Transactional(isolation = REPEATABLE_READ)`가 있으므로, 서비스 proxy의 독립 Transaction 확인은 별도 Transaction Test와 구분한다.

**명세 전체의 상태를 올리지 않는 항목:** `V2-N-23`의 조회 영향 Test는 구현되어 있다. `V2-N-24·25·26`의 퇴사 요청 거부·상태 불변, `V2-N-27`의 **확정 시 409 + 종료 시각**은 위 조회 Test와 관련된 별도 검증이다. 해당 확정 명세를 이 연결만으로 `IMPLEMENTED`로 바꾸지 않는다. 같은 이유로 `V4-N-08·12·13·15·16`의 실제 Request/Candidate 전이·알림, API-LV-02의 서명·만료 거부·현재 권한/DB 재검사·STALE 비교·Rollback·경합도 PR #90의 검증 완료 범위에 포함하지 않는다. 이 절은 다른 PR의 구현·실행 상태를 덮어쓰지 않는다.

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
| D19 (G03-D-19) | V4-N-23~26 (기존) | V4 | PROPOSED_IMPROVEMENT | 변경 없음. 2026-10-28 작성·`LOCAL_PASSED` |
| D21-T1 | 참조: `ShiftServiceTest` L412·L493 (V3-T 번호는 V3 Delivery Sheet 때) | V3 | BASELINE | 회귀 |
| D21-T2 | **V3-N-20** | V3 | MIGRATION | 기존 Test 없음 확인 (`SFT-010` Test 0, §5-3). 관련: V3-N-24 |
| D21-T3 | 참조: **V4-T-29** | V4 | BASELINE | 회귀 |
| D21-T4 | 참조: **V4-T-66** (관련: V4-T-91 통합 흐름) | V4 | BASELINE | 회귀 |
| D21-T5 | **V3-N-21** | V3 | MIGRATION | |
| D21-T6 | **V4-N-39** | V4 | PROPOSED_IMPROVEMENT | ML-2·ML-3 승인 후. → 2026-10-29 `LOCAL_PASSED` ("V4 공개 근무 변경 연결" 절) |
| D21-T7 = D05-T2 | **V2-N-41** | V2 | PROPOSED_IMPROVEMENT | §5-2 실제 중복 → 하나로 |
| D21-T8 = D05-T3 | **V2-N-42** | V2 | PROPOSED_IMPROVEMENT | §5-2 실제 중복 (D05-T3의 "목록 2건"까지 확인) |
| D21-T9 = D05-T6 | **V2-N-43** | V2 | PROPOSED_IMPROVEMENT | §5-2 실제 중복 (D05-T6의 "DB 변경 0"까지 확인) |
| D21-R1~R5 | 보류 (H6) | ML-5 | H6 | `BLOCKED` |
| D22-T1 | **V5-N-17** | V5 | MIGRATION | 알림 저장 실패가 업무에 영향 없음 (현재 동작 고정) |
| D22-T2·T3·T4·T4b·T5·T6·T7·T8·T9·T10·T11·T12 | **V4-N-27·28·29·30·31·32·33·34·35·36·37·38** (순서대로) | V4 | PROPOSED_IMPROVEMENT | 종료된 대타 조회. D22-T4b는 독립 번호 V4-N-30. **2026-10-28 API-SB-01 구현: V4-N-27~35·38 + 새 V4-N-60 작성·`LOCAL_PASSED` (EV-037, 아래 "V4 종료된 대타 조회" 절). V4-N-36·37(SUB-07·05 현재 소속)은 묶음 D**. **2026-10-29 V4-N-36·37 (CHG-03·02) `SubstituteCurrentMembershipListTest` `LOCAL_PASSED` (EV-042, 아래 "V4 보낸·수락한 요청 현재 소속" 절)** |
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
| M3-T1 | **V4-N-44** | V4 | PROPOSED_IMPROVEMENT | 승인 범위 밖 = 없는 ID와 같은 404. PR #83 `SubstituteApprovePermissionLockTest``.outOfScopeApproveGetsSameResponseRegardlessOfRequest` (W안: 어디서도 MANAGER 아님 403 `WPL-004`, 다른 근무지 MANAGER 404), `LOCAL_PASSED` |
| M3-T2·T3 | **V4-N-45·46** | V4 | PROPOSED_IMPROVEMENT · H6 | PR #83 `SubstituteApprovePermissionLockTest``.outOfScopeApproveDoesNotWaitForLocks`·`.approveRechecksStatusAfterLock`, 기존 `runSecondWhileFirstHoldsLock` 방식 (H6 Harness 대신), `LOCAL_PASSED` |
| M3-T4 | **V3-N-23** | V3 | PROPOSED_IMPROVEMENT | 근무 상세 |
| M3-T5 | **V4-N-47** (후보 응답·대타 종료) + **V5-N-18** (알림 읽음) | V4 / V5 | PROPOSED_IMPROVEMENT | Domain별로 나눔 |
| M3-T6 | 참조: 기존 BASELINE (V4-T-23·36 등, 각 API의 정상 경로 Test) | 각 Domain | BASELINE | 회귀 |
| NT-T1~T16 | **V5-N-01~16** (번호 그대로) | V5 | T3·T8·T12는 MIGRATION, 나머지 PROPOSED_IMPROVEMENT | §6 |
| ML5-T1~T15, ML5-R1~R4, H6-C1~C8 | 보류 (H6). ML5-T10 = V2-N-07, ML5-T11 = V2-N-12 (기존). 예외: ML5-T3(H6-C2) = PR #83 `SubstituteApprovePermissionLockTest``.approveAndShiftFirstHandoverDoNotDeadlock`, ML5-T13 = V4-N-46 장면. ML5-T1·T2·T4, H6-C1 ①②·C2 ①②·C3 = `SubstituteShiftChangeIntegrationTest` (실제 V3 취소·재배정 Command). H6-C5·C6 = `SubstituteMemberLeaveTest` (퇴사 흉내). ML5-R1·T5·T6·T8, H6-C5·C7 = `SubstituteMembershipGuardTest` (소속 Guard) | ML-5 | H6 | `BLOCKED` (ML5-R1·T1·T2·T3·T4·T5·T6·T8·T13, H6-C1·C2·C3·C5·C6·C7은 `LOCAL_PASSED`) |
| ErrorCode Test 0 (11개) | **V3-N-24~32**, **V4-N-48** | V3 / V4 | MIGRATION | §5-3 |

번호 사용 결과: V2-N-41~52, V3-N-20~32, V4-N-27~48, V5-N-01~18 (신설), V4-T-53~93 (기존 Test).

**S3 추가 (2026-10-25, [USER-PATH-REVIEW](../contract/USER-PATH-REVIEW.md) §11):** 새 통합 시나리오 Test는 임시 ID 없이 바로 정식 ID를 준다. 모두 `PROPOSED_IMPROVEMENT`, ④ `SPECIFIED` / ⑤ `NOT_RUN`.

| 정식 ID | 경로 | 소유 | 내용 원본 |
|---|---|---|---|
| V2-N-53 | UP-06 퇴사 전체 흐름 | V2 | USER-PATH-REVIEW §7 |
| V2-N-54 | UP-01 재가입 후 이전 소속 격리 | V2 | USER-PATH-REVIEW §2 |
| V3-N-33 | UP-03 공개 근무 취소·담당자 변경 + 대타 종료 + 알림 | V3 | USER-PATH-REVIEW §4. 대타 부분 = V4-N-61·62 (관련, "V4 공개 근무 변경 연결" 절). 알림·전체 경로는 V3 소유 그대로 |
| V5-N-19 | UP-07 알림 ↔ 대체 화면 일치 | V5 | USER-PATH-REVIEW §8 (S3-A: 관리자 행은 API-SB-02 포함) |
| V4-N-49 | API-SB-02 권한: 현재 MANAGER만. 직원 403 `WPL-004`, 비구성원 403 `WPL-003`, 다른 근무지 요청 미포함 | V4 | API-CATALOG API-SB-02 (S3-A) **2026-10-29 작성·`LOCAL_PASSED` (EV-044, 아래 "V4 관리자 끝난 대타 조회" 절, V4-N-50~54 같음)** |
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
| V4-N-48 | `SUB-003` | `SubstituteRequest.validateOpening` L57 (`accept()` L64) | ④ (Service가 먼저 `SUB-023`으로 막음) | Entity Unit: OPEN이 아닌 Request의 `accept()` → `SUB-003`, 상태 불변 → `SubstituteRequestTest.accept_NotOpen_ThrowsAndKeepsState` (ACCEPTED·APPROVED·CLOSED 3건), `LOCAL_PASSED` (EV-041) |

### 5-4. 기존 Test 기대값: Migration 보존 / Improvement 변경 (S2-5B 연결)

Kotlin Migration PR에서는 아래 **모든** 기존 Test의 기대값을 현재 값 그대로 둔다 (MIGRATION-MATRIX). "변경 시점"의 PR에서만, `DECISION-LOG` `APPROVED` 행을 근거로 바꾼다 (PROCESS-CHARTER §6-2). 새 정책으로 조용히 덮어쓰지 않는다.

| 기존 Test | 현재 기대값 (Migration 보존) | Improvement 기대값 | 근거 | 변경 시점 |
|---|---|---|---|---|
| V4-T-02 `test2` L53 | `closedAt == now` | `closedAt == startAt` | G03-D-19 (V4-N-23~26) | **변경됨: 2026-10-28 `feat/v4-expiry-closed-at` (EV-036)** |
| V4-T-25 (L556), V4-T-81 (L570) | `SFT-006` | 새 SUB code 409 | G03-D-03 T1-01 (V4-N-40) | D-03 승인 후 |
| V4-T-64·65·72 | `SUB-012` | `SUB-023` 409 | T1-02 (V4-N-41) | D-03 승인 후 |
| V4-T-63 `acceptCandidate_ShiftCancelled` | `SUB-014` | `SUB-023` 409 | T1-03 (V4-N-42) | D-03 승인 후 |
| V4-T-43 `close_ShiftNotScheduled_ThrowsException` L1173 | `SFT-005` | `SFT-013` 계열 | T1-05 (V4-N-43), P1 낮음 | D-03 승인 후 |
| `WorkplaceMemberRepositoryTest.rejectDuplicateMembershipAtDatabaseLevel` | `DataIntegrityViolationException` | ML-H2 재가입 구조에 따름 | ML-H2 (V2-N-06~) | ML-H2 승인 + DB Schema 변경 PR |
| SUB-05·`ShiftService.detail` 관련 (대상 Method는 ML-4 §8 승인 때 특정) | 현재 소속 확인 없음 | 현재 소속 기준 | ML-4 §8 | ML-4 승인 후 |
| SUB-006 문구 (T1-04) | — | — | message를 확인하는 Test 없음 → 기대값 변경 0 | — |
| Pattern `SHIFT-004`·`SHIFT-005` 4개 (`RegularShiftPatternControllerTest` L812·857·1449·1505) | 대상 직원 의미 | 그대로 (실행자 쪽 M-01 정정은 새 Test만) | ERROR-CODES M-01 | — |
| V4-T-26 `approve_fail_actor_not_in_workplace` (L413, PR #83 뒤 L345) (S2-6A 발견). 실행자 = 그 근무지 구성원 아님 → **범위 밖** | `WPL-003` 403 | **S3-A (W안) 갱신:** 실행자가 어디서도 MANAGER가 아니면 403 `WPL-004` (Request 조회 전, 없는 ID와 같은 응답), 다른 근무지 MANAGER면 404 `SUB-001` "존재하지 않는 대체근무 요청입니다." (없는 ID와 Status·code·message·`data` 동일). Unit Fixture를 실행자의 MANAGER 소속 유무로 나눈다. (S2-6A 안: 404 일괄) | API-CATALOG §5-5 "역할 먼저 검사", M3-T1 (V4-N-44) | **변경됨: PR #83 (2026-10-28, DECISION-LOG §4-6)** |
| V4-T-39 `close_NotManager_ThrowsException` (L1099) (S2-6A 발견). 이름과 달리 Mock은 `NOT_WORKPLACE_MEMBER` → **범위 밖** | `WPL-003` 403 | **S3-A (W안) 갱신:** 위 V4-T-26과 같음 (어디서도 MANAGER 아님 → 403 `WPL-004`, 다른 근무지 MANAGER → 404 `SUB-001`) | 같음, M3-T5 (V4-N-47) | CHG-07 승인 후 |
| V4-T-58 `acceptCandidate_NotOwnCandidate` (L348), V4-T-68 `rejectCandidate_NotOwnCandidate` (L577) (S2-6A 발견). Candidate는 본인만 볼 수 있음 → **범위 밖** | `SUB-020` 403 | 404 `SUB-019` "해당 대타 후보를 찾을 수 없습니다." (없는 Candidate ID와 동일) | M3-T5 (V4-N-47) | CHG-07 승인 후 |
| V4-T-74 `approve_fail_actor_not_manager` (L428, PR #83 뒤 L371) (S2-6A 발견). 실행자 = 같은 근무지 현재 구성원, **역할만 부족** | `WPL-004` 403 | **S3-A 결정 (W안, 사용자 잠정 결정 2026-10-25, 팀 `PENDING`): 어디서도 MANAGER가 아니면 403 `WPL-004` — 지금 code와 같고 검사 순서만 바뀐다 (Request 조회·Lock 전). 다른 근무지 MANAGER면 404 `SUB-001`.** (S3 검토 기록:) 현재 잠정 규칙(§5-5 "근무지 ID가 Path에 없는 API에서 관리자 아님 → 404", M3-T1 "같은 근무지 직원")대로면 404 `SUB-001`. 그러나 실행자가 그 요청의 요청자·후보라 이미 볼 수 있는 자원이면 §5-5의 "볼 수 있는 Resource → 상태에 맞는 응답" 원칙과 어긋나 403이 일관될 수 있다. 관계없는 직원이면 다른 사람 요청의 존재·상태를 알아내지 못하게 하는 404가 의미 있다 | API-CATALOG §5-5 "역할 먼저 검사", M3-T1 | **변경됨: PR #83 (2026-10-28, DECISION-LOG §4-6)** |
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

[DOMAIN-DEVELOPMENT-GUIDE](../delivery/DOMAIN-DEVELOPMENT-GUIDE.md) §7-3 V5-J2. **Java 기준선에서 관찰한 알림 HTTP 계약**을 고정한 Test다. 구분 `MIGRATION` (기존 동작 고정, 새 계약을 정하는 Test 아님). Java Test는 PR #16에서 추가·병합됐고, Kotlin 대응 Class는 PR #60에서 전환·병합됐다. Class: `domain.notification.controller.NotificationHttpContractTest` (`@SpringBootTest` + `@AutoConfigureMockMvc`, `/api/v1/...` + `.contextPath("/api/v1")`로 실제 `CustomAuthenticationFilter`·Security 설정 통과, `TestClock`, MYSQL_TC, `@Transactional` Rollback, DB 확인은 `flush()`·`clear()` 후 재조회).

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
- Java 기준선 CI: PR #16의 `Backend CI`·`Docker Build CI Test` 성공 (EV-020). **Workflow 단위 성공**이며 Kotlin 개별 Test 결과가 아니다.
- Kotlin 전환: PR #60에서 이 Class를 Kotlin으로 전환했다. PR #60의 [Backend Check](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/actions/runs/40577971110)와 [Docker Build CI Test](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/actions/runs/40577970990)는 성공했다. PR #60 시점의 Test별 JUnit XML은 보존하지 않았다. 이후 develop `0aec24f` (+ PR #79 Kotlin Test) 로컬 `clean check`에서 이 Class 10 / 10 PASS (Kotlin `LOCAL_PASSED`, 전체 515 / 515, 근거 TEST-INVENTORY §7 ②).
- 통합: PR #15와 #16은 모두 병합됐다. 이후 현재 develop 통합 실행은 TEST-GAP-ANALYSIS §4-1 C5에 기록한다. 과거의 예상 410건을 현재 기대값으로 사용하지 않는다.
- 범위 밖 (이 Test가 보장하지 않음): Commit 후 알림 생성·Rollback·알림 실패 (V5-J3), `AUTH-005`·`006` 자체 (기존 `CustomAuthenticationFilterTest`), Frontend 화면·다른 브라우저의 날짜 해석. P2로 미룬 것: V5-N-31 (잘못된 ID 형식 `COM-003`), V5-N-32 (알림 API의 `AUTH-006`).

## 7. 공통 `global` (CM) — G-A2 공통 응답 계약

[TEST-GAP-ANALYSIS](TEST-GAP-ANALYSIS.md) G-A2. **Java 기준선에서 관찰한 공통 응답 계약**을 고정하는 Test다 (모든 API 계약을 검증한 것이 아님). 구분 `MIGRATION` (기존 동작 보존). Java 원본 Test PR #15는 병합됐고, Kotlin 대응 Test는 PR #79에서 전환했다 (Class·Method 이름 유지).

| Test ID | Class · Method | Java 원본에서 확인한 보존 계약 | 종류 · 환경 | ⑤ 실행 (Java 기준선 · Kotlin 전환 후) |
|---|---|---|---|---|
| CM-N-01 | `global.dto.ApiResponseJsonContractTest.successResponseSerializesFourFields` | 응답 key가 정확히 `success`·`code`·`message`·`data`, `success` boolean, `code`·`message` 문자열 | SPRINGBOOT (애플리케이션 Jackson Mapper) · MYSQL_TC 설정 | Java `LOCAL_PASSED` · Kotlin `LOCAL_PASSED` |
| CM-N-02 | `…ApiResponseJsonContractTest.successResponseWithoutDataKeepsNullDataField` | data 없는 성공 응답도 `data: null` key 유지 | 같음 | Java `LOCAL_PASSED` · Kotlin `LOCAL_PASSED` |
| CM-N-03 | `…ApiResponseJsonContractTest.errorResponseWithoutDataKeepsNullDataField` | 오류 응답 `success: false` + `data: null` 유지 | 같음 | Java `LOCAL_PASSED` · Kotlin `LOCAL_PASSED` |
| CM-N-04 | `global.exception.ApiResponseHttpContractTest.signupSuccessResponseContract` | `POST /api/v1/auth/signup` → 201, `code "201"`, `data` key 정확히 `id`(number)·`email`·`name` | HTTP (MockMvc, `/api/v1` Context Path, Security Chain·전역 예외 처리기 포함) · MYSQL_TC, `@Transactional` Rollback | Java `LOCAL_PASSED` · Kotlin `LOCAL_PASSED` |
| CM-N-05 | `…ApiResponseHttpContractTest.businessErrorResponseContract` | 가입하지 않은 이메일 로그인 → 401 `AUTH-002`, 메시지 고정, `data: null` | 같음 | Java `LOCAL_PASSED` · Kotlin `LOCAL_PASSED` |
| CM-N-06 | `…ApiResponseHttpContractTest.validationErrorResponseContract` | 값이 빈 회원가입 → 400 `COM-001`, `data.fieldErrors[].{field, message}`, field 집합 {email, password, name} (문구·순서는 고정 안 함) | 같음 | Java `LOCAL_PASSED` · Kotlin `LOCAL_PASSED` |

**⑤ 상태의 뜻 (섞지 않는다):**
- Java `LOCAL_PASSED`: 로컬 macOS·GraalVM 25에서 신규 6/6 (EV-013), 전체 400/400 (EV-014). 개별 결과 근거는 로컬 JUnit XML (저장소 밖).
- Java 기준선 CI: PR #15의 `Backend CI`(`./gradlew check`, ubuntu·Temurin 25)·`Docker Build CI Test` 성공 (EV-016). **Workflow·Job 단위의 역사적 근거**이며 Kotlin 개별 Test 결과가 아니다.
- Kotlin `LOCAL_PASSED` (2026-10-28, PR #79): 같은 Class·Method 이름의 Kotlin Test로 전환했다. 개별 결과 근거는 로컬 JUnit XML 두 벌이다 (저장소 밖, 팀원 접근 불가. XML의 testcase 이름은 `@DisplayName` 값).
  - ① 전환 Commit `baf1aca` (base develop `933fd88`): `./gradlew clean check --no-daemon` JUnit XML 65개, 488 / 488 PASS. 전환 전 `933fd88` 488개와 Class#Test 이름 단위로 비교해 누락 0·추가 0·상태 변화 0. CM-N-01~06 6/6 PASS. 실행자 로컬 `s4-evidence/S2-global-tests/run-001/`.
  - ② 통합 확인 (develop `0aec24f` + PR #79 Kotlin Test 소스): `./gradlew clean check --no-daemon` JUnit XML 71개, **515 / 515 PASS** (= `933fd88` 488 + PR #74 27). CM-N-01~06 6/6 PASS. 실행자 로컬 `s4-evidence/S2-global-tests/run-002-integration-0aec24f/`.
  - 집계는 이름이 줄여 저장되는 결과 파일(`__TEST-…NotificationServiceScheduleTransactionIntegrationTest.xml`, 1건)까지 포함한다. `TEST-*.xml`만 세면 70개·514건으로 1건이 빠진다.
- Kotlin CI: PR #79 Head `2af320c` (develop `0aec24f` 병합 포함)의 [Backend Check](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/actions/runs/40660258512)·[Docker Build CI Test](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/actions/runs/40660258503) 성공. Suite 단위이며 CI는 JUnit XML을 보관하지 않으므로 CI만으로는 Test별 결과를 확인할 수 없다. 이후 Commit은 문서만 바꿨다.
- Java Test 잔여: PR #79 병합 시 `backend/src/test/java` 추적 파일 0개 (병합 전 develop `0aec24f`에는 17개). `backend/src/main/java`는 PR #77 병합으로 이미 0개. 병합 후 develop SHA에서 다시 센다 (S4 최종 Audit).

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
| 2026-10-28 | AUTH-1 코드 검토 반영: V1-N-03 확인 방법 보강, V1-N-13 Method 2개 추가, V1-N-14 추가 (EV-033) |
| 2026-10-28 | AUTH-1: 끝에 "V1 AUTH-1 서버 로그아웃" 절 신설. 예약 V1-N-01~12 등록, 범위 다음 번호 V1-N-13 추가 (출처 검사 단위 Test). ④ `IMPLEMENTED`, ⑤ `LOCAL_PASSED` (EV-032) |
| 2026-10-28 | ML-3 근무 변경 시 대타 종료: 끝에 "V4 근무 변경 시 대타 종료" 절 신설. 예약 V4-N-08~11·17·19·20 등록 (08·09는 V4 함수 부분만), 범위 다음 번호 V4-N-55~59 추가 (EV-034) |
| 2026-10-26 | G-A2: §1에 공통 Domain 코드 `CM` 추가, §7 공통 `global` (CM-N-01~06, Test PR #15 Draft·`develop` 미반영, LOCAL_PASSED·CI 성공·Kotlin 이후 `NOT_RUN` 구분). 변경 이력을 §8로 이동 |
| 2026-10-28 | API-SB-01 종료된 대타 조회: §5-1 D22 행에 구현 표시, 끝에 "V4 종료된 대타 조회" 절 신설 (V4-N-27~35·38, 새 V4-N-60) |
| 2026-10-28 | PR #79 Global Test Kotlin 전환: §7 CM-N-01~06 ⑤를 Java·Kotlin으로 나누고 Kotlin `LOCAL_PASSED` 근거(전환 Commit 488/488, develop `0aec24f` 통합 515/515)를 적음. 이전 "Kotlin 이후 `NOT_RUN`" 표기 대체. §6-1 V5-N-21~30도 같은 통합 실행 근거로 갱신. PR #79 병합 시 `src/test/java` 0개 |
| 2026-10-28 | PR #83 대타 승인 W안·Lock 순서: §5-4 V4-T-26·74 변경됨 표시, V4-T-26 기대값·나눈 Method, V4-T-74 이름·줄, §5-1 V4-N-44~46·ML5-T3·T13 `LOCAL_PASSED` (EV-035). V4-T-24는 준비 코드만 변경 (기대값 그대로) |
| 2026-10-28 | G03-D-19 만료 `closedAt = startAt`: V4-T-02 기대값·V4-T-03 확인 추가 표시, V4-N-23~26 ② APPROVED·④ 작성·⑤ `LOCAL_PASSED` (EV-036), §5-4 V4-T-02 행 변경됨 |
| 2026-10-29 | CHG-02·03 SUB-05·07 현재 소속: D22 행에 구현 표시, 끝에 "V4 보낸·수락한 요청 현재 소속" 절 신설 (V4-N-36·37, EV-042) |
| 2026-10-29 | API-SB-02 관리자 끝난 대타 조회: §5-1 V4-N-49 행에 구현 표시, 끝에 "V4 관리자 끝난 대타 조회" 절 신설 (EV-044) |
| 2026-10-29 | API-SB-02 PR #104 리뷰 반영: "V4 관리자 끝난 대타 조회" 절에 V4-N-50 Test 2개 (근무 시작된 APPROVED 제외, 수락자 여러 명 `SUB-016`) (EV-044) |
| 2026-10-29 | 퇴사자 후보 처리: 끝에 "V4 퇴사자 후보 처리" 절 신설 (V4-N-13~16·22, H6-C5·C6, 새 V4-N-65~71), V4-N-06 행·§5-1 H6 행에 연결 (EV-038) |
| 2026-10-29 | PR #92 리뷰 반영: "V4 퇴사자 후보 처리" 절에 V4-N-72~74 추가 (거절 경로 응답 가능 판정), ⑤ 상태 갱신 (EV-038) |
| 2026-10-29 | V4-N-21 (실패 경로 제외): "V4 종료된 대타 조회" 절에 행 추가, 실제 V3 재배정으로 확인 (EV-040). 실패 경로 미구현 기록 |
| 2026-10-29 | 공개 근무 변경 연결: 끝에 "V4 공개 근무 변경 연결" 절 신설 (V4-N-61·62, V4-N-39, H6-C1~C3, ML5-T1·T2), D21-T6·§5-1 H6·V3-N-33 행에 연결 (EV-039) |
| 2026-10-29 | TODO 21 현재 동작 고정: V4-N-01·02·03·48 작성 (`LOCAL_PASSED`, EV-041), V4-N-04·V4-N-02 일부는 기존 Test로 충족 표시, V4-T-13·14·23·82·83과 §3-3 환경 근거 갱신, 끝에 "V4 현재 동작 고정" 절 신설 |
| 2026-10-29 | ML5-T4 (다른 근무지 수락 × 재배정) 양쪽 순서 작성 (`LOCAL_PASSED`, EV-043), "이중 배정" 해석 기록 (ML-5 §7) |
| 2026-10-29 | 대타 생성·수락·승인 소속 Guard: ML5/H6 행 갱신, 끝에 "V4 대타 소속 Guard" 절 신설. 기존 Mock Test 14개는 준비 코드만 추가 (기대값 그대로) (EV-045) |

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

## V1 AUTH-1 서버 로그아웃 (V1-N-01~13, 2026-10-28)

[AUTH-1](../decisions/proposals/AUTH-1-server-logout.md) §10-1의 예약 번호를 그대로 쓴다. V1-N-13·14는 범위 다음 번호다 (§5-1 규칙). 구분 `IMPROVEMENT` (G03-D-23 APPROVED, DECISION-LOG §4-2). ④ `IMPLEMENTED` — Issue #62는 Backend PR #74 병합 후 종료됐다. Frontend PR #76도 병합됐다. EV-032는 로컬 구현 Test 근거이며, 각 PR의 CI 상태는 PR 기록에서 확인한다. 한 ID에 Method가 여러 개면 모두 적는다.

| Test ID | Class · Method | 확인하는 것 | 종류 · 환경 | ⑤ 실행 |
|---|---|---|---|---|
| V1-N-01 | `ApiV1UserLogoutTest.logoutClearsRefreshTokenAndDeletesCookie` | 200 "로그아웃되었습니다", `data: null`, DB `refreshToken` null, `Set-Cookie` Max-Age 0 | API · MYSQL_TC | LOCAL_PASSED |
| V1-N-02 | `.logoutWithOldDeviceTokenKeepsNewerLogin` | 예전 기기 쿠키로 로그아웃해도 DB = 새 기기 토큰, 새 기기 재발급 200 | API · MYSQL_TC | LOCAL_PASSED |
| V1-N-03 | `.logoutWithoutCookieSucceedsWithoutChange`, `.logoutWithForgedTokenSucceedsWithoutChange`, `.logoutWithExpiredTokenSucceedsWithoutChange`, `.logoutWithAccessTokenAsCookieSucceedsWithoutChange` | 쿠키 없음·위조·만료·Access Token → 200, DB 그대로, 쿠키 삭제 헤더. 위조·만료·Access Token은 **DB 값을 그 토큰으로 맞춰 둔 뒤** 확인 (DB 값과 같아도 토큰 검증에서 걸러지는지) | API · MYSQL_TC | LOCAL_PASSED |
| V1-N-04 | `.refreshAfterLogoutFails` | 로그아웃한 Refresh로 재발급 → 401 `AUTH-003` | API · MYSQL_TC | LOCAL_PASSED |
| V1-N-05 | `RqTest.deleteCookieUsesSameAttributesAsIssuedCookie` | Domain·Secure·SameSite를 채운 설정에서 삭제 쿠키의 Path·HttpOnly·Secure·SameSite·Domain = 발급 쿠키, Max-Age 0 (기존 test5는 Domain 빈 값·Path 미확인) | UNIT · NONE | LOCAL_PASSED |
| V1-N-06 | `ApiV1UserLogoutTest.logoutWithoutValidAccessTokenPassesSecurityFilter` | Access Token 없음 / 잘못된 Bearer 헤더에도 로그아웃 200 (같은 조건의 `/auth/me`는 401 `AUTH-004`·`AUTH-006`으로 Filter 동작 확인) | API · MYSQL_TC | LOCAL_PASSED |
| V1-N-07 | `AuthTokenServiceTest.genRefreshTokenReturnsDifferentTokensInSameSecond` | 같은 1초 두 번 발급해도 토큰이 다름. jti 추가 전 코드에서 실패 확인 (결함 재현) | UNIT · NONE | LOCAL_PASSED |
| V1-N-08 | `.genRefreshTokenFitsRefreshTokenColumn` | 64바이트 키(HS512) + 7자리 id에서 255자 이하. (이 Test 밖 실측: id 8자리부터 256자, EV-032. 배포 확인 항목) | UNIT · NONE | LOCAL_PASSED |
| V1-N-09 | `ApiV1UserLogoutTest.logoutWithAllowedOriginSucceeds`, `.logoutWithOtherOriginIsRejected`, `.logoutWithNullOriginIsRejected`, `.logoutWithAllowedRefererSucceeds`, `.logoutWithOtherRefererIsRejected`, `.logoutWithoutOriginAndRefererIsRejected` | 허용 Origin 200 / 다른 Origin·`null` 403 (기존 CORS가 먼저 거부, JSON 아님) / Origin 없음 + 허용 Referer 200 / 다른 Referer·둘 다 없음 403 `AUTH-007`. 거부 시 DB·쿠키 그대로 (`Set-Cookie` 없음) | API · MYSQL_TC | LOCAL_PASSED |
| V1-N-10 | `.oldTokenWithoutJtiStillWorks` | jti 없는 예전 형식 토큰도 재발급 200, 로그아웃 후 DB null | API · MYSQL_TC | LOCAL_PASSED |
| V1-N-11 | `UserLogoutConcurrencyTest.logoutWaitsForNewerLoginAndKeepsIt`, `.loginWaitsForLogoutAndNewerLoginRemains` | 한쪽이 행을 잡고 Commit 전 대기(Latch) → 다른 쪽이 Lock을 기다림(1초 Timeout 확인) → 최종 DB = 새 기기 토큰 | CONCURRENCY · MYSQL_TC RC | LOCAL_PASSED |
| V1-N-12 | `ApiV1UserLogoutDbErrorTest.logoutWithDbErrorFailsWithoutChange` | 진짜 조건부 UPDATE가 1행을 바꾼 뒤 DB 오류 주입 → 500 `COM-500`, `Set-Cookie` 없음, DB 토큰 되돌려짐 | API · MYSQL_TC (`@MockitoSpyBean`) | LOCAL_PASSED |
| V1-N-13 | `RequestOriginValidatorTest.isAllowedUsesOnlyOriginWhenPresent`, `.isAllowedComparesRefererOriginWhenOriginMissing`, `.isAllowedMatchesCorsRules`, `.isAllowedRejectsWildcardEntry`, `.isAllowedRejectsMissingOrBrokenHeaders` | Origin이 있으면 Origin만 비교 (CORS가 막지 못해도 거부), 없으면 Referer 출처(scheme·host·port). 비교는 Spring CORS와 같은 규칙 (목록 끝의 `/`·대소문자 무시, Referer 기본 포트 80·443 제거). 목록에 `*`가 있어도 거부. 앞글자만 같은 주소·깨진 값·둘 다 없음 거부 | UNIT · NONE | LOCAL_PASSED |
| V1-N-14 | `ApiV1UserLogoutOriginConfigTest.logoutWithOriginWrittenDifferentlyInConfigSucceeds` | 허용 목록을 `https://app.example.com/`, `https://Shop.Example.com`처럼 적어도 CORS를 통과한 Origin(소문자, `/` 없음)은 로그아웃 200 + CORS 허가 헤더 + DB null. 고치기 전 코드(정확히 같은 글자 비교)에서 403으로 실패 확인 (같은 설정의 임시 재현에서 응답 `AUTH-007`) | API · MYSQL_TC (Property 덮어쓰기) | LOCAL_PASSED |

**⑤ 상태의 뜻 (섞지 않는다):**
- `LOCAL_PASSED`: 로컬 macOS·Temurin 25에서 `7c1b77d` 전체 512/512 (EV-032), 수정 후 `8da19fa` 전체 515/515 (EV-033). 개별 결과 근거는 로컬 JUnit XML (저장소 밖).
- CI: PR 생성 후 기록. `develop` 미반영: PR Merge 전까지 `develop`에는 이 Test가 없다.
- 회귀 (기대값 변경 0): `UserServiceTest`, `ApiV1UserControllerTest`, `RqTest` test1~5, `CustomAuthenticationFilterTest` 그대로 통과 (EV-032).
- 범위 밖: Frontend 로그아웃·다시 시도 (FE-AUTH-01~09, 별도 Issue), 운영 CORS Origin·쿠키 SameSite·비밀키 길이 (배포 담당 확인).

## V4 근무 변경 시 대타 종료 (V4-N-08~11·17·19·20, V4-N-55~59, 2026-10-28)

[ML-3](../decisions/proposals/ML-3-substitute-on-leave-and-shift-change.md) §11의 예약 번호(V4-N-08~22)를 그대로 쓴다. V4-N-55~59는 V4에서 이미 쓴 번호(V4-N-54) 다음이다 (§5-1 규칙). 구분 `IMPROVEMENT` (G03-D-15 APPROVED, DECISION-LOG §4-2). ④ `IMPLEMENTED` — `feat/v4-shift-change-close-requests` (**PR 전, `develop` 미반영**). 모두 `SubstituteShiftChangeCloseTest` (MySQL Testcontainers, 실제 Commit). V3 근무 취소·담당자 변경 Command가 아직 없어서 "Shift를 `FOR UPDATE`로 잠근 Transaction 안에서 `SubstituteRequestService.closeActiveRequestsByShiftChange` 호출"로 흉내 낸다.

| Test ID | Method | 확인하는 것 | 종류 · 환경 | ⑤ 실행 |
|---|---|---|---|---|
| V4-N-08 (V4 함수 부분) | `closesOpenRequestAndKeepsCandidates` | OPEN + PENDING 후보 2명 → `CLOSED / SHIFT_CHANGED`, `closedAt = now`, 후보 PENDING 그대로, 반환 1건 (요청자 소속, 수락 후보 없음). 배정 이력 연결·근무 담당 변경·종료 알림은 V3·V5 몫이라 이 Test 범위 밖 | INTEGRATION · MYSQL_TC | LOCAL_PASSED |
| V4-N-09 (V4 함수 부분) | `closesAcceptedRequestAndKeepsAcceptedRecord` | ACCEPTED → `CLOSED / SHIFT_CHANGED`, 수락 후보 ACCEPTED와 응답 시각(`respondedAt`, 종료 1시간 전) 그대로, 반환에 수락 후보 소속 포함. 근무 취소·알림은 V3·V5 몫 | INTEGRATION · MYSQL_TC | LOCAL_PASSED |
| V4-N-10 | `requesterCanCreateNewRequestAfterClose` | 종료 뒤 같은 근무로 새 대타 요청 생성 성공 | INTEGRATION · MYSQL_TC | LOCAL_PASSED |
| V4-N-11 | `pendingCandidateCannotAcceptAfterClose` | 종료 뒤 대기 후보 수락 → `REQUEST_NOT_OPEN`, 요청 상태 그대로 | INTEGRATION · MYSQL_TC | LOCAL_PASSED |
| V4-N-17 | `leavesApprovedRequestAsIs` | APPROVED 요청은 그대로 (`closeReason` null, `approvedAt` 유지), 반환 0건, `Shift.member` = 수락자 그대로 | INTEGRATION · MYSQL_TC | LOCAL_PASSED |
| V4-N-19 | `closedRequestCannotBeReopened` | 종료된 요청에 수락·거절·승인·관리자 종료 → 모두 `BusinessException`, `CLOSED / SHIFT_CHANGED`와 후보 상태 그대로 | INTEGRATION · MYSQL_TC | LOCAL_PASSED |
| V4-N-20 | `publishesNoSubstituteEvent` | 종료 함수가 대타 이벤트를 발행하지 않음 (`MANAGER_CLOSED` 알림 0). 앞선 수락 이벤트가 세어지는지로 셈 자체를 확인 (`@RecordApplicationEvents`, `stream(Any::class.java)`) | INTEGRATION · MYSQL_TC | LOCAL_PASSED |
| V4-N-55 | `leavesClosedRequestAndClosesOnlyActive` | 하루 전 `MANAGER_CLOSED`된 요청은 사유·`closedAt` 그대로, 그 뒤 만든 진행 중 요청만 종료 (API-CATALOG §4 CLOSED 행) | INTEGRATION · MYSQL_TC | LOCAL_PASSED |
| V4-N-56 | `closesAllActiveRequestsInIdOrder` | 진행 중 요청 2건(서비스 검사로는 생기지 않는 데이터) → 모두 종료, 반환 ID 오름차순 (API-CATALOG §4 "모든 OPEN·ACCEPTED". G03-D-15의 2건 이상 처리 미결정 → 팀 확인 대상) | INTEGRATION · MYSQL_TC | LOCAL_PASSED |
| V4-N-57 | `requiresCallerTransaction` | Transaction 없이 호출 → `IllegalTransactionStateException`, 요청 OPEN 그대로 (`Propagation.MANDATORY`) | INTEGRATION · MYSQL_TC | LOCAL_PASSED |
| V4-N-58 | `waitsForAcceptInProgress` | 후보 수락이 Request를 잡고 Commit 전 → Shift를 잡은 종료 함수가 기다림(1초 Timeout 확인) → 수락 Commit 후 ACCEPTED 상태로 종료, 수락 후보 반환 (`runSecondWhileFirstHoldsLock`) | CONCURRENCY · MYSQL_TC RC | LOCAL_PASSED |
| V4-N-59 | `reportsAcceptedCandidateEvenIfRequestWasReadBeforeLock` | Lock 없이 요청을 먼저 읽어 둔(OPEN) Transaction에서 그사이 수락이 Commit돼도 수락 후보를 놓치지 않음 (1차 캐시의 옛 status를 보지 않음, ML-5 §4-3) | CONCURRENCY · MYSQL_TC RC | LOCAL_PASSED |

**⑤ 상태의 뜻 (섞지 않는다):**
- `LOCAL_PASSED`: 로컬 macOS·Temurin 25에서 `77bae13` 전체 500/500, develop 병합 `22a5a2e` 전체 527/527 (EV-034). 개별 결과 근거는 로컬 JUnit XML (저장소 밖).
- CI: PR 생성 후 기록. `develop` 미반영: PR Merge 전까지 `develop`에는 이 Test가 없다.
- 회귀 (기대값 변경 0): 기존 대타 Test 전체 그대로 통과 (EV-034).
- 범위 밖: V3 근무 취소·담당자 변경 Command와 그 경로 Test (V3-N-33, H6-C1~C4), 종료 알림 Type·받는 사람·합치기 (V5, NOTIFICATION-TYPES §3-3), 퇴사 시 후보 처리 (V4-N-12~16·22), 종료 원인 연결 (V4-N-18), 운영 `close_reason` ENUM 변경 (배포 담당, ML-3 §9).

## V4 종료된 대타 조회 (API-SB-01, V4-N-21·27~35·38·60, 2026-10-28)

[G03-D-22](../decisions/proposals/G03-D-22-notification-delivery.md) §9의 D22-T2~T9·T12와 새 V4-N-60. Class: `SubstituteAcceptedClosedRequestTest` (MySQL Testcontainers, test Profile). V4-N-21은 ML-3 §11의 예약 번호 (이력 화면 = API-SB-01: FRONTEND-CONTRACT §7, G03-D-22 §6-2 APPROVED 행, 2026-10-29 추가)

| Test ID | Method | 확인하는 것 | ⑤ 실행 |
|---|---|---|---|
| V4-N-27 (D22-T2) | `managerClosedRequestIsListed` | 관리자 종료 → 1건 `MANAGER_CLOSED`, 종료 시각 = DB `closedAt` | `LOCAL_PASSED` |
| V4-N-28 (D22-T3) | `expiredRequestIsListedWithShiftStart` | 근무 시작 1초 전 0건, 시작 시각부터 계산 만료 `CLOSED/EXPIRED`, 종료 시각 = `startAt`, DB 상태 그대로 | `LOCAL_PASSED` |
| V4-N-29 (D22-T4) | `approvedRequestIsListedForSevenDays` | 승인 당일 1건 APPROVED, 8일 뒤 0건 (근무 전이어도). "예정 근무에 보임"은 V3 근무표라 제외 | `LOCAL_PASSED` |
| V4-N-30 (D22-T4b) | `approvedRequestHidesCurrentAssignee` | 승인 뒤 담당자 변경(V3 인계 대신 `Shift.changeMember`로 흉내) → APPROVED 그대로, 응답 필드에 현재 담당자 없음. "빠진 근무"(API-SC-01)는 제외 | `LOCAL_PASSED` |
| V4-N-21 (실패 경로 제외, 관련: V4-N-30) | `approvedRequestKeepsExactlyOneAcceptedCandidate` | 승인 뒤 대기 후보 수락 → `REQUEST_NOT_OPEN`. 관리자가 그 후보에게 공개 근무 재배정(`ShiftService.reassignPublishedShift`, `closedSubstituteRequests = 0`) 뒤 후보 2명 = ACCEPTED(수락자)·PENDING, 담당 = 재배정 대상, API-SB-01은 수락자에게만 APPROVED 1건·현재 담당자가 된 후보에게 0건 (ML-3-H). 승인 전 ACCEPTED 1명은 기존 `SubstituteRequestConcurrencyTest.concurrentAcceptAllowsOnlyOneCandidate` 참조. ML-3 §3 "여러 명이면 이력 조회 오류" 실패 경로는 미구현 (오류 code·HTTP 상태 미정, 팀 결정 대상) | `LOCAL_PASSED` |
| V4-N-31 (D22-T5) | `closedRequestDisappearsAfterSevenDays` | 정확히 7일 1건, 7일 + 1초·8일 0건, DB Row 그대로 | `LOCAL_PASSED` |
| V4-N-32 (D22-T6) | `rejoinedMemberSeesOnlyCurrentMembershipRecords` | 퇴사 후 같은 근무지 재가입 → 이전 소속 기록 0건, 새 소속 기록만 1건 (PR #82 재가입 구조 사용) | `LOCAL_PASSED` |
| V4-N-33 (D22-T7) | `respondingToClosedRequestIsRejected` | 종료된 요청에 수락·거절 → `CANDIDATE_ALREADY_RESPONDED`·`REQUEST_NOT_OPEN`, 상태 그대로 | `LOCAL_PASSED` |
| V4-N-34 (D22-T8) | `pendingCandidateSeesNothing` | 대기 후보에게는 0건 | `LOCAL_PASSED` |
| V4-N-35 (D22-T9) | `shiftChangedRequestIsListed` | 근무 변경 종료(`closeActiveRequestsByShiftChange`) → `SHIFT_CHANGED` | `LOCAL_PASSED` |
| V4-N-38 (D22-T12) | `onlyMyAcceptedRequestsInLatestOrder` | 내 Candidate만, 효력 종료 시각 최신순, 다른 후보 정보 없음 | `LOCAL_PASSED` |
| V4-N-60 (새 ID) | `endedMembershipRecordsAreHidden` | 수락 뒤 그 근무지를 퇴사한 소속(`leftAt` 있음)의 기록 0건 (G03-D-22 §6-1 현재 소속 조건) | `LOCAL_PASSED` |

**⑤ 상태의 뜻:**
- `LOCAL_PASSED`: 로컬 macOS·Temurin 25에서 develop `55b1ac3` 위 전체 553/553 (EV-037). V4-N-21은 develop `19066d3` 위 전체 681 → 682/682 (EV-040). CI는 PR 생성 후 기록
- 회귀 (기대값 변경 0): 기존 Test 542개 목록 그대로 통과
- 범위 밖: V4-N-36·37 (D22-T10·T11, SUB-07·SUB-05 현재 소속 = CHG-02·03, 묶음 D. SUB-07 조건은 PR #82가 추가) → 2026-10-29 "V4 보낸·수락한 요청 현재 소속" 절에서 작성. Frontend "수락한 요청 — 종료" 화면

## V4 퇴사자 후보 처리 (V4-N-13~16·22, H6-C5·C6, V4-N-65~74, 2026-10-29)

[ML-3](../decisions/proposals/ML-3-substitute-on-leave-and-shift-change.md) §11 예약 번호와 §10 H6. 계약 Test는 범위 다음 번호 (V4-N-61~64는 공개 근무 변경 연결 Test 예정). Class: `SubstituteMemberLeaveTest` (MySQL Testcontainers, test Profile). 퇴사 Command가 없어 "소속 `leftAt` 기록 + 함수 호출"을 한 Transaction으로 흉내 낸다

| Test ID | Method | 확인하는 것 | ⑤ 실행 |
|---|---|---|---|
| V4-N-13 | `pendingCandidateBecomesIneligible` | 후보 B·C 대기, B 퇴사 → B `INELIGIBLE`(`respondedAt` null), C 대기, 요청 OPEN, 반환 닫은 요청 0·INELIGIBLE 1 | `LOCAL_PASSED` |
| V4-N-14 | `lastRejectAfterLeaveClosesAsCandidateIneligible` | 그 뒤 C 거절 → `CANDIDATE_INELIGIBLE`, 새 Event 1건(요청자·관리자), 기존 거절 Event 0건 | `LOCAL_PASSED` |
| V4-N-15 | `onlyPendingCandidateLeavesClosesRequest` | 유일한 대기 후보 퇴사 → `INELIGIBLE` + 요청 `CANDIDATE_INELIGIBLE`, 반환에 포함, 대타 Event 0건 | `LOCAL_PASSED` |
| V4-N-16 | `acceptedCandidateLeavesClosesRequest` | 수락자 퇴사 → 요청 `CANDIDATE_INELIGIBLE`, B ACCEPTED·C 대기 유지, 승인 `INVALID_REQUEST`, 요청자 재요청 성공 + 새 후보에 B 없음 | `LOCAL_PASSED` |
| V4-N-22 | `mixedRejectedAndIneligibleClosesAsCandidateIneligible` | B 거절, C 퇴사(INELIGIBLE), D 마지막 거절 → `CANDIDATE_INELIGIBLE` (기존 거절 경로 종료 판정 변경) | `LOCAL_PASSED` |
| H6-C5 ① | `leaveWaitsForAcceptInProgress` | B 수락이 Request를 잡은 채 → 퇴사 처리 대기 → 수락 반영 뒤 `CANDIDATE_INELIGIBLE`, B ACCEPTED | `LOCAL_PASSED` |
| H6-C5 ② | `acceptWaitsForLeaveInProgress` | 퇴사 처리가 먼저 → B 수락 대기 → `INVALID_CANDIDATE_MEMBER`, B `INELIGIBLE`, 요청 OPEN | `LOCAL_PASSED` |
| H6-C6 ① | `lastRejectWaitsForLeaveInProgress` | 퇴사(B)가 먼저 → C 마지막 거절 대기 → `CANDIDATE_INELIGIBLE` | `LOCAL_PASSED` |
| H6-C6 ② | `leaveWaitsForRejectInProgress` | C 거절이 먼저 → 퇴사 대기 → 응답 가능 후보 0명 → `CANDIDATE_INELIGIBLE` | `LOCAL_PASSED` |
| V4-N-65 | `requiresCallerTransaction` | Transaction 없이 호출 → `IllegalTransactionStateException`, DB 그대로 | `LOCAL_PASSED` |
| V4-N-66 | `leavesStartedShiftRequestsAsIs` | 근무 시작 뒤 퇴사 → 그 요청·후보 그대로 | `LOCAL_PASSED` |
| V4-N-67 | `leavesOtherWorkplaceCandidacyAsIs` | 같은 User의 다른 근무지 소속 후보는 그대로 | `LOCAL_PASSED` |
| V4-N-68 | `leavesApprovedAndClosedRequestsAsIs` | APPROVED·관리자 종료 요청과 그 후보 그대로 | `LOCAL_PASSED` |
| V4-N-69 | `handlesAllCandidaciesInIdOrder` | 걸린 요청 3건 모두 처리, 닫은 요청은 ID 오름차순, INELIGIBLE 3 | `LOCAL_PASSED` |
| V4-N-70 | `usesLatestStatusEvenIfReadBeforeLock` | 퇴사 Transaction이 Lock 전에 후보를 읽은 뒤 그 후보가 수락 → 최신 상태로 판단, ACCEPTED를 INELIGIBLE로 덮지 않음 | `LOCAL_PASSED` |
| V4-N-71 | `leftPendingCandidateIsNotRespondable` | 이미 퇴사했는데 PENDING으로 남은 후보(예전 데이터)는 응답 가능 후보로 세지 않음 → 요청 종료 | `LOCAL_PASSED` |
| V4-N-72 | `rejectWithLeftPendingCandidateClosesRequest` | 퇴사했는데 PENDING으로 남은 C가 있을 때 B 거절 → 응답 가능 후보 0명 → `CANDIDATE_INELIGIBLE`, 새 Event 1건, C는 PENDING 그대로 (PR #92 리뷰 재현, 원문 B+ §6 #8 상황) | `LOCAL_PASSED` |
| V4-N-73 | `rejectWithNonEmployeePendingCandidateClosesRequest` | 남은 대기 후보 C의 역할이 MANAGER → 응답 가능으로 세지 않음 → B 거절 시 `CANDIDATE_INELIGIBLE` | `LOCAL_PASSED` |
| V4-N-74 | `rejectWithOtherWorkplacePendingCandidateClosesRequest` | 다른 근무지 소속이 후보로 들어간 데이터 → 응답 가능으로 세지 않음 → B 거절 시 `CANDIDATE_INELIGIBLE` | `LOCAL_PASSED` |

**⑤ 상태의 뜻:**
- `LOCAL_PASSED`: 로컬 macOS·Temurin 25에서 develop `b411f2e` 위 전체 600/600, 리뷰 반영 후 develop `cade449` 병합 `443a68c` 위 전체 638/638 (EV-038). CI는 PR 생성 후 기록
- 회귀 (기대값 변경 0): 기존 Test 584개 목록 그대로 통과 (`rejectCandidate_LastCandidate_ClosesRequest` 포함). 리뷰 반영 때 Mock Test 3곳은 Mock하는 조회 이름만 바꿈 (`existsRespondableCandidateExcept`)
- 범위 밖: V2 퇴사 확정 경로 Test (V2-N-53), 대타 생성 × 퇴사 (H6-C7, 소속 `FOR SHARE` 필요), 알림 Type·받는 사람 (V5, V5-N-20), 요청자 퇴사 시 본인 근무의 요청 (V3 인계·취소의 `SHIFT_CHANGED`, V4-N-12)

## V4 공개 근무 변경 연결 (V4-N-61·62·39, H6-C1~C3, ML5-T1·T2·T4, 2026-10-29)

V3 공개 근무 취소(`ShiftService.cancelPublishedShift`, PR #87)·재배정(`reassignPublishedShift`, `b411f2e`)이 V4 `closeActiveRequestsByShiftChange`(PR #80)를 부르는 실제 경로를 MySQL에서 확인한다. Class: `SubstituteShiftChangeIntegrationTest` (MySQL Testcontainers, test Profile). V3 두 Command의 V3 Test는 Mock 단위 Test뿐이다. V4-N-61·62는 [ML-3](../decisions/proposals/ML-3-substitute-on-leave-and-shift-change.md) V4 범위 다음 번호 (V4-N-61~64를 이 연결 Test용으로 예약, PR #92에서 65~부터 사용), H6·ML5는 문서 ID 그대로 (§5-1 "같은 내용이면 기존 ID 참조")

| Test ID | Method | 확인하는 것 | ⑤ 실행 |
|---|---|---|---|
| V4-N-61 (V3-N-33 대타 부분, 관련) | `cancelClosesAcceptedRequest` | 수락된 요청 + 대기 후보 → 취소 → `closedSubstituteRequests = 1`, `CLOSED / SHIFT_CHANGED`·`closedAt = 취소 시각`, 후보 상태 그대로, 재요청 `SHIFT_CANCELLED`, 수락자의 API-SB-01에 `SHIFT_CHANGED` | `LOCAL_PASSED` |
| V4-N-62 (V3-N-33 대타 부분, 관련) | `reassignClosesOpenRequest` | OPEN 요청 → A → D 재배정 → `closedSubstituteRequests = 1`, `SHIFT_CHANGED`, 후보 그대로, D 재요청 성공 (V4-N-10 경로), 대기 후보 수락 `REQUEST_NOT_OPEN` (V4-N-11 경로) | `LOCAL_PASSED` |
| V4-N-39 (D21-T6) | `reassignToBlockedAccepterClosesAsShiftChanged` | 수락자가 불가능 시간 등록 → 승인 `CONFLICT_UNAVAILABLE_TIME` (D21-T3) → 관리자가 확인 후 그 수락자에게 재배정 → `SHIFT_CHANGED` (APPROVED 아님), 수락 기록 ACCEPTED, 담당 = 수락자, 배정 이력 1건 `MANAGER_REASSIGNED` | `LOCAL_PASSED` |
| H6-C3 | `createWaitsForCancelInProgress` | 취소가 Shift를 잡은 채 → 대타 생성 대기 → `SHIFT_CANCELLED`, 활성 요청 0 | `LOCAL_PASSED` |
| ML5-T1 | `createWaitsForReassignInProgress` | 재배정이 Shift를 잡은 채 → 원래 담당의 대타 생성 대기 → `NOT_OWN_SHIFT`, 활성 요청 0 | `LOCAL_PASSED` |
| ML5-T2 | `reassignWaitsForCreateInProgress` | 대타 생성이 Shift를 잡은 채 → 재배정 대기 → 새 요청을 `SHIFT_CHANGED`로 닫음, `closedSubstituteRequests = 1` | `LOCAL_PASSED` |
| H6-C1 ① | `reassignWaitsForAcceptInProgress` | 수락이 Request를 잡은 채 → 재배정 대기 → `SHIFT_CHANGED`, 수락 기록 남음, 담당 = 새 담당, 승인 `INVALID_REQUEST` | `LOCAL_PASSED` |
| H6-C1 ② | `acceptWaitsForReassignInProgress` | 재배정이 먼저 → 수락 대기 → `REQUEST_NOT_OPEN`, 후보 PENDING | `LOCAL_PASSED` |
| H6-C2 ① | `reassignWaitsForApproveInProgress` | 승인이 Shift·Request를 잡은 채 → 재배정 대기 → APPROVED 뒤 재배정, `closedSubstituteRequests = 0`, 담당 = 재배정 대상, 이력의 이전 담당 = 수락자, Deadlock 0 (ML5-T3 장면을 실제 재배정 Command로) | `LOCAL_PASSED` |
| H6-C2 ② | `approveWaitsForReassignInProgress` | 재배정이 먼저 → 승인 대기 → `INVALID_REQUEST`, `SHIFT_CHANGED`, 담당 = 재배정 대상 | `LOCAL_PASSED` |
| ML5-T4 ① | `reassignWaitsForOtherWorkplaceAccept` | 다른 근무지(W2) 수락이 User B를 잡은 채 → W1에서 B에게 재배정 대기 → 수락 반영 뒤 재배정 진행(담당 = B, 이력 1건) → W2 승인 `CONFLICT_SHIFT`, W2 담당 = 요청자. B의 공식 배정 1개 | `LOCAL_PASSED` |
| ML5-T4 ② | `otherWorkplaceAcceptWaitsForReassign` | W1 재배정이 User B를 잡은 채 → W2 수락 대기 → `CONFLICT_SHIFT`, B PENDING, W2 요청 OPEN. B의 공식 배정 1개 | `LOCAL_PASSED` |

**⑤ 상태의 뜻:**
- `LOCAL_PASSED`: 로컬 macOS·Temurin 25에서 develop `cade449` 위 전체 629/629 (EV-039). ML5-T4는 develop `e429343` 위 전체 729 → 731/731 (EV-043). CI는 PR 생성 후 기록
- 회귀 (기대값 변경 0): 기존 Test 619개 목록 그대로 통과. Production 코드 변경 없음
- 문서 불일치 (기록만, AI-RULES §3): [ML-5](../decisions/proposals/ML-5-concurrency-and-lock-order.md) §7은 ML5-T1 옆에 "(H6-C1)"이라고 적었지만 ML-3 §10 H6-C1은 "인계 × **수락**"이다. 이 절은 ML5-T1(인계 × 생성)과 H6-C1(인계 × 수락)을 따로 실행했다
- 범위 밖: H6-C4 (관리자 두 명 동시 재배정, 배정 이력 순서 = V3 소유), 종료 알림 Type·받는 사람·합치기 (V5), V3-N-33 알림·화면까지의 전체 경로 (V3)

## V4 현재 동작 고정 (V4-N-01~04·48, 2026-10-29)

구분 `MIGRATION` (현재 동작 고정, 동작 변경 0). V4 Production은 이미 Kotlin이라 §3-4의 "Java에서 먼저 작성"은 지금 Kotlin 동작을 고정하는 것으로 대신한다.

| Test ID | Class · Method | 확인하는 것 | ⑤ 실행 |
|---|---|---|---|
| V4-N-01 | `SubstituteControllerContractTest` (`@WebMvcTest`, Service Mock, Filter 없음) | Endpoint 9개 성공 Status·`success`·`code`·`message`·`data` Field 이름·null 출력·날짜 문자열, SUB-01 201/200 분기, SUB-03 `decision` 누락·null → 400 `COM-001` + `fieldErrors[decision]` + Service 호출 0, 지금 대타 API가 던지는 실패 code 30개(code마다 1번)의 Status·`code`·`message`·`data` null | `LOCAL_PASSED` |
| V4-N-02 | `SubstituteApproveFailureAtomicityTest.approve_FailsAtUnavailableTimeCheck_KeepsRequestCandidatesAndShift` (MySQL, 실제 Commit) | 승인이 수락자의 근무 불가 시간 검사(`SUB-006`)에서 실패 → Request ACCEPTED·`approvedAt`·`closedAt`·`closeReason` null, 수락 후보 ACCEPTED·`respondedAt` 그대로, 대기 후보 PENDING, `Shift.member` = 요청자. `SUB-006`은 정상 흐름으로 도달할 수 있는 마지막 실패 검사다 (뒤의 `SUB-007`은 수락 때 같은 조건으로 먼저 막혀 정상 흐름으로는 도달하지 않음) | `LOCAL_PASSED` |
| V4-N-03 | `SubstituteCandidateRepositoryTest` (4 Method, MySQL로 전환) | 기존 기대값 그대로 MySQL에서 (V4-T-13·14·82·83) | `LOCAL_PASSED` |
| V4-N-04 | 기존 `SubstituteNotificationAfterCommitIntegrationTest` | (새 Test 없음) | 그 Test의 ⑤ |
| V4-N-48 | `SubstituteRequestTest.accept_NotOpen_ThrowsAndKeepsState` | OPEN이 아닌 3상태: `validateOpening()`·`accept()` → `SUB-003`, 상태·종료 사유·시각 불변 | `LOCAL_PASSED` |

**⑤ 상태의 뜻:**
- `LOCAL_PASSED`: 로컬 macOS·Temurin 25에서 develop `19066d3` 위 전체 681 → 728/728 (EV-041). CI는 PR 생성 후 기록
- 회귀 (기대값 변경 0): 기존 Test 681개 목록 그대로 통과. Production 변경 없음
- V4-N-01 실패 code 목록은 지금 대타 Service(와 호출하는 공통 검사)가 던지는 code를 정적으로 모은 것이다. Service는 Mock이고 `GlobalExceptionHandler`는 Endpoint와 상관없이 같은 응답을 만들므로 code마다 1번만 확인한다 (Endpoint 칸은 그 code를 처음 던지는 곳일 뿐 그 Endpoint의 전체 실패 목록이 아니다). message는 Enum `getMessage()`로 비교한다 (문구가 바뀌어도 깨지지 않게, §5-4 "message 확인 Test 없음" 유지)
- TODO 20 (V4-N-40~43)과의 관계: V4-N-01은 "code → HTTP 응답"만 고정하므로 TODO 20의 기대값 변경은 0이다. 새 code만 목록에 추가하고, 대타에서 더 이상 던지지 않는 code(`SFT-006`·`SFT-005` 등)는 목록에서 뺀다. Mock 방식이라 목록이 낡아도 Test가 실패하지 않으므로 TODO 20 PR에서 함께 갱신한다
- 범위 밖: 인증 Filter(401, V1), ACCEPTED 여러 명일 때의 이력 조회 오류 (ML-3 §3, 팀 결정 대상)

## V4 보낸·수락한 요청 현재 소속 (CHG-02·03, V4-N-36·37, 2026-10-29)

[G03-D-22](../decisions/proposals/G03-D-22-notification-delivery.md) §9의 D22-T10·T11, [API-CATALOG](../contract/API-CATALOG.md) CHG-02·03. Class: `SubstituteCurrentMembershipListTest` (MySQL Testcontainers, test Profile)

| Test ID | Method | 확인하는 것 | ⑤ 실행 |
|---|---|---|---|
| V4-N-36 (D22-T10) | `acceptedListShowsOnlyCurrentMembershipCandidates` | 한 직원이 W1·W2에서 수락 → W1 소속만 종료(`leftAt` 직접 기록) → SUB-07에 W2 1건만, W1 Request·Candidate Row 그대로(ACCEPTED). 조건은 PR #82 `e643c30`이 이미 넣음 → Test만 추가 | `LOCAL_PASSED` (D22-T10 중 현재 소속 조건(방어)만. 퇴사 시 요청 종료는 #92 V4-N-12~16·22) |
| V4-N-37 (D22-T11) | `sentListShowsOnlyCurrentMembershipRequests` | 요청 → 소속 종료 → SUB-05 0건 → 같은 근무지 재가입 → 새 소속 요청 1건만, 이전 Request Row는 이전 소속을 가리킨 채 그대로. 관련: V2-N-54 (V2 소유 재가입 통합 시나리오) | `LOCAL_PASSED` |

**⑤ 상태의 뜻:**
- `LOCAL_PASSED`: 로컬 macOS·Temurin 25에서 develop `19066d3` 위 전체 681 → 683/683 (EV-042). CI는 PR 생성 후 기록
- 회귀 (기대값 변경 0): 기존 Test 681개 목록 그대로 통과. V4-T-47·48 (`SubstituteRequestServiceTest` `$SentRequestsTest`)의 Mock 대상 메서드 이름과 `SubstituteRequestConcurrencyTest`의 활성 요청 세기 헬퍼 호출 이름만 바뀜 (Assertion 변경 0이라 §5-4 표에 넣지 않음)
- 같은 근무지 재가입 × SUB-07은 PR #82 `MembershipRejoinCompatibilityIntegrationTest.acceptedSubstituteFromEndedMembershipIsExcludedAfterRejoin`이 이미 확인 (참조)
- 결함 넣기: SUB-05 조회에서 현재 소속 조건 제거 → V4-N-37 FAIL. 첫 작성(develop `cade449`)에서 SUB-07 `c.member.leftAt IS NULL` 삭제 → V4-N-36·PR #82 Test FAIL

## V4 관리자 끝난 대타 조회 (API-SB-02, V4-N-49~54, 2026-10-29)

[API-CATALOG](../contract/API-CATALOG.md) API-SB-02, [USER-PATH-REVIEW](../contract/USER-PATH-REVIEW.md) §9-0 결정 C. Class: `SubstituteClosedRequestTest` (MySQL Testcontainers, test Profile), `SubstituteClosedRequestControllerTest` (`@WebMvcTest`, HTTP 부분). API-SB-02는 사용자 잠정 결정이고 팀 승인은 PR 리뷰로 요청 중이다

| Test ID | Method | 확인하는 것 | ⑤ 실행 |
|---|---|---|---|
| V4-N-49 | `onlyCurrentManagerCanList` | 현재 관리자만 조회. 직원 403 `WPL-004`, 다른 근무지 관리자·퇴사한 관리자·없는 근무지 403 `WPL-003` | `LOCAL_PASSED` |
| V4-N-49 | `otherWorkplaceRequestsAreHidden` | 다른 근무지의 끝난 요청은 안 보임 (두 근무지 서로) | `LOCAL_PASSED` |
| V4-N-50 | `allCloseReasonsListedWithoutApprovedAndActive` | CLOSED 4사유(전원 거절·관리자 종료·근무 변경·후보 자격 상실) 모두 보임, APPROVED·진행 중 안 보임, 근무 변경 종료는 `shiftStatus = CANCELLED`, 수락자 이름(전원 거절은 null) | `LOCAL_PASSED` |
| V4-N-50 | `expiredRequestSplitsExactlyFromOngoingList` | 근무 시작 1초 전 안 보임·시작 시각부터 `EXPIRED`(`closedAt = startAt`), 두 시각 모두 SUB-06 목록과 겹침 0·합치면 전부, DB 상태·`closedAt` 그대로 | `LOCAL_PASSED` |
| V4-N-50 | `startedApprovedRequestIsNotListed` | 내일 근무 2건 수락·1건 승인 → 두 근무 시작 뒤(기본 7일 안) 만료 ACCEPTED만 보이고 근무가 시작된 APPROVED는 안 보임 (PR #104 리뷰: 기존 APPROVED Fixture는 근무가 6일 뒤라 Query에 APPROVED를 넣어도 통과) | `LOCAL_PASSED` |
| V4-N-50 | `duplicatedAcceptersFail` | 만료된 수락 요청의 대기 후보를 DB에서 ACCEPTED로 바꿈(데이터 이상) → 한 명만 고르지 않고 400 `SUB-016` (SUB-06과 같음, ML-3 §3) | `LOCAL_PASSED` |
| V4-N-51 | `defaultSevenDaysAndCustomPeriod` | 기본 7일 (정확히 7일 포함, 7일 + 1초 제외), 8일 뒤 `from`으로 조회, `from`·`to` 경계 포함 | `LOCAL_PASSED` |
| V4-N-51 | `pageBoundary` | `size = 2`, 3건 → 2건 `hasNext` true / 1건 false / 0건 false | `LOCAL_PASSED` |
| V4-N-51 | `invalidParametersAreRejected` | `from > to`, `page = -1`, `size = 0`·`101` → 400 `COM-003`, `size = 100`·`from == to` 허용, 직원은 잘못된 값이어도 403 `WPL-004` 먼저 | `LOCAL_PASSED` |
| V4-N-51 | `SubstituteClosedRequestControllerTest` 3개 | 파라미터 없으면 null·0·20 전달, ISO 날짜시각·page·size 전달, 형식 오류 400 `COM-003` (Service 호출 0) | `LOCAL_PASSED` |
| V4-N-52 | `requesterStillAssignedFact` | 담당 변경 전 true, 다른 직원으로 변경 뒤 false | `LOCAL_PASSED` |
| V4-N-52 | `shiftStartedFact` | 시작 1초 전 false, 시작 시각 true | `LOCAL_PASSED` |
| V4-N-52 | `hasNewerActiveRequestFact` | 같은 근무 새 요청 진행 중 → true, 새 요청도 종료 → 둘 다 false, 세 번째 요청이 근무 시작으로 만료 → false | `LOCAL_PASSED` |
| V4-N-53 | `requesterMembershipBoundary` | 요청자 퇴사 → `requesterMembershipEnded` true, 재가입해 새 소속이 그 근무를 맡아도 true·`requesterStillAssigned` false (새 소속과 합치지 않음) | `LOCAL_PASSED` |
| V4-N-53 | `onlyAllowedFields` | 응답 필드 목록 고정 (User ID·소속 ID·근무지 정보 없음), 거절 후보 이름 없음. JSON에 `requesterMemberId`·`workplaceId` 없음은 Controller Test | `LOCAL_PASSED` |
| V4-N-54 | `mixedEffectiveTimeOrderInDatabase` | CLOSED `closedAt`과 만료 `startAt`을 섞어 정렬, 같은 시각은 요청 ID 내림차순, `size = 1` 페이지로 넘겨도 같은 순서 (MySQL `CASE` 정렬 + `limit ?,?`) | `LOCAL_PASSED` |

**⑤ 상태의 뜻:**
- `LOCAL_PASSED`: 로컬 macOS·Temurin 25에서 develop `6d0f8ea` 위 전체 731 → 747/747 (EV-044). CI는 PR 생성 후 기록
- 회귀 (기대값 변경 0): 기존 Test 731개 목록 그대로 통과
- PR #104 리뷰 반영 (2026-10-29): 위 V4-N-50 Test 2개 추가, develop `6ae6683` 병합 위 842 / 842 → **844 / 844 PASS** (FAIL 0, 기존 목록 그대로 + 새 2개, 경고 같음, `Deadlock found` 0). 결함 넣기 2가지 (수락자 `associateBy`로 되돌림, Query 상태 조건에 APPROVED 추가) → 각각 새 Test가 잡음 (EV-044)
- 범위 밖: Frontend "끝난 요청" 탭과 표시 규칙(FRONTEND-CONTRACT §3-4, Test ID 없음), 성능·실행 계획 (USER-PATH-REVIEW §13-4 S4)

## V4 대타 소속 Guard (ML5-R1·T5·T6·T8, H6-C5·C7, 2026-10-29)

[ML-5](../decisions/proposals/ML-5-concurrency-and-lock-order.md) §4-4·§5·§7, [ML-3](../decisions/proposals/ML-3-substitute-on-leave-and-shift-change.md) §10. V2 퇴사 확정 POST 활성화 조건 (`docs/delivery/TASK-V2-69-leave-confirm.md` L70-71). Class: `SubstituteMembershipGuardTest` (MySQL Testcontainers, test Profile). 퇴사는 "소속 Row만 잠그고 `leftAt` 기록"으로 흉내 내 소속 Guard만으로 직렬화되는지 본다 (실제 V2 퇴사는 Request를 먼저 잠근다)

| Test ID | Method | 확인하는 것 | ⑤ 실행 |
|---|---|---|---|
| ML5-R1 / H6-C7 ① | `createWaitsForCandidateLeaveAndSkipsLeftCandidate` | B 퇴사가 Commit 전 → 대타 생성이 B 소속 Lock을 기다림 → 퇴사 Commit 뒤 B는 후보가 아니고 C만 PENDING (반례 A 차단) | `LOCAL_PASSED` |
| ML5-T6 / H6-C7 ② | `leaveWaitsForCreateInProgress` | 대타 생성이 B 소속 `FOR SHARE`를 잡은 채 → 퇴사 대기 → 생성 Commit 뒤 퇴사가 새 요청의 B를 INELIGIBLE로 정리, 요청 OPEN (V2 실제 경로의 409는 V2 범위) | `LOCAL_PASSED` |
| (요청자) | `createWaitsForRequesterLeave` | 요청자 퇴사가 Commit 전 → 대타 생성 대기 → `NOT_OWN_SHIFT`, 요청 0건 | `LOCAL_PASSED` |
| ML5-T5 / H6-C5 | `acceptWaitsForLeaveOfCandidate` | 후보 B 퇴사가 Commit 전 → 수락 대기 → `SUB-021`, 요청 OPEN, B PENDING | `LOCAL_PASSED` |
| ML5-T8 | `approveWaitsForLeaveOfAccepter` | 수락자 B 퇴사가 Commit 전 → 승인 대기 → `SFT-004`, 요청 ACCEPTED, 근무 담당 = 요청자 | `LOCAL_PASSED` |

**⑤ 상태의 뜻:**
- `LOCAL_PASSED`: 로컬 macOS·Temurin 25에서 develop `63c46d1` 위 전체 803 → 808/808, 최신 develop `6ae6683` 위 826 → 831/831 (EV-045). CI는 PR 생성 후 기록
- 회귀 (기대값 변경 0): 기존 Test 목록 그대로 통과. 기존 Mock Test 14개(`SubstituteCandidateServiceTest` 수락 6, `SubstituteRequestServiceTest` 승인 6·생성 2)는 새 소속 재확인 조회의 Mock 설정만 추가 (Assertion·기대값 변경 0, AI-RULES §2 "Fixture" 원인)
- 수락·승인 Guard는 실제 V2 퇴사가 Request를 먼저 잠가 이미 직렬화되는 경로의 방어 조건이다. 대타 생성 Guard는 퇴사의 조회에 보이지 않는 새 Request의 반례 A를 막는다
- 범위 밖: ML-3 H6-C8 (요청자 퇴사 × 승인, V2·V3 경로), V2 실제 퇴사 Command와의 경합 (V2 Test)
