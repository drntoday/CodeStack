import { NextRequest, NextResponse } from "next/server";
import { queryGroq } from "@/lib/groq";

export async function POST(req: NextRequest) {
  try {
    const { messages } = await req.json();
    const systemPrompt = {
      role: "system",
      content: "You are an expert coding assistant. Answer helpfully.",
    };
    const allMessages = [systemPrompt, ...messages];

    const response = await queryGroq("chat", allMessages);
    return new Response(JSON.stringify({ text: response }), {
      headers: { "Content-Type": "application/json" },
    });
  } catch (error: any) {
    console.error("Groq error:", error);
    const message = error?.message || "AI service unavailable.";
    return NextResponse.json({ error: message }, { status: 503 });
  }
}
