import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  turbopack: {
    externalPackages: ["jose"],
  },
};

export default nextConfig;
