FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# Build/runtime deps for packages such as dbus-python / pygobject on Linux.
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    gcc \
    pkg-config \
    libcairo2-dev \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./
RUN grep -v -E '^(dbus-python|pygobject)==' requirements.txt > requirements.docker.txt && \
    pip install --no-cache-dir -r requirements.docker.txt

COPY . .

EXPOSE 5000

CMD ["python", "src/main.py", "-m", "http", "-p", "5000"]
