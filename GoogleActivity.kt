package com.example.demo_application

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class GoogleAiActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: CardView
    private lateinit var idIVMic: CardView
    private lateinit var adapter: ChatAdapter
    private val requestCodeSpeechInput = 1
    private lateinit var chaList: MutableList<ChatModel>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_google_ai)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        recyclerView = findViewById(R.id.rvChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        idIVMic = findViewById(R.id.idIVMic)

        chaList = mutableListOf()
        adapter = ChatAdapter(chaList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnSend.setOnClickListener {
            val message = etMessage.text.toString().trim()
            if (message.isEmpty()) {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else {
                addMessage(ChatModel(message, true))
                etMessage.text.clear()
                hideKeyboard()
                apiCall(message)
            }
        }

        idIVMic.setOnClickListener {
            hideKeyboard()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask Your AI")
            }

            try {
                startActivityForResult(intent, requestCodeSpeechInput)
            } catch (e: Exception) {
                Toast.makeText(this, "Speech recognition error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == requestCodeSpeechInput && resultCode == RESULT_OK && data != null) {
            val res: ArrayList<String>? = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!res.isNullOrEmpty()) {
                val spokenText = res[0]
                addMessage(ChatModel(spokenText, true))
                etMessage.text.clear()
                hideKeyboard()
                apiCall(spokenText)
            }
        }
    }

    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.let { view ->
            inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun addMessage(message: ChatModel) {
        chaList.add(message)
        adapter.notifyItemInserted(chaList.size - 1)
        recyclerView.scrollToPosition(chaList.size - 1)
    }

    private fun apiCall(prompt: String) {
        val url = "https://api.groq.com/openai/v1/chat/completions"
        val groqApiKey = "YOUR_GROQ_API_KEY"

        val jsonBody = JSONObject().apply {
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("model", "llama3-8b-8192")
            put("temperature", 0.6)
            put("max_completion_tokens", 2048)
            put("top_p", 0.95)
            put("stream", false)
        }

        val request = object : JsonObjectRequest(
            Method.POST,
            url,
            jsonBody,
            Response.Listener { response ->
                try {
                    val answer = response
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    addMessage(ChatModel(answer, false))
                } catch (e: Exception) {
                    addMessage(ChatModel("Error parsing response: ${e.message}", false))
                }
            },
            Response.ErrorListener { error ->
                val statusCode = error.networkResponse?.statusCode
                val rawBody = error.networkResponse?.data?.let { String(it, Charsets.UTF_8) }

                val detailedError = when {
                    rawBody != null -> {
                        try {
                            val jsonError = JSONObject(rawBody)
                            jsonError.optJSONObject("error")?.optString("message") ?: rawBody
                        } catch (_: Exception) {
                            rawBody
                        }
                    }
                    error.message != null -> error.message
                    else -> "Network Error or Invalid API Key (Status Code: $statusCode)"
                }

                addMessage(ChatModel("API Error ($statusCode): $detailedError", false))
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return hashMapOf(
                    "Content-Type" to "application/json",
                    "Authorization" to "Bearer $groqApiKey"
                )
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            60000,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        Volley.newRequestQueue(this).add(request)
    }
}
