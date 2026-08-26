"use client";
import type {ActionResult} from "@/lib/actions/types";
import styles from "./ActionNotice.module.css";
export function ActionNotice({result}:{result:ActionResult<unknown>|null}){if(!result||result.ok)return null;return <p role={result.kind==="error"?"alert":"status"} className={`${styles.notice} ${result.kind==="error"?styles.error:""}`}>{result.message}</p>}
