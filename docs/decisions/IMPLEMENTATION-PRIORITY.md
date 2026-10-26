# Improvement 구현 우선순위 원칙

> **현재 승인 상태 (2026-10-26):** 사용자 전달 구두 승인과 PR 리뷰에 따른 채택 범위·예외는 [Decision Log](DECISION-LOG.md) §4를 따른다. 아래 최초 작성 당시 PROPOSED/PENDING 표기는 이력이며 현재 승인 범위를 덮어쓰지 않는다. 구현·실험·배포 및 미결정 사항은 각각 별도 상태다.

| 축 | 상태 |
|---|---|
| 결정 단계 | **사용자 잠정 원칙** (2026-10-25). 팀 일정·담당자(D-NOW-01) 확인 전 |
| ② 정책 | `PROPOSED` |
| ③ 문서 | `DRAFT`. 묶음별 세부 범위는 S2·S3에서 채운다 |
| 적용 대상 | Kotlin Migration 이후의 Improvement. Migration 자체는 이 문서의 대상이 아니다 |

## 1. 순서

1. **Kotlin Migration이 먼저다.** Domain별로 현재 동작을 보존하고 필수 회귀 Test를 실행 근거(EV)와 함께 통과시킨다. Migration PR에 Improvement를 섞지 않는다 (AI-RULES §2).
2. 그 Domain의 **P0 Improvement** → **P1** → **P2** 순서로 진행한다.
3. 일정이 부족하면 P1부터 줄인다. P0 묶음은 §3 규칙으로만 넣거나 뺀다.

## 2. 등급

| 등급 | 포함 | 판단 기준 |
|---|---|---|
| P0 | 출시하기로 한 사용자 경로(가입·근무표·대타·퇴사·재가입·로그아웃), 접근 권한, 상태 정합성, 핵심 동시성, **그 기능의 배포에 필요한 DDL·Rollback 절차**, 필수 화면(퇴사 미리보기·충돌 확인, 예정 근무, 빠진 근무, 종료된 대타, 내 소속 이력), 오류 시 최신 상태 확인·재시도 안내, 구현 전 격리 실험 | 빠지면 잘못된 업무 상태가 생기거나, 사용자가 결과를 알 수 없거나, 배포할 수 없다 |
| P1 | **최우선 후보: Publish 충돌 목록 표시와 목록 기반 확인 (G03-D-21 §7-2, 팀이 P0 승격 여부 판단)**, 의미가 틀린 기존 ErrorCode 정정 (G03-D-03), 조회 편의(필터·페이지), 관리 화면 개선, 알림 실패 관측 개선, 변경 인지 UX(G03-D-22 D22-6), 공개 근무 시간 변경(G03-D-27 2차) 등 | P0 경로가 닫힌 뒤의 사용성·운영 개선 |
| P2 | Transactional Outbox, 재시도, 중복 처리 방지, 외부 알림 채널 | 전달 신뢰성 기술. 앱 안 알림만 있는 지금은 효과가 작다 |

우선순위 판단 기준은 "새 기술인가"가 아니라 **"사용자에게 잘못된 결과를 보여 주거나 잘못된 업무 상태를 만들 수 있는가"**다.

## 3. P0 정합성 묶음

같은 묶음 안에서 화면만 만들고 정합성 보호를 뒤로 미루지 않는다. 일정이 부족하면 독립된 묶음 전체를 빼고 제외 문장과 사용자 제약을 기록한다.

| 묶음 | 함께 보장할 계약 | 선행 |
|---|---|---|
| A. 서버 로그아웃 | AUTH-1 전체 | 없음 (독립) |
| B. 공개 근무 변경 기반 | Shift 취소·인계 + 활성 대타 종료(ML-3 `SHIFT_CHANGED`) + 배정 이력 + Shift 우선 Lock(ML-5) + 관련 알림·대체 화면 | 격리 실험 (ML-5) |
| B'. 대타 승인 권한·Lock 정합성 | 대타 승인의 권한 범위 선검사(Lock·상태 노출 전) + 승인 Lock 순서 변경 후보(ML-5) + Lock 이후 상태 재검증 ([API-CATALOG](../contract/API-CATALOG.md) CHG-06, 사용자 잠정 결정 2026-10-25). 다른 ID 직접 지정 API: 근무 상세·후보 응답은 P0(각 묶음 D·C에 포함), 대타 종료·알림 읽음은 P0 검토 후보 — S4 실행 결과로 최종 등급 (CHG-07, 사용자 잠정 결정 2026-10-25) | 격리 실험 (ML-5, M3-T2·T3) |
| C. 퇴사 | 소속 종료 + DRAFT 정리 + 미래 공식 근무 인계·취소(B 사용) + 대타 정리(ML-3) + 단일 Transaction + 미리보기·충돌 목록 확인(G03-D-21 D21-7·8) + 재조회 409 | B |
| D. 재가입 | 새 Membership + 현재 소속 유일성(U2' 또는 U3) + 운영 DDL·Rollback + 현재 소속 기준 조회(SUB-05·SUB-07 포함, G03-D-22 §6-4) | C, U2' 실험 |
| E. 직원 상태 조회 | 예정 근무 + 빠진 근무 + 종료된 대타(G03-D-22) + 내 소속 이력 + 접근 제어 | 해당 기능 묶음과 함께 |
| F. 오류 복구 | 업무 오류 구분(G03-D-03) + 최신 상태 재조회 + 재확인 안내 | 각 묶음과 함께 |

### 3-1. 묶음별 추적표 (S2-6A, 2026-10-25)

한 줄로 "무엇을 왜 고치고, 어디가 바뀌고, 무엇으로 확인하는가"를 잇는다. 정책은 모두 사용자 잠정 결정 / ② `PROPOSED` / 팀 `PENDING`, Test는 모두 ④ `SPECIFIED` / ⑤ `NOT_RUN`. Test ID는 [`TEST-INVENTORY`](../evidence/TEST-INVENTORY.md) §5-1 정식 ID. "Migration에서 먼저 지킬 것"은 이 묶음을 구현하기 **전에** 현재 Java 동작 그대로 통과해야 하는 기존·회귀 Test다.

| 묶음 | 문제 (지금 생기는 잘못된 결과) | 정책 카드 | API | 화면 ([FRONTEND](../contract/FRONTEND-CONTRACT.md)) | 새 ErrorCode · 알림 | 확인 Test | Migration에서 먼저 지킬 것 | 구현 전 차단 조건 |
|---|---|---|---|---|---|---|---|---|
| A. 서버 로그아웃 | 로그아웃해도 Refresh Token이 계속 유효 | AUTH-1 | API-AU-01 | §4-4 | `AUTH-007` · 알림 없음 | V1-N-01~12 | `UserServiceTest`, `ApiV1UserControllerTest`, `RqTest`, `CustomAuthenticationFilterTest` (기대값 변경 0) | G03-OQ-01 보안 실험 (S4), 쿠키 `SameSite`·CORS 운영값 (배포 담당) |
| B. 공개 근무 변경 기반 | 공개 근무를 취소·인계할 수단이 없음. 바뀌어도 진행 중 대타가 남음 | ML-2 (D-26·27), ML-3, ML-5, G03-D-21, G03-D-05, NOTIFICATION-TYPES | API-PS-01·02·03 | §3-2, §3-3 | `SFT-020`·`SFT-021`·`SFT-022` · `SHIFT_ASSIGNED`, `SHIFT_CANCELLED`, `SHIFT_REASSIGNED_AWAY`, `SUBSTITUTE_SHIFT_CHANGED` | V3-N-01~19, V4-N-08~22·39, V5-N-01~16, 경합 H6-C1~C4 (보류) | V3 Shift·Schedule 기존 Test (번호 없음, 경로로 추적), V4-T-01~93, V3-N-24~32 (Test 0 ErrorCode), V3-N-20·21·22 | ML-5 Lock 실험 (S4), 알림 Type DB 형식 `UNKNOWN` (배포 담당), **다른 관리자 대체 화면 공백 (S3 차단) → S3-A: API-SB-02 관리자 끝난 대타 요청 (P0 후보, V4-N-49~54)으로 해소 방향 결정, 구현 전까지 공백**, **한 근무에 활성 대타 요청 2개 이상인 기존 데이터 처리 (정책 미정, ML-3 담당)** |
| B'. 대타 승인 권한·Lock | 관리자가 아닌 사용자가 `requestId`로 요청 존재·상태를 구분하고 Lock을 잡을 수 있음 | API-CATALOG CHG-06·07 (M3), ML-5 | 대타 승인·후보 응답·대타 종료·근무 상세·알림 읽음 | §2 (404 공통 처리) | 새 code 없음 (범위 밖 = 없는 ID와 같은 404) | V4-N-44~47, V3-N-23, V5-N-18, M3-T2·T3 = V4-N-45·46 (H6, `BLOCKED`) | V4-T-23~30·73~81 (승인), V4-T-36~46 (종료), V4-T-56~72 (응답) | ML-5 승인 Lock 순서 실험, **기존 권한 실패 Test 기대값 변경 — S3-A W안 (역할 먼저: 비관리자 403 조회 전, 범위 밖 관리자 404). V4-T-74는 code 그대로·순서만 변경 (TEST-INVENTORY §5-4)**, **알림 읽음 404 전환은 G03-D-10 (`DEFER`)과 같은 질문 → 팀 결정.** 권한 확인 전 Lock 제거와 Frontend 읽음 실패 복구(FRONTEND §2)는 별도로 진행 가능 |
| C. 퇴사 | 퇴사 기능이 없음. 수동 처리 시 미래 근무·대타·DRAFT가 퇴사자에게 남음 | ML-4, ML-2, ML-3, ML-5, G03-D-05, G03-D-21 | API-LV-01·02 | §3-1, §3-2, §6 | `WPL-006`~`WPL-011` · 대타 종료 2종 (`SUBSTITUTE_SHIFT_CHANGED`, `SUBSTITUTE_CANDIDATE_INELIGIBLE`) | V2-N-23~40 (ML-4), V2-N-41~52 (D21·D05), V4-N-12~16·22, ML5-T5~T9·T14·T15 (보류) | V2 Workplace 기존 Test (TEST-INVENTORY §4-2), B의 기존 Test | B 완료, ML-5 실험, `closedRequests` 세는 규칙 (API-LV-02, 정의함), 퇴사 vs DRAFT 편집 경합 (R4) |
| D. 재가입 | 퇴사자가 같은 근무지에 다시 들어올 수 없음 (Unique `(workplace_id, user_id)`) | ML-H1, ML-H2, CHG-01~04 | 가입 (변경), API-WP-01 | §4-3, §9-3 | `WPL-006` 재사용, 현재 소속 유일 제약 · `WORKPLACE_MEMBER_JOINED` | V2-N-01~22, V2-N-06 (= ML-H1 V2-N-04 대체), V5-N-06 | `WorkplaceMemberRepositoryTest.rejectDuplicateMembershipAtDatabaseLevel` (Migration에서는 그대로, ML-H2에서 기대값 변경) | C 완료, U2' 실험 (Hibernate 생성 Column + 복합 Unique), **운영 DDL·Rollback 절차 (배포 담당)** |
| E. 직원 상태 조회 | 취소·인계된 근무, 종료된 대타, 과거 소속을 직원이 확인할 곳이 없음 | ML-4 §6, G03-D-22, G03-D-19 | API-SC-01, API-ME-01, API-SB-01, 보낸 요청 (CHG-02) | §4-1, §4-2, §4-3, §5, §7 | 새 code 없음 | V4-N-23~26 (D-19), V4-N-27~38 (D22), V3-N-09 등 (ML-2 조회) | V4-T-01~06 (`SentSubstituteRequestResponseTest`), V4-T-47·48 | 해당 기능 묶음과 함께. V4-T-02 기대값 변경은 D-19 승인 후 |
| F. 오류 복구 | 오류가 났을 때 무엇이 바뀌었는지, 다시 무엇을 해야 하는지 알 수 없음 | G03-D-03, G03-D-05, FRONTEND §2 | 각 Command의 409 (`STALE`·`CONFIRM_REQUIRED`·`STATE_CONFLICT`) | §2, §3-2 | `WPL-010`·`WPL-011`·`SFT-021`, `COM-004` (503, 실험 전 미적용) | V4-N-40~43 (D03, P1), V2-N-41~52, D03-T4 = ML5-T12 (보류) | V4-N-01 방식 HTTP 계약 (각 Domain), T1 기존 Test 5종 (§5-4, Migration에서 그대로) | 503 매핑 실험 (ML-5 ML-14). T1 정정은 P1 |

**묶음 공통으로 아직 해결되지 않은 것:**
- 모든 Test가 실행 전이다. 대타 Test 93개도 이 저장소 기준 ⑤ `UNKNOWN`이라 Migration의 비교 기준(Java 실행 기록)부터 남겨야 한다 (S2-6B).
- 다른 관리자 대체 화면 공백은 해결이 아니라 S3 차단 조건이다 ([NOTIFICATION-TYPES](../contract/NOTIFICATION-TYPES.md) §3-6). S3-A에서 API-SB-02(P0 후보)로 해소 방향을 정했다. 범위가 늘어난다 (API 1, Query 1, Test 6, 화면 탭 1). 구현·팀 승인 전까지 공백은 남는다.
- S4 기술 실험은 "DB에서 실제로 어떻게 되는가"만 알려 준다. 정책 미정 항목(활성 요청 2개 이상, G03-D-10)은 실험으로 정하지 않는다.

**줄일 수 있는 후보 (묶음 안 정합성은 유지):** 관리자의 **단독** 공개 후 근무 추가·담당자 변경 API와 화면. 퇴사 인계에 쓰는 내부 처리(묶음 B)는 유지한다.

## 4. 완료의 정의

코드를 작성한 것이 아니라 `TEST-INVENTORY`의 해당 Test가 ⑤ `PASSED`이고 `TEST-EVIDENCE`에 EV가 기록된 상태를 완료로 본다. 동시성 Test는 `REPRODUCED` / `NOT_REPRODUCED` 판정 규칙을 따르며, 통과가 모든 경합의 부재를 증명하지는 않는다.

## 5. P2 시작 조건

- P0·P1이 EV와 함께 완료되었다.
- 별도 승인을 받았다 (Outbox는 Event 발행 시점 변경 → AI-RULES §2).
- 별도 PR로 진행한다.
- P2 자리에 사용자 가치가 더 큰 작은 개선이 남아 있으면 그것을 먼저 한다.

## 6. 팀이 정할 것

- 묶음별 담당자와 일정 (D-NOW-01)
- 운영 DDL 적용 담당과 절차
- 이 원칙의 채택
- **G03-D-10 (남의 알림 403 / 404, `DEFER`)과 CHG-07 알림 읽음 404 전환의 관계.** CHG-07 (가)는 G03-D-10을 대신 결정하지 않는다. 둘 중 무엇을 기준으로 할지 팀이 정한다 (S2-6A 발견)
- 한 근무에 활성 대타 요청이 2개 이상인 기존 데이터의 처리 (ML-3 담당, 정책 질문. S4는 그런 상태가 만들어지는지만 확인)

## 7. 변경 이력

| 일자 | 내용 | 이유 |
|---|---|---|
| 2026-10-25 | 초안 | 사용자 선택: P0·P1을 목표로 하고 P2는 일정 여유가 있을 때. 교차 검토 의견(배포에 필요한 DDL·필수 화면은 P0, 정합성 묶음 단위, SUB-07·Publish 위험 재평가) 반영 |
| 2026-10-25 | §3에 묶음 B'(대타 승인 권한·Lock 정합성) 추가 | CHG-06 사용자 잠정 결정 |
| 2026-10-25 | 묶음 B'에 CHG-07 등급 반영 | CHG-07 사용자 잠정 결정 |
| 2026-10-25 | S2-6A: §3-1 묶음별 추적표 (문제 → 정책 → API → 화면 → ErrorCode·알림 → Test ID → Migration 보존 → 차단 조건), §6 팀 결정 2건 추가 | S2-5B 정식 Test ID 반영. 정적 확인 중 B'의 기존 Test 기대값 변경(범위 밖 4 + S3 결정 1)과 G03-D-10 연결 누락 발견 |
| 2026-10-25 | S3-A: 묶음 B에 API-SB-02 (P0 후보), 묶음 B'에 W안·Lock 분리 반영 | 사용자 잠정 결정 (USER-PATH-REVIEW §9-0) |
