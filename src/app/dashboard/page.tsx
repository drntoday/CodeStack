"use client"

import { useSession, signOut } from "next-auth/react"
import { useState } from "react"

export default function DashboardPage() {
  const { data: session } = useSession()
  const [repoInput, setRepoInput] = useState("")
  const [owner, setOwner] = useState("")
  const [repo, setRepo] = useState("")
  const [files, setFiles] = useState<string[]>([])
  const [selectedFile, setSelectedFile] = useState("")
  const [fileContent, setFileContent] = useState("")
  const [chatInput, setChatInput] = useState("")
  const [messages, setMessages] = useState<{ role: string; content: string }[]>([])
  const [loading, setLoading] = useState(false)

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
    }
  }

  const sendMessage = async () => {
    if (!chatInput.trim()) return
    const userMsg = { role: "user", content: chatInput }
    setMessages((prev) => [...prev, userMsg])
    setChatInput("")
    setLoading(true)

    // Prepare context about the selected file
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
    if (data.response) {
      setMessages((prev) => [...prev, { role: "assistant", content: data.response }])
    }
    setLoading(false)
  }

  if (!session) return null

  return (
    <div className="min-h-screen p-4 bg-gray-900 text-white">
      <div className="flex justify-between items-center mb-4">
        <h1 className="text-2xl font-bold">Code Stack</h1>
        <button onClick={() => signOut()} className="px-4 py-2 bg-red-500 text-white rounded">
          Sign Out
        </button>
      </div>

      <div className="mb-4 flex gap-2">
        <input
          type="text"
          placeholder="owner/repo"
          className="border p-2 rounded w-64 text-black"
          value={repoInput}
          onChange={(e) => setRepoInput(e.target.value)}
        />
        <button onClick={loadFiles} className="px-4 py-2 bg-blue-500 text-white rounded">
          Load Repo
        </button>
      </div>

      {files.length > 0 && (
        <div className="flex gap-4 mb-4">
          <select
            className="border p-2 rounded w-64 text-black"
            value={selectedFile}
            onChange={(e) => selectFile(e.target.value)}
          >
            <option value="">Select a file</option>
            {files.map((f) => (
              <option key={f} value={f}>{f}</option>
            ))}
          </select>
          {selectedFile && <span className="text-sm self-center">Loaded: {selectedFile}</span>}
        </div>
      )}

      <div className="border rounded h-96 overflow-y-scroll p-4 mb-4 bg-gray-800">
        {messages.map((m, i) => (
          <div key={i} className={`mb-2 ${m.role === "user" ? "text-right" : "text-left"}`}>
            <span className={`inline-block p-2 rounded ${m.role === "user" ? "bg-blue-600" : "bg-gray-700"}`}>
              {m.content}
            </span>
          </div>
        ))}
        {loading && <div className="text-center">Thinking...</div>}
      </div>

      <div className="flex gap-2">
        <input
          type="text"
          className="border p-2 rounded flex-1 text-black"
          placeholder="Ask something about the repo or code..."
          value={chatInput}
          onChange={(e) => setChatInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && sendMessage()}
        />
        <button onClick={sendMessage} className="px-4 py-2 bg-green-500 text-white rounded">
          Send
        </button>
      </div>
    </div>
  )
}
