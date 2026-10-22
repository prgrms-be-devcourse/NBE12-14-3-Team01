# 기술 스택과 Kotlin 의존성 결정

> 작성 기준: 2차 `origin/develop` `243f77c`, 2026-10-22 (D0-B). D0-D (2026-10-23): 3차 `develop` `cdaaa13`의 적용 사실을 §1-A·§1-B·§5-A에 **추가**했다. §1·§3·§4의 D0-B 조사 내용과 판정은 지우거나 바꾸지 않았다.
> 상태: **조사 결과 + 제안.** 표의 "판정"은 채택 결정이 아니다. 채택은 공통 설정 Owner 승인 후 `DECISION-LOG.md`에 기록한다.
> 근거 표기: `BUILD` = build 파일 / `RESOLVED` = `./gradlew dependencies` 실제 해석 결과 (EV-007) / `BOM` = `spring-boot-dependencies-4.1.1.pom` / `CI` = workflow 파일 / `LOCAL` = 이 PC에서만 확인 / `DOC` = 공식 문서 / `INFERENCE` = 추정.

## 1. D0-B 조사 시점 기술 스택 (2차 `243f77c`, Kotlin 없음)

3차 현재 값과 다른 행은 §1-A를 본다 (Gradle Wrapper, Gradle 내장 Kotlin, Kotlin Plugin).

| 항목 | 값 | 근거 |
|---|---|---|
| Gradle Wrapper | 9.7.1 | BUILD (`gradle-wrapper.properties`) |
| Build Script | Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`), 단일 Module `backend` | BUILD |
| Java Toolchain | `JavaLanguageVersion.of(25)` | BUILD |
| 로컬 JDK | Oracle GraalVM 25+37 (Gradle Daemon JVM) | LOCAL |
| CI JDK | Temurin 25 (`actions/setup-java`) | CI (`backend-ci.yml`, `docker-ci.yml`) |
| Docker | Build `eclipse-temurin:25-jdk-alpine`에서 `./gradlew clean build -x test`, Runtime `eclipse-temurin:25-jre-alpine` | BUILD (`backend/Dockerfile`) |
| Gradle 내장 Kotlin | 2.4.0 (`./gradlew --version`) — **Kotlin DSL 실행용이며 프로젝트 Kotlin 버전이 아님** | LOCAL |
| Spring Boot Plugin | 4.1.1 | BUILD |
| Dependency Management Plugin | 1.1.7 | BUILD |
| Spring Framework | 7.0.9 | RESOLVED |
| Spring Security | 7.1.1 | RESOLVED |
| Spring Data JPA | 4.1.1 | RESOLVED |
| Hibernate ORM | 7.4.5.Final | RESOLVED, BOM |
| Jakarta Persistence | 3.2.0 | RESOLVED |
| Bean Validation | Hibernate Validator 9.1.3.Final / Jakarta Validation 3.1.1 | RESOLVED |
| HikariCP | 7.0.2 | RESOLVED |
| JSON Mapper (Spring MVC) | **Jackson 3** `tools.jackson.core:jackson-databind` 3.1.5 (`spring-boot-starter-jackson`) | RESOLVED, BOM (`jackson-bom.version` 3.1.5) |
| Jackson 2 (전이) | `com.fasterxml.jackson.core:jackson-databind` 2.21.5 — springdoc의 `swagger-core-jakarta` 2.2.52, `jjwt-jackson`이 사용 | RESOLVED, BOM (`jackson-2-bom.version` 2.21.5) |
| 코드의 ObjectMapper | `tools.jackson.databind.ObjectMapper` (Jackson 3) — `SecurityConfig`, `CustomAuthenticationFilter` 2곳 | 코드 |
| JWT | JJWT 0.13.0 (`jjwt-api` / `jjwt-impl` / `jjwt-jackson`) | BUILD, RESOLVED |
| SpringDoc | `springdoc-openapi-starter-webmvc-ui` 3.1.0, swagger-ui 5.32.11 | BUILD, RESOLVED |
| REST Docs | 없음 | BUILD |
| Lombok | 1.18.46 (Boot 관리), `compileOnly` + `annotationProcessor` (main/test) | BUILD, RESOLVED |
| MapStruct / QueryDSL / kapt | 없음 | BUILD, 코드 검색 0건 |
| DB Driver | `mysql-connector-j` 9.7.0, H2 2.4.240 | RESOLVED |
| Testcontainers | 2.0.5 (`testcontainers-mysql`) | RESOLVED, BOM |
| Test DB | `jdbc:tc:mysql:9.7.2:///db_test?TC_DAEMON=true`, `ddl-auto: create` | BUILD (`application-test.yml`) |
| Test | JUnit Jupiter 6.0.3, Mockito 5.23.0, AssertJ 3.27.7 | RESOLVED |
| Isolation | 전역 `spring.datasource.hikari.transaction-isolation: TRANSACTION_READ_COMMITTED` (모든 Profile) | BUILD (`application.yml`) |
| Profile | `dev` (기본, H2 file, `ddl-auto: update`) / `test` (Testcontainers MySQL) / `prod` (MySQL, `ddl-auto: update`) / `init-data` | BUILD |
| Code Style | Checkstyle (`config/checkstyle/checkstyle.xml`), `check`에 포함 — **Java만 검사, `.kt`는 대상 아님** | BUILD |
| CI | `Backend CI`: `./gradlew check --no-daemon` / `Docker Build CI Test`: `./gradlew bootJar` + `docker build` | CI |
| Boot BOM의 Kotlin 관리값 | `kotlin.version` 2.3.21, `kotlin-coroutines.version` 1.10.2 | BOM |

## 1-A. 3차 `develop` `cdaaa13` 적용 사실 (PR #1)

근거: PR #1 `chore/kotlin-setup` (2026-10-22 병합), `git diff 8da2bec cdaaa13` (5개 파일). 아래는 **적용 사실**이며 채택 결정 기록이 아니다 (DL-003·004·014 `PENDING`).

| 항목 | 적용 값 (근거 `BUILD`) | D0-B 조사 판정 (§3, 변경하지 않음) | 차이 | 관련 |
|---|---|---|---|---|
| Gradle Wrapper | **9.3.0** (`gradle-wrapper.properties`, 9.7.1에서 변경) | TS-17 "현행 유지" (9.7.1) | 다름. 이유·근거는 §1-B | DL-003 |
| `kotlin("jvm")` | 2.3.21 | TS-01 제안 2.3.21 | 같음 | DL-003 |
| `kotlin("plugin.spring")` | 2.3.21 | TS-02 `REQUIRED` | 같음 | — |
| `kotlin("plugin.jpa")` | 2.3.21 | TS-03 `REQUIRED` | 같음 | — |
| `allOpen { annotation(Entity / MappedSuperclass / Embeddable) }` | 명시 설정 | TS-03: 2.3.20+는 JPA preset all-open 자동 적용으로 조사 (명시 설정 불필요) | 명시 설정 추가 (자동 적용과 중복인지는 미확인) | DL-014 |
| `kotlin("plugin.lombok")` | 2.3.21 (Alpha) | TS-08 `CONDITIONAL` | 적용됨 | DL-004 |
| `org.jetbrains.kotlin:kotlin-reflect` | `implementation`, Version 생략 | TS-04 `REQUIRED` | 같음 | — |
| `tools.jackson.module:jackson-module-kotlin` | `implementation`, Version 생략 (Jackson 3 좌표) | TS-06 `REQUIRED` | 같음 | — |
| `org.jetbrains.kotlin:kotlin-test-junit5` | `testImplementation` | TS-12 `NOT_NEEDED (M0)` | 추가됨. `KotlinEnvironmentTest`가 `kotlin.test.assertEquals` 사용 | DL-014 |
| `-Xannotation-default-target=param-property` | `freeCompilerArgs` | TS-10 `REQUIRED (Owner 확인)` | 같음 | — |
| `-Xjsr305=strict` | `freeCompilerArgs` | TS-11 `NOT_NEEDED` (Kotlin 2.1+ 기본 strict로 조사) | 추가됨 | DL-014 |
| `backend/src/main/kotlin/KotlinEnvironmentCheck.kt` | `object KotlinEnvironmentCheck` (`package com.merge.backend`, 파일은 `src/main/kotlin/` 바로 아래) | §5 원칙 "Production 코드(`src/main/java`) 변경 없음, M0 변경은 `build.gradle.kts`와 `src/test/kotlin`만" | main Source Set에 Kotlin 파일 1개. bootJar 포함 여부 미확인 | DL-014, M0-FOLLOWUP-01 |
| `backend/src/test/kotlin/com/merge/backend/KotlinEnvironmentTest.kt` | `KotlinEnvironmentCheck.message()` 값 확인 1건 | M0-3 계획 (Lombok 멤버 참조 혼합 컴파일)과 범위 다름 | Kotlin 컴파일·실행 확인만 | M0-FOLLOWUP-03 |
| `.idea/codeStyles/Project.xml` | Kotlin `KOTLIN_OFFICIAL` 등 IDE 코드 스타일 | TS-16 (ktlint/detekt)과 별개. Build·CI 검사 아님 | IDE 설정만 | IMP-07 |
| Java Toolchain / Spring Boot / Dependency Management | 25 / 4.1.1 / 1.1.7 | 현행 유지 | 같음 | — |
| 의존성 해석 결과 (`dependencies`) | **3차에서 재실행 기록 없음** | EV-007 (`243f77c`, Gradle 9.7.1) | Library Version이 §1과 같은지 미확인 | M0-FOLLOWUP-02 |
| Gradle 내장 Kotlin (Kotlin DSL 실행용) | 9.3.0 기준 값 미확인 | 9.7.1에서 2.4.0 (LOCAL) | 미확인 | — |

## 1-B. Gradle 9.3.0 변경 — 사실 / 이유 / 근거 / 승인 상태

| 구분 | 내용 | 상태 |
|---|---|---|
| 적용 사실 | Gradle Wrapper 9.7.1 → 9.3.0 (Commit `7505d51` "chore: Gradle 9.3.0 버전 적용", PR #1에 포함, `develop` `cdaaa13`) | ① `VERIFIED_AS_IS` (`gradle-wrapper.properties`) |
| 변경 이유 | Kotlin Migration 환경 구성 시 Kotlin과 Gradle 버전의 호환성에 문제가 없도록 조정해 달라는 요청이 있었고, 그 과정에서 Kotlin 2.3.21의 공식 완전 지원 범위에 Gradle을 맞췄다. **임의 하향이 아니라 공식 호환 범위에 맞춘 조정**이다 | 사용자 확인 (2026-10-23). 요청 원문·회의 기록 링크는 미기록 |
| 공식 근거 | Kotlin Gradle 호환표: KGP **2.3.20–2.3.21 → Gradle 7.6.3–9.3.0** (AGP 8.2.2–9.0.0). 참고로 2.4.20–2.4.21 → 7.6.3–9.7.0. 문서는 "더 새 Gradle도 쓸 수 있으나 Deprecation 경고가 나거나 일부 새 기능이 동작하지 않을 수 있다"고 적는다. 출처: https://kotlinlang.org/docs/gradle-configure-project.html (2026-10-23 확인) | `DOC`. D0-B §3 TS-01·§4 #8의 조사 내용과 일치 |
| 대안 (D0-B §6-1, 기록 유지) | Kotlin 2.4.20/2.4.21로 올리고 Gradle 9.7.x 근처 유지. PR #1은 Kotlin 2.3.21 (Boot 4.1.1 BOM 관리값) + Gradle 9.3.0 쪽을 적용 | 선택 결과의 승인 기록 없음 |
| Test 근거 | `cdaaa13` push CI `Backend CI` (`./gradlew check --no-daemon`) success, `Docker Build CI Test` success (EV-008). **Suite 수준만**. 개별 Test 결과, Test 수, `--warning-mode all` 경고 목록, `dependencies` 전후 차이는 기록 없음 | ⑤ 개별 `UNKNOWN` |
| 승인 상태 | `DECISION-LOG` DL-003 `PENDING (PR #1로 적용됨, 승인 기록 대기)`. PR 리뷰 Approve는 Decision 승인 기록이 아님 | ② `PENDING` |

이 문서 작업(D0-D)에서는 Gradle·Kotlin Version을 바꾸지 않는다.

## 2. 현재 코드의 Kotlin 전환 관련 특성 (develop `243f77c`, main)

| 항목 | 수 | Kotlin 전환에서의 의미 |
|---|---|---|
| Java 파일 | main 130 / test 31 | 266 Test (EV-002, D0-B Snapshot `243f77c`. 최종 Baseline 아님) |
| Lombok 사용 파일 | 56 | Kotlin 코드가 Lombok 생성 멤버를 호출하면 컴파일 실패 (§3 TS-08) |
| Lombok Annotation | `@RequiredArgsConstructor` 41, `@Getter` 23, `@NoArgsConstructor` 11, `@Slf4j` 9, `@Setter` 1, `@Builder` 1, `@AllArgsConstructor` 1 | 모두 Kotlin Lombok Plugin 지원 목록 안 (`@Slf4j`는 외부 호출 대상 아님) |
| `@Entity` | 10 (+ `@MappedSuperclass` `BaseEntity`) | no-arg / all-open 필요 |
| `@ManyToOne` | 12 (LAZY 포함) | all-open 없으면 Lazy Proxy 불가 |
| Java record | 52 (주로 DTO) | Kotlin에서 그대로 참조 가능 |
| `@Transactional` | 40 | `kotlin("plugin.spring")` 필요 |
| `@TransactionalEventListener` | 7 | Proxy·phase 보존 |
| `@Lock` / `@Query` | 7 / 22 | 그대로 이식 |
| `@Configuration` / `@Value`(Spring) | 4 / 8 | CGLIB Proxy, 생성자 주입 |
| `equals`/`hashCode` 재정의 | 0 | Entity 동등성 = Identity |

## 3. Kotlin 전환 의존성 후보

| ID | Plugin / Library | Version / 관리 주체 | 왜 필요한가 | 사용 코드 | 현재로 충분? | 추가 필요? | Java/Kotlin 혼합 위험 | 호환성 근거 | 검증 Test | 판정 |
|---|---|---|---|---|---|---|---|---|---|---|
| TS-01 | `kotlin("jvm")` | **제안 2.3.21** (Boot 4.1.1 BOM 관리값과 같음). 대안 2.4.20/2.4.21. 공통 설정 Owner 결정 | Kotlin 컴파일 | 전체 | 아니오 | 예 | 2.3.20~2.3.21의 공식 완전 지원 Gradle은 9.3.0까지, 현재 9.7.1 → Deprecation 경고·일부 기능 문제 가능. 2.4.20~2.4.21은 9.7.0까지 | DOC (Kotlin Gradle 호환표), BOM | M0-2 Build Log 경고 확인, M0-7 | REQUIRED (Version은 UNKNOWN → M0-2) |
| TS-02 | `kotlin("plugin.spring")` | TS-01과 같은 Version | `@Component`/`@Transactional`/`@Async`/`@SpringBootTest` 계열 Class·메서드 open → Proxy | Service 40개 `@Transactional`, Listener, Config | 아니오 | 예 | 없으면 final Class로 Proxy 실패 또는 Transaction 미적용 | DOC (Spring Boot Kotlin 지원) | M0-4 | REQUIRED |
| TS-03 | `kotlin("plugin.jpa")` | TS-01과 같은 Version | no-arg 생성자 + **2.3.20부터 all-open JPA preset 자동 적용** (`@Entity`/`@Embeddable`/`@MappedSuperclass`) | Entity 10 + BaseEntity | 아니오 | 예 | 2.3.20 미만이면 all-open을 직접 설정해야 함. Kotlin Entity가 Java `BaseEntity`를 상속하는 혼합 구조 | DOC (What's new 2.3.20) | M0-5 | REQUIRED |
| TS-04 | `kotlin-reflect` | Version 생략 (Boot가 Kotlin Plugin Version에 맞춤) | Spring의 Kotlin 생성자·Nullability 해석, Jackson Kotlin Module | 전체 | 아니오 | 예 | 없으면 Kotlin 생성자 Binding·기본값 처리 실패 | DOC ("kotlin-stdlib and kotlin-reflect must be present") | M0-4, M0-6 | REQUIRED |
| TS-05 | `kotlin-stdlib` | Kotlin Gradle Plugin이 자동 추가 | 표준 Library | 전체 | — | 자동 | 없음 | DOC | M0-2 (`dependencies` 확인) | REQUIRED (명시 불필요) |
| TS-06 | `tools.jackson.module:jackson-module-kotlin` | Version 생략 (`tools.jackson:jackson-bom` 3.1.5 → Boot BOM이 import) | Kotlin DTO JSON 직렬화·역직렬화 (생성자, nullable, 기본값) | Kotlin Controller DTO | 아니오 | 예 | **Jackson 2 좌표(`com.fasterxml.jackson.module`)를 넣으면 Spring MVC에 적용되지 않음.** Boot가 classpath에서 자동 등록 | BOM (jackson-bom 3.1.5에 artifact 존재), DOC (자동 등록) | M0-6 | REQUIRED (첫 Kotlin DTO 전에) |
| TS-07 | `com.fasterxml.jackson.module:jackson-module-kotlin` (Jackson 2) | Jackson 2 BOM 2.21.5 | springdoc(Swagger Schema)이 Jackson 2로 Kotlin Model을 해석할 때 | Swagger 문서 | UNKNOWN | UNKNOWN | Kotlin DTO의 Swagger Schema(필수 여부, Field 이름)가 달라질 수 있음 | 미확인 | M0-6에서 `/v3/api-docs` 비교 | UNKNOWN |
| TS-08 | `kotlin("plugin.lombok")` | TS-01과 같은 Version, **Alpha** (2.3.20) | Kotlin 코드가 같은 Module의 Java Lombok 생성 멤버(`getWorkplace()`, `getId()`, `@RequiredArgsConstructor` 생성자, `ErrorCode` getter)를 참조 | V1~V5 병렬 전환 시 거의 모든 Kotlin 파일 | 아니오 | 조건부 | 없으면 Kotlin이 먼저 컴파일되어 Lombok 멤버를 못 봄 → 컴파일 실패. 대안: 참조 대상 Java Class를 먼저 Kotlin으로 바꾸거나 delombok | DOC (Lombok 지원 목록, Alpha) | M0-3 | CONDITIONAL (병렬 전환이면 사실상 필요) |
| TS-09 | Compiler `jvmTarget` / Toolchain | Java Toolchain 25를 공유. Kotlin 2.3.0+가 Java 25 Bytecode 지원 | Java·Kotlin 대상 Bytecode 일치 | 전체 | — | 설정 확인 | Java 25 / Kotlin 다른 Target이면 `kotlin.jvm.target.validation.mode=error`로 Build 실패 | DOC (Gradle 설정, 2.3.0 Java 25 지원) | M0-2 | REQUIRED |
| TS-10 | `-Xannotation-default-target=param-property` | Compiler 옵션 | 생성자 Property의 Validation Annotation 적용 위치 | Request DTO (`@Valid` 13건) | — | 예 | 미설정 시 Annotation 위치 경고·차이 | DOC (Spring Boot Kotlin 권장) | M0-6 | REQUIRED (Owner 확인) |
| TS-11 | `-Xjsr305=strict` | Compiler 옵션 | Spring Nullability Annotation 해석 | — | — | 아니오 | Spring 7은 JSpecify 사용, Kotlin 2.1+ 기본 strict | DOC | M0-4 컴파일 경고 확인 | NOT_NEEDED |
| TS-12 | `kotlin("test")` / `kotlin-test-junit5` | — | Kotlin Test Assertion | — | 예 (JUnit 6 + AssertJ로 충분) | 아니오 | JUnit 6과 `kotlin-test-junit5` 조합은 미확인 | DOC (Boot: JUnit 6 기본) | — | NOT_NEEDED (M0) |
| TS-13 | MockK / SpringMockK | — | Kotlin final Class Mock | — | 예 (Mockito 5 inline mock maker) | 아니오 | — | DOC (권장이지만 필수 아님) | — | NOT_NEEDED (M0), D2에서 재검토 |
| TS-14 | kapt | — | Kotlin 소스용 Annotation Processor | — | 해당 없음 | 아니오 | Lombok은 Java만 처리. Configuration Processor 미사용 | BUILD | — | NOT_NEEDED |
| TS-15 | `io.freefair.lombok` | — | Lombok Gradle 설정 | — | 예 (기존 설정) | 아니오 | — | BUILD | — | NOT_NEEDED |
| TS-16 | ktlint / detekt | — | Kotlin 컨벤션 검사 (공식 MUST "컨벤션") | `.kt` 전체 | 아니오 (Checkstyle은 Java만) | 별도 판단 | `check`에 넣으면 CI 실패 조건이 늘어남 | — | — | CONDITIONAL (M0 이후 별도 PR) |
| TS-17 | Spring Boot / Dependency Management / Gradle / Toolchain Version 변경 | 현행 유지 | — | — | 예 | 아니오 | — | DOC (Boot 4.1.1: Java 17~26, Gradle 9.x) | — | NOT_NEEDED |

D0-D 주: 위 표의 "판정" 칸은 D0-B 조사 당시 값이며 바꾸지 않았다. 3차 `cdaaa13` 실제 적용과의 차이는 §1-A, 유지 여부는 DL-014.

## 4. 조사 항목 정리

| # | 주제 | 결과 | 상태 |
|---|---|---|---|
| 1 | Kotlin 2.3.20+ `kotlin-jpa`의 no-arg / all-open | 2.3.20부터 `kotlin.plugin.jpa`가 all-open(JPA preset)을 자동 적용. `jakarta.persistence` `Entity`/`Embeddable`/`MappedSuperclass`가 open + 기본 생성자 | DOC 확인, **실제 동작은 M0-5** |
| 2 | `@Transactional` / `@Service` / `@Configuration` Proxy | `plugin.spring`이 Spring Annotation(메타 포함) Class를 open. private 메서드·자기 호출은 Java와 같이 Proxy 밖 | DOC, **M0-4** |
| 3 | Entity 기본 생성자 / Lazy / Dirty Checking | no-arg는 합성 생성자. `val` Field는 Dirty Checking 의도와 어긋날 수 있으므로 상태 Field는 `var` + 제한 setter | INFERENCE, **M0-5** |
| 4 | Nullable ID / DTO Nullability | `BaseEntity.id`는 영속 전 null → `Long?`. Request DTO non-null은 오류 응답 변경 위험 (`COM-001` → `COM-002`) | INFERENCE, **M0-6** |
| 5 | Jackson 직렬화·역직렬화 / Bean Validation | Jackson 3 Kotlin Module 좌표 확인 (TS-06). Validation 위치 Flag (TS-10) | BOM·DOC, **M0-6** |
| 6 | Lombok Java ↔ Kotlin | Kotlin → Java Lombok: Plugin(TS-08) 없으면 실패. Java → Kotlin: Kotlin이 먼저 컴파일되므로 문제 없음. Kotlin 안의 Lombok Annotation은 무시됨 | DOC, **M0-3** |
| 7 | 혼합 컴파일 순서 / 순환 | `compileKotlin`(Java 소스 참조) → `compileJava`(Kotlin 출력 참조). 같은 Module 안 Java↔Kotlin 순환 참조는 일반 선언이면 가능, Lombok 생성 멤버는 TS-08 필요. Package 수준 순환 존재: `global.security`/`global.config` → `domain.user`/`domain.workplace`, 모든 Domain → `global` | 코드 (`MIGRATION-MATRIX` §5), **M0-3** |
| 8 | Gradle / JDK / Boot 호환 | Boot 4.1.1: Java 17~26, Gradle 8.14+ / 9.x. Kotlin 2.3.0+: Java 25 Target. KGP 2.3.21: Gradle 9.3.0까지 완전 지원 (9.7.1은 경고 가능) | DOC, **M0-2** |
| 9 | MySQL / Testcontainers 동작 | Kotlin 추가는 test Profile을 바꾸지 않음. Kotlin Test가 같은 Context를 재사용하는지 확인 필요 | INFERENCE, **M0-7** |
| 10 | CI / Docker 재현 | `check`, `bootJar`, Docker 내부 `build -x test` 모두 Kotlin 컴파일을 포함하게 됨. Alpine 이미지에서 Kotlin Daemon 메모리 | UNKNOWN, **M0-8** |

## 5. M0 — Kotlin 환경 적용 계획

D0-D 주: 아래 M0 계획은 D0-B 작성 당시 계획이며 그대로 둔다. 3차에서 실제로 진행된 내용은 §1-A, §5-A를 본다.

실행 위치: **3차 Kotlin Repository에서 최종 Java Baseline(`BL-TBD`)을 Import한 이후** (DL-013). 2차 Java Repository에서는 M0를 진행하지 않는다.

원칙: **Production 코드(`src/main/java`)는 바꾸지 않는다.** M0의 변경은 `build.gradle.kts`와 `src/test/kotlin`의 검증용 Test만이다. 한 단계 = 한 Commit, 단계마다 Test를 실행한다. M0 전체를 하나의 PR로 올리되 Commit 단위로 리뷰한다.

Test 전용 Bean의 범위 (M0-4~6 공통): `src/test/**`의 Class는 운영 Jar(`bootJar`)에 들어가지 않는다. 그러나 `com.merge.backend` 하위 Test Source에 `@Service`·`@RestController`·`@Entity` 등을 두면 **Test 실행 시 모든 `@SpringBootTest` Context의 Component Scan·Entity Scan에 포함된다** (예: 기존 `global/entity/AuditingTestEntity.java`). 실험용 Bean은 해당 Test에서만 쓰이도록 Nested `@TestConfiguration` + `@Import`, Test Slice(`@WebMvcTest` + `@ContextConfiguration`, `@DataJpaTest`) 또는 Scan 범위 밖 Package 등 명시적 구성으로 제한하고, 그 구성이 실제로 격리되는지를 M0에서 함께 검증한다. 운영 Jar 포함 여부는 M0-8에서 별도로 확인한다.

공통 설정 Owner: **미지정 (D-NOW-01)**. 아래 "Owner"는 모두 이 사람이다.

| 단계 | 변경 대상 | 예상 위험 | 명령 / Test | PASS 기준 | 중단 조건 | PR 검토 기준 |
|---|---|---|---|---|---|---|
| **M0-1** Java Baseline·CI 확인 | 없음 | 3차 Repository Import 결과의 Test 수가 최종 Java Baseline(`BL-TBD`) Evidence와 다름 | `git rev-parse HEAD`; `./gradlew clean check --no-daemon`; `./gradlew dependencies --configuration runtimeClasspath > deps-before.txt` | 최종 Java Baseline Evidence와 같은 Test 수 / 0 실패, Checkstyle 통과, 해당 Commit CI success (참고: D0-B Snapshot `243f77c`는 266) | 실패 1건 이상, Test 수가 Baseline Evidence와 다름 | Evidence 기록만 |
| **M0-2** Plugin / Library 최소 적용 | `build.gradle.kts`: `kotlin("jvm")`, `kotlin("plugin.spring")`, `kotlin("plugin.jpa")` (승인 Version), `kotlin-reflect`, `tools.jackson.module:jackson-module-kotlin`, `kotlin { compilerOptions { freeCompilerArgs.add("-Xannotation-default-target=param-property") } }` | Gradle 9.7.1 경고, JVM Target 불일치, 전이 의존성 Version 변화 | `./gradlew clean check --no-daemon --warning-mode all`; `dependencies` 출력 → `deps-after.txt`와 비교 | Baseline Test 수 / 0 실패. 의존성 차이가 Kotlin 계열 추가뿐 (기존 Library Version 변화 0) | 기존 Library Version이 바뀜, Boot/Gradle Version 변경이 필요해짐, Build 실패 | `build.gradle.kts` 외 변경 없음, `deps` 차이 첨부, 경고 목록 첨부 |
| **M0-3** Kotlin 1개 파일 혼합 컴파일 | `src/test/kotlin/.../M0MixedCompileTest.kt`: Java `Schedule`(Lombok `@Getter`), `ApiResponse.success(...)`, `ScheduleErrorCode.OFFICIAL_SHIFT_CONFLICT.code`를 Kotlin에서 참조. Java Test에서 Kotlin Helper 1개 호출 | Lombok 생성 멤버 미인식 | `./gradlew test --tests '*M0MixedCompile*'` | 컴파일 성공 + Test 통과 | Lombok 멤버 미인식 → TS-08 적용 여부를 Owner가 결정 (적용 시 같은 단계에서 재시도, 미적용 시 전환 순서 재설계) | Kotlin 파일 1~2개, Production 변경 0 |
| **M0-4** Spring Bean / 생성자 주입 / Transaction Proxy | Test 전용 Kotlin `@Service` (생성자 주입 + `@Transactional` 메서드) | final Class → Proxy 미적용 | `@SpringBootTest` Kotlin Test: `AopUtils.isAopProxy(bean)`, 메서드 안 `TransactionSynchronizationManager.isActualTransactionActive()`, RuntimeException 시 Rollback 확인 | Proxy true, Transaction active true, Rollback 후 Row 0 | Proxy false → `plugin.spring` 설정 재확인 후에도 false면 중단 | 실험용 Bean이 Nested `@TestConfiguration`/`@Import` 등으로 해당 Test Context에만 등록되고, 다른 `@SpringBootTest` Context에는 없음을 확인 (`src/test` 위치만으로는 격리되지 않음) |
| **M0-5** JPA Entity / Lazy / Dirty Checking | Test 전용 Kotlin `@Entity` 2개 (`@ManyToOne(LAZY)`), 기존 `global/entity/AuditingTestEntity.java`와 같은 방식. Kotlin Entity가 Java `BaseEntity` 상속 | Entity final → Lazy 불가, no-arg 없음, `ddl-auto: create` Table 이름 충돌 | MySQL test Profile: 저장 → 새 Transaction 조회 → `Hibernate.isInitialized(child.parent)` false, Field 변경 후 `save` 없이 Commit → 재조회 값 변경, `Modifier.isFinal(class)` false | 세 조건 모두 만족 | all-open 미적용 (2.3.20+인데도 final) → 중단, Version·Plugin 재검토 | Test Table 이름 접두어 (`m0_`), 기존 Schema Test(EV-005) 영향 없음 |
| **M0-6** Jackson DTO / Validation / Error 응답 | Test 전용 Kotlin Controller + Request DTO 2종 (non-null / nullable + `@field:NotBlank`). Controller는 `@WebMvcTest` + `@ContextConfiguration`(실험 Controller, `GlobalExceptionHandler`) 같은 Slice 구성으로 제한해 다른 Context에 Endpoint가 생기지 않게 함 | Field 누락 시 `COM-002` 응답, Swagger Schema 변화 | `MockMvc` (webmvc-test): 정상 JSON, Field 누락, 빈 문자열 → HTTP Status, `code`, `data` 비교. `/v3/api-docs`에서 DTO Schema 확인 | nullable + `@field:` 방식이 Java와 같은 `400 / COM-001` + Field 오류. 결과를 AI-RULES §5에 반영 | Kotlin Module 자동 등록 안 됨, 같은 응답을 만들 방법이 없음 → 중단, Decision | 응답 비교표 첨부 |
| **M0-7** 기존 Java 회귀 + `check` | 없음 (M0-3~6 Test 포함 상태) | Kotlin Test Context 추가로 MySQL Container·Context 재사용 변화, 실행 시간 증가 | `./gradlew clean check --no-daemon` (2회) | Java Baseline Test 수 / 0 실패 + M0 Test 전부 통과, 2회 동일 | Java Test 실패·Flaky | Test 수 전후 (Baseline → Baseline+M0), 실행 시간 기록 |
| **M0-8** CI / Docker | 없음 | Alpine Docker Build 메모리·시간, `bootJar`에 Kotlin Runtime 누락 | PR CI (`Backend CI`, `Docker Build CI Test`); 로컬 `docker build -t switch-m0 backend` (선택); `unzip -l build/libs/*.jar \| grep kotlin-stdlib` | 두 CI success, Jar에 `kotlin-stdlib`·`kotlin-reflect` 포함 | CI 실패가 환경 원인 → 원인 기록 후 Owner 결정 | CI Run 링크 2개 |

M0 완료 후: M0 검증 Test를 남길지 (회귀 보호용) 삭제할지 Owner가 결정한다. 남기면 `MIGRATION-MATRIX`의 공통 행에 등록한다.

## 5-A. M0 진행 기록과 후속 (D0-D 추가)

| 단계 | 3차 기록 | 상태 |
|---|---|---|
| M0-1 Java Baseline·CI 확인 | 3차에서 로컬 `clean check`·Test 수 기록 없음. 3차 `main` `8da2bec`는 2차 CI Suite success만 | 미기록 |
| M0-2 Plugin / Library 적용 | PR #1로 적용 (§1-A). `deps-before/after` 차이·경고 목록 첨부 기록 없음 | 적용됨, 검토 근거 일부 미기록 |
| M0-3 ~ M0-7 | 계획한 검증 Test 없음 (`KotlinEnvironmentTest` 1건만) | 미실행 |
| M0-8 CI / Docker | `cdaaa13` 두 CI success (EV-008). Jar 내 `kotlin-stdlib` 포함 확인 기록 없음 | Suite success만 |

| ID | 후속 항목 | 처리 | 관련 |
|---|---|---|---|
| M0-FOLLOWUP-01 | `KotlinEnvironmentCheck.kt`의 main Source Set 유지 필요성 (bootJar 포함, 파일 위치와 `package` 경로 불일치) | **이번에 수정·삭제하지 않음.** 공통 설정 Owner 판단 | DL-014 |
| M0-FOLLOWUP-02 | 3차 `cdaaa13`에서 `dependencies` 해석 결과와 `--warning-mode all` 경고 목록 기록 | 실행 후 `TEST-EVIDENCE`에 새 EV | DL-003 |
| M0-FOLLOWUP-03 | M0-3~M0-7 검증(혼합 컴파일·Proxy·JPA·Jackson DTO)의 실행 여부와 범위 | Owner 결정 | DL-004, DL-011 |

## 6. 공통 설정 Owner가 승인할 사항

1·2·3번은 PR #1로 이미 적용됐다 (§1-A). 아래 목록은 승인 **기록**이 남지 않았다는 뜻으로 유지한다 (DL-003·004).


1. Kotlin Plugin Version: 2.3.21 (Boot BOM 기본값, Gradle 9.7.1은 완전 지원 범위 밖) vs 2.4.20/2.4.21 (Gradle 9.7.0까지 지원, Boot BOM 기본값보다 높음).
2. Lombok 상호운용 방식: `kotlin("plugin.lombok")` (Alpha) 적용 vs 참조 대상 우선 전환 / delombok.
3. `-Xannotation-default-target=param-property` 적용.
4. Request DTO Nullability 원칙 (AI-RULES §5) — M0-6 결과 후 확정.
5. Kotlin 컨벤션 도구 (ktlint/detekt) 채택 여부와 시점.
6. M0 검증 Test 유지 여부.
7. M0 기준 Commit: 3차 Repository의 최종 Java Baseline(`BL-TBD`) Import Commit (DL-013).

## 7. Blocker와 시작 조건

D0-D 주 (2026-10-23): 아래는 D0-B 작성 당시 조건이다. 현재 상태 — 3차 Repository 생성·이관 완료 (`main` `8da2bec`, DL-013), PR #76~78 병합 완료, Kotlin 환경은 PR #1로 적용됨 (승인 기록 `PENDING`, DL-003·004), 최종 Baseline 선언(`BL-TBD`)과 Owner 지정(D-NOW-01)은 미완료. 남은 항목은 §5-A M0-FOLLOWUP.

**Kotlin 환경 구축 전 반드시 해결:**

- 공통 설정 Owner 지정 (D-NOW-01).
- §6의 1·2번 승인 (없으면 M0-2·M0-3을 시작할 수 없음).
- 최종 Java Baseline 확정 (`BL-TBD`: 2차 Repository에서 Test PR #76, #77, #78 병합 후) 및 3차 Repository 생성·Import (DL-013, 주소·이관 방식 미정).

**M0 시작 최소 조건:**

1. Owner 지정 + Version·Lombok 방식 승인.
2. 3차 Repository의 Baseline Import Commit에서 새 Branch (`chore/kotlin-env` 등).
3. M0-1 Baseline 재확인 (Test 0 실패).

## 8. 출처

- Kotlin 2.3.20 What's new (kotlin-jpa all-open 자동 적용, Lombok Alpha, Gradle 호환): https://kotlinlang.org/docs/whatsnew2320.html
- Kotlin Gradle 호환표·JVM Target: https://kotlinlang.org/docs/gradle-configure-project.html
- Kotlin Lombok Plugin: https://kotlinlang.org/docs/lombok.html
- Kotlin 2.3.0 출시 (Java 25 지원): https://blog.jetbrains.com/kotlin/2025/12/kotlin-2-3-0-released/
- Spring Boot System Requirements (4.1.1): https://docs.spring.io/spring-boot/system-requirements.html
- Spring Boot Kotlin Support: https://docs.spring.io/spring-boot/reference/features/kotlin.html
- 실제 해석 결과: EV-007 (`TEST-EVIDENCE.md`)
