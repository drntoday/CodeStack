import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { executeRefactorDirect } from "@/lib/actions";
import { rateLimit } from "@/lib/rate-limit";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  // Rate limiting: 10 requests per minute
  const identifier = `${session?.user?.email || "anonymous"}::${new Date().getMinutes()}`;
  if (!rateLimit(identifier, 10, 60000)) {
    return NextResponse.json({ error: "Too many requests. Please wait a moment." }, { status: 429 });
  }

  const { owner, repo, plan } = await req.json();
  if (!owner || !repo || !plan) {
    return NextResponse.json({ error: "Missing fields" }, { status: 400 });
  }
  const result = await executeRefactorDirect(owner, repo, plan, session.accessToken);
  return NextResponse.json(result);
}
