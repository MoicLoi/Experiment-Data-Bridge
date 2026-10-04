import os

import uvicorn


def main() -> None:
    uvicorn.run(
        "edb_python_worker.app:app",
        host=os.getenv("EDB_WORKER_HOST", "127.0.0.1"),
        port=int(os.getenv("EDB_WORKER_PORT", "8000")),
        workers=1,
    )


if __name__ == "__main__":
    main()
