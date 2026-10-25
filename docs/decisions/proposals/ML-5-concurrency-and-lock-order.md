# ML-5 동시성: Lock 순서·재검증·실패 응답

| 축 | 상태 |
|---|---|
| 결정 단계 | **원칙: 사용자 잠정 결정** (2026-10-23). **구체 순서: 분석 후보, 실험 검증 대기** |
| ② 정책 | 카드 `PROPOSED`. `DECISION-LOG` DL-002 (READ COMMITTED), DL-009 (Harness 공유), DL-010 (MySQL 동시성 검증 지위) `PENDING` |
| ③ 문서 | `DRAFT` (저장소 Proposal 문서. 2026-10-25 이관, 원본 로컬 `drafts/D7-ML-5/`) |
| 구현 | 없음. Lock 종류·순서 변경은 AI-RULES §2 승인 대상, Migration PR과 분리 |
| ④ / ⑤ | 전부 `SPECIFIED` / `NOT_RUN`. 이 문서의 Lock 흐름 분석은 **코드를 읽고 MySQL InnoDB 동작을 적용한 추론**이며 실행으로 확인하지 않았다 |
| 기준 코드 | 3차 `develop` `31ecaee` |
| 이관 당시 develop HEAD | `a148a62` (2026-10-25). `backend/src/main`은 `cdaaa13` 이후 변경 0 (이관 시 diff로 확인) |
| 코드 근거 재검증 | **NO** (S2-1은 이관만 함. 본문의 줄 번호·Test 근거는 위 "기준 코드" 시점 값). 이후 검증 예정: S2-2~S3 |
| 원문 | `docs/references/ml/ML-CONCURRENCY-01-R.md` (이 문서가 일부를 수정한다, §8) |
| 앞선 카드 | ML-2, ML-3, ML-H2, ML-4, AUTH-1 |
| 주관 / 리뷰 | H6 총괄(D-NOW-01 미정), V3·V4 Owner, 공통 설정 Owner |

> 동시성은 SWITCH에서 정합성과 상태 전이를 지키는 핵심이다. 이 문서는 "어떤 순서로 잠그는가"만이 아니라 **잠근 뒤 무엇을 다시 확인하는가**, **아직 없는 Row가 생기는 경합을 무엇으로 막는가**, **실패를 어떻게 알리는가**를 함께 정한다.

---

## 1. 용어 (처음 읽는 사람용)

| 용어 | 뜻 |
|---|---|
| Row Lock (`FOR UPDATE`) | 한 Transaction이 Row를 잠그면, 다른 Transaction은 같은 Row를 잠그거나 바꾸려 할 때 **기다린다** |
| 공유 Lock (`FOR SHARE`) | 여러 Transaction이 함께 잡을 수 있지만, `FOR UPDATE`와는 서로 기다린다 |
| Deadlock | A가 X를 잡고 Y를 기다리고, B가 Y를 잡고 X를 기다리는 상태. DB가 한쪽을 실패시킨다 |
| 전역 Lock 순서 | 모든 Command가 같은 종류의 Row를 **같은 순서**로 잠그면 서로 반대로 기다리는 일이 생기지 않는다 |
| 아직 없는 Row 경합 | "활성 요청이 없다"고 확인한 뒤 다른 Transaction이 새 요청을 만드는 경우. 없는 Row는 잠글 수 없으므로 **공통 부모 Row**를 잠가 막는다 |
| 1차 캐시 문제 | JPA는 같은 Transaction에서 이미 읽은 Entity를 다시 조회하면, Lock을 걸어도 **예전에 읽은 값을 그대로 돌려줄 수 있다** |
| READ COMMITTED (RC) | 이 프로젝트의 전역 격리 수준 (DL-002). 일반 조회는 각 문장 시점에 Commit된 값을 본다. Lock 조회(`FOR UPDATE`)는 최신 Commit 값을 읽고 잠근다 |

## 2. AS-IS Lock 지도 (① `VERIFIED_AS_IS`, 정적 확인)

경로 기준: `backend/src/main/java/com/merge/backend/domain/`

| Command | 명시적 Lock 순서 | 그 밖의 쓰기 (암묵적 Lock) | 근거 |
|---|---|---|---|
| 근무표 생성 | Workplace | Schedule·Shift INSERT | `shift/service/ScheduleService.java` L74 |
| Publish | Schedule → User (ID 오름차순) | Schedule UPDATE | L305, L575-596 |
| DRAFT 근무 추가·수정·삭제 | Schedule | Shift INSERT·UPDATE·DELETE | `shift/service/ShiftService.java` L200 |
| 대타 생성 | Shift | 후보 선정 (Lock 없음) → Request·Candidate INSERT | `substitute/service/SubstituteRequestService.java` L67-75, L132 |
| 대타 수락 | Request → User | Candidate·Request UPDATE | `substitute/service/SubstituteCandidateService.java` L116, L194 |
| 대타 거절 | Request | Candidate·Request UPDATE | 같은 파일 L253~ |
| 대타 승인 | Request → User | **Shift UPDATE (`changeMember`, Commit 전 Flush 시 Shift Row에 쓰기 Lock)** | `SubstituteRequestService` L236, L290 |
| 대타 종료 | Request | Request UPDATE | L363 |
| 알림 읽음 | Notification | — | `notification/service/NotificationService.java` L303 |

**InnoDB가 자동으로 거는 Lock (추론, MySQL 동작):** 자식 Row를 INSERT하면 **FK가 가리키는 부모 Row에 공유 Lock**을 건다. 예: Candidate INSERT → 그 후보의 WorkplaceMember Row에 공유 Lock. 이 Lock은 §5에서 쓴다.

## 3. 새 Command가 잠가야 하는 대상 (ML-2·ML-3·ML-H2·ML-4에서)

| Command | 바꾸는 것 | 지켜야 할 불변조건 |
|---|---|---|
| 공개 후 추가 | 새 Shift | 배정받는 User의 이중 배정 없음 |
| 공개 후 인계 | Shift.member, 활성 Request 종료, 배정 이력 | 인계 후 그 근무에 활성 Request 0, 새 담당자 이중 배정 없음 |
| 공개 후 취소 | Shift 상태, 활성 Request 종료 | 취소 후 활성 Request 0 |
| 퇴사 확정 | 소속 `leftAt`, 본인 근무 인계·취소, DRAFT 삭제, 본인 근무의 활성 Request 종료, 후보로 걸린 Request의 INELIGIBLE·종료, 배정 이력, (선택) 초대 코드 | ML-2-INV-01·02, ML-3 상태, 퇴사 후 퇴사자가 유효한 후보·수락자·담당자로 남지 않음 |
| 가입·재가입 | 새 소속 | 현재 소속 ≤ 1 (DB 제약 + 직렬화) |
| 초대 코드 재발급 | Workplace.inviteCode | 재발급 확정 후 이전 코드 가입 0 |

## 4. 검증: 이전 제안 순서의 결함

이전 대화의 후보 순서: **Workplace → Schedule → Request → User → Shift → Membership** (원문 ML-CONCURRENCY-01-R의 Schedule → Request → Shift → Membership을 확장).

### 4-1. 결함 1: 대타 생성과 인계에서 "아직 없는 Request" 경합

| 시점 | T1 대타 생성 (근무 S) | T2 관리자 인계 (근무 S, 이전 순서: Request 먼저) |
|---|---|---|
| 1 | S `FOR UPDATE` 획득 | |
| 2 | 후보 선정, Request INSERT (아직 Commit 전) | 근무 S의 활성 Request 조회 → **없음** (T1 미Commit, RC) → 잠글 Request 없음 |
| 3 | | 새 담당자 User Lock |
| 4 | Commit | S `FOR UPDATE` 대기 → 획득 |
| 5 | | 담당자 변경 Commit. **T1이 만든 OPEN Request가 그대로 남음** → ML-3-Q1 위반 (끝날 수 없는 요청) |

Deadlock은 없지만 **불변조건이 깨진다.** 원인: 대타 생성은 "그 근무의 Request를 새로 만드는 권한"을 **Shift Lock**으로 직렬화하는데, 인계가 Shift보다 Request를 먼저 보면 이 직렬화 지점을 지나쳐 버린다.

→ **결론: 근무 단위의 일(그 근무의 Request 생성·종료, 담당자 변경)은 Shift를 Request보다 먼저 잠가야 한다.**

### 4-2. 결함 2: 그렇게 바꾸면 승인과 반대 순서가 된다

| 시점 | T1 대타 승인 (AS-IS: Request → User → Shift 쓰기) | T2 인계 (수정안: Shift → Request) |
|---|---|---|
| 1 | Request R `FOR UPDATE` | Shift S `FOR UPDATE` |
| 2 | User Lock | R `FOR UPDATE` 대기 (T1 보유) |
| 3 | S UPDATE 시도 → 대기 (T2 보유) | |
| 결과 | **Deadlock** | |

→ **결론: 승인도 Shift를 먼저 잠가야 한다** (AS-IS 순서 변경, V4 Improvement).

### 4-3. 결함 3: 1차 캐시 때문에 승인 순서를 바꾸는 방법이 중요하다

승인은 Request ID만 받는다. Shift를 먼저 잠그려면 Shift ID를 알아야 한다. 이때 **Request Entity를 Lock 없이 먼저 읽으면** 그 Entity가 영속성 Context에 남는다. 이후 `findByIdForUpdate(requestId)`로 Lock을 걸어도 Hibernate는 **이미 관리 중인 Entity를 돌려주며 상태 값을 새로 덮어쓰지 않는다** (원문 ML-CONCURRENCY-01-R이 지적한 문제와 같음). 그러면 기다리는 동안 다른 Transaction이 바꾼 상태(예: CLOSED)를 보지 못한다.

→ **Shift ID는 Scalar 조회(예: `select r.shift.id from SubstituteRequest r where r.id = :id`)로 얻고, Entity는 Lock 조회로 처음 읽는다.** 이미 읽은 Entity를 다시 확인해야 하면 `refresh`(Lock 포함)를 쓴다.

### 4-4. 결함 4: Membership Guard의 시점

퇴사는 소속 Row를 바꾼다(`leftAt`). 다른 Command는 "이 사람이 현재 소속인가"를 확인한다. 둘을 직렬화하는 지점이 소속 Row다.

| 시점 | T1 대타 생성 (B를 후보로) | T2 B 퇴사 |
|---|---|---|
| 1 | 후보 선정에서 B 현재 소속 확인 (Lock 없음) | |
| 2 | | B 소속 `FOR UPDATE`, `leftAt` 기록 |
| 3 | B Candidate INSERT → FK 공유 Lock이 B 소속을 기다림 | |
| 4 | | Commit |
| 5 | INSERT 성공 (FK는 존재만 확인) → **퇴사자 B가 PENDING 후보** | |

→ FK Lock만으로는 막지 못한다. **대타 생성은 후보 소속을 `FOR SHARE`로 잠근 뒤 `leftAt`을 다시 확인하고 INSERT해야 한다.**

반대 순서 (T1이 먼저 B 소속 `FOR SHARE`): T2 퇴사는 B 소속 `FOR UPDATE`에서 T1 Commit까지 기다린다. 그 뒤 퇴사가 **소속 Lock 이후 후보를 다시 조회**하면 T1이 만든 PENDING을 찾는다. 이 Request는 퇴사가 앞에서 잠그지 않았으므로 순서 밖이다 → **차이가 있으면 409로 끝내고 관리자가 다시 시도** (원문의 "abort and rescan"과 같다).

## 5. 수정한 전역 Lock 순서 후보

> **Workplace → Schedule (ID 오름차순) → Shift (ID 오름차순) → SubstituteRequest (ID 오름차순) → User (ID 오름차순) → WorkplaceMember (ID 오름차순)**
>
> 규칙:
> 1. 필요한 Row만, 위 순서로 잠근다. 같은 종류 여러 개는 ID 오름차순.
> 2. **근무 단위의 일은 Shift가 공통 부모 Lock**이다 (그 근무의 Request 생성·종료, 담당자 변경).
> 3. **사람에게 근무를 주는 일은 그 사람의 User가 공통 Lock**이다 (Publish, 수락, 승인, 공개 후 추가·인계, 퇴사 인계).
> 4. **소속 여부가 결과를 바꾸는 일은 WorkplaceMember를 마지막에** 잠그고(다른 Command `FOR SHARE`, 퇴사 `FOR UPDATE`), Lock 후 `leftAt`을 다시 읽는다.
> 5. Lock 이후의 판단은 **Lock 조회로 처음 읽은 값**이나 `refresh`한 값으로 한다.
> 6. 미리 조회한 목록과 Lock 이후 다시 조회한 목록이 다르면 409로 끝낸다 (자동 재시도 없음).

### 5-1. Command별 적용과 바꿀 범위

| Command | 수정 순서 | AS-IS와의 차이 | 구분 |
|---|---|---|---|
| 근무표 생성 | Workplace | 없음 | — |
| Publish | Schedule → User | 없음 | — |
| DRAFT 편집 | Schedule → **담당 소속 `FOR SHARE` + `leftAt` 재확인** | **필수 (2026-10-23 수정).** AS-IS는 `leftAt`을 Lock 없이 확인한다 (`ShiftService.validWorkplaceMember` L230-238). 퇴사 Commit 직전에 확인을 통과한 추가·수정은 **퇴사자 DRAFT 근무를 남겨 ML-2-INV-02를 위반**한다. Publish 안전장치는 공개만 막을 뿐 이 계약을 지키지 못한다. 그래서 담당 소속 `FOR SHARE`로 퇴사의 `FOR UPDATE`와 직렬화하고, 퇴사는 소속 Lock 후 DRAFT를 다시 조회한다 (잠그지 않은 Schedule에서 새 DRAFT가 보이면 409) | V3 변경 |
| 대타 생성 | Shift → (후보 소속 `FOR SHARE` + 재확인) → INSERT | **후보 소속 Guard 추가** | V4 변경 |
| 대타 수락 | Request → User → 후보 소속 `FOR SHARE` 재확인 | **Guard 추가** | V4 변경 |
| 대타 거절 | Request | 없음 (퇴사가 같은 Request를 잠그므로 직렬화) | ML-3 종료 판정만 변경 |
| **대타 승인** | (Scalar로 Shift ID) → **Shift → Request** → **Lock 후 `request.shift == 잠근 Shift`와 Request 상태 재검증** → User → 수락자 소속 `FOR SHARE` 재확인 | **순서 변경 + Guard 추가.** Scalar 조회는 Lock이 아니므로 Lock 후 관계를 반드시 다시 확인 | V4 변경 |
| 대타 종료 | Request | 없음 (Shift를 기다리지 않으므로 순환 없음) | — |
| 공개 후 추가 | Schedule → 새 담당 User → **새 담당 소속 `FOR SHARE` + `leftAt` 재확인** → INSERT | 새 Command | 신규 |
| 공개 후 인계 | Schedule → Shift → 그 근무의 활성 Request → 새 담당 User → (새 담당 소속 `FOR SHARE`) | 새 Command | 신규 |
| 공개 후 취소 | Schedule → Shift → 그 근무의 활성 Request | 새 Command | 신규 |
| 퇴사 확정 | (재발급 시 Workplace) → 관련 Schedule → 본인 근무 Shift → 관련 Request 전체(본인 근무의 활성 + 후보로 걸린 것) → 인계 대상 User 전체 → 인계 대상 소속 `FOR SHARE` + 대상 소속 `FOR UPDATE` → **다시 조회 → 차이 있으면 409** | 새 Command | 신규 |
| 가입·재가입 | Workplace → (현재 코드 재비교) → INSERT (U2' Unique) | Lock 추가 | V2 변경 |
| 초대 코드 재발급 | Workplace | 새 Command | 신규 |

### 5-2. 순환 검사 (쌍별, 추론)

| 쌍 | 공통 Row | 순환 여부 | 이유 |
|---|---|---|---|
| 대타 생성 vs 인계·취소 | Shift | 없음 | 둘 다 Shift 먼저 → 직렬화. 인계는 Shift Lock 뒤 Request를 조회하므로 생성된 Request를 본다 (§4-1 해결) |
| 승인(수정) vs 인계 | Shift, Request | 없음 | 둘 다 Shift → Request |
| 수락 vs 인계 | Request, User | 없음 | 수락은 Shift를 잠그지 않음. 둘 다 Request → User |
| 종료 vs 인계 | Request | 없음 | 종료는 Request만 |
| 수락 vs 승인 | Request, User | 없음 | 둘 다 Request → User |
| Publish vs 승인·인계·추가 | User | 없음 | Publish는 Schedule → User만. 다른 쪽은 Publish가 잡은 Schedule을 기다리지 않거나(승인) 같은 순서(추가·인계: Schedule → … → User) |
| 퇴사 vs 대타 생성 | Shift(본인 근무), 소속 | 없음 | 둘 다 Shift → … → 소속. 소속 Lock 후 다시 조회로 놓친 Row 발견 → 409 (§4-4) |
| 퇴사 vs 다른 근무의 승인 (퇴사자가 수락자) | Request, 소속 | 없음 | 승인: 그 근무 Shift → Request → User → 소속. 퇴사: 그 Request → … → 소속. 퇴사는 다른 사람 Shift를 잠그지 않음 |
| 퇴사 vs 수락 (퇴사자 본인) | Request, User, 소속 | 없음 | 둘 다 Request → User → 소속 |
| 퇴사 vs Publish | Schedule | 없음 | 둘 다 Schedule 먼저 |
| 가입 vs 재발급 | Workplace | 없음 | 같은 Row |
| 가입 vs 퇴사 (같은 사람) | 소속 Unique | 없음 | 가입 INSERT의 중복 검사가 퇴사의 소속 UPDATE Commit을 기다림 (InnoDB 추론) |
| 두 관리자의 같은 근무 인계 | Shift | 없음 | 같은 Row. 관리자가 1명뿐이라 실제로는 같은 관리자의 중복 요청 |

**전역 규칙의 전제:** 퇴사가 소속을 잠가도, **사람에게 근무를 주는 모든 경로**(Publish, 대타 승인, 공개 후 추가·인계, DRAFT 추가·수정, 퇴사 인계)가 같은 규칙(User Lock + 담당 소속 확인)을 지켜야 새 배정이 생기지 않는다. 한 경로라도 빠지면 퇴사 확정 후 퇴사자에게 새 근무가 생길 수 있다.

**결론 (추론):** 수정한 순서에서 위 쌍들은 순환이 없다. **Deadlock이 없다고 확정하지 않는다** (Hibernate Flush 시점, FK·Index Lock이 추가 대기를 만들 수 있음). 기존 V4 승인 순서 변경은 V4 회귀 Test와 함께 확인한다. 단 **모든 경쟁 Command가 규칙을 지킬 때만** 성립한다. 하나라도 순서를 어기면 다시 Deadlock 후보가 된다 → 코드 리뷰 체크리스트와 Test로 고정한다.

### 5-3. 아직 없는 Row 경합 정리

| 확인 | 막는 공통 Lock |
|---|---|
| "이 근무에 활성 Request 없음" | Shift (생성·인계·취소·퇴사 모두 Shift 먼저) |
| "이 사람이 이 시간에 다른 확정 근무 없음" | User (모든 배정 Command가 User Lock) |
| "이 사람이 현재 소속" | WorkplaceMember (`FOR SHARE` / `FOR UPDATE`) |
| "이 사람의 미래 공식 근무 목록" (퇴사) | 소속 `FOR UPDATE` 후 다시 조회 → 차이면 409 |
| "이 근무지에 이 사람의 현재 소속 없음" (가입) | Workplace + U2' Unique |
| "이 근무표의 근무 겹침 없음" (공개 후 추가) | Schedule + User |

## 6. 실패 응답 (사용자 잠정 결정)

| 상황 | 응답 | 근거 |
|---|---|---|
| Deadlock으로 Transaction 실패 | **503** 후보 + "잠시 후 다시 시도해 주세요" | 일시적 처리 실패 |
| Lock 대기 시간 초과 | **503** 후보 | 일시적 처리 실패 |
| 미리보기·다시 조회 결과가 바뀜 | **409** | 업무 상태 충돌, 다시 확인 필요 |
| 이미 처리됨 (이미 종료된 소속 등) | **409** | 업무 상태 충돌 |
| 현재 소속 중복 가입 (이름 붙은 Unique 위반) | **409** WPL-002 | ML-H2 |
| 그 밖의 DB 오류 | **500** (AS-IS 유지) | — |

- **자동 재시도는 하지 않는다** (Frontend·Backend 모두).
- 503·409 뒤에는 Frontend가 **최신 상태를 다시 조회**하고, 이미 성공한 것이 확인되면 완료 화면을 보여 준다 (응답 유실 대비). 퇴사 확정을 자동으로 다시 보내지 않는다.
- 예외 변환은 Spring의 `DeadlockLoserDataAccessException`·`CannotAcquireLockException` 계열과 실제 MySQL 오류 코드(예: 1213 Deadlock, 1205 Lock wait timeout)를 **실험으로 확인한 뒤** 매핑한다. Hibernate·Spring 버전에 따라 전파되는 예외 타입이 다를 수 있다.
- 503일 때 Transaction이 실제로 Rollback됐는지 Test로 확인한다.
- `Retry-After` Header는 MVP에서 넣지 않는다.

## 7. 재현 Test 계획 (모두 `SPECIFIED` / `NOT_RUN`)

방식: PR #5 `ForcedOrderTest`처럼 CountDownLatch로 첫 Transaction을 Commit 전에 붙잡고 두 번째를 실행한다. MySQL Testcontainers, RC. 판정은 `REPRODUCED` / `NOT_REPRODUCED` / `ENVIRONMENT_UNSUITABLE` / `FIXTURE_ERROR` (AI-RULES §6).

**AS-IS 재현 (지금 코드에서 문제가 실제로 생기는지 먼저 확인):**

| ID | 시나리오 | 기대 (AS-IS) |
|---|---|---|
| ML5-R1 | 대타 생성이 후보 선정을 끝낸 뒤, 후보 B의 `leftAt`을 Test 전용으로 갱신해 Commit → 생성 계속 | B PENDING 생성 (반례 A 재현) |
| ML5-R2 | 승인 Transaction이 Request·User Lock을 잡은 상태에서, 다른 Transaction이 같은 Shift Row를 `FOR UPDATE` → 승인이 Shift UPDATE 시도 | Deadlock 재현 여부 확인 (지금은 인계 Command가 없어 Test 전용 Lock으로 흉내) |
| ML5-R3 | Lock 전에 Request Entity를 읽고, 다른 Transaction이 CLOSED로 Commit한 뒤 같은 Transaction에서 `findByIdForUpdate` | 영속성 Context의 옛 상태(OPEN)가 보이는지 확인 (§4-3) |

**목표 계약 (구현 후):**

| ID | 첫 Transaction (붙잡음) | 두 번째 | 지켜야 할 결과 |
|---|---|---|---|
| ML5-T1 (H6-C1) | 인계 (Shift 보유) | 대타 생성 같은 근무 → 대기 | 인계 후 활성 Request 0 |
| ML5-T2 | 대타 생성 (Shift 보유) | 인계 → 대기 | 생성된 Request가 인계 Transaction에서 `SHIFT_CHANGED`로 종료 |
| ML5-T3 (H6-C2) | 승인 (Shift·Request 보유) | 인계 → 대기 | Deadlock 0, 담당자 1명 |
| ML5-T4 | 다른 근무지 수락 (User B 보유) | B에게 인계 → 대기 | B 이중 배정 0 |
| ML5-T5 (H6-C5) | 퇴사 (B 소속 `FOR UPDATE`) | B의 수락 → Guard 대기 후 거부 | B 유효 수락자 아님 |
| ML5-T6 (H6-C7) | 대타 생성 (B 소속 `FOR SHARE`) | B 퇴사 → 대기 후 다시 조회 → 409 | 퇴사 미반영, B PENDING 남음 (다음 시도에서 INELIGIBLE) |
| ML5-T7 (H6-C6) | 퇴사 (후보 Request 보유) | 다른 후보 마지막 거절 → 대기 | 응답 가능 후보 0이면 `CANDIDATE_INELIGIBLE` |
| ML5-T8 (H6-C8) | 승인 (퇴사자가 담당자가 됨) | 퇴사 → 다시 조회 → 409 | 퇴사 후 퇴사자 담당 미래 근무 0 (다음 시도에서 처리) |
| ML5-T9 | 퇴사 (Schedule 보유) | Publish → 대기 | 퇴사자 근무 미공개 |
| ML5-T10 (V2-N-07) | 가입 (Workplace 보유) | 같은 사람 가입 → 대기 후 409 | 현재 소속 1 |
| ML5-T11 (V2-N-12) | 재발급 (Workplace 보유) | 이전 코드 가입 → 대기 후 WPL-001 | 이전 코드 가입 0 |
| ML5-T12 | 일부러 Deadlock 유도 | — | 503 매핑, Rollback 확인 |
| ML5-T13 | 승인에서 Request를 Scalar 조회 후 Lock | 다른 Transaction이 CLOSED Commit | 승인이 CLOSED를 보고 거부 (§4-3 해결 확인) |
| ML5-T14 | 퇴사 (B 소속 `FOR UPDATE`) | B 담당 DRAFT 추가 → 소속 대기 후 거부 | 퇴사 Commit 후 B 담당 DRAFT 0건 (ML-2-INV-02) |
| ML5-T15 | B 담당 DRAFT 추가 (B 소속 `FOR SHARE`, 다른 근무표) | B 퇴사 → 대기 후 다시 조회 → 새 DRAFT 발견 → 409 | 퇴사 미반영, DRAFT 남음 (다음 시도에서 삭제) |
| ML5-R4 (AS-IS) | B 담당 DRAFT 추가가 `leftAt` 확인을 통과한 직후 B `leftAt`을 Test 전용으로 Commit | 추가 계속 | 퇴사자 DRAFT 생성 재현 여부 |

## 7-1. 실험 환경과 범위 (실행 전, 별도 승인 필요)

| 항목 | 계획 |
|---|---|
| 위치 | Project3 저장소의 **격리 Worktree** (`develop` 최신에서 로컬 Branch, Push 안 함). 메인 작업공간·팀 Branch는 건드리지 않음 |
| 코드 | **Test 코드만** 추가. Production 코드는 바꾸지 않음. 필요한 "새 Command"는 Test 안에서 `TransactionTemplate` + Lock Query로 흉내 |
| DB | Testcontainers MySQL 9.7.2 (test Profile), RC. 운영 DB 접속 없음. 로컬 Docker 29.6.1 확인됨 |
| 대상 | ML-H2 U2' S1~S8, ML-5 AS-IS 재현 R1~R3, 예외 매핑(1213·1205 → Spring 예외 타입), G03-OQ-01 `/workplaces/**` 무토큰 요청 결과 (Runtime 확인) |
| 결과 기록 | `TEST-EVIDENCE` 형식(SHA, 환경, 명령, 결과, Log 위치)으로 로컬 EV. 판정은 REPRODUCED / NOT_REPRODUCED / ENVIRONMENT_UNSUITABLE / FIXTURE_ERROR |
| 산출물 | 실험 보고서 1개 + 이 Draft의 "후보 → 확정 또는 수정" 반영 |
| 하지 않는 것 | 실험 Test를 팀 Branch에 Commit·Push하지 않음 (나중에 DL-009·Task 승인 후 정식 Test로 옮김) |

**성공 기준 (판정 방법):**

| 실험 | 확인할 결과 | 판정 |
|---|---|---|
| R1·R4 (반례 재현) | 퇴사자가 PENDING 후보 / DRAFT 담당으로 생기는가 | 생기면 REPRODUCED (설계 근거 확인), 안 생기면 원인 분류 (Fixture? 실제로 막힘?) |
| R2 (승인 Deadlock) | 반대 순서 Lock에서 MySQL 1213이 나는가, 어느 쪽이 실패하는가 | REPRODUCED면 승인 순서 변경 근거 확정 |
| R3 (1차 캐시) | Lock 조회 후에도 옛 상태가 보이는가 | 보이면 Scalar 조회·refresh 규칙 확정 |
| 예외 매핑 | 1213·1205가 어떤 Spring 예외로 오는가, Transaction이 Rollback되는가 | 실제 예외 타입을 문서에 기록 → 503 매핑 근거 |
| G03-OQ-01 | `/api/v1/workplaces/**` 무토큰 요청의 실제 응답 (가입, 구성원 목록, 초대 코드, 근무표, 대타 진행 목록) | 401/403이면 보호됨. 200이나 500(Actor 없음 NPE)이면 **보안·결함 후보** → 즉시 공유 |
| 공통 | Test 한 번 통과 = 안전이 아님 | `NOT_REPRODUCED`를 "결함 없음"으로 쓰지 않음 (AI-RULES §6). AS-IS 재현과 목표 불변조건을 구분 기록 |

## 8. 원문 ML-CONCURRENCY-01-R과의 차이

| 원문 | 이 문서 | 이유 |
|---|---|---|
| 순서 Schedule → Request → Shift → Membership | **Schedule → Shift → Request → User → Membership** | §4-1·4-2: 대타 생성의 Shift 직렬화와 맞추고, 승인과의 Deadlock을 없앰 |
| User Lock 언급 적음 | 배정받는 User Lock 필수 | 이중 배정 방지 (§5 규칙 3) |
| Guard: 다른 Command `FOR SHARE`, 퇴사 `FOR UPDATE` | 유지 + 대타 생성의 후보 Guard 명시 | §4-4 |
| abort and rescan → 409 | 유지 | — |
| 실패 응답 409 / 503 / 500 | 유지 (사용자 잠정 결정) | §6 |

## 9. 사용자 잠정 결정 / 분석 후보 / 검증 대기

| 구분 | 내용 |
|---|---|
| 사용자 잠정 결정 | 전역 Lock 순서 통일, 같은 종류 ID 오름차순, 배정 시 대상 User Lock, Lock 후 최신 상태 재검증, 실패 응답 503/409/500, 자동 재시도 없음 |
| 분석 후보 (사용자 확인 대기) | §5 구체 순서, 승인 순서 변경, 대타 생성·수락의 소속 Guard, 퇴사의 "다시 조회 후 409" |
| 실험 검증 대기 | ML5-R1~R3 (AS-IS 재현), 예외 타입 매핑, InnoDB FK 공유 Lock·Unique 대기 동작 (추론 부분) |
| 팀 결정 대기 | DL-002 (RC 유지), DL-009 (Harness), DL-010 (MySQL 동시성 검증 지위), H6 총괄 (D-NOW-01) |

## 10. 구현 영향 범위

| 영역 | 변경 |
|---|---|
| V4 기존 Command | 생성(후보 Guard), 수락(Guard), 승인(순서 변경 + Scalar 조회 + Guard), 거절(ML-3 종료 판정) |
| V2 | 가입(Workplace Lock + 코드 재비교 + Unique 예외 변환), 재발급(신규), 퇴사(신규) |
| V3 | 공개 후 추가·인계·취소(신규) |
| 공통 | Lock 실패 예외 → 503 매핑, Unique 위반 → 409 매핑 (제약 이름) |
| Repository | Membership `FOR SHARE` 조회, Shift ID Scalar 조회, 근무의 활성 Request `FOR UPDATE` 조회, 대상 사용자 미래 근무 조회 |
| 리뷰 체크리스트 | 새 Command마다 "§5 순서 준수, Lock 후 판단, 1차 캐시" 확인 항목 |

## 11. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-23 | DRAFT 편집 소속 Guard를 필수로 수정 (ML-2-INV-02), ML5-T14·T15·R4 추가, 실험 성공 기준 표 추가 |
| 2026-10-23 | 초안. AS-IS Lock 지도, 이전 순서 결함 4개, 수정 순서 후보(Shift를 Request 앞으로), 쌍별 순환 검사, 실패 응답, 재현 Test 계획 |
