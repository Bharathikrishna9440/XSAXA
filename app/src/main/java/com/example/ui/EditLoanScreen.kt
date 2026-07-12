package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EditLoanScreen(
    loanCycleId: Int,
    viewModel: FinanceViewModel
) {
    val appColors = LocalAppThemeColors.current
    val context = LocalContext.current
    val allLoans by viewModel.allLoanCycles.collectAsStateWithLifecycle()
    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()

    val targetLoan = remember(allLoans, loanCycleId) {
        allLoans.find { it.id == loanCycleId }
    }

    if (targetLoan == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Error: Loan Cycle details not found.")
        }
        return
    }

    val customer = remember(allCustomers, targetLoan.customerId) {
        allCustomers.find { it.id == targetLoan.customerId }
    }

    // Pre-fill states with current values when first loaded
    var loanAmount by rememberSaveable(inputs = arrayOf(targetLoan.id)) { mutableStateOf(targetLoan.loanAmount.toLong().toString()) }
    var interestAmount by rememberSaveable(inputs = arrayOf(targetLoan.id)) { mutableStateOf(targetLoan.interestAmount.toLong().toString()) }
    var deductionAmount by rememberSaveable(inputs = arrayOf(targetLoan.id)) { mutableStateOf(targetLoan.deduction.toLong().toString()) }
    var deductionError by rememberSaveable { mutableStateOf<String?>(null) }
    var loanAmountError by rememberSaveable { mutableStateOf<String?>(null) }
    var weeklyInstalment by rememberSaveable(inputs = arrayOf(targetLoan.id)) { mutableStateOf(targetLoan.weeklyAmount.toLong().toString()) }
    var tenureWeeks by rememberSaveable(inputs = arrayOf(targetLoan.id)) { mutableStateOf(targetLoan.totalWeeks.toString()) }
    val initialDisbursalMode = remember(targetLoan.id) {
        if (targetLoan.notes.startsWith("Multiple - ", ignoreCase = true)) {
            "Multiple"
        } else if (targetLoan.notes.contains("Online", ignoreCase = true) || 
            targetLoan.notes.contains("UPI", ignoreCase = true) || 
            targetLoan.notes.contains("GPay", ignoreCase = true) || 
            targetLoan.notes.contains("PhonePe", ignoreCase = true) || 
            targetLoan.notes.contains("Paytm", ignoreCase = true) || 
            targetLoan.notes.contains("Bank", ignoreCase = true) ||
            targetLoan.notes.contains("Google Pay", ignoreCase = true) ||
            targetLoan.notes.contains("Phone Pe", ignoreCase = true) ||
            targetLoan.notes.contains("IMPS", ignoreCase = true) ||
            targetLoan.notes.contains("NEFT", ignoreCase = true) ||
            targetLoan.notes.contains("RTGS", ignoreCase = true) ||
            targetLoan.notes.contains("Net", ignoreCase = true) ||
            targetLoan.notes.contains("Transfer", ignoreCase = true)) {
            "Online"
        } else {
            "Cash"
        }
    }
    var disbursalMode by rememberSaveable(inputs = arrayOf(targetLoan.id)) { mutableStateOf(initialDisbursalMode) }
    var isMultipleMode by rememberSaveable { mutableStateOf(initialDisbursalMode == "Multiple") }
    
    val initialCashPrincipal = remember(targetLoan.notes) {
        if (targetLoan.notes.startsWith("Multiple - ", ignoreCase = true)) {
            val cashIndex = targetLoan.notes.indexOf("Cash: ₹")
            if (cashIndex != -1) {
                val start = cashIndex + 7
                val end = targetLoan.notes.indexOf(",", start)
                if (end != -1) {
                    targetLoan.notes.substring(start, end).trim()
                } else ""
            } else ""
        } else ""
    }
    val initialOnlinePrincipal = remember(targetLoan.notes) {
        if (targetLoan.notes.startsWith("Multiple - ", ignoreCase = true)) {
            val onlineIndex = targetLoan.notes.indexOf("Online: ₹")
            if (onlineIndex != -1) {
                val start = onlineIndex + 9
                val end = targetLoan.notes.indexOf(".", start)
                if (end != -1) {
                    targetLoan.notes.substring(start, end).trim()
                } else ""
            } else ""
        } else ""
    }
    
    var cashPrincipalStr by rememberSaveable(inputs = arrayOf(targetLoan.id)) { mutableStateOf(initialCashPrincipal) }
    var onlinePrincipalStr by rememberSaveable(inputs = arrayOf(targetLoan.id)) { mutableStateOf(initialOnlinePrincipal) }
    
    val cleanNotes = remember(targetLoan.notes) {
        if (targetLoan.notes.startsWith("Multiple - ", ignoreCase = true)) {
            val parts = targetLoan.notes.split(".")
            if (parts.size > 1) parts[1].trim() else ""
        } else if (targetLoan.notes.startsWith("Online - ", ignoreCase = true)) {
            targetLoan.notes.substring(9)
        } else if (targetLoan.notes.equals("Online", ignoreCase = true)) {
            ""
        } else {
            targetLoan.notes
        }
    }
    var notes by rememberSaveable(inputs = arrayOf(targetLoan.id)) { mutableStateOf(cleanNotes) }
    var isWeeklyInstalmentManuallyEdited by rememberSaveable { mutableStateOf(false) }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var startDateVal by rememberSaveable(inputs = arrayOf(targetLoan.id)) { mutableLongStateOf(targetLoan.startDate) }
    val startDateStr = remember(startDateVal) { sdf.format(java.util.Date(startDateVal)) }

    val showDatePicker = {
        val calendar = Calendar.getInstance().apply { timeInMillis = startDateVal }
        android.app.DatePickerDialog(
            context.findActivity() ?: context,
            { _, year, month, dayOfMonth ->
                val newCalendar = Calendar.getInstance().apply {
                    timeInMillis = startDateVal
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                startDateVal = newCalendar.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val showTimePicker = {
        val calendar = Calendar.getInstance().apply { timeInMillis = startDateVal }
        android.app.TimePickerDialog(
            context.findActivity() ?: context,
            { _, hourOfDay, minute ->
                val newCalendar = Calendar.getInstance().apply {
                    timeInMillis = startDateVal
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                startDateVal = newCalendar.timeInMillis
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Edit Active Loan Account: ${customer?.name ?: "Customer"}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = ColorSlateDark
            )



            if (isMultipleMode) {
                disbursalMode = "Multiple"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = cashPrincipalStr,
                        onValueChange = { input -> 
                            cashPrincipalStr = input.filter { it.isDigit() }
                            loanAmountError = null
                            val p = (cashPrincipalStr.toDoubleOrNull() ?: 0.0) + (onlinePrincipalStr.toDoubleOrNull() ?: 0.0)
                            loanAmount = p.toLong().toString()
                            if (!isWeeklyInstalmentManuallyEdited) {
                                val w = tenureWeeks.toIntOrNull() ?: 0
                                if (w > 0) {
                                    weeklyInstalment = Math.round(p / w).toString()
                                } else {
                                    weeklyInstalment = ""
                                }
                            }
                        },
                        label = { Text("Cash Principal (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp), singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = onlinePrincipalStr,
                        onValueChange = { input -> 
                            onlinePrincipalStr = input.filter { it.isDigit() }
                            loanAmountError = null
                            val p = (cashPrincipalStr.toDoubleOrNull() ?: 0.0) + (onlinePrincipalStr.toDoubleOrNull() ?: 0.0)
                            loanAmount = p.toLong().toString()
                            if (!isWeeklyInstalmentManuallyEdited) {
                                val w = tenureWeeks.toIntOrNull() ?: 0
                                if (w > 0) {
                                    weeklyInstalment = Math.round(p / w).toString()
                                } else {
                                    weeklyInstalment = ""
                                }
                            }
                        },
                        label = { Text("Online Principal (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp), singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (loanAmountError != null) {
                    Text(loanAmountError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            } else {
                OutlinedTextField(
                    value = loanAmount,
                    onValueChange = { input -> 
                        val filtered = input.filter { it.isDigit() }
                        loanAmount = filtered
                        if (!isWeeklyInstalmentManuallyEdited) {
                            val p = filtered.toDoubleOrNull() ?: 0.0
                            val w = tenureWeeks.toIntOrNull() ?: 0
                            if (w > 0) {
                                weeklyInstalment = Math.round(p / w).toString()
                            } else {
                                weeklyInstalment = ""
                            }
                        }
                    },
                    label = { Text("Loan Principal (Udhar Amount) (₹)") },
                    placeholder = { Text("E.g., 10000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = Color.Black,
                        unfocusedLabelColor = Color.Black,
                        focusedPlaceholderColor = Color.DarkGray,
                        unfocusedPlaceholderColor = Color.DarkGray,
                        focusedBorderColor = ColorSlateDark,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = interestAmount,
                onValueChange = { input -> 
                    val filtered = input.filter { it.isDigit() }
                    interestAmount = filtered
                },
                label = { Text("Add-on Interest Fee (₹)") },
                placeholder = { Text("E.g., 2000 (Set 0 if none)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black,
                    focusedPlaceholderColor = Color.DarkGray,
                    unfocusedPlaceholderColor = Color.DarkGray,
                    focusedBorderColor = ColorSlateDark,
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = deductionAmount,
                onValueChange = { input -> 
                    val filtered = input.filter { it.isDigit() }
                    deductionAmount = filtered
                    deductionError = null
                },
                label = { Text("Pre-deducted Fees / Document Charges (₹)") },
                placeholder = { Text("E.g., 500 (Optional deduction)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                isError = deductionError != null,
                supportingText = {
                    if (deductionError != null) {
                        Text(deductionError!!, color = MaterialTheme.colorScheme.error)
                    } else {
                        val p = loanAmount.toDoubleOrNull() ?: 0.0
                        val d = deductionAmount.toDoubleOrNull() ?: 0.0
                        val actual = if (p > 0) p - d else 0.0
                        Text("Actual Cash Disbursed to Client: ₹${actual.toLong()}", color = if (actual < 0) Color.Red else Color.Gray)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black,
                    focusedPlaceholderColor = Color.DarkGray,
                    unfocusedPlaceholderColor = Color.DarkGray,
                    focusedBorderColor = ColorSlateDark,
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                ),
                modifier = Modifier.fillMaxWidth().testTag("loan_deduction_input")
            )

            OutlinedTextField(
                value = weeklyInstalment,
                onValueChange = { input -> 
                    val filtered = input.filter { it.isDigit() }
                    weeklyInstalment = filtered
                    isWeeklyInstalmentManuallyEdited = true
                },
                label = { Text("Weekly Collection Target (Optional) (₹)") },
                placeholder = { Text("E.g., 1200") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black,
                    focusedPlaceholderColor = Color.DarkGray,
                    unfocusedPlaceholderColor = Color.DarkGray,
                    focusedBorderColor = ColorSlateDark,
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = tenureWeeks,
                    onValueChange = { input -> 
                        val filtered = input.filter { it.isDigit() }
                        tenureWeeks = filtered
                        if (!isWeeklyInstalmentManuallyEdited) {
                            val p = loanAmount.toDoubleOrNull() ?: 0.0
                            val w = filtered.toIntOrNull() ?: 0
                            if (w > 0) {
                                weeklyInstalment = Math.round(p / w).toString()
                            } else {
                                weeklyInstalment = ""
                            }
                        }
                    },
                    label = { Text("Weeks tenure") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = Color.Black,
                        unfocusedLabelColor = Color.Black,
                        focusedPlaceholderColor = Color.DarkGray,
                        unfocusedPlaceholderColor = Color.DarkGray,
                        focusedBorderColor = ColorSlateDark,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.weight(1f)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    if (!isMultipleMode) {
                        if (disbursalMode == "Multiple") disbursalMode = "Cash"
                        Text(
                            text = "Disbursal Mode",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(
                                    color = if (disbursalMode == "Cash") Color(0xFFDCFCE7) else Color(0xFFDBEAFE),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (disbursalMode == "Cash") Color(0xFF16A34A) else Color(0xFF2563EB),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    disbursalMode = if (disbursalMode == "Cash") "Online" else "Cash"
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = disbursalMode.uppercase(Locale.getDefault()),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = if (disbursalMode == "Cash") Color(0xFF15803D) else Color(0xFF1D4ED8)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Remarks (Optional)") },
                placeholder = { Text("Remarks details") },
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black,
                    focusedPlaceholderColor = Color.DarkGray,
                    unfocusedPlaceholderColor = Color.DarkGray,
                    focusedBorderColor = ColorSlateDark,
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Disbursal Date & Time input field triggering native pickers
            OutlinedTextField(
                value = startDateStr,
                onValueChange = {},
                label = { Text("Date & Time of Disbursal") },
                readOnly = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black,
                    focusedPlaceholderColor = Color.DarkGray,
                    unfocusedPlaceholderColor = Color.DarkGray,
                    focusedBorderColor = ColorSlateDark,
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker() },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showDatePicker() }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Pick Date", tint = ColorSlateDark)
                        }
                        IconButton(onClick = { showTimePicker() }) {
                            Icon(Icons.Default.AccessTime, contentDescription = "Pick Time", tint = ColorSlateDark)
                        }
                    }
                }
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isMultipleMode = !isMultipleMode }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Multiple Modes (Cash + Online)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ColorSlateDark)
                Checkbox(
                    checked = isMultipleMode,
                    onCheckedChange = { isMultipleMode = it },
                    colors = CheckboxDefaults.colors(checkedColor = appColors.primaryAccent)
                )
            }

            val pForBtn = if (isMultipleMode) {
                (cashPrincipalStr.toDoubleOrNull() ?: 0.0) + (onlinePrincipalStr.toDoubleOrNull() ?: 0.0)
            } else {
                loanAmount.toDoubleOrNull()
            }
            val tForBtn = tenureWeeks.toIntOrNull()
            val wForBtn = if (weeklyInstalment.isBlank()) {
                if (pForBtn != null && tForBtn != null && tForBtn > 0) ((pForBtn + (interestAmount.toDoubleOrNull() ?: 0.0)) / tForBtn) else 0.0
            } else {
                weeklyInstalment.toDoubleOrNull()
            }
            val isSaveEnabled = pForBtn != null && pForBtn > 0.0 && tForBtn != null && tForBtn > 0 && wForBtn != null && wForBtn > 0.0

            Button(
                onClick = {
                    val p = if (isMultipleMode) {
                        (cashPrincipalStr.toDoubleOrNull() ?: 0.0) + (onlinePrincipalStr.toDoubleOrNull() ?: 0.0)
                    } else {
                        loanAmount.toDoubleOrNull()
                    }
                    
                    val i = interestAmount.toDoubleOrNull() ?: 0.0
                    val d = deductionAmount.toDoubleOrNull() ?: 0.0
                    val t = tenureWeeks.toIntOrNull() ?: 10
                    val w = weeklyInstalment.toDoubleOrNull() ?: (if (p != null) ((p + i) / t) else 0.0)
                    
                    val finalNotes = if (isMultipleMode) {
                        "Multiple - Cash: ₹${cashPrincipalStr.ifBlank { "0" }}, Online: ₹${onlinePrincipalStr.ifBlank { "0" }}. $notes"
                    } else if (disbursalMode == "Online") {
                        "Online - $notes"
                    } else {
                        notes
                    }

                    if (p == null || p <= 0.0) {
                        Toast.makeText(context, "Please key in a valid Principal Amount.", Toast.LENGTH_SHORT).show()
                    } else if (d < 0.0) {
                        deductionError = "Deduction must be greater than or equal to 0"
                    } else {
                        viewModel.updateLoanCycle(
                            loanCycleId = loanCycleId,
                            amount = p,
                            interest = i,
                            weeklyInstalment = w,
                            tenureWeeks = t,
                            notes = finalNotes,
                            startDate = startDateVal,
                            deduction = d
                        )
                        Toast.makeText(context, "Loan Account successfully updated!", Toast.LENGTH_SHORT).show()
                        viewModel.navigateBack()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = appColors.primaryAccent,
                    contentColor = Color.White,
                    disabledContainerColor = appColors.primaryAccent.copy(alpha = 0.4f),
                    disabledContentColor = Color.White.copy(alpha = 0.6f)
                ),
                enabled = isSaveEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}
