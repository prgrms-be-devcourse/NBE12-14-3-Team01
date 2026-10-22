# 문서 상태 체계 (5개 축)

> ③ 문서 리뷰: **DRAFT** (D0-C-R 2026-10-23, D0-D 이관). Delivery Sheet(`docs/delivery/`)와 Test Inventory(`docs/evidence/TEST-INVENTORY.md`)가 공통으로 쓴다.
> 한 행에 여러 축이 붙을 수 있다. **서로 다른 축의 값을 같은 뜻으로 쓰지 않는다.**

| 축 | 값 | 뜻 | 누가 바꾸나 |
|---|---|---|---|
| ① 근거 (Source Evidence) | `VERIFIED_AS_IS` | 코드를 읽거나 실행해서 확인한 현재 동작 | 작성자 (근거 위치 필수) |
| | `REVIEW_ACCEPTED` | G-03 계약 후보 (C-ID). 분석 리뷰에서 받아들인 것이며 **팀 승인 아님** | — (G-03 기록) |
| | `SOURCE_STATED` | 2차 P0 정리본·API 명세 원문에 적힌 것 (원문 위치 필수) | 작성자 |
| | `UNKNOWN` | 확인 못 함 | — |
| ② 정책 승인 (Policy Approval) | `PENDING` | Decision Log에 질문만 있음 | — |
| | `PROPOSED` | 구체안이 있지만 승인 전. **구현 지시 아님** | 작성자 |
| | `APPROVED` | Decision Log에 결정 내용·승인자·일자·링크가 있음 | 사람만 |
| | `SUPERSEDED` | 더 새 문서·결정으로 대체됨 (대체한 문서 표기) | 작성자 |
| | `REJECTED` / `DEFERRED` | `DECISION-LOG`에서 기각 / 보류됨 (결정 내용·결정자·일자 필수) | 사람만 |
| ③ 문서 리뷰 (Document Review) | `DRAFT` → `REVIEWED` → `PUBLISHED` | REVIEWED = 리뷰어·일자 기록, PUBLISHED = 3차 저장소 `develop`에 병합 | 리뷰어 / 병합자 |
| ④ Test 준비 (Test Readiness) | `SPECIFIED` | 명세만 있음 | 작성자 |
| | `IMPLEMENTED` | Test 코드가 저장소에 있음 | — |
| | `BLOCKED` | 작성·실행할 수 없음 (사유 필수: 선행 결정, 환경) | 작성자 |
| ⑤ Test 실행 (Test Execution) | `PASSED` / `FAILED` | **이 Test의 개별 결과**가 있는 SHA·환경·근거(EV-ID 또는 Test Report)가 있음 | 실행자 |
| | `NOT_RUN` | 해당 SHA에서 실행하지 않음 | — |
| | `UNKNOWN` | 실행됐을 수 있으나 개별 결과가 남아 있지 않음 (예: CI Suite만 success) | — |

규칙
- `REVIEW_ACCEPTED`와 `SOURCE_STATED`는 ② `APPROVED`가 아니다. 기존 문서의 `EXISTING_P0_POLICY`는 이 둘 중 하나로 읽는다.
- CI `success`는 "그 SHA에서 `./gradlew check`가 실패 없이 끝났다"는 Suite 수준 근거다. Test별 ⑤ `PASSED`로 쓰려면 Test Report(XML 등) 같은 개별 근거가 필요하다.
- ② `PROPOSED`인 개선의 Test는 ④ `SPECIFIED`를 넘지 않는다.
- D0-B 문서와의 대응은 아래 표를 따른다.

| D0-B 문서 값 | 쓰는 문서 | 5축 대응 |
|---|---|---|
| `AS_IS_OBSERVED` | `CONTRACT-BASELINE` | ① `VERIFIED_AS_IS` |
| `PENDING` / `APPROVED` / `REJECTED` / `DEFERRED` | `DECISION-LOG` | ② 같은 값 |
| `DISCREPANCY` | `CONTRACT-BASELINE`, `DECISION-LOG` | 축 값이 아니다. 불일치 기록이며 판정 없음. 해당 질문은 ② `PENDING` |
| `LOCAL_ONLY` | `TEST-EVIDENCE` | 축 값이 아니다. Evidence의 공유 범위 표시. 그 실행 결과를 ⑤에 쓸 때는 근거가 팀에 공유되지 않았음을 함께 적는다 |
| `UNKNOWN` | 전체 | ① 또는 ⑤의 `UNKNOWN` (어느 축인지 함께 적는다) |
