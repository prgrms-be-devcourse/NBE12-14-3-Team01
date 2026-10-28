# Domain 개발 가이드 — Java 고정 → Kotlin 전환 → Improvement → 통합 검증

| 축 | 상태 |
|---|---|
| 대상 | V1~V5 Domain Owner (MIGRATION-MATRIX §1) |
| ③ 문서 | `DRAFT` (2026-10-25). 팀 승인 전 사용자 잠정 원칙 |
| 기준 | Java 기준선 `a148a62` 394/394 PASS × 2 (EV-010·011, `LOCAL_ONLY`) |
| 규칙 우선순위 | [`AI-RULES`](../ai/AI-RULES.md) > 이 문서. 검증 원칙은 [`TEST-STRATEGY`](../evidence/TEST-STRATEGY.md), 공백은 [`TEST-GAP-ANALYSIS`](../evidence/TEST-GAP-ANALYSIS.md), 코드 리뷰 항목은 [`KOTLIN-REVIEW-CHECKLIST`](../migration/KOTLIN-REVIEW-CHECKLIST.md) |

> 각 Owner가 자기 Domain을 따로 진행하되, **같은 순서·같은 Test 규칙·같은 증거 형식**을 쓰게 하는 것이 목적이다. 정책 결정은 이 문서에 없다 (DECISION-LOG, 정책 카드). **이 문서는 팀에 제안하는 개발 방식이며 합의 전이다** — 팀원에게 강제하지 않는다.
> **필요한 만큼만 추가한다:** 기존 Test가 이미 충분한 영역(예: Pattern HTTP 45개, 근무표 공개 Rollback)은 기존 근거를 쓰고, 공백이 큰 영역(TEST-GAP-ANALYSIS §4 A)에 집중한다. 모든 Domain에 같은 수의 Test를 요구하지 않는다.

## 0. 무엇을 어디서 보는가

| 알고 싶은 것 | 원본 |
|---|---|
| 내 Domain 범위·Owner·필수 회귀 Test | MIGRATION-MATRIX §1·§3 |
| 내 Domain API·정책·DoD | `docs/delivery/<Domain>.md` (V4만 있음, 나머지는 [`TEMPLATE`](TEMPLATE.md)로 작성) |
| 사용자 경로와 Domain 간 연결 | [`USER-PATH-REVIEW`](../contract/USER-PATH-REVIEW.md) UP-01~07 |
| 내 Domain의 Test 공백 | TEST-GAP-ANALYSIS §3·§4 |
| 기존·신규 Test ID와 기대값 보존/변경 | [`TEST-INVENTORY`](../evidence/TEST-INVENTORY.md) §5-1·§5-4 |
| API·화면·ErrorCode·알림 계약 | `docs/contract/` |
| 실행 증거 | [`TEST-EVIDENCE`](../evidence/TEST-EVIDENCE.md) |

## 1. Domain별 출발점 (2026-10-25 정적 확인)

| Domain | Owner (초안) | 기존 Test (Method) | 가장 큰 공백 (TEST-GAP-ANALYSIS) | 전환 전 먼저 할 A 항목 |
|---|---|---|---|---|
| V1 Auth·Security | MIGRATION-MATRIX §1 | 26 | 무토큰·권한 경계 일부만 실제 Filter | G-A5 |
| V2 Workplace·Member·Pattern | 〃 | 98 | `WorkplaceController` HTTP Test가 Filter를 끔 | G-A5, (재가입은 Improvement) |
| V3 Schedule·Shift·Unavailable | 〃 | 104 | `Shift`·`UnavailableTime` HTTP 0, Repository Test H2, ErrorCode Test 0 9개 | G-A1, G-A6, G-A7 |
| V4 Substitute | 〃 | 93 | Controller 3개 HTTP 0, 승인 Rollback, 알림 연결 | G-A1, G-A3, G-A4, G-A7 |
| V5 Notification | 〃 | 19 | HTTP 0, Commit 후 알림 생성 통합 1개뿐, 목록 Query MySQL 미검증 | G-A1, G-A3, G-A8 |
| 공통 `global` | **미지정 → 임시 담당 백혜승 (V5 Owner 겸임, 재배정 가능, 팀 확인 전)** | 33 | `ApiResponse` JSON 형식 | G-A2 (모든 Domain보다 먼저. 공통 Owner와 합의가 필요한 선행 항목) |

## 2. Domain 작업 순서 (각 단계는 별도 PR)

```text
Step 0 준비 → Step 1 Java 고정 → Step 2 Production 전환 → Step 3 Test 전환(필요한 것만)
→ Step 4 Kotlin다운 개선 → Step 5 Improvement (정책 승인 후) → Step 6 경로 통합 검증
```

| Step | 하는 일 | 산출물 | 통과 조건 (TEST-STRATEGY §6) | 하지 않는 것 |
|---|---|---|---|---|
| 0 준비 | Delivery Sheet 작성(없으면), 내 Domain의 UP 행·불변식·알림·복구 확인, 공백 A~E 중 내 몫 확정 | Delivery Sheet, Task Card | 채울 수 없는 칸이 있으면 질문 (AI-RULES §1) | 코드 작성 |
| 1 Java 고정 | 내 Domain의 **A 공백 Test를 Java로** 작성 (develop 기준) | Java Test PR | 새 Test가 **현재 Java 동작 그대로** 통과, 기존 Test Diff 0, Production Diff 0, EV 기록 | 기대값을 새 정책으로 쓰기 |
| 2 Production 전환 | 내 Domain Production을 Kotlin으로 | Migration PR | 기준선 394 + Step 1 Test 전부 통과, HTTP Snapshot·Schema 동일, Test는 호출 문법 수정만 (목록 첨부) | 동작 변경·구조 개선 |
| 3 Test 전환 | 필요한 Test만 Kotlin으로 | Test PR | Test Case 목록·Assertion 기대값·실행 수 동일 | 기대값 변경 |
| 4 Kotlin다운 개선 | null·Optional 경계·불변성 | Refactor PR | 같은 Test 재통과 + 체크리스트 | 동작 변경 |
| 5 Improvement | 승인된 V3 정책 | Feature PR | 해당 `PROPOSED_IMPROVEMENT` Test 작성·통과, 기대값 변경은 §5-4·`APPROVED` 행으로만 | 승인 전 정책 |
| 6 경로 통합 | 내 Domain이 소유한 경로 전체 Test (§5) + 전체 Suite | Integration PR | 경로 Test 통과 + 전체 Suite + EV | — |

**공통 Domain(`global`)과 순서:** G-A2는 공통 Owner가 가장 먼저 한다. Domain 전환 순서는 의존 방향(`user ← workplace ← shift ← substitute ← notification`, MIGRATION-MATRIX §5)을 참고해 팀이 정한다. 혼합 단계(일부 Domain만 Kotlin)에서는 KOTLIN-REVIEW-CHECKLIST §1의 Java ↔ Kotlin 호출 호환을 지킨다.

## 3. Test 작성 규칙

### 3-1. 무엇을 확인하는가 (Assertion 깊이)

| Test 종류 | 반드시 확인 | 자주 빠지는 것 |
|---|---|---|
| HTTP 계약 | Status, `$.success`·`$.code`·`$.message`, `$.data`의 **Field 이름과 자료형**, null Field가 응답에 있는지/없는지, 날짜·시간 문자열 형식 | 실패 응답의 `code` 문자열, Validation 실패 (`COM-001`) |
| Command (Service·통합) | 반환값 + **DB 재조회 최종 상태** (`flush()` + `clear()` 후 다시 읽기) | 실패 시 **부작용 없음** (Row 불변, 이벤트·알림 0) |
| 알림 | 받는 사람(소속 ID), Type, Row 수, **보내지 않아야 할 사람 0건** | Commit 전에는 생기지 않음, 업무 실패 시 0건 |
| 동시성 | 각 요청 결과 + **최종 불변식** (담당자 1명, 활성 요청 ≤ 1 등) + Rollback | HTTP·예외만 보고 DB를 안 봄 |
| 권한 | 401·403·404 각각의 `code`와 "Lock·DB 변경 0" | 다른 근무지·퇴사자 경우 |

### 3-2. 공유 MySQL DB 규칙 (TEST-GAP-ANALYSIS §2)

Test MySQL Container는 JVM 동안 1개를 함께 쓰고, 정리 코드가 없다. 그래서:

1. **Test 데이터는 Test마다 고유하게 만든다** (이메일·이름·초대 코드에 고유 접미사). 고정 이름을 다른 Test와 공유하지 않는다.
2. **전체 Table 조회·전체 건수 Assertion을 쓰지 않는다** (`findAll()`, `count()` 절대값). 꼭 필요하면 같은 Test 안의 **전후 비교**만.
3. **조회 범위를 좁힌다.** 목록·충돌 판정 결과를 확인할 때는 그 Test가 만든 근무지·사용자로 범위를 한정한다 (남은 데이터의 활성 요청·근무가 결과에 섞이지 않게).
4. **롤백 Test와 Commit Test를 구분한다.** 일반 통합 Test는 Class에 `@Transactional`(끝나면 롤백). AFTER_COMMIT 알림·동시성·Rollback 검증 Test는 `@Transactional`을 쓰지 않고 `TransactionTemplate`으로 실제 Commit한다 — 이 데이터는 남으므로 1·2를 반드시 지킨다.
5. 시간은 `TestClock`(`global/config`)으로 고정한다. `Thread.sleep`으로 순서를 맞추지 않는다.
6. H2에서 통과한 Repository Query는 MySQL에서 통과한다는 근거가 아니다. 새 Repository Test는 `@ActiveProfiles("test")` + `@AutoConfigureTestDatabase(replace = NONE)` (PR #78 방식)으로 MySQL에서 실행한다.
7. 위 규칙은 예방책이지 충분조건이 아니다. 정리 방식(명시적 Cleanup, 격리 DB 등)은 E4·E5 이후 팀이 정한다 (TEST-GAP-ANALYSIS §2).

### 3-3. 이름·구조

- `@DisplayName`에 업무 문장 ("수락한 사람이 퇴사하면 승인할 수 없다"). Method 이름은 영어 `상황_결과`.
- Given / When / Then 주석으로 단계 구분.
- 실패 Test는 **정확한 ErrorCode**를 확인한다 (예외 Class만 확인하지 않는다).
- 새 Test는 TEST-INVENTORY에 정식 ID로 등록하고 Test 주석이나 DisplayName에 ID를 적지 않는다 (ID 원본은 Inventory 하나).

## 4. Test 형식 예시 (Java, Step 1용)

아래는 **형식 예시**다. 실제 Fixture·Class 이름은 각 Domain 코드에 맞춘다.

**중요 (V5-J1 정적 확인):** `CustomAuthenticationFilter`는 요청 URI가 `/api/`로 시작할 때만 Token을 검사한다. 그래서 MockMvc로 `/notifications`처럼 Context Path 없이 부르면 **Filter가 Token 검사를 건너뛴다.** 실제 인증 경계를 확인하는 Test는 기존 `CustomAuthenticationFilterTest`·`WorkplaceSecurityIntegrationTest`처럼 `get("/api/v1/...").contextPath("/api/v1")`로 부른다. Context Path 없이 `@WithMockUser`로 부르는 기존 방식(예: `RegularShiftPatternControllerTest`)은 응답 형식 확인용이며 인증 경계의 근거가 아니다. 무토큰 401 `AUTH-004`, Bearer 형식 아님 401 `AUTH-005`, 잘못된 Token 401 `AUTH-006`.

**`.contextPath("/api/v1")`를 붙였다는 것만으로 실제 Filter가 동작했다고 단정하지 않는다.** 응답으로 확인한다 (V5-J1 정적 확인):

| 응답 | 누가 만드는가 | 증명하는 것 |
|---|---|---|
| Token 없음 → 401 `AUTH-004` | Spring Security의 `authenticationEntryPoint` (`SecurityConfig`) — `CustomAuthenticationFilter`는 헤더가 비면 그냥 통과시킨다 | Security Chain과 `authenticated()` 규칙이 그 경로에 적용됨 |
| 잘못된 Token → 401 `AUTH-006`, Bearer 형식 아님 → 401 `AUTH-005` | **`CustomAuthenticationFilter`만** 만든다 | 그 요청 URI(`/api/...`)에서 **우리 Filter가 실제로 Token을 검사함** |
| 정상 Token → 성공 응답 | Filter가 `SecurityContext`에 사용자를 넣고 `Rq.getActorId()`가 읽음 | Token → 실행자 연결 |

그래서 인증 경계 Test는 **세 경우를 함께** 둔다. `CustomAuthenticationFilter`는 `@Component`(`OncePerRequestFilter`)이고 `SecurityConfig`에서 `addFilterBefore`로도 등록돼 있다 — `@AutoConfigureMockMvc`는 기본으로 Filter를 포함하므로 `addFilters = false`를 쓰지 않는다.

### 4-1. HTTP 계약 Snapshot (G-A1·A2)

```java
@SpringBootTest
@AutoConfigureMockMvc               // 실제 Security Filter 포함
@ActiveProfiles("test")
@Transactional
class SubstituteRequestApiContractTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("보낸 대타 목록 응답 형식이 Java 기준선과 같다")
    void sentRequests_contract() throws Exception {
        // given: 고유 데이터로 요청 1건 (Fixture는 Domain에 맞춤)

        // 요청 URI = /api/v1/... (Filter는 getRequestURI()가 /api/로 시작할 때만 Token 검사),
        // contextPath = /api/v1 (Security 경로 규칙은 그 뒤 경로로 판단). 기존 CustomAuthenticationFilterTest와 같은 방식
        mvc.perform(get("/api/v1/substitute-requests/sent").contextPath("/api/v1")
                .header("Authorization", "Bearer " + accessTokenOf(requester)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").isString())
            .andExpect(jsonPath("$.data[0].requestId").isNumber())      // 동적 값은 자료형으로
            .andExpect(jsonPath("$.data[0].status").value("OPEN"))
            .andExpect(jsonPath("$.data[0].closedAt").value(nullValue())) // null을 내보내는지까지
            .andExpect(jsonPath("$.data[0].startAt",
                matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));  // 날짜 형식
    }
}
```

전환 전(Java)에 통과시키고, 전환 후(Kotlin) **같은 Test가 그대로** 통과해야 한다. Field 이름이 바뀌면 여기서 실패한다.

- Field 이름은 실제 DTO에서 가져온다 (예: `SentSubstituteRequestResponse`는 `requestId`, `shiftId`, `workplaceId`, `workplaceName`, `startAt`, `endAt`, `status`, `closeReason`, `createdAt`, `approvedAt`, `closedAt`). 계약 Test는 **모든 Field의 이름과 null 여부**를 한 번씩 확인한다.
- 인증: 실제 Filter 경계를 확인하려면 **Context Path 포함 경로 + `.contextPath("/api/v1")` + `Authorization: Bearer <accessToken>`** (`CustomAuthenticationFilter` L40-61)을 쓴다. 응답 형식만 볼 때는 기존 Test처럼 `@WithMockUser` + `Rq` Mock을 써도 되지만, 그 Test는 Filter 경계(G-A5)의 근거가 되지 않는다.

### 4-2. Commit 후 알림 생성 (G-A3)

```java
@SpringBootTest
@ActiveProfiles("test")             // @Transactional 없음: 실제 Commit 필요
class SubstituteApprovedNotificationIntegrationTest {

    @Autowired TransactionTemplate tx;
    @Autowired NotificationRepository notificationRepository;

    @Test
    @DisplayName("대타 승인이 Commit되면 요청자와 수락자에게 알림이 1건씩 생긴다")
    void approve_createsNotificationsAfterCommit() {
        // given: 고유 근무지·직원·수락된 요청 (Commit)

        tx.executeWithoutResult(s -> substituteRequestService.approve(requestId, managerUserId));

        // then: 받는 사람별 Row (전체 건수가 아니라 받는 사람 소속 ID로 조회)
        assertThat(notificationsOf(requesterMemberId)).extracting("type")
            .containsExactly(NotificationType.SUBSTITUTE_APPROVED);
        assertThat(notificationsOf(acceptedMemberId)).hasSize(1);
        assertThat(notificationsOf(otherCandidateMemberId)).isEmpty();   // 받지 않아야 할 사람
    }
}
```

### 4-3. 실패 시 부작용 없음·Rollback (G-A4)

```java
// 승인 실패 → Request·Candidate·Shift 불변, 알림 0
Throwable error = catchThrowable(() ->
    tx.executeWithoutResult(s -> substituteRequestService.approve(requestId, managerUserId)));

assertBusinessError(error, ShiftErrorCode.INVALID_WORKPLACE_MEMBER_VALUE);
SubstituteRequest reloaded = requestRepository.findById(requestId).orElseThrow();
assertThat(reloaded.getStatus()).isEqualTo(RequestStatus.ACCEPTED);
assertThat(shiftRepository.findById(shiftId).orElseThrow().getMember().getId()).isEqualTo(requesterMemberId);
assertThat(notificationsOf(requesterMemberId)).isEmpty();
```

### 4-4. 실행 순서 강제 동시성 (G-D1)

저장소에 이미 있는 **`SubstituteRequestConcurrencyTest.runSecondWhileFirstHoldsLock`** 방식을 표준으로 쓴다: 첫 Transaction을 Commit하지 않고 Latch로 멈춘 뒤 두 번째를 실행하고, 두 번째가 정해진 시간 안에 끝나지 않으면 Lock을 기다린다고 판단한다.

```java
Throwable secondError = runSecondWhileFirstHoldsLock(
    () -> commandA(...),      // 첫 번째: Lock을 잡고 Commit 전 대기
    () -> commandB(...)       // 두 번째: 그 Lock을 기다려야 함
);
assertBusinessError(secondError, EXPECTED_CODE);   // 허용한 결과
assertThat(finalInvariant()).isTrue();             // 최종 DB 불변식 (예: 담당자 1명)
```

한계: "끝나지 않았다"(Timeout)로 대기를 추정한다. 어떤 Lock을 기다리는지까지 확인하려면 MySQL `performance_schema.data_locks` 관찰을 함께 쓴다 (S4 E5에서 시범).

## 5. 사용자 경로 준비와 통합 검증

### 5-1. Domain이 준비할 경로 정보 (Step 0)

내 Domain이 관련된 UP 행마다 아래를 Delivery Sheet에 적는다 (USER-PATH-REVIEW 원본 참조, 복사하지 않음).

| 항목 | 질문 |
|---|---|
| 시작 조건 | 사용자는 어떤 상태에서 시작하는가 |
| 행동 순서 | 어떤 화면·API를 차례로 부르는가 |
| 단계별 기대 결과 | 무엇을 보여 주고 무엇을 저장하는가 |
| 거부 조건 | 언제 거부하고 어떤 code를 주는가 |
| DB 불변식 | 절대 깨지면 안 되는 조건 |
| 알림 | 누가 받고 누가 받지 않는가 |
| 복구 | 실패 후 사용자는 어떻게 다시 진행하는가 |
| Test 근거 | 어느 Test가 어느 단계를 확인하는가 (L1~L5) |
| 미검증 | 아직 Test가 없거나 실행 안 한 부분 |

### 5-2. 역할과 경로 통합 Test의 소유

| 역할 | 맡는 것 | 맡지 않는 것 |
|---|---|---|
| **경로 Owner** (그 경로를 시작하는 Command의 Domain Owner) | 경로 통합 시나리오 Test와 완료 조건, 각 Domain Test와의 연결 | 다른 Domain의 내부 규칙 Test 전부 |
| **각 Domain Owner** | 자기 Domain의 API·DB·정책·실패 조건 Test, 경로 Test에서 자기 Domain 결과를 확인하는 부분 리뷰 | 다른 Domain의 경로 완료 판단 |
| **공통 Owner** | `ApiResponse`, Security, `GlobalExceptionHandler` 계약 (G-A2·A5) | Domain 업무 규칙 |
| **통합 리뷰** | Domain 사이 연결 계약과 최종 사용자 흐름 검토, 경로별 진행 현황 | 개별 Domain Test 작성 |

다른 Domain의 결과(알림 등)는 경로 Test 안에서 확인하고, 그 Domain 고유 규칙은 해당 Domain Test가 따로 확인한다.

| 경로 Test | 소유 | 함께 확인하는 Domain |
|---|---|---|
| V2-N-53 퇴사 전체 | V2 | V3 (근무 인계·취소), V4 (대타 종료), V5 (알림) |
| V2-N-54 재가입 격리 | V2 | V4 (SUB-05·07), V5 (알림 목록) |
| V3-N-33 공개 근무 변경 | V3 | V4, V5 |
| V4-T-88~93 대타 흐름 (기존) | V4 | V5 (G-A3로 보강) |
| V5-N-19 알림 ↔ 대체 화면 | V5 | 각 화면 API의 Domain |

### 5-3. 통합 검증 시점 (마지막에 한 번이 아니라 작게 반복)

- 각 Domain의 Step 2 PR: 그 Domain + 공통 + 전체 Suite 통과, 기준선 394와 Test 목록 비교. **전환 직후 그 Domain이 걸린 기존 경로 Test를 바로 다시 실행한다** (예: V5 전환 후 대타 흐름 + 알림 G-A3).
- 여러 Domain이 전환된 뒤 경로 Test (Step 6): 경로 전체 + 전체 Suite, EV 기록.
- 결과 기록: TEST-EVIDENCE (AI-RULES §6 형식, 로컬이면 `LOCAL_ONLY`).

### 5-4. 계획에 담당이 없던 항목의 임시 담당 (팀 확인 전, 재배정 가능)

시간이 부족하거나 여유 있는 팀원이 있으면 넘긴다. 넘길 때 이 표와 MIGRATION-MATRIX를 함께 고친다.

| 항목 | 임시 담당 | 비고 |
|---|---|---|
| 공통 응답·예외 계약 (G-A2) | 백혜승 | 공통 Owner(D-NOW-01) 지정 시 이관 |
| 통합 리뷰 (경로 진행 현황·연결 계약) | 백혜승 | 경로 Owner는 각 Domain Owner |
| S4 실험 조율 (E2~E6), 동시성 회귀 편입 (G-D1) | 백혜승 | H6 담당(D-NOW-01·DL-009)은 팀 결정 |
| Frontend 수동 확인 목록 관리 (G-E1~E6) | 백혜승 | 화면 수정은 Frontend 담당 |
| 문서 체계 유지 (TEST-INVENTORY·EVIDENCE·GAP) | 백혜승 | — |

배포 담당(Railway 확인, OPS-01 적용)은 운영 접근 권한이 필요해 임시 담당을 두지 않는다 (D-NOW-01, 팀 결정).

## 6. PR 확인 목록 (Domain 공통)

- [ ] 이 PR은 Step 하나만 담는다 (§2)
- [ ] Step 1이면 새 Test가 현재 Java 동작 그대로 통과 (새 정책 기대값 아님)
- [ ] Step 2이면 Test는 호출 문법 수정만, 수정한 줄 목록 첨부, HTTP Snapshot·Schema 비교 동일
- [ ] 공유 DB 규칙 (§3-2): 고유 데이터, 전체 조회 Assertion 없음, Commit Test 구분
- [ ] 실패 Test가 정확한 ErrorCode + 부작용 없음을 확인
- [ ] 새 Test를 TEST-INVENTORY에 정식 ID로 등록
- [ ] EV 기록 (SHA, 환경, 명령, 실행 수, 결과)
- [ ] KOTLIN-REVIEW-CHECKLIST §5 (Step 2~4)

## 7. V5 알림 + 동시성 담당 진행 가이드

### 7-1. 현재 상태 (정적 확인 + E1)

| 영역 | 사실 |
|---|---|
| 알림 생성 | Listener 7개 (근무표 공개 1 + 대타 6) 모두 `@TransactionalEventListener(AFTER_COMMIT)`, 예외는 잡아서 로그만 (업무 성공 유지 = G03-D-22). 저장은 `NotificationService`의 `REQUIRES_NEW` |
| 알림 Test | `NotificationServiceTest` 14 (생성 7종 + 읽음·목록 7, 모두 Mock), `NotificationTest` 3, 읽음 동시성 1 (MySQL), 공개 알림 통합 1 (MySQL, AFTER_COMMIT 실제 확인) — E1에서 모두 PASS |
| 공백 | 대타 6개 Event의 Commit 후 생성 (G-A3), Notification HTTP 계약 (G-A1), 목록 현재 소속 Query의 MySQL 확인 (G-A8), 받는 사람 퇴사 필터 없음·묶음 실패 (G-B4, AS-IS), 읽음의 권한 확인 전 Lock (G-B2) |
| 동시성 | 순서 강제 Test는 대타 4개뿐 (G-D1). Lock 순서 반례는 E5 (DL-009) |

### 7-2. 진행 순서

| 순서 | 할 일 | 산출물 | 근거 |
|---|---|---|---|
| 1 | **Java 고정 (Step 1)** — 작은 Task로 나눔 (§7-3): HTTP 계약 → Commit 후 알림·Rollback·알림 실패 → 목록 Query MySQL → 증거 기록. 기대값은 **현재 Java 동작** (예: 남의 알림 403 `NOT-002`, 없는 ID 404 `NOT-001`). 이것은 그 동작이 옳다는 뜻이 아니다 — V3 기대값(G03-D-10·16)은 Step 5 | Java Test PR + EV | G-A1·A3·A8, G-B4 |
| 2 | **E5 동시성 실험 참여**: 알림 읽음 Lock 점유 관찰, 대타 승인 권한 확인 전 Lock (M3-T2) | EV (`LOCAL_ONLY`) | G-B1·B2, EXPERIMENT-PLAN E5 |
| 3 | **V5 Production 전환 (Step 2)**: Listener의 phase·`REQUIRES_NEW`·예외 처리 그대로, Kotlin Class가 Proxy 대상인지 (`plugin.spring`), Event Class는 V3·V4 전환 시점과 맞춤 | Migration PR | KOTLIN-REVIEW-CHECKLIST §2 |
| 4 | **Improvement (Step 5, 정책 승인 후)**: 새 Type 6개, 받는 사람 규칙 R1~R5, 합치기, 실행자 제외 (X-4), 읽음 응답 code (G03-D-10·16 결정 후), 관리자 끝난 요청 대체 화면 (API-SB-02 연동) | Feature PR | NOTIFICATION-TYPES, V5-N-01~20 |
| 5 | **경로 통합 (Step 6)**: V5-N-19 알림 ↔ 대체 화면, 다른 Domain 경로 Test에서 알림 확인 부분 지원 | Integration PR | §5-2 |
| 6 | **동시성 정식 회귀 편입**: E5에서 재현된 순서 강제 Test를 §4-4 형식으로 정식 Test화 (Domain Owner와 함께) | Test PR | EXPERIMENT-PLAN §0-3 |

### 7-3. Java 고정 Task 분할 (제안, 승인 전)

Task 이름은 작업 이름이며 Test ID가 아니다 (Test는 TEST-INVENTORY에 정식 ID로 등록).

| Task | 목표 | 범위 | 산출물 | 선행 |
|---|---|---|---|---|
| V5-J1 | 현재 Java 알림 동작 확인 | 코드 읽기: 목록 Query(`rm.leftAt is null`), 읽음(`findByIdForUpdate` → 본인·현재 소속 확인 → `markAsRead`), 7개 Listener(AFTER_COMMIT, 예외 로그), 생성(`REQUIRES_NEW`), 받는 사람 조회(`findAllById`) | 동작 표 (Delivery Sheet V5 초안) | — |
| V5-J2 | 알림 HTTP 계약 고정 | `GET /notifications`, `PATCH /notifications/{id}/read` — 성공, 없는 ID, 남의 알림, 과거 소속 알림, 이미 읽은 알림 다시 읽기, 무토큰 401. JSON Field 이름·자료형·null, 다른 알림 불변 | Java 기준선 Test (Security Filter 포함), 이후 PR #60에서 Kotlin 전환 | G-A2 범위 합의 (`ApiResponse` 부분). **완료 기록:** Java Test PR #16 병합, V5-N-21~30 로컬 10/10·전체 404/404와 CI Suite 성공 (EV-017~020); Kotlin Test 전환 PR #60 병합, Backend/Docker CI Suite 성공. Kotlin Test별 결과는 develop `0aec24f` 로컬 통합 실행 10 / 10 PASS (`LOCAL_PASSED`, TEST-INVENTORY §7 ②). |
| V5-J3 | Commit 후 알림·Rollback·알림 실패 | 현재 대타 Event 6개 × G-A3의 4경우. **근무표 공개 알림(Listener 7번째)의 Rollback·알림 실패 경우를 넣을지는 J3 분석에서 정한다** — 기존 `SchedulePublishedEventListenerIntegrationTest`(Service Mock, Commit 후 호출 시점만)와 V3 `ShiftScheduleConcurrencyTest`(공개 알림 Row 수 일부)를 먼저 대조하고, 경로 담당(V3 Owner)과 위치를 정한다 (중복 작성 금지). 알림 실패는 Test 전용 Bean 교체(`@MockitoSpyBean` 등)로 주입 | Java 통합 Test (Commit 사용, §3-2 규칙) | V4 Owner와 V4-N-04 범위 조율 |
| V5-J4 | 목록 Query·소속 격리 | 현재 소속만 반환, 종료된 소속 알림 제외, 다른 사용자 알림 제외 (MySQL) | Java Repository·통합 Test | — |
| V5-J5 | Java 회귀 기준선 기록 | J2~J4 + 기존 394 실행, EV 기록 | TEST-EVIDENCE EV | J2~J4 |
| V5-K1 | V5 Kotlin 전환 (Step 2) | Production만 | Migration PR | J5, E2·E3 결과 (= V5 **전환 관문**. 진행 상태는 MIGRATION-MATRIX §2에 기록) |
| V5-I1 | V3 알림 정책 (Step 5) | 새 Type·받는 사람 규칙·X-4·읽음 code | Feature PR | 정책 승인 |

**V5에서 특히 볼 불변식:** 업무가 실패하면 알림 0건 / 업무가 성공하면 알림 실패가 업무를 되돌리지 않음 / 받지 않아야 할 사람 0건 (정책 적용 시 `SHIFT_CHANGED`의 PENDING 후보, 관리자 수신자 역할의 실행자, 수신 범위 밖 소속). 실행자가 요청자 역할이면 요청자 알림은 유지한다. AS-IS 보호와 정책 적용 후 기대값은 분리한다 / 같은 알림을 동시에 읽어도 첫 `readAt` 유지.


### 7-4. 정책 문서 1차본의 사용 범위와 후속 Task

2026-10-26 사용자 지시에 따라 정해진 서비스 정책과 불변식은 1차 기준으로 사용한다. 모든 잠재 위험을 문서 PR에서 해결하려고 Migration 전체를 멈추지 않는다. 정책을 바꾸는 발견은 Decision Log, 구현 형식은 담당 Task, 실행 결과는 Evidence로 갱신한다.

- V1: 서버 로그아웃·Origin/Cookie·Refresh 조건부 무효화의 구현/HTTP 회귀. 현재 JWT/배포 설정 변경은 별도 OPS Task.
- V2: 퇴사 미리보기·확정 원자성·재가입 새 소속 격리·활성 소속 유일성. 착수 시 previewContext 원본 무결성·기간·반복 규칙을 확정한다.
- V3: 공개 후 추가/취소/담당자 변경·배정 이력·공식 근무 중복 불가. 착수 시 newShiftKey 후보와 소속 Guard를 확정하고 V2 퇴사 경합을 함께 확인한다.
- V4: 퇴사/근무 변경의 대타 종료·후보 정리와 기존 승인 경합. 비정상 활성 요청2개 이상 처리 정책이 필요한 경로는 그 Task에서 먼저 결정한다.
- V5: 상세 수신자 집합·합치기·업무Commit/알림실패격리·대체조회화면. J2/J3/J4는 AS-IS 보호이며 미래 정책 구현을 통과한 것으로 쓰지 않는다.
- Frontend: 미리보기 영향 표시→근무별 선택→확정, 충돌확인/STALE/STATE_CONFLICT를 구분. STALE 후 새 비교정보·유효한 선택 복구, 오류/응답유실 시 자동업무재전송 금지.
- 공통/배포: 기술 실험/Lock/Schema/오류 응답 정합성을 해당 구현 Task의 완료조건으로 관리한다. 운영DDL·혼합버전·복구·키교체 미확인은 문서공개와 별개로 배포를 차단한다.

정책 문서의 1차 완료는 정의한 서비스/금지 상태/오류 복구/대표 검증이 연결됐다는 뜻이다. 비교 정보의 저장·서명 방식, 실제 MySQL 경합, 스키마 이행이 아직 미실행이면 해당 기술 Task는 미완료다. 기존 동작을 보존하는 J4 제출 및 J5→E2/E3→V5-K1 작업은 이 기술 후보 모두의 선행 구현을 기다릴 필요가 없다. 단 재현된 보안·데이터 무결성 위반은 후속으로 조용히 미루지 않고 영향 범위를 분류해 해당 작업을 차단한다.

## 8. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-25 | 초안. Domain별 출발점, 작업 순서 Step 0~6과 통과 조건, Test 작성 규칙 (Assertion 깊이, 공유 MySQL DB 규칙, 이름), Java Test 형식 예시 4종 (HTTP 계약·Commit 후 알림·Rollback·순서 강제), 경로 준비·통합 Test 소유, PR 확인 목록, V5 알림·동시성 담당 진행 순서 |
| 2026-10-25 | 교차 검토 반영: 합의 전 제안 명시, 필요한 만큼만 추가, 역할 4종(경로 Owner·Domain Owner·공통 Owner·통합 리뷰), 통합 검증은 Domain 전환 직후 작게 반복, 공유 DB 규칙에 조회 범위·충분조건 아님 추가, 계획에 없던 항목 임시 담당 백혜승 (재배정 가능, 팀 확인 전), V5 Java 고정 Task 분할 J1~J5·K1·I1 |
| 2026-10-25 | 보완: `.contextPath`만으로 Filter 동작을 단정하지 않음 — AUTH-004(Security entry point)·AUTH-005/006(우리 Filter만)·정상 Token 세 경우로 확인, 예시에 요청 URI·Context Path 주석 |
| 2026-10-26 | §7-3: V5-J2 진행 (PR #16), V5-J3 범위에 근무표 공개 포함 여부는 J3 분석에서 결정 (기존 V3·V5 Test 대조), V5-K1 선행조건이 전환 관문이며 진행 상태는 MATRIX §2라는 위치 표시 |
| 2026-10-25 | 정정 (V5-J1 정적 확인): §4-1 HTTP 예시가 Context Path 없이 호출해 실제 Filter를 건너뛰는 형태였음 → `/api/v1/...` + `.contextPath("/api/v1")`로 수정, 인증 경계 Test와 응답 형식 Test 구분 명시 |
