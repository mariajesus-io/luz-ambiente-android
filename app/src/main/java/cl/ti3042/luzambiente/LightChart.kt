package cl.ti3042.luzambiente

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class LightChart(context: Context) : View(context) {
    private val values = ArrayDeque<Float>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    fun clear() { values.clear(); invalidate() }
    fun add(value: Float) { values.addLast(value); if (values.size > 40) values.removeFirst(); invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val left = 12f * d; val top = 28f * d
        val w = width - 2 * left; val h = height - top - 20f * d
        paint.color = Color.rgb(90, 110, 107); paint.textSize = 12f * d; paint.style = Paint.Style.FILL
        val max = maxOf(1000f, values.maxOrNull() ?: 1000f)
        canvas.drawText("Últimas 40 lecturas • máximo %.0f lx".format(max), left, 17f * d, paint)
        paint.color = Color.rgb(218, 229, 225); paint.strokeWidth = d
        for (i in 0..3) canvas.drawLine(left, top + h * i / 3, left + w, top + h * i / 3, paint)
        if (values.size < 2) return
        path.reset()
        values.forEachIndexed { index, value ->
            val x = left + w * index / 39f; val y = top + h * (1 - value / max)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.color = Color.rgb(8, 127, 117); paint.strokeWidth = 3f * d; paint.style = Paint.Style.STROKE
        canvas.drawPath(path, paint)
    }
}
