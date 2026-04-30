import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken)
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo, plan } = await req.json();
  if (!owner || !repo || !plan)
    return NextResponse.json({ error: "Missing fields" }, { status: 400 });

  // Trigger the GitHub Action
  const dispatchRes = await fetch(
    `https://api.github.com/repos/${owner}/${repo}/actions/workflows/refactor.yml/dispatches`,
    {
      method: "POST",
      headers: {
        Authorization: `token ${session.accessToken}`,
        Accept: "application/vnd.github.v3+json",
      },
      body: JSON.stringify({
        ref: "main",
        inputs: {
          plan: JSON.stringify(plan),
          owner,
          repo,
          base_branch: "main",
        },
      }),
    }
  );
  if (!dispatchRes.ok) {
    const errText = await dispatchRes.text();
    return NextResponse.json({ error: "Dispatch failed", details: errText }, { status: 500 });
  }
  return NextResponse.json({ success: true, message: "Refactor workflow triggered. Check Actions tab." });
}
