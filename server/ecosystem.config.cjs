module.exports = {
  apps: [
    {
      name: "manager",
      script: "npm",
      args: "start",
      instances: 1,
      exec_mode: "fork",
      env: {
        PORT: 3000,
        HOST: "0.0.0.0",
        NODE_ENV: "production"
      }
    },
    {
      name: "manager:numberstatus",
      script: "script/status.mjs",
      instances: 1,
      exec_mode: "fork",
      env: {
        NODE_ENV: "production"
      }
    },
    {
      name: "manager:fetchsms",
      script: "script/fetch.mjs",
      instances: 1,
      exec_mode: "fork",
      env: {
        NODE_ENV: "production"
      }
    }
  ]
};
