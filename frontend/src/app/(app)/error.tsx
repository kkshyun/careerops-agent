"use client";

import Link from "next/link";
import styles from "./App.module.css";

export default function AppError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <section className={styles.panel} role="alert">
      <h1>데이터를 불러오지 못했습니다</h1>
      <p className={styles.muted}>Backend 연결 상태를 확인한 뒤 다시 시도해 주세요.</p>
      <div className={styles.stack}>
        <button className={styles.button} type="button" onClick={reset}>다시 시도</button>
        <Link className={`${styles.button} ${styles.buttonSecondary}`} href="/dashboard">Dashboard로 이동</Link>
      </div>
    </section>
  );
}
