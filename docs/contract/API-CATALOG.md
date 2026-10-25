# API 계약 목록 (V3 Improvement)

| 축 | 상태 |
|---|---|
| ③ 문서 | `DRAFT`. S2-3B 진행 중 — **B1(퇴사·공개 후 근무 변경) 작성·보완 완료 (2026-10-25)**, B2(조회·재가입·초대 코드·로그아웃)는 다음 단계 |
| ② 정책 | 여기 적힌 새 API와 기존 API 변경은 모두 정책 카드의 **사용자 잠정 결정 / `PROPOSED`**에서 나온 **후보**다. 팀 승인 전. 구현 지시가 아니다 (AI-RULES §3) |
| 구현 | 없음. Kotlin Migration PR은 기존 API를 그대로 옮긴다. 이 문서의 내용은 Migration 이후 별도 Improvement PR |
| ④ / ⑤ | 모든 Test `SPECIFIED` / `NOT_RUN` |
| 기준 코드 | 3차 `develop` `a148a62` (Production Java = `cdaaa13`). 기존 Endpoint 목록은 Controller 정적 확인 |
| 이름 | Method·Path·Field 이름은 모두 **후보**다. 기존 Controller·DTO의 명명에 맞춰 S2-3 끝에 확정한다 |

이 문서는 API의 **모양**(무엇을 받고 무엇을 돌려주는가)만 정의한다. 왜 그렇게 정했는지는 정책 카드가 원본이고, 여기서는 한두 줄 요약과 링크만 둔다. ErrorCode 번호는 S2-4의 공통 목록에서 정한다.

---

## 1. 공통 규약

| 항목 | 규칙 | 근거 |
|---|---|---|
| 기본 경로 | `server.servlet.context-path = /api/v1`. 아래 Path는 그 뒤의 경로 | AS-IS |
| 응답 형식 | `ApiResponse { success, code, message, data }` | AS-IS `global/dto/ApiResponse` |
| 인증 | Access Token (Bearer). 실행자는 SecurityContext의 User. 클라이언트가 실행자 ID를 고르지 않는다 | G03-INV-01 |
| 소속 식별 | 업무 대상(퇴사 대상, 인계 대상, 근무 담당)은 **WorkplaceMember ID**로 지정한다. User ID로 지정하지 않는다. 재가입하면 같은 User에 새 WorkplaceMember가 생기므로, User ID로 묶으면 이전 소속과 섞인다 | [ML-H2](../decisions/proposals/ML-H2-rejoin-and-active-membership.md) H2-1, AS-IS `ShiftRequest.memberId` (WorkplaceMember ID) |
| 현재 소속 | `leftAt IS NULL`. 현재 업무 화면과 Command는 현재 소속 기준 | G03-INV-03, [ML-4](../decisions/proposals/ML-4-leave-command-and-access.md) ML-4-3 |
| 시각 | `LocalDateTime`, `Clock` (Asia/Seoul) | AS-IS `TimeConfig` |
| 오류 종류 | INPUT 400 / AUTH 401 / PERMISSION 403 / NOT_FOUND 404 / DUPLICATE·STATE_CONFLICT·STALE·CONFIRM_REQUIRED·FORBIDDEN_CONFLICT 409 / TRANSIENT 503 (실험 후). 새 code는 `{DOMAIN}-{NNN}` 다음 순번 | [G03-D-03](../decisions/proposals/G03-D-03-error-code.md) §5 |
| 409 대응 | CONFIRM_REQUIRED는 확인 화면, STALE은 미리보기 다시 조회. 오류 화면으로 처리하지 않는다. 변경 요청은 자동 재시도하지 않는다 | [G03-D-05](../decisions/proposals/G03-D-05-confirmation-response.md) D05-4·7 |
| 경고 후 확인 | 새 Command는 충돌 목록 형식, 기존 API는 `boolean` 형식 유지 | G03-D-05 D05-8 |
| 원자성 | 하나의 Command는 하나의 Transaction. 409·오류 시 DB 변경 0 | G03-D-05 D05-6, ML-2-A3 |
| 검사 순서 | ① 실행자 권한 (그 근무지 현재 MANAGER) → ② Path·Body의 대상을 **그 근무지 범위 안에서** 조회 → ③ 대상의 현재 상태 → ④ 업무 검증. AS-IS DRAFT API도 같은 순서다 (`requireManager` → `validSchedule` → 담당자 → 시간) | AS-IS `ShiftService.create` L78-92 |
| 대상 범위 오류 (새 API) | Path의 소속·근무표·근무 ID가 없거나 **그 근무지에 속하지 않으면 404**. 둘을 구분하지 않아 다른 근무지 Resource의 존재를 드러내지 않는다. Path 관계는 `Shift → Schedule → Workplace`를 서버가 확인한다. **기존 API는 그대로** (근무표가 다른 근무지 → `SFT-002` 400, 근무가 다른 근무표 → `SFT-003` 400, 소속 없음 → `SFT-016` 404, 다른 근무지·퇴사 소속 → `SFT-004` 400) | 사용자 잠정 결정 2026-10-25 (§6-1). G03-D-04는 팀 `PENDING` |
| 실행자 권한 오류 | 기존 `requireManager` 그대로: 현재 구성원 아님 `WPL-003` 403, 관리자 아님 `WPL-004` 403 | AS-IS `WorkplaceMemberService` |
| 시간 경계 | 겹침 판정은 `[start, end)`: `a.start < b.end ∧ a.end > b.start`. 맞닿음(앞 구간 끝 = 뒤 구간 시작)은 겹침이 아니다. 근무는 같은 날 안(`SFT-011`), 불가능 시간은 자정을 넘을 수 있으므로 근무 시간 안으로 잘라 비교한다. 겹침 **판정**과 표시·비교용 구간 **병합**(맞닿은 구간을 하나로 합침)은 다른 규칙이다 | AS-IS `UnavailableTimeRepository` L13-20, `ShiftRepository` 겹침 Query, G03-INV-05, G03-C-35, G03-D-05 §5-3 |

## 2. 기존 Endpoint (37개, 중복 확인용, AS-IS)

| 영역 | Endpoint |
|---|---|
| Auth (V1) | `POST /auth/signup`, `POST /auth/login`, `POST /auth/refresh`, `GET /auth/me` |
| Workplace (V2) | `POST /workplaces`, `GET /workplaces` (내 현재 소속), `POST /workplaces/join`, `GET /workplaces/{wid}/members`, `GET /workplaces/{wid}/invite-code` |
| Pattern (V2) | `GET·POST /workplaces/{wid}/regular-shift-patterns`, `PUT·DELETE /workplaces/{wid}/regular-shift-patterns/{patternId}` |
| Schedule (V3) | `POST·GET /workplaces/{wid}/schedules`, `PATCH /workplaces/{wid}/schedules/{sid}/publish` |
| Shift (V3) | `POST /workplaces/{wid}/schedules/{sid}/shifts`, `PATCH·DELETE /workplaces/{wid}/schedules/{sid}/shifts/{shiftId}` (**모두 DRAFT 전용**), `GET /schedules/me`, `GET /shifts/{shiftId}` |
| Unavailable (V3) | `GET·POST /unavailable-times`, `PATCH·DELETE /unavailable-times/{id}` |
| Substitute (V4) | `POST /shifts/{shiftId}/substitute-requests`, `GET /substitute-requests/sent`·`received`·`accepted`, `GET /workplaces/{wid}/substitute-requests/ongoing`, `PATCH /substitute-requests/{id}/approve`·`close`, `PATCH /substitute-candidates/{id}/response` |
| Notification (V5) | `GET /notifications`, `PATCH /notifications/{id}/read` |

## 3. B1 — 퇴사와 공개 후 근무 변경 (새 API 5개)

| ID | 기능 | Method · Path (후보) | 출처 카드 |
|---|---|---|---|
| API-LV-01 | 퇴사 미리보기 | `GET /workplaces/{workplaceId}/members/{memberId}/leave-preview` | ML-4 §6 |
| API-LV-02 | 퇴사 확정 | `POST /workplaces/{workplaceId}/members/{memberId}/leave` | ML-4 §6, ML-2, ML-3, G03-D-05 |
| API-PS-01 | 공개 근무표에 근무 추가 | `POST /workplaces/{workplaceId}/schedules/{scheduleId}/published-shifts` | ML-2 D27-2 |
| API-PS-02 | 공개 근무 취소 | `POST /workplaces/{workplaceId}/schedules/{scheduleId}/shifts/{shiftId}/cancel` | ML-2 D26·D27 |
| API-PS-03 | 공개 근무 담당자 변경 | `POST /workplaces/{workplaceId}/schedules/{scheduleId}/shifts/{shiftId}/reassign` | ML-2 D27-2·4 |

**기존 API와의 경계:** 기존 `POST/PATCH/DELETE .../shifts`는 DRAFT 전용이고 공개 근무는 거부한다. ML-2 D27-1("DRAFT CRUD 제한을 단순히 풀지 않고 **별도의 검증된 Command**로 제공")에 따라 **기존 API를 넓히지 않고 별도 Endpoint**로 둔다. 그래서 Migration 계약(기존 API의 거부 동작)은 바뀌지 않는다. 시간 변경 Command는 없다 (D27-2 보류). 시간을 바꾸려면 취소 + 추가이며 원자적이지 않다 (D27-3).

### 3-1. API-LV-01 퇴사 미리보기

| 항목 | 내용 |
|---|---|
| 권한 | 그 근무지의 현재 MANAGER (기존 `WPL-003` / `WPL-004`) |
| Path 변수 | `memberId` = 퇴사 대상의 **현재 WorkplaceMember ID** |
| 대상 조건 | 같은 근무지, 현재 소속, EMPLOYEE (ML-H2 H2-9). 대상 소속이 없거나 다른 근무지 소속이면 **404** (§1 대상 범위 오류). 같은 근무지의 이미 종료된 소속이면 409 STATE_CONFLICT |
| 응답 `data` | `member` (ID, 이름, 역할, 가입 시각) / `ongoingShift` (있으면 확정 불가, 종료 시각) / `publishedShifts[]` (근무 ID, 근무표 ID, 시작·종료, 진행 중 대타 유무·상태) / `draftShiftsToDelete[]` (근무 ID, 시작·종료, 근무표 주차) / `candidacies[]` (대상이 다른 사람 근무의 후보로 걸린 요청: 요청 ID, 후보 상태, 퇴사 시 효과) |
| 넣지 않는 것 | 불가능 시간 충돌 (인계 대상을 고르기 전이라 계산할 수 없음, G03-D-05 §3) |
| 상태 변경 | 없음 (조회) |
| 오류 | 403 PERMISSION (`WPL-003`·`WPL-004`) → 404 NOT_FOUND (대상 소속 없음·다른 근무지, 새 code) → 409 STATE_CONFLICT (대상이 MANAGER, 이미 종료된 소속 — 새 WPL code) 순서로 판정 |
| 데이터 출처 | 대상 소속, 퇴사자 담당 Shift (PUBLISHED·SCHEDULED·시작 전 / DRAFT), 그 근무의 활성 Request, 대상이 Candidate인 활성 Request |
| Test | ML-4 V2-N-23 (미리보기 내용) 등 ML-4 §10 |

### 3-2. API-LV-02 퇴사 확정

| 항목 | 내용 |
|---|---|
| 권한 | API-LV-01과 같음 |
| 요청 | `decisions[]`: { `shiftId`, `shiftStartAt`, `shiftEndAt` (G03-D-05 P2 비교용), `action`: `REASSIGN` + `targetMemberId` (**인계 대상의 현재 WorkplaceMember ID**) 또는 `CANCEL` } / `acknowledgedUnavailableConflicts[]` (G03-D-05 §5-1, 처음에는 빈 목록) / `regenerateInviteCode` (기본 `false`, ML-H2 H2-5) |
| 검사 순서 | ① 권한 → ② 계획 유효성 P1~P5 (G03-D-05 §4-2) → ③ 우회할 수 없는 충돌 (인계 대상의 공식 근무 중복, 다른 근무지, 시작한 근무) → ④ 불가능 시간 확인 범위 (§5-3 겹침 구간 비교) |
| 성공 응답 | 200. `data`: `leftAt`, `reassignedShifts[]`, `cancelledShifts[]`, `deletedDraftShifts[]`, `closedRequests` (사유별 수: `SHIFT_CHANGED`, `CANDIDATE_INELIGIBLE`), `ineligibleCandidacies`, `newInviteCode` (재발급했을 때만) |
| 404 응답 | Path의 대상 소속이 없거나 다른 근무지 (API-LV-01과 같음). 요청 Body의 `targetMemberId`·`shiftId`가 그 근무지에 없거나 다른 근무지 것이어도 같은 404 (존재 여부를 드러내지 않음) |
| 409 응답 | **CONFIRM_REQUIRED** (`data` = G03-D-05 §5-1 충돌 목록, 겹침 구간만) / **STALE** (계획이 지금과 다름 → 미리보기 다시 조회. 예: 같은 근무지의 인계 대상이 그 사이 퇴사, 근무가 그 사이 인계·취소됨, 퇴사자 담당 DRAFT가 새로 생김) / **STATE_CONFLICT** (진행 중 근무 — `data`에 종료 시각, 이미 종료된 소속, 대상이 MANAGER) / **FORBIDDEN_CONFLICT** (인계 대상의 공식 근무 중복. 기존 SFT·SCH code 재사용 여부는 S2-4) |
| 상태 변경 (한 Transaction) | 대상 소속 `leftAt = now` / 인계 근무: `Shift.member = 대상` + 배정 이력 (`MEMBER_LEFT`) / 취소 근무: `CANCELLED` + 취소 사유 `MEMBER_LEFT`·시각·행위자 / DRAFT 삭제 / 퇴사자 근무의 활성 Request → `CLOSED / SHIFT_CHANGED` / 퇴사자가 후보인 Request: PENDING → `INELIGIBLE`, 수락자면 Request `CLOSED / CANDIDATE_INELIGIBLE` / (선택) 초대 코드 재발급. Pattern은 보존하고 자동 적용 제외 |
| Lock (ML-5 후보, 실험 전) | (재발급 시 Workplace) → 관련 Schedule → 퇴사자 근무 Shift → 관련 Request → 인계 대상 User (ID 오름차순) → 인계 대상 소속 `FOR SHARE` + 퇴사 대상 소속 `FOR UPDATE` → 다시 조회 → 새 영향 Row가 있으면 409 STALE |
| 알림 (Commit 후, 최선 노력) | 인계받은 직원 (근무 배정), 대타 종료 관련자 (요청자·후보, 현재 소속만, ML-3), 퇴사자는 받지 않음. 대체 조회 화면: 예정 근무, 보낸·수락 대타 목록 (G03-D-22) |
| 데이터 출처 | API-LV-01과 같고, 인계 대상의 불가능 시간(본인 User 기준), 인계 대상의 공식 근무 (User 기준 충돌 검사) |
| Test | ML-4 V2-N-24~40, ML-2 V3-N-01~08, ML-3 V4-N-08~22 일부, G03-D-05 D05-T1~T9·T11, G03-D-21 D21-T7~T9 |

### 3-3. API-PS-01 공개 근무표에 근무 추가

| 항목 | 내용 |
|---|---|
| 권한 | 그 근무지의 현재 MANAGER |
| 대상 | PUBLISHED Schedule. 추가하는 근무는 시작 전. Path의 근무표가 없거나 다른 근무지면 404 (§1) |
| 요청 | `memberId` (현재 WorkplaceMember ID), `startAt`, `endAt`, `acknowledgedUnavailableConflicts[]` (근무 1건 기준 목록) |
| 기존 DTO와의 관계 | 기존 `ShiftRequest`(`confirmUnavailableConflict: boolean`)를 재사용하지 않고 새 요청 DTO. 기존 DRAFT API 형식은 그대로 |
| 검사 | 근무 시간 규칙 (같은 날, 주차 범위, 시작 < 종료 — 기존 DRAFT 검사와 같은 규칙), 같은 근무표 안 겹침, 다른 근무지 공식 근무 겹침 (우회 불가), 담당자 현재 소속, 불가능 시간 (확인 범위) |
| 성공 응답 | 201 + 추가된 근무 |
| 오류 | 403 (권한) → 404 (근무표·담당 소속 범위) → 409 STATE_CONFLICT (근무표가 PUBLISHED가 아님. 참고: 기존 DRAFT API는 반대 경우를 `SFT-005` 400 "잘못된 상태입니다"로 거부) → 기존 SFT·SCH 계열 code 재사용 후보 (시간 규칙·겹침) → 409 CONFIRM_REQUIRED |
| 상태 변경 | 새 Shift (SCHEDULED). 배정 이력을 남길지는 ML-2-INV-04 범위(담당자 "변경")와 함께 S2-3B 마무리 때 확인 |
| Lock (ML-5 후보) | Schedule → 새 담당 User → 새 담당 소속 `FOR SHARE` + `leftAt` 재확인 → INSERT |
| 알림 | 새 담당자 (근무 배정). 대체 조회 화면: 예정 근무 |
| Test | ML-2 V3-N-11, G03-D-05 형식 Test |

### 3-4. API-PS-02 공개 근무 취소

| 항목 | 내용 |
|---|---|
| 권한 | 그 근무지의 현재 MANAGER |
| 대상 | PUBLISHED·SCHEDULED·시작 전 근무. Path `workplaceId`·`scheduleId`·`shiftId`의 관계(`Shift → Schedule → Workplace`)가 맞지 않거나 없으면 404 (§1) |
| 요청 | 없음 (사유 Code는 서버가 `MANAGER_CANCELLED`로 기록, ML-2 D26-3). 자유 입력 사유를 받을지는 범위 밖 (카드에 없음) |
| 성공 응답 | 200 + 취소된 근무 (상태, 취소 사유·시각) |
| 오류 | 409 STATE_CONFLICT (이미 취소, 시작한 근무, PUBLISHED 아님) |
| 상태 변경 (한 Transaction) | `CANCELLED` + 취소 사유·시각·행위자 / 그 근무의 활성 Request → `CLOSED / SHIFT_CHANGED` (ML-3-Q1). APPROVED Request는 과거 이력으로 그대로 (ML-3-H) |
| Lock (ML-5 후보) | Schedule → Shift → 그 근무의 활성 Request |
| 알림 | 원래 담당자 (근무 취소), 대타 종료 관련자 (ML-3). 대체 조회 화면: 빠진 근무, 보낸·수락 대타 목록 |
| 되돌리기 | CANCELLED는 SCHEDULED로 돌아가지 않는다 (ML-2-INV-03). 같은 시간에 새 근무 추가는 가능 |
| Test | ML-2 V3-N-09·10·13·15·16 |

### 3-5. API-PS-03 공개 근무 담당자 변경

| 항목 | 내용 |
|---|---|
| 권한 | 그 근무지의 현재 MANAGER |
| 대상 | PUBLISHED·SCHEDULED·시작 전 근무. Path 관계 불일치·없음은 404 (§1). `targetMemberId`가 그 근무지에 없거나 다른 근무지 것이어도 404 |
| 요청 | `targetMemberId` (새 담당의 현재 WorkplaceMember ID), `acknowledgedUnavailableConflicts[]` |
| 검사 | 새 담당 현재 소속, 같은 근무표 안 겹침, 다른 근무지 공식 근무 겹침 (우회 불가), 불가능 시간 (확인 범위), 현재 담당과 같은 사람이면 거부 (409 STATE_CONFLICT 후보) |
| 성공 응답 | 200 + 근무 (같은 근무 ID, 새 담당) |
| 상태 변경 (한 Transaction) | `Shift.member = 새 담당` / 배정 이력 1건 (`MANAGER_REASSIGNED`, 이전·새 담당, 행위자, 시각, ML-2 D27-4. 이력 저장 실패 시 전체 Rollback) / 그 근무의 활성 Request → `CLOSED / SHIFT_CHANGED` |
| Lock (ML-5 후보) | Schedule → Shift → 그 근무의 활성 Request → 새 담당 User → 새 담당 소속 `FOR SHARE` |
| 알림 | 새 담당 (근무 배정), 이전 담당 (다른 직원에게 넘어감, 현재 소속일 때), 대타 종료 관련자. 대체 조회 화면: 예정 근무, 빠진 근무 |
| 대타 상태별 처리 | §4 표 (사용자 잠정 결정 2026-10-25) |
| 대타 승인 거부 후 직접 배정 | 허용. APPROVED를 만들지 않고 배정 이력과 `SHIFT_CHANGED`로 기록 (G03-D-21 D21-9) |
| Test | ML-2 V3-N-12·13·14·16, G03-D-21 D21-T6 |

## 4. 대타가 있는 근무의 취소·담당자 변경 (ML-2 D27-5 해석, 사용자 잠정 결정 2026-10-25)

ML-2 D27-5 "진행 중(OPEN·ACCEPTED) 또는 승인된 대타가 있는 근무의 취소·담당자 변경은 **무조건 허용하지 않는다.** 상태별 처리는 ML-3"은 **"조건 없이 허용하지는 않는다. 아래 상태별 처리를 같은 Transaction에서 보장할 때 허용한다"**로 읽는다. 이 해석은 ML-3-Q1, ML-3 V4-N-17, G03-D-21 D21-9, ML-2 V3-N-16("ML-3 규칙대로")과 맞는다. "아예 거부"로 읽으면 이 결정들과 충돌한다. ML-2의 원래 문장은 지우지 않는다.

| 그 근무의 대타 상태 | 관리자 취소·담당자 변경 시 처리 | 근거 |
|---|---|---|
| OPEN | `CLOSED / SHIFT_CHANGED`. Candidate는 그대로 | ML-3-Q1 |
| ACCEPTED | `CLOSED / SHIFT_CHANGED`. 수락한 Candidate의 ACCEPTED 기록 보존 (현재 유효한 대타 약속으로 표시하지 않음) | ML-3-Q1·Q1b |
| APPROVED | Request는 승인 이력으로 그대로. 현재 담당은 `Shift.member`가 기준. 승인받았던 직원은 그 근무가 "빠진 근무"에 나타나고, 수락 이력에는 "승인됨"으로 남는다 | ML-3-H, G03-D-22 S22-c, ML-4 빠진 근무 |
| CLOSED | 바꾸지 않는다 (다시 종료하지 않음) | ML-3 금지 전이 |

**한 근무에 Request가 몇 건 있을 수 있나 (AS-IS 정적 확인):** 활성(OPEN·ACCEPTED) Request는 근무당 최대 1건이다. 다만 **DB 제약이 아니라 서비스 검사**다 (대타 생성이 Shift `FOR UPDATE` 후 `existsByShift_IdAndStatusIn(OPEN, ACCEPTED)`, `SubstituteRequestService` L132·L152-155). CLOSED·APPROVED는 여러 건 남을 수 있다 (예: 대타로 받은 사람이 다시 대타를 요청해 또 승인되면 APPROVED가 2건). 그래서 종료 대상은 **"그 근무의 모든 OPEN·ACCEPTED Request"**로 적고(보통 0~1건), 과거 APPROVED·CLOSED는 몇 건이든 소급해서 바꾸지 않는다.

**함께 지키는 조건:** 근무 시작 전만, 새 담당의 현재 소속, 공식 근무 중복 거부, 불가능 시간 확인 (G03-D-05), Lock (ML-5 후보, 실험 전).

**화면이 서로 모순되지 않아야 하는 예:** A의 근무를 B가 대타로 승인받은 뒤 관리자가 C에게 인계하면, 예정 근무에는 C에게만 보이고, B의 "빠진 근무"와 B의 수락 이력("승인됨")에 각각 나타나며, A의 보낸 요청은 APPROVED 그대로다 (G03-D-22 D22-T4b).

## 5. B2 — 다음 단계에서 작성할 것

| 구분 | 항목 |
|---|---|
| 새 API | 초대 코드 재발급 (기존 `GET .../invite-code`와 같은 Resource, Method만 다름), 빠진 근무 (`GET /schedules/me` 옆), 내 소속 이력 (퇴사 후에도 본인 인증으로 조회), 종료된 대타 (수락한 요청), 서버 로그아웃 |
| 기존 API 동작 변경 | `POST /workplaces/join` (재가입, 동시 가입 409), SUB-05 `GET /substitute-requests/sent` (현재 소속), SUB-07 `GET /substitute-requests/accepted` (현재 소속 조건), `GET /shifts/{shiftId}` (소속 확인), 승인·응답의 잘못된 안내 code (G03-D-03 T1, P1) |
| B2에서 지킬 것 | SUB-05·07은 상태 정합성 / 조회 권한 / 이력 보존 / 재가입 격리를 행으로 나눔. 목록 필터와 ID 직접 조회·Command의 권한 경계를 따로 표시. 과거 승인 이력 DTO는 Candidate 기준, 현재 담당은 Shift 기준 (`SubstituteRequestResponse.from` 재사용 금지) |

## 6. B1 결정 기록 (사용자 잠정 결정 2026-10-25, 팀 승인 전)

1. **대상이 없거나 다른 근무지 소속이면 404로 통일** (질문 1 (가)). 새 퇴사 API에서 시작해 같은 원칙을 새 공개 후 변경 API의 Path 관계·Body 대상에도 적용했다 (§1). 기존 API의 400/404는 Migration에서 그대로 둔다. G03-D-04(노출 정책 전체)는 팀 `PENDING`이며 이 기록은 새 API 범위의 사용자 잠정 결정이다.
2. **대타가 있는 근무도 상태별 처리를 보장하면 취소·담당자 변경 가능** (질문 2 (가), §4).

**구현 메모 (정적 확인):** 지금 불가능 시간 Repository는 겹침 **여부**만 돌려준다 (`existsOverlappingUnavailableTime`). G03-D-05의 겹침 구간 목록을 만들려면 구간을 돌려주는 **내부 조회가 새로 필요하다 (아직 없음, 구현 전)**. 이 내부 조회 결과(불가능 시간 Row)는 서버 안에서 교집합·병합·정규화에만 쓰고, API 응답에는 G03-D-05 §5-2의 겹침 구간만 내보낸다.

**후속 확인 항목 (B1에서 결정하지 않음):**
- 공개 후 근무 **추가**에도 생성 이력을 남길지 (ML-2-INV-04는 담당자 "변경"만 다룸). 기존 Shift의 생성 시각·생성자 기록으로 충분한지 먼저 확인 → ML-2 구현 계약, S3.
- 같은 근무지의 인계 대상이 이미 퇴사한 경우를 409 STALE로 볼지 409 STATE_CONFLICT로 볼지. 서버는 그 퇴사가 미리보기 이후인지 알 수 없다 (요청에 미리보기 시점 정보가 없음). 사용자의 다음 행동("미리보기를 다시 보고 다른 대상 선택")은 같으므로 지금은 STALE로 적었다 → S2-3C에서 화면 대응과 함께 확정.
- 기존 400(`SFT-002`·`SFT-003`·`SFT-005`)은 Kotlin Migration에서 보존하는 **기존 계약**이고, 404·409는 V3 Improvement의 **신규 제안**이다. STATE_CONFLICT 등은 분류이며 실제 code 문자열은 S2-4에서 정한다. 새 공개 후 변경 Command는 Endpoint가 따로지만 시간 규칙·겹침·현재 소속 검사는 기존 검사 로직을 재사용할 수 있다 (ML-2 §8 "공통 검증으로 분리 필요"). 재사용 방식은 구현 설계에서 정한다.

## 7. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-25 | 초안 (S2-3B B1). 공통 규약, 기존 Endpoint 37개, 새 API 5개 (퇴사 미리보기·확정, 공개 후 추가·취소·담당자 변경), 카드 표현 차이 1건, B2 목록, 질문 2개 |
| 2026-10-25 | B1 보완: 질문 1·2 사용자 잠정 결정 반영. §1에 검사 순서·대상 범위 404·실행자 권한·시간 경계 규칙 추가, API별 404 조건, §4를 상태별 처리 표로 교체, §6 결정 기록과 구현 메모 |
