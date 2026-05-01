import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import * as actions from "@/lib/actions";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { owner, repo, files } = await req.json();

  if (!files || files.length === 0) {
    return NextResponse.json({ error: "The repository contains no files." }, { status: 400 });
  }

  const openapi = await actions.generateOpenApi(owner, repo, session.accessToken, files);

  return NextResponse.json({ openapi });
}
