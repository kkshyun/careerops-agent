import { applications } from "@/lib/fixtures/data"; import type { Application,ApplicationDetail,ApplicationStatus,Page,Stage,StageResult,StageType } from "@/lib/types"; import {apiBaseUrl,deleteRequest,fixturePage,getJson,patchJson,postJson,queryString} from "./client";
export async function getApplications(q:{status?:string;jobPostingId?:string;page?:number;size?:number}={}):Promise<Page<Application>>{if(apiBaseUrl)return getJson(`/api/applications?${queryString(q)}`);let list=[...applications].sort((a,b)=>b.updatedAt.localeCompare(a.updatedAt));if(q.status)list=list.filter(a=>a.status===q.status);if(q.jobPostingId)list=list.filter(a=>a.jobPostingId===q.jobPostingId);return fixturePage(list,q.page??0,q.size??20)}
export async function getApplication(id:string):Promise<ApplicationDetail>{if(apiBaseUrl)return getJson(`/api/applications/${encodeURIComponent(id)}`);const item=applications.find(a=>a.id===id);if(!item)throw new Error("지원 내역을 찾을 수 없습니다.");return item}
export type ApplicationCreateRequest={jobPostingId:string;memo?:string;appliedAt?:string};
export type ApplicationUpdateRequest={status?:ApplicationStatus;memo?:string;appliedAt?:string|null};
export const createApplicationRequest=(body:ApplicationCreateRequest)=>postJson<Application>("/api/applications",body);
export const updateApplicationRequest=(id:string,body:ApplicationUpdateRequest)=>patchJson<Application>(`/api/applications/${encodeURIComponent(id)}`,body);
export const deleteApplicationRequest=(id:string)=>deleteRequest(`/api/applications/${encodeURIComponent(id)}`);
export type StageCreateRequest={stageType:StageType;label?:string;scheduledAt?:string;memo?:string};
export type StageUpdateRequest={label?:string;scheduledAt?:string|null;result?:StageResult;memo?:string};
const stagePath=(applicationId:string)=>`/api/applications/${encodeURIComponent(applicationId)}/stages`;
export const createStageRequest=(applicationId:string,body:StageCreateRequest)=>postJson<Stage>(stagePath(applicationId),body);
export const updateStageRequest=(applicationId:string,stageId:string,body:StageUpdateRequest)=>patchJson<Stage>(`${stagePath(applicationId)}/${encodeURIComponent(stageId)}`,body);
export const deleteStageRequest=(applicationId:string,stageId:string)=>deleteRequest(`${stagePath(applicationId)}/${encodeURIComponent(stageId)}`);
export const applicationStatuses:ApplicationStatus[]=["INTERESTED","PLANNED","SUBMITTED","OFFERED","REJECTED","WITHDRAWN"];
