import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { GoogleGenerativeAI } from "@google/generative-ai";

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY!);
const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });

// Build a quick index: list of { path, summary } where summary contains function/class definitions and top-level comments
async function buildIndex(owner: string, repo: string, token: string) {
  const treeRes = await fetch(`https://api.github.com/repos/${owner}/${repo}/git/trees/main?recursive=1`, {
    headers: { Authorization: `token ${token}` },
  });
  const treeData = await treeRes.json();
  const codeFiles = treeData.tree.filter((item: any) => item.type === "blob" && /\.(ts|js|tsx|jsx|py)$/i.test(item.path)).map((item: any) => item.path);

  const index: { path: string; summary: string }[] = [];
  for (const file of codeFiles.slice(0, 200)) { // larger cap, but careful with tokens
    const res = await fetch(`https://api.github.com/repos/${owner}/${repo}/contents/${file}`, {
      headers: { Authorization: `token ${token}` },
    });
    if (res.ok) {
      const data = await res.json();
      const content = data.content ? Buffer.from(data.content, "base64").toString("utf-8") : "";
      // Extract lines that look like function/class definitions or comments
      const lines = content.split("\n");
      const important = lines.filter(line => /^\s*(export\s+)?(async\s+)?function|class\s+\w+|const\s+\w+\s*=\s*(async\s*)?\(/.test(line) || line.trim().startsWith("//") || line.trim().startsWith("/*")).join("\n").slice(0, 600);
      index.push({ path: file, summary: important });
    }
  }
  return index;
}

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo, query } = await req.json();
  if (!owner || !repo || !query) return NextResponse.json({ error: "Missing fields" }, { status: 400 });

  // Use a simple cache in memory (this won't persist across serverless cold starts, but okay for demo)
  const index = await buildIndex(owner, repo, session.accessToken);

  // Send the index + query to Gemini and ask to return the file paths that answer the question
  const prompt = `You are given a codebase index (file paths and key declarations). Answer the question: "${query}". Output a JSON array of relevant file paths, e.g., ["src/auth.ts", "src/login.ts"]. Only JSON, no other text.\n\nIndex:\n${JSON.stringify(index, null, 2)}`;

  const result = await model.generateContent(prompt);
  const response = await result.response;
  const text = response.text();
  let files: string[];
  try {
    files = JSON.parse(text);
  } catch {
    files = [];
  }

  return NextResponse.json({ files });
}
