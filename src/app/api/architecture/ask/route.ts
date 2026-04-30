import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { GoogleGenerativeAI } from "@google/generative-ai";
import { rateLimit } from "@/lib/rate-limit";

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY!);
const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });

export async function POST(req: NextRequest) {
  // Rate limiting: 5 requests per minute (architecture analysis is expensive)
  if (!rateLimit("architecture-ask", 5, 60000)) {
    return NextResponse.json({ error: "Rate limit exceeded. Please wait a minute." }, { status: 429 });
  }

  const session = await auth();
  if (!session?.accessToken) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo, question } = await req.json();
  if (!owner || !repo || !question) return NextResponse.json({ error: "Missing fields" }, { status: 400 });

  // Fetch the whole tree (cached if possible, but for MVP fetch fresh)
  const treeRes = await fetch(`https://api.github.com/repos/${owner}/${repo}/git/trees/main?recursive=1`, {
    headers: { Authorization: `token ${session.accessToken}` },
  });
  if (!treeRes.ok) return NextResponse.json({ error: "Cannot fetch repo tree" }, { status: 500 });
  const treeData = await treeRes.json();
  const files = treeData.tree.filter((item: any) => item.type === "blob" && /\.(ts|js|py|go|java|rb)$/i.test(item.path)).map((item: any) => item.path);

  // Use only file paths (no content) to avoid timeout - Gemini can trace architecture from filenames
  const prompt = `You are an expert software architect. Below is a list of repository files. Answer the user's question by tracing the relevant imports/exports based on file paths alone. Provide a detailed, high-level explanation with file paths.\n\nRepository files:\n${files.slice(0, 30).join('\n')}\n\nQuestion: ${question}`;

  const result = await model.generateContent(prompt);
  const response = await result.response;
  const answer = response.text();

  return NextResponse.json({ answer });
}
