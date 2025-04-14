package com.example.magento2productuplouder

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var skuField: EditText
    private lateinit var nameField: EditText
    private lateinit var priceField: EditText
    private lateinit var optPriceField: EditText
    private lateinit var qtyField: EditText
    private lateinit var label: TextView
    private lateinit var imagePreview: ImageView
    private lateinit var selectImageButton: Button
    private lateinit var takePhotoButton: Button
    private lateinit var createButton: Button

    private var selectedBitmap: Bitmap? = null
    private val client = OkHttpClient()
    private val token = "Bearer yk1e8njsduo00pmg0e4dlc5h8p51f1ch"

    private val REQUEST_IMAGE_PICK = 101
    private val REQUEST_IMAGE_CAPTURE = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        skuField = findViewById(R.id.product_sku)
        nameField = findViewById(R.id.product_name)
        priceField = findViewById(R.id.product_price)
        optPriceField = findViewById(R.id.opt_product_price)
        qtyField = findViewById(R.id.product_qty)
        label = findViewById(R.id.textView)
        imagePreview = findViewById(R.id.image_preview)
        selectImageButton = findViewById(R.id.select_image_button)
        takePhotoButton = findViewById(R.id.take_photo_button)
        createButton = findViewById(R.id.button)

        createButton.setOnClickListener {
            val sku = skuField.text.toString()
            val name = nameField.text.toString().ifBlank { sku }
            val price = priceField.text.toString().toDoubleOrNull() ?: 0.0
            val optPrice = optPriceField.text.toString()
            val qty = qtyField.text.toString().toIntOrNull() ?: 100

            val json = """
                {
                  "product": {
                    "sku": "$sku",
                    "name": "$name",
                    "price": $price,
                    "status": 1,
                    "type_id": "simple",
                    "attribute_set_id": 13,
                    "visibility": 4,
                    "extension_attributes": {
                      "stock_item": {
                        "qty": $qty,
                        "is_in_stock": true
                      },
                      "category_links": [
                        {
                          "position": 0,
                          "category_id": "21"
                        }
                      ]
                    },
                    "custom_attributes": [
                      {
                        "attribute_code": "description",
                        "value": "This is a test product."
                      },
                      {
                        "attribute_code": "short_description",
                        "value": "Short description of the test product."
                      },
                      {
                        "attribute_code": "optovaja_cena",
                        "value": "$optPrice"
                      }
                    ]
                  }
                }
            """.trimIndent()

            val body = json.toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("https://megapoint.com.ua/rest/default/V1/products")
                .addHeader("Authorization", token)
                .post(body)
                .build()

            label.text = "Создание продукта..."

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        label.text = "Ошибка: ${e.message}"
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        if (response.isSuccessful) {
                            label.text = "Продукт успешно создан: $name"
                            label.postDelayed({ label.text = "" }, 3000)

                            skuField.text.clear()
                            nameField.text.clear()
                            priceField.text.clear()
                            optPriceField.text.clear()
                            qtyField.text.clear()

                            selectedBitmap?.let { bitmap ->
                                val stream = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 70, stream)
                                val imageBytes = stream.toByteArray()
                                uploadImage(imageBytes, sku)
                            }
                        } else {
                            label.text = "Ошибка: ${response.code} - ${response.body?.string()}"
                        }
                    }
                }
            })
        }

        selectImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, REQUEST_IMAGE_PICK)
        }

        takePhotoButton.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK && data != null) {
            val bitmap: Bitmap? = when (requestCode) {
                REQUEST_IMAGE_PICK -> {
                    val uri: Uri? = data.data
                    uri?.let {
                        if (Build.VERSION.SDK_INT < 28) {
                            MediaStore.Images.Media.getBitmap(contentResolver, it)
                        } else {
                            val source = ImageDecoder.createSource(contentResolver, it)
                            ImageDecoder.decodeBitmap(source)
                        }
                    }
                }
                REQUEST_IMAGE_CAPTURE -> {
                    data.extras?.get("data") as? Bitmap
                }
                else -> null
            }

            bitmap?.let {
                selectedBitmap = it
                imagePreview.setImageBitmap(it)
            }
        }
    }

    private fun uploadImage(imageBytes: ByteArray, sku: String) {
        val base64Image = "data:image/webp;base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        val jsonObject = JSONObject().apply {
            put("entry", JSONObject().apply {
                put("media_type", "image")
                put("label", "Test Image")
                put("position", 1)
                put("disabled", false)
                put("types", JSONArray().apply {
                    put("image")
                    put("small_image")
                    put("thumbnail")
                })
                put("content", JSONObject().apply {
                    put("base64_encoded_data", base64Image)
                    put("type", "image/webp")
                    put("name", "$sku.webp")
                })
            })
        }

        val requestBody = jsonObject.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://megapoint.com.ua/rest/V1/products/$sku/media")
            .post(requestBody)
            .addHeader("Authorization", token)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("UploadImage", "Ошибка загрузки изображения", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("UploadImage", "Изображение успешно загружено")
                } else {
                    Log.e("UploadImage", "Ошибка ответа: ${response.code}")
                }
            }
        })
    }
}
