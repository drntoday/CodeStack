import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { queryGroq } from "@/lib/groq";
import * as actions from "@/lib/actions";
import { withTimeout } from "@/lib/timeout";
import { rateLimit } from "@/lib/rate-limit";

/**
 * Orchestrator Route - AI-first intent detection and execution
 * Receives user message + context, classifies intent, and executes appropriate action
 */

const ORCHESTRATOR_SYSTEM_PROMPT = `You are Code Stack, a world‑class Full Stack Developer, AI Specialist, and autonomous coding partner. You live inside a powerful development platform that can read, write, test, document, and deploy code directly on GitHub. You are not a generic chatbot — you are an agent that takes real, concrete actions in the user's codebase.

YOUR IDENTITY
- You are a tireless, expert pair‑programmer who can handle everything from a one‑line bug fix to a complete production‑ready application.
- You think end‑to‑end, considering architecture, performance, testing, documentation, and deployment in every answer.
- You work at startup speed but with enterprise‑grade quality.

YOUR CAPABILITIES
You have direct, authenticated access to the user's GitHub repository. You can:
- Read the repository file tree and individual file contents
- Modify existing files or create brand‑new ones
- Commit changes with meaningful messages and open pull requests
- Generate comprehensive unit tests, integration tests, and CI/CD configurations
- Perform security audits on specific commits
- Explain the project architecture and data flow by analysing imports and exports
- Search the codebase for specific logic, functions, or patterns
- Generate README.md, OpenAPI specs, and other documentation from the actual code
- Trigger deployment workflows and analyse CI/CD failures

ABSOLUTE RULES – YOU MUST FOLLOW THESE WITHOUT EXCEPTION
1. NEVER, under any circumstances, suggest manual shell commands like "git status", "npm init", "npm install", "create‑next‑app", "mkdir", or similar. You must perform all work by creating files and committing them through the platform.
2. When a user asks to start a new project or build a feature, IMMEDIATELY create a concrete plan of files and present it as a refactoring plan. Do not merely describe what needs to be done — produce the actual files.
3. Always use the repository context you receive. If the file list is available, reference real file paths. If file contents are provided, use them to inform your code changes. Never invent a project structure when the real one is available.
4. Keep the entire conversation history in mind. If the user previously asked for a Next.js project and now asks for authentication, you already know the stack and can build on it.
5. If a repository is empty or contains only a placeholder file (e.g., "empty.keep", a bare README), treat it as a blank canvas and start scaffolding immediately when the user describes what they want.
6. For any action that modifies code, you MUST set requiresApproval to false for low‑risk operations (single‑file edits, tests, docs) and true for high‑impact ones (bulk refactoring, deployment). This ensures fast working without dangerous surprises.
7. Your classification must be accurate. If you are unsure, fall back to "chat" and ask clarifying questions.

HOW YOU WORK – THE AI‑FIRST DEVELOPMENT LOOP
When a user sends a message, you must decide which action best fulfills the request. Here is every action and exactly when to use it:

- **chat**: Use for general programming questions, explanations, and planning that does not require code changes. Even in chat mode, you should give concrete, actionable advice based on the real repository state.
- **refactor**: Use when the request involves creating, modifying, or deleting multiple files. This includes scaffolding a new project, adding a feature that touches several files, updating deprecated APIs, or performing a large‑scale cleanup. Return a JSON plan with a "file", "instruction", and "reason" for each file. The user can then approve and execute the plan.
- **commit**: Use for a single‑file change (create or update). Provide the file path, the new content, and a commit message.
- **test**: Use when the user asks to generate unit tests or integration tests for a specific file. Use Jest for JavaScript/TypeScript and PyTest for Python.
- **docs**: Use when the user asks for README.md, OpenAPI specifications, or any other documentation. You will read the actual codebase first and then generate the documentation.
- **search**: Use when the user asks "Where is …?" or "Find the logic for …". You will return the most relevant file paths.
- **architecture**: Use when the user asks "Explain the project structure", "How does the data flow from X to Y?", or "What is the architecture of this project?".
- **audit**: Use when the user gives a commit SHA and asks for a security audit. You will fetch the diff and analyse it.
- **deploy**: Use when the user wants to trigger a deployment. Set requiresApproval to true.
- **ci**: Use when the user provides a workflow run ID and asks why it failed or how to optimise it.
- **repo_loaded**: This is an internal action used when a repository is first loaded. You must generate a friendly, personalised greeting that analyses the existing project files and suggests the most logical next steps. Do not use this for any user message.

RESPONSE FORMAT
You must respond with ONLY a valid JSON object. No other text, no markdown, no explanations. The JSON object must follow this exact shape:

{
  "action": "<action_name>",
  "confidence": <0.0 to 1.0>,
  "parameters": {
    // Action‑specific parameters (see below)
  },
  "requiresApproval": true or false,
  "message": "<a short, friendly explanation of what you're doing>"
}

Parameter requirements for each action:
- chat: { messages: [{ role: "user" | "assistant", content: "..." }] }
- refactor: { owner: "...", repo: "...", prompt: "..." (the user's full request), deepContext: false (set to true if the user wants a deep refactor with file contents) }
- commit: { owner: "...", repo: "...", path: "...", content: "the full new file content", message: "commit message", branch: "main" }
- test: { fileContent: "...", filePath: "..." }
- audit: { owner: "...", repo: "...", commitSha: "..." }
- architecture: { owner: "...", repo: "...", question: "..." }
- docs: { owner: "...", repo: "...", type: "readme" or "openapi" }
- search: { owner: "...", repo: "...", query: "..." }
- deploy: { owner: "...", repo: "..." }
- ci: { owner: "...", repo: "...", runId: "..." }

EXAMPLES OF CORRECT BEHAVIOUR
User: "Build a Next.js blog with Tailwind" (repo is empty)
Classification: { "action": "refactor", "confidence": 0.97, "parameters": { "owner": "user", "repo": "blog", "prompt": "Build a Next.js blog with Tailwind" }, "requiresApproval": false, "message": "I'm scaffolding a full Next.js blog with Tailwind CSS. Here's the plan." }

User: "Add a dark mode toggle to the dashboard sidebar"
Classification: { "action": "refactor", "confidence": 0.92, "parameters": { ... }, "requiresApproval": false, "message": "I'll modify the dashboard layout and add a dark mode toggle." }

User: "Where is password reset handled?"
Classification: { "action": "search", "confidence": 0.95, "parameters": { "query": "password reset" }, "requiresApproval": false, "message": "Searching for password reset logic..." }

User: "Explain how authentication works in this project"
Classification: { "action": "architecture", "confidence": 0.94, "parameters": { "question": "Explain how authentication works in this project" }, "requiresApproval": false, "message": "Analysing the authentication flow..." }

User: "Run git status" or "npm install"
Classification: { "action": "chat", "confidence": 0.75, "parameters": { "messages": [...] }, "requiresApproval": false, "message": "I can't run shell commands, but I can read and modify files directly. What do you need?" }

When the user says "Start building this project now" and the repository is empty, you MUST return a refactor plan that creates the full project scaffold. Do not reply with a chat message – move directly to building.

TONE & STYLE
- Be concise, practical, and helpful.
- Celebrate when things go well (emoji are fine in the message field).
- When something goes wrong, be empathetic and offer a solution.
- Never be vague. Every answer must move the project forward concretely.`;

export async function POST(req: NextRequest) {
  const session = await auth();
  
  // Rate limiting: 15 requests per minute for orchestrator (main entry point)
  const identifier = `${session?.user?.email || "anonymous"}::${new Date().getMinutes()}`;
  if (!(await rateLimit(identifier, 15, 60000))) {
    return NextResponse.json({ error: "Too many requests. Please wait a moment." }, { status: 429 });
  }
  
  try {
    const { message, repoContext, selectedFile, fileContent, messages = [] } = await req.json();
    
    if (!message) {
      return NextResponse.json({ error: "Message is required" }, { status: 400 });
    }

    // Build context for classification
    const contextInfo = [];
    if (repoContext?.owner && repoContext?.repo) {
      contextInfo.push(`Repository: ${repoContext.owner}/${repoContext.repo}`);
    }
    if (selectedFile) {
      contextInfo.push(`Selected file: ${selectedFile}`);
    }
    if (fileContent) {
      contextInfo.push(`File content preview: ${fileContent.slice(0, 500)}...`);
    }
    if (repoContext?.files?.length) {
      contextInfo.push(`Repo files: ${repoContext.files.slice(0, 200).join("\n")}`);
    }

    // Build a short history snippet for classification context
    const historySnippet = messages.slice(-6, -1)  // last 5 previous messages, excluding the current one
      .map((m: any) => `${m.role}: ${m.content.slice(0, 200)}`)
      .join("\n");

    // Step 1: Classify the intent with timeout
    const classificationPrompt = `Conversation history (recent):
${historySnippet || "(none)"}

Current user message: "${message}"

${contextInfo.length > 0 ? "Context:\n" + contextInfo.join("\n") : ""}

Classify this request and respond with ONLY valid JSON.`;

    let classificationResponse: string;
    try {
      classificationResponse = await withTimeout(
        queryGroq("chat", [
          { role: "system", content: ORCHESTRATOR_SYSTEM_PROMPT },
          { role: "user", content: classificationPrompt },
        ]),
        7000,
        "Classification timed out"
      );
    } catch (timeoutError: any) {
      console.warn("Classification timeout, falling back to chat:", timeoutError.message);
      // Fallback to chat on timeout
      classificationResponse = JSON.stringify({
        action: "chat",
        confidence: 0.5,
        parameters: { messages: [...messages, { role: "user", content: message }] },
        requiresApproval: false,
        message: "I'll help you with that.",
      });
    }

    // Parse the classification
    let classification: any;
    try {
      // Try to extract JSON from the response
      const jsonMatch = classificationResponse.match(/\{[\s\S]*\}/);
      classification = jsonMatch ? JSON.parse(jsonMatch[0]) : JSON.parse(classificationResponse);
    } catch {
      // Fallback to chat if parsing fails
      classification = {
        action: "chat",
        confidence: 0.8,
        parameters: { messages: [...messages, { role: "user", content: message }] },
        requiresApproval: false,
        message: "I'll help you with that.",
      };
    }

    const { action, parameters, requiresApproval = false } = classification;
    const accessToken = session?.accessToken;

    // Handle actions that don't require auth
    if (action === "chat" && !accessToken) {
      let response: string;
      try {
        response = await withTimeout(
          actions.generateChatResponse(
            [...messages, { role: "user", content: message }],
            repoContext || undefined
          ),
          8000,
          "Chat response timed out"
        );
      } catch (timeoutError: any) {
        console.warn("Chat response timeout:", timeoutError.message);
        response = "I'm sorry, the response took too long. Please try again.";
      }
      // Smart suggestions with shorter timeout, non-blocking
      let smartSuggestions: string[] = [];
      try {
        smartSuggestions = await withTimeout(
          actions.generateSmartSuggestions(messages, message, response),
          5000,
          "Suggestions timed out"
        );
      } catch (timeoutError: any) {
        console.warn("Smart suggestions timeout:", timeoutError.message);
        // Return empty suggestions on timeout
      }
      return NextResponse.json({
        action: "chat",
        result: { text: response },
        message: response,
        suggestions: smartSuggestions.length > 0 ? smartSuggestions : [],
      });
    }

    // Handle repo_loaded special action - AI generates greeting
    if (action === "repo_loaded") {
      if (!accessToken || !repoContext?.owner || !repoContext?.repo) {
        return NextResponse.json({ error: "Repository not loaded" }, { status: 400 });
      }
      const { greeting, suggestions } = await actions.generateGreeting(
        repoContext.owner,
        repoContext.repo,
        repoContext.files || [],
        accessToken
      );
      return NextResponse.json({
        action: "repo_loaded",
        result: { text: greeting },
        message: greeting,
        suggestions,
      });
    }

    // Execute the classified action
    let result: any;
    let executionMessage = classification.message || `Executing ${action}...`;

    switch (action) {
      case "chat": {
        const chatMessages = [...messages, { role: "user", content: message }];
        if (selectedFile && fileContent) {
          chatMessages.push({ 
            role: "system", 
            content: `Current file context (${selectedFile}):\n${fileContent}` 
          });
        }
        let response: string;
        try {
          response = await withTimeout(
            actions.generateChatResponse(chatMessages, repoContext || undefined),
            8000,
            "Chat response timed out"
          );
        } catch (timeoutError: any) {
          console.warn("Chat action timeout:", timeoutError.message);
          response = "I'm sorry, the response took too long. Please try again.";
        }
        result = { text: response };
        break;
      }

      case "refactor": {
        if (!accessToken || !repoContext?.owner || !repoContext?.repo) {
          return NextResponse.json({ 
            error: "Repository not loaded. Please load a repository first.",
            action: "refactor",
            requiresApproval: false,
          }, { status: 400 });
        }
        let plan: any;
        try {
          plan = await withTimeout(
            actions.generateRefactorPlan(
              repoContext.owner,
              repoContext.repo,
              parameters?.prompt || message,
              accessToken,
              repoContext.files,
              parameters?.deepContext || false
            ),
            8000,
            "Refactor plan timed out"
          );
        } catch (timeoutError: any) {
          console.warn("Refactor plan timeout:", timeoutError.message);
          return NextResponse.json({
            error: "Refactoring plan took too long. Please try again.",
            action: "refactor",
            requiresApproval: false,
          }, { status: 504 });
        }
        result = { plan };
        executionMessage = "I've generated a refactoring plan. Should I proceed?";
        break;
      }

      case "test": {
        if (!fileContent) {
          return NextResponse.json({ 
            error: "No file selected. Please select a file to generate tests for.",
            action: "test",
            requiresApproval: false,
          }, { status: 400 });
        }
        let testContent: string;
        try {
          testContent = await withTimeout(
            actions.generateTests(fileContent, selectedFile || "unknown"),
            8000,
            "Test generation timed out"
          );
        } catch (timeoutError: any) {
          console.warn("Test generation timeout:", timeoutError.message);
          return NextResponse.json({
            error: "Test generation took too long. Please try again.",
            action: "test",
            requiresApproval: false,
          }, { status: 504 });
        }
        result = { testContent, filePath: selectedFile };
        executionMessage = "Tests generated! Would you like to apply them?";
        break;
      }

      case "audit": {
        if (!accessToken || !repoContext?.owner || !repoContext?.repo) {
          return NextResponse.json({ error: "Repository not loaded" }, { status: 400 });
        }
        const commitSha = parameters?.commitSha || extractCommitSha(message);
        if (!commitSha) {
          return NextResponse.json({ error: "Commit SHA required for audit" }, { status: 400 });
        }
        let report: string;
        try {
          report = await withTimeout(
            actions.auditCommit(repoContext.owner, repoContext.repo, commitSha, accessToken),
            8000,
            "Audit timed out"
          );
        } catch (timeoutError: any) {
          console.warn("Audit timeout:", timeoutError.message);
          return NextResponse.json({
            error: "Security audit took too long. Please try again.",
            action: "audit",
            requiresApproval: false,
          }, { status: 504 });
        }
        result = { report };
        break;
      }

      case "architecture": {
        if (!accessToken || !repoContext?.owner || !repoContext?.repo) {
          return NextResponse.json({ error: "Repository not loaded" }, { status: 400 });
        }
        let answer: string;
        try {
          answer = await withTimeout(
            actions.answerArchitecture(
              repoContext.owner,
              repoContext.repo,
              parameters?.question || message,
              accessToken,
              repoContext.files
            ),
            8000,
            "Architecture query timed out"
          );
        } catch (timeoutError: any) {
          console.warn("Architecture query timeout:", timeoutError.message);
          return NextResponse.json({
            error: "Architecture analysis took too long. Please try again.",
            action: "architecture",
            requiresApproval: false,
          }, { status: 504 });
        }
        result = { text: answer };
        break;
      }

      case "docs": {
        if (!accessToken || !repoContext?.owner || !repoContext?.repo) {
          return NextResponse.json({ error: "Repository not loaded" }, { status: 400 });
        }
        const docType = parameters?.type || (message.toLowerCase().includes("readme") ? "readme" : "openapi");
        let docResult: string;
        try {
          if (docType === "readme") {
            docResult = await withTimeout(
              actions.generateReadme(repoContext.owner, repoContext.repo, accessToken, repoContext.files || []),
              8000,
              "README generation timed out"
            );
            result = { readme: docResult };
          } else {
            docResult = await withTimeout(
              actions.generateOpenApi(repoContext.owner, repoContext.repo, accessToken, repoContext.files || []),
              8000,
              "OpenAPI generation timed out"
            );
            result = { openapi: docResult };
          }
        } catch (timeoutError: any) {
          console.warn("Docs generation timeout:", timeoutError.message);
          return NextResponse.json({
            error: `Documentation generation took too long. Please try again.`,
            action: "docs",
            requiresApproval: false,
          }, { status: 504 });
        }
        break;
      }

      case "search": {
        if (!accessToken || !repoContext?.owner || !repoContext?.repo) {
          return NextResponse.json({ error: "Repository not loaded" }, { status: 400 });
        }
        let files: string[];
        try {
          files = await withTimeout(
            actions.searchCode(
              repoContext.owner,
              repoContext.repo,
              parameters?.query || message,
              accessToken,
              repoContext.files || []
            ),
            8000,
            "Search timed out"
          );
        } catch (timeoutError: any) {
          console.warn("Search timeout:", timeoutError.message);
          files = []; // Return empty results on timeout
        }
        result = { files };
        break;
      }

      case "commit": {
        if (!accessToken || !repoContext?.owner || !repoContext?.repo || !selectedFile) {
          return NextResponse.json({ error: "Repository and file must be loaded for commit" }, { status: 400 });
        }
        // For commit, we need the new content - either from message or generate it
        const newContent = parameters?.content || fileContent; // In real scenario, AI would generate this
        let commitMsg: string;
        try {
          commitMsg = parameters?.message || await withTimeout(
            actions.generateCommitMessage("", newContent, selectedFile, message),
            8000,
            "Commit message generation timed out"
          );
        } catch (timeoutError: any) {
          console.warn("Commit message timeout:", timeoutError.message);
          commitMsg = "AI-generated changes";
        }
        
        // Return the commit details for approval
        result = { 
          path: selectedFile, 
          content: newContent, 
          message: commitMsg,
          branch: parameters?.branch || "main"
        };
        executionMessage = `Ready to commit to ${result.branch}. Proceed?`;
        break;
      }

      case "pr": {
        if (!accessToken || !repoContext?.owner || !repoContext?.repo) {
          return NextResponse.json({ error: "Repository not loaded" }, { status: 400 });
        }
        // PR creation requires prior commit - this would be handled in follow-up
        result = { 
          head: parameters?.head || `ai-update-${Date.now()}`,
          base: parameters?.base || "main",
          title: parameters?.title || "AI-generated changes",
          body: parameters?.body || "Changes proposed by Code Stack AI."
        };
        executionMessage = "Ready to create PR. Proceed?";
        break;
      }

      case "deploy": {
        if (!accessToken || !repoContext?.owner || !repoContext?.repo) {
          return NextResponse.json({ error: "Repository not loaded" }, { status: 400 });
        }
        // Return deployment info for approval
        result = { owner: repoContext.owner, repo: repoContext.repo };
        executionMessage = "Ready to trigger deployment from main. Proceed?";
        break;
      }

      case "ci": {
        if (!accessToken || !repoContext?.owner || !repoContext?.repo) {
          return NextResponse.json({ error: "Repository not loaded" }, { status: 400 });
        }
        const runId = parameters?.runId || extractRunId(message);
        if (!runId) {
          return NextResponse.json({ error: "Workflow run ID required" }, { status: 400 });
        }
        let analysis: string;
        try {
          analysis = await withTimeout(
            actions.analyzeCICD(repoContext.owner, repoContext.repo, runId, accessToken),
            8000,
            "CI/CD analysis timed out"
          );
        } catch (timeoutError: any) {
          console.warn("CI/CD analysis timeout:", timeoutError.message);
          return NextResponse.json({
            error: "CI/CD analysis took too long. Please try again.",
            action: "ci",
            requiresApproval: false,
          }, { status: 504 });
        }
        result = { analysis };
        break;
      }

      default:
        // Fallback to chat with repository file context
        let response: string;
        try {
          response = await withTimeout(
            actions.generateChatResponse(
              [...messages, { role: "user", content: message }],
              repoContext || undefined
            ),
            8000,
            "Fallback chat timed out"
          );
        } catch (timeoutError: any) {
          console.warn("Fallback chat timeout:", timeoutError.message);
          response = "I'm sorry, the response took too long. Please try again.";
        }
        result = { text: response };
        classification.action = "chat";
    }

    // Build assistant text for suggestions
    let assistantText = executionMessage;
    if (result?.text) assistantText = result.text;
    else if (result?.report) assistantText = result.report;
    else if (result?.analysis) assistantText = result.analysis;
    else if (result?.readme) assistantText = result.readme;
    else if (result?.openapi) assistantText = result.openapi;
    else if (result?.testContent) assistantText = result.testContent;
    else if (result?.files) assistantText = "Found files: " + result.files.join(", ");
    else if (result?.plan) assistantText = "Refactoring plan generated.";
    
    // Smart suggestions with shorter timeout, non-blocking (returns empty array on timeout)
    let smartSuggestions: string[] = [];
    try {
      smartSuggestions = await withTimeout(
        actions.generateSmartSuggestions(messages, message, assistantText),
        5000,
        "Suggestions timed out"
      );
    } catch (timeoutError: any) {
      console.warn("Smart suggestions timeout:", timeoutError.message);
      // Return empty suggestions on timeout - will use fallback below
    }

    return NextResponse.json({
      action: classification.action,
      result,
      requiresApproval,
      message: executionMessage,
      suggestions: smartSuggestions.length > 0 ? smartSuggestions : generateFallbackSuggestions(classification.action),
    });

  } catch (error: any) {
    console.error("Orchestrator fatal error:", error);
    if (error?.status === 429) {
      return NextResponse.json({ error: "Rate limit exceeded. Please wait a moment.", rateLimited: true }, { status: 429 });
    }
    return NextResponse.json(
      { error: typeof error === "string" ? error : (error.message || "Internal server error") },
      { status: 500 }
    );
  }
}

// Helper: Extract commit SHA from message
function extractCommitSha(message: string): string | null {
  const shaRegex = /\b[a-f0-9]{7,40}\b/;
  const match = message.match(shaRegex);
  return match ? match[0] : null;
}

// Helper: Extract run ID from message
function extractRunId(message: string): string | null {
  const runIdRegex = /\b\d{9,}\b/;
  const match = message.match(runIdRegex);
  return match ? match[0] : null;
}

// Fallback suggestions when AI generation fails
function generateFallbackSuggestions(action: string): string[] {
  const fallbacks: Record<string, string[]> = {
    refactor: ["Execute the plan", "Deep refactor with file contents"],
    test: ["Apply test file", "Run tests now"],
    docs: ["Save README.md", "Save OpenAPI spec"],
    deploy: ["Yes, deploy now"],
    commit: ["Commit the changes", "Create a PR"],
    pr: ["Create the PR"],
    ci: ["How do I fix this?"],
    search: ["Open the most relevant file"],
  };
  return fallbacks[action] || [];
}
