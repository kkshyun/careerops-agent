"use client";

import {useFormStatus} from "react-dom";
import styles from "./Form.module.css";

export function Field({label,htmlFor,error,children}:{label:string;htmlFor:string;error?:React.ReactNode;children:React.ReactNode}){return <div className={styles.field}><label htmlFor={htmlFor}>{label}</label>{children}{error&&<FieldError>{error}</FieldError>}</div>}
export function FieldError({children}:{children:React.ReactNode}){return <p className={styles.error}>{children}</p>}
export const TextInput=(props:React.InputHTMLAttributes<HTMLInputElement>)=><input {...props} className={`${styles.input} ${props.className??""}`}/>;
export const Textarea=(props:React.TextareaHTMLAttributes<HTMLTextAreaElement>)=><textarea {...props} className={`${styles.input} ${styles.textarea} ${props.className??""}`}/>;
export const Select=(props:React.SelectHTMLAttributes<HTMLSelectElement>)=><select {...props} className={`${styles.input} ${props.className??""}`}/>;
export function FormActions({children}:{children:React.ReactNode}){return <div className={styles.actions}>{children}</div>}
export function SubmitButton({children,pendingLabel="저장 중…",...props}:React.ButtonHTMLAttributes<HTMLButtonElement>&{pendingLabel?:string}){const {pending}=useFormStatus();return <button {...props} type="submit" className={`${styles.submit} ${props.className??""}`} disabled={pending||props.disabled}>{pending?pendingLabel:children}</button>}
