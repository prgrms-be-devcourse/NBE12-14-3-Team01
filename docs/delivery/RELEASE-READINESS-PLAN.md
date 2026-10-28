# SWITCH — Kotlin 마이그레이션 종료부터 사용자 경로·배포 검증까지

> 상태: DRAFT. 2026-10-28 KST의 실행 계획이며 새 정책 승인이나 기능 구현 지시가 아니다. 작업 담당·출시 범위·미정 계약은 팀이 확정한다. 제출 목표는 팀 계획의 2026-10-29 12:00이며, 마감 때문에 완료 조건을 낮추지 않는다.
>
> 대상 저장소: prgrms-be-devcourse/NBE12-14-3-Team01. 문서 Branch 시작점은 develop 1e905e26bb0de13f8b5581a9acfcd08dd7f86dd3다. 현재 상태 조회 기준 origin/develop은 a5880334697d1151d6eeee7ca5059772bd4a88fb, Tree d030da7b7fa8ed751b2a4e44c924434f0a569edb다. 이후 Commit이 바뀌면 각 실행 결과의 기준을 새로 기록한다.

## 0. 이 문서의 역할과 작업 카드

이 문서는 사람이 작업을 나누고 AI가 같은 순서로 검증하도록 하는 실행 순서다. 현재 상태의 원본은 Git·PR·Issue·JUnit XML·CI이며, 정책은 Decision Log §4의 채택 범위가 기준이다. 문서의 과거 PROPOSED 문구나 테스트 통과를 새 정책 승인으로 읽지 않는다. 현재 진행 현황은 이 문서의 스냅샷을 반복 갱신하기보다 Migration Matrix·Issue·새 Evidence에 연결한다.

- Task ID: 2026-10-28 사용자 요청에 따른 출시 준비 계획 초안. Delivery Board ID는 팀 확인 전이다.
- 변경 대상: 이 계획 문서와 docs/README.md의 문서 Index 한 행. 코드·테스트·정책·기존 Evidence는 변경 대상이 아니다.
- 금지 대상: Production/Test, application 설정, Gradle 의존성, 승인되지 않은 API·DB·Lock·ErrorCode 변경.
- 참고 계약: CONTRACT-BASELINE의 각 도메인 행, DECISION-LOG §4, IMPLEMENTATION-PRIORITY §3. 세부 Task에서는 정확한 ID와 승인 상태를 다시 적는다.
- 이번 문서 검증: 기준 SHA와 현재 파일/Issue/CI 대조, 링크 확인, 문서 Diff 점검. 코드를 실행했다는 의미가 아니다.
- 후속 실행 Evidence: 각 Task에 SHA·Tree·환경·명령·JUnit XML·CI Run·판정을 남긴다.

실행자는 먼저 docs/README.md의 읽기 순서와 docs/ai/AI-RULES.md, docs/STATUS-AXES.md를 따른다. 작업마다 Task Card를 채우고, Commit·Push·PR·Merge·운영 접속은 각각 필요한 사람의 명시적 권한 범위에서 수행한다.

## 1. 완료를 다섯 층으로 말한다

1. **Migration 범위 완료:** 약속한 Java 파일의 Kotlin 전환 또는 의도적 잔존 사유, 호출자, 사례·Assertion 대응이 확인됐다.
2. **코드 통합 완료:** 현재 develop에서 관련·전체 테스트와 CI가 통과하고, 병합된 Tree가 검증한 내용과 연결된다.
3. **P0 업무 완료:** 팀이 이번에 제공한다고 선택한 사용자 경로가 권한·상태·Rollback·재조회·화면까지 닫혔다.
4. **배포 후보 검증 완료:** 격리 환경의 실제 기동, MySQL Schema·SQL·Lock, 대표 API/화면 Smoke, 복구 절차가 확인됐다.
5. **제출 완료:** 코드 Branch, 기획서, WBS, 발표자료, 시스템 구성도/ERD/API 문서의 최종 링크와 시연 범위가 일치한다.

앞 단계가 성공했다고 다음 단계를 자동 통과시키지 않는다. Docker 이미지 빌드 성공은 기동 성공이 아니고, CI Suite 성공은 개별 JUnit 사례의 실행 근거가 아니다. Kotlin 스타일 검사도 Java Checkstyle과 구분한다.

## 2. 확인된 현재 상태와 바로잡을 기록

- develop a588033…에는 PR #71 ShiftServiceTest Java→Kotlin 전환이 추가됐다. 이 Commit의 Backend CI 40626252291과 Docker CI 40626252237은 모두 SUCCESS다. 현재 Commit의 개별 JUnit XML은 이 문서 작성 때 별도로 수집하지 않았다.
- 직전 Notification 최종 감사의 f19e996…에서는 488/488 JUnit 실행, 실패·오류·Skip 0, Portable Gate PASS였다. 그 뒤 #70이 Schedule Java 테스트 2개를 Kotlin으로 전환했으므로 이 488건을 새 Commit의 개별 결과라고 쓰지 않는다.
- 최신 추적 파일 기준 Production Java는 BackendApplication.java와 domain/shift/event/SchedulePublishedEvent.java 두 개다. 전자는 Spring 진입점, 후자는 근무표 공개 Event다. Event 담당자가 후자를 전환한다. 진입점은 전체 Java 제거 목표에 포함할지 팀이 결정한다.
- Test Java는 18개다. Global·standard·애플리케이션 관련 17개에는 TestClock, TestTimeConfig, AuditingTestEntity 같은 보조 파일이 포함된다. 나머지 한 파일은 ShiftModifyIntegrationTest.java다. PR #71이 ShiftServiceTest.java를 Kotlin으로 전환했다. Global 테스트 담당자와 V3 담당자가 중복 없이 범위를 나눈다.
- PR #72는 남은 ShiftModifyIntegrationTest 6건의 Java→Kotlin 전환으로 OPEN·Ready다. 조회한 Head는 62239a15ab51e7fec580460b988ddfeae1d14e82이고 Backend·Docker Check는 SUCCESS다. 리뷰 필요로 Merge State BLOCKED이며 develop의 18개 Java 집계에 아직 반영되지 않았다. PR 본문에 로컬 검증은 적혀 있으나 Issue·Task Card 연결과 확인 체크박스가 비어 있어 담당자에게 정리를 요청한다.
- PR #73은 SchedulePublishedEvent Java record→Kotlin 일반 class 전환으로 OPEN·Ready다. 조회한 Head는 5859cb11568128557dc98d788329a672e91b3cbb이며 Backend·Docker Check SUCCESS, 리뷰 필요로 Merge State BLOCKED다. 실제 병합 전 생성자·record 접근자·값 동등성·List.copyOf 방어 복사·발행자/Listener JVM 호출 계약을 확인한다. 이 PR도 develop의 Production Java 2개 집계에는 아직 반영되지 않았다.
- Notification Production Kotlin은 15개, Test Kotlin은 9개다. 이전 로컬 V5 최종 감사 RESULT.md의 Production 16개 표기는 오기이며 정정 대상으로 기록한다. #53은 새 회귀 테스트 17건이 아니라 변경 경로 17개와 신규 테스트 1건이었다.
- Issue #62 AUTH-1과 #69 H2 Membership는 OPEN이고 체크리스트가 남아 있다. 사용자는 담당자가 작업 중이라고 전달했지만 GitHub assignee는 두 Issue 모두 비어 있고, 구현 PR·최종 테스트 완료는 확인되지 않았다. #69는 초대코드 재발급·기존 코드 무효화도 범위에 포함한다. 상위 #32 Notification과 #63 Schedule Controller Test도 OPEN이다. #63은 대응 Kotlin 파일 존재와 이슈 범위가 일치하는지 확인한다. Migration Matrix §2의 현재 진행 행은 실제 병합 상태와 대조해야 한다.
- Frontend 헤더 로그아웃은 현재 clearToken만 호출하고 서버 로그아웃 API 호출이 없다. 대타 일부 목록은 workplaceId로 화면 필터링한다. 재가입 뒤 과거 소속과 현재 소속의 분리 근거가 되지 않는다.
- Checkstyle은 Java 검사다. ktlint/detekt는 현재 Gradle Gate에 없다. 현재 CI 성공을 Kotlin 린트 PASS로 표현하지 않는다. 현재 develop의 main Kotlin 소스에서 `fun <Receiver>.<name>` 형식의 확장 함수 선언은 정적 탐색으로 찾지 못했다. 공식 과제의 함수·확장 함수 적용 근거는 최종 제출 전에 실제 코드로 다시 확인해야 한다.

이 스냅샷은 작업 착수 시 최신 GitHub와 다시 대조한다. 사용자 제공 공식 과제의 추가 기능은 선택 사항이지만, 팀이 이번 출시 범위에 넣기로 한 기능은 아래 P0 완료 조건으로 판단한다.

## 3. 먼저 팀이 고정할 출시 범위

### 결정 회의의 산출물

**개발 목표는 A, B, B', C, D, E, F 전부 TARGET이다.** 이것은 사용자의 이번 목표 선택이지 구현·검증·출시 승인 기록이 아니다. 팀은 각 묶음의 Backend, Frontend, MySQL/배포, 테스트, 리뷰 담당자와 Task Card·PR을 확정한다. D-NOW-01·05의 담당·범위 공백을 이 기록으로 메운다. 미정인 정책·API 필드는 구현자가 임의로 정하지 않고 Decision Log에 질문과 결정 근거를 남긴다.

이 계획의 진행 칸은 TARGET(목표), IN_PROGRESS(담당자·작업 근거 있음), VERIFYING(구현 후 정확한 Head에서 검증 중), GO(필수 근거 통과), HOLD(차단 조건 남음), EXCLUDED(팀이 이번 제공에서 안전하게 제외하기로 확정)로 쓴다. 이 칸은 docs/STATUS-AXES.md의 5개 근거·정책·문서·Test 축을 대체하지 않는다. 목표 범위와 최종 제공 범위는 별도 칸이다. 현재 A·C·D는 사용자가 담당자의 진행을 전달했지만 Issue #62·#69만으로 구현 완료를 판정하지 않는다. B·B'·E·F의 작업자/PR도 확인 전이므로 TARGET으로 둔다.

- A: AUTH-1 서버 로그아웃. 독립적으로 완료 가능.
- B: 공개 근무 취소·인계, 활성 대타 종료, 이력, 알림·결과 조회. C의 선행.
- B': 대타 승인 권한 선검사, Lock 순서와 재검증. 실험·정책 결정이 필요한 부분은 분리해 판정.
- C: 관리자 퇴사 미리보기·확정, DRAFT·공식 근무·대타 정리와 단일 Transaction. B에 의존.
- D: 종료 소속을 되살리지 않는 재가입, 현재 소속 유일성, 운영 DDL·Rollback, 과거/현재 조회 분리. C에 의존.
- E: 예정/빠진 근무, 종료 대타, 소속 이력 등 해당 기능의 결과 화면.
- F: STALE·확인 필요·실패 뒤 최신 상태 조회, 유효한 선택 복구와 재시도 안내.

공식 과제의 필수 Kotlin 전환·테스트·문서·발표와 팀이 목표로 한 P0 개선을 구분한다. 일정상 묶음을 완성할 수 없으면 팀이 **독립적으로 제외 가능한 묶음인지 먼저 판정**한다. B/B'에 기존 대타·근무 정합성 결함이 있으면 C/D만 빼거나 UI만 숨겨 안전해지는 것은 아니다. 제외 시 기존 P0의 안전성, 선행 기능, 서버 API·Frontend 노출, 이미 병합된 Schema·데이터, 사용자 제약과 시연 문구를 확인한다. E/F는 포함된 업무 묶음의 결과 확인·복구 경로이므로 이를 빼고 해당 묶음을 GO로 선언하지 않는다. 미완성 기능을 완료로 표시하거나 부분 DB 변경이 가능한 상태로 배포하지 않는다. Outbox/Retry·모니터링·광범위한 부하 테스트·Refresh Rotation은 이번 완료 조건 밖에 둔다.

**팀이 지금 답해야 할 선택:** (1) A~F 전부를 목표로 한 담당자·Task Card·PR 경계와 10/29 전 가능한 통합 순서, V4 담당자의 Frontend·배포 검증 겸임 수용 여부 및 백업 담당, (2) Event PR #73·Global·Shift PR #72·BackendApplication의 전환 또는 의도적 잔존, (3) 퇴사 미리보기 비교 정보·Lock 순서·활성 대타 중복 데이터 처리, (4) 재가입 Unique 구현·운영 DDL 및 되돌리기 담당, (5) Frontend와 격리 배포 환경의 Owner, (6) 이번 제출에 Kotlin 린트 Gate를 새로 넣을지, (7) GO에 못 미친 기능을 실제로 독립 제외할 수 있는지다. Decision Log §4가 승인한 방향과 아직 기술 결정을 기다리는 세부사항을 분리한다. 답이 없는 선택을 AI가 채우지 않는다.

**종료 기준:** A~F의 TARGET, 담당자, 선행조건, Task Card·PR 경계와 미정 결정이 한 화면에서 확인된다. 최종 제공 범위는 아래 판정 카드의 근거로 10/29 제출 전 확정한다.

**기능별 판정 카드(한 묶음당 한 장):** Feature ID·기준 SHA·목표 상태 → 담당자(Backend/Frontend/DB/테스트/리뷰) → 선행 관계·승인 계약 → 구현 PR/현재 Head → 관련 Test·JUnit XML·CI → 최신 develop Tree → MySQL/SQL/경합 → 화면·격리 배포 → 남은 위험 → GO/HOLD/EXCLUDED 및 판정자·시각. EXCLUDED면 기존 P0 영향, 서버/화면 노출, DB 변경·Rollback, 사용자에게 알릴 제약을 추가한다. 빈 칸은 PASS가 아니라 UNKNOWN이다.

## 4. 실행 순서

### 도메인별 인계 책임

이 목록은 기존 Matrix의 V1~V5 담당 구분을 사용한다. 아래 작업을 새 담당자로 자동 배정하지 않는다. 각 Owner가 Task Card와 실제 PR에 자신의 범위를 적고, 통합 담당자는 합성 결과를 확인한다.

- **V1 Auth/Security 담당:** Issue #62의 서버 로그아웃과 Refresh 조건부 무효화, 실 Filter Chain, Cookie·Origin·재발급 거부 테스트를 맡는다. Frontend 담당과 로그아웃 클릭·실패 안내를 대조한다. 무토큰/잘못된 Token/다른 로그인 영향/반복 로그아웃을 사례별로 남긴다. 산출물은 API·Filter·DB 근거, V1-N 사례 XML, 격리 환경의 쿠키/CORS 확인이다.
- **V2 Workplace/Member/Pattern 담당:** Issue #69의 관리자 권한, 퇴사 미리보기·확정, 새 Membership 재가입·현재 소속 유일성, 초대코드 재발급·기존 코드 무효화의 실제 Task 경계를 확인한다. 기존 WIP/Stash는 테스트·빌드 완료로 취급하지 않는다. V3/V4 담당과 미래 근무·대타 정리 인터페이스를 확정한다. MySQL Unique, 기존 Row 보존, 권한 거부·STALE·Rollback·재가입 격리 테스트를 제출한다. 운영 DDL·Rollback은 배포 담당과 공동 검토한다.
- **V3 Schedule/Shift/Unavailable 담당:** 별도 Event 전환 담당의 PR #73과 SchedulePublishedEvent 발행 계약을 대조하고, PR #72 ShiftModifyIntegrationTest 리뷰·병합, 공개 근무 취소·인계 기반 B, DRAFT 정리, 기존 Shift Lock·공식 근무 충돌을 맡는다. V4에 Shift 변경 결과·발행 Event·Transaction 경계와 Lock 선행 순서를 인계한다. 실제 MySQL 별도 세션의 공개/변경/퇴사 경합과 최종 Shift·이력·이벤트 Row를 확인한다. UnavailableTime의 승인된 충돌 정책만 적용하고 새 정책을 임의로 만들지 않는다.
- **V4 Substitute 담당:** 활성 요청 SHIFT_CHANGED 종료, 후보 자격 상실, 승인 권한 선검사와 Lock 뒤 재검증, 종료된 대타 결과 조회를 맡는다. V3의 Shift 변경 계약과 맞춰 Request·Candidate·Shift를 같은 결과로 확인한다. 같은 요청 중복, 다른 근무지, 퇴사 후 후보, 한 근무에 활성 요청 여러 건의 미결정 처리, 실패 뒤 정합성을 테스트한다. V3와 Lock 순서를 하나의 실험 근거로 연결한다. 이 담당자가 Frontend 연동·격리 배포 Smoke 조정까지 맡는 것은 **제안**이며, 본인의 수용·시간·배포 권한·백업 담당 확인 전에는 확정 배정으로 쓰지 않는다.
- **V5 Notification 담당(요청자가 마무리 검증, Event·업무 Owner와 공동):** 기존 15개 Kotlin Production·9개 Kotlin 테스트의 Migration 근거와 Issue #32를 정리한다. B~D 계약이 확정되면 새 근무 변경·퇴사·재가입 Event의 수신자·현재 소속·AFTER_COMMIT·REQUIRES_NEW·업무 실패/알림 실패 격리·대체 조회를 검증한다. Event 발행과 업무 데이터의 원자성은 발행 Domain 담당과 함께 확인한다. 승인되지 않은 403/404 변경이나 새 알림 Type은 Migration PR에 섞지 않는다.
- **Global·통합 담당(요청자):** Global·standard·애플리케이션 Java 17개 경로를 실제 테스트와 Fixture로 나눠 전환 범위를 확정하고, 공통 응답·예외·시간·감사·OpenAPI 검증력을 보존한다. 계약을 바꾸지 않는 Kotlin 관용 표현 개선 후보를 직접 선별해 작은 별도 Task/PR로 검증한다. 각 도메인 PR의 현재 Head·CI와 최종 develop의 사례 목록, Kotlin 경고, 문서·Issue 상태, 사용자 경로·격리 배포 검증을 취합한다. 다른 Owner의 P0 구현 완료를 대신 선언하지 않는다.
- **Frontend 담당(팀 지정 대기; V4 담당 겸임 가능성 확인):** V1·V2·V3·V4의 확정 API를 받아 로그아웃, 퇴사 미리보기/충돌 확인, 과거/현재 소속 조회, 예정·빠진 근무·종료 대타, 409 뒤 재조회와 유효한 선택 복구를 실제 화면에서 확인한다. 화면에서 workplaceId만 거르는 동작은 재가입 격리의 서버 검증으로 대체·보완한다. 백엔드 Owner는 자기 응답·ErrorCode·재조회 API 계약을 인계하고 화면 구현 완료로 대신 판정하지 않는다.
- **배포·DB 담당(팀 지정 대기; V4 담당의 Smoke 조정 겸임 가능성 확인):** 격리 환경의 이미지 기동과 Healthcheck, MySQL 버전·Schema/Unique/Enum/SQL/Lock, 운영 적용 전 DDL·Rollback, Cookie/CORS/Secret 설정을 확인한다. Smoke 실행 조정과 운영 DDL 검토·적용 권한은 별개의 책임이다. V4 담당이 겸임하더라도 V2·V3·V4 Owner가 자기 SQL·Transaction·Lock 증거와 데이터 영향을 제출하고, DB 변경 담당과 복구 담당을 별도로 확인한다. 운영 환경의 쓰기·부하·동시성 검증은 별도 승인과 절차를 따른다.

**권장 책임 분리:** 기능 전체를 한 사람에게 몰지 않고 **사용자 Command마다 한 명의 Transaction·PR 주관 Owner**를 정한다. A 로그아웃 Command는 V1, B 공개 근무 취소·인계 Command는 V3, B' 대타 승인 Command는 V4, C 퇴사 확정 Command와 D 재가입 Command는 V2가 주관하는 안이다. 이것은 기존 Service 진입점과 정책 카드의 역할을 근거로 한 제안이며 팀 승인 전 확정 배정이 아니다. V3의 B Command 안에서는 V4가 제공하는 활성 대타 종료 계약을 같은 업무 Transaction으로 실행하고, V2의 C Command 안에서는 V3·V4의 근무·대타 정리 계약을 연결한다. V3·V4·V2는 Lock 순서·상태 재검증·Event 발행 시점과 실패 시 전체 Rollback을 공동 리뷰한다. B의 인터페이스를 합의하면 C/D는 병렬 착수할 수 있지만 B 검증 전 C/D GO는 불가하다.

E/F는 별도 기능 한 명에게 몰기보다 해당 Backend Command Owner가 결과 조회·오류 계약을 제공하고 Frontend Owner가 화면·복구를 연결한다. 요청자는 최종 사용자 경로와 develop 통합의 검증 책임을 맡는다. V4 담당자의 Frontend·배포 조정 겸임은 팀이 동의하고 일정·백업을 확보한 경우에만 적용한다. 겸임하더라도 V1/V2/V3가 자기 API·SQL·DB 결과의 검증 책임을 V4에 넘기지 않는다.

**PR 경계:** V4의 요청 종료 연산이 독립적으로 안전하게 구현·검증될 수 있으면 V4의 작은 선행 PR을 먼저 병합하고 V3가 B Command PR을 올린다. 독립 병합이 부분 동작을 노출하거나 원자성을 깨면 B는 V3가 주관하는 하나의 교차 도메인 PR로 묶고 V4를 필수 공동 리뷰어/기여자로 둔다. C에서도 V2가 Transaction PR을 주관하고 V3·V4는 호출 계약과 상태·Lock 검증을 필수 리뷰한다. B/B'의 Lock 순서·새 ErrorCode·Event Type은 Proposal만으로 구현하지 않고 Decision Log의 승인 범위를 확인한다. 이 경계는 실제 Diff·선행 PR 상태를 본 뒤 팀이 정한다.

각 Owner의 인계 형식은 **기준 SHA·정확한 변경 파일 → 승인 계약/미정 항목 → 실행 테스트와 XML/SQL → CI → 다른 도메인에 넘길 API·Event·DB 영향 → 미검증 한계**다. 통합 담당은 이 인계가 모두 모인 뒤 최종 Tree에서 다시 검증한다. 특히 V3↔V4↔V2는 Shift ID·현재 소속·Event Type·Transaction 소유자·Lock 취득 순서·실패 시 Row 불변·대체 조회 API를 Task Card에서 맞춘다. 서로의 Branch나 Stash를 직접 수정해 통합하지 않는다.

### 요청자 실행 큐

1. **지금:** Global·standard·애플리케이션 Java 17개를 실제 테스트와 3개 보조 파일로 구분하고, 각 Java 테스트의 사례·Assertion·Mock·Spring 설정을 기록한다. 공통 응답/예외, 시간·감사/설정, 앱·표준 테스트로 리뷰 가능한 작은 PR 경계를 정한다.
2. **Migration 병합마다:** PR #72·#73의 Head·Diff·사례·CI를 수집하고 최신 develop의 Java 잔존·JUnit XML 기준선을 새로 만든다. 다른 팀원의 Worktree·WIP는 건드리지 않는다.
3. **P0 구현 중:** V1/V2/V3/V4의 API·Event·DB 계약과 Test ID를 인계받고, B/B'의 기존 P0 안전성부터 합성 후보에서 확인한다. 실패 사례는 기능 Owner에게 재현 조건과 Tree를 돌려준다.
4. **기준선 안정 후:** 실제 가치가 있는 Kotlin 관용 표현 1~2건을 선별해 독립 PR로 전후 비교·관련 테스트를 남긴다. 전체 스캔으로 마감 검증을 밀지 않는다.
5. **B~D 계약 확정 후:** V5의 기존 테스트를 먼저 재사용하고 신규 알림 Type·수신자·소속 격리·대체 화면만 보강한다. 마지막으로 격리 환경의 Backend/Frontend 경로를 배포 담당과 함께 검증한다.

### 단계 M — 남은 Migration과 기준선

1. Event 담당자는 PR #73의 SchedulePublishedEvent.java record와 Kotlin class의 필드·생성·발행 위치·Listener 수신·record 접근자·값 동등성·방어 복사·JVM 타입 변경 영향을 대조한다. Backend·Docker CI 성공과 리뷰 대기 상태를 별도로 기록하고, 승인·병합 후 현재 develop과 다시 확인한다. Notification Listener 7개는 이미 Kotlin이다.
2. Global 담당자는 Java Test 17개를 실제 테스트 클래스와 보조 Fixture로 나눈다. ApiResponse/예외/날짜/감사/설정의 Java→Kotlin 사례·Assertion·Spring 설정 동등성을 보존한다. Security 테스트는 이미 Kotlin인 파일을 중복 전환하지 않는다.
3. V3 담당자는 PR #72의 ShiftModifyIntegrationTest.java→.kt 6건을 기존 Fixture·Transaction·Latch·Assertion과 대조한다. 현재 Head의 CI 성공과 실제 JUnit XML·최신 develop 통합은 구분한다. PR 본문의 Issue·Task Card 연결과 미완료 확인 체크박스를 정리하고 리뷰·병합 후 남은 Java 수를 다시 센다. 이미 병합된 PR #71의 ShiftServiceTest.kt 근거도 연결한다. BackendApplication.java는 범위 결정을 기록한다.
4. 각 변경은 별도 PR에서 관련 테스트, 전체 clean check, JUnit XML 사례 키, Main/Test Checkstyle, 현재 Head CI를 확인한다. 마지막 병합 이후 새 develop에서 다시 통합 검증한다.
5. Migration Matrix 현재 진행 행과 #32·#63 등 Issue 상태를 실제 PR·Evidence에 맞춘다. 과거 Snapshot 수치를 최신 수치로 덮어쓰지 않는다.

**종료 기준:** 전환 대상 누락 0 또는 이유가 적힌 의도적 잔존, 기존 사례·중요 Assertion 소실 0, 최종 develop 개별 결과와 CI 연결, 문서/Issue 현재 상태 일치. 기준이 맞지 않으면 M은 HOLD다.

### 단계 K — 요청자가 맡는 Kotlin 표현·품질 개선

요청자는 Migration 기준선이 잡힌 뒤 실제 사용처를 골라 Kotlin nullability, 읽기 쉬운 표현식·컬렉션 처리, 불변 val, 중복 보일러플레이트 감소, 필요한 함수·확장 함수의 전후 차이를 검토한다. 현재 main Kotlin 소스 정적 탐색에서는 확장 함수 선언이 확인되지 않았으므로 공식 과제의 필수 적용 여부를 점검한다. 적절한 사용처를 정해 계약 보존 테스트와 함께 작은 별도 PR로 구현하거나, 찾지 못했다면 공식 요구사항 미충족으로 명확히 기록하고 팀과 범위를 정한다. 공식 과제의 함수·확장 함수 적용 근거는 실제 생산 코드의 가독성과 동작 보존 테스트로 제시한다. 의미 없는 확장 함수, 전역 대량 변환, 정책·ErrorCode·Transaction·Lock 변경은 하지 않는다. 특히 requireId() 예외와 Java Stream.toList()→Kotlin map()의 결과 특성처럼 이미 검토한 차이는 다시 범위를 판정한다. 변경은 Global Test 전환이나 A~F Improvement와 섞지 않고 별도 작은 Task/PR로 한다.

**우선순위:** M의 누락·현재 P0 정합성·통합 검증이 먼저다. 시간이 부족하면 관용 표현 후보를 문서화하되 새 리팩토링 PR 때문에 필수 검증을 지연하지 않는다. ktlint/detekt가 없으므로 수동 리뷰와 컴파일·관련/전체 테스트를 근거로 삼는다. 신규 린트 Plugin·Gate는 공통 설정 Owner가 승인한 별도 환경 Task로만 추가한다.

### 단계 A — 서버 로그아웃

Issue #62와 AUTH-1 카드 범위에서 Refresh Token의 로그인별 jti, 조건부 DB 무효화, Cookie 삭제, Origin/Referer 거부, 공개 Security 경로를 구현·검증한다. 쿠키 누락/이미 무효 요청의 멱등 성공, 로그아웃 후 같은 Refresh로 재발급 거부, 다른 로그인 토큰 불변, 실패 시 DB 무변경을 각각 확인한다. Frontend는 서버 API 호출과 실패 안내·재시도까지 연결한다. Access Token 즉시 무효화·다중 기기 전체 세션·Rotation은 이 범위에 포함하지 않는다.

**종료 기준:** V1-N-01~12 등 확정된 사례와 기존 Auth 회귀가 통과하고, 현재 Frontend에서 재발급 차단을 사용자가 확인할 수 있다. 배포 담당자는 실제 허용 Origin, Cookie SameSite/Secure, 컬럼 길이와 비밀키 길이를 격리 환경에서 확인한다.

### 단계 B/B' — Shift·Substitute 정합성 기반

퇴사 인계 전에 공개 근무 변경의 상태·배정 이력·활성 대타 종료·알림·대체 조회를 묶어 확인한다. **B의 새 공개 근무 Command는 V3 주관 Transaction, V4의 요청 종료 규칙은 그 안에서 호출되는 계약**으로 제안한다. **B'의 기존 대타 승인 Command는 V4 주관 Transaction**이다. 현재 코드에서 ShiftService.modify는 DRAFT 경로이고, SubstituteRequestService.approve는 Request를 먼저 잠근 뒤 관리자 권한을 확인한다. 따라서 공개 후 Command의 구현 완료나 승인 Lock 순서 수정 완료를 현재 코드 사실로 쓰지 않는다. ML-5의 Shift 우선 Lock·Lock 후 재검증은 아직 기술 실험과 팀 결정이 필요한 후보이며, 각 Command Owner가 V3/V4와 공동 검증한다. 기존 대타 요청이 두 개 이상인 비정상 Row, 남의 알림 403/404처럼 미정인 정책은 실험 결과로 임의 결정하지 않는다.

**종료 기준:** 선택한 API·화면에서 성공/권한 거부/중복·경합/실패 Rollback/재조회가 같은 결과를 보이고 MySQL 실험이 Lock 순서와 최종 Row를 확인한다. B가 끝나지 않으면 C 완료 선언도 보류한다.

### 단계 C/D — 퇴사·재가입

Issue #69는 Backend 한 API의 완료가 아니다. C에서는 현재 관리자와 재직 EMPLOYEE만 대상으로 하고, 진행 중 근무는 거부한다. 미리보기 영향과 확정 시 최신 영향·선택을 비교한다. STALE/CONFIRM_REQUIRED/권한 실패에서는 이번 요청의 업무 변경 0을 확인한다. 성공하면 소속·미래 공식 근무·DRAFT·대타·이력을 단일 업무 Transaction으로 확정한다.

D에서는 기존 소속 Row를 보존하고 새 Row를 만들며, 근무지·사용자당 현재 소속 최대 1개를 DB 제약으로 검증한다. 운영 DDL·기존 데이터 점검·Rollback과 구버전 호환까지 배포 담당이 검토한다. Frontend의 workplaceId만으로 거르던 조회는 현재 소속과 과거 이력 구분에 맞게 연동한다.

**종료 기준:** C와 D의 DB 결과·권한·단일 Transaction·재가입 유일성이 증명되고, 아래 E/F 화면·복구와 결합된다. 필수 화면이나 운영 Unique 이행이 빠진 재가입은 출시 준비 완료가 아니다.

### 단계 E/F — 각 Command의 결과 화면과 실패 복구

V1/V2/V3/V4 Backend Owner는 자기 Command의 성공·권한 거부·STALE/CONFIRM_REQUIRED·네트워크 응답 유실 뒤 확인할 조회 API·ErrorCode를 Frontend Owner에게 인계한다. Frontend는 예정/빠진 근무, 종료된 대타, 소속 이력과 로그아웃 결과를 실제 화면에서 확인한다. 네트워크 응답 유실, 409 STALE, 확인 필요, 권한 상실 뒤에는 최신 상태를 재조회하고 유효한 선택만 복구한다. 같은 변경 요청을 무조건 자동 재전송하지 않는다.

**종료 기준:** 포함한 각 Command마다 성공 결과와 실패 후 화면 행동을 서버 재조회·DB 최종 상태와 연결한다. E/F의 한 경로가 빠지면 해당 A/B/C/D 기능은 GO가 아니다. Frontend 담당자가 V4를 겸임한다면 Backend Owner의 계약 인계와 요청자의 교차 검증을 유지한다.

### 단계 N — Notification의 새 사용자 흐름 연결

B~D의 실제 구현 계약이 확정된 뒤 요청자는 Event·V3·V4 담당과 새 Event/Type/수신자 범위를 대조한다. 업무 Rollback 때 알림 0, 업무 Commit 뒤 알림 실패 때 업무 결과 유지, 재가입 전후 소속 격리, 알림이 없어도 결과를 확인할 대체 화면을 검증한다. 이미 완료된 V5 Java→Kotlin Migration은 새 정책 PR로 다시 쓰지 않는다.

기존 SubstituteNotificationAfterCommitIntegrationTest는 현재 대타 Event의 Commit·Rollback·알림 저장 실패를, NotificationHttpContractTest는 조회·읽음의 HTTP 계약을 이미 다룬다. 먼저 각 테스트의 실제 Assertion과 현재 Head 실행을 확인하고 같은 사례를 중복 작성하지 않는다. 새 B~D에서 생긴 Type·수신자·퇴사/재가입 격리·알림 없는 대체 화면은 TEST-INVENTORY의 V5-N-01~20 및 V2/V3/V4 경로 ID와 대응해 **부족한 사례만** 추가한다. 특히 실행 관리자의 수신 제외와 요청자로서의 수신, 다른 관리자의 종료 내역 조회(API-SB-02), 알림 실패 뒤 업무 Commit 유지, Rollback 때 알림 0, MySQL 현재 소속 Query를 확인한다. 기존 NOT-002의 403/404 정책은 G03-D-10·CHG-07 결정 전에는 변경하지 않는다.

**종료 기준:** 실제 MySQL 재조회와 HTTP/화면 경로에서 업무 결과와 알림 안내가 모순되지 않는다. 새 정책·Type·ErrorCode가 미정이면 그 부분은 HOLD다. 알림이 유실돼도 결과를 확인하는 조회 경로가 없으면 해당 사용자 흐름도 HOLD다.

## 5. 검증 사다리: 각각 다른 근거를 남긴다

Migration M과 A~F 구현은 도메인별로 병렬 진행할 수 있다. 각 PR의 Task Card와 테스트는 독립적으로 확인하고, 최종 통합은 선행 관계와 병합된 develop 순서대로 반복한다. K와 N은 해당 코드·정책의 기준선이 잡힌 뒤 진행한다.

1. **정적·단위:** Java/Kotlin 호출, nullable, Validation 위치, Entity/Query/Lock/Event annotation, 기존 테스트 Assertion을 대조한다. 공식 과제의 Kotlin 함수·확장 함수 적용과 코드 개선 효과는 실제 사용처, 전후 코드, 측정 방법으로 설명한다. 의미 없는 확장 함수를 숫자 맞추기로 추가하지 않는다. Kotlin 린트 도구를 채택하면 별도 승인된 작은 PR의 Gate로 추가한다.
2. **관련 통합:** Spring Bean, 실제 Security Filter, Service·Repository, MySQL Testcontainers, flush/clear 또는 새 Transaction의 DB 재조회로 API·ErrorCode·권한·Rollback을 확인한다. Mock 경계는 실제 검증 범위라고 주장하지 않는다.
3. **MySQL SQL·경합:** 최소 두 독립 DB 세션/Transaction으로 순서를 통제한다. 실행한 SELECT/UPDATE와 FOR UPDATE, 현재 격리수준, 첫 요청의 Lock 보유, 두 번째 요청의 대기, timeout/Deadlock의 예외·Rollback, 최종 Row와 이력·알림을 기록한다. CountDownLatch/Future의 worker 예외와 timeout도 회수한다. 가능하면 performance_schema 관찰을 보조 근거로 쓴다. 우연한 PASS나 NOT_REPRODUCED를 결함 부재로 바꾸지 않는다.
4. **최종 통합:** 포함한 PR을 최신 develop과 결합한 Tree에서 clean check, JUnit XML 사례 누락/추가/Skip, Checkstyle, Backend/Docker CI를 연결한다. 이전 488건은 f19e996…의 역사적 값이며 영구 합격 숫자가 아니다.
5. **격리 배포:** 이미지 빌드 뒤 실제 컨테이너 시작, Healthcheck, 승인된 테스트 MySQL Schema, 환경 설정, Cookie/CORS, 생성한 OpenAPI/Swagger 또는 REST Docs, Frontend 연결을 확인한다. 대표 사용자 Flow와 실패 후 재조회 Smoke를 수행한다. 기동 결과·응답·배포 Commit/Image Digest·DB 버전·Rollback 방법을 남긴다. 운영 DB/Railway/prod Profile에서의 실험은 이 문서가 허용하지 않는다.

각 단계 Evidence는 **Commit SHA와 Tree, OS/JDK/DB/격리수준/Profile, 정확한 명령, 실행 N·PASS·FAIL·ERROR·SKIP, XML/SQL/CI 링크, 검증되지 않은 부분**을 포함한다. 로그에는 Secret·Cookie·Token 값과 개인정보를 남기지 않는다.

## 6. 제출 전 사용자 경로

격리 환경에서 관리자와 직원 계정을 이용해 다음을 실제 순서대로 수행한다. 각 요청 뒤 HTTP 응답과 별도 조회의 최종 DB/화면 상태를 기록한다.

1. 회원가입·로그인 → 근무지 생성·직원 참여 → 권한 없는 사용자와 다른 근무지 접근 거부.
2. 근무표 초안·공개 → 직원의 예정 근무 확인 → 불가능 시간과 확정 근무 충돌 처리.
3. 대타 요청 → 후보 수락·거절 → 관리자 승인 → Request·Candidate·Shift 담당자·이력·알림/대체 조회 일치.
4. 실패 분기: 중복 승인, 권한 상실, Rollback, 동시 변경, 알림 저장 실패, 응답 유실 후 재조회.
5. A가 포함됐다면 로그아웃 → 같은 Refresh로 재발급 거부 → 다른 로그인 영향 없음.
6. B/C/D가 모두 포함됐다면 퇴사 미리보기·확정 → 미래 근무/대타 정리 → 재가입 새 소속 → 과거/현재 이력 격리 → 화면 복구.

해당 기능이 출시 범위 밖이면 그 단계는 SKIPPED가 아니라 **NOT_IN_SCOPE**로 기록하고 이유와 사용자에게 남는 제약을 발표·시연 문서에 적는다.

## 7. 오늘부터 제출까지의 체크포인트

- **10/28 오후 초반:** A~F 전체 TARGET을 공유하고 담당·선행조건·미정 결정·배포 담당을 확정한다. #62·#69의 실제 WIP·PR·테스트 상태와 #72의 리뷰 상태를 확인한다. 계획만 있는 일을 구현 완료로 옮기지 않는다.
- **10/28 오후~저녁:** Event/Global/Shift Migration과 A~F 담당별 개발을 병렬 진행한다. B/B'의 기존 P0 정합성과 MySQL 실험을 우선 확인하고 C/D 통합 가능성을 판정한다. 요청자는 Global 전환과 최종 통합 준비를 맡는다.
- **10/28 밤:** 포함한 B→C→D의 코드·테스트·Frontend·문서가 모두 준비된 경우에만 통합 후보를 검증한다. 차단 조건이 남으면 미완성 묶음을 이번 제공 범위에서 제외하고 UI·설명·Issue에 일치시킨다.
- **10/29 오전:** 코드 범위를 고정하고 최종 develop/격리 배포 Smoke, SQL·Lock 근거, API/ERD, 기획서·WBS·PPT·시연 리허설을 확인한다. 팀 계획의 12:00 제출 전에 결과 링크를 한 번 대조한다.

고정 시각은 팀 일정에 맞춰 조정할 수 있지만, 게이트를 생략해 완료 표현만 앞당기지 않는다.

## 8. GO / HOLD / 범위 제외 판정

**GO**는 선택한 기능 묶음 모두에 대해 승인된 계약, 리뷰·병합, 개별 테스트/CI, MySQL 정합성, Frontend 결과 확인, 격리 배포 기동·Smoke와 제출 링크가 연결된 경우다.

**HOLD**는 정책 미결정, 테스트 실패·누락, 권한 우회, 부분 DB 반영, 필수 Lock/DDL 미검증, 배포 기동 실패 중 하나라도 있는 경우다. 원인과 담당·재검증 조건을 적는다.

**EXCLUDED(범위 제외)**는 팀이 독립 묶음 전체를 이번 제출·시연에서 안전하게 빼기로 결정하고, 기존 P0 영향·선행 기능·서버 API/화면 노출·이미 적용된 DB 변경·사용자 제약·후속 Issue를 일치시킨 경우다. B/B'의 기존 대타 경로에 결함이 남는다면 C/D 제외만으로 서비스 GO가 되지 않는다. E/F의 결과·복구 경로는 포함된 Command의 완료 조건에 붙인다. 제출 자체와 서비스 출시 준비는 별도 판정한다. 일부만 완성한 퇴사·재가입을 GO로 표시하지 않는다.

## 9. 문서·발표와 마지막 인계

- Migration Matrix의 현재 진행 행, TEST-INVENTORY·TEST-EVIDENCE, Issue #32·#63과 각 P0 Issue를 실제 결과에 맞춘다. 과거 Snapshot은 작성 기준을 유지한다.
- API 경로·Validation·ErrorCode·JSON, OpenAPI/Swagger 또는 REST Docs 산출물, ERD·운영 DDL을 최종 구현과 비교한다. 생성 성공만으로 운영 응답까지 같다고 주장하지 않는다.
- 공식 과제 제출물인 코드 Branch 링크, 기획서, WBS, PPT와 팀 계획의 시스템 구성도, 시연 Flow를 확인한다. Notion·발표 링크는 이 문서 작성 시 직접 확인하지 못했다.
- 발표에는 같은 기능의 Java/Kotlin 전후 코드, 보일러플레이트/라인 수의 측정 방법, null/JPA/Transaction 트러블슈팅, 개별 테스트·MySQL·배포 증거, 남은 한계를 함께 싣는다. 줄 수 감소만으로 생산성 향상을 단정하지 않는다.
- 이 설명 문서는 **방법**을 담고, 현재 날짜의 상태는 Issue·Matrix·Evidence에 둔다. 별도 초안인 “SWITCH — Kotlin Migration부터 서비스 완성까지”의 Notification 장은 2026-10-27 당시 기록임을 표시하고 최신 V5 감사와 연결한다.

## 10. 이 문서를 PR로 공유하는 방법

문서 전용 Draft PR에서 이 계획 문서와 docs/README.md의 새 문서 Index 행만 변경한다. 리뷰어에게 (1) A~F 전체 TARGET과 최종 제공 범위 판정 방식, (2) 도메인/Frontend/배포 담당과 선행조건, (3) 근거가 부족하거나 정책 미정인 완료 기준, (4) 실제 10/29 제출 가능 범위를 확인해 달라고 요청한다. 이 PR은 코드 구현·배포·정책 결정을 승인하는 PR이 아니다. 다른 기능 PR과 병렬로 리뷰할 수 있다.

팀이 합의한 담당·일정은 Delivery Board/WBS에, 새 정책 결정은 Decision Log에, 실제 테스트는 TEST-EVIDENCE에 기록한다. 문서 PR이 병합돼 문서 리뷰 축이 PUBLISHED가 되더라도 정책 승인 축이나 테스트 실행 축이 자동으로 바뀌지 않는다. 문서 병합 후에도 기능별 PR은 각자의 승인·CI·병합 절차를 거친다.

## 참고 원본

- 공식 3차 프로젝트 명세·팀 진행 계획: 사용자가 2026-10-28 대화에서 제공. 추가 기능·성능·모니터링은 공식 필수 항목과 구분.
- docs/README.md, docs/ai/AI-RULES.md, docs/STATUS-AXES.md
- docs/migration/MIGRATION-MATRIX.md, docs/migration/TECH-STACK-DECISIONS.md
- docs/contract/CONTRACT-BASELINE.md, docs/contract/API-CATALOG.md, docs/contract/FRONTEND-CONTRACT.md
- docs/decisions/DECISION-LOG.md §4, docs/decisions/IMPLEMENTATION-PRIORITY.md
- docs/evidence/TEST-INVENTORY.md, docs/evidence/TEST-STRATEGY.md, docs/evidence/EXPERIMENT-PLAN.md
- GitHub Issue #62·#69, PR #70·#71·#72·#73, Notification 최종 로컬 감사 V5-notification-final-audit/RESULT.md

이 문서의 후속 구현은 각 Task Card와 승인된 범위에서 별도 실행한다.
