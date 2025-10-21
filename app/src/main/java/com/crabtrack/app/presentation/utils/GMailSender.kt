package com.crabtrack.app.presentation.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object GMailSender {
    fun sendEmail(to: String, subject: String, message: String, onDone: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val props = Properties().apply {
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.host", "smtp.gmail.com")
                    put("mail.smtp.port", "587")
                }

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(
                            "YOUR_GMAIL@gmail.com",
                            "YOUR_APP_PASSWORD"  // from https://myaccount.google.com/apppasswords
                        )
                    }
                })

                val mimeMessage = MimeMessage(session).apply {
                    setFrom(InternetAddress("YOUR_GMAIL@gmail.com"))
                    addRecipient(Message.RecipientType.TO, InternetAddress(to))
                    setSubject(subject)
                    setText(message)
                }

                Transport.send(mimeMessage)
                withContext(Dispatchers.Main) { onDone(true) }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onDone(false) }
            }
        }
    }
}