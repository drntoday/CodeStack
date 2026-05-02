function formatLog(level: string, message: string, extra?: any) {
  return JSON.stringify({
    level,
    timestamp: new Date().toISOString(),
    message,
    ...(extra ? { extra } : {}),
  });
}

export const logger = {
  info: (msg: string, extra?: any) => console.log(formatLog("info", msg, extra)),
  warn: (msg: string, extra?: any) => console.warn(formatLog("warn", msg, extra)),
  error: (msg: string, extra?: any) => console.error(formatLog("error", msg, extra)),
};
