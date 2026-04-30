import { queryGroq } from "./groq";

/**
 * Core action functions for Code Stack AI
 * These functions encapsulate all Groq queries and GitHub API interactions
 * They are used by both the orchestrator and individual API routes
 */

// ==================== CHAT ====================
export async function generateChatResponse(messages: Array<{ role: string; content: string }>): Promise<string> {
  const systemPrompt = {
    role: "system",
    content: "You are an expert coding assistant. Answer helpfully and concisely.",
  };
  const allMessages = [systemPrompt, ...messages];
  return await queryGroq("chat", allMessages);
}

// ==================== REFACTOR ====================
export async function generateRefactorPlan(
  owner: string,
  repo: string,
  prompt: string,
  accessToken: string,
  files?: string[]
): Promise<Array<{ file: string; instruction: string; reason: string }>> {
  // Fetch tree if not provided
  if (!files) {
    const treeRes = await fetch(
      `https://api.github.com/repos/${owner}/${repo}/git/trees/main?recursive=1`,
      {
        headers: {
          Authorization: `token ${accessToken}`,
          Accept: "application/vnd.github.v3+json",
        },
      }
    );
    if (!treeRes.ok) throw new Error("Cannot fetch tree");
    const treeData = await treeRes.json();
    files = treeData.tree
      .filter((item: any) => item.type === "blob" && !item.path.includes("node_modules"))
      .map((item: any) => item.path);
  }

  const planPrompt = `Repository files:\n${(files ?? []).join("\n")}\n\nTask: ${prompt}\n\nProduce a JSON array of objects with "file", "instruction", and "reason". Only JSON.`;
  const responseText = await queryGroq("refactor", [
    { role: "user", content: planPrompt },
  ]);

  try {
    return JSON.parse(responseText);
  } catch {
    throw new Error("Invalid plan generated");
  }
}

export async function executeRefactor(
  owner: string,
  repo: string,
  plan: Array<{ file: string; instruction: string }>,
  accessToken: string
): Promise<{ success: boolean; message?: string; error?: string }> {
  const dispatchRes = await fetch(
    `https://api.github.com/repos/${owner}/${repo}/actions/workflows/refactor.yml/dispatches`,
    {
      method: "POST",
      headers: {
        Authorization: `token ${accessToken}`,
        Accept: "application/vnd.github.v3+json",
      },
      body: JSON.stringify({
        ref: "main",
        inputs: {
          plan: JSON.stringify(plan),
          owner,
          repo,
          base_branch: "main",
        },
      }),
    }
  );
  if (!dispatchRes.ok) {
    const errText = await dispatchRes.text();
    return { success: false, error: `Dispatch failed: ${errText}` };
  }
  return { success: true, message: "Refactor workflow triggered. Check Actions tab." };
}

// ==================== TESTS ====================
export async function generateTests(
  fileContent: string,
  filePath: string
): Promise<string> {
  const prompt = `You are an expert tester. Write a comprehensive test suite for the following code. Include edge cases and aim for 90%+ branch coverage. Use Jest for JavaScript/TypeScript or PyTest for Python.
File: ${filePath}
Code:
\`\`\`
${fileContent}
\`\`\`
Provide ONLY the test file content inside a single code block. No explanations.`;

  const text = await queryGroq("tests", [{ role: "user", content: prompt }]);
  const match = text.match(/```[\s\S]*?\n([\s\S]*?)\n```/);
  return match ? match[1] : text;
}

// ==================== AUDIT ====================
export async function auditCommit(
  owner: string,
  repo: string,
  commitSha: string,
  accessToken: string
): Promise<string> {
  const diffRes = await fetch(
    `https://api.github.com/repos/${owner}/${repo}/commits/${commitSha}`,
    {
      headers: {
        Authorization: `token ${accessToken}`,
        Accept: "application/vnd.github.v3.diff",
      },
    }
  );
  if (!diffRes.ok) throw new Error("Commit not found");
  const diffText = await diffRes.text();

  const prompt = `Analyze this diff for security issues (secrets, API keys, tokens, SQL injection, XSS). Use a bullet list: [- severity] file:line - description. If clean, say "No issues found."
Diff:
\`\`\`diff
${diffText}
\`\`\``;

  return await queryGroq("audit", [{ role: "user", content: prompt }]);
}

// ==================== ARCHITECTURE ====================
export async function answerArchitecture(
  owner: string,
  repo: string,
  question: string,
  accessToken: string
): Promise<string> {
  const treeRes = await fetch(
    `https://api.github.com/repos/${owner}/${repo}/git/trees/main?recursive=1`,
    {
      headers: { Authorization: `token ${accessToken}` },
    }
  );
  if (!treeRes.ok) throw new Error("Cannot fetch repo tree");
  const treeData = await treeRes.json();
  const files = treeData.tree
    .filter((item: any) => item.type === "blob" && /\.(ts|js|py|go|java|rb)$/i.test(item.path))
    .map((item: any) => item.path);

  const prompt = `You are an expert software architect. Below is a list of repository files. Answer the user question by tracing the relevant imports/exports based on file paths alone. Provide a detailed, high-level explanation with file paths.

Repository files:
${files.slice(0, 30).join("\n")}

Question: ${question}`;

  return await queryGroq("architecture", [{ role: "user", content: prompt }]);
}

// ==================== DOCS ====================
export async function generateReadme(
  owner: string,
  repo: string,
  accessToken: string
): Promise<string> {
  const files = ["package.json", "tsconfig.json", "eslint.config.mjs", "src/app/layout.tsx", "src/app/api/chat/route.ts", "src/auth.ts"];
  let collected = "";
  for (const file of files) {
    const res = await fetch(`https://api.github.com/repos/${owner}/${repo}/contents/${file}`, {
      headers: { Authorization: `token ${accessToken}` },
    });
    if (res.ok) {
      const data = await res.json();
      if (data.content) {
        collected += `=== ${file} ===\n${Buffer.from(data.content, "base64").toString("utf-8")}\n\n`;
      }
    }
  }

  const prompt = `You are a technical writer. Given the following files from a Next.js project, produce a thorough README.md. Include:
- Project title and description
- Features
- Prerequisites
- Installation
- Environment variables
- Usage (how to run, build, deploy)
- Project structure overview
Use proper Markdown formatting.

${collected}`;

  return await queryGroq("docs", [{ role: "user", content: prompt }]);
}

export async function generateOpenApi(
  owner: string,
  repo: string,
  accessToken: string
): Promise<string> {
  const routes = [
    "src/app/api/chat/route.ts",
    "src/app/api/github/file/route.ts",
    "src/app/api/github/tree/route.ts",
    "src/app/api/github/commit/route.ts",
    "src/app/api/github/pr/route.ts",
    "src/app/api/refactor/plan/route.ts",
    "src/app/api/refactor/execute/route.ts",
    "src/app/api/tests/generate/route.ts",
    "src/app/api/audit/route.ts",
    "src/app/api/architecture/ask/route.ts",
    "src/app/api/docs/generate-readme/route.ts",
    "src/app/api/webhooks/workflow-failed/route.ts"
  ];
  let routeCode = "";
  for (const route of routes) {
    const res = await fetch(`https://api.github.com/repos/${owner}/${repo}/contents/${route}`, {
      headers: { Authorization: `token ${accessToken}` },
    });
    if (res.ok) {
      const data = await res.json();
      if (data.content) {
        routeCode += `=== ${route} ===\n${Buffer.from(data.content, "base64").toString("utf-8")}\n\n`;
      }
    }
  }

  const prompt = `Analyze the following Next.js API route handlers and generate an OpenAPI 3.0 specification (YAML) for all endpoints. Include request bodies, query parameters, and responses. Use sensible summaries.

${routeCode}`;

  return await queryGroq("docs", [{ role: "user", content: prompt }]);
}

// ==================== SEARCH ====================
async function buildIndex(owner: string, repo: string, token: string): Promise<Array<{ path: string; summary: string }>> {
  const treeRes = await fetch(`https://api.github.com/repos/${owner}/${repo}/git/trees/main?recursive=1`, {
    headers: { Authorization: `token ${token}` },
  });
  const treeData = await treeRes.json();
  const codeFiles = treeData.tree.filter((item: any) => item.type === "blob" && /\.(ts|js|tsx|jsx|py)$/i.test(item.path)).map((item: any) => item.path);

  const index: { path: string; summary: string }[] = [];
  let totalChars = 0;
  for (const file of codeFiles.slice(0, 50)) {
    if (totalChars > 3000) break;
    
    const res = await fetch(`https://api.github.com/repos/${owner}/${repo}/contents/${file}`, {
      headers: { Authorization: `token ${token}` },
    });
    if (res.ok) {
      const data = await res.json();
      const content = data.content ? Buffer.from(data.content, "base64").toString("utf-8") : "";
      const lines = content.split("\n");
      const important = lines.filter(line => /^\s*(export\s+)?(async\s+)?function|class\s+\w+|const\s+\w+\s*=\s*(async\s*)?\(/.test(line) || line.trim().startsWith("//") || line.trim().startsWith("/*")).join("\n").slice(0, 600);
      index.push({ path: file, summary: important });
      totalChars += important.length;
    }
  }
  return index;
}

export async function searchCode(
  owner: string,
  repo: string,
  query: string,
  accessToken: string
): Promise<string[]> {
  const index = await buildIndex(owner, repo, accessToken);

  const prompt = `You are given a codebase index (file paths and key declarations). Answer the question: "${query}". Output a JSON array of relevant file paths, e.g., ["src/auth.ts", "src/login.ts"]. Only JSON, no other text.

Index:
${JSON.stringify(index, null, 2)}`;

  const text = await queryGroq("search", [{ role: "user", content: prompt }]);
  try {
    return JSON.parse(text);
  } catch {
    return [];
  }
}

// ==================== COMMIT MESSAGE ====================
export async function generateCommitMessage(
  oldContent: string,
  newContent: string,
  filePath: string,
  description?: string
): Promise<string> {
  const diffBlock = `Old:\n\`\`\`\n${oldContent}\n\`\`\`\nNew:\n\`\`\`\n${newContent}\n\`\`\``;

  const prompt = `You are an expert git commit message writer. Write a concise, meaningful commit message (first line < 72 chars, optionally followed by a blank line and more description) that describes the change below${description ? `. The user described the change as: "${description}"` : ""}.

File: ${filePath || "unknown"}
Change (diff):
${diffBlock}

Return ONLY the commit message, no other text.`;

  return await queryGroq("commitMessage", [{ role: "user", content: prompt }]);
}

// ==================== CI/CD ANALYSIS ====================
export async function analyzeCICD(
  owner: string,
  repo: string,
  runId: string,
  accessToken: string
): Promise<string> {
  const logsRes = await fetch(
    `https://api.github.com/repos/${owner}/${repo}/actions/runs/${runId}/logs`,
    { headers: { Authorization: `token ${accessToken}` } }
  );
  if (!logsRes.ok) throw new Error("Cannot fetch logs");
  const logText = await logsRes.text();
  const logSnippet = logText.slice(0, 5000);

  const runRes = await fetch(
    `https://api.github.com/repos/${owner}/${repo}/actions/runs/${runId}`,
    { headers: { Authorization: `token ${accessToken}` } }
  );
  const runData = runRes.ok ? await runRes.json() : null;

  const prompt = `You are a DevOps expert. A GitHub Actions workflow has failed. Here are the details:
- Run ID: ${runId}
- Status: ${runData?.conclusion || "unknown"}
- Log snippet (first 5000 chars):
\`\`\`
${logSnippet}
\`\`\`
Analyze:
1. Why did it fail? (explain in plain English)
2. How can we fix it?
3. Any tips to speed up the CI pipeline (caching, parallelism, etc.)?
Format your answer in clear bullet points.`;

  return await queryGroq("ci", [{ role: "user", content: prompt }]);
}

// ==================== DEPLOYMENT ====================
export async function triggerDeploy(
  owner: string,
  repo: string,
  accessToken: string,
  workflowId?: string,
  ref?: string
): Promise<{ success: boolean; message?: string; error?: string }> {
  const url = workflowId
    ? `https://api.github.com/repos/${owner}/${repo}/actions/workflows/${workflowId}/dispatches`
    : `https://api.github.com/repos/${owner}/${repo}/actions/workflows/deploy.yml/dispatches`;

  const dispatchRes = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `token ${accessToken}`,
      Accept: "application/vnd.github.v3+json",
    },
    body: JSON.stringify({ ref: ref || "main" }),
  });

  if (!dispatchRes.ok) {
    const errText = await dispatchRes.text();
    return { success: false, error: `Dispatch failed: ${errText}` };
  }
  return { success: true, message: "Deployment triggered." };
}

// ==================== WEBHOOK TRIGGER ====================
export async function triggerWebhook(
  url: string,
  payload: Record<string, any>
): Promise<{ status: number; body: string }> {
  try {
    const webhookRes = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload || {}),
    });
    const status = webhookRes.status;
    const body = await webhookRes.text();
    return { status, body };
  } catch {
    throw new Error("Webhook call failed");
  }
}

// ==================== CREATE BRANCH & PR ====================
export async function createBranchAndPR(
  owner: string,
  repo: string,
  path: string,
  content: string,
  message: string,
  baseBranch: string,
  prTitle: string,
  prBody: string,
  accessToken: string
): Promise<{ success: boolean; prUrl?: string; error?: string }> {
  const branchName = `ai-update-${Date.now()}`;

  // Get current file SHA if exists
  let sha: string | undefined;
  const fileRes = await fetch(
    `https://api.github.com/repos/${owner}/${repo}/contents/${path}`,
    {
      headers: {
        Authorization: `token ${accessToken}`,
        Accept: "application/vnd.github.v3+json",
      },
    }
  );
  if (fileRes.ok) {
    const fileData = await fileRes.json();
    sha = fileData.sha;
  }

  // Create commit on new branch
  const commitBody: any = {
    message,
    content: Buffer.from(content).toString("base64"),
    branch: branchName,
  };
  if (sha) commitBody.sha = sha;

  const commitRes = await fetch(
    `https://api.github.com/repos/${owner}/${repo}/contents/${path}`,
    {
      method: "PUT",
      headers: {
        Authorization: `token ${accessToken}`,
        Accept: "application/vnd.github.v3+json",
      },
      body: JSON.stringify(commitBody),
    }
  );

  if (!commitRes.ok) {
    const errData = await commitRes.json();
    return { success: false, error: errData.message || "Failed to commit" };
  }

  // Create PR
  const prRes = await fetch(
    `https://api.github.com/repos/${owner}/${repo}/pulls`,
    {
      method: "POST",
      headers: {
        Authorization: `token ${accessToken}`,
        Accept: "application/vnd.github.v3+json",
      },
      body: JSON.stringify({
        title: prTitle,
        head: branchName,
        base: baseBranch,
        body: prBody,
      }),
    }
  );

  if (!prRes.ok) {
    const errData = await prRes.json();
    return { success: false, error: errData.message || "Failed to create PR" };
  }

  const prData = await prRes.json();
  return { success: true, prUrl: prData.html_url };
}
