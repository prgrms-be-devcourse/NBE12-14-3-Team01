<!--
  SWITCH 3차 참고 자료 공유본 (D0-E-2B, 2026-10-23)
  원본: 작성자 로컬 switch-v3-ai/reviews/G-03-contract-preserve-decision-review.md (2026-10-21 작성, 저장소 밖)
  이 머리말과 §1.2 "AI 작업공간" 1줄 외에는 원문을 바꾸지 않았다. 차이와 SHA-256은 docs/references/README.md §3.
-->

> **참고 자료 (공식 계약 아님).** 이 문서는 2차 Java Original `9858612` 기준 AI 분석 결과(G-03)의 공유본이다.
> - 원문의 `REVIEW_ACCEPTED`는 분석 리뷰에서 받아들인 계약 **후보**이며 팀 `APPROVED`가 아니다. 원문 스스로 "Human Approval을 대신하지 않는다"고 적는다.
> - 팀이 승인한 정책·계약은 [`docs/decisions/DECISION-LOG.md`](../decisions/DECISION-LOG.md)와 [`docs/contract/CONTRACT-BASELINE.md`](../contract/CONTRACT-BASELINE.md)의 `APPROVED` 행만이다 (현재 0건).
> - 원문의 상태 용어와 `docs/STATUS-AXES.md`의 대응, 사용 방법은 [`docs/references/README.md`](README.md)를 먼저 읽는다.
> - 원문이 가리키는 G-01·G-02·A-0x·AI-GUIDE·프롬프트 등은 이 저장소에 없다 (README §6).

---

# G-03 Contract / Preserve / Decision Integration Review

> 대상: G-01 Global Input & Handoff Check (`reviews/G-01-global-input-check.md`), Human Follow-up이 반영된 G-02 Cross-Domain Final Consistency Review (`reviews/G-02-cross-domain-consistency-review.md`), A-01~A-07 Human Review
>
> AS-IS Baseline: `985861263c576308168ca539159552d4a4e47606`
>
> 이 문서는 Global Final Consistency Pass의 세 번째 블록이다.
> Java P0의 정상 Business Contract, Migration Preserve, 미결정 정책을 구분한 Canonical Matrix와 Decision Register를 만들고, G-04 / G-05가 쓸 수 있도록 연결한다.
>
> 이 문서는 **통합 초안 / 검토 결과**다. Contract Freeze, Product Decision, Human Approval을 대신하지 않는다. 새 `HUMAN_APPROVED` / `RUNTIME_VERIFIED`를 만들지 않는다.
>
> `G03-C-*`, `G03-IP-*`, `G03-INV-*`, `G03-D-*`, `G03-OQ-*`, `G03-NF-*`, `G03-DOC-BL-*`는 **이 문서 내부 연결용 식별자**다. 원본 Contract / Preserve / Decision / XD / Runtime ID를 대체하거나 재번호하지 않는다.
> 이 문서의 축 값(Contract Disposition, Migration Gate, Test Purpose, Coverage 수준, BLOCKING_STATUS 등)은 **G-03 제안 표기**이며 AI-GUIDE 공식 Taxonomy를 바꾸지 않는다 (§9).

---

# 1. Metadata / Environment Gate

## 1.1 작업 기준

| 항목 | 값 |
|---|---|
| 작업 | G-03 Contract / Preserve / Decision Integration (`prompts/G-03-contract-preserve-decision-prompt.md` 전체 확인 후 수행. 줄 수 표기는 §1.5) |
| Primary Input | `reviews/G-01-global-input-check.md` (1,123줄 전체), `reviews/G-02-cross-domain-consistency-review.md` (Human Follow-up 반영본, 924줄 전체) |
| 성격 | 통합 / 대응표 / Register 작성. Product Decision, Contract Freeze, Runtime 실행, Test 작성·실행, Kotlin Migration, Refactoring, Hardening 없음 |
| 다음 블록 | G-04 Test / Regression / Runtime Integration, G-05 Layer 0~3 Roadmap (둘 다 시작하지 않음) |

## 1.2 Environment Gate

| 항목 | 확인 결과 | 확인 방법 |
|---|---|---|
| AI 작업공간 | `switch-v3-ai/` (작성자 로컬 분석 작업공간, 저장소 밖. 공유본에서 개인 절대 경로를 이 설명으로 바꿈 — `docs/references/README.md` §3) | 작업 디렉터리 |
| Production Repository | `../NBE12-14-2-Team01` (접근 가능) | `git -C` |
| Branch / HEAD | `develop` / `985861263c576308168ca539159552d4a4e47606` (= Baseline) | `git rev-parse` |
| Working Tree | 변경 0건 (작업 전) | `git status --porcelain` |
| Baseline Commit | `commit` | `git cat-file -t` |
| G-01 | 존재 | 파일 확인 |
| G-02 (최신 수정본) | 존재. `G02-DOC-BL-01` (§11.6.1), §12 Handoff 15행, §13 G-04 14행, §14 G-05 7행, §15 `G02-NF-01~04` 확인 | 본문 확인 (§1.3) |
| A-01~A-07 Human Review | 7개 존재 | 파일 확인 |
| AI-GUIDE / SOURCE-INDEX / AUDIT-MAP | 존재 | 파일 확인 |
| G-03 Output | 작업 시작 시 **0바이트 빈 파일**로 존재 → 덮어쓰기 대상 기존 내용 없음 → 작성 | `ls -la` |
| Baseline Code 확인 | 이번 블록에서 Production Code를 새로 읽지 않음. Code 사실은 각 Review 기록과 G-02 §2.3의 `git show` / `git grep` 결과를 인용 | — |

## 1.3 입력 무결성 확인

| 확인 대상 | 결과 |
|---|---|
| G-02 Membership 판정 (§5.2) | "현재 조사 범위에서 `leftAt` 조건 차이에 따른 독립적인 P0 Contract 위반은 확인되지 않았다", DB 상태 UNKNOWN 분리 — 반영 (§4, §6.4) |
| G-02 Security 판정 (§7.1 / §8.1 / §13 #1) | AUTH-EV-01 Harness 공유, WP-DC-01 / PAT-DC-01은 Endpoint별 HTTP 결과와 AUTH-MIG-06 위반으로 각각 판정 — 반영 (§3, §6.3, §11) |
| `G02-DOC-BL-01` | 존재, 상태 `APPROVAL_PENDING` — 반영 (§10) |
| G-02 §12 G-03 Handoff | 15행 — 전수 매핑 (§10.3) |
| G-02 §13 G-04 Handoff | 14행 — G-03 완료 작업으로 보지 않음, 이관 유지 (§10.4) |
| G-02 §14 G-05 Handoff | 7행 — 이관 유지 (§10.4) |
| G-02 §15 New Finding | `G02-NF-01~04` — 처리 위치 기록 (§10.2) |

입력 문서 변경 여부: 작업 시작 시 `AI-GUIDE.md`, `SOURCE-INDEX.md`, `AUDIT-MAP.md`, `audits/`, `reviews/`, `prompts/`, `evidence/`, `sources/` 아래 파일 47개의 SHA-256을 Scratchpad에 기록했다. 종료 시 같은 방식으로 다시 비교한 결과는 §15.2에 적는다. Git 추적 대상이 아닌 작업공간이므로 Git 상태로 입력 문서 변경 여부를 판단하지 않았다.

## 1.4 변경 범위

| 대상 | 변경 |
|---|---|
| `reviews/G-03-contract-preserve-decision-review.md` | **작성** |
| Production Code / Test / Build / Config / Schema | 없음 |
| A-01~A-07 Audit / Review, G-01, G-02 | 없음 |
| AI-GUIDE / SOURCE-INDEX / AUDIT-MAP / Evidence / Source PDF / Prompt | 없음 |
| 임시 파일 | ID 집합 검산 Script와 입력 해시 목록을 Scratchpad에만 생성 (프로젝트 디렉터리 밖) |

## 1.5 Prompt 사용 이력 확인 (Human Follow-up)

| 항목 | 확인 결과 | 확인 방법 |
|---|---|---|
| 최초 표기 | §1.1에 "559줄"로 기록했음 | 최초 작성본 |
| 줄바꿈 문자 수 | 559 | `wc -l` |
| 실제 줄 수 | **560** — 파일 마지막 줄(§17 "G-04와 G-05를 이어서 시작하지 않는다.")이 줄바꿈 없이 끝나 `wc -l`이 1줄 적게 셈 | `awk 'END{print NR}'`, 마지막 바이트 확인 |
| 내용 동일성 | 작업 시작 시 기록한 SHA-256 (`aadb9281…f610e6`)과 Follow-up 시점 값이 같음 → G-03 작성 시 읽은 Prompt와 현재 로컬 파일은 같은 내용 | `shasum -a 256` |
| 읽은 범위 | 작성 시 파일 전체를 출력해 §0~§17을 모두 읽었음 | 작성 시 기록 |
| 판단 | 차이는 줄 수 계산 방식뿐이며 Prompt 내용 차이는 없음. G-03 결과를 다시 생성하지 않음 | — |

같은 방식으로 확인한 G-01 (1,123줄)과 G-02 (924줄)는 `wc -l`과 실제 줄 수가 같다.

---

# 2. Source / Evidence Scope

## 2.1 실제로 읽은 문서

| 구분 | 문서 | 확인 범위 |
|---|---|---|
| Prompt | `prompts/G-03-contract-preserve-decision-prompt.md` | 전체 |
| Primary | `reviews/G-01-global-input-check.md` | 전체 (§1~§16) |
| Primary | `reviews/G-02-cross-domain-consistency-review.md` | 전체 (§1~§17, Follow-up 반영본) |
| Rules | `AI-GUIDE.md` | 목차, §4.7 Decision Log, §15 Migration 작업 원칙, §16 Defect Candidate / Verified Defect와 Migration (L637-730). §12~§13은 G-02 인용 범위 사용 |
| | `SOURCE-INDEX.md` | 목차, §9 Source 사용 원칙 (L192-214) |
| | `AUDIT-MAP.md` | 목차, A-07 출력 경로 (L430-437) |
| A-01 Review | `reviews/A-01-auth-security-review.md` | §6 / §6.1 Migration Contract (L362-440), §12 팀 결정 / §13 공통 결정 (L1081-1175), §17.5~§17.8 (L1489-1610) |
| A-02 Review | `reviews/A-02-workplace-member-review.md` | §12 재가입 선택지 (L1510-1545), WP-DEC-01~03 (L1782-1910) |
| A-03 Review | `reviews/A-03-regular-shift-pattern-review.md` | PAT-DEC-03 선택지 (L1650-1700), PAT-DEC-01~03 (L1998-2075) |
| A-04 Review | `reviews/A-04-schedule-shift-review.md` | SCH-MIG-15 (L1095-1110), SCH-DEC-01~04 (L2377-2470), §20.5~§20.12 (L3071-3290) |
| A-05 Review | `reviews/A-05-unavailable-review.md` | UNA-DN-01~05 (L664-870) |
| A-06 Review | `reviews/A-06-substitute-review.md` | §25.29~§25.34 (L5415-5720), §27.2~§27.16 (L7508-8440) |
| A-07 Review | `reviews/A-07-notification-review.md` | §13~§16 (L333-482), §20 Human Decision Register, §21 이관 (L565-600) |
| Audit | `audits/A-04-schedule-shift.md` | API 목록 (L463-469, SCH-07 경로 확인) |

Source PDF는 이번 블록에서 다시 열지 않았다. Source 조항은 G-02 §10에서 PDF 원문을 직접 확인한 결과를 인용한다 (`SR(G-02)`로 표기).
Test Source도 이번 블록에서 다시 열지 않았다. 기존 Test의 Assert 범위는 G-01 §8.1 (각 Audit 기록 전재)을 인용한다.

## 2.2 Verification Basis / 축 표기 (이 문서 공통)

| 약어 | 의미 |
|---|---|
| `BSV(Review)` | 원 Review / Audit이 `STATIC_VERIFIED`로 기록한 Baseline 사실. G-03에서 Code를 다시 확인하지 않음 |
| `BSV(G-02)` | G-02 §2.3에서 Baseline Code를 `git show` / `git grep`으로 직접 확인한 사실 |
| `SR(G-02)` | G-02 §10에서 Source PDF 원문을 직접 확인한 사실 (`SOURCE_REVIEWED`) |
| `RE` | REVIEW_EVIDENCE (Evidence Register E-xxx 등) |
| `INF` | INFERENCE |
| `UNKNOWN` | 확인되지 않음 |

Contract Status 5축 (프롬프트 §4.3.1, G-03 제안 표기):

| 축 | 이 문서에서 쓰는 값 |
|---|---|
| Source / Baseline Evidence | `BSV(Review)`, `BSV(G-02)`, `SR(G-02)`, `RE`, `INF`, `UNKNOWN`의 조합 |
| Contract Disposition | `REVIEW_ACCEPTED` (원 Review가 정상 Contract 후보로 수용), `CONDITIONAL` (원문이 CONDITIONAL로 기록), `DECISION_PENDING` (정상 Contract 여부 자체가 미결정), `EVIDENCE_PENDING` (Contract 일부가 Evidence 전 미확인) |
| Human Approval | `NOT_RECORDED` (명시적 정책 승인 기록 없음), `APPROVAL_PENDING` (승인 대상 Decision이 열려 있음). `APPROVED_WITH_EVIDENCE` **0건** |
| Runtime Result | `RUNTIME_PENDING (<Runtime ID>)`, `NOT_EXECUTED (Runtime ID 없음)`. `RUNTIME_VERIFIED` **0건** |
| Migration Gate | `READY_FOR_RUNTIME_DESIGN` (G-04 Runtime / Regression 설계로 넘길 준비), `CANDIDATE` (Regression 설계 후보, 연결된 Runtime ID 없음), `BLOCKED_BY_<원ID>` (해당 범위의 Contract 확정이 그 Decision에 막힘). 어떤 값도 `MIGRATION_READY`가 아님 |

---

# 3. Canonical Contract Inventory

## 3.1 작성 원칙

- G-01 §4.1의 원본 Migration Contract ID 71개를 모두 한 번씩 **주 매핑(Primary)** 했다. 다른 Canonical Rule에도 필요한 ID는 **Related**로만 적고 개수에 다시 넣지 않았다.
- Canonical Rule은 **59개**다: 정상 Contract 성격 `G03-C-01~56` 56개 + 원문이 Implementation Preserve로 명시한 `G03-IP-01~03` 3개. 원본 ID 수(71)와 Canonical 수(59)가 다른 이유는 같은 규칙을 여러 Domain이 기록한 경우(예: Pattern → Shift Snapshot)를 한 행으로 묶었기 때문이다 (§3.10).
- 현재 Code가 그렇게 동작한다는 이유만으로 정상 Contract에 새 규칙을 넣지 않았다. 각 행의 "정상 결과"는 원 Review가 수용한 문장 범위로 제한했다.
- 실패 시 **정확한 HTTP Status / ErrorCode 값**은 대부분 COMMON Decision(G01-DG-01) 대상이므로 정상 Contract에 넣지 않았다. 정상 Contract에는 "거부된다 / 상태가 바뀌지 않는다"까지만 두고, 현재 값은 Preserve(§5)와 G-04 `MIGRATION_COMPATIBILITY` 기대값으로 넘긴다.
- Defect Candidate가 가정하는 비정상 결과는 어느 행의 정상 결과에도 넣지 않았다 (§6.3).
- Canonical 표현은 "ACTIVE" 대신 **Current Membership (`leftAt == null`)**을 쓴다 (G02-NF-02). Source의 "ACTIVE" 표현은 Alias로만 기록한다.

## 3.2 A-01 Auth (원본 7 → Canonical 7)

### 3.2.1 규칙 정의

| G03 ID | 원본 ID (Primary / Related) | 규칙 / Actor · Resource | Preconditions | 정상 결과 / Invariant | 실패 · 권한 조건 | 현재 AS-IS | 근거 |
|---|---|---|---|---|---|---|---|
| G03-C-01 | **AUTH-MIG-01** | 회원가입 비밀번호 저장 / 비인증 사용자 · User | 유효한 가입 요청 | 비밀번호 원문이 저장되지 않고 BCrypt Hash가 저장됨 | — (중복은 C-02) | 같음 (`BSV(Review)`) | A-01 Review §6 |
| G03-C-02 | **AUTH-MIG-02** | 중복 이메일 가입 / 비인증 사용자 · User | 같은 이메일 User 존재 | 정상 가입으로 처리되지 않음 | 실패 응답 형식은 COMMON (G03-D-01 계열) | 같음 | A-01 §6 |
| G03-C-03 | **AUTH-MIG-03**, **AUTH-MIG-04** | 로그인 Token 발급 / User | 올바른 자격 증명 | Access Token + Refresh Token 발급, Refresh Token은 HttpOnly Cookie로 전달 | 로그인 실패 시 Token 미발급 (AUTH-TC-04) | 같음. Cookie 설정값은 운영 (O-01) | A-01 §6 |
| G03-C-04 | **AUTH-MIG-05** | Refresh로 Access 재발급 / User | 유효한 Refresh Token, 서버 검증 통과 | 새 Access Token 발급 (핵심 기능만 Contract) | 잘못된 Refresh 실패 (AUTH-TC-06) | 같음. Token 관리 방식(단일 저장 / 덮어쓰기 / Rotation 없음)은 Preserve G01-P-01 | A-01 §6.1 |
| G03-C-05 | **AUTH-MIG-06** / Related: WP-DEFECT-CANDIDATE-01, PAT-DEFECT-CANDIDATE-01 | 보호 API 인증 / 모든 보호 API | 인증 없음 | 보호 API는 미인증 요청을 허용하지 않음 | 미인증 요청 거부 (현재 EntryPoint 응답 `AUTH-004` 401은 응답 값 기록, G-02 §7.2) | `/api/v1/workplaces/**` permitAll Matcher의 실제 적용 여부 **UNKNOWN** (G-02 §7.1) | A-01 §6, G-02 §7.1 |
| G03-C-06 | **AUTH-MIG-07** | 현재 User 조회 / User · `/auth/me` | 유효한 Access Token | 현재 User 정보 조회 | — | 같음. 응답 필드 `id` / `userId` 사용은 AUTH-NE-01 (NEEDS_EVIDENCE) | A-01 §6, §11 |
| G03-C-07 | **WP-MIG-08**, **UNA-MIG-03** / Related: AUTH-MIG-07, SUB-MIG-02 (Client가 ID를 보내지 않음), NOT-MIG-02 / 03 | 인증 Actor 식별 / 전 Domain | 인증된 요청 | Actor는 SecurityContext User. Workplace 행위는 그 User의 Current WorkplaceMember로 판단 | — | `Rq#getActorId()`가 principal을 `SecurityUser`로 Cast (`BSV(G-02)`) | G01-CR-01, G-02 §7.1 |

### 3.2.2 상태 축

| G03 ID | 관련 Domain | Source / Baseline | Disposition | Human Approval | Runtime Result | Migration Gate | Decision Dependency | 연결 Preserve |
|---|---|---|---|---|---|---|---|---|
| G03-C-01 | — | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | NOT_EXECUTED (Runtime ID 없음) | CANDIDATE | — | — |
| G03-C-02 | COMMON | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | NOT_EXECUTED (Runtime ID 없음) | CANDIDATE | 실패 응답 형식만 G01-DG-01 | G01-P-02 (Error Code) |
| G03-C-03 | O-01 | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (AUTH-EV-02, Priority 원문 미기재) | READY_FOR_RUNTIME_DESIGN | — | G01-P-01 |
| G03-C-04 | — | BSV(Review) | REVIEW_ACCEPTED (A-01 DN-02 → P0_CONTRACT_CANDIDATE) | NOT_RECORDED | RUNTIME_PENDING (AUTH-EV-02) | READY_FOR_RUNTIME_DESIGN | Logout / Session (G03-D-23 / 24)은 Token 관리 방식에만 영향 | G01-P-01 |
| G03-C-05 | A-02 / 03 / 04 / 06 | BSV(G-02) (설정) + UNKNOWN (실제 매칭) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (AUTH-EV-01, MUST / PRE_MIGRATION) | READY_FOR_RUNTIME_DESIGN | — | G01-P-02 |
| G03-C-06 | 전 Domain | BSV(Review) | REVIEW_ACCEPTED, 응답 필드명 부분은 EVIDENCE_PENDING (AUTH-NE-01) | NOT_RECORDED | RUNTIME_PENDING (AUTH-EV-01) | READY_FOR_RUNTIME_DESIGN | — | — |
| G03-C-07 | 전 Domain | BSV(Review) + BSV(G-02) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (WP-EV-03, AUTH-EV-01, UNA-EV-01) | READY_FOR_RUNTIME_DESIGN | — | — |

## 3.3 A-02 Workplace / Member (원본 8 → Canonical 6, WP-MIG-08은 C-07)

### 3.3.1 규칙 정의

| G03 ID | 원본 ID | 규칙 / Actor · Resource | Preconditions | 정상 결과 / Invariant | 실패 · 권한 조건 | 현재 AS-IS | 근거 |
|---|---|---|---|---|---|---|---|
| G03-C-08 | **WP-MIG-01**, **WP-MIG-02** | Workplace 생성 / User · Workplace, WorkplaceMember | 인증 User | Workplace와 생성자 MANAGER Membership이 생성되고, 둘은 하나의 Business Command | 생성 중 실패 시 둘 다 남지 않음 (Rollback 주입은 A-02 §9 DEFERRED) | 같음 | A-02 Review §7 |
| G03-C-09 | **WP-MIG-03** | 초대 코드 참여 / User · WorkplaceMember | 유효한 inviteCode | EMPLOYEE Membership 생성 | 잘못된 inviteCode 실패 (A-02 §10 MUST #3) | 같음. Invite Lifecycle은 SR-WP-04 (Contract 아님) | A-02 §7 |
| G03-C-10 | **WP-MIG-04** (Contract 부분) | 중복 Membership 금지 / User · WorkplaceMember | 같은 Workplace / User 조합 | 같은 Workplace / User에 **현재** Membership이 둘 이상 생기지 않음 | 순차 중복 Join 거부 | 기존 row 존재 시 Join 차단 (Preserve G01-P-03). 재가입 방식은 Decision (G03-D-13) | A-02 §7, §17.5 (§6.2 분리표) |
| G03-C-11 | **WP-MIG-05** / Related: PAT-MIG-02, SCH-MIG-05, SUB-MIG-03 / 06 / 08, NOT-MIG-02 | Current Membership 정의 / WorkplaceMember | — | Current Membership = `leftAt == null` (Source 표현 "ACTIVE"는 Alias, G02-NF-02) | — | 조사 범위(`backend/src/main`)에서 `leftAt`을 설정하는 경로가 확인되지 않음. DB 상태는 UNKNOWN (`BSV(G-02)`, G-02 §5.2) | A-02 §7, E-008 (RE), G-02 §5 |
| G03-C-12 | **WP-MIG-06** | 구성원 목록 범위 / MANAGER · WorkplaceMember | 현재 MANAGER 요청 | 목록에는 Current Membership만 포함 | — | `*AndLeftAtIsNull` Query (`BSV(G-02)`) | A-02 §7 |
| G03-C-13 | **WP-MIG-07** / Related: RC-PAT-01 | 구성원 목록 / inviteCode 조회 권한 / User · Workplace | — | Current MANAGER만 조회 가능 | EMPLOYEE / 비구성원 거부 (현재 `WPL-004` / `WPL-003` 403, 값은 Preserve) | 같음 | A-02 §7 |

### 3.3.2 상태 축

| G03 ID | 관련 Domain | Source / Baseline | Disposition | Human Approval | Runtime Result | Migration Gate | Decision Dependency | 연결 Preserve |
|---|---|---|---|---|---|---|---|---|
| G03-C-08 | — | BSV(Review) | REVIEW_ACCEPTED (MUST) | NOT_RECORDED | RUNTIME_PENDING (WP-EV-02) | READY_FOR_RUNTIME_DESIGN | — | — |
| G03-C-09 | — | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (WP-EV-02) | READY_FOR_RUNTIME_DESIGN | — | — |
| G03-C-10 | A-01 (동시 가입 RC-13) | BSV(Review), DB Unique UNKNOWN | REVIEW_ACCEPTED (Contract 부분) | NOT_RECORDED | RUNTIME_PENDING (WP-EV-01 PRE_MIGRATION, WP-EV-04 PRE_HARDENING) | READY_FOR_RUNTIME_DESIGN | G03-D-13 (재가입), G03-D-11 | G01-P-03 |
| G03-C-11 | A-03 / 04 / 06 / 07 | BSV(G-02) + RE (E-008), DB UNKNOWN | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (WP-EV-02, PAT-EV-03) | READY_FOR_RUNTIME_DESIGN | 종료 이후 데이터 처리는 G01-DG-02 | G01-P-04, P-25~28, P-39 |
| G03-C-12 | — | BSV(G-02) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (WP-EV-02) | READY_FOR_RUNTIME_DESIGN | — | — |
| G03-C-13 | A-03 | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (WP-EV-03) | READY_FOR_RUNTIME_DESIGN | 응답 형식만 G01-DG-01 | — |

## 3.4 A-03 RegularShiftPattern (원본 10 → Canonical 6, 그중 2행은 A-04 ID 포함)

### 3.4.1 규칙 정의

| G03 ID | 원본 ID | 규칙 / Actor · Resource | Preconditions | 정상 결과 / Invariant | 실패 · 권한 조건 | 현재 AS-IS | 근거 |
|---|---|---|---|---|---|---|---|
| G03-C-14 | **PAT-MIG-01**, **PAT-MIG-02**, **PAT-MIG-03** / Related: WP-MIG-05 | Pattern 귀속과 대상 / MANAGER · RegularShiftPattern | 요청자 = 같은 Workplace의 Current MANAGER | Pattern은 WorkplaceMember에 귀속. 대상은 같은 Workplace의 Current Membership. 대상 Role은 EMPLOYEE / MANAGER 모두 허용 | 비구성원 / 종료 Member / EMPLOYEE 요청자 거부 (현재 `SHIFT-004` 400 / `SHIFT-005` 400 / `SHIFT-009` 403, 값은 Preserve G01-P-06) | 같음 | A-03 Review §8 |
| G03-C-15 | **PAT-MIG-04** | Pattern 시간 범위 / Pattern | — | `startTime < endTime`. 같은 시각과 자정 넘김 불가 | 위반 시 거부 | 같음 (Test Source t4 / t5, `BSV(Review)`) | A-03 §8. Unavailable은 자정 넘김 허용 → EXPECTED_DOMAIN_DIFFERENCE (G-02 §6.1, COMMON p.111 위임, `SR(G-02)`) |
| G03-C-16 | **PAT-MIG-05** | Pattern 겹침 금지 / Pattern | 같은 Member, 같은 DayOfWeek | `[start,end)` 기준 겹침 금지, 경계 접촉 허용, 수정 시 자기 제외 | 겹침 시 거부 (현재 `SHIFT-007` 409) | 같음 (t22~t30, t37). 동시 등록 보호 없음 → PAT-DEFECT-CANDIDATE-02 | A-03 §8 |
| G03-C-17 | **PAT-MIG-06**, **PAT-MIG-07** | Pattern 상태 / 삭제 / Pattern | — | 상태값 없음, row 존재 = 현재 규칙. 삭제는 Hard Delete | — | 같음. 종료 Member Pattern 삭제 허용(t41)은 Decision 종속 (G01-P-08, G03-D-14) | A-03 §8, COMMON p.102 Checklist (`SR(G-02)`) |
| G03-C-18 | **PAT-MIG-09**, **SCH-MIG-03** | Schedule 생성 시 Snapshot / MANAGER · Schedule, Shift | Schedule 생성 | 생성 시점의 Current Membership Pattern으로 초기 Shift를 만든다 | — | 같음 (`findByMemberWorkplaceIdAndMemberLeftAtIsNull`, `BSV(G-02)`) | A-03 §8, A-04 §8, P0 p.4 (`SR(G-02)`) |
| G03-C-19 | **PAT-MIG-08**, **PAT-MIG-10**, **SCH-MIG-04** | Pattern 비소급 / Pattern, Shift | Schedule 생성 이후 Pattern 변경 / 삭제 | Shift는 Pattern과 독립 Snapshot이며, Pattern 변경 / 삭제가 이미 만든 Schedule / Shift에 소급 반영되지 않음 | — | 같음 | A-03 §8, A-04 §8, P0 p.4 §2 (`SR(G-02)`) |

### 3.4.2 상태 축

| G03 ID | 관련 Domain | Source / Baseline | Disposition | Human Approval | Runtime Result | Migration Gate | Decision Dependency | 연결 Preserve |
|---|---|---|---|---|---|---|---|---|
| G03-C-14 | A-02, A-04 | BSV(Review) + BSV(G-02) | REVIEW_ACCEPTED (MUST) | NOT_RECORDED | RUNTIME_PENDING (PAT-EV-01, PAT-EV-02, PAT-EV-03) | READY_FOR_RUNTIME_DESIGN | G03-D-02 (Error), G03-D-14 (종료 Member) | G01-P-05, P-06, P-07 |
| G03-C-15 | A-05 (차이) | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | NOT_EXECUTED (Runtime ID 없음) | CANDIDATE | — | G01-P-07 |
| G03-C-16 | — | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (PAT-EV-04, PRE_HARDENING, 동시성만) | CANDIDATE (순차) / 동시성은 DEFECT_REPRODUCTION | — | G01-P-07 |
| G03-C-17 | A-02 | BSV(Review) + SR(G-02) | REVIEW_ACCEPTED | NOT_RECORDED | NOT_EXECUTED (Runtime ID 없음) | CANDIDATE | G03-D-14 (t41 범위만) | G01-P-08 |
| G03-C-18 | A-04 | BSV(Review) + BSV(G-02) + SR(G-02) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (PAT-EV-01, PAT-EV-03, SCH-EV-01). 동시 Snapshot은 SCH-EV-06 / PAT-EV-06 DEFER (Contract 없음, A03-XD-02) | READY_FOR_RUNTIME_DESIGN | — | — |
| G03-C-19 | A-04 | BSV(Review) + SR(G-02) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (PAT-EV-01) | READY_FOR_RUNTIME_DESIGN | — | — |

## 3.5 A-04 Schedule / Shift (원본 15 → Canonical 12, SCH-MIG-03 / 04는 C-18 / C-19)

### 3.5.1 규칙 정의

| G03 ID | 원본 ID | 규칙 / Actor · Resource | Preconditions | 정상 결과 / Invariant | 실패 · 권한 조건 | 현재 AS-IS | 근거 |
|---|---|---|---|---|---|---|---|
| G03-C-20 | **SCH-MIG-01** | Schedule 초기 상태 / MANAGER · Schedule | 생성 성공 | Schedule은 DRAFT로 생성 | — | 같음 | A-04 Review §8, §20.5 |
| G03-C-21 | **SCH-MIG-02** | 주차당 Schedule 1개 / Schedule | 같은 Workplace + weekStartDate | Schedule은 1개만 존재 | 중복 생성 거부 | Code 검사 `BSV(Review)`, DB `UNIQUE(workplace_id, week_start_date)` 실존 **UNKNOWN** | A-04 §8 |
| G03-C-22 | **SCH-MIG-05** | DRAFT Shift 담당자 / MANAGER · Shift | DRAFT Schedule | 담당자는 같은 Workplace의 Current Member. EMPLOYEE / MANAGER 모두 배정 가능 | Workplace 불일치 또는 `leftAt != null` 거부 | 같음 (`ShiftService` L237, `BSV(G-02)`) | A-04 §8 |
| G03-C-23 | **SCH-MIG-06** / Related: SCH-MIG-13 | Shift CREATE / UPDATE 검증 / MANAGER · Shift | DRAFT Schedule | 같은 날, `startAt < endAt`, 해당 주차, Schedule 내부 `[start,end)` 겹침 없음, User 전역 공식 Shift 충돌 없음 | 위반 시 거부 (현재 `SFT-*` 400 / `SCH-*` 409 혼용, Preserve G01-P-09) | 같음 | A-04 §8 |
| G03-C-24 | **SCH-MIG-07**, **UNA-MIG-10** | Schedule 쪽 Unavailable 충돌 처리 / MANAGER · Shift, Schedule Publish | 담당 User의 UnavailableTime과 겹침 | Warning + confirm: confirm 없이는 진행되지 않고, confirm 시 진행. confirm은 Hard Validation을 우회하지 않음 (UNA-TG-C-03) | confirm 없음 → Warning 응답 (현재 `SFT-015` 409, 형식은 G03-D-05) | 같음 | A-04 §8, A-05 §13 |
| G03-C-25 | **UNA-MIG-06** | Unavailable 쪽 공식 Shift 충돌 처리 / User · UnavailableTime | 본인 Official Shift와 겹침 | Warning + confirm | confirm 없음 → Warning 응답 (현재 `OFFICIAL_SHIFT_CONFLICT` 400, 형식은 G03-D-05) | 같음 | A-05 §13 |
| G03-C-26 | **SCH-MIG-08** / Related: A03-XD-01 | DRAFT 충돌 허용 / Schedule | DRAFT | DRAFT는 충돌 상태를 가질 수 있고 최종 검증은 Publish에서 한다 | — | 같음 | A-04 §8, §20.5 |
| G03-C-27 | **SCH-MIG-09**, **SCH-MIG-10** | Publish 최종 검증 / MANAGER · Schedule | DRAFT Schedule | Publish는 **정상 순차 Flow**의 최종 Validation Gate. 원문 성공 조건 목록을 모두 만족할 때만 성공 | 검증 실패 시 DRAFT 유지, `publishedAt` null, Notification 0건 (SCH-EV-02, NOT-EV-05(a) 확인 항목) | 같음. 동시 실행 보장은 이 Contract 범위가 아님 (SCH-DC-01, §6.3) | A-04 §8, §20.5 |
| G03-C-28 | **SCH-MIG-11** | Publish 성공 결과 / Schedule | 검증 통과 | PUBLISHED + `publishedAt` 기록, 기존 Shift row 유지, PUBLISHED → DRAFT 역전이 없음 | — | 같음 | A-04 §8, §20.5, P0 p.5 (`SR(G-02)`) |
| G03-C-29 | **SCH-MIG-12** | Official Shift 정의 / Shift | — | Official Shift = PUBLISHED Schedule ∧ SCHEDULED Shift | — | 같음 (`BSV(G-02)`). `leftAt`과의 관계는 Decision 입력 (G01-P-28) | A-04 §8, G-02 §6.3 |
| G03-C-30 | **SCH-MIG-13** / Related: UNA-MIG-06, SUB-MIG-03 / 06 / 08 | Official Shift 충돌 범위 / User | — | Official Shift 충돌은 User 전역 (Workplace 조건 없음) | — | 같음 (`BSV(G-02)`) | A-04 §8, P0 p.17 §21-⑥ (`SR(G-02)`) |
| G03-C-31 | **SCH-MIG-14** / Related: SUB-MIG-08 | PUBLISHED 이후 변경 제한 / MANAGER · Shift | PUBLISHED Schedule | PUBLISHED Shift는 일반 DRAFT CRUD로 생성 / 수정 / 삭제 불가. Shift.member는 SUB-04 APPROVE로만 바뀐다 (G01-CR-11) | 일반 CRUD 거부 | 같음. 정정 수단은 P1 (G03-D-27) | A-04 §8, P0 p.5 (`SR(G-02)`) |
| G03-C-32 | **SCH-MIG-15** (Contract 부분) / Related: NOT-MIG-05 ① | Publish 후 알림 Business 의미 / Schedule → Notification | Publish 성공 (순차) | Publish 성공 시 SchedulePublishedEvent Flow가 존재하며, 그 Business 의미는 C-54 ①(담당 Member에게 `SCHEDULE_PUBLISHED`)으로 판정 | — | Event 구조는 Preserve (G01-P-14 / 32 / 33). 분리표는 §6.1 | A-04 L1100-1108, §20.5 / §20.6, A-07 §14.1 |

### 3.5.2 상태 축

| G03 ID | 관련 Domain | Source / Baseline | Disposition | Human Approval | Runtime Result | Migration Gate | Decision Dependency | 연결 Preserve |
|---|---|---|---|---|---|---|---|---|
| G03-C-20 | — | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (SCH-EV-01) | READY_FOR_RUNTIME_DESIGN | — | — |
| G03-C-21 | — | BSV(Review) + DB UNKNOWN | REVIEW_ACCEPTED, DB 제약 부분 EVIDENCE_PENDING | NOT_RECORDED | RUNTIME_PENDING (SCH-EV-08, MySQL Schema) | READY_FOR_RUNTIME_DESIGN | — | — |
| G03-C-22 | A-02, A-03 | BSV(Review) + BSV(G-02) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (SCH-EV-01, SCH-EV-02) | READY_FOR_RUNTIME_DESIGN | — | G01-P-10 |
| G03-C-23 | A-05, A-06 | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (SCH-EV-01) | READY_FOR_RUNTIME_DESIGN | 응답 형식 G03-D-03 | G01-P-09 |
| G03-C-24 | A-05 | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (SCH-EV-02, UNA-EV-02) | READY_FOR_RUNTIME_DESIGN | 응답 형식 G03-D-05 | G01-P-17 |
| G03-C-25 | A-04 | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (UNA-EV-02) | READY_FOR_RUNTIME_DESIGN | 응답 형식 G03-D-05 | G01-P-17 |
| G03-C-26 | A-03 | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | NOT_EXECUTED (Runtime ID 없음) | CANDIDATE | — | — |
| G03-C-27 | A-05, A-07 | BSV(Review) | REVIEW_ACCEPTED (순차 Flow 범위) | NOT_RECORDED | RUNTIME_PENDING (SCH-EV-02, SCH-EV-03 PRE_HARDENING은 동시성) | READY_FOR_RUNTIME_DESIGN | — | — |
| G03-C-28 | A-07 | BSV(Review) + SR(G-02) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (SCH-EV-01 + NOT-EV-02) | READY_FOR_RUNTIME_DESIGN | — | — |
| G03-C-29 | A-02, A-05, A-06 | BSV(G-02) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (SCH-EV-01) | READY_FOR_RUNTIME_DESIGN | `leftAt` 관계만 G01-DG-02 (A05-XD-05) | G01-P-11, P-12, P-28 |
| G03-C-30 | A-05, A-06 | BSV(G-02) + SR(G-02) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (SCH-EV-02; SCH-EV-07은 동시성) | READY_FOR_RUNTIME_DESIGN | — | G01-P-28 |
| G03-C-31 | A-06 | BSV(Review) + SR(G-02) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (SCH-EV-01) | READY_FOR_RUNTIME_DESIGN | G03-D-27 (정정 기능 도입 시) | — |
| G03-C-32 | A-07 | BSV(Review) | REVIEW_ACCEPTED (Contract 부분, A-07 NOT-MIG-05 ① 확정 근거) | NOT_RECORDED | RUNTIME_PENDING (SCH-EV-01 + NOT-EV-02) | READY_FOR_RUNTIME_DESIGN | 실패 정책 G03-D-22 (Preserve 부분에만 영향) | G01-P-14, P-32, P-33 |

## 3.6 A-05 UnavailableTime (원본 12 → Canonical 8 + IP 1, UNA-MIG-03 / 06 / 10은 C-07 / C-25 / C-24)

### 3.6.1 규칙 정의

| G03 ID | 원본 ID | 규칙 / Actor · Resource | Preconditions | 정상 결과 / Invariant | 실패 · 권한 조건 | 현재 AS-IS | 근거 |
|---|---|---|---|---|---|---|---|
| G03-C-33 | **UNA-MIG-01** | Resource 범위 / User · UnavailableTime | — | UnavailableTime은 User 전역 Resource (Membership 조건 없음) | — | 같음 | A-05 Review §13, A02-XD-03 CLOSED |
| G03-C-34 | **UNA-MIG-02** | API 형태 / User | — | 4개 API의 Path / Method / Request / Response 유지 | — | 같음. Success code / 메시지 / DELETE 200·204는 G03-D-08 (Preserve G01-P-18) | A-05 §13 |
| G03-C-35 | **UNA-MIG-04** | 시간 규칙 / UnavailableTime | — | 원문 시간 규칙, 자정 넘김 허용, Asia/Seoul Clock | 위반 시 거부 (`UNA-001` / `002` / `004` 400) | 같음 | A-05 §13, COMMON p.111 (`SR(G-02)`) |
| G03-C-36 | **UNA-MIG-05** | 자기 겹침 금지 / User · UnavailableTime | 본인 다른 UnavailableTime | `[start,end)` 겹침 금지, 경계 허용, 수정 시 자기 제외 | 겹침 거부 (현재 `UNA-003` 409) | 같음 (Repository Test Source) | A-05 §13 |
| G03-C-37 | **UNA-MIG-07** | 목록 규칙 / User | — | `endAt > now`, `startAt ASC`, `officialShiftConflict` 계산, 빈 목록 허용 | — | 같음. 같은 startAt의 2차 정렬은 G03-D-08 | A-05 §13 |
| G03-C-38 | **UNA-MIG-08** | 삭제 / 다른 Domain 비전파 / UnavailableTime | — | Hard Delete. UnavailableTime 변경이 Schedule / Shift / Candidate를 자동으로 바꾸지 않음 | — | 같음 | A-05 §13 |
| G03-C-39 | **UNA-MIG-09** / Related: G01-CR-02 | Ownership / User · UnavailableTime | — | 본인 Resource만 수정 / 삭제 | 타인 Resource 거부. 없는 ID와 타인 ID의 구분(현재 404 → 403)은 G03-D-06 | 같음 | A-05 §13 |
| G03-C-40 | **UNA-MIG-11** (Contract 부분) / Related: SUB-MIG-03 / 06 / 08 | Substitute 쪽 Unavailable 처리 / Substitute | SUB-01 생성, SUB-03 ACCEPT, SUB-04 APPROVE | SUB-01 Candidate 생성 시 Unavailable 충돌 → Candidate 제외 (모든 Source 일치). SUB-03 / SUB-04에서 Unavailable Validation Query를 다시 수행 | 재검증 실패 처리 방식(현재 Hard Block `SUB-006` 409)은 Preserve G01-P-19, 최종 정책은 G03-D-21 | 같음 | A-05 §13 HR-UNA, A-06 §27.3 / §27.13, G-02 §6.5 |
| G03-IP-01 | **UNA-MIG-12** | (Implementation Preserve) User Row Lock / UnavailableTime 쓰기 | 쓰기 3종 | 정상 Contract 아님. 쓰기 3종에서 User Row Lock을 첫 DB 동작으로 획득하는 현재 구조를 Migration 중 유지 | — | 같음 | A-05 §13 (`DURING_MIGRATION`), G01-P-16 |

### 3.6.2 상태 축

| G03 ID | 관련 Domain | Source / Baseline | Disposition | Human Approval | Runtime Result | Migration Gate | Decision Dependency | 연결 Preserve |
|---|---|---|---|---|---|---|---|---|
| G03-C-33 | A-02 | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (UNA-EV-01) | READY_FOR_RUNTIME_DESIGN | — | — |
| G03-C-34 | COMMON | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (UNA-EV-01) | READY_FOR_RUNTIME_DESIGN | Success code G03-D-08 | G01-P-18 |
| G03-C-35 | A-03 (차이) | BSV(Review) + SR(G-02) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (UNA-EV-08, DEFER) | CANDIDATE | — | — |
| G03-C-36 | — | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (UNA-EV-01) | READY_FOR_RUNTIME_DESIGN | — | G01-P-17 |
| G03-C-37 | — | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (UNA-EV-01) | READY_FOR_RUNTIME_DESIGN | 2차 정렬 G03-D-08 | G01-P-18 |
| G03-C-38 | A-04, A-06 | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (UNA-EV-01) | READY_FOR_RUNTIME_DESIGN | — | G01-P-15 |
| G03-C-39 | COMMON | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (UNA-EV-01) | READY_FOR_RUNTIME_DESIGN | G03-D-06 | G01-P-17 |
| G03-C-40 | A-06 | BSV(Review) + SR(G-02) | CONDITIONAL (원문). 원문 조건이던 A-06 판단은 완료됨 (A-05 UNA-DN-04 판단 문단) | APPROVAL_PENDING (실패 처리 방식만, G03-D-21) | RUNTIME_PENDING (SUB-EV-01 Filter, SUB-EV-08(a) 가시성 PRE_HARDENING) | READY_FOR_RUNTIME_DESIGN (Contract 부분) / Hard Block 정책 확정은 BLOCKED_BY_SUB-DN-02 | G03-D-21 | G01-P-19 |
| G03-IP-01 | A-04 (Publish User Lock) | BSV(Review) | IMPLEMENTATION_PRESERVE (정상 Contract 아님) | NOT_RECORDED | RUNTIME_PENDING (UNA-EV-07, DEFER) | CANDIDATE (구조 보존 확인) | — | G01-P-16 |

## 3.7 A-06 Substitute (원본 12 → Canonical 10 + IP 2)

### 3.7.1 규칙 정의

| G03 ID | 원본 ID | 규칙 / Actor · Resource | Preconditions | 정상 결과 / Invariant | 실패 · 권한 조건 | 현재 AS-IS | 근거 |
|---|---|---|---|---|---|---|---|
| G03-C-41 | **SUB-MIG-01** | 상태 모델 / SubstituteRequest, Candidate | — | **정상 Contract 여부 미결정.** 현재 Request `OPEN / ACCEPTED / APPROVED / CLOSED` + closeReason Matrix, Candidate `PENDING / ACCEPTED / REJECTED`는 Migration 중 보존 | — | 같음 | A-06 HR-SUB-02 RECLASSIFY, §27.10, G-02 §10.1 #1 / #2 |
| G03-C-42 | **SUB-MIG-02** / Related: AUTH-MIG-07, WP-MIG-08 | SUB-01 생성 권한 / User · SubstituteRequest | 대상 Shift가 미래 Official Shift | 요청자는 해당 Shift의 실제 담당 Member 본인. MANAGER도 자기 Shift만 요청 가능. 활성 Request가 있으면 차단. Client는 requester ID를 보내지 않음 | 위반 시 거부 | 같음. Requester `leftAt` 별도 검사 없음은 Preserve G01-P-25 | A-06 §27.2 |
| G03-C-43 | **SUB-MIG-03** / Related: UNA-MIG-11, SCH-MIG-13 | Candidate Pool / SubstituteCandidate | Request 생성 | 같은 Workplace, `leftAt == null`, EMPLOYEE, Requester 제외. Official Shift / UnavailableTime / Active Substitute Promise와 `[start,end)` 겹치면 제외. Candidate 0명이면 Request를 만들지 않음 | — | 같음 | A-06 §27.3 |
| G03-C-44 | **SUB-MIG-04** (Business 부분) | 생성 원자성 / 중복 금지 / Request, Candidate | Candidate ≥ 1 | Request OPEN + Candidate PENDING N건은 하나의 원자적 작업. 한 Request에 같은 Member가 Candidate로 중복 존재하지 않음 | — | DB `UNIQUE(request_id, member_id)`는 Implementation Preserve (G01-P-31) | A-06 §27.3 |
| G03-C-45 | **SUB-MIG-05** (기본 조회 의미) | 조회 API SUB-02 / 05 / 06 / 07 / User, MANAGER | — | §27.8의 조회 조건 / 정렬 / 통합 범위. SUB-02 / 06 / 07은 조회 중 Request 상태를 바꾸지 않음 | — | 같음. Expiry 표시와 Membership 필터는 Pending으로 분리 (HR-SUB-07) | A-06 §27.8 |
| G03-C-46 | **SUB-MIG-06** | SUB-03 ACCEPT / Candidate 본인 | §27.4 조건 (본인, Current EMPLOYEE, PENDING, Request OPEN, PUBLISHED / SCHEDULED, `startAt > now`) | 세 충돌 Query를 다시 수행. 성공 시 Candidate ACCEPTED + Request ACCEPTED (같은 Transaction). Shift.member 불변. 다른 PENDING Candidate는 자동 REJECTED 되지 않음 | 실패 시 상태 유지 | 같음. "항상 최신 Commit을 본다"는 Contract Fact가 아님 (A-06 §27.4) | A-06 §27.4 |
| G03-C-47 | **SUB-MIG-07** | SUB-03 REJECT / Candidate 본인 | PENDING Candidate | REJECTED. 다른 PENDING이 남으면 Request OPEN 유지. 마지막 PENDING이면 Request CLOSED / `ALL_CANDIDATES_REJECTED` (**순차 기준**) | — | 같음 | A-06 §27.5 |
| G03-C-48 | **SUB-MIG-08** / Related: SCH-MIG-14, A04-XD-02 | SUB-04 APPROVE / 현재 MANAGER | Request ACCEPTED, §27.6 재검증 조건 | 하나의 원자적 작업으로 Shift.member = acceptedMember, Request APPROVED. Self Approval 허용. 실패 시 Request / Candidate / Shift.member 유지 | 권한 / 재검증 실패 시 거부 | 같음. 동시성 안전성은 Runtime Pending | A-06 §27.6, P0 p.8 (`SR(G-02)`) |
| G03-C-49 | **SUB-MIG-09** (현재 Close Flow) | SUB-08 Manager Close / 현재 MANAGER | OPEN 또는 ACCEPTED | CLOSED / `MANAGER_CLOSED`. Shift / Candidate 불변, 자동 재탐색 없음 | — | 같음. 재OPEN 없음은 Preserve G01-P-21 (SUB-DN-01 종속) | A-06 §27.7 |
| G03-C-50 | **SUB-MIG-10** / Related: G01-CR-05 | 시간 규칙 / Substitute | — | 충돌은 `[start,end)`, 주요 Command는 `Shift.startAt > now`일 때만, Asia/Seoul | `now >= startAt`이면 생성 / 응답 / 승인 / 종료 대상 아님 | 같음 | A-06 §27.9 |
| G03-IP-02 | **SUB-MIG-11** | (Implementation Preserve) 현재 Lock 지점 / 순서 | — | 정상 Contract 아님. 정합성 보장을 뜻하지 않음 (AI-GUIDE §15) | — | 같음 | A-06 §27.10, G01-P-29 |
| G03-IP-03 | **SUB-MIG-12** | (Implementation Preserve) Notification Event 구조 | — | 정상 Contract 아님. "A-07 판단 종속" → A-07 판단 완료, 연결처는 §6.1 | — | 같음 | A-06 §27.10, G01-P-30 |

### 3.7.2 상태 축

| G03 ID | 관련 Domain | Source / Baseline | Disposition | Human Approval | Runtime Result | Migration Gate | Decision Dependency | 연결 Preserve |
|---|---|---|---|---|---|---|---|---|
| G03-C-41 | Source (P0 정리본) | BSV(Review) + SR(G-02) | DECISION_PENDING | APPROVAL_PENDING (SUB-DN-01) | RUNTIME_PENDING (SUB-EV-02, 현재 동작 기록) | BLOCKED_BY_SUB-DN-01 (Contract 확정만; AS-IS는 G01-P-20 / 21로 보존) | G03-D-17 | G01-P-20, P-21 |
| G03-C-42 | A-01, A-02 | BSV(Review) | REVIEW_ACCEPTED (MUST) | NOT_RECORDED | RUNTIME_PENDING (SUB-EV-01; SUB-EV-09는 동시 생성) | READY_FOR_RUNTIME_DESIGN | Requester `leftAt`만 G03-D-15 | G01-P-24, P-25 |
| G03-C-43 | A-02, A-04, A-05 | BSV(Review) | REVIEW_ACCEPTED (MUST) | NOT_RECORDED | RUNTIME_PENDING (SUB-EV-01) | READY_FOR_RUNTIME_DESIGN | — | G01-P-28, P-42 |
| G03-C-44 | — | BSV(Review), DB UNIQUE UNKNOWN | REVIEW_ACCEPTED | NOT_RECORDED | RUNTIME_PENDING (SUB-EV-04 Schema) | READY_FOR_RUNTIME_DESIGN | — | G01-P-31 |
| G03-C-45 | A-02 | BSV(Review) | REVIEW_ACCEPTED (기본 조회 의미, HR-SUB-07 RECLASSIFY) | NOT_RECORDED | RUNTIME_PENDING (SUB-EV-02; SUB-EV-13 DEFER) | READY_FOR_RUNTIME_DESIGN | G03-D-19 (Expiry 표시), G03-D-15 (Membership 필터) | G01-P-22, P-26, P-27 |
| G03-C-46 | A-04, A-05 | BSV(Review) | REVIEW_ACCEPTED (MUST) | NOT_RECORDED | RUNTIME_PENDING (SUB-EV-01; SUB-EV-05 / 08 PRE_HARDENING) | READY_FOR_RUNTIME_DESIGN | 실패 처리 방식만 G03-D-21 | G01-P-19, P-29 |
| G03-C-47 | A-07 | BSV(Review) | REVIEW_ACCEPTED (순차 기준) | NOT_RECORDED | RUNTIME_PENDING (SUB-EV-02; SUB-EV-06 PRE_HARDENING) | READY_FOR_RUNTIME_DESIGN | — | — |
| G03-C-48 | A-04 | BSV(Review) + SR(G-02) | REVIEW_ACCEPTED (MUST) | NOT_RECORDED | RUNTIME_PENDING (SUB-EV-01; SUB-EV-08 PRE_HARDENING) | READY_FOR_RUNTIME_DESIGN | 실패 처리 방식만 G03-D-21 | G01-P-19, P-24, P-29 |
| G03-C-49 | — | BSV(Review) | REVIEW_ACCEPTED (현재 Close Flow) | NOT_RECORDED | RUNTIME_PENDING (SUB-EV-02) | READY_FOR_RUNTIME_DESIGN | 재OPEN 여부만 G03-D-17 | G01-P-21 |
| G03-C-50 | 공통 Time | BSV(Review) | REVIEW_ACCEPTED (MUST) | NOT_RECORDED | RUNTIME_PENDING (SUB-EV-10, DEFER) | CANDIDATE | — | — |
| G03-IP-02 | A-04, A-05 | BSV(Review) | IMPLEMENTATION_PRESERVE | NOT_RECORDED | RUNTIME_PENDING (SUB-EV-05~09) | CANDIDATE (구조 보존 확인) | — | G01-P-29 |
| G03-IP-03 | A-07 | BSV(Review) | IMPLEMENTATION_PRESERVE | NOT_RECORDED | RUNTIME_PENDING (NOT-EV-03 ⊂ SUB-EV-01 / 02) | CANDIDATE (구조 보존 확인) | 실패 정책 G03-D-22 | G01-P-30, P-32~35 |

## 3.8 A-07 Notification (원본 7 → Canonical 6)

### 3.8.1 규칙 정의

| G03 ID | 원본 ID | 규칙 / Actor · Resource | Preconditions | 정상 결과 / Invariant | 실패 · 권한 조건 | 현재 AS-IS | 근거 |
|---|---|---|---|---|---|---|---|
| G03-C-51 | **NOT-MIG-01** | 생성 주체 / Client · Notification | — | Client는 Notification을 생성할 수 없다 (생성 API 없음, 생성은 Domain Event 결과) | — | 같음 | A-07 §14.1 |
| G03-C-52 | **NOT-MIG-02** (Contract 부분) | NOT-02 목록 / User · Notification | 인증 User | 본인 수신 Notification 목록 | — | `leftAt == null` 조건은 Contract에서 빼고 Preserve (G01-P-39, RECLASSIFY). `id DESC` tie-breaker / Join은 G01-P-41 | A-07 §14.1 |
| G03-C-53 | **NOT-MIG-03**, **NOT-MIG-04** / Related: G01-CR-13 | NOT-03 읽음 / 수신자 본인 | 본인 Notification | 읽음 처리는 순차 반복 시 멱등이며 `readAt`은 최초 값을 유지. 동시 읽음에서도 최초 `readAt` 보존이 Contract | 남의 알림 / 없는 알림 거부 (403 / 404 값과 순서, 종료 Member 403은 Preserve G01-P-38 / 39) | 같음. 동시 보존의 Evidence는 NOT-EV-13 전 미확인 | A-07 §14.1 |
| G03-C-54 | **NOT-MIG-05** / Related: SCH-MIG-15, SUB-MIG-12 | 알림 생성 정책 ①~⑥, ⑨~⑪ / Domain Event → Notification | 각 Business Command 성공 (**순차 실행 기준**) | A-07 §14.1의 수신자 / Type 규칙. 예: ① Publish → 담당 Member당 `SCHEDULE_PUBLISHED` 1건, 미배정 0건, `readAt` null. ③ Candidate 0명 → 현재 MANAGER | ⑦ ⑧ 제외 (NOT-DN-02) | 같음. 동시 실행 결과는 NOT-DC-01 / 02 Runtime 후 판단 | A-07 §14.1, §15.2, API-NOT p.6 / p.11 (`SR(G-02)`). ③의 세 축 구분은 §3.8.3 |
| G03-C-55 | **NOT-MIG-06** (요구사항 부분) | Domain SoT 보호 / Business Transaction ↔ Notification | Business Commit 성공 후 알림 처리 실패 | 알림 실패가 확정된 Business 상태를 되돌리지 않는다 | — | 충족 여부는 Runtime Pending. 구현 방식(AFTER_COMMIT / REQUIRES_NEW / catch + log)은 Preserve G01-P-33~35 | A-07 §9.1, §14.1 |
| G03-C-56 | **NOT-MIG-07** | 기존 알림 불변 / Notification | — | 이미 생성된 알림 내용은 바뀌지 않는다 | — | 같음 | A-07 §14.1 |

### 3.8.2 상태 축

| G03 ID | 관련 Domain | Source / Baseline | Disposition | Human Approval | Runtime Result | Migration Gate | Decision Dependency | 연결 Preserve |
|---|---|---|---|---|---|---|---|---|
| G03-C-51 | — | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | NOT_EXECUTED (Runtime ID 없음) | CANDIDATE | — | — |
| G03-C-52 | A-02 | BSV(Review) + BSV(G-02) | REVIEW_ACCEPTED (RECLASSIFY 범위) | NOT_RECORDED | RUNTIME_PENDING (NOT-EV-01) | READY_FOR_RUNTIME_DESIGN | `leftAt` 조건만 G03-D-16 | G01-P-39, P-41 |
| G03-C-53 | — | BSV(Review) + H2 Test Source | REVIEW_ACCEPTED (순차 멱등), 동시 보존 부분 EVIDENCE_PENDING | NOT_RECORDED | RUNTIME_PENDING (NOT-EV-01; NOT-EV-13 PRE_HARDENING) | READY_FOR_RUNTIME_DESIGN | 403 / 404 G03-D-10, `leftAt` G03-D-16 | G01-P-38, P-39, P-40 |
| G03-C-54 | A-04, A-06 | BSV(Review) + SR(G-02) | REVIEW_ACCEPTED (RECLASSIFY: 순차 기준) | NOT_RECORDED | RUNTIME_PENDING (NOT-EV-02 ⊂ SCH-EV-01, NOT-EV-03 ⊂ SUB-EV-01 / 02) | READY_FOR_RUNTIME_DESIGN | ③ Product Scope 승인 NOT_RECORDED (Decision ID 없음, §3.8.3), 신규 수신 G03-D-16 | G01-P-32, P-36, P-37, P-42 |
| G03-C-55 | A-04, A-06 | 구조 BSV(Review) / 의미 INF | REVIEW_ACCEPTED (요구사항), 충족 여부 EVIDENCE_PENDING | NOT_RECORDED | RUNTIME_PENDING (NOT-EV-04 / NOT-EV-05(b) PRE_HARDENING, NOT-EV-05(a) PRE_MIGRATION 통합) | READY_FOR_RUNTIME_DESIGN | G03-D-22 (구현 / 관측 방식) | G01-P-33, P-34, P-35 |
| G03-C-56 | — | BSV(Review) | REVIEW_ACCEPTED | NOT_RECORDED | NOT_EXECUTED (Runtime ID 없음) | CANDIDATE | — | — |

### 3.8.3 C-54 ③ 후보 0명 Notification — 세 축 구분 (Human Follow-up)

③ "SUB-01 Candidate 0명 → 현재 Workplace MANAGER에게 알림"은 서로 다른 세 가지 사실이 겹쳐 있다. 하나로 합쳐 읽지 않는다.

| 축 | 내용 | 현재 상태 | 근거 |
|---|---|---|---|
| (A) Review가 수용한 현재 P0 알림 동작 | NOT-MIG-05 ③은 A-07 Review가 "순차 실행 기준" Contract로 수용 (RECLASSIFY). NOT-M-17은 ACCEPT / P0 CONTRACT. SUB-01 시도마다 발행되는 빈도는 Contract가 아니라 Preserve (G01-P-42, A-07 §7 "Contract 위반으로 보지 않고 Preserve") | Disposition REVIEW_ACCEPTED. **기존 Regression 기대값 유지**: SUB-EV-02 "후보 0명 → MANAGER" (A-07 §15.2) | A-07 §7 L164, §9.3 NOT-M-17, §14.1, §14.2, §15.2 |
| (B) Source 범위 차이 | P0 정리본 §25 (p.21)는 "후보 없음 관리자 알림"을 P1으로 분류하고, API-NOT p.6 / p.11과 Baseline은 P0 생성 정책으로 둔다 (G-02 §10.1 #5). 이와 별개로 P0 정리본 내부에서도 §19와 §24 / §25의 알림 범위가 다르다 (G02-NF-01). A-07은 이 차이를 DOC_STALE로 단정하지 않고 Global Pass로 넘겼다 (A-07 §21 #7) | **Product Scope 승인: NOT_RECORDED / APPROVAL_PENDING.** 어느 문서를 최종 범위로 볼지 사람의 결정이 없다 | G-02 §10.1 #5, G02-NF-01, A-07 §21 #7 |
| (C) G03-D-18 (NOT-DN-05) | 철회 / 관리자 거절 / 재OPEN 흐름과 그 알림을 도입할지에 대한 결정. **③ 후보 0명 알림은 이 질문에 들어 있지 않다** (A-07 §20 NOT-DN-05 질문). D-18 결과로 ③의 기대값이 바뀌지 않는다 | PENDING. G02-NF-01의 §19 "관리자 거절 또는 재OPEN" 알림 부분과만 관련 | A-07 §20 |

정리:

- ③의 Product Scope를 묻는 **원본 Decision ID는 없다.** G-03은 새 Decision ID를 만들지 않고, 승인 대기를 Backlog G03-DOC-BL-01 (§10.1)에 기록한다.
- Scope 승인이 없다는 이유로 (A)의 Review 기반 기대값을 삭제하거나 바꾸지 않는다. 반대로 (A)가 있다는 이유로 ③이 최종 P0 Product Scope로 승인됐다고 쓰지 않는다.
- G-04는 ③ 관련 Test 기록에 "Review 기반 기대값"과 "Product Scope 승인 상태"를 따로 남긴다 (§11.2.1).

## 3.9 Contract Coverage Index (프롬프트 §5.1)

`ORIGINAL_CONTRACT_ID → CANONICAL_ID (Primary) → Related → G-04 Handoff`. G-04 열의 상세는 §11.2.

| # | 원본 ID | Primary | Related (개수에 넣지 않음) | G-04 Handoff |
|---|---|---|---|---|
| 1 | AUTH-MIG-01 | G03-C-01 | — | AUTH-TC-01 |
| 2 | AUTH-MIG-02 | G03-C-02 | — | AUTH-TC-02 |
| 3 | AUTH-MIG-03 | G03-C-03 | — | AUTH-TC-03 / 04, AUTH-EV-02 |
| 4 | AUTH-MIG-04 | G03-C-03 | — | AUTH-TC-03, AUTH-EV-02 |
| 5 | AUTH-MIG-05 | G03-C-04 | — | AUTH-TC-05 / 06, AUTH-EV-02 |
| 6 | AUTH-MIG-06 | G03-C-05 | — | AUTH-TC-07, AUTH-CT-01, AUTH-EV-01 |
| 7 | AUTH-MIG-07 | G03-C-06 | G03-C-07, G03-C-42 | AUTH-TC-08, AUTH-EV-01 |
| 8 | WP-MIG-01 | G03-C-08 | — | A-02 §10 MUST #1, WP-EV-02 |
| 9 | WP-MIG-02 | G03-C-08 | — | A-02 §10 MUST #1, WP-EV-02 |
| 10 | WP-MIG-03 | G03-C-09 | — | A-02 §10 MUST #2 / #3, WP-EV-02 |
| 11 | WP-MIG-04 | G03-C-10 | — | A-02 §10 MUST #4, WP-EV-01 / 04 |
| 12 | WP-MIG-05 | G03-C-11 | — | A-02 §10 MUST #5, WP-EV-02, PAT-EV-03 |
| 13 | WP-MIG-06 | G03-C-12 | — | A-02 §10 MUST #5, WP-EV-02 |
| 14 | WP-MIG-07 | G03-C-13 | — | A-02 §10 MUST #6, WP-EV-03 |
| 15 | WP-MIG-08 | G03-C-07 | G03-C-42 | A-02 §10 MUST #6, WP-EV-03, AUTH-EV-01 |
| 16 | PAT-MIG-01 | G03-C-14 | — | A-03 §12 Contract MUST, PAT-EV-01 |
| 17 | PAT-MIG-02 | G03-C-14 | G03-C-11 | A-03 §12, PAT-EV-03 |
| 18 | PAT-MIG-03 | G03-C-14 | G03-C-22 | A-03 §12, PAT-EV-01 |
| 19 | PAT-MIG-04 | G03-C-15 | — | A-03 §12 |
| 20 | PAT-MIG-05 | G03-C-16 | G03-INV-05 | A-03 §12, PAT-EV-04 (동시성) |
| 21 | PAT-MIG-06 | G03-C-17 | — | A-03 §12 |
| 22 | PAT-MIG-07 | G03-C-17 | — | A-03 §12 |
| 23 | PAT-MIG-08 | G03-C-19 | — | A-03 §12, PAT-EV-01 |
| 24 | PAT-MIG-09 | G03-C-18 | — | A-03 §12, PAT-EV-01 / 03 |
| 25 | PAT-MIG-10 | G03-C-19 | — | A-03 §12, PAT-EV-01 |
| 26 | SCH-MIG-01 | G03-C-20 | — | A-04 §12 Contract, SCH-EV-01 |
| 27 | SCH-MIG-02 | G03-C-21 | — | A-04 §12, SCH-EV-08 |
| 28 | SCH-MIG-03 | G03-C-18 | — | A-04 §12, SCH-EV-01, PAT-EV-01 |
| 29 | SCH-MIG-04 | G03-C-19 | — | A-04 §12, PAT-EV-01 |
| 30 | SCH-MIG-05 | G03-C-22 | G03-C-11 | A-04 §12, SCH-EV-01 / 02 |
| 31 | SCH-MIG-06 | G03-C-23 | G03-C-30 | A-04 §12, SCH-EV-01 |
| 32 | SCH-MIG-07 | G03-C-24 | — | A-04 §12, SCH-EV-02, UNA-EV-02 |
| 33 | SCH-MIG-08 | G03-C-26 | — | A-04 §12 |
| 34 | SCH-MIG-09 | G03-C-27 | — | A-04 §12, SCH-EV-02 |
| 35 | SCH-MIG-10 | G03-C-27 | — | A-04 §12, SCH-EV-02 |
| 36 | SCH-MIG-11 | G03-C-28 | — | A-04 §12, SCH-EV-01 |
| 37 | SCH-MIG-12 | G03-C-29 | — | A-04 §12 (Official Query), SCH-EV-01 |
| 38 | SCH-MIG-13 | G03-C-30 | G03-C-23, G03-C-25, G03-C-43, G03-C-46, G03-C-48 | A-04 §12, SCH-EV-02 |
| 39 | SCH-MIG-14 | G03-C-31 | G03-C-48 | A-04 §12, SCH-EV-01 |
| 40 | SCH-MIG-15 | G03-C-32 (Contract 부분) | G03-C-54 (Business 의미), Preserve G01-P-14 / 32 / 33 (§6.1) | NOT-TG-C-03, SCH-EV-01 (+NOT-EV-02) |
| 41 | UNA-MIG-01 | G03-C-33 | — | UNA-TG-C-04, UNA-EV-01 |
| 42 | UNA-MIG-02 | G03-C-34 | — | UNA-TG-C-01, UNA-EV-01 |
| 43 | UNA-MIG-03 | G03-C-07 | — | UNA-TG-C-01, UNA-EV-01 |
| 44 | UNA-MIG-04 | G03-C-35 | — | UNA-EV-08 (DEFER) |
| 45 | UNA-MIG-05 | G03-C-36 | G03-INV-05 | UNA-TG-C-04, UNA-EV-01 |
| 46 | UNA-MIG-06 | G03-C-25 | G03-C-30 | UNA-TG-C-03, UNA-EV-02 |
| 47 | UNA-MIG-07 | G03-C-37 | — | UNA-TG-C-02, UNA-EV-01 |
| 48 | UNA-MIG-08 | G03-C-38 | — | UNA-TG-C-04, UNA-TG-C-07 (DEFER) |
| 49 | UNA-MIG-09 | G03-C-39 | — | UNA-TG-C-01, UNA-EV-01 |
| 50 | UNA-MIG-10 | G03-C-24 | — | UNA-TG-C-03 / C-05, UNA-EV-02, SCH-EV-02 |
| 51 | UNA-MIG-11 | G03-C-40 (Contract 부분) | G03-C-43, G03-C-46, G03-C-48, Preserve G01-P-19 | SUB-TG-C-03 (UNA-TG-C-06 이관), SUB-EV-01, SUB-EV-08(a) |
| 52 | UNA-MIG-12 | G03-IP-01 | Preserve G01-P-16 | UNA-EV-07 (DEFER) |
| 53 | SUB-MIG-01 | G03-C-41 | Preserve G01-P-20 / 21 | SUB-EV-02 (현재 동작 기록) |
| 54 | SUB-MIG-02 | G03-C-42 | — | SUB-TG-C-01, SUB-EV-01 |
| 55 | SUB-MIG-03 | G03-C-43 | G03-C-40 | SUB-TG-C-03, SUB-EV-01 |
| 56 | SUB-MIG-04 | G03-C-44 | Preserve G01-P-31 | SUB-TG-C-01, SUB-EV-04 |
| 57 | SUB-MIG-05 | G03-C-45 | — | SUB-TG-C-07, SUB-EV-02 |
| 58 | SUB-MIG-06 | G03-C-46 | G03-C-40 | SUB-TG-C-04, SUB-EV-01 |
| 59 | SUB-MIG-07 | G03-C-47 | — | SUB-TG-C-05, SUB-EV-02 |
| 60 | SUB-MIG-08 | G03-C-48 | G03-C-40, G03-C-31 | SUB-TG-C-02 / C-04, SUB-EV-01 |
| 61 | SUB-MIG-09 | G03-C-49 | Preserve G01-P-21 | SUB-TG-C-05, SUB-EV-02 |
| 62 | SUB-MIG-10 | G03-C-50 | — | SUB-EV-10 (DEFER) |
| 63 | SUB-MIG-11 | G03-IP-02 | Preserve G01-P-29 | SUB-TG-H-04, SUB-EV-05~09 |
| 64 | SUB-MIG-12 | G03-IP-03 | Preserve G01-P-30, G03-C-54 | NOT-TG-C-04 |
| 65 | NOT-MIG-01 | G03-C-51 | — | (Test ID 없음, §11.2) |
| 66 | NOT-MIG-02 | G03-C-52 | — | NOT-TG-C-01, NOT-EV-01 |
| 67 | NOT-MIG-03 | G03-C-53 | — | NOT-TG-C-02 / C-06, NOT-EV-01, NOT-EV-13 |
| 68 | NOT-MIG-04 | G03-C-53 | — | NOT-TG-C-02, NOT-EV-01 |
| 69 | NOT-MIG-05 | G03-C-54 | G03-C-32 | NOT-TG-C-03 / C-04, NOT-EV-02 / 03 |
| 70 | NOT-MIG-06 | G03-C-55 | — | NOT-TG-X-01 / X-02, NOT-EV-04 / 05 |
| 71 | NOT-MIG-07 | G03-C-56 | — | (Test ID 없음, §11.2) |

## 3.10 Coverage 검산 결과

G-01 §4.1 표에서 원본 ID를 Script로 추출하고(71개, 중복 0), 위 §3.9 Index의 "원본 ID" 열과 Primary 열을 다시 추출해 비교했다.

| 검산 항목 | 결과 | ID |
|---|---|---|
| 원본 Contract ID (G-01 §4.1) | **71** (AUTH 7 / WP 8 / PAT 10 / SCH 15 / UNA 12 / SUB 12 / NOT 7) | — |
| Index에 Primary 매핑된 원본 ID | **71** | — |
| `MISSING_ORIGINAL_IDS` | **0** | — |
| `UNEXPECTED_ORIGINAL_IDS` | **0** | — |
| `DUPLICATE_ASSIGNMENTS` (같은 원본 ID가 Primary로 두 번) | **0** | — |
| `UNMAPPED_IDS` | **0** | — |
| 보류 (Primary는 있으나 Contract 확정 보류) | **1** — SUB-MIG-01 (G03-C-41, DECISION_PENDING) | SUB-MIG-01 |
| Canonical Rule 수 | **59** = `G03-C-01~56` (56) + `G03-IP-01~03` (3) | — |
| 여러 원본 ID를 묶은 Canonical | 10개 (C-03, C-07, C-08, C-14, C-17, C-18, C-19, C-24, C-27, C-53) — 묶인 원본 22개 | — |
| 원문 Implementation Preserve ID | 3 (UNA-MIG-12, SUB-MIG-11, SUB-MIG-12) → G03-IP-01~03 (G-01 §4.3과 일치) | — |

**`원본 71 = 매핑 완료 71 + 미매핑 0` 검증 완료.**
71 − 22 + 10 = 59로 Canonical 수와 일치한다.

---

# 4. Cross-Domain Invariant Matrix

G-01 §4.2의 G01-CR-01~14를 Canonical Invariant로 연결한다. "G-02 결과"는 G-02 §5~§7, §9의 판단이다. G-03에서 새 불변조건을 채택하지 않았다. 필요해 보이는 항목은 Decision Candidate(§13 Open Question)로만 남겼다.

| G03 ID | 원 공통 규칙 | Canonical 불변조건 | 연결 Canonical Contract | G-02 결과 (공통 / 의도된 차이) | 열린 입력 |
|---|---|---|---|---|---|
| G03-INV-01 | G01-CR-01 인증 Actor | Actor는 SecurityContext User. Client가 Actor ID를 고르지 않음. Workplace 행위는 Current WorkplaceMember로 판단 (User / WorkplaceMember 권한 경계) | C-05, C-06, C-07, C-42, C-52, C-53 | SAME_RULE. 무인증 경계는 Matcher 적용 UNKNOWN (§7.1) | AUTH-EV-01 |
| G03-INV-02 | G01-CR-02 Ownership | 본인 Resource만 변경 / 조회 | C-39, C-42, C-53 | SAME_RULE. 403 / 404 응답은 Domain별 (§7.2) | G03-D-04 / 06 / 10 |
| G03-INV-03 | G01-CR-03 Current Membership | Current Membership = `leftAt == null` | C-11, C-12, C-14, C-22, C-43, C-46, C-48, C-52 | 정의는 공통. **적용 범위는 Domain마다 다름** (EXPECTED_DOMAIN_DIFFERENCE, G-02 §5.1). 조사 범위에서 `leftAt` 조건 차이에 따른 독립 P0 Contract 위반은 확인되지 않음. 현재 API에서 종료 Membership 생성 경로 미확인, DB 상태 UNKNOWN (G-02 §5.2) | G01-DG-02 |
| G03-INV-04 | G01-CR-04 Role | MANAGER / EMPLOYEE는 WorkplaceMember 속성. 허용 Role은 기능별로 다름 | C-13, C-14, C-22, C-43, C-48 | RELATED (Pattern / Shift 대상은 MANAGER 허용, Candidate는 EMPLOYEE만) | — |
| G03-INV-05 | G01-CR-05 `[start,end)` | 겹침 = `existing.start < target.end AND existing.end > target.start`. 경계 접촉 허용 | C-16, C-23, C-36, C-43, C-50 | SAME_RULE (Query 식 `BSV(G-02)`). Overnight 허용 여부는 COMMON이 Domain에 위임 (C-15 vs C-35) | `TimeRangeUtilsTest` 내용 미검토 (G-04) |
| G03-INV-06 | G01-CR-06 Pattern Snapshot / 비소급 | 생성 시점 Snapshot, 이후 Pattern 변경 / 삭제 비소급 | C-18, C-19 | SAME_SCENARIO (PAT-EV-01). 동시 Snapshot 경쟁은 Contract 없음 (A03-XD-02) | — |
| G03-INV-07 | G01-CR-07 DRAFT / PUBLISHED | DRAFT는 충돌 허용 + 일반 CRUD 가능, PUBLISHED는 역전이 없음 + 일반 CRUD 불가 | C-20, C-26, C-27, C-28, C-31 | Domain 단독, Source 일치 (P0 p.4-5) | G03-D-27 (정정) |
| G03-INV-08 | G01-CR-08 Official Shift / User 전역 충돌 | Official Shift = PUBLISHED ∧ SCHEDULED. 충돌은 User 전역 | C-23, C-25, C-29, C-30, C-43, C-46, C-48 | SAME_RULE (P0 p.17 §21-⑥). 충돌 Query가 `leftAt`을 보지 않는 것은 결함으로도 의도로도 확정하지 않음 | A05-XD-05 / SR-UNA-03 / A06-XD-04 → G03-D-15 묶음 |
| G03-INV-09 | G01-CR-09 Unavailable 처리 | Schedule / Unavailable 쪽 = Warning + confirm. Substitute Candidate 생성 = 제외 (확정). ACCEPT / APPROVE = 재검증 수행 (확정), 실패 처리 방식은 미결정 | C-24, C-25, C-40, C-43, C-46, C-48 | RELATED. 단계별로 분리 유지 (G-02 §6.5) | G03-D-05, G03-D-21 |
| G03-INV-10 | G01-CR-10 Substitute Lifecycle | 현재 상태 모델은 보존, 최종 Product 어휘는 미결정 | C-41, C-46, C-47, C-48, C-49 | Source 차이 (P0 정리본 ↔ COMMON / API, G-02 §10.1) | G03-D-17 |
| G03-INV-11 | G01-CR-11 ACCEPT ≠ APPROVE | ACCEPT는 Shift.member를 바꾸지 않고, APPROVE에서만 Shift.member와 Request APPROVED가 하나의 원자적 작업으로 바뀜 | C-31, C-46, C-48 | SAME_RULE (P0 / COMMON / A-07 일치). 동시성 안전성은 Runtime Pending | SUB-EV-01 / 08 |
| G03-INV-12 | G01-CR-12 Notification 수신자 | 수신자는 Publisher가 Business Transaction 안에서 결정 (순차 기준 규칙은 C-54) | C-32, C-54, IP-03 | SAME_RULE. 동시 실행 시 수신자 정합은 NOT-DC-02 (파생) | G03-D-16 (종료 Member 신규 수신) |
| G03-INV-13 | G01-CR-13 readAt | 최초 `readAt` 유지 | C-53 | Domain 단독 | NOT-EV-13 |
| G03-INV-14 | G01-CR-14 Business ↔ Notification 결과 경계 | 알림 실패가 확정된 Business 상태를 되돌리지 않음 (Contract). AFTER_COMMIT / REQUIRES_NEW / catch + log는 구현 (Preserve) | C-55, C-32, IP-03 | Contract / Preserve 분리 확인 (G-02 §9) | G03-D-22 (AFTER_RUNTIME) |

프롬프트 §6 필수 항목 대응: User / WorkplaceMember 권한 경계(INV-01 / 04), Current Membership(INV-03), Pattern 비소급(INV-06), Schedule Snapshot(INV-06), `[start,end)`(INV-05), Official Shift 정의(INV-08), User 전역 충돌(INV-08), DRAFT / PUBLISHED 변경 규칙(INV-07), ACCEPT / APPROVE 분리(INV-11), APPROVE 시 Shift.member와 Request 전이(INV-11), Candidate 생성 시 Unavailable 제외(INV-09), Notification과 Business Transaction 결과 경계(INV-14). 12개 모두 연결했다.

---

# 5. Migration Preserve Matrix

## 5.1 작성 원칙

- G-01 §5의 G01-P-01~42를 모두 추적한다. G01-P 번호는 G-01 참조 번호이며 원 근거는 "원본 근거" 열에 적는다.
- **Preserve Kind** 값: `IMPLEMENTATION` / `BEHAVIOR` / `미분류`. 원문이 Kind를 명시한 항목은 "원문", 원문이 Kind를 나누지 않은 항목(예: A-07 §14.2의 "Implementation / Behavior Preserve" 묶음 표)은 **G-03 제안 (`PROPOSED / APPROVAL_PENDING`)**으로 표시한다.
- Preserve는 현행 구현의 모든 결과를 정상 Contract로 고정하는 수단이 아니다. Defect Candidate와 연결된 경로는 Kotlin 비교 분류에 `DEFECT_REPRODUCTION_ONLY`를 함께 적는다.
- `CHANGE_GATE`는 원문의 "변경 가능 조건"을 옮겼다. 원문에 없으면 `NOT_DEFINED`.

Kotlin 비교 분류 (G-03 제안, 프롬프트 §7.1):

| 값 | 의미 |
|---|---|
| `EXPECTED_TO_PRESERVE` | Kotlin 결과가 Java Baseline 실측과 같아야 한다. 다르면 Migration Regression 후보 |
| `CHARACTERIZE_ONLY` | Java 값을 기록하되, 내부 구현이라 외부에서 결정적으로 비교하기 어렵거나 문구처럼 Contract가 아닌 항목. 차이는 조사 대상이지만 자동 Regression은 아님 |
| `DECISION_REQUIRED` | 결정 전까지는 보존하지만, 결정 후 기대값이 바뀔 수 있다 (보통 `EXPECTED_TO_PRESERVE`와 함께 표기) |
| `DEFECT_REPRODUCTION_ONLY` | 결함 후보가 가정하는 비정상 결과. 정상 Regression 기대값으로 쓰지 않고 재현 / Characterization으로만 다룬다 |

## 5.2 Preserve Matrix (42행)

| Preserve ID | 원본 근거 | 현재 동작 / 구조 (요약) | Kind (출처) | 관련 Contract | 임의 변경 시 위험 | 현재 검증 수준 | Migration 보호 방식 | Characterization / Compat Test 후보 | Dependency (Decision / Runtime) | Timing | 다음 Owner |
|---|---|---|---|---|---|---|---|---|---|---|---|
| G01-P-01 | A-01 §6.1 / §14 | User당 단일 `refresh_token`, 로그인 시 덮어쓰기, Rotation 없음, Refresh 시 새 RT / Cookie 갱신 없음 | IMPLEMENTATION (원문) | C-03, C-04 | Logout / Session 정책 전 Token 의미 변경 | BSV(Review) | 구조 유지 + DB / Cookie 관측 비교 | AUTH-CT-02 / 03 | G03-D-23 / 24, AUTH-EV-02, SR-AUTH-04 / 06 | 결정 후 별도 단계 | 팀 (A-01) |
| G01-P-02 | A-01 §17.8 | Security Matcher 구성, AccessDeniedHandler 미구성, Success / Error Code | IMPLEMENTATION (G-03 제안) | C-05 | 경계 결과가 Runtime 확인 전에 바뀜 | BSV(G-02) 설정 / 실제 매칭 UNKNOWN | 설정 유지. 무토큰 결과는 Endpoint별 기록 | AUTH-CT-01 | AUTH-EV-01, SR-AUTH-03, G01-DG-01 | PRE_MIGRATION (Evidence) | G-04 / COMMON |
| G01-P-03 | A-02 WP-MIG-04 | 기존 Membership row 존재 시 Join 차단 | BEHAVIOR (G-03 제안) | C-10 | 재가입 정책을 암묵적으로 결정 | BSV(Review) | HTTP 결과 비교 | WP-EV-05 (CONDITIONAL) | G03-D-13 | 조건부 | A-02 |
| G01-P-04 | A-02 §17.6 | Membership 종료 / 재가입 / Role 변경 / MANAGER 위임 Flow 없음, 종료 시 타 Domain 자동 Lifecycle 없음 | BEHAVIOR (G-03 제안, "기능 미추가" Preserve) | C-11 | Lifecycle 정책 선행 결정 | BSV(Review) + BSV(G-02) | 새 Command 추가 금지 | — | G03-D-11 / 12 / 13 | 채택 시 | 팀 / A-02 |
| G01-P-05 | A-03 PAT-PENDING-01 / PAT-DEC-01 | Backend / Frontend PUT, 전체 필드 교체 | BEHAVIOR (원문 "Runtime / Integration Preserve") | C-14, C-16 | Frontend 연동 깨짐 | BSV(Review) | Method / Request 형태 비교 | A-03 §12 Characterization #2 | G03-D-25 | Documentation | API 문서 / COMMON |
| G01-P-06 | A-03 PAT-PENDING-02 | 비구성원 400 `SHIFT-004` / 종료 400 `SHIFT-005` / EMPLOYEE 403 `SHIFT-009` | BEHAVIOR (G-03 제안) | C-14 | COMMON 결정 전 응답 변경 | BSV(G-02) (Enum) | Status / Code 비교 | PAT-EV-02 | G03-D-02, A03-XD-05 | COMMON_DECISION | COMMON |
| G01-P-07 | A-03 PAT-PENDING-03 | `SHIFT-001~009` Prefix | BEHAVIOR (G-03 제안) | C-14~C-16 | Error Catalog 선행 결정 | BSV(G-02) | Code 비교 | — | G03-D-03 계열, A03-XD-05 | COMMON_DECISION | COMMON |
| G01-P-08 | A-03 §20.6 | Membership 종료 시 Pattern 자동 Lifecycle 없음 (종료 Member Pattern 삭제 허용 포함) | BEHAVIOR (G-03 제안) | C-17, C-11 | PAT-DEC-03 선행 결정 | BSV(Review) (t41) | 기능 미추가, t41 결과 비교 | — | G03-D-14 | 채택 시 | A-02 |
| G01-P-09 | A-04 SCH-PENDING-01 | `SCH-*` / `SFT-*`, 400 / 409 혼용 | BEHAVIOR (G-03 제안) | C-23, C-27 | 〃 | BSV(G-02) | Status / Code 비교 | — | G03-D-03 | COMMON_DECISION | COMMON |
| G01-P-10 | A-04 SCH-PENDING-02 | 다른 Workplace Resource ID의 Status / Error 차이 | BEHAVIOR (G-03 제안) | C-22, C-07 | Resource 노출 정책 선행 결정 | BSV(Review) | 비교 | A-04 §12 Characterization | G03-D-04 | COMMON_DECISION | COMMON |
| G01-P-11 | A-04 SCH-PENDING-03 | SCH-08 검사 순서 (존재 → PUBLISHED → SCHEDULED → Ownership) | BEHAVIOR (G-03 제안) | C-29 | 403 / 404 노출 변화 | BSV(Review) | 실패 응답 비교 | A-04 §12 Characterization #2 | G03-D-04 | COMMON_DECISION | COMMON |
| G01-P-12 | A-04 SCH-PENDING-04 / SCH-DEC-03 | `ShiftStatus.CANCELLED` Enum 존재, 쓰기 경로 없음 | IMPLEMENTATION (G-03 제안) | C-29 | Enum 삭제 / 전이 추가 | BSV(G-02) | Enum 유지, 전이 미추가 | — | G03-D-26 | AFTER_MIGRATION | 팀 (A-04) |
| G01-P-13 | A-04 SCH-PENDING-05 | Success Code / Message / 정렬 세부 | BEHAVIOR (G-03 제안) | C-20~C-31 (응답) | API 응답 변화 | BSV(Review) | 응답 비교 | — | G03-D-01 | COMMON_DECISION | COMMON |
| G01-P-14 | A-04 §20.6 | 현재 Publish Event 구조 | IMPLEMENTATION (원문 "구조") | C-32, C-54 | 알림 경로 단절 | BSV(Review) | 구조 유지 + Row 결과로 외부 의미 확인 | NOT-TG-C-03 | G03-D-22 | 결정 + Runtime 후 | G-04 / COMMON |
| G01-P-15 | A-05 HR-UNA-04 | UPDATE는 JPA Dirty Checking, Entity Cascade 없음 | IMPLEMENTATION (원문 "Implementation Fact") | C-38 | 의도치 않은 전파 | BSV(Review) | 구조 유지 | — | — | NOT_DEFINED ("이유 있는 변경만") | A-05 |
| G01-P-16 | A-05 UNA-MIG-12 | 쓰기 3종에서 User Row Lock 선획득 | IMPLEMENTATION (원문) | IP-01 | 동시성 의미 변화 | BSV(Review) (InOrder Mock) | 순서 유지 | — | UNA-EV-07, Hardening | DURING_MIGRATION (원문) | A-05 / G-04 |
| G01-P-17 | A-05 UNA-DN-01 / 03 | Warning 400 `OFFICIAL_SHIFT_CONFLICT` (Schedule 쪽 409 `SFT-015`), `UNA-001~004` / `UNA-403` / `UNA-404` 혼합, DELETE 메시지 | BEHAVIOR (G-03 제안) | C-24, C-25, C-36, C-39 | 응답 변화 | BSV(G-02) | Status / Code 비교 | UNA-EV-02 | G03-D-05 / 06 / 07, A05-XD-03 | COMMON_DECISION | COMMON |
| G01-P-18 | A-05 UNA-DN-05 | Success code, 메시지, 같은 startAt 2차 정렬, DELETE 200 / 204 | BEHAVIOR (G-03 제안) | C-34, C-37 | 응답 변화 | BSV(Review) | 비교 | — | G03-D-08 | NOT_DEFINED (원문 Timing 없음) | COMMON |
| G01-P-19 | A-05 UNA-MIG-11 / UNA-DN-04 = A-06 §27.10 | SUB-03 / SUB-04의 Unavailable Hard Block (409 `SUB-006`) | BEHAVIOR (G-03 제안) | C-40, C-46, C-48 | 정책을 Migration에서 결정 | BSV(Review) | 실패 결과 비교 | A-06 Characterization | G03-D-21, SUB-EV-08(a) | COMMON_DECISION | 사람 (A-05 / A-06) |
| G01-P-20 | A-06 §27.10 | Request `OPEN / ACCEPTED / APPROVED / CLOSED`, Candidate `PENDING / ACCEPTED / REJECTED` | BEHAVIOR (G-03 제안) | C-41 | 상태 어휘 변경 | BSV(Review) | DB 상태값 / 응답 비교 | SUB-EV-02 기록 | G03-D-17, A06-XD-06 | DEFERRED | 팀 (A-06) |
| G01-P-21 | A-06 §27.10 | `CLOSED → OPEN` 없음, 자동 재탐색 없음 | BEHAVIOR (G-03 제안) | C-49, C-41 | 새 전이 추가 | BSV(Review) | 전이 미추가 | — | G03-D-17 | DEFERRED | 팀 (A-06) |
| G01-P-22 | A-06 §27.10 / HR-SUB-07 | SUB-05 Response-only EXPIRED (`closedAt` = 조회 시점 now) | BEHAVIOR (G-03 제안) | C-45 | Expiry 정책 암묵 결정 | BSV(Review) + Unit Test Source | 응답 비교 (Clock 고정) | SUB-TG-X-01 | G03-D-19 | DEFERRED | 팀 (A-06) |
| G01-P-23 | A-06 §27.10 | DB EXPIRED 전이 없음 (OPEN / ACCEPTED 유지 가능), Command 거부 | BEHAVIOR (G-03 제안) | C-45, C-50 | Scheduler / Lazy 전이 무단 도입 | BSV(Review) | DB 상태 비교 | SUB-TG-X-01 | G03-D-19, SUB-EV-02 | DEFERRED | 팀 (A-06) |
| G01-P-24 | A-06 §27.10 / SUB-DN-04 | 현재 ErrorCode / HTTP Status (400 / 409, 다른 Domain 코드 재사용, 검사 순서, Success code) | BEHAVIOR (G-03 제안) | C-42~C-49 | 응답 변화 | BSV(Review) | 비교 | SUB-TG-X-04 (DEFER) | G03-D-09, A06-XD-01 | COMMON_DECISION | COMMON |
| G01-P-25 | A-06 §27.10 | Requester `leftAt` 별도 검사 없음 | BEHAVIOR (G-03 제안) | C-42 | Lifecycle 정책 암묵 결정 | BSV(Review) | 검사 미추가 | SUB-TG-X-03 (DEFER) | G03-D-15 | 조건부 | A-02 |
| G01-P-26 | A-06 §27.10 | SUB-05 requester `leftAt` 필터 없음 | BEHAVIOR (G-03 제안) | C-45 | 〃 | BSV(Review) | 필터 미추가 | SUB-TG-X-03 | G03-D-15 | 조건부 | A-02 |
| G01-P-27 | A-06 §27.10 | SUB-07 accepted member `leftAt` 필터 없음 | BEHAVIOR (G-03 제안) | C-45 | 〃 | BSV(Review) | 필터 미추가 | SUB-TG-X-03 | G03-D-15 | 조건부 | A-02 |
| G01-P-28 | A-06 §27.10 / A-04 §4.7 | Official Shift Conflict Query가 `leftAt` 무시 | BEHAVIOR (G-03 제안) | C-29, C-30, C-43 | 충돌 판정 범위 변화 | BSV(G-02) | Query 의미 유지 | — | G03-D-15 묶음 (A05-XD-05, SR-UNA-03) | 조건부 | A-02 / A-04 |
| G01-P-29 | A-06 SUB-MIG-11 | 현재 Lock 지점 / 순서 | IMPLEMENTATION (원문) | IP-02 | 동시성 의미 변화 | BSV(Review) | 지점 / 순서 유지 | SUB-TG-H-04 | SUB-EV-05~09 | Runtime 후 Hardening | G-04 |
| G01-P-30 | A-06 SUB-MIG-12 / §27.10 | Notification Event 구조 | IMPLEMENTATION (원문) | IP-03, C-54 | 알림 경로 단절 | BSV(Review) | 구조 유지 + Row 결과 확인 | NOT-TG-C-04 | G03-D-22 | 결정 후 | G-04 / COMMON |
| G01-P-31 | A-06 HR-SUB-03 / SUB-MIG-04 | DB `UNIQUE(request_id, member_id)` | IMPLEMENTATION (원문 "Unique 구현은 Preserve") | C-44 | 중복 Candidate 방어 상실 | BSV(Review), 실제 DB UNKNOWN | Schema 유지 | — | SUB-EV-04 | NOT_DEFINED (원문 "—") | G-04 |
| G01-P-32 | A-07 §14.2 | Event 7종 이름 / Payload / 발행 시점 / Publisher가 수신자 결정 | IMPLEMENTATION (G-03 제안) | C-32, C-54, IP-03 | 수신자 규칙 변화 | BSV(Review) | 구조 유지 | — | G03-D-22 | 결정 후 | COMMON |
| G01-P-33 | A-07 §14.2 | `@TransactionalEventListener(AFTER_COMMIT)`, `fallbackExecution` 기본값, 동기 실행 | IMPLEMENTATION (G-03 제안) | C-55 | 실패 경계 변화 | BSV(Review) (동기 실행은 INF) | 구조 유지 | NOT-TG-X-03 (DEFER) | G03-D-22, NOT-EV-04 / 05 | AFTER_RUNTIME | COMMON |
| G01-P-34 | A-07 §14.2 | `NotificationService` `REQUIRES_NEW`, 개수 검증 후 수신자별 save | IMPLEMENTATION (G-03 제안) | C-55 | 〃 | BSV(Review) | 구조 유지 | NOT-TG-X-01 | G03-D-22, SR-NOT-02 | AFTER_RUNTIME | COMMON |
| G01-P-35 | A-07 §14.2 | Listener `catch(Exception)` + `log.error` | IMPLEMENTATION (G-03 제안) | C-55 | 실패 전파 변화 | BSV(Review) | 구조 유지 | NOT-TG-X-01 | G03-D-22, SR-NOT-01 | AFTER_RUNTIME | COMMON |
| G01-P-36 | A-07 §14.2 / NOT-M-23 | 메시지 상수 문구 (생성 주체만 Contract) | BEHAVIOR (G-03 제안) | C-54 | 문구 변화 | BSV(Review) | 문구 기록 | — | — | NOT_DEFINED ("UI 작성 단계") | — |
| G01-P-37 | A-07 §14.2 / NOT-M-04 | NotificationType 8개, 미사용 `SUBSTITUTE_EXPIRED` 삭제 금지 | IMPLEMENTATION (G-03 제안; 이름은 Contract, 미사용 값은 Preserve) | C-54 | Expiry 결정 전 Enum 축소 | BSV(Review) | Enum 유지 | — | G03-D-20, G03-D-19 | Expiry 결정 후 | 팀 (A-06) |
| G01-P-38 | A-07 §14.2 / NOT-M-12 | `NOT-001` 404 / `NOT-002` 403, 존재 → 소유 순서, Success `"200"` | BEHAVIOR (G-03 제안) | C-53 | 응답 변화 | BSV(G-02) | Status / Code 비교 | NOT-EV-01 | G03-D-10 | COMMON_DECISION | COMMON |
| G01-P-39 | A-07 §14.2 / NOT-M-07 | NOT-02 / 03의 `leftAt == null` 조건 | BEHAVIOR (G-03 제안) | C-52, C-53 | 종료 Member 알림 정책 암묵 결정 | BSV(G-02) | 조건 유지 | NOT-TG-X-04 (DEFER) | G03-D-16 | 조건부 | A-02 / 사람 |
| G01-P-40 | A-07 §14.2 / NOT-M-14 | 읽음 `PESSIMISTIC_WRITE` (방식만 Preserve) | IMPLEMENTATION (G-03 제안) | C-53 | 동시 읽음 보장 상실 | BSV(Review) + H2 Test Source | 방식 유지 + 최초 readAt 결과 | NOT-TG-C-06 | NOT-EV-13 | NOT-EV-13 결과 후 | G-04 |
| G01-P-41 | A-07 §14.2 | 목록 `id DESC` tie-breaker, `workplaceName` Join | BEHAVIOR (G-03 제안) | C-52 | 정렬 변화 | BSV(Review) | 정렬 비교 | NOT-TG-C-01 | — | NOT_DEFINED (원문 "—") | — |
| G01-P-42 | A-07 §14.2 | Candidate 0명 알림이 SUB-01 시도마다 발행 | BEHAVIOR (G-03 제안) | C-54, C-43 | 알림 빈도 변화 | BSV(Review) | 건수 비교 | — | Source 범위 차이 (G02-NF-01 / G-02 §10.1 #5) | NOT_DEFINED (원문 "—") | 사람 (Source 정리) |

## 5.3 Preserve Coverage Index (프롬프트 §7.1)

`ORIGINAL_PRESERVE_ID → PRESERVE_KIND → RELATED_CONTRACT → TEST_INTENT → CHANGE_GATE → Kotlin 비교`

TEST_INTENT: `BC` = Behavior Characterization, `MCR` = Migration Compatibility Regression, `IP` = Implementation Preserve (외부 행동 / Transaction 의미 검사로 대체), `DR` = Defect Reproduction 경로 분리.

| Preserve ID | Kind | Related Contract | TEST_INTENT | CHANGE_GATE (원문) | Kotlin 비교 |
|---|---|---|---|---|---|
| G01-P-01 | IMPLEMENTATION | C-03, C-04 | IP + BC | Logout / Session 목표 Contract 채택 후 별도 단계 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-02 | IMPLEMENTATION | C-05 | BC + DR (`/workplaces/**` 무토큰 결과) | AUTH-EV-01 + COMMON 결정 | 설정: EXPECTED_TO_PRESERVE / 무토큰 비정상 결과: DEFECT_REPRODUCTION_ONLY (§6.3) |
| G01-P-03 | BEHAVIOR | C-10 | MCR | Membership 종료 채택 + 재가입 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-04 | BEHAVIOR | C-11 | BC | 팀이 Membership Lifecycle을 3차 Scope로 채택 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-05 | BEHAVIOR | C-14, C-16 | MCR | API 문서 정리 단계 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-06 | BEHAVIOR | C-14 | MCR | COMMON Error 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-07 | BEHAVIOR | C-14~C-16 | MCR | COMMON 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-08 | BEHAVIOR | C-17, C-11 | BC | Membership 종료 채택 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-09 | BEHAVIOR | C-23, C-27 | MCR | COMMON 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-10 | BEHAVIOR | C-22, C-07 | MCR | COMMON 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-11 | BEHAVIOR | C-29 | MCR | COMMON 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-12 | IMPLEMENTATION | C-29 | IP | P1 Cancel 결정 (Migration 이후) | EXPECTED_TO_PRESERVE (Enum 존재) |
| G01-P-13 | BEHAVIOR | C-20~C-31 | MCR | Contract Matrix 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-14 | IMPLEMENTATION | C-32, C-54 | IP (Row 결과로 외부 의미 확인) + DR (NOT-DC-02 수신자) | NOT-DN-04 결정 + Runtime | 순차 결과: EXPECTED_TO_PRESERVE / 오래된 수신자: DEFECT_REPRODUCTION_ONLY |
| G01-P-15 | IMPLEMENTATION | C-38 | IP | NOT_DEFINED ("이유 있는 변경만") | CHARACTERIZE_ONLY |
| G01-P-16 | IMPLEMENTATION | IP-01 | IP | Runtime / Hardening 결과 후 | CHARACTERIZE_ONLY (순서) / UNA-DC-01 결과: DEFECT_REPRODUCTION_ONLY |
| G01-P-17 | BEHAVIOR | C-24, C-25, C-36, C-39 | MCR | COMMON 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-18 | BEHAVIOR | C-34, C-37 | MCR | Contract Matrix 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-19 | BEHAVIOR | C-40, C-46, C-48 | MCR | SUB-DN-02 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-20 | BEHAVIOR | C-41 | MCR | SUB-DN-01 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-21 | BEHAVIOR | C-49, C-41 | MCR | SUB-DN-01 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-22 | BEHAVIOR | C-45 | BC + MCR | SUB-DN-03 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-23 | BEHAVIOR | C-45, C-50 | BC + MCR | SUB-DN-03 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-24 | BEHAVIOR | C-42~C-49 | MCR | COMMON 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-25 | BEHAVIOR | C-42 | BC (Fixture 필요) | A-02 결정 | CHARACTERIZE_ONLY + DECISION_REQUIRED |
| G01-P-26 | BEHAVIOR | C-45 | BC (Fixture 필요) | A-02 결정 | CHARACTERIZE_ONLY + DECISION_REQUIRED |
| G01-P-27 | BEHAVIOR | C-45 | BC (Fixture 필요) | A-02 결정 | CHARACTERIZE_ONLY + DECISION_REQUIRED |
| G01-P-28 | BEHAVIOR | C-29, C-30, C-43 | BC (Fixture 필요) | A-02 / A-04 결정 | CHARACTERIZE_ONLY + DECISION_REQUIRED |
| G01-P-29 | IMPLEMENTATION | IP-02 | IP + DR (SUB-DC-01~03) | Runtime 후 Hardening | CHARACTERIZE_ONLY (구조) / 경쟁 결과: DEFECT_REPRODUCTION_ONLY |
| G01-P-30 | IMPLEMENTATION | IP-03, C-54 | IP (Row 결과) + DR (NOT-DC-01) | NOT-DN-04 결정 | 순차 결과: EXPECTED_TO_PRESERVE / 전원 거절 누락: DEFECT_REPRODUCTION_ONLY |
| G01-P-31 | IMPLEMENTATION | C-44 | IP (Schema) | NOT_DEFINED | EXPECTED_TO_PRESERVE (Schema) |
| G01-P-32 | IMPLEMENTATION | C-32, C-54, IP-03 | IP | 결정 후 | 순차 결과: EXPECTED_TO_PRESERVE / 이름·Payload: CHARACTERIZE_ONLY |
| G01-P-33 | IMPLEMENTATION | C-55 | IP | NOT-DN-04 결정 | CHARACTERIZE_ONLY |
| G01-P-34 | IMPLEMENTATION | C-55 | IP | NOT-DN-04 결정 | CHARACTERIZE_ONLY |
| G01-P-35 | IMPLEMENTATION | C-55 | IP | NOT-DN-04 결정 | CHARACTERIZE_ONLY |
| G01-P-36 | BEHAVIOR | C-54 | BC | NOT_DEFINED ("UI 작성 단계") | CHARACTERIZE_ONLY |
| G01-P-37 | IMPLEMENTATION | C-54 | IP | Expiry 결정 | EXPECTED_TO_PRESERVE (Enum 8개) + DECISION_REQUIRED |
| G01-P-38 | BEHAVIOR | C-53 | MCR | COMMON 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-39 | BEHAVIOR | C-52, C-53 | BC (Fixture 필요) | A-02 결정 | EXPECTED_TO_PRESERVE + DECISION_REQUIRED |
| G01-P-40 | IMPLEMENTATION | C-53 | IP (최초 readAt 결과) | NOT-EV-13 결과 | 최초 readAt: EXPECTED_TO_PRESERVE / Lock 방식: CHARACTERIZE_ONLY |
| G01-P-41 | BEHAVIOR | C-52 | MCR | NOT_DEFINED | EXPECTED_TO_PRESERVE |
| G01-P-42 | BEHAVIOR | C-54, C-43 | MCR | NOT_DEFINED | EXPECTED_TO_PRESERVE (Source 정리 대기) |

## 5.4 Preserve 검산

| 항목 | 결과 |
|---|---|
| 원본 Preserve 행 (G-01 §5) | **42** (G01-P-01~42, Script 추출, 중복 0) |
| §5.2 / §5.3 매핑 | **42 / 42** |
| 누락 / 중복 | **0 / 0** |
| Kind 분포 | IMPLEMENTATION **15** / BEHAVIOR **27** / 미분류 **0** (내역은 §5.5) |
| Kind 제안 승인 상태 | G-03 제안 Kind는 모두 `PROPOSED / APPROVAL_PENDING` (§9) |
| 원문 CHANGE_GATE 없음 (`NOT_DEFINED`) | 5 (P-15, P-31, P-36, P-41, P-42) — P-18은 원문 변경 조건 "Contract Matrix 결정"이 있으나 Timing이 없음 |
| `DEFECT_REPRODUCTION_ONLY` 경로를 가진 Preserve | 5 (P-02, P-14, P-16, P-29, P-30) |

## 5.5 Kind 집계

| Kind | Preserve ID | 개수 |
|---|---|---|
| IMPLEMENTATION (원문 명시) | P-01, P-14, P-15, P-16, P-29, P-30, P-31 | 7 |
| IMPLEMENTATION (G-03 제안) | P-02, P-12, P-32, P-33, P-34, P-35, P-37, P-40 | 8 |
| BEHAVIOR (원문 명시) | P-05 | 1 |
| BEHAVIOR (G-03 제안) | P-03, P-04, P-06, P-07, P-08, P-09, P-10, P-11, P-13, P-17, P-18, P-19, P-20, P-21, P-22, P-23, P-24, P-25, P-26, P-27, P-28, P-36, P-38, P-39, P-41, P-42 | 26 |
| 미분류 | — | 0 |
| **합계** | | **42** |

IMPLEMENTATION 15 (원문 7 + 제안 8), BEHAVIOR 27 (원문 1 + 제안 26). §5.2 Kind 열을 Script로 다시 세어 같은 값을 확인했다. P-14는 원문 표현이 "현재 Publish Event 구조", P-31은 "Unique 구현은 Preserve"라서 원문 쪽으로 셌다.

---

# 6. Contract vs Preserve Boundary

## 6.1 G-02 §9 필수 Boundary

원본 문서를 고치지 않고 G-03 Matrix에서 관계를 표현한다.

| 대상 | Contract 부분 (정상 행동) | Preserve 부분 (현재 구조 / 행동, Migration 중 임의 변경 금지) | Decision / Runtime | G-03 처리 |
|---|---|---|---|---|
| **SCH-MIG-15** | Publish 성공 후 알림 Flow가 존재하고, 그 Business 의미 = NOT-MIG-05 ① (담당 Member당 `SCHEDULE_PUBLISHED` 1건, 미배정 0건, `readAt` null) → **G03-C-32 / G03-C-54** | Event 이름 / Payload / 수신자 ID를 Schedule Domain이 결정 (G01-P-14, P-32), Listener AFTER_COMMIT (G01-P-33) | 실패 처리 개선은 G03-D-22 (NOT-DN-04). Runtime SCH-EV-01 (+NOT-EV-02) | 원본 ID 유지. Contract 부분 Primary = G03-C-32, Preserve 부분 = G01-P-14 / 32 / 33. "임의 제거·구조 변경 금지" 문장은 Preserve 규칙으로 읽는다 |
| **SUB-MIG-12** | Substitute 상태 전이 후 알림 (NOT-MIG-05 ②~⑥, ⑨~⑪, 순차) → **G03-C-54** | Substitute Event Payload / Publisher 구조 (G01-P-30, P-32~35) → **G03-IP-03** | "A-07 판단 종속" → **A-07 판단 완료** (A-07 §14.1 / §14.2). Product 실패 정책은 G03-D-22로 여전히 Pending | 종속 해소 사실을 원본 ID에 연결. 정책 결정 완료로 쓰지 않음 |
| **NOT-MIG-05** | 생성 정책 ①~⑥, ⑨~⑪ (순차 기준) → **G03-C-54** | 메시지 문구 (P-36), Type Enum (P-37), 0명 알림 시도마다 발행 (P-42) | ⑦ ⑧ 제외 (G03-D-20), ③ Product Scope 승인 대기 (G-02 §10.1 #5 / G02-NF-01, Decision ID 없음, §3.8.3), 동시 실행 결과 (NOT-DC-01 / 02) | Contract (③ 포함, Review 기반). 동시 실행 결과는 기대값에 넣지 않음 |
| **NOT-MIG-06** | Notification 실패가 확정된 Business 상태를 되돌리지 않음 → **G03-C-55** | AFTER_COMMIT / REQUIRES_NEW / catch + log (P-33~35) | 충족 여부 = Runtime Pending (NOT-EV-04 / 05). 관측 / 유실 허용 수준 = G03-D-22 | Contract 요구사항과 Runtime 충족 여부를 다른 축(Disposition / Runtime Result)에 기록 |
| **HR-SUB-01** "MUST / MIGRATION_PRESERVE" | — | — | — | Priority 칸에 Preserve 축 값을 같이 쓴 **축 혼합 표기**. Priority = MUST, Preserve = YES로 분리 표기 제안 (§9.2) |
| **G01-P-14, 30, 32~35** | — | 현행 Event 구현 보존 (IMPLEMENTATION) | G03-D-22 | Business 의미는 C-54 / C-55로만 검사. 구현을 정상 Contract로 승격하지 않음 |

## 6.2 원문이 Contract / Preserve / Decision을 한 ID에 함께 기록한 경우

프롬프트 §5.1의 SCH-MIG-15 방식을 같은 성격의 다른 ID에도 적용했다. 원본 ID는 그대로 두고 부분만 나눈다.

| 원본 ID | Contract 부분 (Primary Canonical) | Preserve 부분 | Decision 부분 | 근거 |
|---|---|---|---|---|
| AUTH-MIG-05 | 유효 Refresh → 새 Access (C-04) | Token 관리 방식 (G01-P-01) | G03-D-23 / 24 | A-01 §6.1 |
| WP-MIG-04 | 현재 중복 Membership 금지 (C-10) | row 존재 시 Join 차단 (G01-P-03) | 재가입 방식 (G03-D-13) | A-02 §7 |
| PAT-MIG-07 | Hard Delete (C-17) | 종료 Member Pattern 삭제 허용 (G01-P-08) | G03-D-14 | A-03 §8 |
| SCH-MIG-15 | 알림 Flow의 Business 의미 (C-32 → C-54 ①) | Event 구조 (G01-P-14 / 32 / 33) | G03-D-22 | A-04 L1100-1108 |
| UNA-MIG-11 | Candidate 제외 + 재검증 수행 (C-40) | Hard Block 실패 처리 (G01-P-19) | G03-D-21 | A-05 HR-UNA |
| SUB-MIG-01 | (정상 Contract 여부 미결정, C-41 DECISION_PENDING) | 상태 모델 (G01-P-20 / 21) | G03-D-17 | A-06 HR-SUB-02 |
| SUB-MIG-04 | 원자적 생성 + 중복 금지 Invariant (C-44) | DB UNIQUE (G01-P-31) | — | A-06 §27.3 |
| SUB-MIG-05 | 기본 조회 의미 (C-45) | Response-only EXPIRED (P-22), `leftAt` 필터 없음 (P-26 / 27) | G03-D-19, G03-D-15 | A-06 HR-SUB-07 |
| SUB-MIG-09 | 현재 Manager Close Flow (C-49) | 재OPEN 없음 (G01-P-21) | G03-D-17 | A-06 §27.7 |
| NOT-MIG-02 | 본인 수신 목록 (C-52) | `leftAt == null` 조건 (P-39), tie-breaker (P-41) | G03-D-16 | A-07 §14.1 |
| NOT-MIG-03 | 순차 멱등 + 최초 readAt (C-53) | 403 / 404, `leftAt` 거부 (P-38 / 39), Lock 방식 (P-40) | G03-D-10, G03-D-16 | A-07 §14.1 |
| NOT-MIG-06 | Domain SoT 보호 요구사항 (C-55) | 구현 방식 (P-33~35) | G03-D-22 | A-07 §9.1 |

이 12개 ID는 G-01 §4.3의 "Business Contract 성격 68개" 안에 들어 있다. 68이라는 집계는 틀리지 않았지만, 그중 12개는 Preserve / Decision 부분을 함께 가진다 (G03-NF-01).

응답 세부값 원칙: AUTH-MIG-02, WP-MIG-07, UNA-MIG-02 / 06 / 07 / 09처럼 G-01 "추가 결정" 열에 COMMON 응답 형식만 걸린 ID는 위 표에 넣지 않았다. 이들은 Contract 본문(거부 / 성공 여부, 상태 변화)과 응답 세부값(Status / Code / Success code / 정렬 2차 키)이 처음부터 다른 축이며, 세부값은 Preserve(§5)로 보호한다.

## 6.3 Defect Candidate를 정상 기대값으로 채택하지 않는 경계

AI-GUIDE §16, A-06 §25.29~25.30, A-07 §13의 원칙을 Canonical Contract에 연결한다. Verified Defect는 **0**이며 G-03은 Defect State를 바꾸지 않았다.

| Defect Candidate (원 ID) | 영향 Canonical Contract | 정상 Contract 기대값 | 기대값으로 쓰지 않는 결과 (후보가 가정하는 비정상) | Test 경로 |
|---|---|---|---|---|
| WP-DEFECT-CANDIDATE-01 | C-05 (+C-07) | 보호 API는 미인증 요청을 허용하지 않음 | `/workplaces/**` 하위 무토큰 요청이 401이 아닌 결과로 끝나는 것 | AUTH-EV-01 Harness 공유, **Workplace Endpoint별** HTTP 결과로 개별 판정 (WP-EV-03) |
| PAT-DEFECT-CANDIDATE-01 | C-05 (+C-14) | 〃 | Pattern API 무토큰 요청이 401이 아닌 결과로 끝나는 것 | AUTH-EV-01 Harness 공유, **Pattern Endpoint별** 개별 판정 (PAT-EV-02). WP-DC-01과 함께 Verified / Rejected 된다고 가정하지 않음 |
| AUTH-DEFECT-CANDIDATE-01 | (채택 전 목표 Contract: Logout) | 현재 확정 Contract 없음 | Logout 후 남은 Refresh 재사용 결과를 어느 쪽 기대값으로도 고정하지 않음 | AUTH-EV-03 Characterization, G03-D-23 결정 필요 |
| WP-DEFECT-CANDIDATE-02 | C-10 | 현재 중복 Membership 금지 | 동시 Join 중복 row / 500 | WP-EV-04 (PRE_HARDENING) |
| PAT-DEFECT-CANDIDATE-02 | C-16 | 같은 Member + DayOfWeek 겹침 금지 | 동시 등록으로 겹치는 Pattern 2건 | PAT-EV-04 |
| SCH-DC-01 (+파생 NOT-DC-02) | C-27, C-54 ① | 순차 Publish 검증, Publish 당시 실제 담당자에게 알림 | 오래된 Shift 목록으로 검증 / 공개 / 알림 | SCH-EV-03 (⊃ NOT-EV-06) |
| SCH-DC-02 | C-30 | User 전역 공식 Shift 충돌 금지 | 겹치는 Official Shift 2건 | SCH-EV-07 |
| SCH-DC-03 | C-26, C-27 | 순차 검증 | 동시 CREATE / UPDATE로 겹치는 DRAFT Shift | SCH-EV-03 Harness (DEFER) |
| UNA-DC-01 | C-24, C-27 | Publish 시 Unavailable Warning + confirm | 새 Unavailable 충돌 누락 | UNA-EV-04 / 05 (SCH-EV-03 Harness 공유) |
| UNA-DC-02 | C-25 | 공식 Shift 충돌 Warning | 사라진 충돌로 Warning | UNA-EV-06 (DEFER) |
| SUB-DC-01 | C-43, C-46 | 겹치는 Active Substitute Promise 금지 | 두 Request 모두 ACCEPTED (A-06 §25.29 예시) | SUB-EV-05 |
| SUB-DC-02 (+파생 NOT-DC-01) | C-47, C-54 ⑥ | 마지막 거절 시 CLOSED + 전원 거절 알림 | Request OPEN 고착, 알림 없음 | SUB-EV-06 (⊃ NOT-EV-07) |
| SUB-DC-03 | C-40, C-46, C-48, C-30 | 재검증 수행, User 전역 충돌 금지, 원자적 승인 | 최신 상태를 보지 못한 ACCEPT / APPROVE 결과 | SUB-EV-08(a)(b)(c) |
| SUB-DC-04 | C-46, C-47 | 상태 전이 규칙 | REJECT 직후 ACCEPT 이중 제출 결과 | SUB-EV-11 (DEFER) |

원 Defect Candidate 16개(파생 2 포함)가 모두 위 표에 있다. WP-DC-01 / PAT-DC-01은 같은 원인 후보를 공유하지만 결과는 Endpoint별로 따로 판정한다 (G-02 §7.1 Follow-up 판정 유지).

---

# 7. Global Decision Register

## 7.1 작성 원칙

- G-01 §9의 원본 Decision ID 29개를 모두 Canonical Decision에 연결했다.
- **집계 축 3개를 구분한다**: 원본 ID 29개 / Canonical 질문 28개 (`G03-D-01~28`) / Decision 묶음 10개 (G01-DG-01~10). Alias 병합은 UNA-DN-04 = SUB-DN-02 1건뿐이다. 28은 이번 블록에서 아래 표로 직접 검산한 값이다 (§7.5).
- 같은 질문으로 볼 여지가 있지만 원문이 별도 ID로 둔 항목(예: 403 / 404 노출)은 합치지 않고 `MERGE_CANDIDATE`로만 표시했다. 합칠지는 COMMON 결정 시 사람이 정한다.
- **선택지 열에는 원문에 있는 것만 적었다.** 원문에 선택지가 없으면 `NO_OPTIONS_IN_SOURCE`로 두고 만들어 내지 않았다.
- 어느 선택지도 채택하지 않았다. Decision Status는 29개 모두 `PENDING`이다.

## 7.2 Register — 질문 / 선택지 / AS-IS

| G03 ID | 원본 ID / Alias | 묶음 | Decision Type (원문) | 미결정 질문 | 선택지 (원문에 있는 것만) | 선택지별 영향 (근거 범위) | 기존 AS-IS | 근거 |
|---|---|---|---|---|---|---|---|---|
| G03-D-01 | COMMON-DEC-CANDIDATE-01 (= A-01 DN-06) | DG-01 | CROSS_DOMAIN_DECISION | 성공 응답 `code` 규칙 | 원문 예시: `"200"` / `"201"` / Domain Success Code | 원문 미기재 | `"200"` / `"201"` 등 | A-01 §13 |
| G03-D-02 | PAT-DEC-02 | DG-01 | CROSS_DOMAIN_DECISION → COMMON | Pattern 권한 실패 Error Contract | NO_OPTIONS_IN_SOURCE (Auth / Workplace / Pattern / Schedule / Substitute / Notification과 함께 결정) | 원문 미기재 | 400 / 400 / 403 | A-03 PAT-DEC-02 |
| G03-D-03 | SCH-DEC-01 | DG-01 | COMMON 결정 | ErrorCode Prefix / 400·409 | NO_OPTIONS_IN_SOURCE (`SCH-*` / `SFT-*`, 400 / 409를 전체 Error Contract와 함께 결정) | 원문 미기재 | `SCH-*` / `SFT-*` 혼용 | A-04 SCH-DEC-01 |
| G03-D-04 | SCH-DEC-02 | DG-01 | COMMON 결정 | 다른 Workplace / User Resource 접근 시 노출 정책 | 400 / 403 / 404 | 원문 미기재 | Domain별 상이 | A-04 SCH-DEC-02 |
| G03-D-05 | UNA-DN-01 | DG-01 | CROSS_DOMAIN_DECISION → COMMON | Warning + confirm 응답 계약 | 원문 축: 400 vs 409 | 원문 미기재 | 400 `OFFICIAL_SHIFT_CONFLICT` / 409 `SFT-015` | A-05 UNA-DN-01 |
| G03-D-06 | UNA-DN-02 | DG-01 | CROSS_DOMAIN_DECISION → COMMON | 다른 User Resource 403 / 404 | 403 / 404 | 원문 미기재 | 없는 ID 404 → 타인 403 | A-05 UNA-DN-02 |
| G03-D-07 | UNA-DN-03 | DG-01 | CROSS_DOMAIN_DECISION → COMMON ErrorCode Catalog | Unavailable ErrorCode 형식 / 메시지 | NO_OPTIONS_IN_SOURCE | 원문 미기재 | 혼합 형식, DELETE "수정할 수 없습니다" | A-05 UNA-DN-03 |
| G03-D-08 | UNA-DN-05 | DG-01 (일부) | DEFER_PRODUCT_DECISION | Success code / 메시지 / 같은 startAt 2차 정렬 / DELETE 응답 | DELETE: 200 / 204. 나머지 NO_OPTIONS_IN_SOURCE | 원문 미기재 | 현행 값 | A-05 UNA-DN-05 |
| G03-D-09 | SUB-DN-04 | DG-01 | CROSS_DOMAIN_DECISION → COMMON | Substitute Error Contract | 원문 축: 400 vs 409, 403 vs 404, Prefix, 다른 Domain 코드 재사용, Validation 순서, 이상 데이터 Error, Success code | 원문 미기재 | 현행 | A-06 §27.15 |
| G03-D-10 | NOT-DN-03 | DG-01 | CROSS_DOMAIN_DECISION | 남의 알림 403 / 404, Success code | A 현행 / B 404 통일 / C 403 통일 | ErrorCode / Test 기대값 변경 | 404 → 403, `"200"` | A-07 §20 |
| G03-D-11 | WP-DEC-01 | DG-02 | DECISION_PENDING | Membership 종료를 3차 Scope로 채택할 것인가 | 채택 여부 (원문 질문 형식) | 채택 시 WP-DEC-02 범위 전체 결정 필요 | 종료 Flow 없음 | A-02 WP-DEC-01 |
| G03-D-12 | WP-DEC-02 | DG-02 | DECISION_PENDING | 종료 채택 시 최소 범위 | NO_OPTIONS_IN_SOURCE (결정 항목 목록: 종료 주체, 본인 퇴사, MANAGER의 종료, MANAGER 본인 종료, 마지막 MANAGER, Pattern / DRAFT / PUBLISHED / Substitute / Notification 처리) | "부분적으로 일부 Domain만 변경하지 않는다" (원문) | — | A-02 WP-DEC-02 |
| G03-D-13 | WP-DEC-03 | DG-02 | DEFER_PRODUCT_DECISION (종료 채택 시 CROSS_DOMAIN) | 재가입 방식 | 기존 WorkplaceMember 재활성화 vs 새 WorkplaceMember 생성 | Unique Constraint, 과거 Shift 참조, Pattern, Substitute, Notification에 영향 (원문 목록) | 기존 row 있으면 Join 차단 | A-02 §12 |
| G03-D-14 | PAT-DEC-03 | DG-02 | DECISION_PENDING → A-02 | 종료 시 Pattern 처리 | A Hard Delete / B 남기고 Current Membership Query로 제외 / C 남기되 재가입 시 별도 정리 (원문 "후보", 확정 안 함) | `leftAt` 복원 방식이면 과거 Pattern이 다시 활성화될 가능성 (원문) | 자동 정리 없음 | A-03 PAT-DEC-03 |
| G03-D-15 | SUB-DN-05 | DG-02 | CROSS_DOMAIN_DECISION → A-02 | Substitute 관련 Membership Lifecycle | NO_OPTIONS_IN_SOURCE (항목: Requester / PENDING Candidate / ACCEPTED Member 종료, 진행 중 Request, 과거 조회, Official Shift · `leftAt`) | 원문 미기재 | 별도 처리 없음 | A-06 §27.16 |
| G03-D-16 | NOT-DN-01 | DG-02 | HUMAN_DECISION + CROSS_DOMAIN_DECISION | 종료 Membership의 과거 알림 노출 / 읽음, 신규 수신 | A 현행 / B 노출 + 읽음 허용 / C 읽기 전용 노출 | B·C는 Query·권한 변경, A-02 정책 연동 | 목록 제외 + 읽음 403, 일부 경로 종료 Member 수신 가능 | A-07 §20 |
| G03-D-17 | SUB-DN-01 | DG-03 | DEFER_PRODUCT_DECISION | Request 종료 상태 / 어휘 | 현재 OPEN / ACCEPTED / APPROVED / CLOSED vs WITHDRAWN / UNFILLED / 수락 철회 / 재OPEN | 원문 미기재 (Source 비교는 G-02 §10.1 #1) | OPEN / ACCEPTED / APPROVED / CLOSED + closeReason | A-06 §27.12 |
| G03-D-18 | NOT-DN-05 | DG-03 | DEFER_PRODUCT_DECISION | 철회 / 관리자 거절 / 재OPEN 흐름과 알림 | A 도입 안 함 / B P1 도입 | 상태 Enum / API 추가 | Flow 없음 | A-07 §20 |
| G03-D-19 | SUB-DN-03 | DG-04 | DEFER_PRODUCT_DECISION | Expiry | 원문 질문: DB CLOSED / EXPIRED 전이 필요 여부, Scheduler / Lazy / Hybrid, Expiry Notification 필요 여부 | 원문 미기재. API-SUB p.29는 방식을 "구현 단계에서 결정"으로 둠 (`SR(G-02)`) | Response-only EXPIRED, DB 전이 없음 | A-06 §27.14 |
| G03-D-20 | NOT-DN-02 | DG-04 | DEFER_PRODUCT_DECISION | Expiry 도입 시 만료 알림 | A Expiry와 함께 / B Expiry만 | Scheduler 등 Expiry 방식 종속 | 전이 / 알림 없음, Enum만 | A-07 §20 |
| G03-D-21 | **UNA-DN-04 = SUB-DN-02** (Alias), Related XD: A05-XD-02 | DG-05 | CROSS_DOMAIN_DECISION (A-05 / A-06 공동) | SUB-03 ACCEPT / SUB-04 APPROVE의 Unavailable 충돌 처리 | Hard Block vs Warning + confirm | 원문 미기재 | Hard Block (`SUB-006` 409) | A-05 UNA-DN-04, A-06 §27.13 / §28.3 |
| G03-D-22 | NOT-DN-04 | DG-06 | HUMAN_DECISION + NEEDS_EVIDENCE | 알림 유실 허용 수준과 실패 관측 방식 | A 현행(Log) / B 실패 식별 기록 / C 재처리 수단 | B·C는 구조와 Test 확대. A-07 추천: NOT-EV-04 / 08 결과 전에는 A 유지가 Migration 원칙과 맞음, 방식 선택은 추천하지 않음 | AFTER_COMMIT + REQUIRES_NEW + Log, 재처리 없음 | A-07 §20 |
| G03-D-23 | AUTH-DEC-01 (= A-01 DN-01, SR-AUTH-01) | DG-07 | (A-01: RECLASSIFY → SERVICE_READINESS_CANDIDATE) | 서버 Logout을 목표 Security Contract로 채택할 것인가 | 채택 여부 (원문: "Logout 이후 기존 Refresh Token은 재사용할 수 없어야 한다"를 목표 Contract로 채택하는 경우 MUST) | 원문 미기재 | Backend Logout 없음 | A-01 §12 |
| G03-D-24 | AUTH-DEC-02 (= A-01 DN-04, SR-AUTH-05) | DG-07 | DEFER_PRODUCT_DECISION | 다중 기기 동시 Refresh 지원 | NO_OPTIONS_IN_SOURCE (원문: 필요하면 단일 Token 구조를 제약으로 문서화하고 TO-BE로 미룸) | 원문 미기재 | 단일 refresh_token | A-01 §12 |
| G03-D-25 | PAT-DEC-01 | DG-08 | Preserve + CROSS_DOMAIN_DECISION | Canonical API Method | PUT / PATCH (원문 추천 후보: PUT. 최종은 API 문서 정리 단계) | PUT 근거: Backend / Frontend PUT, 전체 필드 필수, 전체 값 교체 (원문) | Backend / Frontend PUT | A-03 PAT-DEC-01 |
| G03-D-26 | SCH-DEC-03 | DG-09 | Preserve + DEFER_PRODUCT_DECISION | `CANCELLED` 장기 의미 | 원문 질문: 장기 의미, P1 Cancel 포함 여부, SCHEDULED → CANCELLED 전이 도입 여부 | 원문 미기재 | Enum만, 쓰기 경로 없음 | A-04 SCH-DEC-03 |
| G03-D-27 | SCH-DEC-04 | DG-09 | SR-SCH-03 ACCEPT / DEFER | PUBLISHED 이후 정정 범위 | 이번 프로젝트 포함 / SHOULD / DEFER | 원문 미기재 | 정정 수단 없음 (P1) | A-04 SCH-DEC-04 |
| G03-D-28 | OPS-DEC-CANDIDATE-01 | DG-10 | O-01 이관 | 운영 Swagger / API Docs / H2 Console 노출 범위 | NO_OPTIONS_IN_SOURCE | 원문 미기재 | 개발용 permitAll 혼재 | A-01 §13 |

## 7.3 Register — 관계 / Owner / 상태

| G03 ID | 적용 Contract / Preserve | 필요 선행 Decision | 필요 Runtime Evidence | Owner (원문) | Priority / Impact / Timing (원문) | Decision Status | 결정 후 갱신할 문서 | Layer (원문 후보) | Merge 표시 |
|---|---|---|---|---|---|---|---|---|---|
| G03-D-01 | C-34, C-37 / P-13, P-18, P-24, P-38 | — | — | COMMON | DEFER / — / COMMON_DECISION | PENDING | COMMON Response 규칙 (Decision Log), 해당 Preserve 해제 기록 | L0 문서 / L1 이후 | 성공 code 부분은 D-08 / D-09 / D-10과 MERGE_CANDIDATE |
| G03-D-02 | C-14 / P-06, P-07 | — | PAT-EV-02 | COMMON | DEFER / — / COMMON_DECISION | PENDING | COMMON Error Catalog, A-03 관련 Preserve | L0 문서 / L1 이후 | — |
| G03-D-03 | C-23, C-27 / P-07, P-09 | — | — | COMMON | DEFER / — / COMMON_DECISION | PENDING | COMMON Error Catalog | L0 문서 / L1 이후 | — |
| G03-D-04 | C-22, C-29 / P-10, P-11 | — | — | COMMON | DEFER / — / COMMON_DECISION | PENDING | COMMON Security / Resource 정책 | L0 문서 / L1 이후 | D-06 / D-10과 MERGE_CANDIDATE (403 / 404 노출) |
| G03-D-05 | C-24, C-25 / P-17 | — | UNA-EV-02 | COMMON | SHOULD / — / COMMON_DECISION | PENDING | COMMON Error Contract | L0 문서 / L1 이후 | — |
| G03-D-06 | C-39 / P-17 | — | UNA-EV-01 | COMMON | SHOULD / — / COMMON_DECISION | PENDING | 〃 | 〃 | D-04 / D-10과 MERGE_CANDIDATE |
| G03-D-07 | C-35, C-36, C-39 / P-17 | — | — | COMMON | SHOULD / — / COMMON_DECISION | PENDING | COMMON ErrorCode Catalog | 〃 | — |
| G03-D-08 | C-34, C-37 / P-18 | — | — | COMMON | DEFER / — / — (원문 Timing 없음) | PENDING | API-UNA 응답 표기 (Decision Log 경유) | L1 이후 | 성공 code 부분은 D-01과 MERGE_CANDIDATE |
| G03-D-09 | C-42~C-49 / P-24 | — | SUB-EV-03 | COMMON | — (원문 미기재) / — / COMMON_DECISION | PENDING | COMMON Error Contract | L0 문서 / L1 이후 | — |
| G03-D-10 | C-53 / P-38 | — | NOT-EV-01 | COMMON / 사람 | SHOULD (결정) / — / COMMON_DECISION | PENDING | COMMON Error Contract | 〃 | D-04 / D-06과 MERGE_CANDIDATE |
| G03-D-11 | C-11 / P-04 | — ("A-07 확인 후" 조건은 충족, G-02 §5.3) | — | 팀 / A-02 | CONDITIONAL MUST / — / A-07 이후 | PENDING | A-02 결정 기록, G-05 Layer 3 Scope | L3 (채택 시 L2 연계) | — |
| G03-D-12 | C-11, C-14, C-22, C-42 / P-04 | G03-D-11 | SCH-EV-05, SUB-EV-12, NOT-EV-09 (CONDITIONAL) | A-02 + 관련 Domain | (WP-DEC-01 종속) / — / WP-DEC-01 이후 | PENDING | 관련 Domain Decision Log | L3 | — |
| G03-D-13 | C-10 / P-03 | G03-D-11 | WP-EV-05 (CONDITIONAL) | A-02 | DEFER / — / 조건부 | PENDING | A-02 결정 기록 | L3 | — |
| G03-D-14 | C-17 / P-08 | G03-D-11~13 | — | A-02 | CONDITIONAL MUST (SR-PAT-01) / — / 조건부 | PENDING | A-03 / A-02 결정 기록 | L3 | — |
| G03-D-15 | C-42, C-45, C-29 / P-25~28 | G03-D-11 | SUB-EV-12 (DEFER) | A-02 | CONDITIONAL MUST (SR-SUB-05) / — / 조건부 | PENDING | A-06 / A-02 결정 기록 | L3 | A05-XD-05, SR-UNA-03, SR-SCH-04와 같은 Lifecycle 묶음 |
| G03-D-16 | C-52, C-53, C-54 / P-39 | G03-D-11 | NOT-EV-09 (CONDITIONAL) | A-02 / 사람 | DEFER (채택 시 SHOULD 재평가) / — / CONDITIONAL | PENDING | A-07 / A-02 결정 기록 | L3 | Related XD: A02-XD-05 ↔ A07-XD-03 (양방향 한 질문) |
| G03-D-17 | C-41, C-49 / P-20, P-21 | — | — | 팀 (A-06) | (Scope DEFER) / — / DEFERRED | PENDING | Decision Log 후 P0 정리본 정리 (Source 원본은 덮어쓰지 않음, SOURCE-INDEX §9-10) | L3 | Related XD: A06-XD-06 |
| G03-D-18 | C-54 / (현행 Flow 없음) | G03-D-17 (원문 Owner = SUB-DN-01 Owner) | — | A-06 SUB-DN-01 Owner | DEFER / — / DEFERRED | PENDING | 〃, G02-NF-01 중 §19 "관리자 거절 / 재OPEN" 알림 부분 정리 | L3 | ③ 후보 0명 알림은 이 질문 범위 밖 (§3.8.3) |
| G03-D-19 | C-45, C-50 / P-22, P-23, P-37 | — | SUB-EV-02 (현행 기록) | 팀 (A-06) | DEFER (SR-SUB-04) / — / DEFERRED | PENDING | Decision Log 후 P0 정리본 / COMMON / API-SUB 정리 | L3 | Related XD: A06-XD-06 |
| G03-D-20 | C-54 / P-37 | G03-D-19 | NOT-EV-10 (CONDITIONAL) | A-06 SUB-DN-03 Owner | DEFER / — / DEFERRED | PENDING | A-07 / A-06 결정 기록 | L3 | — |
| G03-D-21 | C-40, C-46, C-48 / P-19 | — | SUB-EV-08(a) | 원문 표기 3종 보존: A-05 "A-05 / A-06 공동 Product Decision", A-06 §27.13 `CROSS_DOMAIN_DECISION`, A-06 §28.3 `CROSS_DOMAIN_DECISION → A-05` (G02-NF-03). G-03 제안 표기: "사람 (A-05 / A-06 공동)" — `PROPOSED / APPROVAL_PENDING` | MUST (결정) / — / COMMON_DECISION | PENDING | Decision Log 후 P0 정리본 §16 "재확인" 정리 | L0 결정 / L2 | — |
| G03-D-22 | C-55, C-32, C-54 / P-14, P-30, P-32~35 | — | NOT-EV-04, NOT-EV-08 | COMMON / 사람 | SHOULD (결정) / — / **AFTER_RUNTIME** | PENDING | COMMON 실패 정책 (Decision Log). Retry / Outbox 등은 **채택되지 않음** | L2 / L3 | Related: A07-XD-01, A-04 Final SR Scope "A-07 Notification Failure / Retry", SR-NOT-01 |
| G03-D-23 | (목표 Contract 후보) / P-01 | — | AUTH-EV-03 | 팀 | CONDITIONAL MUST / — / 채택 시 | PENDING | A-01 결정 기록, Security 목표 Contract | L2 / L3 | — |
| G03-D-24 | C-04 / P-01 | G03-D-23 | — | 팀 | DEFER / — / LATER, OUT_OF_CORE | PENDING | A-01 제약 문서 | L3 | — |
| G03-D-25 | C-14, C-16 / P-05 | — | — | API 문서 / COMMON | DEFER / — / Documentation / COMMON | PENDING | API-PAT 문서 (Decision Log 경유) | L0 문서 | — |
| G03-D-26 | C-29 / P-12 | — | — | 팀 | DEFER / — / Migration 이후 | PENDING | A-04 결정 기록 | L3 | — |
| G03-D-27 | C-31 | — | — | 팀 | DEFER (채택 시 SHOULD) / — / Service Readiness 통합 시 | PENDING | A-04 / G-05 Scope | L3 | — |
| G03-D-28 | (Contract 없음) / P-02 일부 | — | AUTH-NE-02 | O-01 | (원문 미기재) / — / OPERATIONS | PENDING | O-01 운영 문서 | L3 | — |

Impact 열은 29개 모두 원문 미기재다 (G-01 §9). G-03은 값을 채우지 않았다.

## 7.4 필수 Decision 묶음 처리 (프롬프트 §10)

| 묶음 | 처리 |
|---|---|
| COMMON Error / Response (G01-DG-01) | D-01~D-10을 하나의 COMMON 묶음으로 통합. 관련 XD (A02-XD-07, A03-XD-05, A04-XD-04, A05-XD-03, A06-XD-01, A07-XD-02)와 A-01 G-01 / SR-AUTH-03 (AccessDeniedHandler, CODE_GAP)을 Related로 연결. **현재 HTTP Status / ErrorCode는 바꾸지 않는다** (Preserve P-02, 06, 07, 09, 10, 11, 13, 17, 18, 24, 38) |
| Membership Lifecycle (G01-DG-02) | 선행 관계: D-11 (채택 여부) → D-12 (범위) → D-14 / D-15 / D-16 / A05-XD-05 / SR-SCH-04 / SR-UNA-03 (Domain별 의미) → D-13 (재가입). G-02 §5.3 순서를 옮긴 것이다. **A-07 검토가 완료됐다는 사실(선행 조건 충족)과 Lifecycle 정책이 결정됐다는 사실은 다르다.** 정책은 미결정 |
| Substitute State / Expiry (DG-03 / 04) | D-17 (상태 어휘), D-19 (Expiry), D-20 (만료 알림)을 분리. **Response-only EXPIRED (P-22)와 DB 상태 전이 (P-23 / D-19)는 별도 계약 후보로 관리**한다 |
| Unavailable 정책 (DG-05) | SUB-DN-02 = UNA-DN-04를 하나의 질문 D-21로 연결하고, A-05 / A-06 공동 결정이라는 provenance와 원문 Owner 표기 3종을 보존 |
| Notification 실패 / 유실 (DG-06) | D-22 Timing **AFTER_RUNTIME** 유지. Retry / Outbox 등을 채택했다고 쓰지 않음 (SR-NOT-01 원문: "후보 방식일 뿐") |
| Source 현재성 | G-02 §10과 G02-NF-01~03을 반영 (§10.2). P0 정리본과 COMMON / API 차이를 G-03이 해소하지 않음. D-17 / D-19 / D-21 / D-18 결정 후 Source 정리 대상으로만 기록 |

## 7.5 Decision Coverage 검산

| 항목 | 결과 |
|---|---|
| 원본 Decision ID (G-01 §9) | **29** (Script 추출, 중복 0): A-01 4 / A-02 3 / A-03 3 / A-04 4 / A-05 5 / A-06 5 / A-07 5 |
| Register에 연결된 원본 ID | **29** (D-21에 2개, 나머지 27개 Canonical에 1개씩) |
| 누락 / 중복 연결 | **0 / 0** |
| Alias 병합 전 / 후 | 29 → **28** (UNA-DN-04 = SUB-DN-02) |
| Decision 묶음 | **10** (DG-01: 10 / DG-02: 6 / DG-03: 2 / DG-04: 2 / DG-05: 1 / DG-06: 1 / DG-07: 2 / DG-08: 1 / DG-09: 2 / DG-10: 1 = 28) |
| MERGE_CANDIDATE (합치지 않음) | 2그룹: 403 / 404 노출 (D-04, D-06, D-10), 성공 code (D-01, D-08 일부, D-09 일부, D-10 일부) |
| 이름 충돌 | 0. A-01의 접두어 없는 `DN-01~06`은 Alias이며 A-05 / A-06 / A-07의 `UNA-DN` / `SUB-DN` / `NOT-DN`과 충돌하지 않음 |
| 원문 명칭이 Decision이 아닌 것 | 0. 단 `COMMON-DEC-CANDIDATE-01`, `OPS-DEC-CANDIDATE-01`은 원문 이름 자체가 "후보"이며, OPS는 O-01 이관 항목 |
| Decision ID 없는 관련 SR 의사결정 대상 | SR-AUTH-04 / 06, SR-WP-04 / 05, SR-UNA-02 / 03, SR-SCH-04 → Related로만 기록, 개수에 넣지 않음 |
| Decision Status | PENDING 29 / DECIDED 0 |

---

# 8. Decision Dependency / Blocking Matrix

## 8.1 판정 원칙

- 원문 "Migration 차단" 값은 29개 모두 **아니오**다 (G-01 §9). 새 증거가 없으므로 차단으로 올리지 않았다.
- A-06 §25.32 Gate A: "Migration 시작 전에 모든 Product Decision을 끝낼 필요는 없다. 다만 Decision Pending 항목에 대해서는 현재 Behavior를 어떻게 Preserve할 것인지가 명확해야 한다." 그래서 `NON_BLOCKING_PRESERVE_AS_IS`는 **임시 보존 규칙(INTERIM_BEHAVIOR)이 원문에 있을 때만** 붙였다.
- 테스트 기대값을 정의할 수 없어 `UNDETERMINED`가 되는 Decision은 없었다. 모든 Decision이 결정 전 AS-IS 보존 범위를 원문에 갖고 있고, 그 범위가 Regression 기대값의 출처(Java Baseline 실측)를 정해 주기 때문이다. 단 기대값의 **실제 값**은 Runtime 전까지 없다 (Runtime Result 축).
- 원문이 결정 시점을 명시한 경우만 그 시점을 적었다. 없으면 `UNSPECIFIED (원문 표현)`.

## 8.2 Blocking Matrix (28 Canonical / 29 ID)

| G03 ID | 원본 ID | BLOCKING_STATUS | RATIONALE (원본 근거) | DECISION_DEADLINE | INTERIM_BEHAVIOR (기존 보존 판단) | APPROVAL_OWNER / DECISION_STATUS |
|---|---|---|---|---|---|---|
| G03-D-01 | COMMON-DEC-CANDIDATE-01 | NON_BLOCKING_PRESERVE_AS_IS | G-01 §9 "아니오", A-01 §17.8 "COMMON Success / Error Code 체계 변경"은 Migration과 분리 | COMMON_DECISION (원문 Timing). 원문 Layer "L0 문서 / L1 이후" | 현재 Success code 유지 (P-13, P-18, P-24, P-38) | COMMON / PENDING |
| G03-D-02 | PAT-DEC-02 | NON_BLOCKING_PRESERVE_AS_IS | 〃 | COMMON_DECISION | 400 / 400 / 403 유지 (P-06) | COMMON / PENDING |
| G03-D-03 | SCH-DEC-01 | NON_BLOCKING_PRESERVE_AS_IS | A-04 §20.12 "ErrorCode 체계 전면 통합"을 Migration과 동시에 하지 않음 | COMMON_DECISION | P-09 | COMMON / PENDING |
| G03-D-04 | SCH-DEC-02 | NON_BLOCKING_PRESERVE_AS_IS | A-04 §20.12 "Security Resource 노출 정책 변경" 금지 | COMMON_DECISION | P-10, P-11 | COMMON / PENDING |
| G03-D-05 | UNA-DN-01 | NON_BLOCKING_PRESERVE_AS_IS | A-05 "Migration 중 임의 변경하지 않는다" | COMMON_DECISION | P-17 | COMMON / PENDING |
| G03-D-06 | UNA-DN-02 | NON_BLOCKING_PRESERVE_AS_IS | G-01 §9 "아니오" | COMMON_DECISION | P-17 (404 → 403 순서) | COMMON / PENDING |
| G03-D-07 | UNA-DN-03 | NON_BLOCKING_PRESERVE_AS_IS | A-05 "Migration에서 임의 변경하지 않는다" | COMMON_DECISION | P-17 | COMMON / PENDING |
| G03-D-08 | UNA-DN-05 | NON_BLOCKING_PRESERVE_AS_IS | A-05 "핵심 P0 Business Contract가 아니다", 현행 값 유지 | UNSPECIFIED (원문 Timing 없음, Layer "L1 이후") | P-18 | COMMON / PENDING |
| G03-D-09 | SUB-DN-04 | NON_BLOCKING_PRESERVE_AS_IS | A-06 §27.10 "현재 ErrorCode / HTTP Status: Migration Preserve YES", §27.11 "새 Error Contract" 추가 금지 | COMMON_DECISION | P-24 | COMMON / PENDING |
| G03-D-10 | NOT-DN-03 | NON_BLOCKING_PRESERVE_AS_IS | A-07 §20 "Migration 차단: 아니오", 결정 전 보존 = "현행 코드 / 순서" | COMMON_DECISION | P-38 | COMMON / 사람 / PENDING |
| G03-D-11 | WP-DEC-01 | NON_BLOCKING_PRESERVE_AS_IS | A-02 §12 "현재 Kotlin Migration에서는 결정하지 않는다", A-04 §20.12 "Membership 종료 기능 추가" 금지 | UNSPECIFIED (원문 "A-07 이후" — 조건 충족, 시점 미지정) | P-04 (종료 Flow 미추가) | 팀 / A-02 / PENDING |
| G03-D-12 | WP-DEC-02 | NON_BLOCKING_PRESERVE_AS_IS | WP-DEC-01 종속 | UNSPECIFIED (원문 "WP-DEC-01 이후") | P-04 | A-02 + 관련 Domain / PENDING |
| G03-D-13 | WP-DEC-03 | NON_BLOCKING_PRESERVE_AS_IS | A-02 §12 "현재 Kotlin Migration에서는 결정하지 않는다" | UNSPECIFIED (원문 "조건부") | P-03 | A-02 / PENDING |
| G03-D-14 | PAT-DEC-03 | NON_BLOCKING_PRESERVE_AS_IS | A-03 "Pattern만 독립적으로 결정하지 않는다" | UNSPECIFIED (원문 "조건부") | P-08 | A-02 / PENDING |
| G03-D-15 | SUB-DN-05 | NON_BLOCKING_PRESERVE_AS_IS | A-06 §27.11 "Membership 종료 자동 Close / Candidate 자동 Reject" 추가 금지 | UNSPECIFIED (원문 "조건부") | P-25, P-26, P-27, P-28 | A-02 / PENDING |
| G03-D-16 | NOT-DN-01 | NON_BLOCKING_PRESERVE_AS_IS | A-07 §20 "Migration 차단: 아니오", 결정 전 보존 = "leftAt 조건" | UNSPECIFIED (원문 CONDITIONAL) | P-39 | A-02 / 사람 / PENDING |
| G03-D-17 | SUB-DN-01 | NON_BLOCKING_PRESERVE_AS_IS (Kotlin 시작) + C-41 Contract 확정에는 CONDITIONAL_BLOCK | A-06 §27.10 상태 모델 Preserve YES, §27.11 WITHDRAWN / UNFILLED / 재OPEN 추가 금지. C-41의 정상 Contract 여부는 이 결정에 종속 | UNSPECIFIED (원문 DEFERRED) | P-20, P-21 | 팀 (A-06) / PENDING |
| G03-D-18 | NOT-DN-05 | NON_BLOCKING_PRESERVE_AS_IS | A-07 §20 "아니오", 결정 전 보존 = "현행" | UNSPECIFIED (원문 DEFERRED) | 새 Flow / 알림 미추가 (A-06 §27.11) | A-06 SUB-DN-01 Owner / PENDING |
| G03-D-19 | SUB-DN-03 | NON_BLOCKING_PRESERVE_AS_IS | A-06 §27.11 "Expiry Scheduler / Lazy Expiry State Mutation" 추가 금지 | UNSPECIFIED (원문 DEFERRED) | P-22, P-23 (Response-only EXPIRED와 DB 전이 없음은 별도 행) | 팀 (A-06) / PENDING |
| G03-D-20 | NOT-DN-02 | NON_BLOCKING_PRESERVE_AS_IS | A-07 §20, 결정 전 보존 = "Enum 유지, 추가 구현 금지" | UNSPECIFIED (원문 DEFERRED, D-19 종속) | P-37 | A-06 SUB-DN-03 Owner / PENDING |
| G03-D-21 | UNA-DN-04 = SUB-DN-02 | NON_BLOCKING_PRESERVE_AS_IS (Kotlin 시작) + Substitute 최종 Contract 확정에는 CONDITIONAL_BLOCK | A-05 UNA-DN-04: "Migration 중에는 현재 Hard Block을 임의 변경하지 않으며, 이 결정의 MUST 우선순위를 Migration 진행의 선행 차단 조건으로 해석하지 않는다" / "최종 Substitute Product Contract를 확정하기 전에 정책 결정을 완료해야 한다" | BEFORE_SUBSTITUTE_CONTRACT_FREEZE (원문 "최종 Substitute Product Contract 확정 전"), Timing COMMON_DECISION | P-19 (Hard Block 유지), A-06 §27.11 "Warning + confirm" 추가 금지 | 사람 (A-05 / A-06) / PENDING |
| G03-D-22 | NOT-DN-04 | NON_BLOCKING_PRESERVE_AS_IS | A-07 §20 "아니오", A-04 §20.12 "Notification Retry 신규 설계" 금지, AI-GUIDE §15 "Outbox 도입" 금지 | **AFTER_RUNTIME** (원문) | P-14, P-30, P-32~35 (A-07 추천: Runtime 전 A 현행 유지) | COMMON / 사람 / PENDING |
| G03-D-23 | AUTH-DEC-01 | NON_BLOCKING_PRESERVE_AS_IS | A-01 §17.7: Logout 목표 Contract 확인은 Kotlin Migration과 비교 **이후**, §17.8 "서버 Logout 신규 구현" 분리 | BEFORE_HARDENING (A-01 §17.7 순서: AUTH-EV-03 → Logout 목표 Contract 확인 → Hardening 설계) | P-01 | 팀 / PENDING |
| G03-D-24 | AUTH-DEC-02 | NON_BLOCKING_PRESERVE_AS_IS | A-01 §12 "이번 Migration 및 핵심 P0 Hardening의 blocker가 아니다" | UNSPECIFIED (원문 LATER / OUT_OF_CORE) | P-01 (단일 Token) | 팀 / PENDING |
| G03-D-25 | PAT-DEC-01 | NON_BLOCKING_PRESERVE_AS_IS | A-03 "Kotlin Migration에서는 현재 동작을 그대로 유지한다" | UNSPECIFIED (원문 "전체 API 문서 정리 단계") | P-05 | API 문서 / COMMON / PENDING |
| G03-D-26 | SCH-DEC-03 | NON_BLOCKING_PRESERVE_AS_IS | A-04 SCH-DEC-03 "Migration 이후 별도 결정", Enum 보존 | AFTER_MIGRATION (원문 "Migration 이후") | P-12 | 팀 / PENDING |
| G03-D-27 | SCH-DEC-04 | NON_BLOCKING_PRESERVE_AS_IS | A-04 "기존 P0 Contract가 아니다", §20.12 "PUBLISHED 정정 API 추가" 금지 | UNSPECIFIED (원문 "Service Readiness 통합 시") | 정정 수단 미추가 | 팀 / PENDING |
| G03-D-28 | OPS-DEC-CANDIDATE-01 | NON_BLOCKING_PRESERVE_AS_IS | G-01 §9 "아니오", A-01 §17.8 "Security Matcher 변경" 분리 | UNSPECIFIED (원문 OPERATIONS) | P-02 (Matcher 현행) | O-01 / PENDING |

## 8.3 집계

| BLOCKING_STATUS | 개수 (Canonical) |
|---|---|
| BLOCKS_PRE_MIGRATION_RUNTIME_GATE | 0 |
| BLOCKS_KOTLIN_START | 0 |
| NON_BLOCKING_PRESERVE_AS_IS | **28** |
| 그중 다른 축(Contract 확정)에 CONDITIONAL_BLOCK 병기 | 2 (D-17 → C-41, D-21 → Substitute 최종 Contract) |
| UNDETERMINED | 0 |

이 표는 *Migration Gate 설계의 입력*이다. `MIGRATION_READY`를 승인하지 않는다. Migration 시작 여부는 A-01 §17.7 / A-04 §20.11 / A-06 §25.32의 `MIGRATION_READY Gate`(핵심 Regression 준비 + PRE_MIGRATION Evidence)에서 판단하며, 그 Gate는 G-04 / G-05 이후의 일이다.

---

# 9. Taxonomy Normalization Proposal

G-02 §11은 공식 개정안이 아닌 제안이다. G-03은 그 제안과 이 문서에서 새로 쓴 축 값을 `ORIGINAL_VALUE / PROPOSED_CANONICAL_VALUE / APPROVAL_STATUS`로 정리한다. **승인되지 않은 변환은 모두 `PROPOSED / APPROVAL_PENDING`**이다. G-02의 `CONVENTION_SUPPORTED`는 "근거 수준" 열에 남기고 승인으로 읽지 않는다.

Priority / Impact / Timing / Destination / Preserve / Review Disposition을 한 칸에 섞지 않는다.

## 9.1 G-02 §11 제안의 승인 상태

| 축 | ORIGINAL_VALUE | PROPOSED_CANONICAL_VALUE | 근거 수준 (G-02) | APPROVAL_STATUS |
|---|---|---|---|---|
| Human Review | ACCEPT / RECLASSIFY / NEEDS_EVIDENCE / DEFER | 그대로 | CONVENTION_SUPPORTED (AI-GUIDE §13) | 변환 없음 (공식 값) |
| Human Review | A-07 `HUMAN_DECISION` (Review 판단 값) | Human Review = ACCEPT로 읽고 Decision Type 축에 `HUMAN_DECISION` | APPROVAL_REQUIRED | PROPOSED / APPROVAL_PENDING |
| Decision Type | A-06 `HUMAN_DECISION` (Destination 쪽) | Decision Type 축 | CONVENTION_SUPPORTED | PROPOSED / APPROVAL_PENDING |
| Priority | AI-GUIDE 예시 LOW / HIGH | Impact로 기록 | CONVENTION_SUPPORTED | PROPOSED / APPROVAL_PENDING (AI-GUIDE 수정은 별도 승인) |
| Priority | "재현 시 MUST / SHOULD", "Runtime SHOULD / Fix CONDITIONAL MUST" | Runtime Priority / Fix Priority / Fix Condition 3열 | APPROVAL_REQUIRED | PROPOSED / APPROVAL_PENDING (G-04에서 적용 여부) |
| Priority | HR-SUB-01 "MUST / MIGRATION_PRESERVE" | Priority = MUST, Preserve = YES | CONVENTION_SUPPORTED | PROPOSED / APPROVAL_PENDING |
| Priority | AUTH-EV-02 / 03 미기재 | `UNSPECIFIED (원문)` | APPROVAL_REQUIRED | PROPOSED / APPROVAL_PENDING (값 부여는 G-04 / 사람) |
| Impact | MEDIUM (A-01), "중간~높음" / "높음" (A-04 Audit) | 원문 보존 + 출처 표시 | APPROVAL_REQUIRED | PROPOSED / APPROVAL_PENDING |
| Timing | `PRE-MIGRATION` / `PRE-HARDENING` | `PRE_MIGRATION` / `PRE_HARDENING` | CONVENTION_SUPPORTED | PROPOSED / APPROVAL_PENDING |
| Timing | "OUT OF CORE", "Out of Core" | `OUT_OF_CORE` | CONVENTION_SUPPORTED | PROPOSED / APPROVAL_PENDING |
| Timing | "COMMON_DECISION 이후" | `COMMON_DECISION` | CONVENTION_SUPPORTED | PROPOSED / APPROVAL_PENDING |
| Timing | LATER / DOCUMENTATION_UPDATE / OPERATIONS (A-01), AFTER_CORE (A-02), DURING_MIGRATION (A-05) | G-02 §11.2 제안값 | APPROVAL_REQUIRED | PROPOSED / APPROVAL_PENDING |
| Timing (Scope) | Runtime In Scope / Optional / Conditional / Out (A-06) | `3rd Scope` 열 | CONVENTION_SUPPORTED | PROPOSED / APPROVAL_PENDING |
| Destination | DEFER_PRODUCT_DECISION (Destination 칸 사용) | Destination + Decision Type 병기 | APPROVAL_REQUIRED | PROPOSED / APPROVAL_PENDING |
| Destination | MIGRATION_PRESERVE (A-02 / 03 / 04 Destination 칸) | Preserve 축으로 이동 | CONVENTION_SUPPORTED | PROPOSED / APPROVAL_PENDING |
| Destination | 복합 Destination (`+`, `→`) | Primary / Related / Conditional Next Destination | APPROVAL_REQUIRED | PROPOSED / APPROVAL_PENDING |
| Destination | OPERATIONS_EVIDENCE (A-01) | RUNTIME_EVIDENCE + Owner O-01 | APPROVAL_REQUIRED | PROPOSED / APPROVAL_PENDING |
| Preserve | 별도 축 | `Preserve = YES` | CONVENTION_SUPPORTED (A-05~A-07) | PROPOSED / APPROVAL_PENDING |
| Preserve Kind | Implementation / Behavior | `IMPLEMENTATION` / `BEHAVIOR` | APPROVAL_REQUIRED | PROPOSED / APPROVAL_PENDING |
| Final Gate | A-01~A-04 판정값 없음 | `NOT_STATED` + 열 분리 (G-02 §11.5) | APPROVAL_REQUIRED | PROPOSED / APPROVAL_PENDING |

## 9.2 G-03에서 새로 쓴 축 값

| 축 | 값 | 사용 위치 | APPROVAL_STATUS |
|---|---|---|---|
| Contract Disposition | REVIEW_ACCEPTED / CONDITIONAL / DECISION_PENDING / EVIDENCE_PENDING / IMPLEMENTATION_PRESERVE | §3 | PROPOSED / APPROVAL_PENDING |
| Human Approval | NOT_RECORDED / APPROVAL_PENDING / APPROVED_WITH_EVIDENCE | §3 | PROPOSED / APPROVAL_PENDING |
| Runtime Result | RUNTIME_PENDING (<ID>) / NOT_EXECUTED | §3 | PROPOSED / APPROVAL_PENDING |
| Migration Gate | CANDIDATE / READY_FOR_RUNTIME_DESIGN / BLOCKED_BY_<ID> | §3 | PROPOSED / APPROVAL_PENDING |
| Preserve Kind 출처 | 원문 / G-03 제안 | §5 | PROPOSED / APPROVAL_PENDING |
| Kotlin 비교 | EXPECTED_TO_PRESERVE / CHARACTERIZE_ONLY / DECISION_REQUIRED / DEFECT_REPRODUCTION_ONLY | §5.3 | PROPOSED / APPROVAL_PENDING |
| BLOCKING_STATUS | BLOCKS_PRE_MIGRATION_RUNTIME_GATE / BLOCKS_KOTLIN_START / NON_BLOCKING_PRESERVE_AS_IS / CONDITIONAL_BLOCK / UNDETERMINED | §8 | PROPOSED / APPROVAL_PENDING |
| DECISION_DEADLINE | BEFORE_SUBSTITUTE_CONTRACT_FREEZE (D-21 원문 표현을 옮김) 외 프롬프트 예시 값 | §8 | PROPOSED / APPROVAL_PENDING |
| Test Purpose / Coverage 수준 | §11.1 | §11 | PROPOSED / APPROVAL_PENDING |

## 9.3 Approved Taxonomy 반영이 필요할 문서

승인될 경우에만 반영한다. 원 Review의 값은 바꾸지 않고 Global 문서에서 `ORIGINAL_VALUE`를 보존한다.

| 문서 | 반영 내용 (승인 시) | 상태 |
|---|---|---|
| `AI-GUIDE.md` §13 | Priority 예시의 LOW / HIGH 위치, `DEFER_PRODUCT_DECISION` Destination 사용, Preserve 축 / Kind, Decision Type 축 | APPROVAL_PENDING, 수정하지 않음 |
| G-03 (이 문서) | §9.1 / §9.2 값의 승인 표기 갱신 | APPROVAL_PENDING |
| G-04 Canonical Runtime / Test Matrix | Runtime / Fix Priority 분리, Test Purpose / Coverage 수준 | G-04 입력 |
| G-05 Roadmap | Timing / 3rd Scope 열 분리 | G-05 입력 |
| A-01~A-07 Review | 수정하지 않음 (원문 보존) | 변경 없음 |

---

# 10. Documentation Update Backlog

## 10.1 Backlog

"발견 상태"와 "수정 상태"를 나눈다. 이번 블록에서 AUDIT-MAP, Source, 기존 Review를 고치지 않았다.

| Backlog ID | 원 Finding | 대상 | 내용 | 선행 조건 | 발견 상태 | 수정 상태 | Owner | 완료 확인 |
|---|---|---|---|---|---|---|---|---|
| **G02-DOC-BL-01** | G01-F-23 | `AUDIT-MAP.md` L437 | `audits/A-07-notification-event.md` → `audits/A-07-notification.md` | 사람의 문서 수정 승인 | CONFIRMED (G-02, G-03에서 L437 문자열 재확인) | **APPROVAL_PENDING / NOT_DONE** | 문서 관리자 / 사람 | AUDIT-MAP 경로와 `audits/` 실제 파일명 일치 |
| G03-DOC-BL-01 | G02-NF-01, G-02 §10.1 #5 | `sources/P0정리본.pdf` §19 (p.15) vs §24 / §25 (p.20-21), API-NOT p.6 / p.11 | (i) P0 정리본 내부 알림 범위 차이, (ii) ③ 후보 0명 알림의 P0 / P1 범위 차이 정리 | (i) G03-D-18 (NOT-DN-05), G03-D-17 결정. (ii) Decision ID 없음 — 사람의 Product Scope 승인 (§3.8.3). Source 원본은 덮어쓰지 않고 Decision Log 후 공식 문서 반영 (SOURCE-INDEX §9-10) | CONFIRMED (G-02 PDF 확인) | DECISION_PENDING / NOT_DONE | 사람 | Decision Log 기록 + 정리본 갱신본에서 범위 일치 |
| G03-DOC-BL-02 | G02-NF-02 | P0 정리본 "ACTIVE" 표현, Global Canonical 문서 | "ACTIVE Pattern / 직원" ↔ Current Membership (`leftAt == null`) / row 존재 = 현재 Pattern 어휘 정리 | 사람의 어휘 승인 | CONFIRMED | **G-03 내부 반영 완료** (§3.1, C-11 / C-17 Alias), Source 정리는 APPROVAL_PENDING / NOT_DONE | 사람 / 문서 관리자 | Canonical 문서와 Source 갱신본 어휘 일치 |
| G03-DOC-BL-03 | G02-NF-03 | G-01 §13 G01-F-06 (가설 표현) | G-01 가설 "A-07 요약 표현 문제 가능성" 정정 사실을 G-01에 주석으로 남길지 | 사람의 문서 수정 승인 (선택 사항) | CONFIRMED (G-02) | **G-03 내부 반영 완료** (D-21 Owner provenance), G-01 주석은 OPTIONAL / APPROVAL_PENDING / NOT_DONE | 문서 관리자 / 사람 | 승인 시 G-01 주석 존재. 미승인 시 G-02 §3.1 / G-03 D-21이 정정 기록 |
| G03-DOC-BL-04 | G02-NF-04 | Global Decision Register / G-04 Runtime Matrix | 양방향 / 짝 XD의 원본 ID 보존과 중복 연결 | 없음 | CONFIRMED | **G-03 Decision 부분 반영 완료** (§10.2), Runtime 짝은 G-04 CARRIED_FORWARD | G-03 / G-04 | G-04 Matrix에 짝 XD가 한 Scenario로 연결 |
| G03-DOC-BL-05 | G01-F-01, G01-F-02 | A-02 Review §13 / §17, A-04 Review §16 / §20 | A02-XD-05 / A04-XD-03의 작성 측 `PENDING FINAL CONSISTENCY CHECK` 표기에 Global 결과 주석 | 사람의 문서 수정 승인 (선택 사항, G-02 §3.3 "별도 승인") | CONFIRMED (G-02) | OPTIONAL / APPROVAL_PENDING / NOT_DONE | 문서 관리자 / 사람 | 승인 시 주석 존재. 미승인 시 G-02 §4.4 / §4.5가 최신 상태를 기록 |
| G03-DOC-BL-06 | G01-F-24, A07-XD-06 | §9.3 문서 목록 | Approved Taxonomy 반영 | §9 승인 | CONFIRMED | APPROVAL_PENDING / NOT_DONE | 사람 (Convention 승인) | 승인 값이 §9.3 문서에 반영 |

Backlog ID는 새 Finding이나 Defect가 아니다. 원 Finding의 후속 작업 추적 번호다.

## 10.2 G02-NF-01~04 처리 위치

| G-02 New Finding | 처리 위치 | Disposition | 다음 Owner |
|---|---|---|---|
| G02-NF-01 (P0 정리본 내부 알림 범위) | G03-DOC-BL-01 (i), §3.8.3 (B). §19 "관리자 거절 / 재OPEN" 부분은 G03-D-18과 관련, ③ 후보 0명 알림 범위는 D-18과 무관한 Scope 승인 대기 | HUMAN_APPROVAL_PENDING (Source 정리) | 사람 |
| G02-NF-02 ("ACTIVE" 어휘) | §3.1 원칙, C-11 / C-17, G03-DOC-BL-02 | HANDLED_IN_G03 (Canonical 어휘) + HUMAN_APPROVAL_PENDING (Source) | 사람 |
| G02-NF-03 (SUB-DN-02 Owner 출처 정정) | D-21 Owner 열 (원문 표기 3종 보존), G03-DOC-BL-03 | HANDLED_IN_G03 | — (G-01 주석은 선택) |
| G02-NF-04 (양방향 / 짝 XD) | Decision 짝: D-16 (A02-XD-05 ↔ A07-XD-03), D-15 (A02-XD-04 ↔ A06-XD-04, A05-XD-05 묶음), D-21 (A05-XD-02), D-22 (A07-XD-01), DG-01 COMMON XD 6개 (§7.4). Runtime / Harness 짝(A03-XD-01 ↔ A04-XD-07, A04-XD-01 ↔ A05-XD-04, A04-XD-02 ↔ A06-XD-03, A04-XD-03 ↔ A07-XD-04, A06-XD-02 ↔ A07-XD-05, A05-XD-06 ↔ A06-XD-05, Security XD 3개)은 §11.4 | HANDLED_IN_G03 (Decision 짝) + CARRIED_FORWARD_G04 (Runtime 짝) | G-04 |

## 10.3 G-02 Handoff 15건 Coverage (프롬프트 §11.1)

| G-02 §12 # | 항목 | G-03 처리 위치 | DISPOSITION | RELATED_ID | NEXT_OWNER |
|---|---|---|---|---|---|
| 1 | Canonical Contract | §3 (59 Canonical, 71 원본 Index §3.9 / 검산 §3.10), §4 (INV-01~14) | HANDLED_IN_G03 / 승인은 HUMAN_APPROVAL_PENDING | 71 Contract ID, G01-CR-01~14 | 사람 (Contract Freeze) / G-04 |
| 2 | SCH-MIG-15 표기 | §6.1, §6.2, C-32 | HANDLED_IN_G03 | SCH-MIG-15, NOT-MIG-05 ①, G01-P-14 / 32 / 33 | — |
| 3 | SUB-MIG-12 "A-07 판단 종속" | §6.1, G03-IP-03 | HANDLED_IN_G03 (종속 해소 연결), 실패 정책은 HUMAN_APPROVAL_PENDING (D-22) | SUB-MIG-12, NOT-MIG-05 / 06, G01-P-30 / 32~35 | COMMON / 사람 |
| 4 | Preserve 통합 | §5 (42행, Kind, CHANGE_GATE, Kotlin 비교) | HANDLED_IN_G03 / Kind 제안은 HUMAN_APPROVAL_PENDING | G01-P-01~42 | 사람 (Kind 승인) / G-04 |
| 5 | COMMON Error Decision | §7 D-01~D-10, §7.4, §8 | HANDLED_IN_G03 (Register) / HUMAN_APPROVAL_PENDING (결정) | G01-DG-01 일체 | COMMON / 사람 |
| 6 | Membership Lifecycle Decision | §7 D-11~D-16, §7.4 선행 관계, §4 INV-03 | HANDLED_IN_G03 / HUMAN_APPROVAL_PENDING | G01-DG-02 일체 | 팀 / A-02 |
| 7 | Unavailable 정책 Decision | D-21 | HANDLED_IN_G03 (단일 Record, Owner 원문 보존 + 제안 표기) / HUMAN_APPROVAL_PENDING | SUB-DN-02 = UNA-DN-04, A05-XD-02 | 사람 (A-05 / A-06) |
| 8 | Source 현재성 Decision | D-17, D-18, D-19, D-20, D-21, §7.4, G03-DOC-BL-01 | HANDLED_IN_G03 (등록) / HUMAN_APPROVAL_PENDING | SUB-DN-01 / 03, NOT-DN-02 / 05, A06-XD-06, G01-P-42 | 사람 |
| 9 | Notification 실패 Decision | D-22 (AFTER_RUNTIME) | HANDLED_IN_G03 / EVIDENCE_PENDING (NOT-EV-04 / 08) | NOT-DN-04, A07-XD-01, A-04 Final SR Scope | COMMON / 사람, G-04 (Evidence) |
| 10 | Taxonomy 정규화 승인 | §9 | HANDLED_IN_G03 (정리) / HUMAN_APPROVAL_PENDING | G-02 §11, A07-XD-06 | 사람 (Convention) |
| 11 | Final Gate 표기 | §9.1 마지막 행 | HUMAN_APPROVAL_PENDING | G01-F-21 | 사람 (Convention) |
| 12 | P0 정리본 내부 알림 범위 | G03-DOC-BL-01, §10.2 | HUMAN_APPROVAL_PENDING | G02-NF-01 | 사람 |
| 13 | "ACTIVE" 어휘 | §3.1, G03-DOC-BL-02 | HANDLED_IN_G03 + HUMAN_APPROVAL_PENDING (Source) | G02-NF-02 | 사람 |
| 14 | Decision 집계 | §7.5 | HANDLED_IN_G03 (**집계 행**, 신규 Product Decision 아님. 29 / 28 / 10 실제 ID로 검산) | 29 Decision ID | — |
| 15 | 승인 후 문서 최신화 Backlog | §10.1 G02-DOC-BL-01 | HUMAN_APPROVAL_PENDING (승인 후 실제 수정 작업으로 남김) | G02-DOC-BL-01, G01-F-23 | 문서 관리자 / 사람 |

**15 / 15 처리 위치 있음, 미반영 0건.**

## 10.4 G-02 §13 / §14 Handoff (G-03 완료 작업으로 보지 않음)

| G-02 Handoff | 행 수 | G-03 처리 |
|---|---|---|
| §13 G-04 Handoff | 14 | 전부 `CARRIED_FORWARD_G04`. G-03은 Contract와 Runtime / Test ID 연결만 추가 (§11). #1 Security는 §6.3 / §11.3, #14 Regression 기대값 원칙은 §5.3 / §6.3 / §11.1에 반영했지만 실행 설계는 G-04 |
| §14 G-05 Handoff | 7 | 전부 `CARRIED_FORWARD_G05`. §12에서 Layer 입력에 G-03 ID를 연결 |

---

# 11. G-04 Test / Runtime Handoff

G-03은 테스트를 작성하거나 실행하지 않았다. 구체적인 Test Case, Fixture, Assertion 코드는 G-04에서 설계한다.

## 11.1 Test 기대값 / Coverage 표기 (G-03 제안)

| 축 | 값 | 의미 |
|---|---|---|
| Test Purpose | `NCR` = NORMAL_CONTRACT_REGRESSION | 올바른 결과가 유지되는가 (A-06 §25.29) |
| | `CHR` = CHARACTERIZATION | Java 현재 행동을 관찰·기록. 모든 관찰 결과를 옳은 Contract로 선언하지 않음 |
| | `MCR` = MIGRATION_COMPATIBILITY | 명시적으로 임시 유지하기로 한 API / DB 관측값의 의도치 않은 변경 탐지. 해제 조건 = 연결 Decision |
| | `DR` = DEFECT_REPRODUCTION | 결함 후보가 실제로 재현되는가. 결과는 정상 기대값이 아님 |
| Existing Coverage | `ASSERT_RECORDED` | Audit 기록상 해당 동작을 Assert하는 Test Source가 있음 (G-03에서 Test Source 미재확인) |
| | `MOCK_CALL_ONLY` | Mock 호출 / `verify`만 기록됨. DB / Transaction 결과 검증으로 보지 않음 |
| | `SOURCE_EXISTS_ONLY` | Test Source 존재만 확인, Assert 내용 미검토 |
| | `NONE_RECORDED` | 해당 Contract를 Assert한다는 기록 없음 |
| | `UNCONFIRMED` | 기록이 불명확해 판단하지 않음 |
| 실행 상태 | 모든 기존 Test: `NOT_EXECUTED` (EXECUTION_VERIFIED 0, G-01 §8.1). 이전 `build/test-results`는 Baseline 이전 결과라 Evidence로 쓰지 않음 (A-04 Review §2) |

공통 원칙:

- 응답 세부값(Status / ErrorCode / Success code / 메시지 / 2차 정렬)은 `MCR`로 Java Baseline 실측값을 기록하고 Kotlin과 비교한다. G-03은 예상 값을 새로 만들지 않는다. 정상 Contract(`NCR`)에는 "거부 / 성공 여부, DB 최종 상태, 상태 불변"만 둔다.
- Mock 호출 확인을 Transaction / DB 결과 검증으로 표시하지 않는다.
- Notification Row 재조회는 응답 후 새 Transaction(또는 Persistence Context Clear)에서 한다. 응답 직후 Row가 없으면 비동기 여부를 먼저 기록한다 (A-07 §15.2 #5).
- `leftAt != null` Fixture는 현재 API로 만드는 경로가 확인되지 않아 DB 직접 준비가 필요할 것으로 본다 (G-02 §5.2). PRE_MIGRATION 현행 확인(`CHR`)과 CONDITIONAL 정책 확인의 기대값을 섞지 않는다 (G01-F-15).

## 11.2 Canonical Contract별 G-04 입력 (59행)

Planned Level: `U` Unit / `I` Integration (H2 가능) / `H` HTTP + 실제 Security Filter / `M` MySQL. Evidence 열의 실행 결과는 모두 `NOT_EXECUTED`이므로 생략한다.

| G03 ID | Test Purpose | Existing Coverage (G-01 §8.1 근거) | Planned Level / Fixture 후보 | Expected Assertions (최소 검증 대상) | Evidence (Test ID / Gap / Runtime) | Java ↔ Kotlin 비교 / 미결정 기대값 위치 |
|---|---|---|---|---|---|---|
| C-01 | NCR | NONE_RECORDED (`UserServiceTest`는 회원가입 성공 미Assert) | I (DB) | users 비밀번호 컬럼이 원문이 아니고 BCrypt 형식 | AUTH-TC-01 | 같은 Assertion |
| C-02 | NCR + MCR | UNCONFIRMED | I / H | 두 번째 가입 거부, User 1건 유지 / 응답 값은 MCR | AUTH-TC-02 | 응답 형식 → D-01 계열 |
| C-03 | NCR + MCR | MOCK_CALL_ONLY (`UserServiceTest` `verify(save)`), Cookie 속성은 `RqTest` Unit ASSERT_RECORDED | H + DB | Access Token, Refresh Cookie(HttpOnly), `users.refresh_token` 저장, Cookie / DB 관계 / Cookie 설정값은 O-01 | AUTH-TC-03 / 04, AUTH-EV-02 (Priority 원문 미기재) | 덮어쓰기 등은 P-01 MCR |
| C-04 | NCR + CHR | MOCK_CALL_ONLY | H + DB | 유효 Refresh → 새 Access 200, 잘못된 Refresh 실패 / 반복 사용·두 번째 로그인 후 처리는 CHR | AUTH-TC-05 / 06, AUTH-CT-02 / 03, AUTH-EV-02 | Token 관리 방식 → D-23 / 24 |
| C-05 | NCR (Matcher 밖 경로) + CHR + **DR (`/workplaces/**` Endpoint별)** | NONE_RECORDED (HTTP + Security Filter Test 0) | H (실제 Security Filter, 무토큰 / 유효 Token) | Matcher 적용 여부(공통 1회), Endpoint별 무토큰 HTTP Status / Body. AUTH-MIG-06 위반 여부는 Endpoint별 판정 | AUTH-TC-07, AUTH-CT-01, AUTH-EV-01, WP-EV-03, PAT-EV-02, SUB-EV-03, SCH-EV-01 / 02, NOT-EV-01, UNA-EV-01 | Java가 401이 아닌 Endpoint는 Kotlin 비교 기대값으로 쓰지 않음 (§11.3) |
| C-06 | NCR | NONE_RECORDED | H | 유효 Token → 현재 User 정보 / 필드명은 AUTH-NE-01 확인 후 | AUTH-TC-08, AUTH-EV-01 | 필드명 EVIDENCE_PENDING |
| C-07 | NCR | NONE_RECORDED (Pattern Test는 `@MockitoBean Rq`) | H | Actor가 Token User와 일치, Client가 보낸 ID로 Actor가 바뀌지 않음 | A-02 §10 MUST #6, WP-EV-03, UNA-EV-01 | 같음 |
| C-08 | NCR | MOCK_CALL_ONLY (`WorkplaceServiceTest` 생성 Role) | H + DB | Workplace 1건 + 생성자 MANAGER Membership 1건 (DB 재조회) / Rollback 주입은 DEFERRED | A-02 §10 MUST #1, WP-EV-02 | 같음 |
| C-09 | NCR | NONE_RECORDED (Join 성공 미Assert) | H + DB | EMPLOYEE Membership 생성, 잘못된 inviteCode 실패 | A-02 §10 MUST #2 / #3, WP-EV-02 | 대소문자는 CHR (A-02 §10 Characterization #3) |
| C-10 | NCR + MCR + DR | MOCK_CALL_ONLY (재참여 예외 타입) | H + DB, M (Unique) | 순차 중복 Join 거부, Membership 1건 유지 / DB Unique 실존 / 동시 Join은 DR | A-02 §10 MUST #4, Hardening #1, WP-EV-01, WP-EV-04 | 재가입 → D-13 |
| C-11 | NCR + CHR | MOCK_CALL_ONLY (`leftAt` Query 미Assert), Pattern 목록 t32는 ASSERT_RECORDED (H2) | I (DB Fixture `leftAt != null`) | Current Membership 판정이 `leftAt == null` 기준 / 종료 Fixture는 CHR | A-02 §10 MUST #5, WP-EV-02, PAT-EV-03 | 종료 이후 정책 → DG-02 |
| C-12 | NCR | MOCK_CALL_ONLY | H + DB | 목록에 Current Membership만 | A-02 §10 MUST #5, WP-EV-02 | 같음 |
| C-13 | NCR + MCR | MOCK_CALL_ONLY (`WorkplaceMemberServiceTest` Happy Path) | H | MANAGER 성공, EMPLOYEE / 비구성원 / 타 Workplace MANAGER 거부 / 값은 MCR | A-02 §10 MUST #6, WP-EV-03 | 응답 → DG-01 |
| C-14 | NCR + MCR | ASSERT_RECORDED (`RegularShiftPatternControllerTest` 45, H2, `@WithMockUser` + Mock Rq) | H (실제 JWT) + DB | 대상 조건 / MANAGER 대상 허용 / 비구성원·종료·EMPLOYEE 요청자 거부 / 값은 MCR | A-03 §12 Contract / Security MUST, PAT-EV-01 / 02 / 03 | 응답 → D-02 |
| C-15 | NCR | ASSERT_RECORDED (t4 / t5) | U / I | 같은 시각, 자정 넘김 거부 | A-03 §12 | 같음 |
| C-16 | NCR + DR | ASSERT_RECORDED (t22~t30, t37) | I, M (동시성) | 겹침 거부, 경계 허용, 수정 시 자기 제외 / 동시 등록은 DR | A-03 §12, Hardening #1, PAT-EV-04 | 같음 |
| C-17 | NCR + CHR | ASSERT_RECORDED (t17, t41) | I | 삭제 후 row 없음 / t41 종료 Member 삭제 허용은 CHR | A-03 §12 | t41 → D-14 |
| C-18 | NCR | MOCK_CALL_ONLY (`ScheduleServiceTest` Pattern 날짜 변환) | H + DB | 생성 시점 Current Membership Pattern 수 = 초기 Shift 수, 담당 / 시간 일치 | A-03 §12, A-04 §12 Contract #1, PAT-EV-01 / 03, SCH-EV-01 | 동시 Snapshot은 SCH-EV-06 (DEFER) |
| C-19 | NCR | NONE_RECORDED (소급 미반영 Assert 없음) | H + DB | Pattern 수정 / 삭제 후 기존 Shift 불변 | A-03 §12 #9~10, A-04 §12 Contract #16, PAT-EV-01 | 같음 |
| C-20 | NCR | ASSERT_RECORDED (`ScheduleTest` Unit) | H + DB | 생성 직후 DRAFT | A-04 §12, SCH-EV-01 | 같음 |
| C-21 | NCR | NONE_RECORDED | M (Schema) + H | 같은 주차 두 번째 생성 거부, DB Unique 실존 | A-04 §12, SCH-EV-08 | 같음 |
| C-22 | NCR + MCR | NONE_RECORDED (create / modify 성공 미Assert) | H + DB | 같은 Workplace Current Member만, MANAGER 배정 가능 / 다른 Workplace 응답은 MCR | A-04 §12, SCH-EV-01 / 02 | 응답 → D-04 |
| C-23 | NCR + MCR | NONE_RECORDED (overlap·공식 충돌 실패 미Assert) | H + DB | 각 조건 위반 거부 + Shift 불변 / 값은 MCR | A-04 §12, SCH-EV-01 | 응답 → D-03 |
| C-24 | NCR + MCR + DR | MOCK_CALL_ONLY (`ShiftServiceTest` / `ScheduleServiceTest` Unavailable), `ScheduleControllerTest` confirm 누락 400 (standalone MockMvc) | H + DB | confirm 없으면 미진행, confirm 시 진행, confirm이 Hard Validation 우회 안 함 / 응답은 MCR / 동시성은 DR (UNA-DC-01) | A-04 §12, UNA-TG-C-03 / C-05, SCH-EV-02, UNA-EV-02 / 04 / 05 | 응답 → D-05 |
| C-25 | NCR + MCR | MOCK_CALL_ONLY (`UnavailableTimeServiceTest`) | H + DB | confirm 없으면 미저장, confirm 시 저장 / 응답은 MCR | UNA-TG-C-03, UNA-EV-02 | 응답 → D-05 |
| C-26 | NCR | NONE_RECORDED | I | 충돌 상태 DRAFT 저장 가능 | A-04 §12 | 같음 |
| C-27 | NCR + DR | MOCK_CALL_ONLY (`ScheduleServiceTest` 성공·실패 12종), H2 동시성 `ShiftScheduleConcurrencyTest` | H + DB, M (동시성) | 각 실패 조건에서 DRAFT 유지, `publishedAt` null, Notification 0건 / 동시 Mutation은 DR | A-04 §12 #11~12, SCH-EV-02, NOT-EV-05(a), SCH-EV-03 | 같음 |
| C-28 | NCR | MOCK_CALL_ONLY (Commit 후 재조회 Test 없음, E-005 / E-011) | H + DB | PUBLISHED, `publishedAt`, Shift row 수 불변, DRAFT로 돌아가지 않음 | A-04 §12 #11 / #17, SCH-EV-01 | 같음 |
| C-29 | NCR + CHR | ASSERT_RECORDED 일부 (`UnavailableTimeRepositoryTest` 공식 충돌 ID Query, 내장 DB) | I (Repository) | PUBLISHED ∧ SCHEDULED만 Official / `leftAt` Fixture는 CHR | A-04 §12 Official Query, SCH-EV-01 | `leftAt` → DG-02 |
| C-30 | NCR + DR | ASSERT_RECORDED 일부 (위와 같음) | H + DB, M (동시성) | 다른 Workplace 공식 Shift와 겹치면 충돌 / 동시 Publish는 DR | A-04 §12, SCH-EV-02, SCH-EV-07 | 같음 |
| C-31 | NCR | UNCONFIRMED | H + DB | PUBLISHED Shift에 일반 CRUD 거부 + Shift 불변 | A-04 §12, SCH-EV-01 | 정정 → D-27 |
| C-32 | NCR (C-54 ①로 검사) + CHR (구조) | MOCK_CALL_ONLY (`SchedulePublishedEventListenerIntegrationTest`, NotificationService Mock, AFTER_COMMIT 시점만) | I (실제 Listener) + DB | Publish 후 `SCHEDULE_PUBLISHED` Row (C-54 ①) / Event 구조는 CHR | NOT-TG-C-03, SCH-EV-01 (+NOT-EV-02) | 구조 → D-22 |
| C-33 | NCR | MOCK_CALL_ONLY | H + DB | 다른 Workplace 소속과 무관하게 User 단위 조회 / 저장 | UNA-TG-C-04, UNA-EV-01 | 같음 |
| C-34 | NCR + MCR | MOCK_CALL_ONLY | H | 4개 API Path / Method / Request / Response 형태 / Success code 값은 MCR | UNA-TG-C-01, UNA-EV-01 | Success code → D-08 |
| C-35 | NCR | MOCK_CALL_ONLY (37건 Mock) | U (Clock 고정) | 시간 규칙, 자정 넘김 허용, Asia/Seoul | UNA-EV-08 (DEFER) | 같음 |
| C-36 | NCR + MCR | ASSERT_RECORDED (`UnavailableTimeRepositoryTest` overlap / 경계 / 자기 제외, 내장 DB INF) | I + H | 겹침 거부, 경계 허용, 수정 시 자기 제외 / 값은 MCR | UNA-TG-C-04, UNA-EV-01 | 같음 |
| C-37 | NCR + MCR | MOCK_CALL_ONLY | H + DB (Clock 고정) | `endAt > now`, `startAt ASC`, 충돌 계산, 빈 목록 / 2차 정렬은 MCR | UNA-TG-C-02, UNA-EV-01 | 2차 정렬 → D-08 |
| C-38 | NCR | MOCK_CALL_ONLY | I + DB | 삭제 후 row 없음, Schedule / Shift / Candidate 불변 | UNA-TG-C-04, UNA-TG-C-07 (DEFER) | 같음 |
| C-39 | NCR + MCR | MOCK_CALL_ONLY | H | 타인 수정 / 삭제 거부 + 원본 불변 / 403·404 값과 순서는 MCR | UNA-TG-C-01, UNA-EV-01 | → D-06 |
| C-40 | NCR + MCR | NONE_RECORDED (`SubstituteCandidateServiceTest`는 Unavailable 실패 미Assert, findCandidates 실제 Filter 미Assert) | I + DB, M (가시성) | Unavailable 겹치는 User는 Candidate 아님, ACCEPT / APPROVE 시 재검증 수행 / 실패 응답(Hard Block)은 MCR | SUB-TG-C-03, SUB-EV-01, SUB-EV-08(a) | 실패 처리 → D-21 |
| C-41 | CHR + MCR | ASSERT_RECORDED 일부 (`SentSubstituteRequestResponseTest` EXPIRED 표시 변환) | I + DB | 현재 상태값 / closeReason Matrix 기록 | SUB-EV-02 | 정상 Contract 여부 → D-17 |
| C-42 | NCR + MCR | MOCK_CALL_ONLY (create 8) | H + DB | 본인 Shift만, 활성 Request 차단, Client ID 무시 / 값은 MCR | SUB-TG-C-01, SUB-EV-01, SUB-EV-09 (동시 생성) | Requester `leftAt` → D-15 |
| C-43 | NCR | NONE_RECORDED (실제 Filter 미Assert) | I + DB | 세 충돌 / Role / `leftAt` / Requester 제외, 0명이면 Request 없음 | SUB-TG-C-03, SUB-EV-01 | 같음 |
| C-44 | NCR | NONE_RECORDED | I + DB, M (Schema) | Request + Candidate 동시 생성 / 중복 Candidate 없음 | SUB-TG-C-01, SUB-EV-04 | UNIQUE → P-31 |
| C-45 | NCR + MCR | ASSERT_RECORDED 일부 (`SubstituteCandidateRepositoryTest` SUB-02 / 07 Query, flush / clear) | I + H | §27.8 조건 / 정렬 / 조회 중 상태 불변 / EXPIRED 표시는 MCR | SUB-TG-C-07, SUB-EV-02, SUB-EV-13 (DEFER) | Expiry → D-19, `leftAt` → D-15 |
| C-46 | NCR + DR | MOCK_CALL_ONLY (ACCEPT 성공 호출 순서), H2 동시성 Test는 정상 결과 Assertion | H + DB, M (동시성) | 성공: Candidate / Request ACCEPTED, Shift.member 불변, 다른 PENDING 유지 / 실패: 상태 유지 / 동시 ACCEPT는 DR | SUB-TG-C-04, SUB-EV-01, SUB-EV-05, SUB-EV-08 | 실패 처리 → D-21 |
| C-47 | NCR + DR | MOCK_CALL_ONLY (REJECT), H2 E-002 정상 결과 Assertion | H + DB, M (동시성) | 부분 거절 시 OPEN 유지, 마지막 거절 시 CLOSED / `ALL_CANDIDATES_REJECTED` / 동시 REJECT는 DR | SUB-TG-C-05, SUB-EV-02, SUB-EV-06 | 같음 |
| C-48 | NCR + DR | MOCK_CALL_ONLY (`approveRequest` 호출만, Shift.member 실제 변경 미Assert) | H + DB, M (동시성) | 성공: Shift.member = acceptedMember + APPROVED (DB 재조회) / 실패: 세 값 유지 / 경쟁은 DR | SUB-TG-C-02 / C-04, SUB-EV-01, SUB-EV-08 | 같음 |
| C-49 | NCR + MCR | MOCK_CALL_ONLY (close 11) | H + DB | CLOSED / `MANAGER_CLOSED`, Shift / Candidate 불변 / 재OPEN 없음은 MCR | SUB-TG-C-05, SUB-EV-02 | 재OPEN → D-17 |
| C-50 | NCR | MOCK_CALL_ONLY (`Clock.fixed`) | U / I (Clock 고정) | `startAt > now` 경계, `[start,end)` | SUB-EV-10 (DEFER) | 같음 |
| C-51 | NCR | NONE_RECORDED | H (API 목록) | Notification 생성 API 없음 | Test ID 없음 → G-04에서 부여 | 같음 |
| C-52 | NCR + MCR | MOCK_CALL_ONLY (목록) | I (Query) + H | 본인 알림만 / `leftAt`·tie-breaker는 MCR | NOT-TG-C-01, NOT-EV-01 | `leftAt` → D-16 |
| C-53 | NCR + MCR | ASSERT_RECORDED (`NotificationTest` 최초 readAt, `NotificationServiceTest` 404 / 403, `NotificationReadConcurrencyTest` H2 결정적 순서) | H + DB, M (동시) | 반복 PATCH 후 같은 readAt, 동시 읽음 최초 readAt / 403·404는 MCR | NOT-TG-C-02 / C-06, NOT-EV-01, NOT-EV-13 | 403 / 404 → D-10 |
| C-54 | NCR + DR | MOCK_CALL_ONLY (Type별 create 7, Listener IT Mock) | I (실제 Listener) + DB | A-07 §15.2 표의 Type / 수신자 / 건수 / `readAt` null (순차) / 동시 실행 수신자는 DR | NOT-TG-C-03 / C-04, NOT-EV-02 (⊂ SCH-EV-01), NOT-EV-03 (⊂ SUB-EV-01 / 02) | ③은 Review 기반 기대값과 Product Scope 승인 상태를 따로 기록 (§11.2.1) |
| C-55 | NCR (요구사항) + CHR | NONE_RECORDED | I (실패 주입), M 권장 | 실패 주입 위치별로 분리 (§11.2.2). 공통 NCR: Business Commit 이후 알림 처리 실패가 확정된 Business 상태를 되돌리지 않음. Notification Row 수 / HTTP 결과는 위치별 관측값으로 기록하고 일괄 확정하지 않음 | NOT-TG-X-01 / X-02, NOT-TG-C-05, NOT-EV-04, NOT-EV-05(a)(b), NOT-EV-08 | 관측 방식 → D-22 |
| C-56 | NCR | NONE_RECORDED | I | 기존 Row 내용이 이후 상태 변화로 바뀌지 않음 | Test ID 없음 → G-04에서 부여 | 같음 |
| IP-01 | CHR (구조) | MOCK_CALL_ONLY (`getByIdForUpdate` 선행 InOrder) | I / M | 쓰기 3종에서 Lock 획득 순서 보존 여부 | UNA-EV-07 (DEFER) | 구조 비교 CHARACTERIZE_ONLY |
| IP-02 | CHR (구조) + DR | NONE_RECORDED (결정적 Lock 순서 제어 Test 없음) | M | 현재 Lock 지점 / 순서 / 경쟁 결과는 DR | SUB-TG-H-04, SUB-EV-05~09 | 구조 비교 CHARACTERIZE_ONLY |
| IP-03 | CHR (구조) | MOCK_CALL_ONLY (Listener IT 1건) | I | Event 발행 시점 / Payload / 수신자 결정 위치 | NOT-TG-C-04 | 외부 의미는 C-54 |

### 11.2.1 C-54 ③ 기록 방식 (Human Follow-up)

| G-04 기록 항목 | 값 |
|---|---|
| Review 기반 기대값 | SUB-01에서 Candidate 0명이면 Request를 만들지 않고 현재 Workplace MANAGER에게 알림 (NOT-MIG-05 ③, A-07 §15.2 SUB-EV-02 "후보 0명 → MANAGER"). 기존 기대값을 그대로 쓴다 |
| 기대값 근거 수준 | `REVIEW_ACCEPTED` (A-07 Human Review). Human Approval NOT_RECORDED |
| Product Scope 승인 상태 | `NOT_RECORDED / APPROVAL_PENDING` (P0 정리본 §25 P1 분류와 API-NOT의 차이, G-02 §10.1 #5). Decision ID 없음 |
| 시도마다 발행 | G01-P-42 Preserve → `MCR` (Java 실측 건수 기록, Kotlin과 비교) |
| Test 결과 해석 | Java와 Kotlin이 같으면 Migration Regression 통과로만 본다. Scope 승인 결과로 해석하지 않는다. 사람이 다른 Scope를 승인하면 이 기대값은 그때 다시 정한다 |

### 11.2.2 C-55 Notification 실패 — 실패 주입 위치별 검증 (Human Follow-up)

정상 Contract(C-55, NOT-MIG-06): **Business Commit 이후** Notification 처리가 실패해도 이미 Commit된 Business 상태를 되돌리거나 Domain SoT를 잘못된 상태로 만들지 않는다. 이 Contract는 아래 F2~F4에만 적용된다. F1은 Commit 전 실패라서 C-55의 대상이 아니다.

A-07 §8은 다음을 확정하지 않는다고 적었다: 알림이 정확히 한 번 생성된다, Commit 후 알림이 반드시 저장된다, 실패 시 자동 Retry된다, 오류 시 특정 HTTP Status가 반드시 나간다. 아래 표의 "관측 기록"은 G-04가 실제 값을 기록할 항목이며, G-03은 그 값을 미리 정하지 않는다.

| 위치 | 실패 주입 지점 | NCR (정상 Contract로 Assert) | 관측 기록 (값을 미리 정하지 않음) | 원문의 예상 / 추론 (참고만, 기대값 아님) | Runtime / Test |
|---|---|---|---|---|---|
| F1(a) | Business Commit 이전 — Business 검증 실패로 Rollback | 해당 Command의 정상 Contract가 정한 상태 불변 (예: C-27 실패 시 DRAFT 유지, `publishedAt` null) | HTTP Status / Body (값은 MCR), 해당 Event Notification Row 수 | A-07: Rollback 시 Listener 미호출 → 알림 없음 = INFERENCE (§8, §9.1). A-07은 NOT-TG-C-05 "검증 실패 시 Row 없음"을 Contract Test로 ACCEPT하고 NOT-IE-02는 NEEDS_EVIDENCE로 둠 | NOT-EV-05(a) (SCH-EV-02 / SUB-EV-02 통합, PRE_MIGRATION), NOT-TG-C-05 |
| F1(b) | Business Commit 단계 실패 (Commit 자체 실패) | 원 Business Command의 결과가 반영되지 않았는지 DB 재조회로 확인 | Listener 호출 여부, Notification Row 수, HTTP Status / Body | A-07: AFTER_COMMIT이므로 Listener 미호출 = INFERENCE | NOT-EV-05(b) (PRE_HARDENING), NOT-TG-X-02 (CHR) |
| F2 | Business Commit 이후 — Listener 안에서 실패 (NotificationService 호출 전 또는 호출 중 예외, `catch(Exception)` + `log.error` 경로) | **Business 최종 상태 = Commit 결과 유지** (새 Transaction에서 재조회) | HTTP Status / Body, Notification Row 수, Error Log 존재 | A-07 §9.1: HTTP는 "확정 불가 — RUNTIME_PENDING (예상: 성공 응답)" | NOT-EV-04 (PRE_HARDENING), NOT-TG-X-01 (CHR) |
| F3 | Notification 저장 Transaction (`REQUIRES_NEW`) 실패 — 저장 / Commit 실패, 수신자 개수 검증 실패 포함 | **Business 최종 상태 = Commit 결과 유지** | 해당 Event Notification Row 수, 다른 Event Row 영향, HTTP Status / Body, Log | A-07 NOT-EV-04 Matrix: "기대 Notification DB: 해당 Event 0건", "기대 HTTP: 성공 (예상)" — REQUIRES_NEW 구조에서 추론한 예상치. 관측 전에는 기대값으로 쓰지 않음 | NOT-EV-04, NOT-TG-X-01 |
| F4 | 일부 수신자 처리 이후 실패 — 수신자별 save 중 k번째 이후 예외 | **Business 최종 상태 = Commit 결과 유지** | 저장된 Row 수 (전부 Rollback인지 부분 저장인지), 저장된 수신자 목록, HTTP Status / Body, Log | SR-NOT-02 "All-or-Nothing (한 수신자 실패 → 전체 미저장)" = SV + INF, Impact LOW, "현재 발생 경로 희박" (A-07 §17) | NOT-EV-04 (All-or-Nothing 확인 항목), NOT-TG-X-01 |

- F2~F4의 Row 수와 HTTP 결과는 NOT-DN-04 (G03-D-22, AFTER_RUNTIME)의 결정 입력이다. 관측 결과를 정상 Contract 기대값으로 승격하지 않는다.
- 관측 결과가 C-55를 위반하면 (Business 상태가 되돌아감) Defect Candidate 판정 입력이 되며, G-03은 Defect State를 바꾸지 않는다.
- 프로세스 종료로 인한 유실(NOT-EV-08)은 위 네 위치와 별도인 Characterization이다.
- Kotlin 비교: NCR(Business 유지)은 `EXPECTED_TO_PRESERVE`. 관측 기록 항목은 Java 실측을 남긴 뒤 비교하며, 구현 방식(P-33~35)은 `CHARACTERIZE_ONLY`다.

## 11.3 Security Runtime (G-02 §13 #1 반영)

| 단계 | G-04 입력 |
|---|---|
| 공통 원인 | `/api/v1/workplaces/**` permitAll Matcher가 실제 요청에 적용되는지 AUTH-EV-01에서 **한 번** 확인 |
| WP-DEFECT-CANDIDATE-01 | Workplace 하위 Endpoint별 무토큰 HTTP Status / Body, AUTH-MIG-06(C-05) 위반 여부 → **개별 판정** (WP-EV-03) |
| PAT-DEFECT-CANDIDATE-01 | Pattern Endpoint별 무토큰 HTTP Status / Body, C-05 위반 여부 → **개별 판정** (PAT-EV-02) |
| 관련 기록 (Defect ID 없음) | Schedule (SCH-EV-01 / 02, A04-XD-05), Substitute Workplace 하위 경로 (SUB-EV-03, A-06 §24.15) — 같은 Harness에서 기록, 새 Defect ID 없음 |
| Matcher 밖 경로 | `/unavailable-times`, `/notifications`, `/substitute-candidates`, `/schedules/me`, `/auth/me` → 무토큰 401이 C-05의 `NCR` 기대값 |
| 기대값 경계 | Matcher가 적용되어 401이 아닌 Endpoint가 나와도 그 결과를 Kotlin 비교의 정상 기대값으로 쓰지 않는다. Matcher 설정 자체는 P-02로 보존하므로, Kotlin에서 그 결과를 어떻게 다룰지는 Open Question G03-OQ-01 |

## 11.4 공유 Runtime / Harness 연결 (G-04 CARRIED_FORWARD)

| 그룹 | 포함 | 연결 Canonical |
|---|---|---|
| G01-RG-01 MySQL 환경 / Schema | UNA-EV-03 ≡ SUB-EV-04 (Version / Isolation, `transaction_isolation` UNKNOWN), WP-EV-01, SCH-EV-08, UNA-IE-05 | C-10, C-21, C-44, 동시성 전체 |
| G01-RG-02 Security | AUTH-EV-01, WP-EV-03, PAT-EV-02, SCH-EV-01 / 02, UNA-EV-01, SUB-EV-03, NOT-EV-01 | C-05, C-07, §11.3 |
| G01-RG-03 Canonical Flow | WP-EV-02 → PAT-EV-01 → SCH-EV-01 (+NOT-EV-02) → SUB-EV-01 (+NOT-EV-03) → NOT-EV-01 | C-08~C-12, C-18~C-20, C-28, C-42~C-48, C-53, C-54 |
| G01-RG-04 실패 시 상태 불변 | SCH-EV-02, UNA-EV-02, SUB-EV-02, NOT-EV-05(a) | C-24, C-25, C-27, C-47, C-49, C-55 |
| G01-RG-05 Publish 동시성 | SCH-EV-03 (⊃ NOT-EV-06, SCH-DC-03), UNA-EV-04 / 05, SCH-EV-04, SUB-EV-08(a)(b) 일부 | C-24, C-27, C-54 (DR) |
| G01-RG-06 Cross-Workplace | SCH-EV-07, SUB-EV-08(b) — Owner 공백 (G01-F-05, A04-XD-02 ↔ A06-XD-03) | C-30, C-48 (DR) |
| G01-RG-07 Substitute 동시성 | SUB-EV-05~09 (⊃ NOT-EV-07) | C-43, C-46~C-48, IP-02 (DR) |
| G01-RG-08 `leftAt` Fixture | PAT-EV-03, SCH-EV-02 (현행 CHR) / WP-EV-05, SCH-EV-05, SUB-EV-12, NOT-EV-09 (CONDITIONAL) | C-11, C-14, C-22, C-27, P-25~28, P-39 |
| G01-RG-09 Notification 실패 | NOT-EV-04, NOT-EV-05(b), NOT-EV-08 | C-55 |
| G01-RG-10 동시 읽음 | NOT-EV-13 | C-53 |

Runtime ID 56개 전체 Canonical Matrix와 Priority 정리는 G-04 몫이다 (G-02 §13 #6, #13).

---

# 12. G-05 Layer 0~3 Handoff

Layer 배치는 잠정 입력이며 확정 Scope가 아니다. Layer 3 후보는 Product Decision과 Evidence 없이 채택하지 않는다. Outbox / Scheduler 등은 채택되지 않았다.

| Layer | 입력 | G-03 ID / 원 ID | Pending 상태 | Owner |
|---|---|---|---|---|
| **L0 AS-IS** | 정상 Contract와 현행 구현 구분 | G03-C-01~56 (REVIEW_ACCEPTED 54 / CONDITIONAL 1 / DECISION_PENDING 1), G03-IP-01~03, Preserve 42 | Human Approval NOT_RECORDED, Contract Freeze 미실행 | 사람 |
| | Runtime Pending 확인 | READY_FOR_RUNTIME_DESIGN Canonical 45개 (§3 상태 축), PRE_MIGRATION Runtime 21 + NOT-EV-05(a) | RUNTIME_PENDING 전부 | G-04 |
| | Contract Freeze 선행 조건 | Human Approval (§3), Taxonomy 승인 (§9), D-17 (C-41), D-21 (Substitute 최종 Contract) | APPROVAL_PENDING | 사람 |
| **L1 Migration** | Preservation / Regression 보호 | §11.2 NCR / MCR, §5.3 EXPECTED_TO_PRESERVE | Test 미작성 / 미실행 | G-04 |
| | Kotlin 전환 시 바꾸지 않을 범위 | Preserve 42 (§5), A-01 §17.8 / A-04 §20.12 / A-06 §27.11 금지 목록, D-01~D-28 INTERIM_BEHAVIOR (§8.2) | Decision 28 PENDING (모두 NON_BLOCKING_PRESERVE_AS_IS) | G-05 |
| **L2 Deepening** | Runtime으로 확인할 정합성·동시성·결함 후보 | §6.3 Defect Candidate 16 (DR 경로), G01-RG-05 / 06 / 07 / 09 / 10 / 12 | Verified Defect 0 | G-04 → G-05 |
| | Contract 기반 Hardening 후보 | SR-AUTH-01 / 03, SR-WP-03, SR-PAT-02, SR-SCH-01 / 02, SR-UNA-01, SR-SUB-01~03, SR-NOT-01~03, D-23 (BEFORE_HARDENING) | 재현 + Contract 위반 확인 전 채택 안 함 | G-05 |
| **L3 Expansion** | Membership Lifecycle | DG-02 (D-11~D-16), SR-WP-01 / 02 / 05, SR-PAT-01, SR-SCH-04, SR-UNA-03, SR-SUB-05 | DECISION_PENDING | 팀 / A-02 |
| | Substitute Expiry / Scheduler | D-19, D-20, SR-SUB-04, SR-NOT-04 | DECISION_PENDING (Scheduler / Lazy / Hybrid 미선택) | 팀 (A-06) |
| | Notification Reliability / Outbox | D-22 (AFTER_RUNTIME), SR-NOT-01 / 02 | DECISION_PENDING + EVIDENCE_PENDING (Retry / Outbox 미채택) | COMMON / 사람 |
| | 상태 어휘 확장 / 기타 | D-17, D-18, D-24, D-26, D-27, D-28, SR-AUTH-04~06, SR-SCH-03, SR-NOT-05, SR-WP-04 | DECISION_PENDING | 팀 / O-01 |

G-02 §14 G-05 Handoff 7행은 그대로 `CARRIED_FORWARD_G05`다 (§10.4).

---

# 13. New Findings / Open Questions

새 Defect는 만들지 않았다. 아래 Finding은 문서 통합 중 확인한 사실이며 Product Decision이 아니다.

## 13.1 New Findings

| ID | 관련 | 내용 | 근거 | Category (G-02 분류 사용) | Next |
|---|---|---|---|---|---|
| G03-NF-01 | G-01 §4.3 (Business 68 / Implementation Preserve 3) | 68개 Business Contract 성격 ID 중 **12개**는 원문이 Contract 부분과 Preserve / Decision 부분을 함께 기록했다 (AUTH-MIG-05, WP-MIG-04, PAT-MIG-07, SCH-MIG-15, UNA-MIG-11, SUB-MIG-01, SUB-MIG-04, SUB-MIG-05, SUB-MIG-09, NOT-MIG-02, NOT-MIG-03, NOT-MIG-06). G-01 집계가 틀린 것은 아니며, Regression 기대값 범위를 정할 때 부분 분리가 필요하다는 사실이다 | §6.2, 각 Review 원문 | TRACEABILITY_GAP | G-03 내부 처리 완료 (§6.2), G-04 기대값 설계 시 적용 |
| G03-NF-02 | SUB-MIG-01 | 원본 Contract ID 71개 중 SUB-MIG-01은 정상 Contract 여부 자체가 미결정이다 (HR-SUB-02 RECLASSIFY). 그래서 "Contract ID = 정상 Contract"로 읽으면 안 된다. Coverage는 매핑 완료, Contract 확정은 보류 | A-06 HR-SUB-02, §3.10 | PRODUCT_DECISION_PENDING | 사람 (D-17) |

## 13.2 Open Questions (Decision Candidate — G-03에서 채택하지 않음)

| ID | 질문 | 왜 열려 있는가 | 관련 | 다음 Owner |
|---|---|---|---|---|
| G03-OQ-01 | AUTH-EV-01에서 `/workplaces/**` Matcher가 적용되어 어떤 Endpoint가 401이 아닌 결과를 낸다면, Kotlin Migration은 그 결과를 Java와 같게 둘 것인가(P-02 Matcher 보존), C-05에 맞출 것인가 | P-02는 "AUTH-EV-01 + COMMON 결정 전 Matcher 변경 금지"이고, C-05는 정상 Contract다. 두 기록은 모순이 아니라 **Runtime 결과에 따라 갈리는 처리 순서**의 문제다. A-01 §17.8은 Security Matcher 변경을 Migration과 분리하라고 적는다 | C-05, P-02, WP-DC-01, PAT-DC-01, SR-AUTH-02 | AUTH-EV-01 결과 후 사람 (G-04 → G-05) |
| G03-OQ-02 | Canonical Contract의 `REVIEW_ACCEPTED`를 Contract Freeze 입력으로 그대로 승인할 것인가 | 원 Review의 수용은 Product 정책 승인이 아니다 (프롬프트 §0.1). 승인 기록이 0건이다 | §3 전체 | 사람 |
| G03-OQ-03 | MERGE_CANDIDATE 2그룹(403 / 404 노출, 성공 code)을 COMMON에서 하나의 결정으로 답할 것인가 | 원문이 ID를 따로 두었고, 같은 답이 모든 Domain에 맞는지 확인되지 않았다 | D-01, D-04, D-06, D-08~D-10 | COMMON / 사람 |

---

# 14. G-03 Final Gate

| Gate | 판정 | 근거 / Follow-up |
|---|---|---|
| 1. Input / Baseline 접근 | **PASS** | G-01 / G-02 최신본 전체, A-01~A-07 관련 절, AI-GUIDE / SOURCE-INDEX / AUDIT-MAP 접근. HEAD = Baseline, Working Tree 0건. 출력 파일 작업 전 0바이트 (§1) |
| 2. Contract 원본 71개 ID 추적 | **PASS** | 71 = 매핑 71 + 미매핑 0. MISSING / UNEXPECTED / DUPLICATE / UNMAPPED 모두 0 (Script 검산, §3.10). Canonical 59 |
| 3. 공통 Invariant 연결 | **PASS** | G01-CR-01~14 → G03-INV-01~14, 프롬프트 §6 필수 12개 항목 연결. 새 불변조건 채택 없음 (§4) |
| 4. Preserve 42개 항목 추적 | **PASS** | 42 / 42, 누락·중복 0. Kind (IMPLEMENTATION 15 / BEHAVIOR 27), CHANGE_GATE, Kotlin 비교 분류 (§5) |
| 5. Contract와 Preserve 혼동 방지 | **PASS** | G-02 §9 필수 Boundary 6건, 혼합 ID 12건 부분 분리, Defect Candidate 16건의 비정상 결과를 기대값에서 제외 (§6) |
| 6. Decision 29개 ID 추적 및 중복 연결 | **PASS** | 29 → 28 → 10 축 분리, 누락·중복 0, MERGE_CANDIDATE는 합치지 않음 (§7.5) |
| 7. 사람 Decision 임의 확정 없음 | **PASS** | Decision Status PENDING 29 / DECIDED 0. 선택지는 원문에 있는 것만, 없으면 NO_OPTIONS_IN_SOURCE. 원문 비차단 항목을 차단으로 올리지 않음 (§7, §8) |
| 8. Taxonomy Original / Proposed / Approval 분리 | **PASS_WITH_FOLLOW_UP** | 모든 변환 `PROPOSED / APPROVAL_PENDING` (§9). Follow-up: 사람의 Convention 승인 |
| 9. Source 및 문서 Backlog 반영 | **PASS_WITH_FOLLOW_UP** | G02-DOC-BL-01 보존, G02-NF-01~04 처리 위치, Backlog 7건 (§10). Follow-up: 실제 문서 수정은 승인 후 (NOT_DONE) |
| 10. G-04 검증 대상 연결 | **PASS_WITH_FOLLOW_UP** | Canonical 59행에 Test Purpose / Coverage / Level / Assertion / Evidence / 비교 연결 (§11). Follow-up: Coverage는 Audit 기록 기준(G-03 Test Source 미재확인), Test ID 없는 항목 (A-02 / 03 / 04 §절 번호, C-51 / C-56) 부여, Harness Owner 공백 (G01-F-05)은 G-04 |
| 11. G-05 Layer Handoff | **PASS** | Layer 0~3 입력에 G-03 ID / 원 ID / Pending / Owner 연결, Layer 3 미채택 유지 (§12) |
| 12. 단일 결과 파일 / Scope 준수 | **PASS** | 이 파일만 작성. 입력 문서 해시 변화 없음 (§15.2). Runtime / Test / Production / Migration 없음 |

**결과: PASS 9 / PASS_WITH_FOLLOW_UP 3 / HOLD 0.**

HOLD 검토: 핵심 원본 ID 누락 0, 정식 승인 없이 확정한 Contract 0, 채택한 Product Decision 0. 남은 것은 Human Approval, Product Decision, Runtime Evidence, G-04 / G-05 작업이며, 프롬프트 §15에 따라 이것만으로 HOLD하지 않았다.

---

# 15. Final Summary / Changes / Stop State

## 15.1 결과 요약

| 항목 | 결과 |
|---|---|
| Contract | 원본 71 / Canonical 59 (C 56 + IP 3) / 매핑 71, 미매핑 0 / 보류 1 (SUB-MIG-01) / 혼합 ID 12 부분 분리 |
| Contract Status 5축 | Disposition: REVIEW_ACCEPTED 54, CONDITIONAL 1 (C-40), DECISION_PENDING 1 (C-41), IMPLEMENTATION_PRESERVE 3. 일부 EVIDENCE_PENDING 부분 4 (C-06, C-21, C-53, C-55) / Human Approval: APPROVED_WITH_EVIDENCE 0 / Runtime: RUNTIME_VERIFIED 0 / Migration Gate: READY_FOR_RUNTIME_DESIGN 45 (그중 C-40은 Hard Block 정책 확정에 BLOCKED_BY_SUB-DN-02 병기), CANDIDATE 13, BLOCKED_BY_SUB-DN-01 1 (C-41). 모두 Script로 §3 상태 축 59행을 다시 세어 확인 |
| Invariant | G01-CR-01~14 → G03-INV-01~14 |
| Preserve | 42 / 42, IMPLEMENTATION 15 / BEHAVIOR 27 / 미분류 0, Kind 제안 승인 대기 |
| Decision | 원본 29 / Canonical 질문 28 / 묶음 10 / PENDING 29 / Blocking: NON_BLOCKING_PRESERVE_AS_IS 28 (Contract 확정 축 CONDITIONAL_BLOCK 병기 2), BLOCKS 0, UNDETERMINED 0 |
| Taxonomy | 변환 전부 PROPOSED / APPROVAL_PENDING |
| 문서 Backlog | 7건 (G02-DOC-BL-01 + G03-DOC-BL-01~06), 실제 수정 0 |
| G-02 Handoff | §12 15 / 15 처리 위치 있음, §13 14행 · §14 7행 CARRIED_FORWARD |
| New Finding / Open Question | G03-NF-01~02 / G03-OQ-01~03 (Defect 신규 0) |
| Final Gate | PASS 9 / PASS_WITH_FOLLOW_UP 3 / HOLD 0 |

## 15.2 작성 / 변경 파일

- 작성: `reviews/G-03-contract-preserve-decision-review.md` (작업 시작 시 0바이트)
- 입력 문서: 작업 시작 시 기록한 47개 파일 SHA-256과 종료 시 값을 비교했다. G-03 출력 파일 외 변화 없음 (종료 시 확인)
- Production Repository: HEAD = Baseline, Working Tree 변경 0건 (종료 시 확인)
- 임시: Scratchpad에 ID 검산 Script (`g03ids.py`, `g03check.py`)와 해시 목록 (`g03-before.txt`)
- Human Follow-up (Targeted): §1.1 Prompt 줄 수 표기 정정과 §1.5 추가, §3.8.3 / §11.2.1 (C-54 ③), §11.2.2 (C-55 실패 위치별 검증) 추가, 관련 행(§3.8 C-54, §6.1, §7.3 D-18, §10.1 G03-DOC-BL-01, §10.2 NF-01, §11.2 C-54 / C-55) 표현 정리. 집계, ID, Decision Status, Defect State, Final Gate는 바꾸지 않음. 수정 전 사본은 Scratchpad `g03-before-followup.md`

## 15.3 실행하지 않은 작업

Test, Gradle, Application, DB / MySQL / Testcontainers, HTTP Request, Concurrency Harness, Product Decision, Contract Freeze, Human Approval 기록, Kotlin Migration, Refactoring, Hardening, Git 상태 변경(Checkout / Reset / Clean / Pull / Rebase / Merge / Commit / Push / Branch 변경), 기존 Audit / Review / G-01 / G-02 / AI-GUIDE / SOURCE-INDEX / AUDIT-MAP / Evidence / Source / Prompt 수정은 하지 않았다.
G-04와 G-05는 시작하지 않았다.

## 15.4 최종 상태

`G03_CONTRACT_PRESERVE_DECISION_INTEGRATED / GLOBAL_CONSISTENCY_PENDING / RUNTIME_PENDING / MIGRATION_NOT_STARTED`

이 상태는 G-03 종료 상태이며 Global Final Consistency 전체 완료가 아니다.
다음 상태는 선언하지 않는다: `CONTRACT_FREEZE_COMPLETE`, `JAVA_RUNTIME_VERIFIED`, `MIGRATION_READY`, `KOTLIN_MIGRATION_STARTED`, `HARDENING_COMPLETE`, `SERVICE_READY`.
