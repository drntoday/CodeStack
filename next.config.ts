import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  turbopack: {
    externalPackages: ["jose"],
  },
  typescript: {
    ignoreBuildErrors: true,
  },
  eslint: {
    ignoreDuringBuilds: true,
  },
};

export default nextConfig;
