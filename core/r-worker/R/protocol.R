`%||%` <- function(value, fallback) if (is.null(value) || length(value) == 0L) fallback else value

protocol_version <- 1L
file_name_header <- "X-EDB-File-Name"
metadata_header <- "X-EDB-Metadata-Base64"

worker_error <- function(res, status, message) {
  res$status <- as.integer(status)
  res$setHeader("Content-Type", "text/plain; charset=UTF-8")
  charToRaw(as.character(message))
}

authorize_request <- function(req) {
  expected <- Sys.getenv("EDB_WORKER_BEARER_TOKEN", "")
  if (!nzchar(expected)) return(TRUE)
  supplied <- req$HTTP_AUTHORIZATION %||% ""
  identical(supplied, paste("Bearer", expected))
}

multipart_parts <- function(req) {
  body <- req$body %||% list()
  if (!is.list(body)) return(list())
  body
}

find_part <- function(parts, name) {
  if (!is.null(parts[[name]])) return(parts[[name]])
  for (part in parts) {
    if (is.list(part) && identical(part$name %||% "", name)) return(part)
  }
  NULL
}

part_value <- function(part) {
  if (is.null(part)) return(NULL)
  if (!is.list(part)) return(part)
  part$parsed %||% part$value
}

part_raw <- function(part) {
  value <- if (is.list(part)) part$value %||% part$parsed else part
  if (is.raw(value)) return(value)
  if (is.character(value) && length(value) == 1L) return(charToRaw(value))
  NULL
}

parse_manifest <- function(parts) {
  part <- find_part(parts, "manifest")
  if (is.list(part) && is.list(part$parsed) && !is.null(part$parsed$schemaVersion)) {
    manifest <- part$parsed
  } else {
    value <- if (is.list(part)) part$value %||% part$parsed else part
    if (is.raw(value)) value <- rawToChar(value)
    if (!is.character(value) || length(value) != 1L) stop("multipart field 'manifest' must contain JSON text")
    manifest <- jsonlite::fromJSON(value, simplifyVector = FALSE)
  }
  if (!identical(as.integer(manifest$schemaVersion %||% 0L), protocol_version)) stop("unsupported schemaVersion")
  if (!nzchar(manifest$handlerId %||% "")) stop("manifest handlerId is required")
  if (!nzchar(manifest$analysisType %||% "")) stop("manifest analysisType is required")
  sources <- manifest$sources %||% list()
  if (length(sources) < 1L) stop("manifest requires at least one source")
  manifest
}

materialize_sources <- function(parts, manifest, directory) {
  max_bytes <- as.numeric(Sys.getenv("EDB_WORKER_MAX_UPLOAD_BYTES", as.character(10 * 1024^3)))
  total <- 0
  sources <- vector("list", length(manifest$sources))
  for (index in seq_along(manifest$sources)) {
    source <- manifest$sources[[index]]
    part_name <- source$partName %||% ""
    if (!nzchar(part_name)) stop("source partName is required")
    content <- part_raw(find_part(parts, part_name))
    if (is.null(content)) stop(paste("missing source file part:", part_name))
    total <- total + length(content)
    if (!is.finite(max_bytes) || max_bytes <= 0 || total > max_bytes) stop("analysis upload exceeds EDB_WORKER_MAX_UPLOAD_BYTES")
    original <- basename(source$fileName %||% paste0("source-", index))
    extension <- tools::file_ext(original)
    path <- file.path(directory, paste0("source-", index, if (nzchar(extension)) paste0(".", extension) else ""))
    writeBin(content, path)
    sources[[index]] <- list(manifest = source, path = path)
  }
  sources
}

metadata_base64url <- function(metadata) {
  encoded <- jsonlite::base64_enc(charToRaw(jsonlite::toJSON(metadata, auto_unbox = TRUE, null = "null")))
  encoded <- gsub("[[:space:]]+", "", encoded)
  sub("=+$", "", chartr("+/", "-_", encoded))
}

path_is_within <- function(path, root) {
  normalize <- function(value) {
    normalized <- gsub("\\", "/", normalizePath(value, mustWork = TRUE), fixed = TRUE)
    if (.Platform$OS.type == "windows") tolower(normalized) else normalized
  }
  normalized_path <- normalize(path)
  normalized_root <- sub("/+$", "", normalize(root))
  startsWith(normalized_path, paste0(normalized_root, "/"))
}
