import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { GoogleGenerativeAI } from "@google/generative-ai";

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY!);
const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo } = await req.json();
  // Fetch all API route files under src/app/api/ (the ones we have)
  const routes = [
    "src/app/api/chat/route.ts",
    "src/app/api/github/file/route.ts",
    "src/app/api/github/tree/route.ts",
    "src/app/api/github/commit/route.ts",
    "src/app/api/github/pr/route.ts",
    "src/app/api/refactor/plan/route.ts",
    "src/app/api/refactor/execute/route.ts",
    "src/app/api/tests/generate/route.ts",
    "src/app/api/audit/route.ts",
    "src/app/api/architecture/ask/route.ts",
    "src/app/api/docs/generate-readme/route.ts", // itself!
    "src/app/api/webhooks/workflow-failed/route.ts"
  ];
  let routeCode = "";
  for (const route of routes) {
    const res = await fetch(`https://api.github.com/repos/${owner}/${repo}/contents/${route}`, {
      headers: { Authorization: `token ${session.accessToken}` },
    });
    if (res.ok) {
      const data = await res.json();
      if (data.content) {
        routeCode += `=== ${route} ===\n${Buffer.from(data.content, "base64").toString("utf-8")}\n\n`;
      }
    }
  }

  const prompt = `Analyze the following Next.js API route handlers and generate an OpenAPI 3.0 specification (YAML) for all endpoints. Include request bodies, query parameters, and responses. Use sensible summaries.\n\n${routeCode}`;

  const result = await model.generateContent(prompt);
  const response = await result.response;
  const openapi = response.text();

  return NextResponse.json({ openapi });
}
