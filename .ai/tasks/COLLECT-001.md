---
task_id: COLLECT-001
title: ALIO(공공기관 채용정보) 공식 Open API 연동 — 외부 API → JobPosting 저장 최소 수직 slice
phase: done
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-13T21:40:00+09:00
codex_thread_id: 019ffad7-364b-75c0-98dc-e086df4d0aa3
---

## Context

JOB-001로 `JobPosting`의 저장/조회(수동 생성)까지는 만들어졌지만, 실제 외부
채용정보를 가져오는 경로는 아직 하나도 없다(`docs/PROJECT.md` 목표 1 "채용공고
자동 수집"의 첫 단계 미착수). 이 Task는 CareerOps의 **첫 실제 외부 Source**를
연결해 `외부 API 호출 → 소스별 DTO → JobPosting으로 정규화 → 저장`이라는
수집 파이프라인의 최소 흐름을 실제로 검증한다.

사용자가 1순위 후보로 제시한 "JOB-ALIO 계열 공식 Open API"는 아래 "조사 결과"에
정리한 것처럼 실제로 존재하고 접근 가능함을 확인했다(공공데이터포털에 등록된
재정경제부 제공 API, 활용신청 후 사용 가능). 따라서 추측 없이 이 API를 대상으로
설계를 진행한다.

## 정정 이력 — 잘못된 Source 선택 (1차 리뷰 통과 후 발견)

COLLECT-001이 1차 리뷰까지 PASS한 뒤, 실제 사용자가 승인받은 API로 수동
검증을 시도하는 과정에서 **Source 선택 자체가 잘못됐다는 것이 드러났다.**

- 처음에는 공공데이터포털(data.go.kr)의 "재정경제부_공공기관 채용정보
  조회서비스"(`https://apis.data.go.kr/1051000/recruitment/list`)를
  Source로 선택했다. Swagger 스펙 원문(페이지에 임베드된 JSON)을 직접
  확인했다는 점에서 "추측하지 않는다"는 원칙은 지켰지만, **사용자가 실제로
  활용신청·승인받은 API가 이것과 다르다는 것**을 몰랐다 — 문서를 정확히
  읽는 것과 사용자가 실제로 쓸 수 있는 계약인지 확인하는 것은 별개였다.
- 실제 호출 시 `403 SERVICE_KEY_IS_NOT_REGISTERED_ERROR`가 났고, 처음엔
  이것도 원인을 코드/인코딩 문제로 의심하고 curl 직접 비교까지 했지만
  (인코딩 문제가 아님은 맞게 확인했다), **진짜 원인은 애초에 승인받은
  API 자체가 달랐다**는 것이었다 — 사용자가 알려줌.
- 사용자가 실제로 승인받아 Swagger에서 동작을 확인한 것은 ALIO 개방데이터
  사이트(`opendata.alio.go.kr`)가 직접 제공하는 `POST /new/v1/recruit/list.do`
  (아래 "정정된 API 사양" 참고)였다.

**학습 사항**: 외부 API 연동에서는 "공식 문서에 있는 비슷한 이름의 API"가
아니라, **사용자가 실제로 활용신청·승인받아 쓸 수 있는 정확한 API 계약**을
기준으로 검증해야 한다. 같은 기관(재정경제부/ALIO)이 데이터셋을 여러
게이트웨이(공공데이터포털 게이트웨이 vs 자체 개방데이터 사이트)로 중복
노출하는 경우, 문서 조사만으로는 사용자가 어느 쪽에 승인받았는지 알 수
없다 — 이런 경우 초기 설계 단계에서 "실제로 활용신청한 API가 어느
것인지"를 먼저 사용자에게 확인했어야 했다.

## 조사 결과 — 공식 API 확인 (근거 포함, 최초 조사 — 아래 "정정된 API 사양"으로 대체됨)

### 선택한 API

**"재정경제부_공공기관 채용정보 조회서비스"** (공공데이터포털 데이터셋 ID
`15125273`)

- 카탈로그 페이지: https://www.data.go.kr/data/15125273/openapi.do
- 제공기관: 재정경제부(경영관리과) — 재정경제부가 운영하는 **공공기관 경영정보
  공개시스템(ALIO, https://www.alio.go.kr )** 을 기반으로, 전국 공공기관이
  수시공시로 등록하는 채용공시를 실시간 수집해 제공한다. 사용자가 도메인
  명칭으로 언급한 **잡알리오(JOB-ALIO, https://job.alio.go.kr )** 가 사람이
  보는 웹 UI이고, 이 API는 그 데이터를 프로그램으로 조회하는 공식 채널이다.
- API 유형: REST. 데이터 포맷: **JSON + XML** (요청 파라미터 `resultType`으로
  선택, 기본값 `json`).
- 비용: 무료. 인증: **활용신청 필요**(아래 "인증/키 발급 절차" 참고).
- 트래픽: 개발계정 기준 **1,000건/일**(운영계정은 활용사례 등록 시 증설 가능
  — 이번 Task는 개발계정으로 충분).

**스펙 확인 방법(중요)**: 이 페이지의 Swagger 문서는 브라우저에서 JS로
렌더링되는 Swagger UI라 일반적인 페이지 텍스트 추출로는 오퍼레이션/파라미터/
응답 필드를 볼 수 없었다. 대신 페이지 HTML 소스(`curl`로 직접 확인, 2026-08-13
조사)에 Swagger UI가 렌더링에 사용하는 **완전한 Swagger 2.0 스펙 JSON 원문이
그대로 인라인 임베드**되어 있어, 이를 통해 오퍼레이션/파라미터/응답 필드
전체를 원문 그대로 확인했다(추측이나 블로그 예제가 아니라 공식 페이지에
내장된 스펙 원문). 아래 "API 사양(확인된 원문 기준)"이 그 내용이다.

### 검토했으나 채택하지 않은 대안

- **알리오플러스(ALIOPLUS) 자체 Open API**
  (https://www.alioplus.go.kr/openapi/openAPI.do ,
  https://opendata.alio.go.kr/recruit/list ) — 재정경제부/기획재정부가 운영하는
  또 다른 채용정보 공개 채널. 존재는 확인했으나, 페이지가 전부 JS 렌더링 UI +
  이미지 가이드(`guide_001.png`~`guide_008.png`)로만 구성돼 있어 endpoint/
  파라미터/응답 필드를 텍스트로 확인할 수 없었다. **중요한 발견**: 위
  data.go.kr 스펙 원문에 담긴 `swaggerOprtinVOs`(내부 게이트웨이 메타데이터)를
  보면 이 API의 실제 백엔드가 `http://opendata.alio.go.kr/api/v1/recruit`임을
  알 수 있다 — 즉 **data.go.kr API와 알리오플러스 오픈API는 같은 원천 데이터를
  가리키며, data.go.kr는 이 데이터에 대한 표준 인증(서비스키) 게이트웨이
  역할**을 한다. 별도로 알리오플러스 자체 인증키 체계를 또 조사/사용할 필요가
  없다고 판단해 **data.go.kr 경로 하나만 채택**한다.

### 결론(최초 조사 시점): 사용 가능 여부

이 결론은 잘못된 Source에 대한 것이었다 — 아래 "정정된 API 사양"을
실제 계약으로 사용한다. 이 섹션과 다음 "API 사양" 섹션은 무엇을 어떻게
잘못 조사했는지 남기기 위해 보존한다(삭제하지 않음).

### API 사양 (확인된 원문 기준, ⚠️ 정정됨 — 아래 "정정된 API 사양" 참고)

---

## 정정된 API 사양 — 실제 승인 API 기준 (Claude가 실제 키로 직접 호출·검증)

사용자가 실제로 활용신청·승인받고 Swagger에서 정상 동작을 확인한 API다.
아래 내용은 **실제 서비스키로 직접 호출해 받은 진짜 응답을 기준으로** 확인한
것이다(fixture나 문서 추측 아님). Secret 값 자체는 어디에도 기록하지 않는다.

**Host/Endpoint**
- Base: `https://opendata.alio.go.kr/new/v1/recruit`
- 목록조회: `POST /list.do` (전체: `https://opendata.alio.go.kr/new/v1/recruit/list.do`)
- 상세조회: `POST /detail.do` (이번 Task는 미사용 — 아래 "상세 API 필요성 검토" 참고)

**요청 — 실제로 성공을 확인한 정확한 조합** (아래 4가지가 전부 있어야
성공한다 — 하나라도 빠지면 `406 Not Acceptable` 또는 `resultCode`가
문자열 `"6"`(서버 에러)인 실패 응답을 받는다는 것을 직접 재현해 확인함):

1. Query parameter: `numOfRows`, `pageNo`, `resultType=json`, `serviceKey`
   (Spring `RestClient`의 기본 `UriBuilderFactory`가 하는 표준 1회
   percent-encoding으로 정상 인증됨 — 이중인코딩 등 특별 처리 불필요.
   raw(무인코딩)로 보내도 동일하게 동작했지만, encoding 안 하는 쪽을
   선택할 이유가 없으므로 `RestClient` 기본 동작 그대로 둔다).
2. Header `swaggerType: Y` — **없으면 인증에 실패한 것처럼 보이는
   일반 오류(`resultCode:"6"`)가 난다.** 이유는 알 수 없으나(Swagger UI
   전용 우회 경로로 추정) 실제로 없으면 실패하고 있으면 성공하는 것을
   직접 재현해 확인했다 — 이번 구현에서는 그대로 포함한다. **API가
   나중에 이 헤더 없이도 동작하도록 바뀌거나, 반대로 이 헤더 자체가
   deprecated될 수 있는 위험이 있음 — 향후 실패 시 1순위 의심 지점으로
   남긴다.**
3. Header `Content-Type: application/json` + **비어있지 않은 body**(`{}`로
   충분) — 사용자가 Swagger에서 복사한 curl에는 body가 없었지만(Swagger의
   "copy as curl" 기능이 브라우저가 실제로 보낸 body를 누락하는 것으로
   보임), 직접 재현 결과 **`Content-Type` 없이/body 없이는 `406`이 난다.**
   query parameter만으로 인증되는 API라도 이 body는 필요하다(실제 body
   내용은 파싱되지 않는 것으로 보임 — `{}`도, 실제 파라미터를 담은
   body도 결과가 동일했음).
4. Header `Accept: application/json` — Swagger curl에 있었고 그대로 포함.

**응답 — 실제로 확인한 구조 (기존 가정과 다름, 중요)**

```json
{
  "result": [
    { "instNm": "...", "recrutPbancTtl": "...", "recrutPblntSn": 303889, "...": "..." },
    { "...": "..." }
  ],
  "resultCode": 200,
  "totalCount": 112857,
  "resultMsg": "성공했습니다."
}
```

- **`result`는 `item`으로 한 번 더 감싸지 않은, item을 직접 담은 flat
  배열이다.** 기존 DTO(`AlioJobResultItem { item: AlioJobItem }` 래퍼)는
  data.go.kr XML 파생 구조를 가정한 것이었고, 실제 ALIO 자체 API에는
  해당하지 않는다 — `AlioJobListResponse.result`는
  `List<AlioJobItem>`이어야 한다. `AlioJobResultItem` 클래스는 더 이상
  쓰이지 않는다(삭제 대상).
- **`resultCode`는 성공 시 JSON 숫자 `200`, 실패 시(예: 필수 헤더 누락)
  JSON 문자열 `"6"`으로 온다 — 같은 필드인데 성공/실패에 따라 JSON
  타입이 다르다.** `AlioJobListResponse.resultCode` 필드를 `String`으로
  선언하면 Jackson 3 `ObjectMapper` 기본 설정이 숫자 `200`도 `String`
  `"200"`으로 자동 변환(coercion)한다는 것을 로컬에서 실제
  jar(`tools.jackson.core:jackson-databind:3.1.4`)로 직접 테스트해
  확인했다(추측 아님) — 별도 커스텀 역직렬화 불필요. 성공 판정은
  `"200".equals(resultCode())`로 바꾼다(기존 `"0".equals(...)`는
  data.go.kr 관례였고 틀렸다).
- `totalCount`는 정수(예: `112857` — 전체 누적 공고 수로 추정, 페이지당
  건수 아님). 기존 `int totalCount` 타입 그대로 유효.
- item의 필드 이름은 기존 매핑 표(§2, 아래 "매핑 표 최종 확인" 참고)와
  **거의 동일하다** — 같은 ALIO 데이터가 원천이기 때문으로 보인다.
  `recrutPblntSn`은 실제로 JSON 숫자(예: `303889`)로 온다 — 기존
  `AlioJobItem.recrutPblntSn`이 이미 `Long`으로 선언돼 있어 그대로
  유효(변경 불필요).

**매핑 표 최종 확인** (실제 응답 값으로 검증, §2 매핑 표는 필드명 기준
그대로 유효 — 값 형태만 아래로 확정)

| 필드 | 실제 관측 예시 | 확인 사항 |
|---|---|---|
| `instNm` | `"한국전력거래소"` | 항상 존재(샘플 10건 전부) |
| `recrutPbancTtl` | `"2026년도 하반기 전력거래소 공개채용(체험형 청년인턴)"` | 항상 존재 |
| `recrutPblntSn` | `303889` (JSON 숫자) | 항상 존재, 정수 |
| `pbancBgngYmd`/`pbancEndYmd` | `"20260813"`/`"20260828"` | `yyyyMMdd` 문자열, 항상 존재(샘플 10건 전부) — 매퍼의 날짜 파싱 로직 그대로 유효 |
| `srcUrl` | `"https://kpx.saramin.co.kr"` 등 | 항상 존재, 다양한 채용 사이트 URL |
| `recrutSeNm` | `"신입"`/`"경력"`/`"신입+경력"` | 자유 문자열 그대로 저장(정규화 안 함 원칙 유지) |
| `ncsCdNmLst`/`workRgnNmLst` | `"경영.회계.사무,교육.자연.사회과학,전기.전자"` 등 | 쉼표 구분 다건, 255자 이내(샘플 기준) |
| `files`/`steps` | `[]` | 목록조회에서 항상 빈 배열 확인(샘플 10건 전부) — 상세조회 불필요 판단 유지 |
| (일부 필드, 예: `nonatchRsn`) | `null` | 매핑 안 하는 필드이므로 영향 없음. null이 오는 필드가 있다는 것만 기록 |

**상세 API(`/detail.do`) 필요성 검토**: 목록 API만으로 `JobPosting`의
모든 필드(companyName/title/sourceUrl/externalId/applicationStartAt/
applicationEndAt/employmentType/jobCategory/location)를 실제 값으로
채울 수 있음을 확인했다. **상세 API는 이번 Task에서 필요하지 않다** —
Out of Scope 유지.

**인증키 발급 절차(정정)**: 사용자는 `opendata.alio.go.kr` 자체 회원가입
후 그 사이트에서 인증키를 발급받았다(공공데이터포털 data.go.kr 가입과는
별개 사이트). 기존 "인증/키 발급 절차" 섹션(§`https://www.data.go.kr`
기준)은 잘못된 Source에 대한 것이므로 더 이상 따르지 않는다 — 이미 사용자가
유효한 키를 발급받아 `.env`에 설정했으므로 이번 Task 범위에서는 재정리만
해둔다(향후 이 키가 만료/재발급될 경우를 위한 기록): 가입 →
"오픈 API신청" 메뉴에서 채용정보 분야 활용신청 → "나의 활동내역 - 인증키
발급현황"에서 키 확인 → 로컬 `.env`의 `JOB_ALIO_API_KEY`에 설정.

---

- Host: `apis.data.go.kr/1051000/recruitment` (data.go.kr 표준 게이트웨이
  경로 — `15125273`은 data.go.kr 카탈로그 ID일 뿐이고, `1051000`이 실제 요청
  경로에 쓰이는 게이트웨이 서비스 ID다. 스펙 원문의 `host` 필드에서 확인)
- Scheme: `https`(권장), `http`도 명세상 허용

**오퍼레이션 1 — 채용공시 목록조회 (`GET /list`)**

- 전체 URL: `https://apis.data.go.kr/1051000/recruitment/list`
- 요청 파라미터(스펙 원문 그대로):

  | 이름 | 필수 | 설명 |
  |---|---|---|
  | `serviceKey` | 예 | 공공데이터포털에서 받은 인증키 |
  | `pageNo` | 아니오 | 페이지번호, 기본값 1 |
  | `numOfRows` | 아니오 | 한 페이지 결과 수, 기본값 10 |
  | `resultType` | 아니오 | 응답형태, 기본값 `json` |
  | `pbancBgngYmd` / `pbancEndYmd` | 아니오 | 채용공시 시작일 기준 조회 시작/종료일(`YYYY-MM-DD`) |
  | `recrutPbancTtl` | 아니오 | 공시제목(문자열 포함 조건) |
  | `ongoingYn` | 아니오 | 진행여부(Y/N) |
  | `pblntInstCd` | 아니오 | 기관코드 |
  | `instClsf` / `instType` | 아니오 | 기관분류/기관유형(코드정의서 참조) |
  | `recrutSe` | 아니오 | 채용구분(코드정의서 참조) |
  | `hireTypeLst` / `ncsCdLst` / `workRgnLst` / `acbgCondLst` | 아니오 | 각각 코드 목록(쉼표 구분, 코드정의서 참조) |
  | `replmprYn` | 아니오 | 대체인력여부(Y/N) |

- 응답 구조(200 성공 시, JSON): `{ "result": [ { "item": { ... } } ], "resultCode": ..., "resultMsg": ..., "totalCount": ... }`.
  **주의**: `result`는 각 원소가 `item` 키로 한 번 더 감싼 배열이다(XML 파생
  구조라 추정 — 흔한 실수 지점이므로 DTO 설계 시 이 중첩을 그대로 반영해야
  한다).
- `item` 필드 전체 목록(응답 스키마 원문): `acbgCondLst`, `acbgCondNmLst`,
  `aplyQlfcCn`, `decimalDay`, `disqlfcRsn`, `files`(목록 조회에서는 항상 빈
  배열 — 상세조회에서만 채워짐), `hireTypeLst`, `hireTypeNmLst`, `instNm`,
  `ncsCdLst`, `ncsCdNmLst`, `nonatchRsn`, `ongoingYn`, `pbadmsStdInstCd`,
  `pbancBgngYmd`, `pbancEndYmd`, `pblntInstCd`, `prefCn`, `prefCondCn`,
  `recrutNope`, `recrutPbancTtl`, `recrutPblntSn`, `recrutSe`, `recrutSeNm`,
  `replmprYn`, `scrnprcdrMthdExpln`, `srcUrl`, `steps`(목록 조회에서는 항상 빈
  배열), `workRgnLst`, `workRgnNmLst`.

**오퍼레이션 2 — 채용공시 상세조회 (`GET /detail`)**: 파라미터
`serviceKey`(필수), `sn`(필수, `recrutPblntSn` 값), `resultType`. 전형단계
(`steps`)/첨부파일(`files`) 상세를 채워서 반환한다. **이번 Task는 사용하지
않는다**(아래 Out of Scope 참고) — 언급하는 이유는 `recrutPblntSn`이 이
오퍼레이션의 lookup key로 쓰인다는 사실이 그 값의 안정성/고유성 판단 근거이기
때문이다(아래 "externalId 판단" 참고).

**응답코드(`resultCode`) 규칙**: 스펙 원문의 오류 설명이 공공데이터포털
활용지원센터 공통 오류코드 체계와 일치한다 — `0`=정상, `1`=어플리케이션 에러,
`2`=DB 에러, `3`=데이터없음 에러, `5`=서비스연결 에러, `6`=서버 에러,
`7`=게이트웨이 인증 에러, `10`=잘못된 요청 파라미터 에러, `11`=필수 파라미터
없음 에러. **data.go.kr류 API는 이런 오류도 HTTP 200으로 반환하고 body의
`resultCode`로만 알려주는 경우가 흔하다** — HTTP status만 보고 성공 판단하지
말고 `resultCode`를 반드시 확인해야 한다(구현 시 유의사항으로 반영).

### 인증/키 발급 절차 — 사용자가 직접 해야 할 일

1. https://www.data.go.kr 회원가입/로그인.
2. https://www.data.go.kr/data/15125273/openapi.do 페이지에서 **"활용신청"**.
   개발단계는 자동승인(신청 즉시~수 시간 내 사용 가능, 페이지에 명시된 정책
   기준). 운영단계(트래픽 증설)는 활용사례 등록 후 별도 심의승인 — 이번
   Task 범위에서는 불필요.
3. 마이페이지 > 오픈API > 개발계정 상세보기에서 **인증키(서비스키)** 확인.
   data.go.kr는 보통 **인코딩(Encoding) 키**와 **디코딩(Decoding) 키** 두
   형태를 함께 제공한다 — 어느 쪽을 써야 하는지는 실제 호출 클라이언트가
   자체적으로 URL 인코딩을 하는지에 따라 달라진다(Spring `RestClient`/
   `UriComponentsBuilder`는 쿼리 파라미터를 자동 인코딩하므로, 보통
   디코딩 키를 그대로 넘기는 편이 안전하다). `resultCode`가 게이트웨이
   인증 에러(`7`) 또는 `SERVICE_KEY_IS_NOT_REGISTERED_ERROR`류로 보이면
   이 인코딩 이슈를 1순위로 의심할 것 — 실제 값으로 검증하기 전까지는
   확정할 수 없어 "확인이 필요한 리스크"로만 남긴다.
4. 발급받은 키를 로컬 `.env`(git 미추적)에 `JOB_ALIO_API_KEY=<발급받은 키>`로
   설정한다. **`.env.example`에는 이미 `JOB_ALIO_API_KEY=` (키 이름만, 값
   없음)가 존재한다 — 이번 Task에서 새로 추가할 필요 없음, 실제 값도 절대
   커밋하지 않는다.**

**중요**: 이 Task의 자동 구현/테스트는 사용자가 위 절차를 아직 밟지 않았다는
전제로 진행한다. Codex는 임의의 fake key로 "실제 호출이 성공한 척"하는 코드를
만들지 않는다 — 자동 테스트는 전부 fixture 기반이고(아래 테스트 전략), 실제
키를 이용한 검증은 Acceptance Criteria의 `[수동]` 항목으로 분리한다.

### 문제 해결(Troubleshooting) — 실 API 연동 검증 중 확인된 사실

**⚠️ 이 섹션은 잘못된 Source(data.go.kr)를 대상으로 진단한 기록이다.**
아래 진단(인코딩 문제 아님, host/path 도달 확인)은 그 자체로는 맞지만,
결론적으로 사용자가 승인받은 API가 애초에 이것이 아니었다("정정 이력"
섹션 참고). 실제 계약은 위 "정정된 API 사양"을 따른다. 이 섹션은 무엇을
어떻게 진단했는지 남기기 위해 삭제하지 않고 보존한다.

실제 키로 첫 수동 검증 시 애플리케이션이 `502`를 반환했다. 원인을 코드가
아니라 실제 HTTP 레벨에서 직접 좁혔다(Secret 값은 노출하지 않고 진행):

- `curl`로 동일한 요청을 두 가지 방식으로 직접 재현했다 — (a) 키를 있는
  그대로(무인코딩) 전달, (b) 키를 URL percent-encoding해서 전달(Spring
  `RestClient`의 기본 `UriBuilderFactory`가 하는 것과 동일한 처리). **두
  방식 모두 완전히 동일한 응답**(`HTTP 403`,
  `{"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR","returnAuthMsg":"등록되지 않은 서비스키","returnReasonCode":"30"}}}`)을
  반환했다. 이는 **인코딩/이중인코딩 문제가 원인이 아님을 확정**한다 — 키에
  이 상황에서 인코딩 여부에 따라 달라질 특수문자(`+`, `/`, `%`)가 없었기
  때문이다(끝의 `=` 한 글자만 있었고, 두 방식 모두 이 부분 처리와 무관하게
  같은 결과였다).
- 이 오류 형식(`OpenAPI_ServiceResponse`/`cmmMsgHeader`)은 공공데이터포털
  게이트웨이 공통 인증 오류 포맷이다(개별 API의 `resultCode` 포맷과는
  다름) — 즉 **host/path(`apis.data.go.kr/1051000/recruitment/list`)까지는
  정상 도달**했고, 게이트웨이가 서비스키 자체를 "등록되지 않음"으로
  거부한 것이다. **애플리케이션 코드/RestClient 사용 방식의 문제가
  아니다.**
- 가능한 실제 원인(코드 수정 대상 아님, 사용자가 data.go.kr에서 확인 필요):
  1. 이 API(데이터셋 `15125273`, "재정경제부_공공기관 채용정보
     조회서비스")에 대해 정확히 활용신청/승인이 완료됐는지 — 다른 API나
     알리오플러스 쪽에만 신청했을 가능성.
  2. 활용신청 직후라면 승인 반영까지 짧은 지연이 있을 수 있음(보통
     개발계정은 즉시 승인이나, 완전히 즉시는 아닐 수 있음).
  3. 마이페이지에서 서비스키를 복사할 때 일부가 누락/손상됐을 가능성(길이
     비교로만 확인 가능 — 값 자체는 비교하지 않음).
- 애플리케이션의 "502 Bad Gateway"는 우리 쪽 설계상 모든 upstream 실패
  (인증 오류 포함)를 균일하게 502로 매핑한 결과다(Acceptance Criteria
  "외부 API 실패 시 동작"과 일치하는 정상 동작) — 502 자체는 버그 신호가
  아니라 "ALIO 쪽에서 뭔가 실패했다"는 정상적인 알림이었다.

## Scope

1. `POST /api/collect/{source}` — 단순 수동 트리거 REST endpoint 1개
   (아래 "API Trigger" 참고). `source=alio`(대소문자 무관)만 지원, 그 외 값은
   `400 Bad Request`.
2. `AlioJobClient` — ALIO `/list` 오퍼레이션을 호출하는 인터페이스 +
   Spring `RestClient` 기반 운영 구현체. `serviceKey`는 환경변수
   (`JOB_ALIO_API_KEY` → `application.yml` property → 생성자/필드 주입)로만
   받는다.
3. ALIO 응답 전용 DTO(`AlioJobListResponse`/`AlioJobItem` — **`AlioJobResultItem`
   래퍼는 정정 후 삭제**, 위 "정정된 API 사양" 참고) — JPA Entity로 쓰지 않는다.
4. `AlioJobMapper` — `AlioJobItem` → 기존 `JobPostingCreateRequest`(JOB-001에서
   이미 만든 DTO 재사용, 새 DTO 추가하지 않음)로 변환.
5. `AlioCollectorService`(가칭) — fetch → 매핑 → 필수 필드 검증 → 중복 skip →
   기존 `JobPostingService.create()` 호출(저장 + 기존 `careerops_job_creation_total`
   자동 계측 재사용) → 결과 집계(`CollectResult`) → collector 전용 Product
   Metric 계측.
6. 최소 dedup 안전장치(DB 제약 아님, 애플리케이션 레벨 skip) — 아래
   "중복 처리 설계" 참고.
7. Product Metric 4종 신설(`careerops_collector_run_total`,
   `careerops_collector_fetched_total`, `careerops_collector_saved_total`,
   `careerops_collector_failed_total`) — 아래 "Product Metric 정의" 참고.
8. fixture 기반 자동 테스트(외부 API 미접근으로도 전체 통과) — 아래
   "테스트 전략" 참고.

## Out of Scope

- 여러 채용 Source, 웹 크롤링, cross-source dedup, 유사도 기반 중복 제거,
  Matching, 사용자 선호, 카카오톡, Scheduler(정기 수집), AI/LLM 연동,
  Frontend, PKB, 자기소개서, `GET /api/jobs` 목록/검색 API 확장.
- ALIO **상세조회(`/detail`)** 오퍼레이션 — `steps`(전형단계)/`files`(첨부파일)
  상세를 저장하지 않는다. `JobPosting`에 대응 필드가 없고, 이번 Task 목적(흐름
  검증)에도 불필요하다.
- **전체 페이지네이션 순회** — `pageNo`를 반복 증가시키며 전체 데이터를 끝까지
  가져오는 루프는 만들지 않는다. 이번 Task는 단일 페이지(`numOfRows` 쿼리
  파라미터, 기본값은 Codex 재량으로 합리적인 값 — 예: 50)만 조회해 흐름을
  검증한다. 전체 수집이 실제로 필요해지면(운영 요건이 생기면) 별도 Task에서
  pagination 루프 + rate limit 고려를 추가한다.
- `source` + `externalId`에 대한 **DB unique 제약 추가** — 아래 "중복 처리
  설계"에서 애플리케이션 레벨 skip으로 대체한 이유를 설명한다. 본격 dedup
  설계(제약, 충돌 처리, race condition 등)는 별도 Task.
- `recrutSe`/`hireTypeLst` 등 코드값의 정규화/enum화, 코드정의서(PDF) 다운로드
  파싱 — 자유 문자열 필드로 그대로 저장한다(JOB-001과 동일 원칙).
- `Company` Entity 분리 — JOB-001 결정 유지.
- 인증키 발급 자동화 — 사용자가 수동으로 수행(위 "인증/키 발급 절차" 참고).
- WireMock 등 HTTP mocking 프레임워크 도입 — 아래 "테스트 전략"/ADR-0007 참고.

## Acceptance Criteria

`[자동]` = 외부 ALIO API 접근 없이 fixture만으로 검증 가능. `[수동]` = 사람이
실제 발급받은 키로 직접 확인해야 함. 자동 항목은 CORE-001/JOB-001과 동일하게
저장소 루트에서 `docker compose up -d`(PostgreSQL)가 기동 중이어야 한다.

- [ ] `[자동]` **정상 수집**: `AlioJobClient`를 fixture 응답(유효한 항목 N건,
      필수 필드 모두 존재)을 반환하는 테스트용 구현으로 대체한 상태에서
      `POST /api/collect/alio` 호출 시 `200 OK`, 응답 본문에 `fetched=N`,
      `saved=N`, `source="ALIO"`이 포함되고, `JobPostingRepository`에 N건이
      실제로 저장되며 각 레코드의 `source`가 `"ALIO"`다.
- [ ] `[자동]` **매핑 정확성**: 저장된 `JobPosting`의 `companyName`/`title`/
      `sourceUrl`/`externalId`/`applicationStartAt`/`applicationEndAt`이
      fixture의 `instNm`/`recrutPbancTtl`/`srcUrl`/`recrutPblntSn`(문자열화)/
      `pbancBgngYmd`/`pbancEndYmd`(`yyyyMMdd` → `LocalDate`)와 정확히 일치한다.
- [ ] `[자동]` **필수 필드 누락 항목 처리**: fixture 응답에 `instNm` 또는
      `recrutPbancTtl`이 빈 문자열/누락인 item이 섞여 있으면, 그 item은
      저장되지 않고(`failed` 집계, reason=`invalid_item`) 나머지 정상 item은
      정상 저장된다(부분 실패가 전체 실패로 전파되지 않음).
- [ ] `[자동]` **중복 skip**: 동일 `source="ALIO"` + `externalId`(=
      `recrutPblntSn`)를 가진 `JobPosting`이 이미 DB에 존재하는 상태에서
      같은 fixture 응답으로 `POST /api/collect/alio`를 다시 호출하면, 해당
      항목은 재저장되지 않고 `JobPostingRepository.count()`가 증가하지
      않는다(같은 응답을 반복 호출해도 DB에 무한히 쌓이지 않음을 검증).
- [ ] `[자동]` **외부 API 실패 시 동작**: `AlioJobClient`가 예외를 던지도록
      설정한 상태에서 `POST /api/collect/alio` 호출 시 `502 Bad Gateway`를
      반환하고, 이 호출로 `JobPosting`이 하나도 저장되지 않으며,
      `careerops_collector_run_total`(태그 `source=alio,result=failed`)이
      `MeterRegistry` 조회 기준 1 이상 증가한다.
- [ ] `[자동]` **지원하지 않는 source**: `POST /api/collect/unknown` 호출 시
      `400 Bad Request`를 반환하고 어떤 `JobPosting`도 저장되지 않는다.
- [ ] `[자동]` **DTO parsing 단위 테스트**: 고정 JSON 문자열(fixture)을
      Jackson으로 `AlioJobListResponse`로 역직렬화하면 `result` 배열의 중첩
      `item` 구조, `totalCount`, 각 필드 값이 원문과 일치한다.
- [ ] `[자동]` **Mapper 단위 테스트**: `AlioJobItem` → `JobPostingCreateRequest`
      매핑에서 날짜 파싱(`yyyyMMdd`), `externalId` 문자열 변환, null/빈 값
      필드(예: `workRgnNmLst` 없음)가 `JobPostingCreateRequest`의 해당
      nullable 필드에 `null`로 정상 반영되는지 검증.
- [ ] `[자동]` **Product Metric 계측 단위 검증**: 위 시나리오들 실행 후
      `MeterRegistry`에서 `careerops.collector.fetched`(태그 `source=alio`),
      `careerops.collector.saved`(태그 `source=alio`),
      `careerops.collector.failed`(태그 `source=alio,reason=invalid_item`),
      `careerops.collector.run`(태그 `source=alio,result=success`) 카운터
      값이 기대한 만큼 증가했음을 확인한다.
- [ ] `[자동]` **외부 API 미접근으로도 전체 테스트 통과**: `cd backend &&
      ./gradlew test`가 실제 `apis.data.go.kr` 네트워크 접근 없이(운영
      구현체 `RestClientAlioJobClient`는 어떤 자동 테스트에서도 인스턴스화만
      되고 실제로 호출되지 않음) 실패 0건으로 통과한다. 기존 JOB-001/CORE-001
      테스트도 회귀 없이 통과한다.
- [ ] `[자동]` **Git tracked file에 secret 없음**: 실제 `JOB_ALIO_API_KEY` 값,
      fixture 외의 실제 API 응답 원본이 어떤 커밋 파일에도 없다.
- [ ] `[수동]` **실제 발급 키로 1회 이상 실 호출**: 사용자가 위 "인증/키 발급
      절차"를 수행해 실제 서비스키를 `JOB_ALIO_API_KEY`로 설정한 뒤 앱을
      기동하고 `POST /api/collect/alio`를 직접 호출해, 실제 ALIO 응답이
      fetched/saved로 정상 반영되는지, 저장된 `companyName`/`title`/
      `sourceUrl` 등이 사람이 보기에 실제 채용공고로서 합리적인지 확인한다.
- [ ] `[수동]` **반복 호출 시 미중복 확인(실 데이터)**: 같은 호출을 다시
      실행해 PostgreSQL에서 `SELECT count(*) FROM job_postings WHERE
      source='ALIO'`가 그대로거나(새 공고가 없다면) 새로 올라온 공고 수만큼만
      증가하는지(기존 공고가 중복 저장되지 않는지) 확인한다.
- [ ] `[수동]` **Prometheus 노출 확인**: `curl -s
      http://localhost:8080/actuator/prometheus`에서
      `careerops_collector_run_total`, `careerops_collector_fetched_total`,
      `careerops_collector_saved_total`가 `source="alio"` 태그로 노출되는지
      확인한다.

## Technical Notes

### 1. 패키지/클래스 구조

```
backend/src/main/java/com/careerops/backend/collector/
├── CollectController.java          # @RestController, POST /api/collect/{source}
├── CollectResult.java              # record(source, fetched, saved, skipped, failed, result)
└── alio/
    ├── AlioCollectorService.java   # 오케스트레이션 1개 클래스(인터페이스 분리 안 함 — job 패키지와 동일 원칙)
    ├── AlioJobClient.java          # interface: AlioJobListResponse fetchList(int pageNo, int numOfRows)
    ├── RestClientAlioJobClient.java# 운영 구현체 — Spring RestClient 사용
    ├── AlioApiException.java       # unchecked, Reason(FETCH_ERROR, PARSE_ERROR) 포함
    ├── AlioJobListResponse.java    # record: List<AlioJobItem> result(flat, item 래퍼 없음 — 정정됨), String resultCode, String resultMsg, int totalCount
    ├── AlioJobItem.java            # record: 실제 매핑에 쓰는 필드만 선언 + @JsonIgnoreProperties(ignoreUnknown = true)
    └── AlioJobMapper.java          # static JobPostingCreateRequest from(AlioJobItem)

backend/src/test/resources/fixtures/alio/
├── alio-list-response-valid.json           # 정상 항목 N건
├── alio-list-response-with-invalid-item.json  # instNm/recrutPbancTtl 누락 항목 포함
└── (필요 시 추가 fixture)

backend/src/test/java/com/careerops/backend/collector/
├── AlioJobListResponseParsingTest.java   # DTO parsing 단위 테스트
├── AlioJobMapperTest.java                # 매핑 단위 테스트
├── AlioCollectorServiceTest.java         # fixture AlioJobClient로 fetch→저장→dedup→metric 전체 흐름
└── CollectControllerTest.java            # MockMvc, 지원하지 않는 source/성공/실패(502) 케이스
```

`CollectController`는 `AlioCollectorService`에 직접 의존한다. `source` 값이
`"alio"`(대소문자 무관)가 아니면 `ResponseStatusException(BAD_REQUEST)`로
즉시 반환한다 — 소스가 2개 이상이 될 때 공통 `Collector` 인터페이스로 추출을
재검토한다(지금 하나뿐인 시점에 전략 패턴/레지스트리를 미리 만들지 않는다,
JOB-001과 동일한 "과도한 추상화 회피" 원칙).

### 2. 매핑 표 (ALIO 응답 → `JobPosting`, `JobPostingCreateRequest` 경유)

| ALIO 필드(`AlioJobItem`) | 설명 | `JobPosting` 필드 | 비고 |
|---|---|---|---|
| `instNm` | 기관명 | `companyName` | **필수 매핑**. 비어있으면 해당 item skip(`invalid_item`) |
| `recrutPbancTtl` | 채용공고제목 | `title` | **필수 매핑**. 비어있으면 해당 item skip(`invalid_item`) |
| (고정값 `"ALIO"`) | — | `source` | API 응답이 아니라 매퍼가 고정 상수로 채운다 |
| `srcUrl` | 출처URL | `sourceUrl` | 있으면 그대로. `JobPostingCreateRequest`의 `@URL` 검증을 통과하지 못하는 형태면 skip 대신 `null`로 저장(URL 형식만으로 전체 item을 버리지 않음 — Codex 재량이나 이 방향 권장, 이유를 구현 시 남길 것) |
| `recrutPblntSn` | 채용공시일련번호(정수) | `externalId` | `String.valueOf(...)`로 변환. 안정성/고유성 판단 근거는 아래 "externalId 판단" 참고 |
| `pbancBgngYmd` | 공고시작일자(`yyyyMMdd`) | `applicationStartAt` | 파싱 실패/누락 시 해당 필드만 `null` — item 전체를 skip하지 않는다 |
| `pbancEndYmd` | 공고종료일자(`yyyyMMdd`) | `applicationEndAt` | 위와 동일 |
| `recrutSeNm` | 채용구분명 | `employmentType` | 자유 문자열 그대로 저장. 정확한 코드값 목록은 공공데이터포털 첨부 "코드정의서" PDF에 있으나 이번 조사에서 다운로드/파싱하지 않았다(자유 문자열 필드라 필수 아님) |
| `ncsCdNmLst` | NCS코드명목록 | `jobCategory` | 자유 문자열 그대로 저장(쉼표구분 다건 가능, 분해 안 함) |
| `workRgnNmLst` | 근무지역명목록 | `location` | 위와 동일 |
| `hireTypeLst`/`hireTypeNmLst` | 고용유형(정규직/계약직 등) | **매핑 안 함** | `employmentType`(채용구분)과는 다른 개념(고용형태). `JobPosting`에 대응 필드 없음 — 스키마 확장하지 않는다 |
| `acbgCondLst`/`acbgCondNmLst` | 학력조건 | **매핑 안 함** | 대응 필드 없음 |
| `aplyQlfcCn` | 신청자격내용 | **매핑 안 함** | 상동 |
| `prefCn`/`prefCondCn` | 우대내용/조건 | **매핑 안 함** | 상동 |
| `disqlfcRsn` | 결격사유 | **매핑 안 함** | 상동 |
| `recrutNope` | 채용인원 | **매핑 안 함** | 상동(향후 필요해지면 별도 Task) |
| `ongoingYn` | 진행여부(Y/N) | **매핑 안 함** | 상동 |
| `decimalDay` | 마감 D-day | **매핑 안 함** | 파생값(오늘 날짜 기준 계산 가능), 저장 불필요 |
| `pblntInstCd`/`pbadmsStdInstCd` | 기관코드류 | **매핑 안 함** | `Company` Entity 미분리 상태(JOB-001 결정)라 불필요 |
| `files`/`steps` | 첨부파일/전형단계 | **매핑 안 함** | 목록조회에서는 항상 빈 배열([]) — 상세조회(`/detail`) 필요, 이번 Task는 미사용(Out of Scope) |
| `replmprYn`/`scrnprcdrMthdExpln`/`nonatchRsn` | 기타 | **매핑 안 함** | 대응 개념 없음 |

**필드 길이 제약 관련 위험(구현 시 유의)**: `JobPostingCreateRequest`(JOB-001)는
`employmentType`/`jobCategory`/`location`/`externalId`/`companyName`/`title`에
`@Size(max = 255)`, `sourceUrl`에 `@Size(max = 2048)` + `@URL` 검증이 이미
걸려 있다. ALIO의 `ncsCdNmLst`/`workRgnNmLst`는 여러 값이 쉼표로 이어붙은
문자열일 수 있어 255자를 넘을 가능성이 있다 — 이 경우 공유 `Validator`로
`JobPostingCreateRequest` 전체를 검증하면 그 필드 하나 때문에 item 전체가
`invalid_item`으로 튕길 수 있다. Codex는 이 트레이드오프를 인지하고(예: 너무
길면 자르지 않고 그대로 실패로 집계할지, 아니면 매퍼에서 안전하게 자를지)
구현 시 판단하고 Technical Notes/PR 설명에 어떤 선택을 했는지 남길 것 —
이번 Task 명세에서 미리 정하지 않는다(실제 값 분포를 모르는 상태에서
자르는 규칙을 추측하면 데이터 손실 방식을 임의로 정하는 것이 되기 때문).

**externalId 판단**: `recrutPblntSn`은 API 스펙상 상세조회(`/detail`)
오퍼레이션의 lookup key(`sn` 파라미터)로 명시적으로 쓰인다 — "이 값으로 특정
공고 하나를 정확히 다시 조회할 수 있다"는 것이 공식 스펙에 기능적으로
증명되어 있으므로, 안정적/고유한 식별자로 판단할 근거가 충분하다. 다만
"항상 unique하다"는 문장이 스펙에 명시적으로 쓰여 있지는 않으므로, **DB
unique 제약은 걸지 않고** 애플리케이션 레벨 skip으로만 처리한다(아래 "중복
처리 설계").

### 3. 중복 처리 설계 (최소 안전장치, 본격 dedup 아님)

- `JobPostingRepository`에 `boolean existsBySourceAndExternalId(String source,
  String externalId)` 메서드를 추가한다(Spring Data JPA 쿼리 메서드,
  구현 코드 불필요).
- `AlioCollectorService`는 각 item을 저장하기 전에 이 메서드로 존재 여부를
  확인하고, 이미 존재하면 저장을 건너뛴다(skip). 저장 자체는 여전히 기존
  `JobPostingService.create()`를 그대로 호출한다(중복 검사만 collector가
  선행).
- **DB unique 제약을 추가하지 않는 이유**: `recrutPblntSn`의 안정성은 스펙
  근거로 합리적으로 신뢰할 수 있지만, "여러 소스에 걸친 unique 제약을 어떻게
  설계할지"(충돌 시 동작, race condition, 향후 다른 소스와의 스키마 정합성)는
  본격적인 dedup 설계 Task에서 한 번에 다루는 것이 낫다고 판단했다(JOB-001의
  기존 방향과 일관). 지금 필요한 건 "같은 응답을 반복 호출해도 DB가 무한히
  쌓이지 않는" 최소 안전장치뿐이고, 애플리케이션 레벨 존재 확인으로 충분히
  해결된다.
- **skip 건수를 별도 Product Metric으로 노출하지 않는다** — 사용자가 제시한
  candidate metric 목록(run/fetched/saved/failed)에 skip이 없고, 지표를
  늘리는 것 자체가 목표가 아니라는 `docs/METRICS.md` 원칙에 따라, skip
  건수는 `CollectResult` 응답 body와 로그로만 노출한다(`fetched - saved -
  failed`로 근사 계산 가능). 운영상 실제로 필요해지면 향후 dedup Task에서
  정식 metric으로 승격한다.

### 4. API Trigger 설계

`POST /api/collect/{source}` (선택). 이유: Scheduler 없이 가장 단순하게
수동으로 "지금 한 번 수집하라"를 표현할 수 있는 형태이고, `{source}` 경로
변수가 향후 소스가 늘어날 때(둘째 Source부터 실제로) 같은 형태의 endpoint를
재사용할 수 있게 해준다(단, 지금은 구현 내부에 `if
!"alio".equalsIgnoreCase(source)` 분기 하나뿐 — 레지스트리/전략 패턴은
아직 만들지 않는다). Body 없음. 쿼리 파라미터 `numOfRows`(선택, 기본값은
Codex 재량으로 합리적인 값 — 예: 50)로 이번 호출에서 가져올 최대 건수를
조절한다. 응답은 `CollectResult`(JSON) — 성공 시 `200`, 미지원 source
시 `400`, 외부 API 호출 실패 시 `502`.

### 4.5 구현 중 발견된 Boot 4.1 블로커 — Jackson 3 패키지 변경

**Codex가 발견, Claude가 Maven Central jar 검사로 검증 후 승인.**
`AlioJobListResponseParsingTest`에서 `import
com.fasterxml.jackson.databind.ObjectMapper;`로 직접 JSON 역직렬화를
시도하다가 `package com.fasterxml.jackson.databind does not exist` 컴파일
에러 발생. 확인 결과 Spring Boot 4.1은 Jackson 3을 기본 JSON 엔진으로
쓰고, `jackson-core`/`jackson-databind`가 새 groupId `tools.jackson.core`
아래 **`tools.jackson.core`/`tools.jackson.databind`** 패키지로 이동했다
(jar 내용 직접 검사로 확인: `tools/jackson/databind/ObjectMapper.class`).
**단 `jackson-annotations`는 그대로 `com.fasterxml.jackson.annotation`
패키지를 유지**하므로(jar 검사로 확인: `com/fasterxml/jackson/annotation/JsonIgnoreProperties.class`),
`AlioJobItem`의 `@JsonIgnoreProperties`/`@JsonProperty` 같은 애노테이션
import는 기존 그대로 두고, `ObjectMapper` 등 core/databind 타입을 직접
쓰는 곳만 `tools.jackson.databind.*`로 바꾼다. Jackson 3의 `readValue()`는
checked exception이 아니라 unchecked(`JacksonException` 계열)를 던지므로
불필요한 `throws` 선언도 함께 정리한다. 이 발견은 `docs/ARCHITECTURE.md`
"Spring Boot 4.1 알려진 모듈 재구성 이슈"에도 공통 참고용으로 반영했다.

### 5. 테스트 전략 — fixture 기반, 실제 API 호출과 완전 분리

- `AlioJobClient`를 인터페이스로 둔 이유는 **정확히 이 요구사항 하나**다 —
  "외부 API가 죽어 있어도(또는 아직 키가 없어도) 자동 테스트 전체가
  실패하지 않아야 한다." 이 인터페이스 분리는 JOB-001이 지켰던 "인터페이스는
  실제 필요(다중 구현체/테스트 mocking)가 있을 때만" 기준을 충족한다 —
  여기서는 "테스트에서 실제로 다른 구현체(fixture 반환)가 필요"하다는 구체적
  근거가 있다.
- **WireMock 등 HTTP mocking 프레임워크는 도입하지 않는다.** 인터페이스
  자체가 이미 HTTP 계층을 완전히 대체 가능하게 만들어주므로, HTTP 서버를
  띄워 응답을 흉내 낼 필요가 없다(ADR-0007 참고).
- 테스트용 `AlioJobClient` 구현은 **손으로 작성한 stub 클래스**(예:
  `FixtureAlioJobClient`, `@TestConfiguration` bean으로 등록해 필요한
  테스트에서 주입)를 기본으로 권장한다 — 프로젝트 전체가 Mockito를 적극
  도입한 이력이 없고, `spring-boot-starter-test`에 이미 포함돼 있어 새
  dependency는 아니지만, 손으로 작성한 stub이 더 명시적이고 Boot 4.1의
  테스트 애노테이션 재배치 리스크(아래 참고)를 피할 수 있다. Mockito
  `@MockBean`류를 쓰고 싶다면 Codex 재량으로 선택 가능하되, **Boot 4.1에서
  `@MockBean`의 패키지 위치가 3.x와 달라졌을 가능성**이 있다(JOB-001에서
  `@DataJpaTest`/`@AutoConfigureTestDatabase`/`@AutoConfigureMockMvc`가
  전부 재배치됐던 전례). 컴파일 에러가 나면 추측으로 다른 걸 시도하지 말고
  blocker로 보고할 것.
- fixture JSON 파일(`alio-list-response-valid.json`,
  `alio-list-response-with-invalid-item.json`)은 **실제 API 응답을 복사한
  것이 아니라, 확인된 응답 스키마를 기준으로 합성한 값**이어야 한다(아직
  실 데이터를 받은 적이 없으므로 — 실제 API 응답을 흉내 낸 것이지 원본
  개인정보/실제 공고 데이터가 아니다).
- `RestClientAlioJobClient`(운영 구현체)는 어떤 자동 테스트에서도 실제로
  호출되지 않는다 — 인스턴스화 자체를 테스트하고 싶다면 URL 빌드 로직
  정도만 순수 단위 테스트로 검증하고, 실제 소켓 연결은 하지 않는다.

### 6. Dependency 및 근거

**구현 중 발견해 추가 승인된 production dependency 1건**:
`org.springframework.boot:spring-boot-starter-restclient`. 최초 계획은
"`RestClient`는 `spring-boot-starter-web`에 이미 있다"였으나(3.x 기준
사실), Boot 4.1은 `RestClientAutoConfiguration`(`RestClient.Builder` bean
제공)을 `spring-boot-autoconfigure`에서 떼어내 별도 모듈
`spring-boot-restclient`로 분리했다 — `RestClientAlioJobClient` 기동 시
`No qualifying bean of type 'RestClient$Builder' available` 런타임 에러로
발견(Maven Central jar 직접 검사로 `spring-boot-autoconfigure-4.1.0.jar`에
RestClient 관련 클래스가 전혀 없고, `spring-boot-restclient-4.1.0.jar`에
`RestClientAutoConfiguration`이 있음을 확인). 이를 가져오는 공식 경로가
`spring-boot-starter-restclient`(`spring-boot-restclient` +
`spring-boot-starter-jackson`을 포함)다. 버전은 명시하지 않는다(BOM 관리).
- JSON parsing: Jackson(Boot 4.1 기본, 위 §4.5 참고) 사용, 별도 파싱
  라이브러리 불필요.
- `WebClient`(reactive)는 이 프로젝트가 WebFlux를 쓰지 않으므로 불필요한
  추가 의존성이 될 것이라 기각(변경 없음).

**신규 test dependency 없음.** WireMock을 검토했으나 위 "테스트 전략"의
이유로 기각(ADR-0007).

### 7. AI 개발 Workflow 프로세스 반영

- Codex는 `.ai/metrics/metrics.jsonl`을 직접 수정하지 않는다 — plan/implement/
  review/verify phase 기록은 오케스트레이터(Claude, `codex-implement` Skill
  절차)가 담당한다(JOB-001과 동일).
- Codex가 새 framework/version 관련 blocker를 만나면(JOB-001에서 Spring Boot
  4.1 모듈 분리 이슈가 3차례 있었던 것처럼, 이번 Task에서는 특히
  `@MockBean` 재배치 가능성과 `RestClient`/Jackson 관련 Boot 4.1 API 변경
  가능성에 유의) 추측해서 다른 클래스/방법으로 우회하지 말고, 정확히 무엇이
  실패했는지와 함께 blocker로 보고한다. Claude가 공식 문서/Maven Central jar
  확인 후 승인·재개 지시한다.
- 실제 ALIO API 키를 아직 사용자가 발급하지 않은 상태로 구현이 시작된다 —
  Codex는 fake key로 "실제 호출 성공"을 가장하는 코드/테스트를 작성하지
  않는다. 실제 키 검증은 `[수동]` Acceptance Criteria로 분리되어 있다.

## Test Plan

- `[자동]` `AlioJobListResponseParsingTest` — 고정 JSON(fixture)을
  `ObjectMapper`로 `AlioJobListResponse`에 역직렬화, 중첩 `item` 구조/
  `totalCount`/필드 값 검증.
- `[자동]` `AlioJobMapperTest` — `AlioJobItem` → `JobPostingCreateRequest`
  매핑 단위 테스트: 정상 케이스, 날짜 파싱, null/누락 필드 처리.
- `[자동]` `AlioCollectorServiceTest` — `AlioJobClient`를 fixture stub으로
  주입, `@DataJpaTest` 대신 실제 서비스 계층 + 로컬 PostgreSQL(JOB-001과
  동일 사전조건)을 사용해 fetch→매핑→검증→저장→dedup skip→metric 계측
  전체 흐름을 검증. 외부 API 실패 시나리오(stub이 `AlioApiException` 던짐)
  포함.
- `[자동]` `CollectControllerTest` — `@SpringBootTest` + `@AutoConfigureMockMvc`,
  `AlioCollectorService`(또는 그 아래 `AlioJobClient`)를 테스트용 구현으로
  대체해 `POST /api/collect/alio` 성공/실패(502)/미지원 source(400) 케이스를
  MockMvc로 검증.
- `[자동]` `cd backend && ./gradlew test` — 위 신규 테스트 + 기존 JOB-001/
  CORE-001 테스트 전체 통과. 사전조건: 저장소 루트에서 `docker compose up -d`.
- `[수동]` 사용자가 실제 `JOB_ALIO_API_KEY` 발급 후 `./gradlew bootRun` →
  `curl -X POST http://localhost:8080/api/collect/alio` 실행, 응답과 DB
  저장 결과, `/actuator/prometheus`의 `careerops_collector_*` 라인을 직접
  확인. 확인 후 프로세스/컨테이너 정리.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | COLLECT-001 명세 기반 최초 구현 지시(collector 패키지, ALIO DTO/Mapper/Service/Controller, fixture 테스트, 신규 dependency 없음 전제) | `com.careerops.backend.collector` 구현했으나 `./gradlew testClasses`에서 `com.fasterxml.jackson.databind` 패키지를 찾을 수 없다는 컴파일 에러(Jackson 3 패키지 이동) — 추측 없이 정확히 보고하고 중단 |
| 2 | Claude가 Maven Central jar 검사로 Jackson 3 패키지 위치(`tools.jackson.databind`) 확인 후 승인·재개 지시(단 `com.fasterxml.jackson.annotation`은 그대로 유지) | import 수정 후 컴파일은 성공했으나, 이번엔 `RestClientAlioJobClient` 기동 시 `No qualifying bean of type 'RestClient$Builder'` 런타임 에러 — 재차 블로커로 중단(16개 중 5개만 통과) |
| 3 | Claude가 Boot 4.1의 RestClient 자동구성 분리(`spring-boot-starter-restclient` 필요)를 jar 검사로 검증 후 승인·재개 지시 | 의존성 추가 후 빌드/테스트 전체 성공(16/16). 컨테이너/프로세스 정상 정리 확인 → reviewer 1차 리뷰 PASS (`.ai/reviews/COLLECT-001-review-1.md`) |
| 4 | 1차 리뷰 PASS 이후, 사용자가 실제 승인받은 API가 1~3라운드가 가정한 data.go.kr이 아니라 ALIO 자체 API(`opendata.alio.go.kr`)임이 확인됨. Claude가 실제 승인키로 직접 curl 검증해 정확한 요청/응답 계약("정정된 API 사양")을 확정한 뒤, host/path/헤더/body/응답 envelope(item wrapper 제거, resultCode 타입) 수정을 정밀 지시 | 한 번에 성공(추가 블로커 없음). 빌드/테스트 전체 성공(16/16, fixture 전용, 실 API 미호출). 이후 Claude가 실제 키로 End-to-End 검증: 최초 실행 fetched=50/saved=50, 재실행 fetched=50/saved=0/skipped=50(중복 방지 확인), Collector/Job metric 전부 정상 노출 확인 → reviewer 2차 리뷰 PASS (`.ai/reviews/COLLECT-001-review-2.md`) |
