# Domain Delivery Sheet — {V번호} {Domain}

> ③ 문서 리뷰: **DRAFT** (D0-C-R, D0-D 이관 2026-10-23). 새 Domain Sheet는 이 파일을 `docs/delivery/<V번호>-<domain>.md`로 복사해 쓴다 (예: `docs/delivery/V4-substitute.md`).
> 이 파일은 템플릿이다. `{ }`를 채우고, 해당 없는 칸은 `—`, 확인하지 못한 칸은 `UNKNOWN`으로 쓴다.
> **원칙**
> - 계약 원문은 `CONTRACT-BASELINE` / G-03 C-ID, 결정은 `DECISION-LOG`에 있다. 이 Sheet에는 **ID와 코드 위치만** 쓰고 내용을 복사하지 않는다.
> - 상태값은 `docs/STATUS-AXES.md`의 5개 축(① 근거 ② 정책 승인 ③ 문서 리뷰 ④ Test 준비 ⑤ Test 실행)을 쓴다. 축을 섞지 않는다. `REVIEW_ACCEPTED`는 `APPROVED`가 아니다.
> - ② `PROPOSED` 행은 **구현 지시가 아니다.** Decision Log에 `APPROVED` 행이 생기기 전에는 G절(Delivery)의 작업으로 옮기지 않는다.
> - Kotlin Migration의 완료 기준(E절)에 Improvement를 섞지 않는다.

## A. 기본 정보

| 항목 | 값 |
|---|---|
| Domain / Owner | {V번호 Domain} / {이름} (팀 확인 전이면 `초안`) |
| Java Baseline | {저장소}@{SHA} |
| Kotlin Target | {3차 Branch / PR} |
| 대상 Package | {`domain/...`} |
| 관련 계약 ID | {C-xx~C-yy, CB-xxx} |
| 관련 Decision / H 과제 | {DL-xxx, G03-D-xx, Hn, ML-xx} (상태 함께 표기) |
| 이 Sheet 상태 | DRAFT / REVIEWED (리뷰어, 일자) |

## B. 실제 구현 현황

### B1. API

| # | Method · Path (context-path `/api/v1` 제외) | 사용자 / 권한 | 성공 Status · code | 주요 실패 (Status · code) | 근거 (Controller:Line) | 상태 |
|---|---|---|---|---|---|---|

### B2. 구성 요소

| 계층 | Class (주요 메서드) | 비고 |
|---|---|---|
| Controller | | |
| Service | | Transaction 경계 표시 |
| Repository | | `@Lock`, `@Query` 표시 |
| Entity / Enum | | 상태 변경 메서드 |
| DTO | | Request Validation, Response Field |
| Event / Listener | | phase |

### B3. DB 제약 (실행 Evidence가 있는 것만 VERIFIED)

| 제약 | 근거 | 상태 |
|---|---|---|

### B4. 기획에만 있고 구현되지 않은 기능

| 기능 | 출처 | 상태 |
|---|---|---|

## C. 기존 정책 (보존 대상)

| 정책 ID | 규칙 요약 (한 줄) | 코드 근거 | 계약 ID | 검증 Test ID | 상태 |
|---|---|---|---|---|---|

### C1. 상태 전이

| 대상 | 허용 전이 | 금지 전이 | 근거 |
|---|---|---|---|

### C2. 역할·자격 Matrix (누가 무엇의 대상·실행자가 될 수 있는가)

| 행위 | 실행자 | 대상 자격 | 근거 | ① 근거 상태 |
|---|---|---|---|---|

### C3. Domain 간 영향

| 방향 | 내용 | 근거 |
|---|---|---|

## D. 기존 테스트 현황

자세한 행은 `docs/evidence/TEST-INVENTORY.md`의 이 Domain 구역에 둔다. 여기에는 요약만 쓴다.

| Test Class | 종류 · 환경 | 수 | 최근 실행 근거 | 검증하지 못하는 조건 |
|---|---|---|---|---|

## E. Kotlin Migration

| DoD 항목 | 이 Domain에서 주의할 점 | 완료 근거 (Test ID / 비교 방법) |
|---|---|---|
| 1. 기존 Test | D절 Test 전부 | ⑤ `PASSED` (Kotlin SHA, Test Report) + 기대값 변경 0 |
| 2. HTTP Status · JSON 동등성 | Status, `code`, Field 이름·순서·null 출력 | Controller/API Test 또는 Java·Kotlin 응답 비교 기록 |
| 3. Validation | 누락·null·형식 오류 → 같은 `COM-*` | 같음 |
| 4. JPA Entity · Proxy · Nullable | Lazy 연관, `id` `Long?`, 상태 Field `private set` | Repository·Integration Test |
| 5. Transaction 경계 | `@Transactional` 위치·readOnly·전파 동일 | Rollback Test |
| 6. Repository Query · Lock | `@Query` 문자열, `@Lock` 종류·호출 순서 동일 | Diff 검토 + MySQL Test |
| 7. Event 발행 시점 | 발행 위치·`AFTER_COMMIT` 동일 | 알림 Row Test |
| 8. DB Schema 동등성 | Kotlin 전환 전후 `ddl-auto: create` Schema 비교 (Column 타입·Unique·FK·ENUM 값) | Schema Evidence (T-02A 방식) |
| 9. 통합 Flow | H절 Flow가 끝까지 동작 | Flow Integration 또는 Smoke |
| 10. 신규 동작 | **0** (Improvement 미포함) | PR Diff |

## F. Improvement (승인 상태별로 분리)

| ID | 문제 / 개선 | 상태 | 영향 (상태 전이·동시성·DB·알림·API) | 필요한 승인 | 추가 Test ID |
|---|---|---|---|---|---|

## G. Delivery

| 항목 | 내용 |
|---|---|
| 선행 작업 | |
| 작업 단위 (WBS / PR) | Migration PR과 Improvement PR을 분리해서 적는다 |
| 담당 | |
| 완료 기준 | |
| Test Evidence | `TEST-EVIDENCE` EV-ID |
| 남은 Blocker | |

## H. 사용자 Flow 완결성 (이 Domain이 포함된 Flow)

| 단계 | 사용자 행동 | API | 결과 상태 | 다음 행동 |
|---|---|---|---|---|
