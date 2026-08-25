import Link from "next/link";import type {ApplicationStatus,NotificationStatus} from "@/lib/types";import styles from "./UI.module.css";
export function PageHeading({title,description,action}:{title:string;description:string;action?:React.ReactNode}){return <header className={styles.pageHeading}><div><h1>{title}</h1><p>{description}</p></div>{action}</header>}
export function SectionHeading({title,href}:{title:string;href?:string}){return <div className={styles.sectionHeading}><h2>{title}</h2>{href&&<Link href={href}>전체 보기</Link>}</div>}
export function Badge({children,tone="neutral"}:{children:React.ReactNode;tone?:"neutral"|"success"|"warning"|"danger"}){return <span className={`${styles.badge} ${styles[tone]}`}>{children}</span>}
export function RailRow({children,tone="neutral",href}:{children:React.ReactNode;tone?:string;href?:string}){const body=<div className={`${styles.railRow} ${styles[`rail_${tone}`]??""}`}>{children}</div>;return href?<Link className={styles.rowLink} href={href}>{body}</Link>:body}
export const appTone=(s:ApplicationStatus)=>s==="OFFERED"?"success":s==="REJECTED"?"danger":s==="SUBMITTED"||s==="PLANNED"?"accent":"neutral";
export const noteTone=(s:NotificationStatus)=>s==="SENT"?"success":s==="FAILED"?"danger":s==="SENDING"?"accent":"warning";
export {styles as uiStyles};
