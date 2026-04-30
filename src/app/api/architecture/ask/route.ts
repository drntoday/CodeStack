import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { GoogleGenerativeAI } from "@google/generative-ai";

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY!);
const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });

// Simple function to extract import/export relationships from a file content
function extractImportsExports(filePath: string, content: string) {
  const imports: string[] = [];
  const exports: string[] = [];
  const importRegex = /import\s+.*?\s+from\s+['"](.+?)['"]/g;
  const exportRegex = /export\s+(?:default\s+)?(?:class|function|const|let|var)\s+(\w+)/g;
  let match;
  while ((match = importRegex.exec(content)) !== null) {
    imports.push(match[1]);
  }
  while ((match = exportRegex.exec(content)) !== null) {
    exports.push(match[1]);
  }
  return { file: filePath, imports, exports };
}

export async function POST(req: NextRequest) {
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

  // Build a summary: for each file, fetch its content and extract imports/exports (limit to N files if too large)
  const fileSummaries: any[] = [];
  for (const file of files.slice(0, 100)) { // cap at 100 files to stay under token limit
    const fileRes = await fetch(`https://api.github.com/repos/${owner}/${repo}/contents/${file}`, {
      headers: { Authorization: `token ${session.accessToken}` },
    });
    if (fileRes.ok) {
      const fileData = await fileRes.json();
      const content = fileData.content ? Buffer.from(fileData.content, "base64").toString("utf-8") : "";
      fileSummaries.push(extractImportsExports(file, content));
    }
  }

  const prompt = `You are an expert software architect. Below is a summary of a codebase (file paths, their imports and exports). Answer the user's question by tracing the relevant imports/exports. Provide a detailed, high-level explanation with file paths.\n\nRepository files summary:\n${JSON.stringify(fileSummaries, null, 2)}\n\nQuestion: ${question}`;

  const result = await model.generateContent(prompt);
  const response = await result.response;
  const answer = response.text();

  return NextResponse.json({ answer });
}
