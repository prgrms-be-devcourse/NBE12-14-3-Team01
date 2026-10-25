# AUTH-1 서버 로그아웃

| 축 | 상태 |
|---|---|
| 결정 단계 | **사용자 잠정 결정** (Q1·S1·S2·Q2·Q3·Q5, 2026-10-23). 팀 승인 전 |
| ② 정책 | 카드 `PROPOSED`. `DECISION-LOG` G03-D-23 `PENDING`, G03-D-24 `PENDING` |
| ③ 문서 | `DRAFT` (저장소 Proposal 문서. 2026-10-25 이관, 원본 로컬 `drafts/D2-AUTH-1/`) |
| 구현 | 없음 |
| ④ / ⑤ | Backend·Frontend 검증 전부 `SPECIFIED` / `NOT_RUN` |
| 기준 코드 | 3차 `develop` `c0427c2` (Production Java = `cdaaa13` = 2차 `8da2bec`). 정적 확인 |
| 이관 당시 develop HEAD | `a148a62` (2026-10-25). `backend/src/main`은 `cdaaa13` 이후 변경 0 (이관 시 diff로 확인) |
| 코드 근거 재검증 | **NO** (S2-1은 이관만 함. 본문의 줄 번호·Test 근거는 위 "기준 코드" 시점 값). 이후 검증 예정: S2-2~S3 |
| 관련 ID | **G03-D-23** (서버 Logout 채택, 주), G03-D-24 (다중 기기), G03-D-03 (ErrorCode 규칙), E-006 |
| 주관 / 리뷰 | V1 Owner (주관). Frontend (필수). 배포 담당 (운영 Origin·쿠키 값 확인, 필수) |

> 저장소에 옮길 위치: `docs/decisions/proposals/AUTH-1-server-logout.md`. 진행 절차는 `docs/decisions/PROCESS-CHARTER.md`를 따른다.
> 이 카드는 "로그아웃이 실제로 끝나게 하는 것"만 다룬다. Refresh Rotation·해시 저장은 AUTH-2 후보로 넘긴다 (§12).

---

## 1. 왜 지금 정해야 하나

- 로그아웃 버튼은 브라우저의 Access Token만 지운다. Refresh Cookie와 DB의 Refresh Token은 남는다.
- 그래서 브라우저가 열려 있는 동안 `/auth/refresh`로 다시 Access Token을 받을 수 있다 (E-006). Refresh JWT는 로그인 시각부터 최대 14일 유효하다.
- 사용자는 로그아웃했다고 믿지만 인증이 끝나지 않는다. Charter §2의 "제공한다고 한 기능이 동작하지 않음"에 해당한다.

## 2. AS-IS (① `VERIFIED_AS_IS`, 정적 확인)

경로 기준: Backend `backend/src/main/java/com/merge/backend/`, Frontend `frontend/src/`

| 항목 | 사실 | 근거 |
|---|---|---|
| 인증 구조 | **Access Token 검증은 Stateless JWT**(서명·만료만 확인, DB 조회 없음). **Refresh Token은 DB에서 상태 관리** | `global/security/CustomAuthenticationFilter.java` L66-72, `domain/user/entity/User.java` L25-26 |
| Refresh 저장 | User당 1개, DB에 원문 저장, `VARCHAR(255)` (길이 지정 없는 `@Column`) | `User.java` L25-26 |
| 로그인 | 새 Refresh로 무조건 덮어씀 → 계정당 동시에 유효한 Refresh 1개 | `domain/user/service/UserService.java` L61-67 |
| 재발급 | 쿠키 값 ≠ DB 값이면 `AUTH-003` 401. Access Token만 새로 발급 (**Rotation 없음** → Refresh 만료는 로그인 시각 기준 고정, 연장되지 않음) | `UserService.java` L69-84 |
| Token 내용 | Refresh = `{id, type: "refresh"}` + `iat`·`exp`(초 단위), 14일. Access = `{id, email, name}`, 10분. **고유 ID(jti) 없음** | `domain/user/service/AuthTokenService.java` L21-35, `standard/Ut.java` L18-38, `backend/src/main/resources/application.yml` L24-25 |
| 쿠키 | HttpOnly, Path `/`, Secure·Domain·SameSite는 환경변수 (기본 Secure=false, 나머지 미지정). **Max-Age 없음 = 세션 쿠키** | `global/rq/Rq.java` L52-57, L68-82, `application.yml` L28-31 |
| 쿠키 삭제 | `Rq.deleteCookie`가 있음 (같은 속성 + MaxAge 0). Production에서 호출하는 곳 0 | `Rq.java` L59-65. `RqTest` test5가 검증 |
| 서버 로그아웃 | 없음 | — |
| 공개 경로 | signup·login·refresh만. **같은 목록이 두 곳에 따로 있음** | `global/security/SecurityConfig.java` L37-43, `CustomAuthenticationFilter.java` L47 |
| CSRF·CORS | CSRF 보호 꺼짐. CORS는 허용 Origin 환경변수(기본 `http://localhost:3000`) + 쿠키 허용 | `SecurityConfig.java` L61, L97-109, `application.yml` L33-34 |
| 운영 환경값 | CORS Origin, 쿠키 SameSite·Secure·Domain, 비밀키 길이, 운영 DB Column 길이 | **UNKNOWN** (저장소에 없음) |
| Frontend 저장 | Access Token = **localStorage** (브라우저를 닫아도 남음). Refresh = HttpOnly Cookie (JS로 읽기·삭제 불가) | `lib/api.ts` L10-25 |
| Frontend 로그아웃 | `clearToken()` + `/login` 이동. 서버 호출 없음 | `components/layout/AppHeader.tsx` L218-220 |
| Frontend 시작 | 저장된 Token 없음 → `/login`. 있음 → `/auth/me`. 401 → Token 삭제 + `/login`. 그 밖의 오류는 로그인 화면으로 보내지 않음 | `components/providers/CurrentUserProvider.tsx` L40-75 |
| Frontend 경합 방지 | 로그인·로그아웃마다 `sessionVersion` 증가. 회차가 바뀐 뒤 도착한 재발급 결과는 버림. 같은 회차의 재발급 요청은 하나로 합침 | `lib/api.ts` L6-25, L118-147 |
| Frontend Test | Test 파일·도구 없음 | `frontend/package.json` |

## 3. 이전 제안

- G03-D-23 (서버 Logout 채택, `DECIDE_NOW`), G03-D-24 (다중 기기 동시 Refresh, `DEFER`): `docs/references/G-03-contract-review.md`.
- H1 (Security Integration: 권한, Logout, Refresh 무효화 / Rotation): `docs/README.md` §5-1.
- 별도 Auth 제안 원문은 없다.

## 4. 사용자 잠정 결정

| ID | 결정 문장 | 이유 |
|---|---|---|
| Q1-1 | 요청한 Refresh Token이 서버의 현재 저장 값과 같을 때만, 비교와 삭제를 **한 번의 조건부 UPDATE**로 무효화한다. 오래된 기기의 로그아웃은 최신 로그인에 영향을 주지 않는다 | 다른 기기의 최신 로그인 보호. 읽고-비교하고-쓰기로 나누면 경합 때 최신 Token을 지울 수 있음 |
| Q1-2 | 로그아웃은 Access Token 없이 Refresh Cookie로 처리한다. 쿠키가 없거나 이미 무효인 정상 요청은 성공으로 응답하고(멱등), 브라우저 Refresh Cookie는 발급 때와 같은 속성으로 삭제한다 | Access가 만료됐거나 Refresh가 덮어써진 기기에서도 로그아웃이 끝나야 함 |
| S1 | 로그인마다 암호학적 난수 기반 `jti`를 Refresh Token에 넣는다 | jti가 없으면 같은 초의 두 로그인이 같은 Token이 되어 Q1-1이 둘을 구분하지 못함 |
| S2 | 서버가 로그아웃 요청의 `Origin`을 허용 목록과 대조한다. `Origin`이 없으면 `Referer`의 출처로 대조한다. 실패하거나 둘 다 없으면 403이고 아무 상태도 바꾸지 않는다 | 쿠키는 자동 전송되므로 다른 사이트의 강제 로그아웃(CSRF) 방지. CORS는 CSRF 방어가 아님 |
| Q2-1 | Access Token 즉시 무효화(요청마다 DB 확인, 차단 목록, Redis, 세션 Table)를 넣지 않는다 | 정상 로그아웃에서는 남은 Token을 쓸 주체가 없음. 비용이 크고, 사용자 단위 무효화는 Q1-1과 충돌 |
| Q2-2 | Access Token 만료 10분을 유지한다 | 기존 인증 흐름과 재발급 주기 보존 |
| Q3-1 | Refresh Cookie는 기존 세션 쿠키로 유지한다. Max-Age 14일이나 "로그인 상태 유지" 옵션은 넣지 않는다 | 최소 변경. 기기에 인증 정보가 남는 기간을 짧게 유지 |
| Q3-2 | Refresh JWT 유효기간 14일(로그인 시각 기준, 연장 없음)을 유지한다 | 기존 계약 보존 |
| Q5-1 | 로그아웃은 서버 API를 호출하고, 결과와 관계없이 화면 정리(Access Token 삭제, 로그인 화면 이동)를 한다. **화면 로그아웃과 서버 로그아웃을 구분**하고, 서버 무효화가 확인되지 않으면 그 사실을 안내하고 다시 시도를 제공한다. 다시 시도는 Refresh Cookie를 포함한 `POST /auth/logout` 재호출이다 | 네트워크 오류로 사용자를 가두지 않으면서, 서버에 남은 인증을 성공으로 숨기지 않음 |
| Q5-2 | 저장된 Access Token이 있을 때만 앱 시작 시 재발급으로 세션을 확인한다. 401이면 Token 삭제 후 로그인 화면, 네트워크 오류·5xx는 만료로 단정하지 않고 다시 시도 가능한 오류 상태로 둔다. 저장된 Token이 없으면 Refresh Cookie만으로 조용히 자동 로그인시키지 않는다 | 브라우저 재시작 뒤 "로그인처럼 보이다가 끊기는" 현상 제거. 실패한 로그아웃이 자동으로 되살아나지 않게 함 |

**검토 반영 (교차 검토 의견):**
- "완전한 Stateless"가 아니라 "Access Stateless + Refresh DB 상태 관리"로 표현.
- "퇴사하면 DB 확인으로 막힌다"를 전체 API 보장으로 쓰지 않음 (§5).
- "브라우저 종료 = 로그아웃", "브라우저를 완전히 종료하세요" 안내를 쓰지 않음 (세션 복원).
- "탈취 위험이 세 방식 모두 비슷하다"를 "이미 탈취된 Token의 유효기간은 같지만, 지속 쿠키는 기기에 더 오래 남는다"로 정정.
- Q5-1 다시 시도와 Q5-2 앱 시작 확인의 예외를 구분.

## 5. 보장 범위와 제외 범위

| 보장함 (구현·검증 후) | 보장하지 않음 |
|---|---|
| 서버 로그아웃이 성공하면 그 Refresh Token으로 새 Access Token을 받을 수 없다 | 이미 발급된 Access Token의 즉시 무효화. **최대 10분** 유효. 미리 복사·탈취됐다면 그 시간 동안 사용될 수 있다 |
| 서버 로그아웃이 성공하면 그 브라우저의 Refresh Cookie가 삭제된다 | 브라우저 종료로 인한 로그아웃. 세션 복원 기능이 쿠키를 남길 수 있다 |
| 오래된 기기의 로그아웃은 최신 로그인을 지우지 않는다 | 여러 기기 동시 로그인. 계정당 유효한 Refresh는 1개 (G03-D-24) |
| 서로 다른 로그인은 서로 다른 Refresh Token을 받는다 | 탈취된 Refresh Token의 사용 차단 (Rotation·해시 저장 없음, AUTH-2) |
| 출처 검증을 통과하지 못한 로그아웃 요청은 아무것도 바꾸지 않는다 | XSS로 localStorage의 Access Token이 노출되는 위험 (저장 위치 변경은 별도 카드) |
| 서버 로그아웃 실패는 사용자에게 성공으로 표시되지 않는다 | 권한 회수의 즉시 반영. 근무지 권한은 API마다 DB의 현재 소속으로 다시 확인하지만, **확인하지 않는 API가 있다** (SUB-05·SUB-07 목록, 대타 생성 요청자 확인. ML-3·ML-4). API별 권한 회귀 Test 대상 |

## 6. 상태 전이

**서버 (`User.refreshToken`):**

| 전이 | 조건 |
|---|---|
| `X → null` | 로그아웃 요청 Token = X (조건부 UPDATE) |
| `아무 값 → 새 값` | 로그인 (기존 동작) |
| 금지 | 로그아웃 요청 Token ≠ 현재 값일 때 변경 |

**Frontend (한 브라우저 탭):**

| 상태 | 들어오는 조건 | 나가는 조건 |
|---|---|---|
| 로그인됨 | 로그인 성공, 앱 시작 확인 성공 | 로그아웃 버튼, 401 |
| 화면 로그아웃 + 서버 확인됨 | 로그아웃 API 200 | 로그인 |
| 화면 로그아웃 + **서버 미확인** | 로그아웃 API 실패 (네트워크, 5xx, 403) | 다시 시도 성공 → "서버 확인됨". **로그인 성공 → 미확인 표시와 다시 시도를 버림** (§7 F-INV) |
| 연결 오류 | 앱 시작 확인 중 네트워크 오류·5xx | 다시 시도 |

## 7. 불변조건

| ID | 불변조건 |
|---|---|
| AUTH-1-INV-01 | 로그아웃은 요청 Token과 다른 저장 값을 바꾸지 않는다 |
| AUTH-1-INV-02 | 서로 다른 로그인은 서로 다른 Refresh Token을 받는다 |
| AUTH-1-INV-03 | 출처 검증을 통과하지 못한 요청은 아무 상태도 바꾸지 않는다 |
| AUTH-1-F-INV-01 | Frontend는 저장된 Access Token이 없으면 Refresh Cookie로 자동 로그인하지 않는다 |
| AUTH-1-F-INV-02 | 로그아웃 다시 시도는 그 로그아웃 이후 새 로그인이 없을 때만 실행된다. 새 로그인이 있으면 버린다 |
| AUTH-1-F-INV-03 | 로그아웃·로그인으로 회차(`sessionVersion`)가 바뀐 뒤 도착한 재발급 결과는 저장하지 않는다 (기존 안전장치 유지) |

F-INV-02가 필요한 이유: 서버 로그아웃이 실패한 뒤 같은 브라우저에서 다시 로그인하면, 쿠키는 **새 로그인의 Token**으로 바뀐다. 이때 남아 있던 다시 시도가 실행되면 새 Token이 DB 값과 같으므로 Q1-1에 따라 **새 로그인이 지워진다.** 서버는 두 요청을 구분할 수 없으므로 Frontend가 막아야 한다.

## 8. API·Security·Frontend 영향 (구현 시)

| 영역 | 내용 | 기존 계약 변경 |
|---|---|---|
| 새 API | `POST /auth/logout` (경로 후보). 요청 본문 없음. 성공: 200, `ApiResponse.success("200", 메시지, null)` 형식 (기존 Auth 성공 응답과 같은 형식) | 새 API. G03-D-23 승인 대상 |
| 공개 경로 | `SecurityConfig` L37-43과 `CustomAuthenticationFilter` L47 **두 곳 모두** 추가 | Security 설정 변경 |
| ErrorCode | 출처 검증 실패: 새 코드 후보 `AUTH-007` / 403 (기존 `UserErrorCode`는 AUTH-001~006). 시스템 장애는 기존 처리 | ErrorCode 추가 (AI-RULES §2 승인 대상, G03-D-03과 함께 확인) |
| 출처 허용 목록 | `custom.cors.allowed-origins` **값**을 재사용. 검사는 로그아웃 처리에서 따로 함. 정확히 일치(scheme·host·port), 와일드카드 없음. `Origin: null`은 거부 | 운영값 확인 필요 (배포 담당) |
| Repository | 조건부 UPDATE 메서드 1개 (`WHERE id = ? AND refresh_token = ?`) | Schema 변경 없음 |
| Token | `genRefreshToken`에 `jti` (예: `UUID.randomUUID()`). 길이는 약 190~235자로 추정 → 255 이하. **운영 비밀키 길이·Column 길이 확인 필요**. jti 없는 기존 Token은 그대로 동작 (재발급·로그아웃은 jti를 보지 않음) | Token 형식 변경 (내부) |
| Frontend 로그아웃 | ① `/auth/logout` 호출 (`credentials: include`) ② 결과와 관계없이 `clearToken()` + `/login` ③ 실패하면 "서버 로그아웃 미확인" 표시를 남김 (예: `sessionStorage` 표시값, 인증 정보 아님). 로그인 화면이 안내와 다시 시도 버튼을 보여 줌 ④ 로그인 성공 시 표시값 삭제 | Frontend 동작 변경 |
| Frontend 오류 구분 | 네트워크 오류·5xx: "서버 로그아웃을 확인하지 못했습니다. 다시 시도해 주세요" + 다시 시도. 403(출처 실패): 다시 시도해도 같은 결과일 수 있으므로 "보안 확인에 실패했습니다" 안내 + 문의 경로. 200: 표시 없음 | — |
| Frontend 재발급 제외 | `/auth/logout`을 `NO_REFRESH_PATHS`(`lib/api.ts` L63)에 추가 | — |
| Frontend 앱 시작 | Token이 있을 때만 기존 `refreshAccessToken()`을 먼저 호출 (같은 회차 재발급 합치기를 재사용) → 성공이면 `/auth/me` → 401이면 Token 삭제 + `/login` → 네트워크·5xx면 연결 오류 상태 + 다시 시도 | 시작 시 요청 1회 추가 (전체 새로고침 때만) |

## 9. 동시성

| 경합 | 결과 | 보장 수단 |
|---|---|---|
| 기기 A 로그아웃 vs 기기 B 로그인 | B 유지 | 조건부 UPDATE (INV-01) |
| 같은 초의 두 로그인 | 서로 다른 Token | jti (INV-02) |
| 같은 기기 로그아웃 vs 재발급 | 재발급이 먼저 DB를 읽었다면 Access Token 1개가 더 발급될 수 있음. **Q2 범위(최대 10분)로 수용.** Frontend는 회차가 바뀐 결과를 버림 | F-INV-03 |
| 로그아웃 실패 → 재로그인 → 남은 다시 시도 | 새 로그인을 지울 위험 | F-INV-02 (Frontend) |
| 같은 계정 로그인 vs 로그인 | 마지막 로그인이 남음 (AS-IS) | — |
| 앱 시작 확인 vs 다른 요청의 재발급 | 같은 회차 재발급을 하나로 합침 | 기존 `refreshAccessToken()` |

## 10. 검증 계약

모두 ④ `SPECIFIED` / ⑤ `NOT_RUN`. 승인 전 작성 금지. `TEST-INVENTORY` 등록 시 `V1-N-nn`.

### 10-1. Backend

| ID | Given | When | Then | 층위 |
|---|---|---|---|---|
| V1-N-01 | 로그인 후 쿠키 = DB 값, 허용 Origin | 로그아웃 | 200, DB `refreshToken` null, `Set-Cookie` MaxAge 0 | INTEGRATION |
| V1-N-02 | A 로그인 → B 로그인 | A의 쿠키로 로그아웃 | 200, DB = B 그대로, B 쿠키로 재발급 성공 | INTEGRATION |
| V1-N-03 | 쿠키 없음 / 서명 위조 / 만료 / Access Token을 쿠키로 보냄 | 로그아웃 | 200, DB 그대로, 쿠키 삭제 헤더 | INTEGRATION |
| V1-N-04 | 로그아웃 성공 | 같은 Refresh로 `/auth/refresh` | 401 `AUTH-003` | INTEGRATION (**필수**) |
| V1-N-05 | — | `deleteCookie` | 삭제 쿠키의 Path·HttpOnly·Secure·SameSite·Domain = 발급 쿠키 | UNIT (`RqTest` 확장) |
| V1-N-06 | Access Token 없음 | 실제 Security Filter로 로그아웃 | Controller 도달 (401 아님) | API |
| V1-N-07 | 같은 사용자 | 같은 1초 안에 Refresh 2번 발급 | 서로 다른 Token. **현재 코드에서는 실패 예상 = 결함 재현** | UNIT |
| V1-N-08 | jti 포함 Token | 발급 | 길이 ≤ 255 | UNIT |
| V1-N-09 | 허용 Origin / 비허용 / `null` / Origin 없음 + 허용 Referer / 둘 다 없음 | 로그아웃 | 처리 / 403·DB 그대로·쿠키 그대로 / 403 / 처리 / 403 | API |
| V1-N-10 | jti 없는 기존 Token | 재발급, 로그아웃 | 기존과 같게 동작 | INTEGRATION |
| V1-N-11 | 로그아웃(A)과 로그인(B) | 동시 실행 | B 유지 | CONCURRENCY · MYSQL_TC |
| V1-N-12 | DB 오류 주입 | 로그아웃 | 5xx, 성공 응답 아님, DB 변경 없음 | INTEGRATION |

회귀 (기대값 변경 0): `UserServiceTest`, `ApiV1UserControllerTest`, `RqTest`, `CustomAuthenticationFilterTest`.

### 10-2. Frontend (Test 도구가 없으므로 수동 확인 시나리오)

| ID | Given | When | Then |
|---|---|---|---|
| FE-AUTH-01 | 로그인 상태 | 로그아웃 | 개발자 도구에서 Refresh Cookie 없음, localStorage 비어 있음, 로그인 화면, 안내 없음 |
| FE-AUTH-02 | Backend 중지 | 로그아웃 | 로그인 화면 + "서버 로그아웃을 확인하지 못했습니다" + 다시 시도. 새로고침해도 자동 로그인되지 않음 |
| FE-AUTH-03 | FE-AUTH-02 다음 Backend 재시작 | 다시 시도 | 200, 쿠키 삭제, 안내 사라짐 |
| FE-AUTH-04 | FE-AUTH-02 다음 같은 계정 또는 다른 계정으로 로그인 | 로그인 후 상태 확인 | 다시 시도 버튼·표시 사라짐. 새 로그인 유지 (F-INV-02) |
| FE-AUTH-05 | 로그인 → Refresh Cookie만 삭제 (브라우저 재시작과 같은 효과) | 새로고침 | 바로 로그인 화면 (유령 로그인 없음) |
| FE-AUTH-06 | 로그인 상태, Backend 중지 | 새로고침 | 로그인 화면으로 보내지 않고 연결 오류 + 다시 시도. Token 유지 |
| FE-AUTH-07 | 재발급 응답이 늦게 오도록 지연 (개발자 도구 Throttling) | 재발급 중 로그아웃 | 늦게 온 Access Token이 저장되지 않음 (F-INV-03) |
| FE-AUTH-08 | Refresh Cookie는 있고 localStorage는 비어 있음 | 앱 접속 | 로그인 화면. 자동 로그인 없음 (F-INV-01) |
| FE-AUTH-09 | 허용되지 않은 Origin 설정 (로컬 재현) | 로그아웃 | 403 안내 문구가 네트워크 오류 안내와 다름 |

## 11. 재검토 조건

- 비밀번호 변경, 계정 탈퇴, 관리자 강제 로그아웃, "모든 기기에서 로그아웃"을 도입할 때 → Q2-1 재검토. 방식(Redis, DB 세션 버전, Token 식별자 등)은 그때 요구사항과 성능으로 정한다.
- 여러 기기 동시 세션 (G03-D-24) → Q1-1과 저장 모델 재검토.
- 권한 정보를 Access Token 안에 넣을 때 → Q2-1 재검토.
- 운영 배포 구조(Origin, SameSite) 확인 결과가 가정과 다를 때 → S2 재검토.
- "로그인 상태 유지" 요구가 나올 때 → Q3-1을 Improvement 카드로.

## 12. Cross-Domain 영향과 리뷰

| 대상 | 확인할 것 | 리뷰 |
|---|---|---|
| V1 Auth (주관) | 전체 | 필수 |
| Frontend | §8 Frontend 행, §10-2, F-INV-01~03 | 필수 |
| 배포 담당 | 운영 CORS Origin, 쿠키 SameSite·Secure·Domain, 비밀키 길이, 운영 `users.refresh_token` 길이 | 필수 |
| 공통 설정 Owner | 새 ErrorCode `AUTH-007`, Security 공개 경로 | 확인 |
| V2~V5 | 영향 없음. 단 §5의 "권한 회수" 한계는 ML-3·ML-4와 연결 | 없음 |

## 13. 후속 카드로 넘긴 항목

| 항목 | 카드 |
|---|---|
| Refresh Token Rotation, DB 해시 저장, 재사용 탐지 | AUTH-2 (후보) |
| Access Token 저장 위치 (localStorage → 메모리 등), XSS 노출 축소 | AUTH-3 (후보) |
| "로그인 상태 유지" 선택 | Improvement 후보 |
| 다중 기기 세션 | G03-D-24 |
| `/auth/refresh`의 출처 검증 (같은 구조. 응답을 읽을 수 없어 현재 피해 없음) | AUTH-2와 함께 검토 |
| 퇴사자 권한 회수 API별 확인 | ML-3·ML-4 |
| Frontend Test 도구 도입 | 범위 밖 (팀 판단) |

## S1 결정 연결 (2026-10-25)

이 카드 본문은 이관 당시 그대로다. 아래는 2026-10-25에 기록한 사용자 잠정 결정(모두 ② `PROPOSED`, 팀 승인 전)과 이 카드의 연결 위치다. 결정 내용은 연결된 카드가 원본이다. 본문 수정이 필요한 곳은 S2-3 이후 해당 단계에서 고친다.

| 이 카드의 위치 | 연결된 결정 | 요약 |
|---|---|---|
| §ErrorCode 행 (새 code 후보 `AUTH-007` / 403) | [G03-D-03](G03-D-03-error-code.md) §4·§5-2 | 새 code는 `{DOMAIN}-{NNN}` 다음 순번, 공통 목록에 먼저 예약 (S2-4). 403 = 행위자 권한 부족 기준과 맞는지 S2-4에서 확인 |

## 14. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-23 | 초안. Q1·S1·S2·Q2·Q3·Q5 사용자 잠정 결정 통합. 교차 검토 의견 반영 (§4) |
| 2026-10-25 | 저장소 이관 (메타데이터만 수정). "S1 결정 연결" 절 추가와 머리 표 경로를 링크로 변경 (본문 판단 변경 없음) |
