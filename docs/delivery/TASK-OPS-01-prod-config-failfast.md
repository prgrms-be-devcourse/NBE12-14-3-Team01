# Task Card 초안 — OPS-01 운영 설정 fail-fast

| 축 | 상태 |
|---|---|
| 상태 | **초안 (승인 전).** 사용자 요청으로 작성 (2026-10-25). 이 카드로는 아무 파일도 바꾸지 않았다 |
| 출처 | [`EXPERIMENT-PLAN`](../evidence/EXPERIMENT-PLAN.md) §5-1 환경·보안 관찰 (정적 확인, 비밀 값 미출력) |
| 성격 | 배포 안전 항목. Kotlin Migration(Track M)·V3 Improvement(Track I)·S4 실험과 별개. 이 카드가 운영 설정 개선안·확인 목록·배포·되돌리기의 원본이다 (S4 계획서는 참조만) |
| 검증 상태 | **운영 안전성은 검증되지 않았다.** 코드에서 "값이 빠지면 개발용 값으로 기동할 수 있는 구조"를 확인했을 뿐이다. Railway의 실제 값·유효성, HTTPS, Cookie 실제 전송, 배포 이력은 배포 담당 확인 전이다. 운영이 공개된 기본 키를 쓰는 것이 확인되면 **우선 대응**, 아니면 **예방적 개선**으로 등급이 갈린다 |
| 필요한 승인 | `application*.yml`·Dockerfile 변경이므로 승인된 Task에서만 수행 (AI-RULES §2). 담당: 공통 설정·배포 담당 (D-NOW-01, 미지정) |

## 1. 왜 필요한가

아래에 열거한 JWT·Cookie·CORS·Profile 값이 빠지면 개발용 기본값으로 기동할 수 있다 (fail-open). DB 접속 변수처럼 기본값이 없는 값은 별개다. 운영 자동 DDL은 환경 변수 누락과 구분해 함께 관리한다.

| 빠진 값 | 지금 일어나는 일 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `backend/Dockerfile`이 Profile을 지정하지 않고 기본 Profile이 `dev` → 컨테이너 안 H2 파일 DB, H2 Console 켜짐, SQL TRACE 로그 |
| `JWT_SECRET_KEY` | 공통 `application.yml`의 기본 키로 JWT 서명. 저장소 이력에 있는 키라 공개된 것으로 봐야 함 |
| `COOKIE_SECURE`·`COOKIE_SAME_SITE` | Secure 아닌 Refresh Cookie, SameSite 미지정 |
| `CORS_ALLOWED_ORIGINS` | 허용 출처가 `http://localhost:3000` |
| 운영 `spring.jpa.hibernate.ddl-auto=update` (환경 변수 누락과 별개의 현재 설정) | 기동 시 자동 DDL이 실행될 수 있다. 기존 Unique 제거·Enum 허용 값·생성 Column 등을 의도한 순서로 이행·검증하는 절차를 대신하지 않는다. test의 `create` 결과만으로 기존 운영 스키마의 호환성을 보장하지 못한다 |

이미 양호한 것: 운영 DB 접속(`MYSQLURL`·`MYSQLUSER`·`MYSQLPASSWORD`)과 초기 데이터 비밀번호(`APP_INIT_DEFAULT_PASSWORD`)는 기본값 없는 환경 변수다.

## 2. Task Card (AI-RULES §1 형식)

```text
Task ID        : OPS-01 (Board ID 미정)
기준 SHA       : 3차 develop 최신 (작성 시점 a148a62)
변경 대상      : backend/src/main/resources/application-prod.yml
                 backend/Dockerfile
                 (선택) backend/src/main/resources/application.yml, application-dev.yml
                 (선택) application-test.yml 위치 이동 → backend/src/test/resources/
                 (추가) 설정 정적 검증 Test 1개 (src/test, prod Profile을 켜지 않음)
금지 대상      : Production Java 코드, 기존 Test 기대값, build.gradle.kts, Security·Cookie 동작 로직,
                 dev·test Profile의 기존 동작, 운영 DB·Railway 접속, 비밀 값의 출력·기록
보존 계약 ID   : 응답 형식·API 경로 변경 없음. Refresh Cookie 속성은 운영 환경 변수 값 그대로 (Rq L52-80)
관련 Decision  : G03-D-28 (배포 전 운영 노출 점검, PENDING) — 이 Task가 대신 결정하지 않음.
                 D-NOW-01 (배포 담당, PENDING)
실행할 Test    : 전체 ./gradlew check (CI Backend Check), Docker Build CI, 아래 §4의 설정 정적 Test
완료 Evidence  : PR CI Run, 배포 담당의 Railway 확인 결과 (값 없이 있음/없음만)
```

## 3. 변경안

**필수 ① `application-prod.yml` — 운영 필수 값을 기본값 없이 다시 선언**

```yaml
custom:
  jwt:
    secret-key: ${JWT_SECRET_KEY}          # 기본값 없음: 없으면 기동 실패
  cookie:
    secure: ${COOKIE_SECURE}               # 운영은 true
    same-site: ${COOKIE_SAME_SITE}         # 값은 운영 도메인 구조 확인 후 (§3 주석)
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS}
```

SameSite: Frontend·Backend가 다른 site(등록 도메인 기준)면 `None` + Secure, 같은 site면 `Lax` 등도 가능 — 운영 URL·HTTPS 구조 확인 후 결정. Origin이 달라도 같은 site일 수 있으므로 "출처가 다르면 None"으로 단정하지 않는다.

prod Profile에서 공통 `application.yml`의 같은 키를 덮어쓴다. 값이 없으면 Spring이 기동 중 placeholder 해석 실패로 멈춘다. dev·test는 공통 파일의 기본값을 그대로 쓴다. `custom.cookie.domain`은 빈 값이 정상일 수 있어 배포 구조를 확인한 뒤 정한다.

**검토 ② 운영 Profile 보장 — 방식은 배포 구조 확인 후 결정**

```dockerfile
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]
```

위 방식(Dockerfile 고정)은 **이 Dockerfile이 운영 배포 전용 이미지일 때만** 적절하다. 같은 이미지를 테스트·스테이징에도 쓴다면 Profile 고정이 오히려 불편하다. 대안: Railway의 `SPRING_PROFILES_ACTIVE=prod`를 필수 설정으로 관리하고, 애플리케이션 기동 시 운영 필수 값을 검사한다 (후속 코드 Task). 확인할 것: Railway가 이 Dockerfile로 빌드하는지, 스테이징 등 다른 환경이 같은 이미지를 쓰는지. Dockerfile에 한 줄을 넣어도 JWT·Cookie·CORS 안전성은 보장되지 않는다 — ①과 별개로 검증한다.

(사실: 로컬 `bootRun`은 dev 기본값이고, Docker CI는 이미지 빌드만 하고 실행하지 않는다.)

**선택 ③** 공통 `application.yml`의 JWT 기본 키를 제거하고 개발용 키를 `application-dev.yml`·`application-test.yml`로 옮긴다 ("로컬 전용" 표시, 운영 키와 다른 값).

**선택 ④** `application-test.yml`을 `src/test/resources`로 옮긴다. S4 E1 Java 기준선을 먼저 확보한 뒤, 이동 전후 결과가 같은지 비교한다.

**후속 (별도 Task, 코드 변경)** prod Profile 기동 검사: JWT 키 길이(HS256 32 byte 이상), 알려진 개발용 키와 다름, `COOKIE_SECURE=true`.

**후속 (별도 승인 Task) 운영 스키마 이행과 `validate` 검토**

이 문서 수정은 `application-prod.yml` 변경이나 운영 DDL 실행을 승인하지 않는다. ML-H2·ML-2·ML-3도 팀 승인 전이며 새 정책을 대신 승인하지 않는다.

1. 배포 담당이 실제 DB 버전·스키마명·대상 테이블명을 확인하고 `SHOW CREATE TABLE`·인덱스 정의의 시점 있는 스키마 전용 자료를 확보한다. 인증정보·업무 데이터는 Git에 올리지 않는다. 정책 문서의 테이블 표기와 실제 이름이 같은지도 확인한다.
2. 대상 Entity/Enum과 실제 스키마를 대조한다. 기존 Membership Unique, Enum/VARCHAR/CHECK, 생성 Column 등을 확인한다. Hibernate `update`는 일부 누락 객체와 컬럼 타입을 변경할 수 있으므로 "추가만 한다"고 단정하지 않는다. 다만 검토된 제약 제거·데이터 이행·되돌리기를 자동 보장하는 도구로 취급하지 않는다.
3. 정책 승인 후 필요한 forward DDL·데이터 이행·구버전 호환·복구 절차를 리뷰한다. 자동 DDL이 먼저 실행되지 않도록 실행 주체·순서를 정한다. 운영 구조를 복제한 비운영 MySQL에서 이전 스키마→이행→재가입·활성 소속 유일성·동시 가입·새 Enum 저장/구버전 읽기 등을 확인한다.
4. 차이를 해소하고 해당 조합을 검증한 뒤에만 별도 설정 변경으로 `prod`의 `ddl-auto: validate` 전환을 검토한다. 지금 즉시 전환하면 기존 차이로 기동 실패할 수 있다. `validate` 통과도 모든 Unique·Enum·생성식·데이터 불변식이 일치한다는 완전한 증거는 아니므로 명시적 스키마 비교와 DB 테스트를 유지한다.

## 4. 검증 방법

| 확인 | 방법 | 비고 |
|---|---|---|
| prod 설정에 기본값이 없는가 | Test에서 `application-prod.yml`을 YAML로 읽어 `custom.jwt.secret-key` 등이 `${변수}` 형태이고 `:`(기본값)가 없는지 확인 | **prod Profile을 켜지 않는다** (AI-RULES §7) |
| dev·test 동작이 그대로인가 | 전체 `./gradlew check` | 기존 Test 기대값 변경 0 |
| 이미지가 만들어지는가 | Docker Build CI | 컨테이너 실행 안 함 |
| 운영에서 실제로 기동하는가 | 배포 담당이 Railway 배포로 확인 | 아래 §5 확인 후 배포 |

## 5. 배포 순서와 되돌리기

**전체 순서 (S4와의 관계):** 이 Task의 설정 변경은 **S4 E1 Java 기준선(`a148a62`)을 확보한 뒤** 별도 Branch에서 한다. 기준선 전에 설정 파일·Dockerfile을 바꾸면 비교 조건이 달라진다. 계획·조사(배포 담당 확인 요청 포함)는 지금 병행해도 된다.
**예외 — 보안 조치 우선:** Railway 확인 결과 운영이 저장소의 공개된 JWT 기본 키를 쓰고 있거나 dev Profile로 기동 중이면, E1보다 먼저 배포 담당과 즉시 조치 범위(키 교체, 환경 변수 추가)를 협의한다. 이 경우 조치는 Railway 설정 변경이 먼저이고 코드 변경은 그다음이다.


### 5-1. 병합·배포 전 담당자 확인

1. **기동 실패 시 서비스 유지 조건부터 확인한다.** Railway Healthcheck 경로·포트·timeout·인증 허용·준비 완료 응답, 기존 배포 유지와 트래픽 전환, volume 사용 여부를 담당자가 비운영 환경에서 확인한다. Healthcheck 미설정 상태에서 무중단을 가정하지 않는다. 이 문서 작성자는 실제 Railway 설정을 조회하지 않았다.
2. 환경 변수는 값 없이 있음/없음·같음/다름만 기록한다.
   - `SPRING_PROFILES_ACTIVE=prod` 존재 여부
   - `JWT_SECRET_KEY` 존재 및 공개된 개발용 키와 다른지. 공개 키 사용 이력이 있으면 담당자와 즉시 교체 범위를 협의한다. 현재 단일 키로 Access/Refresh를 검증하므로 교체하면 기존 토큰이 거부될 수 있다. 재로그인 공지·시점·혼합 버전 동작을 준비한다.
   - `COOKIE_SECURE=true`, `COOKIE_SAME_SITE`, `CORS_ALLOWED_ORIGINS`, 운영 Frontend/Backend URL·HTTPS·same-site 여부
   - Dockerfile 사용 여부·운영/스테이징 이미지 공유 여부
   - `init-data` Profile 비활성 및 `APP_INIT_DEFAULT_PASSWORD` 정리 여부
3. 실제 스키마의 `SHOW CREATE TABLE`·인덱스 자료를 확보하고 기존 Unique·Enum/VARCHAR/CHECK를 확인한다. `UNKNOWN` 해소와 정책 승인 전까지 ML-H2 등 스키마 변경 기능을 운영에 적용하지 않는다. 스키마 자료만으로 데이터 복구는 할 수 없다.
4. 데이터 손실 가능성이 있는 변경은 별도 데이터 백업/시점 복구(PITR) 가능성·복원 리허설·복구 시간과 배포 중 쓰기 처리 계획을 확인한다. 기존/신규 앱이 겹쳐 실행될 때 양쪽이 스키마·토큰을 처리할 수 있는지도 확인한다.

### 5-2. 적용 순서

위 확인이 끝난 뒤 승인된 설정 변경만 배포한다. 환경 변수 누락 시 새 배포의 기동 실패는 의도된 결과이나, 기존 서비스 유지 여부는 실제 배포 구성으로 확인한다. 스키마 이행·JWT 키 교체는 단순 설정 파일 변경과 구분해 승인된 순서로 수행한다. 적용 후 실제 기동·로그인/Refresh·Cookie/CORS·핵심 경로를 담당자가 확인한다.

### 5-3. 변경 종류별 복구

- **설정 파일·Dockerfile:** PR Revert는 코드만 되돌린다. 실제 환경 변수·Profile과 이전/현재 이미지의 호환성을 함께 확인한다. 누락된 변수 추가나 이전 배포 복구는 담당자 승인하에 한다.
- **JWT 키 교체:** 코드 Revert만으로 기존 세션이 복구된다고 보장하지 않는다. 키를 복원하면 아직 유효한 일부 구토큰이 다시 검증될 수 있으므로 "항상 비가역"이라고도 단정하지 않는다. 만료·폐기·DB Refresh 값 변경은 별개이며, 유출/공개 키를 복원하는 것은 복구안이 아니다. 승인된 새 키 유지·재로그인·필요한 세션 폐기 절차를 준비한다.
- **스키마·데이터 변경:** 코드 Revert나 앱 이미지 복구는 DB를 되돌리지 않는다. 호환성을 유지하는 forward fix, 검토된 reverse DDL, 데이터 백업/PITR 중 가능한 방법을 선택한다. 재가입 이력이 생긴 뒤 옛 `(workplace_id, user_id)` Unique를 그대로 재생성하면 중복 때문에 실패할 수 있다. 새 Enum 값도 구버전 앱에서 읽지 못할 수 있다. 무조건 복구 가능/불가능하다고 단정하지 않는다.
- **기동 실패·Railway Rollback:** 담당자가 Healthcheck 및 트래픽 전환 결과를 확인한다. Railway Rollback은 과거 이미지뿐 아니라 환경 변수를 복원할 수 있으므로, 폐기한 JWT 키가 재활성화되지 않도록 복구 대상의 변수도 함께 검토한다. DB 변경이나 외부 부작용까지 복구한다고 가정하지 않는다.

참고: [Railway Healthchecks](https://docs.railway.com/deployments/healthchecks), [Railway Rollback](https://docs.railway.com/guides/roll-back-bad-deploy), [Hibernate schema configuration](https://github.com/hibernate/hibernate-orm/blob/main/documentation/src/main/asciidoc/introduction/Configuration.adoc). 플랫폼 문서와 실제 서비스 설정은 별개로 확인한다.

## 6. 남은 질문

- 실제 스키마·데이터 복구 계획, `validate` 전환의 별도 Task 범위와 Healthcheck/volume에 따른 서비스 유지 조건
- 배포 담당 (D-NOW-01)
- `custom.cookie.domain`을 운영에서 빈 값으로 둘지
- 선택 ③·④를 이 Task에 포함할지 (④ `application-test.yml` 이동은 E1 기준선 이후, 이동 전후 결과 비교)
- ② 운영 Profile 보장 방식 (Dockerfile 고정 / Railway 필수 설정 + 기동 검사)
- 저장소의 기본 JWT 키가 과거 운영에서 쓰인 적이 있는지 (교체 필요 여부)

## 7. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-25 | 초안. 정적 확인 근거, 변경안, 검증 방법, 배포 순서. 실제 설정 파일 변경 0 |
| 2026-10-25 | 검증 상태 행 추가 (운영 안전성 미검증, 우선 대응 / 예방 개선 구분), 원본 문서 역할 명시 |
| 2026-10-25 | 교차 검토 반영: SameSite는 같은 site 여부로 결정 (`None` 단정 철회), Dockerfile Profile 고정은 "필수"에서 "검토"로 (운영 전용 이미지일 때만, 대안 제시), 설정 변경은 E1 기준선 이후 / 공개 키 사용 확인 시 보안 조치 우선 |
| 2026-10-26 | PR #14 피드백 반영안: 운영 자동 DDL 누락·기동 실패 조건·JWT/스키마 복구 구분 보완. 실제 설정/운영 작업 없음, 정책 PENDING 유지 |
