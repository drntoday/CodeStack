import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  serverExternalPackages: ["jose"],
  typescript: {
    ignoreBuildErrors: true,
  },
  eslint: {
    ignoreDuringBuilds: true,
  },
};

export default nextConfig;
