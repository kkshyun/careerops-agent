package com.careerops.backend.recommend;

import com.careerops.backend.career.*;
import com.careerops.backend.job.*;
import com.careerops.backend.pkbimport.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecommendationCandidateReaderTest {
    JobPostingRepository jobs=mock(JobPostingRepository.class); CareerExperienceRepository experiences=mock(CareerExperienceRepository.class);
    ExperienceTagRepository tags=mock(ExperienceTagRepository.class); CertificationRepository certifications=mock(CertificationRepository.class);
    EducationRepository educations=mock(EducationRepository.class); AwardRepository awards=mock(AwardRepository.class); ImportCandidateRepository imports=mock(ImportCandidateRepository.class);

    @Test void materializesApprovedImmutableSnapshotAndFlattensTags(){
        CareerExperience manual=experience(1L,SourceType.MANUAL,null), approved=experience(2L,SourceType.IMPORT,20L), pending=experience(3L,SourceType.IMPORT,30L);
        ImportCandidate approvedImport=mock(ImportCandidate.class), pendingImport=mock(ImportCandidate.class);
        when(approvedImport.getId()).thenReturn(20L);when(approvedImport.getStatus()).thenReturn(ImportCandidateStatus.APPROVED);
        when(pendingImport.getId()).thenReturn(30L);when(pendingImport.getStatus()).thenReturn(ImportCandidateStatus.PENDING);
        ExperienceTag tag=mock(ExperienceTag.class);when(tag.getCareerExperience()).thenReturn(approved);when(tag.getKeyword()).thenReturn("spring");
        when(imports.findAll()).thenReturn(List.of(approvedImport,pendingImport));when(experiences.findAll()).thenReturn(List.of(manual,approved,pending));
        when(tags.findByCareerExperienceIdIn(List.of(1L,2L))).thenReturn(List.of(tag));when(certifications.findAll()).thenReturn(List.of());when(educations.findAll()).thenReturn(List.of());when(awards.findAll()).thenReturn(List.of());when(jobs.findAllByStatus("OPEN")).thenReturn(List.of(job(7L)));
        var input=new RecommendationCandidateReader(jobs,experiences,tags,certifications,educations,awards,imports).read();
        assertThat(input.experiences()).extracting(v->v.id()).containsExactly(1L,2L);
        assertThat(input.experiences().get(1).tags()).containsExactly("spring");
        assertThat(input.candidates()).extracting(v->v.id()).containsExactly(7L);
        assertThatThrownBy(()->input.experiences().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }
    private CareerExperience experience(long id,SourceType source,Long importId){CareerExperience value=mock(CareerExperience.class);when(value.getId()).thenReturn(id);when(value.getSourceType()).thenReturn(source);when(value.getSourceImportCandidateId()).thenReturn(importId);return value;}
    private JobPosting job(long id){JobPosting v=new JobPosting("c","t","regular","new","degree","OPEN",null,"IT",null,LocalDate.now(),LocalDate.now().plusDays(1),"TEST","url","external");ReflectionTestUtils.setField(v,"id",id);return v;}
}
