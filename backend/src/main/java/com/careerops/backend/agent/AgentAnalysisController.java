package com.careerops.backend.agent;

import com.careerops.backend.agent.dto.AgentAnalysisResponse;
import com.careerops.backend.agent.llm.AgentAnalysisException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
public class AgentAnalysisController {
    private final AgentAnalysisService service;
    public AgentAnalysisController(AgentAnalysisService service){ this.service=service; }
    @PostMapping("/{jobId}/agent-analysis")
    public AgentAnalysisResponse analyze(@PathVariable Long jobId){ return service.analyze(jobId); }
    @ExceptionHandler(AgentAnalysisException.class) @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public void analysisFailure(){ }
}
