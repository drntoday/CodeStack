import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { rateLimit } from "@/lib/rate-limit";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken)
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  // Rate limiting: 5 requests per 5 minutes (stricter for SSRF protection)
  const identifier = `${session?.user?.email || "anonymous"}::${Math.floor(Date.now() / 300000)}`;
  if (!(await rateLimit(identifier, 5, 300000))) {
    return NextResponse.json({ error: "Too many requests. Please wait a moment." }, { status: 429 });
  }

  const { url, payload } = await req.json();
  if (!url)
    return NextResponse.json({ error: "Missing webhook URL" }, { status: 400 });

  // SSRF protection - block internal addresses
  try {
    const urlObj = new URL(url);
    const blockedHosts = ["localhost", "127.0.0.1", "::1", "0.0.0.0", "169.254.169.254", "metadata.google.internal"];
    if (blockedHosts.includes(urlObj.hostname) || urlObj.hostname.endsWith(".local")) {
      return NextResponse.json({ error: "Cannot trigger webhooks to internal addresses" }, { status: 403 });
    }
  } catch (e) {
    return NextResponse.json({ error: "Invalid URL format" }, { status: 400 });
  }

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
