package com.sunpanel.widget

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * 专业 HSV 颜色选择器
 * 上：色相条（横向渐变，可拖动）
 * 下：饱和度/亮度取色方块（横向=S，纵向=V，可拖动）
 * 类似 PS / 画图里的取色器
 */
class HsvColorPickerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 当前颜色值
    private var hue = 0f          // 色相 0~360
    private var saturation = 1f   // 饱和度 0~1
    private var value = 1f        // 亮度 0~1

    // 布局区域
    private val hueBarRect = RectF()      // 色相条区域
    private val svRect = RectF()          // SV 方块区域
    private val barHeight = 40f           // 色相条高度
    private val margin = 16f              // 边距
    private val cornerRadius = 8f

    // 画笔
    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val svPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }
    private val indicatorPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#666666")
    }

    private var barThumbX = 0f   // 色相条滑块位置
    private var svThumbX = 0f    // SV 方块滑块位置
    private var svThumbY = 0f

    // 回调
    var onColorChanged: ((color: Int) -> Unit)? = null

    // 色相条渐变颜色数组
    private val hueColors = intArrayOf(
        Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
        Color.BLUE, Color.MAGENTA, Color.RED
    )
    private val huePositions = floatArrayOf(0f, 1f/6, 2f/6, 3f/6, 4f/6, 5f/6, 1f)
    private val hueGradient = LinearGradient(0f, 0f, 1f, 0f, hueColors, huePositions, Shader.TileMode.CLAMP)

    // SV 方块着色器
    private var svShader: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = margin * density
        val availableWidth = w - pad * 2
        val svSize = availableWidth

        // 色相条区域（顶部）
        hueBarRect.set(pad, pad, pad + availableWidth, pad + barHeight * density)

        // SV 方块（下方）
        val svTop = hueBarRect.bottom + 12f * density
        svRect.set(pad, svTop, pad + svSize, svTop + svSize)

        // 初始化滑块位置
        barThumbX = hueBarRect.left + hue / 360f * availableWidth
        svThumbX = svRect.left + saturation * svSize
        svThumbY = svRect.top + (1f - value) * svSize

        // 更新着色器
        updateShaders(w.toFloat(), h.toFloat())
    }

    private fun updateShaders(w: Float, h: Float) {
        // 色相条着色器
        hueGradient.setLocalMatrix(Matrix().apply {
            setScale(w, 1f)
        })
        // 色相条用固定布局比例
        huePaint.shader = LinearGradient(
            hueBarRect.left, 0f, hueBarRect.right, 0f,
            hueColors, huePositions, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = density
        val r = cornerRadius * density

        // 绘制色相条
        canvas.drawRoundRect(hueBarRect, r, r, huePaint)

        // 色相条边框
        canvas.drawRoundRect(hueBarRect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.parseColor("#DDDDDD")
        })

        // 色相条滑块（圆形指示器）
        val barCx = barThumbX
        val barCy = hueBarRect.centerY()
        canvas.drawCircle(barCx, barCy, 8f * density, indicatorPaint)
        canvas.drawCircle(barCx, barCy, 8f * density, indicatorPaint2)

        // 绘制 SV 方块
        val svBg = Paint(Paint.ANTI_ALIAS_FLAG)
        // 当前色相对应的纯色
        val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        // 第一层：饱和度渐变（横向） 白 → 纯色
        svBg.shader = LinearGradient(
            svRect.left, 0f, svRect.right, 0f,
            Color.WHITE, hueColor, Shader.TileMode.CLAMP
        )
        val svR = r
        canvas.drawRoundRect(svRect, svR, svR, svBg)
        // 第二层：亮度渐变（纵向） 透明黑 → 纯黑（叠加变暗，保留色相）
        svBg.shader = LinearGradient(
            0f, svRect.top, 0f, svRect.bottom,
            0x00000000, 0xFF000000, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(svRect, svR, svR, svBg)
        // SV 边框
        canvas.drawRoundRect(svRect, svR, svR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.parseColor("#DDDDDD")
        })

        // SV 滑块（圆圈）
        canvas.drawCircle(svThumbX, svThumbY, 8f * density, indicatorPaint)
        canvas.drawCircle(svThumbX, svThumbY, 8f * density, indicatorPaint2)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // 判断触摸区域
                if (hueBarRect.contains(x, y) || (y >= hueBarRect.bottom && y <= hueBarRect.bottom + 20f * density)) {
                    // 色相条
                    val clamped = x.coerceIn(hueBarRect.left, hueBarRect.right)
                    barThumbX = clamped
                    hue = (clamped - hueBarRect.left) / hueBarRect.width() * 360f
                    hue = hue.coerceIn(0f, 360f)
                    // 更新 SV 方块（色相变了，需要重绘）
                    invalidate()
                    notifyColorChanged()
                    return true
                }
                if (svRect.contains(x, y)) {
                    // SV 方块
                    svThumbX = x.coerceIn(svRect.left, svRect.right)
                    svThumbY = y.coerceIn(svRect.top, svRect.bottom)
                    saturation = (svThumbX - svRect.left) / svRect.width()
                    value = 1f - (svThumbY - svRect.top) / svRect.height()
                    saturation = saturation.coerceIn(0f, 1f)
                    value = value.coerceIn(0f, 1f)
                    invalidate()
                    notifyColorChanged()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun notifyColorChanged() {
        val color = Color.HSVToColor(floatArrayOf(hue, saturation, value))
        onColorChanged?.invoke(color)
    }

    // ========== 公共接口 ==========

    /** 设置当前颜色（同步滑块位置） */
    fun setColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue = hsv[0].coerceIn(0f, 360f)
        saturation = hsv[1].coerceIn(0f, 1f)
        value = hsv[2].coerceIn(0f, 1f)
        // 更新滑块位置
        if (width > 0) {
            val available = (width - margin * 2 * density).coerceAtLeast(1f)
            barThumbX = hueBarRect.left + hue / 360f * (hueBarRect.width().coerceAtLeast(1f))
            val svSize = svRect.width().coerceAtLeast(1f)
            svThumbX = svRect.left + saturation * svSize
            svThumbY = svRect.top + (1f - value) * svSize
        }
        invalidate()
    }

    /** 获取当前颜色（ARGB） */
    fun getColor(): Int = Color.HSVToColor(floatArrayOf(hue, saturation, value))

    /** 获取当前颜色（十六进制字符串） */
    fun getHexColor(): String = String.format("#%06X", 0xFFFFFF and getColor())

    private val density: Float get() = resources.displayMetrics.density
}