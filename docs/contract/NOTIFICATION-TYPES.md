# NotificationType 공통 목록

> **현재 승인 상태 (2026-10-26):** 사용자 전달 구두 승인과 PR 리뷰에 따른 채택 범위·예외는 [Decision Log](../decisions/DECISION-LOG.md) §4를 따른다. 아래 최초 작성 당시 PROPOSED/PENDING 표기는 이력이며 현재 승인 범위를 덮어쓰지 않는다. 구현·실험·배포 및 미결정 사항은 각각 별도 상태다.

| 축 | 상태 |
|---|---|
| ③ 문서 | `DRAFT` (S2-4B 초안, 2026-10-25) |
| ② 정책 | 기존 8종은 **AS-IS 기록**. 새 Type은 최초 작성 당시 후보 (`PROPOSED`)였고, 현재 [Decision Log](../decisions/DECISION-LOG.md) §4-2 G03-D-22 적용 조건("새 알림6종·상세 수신자/합치기 규칙")으로 채택. 같은 행의 "필요한 대체 화면이 해당 기능 묶음에서 구현·검증되어야 완료" 조건은 §8 남은 공백 참고 |
| 구현 | 최초 작성 당시 없음. **2026-10-29 새 Type 6종 구현** (`feat/v5-shift-notifications`, 아래 §8). 기존 8종의 Type·문구·발행 시점은 그대로 |
| ④ / ⑤ | 최초 작성 당시 전부 `SPECIFIED` / `NOT_RUN`. 새 Type 구현 Test는 §8 (로컬 실행, PR 기준) |
| 기준 코드 | 3차 `develop` `a148a62` (Production Java = `cdaaa13`). 정적 확인 |
| 전달 수준 | **최선 노력** ([G03-D-22](../decisions/proposals/G03-D-22-notification-delivery.md) D22-1~5). 알림은 보조 수단이고, 업무 결과는 대체 조회 화면이 보장한다. Outbox·재시도는 이번 범위 밖 |

새 Type을 추가할 때마다 **대체 조회 화면**을 함께 정한다. 대체 화면이 없으면 그 알림을 추가하지 않거나 화면을 먼저 만든다 (G03-D-22 D22-3).

---

## 1. AS-IS (① `VERIFIED_AS_IS`, 정적 확인)

| 항목 | 현재 | 근거 |
|---|---|---|
| 저장 | `Notification { recipientMember (WorkplaceMember, NOT NULL), type (@Enumerated STRING), message (고정 문구), readAt }`. 받는 사람은 **User가 아니라 그때의 소속(WorkplaceMember)** | `notification/entity/Notification.java` |
| 발행 시점 | 업무 Transaction 안에서 받는 사람 소속 ID 목록을 만들어 Event로 발행 → **Commit 후** Listener(`AFTER_COMMIT`)가 별도 Transaction(`REQUIRES_NEW`)으로 저장 → 실패하면 `log.error`만 | `*EventListener.java`, `NotificationService` |
| 묶음 실패 | 받는 사람 ID 중 하나라도 조회되지 않으면 `IllegalStateException`으로 **그 Event의 알림 전체가 저장되지 않는다** (로그만). 소속 Row는 지워지지 않으므로 지금은 도달하기 어렵다. 이것은 "알림 실패가 업무를 Rollback하지 않는다"(정상)와 다른 문제다: **한 명의 문제로 다른 사람의 알림까지 사라질 수 있는 현재 한계** | `NotificationService` L63-78 등 |
| 문구 저장 | 문구는 **알림 Row마다 저장**된다 (`message` Column, `create(recipient, type, message)`). Type별 고정 문구는 지금 코드가 상수를 넘기는 방식일 뿐이라, 같은 Type에 다른 문구를 저장할 수 있다 | `NotificationService` L46-57 |
| Frontend 표시 | 알림 목록은 **서버가 저장한 `message`를 그대로 보여 준다.** Type으로 문구를 만들지 않는다 | `AppHeader.tsx` L21, L411 |
| 받는 사람 계산 | 관리자 목록은 `findAllByWorkplace_IdAndRoleAndLeftAtIsNull` (현재 관리자만). 요청자·후보 목록은 Request의 소속 ID를 그대로 씀 (**현재 소속인지 거르지 않음**). 저장할 때도 `leftAt`을 다시 보지 않는다 | `SubstituteRequestService` L455-528, `SubstituteCandidateService` L308-364 |
| 조회·읽음 | 목록과 읽음 모두 "수신 소속의 User = 나 ∧ 수신 소속이 현재 소속" (퇴사자는 자기 알림을 볼 수 없음) | `NotificationRepository` L19, `NotificationService` L310-317 |
| 재가입 격리 | 알림이 그때의 소속을 가리키므로, 재가입한 새 소속에는 이전 소속의 알림이 보이지 않는다 (이전 소속은 `leftAt`이 있어 목록 조건에서 빠짐) | 위 두 조건 |
| DB 저장 형식 | 세 가지를 구분한다. ① **JPA 매핑:** `@Enumerated(EnumType.STRING)` — Enum 값을 문자열로 읽고 쓴다는 뜻일 뿐 Column 형식을 정하지 않는다. ② **Hibernate가 만드는 DDL:** Spring Boot 4.1.1(Hibernate 7) + MySQL에서는 `ENUM(...)` Column을 만드는 것으로 **추정**한다 (확인 안 함). ③ **운영 Schema:** Column이 언제 어떤 버전으로 만들어졌는지에 따라 `VARCHAR`일 수도 `ENUM`일 수도 있고 CHECK 제약이 있을 수도 있다. 운영 `ddl-auto: update`는 일부 스키마 변경을 시도할 수 있으나 검토된 제약 제거·Enum 이행·데이터 복구를 보장하지 않는다. 실행·복구 순서는 [OPS-01](../delivery/TASK-OPS-01-prod-config-failfast.md) §3·§5를 따른다. **운영 형식은 `UNKNOWN`** (운영 접속 금지, 배포 담당 확인). `VARCHAR`이고 제약이 없으면 DDL이 필요 없고, `ENUM`이거나 CHECK가 있으면 DDL이 필요하다. ②는 S4 격리 실험(Testcontainers MySQL)에서 `SHOW CREATE TABLE`로 확인할 수 있다. ML-3의 Enum DDL과 같은 문제 | `build.gradle.kts` L5, `application-prod.yml` L10, ML-3 §9 |

## 2. 기존 8종 (AS-IS)

| Type | 발행 사건 | 받는 사람 | 문구 | 대체 조회 화면 | 상태 |
|---|---|---|---|---|---|
| `SCHEDULE_PUBLISHED` | 근무표 공개 | 그 근무표의 근무 담당 소속 | "새로운 주간 근무표가 공개되었습니다." | 예정 근무 | 발행 중 (`ScheduleService` L612-630) |
| `SUBSTITUTE_REQUEST_RECEIVED` | 대타 요청 생성 | 후보 (PENDING) | "새로운 대타 요청이 도착했습니다." | 받은 요청 | 발행 중 |
| `SUBSTITUTE_NO_CANDIDATE` | 대타 요청 생성 시 후보 0명 | 현재 관리자 | "대체 근무 가능한 후보가 없습니다." | (요청자) 생성 응답, 보낸 요청 | 발행 중 |
| `SUBSTITUTE_ACCEPTED` | 후보 수락 | 현재 관리자 | "대체 근무 수락자가 생겼습니다. 최종 승인이 필요합니다." | 관리자 진행 중 목록 | 발행 중 |
| `SUBSTITUTE_ALL_REJECTED` | 마지막 후보 거절 | 요청자 + 현재 관리자 | "대체 근무 요청을 받은 모든 수신자가 거절했습니다." | 보낸 요청 | 발행 중 |
| `SUBSTITUTE_APPROVED` | 관리자 승인 | 요청자, 수락자 (문구 2종) | "요청한 대체 근무 변경이 최종 승인되었습니다." / "수락한 대체 근무가 공식 근무로 확정되었습니다." | 보낸 요청, 예정 근무 | 발행 중 |
| `SUBSTITUTE_MANAGER_CLOSED` | 관리자 종료 | 요청자 + (OPEN이면 PENDING 후보 / ACCEPTED면 수락자) | "관리자가 대체 근무 요청을 종료했습니다." | 보낸 요청, (수락자) 수락한 요청 — 종료 (G03-D-22) | 발행 중 |
| `SUBSTITUTE_EXPIRED` | — | — | — | — | **Enum에만 있고 발행 경로 없음.** 만료 알림 정책(G03-D-20)이 정해지기 전에는 **지우지도 활성화하지도 않는다** |

## 3. 새 Type 후보

### 3-1. 목록

| 후보 Type | 발행 사건 (API) | 받는 사람 (현재 소속만) | 문구 목적 (후보 문구) | 대체 조회 화면 | 근거 |
|---|---|---|---|---|---|
| `SHIFT_ASSIGNED` | 공개 후 근무 추가 (API-PS-01), 공개 후 담당자 변경의 **새 담당** (API-PS-03), 퇴사 인계의 **인계받는 직원** (API-LV-02) | 새 담당 | 새로 해야 할 근무가 생겼다 ("새 근무가 배정되었습니다") | 예정 근무 | ML-2 §8 알림, ML-4 §6-1 |
| `SHIFT_CANCELLED` | 공개 근무 취소 (API-PS-02) | 원래 담당 | 내 근무가 취소되었다 ("근무가 취소되었습니다") | 빠진 근무 | ML-2 §8, D26 |
| `SHIFT_REASSIGNED_AWAY` | 공개 후 담당자 변경의 **이전 담당** (API-PS-03) | 이전 담당 | 내 근무가 다른 직원에게 넘어갔다 ("근무가 다른 직원에게 넘어갔습니다", **새 담당 이름 없음**) | 빠진 근무 | ML-2 §8, FRONTEND-CONTRACT §9-2 |
| `SUBSTITUTE_SHIFT_CHANGED` | 근무 취소·인계로 그 근무의 활성 요청이 `SHIFT_CHANGED`로 종료 (API-PS-02·03, API-LV-02) | 요청자, **ACCEPTED 후보**, 현재 관리자 중 **실행한 관리자를 뺀** 관리자 (모두 현재 소속만). **PENDING 후보는 받지 않음** (§6-3) | 근무가 바뀌어 대타 요청이 끝났다 (할 일 없음) | §3-6 | ML-3 §6, ML-3 L125-126, 사용자 잠정 결정 2026-10-25 (§6-1) |
| `SUBSTITUTE_CANDIDATE_INELIGIBLE` | 응답 가능한 후보가 없어져 `CANDIDATE_INELIGIBLE`로 종료 (거절 또는 퇴사 처리 안) | 요청자, 현재 관리자 (다른 후보는 B+ 원문 기준 대상 아님). **퇴사로 생긴 경우에도 퇴사자만 빠지고 요청자·관리자는 받는다** (S3-A: 퇴사 확정 Command로 생긴 경우 **그 Command를 실행한 관리자는 관리자 수신에서 제외**, 실행자가 그 요청의 요청자이면 요청자로서는 받음 — §6-3-2) | 진행할 수 있는 후보가 없어 끝났다. 근무는 그대로이니 필요하면 다시 요청 | §3-6 | ML-3 V4-N-14, ML-SUB-STATE-01 §5, 사용자 잠정 결정 2026-10-25 (§6-1) |
| `WORKPLACE_MEMBER_JOINED` | 가입·재가입 성공 (`POST /workplaces/join`) | 그 근무지의 현재 관리자 | 직원이 가입·재가입했다 (문구로 신규·재가입 구분). 사후 안내이지 승인이 아님 | 구성원 목록 | ML-H2 H2-10·H2-11 |

**보내지 않는 것:**
- **퇴사자에게 보내는 알림** (퇴사 결과, 퇴사자 근무의 취소·인계). 퇴사 확정으로 소속이 끝나므로 알림 목록 조건(현재 소속)에서 보이지 않는다. 퇴사자는 마이페이지 "내 소속 이력"에서 확인한다 (G03-D-22 §5, ML-2 §8 "퇴사자는 받지 않음").
- 초대 코드 재발급 (관리자가 직접 전달, ML-H2).
- `INELIGIBLE`로 바뀐 후보 본인 (퇴사자이므로).

### 3-2. 받는 사람 규칙

| 규칙 | 내용 |
|---|---|
| R1 현재 소속만 | 받는 사람은 **User가 아니라 그 근무지의 WorkplaceMember 상태**로 정한다. 다른 근무지에 소속이 남아 있는 사람이라도, 이 근무지 소속이 끝났으면 이 근무지 알림은 받지 않는다. 받는 사람 소속 ID를 만들 때 **현재 소속(`leftAt IS NULL`)만** 넣는다. 업무 Transaction 안에서 Lock 이후의 상태로 계산한다 (퇴사 Command는 소속 종료와 같은 Transaction이므로 퇴사자는 빠진다). AS-IS의 요청자·후보 목록은 이 필터가 없다 (§1) — 새 알림부터 적용하고, 기존 알림의 같은 변경은 Improvement 후보로 기록한다 |
| R2 Commit 이후 | 알림 저장은 업무 Commit 이후 별도 Transaction (AS-IS와 같음). 알림이 실패해도 업무 결과는 그대로다 |
| R3 Commit과 저장 사이의 퇴사 | Commit과 알림 저장 사이(매우 짧음)에 받는 사람이 퇴사하면 알림 Row는 생기지만 목록 조건에서 보이지 않는다. 결과가 틀리지 않으므로 허용한다 |
| R4 재가입 격리 | 알림은 그때의 소속 ID에 저장한다. 재가입 후 새 소속에는 이전 소속 알림이 보이지 않는다 (AS-IS로 이미 성립) |
| R5 묶음 실패 범위 | AS-IS는 받는 사람 하나가 조회되지 않으면 그 Event의 알림이 전부 저장되지 않는다. 새 알림은 소속 Row가 지워지지 않는 한 같은 문제가 없다. 받는 사람별로 따로 저장할지는 구현 때 정한다 (P1) |

### 3-2-1. 소속 확인 시점

- 받는 사람 목록은 **업무 Transaction 안, Lock 이후의 상태**로 만든다 (R1).
- 알림 저장은 Commit 후라, 그 사이 받는 사람이 퇴사하면 Row는 생기지만 목록 조건에서 보이지 않는다 (R3). **조회 권한(현재 소속)과 결과가 같으므로 허용한다.** 이 경합은 Test로 남긴다 (NT-T9).

### 3-3. 한 Command에서 같은 사람에게 알림이 여러 개 생기는 경우

**같은 Transaction에서 생겼다고 무조건 중복으로 보지 않는다.** 다음 세 조건을 **모두** 만족할 때만 하나로 합친다:
1. 받는 사람이 같다
2. 사용자가 할 행동이 같다 (또는 한쪽 알림이 다른 쪽 결과일 뿐 따로 할 일이 없다)
3. 합쳐도 전달하려는 사실이 빠지지 않는다 (문구에 두 사실을 모두 담을 수 있다)

목적이 다른 알림(예: 근무가 넘어감 vs 다시 대타를 요청해야 함)은 따로 보낸다.

| Command | 같은 사람이 받게 되는 알림 | 처리 |
|---|---|---|
| 공개 후 담당자 변경 A → C, A가 그 근무로 대타 요청 중 | A: `SHIFT_REASSIGNED_AWAY` + 대타 종료(요청자) | 근무가 넘어갔으므로 A가 대타에 대해 할 일이 없다 → 세 조건 충족 → 합침 (§6-2) |
| 공개 근무 취소, 담당 A가 대타 요청 중 | A: `SHIFT_CANCELLED` + 대타 종료(요청자) | 근무가 취소되어 대타에 할 일 없음 → 합침 (§6-2) |
| 담당자 변경 A → B, B가 A 요청의 PENDING 후보 | B: `SHIFT_ASSIGNED`만 | PENDING 후보는 근무 변경 종료 알림을 받지 않으므로(§6-3) 합칠 것이 없다. B가 ACCEPTED 후보였다면 §3-4대로 합침 |
| (참고) 후보 자격 상실 종료 | 요청자: 대타 종료(자격 상실)만 | 근무는 그대로 요청자 것이고 다시 요청할지 정해야 함 → 다른 알림과 합치지 않음 |
| 관리자 종료 대신 근무 변경으로 끝난 요청 | `SUBSTITUTE_MANAGER_CLOSED`를 함께 보내지 않는다 | ML-3 L125 (이미 결정) |
| 퇴사 확정 (근무 여러 개 인계) | 인계받는 직원: 근무마다 `SHIFT_ASSIGNED` 1건 | 근무 단위로 보낸다 (한 건으로 합치는 것은 P1) |

### 3-4. 받는 사람별 판단 (예: A의 근무를 관리자 M이 C에게 인계, A의 요청을 B가 수락한 상태)

합치기는 **받는 사람마다 따로** 판단한다. 한 사람의 알림을 합쳤다고 다른 사람의 알림을 줄이지 않는다.

| 사람 (역할) | 일어난 일 | 보내는 알림 | 합치기 |
|---|---|---|---|
| A (원래 담당·요청자) | 근무가 C에게 넘어감 + 자기 요청 종료 | `SHIFT_REASSIGNED_AWAY` 1건, 문구 "근무가 다른 직원에게 넘어갔습니다. 진행 중이던 대타 요청도 종료되었습니다." | 합침 (세 조건 충족) |
| B (수락했던 후보) | 수락했던 요청 종료 | `SUBSTITUTE_SHIFT_CHANGED` 1건 | 해당 없음 (B에게 다른 알림 없음) |
| C (새 담당) | 새 근무 배정 | `SHIFT_ASSIGNED` 1건 | 해당 없음 |
| C가 그 요청의 PENDING 후보이기도 했다면 | 새 근무 배정 (+ 후보였던 요청 종료) | `SHIFT_ASSIGNED` 1건. PENDING 후보는 종료 알림을 받지 않으므로(§6-3) 문구에 종료를 덧붙이지 않는다 | 해당 없음 |
| C가 그 요청의 **ACCEPTED** 후보였다면 (수락했지만 승인 전에 관리자가 C에게 직접 배정) | 새 근무 배정 + 수락했던 요청 종료 | `SHIFT_ASSIGNED` 1건, 문구에 "수락했던 대타 요청은 종료되었습니다"를 덧붙임 | 합침 (세 조건 충족) |
| M (실행한 관리자) | 자기가 한 일 | **보내지 않음.** 확정 응답에서 결과를 본다 (API-PS-02·03의 `closedSubstituteRequests`, API-LV-02의 `closedRequests`) | — |
| 다른 현재 관리자 | 요청 종료 | `SUBSTITUTE_SHIFT_CHANGED` 1건 | — |
| D (응답하지 않은 PENDING 후보) | 요청 종료 | **보내지 않음** (§6-3) | — |

**DB는 합치지 않는다:** 알림을 하나로 합쳐도 근무 변경(배정 이력), 대타 종료(`SHIFT_CHANGED`, `closedAt`), Candidate 상태는 각각 기록된다 (ML-2·ML-3). 합치는 것은 사용자에게 보내는 안내뿐이다.

**문구 작성 주의:** 덧붙이는 문구는 그 사람의 **실제 역할**(요청자 / 수락한 후보)에 맞게 쓴다. 수락하지 않은 사람에게 "수락했던"을 쓰지 않는 등, 일어나지 않은 행동을 한 것처럼 표현하지 않는다. 이것은 Type이 아니라 문구 작성 로직의 책임이다.

**합친 알림의 Type:** 별도 통합 Type을 만들지 않는다. `SHIFT_REASSIGNED_AWAY`·`SHIFT_CANCELLED`·`SHIFT_ASSIGNED`를 쓰고 **서버가 만든 문구에** 대타 종료를 덧붙인다. 문구가 Row마다 저장되고 Frontend가 그 문구를 그대로 보여 주므로(§1) Frontend 변경이 없다. Frontend가 Type만 보고 고정 문구를 만들지 않는다는 조건을 계약으로 둔다.

### 3-5. 서로 다른 두 근무

근무 두 개가 각각 바뀌면 각각 별도 사건으로 보낸다 (퇴사 확정의 근무별 `SHIFT_ASSIGNED`와 같음).

### 3-6. 대체 조회 화면이 실제로 있는가 (API 조회 조건으로 확인)

| 받는 사람 | 알림 | 대체 화면 | 실제 조회 가능 여부 |
|---|---|---|---|
| 요청자 | 대타 종료 2종, 합친 알림 | 보낸 요청 **"지난 요청" 탭** (`sent/page.tsx` L27, L200) | **가능.** SUB-05는 종료된 요청도 돌려주고 화면이 탭으로 나눈다. 조건: CHG-02(현재 소속 요청만) 적용, 새 사유 `SHIFT_CHANGED`·`CANDIDATE_INELIGIBLE` 문구 추가 (FRONTEND-CONTRACT §4-2) |
| 수락했던 후보 | `SUBSTITUTE_SHIFT_CHANGED` | 수락한 요청 — 종료 (API-SB-01, 새 API) | API-SB-01 구현 후 가능. **지금 받은 요청 화면은 "대기"·"수락" 탭만 있고 진행 중만 보여 준다** (`received/page.tsx` L51) |
| PENDING 후보 | **보내지 않음** (§6-3) | 받은 요청 목록에서 사라짐 (G03-D-22 S22-a) | 알림이 없으므로 대체 화면 규칙과 충돌하지 않는다. **이미 열린 화면 처리:** 다시 조회하면 받은 요청 목록에서 빠진다(SUB-02 조건 `Request OPEN`). 그 화면에서 수락·거절을 누르면 서버가 `SUB-023` 409 "더 이상 응답할 수 없는 대타 요청입니다"로 거부한다 (`SubstituteCandidateService` L141-147, Candidate가 PENDING이어도 Request가 OPEN이 아니면 거부). 화면은 목록을 다시 조회한다. PENDING 후보용 종료 내역 화면은 이번 MVP에 넣지 않는다 |
| 새 담당 | `SHIFT_ASSIGNED` | 예정 근무 (기존) | 가능 |
| 이전 담당 | `SHIFT_REASSIGNED_AWAY`, `SHIFT_CANCELLED` | 빠진 근무 (API-SC-01, 새 API) | **가능** (API-SC-01 PR #95 병합) |
| 실행한 관리자 | 보내지 않음 (§6-3) | 확정 응답 (변경·취소된 근무 수, 종료된 대타 요청 수) | API-LV-02 응답에 `closedRequests`가 있고, API-PS-02·03 응답에 `closedSubstituteRequests`를 추가했다 (API-CATALOG) |
| 다른 현재 관리자 | 대타 종료 2종 | 관리자 진행 중 목록에서 **사라짐**. 관리자용 종료 요청 목록은 없다 | **약함.** AS-IS의 `SUBSTITUTE_ALL_REJECTED`(관리자 수신)도 같은 상태다. **근무표는 "지금 누가 담당인가"만 보여 주고, "누가 수락했고 왜 종료됐는가"는 보여 주지 않는다** — 근무표가 대타 이력을 대신하지 않는다. 관리자용 종료 내역 조회는 **P1 후보**, 필요 업무가 있는지 S3에서 다시 평가. **해결된 공백이 아니다. S3 차단 조건**이며, S3에서 관리자 업무 경로로 결론을 내기 전에는 알림 정책을 완료로 보지 않는다. G03-D-22의 대체 화면 원칙을 관리자 알림 전체(기존 `SUBSTITUTE_ALL_REJECTED` 포함)에 적용할지도 S3에서 함께 정한다. → **S3-A: 관리자 끝난 대타 요청 조회 API-SB-02 (P0 후보, 팀 `PENDING`)를 대체 화면으로 한다.** `ALL_REJECTED`·`CANDIDATE_INELIGIBLE`·`SHIFT_CHANGED`와 알림 없는 만료까지 포함, `NO_CANDIDATE`는 제외. 구현 전까지 공백은 남는다 |
| 관리자 | `WORKPLACE_MEMBER_JOINED` | 구성원 목록 (기존) | 가능 |

## 4. DB·운영 영향

- 새 Type은 Enum 값 추가다. 운영 Column이 MySQL `ENUM`이거나 CHECK 제약이 있으면 **운영 DDL**(Column 정의 변경)이 필요하고, 제약 없는 `VARCHAR`면 필요 없다 (§1, 운영 형식 `UNKNOWN`). ML-3의 `CandidateStatus`·`RequestCloseReason` 확장과 **같은 배포 창에서** 처리하는 것을 권한다 (IMPLEMENTATION-PRIORITY "운영 DDL·Rollback은 P0 배포 조건").
- **호환:** 새 앱은 기존 값을 그대로 읽는다. 반대로 **이전 앱은 새 값을 읽지 못한다.**
- **Rollback 주의:** 새 Type 값이 저장된 뒤 이전 버전 앱으로 되돌리면 이전 앱은 그 값을 Enum으로 읽지 못해 알림 목록 조회가 실패(500)할 수 있다 (ML-SUB-STATE-01 §5와 같은 위험). Rollback 절차에 데이터 처리 방안이 필요하다.
- 운영 Column 형식·값 목록 확인은 배포 담당 확인 항목이다 (운영 DB 접속 금지).

## 5. 검증 계약 (임시 ID, 전부 `SPECIFIED` / `NOT_RUN`. 정식 ID는 NT-T{n} = V5-N-{n} (n ≤ 16. V5-N-17·18은 다른 출처라 S3-A 이후 새 알림 Test는 V5-N-20부터 직접 번호), [TEST-INVENTORY](../evidence/TEST-INVENTORY.md) §5-1·§6)

| 임시 ID | 내용 |
|---|---|
| NT-T1 | 새 알림마다: 업무 Commit 후 생성. 알림 저장 실패를 주입해도 업무 결과·HTTP 응답은 그대로 (G03-D-22 D22-T1 방식) |
| NT-T2 | 받는 사람에 퇴사자가 포함되지 않음 (퇴사 확정, 공개 후 변경 각각) |
| NT-T3 | 재가입 후 새 소속의 알림 목록에 이전 소속 알림 0건 (AS-IS 고정, MIGRATION) |
| NT-T4 | `SHIFT_REASSIGNED_AWAY`·`SHIFT_CANCELLED` 응답·문구에 새 담당자 정보 없음 |
| NT-T5 | 근무 변경으로 끝난 요청에 `SUBSTITUTE_MANAGER_CLOSED` 0건, 종료 알림은 받는 사람별 규칙대로 (§3-4, §6-2) |
| NT-T6 | 가입·재가입 후 그 근무지 현재 관리자에게만 1건, 문구로 신규·재가입 구분 |
| NT-T7 | 알림이 없어도 대체 조회 화면에서 같은 사실을 확인 (§2·§3 대체 화면 칸) |
| NT-T8 | `SUBSTITUTE_EXPIRED`는 발행되지 않음 (현재 동작 고정) |
| NT-T9 | Commit 후 알림 저장 전에 받는 사람이 퇴사 (경합 주입) → Row가 생겨도 그 사람의 알림 목록에 보이지 않음, 업무 결과 그대로 |
| NT-T10 | §3-4 표: A에게 합친 1건, B·C에게 각 1건, B·C의 알림이 빠지지 않음. DB에 배정 이력·`SHIFT_CHANGED`·Candidate 상태가 각각 남음 |
| NT-T11 | 두 종료 사유가 각각 맞는 Type으로 발행됨 (`SHIFT_CHANGED` → `SUBSTITUTE_SHIFT_CHANGED`, `CANDIDATE_INELIGIBLE` → `SUBSTITUTE_CANDIDATE_INELIGIBLE`) |
| NT-T12 | 받는 사람 중 한 명이 조회되지 않으면 그 사건의 알림 전체가 저장되지 않음 (현재 한계 기록용, MIGRATION) |
| NT-T13 | PENDING 후보가 있는 요청이 `SHIFT_CHANGED`로 종료 → 그 후보에게 알림 0건. 그 후보가 수락·거절 시도 → `SUB-023` 409, 상태 불변. 받은 요청 다시 조회 → 그 요청 없음 |
| NT-T14 | 관리자 M이 근무 인계·취소·퇴사 확정 → M에게 **관리자 대상** 대타 종료 알림 0건 (`SHIFT_CHANGED`·`CANDIDATE_INELIGIBLE` 모두, S3-A), 다른 현재 관리자에게 1건씩, 퇴사·다른 근무지 관리자 0건 |
| NT-T15 | API-PS-02·03·LV-02 성공 응답에 종료된 대타 요청 수가 있고 실제 종료 건수와 같음 |
| NT-T16 | `closedSubstituteRequests` 세는 규칙 (API-PS-02·03): ① 근무에 과거 APPROVED 1, CLOSED 1, 활성 ACCEPTED 1 (후보 3명) → 근무 취소 → `1` (후보 수 3·알림 수·과거 요청과 무관). ② 활성 요청 없음 → `0`, 대타 종료 알림 0건. ③ 확정이 실패(409·Rollback)하면 종료 0, 성공 응답 없음. ④ API-LV-02 `closedRequests`: 총수 = 사유별 수의 합 = 실제 종료된 서로 다른 요청 수 (한 요청은 사유 하나). **미정:** 한 근무에 활성 요청이 비정상적으로 2개 이상인 기존 데이터의 처리(모두 종료 / 오류)는 ML-3·ML-5에 정해져 있지 않다. 이 경우의 기대값은 정하지 않는다 (S4에서 기존 데이터 확인 후 결정) |
| V5-N-20 (S3-A, NT 번호 없음) | 관리자 M이 직원 E를 퇴사 확정, E는 M이 요청한 대타의 ACCEPTED 후보 → 그 요청 `CLOSED / CANDIDATE_INELIGIBLE`. M에게 관리자 대상 알림 0건, **요청자 알림 1건**. 다른 현재 관리자에게 1건씩 |

## 6. 결정 (사용자 잠정 결정 2026-10-25) 과 남은 질문

### 6-1. 대타 종료 알림은 사유별로 나눈다 (질문 1 (가))

`SUBSTITUTE_SHIFT_CHANGED`(근무가 바뀌어 할 일 없음)와 `SUBSTITUTE_CANDIDATE_INELIGIBLE`(근무는 그대로, 다시 요청 검토). 요청자의 다음 행동과 받는 사람 범위가 다르다. Type 이름은 후보.

### 6-2. 같은 사건의 알림은 받는 사람별로, 세 조건을 모두 만족할 때만 합친다 (질문 2 (가))

§3-3의 세 조건, §3-4의 받는 사람별 판단, DB는 합치지 않음, 통합 Type 없이 서버 문구로 합침.

### 6-3. 받는 사람 범위 결정 (사용자 잠정 결정 2026-10-25)

1. **`SHIFT_CHANGED`로 종료될 때 응답하지 않은 PENDING 후보에게는 알림을 보내지 않는다.**
   - 이유: 응답할 의무가 생기지 않은 상태이고, 응답하지 않은 요청이 끝나면 목록에서 사라지는 것으로 충분하다고 이미 정했다 (G03-D-22 S22-a). 알림을 보내고 확인할 화면이 없는 상태를 만들지 않는다 (G03-D-22 D22-3).
   - **알림을 보내지 않는 것과 상태 정리는 별개다:** 요청은 `CLOSED / SHIFT_CHANGED`가 되고, 그 후보의 수락·거절은 서버가 거부한다 (§3-6).
   - **적용 범위는 `SHIFT_CHANGED`의 PENDING 후보로 한정한다.** `CANDIDATE_INELIGIBLE`은 원래 후보가 받는 사람이 아니며(§3-1), 다른 종료 사유로 넓히지 않는다.
   - ML-3 L126의 받는 사람("ACCEPTED·PENDING 후보")을 이 결정으로 좁힌다. ML-3 원문은 지우지 않고 ML-3의 "S1 결정 연결"에 연결했다.
2. **근무 취소·인계·퇴사를 직접 실행한 관리자에게는 그 사건의 대타 종료 알림을 만들지 않는다.**
   - 실행한 관리자는 확정 응답으로 결과를 본다. 그래서 확정 응답에 종료된 대타 요청 수를 반드시 담는다 (API-CATALOG API-LV-02 `closedRequests`, API-PS-02·03 `closedSubstituteRequests` 추가).
   - 다른 현재 관리자에게는 보낸다. 현재 관리자는 AS-IS와 같이 `findAllByWorkplace_IdAndRoleAndLeftAtIsNull(MANAGER)`로 정하고 실행자의 소속 ID를 뺀다.
   - **(S3-A, 사용자 잠정 결정 2026-10-25, 충돌 X-4 해소)** 이 제외는 그 Command가 만든 `SUBSTITUTE_SHIFT_CHANGED`와 `SUBSTITUTE_CANDIDATE_INELIGIBLE` **둘 다**에 적용한다 (아래 "자동으로 넓히지 않는다"의 예외로 명시해 포함). 제외 대상은 **관리자 수신자 역할**뿐이다. 실행자가 그 요청의 요청자이면 요청자로서의 알림은 받는다 (확정 응답은 사유별 수만 보여 주므로 어느 요청이 자기 것인지 알 수 없다. 관리자도 대타를 요청할 수 있다 — SUB-01은 역할 무관). 거절로 생긴 `CANDIDATE_INELIGIBLE`은 실행자가 없으므로 모든 현재 관리자가 받는다.
   - **받는 사람 선별 순서 (S3-B):** ① 역할별 받는 사람 집합을 따로 만든다 (요청자 / ACCEPTED 후보 / 현재 관리자에서 실행자 소속 ID를 뺀 집합) → ② 각 집합에 Type 규칙을 적용한다 → ③ 마지막에 받는 사람별로 §6-2 합치기를 적용한다. 실행자 제외는 ①의 **관리자 집합에서만** 일어나므로, 실행자가 요청자이면 요청자 집합에서 온 알림은 ③ 이후에도 남는다. 최종 중복 제거를 사람 단위로 먼저 하지 않는다.
   - **이 제외 규칙을 기존 Type이나 다른 새 Type에 자동으로 넓히지 않는다.** 기존 관리자 알림(`SUBSTITUTE_ACCEPTED` 등)은 실행자가 관리자가 아니므로 해당하지 않는다.

**남은 질문:** 없음.

**S3 추적 (해결 아님):**
- 다른 현재 관리자의 대체 화면 공백 (§3-6). S3 차단 조건, P1 후보. → S3-A: API-SB-02 P0 후보로 방향 결정 (팀 `PENDING`). 구현·승인 전까지 공백은 남음.
- G03-D-22 대체 화면 원칙을 관리자 알림 전체에 적용할 범위.
- ~~API-LV-02 `closedRequests` 세는 규칙~~ → S2-5B에서 같은 규칙으로 연결 (API-CATALOG API-LV-02, NT-T16 ④).
- 한 근무에 활성 대타 요청이 2개 이상인 기존 데이터의 처리 (ML-3·ML-5 미정, S4에서 운영 외 데이터 기준 확인).

## 7. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-25 | 초안 (S2-4B). AS-IS 저장·발행·조회·재가입 격리·DB 형식(추정), 기존 8종(미발행 1종), 새 후보 6종과 받는 사람·대체 화면, 보내지 않는 것, 받는 사람 규칙 R1~R5, 중복 경우, DB·Rollback 영향, Test NT-T1~T8, 질문 2개 |
| 2026-10-25 | 교차 검토 반영: DB 형식을 JPA 매핑 / Hibernate DDL / 운영 Schema로 구분(운영 `UNKNOWN`, S4에서 Hibernate DDL 확인 가능), 받는 사람은 User가 아니라 그 근무지 소속 상태 기준, 알림 합치기 세 조건 |
| 2026-10-25 | 질문 1·2 (가) 사용자 잠정 결정 반영 (§6-1·6-2). 문구가 Row마다 저장되고 Frontend가 서버 문구를 그대로 보여 준다는 사실(§1), 받는 사람별 판단(§3-4), 서로 다른 두 근무(§3-5), 대체 화면 실제 조회 가능 여부(§3-6), 소속 확인 시점, 묶음 실패 한계 구분, 호환 방향, Test NT-T9~T12, 새 질문 2개 |
| 2026-10-25 | 받는 사람 범위 2건 사용자 잠정 결정 (§6-3): `SHIFT_CHANGED`의 PENDING 후보 제외, 실행한 관리자 제외. §3-1·3-4·3-6 갱신 (D·ACCEPTED C 행, 문구 작성 주의, 이미 열린 화면의 `SUB-023` 거부, 근무표가 대타 이력을 대신하지 않음), Test NT-T13~T15 |
| 2026-10-25 | Commit 전 보완: `closedSubstituteRequests` 세는 규칙 명시 (API-CATALOG PS-02·03), 다른 관리자 대체 화면 공백을 S3 차단 조건으로 명시, §6 S3 추적 항목, Test NT-T16 |
| 2026-10-25 | S2-5B: NT-T16 정정 — ① 사전조건 오류 수정 (후보가 수락한 요청은 OPEN이 아니라 ACCEPTED), ② "활성 요청 2개 → 2" 기대값 철회 (근거 카드 없음, 미정으로 이동), ③ 실패 시·④ LV-02 경우 추가. 정식 ID 대응 (V5-N) |
| 2026-10-25 | S3: API ID 표기 정정 (받은 요청 SUB-06 → SUB-02, V4 Delivery Sheet B1 기준). [USER-PATH-REVIEW](USER-PATH-REVIEW.md) §9-4 충돌 X-4 (`CANDIDATE_INELIGIBLE`의 실행한 관리자) 연결 — 본문 결정 변경 없음 |
| 2026-10-25 | S3-A: 충돌 X-4 해소 (§3-1 `CANDIDATE_INELIGIBLE` 받는 사람, §6-3-2, NT-T14 일치 — 실행자는 관리자 수신에서만 제외), V5-N-20 추가, 다른 관리자 대체 화면을 API-SB-02로 연결 — 사용자 잠정 결정 |
| 2026-10-25 | S3-B: §6-3-2에 받는 사람 선별 순서 (역할별 집합 → Type 규칙 → 받는 사람별 합치기) 명시 |
| 2026-10-29 | §8 구현 기록 추가 (새 Type 6종, 발행·계산·저장 방식, 날짜 포함 문구 사용자 결정, 배포 ENUM 주의). 머리 표 구현 행, §3-6 이전 담당 행(API-SC-01 병합) 갱신 |

## 8. 구현 기록 (2026-10-29, V5)

| 항목 | 구현 |
|---|---|
| Type | `SHIFT_ASSIGNED`, `SHIFT_CANCELLED`, `SHIFT_REASSIGNED_AWAY`, `SUBSTITUTE_SHIFT_CHANGED`, `SUBSTITUTE_CANDIDATE_INELIGIBLE`, `WORKPLACE_MEMBER_JOINED` (`NotificationType` 끝에 추가). `SUBSTITUTE_EXPIRED`는 그대로 (NT-T8) |
| 발행 지점 | V3 `ShiftService`의 공개 후 추가(API-PS-01)·취소(API-PS-02)·재배정(API-PS-03) 끝에서 `PublishedShiftChangedEvent` (종료된 요청 목록 = V4 `closeActiveRequestsByShiftChange` 반환값 그대로). V4 거절 경로의 `SubstituteCandidateIneligibleEvent` (#92). V2 가입·재가입 성공 뒤 `WorkplaceMemberJoinedEvent` |
| 받는 사람 계산 시점 (R1) | 동기 `@EventListener` (`NotificationPlanningListener`)가 **업무 Transaction 안**(발행 시점, Lock 이후)에서 읽기만 하고 받는 사람·문구를 정한다. 예외는 삼켜 업무를 막지 않는다 (R2). 저장은 `PlannedNotificationsEvent`의 `AFTER_COMMIT` Listener |
| 받는 사람 규칙 | §6-3 순서대로: ① 역할별 집합(근무 알림: 원래/새 담당, 요청자, ACCEPTED 후보, 현재 관리자 − 실행 관리자) → ② Type → ③ 받는 사람별 합치기 (`ShiftChangeNotificationPlanner`, 순수 계산). 모든 역할에서 **현재 소속만** 남긴다. #92 Event는 요청자 퇴사를 거르지 않으므로 계산 때 현재 소속만 남긴다 |
| 합치기 (§3-4) | 근무 알림을 받는 사람은 대타 종료를 문구에 덧붙여 1건: 요청자·관리자 역할 "진행 중이던 대타 요청도 종료되었습니다.", ACCEPTED 후보 "수락했던 대타 요청은 종료되었습니다." PENDING 후보는 역할 집합에 없어 덧붙이지 않음. 나머지는 요청마다 `SUBSTITUTE_SHIFT_CHANGED` 1건 |
| 실행 관리자 (§6-3-2) | **관리자 수신 역할에서만** 뺀다. 실행 관리자가 그 근무의 원래 담당·요청자이거나 새 담당이면 그 역할의 알림은 받는다 (문구대로 적용, 별도 결정 아님) |
| 문구 (사용자 결정 2026-10-29) | 알림 목록이 근무지 이름 + 문구만 보여 주고 이동 기능이 없어 **근무 날짜·시간을 문구에 넣는다**: "10월 30일(금) 09:00~13:00 근무가 취소되었습니다." 새 담당자 이름·ID는 넣지 않음 (NT-T4). 자격 상실은 요청자에게만 "근무는 그대로이니 필요하면 다시 요청해 주세요."를 덧붙임. 가입은 "○○님이 근무지에 가입했습니다." / 같은 근무지에 끝난 소속이 있으면 "다시 가입했습니다." (이름 길이 제한이 없어 50자로 줄임, `message` Column 255) |
| 저장 (R5, 구현 결정) | 받는 사람마다 별도 `REQUIRES_NEW` Transaction. 한 사람 실패는 로그만 남기고 다른 사람 알림은 저장 |
| Test | `ShiftChangeNotificationPlannerTest` 11 (§3-4 표, 가입 문구·긴 이름, ACCEPTED/PENDING 새 담당, 취소 합치기, NT-T16 ②, 실행 관리자, R1, NT-T4), `ShiftChangeNotificationIntegrationTest` 8 (실제 V3·V4·V2 Command → MySQL 알림 Row: NT-T10·T13·T14·T5·T2·T6·T11, API-PS-01), `ShiftChangeNotificationFailureIntegrationTest` 2 (NT-T1, R5) |
| 배포 (운영 DB) | `notification.type`이 MySQL ENUM이면 새 값 6개를 **배포 전에** 추가해야 저장된다 (test DB는 Hibernate가 알파벳순 ENUM 생성). 운영 `SHOW CREATE TABLE notification`으로 기존 값·순서를 확인하고 **기존 순서 그대로 끝에** 6개를 붙인다. 새 값이 저장된 뒤 이전 앱으로 Rollback하면 알림 목록 조회가 실패할 수 있다 (§4) |
| 남은 공백 | **Decision Log §4-2 완료 조건 미충족 부분:** 다른 현재 관리자의 `SUBSTITUTE_SHIFT_CHANGED`·`SUBSTITUTE_CANDIDATE_INELIGIBLE` 대체 화면(API-SB-02) 미구현 (§3-6, 기존 `SUBSTITUTE_ALL_REJECTED`와 같은 상태) → 관리자 대상 두 알림은 발행하지만 API-SB-02 구현·검증 전까지 "완료"로 기록하지 않는다. 보낸 요청 화면의 `SHIFT_CHANGED`·`CANDIDATE_INELIGIBLE` 사유 문구는 Frontend 후속. 퇴사 확정(API-LV-02) 알림(V5-N-20)은 그 API 구현 때 연결 |
