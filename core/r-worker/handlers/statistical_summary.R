statistical_summary_handler <- function(manifest, sources, output_directory) {
  source <- sources[[1L]]
  format <- toupper(source$manifest$format %||% "")
  if (!identical(format, "CSV")) stop("r-statistical-summary currently supports CSV input")

  data <- utils::read.csv(source$path, check.names = FALSE, stringsAsFactors = FALSE)
  numeric_columns <- names(data)[vapply(data, is.numeric, logical(1))]
  summaries <- lapply(numeric_columns, function(name) {
    values <- data[[name]]
    finite <- values[is.finite(values)]
    list(
      name = name,
      count = length(values),
      missing = sum(is.na(values)),
      mean = if (length(finite)) mean(finite) else NULL,
      standardDeviation = if (length(finite) > 1L) stats::sd(finite) else NULL,
      minimum = if (length(finite)) min(finite) else NULL,
      median = if (length(finite)) stats::median(finite) else NULL,
      maximum = if (length(finite)) max(finite) else NULL
    )
  })
  result <- list(
    schemaVersion = 1L,
    handlerId = manifest$handlerId,
    rows = nrow(data),
    columns = ncol(data),
    numericColumns = summaries
  )
  output <- file.path(output_directory, "r-statistical-summary.json")
  jsonlite::write_json(result, output, auto_unbox = TRUE, pretty = TRUE, null = "null", na = "null")
  list(
    path = output,
    fileName = "r-statistical-summary.json",
    mediaType = "application/json",
    metadata = list(engine = "R", rows = nrow(data), columns = ncol(data))
  )
}
