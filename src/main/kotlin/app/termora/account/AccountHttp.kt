package app.termora.account

import app.termora.Application

object AccountHttp {
     val client = Application.httpClient.newBuilder()
        .build()
}