# 참고 자료 (References)

> ③ 문서 리뷰: **DRAFT** (D0-E-2B, 2026-10-23).
> 이 디렉터리의 자료는 **검토 후 선별해 공유한 참고 자료**다. 공식 계약도, 승인된 정책도, 구현 지시도 아니다.
> 팀이 승인한 정책·계약은 [`DECISION-LOG`](../decisions/DECISION-LOG.md)와 [`CONTRACT-BASELINE`](../contract/CONTRACT-BASELINE.md)의 `APPROVED` 행만이다 (현재 0건). 우선순위는 [`docs/README.md`](../README.md) §3을 따른다 (AI 분석 보고서는 7순위).

## 1. 목록

| 파일 | 원본 | 작성 시점 | 기준 Java SHA | 내용 | 3차 문서에서의 쓰임 |
|---|---|---|---|---|---|
| [`G-03-contract-review.md`](G-03-contract-review.md) | 작성자 로컬 `switch-v3-ai/reviews/G-03-contract-preserve-decision-review.md` | 2026-10-21 (AI 분석, Human Follow-up 반영본) | 2차 Original `985861263c576308168ca539159552d4a4e47606` | Canonical Contract C-01~C-56, Implementation Preserve IP-01~03, Decision Register G03-D-01~28, Open Question G03-OQ-01~03 | `CONTRACT-BASELINE`·`MIGRATION-MATRIX`·`DECISION-LOG`·Delivery Sheet·Test Inventory가 C-ID·G03-D-ID로 참조 |
| [`ml/ML-DECISION-01-FINAL.md`](ml/ML-DECISION-01-FINAL.md) | 작성자 로컬 AI 대화 Log | 2026-10-22 | 2차 `8da2bec` | 퇴사 Command 패키지 (과거 제안) | 정책 카드 ML-H1·ML-2~5의 이전 제안 (§7) |
| [`ml/ML-SUB-STATE-01.md`](ml/ML-SUB-STATE-01.md) | 작성자 로컬 AI 대화 Log | 2026-10-22 | 2차 `8da2bec` | 대타 후보 자격 상실 B+ (과거 제안) | 정책 카드 ML-3의 이전 제안 (§7) |
| [`ml/ML-CONCURRENCY-01-R.md`](ml/ML-CONCURRENCY-01-R.md) | 작성자 로컬 AI 대화 Log | 2026-10-22 | 2차 `8da2bec` | 퇴사 경합 Membership Guard Lock (과거 제안) | 정책 카드 ML-5의 이전 제안 (§7) |

기준 SHA 차이: G-03은 Original `9858612` 기준이다. 3차 문서의 Java 기준(2차 Snapshot `243f77c`, Baseline 후보 `8da2bec`, 3차 `cdaaa13`)과 Production Java 코드는 같지만 설정이 다르다 (전역 READ COMMITTED 등, `docs/README.md` §4-1). G-03은 이 설정 차이를 반영하지 않는다.

## 2. 사용 방법

1. C-ID·G03-D-ID가 붙은 행을 읽을 때 원문 규칙과 근거를 확인하는 용도로만 쓴다.
2. 현재 동작은 코드에서 다시 확인한다. G-03과 코드가 다르면 어느 쪽도 고르지 않고 `DECISION-LOG`에 `DISCREPANCY / PENDING`으로 기록한다 (`docs/ai/AI-RULES.md` §3).
3. G-03의 Decision 후보·선택지·제안은 결정이 아니다. 질문과 상태는 `DECISION-LOG` §2에서 관리한다.
4. G-03의 Test·Runtime 계획(§11)은 G-03 시점의 제안이다. 현재 Test 상태는 [`TEST-INVENTORY`](../evidence/TEST-INVENTORY.md), 실행 근거는 [`TEST-EVIDENCE`](../evidence/TEST-EVIDENCE.md)를 본다.
5. Membership Lifecycle 구체안(ML-DECISION-01, ML-SUB-STATE-01, ML-CONCURRENCY-01-R)은 G-03 이후 별도 분석이며 G-03에 없다. G-03에는 질문(G03-D-11~16)만 있다. 구체안 원문은 `ml/`에 있다 (§7). 지금 검토 중인 정책 카드는 `docs/decisions/proposals/`에 있고, 원문과 카드가 다르면 **카드가 최신 검토본**이다 (그래도 팀 승인 전).

## 3. 원본과 공유본의 차이

| 항목 | 값 |
|---|---|
| 원본 SHA-256 | `188689f88a17b418ebed8d2633c922ac62b0a34fde642ad3ea59dac7e37fbf68` (1,221줄) |
| 공유본 SHA-256 | `67418eb0eaa8964451b69af1a97eb8bc5a7317baee8526d010cf5a5e65d1c1f9` (1,235줄) |
| 공유본 본문 (머리말 제외) SHA-256 | `a04f55c459564ce4ebbd28765196be2aa374f7df0144e0ed20ce4e4e474f4a65` |
| 추가 | 맨 앞 머리말 14줄 (HTML 주석 + 참고 자료 안내 blockquote) |
| 변경 | 본문 1줄: §1.2 Environment Gate 표의 "AI 작업공간" 값. 개인 절대 경로(`/Users/<사용자>/…/switch-v3-ai`)를 "`switch-v3-ai/` (작성자 로컬 분석 작업공간, 저장소 밖 …)" 설명으로 바꿈 |
| 바꾸지 않음 | 나머지 1,220줄 전부: 계약 본문, C-ID·IP-ID·G03-D-ID·OQ-ID, 모든 상태 값, 근거 표기, §1.2의 상대 경로 `../NBE12-14-2-Team01` (2차 저장소 Clone을 가리키는 작성 당시 기록) |
| 공유 전 검사 | Secret·Token 값·Password·Email·전화번호·외부 URL·개인 이름: 0건. Token은 개념 설명으로만 등장 |

검증 방법: 공유본에서 머리말 14줄을 뺀 내용과 원본을 `diff`하면 위 1줄만 다르다.

## 4. G-03 상태 용어와 `docs/STATUS-AXES.md`의 대응

G-03의 상태 용어는 G-03이 제안한 표기다 (G-03 §2.2, §9). 이 대응표는 읽는 방법일 뿐 G-03의 값을 바꾸거나 새 상태값을 만들지 않는다.

| G-03 축 / 값 | 뜻 (G-03 §2.2) | STATUS-AXES로 읽는 법 |
|---|---|---|
| 근거 `BSV(Review)`, `BSV(G-02)` | Original `9858612`에서 정적으로 확인한 사실 (G-03은 코드를 다시 읽지 않음) | ① `VERIFIED_AS_IS`에 해당하는 근거이나 기준 SHA가 `9858612`다. 3차 문서에 옮길 때는 현재 코드 위치를 다시 적는다 |
| 근거 `SR(G-02)` | 2차 명세 PDF 원문을 확인한 사실 | ① `SOURCE_STATED` |
| 근거 `RE`, `INF`, `UNKNOWN` | Review Evidence / 추정 / 미확인 | ① 대응 값 없음 (`RE`·`INF`), `UNKNOWN`은 ① `UNKNOWN` |
| Disposition `REVIEW_ACCEPTED` | 원 Review가 정상 Contract **후보**로 수용 (54건) | ① `REVIEW_ACCEPTED`. **② `APPROVED` 아님** |
| Disposition `CONDITIONAL` | 원문이 조건부로 기록 (C-40 1건) | 대응 값 없음. 원문 표기 그대로 인용, 승인 아님 |
| Disposition `DECISION_PENDING` | 정상 Contract 여부 자체가 미결정 (C-41 1건) | 계약 후보 아님. 관련 질문은 ② `PENDING` |
| Disposition `EVIDENCE_PENDING` | Contract 일부가 Evidence 전 미확인 (C-06·C-21·C-53·C-55 일부) | 해당 부분은 ① `UNKNOWN` |
| Disposition `IMPLEMENTATION_PRESERVE` | 정상 Contract가 아닌 구현 보존 대상 (IP-01~03) | 계약 아님. Migration 보존 대상 (`docs/ai/AI-RULES.md` §2·§5) |
| Human Approval `NOT_RECORDED`, `APPROVAL_PENDING` | 승인 기록 없음 / 승인 대상 Decision이 열려 있음. `APPROVED_WITH_EVIDENCE` 0건 | ② `PENDING` (승인 기록 없음) |
| Runtime `RUNTIME_PENDING (<ID>)`, `NOT_EXECUTED` | G-03 시점 실행 근거 없음. `RUNTIME_VERIFIED` 0건 | G-03 시점 ⑤ `NOT_RUN`. 현재 ⑤ 상태는 `TEST-INVENTORY` |
| Migration Gate `READY_FOR_RUNTIME_DESIGN`, `CANDIDATE`, `BLOCKED_BY_<ID>` | 다음 설계 단계로 넘길 준비 정도. `MIGRATION_READY` 아님 | 대응 값 없음 |
| Decision 분류 `DECISION_PENDING`, `CROSS_DOMAIN_DECISION`, `DEFER_PRODUCT_DECISION`, `HUMAN_DECISION`, `NEEDS_EVIDENCE` (§7) | 질문의 성격·시점 분류 | 모두 ② `PENDING`. `DECISION-LOG` §2 |

## 5. G-03 절과 3차 문서의 Domain(V) 배정

G-03은 C-ID를 원 Audit(A-01~A-07) 절로 묶는다. 3차 문서의 V 배정은 내용 기준이라 경계 3개가 다르다. C-ID와 내용은 같다.

| G-03 절 | C-ID | 3차 문서 V |
|---|---|---|
| 3.2 A-01 Auth | C-01~C-07 | V1 |
| 3.3 A-02 Workplace / Member | C-08~C-13 | V2 |
| 3.4 A-03 RegularShiftPattern | C-14~C-19 | V2 (C-14~C-17). **C-18·C-19는 V3** (Schedule 생성 시 Pattern Snapshot, SCH-MIG ID 포함) |
| 3.5 A-04 Schedule / Shift | C-20~C-32 | V3 |
| 3.6 A-05 UnavailableTime | C-33~C-40, IP-01 | V3 (C-33~C-39). **C-40은 V4** (Substitute 쪽 Unavailable 처리, UNA-MIG-11) |
| 3.7 A-06 Substitute | C-41~C-50, IP-02·03 | V4 |
| 3.8 A-07 Notification | C-51~C-56 | V5 |

## 6. 이번에 공유하지 않은 자료와 이유

모두 작성자 로컬(`switch-v3-ai/`, 2차 Worktree)에 있다. 필요해지면 같은 절차(검토 → 민감 정보 확인 → 차이 기록)로 선별 공유한다.

| 자료 | 분류 | 이유 |
|---|---|---|
| G-05 Roadmap / Migration Readiness | LATER | H1~H8, D-NOW-01~11의 출처지만 2026-10-22 03시 기준 일정·상태가 오래됨 (PR #76~78 OPEN, 3차 저장소 없음 시점). 용어 정의만 `docs/README.md` §5-1에 옮김 |
| G-04 Test / Runtime Integration | LATER | 정적 Test 조사 (실행 0). Domain Test 보강 시 필요한 부분만 |
| A-01~A-07 Review, Audit | LATER (Review) / LOCAL_ONLY (Audit) | G-03의 원천. Domain Owner가 필요할 때 Review만 |
| G-01, G-02 | LOCAL_ONLY | G-03에 통합됨 |
| 2차 명세 PDF (P0 정리본, COMMON Policy, API-*), 기획서, 피드백 정리안 | LATER | 팀 소유 원본. 저장소 복사보다 Notion 등 원 위치 링크가 적절 (DL-005) |
| 2차 피드백 Register | LATER | 원 피드백의 공유 범위는 팀 판단 |
| AI-GUIDE, AUDIT-MAP, SOURCE-INDEX, 프롬프트, scratch | LOCAL_ONLY | 분석 작업 도구. 상태 용어가 STATUS-AXES와 달라 혼동 위험. 역할은 `docs/ai/AI-RULES.md`가 대신함 |
| 초기 P0 진단 메모 | LOCAL_ONLY | 외부 대화 링크 포함, 오래된 제안 |
| D0-B / D0-C 초안, D0-D 인계서 | LOCAL_ONLY | 내용은 이미 `docs/`에 반영됨. 인계서는 로컬 세션 기록 경로 포함 |
| 로컬 세션 기록 (ML 분석 원문) | 일부 공유 (2026-10-23) | 최종본 3종만 `ml/`에 원문 그대로 공유 (§7, 사용자 결정 2026-10-23. 이전 기준 "정리해 옮김, 그대로 복사 금지"를 바꿈). 직전안·사용자 입력 메시지·Log 파일 자체는 LOCAL_ONLY |
| H6 Harness Source, 실행 Log | LATER (Harness, DL-009) / LOCAL_ONLY (Log) | Harness는 docs가 아니라 별도 Test PR로. Log는 개인 경로 포함, 요약은 `TEST-EVIDENCE` EV-001~007 |

## 7. ML 제안 원문 (`ml/`)

| 항목 | 내용 |
|---|---|
| 성격 | 과거 AI 제안 원문. **현행 계약 아님, 팀 승인 전.** 각 파일 머리말에 이후 대체 관계를 적었다 |
| 원본 | 작성자 로컬 Claude Code 대화 Log (2차 분석 작업공간 세션). 저장소에 없음 |
| 추가 | 각 파일 맨 앞 머리말 (HTML 주석 1줄 + 제목 + 안내 blockquote) |
| 변경 | 본문 0줄. 머리말 아래는 대화 Log의 Assistant 응답 Text를 그대로 옮김 |
| 공유 전 검사 | 본문에서 개인 절대 경로·Log 파일 경로·Secret·Token 값·Password·Email·외부 URL 검색: 0건. 머리말의 출처는 경로 없이 "작성자 로컬 AI 대화 Log"로 적음 |

| 파일 | 원본 위치 (1-based line, 시각) | 본문 SHA-256 | 대체·후속 |
|---|---|---|---|
| `ml/ML-DECISION-01-FINAL.md` | line 9431, 2026-10-22T07:26:20Z | `3856b3f836dd80c6524ce11e271b371bb4e9bd5a30dbc36ecff1d2c410b8c357` | 대타 정리 → ML-SUB-STATE-01로 대체. Lock 순서 → ML-CONCURRENCY-01-R로 대체. 이력·재가입 → 카드 ML-H1·ML-H2. 근무 처리·퇴사 Command → 카드 ML-2·ML-4 (§8) |
| `ml/ML-SUB-STATE-01.md` | line 9601, 2026-10-22T08:24:40Z | `58e23d1a33d925c70e9f457bf67e6e049007e09cb419ef4d8d2c06c01d336e67` | 후속 카드 ML-3 (§8) |
| `ml/ML-CONCURRENCY-01-R.md` | line 9678, 2026-10-22T08:49:49Z | `76caf22eea69d5ccbd6db860e1697b802ebf2cba29e8adcdcd1ce49aeb32b590` | 후속 카드 ML-5 (§8). 실행 근거 없음 (H6 미실행) |

검증 방법: 각 파일에서 머리말(첫 `---` 줄까지와 그 다음 빈 줄)과 파일 끝 줄바꿈 1개를 뺀 본문의 SHA-256이 위 값과 같다.

## 8. ML 원문과 후속 정책 카드의 대응 (2026-10-25)

원문 파일(`ml/`)은 수정하지 않는다. 후속 카드는 모두 **사용자 잠정 결정 / ② `PROPOSED`**이고 팀 승인 전이다. 원문과 카드가 다르면 카드가 최신 검토본이지만, 구현 기준이 되는 것은 `DECISION-LOG`의 `APPROVED` 행뿐이다 (현재 0건).

| 원문 | 원문의 내용 | 후속 카드 | 바뀐 점 |
|---|---|---|---|
| [`ML-DECISION-01-FINAL`](ml/ML-DECISION-01-FINAL.md) | 퇴사자의 미래 공식 근무 **전부 CANCELLED** | [ML-2](../decisions/proposals/ML-2-leave-shifts-and-published-changes.md) | 근무마다 관리자가 **인계 또는 취소** 선택 |
| 〃 | DRAFT·Pattern hard delete | ML-2 | DRAFT 삭제 + 목록 표시, **Pattern은 보존하고 자동 적용에서 제외** |
| 〃 | 공개 근무표에 근무 추가 (C-31 예외), 단독 취소 제외 | ML-2 (D-26·D-27) | 공개 후 추가·취소·담당자 변경을 별도 Command로. 시간 변경은 보류 |
| 〃 | 퇴사 Command (현재 MANAGER가 현재 EMPLOYEE를) | [ML-4](../decisions/proposals/ML-4-leave-command-and-access.md), [ML-H2](../decisions/proposals/ML-H2-rejoin-and-active-membership.md) H2-9 | 미리보기 → 확정, 재조회 409. 대상은 EMPLOYEE만 |
| 〃 | 종료된 내 소속 조회 | ML-4 | 마이페이지 "내 소속 이력", 빠진 근무 |
| 〃 | 재가입 UNKNOWN | [ML-H1](../decisions/proposals/ML-H1-membership-history.md) → ML-H2 | 새 Row로 재가입 포함 |
| [`ML-SUB-STATE-01`](ml/ML-SUB-STATE-01.md) | `INELIGIBLE` / `CANDIDATE_INELIGIBLE` (B+) | [ML-3](../decisions/proposals/ML-3-substitute-on-leave-and-shift-change.md) | 유지 (구성 규칙 포함) |
| 〃 | 요청자 퇴사 → `MANAGER_CLOSED` | ML-3 | **`SHIFT_CHANGED`** (근무 인계·취소가 원인) |
| [`ML-CONCURRENCY-01-R`](ml/ML-CONCURRENCY-01-R.md) | 순서 Schedule → Request → Shift → Membership, R-Gating, 퇴사는 User를 잠그지 않음 | [ML-5](../decisions/proposals/ML-5-concurrency-and-lock-order.md) | 순서 후보 확장 (Workplace → Schedule → Shift → Request → User → Membership), 승인은 Shift → Request로 변경 후보. **분석 후보, 실험 검증 대기** |
| 〃 | Deadlock·Lock 대기 초과 503 | ML-5, [G03-D-03](../decisions/proposals/G03-D-03-error-code.md) §5-3 | 기술 후보. MySQL·Spring Rollback 확인 후 적용 |
