import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { GoogleGenerativeAI } from "@google/generative-ai";
import { rateLimit } from "@/lib/rate-limit";

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY!);
const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });

export async function POST(req: NextRequest) {
  // Rate limiting: 10 requests per minute
  if (!rateLimit("audit", 10, 60000)) {
    return NextResponse.json({ error: "Rate limit exceeded. Please wait a minute." }, { status: 429 });
  }

  const session = await auth();
  if (!session?.accessToken)
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo, commitSha } = await req.json();
  if (!owner || !repo || !commitSha)
    return NextResponse.json({ error: "Missing fields" }, { status: 400 });

  // Fetch diff of the commit
  const diffRes = await fetch(
    `https://api.github.com/repos/${owner}/${repo}/commits/${commitSha}`,
    {
      headers: {
        Authorization: `token ${session.accessToken}`,
        Accept: "application/vnd.github.v3.diff",
      },
    }
  );
  if (!diffRes.ok)
    return NextResponse.json({ error: "Commit not found" }, { status: 404 });
  const diffText = await diffRes.text();

  const prompt = `Analyze this diff for security issues (secrets, API keys, tokens, SQL injection, XSS). Use a bullet list: [- severity] file:line - description. If clean, say "No issues found."\nDiff:\n\`\`\`diff\n${diffText}\n\`\`\``;

  const result = await model.generateContent(prompt);
  const response = await result.response;
  const report = response.text();

  return NextResponse.json({ report });
}
