# Firebase Login System - Migration Instructions

This document describes all the custom code added on top of the original Home Assistant Android app to implement a Firebase-based login system with automated WebView login injection.

---

## OVERVIEW OF THE FLOW

```
Splash (WelcomeFragment)
  → Location permission check
  → LoginFragment (Firebase email/password login)
    → Fetches user data from Firestore (email, subscription expiry)
    → Fetches server passwords from "serverPasswords" subcollection
    → If multiple servers → shows ServerListChooser dialog
    → Fetches server details (externalUrl, internalUrl) from "servers" collection
    → Validates subscription via server time API
    → Decrypts password using AES-256-CBC
    → Stores credentials in HassioUserSession singleton
  → ManualSetupView (auto-fills externalUrl and auto-clicks connect)
  → AuthenticationFragment (WebView auto-login injection)
    → JavaScript injects username/password into HA login form
    → Auto-submits, shows loading overlay
  → MobileAppIntegrationFragment
    → Auto-adds WiFi SSID + internalUrl to all servers
  → WebViewActivity
    → Also triggers autoAddCurrentWifiToAllServers if internalUrl is set
```

---

## FILES TO CREATE (New Files)

### 1. `LoginFragment.kt`
**Path:** `app/src/main/java/io/homeassistant/companion/android/onboarding/login/LoginFragment.kt`

```kotlin
package io.homeassistant.companion.android.onboarding.login

import ServerTimeFetchService
import ServerTimeFetchServiceImpl
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryFragment
import io.homeassistant.companion.android.onboarding.manual.ManualSetupFragment
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginFragment : Fragment() {

    private var isLoading by mutableStateOf(false)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    LoginView(
                        onLoginClick = { username, password -> loginUserWithFirebase(username, password)},
                        isLoading = isLoading
                    )
                }
            }
        }
    }

    private fun loginUserWithFirebase(username: String, password: String) {
        if (validateCredentials(username, password)) {
            Log.d("Firestore", "loginUserWithFirebase")
            isLoading = true
            val auth = FirebaseAuth.getInstance()
            val serverTimeService: ServerTimeFetchService = ServerTimeFetchServiceImpl()
            // Use coroutines to handle Firebase calls
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    auth.signInWithEmailAndPassword(username, password).await()
                    Log.d("Firestore", "SignInSuccess2")

                    val userId = auth.currentUser?.uid
                    Log.d("Firestore", "user id ${userId}")
                    isLoading = false

                    if (userId != null) {
                        Log.d("Firestore", "calling getWebViewCredentials")
                        // Fetch the external URL, webview username, and password from Firebase
                        val webviewCredentials = getWebViewCredentials(userId)
                        Log.d("Firestore", "result getWebViewCredentials ${webviewCredentials?.expirationDate}")

                        if (webviewCredentials != null) {
                            val currentTime = serverTimeService.fetchServerTime()

                            if (currentTime == null) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    Toast.makeText(requireContext(), "Failed to fetch server time", Toast.LENGTH_LONG).show()
                                }
                                return@launch
                            }

                            // Check expiration date
                            if (webviewCredentials.expirationDate == null || webviewCredentials.expirationDate < currentTime) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    Toast.makeText(requireContext(), "You don't have a subscription", Toast.LENGTH_LONG).show()
                                }
                                FirebaseAuth.getInstance().signOut()
                                return@launch
                            }

                            CoroutineScope(Dispatchers.Main).launch {
                                Toast.makeText(requireContext(), "Login successful", Toast.LENGTH_SHORT).show()
                            }

                            Log.d("Firestore", "External URL: ${webviewCredentials.externalUrl}")
                            Log.d("Firestore", "Webview Username: ${webviewCredentials.username}")
                            Log.d("Firestore", "Webview Password: ${webviewCredentials.password}")

                            // Save credentials to UserSession
                            HassioUserSession.externalUrl = webviewCredentials.externalUrl
                            HassioUserSession.internalUrl = webviewCredentials.internalUrl
                            HassioUserSession.webviewUsername = webviewCredentials.username

                            if(webviewCredentials.password.isNullOrEmpty()){
                                Log.e("LoginFragment", "webviewCredentials Password is null or empty")
                                CoroutineScope(Dispatchers.Main).launch {
                                    Toast.makeText(requireContext(), "WebCred Pass is Empty", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }

                            try {
                                // Create an instance of CryptoUtil
                                val cryptoUtil = CryptoUtil()

                                // Attempt to decrypt the password
                                val decryptedPassword = cryptoUtil.aes256CbcPkcs7Decrypt(
                                webviewCredentials.password.toString()
                                )

                                // Save decrypted password to the session
                                HassioUserSession.webviewPassword = decryptedPassword

                            } catch (e: Exception) {
                                // Log error and show a Toast message for decryption failure
                                Log.e("CryptoUtil", "Password decryption failed: ${e.message}")
                                CoroutineScope(Dispatchers.Main).launch {
                                    Toast.makeText(requireContext(), "Password decryption failed", Toast.LENGTH_LONG).show()
                                }
                                return@launch // Exit if decryption fails
                            }

                            loginNavigation() // Proceed with the next step
                        } else {
                            Log.d("Firestore", "No webview credentials found for this user.")
                        }
                    } else {
                        Log.d("FirebaseAuth", "User ID is null after login.")
                    }
                } catch (e: Exception) {
                    isLoading = false
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(requireContext(), "Authentication failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun loginNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && HassioUserSession.externalUrl == null) {
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.content, DiscoveryFragment::class.java, null)
                .addToBackStack("Welcome")
                .commit()
        } else {
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.content, ManualSetupFragment::class.java, null)
                .addToBackStack("Welcome")
                .commit()
        }
    }

    private fun validateCredentials(username: String, password: String): Boolean {
        return when {
            username.isEmpty() -> {
                Toast.makeText(requireContext(), "Email cannot be empty", Toast.LENGTH_LONG).show()
                false
            }
            password.isEmpty() -> {
                Toast.makeText(requireContext(), "Password cannot be empty", Toast.LENGTH_LONG).show()
                false
            }
            !Patterns.EMAIL_ADDRESS.matcher(username).matches() -> {
                Toast.makeText(requireContext(), "Invalid email format", Toast.LENGTH_LONG).show()
                false
            }
            else -> true
        }
    }

    data class WebviewCredentials(
        val externalUrl: String?,
        val internalUrl: String?,
        val username: String?,
        val password: String?,
        val expirationDate: Long?
    )

    private suspend fun getWebViewCredentials(userId: String): WebviewCredentials? {
        val db = FirebaseFirestore.getInstance()
        Log.d("Firestore", "getWebViewCredentials init db")
        return try {
            val userDoc = db.collection("users").document(userId).get().await()
            if (userDoc.exists()) {
                // Get the email as the username
                val webviewUsername = userDoc.getString("email")
                // Get the expiration date from subscription.expiresAt
                val expirationDate = userDoc.getTimestamp("subscription.expiresAt")?.toDate()?.time

                var webviewPassword: String? = null
                var externalUrl: String? = null
                var internalUrl: String? = null

                // Access the serverPasswords subcollection and get the first document
                val serverPasswordsCollection = userDoc.reference.collection("serverPasswords")
                val serverPasswordsSnapshot = serverPasswordsCollection.get().await()

                Log.d("Firestore", "serverpass size: ${serverPasswordsSnapshot.size()}")
                var firstServerPasswordDoc: DocumentSnapshot?
                if (serverPasswordsSnapshot.documents.size > 1) {
                    Log.d("Firestore", "Multiple serverPasswords found for user: $userId")
                    val selectedDoc = ServerListChooser.showServerSelectionDialog(requireContext(), serverPasswordsSnapshot.documents)
                    firstServerPasswordDoc = selectedDoc
                } else {
                        firstServerPasswordDoc = serverPasswordsSnapshot.documents.firstOrNull()
                }

                if (firstServerPasswordDoc != null) {
                    // Get the encrypted password
                    webviewPassword = firstServerPasswordDoc.getString("encryptedPass")

                    // Get the server ID (document ID)
                    val serverId = firstServerPasswordDoc.id
                    Log.d("Firestore", "First Server DOC: $serverId")

                    // Access the servers collection to get the externalUrl
                    val serverDoc = db.collection("servers").document(serverId).get().await()
                    if (serverDoc.exists()) {
                        externalUrl = serverDoc.getString("externalUrl")
                        internalUrl = serverDoc.getString("internalUrl")

                        // After retrieving externalUrl from serverDoc
                        if (externalUrl != null && !externalUrl.startsWith("http://") && !externalUrl.startsWith("https://")) {
                            externalUrl = "https://$externalUrl"
                        }

                        if (internalUrl != null && !internalUrl.startsWith("http://") && !internalUrl.startsWith("https://")) {
                            internalUrl = "http://$internalUrl"
                        }

                    } else {
                        Log.d("Firestore", "No server found with ID: $serverId")
                    }
                } else {
                    Log.d("Firestore", "No serverPasswords documents for user: $userId")
                }

                Log.d(
                    "Firestore:LoginFragment",
                    "webviewPassword: $webviewPassword, expirationDate: $expirationDate, externalUrl: $externalUrl"
                )

                WebviewCredentials(externalUrl, internalUrl, webviewUsername, webviewPassword, expirationDate)
            } else {
                Log.d("Firestore", "No such document.")
                null
            }
        } catch (e: Exception) {
            Log.e("Firestore", "Error getting document: ", e)
            null
        }
    }
}
```

---

### 2. `LoginView.kt`
**Path:** `app/src/main/java/io/homeassistant/companion/android/onboarding/login/LoginView.kt`

```kotlin
package io.homeassistant.companion.android.onboarding.login

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.R

@Preview
@Composable
fun LoginView(
    onLoginClick: (String, String) -> Unit = { _, _ -> },
    isLoading: Boolean = false
) {

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current

    Surface {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.TopCenter) {
                Image(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction = 0.44f),
                    painter = painterResource(id = R.drawable.login_bg_shape),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                )

                Row(
                    modifier = Modifier.padding(top = 55.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ){
                    Icon(
                        tint = Color.White,
                        modifier = Modifier.size(100.dp),
                        painter = painterResource(id = R.drawable.my_smart_home_icon), contentDescription = null)
                }

                Text(
                    style = MaterialTheme.typography.h5.copy(
                        color = colorResource(id = io.homeassistant.companion.android.common.R.color.colorPrimary),
                    ),
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .align(alignment = Alignment.BottomCenter),
                    text = stringResource(id = io.homeassistant.companion.android.common.R.string.login),
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    visualTransformation = PasswordVisualTransformation()
                )
                if(isLoading){
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                }else{
                    Button(
                        onClick = { onLoginClick(email, password) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterHorizontally),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorResource(id = io.homeassistant.companion.android.common.R.color.colorPrimary)
                        )
                    ) {
                        Text(
                            stringResource(id = io.homeassistant.companion.android.common.R.string.login),
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
            val context = LocalContext.current

            Text(
                text = "Need Help?",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp)
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://mysmarthomes.us/"))
                        context.startActivity(intent)
                    },
                color = colorResource(id = io.homeassistant.companion.android.common.R.color.colorPrimary),
                )
        }
    }
}
```

---

### 3. `HassioUserSession.kt`
**Path:** `app/src/main/java/io/homeassistant/companion/android/onboarding/login/HassioUserSession.kt`

```kotlin
package io.homeassistant.companion.android.onboarding.login

object HassioUserSession {
    var externalUrl: String? = null
    var internalUrl: String? = null
    var webviewUsername: String? = null
    var webviewPassword: String? = null
}
```

---

### 4. `CryptoUtil.kt`
**Path:** `app/src/main/java/io/homeassistant/companion/android/onboarding/login/CryptoUtil.kt`

```kotlin
package io.homeassistant.companion.android.onboarding.login
import io.homeassistant.companion.android.BuildConfig
import java.util.Base64
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoUtil {

    fun aes256CbcPkcs7Decrypt(encryptedBase64: String): String {
        val encryptedData = Base64.getDecoder().decode(encryptedBase64)

        val secretKeyString = BuildConfig.MSH_AES_KEY
        val ivString = BuildConfig.MSH_AES_IV

        if (secretKeyString.length != 32) throw IllegalArgumentException("AES key must be 32 characters long for AES-256.")
        if (ivString.length != 16) throw IllegalArgumentException("AES IV must be 16 characters long for AES-CBC.")

        val secretKey: Key = SecretKeySpec(secretKeyString.toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(ivString.toByteArray(Charsets.UTF_8))

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

        val decryptedBytes = cipher.doFinal(encryptedData)

        return String(decryptedBytes, Charsets.UTF_8)
    }
}
```

---

### 5. `ServerListChooser.kt`
**Path:** `app/src/main/java/io/homeassistant/companion/android/onboarding/login/ServerListChooser.kt`

```kotlin
package io.homeassistant.companion.android.onboarding.login
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.app.AlertDialog
import android.content.Context
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import io.homeassistant.companion.android.R

class ServerListChooser {
    data class ServerItem(val name: String, val doc: DocumentSnapshot)

    companion object {
        lateinit var dialog: AlertDialog
        public suspend fun showServerSelectionDialog(
            context: Context,
            serverPasswords: List<DocumentSnapshot>
        ): DocumentSnapshot = suspendCoroutine { continuation ->
            val builder = AlertDialog.Builder(context)
            val inflater = LayoutInflater.from(context)

            val dialogView = inflater.inflate(R.layout.msh_server_selection_dialog, null)
            val listView = dialogView.findViewById<ListView>(R.id.server_list)

            class ServerAdapter(
                context: Context,
                private val servers: List<ServerItem>
            ) : ArrayAdapter<ServerItem>(context, R.layout.msh_server_list_item, servers) {

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: inflater.inflate(R.layout.msh_server_list_item, parent, false)

                    val serverIcon = view.findViewById<ImageView>(R.id.server_icon)
                    val serverName = view.findViewById<TextView>(R.id.server_name)
                    val serverUrl = view.findViewById<TextView>(R.id.server_url)
                    val serverId = view.findViewById<TextView>(R.id.server_id)

                    val server = servers[position]

                    serverIcon.setImageResource(R.drawable.app_icon_round)
                    serverName.text = server.name
                    serverId.text = server.doc.id

                    return view
                }
            }

            CoroutineScope(Dispatchers.Main).launch {
                val serverItems = mutableListOf<ServerItem>()

                serverPasswords.forEach { doc ->
                    val serverId = doc.id
                    try {
                        val serverDoc = FirebaseFirestore.getInstance()
                            .collection("servers")
                            .document(serverId)
                            .get()
                            .await()

                        val serverName = serverDoc.getString("homeName") ?: serverId
                        serverItems.add(ServerItem(serverName, doc))
                    } catch (e: Exception) {
                        serverItems.add(ServerItem(serverId, doc))
                    }
                }

                val adapter = ServerAdapter(context, serverItems)
                listView.adapter = adapter

                listView.setOnItemClickListener { _, _, position, _ ->
                    dialog.dismiss()
                    continuation.resume(serverItems[position].doc)
                }

                dialog = builder.setTitle("Select Server")
                    .setView(dialogView)
                    .setOnCancelListener {
                        continuation.resumeWithException(Exception("Server selection cancelled"))
                    }
                    .create()
                dialog.show()
            }
        }
    }
}
```

---

### 6. `ServerTimeFetchService.kt`
**Path:** `app/src/main/java/io/homeassistant/companion/android/onboarding/login/ServerTimeFetchService.kt`

> **NOTE:** In the original project this file has no package declaration (it's in the default package). You should add the proper package for your project.

```kotlin
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import android.util.Log

interface ServerTimeFetchService {
    suspend fun fetchServerTime(): Long?
}

class ServerTimeFetchServiceImpl : ServerTimeFetchService {
    private val client = OkHttpClient()
    private val url = "https://getservertime-jrskleaqea-uc.a.run.app"

    override suspend fun fetchServerTime(): Long? {
        print("fetching server time.")
        return try {
            val request = Request.Builder().url(url).build()
            val response: Response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val jsonResponse = JSONObject(response.body?.string() ?: "")
                val timeString = jsonResponse.getString("time")
                val formatter = DateTimeFormatter.ISO_DATE_TIME
                val serverTime = OffsetDateTime.parse(timeString, formatter).toInstant().toEpochMilli()
                serverTime
            } else {
                Log.e("ServerTimeFetcher", "Failed with code: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e("ServerTimeFetcher", "Exception: ${e.message}")
            null
        }
    }
}
```

---

### 7. `MSHAutoWifiManager.kt`
**Path:** `app/src/main/java/io/homeassistant/companion/android/util/MSHAutoWifiManager.kt`

```kotlin
package io.homeassistant.companion.android.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.wifi.WifiHelper
import io.homeassistant.companion.android.onboarding.login.HassioUserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MSHAutoWifiManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverManager: ServerManager,
    private val wifiHelper: WifiHelper
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun autoAddCurrentWifi(serverId: Int) {
        scope.launch {
            try {
                if (!wifiHelper.isUsingWifi()) {
                    Log.d(TAG, "Not using WiFi, skipping auto-add")
                    return@launch
                }

                val currentSsid = wifiHelper.getWifiSsid()?.removeSurrounding("\"")
                if (currentSsid.isNullOrBlank()) {
                    Log.d(TAG, "No valid SSID found")
                    return@launch
                }

                val server = serverManager.getServer(serverId) ?: run {
                    Log.e(TAG, "Server not found for ID: $serverId")
                    return@launch
                }

                if (server.connection.internalSsids.contains(currentSsid)) {
                    Log.d(TAG, "SSID $currentSsid already in list")
                    return@launch
                }

                val updatedSsids = (server.connection.internalSsids + currentSsid).sorted()

                serverManager.updateServer(
                    server.copy(
                        connection = server.connection.copy(
                            internalSsids = updatedSsids
                        )
                    )
                )

                Log.d(TAG, "Successfully added SSID: $currentSsid")
            } catch (e: Exception) {
                Log.e(TAG, "Error auto-adding WiFi SSID", e)
            }
        }
    }

    fun autoAddCurrentWifiToAllServers() {
        val internalUrl = HassioUserSession.internalUrl ?: "";
        scope.launch {
            try {
                val servers = serverManager.defaultServers
                for (server in servers) {
                    autoAddCurrentWifi(server.id)
                    updateServerInternalUrl(server.id, internalUrl);
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error auto-adding WiFi SSID to all servers", e)
            }
        }
    }

    fun updateServerInternalUrl(serverId: Int, newInternalUrl: String) {
        scope.launch {
            try {
                val server = serverManager.getServer(serverId) ?: run {
                    Log.e(TAG, "Server not found for ID: $serverId")
                    return@launch
                }

                val updatedServer = server.copy(
                    connection = server.connection.copy(
                        internalUrl = newInternalUrl
                    )
                )

                serverManager.updateServer(updatedServer)
                Log.d(TAG, "Successfully updated internal URL to: $newInternalUrl")

            } catch (e: Exception) {
                Log.e(TAG, "Error updating internal URL", e)
            }
        }
    }

    companion object {
        private const val TAG = "AutoWifiManager"
    }
}
```

---

### 8. `VersionChecker.kt`
**Path:** `app/src/main/java/io/homeassistant/companion/android/util/VersionChecker.kt`

```kotlin
package io.homeassistant.companion.android.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class VersionChecker {

    companion object {
        val THIS_APP_VERSION_CODE = 1;
        private val VERSION_URL = "https://my-smart-homes.github.io/app-landing/version.json?rand=${System.currentTimeMillis()}"

        suspend fun checkForUpdate(context: Context, onUpdateAvailable: () -> Unit) {
            try {
                val url = URL(VERSION_URL)
                withContext(Dispatchers.IO) {
                    (url.openConnection() as? HttpURLConnection)?.run {
                        requestMethod = "GET"
                        inputStream.bufferedReader().use {
                            val response = it.readText()
                            val jsonObject = JSONObject(response)
                            val latestVersionCode = jsonObject.getInt("latest_version_code")
                            if (latestVersionCode > THIS_APP_VERSION_CODE) {
                                withContext(Dispatchers.Main) { onUpdateAvailable() }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VersionChecker", "Failed to check version", e)
            }
        }
    }
}
```

---

### 9. `UpdateDialogUtil.kt` (DialogUtils)
**Path:** `app/src/main/java/io/homeassistant/companion/android/util/UpdateDialogUtil.kt`

```kotlin
package io.homeassistant.companion.android.util

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import android.net.Uri

object DialogUtils {

    fun showUpdateDialog(context: Context) {
        AlertDialog.Builder(context).apply {
            setTitle("Update Available")
            setMessage("A new version is available. Please update to continue.")
            setPositiveButton("Update") { _, _ ->
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://my-smart-homes.github.io/app-landing/"))
                context.startActivity(browserIntent)
            }
            setNegativeButton("Later", null)
            show()
        }
    }
}
```

---

### 10. `LocationPermissionInfoHandler.kt`
**Path:** `common/src/main/java/io/homeassistant/companion/android/common/util/LocationPermissionInfoHandler.kt`

```kotlin
package io.homeassistant.companion.android.common.util

import android.Manifest
import android.content.Context
import androidx.appcompat.app.AlertDialog
import io.homeassistant.companion.android.common.R as commonR

object LocationPermissionInfoHandler {

    fun showLocationPermInfoDialogIfNeeded(context: Context, permissions: Array<String>, continueYesCallback: () -> Unit, continueNoCallback: (() -> Unit)? = null) {
        if (permissions.any {
                it == Manifest.permission.ACCESS_FINE_LOCATION || it == Manifest.permission.ACCESS_BACKGROUND_LOCATION
            }
        ) {
            AlertDialog.Builder(context)
                .setTitle(commonR.string.location_perm_info_title)
                .setMessage(context.getString(commonR.string.location_perm_info_message))
                .setPositiveButton(commonR.string.confirm_positive) { dialog, _ ->
                    dialog.dismiss()
                    continueYesCallback()
                }
                .setNegativeButton(commonR.string.confirm_negative) { dialog, _ ->
                    dialog.dismiss()
                    if (continueNoCallback != null) continueNoCallback()
                }
                .show()
        } else {
            continueYesCallback()
        }
    }
}
```

---

## XML RESOURCE FILES TO CREATE

### 11. `login_bg_shape.xml`
**Path:** `app/src/main/res/drawable/login_bg_shape.xml`

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="375dp"
    android:height="311dp"
    android:viewportWidth="375"
    android:viewportHeight="311">
  <path
      android:pathData="M375,0H0V311L171.26,192.24C182.23,184.63 196.77,184.63 207.74,192.24L375,308.23V0Z"
      android:fillColor="#3EBDF1"
      android:fillType="evenOdd"/>
</vector>
```

### 12. `my_smart_home_icon.xml`
**Path:** `app/src/main/res/drawable/my_smart_home_icon.xml`

This is a vector drawable of the MSH logo (house + wifi signals + "MY SMART HOMES" text). Copy the full file from the source project.

### 13. `msh_server_selection_dialog.xml`
**Path:** `app/src/main/res/layout/msh_server_selection_dialog.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<ListView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/server_list"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="16dp" />
```

### 14. `msh_server_list_item.xml`
**Path:** `app/src/main/res/layout/msh_server_list_item.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="8dp">

    <ImageView
        android:id="@+id/server_icon"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:layout_marginEnd="16dp"
        android:scaleType="fitCenter" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <TextView
            android:id="@+id/server_name"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="16sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/server_id"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="12sp"
            android:textColor="#666666" />

        <TextView
            android:id="@+id/server_url"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="12sp"
            android:textColor="#666666" />
    </LinearLayout>
</LinearLayout>
```

---

## FILES TO MODIFY (Existing Files)

### 15. `WelcomeFragment.kt` — Redirect splash to LoginFragment
**Path:** `app/src/main/java/io/homeassistant/companion/android/onboarding/welcome/WelcomeFragment.kt`

**Changes:**
- Add import: `import io.homeassistant.companion.android.onboarding.login.LoginFragment`
- Add import: `import io.homeassistant.companion.android.common.util.LocationPermissionInfoHandler`
- Add import: `import io.homeassistant.companion.android.util.DialogUtils`
- Add import: `import io.homeassistant.companion.android.util.VersionChecker`
- Replace the `welcomeNavigation()` function body: Instead of navigating to DiscoveryFragment/ManualSetupFragment, it now checks location permissions and then calls `navigateToLogin()`
- Add `navigateToLogin()` function that replaces content with `LoginFragment`
- Add `checkForAppUpdate()` function that calls `VersionChecker.checkForUpdate()` and shows `DialogUtils.showUpdateDialog()` on update available
- The original navigation to DiscoveryFragment/ManualSetupFragment is commented out

**Full replacement for `welcomeNavigation()`:**
```kotlin
private fun welcomeNavigation() {
    val permissionsToCheck = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        }
        else -> {
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    if (!checkPermissions(permissionsToCheck)) {
        LocationPermissionInfoHandler.showLocationPermInfoDialogIfNeeded(
            requireContext(),
            permissionsToCheck,
            continueYesCallback = {
                requestLocationPermission()
            },
            continueNoCallback = {
                navigateToLogin()
            }
        )
    } else {
        navigateToLogin()
    }
}

private fun navigateToLogin() {
    parentFragmentManager
        .beginTransaction()
        .replace(R.id.content, LoginFragment::class.java, null)
        .addToBackStack("Login")
        .commit()
}
```

---

### 16. `AuthenticationFragment.kt` — Add WebView auto-login injection
**Path:** `app/src/main/java/io/homeassistant/companion/android/onboarding/authentication/AuthenticationFragment.kt`

**Changes:**
- Add import: `import io.homeassistant.companion.android.onboarding.login.HassioUserSession`
- Add import: `import org.json.JSONObject`
- In the `WebViewClient.onPageFinished()` callback, add call: `injectAutoLoginScript(view)`
- Add the entire `injectAutoLoginScript(webView: WebView?)` function (see below)

**The injection function to add:**
```kotlin
private fun injectAutoLoginScript(webView: WebView?) {
    val username = HassioUserSession.webviewUsername ?: ""
    val password = HassioUserSession.webviewPassword ?: ""

    val escapedUsername = JSONObject.quote(username)
    val escapedPassword = JSONObject.quote(password)

    val jsScript = """
        (function() {
            let found = false;

            function createLoadingOverlay() {
                var overlay = document.createElement('div');
                overlay.id = 'loadingOverlay';
                overlay.style.position = 'fixed';
                overlay.style.top = '0';
                overlay.style.left = '0';
                overlay.style.width = '100%';
                overlay.style.height = '100%';
                overlay.style.backgroundColor = 'white';
                overlay.style.zIndex = '9999';
                overlay.style.display = 'flex';
                overlay.style.flexDirection = 'column';
                overlay.style.justifyContent = 'center';
                overlay.style.alignItems = 'center';
                overlay.innerHTML = `
                    <div class="spinner"></div>
                    <p style="font-size: 24px; font-family: Arial, sans-serif; color: black;">Signing in...</p>
                `;

                var style = document.createElement('style');
                style.innerHTML = `
                    .spinner {
                        border: 8px solid #f3f3f3;
                        border-top: 8px solid #3498db;
                        border-radius: 50%;
                        width: 50px;
                        height: 50px;
                        animation: spin 1s linear infinite;
                    }

                    @keyframes spin {
                        0% { transform: rotate(0deg); }
                        100% { transform: rotate(360deg); }
                    }
                `;
                document.head.appendChild(style);
                document.body.appendChild(overlay);
            }

            function showErrorInOverlay() {
                const overlay = document.getElementById('loadingOverlay');
                if (overlay) {
                    overlay.innerHTML = `
                        <p style="font-size: 24px; font-family: Arial, sans-serif; color: red;text-align:center">⚠️<br/><br/>Invalid username or password.</p>
                    `;
                }
            }

            function removeLoadingOverlay() {
                var overlay = document.getElementById('loadingOverlay');
                if (overlay) {
                    overlay.remove();
                }
            }

            function checkForErrorAlert() {
                const interval = setInterval(() => {
                    const isErrorAlertPresent = document.querySelector('ha-alert[alert-type="error"]') !== null;
                    if (isErrorAlertPresent) {
                        clearInterval(interval);
                        showErrorInOverlay();
                    }
                }, 1000);
            }

            function checkInputElement() {
                if (found) { return; }

                var inputElement = document.querySelector('input[name="username"]');
                if (inputElement) {
                    found = true;
                    console.log("Input element found");
                    doSignIn();
                } else {
                    console.log("Input element not found");
                }
            }

            function doSignIn() {
                var usernameInput = document.querySelector('input[name="username"]');
                var passwordInput = document.querySelector('input[name="password"]');
                var loginButton = document.querySelector('mwc-button');

                usernameInput.value = ${'$'}escapedUsername;
                var usernameEvent = new Event('input', { bubbles: true });
                usernameInput.dispatchEvent(usernameEvent);

                passwordInput.value = ${'$'}escapedPassword;
                var passwordEvent = new Event('input', { bubbles: true });
                passwordInput.dispatchEvent(passwordEvent);

                setTimeout(function() {
                    var clickEvent = new MouseEvent('click', {
                        view: window,
                        bubbles: true,
                        cancelable: true
                    });
                    loginButton.dispatchEvent(clickEvent);

                    checkForErrorAlert();
                }, 100);
            }

            setInterval(checkInputElement, 1000);
            createLoadingOverlay();
        })();
    """.trimIndent()

    Log.d(TAG, "Executing JavaScript: ${'$'}jsScript")

    webView?.evaluateJavascript(jsScript) { result ->
        Log.d(TAG, "JavaScript execution result: ${'$'}result")
    }
}
```

> **IMPORTANT:** In the actual Kotlin file, the `$escapedUsername` and `$escapedPassword` inside the JS string template use Kotlin string interpolation directly (no `${'$'}`). The escaping above is just for markdown rendering. Use `$escapedUsername` and `$escapedPassword` directly.

---

### 17. `ManualSetupView.kt` — Auto-fill external URL and auto-connect
**Path:** `app/src/main/java/io/homeassistant/companion/android/onboarding/manual/ManualSetupView.kt`

**Changes:**
- Add import: `import io.homeassistant.companion.android.onboarding.login.HassioUserSession`
- Add import: `import androidx.compose.runtime.LaunchedEffect`
- Add import: `import androidx.compose.runtime.saveable.rememberSaveable`
- Add import: `import kotlinx.coroutines.launch`
- Add a `LaunchedEffect(Unit)` block at the top of the composable that:
  1. Reads `HassioUserSession.externalUrl`
  2. If not empty, sets `manualUrl.value = externalUrl`
  3. Calls `connectedClicked()` automatically
  4. Shows a loading overlay while connecting

```kotlin
val isLoading = rememberSaveable { mutableStateOf(true) }

LaunchedEffect(Unit) {
    val externalUrl = HassioUserSession.externalUrl ?: ""
    if (externalUrl.isNotEmpty() && isLoading.value == true) {
        manualUrl.value = externalUrl
        connectedClicked()
        launch {
            kotlinx.coroutines.delay(1000)
            isLoading.value = false
        }
    }
}
```

And at the bottom of the composable, add a loading overlay:
```kotlin
if (isLoading.value) {
    Surface(
        color = MaterialTheme.colors.background.copy(alpha = 1f),
        modifier = Modifier.fillMaxSize()
    ) { }
}
```

---

### 18. `MobileAppIntegrationFragment.kt` — Auto WiFi setup
**Path:** `app/src/main/java/io/homeassistant/companion/android/onboarding/integration/MobileAppIntegrationFragment.kt`

**Changes:**
- Add import: `import io.homeassistant.companion.android.onboarding.login.HassioUserSession`
- Add import: `import io.homeassistant.companion.android.util.MSHAutoWifiManager`
- Add field: `@Inject lateinit var autoWifiManager: MSHAutoWifiManager`

---

### 19. `WebViewActivity.kt` — Trigger auto WiFi on load
**Path:** `app/src/main/java/io/homeassistant/companion/android/webview/WebViewActivity.kt`

**Changes:**
- Add import: `import io.homeassistant.companion.android.onboarding.login.HassioUserSession`
- Add import: `import io.homeassistant.companion.android.util.MSHAutoWifiManager`
- Add field: `@Inject lateinit var autoWifiManager: MSHAutoWifiManager`
- After WebView setup is complete, add:
```kotlin
Log.d(TAG, "HassioUserSession.internalUrl ${HassioUserSession.internalUrl}");
if(HassioUserSession.internalUrl!=null){
    autoWifiManager.autoAddCurrentWifiToAllServers()
}
```

---

### 20. `app/build.gradle.kts` — Build config and dependencies

**Add BuildConfig fields** (inside `defaultConfig` or `buildTypes`):
```kotlin
buildConfigField("String", "MSH_AES_KEY", "\"${System.getenv("MSH_AES_KEY") ?: ""}\"")
buildConfigField("String", "MSH_AES_IV", "\"${System.getenv("MSH_AES_IV") ?: ""}\"")
```

**Add dependencies:**
```kotlin
implementation(libs.firebase.auth.ktx)
implementation(libs.firebase.firestore.ktx)
```

Also ensure Firebase BOM and messaging are in the full variant:
```kotlin
"fullImplementation"(platform(libs.firebase.bom))
"fullImplementation"(libs.firebase.messaging)
```

---

## STRING RESOURCES NEEDED

In `common/src/main/res/values/strings.xml`, ensure these strings exist:
- `login` → "Log in"
- `location_perm_info_title` → "Location access"
- `location_perm_info_message` → (Location permission explanation text)
- `confirm_positive` → (e.g., "Allow" or "Yes")
- `confirm_negative` → (e.g., "Deny" or "No")

---

## ENVIRONMENT VARIABLES

Set these env vars for the AES decryption to work:
- `MSH_AES_KEY` — 32-character AES-256 key
- `MSH_AES_IV` — 16-character AES CBC IV

---

## FIREBASE FIRESTORE STRUCTURE

```
users/{userId}
  ├── email: String
  ├── subscription.expiresAt: Timestamp
  └── serverPasswords/ (subcollection)
        └── {serverId}
              └── encryptedPass: String

servers/{serverId}
  ├── externalUrl: String
  ├── internalUrl: String
  └── homeName: String
```

---

## SUMMARY OF ALL FILES

| # | Action | File Path |
|---|--------|-----------|
| 1 | CREATE | `app/.../onboarding/login/LoginFragment.kt` |
| 2 | CREATE | `app/.../onboarding/login/LoginView.kt` |
| 3 | CREATE | `app/.../onboarding/login/HassioUserSession.kt` |
| 4 | CREATE | `app/.../onboarding/login/CryptoUtil.kt` |
| 5 | CREATE | `app/.../onboarding/login/ServerListChooser.kt` |
| 6 | CREATE | `app/.../onboarding/login/ServerTimeFetchService.kt` |
| 7 | CREATE | `app/.../util/MSHAutoWifiManager.kt` |
| 8 | CREATE | `app/.../util/VersionChecker.kt` |
| 9 | CREATE | `app/.../util/UpdateDialogUtil.kt` |
| 10 | CREATE | `common/.../util/LocationPermissionInfoHandler.kt` |
| 11 | CREATE | `app/src/main/res/drawable/login_bg_shape.xml` |
| 12 | CREATE | `app/src/main/res/drawable/my_smart_home_icon.xml` |
| 13 | CREATE | `app/src/main/res/layout/msh_server_selection_dialog.xml` |
| 14 | CREATE | `app/src/main/res/layout/msh_server_list_item.xml` |
| 15 | MODIFY | `app/.../onboarding/welcome/WelcomeFragment.kt` |
| 16 | MODIFY | `app/.../onboarding/authentication/AuthenticationFragment.kt` |
| 17 | MODIFY | `app/.../onboarding/manual/ManualSetupView.kt` |
| 18 | MODIFY | `app/.../onboarding/integration/MobileAppIntegrationFragment.kt` |
| 19 | MODIFY | `app/.../webview/WebViewActivity.kt` |
| 20 | MODIFY | `app/build.gradle.kts` |
