# AI 개발·변경 제한 규칙

> 상태: **초안 (팀 승인 전)**. 승인 후 이 파일은 팀 승인으로만 바꾼다. AI는 읽기 전용.
> D0-D (2026-10-23): 3차 저장소 이관 시 사용자 지시에 따라 사실·경로·상태 규칙 참조만 정정했다. 새 업무 정책·권한 규칙은 추가하지 않았다.
> 적용 범위: `backend/` 전체. `frontend/`는 범위 밖 (Frontend 담당 판단).
> 적용 대상: Claude Code를 포함한 모든 AI 도구와, AI 결과를 반영하는 팀원.

## 1. 작업 시작 전

1. `docs/README.md` §1 순서대로 읽는다. 외부 분석 보고서(G-0x, A-0x)는 Matrix·Task Card에 ID로 지정된 부분만 읽는다. G-03은 `docs/references/`의 참고 공유본으로 읽을 수 있고, 그 밖의 원문은 저장소에 없다 (`docs/references/README.md`).
2. 상태값은 `docs/STATUS-AXES.md`의 5개 축으로 읽는다. 축을 섞지 않는다.
3. 아래 Task Card를 채운다. 채울 수 없는 칸이 있으면 작업을 시작하지 않고 질문한다.

```text
Task ID        : (Board ID, 예: V3-02)
기준 SHA       : (작업 Branch의 base. 3차 Repository develop 기준 Commit, 현재 cdaaa13. Java Baseline 후보 8da2bec, 최종 선언은 BL-TBD)
변경 대상      : (파일·Class 목록)
금지 대상      : (예: Lock Query, application*.yml, build.gradle.kts, 기존 Test 기대값)
보존 계약 ID   : (CONTRACT-BASELINE 행 / G-03 C-ID)
관련 Decision  : (DECISION-LOG ID, PENDING이면 건드리지 않음)
실행할 Test    : (Matrix의 필수 Regression Test + 명령)
완료 Evidence  : (CI Run / 로컬 Log 위치)
```

## 2. 변경 제한

- 다음은 **승인된 Decision 없이 바꾸지 않는다:** API 경로·Method·요청/응답 Field, `ApiResponse` 형식, HTTP Status, ErrorCode의 `code`/`message`/Status, Enum 이름·값, 상태 전이, DB Schema(Column, Unique, FK, ENUM), Transaction 경계, Rollback 규칙, Lock 종류·순서, Isolation Level, Event 발행 시점(`AFTER_COMMIT` 등).
- Migration PR에서는 새 기능, API 변경, 정책 변경, Lock/Isolation 변경, Outbox, 성능 최적화, 새 상태 전이를 함께 하지 않는다.
- Production 코드는 승인된 Task의 "변경 대상" 안에서만 바꾼다.
- 기존 Test의 기대값·Assertion을 바꾸지 않는다. 실패하면 원인을 분류해 보고한다 (Fixture / 환경 / 결함 후보).
- Test를 `@Disabled` 처리하거나 삭제해서 통과시키지 않는다.

## 3. 불일치·불명확할 때

- 예상 동작이 분명하지 않으면 질문하거나 HOLD한다. 추측으로 구현하지 않는다.
- 코드·명세·Test가 다르면 **어느 쪽이 맞는지 고르지 않는다.** `DECISION-LOG.md`에 `DISCREPANCY / PENDING` 행을 추가하고, Migration은 현재 develop 동작을 보존한다.
- AI 분석 보고서의 제안을 계약으로 취급하지 않는다. `CONTRACT-BASELINE`의 `APPROVED` 행과 `DECISION-LOG`의 `APPROVED` 행만 계약이다. G-03 C-ID(① `REVIEW_ACCEPTED`)도 승인된 계약이 아니다.
- ② `PROPOSED` 항목(예: Membership Lifecycle, ML-SUB-STATE-01, ML-CONCURRENCY-01-R)은 구현 지시가 아니다. `DECISION-LOG`에 `APPROVED` 행이 생기기 전에는 Production 코드나 Test 코드로 옮기지 않는다. 해당 Test는 ④ `SPECIFIED`를 넘지 않는다 (`docs/STATUS-AXES.md`).

예 (2차 `243f77c` 정적 확인. 3차 `cdaaa13`까지 Production Java 변경 0): `SecurityConfig`는 `/api/v1/workplaces/**`를 `permitAll`로 두는데 `server.servlet.context-path`가 `/api/v1`이다. 이 Matcher가 실제로 적용되는지는 Runtime 미확인 (G03-OQ-01, AUTH-EV-01). → Kotlin 전환 시 Matcher를 "정리"하지 않고 그대로 옮기고, 결과는 Decision으로 처리한다.

## 4. 의존성·환경 변경 금지

- Gradle Plugin·Library 추가·제거, Version 변경, Gradle Wrapper 변경, Java Toolchain 변경, `application*.yml` 변경은 **승인된 환경 Task(M0 등)에서만** 한다.
- Spring Boot / Kotlin / Gradle Version을 "최신"이라는 이유로 올리지 않는다. 근거는 `TECH-STACK-DECISIONS.md`에 기록하고 공통 설정 Owner가 승인한다.
- Spring Boot BOM이 관리하는 Library에는 Version을 적지 않는다.
- 의존성 변경 PR에는 변경 전후 `./gradlew dependencies --configuration runtimeClasspath` 차이를 첨부한다.
- 현재 적용 상태 (3차 `develop` `cdaaa13`, PR #1): Gradle 9.3.0, Kotlin 2.3.21 Plugin 4종 등. 적용 사실과 이유는 `TECH-STACK-DECISIONS.md` §1-A·§1-B, 승인 기록은 `DECISION-LOG` DL-003·004·014 (`PENDING`). 이 적용 사실을 근거로 다른 Version 변경을 하지 않는다.

## 5. Kotlin 구현 규칙

"이유" 칸의 수치는 2차 `243f77c` 기준이다 (3차 `cdaaa13`까지 Production Java 변경 0).

| 주제 | 규칙 | 이유 (현재 코드 근거) |
|---|---|---|
| Entity | `data class` 금지. `class` + `kotlin("plugin.jpa")` 의존 (no-arg + all-open). 상태 Field는 `var` + `protected set` 또는 `private set` | Entity 10개가 Lombok `@NoArgsConstructor` + 상태 변경 메서드(`Schedule.publish`) 사용. Lazy Proxy·Dirty Checking 보존 |
| Entity 동등성 | `equals`/`hashCode`를 새로 추가하지 않는다 | 현재 Entity에 `equals`/`hashCode` 재정의 0건 (Identity 동등성) |
| ID | `BaseEntity`의 `Long id`는 영속 전 `null`. Kotlin에서는 `Long?`로 노출하고 `!!` 대신 명시적 예외 또는 `requireNotNull` | `BaseEntity.id` IDENTITY 생성 |
| Request DTO | 필수 Field도 우선 **nullable + `@field:NotNull`/`@field:NotBlank`** 로 옮긴다 | non-null Kotlin Field가 JSON에서 빠지면 Jackson이 Bean Validation 전에 실패할 수 있다. 그러면 `GlobalExceptionHandler`에서 `MethodArgumentNotValidException` → `COM-001` (Field 오류 `data` 포함) 대신 `HttpMessageNotReadableException` → `COM-002` (`data` 없음)로 응답이 바뀐다 (INFERENCE, M0-6에서 실제 비교) |
| Validation Annotation | `@field:` 사용 위치를 명시하거나 `-Xannotation-default-target=param-property` 적용 후 검증 | Spring Boot Kotlin 문서 권장 Flag. 3차 `cdaaa13`에 Flag 적용됨 (PR #1). 실제 Validation 동작 비교(M0-6)는 기록 없음 |
| Response DTO | Java record → Kotlin `data class` 허용. Field 이름·순서·null 출력 여부를 보존 | 52개 record DTO. JSON 형식은 계약 |
| `!!` | 금지. 꼭 필요하면 사유 주석 | Nullability 개선이 공식 MUST |
| Spring Bean | 생성자 주입 유지. `@Transactional`은 public 메서드에만 | `@RequiredArgsConstructor` 41건, `@Transactional` 40건 |
| Lock / Query | `@Lock`, `@Query` 문자열·순서를 그대로 옮긴다 | `@Lock` 7건, `@Query` 22건 |
| Event | `@TransactionalEventListener` phase·Transaction 설정을 그대로 옮긴다 | 7건 |
| 확장 함수 | 동작을 바꾸지 않는 범위에서만. 전후 비교 사례로 기록 | 공식 MUST (전후 비교) |
| Lombok | Kotlin 코드에 Lombok Annotation을 쓰지 않는다 (Kotlin 컴파일러가 무시) | Kotlin Lombok 문서. 3차 `cdaaa13`의 `kotlin("plugin.lombok")`(Alpha)은 Kotlin이 **Java** Lombok 생성 멤버를 참조하기 위한 것이다 (DL-004) |

## 6. Test와 Evidence

- 관련 Test를 **실제로 실행**하고 결과를 보고한다. "Test가 있음"과 "통과함"을 구분한다.
- 결과 형식 (`TEST-EVIDENCE.md`):

```text
SHA / Branch :
환경         : JDK, OS, DB(H2|MySQL 버전), Session Isolation, Profile
명령         :
결과         : 실행 N / PASS / FAIL / SKIP, 실패 Test 이름
Log 위치     :
판정         : (사실만. 원인 추정은 "추정"으로 표시)
```

- 개별 Test를 ⑤ `PASSED`로 쓰려면 그 SHA의 개별 결과 근거(Test Report XML 등 + EV-ID)가 필요하다. CI Suite `success`만 있으면 개별 Test는 ⑤ `UNKNOWN`이다 (`docs/STATUS-AXES.md`). 현재 `Backend CI`는 Test Report를 Artifact로 보관하지 않는다.
- 동시성 Test는 `REPRODUCED` / `NOT_REPRODUCED` / `ENVIRONMENT_UNSUITABLE` / `FIXTURE_ERROR`로 판정한다. `NOT_REPRODUCED`를 "결함 없음"으로 쓰지 않는다.

## 7. Production·운영

- 운영 DB / Railway에 접속·쓰기·부하·동시성 실험을 하지 않는다.
- `prod` Profile로 Application을 실행하지 않는다.
- Secret·비밀번호·Token 값을 출력하거나 문서에 쓰지 않는다.

## 8. Git

- 작업은 별도 Branch에서 한다. `develop`/`main`에 직접 Commit하지 않는다.
- Commit / Push / Merge / PR 생성은 **각각** 사람의 명시적 승인 후에 한다. 한 번의 승인을 다음 작업으로 확장하지 않는다.
- 다른 사람의 Branch·Worktree·Stash를 바꾸지 않는다.
- `reset --hard`, `push --force`, `clean`은 하지 않는다.

## 9. 검증 수단 (규칙을 지켰는지 사람이 확인하는 방법)

| 수단 | 위치 |
|---|---|
| 변경 파일·보존 계약·Test 범위 지정 | Task Card (§1). PR Template 보강은 승인 대기 — 승인 전에는 PR 본문에 Task Card를 붙인다 |
| 기대값·정책·API·Enum·ErrorCode·Schema 변경 승인 | `DECISION-LOG.md` + PR 리뷰 |
| 별도 Branch, Diff 검토, 사람 리뷰 후 통합 | PR (리뷰어 1명 이상). 2차 Java Repository `develop`에는 Ruleset이 있음 (2026-10-22 확인, `DECISION-LOG` DL-012). 3차 Kotlin Repository `develop` Ruleset (2026-10-23 `gh api .../rules/branches/develop` 확인): `pull_request` (승인 1명), `deletion`, `non_fast_forward`. **필수 Status Check 없음** (DL-012 `PENDING`) |
| 관련 Test 실제 실행 | PR CI (`Backend CI`: `./gradlew check`, `Docker Build CI Test`) + `TEST-EVIDENCE.md` |
| 의존성 변경 확인 | PR에 `dependencies` 출력 차이 첨부 |
