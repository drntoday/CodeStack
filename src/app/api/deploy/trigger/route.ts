import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo, workflow_id, ref } = await req.json();
  if (!owner || !repo) return NextResponse.json({ error: "Missing fields" }, { status: 400 });

  const url = workflow_id
    ? `https://api.github.com/repos/${owner}/${repo}/actions/workflows/${workflow_id}/dispatches`
    : `https://api.github.com/repos/${owner}/${repo}/actions/workflows/deploy.yml/dispatches`;

  const dispatchRes = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `token ${session.accessToken}`,
      Accept: "application/vnd.github.v3+json",
    },
    body: JSON.stringify({ ref: ref || "main" }),
  });

  if (!dispatchRes.ok) {
    const errText = await dispatchRes.text();
    return NextResponse.json({ error: "Dispatch failed", details: errText }, { status: 500 });
  }
  return NextResponse.json({ success: true, message: "Deployment triggered." });
}
