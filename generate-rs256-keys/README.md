# RS256 Key Generation (Auth + WebSocket)

Files created here:
- jwt.key (PKCS#8 RSA private key)
- jwt.pub (SPKI RSA public key)
- spring-application-yml-snippet.txt (paste into auth/src/main/resources/application.yml under jwt:)
- websocket-env-snippet.txt (paste line into websocket/.env)

Usage (PowerShell):

  # From this folder
  node generate-keys.js

  # Open snippet files and copy-paste:
  # - Put private key block into application.yml (jwt.privateKeyPem)
  # - Put AUTH_PUBLIC_PEM line into websocket/.env

Security:
- Do NOT commit jwt.key to git.
- Limit file permissions to your user only.
