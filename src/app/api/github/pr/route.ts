import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { owner, repo, head, base, title, body: prBody } = await req.json();
  if (!owner || !repo || !head || !title) {
    return NextResponse.json({ error: "Missing fields" }, { status: 400 });
  }

  try {
    const response = await fetch(
      `https://api.github.com/repos/${owner}/${repo}/pulls`,
      {
        method: "POST",
        headers: {
          Authorization: `token ${session.accessToken}`,
          Accept: "application/vnd.github.v3+json",
        },
        body: JSON.stringify({
          title,
          head,
          base: base || "main",
          body: prBody || "",
        }),
      }
    );
    const data = await response.json();
    if (!response.ok) {
      return NextResponse.json({ error: data }, { status: response.status });
    }
    return NextResponse.json({ success: true, data });
  } catch (error) {
    return NextResponse.json({ error: "Internal error" }, { status: 500 });
  }
}
