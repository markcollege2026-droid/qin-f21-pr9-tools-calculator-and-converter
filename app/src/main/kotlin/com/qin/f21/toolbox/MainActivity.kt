package com.qin.f21.toolbox

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.qin.f21.toolbox.core.hardware.HardwareCapabilities
import com.qin.f21.toolbox.ui.HomeScreen
import com.qin.f21.toolbox.ui.Screen
import com.qin.f21.toolbox.ui.ScreenHost

/**
 * The single activity of the toolbox.
 *
 * It hosts a stack of [Screen]s, draws the header and routes the hardware keys
 * (D-pad, Enter, Back and the numeric keypad) to the visible screen.
 */
class MainActivity : Activity(), ScreenHost {

    private lateinit var content: FrameLayout
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var backView: ImageView
    private lateinit var headerIcon: ImageView
    private val stack = ArrayList<Screen>()

    override val activity: Activity get() = this
    override val capabilities: HardwareCapabilities get() = Toolbox.capabilities

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        content = findViewById(R.id.content)
        titleView = findViewById(R.id.header_title)
        subtitleView = findViewById(R.id.header_subtitle)
        backView = findViewById(R.id.btn_back)
        headerIcon = findViewById(R.id.header_icon)
        backView.setOnClickListener { pop() }
        if (savedInstanceState == null) {
            push(HomeScreen(this))
        }
    }

    // ---- ScreenHost -----------------------------------------------------------

    override fun push(screen: Screen) {
        stack.add(screen)
        show(screen)
    }

    override fun pop() {
        if (stack.isEmpty()) return
        val screen = stack.removeAt(stack.size - 1)
        screen.onHidden()
        if (stack.isEmpty()) {
            finish()
            return
        }
        show(stack[stack.size - 1])
    }

    override fun replace(screen: Screen) {
        if (stack.isNotEmpty()) {
            val previous = stack.removeAt(stack.size - 1)
            previous.onHidden()
        }
        stack.add(screen)
        show(screen)
    }

    override fun setTitle(title: String, subtitle: String?) {
        titleView.text = title
        if (subtitle.isNullOrEmpty()) {
            subtitleView.visibility = View.GONE
        } else {
            subtitleView.visibility = View.VISIBLE
            subtitleView.text = subtitle
        }
    }

    override fun showMessage(message: String) {
        Toolbox.toast(this, message)
    }

    override fun askPermissions(permissions: Array<String>, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions, requestCode)
        }
    }

    override fun onScreenResumed(screen: Screen) {
        screen.onShown()
    }

    private fun show(screen: Screen) {
        content.removeAllViews()
        val view = screen.createView(content)
        content.addView(view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        setTitle(screen.title, screen.subtitle)
        backView.visibility = if (stack.size > 1) View.VISIBLE else View.INVISIBLE
        val icon = screen.icon
        if (icon != null) {
            headerIcon.visibility = View.VISIBLE
            headerIcon.setImageResource(iconRes(icon))
            headerIcon.setColorFilter(getColor(R.color.text_muted))
        } else {
            headerIcon.visibility = View.GONE
        }
        screen.onShown()
        focusFirstStop()
    }

    /**
     * Gives the freshly shown screen a keyboard focus stop.
     *
     * A D-pad needs one view with the focus or the arrow keys do nothing. The screen roots
     * are containers, so requesting the focus in the DOWN direction walks into the first
     * focusable child. An existing focus is never taken away, which matters when a screen
     * is only resumed rather than rebuilt.
     */
    private fun focusFirstStop() {
        content.post {
            if (content.findFocus() == null) {
                content.requestFocus(View.FOCUS_DOWN)
            }
        }
    }

    private fun iconRes(name: String): Int {
        val id = resources.getIdentifier(name, "drawable", packageName)
        return if (id != 0) id else R.drawable.ic_apps
    }

    // ---- key handling ---------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val screen = stack.lastOrNull()
        if (screen != null) {
            if (screen.onKeyDown(keyCode, event)) return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (screen?.onBackPressed() == true) return true
            pop()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val screen = stack.lastOrNull()
        if (screen != null) {
            if (screen.onKeyLongPress(keyCode, event)) return true
        }
        return super.onKeyUp(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        stack.lastOrNull()?.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Toolbox.refreshCapabilities()
        val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        val screen = stack.lastOrNull() ?: return
        // Screens build their permission state into the view tree, so a granted permission
        // has to rebuild the screen; a denial only refreshes the current one.
        if (granted) show(screen) else screen.onShown()
    }

    override fun onResume() {
        super.onResume()
        stack.lastOrNull()?.onShown()
    }
}
