"use client"

import { signIn, useSession } from "next-auth/react"
import { useRouter } from "next/navigation"
import { useEffect } from "react"

export default function Home() {
  const { data: session, status } = useSession()
  const router = useRouter()

  useEffect(() => {
    if (status === "authenticated") {
      router.push("/dashboard")
    }
  }, [status, router])

  if (status === "loading") {
    return (
      <div className="min-h-screen bg-gray-900 text-white flex items-center justify-center">
        <p>Loading...</p>
      </div>
    )
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-center p-24 bg-gray-900 text-white">
      <h1 className="text-4xl font-bold mb-4">Code Stack</h1>
      <p className="mb-8 text-lg text-center max-w-xl text-gray-300">
        Your free AI coding partner – ask questions, refactor entire codebases, generate tests, and automate DevOps, all from the browser.
      </p>
      
      {!session ? (
        <button
          onClick={() => signIn("github")}
          className="px-6 py-3 bg-gray-800 hover:bg-gray-700 border border-gray-700 rounded-lg flex items-center gap-3 transition-colors"
        >
          <svg className="w-6 h-6" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-1.334-.442-.261.026-.66.026-.66 1.204-.546 1.986.546 1.986.546 1.157 1.986 3.036 1.41 3.771 1.078.117-.836.451-1.411.818-1.734-2.886-.328-5.919-1.444-5.919-1.444-.444-.262.025-.66.025-.66 1.205-.545 1.987.546 1.987.546 1.158 1.987 3.038 1.411 3.773 1.079.117-.836.45-1.411.816-1.734-2.887-.328-5.92-1.444-5.92-1.444-.443-.262.026-.66.026-.66 1.205-.545 1.987.546 1.987.546 1.158 1.987 3.038 1.411 3.773 1.079.117-.836.45-1.411.816-1.734-2.887-.328-5.92-1.444-5.92-1.444-.443-.262.026-.66.026-.66z"/>
          </svg>
          Sign in with GitHub
        </button>
      ) : (
        <p className="text-green-400">Redirecting to dashboard...</p>
      )}
    </main>
  )
}
