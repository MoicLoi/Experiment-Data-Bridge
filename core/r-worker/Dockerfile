# syntax=docker/dockerfile:1.7

FROM r-base:4.6.1

ARG EDB_WORKER_UID=10001
ARG EDB_WORKER_GID=10001

ENV EDB_WORKER_HOST=0.0.0.0 \
    EDB_WORKER_PORT=8001 \
    TMPDIR=/var/tmp/edb-worker \
    DEBIAN_FRONTEND=noninteractive

RUN mkdir -p /var/tmp/edb-worker /opt/worker \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        libcurl4-openssl-dev \
        libsodium-dev \
        libssl-dev \
    && Rscript -e "install.packages(c('jsonlite','plumber'), repos='https://cloud.r-project.org'); stopifnot(requireNamespace('jsonlite', quietly=TRUE), requireNamespace('plumber', quietly=TRUE))" \
    && groupadd --system --gid "${EDB_WORKER_GID}" edb-worker \
    && useradd --system --uid "${EDB_WORKER_UID}" --gid edb-worker --no-create-home edb-worker \
    && chown edb-worker:edb-worker /var/tmp/edb-worker /opt/worker \
    && rm -rf /var/lib/apt/lists/*

COPY --chown=edb-worker:edb-worker DESCRIPTION plumber.R run.R /opt/worker/
COPY --chown=edb-worker:edb-worker R /opt/worker/R
COPY --chown=edb-worker:edb-worker handlers /opt/worker/handlers

WORKDIR /opt/worker
USER edb-worker:edb-worker
EXPOSE 8001

HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
    CMD curl --fail --silent --show-error "http://127.0.0.1:${EDB_WORKER_PORT}/health" >/dev/null || exit 1

ENTRYPOINT ["Rscript", "--vanilla", "/opt/worker/run.R"]
