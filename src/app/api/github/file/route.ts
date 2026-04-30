import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { owner, repo, path } = await req.json();
  if (!owner || !repo || !path) {
    return NextResponse.json({ error: "Missing fields" }, { status: 400 });
  }

  try {
    const response = await fetch(
      `https://api.github.com/repos/${owner}/${repo}/contents/${path}`,
      {
        headers: {
          Authorization: `token ${session.accessToken}`,
          Accept: "application/vnd.github.v3+json",
        },
      }
    );
    if (!response.ok) {
      return NextResponse.json(
        { error: "GitHub API error", status: response.status },
        { status: response.status }
      );
    }
    const data = await response.json();
    // GitHub returns content Base64 encoded
    const content = data.content
      ? Buffer.from(data.content, "base64").toString("utf-8")
      : "";
    return NextResponse.json({ content, path, sha: data.sha });
  } catch (error) {
    return NextResponse.json({ error: "Internal error" }, { status: 500 });
  }
}
