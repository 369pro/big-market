#!/usr/bin/env python3
"""Create private local Agent config and its scoped MySQL database account.

Requires the existing local-mysql and chatgpt-data containers. Existing config
is preserved. No existing business tables or balances are changed.
"""
import json
import os
from pathlib import Path
import secrets
import subprocess
from urllib.parse import quote, unquote, urlsplit


def env_file(path):
    if not path.exists():
        return {}
    return dict(line.split("=", 1) for line in path.read_text().splitlines()
                if "=" in line and not line.lstrip().startswith("#"))


def main():
    folder = Path(__file__).resolve().parent
    shared_path = folder / "agent-internal.env"
    private_path = folder / "agent-service.env"
    shared, private = env_file(shared_path), env_file(private_path)
    key = shared.get("AGENT_INTERNAL_KEY") or private.get("AGENT_INTERNAL_KEY") or secrets.token_hex(32)
    if any(conf.get("AGENT_INTERNAL_KEY", key) != key for conf in (shared, private)):
        raise SystemExit("Existing Agent service keys disagree; align them before continuing.")
    url = private.get("DATABASE_URL")
    if url:
        parsed = urlsplit(url)
        if parsed.username != "ai_agent" or parsed.path != "/ai_agent":
            raise SystemExit("Existing DATABASE_URL uses another database; refusing to change it.")
        password = unquote(parsed.password or "")
    else:
        password = secrets.token_hex(24)
        url = f"mysql+pymysql://ai_agent:{quote(password, safe='')}@host.docker.internal:3306/ai_agent?charset=utf8mb4"
    if not password or not all(c.isalnum() for c in password):
        raise SystemExit("Setup expects an existing alphanumeric local database password.")
    info = json.loads(subprocess.check_output([
        "docker", "inspect", "llm-market-local-chatgpt-data-1"]))[0]
    current = dict(item.split("=", 1) for item in info["Config"]["Env"])
    command_env = dict(os.environ, MYSQL_PWD=current["LOCAL_MYSQL_PASSWORD"])
    sql = (
        "CREATE DATABASE IF NOT EXISTS ai_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n"
        f"CREATE USER IF NOT EXISTS 'ai_agent'@'%' IDENTIFIED BY '{password}';\n"
        "GRANT ALL PRIVILEGES ON ai_agent.* TO 'ai_agent'@'%';\n"
    )
    subprocess.run(["docker", "exec", "-i", "-e", "MYSQL_PWD", "local-mysql", "mysql",
                    "-u" + current.get("LOCAL_MYSQL_USERNAME", "root")],
                   input=sql.encode(), env=command_env, check=True)
    shared.update(AGENT_INTERNAL_KEY=key, AGENT_SERVICE_URL="http://agent-service:8093")
    private.update(AGENT_INTERNAL_KEY=key, DATABASE_URL=url,
                   JAVA_SERVICE_URL="http://chatgpt-data:8091/api/v1/internal/agent")
    for path, values in ((shared_path, shared), (private_path, private)):
        # Set permissions before writing any secret bytes.
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        os.fchmod(fd, 0o600)
        with os.fdopen(fd, "w") as stream:
            stream.write("".join(f"{k}={v}\n" for k, v in values.items()))
    print("Agent database and private local configuration are ready; existing data preserved.")


if __name__ == "__main__":
    main()
