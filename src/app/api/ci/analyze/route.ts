import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { GoogleGenerativeAI } from "@google/generative-ai";

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY!);
const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken)
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo, runId } = await req.json();
  if (!owner || !repo || !runId)
    return NextResponse.json({ error: "Missing fields" }, { status: 400 });

  // Fetch logs (first 5000 chars to stay within token limits)
  const logsRes = await fetch(
    `https://api.github.com/repos/${owner}/${repo}/actions/runs/${runId}/logs`,
    { headers: { Authorization: `token ${session.accessToken}` } }
  );
  if (!logsRes.ok)
    return NextResponse.json({ error: "Cannot fetch logs" }, { status: 500 });
  const logText = await logsRes.text();
  const logSnippet = logText.slice(0, 5000);

  // Also fetch basic run info
  const runRes = await fetch(
    `https://api.github.com/repos/${owner}/${repo}/actions/runs/${runId}`,
    { headers: { Authorization: `token ${session.accessToken}` } }
  );
  const runData = runRes.ok ? await runRes.json() : null;

  const prompt = `You are a DevOps expert. A GitHub Actions workflow has failed. Here are the details:
- Run ID: ${runId}
- Status: ${runData?.conclusion || "unknown"}
- Log snippet (first 5000 chars):
\`\`\`
${logSnippet}
\`\`\`
Analyze:
1. Why did it fail? (explain in plain English)
2. How can we fix it?
3. Any tips to speed up the CI pipeline (caching, parallelism, etc.)?
Format your answer in clear bullet points.`;

  const result = await model.generateContent(prompt);
  const response = await result.response;
  const analysis = response.text();

  return NextResponse.json({ analysis });
}
