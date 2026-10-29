# API 계약 목록 (V3 Improvement)

> **현재 승인 상태 (2026-10-26):** 사용자 전달 구두 승인과 PR 리뷰에 따른 채택 범위·예외는 [Decision Log](../decisions/DECISION-LOG.md) §4를 따른다. 아래 최초 작성 당시 PROPOSED/PENDING 표기는 이력이며 현재 승인 범위를 덮어쓰지 않는다. 구현·실험·배포 및 미결정 사항은 각각 별도 상태다.

| 축 | 상태 |
|---|---|
| ③ 문서 | `DRAFT`. S2-3B — **B1(퇴사·공개 후 근무 변경)**, **B2(소속·조회·인증) 초안 (2026-10-25)** |
| ② 정책 | 여기 적힌 새 API와 기존 API 변경은 모두 정책 카드의 **사용자 잠정 결정 / `PROPOSED`**에서 나온 **후보**다. 팀 승인 전. 구현 지시가 아니다 (AI-RULES §3) |
| 구현 | 최초 작성 시 없음. API-LV-01은 PR #90으로 구현·병합됨 (§3-1). 그 밖의 구현 기록은 각 API 절을 따른다. Kotlin Migration과 Improvement는 구분한다 |
| ④ / ⑤ | 최초 작성 시 모든 Test `SPECIFIED` / `NOT_RUN`. API-LV-01의 구현 Test 연결·실행 상태는 TEST-INVENTORY §4-3, 다른 후속 기록은 각 API 절을 따른다 |
| 기준 코드 | 3차 `develop` `a148a62` (Production Java = `cdaaa13`). 기존 Endpoint 목록은 Controller 정적 확인 |
| 이름 | 최초 작성 시 Method·Path·Field 이름은 모두 **후보**. API-LV-01의 구현 형식은 §3-1, 다른 후속 확정 기록은 각 API 절을 따른다 |

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
| 409 대응 | CONFIRM_REQUIRED는 확인 화면, STALE(제출한 계획이 지금 상태와 맞지 않음)은 미리보기 다시 조회. 오류 화면으로 처리하지 않는다. 변경 요청은 자동 재시도하지 않는다. 화면별 대응은 [FRONTEND-CONTRACT](FRONTEND-CONTRACT.md) §2 | [G03-D-05](../decisions/proposals/G03-D-05-confirmation-response.md) D05-4·7 |
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

**구현 확인 (2026-10-29):** [PR #90](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/90) 병합 SHA `e039ccf3627c89bab8c7d00ee02e0e65cb90bde6`의 `LeavePreviewController`·`LeavePreviewService`·`LeavePreviewResponse`·`LeavePreviewContextService`를 정적 대조했다 (① `VERIFIED_AS_IS`). 아래는 조회 API의 구현 사실이며 API-LV-02 구현 완료나 미결정 정책의 승인을 뜻하지 않는다.

| 항목 | 내용 |
|---|---|
| 구현 상태 | 구현·병합됨 (PR #90). `GET /api/v1/workplaces/{workplaceId}/members/{memberId}/leave-preview`. 정상 조회는 200, `ApiResponse`의 `code = "200"`, `message = "퇴사 미리보기를 조회했습니다."` |
| 권한 | 그 근무지의 현재 MANAGER (기존 `WPL-003` / `WPL-004`) |
| Path 변수 | `memberId` = 퇴사 대상의 **현재 WorkplaceMember ID** |
| 대상 조건 | 같은 근무지, 현재 소속, EMPLOYEE (ML-H2 H2-9). 대상 소속이 없거나 다른 근무지 소속이면 **404** (§1 대상 범위 오류). 같은 근무지의 이미 종료된 소속이면 409 STATE_CONFLICT |
| 응답 `data` | `member`: { `memberId`, `name`, `role`, `joinedAt` } / `canLeave` / `blockingReason` / `ongoingShift` / `publishedShifts[]` / `draftShiftsToDelete[]` / `candidacies[]` / `previewContext` |
| 퇴사 가능 여부 | 진행 중 공식 근무(`PUBLISHED`·`SCHEDULED`, `startAt ≤ now < endAt`)가 있으면 **200 + `canLeave = false`**, `blockingReason = "ONGOING_SHIFT"`, `ongoingShift`에 해당 근무와 종료 시각 반환. 여러 건이면 종료 시각이 가장 늦은 근무 (동률이면 큰 `shiftId`)를 반환. 없으면 `canLeave = true`, `blockingReason = null`, `ongoingShift = null`. `endAt == now`는 진행 중이 아니다. 조회에서 `WPL-009`를 반환하지 않으며, 확정의 409 계약은 API-LV-02에 유지 |
| 근무·대타 영향 형식 | `ongoingShift`·`draftShiftsToDelete[]`: { `shiftId`, `memberId`, `scheduleId`, `scheduleStatus`, `shiftStatus`, `weekStartDate`, `startAt`, `endAt` }. `publishedShifts[]`: 같은 근무 식별·상태·시간 필드 (`weekStartDate` 제외) + `activeRequests[]`: { `requestId`, `requesterMemberId`, `status`, `wouldCloseRequest` }. `candidacies[]`: { `candidateId`, `requestId`, `requesterMemberId`, `shift` (위 근무 형식), `requestStatus`, `candidateStatus`, `otherCurrentPendingCandidates`, `wouldBecomeIneligible`, `wouldCloseRequest` }. 효과는 예상값이며 조회로 상태를 바꾸지 않음 |
| 비교 정보 (발급 구현) | `previewContext`는 문자열 `lv1.<payload>.<signature>`. payload는 JSON을 Base64 URL-safe 무패딩으로 인코딩하고, signature도 같은 인코딩을 사용. JSON 필드: `schemaVersion = 1`, `actorUserId`, `workplaceId`, `memberId` (WorkplaceMember ID), `issuedAt`, `expiresAt` (Instant), `facts` (정규화한 `LeavePreviewFacts`: 소속·공식/진행 중/DRAFT 근무·활성 요청·후보 관계/상태/예상 효과). **`expiresAt = issuedAt + 15분`** |
| 서명·후속 검증 | `HMAC-SHA256`으로 `lv1.<payload>`를 서명. JWT 비밀키에서 용도 문자열 `switch:leave-preview:v1`을 HMAC해 파생한 별도 서명 키 사용. 로그인 Token이나 확정 권한이 아니다. **서명·만료 검증, 실행자/근무지/대상 결합 확인, 현재 권한·DB 재검증 및 STALE 비교는 후속 API-LV-02 범위**이며 PR #90의 발급 구현과 구분. 재사용·오류 분기 등 미결정 사항은 G03-D-05 §5-4와 Decision Log §4-4 유지 |
| 넣지 않는 것 | 불가능 시간 충돌 (인계 대상을 고르기 전이라 계산할 수 없음, G03-D-05 §3) |
| 상태 변경 | 없음 (조회). `@Transactional(readOnly = true, isolation = REPEATABLE_READ)`로 표시값과 서명 원본을 같은 조회 기준에서 구성. 명시적 Lock·업무 쓰기 없음 |
| 오류 | 403 PERMISSION (`WPL-003`·`WPL-004`) → 404 NOT_FOUND (`WPL-006`: 대상 소속 없음·다른 근무지) → 409 STATE_CONFLICT 순서로 판정. 같은 근무지의 대상은 **소속 종료 여부 (`WPL-008`) → 관리자 역할 (`WPL-007`) 순서**로 검사하므로, **이미 종료된 MANAGER 소속에도 `WPL-008`이 반환된다** |
| 데이터 출처 | 대상 소속, 퇴사자 담당 Shift (PUBLISHED·SCHEDULED·`endAt > now`인 진행 중/미래 근무 및 시각 제한 없는 DRAFT), 미래 공식 근무의 활성 Request, 대상이 PENDING·ACCEPTED Candidate인 활성 Request (같은 근무지의 PUBLISHED·SCHEDULED·시작 전 근무에 한함) |
| Test | ML-4 V2-N-23 (미리보기 내용) 및 관련 권한·대상·경계·ML-3 예상 효과 검증: [TEST-INVENTORY §4-3](../evidence/TEST-INVENTORY.md#4-3-api-lv-01-퇴사-미리보기-pr-90). 조회 Test와 퇴사 확정 명세의 전체 검증을 구분 |

### 3-2. API-LV-02 퇴사 확정

| 항목 | 내용 |
|---|---|
| 권한 | API-LV-01과 같음 |
| 요청 | `previewContext` (API-LV-01에서 발급한 문자열을 그대로 전달하는 후보. 발급 형식은 §3-1, 확정 검증은 후속 구현) / `decisions[]`: { `shiftId`, `shiftStartAt`, `shiftEndAt` (G03-D-05 P2 비교용), `action`: `REASSIGN` + `targetMemberId` (**인계 대상의 현재 WorkplaceMember ID**) 또는 `CANCEL` } / `acknowledgedUnavailableConflicts[]` (G03-D-05 §5-1, 처음에는 빈 목록) / `regenerateInviteCode` (기본 `false`, ML-H2 H2-5) |
| 검사 순서 | ① 권한 → ② 계획 유효성 P1~P5 (G03-D-05 §4-2) → ③ 우회할 수 없는 충돌 (인계 대상의 공식 근무 중복, 다른 근무지, 시작한 근무) → ④ 불가능 시간 확인 범위 (§5-3 겹침 구간 비교) |
| 성공 응답 | 200. `data`: `leftAt`, `reassignedShifts[]`, `cancelledShifts[]`, `deletedDraftShifts[]`, `closedRequests` (총수와 사유별 수: `SHIFT_CHANGED`, `CANDIDATE_INELIGIBLE`. 세는 규칙은 API-PS-02 `closedSubstituteRequests`와 같다. 한 요청은 사유 하나로만 세므로 사유별 수의 합 = 총수), `ineligibleCandidacies`, `newInviteCode` (재발급했을 때만) |
| 404 응답 | Path의 대상 소속이 없거나 다른 근무지 (API-LV-01과 같음). 요청 Body의 `targetMemberId`·`shiftId`가 그 근무지에 없거나 다른 근무지 것이어도 같은 404 (존재 여부를 드러내지 않음) |
| 409 응답 | **CONFIRM_REQUIRED** (`data` = G03-D-05 §5-1 충돌 목록, 겹침 구간만) / **STALE** (계획이 지금과 다름 → 미리보기 다시 조회. 예: 같은 근무지의 인계 대상이 그 사이 퇴사, 근무가 그 사이 인계·취소됨, 미리보기 이후 DRAFT/대타 자동 대상 추가·교체·처리 효과 확대. 자동 대상이 감소하기만 하면 다른 조건 재검증 후 진행 가능) / **STATE_CONFLICT** (진행 중 근무 — `data`에 종료 시각, 이미 종료된 소속, 대상이 MANAGER) / **FORBIDDEN_CONFLICT** (인계 대상의 공식 근무 중복. 기존 SFT·SCH code 재사용 여부는 S2-4) |
| 상태 변경 (한 Transaction) | 대상 소속 `leftAt = now` / 인계 근무: `Shift.member = 대상` + 배정 이력 (`MEMBER_LEFT`) / 취소 근무: `CANCELLED` + 취소 사유 `MEMBER_LEFT`·시각·행위자 / DRAFT 삭제 / 퇴사자 근무의 활성 Request → `CLOSED / SHIFT_CHANGED` / 퇴사자가 후보인 Request: PENDING → `INELIGIBLE`, 수락자면 Request `CLOSED / CANDIDATE_INELIGIBLE` / (선택) 초대 코드 재발급. Pattern은 보존하고 자동 적용 제외 |
| Lock (ML-5 후보, 실험 전) | (재발급 시 Workplace) → 관련 Schedule → 퇴사자 근무 Shift → 관련 Request → 인계 대상 User (ID 오름차순) → 인계 대상 소속 `FOR SHARE` + 퇴사 대상 소속 `FOR UPDATE` → 다시 조회 → 미리보기와 대상+처리 효과 비교(추가/교체/확대는409 STALE). 검사 후 삽입 경합도 공통 Guard로 검증 |
| 알림 (Commit 후, 최선 노력) | 인계받은 직원 (근무 배정), 대타 종료 관련자 (요청자·ACCEPTED 후보, **실행 관리자를 뺀 다른 현재 관리자**, 현재 소속만, ML-3·NOTIFICATION-TYPES §6-3-2), 퇴사자는 받지 않음. 대체 조회 화면: 예정 근무, 보낸·수락 대타 목록, 관리자 끝난 대타 (API-SB-02) (G03-D-22) |
| 구현 (2026-10-29) | PR #102 핵심 로직 + **POST 활성화** (소속 Guard #105·#106 뒤 Readiness Bean, `feat/v2-leave-confirm-activate`). 오류 code: 공식 근무 충돌은 기존 `SFT-009`/`SFT-010`, 원본 검증 실패는 `WPL-010` (DL-016, 팀 기록 필요). 알림은 소속 종료 뒤 발행 (V5-N-20). Test `LeaveConfirmHttpIntegrationTest` (Security Filter·JWT) |
| 데이터 출처 | API-LV-01과 같고, 인계 대상의 불가능 시간(본인 User 기준), 인계 대상의 공식 근무 (User 기준 충돌 검사) |
| Test | ML-4 V2-N-24~40, ML-2 V3-N-01~08, ML-3 V4-N-08~22 일부, G03-D-05 D05-T1~T9·T11, G03-D-21 D21-T7~T9 |

### 3-3. API-PS-01 공개 근무표에 근무 추가

| 항목 | 내용 |
|---|---|
| 권한 | 그 근무지의 현재 MANAGER |
| 대상 | PUBLISHED Schedule. 추가하는 근무는 시작 전. Path의 근무표가 없거나 다른 근무지면 404 (§1) |
| 요청 | `memberId` (현재 WorkplaceMember ID), `startAt`, `endAt`, `acknowledgedUnavailableConflicts[]`: `{ newShiftKey: { scheduleId, targetMemberId, shiftStartAt, shiftEndAt }, overlaps[] }` (신규 추가는 shiftId 없음, G03-D-05 §5-1) |
| 기존 DTO와의 관계 | 기존 `ShiftRequest`(`confirmUnavailableConflict: boolean`)를 재사용하지 않고 새 요청 DTO. 기존 DRAFT API 형식은 그대로 |
| 검사 | 근무 시간 규칙 (같은 날, 주차 범위, 시작 < 종료 — 기존 DRAFT 검사와 같은 규칙), 같은 근무표 안 겹침, 다른 근무지 공식 근무 겹침 (우회 불가), 담당자 현재 소속, 불가능 시간 (확인 범위) |
| 성공 응답 | 201 + 추가된 근무. 첫409에서는 Shift·배정 이력·알림 저장0; 확인 재요청이 현재 계획과 일치하고 재검증을 통과한 경우에만 생성 |
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
| 성공 응답 | 200 + 취소된 근무 (상태, 취소 사유·시각) + `closedSubstituteRequests` (이번 Command의 Transaction에서 OPEN·ACCEPTED → CLOSED로 실제 바뀐 서로 다른 대타 요청 수. 이미 APPROVED·CLOSED였던 요청, 후보 수, 알림 수, 근무 수는 세지 않는다). 실행한 관리자는 이 응답으로 결과를 보고 대타 종료 알림을 받지 않는다 ([NOTIFICATION-TYPES](NOTIFICATION-TYPES.md) §6-3) |
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
| 성공 응답 | 200 + 근무 (같은 근무 ID, 새 담당) + `closedSubstituteRequests` (세는 규칙은 API-PS-02와 같다). 실행한 관리자는 대타 종료 알림을 받지 않는다 (NOTIFICATION-TYPES §6-3) |
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

## 5. B2 — 소속·조회·인증 (새 API 5개, 기존 API 동작 변경 6개)

### 5-0. 권한 모델 세 가지 (모든 조회를 `leftAt IS NULL`로 묶지 않는다)

| 모델 | 대상 | 기준 | 적용 API |
|---|---|---|---|
| **M1 현재 업무** | 지금 해야 할 일, 지금 처리할 대타 | 실행자의 **현재 소속**(`leftAt IS NULL`). 이전 소속 Row의 업무는 보이지 않는다 | 예정 근무, 근무 상세, 빠진 근무, SUB-05·06·07, 종료된 대타, 알림 |
| **M2 본인 과거 소속** | 내가 어디에 언제 소속됐었나 | 인증된 **본인 User**의 종료된 소속 Row. 다른 사람 것은 볼 수 없다 | 내 소속 이력 |
| **M3 ID 직접 지정 검사** | `requestId`·`shiftId`·`candidateId`·`notificationId`를 Path로 받는 상세·Command | **M1·M2와 따로 있는 권한 모델이 아니라, ID로 접근할 때 M1(또는 M2)에 더해 적용하는 추가 검사다.** 그 ID의 Resource가 실행자의 권한 범위(현재 관리자인 근무지, 본인 Candidate, 본인 알림 등)에 있는지 확인하고, **범위 확인 전에는 Resource의 상태를 드러내거나 Lock을 잡지 않는다** (§5-5) | 근무 상세, 대타 승인·종료, 후보 응답, 알림 읽음 |

이전 소속 기간의 **업무 상세**(과거 근무·대타 상세)는 이번 MVP에서 제공하지 않는다 (ML-4-6).

### 5-1. 새 API

| ID | 기능 | Method · Path (후보) | 출처 |
|---|---|---|---|
| API-WP-01 | 초대 코드 재발급 | `POST /workplaces/{workplaceId}/invite-code` | [ML-H2](../decisions/proposals/ML-H2-rejoin-and-active-membership.md) H2-5, §8 |
| API-SC-01 | 빠진 근무 | `GET /schedules/me/removed` | [ML-4](../decisions/proposals/ML-4-leave-command-and-access.md) §6-1 |
| API-ME-01 | 내 소속 이력 | `GET /me/memberships?status=ENDED` (또는 `GET /workplaces/history`) | ML-4 §6, ML-4-5 |
| API-SB-01 | 종료된 대타 (내가 수락한 요청) | `GET /substitute-requests/accepted/closed` | [G03-D-22](../decisions/proposals/G03-D-22-notification-delivery.md) §6 |
| API-AU-01 | 서버 로그아웃 | `POST /auth/logout` | [AUTH-1](../decisions/proposals/AUTH-1-server-logout.md) |
| API-SB-02 | 관리자 — 끝난 대타 요청 (종료·만료) | `GET /workplaces/{workplaceId}/substitute-requests/closed` | [USER-PATH-REVIEW](USER-PATH-REVIEW.md) §9-3 (S3-A) |

#### API-WP-01 초대 코드 재발급

| 항목 | 내용 |
|---|---|
| 권한 | 그 근무지의 현재 MANAGER. 다른 근무지 → 404 (§1 대상 범위 오류) |
| 기존 API와의 관계 | 같은 Path의 `GET`(현재 코드 조회)은 그대로. Method만 다른 새 Command |
| 요청 / 응답 | 요청 Body 없음 / 200 + 새 코드 |
| 상태 변경 | `Workplace.inviteCode` 교체. 이전 코드는 즉시 무효 (이후 가입 시 `WPL-001`). 새 코드 ≠ 직전 코드, 다른 근무지 코드와 중복 아님. 이전 코드 이력은 저장하지 않음 (ML-H2 §8 한계) |
| Lock (ML-H2 §8 B안, 실험 전) | Workplace `FOR UPDATE`. 가입도 같은 Row를 잠그고 Lock 후 제출 코드를 최신 코드와 다시 비교 → 재발급 확정 후에는 이전 코드로 가입이 성공하지 않음 (ML-H2-INV-04) |
| 퇴사 화면 연동 | API-LV-02의 `regenerateInviteCode`로도 같은 처리 (같은 Transaction) |
| 오류 | 403 (권한), 404 (범위) |
| 알림 | 없음 (새 코드는 관리자가 직접 전달, ML-H2 §4-③) |
| Test | ML-H2 V2-N-11 |

#### API-SC-01 빠진 근무

| 항목 | 내용 |
|---|---|
| 권한 모델 | M1. 실행자의 현재 소속 기준 |
| 응답 | 근무별 한 줄: 근무 ID, 근무지, 원래 시작·종료, 사건(`CANCELLED` 또는 `REASSIGNED`), 사건 시각, 사건 사유(`MEMBER_LEFT`, `MANAGER_CANCELLED`, `MANAGER_REASSIGNED`). **새 담당자의 이름·ID는 넣지 않는다** ([FRONTEND-CONTRACT](FRONTEND-CONTRACT.md) §9-2, 사용자 잠정 결정 2026-10-25) |
| 조회 범위 | 원래 근무 시작 시각 기준 **미래 + 최근 7일**. 조회 범위일 뿐 보존 기간이 아님 |
| 데이터 출처 | ① `shift.member = 나의 현재 소속`이고 CANCELLED (Shift 취소 정보) ∪ ② 배정 이력에서 `이전 담당 = 나의 현재 소속` (가장 마지막 Row). 지금 내가 담당인 근무는 제외. 대타 승인으로 넘어간 근무는 제외 (SUB-05 APPROVED에서 봄) |
| 상태 변경 | 없음 |
| Test | ML-4 V2-N-39 (조회 규칙) 등 |
| 메모 | ML-4 §6-1에 "'시작 전'으로 범위를 제한하면 건수가 작다"는 비용 메모가 있는데, 결정된 범위는 "미래 + 최근 7일"이다. 구현 때 건수를 다시 확인한다 |
| 이력 근거가 충분한가 | 인계 전 담당자는 **새로 만드는 배정 이력 Table**(ML-2 D27-4, 당시 WorkplaceMember ID 저장)에서만 알 수 있다. AS-IS에는 공개 근무를 관리자가 인계하는 경로가 없고, 담당자를 바꾸는 유일한 경로는 대타 승인인데 이것은 빠진 근무 대상이 아니다. 그래서 **기능 출시 이후의 사건은 모두 이력에 남고, 출시 이전에는 재구성할 사건이 없다** (정적 추론). 취소는 Shift가 끝 상태라 `shift.member`가 취소 당시 담당자다 (ML-4 §6-1) |
| 구현 (2026-10-29) | `feat/v5-removed-shifts` (V3 담당과 협의해 V5가 구현), Test `RemovedShiftQueryServiceTest` 12건 (V2-N-39 포함, 실제 MySQL, 취소·재배정은 API-PS-02·03 Command로 생성). 응답 필드: `shiftId`, `workplaceId`, `workplaceName`, `startAt`, `endAt`, `event` (`CANCELLED`/`REASSIGNED`), `occurredAt` (취소 = `cancelledAt`, 인계 = 배정 이력 생성 시각), `reason`. 문서에 없어 V5가 정한 것: 정렬 = 사건 시각 최신순·같으면 근무 ID 최신순 (API-SB-01과 같음, 알림에서 들어온 사건이 맨 위) — ML-4 §6-1의 "근무 시각순"을 PR #95 리뷰 합의로 같이 고침, 7일 경계 포함 (`startAt >= now - 7일`), "가장 마지막 Row" = 그 근무에서 내가 이전 담당인 이력 중 ID 최대, `cancelledAt`이 없는 CANCELLED Row는 제외 (현재 CANCELLED로 바꾸는 경로는 `Shift.cancel`뿐이고 항상 기록함). `MEMBER_LEFT`는 퇴사 확정(API-LV-02) 구현 전이라 데이터가 생기지 않는다 |

#### API-ME-01 내 소속 이력

| 항목 | 내용 |
|---|---|
| 권한 모델 | **M2.** 인증된 본인 User의 종료된 소속. 퇴사 후에도 User 계정과 로그인은 그대로이므로 본인 인증으로 조회할 수 있다 (소속이 없어도 됨) |
| 응답 | `memberships[]`: 소속 ID, 근무지 이름(현재 값), 역할, `joinedAt`, `leftAt`. `leftAt` 내림차순. 같은 근무지에 여러 번 소속됐다면 기간마다 따로 |
| 기존 API와의 관계 | 기존 `GET /workplaces`는 **현재 소속만** (`findAllByUser_IdAndLeftAtIsNull`). 넓히지 않고 새 API |
| 제외 | 과거 근무·대타 상세 (ML-4-6). 근로 증명 용도 아님 ("소속 이력"으로 표현) |
| Test | ML-4 §10 해당 행 |

#### API-SB-01 종료된 대타 (내가 수락한 요청)

| 항목 | 내용 |
|---|---|
| 권한 모델 | M1. 실행자의 현재 소속 Candidate (`c.member.leftAt IS NULL`)만 |
| 대상 | 내 Candidate가 ACCEPTED이고 Request가 끝난 것: APPROVED, CLOSED, 또는 OPEN·ACCEPTED이면서 `startAt ≤ now` (계산된 만료) |
| 조회 범위 | 효력 종료 시각 기준 최근 7일 (저장된 종료 `closedAt`, 승인 `approvedAt`, 자동 만료 `startAt`) |
| 응답 | 요청 ID, 근무 시작·종료, 근무지, 요청자 이름, `requestStatus`, `closeReason`, `candidateStatus`, 효력 종료 시각. **현재 근무 담당자 정보와 다른 후보 정보는 넣지 않는다.** 문구는 Frontend가 정한다 (G03-D-22 §6-2) |
| DTO 출처 | 수락자 = Candidate. `SubstituteRequestResponse.from`(현재 `Shift.member`를 읽음)을 재사용하지 않는다 |
| 상태 변경 | 없음. 이 목록의 항목에 수락·거절 Command는 기존 검사로 거부 |
| Test | G03-D-22 D22-T2~T12, D22-T4b |
| 구현 (2026-10-28) | `feat/v4-accepted-closed-requests` `36c51d2`, Test V4-N-27~35·38·60 (EV-037). 문서에 없어 V4 Owner가 정한 것: 정렬 = 효력 종료 시각 최신순·같으면 요청 ID 최신순 (API-SB-02와 같음), 응답의 효력 종료 시각 필드 이름 `closedAt` (SUB-05와 같음), 7일 경계 포함 (`>= now - 7일`). 계산된 만료는 SUB-05처럼 `CLOSED` / `EXPIRED`. `closedAt`이 없는 옛 CLOSED·APPROVED Row는 목록에서 빠진다 |

#### API-SB-02 관리자 — 끝난 대타 요청 (종료·만료) (S3-A, 사용자 잠정 결정 2026-10-25, 팀 `PENDING`)

| 항목 | 내용 |
|---|---|
| 목적 | 알림을 놓친 관리자가 끝난 대타 요청을 다시 확인하고 필요한 조치(다른 직원에게 담당자 변경 등)를 판단 (G03-D-22 대체 화면 원칙, USER-PATH-REVIEW §8 빈칸 해소) |
| Method · Path (후보) | `GET /workplaces/{workplaceId}/substitute-requests/closed?from=&to=&page=&size=` |
| 권한 모델 | M1. Path 근무지의 현재 MANAGER (`requireManager`, 기존 403 `WPL-003`·`WPL-004`) |
| 대상 | 그 근무지 근무의 Request 중 끝난 것: **CLOSED (모든 사유)** 또는 **OPEN·ACCEPTED이면서 `startAt ≤ now`** (계산된 만료, G03-D-19). APPROVED는 넣지 않는다 (승인 결과는 근무표에서 확인). `now`는 서버 `Clock`. 경계 `startAt == now`는 만료로 본다 — AS-IS 만료 판정 `!now.isBefore(startAt)` (`SentSubstituteRequestResponse` L35)과 같고, SUB-06의 `startAt > now`와 정확히 나뉜다 |
| 기간 | `from`·`to` = 효력 종료 시각 범위, 기본값 최근 7일. **7일은 기본 필터일 뿐 보존 기간·조회 상한이 아니다.** 효력 종료 시각 = CLOSED는 저장된 `closedAt`, 만료는 `startAt` (API-SB-01과 같은 정의). DB 식 (**위 "대상" 조건으로 먼저 거른 행에만** 적용, APPROVED에는 쓰지 않음 — APPROVED도 `closedAt`을 씀): `CASE WHEN status = 'CLOSED' THEN closedAt ELSE shift.startAt END` — CLOSED 전이는 모두 `closedAt`을 쓴다 (`SubstituteRequest` L70-73, L92-95; S3-B 정적 확인). 필터·정렬·페이지는 DB에서 처리 (성능은 S4) |
| 정렬·페이지 | 효력 종료 시각 내림차순, 같으면 Request ID 내림차순. `page`·`size` (최대 `size`는 구현 계약). 다음 페이지 여부 포함 |
| 응답 항목 | 요청 ID / 근무 (ID, 시작·종료, 상태 `SCHEDULED`·`CANCELLED`) / `outcome` (`CLOSED` + `closeReason`, 또는 `EXPIRED`) / 효력 종료 시각 / 요청자 이름 + `requesterMembershipEnded` (요청 당시 소속 Row가 지금 종료됐는지) / 수락자 이름 (ACCEPTED 후보가 있었던 경우만) / **사실 Field:** `requesterStillAssigned` (지금 `Shift.member`가 요청 당시 요청자 소속 Row와 같은가), `shiftStarted`, `hasNewerActiveRequest` (같은 근무에 더 최근의 **진행 중** 요청이 있는가 — 진행 중 = DB OPEN·ACCEPTED ∧ `startAt > now`, SUB-06과 같은 정의) |
| 이름 | 요청자·수락자 이름은 현재 `User.name`을 읽는다. 지금은 이름 변경 기능이 없어 요청 당시 이름과 같다 (`User` 생성자에서만 설정, S3 Commit 전 정적 확인). 이름 변경 기능이 생기면 스냅샷 보존을 다시 정한다 |
| 넣지 않는 것 | **User ID·소속(WorkplaceMember) ID** (응답 ID는 요청 ID·근무 ID뿐), 거절·대기 후보 목록, 지금 담당자가 요청자가 아닐 때의 현재 담당자 이름(근무표에서 확인), 불가능 시간 사유, 다른 근무지 정보. 요청자를 **현재** 소속 ID와 연결하지 않는다 (재가입 후 새 소속과 합치지 않음) |
| 후속 조치 판단 | 서버는 판단하지 않고 사실 Field만 준다. "조치 필요" 표시 규칙은 [FRONTEND-CONTRACT](FRONTEND-CONTRACT.md) §3-4 |
| 기존 API와의 관계 | 관리자 진행 중 목록(SUB-06: OPEN·ACCEPTED ∧ `startAt > now`)은 바꾸지 않는다. 두 목록은 겹치지 않는다 |
| 상태 변경 | 없음. 계산된 만료를 DB에 CLOSED로 저장하지 않는다 (G03-D-19 D19-2와 같음) |
| 경로·ID 표기 | 실제 경로는 `/api/v1` 뒤 (§1). **`API-SB-02`는 새 API 목록 번호이고 V4 Delivery Sheet의 `SUB-02`(받은 요청)와 다른 것이다** |
| 한계 | `SUBSTITUTE_NO_CANDIDATE`는 Request Row가 없어 나오지 않는다 (USER-PATH-REVIEW O-2) |
| 선행 조건 | G03-D-19 만료 정의 (팀 `PENDING`). `SHIFT_CHANGED`·`CANDIDATE_INELIGIBLE` 사유는 ML-3 이후에 생기며, 그 전에는 기존 사유만 나온다 |
| 비용 (추정) | API 1, 효력 종료 시각을 계산·정렬하는 Query 1 (`COALESCE`), DTO 1, Test 6개, 승인 관리 화면 탭 1 |
| 등급 | **P0 후보** (묶음 B) |
| Test | V4-N-49~54 |
| 구현 (2026-10-29, 구현, 팀 승인은 PR 리뷰로 요청 중) | `feat/v4-manager-closed-requests`, Test V4-N-49~54 (EV-044). 경로 확정 `GET /workplaces/{workplaceId}/substitute-requests/closed?from=&to=&page=&size=` (`SubstituteRequestController`, SUB-06 옆). 문서에 없어 V4 Owner가 정한 것: `from`·`to` = ISO 날짜시각, 둘 다 포함, 기본 `from = now − 7일`·`to = now` / `page` 0부터, `size` 기본 20·최대 100, `from > to`·범위 밖 → 400 `COM-003` / 응답 `data = { items, page, size, hasNext }` (전체 개수 없음) / 만료는 `outcome = EXPIRED`·`closeReason = EXPIRED`·`closedAt = startAt` (API-SB-01·SUB-05와 같은 표기) / 없는 근무지는 `requireManager`만으로 403 `WPL-003` (§1 권한 먼저. SUB-06의 404 `WPL-005` 선검사는 따르지 않음. PR #104 리뷰에서 현재 동작(AS-IS)으로 확인) / `hasNewerActiveRequest`의 "더 최근" = 요청 ID가 더 큼 / 한 요청에 수락자가 여러 명이면 한 명만 고르지 않고 400 `SUB-016` (SUB-06 `getAcceptedCandidates`와 같음, ML-3 §3 "여러 명이면 오류". PR #104 리뷰 반영. API-SB-01의 같은 실패 경로(V4-N-21)는 오류 code 팀 결정 대상 그대로) |

#### API-AU-01 서버 로그아웃

| 항목 | 내용 |
|---|---|
| 인증 | **Access Token 없이** Refresh Cookie로 처리 (AUTH-1 Q1-2). Security 공개 경로에 추가 필요 (지금 공개 경로: signup, login, refresh) |
| 출처 검증 | `Origin`을 허용 목록과 대조, 없으면 `Referer` 출처. 실패·둘 다 없음 → 403, 상태 변경 0 (AUTH-1 S2). **AS-IS 비교:** CSRF 보호는 꺼져 있고(`csrf.disable()`), CORS 허용 출처는 `custom.cors.allowed-origins` (환경 변수 `CORS_ALLOWED_ORIGINS`)다. CORS는 CSRF 방어가 아니므로 로그아웃에만 Origin 검증을 새로 둔다. 허용 목록을 어디서 읽을지(같은 설정 재사용 여부)는 구현 때 정한다. `application*.yml` 변경이 필요하면 AI-RULES §4 승인 대상 |
| 상태 변경 | 쿠키의 Refresh Token이 DB 저장 값과 같을 때만 한 번의 조건부 UPDATE로 무효화 (Q1-1). 로그인마다 `jti` (S1). 쿠키가 없거나 이미 무효면 성공 (멱등) |
| 응답 | 200. 브라우저 Refresh Cookie를 발급 때와 같은 속성으로 삭제. AS-IS 쿠키: `HttpOnly`, `Path=/`, `Secure`·`SameSite`는 설정값 (`Rq` L52-80). 삭제 Helper(`Max-Age=0`)가 같은 속성을 쓰는지는 구현 때 확인 |
| 하지 않는 것 | Access Token 즉시 무효화, 다중 기기 세션, Rotation (AUTH-1 Q2-1, G03-D-24) |
| 배포 환경 확인 (S3) | Frontend와 Backend의 출처가 다르면(다른 도메인) Refresh Cookie는 `SameSite=None; Secure`여야 전송된다. 이 값과 허용 출처 목록이 운영 배포와 맞는지, `Origin`이 비는 요청(일부 브라우저·직접 호출)이 `Referer`로 처리되는지 확인. 운영 설정값은 배포 담당 확인 (운영 접속 금지) |
| Frontend | 결과와 관계없이 화면 정리. 서버 무효화가 확인되지 않으면 안내하고 다시 시도 (Q5-1) |
| Test | AUTH-1 V1-N-01~13 (TEST-INVENTORY V1 AUTH-1 절) |
| 구현 (2026-10-28) | Issue #62 `feat/auth-server-logout`은 Backend PR #74 병합 후 종료됐다. Frontend는 PR #76에서 병합됐다. 경로 `POST /auth/logout` 확정, 성공 200 `"로그아웃되었습니다"`·`data: null`. 허용 목록은 `custom.cors.allowed-origins` 재사용 (`application.yml` 변경 없음). **허용 안 된 Origin·`Origin: null`은 기존 CORS가 먼저 403 `Invalid CORS request`(JSON 아님)**, Origin 없음 + Referer 실패·둘 다 없음은 403 `AUTH-007`. 세부는 AUTH-1 "Backend 구현 기록" 절. 병합은 배포 환경 검증 완료를 뜻하지 않는다. |

### 5-2. 기존 API 동작 변경 (모두 승인 대상, Migration 후 Improvement)

| ID | 기존 API | 지금 | 바뀌는 점 | 근거 |
|---|---|---|---|---|
| CHG-01 | `POST /workplaces/join` | 같은 (근무지, User) Row가 있으면 `WPL-002` (퇴사자 포함). DB Unique `(workplace_id, user_id)` | 유효 코드 + 현재 소속 없음이면 **새 WorkplaceMember Row** (재가입). 이전 Row는 그대로. 현재 소속이면 `WPL-002`. 동시 가입은 현재 소속 Unique(U2' 우선, 실험 조건부) 위반을 `WPL-002` 409로 변환 (지금은 500). 가입도 Workplace Row Lock 후 코드 재비교 (ML-H2 §8 B안) | ML-H2 |
| CHG-02 | SUB-05 `GET /substitute-requests/sent` | User 기준 (`findAllByRequesterMember_User_Id…`), 소속 조건 없음 | **현재 소속의 요청만** | ML-4-4, G03-D-22 §6-4. **구현 (2026-10-29):** `findAllByRequesterMember_User_IdAndRequesterMember_LeftAtIsNullOrderByCreateDateDesc` (현재 소속 조건 추가, 응답·정렬·에러 그대로), Test V4-N-37 (EV-042) |
| CHG-03 | SUB-07 `GET /substitute-requests/accepted` | User 기준, `c.member.leftAt` 조건 없음 | 현재 소속 조건 추가 (방어) | G03-D-22 §6-4. **구현:** PR #82 `e643c30`이 `c.member.leftAt IS NULL` 추가, Test V4-N-36 (EV-042) |
| CHG-04 | `GET /shifts/{shiftId}` | 공개·SCHEDULED·담당 **User** = 나 확인. 담당 소속이 현재 소속인지는 확인 안 함 | 담당 소속이 현재 소속인지 확인 (재가입자가 이전 소속 근무를 ID로 보지 못하게). CANCELLED 거부는 유지 | ML-4 §8 ① |
| CHG-05 | 대타 승인 `PATCH .../approve`, 후보 응답 `PATCH .../response` | 시작한 근무에서 승인 `SFT-006`, 응답 `SUB-012` (요청 문구) | 잘못된 안내 정정 (T1-01·02). **P1** | G03-D-03 §3-1 |
| CHG-06 | 대타 승인 `PATCH .../approve` | **Request를 `FOR UPDATE`로 잠그고 상태를 검사한 뒤에 관리자 권한을 검사한다** (`SubstituteRequestService` L236 → L241 → L247). 그래서 그 근무지 관리자가 아닌 인증 사용자도 임의 `requestId`로 존재·상태(404 / `SUB-002` 400 / 403)를 구분할 수 있고 Row Lock을 잠깐 잡게 된다 | M3 선검사 (§5-5) | **사용자 잠정 결정 2026-10-25: 대타 P0 정합성 묶음에 포함 (§5-4 (나))**. 2026-10-28 구현 PR #83 (DECISION-LOG §4-6) |
| CHG-07 | M3 선검사를 다른 ID 직접 지정 API에도 | §5-5 표 | §5-5 | **사용자 잠정 결정 2026-10-25: (가) 같은 원칙 적용 (§5-6)** |

**등급 (Improvement 기준, Kotlin Migration은 모두 기존 동작 보존):**

| 항목 | 등급 | 이유 |
|---|---|---|
| CHG-01 재가입·현재 소속 Unique | P0 (묶음 D) | 재가입 기능 자체 |
| CHG-02·03 SUB-05·07 현재 소속 | P0 (묶음 C·D 조건) | 재가입·다른 근무지 퇴사 후 이전 소속 기록 노출 방지 |
| CHG-04 근무 상세 현재 소속 | P0 후보 (묶음 D 조건) | 재가입자가 이전 소속 근무를 ID로 조회. 권한 위험도 평가 대상 (§5-5) |
| CHG-06 대타 승인 M3 선검사 | **P0 (대타 정합성 묶음, 사용자 잠정 결정)** | 권한 경계 문제. ML-5의 승인 Lock 순서 변경 후보와 같은 코드 |
| CHG-07 근무 상세·후보 응답 M3 선검사 | P0 | 대타·퇴사·재가입 묶음에서 어차피 고치는 API. 근무 상세는 상태 노출, 후보 응답은 Lock 점유 |
| CHG-07 대타 종료·알림 읽음 M3 선검사 | **P0 검토 후보** (최종 등급은 §5-6 조건 확인 후) | 정적 확인으로는 권한 확인 전 DB 변경·이벤트가 없고 존재 노출과 Lock 점유만 있음. Lock 영향은 실행으로 확인 |
| CHG-05 잘못된 안내 code | P1 | 사용자 흐름을 막지 않음. 막는 사례가 나오면 P0로 다시 평가 (G03-D-03 D03-5) |
| 새 API (소속 이력, 종료된 대타, 빠진 근무, 초대 코드) | 각 기능 묶음 (E, D) | 기능의 일부 |
| 서버 로그아웃 | 묶음 A (AUTH-1) | — |

### 5-3. SUB-05·SUB-07을 네 가지로 나눈 계약

| 구분 | SUB-05 보낸 요청 | SUB-07 수락한 요청 (진행 중) |
|---|---|---|
| 상태 정합성 | 퇴사 시 내 근무의 활성 요청은 `SHIFT_CHANGED`로 종료 (ML-3-Q1a) | 내가 수락자인 채로 퇴사하면 그 요청은 `CANDIDATE_INELIGIBLE`로 종료 (ML-3-A) |
| 조회 권한 | 현재 소속의 요청만 (CHG-02) | 현재 소속 Candidate만 (CHG-03) |
| 이력 보존 | 이전 소속 요청 Row는 지우지 않음. 화면에서만 빠짐 | 이전 소속 Candidate Row는 지우지 않음 |
| 재가입 격리 | 요청은 당시 `requester_member_id`를 가리키므로 새 소속과 섞이지 않음 | Candidate는 당시 `member_id`를 가리키므로 섞이지 않음 |
| ID 직접 지정 (M3) | 해당 없음 (목록) | 응답 Command `PATCH /substitute-candidates/{id}/response`는 본인 Candidate(`SUB-020`)·현재 직원(`SUB-021`)·PENDING(`SUB-022`)을 이미 확인 |

**같은 근무지를 떠났지만 다른 근무지에는 남은 사람:** 두 목록 모두 User 전체가 아니라 **현재 소속 Row**로 거르므로, 떠난 근무지의 기록만 빠지고 남은 근무지의 기록은 보인다.

### 5-4. CHG-06 결정 (사용자 잠정 결정 2026-10-25: (나))

대타 승인의 권한 검사 순서 개선을 **대타 P0 정합성 묶음**에 넣는다. 이유: 안내 문구가 아니라 권한 경계 문제이고, ML-5의 승인 Lock 순서 변경 후보(Shift → Request)와 같은 흐름을 고치게 되므로 한 번에 정리한다. Kotlin Migration은 기존 동작을 그대로 옮기고 이 위험을 알려진 이슈로 기록한다. 실행으로 확인하기 전이므로 **"취약점 확정"이 아니라 "정적 확인한 위험"**이다.

**구현 (2026-10-28, PR #83, DECISION-LOG §4-6):** 대타 승인(SUB-04)만 §5-5 "역할 먼저 검사"와 ML-5 순서로 바꿨다 — 역할 확인 → Lock 없는 근무·근무지 ID 조회 → Shift `FOR UPDATE` → Request `FOR UPDATE` → Lock 뒤 근무 동일성·상태·관리자 재확인. §5-5 "고칠 방향" 4단계의 수락자 소속 `FOR SHARE`는 퇴사 기능과 함께 묶음 C에서 추가한다. §5-5 표의 대타 종료·후보 응답·근무 상세·알림 읽음은 AS-IS다.

### 5-5. M3 선검사 — 현재 코드와 고칠 방향

**도달 가능성 (정적):** Substitute·Shift·Notification API는 Security에서 `anyRequest().authenticated()`만 적용된다. 역할 검사(`@PreAuthorize` 등)는 없다 (`SecurityConfig` L36-58). 그래서 **로그인한 사용자라면 누구나 아래 Service 코드까지 도달한다.**

| API | 지금 순서 (정적) | 권한 확인 전에 드러나는 것 | 권한 확인 전 Lock |
|---|---|---|---|
| 대타 승인 `PATCH /substitute-requests/{id}/approve` | Request `FOR UPDATE` → 상태 → `requireManager` | 존재 (404), **상태** (`SUB-002` 400 vs 403) | 있음 |
| 근무 상세 `GET /shifts/{id}` | 조회 → 공개 여부 (`SFT-012` 404) → 취소 여부 (`SFT-013` 404) → 담당 User 확인 (`SFT-014` 403) | 존재, **상태** (DRAFT·CANCELLED vs 403) | 없음 (읽기) |
| 후보 응답 `PATCH /substitute-candidates/{id}/response` | Request `FOR UPDATE` → 본인 Candidate (`SUB-020` 403) → 현재 직원 → 상태 | 존재 (404 vs 403) | 있음 |
| 대타 종료 `PATCH /substitute-requests/{id}/close` | Request `FOR UPDATE` → `requireManager` → 상태 | 존재 (404 vs 403) | 있음 |
| 알림 읽음 `PATCH /notifications/{id}/read` | Notification `FOR UPDATE` → 본인 확인 (`NOT-002` 403) | 존재 (404 vs 403) | 있음 |

ID가 IDENTITY(순번)라서 값을 바꿔 가며 시도하기 쉽다.

**고칠 방향 (설계 후보, 구현 전):**

1. **범위 정보만 먼저 읽는다.** Path에 근무지 ID가 없으므로, 그 Resource가 속한 근무지(또는 소유 소속)를 **Lock 없는 Scalar 조회**로 읽는다. 이 단계에서는 상태를 읽어 응답에 쓰지 않는다.
2. **권한 범위를 확인한다.** 실행자가 그 근무지의 현재 관리자(또는 본인 Resource)가 아니면 **404**로 끝낸다 ("없음"과 같은 응답, §1 대상 범위 원칙). 이때 Lock은 잡지 않았다.
3. **ML-5 순서로 Lock을 잡는다** (승인 후보: Shift → Request).
4. **Lock 이후 업무 상태를 다시 검증한다** (Request 상태, Shift 상태, 시작 전, 수락자 현재 소속 `FOR SHARE`).
5. 처리한다.

**범위 검사와 Lock 이후 재검증 (API마다 다름):** 공통 원칙은 같지만, Lock 이후 다시 확인할 권한 조건은 API마다 다르다. "Lock 전에 한 번 확인하면 충분하다"는 판단을 다섯 API에 똑같이 적용하지 않는다.

| API | 바뀌지 않는 관계 (Lock 전 확인으로 충분) | 바뀔 수 있는 권한 조건 (Lock 후 다시 확인) | 근거 |
|---|---|---|---|
| 대타 승인 | Request → Shift → Schedule → Workplace | 실행자 MANAGER의 현재 소속 (MANAGER는 퇴사 대상이 아니므로 지금은 바뀌지 않음, 역할 변경 기능이 생기면 다시 봄), 수락자의 현재 소속 (`FOR SHARE`, ML-5) | ML-H2 H2-9, ML-5 |
| 대타 종료 | 같음 | 실행자 MANAGER의 현재 소속 (같은 이유) | 같음 |
| 후보 응답 | Candidate → Request, Candidate → 소속 → User | **실행자(직원)의 현재 소속** — 직원은 퇴사할 수 있다. Lock 후 `FOR SHARE`로 다시 읽음 (ML-5 "대타 수락: Request → User → 후보 소속 `FOR SHARE` 재확인") | ML-5 |
| 근무 상세 | Shift → Schedule → Workplace | 담당 소속이 실행자의 **현재** 소속인지 (재가입자는 같은 User라도 다른 소속, CHG-04). 읽기 전용이라 Lock은 없고 한 번의 조회 안에서 판단 | ML-4 §8 ① |
| 알림 읽음 | Notification → 수신 소속 → User | **수신 소속의 현재 여부** — 수신자는 퇴사할 수 있다. 지금 코드는 Lock 후 `leftAt`을 확인함 (`NotificationService` L310-317) | AS-IS |

**같은 User ≠ 같은 소속:** 근무 상세·알림 읽음 등 현재 업무(M1) 검사는 User가 같은지만이 아니라 **그 Resource의 소속이 실행자의 현재 소속인지**를 본다. 과거 소속의 기록은 M2(내 소속 이력)로만 본다.

**응답 규칙 (볼 수 있는 범위 안에서만 상태 오류를 공개):**

| 상황 | 응답 |
|---|---|
| ID가 없음 / 다른 근무지 것 / 볼 권한이 없음 | **404** (셋을 구별하지 않음) |
| Path에 근무지 ID가 있고 실행자가 그 근무지의 현재 구성원인데 관리자가 아님 | 403 `WPL-004` (기존 `requireManager` 그대로, §1) |
| Path에 근무지 ID가 없는 API(대타 승인·종료)에서 관리자가 아님 | **404** (근무지 맥락을 응답으로 드러내지 않음). → **S3-A에서 "역할 먼저 검사"(W안)로 대체** (아래) |
| 볼 수 있는 Resource인데 업무 상태가 맞지 않음 (예: 본인 Candidate인데 이미 응답함) | 상태에 맞는 409 |
| 실행자 자신의 소속이 이미 종료됨 | 그 API의 403 정책 (예: 후보 응답 `SUB-021`) |


**기존 Test 기대값 영향 (S2-6A 정적 확인):** 위 규칙을 적용하면 범위 밖 접근을 확인하는 기존 Test 4개의 기대값이 403 → 404(없는 ID와 같은 code·message)로 바뀐다 (V4-T-26·39·58·68, [TEST-INVENTORY](../evidence/TEST-INVENTORY.md) §5-4). **역할만 부족한 같은 근무지 구성원(V4-T-74)은 S3에서 다시 정한다:** 그 요청과 관계없는 직원에게는 404가 다른 사람 요청의 존재·상태 노출을 막지만, 그 요청의 요청자·후보처럼 이미 볼 수 있는 실행자에게 404는 "볼 수 있는 Resource → 상태에 맞는 응답" 원칙과 어긋난다. Migration에서는 그대로 두고 CHG-06·07 승인 후 Improvement PR에서만 바꾼다. 알림 읽음의 403 → 404는 DECISION-LOG **G03-D-10 (`DEFER` / `PENDING`)과 같은 질문**이다. CHG-07 (가)가 G03-D-10을 대신 결정하지 않으며, 팀이 기준을 정하기 전에는 알림 읽음을 404 전환 대상으로 확정하지 않는다.

**역할 먼저 검사 — 대타 승인(SUB-04)·종료(SUB-08) (S3-A, 사용자 잠정 결정 2026-10-25, 팀 `PENDING`, [USER-PATH-REVIEW](USER-PATH-REVIEW.md) §9-1):** 관리자 전용 명령인데 Path에 근무지 ID가 없는 두 API에만 적용한다. **이 규칙과 위 M3 404 은닉은 DECISION-LOG G03-D-04 (다른 근무지·User 자원 접근 노출, `DEFER` / `PENDING`)의 답에 해당하므로 팀이 함께 검토·승인한다 (G03-D-04를 해결됨으로 표시하지 않음).**

| 순서 | 검사 | 실패 응답 |
|---|---|---|
| 1 | 실행자가 **어느 근무지에서도** 현재 MANAGER가 아니다 (Request를 조회하기 **전**) | 403 `WPL-004`. 존재하는 ID와 없는 ID에 같은 응답 → 요청 존재가 드러나지 않음 |
| 2 | Request를 잠그지 않고 조회해 그 근무지의 현재 MANAGER인지 확인 | 없는 ID·다른 근무지 요청 → 같은 404 `SUB-001` |
| 3 | Request `FOR UPDATE` → **그 근무지의 현재 MANAGER인지 다시 확인** + 상태·업무 규칙 재검증 (CHG-06, ML-5 Lock 순서) | 기존 명령 계약 |

- 1단계는 **빠른 거부 조건일 뿐 최종 인가가 아니다.** W1의 MANAGER이고 W2의 직원인 사용자의 W2 요청은 2단계에서 거부된다. 최종 인가는 2·3단계(그 근무지의 현재 MANAGER)다. 지금 기능에는 관리자 퇴사·역할 변경이 없어 Lock 사이에 관리자 권한이 바뀌지 않지만, 그런 기능이 생기면 3단계 재확인이 필수다 (S3-B).
- 요청자·후보·관계없는 직원·퇴사자 모두 1단계에서 403이다. 요청자·후보에게 "없는 요청"이라 답하지 않고, 관계없는 직원은 ID를 바꿔도 존재 여부를 알 수 없다.
- 남는 경우: 다른 근무지 MANAGER이면서 이 근무지 요청의 요청자·후보인 사용자 → 2단계 404 (드묾, 명령은 어차피 거부).
- 적용 범위 밖: 후보 응답(SUB-03, 본인 Candidate 확인)과 알림 읽음은 위 응답 규칙(범위 밖 = 404)을 그대로 따른다. 알림 읽음의 응답 code는 G03-D-10 결정 전까지 AS-IS다.
- 기존 Test 영향 (TEST-INVENTORY §5-4 갱신): V4-T-74는 code가 `WPL-004` 그대로이고 **검사 순서만** 바뀐다. V4-T-26·39는 실행자의 MANAGER 소속 유무에 따라 403 / 404로 나뉜다. Migration에서는 모두 AS-IS 유지.

**권한 확인 전 부작용 (정적 확인, `a148a62`):** 다섯 API 모두 **권한 확인 전에는 DB 변경과 이벤트 발행이 없다.** 대타 종료는 `requireManager` 이후에 `closeByManager`와 이벤트 발행 (`SubstituteRequestService` L372 → L412·L414), 알림 읽음은 본인 확인 이후에 `markAsRead` (`NotificationService` L313-321). 알림 목록(`NotificationRepository` L19)과 읽음은 같은 조건(수신 소속의 User = 나, 현재 소속)이라 "목록에는 없는데 ID로 읽음 처리"되는 경로가 없다. 권한 확인 전에 남는 것은 **존재 노출과 Row Lock 점유**다. Lock 점유의 실제 영향은 실행 전까지 `UNKNOWN`이다.

**검증 계약 (임시 ID, 전부 `SPECIFIED` / `NOT_RUN`, 번호는 S2-5):**

| 임시 ID | 내용 |
|---|---|
| M3-T1 | 다른 근무지 관리자·같은 근무지 직원·퇴사자가 존재하는 `requestId`(상태별: OPEN·ACCEPTED·CLOSED)와 없는 `requestId`로 승인 시도 → **응답 Status·code·message가 모두 같음** (404), DB 변경 0. **S3-A (W안) 갱신:** 어디서도 MANAGER가 아닌 실행자(같은 근무지 직원·퇴사자)는 존재·없는 ID 모두 **403 `WPL-004`로 같음** (Request 조회·Lock 0), 다른 근무지 MANAGER는 없는 ID와 같은 404 |
| M3-T2 | 권한 없는 승인 요청을 보내는 동안 실제 관리자의 같은 Request 승인 → 권한 없는 요청이 **Row Lock을 잡지 않음** (H6, `LockWaitProbe`로 대기 0) |
| M3-T3 | 범위 확인 후 Lock 전에 다른 관리자가 그 Request를 종료 → Lock 이후 재검증으로 승인 거부, `Shift.member` 불변 (TOCTOU, H6) |
| M3-T4 | 근무 상세: 다른 사람의 DRAFT·CANCELLED·공개 근무 ID → 같은 응답 |
| M3-T5 | 후보 응답·대타 종료·알림 읽음: 남의 ID와 없는 ID → 같은 응답, Lock 없음. **S3-A:** 대타 종료는 M3-T1과 같은 W안. 알림 읽음은 Lock 전 확인만 적용하고 응답 code는 G03-D-10 결정 전까지 AS-IS |
| M3-T6 | 회귀: 권한 있는 정상 경로의 기존 응답과 상태 전이 그대로 |

### 5-6. CHG-07 결정 (사용자 잠정 결정 2026-10-25: (가))

ID를 직접 받는 API 전체에 M3 선검사 원칙을 적용한다. 구현은 기능 묶음·위험도·Test 범위에 맞춰 나눌 수 있다 (한 PR에 모두 넣을 필요 없음).

| API | 등급 | 조건 |
|---|---|---|
| 대타 승인 | P0 (CHG-06) | 묶음 B' |
| 근무 상세 | P0 | 재가입 묶음 D 조건 (CHG-04와 같이) |
| 후보 응답 | P0 | 대타·퇴사 묶음 |
| 대타 종료, 알림 읽음 | **P0 검토 후보** | 정적 확인 결과(권한 확인 전 부작용 없음, 존재 노출 + Lock 점유만)와 S4 실행 결과(M3-T2·T5의 Lock 영향)를 보고 최종 등급을 정한다. Lock 영향이 의미 없게 나오면 P1, 관리자 처리 지연이 재현되면 P0 |

이것은 정적 검토 결과다. 보안 Test 완료나 취약점 확정으로 표현하지 않는다.

## 6. B1 결정 기록 (사용자 잠정 결정 2026-10-25, 팀 승인 전)

1. **대상이 없거나 다른 근무지 소속이면 404로 통일** (질문 1 (가)). 새 퇴사 API에서 시작해 같은 원칙을 새 공개 후 변경 API의 Path 관계·Body 대상에도 적용했다 (§1). 기존 API의 400/404는 Migration에서 그대로 둔다. G03-D-04(노출 정책 전체)는 팀 `PENDING`이며 이 기록은 새 API 범위의 사용자 잠정 결정이다.
2. **대타가 있는 근무도 상태별 처리를 보장하면 취소·담당자 변경 가능** (질문 2 (가), §4).

**구현 메모 (정적 확인):** 지금 불가능 시간 Repository는 겹침 **여부**만 돌려준다 (`existsOverlappingUnavailableTime`). G03-D-05의 겹침 구간 목록을 만들려면 구간을 돌려주는 **내부 조회가 새로 필요하다 (아직 없음, 구현 전)**. 이 내부 조회 결과(불가능 시간 Row)는 서버 안에서 교집합·병합·정규화에만 쓰고, API 응답에는 G03-D-05 §5-2의 겹침 구간만 내보낸다.

**후속 확인 항목 (B1에서 결정하지 않음):**
- 공개 후 근무 **추가**에도 생성 이력을 남길지 (ML-2-INV-04는 담당자 "변경"만 다룸). 기존 Shift의 생성 시각·생성자 기록으로 충분한지 먼저 확인 → ML-2 구현 계약, S3.
- ~~같은 근무지의 인계 대상이 이미 퇴사한 경우의 409 종류~~ → **409 STALE로 사용자 잠정 결정 (2026-10-25, [FRONTEND-CONTRACT](FRONTEND-CONTRACT.md) §9-1).** STALE = "제출한 계획이 지금 상태와 맞지 않아 다시 조회해야 한다". 퇴사 대상 자체가 이미 종료된 경우는 STATE_CONFLICT, 없음·다른 근무지는 404.
- 기존 400(`SFT-002`·`SFT-003`·`SFT-005`)은 Kotlin Migration에서 보존하는 **기존 계약**이고, 404·409는 V3 Improvement의 **신규 제안**이다. STATE_CONFLICT 등은 분류이며 실제 code 문자열은 S2-4에서 정한다. 새 공개 후 변경 Command는 Endpoint가 따로지만 시간 규칙·겹침·현재 소속 검사는 기존 검사 로직을 재사용할 수 있다 (ML-2 §8 "공통 검증으로 분리 필요"). 재사용 방식은 구현 설계에서 정한다.

## 7. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-25 | 초안 (S2-3B B1). 공통 규약, 기존 Endpoint 37개, 새 API 5개 (퇴사 미리보기·확정, 공개 후 추가·취소·담당자 변경), 카드 표현 차이 1건, B2 목록, 질문 2개 |
| 2026-10-25 | B1 보완: 질문 1·2 사용자 잠정 결정 반영. §1에 검사 순서·대상 범위 404·실행자 권한·시간 경계 규칙 추가, API별 404 조건, §4를 상태별 처리 표로 교체, §6 결정 기록과 구현 메모 |
| 2026-10-25 | B2 초안: 권한 모델 M1·M2·M3, 새 API 5개 (초대 코드 재발급, 빠진 근무, 내 소속 이력, 종료된 대타, 서버 로그아웃), 기존 API 동작 변경 6개 (재가입, SUB-05, SUB-07, 근무 상세, T1 code, 승인 권한 검사 순서), SUB-05·07 네 가지 구분, 질문 1개 |
| 2026-10-25 | B2 보완: CHG-06을 대타 P0 묶음으로 사용자 잠정 결정. M3를 추가 검사로 정의. Security 도달 가능성 확인, 같은 패턴의 ID 직접 지정 API 4개 발견(CHG-07), 고칠 방향 5단계와 TOCTOU 판단, Test M3-T1~T6, CHG별 등급 표 |
| 2026-10-25 | CHG-07 (가) 사용자 잠정 결정. API별 Lock 이후 재검증 조건 표(직원 소속은 바뀔 수 있음), 같은 User ≠ 같은 소속, 404/403/409 응답 규칙, 권한 확인 전 부작용 정적 확인(없음), 대타 종료·알림 읽음은 P0 검토 후보. 이전 TOCTOU 문단("범위 판단은 뒤집히지 않는다")은 대타 승인·종료에만 맞아 API별 표로 교체 |
| 2026-10-25 | S2-5B: API-LV-02 `closedRequests`에 총수와 세는 규칙 연결 (ML-4 L113의 "총수와 사유별 수"와 맞춤, PS-02와 같은 규칙) |
| 2026-10-25 | S2-6A: M3 응답 규칙의 기존 Test 기대값 영향과 G03-D-10 연결 명시 (결정 아님) |
| 2026-10-25 | S3-A: 대타 승인·종료의 "역할 먼저 검사"(W안), M3-T1·T5 기대값 갱신, 새 API-SB-02 (관리자 끝난 대타 요청, P0 후보) — 사용자 잠정 결정, 팀 `PENDING` |
| 2026-10-25 | S3-B: W안 3단계에 Lock 후 권한 재확인과 1단계가 최종 인가가 아님을 명시, API-SB-02에 경계(`startAt == now`)·`Clock`·DB 식·ID 비노출·만료 비저장·`/api/v1`·ID 혼동 주의 추가 (정적 확인 근거 포함) |
| 2026-10-25 | S3 Commit 전: W안·M3와 G03-D-04 연결 명시, API-SB-02 DB 식의 적용 전제(대상 필터 후, APPROVED 제외), `hasNewerActiveRequest`의 진행 중 정의, 이름 스냅샷 사실 |
| 2026-10-28 | API-AU-01 구현 행 추가 (경로·응답 확정, 출처 검사와 CORS 거부 관계), Test ID V1-N-13 추가 |
| 2026-10-28 | API-SB-01 구현 행 추가 (경로 확정, 정렬·필드 이름·7일 경계) |
| 2026-10-29 | API-SC-01 구현 행 추가 (응답 필드, 정렬·7일 경계·마지막 이력 Row 정의) |
| 2026-10-28 | CHG-06 대타 승인 구현 표시 (§2 표, §5-4 구현 문단, PR #83). 대타 종료 등 다른 API는 그대로 |
| 2026-10-29 | PR #90 병합 코드 기준 API-LV-01 구현·응답·서명 문자열/15분 유효기간 동기화. API-LV-02 후속 검증 및 Test Inventory 연결 구분 |
| 2026-10-29 | CHG-02 SUB-05 현재 소속 구현, CHG-03은 PR #82 구현에 Test 연결 (V4-N-36·37, EV-042) |
| 2026-10-29 | API-SB-02 구현 행 추가 (경로 확정, 기간·페이지 파라미터, 응답 필드, 사실 Field 정의, 없는 근무지 403). 팀 승인은 PR 리뷰로 요청 중 (EV-044) |
| 2026-10-29 | API-SB-02 PR #104 리뷰 반영: 수락자 여러 명 → `SUB-016`, 없는 근무지 403은 AS-IS로 확인 (EV-044) |
| 2026-10-29 | API-LV-02 POST 활성화·구현 행, 알림 행에 다른 현재 관리자 반영 |
