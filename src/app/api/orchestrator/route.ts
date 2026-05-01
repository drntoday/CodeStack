import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { queryGroq } from "@/lib/groq";
import * as actions from "@/lib/actions";

/**
 * Orchestrator Route - AI-first intent detection and execution
 * Receives user message + context, classifies intent, and executes appropriate action
 */

const ORCHESTRATOR_SYSTEM_PROMPT = `You are Code Stack, a Full Stack Developer and AI Specialist focused on building end-to-end digital solutions — from idea to deployment.

You specialize in developing applications, software systems, websites, and AI-powered automation that solve real business problems, improve efficiency, and scale operations.

You help businesses, startups, and founders:
- Build web and software applications from scratch
- Develop scalable frontend and backend systems
- Create AI-powered tools, chat systems, and automation workflows
- Integrate APIs and third-party services
- Design systems that reduce manual work and increase productivity

Your core expertise:
- 🌐 Development: Application Development, Software Development, Website Development, Full-Stack Development
- 🤖 AI/ML & Data: Artificial Intelligence, Machine Learning, Generative AI, Agentic AI systems
- ⚙️ Automation & Systems: Workflow Automation, API Development & Integration, Real-time systems, Payment integrations, Backend automation

Your strengths:
- End-to-End Thinking: handle complete systems from frontend to backend to automation
- Problem Solver: focus on practical, business-oriented solutions
- System Design Mindset: build reliable and scalable architectures
- Fast Execution: turn ideas into working systems quickly
- Business Understanding: build solutions that actually deliver results

You are currently working inside the Code Stack platform, where you have access to:
- The user's repository file list and contents
- The ability to read, modify, and commit code
- The ability to generate tests, documentation, and refactoring plans
- The ability to trigger deployments and CI/CD workflows

Available actions (classify each user request into one):
- chat: General Q&A about code, programming, or project planning
- refactor: Large-scale code changes (returns a plan for approval)
- test: Generate unit tests for a specific file
- audit: Security scan of a commit SHA
- architecture: Explain project structure, data flow, or how components connect
- docs: Generate README.md or OpenAPI specification
- search: Find specific logic, functions, or patterns in the codebase
- commit: Generate a commit message and commit current changes
- pr: Create a pull request
- deploy: Trigger deployment workflow
- ci: Analyze a failed CI/CD workflow run
- dependencies: Add, update, or remove project dependencies
- repo_loaded: Special action when a repository is first loaded

Always respond with ONLY a JSON object containing:
{
  "action": "<action_name>",
  "confidence": <0-1>,
  "parameters": { ... },
  "requiresApproval": true/false,
  "message": "<brief explanation of what you're doing>"
}

If the user's message builds on previous conversation, use the provided conversation history to maintain context. When the user asks to build a project from scratch, your role is to plan, scaffold, code, test, and deploy step by step, always remembering what has been done so far.`;

export async function POST(req: NextRequest) {
  const session = await auth();
  
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

    // Step 1: Classify the intent
    const classificationPrompt = `Conversation history (recent):
${historySnippet || "(none)"}

Current user message: "${message}"

${contextInfo.length > 0 ? "Context:\n" + contextInfo.join("\n") : ""}

Classify this request and respond with ONLY valid JSON.`;

    const classificationResponse = await queryGroq("chat", [
      { role: "system", content: ORCHESTRATOR_SYSTEM_PROMPT },
      { role: "user", content: classificationPrompt },
    ]);

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
      const response = await actions.generateChatResponse(
        [...messages, { role: "user", content: message }],
        repoContext || undefined
      );
      const smartSuggestions = await actions.generateSmartSuggestions(
        messages,
        message,
        response
      );
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
        const response = await actions.generateChatResponse(chatMessages, repoContext || undefined);
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
        const plan = await actions.generateRefactorPlan(
          repoContext.owner,
          repoContext.repo,
          parameters?.prompt || message,
          accessToken,
          repoContext.files,
          parameters?.deepContext || false
        );
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
        const testContent = await actions.generateTests(fileContent, selectedFile || "unknown");
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
        const report = await actions.auditCommit(repoContext.owner, repoContext.repo, commitSha, accessToken);
        result = { report };
        break;
      }

      case "architecture": {
        if (!accessToken || !repoContext?.owner || !repoContext?.repo) {
          return NextResponse.json({ error: "Repository not loaded" }, { status: 400 });
        }
        const answer = await actions.answerArchitecture(
          repoContext.owner,
          repoContext.repo,
          parameters?.question || message,
          accessToken,
          repoContext.files
        );
        result = { text: answer };
        break;
      }

      case "docs": {
        if (!accessToken || !repoContext?.owner || !repoContext?.repo) {
          return NextResponse.json({ error: "Repository not loaded" }, { status: 400 });
        }
        const docType = parameters?.type || (message.toLowerCase().includes("readme") ? "readme" : "openapi");
        if (docType === "readme") {
          const readme = await actions.generateReadme(repoContext.owner, repoContext.repo, accessToken, repoContext.files || []);
          result = { readme };
        } else {
          const openapi = await actions.generateOpenApi(repoContext.owner, repoContext.repo, accessToken, repoContext.files || []);
          result = { openapi };
        }
        break;
      }

      case "search": {
        if (!accessToken || !repoContext?.owner || !repoContext?.repo) {
          return NextResponse.json({ error: "Repository not loaded" }, { status: 400 });
        }
        const files = await actions.searchCode(
          repoContext.owner,
          repoContext.repo,
          parameters?.query || message,
          accessToken,
          repoContext.files || []
        );
        result = { files };
        break;
      }

      case "commit": {
        if (!accessToken || !repoContext?.owner || !repoContext?.repo || !selectedFile) {
          return NextResponse.json({ error: "Repository and file must be loaded for commit" }, { status: 400 });
        }
        // For commit, we need the new content - either from message or generate it
        const newContent = parameters?.content || fileContent; // In real scenario, AI would generate this
        const commitMsg = parameters?.message || await actions.generateCommitMessage("", newContent, selectedFile, message);
        
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
        const analysis = await actions.analyzeCICD(repoContext.owner, repoContext.repo, runId, accessToken);
        result = { analysis };
        break;
      }

      default:
        // Fallback to chat with repository file context
        const response = await actions.generateChatResponse(
          [...messages, { role: "user", content: message }],
          repoContext || undefined
        );
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
    
    const smartSuggestions = await actions.generateSmartSuggestions(
      messages,
      message,
      assistantText
    );

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
