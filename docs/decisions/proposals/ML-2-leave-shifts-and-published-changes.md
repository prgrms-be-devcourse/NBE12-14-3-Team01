# ML-2 퇴사와 근무 정리 · G03-D-26 취소 의미 · G03-D-27 공개 후 변경

> **현재 승인 상태 (2026-10-26):** 사용자 전달 구두 승인과 PR 리뷰에 따른 채택 범위·예외는 [Decision Log](../DECISION-LOG.md) §4를 따른다. 아래 최초 작성 당시 PROPOSED/PENDING 표기는 이력이며 현재 승인 범위를 덮어쓰지 않는다. 구현·실험·배포 및 미결정 사항은 각각 별도 상태다.

| 축 | 상태 |
|---|---|
| 결정 단계 | **사용자 잠정 결정** (2026-10-23). 팀 승인 전 |
| ② 정책 | 카드 `PROPOSED`. `DECISION-LOG` G03-D-11·D-12·D-14·D-26·D-27 모두 `PENDING` |
| ③ 문서 | `DRAFT` (저장소 Proposal 문서. 2026-10-25 이관, 원본 로컬 `drafts/D3-ML-2/`) |
| 구현 | 없음. **Kotlin Migration PR에 섞지 않는 별도 Improvement** (A-04 §20.12 "PUBLISHED 정정 API 추가"는 Migration 금지) |
| ④ / ⑤ | 전부 `SPECIFIED` / `NOT_RUN` |
| 기준 코드 | 3차 `develop` `c0427c2` (Production Java = `cdaaa13`). 이후 `31ecaee`(PR #5)는 Substitute **Test만** 변경. 정적 확인 |
| 이관 당시 develop HEAD | `a148a62` (2026-10-25). `backend/src/main`은 `cdaaa13` 이후 변경 0 (이관 시 diff로 확인) |
| 코드 근거 재검증 | **NO** (S2-1은 이관만 함. 본문의 줄 번호·Test 근거는 위 "기준 코드" 시점 값). 이후 검증 예정: S2-2~S3 |
| 관련 ID | G03-D-11 (퇴사 채택, 전제), G03-D-12 (종료 최소 범위), G03-D-14 (Pattern), **G03-D-26** (CANCELLED 의미), **G03-D-27** (공개 후 정정 범위), G03-D-21 (불가능 시간 처리), G03-D-22 (알림 유실), G03-C-29, **G03-C-31** (공개 후 변경 제한, 계약 변경 대상), G03-INV-04·07 |
| 앞선 카드 | [ML-H1](ML-H1-membership-history.md) (소속 이력 보존, PR #6) |
| 주관 / 리뷰 | V3 Owner (주관). V2 (퇴사 Command), V4 (대타), V5 (알림), Frontend (필수) |

> 저장소 위치 예정: `docs/decisions/proposals/ML-2-leave-shifts-and-published-changes.md`. 절차는 `docs/decisions/PROCESS-CHARTER.md`.
> 이 카드가 닫혀도 퇴사 기능은 완성되지 않는다. 대타 정리(ML-3), 재가입(ML-H2), 권한·진행 중 근무·조회(ML-4), Lock(ML-5)이 연결되어야 한다.
> 이 카드의 퇴사 관련 내용은 G03-D-11(퇴사를 3차 범위로 채택)이 팀에서 채택된다는 전제 위에 있다. 공개 후 변경(G03-D-27)은 퇴사와 독립적으로도 의미가 있다.

---

## 1. 왜 지금 정해야 하나

퇴사가 `leftAt`만 기록한다면 (예: 직원 이 10/23 퇴사, 공개 근무 10/24·10/26, 다음 주 DRAFT 2건, 월·수 Pattern):
1. 관리자 근무표에 이의 공개 근무가 평소와 똑같이 보인다. 빈자리가 보이지 않아 결근이 생긴다.
2. 공개 근무표는 고칠 수 없고, 같은 주에 근무표를 하나 더 만들 수도 없다. 대타 요청은 담당자 본인만 만들 수 있는데 담당자가 퇴사자다. **채울 방법이 없다.**
3. 다음 주 DRAFT에 이의 근무가 남아 있으면 Publish 전체가 거부된다.
4. 공개 후 일반 운영 변경(취소, 추가 근무, 담당자 교체)도 할 수 없다 (P1으로 미뤄져 있었음).

## 2. AS-IS (① `VERIFIED_AS_IS`, 정적 확인)

경로 기준: `backend/src/main/java/com/merge/backend/domain/`

| 사실 | 근거 |
|---|---|
| Schedule = DRAFT / PUBLISHED. 같은 근무지·주에 1개. PUBLISHED → DRAFT 역전이 없음 | `shift/entity/ScheduleStatus.java`, G03-INV-07 (P0 §3) |
| Shift = SCHEDULED / CANCELLED. CANCELLED로 바꾸는 코드 0곳. 취소 사유·시각·취소자 Column 없음 | `shift/entity/ShiftStatus.java`, `shift/entity/Shift.java` |
| `Shift.member` NOT NULL. `changeMember`는 대타 승인에서만 사용 | `Shift.java` L26, L65, `substitute/entity/SubstituteRequest.java` L82 |
| Shift 추가·수정·삭제는 DRAFT에서만 (`validSchedule`). DRAFT 수정은 `memberId`를 받아 담당자도 바꿀 수 있음. 불가능 시간은 **경고 후 확인** (`confirmUnavailableConflict`) | `shift/service/ShiftService.java` L198-212, `shift/dto/ShiftRequest.java` |
| 겹침·충돌 검사는 모두 `status = SCHEDULED` → CANCELLED는 즉시 제외 | `shift/repository/ShiftRepository.java` L41, L60, L76, L91 |
| 대타 생성·수락·승인은 취소된 근무를 거부. 대타 승인의 불가능 시간 충돌은 **거부** (SUB-006) | `SubstituteRequestService` L141, `SubstituteCandidateService` L159, V4-T-29 |
| 관리자 주간 근무표는 상태·퇴사와 무관하게 모든 Shift 반환 (`status` 포함). Frontend는 Shift 상태로 구분해 그리지 않음 | `shift/service/ScheduleService.java` L149-190, `shift/dto/ScheduleShiftResponse.java`, `frontend/.../schedule/page.tsx` L42 |
| 직원 "내 근무"는 `leftAt IS NULL` + PUBLISHED + SCHEDULED만. 취소되면 **흔적 없이 사라짐**. Frontend 철자 `"CANCELED"`는 Backend 값과 다름 | `ShiftRepository` L70-80, `frontend/.../my-shifts/page.tsx` L35 |
| Publish는 DRAFT에 퇴사자 담당 Shift가 있으면 전체 거부 (`INVALID_SHIFT_MEMBER`) | `ScheduleService` L345-363 |
| Lock: DRAFT Shift 편집과 Publish는 Schedule Row `PESSIMISTIC_WRITE`. 대타 생성은 Shift Row Lock. 대타 승인은 Request·User Lock (Shift는 잠그지 않음). Shift `@Version` 없음 | `ShiftService` L200, `ScheduleService` L274, `SubstituteRequestService` L132·L236 |
| Pattern = 담당자 + 요일 + 시각. 시작·종료일 없음. **새 DRAFT 생성 시 `MemberLeftAtIsNull`로 퇴사자 Pattern 자동 제외.** 목록 조회도 제외. 퇴사자 대상 등록·수정 거부 | `shift/entity/RegularShiftPattern.java`, `shift/repository/RegularShiftPatternRepository.java` L13, `shift/service/RegularShiftPatternService.java` L104-112, L163 |
| Pattern은 근무표 생성 시점에만 Shift로 복사 (소급 없음) | `ScheduleService` createDraftSchedule, G-03 C-18·C-19 |
| `shift_id`를 참조하는 것은 `SubstituteRequest`뿐 (NOT NULL). DRAFT Shift를 참조하는 Row 없음 (대타는 PUBLISHED만) | 전체 검색 |
| 알림: 8 Type, 근무를 가리키는 Column 없음 (받는 사람·Type·문구·`readAt`). Commit 후 `REQUIRES_NEW`, 실패 시 로그만. 앱 안 목록만 (푸시·메일 없음) | `notification/entity/Notification.java`, `NotificationType.java`, 각 Listener |
| Migration 도구 없음. 운영 `ddl-auto: update` | `backend/build.gradle.kts`, `application-prod.yml` L10 |

**2차 명세·기존 기록:** API-SCH는 공개 후 수정·취소를 P1으로 미룸 (SCH-02 p.20 등). G03-C-31 "PUBLISHED Shift는 일반 CRUD 불가, `Shift.member`는 SUB-04 APPROVE로만" (① `REVIEW_ACCEPTED`). G01-P-12 "CANCELLED Enum 존재, 쓰기 경로 없음". P0 §16 "담당자·시간이 달라지면 기존 승인 불가".

## 3. 이전 제안

- `docs/references/ml/ML-DECISION-01-FINAL.md`: 미래 공식 근무 전부 CANCELLED, 공개 근무표에 근무 추가(C-31 예외), DRAFT·Pattern hard delete. **이 카드에서 일부를 바꿨다** (§13).
- [`G03-D-19-expiry.md`](G03-D-19-expiry.md) §11: G03-D-27 채택 시 선행 제약 9개.

## 4. 사용자 잠정 결정

### 4-1. ML-2 퇴사 시 근무 처리

| ID | 결정 문장 | 이유 |
|---|---|---|
| ML-2-A | 퇴사 시 퇴사 대상이 **현재 담당자**인 이 근무지의 미래 PUBLISHED·SCHEDULED Shift를 모두 보여 주고, 관리자가 근무마다 **다른 현재 구성원에게 인계** 또는 **취소**를 고른다. 고르지 않은 근무가 있으면 퇴사를 확정하지 않는다 | 매장은 근무 자리(시간)는 두고 사람만 바꾸는 것이 보통이다. 빈자리를 몰래 남기지 않는다 |
| ML-2-A1 | 인계 대상에는 **실제로 그 근무를 할** 관리자도 포함할 수 있다. 빈자리를 없애려고 이름만 올리는 용도로 쓰지 않는다 | 근무표가 실제 운영과 같아야 한다 |
| ML-2-A2 | 확정 시 서버가 최신 상태를 다시 검사한다. 화면에서 본 목록·담당자·상태와 다르면 확정을 거부하고 다시 확인하게 한다 (409 후보) | 미리보기 후 다른 관리자의 변경이나 대타 승인이 있을 수 있다 |
| ML-2-A3 | 소속 종료, 인계, 취소, DRAFT 삭제, 대타 정리, 배정 이력 저장은 **하나의 Transaction**이다. 일부만 성공하지 않는다. 알림은 Commit 후 (원자성 없음) | 정합성 |
| ML-2-B1 | 이미 만든 DRAFT의 퇴사자 담당 Shift는 **퇴사 Transaction에서 자동 삭제**한다. 결과 화면(API 응답)에 삭제된 근무 목록을 보여 준다. Publish의 퇴사자 검사(`INVALID_SHIFT_MEMBER`)는 안전장치로 유지한다 | DRAFT는 공개 전 계획이고 참조 Row가 없어 이력 손실이 없다. 관리자는 평소처럼 DRAFT를 편집한다 |
| ML-2-B2 | 퇴사자의 정기 Pattern Row는 **삭제하지 않는다.** 종료된 소속에 속하므로 이후 근무표 생성에서 자동 제외된다 | 코드 변경 없이 목적("퇴사 후 반복 근무가 더 생기지 않음") 달성. 업무상 보존 가치는 없으며 구현 단순성 때문에 둔다 (아래 비고). 재가입하면 새 소속에 새 Pattern을 등록하므로 옛 Pattern이 되살아나지 않음 |
| ML-2-B3 | 이미 만든 근무표에는 Pattern 변경을 소급하지 않는다 (AS-IS 유지). 신입의 Pattern은 **등록 후 새로 만드는 근무표부터** 반영된다 | 기존 동작 |
| — | 분할 보충(근무 하나를 여러 명이 나눔)은 지원하지 않는다. 근무 하나 = 담당자 한 명 | 사용자 결정: 매장에서는 정해진 시간을 통째로 대체하는 경우가 대부분 |

> **ML-2-B2를 고른 이유 (사용자 확인, 2026-10-23):** 퇴사자의 Pattern은 **업무상 보존할 필요가 없다.** 그래도 남기는 이유는 **구현 단순성**이다. 이미 모든 조회가 종료된 소속을 걸러 내므로, 삭제 로직을 새로 만들 필요가 없다. 따라서 이 Row는 "이력"으로 쓰지 않는다 (화면·통계에서 사용하지 않음). 계정 탈퇴·개인정보 파기 정책이 생기면 함께 지울 대상이다. 앞으로 Pattern을 사용자·근무지 기준으로 조회하는 기능을 만들 때는 반드시 현재 소속만 거른다 (Test V3-N-08, ML-H2 확인 항목).

### 4-2. G03-D-26 CANCELLED의 의미

| ID | 결정 문장 |
|---|---|
| D26-1 | **CANCELLED는 아직 시작하지 않은 공식 Shift의 배정을 취소한 끝 상태다.** Row와 원래 담당자 참조, 연결된 대타 이력은 보존하며, 같은 Row를 다시 SCHEDULED로 바꾸지 않는다 |
| D26-2 | CANCELLED는 **배정 종료**를 뜻한다. 매장의 인력 수요가 사라졌다는 뜻이 아니다 |
| D26-3 (R1) | 취소 시 사유(예: `MEMBER_LEFT`, `MANAGER_CANCELLED`), 취소 시각, 취소를 일으킨 행위자를 기록한다. 행위자는 해당되지 않으면 null일 수 있다 (예: 퇴사 Command 내부 자동 처리. 퇴사 실행자는 퇴사 Command 쪽 기록과 연결해 검토) |
| D26-4 | 인계한 근무는 CANCELLED가 아니다 |
| D26-5 | 취소 후 보충 연결(N2)은 **이번 범위에서 분리**한다 (폐기 아님) |

### 4-3. G03-D-27 공개 후 변경 1차 범위

| ID | 결정 문장 |
|---|---|
| D27-1 | PUBLISHED Schedule은 DRAFT로 되돌리지 않는다. DRAFT CRUD 제한을 단순히 풀지 않고, **별도의 검증된 Command**로 제공한다 |
| D27-2 | 1차: 퇴사 시 인계·취소 (ML-2-A), 관리자의 미래 공식 Shift **추가**, **취소**, **담당자 변경**. **시간 변경은 보류** |
| D27-3 | 시간 변경이 필요하면 취소 + 추가로 처리할 수 있다. **이것은 한 번의 원자적 수정이 아니다** (중간 실패 시 취소만 남을 수 있음). 하나의 교체 Command가 필요한지는 후속 검토 |
| D27-4 | 담당자 변경마다 배정 이력(근무, 이전·새 담당자, 사유, 행위자, 시각)을 **같은 Transaction**에 기록한다. 이력 저장이 실패하면 변경도 Rollback |
| D27-5 | 진행 중(OPEN·ACCEPTED) 또는 승인된 대타가 있는 근무의 취소·담당자 변경은 무조건 허용하지 않는다. 상태별 처리는 ML-3 |
| D27-6 (전달) | 화면 표시가 기준, 알림은 보조. 직원·관리자 근무표에 취소·추가·인계를 표시하고, 관련자에게 변경 전·후가 담긴 알림을 보낸다. **알림 유실과 미열람까지 막는다고 표현하지 않는다.** 취소된 근무가 직원 화면에서 흔적 없이 사라지지 않게 한다 |

## 5. 보장 범위와 제외 범위

| 보장함 (구현·검증 후) | 보장하지 않음 / 제외 |
|---|---|
| 퇴사 확정 후 퇴사자가 담당인 이 근무지의 미래 공식 근무가 남지 않는다 (모두 인계 또는 취소) | 다른 근무지의 근무 (소속은 근무지별) |
| 인계해도 근무 Row·ID가 유지되고, 이전 담당자가 이력에 남는다 | 대타 승인으로 바뀐 담당자의 이력 Table 기록 (기존 `SubstituteRequest`가 기록, §8 ④) |
| 퇴사 후 Publish가 퇴사자 근무 때문에 막히지 않는다 | 취소 후 다른 날 추가한 근무가 그 취소를 보충했는지의 자동 추적 (N2 분리) |
| 취소된 근무는 직원·관리자 화면에서 사라지지 않는다 | 직원이 변경을 읽었는지·이해했는지 (`readAt`은 있으나 이번에 쓰지 않음) |
| 공개 후 변경은 같은 검증(현재 소속, 겹침, 다른 근무지 충돌, 불가능 시간)을 거친다 | 공개 후 시간 변경, 원자적 교체 |
| | 분할 보충, 담당자 없는 "빈 근무" |
| | 진행 중 근무가 있는 퇴사 (ML-4, 이전 잠정 정책: 거부) |
| | 퇴사자 본인의 과거 근무 조회 (ML-4) |

## 6. 상태 전이

| 대상 | 허용 | 금지 |
|---|---|---|
| Shift (PUBLISHED) | `SCHEDULED → CANCELLED` (시작 전, 관리자 취소 또는 퇴사 취소)<br>`SCHEDULED(담당 X) → SCHEDULED(담당 Y)` (시작 전, 관리자 변경 또는 퇴사 인계. 기존 대타 승인 포함) | `CANCELLED → SCHEDULED`<br>시작한 근무의 취소·인계<br>PUBLISHED Shift 삭제<br>PUBLISHED Shift 시간 변경 (보류) |
| Shift (DRAFT) | 기존 CRUD. 퇴사 시 퇴사자 담당 Row 삭제 | — |
| Schedule | 변경 없음 | `PUBLISHED → DRAFT` |
| Pattern | 변경 없음 (퇴사자 Pattern은 그대로 두고 적용만 제외) | 퇴사 처리에서 삭제 |

시간 경계: 시작 = `startAt ≤ now`. `startAt == now`는 이미 시작으로 본다 (대타 활성 기준 `startAt > now`와 같음).

## 7. 불변조건

| ID | 불변조건 |
|---|---|
| ML-2-INV-01 | 퇴사 확정 후, 종료된 소속이 담당인 미래 PUBLISHED·SCHEDULED Shift는 0건이다 |
| ML-2-INV-02 | 퇴사 확정 후, 종료된 소속이 담당인 DRAFT Shift는 0건이다 |
| ML-2-INV-03 | CANCELLED Shift는 다시 SCHEDULED가 되지 않는다 |
| ML-2-INV-04 | PUBLISHED Shift의 담당자가 바뀔 때마다 배정 이력이 1건 생긴다 (대타 승인 제외, §8 ④) |
| ML-2-INV-05 | 공개 후 추가·인계로 SCHEDULED가 되는 근무의 담당자는 그 근무지의 현재 소속이다 |
| ML-2-INV-06 | 퇴사 처리는 전부 반영되거나 전부 반영되지 않는다 |

## 8. API·DB·Frontend·알림 영향 (구현 시)

| 영역 | 내용 | 기존 계약 변경 |
|---|---|---|
| 퇴사 Command | 미리보기(미래 공식 근무 목록, 삭제될 DRAFT 목록) + 확정(근무별 선택 목록). 경로·주체는 ML-4 | 새 API |
| 공개 후 Command | 추가, 취소, 담당자 변경 (각 1개). 관리자 권한, 시작 전만 | 새 API. **G03-C-31 계약 후보 변경** (Decision에 "유지하는 제한 / 대체하는 부분"을 명시) |
| 공통 검증 | 주차 범위, 겹침, 다른 근무지 공식 근무 충돌, 불가능 시간, 현재 소속. 지금은 `ScheduleService` 안의 private 메서드라 공통 검증으로 분리 필요 (L365-520) | 내부 |
| ① 불가능 시간 의미 | 추천: 관리자 기능이므로 DRAFT·Publish와 같은 **경고 후 확인**. 대타 승인(거부)과 다름 → G03-D-21과 함께 기록 | 결정 필요 → **[G03-D-21](G03-D-21-unavailable-time.md) D21-1에서 사용자 잠정 결정 (2026-10-25)** |
| Shift Column | 취소 사유, 취소 시각, 취소 행위자 (nullable) | DB 변경 |
| 배정 이력 Table | 근무, 이전 담당자, 새 담당자, 사유(`MEMBER_LEFT`, `MANAGER_REASSIGNED`. `SUBSTITUTE_APPROVED`는 예약), 행위자, 시각 | 새 Table |
| ④ 대타 승인과의 관계 | 1차에는 관리자 변경·퇴사 인계만 기록. 대타 승인 변경은 기존 `SubstituteRequest`(APPROVED)로 추적. 이력 Table에 넣으려면 V4 Improvement | — |
| 조회 응답 | **(2026-10-23 수정, ML-4 조회 체계)** 관리자 근무표에 상태·취소·인계 정보 Field 추가. 직원 "내 근무"(`GET /schedules/me`)는 **현재 소속의 SCHEDULED만 유지** (AS-IS 계약 그대로). 취소·인계로 **나에게서 빠진 근무**는 새 API로 보여 준다 (ML-4 §6-1) | 응답 Field 추가, 새 API (AI-RULES §2) |
| Frontend | 관리자 근무표: 취소선·사유·시각, 인계 표시, 추가·취소·담당자 변경 UI, 퇴사 미리보기·결과 화면. 직원: "빠진 근무" 화면 (취소·인계 표시). `"CANCELED"` 철자 수정 | Frontend 변경 |
| 알림 | 새 Type 후보: 근무 배정, 근무가 다른 직원에게 넘어감, 근무 취소. 받는 사람: 새·이전 담당자(현재 소속일 때), 진행 중 대타 관련자(ML-3). **퇴사자는 받지 않음** | Enum 추가 (운영 DDL 가능성) |
| 운영 DDL 절차 | Migration 도구가 없다. nullable Column·새 Table·Enum 값 추가를 운영에 적용하고 되돌리는 절차를 팀이 정해야 한다. ML-3 B+(대타 Enum), 알림 Type과 묶어 한 번에 결정 | **새 팀 결정 후보** |

## 9. 동시성 (Lock 순서는 ML-5)

| 경합 | 지켜야 할 결과 |
|---|---|
| 퇴사 확정 vs 대타 승인 (퇴사자가 담당자가 되는 순간, 원문 ML-05 반례) | 퇴사 후 퇴사자 담당 미래 근무 0건 (INV-01) |
| 퇴사 확정 vs 다른 관리자의 추가·인계·취소 | 확정 시 재검사로 409 또는 순서대로 반영. 모순 없음 |
| 관리자 두 명의 같은 근무 동시 변경 | 최종 상태 하나. 이력 Row가 실제 변경 순서와 일치 |
| 퇴사 vs Publish / DRAFT 편집 | 퇴사자 근무가 공개되지 않음 (INV-02 + Publish 안전장치) |
| 담당자 변경 vs 대타 수락·승인 | ML-3 규칙과 함께. 현재 대타 승인은 Shift를 잠그지 않음 |

후보 기준점: DRAFT 편집·Publish가 이미 쓰는 **Schedule Row Lock**을 공개 후 Command도 먼저 잡는다. 최종 결정은 ML-5.

## 10. 검증 계약

모두 ④ `SPECIFIED` / ⑤ `NOT_RUN`. 승인 전 작성 금지. 등록 시 `V3-N-nn`.

| ID 후보 | Given | When | Then |
|---|---|---|---|
| V3-N-01 | 퇴사자 담당 미래 공식 근무 2건, 선택: 1건 인계(B), 1건 취소 | 퇴사 확정 | 1건 담당 B (같은 ID), 1건 CANCELLED (사유 `MEMBER_LEFT`, 시각, 원래 담당자 유지), 이력 1건, `leftAt` 기록 |
| V3-N-02 | 미래 공식 근무 2건 중 1건만 선택 | 퇴사 확정 | 거부, 아무것도 바뀌지 않음 |
| V3-N-03 | 미리보기 후 다른 관리자가 근무 1건 추가 | 미리보기 목록으로 확정 | 409, 아무것도 바뀌지 않음 |
| V3-N-04 | 인계 대상이 퇴사자·다른 근무지 구성원·겹치는 근무 보유 | 확정 | 거부 |
| V3-N-05 | 대타 승인으로 담당자가 이미 다른 직원인 근무 | 퇴사 | 그 근무는 목록에 없고 바뀌지 않음 |
| V3-N-06 | 퇴사자 DRAFT 근무 2건, 다른 직원 DRAFT 1건 | 퇴사 확정 | 퇴사자 2건 삭제, 1건 유지, 응답에 삭제 목록 |
| V3-N-07 | V3-N-06 이후 | Publish | 성공 |
| V3-N-08 | 퇴사자 Pattern | 퇴사 후 새 근무표 생성 | 퇴사자 근무 0건, Pattern Row는 남음 (기존 동작 회귀) |
| V3-N-09 | 공개 근무 | 관리자 취소 | CANCELLED + 사유 `MANAGER_CANCELLED`·시각·행위자, 직원 "빠진 근무" 목록에 취소로 표시("내 근무"에는 없음), 같은 시간 다른 직원 추가 가능 |
| V3-N-10 | CANCELLED 근무 | 다시 SCHEDULED로 바꾸는 모든 경로 | 없음 / 거부 |
| V3-N-11 | 공개 근무표 | 관리자 추가 (현재 구성원, 시작 전) | 새 SCHEDULED Row, 직원 "내 근무"에 표시, 알림 1건 |
| V3-N-12 | 공개 근무 | 관리자 담당자 변경 | 같은 ID, 새 담당자, 이력 1건, 알림 |
| V3-N-13 | 시작한 근무 | 취소·담당자 변경 | 거부 |
| V3-N-14 | 이력 저장 실패 주입 | 담당자 변경 | 담당자 변경도 Rollback |
| V3-N-15 | 알림 실패 주입 | 취소 | 취소는 성공, 화면 표시 유지 |
| V3-N-16 | 진행 중 대타가 있는 근무 | 취소·담당자 변경 | ML-3 규칙대로 (그전까지 정의 안 됨) |
| V3-N-17 | 관리자 두 명 | 같은 근무 동시 변경 | 최종 상태 하나, 이력 일치 (MySQL) |
| V3-N-18 | 퇴사와 대타 승인 | 동시 실행 | INV-01 유지 (MySQL, ML-5) |
| V3-N-19 | 퇴사와 Publish | 동시 실행 | 퇴사자 근무 미공개 (MySQL) |

회귀: 기존 Shift·Schedule·Pattern Test 기대값 변경 0. 단 "PUBLISHED Shift는 수정·삭제 불가" 계열 Test가 있다면 C-31 변경 Decision에 따라 다룬다 (현재 이 제한을 고정하는 Test는 삭제 Mock 수준만 확인됨, 작성자 로컬 조사).

## 11. 재검토 조건

- 분할 근무·인력 수요(필요 인원) 관리 요구가 생길 때 → N2·"빈 근무" 재검토.
- 시간 변경 요구가 실제로 자주 나올 때 → 원자적 교체 Command.
- 알림 유실이 실제 문제가 될 때 → G03-D-22·H5 (재시도, Outbox).
- G03-D-11이 채택되지 않을 때 → ML-2-A·B 무효, G03-D-27만 독립 검토.
- 운영 DDL 절차 결정 결과에 따라 → §8 DB 항목 범위 조정.

## 12. Cross-Domain 영향과 리뷰

| 대상 | 확인할 것 | 리뷰 |
|---|---|---|
| V3 Schedule/Shift (주관) | 전체 | 필수 |
| V2 Workplace | 퇴사 Command 효과, 인계 대상 현재 소속 판정 | 필수 |
| V4 Substitute | D27-5, 퇴사자 요청·후보·수락 근무 (ML-3), 대타 승인의 담당자 변경과 이력 관계 | 필수 |
| V5 Notification | 새 Type, 받는 사람, 유실 시 화면 기준 | 필수 |
| Frontend | §8 Frontend 행 | 필수 |
| 공통 설정 Owner | 운영 DDL 절차, 응답 Field 추가, C-31 변경 | 필수 |

## 13. 이전 잠정 결정과의 차이 (조용히 덮어쓰지 않음)

| 이전 (이 대화 앞부분, 2026-10-23) | 현재 | 이유 | 영향 |
|---|---|---|---|
| ML-2 A2: 퇴사자의 미래 공식 근무 **전부 CANCELLED** | 근무별 **인계 또는 취소** 선택 (ML-2-A) | 매장은 근무 자리를 두고 사람만 바꾸는 것이 보통. 취소 후 새로 만드는 것보다 이력·화면이 단순 | D-26 N2 불필요해짐, D-27에 담당자 변경 추가, 퇴사 Command 입력이 커짐 (ML-4) |
| D-27: 1차 = 퇴사 취소·추가·취소, **담당자 직접 변경 보류** | 담당자 변경을 1차에 포함, 시간 변경만 보류 | 보류 이유는 주로 시간 변경(P0 §16). 담당자 변경은 대타 승인과 같은 의미. 단 OPEN·ACCEPTED 대타 문제는 남음 (D27-5) | 배정 이력 Table, ML-3 규칙 |
| D-26 N2: 보충 필요 표시 + 보충 근무 연결 | **이번 범위에서 분리** (폐기 아님) | 인계로 대부분 해결. 분할 보충 없음 | 취소 후 보충 추적 불가 → §5 한계 |
| (원문 9431) DRAFT·Pattern hard delete | DRAFT 삭제, **Pattern 보존** | Pattern은 `leftAt`으로 이미 자동 제외 | 코드 변경 감소 |
| (원문 9431) 공개 근무표 추가를 퇴사 보충용 C-31 예외로 | **일반 공개 후 추가**로 정의하고 퇴사 보충에도 사용 | 같은 일을 하는 API를 두 번 만들지 않음 | G03-D-27 범위 확장 |

## 14. 후속 카드로 넘긴 항목

| 항목 | 카드 |
|---|---|
| 취소·인계 대상 근무의 진행 중·승인 대타 처리, 퇴사자가 후보·수락자인 경우 | ML-3 |
| 재가입 시 새 소속 Row. Pattern은 소속 Row(`member_id`)에 묶이고 근무표 생성은 현재 소속의 Pattern만 쓰므로, 옛 Pattern은 새 소속에 적용되지 않는다 (회귀 Test로 확인). 별도 위험: Pattern API의 관리자 확인(`RegularShiftPatternService` L237-249)이 `findByWorkplaceIdAndUserId`(`leftAt` 조건 없는 단건 조회)를 써서, 같은 사용자·근무지에 Row가 2개가 되면 단건 조회가 실패한다. 현재 소속 기준 조회로 바꾸고 Unique 변경과 함께 Test | ML-H2 |
| 퇴사 주체·권한, 진행 중 근무 거부, 퇴사 API 형식, 퇴사자 조회 | ML-4 |
| Lock 순서, 미리보기-확정 재검사 방식 | ML-5 |
| 불가능 시간 의미 통일 | G03-D-21 |
| 운영 DDL 적용·Rollback 절차 | 새 팀 결정 후보 |
| 원자적 교체, 시간 변경, N2, 관리자 취소 상세 사유 | G03-D-27·D-26 후속 |

## 14-1. ML-H2와의 연결 (2026-10-23)

- 퇴사 대상은 **현재 EMPLOYEE 소속만**이다 (ML-H2 H2-9). 퇴사 미리보기·확정은 대상이 MANAGER면 서버에서 거부한다.
- 퇴사 확정 화면에 **"초대 코드도 재발급"** 선택지를 둔다 (기본 해제, ML-H2 H2-5). 퇴사와 같은 Transaction에서 처리할지는 ML-4·5에서 정한다.
- 퇴사는 `leftAt`만 기록한다. 현재 소속 유일성 Column은 U2'면 DB가 자동 계산한다 (U3면 같은 Command에서 함께 갱신).
- (ML-4 연결) D27-6을 지키려면 근무 상세 API(`GET /shifts/{id}`)가 **현재 소속 본인의 CANCELLED 근무 상세를 허용**해야 한다. 지금은 `IS_CANCELED_SHIFT`로 거부한다 (ML-4 §8 ②).

## S1 결정 연결 (2026-10-25)

이 카드 본문은 이관 당시 그대로다. 아래는 2026-10-25에 기록한 사용자 잠정 결정(모두 ② `PROPOSED`, 팀 승인 전)과 이 카드의 연결 위치다. 결정 내용은 연결된 카드가 원본이다. 본문 수정이 필요한 곳은 S2-3 이후 해당 단계에서 고친다.

| 이 카드의 위치 | 연결된 결정 | 요약 |
|---|---|---|
| §8 ① 불가능 시간 의미 (결정 필요) | [G03-D-21](G03-D-21-unavailable-time.md) D21-1·5 | 관리자 직접 배정(공개 후 추가·담당자 변경, 퇴사 인계)은 경고 후 확인. 확인으로 넘어갈 수 있는 것은 불가능 시간 경고뿐 |
| 공개 후 담당자 변경과 대타 승인 거부 | [G03-D-21](G03-D-21-unavailable-time.md) D21-9 | 승인이 `SUB-006`으로 거부된 뒤 직접 배정하면 APPROVED를 만들지 않고 배정 이력·`SHIFT_CHANGED`로 기록 |
| 시간 변경 보류 (D27-2) | [G03-D-19](G03-D-19-expiry.md) §0-6 | 공개 근무 시간 직접 변경을 도입하면 G03-D-19를 다시 연다 |
| 공개 후 변경·퇴사 인계의 배포 | [G03-D-19](G03-D-19-expiry.md) §0-7, [IMPLEMENTATION-PRIORITY](../IMPLEMENTATION-PRIORITY.md) §3 묶음 B | 취소·인계는 ML-3 대타 종료와 ML-5 Shift 우선 Lock과 함께 배포 |
| 알림 유실 (§후속) | [G03-D-22](G03-D-22-notification-delivery.md) D22-1·3 | 알림은 최선 노력. 근무 취소·인계는 "빠진 근무"가 대체 조회 화면 |
| 공개 후 추가·담당자 변경의 불가능 시간 확인 | [G03-D-05](G03-D-05-confirmation-response.md) D05-2·8 | 새 Command는 충돌 목록 형식(1건)을 쓴다. 기존 DRAFT·Publish의 boolean 확인은 그대로 |
| D27-5 "무조건 허용하지 않는다" | [API-CATALOG](../../contract/API-CATALOG.md) §4 | "상태별 처리를 보장할 때 허용"으로 해석 (사용자 잠정 결정 2026-10-25). 원래 문장은 그대로 둠 |

## 15. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-23 | 초안. ML-2-A·B, D-26, D-27 사용자 잠정 결정 통합. 이전 잠정 결정과의 차이는 §13 |
| 2026-10-23 | §8 조회 응답 수정: "내 근무가 CANCELLED도 반환" → "내 근무는 AS-IS, 빠진 근무는 새 API" (ML-4 §6-1). V3-N-09 기대값 수정. §14-1에 ML-H2·ML-4 연결 |
| 2026-10-25 | 저장소 이관 (메타데이터만 수정). "S1 결정 연결" 절 추가, 머리 표·§3 경로를 링크로 변경, §8 ① "결정 필요" 칸에 G03-D-21 결정 연결 표시 (본문 판단 변경 없음) |
