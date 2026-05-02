import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { queryGroq } from "@/lib/groq";
import { rateLimit } from "@/lib/rate-limit";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken)
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  // Rate limiting: 10 requests per minute
  const identifier = `${session?.user?.email || "anonymous"}::${new Date().getMinutes()}`;
  if (!rateLimit(identifier, 10, 60000)) {
    return NextResponse.json({ error: "Too many requests. Please wait a moment." }, { status: 429 });
  }

  const { owner, repo, filePath, fileContent } = await req.json();
  if (!fileContent)
    return NextResponse.json({ error: "Missing file content" }, { status: 400 });

  const prompt = `You are an expert tester. Write a comprehensive test suite for the following code. Include edge cases and aim for 90%+ branch coverage. Use Jest for JavaScript/TypeScript or PyTest for Python.
File: ${filePath}
Code:
\`\`\`
${fileContent}
\`\`\`
Provide ONLY the test file content inside a single code block. No explanations.`;

  const text = await queryGroq("tests", [{ role: "user", content: prompt }]);
  const match = text.match(/```[\s\S]*?\n([\s\S]*?)\n```/);
  const testContent = match ? match[1] : text;

  return NextResponse.json({ testContent });
}
