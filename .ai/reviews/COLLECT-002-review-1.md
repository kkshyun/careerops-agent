---
task_id: COLLECT-002
review_round: 1
reviewer: claude
reviewed_at: 2026-08-15T21:00:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] **마이그레이션/기동** — 충족. `backend/src/main/resources/db/migration/V2__add_job_posting_fields.sql:1-5`가
      `career_level`, `education_requirement`, `status`, `institution_code`
      4개 컬럼을 `job_postings`에 추가. `ddl-auto=validate` 하에서 전체
      테스트 스위트(Spring context 기동 포함)가 통과했으므로 마이그레이션이
      엔티티와 정합함을 확인.
- [x] **Mapper 정정 검증** — 충족. `AlioJobMapper.java:18-33`에서
      `employmentType`←`item.hireTypeNmLst()`, `careerLevel`←
      `item.recrutSeNm()`, `educationRequirement`←`item.acbgCondNmLst()`,
      `institutionCode`←`item.pblntInstCd()` 순서로 정확히 매핑됨(생성자
      인자 순서가 `JobPostingCreateRequest`의 필드 순서와 일치함을
      `JobPostingCreateRequest.java:9-24`와 대조 확인). `AlioJobMapperTest
      .java:15-33` (`mapsAlioFieldsAndParsesDates`)와
      `mapsMissingOptionalAndInvalidDateFieldsToNull`이 정상/null 케이스
      모두 검증.
- [x] **status 매핑 검증** — 충족. `AlioJobMapper.java:36-42`
      (`mapStatus`)가 `"Y"`→`"OPEN"`, `"N"`→`"CLOSED"`, 그 외(null 포함)→
      `null`로 정확히 변환. `AlioJobMapperTest.java:57-66`
      (`mapsClosedAndUnknownStatuses`)가 `"N"`→`CLOSED`,
      `"UNKNOWN"`→`null` 케이스를 커버하고, `mapsAlioFieldsAndParsesDates`가
      `"Y"`→`OPEN`, `mapsMissingOptionalAndInvalidDateFieldsToNull`이
      `ongoingYn` 누락(생성자에 `null` 전달) 시 `null`을 검증.
- [x] **신규 저장 시 필드 반영** — 충족.
      `AlioCollectorServiceTest.java:40-56`에서 fixture 저장 후
      `employmentType`/`careerLevel`/`educationRequirement`/`status`/
      `institutionCode` 5개(고용형태 포함) 값을 fixture와 대조.
- [x] **상태 갱신** — 충족. `AlioCollectorService.java:79-92`가
      `findFirstBySourceAndExternalId`로 조회 후 `Objects.equals(existing
      .getStatus(), request.status())`가 다르면
      `jobPostingService.updateStatus(...)` 호출 + `updated++`, 같으면
      `skipped++`. `AlioCollectorServiceTest.java:72-88`
      (`updatesOnlyStatusWhenExistingPostingStatusChanges`)이 두 번째
      fixture(`alio-list-response-closed.json`)에서 동일 `externalId`
      (1001)로 `companyName`/`title`/`srcUrl`/`ncsCdNmLst` 등을 일부러
      다른 값으로 채워, 갱신 후 `status`만 `"CLOSED"`로 바뀌고
      `companyName`/`title`은 원래 값 그대로임을 검증(테스트 설계가 실제로
      "다른 필드는 안 건드림"을 증명하도록 잘 짜여 있음). `repository
      .count()` 불변, `CollectResult.updated == 1`도 함께 확인.
- [x] **상태 불변 시 skip 유지** — 충족. 기존 dedup 테스트
      (`AlioCollectorServiceTest.java` 두 번째 `collect()` 재수집 시나리오,
      L64-76 부근)가 동일 fixture 재수집 시 `updated=0`,
      `skipped`만 증가함을 `CollectResult("ALIO", 3, 0, 1, 0, 2, "success")`로
      검증.
- [x] **API 응답 노출** — 충족. `JobPostingResponse.java:8-46`에 4개
      신규 필드 추가 및 `from()` 갱신. `JobPostingControllerTest.java:45-51,
      89-92`에서 `GET /api/jobs/{id}` 응답에 4개 필드가 노출됨을 확인.
- [x] **회귀 없음** — 충족. `cd backend && ./gradlew test --rerun` 직접
      재실행 결과 `BUILD SUCCESSFUL`, JUnit XML 합산 **35 tests / 0
      failures / 0 errors**(Claude가 보고한 결과와 일치, 재확인 완료).
- [x] **Git tracked file에 secret 없음** — 충족. `git diff -- backend/`에
      `api[_-]?key|secret|password|token` 패턴 grep 결과 없음. `.env`는
      tracked 파일 아님. 신규 production/test dependency 추가 없음
      (`build.gradle` diff 없음).
- [ ] **실제 키로 재검증** `[수동]` — 미검증(이번 리뷰 범위 밖, Task
      명세상 수동 항목). 필요 시 별도로 `JOB_ALIO_API_KEY` 설정 후
      1회 호출 확인 권장.

## 추가 확인 사항 (Task 지시 8개 항목)

1. `employmentType`↔`careerLevel` 매핑 정정 — 확인됨 (위 참고).
2. `ongoingYn`→`status` 변환 — 확인됨 (위 참고).
3. 재수집 시 상태 갱신 로직(dedup→비교/갱신) — 확인됨. `count()` 불변,
   companyName/title 등 미변경 모두 fixture로 실증.
4. `CollectResult.updated` 필드 — `CollectResult.java:8` 추가, 관련 테스트
   (`CollectControllerTest.java:44`, `AlioCollectorServiceTest.java` 다수)
   반영됨.
5. `JobPosting`/`JobPostingCreateRequest`/`JobPostingResponse`/
   `V2__add_job_posting_fields.sql` 4개 필드 일관성 — 필드명/순서/nullable
   여부(`@Size(max=255)`) 모두 일치 확인.
6. `ManualImportService` — `ManualImportService.java:52-67`
   (`toCreateRequest`)에서 신규 4개 필드에 전부 `null` 전달. 값 추측 없음
   (근거 기반 검증 원칙 준수).
7. Out of Scope 미침범 — `JobPostingController.java` diff 없음(필터
   파라미터 미추가), `AlioCollectorService.java`에 새 `Counter.builder`
   추가 없음(신규 Prometheus metric 없음), 기관유형/분류 텍스트 매핑·
   steps/files 저장 코드 없음, status 외 필드 전체 동기화 로직 없음 —
   모두 명세대로 손대지 않음.
8. Secret 미커밋 — 위 참고, 문제 없음.

## 테스트 결과

- test_count: 35
- test_pass_count: 35 (failures: 0, errors: 0)
- 실행 방법: 저장소 루트 `docker compose ps`로 PostgreSQL/Redis 기동 확인
  후, `.env` source → `cd backend && ./gradlew test --rerun --console=plain`
  직접 재실행(캐시 우회). `build/test-results/test/*.xml`의 `tests=`/
  `failures=`/`errors=` 합산으로 35/0/0 재확인.
- Codex가 보고했던 "sandbox 제약으로 자체 실행 불가" 상태와 별개로,
  Claude(오케스트레이터)의 최초 실행 및 이번 리뷰 재실행 모두 통과.

## Findings

- 특이사항 없음. 과도한 추상화나 불필요한 패턴 추가 없음(mutator를
  `status` 하나로 최소화한 설계가 Technical Notes의 의도와 일치).
- 신규 production/test dependency 없음.
- 자기소개서 관련 로직 아님(해당 없음), 다만 유사 원칙인 "AI가 사용자가
  제공하지 않은 값을 추정하지 않는다"는 `ManualImportService`의 신규 필드
  `null` 처리에서 잘 지켜짐.
- 사소한 참고(수정 불필요): `alio-list-response-closed.json` fixture는
  `files`/`steps` 키가 없는데(다른 valid fixture는 빈 배열 포함), 파싱
  테스트(`AlioJobListResponseParsingTest`)나 컬렉터 테스트 어느 쪽도 이
  fixture로 `files`/`steps` 파싱을 검증하지 않으므로 실제 영향 없음.

## 다음 액션

- **PASS.** COLLECT-002 완료 처리 가능. `.ai/metrics/metrics.jsonl`에
  최종 상태(review_round=1, verdict=PASS, test 35/35) 기록 권장.
- 남은 것은 `[수동]` 실제 키 재검증 1건뿐(Task 명세상 자동 리뷰 범위
  밖) — 오케스트레이터가 별도로 수행할지 사용자와 확인 필요.
