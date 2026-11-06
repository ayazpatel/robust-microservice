// File: scripts/generate-react-context.js
// Usage: node generate-react-context.js > react_context.txt

const fs = require("fs");
const path = require("path");

// 👇 Change this to point to your Vite React folder (usually "frontend" or "client")
const baseDir = path.resolve(__dirname, ".");

// ⛔ Directories and files to skip
const skipDirs = [
  "node_modules",
  "dist",
  ".git",
  ".idea",
  ".vscode",
  ".DS_Store",
  "package-lock.json",
  "yarn.lock",
  "react_context.txt",
  "generate-react-context.js"
];

function walk(dir, fileList = []) {
  fs.readdirSync(dir).forEach(item => {
    const fullPath = path.join(dir, item);
    const relPath = path.relative(baseDir, fullPath).replace(/\\/g, "/");

    // Skip unwanted directories/files
    if (skipDirs.some(d => relPath.startsWith(d))) return;

    const stat = fs.statSync(fullPath);
    if (stat.isDirectory()) {
      walk(fullPath, fileList);
    } else {
      // Only include source files typically used in React/Vite apps
      if (/\.(js|jsx|ts|tsx|css|scss|html|json|md|env)$/i.test(item)) {
        fileList.push(fullPath);
      }
    }
  });
  return fileList;
}

// 🧭 Gather all frontend files
const allFiles = walk(baseDir);

// 🧾 Print each file’s path and contents
allFiles.forEach(fp => {
  const rel = path.relative(baseDir, fp).replace(/\\/g, "/");
  console.log("=".repeat(80));
  console.log(`File: ${rel}`);
  console.log("=".repeat(80));
  console.log(fs.readFileSync(fp, "utf8"));
  console.log("\n\n");
});
