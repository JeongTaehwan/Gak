// 스캐폴딩 확인용 최소 페이지. 실제 화면은 디자인 확정본으로 다음 단계에서 구현한다.
// UI는 @usetaehwan/ui 컴포넌트와 색·간격 토큰만 재사용한다(하드코딩 금지).
export default function Home() {
  return (
    <main className="mx-auto flex min-h-dvh max-w-2xl flex-col justify-center gap-4 px-6">
      <h1 className="text-4xl font-bold tracking-tight">각 (Gak)</h1>
      <p className="text-lg opacity-80">
        축구 팀 진단·예측 웹앱 — 스캐폴딩 완료. 화면은 다음 단계에서 구현합니다.
      </p>
    </main>
  );
}
