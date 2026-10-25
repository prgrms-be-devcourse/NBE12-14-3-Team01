# S4 실험 계획 (S4-P)

| 축 | 상태 |
|---|---|
| 결정 단계 | **계획 초안** (S4-P, 2026-10-25). 실험 실행 승인 없음 |
| ② 정책 | 이 문서는 정책을 정하지 않는다. 인용하는 정책은 사용자 잠정 결정 / 팀 `PENDING` |
| ③ 문서 | `DRAFT` |
| 실행 | 이 문서 작성 중 Gradle·Test·Docker·DB 명령 실행 0. 아래 명령은 **초안**이다 |

> 검증 방법의 원칙은 [`TEST-STRATEGY`](TEST-STRATEGY.md), 결과 기록 형식은 [`TEST-EVIDENCE`](TEST-EVIDENCE.md)와 [`AI-RULES`](../ai/AI-RULES.md) §6을 따른다. 이 문서는 S4에서 **무엇을, 어떤 순서로, 어떤 격리 조건에서** 확인할지 정한다.

## 0. 원칙

1. **실험은 동작을 확인하고 정책을 정하지 않는다.** "DB·Spring에서 실제로 어떻게 되는가"까지만 기록한다. 결과가 정책 선택지 하나를 유리하게 만들어도 결정은 사용자·팀이 한다.
2. **실험마다 따로 승인받는다.** 앞 실험의 승인을 다음 실험으로 넓히지 않는다 (AI-RULES §8).
3. **실험 코드는 전용 Worktree에만 두고 정식 코드에 자동 반영하지 않는다** (사용자 결정 2026-10-25, 보완). `exp/s4-*` Branch의 별도 Worktree에서 작성·실행한다. 실험 Branch는 어디에도 병합하지 않고 Push·PR도 하지 않는다. 실험 코드의 로컬 Commit은 **그 실험의 승인 범위에 포함된 경우에만** 한다. 통합 Branch(`docs/switch-v3-policy-integration`)와 주 작업 공간에는 코드를 넣지 않는다. 실험이 끝나면 결과를 아래처럼 나눠 처리한다 — Branch를 지워도 재현 방법과 증거는 남긴다.

   | 실험 결과 | 처리 |
   |---|---|
   | 일회성 확인 (Kotlin 호환성 등) | 결과·환경·재현 절차를 TEST-EVIDENCE에 기록한 뒤 코드 정리 |
   | 실제 결함·반례를 재현한 Test | **정식 회귀 Test 편입 후보**로 TEST-INVENTORY에 등록. 편입은 별도 Task에서 다시 작성·검증 |
   | MySQL Query·DDL 실험 | SQL 원문, 실행 계획, DB 버전을 기록 |
   | 실제 코드에 반영할 변경 | 별도 구현 PR에서 처음부터 다시 검증 (실험 코드를 옮겨 붙이지 않음) |
   | 불필요한 임시 파일 | 정리 |
4. **Production 코드는 실험 Branch에서도 바꾸지 않는다.** 실험 코드는 `src/test/**` 아래 실험 전용 Package(`com.merge.backend.s4exp`)에만 둔다. Production 동작을 바꿔야 확인되는 가설은 이 계획 범위 밖이다.
5. **운영 자원 접근 금지** (AI-RULES §7): `prod` Profile 실행 없음, 운영 DB·Railway 접속 없음. DB는 Testcontainers MySQL만 쓴다.
6. **실행하지 않은 것은 `PASSED`로 쓰지 않는다.** 결과는 TEST-EVIDENCE에 `LOCAL_ONLY`로 남기고, TEST-INVENTORY ⑤는 그 EV를 근거로만 바꾼다. 동시성 판정은 `REPRODUCED` / `NOT_REPRODUCED` / `ENVIRONMENT_UNSUITABLE` / `FIXTURE_ERROR`.

## 1. 공통 환경 (정적 확인, `a148a62`)

| 항목 | 값 | 근거 |
|---|---|---|
| 기준 SHA | `a148a62` (`origin/develop`). 문서 Branch는 코드가 같다 | `git log` |
| JDK | 25 (Gradle toolchain) | `build.gradle.kts` L20. CI는 Temurin 25 |
| Gradle | 9.3.0 (Wrapper) | `gradle-wrapper.properties` |
| Spring Boot / Kotlin | 4.1.1 / 2.3.21 (`plugin.spring`·`jpa`·`lombok`) | `build.gradle.kts` |
| DB | Testcontainers MySQL 9.7.2 (`jdbc:tc:mysql:9.7.2`, test Profile) | `application-test.yml` |
| Session 격리 수준 | READ COMMITTED (Hikari) | `application.yml` L14 |
| Schema | test Profile `ddl-auto: create` (운영은 `update`) | `application-test.yml` L7 |
| Test 설정 | `useJUnitPlatform()`만 (Filter·Tag 없음) | `build.gradle.kts` L74-76 |
| JUnit XML 위치 | `backend/build/test-results/test/TEST-*.xml` | Gradle 기본값 |
| 실행 OS | 로컬 macOS (CI는 ubuntu-latest) | — |
| 필수 도구 | Docker (Testcontainers) | E0에서 확인 |

## 2. Worktree·결과 보관

| 용도 | 위치 (제안) | 규칙 |
|---|---|---|
| Java 기준선 | `~/lec/Project3/NBE12-14-3-wt/s4-baseline` (`a148a62` detached) | 코드 수정 0. 실행만 |
| 실험 | `~/lec/Project3/NBE12-14-3-wt/s4-exp-<이름>` (Branch `exp/s4-<이름>`, `a148a62`에서 시작) | `src/test/**/s4exp`에만 작성 |
| JUnit XML 원본 | `~/lec/Project3/s4-evidence/<EV-ID>/` (저장소 밖) | Git에 Commit하지 않음 (TEST-STRATEGY §5) |
| 저장소 기록 | TEST-EVIDENCE 행: SHA, 환경, 명령, 실행·PASS·FAIL·SKIP 수, 실패 Test 이름, 원본 위치, 원본 묶음의 SHA-256 | 요약만 |

**민감 정보 검사 (XML 보관·공유 전):** 아래 값이 XML(특히 `system-out`)에 들어 있는지 **건수만** 출력해 확인한다. 값 자체는 출력하지 않는다.
- `application.yml`의 JWT 비밀 키 기본값 (설정 파일에서 읽어 변수로만 비교)
- 실행 환경에 설정된 `APP_INIT_DEFAULT_PASSWORD`·`MYSQLPASSWORD` 등 비밀 환경 변수 값 (있으면 변수로만 비교. `application-init-data.yml`·`application-prod.yml`에는 기본값이 없다 — §5-1)
- JWT 형태 문자열 (`eyJ`로 시작), 쿠키 값
- 사용자 홈 절대 경로 (공유할 때만 가림)

건수가 0이 아니면 원본은 로컬에 두고, 공유본은 가린 뒤 만든다.

**원상복구:** 실험이 끝나면 Worktree 제거와 실험 Branch 삭제를 **각각 승인받아** 실행한다. `git clean`·`reset --hard`는 쓰지 않는다 (AI-RULES §8). Testcontainers Container는 Test 종료 시 제거되는지 `docker ps`로 확인한다.

## 3. 실험 목록

### E0. 실행 환경 준비

| 항목 | 내용 |
|---|---|
| 가설 | 로컬에서 JDK 25 + Gradle 9.3.0 + Docker로 Testcontainers MySQL 9.7.2 Test를 **안전하게** 실행할 수 있다 (운영 자원에 연결되지 않음) |
| 현재 근거 | CI(EV-008)에서는 `check`가 성공. 로컬 3차 저장소 실행 기록 없음 |
| 범위 | **환경 확인만.** 전체 Test·DB 실험은 하지 않는다 (E1부터) |
| 확인 항목 | ① 기준선 Worktree가 `a148a62`이고 작업 트리가 깨끗한가 ② `java -version`, `./gradlew --version`, `docker info --format '{{.ServerVersion}}'` ③ Testcontainers 조건 (Docker 실행 중, 이미지 받기 가능 여부 — 받기는 E1에서) ④ **운영 자원 연결 위험:** 셸 환경에 `SPRING_PROFILES_ACTIVE`·`MYSQLURL`·`MYSQLUSER`·`MYSQLPASSWORD`·`JWT_SECRET_KEY`·`APP_INIT_DEFAULT_PASSWORD`가 설정돼 있는지 **이름만** 확인 (값 출력 금지). 설정돼 있으면 E1 전에 해제 방법을 정한다. test Profile의 DB URL이 `jdbc:tc:`인지 재확인 ⑤ 결과 보관 위치(§2) 생성 가능 여부 ⑥ `.gitignore`가 `build/`를 제외하는지, 결과 보관 위치가 저장소 밖인지 ⑦ 원상복구 절차 (§2) |
| 성공 기준 | ①~⑦ 모두 확인. JDK 25 사용. ④에서 운영 값이 하나도 없음 |
| 실패 시 | 이후 실험 중단, 원인만 보고 (설치·설정 변경은 별도 승인) |
| 필요 승인 | 기준선 Worktree 생성(허용 변경: `git worktree add --detach`로 새 폴더 1개, 기존 Branch·Worktree 변경 없음) + 결과 보관 폴더 생성 + 위 확인 명령 실행 |

### E1. Java 전체 Test 기준선

| 항목 | 내용 |
|---|---|
| 가설 | `a148a62`의 전체 Test가 로컬 MySQL Testcontainers에서 통과한다. 정적 집계 Test Method 373개(정적 계산 실행 사례 394)와 실제 실행 수가 맞는다. 같은 조건 2회 실행에서 결과가 같다 |
| 현재 근거 | 정적 집계 (MIGRATION-MATRIX §3). 2차 `243f77c`에서 266/266 (EV-002, 다른 코드). 2차 `9858612` H2 실행에서 동시성 Test 3개 실패 (EV-001) |
| 아직 증명 안 됨 | 3차 코드의 개별 Test 결과 전부, 불안정 Test 유무 |
| 명령 초안 | ① `./gradlew test --no-daemon --rerun-tasks` → XML 복사 (Run 1) ② 같은 명령 다시 → XML 복사 (Run 2) ③ `./gradlew check --no-daemon` 1회 (CI와 같은 범위) |
| 결과 집계 | XML의 `tests`·`failures`·`errors`·`skipped` 합계, Test Case 이름 목록. Run 1·2 비교 |
| 판정 | Test Case마다 다음 중 하나로 기록한다. **`PASS`** 두 Run 모두 통과 / **`KNOWN_FAILURE`** 두 Run에서 같은 이유로 실패 / **`FLAKY`** Run마다 결과가 다름 / **`ENVIRONMENT_BLOCKED`** 환경 때문에 실행 불가 (Docker·이미지·메모리 등) |
| 정적 집계와의 관계 | 373 Method / 394 실행 사례는 정적 계산값이다. **실제 실행 수는 XML로 따로 기록**하고, 다르면 차이 원인(조건부 비활성화·동적 Test 등)을 적는다. 정적 값을 실행 결과로 쓰지 않는다 |
| 실패 처리 | 실패를 **고치지 않고 기대값도 바꾸지 않는다** (TEST-STRATEGY §2 단계 1). 실패마다 메시지, 두 Run 재현 여부, 실행 순서 영향 의심 여부, 2차 H2 실행(EV-001)과의 차이, 환경 영향 의심 여부를 기록한다. Migration 전에 해결할지 기준선에 "알려진 실패"로 둘지는 사용자·팀이 정한다 |
| 선행 조건 | E0 통과, XML 보관 위치·민감 정보 검사 방법 확정 (§2) |
| 기록 | EV-009 (Run 1), EV-010 (Run 2), EV-011 (`check`) — 번호는 실행 시 확정 |
| 필요 승인 | E1 실행. 결과를 Migration 기준선(`BL-TBD`)으로 선언하는 것은 팀 결정 |

### E2. Kotlin ↔ Java 상호운용 (KOTLIN-REVIEW-CHECKLIST §1)

| 항목 | 내용 |
|---|---|
| 가설 | ① Java record 접근자(`closedAt()`)를 쓰는 Java 코드는 `@JvmRecord data class`에서 그대로 컴파일되고, 일반 `data class`에서는 실패한다. ② 세 형태의 Jackson JSON(Field 이름·순서·null)이 같다. ③ Kotlin `val success: Boolean` / `val isSuccess` / `@get:JvmName("isSuccess")`에서 Java 접근자 이름과 JSON Field 이름이 어떻게 나오는지. ④ 기본값 인자가 있는 Kotlin 생성자는 Java에서 보이지 않고 `@JvmOverloads`로 보인다. ⑤ `@Value`를 생성자 인자 / `lateinit var`에 쓸 때 주입 결과. ⑥ `ReflectionTestUtils.setField`가 Kotlin `var`·`val` Backing Field에 동작하는지 (JDK 25 경고 포함) |
| 현재 근거 | 정적 분석: record 52개, Test의 record 접근자 호출 178회, `isSuccess()` 15회, 생성자 호출 241회, `setField` 51회 |
| 실험 코드 | `src/test/kotlin/.../s4exp/` Kotlin DTO·Bean, `src/test/java/.../s4exp/` Java 호출 코드. 실제 DTO(`SentSubstituteRequestResponse` 등)와 같은 모양의 **복제본**만 쓰고 원본은 건드리지 않는다 |
| 명령 초안 | `./gradlew test --no-daemon --tests 'com.merge.backend.s4exp.interop.*'` |
| 성공 기준 | 가설별 관찰값을 표로 기록 (컴파일 성공·실패 메시지 포함). "실패"도 결과다 |
| 결정에 쓰이는 곳 | DTO 전환 방식 기준 (TEST-STRATEGY §10), KOTLIN-REVIEW-CHECKLIST §1 선택지 |
| 필요 승인 | 실험 Worktree·Branch 생성, 실험 코드 작성, 실행 |

### E3. Spring Proxy·Transaction·JPA (TECH-STACK M0-4~6)

| 항목 | 내용 |
|---|---|
| 가설 | ① Kotlin `@Service` + `@Transactional`에서 예외 시 Rollback된다 (`plugin.spring`). 같은 Class 안 자기 호출에는 Transaction이 적용되지 않는다 (Java와 같음). ② Kotlin `@Entity` + `@ManyToOne(LAZY)`는 Proxy로 지연 조회되고, `var` + `private set` 상태 변경이 Commit 때 반영된다 (`plugin.jpa`). ③ non-null Request DTO에서 Field가 빠지면 응답이 `COM-001`과 다르게 나오는지, nullable + `@field:NotNull`이면 `COM-001`인지 |
| 실험 코드 | `s4exp.spring` Package의 Test 전용 Bean·Entity·Controller. Test 전용 Entity는 test Profile `ddl-auto: create`로 Table이 생긴다 (기존 `AuditingTestEntity`와 같은 방식, TECH-STACK-DECISIONS L147 "Test 전용 Bean의 범위" 주의 사항 적용) |
| 명령 초안 | `./gradlew test --no-daemon --tests 'com.merge.backend.s4exp.spring.*'` |
| 성공 기준 | 가설별 관찰값. Proxy Class 이름, Rollback 후 Row 수, 변경 감지 후 재조회 값, HTTP 응답 JSON |
| 필요 승인 | E2와 같은 Worktree 사용 가능 (실행은 별도 승인) |

### E4. MySQL Schema·제약·Lock 예외

| 항목 | 내용 |
|---|---|
| 가설 | ① Hibernate가 만든 Schema(test Profile)에서 Enum Column 형식(`NotificationType`, `RequestStatus` 등)이 MySQL `ENUM`인지 `VARCHAR`인지 (NOTIFICATION-TYPES §1 "추정 ENUM" 확인). ② U2': 생성 Column + 복합 Unique로 "근무지·사용자당 현재 소속 1개"를 MySQL 9.7.2에서 강제할 수 있고 Hibernate가 그 DDL을 만들거나 받아들인다. ③ Deadlock(1213)·Lock 대기 초과(1205) 시 Spring이 던지는 예외 종류와 Transaction 전체 Rollback 여부, 현재 `GlobalExceptionHandler` 응답 (AS-IS는 `COM-500` 예상) |
| 현재 근거 | 정적: `@Enumerated(STRING)`, ML-H2 U2' 설계, ERROR-CODES `COM-004` 후보 |
| 실험 코드 | `s4exp.db` — Schema 조회(`information_schema`), Test 전용 Entity(U2'), 두 Transaction으로 Deadlock·Lock 대기 유도 |
| 명령 초안 | `./gradlew test --no-daemon --tests 'com.merge.backend.s4exp.db.*'` |
| 성공 기준 | Column 정의 원문, 제약 위반 예외, 예외 Class 이름과 Rollback 후 Row 수 |
| 한계 | 운영 Schema는 `ddl-auto: update`로 만들어졌고 형식이 `UNKNOWN`이다. 이 결과를 운영에 일반화하지 않는다 (배포 담당 확인 필요) |
| 필요 승인 | 실행 |

### E6. API-SB-02 조회 가능성

| 항목 | 내용 |
|---|---|
| 가설 | 효력 종료 시각이 다른 CLOSED와 계산된 만료(OPEN·ACCEPTED ∧ `startAt ≤ now`)를 MySQL에서 한 Query로 ① 거르고 ② `CASE` 식으로 정렬하고 ③ 같은 시각은 ID로 안정 정렬하고 ④ 기간 필터·페이지·전체 수를 정확히 낼 수 있다 |
| Fixture | CLOSED (사유 3종 이상), OPEN·ACCEPTED의 `startAt < now`·`== now`·`> now`, APPROVED, `closedAt`이 null인 CLOSED (예전 데이터 가정), 다른 근무지, 같은 시각 여러 건 |
| 실험 코드 | `s4exp.query` — Test 전용 Repository Query (Production Repository에 추가하지 않음) |
| 명령 초안 | `./gradlew test --no-daemon --tests 'com.merge.backend.s4exp.query.*'` + 같은 SQL의 `EXPLAIN` 출력 기록 |
| 성공 기준 | 기대 목록·순서·전체 수와 일치. `startAt == now`는 만료에 포함. APPROVED·진행 중·다른 근무지 0건. null `closedAt` 처리 결과 기록. 실행 계획(접근 방식·Index 사용) 기록 |
| 결정에 쓰이는 곳 | API-SB-02 구현 계약 (페이지 방식, `size` 상한, 필요한 Index) |
| 필요 승인 | 실행 |

### E5. ML-5 Lock 순서·동시성 (H6)

| 항목 | 내용 |
|---|---|
| 가설 | ML-5 R1~R4 반례가 AS-IS에서 재현되는지. 알림 읽음은 권한 실패에도 Row Lock을 잡는지 (CHG-07). 대타 승인의 권한 확인 전 Lock 점유 (M3-T2) |
| 현재 근거 | 정적 분석 (ML-5, USER-PATH-REVIEW §0-1). 2차 저장소의 T-02B Harness Self Test 18/18 (EV-006, `LOCAL_ONLY`, 미Commit) |
| Harness 선택지 (DL-009 결정 대상) | **(a) PR #5 `ForcedOrderTest` 방식** (`CountDownLatch`로 순서 강제, 저장소에 이미 있는 Pattern) / (b) 2차 T-02B Harness 이식 / (c) MySQL `performance_schema.data_locks` 조회로 Lock 점유 관찰 (a·b와 함께 사용). 제안: **(a) + (c)로 먼저 시도** — 새 공유 Harness 없이 시작할 수 있다 |
| 실험 코드 | `s4exp.concurrency` — 퇴사 기능이 없으므로 R1·R4는 Test 전용 `leftAt` 갱신으로 흉내 (ML-5 원문 방식) |
| 명령 초안 | `./gradlew test --no-daemon --tests 'com.merge.backend.s4exp.concurrency.*'` (반복 횟수는 실험별로 정함) |
| 판정 | `REPRODUCED` / `NOT_REPRODUCED` / `ENVIRONMENT_UNSUITABLE` / `FIXTURE_ERROR`. `NOT_REPRODUCED`는 "결함 없음"이 아니다 |
| 필요 결정·승인 | DL-009 (Harness 선택), DL-010 (MySQL 동시성 검증 지위), 실행 승인 |

## 4. 순서와 중단 조건

```text
E0 환경 → E1 Java 기준선 → E2·E3 (같은 실험 Worktree) → E4 → E6 → E5 (DL-009 후)
```

- E1을 Kotlin 관련 실험보다 먼저 한다. 비교할 기준이 없으면 E2·E3의 "같다/다르다"를 판단할 수 없다.
- E6은 독립적이고 작아서 E5보다 먼저 한다. E5는 Harness 결정(DL-009)이 필요해 마지막이다.
- **중단 조건:** E0 실패(환경), 실험 중 Production 코드 변경이 필요해지는 경우, 운영 자원 접근이 필요해지는 경우, 민감 정보가 저장소 Commit 대상에 섞이는 경우 → 멈추고 보고.
- E1의 Test 실패는 중단 조건이 아니다. "알려진 실패"로 기록하고 다음 실험 승인 여부를 묻는다.

## 5. 남은 위험

| 위험 | 영향 | 대응 |
|---|---|---|
| 모든 결과가 `LOCAL_ONLY` | 팀원이 원본을 볼 수 없음 | 요약·SHA-256을 TEST-EVIDENCE에, 필요하면 가린 공유본 |
| 로컬 macOS ↔ CI ubuntu 차이 | 시간·Docker 동작 차이 | 실행 환경을 EV에 기록, 차이가 의심되면 CI 실행은 별도 승인 |
| MySQL 9.7.2 ↔ 운영 버전 `UNKNOWN` | 결과 일반화 불가 | 배포 담당 확인 (D-NOW-01) |
| test `create` ↔ 운영 `update` | Schema 차이 | E4 결과를 운영 Schema로 단정하지 않음 |
| 동시성 Test의 우연한 통과 | 잘못된 안심 | 반복 실행, 순서 강제, TEST-STRATEGY §8 실패 확인 (별도 승인) |
| 실험 결과로 정책을 정하려는 유혹 | 원칙 위반 | §0-1 |

### 5-1. 환경·보안 관찰과 개선 제안 (실험과 별개, 2026-10-25 정적 확인)

설정 파일은 키와 값 형태(환경 변수 / 기본값 유무 / 직접 값)만 확인했고 **비밀 값은 출력·기록하지 않았다.** 운영(Railway) 설정은 접속 금지라 확인하지 않았다 — 배포 담당 확인 대상.

| 사실 (정적) | 위험 | 상태 |
|---|---|---|
| `application-prod.yml`의 DB 접속(`MYSQLURL`·`MYSQLUSER`·`MYSQLPASSWORD`)은 **환경 변수, 기본값 없음** | 없음 (값이 없으면 기동 실패) | 양호 |
| `application-init-data.yml`의 초기 비밀번호는 `APP_INIT_DEFAULT_PASSWORD` **환경 변수, 기본값 없음**. `InitData`는 `@Profile("init-data")`일 때만 동작 | 운영에서 `init-data` Profile을 켜지 않으면 동작하지 않음 | 양호 (S4-P 초안의 "기본 비밀번호가 있다"는 **잘못된 기록이라 정정**) |
| **JWT 서명 키** `custom.jwt.secret-key`가 공통 `application.yml`에 `${JWT_SECRET_KEY:<기본값>}` — **기본값이 저장소에 있다** | 운영에서 `JWT_SECRET_KEY`가 빠지면 **오류 없이 저장소의 기본 키로 서명**한다 (fail-open). 저장소 이력에 남은 키는 공개된 것으로 봐야 한다 | **우선 확인** |
| `custom.cookie.secure` 기본 `false`, `same-site` 기본 빈 값, `cors.allowed-origins` 기본 `http://localhost:3000` (공통 `application.yml`) | 운영에서 환경 변수가 빠지면 Secure 아닌 Cookie·잘못된 CORS로 **오류 없이** 동작 | 확인 필요 |
| 운영 이미지 `backend/Dockerfile`은 `java -jar app.jar`만 실행하고 **Profile을 지정하지 않는다**. `application.yml`의 `spring.profiles.default: dev` | Railway에 `SPRING_PROFILES_ACTIVE=prod`가 없으면 **dev Profile로 기동** — 컨테이너 안 H2 파일 DB, H2 Console 켜짐, SQL TRACE 로그 | **우선 확인** |
| `application-test.yml`이 `src/main/resources`에 있음 → Boot Jar에 포함될 수 있음 | test Profile이 켜지지 않으면 쓰이지 않는다. 켜져도 `jdbc:tc:` 드라이버가 운영 classpath에 없어 기동 실패할 가능성이 높다 (추정). 위치가 의도와 다르다 | 정리 대상 (낮음) |
| JUnit XML·로그 | `system-out`에 SQL·설정이 섞일 수 있음 | §2 민감 정보 검사 |

**개선 제안 (승인된 Task에서만 적용 — `application*.yml`·Dockerfile 변경, AI-RULES):**
1. **운영에서 필수 값이 없으면 기동 실패하게 (fail-fast).** `application-prod.yml`에 운영 필수 값을 기본값 없이 다시 선언한다. 공통 `application.yml`의 기본값은 dev·test 편의용으로 남는다.
   ```yaml
   custom:
     jwt:
       secret-key: ${JWT_SECRET_KEY}          # 기본값 없음: 없으면 기동 실패
     cookie:
       secure: ${COOKIE_SECURE}               # 운영은 true
       same-site: ${COOKIE_SAME_SITE}         # Frontend·Backend 도메인이 다르면 None
     cors:
       allowed-origins: ${CORS_ALLOWED_ORIGINS}
   ```
2. **운영 이미지의 기본 Profile을 prod로.** `backend/Dockerfile`의 실행 단계에 `ENV SPRING_PROFILES_ACTIVE=prod`. Railway 환경 변수가 있으면 그 값이 우선하고, 빠져도 dev로 뜨지 않는다. 로컬 `bootRun`(dev 기본값)과 Docker CI(이미지 빌드만, 실행 안 함)에는 영향 없음.
3. (선택) **공통 파일에서 JWT 기본 키 제거.** `application.yml`은 `${JWT_SECRET_KEY}`만 두고, 개발용 키는 `application-dev.yml`·`application-test.yml`에 "로컬 전용" 표시와 함께 둔다. 운영 키와 반드시 다른 값.
4. (선택, 코드 변경) prod Profile 기동 검사: JWT 키 길이(HS256은 32 byte 이상), 알려진 개발용 키와 같지 않음, `COOKIE_SECURE=true`. Improvement Task로 분리.
5. `application-test.yml`을 `src/test/resources`로 이동. 이동 전후 E1 결과 비교로 Test 동작이 같은지 확인.

**배포 담당 확인 목록 (값을 공유하지 않고 "있음/없음·같음/다름"만 답한다):**
- Railway에 `SPRING_PROFILES_ACTIVE=prod`가 있는가
- `JWT_SECRET_KEY`가 있고, 저장소 `application.yml`의 기본값과 **다른가**. 같거나 과거에 비어 있던 적이 있으면 **교체** (교체하면 기존 로그인·Refresh Token이 무효가 되어 사용자가 다시 로그인해야 함)
- `COOKIE_SECURE=true`, `COOKIE_SAME_SITE`(다른 도메인이면 `None`), `CORS_ALLOWED_ORIGINS`(운영 Frontend 출처)가 있는가
- `init-data` Profile이 운영에서 꺼져 있는가, `APP_INIT_DEFAULT_PASSWORD`가 운영에 남아 있지 않은가

위 1·2는 Kotlin Migration·Improvement와 무관한 **배포 안전 항목**이라 먼저 처리할 가치가 있다 (G03-D-28 배포 전 점검과 같은 범주).

## 6. 실행 전에 필요한 결정

| 항목 | 누가 |
|---|---|
| E0 승인 (기준선 Worktree 생성 + 버전 확인 명령) | 사용자 |
| 결과 보관 위치 확정 (§2 제안 경로) | 사용자 |
| E1을 Migration 기준선(`BL-TBD`)으로 선언할지 | 팀 |
| E5 Harness 선택 (DL-009), MySQL 동시성 검증 지위 (DL-010) | 팀 |
| 실험 후 Worktree·Branch 정리 시점 | 사용자 (실험별) |

## 7. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-25 | 초안 (S4-P). 원칙 6개, 공통 환경, Worktree·결과 보관·민감 정보 검사·원상복구, 실험 E0~E6, 순서·중단 조건, 남은 위험, 실행 전 결정. 실험 코드는 전용 Worktree에만 두고 버림 (사용자 결정). 실행 0 |
| 2026-10-25 | 보완: 실험 코드 처리 기준(일회성·회귀 후보·Query·구현 반영·임시 파일), 실험 코드 로컬 Commit은 실험별 승인, E0 범위(환경 확인만, 운영 연결 위험 이름 확인, Git 제외·원상복구), E1 판정 `PASS`·`KNOWN_FAILURE`·`FLAKY`·`ENVIRONMENT_BLOCKED`와 정적 집계 분리, §5-1 환경·보안 관찰과 개선 제안·배포 담당 확인 목록. 초기 데이터 "기본 비밀번호" 기록 정정 (기본값 없음) |
