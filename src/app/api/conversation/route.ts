import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { getConversation, setConversation } from "@/lib/storage";

export async function GET(req: NextRequest) {
  const session = await auth();
  if (!session?.user) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { searchParams } = new URL(req.url);
  const owner = searchParams.get("owner");
  const repo = searchParams.get("repo");
  if (!owner || !repo) return NextResponse.json({ error: "Missing owner/repo" }, { status: 400 });

  const key = `conversation:${owner}/${repo}`;
  const messages = await getConversation(key);
  return NextResponse.json({ messages });
}

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.user) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo, messages } = await req.json();
  if (!owner || !repo || !Array.isArray(messages)) {
    return NextResponse.json({ error: "Missing owner/repo/messages" }, { status: 400 });
  }

  const key = `conversation:${owner}/${repo}`;
  // Keep only the last 100 messages to avoid unbounded storage
  const recent = messages.slice(-100);
  await setConversation(key, recent);
  return NextResponse.json({ success: true });
}
