Yup bhai 😄 — agar tu **Ubuntu** pe Linux build kar raha hai, to sab toolchain aur dependency ek hi command se aa jati hai. Yahaan **pure step-by-step** likha hai — ye tere `LINUX_COMPILE_RUN.md` me bhi daal sakta hai.

---

## 🧱 Step 1: Basic System Setup

Run these commands (for **Ubuntu 20.04 / 22.04 / 24.04**):

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y build-essential cmake git pkg-config curl unzip tar
```

**Explanation:**

* `build-essential` → installs gcc, g++, make (compiler toolchain)
* `cmake` → build system generator
* `git` → to clone repos
* `pkg-config` → helps CMake find system libs
* `curl`, `unzip`, `tar` → used by vcpkg to download/extract packages

---

## ⚙️ Step 2: Clone & Bootstrap vcpkg Locally

```bash
git clone https://github.com/microsoft/vcpkg ../../vcpkg
../../vcpkg/bootstrap-vcpkg.sh
```

👉 This creates a local `vcpkg/` folder inside your project (same structure as your Windows build).

---

## 🧩 Step 3: Verify Toolchain Versions

Check your compiler:

```bash
g++ --version
cmake --version
```

If you want to use **clang** instead:

```bash
sudo apt install -y clang
```

Then build with:

```bash
cmake -DCMAKE_CXX_COMPILER=clang++ ...
```

---

## 🧰 Step 4: Build Using Script

From your project:

```bash
cd ws-server/linux
chmod +x scripts/build.sh
./scripts/build.sh
```

That will:

1. Use your local vcpkg toolchain
2. Install required ports (`uwebsockets`, `usockets`, `openssl`, etc.)
3. Compile and output binary → `linux/build/ws_server`

---

## ✅ Step 5: Run & Test

```bash
./build/ws_server
curl http://localhost:9001/healthz
```

If you see `ok` → build successful 🎉

---

## 🧠 Bonus: Optional Dev Tools

For comfort, install:

```bash
sudo apt install -y htop net-tools tmux vim
```

And if you want to run the server automatically:

```bash
sudo apt install -y systemd
```

---

Would you like me to include this **toolchain installation section** automatically into your `LINUX_COMPILE_RUN.md` file so it’s ready to commit?
