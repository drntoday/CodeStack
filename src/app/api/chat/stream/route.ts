import { NextRequest } from "next/server";
import Groq from "groq-sdk";

export const runtime = "edge";

export async function POST(req: NextRequest) {
  const { messages, model = "llama-3.1-8b-instant" } = await req.json();
  const groq = new Groq({ apiKey: process.env.GROQ_API_KEY });
  const stream = await groq.chat.completions.create({
    model,
    messages,
    stream: true,
  });

  const encoder = new TextEncoder();
  return new Response(
    new ReadableStream({
      async start(controller) {
        for await (const chunk of stream) {
          const text = chunk.choices[0]?.delta?.content || "";
          controller.enqueue(encoder.encode(text));
        }
        controller.close();
      },
    }),
    { headers: { "Content-Type": "text/plain; charset=UTF-8" } }
  );
}
