import Link from "next/link";
import { PageHeading, RailRow, SectionHeading, appTone, noteTone, Badge } from "@/components/ui";
import { applicationStatuses, getApplications } from "@/lib/api/applications";
import { getJobs } from "@/lib/api/jobs";
import { getNotifications } from "@/lib/api/notifications";
import { applicationLabel, formatDate, notificationLabel, scoreLabel } from "@/lib/format";
import styles from "../App.module.css";
import dash from "./page.module.css";
export const dynamic = "force-dynamic";
export default async function Dashboard(){
 const statusPromise=Promise.all(applicationStatuses.map(status=>getApplications({status,size:1})));
 const [open,statusPages,pending,recentApps,recentNotes]=await Promise.all([getJobs({status:"OPEN",size:5}),statusPromise,getNotifications({status:"PENDING",size:20}),getApplications({size:5}),getNotifications({size:5})]);
 const active=statusPages.slice(0,3).reduce((sum,page)=>sum+page.totalElements,0), nearest=open.content[0];
 const recommended=[...pending.content].sort((a,b)=>b.recommendationScore-a.recommendationScore).slice(0,4);
 return <><PageHeading title="Dashboard" description="공고 유입부터 전형 진행까지, 지금 확인할 항목을 모았습니다."/><section className={dash.priority}><p>가장 가까운 마감</p>{nearest?<Link href={`/jobs/${nearest.id}`}><strong>{nearest.companyName} · {nearest.title}</strong><span className={styles.mono}>{formatDate(nearest.applicationEndAt)}</span></Link>:<span>다가오는 마감이 없습니다.</span>}</section><div className={styles.summaryStrip}><div className={styles.summaryItem}><span>OPEN 채용공고</span><strong>{open.totalElements}</strong></div><div className={styles.summaryItem}><span>관심·지원 중</span><strong>{active}</strong></div><div className={styles.summaryItem}><span>PENDING 알림</span><strong>{pending.totalElements}</strong></div></div><SectionHeading title="추천 공고" href="/notifications"/><p className={dash.caption}>추천 관련도는 지원자 PKB와의 상대적 연관도이며 결과를 예측하지 않습니다.</p><div className={dash.recommendations}>{recommended.map(n=><Link key={n.id} href={`/jobs/${n.jobId}`} className={dash.recommendation}><span className={styles.mono}>관련도 {scoreLabel(n.recommendationScore)}/1.0</span><strong>{n.companyName}</strong><p>{n.title}</p><small>{n.reason}</small></Link>)}</div><div className={dash.columns}><section><SectionHeading title="지원 현황" href="/applications"/>{recentApps.content.map(a=><RailRow key={a.id} href={`/applications/${a.id}`} tone={appTone(a.status)}><div className={dash.row}><div className={styles.rowTitle}><strong>{a.companyName}</strong><span>{a.title}</span></div><Badge>{applicationLabel[a.status]}</Badge></div></RailRow>)}</section><section><SectionHeading title="최근 알림" href="/notifications"/>{recentNotes.content.map(n=><RailRow key={n.id} tone={noteTone(n.status)} href={`/jobs/${n.jobId}`}><div className={dash.row}><div className={styles.rowTitle}><strong>{n.companyName}</strong><span>{formatDate(n.createdAt,true)}</span></div><Badge>{notificationLabel[n.status]}</Badge></div></RailRow>)}</section></div></>;
}
