# Decision Log

> 작성 기준: 2026-10-22 (D0-B), D0-D 이관·사실 갱신 2026-10-23 (3차 `develop` `cdaaa13`). **현재 정책 방향 채택 13건: §2 및 §4 (2026-10-26 사용자 전달 승인 기록).**
> D0-D에서 바꾼 것은 "결정" 칸의 **적용 사실·이유·근거** 기록과 DL-014 추가뿐이다. 상태는 모두 `PENDING`으로 유지했다. GitHub PR 리뷰 Approve는 이 Log의 `APPROVED`가 아니다.
> 상태값의 5축 대응은 `docs/STATUS-AXES.md` (② 정책 승인).
> 정책 결정의 Source of Truth. 승인된 행만 `CONTRACT-BASELINE`·Test 기대값 변경의 근거가 된다.
> AI의 독립적인 승인 판단은 금지한다. 이번 §4는 사용자가 구두 승인을 전달하고 기록을 지시한 내용의 대필이며, 승인 범위를 AI가 확대하지 않는다.

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
| DL-004 | Lombok 상호운용 (`kotlin("plugin.lombok")` Alpha / 순서 조정·delombok) | 기술 환경 | M0-3 시작 불가 → **PR #1로 적용됨. 현행 유지** | M0-3 전 | 공통 설정 Owner | PENDING (PR #1로 적용됨, 승인 기록 대기) | **적용 사실 (PR #1, `cdaaa13`):** `kotlin("plugin.lombok") version "2.3.21"`. 기존 Java Lombok 설정(`compileOnly`/`annotationProcessor`) 유지. **검증:** Kotlin → Java Lombok 멤버 참조 Test(M0-3 계획)는 기록 없음. 환경 검증 Test `KotlinEnvironmentTest`는 Lombok을 참조하지 않음. **제거 사실 (2026-10-28, PR #81):** PR #77·#79로 Java 소스가 0개가 되어 Kotlin이 참조할 Java Lombok 멤버가 없어졌으므로 `kotlin("plugin.lombok")`와 Lombok 의존성 4개를 제거했다 (develop `92dcdfa` 대비 Test 515/515 이름·결과 동일, 근거 TECH-STACK-DECISIONS §1). 실행 이력이며 상태·승인 칸은 바꾸지 않는다. **승인자·일자:** 미기록 (사람이 기입) |
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
| DL-016 | API-LV-02의 공식 근무 충돌 HTTP와 Context 실패 세분화 | DISCREPANCY / 기술 계약 미확정 | 기존 SFT-009·010의 400 유지, 퇴사 POST는 연동 Bean 부재로 비활성. 임의의 새 오류 번호·정책을 만들지 않음 | API-LV-02 활성화 전 | V2 / V3 / 공통 / Frontend Owner | PENDING | **확인 사실 (Issue #69, 2026-10-29, 기준 9b35b18):** API-CATALOG §3-2는 공식 충돌을 FORBIDDEN_CONFLICT 409로 제시하지만 재사용 대상 V3 code는 400이다. 같은 절은 기존 SFT·SCH code 재사용 여부를 S2-4로 남겨 두었다. G03-D-05 §5-4의 잘못된 Context 400 / 구형 Context 409 세분화도 미확정이다. 현재 내부 Service의 원본 검증 실패는 새 미리보기를 요구하는 WPL-010으로 처리하나 활성화 전 적용 계약을 확정해야 한다. 상세 및 #92·Guard 의존성: `docs/delivery/TASK-V2-69-leave-confirm.md`. **결정·승인 없음.** **구현 상태 (2026-10-29, 결정 아님):** 퇴사 확정 POST 활성화 PR이 기존 code(공식 충돌 `SFT-009`/`SFT-010`, Context 실패 `WPL-010`)를 유지한 채 활성화. #93(공개 후 추가)과 같은 재사용 방향. 팀 확정 필요. |

## 2. G-03 / G-05 결정 후보 (외부 원문은 ID로 참조)

미결정 시 처리는 "현행 보존 (Migration 비차단)"이다. 현재 승인 범위는 §4, 나머지는 PENDING이다.
G-03 원문은 참고 공유본 `docs/references/G-03-contract-review.md`에 있다 (승인된 결정 아님). G-05 원문은 저장소에 없다 (DL-005). `D-NOW-01~11`은 G-05 §0.3 "사람이 먼저 정할 것" 목록에서 온 실행 결정 후보다 (`docs/README.md` §5-1). Membership Lifecycle 관련 구체안(ML-DECISION-01, ML-SUB-STATE-01, ML-CONCURRENCY-01-R)은 ② `PROPOSED`인 과거 제안이며, 원문을 `docs/references/ml/`에 보존했다 (현행 계약 아님). 지금 검토 중인 정책 카드는 §3에 연결한다. 과거 제안 원문 자체의 상태는 바꾸지 않는다. 아래 현재 상태는 §4의 사람 전달 승인 범위를 반영한다.

| ID | 질문 (요약) | 시점 (G-05 제안) | 결정 주체 | 상태 |
|---|---|---|---|---|
| G03-D-01 | 성공 응답 `code` 규칙 | DEFER | COMMON | PENDING |
| G03-D-02 | Pattern 권한 실패 Error Contract | DEFER | COMMON | PENDING |
| G03-D-03 | ErrorCode Prefix / 400·409 | DEFER | COMMON | APPROVED (§4) |
| G03-D-04 | 다른 Workplace / User Resource 접근 시 노출 정책 | DEFER | COMMON | PENDING |
| G03-D-05 | Warning + confirm 응답 계약 | DEFER | COMMON | APPROVED (§4, 정책 방향; 기술 명세 조건 별도) |
| G03-D-06 | 다른 User Resource 403 / 404 | DEFER | COMMON | PENDING |
| G03-D-07 | Unavailable ErrorCode 형식 / 메시지 | DEFER | COMMON | PENDING |
| G03-D-08 | Unavailable Success code / 정렬 / DELETE 응답 | DEFER | COMMON | PENDING |
| G03-D-09 | Substitute Error Contract | DEFER | COMMON | PENDING |
| G03-D-10 | 남의 알림 403 / 404, Success code | DEFER | COMMON | PENDING |
| G03-D-11 | Membership 종료를 3차 Scope로 채택 (H2) | DECIDE_NOW | 팀 | APPROVED (§4) |
| G03-D-12 | 종료 채택 시 최소 범위 | D-11 종속 | 팀 | APPROVED (§4) |
| G03-D-13 | 재가입 방식 | D-11 종속 | 팀 | APPROVED (§4) |
| G03-D-14 | 종료 시 Pattern 처리 | D-11 종속 | 팀 | APPROVED (§4) |
| G03-D-15 | Substitute 관련 Membership Lifecycle | D-11 종속 | 팀 | APPROVED (§4) |
| G03-D-16 | 종료 Membership의 알림 노출 / 수신 | D-11 종속 | 팀 | PENDING |
| G03-D-17 | Request 종료 상태 / 어휘 | DEFER | 팀 | PENDING |
| G03-D-18 | 철회 / 관리자 거절 / 재OPEN 흐름과 알림 | DEFER | 팀 | PENDING |
| G03-D-19 | Expiry | DEFER | 팀 | APPROVED (§4) |
| G03-D-20 | Expiry 도입 시 만료 알림 | D-19 종속 | 팀 | PENDING |
| G03-D-21 | ACCEPT / APPROVE의 Unavailable 충돌 처리 | DECIDE_AFTER_EVIDENCE | 팀 | APPROVED (§4) |
| G03-D-22 | 알림 유실 허용 수준과 실패 관측 (H5 연관) | DECIDE_AFTER_EVIDENCE | 팀 | APPROVED (§4) |
| G03-D-23 | 서버 Logout 채택 (H1) | DECIDE_NOW | 팀 | APPROVED (§4) |
| G03-D-24 | 다중 기기 동시 Refresh | DEFER | 팀 | PENDING |
| G03-D-25 | Pattern Canonical API Method (현행 PUT) | DECIDE_NOW (문서 기준) | 팀 | PENDING |
| G03-D-26 | `CANCELLED` 장기 의미 | DEFER | 팀 | APPROVED (§4) |
| G03-D-27 | PUBLISHED 이후 정정 범위 | DEFER | 팀 | APPROVED (§4) |
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
| [`ML-H1`](proposals/ML-H1-membership-history.md) Membership 이력 보존과 재가입 범위 | G03-D-13 (주), G03-D-11 (전제) | 사용자 잠정 결정 / ② `PROPOSED` / ③ `DRAFT` | G03-D-11 `PENDING`, G03-D-13 `PENDING` | H1-a 유효. H1-b·c·d는 후속 사용자 잠정 결정 [`ML-H2`](proposals/ML-H2-rejoin-and-active-membership.md)로 대체 (팀 승인 전) |
| [`G03-D-19`](proposals/G03-D-19-expiry.md) 대타 요청 만료 표현 | G03-D-19 (주), G03-D-20·26·27 (연결) | 사용자 잠정 결정 (2026-10-25) / ② `PROPOSED` / ③ `DRAFT` | G03-D-19 `PENDING` (작성 당시) → **현재 `APPROVED` (§2·§4-2)** | B1 (자동 만료에만 `closedAt = startAt`) + 보낸 요청 화면에 만료 시각 표시. V4-T-02 기대값 변경 포함 (승인 대상). 공개 근무 시간 변경 도입 시 재검토 |
| [`G03-D-21`](proposals/G03-D-21-unavailable-time.md) 근무 불가능 시간 충돌 처리 | G03-D-21 (주), G03-C-24·25·38·40, G03-INV-09 | 사용자 잠정 결정 (2026-10-25) / ② `PROPOSED` / ③ `DRAFT` | G03-D-21 `PENDING` | 관리자 직접 배정은 경고 후 확인, 대타 수락·승인은 거부, 후보 선정은 제외 (현재 동작 유지). 퇴사 일괄 인계는 충돌 목록 확인 + 확정 시 재계산. 승인 거부 후 직접 배정은 APPROVED를 만들지 않음 |
| [`G03-D-22`](proposals/G03-D-22-notification-delivery.md) 알림 전달 수준과 상태 확인 보장 | G03-D-22 (주), G03-D-16·20, H5 | 사용자 잠정 결정 (2026-10-25) / ② `PROPOSED` / ③ `DRAFT` | G03-D-22 `PENDING` (작성 당시) → **현재 `APPROVED` (§2·§4-2)** | 알림은 최선 노력(현재 구조 유지), 업무 결과는 조회 화면이 보장. 수락 이력이 있는 후보에게 종료된 대타를 효력 종료 시각 기준 최근 7일 읽기 전용으로 표시 (MVP). 승인된 수락자도 "승인됨"으로 표시하되 현재 담당과 구분. SUB-07·SUB-05 현재 소속 조건은 퇴사·재가입 묶음의 P0 조건. Outbox·재시도는 후속 |
| [`G03-D-03`](proposals/G03-D-03-error-code.md) ErrorCode 체계와 정정 기준 | G03-D-03 (주), G03-D-05 (연결) | 사용자 잠정 결정 (2026-10-25) / ② `PROPOSED` / ③ `DRAFT` | G03-D-03 `PENDING` | `{DOMAIN}-{NNN}` 유지, HTTP·종류는 공통 목록 문서(S2)에서 관리. Migration은 기존 그대로. 잘못된 안내(T1)만 정정 (기본 P1): 승인 `SFT-006` → 새 SUB code, 수락·거절 `SUB-012` → 호출 지점 분리. 503 자동 재시도 금지 |
| [`AUTH-1`](proposals/AUTH-1-server-logout.md) 서버 로그아웃 | G03-D-23 (주), G03-D-24, G03-D-03, E-006 | 사용자 잠정 결정 (2026-10-23) / ② `PROPOSED` / ③ `DRAFT` | G03-D-23 `PENDING`, G03-D-24 `PENDING` | Refresh Token 조건부 무효화. 다중 기기·Rotation·Access 즉시 무효화는 제외. 새 code 후보 `AUTH-007`은 G03-D-03 §4 |
| [`ML-2`](proposals/ML-2-leave-shifts-and-published-changes.md) 퇴사 시 공식 근무 처리와 공개 후 변경 | G03-D-26·27 (주), G03-D-12·14, G03-D-11 (전제), G03-C-31 (계약 변경 대상) | 사용자 잠정 결정 (2026-10-23) / ② `PROPOSED` / ③ `DRAFT` | G03-D-11·12·14·26·27 `PENDING` | 근무별 인계·취소, DRAFT 삭제, Pattern 보존·적용 제외, 공개 후 추가·취소·담당자 변경, 배정 이력. 불가능 시간은 G03-D-21 |
| [`ML-3`](proposals/ML-3-substitute-on-leave-and-shift-change.md) 퇴사·근무 변경 시 대타 정리 | G03-D-15 (주), G03-D-17·18, G03-D-11 (전제), G03-D-22 | 사용자 잠정 결정 (2026-10-23) / ② `PROPOSED` / ③ `DRAFT` | G03-D-15·17·18 `PENDING` | `SHIFT_CHANGED`·`CANDIDATE_INELIGIBLE`·`INELIGIBLE`, ACCEPTED 보존, 닫힌 요청 재OPEN 없음. 운영 Enum DDL 필요 |
| [`ML-H2`](proposals/ML-H2-rejoin-and-active-membership.md) 재가입과 현재 소속 유일성 | G03-D-13 (주), G03-D-11 (전제) | 사용자 잠정 결정 (2026-10-23) / ② `PROPOSED` / ③ `DRAFT`. 기술 선택 U2'는 실험 통과 조건부 | G03-D-11·13 `PENDING` | 새 Row, 현재 소속 ≤ 1 (DB 제약, U2' 우선·실패 시 U3), 유효 초대 코드로 재가입, EMPLOYEE만 퇴사 대상. ML-H1 H1-b·c·d를 대체 |
| [`ML-4`](proposals/ML-4-leave-command-and-access.md) 퇴사 Command와 조회 권한 | G03-D-12 (주), G03-D-11 (전제), G03-D-16, G03-D-04·06, G03-D-21·22 | 사용자 잠정 결정 (2026-10-23) / ② `PROPOSED` / ③ `DRAFT` | G03-D-11·12·16 `PENDING` | 미리보기 → 확정, 재조회 409, 진행 중 근무 거부, 빠진 근무·내 소속 이력. 경고 응답 형식은 G03-D-05 (미결) |
| [`ML-5`](proposals/ML-5-concurrency-and-lock-order.md) 동시성과 Lock 순서 | DL-002·009·010, G03-D-11 (전제) | 원칙: 사용자 잠정 결정 (2026-10-23) / ② `PROPOSED` / ③ `DRAFT`. 구체 순서: 분석 후보, 실험 검증 대기 | DL-002·009·010 `PENDING` | 전역 Lock 순서 후보, 소속 Guard, 승인 순서 변경 후보. 503 매핑은 G03-D-03 §5-3 (실험 후 적용) |
| [`G03-D-05`](proposals/G03-D-05-confirmation-response.md) 경고 후 확인 응답 계약 | G03-D-05 (주), G03-D-21·03 | 사용자 잠정 결정 (2026-10-25) / ② `PROPOSED` / ③ `DRAFT`. S05-a: 겹침 구간만 표시 | G03-D-05 `PENDING` | 새 Command만: 확정 요청 → 409 CONFIRM_REQUIRED + 충돌 목록 → 확인 목록과 함께 같은 API 재요청, 현재 충돌 ⊆ 확인 범위면 진행. 계획 불일치는 409 STALE. 기존 DRAFT·Publish·불가능 시간 등록 형식은 유지 |
| [`API-CATALOG`](../contract/API-CATALOG.md) B1 퇴사·공개 후 변경 API | G03-D-04 (새 API 범위), ML-2 D27-5 해석 | 사용자 잠정 결정 (2026-10-25) / ③ `DRAFT` | G03-D-04 `PENDING` | 새 API는 대상 없음·다른 근무지를 404로 통일 (기존 API 400/404 유지). 대타가 있는 근무도 상태별 처리(OPEN·ACCEPTED → `SHIFT_CHANGED`, APPROVED 이력 보존)를 보장하면 취소·담당자 변경 가능 |
| [`USER-PATH-REVIEW`](../contract/USER-PATH-REVIEW.md) S3 사용자 경로 검증 (S3-A 결정) | CHG-06·07 (M3), G03-D-10 (연결), G03-D-22 (관리자 대체 화면) | 사용자 잠정 결정 (2026-10-25) / ③ `DRAFT` | G03-D-10 `PENDING` (`DEFER` 유지) | A: 대타 승인·종료는 역할 먼저 검사 (비관리자 403 조회 전, 범위 밖 관리자 404). B: 알림 읽음 응답 code는 G03-D-10 대기, Lock 전 확인·Frontend 복구는 별도. C: 관리자 끝난 대타 요청 API-SB-02 (종료+만료, 사실 Field, 7일 기본 필터) P0 후보. X-4: 실행한 관리자는 관리자 수신에서만 제외. B2·Frontend·ErrorCode·NotificationType·우선순위·검증 전략 행은 S3-B에서 추가 (아래) |
| [`API-CATALOG`](../contract/API-CATALOG.md) B2 새 API·기존 API 변경 (CHG-01~07, M3) | **G03-D-04 (연결, `DEFER`)**, ML-H2, ML-4, G03-D-22 | 사용자 잠정 결정 (2026-10-25) / ③ `DRAFT` | G03-D-04 `PENDING` | CHG-06 (나) 대타 승인 권한 검사 순서 P0, CHG-07 (가) ID 직접 지정 API 전체에 M3 선검사, S3-A W안. **M3·W안은 G03-D-04(다른 근무지·User 자원 접근 노출 정책)의 답에 해당한다 → 팀이 함께 승인해야 함** (S3-B 발견, 결정 아님). 대타 승인(SUB-04) 부분만 §4-6에서 채택 (2026-10-28, PR #83) |
| [`FRONTEND-CONTRACT`](../contract/FRONTEND-CONTRACT.md) §9 화면 계약 | G03-D-05, ML-4 | 사용자 잠정 결정 (2026-10-25) / ③ `DRAFT` | — | 인계 대상이 확정 전 소속 종료 → 409 STALE, 빠진 근무에 새 담당자 이름 없음, 이전 소속 데이터 폐기 원칙. S3-A: 알림 읽음 실패 복구, 관리자 끝난 요청 탭 |
| [`ERROR-CODES`](../contract/ERROR-CODES.md) §6 | G03-D-03 | 사용자 잠정 결정 (2026-10-25) / ③ `DRAFT` | G03-D-03 `PENDING` | 목록 확인 경고는 새 code `WPL-011`·`SFT-021` (공통 `data` 형식), T1-04 `SUB-006`은 code 유지·문구만 중립화 |
| [`NOTIFICATION-TYPES`](../contract/NOTIFICATION-TYPES.md) §6 | G03-D-22, **G03-D-16 (연결)**, **G03-D-20 (연결)** | 사용자 잠정 결정 (2026-10-25) / ③ `DRAFT` | G03-D-16·20 `PENDING` | 종료 알림 사유별 Type, 받는 사람별 합치기 세 조건, `SHIFT_CHANGED`의 PENDING 후보 제외, 실행 관리자는 관리자 수신에서만 제외 (S3-A X-4). 과거 소속 알림 읽음(B-2)은 G03-D-16과 같은 질문, 관리자 만료 조회(API-SB-02)는 만료 알림(G03-D-20) 없이 대체 화면만 제공 |
| [`IMPLEMENTATION-PRIORITY`](IMPLEMENTATION-PRIORITY.md) 구현 우선순위 원칙 | — | 사용자 잠정 원칙 (2026-10-25) / ③ `DRAFT` | — | P0/P1/P2 기준, 정합성 묶음 A~F·B', 묶음별 추적표. 일정·담당(D-NOW-01) 미정 |
| [`TEST-STRATEGY`](../evidence/TEST-STRATEGY.md)·[`KOTLIN-REVIEW-CHECKLIST`](../migration/KOTLIN-REVIEW-CHECKLIST.md) 검증 전략 | DL-009·010 (연결), IMP-07 (연결) | 사용자 잠정 원칙 (2026-10-25) / ③ `DRAFT` | DL-009·010 `PENDING` | Migration 5단계, Test 호출 문법 수정만 허용, Java 기준선 로컬 실행 + XML (실행은 S4 승인 후), `prod` Profile 실행 승인 안 함, `@JvmRecord`는 DTO별 선택지 |

## 4. 현재 정책 채택 범위 — 사용자 전달 승인 기록 (2026-10-26)

### 4-1. 근거와 기록 방식

사용자는 2026-10-26 이 문서 보정 작업을 지시하면서 **PR #14 Approve와 구두 승인을 받았고, 정책 방향은 유지하되 문서 모순·구현 공백을 보정하면 된다**고 전달했다. 이 기록은 그 사람의 전달 내용을 대필한 것이며 AI가 독립적으로 팀 승인을 추정한 것이 아니다. 기존의 PR Approve만으로는 정책 승인으로 읽지 않는 일반 규칙은 유지하되, 이번에는 사람의 구두 승인 전달을 추가 근거로 사용한다.

- 승인 주체: 팀의 구두 승인 및 PR 리뷰 승인, 사용자 전달. 구두 승인 참석자별 성명·실제 회의 일자는 제공되지 않았으므로 추정하지 않는다.
- 전달자: 이번 문서 보정을 지시한 사용자. 전달일: 2026-10-26. 기록자: 사용자 지시에 따른 Codex 대필.
- PR 근거: [PR #14](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/14), Head `0ea4eda`의 woneychl·deokwoo-kim Approve(2026-10-26). 두 사람을 구두 승인 참석자로 간주하지 않는다.
- 채팅 전달 원문 요지: “approved를 한 것과 구두 승인으로 받았어서”, “대부분 확인하고 괜찮다는 입장”, “모순이나 충돌하는 부분 … 정합성 … 문제점을 파악해서 수정”.
- 이 전달은 이미 문장으로 선택된 정책 방향의 채택 근거다. 문서에 **미정**으로 남아 있는 선택지에 AI가 답을 만들거나, 기술 실험·운영 검증을 통과한 것으로 기록하는 근거가 아니다.

### 4-2. 채택한 결정 문장

아래 기존12개와 §4-4의 G03-D-05 정책 방향은 **APPROVED**다. 승인 근거·전달자·일자는 §4-1을 공통 적용한다. 정책 구현은 Migration과 분리하며 해당 Task의 구현·회귀·배포 조건을 각각 충족한다.

| Decision | 결정 내용 | 적용 조건·예외 |
|---|---|---|
| G03-D-03 | DOMAIN-NNN ErrorCode 체계 유지, 문서의 T1 오류 의미 정정을 별도 Improvement로 진행 | Migration의 기존 code·message·HTTP 기대값은 유지. 새 Command의 미완성 응답 형식은 G03-D-05에서 별도 확정 |
| G03-D-11 | 퇴사와 재가입을 V3 Improvement 범위에 포함 | Kotlin 변환 PR과 분리 |
| G03-D-12 | 현재 관리자에 의한 EMPLOYEE 퇴사, 미리보기·근무별 인계/취소·단일 Transaction으로 소속/근무/대타 정리 | STALE은 §4-4의 미리보기 기준. 비교 정보의 기술 구현은 별도 착수 조건. 진행 중 근무 거부 |
| G03-D-13 | 종료 소속을 재활성화하지 않고 새 Row로 재가입, 현재 소속은 근무지·사용자당 최대1 | U2' 생성 컬럼은 실험 통과 조건부, 실패 시 대안 검토. 운영 Unique 이행·구버전 호환 검증 전 배포하지 않음 |
| G03-D-14 | 퇴사자의 DRAFT 근무 정리, Pattern 보존·자동 적용 제외 | 퇴사와 편집 경합의 불변식은 별도 MySQL 실험/회귀로 확인 |
| G03-D-15 | 근무 변경 시 활성 요청 SHIFT_CHANGED 종료, 수락 후보 퇴사 시 CANDIDATE_INELIGIBLE 종료, 닫힌 요청 재오픈 없음 | 이력 보존. 활성 요청2개 이상 비정상 데이터 처리 방식은 미결정 |
| G03-D-19 | 시작 시각이 지난 OPEN/ACCEPTED를 응답에서 CLOSED/EXPIRED, 자동 만료의 closedAt=startAt | DB 상태를 조회에서 바꾸지 않음. 기존 Test 기대값 정정은 별도 Improvement에서 수행 |
| G03-D-21 | 관리자 직접 배정은 불가능 시간 경고 후 확인, 대타 수락·승인은 거부 | 공식 근무 중복·권한·소속·시작한 근무는 확인으로 우회 불가 |
| G03-D-22 | 알림 최선 노력, 실패해도 업무 Commit 유지, 업무 결과는 대체 화면으로 확인 | 새 알림6종·상세 수신자/합치기 규칙. SHIFT_CHANGED의 PENDING 후보 제외; 실행자는 관리자 수신자 집합에서만 제외, 요청자 역할 알림은 유지. 필요한 대체 화면이 해당 기능 묶음에서 구현·검증되어야 완료 |
| G03-D-23 | AUTH-1의 Refresh 조건부 무효화 방식 서버 로그아웃 | Access 즉시 무효화·다중 기기·Rotation은 자동 포함하지 않음. 로그인별 jti·Origin·Cookie 계약은 별도 구현/회귀 확인 |
| G03-D-26 | CANCELLED는 시작 전 공식 근무 배정 종료의 끝 상태, 사유·시각·행위자 기록 | 인력 수요 자체가 사라졌다는 의미로 쓰지 않음 |
| G03-D-27 | 공개 후 추가·취소·담당자 변경을 별도 Command로 제공하고 담당자 변경 이력 보존 | 시간 변경·원자적 교체는 범위 밖. 기존 DRAFT API의 제한은 보존 |

### 4-3. 이번 승인으로 답을 만들지 않은 항목

- G03-D-05: 정책 방향과 STALE 미리보기 기준은 §4-4로 확정했다. 신규 추가의 newShiftKey 및 미리보기 비교 정보의 구체 형식·무결성 방식은 구현 착수 시 확정하고 검증한다. 기술 선택을 과거 구두 승인에 소급 포함하지 않는다.
- G03-D-04·10·16: 기존 API의 범위 밖/종료 소속 접근 응답 예외는 원문의 미정 상태를 유지한다. 특히 알림 읽음을 403에서404로 임의 전환하지 않는다. 새 API의 명시된404 원칙을 기존 API 전체로 확대하지 않는다.
- ML-5: 전역 순서·소속 Guard·Lock 후 재검증 원칙의 방향을 유지하되 구체 순서·RC/RR·교착/timeout 매핑은 실험 결과와 별도 Task로 확정한다. DL-002·009·010의 기술 상태를 자동 승인하지 않는다.
- 운영 Schema·DDL·키 교체·Healthcheck·복구, 담당자 지정·일정·공식 Baseline 선언은 별도 확인 대상이다. 새 Enum 값 저장 후 구버전 읽기와 재가입 이력 후 옛 Unique 복구가 가능한지 반드시 검증한다.

§1의 과거 기술 결정과 §3의 카드 연결표는 기존 작성 이력이다. 현재 정책 상태는 §2와 이 §4가 우선한다. 과거 카드 머리말의 PROPOSED/PENDING 표기만 읽어 이미 채택한 위 정책 방향13개을 다시 미승인으로 되돌리지 않는다. 승인 범위를 넘는 Field·ErrorCode 번호·배포 절차까지 모두 확정됐다는 뜻도 아니다.

### 4-4. 퇴사 미리보기 기준 확정 (2026-10-26)

사용자가 이 대화의 선택 질문에 **“미리보기 기준: 이후 자동 대상이 늘면 다시 미리보기, 줄면 진행”**을 선택했다. 이 결정은 G03-D-05의 정책 방향을 APPROVED로 기록하는 직접 근거다. 전달된 기존 구두 승인과 구분하며, 아래 비교 정보의 기술 구현까지 승인/검증됐다고 쓰지 않는다.

1. 공식 근무: 미리보기에서 확인한 대상·담당자·상태·시간과 확정 계획이 일치해야 한다. 대상 추가/삭제/변경은 재확인 대상이다. 인계 대상은 선택 이후 현재 소속인지 다시 검사한다.
2. 자동 처리: 현재 영향이 미리보기 영향의 부분집합인 경우만 진행 가능하다. ID와 건수만 비교하지 않고 대상별 삭제·후보자격변경·요청종료 등 처리 효과를 비교한다. 새로운 대상, 같은 건수의 대상 교체, 새 처리 효과·이력 영향이 생기면 409 STALE이다. 영향 감소 여부가 불명확하면 STALE로 처리한다.
3. STALE/CONFIRM_REQUIRED 및 그 밖의 확정 실패 시 이번 퇴사 요청의 업무 변경은0이다. 이미 다른 요청이 Commit한 변경은 되돌리지 않는다. 확정 성공은 소속·근무·대타·배정 이력 등을 단일 업무 Transaction으로 Commit한다.
4. 미리보기와 확정 사이뿐 아니라 Lock 대기 중 변경도 서버가 재검사한다. 검사를 통과한 뒤 새 근무/후보가 끼어들지 않도록 경쟁 Command의 소속 Guard·Lock 규약을 함께 검증한다.
5. 화면은 STALE이면 미리보기와 비교 정보를 함께 새로 받는다. 여전히 유효한 인계·취소 선택만 복구하고 바뀐 영향을 다시 보여 준다. 변경 요청을 자동 재전송하지 않는다.

**문서 1차본의 경계:** 정책 방향은 이 절로 확정한다. API의 `previewContext`와 신규 추가의 `newShiftKey`는 구현을 위한 1차 계약 후보이며 필드·무결성 방식·유효기간은 해당 Command의 구현 착수 시 V2/공통/Frontend가 확정한다. 서버가 미리보기의 원본 범위를 검증할 수 없으면 그 Command 구현을 완료로 처리하지 않는다. 이 기술 착수 조건은 J4 제출이나 AS-IS Kotlin Migration 전체의 차단 조건이 아니다. 상세 보장과 검증 항목은 G03-D-05 §5-4 및 DOMAIN-DEVELOPMENT-GUIDE §7-4를 따른다.

### 4-5. AUTH-1 Backend 구현 세부 (2026-10-28, V1 Owner 결정)

G03-D-23(§4-2) 범위 안에서 V1 Owner가 Issue #62 구현 중 이 대화의 선택 질문에 답한 내용이다. **팀 승인 기록이 아니다.** 팀 확인은 PR 리뷰로 하며, 승인되면 이 절에 승인자·일자를 적는다. AI가 승인 범위를 넓히지 않는다.

| 항목 | 결정 | 근거 |
|---|---|---|
| `AUTH-007` | 403, 문구 "허용되지 않은 요청 출처입니다." | ERROR-CODES §4 예약 번호, G03-D-03 §5-2 (403 = 행위자 권한 부족) |
| 로그아웃 성공 응답 | 200, "로그아웃되었습니다", `data: null` | AUTH-1 §8 응답 형식 |
| `jti` 형식 | `UUID.randomUUID()` (AUTH-1 §8 예시) | 64바이트 이상 비밀키 + id 8자리부터 255자 초과는 배포 확인 항목으로 넘김 (EV-032) |
| 허용 안 된 Origin의 응답 | 기존 CORS 거부(403 `Invalid CORS request`)를 그대로 둠. `AUTH-007`은 Referer 실패·둘 다 없음에서 사용 | 보안 결과 같음, 브라우저는 그 응답을 읽지 못함. 보안 필터 순서 변경 없음 (AUTH-1 "Backend 구현 기록" 절) |

### 4-6. 대타 승인 응답·Lock 순서 (B', 2026-10-28, PR #83 리뷰 승인)

USER-PATH-REVIEW 결정 A(W안)와 ML-5의 승인 Lock 순서를 **대타 승인 API(SUB-04)에 한해** 채택한다.

- 근거: [PR #83](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/83) Head `03a4bcc`의 abcdeghi·dewyflowers Approve (2026-10-28). dewyflowers 리뷰가 "이 PR 승인을 결정 A 수용으로 기록"을 요청했고, V4 Owner가 이 리뷰 승인으로 팀 승인을 갈음한다고 전달했다 (2026-10-28). §4-1의 "PR Approve만으로는 정책 승인으로 읽지 않는다" 규칙의 예외이며 이 절의 범위에만 적용한다.
- abcdeghi 리뷰는 이 응답이 G03-D-04 미결정 사항과 관련돼 "나중에 충돌하면 합의해서 맞출 수 있다"는 여지를 남겼다. G03-D-04가 다르게 결정되면 이 절을 다시 연다.

| 항목 | 결정 | 근거 |
|---|---|---|
| 승인 실패 응답 (결정 A) | 어느 근무지에서도 현재 MANAGER가 아니면 Request 조회 전 403 `WPL-004`. 다른 근무지 요청과 없는 ID는 같은 404 `SUB-001` | API-CATALOG §5-5, USER-PATH-REVIEW §9-0 |
| 승인 Lock 순서 | Lock 없는 ID 조회 → Shift `FOR UPDATE` → Request `FOR UPDATE` → Lock 뒤 근무 동일성·상태·관리자 재확인 → 수락자 User | ML-5 §5-1 승인 행. ML5-T3이 이전 코드에서 Deadlock으로 실패(ML5-R2 재현), 바꾼 코드에서 통과 (EV-035) |
| 기존 Test 기대값 | V4-T-26을 둘로 나눔 (403 `WPL-004` / 404 `SUB-001`), V4-T-74는 code 그대로·조회 전 거절 확인 추가 | TEST-INVENTORY §5-4 |

**이 절로 정하지 않은 것:** G03-D-04 전체(다른 API의 범위 밖 응답), 대타 종료(SUB-08)의 W안과 후보 응답·알림 읽음 선검사(CHG-07), 수락자 소속 `FOR SHARE` 재확인(묶음 C), Lock 뒤 근무 동일성 불일치 응답(지금 500, ML-5 §6 기준이면 409 후보로 ErrorCode 추가 필요), 다른 Command의 Lock 순서와 RC/RR·교착/timeout 응답 매핑.
