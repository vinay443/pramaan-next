# Developer Setup

Toolchain required to build `pramaan-next`: **Git, Java 21, Go, Node.js LTS + npm,
Docker, and the Spring Boot Maven Wrapper**. No Python is used anywhere.

This laptop is **Windows 11** with `winget` available. Commands below are for
Windows PowerShell; macOS/Linux equivalents are noted at the end.

---

## 0. Current state of this machine

| Tool           | Status         | Notes                          |
|----------------|----------------|--------------------------------|
| Git            | ✅ installed   | 2.55.x                         |
| Node.js / npm  | ✅ installed   | Node v24 LTS, npm 11.x         |
| Docker Desktop | ✅ installed   | Docker 29.x                    |
| Java 21 (JDK)  | ❌ missing     | install below                  |
| Go             | ❌ missing     | install below                  |
| Maven Wrapper  | ✅ in repo     | `backend-java/mvnw` / `mvnw.cmd` — no global Maven needed |

---

## 1. Install missing tools

### Java 21 (Eclipse Temurin JDK)

```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK -e --source winget
```

### Go

```powershell
winget install --id GoLang.Go -e --source winget
```

**Close and reopen the terminal** afterwards so `PATH` / `JAVA_HOME` refresh.

---

## 2. Already installed — reinstall only if verification fails

```powershell
winget install --id Git.Git -e --source winget
winget install --id OpenJS.NodeJS.LTS -e --source winget
winget install --id Docker.DockerDesktop -e --source winget
```

Start Docker Desktop once and let it finish initializing before using `docker`.

---

## 3. Spring Boot / Maven Wrapper

Nothing to install. The wrapper is committed under `backend-java/`:

- `mvnw` (Linux/macOS), `mvnw.cmd` (Windows)
- `.mvn/wrapper/maven-wrapper.properties` — pins Maven **3.9.9**

On first use the wrapper downloads that Maven version automatically. It requires
only a JDK on `PATH`. Do **not** install a system-wide Maven.

---

## 4. Verification

Run each command; every one should print a version or succeed.

### Git
```powershell
git --version
```

### Java 21
```powershell
java -version
javac -version
```
Both must report `21.x`. Also confirm `JAVA_HOME`:
```powershell
$env:JAVA_HOME
```

### Go
```powershell
go version
```
Expect `go1.23` or newer.

### Node.js + npm
```powershell
node --version
npm --version
```
Node must be an even-numbered LTS (v20 / v22 / v24).

### Docker
```powershell
docker --version
docker compose version
docker run --rm hello-world
```
`hello-world` must pull and run (Docker Desktop running).

### Spring Boot / Maven Wrapper
From the repo root:
```powershell
cd backend-java
.\mvnw.cmd -v
.\mvnw.cmd -q compile
cd ..
```
`mvnw -v` prints Apache Maven 3.9.9 and the Java 21 runtime. `compile` succeeds
against the scaffold project.

### TypeScript (via frontend toolchain)
```powershell
cd frontend-react
npm install
npx tsc --version
npm run build
cd ..
```
`tsc` reports 5.x; `npm run build` produces `dist/`.

### Infrastructure (Docker Compose)
From the repo root:
```powershell
copy .env.example .env
docker compose up -d
docker compose ps
```
All three services (`postgres`, `pgvector`, `minio`) should reach `healthy`.
Quick checks:
```powershell
docker exec pramaan-postgres pg_isready -U pramaan
docker exec pramaan-pgvector psql -U pramaan -d pramaan_vectors -c "SELECT extname FROM pg_extension WHERE extname='vector';"
```
Second command must list `vector`. MinIO console: http://localhost:9001
(user `pramaan`, password `pramaan-secret`).

Tear down when done (`-v` also wipes data volumes):
```powershell
docker compose down
```

### Agents (Go build)
```powershell
cd agents-go
go build ./...
go test ./...
cd ..
```

---

## 5. macOS / Linux equivalents

| Tool     | macOS (Homebrew)                 | Debian/Ubuntu                                  |
|----------|----------------------------------|-----------------------------------------------|
| Git      | `brew install git`               | `sudo apt install git`                         |
| Java 21  | `brew install temurin@21`        | `sudo apt install openjdk-21-jdk`              |
| Go       | `brew install go`                | `sudo apt install golang` (or tarball from go.dev) |
| Node LTS | `brew install node@22`           | `curl -fsSL https://deb.nodesource.com/setup_22.x \| sudo -E bash - && sudo apt install nodejs` |
| Docker   | Docker Desktop for Mac           | `sudo apt install docker.io docker-compose-plugin` |

Verification commands are identical, using `./mvnw` instead of `.\mvnw.cmd`.
