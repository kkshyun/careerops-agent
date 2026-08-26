"use server";

import {redirect} from "next/navigation";
import {revalidatePath} from "next/cache";
import {apiBaseUrl} from "@/lib/api/client";
import {createApplicationRequest,deleteApplicationRequest,updateApplicationRequest} from "@/lib/api/applications";
import type {Application,ApplicationStatus} from "@/lib/types";
import {actionError,DEMO_MESSAGE} from "./result";
import type {ActionResult} from "./types";

const demo=():ActionResult<never>=>({ok:false,kind:"demo",message:DEMO_MESSAGE});

export async function createApplication(input:{jobPostingId:string;memo?:string;appliedAt?:string}):Promise<ActionResult<Application>>{
  if(!apiBaseUrl)return demo();
  try{return {ok:true,data:await createApplicationRequest(input)}}catch(error){return actionError(error,"이미 이 채용공고에 지원 등록이 되어 있습니다.")}
}

export async function updateApplicationStatus(id:string,status:ApplicationStatus):Promise<ActionResult<Application>>{
  if(!apiBaseUrl)return demo();
  try{const data=await updateApplicationRequest(id,{status});revalidatePath(`/applications/${id}`);revalidatePath("/applications");return {ok:true,data}}catch(error){return actionError(error)}
}

export async function updateApplicationMemo(id:string,memo:string):Promise<ActionResult<Application>>{
  if(!apiBaseUrl)return demo();
  try{const data=await updateApplicationRequest(id,{memo});revalidatePath(`/applications/${id}`);revalidatePath("/applications");return {ok:true,data}}catch(error){return actionError(error)}
}

export async function updateApplicationAppliedAt(id:string,appliedAt:string|null):Promise<ActionResult<Application>>{
  if(!apiBaseUrl)return demo();
  try{const data=await updateApplicationRequest(id,{appliedAt});revalidatePath(`/applications/${id}`);revalidatePath("/applications");return {ok:true,data}}catch(error){return actionError(error)}
}

export async function deleteApplication(id:string,opts:{redirectTo?:string}={}):Promise<ActionResult<undefined>>{
  if(!apiBaseUrl)return demo();
  try{await deleteApplicationRequest(id)}catch(error){return actionError(error)}
  revalidatePath("/applications");
  if(opts.redirectTo)redirect(opts.redirectTo);
  return {ok:true,data:undefined};
}
