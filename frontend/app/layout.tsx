import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "각 (Gak) — 축구 팀 진단·예측",
  description:
    "리그·컵·유럽대항전을 가로질러 팀의 실제 일정을 통합하고, 일정 밀집도로 부진 원인을 진단하며, 다음 경기를 예측하고 적중률을 추적한다.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko" suppressHydrationWarning>
      <head>
        {/* 한글 본문용 Pretendard. 디자인 확정 시 폰트 전략은 재검토한다. */}
        <link
          rel="stylesheet"
          href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable.min.css"
        />
      </head>
      <body>{children}</body>
    </html>
  );
}
