import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken)
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const { url, payload } = await req.json();
  if (!url)
    return NextResponse.json({ error: "Missing webhook URL" }, { status: 400 });

  try {
    const webhookRes = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload || {}),
    });
    const status = webhookRes.status;
    const body = await webhookRes.text();
    return NextResponse.json({ status, body });
  } catch (error) {
    return NextResponse.json({ error: "Webhook call failed" }, { status: 500 });
  }
}
