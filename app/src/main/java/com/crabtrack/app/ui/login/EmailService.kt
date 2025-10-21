package com.crabtrack.app.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

data class EmailJSRequest(
    val service_id: String,
    val template_id: String,
    val user_id: String,
    val template_params: Map<String, String>
)

data class EmailJSResponse(val status: String?, val text: String?)

interface EmailService {
    @Headers("Content-Type: application/json")
    @POST("api/v1.0/email/send")
    fun sendEmail(@Body body: EmailJSRequest): Call<EmailJSResponse>
}
