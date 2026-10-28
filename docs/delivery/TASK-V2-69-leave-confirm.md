# Issue #69 — 퇴사 확정 V2 구현 기록

- Task: API-LV-02 / Issue #69, 사용자 직접 구현 지시 (2026-10-29).
- 기준 SHA: `9b35b180f91d9e1a77a8a5e6f4c91348bebf3c51`. GitHub develop 및 로컬 origin/develop `cade4495635e4dc855cc769b4a4c658438ab6814` 포함, 추가 병합 없음.
- 보존: 기존 미리보기 API·응답, Stash 0·1, 기존 테스트 기대값, Gradle·application 설정, DB 스키마·기존 상태 Enum.
- 변경 범위: V2 확정 Controller/Service/Lock 부품, 기존 원본 비교 연결, V3의 기존 검증·배정 이력 로직을 재사용하는 퇴사 일괄 처리, 관련 신규 통합 테스트.
- 계약 근거: API-CATALOG §3-2, ERROR-CODES §4 WPL-009~011, ML-2~5, G03-D-05, DECISION-LOG §4-2·4-4. 새 정책 승인 기록이 아니다.
- Stash 확인: `git stash show -p 'stash@{0}'`, `git stash show -p 'stash@{1}'`. Context 변경은 동일하고 현재 HEAD에도 동일하게 존재한다. untracked WIP 경로도 현재 파일을 재사용하며 apply/pop/drop하지 않았다.

## 구현 경계

`LeaveConfirmService.confirm`은 단일 READ_COMMITTED Transaction 안에서 권한 확인 → 서명 원본 검증 → Workplace(재발급 시) → Schedule → 본인 Shift → 관련 전체 Request → 인계 대상 User → 관련 소속 순서로 잠근다. 같은 종류는 ID 오름차순이며 소속은 대상 FOR UPDATE, 나머지 FOR SHARE다. 잠금 뒤 refresh 및 최신 조회로 P1~P5와 잠금 범위를 확인한다. 범위 밖 새 Row는 추가 잠금하지 않고 STALE로 끝낸다.

V3는 기존 공식 충돌·불가능 시간 구간 병합/포함 비교를 재사용한다. 전체 공식 충돌 검사를 경고보다 먼저 수행한다. 검증 뒤 MEMBER_LEFT 배정 이력·취소, 기존 V4 SHIFT_CHANGED 종료, DRAFT 삭제, 소속 종료·선택적 코드 교체를 같은 Transaction에 반영한다. Pattern은 보존한다.

## 미완료 / POST 비활성 조건

- PR #92는 확인 당시 OPEN (`947e0b8`). 코드·Enum·타입을 복사하지 않았다. `LeaveConfirmationIntegration` 구현이 없으므로 Controller Bean과 실제 POST는 등록되지 않는다. 직접 Service 호출에도 후보 영향이 있으면 쓰기 전에 연동 부재로 중단한다.
- #92 병합 후 V4 실제 종료 Request ID·INELIGIBLE 수를 연결해야 한다. 검증한 영향과 반환 결과가 다르면 전체 Rollback한다. 새 Request를 순서 밖으로 잠그는 어댑터는 허용하지 않는다.
- DRAFT 편집·Pattern 복사 생성·Publish 및 대타 생성/수락/승인의 소속 Guard, #93 등 새 배정 경로와의 경합을 검증하기 전 Bridge Bean을 등록하지 않는다.
- 알림 및 대체 화면 V5 연동은 미완료다.
- 계약 확인 필요: API-LV-02는 공식 충돌을 FORBIDDEN_CONFLICT 409로 제시하지만 재사용 대상 V3 `SFT-009`/`SFT-010`은 현재 400이다. 기존 ErrorCode를 변경하거나 미예약 새 번호를 만들지 않았다. 공개 API 활성화 전 Owner가 적용 계약을 확정해야 한다.
- Context 검증 실패는 현재 새 미리보기를 요구하는 WPL-010으로 처리한다. 잘못된 형식·서명과 만료의 400/409 세분화는 G03-D-05 §5-4에 남은 기술 계약 확인 항목이며 POST 활성화 전 확정한다.
- 활성 요청 중복 등 비정상 데이터의 새로운 정리 정책은 정하지 않았다.

## 검증

관련 단위 테스트 → V2 실제 MySQL 통합·잠금 대기·Rollback 테스트 → 관련 도메인 회귀 → 최종 clean check 순서로 실행한다. 실제 실행 결과와 개별 Test XML 집계는 이 작업의 최종 검증 기록을 따른다. 통과 근거 없이 API 전체 완료·동시성 보호 완료로 표시하지 않는다.

### 실제 실행 결과 (2026-10-29)

- SHA / Branch: `9b35b180f91d9e1a77a8a5e6f4c91348bebf3c51` + 이 Task 변경 / `feature/v2-membership-leave-confirm` (실행 후 동일 코드로 기능 단위 Commit).
- 환경: Windows, Temurin JDK 25.0.1, Gradle 9.3.0, test Profile의 Testcontainers MySQL 9.7.2, READ_COMMITTED. 운영 DB 사용 없음.
- 선행 실행: Context 검증·비교·계획·DTO 단위 테스트 및 ShiftServiceTest 성공. 이후 신규 확정·기존 Writer 통합 테스트, LeavePreview 전체·ShiftService/Controller·SubstituteShiftChangeClose 회귀 성공.
- 최종 명령 (backend): `.\gradlew.bat clean check --console=plain`.
- 최종 결과: **92 Suite / 665 실행 / 665 PASS / FAIL 0 / ERROR 0 / SKIP 0**, BUILD SUCCESSFUL (3m 8s).
- 개별 근거: `backend/build/test-results/test/*.xml` 전체 집계 (`__TEST-…xml` 포함), `backend/build/reports/tests/test/index.html`.
- 신규 Class: `LeaveConfirmServiceIntegrationTest` **13/13 PASS**. 실제 Commit 후 재조회로 MEMBER_LEFT 인계·취소 이력, SHIFT_CHANGED 종료, DRAFT 삭제·Pattern 보존·소속 종료·초대 코드 교체를 확인했다. 경고 시 변경 없음, 공식 충돌 확인 우회 불가, STALE, 권한, 종료된 인계 대상, 후반 실패 전체 Rollback, 같은 소속 동시 확정 성공 1건을 확인했다.
- Lock 대기 실험: 별도 Transaction이 Schedule/User를 보유한 상태에서 미래 요청의 Timeout과 MySQL `SHOW FULL PROCESSLIST`의 해당 `FOR UPDATE` SQL을 확인했다. 보유 Transaction Commit 후 새 DRAFT는 STALE, 새 불가능 시간은 WPL-011로 거부하며 이번 요청 변경은 0이다.
- 최초 신규 통합 실행은 13건 중 4건 실패했다. 두 건은 performance_schema 조회 권한, 한 건은 MANDATORY Proxy에 대한 Spy 설정 실패, 한 건은 이 Spy 설정의 영향이었다. 본인이 작성한 Fixture의 관측 방식을 기존 프로젝트 방식으로 맞추고 AOP Target Spy를 사용해 수정했다. 검증 항목·기존 기대값을 낮추지 않았으며 위 최종 전체 실행에서 모두 통과했다.
- 한계: 위 결과는 PR #92·소속 Guard·V5의 미구현 연동까지 검증했다는 뜻이 아니다. Controller 미등록 및 V4 부재 시 변경 전 중단도 개별 테스트로 확인했다. 기존 코드의 Kotlin 경고는 남아 있다.

## 변경 파일

- V2 신규: `backend/src/main/kotlin/com/merge/backend/domain/workplace/controller/LeaveConfirmController.kt`, `service/LeaveConfirmService.kt`, `service/LeaveConfirmationLocks.kt`, `service/LeaveConfirmationIntegration.kt` (service 경로의 기준은 같은 workplace 디렉터리).
- V2 기존 연결: `backend/src/main/kotlin/com/merge/backend/domain/workplace/service/LeavePlanPreparation.kt`, `service/LeavePreviewService.kt`, `exception/WorkplaceErrorCode.kt`.
- V3: `backend/src/main/kotlin/com/merge/backend/domain/shift/service/MemberLeaveShiftPlan.kt`, `ShiftService.kt`.
- 신규 테스트: `backend/src/test/kotlin/com/merge/backend/domain/workplace/service/LeaveConfirmServiceIntegrationTest.kt`.
- 문서: 이 Task Card 및 `docs/decisions/DECISION-LOG.md` DL-016 (정책 미확정 기록).
- 기존 요청·응답 DTO, 서명 검증, 비교, 소속 Writer 및 기존 테스트는 재사용한다. 기존 테스트의 기대값·비활성 여부는 변경하지 않았다.

## 후속 통합 — 2026-10-29

이전 절은 `53b5f3b` 구현 당시 기록이다. 아래가 이번 후속 작업 상태이며 이전 테스트 실행 사실은 유지한다.

- 기준: `53b5f3b`. GitHub 및 fetch 후 origin/develop `19066d3509d6f1334941ea568e0778b25354e1be` 확인, 차이 3 ahead / 15 behind. 사용자에게 먼저 보고하고 **직접 병합 승인**을 받은 뒤 `git merge --no-commit origin/develop` 실행.
- #92·#93·#96 MERGED 확인. ShiftService import 충돌은 V2 일괄 처리와 #93 공개 근무 추가를 모두 보존해 해결했다. 새로 병합된 V3 확인 구간 검증도 유지했다. `switch-v2-review.zip` 사용자 파일은 수정·추가 커밋하지 않는다.
- **V4 실제 연동 완료:** `LeaveCandidacyAdapter`가 `SubstituteCandidateService.closeCandidaciesByMemberLeave`의 기존 상태 전이를 호출한다. PENDING→INELIGIBLE, 수락 기록 보존, CANDIDATE_INELIGIBLE 종료 및 실제 건수를 V2의 원자적 처리에 연결했다.
- **Request 순서·범위:** #92 기존 2인자 함수는 활성 요청을 ID 순서 FOR UPDATE로 읽으므로 User/소속 Lock 뒤 그대로 호출하면 새 Row를 뒤늦게 잠글 수 있다. V2용 3인자 함수는 같은 활성 predicate를 재사용하고 이미 잠근 ID 범위만 읽으며 **새 FOR UPDATE를 발행하지 않는다**. 기존 2인자 함수와 상태 전이 구현은 보존했다.
- Adapter는 V4의 실제 활성 ID를 Scalar로 먼저 비교한다. V2 범위를 벗어나면 WPL-010, 실제 소유한 Request 쓰기 Lock이 없으면 정리 전에 중단한다. 처리 결과와 V2 예상 효과가 다르면 기존 전체 Rollback 검증도 유지한다.
- 원래 665개 테스트를 삭제·약화·비활성화하지 않았다. 기존 'V4 연동 부재' 테스트는 연동 Bean이 생긴 현재에도 동일한 부재 상황을 만들도록 빈 ObjectProvider를 테스트 구간에만 주입하고 원복한다. 기존 실패·변경 없음 Assertion은 유지했다.

### POST는 계속 비활성

실제 V4 Adapter 등록과 POST 활성화 조건을 분리했다. `LeaveConfirmationReadiness` 구현 Bean은 제공하지 않는다. Adapter가 등록돼도 Controller Bean/POST가 생기지 않는 것을 테스트한다. 제출 우선순위에 따라 다음 광범위한 Guard 보완은 별도 작업으로 남긴다.

- DRAFT 생성/Pattern 복사: Workplace Lock은 있으나 여러 대상 소속 Guard와 Lock 후 현재 소속 재검사가 부족하다.
- DRAFT 생성·수정: Schedule Lock 뒤 담당 소속의 FOR SHARE 재확인이 부족하다.
- Publish: Schedule·User Lock 및 현재 소속 검사는 있으나 마지막 소속 Guard가 부족하다.
- 대타 생성: Shift Lock 뒤 요청자·후보 소속 Guard가 부족하다.
- 대타 수락·승인: Request/Shift·User 잠금은 있으나 대상 소속 Guard와 최신 재확인이 부족하다.
- #93 공개 근무 추가와 기존 공개 근무 재배정은 Schedule→User(재배정은 Shift/Request 포함)→소속 FOR SHARE를 사용한다. 이 경로를 포함해 퇴사와의 전체 경합 검증은 아직 끝나지 않았다.
- DL-016의 기존 400/409 및 Context 실패 세분화는 결정하지 않았고 기존 ErrorCode/HTTP를 유지했다. V5 알림 연동도 별도다.

### 후속 검증 범위

실제 V4의 대기 후보·수락자·다른 응답 가능 후보 처리, 사유별 건수, V4 처리 후 실패의 전체 Rollback, 범위 밖 Request를 잠그지 않는 거부, 실제 쓰기 Lock 필수, Request Lock 대기 중 수락 Commit 후 STALE, Adapter만으로 POST가 활성화되지 않는 조건을 추가 검증한다. 관련 범위를 먼저 실행한 뒤 변경된 전체 코드로 clean check를 실행한다.

### 후속 실제 실행 결과

- SHA / Branch: `53b5f3b` + 승인된 `origin/develop 19066d3` 병합 + 이번 후속 변경 / `feature/v2-membership-leave-confirm`. 검증 후 동일 코드로 Commit한다.
- 환경: Windows / Temurin JDK 25.0.1 / Gradle 9.3.0 / test Profile Testcontainers MySQL 9.7.2 / READ_COMMITTED. 운영 DB 접근 없음.
- 선행 명령: `.\gradlew.bat test --tests '*LeaveConfirmServiceIntegrationTest' --tests '*SubstituteMemberLeaveTest' --tests '*SubstituteShiftChangeIntegrationTest' --tests '*ShiftServiceTest' --console=plain` — BUILD SUCCESSFUL.
- 최종 명령 (backend): `.\gradlew.bat clean check --console=plain` — **BUILD SUCCESSFUL (3m 27s)**.
- 최종 XML 집계: **95 Suite / 735 실행 / 735 PASS / FAIL 0 / ERROR 0 / SKIP 0**. `backend/build/test-results/test/*.xml` (`__TEST-…xml` 포함), HTML `backend/build/reports/tests/test/index.html`.
- 개별 결과: `LeaveConfirmServiceIntegrationTest` **21/21**, `SubstituteMemberLeaveTest` **19/19**, `SubstituteShiftChangeIntegrationTest` **10/10**. 기존 13개 V2 확정 시나리오에 새 통합 8개를 추가했고, 기존 실패/변경 없음 검증도 보존했다.
- Request 범위 밖 Row가 다른 Transaction의 쓰기 Lock에 잡혀 있어도 Adapter는 기다리지 않고 WPL-010으로 끝나는 것을 확인했다. Request Lock 대기 중 다른 Transaction이 수락한 경우 STALE이고, 상대 Commit은 보존하면서 이번 퇴사 쓰기는 0이다.
- 실제 V4 정리 후 마지막 V2 단계 실패 시 후보 INELIGIBLE·Request 종료·근무/이력/DRAFT 변경까지 모두 Rollback되는 것을 확인했다. V4의 실제 결과와 V2의 예상 효과 집계도 일치했다.
- POST 활성화/MVC 성공 경로는 검증 완료로 표시하지 않는다. 이번 범위는 실제 V4 연동과 원자성·기존 Row 경합 검증이며, 위 Guard/HTTP 계약/V5 미완료 항목은 그대로 남는다.

## V2 요청 검증·HTTP 후속 (2026-10-29)

- 기준 `9c62d53` + 이번 V2 변경. 중단 당시 수정된 네 파일을 보존하고 기존 테스트를 삭제·약화·비활성화하지 않았다.
- `REASSIGN`의 인계 대상 누락과 결정·확인 항목·확인 구간의 null 원소를 기존 Bean Validation / `COM-001`로 거부한다. Controller를 거치지 않는 Service 호출도 현재 관리자·대상 검증 후, 업무 Lock 획득 전에 동일한 필드 오류를 반환한다. 미확정 `CANCEL` 추가 필드 및 400/409 정책은 정하지 않았다.
- `LeaveConfirmControllerTest`: 독립 MockMvc **15/15 PASS**. 응답 요약·입력 오류·기존 오류 Envelope·WPL-009/WPL-011 충돌 데이터 확인.
- `LeaveConfirmServiceIntegrationTest`: **24/24 PASS**. 기존 21개를 보존하고 테스트 전용 MockMvc → 실제 V2·V3·V4 MySQL Transaction 성공, WPL-011 발생 시 DB 변경 0, 다른 Transaction이 Schedule Lock을 보유해도 잘못된 인계 입력이 Lock을 기다리기 전에 COM-001로 거부되는 경우를 추가했다.
- MockMvc는 테스트에서 Controller 객체와 Rq를 직접 구성한다. **Readiness Bean이나 실제 운영 POST를 등록하지 않으며 Security Filter 전체 검증을 대체하지 않는다.** 기존 Spring Context의 Controller 미등록 검증도 유지했다.
- 환경: Windows / Temurin JDK 25.0.1 / Gradle 9.3.0 / test Profile Testcontainers MySQL 9.7.2 / READ_COMMITTED. 운영 DB 접근 없음.
- 최종 명령 (backend): `.\gradlew.bat clean check --console=plain` — **BUILD SUCCESSFUL (3m 24s)**. JUnit XML 전체 집계 **96 Suite / 753 실행 / 753 PASS / FAIL 0 / ERROR 0 / SKIP 0**. XML `backend/build/test-results/test/*.xml` (`__TEST-…xml` 포함), HTML `backend/build/reports/tests/test/index.html`.
- POST 활성화와 전체 Lock·동시성 검증은 미완료다. V3의 `ScheduleService.createDraftSchedule` 및 `ShiftService.create`·`modify`에서 새 DRAFT 소속 Guard가 필요하고, V4의 `SubstituteRequestService.create` / `SubstituteCandidateService.findCandidates`·`createCandidates`에서 새 후보 소속 Guard가 필요하다. 생성/수정 직전 소속 FOR SHARE 후 최신 `leftAt`을 재확인해야 한다.
- `ScheduleService.publishSchedule`, `SubstituteCandidateService.acceptCandidate`, `SubstituteRequestService.approve`도 User Lock 이후 소속 FOR SHARE/최신 재확인이 없으므로 담당자 검토가 필요하다. 이미 포착된 대상은 공통 Schedule 또는 Request Lock으로 직렬화되는 경로가 있어, 추가 Lock의 독립적 필요성과 전체 순서는 실제 경합 테스트로 확인한다. 공개 후 추가·재배정에는 이미 소속 FOR SHARE가 있으나 퇴사와의 통합 경합 검증은 별도다.
- DL-016의 공식 근무 충돌 400/409 및 유효하지 않은 Context의 오류 분기는 여전히 PENDING이다. V5 알림·Frontend는 이번 범위 밖이다.
