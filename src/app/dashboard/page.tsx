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
  pendingDoc?: { type: "readme" | "openapi"; content: string }
}

interface RepoContext {
  owner: string
  repo: string
  files: string[]
}

interface PendingChange {
  path: string
  content: string
  message: string
  branch: string
}

export default function Dashboard() {
  const { data: session } = useSession()
  
  // Repo state
  const [repoInput, setRepoInput] = useState("")
  const [repoContext, setRepoContext] = useState<RepoContext | null>(null)
  const [loadingRepo, setLoadingRepo] = useState(false)
  const [repoLoadTimeout, setRepoLoadTimeout] = useState(false)
  const [manualReloadNeeded, setManualReloadNeeded] = useState(false)
  
  // File tree state
  const [selectedFile, setSelectedFile] = useState("")
  const [fileContent, setFileContent] = useState("")
  const [sidebarOpen, setSidebarOpen] = useState(true)
  
  // Chat state
  const [messages, setMessages] = useState<Message[]>([])
  const [chatInput, setChatInput] = useState("")
  const [loading, setLoading] = useState(false)
  
  // Pending changes state - now an array for multiple pending changes
  const [pendingChanges, setPendingChanges] = useState<PendingChange[]>([])
  const [pendingChange, setPendingChange] = useState<PendingChange | null>(null)
  const [committing, setCommitting] = useState(false)
  
  // Auto-commit mode toggle
  const [autoCommitMode, setAutoCommitMode] = useState(false)

  // Ref for auto-scrolling to pending changes
  const pendingChangeRef = useRef<HTMLDivElement>(null)
  
  // Auto-scroll to pending change when it appears
  useEffect(() => {
    if (pendingChanges.length > 0 && pendingChangeRef.current) {
      pendingChangeRef.current.scrollIntoView({ behavior: "smooth", block: "center" })
    }
  }, [pendingChanges])
  // Refactor plan state
  const [refactorPlan, setRefactorPlan] = useState<{ file: string; instruction: string; reason: string; enabled?: boolean }[] | null>(null)
  const [executingRefactor, setExecutingRefactor] = useState(false)
  const [refactorProgress, setRefactorProgress] = useState<{ current: number; total: number } | null>(null)
  
  // Generated test state
  const [generatedTest, setGeneratedTest] = useState("")
  
  // Auto-save toggle for docs
  const [autoSaveDocs, setAutoSaveDocs] = useState(false)
  
  // Deploy state
  const [deploying, setDeploying] = useState(false)
  
  // Webhook trigger state
  const [webhookPanelOpen, setWebhookPanelOpen] = useState(false)
  const [webhookUrl, setWebhookUrl] = useState("")
  const [webhookPayload, setWebhookPayload] = useState("{}")
  const [triggeringWebhook, setTriggeringWebhook] = useState(false)
  const [webhookResult, setWebhookResult] = useState<{ status?: number; body?: string; error?: string } | null>(null)
  
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const repoLoadStartTime = useRef<number>(Date.now())

  // Timeout guard for repoContext.files
  useEffect(() => {
    if (loadingRepo) {
      repoLoadStartTime.current = Date.now()
      const timeoutId = setTimeout(() => {
        if (!repoContext?.files) {
          setRepoLoadTimeout(true)
          setManualReloadNeeded(true)
        }
      }, 10000) // 10 second timeout
      
      return () => clearTimeout(timeoutId)
    }
  }, [loadingRepo, repoContext?.files])

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

  // Auto-load user's most recent repo when session is available
  useEffect(() => {
    if (session?.user?.name && !repoContext && !loadingRepo) {
      const username = session.user.name // GitHub login
      fetch(`https://api.github.com/users/${username}/repos?sort=updated&per_page=1`, {
        headers: { Authorization: `token ${session.accessToken}` },
      })
        .then(res => res.json())
        .then(repos => {
          if (repos?.[0]) {
            const { owner: { login }, name } = repos[0]
            const ownerRepoStr = `${login}/${name}`
            setRepoInput(ownerRepoStr)
            setLoadingRepo(true) // Block UI while loading tree
            return loadFilesDirect(ownerRepoStr)
          }
        })
        .catch(console.error)
    }
  }, [session])

  const loadFilesDirect = async (ownerRepoStr: string) => {
    if (!ownerRepoStr.includes("/")) return alert("Enter owner/repo")
    const [owner, repo] = ownerRepoStr.split("/")
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
        
        // Send special message to trigger AI greeting
        sendMessage("__REPO_LOADED__")
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
        
        // Send special message to trigger AI greeting
        sendMessage("__REPO_LOADED__")
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
        setPendingChanges([])
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
    // Block messages if timeout occurred and manual reload is needed
    if (manualReloadNeeded) {
      setMessages(prev => [...prev, { 
        role: "system", 
        content: "⚠️ Repository loading timed out. Please reload the page to try again." 
      }])
      return
    }
    
    const messageToSend = customMessage || chatInput
    if (!messageToSend.trim()) return
    
    // Validate repoContext.files before sending
    if (repoContext && (!repoContext.files || repoContext.files.length === 0)) {
      setMessages(prev => [...prev, { 
        role: "system", 
        content: "⚠️ The file list hasn't loaded yet. Wait a moment and try again." 
      }])
      return
    }
    
    // Don't add the sentinel message to visible messages
    if (messageToSend !== "__REPO_LOADED__") {
      const userMsg: Message = { role: "user", content: messageToSend }
      setMessages(prev => [...prev, userMsg])
    }
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

      let data;
      try {
        data = await res.json();
      } catch (jsonError) {
        console.error("Response is not valid JSON:", jsonError);
        setMessages(prev => [...prev, { role: "assistant", content: "Error: Received an invalid response from the server. Please try again." }]);
        setLoading(false);
        return;
      }

      if (data.error) {
        setMessages(prev => [...prev, { role: "assistant", content: `Error: ${data.error}` }])
        return
      }

      // Extract content from any result shape
      let assistantContent = "";
      if (data.result?.text) {
        assistantContent = data.result.text;
      } else if (data.result?.report) {
        assistantContent = data.result.report;
      } else if (data.result?.analysis) {
        assistantContent = data.result.analysis;
      } else if (data.result?.readme) {
        assistantContent = data.result.readme;
      } else if (data.result?.openapi) {
        assistantContent = data.result.openapi;
      } else if (data.result?.testContent) {
        assistantContent = data.result.testContent;
      } else if (data.result?.files) {
        assistantContent = data.result.files.join("\n");
      } else if (data.message) {
        assistantContent = data.message;
      } else {
        assistantContent = "No response.";
      }

      // Handle refactor plan
      if (data.result?.plan) {
        setRefactorPlan(data.result.plan.map((step: any) => ({ ...step, enabled: true })));
      }

      // Build assistant response based on action
      const assistantMsg: Message = {
        role: "assistant",
        content: assistantContent,
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
        // Set pending change that will be visible and auto-scroll into view
        setPendingChanges(prev => [...prev, {
          path: `${selectedFile}.test.ts`,
          content: data.result.testContent,
          message: `Add tests for ${selectedFile}`,
          branch: "main",
        }])
      }

      // Auto-handle README generation
      if (data.action === "docs" && data.result?.readme) {
        assistantMsg.content = data.result.readme
        if (autoSaveDocs) {
          // Auto-commit README
          setPendingChanges(prev => [...prev, { 
            path: "README.md",
            content: data.result.readme,
            message: "Generate README.md",
            branch: "main",
           }])
          // Auto-commit when auto-save is enabled
          setTimeout(() => commitChanges(false), 100)
        } else {
          // Show pending change with save button in message
          assistantMsg.pendingDoc = { type: "readme", content: data.result.readme }
        }
      }

      // Auto-handle OpenAPI generation
      if (data.action === "docs" && data.result?.openapi) {
        assistantMsg.content = data.result.openapi
        if (autoSaveDocs) {
          // Auto-commit OpenAPI
          setPendingChanges(prev => [...prev, { 
            path: "openapi.yaml",
            content: data.result.openapi,
            message: "Generate OpenAPI specification",
            branch: "main",
           }])
          // Auto-commit when auto-save is enabled
          setTimeout(() => commitChanges(false), 100)
        } else {
          // Show pending change with save button in message
          assistantMsg.pendingDoc = { type: "openapi", content: data.result.openapi }
        }
      }

      if (data.action === "search" && data.result?.files) {
        assistantMsg.files = data.result.files
      }

      if (data.action === "commit" && data.result) {
        setPendingChanges(prev => [...prev, { 
          path: data.result.path,
          content: data.result.content,
          message: data.result.message,
          branch: data.result.branch,
         }])
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

  const executeRefactor = async (mode: "workflow" | "direct" = "workflow") => {
    if (!repoContext || !refactorPlan) return
    setExecutingRefactor(true)

    try {
      const planToExecute = refactorPlan
        .filter(s => s.enabled)
        .map(({ file, instruction }) => ({ file, instruction }))

      // If mode is "direct", we need to call the backend directly with progress updates
      if (mode === "direct") {
        // Set initial progress state
        setRefactorProgress({ current: 0, total: planToExecute.length })
        
        // Call the actions module via a new API endpoint that supports streaming/progress
        const res = await fetch("/api/refactor/execute", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ ...repoContext, plan: planToExecute, mode: "direct" }),
        })

        const data = await res.json()
        
        // Clear progress indicator
        setRefactorProgress(null)
        
        if (data.success) {
          setMessages(prev => [...prev, {
            role: "assistant",
            content: `✅ ${data.message || `Successfully committed ${data.filesCommitted}/${data.totalFiles} files!`}`,
            suggestions: ["Generate updated docs", "Create a PR for these changes"],
          }])
          setRefactorPlan(null)
        } else {
          setMessages(prev => [...prev, {
            role: "assistant",
            content: `❌ Error: ${data.error}`,
          }])
        }
      } else {
        // Original workflow dispatch mode
        const res = await fetch("/api/refactor/execute", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ ...repoContext, plan: planToExecute, mode: "workflow" }),
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
          // If workflow dispatch fails, offer to fallback to direct commit
          setMessages(prev => [...prev, {
            role: "assistant",
            content: `⚠️ Workflow dispatch failed (${data.error}). Would you like to try Direct Commit mode instead?`,
            suggestions: ["Yes, use Direct Commit mode", "Cancel"],
          }])
        }
      }
    } catch (error: any) {
      setRefactorProgress(null)
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

      setPendingChanges([])
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
    // Handle special suggestion for Direct Commit mode fallback
    if (suggestion === "Yes, use Direct Commit mode") {
      executeRefactor("direct");
      return;
    }
    sendMessage(suggestion);
  }

  // Deploy handler
  const triggerDeploy = async () => {
    if (!repoContext) return alert("No repository loaded")
    
    const confirmed = window.confirm(`Trigger deploy.yml workflow on ${repoContext.owner}/${repoContext.repo}?`)
    if (!confirmed) return
    
    setDeploying(true)
    try {
      const res = await fetch("/api/deploy/trigger", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ owner: repoContext.owner, repo: repoContext.repo }),
      })
      
      const data = await res.json()
      if (res.ok) {
        setMessages(prev => [...prev, {
          role: "assistant",
          content: `✅ Deployment triggered successfully! Check GitHub Actions for progress.`,
        }])
      } else {
        setMessages(prev => [...prev, {
          role: "assistant",
          content: `❌ Deployment failed: ${data.error || "Unknown error"}`,
        }])
      }
    } catch (error: any) {
      setMessages(prev => [...prev, {
        role: "assistant",
        content: `❌ Error triggering deployment: ${error.message}`,
      }])
    } finally {
      setDeploying(false)
    }
  }

  // Webhook trigger handler
  const triggerWebhook = async () => {
    if (!webhookUrl.trim()) {
      setWebhookResult({ error: "Please enter a webhook URL" })
      return
    }
    
    let parsedPayload = {}
    try {
      parsedPayload = JSON.parse(webhookPayload)
    } catch (e) {
      setWebhookResult({ error: "Invalid JSON payload" })
      return
    }
    
    setTriggeringWebhook(true)
    setWebhookResult(null)
    
    try {
      const res = await fetch("/api/tools/trigger", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url: webhookUrl, payload: parsedPayload }),
      })
      
      const data = await res.json()
      if (res.ok) {
        setWebhookResult({ status: data.status, body: data.body })
        setMessages(prev => [...prev, {
          role: "assistant",
          content: `✅ Webhook triggered! Status: ${data.status}`,
        }])
      } else {
        setWebhookResult({ error: data.error || "Webhook call failed" })
      }
    } catch (error: any) {
      setWebhookResult({ error: error.message })
    } finally {
      setTriggeringWebhook(false)
    }
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
            {/* Auto-save docs toggle */}
            <label className="flex items-center gap-2 text-xs cursor-pointer">
              <input
                type="checkbox"
                checked={autoSaveDocs}
                onChange={(e) => setAutoSaveDocs(e.target.checked)}
                className="rounded border-white/20 bg-white/5"
              />
              <span className="text-white/70">Auto-save docs</span>
            </label>
            
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
              <button
                onClick={triggerDeploy}
                disabled={!repoContext || deploying}
                className="px-3 py-1.5 text-sm rounded-lg bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center gap-1"
              >
                {deploying ? (
                  <>
                    <svg className="w-3 h-3 animate-spin" viewBox="0 0 24 24" fill="none">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                    </svg>
                    Deploying...
                  </>
                ) : (
                  <>
                    <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                    </svg>
                    Deploy
                  </>
                )}
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
            
            {/* Trigger Webhook Section */}
            <div className="mt-6 pt-4 border-t border-white/10">
              <button
                onClick={() => setWebhookPanelOpen(!webhookPanelOpen)}
                className="w-full flex items-center justify-between text-sm font-semibold text-white/70 hover:text-white transition-colors"
              >
                <span className="flex items-center gap-2">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                  Trigger Webhook
                </span>
                <svg className={`w-4 h-4 transition-transform ${webhookPanelOpen ? "rotate-180" : ""}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </button>
              
              {webhookPanelOpen && (
                <div className="mt-3 space-y-3">
                  <div>
                    <label className="block text-xs text-white/50 mb-1">Webhook URL</label>
                    <input
                      type="url"
                      placeholder="https://example.com/webhook"
                      className="w-full px-2 py-1.5 text-xs rounded-lg bg-white/5 border border-white/10 focus:border-blue-500/50 focus:outline-none transition-colors"
                      value={webhookUrl}
                      onChange={(e) => setWebhookUrl(e.target.value)}
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-white/50 mb-1">Payload (JSON)</label>
                    <textarea
                      placeholder='{"key": "value"}'
                      className="w-full px-2 py-1.5 text-xs rounded-lg bg-white/5 border border-white/10 focus:border-blue-500/50 focus:outline-none transition-colors resize-none"
                      rows={4}
                      value={webhookPayload}
                      onChange={(e) => setWebhookPayload(e.target.value)}
                    />
                  </div>
                  <button
                    onClick={triggerWebhook}
                    disabled={triggeringWebhook || !webhookUrl.trim()}
                    className="w-full px-3 py-1.5 text-xs rounded-lg bg-purple-600 hover:bg-purple-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center justify-center gap-1"
                  >
                    {triggeringWebhook ? (
                      <>
                        <svg className="w-3 h-3 animate-spin" viewBox="0 0 24 24" fill="none">
                          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                        </svg>
                        Triggering...
                      </>
                    ) : (
                      <>
                        <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                        </svg>
                        Trigger Webhook
                      </>
                    )}
                  </button>
                  
                  {webhookResult && (
                    <div className={`p-2 rounded-lg text-xs ${webhookResult.error ? "bg-red-600/20 border border-red-500/30" : "bg-emerald-600/20 border border-emerald-500/30"}`}>
                      {webhookResult.error ? (
                        <p className="text-red-300">{webhookResult.error}</p>
                      ) : (
                        <>
                          <p className="text-emerald-300 font-medium">Status: {webhookResult.status}</p>
                          {webhookResult.body && (
                            <pre className="mt-1 text-white/60 whitespace-pre-wrap break-words max-h-20 overflow-y-auto">
                              {webhookResult.body.length > 200 ? `${webhookResult.body.substring(0, 200)}...` : webhookResult.body}
                            </pre>
                          )}
                        </>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
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
          {/* Timeout error banner */}
          {manualReloadNeeded && (
            <div className="mx-4 mt-4 rounded-xl backdrop-blur-xl bg-red-600/20 border border-red-500/30 p-3">
              <p className="text-sm text-red-200">
                ⚠️ Repository loading timed out after 10 seconds. Please reload the page to try again.
              </p>
            </div>
          )}
          
          {/* Warning banner when file tree is missing */}
          {repoContext && (!repoContext.files || repoContext.files.length === 0) && !manualReloadNeeded && (
            <div className="mx-4 mt-4 rounded-xl backdrop-blur-xl bg-amber-600/10 border border-amber-500/20 p-3">
              <p className="text-sm text-amber-200">
                ⚠️ The repository file list is not available. Responses may be incomplete.
              </p>
            </div>
          )}
          
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
                    
                    {/* Render progress indicator for direct commit mode */}
                    {refactorProgress && (
                      <div className="mt-3 p-3 rounded-lg bg-blue-900/20 border border-blue-500/30">
                        <div className="flex items-center gap-2">
                          <div className="animate-spin h-4 w-4 border-2 border-blue-400 border-t-transparent rounded-full"></div>
                          <span className="text-sm text-blue-300">
                            Committing {refactorProgress.current} of {refactorProgress.total} files...
                          </span>
                        </div>
                        <div className="mt-2 h-2 bg-blue-900/50 rounded-full overflow-hidden">
                          <div 
                            className="h-full bg-blue-500 transition-all duration-300"
                            style={{ width: `${(refactorProgress.current / refactorProgress.total) * 100}%` }}
                          ></div>
                        </div>
                      </div>
                    )}
                    
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
                          <div className="mt-2 flex gap-2">
                            <button
                              onClick={() => executeRefactor("workflow")}
                              disabled={executingRefactor}
                              className="px-3 py-1.5 text-xs rounded-lg bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 transition-colors"
                            >
                              {executingRefactor ? "Executing..." : "✓ Approve & Execute (Workflow)"}
                            </button>
                            <button
                              onClick={() => executeRefactor("direct")}
                              disabled={executingRefactor}
                              className="px-3 py-1.5 text-xs rounded-lg bg-blue-600 hover:bg-blue-500 disabled:opacity-50 transition-colors"
                            >
                              ⚡ Direct Commit (Fallback)
                            </button>
                          </div>
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

                    {/* Pending doc save buttons for README/OpenAPI */}
                    {msg.pendingDoc && (
                      <div className="mt-3 p-3 rounded-lg bg-white/5 border border-white/10">
                        <p className="text-xs font-semibold text-white/70 mb-2">
                          {msg.pendingDoc.type === "readme" ? "📄 README.md generated" : "📑 OpenAPI spec generated"}
                        </p>
                        <div className="flex gap-2">
                          <button
                            onClick={() => {
                              setPendingChanges(prev => [...prev, { 
                                path: msg.pendingDoc?.type === "readme" ? "README.md" : "openapi.yaml",
                                content: msg.pendingDoc?.content || "",
                                message: msg.pendingDoc?.type === "readme" ? "Generate README.md" : "Generate OpenAPI specification",
                                branch: "main",
                               }])
                            }}
                            className="px-3 py-1.5 text-xs rounded-lg bg-emerald-600 hover:bg-emerald-500 transition-colors"
                          >
                            Save as {msg.pendingDoc.type === "readme" ? "README.md" : "openapi.yaml"}
                          </button>
                          <button
                            onClick={() => {
                              setPendingChanges(prev => [...prev, { 
                                path: msg.pendingDoc?.type === "readme" ? "README.md" : "openapi.yaml",
                                content: msg.pendingDoc?.content || "",
                                message: msg.pendingDoc?.type === "readme" ? "Generate README.md" : "Generate OpenAPI specification",
                                branch: "main",
                               }])
                              setTimeout(() => commitChanges(false), 100)
                            }}
                            className="px-3 py-1.5 text-xs rounded-lg bg-blue-600 hover:bg-blue-500 transition-colors"
                          >
                            Apply & Commit
                          </button>
                        </div>
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
            <div ref={pendingChangeRef} className="mx-4 mb-4 rounded-xl backdrop-blur-xl bg-amber-600/10 border border-amber-500/20 p-4">
              <h3 className="text-sm font-semibold mb-2 flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
                Pending change to {pendingChange.path}
              </h3>
              <div className="text-xs text-white/60 mb-3">
                Commit message: <span className="text-white">{pendingChange.message}</span>
              </div>
              <div className="flex gap-2">
                <button
                  onClick={() => commitChanges(false)}
                  disabled={committing}
                  className="px-3 py-1.5 text-xs rounded-lg bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 transition-colors"
                >
                  {committing ? "Committing..." : "✓ Apply as new test file"}
                </button>
                <button
                  onClick={() => commitChanges(true)}
                  disabled={committing}
                  className="px-3 py-1.5 text-xs rounded-lg bg-purple-600 hover:bg-purple-500 disabled:opacity-50 transition-colors"
                >
                  {committing ? "Creating..." : "Create PR"}
                </button>
                <button
                  onClick={() => setPendingChanges([])}
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
                  className={`flex-1 px-4 py-3 rounded-xl bg-white/5 border border-white/10 focus:border-blue-500/50 focus:outline-none transition-colors placeholder:text-white/30 ${loadingRepo ? 'opacity-50 cursor-not-allowed' : ''}`}
                  value={chatInput}
                  onChange={(e) => setChatInput(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && sendMessage()}
                  disabled={loadingRepo}
                />
                <button
                  onClick={() => sendMessage()}
                  disabled={loading || !chatInput.trim() || loadingRepo}
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
