package io.github.itsoumyad.sync

sealed interface ConflictResolver {
    fun resolve(
        local: SyncOperation,
        serverPayload: Map<String, Any?>,
        serverTimestamp: Long
    ): ResolutionResult

    data class ResolutionResult(
        val acceptedPayload: Map<String, Any?>,
        val strategyApplied: String
    )

    object ClientWins : ConflictResolver {
        override fun resolve(
            local: SyncOperation,
            serverPayload: Map<String, Any?>,
            serverTimestamp: Long
        ): ResolutionResult {
            return ResolutionResult(
                acceptedPayload = local.payload,
                strategyApplied = "ClientWins"
            )
        }
    }

    object ServerWins : ConflictResolver {
        override fun resolve(
            local: SyncOperation,
            serverPayload: Map<String, Any?>,
            serverTimestamp: Long
        ): ResolutionResult {
            return ResolutionResult(
                acceptedPayload = serverPayload,
                strategyApplied = "ServerWins"
            )
        }
    }

    object TimestampResolution : ConflictResolver {
        override fun resolve(
            local: SyncOperation,
            serverPayload: Map<String, Any?>,
            serverTimestamp: Long
        ): ResolutionResult {
            return if (local.clientTimestamp >= serverTimestamp) {
                ResolutionResult(
                    acceptedPayload = local.payload,
                    strategyApplied = "TimestampResolution_Client"
                )
            } else {
                ResolutionResult(
                    acceptedPayload = serverPayload,
                    strategyApplied = "TimestampResolution_Server"
                )
            }
        }
    }
}
