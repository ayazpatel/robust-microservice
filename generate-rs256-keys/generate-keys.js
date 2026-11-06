#!/usr/bin/env node
/**
 * generate-keys.js
 * - Generates RSA keypair (PKCS#8 private, SPKI public) for RS256
 * - Emits files and ready-to-copy snippets for Spring (application.yml) and WebSocket (.env)
 */

import { generateKeyPairSync } from 'crypto';
import fs from 'fs';
import path from 'path';

const outDir = process.cwd();

function main() {
  const { publicKey, privateKey } = generateKeyPairSync('rsa', {
    modulusLength: 2048,
    publicKeyEncoding: { type: 'spki', format: 'pem' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' }
  });

  const privPath = path.join(outDir, 'jwt.key');
  const pubPath = path.join(outDir, 'jwt.pub');
  fs.writeFileSync(privPath, privateKey, { mode: 0o600 });
  fs.writeFileSync(pubPath, publicKey, { mode: 0o644 });

  // Build snippets
  const privateYaml = `jwt:\n  privateKeyPem: |\n${privateKey.split('\n').map(l => '    ' + l).join('\n')}`;
  const publicOneLine = publicKey.replace(/\r?\n/g, '\\n');
  const envLine = `AUTH_PUBLIC_PEM=${publicOneLine}`;

  fs.writeFileSync(path.join(outDir, 'spring-application-yml-snippet.txt'), privateYaml + '\n');
  fs.writeFileSync(path.join(outDir, 'websocket-env-snippet.txt'), envLine + '\n');

  // README instructions
  const readme = `# RS256 Key Generation (Auth + WebSocket)\n\nFiles created here:\n- jwt.key (PKCS#8 RSA private key)\n- jwt.pub (SPKI RSA public key)\n- spring-application-yml-snippet.txt (paste into auth/src/main/resources/application.yml under jwt:)\n- websocket-env-snippet.txt (paste line into websocket/.env)\n\nUsage (PowerShell):\n\n  # From this folder\n  node generate-keys.js\n\n  # Open snippet files and copy-paste:\n  # - Put private key block into application.yml (jwt.privateKeyPem)\n  # - Put AUTH_PUBLIC_PEM line into websocket/.env\n\nSecurity:\n- Do NOT commit jwt.key to git.\n- Limit file permissions to your user only.\n`;
  fs.writeFileSync(path.join(outDir, 'README.md'), readme);

  console.log('Generated jwt.key and jwt.pub');
  console.log('Snippets written: spring-application-yml-snippet.txt, websocket-env-snippet.txt');
}

main();
