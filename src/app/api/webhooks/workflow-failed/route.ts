import { NextRequest, NextResponse } from "next/server";
import { createHmac } from "crypto";
import { GoogleGenerativeAI } from "@google/generative-ai";
import { Octokit } from "@octokit/rest";

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY!);
const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });
const octokit = new Octokit({ auth: process.env.HEALER_TOKEN });

function verifySignature(payload: string, signature: string): boolean {
  const secret = process.env.WEBHOOK_SECRET!;
  if (!secret) return false;
  const hmac = createHmac("sha256", secret);
  hmac.update(payload);
  const digest = "sha256=" + hmac.digest("hex");
  try {
    return crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(digest));
  } catch {
    return false;
  }
}

export async function POST(req: NextRequest) {
  // Verify webhook signature
  const rawBody = await req.text();
  const signature = req.headers.get("x-hub-signature-256") || "";
  if (!verifySignature(rawBody, signature)) {
    return NextResponse.json({ error: "Invalid signature" }, { status: 403 });
  }
  
  const payload = JSON.parse(rawBody);

  if (
    payload.action !== "completed" ||
    payload.workflow_run?.conclusion !== "failure"
  ) {
    return NextResponse.json({ message: "Ignored non-failure" });
  }

  const { owner, repo, run_id, head_branch } = payload.repository;
  const workflowRunId = payload.workflow_run.id;

  try {
    // Fetch logs (first 4000 chars to save tokens)
    const logsRes = await fetch(
      `https://api.github.com/repos/${owner.login}/${repo.name}/actions/runs/${workflowRunId}/logs`,
      { headers: { Authorization: `token ${process.env.HEALER_TOKEN}` } }
    );
    const logText = await logsRes.text();
    const logSnippet = logText.slice(0, 4000);

    // Get the diff of the last commit on the head branch
    const { data: branchData } = await octokit.repos.getBranch({
      owner: owner.login,
      repo: repo.name,
      branch: head_branch,
    });
    const commitSha = branchData.commit.sha;
    const { data: commitData } = await octokit.repos.getCommit({
      owner: owner.login,
      repo: repo.name,
      ref: commitSha,
    });
    const diff = commitData.files?.map((f) => f.patch).join("\n") || "";

    const prompt = `A CI run failed on branch ${head_branch}. Logs:\n\`\`\`\n${logSnippet}\n\`\`\`\nDiff that triggered the failure:\n\`\`\`diff\n${diff}\n\`\`\`\nAnalyze the error. If you can propose a fix, output only a JSON array of changed files: [{"file": "path", "content": "new content"}]. If no fix is possible, output {"noop": true}. No other text.`;

    const result = await model.generateContent(prompt);
    const response = await result.response;
    const text = response.text();
    const fixData = JSON.parse(text);

    if (fixData.noop) {
      return NextResponse.json({ message: "No fix proposed" });
    }

    // Create a new branch and commit fixes
    const fixBranch = `heal-${run_id}`;
    const { data: mainRef } = await octokit.git.getRef({
      owner: owner.login,
      repo: repo.name,
      ref: `heads/main`,
    });
    await octokit.git.createRef({
      owner: owner.login,
      repo: repo.name,
      ref: `refs/heads/${fixBranch}`,
      sha: mainRef.object.sha,
    });

    for (const { file, content } of fixData) {
      await octokit.repos.createOrUpdateFileContents({
        owner: owner.login,
        repo: repo.name,
        path: file,
        branch: fixBranch,
        message: `heal: fix ${file}`,
        content: Buffer.from(content).toString("base64"),
      });
    }

    // Open a PR
    await octokit.pulls.create({
      owner: owner.login,
      repo: repo.name,
      head: fixBranch,
      base: "main",
      title: `🔥 Automated fix for build failure #${run_id}`,
      body: `The CI run [${run_id}](${payload.workflow_run.html_url}) failed. Gemini proposed this fix.`,
    });

    return NextResponse.json({ message: "Fix PR created" });
  } catch (err) {
    console.error(err);
    return NextResponse.json({ error: "Processing failed" }, { status: 500 });
  }
}
