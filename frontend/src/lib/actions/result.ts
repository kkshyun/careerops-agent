import {ApiError} from "../api/client.ts";
import type {ActionResult} from "./types";

export const DEMO_MESSAGE="데모 데이터에서는 저장되지 않습니다. 로컬에서 API_BASE_URL을 설정한 실제 Backend에 연결하면 저장할 수 있습니다.";

export function errorMessage(status:number, conflictMessage?:string):string{
  if(status===400)return "입력값을 확인해주세요.";
  if(status===404)return "대상을 찾을 수 없습니다. 새로고침 후 다시 시도해주세요.";
  if(status===409&&conflictMessage)return conflictMessage;
  return "요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.";
}

export function actionError(error:unknown,conflictMessage?:string):ActionResult<never>{
  if(error instanceof ApiError)return {ok:false,kind:"error",status:error.status,message:errorMessage(error.status,conflictMessage)};
  return {ok:false,kind:"error",message:errorMessage(500)};
}
