import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { GoogleGenerativeAI } from "@google/generative-ai";

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY!);
const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo } = await req.json();
  if (!owner || !repo) return NextResponse.json({ error: "Missing fields" }, { status: 400 });

  // Fetch top-level files: package.json, .env.example, main entry points, README template etc.
  const files = ["package.json", "tsconfig.json", "eslint.config.mjs", "src/app/layout.tsx", "src/app/api/chat/route.ts", "src/auth.ts"];
  let collected = "";
  for (const file of files) {
    const res = await fetch(`https://api.github.com/repos/${owner}/${repo}/contents/${file}`, {
      headers: { Authorization: `token ${session.accessToken}` },
    });
    if (res.ok) {
      const data = await res.json();
      if (data.content) {
        collected += `=== ${file} ===\n${Buffer.from(data.content, "base64").toString("utf-8")}\n\n`;
      }
    }
  }

  const prompt = `You are a technical writer. Given the following files from a Next.js project, produce a thorough README.md. Include:
- Project title and description
- Features
- Prerequisites
- Installation
- Environment variables
- Usage (how to run, build, deploy)
- Project structure overview
Use proper Markdown formatting.\n\n${collected}`;

  const result = await model.generateContent(prompt);
  const response = await result.response;
  const readme = response.text();

  return NextResponse.json({ readme });
}
