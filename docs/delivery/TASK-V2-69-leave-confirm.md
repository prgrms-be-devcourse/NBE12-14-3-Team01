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
