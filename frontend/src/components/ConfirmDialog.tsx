"use client";

import {forwardRef,useImperativeHandle,useRef,useState} from "react";
import styles from "./ConfirmDialog.module.css";

export type ConfirmDialogHandle={open:()=>void};
type ConfirmDialogProps={title:string;description?:string;confirmLabel?:string;cancelLabel?:string;onConfirm:()=>void|Promise<void>};

const ConfirmDialog=forwardRef<ConfirmDialogHandle,ConfirmDialogProps>(function ConfirmDialog({title,description,confirmLabel="삭제",cancelLabel="취소",onConfirm},ref){
  const dialogRef=useRef<HTMLDialogElement>(null);
  const restoreFocusRef=useRef<HTMLElement|null>(null);
  const [isPending,setIsPending]=useState(false);
  useImperativeHandle(ref,()=>({open(){restoreFocusRef.current=document.activeElement as HTMLElement;dialogRef.current?.showModal()}}),[]);
  const confirm=async()=>{setIsPending(true);try{await onConfirm();dialogRef.current?.close()}catch{return}finally{setIsPending(false)}};
  return <dialog ref={dialogRef} className={styles.dialog} onClick={event=>{if(event.target===dialogRef.current)dialogRef.current?.close()}} onClose={()=>restoreFocusRef.current?.focus()}><div className={styles.content}><h2>{title}</h2>{description&&<p>{description}</p>}<div className={styles.actions}><button type="button" className={styles.cancel} autoFocus disabled={isPending} onClick={()=>dialogRef.current?.close()}>{cancelLabel}</button><button type="button" className={styles.confirm} disabled={isPending} onClick={confirm}>{isPending?"처리 중…":confirmLabel}</button></div></div></dialog>
});
export default ConfirmDialog;
