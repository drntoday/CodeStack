import { GoogleGenerativeAI } from "@google/generative-ai"
import { rateLimit } from "@/lib/rate-limit"

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY!)

export async function POST(req: Request) {
  // Rate limiting: 14 requests per minute (stay under Gemini's 15/min free tier limit)
  if (!rateLimit("gemini-chat", 14, 60000)) {
    return new Response(JSON.stringify({ error: "Rate limit exceeded. Please wait a minute." }), {
      status: 429,
      headers: { "Content-Type": "application/json" },
    })
  }

  try {
    const { messages } = await req.json()

    if (!messages || !Array.isArray(messages)) {
      return new Response(JSON.stringify({ error: "Invalid messages format" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      })
    }

    const apiKey = process.env.GEMINI_API_KEY!
    if (!apiKey) {
      return new Response(JSON.stringify({ error: "API key not configured" }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      })
    }

    const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" })

    // Convert messages to Gemini format
    const lastUserMsg = messages[messages.length - 1].content;
    const prompt = `You are an AI coding assistant. Provide helpful answers. If asked to modify the content of a file, output ONLY the new full content of the file inside a single code block. Do not include any explanations before or after the code block.\n\n${messages.map((m: { role: string; content: string }) => 
      `${m.role === 'user' ? 'User' : 'Assistant'}: ${m.content}`
    ).join('\n')}`;

    const result = await model.generateContent(prompt)
    const response = await result.response
    const text = response.text()

    return new Response(JSON.stringify({ text }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    })
  } catch (error) {
    console.error("Gemini API error:", error)
    return new Response(JSON.stringify({ error: "AI service unavailable. Check quota or API key." }), {
      status: 503,
      headers: { "Content-Type": "application/json" },
    })
  }
}
