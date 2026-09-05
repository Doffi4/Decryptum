package com.doffi4.doffisecure.autofill

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.doffi4.doffisecure.R
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import com.doffi4.doffisecure.security.AppLocaleManager
import com.doffi4.doffisecure.security.UserSettingsManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AutofillSaveActivity : FragmentActivity() {

    private val passwordRepository: IPasswordRepository by inject()

    override fun attachBaseContext(newBase: Context) {
        val savedLang = UserSettingsManager.getSavedLanguage(newBase)
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase, savedLang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialService = intent.getStringExtra(EXTRA_SERVICE).orEmpty()
        val initialUsername = intent.getStringExtra(EXTRA_USERNAME).orEmpty()
        val initialPassword = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()
        val initialUrl = intent.getStringExtra(EXTRA_URL)

        setContent {
            var service by remember { mutableStateOf(initialService) }
            var username by remember { mutableStateOf(initialUsername) }
            var password by remember { mutableStateOf(initialPassword) }
            var passwordVisible by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.autofill_save_title),
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = service,
                            onValueChange = { service = it },
                            label = { Text(stringResource(R.string.autofill_save_service_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(stringResource(R.string.autofill_save_username_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.autofill_save_password_label)) },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                setResult(Activity.RESULT_CANCELED)
                                finish()
                            }) {
                                Text(stringResource(R.string.autofill_save_btn_discard))
                            }

                            Spacer(Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    if (service.isBlank() || password.isBlank()) {
                                        return@Button
                                    }
                                    lifecycleScope.launch {
                                        val newPassword = Password(
                                            id = 0L,
                                            service = service.trim(),
                                            username = username.trim(),
                                            password = password,
                                            url = initialUrl,
                                            createdAt = System.currentTimeMillis()
                                        )
                                        passwordRepository.addPassword(newPassword)
                                        Toast.makeText(
                                            this@AutofillSaveActivity,
                                            getString(R.string.autofill_saved_success),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        setResult(Activity.RESULT_OK)
                                        finish()
                                    }
                                },
                                enabled = service.isNotBlank() && password.isNotBlank()
                            ) {
                                Text(stringResource(R.string.autofill_save_btn_save))
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_SERVICE = "extra_service"
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_URL = "extra_url"
    }
}
