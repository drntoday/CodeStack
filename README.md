# Code Stack Control Panel

A Next.js app that authenticates via GitHub OAuth and lets you chat with Gemini directly in the browser.

## Features

- **GitHub OAuth Authentication** - Sign in with your GitHub account using NextAuth.js
- **Gemini Chat** - Chat with Google's Gemini 2.0 Flash model
- **Protected Dashboard** - Only authenticated users can access the chat interface
- **Free Tier Ready** - Built to work within Vercel Hobby and Gemini free tier limits

## Getting Started

### Prerequisites

1. A GitHub account (for OAuth)
2. A Google AI Studio API key (free at https://aistudio.google.com/app/apikey)

### Environment Variables

Copy `.env.local.example` to `.env.local` and fill in your credentials:

```bash
cp .env.local.example .env.local
```

Then edit `.env.local`:

- `CODESTACK_CLIENT_ID` - Create an OAuth App at https://github.com/settings/developers
- `CODESTACK_CLIENT_SECRET` - From your GitHub OAuth App
- `GEMINI_API_KEY` - Get from https://aistudio.google.com/app/apikey
- `NEXTAUTH_SECRET` - Generate with `openssl rand -base64 32`
- `NEXTAUTH_URL` - Your app URL (use `http://localhost:3000` for local dev)

### Installation

```bash
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) to see the app.

## Deployment on Vercel

1. Push this repo to GitHub
2. Import the project on Vercel
3. Add environment variables in Vercel settings:
   - `CODESTACK_CLIENT_ID`
   - `CODESTACK_CLIENT_SECRET`
   - `GEMINI_API_KEY`
   - `NEXTAUTH_SECRET`
4. Set the callback URL in your GitHub OAuth App to `https://your-app.vercel.app/api/auth/callback/github`

## Project Structure

```
src/app/
├── api/
│   ├── auth/[...nextauth]/route.ts  # NextAuth API route
│   └── chat/route.ts                # Gemini chat API
├── dashboard/page.tsx               # Protected chat dashboard
├── layout.tsx                       # Root layout with SessionProvider
└── page.tsx                         # Landing page with sign-in button
```

## Rate Limiting

The chat API includes basic rate limiting checks. For production use, implement proper rate limiting using a store like Redis or Vercel KV.

## License

MIT
