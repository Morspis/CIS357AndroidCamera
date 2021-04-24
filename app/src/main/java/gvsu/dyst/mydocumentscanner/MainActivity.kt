package gvsu.dyst.mydocumentscanner

// Android Camera Document Scanner App
//
// CIS 357 - Mobile Application Development
// Tyler Dys & Ethan Walser
//
// Uses AndroidDocumentScanner library from https://github.com/mayuce/AndroidDocumentScanner
// Uses OpenCV library from https://opencv.org/releases/

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kotlinpermissions.KotlinPermissions
import com.labters.documentscanner.ImageCropActivity
import com.labters.documentscanner.helpers.ScannerConstants
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    lateinit var imageSelectBtn: Button
    lateinit var saveBtn: Button
    lateinit var imageView: ImageView
    lateinit var currentPhotoPath: String
    lateinit var croppedImage: Bitmap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        imageSelectBtn = findViewById(R.id.imageSelectBtn)
        imageView = findViewById(R.id.imageView)
        saveBtn = findViewById(R.id.saveBtn)
        saveBtn.setOnClickListener {
            saveToGallery(MainActivity@ this, croppedImage)
        }
        requestPermissions()
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED) {
            KotlinPermissions.with(this)
                .permissions(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
                )
                .onAccepted { permissions ->
                    prepareView()
                }
                .onDenied { permissions ->
                    requestPermissions()
                }
                .ask()
        } else {
            prepareView()
        }
    }

    private fun prepareView() {
        imageSelectBtn.setOnClickListener(View.OnClickListener {
            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle("Photo Location")
            builder.setMessage("Where would you like to get your photo from?")
            builder.setPositiveButton("Gallery") { dialog, which ->
                dialog.dismiss()
                val intent = Intent(Intent.ACTION_PICK)
                intent.type = "image/*"
                startActivityForResult(intent, 1111)
            }
            builder.setNegativeButton("Camera") { dialog, which ->
                dialog.dismiss()
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (cameraIntent.resolveActivity(packageManager) != null) {
                    var imageFile: File? = null
                    try {
                        imageFile = createImageFile()
                    } catch (ex: IOException) {
                        Log.i("Main", "IOException")
                    }
                    if (imageFile != null) {
                        val builder = StrictMode.VmPolicy.Builder()
                        StrictMode.setVmPolicy(builder.build())
                        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(imageFile))
                        startActivityForResult(cameraIntent, 1231)
                    }
                }
            }
            builder.setNeutralButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            val dialog: AlertDialog = builder.create()
            dialog.show()
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1111 && resultCode == RESULT_OK && data != null) {
            var selectedImage = data.data
            var image: Bitmap? = null
            try {
                val inputStream = selectedImage?.let { contentResolver.openInputStream(it) }
                image = BitmapFactory.decodeStream(inputStream)
                ScannerConstants.selectedImageBitmap = image
                startActivityForResult(
                    Intent(MainActivity@ this, ImageCropActivity::class.java),
                    1234
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (requestCode == 1231 && resultCode == Activity.RESULT_OK) {
            ScannerConstants.selectedImageBitmap = MediaStore.Images.Media.getBitmap(
                this.contentResolver,
                Uri.parse(currentPhotoPath)
            )
            startActivityForResult(Intent(MainActivity@ this, ImageCropActivity::class.java), 1234)
        } else if (requestCode == 1234 && resultCode == Activity.RESULT_OK) {
            if (ScannerConstants.selectedImageBitmap != null) {
                imageView.setImageBitmap(ScannerConstants.selectedImageBitmap)
                imageView.visibility = View.VISIBLE
                imageSelectBtn.visibility = View.GONE
                saveBtn.visibility = View.VISIBLE

                croppedImage = ScannerConstants.selectedImageBitmap
            }
        }
    }

    private fun saveToGallery(context: Context, bitmap: Bitmap) {
        val filename = "${System.currentTimeMillis()}.png"
        val write: (OutputStream) -> Boolean = {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}")
        }

        context.contentResolver.let {
            it.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)?.let { uri ->
                it.openOutputStream(uri)?.let(write)
            }
        }
                
        saveBtn.visibility = View.GONE
        imageView.visibility = View.GONE
        imageSelectBtn.visibility = View.VISIBLE
        
        val toast = Toast.makeText(applicationContext, "Image Saved to Photos", Toast.LENGTH_LONG)
        toast.show()
    }

    @SuppressLint("SimpleDateFormat")
    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "JPEG_$timeStamp"
        val storageDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        )
        val image = File.createTempFile(
            imageFileName, // prefix
            ".jpg", // suffix
            storageDir      // directory
        )

        currentPhotoPath = "file:" + image.absolutePath
        return image
    }
}