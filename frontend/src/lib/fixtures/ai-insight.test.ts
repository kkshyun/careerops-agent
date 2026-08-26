import assert from "node:assert/strict";
import {describe,it} from "node:test";
import {AI_INSIGHT_BACKEND_DEMO_JOB,AI_INSIGHT_FIXTURE_DEMO_JOB,applicationDraftFixture,hasAiInsightDemo,hasValidDraftCharacterCounts} from "./ai-insight.ts";

describe("AI insight fixtures",()=>{
  it("recognizes the backend numeric demo job id and the fixture string id",()=>{
    assert.equal(hasAiInsightDemo(7501),true);
    assert.equal(hasAiInsightDemo("7501"),true);
    assert.equal(hasAiInsightDemo("job-orbit-01"),true);
    assert.equal(hasAiInsightDemo(7502),false);
  });

  it("provides valid representative links for both data sources",()=>{
    assert.equal(AI_INSIGHT_BACKEND_DEMO_JOB.id,"7501");
    assert.equal(AI_INSIGHT_FIXTURE_DEMO_JOB.id,"job-orbit-01");
  });

  it("keeps every character count equal to the JavaScript draft length",()=>{
    assert.equal(hasValidDraftCharacterCounts(applicationDraftFixture),true);
    for(const question of applicationDraftFixture.questions){
      assert.equal(question.characterCount,question.draft.length);
    }
  });
});
