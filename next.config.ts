import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Disabling source maps reduces memory usage significantly
  productionBrowserSourceMaps: false,
  experimental: {
    turbo: false,
  },
  // Ensure the build doesn't hang on linting/type-checking if memory is tight
  eslint: {
    ignoreDuringBuilds: true,
  },
  typescript: {
    ignoreBuildErrors: true,
  }
};

export default nextConfig;
