<!-- 참고 자료 원문 보존본. 머리말 아래 본문은 대화 Log 원문 그대로이며 수정하지 않았다. 본문 SHA-256: 76caf22eea69d5ccbd6db860e1697b802ebf2cba29e8adcdcd1ce49aeb32b590 -->

# ML-CONCURRENCY-01-R — 퇴사 경합 Lock 설계 (Membership Guard) 반례 검증

> **과거 제안 원문이다. 현행 계약이 아니며, 팀 승인 전이다.**
> - ② 정책: `PROPOSED` (원문 작성 시점). `DECISION-LOG`의 관련 행(G03-D-11~16·26·27)은 모두 `PENDING`이다. 구현 지시가 아니다 (`docs/ai/AI-RULES.md` §3).
> - 출처: 작성자 로컬 AI 대화 Log (Claude Code 세션, 2차 분석 작업공간), 1-based line 9678, 2026-10-22T08:49:49.716Z. Log 파일은 저장소에 없다 (`docs/references/README.md` §6).
> - 작성 기준 코드: 2차 `origin/develop` `8da2bec` (Production Java는 3차 `develop`과 같다). 정적 분석이며 Test는 실행하지 않았다.
> - 본문 안의 "추천", "MUST", "정합니다" 같은 표현은 작성 당시 AI 제안의 어조다. 결정이 아니다.
> - 이후 관계 (본문을 읽기 전에 확인):
>   - 직전안: ML-CONCURRENCY-01 → 이 문서가 반례 검증본
>   - [`ML-DECISION-01-FINAL.md`](ML-DECISION-01-FINAL.md)의 User Lock 방식을 대체한다 (Guard = Membership Row, 다른 Command `FOR SHARE` / 퇴사 `FOR UPDATE`)
>   - "Deadlock 없음"은 **실행 근거 없음** (H6 ML-01~14 미실행, ⑤ `NOT_RUN`). `DECISION-LOG` DL-009·DL-002가 먼저 결정돼야 한다
>   - Lock 종류·순서 변경은 `docs/ai/AI-RULES.md` §2의 승인 대상이다. 후속 카드 ML-5에서 다시 결정 (아직 카드 없음)

---

**판정: B 방식 YES_WITH_CONDITIONS (유지 가능, 단 세 곳을 고쳐야 함).** 이전 보고에는 반례로 틀린 부분이 네 군데 있었습니다.

1. **Guard Lock 대상을 User에서 Membership Row로 바꿉니다.**
   - User Lock만으로는 Lock을 쓰지 않는 DRAFT·Pattern 저장이나 FK 확인을 막지 못합니다.
   - 다른 근무지 작업까지 불필요하게 막습니다.
2. **"Lock 이후 재검증"은 Entity Getter로 하면 안 됩니다.**
   - 이미 메모리(Persistence Context)에 올라온 Entity는 같은 SELECT를 다시 실행해도 갱신되지 않습니다.
   - 반드시 잠그는 조회로 DB의 최신 Commit 값을 읽어야 합니다.
3. **퇴사의 영향 범위를 "퇴사자의 모든 미래 Shift (DRAFT·PUBLISHED 무관)"로 정의합니다.**
   - 이렇게 해야 퇴사 도중 Publish가 끼어들어도 불필요하게 중단되지 않습니다.
4. **H6-ML-05 시나리오는 실제 Lock 순서와 모순이었습니다.** §6에서 수정했습니다.

**"Deadlock 없음"은 아직 증명되지 않았습니다 (UNKNOWN).** 아래는 논증일 뿐입니다. 그 근거인 InnoDB FK 확인 Lock과 Hibernate Flush 순서도 이 환경에서 실측하지 않았습니다. H6 실행 전까지 UNKNOWN입니다.

읽기 전용으로 검토했고 Test는 실행하지 않았습니다 (`origin/develop` `8da2bec`).

## 1. 실제 Lock 그래프
**표기**
- `→`는 "앞의 Lock을 잡은 채로 뒤의 Lock을 얻는다"는 뜻입니다.
- `X`는 배타 Lock, `S`는 공유 Lock입니다.
- 노드 약자: S = Schedule, R = SubstituteRequest, Sh = Shift, U = User, M = WorkplaceMember, C = SubstituteCandidate, P = RegularShiftPattern.

**출처 구분**
- 명시 Lock: VERIFIED_AS_IS (`@Lock` Query와 호출 위치).
- 암묵 Lock은 다음 동작에 근거한 **추정**입니다.
  - InnoDB는 자식 Row를 INSERT하거나 FK Column을 UPDATE할 때 부모 Row에 `S` Lock을 잡습니다.
  - Hibernate는 IDENTITY 전략이면 `save` 즉시 INSERT합니다.
  - Dirty 변경은 Commit 시점 Flush, 또는 같은 Table을 조회하는 JPQL 직전 AUTO Flush 때 UPDATE됩니다.

| Command | 실제 순서 (명시 Lock / 암묵 Lock) |
|---|---|
| Publish | `S(X, L305) → U(X, id 오름차순, L572~595)`. Commit 때 S UPDATE (이미 보유) |
| DRAFT 생성 | `S(X, L200) → [INSERT Sh] → M(S, FK)` |
| DRAFT 수정 | `S(X) → [Flush: UPDATE Sh(X)] → 새 담당자 M(S, FK)` |
| DRAFT 삭제 | `S(X) → Sh(X, DELETE)` |
| 대타 생성 | `요청자 Sh(X, L132) → [INSERT R] → 요청자 M(S, FK) → [INSERT C×N] → 후보 M(S, FK)`. **후보 Membership은 FK 확인뿐이고 현재 구성원 재확인이 없음** |
| ACCEPT | `R(X, L116) → U(X, L194) → [Flush: UPDATE C(X), R]` |
| REJECT | `R(X) → [C 변경 후 남은 후보 Query 직전 AUTO Flush: UPDATE C(X)] → R UPDATE` |
| APPROVE | `R(X, L236) → U(X, L290) → [Flush: UPDATE Sh(X)] → 수락자 M(S, FK)`. **U 다음에 Sh**를 잡음 |
| Manager CLOSE | `R(X, L363) → R UPDATE` |
| 알림 생성 (`AFTER_COMMIT`, `REQUIRES_NEW`) | `[INSERT Notification] → 수신자 M(S, FK)`. 다른 Lock 없음 |
| Pattern 등록·수정 | 명시 Lock 없음. INSERT 시 `M(S, FK)` |

**이전 규칙과 맞지 않는 곳**
- 이전 규칙 `Schedule → Request → Shift → User`는 APPROVE의 `U → Sh`와 충돌합니다.
- 그래서 **"전역 순서"만으로는 Deadlock이 없다고 증명할 수 없습니다.** 실제 안전 근거는 아래 R-Gating입니다.
- **R-Gating:** APPROVE는 Shift를 항상 자기 Request Lock을 잡은 상태에서만 바꿉니다. 퇴사는 그 Shift를 잠그기 **전에** 같은 Request를 먼저 잠급니다.

## 2. 두 Transaction 반례 검증
퇴사는 §4의 수정 설계를 기준으로 합니다.

| # | Lock 순서와 대기 위치 | 오래된 Entity 위험 | 수정하지 않으면 최종 상태 | 깨지는 불변식 | 최소 수정 |
|---|---|---|---|---|---|
| A | 대타 생성이 X를 현재 구성원으로 조회 → 퇴사가 `M(X)`를 X Lock으로 잡고 Commit → 생성의 Candidate INSERT가 FK `M(S)`에서 **대기했다가 통과** | 생성은 처음 읽은 값만 가지고 있음 | 퇴사자 X가 PENDING 후보 | INV-L1 | 생성이 Request INSERT **전에** 후보 Membership을 `FOR SHARE`로 잠그고 현재 구성원만 다시 고름 |
| B | 퇴사가 영향 대상을 먼저 조회한 뒤 새 Request·Candidate가 Commit됨 | — | 퇴사가 그 대상을 놓침 | INV-L1 | 퇴사는 `M(X)` X Lock을 잡은 **뒤에** 다시 조회하고, 처음 범위에 없던 ID가 있으면 409로 중단 |
| C | Publish가 S Lock을 잡고 구성원을 검증(L345)한 뒤 U Lock 대기 | Publish가 이미 읽은 Membership Entity | S가 퇴사 범위에 있으면 퇴사가 S를 먼저 잡아 순서가 정해짐. 범위 밖 S'(퇴사 조회 이후 만든 DRAFT)이면 퇴사자의 공식 근무가 생김 | INV-L2 | Publish는 U Lock 이후 담당자 Membership을 `FOR SHARE`로 다시 확인 |
| D | APPROVE가 R·U Lock을 잡고 수락자를 검증(L278) | 같음 | 같은 R이면 R-Gating으로 차단됨. 수락자 검증 자체는 U Lock 이전 | INV-L4 | U Lock 이후 수락자 Membership을 `FOR SHARE`로 다시 확인 |
| E | APPROVE `R → U → Sh` vs 퇴사 | — | 이전 설계(퇴사가 `Sh → U` 순서)에서도 퇴사가 R을 먼저 잡으므로 순환은 생기지 않음. **단 퇴사가 R보다 Sh를 먼저 잡으면 Deadlock** | — | 퇴사 순서를 `S → R → Sh → M`으로 고정. R-Gating을 계약으로 명시 |
| F | 같은 User가 W1·W2에 소속. 이전 설계는 퇴사가 `U(X)`를 잠가 **W2의 Publish·ACCEPT까지 대기** | — | 정합성은 맞지만 다른 근무지에 불필요한 대기 | (성능) | Guard를 `M(X, W1)`으로 바꿔 W2와 무관하게 함. W2 Publish가 W1의 취소 직전 상태를 읽으면 "충돌로 거부"되는 쪽이므로 안전 |
| G | DRAFT 생성이 S' Lock + `M(X)` S(FK)로 INSERT. 퇴사는 `M(X)` X Lock을 기다리거나, 퇴사가 먼저면 DRAFT INSERT가 기다렸다 통과 | DRAFT 생성은 처음 읽은 값 | 퇴사자의 DRAFT가 남음 → S' Publish가 `SCH-006`으로 막힘 (공식 근무는 아님) | DRAFT 위생 | (SHOULD) DRAFT 생성·수정에 담당자 Membership `FOR SHARE` 재확인. Pattern 등록도 같은 방식 |

## 3. Lock 이후 재검증 방식
| 방식 | READ COMMITTED에서 최신 값을 보는가 | JPA 1차 캐시 | REPEATABLE READ에서 | 판정 |
|---|---|---|---|---|
| 1. User Lock + Repository로 Entity 다시 조회 | SQL은 최신 Row를 읽음 | **이미 관리 중인 Entity면 결과 Row를 버리고 기존 객체를 반환** → 오래된 `leftAt` | Snapshot | ✗ |
| 2. User Lock + 새 Scalar Query 또는 `refresh` | 최신 | Scalar·`refresh`는 캐시를 우회 | 일반 조회는 Snapshot (stale) | READ COMMITTED에 의존 |
| **3. Membership Row를 잠그는 조회** | 최신 (InnoDB의 잠그는 조회는 항상 최신 Commit 값을 읽음) | Scalar 결과로 받으면 캐시 무관 | **격리 수준과 무관** | **추천** |

**추천 (PROPOSED)**
- 참여자를 확정하는 Command는 `SELECT id FROM <workplace_member 테이블> WHERE id IN (...) AND left_at IS NULL FOR SHARE`처럼 **Scalar 결과의 잠그는 조회**를 씁니다. 반환된 ID만 유효로 봅니다.
- 퇴사는 같은 Row를 `FOR UPDATE`로 잠급니다.
- Table 이름은 실제 Schema(T-02A Evidence)에서 확인해야 합니다. JPQL Entity 조회를 쓴다면 `refresh(entity, PESSIMISTIC_READ)`로 대체할 수 있습니다.
- `FOR SHARE`끼리는 충돌하지 않으므로 동시 Publish 같은 일반 작업은 서로 막지 않습니다.
- 기존 User Lock은 그대로 둡니다. User 기준 근무 충돌 검사용입니다.

## 4. 새 영향 대상 처리 (최소 수정 설계, PROPOSED)
**퇴사 절차**
1. 권한 확인과 `now` 고정.
2. Lock 없이 ID 집합 Σ0을 조회합니다.
   - S0 = 퇴사자의 `startAt > now`인 **모든** Shift(DRAFT·PUBLISHED 무관)가 속한 Schedule
   - R0 = 퇴사자가 요청자·PENDING·ACCEPTED인, 근무 시작 전 OPEN·ACCEPTED Request
   - Sh0 = 그 모든 Shift (+ 진행 중 공식 근무 판정용)
3. `S0 → R0 → Sh0` 순서로 잠급니다. 각 종류 안에서는 id 오름차순입니다.
4. `M(X)`를 `FOR UPDATE`로 잠그고 `leftAt IS NULL`을 확인합니다. 이미 퇴사했으면 409 (ML-Q21).
5. 같은 조회로 Σ1을 다시 구합니다.
   - **Σ1 ⊄ Σ0이면 (새 ID 발견)** 아무것도 쓰지 않고 Rollback한 뒤 409 "영향 변경"을 돌려줍니다. 새 Lock은 잡지 않으므로 이 경로에서는 Deadlock이 생기지 않습니다.
   - Σ1 ⊆ Σ0이면 Σ1 기준으로 처리합니다. 사라진 대상은 무시합니다.
6. 상태 변경 후 Commit합니다. 변경은 모두 이미 잠근 Row이거나 퇴사자 소유의 Pattern·DRAFT입니다.

**확인한 점**
- **잠근 Row와 아닌 Row 구분:** 종류별 ID 집합 비교로 충분합니다. 영향 규모는 작아서 전체 비교가 가능합니다.
- **미리보기 이후 변경:** 실행 요청에 미리보기 시점 Σ의 Hash를 함께 보냅니다. 실행할 때 Σ1과 다르면 409로 응답하고 새 영향 목록을 함께 돌려줍니다. 관리자가 보지 못한 근무가 취소되는 일을 막습니다.
- **반복 실패:** 중단은 퇴사 Transaction이 열려 있는 짧은 시간 안에 **퇴사자와 관련된** 새 대상이 Commit될 때만 일어납니다. 계속 반복될 가능성은 낮습니다 (추정). MVP에서는 서버 자동 재시도 없이 "새 영향 목록 확인 후 다시 실행"으로 처리합니다.

## 5. HTTP ErrorCode 정책 (이전의 "Lock 실패는 모두 409"를 수정)
| 경우 | Status | ErrorCode (이름 미정) | 재시도 | 관리자 안내 |
|---|---|---|---|---|
| 업무 상태 충돌 (진행 중 근무, 대상이 MANAGER, 이미 퇴사) | 409 | `WPL-0xx` | 그대로는 불가 | 사유와 종료 시각 |
| 미리보기 이후 변경 또는 다시 조회 중 새 대상 발견 | 409 | `WPL-0xx` (영향 변경) | 가능 (새 영향 목록 확인 후) | "영향 범위가 바뀌었습니다" + 새 목록 |
| Deadlock (MySQL 1213) | **503** | `COM-0xx` (일시 충돌) | 가능. 전체 Rollback되므로 안전 | "잠시 후 다시 시도" |
| Lock 대기 시간 초과 (1205) | 503 | 같음 | 가능 | 같음 |
| 기타 DB 오류 | 500 | 기존 처리 | 불가 | 기존 |

**주의**
- 현재는 Deadlock·대기 초과 모두 `Exception.class` 처리로 500입니다 (VERIFIED_AS_IS).
- Spring이 이 Stack에서 어떤 예외 Class로 변환하는지는 UNKNOWN입니다 (`PessimisticLockingFailureException` 계열로 추정). H6로 확인합니다.
- MySQL의 대기 초과는 기본 설정에서 **문장 하나만** Rollback합니다. 예외가 `@Transactional` 밖으로 전파되어 Transaction 전체가 Rollback되는지 Test로 확인해야 합니다.

## 6. 수정된 H6 테스트 Matrix (설계만, 미실행)
- **환경:** Testcontainers MySQL 9.7.2, Session READ COMMITTED (+ REPEATABLE READ 대조 1회).
- **도구:** T-02B `StepGate`(멈춤), `LockWaitProbe`(대기 SQL 확인). LOCAL_ONLY라 DL-009 결정이 필요합니다.
- **멈추는 방법:** 테스트에서 Repository를 Spy로 감싸 특정 호출 직후 `StepGate`에서 멈춥니다.

| ID | T1 멈춤 지점 / 보유 Lock | T2 대기 SQL | 재개 순서 | 예상 결과 | 최종 DB | 불변식 |
|---|---|---|---|---|---|---|
| ML-01 (A) | 대타 생성: 후보 조회 직후 / 요청자 `Sh(X)` | 퇴사 (대기 없음) | T2 Commit → T1 재개 | T1 성공 | 새 Request 후보에 X 없음 (`FOR SHARE` 재선정) | INV-L1 |
| ML-02 (B) | 대타 생성: X 포함 `M(S)` 보유, C INSERT 후 Commit 직전 | 퇴사: `M(X) FOR UPDATE` 대기 | T1 Commit → T2 다시 조회 | T2 **409 영향 변경** → 다시 실행하면 성공 | 다시 실행 후 X `INELIGIBLE`, `leftAt` 설정 | INV-L1 |
| ML-03 (A 역순) | 퇴사: `M(X)` X 보유, Commit 직전 | 대타 생성: `M(X) FOR SHARE` 대기 | T1 Commit → T2 | T2 성공 (X 제외) | 후보에 X 없음 | INV-L1 |
| ML-04 | ACCEPT(X): `R(Q)` 보유, U Lock 이전 | 퇴사: `R(Q) FOR UPDATE` 대기 | T1 Commit → T2 | 둘 다 성공 | Q `CLOSED/CANDIDATE_INELIGIBLE`, X `ACCEPTED` 유지 | INV-L1, L3 |
| ML-04' | 퇴사: `R(Q)` 보유 | ACCEPT(X): `R(Q)` 대기 | T1 Commit → T2 | T2 `SUB-021` 403 (L136이 먼저 검사) | X `INELIGIBLE` | INV-L1 |
| **ML-05 (수정, D)** | APPROVE(수락자 X): `R(Q)` 보유, U Lock 이전 | 퇴사: `R(Q)` 대기 | T1 Commit (`Shift T.member` = X) → T2 다시 조회 | T는 Sh0에 없던 X의 근무 → T2 **409** → 다시 실행 | 다시 실행 후 T `CANCELLED`(담당 X), Q `APPROVED` 유지 | INV-L2, L4 |
| ML-05' | 퇴사: `R(Q)` 보유 | APPROVE: `R(Q)` 대기 | T1 Commit → T2 | T2 `INVALID_REQUEST` | `Shift.member` 변경 없음 | INV-L4 |
| **ML-06 (수정, C)** | Publish(S, X의 DRAFT 포함): `S` 보유 | 퇴사: `S FOR UPDATE` 대기 | T1 Commit → T2 | T2 성공. 다시 조회해도 같은 Shift ID라 중단 없음. 이제 공식 근무 → 취소 (진행 중이면 409 업무 충돌) | S `PUBLISHED`, T `CANCELLED`(담당 X) | INV-L2 |
| ML-06' | 퇴사: `S` 보유 | Publish: `S` 대기 | T1 Commit → T2 | T2는 DRAFT 삭제 후 상태로 검증 (비었으면 `EMPTY_SCHEDULE`) | 퇴사자 SCHEDULED 0 | INV-L2 |
| ML-07 | 퇴사 #1: Σ Lock 보유 | 퇴사 #2: 첫 공통 Lock(S·R·Sh) 또는 `M(X)` 대기 | #1 Commit → #2 | #2 409 이미 퇴사 | 처리 1회 | 중복 방지 |
| ML-08 | 공개 근무 추가(X, S'): `S'`, `U(X)`, `M(X) S` 보유, INSERT 후 | 퇴사: `M(X) FOR UPDATE` 대기 | T1 Commit → T2 다시 조회 | T2 409 → 다시 실행 | 새 근무 `CANCELLED` | INV-L2 |
| ML-09 | REJECT(Y): `R(Q)` 보유 | 퇴사(X, Q의 마지막 후보): `R(Q)` 대기 | 두 순서 모두 | 둘 다 성공 | Q 종료 1회, 종료 알림 1건, 사유는 구성 규칙대로 | INV-L3 |
| ML-10 (E) | APPROVE(요청자 X, 수락자 Z): `R(Q)`, `U(Z)` 보유, Flush 직전 | 퇴사: `R(Q)` 대기 | T1 Commit → T2 | 둘 다 성공. **Deadlock 0** (확인 대상) | T 담당 Z `SCHEDULED`, Q `APPROVED`, X 퇴사. T는 이제 X 소유가 아니므로 건너뜀 | Lock 순서 |
| ML-11 (F) | 퇴사(X, W1): `M(X,W1)` 보유 | W2 Publish(X 포함): `U(X)` | 동시 | **W2가 W1 퇴사 때문에 대기하지 않음** (`LockWaitProbe`로 대기 0 확인) | W2 변경 없음 | INV-L5 |
| ML-12 (G) | DRAFT 생성(X, S'): `S'` 보유, INSERT 직전 | 퇴사: `M(X)` 보유 | T2 Commit → T1 | (SHOULD 수정 후) T1 `SFT-004` | 퇴사자 DRAFT 0 | DRAFT 위생 |
| ML-13 | 중간 실패 주입 | — | — | 실패 | 변경 0 | INV-L6 |
| ML-14 | Lock 대기 초과 주입 (짧은 `innodb_lock_wait_timeout` Session 설정) | — | — | 503 | 변경 0 (Transaction 전체 Rollback 확인) | INV-L6 |

## 7. 기존 Production 변경 위치 (Improvement PR, 승인 필요)
| 구분 | 위치 | 변경 |
|---|---|---|
| MUST | `SubstituteRequestService.create` / `SubstituteCandidateService.findCandidates` | Request INSERT 전에 후보 Membership을 `FOR SHARE`로 다시 선정 |
| MUST | `ScheduleService.publishSchedule` (L572 이후) | 담당자 Membership `FOR SHARE` 재확인 |
| MUST | `SubstituteCandidateService.acceptCandidate` (L194 이후) | 후보 Membership `FOR SHARE` 재확인 |
| MUST | `SubstituteRequestService.approve` (L290 이후) | 수락자 Membership `FOR SHARE` 재확인 |
| SHOULD | `ShiftService` DRAFT 생성·수정, `RegularShiftPatternService` 등록·수정 | 대상 Membership `FOR SHARE` 재확인 |
| 신규 | `WorkplaceMemberRepository` | Scalar 잠그는 조회 (`FOR SHARE`·`FOR UPDATE`) |
| 신규 | 퇴사 Command | §4 절차 |
| 신규 | 공개 근무 추가 Command | `S → U → M(FOR SHARE)` |
| 공통 | `GlobalExceptionHandler` | Lock 실패 → 503 |

**Lock 순서 변경 여부**
- 기존 명시 Lock의 종류와 순서는 바꾸지 않습니다. Lock을 추가하고 재검증만 넣습니다.
- 그래도 AI-RULES §2의 "Lock 종류·순서" 변경 승인 대상입니다.

## 8. ML-DECISION-01에 반영할 동시성 계약 (PROPOSED)
> 구성원을 미래의 유효 참여자로 확정하는 Command(Publish, 대타 후보 선정, ACCEPT, APPROVE, 공개 근무 추가)는 기존 Lock을 잡은 뒤 해당 Membership Row를 잠그는 조회(`FOR SHARE`)로 현재 소속 여부를 다시 확인한다. 이미 읽어 둔 Entity 값으로 판단하지 않는다.
> Membership 종료는 다음 순서로 실행한다:
> 1. 영향 ID 집합(퇴사자의 모든 미래 Shift와 그 Schedule, 관련 Request)을 조회한다.
> 2. `Schedule → Request → Shift` 순서(종류마다 id 오름차순)로 잠근다.
> 3. Membership Row를 `FOR UPDATE`로 잠근다.
> 4. 같은 조회를 다시 실행한다. 처음 범위에 없던 ID가 있거나 미리보기 Hash와 다르면 아무것도 바꾸지 않고 409(영향 변경)로 끝낸다.
>
> APPROVE의 Shift 변경은 항상 그 Request Lock 아래에서만 일어나며, 퇴사는 Shift보다 Request를 먼저 잠근다 (R-Gating).
> 퇴사는 User Row를 잠그지 않는다. 그래서 다른 근무지 작업을 막지 않는다.
> Deadlock과 Lock 대기 초과는 전체 Rollback 후 503(재시도 가능)으로 응답한다.
> Deadlock이 없다는 것은 H6 ML-10·ML-11·ML-14로 확인하기 전까지 **UNKNOWN**이다.
> 상태: **PROPOSED**.

## 9. 팀 승인 사항 (2개)
1. **Guard를 Membership Row 잠그는 조회로 할지**
   - 다른 Command는 `FOR SHARE`, 퇴사는 `FOR UPDATE`. 이 방식은 READ COMMITTED에 의존하지 않습니다.
   - 기존 Command 4곳(MUST) 수정을 함께 승인해야 합니다.
   - DRAFT·Pattern(SHOULD)을 이번에 포함할지도 정해야 합니다.
2. **오류 정책을 다음과 같이 나눌지**
   - 업무 충돌·영향 변경은 409, Deadlock·Lock 대기 초과는 503(재시도 가능), 그 밖의 DB 오류는 500
   - 미리보기 Hash 비교(관리자가 본 범위와 실제 처리 범위 일치)를 MVP에 넣을지

## 10. 구현 순서와 완료 기준
| 순서 | 작업 | 완료 기준 |
|---|---|---|
| 0 | 선행 결정: DL-009 (Harness 공유), DL-002 (격리 수준 기록), 위 승인 2개 | Decision Log에 승인자와 일자 기록 |
| 1 | H6 Harness를 팀 Branch로 이동, Spy 기반 멈춤 지점 Utility | Harness Self Test가 CI에서 통과 |
| 2 | **현재 동작 기록 Test 먼저 작성:** ML-01·03·05'·10의 퇴사 쪽을 Test 전용 `leftAt` 갱신으로 흉내 내서 R1(A)이 실제로 재현되는지 기록 | `REPRODUCED` / `NOT_REPRODUCED` 기록 |
| 3 | `WorkplaceMemberRepository`에 Scalar 잠그는 조회 추가 | 단위 Test: Persistence Context에 오래된 Entity가 있어도 최신 값 반환 |
| 4 | MUST 4곳 재검증 추가 | 기존 Test 기대값 변경 0 + ML-01·03·04'·05' 통과 |
| 5 | 퇴사 Command (§4) + 공개 근무 추가 | ML-02·04~11 통과, Rollback ML-13 통과 |
| 6 | 오류 처리 (503) | ML-14 통과. 예외 Class 실측 기록 |
| 7 | (SHOULD) DRAFT·Pattern 재검증 | ML-12 통과 |
| 8 | Evidence | 각 H6 결과를 `TEST-EVIDENCE` 형식으로 기록 (SHA, 환경, 격리 수준). RR 대조 결과 포함 |

Kotlin Migration PR과 분리하며, V2~V4 Kotlin 전환 이후 진행합니다.
