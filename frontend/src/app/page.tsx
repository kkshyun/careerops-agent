import Link from "next/link";

export default function Home() {
  return (
    <main className="landing">
      <section className="hero" aria-labelledby="service-title">
        <p className="eyebrow">나에게 맞는 채용의 시작</p>
        <h1 id="service-title">CareerOps</h1>
        <p className="subtitle">
          채용 공고를 모으고, 내 경험과 맞는 공고를 추천해주는 서비스
        </p>
        <Link className="cta" href="/dashboard">
          시작하기
        </Link>
      </section>
    </main>
  );
}
