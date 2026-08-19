package com.careerops.backend.match;

import com.careerops.backend.career.Award;
import com.careerops.backend.career.CareerExperience;
import com.careerops.backend.career.Certification;
import com.careerops.backend.career.Education;
import com.careerops.backend.career.ExperienceTag;
import com.careerops.backend.match.KeywordNormalizer.JobCategory;
import com.careerops.backend.match.dto.MatchEvidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CareerMatchEngine {
    static final double EXPERIENCE_WEIGHT = 0.70;
    static final double CERTIFICATION_WEIGHT = 0.15;
    static final double EDUCATION_WEIGHT = 0.10;
    static final double AWARD_WEIGHT = 0.05;

    private static final Comparator<MatchEvidence> EVIDENCE_ORDER =
            Comparator.comparingDouble(MatchEvidence::score).reversed().thenComparing(MatchEvidence::id);

    private final KeywordNormalizer normalizer;

    public CareerMatchEngine(KeywordNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public MatchResult calculate(
            String jobCategory,
            List<CareerExperience> experiences,
            Map<Long, List<ExperienceTag>> tagsByExperienceId,
            List<Certification> certifications,
            List<Education> educations,
            List<Award> awards) {
        List<JobCategory> categories = normalizer.splitJobCategories(jobCategory);
        Set<Integer> matchedCategoryIndexes = new HashSet<>();

        List<MatchEvidence> experienceEvidence = experiences.stream()
                .map(item -> scoreExperience(item, tagsByExperienceId.getOrDefault(item.getId(), List.of()),
                        categories, matchedCategoryIndexes))
                .filter(evidence -> evidence.score() > 0.0)
                .sorted(EVIDENCE_ORDER).limit(5).toList();
        List<MatchEvidence> certificationEvidence = certifications.stream()
                .map(item -> scoreCertification(item, categories, matchedCategoryIndexes))
                .filter(evidence -> evidence.score() > 0.0)
                .sorted(EVIDENCE_ORDER).limit(3).toList();
        List<MatchEvidence> educationEvidence = educations.stream()
                .map(item -> scoreEducation(item, categories, matchedCategoryIndexes))
                .filter(evidence -> evidence.score() > 0.0)
                .sorted(EVIDENCE_ORDER).limit(3).toList();
        List<MatchEvidence> awardEvidence = awards.stream()
                .map(item -> scoreAward(item, categories, matchedCategoryIndexes))
                .filter(evidence -> evidence.score() > 0.0)
                .sorted(EVIDENCE_ORDER).limit(3).toList();

        double overallScore = maxScore(experienceEvidence) * EXPERIENCE_WEIGHT
                + maxScore(certificationEvidence) * CERTIFICATION_WEIGHT
                + maxScore(educationEvidence) * EDUCATION_WEIGHT
                + maxScore(awardEvidence) * AWARD_WEIGHT;
        overallScore = Math.max(0.0, Math.min(1.0, overallScore));

        List<String> unmatched = new ArrayList<>();
        for (int index = 0; index < categories.size(); index++) {
            if (!matchedCategoryIndexes.contains(index)) {
                unmatched.add(categories.get(index).raw());
            }
        }
        return new MatchResult(overallScore, experienceEvidence, certificationEvidence,
                educationEvidence, awardEvidence, unmatched);
    }

    private MatchEvidence scoreExperience(CareerExperience item, List<ExperienceTag> tags,
                                          List<JobCategory> categories, Set<Integer> matchedIndexes) {
        List<String> fields = new ArrayList<>();
        double score = 0.0;
        if (matchesAny(tags.stream().map(ExperienceTag::getKeyword).toList(), categories, matchedIndexes)) {
            fields.add("tags"); score += 0.5;
        }
        if (matches(item.getTitle(), categories, matchedIndexes)) { fields.add("title"); score += 0.3; }
        if (matches(item.getSummary(), categories, matchedIndexes)) { fields.add("summary"); score += 0.2; }
        if (matches(item.getDetail(), categories, matchedIndexes)) { fields.add("detail"); score += 0.1; }
        return new MatchEvidence("CAREER_EXPERIENCE", item.getId(), item.getTitle(), Math.min(1.0, score), fields);
    }

    private MatchEvidence scoreCertification(Certification item, List<JobCategory> categories,
                                             Set<Integer> matchedIndexes) {
        List<String> fields = new ArrayList<>();
        if (matches(item.getName(), categories, matchedIndexes)) fields.add("name");
        if (matches(item.getDescription(), categories, matchedIndexes)) fields.add("description");
        return new MatchEvidence("CERTIFICATION", item.getId(), item.getName(), fields.isEmpty() ? 0.0 : 1.0, fields);
    }

    private MatchEvidence scoreEducation(Education item, List<JobCategory> categories, Set<Integer> matchedIndexes) {
        boolean matched = matches(item.getMajor(), categories, matchedIndexes);
        return new MatchEvidence("EDUCATION", item.getId(), item.getMajor(), matched ? 1.0 : 0.0,
                matched ? List.of("major") : List.of());
    }

    private MatchEvidence scoreAward(Award item, List<JobCategory> categories, Set<Integer> matchedIndexes) {
        List<String> fields = new ArrayList<>();
        if (matches(item.getTitle(), categories, matchedIndexes)) fields.add("title");
        if (matches(item.getDescription(), categories, matchedIndexes)) fields.add("description");
        return new MatchEvidence("AWARD", item.getId(), item.getTitle(), fields.isEmpty() ? 0.0 : 1.0, fields);
    }

    private boolean matchesAny(List<String> values, List<JobCategory> categories, Set<Integer> matchedIndexes) {
        boolean matched = false;
        for (String value : values) matched |= matches(value, categories, matchedIndexes);
        return matched;
    }

    private boolean matches(String value, List<JobCategory> categories, Set<Integer> matchedIndexes) {
        boolean matched = false;
        for (int index = 0; index < categories.size(); index++) {
            if (normalizer.matches(value, categories.get(index).normalized())) {
                matchedIndexes.add(index);
                matched = true;
            }
        }
        return matched;
    }

    private double maxScore(List<MatchEvidence> evidence) {
        return evidence.stream().mapToDouble(MatchEvidence::score).max().orElse(0.0);
    }

    public record MatchResult(double overallScore, List<MatchEvidence> experiences,
                              List<MatchEvidence> certifications, List<MatchEvidence> educations,
                              List<MatchEvidence> awards, List<String> unmatchedJobCategories) {
    }
}
