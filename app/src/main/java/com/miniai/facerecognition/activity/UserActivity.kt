package com.miniai.facerecognition.activity

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.miniai.facerecognition.ImageRotator
import com.miniai.facerecognition.R
import com.miniai.facerecognition.UserDB
import com.miniai.facerecognition.UserInfo
import com.miniai.facerecognition.UsersAdapter
import com.miniai.facerecognition.manager.FaceManager

class UserActivity : AppCompatActivity() {

    companion object {
        private val ADD_USER_REQUEST_CODE = 1
    }

    private lateinit var adapter: UsersAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)

        adapter = UsersAdapter(this, UserDB.userInfos)
        val listView: ListView = findViewById<View>(R.id.userList) as ListView
        listView.setAdapter(adapter)

        listView.setOnItemClickListener { _, _, i, _ ->
            val alertDialog: AlertDialog.Builder = AlertDialog.Builder(this@UserActivity)
            alertDialog.setTitle(getString(R.string.delete_user))

            val inflater = layoutInflater
            val dialogView = inflater.inflate(R.layout.activity_dialog, null)
            alertDialog.setView(dialogView)

            // Access the views in the custom dialog layout
            val imageView = dialogView.findViewById<ImageView>(R.id.dialogFaceView)
            val textView = dialogView.findViewById<TextView>(R.id.dialogTextView)

            // Customize the views
            // Get the data item for this position
            imageView.setImageBitmap(UserDB.userInfos[i].faceImage)
            textView.text = UserDB.userInfos[i].userName

            // Set positive button and its click listener
            alertDialog.setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                // Handle positive button click, if needed
                FaceManager.getInstance().deleteUser(UserDB.userInfos[i].userName)
                UserDB.userInfos.removeAt(i)

                adapter.notifyDataSetChanged()
                dialog.dismiss()
                FaceManager.getInstance().reset()
            }

            // Set positive button and its click listener
            alertDialog.setNegativeButton(getString(R.string.delete_all)) { dialog, _ ->
                // Handle positive button click, if needed
                FaceManager.getInstance().deleteAllUser()
                UserDB.userInfos.clear()

                adapter.notifyDataSetChanged()
                dialog.dismiss()
                FaceManager.getInstance().reset()
            }

            // Create and show the AlertDialo
            val alert: AlertDialog = alertDialog.create()
            alert.show()
        }

        findViewById<FloatingActionButton>(R.id.buttonAdd).setOnClickListener {
            val intent = Intent()
            intent.setType("image/*")
            intent.setAction(Intent.ACTION_PICK)
            startActivityForResult(
                Intent.createChooser(intent, getString(R.string.select_picture)),
                ADD_USER_REQUEST_CODE
            )
        }

    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADD_USER_REQUEST_CODE && resultCode == RESULT_OK) {
            try {
                val bitmap: Bitmap = ImageRotator.getCorrectlyOrientedImage(this, data?.data!!)
                val faceBox = FaceManager.getInstance().getFaceBox(bitmap)
                when (faceBox.first) {
                    UserInfo.NO_FACE -> {
                        Toast.makeText(
                            this,
                            getString(R.string.no_face_detected),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    UserInfo.FAKE -> {
                        Toast.makeText(
                            this,
                            getString(R.string.liveness_check_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    UserInfo.MULTI_FACE -> {
                        Toast.makeText(
                            this,
                            getString(R.string.multiple_face_detected),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    UserInfo.UNKNOWN -> {
                        Toast.makeText(
                            this,
                            "Unknown Error",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    UserInfo.SUCCESS -> {
                        val inputView = LayoutInflater.from(this)
                            .inflate(R.layout.dialog_input_view, null, false)
                        val editText = inputView.findViewById<EditText>(R.id.et_user_name)
                        val ivHead = inputView.findViewById<ImageView>(R.id.iv_head)

                        val faceImage =
                            FaceManager.getInstance().cropFaceBitmap(bitmap, faceBox.second)
                        ivHead.setImageBitmap(faceImage)
                        editText.hint = "User Name"
                        val confirmUpdateDialog: AlertDialog = AlertDialog.Builder(this)
                            .setView(inputView)
                            .setPositiveButton(
                                "OK", null
                            )
                            .setNegativeButton(
                                "Cancel", null
                            )
                            .create()
                        confirmUpdateDialog.show()
                        confirmUpdateDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                            .setOnClickListener {
                                val s = editText.text.toString()
                                if (TextUtils.isEmpty(s)) {
                                    editText.error =
                                        application.getString(R.string.name_should_not_be_empty)
                                    return@setOnClickListener
                                }

                                var exists = false
                                for (user in UserDB.userInfos) {
                                    if (TextUtils.equals(user.userName, s)) {
                                        exists = true
                                        break
                                    }
                                }

                                if (exists) {
                                    editText.error = "Name already exists!"
                                    return@setOnClickListener
                                }

                                if (!FaceManager.getInstance()
                                        .insertUser(s, faceImage, bitmap, faceBox.second)
                                ) {
                                    editText.error = "Face already exists!"
                                    return@setOnClickListener
                                }

                                confirmUpdateDialog.cancel()

                                adapter.notifyDataSetChanged()
                                Toast.makeText(
                                    this,
                                    getString(R.string.register_successed),
                                    Toast.LENGTH_SHORT
                                ).show()
                                FaceManager.getInstance().reset()
                            }
                    }
                }
            } catch (e: Exception) {
                //handle exception
                e.printStackTrace()
            }
        }
    }
}