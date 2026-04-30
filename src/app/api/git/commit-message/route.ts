import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";
import { queryGroq } from "@/lib/groq";

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { oldContent, newContent, filePath, description } = await req.json();
  if (!oldContent && !newContent) {
    return NextResponse.json({ error: "Need old or new content" }, { status: 400 });
  }

  const diffBlock = `Old:\n\`\`\`\n${oldContent}\n\`\`\`\nNew:\n\`\`\`\n${newContent}\n\`\`\``;

  const prompt = `You are an expert git commit message writer. Write a concise, meaningful commit message (first line < 72 chars, optionally followed by a blank line and more description) that describes the change below${description ? `. The user described the change as: "${description}"` : ""}.

File: ${filePath || "unknown"}
Change (diff):
${diffBlock}

Return ONLY the commit message, no other text.`;

  const message = await queryGroq("commitMessage", [{ role: "user", content: prompt }]);

  return NextResponse.json({ message });
}
