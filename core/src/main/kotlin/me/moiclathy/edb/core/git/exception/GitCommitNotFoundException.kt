package me.moiclathy.edb.core.git.exception

class GitCommitNotFoundException(commitId: String) :
    RuntimeException("Git commit not found: $commitId")