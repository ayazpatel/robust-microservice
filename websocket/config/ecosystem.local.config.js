module.exports = {
  apps: [
    {
      name: "realtime-ws-local",
      script: "src/server.js",
      interpreter: "node",
      instances: 2,                 // laptop: 2 workers
      exec_mode: "cluster",
      env: {
        PORT: process.env.PORT || 8080,
        NODE_OPTIONS: "--max-old-space-size=2048",
        // Pass-through critical env vars for clustered runs
        RABBIT_URL: process.env.RABBIT_URL,
        REDIS_URL: process.env.REDIS_URL,
        AUTH_PUBLIC_PEM: process.env.AUTH_PUBLIC_PEM,
        AUTH_PUBLIC_PEM_FILE: process.env.AUTH_PUBLIC_PEM_FILE,
        RABBIT_EXCHANGE: process.env.RABBIT_EXCHANGE,
        RABBIT_ROUTING_KEY: process.env.RABBIT_ROUTING_KEY,
        WS_ACK: process.env.WS_ACK,
        WS_WELCOME: process.env.WS_WELCOME
      }
    }
  ]
};
