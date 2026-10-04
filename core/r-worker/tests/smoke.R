arguments <- commandArgs(trailingOnly = FALSE)
file_argument <- grep("^--file=", arguments, value = TRUE)
script_path <- if (length(file_argument)) sub("^--file=", "", file_argument[[1L]]) else "tests/smoke.R"
root <- normalizePath(file.path(dirname(script_path), ".."), mustWork = TRUE)
local_library <- file.path(root, ".r-library")
if (dir.exists(local_library)) .libPaths(c(local_library, .libPaths()))
source(file.path(root, "R", "protocol.R"))
source(file.path(root, "R", "registry.R"))
source(file.path(root, "handlers", "statistical_summary.R"))
source(file.path(root, "handlers", "tabular_report.R"))

directory <- tempfile("edb-r-worker-test-")
dir.create(directory)
on.exit(unlink(directory, recursive = TRUE, force = TRUE), add = TRUE)
csv <- file.path(directory, "source.csv")
writeLines(c("x,y,label", "1,2,a", "3,,b"), csv)
manifest <- list(handlerId = "r-statistical-summary", parameters = list())
source <- list(manifest = list(format = "CSV"), path = csv)
artifact <- statistical_summary_handler(manifest, list(source), directory)
stopifnot(
  file.exists(artifact$path),
  identical(artifact$mediaType, "application/json"),
  path_is_within(artifact$path, directory)
)
result <- jsonlite::read_json(artifact$path, simplifyVector = TRUE)
numeric_column_count <- if (is.data.frame(result$numericColumns)) {
  nrow(result$numericColumns)
} else {
  length(result$numericColumns)
}
stopifnot(result$rows == 2L, result$columns == 3L, numeric_column_count == 2L)

report_manifest <- list(
  handlerId = "r-tabular-summary-report",
  parameters = list(
    "edb.report.title" = "Smoke report",
    "edb.report.outputFileName" = "smoke-report.pdf"
  )
)
report_artifact <- tabular_report_handler(report_manifest, list(source), directory)
signature <- readBin(report_artifact$path, "raw", n = 4L)
stopifnot(
  identical(report_artifact$fileName, "smoke-report.pdf"),
  identical(report_artifact$mediaType, "application/pdf"),
  identical(rawToChar(signature), "%PDF")
)
encoded_metadata <- metadata_base64url(report_artifact$metadata)
stopifnot(!grepl("[[:space:]]", encoded_metadata), nchar(encoded_metadata) > 76L)
cat("R worker smoke test passed\n")
