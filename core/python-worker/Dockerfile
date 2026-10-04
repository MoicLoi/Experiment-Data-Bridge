# syntax=docker/dockerfile:1.7

FROM python:3.12-slim-bookworm AS builder

ENV PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /build

RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:${PATH}"

COPY requirements.lock ./
RUN pip install --no-cache-dir -r requirements.lock


FROM python:3.12-slim-bookworm AS runtime

ARG EDB_WORKER_UID=10001
ARG EDB_WORKER_GID=10001

ENV PATH="/opt/venv/bin:${PATH}" \
    PYTHONPATH=/opt/worker/src \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    EDB_WORKER_HOST=0.0.0.0 \
    EDB_WORKER_PORT=8000 \
    TMPDIR=/var/tmp/edb-worker \
    EDB_WORKER_VERSION=0.1.0 \
    EDB_WORKER_IMAGE_VERSION=dev

RUN groupadd --system --gid "${EDB_WORKER_GID}" edb-worker \
    && useradd --system --uid "${EDB_WORKER_UID}" --gid edb-worker --no-create-home edb-worker \
    && mkdir -p /var/tmp/edb-worker \
    && chown edb-worker:edb-worker /var/tmp/edb-worker

COPY --from=builder /opt/venv /opt/venv
COPY --chown=edb-worker:edb-worker src /opt/worker/src

WORKDIR /opt/worker

USER edb-worker:edb-worker
EXPOSE 8000

HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
    CMD ["python", "-c", "import os,urllib.request; urllib.request.urlopen('http://127.0.0.1:'+os.getenv('EDB_WORKER_PORT','8000')+'/health', timeout=2).read()"]

ENTRYPOINT ["python", "-m", "edb_python_worker"]
