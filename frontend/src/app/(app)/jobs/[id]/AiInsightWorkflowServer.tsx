import {apiBaseUrl} from "@/lib/api/client";
import {AI_INSIGHT_BACKEND_DEMO_JOB,AI_INSIGHT_FIXTURE_DEMO_JOB} from "@/lib/fixtures/ai-insight";
import AiInsightWorkflow from "./AiInsightWorkflow";

export default function AiInsightWorkflowServer({jobId}:{jobId:string}){
  const demoJob=apiBaseUrl?AI_INSIGHT_BACKEND_DEMO_JOB:AI_INSIGHT_FIXTURE_DEMO_JOB;
  return <AiInsightWorkflow jobId={jobId} demoJob={demoJob}/>;
}
