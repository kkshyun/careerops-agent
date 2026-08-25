"use server";
import {getJobMatch} from "@/lib/api/jobs";
export async function requestMatch(jobId:string){return getJobMatch(jobId)}
