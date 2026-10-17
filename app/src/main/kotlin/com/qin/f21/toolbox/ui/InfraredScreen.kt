package com.qin.f21.toolbox.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.core.common.Json
import com.qin.f21.toolbox.core.hardware.InfraredController
import com.qin.f21.toolbox.util.Assets

/**
 * Infrared remote.
 *
 * The bundled code list is stored inside the APK. Whether the codes can actually be sent
 * depends on the hardware: the screen only offers the sender when the platform reports an
 * infrared emitter, and explains the situation otherwise.
 */
class InfraredScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Infrared remote"

    private val controller = InfraredController(host.activity)
    private lateinit var statusLabel: TextView
    private lateinit var listColumn: LinearLayout

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val root = Widgets.column(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        val scroll = ScrollView(context)
        val column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 6),
            Widgets.dp(context, 10), Widgets.dp(context, 12)
        )

        val statusCard = Widgets.column(context)
        statusCard.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 8),
            Widgets.dp(context, 12), Widgets.dp(context, 8)
        )
        statusCard.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline)
        )
        statusLabel = Widgets.text(context, "", R.style.Text_Body)
        statusCard.addView(statusLabel)
        val statusParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        statusParams.bottomMargin = Widgets.dp(context, 8)
        statusCard.layoutParams = statusParams
        column.addView(statusCard)

        if (!controller.isAvailable) {
            column.addView(unavailableCard())
        } else {
            column.addView(Widgets.sectionHeader(context, context.getString(R.string.ir_devices)))
            listColumn = Widgets.column(context)
            column.addView(listColumn)
            column.addView(Widgets.sectionHeader(context, context.getString(R.string.ir_custom)))
            column.addView(customSection())
            buildDeviceList()
        }

        scroll.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
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
        val title = Widgets.text(context, context.getString(R.string.ir_unavailable_title), R.style.Text_Subtitle)
        card.addView(title)
        val message = Widgets.text(
            context,
            context.getString(R.string.ir_unavailable_message, context.getString(R.string.app_name)),
            R.style.Text_Caption
        )
        message.setPadding(0, Widgets.dp(context, 6), 0, 0)
        card.addView(message)
        statusLabel.text = context.getString(R.string.ir_status_missing)
        return card
    }

    private fun customSection(): View {
        val context = host.activity
        val column = Widgets.column(context)
        val row = Widgets.row(context)
        val field = Widgets.editField(context, "0x20DF10EF")
        field.inputType = android.text.InputType.TYPE_CLASS_TEXT
        row.addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val send = Widgets.button(context, context.getString(R.string.ir_send))
        send.setOnClickListener {
            val pattern = parsePattern(field.text?.toString() ?: "")
            if (pattern == null) {
                host.showMessage(context.getString(R.string.ir_invalid_code))
            } else if (!controller.transmit(pattern)) {
                host.showMessage(context.getString(R.string.ir_send_failed))
            } else {
                host.showMessage(context.getString(R.string.ir_sent))
                context.getString(R.string.ir_sent)
            }
        }
        row.addView(send, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        column.addView(row)
        val hint = Widgets.caption(context, context.getString(R.string.ir_custom_hint))
        column.addView(hint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return column
    }

    private fun buildDeviceList() {
        val context = host.activity
        val json = Assets.readJson(context, "data/ir_codes.json")
        val devices = json?.arr("devices") ?: emptyList()
        for (device in devices) {
            val deviceName = device.string("name")
            val carrier = device.int("carrier", 38000)
            listColumn.addView(Widgets.title(context, deviceName))
            for (code in device.arr("codes")) {
                val name = code.string("name")
                val hex = code.string("code")
                val row = Widgets.row(context)
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(
                    Widgets.dp(context, 12), Widgets.dp(context, 8),
                    Widgets.dp(context, 12), Widgets.dp(context, 8)
                )
                row.background = Widgets.roundedBackground(
                    context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline)
                )
                row.isFocusable = true
                row.isClickable = true
                row.addView(Widgets.text(context, name, R.style.Text_Body), LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ))
                row.addView(Widgets.text(context, hex, R.style.Text_Caption))
                row.setOnClickListener { send(hex, carrier) }
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = Widgets.dp(context, 4)
                row.layoutParams = params
                listColumn.addView(row)
            }
        }
    }

    private fun send(hex: String, carrier: Int) {
        val pattern = parsePattern(hex)
        if (pattern == null) {
            host.showMessage(host.activity.getString(R.string.ir_invalid_code))
            return
        }
        val ok = controller.transmit(pattern, carrier)
        host.showMessage(
            host.activity.getString(if (ok) R.string.ir_sent else R.string.ir_send_failed)
        )
    }

    /** Parses "0x20DF10EF" or "20 DF 10 EF" into the alternating mark/space pattern. */
    private fun parsePattern(text: String): IntArray? {
        val cleaned = text.trim().replace("0x", "", ignoreCase = true).replace(" ", "")
        if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
        val bytes = ArrayList<Int>()
        for (i in cleaned.indices step 2) {
            val value = cleaned.substring(i, i + 2).toIntOrNull(16) ?: return null
            bytes.add(value)
        }
        val pattern = ArrayList<Int>()
        for (byte in bytes) {
            // each byte: low nibble = mark, high nibble = space (in units of 562 us)
            pattern.add((byte and 0x0F) * 562)
            pattern.add((byte shr 4) * 562)
        }
        val result = IntArray(pattern.size)
        for (i in pattern.indices) result[i] = pattern[i]
        return result
    }
}
