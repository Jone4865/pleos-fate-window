package com.fatewindow.app.ui

import android.content.Context
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.fatewindow.app.vehicle.Seat
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * 차량 실내 3D 원근 일러스트 (이미지 에셋 없이 Canvas 로 직접 그림).
 *
 * 위에서 본 차를 원근 변환(뒤쪽 위에서 내려다보는 시점)으로 기울여 쇼룸 스테이지 위에 올린다.
 * 파노라마 글라스 루프 너머로 실내가 보이고, 실제 차량 연출(좌석별 독서등, 암전, 당첨 스포트라이트,
 * 창문 내려감)을 그대로 재현한다. 차 앞쪽이 화면 위, 운전석은 왼쪽(LHD).
 */
class CabinView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    // ================= 상태 =================
    private val seats = Seat.ALL
    private var occupied: Set<Seat> = seats.toSet()
    private val lightTarget = FloatArray(seats.size)
    private val lightNow = FloatArray(seats.size)
    private val windowTarget = FloatArray(seats.size)
    private val windowNow = FloatArray(seats.size)
    private var dimTarget = 0f
    private var dimNow = 0f
    private var winner: Seat? = null
    private var ambientMode = false
    private var lastFrame = 0L
    private var revealAt = 0L

    fun setOccupied(s: Collection<Seat>) { occupied = s.toSet(); invalidate() }
    fun setAmbientMode(on: Boolean) { ambientMode = on; invalidate() }

    /** 룰렛: 이 좌석만 켬 (null 이면 전부 끔) */
    fun spotlight(seat: Seat?) {
        for (i in seats.indices) lightTarget[i] = if (seats[i] == seat) 1f else 0f
        kick()
    }

    /** 실내 암전 */
    fun blackout(on: Boolean) { dimTarget = if (on) 1f else 0f; kick() }

    /** 당첨 연출 */
    fun reveal(seat: Seat, windowOpened: Boolean) {
        winner = seat
        revealAt = SystemClock.uptimeMillis()
        dimTarget = 1f
        spotlight(seat)
        if (windowOpened) windowTarget[seats.indexOf(seat)] = 1f
        kick()
    }

    /** 라운드 종료: 창문 닫고 조명 복귀 */
    fun resetRound() {
        winner = null
        lightTarget.fill(0f)
        windowTarget.fill(0f)
        dimTarget = 0f
        kick()
    }

    private fun kick() { lastFrame = 0L; postInvalidateOnAnimation() }

    // ================= 팔레트 =================
    private val gold = Color.parseColor("#FFD34D")
    private val cyan = Color.parseColor("#3FE0FF")
    private val ink = Color.parseColor("#0B0B14")
    private val paintMid = Color.parseColor("#3A4559")

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }

    // ================= 지오메트리 =================
    private var bw = 0f              // 차 폭
    private var bh = 0f              // 차 길이
    private var cx = 0f              // 차 중심 x
    private var top = 0f             // 차 앞끝 y
    private val persp = Matrix()     // 원근 변환
    private val camera = Camera()

    private val bodyPath = Path()
    private val hoodCreases = Path()
    private val headL = Path(); private val headR = Path()
    private val tail = Path()
    private val windshield = Path()
    private val rearGlass = Path()
    private val tub = RectF()
    private val dash = RectF()
    private val display = RectF()
    private val wheels = Array(4) { RectF() }
    private val mirrors = Array(2) { RectF() }
    private val seatRects = Array(seats.size) { RectF() }
    private val windowRects = Array(seats.size) { RectF() }

    private var stageShader: Shader? = null
    private var shadowShader: Shader? = null
    private var paintShader: Shader? = null
    private var sheenShader: Shader? = null
    private var glassShader: Shader? = null
    private var roofShader: Shader? = null
    private var tubShadeShader: Shader? = null
    private var displayShader: Shader? = null
    private val coneShaders = arrayOfNulls<Shader>(seats.size)

    private fun X(f: Float) = cx + bw * f     // f: -0.5(왼쪽) ~ 0.5(오른쪽)
    private fun Y(f: Float) = top + bh * f    // f: 0(앞) ~ 1(뒤)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val aspect = 0.46f
        bh = min(h * 0.98f, w * 0.70f / aspect)
        bw = bh * aspect
        cx = w / 2f
        top = (h - bh) / 2f

        // 원근: 앞쪽이 멀어지게 X축 회전 → 뒤쪽 위에서 내려다보는 시점
        camera.save()
        camera.setLocation(0f, 0f, -26f)
        camera.rotateX(30f)
        camera.getMatrix(persp)
        camera.restore()
        persp.preTranslate(-cx, -h / 2f)
        persp.postTranslate(cx, h / 2f + h * 0.03f)

        buildBody()
        buildDetails()
        buildShaders(h)
    }

    /** 펜더가 부푼 차체 실루엣 (오른쪽 반을 그리고 대칭) */
    private fun buildBody() {
        val right = listOf(
            floatArrayOf(0.22f, 0.000f, 0.36f, 0.012f, 0.41f, 0.055f),   // 코 → 앞 모서리
            floatArrayOf(0.46f, 0.095f, 0.50f, 0.140f, 0.500f, 0.205f),  // 앞 펜더
            floatArrayOf(0.50f, 0.300f, 0.465f, 0.380f, 0.465f, 0.500f), // 허리
            floatArrayOf(0.465f, 0.620f, 0.50f, 0.690f, 0.500f, 0.790f), // 뒤 펜더
            floatArrayOf(0.50f, 0.900f, 0.47f, 0.965f, 0.38f, 0.985f),   // 뒤 모서리
            floatArrayOf(0.25f, 1.000f, 0.10f, 1.000f, 0.000f, 1.000f),  // 꼬리 중앙
        )
        bodyPath.reset()
        bodyPath.moveTo(X(0f), Y(0f))
        right.forEach { s -> bodyPath.cubicTo(X(s[0]), Y(s[1]), X(s[2]), Y(s[3]), X(s[4]), Y(s[5])) }
        // 왼쪽: 같은 곡선을 거꾸로, x 반전
        val pts = mutableListOf(floatArrayOf(0f, 0f))
        right.forEach { pts += floatArrayOf(it[4], it[5]) }
        for (k in right.indices.reversed()) {
            val s = right[k]
            val end = pts[k]
            bodyPath.cubicTo(X(-s[2]), Y(s[3]), X(-s[0]), Y(s[1]), X(-end[0]), Y(end[1]))
        }
        bodyPath.close()
    }

    private fun buildDetails() {
        // 바퀴 (차체 밖으로 살짝 보임)
        wheels[0].set(X(-0.535f), Y(0.125f), X(-0.415f), Y(0.240f))
        wheels[1].set(X(0.415f), Y(0.125f), X(0.535f), Y(0.240f))
        wheels[2].set(X(-0.535f), Y(0.720f), X(-0.415f), Y(0.835f))
        wheels[3].set(X(0.415f), Y(0.720f), X(0.535f), Y(0.835f))
        // 사이드미러
        mirrors[0].set(X(-0.59f), Y(0.262f), X(-0.47f), Y(0.300f))
        mirrors[1].set(X(0.47f), Y(0.262f), X(0.59f), Y(0.300f))

        hoodCreases.reset()
        for (sgn in listOf(-1f, 1f)) {
            hoodCreases.moveTo(X(sgn * 0.13f), Y(0.035f))
            hoodCreases.cubicTo(X(sgn * 0.17f), Y(0.08f), X(sgn * 0.21f), Y(0.14f), X(sgn * 0.23f), Y(0.19f))
        }
        headR.reset(); headR.moveTo(X(0.12f), Y(0.020f)); headR.quadTo(X(0.33f), Y(0.022f), X(0.405f), Y(0.062f))
        headL.reset(); headL.moveTo(X(-0.12f), Y(0.020f)); headL.quadTo(X(-0.33f), Y(0.022f), X(-0.405f), Y(0.062f))
        tail.reset(); tail.moveTo(X(-0.37f), Y(0.972f)); tail.quadTo(X(0f), Y(0.992f), X(0.37f), Y(0.972f))

        windshield.reset()
        windshield.moveTo(X(-0.30f), Y(0.195f)); windshield.quadTo(X(0f), Y(0.180f), X(0.30f), Y(0.195f))
        windshield.lineTo(X(0.385f), Y(0.262f)); windshield.lineTo(X(-0.385f), Y(0.262f)); windshield.close()

        rearGlass.reset()
        rearGlass.moveTo(X(-0.385f), Y(0.872f)); rearGlass.lineTo(X(0.385f), Y(0.872f))
        rearGlass.lineTo(X(0.31f), Y(0.925f)); rearGlass.quadTo(X(0f), Y(0.935f), X(-0.31f), Y(0.925f)); rearGlass.close()

        tub.set(X(-0.385f), Y(0.255f), X(0.385f), Y(0.880f))
        dash.set(X(-0.365f), Y(0.262f), X(0.365f), Y(0.305f))
        display.set(X(-0.12f), Y(0.268f), X(0.12f), Y(0.297f))

        // 좌석: 앞줄 / 뒷줄, 왼쪽 = 운전석 쪽
        seats.forEachIndexed { i, s ->
            val left = s.side == Seat.Side.DRIVER
            val t = if (s.row == 1) 0.355f else 0.630f
            val b = t + 0.225f
            val x0 = if (left) -0.335f else 0.035f
            seatRects[i].set(X(x0), Y(t), X(x0 + 0.30f), Y(b))
            val wx = if (left) -0.432f else 0.388f
            windowRects[i].set(X(wx), Y(t + 0.005f), X(wx + 0.044f), Y(b - 0.015f))
        }
    }

    private fun buildShaders(h: Int) {
        stageShader = RadialGradient(cx, Y(0.5f), bh * 0.62f,
            intArrayOf(Color.argb(170, 32, 48, 80), Color.argb(60, 20, 26, 44), Color.TRANSPARENT),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        shadowShader = RadialGradient(cx, Y(0.52f), bh * 0.55f,
            intArrayOf(Color.argb(200, 0, 0, 0), Color.argb(90, 0, 0, 0), Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        // 메탈릭 도장: 가장자리 어둡고 가운데 하이라이트
        paintShader = LinearGradient(X(-0.5f), 0f, X(0.5f), 0f,
            intArrayOf(Color.parseColor("#0C0E14"), Color.parseColor("#2B3446"), Color.parseColor("#55627C"),
                Color.parseColor("#2B3446"), Color.parseColor("#0C0E14")),
            floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f), Shader.TileMode.CLAMP)
        sheenShader = LinearGradient(0f, Y(0f), 0f, Y(1f),
            intArrayOf(Color.argb(70, 255, 255, 255), Color.TRANSPARENT, Color.argb(90, 0, 0, 0)),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        glassShader = LinearGradient(0f, Y(0.18f), 0f, Y(0.26f),
            Color.parseColor("#6C93B8"), Color.parseColor("#162334"), Shader.TileMode.CLAMP)
        roofShader = LinearGradient(tub.left, tub.top, tub.right, tub.bottom,
            intArrayOf(Color.argb(10, 200, 230, 255), Color.argb(38, 200, 230, 255), Color.argb(6, 200, 230, 255)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        tubShadeShader = RadialGradient(tub.centerX(), tub.centerY(), tub.height() * 0.62f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(170, 0, 0, 0)),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        displayShader = LinearGradient(display.left, display.top, display.right, display.bottom,
            Color.parseColor("#2E6496"), Color.parseColor("#0E2236"), Shader.TileMode.CLAMP)
        seats.indices.forEach { i ->
            val r = seatRects[i]
            coneShaders[i] = RadialGradient(r.centerX(), r.centerY(), r.width() * 0.95f,
                intArrayOf(Color.argb(240, 255, 238, 175), Color.argb(130, 255, 211, 77), Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        }
    }

    // ================= 그리기 =================
    override fun onDraw(canvas: Canvas) {
        if (bw == 0f) return
        val moving = step()

        canvas.save()
        canvas.concat(persp)

        drawStage(canvas)
        drawShell(canvas)
        drawInterior(canvas)
        seats.indices.forEach { drawSeat(canvas, it, lit = false) }
        drawRoofGlass(canvas)
        seats.indices.forEach { drawWindow(canvas, it) }

        // 암전: 차 전체를 어둡게
        if (dimNow > 0.001f) {
            fill.shader = null
            fill.color = Color.argb((205 * dimNow).toInt(), 2, 2, 6)
            canvas.drawPath(bodyPath, fill)
            wheels.forEach { canvas.drawRoundRect(it, bw * 0.03f, bw * 0.03f, fill) }
        }

        // 빛은 암전 위에: 어둠 속 스포트라이트
        drawLamps(canvas)
        drawAmbient(canvas)
        seats.indices.forEach { i ->
            if (lightNow[i] > 0.01f) {
                drawCone(canvas, i)
                drawSeat(canvas, i, lit = true)
            }
        }
        seats.indices.forEach { drawWindowOpening(canvas, it) }
        winner?.let { drawWinnerRing(canvas, seats.indexOf(it)) }
        canvas.restore()

        // 글자는 원근 없이 또렷하게 (좌표만 변환)
        seats.indices.forEach { drawLabel(canvas, it) }
        seats.indices.forEach { drawWindowText(canvas, it) }
        winner?.let { drawWinnerBadge(canvas, seats.indexOf(it)) }

        if (moving || winner != null || windowNow.any { it > 0.01f }) postInvalidateOnAnimation()
    }

    /** 목표값으로 부드럽게 접근. 아직 움직이는 중이면 true */
    private fun step(): Boolean {
        val t = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 16f else (t - lastFrame).coerceAtMost(64).toFloat()
        lastFrame = t
        var moving = false
        fun approach(cur: Float, tgt: Float, speed: Float): Float {
            val d = tgt - cur
            if (abs(d) < 0.003f) return tgt
            moving = true
            return cur + d * min(1f, dt / 1000f * speed)
        }
        for (i in seats.indices) {
            lightNow[i] = approach(lightNow[i], lightTarget[i], 22f)
            windowNow[i] = approach(windowNow[i], windowTarget[i], 1.4f)
        }
        dimNow = approach(dimNow, dimTarget, 7f)
        return moving
    }

    private fun drawStage(c: Canvas) {
        fill.shader = stageShader
        c.drawCircle(cx, Y(0.5f), bh * 0.62f, fill)
        fill.shader = null
        stroke.shader = null
        stroke.strokeWidth = bw * 0.006f
        stroke.color = Color.argb(70, 255, 211, 77)
        c.drawCircle(cx, Y(0.5f), bh * 0.56f, stroke)
        stroke.color = Color.argb(40, 63, 224, 255)
        c.drawCircle(cx, Y(0.5f), bh * 0.61f, stroke)
        // 그림자
        fill.shader = shadowShader
        c.drawOval(X(-0.62f), Y(-0.02f), X(0.62f), Y(1.04f), fill)
        fill.shader = null
    }

    private fun drawShell(c: Canvas) {
        // 바퀴 (차체가 안쪽을 덮음)
        fill.shader = null
        wheels.forEach { r ->
            fill.color = Color.parseColor("#07070A")
            c.drawRoundRect(r, bw * 0.03f, bw * 0.03f, fill)
            stroke.strokeWidth = bw * 0.006f
            stroke.color = Color.parseColor("#2A2D36")
            for (k in 1..4) {
                val yy = r.top + r.height() * k / 5f
                c.drawLine(r.left + r.width() * 0.15f, yy, r.right - r.width() * 0.15f, yy, stroke)
            }
        }
        // 사이드미러
        mirrors.forEach { m ->
            fill.shader = paintShader
            c.drawRoundRect(m, m.height() * 0.5f, m.height() * 0.5f, fill)
            fill.shader = null
            fill.color = Color.parseColor("#9FD6FF")
            fill.alpha = 90
            c.drawRoundRect(m.left + m.width() * 0.15f, m.top + m.height() * 0.25f, m.right - m.width() * 0.15f, m.bottom - m.height() * 0.25f, m.height() * 0.2f, m.height() * 0.2f, fill)
            fill.alpha = 255
        }
        // 차체 도장 + 상하 광택
        fill.shader = paintShader
        c.drawPath(bodyPath, fill)
        fill.shader = sheenShader
        c.drawPath(bodyPath, fill)
        fill.shader = null
        // 윤곽 하이라이트
        stroke.strokeWidth = bw * 0.006f
        stroke.color = Color.argb(120, 160, 180, 210)
        c.drawPath(bodyPath, stroke)
        // 보닛 주름 + 중앙 하이라이트
        stroke.strokeWidth = bw * 0.005f
        stroke.color = Color.argb(110, 0, 0, 0)
        c.drawPath(hoodCreases, stroke)
        stroke.color = Color.argb(60, 255, 255, 255)
        c.save(); c.translate(bw * 0.006f, 0f); c.drawPath(hoodCreases, stroke); c.restore()
        // 문 틈 + 손잡이
        stroke.color = Color.argb(150, 0, 0, 0)
        stroke.strokeWidth = bw * 0.004f
        for (sgn in listOf(-1f, 1f)) {
            c.drawLine(X(sgn * 0.435f), Y(0.335f), X(sgn * 0.488f), Y(0.335f), stroke)
            c.drawLine(X(sgn * 0.435f), Y(0.610f), X(sgn * 0.470f), Y(0.610f), stroke)
            fill.color = Color.argb(160, 200, 210, 230)
            c.drawRoundRect(X(sgn * 0.452f) - bw * 0.012f, Y(0.47f), X(sgn * 0.452f) + bw * 0.012f, Y(0.50f), bw * 0.01f, bw * 0.01f, fill)
            c.drawRoundRect(X(sgn * 0.448f) - bw * 0.012f, Y(0.74f), X(sgn * 0.448f) + bw * 0.012f, Y(0.77f), bw * 0.01f, bw * 0.01f, fill)
        }
        // 앞유리 + 반사
        fill.shader = glassShader
        c.drawPath(windshield, fill)
        fill.shader = null
        stroke.strokeWidth = bw * 0.008f
        stroke.color = Color.argb(80, 255, 255, 255)
        c.drawLine(X(-0.22f), Y(0.205f), X(-0.05f), Y(0.200f), stroke)
        // 뒷유리
        fill.color = Color.parseColor("#1A2B3F")
        c.drawPath(rearGlass, fill)
    }

    private fun drawLamps(c: Canvas) {
        // LED 헤드램프 + 테일램프 바 (암전 중에도 은은하게 켜 둠 → 차 형태가 살아 있음)
        for (p in listOf(headL, headR)) {
            stroke.color = Color.argb(70, 160, 230, 255); stroke.strokeWidth = bw * 0.035f; c.drawPath(p, stroke)
            stroke.color = Color.parseColor("#E8FBFF"); stroke.strokeWidth = bw * 0.011f; c.drawPath(p, stroke)
        }
        stroke.color = Color.argb(90, 255, 40, 70); stroke.strokeWidth = bw * 0.04f; c.drawPath(tail, stroke)
        stroke.color = Color.parseColor("#FF3550"); stroke.strokeWidth = bw * 0.012f; c.drawPath(tail, stroke)
    }

    private fun drawInterior(c: Canvas) {
        fill.shader = null
        fill.color = Color.parseColor("#0E0F15")
        c.drawRoundRect(tub, bw * 0.10f, bw * 0.10f, fill)
        // 대시보드 + 센터 디스플레이 + 운전대
        fill.color = Color.parseColor("#1A1C26")
        c.drawRoundRect(dash, bw * 0.03f, bw * 0.03f, fill)
        fill.shader = displayShader
        c.drawRoundRect(display, bw * 0.012f, bw * 0.012f, fill)
        fill.shader = null
        fill.color = gold
        c.drawCircle(display.centerX(), display.centerY(), bw * 0.008f, fill)
        val drv = seatRects[0]
        stroke.color = Color.parseColor("#3A3F50")
        stroke.strokeWidth = bw * 0.018f
        c.drawCircle(drv.centerX(), dash.bottom + bw * 0.035f, bw * 0.068f, stroke)
        stroke.strokeWidth = bw * 0.012f
        c.drawLine(drv.centerX() - bw * 0.068f, dash.bottom + bw * 0.035f, drv.centerX() + bw * 0.068f, dash.bottom + bw * 0.035f, stroke)
        // 센터 콘솔
        fill.color = Color.parseColor("#181A23")
        c.drawRoundRect(X(-0.028f), seatRects[0].top + bw * 0.02f, X(0.028f), seatRects[0].bottom, bw * 0.02f, bw * 0.02f, fill)
        // 실내 가장자리 음영 (깊이감)
        fill.shader = tubShadeShader
        c.drawRoundRect(tub, bw * 0.10f, bw * 0.10f, fill)
        fill.shader = null
    }

    private fun drawRoofGlass(c: Canvas) {
        // 루프 레일 + B필러
        stroke.shader = null
        stroke.color = paintMid
        stroke.strokeWidth = bw * 0.020f
        c.drawRoundRect(tub, bw * 0.10f, bw * 0.10f, stroke)
        fill.shader = null
        fill.color = Color.argb(225, 42, 50, 66)
        c.drawRect(X(-0.385f), Y(0.596f), X(0.385f), Y(0.612f), fill)
        // 파노라마 글라스 반사
        fill.shader = roofShader
        c.drawRoundRect(tub, bw * 0.10f, bw * 0.10f, fill)
        fill.shader = null
        val streak = Path().apply {
            moveTo(X(0.10f), Y(0.33f)); lineTo(X(0.22f), Y(0.33f)); lineTo(X(-0.02f), Y(0.86f)); lineTo(X(-0.14f), Y(0.86f)); close()
        }
        fill.color = Color.argb((14 * (1f - dimNow)).toInt(), 255, 255, 255)
        c.drawPath(streak, fill)
    }

    private fun drawSeat(c: Canvas, i: Int, lit: Boolean) {
        val r = seatRects[i]
        // 빈 좌석은 흐리게 (탑승자 그림 없이 착좌 여부 표시)
        val a = (if (lit) lightNow[i] else 1f) * (if (seats[i] in occupied) 1f else 0.4f)
        val w = r.width()
        val h = r.height()
        val rad = w * 0.20f
        val cushion = RectF(r.left + w * 0.07f, r.top, r.right - w * 0.07f, r.top + h * 0.56f)
        val back = RectF(r.left, r.top + h * 0.52f, r.right, r.top + h * 0.86f)
        val head = RectF(r.left + w * 0.30f, r.top + h * 0.87f, r.right - w * 0.30f, r.bottom)

        val hi = if (lit) Color.parseColor("#C9A445") else Color.parseColor("#454B5E")
        val lo = if (lit) Color.parseColor("#6B5626") else Color.parseColor("#22252F")
        fill.shader = LinearGradient(r.left, r.top, r.right, r.top, intArrayOf(lo, hi, lo), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        fill.alpha = (255 * a).toInt()
        c.drawRoundRect(cushion, rad, rad, fill)
        c.drawRoundRect(back, rad, rad, fill)
        c.drawRoundRect(head, rad * 0.6f, rad * 0.6f, fill)
        fill.shader = null
        // 사이드 볼스터 그림자
        fill.color = Color.argb((70 * a).toInt(), 0, 0, 0)
        c.drawRoundRect(back.left, back.top, back.left + w * 0.12f, back.bottom, rad, rad, fill)
        c.drawRoundRect(back.right - w * 0.12f, back.top, back.right, back.bottom, rad, rad, fill)
        fill.alpha = 255
        // 박음질
        stroke.strokeWidth = w * 0.010f
        stroke.color = Color.argb((45 * a).toInt(), 255, 255, 255)
        c.drawLine(cushion.left + w * 0.18f, cushion.top + h * 0.06f, cushion.left + w * 0.18f, cushion.bottom - h * 0.06f, stroke)
        c.drawLine(cushion.right - w * 0.18f, cushion.top + h * 0.06f, cushion.right - w * 0.18f, cushion.bottom - h * 0.06f, stroke)
    }

    private fun drawCone(c: Canvas, i: Int) {
        val r = seatRects[i]
        fill.shader = coneShaders[i]
        fill.alpha = (255 * lightNow[i]).toInt()
        c.drawCircle(r.centerX(), r.centerY(), r.width() * 0.95f, fill)
        fill.shader = null
        fill.alpha = 255
        fill.color = Color.argb((255 * lightNow[i]).toInt(), 255, 250, 225)
        c.drawCircle(r.centerX(), r.top + r.height() * 0.30f, bw * 0.012f, fill)
    }

    private fun drawAmbient(c: Canvas) {
        val base = 0.45f * (1f - dimNow)
        stroke.shader = null
        seats.indices.forEach { i ->
            val boost = if (ambientMode) lightNow[i] else 0f
            val k = (base + boost).coerceAtMost(1f)
            if (k < 0.02f) return@forEach
            val r = seatRects[i]
            val x = if (seats[i].side == Seat.Side.DRIVER) tub.left + bw * 0.016f else tub.right - bw * 0.016f
            stroke.color = cyan
            stroke.alpha = (90 * k).toInt()
            stroke.strokeWidth = bw * 0.028f
            c.drawLine(x, r.top + r.height() * 0.10f, x, r.bottom - r.height() * 0.10f, stroke)
            stroke.alpha = (255 * k).toInt()
            stroke.strokeWidth = bw * 0.007f
            c.drawLine(x, r.top + r.height() * 0.10f, x, r.bottom - r.height() * 0.10f, stroke)
        }
        stroke.alpha = 255
    }

    private fun drawWindow(c: Canvas, i: Int) {
        val w = windowRects[i]
        val glassBottom = w.bottom - (w.height() * 0.88f) * windowNow[i]
        fill.shader = LinearGradient(w.left, w.top, w.right, w.bottom,
            Color.parseColor("#86AED2"), Color.parseColor("#22405E"), Shader.TileMode.CLAMP)
        c.drawRoundRect(w.left, w.top, w.right, glassBottom, w.width() * 0.4f, w.width() * 0.4f, fill)
        fill.shader = null
    }

    private fun drawWindowOpening(c: Canvas, i: Int) {
        val f = windowNow[i]
        if (f < 0.01f) return
        val w = windowRects[i]
        val glassBottom = w.bottom - (w.height() * 0.88f) * f
        fill.color = cyan
        fill.alpha = (210 * f).toInt()
        c.drawRoundRect(w.left, glassBottom, w.right, w.bottom, w.width() * 0.4f, w.width() * 0.4f, fill)
        fill.alpha = (70 * f).toInt()
        c.drawRoundRect(w.left - w.width() * 0.8f, glassBottom, w.right + w.width() * 0.8f, w.bottom, w.width(), w.width(), fill)
        fill.alpha = 255
        // 바람 줄기
        val out = if (seats[i].side == Seat.Side.DRIVER) -1f else 1f
        val x0 = if (out < 0) X(-0.52f) else X(0.52f)
        val phase = (SystemClock.uptimeMillis() % 1200) / 1200f
        stroke.color = Color.argb((210 * f).toInt(), 160, 240, 255)
        stroke.strokeWidth = bw * 0.009f
        for (k in 0 until 3) {
            val yy = w.top + w.height() * (0.25f + 0.25f * k)
            val len = bw * (0.07f + 0.035f * sin((phase + k * 0.3f) * 6.283f))
            c.drawLine(x0, yy, x0 + out * len, yy, stroke)
        }
    }

    private fun drawWinnerRing(c: Canvas, i: Int) {
        val r = seatRects[i]
        val t = (SystemClock.uptimeMillis() - revealAt) / 1000f
        val pulse = 0.5f + 0.5f * sin(t * 4f)
        stroke.color = gold
        stroke.alpha = (120 + 120 * pulse).toInt()
        stroke.strokeWidth = bw * (0.010f + 0.008f * pulse)
        val pad = bw * (0.015f + 0.015f * pulse)
        c.drawRoundRect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad, r.width() * 0.22f, r.width() * 0.22f, stroke)
        stroke.alpha = 255
    }

    // ---------- 원근 밖에서 그리는 글자 ----------
    private val pt = FloatArray(2)
    private fun map(x: Float, y: Float): FloatArray { pt[0] = x; pt[1] = y; persp.mapPoints(pt); return pt }

    private fun drawLabel(c: Canvas, i: Int) {
        val r = seatRects[i]
        val litK = lightNow[i]
        val visible = (1f - dimNow * (1f - litK)).coerceIn(0.15f, 1f)
        // 좌석 이름은 좌석 바로 아래(뒤쪽)에: 탑승자를 가리지 않게
        val p = map(r.centerX(), r.bottom)
        text.textSize = sp(28f)
        text.color = if (litK > 0.5f) gold else Color.parseColor("#D6D6E4")
        text.alpha = (255 * visible).toInt()
        val below = if (seats[i] == winner) sp(48f) else sp(30f)   // 당첨 링과 겹치지 않게
        c.drawText(seats[i].label, p[0], p[1] + below, text)
        if (seats[i] !in occupied) {
            val q = map(r.centerX(), r.top + r.height() * 0.45f)
            text.textSize = sp(22f)
            text.color = Color.parseColor("#8A8AA6")
            text.alpha = (255 * visible).toInt()
            c.drawText("빈 좌석", q[0], q[1], text)
        }
        text.alpha = 255
    }

    private fun drawWindowText(c: Canvas, i: Int) {
        val f = windowNow[i]
        if (f < 0.01f) return
        val w = windowRects[i]
        val out = if (seats[i].side == Seat.Side.DRIVER) -1f else 1f
        val p = map(if (out < 0) X(-0.62f) else X(0.62f), w.centerY())
        text.textSize = sp(26f)
        text.color = cyan
        text.alpha = (255 * f).toInt()
        c.drawText("열림", p[0], p[1] + sp(40f), text)
        text.alpha = 255
    }

    private fun drawWinnerBadge(c: Canvas, i: Int) {
        val r = seatRects[i]
        val p = map(r.centerX(), r.top)
        text.textSize = sp(34f)
        val label = "당첨"
        val tw = text.measureText(label)
        val wBadge = tw + sp(44f)
        val hBadge = sp(56f)
        val cy = p[1] - hBadge * 0.25f
        fill.shader = null
        fill.color = gold
        c.drawRoundRect(p[0] - wBadge / 2, cy - hBadge / 2, p[0] + wBadge / 2, cy + hBadge / 2, hBadge / 2, hBadge / 2, fill)
        text.color = ink
        c.drawText(label, p[0], cy + text.textSize * 0.36f, text)
    }
}
