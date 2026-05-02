/**
 * Timeout utility for wrapping promises with a time limit
 */

export function withTimeout<T>(
  promise: Promise<T>,
  ms: number,
  message = "Operation timed out"
): Promise<T> {
  const timeout = new Promise<never>((_, reject) =>
    setTimeout(() => reject(new Error(message)), ms)
  );
  return Promise.race([promise, timeout]);
}
