import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import * as actions from "@/lib/actions";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken)
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo, prompt, files } = await req.json();
  if (!owner || !repo || !prompt)
    return NextResponse.json({ error: "Missing fields" }, { status: 400 });

  if (!files || files.length === 0) {
    return NextResponse.json({ error: "The repository contains no files." }, { status: 400 });
  }

  try {
    const plan = await actions.generateRefactorPlan(owner, repo, prompt, session.accessToken, files);
    return NextResponse.json({ plan });
  } catch (error: any) {
    return NextResponse.json({ error: error.message || "Invalid plan generated" }, { status: 500 });
  }
}
