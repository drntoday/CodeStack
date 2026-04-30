import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { owner, repo, path, content, message, branch, sha } = await req.json();
  if (!owner || !repo || !path || !content || !message) {
    return NextResponse.json({ error: "Missing fields" }, { status: 400 });
  }

  // If a sha is provided, we are updating an existing file (must match)
  // Otherwise we are creating a new file.
  const body: any = {
    message,
    content: Buffer.from(content).toString("base64"),
    branch,
  };
  if (sha) body.sha = sha;

  try {
    const response = await fetch(
      `https://api.github.com/repos/${owner}/${repo}/contents/${path}`,
      {
        method: "PUT",
        headers: {
          Authorization: `token ${session.accessToken}`,
          Accept: "application/vnd.github.v3+json",
        },
        body: JSON.stringify(body),
      }
    );
    const data = await response.json();
    if (!response.ok) {
      return NextResponse.json({ 
        error: typeof data.message === 'string' ? data.message : 'GitHub API error',
        details: data 
      }, { status: response.status });
    }
    return NextResponse.json({ success: true, data });
  } catch (error) {
    console.error("Commit error:", error);
    return NextResponse.json({ error: "Failed to commit changes" }, { status: 500 });
  }
}
