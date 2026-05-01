import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import * as actions from "@/lib/actions";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo, question, files } = await req.json();
  if (!owner || !repo || !question) return NextResponse.json({ error: "Missing fields" }, { status: 400 });

  if (!files || files.length === 0) {
    return NextResponse.json({ error: "No file list available. Please load the repository first." }, { status: 400 });
  }

  const answer = await actions.answerArchitecture(owner, repo, question, session.accessToken, files);

  return NextResponse.json({ answer });
}
