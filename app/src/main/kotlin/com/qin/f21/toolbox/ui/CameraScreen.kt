package com.qin.f21.toolbox.ui

import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.qin.f21.toolbox.CameraActivity
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.core.hardware.DeviceInfo
import java.io.File

/**
 * Photo tool. Photos are taken with the rear camera and stored in the app specific
 * external storage, they can be shared with other apps.
 */
class CameraScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Camera"

    private lateinit var preview: ImageView
    private lateinit var infoLabel: TextView
    private lateinit var actionRow: LinearLayout
    private var lastFile: File? = null

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val root = Widgets.column(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        val scroll = ScrollView(context).forKeypad()
        val column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 12),
            Widgets.dp(context, 12), Widgets.dp(context, 24)
        )

        val card = Widgets.column(context)
        card.gravity = Gravity.CENTER
        card.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 12),
            Widgets.dp(context, 12), Widgets.dp(context, 12)
        )
        card.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline), 16
        )
        preview = ImageView(context)
        preview.setImageResource(R.drawable.ic_camera)
        preview.setColorFilter(Widgets.color(context, R.color.text_muted))
        preview.adjustViewBounds = true
        card.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Widgets.dp(context, 180)
        ))
        infoLabel = Widgets.caption(context, context.getString(R.string.camera_no_photo))
        infoLabel.gravity = Gravity.CENTER
        card.addView(infoLabel)
        column.addView(card)

        actionRow = Widgets.row(context)
        val take = Widgets.button(context, context.getString(R.string.camera_take_photo))
        take.setOnClickListener { takePhoto() }
        actionRow.addView(take, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(actionRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Widgets.dp(context, 8) })

        column.addView(Widgets.caption(context, context.getString(R.string.camera_storage_note)))

        scroll.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        if (!Toolbox.capabilities.hasCamera) {
            column.addView(unavailableCard())
        }
        return root
    }

    private fun unavailableCard(): View {
        val context = host.activity
        val card = Widgets.column(context)
        card.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 14),
            Widgets.dp(context, 12), Widgets.dp(context, 14)
        )
        card.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface_alt), Widgets.color(context, R.color.outline_strong)
        )
        card.addView(Widgets.text(context, context.getString(R.string.camera_unavailable_title), R.style.Text_Subtitle))
        card.addView(Widgets.caption(context, context.getString(R.string.scanner_unavailable_message)))
        return card
    }

    private fun takePhoto() {
        if (!Toolbox.capabilities.hasCamera) {
            host.showMessage(host.activity.getString(R.string.scanner_unavailable_message))
            return
        }
        val intent = Intent(host.activity, CameraActivity::class.java)
        intent.putExtra(CameraActivity.EXTRA_MODE, CameraActivity.MODE_PHOTO)
        host.activity.startActivityForResult(intent, REQUEST_PHOTO)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_PHOTO) return false
        if (resultCode != android.app.Activity.RESULT_OK) return true
        val path = data?.getStringExtra(CameraActivity.RESULT_PATH) ?: return true
        val file = File(path)
        lastFile = file
        infoLabel.text = host.activity.getString(
            R.string.camera_photo_info, file.name, DeviceInfo.formatBytes(file.length())
        )
        preview.setImageURI(Uri.fromFile(file))
        rebuildActions()
        return true
    }

    private fun rebuildActions() {
        actionRow.removeAllViews()
        if (lastFile == null) return
        val share = Widgets.button(host.activity, host.activity.getString(R.string.action_share), secondary = true)
        share.setOnClickListener {
            try {
                val uri = Uri.parse("content://${host.activity.packageName}.fileprovider/${lastFile?.name}")
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "image/jpeg"
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                host.activity.startActivity(
                    Intent.createChooser(intent, host.activity.getString(R.string.action_share))
                )
            } catch (e: Exception) {
                host.showMessage(host.activity.getString(R.string.error_generic))
            }
        }
        actionRow.addView(share, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actionRow.addView(Widgets.spacer(host.activity, 6))
        val take = Widgets.button(host.activity, host.activity.getString(R.string.camera_take_photo), secondary = true)
        take.setOnClickListener { takePhoto() }
        actionRow.addView(take, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    companion object {
        const val REQUEST_PHOTO = 3002
    }
}
