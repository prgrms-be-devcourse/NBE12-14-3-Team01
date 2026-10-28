# ML-3 퇴사·공개 근무 변경과 대타 요청·후보 정합성

> **현재 승인 상태 (2026-10-26):** 사용자 전달 구두 승인과 PR 리뷰에 따른 채택 범위·예외는 [Decision Log](../DECISION-LOG.md) §4를 따른다. 아래 최초 작성 당시 PROPOSED/PENDING 표기는 이력이며 현재 승인 범위를 덮어쓰지 않는다. 구현·실험·배포 및 미결정 사항은 각각 별도 상태다.

| 축 | 상태 |
|---|---|
| 결정 단계 | **사용자 잠정 결정** (2026-10-23). 팀 승인 전 |
| ② 정책 | 카드 `PROPOSED`. `DECISION-LOG` G03-D-11·D-15·D-17·D-18·D-22 모두 `PENDING` |
| ③ 문서 | `DRAFT` (저장소 Proposal 문서. 2026-10-25 이관, 원본 로컬 `drafts/D4-ML-3/`) |
| 구현 | **근무 변경 시 대타 종료 구현 (2026-10-28, PR #80 머지)**. V3 공개 근무 취소(PR #87)·담당자 변경(`b411f2e`)이 이 함수를 호출. **퇴사자 후보 처리 구현 (2026-10-29, `feat/v4-leave-candidate-ineligible` `9e80e5a`)**: ML-3-P·P1·A와 거절 경로 종료 판정. V2 퇴사 확정 연결은 없음. 세부는 끝의 "V4 구현 기록" 절들. Kotlin Migration PR과 분리된 Improvement |
| ④ / ⑤ | V4-N-08·09(V4 함수 부분)·10·11·17·19·20, V4-N-55~59 `IMPLEMENTED` / `LOCAL_PASSED` (EV-034, TEST-INVENTORY "V4 근무 변경 시 대타 종료" 절). V4-N-13~16·22, H6-C5·C6, V4-N-65~71 `IMPLEMENTED` / `LOCAL_PASSED` (EV-038, TEST-INVENTORY "V4 퇴사자 후보 처리" 절). 나머지 신규 Test `SPECIFIED` / `NOT_RUN` |
| 기준 코드 | 3차 `develop` `31ecaee` (PR #5 병합 후. Production은 `c0427c2`와 같고 Substitute Test만 변경). 정적 확인 |
| 이관 당시 develop HEAD | `a148a62` (2026-10-25). `backend/src/main`은 `cdaaa13` 이후 변경 0 (이관 시 diff로 확인) |
| 코드 근거 재검증 | **NO** (S2-1은 이관만 함. 본문의 줄 번호·Test 근거는 위 "기준 코드" 시점 값). 이후 검증 예정: S2-2~S3 |
| 관련 ID | G03-D-11 (퇴사 채택, 전제), **G03-D-15** (Substitute 관련 Membership Lifecycle), G03-D-17 (Request 종료 상태·어휘), G03-D-18 (재OPEN), G03-D-22 (알림 유실), C-42~C-50, V4-P05~P08, V4-N-06·N-07 (Inventory의 PROPOSED 자리) |
| 앞선 카드 | [ML-H1](ML-H1-membership-history.md) (PR #6), ML-2·D-26·D-27 ([ML-2](ML-2-leave-shifts-and-published-changes.md)) |
| 원문 | [`ML-SUB-STATE-01`](../../references/ml/ML-SUB-STATE-01.md) (B+), [`ML-CONCURRENCY-01-R`](../../references/ml/ML-CONCURRENCY-01-R.md) (`develop`, PR #6 병합) |
| 주관 / 리뷰 | V4 Owner (주관). V2·V3 (퇴사·근무 변경 Command), V5 (알림), Frontend, 공통 설정 Owner (운영 DDL) |

> 이 카드는 **근무 변경·퇴사가 대타 요청과 후보에 주는 영향**만 다룬다. Lock 순서는 ML-5, 퇴사 API·권한은 ML-4, 재가입은 ML-H2.

---

## 1. 왜 지금 정해야 하나

ML-2·D-27로 공개 후 근무 인계·취소와 퇴사가 생기면, 지금 대타 코드에는 **아무도 끝낼 수 없는 요청**이 생긴다.
- 인계 후에도 후보가 수락할 수 있지만 승인은 실패한다. 새 담당자는 그 근무로 대타 요청을 만들 수 없다 (활성 요청 존재).
- 취소 후에는 수락·승인뿐 아니라 **관리자 종료도 실패**한다 (근무가 SCHEDULED가 아님).
- 퇴사자가 PENDING 후보로 남으면 요청이 OPEN으로 남아 요청자의 재요청을 막는다.
- 퇴사자가 ACCEPTED 수락자면 승인은 막히지만 요청은 ACCEPTED로 남는다.

## 2. AS-IS (① `VERIFIED_AS_IS`, 정적 확인)

경로 기준: `backend/src/main/java/com/merge/backend/domain/substitute/`

| 항목 | 사실 | 근거 |
|---|---|---|
| 상태 | Request `OPEN / ACCEPTED / APPROVED / CLOSED`. CloseReason `ALL_CANDIDATES_REJECTED / EXPIRED / MANAGER_CLOSED` (`EXPIRED`는 저장되지 않고 DTO에서만 계산). Candidate `PENDING / REJECTED / ACCEPTED` | `entity/RequestStatus.java`, `RequestCloseReason.java`, `CandidateStatus.java` |
| 저장 형식 | 세 값 모두 `@Enumerated(EnumType.STRING)`. **운영 MySQL Column이 VARCHAR인지 ENUM인지는 UNKNOWN** | `entity/SubstituteRequest.java` L33-37, `entity/SubstituteCandidate.java` L29-30 |
| 생성 | Shift Lock. 요청자 = 현재 담당자. 같은 근무에 OPEN·ACCEPTED가 있으면 거부. 후보는 현재 EMPLOYEE 중 요청자·충돌·불가능 시간·다른 대타 약속 제외 (Lock 없이 조회) | `service/SubstituteRequestService.java` L132-154, PR #5 Test `findCandidates_FiltersIneligibleMembers` |
| 수락 | Request Lock → User Lock. 후보가 현재 EMPLOYEE, Request OPEN, 근무 공개·SCHEDULED·시작 전, 충돌 없음. **근무 담당자 = 요청자인지는 확인하지 않음** | `service/SubstituteCandidateService.java` L116-170 |
| 거절 | Request Lock. 퇴사자 거절 거부. 마지막 PENDING이 거절하면 `ALL_CANDIDATES_REJECTED` | 같은 파일, PR #5 `RejectTest` |
| 승인 | Request Lock → User Lock → `changeMember` (Shift Row 쓰기). 근무 SCHEDULED, **담당자 = 요청자 (아니면 `INVALID_MEMBER`)**, 수락자 현재 소속 | `service/SubstituteRequestService.java` L233-282 |
| 관리자 종료 | OPEN·ACCEPTED, **근무 SCHEDULED·시작 전일 때만**. `MANAGER_CLOSED`. Candidate 불변 (C-49) | 같은 파일 L361-416 |
| 확정 대타 충돌 | `request.status = ACCEPTED`일 때만 셈 → 닫힌 요청의 ACCEPTED Candidate는 유효한 약속이 아님 | `repository/SubstituteCandidateRepository.java` (원문 B+ §3) |
| 이력 표시 | 승인 응답은 새 담당자를 **현재 `Shift.member`**에서 읽음 (승인 직후 응답이라 지금은 맞음). 목록은 수락자를 Candidate에서 읽음 | `dto/SubstituteRequestResponse.java` L23-24, `dto/SubstituteRequestListResponse.java` L26-36 |
| 알림 | Commit 후 `REQUIRES_NEW`, 실패 시 로그만 | `notification/event/*Listener.java` |

## 3. 사용자 잠정 결정

| ID | 결정 문장 |
|---|---|
| ML-3-Q1 | 공개 근무를 **인계하거나 취소**할 때 그 근무의 OPEN·ACCEPTED Request를 **같은 업무 Transaction**에서 CLOSED로 종료한다. 종료 사유는 **`SHIFT_CHANGED`**. Candidate 상태와 응답 이력은 바꾸지 않는다 |
| ML-3-Q1a | 요청자가 퇴사해서 근무가 인계·취소되는 경우도 `SHIFT_CHANGED`다 (이전 원문의 `MANAGER_CLOSED` 사용을 대체) |
| ML-3-Q1b | Request가 CLOSED면 ACCEPTED Candidate가 있어도 **현재 유효한 대타 약속으로 표시하지 않는다** |
| ML-3-P | 퇴사자가 **PENDING** 후보면 `INELIGIBLE`로 바꾼다. `respondedAt`은 null로 둔다 (본인의 실제 수락·거절 시각에만 씀) |
| ML-3-P1 | **OPEN** Request에서 응답 가능한 후보(현재 소속의 PENDING)가 0명이 되면 종료한다. 사유는 종료 시점의 후보 구성으로 정한다: INELIGIBLE이 1명 이상이면 `CANDIDATE_INELIGIBLE`, 전원 REJECTED면 기존 `ALL_CANDIDATES_REJECTED` (원문 B+ 구성 규칙). OPEN이 아니면(ACCEPTED·APPROVED·CLOSED) 이 규칙을 적용하지 않는다. 구현 정의(PR #92 리뷰): 응답 가능 = 같은 근무지의 현재 EMPLOYEE 소속 PENDING (수락·거절할 수 있는 후보), 사유는 "나머지 전원 REJECTED가 아니면 `CANDIDATE_INELIGIBLE`" → 끝의 "V4 구현 기록 — 퇴사자 후보 처리" |
| ML-3-A | 퇴사자가 **ACCEPTED** 수락자면 Request를 `CANDIDATE_INELIGIBLE`로 CLOSED 처리한다. Candidate의 ACCEPTED 이력은 보존한다 (`ACCEPTED → INELIGIBLE` 금지) |
| ML-3-R | CLOSED Request를 다시 OPEN으로 되돌리지 않는다. 종료 후 재요청은 **현재 소속과 자격 기준으로 후보를 새로 선정**한다 |
| ML-3-H | APPROVED Request는 과거 승인 이력으로 보존한다. 이력의 수락자는 Candidate(ACCEPTED)와 승인 시각으로 표시하고, **현재 `Shift.member`로 표시하지 않는다** |

**결정 수준 구분 (2026-10-23 교차 검증 반영):**
- 사용자가 명시적으로 고른 것: Q1(O1 + 새 종료 사유), P-a, A-a, **P1 구성 규칙 (2026-10-23 확인)**.
- ML-3-P1의 종료 사유 구성 규칙은 원문 B+(`docs/references/ml/ML-SUB-STATE-01.md` §1 표 B·B', §2 수정 문장)에서 가져왔고, 사용자가 확인해 잠정 결정이 됐다. 퇴사 처리와 거절 처리는 **같은 종료 판정 규칙**을 쓴다. "응답 가능 후보"를 판단하는 시점(Lock 이후 다시 조회한 소속·상태 기준)은 ML-5에서 확정한다.
- 승인된 요청의 ACCEPTED 후보가 1명이라는 보장은 DB가 아니라 로직에 있다. 여러 명이 발견되면(코드 변경·데이터 불일치) 이력 조회는 오류로 처리하고 운영 확인 대상으로 남긴다 (V4-N-21에 실패 경로 포함).
- 사유 의미 정의:
  - `ALL_CANDIDATES_REJECTED`: 종료 시점에 응답 가능 후보가 0명이고, 후보 **전원이 본인 거절**(REJECTED)인 경우. 의미는 기존과 같다.
  - `CANDIDATE_INELIGIBLE`: 후보의 자격 상실이 원인이 되어 요청이 끝난 경우. ① 응답 가능 후보가 0명이고 INELIGIBLE이 1명 이상 섞인 경우(예: REJECTED·INELIGIBLE·REJECTED), ② 수락자가 퇴사한 경우(ML-3-A). "전원 자격 상실"이라는 뜻이 아니다.
- 후보 0명인 요청은 생기지 않는다. 후보가 없으면 요청 자체를 만들지 않는다 (`create_NoCandidate_DoesNotCreateRequest`, V4-T-16).
- **기존 코드 영향 (VERIFIED_AS_IS):** 지금 거절 처리는 "현재 후보 외에 PENDING이 남았는가"만 보고 `ALL_CANDIDATES_REJECTED`로 닫는다 (`SubstituteCandidateService` L274-284, `existsByRequest_IdAndStatusAndIdNot`). INELIGIBLE을 도입하면 **이 종료 판정도 구성 규칙으로 바꿔야 한다** (기존 V4 거절 경로 변경). 기존 Test `rejectCandidate_LastCandidate_ClosesRequest`는 INELIGIBLE이 없는 구성이라 기대값이 그대로다.
- **승인 대상 식별 (ML-3-H 근거, VERIFIED_AS_IS):** Request에는 승인 대상 FK가 없다 (필드: Shift, 요청자, 상태, 종료 사유, `approvedAt`, `closedAt`). 하지만 Request는 `OPEN → ACCEPTED`를 한 번만 거치고 되돌아가지 않으며, 수락은 Request Lock 아래에서 OPEN일 때만 가능하다. 그래서 **APPROVED Request의 ACCEPTED Candidate는 정확히 1명**이다. DB 제약이 아니라 로직으로 보장되므로 회귀 Test로 고정한다 (V4-N-21). 새 Column은 필요 없다.

**상태 / 종료 사유 / 변경 이력 구분:**
- 상태(Status): 지금 어떤 상태인가 (Candidate `INELIGIBLE` 추가)
- 종료 사유(Reason): CLOSED가 된 이유 (`SHIFT_CHANGED`, `CANDIDATE_INELIGIBLE` 추가)
- 변경 이력(History): 누가 언제 무엇을 바꿨나 (ML-2 배정 이력, D-26 취소 정보)

## 4. 종료 원인을 종료 당시 기준으로 남기는 방법

`SHIFT_CHANGED`만 저장하면 "근무 변경으로 종료"까지만 정확하다. "퇴사로 인계되어 종료"처럼 원인을 보여 주려면 **종료 당시의 근거**가 필요하다. 같은 근무의 담당자가 나중에 또 바뀌면 "가장 최근 배정 이력"은 틀린 원인을 가리킨다.

| 원인 | 근거 | 나중에 바뀌는가 |
|---|---|---|
| 근무 취소 (관리자·퇴사) | Shift의 취소 사유·시각 (D-26 R1) | **바뀌지 않음.** CANCELLED는 끝 상태 |
| 근무 인계 (관리자·퇴사) | 그때의 배정 이력 Row | 이력 Row 자체는 불변이지만, **같은 Shift에 이력이 여러 개** 생길 수 있음 |

**최소 계약 (이번 잠정 결정):** `SHIFT_CHANGED`를 저장하고, 화면·알림은 "근무 변경으로 대타 요청이 종료되었습니다"를 보장한다. 이 문구는 나중에 같은 근무가 다시 바뀌어도 틀리지 않는다.

**상세 표시 후보 (설계 후보, 미결정. 구현 계약 아님):** Request에 nullable 연결 1개를 둔다: **종료를 일으킨 배정 이력 ID**. 인계로 종료될 때만 채운다. 취소로 종료된 경우는 Shift 취소 정보(끝 상태라 불변)로 충분하다. 큰 이벤트 시스템은 넣지 않는다. 채택한다면 함께 정할 것:
- 배정 이력 Row는 수정·삭제하지 않는 기록으로 다룬다.
- 연결과 Request 종료, 배정 이력 저장은 같은 Transaction이다.
- 연결이 비어 있거나 조회되지 않으면 최소 계약 문구로 대체한다.
- 대안: Request에 원인 문자열이나 원인 Enum을 따로 저장. 연결 없이 간단하지만, 결국 Enum이 늘어난다.
- 화면을 "근무 변경으로 종료"로만 보여 주기로 하면 연결 없이 `SHIFT_CHANGED`만으로 충분하다. **이것은 Frontend 표시 수준 결정에 따른다 (미정).**

## 5. 상태 전이 (제안)

**Candidate:**

| 전이 | 누가 | 조건 |
|---|---|---|
| `PENDING → ACCEPTED` / `PENDING → REJECTED` | 후보 본인 (기존) | 기존 |
| **`PENDING → INELIGIBLE`** | 퇴사 Transaction | 대상 소속 종료, Request OPEN·ACCEPTED, 근무 시작 전 |
| 그 밖 | — | 금지 (`ACCEPTED → INELIGIBLE`, `INELIGIBLE → *` 포함) |

**Request:**

| 전이 | 누가 | 사유 |
|---|---|---|
| `OPEN → ACCEPTED`, `ACCEPTED → APPROVED` | 기존 | — |
| `OPEN·ACCEPTED → CLOSED` | 관리자 종료 API (기존) | `MANAGER_CLOSED` |
| `OPEN → CLOSED` | 마지막 PENDING 거절 (기존) | `ALL_CANDIDATES_REJECTED` 또는 `CANDIDATE_INELIGIBLE` (구성 규칙, ML-3-P1) |
| **`OPEN·ACCEPTED → CLOSED`** | 근무 인계·취소 (관리자 또는 퇴사) | **`SHIFT_CHANGED`** |
| **`OPEN → CLOSED`** | 퇴사로 응답 가능 후보 0명 | **`CANDIDATE_INELIGIBLE`** |
| **`ACCEPTED → CLOSED`** | 수락자 퇴사 | **`CANDIDATE_INELIGIBLE`** |
| `CLOSED → *`, `APPROVED → *` | — | 금지 |

## 6. 퇴사·관리자 변경과의 Transaction 관계

| Command | 같은 Transaction에서 처리 |
|---|---|
| 관리자 인계 | 그 근무의 활성 Request 종료(`SHIFT_CHANGED` + 배정 이력 연결) → 담당자 변경 → 배정 이력 저장 |
| 관리자 취소 | 그 근무의 활성 Request 종료(`SHIFT_CHANGED`) → CANCELLED + 취소 정보 |
| 퇴사 | ① 퇴사자가 **요청자**인 근무: ML-2-A 선택(인계·취소)에 따라 위 두 줄과 같음<br>② 퇴사자가 **PENDING 후보**: `INELIGIBLE` → 그 Request의 응답 가능 후보가 0명이면 `CANDIDATE_INELIGIBLE` 종료<br>③ 퇴사자가 **ACCEPTED 수락자**: Request `CANDIDATE_INELIGIBLE` 종료<br>④ 소속 종료, DRAFT 삭제 등 ML-2 처리 |

- 일부만 성공하는 경우가 없어야 한다 (요청만 닫히고 근무 변경 실패, 또는 반대).
- 알림은 Commit 후 별도라 같은 원자성을 갖지 않는다. 요청 종료 하나에 **종료 알림 1건만 발행을 시도**한다 (`SHIFT_CHANGED`·`CANDIDATE_INELIGIBLE` 종료가 `MANAGER_CLOSED` 알림을 함께 보내지 않음). 전달 보장 수준은 G03-D-22.
- 알림 받는 사람: 요청자(현재 소속일 때), ACCEPTED·PENDING 후보(현재 소속일 때), 현재 관리자. **퇴사자는 받지 않는다.**

## 7. ML-5 의존성 (Lock 순서)

- 인계·취소 Transaction은 그 근무의 활성 Request를 잠가야 수락·승인과 순서가 정해진다.
- 기존 승인은 **Request → User → (Commit 시) Shift Row 쓰기** 순서다. 인계가 **Shift → Request** 순서로 잠그면 서로 반대가 되어 **Deadlock 후보**가 된다.
- 퇴사 Transaction은 퇴사자가 후보인 다른 사람의 Request도 잠근다. 수락은 Request Lock 뒤에 `leftAt`을 보므로, 원문 ML-CONCURRENCY-01-R은 Membership Row Guard(다른 Command `FOR SHARE`, 퇴사 `FOR UPDATE`)를 제안했다.
- 후보 선정(생성)은 Lock 없이 소속을 읽는다 → 퇴사와 동시에 퇴사자가 새 PENDING이 될 수 있다 (원문 반례 A).
- **전역 순서 후보:** ~~원문의 Schedule → Request → Shift → Membership~~ → **ML-5 Draft에서 Workplace → Schedule → Shift → Request → User → Membership으로 수정** (대타 생성의 Shift 직렬화와 승인 Deadlock 때문, `drafts/D7-ML-5/` §4). 최종 결정은 ML-5.
- **퇴사 Transaction이 건드리는 범위 (ML-2 + ML-3):**
  - 퇴사자 **본인 근무**: 그 근무지의 미래 공식 근무(인계·취소)와 그 근무의 활성 Request(`SHIFT_CHANGED`), DRAFT 근무 삭제.
  - 퇴사자가 **다른 사람 근무의 후보**: PENDING → INELIGIBLE, ACCEPTED면 Request `CANDIDATE_INELIGIBLE`, 응답 가능 후보 0명이면 Request 종료.
  - 범위: Candidate는 그 근무지의 소속 Row를 가리키므로, **다른 근무지의 소속·근무·대타는 건드리지 않는다.**
- **ML-5에서 정할 구체 항목:**
  - 여러 Request를 잠글 때 순서를 고정한다 (예: Request ID 오름차순). 퇴사가 잠그는 Request(본인 근무 + 후보로 걸린 근무)를 하나의 정렬된 목록으로 만든다.
  - Lock을 잡은 뒤 Candidate 상태, 소속(`leftAt`), Request 상태를 **다시 조회**한다 (영속성 Context의 오래된 값이 아니라 DB 값. 원문 ML-CONCURRENCY-01-R의 지적).
  - 미리보기와 다르면 거부(409)하고 다시 확인하게 한다. 자동 재시도는 하지 않는다 (후보).
  - Deadlock·Lock 대기 초과의 응답 (원문 제안 503, 지금은 500) → ML-5.

## 8. API·Frontend·DB 영향

| 영역 | 내용 | 기존 계약 변경 |
|---|---|---|
| API 응답 | Candidate `status`에 `INELIGIBLE`, Request `closeReason`에 `SHIFT_CHANGED`·`CANDIDATE_INELIGIBLE`이 나올 수 있음. 원인 표시를 하면 응답 Field 추가 (§4) | Enum 값·응답 계약 변경 (AI-RULES §2 승인 대상) |
| 새 API | 없음 (기존 Command 내부 효과) | — |
| Frontend | 보낸 요청: 새 종료 사유 label, 종료일 표시 (`getDateLine` 조건, 원문 B+ §5). 받은 요청·수락 목록: 닫힌 요청을 유효 약속으로 보이지 않게. 관리자 진행 중 목록: 종료된 요청 제외 (기존 동작) | Frontend 변경 |
| 알림 | 새 종료 알림 Type 후보 1개 (근무 변경·후보 자격 상실 공통 또는 각각, 구현 카드에서 결정) | `NotificationType` 추가 |
| DB | `RequestCloseReason` 2개, `CandidateStatus` 1개, `NotificationType` 값 추가. §4 연결 Column 1개 (nullable) | §9 |
| 기존 이름 충돌 | PR #5 Test `findCandidates_FiltersIneligibleMembers`의 "Ineligible"은 **후보 선정에서 제외**라는 뜻. 새 `INELIGIBLE` 상태와 다름. 이름이 헷갈릴 수 있어 리뷰 때 짚는다 | — |

## 9. 운영 DB 변경과 Rollback (미정, 팀 결정 후보)

| 확인할 것 | 상태 |
|---|---|
| `substitute_requests.close_reason`, `substitute_candidates.status`, `notifications.type`의 운영 Column 형식 (VARCHAR / MySQL ENUM / CHECK 제약) | **UNKNOWN.** Hibernate 기본 동작으로는 MySQL ENUM일 가능성이 있으나 단정하지 않음. 배포 담당 확인 필요 |
| 값 추가 방법 | VARCHAR면 길이만 확인. ENUM이면 허용 값 변경 DDL. CHECK면 제약 변경 |
| `ddl-auto: update` | 일부 스키마 변경을 시도할 수 있지만 제약 제거·Enum/데이터 이행·복구를 보장하지 않는다. **안전장치로 쓰지 않음**. 운영 절차는 [OPS-01](../../delivery/TASK-OPS-01-prod-config-failfast.md) §3·§5 |
| 배포 순서 후보 | ① DB에 새 값 허용 → ② 새 값을 **읽을 수 있는** 앱 배포 (아직 쓰지 않음) → ③ 새 값을 쓰는 기능 활성화 |
| Rollback 위험 | 새 값이 저장된 뒤 예전 앱으로 되돌리면 Enum 변환 실패로 조회 500 (원문 B+ §5). ② 단계 이후로만 되돌리거나, 되돌릴 때 값을 바꾸는 절차가 필요 |
| 함께 묶을 변경 | ML-2 Shift 취소 Column, 배정 이력 Table, §4 연결 Column, 알림 Type |
| 확인 (2026-10-28, 실행) | test Profile(Testcontainers MySQL 9.7.2, `ddl-auto: create`)에서 Hibernate가 `@Enumerated(STRING)`을 **MySQL `ENUM(...)`**으로 만듦: `substitute_request.close_reason` = `enum('ALL_CANDIDATES_REJECTED','EXPIRED','MANAGER_CLOSED')`(새 코드에서는 `SHIFT_CHANGED` 포함), `substitute_candidate.status`·`substitute_request.status`·`notification.type`도 ENUM. 예전 값만 있는 ENUM에 `SHIFT_CHANGED` 저장 → `DataIntegrityViolationException / Data truncated` (`sql_mode`에 `STRICT_TRANS_TABLES`). `application-prod.yml`은 `ddl-auto: update`이고 별도 Migration 도구 없음. `update`가 기존 ENUM 허용 값을 바꾸는지는 **실행하지 않음**. → V3 근무 취소·담당자 변경이 종료 함수를 호출하기 전에 운영 DB에 `ALTER TABLE substitute_request MODIFY close_reason ENUM('ALL_CANDIDATES_REJECTED','EXPIRED','MANAGER_CLOSED','SHIFT_CHANGED');` 적용 필요 (배포 담당, EV-034). 운영 Column 실제 형식은 여전히 배포 담당 확인 대상. **퇴사자 후보 처리 추가 (2026-10-29):** 새 값 `CANDIDATE_INELIGIBLE`·`INELIGIBLE` 때문에 V2 퇴사 확정 배포 전에 `ALTER TABLE substitute_request MODIFY close_reason ENUM('ALL_CANDIDATES_REJECTED','EXPIRED','MANAGER_CLOSED','SHIFT_CHANGED','CANDIDATE_INELIGIBLE');`와 `ALTER TABLE substitute_candidate MODIFY status ENUM('ACCEPTED','PENDING','REJECTED','INELIGIBLE') NOT NULL;` 적용 필요 (EV-038). **값 순서 (PR #92 리뷰):** 리뷰어 확인으로 test DB의 Hibernate 생성 순서는 알파벳순(`enum('ACCEPTED','PENDING','REJECTED')`)이다. 기존 값 순서를 바꾸면 Table 재작성이 생길 수 있으므로, 배포 전 운영 `SHOW CREATE TABLE`로 실제 순서를 확인하고 **기존 순서 그대로 끝에 새 값만 추가**한다. 위 ALTER 문의 기존 값 부분은 test DB 기준 예시 |

## 10. H6 경합 후보 (기대 결과 수정본, 모두 `SPECIFIED` / `NOT_RUN`)

수정 원칙: **과거 이력(ACCEPTED 기록)과 현재 유효 상태(활성 Request, 승인 가능 여부)를 구분**한다. ACCEPTED Candidate + CLOSED Request 조합은 오류가 아니다.

| ID | Given | When | Then (지켜야 할 결과) |
|---|---|---|---|
| H6-C1 | OPEN Request, 후보 B PENDING | 관리자 인계(A→C)와 B의 수락 동시 실행 | 인계 확정 후 그 근무에 **유효한 OPEN·ACCEPTED Request가 0건**. B의 수락이 먼저 Commit됐다면 B `ACCEPTED` 이력은 남을 수 있다. 근무 담당 C. 승인 불가 |
| H6-C2 | ACCEPTED Request | 관리자 인계와 승인 동시 실행 | Deadlock 없이 둘 중 하나 순서로 정리. 최종 담당자 1명. 승인이 먼저면 담당자 = 수락자 → 인계가 그 상태를 다시 검사. 인계가 먼저면 승인 `INVALID_MEMBER` 또는 `INVALID_REQUEST` |
| H6-C3 | 공개 근무 | 관리자 취소와 새 대타 요청 생성 동시 실행 | 취소 확정 후 그 근무에 활성 Request 0건 |
| H6-C4 | 공개 근무 | 관리자 두 명의 인계 동시 실행 | 최종 담당자 1명, 배정 이력 순서가 실제 반영 순서와 일치 |
| H6-C5 | 후보 B PENDING | B 퇴사와 B 본인의 수락 동시 실행 | 퇴사 확정 후 B가 **유효한 수락자이거나 승인 가능한 후보로 남지 않는다.** 수락이 먼저 Commit됐다면 B `ACCEPTED` 이력 + Request `CANDIDATE_INELIGIBLE` CLOSED (A-a)가 정상 |
| H6-C6 | 후보 B·C PENDING | B 퇴사와 C의 거절 동시 실행 | **응답 가능 후보가 실제로 0명이 된 경우에만** Request CLOSED, 사유 `CANDIDATE_INELIGIBLE`. 다른 응답 가능 후보가 남아 있으면 OPEN이 정상 |
| H6-C7 | 후보 B (현재 소속) | B 퇴사와 B를 후보로 넣는 새 대타 요청 생성 동시 실행 | 퇴사 확정 후 B가 PENDING 후보로 남지 않음 (원문 반례 A) |
| H6-C8 | 퇴사자 담당 근무에 ACCEPTED Request | 퇴사(인계 선택)와 승인 동시 실행 | 퇴사 후 퇴사자 담당 미래 근무 0건 (ML-2-INV-01), 모순 없음 |

재현 방식: PR #5로 들어온 `ForcedOrderTest` 방식(CountDownLatch로 첫 Transaction을 Commit 전 대기)을 재사용할 수 있다. LOCAL_ONLY H6 Harness(DL-009)가 없어도 상당 부분 가능하다.

## 11. 검증 계약 (기능)

모두 `SPECIFIED` / `NOT_RUN`. 승인 전 작성 금지. Inventory의 V4-N-06(B+), V4-N-07(퇴사 경합) 자리를 이 카드로 구체화하고, 새 ID는 V4-N-08부터.

| ID 후보 | Given → Then |
|---|---|
| V4-N-08 | OPEN(후보 2 PENDING) 근무를 인계 → Request CLOSED `SHIFT_CHANGED` + 배정 이력 연결, 후보 상태 그대로, 근무 담당 변경, 종료 알림 1건 |
| V4-N-09 | ACCEPTED(B) 근무를 취소 → Request CLOSED `SHIFT_CHANGED`, B `ACCEPTED` 유지, 근무 CANCELLED, B에게 알림 |
| V4-N-10 | V4-N-08 이후 새 담당자가 같은 근무로 대타 요청 → 성공 |
| V4-N-11 | V4-N-08 이후 후보가 수락 → `REQUEST_NOT_OPEN` |
| V4-N-12 | 요청자 퇴사(인계 선택) → `SHIFT_CHANGED`, 원인 표시 = 퇴사 (배정 이력 `MEMBER_LEFT`) |
| V4-N-13 | 후보 B·C PENDING, B 퇴사 → B `INELIGIBLE`(`respondedAt` null), Request OPEN, 알림 0 |
| V4-N-14 | V4-N-13 이후 C 거절 → Request CLOSED `CANDIDATE_INELIGIBLE` (구성 규칙), 알림 1건 |
| V4-N-15 | 유일한 후보 B 퇴사 → B `INELIGIBLE`, Request CLOSED `CANDIDATE_INELIGIBLE` |
| V4-N-16 | B ACCEPTED, B 퇴사 → Request CLOSED `CANDIDATE_INELIGIBLE`, B `ACCEPTED` 유지, 승인 거부, 요청자 재요청 성공 + 새 후보에 B 없음 |
| V4-N-17 | APPROVED 근무를 나중에 인계 → APPROVED Request 그대로, 이력 조회의 수락자 = Candidate(ACCEPTED), 현재 `Shift.member`와 다를 수 있음 |
| V4-N-18 | A→B 인계로 종료된 Request, 이후 B→C 재인계 → 최소 계약: 종료 사유 `SHIFT_CHANGED`와 문구 불변. (§4 상세 표시 후보를 채택한 경우에만) 종료 원인 = 첫 인계 이력 |
| V4-N-21 | APPROVED Request → ACCEPTED Candidate 정확히 1명, 이력 화면의 수락자 = 그 Candidate (이후 근무 인계와 무관) |
| V4-N-22 | 후보 B REJECTED, C INELIGIBLE, D 마지막 거절 → `CANDIDATE_INELIGIBLE` (기존 거절 경로의 종료 판정 변경 검증) |
| V4-N-19 | CLOSED Request를 OPEN으로 되돌리는 모든 경로 → 없음 / 거부 |
| V4-N-20 | `SHIFT_CHANGED` 종료 → `MANAGER_CLOSED` 알림이 함께 나가지 않음 |

**PR #5 Test와의 연결 (회귀, 기대값 변경 0):**
`acceptCandidate_LeftMember`, `rejectCandidate_LeftMember`, `approve_fail_accepted_member_left`, `approve_fail_shift_member_changed`, `approve_fail_shift_cancelled`, `SubstituteFlowIntegrationTest.cannotApproveWhenAcceptedMemberLeft`, `acceptThenManagerClose_ShiftUnchanged_CannotApprove`, `rejectCandidate_LastCandidate_ClosesRequest` (INELIGIBLE이 없는 구성이라 기대값 그대로), `close_DoesNotModifyCandidateStatus`, `SimultaneousTest`·`ForcedOrderTest` 8개.

## 12. 재검토 조건

- 운영 DDL을 하지 않기로 결정되면 → P를 P-b(PENDING 유지 + 표시·종료 판단 보완), 사유를 `MANAGER_CLOSED` 재사용으로 축소 검토.
- G03-D-18(재OPEN) 정책이 채택되면 → ML-3-R 재검토.
- 종료 원인 표시 수준이 "근무 변경" 한 줄로 충분하다고 정해지면 → §4 연결 Column 제거.
- 알림 전달 보장 요구가 생기면 → G03-D-22, H5.

## 13. Cross-Domain 영향과 리뷰

| 대상 | 확인할 것 | 리뷰 |
|---|---|---|
| V4 Substitute (주관) | 전체 | 필수 |
| V2 Workplace | 퇴사 Transaction 내 대타 처리 순서 | 필수 |
| V3 Schedule/Shift | 인계·취소 Command 안의 Request 종료, 배정 이력 연결 | 필수 |
| V5 Notification | 종료 알림 1건, 받는 사람, 새 Type | 필수 |
| Frontend | §8 | 필수 |
| 공통 설정 Owner | §9 운영 DDL·Rollback | 필수 |

## 14. 이전 제안·잠정 결정과의 차이

| 이전 | 현재 | 이유 |
|---|---|---|
| 원문 9430: 퇴사자 PENDING을 `REJECTED`로 재사용 | `INELIGIBLE` (원문 9601 B+로 이미 대체) | 본인 응답과 자격 상실 구분 |
| 원문 9430·9601: 요청자 퇴사 시 `MANAGER_CLOSED` | **`SHIFT_CHANGED`** | 관리자가 직접 닫은 것이 아니며, ML-2에서 요청자의 근무가 인계·취소되므로 근무 변경이 원인 |
| (없음) | 관리자 인계·취소 시 자동 종료 (O1) | D-27로 공개 후 변경이 생김 |
| 앞 대화의 H6-C1 "수락 성공 + 요청 CLOSED가 동시에 남지 않음", H6-C5 "퇴사 후 퇴사자 ACCEPTED 없음" | §10 수정본 | 이력과 현재 유효 상태를 혼동해, 올바른 구현이 실패하는 기대값이었음 |
| 앞 대화: 종료 원인은 Shift 상태·최근 배정 이력에서 읽기 | §4 종료 당시 배정 이력 연결 | 같은 근무의 담당자가 여러 번 바뀌면 최근 이력이 틀림 |

## 15. 후속

| 항목 | 카드 |
|---|---|
| 전역 Lock 순서, Membership Guard, H6-C1~C8 재현 | ML-5 |
| PR #5에서 빠진 "일찍 실패 시 User Lock 미획득" 확인이 필수 불변조건인지 | ML-5 |
| 퇴사 API 형식·권한 | ML-4 |
| 재가입 후 재요청 시 후보 선정 (새 소속 Row) | ML-H2 |
| 운영 DDL·Rollback 절차 | 팀 결정 후보 |
| 알림 전달 보장 | G03-D-22 |
| V4 Test Inventory 정정 (PR #5 반영) | 별도 메모 `V4-TEST-INVENTORY-DELTA-PR5.md` → 별도 문서 변경 |

## S1 결정 연결 (2026-10-25)

이 카드 본문은 이관 당시 그대로다. 아래는 2026-10-25에 기록한 사용자 잠정 결정(모두 ② `PROPOSED`, 팀 승인 전)과 이 카드의 연결 위치다. 결정 내용은 연결된 카드가 원본이다. 본문 수정이 필요한 곳은 S2-3 이후 해당 단계에서 고친다.

| 이 카드의 위치 | 연결된 결정 | 요약 |
|---|---|---|
| ML-3-A, Candidate 금지 전이 (`ACCEPTED → *`) | [G03-D-22](G03-D-22-notification-delivery.md) §4-3 | 종료된 대타 조회가 "현재 ACCEPTED = 수락 이력"에 의존한다. 이 금지 전이를 유지해야 한다 |
| 새 종료 사유 `SHIFT_CHANGED`·`CANDIDATE_INELIGIBLE`의 `closedAt` | [G03-D-19](G03-D-19-expiry.md) §0-2, [G03-D-22](G03-D-22-notification-delivery.md) §4-3 | Command 시각을 DB `closedAt`에 저장 (B1 미적용). 구현 계약에서 확인 |
| ML-3-H (APPROVED는 과거 이력) | [G03-D-22](G03-D-22-notification-delivery.md) S22-c | 승인된 수락자에게 "승인됨" 표시, 현재 담당과 구분. 승인 응답 DTO 재사용 금지 |
| 알림 전달 보장 | [G03-D-22](G03-D-22-notification-delivery.md) D22-1·5 | 최선 노력. Outbox 등은 후속 |
| L126 알림 받는 사람 ("요청자, ACCEPTED·PENDING 후보, 현재 관리자") | [NOTIFICATION-TYPES](../../contract/NOTIFICATION-TYPES.md) §6-3 | `SHIFT_CHANGED` 종료에서 **PENDING 후보와 실행한 관리자는 받지 않는다** (사용자 잠정 결정 2026-10-25). 원래 문장은 그대로 둠 |
| V4-N-08 "종료 알림 1건", V4-N-09 "B에게 알림", V4-N-14 "알림 1건" (§11 Test) | [NOTIFICATION-TYPES](../../contract/NOTIFICATION-TYPES.md) §3-4·§6-3, NT-T10·T13·T14 (= V5-N-10·13·14) | 이 Test들은 **업무 결과**(Request·Candidate·근무 상태)만 V4가 확인한다. 알림 기대값은 받는 사람별 표(§3-4)를 따른다: PENDING 후보 0건, 실행한 관리자 0건, 다른 현재 관리자 각 1건, 요청자·ACCEPTED 후보는 Type 규칙대로. 원문의 "1건"은 고치지 않고 이 연결로 대체한다 (사용자 잠정 결정 2026-10-25, 팀 `PENDING`) |
| V4-N-20 (`SHIFT_CHANGED` 종료 → `MANAGER_CLOSED` 알림 없음) | [TEST-INVENTORY](../../evidence/TEST-INVENTORY.md) §5-2 | NT-T5 (= V5-N-05)와 실제 중복. ID는 유지하고 V5-N-05로 한 번만 작성 |

## V4 구현 기록 (2026-10-28)

ML-3-Q1(G03-D-15 APPROVED)의 "근무 변경 시 활성 요청 `SHIFT_CHANGED` 종료" 중 V4 부분. V3 공개 근무 취소(API-PS-02)·담당자 변경(API-PS-03) Command가 같은 Transaction 안에서 부르는 함수로 만들었다 (RELEASE-READINESS-PLAN §4 "V4의 요청 종료 규칙은 그 안에서 호출되는 계약", PR 경계 "V4의 작은 선행 PR").

| 항목 | 내용 | 근거 |
|---|---|---|
| 함수 | `SubstituteRequestService.closeActiveRequestsByShiftChange(shiftId, now)` → `List<ShiftChangeClosedRequest>` (`requestId`, `requesterMemberId`, `acceptedMemberId`) | V4 Owner 결정 (문서에 형식 없음) |
| 대상 | 그 근무의 OPEN·ACCEPTED Request 전부를 ID 오름차순 `FOR UPDATE`로 조회 (`SubstituteRequestRepository.findActiveByShiftIdForUpdate`) → `CLOSED / SHIFT_CHANGED`, `closedAt = now`. Candidate는 건드리지 않음. APPROVED·CLOSED는 조회 대상이 아님 | ML-3-Q1·Q1b·H, API-CATALOG §4, ML-5 §5 규칙 1 |
| 2건 이상 | 모두 종료 | API-CATALOG §4 "모든 OPEN·ACCEPTED". G03-D-15의 처리 방식은 미결정 → **팀 확인 대상** |
| Transaction | `Propagation.MANDATORY`: 호출한 쪽 Transaction에 합류, 없으면 예외. 따로 Commit하지 않음 | ML-2-A3, ML-3 §6 |
| Lock 순서 | 호출하는 쪽이 Shift를 먼저 `FOR UPDATE` → 이 함수가 Request → (V3가) 새 담당 User | ML-5 §5-1, API-PS-02·03 |
| 수락 후보 | Request `status`를 보지 않고 ACCEPTED Candidate를 직접 조회 (Lock 전에 읽힌 Entity의 옛 status를 보지 않기 위해). 진행 중 요청에 ACCEPTED 후보가 있으면 그 요청은 ACCEPTED | ML-5 §4-3 |
| 알림 | **이 함수는 이벤트를 발행하지 않는다.** 요청자 알림은 V3의 `SHIFT_CANCELLED`·`SHIFT_REASSIGNED_AWAY`에, 새 담당이 된 수락 후보 알림은 `SHIFT_ASSIGNED`에 합쳐야 하고(받는 사람별 판단) 받는 사람은 Lock 이후 상태로 정해야 해서, 판단에 필요한 요청자·수락 후보 소속을 돌려준다. 알림 발행 위치는 V3·V5와 확정 | NOTIFICATION-TYPES §3-2 R1·§3-3·§3-4, G03-D-22 |
| `closedSubstituteRequests` | 반환 목록의 크기 (이번 Transaction에서 실제로 바뀐 서로 다른 요청 수) | API-PS-02·03 |
| 운영 DB | `close_reason`이 MySQL ENUM이라 V3 호출 전에 `ALTER` 필요 (§9 확인 행) | §9 |
| 하지 않은 것 | V3 Command 연결, 알림 Type·발행, 퇴사 처리(P·P1·A), 종료 원인 연결(§4), Frontend `SHIFT_CHANGED` 표시 | — |

## V4 구현 기록 — 퇴사자 후보 처리 (2026-10-29)

ML-3-P·P1·A의 V4 부분. V2 퇴사 확정 Command(API-LV-02)가 같은 Transaction 안에서 부르는 함수로 만들었다 (RELEASE-READINESS-PLAN: C는 V2 주관, V4는 호출 계약 제공). 이 기능만으로는 호출하는 곳이 없다.

| 항목 | 내용 | 근거 |
|---|---|---|
| 함수 | `SubstituteCandidateService.closeCandidaciesByMemberLeave(memberId, now)` → `MemberLeaveCandidacyResult` (`closedRequests`: 닫은 요청 ID·요청자 소속 ID, ID 오름차순 / `ineligibleCandidacies`: INELIGIBLE로 바꾼 대기 후보 수) | V4 Owner 결정 (문서에 형식 없음). API-LV-02 `closedRequests`·`ineligibleCandidacies` |
| 대상 | 그 소속이 PENDING·ACCEPTED 후보로 걸린 OPEN·ACCEPTED Request 중 **근무 시작 전**인 것을 ID 오름차순 `FOR UPDATE` (`findActiveByCandidateMemberIdForUpdate`). 다른 근무지 소속·APPROVED·CLOSED는 건드리지 않음 | §5 (`PENDING → INELIGIBLE` 조건), §7 범위, ML-5 §5-1 |
| 처리 | 수락자 → Request `CLOSED / CANDIDATE_INELIGIBLE`, 후보 ACCEPTED 유지 (A). 대기 후보 → `INELIGIBLE`, `respondedAt` null (P). OPEN이고 응답 가능 후보(**그 소속을 뺀, 같은 근무지의 현재 EMPLOYEE 소속 PENDING**) 0명 → `CANDIDATE_INELIGIBLE` 종료 (P1) | ML-3-P·P1·A |
| Lock 뒤 판단 | Request·Candidate 상태를 Entity가 아니라 DB 값으로 다시 읽어 판단 (Lock 전에 읽힌 Entity의 옛 상태로 수락 기록을 덮지 않기 위해) | ML-5 §4-3 |
| 퇴사 순서와 무관 | 응답 가능 후보를 셀 때 퇴사자 소속을 ID로 직접 빼므로, V2가 `leftAt`을 이 함수 전·후 어느 쪽에 기록해도 결과가 같다 | — |
| Transaction | `Propagation.MANDATORY` | ML-3 §6 단일 Transaction |
| 거절 경로 | 퇴사 함수와 같은 조회(`existsRespondableCandidateExcept`)로 응답 가능 후보를 센다. 0명이면 거절한 후보를 뺀 나머지가 전원 REJECTED일 때 기존 `ALL_CANDIDATES_REJECTED` + 기존 Event, 아니면(INELIGIBLE, 퇴사했는데 PENDING, EMPLOYEE가 아닌 PENDING, 다른 근무지 소속) `CANDIDATE_INELIGIBLE` + 새 `SubstituteCandidateIneligibleEvent` (받는 사람 = 요청자 + 현재 관리자). 기존 Test 기대값 변경 0 | §3 "같은 종료 판정 규칙", NOTIFICATION-TYPES `SUBSTITUTE_CANDIDATE_INELIGIBLE`(거절 또는 퇴사 처리 안)·NT-T11, G03-D-22 새 알림 6종 |
| 알림 | 퇴사 함수는 이벤트를 발행하지 않는다 (받는 사람은 퇴사 확정 결과로 V5가 정함, 퇴사자 제외). 거절 경로의 새 Event를 받는 Listener와 `NotificationType`은 **V5 후속**. INELIGIBLE은 이 함수만 만들어 V2 퇴사 확정 전에는 생기지 않으므로 그 전까지 알림 공백은 없다 | NOTIFICATION-TYPES §3-1, V5-N-20 |
| 결정 수준 | ML-3-A는 DECISION-LOG §4-2 G03-D-15 APPROVED. ML-3-P·P1은 사용자 잠정 결정 (G03-D-12 "단일 Transaction으로 대타 정리", §4-4 "후보자격변경"을 근거로 구현) → **팀 확인 대상** | DECISION-LOG §4 |
| 응답 가능 후보 정의 | PENDING ∧ `leftAt IS NULL` ∧ `role = EMPLOYEE` ∧ 소속 근무지 = 요청 근무지. 수락·거절 검사(`validateRespondableCandidate`, §2 수락 행 "현재 EMPLOYEE")와 퇴사 미리보기(PR #90 `LeavePreviewService`)와 같은 조건. 후보는 생성 때 같은 근무지의 현재 EMPLOYEE만 들어가므로 지금 도달할 수 있는 데이터에서는 "현재 소속의 PENDING"과 결과가 같다 | PR #92 리뷰 (권장 의견) |
| 리뷰 반영 (PR #92) | 거절 경로가 퇴사 여부를 보지 않아, 퇴사했는데 PENDING으로 남은 후보가 있으면 요청이 OPEN으로 남던 문제 (리뷰어가 MySQL에서 재현) → 위 거절 경로로 수정, V4-N-72~74 추가 (EV-038) | PR #92 리뷰 (수정 요청) |
| 원문 B+와 다른 점 (열린 항목) | 원문 B+ §2 추가 문장 "종료할 때 퇴사한 소속의 PENDING을 `INELIGIBLE`로 함께 정리"와 §6 #8 기대값("Y `INELIGIBLE`로 정리")은 이 문서에 옮겨 오지 않았다. 구현은 정리하지 않고 PENDING으로 둔다 (퇴사 경로 V4-N-71, 거절 경로 V4-N-72 같음. 관리자 종료·만료·근무 변경 종료도 PENDING을 그대로 둠). 정리가 필요하면 팀 결정 후 별도 변경 | 사용자 선택 (2026-10-29), AI-RULES §3 |
| 운영 DB | `close_reason`·`substitute_candidate.status` ENUM에 새 값 `ALTER` 필요 (§9) | §9 |
| 하지 않은 것 | V2 퇴사 확정 연결, 대타 생성·수락 시 소속 `FOR SHARE` 재확인(H6-C7, ML-5 Membership Guard), 승인 시 수락자 소속 재확인, 알림 Type, Frontend 새 상태·사유 표시 | — |

## 16. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-23 | 초안. ML-3-Q1 (O1 + `SHIFT_CHANGED`), P-a, A-a 사용자 잠정 결정. H6-C1·C5·C6 기대값 수정, 종료 원인 연결(§4), 운영 DDL·Rollback(§9) 반영 |
| 2026-10-23 | P1 구성 규칙 사용자 확인 → 잠정 결정. 퇴사·거절 공통 판정, 판단 시점은 ML-5, 승인 후보 다수 발견 시 처리 추가 |
| 2026-10-23 | 교차 검증 반영: P1 구성 규칙을 "원문 B+ 구체화, 사용자 확인 대기"로 표시하고 사유 의미 정의. 기존 거절 종료 판정 변경 필요(L274-284) 추가. 승인 대상 식별 근거와 V4-N-21. §4를 최소 계약 / 상세 표시 후보로 분리. §7에 퇴사 범위·Lock 구체 항목. V4-N-18 조건부, V4-N-22 추가 |
| 2026-10-25 | 저장소 이관 (메타데이터만 수정). "S1 결정 연결" 절 추가와 머리 표 경로를 링크로 변경 (본문 판단 변경 없음) |
| 2026-10-25 | S2-5B: "S1 결정 연결"에 V4-N-08·09·14 알림 기대값 연결, V4-N-20 통합 표시 (본문 수정 없음) |
| 2026-10-28 | 근무 변경 시 대타 종료 구현: 머리 표 구현·④/⑤, §9 확인 행 (MySQL ENUM, 예전 ENUM 저장 실패 재현, `ALTER` 필요), 끝에 "V4 구현 기록" 절 (EV-034) |
| 2026-10-29 | 퇴사자 후보 처리 구현: 머리 표 구현·④/⑤, §9에 ENUM 2개 ALTER, 끝에 "V4 구현 기록 — 퇴사자 후보 처리" 절 (EV-038). 머리 표의 근무 변경 구현 상태를 PR #80 머지·V3 호출로 갱신 |
| 2026-10-29 | PR #92 리뷰 반영: 거절 경로를 퇴사 함수와 같은 응답 가능 판정으로, 응답 가능 정의에 EMPLOYEE·같은 근무지 추가, ML-3-P1 행에 구현 정의 연결, 원문 B+ 정리 문장과의 차이를 열린 항목으로 기록, §9 ENUM 값 순서 주의 (EV-038) |
