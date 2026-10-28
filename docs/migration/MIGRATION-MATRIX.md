# Domain Migration / Regression Matrix

> 작성 기준: 2차 `origin/develop` `243f77c`, 2026-10-22 (D0-B). D0-D (2026-10-23): §2 PR·Kotlin 상태를 3차 `develop` `cdaaa13` 기준으로 갱신. 상태: **초안**.
> Domain별 상세(API, 정책, Test, DoD)는 `docs/delivery/<Domain>.md`, Test 행 단위는 `docs/evidence/TEST-INVENTORY.md`.
> 이 문서는 기술 진행 상태의 Source of Truth다. 일정·담당은 Notion Delivery Board가 SoT이고, Board 행은 이 문서의 행 ID로 연결한다.
> 검증 상태 칸은 **통과한 Run 링크가 있을 때만** 채운다. Test가 존재한다는 것만으로는 채우지 않는다.
> 2026-10-27 (V1): §2 V1 행을 develop `6ae2727` 기준으로 갱신했다 (근거 TEST-EVIDENCE EV-024~029). 다른 행과 아래 snapshot 설명은 그대로 둔다.
> 2026-10-26 (V5-J2): §3 V5 보강 칸, §4 공통 행 R1 분리, §4 아래에 Kotlin 전환 관문 문서 위치를 적었다.
> S2-6B (2026-10-25): §3에 `a148a62` 정적 집계와 PASS 조건 보완을 추가했다. §2 상태 칸과 Migration 단계의 대응은 [`TEST-STRATEGY`](../evidence/TEST-STRATEGY.md) §2.

## 1. Domain 범위 (실제 Package 기준)

| V | Domain | Package / Class | Owner (초안, 팀 확인 필요 — D-NOW-01) |
|---|---|---|---|
| V1 | Auth / Security | `domain/user/**`, `global/security/**`, `global/rq/**` | 김덕우 |
| V2 | Workplace / Member / Pattern | `domain/workplace/**`, **`domain/shift/**/RegularShiftPattern*`** | 황희리 |
| V3 | Schedule / Shift / Unavailable | `domain/shift/**/Schedule*`, `Shift*`, `UnavailableTime*`, `domain/shift/event/**` | 박세민 |
| V4 | Substitute | `domain/substitute/**` | 최세현 |
| V5 | Notification / Event | `domain/notification/**` | 백혜승 |
| 공통 | Global | `global/config`, `global/dto`, `global/entity`, `global/exception`, `global/util`, `standard` | 공통 설정 Owner (미지정 → **임시 담당 백혜승**, 재배정 가능, 팀 확인 전 — [DOMAIN-DEVELOPMENT-GUIDE](../delivery/DOMAIN-DEVELOPMENT-GUIDE.md) §5-4) |

주의: Pattern(V2)은 V3와 같은 `domain/shift` Package에 있다. `ScheduleService`(V3)가 `RegularShiftPattern`을 읽는다.

## 2. 진행 상태

> 아래 표는 2026-10-23 `cdaaa13`의 역사적 snapshot이다. 현재 Kotlin 전환0/현재 Blocker로 해석하지 않는다. 2026-10-26 확인된 develop `9959077`에는 Workplace·V4 Kotlin 변경과 mockito-kotlin이 포함되어 있다. J2 PR16/J3 PR21 및 J4 로컬 d9f193b는 각각의 Branch/실행 SHA와 develop 반영 여부를 나누어 기록한다. 이 문서 보정은 미검증 Kotlin 품질/통합 상태를 PASS로 채우지 않는다.

Kotlin 상태 근거 (2026-10-23 확인): 3차 원격 Branch는 `develop`·`main`뿐. `develop` `cdaaa13`의 `.kt`는 환경 검증 파일 2개(`KotlinEnvironmentCheck.kt`, `KotlinEnvironmentTest.kt`, PR #1)이며 **Domain 코드의 Kotlin 전환은 0**. 담당자 보고는 받지 않았다 → 담당자 보고 전까지 `UNKNOWN`으로 표시한다.
2차 Java 저장소의 Test PR #76·#77·#78은 모두 2026-10-22 병합됐다 (2차 `origin/develop` `8da2bec` = 3차 `main`).

| V | Branch / PR | Kotlin (Git) | Kotlin (담당자 보고) | MIGRATION_CODE_COMPLETE | KOTLIN_QUALITY_REVIEWED | UNIT_TEST_VERIFIED | INTEGRATION_TEST_VERIFIED | CONTRACT_REGRESSION_VERIFIED | CROSS_DOMAIN_INTEGRATED | API_DOC_UPDATED | Blocker |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 공통 (M0) | 3차 PR #1 `chore/kotlin-setup` (MERGED → `cdaaa13`) | 환경 검증 파일 2개 | UNKNOWN | — | — | — | — | — | — | — | Owner 미지정 (D-NOW-01), DL-003·004·014 승인 기록, M0-FOLLOWUP-01~03 (`TECH-STACK-DECISIONS` §5-A) |
| V1 | 3차 PR #19 `refactor/v1-auth-kotlin`, #26 `fix/rq-actor-id-non-null`, #22 `test/v1-auth-hardening` (모두 MERGED), #46 `test/v1-auth-kotlin` (V1 Test 7개 Kotlin 전환, MERGED). Improvement: Issue #62 `feat/auth-server-logout` (AUTH-1 서버 로그아웃 Backend, PR 전). 이전: 2차 PR #76 `test/auth-tests` | V1 Production 16개 전부 `.kt` (`domain/user/**`, `global/security/**`, `global/rq/**`, develop `6ae2727`). V1 Test 7개 전부 `.kt` (#46, EV-031) | 전환 완료 (2026-10-27) | ✅ EV-024·EV-026, develop Run `40431452631` | — (Step 4 전) | ✅ develop Run `40431452631` (Suite), 로컬 개별 438 PASS (EV-029) | ✅ develop Run `40431452631` (Suite), 로컬 개별 438 PASS (EV-029) | — (로컬 비교 EV-025·EV-027만, CI Run 없음) | — | — | 없음. 남은 것: Step 4, AUTH-1 Backend PR 리뷰·머지 (구현·로컬 검증 EV-032, 512/512), AUTH-1 Frontend (별도 Issue), 배포 전 운영 CORS Origin·쿠키 SameSite·비밀키 길이 확인 (배포 담당) |
| V2 | 2차 PR #78 `test/workplace-tests` (Java Test, MERGED) | 없음 | UNKNOWN | — | — | — | — | — | — | — | M0 |
| V3 | 2차 PR #77 `test/schedule-state-consistency` (Java Test, MERGED) | 없음 | UNKNOWN | — | — | — | — | — | — | — | M0, Lock 구간은 H6 결과 (D-NOW-04) |
| V4 | 없음 (Delivery Sheet 시범: `docs/delivery/V4-substitute.md`) | 없음 | UNKNOWN | — | — | — | — | — | — | — | M0, Lock 구간은 H6 결과 (D-NOW-04) |
| V5 | 없음 | 없음 | UNKNOWN | — | — | — | — | — | — | — | M0 |

Java 실행 결과 (모든 V 공통): D0-B 조사 당시 Java Snapshot `243f77c`에서 266 Test PASS — EV-002, EV-003. **최종 Java Baseline(`BL-TBD`)이 아니다.** PR #76·#77·#78은 병합됐고 후보 SHA는 `8da2bec`이지만, `8da2bec`(2차 CI Run `39729371810`)와 3차 `cdaaa13`(EV-008)에는 **CI Suite success만** 있고 개별 Test 결과·Test 수 기록이 없다. 최종 Baseline(`BL-TBD`)은 B3 검증 후 선언한다 (DL-013). 이것은 **Java** 검증이며 Kotlin 상태 칸에 쓰지 않는다.

## 3. Domain별 필수 Regression Test (재사용할 기존 Test)

실행 결과: EV-002 (D0-B 조사 당시 Snapshot `243f77c`, MySQL 9.7.2 RC, 266/266 PASS). 수는 Class별 실행 수. **아래 수는 `243f77c` 기준이며 PR #76~78이 추가한 Test(`backend/src/test` 11개 파일 변경)는 포함하지 않는다.** 최종 Java Baseline(`BL-TBD`) 확정 후 다시 갱신한다.

| V | 기존 Test (실행 수) | 합계 | 보존 계약 (G-03 C-ID, V 배정은 `docs/references/README.md` §5) | 보강 필요 (G-05 §9 제안, 승인 전) |
|---|---|---|---|---|
| V1 | `UserServiceTest` (11), `RqTest` (5) | 16 | C-01~C-07 | 실제 Security Filter + JWT HTTP Test 0건 → AUTH-EV-01. PR #76이 `CustomAuthenticationFilterTest`, `ApiV1UserControllerTest` 추가, `UserServiceTest` 보강 (병합됨, 내용·실행 수 미기록) |
| V2 | `RegularShiftPatternControllerTest` (45), `WorkplaceServiceTest` (6), `WorkplaceMemberServiceTest` (1) | 52 | C-08~C-17 | Workplace Join 성공/실패, DB Unique 실존 (PR #78이 Workplace Controller·Repository·Security·Integration Test 추가, 병합됨, 실행 수 미기록) |
| V3 | `UnavailableTimeServiceTest` (37), `ScheduleServiceTest` (23), `ShiftServiceTest` (14), `ScheduleControllerTest` (5), `UnavailableTimeRepositoryTest` (5, **H2에서 실행**), `ScheduleTest` (4), `ShiftScheduleConcurrencyTest` (4), `UnavailableTimeConcurrencyTest` (3) | 95 | C-18~C-39 | Publish 성공/실패 DB 최종 상태 (현재 Mock), Shift CREATE/UPDATE 성공, Official Shift Query Repository Test. PR #77이 `ScheduleRollbackIntegrationTest`, `ShiftModifyIntegrationTest` 추가 (병합됨, 실행 수 미기록) |
| V4 | `SubstituteRequestServiceTest` (Approve 8, Close 11, Create 8, List 5, SentRequests 2), `SentSubstituteRequestResponseTest` (6), `SubstituteCandidateServiceTest` (6), `SubstituteRequestConcurrencyTest` (4), `SubstituteCandidateRepositoryTest` (2) | 52 | C-40~C-50 | **APPROVE 후 `Shift.member` DB 재조회** (C-48, 현재 Mock만), Candidate Filter 실제 DB |
| V5 | `NotificationServiceTest` (14), `NotificationTest` (3), `SchedulePublishedEventListenerIntegrationTest` (1), `NotificationReadConcurrencyTest` (1) | 19 | C-51~C-56 | 알림 Row 검증 0 (V5 Package 기준) → Publish / Substitute 알림 Row. 단 V3 `ShiftScheduleConcurrencyTest.publishAndModifyKeepAssigneeAndNotificationConsistent`가 근무표 공개 알림 Row 수를 일부 확인한다 (V5-J3에서 중복 작성 주의). **HTTP 계약: V5-N-21~30 (2026-10-26, Test PR #16 Draft·`develop` 미반영, EV-017~020)** |
| 공통 | `TimeRangeUtilsTest` (15), `GlobalExceptionHandlerTest` (7), `ApiResponseTest` (4), `UtTest` (3), `BaseEntityAuditingTest` (2), `BackendApplicationTests` (1) | 32 | 응답 형식, 시간 Util | — |
| **합계 (Snapshot `243f77c`)** | | **266** | | |

**`a148a62` 정적 집계 (S2-6B, 2026-10-25, Test 실행 아님):** 위 표는 `243f77c` 기준이라 PR #76~78과 3차 PR #5가 추가한 Test가 빠져 있다. 아래는 `@Test`·`@ParameterizedTest`·`@RepeatedTest`가 붙은 Method 수(Domain 배정은 §1 Package 기준)다. ⑤는 이 저장소에 개별 실행 근거가 없어 전부 `UNKNOWN`이다. 최종 Java Baseline(`BL-TBD`)은 기준선 실행(TEST-STRATEGY §5) 후 확정한다.

| V | Test Method | 파일 | 정적 계산 실행 사례 (Parameterized 입력 수·Repeated 횟수로 펼친 예상값, 실제 실행 아님) | 비고 |
|---|---|---|---|---|
| V1 | 26 | 4 | 26 | `UserServiceTest` 12, `ApiV1UserControllerTest` 6, `RqTest` 5, `CustomAuthenticationFilterTest` 3 |
| V2 | 98 | 7 | 98 | Pattern 45 포함 |
| V3 | 104 | 10 | 104 | |
| V4 | 93 | 6 | 114 | 상세 TEST-INVENTORY §3-1-1 |
| V5 | 19 | 4 | 19 | |
| 공통 | 33 | 7 | 33 | `KotlinEnvironmentTest` 1 포함 |
| **합계** | **373** | 38 | **394** | `@Test` 366 + `@ParameterizedTest` 3 (8건) + `@RepeatedTest` 4 (20회). 실제 실행은 아래 S4-E1 |

**Java 기준선 실행 (S4-E1, 2026-10-25, `LOCAL_ONLY`):** `a148a62`에서 **394 실행 / 394 PASS** 2회 (EV-010, EV-011), `check` (Checkstyle) 통과 (EV-012). 환경: macOS + Oracle GraalVM 25 + Testcontainers MySQL 9.7.2 (EV-009). 정적 계산 394 = 실제 실행 394.
- 이것은 **Java 기준선의 실행 근거**다. Kotlin Migration 완료, 통합 검증 완료, Migration 안전성 증명이 **아니다**. 위 §2 표의 검증 상태 칸(`UNIT_TEST_VERIFIED` 등)은 Kotlin 전환 PR의 근거이므로 채우지 않는다.
- 최종 Java Baseline(`BL-TBD`)으로 선언할지는 팀이 정한다. CI(ubuntu, Temurin 25) 결과가 아니다.
- DB 사용 분포: DB 없음 258 Method / MySQL Testcontainers 106 / H2 9. 기존 Test가 보호하는 범위와 공백은 [`TEST-GAP-ANALYSIS`](../evidence/TEST-GAP-ANALYSIS.md).

Kotlin 전환 PR의 최소 PASS 조건 (제안): 해당 V 행의 기존 Test 전부 + 공통 행 + 전체 `./gradlew check` 통과. Test 기대값 변경 0.

S2-6B 보완 (2026-10-25, 제안): ① Java ↔ Kotlin 호출 호환성을 위한 Test **호출 문법** 수정은 허용하되 Assertion 의미·기대 ErrorCode·HTTP 상태·DB 결과는 바꾸지 않는다 (호출 문법 수정 줄 목록을 PR에 첨부). ② Java SHA와 Kotlin SHA 양쪽의 실행 근거(EV)가 있어야 한다. ③ Test Case 목록·실행 사례 수가 같아도 그것만으로 동작 보존을 증명했다고 쓰지 않고, HTTP 계약 Snapshot과 Schema 비교를 함께 본다. 상세 [`TEST-STRATEGY`](../evidence/TEST-STRATEGY.md) §3·§5·§6, 리뷰 항목 [`KOTLIN-REVIEW-CHECKLIST`](KOTLIN-REVIEW-CHECKLIST.md).

## 4. Domain별 기술 선행조건과 손대지 않을 구간

| V | 기술 선행조건 (`TECH-STACK-DECISIONS` §3의 `TS-` ID) | 보존 구간 (Migration에서 변경 금지) | 관련 Decision |
|---|---|---|---|
| V1 | M0 완료, TS-08 (Lombok) | `SecurityConfig` Matcher 목록 그대로 (G03-OQ-01), Cookie·JWT 설정 | G03-D-23, G03-D-24, G03-D-28, G03-OQ-01 |
| V2 | M0 완료, TS-03 (JPA), TS-06 (Jackson) | Unique (`workplace_id,user_id`, `invite_code`), `leftAt` 처리 현행 | G03-D-02, G03-D-11~14, G03-D-25 |
| V3 | M0 완료, TS-03, TS-06 | `@Lock` Query, Publish 충돌 검사 순서, Cross-Workplace 현행 동작 (DL-001) | G03-D-03~08, G03-D-26, G03-D-27, DL-001, DL-002 |
| V4 | M0 완료, TS-03, TS-06 | `@Lock` Query, 상태 전이, Candidate 재검증 | G03-D-09, G03-D-15, G03-D-17, G03-D-19, G03-D-21 |
| V5 | M0 완료 | `@TransactionalEventListener` phase, 알림 생성 시점 | G03-D-10, G03-D-16, G03-D-18, G03-D-20, G03-D-22 |
| 공통 | M0 완료 | `ApiResponse` 형식, `ErrorCode` 인터페이스, `GlobalExceptionHandler` 매핑, `BaseEntity` Auditing. **단 R1(Controller 없는 경로가 마지막 `Exception` 처리로 가서 500 `COM-500`, MockMvc 관찰)은 보존할 계약이 아니다** — 전환에서 일부러 바꾸지도, Test로 고정하지도 않고, 처리 방식은 별도 Improvement 결정으로 다룬다 (TEST-GAP-ANALYSIS §4-1 R1) | G03-D-01 |

**Kotlin 전환 관문 문서 위치 (역할 구분, 2026-10-26):** 이 §4는 **기술 선행조건(TS-ID)과 보존 구간**만 다룬다. Domain별 Kotlin 전환을 시작해도 되는지의 **Test 선행조건(관문)**은 [`DOMAIN-DEVELOPMENT-GUIDE`](../delivery/DOMAIN-DEVELOPMENT-GUIDE.md) §7-3에 있고 (예: V5는 V5-K1의 선행 = V5-J5(J2~J4 + 기존 394 실행 기록), E2·E3 결과), 실제 진행 상태는 이 문서 §2에 기록한다. 관문을 여기에 새로 만들지 않는다. 관문 판단 때는 §7-3에 적힌 **모든** 선행조건을 확인한다 (일부만 충족하면 준비 완료가 아님).

## 5. 코드 의존 관계 (develop `243f77c`, main, `import` 기준)

| From → To | import 문 수 |
|---|---|
| workplace (controller/entity/repository/service) → user (entity/service) | 5 |
| shift → workplace (entity/repository/service/exception) | 19 |
| shift → user (entity/repository/service) | 4 |
| substitute → shift (entity/repository/exception) | 15 |
| substitute → workplace | 13 |
| substitute → user (service) | 2 |
| notification → substitute.event | 6 |
| notification → shift.event | 1 |
| notification → workplace (entity/repository) | 3 |
| `global/security` → user (`UserService`, `UserErrorCode`) | 3 |
| `global/config/InitData` → user, workplace | 7 |
| 모든 Domain → `global` (`BaseEntity`, `ApiResponse`, `ErrorCode`, `Rq` 등) | 다수 |

의존 방향: `user ← workplace ← shift(V2 Pattern + V3) ← substitute ← notification`, 그리고 `global ↔ user/workplace` 순환.

전환 순서에 주는 의미:
- 5명이 동시에 전환하면 Kotlin 코드가 아직 Java인 다른 Domain의 Lombok Class를 호출한다 → `kotlin("plugin.lombok")` (TS-08) 또는 순서 조정이 필요하다.
- 공통 `global`의 `BaseEntity` / `ApiResponse` / `ErrorCode`를 언제 누가 바꿀지 정해야 한다. 공통 Owner의 별도 PR로 하고, 그 전까지 Java로 둔다 (제안).
- V3·V4의 Lock 구간은 H6 재현 결과 전에는 의미 변경 없이 옮기기만 한다.

## 6. 개선 후보 등록부 (D3, 승인 전)

| ID | 후보 | 구분 | 사용자 가치 | 영향 Domain | 구현 부담 | Test 부담 | 선행조건 | 승인 |
|---|---|---|---|---|---|---|---|---|
| IMP-01 | 실제 Security Filter 경계 검증 (AUTH-EV-01) | 안전 위험 (검증 후 필수 가능) | 무토큰 접근 차단 확인 | V1 | 작음 (Test) | 중간 | M0 무관, PR #76 확인 | PENDING |
| IMP-02 | 동시 ACCEPT 재현 (SUB-EV-05) RR / RC 대조 | 안전 위험 | 대타 중복 확정 방지 | V4 | 작음 (Harness 있음, LOCAL_ONLY) | 중간 | T-02A/B 공유 승인 | PENDING |
| IMP-03 | 서버 Logout (H1) | 팀 선택 | 보안 | V1 | 중간 | 중간 | G03-D-23 | PENDING |
| IMP-04 | Membership 종료 최소안 (H2). 구체안 ML-DECISION-01·ML-SUB-STATE-01·ML-CONCURRENCY-01-R은 ② `PROPOSED`, 팀 문서 미작성 | 팀 선택 | 퇴사자 처리 | V2~V5 | 큼 | 큼 | G03-D-11 | PENDING |
| IMP-05 | DRAFT 조회에 다른 근무지 충돌 boolean 표시 | 팀 선택 (P1 후보) | 관리자 사전 인지 | V3 | 중간 | 중간 | DL-001 | PENDING |
| IMP-06 | Outbox / Retry (H5) | 팀 선택 (비권장) | 알림 유실 감소 | V5 | 큼 | 큼 | G03-D-22 | PENDING |
| IMP-07 | Kotlin 컨벤션 도구 (ktlint/detekt) | 공식 MUST 지원 | 코드 품질 | 전체 | 작음 | 작음 | M0 | PENDING |

구분: 공식 MUST / 기존 승인 P0 (현재 0건) / 안전 위험 (검증 후 필수 가능) / 팀 선택.
