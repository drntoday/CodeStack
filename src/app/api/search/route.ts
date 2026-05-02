import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import * as actions from "@/lib/actions";
import { rateLimit } from "@/lib/rate-limit";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  // Rate limiting: 10 requests per minute
  const identifier = `${session?.user?.email || "anonymous"}::${new Date().getMinutes()}`;
  if (!(await rateLimit(identifier, 10, 60000))) {
    return NextResponse.json({ error: "Too many requests. Please wait a moment." }, { status: 429 });
  }

  const { owner, repo, query, files } = await req.json();
  if (!owner || !repo || !query) return NextResponse.json({ error: "Missing fields" }, { status: 400 });

  if (!files || files.length === 0) {
    return NextResponse.json({ error: "No file list available. Please load the repository first." }, { status: 400 });
  }

  const matchedFiles = await actions.searchCode(owner, repo, query, session.accessToken, files);

  return NextResponse.json({ files: matchedFiles });
}
