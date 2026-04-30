import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { queryGroq } from "@/lib/groq";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken)
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo, prompt } = await req.json();
  if (!owner || !repo || !prompt)
    return NextResponse.json({ error: "Missing fields" }, { status: 400 });

  // Fetch the repository tree
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

  // Ask Groq to propose a plan
  const planPrompt = `Repository files:\n${files.join("\n")}\n\nTask: ${prompt}\n\nProduce a JSON array of objects with "file", "instruction", and "reason". Only JSON.`;
  const responseText = await queryGroq("refactor", [
    { role: "user", content: planPrompt },
  ]);

  try {
    const plan = JSON.parse(responseText);
    return NextResponse.json({ plan });
  } catch {
    return NextResponse.json({ error: "Invalid plan generated", raw: responseText }, { status: 500 });
  }
}
