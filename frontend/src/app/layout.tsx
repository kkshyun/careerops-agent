import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "CareerOps",
  description: "내 경험에 맞는 채용 공고를 추천하는 서비스",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
