import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { queryGroq } from "@/lib/groq";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo, question } = await req.json();
  if (!owner || !repo || !question) return NextResponse.json({ error: "Missing fields" }, { status: 400 });

  // Fetch the whole tree
  const treeRes = await fetch(`https://api.github.com/repos/${owner}/${repo}/git/trees/main?recursive=1`, {
    headers: { Authorization: `token ${session.accessToken}` },
  });
  if (!treeRes.ok) return NextResponse.json({ error: "Cannot fetch repo tree" }, { status: 500 });
  const treeData = await treeRes.json();
  const files = treeData.tree.filter((item: any) => item.type === "blob" && /\.(ts|js|py|go|java|rb)$/i.test(item.path)).map((item: any) => item.path);

  const prompt = `You are an expert software architect. Below is a list of repository files. Answer the user question by tracing the relevant imports/exports based on file paths alone. Provide a detailed, high-level explanation with file paths.

Repository files:
${files.slice(0, 30).join("\n")}

Question: ${question}`;

  const answer = await queryGroq("architecture", [{ role: "user", content: prompt }]);

  return NextResponse.json({ answer });
}
