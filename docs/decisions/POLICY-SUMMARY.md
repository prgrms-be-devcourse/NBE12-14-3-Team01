# SWITCH V3 정책 요약 — 퇴사·재가입·공개 후 변경·로그아웃

> 팀 공유용 요약 (2026-10-23 작성, 2026-10-25 저장소 이관, 원본 로컬 `drafts/D8-SUMMARY/`). 상세는 정책 카드별 Proposal(`proposals/`)에 있다.
> 2026-10-25 S2-6A에서 G03-D-19·21·22·03·05, M3, 알림 Type, ErrorCode, 정식 Test ID 결과를 반영했다 (§2 아래쪽 행, §4, §6). 묶음별 상세 추적은 [`IMPLEMENTATION-PRIORITY`](IMPLEMENTATION-PRIORITY.md) §3-1.
> **이 문서의 정책은 모두 사용자 잠정 결정 / PROPOSED이며 팀 APPROVED가 아니다.** 구현 지시가 아니다 (AI-RULES §3).
> Test·실험은 아직 실행하지 않았다 (SPECIFIED / NOT_RUN).

## 1. 이번 프로젝트 포함 범위

| 포함 | 제외 (후속) |
|---|---|
| 관리자가 직원을 퇴사 처리 (EMPLOYEE만) | 직원 스스로 나가기, 앱 내 퇴사 요청·승인 |
| 같은 근무지 재가입 (새 소속 기록, 유효 초대 코드) | 개인별 재가입 차단·해제, 가입 승인 |
| 초대 코드 재발급 (관리자) | 초대 코드 유효기간, 추측 시도 제한 |
| 공개 근무표의 근무 추가·취소·담당자 변경 | 공개 근무의 시간 변경, 원자적 교체 |
| 퇴사 시 근무 인계 또는 취소, DRAFT 정리, 대타 정리 | 분할 보충, 담당자 없는 빈 근무 |
| 서버 로그아웃 (Refresh 무효화) | Access Token 즉시 무효화, 다중 기기 세션, Rotation |
| 가입·재가입 알림, 근무 배정·취소·인계 알림 | 알림 전달 보장 (재시도·Outbox) |
| 직원 "빠진 근무" 확인, 마이페이지 "내 소속 이력" | 과거 근무·대타 상세 이력, 근로 증명 |
| | 관리자 위임·역할 변경, 근무지 정리, 계정 삭제 |

범위 결정 G03-D-11: 사용자가 "퇴사 + 재가입 포함"으로 결정. **팀 공식 기록(결정자·일자·근거)은 아직 없음** → 팀 확인 요청 (§7).

## 2. 핵심 정책 (사용자 잠정 결정)

| 영역 | 정책 | 카드 |
|---|---|---|
| 소속 이력 | 퇴사는 Row를 지우지 않고 `leftAt` 기록. 재활성화 금지. 재가입은 새 Row | ML-H1, ML-H2 |
| 현재 소속 | 근무지·사용자당 현재 소속(`leftAt IS NULL`) 최대 1개를 **DB 제약**으로 보장. 동시 가입은 409 | ML-H2 |
| 퇴사 실행 | 현재 관리자만, 대상은 현재 EMPLOYEE만. 진행 중 근무가 있으면 거부. 미리보기 → 확정, 확정 시 최신 상태 재검사 후 다르면 409 | ML-4 |
| 공개 근무 | 퇴사자의 미래 공식 근무는 근무마다 **인계 또는 취소**를 관리자가 선택 (미선택 시 확정 불가) | ML-2 |
| DRAFT·Pattern | DRAFT 근무는 자동 삭제 + 목록 표시. Pattern은 남기지만 자동으로 적용 제외 | ML-2 |
| 취소 의미 | CANCELLED = 시작 전 공식 근무의 배정 종료(끝 상태). 사유·시각·행위자 기록. 인력 수요가 사라졌다는 뜻은 아님 | D-26 |
| 공개 후 변경 | 추가·취소·담당자 변경을 별도 Command로. 담당자 변경마다 배정 이력 기록 | D-27 |
| 대타 | 근무가 인계·취소되면 진행 중 요청을 `SHIFT_CHANGED`로 종료. 퇴사자가 대기 후보면 `INELIGIBLE`, 수락자면 요청을 `CANDIDATE_INELIGIBLE`로 종료. 이력 보존, 닫힌 요청 재오픈 없음 | ML-3 |
| 조회 | "내 근무"는 지금 해야 할 근무만 (기존 그대로). 취소·인계된 근무는 새 "빠진 근무" API (원래 시작 시각 기준 미래 + 최근 7일, 조회 범위일 뿐 보존 기간 아님). 과거 소속은 마이페이지 "내 소속 이력". 현재 업무는 모두 **현재 소속 기준** | ML-4 |
| 로그아웃 | Refresh Token이 DB 값과 같을 때만 조건부 무효화, 로그인마다 고유 jti, Origin 검증, 실패해도 화면 정리 + 다시 시도, 세션 쿠키·14일 유지 | AUTH-1 |
| 대타 만료 표시 | 시작 시각이 지난 OPEN·ACCEPTED 요청은 조회 응답에서 CLOSED / EXPIRED, `closedAt = startAt` (응답에서 계산, DB 불변) | G03-D-19 |
| 불가능 시간 충돌 | 관리자 배정(공개 후 추가·담당자 변경·퇴사 인계)은 경고 후 확인, 대타 수락·승인은 거부 | G03-D-21 |
| 확인 응답 | 409 `CONFIRM_REQUIRED` + 충돌 목록, 확인한 목록을 다시 보내 확정. 미리보기 이후 상태가 바뀌면 409 `STALE` | G03-D-05 |
| 알림 | 최선 노력 (실패해도 업무 성공). 알림 대신 볼 수 있는 화면을 둔다. 대타 종료는 사유별 Type, PENDING 후보·실행한 관리자는 받지 않음. 새 Type 6개 | G03-D-22, [NOTIFICATION-TYPES](../contract/NOTIFICATION-TYPES.md) |
| ErrorCode | `{DOMAIN}-{NNN}` 형식 유지, 오류 종류는 목록 문서에만 기록. 새 code 12개 예약, 의미가 틀린 기존 code 5건(T1)은 P1 정정 | G03-D-03, [ERROR-CODES](../contract/ERROR-CODES.md) |
| ID 직접 접근 (M3) | ID로 접근하는 승인·응답·종료·상세·알림 읽음은 Lock·상태를 보기 전에 권한 범위를 먼저 확인. 범위 밖은 없는 ID와 같은 404 | [API-CATALOG](../contract/API-CATALOG.md) CHG-06·07 |
| 동시성 | 전역 Lock 순서 통일, 배정 시 대상 User Lock, Lock 후 재검증, Deadlock·Lock 대기 초과 503 / 상태 충돌 409, 자동 재시도 없음 | ML-5 |

## 3. 기존 코드·API·DB·Test에 주는 영향

| 영향 | 내용 | 주의 |
|---|---|---|
| **DB 제약 변경** | `UNIQUE(workplace_id, user_id)` → 현재 소속만 유일 (생성 Column 방식 후보) | 운영 DDL 필요, 되돌릴 수 없는 지점 있음 |
| **DB 추가** | Shift 취소 정보 Column, 배정 이력 Table, Enum 값 (대타 종료 사유 2, 후보 상태 1, 알림 Type 4~5) | Migration 도구 없음. 운영 Column 형식 UNKNOWN |
| **기존 V4 Lock 변경** | 대타 승인 Lock 순서 (Shift 먼저), 대타 생성·수락에 소속 확인 Lock 추가, 거절의 종료 판정 변경 | V4 Migration과 분리된 Improvement |
| **기존 API 동작 변경** | 근무 상세에 현재 소속 확인, 보낸 대타 목록을 현재 소속 기준으로, 동시 가입 500 → 409 | 기존 Test 기대값 변경은 승인 후 (Charter §6-2) |
| **새 API** | 퇴사 미리보기·확정, 공개 후 추가·취소·담당자 변경, 빠진 근무, 내 소속 이력, 초대 코드 재발급, 서버 로그아웃 | ErrorCode 10개 이상 새로 필요 → G03-D-03 먼저 |
| **Frontend** | 퇴사 화면, 근무표 취소·인계 표시와 버튼, 빠진 근무 탭, 마이페이지 소속 이력, 초대 코드 재발급, 로그아웃 실패 안내·앱 시작 확인 | 새 화면 4~5개 |
| Kotlin Migration | **위 변경은 Migration PR에 섞지 않는다.** Migration은 현재 동작을 그대로 옮기고, Improvement는 그 뒤 별도 PR | Charter §2, AI-RULES §2 |

## 4. 기술 검증 대기 (실행 전)

| 항목 | 확인할 것 |
|---|---|
| 현재 소속 유일성 (U2') | Hibernate 생성 Column + 복합 Unique가 Test DB에서 실제로 만들어지고 동작하는지 (S1~S8). 실패 시 대안 U3 |
| Lock 순서 (ML-5) | 지금 코드에서 반례가 실제로 재현되는지 (R1~R3), 수정 순서의 경합 13개, 예외 매핑 (1213·1205) |
| 보안 G03-OQ-01 (우선) | `/workplaces/**` permitAll 상태에서 무토큰 요청이 실제로 어디까지 통과하는지. 200·500이면 결함 후보로 즉시 공유 |
| 퇴사 vs DRAFT 편집 | 퇴사 후 퇴사자 DRAFT 근무 0건 (ML-2-INV-02)을 Lock으로 지킬 수 있는지 (R4, T14·T15) |
| Java → Kotlin 상호 호출 | Java Test를 그대로 둔 채 Production을 Kotlin으로 바꿀 수 있는지 (record DTO 52개의 accessor를 Test가 178회 호출, Entity 생성자 241회, `ReflectionTestUtils` 51회). S2-6B에서 방식 결정, M0-6 실험 |
| Java 기준 실행 기록 | Migration 비교 기준이 될 Java Test 실행 결과 (현재 대타 93개 ⑤ `UNKNOWN`) |
| 운영 환경 | 운영 MySQL 버전, Column 형식, Index 이름, 쿠키·CORS 운영값 (배포 담당) |

## 5. 아직 구현하지 않은 것

전부. 코드·Test·DB 변경은 0건이다. 정책 카드와 검증 계획만 있다.

## 6. 남은 결정 (사용자·팀)

| ID | 내용 | 누가 |
|---|---|---|
| G03-D-03 | ErrorCode 규칙 — **사용자 잠정 결정 (2026-10-25)**, 팀 승인 대기 | 팀 |
| G03-D-19 | 대타 만료 표현 — **사용자 잠정 결정 (B1)**, 팀 승인 대기. V4-T-02 기대값 변경 승인 포함 | 팀 |
| G03-D-21 | 불가능 시간 충돌 — **사용자 잠정 결정 (관리자 배정 경고·확인, 수락·승인 거부)**, 팀 승인 대기 | 팀 |
| G03-D-22 | 알림 전달 — **사용자 잠정 결정 (최선 노력 + 대체 화면)**, 팀 승인 대기. 다른 관리자 대체 화면 공백은 S3 차단 조건 | 팀 |
| G03-D-10 | 남의 알림 403 / 404 (`DEFER`). CHG-07 알림 읽음 404 전환과 같은 질문 | 팀 |
| 활성 대타 요청 2개 이상 | 기존 데이터에 그런 상태가 있을 때 처리 (ML-3·ML-5 미정) | ML-3 담당 → 팀 |
| 운영 DDL·Rollback | 적용 절차, 배포 순서, 되돌릴 수 없는 지점 | 팀 + 배포 담당 |
| DL-002·009·010 | RC 유지, Harness 공유, MySQL 동시성 검증 지위 | 팀 |
| D-NOW-01 | 공통 설정·H6 담당자 | 팀 |

## 7. 팀에 요청할 것

1. **G03-D-11 확인:** 퇴사 + 재가입을 이번 범위에 넣는 것에 동의하는지. 동의하면 DECISION-LOG에 결정자·일자를 기록.
2. **Domain Owner 리뷰:** V2(퇴사·재가입·초대 코드), V3(공개 후 변경), V4(대타 정리·Lock 변경), V5(알림 Type), V1(로그아웃), Frontend(새 화면).
3. **일정 판단:** 위 범위가 일정 안에 들어가는지. 들어가지 않으면 우선순위 조정 (예: 공개 후 근무 추가·담당자 변경을 퇴사 인계보다 뒤로).
4. **운영 DDL 절차** 담당과 방식.
5. **G03-D-03 ErrorCode 규칙** 결정.

## 8. 정책 카드 위치

| 카드 | 위치 |
|---|---|
| AUTH-1 | [`proposals/AUTH-1-server-logout.md`](proposals/AUTH-1-server-logout.md) |
| ML-2 + D-26 + D-27 | [`proposals/ML-2-leave-shifts-and-published-changes.md`](proposals/ML-2-leave-shifts-and-published-changes.md) |
| ML-3 + V4 Test 정정 메모 | [`proposals/ML-3-substitute-on-leave-and-shift-change.md`](proposals/ML-3-substitute-on-leave-and-shift-change.md) (V4 Test 정정 메모는 S2-5B에서 `TEST-INVENTORY` §3-1-1·§3-2-1에 통합, 원본 로컬 `drafts/D4-ML-3/`) |
| ML-H2 | [`proposals/ML-H2-rejoin-and-active-membership.md`](proposals/ML-H2-rejoin-and-active-membership.md) |
| ML-4 | [`proposals/ML-4-leave-command-and-access.md`](proposals/ML-4-leave-command-and-access.md) |
| ML-5 | [`proposals/ML-5-concurrency-and-lock-order.md`](proposals/ML-5-concurrency-and-lock-order.md) |
| ML-H1, Charter, G03-D-19 | 저장소 (PR #6 병합) |
