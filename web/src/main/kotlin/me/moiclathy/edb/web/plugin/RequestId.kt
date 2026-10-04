package me.moiclathy.edb.web.plugin


import io.ktor.server.application.*
import io.ktor.util.AttributeKey
import java.util.UUID


val RequestIdKey =
    AttributeKey<String>(
        "RequestId"
    )


fun Application.configureRequestId(){


    intercept(ApplicationCallPipeline.Setup){


        val requestId =
            call.request.headers["X-Request-ID"]
                ?: UUID.randomUUID()
                    .toString()


        call.attributes.put(
            RequestIdKey,
            requestId
        )


        call.response.headers.append(
            "X-Request-ID",
            requestId
        )

    }

}