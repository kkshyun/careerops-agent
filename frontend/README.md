# CareerOps Frontend

## 로컬 개발

Node.js와 npm이 설치된 환경에서 다음 명령을 실행합니다.

```bash
npm install
npm run dev
```

브라우저에서 [http://localhost:3000](http://localhost:3000)을 엽니다.

백엔드 API 연결이 필요할 때 `.env.local.example`을 `.env.local`로 복사하고
`NEXT_PUBLIC_API_BASE_URL` 값을 설정합니다. 현재 랜딩 페이지는 API를 호출하지
않습니다.

## Vercel 배포

Vercel에서 저장소를 연결할 때 다음 값을 사용합니다.

- Framework Preset: `Next.js`
- Root Directory: `frontend`
- Build Command: 기본값 (`npm run build`)
- Output Directory: 기본값 (직접 지정 불필요)
- Install Command: 기본값 (`npm install`)
- Environment Variables: `NEXT_PUBLIC_API_BASE_URL` — 백엔드 API base URL이
  확정되면 설정합니다.
