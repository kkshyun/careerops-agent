import type { ApplicationStatus,NotificationStatus } from "./types";
export const formatDate=(value:string|null,withTime=false)=>value?new Intl.DateTimeFormat("ko-KR",withTime?{dateStyle:"medium",timeStyle:"short"}:{dateStyle:"medium"}).format(new Date(value)):"—";
export const applicationLabel:Record<ApplicationStatus,string>={INTERESTED:"관심",PLANNED:"지원 예정",SUBMITTED:"지원 완료",OFFERED:"최종 합격",REJECTED:"불합격",WITHDRAWN:"지원 철회"};
export const notificationLabel:Record<NotificationStatus,string>={PENDING:"대기",SENDING:"처리 중",SENT:"완료",FAILED:"실패"};
export const stageTypeLabel={DOCUMENT:"서류",CODING_TEST:"코딩테스트",WRITTEN:"필기",INTERVIEW:"면접",FINAL:"최종",OTHER:"기타"} as const;
export const stageResultLabel={PENDING:"대기",PASSED:"합격",FAILED:"불합격",CANCELLED:"취소"} as const;
export const scoreLabel=(score:number)=>score.toFixed(2);
export const isClosingSoon=(date:string|null)=>{if(!date)return false;const today=new Date(Date.now());today.setHours(0,0,0,0);const target=new Date(`${date}T00:00:00`);const days=(target.getTime()-today.getTime())/86400000;return days>=0&&days<=7};
