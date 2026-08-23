package com.careerops.backend.applicationdraft;

import com.careerops.backend.applicationdraft.llm.ApplicationDraftException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApplicationDraftController.class)
class ApplicationDraftControllerTest {
    @Autowired MockMvc mvc; @MockitoBean ApplicationDraftService service;
    @Test void emptyAndElevenQuestionsAreRejectedBeforeService() throws Exception {mvc.perform(post("/api/jobs/1/application-draft").contentType("application/json").content("{\"questions\":[]}" )).andExpect(status().isBadRequest());
        String item="{\"questionId\":\"q\",\"question\":\"x\"}";mvc.perform(post("/api/jobs/1/application-draft").contentType("application/json").content("{\"questions\":["+String.join(",",java.util.Collections.nCopies(11,item))+"]}" )).andExpect(status().isBadRequest());verifyNoInteractions(service);}
    @Test void providerFailureIs502() throws Exception {when(service.draft(anyLong(),any())).thenThrow(new ApplicationDraftException(ApplicationDraftException.Reason.NETWORK_TIMEOUT));mvc.perform(post("/api/jobs/1/application-draft").contentType("application/json").content("{\"questions\":[{\"questionId\":\"q\",\"question\":\"x\"}]}" )).andExpect(status().isBadGateway());}
}
