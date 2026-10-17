package com.qin.f21.toolbox.ui

import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.core.hardware.TorchController
import com.qin.f21.toolbox.core.hardware.TorchController.TorchState

/**
 * Flashlight. The Qin F21 Pro has a single rear camera with an LED flash which the
 * platform exposes as a torch; if the flash unit is missing the screen explains it
 * instead of pretending to work.
 */
class FlashlightScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Flashlight"

    private val controller = TorchController(host.activity)
    private lateinit var stateLabel: TextView
    private lateinit var toggleButton: TextView
    private lateinit var iconView: ImageView

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val root = Widgets.column(context)
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        val card = Widgets.column(context)
        card.gravity = Gravity.CENTER
        card.setPadding(
            Widgets.dp(context, 16), Widgets.dp(context, 20),
            Widgets.dp(context, 16), Widgets.dp(context, 20)
        )
        card.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline)
        )

        iconView = ImageView(context)
        iconView.setImageResource(R.drawable.ic_flashlight)
        iconView.setColorFilter(Widgets.color(context, R.color.text_muted))
        card.addView(iconView, LinearLayout.LayoutParams(Widgets.dp(context, 64), Widgets.dp(context, 64)))

        stateLabel = Widgets.text(context, "", R.style.Text_Title)
        stateLabel.gravity = Gravity.CENTER
        stateLabel.setPadding(0, Widgets.dp(context, 12), 0, 0)
        card.addView(stateLabel)

        val detail = Widgets.caption(
            context,
            if (controller.isAvailable) {
                context.getString(R.string.flashlight_detail)
            } else {
                context.getString(R.string.flashlight_unavailable)
            }
        )
        detail.gravity = Gravity.CENTER
        detail.setPadding(0, Widgets.dp(context, 6), 0, 0)
        card.addView(detail)

        toggleButton = Widgets.button(context, context.getString(R.string.action_turn_on))
        toggleButton.isEnabled = controller.isAvailable
        toggleButton.setOnClickListener { toggle() }
        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        buttonParams.topMargin = Widgets.dp(context, 16)
        card.addView(toggleButton, buttonParams)

        root.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 12), Widgets.dp(context, 12), Widgets.dp(context, 12), 0) })

        update()
        return root
    }

    private fun toggle() {
        when (controller.setEnabled(!controller.isOn())) {
            TorchState.ON -> {
                host.showMessage(host.activity.getString(R.string.flashlight_on))
            }
            TorchState.OFF -> {
                host.showMessage(host.activity.getString(R.string.flashlight_off))
            }
            TorchState.UNAVAILABLE -> {
                host.showMessage(host.activity.getString(R.string.flashlight_unavailable))
            }
        }
        update()
    }

    private fun update() {
        if (!controller.isAvailable) {
            stateLabel.text = host.activity.getString(R.string.flashlight_unavailable_title)
            stateLabel.setTextColor(Widgets.color(host.activity, R.color.text_muted))
            iconView.setColorFilter(Widgets.color(host.activity, R.color.text_muted))
            toggleButton.isEnabled = false
            return
        }
        val on = controller.isOn()
        stateLabel.text = host.activity.getString(if (on) R.string.flashlight_on else R.string.flashlight_off)
        stateLabel.setTextColor(
            Widgets.color(host.activity, if (on) R.color.accent else R.color.text_primary)
        )
        iconView.setColorFilter(
            Widgets.color(host.activity, if (on) R.color.accent else R.color.text_muted)
        )
        toggleButton.text = host.activity.getString(if (on) R.string.action_turn_off else R.string.action_turn_on)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_CAMERA || keyCode == KeyEvent.KEYCODE_SPACE) {
            toggle()
            return true
        }
        return false
    }

    override fun onHidden() {
        controller.setEnabled(false)
    }
}
