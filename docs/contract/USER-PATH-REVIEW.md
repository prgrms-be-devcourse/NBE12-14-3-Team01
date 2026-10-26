# 사용자 경로 교차 검증 (S3)

> **현재 승인 상태 (2026-10-26):** 사용자 전달 구두 승인과 PR 리뷰에 따른 채택 범위·예외는 [Decision Log](../decisions/DECISION-LOG.md) §4를 따른다. 아래 최초 작성 당시 PROPOSED/PENDING 표기는 이력이며 현재 승인 범위를 덮어쓰지 않는다. 구현·실험·배포 및 미결정 사항은 각각 별도 상태다.

| 축 | 상태 |
|---|---|
| 결정 단계 | **조사·초안** (S3, 2026-10-25). 정책 결정 없음. 아래 선택지는 사용자·팀 결정 대상 |
| ② 정책 | 이 문서가 인용하는 정책은 모두 사용자 잠정 결정 / ② `PROPOSED` / 팀 `PENDING` |
| ③ 문서 | `DRAFT` |
| 방법 | 정적 대조만. 기준 `a148a62` (Backend·Frontend 코드), 문서 기준 `dfb21b6`. Test·Gradle·DB 실험 실행 0. 새 Test는 ④ `SPECIFIED` / ⑤ `NOT_RUN` |

> 정책·API·화면·ErrorCode·알림·Test가 **사용자 행동 순서**에서 서로 맞는지 본다. 각 계약의 원본은 [`API-CATALOG`](API-CATALOG.md), [`FRONTEND-CONTRACT`](FRONTEND-CONTRACT.md), [`ERROR-CODES`](ERROR-CODES.md), [`NOTIFICATION-TYPES`](NOTIFICATION-TYPES.md), [`TEST-INVENTORY`](../evidence/TEST-INVENTORY.md)이고, 이 문서는 그 사이의 일치·공백·충돌만 기록한다. 기존 문서 원문은 바꾸지 않고 결정이 나면 각 원본에 연결한다.

판정 표기: **일치** (계약끼리 맞음) / **공백** (필요한 화면·Test·계약이 없음) / **충돌** (두 문서가 다른 것을 말함) / **결정** (정책 선택 필요, §9).

## 0. S3로 넘어온 항목과 처리

| 출처 | 항목 | 이 문서 |
|---|---|---|
| NOTIFICATION-TYPES §3-6, G03-D-22 L126 | 다른 관리자 대체 화면 공백, "알림 Type ↔ 대체 화면" 빈칸 확인 | §8 표, §9-3 결정 C |
| API-CATALOG §5-5, TEST-INVENTORY §5-4 | V4-T-74 역할만 부족한 경우 | §9-1 결정 A |
| DECISION-LOG G03-D-10 (`DEFER`) | 남의 알림 403 / 404 | §9-2 결정 B |
| ERROR-CODES L75 | T1 정정이 사용자 흐름을 막으면 P0로 재평가 | §4 UP-05: 막는 사례 없음 (P1 유지) |
| API-CATALOG L234 | 배포 환경 Cookie `SameSite`·허용 출처 | 정적으로 확인 불가 → TEST-STRATEGY §7 (배포 담당) |
| API-CATALOG L360 | 공개 후 근무 **추가**의 생성 이력 | 미결 유지 → ML-2 구현 계약 (§10 O-6) |
| ML 카드 6개 머리 "코드 근거 재검증 NO, S2-2~S3" | 이관 카드의 코드 근거 | §0-1 표본 확인만. 카드 전체 재검증은 남음 (§10 O-4) |

### 0-1. 경로 판단에 쓴 코드 사실 표본 확인 (`a148a62`)

| 사실 | 위치 | 결과 |
|---|---|---|
| 대타 승인: Request `FOR UPDATE` → 상태 검사 → `requireManager` 순서 | `SubstituteRequestService` L236 → L241 → L247 | 문서와 같음 |
| 주간 근무표 조회는 관리자 전용 | `ScheduleService` L155 | 같음 |
| `WorkplaceMember.leftAt`은 생성자에서만 씀 | `WorkplaceMember` L57 | 같음 |
| 받은 요청(SUB-02)은 `Request OPEN`·`PENDING`·시작 전·공개·SCHEDULED만 | `SubstituteCandidateRepository` L60-70 | 같음 |
| 알림 목록은 현재 소속(`rm.leftAt is null`)만 | `NotificationRepository` L19 | 같음 |
| 알림 읽음: `findByIdForUpdate`로 **먼저 잠그고** 본인·현재 소속 확인. 남의 알림과 내 과거 소속 알림이 **같은 조건**에서 403 `NOT-002`, 없는 ID는 404 `NOT-001` | `NotificationService` L302-319 | 같음 (CHG-07 "Lock 점유" 근거). G03-D-10이 사실상 두 질문임을 확인 (§9-2) |

## 1. 경로 목록

| ID | 경로 | 주 Domain | 묶음 (IMPLEMENTATION-PRIORITY §3-1) |
|---|---|---|---|
| UP-01 | 근무지 소속·재가입과 이전 소속 격리 | V2 | D |
| UP-02 | DRAFT 근무표 생성·수정·공개 | V3 | (AS-IS 보존) |
| UP-03 | 공개 근무 추가·취소·담당자 변경 | V3 | B |
| UP-04 | 불가능 시간 충돌 경고와 재확인 | V3 | B·C |
| UP-05 | 대타 요청·수락·승인·종료 | V4 | B' (+ AS-IS) |
| UP-06 | 관리자 퇴사 미리보기·확정 | V2 | C |
| UP-07 | 알림 수신·합치기·읽음·실패 시 대체 조회 | V5 | B·C·D |

서버 로그아웃(묶음 A)은 이번 경로 목록에 없다. AUTH-1 카드 기준이며 배포 Cookie 확인은 TEST-STRATEGY §7.

## 2. UP-01 소속·재가입과 이전 소속 격리

| 단계 | 행위자 | API / 화면 | 결과·다음 행동 | 실패·권한·경합 | 알림 | Test | 판정 |
|---|---|---|---|---|---|---|---|
| 가입 | 직원 | `POST /workplaces/join` / 근무지 목록 | 새 소속 → 근무지 목록 | 현재 소속자 `WPL-002` 409. 동시 가입 409 (ML-H2, AS-IS는 500) | `WORKPLACE_MEMBER_JOINED` → 현재 관리자 (대체: 구성원 목록) | V2-N-06~22, V5-N-06, V2-N-07 (= ML5-T10, H6) | 일치 |
| 재가입 | 퇴사자 | 같은 API (CHG-01) | **새 소속 Row** (이전 Row 그대로) | 유효 코드 필요 | 같음 (문구로 재가입 구분) | V2-N-06 | 일치 |
| 재가입 후 업무 조회 | 재가입자 | 보낸 요청(SUB-05), 수락한 요청(SUB-07), 근무 상세, 알림 목록 | 이전 소속 기록 0건 | 화면의 `workplaceId` 필터로는 이전 소속을 구별할 수 없음(`MyWorkplaceResponse`에 소속 ID 없음) → **서버 현재 소속 조건 필요** (CHG-02·03·04). 알림 목록은 AS-IS로 이미 현재 소속만 (§0-1) | — | V4-N-37 (= D22-T11), V3-N-23 (M3-T4), V5-N-03 | 일치 |
| 재가입 전에 열어 둔 알림 창에서 이전 소속 알림 읽음 | 재가입자 | 알림 읽음 / `AppHeader` | — | 403 `NOT-002` → 화면은 모든 오류에 "다시 시도해주세요" 알림창만 띄우고 목록을 다시 받지 않음 → 다시 눌러도 같은 실패 | — | 없음 | **공백 G-1** (§10) |
| 이전 소속 확인 | 본인 | API-ME-01 / 마이페이지 | 기간마다 한 줄 | 로그인 본인이면 소속 0개여도 가능 | — | ML-4 V2-N | 일치 |

**새 통합 시나리오 Test:** V2-N-54 — 가입 → 근무·대타·알림 생성 → 퇴사 → 재가입 → 새 소속 기준 조회(SUB-05·07, 근무 상세, 알림 목록)에서 이전 소속 기록 0건, 이전 Row·`member_id` 불변, 내 소속 이력 2줄.

## 3. UP-02 DRAFT 근무표 생성·수정·공개 (AS-IS 보존 경로)

| 단계 | API / 화면 | 결과 | 실패·경합 | 알림 | Test | 판정 |
|---|---|---|---|---|---|---|
| 근무표 생성 | SCH-01 / 근무표 | 빈 DRAFT, Pattern 변환 | 월요일 아님 등 SCH code | — | `ScheduleServiceTest` (BASELINE) | 일치 |
| DRAFT 근무 추가·수정 | SCH-02·04 / `window.confirm` | 불가능 시간 겹침 → `requiresConfirmation` → 확인 후 저장 (D05-8: 형식 유지) | 같은 근무지 겹침 `SFT-009`, 다른 근무지 공식 근무 겹침 `SFT-010` (**Test 0**), 시간 검사 `SFT-007`·`SFT-011` 순서 (**Test 0**), 주간 밖 `SFT-008` (**Test 0**) | — | `ShiftServiceTest` L412·L493, `ShiftModifyIntegrationTest` L175, V3-N-24·29·31 (Migration 명세), V3-N-22 (D05-T10) | 일치 + Migration Test 공백은 명세됨 |
| 공개 | SCH-06 / 근무표 | PUBLISHED, 담당자에게 알림 | 확인 없이 불가능 시간 충돌 → 경고, `confirm=true`여도 공식 충돌 거부 (`ScheduleServiceTest` L2380), 자정 넘는 근무 `SCH-008` (**Test 0**) | `SCHEDULE_PUBLISHED` → 예정 근무 | V3-N-32, V5-N-17 (= D22-T1, 알림 실패 격리) | 일치 |
| 퇴사와 DRAFT 편집 경합 | — | 퇴사 후 퇴사자 DRAFT 0건 (ML-2-INV-02) | ML5-T14·T15, R4 | — | 보류 (H6 `BLOCKED`) | 결정 대기 (S4) |

P1 최우선 후보(Publish 충돌 목록 표시와 목록 기반 확인, IMPLEMENTATION-PRIORITY §2)는 그대로다.

## 4. UP-03 공개 근무 추가·취소·담당자 변경

| 단계 | API / 화면 | 결과·다음 행동 | 실패·권한·경합 | 알림 (받는 사람 → 대체 화면) | Test | 판정 |
|---|---|---|---|---|---|---|
| 추가 | API-PS-01 / 근무표 "근무 추가" | 새 공개 근무 | 실행자 `WPL-003`·`WPL-004` 403, 범위 밖 404, `SFT-020` (공개 아님·시작한 근무), `SFT-021` 확인 필요, 공식 근무 겹침 거부 | `SHIFT_ASSIGNED` → 새 담당 → 예정 근무 | V3-N-01~19 | 일치. 생성 이력은 미결 (O-6) |
| 취소 | API-PS-02 / 근무 행 "취소" | CANCELLED + 사유·시각. 응답 `closedSubstituteRequests`로 "대타 요청도 종료됨" 표시 (실행한 관리자의 유일한 안내) | 위와 같음 | `SHIFT_CANCELLED` → 원래 담당 → 빠진 근무 (API-SC-01, 새 API). 대타가 있었으면 `SUBSTITUTE_SHIFT_CHANGED` (§8) | V3-N-09, V4-N-09, V5-N-16 | 일치 + §8 공백 |
| 담당자 변경 | API-PS-03 / "담당자 변경" | 새 담당, "인계됨", 종료된 대타 수 | `SFT-022` 같은 담당자, `SFT-021` 확인, FORBIDDEN 공식 겹침 | `SHIFT_ASSIGNED` → 새 담당, `SHIFT_REASSIGNED_AWAY` → 이전 담당 (새 담당 이름 없음), `SUBSTITUTE_SHIFT_CHANGED` (§8) | V4-N-08·10·11·17·18, V5-N-04·10 | 일치 + §8 공백 |
| 열린 화면의 대기 후보 | 받은 요청 화면 | 다시 조회하면 사라짐 | 수락·거절 → `SUB-023` 409 → 안내 후 목록 다시 조회 (FRONTEND §4-2) | 알림 없음 (§6-3) | V5-N-13, V4-N-11 | 일치 |
| 두 관리자 동시 담당자 변경 / 취소 vs 새 대타 요청 | — | 최종 담당 1명, 활성 요청 0 | H6-C3·C4 | 실패한 쪽 알림 0 | 보류 (H6) | 결정 대기 (S4) |
| 내 근무 상태 철자 | 내 근무 화면 | `"CANCELED"` → `"CANCELLED"` | — | — | FRONTEND §3-3 | 일치 (Frontend 수정 필요) |

**새 통합 시나리오 Test:** V3-N-33 — 공개 근무에 대타 OPEN(PENDING 2) / ACCEPTED 상태에서 관리자 M이 취소 또는 담당자 변경 → 근무 상태·담당, Request `CLOSED / SHIFT_CHANGED`, Candidate 상태 불변, 배정 이력, 응답 `closedSubstituteRequests`, 받는 사람별 알림 Row(§3-4: M 0, 다른 관리자 1, PENDING 0)를 한 Test에서 DB 재조회로 확인. 중간 실패 주입 시 전체 Rollback.

## 5. UP-04 불가능 시간 충돌 경고와 재확인

| 상황 | 동작 | 화면 | ErrorCode | Test | 판정 |
|---|---|---|---|---|---|
| 직원이 공개 근무와 겹치는 불가능 시간 등록 | 확인 후 등록 성공, 근무·대타 변화 없음 | 기존 `window.confirm` | — | V3-N-21 (= D21-T5, MIGRATION) | 일치 |
| 관리자 배정 (PS-01·03, LV-02)이 불가능 시간과 겹침 | 409 `CONFIRM_REQUIRED` + 겹침 목록 → 같은 요청 + 확인 목록으로 재전송. 확인 범위를 넘는 새 겹침이면 다시 409 | 새 확인 화면 (FRONTEND §3-2), 겹치는 구간만 표시 | `SFT-021`, `WPL-011` | V2-N-41~48 (D05·D21), D05-T11 = V2-N-52 (응답 Field 노출 범위) | 일치 |
| 대타 수락·승인이 불가능 시간과 겹침 | 확인 없이 거부 | 오류 안내 | `SUB-006` (문구 중립화는 T1-04, P1) | V4-T-29·66·91 (BASELINE) | 일치 |
| 경합 (확인 후 확정 전 새 불가능 시간) | 확정이 새 충돌을 찾아 409 | 새 목록 재확인 | — | D21-R1~R5 보류 (H6) | 결정 대기 (S4) |

## 6. UP-05 대타 요청·수락·승인·종료

| 단계 | 행위자 | API / 화면 | 결과 | 실패·권한 (AS-IS → Improvement) | 알림 (→ 대체 화면) | Test | 판정 |
|---|---|---|---|---|---|---|---|
| 요청 | 담당 직원 | SUB-01 / 대타 요청 | 후보 있으면 201 OPEN, 없으면 200 `requestCreated=false` (Request 없음) | 남의 근무 `SUB-011`, 시작 `SUB-012`, 취소 `SUB-014`, 활성 요청 `SUB-013` | 후보에게 `SUBSTITUTE_REQUEST_RECEIVED` → 받은 요청. 후보 0명이면 관리자에게 `SUBSTITUTE_NO_CANDIDATE` → **관리자 대체 화면 없음 (Request Row도 없음, AS-IS)** | V4-T-15~22 | 일치 + §8 공백 (AS-IS) |
| 수락·거절 | 후보 | SUB-03 / 받은 요청 | ACCEPTED / 마지막 거절이면 CLOSED | 남의 Candidate `SUB-020` 403 → **404 `SUB-019`** (CHG-07, V4-T-58·68). 시작·취소된 근무 `SUB-012`·`SUB-014` → `SUB-023` (T1-02·03, **P1 유지**: 응답 불가라는 결과는 같고 문구만 틀림, 흐름을 막지 않음) | 수락 → 관리자 `SUBSTITUTE_ACCEPTED` → 진행 중 목록. 전원 거절 → 요청자 + 관리자 `SUBSTITUTE_ALL_REJECTED` → 요청자: 보낸 요청 / **관리자: 대체 화면 없음 (AS-IS)** | V4-T-07~12·56~72, V4-N-41·42 | 일치 + §8 공백 (AS-IS) |
| 승인 | 관리자 | SUB-04 / 승인 관리 | APPROVED, `Shift.member` = 수락자 | 비구성원 `WPL-003` → **404 `SUB-001`** (CHG-06, V4-T-26). **같은 근무지 비관리자 `WPL-004` → 결정 A** (V4-T-74). 시작 `SFT-006` → `SUB-024` (T1-01, P1). Lock 순서 변경 (ML-5) | 요청자·수락자 `SUBSTITUTE_APPROVED` → 보낸 요청 / 예정 근무 | V4-T-23~30·73~81, V4-N-40·44~46 | **결정 A** |
| 종료 | 관리자 | SUB-08 / 승인 관리 | CLOSED `MANAGER_CLOSED` | 비구성원 `WPL-003` → **404 `SUB-001`** (V4-T-39). 같은 근무지 비관리자 → 결정 A와 같은 질문 (기존 Test 없음) | 요청자 → 보낸 요청, ACCEPTED면 수락자 → 수락한 요청 — 종료 (API-SB-01), **OPEN이면 PENDING 후보에게도 발행 (AS-IS) → 받은 요청에서 사라짐** | V4-T-36~46, V4-N-47 | 일치 + 관찰 O-1 |
| 만료 | — | 보낸 요청 | 응답에서 CLOSED / EXPIRED, `closedAt = startAt` | — | 없음 (`SUBSTITUTE_EXPIRED` 미발행 유지) | V4-T-01~05, V4-N-23~26 | 일치 |
| 화면 경로 | — | 승인·종료 버튼은 `substitutes/admin` 화면에만 있고 메뉴는 `managerOnly` (`lib/navigation.ts` L50-52, 근무지 홈 L270-273) | 직원이 승인·종료에 닿는 **화면 경로 없음** → 결정 A는 직접 API 호출에만 해당 | — | — | — | 사실 |

기존 Flow Test(V4-T-88~93)가 요청 → 수락 → 승인·전원 거절·관리자 종료 흐름을 다룬다. 새 통합 시나리오 Test는 추가하지 않는다.

## 7. UP-06 관리자 퇴사 미리보기·확정

| 단계 | API / 화면 | 결과·다음 행동 | 실패·권한·경합 | 알림 | Test | 판정 |
|---|---|---|---|---|---|---|
| 미리보기 | API-LV-01 / 구성원 목록 → 미리보기 (EMPLOYEE 행에만 버튼) | 미래 공식 근무, 삭제될 DRAFT, 걸린 대타. 진행 중 근무면 종료 시각과 확정 불가 | 실행자 `WPL-003`·`WPL-004`, 대상 없음·다른 근무지 `WPL-006` 404, 대상 MANAGER `WPL-007`, 이미 퇴사 `WPL-008`, 진행 중 근무 `WPL-009` | — | V2-N-23~40 (ML-4) | 일치 |
| 근무별 선택 | 화면 안 | 모든 근무에 인계(대상 선택) 또는 취소를 골라야 확정 버튼 활성 | — | — | — | 일치 |
| 확정 | API-LV-02 | 소속 종료, 인계·취소, DRAFT 삭제, 대타 정리, 선택 시 초대 코드 재발급. 응답 `closedRequests`(총수 = 사유별 합) | `WPL-011` 확인 필요 → 재확인. `WPL-010` STALE (P1~P5, 인계 대상이 확정 전에 퇴사한 경우 포함) → 미리보기 다시 조회. 공식 근무 겹침 거부 (확인 무관). 마지막 단계 실패 → 전체 Rollback | 인계받는 직원 `SHIFT_ASSIGNED` → 예정 근무. 퇴사자의 근무에 걸린 요청 `SUBSTITUTE_SHIFT_CHANGED` → **요청자가 퇴사자 본인이면 볼 수 없음** (목록이 현재 소속 기준, 의도된 동작) / ACCEPTED 후보 → API-SB-01 / 다른 관리자 → §8. 퇴사자가 ACCEPTED 후보였던 다른 요청 `SUBSTITUTE_CANDIDATE_INELIGIBLE` → 요청자 → 보낸 요청 / 관리자 → **충돌 X-4** | V2-N-41~52, V4-N-12~16·22, V5-N-14 | 충돌 X-4 + §8 공백 |
| 퇴사 직후 열린 화면 | FRONTEND §6 | `WPL-003` → 근무지 목록 이동, 후보 응답 `SUB-021`, 알림 읽음 403 | 알림 읽음 오류는 G-1과 같은 문제 | — | — | 공백 G-1 |
| 퇴사 vs 수락·승인·Publish 경합 | — | — | ML5-T5~T9, H6-C5~C8 | 실패한 쪽 알림 0 | 보류 (H6) | 결정 대기 (S4) |

**새 통합 시나리오 Test:** V2-N-53 — 직원 A 소속, A의 공개 근무 2개(하나는 B가 대타 수락, 하나는 대타 없음), A가 다른 요청의 ACCEPTED 후보, A 담당 DRAFT 1개 → 미리보기 → (근무 1 C에게 인계, 근무 2 취소) 확정 → A `leftAt`, 근무 담당·상태, A 근무의 요청 `SHIFT_CHANGED`, A가 수락했던 요청 `CANDIDATE_INELIGIBLE`, 과거 APPROVED 이력 보존, DRAFT 0건, C의 예정 근무, `closedRequests` 사유별 수, 받는 사람별 알림 Row를 DB 재조회로 확인. 알림 저장 실패를 주입해도 업무 결과 불변.

## 8. UP-07 알림 — Type별 대체 화면 확인 (G03-D-22 L126 규칙)

G03-D-22 L126: "빈칸이 있으면 그 알림을 추가하지 않거나 화면을 먼저 만든다." 이 규칙은 **새 Type**에 적용한다. AS-IS Type의 빈칸은 Migration에서 그대로 두고 기록한다.

| Type | 받는 사람 | 대체 화면 | 판정 |
|---|---|---|---|
| `SCHEDULE_PUBLISHED` | 담당 직원 | 예정 근무 | 있음 |
| `SUBSTITUTE_REQUEST_RECEIVED` | 후보 | 받은 요청 | 있음 |
| `SUBSTITUTE_NO_CANDIDATE` | 관리자 | **없음** (Request Row도 없음) | AS-IS 빈칸 |
| `SUBSTITUTE_ACCEPTED` | 관리자 | 진행 중 목록 | 있음 |
| `SUBSTITUTE_ALL_REJECTED` | 요청자 / 관리자 | 보낸 요청 "지난 요청" 탭 / **API-SB-02 (S3-A)** | 관리자 AS-IS 빈칸 → 결정 C로 해소 방향 |
| `SUBSTITUTE_APPROVED` | 요청자 / 수락자 | 보낸 요청 / 예정 근무 | 있음 |
| `SUBSTITUTE_MANAGER_CLOSED` | 요청자 / 수락자 / PENDING 후보 | 보낸 요청 / API-SB-01 (새) / 받은 요청에서 사라짐 | 있음 (수락자는 새 API 후) |
| `SHIFT_ASSIGNED` (새) | 새 담당 | 예정 근무 | 있음 |
| `SHIFT_CANCELLED` (새) | 원래 담당 | 빠진 근무 (API-SC-01, 새) | 새 API와 함께 |
| `SHIFT_REASSIGNED_AWAY` (새) | 이전 담당 | 빠진 근무 | 새 API와 함께 |
| `SUBSTITUTE_SHIFT_CHANGED` (새) | 요청자 / ACCEPTED 후보 / 다른 관리자 | 보낸 요청 / API-SB-01 / **API-SB-02 (S3-A, P0 후보)** | 결정 C로 빈칸 해소 방향 (구현 전) |
| `SUBSTITUTE_CANDIDATE_INELIGIBLE` (새) | 요청자 / 관리자 | 보낸 요청 / **API-SB-02 (S3-A)** | 결정 C로 빈칸 해소 방향 (구현 전) |
| `WORKPLACE_MEMBER_JOINED` (새) | 관리자 | 구성원 목록 | 있음 |
| (알림 없음) 계산된 만료 | 관리자 | 진행 중 목록에서 사라짐 → **API-SB-02 (S3-A)** | S3-A에서 발견, C1 대상에 포함 |

그 밖의 알림 경로: 저장 실패는 업무에 영향 없음 (V5-N-01·17), 한 받는 사람 조회 실패 시 그 사건 알림 전체 누락 (V5-N-12, 현재 한계 기록), 합치기 세 조건 (§6-2), 목록은 현재 소속만 (§0-1), 읽음은 결정 B·공백 G-1.

**새 통합 시나리오 Test:** V5-N-19 — §8 표의 "있음" 행마다 알림이 가리키는 사실을 대체 화면 API에서 같은 값으로 조회할 수 있는지 (알림 Row를 지운 상태에서도). 결정 C 이후 관리자 행 추가.

## 9. 결정이 필요한 것 (선택지 비교, 결정하지 않음)

### 9-0. S3-A 결정 기록 (사용자 잠정 결정 2026-10-25, 팀 `PENDING`)

| 결정 | 선택 | 반영 위치 |
|---|---|---|
| A (V4-T-74) | **W안 — 역할 먼저 검사.** 대타 승인·종료에서 실행자가 어느 근무지에서도 MANAGER가 아니면 Request 조회 전에 403 `WPL-004` (존재·없는 ID 같은 응답). MANAGER면 범위 확인 후 범위 밖은 없는 ID와 같은 404. 아래 §9-1의 X·Y·Z 대신 새 대안으로 선택 (Y와 같이 존재를 숨기면서 "조회 권한" 정의와 관계 조회가 필요 없음) | API-CATALOG §5-5, M3-T1·T5, TEST-INVENTORY §5-4 |
| B (G03-D-10) | **`DEFER` 유지.** B-1(남의 알림)·B-2(과거 소속 알림)로 나눠 기록. 권한 확인 전 Lock 제거(CHG-07)와 Frontend 읽음 실패 복구는 응답 code와 별도로 진행 가능. 기존 응답 동작은 바꾸지 않음 | FRONTEND-CONTRACT §2, TEST-INVENTORY §5-4·§6 |
| C (관리자 대체 화면) | **C1 — API-SB-02 관리자 끝난 대타 요청 (P0 후보).** 대상 = CLOSED 모든 사유 + 계산된 만료 (APPROVED 제외). 후속 조치는 **사실 Field**로 주고 표시 규칙은 Frontend 계약에. 7일은 기본 필터 (보존 기간·상한 아님) | API-CATALOG API-SB-02, FRONTEND-CONTRACT §3-4·§5, IMPLEMENTATION-PRIORITY 묶음 B |
| X-4 | **(a) — 실행한 관리자는 관리자 수신에서만 제외** (`SHIFT_CHANGED`·`CANDIDATE_INELIGIBLE` 모두). 실행자가 요청자이면 요청자 알림은 받음 | NOTIFICATION-TYPES §3-1·§6-3-2·NT-T14, V5-N-20 |

**S3-A에서 새로 확인한 사실:** 관리자 진행 중 목록(SUB-06)은 `startAt > now`만 보여 주고 만료 알림도 없어서, **승인 없이 근무가 시작된 요청은 관리자 화면에서 흔적 없이 사라진다** (`SubstituteRequestRepository` L42-47). C1의 대상에 계산된 만료를 넣은 이유다.

### 9-1. 결정 A — V4-T-74: 대타 승인·종료를 관리자가 아닌 같은 근무지 구성원이 호출

화면 경로가 없다 (§6). 이 결정은 직접 API 호출의 응답 의미와 노출 범위만 바꾼다. 세 경우를 나눈다.

| 경우 | 누구 | 그 요청을 볼 수 있나 |
|---|---|---|
| ① | 그 요청의 요청자·후보 | 예 (보낸 요청·받은 요청) |
| ② | 그 요청과 관계없는 같은 근무지 직원 | 아니오 (직원은 근무지 전체 대타 목록을 볼 수 없음: 진행 중 목록은 관리자 전용) |
| ③ | 관리자 전용 작업의 역할 부족 일반 | Path에 근무지 ID가 있는 API는 이미 403 `WPL-004` (§5-5 규칙) |

| 선택지 | ① | ② | ③과의 일관성 | 장점 | 단점 | 기존 Test 영향 |
|---|---|---|---|---|---|---|
| X. 비관리자 모두 404 (현재 잠정 규칙, M3-T1) | 404 `SUB-001` | 404 `SUB-001` | 다름 (같은 역할 부족인데 URL 형태에 따라 403/404) | 선검사가 단순 (관리자 여부만) | ①에게 "없는 요청"이라 답함 → "볼 수 있는 자원은 상태에 맞는 응답" 원칙과 어긋남 | V4-T-74 → 404 |
| Y. ① 403, ② 404 | 403 `WPL-004` | 404 `SUB-001` | ①은 같음 | 원칙과 가장 일관 | Lock 전에 요청자·후보 관계를 조회해야 함 (잠그지 않는 조회 1~2회), Test 경우 증가 | V4-T-74는 Fixture의 실행자 관계를 정해야 함 (현재 Mock은 관계 미지정) |
| Z. 같은 근무지 구성원 모두 403, 비구성원 404 | 403 `WPL-004` | 403 `WPL-004` | 같음 | **V4-T-74 기대값 변경 0**, 근무지 ID가 Path에 있는 API와 같은 규칙 | ②가 순차 ID로 **자기 근무지 안** 요청의 존재 여부를 알 수 있음. 상태는 선검사가 상태 검사보다 앞이면 노출되지 않음 | V4-T-74 그대로 |

공통: 어느 선택지든 권한 확인을 Lock·상태 검사보다 **먼저** 한다 (CHG-06). M3-T1의 "같은 근무지 직원 → 404" 문장은 X에서만 그대로이고 Y·Z에서는 고쳐야 한다. 종료(SUB-08)에도 같은 답을 적용할지 함께 정한다.

### 9-2. 결정 B — G03-D-10: 알림 읽음 403 / 404

§0-1에서 현재 코드는 두 경우를 한 조건으로 처리함을 확인했다. **두 질문으로 나눈다.**

| 경우 | AS-IS |
|---|---|
| B-1 다른 User의 알림 ID | 403 `NOT-002` |
| B-2 내 알림이지만 종료된 소속 (퇴사 후, 재가입 후) | 403 `NOT-002` |
| B-3 없는 ID | 404 `NOT-001` |

| 선택지 | B-1 | B-2 | 근거 | 기존 Test 영향 |
|---|---|---|---|---|
| A. 현행 | 403 | 403 | 변경 없음 | 없음 |
| B. 범위 밖 404 (CHG-07 원칙) | 404 `NOT-001` | 403 (§5-5 "실행자 자신의 소속 종료 → 403 정책") | 남의 알림 ID 존재를 숨김 | L187 테스트 404로 |
| B'. 모두 404 | 404 | 404 | 과거 소속 기록은 M2(내 소속 이력)로만 본다는 원칙과 일치 | L187·L240 둘 다 |
| C. 403 통일 | 403 | 403 (없는 ID도 403) | 단순 | L155 (없는 ID) 변경 |

**G03-D-10과 분리할 수 있는 것:** 권한 확인 전에 Row를 잠그는 순서(CHG-07)는 응답 code와 무관하게 고칠 수 있다. G03-D-10이 `DEFER`여도 CHG-07의 "Lock 전 확인"은 진행 가능하다 (응답 code만 G03-D-10 결정 전까지 AS-IS 유지).
**어느 선택지든 필요한 것:** 공백 G-1 (Frontend가 403/404에서 항목 제거·목록 재조회).

### 9-3. 결정 C — 다른 관리자의 대타 종료 알림과 대체 화면

**관리자가 놓친 알림 대신 다시 확인해야 하는 정보 (업무 필요):**

| 알림 | 관리자가 할 일이 남는가 | 필요한 정보 |
|---|---|---|
| `SUBSTITUTE_ALL_REJECTED` (AS-IS) | **예.** 요청자가 여전히 근무를 맡고 있고 대타를 원했다 → 다른 직원에게 담당자 변경 등 | 근무, 요청자, 종료 사유·시각 |
| `SUBSTITUTE_CANDIDATE_INELIGIBLE` (새) | **예.** 위와 같음 (수락자가 퇴사해 다시 비게 됨) | 같음 |
| `SUBSTITUTE_NO_CANDIDATE` (AS-IS) | 예. 후보가 없어 요청 자체가 만들어지지 않음 | 근무, 요청자 (Request Row 없음 → 목록으로는 보여 줄 수 없음) |
| `SUBSTITUTE_SHIFT_CHANGED` (새, 다른 관리자) | 거의 없음. 다른 관리자가 이미 처리했고 현재 담당은 근무표에 보임 | 누가 바꿨는지 정도 |

| 선택지 | 내용 | 범위 | 장점 | 단점 |
|---|---|---|---|---|
| C1. 관리자 "최근 종료된 대타 요청" 조회 (P0) | 새 API(최근 7일, 사유·시각·요청자·근무) + 승인 관리 화면 탭 | ALL_REJECTED·INELIGIBLE·SHIFT_CHANGED·MANAGER_CLOSED. NO_CANDIDATE는 Row가 없어 제외 | G03-D-22 원칙을 관리자에게도 적용, AS-IS 빈칸까지 해소 | 새 API·화면·Test (P0 범위 증가) |
| C2. 새 관리자 알림은 화면이 생길 때까지 추가하지 않음 (L126 규칙 그대로) | 다른 관리자에게 `SHIFT_CHANGED`·`INELIGIBLE`을 보내지 않음. AS-IS `ALL_REJECTED`는 그대로 | — | 범위 최소, 규칙과 일치 | 할 일이 남는 `INELIGIBLE`을 관리자가 알 방법이 없음. §6-3의 "다른 관리자에게 보낸다" 결정을 되돌림 |
| C3. 알림은 보내고 빈칸을 예외로 기록, C1을 P1로 | — | — | 알림 정책 유지 | G03-D-22 원칙의 명시적 예외, L126 규칙과 충돌 → 예외 승인 필요 |
| C1'. 혼합 | `INELIGIBLE`은 C1 화면과 함께 P0, 다른 관리자 `SHIFT_CHANGED`는 C2 (보내지 않음) | 할 일이 남는 것만 화면 | 업무 필요에 맞춤 | §6-3 일부 조정 필요 |

### 9-4. 충돌 X-4 — `SUBSTITUTE_CANDIDATE_INELIGIBLE`의 실행한 관리자

| 문서 | 내용 |
|---|---|
| NOTIFICATION-TYPES §6-3-2 | "근무 취소·인계·**퇴사**를 직접 실행한 관리자에게는 **그 사건의 대타 종료 알림**을 만들지 않는다" |
| NT-T14 (= V5-N-14) | 퇴사 확정 → 실행한 관리자에게 대타 종료 알림 0건 |
| API-LV-02 `closedRequests` | 사유별 수에 `CANDIDATE_INELIGIBLE` 포함 (실행자는 응답으로 확인 가능) |
| NOTIFICATION-TYPES §3-1 L53 | 받는 사람 "요청자, 현재 관리자" — **실행자 제외 없음** |
| §6-3-2 마지막 | "이 제외 규칙을 기존 Type이나 **다른 새 Type**에 자동으로 넓히지 않는다" |

선택지: (a) 퇴사로 생긴 `INELIGIBLE`도 실행자 제외 — §6-3-2 본문·NT-T14·응답 수와 일치, L53에 "퇴사 확정으로 생기면 실행자 제외" 추가. 거절로 생긴 `INELIGIBLE`은 실행자가 없으므로 그대로. (b) L53 그대로 (실행자도 받음) — NT-T14를 고쳐야 함. **어느 쪽인지 사용자 확인 필요** (S2-4B에서 쓴 문장끼리의 불일치).

## 10. 공백·관찰 (정책 결정 아님)

| ID | 내용 | 처리 후보 |
|---|---|---|
| G-1 | 알림 읽음 실패 시 Frontend가 code와 관계없이 "다시 시도해주세요"만 띄우고 목록을 다시 받지 않음 (`AppHeader` L263-268). 403/404면 다시 눌러도 같은 실패 | FRONTEND-CONTRACT §2·§6에 "403/404 → 항목 제거 + 목록 재조회" 추가 (결정 B와 무관하게 필요). P1 후보 (흐름을 막지 않지만 잘못된 안내). **S3-A: FRONTEND-CONTRACT §2에 복구 규칙 반영** (403·404 → 목록 재조회, 네트워크·5xx → 유지) |
| O-1 | 관리자 종료(`MANAGER_CLOSED`)는 OPEN이면 PENDING 후보에게도 알림 (AS-IS), 근무 변경 종료(`SHIFT_CHANGED`)는 PENDING 후보 제외 (§6-3). 같은 "응답 전 종료"인데 다름 | Migration은 AS-IS 유지. Improvement에서 맞출지 P1 검토 (§6-3은 범위를 `SHIFT_CHANGED`로 한정했으므로 자동 확장하지 않음) |
| O-2 | `SUBSTITUTE_NO_CANDIDATE`의 관리자 대체 화면 없음, Request Row도 없음 | 결정 C1에서도 제외됨. 필요하면 별도 기록 방식 검토 (P2) |
| O-3 | TEST-INVENTORY §3-1-1과 V4 Delivery Sheet의 "실행 사례"(114) 표현이 실제 실행 횟수처럼 읽힘 | S3에서 "정적 계산 실행 사례"로 정정 (이번 변경) |
| O-4 | 이관된 ML 카드 6개의 코드 근거 재검증은 §0-1 표본(6/6 일치)만 했다 | 카드별 전체 재검증은 S4 전 별도 블록 또는 각 Domain 구현 계약 때 |
| O-5 | 알림 읽음의 Lock 전 권한 확인 부재는 정적으로 확인됨 (§0-1) | CHG-07 그대로. Lock 영향 실측은 S4 |
| O-6 | 공개 후 근무 추가의 생성 이력 필요 여부 (API-CATALOG L360) | ML-2 구현 계약에서 결정 |
| X-5 | API ID 표기 오류: G03-D-22 L40·L116, FRONTEND-CONTRACT §1·§4-2·§6, NOTIFICATION-TYPES §3-6이 받은 요청을 `SUB-06`으로 적음. 기준(V4 Delivery Sheet B1)은 받은 요청 `SUB-02`, `SUB-06`은 관리자 진행 중 목록 | **S3에서 표기만 정정** (각 문서 변경 이력에 기록, 내용 변경 없음). 단 API-CATALOG §5 M1 L160의 "SUB-05·06·07"은 현재 업무 범위 목록이라 `SUB-06`(관리자 진행 중)이 맞을 수 있고 받은 요청 `SUB-02`가 빠졌을 수 있다 → **고치지 않고 의도 확인** (범위 표기라 계약에 해당) |

## 11. 새 통합 시나리오 Test (정식 ID, ④ `SPECIFIED` / ⑤ `NOT_RUN`)

| ID | 경로 | 구분 | 비고 |
|---|---|---|---|
| V2-N-53 | UP-06 퇴사 전체 흐름 | PROPOSED_IMPROVEMENT | 묶음 C 완료 조건 후보 |
| V2-N-54 | UP-01 재가입 후 이전 소속 격리 | PROPOSED_IMPROVEMENT | 묶음 D |
| V3-N-33 | UP-03 공개 근무 취소·담당자 변경 + 대타 종료 + 알림 | PROPOSED_IMPROVEMENT | 묶음 B |
| V5-N-19 | UP-07 알림 ↔ 대체 화면 일치 | PROPOSED_IMPROVEMENT | S3-A: 관리자 행은 API-SB-02로 확인 |
| V4-N-49~54 | 결정 C — API-SB-02 (권한·대상·기간·사실 Field·소속 경계·MySQL Query) | PROPOSED_IMPROVEMENT | S3-A 추가 |
| V5-N-20 | X-4 — 실행자가 요청자이기도 한 경우 | PROPOSED_IMPROVEMENT | S3-A 추가 |

UP-02 (AS-IS)와 UP-05 (기존 V4-T-88~93)는 새 통합 Test를 추가하지 않는다. UP-04는 V2-N-41~48이 다룬다.

## 13. S3-B 최종 정합성 검증 (2026-10-25, 정적 확인)

### 13-1. `SUB-02` / `SUB-06` 참조 분류

기준: V4 Delivery Sheet B1 — `SUB-02` = 받은 요청 `GET /substitute-requests/received`, `SUB-06` = 관리자 진행 중 목록 `GET /workplaces/{workplaceId}/substitute-requests/ongoing`.

| 분류 | 건수 | 내용 |
|---|---|---|
| 표시 이름 오류 | 6 (모두 S3에서 정정) | G03-D-22 L40·L116, FRONTEND-CONTRACT §1·§4-2·§6, NOTIFICATION-TYPES §3-6. 모두 Path·설명은 받은 요청으로 맞고 ID만 틀렸다 |
| 계약 참조 오류 | 0 | — |
| 화면 연결 오류 | 0 | 실제 화면 호출 확인: 승인 관리 → `/workplaces/{id}/substitute-requests/ongoing` (SUB-06), 받은 요청 → `/received`·`/accepted` (SUB-02·07), 보낸 요청 → `/sent` (SUB-05) |
| Test 연결 오류 | 0 | V4-T-07·13 = SUB-02, V4-T-31~35 = SUB-06 (`ListTest`) 모두 맞음 |
| 의도 확인 | 1 | API-CATALOG §5 M1 L160 "SUB-05·06·07" (현재 업무 범위). 계약 범위라 고치지 않음 |

나머지 사용처(G03-D-19 L101·L105·L118·L170, ML-4 L36, V4 Delivery Sheet, 이 문서)는 모두 맞다. **혼동 주의:** 새 API 목록의 `API-SB-02`는 `SUB-02`와 다르다 (API-CATALOG API-SB-02에 명시).

### 13-2. 코드 사실 재검증 (§0-1 표본 6건 + 추가 7건, `a148a62`)

| ML 카드의 AS-IS 주장 | 위치 | 결과 |
|---|---|---|
| ML-4: 받은 요청(SUB-02)은 현재 소속 Candidate만 | `SubstituteCandidateRepository` (`c.member.leftAt IS NULL`) | 같음 |
| ML-4: 수락한 요청(SUB-07)은 소속 확인 없이 ACCEPTED·시작 전만 | 같은 파일 SUB-07 Query | 같음 |
| ML-4: 보낸 요청(SUB-05)은 User 기준, 소속 조건 없음 | `SubstituteRequestRepository` L20 | 같음 |
| ML-4: `requireManager`는 현재 소속(`LeftAtIsNull`) 기준 | `WorkplaceMemberService` L17-20 | 같음 |
| ML-H2: `UNIQUE(workplace_id, user_id)` | `WorkplaceMember` L15 | 같음 |
| ML-5: 승인은 Request `FOR UPDATE` → User `FOR UPDATE` (Shift Lock 없음) | `SubstituteRequestService` L236, L290 | 같음 |
| NOTIFICATION-TYPES §1: 받는 사람은 소속 ID로 조회, `leftAt` 필터 없음 | `NotificationService` `findAllById` | 같음 |

13건 모두 카드와 일치했다. 이것은 AS-IS 확인이며 V3 정책과의 차이(예: CHG-02·03의 현재 소속 조건)는 오류가 아니라 Improvement 대상이다. 카드 전체의 줄 번호 재검증은 여전히 남는다 (O-4).

### 13-3. 경로별 Test 연결 (연결 ≠ 경로 전체 검증 ≠ 실행 완료)

이 표는 **추적성**만 보여 준다. 새 Test는 모두 ④ `SPECIFIED` / ⑤ `NOT_RUN`, 기존 Test는 이 저장소에 개별 실행 근거가 없어 ⑤ `UNKNOWN`이다. Test가 연결됐다는 것은 기능이 정상 동작한다는 뜻이 아니다.

| 경로 | 기존 Test (Migration에서 보존) | Improvement에서 바뀌는 기대값 | 새 Test (`SPECIFIED`) | 경로 전체를 잇는 Test | S4 선행 | 정책 대기 (`BLOCKED` 사유) |
|---|---|---|---|---|---|---|
| UP-01 | Workplace Join Test (TEST-INVENTORY §4-2), 알림 목록 현재 소속 (V5-N-03, MIGRATION) | `rejectDuplicateMembershipAtDatabaseLevel` (ML-H2) | V2-N-06~22, V5-N-06 | **V2-N-54** | U2' (생성 Column Unique), 동시 가입 V2-N-07 (H6) | H6 Harness (DL-009) |
| UP-02 | `ScheduleServiceTest`, `ShiftServiceTest`, `ShiftModifyIntegrationTest`, `ScheduleRollbackIntegrationTest`, `ShiftScheduleConcurrencyTest`, `SchedulePublishedEventListenerIntegrationTest` | 없음 (AS-IS 경로) | V3-N-20~22·24~32 (MIGRATION), V5-N-17 | 기존 Test가 공개 → 알림까지 다룸. 새 통합 Test 없음 | 퇴사 vs DRAFT (ML5-T14·T15, R4) | H6 |
| UP-03 | 없음 (새 기능) | — | V3-N-01~19, V4-N-08~11·17~21, V5-N-01~05·10·13·16 | **V3-N-33** | H6-C3·C4, ML-5 Lock | H6 |
| UP-04 | `ShiftServiceTest` L412·L493, V4-T-29·66·91 | T1-04 문구 (확인 Test 없음 → 0) | V2-N-41~52, V3-N-21 | V2-N-53 안에서 퇴사 확인 흐름 | D21-R1~R5 | H6 |
| UP-05 | V4-T-01~93 | V4-T-02 (D-19), V4-T-25·81 (T1-01), 64·65·72 (T1-02), 63 (T1-03), 43 (T1-05), 26·39·74 (W안), 58·68 (M3) | V4-N-23~48, **V4-N-49~54 (API-SB-02)** | V4-T-88~93 (기존 Flow) + V4-N-02 | ML-5 승인 Lock 순서, V4-N-05·45·46 (H6) | H6, D-19 팀 승인 |
| UP-06 | 없음 (새 기능). V2 회귀 (§4-2) | — | V2-N-23~52, V4-N-12~16·22, V5-N-14·20 | **V2-N-53** | ML5-T5~T9, H6-C5~C8, 503 | H6, 활성 요청 2개 이상 처리 (ML-3) |
| UP-07 | `NotificationServiceTest` 14, `NotificationTest` 3, 공개 알림 통합 1, 읽음 동시성 1 | `NotificationServiceTest` L187 (G03-D-10 대기 → 변경 확정 안 함) | V5-N-01~20 | **V5-N-19** | 알림 읽음 Lock 영향 실측 (CHG-07) | G03-D-10, API-SB-02 팀 승인 (V5-N-19 관리자 행) |

Frontend 복구 규칙(G-1)과 화면 표시 규칙(§3-4)은 Frontend Test 체계가 없어 Test ID를 주지 않았다 (수동 확인 대상, S4 이후 결정).

### 13-4. API-SB-02 정적 검증

| 확인 항목 | 결과 | 근거 |
|---|---|---|
| CLOSED 전체 + 시간상 만료된 OPEN·ACCEPTED, APPROVED 제외 | 계약에 명시 | API-CATALOG API-SB-02 대상 |
| `startAt` 경계·서버 시각 | `startAt ≤ now` (경계 포함), 서버 `Clock` | AS-IS `!now.isBefore(startAt)`, SUB-06 `startAt > now`와 정확히 나뉨 |
| 효력 종료 시각 기반 기간·정렬·페이지를 DB에서 | 가능 (정적): `CASE WHEN status='CLOSED' THEN closedAt ELSE shift.startAt END`. CLOSED 전이는 모두 `closedAt`을 씀 | `SubstituteRequest` L70-73·L92-95. 계산식이라 Index를 쓰기 어려움 → 성능은 S4 |
| 조회가 DB를 바꾸지 않음 | 명시 (만료를 CLOSED로 저장하지 않음) | G03-D-19 D19-2 |
| 현재 Workplace MANAGER 권한 | `requireManager` (현재 소속) | M1, ML-4 확인 |
| 퇴사·재가입 정보 노출 | User ID·소속 ID 비노출, `requesterStillAssigned`는 소속 Row 비교, 표시 "(이전 소속)" | S3-B 보완 |
| SUB-02·SUB-06과 중복·충돌 | 없음: SUB-02는 직원 본인 후보, SUB-06은 진행 중, API-SB-02는 끝난 요청 | §13-1 |
| `/api/v1`·DTO | 경로는 `/api/v1` 뒤 (API-CATALOG §1), 응답은 `ApiResponse` 형식 | 기존 규약 |
| "조치 필요"가 취소·시작한 근무에 잘못 표시되지 않음 | 근무 `SCHEDULED` ∧ 시작 전 조건 추가 | FRONTEND §3-4 (S3-B 보완) |
| 실제 행동 가능 여부는 서버 재검증 | 명시 | FRONTEND §3-4 |
| **미검증 (S4·구현)** | Query 성능·실행 계획, `size` 최대값, 페이지 방식(offset/cursor), 동률 정렬의 안정성 실측 | — |

### 13-5. 새로 확인한 정책 연결 (결정 아님)

- **G03-D-04** (다른 근무지·User 자원 접근 노출, `DEFER`): M3 (CHG-06·07)과 W안이 이 질문의 답에 해당한다. DECISION-LOG §3에 연결만 했다. 팀 리뷰에서 함께 승인해야 한다.
- **G03-D-10과 G03-D-16의 범위 구분:** G03-D-10 = B-1 **다른 User의 알림** (자원 은닉). G03-D-16 = B-2 **내 과거 소속(WorkplaceMember Row)의 알림** 노출·수신 (소속 격리). 둘 다 `PENDING`이며 서로 대신 결정하지 않는다. 재가입하면 새 소속 Row가 생기므로 과거 소속 알림을 현재 소속에 연결하지 않는다는 원칙은 두 결정과 무관하게 유지한다 (AS-IS 목록도 현재 소속만). TEST-INVENTORY §5-4에서 L187은 G03-D-10, L240은 G03-D-16 대기로 나눴다.
- **G03-D-20** (만료 알림, `PENDING`): API-SB-02는 만료 **알림** 없이 만료 **조회**만 제공한다. D-20을 정하지 않아도 된다.
- DECISION-LOG §3에 S2-3B 이후 결정 행 6개를 추가했다 (B2, FRONTEND §9, ERROR-CODES §6, NOTIFICATION-TYPES §6, IMPLEMENTATION-PRIORITY, 검증 전략).

## 12. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-25 | 초안 (S3). 경로 7개 교차 대조, 코드 사실 표본 6건 확인, 알림 Type별 대체 화면 표, 결정 A·B·C 선택지 비교, 충돌 X-4, 공백 G-1, 관찰 O-1~O-6, API ID 표기 오류 X-5 정정, 새 통합 시나리오 Test 4개. 결정 0, 실행 0 |
| 2026-10-25 | S3-A: 결정 A(W안)·B(DEFER 유지, Lock·Frontend 분리)·C(API-SB-02, 종료+만료, 사실 Field)·X-4(관리자 수신만 제외) 기록 (§9-0), §8 관리자 행·만료 행, G-1 반영 위치, Test V4-N-49~54·V5-N-20. 선택지 비교 원문(§9-1~9-4)은 그대로 둠 |
| 2026-10-25 | S3-B: §13 최종 정합성 검증 — SUB-02/06 분류 (표시 오류 6 정정, 계약·화면·Test 오류 0), 코드 사실 추가 7건 (총 13건 일치), 경로별 Test 연결 표, API-SB-02 정적 검증, G03-D-04·16·20 연결 발견 |
| 2026-10-25 | S3 Commit 전: G03-D-10(B-1)·G03-D-16(B-2) 범위 구분, §13-3에 추적성 ≠ 실행 완료 명시 |

### 퇴사 경로 1차 기준 갱신 (2026-10-26)

UP-06은 Decision Log §4-4의 미리보기 기준을 따른다. 미리보기의 공식 근무 계획은 동일성을, 자동 대상+효과는 현재 집합의 부분집합 여부를 검사한다. 새 대상·교체·영향 확대에는STALE/이번업무변경0, 감소만 있으면 필수조건 재검증 후 남은 대상으로 진행한다. API-LV-01/02의 비교정보 후보와 Frontend의 새미리보기/확인목록 폐기를 연결한다. 위 표의 과거 X-4와 관리자 대체화면 논의는 §9 이후 결정 연결 및 NOTIFICATION-TYPES §6-3을 따른다. 원문 분석의 공백 표기를 현재 해결 완료로 소급 변경하지 않는다.
