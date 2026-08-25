import type { Metadata } from "next";
import { IBM_Plex_Mono, IBM_Plex_Sans_KR } from "next/font/google";
import "./globals.css";

const sans=IBM_Plex_Sans_KR({weight:["400","500","600","700"],subsets:["latin"],variable:"--font-sans",display:"swap"});
const mono=IBM_Plex_Mono({weight:["400","500"],subsets:["latin"],variable:"--font-mono",display:"swap"});

export const metadata: Metadata = {
  title: "CareerOps",
  description: "내 경험에 맞는 채용 공고를 추천하는 서비스",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko" className={`${sans.variable} ${mono.variable}`}>
      <body>{children}</body>
    </html>
  );
}
