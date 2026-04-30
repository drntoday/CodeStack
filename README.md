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

- `CODESTACK_GITHUB_ID` - Create an OAuth App at https://github.com/settings/developers
- `CODESTACK_GITHUB_SECRET` - From your GitHub OAuth App
- `GEMINI_API_KEY` - Get from https://aistudio.google.com/app/apikey
- `AUTH_SECRET` - Generate with `openssl rand -base64 32`
- `HEALER_TOKEN` - Fine-grained GitHub PAT for self-healing (see Self-Healing Setup below)
- `WEBHOOK_SECRET` - Random string for webhook signature validation (see Self-Healing Setup below)

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
   - `CODESTACK_GITHUB_ID`
   - `CODESTACK_GITHUB_SECRET`
   - `GEMINI_API_KEY`
   - `AUTH_SECRET`
   - `HEALER_TOKEN` (for self-healing)
   - `WEBHOOK_SECRET` (for webhook validation)
4. Set the callback URL in your GitHub OAuth App to `https://your-app.vercel.app/api/auth/callback/github`

## Self-Healing Setup

To enable automatic CI failure detection and fix suggestions:

1. **Create a GitHub fine-grained personal access token:**
   - Go to https://github.com/settings/tokens
   - Create a new fine-grained token with the following permissions:
     - Contents: read/write
     - Pull requests: read/write
     - Actions: read
   - Store this token as `HEALER_TOKEN` in Vercel environment variables

2. **Configure the webhook in your GitHub repository:**
   - Go to your GitHub repo → Settings → Webhooks → Add webhook
   - Payload URL: `https://your-vercel-app.vercel.app/api/webhooks/workflow-failed`
   - Content type: `application/json`
   - Secret: Enter a random string (this will be your `WEBHOOK_SECRET`)
   - Events: Select "Workflow runs" only
   - Save the webhook

3. **Add the same secret to Vercel:**
   - In Vercel settings, add `WEBHOOK_SECRET` with the same value you used in the webhook

When a workflow fails, GitHub will notify your app, which will analyze the logs and create a PR with a proposed fix.

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
