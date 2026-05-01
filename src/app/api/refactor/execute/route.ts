import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { executeRefactor } from "@/lib/actions";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken)
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo, plan, mode = "workflow" } = await req.json();
  if (!owner || !repo || !plan)
    return NextResponse.json({ error: "Missing fields" }, { status: 400 });

  // Use the shared executeRefactor function which handles both workflow and direct modes
  const result = await executeRefactor(owner, repo, plan, session.accessToken, mode as "workflow" | "direct");
  
  if (result.success) {
    return NextResponse.json(result);
  } else {
    return NextResponse.json({ error: result.error }, { status: 500 });
  }
}
