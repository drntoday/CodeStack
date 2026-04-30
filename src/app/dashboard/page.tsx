"use client"

import { useSession, signOut } from "next-auth/react"
import { useState, useEffect, useRef } from "react"

interface Message {
  role: "user" | "assistant" | "system"
  content: string
  action?: string
  plan?: Array<{ file: string; instruction: string; reason: string; enabled?: boolean }>
  testContent?: string
  files?: string[]
  requiresApproval?: boolean
  suggestions?: string[]
}

interface RepoContext {
  owner: string
  repo: string
  files: string[]
}

export default function Dashboard() {
  const { data: session } = useSession()
  
  // Repo state
  const [repoInput, setRepoInput] = useState("")
  const [repoContext, setRepoContext] = useState<RepoContext | null>(null)
  const [loadingRepo, setLoadingRepo] = useState(false)
  
  // File tree state
  const [selectedFile, setSelectedFile] = useState("")
  const [fileContent, setFileContent] = useState("")
  const [sidebarOpen, setSidebarOpen] = useState(true)
  
  // Chat state
  const [messages, setMessages] = useState<Message[]>([])
  const [chatInput, setChatInput] = useState("")
  const [loading, setLoading] = useState(false)
  
  // Pending changes state
  const [pendingChange, setPendingChange] = useState<{
    path: string
    content: string
    message: string
    branch: string
  } | null>(null)
  const [committing, setCommitting] = useState(false)
  
  // Refactor plan state
  const [refactorPlan, setRefactorPlan] = useState<{ file: string; instruction: string; reason: string; enabled?: boolean }[] | null>(null)
  const [executingRefactor, setExecutingRefactor] = useState(false)
  
  // Generated test state
  const [generatedTest, setGeneratedTest] = useState("")
  
  const messagesEndRef = useRef<HTMLDivElement>(null)

  // Auto-scroll to bottom of chat
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" })
  }, [messages])

  // Load repo tree on initial mount if we have a cached repo
  useEffect(() => {
    const savedRepo = localStorage.getItem("codeStackRepo")
    if (savedRepo) {
      try {
        const parsed = JSON.parse(savedRepo)
        setRepoContext(parsed)
        setRepoInput(`${parsed.owner}/${parsed.repo}`)
      } catch {}
    }
  }, [])

  const loadFiles = async () => {
    if (!repoInput.includes("/")) return alert("Enter owner/repo")
    const [owner, repo] = repoInput.split("/")
    setLoadingRepo(true)
    
    try {
      const res = await fetch("/api/github/tree", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ owner, repo }),
      })
      
      if (res.ok) {
        const data = await res.json()
        const context = { owner, repo, files: data.files }
        setRepoContext(context)
        localStorage.setItem("codeStackRepo", JSON.stringify(context))
        setMessages(prev => [...prev, {
          role: "assistant",
          content: `Loaded ${data.files.length} files from ${owner}/${repo}. What would you like to do?`,
          suggestions: [
            "Explain the project structure",
            "Search for authentication logic",
            "Generate a README",
          ],
        }])
      } else {
        alert("Failed to load repository")
      }
    } catch (error) {
      console.error(error)
      alert("Error loading repository")
    } finally {
      setLoadingRepo(false)
    }
  }

  const selectFile = async (path: string) => {
    if (!repoContext) return
    setSelectedFile(path)
    
    try {
      const res = await fetch("/api/github/file", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...repoContext, path }),
      })
      
      if (res.ok) {
        const data = await res.json()
        setFileContent(data.content)
        setPendingChange(null)
        setGeneratedTest("")
        
        // Add system message about file selection
        setMessages(prev => [...prev, {
          role: "system",
          content: `📄 Selected: ${path}`,
        }])
      }
    } catch (error) {
      console.error(error)
    }
  }

  const sendMessage = async (customMessage?: string) => {
    const messageToSend = customMessage || chatInput
    if (!messageToSend.trim()) return
    
    const userMsg: Message = { role: "user", content: messageToSend }
    setMessages(prev => [...prev, userMsg])
    setChatInput("")
    setLoading(true)

    try {
      const res = await fetch("/api/orchestrator", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          message: messageToSend,
          repoContext,
          selectedFile,
          fileContent,
          messages: messages.filter(m => m.role !== "system"),
        }),
      })

      const data = await res.json()

      if (data.error) {
        setMessages(prev => [...prev, { role: "assistant", content: `Error: ${data.error}` }])
        return
      }

      // Build assistant response based on action
      const assistantMsg: Message = {
        role: "assistant",
        content: data.message || data.result?.text || "",
        action: data.action,
        requiresApproval: data.requiresApproval,
        suggestions: data.suggestions,
      }

      // Handle specific action results
      if (data.action === "refactor" && data.result?.plan) {
        assistantMsg.plan = data.result.plan
        setRefactorPlan(data.result.plan.map((step: any) => ({ ...step, enabled: true })))
      }

      if (data.action === "test" && data.result?.testContent) {
        assistantMsg.testContent = data.result.testContent
        setGeneratedTest(data.result.testContent)
        setPendingChange({
          path: `${selectedFile}.test.ts`,
          content: data.result.testContent,
          message: `Add tests for ${selectedFile}`,
          branch: "main",
        })
      }

      if (data.action === "search" && data.result?.files) {
        assistantMsg.files = data.result.files
      }

      if (data.action === "commit" && data.result) {
        setPendingChange({
          path: data.result.path,
          content: data.result.content,
          message: data.result.message,
          branch: data.result.branch,
        })
      }

      setMessages(prev => [...prev, assistantMsg])
    } catch (error: any) {
      console.error(error)
      setMessages(prev => [...prev, { 
        role: "assistant", 
        content: `Error: Failed to process request. ${error.message}` 
      }])
    } finally {
      setLoading(false)
    }
  }

  const executeRefactor = async () => {
    if (!repoContext || !refactorPlan) return
    setExecutingRefactor(true)

    try {
      const planToExecute = refactorPlan
        .filter(s => s.enabled)
        .map(({ file, instruction }) => ({ file, instruction }))

      const res = await fetch("/api/refactor/execute", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...repoContext, plan: planToExecute }),
      })

      const data = await res.json()
      
      if (data.success) {
        setMessages(prev => [...prev, {
          role: "assistant",
          content: "✅ Refactor workflow started! Check GitHub Actions for progress and the resulting PR.",
          suggestions: ["Monitor the workflow", "Generate updated docs"],
        }])
        setRefactorPlan(null)
      } else {
        setMessages(prev => [...prev, {
          role: "assistant",
          content: `❌ Error: ${data.error}`,
        }])
      }
    } catch (error: any) {
      setMessages(prev => [...prev, {
        role: "assistant",
        content: `❌ Error executing refactor: ${error.message}`,
      }])
    } finally {
      setExecutingRefactor(false)
    }
  }

  const commitChanges = async (createPR: boolean) => {
    if (!pendingChange || !repoContext) return
    setCommitting(true)

    try {
      // Get current file SHA if updating
      let sha: string | undefined
      const metaRes = await fetch("/api/github/file", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...repoContext, path: pendingChange.path }),
      })
      if (metaRes.ok) {
        const meta = await metaRes.json()
        sha = meta.sha
      }

      // Commit the changes
      const commitRes = await fetch("/api/github/commit", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...repoContext,
          path: pendingChange.path,
          content: pendingChange.content,
          message: pendingChange.message,
          branch: pendingChange.branch,
          sha,
        }),
      })

      if (!commitRes.ok) {
        const err = await commitRes.json()
        throw new Error(err.error || "Commit failed")
      }

      if (createPR) {
        // Create a new branch and PR
        const branchName = `ai-update-${Date.now()}`
        
        // First commit to new branch (already done above if we specify branch)
        // Now create PR
        const prRes = await fetch("/api/github/pr", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            ...repoContext,
            head: branchName,
            base: "main",
            title: pendingChange.message,
            body: "Changes proposed by Code Stack AI.",
          }),
        })

        if (prRes.ok) {
          const prData = await prRes.json()
          setMessages(prev => [...prev, {
            role: "assistant",
            content: `✅ PR created: ${prData.data.html_url}`,
            suggestions: ["Monitor CI for this PR", "Add reviewers"],
          }])
        }
      } else {
        setMessages(prev => [...prev, {
          role: "assistant",
          content: `✅ Committed to ${pendingChange.branch}!`,
          suggestions: ["Create a PR", "Deploy changes"],
        }])
      }

      setPendingChange(null)
      // Refresh file if it was the selected one
      if (pendingChange.path === selectedFile) {
        selectFile(selectedFile)
      }
    } catch (error: any) {
      setMessages(prev => [...prev, {
        role: "assistant",
        content: `❌ Error: ${error.message}`,
      }])
    } finally {
      setCommitting(false)
    }
  }

  const handleSuggestionClick = (suggestion: string) => {
    sendMessage(suggestion)
  }

  // Extract filename from message for auto-context
  const extractFileName = (message: string): string | null => {
    const filePatterns = [
      /(?:in|from|file[:\s]+)\s*([a-zA-Z0-9_\-./]+\.(ts|tsx|js|jsx|py|go|java|rb))/i,
      /([a-zA-Z0-9_\-./]+\.(ts|tsx|js|jsx|py|go|java|rb))/i,
    ]
    
    for (const pattern of filePatterns) {
      const match = message.match(pattern)
      if (match) {
        const fileName = match[1]
        // Check if this file exists in our repo
        if (repoContext?.files.includes(fileName)) {
          return fileName
        }
      }
    }
    return null
  }

  if (!session) return null

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950 text-white">
      {/* Header */}
      <header className="fixed top-0 left-0 right-0 z-50 backdrop-blur-xl bg-slate-900/80 border-b border-white/10">
        <div className="max-w-[1800px] mx-auto px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <h1 className="text-xl font-bold bg-gradient-to-r from-blue-400 to-emerald-400 bg-clip-text text-transparent">
              Code Stack
            </h1>
            {repoContext && (
              <span className="text-xs px-2 py-1 rounded-full bg-white/10 border border-white/10">
                {repoContext.owner}/{repoContext.repo}
              </span>
            )}
          </div>
          
          <div className="flex items-center gap-2">
            {/* Repo loader */}
            <div className="flex gap-2">
              <input
                type="text"
                placeholder="owner/repo"
                className="w-40 px-3 py-1.5 text-sm rounded-lg bg-white/5 border border-white/10 focus:border-blue-500/50 focus:outline-none transition-colors"
                value={repoInput}
                onChange={(e) => setRepoInput(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && loadFiles()}
              />
              <button
                onClick={loadFiles}
                disabled={loadingRepo}
                className="px-3 py-1.5 text-sm rounded-lg bg-blue-600 hover:bg-blue-500 disabled:opacity-50 transition-colors"
              >
                {loadingRepo ? "Loading..." : "Load"}
              </button>
            </div>
            
            <button
              onClick={() => signOut()}
              className="px-3 py-1.5 text-sm rounded-lg bg-red-600/20 hover:bg-red-600/40 border border-red-500/30 transition-colors"
            >
              Sign Out
            </button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <div className="pt-16 h-screen flex">
        {/* Left Sidebar - File Tree */}
        <aside
          className={`${
            sidebarOpen ? "w-72" : "w-0"
          } transition-all duration-300 overflow-hidden border-r border-white/10 backdrop-blur-xl bg-slate-900/50`}
        >
          <div className="p-4 h-full overflow-y-auto">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-sm font-semibold text-white/70">Files</h2>
              <button
                onClick={() => setSidebarOpen(false)}
                className="p-1 rounded hover:bg-white/10 transition-colors"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                </svg>
              </button>
            </div>
            
            {repoContext?.files ? (
              <ul className="space-y-0.5">
                {repoContext.files.map((file) => (
                  <li key={file}>
                    <button
                      onClick={() => selectFile(file)}
                      className={`w-full text-left px-2 py-1.5 text-xs rounded transition-colors truncate ${
                        selectedFile === file
                          ? "bg-blue-600/30 text-blue-300 border border-blue-500/30"
                          : "hover:bg-white/5 text-white/60"
                      }`}
                    >
                      {file}
                    </button>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-xs text-white/40">Load a repository to see files</p>
            )}
          </div>
        </aside>

        {/* Toggle sidebar button */}
        {!sidebarOpen && (
          <button
            onClick={() => setSidebarOpen(true)}
            className="absolute left-4 top-20 p-2 rounded-lg bg-slate-800/80 hover:bg-slate-700 border border-white/10 transition-colors z-10"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </button>
        )}

        {/* Right Panel - Chat */}
        <main className="flex-1 flex flex-col min-w-0">
          {/* Chat Messages */}
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            {messages.length === 0 && (
              <div className="h-full flex items-center justify-center">
                <div className="text-center max-w-md">
                  <h2 className="text-2xl font-bold mb-2 bg-gradient-to-r from-blue-400 to-emerald-400 bg-clip-text text-transparent">
                    Welcome to Code Stack
                  </h2>
                  <p className="text-white/60 mb-6">
                    Your AI-first coding partner. Load a repository and start chatting.
                  </p>
                  <div className="grid grid-cols-2 gap-2 text-sm">
                    {[
                      "Add error handling to all API routes",
                      "Generate tests for the login file",
                      "Where is the password reset logic?",
                      "Create a PR with these changes",
                    ].map((example) => (
                      <button
                        key={example}
                        onClick={() => sendMessage(example)}
                        className="p-3 rounded-lg bg-white/5 hover:bg-white/10 border border-white/10 text-left transition-colors"
                      >
                        {example}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            )}

            {messages.map((msg, i) => (
              <div
                key={i}
                className={`flex ${msg.role === "user" ? "justify-end" : msg.role === "system" ? "justify-center" : "justify-start"}`}
              >
                {msg.role === "system" ? (
                  <span className="text-xs text-white/40">{msg.content}</span>
                ) : (
                  <div
                    className={`max-w-[85%] rounded-2xl px-4 py-3 backdrop-blur-xl border ${
                      msg.role === "user"
                        ? "bg-blue-600/20 border-blue-500/30"
                        : "bg-emerald-600/10 border-emerald-500/20"
                    }`}
                  >
                    <p className="text-sm whitespace-pre-wrap">{msg.content}</p>
                    
                    {/* Render plan if present */}
                    {msg.plan && (
                      <div className="mt-3 space-y-2">
                        <p className="text-xs font-semibold text-white/70">Proposed Plan:</p>
                        <ul className="text-xs space-y-1 max-h-40 overflow-y-auto">
                          {msg.plan.map((step, idx) => (
                            <li key={idx} className="flex items-start gap-2">
                              <input
                                type="checkbox"
                                checked={step.enabled}
                                onChange={() => {
                                  setRefactorPlan(prev => prev ? prev.map((s, j) => 
                                    j === idx ? { ...s, enabled: !s.enabled } : s
                                  ) : null)
                                }}
                                className="mt-0.5 rounded border-white/20 bg-white/5"
                              />
                              <span>
                                <strong className="text-blue-300">{step.file}</strong>
                                <span className="text-white/60">: {step.instruction}</span>
                              </span>
                            </li>
                          ))}
                        </ul>
                        {msg.requiresApproval && (
                          <button
                            onClick={executeRefactor}
                            disabled={executingRefactor}
                            className="mt-2 px-3 py-1.5 text-xs rounded-lg bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 transition-colors"
                          >
                            {executingRefactor ? "Executing..." : "✓ Approve & Execute"}
                          </button>
                        )}
                      </div>
                    )}

                    {/* Render search results */}
                    {msg.files && msg.files.length > 0 && (
                      <div className="mt-3">
                        <p className="text-xs font-semibold text-white/70 mb-1">Found files:</p>
                        <ul className="text-xs space-y-0.5">
                          {msg.files.map((file, idx) => (
                            <li key={idx}>
                              <button
                                onClick={() => selectFile(file)}
                                className="text-blue-400 hover:text-blue-300 hover:underline"
                              >
                                {file}
                              </button>
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}

                    {/* Suggestions chips */}
                    {msg.suggestions && msg.suggestions.length > 0 && (
                      <div className="mt-3 flex flex-wrap gap-2">
                        {msg.suggestions.map((suggestion, idx) => (
                          <button
                            key={idx}
                            onClick={() => handleSuggestionClick(suggestion)}
                            className="px-2 py-1 text-xs rounded-full bg-white/10 hover:bg-white/20 border border-white/10 transition-colors"
                          >
                            {suggestion}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            ))}
            
            {loading && (
              <div className="flex justify-start">
                <div className="rounded-2xl px-4 py-3 backdrop-blur-xl bg-emerald-600/10 border border-emerald-500/20">
                  <div className="flex items-center gap-2">
                    <div className="w-2 h-2 rounded-full bg-emerald-400 animate-bounce" style={{ animationDelay: "0ms" }} />
                    <div className="w-2 h-2 rounded-full bg-emerald-400 animate-bounce" style={{ animationDelay: "150ms" }} />
                    <div className="w-2 h-2 rounded-full bg-emerald-400 animate-bounce" style={{ animationDelay: "300ms" }} />
                  </div>
                </div>
              </div>
            )}
            
            <div ref={messagesEndRef} />
          </div>

          {/* Pending Change Preview */}
          {pendingChange && (
            <div className="mx-4 mb-4 rounded-xl backdrop-blur-xl bg-amber-600/10 border border-amber-500/20 p-4">
              <h3 className="text-sm font-semibold mb-2 flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-amber-400" />
                Pending change to {pendingChange.path}
              </h3>
              <div className="text-xs text-white/60 mb-3">
                Commit message: <span className="text-white">{pendingChange.message}</span>
              </div>
              <div className="flex gap-2">
                <button
                  onClick={() => commitChanges(false)}
                  disabled={committing}
                  className="px-3 py-1.5 text-xs rounded-lg bg-amber-600 hover:bg-amber-500 disabled:opacity-50 transition-colors"
                >
                  {committing ? "Committing..." : "Commit"}
                </button>
                <button
                  onClick={() => commitChanges(true)}
                  disabled={committing}
                  className="px-3 py-1.5 text-xs rounded-lg bg-purple-600 hover:bg-purple-500 disabled:opacity-50 transition-colors"
                >
                  {committing ? "Creating..." : "Create PR"}
                </button>
                <button
                  onClick={() => setPendingChange(null)}
                  className="px-3 py-1.5 text-xs rounded-lg bg-white/10 hover:bg-white/20 transition-colors"
                >
                  Cancel
                </button>
              </div>
            </div>
          )}

          {/* Input Area */}
          <div className="p-4 border-t border-white/10 backdrop-blur-xl bg-slate-900/50">
            <div className="max-w-4xl mx-auto">
              <div className="flex gap-2">
                <input
                  type="text"
                  placeholder="What do you want to do with your codebase?"
                  className="flex-1 px-4 py-3 rounded-xl bg-white/5 border border-white/10 focus:border-blue-500/50 focus:outline-none transition-colors placeholder:text-white/30"
                  value={chatInput}
                  onChange={(e) => setChatInput(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && sendMessage()}
                />
                <button
                  onClick={() => sendMessage()}
                  disabled={loading || !chatInput.trim()}
                  className="px-6 py-3 rounded-xl bg-gradient-to-r from-blue-600 to-emerald-600 hover:from-blue-500 hover:to-emerald-500 disabled:opacity-50 disabled:cursor-not-allowed transition-all font-medium"
                >
                  Send
                </button>
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>
  )
}
