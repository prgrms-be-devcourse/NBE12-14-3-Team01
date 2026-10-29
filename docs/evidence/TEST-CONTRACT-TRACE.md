# 테스트 계약 추적표 (Contract Test Trace)

| 축 | 상태 |
|---|---|
| 목적 | 정책·계약 문서에 정의된 테스트 ID 중 **TEST-INVENTORY·카드에 실행 기록이 연결되지 않은 ID**를 실제 Test와 이어 주고, 실제로 Test가 없는 ID를 구분한다 |
| 기준 | develop `fb6ba66` (#104·#105 포함) + 당시 열린 PR #106(이후 `247226d`로 병합)의 문서·Test 소스 (2026-10-29) |
| 방법 | ① `docs/decisions/proposals/*.md`·`docs/contract/*.md` 표 첫 칸의 ID 168개 추출 → ② TEST-INVENTORY·카드의 `LOCAL_PASSED`/실행 표기 또는 Test 코드의 ID 언급이 있으면 "연결됨" (78개) → ③ 나머지 90개를 계약 문장과 Test 이름·메서드로 **정적 대조** |
| 한계 | ③은 Test 이름과 계약 문장의 대조다. 각 Test 본문의 기대값이 계약 문장과 완전히 같은지는 ID별로 검토하지 않았다. 실행 결과는 각 PR의 EV 기록과 아래 "이 PR로 추가"를 따른다 |
| 관계 | TEST-INVENTORY를 대체하지 않는다. 당시 열린 PR과 같은 자리에 줄을 추가하지 않도록 별도 파일로 둔다. 다음 TEST-INVENTORY 정리 때 이 표의 연결을 옮긴다 |

판정: **연결** = 기존 Test가 계약 장면을 다룸 (ID 연결 기록만 없었음) / **이 PR** = 이 PR에서 Test 추가 / **없음(후)** = Test 없음, 제출 후 / **범위 밖** = 기능 없음·대체된 계약

## 1. V2 소속·퇴사·재가입

| ID | 계약 요약 | 판정 | 근거 Test (Class.method) |
|---|---|---|---|
| V2-N-01·02·04·05 | ML-H1 원안 (퇴사 Command 전 정의) | 범위 밖 | ML-4 V2-N-24~29와 ML-H2 V2-N-06으로 대체됨 |
| V2-N-03 | 종료 Row → 관리자·목록에서 제외 | 연결 | `WorkplaceServiceIntegrationTest.rejectRetiredManagerAccess`, `MembershipRejoinCompatibilityIntegrationTest` 전체 |
| V2-N-06 | 퇴사 후 재가입 → 새 Row, 이전 Row 유지 | 연결 | `MembershipRejoinIntegrationTest.rejoinCreatesNewMembershipAndPreservesEndedRowAndPatternReference` |
| V2-N-07 | 동시 가입 → 현재 소속 1, 나머지 WPL-002 | 연결 | `MembershipRejoinConcurrencyIntegrationTest.concurrentRejoinsCommitOneNewMembershipAndRejectNineDuplicates` |
| V2-N-08 | 현재 소속자 재가입 → WPL-002 | 연결 | `MembershipRejoinIntegrationTest.currentMembershipJoinReturnsConflictWithoutCreatingAnotherRow`, `WorkplaceServiceIntegrationTest.rejectDuplicateJoin` |
| V2-N-09 | 현재 Row 2개 직접 저장 → Unique 위반 | 연결 | `MembershipRejoinIntegrationTest.schemaAllowsMultipleEndedMembershipsButRejectsAnotherCurrentMembership` |
| V2-N-10 | 다른 제약 위반은 WPL-002로 바뀌지 않음 | 없음(후) | `joinWorkplace`의 제약 이름 분기를 직접 고정한 Test 없음 |
| V2-N-11 | 초대 코드 재발급 → 이전 코드 WPL-001 | 연결 | `MembershipRejoinIntegrationTest.regeneratedInviteCodeRejectsOldCodeAndAcceptsNewCodeThroughHttp` |
| V2-N-12 | 가입 × 재발급 동시 | 연결 | `MembershipRejoinConcurrencyIntegrationTest.regenerationCommitsBeforeWaitingJoinSoOldCodeIsRejectedAndNewCodeWorks` |
| V2-N-13·14 | 재가입 후 Pattern·새 근무표는 새 소속만 | 연결 | `MembershipRejoinCompatibilityIntegrationTest.regularPatternsAndDraftGenerationUseOnlyRejoinedMembership` |
| V2-N-15 | 재가입 후 이전 기록 `member_id` 불변 | 연결 | `MembershipRejoinIntegrationTest.rejoinCreatesNewMembershipAndPreservesEndedRowAndPatternReference` |
| V2-N-16 | 재가입 후 이전 소속 알림 미표시 (= NT-T3) | 없음(후) | 알림 조회 조건(현재 소속)으로 성립하나 재가입 장면으로 고정한 Test 없음 |
| V2-N-17 | (U3일 때만) CHECK 위반 | 범위 밖 | U3 미채택 (U2' `active_marker` 사용) |
| V2-N-18·25 | MANAGER 퇴사 대상 → 거부 | 연결 | `LeavePreviewServiceIntegrationTest.rejectManagerTarget` (조회), `LeaveConfirmServiceIntegrationTest` 권한·대상 Test |
| V2-N-19 | 가입 → 그 근무지 현재 관리자에게만 1건 | 연결 + 이 PR | `ShiftChangeNotificationIntegrationTest.joinAndRejoinNotifyCurrentManagers`(#100) + **`JoinAndExpiryNotificationIntegrationTest.joinNotifiesOnlyManagersOfThatWorkplace`** (다른 근무지 관리자 0건) |
| V2-N-20 | 재가입 → 재가입 문구 1건 | 연결 | `ShiftChangeNotificationIntegrationTest.joinAndRejoinNotifyCurrentManagers` (#100) |
| V2-N-21 | 가입 Rollback → 알림 0건 | **이 PR** | `JoinAndExpiryNotificationIntegrationTest.rejectedJoinCreatesNoNotification`, `.rolledBackJoinCreatesNoNotification` |
| V2-N-22 | 알림 실패 주입 → 가입 성공 | **이 PR** | `ShiftChangeNotificationFailureIntegrationTest.joinNotificationFailureDoesNotChangeJoin` |
| V2-N-24 | EMPLOYEE·다른 근무지 관리자의 퇴사 요청 → 403 | 연결 | `LeavePreviewServiceIntegrationTest.rejectEmployeeActor`·`rejectOutsiderBeforeLookingUpTarget`, `LeaveConfirmServiceIntegrationTest.permissionPrecedesTargetLookupAndEndedHandoverTargetIsStale` |
| V2-N-26 | 이미 종료 → 409 | 연결 | `LeavePreviewServiceIntegrationTest.rejectEndedTarget`, `LeaveConfirmServiceIntegrationTest.permitsDraftReductionAndRejectsRepeatedCompletion` |
| V2-N-27 | 진행 중 근무 → 409 + 종료 시각 | 연결 | `LeaveConfirmServiceIntegrationTest.ongoingWorkAndExpiredContextRefuseWithoutWrites` |
| V2-N-28 | 미리보기 후 근무 추가 → 확정 409 | 연결 | `LeaveConfirmServiceIntegrationTest.publishedTimeChangesProduceStaleAndNoWrites`, `DraftMembershipGuardConcurrencyTest.leaveWaitsForDraftAddAndBecomesStale` (#106) |
| V2-N-29 | 응답 요약 수 = 실제 변경 수 | 연결 | `LeaveConfirmServiceIntegrationTest.commitsReassignmentCancellationDraftDeletionPatternPreservationAndInviteRotationTogether`, `actualV4ClosesSolePendingCandidateAndCountsBothReasons` |
| V2-N-30 | 퇴사 후 근무지·관리·내 근무·알림 거부 또는 미표시 | 없음(후) | 각 조회의 현재 소속 조건으로 성립하나 "퇴사 확정 → 각 API" 장면 Test 없음 (POST 비활성) |
| V2-N-31 | 퇴사 후 이전 근무 상세 거부 | 연결 | `MembershipRejoinCompatibilityIntegrationTest.endedMembershipShiftIsExcludedFromBothListAndDetailAfterRejoin` |
| V2-N-32·33 | 퇴사·재가입 후 보낸 대타 목록 | 연결 | `SubstituteCurrentMembershipListTest.sentListShowsOnlyCurrentMembershipRequests` (#101) |
| V2-N-34 | 과거 소속 이력 = 본인 종료 소속만 | 연결 | `MembershipRejoinIntegrationTest.endedMembershipHttpHistoryContainsOnlyActorHistoryAndExcludesCurrentMembership` |
| V2-N-35 | 취소 근무: 상세 거부, 빠진 근무에 표시 | 연결 | `ShiftServiceTest` "근무 상세 조회 실패 - 취소된 근무인 경우", `RemovedShiftQueryServiceTest.cancelledShiftIsListed` (#95) |
| V2-N-36·37·38·40 | 빠진 근무 (인계 표시, 재가입 격리, A→B→A 제외, 8일 전 제외) | 연결 | `RemovedShiftQueryServiceTest.reassignedShiftIsListedForPreviousMemberOnly`·`previousMembershipIsExcludedAfterRejoin`·`shiftReturnedToMeIsExcluded`·`sevenDayBoundaryIsInclusive` (#95) |
| V2-N-53 | UP-06 퇴사 전체 흐름 | 없음(후) | 퇴사 확정 POST 활성화 후 사용자 경로 Test |

## 2. V3 근무·공개 후 변경 (ML-2)

| ID | 계약 요약 | 판정 | 근거 Test |
|---|---|---|---|
| V3-N-01 | 퇴사: 1건 인계·1건 취소(`MEMBER_LEFT`)·이력·`leftAt` | 연결 | `LeaveConfirmServiceIntegrationTest.commitsReassignmentCancellationDraftDeletionPatternPreservationAndInviteRotationTogether` |
| V3-N-02 | 미래 근무 일부만 선택 → 거부 | 연결 | `LeaveConfirmServiceIntegrationTest.duplicateAndIncompleteDecisionsAreStaleWithoutWrites` |
| V3-N-03 | 미리보기 후 근무 추가 → 409 | 연결 | V2-N-28과 같음 |
| V3-N-04 | 인계 대상이 퇴사자·다른 근무지·겹치는 근무 → 거부 | 연결 | `LeaveConfirmServiceIntegrationTest.permissionPrecedesTargetLookupAndEndedHandoverTargetIsStale`, `reusesExistingV3OfficialConflictErrorAndCannotBypassWithAcknowledgements` |
| V3-N-05 | 대타 승인으로 이미 다른 담당인 근무 → 퇴사 목록에 없음 | 연결 | `LeavePreviewServiceIntegrationTest.classifyShiftsAndPreserveComparisonFacts` (담당 기준 분류) |
| V3-N-06·08 | 퇴사 DRAFT 삭제·Pattern 유지, 퇴사 후 새 근무표에 퇴사자 0건 | 연결 | `LeaveConfirmServiceIntegrationTest.commits…PatternPreservation…`, `DraftMembershipGuardConcurrencyTest.scheduleCreationWaitsForLeaveAndSkipsLeaverPattern` (#106) |
| V3-N-07 | 퇴사 후 Publish 성공 | 없음(후) | |
| V3-N-09 | 관리자 취소 → 사유·시각·행위자, 빠진 근무 표시 | 연결 | `ShiftServiceTest` "공개된 시작 전 Shift를 취소하고 취소 메타데이터를 기록한다", `RemovedShiftQueryServiceTest.cancelledShiftIsListed` |
| V3-N-10 | CANCELLED를 다시 SCHEDULED로 바꾸는 경로 없음 | 부분 | `ShiftServiceTest` "이미 취소된 공개 Shift는 다시 취소할 수 없다" (재취소만). 수정·재배정 경로 고정은 없음(후) |
| V3-N-11 | 공개 근무표에 추가 → SCHEDULED, 알림 1건 | 연결 | `ShiftServiceTest` (#93 `createPublishedShift_*`), `ShiftChangeNotificationIntegrationTest.addPublishedShiftNotifiesAssignee` (#100) |
| V3-N-12 | 담당자 변경 → 같은 ID·이력·알림 | 연결 | `ShiftServiceTest` "공개 Shift 담당자 변경은 활성 대타를 종료하고 이력을 같은 순서로 저장한다", `ShiftChangeNotificationIntegrationTest.reassignNotifiesEachRoleOnce` |
| V3-N-13 | 시작한 근무 취소·변경 → 거부 | 연결 | `ShiftServiceTest` "시작한 공개 Shift는 취소할 수 없다" 등 |
| V3-N-14 | 이력 저장 실패 → 담당자 변경 Rollback | 없음(후) | 대타 종료 실패 Rollback(`ShiftServiceTest` L781)은 있으나 이력 저장 실패 주입은 없음. V3 담당 |
| V3-N-15 | 알림 실패 → 취소 성공 | 연결 | `ShiftChangeNotificationFailureIntegrationTest.allNotificationFailuresDoNotChangeBusinessResult` (#100) |
| V3-N-16 | 진행 중 대타 있는 근무 취소·변경 → ML-3 | 연결 | `SubstituteShiftChangeIntegrationTest` (#96, V4-N-61·62) |
| V3-N-17 | 관리자 두 명 동시 변경 (= H6-C4) | 없음(후) | V3 담당. #96에서 범위 밖으로 기록 |
| V3-N-18 | 퇴사 × 대타 승인 동시 | 부분 | `SubstituteMembershipGuardTest.approveWaitsForLeaveOfAccepter` (#105, 소속만 잠근 퇴사). 실제 퇴사 Service와의 경합은 없음(후) |
| V3-N-19 | 퇴사 × Publish 동시 (= ML5-T9) | 없음(후) | |

## 3. V4 대타·동시성 (ML-3, ML-5)

| ID | 계약 요약 | 판정 | 근거 Test |
|---|---|---|---|
| V4-N-18 | 재인계 뒤에도 `SHIFT_CHANGED` 유지 | 없음(후) | V4 담당 (작은 Test) |
| H6-C4 | 관리자 두 명 인계 동시 | 없음(후) | V3-N-17과 같음 |
| ML5-T7 (H6-C6) | 퇴사 × 다른 후보 마지막 거절 | 연결 | `SubstituteMemberLeaveTest.lastRejectWaitsForLeaveInProgress`·`lastRejectAfterLeaveClosesAsCandidateIneligible` (#92) |
| ML5-T9 | 퇴사 × Publish | 없음(후) | V3-N-19와 같음 |
| ML5-T12 | Deadlock 유도 → 503 매핑 | 범위 밖 | 503 매핑 미구현 (G03-D-03 §5-3, 실험 후 적용) |
| ML5-R2 | 이전 승인 순서의 Deadlock 재현 | 연결 | `SubstituteApprovePermissionLockTest.approveAndShiftFirstHandoverDoNotDeadlock` (#83, 이전 코드에서 실패 재현 EV-035) |
| ML5-R3 | Lock 전에 읽은 Entity의 옛 상태 | 연결 | `SubstituteApprovePermissionLockTest.approveRechecksStatusAfterLock` (V4-N-46), `SubstituteShiftChangeCloseTest.reportsAcceptedCandidateEvenIfRequestWasReadBeforeLock`, `SubstituteMemberLeaveTest.usesLatestStatusEvenIfReadBeforeLock` |

## 4. V5 알림 (NOTIFICATION-TYPES §5)

PR #100의 새 알림 Test는 NOTIFICATION-TYPES §8에만 기록돼 있어 여기에 함께 연결한다.

| ID | 계약 요약 | 판정 | 근거 Test |
|---|---|---|---|
| NT-T1 | 알림 실패 주입 → 업무·응답 그대로 | 연결 | `ShiftChangeNotificationFailureIntegrationTest.allNotificationFailuresDoNotChangeBusinessResult` |
| NT-T2 | 퇴사자 제외 | 연결 | `ShiftChangeNotificationIntegrationTest.leftOwnerGetsNoNotification`, `ShiftChangeNotificationPlannerTest.nonCurrentMembersExcluded` |
| NT-T3 | 재가입 후 이전 소속 알림 0건 | 없음(후) | V2-N-16과 같음 |
| NT-T4 | 넘어감·취소 문구에 새 담당 정보 없음 | 연결 | `ShiftChangeNotificationPlannerTest.reassignedAwayHasNoNewAssigneeInfo` |
| NT-T5 | 근무 변경 종료에 `SUBSTITUTE_MANAGER_CLOSED` 0건 | 연결 | `ShiftChangeNotificationIntegrationTest.reassignNotifiesEachRoleOnce` |
| NT-T6 | 가입·재가입 → 현재 관리자에게만 | 연결 + 이 PR | `ShiftChangeNotificationIntegrationTest.joinAndRejoinNotifyCurrentManagers`, `JoinAndExpiryNotificationIntegrationTest.joinNotifiesOnlyManagersOfThatWorkplace` |
| NT-T7 | 알림 없어도 대체 화면에서 확인 (= V5-N-19) | 없음(후) | 사용자 경로 Test. 관리자 대체 화면은 API-SB-02 (#104) |
| NT-T8 | `SUBSTITUTE_EXPIRED` 미발행 | **이 PR** | `JoinAndExpiryNotificationIntegrationTest.expiredRequestCreatesNoExpiredNotification` |
| NT-T9 | Commit과 저장 사이 퇴사 경합 | 없음(후) | |
| NT-T10 | §3-4 표 (합친 1건, 각자 1건, DB 기록 유지) | 연결 | `ShiftChangeNotificationIntegrationTest.reassignNotifiesEachRoleOnce`, `ShiftChangeNotificationPlannerTest.reassignTableOfSection34` |
| NT-T11 | 두 종료 사유 → 맞는 Type | 연결 | `ShiftChangeNotificationIntegrationTest.cancelNotifiesOwnerAccepterAndOtherManagers` (`SHIFT_CHANGED`), `.candidateIneligibleNotifiesRequesterAndManagers` (`CANDIDATE_INELIGIBLE`) |
| NT-T12 | 기존 알림의 묶음 실패 한계 (AS-IS) | 없음(후) | 새 알림은 받는 사람별 저장 (R5, `oneRecipientFailureDoesNotDropOthers`) |
| NT-T13 | PENDING 후보 알림 0건 | 연결 | `ShiftChangeNotificationIntegrationTest.reassignNotifiesEachRoleOnce` |
| NT-T14 | 실행 관리자 0건, 다른 관리자 1건 | 연결 | 같은 Test + `ShiftChangeNotificationPlannerTest.actorExcludedOnlyFromManagerRole` |
| NT-T15 | 응답의 종료 요청 수 = 실제 종료 수 | 연결 | `SubstituteShiftChangeIntegrationTest` (#96, `closedSubstituteRequests`) |
| NT-T16 ② | 활성 요청 없음 → 0, 종료 알림 0 | 연결 | `ShiftChangeNotificationIntegrationTest.cancelWithoutRequestNotifiesOwnerOnly` |
| V5-N-20 | 퇴사 확정의 자격 상실 알림 (실행 관리자 제외) | 없음(후) | 퇴사 확정 알림 연결 전 (#102 후속 4번) |

## 5. 퇴사 확정 확인 형식 (G03-D-05, G03-D-21)

| ID | 판정 | 근거 Test |
|---|---|---|
| D05-T1·T2, D21-T7 | 연결 | `LeaveConfirmServiceIntegrationTest.warningReportsOnlyClippedIntervalsAndConfirmationThenSucceeds` |
| D05-T6, D21-T9 | 연결 | `LeaveConfirmServiceIntegrationTest.reusesExistingV3OfficialConflictErrorAndCannotBypassWithAcknowledgements` |
| D05-T7 | 연결 | `publishedTimeChangesProduceStaleAndNoWrites`, `duplicateAndIncompleteDecisionsAreStaleWithoutWrites` |
| D05-T8 | 연결 | `permitsDraftReductionAndRejectsRepeatedCompletion` (감소 성공), `DraftMembershipGuardConcurrencyTest.leaveWaitsForDraftAddAndBecomesStale` (증가 STALE) |
| D05-T9 | 연결 | `lateFailureRollsBackClosedRequestsHistoryCancelledShiftAndDeletedDraft`, `failureAfterRealV4CleanupRollsBackCandidateAndAllV2V3Changes` |
| D05-T3·T4·T5·T5b·T5c·T10·T11, D21-T8 | 없음(후) | 미리보기 뒤 확인 목록이 바뀌는 경우·기존 API 형식 회귀·응답 Field — **퇴사 확정 POST 활성화 체크리스트에 포함** |
| D21-T1~T5 | 연결 | G03-D-21 표의 BASELINE·MIGRATION 표기 (`ShiftServiceTest`·`SubstituteRequestServiceTest`·`SubstituteCandidateServiceTest`), D21-T3·T6은 V4-N-39 (#96) |

## 6. 요약

| 판정 | 개수 (대략) |
|---|---|
| 연결 (기존 Test가 다룸) | 55 |
| 이 PR로 추가 | 4개 ID (V2-N-21·22, NT-T8, V2-N-19 보강) |
| 없음 (제출 후) | 약 25 — 퇴사 확정 POST 활성화 관련 (D05·D21·V2-N-30·53·V5-N-20), 동시성 확장 (V3-N-17·19, ML5-T9, NT-T9), 작은 회귀 (V2-N-10·16, V3-N-07·10·14, V4-N-18, NT-T3·T7·T12) |
| 범위 밖 | 7 (V2-N-01·02·04·05·17, ML5-T12 등) |

## 7. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-29 | 초안. 연결되지 않은 90개 ID 정적 대조, #100 알림 Test 연결, V2-N-21·22·NT-T8 Test 추가 (EV는 PR 본문) |
