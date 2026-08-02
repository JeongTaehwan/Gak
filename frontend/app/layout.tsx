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
        {/* 본문: Pretendard (한글) / 숫자·헤드라인: Archivo 900 (시안 폰트 규칙) */}
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link
          rel="preconnect"
          href="https://fonts.gstatic.com"
          crossOrigin="anonymous"
        />
        <link
          rel="stylesheet"
          href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable.min.css"
        />
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Archivo:wght@500;700;800;900&display=swap"
        />
      </head>
      <body>{children}</body>
    </html>
  );
}
