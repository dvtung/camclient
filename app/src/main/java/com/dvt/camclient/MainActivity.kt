package com.dvt.camclient

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btn_camActivity:Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        btn_camActivity = findViewById<Button>(R.id.button)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        btn_camActivity.setOnClickListener{
            val intent = Intent(this, CamActivity::class.java)

            intent.putExtra("", "")

            startActivity(intent)

        }
        checkPermissions()
    }

    //region Request Permission
    private val permissionList = if (Build.VERSION.SDK_INT >= 33){
        arrayListOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        arrayListOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
    private val CAMERA_PERMISSION_REQUEST_CODE = 100

    private fun checkPermissions(){
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED -> {
                requestPermissions()
            }

            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) -> {
                showPermissionRationaleDialog()
            }
        }
    }

    private fun requestPermissions(){
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
    }
    private fun showPermissionRationaleDialog() {
        // Hiển thị dialog giải thích lý do cần quyền truy cập camera
        runOnUiThread {
            Toast.makeText(this, "Ứng dụng cần quyền truy cập camera để chụp ảnh", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)

        when (requestCode){
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    Log.d("CameraPermission", "Người dùng đã cấp quyền truy cập camera")
                else {
                    Log.d("CameraPermission", "Người dùng từ chối cấp quyền truy cập camera")
                    Toast.makeText(this, "Bạn đã từ chối quyền truy cập camera", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    //endregion

}