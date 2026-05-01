import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  serverExternalPackages: ["jose"],
  typescript: {
    ignoreBuildErrors: false,
  },
};

export default nextConfig;
