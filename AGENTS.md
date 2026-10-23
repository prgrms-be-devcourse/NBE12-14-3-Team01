# SWITCH — AI 개발 작업 진입점

이 파일은 Codex와 Claude Code가 공통으로 참조하는 작업 안내 문서다. 규칙 본문은 `docs/ai/AI-RULES.md`에 있다.
이 파일과 AI-RULES가 충돌하면 AI-RULES를 따른다.
상태: **초안 (팀 승인 전)**. 이 저장소는 SWITCH 3차 Kotlin Migration 저장소다 (2차 Java 저장소와 분리, `docs/decisions/DECISION-LOG.md` DL-013).

## 작업 전에 이 순서로 읽는다

1. `docs/README.md` — 문서 위치, Source 우선순위, 상태값
2. `docs/ai/AI-RULES.md` — 변경 제한 규칙 (필수)
3. `docs/STATUS-AXES.md` — 상태 5개 축 (근거 / 정책 승인 / 문서 리뷰 / Test 준비 / Test 실행)
4. 이번 작업의 Task Card (Delivery Board / Issue / 사람의 지시)와 해당 Domain의 Delivery Sheet (`docs/delivery/`)
5. `docs/migration/MIGRATION-MATRIX.md` — 해당 Domain 행 (범위, 필수 Regression Test, 선행조건)
6. `docs/contract/CONTRACT-BASELINE.md` — 해당 Domain과 공통 규약
7. `docs/decisions/DECISION-LOG.md` — 해당 Domain의 `PENDING` / `APPROVED` 항목
8. 관련 Java Source와 Test (`docs/evidence/TEST-INVENTORY.md`의 해당 Domain 구역)
9. 기술 환경을 바꾸는 작업이면 `docs/migration/TECH-STACK-DECISIONS.md`

## 절대 규칙 (요약)

- 승인된 Task 범위 밖의 Production 코드를 바꾸지 않는다.
- Contract / API / ErrorCode / Enum / DB Schema / Transaction / Lock / Isolation을 조용히 바꾸지 않는다.
- Gradle Plugin·Library 추가, Version 변경, `application*.yml` 변경은 승인된 Task에서만 한다.
- 기존 Test 기대값을 임의로 바꾸지 않는다.
- 코드·명세·Test가 충돌하면 어느 쪽이 맞는지 고르지 않는다. 기록하고 질문하거나 HOLD한다.
- `REVIEW_ACCEPTED`(G-03 C-ID)는 팀 승인이 아니다. `PROPOSED` 항목은 구현 지시가 아니다 (`docs/STATUS-AXES.md`).
- 운영 DB / Railway에 접속·쓰기·부하를 주지 않는다. Secret 값을 출력하지 않는다.
- Commit / Push / Merge / PR 생성은 각각 사람의 명시적 승인을 받은 뒤에만 한다.
- Test 결과는 SHA / 환경 / 명령 / 실제 결과로 보고한다. 실행하지 않은 Test를 통과로 쓰지 않는다. CI Suite 성공을 개별 Test 통과로 쓰지 않는다.
