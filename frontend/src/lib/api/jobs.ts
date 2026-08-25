import { jobs, matchFixture } from "@/lib/fixtures/data";
import type { Job, JobDetail, JobMatch, Page } from "@/lib/types";
import { apiBaseUrl, fixturePage, getJson, queryString } from "./client";
export type JobQuery={status?:string;careerLevel?:string;companyName?:string;jobCategory?:string;page?:number;size?:number};
export async function getJobs(q:JobQuery={}):Promise<Page<Job>>{if(apiBaseUrl)return getJson(`/api/jobs?${queryString(q)}`);const list=jobs.filter(j=>(!q.status||j.status===q.status)&&(!q.careerLevel||j.careerLevel===q.careerLevel)&&(!q.companyName||j.companyName.includes(q.companyName))&&(!q.jobCategory||j.jobCategory.includes(q.jobCategory)));return fixturePage(list,q.page??0,q.size??20)}
export async function getJob(id:string):Promise<JobDetail>{if(apiBaseUrl)return getJson(`/api/jobs/${encodeURIComponent(id)}`);const item=jobs.find(j=>j.id===id);if(!item)throw new Error("채용공고를 찾을 수 없습니다.");return item}
export async function getJobMatch(id:string):Promise<JobMatch>{if(apiBaseUrl)return getJson(`/api/jobs/${encodeURIComponent(id)}/match`);return {...matchFixture,careerLevel:(jobs.find(j=>j.id===id)?.careerLevel??matchFixture.careerLevel)}}
