"use client";

import {useRef,useState} from "react";
import {useRouter} from "next/navigation";
import {ActionNotice} from "@/components/ActionNotice";
import ConfirmDialog,{type ConfirmDialogHandle} from "@/components/ConfirmDialog";
import {Field,FormActions,Select,SubmitButton,Textarea,TextInput} from "@/components/form";
import {Badge} from "@/components/ui";
import {createStage,deleteStage,updateStage} from "@/lib/actions/application-stages";
import type {ActionResult} from "@/lib/actions/types";
import {formatDate,stageResultLabel,stageTypeLabel} from "@/lib/format";
import type {Stage,StageResult,StageType} from "@/lib/types";
import styles from "../../App.module.css";
import local from "./StageEditor.module.css";

const stageTypes:StageType[]=["DOCUMENT","CODING_TEST","WRITTEN","INTERVIEW","FINAL","OTHER"];
const stageResults:StageResult[]=["PENDING","PASSED","FAILED","CANCELLED"];
const resultTone={PENDING:"neutral",PASSED:"success",FAILED:"danger",CANCELLED:"neutral"} as const;
const optional=(data:FormData,name:string)=>{const value=String(data.get(name)??"").trim();return value||undefined};
const localDateTime=(value:string|null)=>value?value.slice(0,16):"";

function StageItem({applicationId,stage}:{applicationId:string;stage:Stage}){
  const router=useRouter();
  const dialog=useRef<ConfirmDialogHandle>(null);
  const [editing,setEditing]=useState(false);
  const [result,setResult]=useState<ActionResult<Stage|undefined>|null>(null);
  const submit=async(data:FormData)=>{const response=await updateStage(applicationId,stage.id,{label:optional(data,"label"),scheduledAt:optional(data,"scheduledAt"),result:String(data.get("result")) as StageResult,memo:optional(data,"memo")});setResult(response);if(response.ok){setEditing(false);router.refresh()}};
  const remove=async()=>{const response=await deleteStage(applicationId,stage.id);setResult(response);if(response.ok)router.refresh();else throw new Error(response.message)};
  return <li><div className={local.stageHeader}><strong>{stageTypeLabel[stage.stageType]}{stage.label?` · ${stage.label}`:""}</strong><div className={local.itemActions}><button type="button" className={`${styles.button} ${styles.buttonSecondary}`} onClick={()=>setEditing(value=>!value)} aria-expanded={editing}>{editing?"수정 취소":"수정"}</button><button type="button" className={`${styles.button} ${styles.buttonSecondary}`} onClick={()=>dialog.current?.open()}>삭제</button></div></div><p className={styles.mono}>{stage.scheduledAt?formatDate(stage.scheduledAt,true):"일정 미정"}</p><Badge tone={resultTone[stage.result]}>{stageResultLabel[stage.result]}</Badge>{stage.memo&&<p className={styles.muted}>{stage.memo}</p>}{editing&&<form action={submit} className={local.form}><Field label="단계명 (선택)" htmlFor={`stage-label-${stage.id}`}><TextInput id={`stage-label-${stage.id}`} name="label" maxLength={100} defaultValue={stage.label??""}/></Field><Field label="예정 일시 (선택)" htmlFor={`stage-scheduled-${stage.id}`}><TextInput id={`stage-scheduled-${stage.id}`} name="scheduledAt" type="datetime-local" defaultValue={localDateTime(stage.scheduledAt)}/></Field><Field label="결과" htmlFor={`stage-result-${stage.id}`}><Select id={`stage-result-${stage.id}`} name="result" defaultValue={stage.result}>{stageResults.map(value=><option key={value} value={value}>{stageResultLabel[value]}</option>)}</Select></Field><Field label="메모 (선택)" htmlFor={`stage-memo-${stage.id}`}><Textarea id={`stage-memo-${stage.id}`} name="memo" maxLength={1000} defaultValue={stage.memo??""}/></Field><FormActions><SubmitButton>수정 저장</SubmitButton></FormActions></form>}<ActionNotice result={result}/><ConfirmDialog ref={dialog} title="이 전형 단계를 삭제할까요?" description="삭제한 전형 단계는 다시 확인할 수 없습니다." onConfirm={remove}/></li>
}

export default function StageEditor({applicationId,stages}:{applicationId:string;stages:Stage[]}){
  const router=useRouter();
  const [adding,setAdding]=useState(false);
  const [result,setResult]=useState<ActionResult<Stage>|null>(null);
  const submit=async(data:FormData)=>{const response=await createStage(applicationId,{stageType:String(data.get("stageType")) as StageType,label:optional(data,"label"),scheduledAt:optional(data,"scheduledAt"),memo:optional(data,"memo")});setResult(response);if(response.ok){setAdding(false);router.refresh()}};
  return <>{stages.length?<ol className={styles.timeline}>{stages.map(stage=><StageItem key={stage.id} applicationId={applicationId} stage={stage}/>)}</ol>:<p className={styles.muted}>등록된 전형 단계가 없습니다.</p>}<div className={local.addSection}><button type="button" className={`${styles.button} ${styles.buttonSecondary}`} onClick={()=>setAdding(value=>!value)} aria-expanded={adding}>{adding?"추가 취소":"전형 단계 추가"}</button>{adding&&<form action={submit} className={local.form}><Field label="전형 유형" htmlFor="new-stage-type"><Select id="new-stage-type" name="stageType" required defaultValue=""><option value="" disabled>선택해주세요</option>{stageTypes.map(value=><option key={value} value={value}>{stageTypeLabel[value]}</option>)}</Select></Field><Field label="단계명 (선택)" htmlFor="new-stage-label"><TextInput id="new-stage-label" name="label" maxLength={100}/></Field><Field label="예정 일시 (선택)" htmlFor="new-stage-scheduled"><TextInput id="new-stage-scheduled" name="scheduledAt" type="datetime-local"/></Field><Field label="메모 (선택)" htmlFor="new-stage-memo"><Textarea id="new-stage-memo" name="memo" maxLength={1000}/></Field><FormActions><SubmitButton>단계 추가</SubmitButton></FormActions></form>}<ActionNotice result={result}/></div></>
}
