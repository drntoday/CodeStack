/**
 * Simple unified diff generator for displaying code changes
 * Implements a basic diff algorithm without external dependencies
 */

export interface DiffHunk {
  oldStart: number;
  oldLines: number;
  newStart: number;
  newLines: number;
  lines: string[];
}

export interface FileDiff {
  oldPath?: string;
  newPath?: string;
  hunks: DiffHunk[];
  stats: {
    additions: number;
    deletions: number;
    totalChanges: number;
  };
}

/**
 * Compute the Longest Common Subsequence (LCS) for two arrays
 */
function computeLCS(oldLines: string[], newLines: string[]): number[][] {
  const m = oldLines.length;
  const n = newLines.length;
  const dp: number[][] = Array(m + 1)
    .fill(null)
    .map(() => Array(n + 1).fill(0));

  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      if (oldLines[i - 1] === newLines[j - 1]) {
        dp[i][j] = dp[i - 1][j - 1] + 1;
      } else {
        dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
      }
    }
  }

  return dp;
}

/**
 * Generate diff hunks from LCS table
 */
function generateHunks(
  dp: number[][],
  oldLines: string[],
  newLines: string[]
): DiffHunk[] {
  const hunks: DiffHunk[] = [];
  let i = oldLines.length;
  let j = newLines.length;

  const operations: Array<{ type: 'equal' | 'delete' | 'insert'; line: string }> = [];

  // Backtrack to find operations
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && oldLines[i - 1] === newLines[j - 1]) {
      operations.unshift({ type: 'equal', line: oldLines[i - 1] });
      i--;
      j--;
    } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
      operations.unshift({ type: 'insert', line: newLines[j - 1] });
      j--;
    } else {
      operations.unshift({ type: 'delete', line: oldLines[i - 1] });
      i--;
    }
  }

  // Group operations into hunks with context
  const result: DiffHunk[] = [];
  let currentHunk: DiffHunk | null = null;
  let oldLineNum = 1;
  let newLineNum = 1;
  let pendingContext: string[] = [];

  for (const op of operations) {
    if (op.type === 'equal') {
      pendingContext.push(` ${op.line}`);
      if (pendingContext.length > 3) {
        pendingContext.shift();
      }
      oldLineNum++;
      newLineNum++;
    } else if (op.type === 'delete') {
      if (!currentHunk) {
        currentHunk = {
          oldStart: oldLineNum - pendingContext.length,
          oldLines: 0,
          newStart: newLineNum - pendingContext.length,
          newLines: 0,
          lines: [...pendingContext],
        };
        pendingContext = [];
      }
      currentHunk.lines.push(`-${op.line}`);
      currentHunk.oldLines++;
      oldLineNum++;
    } else if (op.type === 'insert') {
      if (!currentHunk) {
        currentHunk = {
          oldStart: oldLineNum - pendingContext.length,
          oldLines: 0,
          newStart: newLineNum - pendingContext.length,
          newLines: 0,
          lines: [...pendingContext],
        };
        pendingContext = [];
      }
      currentHunk.lines.push(`+${op.line}`);
      currentHunk.newLines++;
      newLineNum++;
    }
  }

  if (currentHunk) {
    result.push(currentHunk);
  }

  return result;
}

/**
 * Generate a unified diff between two file contents
 */
export function generateUnifiedDiff(
  oldContent: string,
  newContent: string,
  options?: {
    oldPath?: string;
    newPath?: string;
    contextLines?: number;
  }
): FileDiff {
  const oldPath = options?.oldPath || 'a/file';
  const newPath = options?.newPath || 'b/file';

  const oldLines = oldContent.split('\n');
  const newLines = newContent.split('\n');

  // Handle identical files
  if (oldContent === newContent) {
    return {
      oldPath,
      newPath,
      hunks: [],
      stats: { additions: 0, deletions: 0, totalChanges: 0 },
    };
  }

  const dp = computeLCS(oldLines, newLines);
  const hunks = generateHunks(dp, oldLines, newLines);

  // Calculate stats
  let additions = 0;
  let deletions = 0;
  for (const hunk of hunks) {
    for (const line of hunk.lines) {
      if (line.startsWith('+')) additions++;
      if (line.startsWith('-')) deletions++;
    }
  }

  return {
    oldPath,
    newPath,
    hunks,
    stats: {
      additions,
      deletions,
      totalChanges: additions + deletions,
    },
  };
}

/**
 * Format a FileDiff as a unified diff string
 */
export function formatUnifiedDiff(diff: FileDiff): string {
  if (diff.hunks.length === 0) {
    return 'No changes';
  }

  const lines: string[] = [];

  // Header
  const oldPath = diff.oldPath || 'a/file';
  const newPath = diff.newPath || 'b/file';
  lines.push(`--- ${oldPath}`);
  lines.push(`+++ ${newPath}`);

  // Hunks
  for (const hunk of diff.hunks) {
    const header = `@@ -${hunk.oldStart},${hunk.oldLines} +${hunk.newStart},${hunk.newLines} @@`;
    lines.push(header);
    lines.push(...hunk.lines);
  }

  return lines.join('\n');
}

/**
 * Generate diffs for multiple files (for cross-file changes)
 */
export function generateMultiFileDiff(
  files: Array<{
    path: string;
    oldContent: string;
    newContent: string;
  }>
): FileDiff[] {
  return files.map(file =>
    generateUnifiedDiff(file.oldContent, file.newContent, {
      oldPath: `a/${file.path}`,
      newPath: `b/${file.path}`,
    })
  );
}

/**
 * Format multiple file diffs as a single diff string
 */
export function formatMultiFileDiff(diffs: FileDiff[]): string {
  const parts = diffs
    .filter(diff => diff.hunks.length > 0)
    .map(diff => formatUnifiedDiff(diff));

  if (parts.length === 0) {
    return 'No changes in any files';
  }

  return parts.join('\n\n');
}
