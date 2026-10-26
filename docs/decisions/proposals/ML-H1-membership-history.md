# ML-H1 Membership 이력 보존과 재가입 범위

> **현재 승인 상태 (2026-10-26):** 사용자 전달 구두 승인과 PR 리뷰에 따른 채택 범위·예외는 [Decision Log](../DECISION-LOG.md) §4를 따른다. 아래 최초 작성 당시 PROPOSED/PENDING 표기는 이력이며 현재 승인 범위를 덮어쓰지 않는다. 구현·실험·배포 및 미결정 사항은 각각 별도 상태다.

| 축 | 상태 |
|---|---|
| 결정 단계 | **사용자 잠정 결정** (2026-10-23). 팀 승인 전 |
| ② 정책 | 카드 `PROPOSED`. `DECISION-LOG`의 G03-D-11·G03-D-13은 `PENDING` 그대로 |
| ③ 문서 | `DRAFT` |
| 구현 | 없음 |
| ④ / ⑤ | 신규 Test 전부 `SPECIFIED` / `NOT_RUN` (`TEST-INVENTORY` §4) |
| 기준 코드 | 3차 `develop` `c0427c2` (Production Java = `cdaaa13` = 2차 `8da2bec`). 정적 확인 |
| 관련 ID | G03-D-11 (종료 채택), **G03-D-13 (재가입 방식)**, G03-D-03 (ErrorCode 체계), G03-C-10·C-11, G03-INV-03, G01-P-03·P-04 |
| 주관 / 리뷰 | V2 Owner (주관). V3·V4·V5 Owner, Frontend (§11) |

> 이 카드는 퇴사 기능의 **전제(소속 이력 모델)**와 **재가입 범위의 현재 위치**만 정한다. 퇴사 Command, 권한, Shift·대타 정리, 동시성은 후속 카드 ML-2~5에서 정한다. 이 카드가 닫혀도 퇴사 기능은 완성되지 않는다.
> 이 카드는 G03-D-11(퇴사를 3차 Scope로 채택)이 팀에서 채택된다는 전제 위에 있다. G03-D-11이 채택되지 않으면 이 카드 전체를 다시 연다.
> 진행 절차는 [`PROCESS-CHARTER`](../PROCESS-CHARTER.md)를 따른다.

---

## 1. 왜 지금 정해야 하나

- 과거 업무 기록은 모두 `WorkplaceMember`를 NOT NULL FK로 가리킨다: Shift, RegularShiftPattern, SubstituteRequest(요청자), SubstituteCandidate, Notification(수신자).
- 퇴사 기능을 만들면 "퇴사한 사람의 Row를 어떻게 남기는가"가 처음으로 정해진다. 여기서 Row를 지우거나 되살리면, 나중에 재가입을 넣을 때 퇴사 전후 기간을 구분할 수 없다.
- 현재 재가입 시도에는 `WPL-002 "이미 참여 중인 근무지입니다."`가 나간다. 퇴사 기능이 생기면 이 문구가 퇴사자에게 사실과 다른 안내가 된다.

## 2. Java AS-IS (① `VERIFIED_AS_IS`, 정적 확인)

경로 기준: `backend/src/main/java/com/merge/backend/domain/`

| 사실 | 근거 |
|---|---|
| `UNIQUE(workplace_id, user_id)` | `workplace/entity/WorkplaceMember.java` L13-17 |
| `leftAt`은 생성자에서만 설정. role·`joinedAt`·`leftAt`을 바꾸는 메서드 없음 | 같은 파일 L37-58 |
| 퇴사(`leftAt` 기록) 경로 없음 | G03-C-11 "leftAt을 설정하는 경로가 확인되지 않음". 운영 DB에 `leftAt`이 있는 Row가 있는지는 UNKNOWN (G-02 §5.2) |
| 가입 중복 검사는 `leftAt`과 무관 | `workplace/service/WorkplaceService.java` L64 `existsByWorkplaceAndUser` → `ALREADY_JOINED_WORKPLACE` |
| WPL ErrorCode는 001~005 | `workplace/exception/WorkplaceErrorCode.java` (WPL-002 = 409 CONFLICT) |
| 현재 구성원 판정 = `leftAt IS NULL` | G03-INV-03. 예: `WorkplaceService` L81 `findAllByUser_IdAndLeftAtIsNull` |
| 운영 Profile은 `ddl-auto: update` | `backend/src/main/resources/application-prod.yml` L10 (Unique 제거를 자동으로 하지 않음) |

**결론:** 재가입은 서비스 검사와 DB Unique 두 겹으로 막혀 있다 (G01-P-03 Preserve). 다만 퇴사 경로가 없으므로, 퇴사자가 WPL-002를 받는 상황은 **현재 API로는 만들 수 없다.**

## 3. 이전 제안 (원문 위치)

원문은 [`docs/references/ml/`](../../references/ml/)에 있다 (과거 제안, 현행 계약 아님).

- 작성자 잠정 정책 14 (ML-DECISION-01-FINAL의 입력): "재가입은 새 Membership 이력을 선호하지만 MVP 포함 여부와 DB Unique 변경은 별도 미정이다."
- [`ML-DECISION-01-FINAL`](../../references/ml/ML-DECISION-01-FINAL.md): Membership은 `leftAt` null → now. Role·`joinedAt` 유지. Row 보존. 재가입은 UNKNOWN (ML-Q03). C-10은 "재가입 결정 전까지 변경 없음".
- [`ML-CONCURRENCY-01-R`](../../references/ml/ML-CONCURRENCY-01-R.md): Guard 대상이 Membership Row다. Row를 보존해야 성립한다.

## 4. 사용자 잠정 결정

| ID | 결정 문장 | 이유 (사용자 의견 요지) | 성격 |
|---|---|---|---|
| H1-a | 퇴사 처리에서 WorkplaceMember Row를 삭제하지 않고 `leftAt`을 기록한다. ID, `joinedAt`, role과 과거 업무(Shift·Pattern·Request·Candidate·Notification) FK를 보존하며, 종료된 Row는 재활성화하지 않는다. 계정 탈퇴·개인정보 파기는 별도 계약에 따른다 | 퇴사 직후 지울 필요가 없다. 다시 활성화하지 않아야 나중에 다시 처리되어도 문제가 없다 | 정책 |
| H1-b | 재가입은 이번 SWITCH V3에서 **구현할 가능성이 높다.** 이번 프로젝트에서 제외를 확정하지 않는다. 현재는 퇴사 구현 묶음에서 **우선 분리**하고, ML-3과 ML-H2의 DB·동시성·이력 조회 비용을 검토한 뒤 이번 프로젝트 포함 여부를 최종 결정한다 | 나중에 필요하고, 이번에 넣을 가능성이 높다 | **구현 범위: 미결정** → `SUPERSEDED`: [ML-H2](ML-H2-rejoin-and-active-membership.md) H2-4 "재가입 = 유효한 초대 코드 + 현재 소속 없음"으로 이번 프로젝트 포함. 후속 사용자 잠정 결정으로 대체 (팀 승인 전, 구현 기준 전환은 팀 승인 필요) |
| H1-c | 재가입을 구현한다면 새 Membership Row로 소속 기간을 구분하는 방식을 우선 후보로 둔다. 현재 DB Unique는 유지한다. Unique 변경과 활성 소속 유일성 구현 방법은 ML-H2에서 정한다 | 가입했다가 퇴사한 기록과 다시 회원이 된 사실은 서로 다른 정보다 | 설계 후보 → `SUPERSEDED`: [ML-H2](ML-H2-rejoin-and-active-membership.md) H2-1 (새 Row), H2-2·H2-8 (현재 소속 유일성, U2' 우선·실험 통과 조건부). "현재 DB Unique 유지"도 대체됨. 후속 사용자 잠정 결정으로 대체 (팀 승인 전, 구현 기준 전환은 팀 승인 필요) |
| H1-d | 재가입을 지원하지 않는 동안, 종료된 소속자가 다시 참여하면 WPL-002를 재사용하지 않고 새 ErrorCode로 거부한다. 현재 소속자의 중복 가입은 WPL-002를 유지한다. HTTP 409는 후보다 | 올바르게 응답하는 쪽 | **Improvement** (AS-IS 아님) → `SUPERSEDED`: [ML-H2](ML-H2-rejoin-and-active-membership.md) §8 "H1-d(퇴사자 전용 오류)는 쓰지 않음". 재가입이 정상 경로가 되어 필요 없어짐. 후속 사용자 잠정 결정으로 대체 (팀 승인 전, 구현 기준 전환은 팀 승인 필요) |

H1-c 예: 직원이 1월에 가입해 3월에 퇴사하고 6월에 다시 가입하면, 1~3월 소속과 6월 이후 소속을 별개 Row로 남긴다. 현재 `UNIQUE(workplace_id, user_id)` 때문에 이 방식은 그대로는 구현할 수 없다.

**H1-b와 H1-d의 관계:** ML-H2에서 재가입을 퇴사와 같은 묶음에 넣기로 하면, 퇴사자가 재가입을 거부당하는 기간이 없어져 H1-d가 필요 없을 수 있다. 그래서 H1-d의 구현 여부도 ML-H2 결정 때 함께 다시 본다.

> **후속 연결 (2026-10-25):** H1-b·H1-c·H1-d는 [ML-H2](ML-H2-rejoin-and-active-membership.md)의 사용자 잠정 결정(2026-10-23)으로 대체되었다. **H1-a는 그대로 유효**하며 ML-H2의 전제다. ML-H2도 팀 승인 전이므로, 구현 기준을 ML-H2로 바꾸는 것은 팀 승인(`DECISION-LOG` G03-D-11·13) 이후다. 원래 문장은 판단 근거로 남겨 둔다.

**검토 반영:**
- "영구 보존" 표현을 H1-a의 범위 문장으로 바꿈 (교차 검토 의견).
- 재가입을 `DEFERRED`나 "제외 확정"으로 적지 않고 "구현 범위 미결정"으로 표기 (교차 검토 의견, 사용자 의견 2026-10-23).
- 재검토 때 "어차피 DDL을 하니까"로 판단하지 않도록 §10에 비용을 나눠 적음 (교차 검토 의견).

## 5. 보장 범위와 제외 범위

**이 카드가 보장하려는 것 (구현·검증 후):**
- 퇴사 후에도 과거 업무 기록이 같은 Member Row를 가리킨다.
- 종료된 소속은 어떤 조회에서도 현재 구성원으로 판정되지 않는다.
- 재가입을 지원하지 않는 동안, 퇴사자의 재참여 시도에 사실과 맞는 오류가 나간다.

**보존 범위와 삭제 가능 범위 (ML-2와의 경계):**
- 보존: Membership Row, 과거 공식 Shift, 취소된 공식 Shift, SubstituteRequest·Candidate, Notification.
- 삭제 가능: 아직 발행되지 않은 계획(RegularShiftPattern, DRAFT Shift). 처리 방식은 **ML-2에서 정한다** (ML-DECISION-01-FINAL은 hard delete를 제안했으나 승인 전).

**지금 정하지 않는 것 (사용자에게 생길 수 있는 제약):**
- 재가입 구현 시점이 정해지기 전까지, 퇴사자는 같은 근무지로 다시 참여할 수 없다. 관리자는 새 근무지나 다른 계정으로 우회해야 한다.
- 퇴사자가 자기 과거 기록을 볼 수 있는지는 정하지 않는다. 현재는 `leftAt` 필터 때문에 볼 수 없다 (ML-4).
- 개인정보 보존·파기 기간은 범위 밖이다.

## 6. 상태 전이

| 대상 | 허용 | 금지 |
|---|---|---|
| WorkplaceMember | `현재 소속 (leftAt = null) → 종료된 소속 (leftAt = t)`, Row마다 1회만 | `leftAt` → null (재활성화)<br>종료 Row의 `leftAt` 값 변경<br>퇴사 처리에서 Row 삭제<br>role·`joinedAt` 변경 (이 카드 범위에서) |

- `t`는 퇴사 Command의 `Clock` 시각이다. 실행 주체·사전조건(진행 중 근무 등)은 ML-4에서 정한다.
- 재가입이 들어오면 "같은 Row를 되살리는 전이"가 아니라 "새 Row 생성"이다 (H1-c 후보). 이 전이의 조건은 ML-H2에서 정한다.
- Role 변경·MANAGER 위임은 이 카드 범위 밖이다 (G01-P-04).

## 7. 불변조건

ID 규칙은 `PROCESS-CHARTER` §5-5를 따른다. 확인한 기존 ID: `G03-INV-01~14` (G-03 공유본), ML 원문 내부의 `INV-C1~C4`·`INV-L1~L6`·`INV-S3`. 작성자 로컬 분석에서 임시로 쓴 `INV-H1~H3`은 쓰지 않는다 (아래 표로 대체).

| ID | 불변조건 | 현재 보장 수단 | 상태 |
|---|---|---|---|
| ML-H1-INV-01 | 종료된 Membership Row는 퇴사 처리로 삭제되지 않는다. 과거 기록의 `member_id` FK는 바뀌지 않는다 | 삭제 경로 없음 (정적). FK NOT NULL | PROPOSED |
| ML-H1-INV-02 | `leftAt`은 한 번 기록되면 null로 돌아가거나 다른 값으로 바뀌지 않는다 | 쓰기 경로 자체가 없음 (AS-IS). 퇴사 Command 도입 후에는 구현이 보장해야 함 | PROPOSED |
| (참조) G03-INV-03 | Current Membership = `leftAt == null` | `...AndLeftAtIsNull` Query | ① `REVIEW_ACCEPTED` (G-03). `leftAt` 조건이 빠진 SUB-05·SUB-07 목록은 ML-3·ML-4에서 다룸 |
| (참조) G03-C-10 | 같은 Workplace·User에 현재 Membership이 둘 이상 생기지 않는다 | DB Unique + `WorkplaceService` L64 | ① `REVIEW_ACCEPTED`. H1-c 도입 시 대체 수단 필요 (ML-H2) |

## 8. API·DB·Frontend 영향

| 영역 | 영향 | 기존 계약 변경 |
|---|---|---|
| 새 API | 없음. 퇴사 API는 ML-4 | — |
| `POST /workplaces/join` | 종료된 소속자에게 새 ErrorCode 응답 (H1-d) | **변경 있음. 단 현재 도달할 수 없는 상태에 대해서만.** 현재 소속자의 WPL-002, 잘못된 코드의 WPL-001, 정상 가입은 그대로 |
| ErrorCode | 후보: `WPL-006` / HTTP 409 / 메시지 예 "퇴사한 근무지에는 다시 참여할 수 없습니다." Enum 이름 미정 | AI-RULES §2 승인 대상 (ErrorCode `code`/`message`/Status) |
| DB | 변경 없음. Unique 유지 | 없음 |
| Frontend | join 오류 처리에 새 `code` 분기 (문구 표시) | 작음. Frontend 담당 확인 필요 |

**ErrorCode를 확정하기 전에 확인할 것:**
1. 2차 기획 API-WP 명세에 WPL-006 이후 번호가 이미 정의돼 있는지 (명세는 저장소에 없음, DL-005).
2. G03-D-03 (ErrorCode Prefix / 400·409 규칙)과 맞는지. 409는 "현재 상태와 충돌"이라 후보로 적절하지만 공통 규칙은 아직 `PENDING`이다.
3. 퇴사자에게 "과거에 소속했었다"는 사실을 응답으로 알려도 되는지. 본인 이력이라 노출 문제는 작다고 보지만 V2 Owner가 확인한다.

## 9. 검증 계약

### 9-1. 신규 Test

`TEST-INVENTORY` §4에 같은 행을 `V2-N-01~05`로 등록했다. 전부 ④ `SPECIFIED` / ⑤ `NOT_RUN`이다.

| ID (별칭) | Given | When | Then | 구분 · 작성 가능 여부 |
|---|---|---|---|---|
| V2-N-01 (ML-H1-T1) | 현재 EMPLOYEE Member M (id=m, role, `joinedAt`=j) | 퇴사 처리 (Clock = t) | 같은 id m의 Row가 남는다. `leftAt = t`. role·`joinedAt = j` 그대로. Row 수 변화 없음 | PROPOSED_IMPROVEMENT · 불가 (퇴사 Command 없음, ML-4) |
| V2-N-02 (ML-H1-T2) | M이 퇴사 전에 Shift·Request·Candidate·Notification을 가짐 (삭제 가능 범위 제외) | 퇴사 처리 | 보존 범위의 각 Row가 그대로 있고 `member_id = m` | PROPOSED_IMPROVEMENT · 불가 (ML-4, 보존 범위는 ML-2) |
| V2-N-03 (ML-H1-T3) | 종료된 Row (`leftAt` ≠ null)를 5-인자 생성자로 준비 | `requireManager`, 구성원 목록, 내 근무지 목록 조회 | M은 결과에 없다. M 명의의 관리자 행위는 WPL-003 / WPL-004 | **MIGRATION (현재 Java 동작 고정)** · 가능. 퇴사 Command 없이 작성할 수 있음 |
| V2-N-04 (ML-H1-T4) — `SUPERSEDED` → ML-H2 V2-N-06 | User U의 종료 Row가 Workplace W에 있음 (5-인자 생성자) | U가 W의 초대 코드로 참여 | 새 ErrorCode (후보 WPL-006, 409). 새 Row 저장 0 | PROPOSED_IMPROVEMENT · 승인 전 작성 금지 |
| V2-N-05 (ML-H1-T5) | M이 이미 퇴사함 (`leftAt = t1`) | 다시 퇴사 처리 (Clock = t2) | `leftAt = t1` 그대로. 응답(오류 또는 멱등 성공)은 **ML-4에서 정한다** | PROPOSED_IMPROVEMENT · 불가 (ML-4) |

V2-N-03은 현재 동작을 고정하는 Test라 정책 승인 대상이 아니다. 작성하려면 Task 승인이 필요하다 (`TEST-INVENTORY` §3-4와 같은 기준).

### 9-2. 기존 Join Test 회귀 (기대값 변경 0)

`PROCESS-CHARTER` §6을 따른다. 아래의 "Arrange 변경 가능"은 Mock 준비 코드만 바뀔 수 있다는 뜻이고, 업무상 기대 결과는 바꾸지 않는다.

경로 기준: `backend/src/test/java/com/merge/backend/domain/workplace/`

| Test | 업무 상황 | 기대 결과 (불변) | H1-d 구현 시 |
|---|---|---|---|
| `service/WorkplaceServiceTest.rejectDuplicateJoin` L189-212 | 현재 소속자가 다시 가입 | `ALREADY_JOINED_WORKPLACE`, `save` 호출 0 | **Arrange 변경 가능.** Class가 `@ExtendWith(MockitoExtension.class)`라 Strict Stubs다. 구현이 `existsByWorkplaceAndUser`를 더 이상 부르지 않으면 이 Stub 때문에 `UnnecessaryStubbingException`으로 실패한다. 이때 Stub을 "현재 소속 Row 있음"을 뜻하는 새 조회로 바꾸고, 같은 업무 상황을 검증하는지 리뷰어가 확인한다 (Charter §6-3) |
| `service/WorkplaceServiceTest.joinAsEmployee` L74 | 이력 없는 사용자 가입 | EMPLOYEE·`joinedAt`·`leftAt` null, `save` 1회 | 중복 검사 Stub 없음. Mock 기본값(false / `Optional.empty()`)으로 통과할 것으로 예상 (추정, 구현 후 확인) |
| `service/WorkplaceServiceIntegrationTest.rejectDuplicateJoin` L118 | 현재 소속자 재가입 (실제 DB) | `ALREADY_JOINED_WORKPLACE` | 변경 없음 |
| `service/WorkplaceServiceIntegrationTest.joinWorkplace` L89 | 정상 가입 | Row 생성, `leftAt` null | 변경 없음 |
| `controller/WorkplaceControllerTest.rejectDuplicateJoin` L283 | Service가 WPL-002를 던짐 | 409, `code = WPL-002` | 변경 없음 |
| `repository/WorkplaceMemberRepositoryTest.rejectDuplicateMembershipAtDatabaseLevel` L241 | 같은 조합 두 번 저장 | `DataIntegrityViolationException` | 변경 없음 (Unique 유지). **ML-H2에서 재가입을 넣으면 이 기대값이 바뀌므로 그때 별도 승인 필요** → [ML-H2](ML-H2-rejoin-and-active-membership.md)가 재가입 포함으로 잠정 결정함. 기대값 변경 대상 (승인 전 변경 금지) |

## 10. ML-H2 재검토 조건

> 재가입은 이번 SWITCH V3에서 구현할 가능성이 높은 기능이다. 현재 퇴사 구현 묶음에서 우선 분리했고, 이번 프로젝트 포함 여부는 정하지 않았다. ML-3(Substitute 후보 상태 모델·운영 DB DDL 범위)이 정해지면 ML-H2에서 결정한다. 팀 승인 전이다.

ML-H2에서 결정할 때 두 가지를 나눠 비교한다.
1. **함께 하면 줄어드는 비용:** 운영 DDL 절차(배포 창, 수동 적용, Rollback 계획)를 한 번으로 합칠 수 있다. 단, 대타 Enum 확장과 Membership Unique 변경은 **서로 다른 Data Migration**이다.
2. **그래도 남는 비용:**
   - 활성 소속 유일성 대체 수단 (MySQL에는 부분 Unique가 없음)
   - 동시 가입 제어와 동시성 Test
   - `RegularShiftPatternService` L242 `findByWorkplaceIdAndUserId` 단건 조회 수정 (Row가 2개면 예외)
   - 재가입 후 이전 기간 기록의 조회 범위 (`SubstituteRequestRepository` L20 보낸 요청 목록 등)
   - 기존 Unique Test 기대값 변경 절차 (§9-2 마지막 행)

다른 재검토 트리거:
- 팀이 "퇴사 후 같은 근무지 복귀"를 시연·사용자 시나리오에 넣기로 할 때.
- G03-D-13이 팀에서 다른 방식으로 결정될 때.

ML-H2 완료 조건: 이번 프로젝트 포함 여부, 포함 시 구조(H1-c 확정 여부), Unique DDL 절차, H1-d 유지 여부, 영향 Test 목록이 카드에 기록됨.

> **재검토 결과 (2026-10-25 연결):** [ML-H2](ML-H2-rejoin-and-active-membership.md)에 기록됨 (사용자 잠정 결정 2026-10-23). 포함, 새 Row, U2' 우선(실험 통과 조건부, 실패 시 U3), H1-d 미사용. 팀 승인 전.

## 11. Cross-Domain 영향과 리뷰

| Domain | 확인할 것 | 리뷰 |
|---|---|---|
| V2 Workplace (주관) | 전체. 특히 §4 H1-b 범위 표현, §8 ErrorCode, §9-2 Arrange 변경 | 필수 |
| V3 Schedule/Shift | §5 보존·삭제 범위 (Pattern·DRAFT는 ML-2), 현재 구성원 조회가 `leftAt` 기준인지 | 필수 |
| V4 Substitute | Request·Candidate 보존. SUB-05·SUB-07 `leftAt` 공백은 ML-3·ML-4 | 필수 |
| V5 Notification | 수신자 FK 보존 | 확인 |
| V1 Auth | 영향 없음 | 없음 |
| Frontend | join 오류 문구 분기 | 확인 |

## 12. 후속 카드로 넘긴 항목

| 항목 | 카드 |
|---|---|
| 재가입 이번 프로젝트 포함 여부와 구조, H1-d 유지 여부 | ML-H2 |
| Pattern·DRAFT 처리, 공식 Shift 취소·보충 | ML-2 |
| 대타 Request·Candidate 정리 (B+ 여부) | ML-3 |
| 퇴사 주체·사전조건, 진행 중 근무, 중복 퇴사 응답(V2-N-05), 퇴사자 조회 4종 | ML-4 |
| 동시성 불변조건·Lock (H6) | ML-5 |
| 개인정보 보존·파기 | 범위 밖 (기록만) |

## 13. 변경 이력

| 일자 | 내용 | 이유 |
|---|---|---|
| 2026-10-23 | 초안 작성 (사용자 잠정 결정 H1-a~d) | 작성자 로컬 분석과 사용자 답변 |
| 2026-10-23 | H1-b를 "이번 묶음에서 제외"에서 "구현 범위 미결정, 구현 가능성 높음"으로 수정. H1-b·H1-d 관계 추가 | 사용자 의견: 재가입은 이번 프로젝트에서 구현할 가능성이 높음 |
| 2026-10-25 | H1-b·H1-c·H1-d와 §9 Unique 행, §10에 후속 카드 ML-H2 연결 표시 (`SUPERSEDED`, 팀 승인 전). 원래 문장은 그대로 둠 | S2-2 대체 관계 정리 |
