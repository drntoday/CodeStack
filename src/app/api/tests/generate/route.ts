import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { GoogleGenerativeAI } from "@google/generative-ai";
import { rateLimit } from "@/lib/rate-limit";

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY!);
const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });

export async function POST(req: NextRequest) {
  // Rate limiting: 8 requests per minute (test generation is expensive)
  if (!rateLimit("tests-generate", 8, 60000)) {
    return NextResponse.json({ error: "Rate limit exceeded. Please wait a minute." }, { status: 429 });
  }

  const session = await auth();
  if (!session?.accessToken)
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo, filePath, fileContent } = await req.json();
  if (!fileContent)
    return NextResponse.json({ error: "Missing file content" }, { status: 400 });

  const prompt = `You are an expert tester. Write a comprehensive test suite for the following code. Include edge cases and aim for 90%+ branch coverage. Use Jest for JavaScript/TypeScript or PyTest for Python.
File: ${filePath}
Code:
\`\`\`\n${fileContent}\n\`\`\`
Provide ONLY the test file content inside a single code block. No explanations.`;

  const result = await model.generateContent(prompt);
  const response = await result.response;
  const text = response.text();
  const match = text.match(/```[\s\S]*?\n([\s\S]*?)\n```/);
  const testContent = match ? match[1] : text;

  return NextResponse.json({ testContent });
}
