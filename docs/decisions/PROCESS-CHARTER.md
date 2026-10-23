# 정책 결정 Process Charter

> ③ 문서 리뷰: **DRAFT** (2026-10-23). **팀 승인 전 초안**이다.
> 이 문서는 정책 카드를 만들고 결정하는 **절차**만 정한다. 업무 정책은 정하지 않는다.
> `docs/ai/AI-RULES.md`, `docs/STATUS-AXES.md`, `docs/decisions/DECISION-LOG.md`의 규칙과 상태 체계를 바꾸지 않는다. 이 문서와 그 문서들이 다르면 **그 문서들이 우선**이다.

## 1. 목적

SWITCH V3는 Java → Kotlin Migration을 하면서, 사용자 Flow가 끝까지 닫히는지를 기준으로 정책을 정하고 그 정책을 상태 전이·검증·Test로 내린다.
결과는 Domain마다 세 가지 계약으로 남긴다.

| 계약 | 담는 내용 | 위치 |
|---|---|---|
| 서비스 계약 | 어떤 기능을 누가 쓰는가. 정상·실패·종료 시 무엇을 보장하는가 | `docs/delivery/<Domain>.md` H절 (Flow), 정책 카드 |
| 개발 계약 | API·상태 전이·DB 불변조건·권한·Transaction·동시성 | Delivery Sheet B·C절, `docs/contract/CONTRACT-BASELINE.md` |
| 검증 계약 | 필수 Test, 층위, 환경(MySQL/H2), 실행 근거 | Delivery Sheet D·E절, `docs/evidence/TEST-INVENTORY.md`, `TEST-EVIDENCE.md` |

## 2. Java AS-IS와 Improvement 구분

Java AS-IS는 **변경 전 동작을 알려 주는 기준선**이다. 정답 계약은 아니다 (`docs/README.md` §3 순위 4·5).
항목마다 아래 셋 중 하나로 판정한다.

| 판정 | 뜻 | Kotlin에서의 처리 |
|---|---|---|
| 보존 | 맞는 동작이거나, 한계가 있어도 범위상 받아들임 | 그대로 옮기고 한계를 문서에 적는다 |
| 결함 수정 | 보안·정합성 결함 | 그대로 복제하지 않는다. 전후 동작, 승인, Test 기준을 적은 **별도 PR**에서 고친다 |
| 개선 (Improvement) | 결함은 아니지만 더 나은 정책 | 승인 후 Improvement PR |

- AI-RULES §2의 "Migration PR에서 동작을 바꾸지 않는다"는 위 표와 충돌하지 않는다. 금지하는 것은 **Migration PR 안에서 섞어 고치는 것**이다.
- 결함 수정을 Java에서 먼저 할지, Kotlin 전환 후에 할지는 D-NOW-04 (`PENDING`)가 정한다.
- MVP 범위 밖 기능(예: 공개 후 Shift 정정)은 구현하지 않아도 된다. 대신 할 수 없다는 사실, 사용자가 겪는 한계, 나중에 도입할 때의 선행 조건을 적는다.
- MVP가 제공한다고 한 기능(예: 대타 승인 → 공식 Shift 담당자 변경)이 동작하지 않으면 결함이다. Migration 완료를 막는다.

## 3. 근거와 결정 단계 구분

같은 행에 아래 단계를 섞어 쓰지 않는다.

| 단계 | 뜻 | 기록 위치 | 표기 |
|---|---|---|---|
| 코드 사실 | 코드를 읽거나 실행해서 확인 | 카드 "AS-IS" 절, Delivery Sheet | ① `VERIFIED_AS_IS` + 코드 위치 |
| 이전 제안 | 과거 AI 분석·제안 원문 | `docs/references/` | 원문 상태 그대로 (예: `PROPOSED`). 현행 계약 아님 |
| 사용자 잠정 결정 | 작업자가 카드에서 고른 문장 | 카드 "사용자 잠정 결정" 절 | "사용자 잠정 결정" + 카드의 ② `PROPOSED` |
| 팀 승인 | 팀이 결정 문장을 승인 | `DECISION-LOG` 해당 행 | ② `APPROVED` + 결정 내용·승인자·일자·링크. **사람만 기입** |
| 구현 | 코드가 저장소에 있음 | PR, Delivery Sheet G절 | PR 링크 |
| Test Evidence | 개별 Test 결과 | `TEST-EVIDENCE` → `TEST-INVENTORY` | ④ `SPECIFIED`/`IMPLEMENTED`, ⑤ `PASSED`/`FAILED`/`NOT_RUN`/`UNKNOWN` |

### 3-1. 카드 상태와 `DECISION-LOG` 상태

- 정책 카드(`docs/decisions/proposals/`)는 ② `PROPOSED`로 표시한다 (`STATUS-AXES`의 "구체안이 있지만 승인 전").
- `DECISION-LOG`는 기존 값(`PENDING` / `APPROVED` / `REJECTED` / `DEFERRED`)만 쓴다. `PROPOSED`를 새 상태로 넣지 않는다. 카드가 있는 행은 `PENDING`을 유지하고 카드 링크만 연결한다 (`DECISION-LOG` §3).
- "사용자 잠정 보류"는 `DEFERRED`가 아니다. `DEFERRED`는 팀이 보류를 결정한 경우에만 쓴다.

### 3-2. 제안서 PR 병합과 팀 승인

| 사건 | 바뀌는 것 | 바뀌지 않는 것 |
|---|---|---|
| 제안서 PR 리뷰 Approve | 없음 (PR 승인일 뿐) | ② 상태 |
| 제안서 PR 병합 | 카드 ③ → `PUBLISHED` (팀 저장소에 실림) | ② `PROPOSED`, `DECISION-LOG` `PENDING` |
| 팀 승인 | `DECISION-LOG` 행 → `APPROVED` (사람이 결정 내용·승인자·일자·근거 링크 기입) | — |

- `DECISION-LOG` 머리말처럼 "GitHub PR 리뷰 Approve는 이 Log의 `APPROVED`가 아니다."
- 팀 승인의 근거는 회의록 또는 PR 대화에서 **결정 문장을 명시적으로 승인한 기록**이다. 승인 방식(회의 / PR 댓글 / 승인 인원)은 팀이 정한다 (미결정).
- 승인되면 `DECISION-LOG` 기록 규칙의 순서를 따른다: Decision Log → `CONTRACT-BASELINE` → Test 기대값 → 코드 (각각 PR).

## 4. 사용자 Path 종료 판정 7개 질문

카드는 아래 질문에 답해서 사용자 Path가 닫히는지 판단한다.

| 기준 | 질문 | 예 (대타) |
|---|---|---|
| 시작 | 누가 어떤 조건에서 실행할 수 있는가 | 현재 소속 직원만 수락 |
| 성공 | 성공하면 DB와 사용자 화면에 무엇이 남는가 | 승인 후 공식 Shift 담당자 변경 |
| 실패·종료 | 실패·만료·거절 후 업무가 어떻게 끝나는가. **누가 끝낼 수 있는가** | 만료 요청 승인 거부. 아무도 끝낼 수 없는 상태가 남으면 닫히지 않은 Path |
| 재시도·복구 | 실패하거나 중단되면 다음에 무엇을 할 수 있는가 | 관리자 종료 후 새 요청 |
| 정합성·동시성 | 동시에 실행해도 무엇이 깨지면 안 되는가 | 같은 Shift 중복 승인 방지 |
| 사용자·운영 관측 | 결과를 어디서 확인하고 문제를 어떻게 발견하는가 | 근무표 재조회, 오류 기록 |
| 범위·검증 | 무엇을 보장하고 무엇을 미루는가. **근거 수준**은 무엇인가 (정적 확인 / Mock Test / DB Test / 동시성 재현 / 실행 Evidence) | P0 필수 Test와 후속 개선 분리 |

세 층위를 함께 본다. 어긋나는 곳이 닫히지 않은 Path의 후보다.

| 층위 | 질문 | 대타 만료 예 |
|---|---|---|
| 업무상 종료 | 더 진행할 수 없는가 | `now ≥ startAt` |
| 저장 상태 | DB에 무엇이 남는가 | OPEN 유지 |
| 사용자 표시 | 사용자가 결과를 이해하는가 | CLOSED / EXPIRED 표시 |

## 5. 정책 카드

### 5-1. 크기

- 카드 하나 = 사용자가 고를 질문 **1~3개** + 그 결과로 바뀌는 API·DB·상태·Test 목록.
- 이보다 커지면 카드를 나눈다.
- 결정은 카드 하나씩 한다. 구현 착수는 서로 묶인 카드가 모두 결정된 뒤 묶음 단위로 한다 (예: 퇴사는 ML-H1~ML-5).

### 5-2. 진행 순서

| 단계 | 누가 | 산출 |
|---|---|---|
| 1. 카드 제시 | 작성자 (AI 가능) | 코드 사실(위치), 선택지, 장단점, 추천과 이유, 놓치기 쉬운 점, 질문 1~3개 |
| 2. 의견 | 사용자 (외부 AI 의견 참고 가능) | 의견, 우려 |
| 3. 검증 | 작성자 | 의견을 코드로 다시 확인, 위험, 보완 |
| 4. 확정 문장 합의 | 사용자 | "이 문장으로 기록" 확인 |
| 5. 문서 작성 | 작성자 | 카드 파일, `DECISION-LOG` §3 연결, `TEST-INVENTORY` `SPECIFIED` 행 |
| 6. Diff 확인 → Commit → PR | 사용자가 단계마다 승인 | AI-RULES §8: Commit / Push / PR 생성은 각각 승인 |
| 7. 팀 리뷰·승인 | 팀 | §3-2 |

외부 AI 의견을 다루는 규칙:
- 3단계의 검토 자료로만 쓴다.
- "사용자 잠정 결정" 칸에는 4단계에서 사용자가 확인한 문장만 적는다.
- 채택한 지적은 카드의 "검토 반영"에 한 줄로 남긴다.
- 사실 판단에는 코드 위치를 붙인다.

### 5-3. 새 질문을 발견했을 때

- 현재 카드의 결정에 꼭 필요하면 현재 카드에 넣는다. 질문 수가 3개를 넘으면 카드를 나눈다.
- 아니면 카드의 "후속 카드로 넘긴 항목" 표에 적고, 받을 카드 ID를 붙인다. 아직 없는 카드면 ID만 예약한다.
- 기존 `DECISION-LOG` 질문(G03-D-*, DL-*)과 같은 내용이면 새 ID를 만들지 않고 그 ID를 쓴다.

### 5-4. 카드에 반드시 남길 것

- 재검토 조건 (무엇이 바뀌면 이 카드를 다시 여는가)
- 다른 Domain 영향과 리뷰 필요 여부
- 후속 카드로 넘긴 항목
- 변경 이력 (일자, 무엇을, 왜)

이것이 있어야 나중 카드에서 앞 카드를 처음부터 다시 조사하지 않는다.

### 5-5. ID 규칙

- 카드 ID: 기존 Decision ID가 있으면 그 ID(예: `G03-D-19`). 새 묶음이면 묶음 접두어 + 번호(예: `ML-H1`, `AUTH-1`).
- 카드 안의 불변조건: `<카드 ID>-INV-nn`. 같은 뜻의 기존 ID(`G03-INV-*`, G-03 C-ID)가 있으면 새로 만들지 않고 참조한다.
- 카드 안의 Test: `TEST-INVENTORY` 형식(`{V}-N-nn`)으로 등록하고, 카드 번호(예: `ML-H1-T1`)는 별칭으로 남긴다.

### 5-6. 카드 형식

```text
# <ID> <제목>
상태 표: 결정 단계 / ② / ③ / 구현 / ④·⑤ / 기준 코드 / 관련 ID / 주관·리뷰
1. 왜 지금 정해야 하나
2. AS-IS (① VERIFIED_AS_IS, 코드 위치)
3. 이전 제안 (원문 위치)
4. 사용자 잠정 결정 (+ 이유, 검토 반영)
5. 보장 범위와 제외 범위 (사용자에게 생기는 제약)
6. 상태 전이 (허용 / 금지)
7. 불변조건
8. API·DB·Frontend 영향 (기존 계약 변경 여부)
9. 검증 계약 (신규 Test, 기존 Test 회귀)
10. 재검토 조건
11. Cross-Domain 영향과 리뷰
12. 후속 카드로 넘긴 항목
13. 변경 이력
```

## 6. Test 기대값 변경 절차

AI-RULES §2를 그대로 따른다: 기존 Test의 기대값·Assertion을 바꾸지 않고, `@Disabled`·삭제로 통과시키지 않는다. 아래는 그 규칙을 적용하는 방법이다.

### 6-1. 무엇이 "기대값·Assertion 변경"인가

다음은 모두 변경이다. Assertion **약화**도 포함한다.
- 기대 Status·`code`·ErrorCode·DB 최종 상태 값을 바꾸는 것
- 구체적인 값 비교를 넓은 비교로 바꾸는 것 (예: 특정 ErrorCode → 아무 `BusinessException`, 정확한 값 → `isNotNull`)
- 확인 항목을 빼는 것 (예: `verify(..., never()).save(...)` 삭제)
- Test를 `@Disabled` 처리하거나 지우는 것

### 6-2. 허용되는 경우

- `DECISION-LOG`에 그 기대값 변경을 근거로 하는 `APPROVED` 행이 있을 때만 바꾼다.
- 순서는 `DECISION-LOG` 기록 규칙을 따른다: Decision Log → `CONTRACT-BASELINE` → Test 기대값 → 코드.
- PR에 Decision ID, 바뀌기 전과 후의 기대값, 이유를 적는다.

### 6-3. 준비 코드(Arrange) 변경 (제안 — AI-RULES에 아직 없는 규칙)

구현 방식이 바뀌어 Mock 준비나 Fixture만 바뀌어야 할 때가 있다.
예: `MockitoExtension`의 Strict Stubs에서는 쓰이지 않는 Stub이 `UnnecessaryStubbingException`으로 실패한다.

- 기대값·Assertion은 그대로 둔다 (§6-1에 해당하지 않아야 한다).
- **같은 업무 상황**을 검증하는지 리뷰어가 확인한다 (예: "현재 소속자가 다시 가입" 상황이 그대로인지).
- PR에 근거 카드·Decision ID, 바뀐 준비 코드, 이유를 적는다.
- 이 절차는 팀 승인 전 제안이다. 승인되면 AI-RULES에 반영할지 팀이 정한다.

### 6-4. 새 정책의 Test

- ② `PROPOSED` 정책의 Test는 ④ `SPECIFIED`까지만 쓴다 (`STATUS-AXES`). 승인 전에는 Test 코드를 작성하지 않는다.
- 현재 Java 동작을 고정하는 Test(`MIGRATION`)는 정책 승인 대상이 아니다. Task 승인 후 작성한다 (`TEST-INVENTORY` §3-4).

## 7. Cross-Domain Owner 리뷰

- 카드는 "Cross-Domain 영향과 리뷰" 표에 Domain(V1~V5, Frontend)마다 확인할 내용과 **필수 / 확인 / 없음**을 적는다.
- 주관 Domain Owner는 카드 전체를 본다. 다른 Domain Owner는 자기 Domain 행만 본다.
- Owner가 확정되기 전(D-NOW-01 `PENDING`)에는 이름 대신 역할(예: "V4 Owner")로 적는다.
- 필수 리뷰가 끝나지 않은 카드는 팀 승인 대상으로 올리지 않는다.

## 8. 완료 기준 구분

| 완료 | 기준 | 뜻하지 않는 것 |
|---|---|---|
| 카드 초안 완료 | §5-6 절이 모두 채워짐. 사용자 잠정 결정 문장 확인됨 | 팀 승인 |
| 카드 공개 | 제안서 PR 병합 (③ `PUBLISHED`) | 팀 승인 |
| 팀 승인 | `DECISION-LOG` 행 `APPROVED` | 구현 |
| 구현 완료 | Improvement PR 병합, 관련 Test ④ `IMPLEMENTED` | 실행 검증 |
| 검증 완료 | 관련 Test ⑤ `PASSED` (개별 결과 + EV-ID) | Migration 완료 |
| Migration 완료 | Delivery Sheet E절 DoD 10개 충족 (Improvement 미포함) | — |

## 9. 문서 위치

| 위치 | 역할 |
|---|---|
| `docs/references/` (`ml/` 포함) | 과거 분석·제안 원문, 출처, SHA, 대체 관계. 현행 계약 아님 |
| `docs/decisions/proposals/` | 지금 검토 중인 정책 카드 (② `PROPOSED`) |
| `docs/decisions/DECISION-LOG.md` | 기존 Decision ID와 공식 결정 상태 |
| `docs/delivery/` | Domain 담당자가 구현할 계약 |
| `docs/evidence/` | Test Inventory와 실제 실행 근거 |

## 10. 변경 이력

| 일자 | 내용 |
|---|---|
| 2026-10-23 | 초안. 작성자 로컬 대화에서 합의한 진행 방식(판정 3분류, 7개 질문, 카드 형식·순서)을 정리 |
