# G03-D-19 대타 요청 만료 표현 (Expiry)

| 축 | 상태 |
|---|---|
| 결정 단계 | 제안 (작성자 검토용 초안 보존). 사용자 잠정 결정 문장은 아직 확인 전 |
| ② 정책 | `PROPOSED`. `DECISION-LOG` G03-D-19는 `PENDING` |
| ③ 문서 | `DRAFT` |
| 구현 | 없음 |
| ④ / ⑤ | 신규·변경 Test 전부 `SPECIFIED` / `NOT_RUN` |
| 기준 코드 | 3차 `docs/switch-v3-foundation` `0071db3` (Production Java = `cdaaa13`). 정적 확인 |
| 관련 ID | G03-D-19, G03-D-20, G03-D-26, G03-D-27, D-NOW-04, V4-P10, C-45 |
| 주관 / 리뷰 | V4 Owner (주관), Frontend, 공통 설정 Owner |

> 2026-10-23 작성자 로컬 대화에서 작성한 초안을 옮겼다. 대화 형식 문장(도입·맺음)만 뺐고 판단 내용은 바꾸지 않았다.
> 본문의 "추천"은 작성 당시 제안이다. 결정이 아니다.
> 2차 명세(API-SUB, API-SCH, P0) 쪽수는 외부 원본 기준이다. 원본은 저장소에 없다 (DL-005).

## 1. 결정 배경과 기존 코드

| 구분 | 내용 | 근거 |
|---|---|---|
| Java AS-IS (① `VERIFIED_AS_IS`, 정적) | 활성 Request = OPEN/ACCEPTED ∧ `startAt > now`. 모든 쓰기 API와 SUB-02·06·07 목록이 같은 기준을 씀 | `SubstituteRequestService` L148·L298·L330·L396, `SubstituteCandidateService` L200·L264, Repository 목록 Query `startAt > :now` |
| Java AS-IS | 만료는 DB에 기록하지 않음. DB `closedAt`은 명시적 종료 3곳에서만 기록 | `SubstituteRequest` L73·L87·L95 |
| Java AS-IS | SUB-05만 응답을 CLOSED/EXPIRED로 바꾸고 `closedAt`에 **조회 시각**을 넣음 | `SentSubstituteRequestResponse` L28~52 |
| Java AS-IS | 공개된 Shift의 시간을 바꾸는 경로가 없음 → `startAt`이 안정적 | `ShiftService.validSchedule` L198~212 (DRAFT가 아니면 거부) |
| 2차 명세 원문 | SUB-02·03·04·05·07: 만료 Request는 "CLOSED, EXPIRED, closedAt = now **처리 대상**". 실행 방식(Scheduler/Lazy/Hybrid)은 구현 단계에서 결정 | API-SUB p.29·45·72·93·127 |
| 2차 명세 원문 | SUB-05 `closedAt` = "lifecycle 종료 시각". 상태 Matrix "CLOSED → closedAt ≠ null" | API-SUB p.95·96 |
| P0 가이드 | 시작 시각까지 미확정이면 UNFILLED. 자동 처리는 **P2** | P0 §17·§26 |
| 2차 명세 원문 | PUBLISHED 일반 수정/취소는 P1 범위 | API-SCH SCH-02 p.20, SCH-03 p.33, SCH-04 p.45, SCH-06 p.85 |
| 과거 검토 제안 | G-03이 C-45에서 Expiry 표시를 떼어 G03-D-19 `DEFER_PRODUCT_DECISION`으로 분류 | G-03 §3.7.2, §7.2 |
| 작성자 기억 (미확정) | 시간 기반 논리적 만료가 원래 설계 의도 | 작성자 로컬 조사 P-D01-1A |
| 팀 승인 | **없음** (`DECISION-LOG` G03-D-19 `PENDING`) | — |
| 실행 근거 | `SentSubstituteRequestResponseTest` 6건은 2차 `243f77c`에서 개별 `PASSED`(EV-002). 3차 `cdaaa13`은 CI Suite 성공만(EV-008, 개별 `UNKNOWN`). 이 초안 작성 때 실행하지 않음 | `TEST-EVIDENCE` |

## 2. 초기 명세와 현재 구현의 차이

1. 명세는 만료를 **저장된 상태 전이**로 보고, Java는 **조회할 때만 바꿔서 보여 준다.**
2. 명세의 "closedAt = now"는 전이를 실제로 처리한 시각을 뜻한다. Java는 처리 자체가 없어서 **조회한 시각**을 넣는다. 같은 요청을 언제 조회하느냐에 따라 값이 달라진다.
3. 화면에 보이는 결과는 명세와 같다. 만료 요청은 SUB-02·06·07 목록에서 빠지고, SUB-05에서는 CLOSED/EXPIRED로 보이며, 쓰기 API는 거부한다.

## 3. 선택지 비교

| 기준 | A 현행 | **B1 논리적 만료 + `closedAt = startAt`** | B2 논리적 만료 + `closedAt = null` (필요하면 DTO 계산 `expiredAt`) | C 실제로 저장 (Lazy / Scheduler) |
|---|---|---|---|---|
| 데이터 의미 | 조회할 때마다 바뀌는 값 | 판정 경계와 같은 고정값 | DB와 같음. 단 "CLOSED이면 closedAt ≠ null" 규칙과 어긋남 | 저장 시각 (실행이 지연된 만큼 `startAt`보다 늦을 수 있음) |
| API 호환 | 변경 0 | 만료 응답의 `closedAt` **값만** 바뀜 | null이 됨. `expiredAt`을 넣으면 필드 추가 | 형식 동일, DB 의미 변경 |
| DB Schema | 없음 | 없음 | 없음 | 없음으로 추정 (운영 DB 미확인) |
| Frontend | 없음 | 없음 | `expiredAt`을 넣을 때만 | 없음 |
| 동시성 | 없음 | 없음 | 없음 | 승인·종료와 경쟁 (H6) |
| 알림·통계 | 불가 | 불가 | 불가 | 가능 (G03-D-20) |
| 바꿀 Test | 0 | 기대값 1줄 | 1줄 이상 | 다수 |
| MVP 필요성 | — | 낮은 비용의 정정 | 낮음 | P2 |

## 4. B1 추천 이유와 포기하는 장점

- **추천 이유:**
  - 작성자가 기억하는 설계 의도와 모든 활성 판정 경로에 맞는다.
  - 조회 때마다 값이 바뀌는 문제를 1줄로 없앤다.
  - 명세의 "CLOSED → closedAt ≠ null"을 유지한다.
  - DB, Frontend, 동시성 영향이 0이다.
  - B1의 값(`startAt`)은 나중에 C를 도입해도 "이상적인 만료 시각"과 같다.
- **포기하는 것:**
  - 만료를 DB에서 집계하거나 `SUBSTITUTE_EXPIRED` 알림을 보낼 수 없다 (C의 장점).
  - 명세 문장 "closedAt = now"를 글자 그대로 따르지 않고 "업무상 종료 효력 시각"으로 해석한다. **이 해석 자체가 승인 대상**이다.

## 5. API·DB·Frontend·Notification 영향

- **API:** GET `/substitute-requests/sent`에서 만료 요청의 `closedAt` 값만 바뀐다. status, closeReason, 필드 이름·타입·null 여부는 같다. 다른 Endpoint는 영향이 없다.
- **DB:** 없다. 실제 DB 종료 시각(`closedAt` Column)은 명시적 종료에만 쓰인다. API가 보여 주는 업무상 종료 효력 시각은 만료일 때 DTO에서 계산한다.
- **Frontend:** 필수 변경 없다. 지금 `sent/page.tsx` L133~149는 만료의 종료 날짜를 일부러 숨긴다. B1을 적용하면 이것을 보여 주는 선택지가 생긴다.
- **Notification:** 없다. 만료 알림은 G03-D-20에서 따로 다룬다.

## 6. 시간 경계와 예외 처리

- **경계:** `now ≥ startAt`이면 만료다 (`now == startAt` 포함). 시계는 `Clock`(Asia/Seoul) 하나다. 쓰기 API가 거부하는 경계와 같다.
- **적용 대상:** OPEN·ACCEPTED만. APPROVED·CLOSED는 DB 값 그대로 응답한다.
- **Shift 취소(CANCELLED):** 지금은 쓰는 경로가 없어 해당 없다. 도입하면 SUB-05가 Shift 상태를 걸러내지 않으므로 시작 전까지 OPEN으로 보인다 (G03-D-26과 연결).
- **B1이 성립하는 전제:** 공개된 Shift의 `startAt`이 바뀌지 않는다는 것. G03-D-27과 연결된다 (§11).

## 7. 최소 Acceptance Criteria와 Test

| AC | 조건 | Test |
|---|---|---|
| AC1 | OPEN이고 `now > startAt` → CLOSED/EXPIRED, `closedAt == startAt` | `SentSubstituteRequestResponseTest.test2` L53 기대값 `now` → `SHIFT_START` (**기대값 변경, 승인 필요**, `PROCESS-CHARTER` §6-2) |
| AC2 | ACCEPTED이고 `now == startAt` → 만료, `closedAt == startAt` | test3에 `closedAt` 확인 추가 |
| AC3 | 같은 요청을 다른 두 시각에 조회해도 `closedAt`이 같음 | 신규 단위 Test 1건 |
| AC4 | `now < startAt` → 원래 status, `closedAt = null` | test1 (기존) |
| AC5 | APPROVED·CLOSED는 저장된 값 그대로 | test4·5 (기존) |
| AC6 | 조회한 뒤에도 DB row가 OPEN/ACCEPTED, `closedAt = null` 그대로 | 신규 MySQL Integration 1건 (권장) |
| AC7 | 서비스 목록에서도 같은 값 | `SubstituteRequestServiceTest` L1533~1564에 `closedAt` 확인 추가 |
| AC8 | 만료 요청은 SUB-02·06·07 목록에서 빠지고 쓰기 API는 거부 | 기존 동작 (회귀만 확인) |

모든 AC는 ④ `SPECIFIED`다. 실행 전에는 ⑤ `PASSED`로 쓰지 않는다. 이 AC는 승인 전이라 `TEST-INVENTORY`에 아직 등록하지 않았다.

## 8. 적용 시점

| 선택지 | 장점 | 단점 |
|---|---|---|
| Java 선수정 (D-NOW-04) | Kotlin 비교 기준이 처음부터 B1 | Java Baseline이 다시 바뀌어 `BL-TBD` 재선언 필요 |
| **Kotlin 전환 후 별도 Improvement PR (추천)** | AI-RULES §2를 지킴: Migration PR은 A를 그대로 옮김 | 전환 기간 동안 A 유지 |

## 9. 리뷰어와 승인 질문

- **리뷰어** (실명 미지정, D-NOW-01): V4 Owner, Frontend 담당 (보낸 요청 화면), 공통 설정 Owner (기대값 변경 승인 경로).
- **승인 질문:**
  1. 논리적 만료를 유지하고 저장(C)과 만료 알림을 MVP 밖으로 둘지
  2. 만료 응답 `closedAt`의 의미를 "업무상 종료 효력 시각 = `startAt`"으로 할지 (명세 문장의 해석)
  3. test2 기대값 변경을 승인할지
  4. 적용 시점 (Kotlin 전환 후 별도 PR인지)

## 10. 남은 위험과 후속 결정

- G03-D-27(공개 후 정정)을 도입하면 B1의 전제가 흔들린다 (§11).
- G03-D-20(만료 알림)과 C(저장)는 운영 통계가 필요해지면 다시 검토한다.
- G03-D-26(Shift 취소)을 도입하면 SUB-05가 Shift 상태를 걸러낼지 정해야 한다.
- 퇴사로 취소된 근무의 시작 전 요청은 퇴사 Transaction에서 닫히는 것으로 제안돼 있다 (`docs/references/ml/ML-DECISION-01-FINAL.md`). 둘의 경계는 ML-3 카드에서 확인한다.

## 11. G03-D-27과 연결되는 선행 제약 (확정하지 않음)

G03-D-27을 채택할 때 검토할 조건이다.

1. **공개 → DRAFT 재전환 금지:** 원문에 기록돼 있다 (P0 §3).
2. **시작 전 정정과 시작 후 이력 정정을 별도 기능으로 구분:** 기록 없음, 새 추천이다.
3. **시각 유효성:** 기존 `startAt > now`, 새 `startAt > now`, `startAt < endAt`, 같은 날짜, 주차 범위 안. 범위 규칙은 API-SCH SCH-02 p.21~22와 같은 기준이다.
4. **활성(OPEN/ACCEPTED) Request가 있을 때의 시간·담당자 변경:** 수정을 거부하거나, 같은 Transaction에서 Request를 종료한다. P0 §16 "Shift 담당자/시간이 달라짐 → 기존 승인 불가"를 지킬 장치가 지금은 없다 (수락 당시 시간을 저장하지 않음).
5. **과거 CLOSED/EXPIRED 요청의 만료 이력 보존:** 조건 2·3이 지켜지면 B1의 `startAt`은 바뀌지 않는다.
6. **과거 APPROVED 요청의 수락 조건·승인 이력 보존:** 조건 3만으로는 충분하지 않다.
   - 승인된 Shift가 아직 시작 전이면, 조건 3을 만족한 채로 시간을 바꿀 수 있다.
   - 그러면 그 APPROVED Request가 화면에 새 시간을 보여 준다. DTO가 Shift를 실시간으로 읽기 때문이다.
   - 수락자가 옛 시간에 동의했다는 기록도 남지 않는다.
   - 해결하려면 승인 당시 시간 Snapshot, 별도 이력, 또는 "APPROVED 이력이 있는 Shift는 정정 제한" 중 하나가 필요하다. 근거 없이 확정하지 않는다.
7. **Shift 취소:** 삭제가 아닌 상태 변경(CANCELLED)으로 하고 관련 Request를 종료한다. 삭제하면 `shift_id`(nullable=false FK)가 깨진다.
8. **승인과 정정의 동시성:** 승인은 Request·User Row만 잠그고 Shift·Schedule은 잠그지 않는다. 정정을 도입하면 공통 Lock 지점과 Lock 순서가 필요하다 (H6, 실행 검증 없음).
9. **Frontend·Notification:** 근무표 화면의 DRAFT 전용 편집 조건 7곳을 바꿔야 하고, 정정·취소 알림 Type이 필요하다 (지금 8종에 없음).

## 12. 승인 전 추가로 확인할 사실

1. 운영 DB의 `substitute_requests.close_reason` 컬럼 형식(ENUM 값 목록)과 만료처럼 보이는 OPEN/ACCEPTED row 수. C를 고를 때만 필요하다. 운영 DB 접근은 금지이므로 담당자 확인이 필요하다.
2. `SentSubstituteRequestResponseTest`와 `SubstituteRequestServiceTest`의 해당 Test를 3차 HEAD에서 실제로 실행해 현재 기대값이 통과하는지 (기준선 Evidence).
3. API-SUB 원본(Notion)을 고칠 권한과 담당자. "closedAt = now"의 해석을 명세에 반영할 수 있는지.

## 13. 승인 후 바꿀 문서·Test·코드

| 대상 | 항목 |
|---|---|
| `DECISION-LOG` | §2 G03-D-19 행: 결정 내용, 승인자, 일자. G03-D-27 행에 §11 선행 제약 참조 추가 |
| `CONTRACT-BASELINE` | 지금 SUB-05 행 없음 → **Java AS-IS 행**(현행 A)과 **승인된 계약 행**(B1, `APPROVED`)을 구분해 추가 |
| V4 Delivery Sheet | V4-P10 규칙 문장, C·F절, V4-T-02 기대값 비고 |
| API-SUB (외부 원본) | SUB-05 `closedAt` 정의와 만료 처리 문단 (p.93·95) |
| `TEST-INVENTORY` | V4-T-02·03 행 기대값, AC3·AC6 신규 행 (④ `SPECIFIED`) |
| `TEST-EVIDENCE` | 실행한 뒤 새 EV 추가. 기존 EV는 바꾸지 않음 |
| `MIGRATION-MATRIX` | V4 행에 "Migration은 A 보존, B1은 별도 Improvement" 비고 |
| 코드 | `SentSubstituteRequestResponse` 만료 분기의 `closedAt` 인자 1곳 |
| Test | test2 기대값, test3·Service Test에 확인 추가, 신규 2건 |

추천 적용 순서: 팀 리뷰(§9, §12의 1·3) → `DECISION-LOG` 승인 기록 → 기준선 Test 실행과 EV 기록 → Kotlin Migration은 A를 그대로 옮김 → 별도 Improvement PR (B1) → G03-D-27 채택 시 §11 제약을 결정 조건으로 붙임.

## 14. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-23 | 작성자 로컬 대화의 초안을 옮김. 대화 문장 제거. 도입 문단의 API-SCH 근거를 §1 표의 행으로 옮김. 상태 표, §10 마지막 항목(ML 연결) 추가 |
