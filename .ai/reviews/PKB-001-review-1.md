---
task_id: PKB-001
review_round: 1
reviewer: claude
reviewed_at: 2026-08-18T18:00:00+09:00
verdict: NEEDS_REVISION
---

## Acceptance Criteria 체크

1. `POST` type+title만 → 201, bullets/tags 빈 배열 — **충족**.
   `CareerExperienceControllerTest.createsMinimalAndFullExperienceWithOrderedBulletsAndDeduplicatedTags` L28-31.
2. `POST` bullets(3)/tags(3) → sortOrder 0/1/2, dedup 2건 — **충족**.
   같은 테스트 L33-42. `CareerExperienceService.saveBullets` (`CareerExperienceService.java:87-93`)가
   배열 index를 그대로 `sortOrder`로 저장.
3. `POST` title 누락 → 400, row 미생성(부모/자식 모두) — **충족**.
   `rejectsInvalidRequestsAtomically` L46-57, `repository.count()`/`bulletRepository.count()`/
   `tagRepository.count()` 모두 확인.
4. `POST` endDate<startDate → 400, row 미생성 — **충족**. 같은 테스트 L51-53.
5. `POST` tags 대소문자 dedup — **충족**(1/2와 같은 테스트로 커버, `"Spring"/"spring"` → tags.length()==2).
6. `GET` type 필터 — **충족(단, 격리 검증 아님)**. `listsWithCombinedFiltersAsFlatResponses` L63-67에서
   `type=PROJECT&keyword=시스템`을 **동시에** 넘겨 검증 — type 단독으로 필터링되는지, keyword가
   결과를 좁힌 것인지 이 테스트만으로는 구분 불가(둘 다 같은 항목을 제외시킴). 구현(`search` JPQL,
   `CareerExperienceRepository.java:11-20`)은 `(:type IS NULL OR ...) AND (:keyword IS NULL OR ...)`로
   두 파라미터가 독립적으로 optional bind되어 정상 동작할 것으로 보이나, 테스트로 개별 검증되지 않음.
7. `GET` keyword 필터 — 6번과 동일 사유로 **충족(단, 격리 검증 아님)**.
8. `GET` 목록 응답에 bullets/tags 없음(flat) — **충족**. 같은 테스트 L66-67,
   `jsonPath("$.content[0].bullets").doesNotExist()`/`tags`도 동일. `CareerExperienceResponse`
   (`dto/CareerExperienceResponse.java`)에 bullets/tags 필드 자체가 없음.
9. `GET` 목록 N+1 부재 — **충족**. Task 명세가 명시한 검증 방식("코드 경로상 목록 조회가
   `ExperienceBulletRepository`/`ExperienceTagRepository`를 참조하지 않음")대로 확인함:
   `CareerExperienceService.findAll()` (`CareerExperienceService.java:35-38`)은
   `CareerExperienceRepository.search()`만 호출하고, 이 메서드는 JPQL 생성자 표현식
   (`CareerExperienceRepository.java:11-20`)으로 `CareerExperienceResponse`를 직접 만들어
   bulletRepository/tagRepository를 전혀 참조하지 않음. 코드 경로상 N+1 불가능.
10. `GET /{id}` bullets sortOrder ASC + tags, 404 — **충족**.
    `CareerExperienceService.detail()` (`CareerExperienceService.java:80-86`)이
    `findByCareerExperienceIdOrderBySortOrderAsc` 사용. 404는
    `getsDetailAndPatchesListsUsingWholeReplacementRules`(L91-93, 최종 GET) 및
    `returnsNotFoundAndDeletesWithDatabaseCascade`(L103)에서 확인.
11. `PATCH` summary만 변경, 나머지 유지 — **충족**. `getsDetailAndPatchesListsUsingWholeReplacementRules`
    L78-82.
12. `PATCH` bullets 생략 시 무변경 — **충족**. 같은 테스트, 첫 PATCH 호출(`{"summary":"변경"}`)에서
    bullets/tags 필드 자체가 요청에 없고 응답에서 기존 값 유지 확인(L80-82).
13. `PATCH {"bullets":[]}` → 전체 삭제 — **충족**. 같은 테스트 L83-86.
14. `PATCH {"bullets":[...]}` → 전체 교체(재조회로 확인) — **충족**. 같은 테스트 L87-92,
    PATCH 이후 별도 `GET`으로 재조회하여 확인(L91-92).
15. `PATCH` bullets/tags 독립적 처리 — **부분 충족**. L87-90에서 `bullets`만 교체하고
    `tags: null`을 보내 tags가 무변경(`"Kotlin"` 유지)임을 확인 — "bullets 변경 + tags 무변경"
    방향은 검증됨. 그러나 반대 방향("tags만 변경 + bullets 무변경", 즉 `bullets` 필드를 생략하고
    `tags`만 배열로 보내는 케이스)은 테스트에 없음. L83-86은 bullets/tags를 **동시에** 바꾸는
    케이스라 독립성 증거가 되지 않음.
16. `PATCH` 존재하지 않는 id → 404 — **충족**. `returnsNotFoundAndDeletesWithDatabaseCascade` L104-105.
17. `DELETE` 204 → 단건조회 404, 존재하지 않는 id → 404 — **구현은 정상, 테스트가 실패**(아래
    "테스트 결과" 참고). `CareerExperienceService.delete()` 자체는 정상 동작하지만, 이를 검증하는
    `returnsNotFoundAndDeletesWithDatabaseCascade` 테스트가 Hibernate flush-time 예외로
    실패하여 자동 검증이 통과하지 못한 상태.
18. `DELETE` cascade(bullets/tags 함께 삭제) — 위와 동일 사유로 테스트 미통과.
19. 기존 JobPosting/COLLECT/JobApplication/ApplicationStage 회귀 없음 — **충족**. `git diff --stat`으로
    `job`/`collector`/`application` 패키지 무변경 확인, 전체 스위트 재실행 결과 `career` 패키지 외
    실패 없음(아래 테스트 결과).
20. `cd backend && ./gradlew test` 전체 실패 0건 — **미충족**. 100개 중 1개 실패
    (`CareerExperienceControllerTest.returnsNotFoundAndDeletesWithDatabaseCascade`).

추가 확인 항목(리뷰 요청 5번):
- Migration `uk_experience_tags_experience_keyword` — **명세대로 표현식 인덱스**로 작성됨
  (`CREATE UNIQUE INDEX ... ON experience_tags (career_experience_id, LOWER(keyword))`,
  `V7__create_career_experiences_tables.sql:33-34`). plain `CONSTRAINT ... UNIQUE`가 아님.
- `@Transactional` — `create()`/`update()`/`delete()`에만 적용, `findById()`/`findAll()`에는
  없음(`CareerExperienceService.java:25,42,70` vs `35,40`). ADR-0019 그대로 준수.
- PATCH null/[]/[...] 컨벤션 — `update()` L56-65에서 `request.bullets() != null`/
  `request.tags() != null`으로 정확히 구분. DTO가 `List<...>` 타입이라 Jackson이 JSON `null`과
  `[]`를 자연스럽게 구분(record 필드에 default 없음). 정확히 구현됨.
- `startDate` nullable — `CareerExperience.java:17`에 `@Column(nullable=false)` 없음(plain
  `private LocalDate startDate;`). Migration도 `start_date DATE`(NOT NULL 없음). 명세대로 nullable.
- tag dedup(대소문자 무시, 첫 표기 유지) — 애플리케이션 레벨(`saveTags`,
  `CareerExperienceService.java:94-101`, `HashSet<String>`에 `toLowerCase()` 넣고 `add()` 성공한
  것만 저장 → 먼저 등장한 표기 유지) + DB UNIQUE 표현식 인덱스 백스톱 모두 존재. 둘 다 확인.
- cascade 삭제 — `experience_bullets`/`experience_tags` 두 테이블 FK 모두
  `ON DELETE CASCADE`(`V7__create_career_experiences_tables.sql:17,28`). 충족.
- 기존 `job`/`collector`/`application` 패키지 — `git diff --stat`으로 무변경 확인(추적 안 된 신규
  파일만 존재, 기존 파일 수정 없음).

## 테스트 결과

`cd backend && ./gradlew test` 직접 실행(사전에 `docker compose ps`로 postgres/redis 기동 확인,
이미 healthy 상태였음).

- test_count: 100
- test_pass_count: 99
- 실패 1건: `CareerExperienceControllerTest.returnsNotFoundAndDeletesWithDatabaseCascade`
  - 예외: `org.hibernate.TransientPropertyValueException: Persistent instance of
    'ExperienceBullet' references an unsaved transient instance of 'CareerExperience'
    (persist the transient instance before flushing)`
  - 실패 위치: `CareerExperienceService.delete()`의 `repository.flush()` 호출
    (`CareerExperienceService.java:74`), 스택트레이스상
    `Cascade.cascadeToOne` → `AbstractFlushingEventListener.checkForTransientReferences` →
    `prepareEntityFlushes`에서 발생.
- `career` 패키지 외 전체 스위트(job/collector/application) 재실행 결과 회귀 없음(100건 중 실패는
  위 1건뿐).

### 실패 원인 판정 — 테스트 코드 문제(프로덕션 코드 결함 아님)

직접 코드를 읽고 스택트레이스를 재현하여 확인한 결과, **프로덕션 코드가 아니라 테스트 코드의
버그**로 판정한다. 근거:

1. `CareerExperienceService.delete()`(`CareerExperienceService.java:70-75`)는 `find(id)` →
   `repository.delete(entity)` → `repository.flush()`만 수행하고, bullet/tag repository를
   전혀 호출하지 않는다 — Task 명세 12번("두 자식 FK 모두 `ON DELETE CASCADE`... Entity에는
   `@OneToMany`/양방향 관계를 추가하지 않는다")대로 **DB 레벨 cascade에만 의존**하도록
   정확히 구현되어 있다. 이는 명세가 요구하는 설계다.
2. 실제 요청 흐름(1 HTTP 요청 = 1 트랜잭션 = 새 persistence context)에서는 `delete()` 호출 전에
   그 트랜잭션 안에서 `ExperienceBullet`/`ExperienceTag`를 미리 로드/생성하는 코드 경로가
   존재하지 않는다 — `delete()` 자신도 이들을 로드하지 않는다. 따라서 실제 운영 트래픽에서는
   이 예외가 재현되지 않는다.
3. 실패한 테스트만 이 예외를 만든다: `returnsNotFoundAndDeletesWithDatabaseCascade`는
   `@Transactional`이 붙은 테스트 메서드 안에서 `repository.saveAndFlush()`/
   `bulletRepository.saveAndFlush()`/`tagRepository.saveAndFlush()`로 부모+자식을 **테스트
   코드가 직접** 만들고, 이어서 같은 스레드(같은 트랜잭션·같은 persistence context)에서
   MockMvc로 DELETE를 호출한다 — 서비스가 부모를 지우고 flush할 때, 여전히 persistence
   context에 남아있는(unchanged, managed) 자식 `ExperienceBullet`이 non-nullable FK로
   "곧 삭제될 부모"를 참조하고 있어 Hibernate의 flush-time 참조 무결성 검사
   (`checkForTransientReferences`)에 걸린다.
4. 이 프로젝트 자체가 이미 같은 패턴을 올바르게 처리한 선례를 갖고 있다:
   `CareerExperienceRepositoryTest.deletingExperienceCascadesToChildren()`
   (`CareerExperienceRepositoryTest.java:44-56`)은 정확히 같은 상황(부모+자식 직접 생성 후
   부모 삭제)에서 `entityManager.clear()`(L48)를 호출해 persistence context를 비운 뒤
   삭제를 수행하며, 이 테스트는 **100건 중 99건 통과에 포함되어 정상 통과**한다. 즉 Codex는
   Repository 테스트에서는 이 문제를 이미 알고 올바르게 회피했지만, Controller 테스트
   (`CareerExperienceControllerTest`)에는 같은 처리를 빠뜨렸다.
5. APPLICATION-002 round 1의 `ApplicationStageRepositoryTest.deletingApplicationCascadesToStages()`
   케이스와 근본 원인이 동일하며, 그때도 프로덕션 코드는 무변경으로 확인되고 테스트 코드만
   (`TestEntityManager.flush()+clear()`) 수정되어 해결됐다 — 이번에도 같은 클래스의 문제다.

## Findings

- **[블로킹]** `CareerExperienceControllerTest.returnsNotFoundAndDeletesWithDatabaseCascade`가
  persistence context 오염으로 인한 Hibernate flush-time 예외로 실패한다(AC 17/18/20 미충족).
  프로덕션 코드(`CareerExperienceService.delete()`)는 수정 불필요 — 테스트 코드만 고치면 된다.
- **[경미]** AC 6/7(type 필터, keyword 필터 개별 검증)이 `listsWithCombinedFiltersAsFlatResponses`
  하나의 테스트에서 두 파라미터를 동시에 넘겨서만 검증되어, type 단독/keyword 단독으로 필터링이
  동작하는지 테스트로는 구분되지 않는다. 구현(JPQL의 독립적 `OR :param IS NULL` 조건)은 정상으로
  보이나 테스트 커버리지 갭이다.
- **[경미]** AC 15(bullets/tags 독립적 PATCH)가 "tags만 변경, bullets는 생략되어 무변경"
  방향은 테스트에 없다("bullets만 변경, tags는 null이라 무변경" 방향만 있음). 완전한 양방향
  독립성 증거는 아니다.
- **[경미/설계 참고]** `CareerExperienceRepository.findByType(ExperienceType, Pageable)`
  (`CareerExperienceRepository.java:21`)이 프로덕션 코드(Service/Controller) 어디에서도 호출되지
  않고 `CareerExperienceRepositoryTest.savesFiltersAndOrdersBullets`에서만 사용된다 — 목록
  조회는 전부 `search()`를 통한다. 죽은 API는 아니지만(테스트 전용), 명세에 없는 메서드이므로
  필요 없다면 제거하거나, 유지한다면 그 이유를 남기는 편이 낫다. 수정 필수 아님.
- Secret/API Key 커밋 없음. 신규 production dependency 없음(명세와 일치). 자기소개서 관련
  로직 없음(이번 Task 범위 아님) — 근거 기반 검증 원칙 위반 사항 없음.
- `.ai/metrics/metrics.jsonl` self-report 오염 건은 이미 Claude가 되돌렸음을 확인(현재
  `git diff`에 해당 파일 변경 없음) — 재조치 불필요.

## 다음 액션

**NEEDS_REVISION** — 같은 Codex thread(`01a01408-3413-7ec3-9529-7dad843edb16`)에 아래를
그대로 전달해 수정 요청.

### 수정 요청 1(필수, 블로킹) — 테스트 코드만 수정

파일: `backend/src/test/java/com/careerops/backend/career/CareerExperienceControllerTest.java`

- `jakarta.persistence.EntityManager`를 import하고, `@Autowired EntityManager entityManager;`
  필드를 추가한다(`CareerExperienceRepositoryTest.java`가 이미 쓰는 것과 동일 패턴).
- `returnsNotFoundAndDeletesWithDatabaseCascade()`에서 부모(`repository.saveAndFlush(...)`)
  + 자식(`bulletRepository.saveAndFlush(...)`, `tagRepository.saveAndFlush(...)`) 생성 직후,
  MockMvc `delete(...)` 호출 **이전에** `entityManager.clear();`를 추가한다. 이렇게 하면 테스트가
  직접 만든 `ExperienceBullet`/`ExperienceTag`가 persistence context에서 detach되어,
  `CareerExperienceService.delete()`가 부모를 지우고 flush할 때 Hibernate의
  `checkForTransientReferences`가 더 이상 "곧 삭제될 부모를 참조하는 managed 자식"을 발견하지
  않는다 — `CareerExperienceRepositoryTest.deletingExperienceCascadesToChildren()`
  (`CareerExperienceRepositoryTest.java:44-56`, 특히 L48의 `entityManager.clear()`)과 정확히
  같은 처리다.
- `entity.getId()`는 detach된 엔티티에서도 getter 호출로 문제 없이 값을 반환하므로, `clear()`
  이후 `entity.getId()`를 계속 사용하는 나머지 코드는 그대로 두면 된다.
- **`CareerExperienceService.java`는 수정하지 않는다** — `delete()`는 Task 명세 12번(DB
  `ON DELETE CASCADE`에만 의존, `@OneToMany`/양방향 관계 추가 금지)대로 정확히 구현되어 있고,
  이 실패는 실제 운영 트래픽(1 요청 = 1 새 트랜잭션)에서는 재현되지 않는 테스트 전용 문제다.

### 수정 요청 2(선택, 경미) — 테스트 커버리지 보강

원한다면 다음도 함께 보강 요청 가능(블로킹 아님, PASS 판정에 필수는 아니지만 있으면 좋음):
- `listsWithCombinedFiltersAsFlatResponses`에 type-only, keyword-only 각각 단독 파라미터로
  호출하는 케이스를 추가해 AC 6/7을 명확히 개별 검증.
- `getsDetailAndPatchesListsUsingWholeReplacementRules`에 "tags만 배열로 교체하고 bullets는
  생략(무변경)"하는 반대 방향 케이스를 추가해 AC 15의 독립성을 양방향으로 증명.

### 재검증 절차

수정 후 `cd backend && ./gradlew test` 재실행하여 100/100(또는 추가 테스트 포함 총계) 전부
통과 확인. 통과하면 라운드 2 리뷰에서 PASS 처리 가능.
