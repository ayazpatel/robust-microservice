module.exports = {
  apps: [
    {
      name: "realtime-ws-bare",
      script: "src/server.js",
      interpreter: "bun",
      instances: 10,                // 12 CPU → 10 workers
      exec_mode: "cluster",
      max_memory_restart: "10G",
      env: {
        PORT: process.env.PORT || 443,
        NODE_OPTIONS: "--max-old-space-size=8192"
      }
    }
  ]
};
