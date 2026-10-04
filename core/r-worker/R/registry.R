.operations <- new.env(parent = emptyenv())

register_operation <- function(id, handler) {
  if (!is.character(id) || length(id) != 1L || !grepl("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$", id)) {
    stop("operation id is invalid")
  }
  if (!is.function(handler)) stop("operation handler must be a function")
  if (exists(id, envir = .operations, inherits = FALSE)) stop(paste("operation already registered:", id))
  assign(id, handler, envir = .operations)
}

operation_ids <- function() sort(ls(.operations, all.names = TRUE))

require_operation <- function(id) {
  if (!exists(id, envir = .operations, inherits = FALSE)) stop(paste("unsupported handlerId:", id))
  get(id, envir = .operations, inherits = FALSE)
}
