<div align="center">

<img src="frontend/public/switch-logo.png" width="420" alt="SWITCH Logo" />

<br>

### 근무 스케줄과 대체근무 요청을 더 간편하게

소규모 사업장을 위한  
**근무 일정 및 대체근무 관리 서비스**

<br>

**Team Merge · 머지가머지**

<br>

[🌐 SWITCH 바로가기](https://switch-drab.vercel.app/)

</div>

---

## 📌 프로젝트 소개

**SWITCH**는 소규모 카페 및 음식점의 근무 일정과  
대체근무 요청을 한곳에서 관리하기 위한 서비스입니다.

관리자는 정기 근무 패턴을 기반으로 주간 근무표를 생성·공개하고,  
근무자는 자신의 근무 일정을 확인하거나 필요한 경우 대체근무를 요청할 수 있습니다.

기존에 메신저 등을 통해 개별적으로 이루어지던 대체근무 과정을

**요청 → 후보 자동 선별 → 직원 수락 → 관리자 승인 → 공식 근무표 반영**

의 흐름으로 관리하여 근무 일정 변경 과정을 명확하게 관리하는 것을 목표로 합니다.

3차 프로젝트에서는 기존 Java 백엔드와 테스트를 **Kotlin으로 전환**하고,<br>
재가입·소속 이력, 퇴사 미리보기, 공개 근무 변경 및 알림·결과 조회 기능을 보강했습니다.

### 📅 프로젝트 기간

**2026.10.22 ~ 2026.10.29** (3차 프로젝트)

---

## ✨ 주요 기능

<details>
<summary><b>주요 기능 보기</b></summary>

<br>

### 👑 관리자

- 근무지 생성 및 직원 조회
- 초대 코드를 통한 근무지 참여
- 정기 근무 패턴 등록 및 관리
- 주간 근무표 생성·수정·공개
- 공개된 근무표에 시작 전 근무 추가·취소·담당자 재배정
- 직원 퇴사 전 공식 근무·DRAFT·대타 요청의 예상 영향 미리보기
- 근무지 초대 코드 재발급
- 대체근무 요청 최종 승인
- 승인 완료 후 공식 근무표 반영
- 종료·만료된 대체근무 요청 조회 및 관리자 화면 제공

### 👤 근무자

- 참여 중인 근무지 조회
- 종료된 소속 이력 조회 API 및 기존 이력을 보존하는 새 소속으로 재가입
- 개인 근무 일정 조회
- 근무 불가능 시간 등록 및 관리
- 대체근무 요청
- 받은 대체근무 요청 조회 및 수락·거절
- 보낸 대체근무 요청 진행 상태 조회
- 수락한 대체근무의 승인·종료·만료 결과를 최근 7일간 읽기 전용으로 조회
- 보낸 대체근무 요청의 만료 시각과 근무 변경·후보 자격 상실 종료 사유 확인
- 취소되거나 다른 직원에게 넘어간 근무 조회 API

### 🔔 공통

- JWT 기반 회원 인증
- 서버 로그아웃 시 Refresh Token 조건부 무효화 및 쿠키 삭제
- 근무지별 `MANAGER` / `EMPLOYEE` 권한 분리
- 대체근무 상태 기반 흐름 관리
- 근무표 공개 및 대체근무 주요 상태 변경 알림
- 공개 근무 추가·취소·재배정, 후보 자격 상실 및 가입·재가입 알림

> 기능 설명은 2026.10.29에 확인한 `develop` 코드와 병합된 PR 기준입니다.<br>
> API 구현·화면 연결·운영 배포 여부를 구분하며, 배포된 서비스의 최신 반영 여부는 확인하지 않았습니다.

**퇴사 기능 반영 상태**

- 미리보기 조회 API는 소속·근무·대타 상태를 변경하지 않고 예상 영향과 서명된 `previewContext`를 반환합니다 ([#90](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/90)).
- 퇴사 확정 POST는 핵심 로직이 구현된 [#102](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/102) 당시에는 **비활성**이었습니다. 이후 [#112](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/112)가 병합되어 최신 `develop`에는 활성화 구성과 퇴사 확정 알림 연결이 반영되어 있습니다.
- 퇴사 미리보기·확정의 Frontend 화면 연결은 아직 반영되지 않았습니다. 운영 환경의 POST 활성화 여부는 별도로 확인해야 합니다.

</details>

---

## 🛠 Tech Stack

### Backend

![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![JDK](https://img.shields.io/badge/JDK-25-007396?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-6DB33F?style=flat-square&logo=spring&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=flat-square&logo=jsonwebtokens&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white)

### Frontend

![Next.js](https://img.shields.io/badge/Next.js-16.3.5-black?style=flat-square&logo=nextdotjs&logoColor=white)
![React](https://img.shields.io/badge/React-19.2.8-61DAFB?style=flat-square&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?style=flat-square&logo=typescript&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind%20CSS-4-06B6D4?style=flat-square&logo=tailwindcss&logoColor=white)

### Database / DevOps / Deployment

![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=flat-square&logo=mysql&logoColor=white)
![H2](https://img.shields.io/badge/H2-09476B?style=flat-square)
![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)
![Vercel](https://img.shields.io/badge/Vercel-000000?style=flat-square&logo=vercel&logoColor=white)
![Railway](https://img.shields.io/badge/Railway-0B0D0E?style=flat-square&logo=railway&logoColor=white)

### Collaboration

![Git](https://img.shields.io/badge/Git-F05032?style=flat-square&logo=git&logoColor=white)
![GitHub](https://img.shields.io/badge/GitHub-181717?style=flat-square&logo=github&logoColor=white)
![Notion](https://img.shields.io/badge/Notion-000000?style=flat-square&logo=notion&logoColor=white)
![Slack](https://img.shields.io/badge/Slack-4A154B?style=flat-square&logo=slack&logoColor=white)

---

## 💡 기술적 포인트

### 🟣 Kotlin 마이그레이션

기존 Java 백엔드와 테스트를 Kotlin 2.3.21 / JDK 25 환경으로 전환했습니다.<br>
기존 API·응답·상태 전이를 보존하는 마이그레이션과 신규 기능 개선을 별도 PR로 진행했습니다.

DTO에는 `data class`를 활용하고, JPA Entity는 일반 `class`와 JPA 플러그인을 사용합니다.<br>
Nullability와 Validation 사용 위치를 명시하고, 알림 수신자 조회의 공통 처리는 확장 함수로 모았습니다.

### 🔐 근무지 단위 권한 관리

사용자는 여러 근무지에 참여할 수 있으며,  
각 근무지마다 `MANAGER` 또는 `EMPLOYEE` 역할을 가질 수 있습니다.

현재 근무지에서의 역할을 기준으로 접근 가능한 API와 화면을 분리하여  
관리자 전용 기능이 직원에게 노출되거나 호출되지 않도록 처리했습니다.

### 🔄 대체근무 후보 자동 선별 및 상태 관리

대체근무 요청 시 같은 근무지의 직원 중  
요청자 본인과 근무 시간이 충돌하는 직원을 제외하여 요청 가능한 후보를 자동으로 선별합니다.

후보 선별 시 공식 근무 일정, 근무 불가능 시간,  
이미 수락한 대체근무와의 시간 충돌을 함께 확인합니다.

후보 선정 이후 일정이 변경될 가능성을 고려하여  
직원이 요청을 수락하는 시점에 충돌 여부를 다시 검증합니다.

### ✅ 트랜잭션을 통한 데이터 정합성 유지

대체근무 수락 과정에서 후보 및 요청의 최신 상태를 다시 확인하고,  
재검증부터 상태 변경까지 하나의 트랜잭션으로 처리합니다.

관리자 승인 전까지는 기존 공식 근무 담당자를 유지하며,  
승인 완료 시에만 실제 근무 담당자를 변경합니다.

재가입 시에는 종료된 소속을 재활성화하지 않고 새 소속을 생성하며,<br>
생성 컬럼과 Unique 제약으로 같은 사용자의 근무지별 현재 소속 중복을 제한합니다.

공개 근무 취소·재배정은 배정 이력 저장과 진행 중 대타 요청 종료를 같은 트랜잭션으로 처리합니다.<br>
근무 배정·대타 처리 시 잠금 이후 현재 소속을 다시 확인하여 퇴사와 경합하는 변경을 검증합니다.

### 🧪 MySQL 동시성 테스트

Testcontainers MySQL을 사용해 재가입·초대 코드 재발급, 근무 변경·대타 수락 등<br>
여러 트랜잭션이 경합하는 시나리오와 실패 시 롤백 후 DB 상태를 확인하는 테스트를 보강했습니다.

테스트 구현과 실행 결과를 구분하며, 실행 SHA·환경·명령·결과는<br>
[`TEST-EVIDENCE`](docs/evidence/TEST-EVIDENCE.md)에 기록합니다. 운영 DB의 검증 결과로 확대하지 않습니다.

### 🔔 커밋 이후 알림 생성

근무표 공개와 대체근무 주요 상태 변경 시 이벤트를 발행하고,  
`@TransactionalEventListener`를 활용하여 원본 작업이 정상적으로 반영된 이후 알림을 생성합니다.

이를 통해 핵심 비즈니스 로직과 알림 처리를 분리했습니다.

공개 근무 변경 알림은 받는 사람별 역할과 현재 소속을 기준으로 중복 문구를 합치고,<br>
업무 커밋 이후 받는 사람별 별도 트랜잭션으로 저장합니다.

알림은 최선 노력 방식이며, 놓친 결과를 다시 확인할 수 있도록 종료된 대타·빠진 근무 조회 API를 제공합니다.<br>
직원의 수락한 대타 종료 결과는 화면에 연결되어 있으며, 관리자 종료 목록은 화면에 연결되었으며, 빠진 근무 화면은 별도 연결 대상입니다.

### 🔑 JWT 인증 및 토큰 재발급

Access Token을 이용해 API 요청을 인증하며,  
토큰 만료 시 Refresh Token을 통해 Access Token을 재발급합니다.

동시에 여러 API 요청에서 토큰 만료가 발생하더라도  
재발급 요청이 중복되지 않도록 처리했습니다.

또한 로그인·로그아웃 시 변경되는 `sessionVersion`을 기준으로  
재발급 도중 세션이 변경된 경우 이전 세션의 응답이 현재 인증 상태에 반영되지 않도록 처리했습니다.

서버 로그아웃은 요청의 Refresh Token이 저장된 값과 일치할 때만 무효화하여,<br>
이전 세션의 로그아웃이 새 로그인 세션을 지우지 않도록 처리합니다. 발급된 Access Token의 즉시 무효화는 포함하지 않습니다.

---

## 🗄 ERD

<p align="center">
  <img src="docs/images/erd.png" width="900" alt="SWITCH ERD" />
</p>

---

## 🏗 시스템 아키텍처

<p align="center">
  <img src="docs/images/architecture.png" width="1000" alt="SWITCH Architecture" />
</p>

---

## 🔄 CI/CD 및 배포 구조

<p align="center">
  <img src="docs/images/deploymentPipeline.png" width="1000" alt="SWITCH CI/CD Deployment Architecture" />
</p>

---

## 🚀 실행 방법

### 실행 환경

- JDK 25
- Node.js / npm

### Backend

저장소 루트에서 백엔드 디렉터리로 이동합니다.

```bash
cd backend
```

Windows:

```bash
gradlew.bat bootRun
```

macOS / Linux:

```bash
./gradlew bootRun
```

기본 API 주소:

```text
http://localhost:8080/api/v1
```

> 로컬 개발 환경에서는 H2 Database를 사용합니다.

### Frontend

프론트엔드 디렉터리로 이동합니다.

```bash
cd frontend
```

의존성을 설치하고 개발 서버를 실행합니다.

```bash
npm install
npm run dev
```

기본 접속 주소:

```text
http://localhost:3000
```

별도의 백엔드 주소를 사용할 경우  
`frontend/.env.local`에 다음과 같이 설정합니다.

```env
NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
```

---

## 👥 Team Merge

| 이름 | 역할 | 담당 기능 |
| --- | --- | --- |
| **황희리** | 팀장 | 근무지·소속·정기근무 Kotlin 전환 · 재가입·소속 이력 · 퇴사 미리보기·확정 핵심 로직 ([#52](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/52), [#82](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/82), [#90](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/90), [#102](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/102)) |
| **백혜승** | 서기 | 알림 Kotlin 전환·신규 알림 · 공통 API 계약 테스트·테스트 Kotlin 전환 · 퇴사 확정 POST 활성화·알림 연결 ([#15](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/15), [#53](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/53), [#79](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/79), [#100](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/100), [#112](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/112)) |
| **김덕우** | 팀원 | 회원·인증 Kotlin 전환 · 서버·화면 로그아웃 · 대타 정합성 보완·종료 조회 API 및 직원 화면 ([#19](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/19), [#74](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/74), [#76](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/76), [#101](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/101), [#104](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/104), [#110](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/110)) |
| **박세민** | 팀원 | 근무표·근무·근무 불가 시간 Kotlin 전환 · 공개 근무 추가·취소·재배정 및 화면 연동 ([#24](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/24), [#50](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/50), [#87](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/87), [#91](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/91), [#93](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/93), [#108](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/108)) |
| **최세현** | 팀원 | 대체근무 도메인·테스트 Kotlin 전환 · Global 공통 코드 Kotlin 전환 ([#17](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/17), [#23](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/23), [#29](https://github.com/prgrms-be-devcourse/NBE12-14-3-Team01/pull/29)) |

> 담당 내용은 3차 저장소에서 병합된 PR 작성 내역을 기준으로 정리했습니다.

---

## 🤝 Convention

<details>
<summary><b>개발 컨벤션 보기</b></summary>

<br>

### Branch

```text
feat/기능명
fix/기능명
docs/문서명
```

예시:

```text
feat/workplace
feat/schedule
fix/substitute-request
docs/readme
```

### Commit

| Type | 설명 |
| --- | --- |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `test` | 테스트 코드 추가 및 수정 |
| `docs` | 문서 추가 및 수정 |
| `refactor` | 비즈니스 로직 변경 없이 코드 구조 개선 |
| `chore` | 설정, 의존성 등 기타 작업 |

### Code Style

- Kotlin 공식 코드 스타일 (`KOTLIN_OFFICIAL`) 기반
- Kotlin 들여쓰기 Space 4칸
- IntelliJ Project Code Style 공유
- DTO는 `data class`, JPA Entity는 일반 `class` 사용
- Validation Annotation 사용 위치(`@field:` 등) 명시, `!!` 사용은 원칙적으로 금지
- Kotlin 린트 도구(ktlint/detekt)는 현재 빌드·CI에 미적용
- 기능별 Branch 개발 후 Pull Request 및 Code Review 진행

</details>

---

<div align="center">

### 근무가 필요한 순간, SWITCH

**Team Merge · 머지가머지**

</div>
