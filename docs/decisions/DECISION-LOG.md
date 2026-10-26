# Decision Log

> 작성 기준: 2026-10-22 (D0-B), D0-D 이관·사실 갱신 2026-10-23 (3차 `develop` `cdaaa13`). **현재 `APPROVED` 0건.**
> D0-D에서 바꾼 것은 "결정" 칸의 **적용 사실·이유·근거** 기록과 DL-014 추가뿐이다. 상태는 모두 `PENDING`으로 유지했다. GitHub PR 리뷰 Approve는 이 Log의 `APPROVED`가 아니다.
> 상태값의 5축 대응은 `docs/STATUS-AXES.md` (② 정책 승인).
> 정책 결정의 Source of Truth. 승인된 행만 `CONTRACT-BASELINE`·Test 기대값 변경의 근거가 된다.
> AI는 `PENDING` / `DISCREPANCY` 행을 추가할 수 있지만 상태를 `APPROVED` / `REJECTED`로 바꾸지 않는다.

## 기록 규칙

- 상태: `PENDING` → `APPROVED` / `REJECTED` / `DEFERRED`. 결정 칸에 **결정 내용, 승인자, 일자, 회의·PR 링크**를 적는다.
- 결정되면 `결정 후 갱신` 순서로 반영한다: Decision Log → `CONTRACT-BASELINE` → Test 기대값 → 코드 (각각 PR).
- 결정 전에는 `미결정 시 처리`를 따른다.
- 코드·명세·Test 불일치를 발견하면 `DISCREPANCY` 구분으로 `DL-` 행을 추가한다.

## 1. 3차 실행 결정 (DL-)

| ID | 질문 | 구분 | 미결정 시 처리 | 결정 시점 | 결정 주체 | 상태 | 결정 (내용 / 승인자 / 일자) |
|---|---|---|---|---|---|---|---|
| DL-001 | Cross-Workplace: 다른 근무지 공식 Shift와 겹치는 Pattern·DRAFT를 허용하는가 (A 등록부터 차단 / B 허용+경고, Publish 차단 / C 현행 유지+Publish 차단) | 신규 정책 제안 | 현행 보존 (CB-SCH-01~04). 제안: C + 선택 P1 boolean 표시 (IMP-05) | Migration 중 | 팀 | PENDING | |
| DL-002 | develop의 전역 READ COMMITTED (`application.yml`, 모든 Profile)를 유지하는가 | DISCREPANCY (Original ↔ develop, 근거 Evidence 없음) | develop 설정 보존. Kotlin 이식 기준도 RC | H6 재현 결과 후 | 팀 / H6 총괄 | PENDING | |
| DL-003 | Kotlin Gradle Plugin Version (2.3.21 / 2.4.20~21)과 그에 맞는 Gradle Wrapper Version | 기술 환경 | M0 시작 불가 → **PR #1로 적용됨. 현행 유지, 추가 변경 금지** | M0-2 전 | 공통 설정 Owner | PENDING (PR #1로 적용됨, 승인 기록 대기) | **적용 사실 (PR #1 `chore/kotlin-setup`, 2026-10-22 병합 → `develop` `cdaaa13`):** Kotlin 2.3.21 (`jvm`·`plugin.spring`·`plugin.jpa`·`plugin.lombok`), Gradle Wrapper 9.7.1 → **9.3.0**. **변경 이유 (사용자 확인, 2026-10-23):** Kotlin Migration 환경 구성 시 Kotlin과 Gradle 버전 호환성 문제가 없도록 조정해 달라는 요청에 따라, Kotlin 2.3.21의 공식 완전 지원 범위에 Gradle을 맞춤. **공식 근거:** Kotlin Gradle 호환표 — KGP 2.3.20–2.3.21의 Gradle min–max 7.6.3–9.3.0 (https://kotlinlang.org/docs/gradle-configure-project.html, 2026-10-23 확인). **Test 근거:** CI Suite success만 (EV-008). 개별 Test 결과·Build 경고 목록은 미기록. **승인자·일자:** 미기록 (사람이 기입). PR 리뷰 Approve 2건은 PR 승인이며 이 행의 결정 기록이 아님. 상세: `TECH-STACK-DECISIONS` §1-A·§1-B |
| DL-004 | Lombok 상호운용 (`kotlin("plugin.lombok")` Alpha / 순서 조정·delombok) | 기술 환경 | M0-3 시작 불가 → **PR #1로 적용됨. 현행 유지** | M0-3 전 | 공통 설정 Owner | PENDING (PR #1로 적용됨, 승인 기록 대기) | **적용 사실 (PR #1, `cdaaa13`):** `kotlin("plugin.lombok") version "2.3.21"`. 기존 Java Lombok 설정(`compileOnly`/`annotationProcessor`) 유지. **검증:** Kotlin → Java Lombok 멤버 참조 Test(M0-3 계획)는 기록 없음. 환경 검증 Test `KotlinEnvironmentTest`는 Lombok을 참조하지 않음. **승인자·일자:** 미기록 (사람이 기입) |
| DL-005 | 외부 분석 자료(G-0x, A-0x, 2차 명세 PDF) 공유 위치 | 문서 | ID 참조 기본. 검토 후 선별 공유한 자료만 `docs/references/` | 10/22 | 팀 | PENDING | **사실 (D0-E-2B, 2026-10-23, 사용자 지시):** G-03 원문을 참고 공유본으로 첫 docs PR에 포함 (`docs/references/G-03-contract-review.md`, 개인 경로 1줄만 바꿈, SHA-256은 `docs/references/README.md` §3). → G-03 **원문 접근성**은 해결. **미해결:** G-01·G-02·G-04·G-05·A-0x·2차 명세 PDF의 공유 위치, 참고 자료 공유 기준의 팀 승인, G-03 계약 후보를 Kotlin 비교 기준으로 승인할지 (G03-OQ-02). **승인자·일자:** 미기록 |
| DL-006 | `docs/` 구조, SoT 분담 (기술 상태 = Matrix, 일정 = Notion) | 문서 | 이 PR은 초안 | D0-B 리뷰 | 팀 | PENDING | |
| DL-007 | "Migration은 develop 현재 동작 보존 (정답 판정 아님)" 원칙 | 작업 규칙 | AI-RULES 초안대로 운용 | D0-B 리뷰 | 팀 | PENDING | |
| DL-008 | 10/24~25 주말 작업 여부 | 일정 | UNKNOWN. 작업 시간 추정 금지 | 10/23 | 팀 | PENDING | |
| DL-009 | T-02A/B (MySQL Evidence, 동시성 Harness)를 팀 Branch에 올리는가 | Test 인프라 | LOCAL_ONLY 유지 | 10/22~23 | 팀 / H6 총괄 | PENDING | |
| DL-010 | MySQL 동시성 검증 지위 (공식 선택 / 팀 필수 개선, 최소 SUB-EV-05) | 범위 | 선택으로 취급 | 10/22 | 팀 | PENDING | |
| DL-011 | Kotlin Request DTO Nullability 원칙 (nullable + `@field:` Validation) | 기술 규칙 | AI-RULES §5 초안 | M0-6 결과 후 | 공통 설정 Owner | PENDING | |
| DL-012 | `develop` Branch 보호 (PR 필수, CI 필수, 리뷰 1명) | 작업 규칙 | **2차 Java Repository (확인된 사실, 2026-10-22 `gh api .../rules/branches/develop`):** Ruleset `pull_request` (승인 1명), `required_status_checks` (`Backend Check`), `non_fast_forward`, `deletion`. 클래식 Branch Protection API는 404 (Ruleset 방식). **3차 Kotlin Repository:** 별도 설정 필요 | 3차 Repository 생성 시 | 저장소 관리자 | PENDING (3차 Repository 설정) | **3차 사실 (2026-10-23 `gh api repos/prgrms-be-devcourse/NBE12-14-3-Team01/rules/branches/develop`):** Ruleset `pull_request` (승인 1명), `deletion`, `non_fast_forward`. **`required_status_checks` 없음** (2차와 다름). 필수 Check 추가 여부는 미결정 |
| DL-013 | 2차 Java Repository와 3차 Kotlin Migration Repository를 분리하는가 | 작업 구조 | 아래 "팀 전달 내용" 기준으로 진행. 미확정 항목은 UNKNOWN 유지 | — | 팀 | PENDING (팀 결정 전달됨, 승인 기록 대기) | **팀 전달 내용 (B1 작업 지시, 2026-10-22):** 2차 Java Repository와 3차 Kotlin Migration Repository를 분리한다. 2차 Repository에서 Test PR(#76, #77, #78)을 보강·병합해 Java Baseline을 확정한 뒤, 새 3차 Repository에서 Kotlin Migration을 진행한다. **승인 주체·회의 일자:** 근거 문서 없음 (미기록, 사람이 기입). **새 Repository 주소:** `prgrms-be-devcourse/NBE12-14-3-Team01` (2026-10-23 확인). **최종 Java Baseline SHA:** 후보 `8da2bec` (2차 `origin/develop`, PR #76~78 병합 후). 최종 선언은 `BL-TBD` (B3: 로컬 `clean check`, 개별 Test 수 기록 전). **이관 사실 (2026-10-23 확인):** 3차 `main` = `8da2bec` (History 유지), `frontend/` 포함, LICENSE 파일 없음. 이관 방식에 대한 승인 기록은 없음 |
| DL-014 | PR #1 적용 내용 중 D0-B 조사 판정(`TECH-STACK-DECISIONS` §3)과 다른 항목을 유지하는가 | DISCREPANCY (D0-B 조사 판정 ↔ PR #1 적용, **판정 없음 — 오류로 단정하지 않음**) | 현행 유지. 이 문서 작업에서 수정·삭제하지 않음 | M0 후속 (M0-FOLLOWUP) | 공통 설정 Owner | PENDING | **적용 사실 (`cdaaa13`):** `-Xjsr305=strict` (D0-B TS-11 `NOT_NEEDED`), `kotlin-test-junit5` (TS-12 `NOT_NEEDED`), Gradle 9.7.1 → 9.3.0 (TS-17 "현행 유지" → 이유·근거는 DL-003), `backend/src/main/kotlin/KotlinEnvironmentCheck.kt`가 main Source Set에 포함 (M0 원칙 "Production 코드 변경 없음"과 다름, bootJar 포함 여부 미확인), `allOpen { Entity/MappedSuperclass/Embeddable }` 명시 (TS-03은 2.3.20+ 자동 적용으로 조사), `kotlin-reflect`·`tools.jackson.module:jackson-module-kotlin` (TS-04·06 `REQUIRED`와 일치), `.idea/codeStyles/Project.xml` (Kotlin `KOTLIN_OFFICIAL`). **후속:** `TECH-STACK-DECISIONS` §5-A M0-FOLLOWUP-01~03 |
| DL-015 | Kotlin Test의 Mock Library로 `mockito-kotlin`을 추가하는가 (MockK 대신) | 기술 환경 | 추가하지 않으면 Kotlin Test는 Mockito + 직접 작성한 Helper로 작성 | V4 Test Kotlin 전환 전 | 공통 설정 Owner | PENDING | **제안 내용 (Branch `chore/mockito-kotlin`, 기준 `a148a62`):** `testImplementation("org.mockito.kotlin:mockito-kotlin:6.4.0")`. Mock 동작은 Mockito와 같고, MockK는 기존 `given`/`verify` 전면 재작성과 동작 차이로 Test 의도 유지 위험이 커서 미채택. **근거:** `runtimeClasspath` 차이 없음, `testRuntimeClasspath`에 `mockito-kotlin`·`mockito-core` 5.23.0(기존과 같음)·`kotlin-reflect`(2.3.21로 해석)만 추가, 전후 `./gradlew clean check` 394/394 PASS·Test 이름 동일 (로컬). 상세 `TECH-STACK-DECISIONS` §1-C. **요청자:** V4 Owner (2026-10-26). **승인자·일자:** 미기록 (사람이 기입) |

## 2. G-03 / G-05 결정 후보 (외부 원문은 ID로 참조)

미결정 시 처리는 모두 "현행 보존 (Migration 비차단)"이다. 상태는 모두 `PENDING`.
G-03 원문은 참고 공유본 `docs/references/G-03-contract-review.md`에 있다 (승인된 결정 아님). G-05 원문은 저장소에 없다 (DL-005). `D-NOW-01~11`은 G-05 §0.3 "사람이 먼저 정할 것" 목록에서 온 실행 결정 후보다 (`docs/README.md` §5-1). Membership Lifecycle 관련 구체안(ML-DECISION-01, ML-SUB-STATE-01, ML-CONCURRENCY-01-R)은 ② `PROPOSED`인 과거 제안이며, 원문을 `docs/references/ml/`에 보존했다 (현행 계약 아님). 지금 검토 중인 정책 카드는 §3에 연결한다. 아래 G03-D-11~16·26·27의 상태를 바꾸지 않는다.

| ID | 질문 (요약) | 시점 (G-05 제안) | 결정 주체 | 상태 |
|---|---|---|---|---|
| G03-D-01 | 성공 응답 `code` 규칙 | DEFER | COMMON | PENDING |
| G03-D-02 | Pattern 권한 실패 Error Contract | DEFER | COMMON | PENDING |
| G03-D-03 | ErrorCode Prefix / 400·409 | DEFER | COMMON | PENDING |
| G03-D-04 | 다른 Workplace / User Resource 접근 시 노출 정책 | DEFER | COMMON | PENDING |
| G03-D-05 | Warning + confirm 응답 계약 | DEFER | COMMON | PENDING |
| G03-D-06 | 다른 User Resource 403 / 404 | DEFER | COMMON | PENDING |
| G03-D-07 | Unavailable ErrorCode 형식 / 메시지 | DEFER | COMMON | PENDING |
| G03-D-08 | Unavailable Success code / 정렬 / DELETE 응답 | DEFER | COMMON | PENDING |
| G03-D-09 | Substitute Error Contract | DEFER | COMMON | PENDING |
| G03-D-10 | 남의 알림 403 / 404, Success code | DEFER | COMMON | PENDING |
| G03-D-11 | Membership 종료를 3차 Scope로 채택 (H2) | DECIDE_NOW | 팀 | PENDING |
| G03-D-12 | 종료 채택 시 최소 범위 | D-11 종속 | 팀 | PENDING |
| G03-D-13 | 재가입 방식 | D-11 종속 | 팀 | PENDING |
| G03-D-14 | 종료 시 Pattern 처리 | D-11 종속 | 팀 | PENDING |
| G03-D-15 | Substitute 관련 Membership Lifecycle | D-11 종속 | 팀 | PENDING |
| G03-D-16 | 종료 Membership의 알림 노출 / 수신 | D-11 종속 | 팀 | PENDING |
| G03-D-17 | Request 종료 상태 / 어휘 | DEFER | 팀 | PENDING |
| G03-D-18 | 철회 / 관리자 거절 / 재OPEN 흐름과 알림 | DEFER | 팀 | PENDING |
| G03-D-19 | Expiry | DEFER | 팀 | PENDING |
| G03-D-20 | Expiry 도입 시 만료 알림 | D-19 종속 | 팀 | PENDING |
| G03-D-21 | ACCEPT / APPROVE의 Unavailable 충돌 처리 | DECIDE_AFTER_EVIDENCE | 팀 | PENDING |
| G03-D-22 | 알림 유실 허용 수준과 실패 관측 (H5 연관) | DECIDE_AFTER_EVIDENCE | 팀 | PENDING |
| G03-D-23 | 서버 Logout 채택 (H1) | DECIDE_NOW | 팀 | PENDING |
| G03-D-24 | 다중 기기 동시 Refresh | DEFER | 팀 | PENDING |
| G03-D-25 | Pattern Canonical API Method (현행 PUT) | DECIDE_NOW (문서 기준) | 팀 | PENDING |
| G03-D-26 | `CANCELLED` 장기 의미 | DEFER | 팀 | PENDING |
| G03-D-27 | PUBLISHED 이후 정정 범위 | DEFER | 팀 | PENDING |
| G03-D-28 | 운영 Swagger / API Docs / H2 Console 노출 범위 | DECIDE_NOW (배포 전) | 팀 | PENDING |
| G03-OQ-01 | `/workplaces/**` 무토큰 결과의 Kotlin 처리 (CB-SEC-01) | DECIDE_AFTER_EVIDENCE (AUTH-EV-01) | 사람 | PENDING |
| G03-OQ-02 | REVIEW_ACCEPTED를 Kotlin 비교 기준으로 승인 | DECIDE_NOW | 사람 | PENDING |
| G03-OQ-03 | MERGE_CANDIDATE (403 / 404, 성공 code) 일괄 결정 | DEFER | COMMON | PENDING |
| D-NOW-01 | Integration Owner(공통 설정 Owner) / 배포 / H8 담당, H6 총괄 확정 | DECIDE_NOW | 팀 | PENDING |
| D-NOW-02 | 기준 Branch (Original만 / develop 병행) — DL-013의 팀 전달 내용(최종 Java Baseline `BL-TBD`를 Kotlin 출발점으로 사용)으로 질문 범위가 바뀜. 최종 처리는 사람이 기입 | DECIDE_NOW | 팀 | PENDING |
| D-NOW-03 | MySQL Test Infrastructure 사용 — develop에 PR #75로 반영됨 (사후 확인 필요) | DECIDE_NOW | 팀 | PENDING |
| D-NOW-04 | Java 선수정 기준 (G-05 §7.3) | DECIDE_NOW / AFTER_EVIDENCE | 팀 | PENDING |
| D-NOW-05 | H1 / H2 / H5 / H7 이번 주 채택 범위 | DECIDE_NOW | 팀 | PENDING |
| D-NOW-06 | Domain Minimum Regression Set (MIGRATION-MATRIX §3) | DECIDE_NOW | 팀 | PENDING |
| D-NOW-07 | G-04 Test Purpose 제안 3건 | DECIDE_NOW | 팀 | PENDING |
| D-NOW-08 | Harness Owner 10건 | DECIDE_NOW / DEFER | 팀 | PENDING |
| D-NOW-09 | AUTH-EV-02 / 03 Runtime Priority | DECIDE_NOW | 팀 | PENDING |
| D-NOW-10 | Taxonomy 승인 (G-03 §9) | DEFER | 사람 | PENDING |
| D-NOW-11 | 문서 Backlog (G02-DOC-BL-01, G03-DOC-BL-01~06) | DEFER | 사람 | PENDING |

## 3. 정책 카드 연결

정책 카드(`docs/decisions/proposals/`)가 있는 질문을 연결한다. 위 §1·§2 행의 상태는 바꾸지 않는다.
카드의 ② `PROPOSED`와 사용자 잠정 결정은 팀 승인이 아니다. 팀이 승인하면 사람이 해당 §1·§2 행을 바꾸고 결정 내용·승인자·일자·근거 링크를 적는다 (`docs/decisions/PROCESS-CHARTER.md` §3-2).

| 카드 | 연결 ID | 카드 상태 | 이 Log의 상태 | 비고 |
|---|---|---|---|---|
| [`ML-H1`](proposals/ML-H1-membership-history.md) Membership 이력 보존과 재가입 범위 | G03-D-13 (주), G03-D-11 (전제) | 사용자 잠정 결정 / ② `PROPOSED` / ③ `DRAFT` | G03-D-11 `PENDING`, G03-D-13 `PENDING` | 재가입 구현 범위는 미결정 (ML-H2). 신규 ErrorCode 후보는 G03-D-03과 함께 확인 |
| [`G03-D-19`](proposals/G03-D-19-expiry.md) 대타 요청 만료 표현 | G03-D-19 (주), G03-D-20·26·27 (연결) | 제안 / ② `PROPOSED` / ③ `DRAFT`. 사용자 잠정 결정 확인 전 | G03-D-19 `PENDING` | 추천안 B1 (`closedAt = startAt`)은 Test 기대값 변경을 포함 |
