"use client"

import { useEffect } from "react"

export default function Toast({ message, type = "info", onClose }: { message: string; type?: string; onClose: () => void }) {
  useEffect(() => {
    const timer = setTimeout(onClose, 5000);
    return () => clearTimeout(timer);
  }, [onClose]);

  return (
    <div className={`fixed bottom-4 right-4 z-50 px-6 py-3 rounded-lg text-white shadow-lg ${type === "error" ? "bg-red-600" : "bg-yellow-600"}`}>
      {message}
    </div>
  );
}
