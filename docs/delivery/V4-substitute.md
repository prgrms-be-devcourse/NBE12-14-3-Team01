# Domain Delivery Sheet — V4 Substitute (시범)

> ③ 문서 리뷰: **DRAFT** (D0-C 작성 2026-10-22, D0-C-R 수정 2026-10-23, D0-D 3차 이관 2026-10-23). 상태값은 `docs/STATUS-AXES.md`, 양식은 `docs/delivery/TEMPLATE.md`.
> 경로 약어: `S/` = `backend/src/main/java/com/merge/backend/domain/substitute/`, `T/` = `backend/src/test/java/com/merge/backend/domain/substitute/`.
> 계약은 G-03 C-ID로만 참조한다. G-03 원문은 참고 공유본 [`docs/references/G-03-contract-review.md`](../references/G-03-contract-review.md)에 있다 (Original `9858612` 기준 AI 분석, 승인된 계약 아님. 용어 대응은 [`docs/references/README.md`](../references/README.md) §4). C-ID 대응은 D0-E-2B (2026-10-23)에 원문과 대조해 정정했다. G-03 계약은 ① `REVIEW_ACCEPTED`이며 ② `APPROVED`가 아니다. 2차 P0 PDF는 이번 환경에서 읽지 못했으므로 `SOURCE_STATED` 표기는 쓰지 않았다.

## A. 기본 정보

| 항목 | 값 | 상태 |
|---|---|---|
| Domain / Owner | V4 Substitute / 최세현 (MIGRATION-MATRIX 초안 값) | ① UNKNOWN (팀 확인 전) |
| Java Baseline | 2차 `8da2bec` = 3차 `main` `8da2bec`. 최종 Baseline 선언(`BL-TBD`)은 B3 검증 전 | ① VERIFIED_AS_IS (SHA 일치) |
| Kotlin Target | 3차 `develop` `cdaaa13` (PR #1: Kotlin 2.3.21, `plugin.spring`·`jpa`·`lombok`, Gradle 9.3.0)에서 갈라지는 Feature Branch | ① VERIFIED_AS_IS (환경) / Version 결정 기록 ② PENDING (DL-003·004) |
| 대상 Package | `domain/substitute/**` 28개 (controller 3, dto 9, entity 5, event 6, exception 1, repository 2, service 2) | ① VERIFIED_AS_IS |
| 관련 계약 | C-42~C-50 (G-03 §3.7 A-06), C-22 (Shift 담당 자격), C-30 (User 전역 충돌), C-54 (알림 생성 정책) | ① `REVIEW_ACCEPTED` (G-03 Disposition). C-54 ③ 후보 0명 알림의 Product Scope 승인은 `NOT_RECORDED` |
| 관련 계약 (상태 다름) | C-41 상태 모델 (§3.7) / C-40 Substitute 쪽 Unavailable 처리 (§3.6 A-05 절, UNA-MIG-11) | C-41: `DECISION_PENDING` (G03-D-17). C-40: `CONDITIONAL`, 실패 처리 방식은 `APPROVAL_PENDING` (G03-D-21). **둘 다 `REVIEW_ACCEPTED` 아님** |
| 관련 Decision | G03-D-09, D-15, D-17~D-21, DL-002 (전역 RC), DL-009 (H6 Harness) | ② PENDING |
| 관련 Improvement | H6, ML-SUB-STATE-01 (B+), ML-CONCURRENCY-01-R, Membership Lifecycle | ② PROPOSED (문서 파일 없음) |

## B. 실제 구현 현황 (① 전부 VERIFIED_AS_IS, `8da2bec`)

### B1. API (8개, context-path `/api/v1` 제외)

| # | Method · Path | 실행자 | 성공 | 주요 실패 (Status · code) | 근거 |
|---|---|---|---|---|---|
| SUB-01 | `POST /shifts/{shiftId}/substitute-requests` | 근무 당사자 (Role 무관) | **201** Request 생성 / **200** `requestCreated=false` (후보 0) | 404 SUB-009, 400 SUB-010, 400 SUB-014, 403 SUB-011, 400 SUB-012, 409 SUB-013 | `SubstituteController` L23~45, `SubstituteRequestService.create` L65~115, `validateSubstituteRequest` L130~157 |
| SUB-05 | `GET /substitute-requests/sent` | 로그인 User | 200 | — | `SubstituteController` L49~56, Service L117~127 |
| SUB-02 | `GET /substitute-requests/received` | 후보 본인 | 200 | — | `SubstituteRequestController` L30, `SubstituteCandidateService.findReceivedRequests` L79~89 |
| SUB-07 | `GET /substitute-requests/accepted` | 수락한 후보 | 200 | — | `SubstituteRequestController` L53, CandidateService L91~101 |
| SUB-06 | `GET /workplaces/{workplaceId}/substitute-requests/ongoing` | 현재 MANAGER | 200 | 403 WPL-003/004, 404 SUB-015, 400 SUB-016 | `SubstituteRequestController` L76, Service L160~230 |
| SUB-03 | `PATCH /substitute-candidates/{candidateId}/response` (body `@NotNull`) | 후보 본인 | 200 | 404 SUB-019, 403 SUB-020, 403 SUB-021, 409 SUB-022, 409 SUB-023, 400 SUB-010/014/012, 409 SUB-005~007 | `SubstituteCandidateController` L26~66, `validateRespondableCandidate` L104~172, `acceptCandidate` L176~250, `rejectCandidate` L252~290 |
| SUB-04 | `PATCH /substitute-requests/{requestId}/approve` | 현재 MANAGER | 200 | 404 SUB-001, 400 SUB-002, 400 SUB-004, 409 SUB-008, 400 SFT-004, 409 SUB-005~007, SCH·SFT 계열 | `SubstituteRequestController` L95, `approve` L232~352 |
| SUB-08 | `PATCH /substitute-requests/{requestId}/close` | 현재 MANAGER | 200 | 404 SUB-001, 400 SUB-017, 400 SUB-018, SCH NOT_PUBLISHED, SFT INVALID_STATUS_VALUE | `SubstituteRequestController` L113, `close` L360~415 |

공통: `ApiResponse` Envelope (CB-COM-02), `GlobalExceptionHandler` (CB-COM-04), ErrorCode `S/exception/SubstituteRequestErrorCode.java` SUB-001~023. "현재 구성원 아님"이 Domain마다 다른 Code·Status로 나가는 것은 G03-D-04/D-09 대상이며 **보존**한다.

### B2. 구성 요소

| 계층 | Class (주요 메서드) | 비고 |
|---|---|---|
| Controller | `SubstituteController`, `SubstituteRequestController`, `SubstituteCandidateController` | 성공 code 문자열은 Controller에서 지정 |
| Service | `SubstituteRequestService` (create, getSentRequests, list, approve, close), `SubstituteCandidateService` (findCandidates, createCandidates, findReceivedRequests, findAcceptedPendingRequests, acceptCandidate, rejectCandidate) | Command `@Transactional`, 조회 `readOnly`. `findCandidates`(`readOnly`)는 `create` Transaction 안에서 호출됨 |
| Repository | `SubstituteRequestRepository.findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)` L22), `findAllByWorkplaceId` L42~48 / `SubstituteCandidateRepository` 확정 대타 충돌 L26~37, 받은 요청 L59, 수락 대기 L86, 충돌 제외 L120 | 상태 비교에 문자열 literal(`'PENDING'`)과 Enum 경로가 섞여 있음 |
| Entity / Enum | `SubstituteRequest` (accept, closeAllCandidatesRejected, approveRequest → `shift.changeMember`, closeByManager), `SubstituteCandidate` (reject, acceptRequest), `RequestStatus`, `CandidateStatus`, `RequestCloseReason` | Lombok `@Getter @NoArgsConstructor` |
| DTO | Response 8개 + `SubstituteCandidateRespondRequest` | `SentSubstituteRequestResponse.from`이 EXPIRED 계산 (L28~52) |
| Event | 6개 → V5 Listener (`AFTER_COMMIT`, `REQUIRES_NEW`, 실패 시 Log) | CB-EVT-01 |

### B3. DB 제약

| 제약 | 근거 | ① 상태 |
|---|---|---|
| `(request_id, member_id)` Unique | Entity `@UniqueConstraint` | 코드 VERIFIED_AS_IS / MySQL 실존 UNKNOWN (CB-DB-01 기록 범위 확인 필요) |
| FK 4개 NOT NULL (`shift_id`, `requester_member_id`, `request_id`, `member_id`) | `@JoinColumn(nullable = false)` | 코드 VERIFIED_AS_IS |
| `status`, `close_reason` Column 형식 | test는 `ddl-auto: create`, 운영 미확인 | UNKNOWN |

### B4. 정의에만 있거나 정해지지 않은 것

| 항목 | 현재 Java | ② 상태 |
|---|---|---|
| `EXPIRED` 저장 | 저장하지 않음. 응답에서만 계산 | G03-D-19 PENDING |
| 철회·재OPEN | 없음 | G03-D-18 PENDING |
| 요청자 `leftAt` 검사 | 없음 | G03-D-15 PENDING |
| 이전에 거절한 후보 제외 | 없음 (재요청 시 다시 후보가 됨, §C3) | 정책 없음 (UNKNOWN) |

## C. 기존 정책 (보존 대상)

모든 행: ① 코드는 VERIFIED_AS_IS. 계약 칸의 C-ID 상태는 G-03 원문을 따른다 (대부분 `REVIEW_ACCEPTED`, 예외는 해당 행에 표기). ② 정책 승인은 별도 기록 없음. G-03과 현재 코드의 충돌은 V4 범위에서 0건 (D0-E-2B 대조).

| 정책 ID | 규칙 요약 | 코드 근거 | 계약 | 관련 Test |
|---|---|---|---|---|
| V4-P01 | 요청자는 근무 당사자 본인 (Role 무관), 대상은 PUBLISHED ∧ SCHEDULED ∧ `startAt > now`, 활성 요청(OPEN/ACCEPTED) 1개 | `validateSubstituteRequest` L130~157 (Shift `FOR UPDATE`) | C-42, C-50 | V4-T-17~22, V4-T-49 |
| V4-P02 | 후보 = 같은 근무지 `leftAt == null` ∧ **EMPLOYEE** ∧ 요청자 제외 ∧ (다른 근무지 포함) 공식 근무·불가능 시간·확정 대타와 겹치지 않음 | `findCandidates` L44~69 (EMPLOYEE 필터 L50) | C-43, C-40, C-30 | V4-T-15 (Mock, 필터 조건별 검증 없음) |
| V4-P03 | 후보 0명이면 Request를 저장하지 않고 응답 200 `requestCreated=false`, 현재 MANAGER에게 `SUBSTITUTE_NO_CANDIDATE` | `create` L77~90, L445~470 | C-43 (후보 0명이면 Request 없음), C-54 ③ (현재 MANAGER 알림, Product Scope 승인 `NOT_RECORDED`). 200 `requestCreated=false` 응답 형식은 G-03 계약 문장에 없음 (코드 사실) | V4-T-16 |
| V4-P04 | Request OPEN + Candidate PENDING N건은 같은 Transaction | `create` | C-44 | V4-T-15 (Mock) |
| V4-P05 | ACCEPT: 본인 ∧ 현재 EMPLOYEE ∧ PENDING ∧ Request OPEN ∧ 시작 전, 충돌 3종 재검사 → Candidate·Request ACCEPTED. `Shift.member` 불변 | `acceptCandidate` | C-46 | V4-T-08, 11, 12, 50, 52 |
| V4-P06 | REJECT: 남은 PENDING이 없으면 `CLOSED / ALL_CANDIDATES_REJECTED` | `rejectCandidate` L272~285 | C-47 | V4-T-09, 10, 51 |
| V4-P07 | APPROVE: 현재 MANAGER, Request ACCEPTED, 수락자 현재 EMPLOYEE, 재검증 후 `Shift.member = 수락자`, APPROVED. Self Approval 허용 | `approve` L232~352 | C-48 | V4-T-23~30 (Mock) |
| V4-P08 | CLOSE: 현재 MANAGER, OPEN/ACCEPTED, 시작 전 → `CLOSED / MANAGER_CLOSED`. Candidate 불변, 재탐색 없음 | `close` L360~415 | C-49 | V4-T-36~46 |
| V4-P09 | 주요 Command는 `startAt > now` (Asia/Seoul Clock). `startAt == now`는 시작된 것 | 각 Command | C-50 | V4-T-21, 25, 44, 45 |
| V4-P10 | 조회 시 OPEN/ACCEPTED ∧ `now ≥ startAt` → 응답 `CLOSED/EXPIRED` (DB 불변) | `SentSubstituteRequestResponse` L28~52 | C-45 (조회 의미, 조회 중 상태 불변). **EXPIRED 표시는 C-45에서 분리된 G03-D-19 (Expiry) `PENDING`** — 현재 표시 방식은 보존 대상이나 계약 후보 아님. **2026-10-28: G03-D-19 APPROVED(DECISION-LOG §4-2)에 따라 만료 응답의 `closedAt` = 근무 시작 시각으로 변경 (`feat/v4-expiry-closed-at`, EV-036)** | V4-T-01~05, 47, V4-N-23~26 |

### C1. 상태 전이 (현재 Java)

| 대상 | 허용 | 없음 / 금지 | 근거 |
|---|---|---|---|
| Request | `OPEN → ACCEPTED → APPROVED`, `OPEN/ACCEPTED → CLOSED` (`ALL_CANDIDATES_REJECTED`, `MANAGER_CLOSED`) | 역전이, 재OPEN, 저장된 `EXPIRED` | `SubstituteRequest.java` |
| Candidate | `PENDING → ACCEPTED`, `PENDING → REJECTED` | 그 밖 전부. 다른 후보가 수락해도 나머지 PENDING은 그대로 | `SubstituteCandidate.java`, `acceptRequest` |

상태 모델이 정식 계약인지는 C-41 DECISION_PENDING (G03-D-17). Migration에서는 값과 전이를 그대로 옮긴다.

### C2. 역할·자격 Matrix (Substitute와 연결된 자격을 구분)

| 행위 | 실행자 | 대상 자격 | 근거 | ① 근거 상태 |
|---|---|---|---|---|
| DRAFT Shift 배정 (V3) | 현재 MANAGER | 같은 근무지 **현재 구성원, MANAGER·EMPLOYEE 모두** | `ShiftService` L237 (Role 검사 없음), C-22 | VERIFIED_AS_IS |
| Pattern 등록 (V2) | 현재 MANAGER | 같은 근무지 현재 구성원, **Role 무관** | `RegularShiftPatternService` L140~170, C-14 | VERIFIED_AS_IS |
| 대타 요청 생성 | 근무 당사자 본인 (**MANAGER도 자기 근무면 가능**) | 자기 공개 근무 | V4-P01, C-42 | VERIFIED_AS_IS |
| 후보 선정 | 시스템 | **EMPLOYEE만** (MANAGER는 후보가 되지 않음) | V4-P02 L50 | VERIFIED_AS_IS |
| 수락·거절 | 후보 본인, **현재 EMPLOYEE** | — | L136 | VERIFIED_AS_IS |
| 승인 | 현재 MANAGER (본인 요청도 승인 가능) | 수락자는 현재 EMPLOYEE | L278, C-48 | VERIFIED_AS_IS |
| 종료 | 현재 MANAGER | OPEN/ACCEPTED, 시작 전 | V4-P08 | VERIFIED_AS_IS |

결과: MANAGER는 근무를 맡을 수 있고 자기 근무의 대타를 요청할 수 있지만, 다른 사람 근무의 대타 후보는 될 수 없다. 이 비대칭이 의도인지는 정책 기록이 없다 (② 기록 없음). Migration에서는 그대로 보존한다.

### C3. 재요청 Flow (현재 Java, ① VERIFIED_AS_IS)

| 상황 | 재요청 가능 여부 | 근거 |
|---|---|---|
| `ALL_CANDIDATES_REJECTED` 또는 `MANAGER_CLOSED` 이후 | 가능 (활성 요청 검사는 OPEN/ACCEPTED만) | `existsByShift_IdAndStatusIn` L153 |
| 후보 재계산 | 요청할 때마다 V4-P02로 새로 계산 | `findCandidates` |
| 이전에 거절한 후보 | **다시 후보가 되어 다시 알림을 받음** (이력 제외 조건 없음) | `findCandidates` 필터 목록 |
| 재계산 결과 후보 0명 | Request 없이 200, 관리자에게 NO_CANDIDATE 알림이 다시 감 | V4-P03 |
| 근무 시작 이후 | 불가 (SUB-012). 시작 검사(L148)가 활성 요청 검사(L153)보다 먼저 | `validateSubstituteRequest` |
| APPROVED 이후 | 원래 요청자는 불가 (근무 담당이 바뀌어 SUB-011). 새 담당자(이전 수락자)는 가능 (APPROVED는 활성 요청이 아님) | L144, L153 |
| 근무 시작 전 OPEN 요청이 이미 있는데 다시 요청 | 불가 (SUB-013) | L153 |

### C4. Domain 간 영향

| 방향 | 내용 | 근거 |
|---|---|---|
| V4 → V3 | APPROVE가 `Shift.member` 변경 | `approveRequest` |
| V3 → V4 | Shift 상태·시간이 요청 가능 여부와 EXPIRED를 결정 | V4-P01, P09, P10 |
| V4 → V2 | `requireManager`, `leftAt` 필터 | Service |
| V4 → V5 | 6개 Event → 알림 | B2 |
| V4 ↔ User 전역 | 충돌 검사는 User 기준 (다른 근무지 포함) | C-30 |

## D. 기존 테스트 현황 (요약, 행 단위는 `docs/evidence/TEST-INVENTORY.md` §3)

> **S2-6A 정정 (2026-10-25, 정적 확인):** 아래 표는 PR #5 이전(`243f77c`) 52개 기준이다. PR #5(`31ecaee`) 이후 `a148a62` 기준은 **Test Method 93개, 정적 계산 실행 사례 114회**(Parameterized·Repeated를 펼친 예상값, 실제 실행 아님 — S3 표현 정정)다 (`SubstituteRequestServiceTest` 43, `SubstituteCandidateServiceTest` 26, `SubstituteRequestConcurrencyTest` 8, `SubstituteFlowIntegrationTest` 6 (신규), `SentSubstituteRequestResponseTest` 6, `SubstituteCandidateRepositoryTest` 4). 93개의 ⑤는 이 저장소에 개별 실행 근거가 없어 `UNKNOWN`이다. 상세: TEST-INVENTORY §3-1-1·§3-2-1 (V4-T-53~93). Controller / HTTP Test는 여전히 0, 알림 Row Integration도 0.

| Test Class | 종류 · 환경 (근거) | 수 | ⑤ 실행 근거 | 검증하지 못하는 조건 |
|---|---|---|---|---|
| `SentSubstituteRequestResponseTest` | UNIT · NONE | 6 | `PASSED` @`243f77c` (로컬 Test Report XML, skipped 0) | — |
| `SubstituteRequestServiceTest` (Nested 5) | UNIT · MOCK (`MockitoExtension`) | 34 | 같음 | DB 최종 상태 (APPROVE 후 `Shift.member`), HTTP |
| `SubstituteCandidateServiceTest` | UNIT · MOCK | 6 | 같음 | 같음 |
| `SubstituteCandidateRepositoryTest` | SLICE `@DataJpaTest` · **H2** (설정 근거: `@ActiveProfiles("test")`와 `@AutoConfigureTestDatabase` 없음 → 기본 대체 동작, H2가 runtime classpath에 있음. Log 미확인) | 2 | 같음 | MySQL에서의 Query 동작 |
| `SubstituteRequestConcurrencyTest` | CONCURRENCY `@SpringBootTest` + `@ActiveProfiles("test")` · **MYSQL_TC** 9.7.2, Session RC (설정 근거: `application-test.yml` `jdbc:tc:mysql:9.7.2`, `application.yml` Hikari RC) | 4 | 같음 | 경쟁 순서 결정성, RR 대조 (SUB-DC-01) |
| Controller / HTTP | — | **0** | — | Status 분기, ErrorCode 매핑, Validation, 인증 |
| 알림 Row Integration | — | **0** | — | Event별 수신자·Type (C-54) |

- **SHA별 ⑤ 실행 상태**
  - 2차 `243f77c`: 52개 `PASSED` (개별 Report: 작성자 로컬 `NBE12-wt/develop-243f77c/backend/build/test-results/test/TEST-com.merge.backend.domain.substitute.*.xml`, 3차 저장소에 없음. EV-002)
  - 2차 `8da2bec`·3차 `cdaaa13`: CI Suite success (2차 Run `39729371810`, 3차 Run `39748913867` = EV-008). 개별 결과는 남아 있지 않음 → `UNKNOWN`
- `243f77c → 8da2bec` 사이에 Substitute Production·Test 파일 변경은 0. `cdaaa13`은 Gradle 9.3.0·Kotlin Plugin 추가로 **실행 환경이 다르다.**
- CI 실행 범위: `backend-ci.yml`이 `./gradlew check --no-daemon` 실행. `build.gradle.kts`의 Test 설정은 `useJUnitPlatform()`뿐 (Filter·Exclude 없음). Test 코드에 `@Disabled`·`@Tag` 없음 (`git grep` 0건)

## E. Kotlin Migration — DoD

Improvement(B+, Guard Lock, Membership Lifecycle)는 이 DoD에 포함하지 않는다.

| DoD | V4에서 확인할 것 | 완료 근거 |
|---|---|---|
| 1. 기존 Test | D절 93개 (정적 계산 실행 사례 114회, S2-6A 정정. 이전 표기 52개는 PR #5 이전) | Java SHA와 Kotlin SHA 양쪽에서 ⑤ `PASSED` (Test Report 보관, Test 이름 목록·실행 횟수 일치) + 기대값 변경 0 |
| 2. HTTP · JSON 동등성 | SUB-01 201/200 분기, 8개 응답 Field 이름·null 출력, 성공 `code` 문자열 | V4-N-01 (현재 Test 없음 → 전환 **전에** Java로 먼저 작성) |
| 3. Validation | `SubstituteCandidateRespondRequest` 누락·null → COM-001 (nullable + `@field:NotNull`, TECH-STACK M0-6) | V4-N-01 |
| 4. Entity · Proxy · Nullable | `closeReason`, `approvedAt`, `closedAt`, `respondedAt` nullable. Lazy 연관 (`shift`, `requesterMember`, `request`, `member`). `id` `Long?`, `!!` 0 | V4-N-02, V4-N-03 |
| 5. Transaction | `create` 안의 `findCandidates`(`readOnly`) 호출 구조, Command 원자성 | V4-N-02 (Rollback) |
| 6. Query · Lock | `findByIdForUpdate`, Candidate Query 문자열 그대로 | Diff 검토 + V4-N-03 (MySQL) |
| 7. Event 시점 | 6개 Event 발행 위치, `AFTER_COMMIT` | V4-N-04 |
| 8. Schema 동등성 | `substitute_request`·`substitute_candidate` Column·Unique·FK·ENUM 값 (Table 이름은 실제 Schema 확인) | 전환 전후 Schema 비교 기록 |
| 9. 통합 Flow | H절 1~7단계 | Flow Integration 1개 (V4-N-02 확장 가능). PR #5의 `SubstituteFlowIntegrationTest` (V4-T-88~93)가 일부를 다룬다 — 충족 여부는 실행 근거 확인 후 판단 |
| 10. 신규 동작 | 0 | PR Diff |

## F. Improvement (② 상태별)

| ID | 내용 | ② 상태 | 영향 | 필요한 승인 | Test |
|---|---|---|---|---|---|
| SUB-EV-01 | APPROVE 후 DB 재조회 Test | 해당 없음 (동작 변경 없는 Test 보강) | 없음 | V4 Owner (Task 승인) | V4-N-02 (Migration DoD로 이동) |
| IMP-02 / SUB-EV-05 | 동시 ACCEPT 결정적 재현, RC/RR 대조 (현재 동작 재현, 동작 변경 없음) | PENDING (`MIGRATION-MATRIX` IMP-02) | 동시성 검증 | DL-009, DL-010 | V4-N-05 (BLOCKED) |
| ML-SUB-STATE-01 (B+) | Candidate `INELIGIBLE`, 종료 사유 `CANDIDATE_INELIGIBLE` | **PROPOSED · 현재 Java에 없음 · 문서 파일 없음** | Enum, MySQL ENUM DDL, 알림, Frontend | 팀 + 배포·DB 담당 | V4-N-06 (작성 금지) |
| ML-CONCURRENCY-01-R | 후보 선정·ACCEPT·APPROVE의 Membership `FOR SHARE` 재확인 | **PROPOSED · 현재 Java에 없음 · 문서 파일 없음** | 기존 Command Lock 추가 (AI-RULES §2) | 팀 | V4-N-07 (작성 금지) |
| Membership Lifecycle (ML-DECISION-01) | 퇴사 시 대타 정리 | **PROPOSED · 문서 파일 없음** (G03-D-11 PENDING) | V2~V5 | 팀 | — |

## G. Delivery

| 항목 | 내용 |
|---|---|
| 선행 작업 | B3 Baseline 확정, 3차 docs PR, V4 Owner 확정, V2·V3 Entity 전환 순서 (Lombok 상호운용은 PR #1 `plugin.lombok`으로 준비됨) |
| 작업 단위 | ① Java Test 보강 PR (V4-N-01~04, 기존 기대값 변경 0) ② Migration PR (Kotlin 변환, 동작 변경 0) ③ Improvement PR (승인 후에만) |
| 담당 | V4 Owner (팀 확인 필요) |
| 완료 기준 | E절 DoD 1~10 |
| Test Evidence | 3차 `docs/evidence/TEST-EVIDENCE.md`: EV-008은 Suite 수준만. V4 개별 결과 EV는 아직 없음 |
| 남은 Blocker | 문서 체계는 D0-D docs PR 병합 전 (③ DRAFT), G-03은 참고 공유본만 있고 계약 승인은 없음 (G03-OQ-02 `PENDING`), Owner 미확정, 개별 Test 결과가 CI에 남지 않음 (Report 보관 방법 필요), H6 Harness LOCAL_ONLY |

## H. 사용자 Flow 완결성 (대타 기본 Flow)

| 단계 | 사용자 행동 | API | 결과 상태 | 다음 행동 |
|---|---|---|---|---|
| 1 | 근무 당사자가 대타 요청 | SUB-01 | Request OPEN + Candidate PENDING N, 후보 알림 / 후보 0이면 200 + 관리자 알림 | 2, 또는 (후보 0) 관리자 오프라인 대응 |
| 2 | 후보가 받은 요청 확인 | SUB-02 | — | 3 |
| 3a | 후보 수락 | SUB-03 | Candidate·Request ACCEPTED, 관리자 알림 | 4 |
| 3b | 후보 거절 (마지막이면 종료) | SUB-03 | REJECTED / `ALL_CANDIDATES_REJECTED`, 요청자·관리자 알림 | 요청자 재요청 (C3) |
| 4 | 관리자 진행 중 목록 확인 | SUB-06 | — | 5 또는 6 |
| 5 | 관리자 승인 | SUB-04 | APPROVED, `Shift.member` 변경, 요청자·수락자 알림 | 수락자 "내 근무"에 표시 (V3) |
| 6 | 관리자 종료 | SUB-08 | `MANAGER_CLOSED`, 알림 | 요청자 재요청 (C3) |
| 7 | 시작 시각 경과 | — | DB 불변, 보낸 요청에 `EXPIRED` 표시 | 종료 |
