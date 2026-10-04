tabular_report_handler <- function(manifest, sources, output_directory) {
  if (!length(sources)) stop("r-tabular-summary-report requires at least one source")
  tables <- lapply(sources, function(source) {
    format <- toupper(source$manifest$format %||% "")
    if (!identical(format, "CSV")) stop("r-tabular-summary-report currently supports CSV input")
    list(
      fileName = source$manifest$fileName %||% basename(source$path),
      data = utils::read.csv(source$path, check.names = FALSE, stringsAsFactors = FALSE)
    )
  })

  parameters <- manifest$parameters %||% list()
  title <- parameters[["edb.report.title"]] %||% "EDB R Tabular Summary"
  requested_name <- basename(parameters[["edb.report.outputFileName"]] %||% "r-tabular-summary-report.pdf")
  if (!nzchar(requested_name)) requested_name <- "r-tabular-summary-report.pdf"
  if (!identical(tolower(tools::file_ext(requested_name)), "pdf")) {
    requested_name <- paste0(tools::file_path_sans_ext(requested_name), ".pdf")
  }
  output <- file.path(output_directory, requested_name)

  grDevices::pdf(output, width = 8.27, height = 11.69, onefile = TRUE)
  on.exit(grDevices::dev.off(), add = TRUE)
  for (table in tables) {
    data <- table$data
    numeric_columns <- names(data)[vapply(data, is.numeric, logical(1))]
    lines <- c(
      paste("Source:", table$fileName),
      paste("Rows:", nrow(data), " Columns:", ncol(data)),
      paste("Column names:", paste(names(data), collapse = ", ")),
      if (length(numeric_columns)) paste("Numeric columns:", paste(numeric_columns, collapse = ", ")) else "Numeric columns: none",
      "",
      utils::capture.output(summary(data))
    )
    report_text_page(title, lines)
  }
  grDevices::dev.off()
  on.exit(NULL, add = FALSE)

  list(
    path = output,
    fileName = requested_name,
    mediaType = "application/pdf",
    metadata = list(
      engine = "R",
      generator = "r-tabular-summary-report",
      sourceCount = length(tables),
      totalRows = sum(vapply(tables, function(table) nrow(table$data), integer(1)))
    )
  )
}

report_text_page <- function(title, lines) {
  graphics::plot.new()
  graphics::par(family = "mono", mar = c(1, 1, 1, 1))
  graphics::text(0.02, 0.98, title, adj = c(0, 1), cex = 1.15, font = 2)
  visible <- head(lines, 48L)
  y <- seq(0.92, 0.04, length.out = max(length(visible), 1L))
  if (length(visible)) graphics::text(0.02, y, visible, adj = c(0, 1), cex = 0.68)
}
