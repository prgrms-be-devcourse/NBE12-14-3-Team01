<!-- 참고 자료 원문 보존본. 머리말 아래 본문은 대화 Log 원문 그대로이며 수정하지 않았다. 본문 SHA-256: 58e23d1a33d925c70e9f457bf67e6e049007e09cb419ef4d8d2c06c01d336e67 -->

# ML-SUB-STATE-01 — 대타 후보 자격 상실 상태 모델 (B+)

> **과거 제안 원문이다. 현행 계약이 아니며, 팀 승인 전이다.**
> - ② 정책: `PROPOSED` (원문 작성 시점). `DECISION-LOG`의 관련 행(G03-D-11~16·26·27)은 모두 `PENDING`이다. 구현 지시가 아니다 (`docs/ai/AI-RULES.md` §3).
> - 출처: 작성자 로컬 AI 대화 Log (Claude Code 세션, 2차 분석 작업공간), 1-based line 9601, 2026-10-22T08:24:40.093Z. Log 파일은 저장소에 없다 (`docs/references/README.md` §6).
> - 작성 기준 코드: 2차 `origin/develop` `8da2bec` (Production Java는 3차 `develop`과 같다). 정적 분석이며 Test는 실행하지 않았다.
> - 본문 안의 "추천", "MUST", "정합니다" 같은 표현은 작성 당시 AI 제안의 어조다. 결정이 아니다.
> - 이후 관계 (본문을 읽기 전에 확인):
>   - 직전안: ML-SUB-DECISION (B안) → ML-SUB-STATE-DECISION-R (B+ `YES_WITH_CONDITIONS`) → 이 문서가 마감 검증본
>   - 작성자 잠정 정책의 "새 Enum은 필요성 입증 전 추가 금지" 원칙과 긴장 관계다. 필요성 논증은 이 원문 안에 있다
>   - 원문 내부 ID `INV-C1~C4`는 이 문서 안에서만 쓰는 이름이다. `ML-H1-INV-*`, `G03-INV-*`와 다르다
>   - 후속 카드 ML-3에서 다시 결정 (아직 카드 없음). 운영 MySQL ENUM DDL·Rollback 위험이 열려 있다

---

**판정: B+ YES_WITH_CONDITIONS (유지 가능).** 이번 검증에서 고칠 것은 문장 세 개입니다.

1. 이전 보고의 **"종료 사유는 처리 순서와 무관하다"는 표현이 부정확했습니다.**
   - 정확한 표현은 이렇습니다. 사유는 **종료하는 순간의 Candidate 구성**으로 정합니다. 요청이 OPEN인 동안 일어난 사건끼리는 순서가 바뀌어도 사유가 같습니다. 종료 이후의 사건은 소급하지 않습니다.
2. 종료 판단에 **실행할 수 있는 조건(사전조건)**을 붙입니다.
3. 종료할 때 퇴사자의 PENDING을 함께 `INELIGIBLE`로 정리합니다.

이번에도 소스만 확인했고 Test는 실행하지 않았습니다 (`origin/develop` `8da2bec`).

## 1. 종료 순서 반례 판단
| 예시 | 사건 순서 | 최종 사유 | 판단 |
|---|---|---|---|
| A | X, Y가 직접 거절 → Request `ALL_CANDIDATES_REJECTED`로 종료 → 그 뒤 Y 퇴사 | `ALL_CANDIDATES_REJECTED` | **정상.** 종료 시점에 후보 전원이 실제로 거절했으므로 사실과 맞습니다. 퇴사는 CLOSED Request를 바꾸지 않습니다 (금지 전이) |
| B | Y 퇴사 → Y `INELIGIBLE`, OPEN 유지 → X 직접 거절 → 종료 | `CANDIDATE_INELIGIBLE` | 정상. 종료 시점의 구성이 {`REJECTED`, `INELIGIBLE`} |
| B' | X 직접 거절 (Y가 PENDING이라 OPEN 유지) → Y 퇴사 → 종료 | `CANDIDATE_INELIGIBLE` | B와 같음. **OPEN 동안 일어난 같은 사건 묶음이면 순서와 무관** |

A와 B의 사유가 다른 것은 정상입니다. 종료 시점에 이미 일어난 사건이 서로 다르기 때문입니다. 두 원칙은 이렇게 구분합니다.
- **(1) 소급 금지:** CLOSED·APPROVED Request와 그 Candidate는 이후 사건으로 바꾸지 않습니다.
- **(2) 구성 기준 결정:** 종료하는 순간 해당 Request의 Candidate 상태 구성만으로 사유를 정합니다.

## 2. 수정이 필요한 정책 문장
- **(기존)** "종료 사유는 처리 순서와 무관하다"
  → **(수정)** "종료 사유는 종료 시점의 Candidate 구성으로 결정한다. Request가 OPEN인 동안 발생한 사건의 순서는 사유에 영향을 주지 않는다. 종료 이후의 사건은 종료 사유와 Candidate를 소급 변경하지 않는다."
- **(기존)** "응답 가능 후보가 0이 되면 종료"
  → **(수정)** "종료 판단은 **다음 두 Command 안에서만**, Request Lock을 잡은 뒤 실행한다: ① 후보 직접 거절(`rejectCandidate`), ② 퇴사 처리 중 퇴사자가 해당 Request의 PENDING·ACCEPTED 후보인 경우. 실행 전에 다음 사전조건을 모두 만족해야 한다: Request가 OPEN(①, ② PENDING) 또는 ACCEPTED(② 수락자), 근무가 PUBLISHED ∧ SCHEDULED ∧ `startAt > now`. 하나라도 어기면 종료 판단을 하지 않는다."
  - **효과:** 근무가 시작됐거나, 취소됐거나, 이미 종료돼서 0명이 된 경우는 `CANDIDATE_INELIGIBLE`로 잘못 종료되지 않습니다.
  - 근무 시작 이후는 기존처럼 EXPIRED로 계산합니다.
- **(추가)** "종료할 때 같은 Request에 **퇴사한 Membership의 PENDING**이 남아 있으면 `INELIGIBLE`로 함께 바꾼다 (시각은 해당 `leftAt`)."
  - 동시 실행으로 남은 PENDING이 종료된 Request에 그대로 남지 않게 하기 위함입니다.
  - 이 문장이 있어야 INV-S3이 지켜집니다.

**응답 가능 후보의 정의:** 위 사전조건을 이미 확인했다면 같은 Request 안에서 `PENDING ∧ member.leftAt IS NULL`인 후보만 셉니다.

**조회 필터만으로 해결되는 것 / 안 되는 것**

| 해결됨 (기존 필터, VERIFIED_AS_IS) | 해결 안 됨 |
|---|---|
| 퇴사자는 받은 요청 목록에서 빠짐 (`c.member.leftAt IS NULL`, L59) | 퇴사자 PENDING이 **유일한 후보**인 Request는 종료를 일으킬 사건이 없어 OPEN으로 남음 |
| 퇴사자 응답 거부 (`SUB-021`, L136) | 그 동안 요청자의 보낸 요청에는 OPEN으로 보이고 **재요청이 막힘** (`ACTIVE_REQUEST_EXISTS`) |
| 승인 시 수락자 `leftAt` 검사 (L278) | 관리자 진행 중 목록에도 OPEN으로 남음 |
| | 관리자 close로만 회복 가능 → ML-CONCURRENCY-01 |

## 3. ACCEPTED 수락자 퇴사 검증 (VERIFIED_AS_IS 근거)
| 확인 항목 | 결과 | 근거 |
|---|---|---|
| 승인 API가 거부하는가 | 거부함. `status != ACCEPTED`이면 `INVALID_REQUEST` | `SubstituteRequestService` L241 |
| 요청자 화면 | `CLOSED` + `closeReason` + `closedAt`. 이 응답에는 수락자 정보가 없음 | `SentSubstituteRequestResponse` L10~24 |
| 관리자 화면 | 진행 중 목록은 OPEN·ACCEPTED만 → 사라짐. 수락자 표시는 ACCEPTED일 때만 | Repository L42, Service L174 |
| 이전 수락자를 현재 담당자로 오인하는가 | 오인하지 않음. 승인이 없었으므로 `Shift.member`는 원래 요청자 그대로 | `approveRequest`만 `changeMember` |
| 이전 수락자가 다른 대타를 막는가 | 막지 않음. 확정 대타 충돌 Query가 `c.request.status = 'ACCEPTED'`를 요구 → 종료 후 제외됨 | `SubstituteCandidateRepository` L26~37 |
| 요청자가 재요청할 수 있는가 | 가능함. 활성 요청 검사는 OPEN·ACCEPTED만 | L153 |
| 새 후보에 퇴사자가 들어가는가 | 들어가지 않음. 후보는 `findAllByWorkplace_IdAndLeftAtIsNull`에서 고름 | `SubstituteCandidateService` L48 |
| 중복 알림 | **설계로 막아야 함 (PROPOSED).** `CANDIDATE_INELIGIBLE` 종료는 새 종료 이벤트 1개만 발행하고 `MANAGER_CLOSED` 이벤트는 발행하지 않음. 수신자는 요청자 + 현재 관리자 | — |

## 4. Candidate / Request 최종 상태 전이표 (PROPOSED_TO_BE. 기존 행은 VERIFIED_AS_IS)
**Candidate**

| 전이 | 주체 | 사전조건 | `respondedAt` |
|---|---|---|---|
| `PENDING → ACCEPTED` (기존) | 후보 본인 | 현재 EMPLOYEE, Request OPEN, 근무 시작 전·PUBLISHED·SCHEDULED, 충돌 없음 | 응답 시각 |
| `PENDING → REJECTED` (기존) | 후보 본인 | 위와 같음 (충돌 검사 제외) | 응답 시각 |
| `PENDING → INELIGIBLE` (신규) | 퇴사 처리 | 대상 Membership 종료, Request OPEN·ACCEPTED, 근무 시작 전 | null (시각은 `leftAt`) |
| `PENDING → INELIGIBLE` (신규, 정리) | 종료 판단 | 종료 시점에 같은 Request의 퇴사자 PENDING | null |
| 그 밖의 전이 | — | **금지** (`ACCEPTED → INELIGIBLE` 포함) | — |

**Request**

| 전이 | 주체 | 사유 |
|---|---|---|
| `OPEN → ACCEPTED`, `ACCEPTED → APPROVED` | 기존 | — |
| `OPEN|ACCEPTED → CLOSED` | 관리자 close API, 또는 퇴사 처리에서 요청자 퇴사 | `MANAGER_CLOSED` |
| `OPEN → CLOSED` | 종료 판단 (응답 가능 0 ∧ 후보 전원 `REJECTED`) | `ALL_CANDIDATES_REJECTED` |
| `OPEN → CLOSED` | 종료 판단 (응답 가능 0 ∧ `INELIGIBLE` 1명 이상) | `CANDIDATE_INELIGIBLE` |
| `ACCEPTED → CLOSED` | 퇴사 처리 (수락자 퇴사) | `CANDIDATE_INELIGIBLE` |
| `CLOSED`·`APPROVED`에서의 전이 | — | **금지** |
| EXPIRED | 저장하지 않음 | 계산값 |

## 5. 구현 전 조건 (중요도 순 3개)
1. **운영 DB 확인과 DDL·Rollback 절차 승인**
   - 확인할 것: 실제 Table·Column 이름, candidate status·close_reason(+ 알림 type)이 MySQL `enum`인지, 현재 값 목록
   - 절차: 배포 전 수동 ALTER → 앱 배포 순서
   - **새 값이 저장된 뒤 이전 버전 앱으로 되돌리면 Enum 변환이 실패해 500이 납니다.** 그 경우의 데이터 처리 방안이 필요합니다.
   - 담당자를 지정해야 합니다.
2. **종료 판단을 위 두 Command로 한정하고, 사전조건과 퇴사자 PENDING 정리 문장을 함께 승인**
   - 기존 `rejectCandidate`의 종료 부분을 공통 판단으로 교체합니다.
   - 지금 도달할 수 있는 모든 상태에서는 기존 C-47 결과와 같아야 하며, 회귀 Test로 확인합니다.
3. **새 알림 Type 결정과 Frontend 반영**
   - 알림: 새 Type은 "대체 근무를 진행할 수 있는 후보가 없어 요청이 종료되었습니다. 필요하면 다시 요청해 주세요."이고, 수신자는 요청자 + 현재 관리자입니다.
   - Frontend: 보낸 요청 화면에 label을 추가하고 `getDateLine`의 종료일 조건에 `CANDIDATE_INELIGIBLE`을 넣습니다. 지금은 "종료됨"으로만 보이고 종료일은 표시되지 않습니다. `closedAt`은 이미 DTO에 있습니다.
   - Kotlin 전환 PR과 분리한 Improvement PR로 진행합니다.

## 6. 필수 테스트 8개 (미실행, 모두 `Clock.fixed`, 근무는 시작 전)
| # | 입력 | 실행 | 예상 DB 상태 |
|---|---|---|---|
| 1 | 후보 X·Y PENDING, Y 퇴사 | 퇴사 | Y `INELIGIBLE`(`respondedAt` null), Request OPEN, 알림 0 |
| 2 | 1 이후 X 직접 거절 | `rejectCandidate` | X `REJECTED`, Request `CLOSED` / `CANDIDATE_INELIGIBLE`, `closedAt` = Clock 값, 종료 알림 1건 |
| 3 | X 거절 후 Y 퇴사 (2의 역순) | 거절 → 퇴사 | 2와 같은 최종 상태 |
| 4 | X·Y 모두 직접 거절 → 이후 Y 퇴사 | 거절 2회 → 퇴사 | `ALL_CANDIDATES_REJECTED` 유지, Y `REJECTED` 유지 (소급 없음) |
| 5 | X `ACCEPTED`, Y PENDING, X 퇴사 → 승인 시도 → 요청자 재요청 | 퇴사 → 승인 → 대타 생성 | X `ACCEPTED` 유지, Request `CANDIDATE_INELIGIBLE`, 승인은 `INVALID_REQUEST`, `Shift.member` = 요청자, 새 Request 201이고 후보에 X 없음, 종료 알림 1건 (`MANAGER_CLOSED` 알림 0) |
| 6 | 요청자 퇴사 (OPEN, 후보 2) | 퇴사 | Request `MANAGER_CLOSED` → 근무 `CANCELLED`, Candidate 변경 없음 |
| 7 | `startAt == now`, Y PENDING, Y 퇴사 | 퇴사 | Candidate·Request 변경 없음, 보낸 요청 응답은 `CLOSED` / `EXPIRED` |
| 8 | 경쟁 재현: Y 퇴사 Commit 후 Y PENDING Row를 직접 만들고 X가 거절 | 거절 | Request `CANDIDATE_INELIGIBLE`, Y `INELIGIBLE`로 정리 (응답 가능 수 기준 + 정리 문장 검증) |

회귀: 기존 대타 Test의 기대값 변경 0. 4번은 C-47 회귀를 겸합니다.

## 7. ML-SUB-STATE-01 Decision Card 최종 문구 (PROPOSED)
> **ML-SUB-STATE-01 (ML-Q11 / G03-D-15, D-17 / C-41 확장, C-47 정밀화):**
> 대타 후보가 응답하기 전에 Membership 종료로 자격을 잃으면 Candidate를 `INELIGIBLE`로 기록한다. `respondedAt`은 본인의 실제 수락·거절 시각에만 쓴다.
> 이미 수락한 후보가 퇴사하면 Candidate `ACCEPTED`를 보존하고 Request를 `CLOSED / CANDIDATE_INELIGIBLE`로 종료한다.
> 종료 판단은 후보 직접 거절과 퇴사 처리 안에서만, Request Lock 이후 사전조건(Request OPEN 또는 ACCEPTED, 근무 PUBLISHED ∧ SCHEDULED ∧ `startAt > now`)을 만족할 때 실행한다.
> 현재 구성원인 PENDING 후보가 0이면 종료하며, 사유는 종료 시점의 구성으로 정한다: 전원 `REJECTED`면 `ALL_CANDIDATES_REJECTED`, `INELIGIBLE`이 하나라도 있으면 `CANDIDATE_INELIGIBLE`.
> 종료할 때 같은 Request에 남은 퇴사자 PENDING은 `INELIGIBLE`로 정리한다. 종료된 Request와 그 Candidate는 이후 사건으로 소급 변경하지 않는다.
> 요청자가 퇴사하면 `MANAGER_CLOSED`. 근무가 시작된 요청은 저장 상태를 유지하고 EXPIRED로 계산한다.
> `CANDIDATE_INELIGIBLE` 종료는 요청자와 현재 관리자에게 종료 알림 1건만 보낸다.
> 운영 MySQL ENUM 확장 DDL, Rollback 절차, Frontend label 반영을 구현 조건으로 한다. Kotlin Migration과 분리한 Improvement PR로 진행한다.
> 상태: **PROPOSED**.

## 8. ML-CONCURRENCY-01로 넘길 불변식
- **INV-C1:** Membership 종료가 Commit된 뒤에는, 그 Membership이 근무 시작 전 OPEN·ACCEPTED Request의 **새** PENDING·ACCEPTED 후보가 되지 않는다.
  - 대상 경로: 대타 요청 생성, 후보 수락
- **INV-C2:** 같은 Request에서 직접 거절과 퇴사가 동시에 실행돼도 Request 종료는 최대 1회, 종료 알림도 최대 1건이다. 사유는 종료 시점의 구성 규칙과 일치한다.
- **INV-C3:** 퇴사와 승인이 동시에 실행되면 둘 중 하나만 반영된다.
  - 승인이 먼저면: `Shift.member` = 수락자이고, 그 근무는 퇴사 규칙(ML-DECISION-01)으로 처리된다.
  - 퇴사가 먼저면: 승인은 거부되고 `Shift.member`는 바뀌지 않는다.
- **INV-C4:** 근무 시작 전 OPEN Request에는 현재 구성원인 PENDING 후보가 1명 이상 있다. 예외는 INV-C1이 보장되기 전에 남을 수 있는 "퇴사자만 후보인 요청"이며, 이 위험은 측정 대상으로 기록한다.
