import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  serverExternalPackages: ["jose"],
  eslint: {
    ignoreDuringBuilds: true,
  },
  typescript: {
    ignoreBuildErrors: true,
  },
};

export default nextConfig;
