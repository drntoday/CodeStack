import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { GoogleGenerativeAI } from "@google/generative-ai";
import { rateLimit } from "@/lib/rate-limit";

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY!);
const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });

export async function POST(req: NextRequest) {
  // Rate limiting: 10 requests per minute
  if (!rateLimit("refactor-plan", 10, 60000)) {
    return NextResponse.json({ error: "Rate limit exceeded. Please wait a minute." }, { status: 429 });
  }

  const session = await auth();
  if (!session?.accessToken)
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo, prompt } = await req.json();
  if (!owner || !repo || !prompt)
    return NextResponse.json({ error: "Missing fields" }, { status: 400 });

  // Fetch the repository tree (file list only, no contents)
  const treeRes = await fetch(
    `https://api.github.com/repos/${owner}/${repo}/git/trees/main?recursive=1`,
    {
      headers: {
        Authorization: `token ${session.accessToken}`,
        Accept: "application/vnd.github.v3+json",
      },
    }
  );
  if (!treeRes.ok)
    return NextResponse.json({ error: "Cannot fetch tree" }, { status: 500 });
  const treeData = await treeRes.json();
  const files = treeData.tree
    .filter((item: any) => item.type === "blob" && !item.path.includes("node_modules"))
    .map((item: any) => item.path);

  // Ask Gemini to propose a plan
  const planPrompt = `You are given a repository with the following file list:\n${files.join("\n")}\n\nTask: ${prompt}\n\nProduce a JSON array of objects. Each object must have "file" (the path), "instruction" (a detailed instruction for what to change in that file), and "reason" (one sentence why this change is needed). Return ONLY the JSON array. Do not include any other text.`;
  const result = await model.generateContent(planPrompt);
  const responseText = (await result.response).text();

  try {
    const plan = JSON.parse(responseText);
    return NextResponse.json({ plan });
  } catch {
    return NextResponse.json({ error: "Invalid plan generated", raw: responseText }, { status: 500 });
  }
}
