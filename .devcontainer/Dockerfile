FROM mcr.microsoft.com/devcontainers/java:3-21-trixie

RUN apt-get update && apt-get install -y \
    sqlite3 \
    libsqlite3-dev \
    && rm -rf /var/lib/apt/lists/*