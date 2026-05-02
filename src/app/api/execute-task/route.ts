import { NextRequest, NextResponse } from "next/server";
import { updateJob } from "@/lib/jobs";
import * as actions from "@/lib/actions";
import { withTimeout } from "@/lib/timeout";

export const dynamic = "force-dynamic";

export async function POST(req: NextRequest) {
  const { jobId, action, parameters, accessToken } = await req.json();

  try {
    let result: any;
    switch (action) {
      case "refactor":
        result = await withTimeout(
          actions.generateRefactorPlan(
            parameters.owner, parameters.repo, parameters.prompt,
            accessToken, parameters.files, parameters.deepContext
          ), 30000, "Refactor timed out"
        );
        await updateJob(jobId, { status: "done", result: { plan: result } });
        break;
      case "test":
        result = await withTimeout(
          actions.generateTests(parameters.fileContent, parameters.filePath),
          30000, "Test generation timed out"
        );
        await updateJob(jobId, { status: "done", result: { testContent: result } });
        break;
      case "audit":
        result = await withTimeout(
          actions.auditCommit(parameters.owner, parameters.repo, parameters.commitSha, accessToken),
          30000, "Audit timed out"
        );
        await updateJob(jobId, { status: "done", result: { report: result } });
        break;
      case "architecture":
        result = await withTimeout(
          actions.answerArchitecture(parameters.owner, parameters.repo, parameters.question, accessToken, parameters.files),
          30000, "Architecture query timed out"
        );
        await updateJob(jobId, { status: "done", result: { text: result } });
        break;
      case "docs":
        if (parameters.type === "readme") {
          result = await withTimeout(
            actions.generateReadme(parameters.owner, parameters.repo, accessToken, parameters.files),
            30000, "README generation timed out"
          );
          await updateJob(jobId, { status: "done", result: { readme: result } });
        } else {
          result = await withTimeout(
            actions.generateOpenApi(parameters.owner, parameters.repo, accessToken, parameters.files),
            30000, "OpenAPI generation timed out"
          );
          await updateJob(jobId, { status: "done", result: { openapi: result } });
        }
        break;
      case "ci":
        result = await withTimeout(
          actions.analyzeCICD(parameters.owner, parameters.repo, parameters.runId, accessToken),
          30000, "CI/CD analysis timed out"
        );
        await updateJob(jobId, { status: "done", result: { analysis: result } });
        break;
      default:
        await updateJob(jobId, { status: "failed", error: "Unknown background action" });
    }
  } catch (err: any) {
    await updateJob(jobId, { status: "failed", error: err.message });
  }

  return NextResponse.json({ ok: true });
}
