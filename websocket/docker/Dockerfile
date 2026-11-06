FROM oven/bun:1.1
WORKDIR /app
COPY package.json package.json
RUN bun install --production
COPY src ./src
EXPOSE 8080 443
CMD ["bun", "src/server.js"]
