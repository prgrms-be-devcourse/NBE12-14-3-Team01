# G03-D-22 알림 전달 수준과 상태 확인 보장

> **현재 승인 상태 (2026-10-26):** 사용자 전달 구두 승인과 PR 리뷰에 따른 채택 범위·예외는 [Decision Log](../DECISION-LOG.md) §4를 따른다. 아래 최초 작성 당시 PROPOSED/PENDING 표기는 이력이며 현재 승인 범위를 덮어쓰지 않는다. 구현·실험·배포 및 미결정 사항은 각각 별도 상태다.

| 축 | 상태 |
|---|---|
| 결정 단계 | **사용자 잠정 결정** (2026-10-25, D22-1~6, S22-a·b·c) |
| ② 정책 | 카드 `PROPOSED`. `DECISION-LOG` G03-D-22는 `PENDING` 그대로 |
| ③ 문서 | `DRAFT` |
| 구현 | 없음. 알림 구조는 현재 동작 유지, 종료된 대타 조회는 Improvement |
| ④ / ⑤ | 신규 Test 전부 `SPECIFIED` / `NOT_RUN` |
| 기준 코드 | 3차 `develop` `a148a62` (Production Java = `cdaaa13`). 정적 확인 |
| 관련 ID | G03-D-22 (주), G03-D-16·D-20 (알림 Type·만료 알림), G03-D-19 (만료 시각), H5 (Outbox), ML-3·ML-4 (로컬 Draft), C-51~C-56 |
| 주관 / 리뷰 | V5 Owner (알림), V4 Owner (종료된 대타 조회), Frontend |

> 진행 절차는 [`PROCESS-CHARTER`](../PROCESS-CHARTER.md)를 따른다.

---

## 1. 왜 지금 정해야 하나

- 새 기능(근무 취소·인계·배정, 대타 종료 사유 추가, 가입·재가입)이 알림 Type을 4~5개 늘린다 (로컬 요약 Draft §3).
- 알림이 유실됐을 때 사용자가 상태 변화를 알아낼 다른 방법이 없으면 Flow가 닫히지 않는다.
- 전달을 보장하려면(Outbox·재시도) 구조와 비용이 크게 달라진다. 어느 수준을 이번에 약속할지 정해야 한다.

## 2. Java AS-IS (① `VERIFIED_AS_IS`, 정적 확인)

| 항목 | 현재 | 근거 |
|---|---|---|
| 발행 시점 | 업무 Commit 후 (`@TransactionalEventListener(phase = AFTER_COMMIT)`), Listener 7개 | `notification/event/*Listener.java` L18 |
| 저장 Transaction | 별도 (`Propagation.REQUIRES_NEW`) | `NotificationService` L61·89·117·145·173·201·268 |
| 실패 처리 | `try/catch`로 `log.error`만. 업무 결과와 HTTP 응답은 그대로 성공 | 각 Listener L28-29 |
| 유실 경우 | ① 알림 저장 중 DB·코드 오류 (로그 남음) ② Commit 직후 프로세스 종료 (로그도 없음, 추론) | — |
| 전달 채널 | 앱 안 목록뿐. `GET /notifications`, `PATCH /notifications/{id}/read`. 푸시·메일 없음. Frontend는 Header에서 조회 | `NotificationController` L25·47, `AppHeader.tsx` L183-186 |
| Type | 8개. `SUBSTITUTE_EXPIRED`는 발행 경로 없음 | `NotificationType` |
| 수신자 조회 | 현재 소속(`leftAt IS NULL`)만 → 퇴사자는 자기 알림을 볼 수 없음 | `NotificationRepository` L19 |

**대타 후보 쪽 조회 (종료된 대타 조회와 관련):**

| API | 조건 | 근거 |
|---|---|---|
| SUB-02 받은 요청 `GET /substitute-requests/received` | 내 Candidate `PENDING` ∧ Request `OPEN` ∧ 시작 전 ∧ PUBLISHED ∧ SCHEDULED ∧ `c.member.leftAt IS NULL` | `SubstituteCandidateRepository` L55-70 |
| SUB-07 수락한 요청 `GET /substitute-requests/accepted` | 내 Candidate `ACCEPTED` ∧ Request `ACCEPTED` ∧ 시작 전 ∧ PUBLISHED ∧ SCHEDULED. **`c.member.leftAt` 조건 없음** | `SubstituteCandidateRepository` L82-97 |
| Candidate의 소속 | `SubstituteCandidate.member` → WorkplaceMember (NOT NULL). User가 아니라 **그때의 Membership**을 가리킨다 | `SubstituteCandidate` L25 |
| 후보 선정 범위 | 근무지의 현재 구성원 중 요청자·MANAGER·충돌자 제외 **전원** | `SubstituteCandidateService` L48-66 |

결과: 요청이 끝나면(승인·종료·만료) 후보 화면 두 곳에서 **조용히 사라진다.** 수락했던 직원은 다른 사람이 선정됐는지, 관리자가 닫았는지, 근무가 바뀌었는지, 만료됐는지 구분할 수 없다.

## 3. 이전 제안

- G-03: G03-D-22 "알림 유실 허용 수준과 실패 관측 (H5 연관)", `DECIDE_AFTER_EVIDENCE`.
- 로컬 ML-3 Draft: 요청 종료 하나에 종료 알림 1건만 발행 시도. 전달 보장 수준은 G03-D-22.
- 로컬 ML-4 Draft: "빠진 근무"(원래 시작 시각 기준 미래 + 최근 7일), "내 소속 이력" — 알림을 놓쳐도 확인할 화면으로 설계.

## 4. 사용자 잠정 결정 (2026-10-25)

### 4-1. 결정 문장

| ID | 결정 | 선택 이유 | 성격 |
|---|---|---|---|
| D22-1 | 알림은 **최선 노력**이다. 현재 구조(Commit 후, 별도 Transaction, 실패 시 로그)를 유지한다. 알림 실패는 업무 결과를 바꾸지 않는다 | 이번 프로젝트는 핵심 업무·정합성·조회 경험이 우선이다. 앱 안 목록뿐인 알림에서 유실은 "읽지 않은 알림 하나가 덜 보이는 것"이다 | 현재 동작 유지 |
| D22-2 | 알림 유실 가능성을 계약에 명시한다 (저장 실패, Commit 직후 프로세스 종료) | 알림을 상태 판단 근거로 쓰는 구현을 막는다 | 계약 설명 |
| D22-3 | 알림은 보조 수단이다. **사용자에게 중요한 업무 결과는 DB 기반 조회 API와 화면에서 확인할 수 있어야 한다.** 새 알림 Type을 추가할 때마다 대체 조회 화면을 지정한다 (§5 표) | 상태 확인을 화면이 보장하면 알림이 유실돼도 Flow가 닫힌다 | MVP 구현 완료 조건 |
| D22-4 | **종료된 대타 요청을 후보 화면에서 읽기 전용으로 보여 준다** (이번 MVP 포함). 최소 범위는 **수락했던 후보**다. 종료 이유를 구분해 보여 준다. 보이더라도 수락·거절 등 Command는 허용하지 않는다 | 수락한 직원에게 결과가 사라지면 이유를 알 수 없다. 알림을 보조 수단으로 둔 결정과 일관된다 | Improvement (조회 API·화면) |
| D22-5 | Transactional Outbox, 재시도, 중복 처리 방지, 별도 처리기, 외부 알림 채널은 **후속 Improvement**로 분리한다 | 비용이 크고 "Scheduler 없음" 방침과 충돌한다 | 범위 밖 |
| D22-6 | "조회할 수 있다"와 "사용자가 변경을 알아차린다"는 다르다. 로그인 후 예정 근무·빠진 근무가 바로 보이는지, 변경 표시가 눈에 띄는지는 Frontend UX 검토 항목으로 남긴다 | 앱을 열지 않으면 조회 API가 있어도 알 수 없다 | UX 검토 (P1 후보) |

### 4-2. 세부 결정 (S22-a·b 사용자 잠정 결정 2026-10-25)

| ID | 질문 | 결정 | 이유 |
|---|---|---|---|
| S22-a | 대기 중(PENDING)이던 후보에게도 종료된 요청을 보여 줄까 | **수락 이력이 있는 후보만.** PENDING·REJECTED는 지금처럼 목록에서 사라진다 | 후보 선정이 근무지 구성원 **전원**이라(§2) PENDING까지 넣으면 모든 직원이 근무지의 모든 종료 요청을 7일 동안 보게 된다. 응답하지 않은 직원에게 요청이 닫히는 것은 모집 공고가 닫히는 것과 같아 혼란이 작다. 조회 비용 차이는 거의 없으므로 나중에 넓히기 쉽다 |
| S22-b | 표시 기간의 기준 시각 | **요청의 "효력 종료 시각" 기준 최근 7일.** 저장된 종료는 DB `closedAt`, 승인은 `approvedAt`(= DB `closedAt`), 자동 만료는 G03-D-19 B1과 같은 `startAt` | D-19의 만료 시각과 같은 기준이라 화면 값과 목록 기간이 맞는다. 2주 뒤 근무의 요청이 오늘 닫히면 오늘부터 7일간 보인다. "빠진 근무"는 근무 자체를 다루므로 근무 시작 기준이고, 이것은 요청 결과를 다루므로 종료 기준이다 (기준이 다른 이유를 화면 계약에 적는다). **조회 범위이지 데이터 삭제 기간이 아니다** |

### 4-3. 저장 데이터로 재구성할 수 있는지 검증 (`a148a62` 정적)

| 확인 항목 | 결과 | 근거 |
|---|---|---|
| 수락 후 종료되면 Candidate 상태가 바뀌는가 | **바뀌지 않는다.** Candidate의 쓰기 메서드는 `reject`(PENDING에서만)와 `acceptRequest` 두 개뿐이다. 관리자 종료·승인·전원 거절은 Request만 바꾼다 | `SubstituteCandidate` L43-56, `SubstituteCandidateService` L141 (PENDING 아니면 `CANDIDATE_ALREADY_RESPONDED`), `SubstituteRequest` L69-97 |
| 현재 ACCEPTED만 조회하면 과거 수락자가 빠지는가 | **빠지지 않는다 (AS-IS).** ACCEPTED는 끝 상태다. 로컬 ML-3 Draft도 `ACCEPTED → INELIGIBLE`을 금지하고, 근무 인계·수락자 퇴사 때 ACCEPTED를 보존한다. 따라서 "현재 ACCEPTED" = "수락 이력 있음"이다. **ML-3이 이 금지를 유지해야 성립한다** (ML-3 리뷰 확인 항목) | ML-3 Draft §상태 전이, V4-N-08·N-17 |
| 종료 효력 시각 | 관리자 종료 `closedAt = now` (L92-96), 전원 거절 `closedAt = now` (L69-73), 승인 `approvedAt = closedAt = now` (L77-89), 자동 만료는 저장 없음 → `startAt` (D-19). ML-3의 `SHIFT_CHANGED`·`CANDIDATE_INELIGIBLE`은 Draft상 그 Command 시각을 `closedAt`에 저장 (구현 시 확인) | `SubstituteRequest` |
| 승인받은 후보와 아닌 후보 구분 | 수락자는 Request당 1명이다 (수락은 Request OPEN일 때만, 수락하면 ACCEPTED로 바뀜, L62-66). 내가 ACCEPTED이고 Request가 APPROVED면 승인 대상은 나다. **수락한 후보에게 "다른 후보가 선정됨"은 생기지 않는다** | `SubstituteRequest.accept` L62-66 |
| 종료 사유 표시 | Request 상태 + `closeReason` + 계산된 만료로 모두 구분된다 (§6-2). 새 Enum 불필요 | — |
| 현재 소속 기준 조회 | Candidate는 그때의 Membership(`member_id`)을 가리키므로 `c.member.leftAt IS NULL`로 이전 소속 기간이 분리된다. 응답은 본인 Candidate만 기준으로 하며, 기존 SUB-07 DTO에도 다른 후보 정보 Field가 없다 | `SubstituteCandidate` L25, `AcceptedPendingSubstituteRequestResponse` L8-20 |
| 종료된 요청에 수락·거절 | 거부된다. Candidate가 PENDING이 아니면 `CANDIDATE_ALREADY_RESPONDED`, Request가 OPEN이 아니거나 시작했으면 거부 | `SubstituteCandidateService` L141, L169 |

**미해결 항목:** 없음 (AS-IS 기준). ML-3의 새 종료 사유가 `closedAt`을 저장하는지와 ACCEPTED 보존은 ML-3 구현 계약으로 확인한다.

### 4-4. 승인된 수락자를 종료 내역에 넣을지 (S22-c, 사용자 잠정 결정 2026-10-25: (가) 포함)

| 선택 | 내용 | 장점 | 단점 |
|---|---|---|---|
| **(가) 포함 (선택)** | "승인됨"을 짧게 표시 | 수락한 요청이 목록에서 사라진 이유를 알 수 있다 (알림이 유실돼도). 추가 조회 비용 없음 | 예정 근무와 같은 근무가 두 화면에 보인다 |
| (나) 제외 | 승인된 근무는 예정 근무에서만 확인 | 화면 중복이 없다 | 수락 목록에서 사라진 이유를 사용자가 연결해야 한다 |

**조건:**

- APPROVED는 **그 시점에 대타 요청이 승인되었다는 과거 사실**이다. 현재 담당 여부를 뜻하지 않는다.
- 현재 담당자는 Shift의 현재 배정으로 판단한다. 종료된 대타 조회는 수락자를 Candidate(ACCEPTED)로 읽고, 현재 `Shift.member`로 표시하지 않는다 (ML-3-H). 승인 API 응답 DTO(`SubstituteRequestResponse.from`, 현재 `Shift.member`를 읽음)를 이 조회에 재사용하지 않는다 (`SubstituteRequestResponse` L23-24, 2026-10-25 정적 확인).
- 승인 뒤 그 근무가 다른 사람에게 인계되거나 취소되어도 APPROVED 이력은 그대로 둔다. 그 근무는 "빠진 근무"(ML-4)에 나타난다.
- 문구는 현재 담당을 단정하지 않는다. 예: "승인됨 — 현재 근무는 내 예정 근무에서 확인하세요."
- 7일 기준은 승인 효력 시각(`approvedAt`)이다. 근무가 아직 미래여도 이 목록에서는 7일 뒤 빠진다. 앞으로 할 근무는 예정 근무 화면이 근무일까지 보여 준다. 이 목록은 **처리 결과 확인용**이다.
- 본인의 현재 소속 Candidate만 조회한다 (§6-1).

| 화면 | 보여 주는 사실 |
|---|---|
| 종료된 대타 (수락한 요청) | 그 요청이 승인·종료·만료되었다는 처리 결과 |
| 예정 근무 | 지금 내가 담당하는 SCHEDULED 근무 |
| 빠진 근무 | 이후 인계·취소되어 내 담당에서 빠진 근무 |

같은 근무가 여러 화면에 나와도 각 화면이 다른 사실을 보여 주면 문제가 아니다.

## 5. 알림과 대체 조회 화면 (D22-3 점검표)

| 알림 (현재·예정) | 대체 조회 화면 | 상태 |
|---|---|---|
| 근무표 공개 | 내 근무 (예정 근무) | 있음 |
| 대타 요청 받음 | SUB-02 받은 요청 | 있음 |
| 후보 없음 | 생성 응답 + SUB-05 보낸 요청 | 있음 |
| 수락됨 | 관리자 진행 중 목록, SUB-05 | 있음 |
| 전원 거절, 관리자 종료, 승인 | SUB-05 (요청자). **수락했던 후보: D22-4 화면** | 요청자 있음. 후보는 D22-4 구현 후 |
| (예정) 근무 취소·인계 | 빠진 근무 (ML-4) | Draft |
| (예정) 새 배정 | 예정 근무 | 있음 |
| (예정) 대타 종료 `SHIFT_CHANGED`·`CANDIDATE_INELIGIBLE` | SUB-05 (label 추가), D22-4 화면. `SHIFT_CHANGED`의 PENDING 후보 제외, 실행자는 관리자 수신자 집합에서만 제외하며 요청자 역할 알림은 유지 ([NOTIFICATION-TYPES](../../contract/NOTIFICATION-TYPES.md) §6-3) | Draft |
| (예정) 가입·재가입 | 관리자 구성원 목록 | 있음 |
| (예정) 퇴사 결과 | 마이페이지 "내 소속 이력" (ML-4). 알림 자체는 퇴사자에게 보이지 않음 (§2) | Draft |

S3 추적표에서 "알림 Type ↔ 대체 화면"을 빠짐없이 다시 확인한다. 빈칸이 있으면 그 알림을 추가하지 않거나 화면을 먼저 만든다.

## 6. 종료된 대타 조회 계약 (D22-4, 설계 후보)

### 6-1. 대상과 조회 조건 (S22-a·b 추천 기준)

- 나의 **현재 소속**(`c.member.leftAt IS NULL`)의 Candidate 중 `ACCEPTED`.
- 그 Request가 끝난 것: APPROVED, CLOSED, 또는 OPEN·ACCEPTED이면서 `startAt ≤ now`(자동 만료).
- 효력 종료 시각이 `now - 7일` 이후.
- **재가입 분리:** Candidate가 그때의 Membership을 가리키므로(§2), 현재 소속 조건만으로 이전 소속 기간의 기록은 섞이지 않는다. 기존 SUB-07처럼 User 기준 + `leftAt` 조건 없이 쓰면 섞이므로 새 조회는 반드시 현재 소속 조건을 포함한다.

### 6-2. 화면 문구를 정하는 정보 (새 Enum 없이)

수락했던 후보(ACCEPTED) 기준:

| Request 상태 / 사유 | 의미 | 표시 예 |
|---|---|---|
| APPROVED | 내 수락이 승인됨 (ACCEPTED 후보는 Request당 1명, 로컬 ML-3 V4-N-21). 현재 담당 여부는 말하지 않음 (§4-4) | "승인됨 — 현재 근무는 내 예정 근무에서 확인하세요" |
| CLOSED / `MANAGER_CLOSED` | 관리자가 닫음 | "관리자가 요청을 종료했어요" |
| OPEN·ACCEPTED + `startAt ≤ now` (계산 `EXPIRED`) | 승인 전 근무 시작 | "근무 시작 시각에 만료되었어요" + 만료 시각 (D-19) |
| CLOSED / `SHIFT_CHANGED` (ML-3) | 근무 취소·인계 | "근무가 변경되어 요청이 종료되었어요" |
| CLOSED / `CANDIDATE_INELIGIBLE` (ML-3) | 수락자인 내가 퇴사한 경우에만 생김 → 나는 현재 소속이 아니라 이 화면에 오지 않음 | (도달 불가) |

- 수락했던 후보에게 "다른 후보가 선정됨"은 생기지 않는다. Request는 수락이 OPEN일 때 한 번만 가능하므로, 내가 ACCEPTED이면 승인 대상은 나다.
- 응답 Field는 기존 SUB-05처럼 원래 상태값(`requestStatus`, `closeReason`, `candidateStatus`)과 효력 종료 시각을 주고, 문구는 Frontend가 정한다. 서버가 표시용 새 Enum을 만들지 않는다.
- S22-a에서 PENDING을 포함하기로 바꾸면 "다른 직원이 수락함"(Request ACCEPTED·APPROVED + 나 PENDING) 행이 추가로 필요하다.

### 6-3. API 형태 (S2에서 확정)

- 후보 1: SUB-07 응답을 넓혀 진행 중과 종료를 함께 반환 → 기존 API 계약 변경 (기존 Test 영향).
- 후보 2 (추천): 새 조회 `GET /substitute-requests/accepted/closed` (이름 미정) → 기존 SUB-07 계약 유지, Migration과 분리 쉬움.
- 종료된 항목에는 응답 버튼을 표시하지 않는다. 서버 Command는 기존 검사(Request OPEN·시작 전)로 이미 거부한다.

## 6-4. 출시 차단 위험 평가 (교차 검토 요청, 2026-10-25 정적 확인)

| 위험 | 확인한 사실 | 평가 | 처리 |
|---|---|---|---|
| SUB-07 수락한 요청이 재가입 후 이전 소속 기록을 보여 줌 | SUB-07은 User 기준이고 `c.member.leftAt` 조건이 없다 (L82-97). 하지만 조건이 "Request ACCEPTED ∧ 시작 전"이다. 로컬 ML-3은 수락자가 퇴사하면 그 Request를 `CANDIDATE_INELIGIBLE`로 닫는다. 시작한 요청은 시간 조건으로 빠진다 | ML-3 종료 처리와 함께 배포하면 이전 소속 기록은 나오지 않는다 (추론). 퇴사를 ML-3 없이 배포하면 나온다. 같은 근무지를 떠났지만 다른 근무지에는 남은 사용자에게도 같은 문제다 | **퇴사·재가입 묶음의 P0 조건.** 방어로 `c.member.leftAt IS NULL` 조건 추가를 같은 묶음에 넣는다 (기존 API 동작 변경, 승인 대상) |
| SUB-05 보낸 요청이 재가입 후 이전 소속 요청을 섞어 보여 줌 | `findAllByRequesterMember_User_IdOrderByCreateDateDesc` (User 기준, 소속 조건 없음, `SubstituteRequestRepository` L20) | 재가입하면 이전 기간의 요청이 현재 목록에 섞인다. 로컬 요약 Draft §3이 "보낸 대타 목록을 현재 소속 기준으로" 변경을 이미 계획했다 | **재가입 묶음의 P0 조건** (같은 처리) |
| Publish 확인 Flag가 `boolean` 1개 | 서버의 409 응답은 `ConfirmationRequiredResponse(true)`뿐이고 **어느 근무·직원이 충돌하는지 보내지 않는다** (`ScheduleService` L504-520). Frontend는 `window.confirm`으로 메시지만 보여 주고 곧바로 `true`로 다시 보낸다 (`schedule/page.tsx` L627-650) | 경고와 재전송 사이는 사람이 확인 버튼을 누르는 몇 초다. 그 사이에 새 충돌이 생겨 함께 통과할 가능성은 낮다. 통과해도 관리자가 불가능 시간 충돌을 확인하고 공개한다는 정책(D21-1) 안의 결과이고, 불변식(공식 근무 중복 등)은 깨지지 않는다. **더 큰 문제는 관리자가 어떤 충돌인지 볼 수 없다는 점**이다 | 출시 차단 아님 (현재 2차 서비스와 같은 동작). **P1:** 409 응답에 충돌 목록을 넣고 화면에 표시. 목록 기반 확인으로 바꾸는 것은 P1 이후. 퇴사 일괄 인계(D21-7·8)는 미리보기와 확정 사이가 길고 여러 근무를 한 번에 바꾸므로 목록 확인을 P0로 둔다 |

## 7. 보장 범위와 제외 범위

| 보장 | 제외 |
|---|---|
| 알림 실패가 업무 결과를 바꾸지 않음 | 알림 전달 보장 (Outbox·재시도) |
| 중요한 업무 결과는 화면에서 확인 가능 (§5) | 외부 채널 (푸시·메일) |
| 수락했던 후보가 최근 7일 종료 결과와 이유를 확인 | PENDING·REJECTED 후보의 종료 내역 (S22-a 추천 기준) |
| 이전 소속 기간 기록이 섞이지 않음 | 7일보다 오래된 종료 내역 화면 (기록은 지우지 않음) |
| | 퇴사자의 알림 조회 |

## 8. 후속 Outbox 도입 조건 (D22-5)

- 알림 유실로 실제 운영 문제가 반복될 때
- 푸시·메일 같은 외부 전달 채널을 추가할 때
- 알림을 업무 판단 근거로 써야 하는 기능이 생길 때

도입하면 Event 발행 시점이 바뀌므로 AI-RULES §2 승인 대상이다.

## 9. 검증 계약 (전부 ④ `SPECIFIED` / ⑤ `NOT_RUN`)

번호는 S2에서 V4·V5 Inventory 번호로 바꾼다.

| 임시 ID | Given | When | Then |
|---|---|---|---|
| D22-T1 | 알림 저장이 실패하도록 주입 | 근무표 공개 | 공개 성공·HTTP 성공, 알림 Row 0, 오류 로그 1 (현재 동작 고정, MIGRATION) |
| D22-T2 | 내가 ACCEPTED, 관리자가 종료 | 종료된 대타 조회 | 1건, `MANAGER_CLOSED`, 효력 종료 시각 = DB `closedAt` |
| D22-T3 | 내가 ACCEPTED, 승인 전 근무 시작 | 조회 (`now = startAt + 1h`) | 1건, 계산 `EXPIRED`, 효력 종료 시각 = `startAt` |
| D22-T4 | 내가 ACCEPTED, 승인됨 (`approvedAt` = 오늘, 근무는 3주 뒤) | 조회 (오늘, 8일 뒤) | 오늘 1건 APPROVED. 8일 뒤 0건. 예정 근무에는 근무일까지 보임 |
| D22-T4b | D22-T4 이후 관리자가 그 근무를 C에게 인계 (ML-2) | 조회 | APPROVED 1건 그대로, 응답에 현재 담당자(C) 정보 없음. B의 빠진 근무에 1건 |
| D22-T5 | 종료 후 8일 경과 | 조회 | 0건 (Row는 DB에 그대로) |
| D22-T6 | 이전 소속에서 ACCEPTED 후 종료 → 퇴사 → 재가입 | 조회 | 이전 소속 기록 0건 |
| D22-T7 | 종료된 요청 | 수락·거절 시도 | 기존 거부 그대로, 상태 불변 |
| D22-T8 | 내가 PENDING, 요청 종료 | 조회 | 0건 (S22-a) |
| D22-T9 | (ML-3 승인 후) 근무 인계로 종료 | 조회 | `SHIFT_CHANGED` |
| D22-T10 | W1에서 ACCEPTED 상태로 W1 퇴사, W2에는 소속 유지 | SUB-07 조회 | W1 요청 0건 (ML-3 종료 + 현재 소속 조건, §6-4) |
| D22-T11 | W1에서 요청을 보낸 뒤 퇴사 → W1 재가입 | SUB-05 조회 | 이전 소속 기간 요청 0건 (§6-4, ML-4 현재 소속 기준) |
| D22-T12 | 다른 직원의 Candidate | 종료된 대타 조회 | 본인 Candidate만 반환. 다른 후보의 이름·상태 Field 없음 |

## 10. 재검토 조건

- §8의 Outbox 도입 조건 중 하나가 생기면 D22-1·5를 다시 연다.
- 직원이 "응답 안 한 요청의 결과도 보고 싶다"는 요구가 있으면 S22-a를 넓힌다.

## 11. Cross-Domain 영향과 리뷰

| Domain | 영향 | 리뷰 |
|---|---|---|
| V5 Notification | 현재 구조 유지, 새 Type마다 대체 화면 지정 | 필수 |
| V4 Substitute | 종료된 대타 조회 API (새 조회) | 필수 |
| V2 Workplace | 현재 소속 조건 (재가입 분리) | 확인 |
| Frontend | 수락한 요청 화면에 종료 탭·문구, 변경 인지 UX (D22-6) | 필수 |

## 12. 후속 카드로 넘긴 항목

| 항목 | 넘긴 곳 |
|---|---|
| 새 조회 API 이름·응답 Field | S2 API 계약 목록 |
| 새 알림 Type 목록과 대체 화면 연결 | S2 NotificationType 목록, S3 추적표 |
| 기존 SUB-07의 `leftAt` 조건 누락 (AS-IS 보존 대상) | ML-4 (현재 업무는 현재 소속 기준), Migration은 그대로 |
| 변경 인지 UX | Frontend, P1 후보 |

## 13. 변경 이력

| 일자 | 내용 | 이유 |
|---|---|---|
| 2026-10-25 | 초안 작성. 사용자 잠정 결정 D22-1~6, 세부 S22-a·b는 추천 상태 | 사용자 선택: 질문 1 (가), 질문 2 (b). 교차 검토 의견(조회 가능 ≠ 변경 인지, 종료 이유 구분, 재가입 분리, Outbox 도입 조건) 반영 |
| 2026-10-25 | S22-a·b를 사용자 잠정 결정으로 변경. §4-3 저장 데이터 재구성 검증, §4-4 S22-c(승인된 수락자 포함 여부, 확인 대기), §6-4 출시 차단 위험 평가(SUB-07·SUB-05·Publish 확인) 추가 | 사용자 선택과 교차 검토 요청. 코드 정적 확인 |
| 2026-10-25 | S22-c를 (가) 포함으로 사용자 잠정 결정. 승인 이력과 현재 담당 구분 조건, 7일 기준 = `approvedAt`, 화면별 사실 표, D22-T4b 추가 | 사용자 선택. 교차 검토 의견(승인됨 ≠ 현재 담당) 반영 |
| 2026-10-25 | S3: API ID 표기 정정 (받은 요청 SUB-06 → SUB-02, V4 Delivery Sheet B1 기준. 내용 변경 없음). L126의 "알림 Type ↔ 대체 화면" 추적은 [USER-PATH-REVIEW](../../contract/USER-PATH-REVIEW.md) §8 | S3 교차 대조 (X-5) |
