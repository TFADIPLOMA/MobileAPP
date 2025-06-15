// FaceOverlayView.kt
package com.example.tfaandroid

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class FaceOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var faceRects: List<Rect> = emptyList()
        set(value) {
            field = value
            invalidate() // Перерисовать экран
        }

    private val paint = Paint().apply {
        color = Color.GREEN //TRANSPARENT
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (rect in faceRects) {
            canvas.drawRect(rect, paint)
        }
    }
}
