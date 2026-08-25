package com.careerops.backend.recommend;

import com.careerops.backend.career.*;
import com.careerops.backend.job.*;
import com.careerops.backend.recommend.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class JobRecommendationTransactionIntegrationTest {
    @Autowired JobRecommendationService service;
    @Autowired CareerExperienceRepository experiences;
    @Autowired JobPostingRepository jobs;
    @Autowired TransactionCapturingClient client;
    CareerExperience experience;
    JobPosting job;

    @BeforeEach void setUp(){
        experience=experiences.saveAndFlush(new CareerExperience(ExperienceType.PROJECT,"RECOMMEND TX",null,null,null,null,"summary","detail"));
        String id=UUID.randomUUID().toString();
        job=jobs.saveAndFlush(new JobPosting("RECOMMEND TX","RECOMMEND TX","regular","new","degree","OPEN",null,"IT",null,LocalDate.now(),LocalDate.now().plusDays(1),"TEST","https://example.invalid/recommend-tx/"+id,id));
        client.reset();
    }

    @AfterEach void tearDown(){ jobs.delete(job); jobs.flush(); experiences.delete(experience); experiences.flush(); }

    @Test void providerDelayRunsWithoutTransactionAndDoesNotBlockIndependentQuery() throws Exception {
        try(ExecutorService executor=Executors.newSingleThreadExecutor()){
            Future<?> recommendation=executor.submit(()->service.recommend(5));
            assertThat(client.entered.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(client.transactionActive.get()).isFalse();
            assertThat(jobs.count()).isPositive();
            client.release.countDown();
            recommendation.get(5,TimeUnit.SECONDS);
        }finally{client.release.countDown();}
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary TransactionCapturingClient transactionCapturingClient(){return new TransactionCapturingClient();}
    }

    static class TransactionCapturingClient implements JobRecommendationClient {
        final AtomicBoolean called=new AtomicBoolean();
        final AtomicBoolean transactionActive=new AtomicBoolean(true);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        void reset(){called.set(false);transactionActive.set(true);entered=new CountDownLatch(1);release=new CountDownLatch(1);}
        public RawRecommendationResult recommend(RecommendationInput input,int providerTopK){
            called.set(true);
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            entered.countDown();
            try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test release timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
            return new RawRecommendationResult(List.of());
        }
    }
}
