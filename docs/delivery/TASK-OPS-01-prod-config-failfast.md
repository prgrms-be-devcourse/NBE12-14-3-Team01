# Task Card 초안 — OPS-01 운영 설정 fail-fast

| 축 | 상태 |
|---|---|
| 상태 | **초안 (승인 전).** 사용자 요청으로 작성 (2026-10-25). 이 카드로는 아무 파일도 바꾸지 않았다 |
| 출처 | [`EXPERIMENT-PLAN`](../evidence/EXPERIMENT-PLAN.md) §5-1 환경·보안 관찰 (정적 확인, 비밀 값 미출력) |
| 성격 | 배포 안전 항목. Kotlin Migration(Track M)·V3 Improvement(Track I)·S4 실험과 별개. 이 카드가 운영 설정 개선안·확인 목록·배포·되돌리기의 원본이다 (S4 계획서는 참조만) |
| 검증 상태 | **운영 안전성은 검증되지 않았다.** 코드에서 "값이 빠지면 개발용 값으로 기동할 수 있는 구조"를 확인했을 뿐이다. Railway의 실제 값·유효성, HTTPS, Cookie 실제 전송, 배포 이력은 배포 담당 확인 전이다. 운영이 공개된 기본 키를 쓰는 것이 확인되면 **우선 대응**, 아니면 **예방적 개선**으로 등급이 갈린다 |
| 필요한 승인 | `application*.yml`·Dockerfile 변경이므로 승인된 Task에서만 수행 (AI-RULES §2). 담당: 공통 설정·배포 담당 (D-NOW-01, 미지정) |

## 1. 왜 필요한가

운영(Railway)에 필요한 환경 변수가 하나라도 빠지면, 지금 설정은 **오류 없이 개발용 값으로 기동한다** (fail-open).

| 빠진 값 | 지금 일어나는 일 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `backend/Dockerfile`이 Profile을 지정하지 않고 기본 Profile이 `dev` → 컨테이너 안 H2 파일 DB, H2 Console 켜짐, SQL TRACE 로그 |
| `JWT_SECRET_KEY` | 공통 `application.yml`의 기본 키로 JWT 서명. 저장소 이력에 있는 키라 공개된 것으로 봐야 함 |
| `COOKIE_SECURE`·`COOKIE_SAME_SITE` | Secure 아닌 Refresh Cookie, SameSite 미지정 |
| `CORS_ALLOWED_ORIGINS` | 허용 출처가 `http://localhost:3000` |

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


1. **병합 전에** 배포 담당이 Railway 값을 확인한다 (값은 공유하지 않고 "있음/없음·같음/다름"만):
   - `SPRING_PROFILES_ACTIVE=prod`가 있는가
   - `JWT_SECRET_KEY`가 있고 저장소 `application.yml`의 기본값과 **다른가**. 같거나 과거에 비어 있던 적이 있으면 **교체** (기존 로그인·Refresh Token 무효 → 사용자 재로그인)
   - `COOKIE_SECURE=true`, `COOKIE_SAME_SITE`, `CORS_ALLOWED_ORIGINS`가 있는가. 운영 Frontend·Backend URL, HTTPS 여부, 같은 site인지 (SameSite 결정에 필요)
   - Railway가 `backend/Dockerfile`로 빌드하는지, 같은 이미지를 쓰는 다른 환경(스테이징 등)이 있는지 (②의 방식 결정에 필요)
   - `init-data` Profile이 꺼져 있고 `APP_INIT_DEFAULT_PASSWORD`가 남아 있지 않은가
2. 값이 하나라도 없으면 이 변경 이후 배포가 **기동 실패**한다 (의도된 동작). 그래서 1을 먼저 끝낸다.
3. 되돌리기: PR Revert. 또는 빠진 환경 변수를 Railway에 추가.

## 6. 남은 질문

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
