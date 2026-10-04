#* @apiTitle EDB R Analysis Worker

#* Worker readiness and operation discovery
#* @get /health
#* @serializer unboxedJSON
function() {
  list(status = "ok", protocolVersion = protocol_version, operations = operation_ids())
}

#* Execute a registered analysis operation
#* @post /analyze
#* @parser multi
#* @serializer octet
function(req, res) {
  if (!authorize_request(req)) return(worker_error(res, 401L, "invalid worker bearer token"))
  directory <- tempfile("edb-r-worker-")
  dir.create(directory, recursive = TRUE)
  on.exit(unlink(directory, recursive = TRUE, force = TRUE), add = TRUE)

  tryCatch({
    parts <- multipart_parts(req)
    manifest <- parse_manifest(parts)
    sources <- materialize_sources(parts, manifest, directory)
    artifact <- require_operation(manifest$handlerId)(manifest, sources, directory)
    artifact_path <- normalizePath(artifact$path, mustWork = TRUE)
    if (!path_is_within(artifact_path, directory)) stop("operation returned an artifact outside its temporary directory")
    res$setHeader(file_name_header, artifact$fileName)
    res$setHeader(metadata_header, metadata_base64url(artifact$metadata %||% list()))
    res$setHeader("Content-Type", artifact$mediaType %||% "application/octet-stream")
    readBin(artifact_path, "raw", n = file.info(artifact_path)$size)
  }, error = function(error) {
    worker_error(res, 400L, conditionMessage(error))
  })
}
