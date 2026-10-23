<!-- 참고 자료 원문 보존본. 머리말 아래 본문은 대화 Log 원문 그대로이며 수정하지 않았다. 본문 SHA-256: 3856b3f836dd80c6524ce11e271b371bb4e9bd5a30dbc36ecff1d2c410b8c357 -->

# ML-DECISION-01-FINAL — 퇴사(Membership 종료) Command 패키지

> **과거 제안 원문이다. 현행 계약이 아니며, 팀 승인 전이다.**
> - ② 정책: `PROPOSED` (원문 작성 시점). `DECISION-LOG`의 관련 행(G03-D-11~16·26·27)은 모두 `PENDING`이다. 구현 지시가 아니다 (`docs/ai/AI-RULES.md` §3).
> - 출처: 작성자 로컬 AI 대화 Log (Claude Code 세션, 2차 분석 작업공간), 1-based line 9431, 2026-10-22T07:26:20.600Z. Log 파일은 저장소에 없다 (`docs/references/README.md` §6).
> - 작성 기준 코드: 2차 `origin/develop` `8da2bec` (Production Java는 3차 `develop`과 같다). 정적 분석이며 Test는 실행하지 않았다.
> - 본문 안의 "추천", "MUST", "정합니다" 같은 표현은 작성 당시 AI 제안의 어조다. 결정이 아니다.
> - 이후 관계 (본문을 읽기 전에 확인):
>   - 이 원문의 입력은 작성자가 제시한 잠정 정책 14개다 (대화 Log의 같은 세션, 이 원문 직전 사용자 메시지). 정책 14: "재가입은 새 Membership 이력을 선호하지만 MVP 포함 여부와 DB Unique 변경은 별도 미정이다."
>   - **대타 정리 방식** (퇴사자 PENDING 후보를 `REJECTED`로 재사용, C-47 예외) → [`ML-SUB-STATE-01.md`](ML-SUB-STATE-01.md)의 B+ (`INELIGIBLE` / `CANDIDATE_INELIGIBLE`)로 **대체됨**
>   - **Lock 순서** (Schedule → Request → User) → [`ML-CONCURRENCY-01-R.md`](ML-CONCURRENCY-01-R.md)의 Membership Guard로 **대체됨**
>   - **Membership 이력·재가입** (원문에서 재가입은 UNKNOWN, ML-Q03) → 현재 검토 카드 [`ML-H1`](../../decisions/proposals/ML-H1-membership-history.md)
>   - 진행 중 근무 거부, 미래 공식 Shift `CANCELLED`, 공개 근무표 추가(C-31 예외), 종료 소속 조회 → 후속 카드 ML-2·ML-4에서 다시 결정 (아직 카드 없음)

---

ML-DECISION-01-FINAL 검증 결과입니다. 이번 정책 패키지는 기존 서비스와 모순 없이 운영할 수 있습니다. 단 조건이 세 가지 있습니다.

1. **공개 근무표에 근무를 추가하는 기능**에 대해 C-31 예외를 승인받아야 합니다.
2. 운영 DB와 Frontend가 `CANCELLED`를 제대로 처리하는지 확인해야 합니다.
3. 퇴사가 잠가야 할 Lock 대상을 정해야 합니다.

읽기 전용으로 검토했습니다 (`origin/develop` `8da2bec`). 정책 2번(진행 중 근무가 있으면 거부)은 사용자 선택을 따랐습니다.

**이전 보고 정정:** 앞선 카드에서 "관리자 close 때 PENDING 후보도 정리된다 (L498~504)"고 썼는데 **틀렸습니다.** 그 코드는 알림 수신자를 고르는 부분입니다. close는 Candidate 상태를 바꾸지 않습니다 (C-49 "Shift / Candidate 불변"과 같음). 남은 PENDING 후보는 요청이 OPEN이 아니라서 목록에서만 숨겨집니다.

## 1. 정책별 판정
| # | 정책 | 판정 | 근거·조건 |
|---|---|---|---|
| 1 | 관리자가 EMPLOYEE를 즉시 퇴사 처리 | PROPOSED_TO_BE | 퇴사 Command 0건 (VERIFIED_AS_IS). `requireManager` 재사용 가능 |
| 2 | 진행 중 공식 근무가 있으면 거부하고 종료 시각 안내 | PROPOSED_TO_BE | C-50 기준과 같음. 기존 동작과 충돌 없음 |
| 3 | 과거 근무 보존 | VERIFIED_AS_IS (삭제 경로 없음) | — |
| 4 | 미래 근무를 `CANCELLED`로 바꾸고 담당자·시간 보존 | PROPOSED_TO_BE | Enum 값은 있음. 운영 Column 정의는 UNKNOWN |
| 5 | 취소 = "배정 취소". 공백 판단은 관리자 몫 | PROPOSED_TO_BE (G03-D-26) | 충돌 Query는 이미 `SCHEDULED`만 셈 → 취소 즉시 충돌에서 빠짐 (VERIFIED_AS_IS) |
| 6 | 공개 근무표의 제한적 취소·추가 | PROPOSED_TO_BE, **C-31 예외** | 아래 §4 |
| 7 | 새 근무는 독립된 Row, 원래 근무와의 연결은 후속 | PROPOSED_TO_BE | Schema 변경 없음 |
| 8 | Pattern·DRAFT 정리 | PROPOSED_TO_BE | 기존 Hard Delete 재사용 (C-17, `ShiftService.delete`) |
| 9 | 대타 상태별 정리, 기존 Enum 재사용 | PROPOSED_TO_BE | §3. 새 Enum 없이 가능 |
| 10 | 시작된 요청은 DB에 OPEN/ACCEPTED로 두고 시각 기준으로 EXPIRED 판단 | **VERIFIED_AS_IS** | 모든 Command와 목록이 `startAt > now`로 거름. 응답에서 EXPIRED 표시 (`SentSubstituteRequestResponse` L34) |
| 11 | 스케줄러 없음 | VERIFIED_AS_IS (인프라 0) | 이 패키지에 스케줄러가 필요한 동작 없음 |
| 12 | 퇴사자 본인의 결과 조회 | PROPOSED_TO_BE | 알림은 숨겨지므로 (NOT-02/03) 별도 조회 API 필요 |
| 13 | 본인 탈퇴, 관리자 퇴사, 예약 퇴사, 계정 탈퇴는 제외 | PROPOSED_TO_BE | — |
| 14 | 재가입 | UNKNOWN (ML-Q03) | 이 패키지와 독립 |

## 2. 최종 권장 MVP 정책 패키지 (PROPOSED)
- **Command 1개: 퇴사 실행** (영향 조회 겸용)
  - 진행 중 공식 근무가 있으면 거부합니다.
  - 없으면 하나의 Transaction에서 처리합니다: 대타 정리 → 미래 근무 취소 → DRAFT·Pattern 삭제 → `leftAt = now`.
- **Command 1개: 공개 근무표에 근무 추가** (C)
- **조회 1개: 종료된 내 소속**
- **관리자의 단독 공개 근무 취소(B)는 MVP에서 제외하고, 퇴사 Command 안에서만 사용합니다.**
  - 이유: 현재 직원의 근무를 취소하면 "내 근무"가 `SCHEDULED`만 보여 주므로 **그 직원 화면에서 알림 없이 사라집니다.**
  - 이것을 알리려면 알림 Type을 추가해야 합니다 (Enum 변경).

## 3. 상태 전이표 (모두 PROPOSED, 실행 주체는 퇴사 Command를 실행한 관리자)
| 대상 | 사전조건 | 변경 전 → 후 | 이력 | P0 예외 |
|---|---|---|---|---|
| Membership | 같은 근무지 현재 MANAGER가 실행, 대상은 현재 EMPLOYEE, 진행 중 공식 근무 0 | `leftAt` null → now. Role·`joinedAt` 유지 | Row 보존 | 없음 (새 Command, C-11 유지) |
| Pattern | 대상의 Pattern | 삭제 (Hard Delete) | 없음. 기존 C-17과 같음 | 없음 |
| DRAFT Shift | DRAFT Schedule 안의 대상 Shift (시각 무관) | 삭제 | 없음. DRAFT는 공식 기록이 아님 | 없음 (DRAFT CRUD 범위) |
| 미래 공식 근무 | PUBLISHED ∧ SCHEDULED ∧ `startAt > now` | `SCHEDULED` → `CANCELLED`. `member`·시간 유지 | Row 보존 | **C-31 예외** |
| Request (대상이 요청자, 미래) | OPEN / ACCEPTED | → `CLOSED` / `MANAGER_CLOSED`. 근무 취소보다 **먼저** 처리 | 보존 | 없음 (C-49와 같은 동작) |
| Candidate (대상이 PENDING, 미래) | 요청 OPEN | `PENDING` → `REJECTED`. 남은 PENDING이 없으면 요청을 `CLOSED` / `ALL_CANDIDATES_REJECTED` | 보존 | **C-47 예외** (본인 응답이 아닌 시스템 거절) |
| Candidate (대상이 ACCEPTED, 미래) | 요청 ACCEPTED | 요청 → `CLOSED` / `MANAGER_CLOSED`. Candidate는 변경 없음 (C-49) | 보존 | 없음 |
| APPROVED로 대상이 담당자가 된 근무 | 미래 공식 근무 | 위 "미래 공식 근무"와 같이 취소. Request는 `APPROVED`로 유지 | 보존 | C-31 (같은 예외) |
| 시작된 요청 | `startAt ≤ now` | **변경 없음.** 화면상 EXPIRED, 퇴사를 막지 않음 | 보존 | 없음 |

**API 응답 (형식 미정)**
- 성공: 처리 건수 요약 (취소된 근무, 종료된 요청, 거절 처리된 후보, 삭제된 DRAFT·Pattern 수)
- 진행 중 근무로 거부: 409 계열 + 해당 근무의 `endAt`

## 4. API별 계약 초안 (경로·이름·ErrorCode는 확정하지 않음)
**① 퇴사 실행**
- **권한:** 같은 근무지의 현재 MANAGER. 다른 근무지 관리자 → `WPL-003`, 직원 → `WPL-004` (C-13 값 재사용).
- **대상:** 같은 근무지의 현재 EMPLOYEE Membership.
- **거부:** 대상이 MANAGER, 이미 퇴사함, 다른 근무지 소속, 진행 중 공식 근무 있음.
- **확인 Flag:** 없으면 영향 목록만 돌려줍니다. 있으면 실행합니다.
- **시각:** 명령 시작 때 `now` 1회.
- **원자성:** 전부 반영되거나 전부 취소됩니다.

**Lock 후보 (확정 아님)**
- 순서: 대상이 포함된 DRAFT·PUBLISHED Schedule → 관련 Request → 대상 User → 다시 읽고 판단 → 변경.
- 근거: Publish(L305 Schedule Lock 후 구성원 조회), APPROVE(L236 Request Lock 후 수락자 조회), ACCEPT(L116 Request Lock 후 L136 `leftAt` 검사)는 모두 **처음 잡는 Lock 이후에** 구성원 정보를 읽습니다.
- 그래서 퇴사가 같은 Schedule·Request를 먼저 잠그면 세 경로와 순서가 정해집니다.
- 이것은 develop의 전역 READ COMMITTED 아래에서만 성립하는 추정입니다 (DL-002). H6로 검증해야 합니다.

**② 공개 근무표에 근무 추가 (C)**
- **권한:** 현재 MANAGER.
- **사전조건:**
  - Schedule이 PUBLISHED, `startAt > now`
  - 같은 날, 해당 주차 안, `start < end`
  - 대상은 같은 근무지의 현재 구성원 (C-22와 같음)
- **검증:**
  - 근무표 내부 같은 구성원의 겹침 (`existsOverlappingInSchedule`, `CANCELLED` 제외)
  - User 기준 공식 근무 충돌 (C-30)
  - 확정된 대타 약속 충돌 (APPROVE와 같은 수준)
  - 불가능 시간은 Warning + confirm (C-24)
- **Lock:** Schedule `FOR UPDATE` → 대상 User `FOR UPDATE` → 다시 검증. Publish와 같은 순서입니다.
- **중복 요청:** 같은 요청이 두 번 오면 Schedule Lock으로 순서가 정해지고, 두 번째는 내부 겹침으로 거부됩니다.
- **결과:** 새 `SCHEDULED` Row. 원래 근무와 연결하지 않습니다.
- **사유 입력:** 받지 않습니다 (Column 없음, 후속).
- **알림:** MVP에서는 없습니다. 직원은 "내 근무"에서 확인합니다. 아래 질문 3 참조.
- **예외 범위:** C-31 예외는 "PUBLISHED 주차에 시작 전 근무를 새 Row로 추가"까지만입니다. 기존 Row는 수정하지 않습니다.

**③ 종료된 내 소속 조회**
- **권한:** 로그인한 User 본인.
- **응답:** 근무지 이름, `joinedAt`, `leftAt`.
- 기존 NOT-02/03과 C-12는 바꾸지 않습니다.

**제외:** A(공개 근무 자유 수정)는 계속 금지합니다 (C-31). B(단독 취소)는 후속입니다.

## 5. 사용자 흐름 완결성 점검
| 행동 | 기존 API | 신규 | 최종 DB 상태 / 다음 행동 |
|---|---|---|---|
| 퇴사 영향 조회 | 없음 | ① (확인 없이 호출) | 변경 없음 → 관리자가 내용을 확인 |
| 퇴사 실행 | 없음 | ① (확인 후 호출) | §3 상태 전이 → 근무표 확인 |
| 진행 중 근무로 거부됨 | — | ① | 변경 없음 + `endAt` 안내 → 근무가 끝난 뒤 다시 시도 |
| 취소된 근무 조회 | `GET /workplaces/{id}/schedules` (status·담당자 이름 포함, VERIFIED_AS_IS) | Frontend 표시만 필요 | → 근무 추가 |
| 공개 근무표에 근무 추가 | 없음 (**DRAFT만 가능, 주차당 Schedule 1개**) | ② | 새 Row `SCHEDULED` → 직원 "내 근무"에 표시 |
| 대타 정리 | close만 있음 | ① 안에서 처리 | §3 |
| 퇴사 결과 확인 (퇴사자) | 없음 (알림은 숨김) | ③ | — |
| 실패·중복 | — | ①·② | 원자성으로 다시 시도 가능. 이미 퇴사한 대상은 409 또는 no-op (ML-Q21에서 결정) |

②가 승인되지 않으면 "근무 추가" 행이 **BLOCKER**가 됩니다.

## 6. 핵심 테스트 매트릭스
특별히 적지 않은 시간은 2026-10-26, `Clock.fixed`이고 근무 ①은 09:00~14:00입니다.

| # | 입력 | 실행 | 기대 응답 | DB 최종 상태 |
|---|---|---|---|---|
| T1 | now 11:00, 근무 ① 진행 중 | ① 실행 | 409 계열 + `endAt` 14:00 | `leftAt` null, 모든 Row 변경 없음 |
| T2 | now 14:00 (`endAt == now`), 18~22시 근무 ② | ① 실행 | 200 | ① 변경 없음, ② `CANCELLED`(담당자 유지), `leftAt = 14:00` |
| T3 | now 09:00 (`startAt == now`) | ① 실행 | 409 | 변경 없음 |
| T4 | T2 이후, 같은 User가 다른 근무지 18~22시 DRAFT 배정 → Publish | 기존 API | 성공 | 충돌 없음 (`CANCELLED` 제외) |
| T5 | T2 이후 관리자 근무표 조회 | `GET .../schedules` | 200, ② `status = CANCELLED`, 담당자 이름 = 퇴사자 | — |
| T6 | 다른 직원을 18~22시로 추가 | ② | 200 | 새 Row `SCHEDULED`. ②는 `CANCELLED`로 유지 |
| T7 | 같은 직원 같은 시간 추가 2번째 / 다른 근무지 공식 근무와 겹침 / `startAt ≤ now` / DRAFT 근무표 대상 | ② | 각각 거부 | 새 Row 0 |
| T8 | 퇴사자가 요청자인 미래 OPEN 요청, 후보 2 | ① 실행 | 200 | 요청 `MANAGER_CLOSED`, Candidate 변경 없음(PENDING), 근무 `CANCELLED`. 처리 순서를 바꾸면 실패 |
| T9 | 퇴사자가 마지막 PENDING 후보 / 다른 PENDING이 남은 경우 | ① 실행 | 200 | `REJECTED` + 요청 `ALL_CANDIDATES_REJECTED` / 요청 OPEN 유지 |
| T10 | 퇴사자가 ACCEPTED 수락자 | ① 후 요청자가 대타 재요청 | 200 / 재요청 201 | 요청 `MANAGER_CLOSED`, 원래 근무는 요청자에게 유지 |
| T11 | 시작된 근무의 OPEN 요청 (퇴사자가 요청자) | ① 실행 | 200 (진행 중 근무가 끝난 경우) | 요청 OPEN 유지, 보낸 요청 응답에 EXPIRED 표시 |
| T12 | 권한: 다른 근무지 관리자 / 직원 / 대상이 MANAGER / 이미 퇴사 | ① 실행 | `WPL-003` / `WPL-004` / 409 / ML-Q21 | 변경 없음 |
| T13 | 중간 단계 실패 주입 | ① 실행 | 실패 | 모든 Entity가 실행 전 상태 (#78 Rollback Test 방식) |
| T14 | 동시성 (H6): 퇴사 vs Publish, 퇴사 vs APPROVE(퇴사자가 수락자), 퇴사 vs ACCEPT, 퇴사 2건 동시 | 결정적 Harness | 한쪽만 성공하거나 순서대로 처리 | 퇴사자가 담당인 `SCHEDULED` 미래 근무 0, 퇴사자가 ACCEPTED인 활성 요청 0 |
| T15 | 다른 근무지 비영향 | ① 실행 | — | 같은 User의 다른 Membership·Role·근무 변경 없음 |
| T16 | 퇴사자 결과 조회 | ③ | 200, `leftAt` 포함 | — |

## 7. 필요한 신규 Command·조회
- **①** 퇴사 실행 (영향 조회 겸용)
- **②** 공개 근무표에 근무 추가
- **③** 종료된 내 소속 조회
- **Entity 메서드:** `WorkplaceMember.leave(now)`, `Shift.cancel()`
- **ErrorCode:** 진행 중 근무 있음, 대상이 MANAGER, 이미 퇴사 (이름·Status 미정)
- **Frontend:** 근무표의 `CANCELLED` 표시, 근무 추가 화면, 퇴사 확인 Dialog, 종료된 소속 화면, `my-shifts`의 `"CANCELED"` 철자 정리

## 8. 팀이 승인해야 할 P0 예외 목록
| 계약 | 영향 | 구분 |
|---|---|---|
| **C-31** | 퇴사로 미래 공식 근무 취소, PUBLISHED 주차에 새 Row 추가 | **예외 승인 필요** |
| **C-47** | PENDING 후보를 본인 대신 시스템이 `REJECTED` 처리 | **예외 승인 필요** |
| G03-D-26 | `CANCELLED` = 배정 취소. 처음으로 이 상태가 실제로 쓰임 | 결정 필요 (C-29 정의 자체는 유지) |
| C-11, C-12, C-13, C-29, C-42, C-49, C-50, NOT-02/03 | 변경 없음 | C-42의 요청자 `leftAt` 검사 공백(G03-D-15)은 퇴사자에게 `SCHEDULED` 미래 근무가 남지 않으므로 사실상 발생하지 않음 |
| C-10 | 변경 없음 (재가입 결정 전까지) | — |

위 신규 동작은 모두 PROPOSED_TO_BE이며 EXISTING_P0_POLICY로 기록하지 않습니다.

## 9. 치명적 BLOCKER (3개)
1. **C-31 예외(②) 승인:** 승인되지 않으면 공개 주차의 빈 근무를 앱에서 채울 수 없습니다.
2. **운영 DB의 `shift.status` Column과 Frontend 표시:** 운영 Column에 `CANCELLED`가 허용되는지 UNKNOWN입니다. Frontend는 Shift 단위 `CANCELLED` 분기가 없습니다 (정적 확인, `schedule/page.tsx`). 둘 중 하나라도 안 되면 취소된 근무가 정상 근무처럼 보이거나 저장이 실패합니다.
3. **동시성의 남은 위험**
   - Lock 순서가 성립하는 근거가 전역 READ COMMITTED입니다 (DL-002).
   - **대타 요청 생성은 후보의 `leftAt`을 Lock 없이 읽습니다.** 그래서 퇴사와 동시에 퇴사자가 새 PENDING 후보로 들어갈 수 있습니다.
   - 이 경우 피해는 다음과 같습니다: 퇴사자는 응답할 수 없고, 요청이 OPEN으로 남거나 마지막 거절 후에도 자동 종료되지 않습니다.
   - 관리자가 close하면 회복됩니다. 이 위험을 수용할지, H6로 측정할지 정해야 합니다.

## 10. Decision Log·Contract에 기록할 요약 (PROPOSED)
> **ML-DECISION-01 (G03-D-11/12/14/15/26/27 연결):** 3차 MVP에서 현재 MANAGER는 같은 근무지의 현재 EMPLOYEE를 즉시 퇴사 처리할 수 있다. 진행 중 공식 근무가 있으면 거부하고 종료 시각을 알린다. 퇴사 시 하나의 Transaction에서 다음을 처리한다: 미래 대타 요청 종료(`MANAGER_CLOSED`), 퇴사자의 PENDING 후보 거절(`REJECTED`), 미래 공식 근무를 `CANCELLED`로 변경(담당자·시간 유지), DRAFT Shift·Pattern 삭제, `leftAt = now` 설정. 시작된 대타 요청은 DB 상태를 유지하고 시각 기준으로 EXPIRED로 판단하며, 스케줄러는 두지 않는다. 관리자는 PUBLISHED 주차에 시작 전 근무를 새 Row로 추가할 수 있다. 기존 공개 근무의 자유 수정과 단독 취소는 허용하지 않는다. 퇴사자는 종료된 소속 조회로 결과를 확인한다. C-31·C-47 예외, G03-D-26 의미 결정이 필요하다. 상태: **PROPOSED**.

- **CONTRACT-BASELINE:** 승인 후 `CB-WP-*`(①·③)와 `CB-SCH-*`(②, `CANCELLED` 전이)를 추가합니다.

## 사용자와 결정할 질문 (3개)
1. **퇴사자 PENDING 후보의 `REJECTED` 재사용:** 퇴사자가 마지막 후보이면 요청자에게 "대체 근무 요청을 받은 모든 수신자가 거절했습니다." 알림이 갑니다. 실제로는 퇴사 때문입니다. 이 표현을 수용할까요, 아니면 이 경우만 요청을 `MANAGER_CLOSED`("관리자가 대체 근무 요청을 종료했습니다.")로 끝낼까요? 둘 다 새 Enum은 필요 없습니다.
2. **관리자의 단독 공개 근무 취소(B)를 MVP에서 빼는 것에 동의하나요?** 넣으려면 현재 직원 화면에서 근무가 조용히 사라지지 않도록 알림 Type 추가(Enum 변경)가 함께 필요합니다.
3. **공개 근무 추가(②)를 알림 없이 진행할까요?** 직원은 "내 근무"에서 확인합니다. 기존 `SCHEDULE_PUBLISHED`("새로운 주간 근무표가 공개되었습니다.")를 재사용하면 의미가 맞지 않습니다.
