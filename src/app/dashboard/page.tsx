"use client"

import { useSession, signOut } from "next-auth/react"
import { useState } from "react"

export default function Dashboard() {
  const { data: session } = useSession()
  const [activeTab, setActiveTab] = useState("chat")
  const [repoInput, setRepoInput] = useState("")
  const [owner, setOwner] = useState("")
  const [repo, setRepo] = useState("")
  const [files, setFiles] = useState<string[]>([])
  const [selectedFile, setSelectedFile] = useState("")
  const [fileContent, setFileContent] = useState("")
  const [chatInput, setChatInput] = useState("")
  const [messages, setMessages] = useState<{ role: string; content: string }[]>([])
  const [loading, setLoading] = useState(false)
  const [pendingChange, setPendingChange] = useState<string | null>(null)
  const [commitMessage, setCommitMessage] = useState("")
  const [prTitle, setPrTitle] = useState("")
  const [prBody, setPrBody] = useState("")
  const [committing, setCommitting] = useState(false)
  const [refactorPrompt, setRefactorPrompt] = useState("")
  const [refactorPlan, setRefactorPlan] = useState<{ file: string; instruction: string; reason: string; enabled: boolean }[] | null>(null)
  const [refactorLoading, setRefactorLoading] = useState(false)
  const [refactorMessage, setRefactorMessage] = useState("")
  const [generatingTests, setGeneratingTests] = useState(false)
  const [generatedTest, setGeneratedTest] = useState("")
  const [auditCommit, setAuditCommit] = useState("")
  const [auditReport, setAuditReport] = useState("")
  const [auditing, setAuditing] = useState(false)
  
  // Architecture Q&A state
  const [archQuestion, setArchQuestion] = useState("")
  const [archAnswer, setArchAnswer] = useState("")
  const [archLoading, setArchLoading] = useState(false)
  
  // Documentation generation state
  const [generatingReadme, setGeneratingReadme] = useState(false)
  const [readmeContent, setReadmeContent] = useState("")
  const [generatingOpenApi, setGeneratingOpenApi] = useState(false)
  const [openApiContent, setOpenApiContent] = useState("")
  
  // Code Search state
  const [searchQuery, setSearchQuery] = useState("")
  const [searchResults, setSearchResults] = useState<string[]>([])
  const [searching, setSearching] = useState(false)

  // Natural Language Git - AI commit message suggestion
  const [suggestingMessage, setSuggestingMessage] = useState(false)

  // CI/CD Optimization state
  const [ciRunId, setCiRunId] = useState("")
  const [ciAnalysis, setCiAnalysis] = useState("")
  const [ciLoading, setCiLoading] = useState(false)

  // Tool Integration (Custom Webhooks) state
  const [toolName, setToolName] = useState("")
  const [toolUrl, setToolUrl] = useState("")
  const [toolPayload, setToolPayload] = useState("{}")
  const [toolResult, setToolResult] = useState("")
  const [toolRunning, setToolRunning] = useState(false)

  // Deployment trigger state
  const [deploying, setDeploying] = useState(false)
  const [deployMessage, setDeployMessage] = useState("")

  const generateRefactorPlan = async () => {
    if (!owner || !repo || !refactorPrompt) return alert("Load a repo and enter an instruction first.")
    setRefactorLoading(true)
    setRefactorMessage("")
    const res = await fetch("/api/refactor/plan", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ owner, repo, prompt: refactorPrompt }),
    })
    const data = await res.json()
    if (data.plan) {
      setRefactorPlan(data.plan.map((step: any) => ({ ...step, enabled: true })))
    } else {
      alert("Failed to generate plan: " + (data.error || data.raw))
    }
    setRefactorLoading(false)
  }

  const executeRefactor = async () => {
    if (!owner || !repo || !refactorPlan) return
    setRefactorLoading(true)
    setRefactorMessage("")
    const planToExecute = refactorPlan.filter(s => s.enabled).map(({ file, instruction }) => ({ file, instruction }))
    const res = await fetch("/api/refactor/execute", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ owner, repo, plan: planToExecute }),
    })
    const data = await res.json()
    if (data.success) {
      setRefactorMessage("Refactor workflow started! Check GitHub Actions for progress and the resulting PR.")
      setRefactorPlan(null)
    } else {
      setRefactorMessage("Error: " + (data.error || "Unknown"))
    }
    setRefactorLoading(false)
  }

  const handleGenerateTests = async () => {
    if (!selectedFile || !fileContent || !owner || !repo) return;
    setGeneratingTests(true);
    const res = await fetch("/api/tests/generate", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ owner, repo, filePath: selectedFile, fileContent }),
    });
    const data = await res.json();
    if (data.testContent) setGeneratedTest(data.testContent);
    else alert("Test generation failed.");
    setGeneratingTests(false);
  }

  const handleAudit = async () => {
    if (!owner || !repo || !auditCommit) return;
    setAuditing(true);
    const res = await fetch("/api/audit", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ owner, repo, commitSha: auditCommit }),
    });
    const data = await res.json();
    setAuditReport(data.report || "Error");
    setAuditing(false);
  };

  const askArchitecture = async () => {
    if (!owner || !repo || !archQuestion) return;
    setArchLoading(true);
    const res = await fetch("/api/architecture/ask", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ owner, repo, question: archQuestion }),
    });
    const data = await res.json();
    setArchAnswer(data.answer || "Error");
    setArchLoading(false);
  };

  const generateReadme = async () => {
    setGeneratingReadme(true);
    const res = await fetch("/api/docs/generate-readme", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ owner, repo }),
    });
    const data = await res.json();
    setReadmeContent(data.readme);
    setGeneratingReadme(false);
  };

  const generateOpenApi = async () => {
    setGeneratingOpenApi(true);
    const res = await fetch("/api/docs/generate-openapi", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ owner, repo }),
    });
    const data = await res.json();
    setOpenApiContent(data.openapi);
    setGeneratingOpenApi(false);
  };

  const handleSearch = async () => {
    if (!owner || !repo || !searchQuery) return;
    setSearching(true);
    const res = await fetch("/api/search", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ owner, repo, query: searchQuery }),
    });
    const data = await res.json();
    setSearchResults(data.files || []);
    setSearching(false);
  };

  // Natural Language Git - AI commit message suggestion
  const suggestCommitMessage = async () => {
    if (!pendingChange) return;
    setSuggestingMessage(true);
    const res = await fetch("/api/git/commit-message", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        oldContent: fileContent,
        newContent: pendingChange,
        filePath: selectedFile,
        description: chatInput,
      }),
    });
    const data = await res.json();
    if (data.message) {
      setCommitMessage(data.message);
    }
    setSuggestingMessage(false);
  };

  // CI/CD Optimization - analyze failed pipeline
  const analyzeCICD = async () => {
    if (!owner || !repo || !ciRunId) return;
    setCiLoading(true);
    const res = await fetch("/api/ci/analyze", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ owner, repo, runId: ciRunId }),
    });
    const data = await res.json();
    setCiAnalysis(data.analysis || "Error");
    setCiLoading(false);
  };

  // Tool Integration - trigger custom webhook
  const triggerTool = async () => {
    if (!toolUrl) return;
    setToolRunning(true);
    let payload;
    try {
      payload = JSON.parse(toolPayload);
    } catch {
      alert("Invalid JSON payload");
      setToolRunning(false);
      return;
    }
    const res = await fetch("/api/tools/trigger", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ url: toolUrl, payload }),
    });
    const data = await res.json();
    setToolResult(JSON.stringify(data, null, 2));
    setToolRunning(false);
  };

  const loadFiles = async () => {
    if (!repoInput.includes("/")) return alert("Enter owner/repo")
    const [o, r] = repoInput.split("/")
    setOwner(o)
    setRepo(r)
    const res = await fetch("/api/github/tree", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ owner: o, repo: r }),
    })
    if (res.ok) {
      const data = await res.json()
      setFiles(data.files)
    }
  }

  const selectFile = async (path: string) => {
    setSelectedFile(path)
    const res = await fetch("/api/github/file", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ owner, repo, path }),
    })
    if (res.ok) {
      const data = await res.json()
      setFileContent(data.content)
      setPendingChange(null) // reset when switching files
    }
  }

  const sendMessage = async () => {
    if (!chatInput.trim()) return
    const userMsg = { role: "user", content: chatInput }
    setMessages((prev) => [...prev, userMsg])
    setChatInput("")
    setLoading(true)

    let contextPrompt = chatInput
    if (selectedFile && fileContent) {
      contextPrompt = `[File: ${selectedFile}]\n\`\`\`\n${fileContent}\n\`\`\`\n\n${chatInput}`
    }

    const res = await fetch("/api/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        messages: [...messages, { role: "user", content: contextPrompt }],
      }),
    })
    const data = await res.json()
    if (data.text) {
      setMessages((prev) => [...prev, { role: "assistant", content: data.text }])
      // If we have a selected file, treat the AI response as a possible edit
      if (selectedFile) {
        setPendingChange(data.text)
      }
    }
    setLoading(false)
  }

  const commitDirectly = async () => {
    if (!pendingChange || !selectedFile) return
    setCommitting(true)
    // Get SHA if file exists (update)
    let sha: string | undefined;
    if (fileContent) {
      // Fetch current file sha again? We didn't store sha from file read earlier.
      // Let's do a quick fetch for the file metadata.
      const metaRes = await fetch("/api/github/file", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ owner, repo, path: selectedFile }),
      })
      if (metaRes.ok) {
        const meta = await metaRes.json()
        sha = meta.sha
      }
    }

    const res = await fetch("/api/github/commit", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        owner,
        repo,
        path: selectedFile,
        content: pendingChange,
        message: commitMessage || `Update ${selectedFile}`,
        branch: "main",
        sha,
      }),
    })
    if (res.ok) {
      alert("Committed successfully to main!")
      // Refresh file content
      selectFile(selectedFile)
    } else {
      const err = await res.json()
      alert("Commit failed: " + JSON.stringify(err))
    }
    setCommitting(false)
  }

  const commitAndPR = async () => {
    if (!pendingChange || !selectedFile) return
    const branchName = `ai-update-${Date.now()}`
    setCommitting(true)

    // Step 1: Create the new branch (by committing to it with the new file)
    let sha: string | undefined
    const metaRes = await fetch("/api/github/file", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ owner, repo, path: selectedFile }),
    })
    if (metaRes.ok) {
      const meta = await metaRes.json()
      sha = meta.sha
    }

    const commitRes = await fetch("/api/github/commit", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        owner,
        repo,
        path: selectedFile,
        content: pendingChange,
        message: commitMessage || `Update ${selectedFile}`,
        branch: branchName,
        sha,
      }),
    })

    if (!commitRes.ok) {
      const err = await commitRes.json()
      alert("Failed to create branch: " + JSON.stringify(err))
      setCommitting(false)
      return
    }

    // Step 2: Open a PR
    const prRes = await fetch("/api/github/pr", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        owner,
        repo,
        head: branchName,
        base: "main",
        title: prTitle || "AI update",
        body: prBody || "Changes proposed by Code Stack AI.",
      }),
    })
    if (prRes.ok) {
      const prData = await prRes.json()
      alert(`PR created: ${prData.data.html_url}`)
    } else {
      const err = await prRes.json()
      alert("PR creation failed: " + JSON.stringify(err))
    }
    setCommitting(false)
  }

  if (!session) return null

  return (
    <div className="min-h-screen p-4">
      <div className="flex justify-between items-center mb-4">
        <h1 className="text-2xl font-bold">Code Stack</h1>
        <button onClick={() => signOut()} className="px-4 py-2 bg-red-500 text-white rounded">
          Sign Out
        </button>
      </div>

      {/* Repo loader – common across all tabs */}
      <div className="mb-4 flex gap-2">
        <input
          type="text"
          placeholder="owner/repo"
          className="border p-2 rounded w-64"
          value={repoInput}
          onChange={(e) => setRepoInput(e.target.value)}
        />
        <button onClick={loadFiles} className="px-4 py-2 bg-blue-500 text-white rounded">Load Repo</button>
        {files.length > 0 && (
          <select
            className="border p-2 rounded w-64"
            value={selectedFile}
            onChange={(e) => selectFile(e.target.value)}
          >
            <option value="">Select a file</option>
            {files.map((f) => (<option key={f} value={f}>{f}</option>))}
          </select>
        )}
      </div>

      {/* Tab navigation */}
      <div className="flex gap-2 mb-4 flex-wrap">
        {["chat", "refactor", "testing", "onboarding", "workflow"].map(tab => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={`px-4 py-2 rounded capitalize ${activeTab === tab ? "bg-blue-500 text-white" : "bg-gray-200"}`}
          >
            {tab === "chat" ? "Chat & Editor" : tab === "onboarding" ? "Onboard & Docs" : tab}
          </button>
        ))}
      </div>

      {/* Render the active tab */}
      {activeTab === "chat" && (
        <div className="space-y-4">
          {selectedFile && (
            <div className="flex gap-2 mb-4">
              <button
                onClick={handleGenerateTests}
                disabled={generatingTests}
                className="px-4 py-2 bg-teal-500 text-white rounded disabled:opacity-50"
              >
                {generatingTests ? "Generating…" : "Generate Tests"}
              </button>
              {generatedTest && (
                <button
                  onClick={() => {
                    setPendingChange(generatedTest);
                    setCommitMessage(`Add tests for ${selectedFile}`);
                  }}
                  className="px-4 py-2 bg-orange-500 text-white rounded"
                >
                  Apply as new test file
                </button>
              )}
            </div>
          )}
          
          {generatedTest && (
            <details className="mb-4">
              <summary className="cursor-pointer text-sm font-semibold">View generated test</summary>
              <pre className="whitespace-pre-wrap text-xs bg-gray-100 p-2 rounded max-h-40 overflow-auto">
                {generatedTest}
              </pre>
            </details>
          )}

          {/* Chat area */}
          <div className="border rounded h-64 overflow-y-scroll p-4 mb-4 bg-gray-50">
            {messages.map((m, i) => (
              <div key={i} className={`mb-2 ${m.role === "user" ? "text-right" : "text-left"}`}>
                <span className={`inline-block p-2 rounded max-w-[80%] whitespace-pre-wrap ${m.role === "user" ? "bg-blue-100" : "bg-green-100"}`}>
                  {m.content}
                </span>
              </div>
            ))}
            {loading && <div className="text-center">Thinking...</div>}
          </div>

          <div className="flex gap-2 mb-4">
            <input
              type="text"
              className="border p-2 rounded flex-1"
              placeholder="Ask something or request an edit..."
              value={chatInput}
              onChange={(e) => setChatInput(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && sendMessage()}
            />
            <button onClick={sendMessage} className="px-4 py-2 bg-green-500 text-white rounded">
              Send
            </button>
          </div>

          {/* Pending change preview */}
          {pendingChange && selectedFile && (
            <div className="border rounded p-4 mb-4 bg-yellow-50">
              <h2 className="font-semibold mb-2">Proposed change to {selectedFile}</h2>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <div className="text-xs font-mono bg-gray-200 p-2 rounded">Current</div>
                  <pre className="whitespace-pre-wrap text-xs bg-gray-100 p-2 rounded max-h-40 overflow-auto">{fileContent}</pre>
                </div>
                <div>
                  <div className="text-xs font-mono bg-green-200 p-2 rounded">New</div>
                  <pre className="whitespace-pre-wrap text-xs bg-green-50 p-2 rounded max-h-40 overflow-auto">{pendingChange}</pre>
                </div>
              </div>

              <div className="mt-4 space-y-2">
                <div className="flex gap-2 items-center">
                  <input
                    type="text"
                    placeholder="Commit message"
                    className="border p-2 rounded flex-1"
                    value={commitMessage}
                    onChange={(e) => setCommitMessage(e.target.value)}
                  />
                  <button
                    onClick={suggestCommitMessage}
                    disabled={suggestingMessage || !pendingChange}
                    className="px-3 py-1 bg-gray-200 rounded hover:bg-gray-300 text-sm whitespace-nowrap"
                  >
                    {suggestingMessage ? "Thinking…" : "✨ AI generate"}
                  </button>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={commitDirectly}
                    disabled={committing}
                    className="px-4 py-2 bg-orange-500 text-white rounded disabled:opacity-50"
                  >
                    Commit directly to main
                  </button>
                  <button
                    onClick={commitAndPR}
                    disabled={committing}
                    className="px-4 py-2 bg-purple-500 text-white rounded disabled:opacity-50"
                  >
                    Create PR
                  </button>
                </div>
                {committing && <span className="text-sm">Working...</span>}
              </div>
            </div>
          )}

          {/* PR details inputs */}
          {committing && (
            <div className="space-y-2 mt-2">
              <input
                type="text"
                placeholder="PR title (if creating PR)"
                className="border p-2 rounded w-full"
                value={prTitle}
                onChange={(e) => setPrTitle(e.target.value)}
              />
              <textarea
                placeholder="PR description"
                className="border p-2 rounded w-full"
                value={prBody}
                onChange={(e) => setPrBody(e.target.value)}
              />
            </div>
          )}
        </div>
      )}

      {activeTab === "refactor" && (
        <div className="border rounded p-4 mt-6 bg-white">
          <h2 className="text-xl font-bold mb-2">Mass Refactoring</h2>
          <p className="text-sm text-gray-600 mb-2">
            Describe a high‑level change (e.g., "Add JSDoc comments to all functions in /src").
          </p>
          <textarea
            className="border p-2 rounded w-full h-24"
            placeholder="Refactor instruction..."
            value={refactorPrompt}
            onChange={(e) => setRefactorPrompt(e.target.value)}
          />
          <button
            onClick={generateRefactorPlan}
            disabled={refactorLoading}
            className="mt-2 px-4 py-2 bg-indigo-500 text-white rounded disabled:opacity-50"
          >
            Generate Plan
          </button>

          {refactorPlan && (
            <div className="mt-4">
              <h3 className="font-semibold">Proposed Plan (toggle steps to enable/disable):</h3>
              <ul className="list-none my-2 text-sm space-y-2">
                {refactorPlan.map((step, i) => (
                  <li key={i} className="flex items-start gap-2 border p-2 rounded">
                    <input
                      type="checkbox"
                      checked={step.enabled}
                      onChange={() => {
                        setRefactorPlan(prev => prev ? prev.map((s, idx) => idx === i ? { ...s, enabled: !s.enabled } : s) : null);
                      }}
                      className="mt-1"
                    />
                    <div>
                      <strong>{step.file}</strong>: {step.instruction}
                      <div className="text-xs text-gray-500">{step.reason}</div>
                    </div>
                  </li>
                ))}
              </ul>
              <button
                onClick={executeRefactor}
                disabled={refactorLoading}
                className="px-4 py-2 bg-purple-600 text-white rounded disabled:opacity-50"
              >
                Execute Refactor (via GitHub Action)
              </button>
            </div>
          )}
          {refactorMessage && <p className="mt-2 text-sm">{refactorMessage}</p>}
        </div>
      )}

      {activeTab === "testing" && (
        <div className="space-y-4">
          {/* Security Audit Section */}
          <div className="border rounded p-4">
            <h2 className="text-xl font-bold mb-2">Security Audit</h2>
            <input
              type="text"
              placeholder="Commit SHA"
              className="border p-2 rounded w-64"
              value={auditCommit}
              onChange={(e) => setAuditCommit(e.target.value)}
            />
            <button
              onClick={handleAudit}
              disabled={auditing}
              className="ml-2 px-4 py-2 bg-red-500 text-white rounded disabled:opacity-50"
            >
              {auditing ? "Scanning…" : "Audit Commit"}
            </button>
            {auditReport && (
              <pre className="mt-3 whitespace-pre-wrap bg-gray-900 text-green-400 p-3 rounded text-sm">
                {auditReport}
              </pre>
            )}
          </div>

          {/* CI/CD Assistant Section */}
          <div className="border rounded p-4">
            <h2 className="text-xl font-bold mb-2">CI/CD Assistant</h2>
            <p className="text-sm mb-2">Paste a failed workflow run ID (find it in the Actions tab).</p>
            <input
              type="text"
              placeholder="e.g., 1234567890"
              className="border p-2 rounded w-64"
              value={ciRunId}
              onChange={(e) => setCiRunId(e.target.value)}
            />
            <button
              onClick={analyzeCICD}
              disabled={ciLoading}
              className="ml-2 px-4 py-2 bg-yellow-500 text-white rounded disabled:opacity-50"
            >
              {ciLoading ? "Analyzing…" : "Analyze failure"}
            </button>
            {ciAnalysis && (
              <pre className="mt-3 bg-gray-100 p-3 rounded text-sm whitespace-pre-wrap">{ciAnalysis}</pre>
            )}
          </div>
        </div>
      )}

      {activeTab === "onboarding" && (
        <div className="space-y-4">
          {/* Code Search Section */}
          <div className="border rounded p-4">
            <h2 className="text-xl font-bold mb-2">Code Search</h2>
            <div className="flex gap-2">
              <input
                type="text"
                placeholder="e.g., where is password reset?"
                className="border p-2 rounded flex-1"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleSearch()}
              />
              <button
                onClick={handleSearch}
                disabled={searching}
                className="px-4 py-2 bg-green-500 text-white rounded disabled:opacity-50"
              >
                {searching ? "Searching…" : "Search"}
              </button>
            </div>
            {searchResults.length > 0 && (
              <ul className="mt-2 list-disc ml-6">
                {searchResults.map((f, i) => (
                  <li key={i}>
                    <button
                      className="text-blue-600 hover:underline text-sm"
                      onClick={() => selectFile(f)}
                    >
                      {f}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* Architecture Q&A Section */}
          <div className="border rounded p-4">
            <h2 className="text-xl font-bold mb-2">Architecture Q&A</h2>
            <input
              type="text"
              placeholder="e.g., How does the login flow to the database?"
              className="border p-2 rounded w-full"
              value={archQuestion}
              onChange={(e) => setArchQuestion(e.target.value)}
            />
            <button
              onClick={askArchitecture}
              disabled={archLoading}
              className="mt-2 px-4 py-2 bg-teal-500 text-white rounded disabled:opacity-50"
            >
              {archLoading ? "Analyzing…" : "Ask"}
            </button>
            {archAnswer && (
              <div className="mt-3 bg-gray-100 p-3 rounded text-sm whitespace-pre-wrap">{archAnswer}</div>
            )}
          </div>

          {/* Automatic Documentation Section */}
          <div className="border rounded p-4">
            <h2 className="text-xl font-bold mb-2">Automatic Documentation</h2>
            <button
              onClick={generateReadme}
              disabled={generatingReadme}
              className="px-4 py-2 bg-blue-500 text-white rounded disabled:opacity-50"
            >
              {generatingReadme ? "Generating…" : "Generate README.md"}
            </button>
            <button
              onClick={generateOpenApi}
              disabled={generatingOpenApi}
              className="ml-2 px-4 py-2 bg-indigo-500 text-white rounded disabled:opacity-50"
            >
              {generatingOpenApi ? "Generating…" : "Generate OpenAPI Spec"}
            </button>

            {readmeContent && (
              <details className="mt-2">
                <summary className="cursor-pointer text-sm">View README.md</summary>
                <pre className="bg-gray-100 p-2 rounded text-xs whitespace-pre-wrap max-h-60 overflow-auto">{readmeContent}</pre>
              </details>
            )}
            {openApiContent && (
              <details className="mt-2">
                <summary className="cursor-pointer text-sm">View OpenAPI Spec</summary>
                <pre className="bg-gray-100 p-2 rounded text-xs whitespace-pre-wrap max-h-60 overflow-auto">{openApiContent}</pre>
              </details>
            )}
          </div>
        </div>
      )}

      {activeTab === "workflow" && (
        <div className="space-y-4">
          {/* Tool Integration Section */}
          <div className="border rounded p-4">
            <h2 className="text-xl font-bold mb-2">Tool Integration</h2>
            <div className="space-y-2">
              <input
                type="text"
                placeholder="Tool name (e.g., Slack)"
                className="border p-2 rounded w-full"
                value={toolName}
                onChange={(e) => setToolName(e.target.value)}
              />
              <input
                type="text"
                placeholder="Webhook URL"
                className="border p-2 rounded w-full"
                value={toolUrl}
                onChange={(e) => setToolUrl(e.target.value)}
              />
              <textarea
                placeholder='Payload JSON (use {"text": "Hello"})'
                className="border p-2 rounded w-full h-20"
                value={toolPayload}
                onChange={(e) => setToolPayload(e.target.value)}
              />
              <button
                onClick={triggerTool}
                disabled={toolRunning}
                className="px-4 py-2 bg-gray-700 text-white rounded disabled:opacity-50"
              >
                {toolRunning ? "Triggering…" : "Trigger Tool"}
              </button>
              {toolResult && (
                <pre className="bg-gray-100 p-2 rounded text-xs overflow-auto max-h-28">{toolResult}</pre>
              )}
            </div>
          </div>

          {/* Deployment Trigger Section */}
          <div className="border rounded p-4 mt-4">
            <h3 className="font-semibold mb-2">Deployment Trigger</h3>
            <button
              onClick={async () => {
                if (!owner || !repo) return;
                setDeploying(true);
                const res = await fetch("/api/deploy/trigger", {
                  method: "POST",
                  headers: { "Content-Type": "application/json" },
                  body: JSON.stringify({ owner, repo }),
                });
                const data = await res.json();
                setDeployMessage(data.success ? "Deployment triggered!" : "Failed: " + JSON.stringify(data));
                setDeploying(false);
              }}
              disabled={deploying}
              className="px-4 py-2 bg-green-600 text-white rounded disabled:opacity-50"
            >
              {deploying ? "Triggering..." : "Deploy from main"}
            </button>
            <p className="text-xs mt-1">Triggers the `deploy.yml` workflow in your repo.</p>
            {deployMessage && <p className="text-sm mt-2">{deployMessage}</p>}
          </div>
        </div>
      )}
    </div>
  );
}
