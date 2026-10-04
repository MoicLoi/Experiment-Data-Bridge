arguments <- commandArgs(trailingOnly = FALSE)
file_argument <- grep("^--file=", arguments, value = TRUE)
script_path <- if (length(file_argument)) sub("^--file=", "", file_argument[[1L]]) else "run.R"
root <- normalizePath(dirname(script_path), mustWork = TRUE)
local_library <- file.path(root, ".r-library")
if (dir.exists(local_library)) .libPaths(c(local_library, .libPaths()))
source(file.path(root, "R", "protocol.R"), local = globalenv())
source(file.path(root, "R", "registry.R"), local = globalenv())
source(file.path(root, "handlers", "statistical_summary.R"), local = globalenv())
source(file.path(root, "handlers", "tabular_report.R"), local = globalenv())

register_operation("r-statistical-summary", statistical_summary_handler)
register_operation("r-tabular-summary-report", tabular_report_handler)

host <- Sys.getenv("EDB_WORKER_HOST", Sys.getenv("EDB_R_WORKER_HOST", "127.0.0.1"))
port <- as.integer(Sys.getenv("EDB_WORKER_PORT", Sys.getenv("EDB_R_WORKER_PORT", "8001")))
if (is.na(port) || port < 1L || port > 65535L) stop("EDB_R_WORKER_PORT is invalid")

api <- plumber::plumb(file.path(root, "plumber.R"))
api$run(host = host, port = port)
