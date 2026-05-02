"use client"
import { Component, ReactNode } from "react";

interface Props { children: ReactNode; }
interface State { hasError: boolean; }

export default class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() { return { hasError: true }; }

  componentDidCatch(error: Error, info: any) {
    console.error("Dashboard Error:", error, info);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen bg-gray-900 text-white flex items-center justify-center">
          <div className="text-center p-8">
            <h2 className="text-xl font-bold mb-2">Something went wrong</h2>
            <p className=" text-white/60 mb-4">An unexpected error occurred. Please refresh the page.</p>
            <button onClick={() => window.location.reload()} className="px-4 py-2 bg-blue-600 rounded">
              Reload
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
