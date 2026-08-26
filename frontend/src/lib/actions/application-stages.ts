"use server";

import {revalidatePath} from "next/cache";
import {apiBaseUrl} from "@/lib/api/client";
import {createStageRequest,deleteStageRequest,updateStageRequest} from "@/lib/api/applications";
import type {Stage,StageResult,StageType} from "@/lib/types";
import {actionError,DEMO_MESSAGE} from "./result";
import type {ActionResult} from "./types";

const demo=():ActionResult<never>=>({ok:false,kind:"demo",message:DEMO_MESSAGE});

export async function createStage(applicationId:string,input:{stageType:StageType;label?:string;scheduledAt?:string;memo?:string}):Promise<ActionResult<Stage>>{
  if(!apiBaseUrl)return demo();
  try{const data=await createStageRequest(applicationId,input);revalidatePath(`/applications/${applicationId}`);return {ok:true,data}}catch(error){return actionError(error)}
}

export async function updateStage(applicationId:string,stageId:string,input:{label?:string;scheduledAt?:string|null;result?:StageResult;memo?:string}):Promise<ActionResult<Stage>>{
  if(!apiBaseUrl)return demo();
  try{const data=await updateStageRequest(applicationId,stageId,input);revalidatePath(`/applications/${applicationId}`);return {ok:true,data}}catch(error){return actionError(error)}
}

export async function deleteStage(applicationId:string,stageId:string):Promise<ActionResult<undefined>>{
  if(!apiBaseUrl)return demo();
  try{await deleteStageRequest(applicationId,stageId);revalidatePath(`/applications/${applicationId}`);return {ok:true,data:undefined}}catch(error){return actionError(error)}
}
