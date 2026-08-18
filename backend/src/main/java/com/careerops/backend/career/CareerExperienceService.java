package com.careerops.backend.career;

import com.careerops.backend.career.dto.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;

@Service
public class CareerExperienceService {
    private final CareerExperienceRepository repository;
    private final ExperienceBulletRepository bulletRepository;
    private final ExperienceTagRepository tagRepository;

    public CareerExperienceService(CareerExperienceRepository repository,
                                   ExperienceBulletRepository bulletRepository,
                                   ExperienceTagRepository tagRepository) {
        this.repository = repository; this.bulletRepository = bulletRepository; this.tagRepository = tagRepository;
    }

    @Transactional
    public CareerExperienceDetailResponse create(CareerExperienceCreateRequest request) {
        validateDates(request.startDate(), request.endDate());
        CareerExperience entity = repository.save(new CareerExperience(request.type(), request.title(),
                request.organization(), request.role(), request.startDate(), request.endDate(), request.summary(), request.detail()));
        saveBullets(entity, request.bullets() == null ? List.of() : request.bullets());
        saveTags(entity, request.tags() == null ? List.of() : request.tags());
        return detail(entity);
    }

    public CareerExperienceListResponse findAll(ExperienceType type, String keyword, Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), 100);
        return CareerExperienceListResponse.from(repository.search(type, keywordPattern(keyword), PageRequest.of(pageable.getPageNumber(), size)));
    }

    private static String keywordPattern(String keyword) {
        return keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%";
    }

    public CareerExperienceDetailResponse findById(Long id) { return detail(find(id)); }

    @Transactional
    public CareerExperienceDetailResponse update(Long id, CareerExperienceUpdateRequest request) {
        CareerExperience entity = find(id);
        LocalDate start = request.startDate() == null ? entity.getStartDate() : request.startDate();
        LocalDate end = request.endDate() == null ? entity.getEndDate() : request.endDate();
        validateDates(start, end);
        if (request.type() != null) entity.updateType(request.type());
        if (request.title() != null) entity.updateTitle(request.title());
        if (request.organization() != null) entity.updateOrganization(request.organization());
        if (request.role() != null) entity.updateRole(request.role());
        if (request.startDate() != null) entity.updateStartDate(request.startDate());
        if (request.endDate() != null) entity.updateEndDate(request.endDate());
        if (request.summary() != null) entity.updateSummary(request.summary());
        if (request.detail() != null) entity.updateDetail(request.detail());
        if (request.bullets() != null) {
            bulletRepository.deleteByCareerExperienceId(id);
            bulletRepository.flush();
            saveBullets(entity, request.bullets());
        }
        if (request.tags() != null) {
            tagRepository.deleteByCareerExperienceId(id);
            tagRepository.flush();
            saveTags(entity, request.tags());
        }
        repository.saveAndFlush(entity);
        return detail(entity);
    }

    @Transactional
    public void delete(Long id) {
        CareerExperience entity = find(id);
        repository.delete(entity);
        repository.flush();
    }

    private CareerExperience find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private CareerExperienceDetailResponse detail(CareerExperience entity) {
        var bullets = bulletRepository.findByCareerExperienceIdOrderBySortOrderAsc(entity.getId()).stream()
                .map(ExperienceBulletResponse::from).toList();
        var tags = tagRepository.findByCareerExperienceIdOrderByIdAsc(entity.getId()).stream()
                .map(ExperienceTag::getKeyword).toList();
        return CareerExperienceDetailResponse.from(entity, bullets, tags);
    }
    private void saveBullets(CareerExperience entity, List<ExperienceBulletRequest> requests) {
        for (int i = 0; i < requests.size(); i++) {
            ExperienceBulletRequest item = requests.get(i);
            bulletRepository.save(new ExperienceBullet(entity, item.bulletType(), item.content(), i));
        }
        bulletRepository.flush();
    }
    private void saveTags(CareerExperience entity, List<String> tags) {
        Set<String> seen = new HashSet<>();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) continue;
            if (seen.add(tag.toLowerCase(Locale.ROOT))) tagRepository.save(new ExperienceTag(entity, tag));
        }
        tagRepository.flush();
    }
    private void validateDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must not be before startDate");
        }
    }
}
