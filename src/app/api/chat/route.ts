import { GoogleGenerativeAI } from "@google/generative-ai"

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY!)

export async function POST(req: Request) {
  try {
    const { messages } = await req.json()

    if (!messages || !Array.isArray(messages)) {
      return new Response(JSON.stringify({ error: "Invalid messages format" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      })
    }

    // Simple rate limiting check - in production use a proper store
    const apiKey = process.env.GEMINI_API_KEY!
    if (!apiKey) {
      return new Response(JSON.stringify({ error: "API key not configured" }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      })
    }

    const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" })

    // Convert messages to Gemini format
    const prompt = messages.map((m: { role: string; content: string }) => 
      `${m.role === 'user' ? 'User' : 'Assistant'}: ${m.content}`
    ).join('\n')

    const result = await model.generateContent(prompt)
    const response = await result.response
    const text = response.text()

    return new Response(JSON.stringify({ response: text }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    })
  } catch (error) {
    console.error("Gemini API error:", error)
    return new Response(JSON.stringify({ error: "Failed to get response from Gemini" }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    })
  }
}
