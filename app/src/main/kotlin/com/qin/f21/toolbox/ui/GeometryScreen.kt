package com.qin.f21.toolbox.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.core.calc.GeometryCalculator
import com.qin.f21.toolbox.core.common.Numbers

/**
 * Geometry helpers: circle, rectangle, triangle, sphere, cylinder and Pythagoras.
 */
class GeometryScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Geometry"

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val scroll = android.widget.ScrollView(context).forKeypad()
        val column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 6),
            Widgets.dp(context, 10), Widgets.dp(context, 12)
        )

        for (entry in entries()) {
            column.addView(entryRow(entry))
        }

        scroll.addView(column, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return scroll
    }

    private class Entry(
        val title: String,
        val description: String,
        val icon: Int,
        val screen: (ScreenHost) -> Screen
    )

    private fun entries(): List<Entry> = listOf(
        Entry("Circle", "Area and circumference", R.drawable.ic_square) { host ->
            FormScreen(
                host,
                "Circle",
                "Area and circumference of a circle.",
                listOf(FormScreen.FormField("radius", "Radius", "5", "units")),
                { values ->
                    val radius = values["radius"] ?: 0.0
                    host.activity.getString(
                        R.string.geo_circle,
                        Numbers.format(GeometryCalculator.circleArea(radius)),
                        Numbers.format(GeometryCalculator.circleCircumference(radius))
                    )
                }
            )
        },
        Entry("Rectangle", "Area and perimeter", R.drawable.ic_square) { host ->
            FormScreen(
                host,
                "Rectangle",
                "Area and perimeter of a rectangle.",
                listOf(
                    FormScreen.FormField("width", "Width", "4", "units"),
                    FormScreen.FormField("height", "Height", "6", "units")
                ),
                { values ->
                    val width = values["width"] ?: 0.0
                    val height = values["height"] ?: 0.0
                    host.activity.getString(
                        R.string.geo_rectangle,
                        Numbers.format(GeometryCalculator.rectangleArea(width, height)),
                        Numbers.format(GeometryCalculator.rectanglePerimeter(width, height))
                    )
                }
            )
        },
        Entry("Triangle", "Area of a triangle", R.drawable.ic_geometry) { host ->
            FormScreen(
                host,
                "Triangle",
                "Area of a triangle from its base and height.",
                listOf(
                    FormScreen.FormField("base", "Base", "8", "units"),
                    FormScreen.FormField("height", "Height", "3", "units")
                ),
                { values ->
                    host.activity.getString(
                        R.string.geo_triangle,
                        Numbers.format(GeometryCalculator.triangleArea(values["base"] ?: 0.0, values["height"] ?: 0.0))
                    )
                }
            )
        },
        Entry("Right triangle", "Hypotenuse of a right triangle", R.drawable.ic_geometry) { host ->
            FormScreen(
                host,
                "Right triangle",
                "Calculates the hypotenuse from the two legs.",
                listOf(
                    FormScreen.FormField("a", "Leg a", "3", "units"),
                    FormScreen.FormField("b", "Leg b", "4", "units")
                ),
                { values ->
                    host.activity.getString(
                        R.string.geo_right_triangle,
                        Numbers.format(GeometryCalculator.rightTriangleHypotenuse(values["a"] ?: 0.0, values["b"] ?: 0.0))
                    )
                }
            )
        },
        Entry("Trapezoid", "Area of a trapezoid", R.drawable.ic_square) { host ->
            FormScreen(
                host,
                "Trapezoid",
                "Area from the two parallel sides and the height.",
                listOf(
                    FormScreen.FormField("a", "Side a", "6", "units"),
                    FormScreen.FormField("b", "Side b", "4", "units"),
                    FormScreen.FormField("height", "Height", "5", "units")
                ),
                { values ->
                    host.activity.getString(
                        R.string.geo_trapezoid,
                        Numbers.format(
                            GeometryCalculator.trapezoidArea(
                                values["a"] ?: 0.0,
                                values["b"] ?: 0.0,
                                values["height"] ?: 0.0
                            )
                        )
                    )
                }
            )
        },
        Entry("Sphere", "Volume and surface area", R.drawable.ic_cube) { host ->
            FormScreen(
                host,
                "Sphere",
                "Volume and surface area of a sphere.",
                listOf(FormScreen.FormField("radius", "Radius", "3", "units")),
                { values ->
                    val radius = values["radius"] ?: 0.0
                    host.activity.getString(
                        R.string.geo_sphere,
                        Numbers.format(GeometryCalculator.sphereVolume(radius)),
                        Numbers.format(GeometryCalculator.sphereArea(radius))
                    )
                }
            )
        },
        Entry("Cylinder", "Volume of a cylinder", R.drawable.ic_cube) { host ->
            FormScreen(
                host,
                "Cylinder",
                "Volume of a cylinder from radius and height.",
                listOf(
                    FormScreen.FormField("radius", "Radius", "3", "units"),
                    FormScreen.FormField("height", "Height", "7", "units")
                ),
                { values ->
                    host.activity.getString(
                        R.string.geo_cylinder,
                        Numbers.format(
                            GeometryCalculator.cylinderVolume(values["radius"] ?: 0.0, values["height"] ?: 0.0)
                        )
                    )
                }
            )
        }
    )

    private fun entryRow(entry: Entry): View {
        val context = host.activity
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 8),
            Widgets.dp(context, 10), Widgets.dp(context, 8)
        )
        row.background = Widgets.roundedBackground(
            context, Widgets.color(context, R.color.surface), Widgets.color(context, R.color.outline)
        )
        row.isFocusable = true
        row.isClickable = true
        val icon = ImageView(context)
        icon.setImageResource(entry.icon)
        icon.setColorFilter(Widgets.color(context, R.color.primary))
        row.addView(icon, LinearLayout.LayoutParams(Widgets.dp(context, 22), Widgets.dp(context, 22)))
        row.addView(Widgets.spacer(context, 10))
        val column = Widgets.column(context)
        column.addView(Widgets.text(context, entry.title, R.style.Text_Body))
        column.addView(Widgets.text(context, entry.description, R.style.Text_Caption))
        row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener { host.push(entry.screen(host)) }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = Widgets.dp(context, 6)
        row.layoutParams = params
        return row
    }
}
