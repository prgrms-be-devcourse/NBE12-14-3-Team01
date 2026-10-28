# SWITCH 3차 개발 문서 Index

> 작성 기준: D0-B `243f77c` (2026-10-22, 2차 Java 저장소에서 작성) → **D0-D 3차 저장소 이관** (2026-10-23, 3차 `develop` `cdaaa13` 기준).
> ③ 문서 리뷰: **DRAFT**. 이 문서 체계는 **팀 승인 전 초안**이다. 정책 승인 여부는 `DECISION-LOG`의 현재 결정 범위와 `CONTRACT-BASELINE`을 따른다. 2026-10-26 구두 승인 전달 기록은 `DECISION-LOG` §4에 있다. 문서 DRAFT와 정책 승인 상태를 섞지 않는다.

## 1. 읽는 순서

| 순서 | 문서 | 언제 |
|---|---|---|
| 1 | [`AGENTS.md`](../AGENTS.md) (루트) | Codex 자동 로드 / Claude Code는 `CLAUDE.md`에서 참조 |
| 2 | 이 문서 | 문서 위치·우선순위 확인 |
| 3 | [`docs/ai/AI-RULES.md`](ai/AI-RULES.md) | 모든 작업 전 |
| 4 | [`docs/STATUS-AXES.md`](STATUS-AXES.md) | 상태값을 읽거나 쓰기 전 |
| 5 | Task Card (Delivery Board) + `docs/delivery/<Domain>.md` ([템플릿](delivery/TEMPLATE.md), [V4 Substitute 시범](delivery/V4-substitute.md)). 참고: 개발 순서·Test 규칙 제안 [`DOMAIN-DEVELOPMENT-GUIDE`](delivery/DOMAIN-DEVELOPMENT-GUIDE.md), Task Card 초안 [`TASK-OPS-01`](delivery/TASK-OPS-01-prod-config-failfast.md) | 이번 작업의 범위 확인 |
| 6 | [`docs/migration/MIGRATION-MATRIX.md`](migration/MIGRATION-MATRIX.md). 참고: Kotlin 전환 PR 리뷰 항목 [`KOTLIN-REVIEW-CHECKLIST`](migration/KOTLIN-REVIEW-CHECKLIST.md) | Domain 작업 전 |
| 7 | [`docs/contract/CONTRACT-BASELINE.md`](contract/CONTRACT-BASELINE.md). 참고: V3 Improvement 계약 초안 (③ `DRAFT`, `APPROVED` 아님) [`API-CATALOG`](contract/API-CATALOG.md), [`ERROR-CODES`](contract/ERROR-CODES.md), [`FRONTEND-CONTRACT`](contract/FRONTEND-CONTRACT.md), [`NOTIFICATION-TYPES`](contract/NOTIFICATION-TYPES.md), [`USER-PATH-REVIEW`](contract/USER-PATH-REVIEW.md) | API / Error / Enum / Transaction에 닿는 작업 전 |
| 8 | [`docs/decisions/DECISION-LOG.md`](decisions/DECISION-LOG.md), [`PROCESS-CHARTER`](decisions/PROCESS-CHARTER.md), 정책 카드 [`decisions/proposals/`](decisions/proposals/). 참고: 정책 요약 [`POLICY-SUMMARY`](decisions/POLICY-SUMMARY.md), 구현 우선순위 원칙 [`IMPLEMENTATION-PRIORITY`](decisions/IMPLEMENTATION-PRIORITY.md) | 정책이 걸린 작업 전. 정책 카드를 만들거나 결정할 때 |
| 9 | [`docs/migration/TECH-STACK-DECISIONS.md`](migration/TECH-STACK-DECISIONS.md) | Gradle / Plugin / Version / Kotlin 환경 작업 전 |
| 10 | [`docs/evidence/TEST-INVENTORY.md`](evidence/TEST-INVENTORY.md), [`docs/evidence/TEST-EVIDENCE.md`](evidence/TEST-EVIDENCE.md). 참고: 검증 원칙 [`TEST-STRATEGY`](evidence/TEST-STRATEGY.md), 공백 분석 [`TEST-GAP-ANALYSIS`](evidence/TEST-GAP-ANALYSIS.md), S4 실험 계획 [`EXPERIMENT-PLAN`](evidence/EXPERIMENT-PLAN.md) | Test를 고르거나 결과를 보고·확인할 때 |
| 11 | [`docs/references/README.md`](references/README.md) → [`G-03-contract-review.md`](references/G-03-contract-review.md), [`references/ml/`](references/ml/) | C-ID·G03-D-ID의 원문 규칙·근거, ML 과거 제안 원문을 확인할 때 (참고 자료, 승인된 계약 아님) |

> "참고:" 뒤의 문서는 필수 읽기가 아니다. 그 작업에 닿을 때만 연다. ③ 문서 리뷰는 DRAFT 상태이며, ② 정책의 현재 채택 범위는 DECISION-LOG §4를 별도로 따른다. 각 문서의 상태는 문서 머리말과 §2 표를 따른다.

## 2. 문서 목록과 책임

| 문서 | 목적 | 수정 담당 | Source of Truth | 갱신 시점 | AI 권한 |
|---|---|---|---|---|---|
| `AGENTS.md` | 공통 AI 작업 진입점 (기존 CLAUDE.md 지침 이동) | 공통 설정 Owner (미지정) | 진입 규칙 | AI-RULES 변경 시 | 읽기 전용 (기존 CLAUDE.md 규칙 계승) |
| `CLAUDE.md` | Claude Code용 AGENTS.md 참조 파일 | 공통 설정 Owner (미지정) | 진입 규칙 | AI-RULES 변경 시 | 읽기 전용 (D0-D 최초 이관 때만 사실·경로 정정 허용, 사용자 지시 2026-10-23) |
| `docs/README.md` | 문서 Index | 공통 설정 Owner | 문서 위치 | 문서 추가·이동 시 | 요청 시 수정 |
| `docs/ai/AI-RULES.md` | AI·팀원 변경 제한 규칙 | 팀 승인 (초안: H6 총괄) | 작업 규칙 | 팀 승인 시에만 | **읽기 전용** (D0-D 최초 이관 때만 사실·경로·상태 규칙 정정 허용, 사용자 지시 2026-10-23) |
| `docs/STATUS-AXES.md` | 상태 5개 축 정의 | 공통 설정 Owner | 상태값 의미 | 팀 승인 시 | 읽기 전용 |
| `docs/contract/CONTRACT-BASELINE.md` | 승인된 서비스 계약 + 공통 규약 | Domain Owner 작성 → 팀 승인 | `APPROVED` 행만 | D1, Decision 승인 시 | 초안 행 작성 가능, `APPROVED` 표시 금지 |
| `docs/contract/API-CATALOG.md` | V3 Improvement API 모양 (받는 값·돌려주는 값) 초안 | 해당 Domain Owner 리뷰 → 팀 승인 | 없음 (③ `DRAFT`. 왜 그렇게 정했는지는 정책 카드) | 정책 카드 변경 시 | 초안 작성 가능, `APPROVED` 표시 금지 |
| `docs/contract/ERROR-CODES.md` | ErrorCode 공통 목록 (현재 코드 + 후보) 초안 | 공통 설정 Owner → 팀 승인 | 없음 (③ `DRAFT`. 현재 행은 코드 확인 사실 기록) | ErrorCode 추가·결정 시 | 초안 작성 가능, `APPROVED` 표시 금지 |
| `docs/contract/FRONTEND-CONTRACT.md` | 화면 → API → 오류 대응 → 권한 연결 초안 (Frontend 담당 리뷰 대상) | Frontend 담당 리뷰 → 팀 승인 | 없음 (③ `DRAFT`. API 모양은 API-CATALOG) | API·정책 변경 시 | 초안 작성 가능, `APPROVED` 표시 금지 |
| `docs/contract/NOTIFICATION-TYPES.md` | NotificationType 공통 목록 (현재 코드 + 후보) 초안 | V5 Owner 리뷰 → 팀 승인 | 없음 (③ `DRAFT`. 현재 행은 코드 확인 사실 기록) | Type·받는 사람 규칙 변경 시 | 초안 작성 가능, `APPROVED` 표시 금지 |
| `docs/contract/USER-PATH-REVIEW.md` | 정책·API·화면·ErrorCode·알림·Test의 사용자 경로 교차 검증 | 작성자 → 팀 리뷰 | 없음 (③ `DRAFT`, 검토 기록) | 계약 초안 변경 시 | 작성 가능 |
| `docs/migration/MIGRATION-MATRIX.md` | Domain 범위, 필수 Test, 검증 상태, 선행조건 | 각 V Owner | 기술 진행 상태 | 각 PR과 함께 | 요청 시 수정 (Evidence 링크 필수) |
| `docs/migration/TECH-STACK-DECISIONS.md` | 기술 의존성·Version 결정, M0 계획 | 공통 설정 Owner | 기술 환경 결정 | 의존성 변경 승인 시 | 조사 결과·적용 사실 추가 가능, 결정 표시 금지 |
| `docs/migration/KOTLIN-REVIEW-CHECKLIST.md` | Kotlin 전환 PR 리뷰 확인 항목·판단 기준 | 공통 설정 Owner | 없음 (③ `DRAFT`. 구현 규칙 원본은 AI-RULES §5) | AI-RULES·TEST-STRATEGY 변경 시 | 요청 시 수정 |
| `docs/decisions/DECISION-LOG.md` | 정책 결정·불일치 기록 | 결정자 (팀) | 정책 결정 | 회의·승인 직후 | `PENDING` / `DISCREPANCY` 행 추가와 사실 기록만 가능 |
| `docs/decisions/PROCESS-CHARTER.md` | 정책 카드 작성·결정 절차, Test 기대값 변경 절차 (팀 승인 전 초안) | 팀 승인 | 결정 절차 | 팀 승인 시 | 초안 작성. 승인 표시 금지 |
| `docs/decisions/proposals/*.md` | 검토 중인 정책 카드 (② `PROPOSED`) | 카드 작성자 → 해당 Domain Owner 리뷰 | 없음 (결정은 `DECISION-LOG`) | 카드 진행 시 | 작성 가능. 사용자 확인 문장만 "사용자 잠정 결정"으로 기록 |
| `docs/decisions/POLICY-SUMMARY.md` | 정책 카드 요약 (팀 공유용) | 카드 작성자 | 없음 (② `PROPOSED` 요약. 원본은 정책 카드·`DECISION-LOG`) | 정책 카드 변경 시 | 작성 가능. 승인 표시 금지 |
| `docs/decisions/IMPLEMENTATION-PRIORITY.md` | Improvement 구현 우선순위 원칙 (제안) | 팀 승인 | 없음 (③ `DRAFT`, 팀 합의 전) | 팀 합의 시 | 초안 작성. 승인 표시 금지 |
| `docs/delivery/TEMPLATE.md` | Domain Delivery Sheet 템플릿 | 공통 설정 Owner | 양식 | 양식 변경 승인 시 | 읽기 전용 |
| `docs/delivery/V4-substitute.md` | V4 Substitute Delivery Sheet (시범) | V4 Owner | Domain 현황 요약 (계약·결정은 ID 참조만) | Domain PR과 함께 | 요청 시 수정 (근거 위치 필수) |
| `docs/delivery/DOMAIN-DEVELOPMENT-GUIDE.md` | Domain별 개발 순서·Test 규칙·증거 형식 (팀 제안) | 팀 합의 | 없음 (③ `DRAFT`, 팀 합의 전. 팀원에게 강제하지 않음) | 팀 합의 시 | 초안 작성. 강제·승인 표시 금지 |
| [`RELEASE-READINESS-PLAN.md`](delivery/RELEASE-READINESS-PLAN.md) | Migration 종료·A~F 목표·사용자 경로·격리 배포 검증 실행 계획 | 도메인 Owner 검토 → 팀 합의 | 없음 (③ `DRAFT`; 정책·테스트 결과의 원본 아님) | 목표·담당·검증 Gate 합의 시 | 초안 작성 가능. 구현·정책 승인 표시 금지 |
| `docs/delivery/TASK-OPS-01-prod-config-failfast.md` | Task Card 초안 (운영 설정 fail-fast) | 담당자 지정 전 | 없음 (초안) | Task 진행 시 | 작성 가능 |
| `docs/evidence/TEST-INVENTORY.md` | Test 목록·준비·실행 상태 | 각 V Owner | Test 목록 | Test 추가·실행 시 | 요청 시 수정. 실행 결과는 EV-ID로만 연결 |
| `docs/evidence/TEST-EVIDENCE.md` | 실행 근거 | 실행한 사람 | 실행 결과 | 실행할 때마다 (append-only) | 실제 실행한 결과만 추가 |
| `docs/evidence/TEST-STRATEGY.md` | 무엇을·언제·어떤 기준으로 검증하는가 (원칙) | 팀 승인 | 없음 (③ `DRAFT`, 팀 합의 전) | 팀 합의 시 | 초안 작성. 승인 표시 금지 |
| `docs/evidence/TEST-GAP-ANALYSIS.md` | 기존 Test의 사용자 경로·불변식 보호 범위와 공백 분석 | 각 V Owner | 없음 (③ `DRAFT`, 분석 기록) | Test 추가·분석 시 | 요청 시 수정 |
| `docs/evidence/EXPERIMENT-PLAN.md` | S4 실험 계획 (무엇을·어떤 순서·격리 조건) | 실행 담당 | 없음 (③ `DRAFT`, 계획) | 실험 계획 변경 시 | 요청 시 수정 |
| `docs/images/*` | ERD, Architecture, Deployment 그림 (2차 종료 시점) | — | 참고 | — | 읽기 전용 |
| `docs/references/README.md` | 선별 공유한 참고 자료 목록·차이·용어 대응 | 공통 설정 Owner | 참고 자료 범위 | 참고 자료 추가·제외 시 | 요청 시 수정 |
| `docs/references/G-03-contract-review.md` | G-03 분석 원문 공유본 (Original `9858612` 기준) | — (스냅샷) | 없음 (참고 자료, 7순위) | 원본이 바뀌어도 자동 갱신하지 않음 | 읽기 전용 |
| `docs/references/ml/*.md` | ML 과거 제안 원문 3종 (2차 `8da2bec` 기준) | — (스냅샷) | 없음 (참고 자료, 7순위) | 갱신하지 않음. 최신 검토는 정책 카드 | 읽기 전용 (머리말의 대체 관계만 추가 가능) |
| `.github/pull_request_template.md` | PR 양식 (기존 파일, D0-B·D0-D에서 미수정. 보강안은 승인 대기) | 공통 설정 Owner | PR 양식 | 승인 시 | 읽기 전용 |
| Notion WBS / Delivery Board | 일정·담당·진행 | 팀 | 일정·담당 | 매일 | 접근 불가 (링크만) |

## 3. Source 우선순위

| 순위 | 구분 | 무엇을 결정하는가 | 예 |
|---|---|---|---|
| 1 | 공식 프로젝트 요구사항 | 제출 **범위** (동작은 결정하지 않음) | 3차 공식 가이드 (저장소에 없음, UNKNOWN) → 팀 계획서 "필수 수행 사항" 요약으로 대체 |
| 2 | 승인된 정책 결정 | 해당 항목만 아래 순위보다 우선 | `DECISION-LOG.md`의 `APPROVED` 행과 §4의 승인 범위·예외 |
| 3 | 승인된 서비스 계약 | 계약 | `CONTRACT-BASELINE.md`의 승인된 결정 연결 (기존 AS-IS 행을 소급 승인하지 않음) |
| 4 | 실행 Test Evidence | Migration 중 **보존할 현재 동작** — 정답이라는 뜻은 아님 | 최종 Java Baseline(`BL-TBD`)의 실행 결과. D0-B Snapshot `243f77c`의 266 PASS(EV-002), `a148a62`의 394 PASS × 2(EV-010·011) 등 SHA별 근거를 구분한다. 기준선 최종 선언과 실행 사실은 별개 |
| 5 | Java AS-IS 구현 | 현재 사실 | Java Baseline 후보 `8da2bec` (= 3차 `main`), Snapshot `243f77c`, Original `9858612` |
| 6 | 2차 명세 (계약 후보) | 비교 대상 (`AUDIT_REQUIRED`) | P0 정리본, COMMON Policy, API-* PDF |
| 7 | AI 분석 보고서 | 제안·근거 색인 | G-01~G-05, A-01~07. G-03은 `docs/references/`에 참고 공유본이 있다. G-03 C-ID는 대부분 ① `REVIEW_ACCEPTED` (팀 승인 아님) |
| 8 | 참고·계획 자료 | 배경 | 기획서, 피드백 정리안 |

**기존 Java Test가 통과했다는 이유만으로 그 동작을 정답 정책으로 확정하지 않는다.** 순위 4는 "Migration에서 바꾸지 않는다"는 뜻이다.

### 충돌 처리

| 충돌 | 처리 |
|---|---|
| 코드 ↔ 명세 | `DECISION-LOG`에 `DISCREPANCY / PENDING` 기록. Migration은 현재 동작을 보존하되 정답 판정이 아님을 표시 |
| 코드 ↔ Test (develop에서 실패) | 기대값 변경 금지. 원인 분류 (Fixture / 환경 / 결함 후보) 후 보고, HOLD |
| Test ↔ 명세 | `DECISION-LOG`에 기록. Test 유지 |
| 분석 보고서 ↔ 코드 | 코드를 다시 확인. 보고서 쪽을 정정 후보로 표시 |
| 결정 없이는 진행 불가 | HOLD 후 Owner에게 질문 |

## 4. 기준 Ref

### 4-1. 2차 Java Repository (`prgrms-be-devcourse/NBE12-14-2-Team01`)

| 구분 | Ref | 의미 | 실행 근거 |
|---|---|---|---|
| Java Original | `985861263c576308168ca539159552d4a4e47606` | 2차 P0 AS-IS의 의미 기준 | `TEST-EVIDENCE` EV-001 |
| D0-B 조사 당시 Java Snapshot | `243f77c48d57eb89a118eac99f51e52db72c2271` (2026-10-22 조사 시점 `origin/develop`) | D0-B 조사·문서 작성 기준. **최종 Java Baseline 아님** | EV-002, EV-003 |
| Java Baseline 후보 | `8da2bec` (`origin/develop`, Test PR #76·#77·#78 병합 후, 2026-10-22) | Kotlin Migration 출발점 후보 | CI Suite success만 (2차 Backend CI Run `39729371810`). 개별 Test 결과·Test 수 미기록 |
| 최종 Java Baseline | **`BL-TBD`** | 후보 `8da2bec`를 B3 검증(로컬 `clean check`, 개별 Test 수 기록) 후 선언 | 미실행 |

Snapshot `243f77c`는 Original과 Production Java 코드가 같고, 다음이 다르다: 전역 `transaction-isolation: TRANSACTION_READ_COMMITTED` (`application.yml`, 모든 Profile), `build.gradle.kts`의 `testImplementation("org.testcontainers:testcontainers-mysql")` 추가, test Profile의 Testcontainers MySQL 9.7.2 (`application-test.yml`), `UnavailableTimeConcurrencyTest`의 `@Import(TestTimeConfig.class)`.
`243f77c → 8da2bec` 차이는 `backend/src/test/**` 11개 파일 (PR #76~78 Test 추가·보강)뿐이고 `backend/src/main/**` 변경은 0이다 (`git diff --stat`, 2026-10-23 확인).

### 4-2. 3차 Kotlin Migration Repository (`prgrms-be-devcourse/NBE12-14-3-Team01`, 이 저장소)

| 구분 | Ref | 의미 | 실행 근거 |
|---|---|---|---|
| `main` | `8da2bec` | 2차 `origin/develop` 최종 SHA와 같음 (History 유지 이관) | — |
| `develop` | `cdaaa13` (PR #1 `chore/kotlin-setup` 병합, 2026-10-22) | Kotlin 환경 적용. Production Java 변경 0, `src/main/kotlin`에 환경 검증 파일 1개 추가 (`TECH-STACK-DECISIONS` §1-A) | EV-008 (CI Suite success, 개별 결과 미보존) |
| `docs/switch-v3-foundation` | `cdaaa13`에서 분기 | D0-D 문서 이관 Branch (이 문서) | — |

## 5. 자료 분류와 위치

| 분류 | 자료 | 위치 | 기준 | 상태 |
|---|---|---|---|---|
| 공식 프로젝트 요구사항 | 3차 공식 가이드 | **UNKNOWN (저장소·작업공간 모두 없음)** | — | 팀 계획서로 대체 |
| 승인된 서비스 계약 | — | `docs/contract/` | — | **0건** |
| 2차 명세 (계약 후보) | P0 정리본, COMMON Policy, API-AUTH/WP/PAT/SCH/UNA/SUB/NOT | 외부: 공유 위치 미정 (원본은 작성자 로컬 `switch-v3-ai/sources/`) | 2차 작성본 | `AUDIT_REQUIRED` |
| Java AS-IS 참고 | A-01~07 Audit / Review | 외부 (작성자 로컬 `switch-v3-ai/audits`, `reviews`) | Original `9858612` | 분석 |
| AI 분석 보고서 | G-03 (C-ID·G03-D-ID 원문) | **`docs/references/G-03-contract-review.md`** (참고 공유본, 개인 경로 1줄만 바꿈) | Original `9858612` | 제안 / 참고. C-ID 상태는 원문 그대로 (`REVIEW_ACCEPTED` 54, `CONDITIONAL` C-40, `DECISION_PENDING` C-41) |
| AI 분석 보고서 (미공유) | G-01, G-02, G-04, G-05 | 외부 (작성자 로컬 `switch-v3-ai/reviews`). 저장소에 없음. 제외 이유는 `docs/references/README.md` §6 | Original `9858612` (G-05는 develop 일부 반영) | 제안 |
| 정책 결정 대기 | G03-D-01~28, OQ-01~03, D-NOW-01~11, DL-001~014 | `docs/decisions/DECISION-LOG.md` | G-03 / G-05 / D0-D | `PENDING` |
| 정책 제안 (과거 원문) | Membership Lifecycle (ML-DECISION-01), ML-SUB-STATE-01 (B+), ML-CONCURRENCY-01-R (Guard Lock) | `docs/references/ml/` (원문 보존본, 현행 계약 아님) | 2차 `8da2bec` | ② `PROPOSED`, 현재 Java에 없음 |
| 정책 카드 (검토 중) | ML-H1 (Membership 이력·재가입 범위), G03-D-19 (대타 만료 표현) | `docs/decisions/proposals/`. 진행 절차는 `docs/decisions/PROCESS-CHARTER.md` | 3차 `develop` `c0427c2` | 카드 ② `PROPOSED`, `DECISION-LOG` `PENDING` |
| 상태 체계 | 5개 축 | `docs/STATUS-AXES.md` | D0-C-R | 초안 |
| Domain Delivery | 템플릿, V4 시범 | `docs/delivery/` | 2차 `8da2bec` | 초안 |
| Test 목록 | 필드 정의, V4 구역 | `docs/evidence/TEST-INVENTORY.md` | 2차 `243f77c`·`8da2bec` | 초안 |
| Kotlin 구현 규칙 | — | `docs/ai/AI-RULES.md` §5, `docs/migration/TECH-STACK-DECISIONS.md` | 3차 `develop` `cdaaa13` | 초안 |
| 실행 Evidence | T-01, T-02A, T-02B, CI | `docs/evidence/TEST-EVIDENCE.md` | 각 행의 SHA (EV-001~007은 2차) | 기록 |
| H6 동시성 Harness | T-02A/B Test Source | 작성자 로컬 (2차 Worktree, 미Commit) | 2차 `243f77c` | `LOCAL_ONLY` (DL-009 PENDING) |
| 팀 실행·WBS·PR | WBS, Delivery Board, 기획서 | Notion (링크 미등록) | — | 팀 관리 |
| 참고 | `README.md`, `docs/images/*` (ERD, Architecture, Deployment) | 저장소 | 2차 종료 시점 | 실제 Schema와 대조 안 됨 |

외부 자료는 기본적으로 **ID로 참조**한다 (예: `C-48`, `SCH-MIG-08`, `G03-D-11`). 원문이 필요한 자료만 **검토 후 선별해** `docs/references/`에 공유한다 (민감 정보 확인, 원본과의 차이·SHA-256 기록). 공유한 참고 자료도 공식 계약·승인된 정책·구현 지시가 아니며, 위 §3 우선순위는 그대로다. 팀이 승인한 정책은 `DECISION-LOG`의 `APPROVED` 행에서만 확인한다. 나머지 자료의 공유 위치는 팀 결정 사항이다 (`DECISION-LOG` DL-005).

### 5-1. 용어: H1~H8, D-NOW

출처: G-05 §14 "V1~V5 Domain Migration / H1~H8 Technical Deepening 배치"의 "내용" 칸 (G-05가 2차 팀 계획 초안 PLAN-SRC-001에서 인용). G-05 원문은 미공유 (`docs/references/README.md` §6).
H1~H8은 Kotlin Migration(V1~V5)과 별개의 **개선(Technical Deepening) 항목**이다. 채택 여부·범위는 결정되지 않았다 (D-NOW-05 `PENDING`). 구체안이 있는 항목도 ② `PROPOSED`이며 구현 지시가 아니다. 결정은 `DECISION-LOG`, Domain 영향은 `docs/delivery/`를 본다.
`H2`(개선 항목)는 H2 Database와 다르다.

| ID | G-05 §14 "내용" (원문) | 관련 ID (G-05 §14 "차이 / 확인 필요"·배치 칸, 3차 문서의 DL) |
|---|---|---|
| H1 | Security Integration (권한, Logout, Refresh 무효화 / Rotation) | G03-D-23 (Logout), D-NOW-05 |
| H2 | Membership Lifecycle | G03-D-11, D-NOW-05. 구체안 ML-* 원문은 `docs/references/ml/` (`PROPOSED`), 검토 카드는 `docs/decisions/proposals/ML-H1-*` |
| H3 | Schedule State Consistency | SCH-DC-01 (재현·판정), D-NOW-04 |
| H4 | Idempotency / State Machine | SUB-DC-01~04 (재현·판정) |
| H5 | Outbox / Retry | G03-D-22, D-NOW-05 |
| H6 | Concurrency / Lock (MySQL 결정적 Test) | DL-002, DL-009, DL-010, D-NOW-04 |
| H7 | Monitoring (Metrics / Grafana) | D-NOW-05 |
| H8 | CI / Smoke Test | D-NOW-01, DL-012 |

`D-NOW-01~11`: G-05 §0.3 "사람이 먼저 정할 것 (`DECIDE_NOW`)"에서 온 실행 결정 후보다. 질문 요약과 상태(`PENDING`)는 `DECISION-LOG` §2에 있다.

## 6. 상태값

D0-B 문서(`CONTRACT-BASELINE`, `DECISION-LOG`, `MIGRATION-MATRIX`, `TEST-EVIDENCE`)는 아래 값을 쓴다. Delivery Sheet와 Test Inventory는 `docs/STATUS-AXES.md`의 5개 축을 쓴다. 대응은 `STATUS-AXES.md` 끝의 표를 따른다.

| 상태 | 의미 |
|---|---|
| `APPROVED` | 팀이 승인. 승인자·일자 필수 |
| `AS_IS_OBSERVED` | 코드·Test로 현재 동작을 확인. 정책 승인 아님 |
| `DISCREPANCY` | 코드·명세·Test 간 불일치 기록. 판정 없음 |
| `PENDING` | 결정 대기 |
| `UNKNOWN` | 확인하지 못함 |
| `LOCAL_ONLY` | 로컬에서만 실행·존재, 팀 Branch 미병합 |

Domain 검증 상태 (Matrix 전용): `MIGRATION_CODE_COMPLETE`, `KOTLIN_QUALITY_REVIEWED`, `UNIT_TEST_VERIFIED`, `INTEGRATION_TEST_VERIFIED`, `CONTRACT_REGRESSION_VERIFIED`, `CROSS_DOMAIN_INTEGRATED`, `API_DOC_UPDATED`. 검증 칸은 **통과한 Run 링크가 있을 때만** 채운다.

## 7. 단계 (제안)

| 단계 | 내용 | 주요 문서 | 현재 |
|---|---|---|---|
| D0-B | 문서 체계 초안 (2차 저장소에서 작성) | 전체 골격 | 완료 (미병합 초안) |
| D0-C / D0-C-R | 상태 5축, Delivery Sheet 템플릿, V4 시범 | `STATUS-AXES`, `delivery/`, `TEST-INVENTORY` | 완료 (초안) |
| D0-D | 3차 저장소 이관 (이 PR) | 전체 | 진행 중 |
| M0 | Kotlin 공통 환경 (Production 코드 변경 없음) | `TECH-STACK-DECISIONS.md` §5 | PR #1로 Plugin·Library 적용 (M0-2 상당). M0-1, M0-3~M0-8의 실행 기록은 없음 |
| D1 | 계약·공통 규약 기준 확정 (승인) | `CONTRACT-BASELINE.md`, `DECISION-LOG.md` | 미시작 |
| D2 | Domain별 Kotlin 전환 + Regression | `MIGRATION-MATRIX.md`, `delivery/`, `TEST-EVIDENCE.md` | 미시작 |
| D3 | 개선·Hardening·제출 산출물 | `DECISION-LOG.md`, Notion | 미시작 |

| 항목 | 단계 |
|---|---|
| API 명세 ↔ Java 구현 차이 | D1 기록 → 정책은 Decision |
| ApiResponse / HTTP Status / ErrorCode | D1 (현행 보존값 기록·승인) |
| Enum / 상태 전이 / DB Schema 호환성 | D1 기준, D2 검증 |
| 인증·인가 / Security Filter | D2 (V1 전환), D3 (AUTH-EV-01, H1) |
| Transaction / Rollback / Lock | D2 보존 (변경 금지), D3 (H6) |
| Membership `leftAt` / 종료 Lifecycle | D1 현행 기록, D3 (H2, G03-D-11, ML 제안은 `PROPOSED`) |
| Cross-Workplace Pattern / DRAFT / Publish | D1 현행 기록, 정책은 DL-001 → D3 |
| Substitute Request / Candidate 상태 전이 | D1 기록, D2 (V4) 보존, D3 (H4) |
| Notification Event / AFTER_COMMIT / 실패 처리 | D2 (V5) 보존, D3 (H5, G03-D-22) |
| Kotlin Nullability / JPA 호환성 | M0 검증, D2 적용 |
| 기존 Test 재사용·보강·신규 | D2 (재사용·보강), D3 (Hardening 신규) |
| Kotlin 전후 실제 계약 검증 | D2 (Domain PR CI), D3 (전체 회귀) |
| CI / Railway / 비파괴 Smoke | M0-8 (CI·Docker), D3 (H8) |
| 기획서 / WBS / Swagger / 발표 | D3 (WBS·기획서는 Notion에서 병행) |

## 8. 기술 환경 (요약, 3차 `develop` `cdaaa13`)

Java 25 Toolchain, **Gradle 9.3.0** (PR #1에서 9.7.1 → 9.3.0, Kotlin 2.3.21 공식 호환 범위에 맞춘 조정), Spring Boot 4.1.1. Library 해석 값 (Hibernate 7.4.5, Jackson 3.1.5 `tools.jackson`, Testcontainers 2.0.5 + MySQL 9.7.2)은 2차 `243f77c`·Gradle 9.7.1 기준 EV-007이며 3차에서 재해석하지 않았다 (M0-FOLLOWUP-02).
**Kotlin 2.3.21** (`jvm`, `plugin.spring`, `plugin.jpa`, `plugin.lombok`) 적용됨. Domain 코드의 Kotlin 전환은 0건.
2026-10-28 현재 (develop `92dcdfa`): Backend Java 소스 0개 (PR #77·#79). Java 전용 빌드 설정(`plugin.lombok`, Lombok 의존성, Checkstyle)은 PR #81에서 제거.
상세, 적용 사실과 D0-B 조사 판단의 차이, M0 계획은 `docs/migration/TECH-STACK-DECISIONS.md`. 승인 기록은 `DECISION-LOG` DL-003·004·014 (모두 `PENDING`).
