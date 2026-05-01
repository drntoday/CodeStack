import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { executeRefactorDirect } from "@/lib/actions";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }
  const { owner, repo, plan } = await req.json();
  if (!owner || !repo || !plan) {
    return NextResponse.json({ error: "Missing fields" }, { status: 400 });
  }
  const result = await executeRefactorDirect(owner, repo, plan, session.accessToken);
  return NextResponse.json(result);
}
