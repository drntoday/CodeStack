# Code Stack

Your free AI coding partner – ask questions, refactor entire codebases, generate tests, and automate DevOps, all from the browser.

## Features

- **AI-Powered Code Assistance** - Get help with coding questions and code refactoring
- **Test Generation** - Automatically generate tests for your codebase
- **DevOps Automation** - Automate CI/CD pipelines and deployment processes
- **GitHub Integration** - Seamless integration with GitHub repositories
- **Architecture Analysis** - Analyze and understand code architecture
- **Code Audit** - Perform security and quality audits on your code
- **Documentation Generation** - Auto-generate documentation for your projects

## Tech Stack

- **Framework**: [Next.js 16](https://nextjs.org/)
- **Language**: [TypeScript](https://www.typescriptlang.org/)
- **Styling**: [Tailwind CSS 4](https://tailwindcss.com/)
- **Authentication**: [NextAuth.js](https://next-auth.js.org/) (GitHub OAuth)
- **AI SDKs**:
  - [Groq SDK](https://groq.com/) - Fast AI inference
  - [Octokit REST](https://octokit.github.io/rest.js/) - GitHub API client
- **Storage**: [Vercel KV](https://vercel.com/docs/storage/vercel-kv)

## Getting Started

### Prerequisites

- Node.js 20+ 
- npm or yarn
- GitHub account for authentication
- API keys for Groq and other services

### Installation

1. Clone the repository:
```bash
git clone https://github.com/yourusername/code-stack.git
cd code-stack
```

2. Install dependencies:
```bash
npm install
```

3. Set up environment variables:
Create a `.env.local` file in the root directory with the following variables:
```env
GROQ_API_KEY=your_groq_api_key
CODESTACK_GITHUB_ID=your_github_client_id
CODESTACK_GITHUB_SECRET=your_github_client_secret
AUTH_SECRET=your_nextauth_secret
NEXTAUTH_URL=http://localhost:3000
VERCEL_KV_URL=your_vercel_kv_url
HEALER_TOKEN=your_github_healer_token
WEBHOOK_SECRET=your_webhook_secret
```

4. Run the development server:
```bash
npm run dev
```

5. Open [http://localhost:3000](http://localhost:3000) in your browser

## Available Scripts

| Command | Description |
|---------|-------------|
| `npm run dev` | Start development server |
| `npm run build` | Build for production |
| `npm run start` | Start production server |
| `npm run lint` | Run ESLint |

## Project Structure

```
src/
├── app/                  # Next.js App Router
│   ├── api/              # API routes
│   │   ├── architecture/ # Architecture analysis endpoints
│   │   ├── audit/        # Code audit endpoints
│   │   ├── auth/         # Authentication endpoints
│   │   ├── chat/         # Chat/conversation endpoints
│   │   ├── ci/           # CI/CD endpoints
│   │   ├── deploy/       # Deployment endpoints
│   │   ├── docs/         # Documentation endpoints
│   │   ├── git/          # Git operations endpoints
│   │   ├── github/       # GitHub integration endpoints
│   │   ├── orchestrator/ # Task orchestration endpoints
│   │   ├── refactor/     # Code refactoring endpoints
│   │   ├── search/       # Code search endpoints
│   │   ├── tests/        # Test generation endpoints
│   │   ├── tools/        # Utility tool endpoints
│   │   └── webhooks/     # Webhook handlers
│   ├── dashboard/        # Dashboard pages
│   ├── globals.css       # Global styles
│   ├── layout.tsx        # Root layout
│   └── page.tsx          # Home page
├── components/           # React components
├── lib/                  # Utility libraries
├── types/                # TypeScript type definitions
└── auth.ts               # Authentication configuration
```

## API Endpoints

The application provides various API endpoints for different functionalities:

- `POST /api/chat` - Send messages to the AI assistant
- `POST /api/refactor` - Request code refactoring
- `POST /api/tests` - Generate tests for code
- `POST /api/architecture` - Analyze code architecture
- `POST /api/audit` - Perform code audit
- `POST /api/deploy` - Trigger deployments
- `POST /api/github/*` - GitHub integration operations

## Authentication

Code Stack uses GitHub OAuth for authentication via NextAuth.js. Users can sign in with their GitHub account to access all features.

## Self‑Healing Setup

Code Stack includes a self-healing feature that can automatically fix issues in your repository. To enable this:

1. **Create a GitHub Fine-Grained Personal Access Token:**
   - Go to https://github.com/settings/tokens
   - Click "Generate new token" → "Generate fine-grained token"
   - Select the repository you want to manage
   - Grant the following permissions:
     - **Contents**: Read and write
     - **Pull requests**: Read and write
     - **Actions**: Read
   - Generate the token and copy it

2. **Create a Webhook Secret:**
   - Generate a random secret string (e.g., using `openssl rand -base64 32`)

3. **Configure Environment Variables in Vercel:**
   - Go to your Vercel project settings
   - Navigate to "Environment Variables"
   - Add the following:
     - `HEALER_TOKEN`: Your GitHub fine-grained personal access token
     - `WEBHOOK_SECRET`: Your webhook secret string

4. **Configure GitHub Webhook:**
   - Go to your repository settings on GitHub
   - Navigate to "Webhooks" → "Add webhook"
   - Set Payload URL to: `https://<your-vercel-app>.vercel.app/api/webhooks/workflow-failed`
   - Set Content type to: `application/json`
   - Set Secret to the same value as `WEBHOOK_SECRET`
   - Select "Let me select individual events" → Choose "Workflow runs"
   - Save the webhook

## Deployment

The easiest way to deploy this application is using the [Vercel Platform](https://vercel.com/new).

[![Deploy with Vercel](https://vercel.com/button)](https://vercel.com/new/clone?repository-url=https://github.com/yourusername/code-stack)

### Deployment Setup

To use the automated GitHub Actions deployment workflow (`.github/workflows/deploy.yml`), you need to configure the following secrets in your GitHub repository:

1. **Create a Vercel token**: Go to https://vercel.com/account/tokens and generate a new API token.
2. **Get your Project ID and Org ID**: In your Vercel project settings, find the Project ID and Organization ID.
3. **Add GitHub Secrets**: In your GitHub repository, go to Settings → Secrets and variables → Actions and add:
   - `VERCEL_TOKEN`: Your Vercel API token
   - `VERCEL_PROJECT_ID`: Your Vercel project ID
   - `VERCEL_ORG_ID`: Your Vercel organization ID

Once configured, you can trigger a production deployment by going to Actions → Deploy → Run workflow.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- [Next.js](https://nextjs.org/) team for the amazing framework
- [Groq](https://groq.com/) for fast AI inference
- [Vercel](https://vercel.com/) for hosting and storage solutions
