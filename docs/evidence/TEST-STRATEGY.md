# Test 전략 — Kotlin Migration과 V3 Improvement 검증

| 축 | 상태 |
|---|---|
| 결정 단계 | **사용자 잠정 원칙** (S2-6B, 2026-10-25). 팀 리뷰 전 |
| ② 정책 | `PROPOSED` / 팀 `PENDING` |
| ③ 문서 | `DRAFT` |
| Test 실행 | 이 문서 작성 중 Test·Gradle·DB 실험 실행 0. 이 문서의 모든 Test는 ④ `SPECIFIED`, ⑤ `NOT_RUN` 또는 `UNKNOWN` |

> 이 문서는 "무엇을, 언제, 어떤 기준으로 검증하는가"를 정한다. Test 행(ID·상태)은 [`TEST-INVENTORY`](TEST-INVENTORY.md), 실제 실행 결과는 [`TEST-EVIDENCE`](TEST-EVIDENCE.md), Domain별 범위·진행 상태는 [`MIGRATION-MATRIX`](../migration/MIGRATION-MATRIX.md), Kotlin 코드 리뷰 항목은 [`KOTLIN-REVIEW-CHECKLIST`](../migration/KOTLIN-REVIEW-CHECKLIST.md). 규칙이 충돌하면 [`AI-RULES`](../ai/AI-RULES.md)를 따른다.

## 1. 원칙

1. **기대값 보존 규칙.** Java ↔ Kotlin 호출 호환성을 위한 수정(접근자·생성자 호출 문법)은 허용한다. Test의 검증 목적, 사전조건, Assertion의 의미, 기대 ErrorCode·HTTP 상태·DB 결과는 바꾸지 않는다. 의미가 바뀌는 수정은 `DECISION-LOG` `APPROVED` 행이 있을 때 Improvement PR에서만 한다 (PROCESS-CHARTER §6-2).
2. **Domain별 전환 기준.** 저장소 전체를 한 번에 바꾸지 않는다. 한 Domain(또는 더 작은 단위)을 전환할 때마다 Java 기준선과 같은 행동을 확인한다 (§2, §5).
3. **Kotlin 리팩터링 기준.** null·Optional·객체 설계 개선은 언어 전환과 다른 PR이다. 리팩터링 전후에 같은 Test를 다시 실행한다.
4. **검증 증거 기준.** Java와 Kotlin 양쪽에서 SHA, 환경, 명령, JUnit 결과, 알려진 실패를 기록한다 (AI-RULES §6 형식).
5. **같은 이름·같은 횟수는 증명이 아니다.** Test 이름 목록과 실행 횟수가 같고 모두 통과해도 "Test가 확인하는 범위 안에서 차이를 찾지 못했다"는 뜻일 뿐이다. 확인하지 않는 동작(HTTP 응답 형식, DB Schema, 확인하지 않는 실패 분기)은 §5의 별도 비교로 보강한다.
6. **Kotlin다운 코드 품질은 Test만으로 판단하지 않는다.** 동작은 Test로, 타입 안전성은 컴파일러로, 스타일·설계는 정적 분석과 리뷰로 판단한다 (§4 영역 H, KOTLIN-REVIEW-CHECKLIST).

## 2. Migration 단계와 완료 기준

멘토 피드백 반영 (2026-10-25): Production과 Test 중 무엇을 먼저 전환해도 되지만, 이 프로젝트는 **Production 먼저 → 필요한 Test 전환 → Kotlin다운 개선** 순서를 기본으로 한다. 기존 Java Test를 기준으로 쓸 수 있기 때문이다. 단계 2와 3은 Domain마다 일부 겹칠 수 있다 (예: Java record DTO 전환 시 Java Test 호출 문법 수정).

| 단계 | 하는 일 | 완료 기준 | 하지 않는 것 | MIGRATION-MATRIX 상태 칸 |
|---|---|---|---|---|
| 1. Java 기준선 | 회귀 Test 보강 (`MIGRATION` 구분 Test: V4-N-01~04, V3-N-20~32, V4-N-48 등) + Java SHA에서 전체 실행 | 보강 Test가 **현재 Java 동작 그대로** 통과. 전체 실행 결과(JUnit XML) EV 기록. 실패가 있으면 Migration 전에 해결하거나 "알려진 실패"로 명시 | Production 변경, 기대값 변경 | (선행 조건) |
| 2. Production 전환 | 그 Domain의 Production 코드를 Kotlin으로 | §6의 ② 통과 조건 | 동작 변경, 새 기능, 구조 개선 | `MIGRATION_CODE_COMPLETE`, `UNIT_TEST_VERIFIED`, `INTEGRATION_TEST_VERIFIED`, `CONTRACT_REGRESSION_VERIFIED` |
| 3. Test 전환 (필요한 것만) | Java Test 중 Kotlin으로 옮길 필요가 있는 것 | §6의 ③ 통과 조건. Java Test를 전부 옮기는 것은 필수가 아니다 | 기대값·사전조건 변경 | (같은 칸 재확인) |
| 4. Kotlin다운 개선 | null 처리, Optional 경계, 불변성, 객체 설계 | §6의 ④ 통과 조건 + KOTLIN-REVIEW-CHECKLIST | 동작 변경 | `KOTLIN_QUALITY_REVIEWED` |
| 5. Improvement | 승인된 V3 정책 (P0 → P1) | §6의 ⑤ 통과 조건. `PROPOSED_IMPROVEMENT` Test가 `IMPLEMENTED` + `PASSED` | 승인 전 정책 | `CROSS_DOMAIN_INTEGRATED`, `API_DOC_UPDATED` |

Domain 순서는 의존 방향(`user ← workplace ← shift ← substitute ← notification`, MIGRATION-MATRIX §5)과 공통 `global`(별도 Owner PR) 결정에 따른다. 이 문서는 순서를 정하지 않는다.

## 3. Test 변경 허용 범위 (단계 2·3)

| 구분 | 예 | 허용 | 기록 |
|---|---|---|---|
| 호출 문법 | `response.closedAt()` → `response.getClosedAt()`, `isSuccess()` 호출 대상 이름, 생성자 인자 순서 맞춤 | 허용 | PR 본문에 "호출 문법만 수정한 줄" 목록. Assertion 줄의 기대값 Diff 0 |
| Fixture 준비 | Kotlin 생성자 기본값 때문에 Java에서 쓰던 생성자가 없어 다른 생성자 호출 | 허용 (같은 상태를 만들 때만) | 같음 + 만들어지는 상태가 같다는 근거 |
| Mock 준비 | Kotlin final Class·non-null 인자 때문에 Stub 방식 변경 | 허용 (검증 대상 호출·횟수 불변) | 같음 |
| Assertion 의미 | 기대 ErrorCode·HTTP 상태·DB 결과·호출 횟수 변경, 확인 항목 삭제 | **금지** (Improvement PR + `APPROVED` 행에서만) | — |
| Test 삭제·`@Disabled` | — | **금지** | — |

호환성 선택지(`@JvmRecord`, `@JvmOverloads`, `@get:JvmName` 등)를 쓸지, Java Test의 호출 문법을 바꿀지는 DTO·Entity마다 KOTLIN-REVIEW-CHECKLIST §1로 판단한다. 어느 쪽이든 Test의 검증 의미와 기대값은 보존한다.

## 4. 검증 영역

| 영역 | 목적 | 환경 | 대상 | 기대값 형태 | 실패 시 함께 확인 | 증거 | 적용 단계 | 현재 |
|---|---|---|---|---|---|---|---|---|
| A. 단위·도메인 | 업무 규칙 하나, 상태 전이 하나 | UNIT (NONE·MOCK) | Entity, Service | 반환값·상태·ErrorCode | 부작용 없음 (Mock 호출 0, 이벤트 0) | JUnit XML | 1, 2, 5 | 다수 존재 (V4-T 등) |
| B. HTTP 계약 | Status, `ApiResponse` 형식, ErrorCode, Field 이름, null 출력, Validation | SLICE (WebMvc) · HTTP | Controller + `GlobalExceptionHandler` | 응답 JSON 전체 (Snapshot 비교 가능) | 잘못된 입력·필수 Field 누락 → 기존 code | JUnit XML + Snapshot 파일 | 1 (Java로 먼저), 2 | V4는 0 (V4-N-01 예정) |
| C. 보안·권한 | 인증 Filter, 권한 범위, 404/403 규칙 | API (실제 Filter + JWT) | Security, M3 선검사 | Status·code·message·`data` | 권한 확인 전 Lock·DB 변경·이벤트 0 | JUnit XML | 1 (현재 동작), 5 (M3) | 실제 Filter Test 0 (AUTH-EV-01) |
| D. 통합 시나리오 | 여러 Domain에 걸친 업무 흐름이 끝까지 일관됨 | INTEGRATION · MYSQL_TC | Service 여러 개 + DB | DB 최종 상태 (Shift 담당, Request 상태·사유, Membership, 이력, 알림 Row) | 중간 실패 시 전체 Rollback, 알림 실패가 업무에 영향 없음 | JUnit XML + 실행 Log | 5 (S3 경로마다 1개 이상) | 대타 흐름 6개 (V4-T-88~93). 퇴사·인계 흐름은 기능 없음 |
| E. DB 정합성 | 유일성, Rollback, 이력 보존, Schema | INTEGRATION · MYSQL_TC, Schema 비교 | Repository, 제약, DDL | 제약 위반 예외, Row 수, Column 정의 | H2와 MySQL 결과 차이 | JUnit XML + Schema 덤프 비교 | 1, 2 (Schema 동등성), S4, 5 | Repository Test 일부가 H2 (V4-N-03) |
| F. 동시성 | 동시 요청에서 불변식 유지, Lock 순서, Deadlock·Timeout 처리 | CONCURRENCY (반복·스레드), H6 (순서 강제) · MYSQL_TC + 격리 수준 기록 | Lock 구간 | 최종 상태 불변식 (담당자 1명, 활성 요청 ≤ 1 등) | Rollback 범위, 실패 명령의 이력·알림 0 | JUnit XML + `REPRODUCED` / `NOT_REPRODUCED` 판정 | 1, S4, 5 | RC 동시 실행 + 순서 강제 일부 존재. H6는 DL-009 전 `BLOCKED` |
| G. Kotlin·Spring 호환 | 전환 후 Spring·JPA·Jackson 동작이 같음 | INTEGRATION · MYSQL_TC, SLICE | Proxy, Entity, DTO | Rollback 발생, LAZY 로딩, 변경 감지 반영, JSON 동일 | — | JUnit XML (M0-4~6) | S4 (M0), 2 | M0-3~7 계획만 (TECH-STACK §5) |
| H. 코드 품질 | null 안전성, 불변성, 설계, 스타일 | 컴파일러, 정적 분석(도구 미정), 리뷰 | Kotlin 코드 | `!!` 0 (사유 주석 예외), 체크리스트 통과 | — | PR 리뷰 기록 (+ 도구 결과, 채택 시) | 2, 4 | 도구 미정 (IMP-07 `PENDING`) |
| I. 운영 유사 환경 | 운영 설정에서 뜨고 동작함 | Docker Image + Testcontainers MySQL, 운영 Schema 구조 (값 없음) | 전체 Application | Context 기동, 대표 API 응답, Schema `validate` | — | 실행 Log, EV | 배포 전 | CI `check` + Docker build만 |
| J. Test 품질 | Test가 실제 결함을 잡는가 | 로컬 | 핵심 불변식 Test | 일부러 깨뜨린 코드에서 Test 실패 | — | 실패 확인 기록 (EV) | 1, 5 (주요 PR) | 없음 |

## 5. Java 기준선 ↔ Kotlin 비교 계획

| 비교 대상 | 방법 | 같다고 볼 조건 | 이것만으로 증명하지 못하는 것 |
|---|---|---|---|
| Test 결과 | Java SHA와 Kotlin SHA에서 같은 명령 실행, JUnit XML의 Test Case 목록·결과 비교 (실제 실행된 사례 단위: Parameterized·Repeated 포함. 정적 계산값 394와는 실행 후 대조) | 목록 동일, 모두 통과, 알려진 실패는 양쪽에서 같은 이유 | Test가 확인하지 않는 동작 |
| HTTP 계약 | 대표 API별 응답 JSON Snapshot (성공·대표 실패·Validation 실패) | Status·`code`·`message`·Field 이름·null 출력·날짜 형식 동일 | Snapshot에 없는 API |
| DB 최종 상태 | 통합 Test의 DB 재조회 확인 (`flushAndClear` 이후) | Row·Column 값 동일 | 동시 실행 경로 |
| Schema | Hibernate가 만든 Schema 덤프를 전환 전후 비교 (Testcontainers MySQL) | Table·Column·Type·Nullable·Unique·FK·ENUM 값 동일 (V4 DoD 8) | 운영 Schema (운영 접속 금지, 배포 담당이 구조 제공) |
| 동시성 | 같은 Harness·같은 격리 수준에서 판정 비교 | 판정 동일 | `NOT_REPRODUCED`는 결함 없음이 아님 |
| 실행 환경 | JDK, OS, DB 종류·버전, Session Isolation, Profile, Gradle 버전 기록 | 기록됨 | 다른 환경으로의 일반화 |

**기준선의 현재 상태:** 3차 저장소에는 Java 기준 실행의 개별 결과가 없다. `a148a62`의 Test Method 373개(정적 계산 실행 사례 394)의 ⑤는 `UNKNOWN`이다 (EV-008은 Suite success만).

**기준선 실행 방식 (사용자 잠정 결정 2026-10-25, 팀 `PENDING`):** 로컬 실행 + JUnit XML 기록으로 계획한다. 실제 실행은 S4의 별도 승인 후. CI Artifact 보관은 후속 개선 선택지로 남긴다. 순서: ① Java 기준 Commit 지정 → ② 그 Commit에서 실행 → ③ 결과·환경 기록 → ④ 실패가 있으면 원인·재현 조건 기록 → ⑤ Kotlin 전환 후 같은 조건으로 재실행·비교. JUnit XML은 Git에 Commit하는 것을 기본으로 하지 않는다 (경로·설정 값·Test 출력이 들어 있을 수 있음). **원본 보관 위치와 민감 정보 검사 방법을 실행 전에 정하고**, TEST-EVIDENCE에는 SHA·명령·환경·통과/실패 요약·원본 위치와 보관 방법을 기록한다.

## 6. PR 종류별 통과 조건

| PR | 통과 조건 | 필수 증거 |
|---|---|---|
| ① Java Test 보강 | 추가 Test가 현재 Java 코드에서 통과. 기존 Test Diff 0. Production Diff 0 | EV (Java SHA, XML) |
| ② Production 전환 | 그 Domain + 공통 + 전체 `./gradlew check` 통과. §5 Test 결과·HTTP 계약·Schema 비교 동일. Test는 §3 허용 범위의 수정만. 동작 변경 0 | EV (Java SHA와 Kotlin SHA 양쪽), Snapshot·Schema 비교 결과, 호출 문법 수정 목록 |
| ③ Test 전환 | 전환 전후 Test Case 목록·Assertion 기대값 동일. 실행 사례 수 동일 | EV, 전후 Assertion 대응표 |
| ④ Kotlin다운 개선 | 같은 Test 전부 재통과. KOTLIN-REVIEW-CHECKLIST 통과. 호출 계약이 바뀌면(Optional → nullable 등) 그 호출자 Test 추가 | EV, 리뷰 기록 |
| ⑤ Improvement | 해당 `PROPOSED_IMPROVEMENT` Test 작성·통과. 기대값 변경은 TEST-INVENTORY §5-4와 `APPROVED` 행으로만 | EV, DECISION-LOG 행 |
| ⑥ 배포 전 검증 | §7 항목 통과 | EV, 배포 담당 확인 |

## 7. 운영 유사 환경 검증 계획 (운영 DB·Railway 접속 없음)

| 항목 | 방법 | 선행 조건 |
|---|---|---|
| 운영 설정 호환성 | **`prod` Profile 실행은 승인하지 않는다 (사용자 잠정 결정 2026-10-25, AI-RULES §7 유지).** Profile 이름보다 "그 설정이 어떤 외부 자원에 연결하는가"가 위험이다. 운영 외부 자원에 연결하지 않는 격리된 설정 검증 방법(별도 Test 구성)을 우선 검토한다. 규칙 예외가 필요하면 팀이 먼저 결정한다. 실제 운영 실행과 운영 유사 환경 Test는 구분해 기록한다 | 팀 결정 (예외가 필요할 때) |
| Docker Image 실행 | CI가 만드는 Image를 로컬 Testcontainers MySQL에 연결해 기동, 대표 API 호출 | 승인된 실험 |
| Schema 정합 | 배포 담당이 제공한 운영 Schema 구조(값 없음) 위에서 Hibernate `validate` | 배포 담당 (D-NOW-01) |
| Cookie·CORS·문서 노출 | Refresh Cookie `SameSite`·`Secure`, `CORS_ALLOWED_ORIGINS`, Swagger·H2 Console 노출 (G03-D-28) | 운영값 확인 (배포 담당) |
| 배포 후 비파괴 Smoke | 읽기 전용 확인만. 실행 주체·시점은 별도 승인 | D-NOW-01 |

## 8. Test 품질 평가 기준

- **실패 확인:** 핵심 불변식 Test(Lock, 상태 전이, 권한 선검사)는 대상 코드를 일부러 작게 깨뜨려 Test가 실패하는지 확인하고 EV로 남긴다. 특히 동시성 Test는 우연히 통과할 수 있다. 조건: 별도 로컬 Worktree에서만 한다, 바꾼 줄과 되돌린 사실을 기록한다, 그 변경은 Commit·Push하지 않는다, 공유 Branch·운영 환경에는 적용하지 않는다, 실행 전 별도 승인을 받는다.
- **실패 분기 확인 여부:** 라인 수보다 "그 실패 분기의 code를 확인하는 Test가 있는가"를 본다 (TEST-INVENTORY §5-3 방식).
- **보조 도구 (도입 미정):** JaCoCo는 확인하지 않은 영역을 찾는 보고용, PIT(Mutation)는 핵심 Service 시험 적용 후보. 둘 다 통과 조건으로 쓰지 않는다 (팀 결정 전).

## 9. TEST-INVENTORY와 TEST-EVIDENCE의 역할

| 문서 | 담는 것 | 담지 않는 것 |
|---|---|---|
| TEST-INVENTORY | Test ID, 정책 연결, 기대 결과, ④ 준비 상태, ⑤ 상태와 EV-ID 연결 | 실행 Log 내용 |
| TEST-EVIDENCE | 실제로 실행한 결과만 (Append-only): SHA, 환경, 명령, 결과, Log 위치, 판정 | 실행하지 않은 Test, 계획 |
| 이 문서 | 검증 방법·시점·통과 기준 | 개별 Test 행, 실행 결과 |

## 10. 남은 결정 (팀) — 이 문서는 정하지 않는다

| 항목 | 내용 | 관련 |
|---|---|---|
| Java 기준선 실행 방식 | 사용자 잠정 결정: (a) 로컬 실행 + JUnit XML 기록 (§5). 남은 것: 기준 Commit 지정, XML 원본 보관 위치·민감 정보 검사 방법, 팀 승인. (b) CI Artifact 보관은 후속 개선 선택지 | AI-RULES §6, EV-008 |
| 운영 설정 검증 | 사용자 잠정 결정: `prod` Profile 실행 승인 안 함 (§7). 격리된 설정 검증 방법 검토, 규칙 예외는 팀 결정 | AI-RULES §7 |
| DTO 호환 방식 | DTO별로 `@JvmRecord` 등 호환 선택지 / Java Test 호출 문법 수정 중 무엇을 쓸지의 기준 확정 (KOTLIN-REVIEW-CHECKLIST §1) | M0-6 |
| 정적 분석 도구 | ktlint/detekt 채택과 시점 | IMP-07 |
| Test 품질 도구 | JaCoCo/PIT 도입 여부 | — |
| H6 Harness | 공유·검증 지위 | DL-009, DL-010 |
| 운영 검증 책임 | 배포 담당, 운영값 확인 | D-NOW-01 |
| V4-T-74 응답 (S3) | 같은 근무지 구성원이라도 한 응답으로 묶지 않는다. 세 경우를 나눠 정한다: ① 그 요청을 볼 수 있는 사용자(요청자·후보), ② 그 요청과 관계없는 사용자, ③ 관리자 전용 작업에 대한 역할 부족. 결정 전에는 기대값 변경 대상으로 확정하지 않는다 | TEST-INVENTORY §5-4, API-CATALOG §5-5 |
| G03-D-10 (`DEFER`) | 남의 알림 읽음 403 / 404. 팀 결정 전에는 변경 대상으로 확정하지 않는다 | DECISION-LOG |
| 정책 미정 (S3·S4로 해결되지 않음) | 다른 관리자 대체 화면 공백, 한 근무에 활성 대타 요청 2개 이상인 기존 데이터의 처리 | NOTIFICATION-TYPES §3-6, §6 |
| ML-5 Lock 순서 | S4 실험 결과 전 확정하지 않음 | ML-5 |

S4 기술 실험은 "DB·Spring에서 실제로 어떻게 동작하는가"를 알려 줄 뿐, 업무 정책(무엇이 맞는 동작인가)을 대신 정하지 않는다.

## 11. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-25 | 초안 (S2-6B). 원칙 6개, Migration 5단계와 완료 기준, Test 변경 허용 범위, 검증 영역 10개, Java ↔ Kotlin 비교 계획, PR별 통과 조건, 운영 유사 환경 계획, Test 품질 기준, 문서 역할, 남은 결정. 멘토 피드백(Production 먼저, Test 전환은 필요한 것만, 이후 Kotlin다운 개선) 반영. Test 실행 0 |
| 2026-10-25 | Commit 전 보완: 394는 정적 계산 실행 사례(실제 실행 아님) 명시, 기준선 실행 방식·`prod` Profile 사용자 잠정 결정 반영, XML 보관 위치·민감 정보 검사 선행, 실패 확인의 격리·되돌림·Commit 금지 조건 |
