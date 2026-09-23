package com.example.starjump

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Random
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * لعبة "قفزة النجوم" ⭐🚀 — لعبة لا نهائية ممتعة!
 */
class GameActivity : AppCompatActivity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameView = GameView(this)
        setContentView(gameView)
    }

    override fun onPause() { super.onPause(); gameView.pause() }
    override fun onResume() { super.onResume(); gameView.resume() }
}

// ═══════════════ أنواع الكيانات ═══════════════

internal enum class ItemType { STAR, BIG_STAR, DIAMOND, SPIKE }

internal class Item(
    var x: Float,
    val track: Int,
    val type: ItemType,
    var collected: Boolean = false,
    val phase: Float = Random().nextFloat() * 6.28f
)

internal data class WorldTheme(
    val name: String,
    val skyTop: Int,
    val skyBottom: Int,
    val ground: Int,
    val groundLine: Int,
    val accent: Int
)

// ═══════════════ سطح اللعبة ═══════════════

class GameView(context: Context) : SurfaceView(context), Runnable, SurfaceHolder.Callback {

    @Volatile private var running = false
    private var gameThread: Thread? = null

    // حالة اللاعب
    private var playerY = 0f
    private var velocityY = 0f
    private var onGround = true
    private var usedDoubleJump = false

    // العالم
    private var distance = 0f
    private var speed = 0.18f
    private var score = 0L
    private var combo = 0
    private var shieldTime = 0f
    private var magnetTime = 0f
    private var currentWorld = 0

    private var bestScore = loadBestScore(context)

    private val items = ArrayList<Item>()
    private var nextSpawnX = 0.7f
    private var lastSpikeTrack = -1

    // مؤثرات
    private inner class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val color: Int, val size: Float
    )

    private val particles = ArrayList<Particle>()
    private var shakeTime = 0f
    private var bannerText = ""
    private var bannerTime = 0f
    private var gameOverAt = -1L

    private var gameOver = false

    // الرسم
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val starPath = Path()
    private val spikePath = Path()
    private val heartDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_heart)
    private val rocketDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_rocket)
    private val diamondDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_diamond)

    private val themes = listOf(
        WorldTheme("غروب", 0xFF1A2151.toInt(), 0xFFFF7E5F.toInt(), 0xFF2E7D32.toInt(), 0xFF66BB6A.toInt(), 0xFFFFD54F.toInt()),
        WorldTheme("فضاء", 0xFF0D0221.toInt(), 0xFF261447.toInt(), 0xFF4A148C.toInt(), 0xFF7B1FA2.toInt(), 0xFF4FC3F7.toInt()),
        WorldTheme("قطبي", 0xFF0F2027.toInt(), 0xFF2C5364.toInt(), 0xFFE0E5EC.toInt(), 0xFF80DEEA.toInt(), 0xFFFFAB40.toInt()),
        WorldTheme("غابة", 0xFF0B3D2E.toInt(), 0xFF1B5E20.toInt(), 0xFF33691E.toInt(), 0xFF9CCC65.toInt(), 0xFFFFEE58.toInt())
    )

    init {
        holder.addCallback(this)
        isFocusable = true
        buildStarPath()
    }

    private fun buildStarPath() {
        starPath.reset()
        val spikes = 5
        val outer = 1f
        val inner = 0.45f
        var rot = -Math.PI / 2
        val step = Math.PI / spikes
        for (i in 0 until spikes * 2) {
            val r = if (i % 2 == 0) outer else inner
            val px = cos(rot) * r
            val py = sin(rot) * r
            if (i == 0) starPath.moveTo(px.toFloat(), py.toFloat())
            else starPath.lineTo(px.toFloat(), py.toFloat())
            rot += step
        }
        starPath.close()
    }

    // ═════════ حلقة اللعبة ═════════

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            var dt = (now - last) / 1_000_000_000f
            last = now
            if (dt > 0.1f) dt = 0.1f
            update(dt)
            drawFrame()
            val frameMs = (System.nanoTime() - now) / 1_000_000
            if (frameMs < 16) try {
                Thread.sleep(16 - frameMs)
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun update(dt: Float) {
        if (gameOver) {
            if (shakeTime > 0) shakeTime -= dt
            updateParticles(dt)
            return
        }

        // صعوبة متزايدة بلا نهاية
        speed = 0.18f + minOf(distance * 0.00006f, 0.35f)
        distance += speed * dt * 100f

        // الجاذبية
        if (!onGround) {
            velocityY -= 2600f * dt
            playerY += velocityY * dt
            if (playerY <= 0f) {
                playerY = 0f
                onGround = true
                usedDoubleJump = false
            }
        }

        if (shieldTime > 0) shieldTime -= dt
        if (magnetTime > 0) magnetTime -= dt
        if (bannerTime > 0) bannerTime -= dt
        if (shakeTime > 0) shakeTime -= dt

        spawnItems()
        moveAndCollide(dt)
        updateParticles(dt)

        // تبديل العوالم — للأبد!
        val worldIndex = ((distance / 900f).toInt()) % themes.size
        if (worldIndex != currentWorld && distance > 60f) {
            currentWorld = worldIndex
            showBanner("أهلاً بعالم ${themes[worldIndex].name} 🌍")
        }
    }

    private fun showBanner(text: String) {
        bannerText = text
        bannerTime = 2.5f
    }

    private fun groundScreenY(): Float = height * 0.78f
    private fun trackY(track: Int): Float = groundScreenY() - 90f - track * 130f

    private fun spawnItems() {
        val rnd = Random()
        while (nextSpawnX < 1.4f) {
            val roll = rnd.nextFloat()
            val track = rnd.nextInt(3)
            val type = when {
                roll < 0.52f -> ItemType.STAR
                roll < 0.68f -> ItemType.BIG_STAR
                roll < 0.78f -> ItemType.DIAMOND
                else -> ItemType.SPIKE
            }
            if (type == ItemType.SPIKE && track == lastSpikeTrack) {
                nextSpawnX += 0.12f
                continue
            }
            items.add(Item(nextSpawnX, track, type))
            lastSpikeTrack = if (type == ItemType.SPIKE) track else -1
            nextSpawnX += 0.14f + rnd.nextFloat() * 0.12f
        }
    }

    private fun moveAndCollide(dt: Float) {
        val dx = speed * dt
        val playerCx = width * 0.22f
        val playerCy = groundScreenY() - 75f - playerY
        val it = items.iterator()
        while (it.hasNext()) {
            val item = it.next()
            item.x -= dx
            if (item.x < -0.25f) {
                it.remove()
                if (item.type == ItemType.STAR || item.type == ItemType.BIG_STAR) combo = 0
                continue
            }
            if (item.collected) continue

            var ix = item.x * width
            val iy = trackY(item.track) + sin((distance + item.phase * 100) * 0.06f) * 14f

            // المغناطيس يسحب النجوم نحو اللاعب
            if (magnetTime > 0 && item.type != ItemType.SPIKE) {
                val d = hypot((ix - playerCx).toDouble(), (iy - playerCy).toDouble())
                if (d < 300) {
                    item.x += (playerCx / width - item.x) * 0.12f
                    ix = item.x * width
                }
            }

            val hitR = if (item.type == ItemType.SPIKE) 60f else 85f
            val dist = hypot((ix - playerCx).toDouble(), (iy - playerCy).toDouble())
            if (dist < hitR) {
                when (item.type) {
                    ItemType.STAR -> collect(item, 10)
                    ItemType.BIG_STAR -> collect(item, 50)
                    ItemType.DIAMOND -> {
                        item.collected = true
                        combo++
                        burst(ix, iy, 0xFF4FC3F7.toInt())
                        if (Random().nextBoolean()) {
                            shieldTime = 8f
                            showBanner("🛡️ درع حماية فعال!")
                        } else {
                            magnetTime = 8f
                            showBanner("🧲 مغناطيس النجوم!")
                        }
                    }
                    ItemType.SPIKE -> {
                        item.collected = true
                        if (shieldTime > 0) {
                            shieldTime = 0f
                            burst(ix, iy, 0xFF4FC3F7.toInt())
                            showBanner("الدرع امتص الضربة! 💥")
                        } else {
                            endGame()
                            return
                        }
                    }
                }
            }
        }
    }

    private fun collect(item: Item, base: Int) {
        item.collected = true
        combo++
        val bonus = if (combo >= 5) 2 else 1
        score += (base * bonus).toLong()
        val color = when (item.type) {
            ItemType.BIG_STAR -> 0xFFFFD54F.toInt()
            else -> 0xFFFFF176.toInt()
        }
        burst(item.x * width, trackY(item.track), color)
    }

    private fun burst(x: Float, y: Float, color: Int) {
        repeat(12) {
            val a = Math.random() * 6.28
            val v = 120 + Math.random() * 260
            particles.add(
                Particle(
                    x, y,
                    (cos(a) * v).toFloat(), (sin(a) * v).toFloat(),
                    0.6f + Math.random().toFloat() * 0.4f,
                    color, 4f + Math.random().toFloat() * 6f
                )
            )
        }
    }

    private fun updateParticles(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += 700 * dt
            p.life -= dt
            if (p.life <= 0) it.remove()
        }
    }

    private fun endGame() {
        gameOver = true
        gameOverAt = System.currentTimeMillis()
        shakeTime = 0.5f
        if (score > bestScore) {
            bestScore = score
            saveBestScore(context, bestScore)
            showBanner("🏆 رقم قياسي جديد!")
        }
        vibrateDevice()
    }

    private fun vibrateDevice() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }

    // ═════════ الإدخال ═════════

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (gameOver) {
                if (System.currentTimeMillis() - gameOverAt > 900) restart()
                return true
            }
            doJump()
        } else if (event.action == MotionEvent.ACTION_UP) {
            if (velocityY > 600f) velocityY = 600f
        }
        return true
    }

    private fun doJump() {
        if (onGround) {
            onGround = false
            usedDoubleJump = false
            velocityY = 1250f
            burst(width * 0.22f, groundScreenY() - 20f, 0xFFFFFFFF.toInt())
        } else if (!usedDoubleJump) {
            usedDoubleJump = true
            velocityY = 1000f
            burst(width * 0.22f, groundScreenY() - 75f - playerY, 0xFFFFD54F.toInt())
        }
    }

    private fun restart() {
        playerY = 0f; velocityY = 0f; onGround = true; usedDoubleJump = false
        distance = 0f; speed = 0.18f; score = 0; combo = 0
        shieldTime = 0f; magnetTime = 0f; currentWorld = 0
        items.clear(); nextSpawnX = 0.7f; lastSpikeTrack = -1
        gameOver = false
        particles.clear()
    }

    // ═════════ الرسم ═════════

    private fun drawFrame() {
        val canvas: Canvas = try {
            holder.lockCanvas() ?: return
        } catch (_: Exception) {
            return
        }
        try {
            render(canvas)
        } finally {
            try {
                holder.unlockCanvasAndPost(canvas)
            } catch (_: Exception) {
            }
        }
    }

    private fun render(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val theme = themes[currentWorld]

        canvas.save()
        if (shakeTime > 0) {
            val s = shakeTime * 30
            canvas.translate(
                (Math.random() * s - s / 2).toFloat(),
                (Math.random() * s - s / 2).toFloat()
            )
        }

        paint.shader = LinearGradient(0f, 0f, 0f, h, theme.skyTop, theme.skyBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(-40f, -40f, w + 40f, h + 40f, paint)
        paint.shader = null

        drawStarsBackground(canvas, w, h)
        drawMountains(canvas, w, h, theme)
        drawGround(canvas, w, h, theme)

        for (item in items) if (!item.collected) drawItem(canvas, item)

        for (p in particles) {
            paint.color = p.color
            paint.alpha = (p.life * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, (p.size * p.life).coerceAtLeast(0.5f), paint)
        }
        paint.alpha = 255

        drawPlayer(canvas)
        drawHud(canvas, w, h, theme)
        if (gameOver) drawGameOver(canvas, w, h)

        canvas.restore()
    }

    private fun drawStarsBackground(canvas: Canvas, w: Float, h: Float) {
        val rnd = Random(42)
        paint.color = 0xAAFFFFFF.toInt()
        repeat(40) {
            val baseX = rnd.nextFloat() * w
            val parallax = 0.2f + rnd.nextFloat()
            val sy = rnd.nextFloat() * h * 0.6f
            val r = 1f + rnd.nextFloat() * 2.5f
            var sx = (baseX - distance * 2f * parallax) % w
            if (sx < 0) sx += w
            canvas.drawCircle(sx, sy, r, paint)
        }
    }

    private fun drawMountains(canvas: Canvas, w: Float, h: Float, theme: WorldTheme) {
        paint.color = darken(theme.skyBottom, 0.6f)
        val base = groundScreenY()
        val path = Path()
        path.moveTo(0f, base)
        val seg = w / 2f
        val off = (distance * 8f) % seg
        var x = -off - seg
        var up = true
        while (x < w + seg) {
            val peak = if (up) base - h * 0.16f else base - h * 0.09f
            path.lineTo(x + seg / 2, peak)
            path.lineTo(x + seg, base)
            x += seg
            up = !up
        }
        path.lineTo(w + 40, base); path.lineTo(w + 40, h + 40); path.lineTo(-40f, h + 40); path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawGround(canvas: Canvas, w: Float, h: Float, theme: WorldTheme) {
        val gy = groundScreenY()
        paint.color = theme.ground
        canvas.drawRect(0f, gy, w, h, paint)
        paint.color = theme.groundLine
        canvas.drawRect(0f, gy, w, gy + 14f, paint)
        paint.color = darken(theme.ground, 0.7f)
        val spacing = 140f
        val off = (distance * 6f) % spacing
        var x = -off
        while (x < w) {
            canvas.drawRect(x, gy + 34f, x + 50f, gy + 42f, paint)
            x += spacing
        }
    }

    private fun drawItem(canvas: Canvas, item: Item) {
        val x = item.x * width
        val y = trackY(item.track) + sin((distance + item.phase * 100) * 0.06f) * 14f
        when (item.type) {
            ItemType.STAR, ItemType.BIG_STAR -> {
                val size = if (item.type == ItemType.BIG_STAR) 46f else 28f
                paint.shader = RadialGradient(
                    x, y, size * 1.9f,
                    0x66FFEB3B, 0x00FFEB3B, Shader.TileMode.CLAMP
                )
                canvas.drawCircle(x, y, size * 1.9f, paint)
                paint.shader = null
                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(distance * (if (item.type == ItemType.BIG_STAR) 1.5f else 3f))
                canvas.scale(size, size)
                paint.color =
                    if (item.type == ItemType.BIG_STAR) 0xFFFFC107.toInt() else 0xFFFFEB3B.toInt()
                canvas.drawPath(starPath, paint)
                canvas.restore()
            }
            ItemType.DIAMOND -> {
                diamondDrawable?.let {
                    val s = 70
                    it.setBounds(
                        (x - s / 2).toInt(), (y - s / 2).toInt(),
                        (x + s / 2).toInt(), (y + s / 2).toInt()
                    )
                    it.draw(canvas)
                }
            }
            ItemType.SPIKE -> {
                spikePath.reset()
                spikePath.moveTo(x - 42f, y + 36f)
                spikePath.lineTo(x - 14f, y - 34f)
                spikePath.lineTo(x, y + 6f)
                spikePath.lineTo(x + 14f, y - 44f)
                spikePath.lineTo(x + 42f, y + 36f)
                spikePath.close()
                paint.color = 0xFFE53935.toInt()
                canvas.drawPath(spikePath, paint)
                paint.color = 0xFFB71C1C.toInt()
                canvas.drawRect(x - 46f, y + 32f, x + 46f, y + 44f, paint)
            }
        }
    }

    private fun drawPlayer(canvas: Canvas) {
        val px = width * 0.22f
        val py = groundScreenY() - 75f - playerY
        val lean = if (onGround) 0f else (velocityY / 1250f) * -10f

        // ظل
        paint.style = Paint.Style.FILL
        paint.color = 0x44000000
        val shadowScale = (1f - playerY / 900f).coerceIn(0.3f, 1f)
        canvas.drawOval(
            RectF(px - 55 * shadowScale, groundScreenY() - 10f,
                px + 55 * shadowScale, groundScreenY() + 14f), paint
        )

        canvas.save()
        canvas.translate(px, py)
        canvas.rotate(lean)

        // الدرع
        if (shieldTime > 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 7f
            paint.color = if (shieldTime < 2.5f && (shieldTime * 8).toInt() % 2 == 0)
                0x444FC3F7 else 0xCC4FC3F7.toInt()
            canvas.drawCircle(0f, 0f, 100f, paint)
            paint.style = Paint.Style.FILL
        }

        // لهب المحرك
        if (!onGround) {
            val flame = 40f + (Math.random() * 30).toFloat()
            paint.shader = LinearGradient(
                0f, 72f, 0f, 72f + flame,
                0xFFFFEB3B.toInt(), 0x00FF5722, Shader.TileMode.CLAMP
            )
            val fp = Path()
            fp.moveTo(-18f, 72f); fp.lineTo(18f, 72f); fp.lineTo(0f, 72f + flame); fp.close()
            canvas.drawPath(fp, paint)
            paint.shader = null
        }

        // الصاروخ البطل 🚀
        rocketDrawable?.let {
            val s = 150
            it.setBounds(-s / 2, -s / 2, s / 2, s / 2)
            it.draw(canvas)
        }

        canvas.restore()
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float, theme: WorldTheme) {
        textPaint.setShadowLayer(6f, 2f, 2f, 0x88000000)
        textPaint.textSize = 60f
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("$score", w / 2, 95f, textPaint)
        textPaint.textSize = 28f
        canvas.drawText("الأفضل: $bestScore", w / 2, 135f, textPaint)

        textPaint.textSize = 32f
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = theme.accent
        canvas.drawText("${theme.name} 🌍", 30f, 70f, textPaint)

        var iy = 120f
        textPaint.color = Color.WHITE
        if (shieldTime > 0) {
            canvas.drawText("🛡️ ${shieldTime.toInt() + 1}", 30f, iy, textPaint)
            iy += 45f
        }
        if (magnetTime > 0) {
            canvas.drawText("🧲 ${magnetTime.toInt() + 1}", 30f, iy, textPaint)
        }

        if (combo >= 5) {
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = 0xFFFFD54F.toInt()
            canvas.drawText("كومبو ×2 🔥", w - 30f, 70f, textPaint)
        }

        if (bannerTime > 0) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 46f
            textPaint.color = if ((bannerTime * 4).toInt() % 2 == 0) Color.WHITE else theme.accent
            canvas.drawText(bannerText, w / 2, h * 0.28f, textPaint)
        }
        textPaint.setShadowLayer(0f, 0f, 0f, 0)
    }

    private fun drawGameOver(canvas: Canvas, w: Float, h: Float) {
        paint.color = 0xB0000000.toInt()
        canvas.drawRect(-40f, -40f, w + 40f, h + 40f, paint)

        heartDrawable?.let {
            val s = 170
            it.setBounds(
                (w / 2 - s / 2).toInt(), (h * 0.26f).toInt(),
                (w / 2 + s / 2).toInt(), (h * 0.26f + s).toInt()
            )
            it.draw(canvas)
        }

        textPaint.setShadowLayer(6f, 2f, 2f, 0x88000000)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.WHITE
        textPaint.textSize = 68f
        canvas.drawText("انتهت الجولة!", w / 2, h * 0.52f, textPaint)
        textPaint.textSize = 42f
        canvas.drawText("نتيجتك: $score", w / 2, h * 0.59f, textPaint)
        canvas.drawText("الأفضل: $bestScore", w / 2, h * 0.65f, textPaint)
        if (System.currentTimeMillis() - gameOverAt > 900) {
            textPaint.color = 0xFFFFD54F.toInt()
            canvas.drawText("اضغط للعب مرة أخرى ▶", w / 2, h * 0.76f, textPaint)
        }
        textPaint.setShadowLayer(0f, 0f, 0f, 0)
    }

    // ═════════ دورة الحياة ═════════

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!running) {
            running = true
            gameThread = Thread(this).also { it.start() }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) = pause()

    fun pause() {
        running = false
        try { gameThread?.join() } catch (_: InterruptedException) {}
        gameThread = null
    }

    fun resume() {
        if (!running && holder.surface.isValid) {
            running = true
            gameThread = Thread(this).also { it.start() }
        }
    }

    companion object {
        private const val PREFS = "star_jump_prefs"
        private const val KEY_BEST = "best_score"

        fun loadBestScore(context: Context): Long =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_BEST, 0L)

        fun saveBestScore(context: Context, score: Long) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_BEST, score).apply()
        }

        private fun darken(color: Int, factor: Float): Int = Color.rgb(
            (Color.red(color) * factor).toInt(),
            (Color.green(color) * factor).toInt(),
            (Color.blue(color) * factor).toInt()
        )
    }
}
