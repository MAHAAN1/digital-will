import { fileURLToPath } from 'node:url';

function backendOrigin() {
  const configured = process.env.BACKEND_ORIGIN || process.env.NEXT_PUBLIC_API_URL;
  if (!configured && process.env.NODE_ENV !== 'development') {
    throw new Error('Set BACKEND_ORIGIN to the Spring API origin before building the frontend.');
  }
  const url = new URL(configured || 'http://127.0.0.1:8080');
  const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  const localHttp = loopback && !process.env.VERCEL && url.protocol === 'http:';
  if ((!localHttp && url.protocol !== 'https:') || (loopback && process.env.VERCEL) ||
      url.username || url.password || url.pathname !== '/' || url.search || url.hash) {
    throw new Error('BACKEND_ORIGIN must be an HTTPS origin. HTTP loopback is supported for local testing.');
  }
  return url.origin;
}

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  outputFileTracingRoot: fileURLToPath(new URL('.', import.meta.url)),
  async rewrites() {
    if (process.env.VERCEL && !process.env.BACKEND_ORIGIN && !process.env.NEXT_PUBLIC_API_URL) return [];
    return [{ source: '/api/:path*', destination: backendOrigin() + '/api/:path*' }];
  },
  async headers() {
    return [{
      source: '/:path*',
      headers: [
        { key: 'Referrer-Policy', value: 'no-referrer' },
        { key: 'X-Frame-Options', value: 'DENY' },
        { key: 'X-Content-Type-Options', value: 'nosniff' },
        { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=(), payment=()' },
        { key: 'Content-Security-Policy', value: "base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'" },
      ],
    }];
  },
};

export default nextConfig;
