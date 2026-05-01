import { queryGroq } from "./groq";
import { generateUnifiedDiff, formatUnifiedDiff, FileDiff } from "./diff";

/**
 * Core action functions for Code Stack AI
 * These functions encapsulate all Groq queries and GitHub API interactions
 * They are used by both the orchestrator and individual API routes
 */

// ==================== CHAT ====================
export async function generateChatResponse(
  messages: Array<{ role: string; content: string }>,
  repoContext?: { owner: string; repo: string; files?: string[] }
): Promise<string> {
  const systemPromptParts = ["You are an expert coding assistant. Answer helpfully and concisely."];
  
  // Add repository file context if available - include up to 200 files
  if (repoContext?.files && repoContext.files.length > 0) {
    const fileListSummary = repoContext.files.length <= 200 
      ? repoContext.files.join("\n")
      : repoContext.files.slice(0, 200).join("\n") + `\n... and ${repoContext.files.length - 200} more files`;
    systemPromptParts.push(`\n\nThe user is working with the repository ${repoContext.owner}/${repoContext.repo} which contains these files:\n${fileListSummary}`);
  } else if (repoContext?.owner && repoContext?.repo) {
    // System-level guardrail: No file list available
    systemPromptParts.push("\n\nYou do NOT have access to the repository's file structure. If the user's question requires knowing the files, tell them to load the repository. Do NOT invent any file names.");
  }
  
  const systemPrompt = {
    role: "system",
    content: systemPromptParts.join(""),
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
  files: string[]
): Promise<Array<{ file: string; instruction: string; reason: string }>> {
  // Return error if files list is empty - do not call LLM
  if (files.length === 0) {
    throw new Error("No file list available. Do NOT invent any file names. If you cannot answer without the file list, say so clearly.");
  }

  // Include up to 200 files in the prompt
  const filesToUse = files.length <= 200 ? files : files.slice(0, 200);
  const planPrompt = `Repository files:\n${filesToUse.join("\n")}\n\nTask: ${prompt}\n\nProduce a JSON array of objects with "file", "instruction", and "reason". Only JSON.`;
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
  accessToken: string,
  mode: "workflow" | "direct" = "workflow"
): Promise<{ success: boolean; message?: string; error?: string; filesCommitted?: number; totalFiles?: number }> {
  // If mode is "direct", skip workflow dispatch and go straight to direct commits
  if (mode === "direct") {
    return await executeRefactorDirect(owner, repo, plan, accessToken);
  }

  // Try workflow dispatch first
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
  
  // If workflow dispatch fails (e.g., refactor.yml doesn't exist), fallback to direct commits
  if (!dispatchRes.ok) {
    const errText = await dispatchRes.text();
    console.log("Workflow dispatch failed, falling back to direct commits:", errText);
    return await executeRefactorDirect(owner, repo, plan, accessToken);
  }
  
  return { success: true, message: "Refactor workflow triggered. Check Actions tab." };
}

// Helper function to execute refactoring via direct commits
export async function executeRefactorDirect(
  owner: string,
  repo: string,
  plan: Array<{ file: string; instruction: string }>,
  accessToken: string
): Promise<{ success: boolean; message?: string; error?: string; filesCommitted?: number; totalFiles?: number }> {
  let filesCommitted = 0;
  const totalFiles = plan.length;
  
  for (const step of plan) {
    try {
      // Get current file SHA if it exists
      let sha: string | undefined;
      const fileMetaRes = await fetch(
        `https://api.github.com/repos/${owner}/${repo}/contents/${step.file}`,
        {
          headers: {
            Authorization: `token ${accessToken}`,
            Accept: "application/vnd.github.v3+json",
          },
        }
      );
      
      if (fileMetaRes.ok) {
        const fileMeta = await fileMetaRes.json();
        sha = fileMeta.sha;
      }
      
      // Generate new content based on instruction using Groq
      let oldContent = "";
      if (sha) {
        // Fetch existing content
        const contentRes = await fetch(
          `https://api.github.com/repos/${owner}/${repo}/contents/${step.file}`,
          {
            headers: {
              Authorization: `token ${accessToken}`,
              Accept: "application/vnd.github.v3+json",
            },
          }
        );
        if (contentRes.ok) {
          const contentData = await contentRes.json();
          oldContent = Buffer.from(contentData.content, "base64").toString("utf-8");
        }
      }
      
      // Use Groq to generate new content based on instruction
      const { queryGroq } = await import("./groq");
      const prompt = `You are an expert code editor. Apply the following instruction to the given file content.
File: ${step.file}
Instruction: ${step.instruction}

Current content:
\`\`\`
${oldContent}
\`\`\`

Return ONLY the complete updated file content. No explanations, no markdown code blocks.`;
      
      const newContent = await queryGroq("refactor", [{ role: "user", content: prompt }]);
      
      // Generate diff for preview before committing
      const fileDiff = generateUnifiedDiff(oldContent, newContent, {
        oldPath: step.file,
        newPath: step.file,
      });

      // Commit the changes
      const commitRes = await fetch(
        `https://api.github.com/repos/${owner}/${repo}/contents/${step.file}`,
        {
          method: "PUT",
          headers: {
            Authorization: `token ${accessToken}`,
            Accept: "application/vnd.github.v3+json",
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: `AI Refactor: ${step.file}`,
            content: Buffer.from(newContent).toString("base64"),
            branch: "main",
            ...(sha ? { sha } : {}),
          }),
        }
      );
      
      if (!commitRes.ok) {
        const errData = await commitRes.json();
        throw new Error(errData.message || "Commit failed");
      }
      
      filesCommitted++;
    } catch (error: any) {
      console.error(`Failed to commit ${step.file}:`, error);
      return { 
        success: false, 
        error: `Failed to commit ${step.file}: ${error.message}`,
        filesCommitted,
        totalFiles
      };
    }
  }
  
  return { 
    success: true, 
    message: `Successfully committed ${filesCommitted}/${totalFiles} files directly to main.`,
    filesCommitted,
    totalFiles
  };
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
  accessToken: string,
  files: string[]
): Promise<string> {
  // Return error if files list is empty - do not call LLM
  if (files.length === 0) {
    return "No file list available. Do NOT invent any file names. If you cannot answer without the file list, say so clearly.";
  }

  // Include up to 200 files
  const filesToUse = files.length <= 200 ? files : files.slice(0, 200);
  const fileList = filesToUse.join("\n");

  const prompt = `You are an expert software architect. The repository at ${owner}/${repo} has the following files:\n${fileList}\n\nBased on this file list, answer the user's question: "${question}". If the repository is empty or has only a placeholder file, state that clearly and do not invent content.`;

  const { queryGroq } = await import("@/lib/groq");
  return queryGroq("architecture", [{ role: "user", content: prompt }]);
}

// ==================== DOCS ====================
export async function generateReadme(
  owner: string,
  repo: string,
  accessToken: string,
  files: string[]
): Promise<string> {
  // Return error if files list is empty - do not call LLM
  if (files.length === 0) {
    return "No file list available. Do NOT invent any file names. If you cannot answer without the file list, say so clearly.";
  }

  // Include up to 200 files
  const filesToUse = files.length <= 200 ? files : files.slice(0, 200);
  const fileList = filesToUse.join("\n");

  const prompt = `You are a technical writer. The repository at ${owner}/${repo} contains these files:\n${fileList}\n\nGenerate a professional README.md based on this file structure. If the repository appears to be empty or has only a placeholder like "empty.keep", state that clearly and do not invent content.`;
  
  const { queryGroq } = await import("@/lib/groq");
  return queryGroq("docs", [{ role: "user", content: prompt }]);
}

export async function generateOpenApi(
  owner: string,
  repo: string,
  accessToken: string,
  files: string[]
): Promise<string> {
  // Return error if files list is empty - do not call LLM
  if (files.length === 0) {
    return "No file list available. Do NOT invent any file names. If you cannot answer without the file list, say so clearly.";
  }

  // Include up to 200 files
  const filesToUse = files.length <= 200 ? files : files.slice(0, 200);
  const fileList = filesToUse.join("\n");

  const prompt = `You are an API documentation expert. The repository at ${owner}/${repo} contains these files:\n${fileList}\n\nGenerate an OpenAPI 3.0 specification (YAML) if API routes exist. If no API-related files are present, state that there is no API to document. Do not invent endpoints.`;
  
  const { queryGroq } = await import("@/lib/groq");
  return queryGroq("docs", [{ role: "user", content: prompt }]);
}

// ==================== SEARCH ====================
export async function searchCode(
  owner: string,
  repo: string,
  query: string,
  accessToken: string,
  files: string[]
): Promise<string[]> {
  // Return empty array if files list is empty - do not call LLM
  if (files.length === 0) {
    return [];
  }

  // Simple keyword matching: find files whose path or name contains the query
  const lowerQuery = query.toLowerCase();
  const matched = files.filter((file: string) => file.toLowerCase().includes(lowerQuery));
  
  if (matched.length === 0) {
    // If direct match fails, ask Groq for semantic suggestions (optional but helpful)
    // Include up to 200 files in the prompt
    const filesToUse = files.length <= 200 ? files : files.slice(0, 200);
    const prompt = `The repository ${owner}/${repo} contains these files:\n${filesToUse.join("\n")}\n\nThe user searched for: "${query}". Which files are most relevant? Return ONLY a JSON array of file paths, e.g., ["src/auth.ts"]. If nothing matches, return empty array [].`;
    
    const { queryGroq } = await import("@/lib/groq");
    const groqResponse = await queryGroq("search", [{ role: "user", content: prompt }]);
    try {
      const parsed = JSON.parse(groqResponse);
      if (Array.isArray(parsed)) return parsed.filter((f: string) => files.includes(f));
    } catch {
      // ignore parsing errors
    }
    return [];
  }

  return matched;
}

// ==================== GREETING ====================
export async function generateGreeting(
  owner: string,
  repo: string,
  files: string[],
  accessToken: string
): Promise<{ greeting: string; suggestions: string[] }> {
  // Check for empty repository with only placeholder file
  const isOnlyPlaceholder = files.length === 1 && files[0] === "empty.keep";
  
  if (isOnlyPlaceholder) {
    return {
      greeting: `This repository contains only empty.keep. There is no project structure to explain.`,
      suggestions: [
        "Add your first source file",
        "Initialize a new project",
        "Clone an existing project here",
      ],
    };
  }

  // Build file tree summary for context - include up to 200 files
  const fileListSummary = files.length > 0
    ? files.slice(0, 200).join("\n")
    : "No files found";

  // Add guardrail instruction when no files are available
  const guardrailInstruction = files.length === 0
    ? "\n\nNote: This repository appears to be empty. Do NOT invent any file names or project details. Provide a generic welcome message and suggest the user add code to the repository."
    : "";

  const prompt = `You are Code Stack, an AI-first coding assistant. A user has just loaded the repository ${owner}/${repo}.

The repository contains these files (showing up to 200):
${fileListSummary}${guardrailInstruction}

Based on the file structure, provide:
1. A friendly, concise greeting (1-2 sentences) that identifies the project type (e.g., "I see this is a Next.js project with an auth system")
2. Three intelligent, context-aware suggestions for what the user might want to do next

Respond with ONLY valid JSON in this format:
{
  "greeting": "Your greeting here",
  "suggestions": ["Suggestion 1", "Suggestion 2", "Suggestion 3"]
}`;

  const { queryGroq } = await import("./groq");
  const responseText = await queryGroq("chat", [{ role: "user", content: prompt }]);

  try {
    const parsed = JSON.parse(responseText);
    return {
      greeting: parsed.greeting || `Loaded ${owner}/${repo}. How can I help?`,
      suggestions: Array.isArray(parsed.suggestions) ? parsed.suggestions : [
        "Explain the project structure",
        "Search for authentication logic",
        "Generate a README",
      ],
    };
  } catch {
    return {
      greeting: `Loaded ${owner}/${repo}. How can I help?`,
      suggestions: [
        "Explain the project structure",
        "Search for authentication logic",
        "Generate a README",
      ],
    };
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
