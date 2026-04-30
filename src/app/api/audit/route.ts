import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { queryGroq } from "@/lib/groq";

export async function POST(req: NextRequest) {
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

  const prompt = `Analyze this diff for security issues (secrets, API keys, tokens, SQL injection, XSS). Use a bullet list: [- severity] file:line - description. If clean, say "No issues found."
Diff:
\`\`\`diff
${diffText}
\`\`\``;

  const report = await queryGroq("audit", [{ role: "user", content: prompt }]);

  return NextResponse.json({ report });
}
