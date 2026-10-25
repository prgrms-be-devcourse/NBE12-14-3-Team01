# ML-H2 재가입과 현재 소속 유일성

| 축 | 상태 |
|---|---|
| 결정 단계 | **사용자 잠정 결정** (2026-10-23). 기술 선택(U2')은 **실험 통과 조건부** |
| ② 정책 | 카드 `PROPOSED`. `DECISION-LOG` G03-D-11·**G03-D-13** (재가입 방식) `PENDING` |
| ③ 문서 | `DRAFT` (저장소 Proposal 문서. 2026-10-25 이관, 원본 로컬 `drafts/D5-ML-H2/`) |
| 구현 | 없음. Kotlin Migration과 분리된 Improvement |
| ④ / ⑤ | 전부 `SPECIFIED` / `NOT_RUN` |
| 기준 코드 | 3차 `develop` `31ecaee`. 정적 확인 |
| 이관 당시 develop HEAD | `a148a62` (2026-10-25). `backend/src/main`은 `cdaaa13` 이후 변경 0 (이관 시 diff로 확인) |
| 코드 근거 재검증 | **NO** (S2-1은 이관만 함. 본문의 줄 번호·Test 근거는 위 "기준 코드" 시점 값). 이후 검증 예정: S2-2~S3 |
| 관련 ID | G03-D-11, **G03-D-13**, G03-C-09·C-10·C-11, G01-P-03·P-04, AUTH 무관 |
| 앞선 카드 | **ML-H1** (PR #6, H1-b·c·d를 이 카드가 갱신), ML-2 (퇴사 화면), ML-3 (재요청 후보) |
| 주관 / 리뷰 | V2 Owner (주관). V3·V4·V5 (이전 기록 조회), Frontend, 배포 담당 (운영 Schema), 공통 설정 Owner (예외 처리) |

---

## 1. 왜 지금 정해야 하나

- 퇴사 기능(ML-2~5)을 넣으면, 퇴사한 사람이 같은 근무지에 다시 들어오는 경우가 생긴다.
- 지금은 서비스 검사와 DB Unique가 재가입을 막는다. 퇴사자에게는 "이미 참여 중"(WPL-002)이라는 사실과 다른 오류가 나간다.
- 사용자 의견: 재가입은 이번 프로젝트에서 제공한다. 이전 소속 기록은 별개로 남긴다. 현재 소속은 하나이고 역할도 하나다.

## 2. AS-IS (① `VERIFIED_AS_IS`, 정적 확인)

경로 기준: `backend/src/main/java/com/merge/backend/`

| 사실 | 근거 |
|---|---|
| `UNIQUE(workplace_id, user_id)`. 가입 검사 `existsByWorkplaceAndUser`는 `leftAt`과 무관 | `domain/workplace/entity/WorkplaceMember.java` L13-17, `domain/workplace/service/WorkplaceService.java` L64 |
| 가입은 초대 코드만 있으면 EMPLOYEE로 즉시 소속 (승인 단계 없음) | `WorkplaceService.joinWorkplace` |
| **동시 가입 시 Unique 위반 예외를 따로 처리하지 않아 500** (`Exception.class` 처리기) | `global/exception/GlobalExceptionHandler.java` L118 |
| 초대 코드: 근무지 생성 때 1회 생성 (`UUID` 앞 8자리 대문자, 중복이면 다시 생성). **재발급 API 없음.** 조회는 **현재 관리자만** | `WorkplaceService.java` L28-56, L106-112, `domain/workplace/entity/Workplace.java` L18-22 |
| **MANAGER는 근무지를 만든 사람뿐.** 역할을 바꾸는 코드 경로 없음 (생성 시 MANAGER, 가입 시 EMPLOYEE) | `WorkplaceService.java` L40, 전체 검색 (InitData 제외) |
| 단건 조회 `findByWorkplaceIdAndUserId`(`leftAt` 조건 없음)를 Pattern API 관리자 확인에서 사용 | `domain/workplace/repository/WorkplaceMemberRepository.java` L27, `domain/shift/service/RegularShiftPatternService.java` L237-249 |
| 알림 목록: 사용자 + `leftAt IS NULL` 소속만 → 재가입 후 이전 소속 알림은 안 보임 | `domain/notification/repository/NotificationRepository.java` L19-23 |
| 보낸 대타 목록: **사용자 기준, 소속 기간 구분 없음** → 재가입 후 이전 소속 요청이 다시 보임 | `domain/substitute/repository/SubstituteRequestRepository.java` L20 |
| 근무 불가 시간은 User에 묶임 → 소속이 바뀌어도 이어짐 | `UnavailableTime.user` |
| Membership Repository Test는 MySQL Testcontainers 9.7.2에서 실행 (`create`) | `WorkplaceMemberRepositoryTest` (test Profile, 내장 DB 대체 안 함), `application-test.yml` |
| 운영 MySQL 버전, 실제 Index 이름·Column 형식 | **UNKNOWN** |

## 3. 사용자 잠정 결정

| ID | 결정 문장 | 성격 |
|---|---|---|
| H2-1 | 재가입은 **새 Membership Row**를 만든다. 이전 Row를 재활성화하거나 합치지 않는다. 이전 근무·Pattern·대타·알림의 `member_id`를 새 Row로 바꾸지 않는다 | 정책 (ML-H1 H1-a·c 확정) |
| H2-2 | 같은 근무지·사용자에 **현재 소속(`leftAt IS NULL`) Row는 최대 1개**. 그래서 현재 역할도 1개다. 이것을 **DB 제약**으로 보장한다 | 정책 |
| H2-3 | 같은 사람이 같은 근무지에 여러 역할로 동시에 소속되는 경우는 지원하지 않는다 | 범위 |
| H2-4 | 재가입 = **유효한 초대 코드 + 현재 소속 없음**이면 하는 일반 가입이다. 별도 승인·차단 단계는 없다. 새 소속은 EMPLOYEE로 시작한다 | 정책 |
| H2-5 | 관리자는 초대 코드를 **재발급**할 수 있다. 재발급하면 이전 코드는 즉시 무효다 (WPL-001). 퇴사 처리 화면에 "초대 코드도 재발급"을 **선택 사항(기본 해제)**으로 두고, 퇴사와 무관한 독립 재발급 경로도 둔다 | 정책 |
| H2-6 | **초대 코드 재발급은 이전 코드의 사용을 막는 수단이지, 특정 사용자의 재가입을 막는 기능이 아니다.** 퇴사자가 다른 경로로 새 코드를 받으면 들어올 수 있다. 개인별 재가입 차단·해제, 가입 승인은 이번 범위에서 제외 (후속 Improvement) | 범위·한계 |
| H2-7 | 동시 가입으로 현재 소속이 2개가 되려 하면 하나만 성공하고 나머지는 **WPL-002 (409)** | 계약 |
| H2-8 | 현재 소속 유일성의 기술 수단은 **U2' (DB 생성 Column + 복합 Unique) 우선**. 실험(§9) 실패 시 U3 (저장 marker + Unique + CHECK) | **기술 검증 대기** |

| H2-9 | **퇴사 처리 대상은 현재 EMPLOYEE 소속만.** 현재 MANAGER 소속은 소속 종료 Command의 대상이 아니며, 서버가 대상 소속의 실제 역할을 검사해 거부한다 (화면에서 버튼을 숨기는 것만으로 처리하지 않음). 관리자의 로그아웃은 그대로 가능 | 정책 (사용자 확인 2026-10-23) |
| H2-10 | 정상 가입·재가입이 **Commit된 뒤** 그 근무지의 현재 관리자에게 알림 생성을 **시도**한다. Type 1개(예: `WORKPLACE_MEMBER_JOINED`), 문구로 신규·재가입을 구분 (재가입 = 같은 근무지에 종료된 소속이 있음). 다른 근무지 관리자에게는 보내지 않는다. 가입 한 건에 알림 한 건 | 정책 |
| H2-11 | 가입 알림은 **"직원이 제대로 가입했다"는 사후 안내**다. 가입 승인도 아니고, 특정 사용자의 재가입을 막지도 않는다. 알림 실패·미열람은 가입을 취소하지 않는다. 정확한 소속 상태의 기준은 **기존 구성원 목록 API** (`GET /workplaces/{id}/members`, 관리자 전용, 현재 소속만 반환)다 | 정책·한계 |
| H2-12 | 근무지 정리(폐쇄), 관리자 위임·역할 변경, 관리자 계정을 쓸 수 없게 된 경우의 복구는 **이번 MVP에서 제공하지 않는 운영 한계**로 기록한다 (후속 보완). 지금은 계정 삭제 API가 없어 계정 삭제로 관리자가 0명이 되는 경로는 없다 (`ApiV1UserController`: signup·login·refresh·me뿐). 계정 삭제를 도입하면 이 위험을 다시 본다 | 범위·한계 |
| H2-13 | 재가입 후에도 **근무 불가 시간은 User 단위로 유지**된다 (AS-IS). 의도된 동작으로 둔다 | 정책 (AS-IS 유지) |

**범위 확인 (사용자 확인 2026-10-23):** "현재 소속 1개"는 **근무지별**이다. 한 사용자는 여러 근무지에 각각 1개씩 소속될 수 있다 (AS-IS 유지: 내 근무지 목록, 다른 근무지 근무 충돌 검사).

## 4. 놓치기 쉬운 결과 (이 결정이 다른 곳에 주는 영향)

| # | 사실과 결과 | 처리 |
|---|---|---|
| ① | **MANAGER는 생성자 1명뿐이고 역할 변경 경로가 없다.** 관리자가 퇴사하면 그 근무지에는 관리자가 없어져 근무표·대타 승인·초대 코드 재발급이 영원히 불가능하다. 관리자가 재가입하면 EMPLOYEE로 돌아와 다시 관리자가 될 수 없다 | **ML-4 필수 결정.** 이전 원문(ML-DECISION-01)은 "현재 MANAGER가 현재 EMPLOYEE를 퇴사 처리"였다. → **H2-9로 결정** (EMPLOYEE만 퇴사 대상). 위임·역할 변경은 범위 밖 (H2-12) |
| ② | 승인 단계가 없으므로 관리자는 **누가 (다시) 들어왔는지 모를 수 있다** | → **H2-10·H2-11로 결정** (가입 후 관리자 알림, 사후 안내) |
| ③ | 초대 코드는 관리자만 조회하므로, 재발급 후 새 코드는 **관리자가 직접 전달**해야 한다 | Frontend: 재발급 결과에 새 코드 표시 |
| ④ | 재발급은 그 근무지의 **모든** 예비 가입자에게 영향 (기존 코드를 받은 신입도 막힘) | 퇴사 화면 선택은 기본 해제. 안내 문구 |
| ⑤ | 보낸 대타 목록이 사용자 기준이라 재가입자에게 이전 소속 요청이 보임 | ML-4 (이전 기록 접근) |
| ⑥ | 근무 불가 시간은 User 기준이라 재가입 후에도 그대로 적용 | 의도된 동작으로 기록 (회귀 Test) |

## 5. 상태 전이

| 대상 | 허용 | 금지 |
|---|---|---|
| WorkplaceMember | 생성 (현재) → 퇴사 (`leftAt` 기록, 1회) | 재활성화, 이전 Row에 다시 가입, 역할 변경 (범위 밖) |
| 같은 (근무지, 사용자) | 종료 Row 0~N개 + 현재 Row 0~1개 | 현재 Row 2개 이상 |
| Workplace.inviteCode | 현재 코드 → 새 코드 (관리자 재발급) | 새 코드 = 직전 코드 |

## 6. 불변조건

| ID | 불변조건 | 보장 수단 |
|---|---|---|
| ML-H2-INV-01 | 같은 (근무지, 사용자)에 `leftAt IS NULL` Row ≤ 1 | DB 제약 (U2' 또는 U3) |
| ML-H2-INV-02 | (U3일 때만) `active_marker = 1` ⇔ `leftAt IS NULL` | DB CHECK + Entity 캡슐화 + Test. U2'에서는 구조상 항상 성립 |
| ML-H2-INV-03 | 이전 소속에 연결된 기록(Shift, Pattern, Request, Candidate, Notification)의 `member_id`는 재가입으로 바뀌지 않는다 | 쓰기 경로 없음 + 회귀 Test (ML-H1-INV-01 확장) |
| ML-H2-INV-04 | 재발급 확정 후에는 이전 코드로 가입이 성공하지 않는다 | 가입·재발급이 같은 Workplace Row Lock + Lock 후 코드 재비교 (§8) |
| (참조) ML-H1-INV-02 | `leftAt`은 한 번 기록되면 바뀌지 않는다 | — |

## 7. API·DB·Frontend 영향

| 영역 | 내용 | 기존 계약 변경 |
|---|---|---|
| `POST /workplaces/join` | 유효 코드 + 현재 소속 없음 → 새 Row (재가입 포함). 현재 소속 있음 → WPL-002. 무효 코드 → WPL-001. **H1-d(퇴사자 전용 오류)는 쓰지 않음** | 퇴사자의 결과가 오류 → 성공으로 바뀜 (현재는 도달 불가 상태) |
| 초대 코드 재발급 | 새 API (예: `POST /workplaces/{id}/invite-code`). 현재 관리자만, 그 근무지만. 응답에 새 코드 | 새 API |
| 퇴사 Command | 선택 입력 "초대 코드 재발급" (ML-2 퇴사 화면). 퇴사와 같은 Transaction에서 처리할지는 ML-4·5 | ML-2·ML-4 |
| 예외 처리 | 이름을 붙인 현재 소속 Unique 위반만 WPL-002(409)로 변환. 다른 제약 위반은 그대로 (`GlobalExceptionHandler`에 `DataIntegrityViolationException` 처리 + 제약 이름 확인) | AS-IS 500 → 409 (결함 수정 성격) |
| DB | U2': 생성 Column + 새 Unique, 기존 Unique 삭제. U3: Column + 데이터 채우기 + CHECK + 새 Unique, 기존 Unique 삭제 | §9 |
| 단건 조회 | `findByWorkplaceIdAndUserId` → 현재 소속 조회로 교체 | 내부 |
| 알림 (후보) | 가입·재가입 시 관리자 알림 (§10 질문 2) | 새 Type |
| Frontend | 재가입 성공 화면은 일반 가입과 같음. 관리자 화면에 초대 코드 재발급 버튼과 새 코드 표시, 퇴사 화면 선택지 | Frontend |

## 8. 초대 코드 보안·경합 최소 점검

| 항목 | 계약 |
|---|---|
| 직전 코드와 같은 값 | 생성 시 "현재 코드와 다름 + 다른 근무지 코드와 중복 아님"을 확인. 더 오래된 코드와 우연히 같아질 확률은 8자리 16진수(약 43억 가지)라 매우 낮다. 이전 코드 이력은 저장하지 않는다 (이 한계를 기록) |
| 권한 | 인증된 현재 관리자 + 그 근무지만. 다른 근무지 코드 재발급 거부 |
| 난수 | `UUID.randomUUID()` (SecureRandom 기반) 앞 8자리. 문자 집합은 16진수 |
| 추측 시도 | 가입은 로그인이 필요하지만 시도 횟수 제한은 없다. WPL-001로 코드 존재 여부가 드러남. **횟수 제한은 후속 보안 개선 후보** |
| 가입 vs 재발급 경합 | **B안 채택 (직렬화):** 가입도 Workplace Row를 잠그고, Lock 후 제출 코드를 DB의 최신 코드와 다시 비교한다. 재발급도 같은 Row를 잠근다 (`WorkplaceRepository.findByIdForUpdate` 존재). 근무지별 가입이 순서대로 처리되는 비용은 작다. ML-5 필수 경합 Test |

## 9. 기술 선택과 운영 적용

### 9-1. U2' (우선) vs U3 (대안)

| | U2' | U3 |
|---|---|---|
| 구성 | `active_marker` = `CASE WHEN left_at IS NULL THEN 1 END` (DB 생성 Column) + `UNIQUE(workplace_id, user_id, active_marker)` 이름 지정 | 앱이 marker 저장 + 같은 Unique + CHECK |
| 두 값 어긋남 | 구조상 없음 | CHECK + 캡슐화로 막음 |
| 예전 앱 배포 공백 | 없음 (DB가 계산) | 있음 (아래 순서 필수) |
| Entity | Hibernate `@GeneratedColumn` (지원은 문서상 확인, **이 프로젝트에서 실제 DDL 생성은 미확인**) | `@UniqueConstraint`, Hibernate `@Check` |
| 운영 조건 | 생성 Column 지원 (MySQL 5.7 이상) | CHECK 강제 (8.0.16 이상) |

### 9-2. U2' 실험 계획 (이번에는 실행하지 않음)

- 위치: 격리 Branch 또는 로컬 실험. Production·저장소 Test는 바꾸지 않는다. 실행은 별도 승인.
- 환경: 3차 `develop` 기준, Testcontainers MySQL 9.7.2, Hibernate 7.4.x, `ddl-auto: create`.

| # | 확인 | 통과 기준 |
|---|---|---|
| S1 | DDL 생성 | 생성된 `workplace_member` 정의에 생성 Column과 이름 붙은 복합 Unique가 있음 (`SHOW CREATE TABLE`) |
| S2 | INSERT | 현재 소속 저장 시 `active_marker = 1` |
| S3 | UPDATE | `leftAt` 기록 후 `active_marker = NULL` |
| S4 | 중복 | 같은 (근무지, 사용자) 현재 Row 2개 저장 → Unique 위반 |
| S5 | 재가입 | 종료 Row 2개 + 현재 Row 1개 공존 |
| S6 | 예외 | 위반 예외에서 제약 이름을 읽어 WPL-002로 변환. 다른 제약 위반은 변환 안 됨. Transaction Rollback 상태에서 응답 정상 |
| S7 | 동시 가입 | 스레드 N개 동시 가입 → 성공 1, 나머지 409 (`ForcedOrderTest`·`SimultaneousTest` 방식) |
| S8 | Entity 읽기 | `active_marker`를 Entity가 쓰지 않음 (insertable·updatable false) |

- **추가 성공 기준 (2026-10-23):** 재가입 후 이전 Row 보존, 같은 (근무지, 사용자) 현재 Row ≤ 1, 동시 가입 2건 중 1건만 성공, 실제 DB Unique 위반이 WPL-002(409)로 응답, Hibernate가 만든 DDL과 MySQL `SHOW CREATE TABLE` 결과가 일치, **예전 앱(지금 Entity)과 새 Schema의 호환 조건**(예전 앱이 생성 Column을 몰라도 INSERT·SELECT가 되는지).
- 결과 기록: 실행하면 `TEST-EVIDENCE` 형식으로 EV를 만든다. 실패하면 원인을 분류하고 U3로 전환을 제안한다.

### 9-3. 운영 적용 원칙 (확정 DDL 아님, 팀 결정 대기)

1. 운영 MySQL 버전, 기존 Unique Index 이름, `left_at` Row 수(0 예상), 같은 (근무지, 사용자) 중복 0건 확인.
2. 새 Column(U2' 생성 Column / U3 일반 Column + 데이터 채우기) 추가.
3. 새 Unique 추가. 기존 Unique와 함께 있어도 된다 (기존이 더 엄격).
4. 새 앱 배포. 재가입 허용은 꺼 둔다 (기존 Unique가 아직 막음).
5. (U3) 불일치 0건 확인.
6. 기존 Unique 삭제 → 재가입 허용.

- **Rollback:** Schema Rollback과 앱 Rollback을 따로 판단한다. **기존 유일성 가정(같은 근무지·사용자에 Row 1개)을 깨는 데이터가 생긴 뒤에는 예전 앱으로의 안전한 Rollback을 보장할 수 없다** (예전 앱의 단건 조회가 깨짐). 6단계 직후 재가입 Row가 아직 없으면 예전 앱 일부 기능은 동작할 수 있지만, 기존 Unique 복구는 별도 검증이 필요하다.
- ML-2·ML-3 DB 변경(취소 Column, 배정 이력, 대타·알림 Enum)과 묶어 하나의 운영 변경 계획으로 정한다.

## 10. 초대 코드 관리 정책 (MVP 최소 범위)

사용자 의견: 재가입은 코드를 받고 들어오는 것을 허용하되, 초대 코드 관리는 별도 정책이 필요하다. 이번 MVP에서 정하는 것과 미루는 것을 나눈다.

| 항목 | MVP (사용자 잠정 결정) | 후속 |
|---|---|---|
| 조회 | 현재 관리자만 (AS-IS) | — |
| 재발급 | 현재 관리자만, 수동. 독립 경로 + 퇴사 화면 선택(기본 해제). 이전 코드 즉시 무효, 새 코드 ≠ 직전 코드 | 자동 주기 재발급 |
| 전달 | 관리자가 직접 전달. **받은 사람이 다시 공유할 수 있다는 한계**를 기록 | 개인별 초대 링크 |
| 유효기간 | 없음 (재발급할 때까지 유효) | 만료 시각 |
| 추측 시도 | 제한 없음 → **보안 위험으로 기록** | 시도 횟수 제한 (현재 배포 형태에서 가능한 방식부터 검토. 별도 인프라 전제 아님) |
| 경합 | 가입·재발급 직렬화 (§8 B안) | — |

## 10-1. 해결된 질문 (2026-10-23)

1. 관리자 퇴사 → H2-9 (EMPLOYEE만 퇴사 대상).
2. 가입·재가입 알림 → H2-10·H2-11 (사후 안내).

## 11. 검증 계약

모두 `SPECIFIED` / `NOT_RUN`. 승인 전 작성 금지. 등록 시 V2 구역 (V2-N-01~05는 ML-H1).

| ID | Given → Then |
|---|---|
| V2-N-06 | 퇴사한 사용자, 유효 코드로 재가입 → 새 Row(새 ID·`joinedAt`, EMPLOYEE), 이전 Row 그대로 |
| V2-N-07 | 같은 사용자의 가입 2건 동시 → 현재 소속 1, 나머지 409 WPL-002 (MySQL) |
| V2-N-08 | 현재 소속자 재가입 → WPL-002 (기존 회귀) |
| V2-N-09 | 현재 Row 2개를 직접 저장 → Unique 위반 (제약 검증) |
| V2-N-10 | 다른 제약 위반 → WPL-002로 바뀌지 않음 |
| V2-N-11 | 재발급 → 이전 코드 WPL-001, 새 코드 성공, 새 코드 ≠ 직전 코드. 관리자 아님·다른 근무지 → 거부 |
| V2-N-12 | 가입(이전 코드)과 재발급 동시 → 재발급이 먼저 확정되면 가입 실패 (MySQL) |
| V2-N-13 | 재가입 후 Pattern API 관리자 확인 → 단건 조회 예외 없음 |
| V2-N-14 | 재가입 후 새 근무표 → 이전 소속 Pattern·취소 근무 미반영 |
| V2-N-15 | 재가입 후 이전 소속 기록의 `member_id` 불변 (INV-03) |
| V2-N-16 | 재가입 후 알림 목록에 이전 소속 알림 없음 (AS-IS 회귀), 근무 불가 시간 유지 |
| V2-N-17 | (U3일 때만) marker·`leftAt` 불일치 저장 → CHECK 위반 |
| V2-N-18 | 현재 MANAGER 소속을 퇴사 대상으로 요청 → 거부 (서버 역할 검사), 아무것도 바뀌지 않음 |
| V2-N-19 | 신규 가입 Commit → 그 근무지 현재 관리자에게 알림 1건 (신규 문구). 다른 근무지 관리자 0건 |
| V2-N-20 | 재가입 Commit → 알림 1건 (재가입 문구) |
| V2-N-21 | 가입이 Rollback(예: 동시 가입으로 409) → 알림 0건 |
| V2-N-22 | 알림 생성 실패 주입 → 가입은 성공, 구성원 목록에 새 소속 표시 |
| 기존 | `WorkplaceMemberRepositoryTest.rejectDuplicateMembershipAtDatabaseLevel`: 의미가 "같은 조합 금지" → "현재 소속 중복 금지"로 바뀜. **승인된 Decision 필요** (Charter §6-2) |

## 12. 다른 카드와의 차이 (조용히 덮어쓰지 않음)

| 카드 | 이전 | 이 카드 이후 | 처리 |
|---|---|---|---|
| ML-H1 H1-b (PR #6) | 재가입 구현 범위 미결정 | **이번 프로젝트에 포함** | ML-H2 문서 PR에서 ML-H1 변경 이력 추가 (PR #6 범위를 키우지 않음) |
| ML-H1 H1-c | 새 Row 우선 후보 | 확정 (H2-1) | 같음 |
| ML-H1 H1-d | 퇴사자 재가입 시 새 ErrorCode | **대체됨.** 재가입이 정상 Path. 무효 코드 WPL-001, 현재 소속 WPL-002 | 같음. V2-N-04(ML-H1-T4)는 V2-N-06으로 대체 |
| ML-H1 §10 | ML-3 이후 ML-H2에서 재검토 | 이 카드 | — |
| ML-2 | 퇴사 화면 | "초대 코드도 재발급" 선택지 추가 (기본 해제) | ML-2 Draft에 한 줄 추가 예정 |
| ML-3 | 재요청 시 현재 소속 기준 후보 | 재가입자는 새 Row로 후보가 됨 | 충돌 없음 |

## 13. 후속

| 항목 | 카드 |
|---|---|
| 관리자 위임·역할 변경·근무지 정리·관리자 계정 복구 | 후속 보완 (H2-12) |
| 재가입자의 이전 기록 접근 (보낸 대타 목록 등) | ML-4 |
| 가입·재발급 경합 Lock, 전역 순서 | ML-5 |
| 개인별 재가입 차단·해제, 가입 승인, 초대 코드 유효기간, 추측 시도 제한 | 후속 Improvement |
| 운영 DDL·Rollback | 팀 결정 후보 (ML-2·ML-3과 묶음) |

## 14. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-23 | H2-9~H2-13 추가 (관리자 퇴사 제외, 가입 알림 = 사후 안내, 운영 한계, 근무 불가 시간 유지). §10 초대 코드 관리 정책(MVP 최소), V2-N-18~22 |
| 2026-10-23 | 초안. H2-1~H2-8 사용자 잠정 결정 (U2'는 실험 조건부). 관리자 유일성·역할 변경 부재 발견(§4-①), 가입 알림 후보, 초대 코드 경합 B안 |
