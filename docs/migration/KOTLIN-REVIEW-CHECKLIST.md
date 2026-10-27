# Kotlin 전환 리뷰 체크리스트

| 축 | 상태 |
|---|---|
| 결정 단계 | **사용자 잠정 원칙** (S2-6B, 2026-10-25). 팀 리뷰 전 |
| ② 정책 | `PROPOSED` / 팀 `PENDING` |
| ③ 문서 | `DRAFT` |
| 실험 | 아래 "확인 방법"의 실험·Test는 모두 실행 전 (`NOT_RUN`) |

> 구현 규칙의 원본은 [`AI-RULES`](../ai/AI-RULES.md) §5다. 이 문서는 PR 리뷰에서 확인할 항목과 판단 기준만 모은다. 충돌하면 AI-RULES를 따른다. 검증 단계·통과 조건은 [`TEST-STRATEGY`](../evidence/TEST-STRATEGY.md).
> 수치는 `a148a62` 정적 집계 (S2-5B·S2-6B).

## 1. Java ↔ Kotlin 호출 호환성 (Production 전환 PR, Java Test 유지 단계)

Production을 Kotlin으로 바꿔도 Java Test가 그대로 컴파일되고 같은 의미로 실행되는지 본다. 호환 선택지를 쓸지, Java Test의 **호출 문법만** 바꿀지는 대상마다 판단한다. 어느 경우든 Test의 검증 의미와 기대값은 보존한다 (TEST-STRATEGY §3).

| 항목 | 위험 | 규모 | 선택지 (우선 검토 순) | 확인 방법 |
|---|---|---|---|---|
| Java `record` DTO → Kotlin | Kotlin `data class`의 접근자는 `getClosedAt()`. Java Test의 `closedAt()` 호출이 컴파일되지 않음 | record 52개, Test의 record 접근자 호출 178회 (14개 파일) | ① **`@JvmRecord`를 우선 검토할 선택지로 본다 (규칙 아님).** DTO마다 확인 후 결정: JVM 제약(모든 Property `val`, 다른 Class 상속 불가, JVM 16+ — 이 프로젝트는 Java 25), 생성자 형태, Jackson 직렬화 결과(Field 이름·순서·null 출력)가 Java record와 같은지. ② 부적합하면 Java Test의 호출 문법만 변경 (`closedAt()` → `getClosedAt()`). ③ 그 DTO를 Test 전환 PR까지 Java로 유지 | M0-6 확장: 같은 DTO를 record / `@JvmRecord` / `data class`로 직렬화해 JSON 비교 |
| Lombok `boolean` 접근자 | Lombok `@Getter boolean success` → `isSuccess()`. Kotlin `val success: Boolean` → `getSuccess()`. `val isSuccess` → `isSuccess()`지만 JSON Field 이름이 바뀔 수 있음 | `ApiResponse.success` (Java Test에서 `isSuccess()` 15회, 3개 파일) | `@get:JvmName` 등 접근자 이름 유지 선택지 / Java Test 호출 문법 수정. **JSON의 `success` Field 이름은 계약이라 바뀌면 안 됨** | Jackson 직렬화 결과 비교 (HTTP 계약 Snapshot) |
| Entity 생성자 | Kotlin 기본값 인자는 Java에서 보이지 않음. 인자 순서가 바뀌면 다른 값이 들어감 | Test의 `new Entity(...)` 241회 | 기존 Java 생성자 시그니처를 Kotlin 생성자로 그대로 유지 / 필요한 곳만 `@JvmOverloads` (무조건 붙이지 않음) / Java Test의 Fixture 생성 호출 수정 (같은 상태를 만들 때만) | 컴파일 + 기존 Test 통과 |
| `ReflectionTestUtils.setField` | Field 이름이 바뀌면 실패. `val`(final) Field의 Reflection 변경은 JDK에 따라 제한될 수 있음 | 51회 | Backing Field 이름 유지, 상태 Field는 `var` + `private set` (AI-RULES §5) | 기존 Test 통과 |
| `@Value` Field 주입 | Java의 `@Value private boolean cookieSecure` 같은 Field 주입을 Kotlin으로 옮길 때 non-null `val`은 생성 시점에 값이 없어 그대로 옮길 수 없음 | `Rq` (`custom.cookie.*` 3개) | 생성자 인자 `@Value` / `@field:Value lateinit var`(primitive는 불가) 중 선택. 설정 값·기본값 동작 동일 | `RqTest` 통과 + Cookie 속성 확인 |
| Static 호출 | Java의 `Xxx.from(...)` 같은 static 메서드 → Kotlin `companion object` | `SentSubstituteRequestResponse.from` 등 | `@JvmStatic` / 호출 문법 수정 | 컴파일 |
| Lombok Builder | Kotlin에는 Lombok Builder가 없음 | Production `@Builder` 1개 (`ApiResponse`, private), Test의 `.builder()` 0회 | 해당 없음에 가까움. 내부 생성 경로만 확인 | 컴파일 |
| 혼합 단계의 Lombok 참조 | Kotlin 코드가 아직 Java인 다른 Domain의 Lombok 생성 멤버를 호출 | MIGRATION-MATRIX §5 의존 관계 | `kotlin("plugin.lombok")` (Alpha, DL-004) / 전환 순서 조정 | 컴파일 |

## 2. Spring·JPA·Jackson 동작 (Test로 확인)

| 항목 | 확인할 것 | 근거 | 확인 Test |
|---|---|---|---|
| `@Transactional` Proxy | `kotlin("plugin.spring")`이 Spring Annotation Class를 열어 주므로 "final이라 실패"가 기본은 아니다. 남는 위험: Annotation 없는 Class의 Proxy, 같은 Class 안 자기 호출(Java와 같음), private 메서드의 `@Transactional` | TS-02 (`plugin.spring`) | M0-4: 예외 시 Rollback 발생 확인 |
| JPA Entity | no-arg 생성자, all-open(JPA preset, Kotlin 2.3.20+), LAZY Proxy, 변경 감지 반영 | TS-03, AI-RULES §5 Entity | M0-5: `@ManyToOne(LAZY)` 조회, 상태 변경 후 Commit 반영 |
| 영속 전 ID | `BaseEntity.id`는 영속 전 null → `Long?`, `!!` 대신 `requireNotNull` | AI-RULES §5 ID | 저장 전후 ID 사용 경로 |
| Request DTO null | 필수 Field 누락 시 Jackson이 Bean Validation 전에 실패하면 응답이 `COM-001`에서 바뀜 | AI-RULES §5 Request DTO | M0-6: 누락·null 요청 → 기존 code |
| Response JSON | Field 이름·순서·null 출력·날짜 형식 | AI-RULES §5 Response DTO | HTTP 계약 Snapshot (V4-N-01 방식) |
| Event | `@TransactionalEventListener` phase 그대로 | AI-RULES §5 Event | V4-N-04, V5-N |
| Lock·Query | `@Lock`·`@Query` 문자열·순서 그대로 | AI-RULES §5 Lock | Diff 검토 + 동시성 Test |

## 3. Kotlin다운 코드 리뷰 기준 (단계 4, 리뷰·정적 분석)

scope 함수(`let`·`run`·`also`·`apply`)를 **얼마나 썼는가**는 기준이 아니다. 동작이 같은 두 코드 중 더 읽기 쉽고 의도가 드러나는 쪽을 고른다.

| 주제 | 기준 | 예 |
|---|---|---|
| null 안전성 | `!!` 금지 (꼭 필요하면 사유 주석). 기본값은 `?:`, 사전조건은 `require`·`requireNotNull`, 상태 조건은 `check` | `member.nickname ?: "익명"` (불필요한 `let` 감싸기보다 명확) |
| scope 함수 | `?.let`은 null이 아닐 때만 할 일이 있을 때, `also`는 부수 작업(로그 등), `apply`는 객체 설정, `run`은 계산 결과 반환. 중첩 scope 함수와 `it` 남용은 피함 | — |
| 불변성 | `val` 우선, 공개 컬렉션은 읽기 전용 타입. Entity 상태 Field는 `var` + 제한 setter (AI-RULES §5) | — |
| 분기 | 상태 Enum 분기는 `when`으로 모든 값을 다룸 (`else`로 새 값을 숨기지 않음) | 대타 종료 사유·Candidate 상태 |
| Optional 경계 | Kotlin 내부 코드의 불필요한 `Optional`은 단계 4에서 nullable로. **Spring Data `CrudRepository.findById`는 `Optional` 계약이라 그대로**이며 Kotlin 쪽은 `findByIdOrNull` 확장 사용. 직접 정의한 Query Method를 `T?` 반환으로 바꾸면 호출 계약 변경이므로 호출자 Test를 함께 확인 | — |
| 확장 함수 | 동작을 바꾸지 않는 범위에서만, 전후 비교 사례로 기록 (AI-RULES §5) | — |
| Entity | `data class` 금지, `equals`/`hashCode` 새로 추가 금지 (AI-RULES §5) | — |
| 예외 | 업무 오류는 기존 `BusinessException(ErrorCode)` 경로 유지. Kotlin 표준 예외(`IllegalArgumentException` 등)가 `COM-500`으로 새지 않는지 | `require` 실패가 업무 오류 응답을 대신하지 않게 |

## 4. 정적 분석 도구 (도입 미정)

ktlint(포맷)·detekt(복잡도, `UnsafeCallOnNullableType` 등)는 **IMP-07 `PENDING`**이다. 이 문서는 도입을 결정하지 않는다. 도입하면 기존 위반 Baseline + 보고만으로 시작하는 방식이 후보다 (Gradle Plugin 추가는 승인된 Task에서만, AI-RULES).

## 5. PR 리뷰 확인 목록

- [ ] 이 PR의 단계(전환 / Test 전환 / Kotlin다운 개선 / Improvement)가 하나인가 (TEST-STRATEGY §2)
- [ ] Test Diff가 TEST-STRATEGY §3 허용 범위 안인가 (호출 문법만 수정한 줄 목록 첨부)
- [ ] §1 호환 선택지를 쓴 곳마다 확인 근거(JSON 비교 등)가 있는가
- [ ] HTTP 계약 Snapshot과 Schema 비교가 같은가 (Production 전환 PR)
- [ ] `!!` 0 (예외는 사유 주석), Entity `data class` 0, Lombok Annotation 0 (Kotlin 코드)
- [ ] `@Lock`·`@Query`·Event phase가 그대로인가
- [ ] Java SHA와 Kotlin SHA 양쪽의 EV가 있는가

## 6. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-25 | 초안 (S2-6B). Java ↔ Kotlin 호출 호환성 8개 항목(`@JvmRecord`는 DTO별 확인 후 결정하는 선택지, 부적합하면 Java Test 호출 문법만 변경), Spring·JPA·Jackson 동작 7개, Kotlin다운 코드 기준, Optional 경계, 정적 분석 도구 미정, PR 확인 목록 |
