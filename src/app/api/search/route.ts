import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { queryGroq } from "@/lib/groq";

// Build a quick index: list of { path, summary }
async function buildIndex(owner: string, repo: string, token: string) {
  const treeRes = await fetch(`https://api.github.com/repos/${owner}/${repo}/git/trees/main?recursive=1`, {
    headers: { Authorization: `token ${token}` },
  });
  const treeData = await treeRes.json();
  const codeFiles = treeData.tree.filter((item: any) => item.type === "blob" && /\.(ts|js|tsx|jsx|py)$/i.test(item.path)).map((item: any) => item.path);

  const index: { path: string; summary: string }[] = [];
  let totalChars = 0;
  for (const file of codeFiles.slice(0, 50)) {
    if (totalChars > 3000) break;
    
    const res = await fetch(`https://api.github.com/repos/${owner}/${repo}/contents/${file}`, {
      headers: { Authorization: `token ${token}` },
    });
    if (res.ok) {
      const data = await res.json();
      const content = data.content ? Buffer.from(data.content, "base64").toString("utf-8") : "";
      const lines = content.split("\n");
      const important = lines.filter(line => /^\s*(export\s+)?(async\s+)?function|class\s+\w+|const\s+\w+\s*=\s*(async\s*)?\(/.test(line) || line.trim().startsWith("//") || line.trim().startsWith("/*")).join("\n").slice(0, 600);
      index.push({ path: file, summary: important });
      totalChars += important.length;
    }
  }
  return index;
}

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo, query } = await req.json();
  if (!owner || !repo || !query) return NextResponse.json({ error: "Missing fields" }, { status: 400 });

  const index = await buildIndex(owner, repo, session.accessToken);

  const prompt = `You are given a codebase index (file paths and key declarations). Answer the question: "${query}". Output a JSON array of relevant file paths, e.g., ["src/auth.ts", "src/login.ts"]. Only JSON, no other text.

Index:
${JSON.stringify(index, null, 2)}`;

  const text = await queryGroq("search", [{ role: "user", content: prompt }]);
  let files: string[];
  try {
    files = JSON.parse(text);
  } catch {
    files = [];
  }

  return NextResponse.json({ files });
}
