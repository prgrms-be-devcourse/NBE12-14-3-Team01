# Test 실행 Evidence

> Append-only. 실제로 실행한 결과만 기록한다. 실행하지 않은 Test는 쓰지 않는다.
> 판정 칸에는 사실만 쓰고, 원인 추정은 "추정"으로 표시한다.
> 로컬 Log 경로는 실행자 PC 기준이며 팀원이 볼 수 없다. 공유가 필요하면 PR·CI 링크를 남긴다.

> **저장소 구분 (D0-D, 2026-10-23):** EV-001~EV-007은 **2차 Java 저장소**(`prgrms-be-devcourse/NBE12-14-2-Team01`)의 SHA와 작성자 PC의 과거 로컬 검증 결과다. 3차 저장소로 이관하면서 행 내용은 바꾸지 않았다. 로컬 Log 경로는 작성자 PC 기준이며 3차 저장소에 없다.
> EV-008부터는 **3차 저장소**(`prgrms-be-devcourse/NBE12-14-3-Team01`)에서 실제로 실행된 결과만 기록한다.
> CI Suite `success`는 "그 SHA에서 `./gradlew check`가 실패 없이 끝났다"는 뜻이며 개별 Test의 `PASSED` 근거가 아니다 (`docs/STATUS-AXES.md` ⑤). 현재 `Backend CI`는 Test Report를 Artifact로 보관하지 않는다.

| ID | 일자 | SHA / Branch | 환경 | 명령 | 결과 | Log / 링크 | 판정 | 상태 |
|---|---|---|---|---|---|---|---|---|
| EV-001 | 2026-10-22 | `9858612` (Original, detached Worktree) | 로컬 macOS, GraalVM JDK 25, H2 2.4.240 (Session RC) | `./gradlew test` | 266 실행 / 263 PASS / **3 FAIL** — 모두 `UnavailableTimeConcurrencyTest` (`NOT_FUTURE_TIME` 1, XOR 단언 실패 2) | 실행자 로컬 `NBE12-wt/original-9858612.gradle.log` | Test Clock / Fixture 실패로 분류 (추정: 현재 시각 의존). 같은 SHA의 CI Run `36805609127`(2026-10-01)은 success | 기록 |
| EV-002 | 2026-10-22 | `243f77c` (develop, detached Worktree) | 로컬 macOS, GraalVM JDK 25, Testcontainers MySQL 9.7.2 (Session RC, Hibernate 로그 "Isolation level: READ_COMMITTED") | `./gradlew test` | **266 / 266 PASS**. 단 `UnavailableTimeRepositoryTest`(`@DataJpaTest`)는 H2에서 실행 | 실행자 로컬 `NBE12-wt/develop-243f77c.gradle.log` | D0-B 조사 당시 Java Snapshot 결과. **최종 Java Baseline(`BL-TBD`) 아님** | 기록 |
| EV-003 | 2026-10-20 | `243f77c` (develop push) | GitHub Actions ubuntu-latest, Temurin 25 | `Backend CI` (`./gradlew check`), `Docker Build CI Test` | success / success | Run `39382112989`, `39382112986` | CI에서 Testcontainers 동작 | 기록 |
| EV-004 | 2026-10-22 | PR #76 `test/auth-tests` `14c1f84` | GitHub Actions | PR CI | Backend Check pass, docker-build-test pass | Run `39703627596`, `39703627572` | 미병합 PR의 CI 결과. develop 기준선 아님 | 기록 (미병합) |
| EV-005 | 2026-10-22 | 로컬 `test/mysql-environment-evidence` (base `243f77c`, **미Commit**) | 로컬, Testcontainers MySQL 9.7.2, InnoDB, global RR / Session RC (test-only Property로 RR Context 별도) | T-02A Evidence Test 4 Class | 5 / 5 PASS. Schema Unique 5개·FK 확인, `schedule.status` = MySQL `enum('DRAFT','PUBLISHED')`, time_zone UTC, strict sql_mode | 실행자 로컬 `NBE12-wt/mysql-env-evidence.gradle.log`, `t02a-schema-recheck.log` | test Profile Schema의 사실. 운영 Schema와 같다는 근거 아님 | **LOCAL_ONLY** |
| EV-006 | 2026-10-22 | 동일 (미Commit) | 동일, RC Context + RR Context | T-02B Harness Self Test | 18 / 18 PASS (RC 9 + RR 9), `checkstyleTest` PASS | 실행자 로컬 `NBE12-wt/t02b-harness.gradle.log`, `t02b-checkstyle.log` | **Harness 자체 검증만.** SUB-DC-01 / SUB-DC-02 / SCH-DC-01 / SCH-DC-02 등 비즈니스 동시성 결함은 재현·해결되지 않음 | **LOCAL_ONLY** |
| EV-007 | 2026-10-22 | `243f77c` (detached Worktree) | 로컬, Gradle 9.7.1 | `./gradlew dependencies --configuration runtimeClasspath` / `testRuntimeClasspath` / `annotationProcessor` | 해석 성공. 주요 Version은 `TECH-STACK-DECISIONS.md` §1 | 실행자 로컬 scratch | 의존성 해석만 (Test 아님) | 기록 |
| EV-008 | 2026-10-22 | **3차** `cdaaa13` (`develop` push, PR #1 병합) | GitHub Actions ubuntu-latest, Temurin 25, Gradle 9.3.0 (Wrapper), Testcontainers MySQL 9.7.2 (test Profile 설정 근거) | `Backend CI` (`./gradlew check --no-daemon`), `Docker Build CI Test` | success / success (**Suite 수준**). 개별 Test 결과·실행 수 미보존 | Run `39748913867`, `39748913987` | Kotlin 환경 적용 후 `check` Suite 통과. Backend CI Log(2026-10-23 확인)에서 실행된 Gradle Task: `compileKotlin`, `compileJava`, `compileTestKotlin`, `compileTestJava`, `checkstyleMain`, `checkstyleTest`, `test`, `check` (`BUILD SUCCESSFUL in 2m 39s`). **증명하지 않는 것:** 개별 Test 결과 (⑤ `UNKNOWN`, Log에 Test 수 없음, Test Report Artifact 0건), Testcontainers MySQL 실제 기동 (Test 출력 미표시), Kotlin Domain Migration (전환 0건), 계약·사용자 Flow 검증. 경고 목록·의존성 차이는 미기록 | 기록 (Suite만) |

## 기록 양식

```text
| EV-0NN | YYYY-MM-DD | SHA / Branch | JDK, OS, DB(종류·버전), Session Isolation, Profile | 명령 | 실행 N / PASS / FAIL / SKIP, 실패 Test 이름 | Log 위치 또는 CI Run | 사실 기반 판정 | 기록 / LOCAL_ONLY / 미병합 |
```

동시성 Test 판정: `REPRODUCED` / `NOT_REPRODUCED` / `ENVIRONMENT_UNSUITABLE` / `FIXTURE_ERROR`. `NOT_REPRODUCED`는 "결함 없음"이 아니다.
