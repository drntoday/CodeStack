import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";

let cachedTree: any = null;
let cacheTime = 0;
const CACHE_DURATION = 5 * 60 * 1000; // 5 minutes

export async function POST(req: NextRequest) {
  const session = await auth();
  if (!session?.accessToken) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { owner, repo } = await req.json();
  if (!owner || !repo) {
    return NextResponse.json({ error: "Missing fields" }, { status: 400 });
  }

  // Check cache
  const now = Date.now();
  if (cachedTree && now - cacheTime < CACHE_DURATION) {
    return NextResponse.json(cachedTree);
  }

  try {
    // Get the root tree
    const branchRes = await fetch(
      `https://api.github.com/repos/${owner}/${repo}/git/ref/heads/main`,
      {
        headers: {
          Authorization: `token ${session.accessToken}`,
          Accept: "application/vnd.github.v3+json",
        },
      }
    );
    if (!branchRes.ok) {
      return NextResponse.json(
        { error: "Failed to fetch branch" },
        { status: branchRes.status }
      );
    }
    const branchData = await branchRes.json();
    const commitSha = branchData.object.sha;

    const treeRes = await fetch(
      `https://api.github.com/repos/${owner}/${repo}/git/trees/${commitSha}?recursive=1`,
      {
        headers: {
          Authorization: `token ${session.accessToken}`,
          Accept: "application/vnd.github.v3+json",
        },
      }
    );
    if (!treeRes.ok) {
      return NextResponse.json(
        { error: "Failed to fetch tree" },
        { status: treeRes.status }
      );
    }
    const treeData = await treeRes.json();
    const files = treeData.tree
      .filter((item: any) => item.type === "blob")
      .map((item: any) => item.path);

    cachedTree = { files, owner, repo };
    cacheTime = now;
    return NextResponse.json({ files, owner, repo });
  } catch (error) {
    return NextResponse.json({ error: "Internal error" }, { status: 500 });
  }
}
