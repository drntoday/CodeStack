"use client"

import { useSession, signOut } from "next-auth/react"
import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"

interface Message {
  role: string
  content: string
}

export default function DashboardPage() {
  const { data: session, status } = useSession()
  const router = useRouter()
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState("")
  const [isLoading, setIsLoading] = useState(false)

  useEffect(() => {
    if (status === "unauthenticated") {
      router.push("/")
    }
  }, [status, router])

  if (status === "loading") {
    return (
      <div className="min-h-screen bg-gray-900 text-white flex items-center justify-center">
        <p>Loading...</p>
      </div>
    )
  }

  if (!session) {
    return null
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!input.trim() || isLoading) return

    const userMessage: Message = { role: "user", content: input.trim() }
    const newMessages = [...messages, userMessage]
    setMessages(newMessages)
    setInput("")
    setIsLoading(true)

    try {
      const res = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ messages: newMessages }),
      })

      const data = await res.json()
      if (data.response) {
        setMessages([...newMessages, { role: "assistant", content: data.response }])
      } else if (data.error) {
        setMessages([...newMessages, { role: "assistant", content: `Error: ${data.error}` }])
      }
    } catch (error) {
      setMessages([...newMessages, { role: "assistant", content: "Failed to get response" }])
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-900 text-white">
      <header className="border-b border-gray-700 p-4">
        <div className="max-w-4xl mx-auto flex justify-between items-center">
          <h1 className="text-xl font-bold">Code Stack Control Panel</h1>
          <div className="flex items-center gap-4">
            <span className="text-sm text-gray-400">{session.user?.email}</span>
            <button
              onClick={() => signOut()}
              className="px-4 py-2 bg-red-600 hover:bg-red-700 rounded text-sm"
            >
              Sign Out
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-4xl mx-auto p-4">
        <div className="bg-gray-800 rounded-lg h-[60vh] overflow-y-auto mb-4 p-4">
          {messages.length === 0 ? (
            <p className="text-gray-400 text-center mt-20">
              Start a conversation with Gemini. Ask coding questions!
            </p>
          ) : (
            messages.map((msg, idx) => (
              <div
                key={idx}
                className={`mb-4 ${msg.role === "user" ? "text-right" : "text-left"}`}
              >
                <div
                  className={`inline-block px-4 py-2 rounded-lg max-w-[80%] ${
                    msg.role === "user"
                      ? "bg-blue-600"
                      : "bg-gray-700"
                  }`}
                >
                  <p className="text-sm whitespace-pre-wrap">{msg.content}</p>
                </div>
              </div>
            ))
          )}
          {isLoading && (
            <div className="text-left mb-4">
              <div className="inline-block px-4 py-2 bg-gray-700 rounded-lg">
                <p className="text-sm text-gray-400">Thinking...</p>
              </div>
            </div>
          )}
        </div>

        <form onSubmit={handleSubmit} className="flex gap-2">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask Gemini a coding question..."
            className="flex-1 px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg focus:outline-none focus:border-blue-500"
            disabled={isLoading}
          />
          <button
            type="submit"
            disabled={isLoading || !input.trim()}
            className="px-6 py-3 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-600 rounded-lg font-medium"
          >
            Send
          </button>
        </form>
      </main>
    </div>
  )
}
