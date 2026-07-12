package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.Customer
import com.example.data.LoanCycle
import java.util.Locale
import com.example.util.CurrencyFormatter


object SmsService {

    fun sendSmsIntent(
        context: Context,
        phone: String,
        text: String,
        smsPaused: Boolean,
        simSelection: String
    ) {
        if (phone.trim().isEmpty()) {
            Toast.makeText(context, "No phone number available. SMS skipped.", Toast.LENGTH_SHORT).show()
            return
        }
        if (smsPaused) {
            Toast.makeText(context, "All outgoing SMS notifications are temporarily PAUSED globally in Settings.", Toast.LENGTH_LONG).show()
            return
        }
        var cleanPhone = phone.replace("+", "").replace(" ", "").trim()
        if (!cleanPhone.startsWith("91")) {
            cleanPhone = "91$cleanPhone"
        }
        val formattedPhone = "+$cleanPhone"



        var subId: Int? = null
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
                val activeList = subManager?.activeSubscriptionInfoList
                if (activeList != null) {
                    val targetSlot = if (simSelection.contains("SIM 1")) 0 else 1
                    val info = activeList.find { it.simSlotIndex == targetSlot }
                    if (info != null) {
                        subId = info.subscriptionId
                    }
                }
            } catch (e: SecurityException) {
                // Ignore security exception if permission not granted
            }
        }

        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$formattedPhone")
                putExtra("sms_body", text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                val slot = if (simSelection.contains("SIM 1")) 0 else 1
                val fallbackSubId = if (slot == 0) 1 else 2
                val finalSubId = subId ?: fallbackSubId

                // Standard Android SDK key (API 22+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                    putExtra("android.telephony.extra.SUBSCRIPTION_INDEX", finalSubId)
                }

                // OEM & common messaging app dual-SIM extras
                putExtra("simSlot", slot)
                putExtra("sim_slot", slot)
                putExtra("slot", slot)
                putExtra("phone", slot)
                putExtra("com.android.phone.extra.slot", slot)
                putExtra("com.android.phone.force.slot", slot)
                
                putExtra("subscription", finalSubId)
                putExtra("sub_id", finalSubId)
                putExtra("subscription_id", finalSubId)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Launch SMS Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getUpiLink(context: Context, upiId: String, customerName: String): String {
        val prefs = context.getSharedPreferences("weekly_finance_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("upi_link_sharing", true)
        val rawUpiLink = prefs.getString("upi_link", "upi://pay?pa=9440736893@ptyes&pn=Muneeswaran%20P") ?: "upi://pay?pa=9440736893@ptyes&pn=Muneeswaran%20P"
        return if (enabled) {
            rawUpiLink
        } else {
            ""
        }
    }

    fun triggerNewLoanSms(
        context: Context,
        customer: Customer,
        loan: LoanCycle,
        template: String,
        upiId: String,
        smsPaused: Boolean,
        simSelection: String,
        phone: String = customer.phone
    ) {
        val totalDue = loan.loanAmount + loan.interestAmount
        val upiLink = getUpiLink(context, upiId, customer.name)
        val formattedDate = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        val busName = context.getSharedPreferences("weekly_finance_prefs", Context.MODE_PRIVATE).getString("business_name", "MD Finance") ?: "MD Finance"

        val text = template
            .replace("{customer}", customer.name)
            .replace("{name}", customer.name)
            .replace("{amount}", CurrencyFormatter.format(loan.loanAmount))
            .replace("{interest}", CurrencyFormatter.format(loan.interestAmount))
            .replace("{weekly}", CurrencyFormatter.format(loan.weeklyAmount))
            .replace("{inst_amt}", CurrencyFormatter.format(loan.weeklyAmount))
            .replace("{outstanding}", CurrencyFormatter.format(totalDue))
            .replace("{balance}", CurrencyFormatter.format(totalDue))
            .replace("{upi}", upiId)
            .replace("{upi_link}", upiLink)
            .replace("{business}", busName)
            .replace("{date}", formattedDate)

        sendSmsIntent(context, phone, text, smsPaused, simSelection)
    }

    fun triggerPaymentEntrySms(
        context: Context,
        customer: Customer,
        loan: LoanCycle,
        paymentAmount: Double,
        weekNum: Int,
        template: String,
        upiId: String,
        smsPaused: Boolean,
        simSelection: String,
        phone: String = customer.phone
    ) {
        val totalDue = loan.loanAmount + loan.interestAmount
        val currentPaid = loan.paidAmount + paymentAmount
        val remaining = maxOf(0.0, totalDue - currentPaid)
        val upiLink = getUpiLink(context, upiId, customer.name)
        val formattedDate = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        val busName = context.getSharedPreferences("weekly_finance_prefs", Context.MODE_PRIVATE).getString("business_name", "MD Finance") ?: "MD Finance"

        val text = template
            .replace("{customer}", customer.name)
            .replace("{name}", customer.name)
            .replace("{amount}", CurrencyFormatter.format(paymentAmount))
            .replace("{p_amount}", CurrencyFormatter.format(paymentAmount))
            .replace("{week}", weekNum.toString())
            .replace("{inst_amt}", CurrencyFormatter.format(loan.weeklyAmount))
            .replace("{outstanding}", CurrencyFormatter.format(remaining))
            .replace("{balance}", CurrencyFormatter.format(remaining))
            .replace("{upi}", upiId)
            .replace("{upi_link}", upiLink)
            .replace("{business}", busName)
            .replace("{date}", formattedDate)

        sendSmsIntent(context, phone, text, smsPaused, simSelection)
    }

    fun triggerManualReminderSms(
        context: Context,
        customer: Customer,
        loan: LoanCycle,
        template: String,
        upiId: String,
        smsPaused: Boolean,
        simSelection: String,
        phone: String = customer.phone
    ) {
        val totalDue = loan.loanAmount + loan.interestAmount
        val remaining = maxOf(0.0, totalDue - loan.paidAmount)
        val upiLink = getUpiLink(context, upiId, customer.name)
        val formattedDate = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        val busName = context.getSharedPreferences("weekly_finance_prefs", Context.MODE_PRIVATE).getString("business_name", "MD Finance") ?: "MD Finance"

        val text = template
            .replace("{customer}", customer.name)
            .replace("{name}", customer.name)
            .replace("{weekly}", CurrencyFormatter.format(loan.weeklyAmount))
            .replace("{inst_amt}", CurrencyFormatter.format(loan.weeklyAmount))
            .replace("{outstanding}", CurrencyFormatter.format(remaining))
            .replace("{balance}", CurrencyFormatter.format(remaining))
            .replace("{upi}", upiId)
            .replace("{upi_link}", upiLink)
            .replace("{business}", busName)
            .replace("{date}", formattedDate)

        sendSmsIntent(context, phone, text, smsPaused, simSelection)
    }

    fun triggerWhatsappWebFallback(context: Context, phone: String, text: String) {
        if (phone.trim().isEmpty()) {
            Toast.makeText(context, "No phone number available. WhatsApp skipped.", Toast.LENGTH_SHORT).show()
            return
        }
        var cleanPhone = phone.filter { it.isDigit() }
        if (cleanPhone.isNotBlank() && cleanPhone.length == 10) {
            cleanPhone = "91$cleanPhone"
        }
        try {
            val url = if (cleanPhone.isNotBlank()) {
                "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(text)}"
            } else {
                "https://api.whatsapp.com/send?text=${Uri.encode(text)}"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Launch WhatsApp Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerWhatsappReminder(
        context: Context,
        customer: Customer,
        loan: LoanCycle,
        template: String,
        upiId: String,
        qrImageUri: String?,
        phone: String = customer.phone
    ) {
        if (phone.trim().isEmpty()) {
            Toast.makeText(context, "No phone number available. WhatsApp skipped.", Toast.LENGTH_SHORT).show()
            return
        }
        val totalDue = loan.loanAmount + loan.interestAmount
        val remaining = maxOf(0.0, totalDue - loan.paidAmount)
        val upiLink = getUpiLink(context, upiId, customer.name)
        val formattedDate = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        val busName = context.getSharedPreferences("weekly_finance_prefs", Context.MODE_PRIVATE).getString("business_name", "MD Finance") ?: "MD Finance"

        val text = template
            .replace("{customer}", customer.name)
            .replace("{name}", customer.name)
            .replace("{weekly}", CurrencyFormatter.format(loan.weeklyAmount))
            .replace("{inst_amt}", CurrencyFormatter.format(loan.weeklyAmount))
            .replace("{outstanding}", CurrencyFormatter.format(remaining))
            .replace("{balance}", CurrencyFormatter.format(remaining))
            .replace("{upi}", upiId)
            .replace("{upi_link}", upiLink)
            .replace("{business}", busName)
            .replace("{date}", formattedDate)

        var cleanPhone = phone.filter { it.isDigit() }
        if (cleanPhone.isNotBlank()) {
            if (cleanPhone.length == 10) {
                cleanPhone = "91$cleanPhone"
            }
        }

        if (!qrImageUri.isNullOrBlank()) {
            try {
                var imageUri = Uri.parse(qrImageUri)
                if (imageUri.scheme == "file" || imageUri.scheme.isNullOrEmpty()) {
                    val path = imageUri.path ?: qrImageUri
                    val file = java.io.File(path)
                    imageUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
                
                // 1. Try launching through official WhatsApp app
                val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    putExtra(Intent.EXTRA_TEXT, text)
                    if (cleanPhone.isNotBlank()) {
                        putExtra("jid", "$cleanPhone@s.whatsapp.net")
                    }
                    clipData = android.content.ClipData.newRawUri("", imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage("com.whatsapp")
                }

                // 2. Try launching through WhatsApp Business
                val whatsappBusinessIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    putExtra(Intent.EXTRA_TEXT, text)
                    if (cleanPhone.isNotBlank()) {
                        putExtra("jid", "$cleanPhone@s.whatsapp.net")
                    }
                    clipData = android.content.ClipData.newRawUri("", imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage("com.whatsapp.w4b")
                }

                // 3. Fallback generic system intent
                val genericShareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    putExtra(Intent.EXTRA_TEXT, text)
                    clipData = android.content.ClipData.newRawUri("", imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                try {
                    context.startActivity(whatsappIntent)
                } catch (e1: Exception) {
                    try {
                        context.startActivity(whatsappBusinessIntent)
                    } catch (e2: Exception) {
                        try {
                            val chooser = Intent.createChooser(genericShareIntent, "Attach QR & Send Link")
                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(chooser)
                        } catch (e3: Exception) {
                            triggerWhatsappWebFallback(context, cleanPhone, text)
                        }
                    }
                }
            } catch (e: Exception) {
                triggerWhatsappWebFallback(context, cleanPhone, text)
            }
        } else {
            triggerWhatsappWebFallback(context, cleanPhone, text)
        }
    }
}
