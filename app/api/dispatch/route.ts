import { Octokit } from "@octokit/rest";

export async function POST(req: Request) {
  const { task, files, modelInstructions } = await req.json();

  // Initialize Octokit with your GitHub PAT (Personal Access Token)
  const octokit = new Octokit({ auth: process.env.CODESTACK_PAT });

  try {
    // This triggers the 'repository_dispatch' event in GitHub Actions
    await octokit.repos.createDispatchEvent({
      owner: "YOUR_GITHUB_USERNAME",
      repo: "CodeStack",
      event_type: "gemini_task",
      client_payload: {
        task_description: task,
        target_files: files,
        instructions: modelInstructions,
      },
    });

    return new Response(JSON.stringify({ status: "Action Triggered!" }), { status: 200 });
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500 });
  }
}
