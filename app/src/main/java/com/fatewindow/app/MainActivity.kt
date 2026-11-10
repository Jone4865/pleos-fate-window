package com.fatewindow.app

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fatewindow.app.databinding.ActivityMainBinding
import com.fatewindow.app.game.GameController
import com.fatewindow.app.game.GameMode
import com.fatewindow.app.game.Mission
import com.fatewindow.app.game.Missions
import com.fatewindow.app.game.RevealTier
import com.fatewindow.app.game.RoundEvent
import com.fatewindow.app.game.RoundResult
import com.fatewindow.app.vehicle.PleosVehicleController
import com.fatewindow.app.vehicle.StubVehicleController
import com.fatewindow.app.vehicle.VehicleController
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private enum class Ui { SETUP, SPINNING, RESULT }

    private lateinit var binding: ActivityMainBinding
    private lateinit var vehicle: VehicleController
    private lateinit var controller: GameController

    private var mode = GameMode.LUCKY_DRAW
    private val enabledStakes = Missions.stakes.map { it.id }.toMutableSet()
    private val modeCards = mutableMapOf<GameMode, MaterialCardView>()
    private var lastRound: RoundResult? = null
    private var mission: Mission? = null
    private var ui = Ui.SETUP
    private var roundJob: Job? = null
    private var safetyJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pleos 차량 서비스가 있으면 실제 SDK, 없으면(일반 폰 등) 스텁
        val pleos = hasPleosVehicleService()
        vehicle = if (pleos) PleosVehicleController(this) else StubVehicleController()
        controller = GameController(vehicle)
        binding.connPill.text = if (pleos) "Pleos 차량 연결됨" else "데모 모드"

        buildModeCards()
        buildStakeChips()
        selectMode(GameMode.LUCKY_DRAW)

        binding.startButton.setOnClickListener { startRound() }
        binding.finishButton.setOnClickListener { finishRound() }
        binding.rerollButton.setOnClickListener { reroll() }
        binding.windowSwitch.setOnCheckedChangeListener { _, _ -> refreshVehicleState() }
        binding.ambientSwitch.setOnCheckedChangeListener { _, _ -> refreshVehicleState() }

        render(Ui.SETUP)
        // SDK 서비스 바인딩 시간을 조금 준 뒤, 준비 화면에 있는 동안 착좌·날씨·연출 티어를 주기적으로 갱신
        lifecycleScope.launch {
            delay(1200)
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    if (ui == Ui.SETUP) refreshVehicleState()
                    delay(2500)
                }
            }
        }
    }

    // ---------------- 준비 화면 구성 ----------------

    private fun buildModeCards() {
        val icons = mapOf(
            GameMode.LUCKY_DRAW to R.drawable.ic_mode_lucky,
            GameMode.TRUTH_OR_DARE to R.drawable.ic_mode_truth,
            GameMode.PENALTY to R.drawable.ic_mode_penalty,
            GameMode.SPOTLIGHT_TALK to R.drawable.ic_mode_talk,
        )
        GameMode.entries.forEachIndexed { i, m ->
            val card = MaterialCardView(this).apply {
                radius = dp(28f)
                cardElevation = 0f
                strokeWidth = dp(3f).toInt()
                isClickable = true
                isFocusable = true
                setOnClickListener { selectMode(m) }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(28f).toInt(), 0, dp(20f).toInt(), 0)
            }
            row.addView(ImageView(this).apply { setImageResource(icons.getValue(m)) },
                LinearLayout.LayoutParams(dp(88f).toInt(), dp(88f).toInt()))
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24f).toInt(), 0, 0, 0) }
            texts.addView(TextView(this).apply {
                text = m.title; textSize = 34f; setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.fw_text))
            })
            texts.addView(TextView(this).apply {
                text = m.desc; textSize = 22f
                setTextColor(ContextCompat.getColor(context, R.color.fw_text_dim))
            })
            row.addView(texts)
            card.addView(row)
            val lp = GridLayout.LayoutParams(GridLayout.spec(i / 2), GridLayout.spec(i % 2, 1f)).apply {
                width = 0
                height = dp(150f).toInt()
                setMargins(0, 0, if (i % 2 == 0) dp(18f).toInt() else 0, dp(18f).toInt())
            }
            binding.modeGrid.addView(card, lp)
            modeCards[m] = card
        }
    }

    private fun buildStakeChips() {
        Missions.stakes.forEach { s ->
            val chip = Chip(this).apply {
                text = s.label
                isCheckable = true
                isChecked = true
                isCheckedIconVisible = false
                textSize = 28f
                chipMinHeight = dp(80f)
                chipStartPadding = dp(22f)
                chipEndPadding = dp(22f)
                chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.chip_bg)
                setTextColor(ContextCompat.getColorStateList(context, R.color.chip_text))
                chipStrokeColor = ContextCompat.getColorStateList(context, R.color.chip_stroke)
                chipStrokeWidth = dp(2f)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) enabledStakes += s.id else enabledStakes -= s.id
                }
            }
            binding.stakeGroup.addView(chip)
        }
    }

    private fun selectMode(m: GameMode) {
        mode = m
        binding.messageText.isVisible = false   // 이전 라운드의 안내 문구는 새 선택 시 지움
        modeCards.forEach { (k, card) ->
            val on = k == m
            card.strokeColor = ContextCompat.getColor(this, if (on) R.color.fw_gold else R.color.fw_stroke)
            card.setCardBackgroundColor(ContextCompat.getColor(this, if (on) R.color.fw_surface_selected else R.color.fw_surface))
        }
        binding.optionsLabel.text = Missions.poolHint(m) + if (m == GameMode.LUCKY_DRAW) "  (여러 개 선택)" else ""
        binding.stakeGroup.isVisible = m == GameMode.LUCKY_DRAW
    }

    /** 착좌·날씨·옵션으로 이번 라운드 연출(티어) 미리보기 */
    private fun refreshVehicleState() {
        lifecycleScope.launch {
            binding.cabinView.setOccupied(vehicle.occupiedSeats())
            val caps = vehicle.readCapabilities()
            val tier = RevealTier.decide(caps, binding.windowSwitch.isChecked, binding.ambientSwitch.isChecked)
            val weather = when {
                caps.isRaining -> " · 비 감지"
                caps.isNight -> " · 야간"
                else -> ""
            }
            binding.tierPill.text = tier.label + weather
            binding.cabinView.setAmbientMode(tier == RevealTier.TIER1_AMBIENT)
        }
    }

    // ---------------- 라운드 ----------------

    private fun startRound() {
        render(Ui.SPINNING)
        binding.spinSeat.text = ""
        roundJob = lifecycleScope.launch {
            try {
                lastRound = controller.playRound(
                    windowOptionEnabled = binding.windowSwitch.isChecked,
                    ambientOptionEnabled = binding.ambientSwitch.isChecked
                ) { event -> onRoundEvent(event) }
                watchGearDuringResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: GameController.DrivingStartedException) {
                controller.emergencyReset()
                failRound(e.message)
            } catch (e: GameController.NotParkedException) {
                failRound(e.message)
            } catch (e: Exception) {
                controller.emergencyReset()
                failRound(e.message ?: "오류가 발생했어요")
            }
        }
    }

    private fun onRoundEvent(event: RoundEvent) {
        when (event) {
            is RoundEvent.Started -> {
                binding.cabinView.setOccupied(event.players)
                binding.cabinView.setAmbientMode(event.tier == RevealTier.TIER1_AMBIENT)
                binding.cabinView.blackout(true)
                binding.tierPill.text = event.tier.label
            }
            is RoundEvent.Spin -> {
                binding.cabinView.spotlight(event.seat)
                binding.spinSeat.text = event.seat.label
                binding.spinTitle.animate().cancel()
                binding.spinTitle.scaleX = 1.08f
                binding.spinTitle.scaleY = 1.08f
                binding.spinTitle.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            is RoundEvent.Blackout -> {
                binding.cabinView.spotlight(null)
                showBlackout(true)
            }
            is RoundEvent.Winner -> {
                showBlackout(false)
                binding.cabinView.reveal(event.seat, event.windowOpened)
                binding.winnerText.text = "${event.seat.label} 당첨!"
                binding.tierText.text = event.tier.label + if (event.windowOpened) " · 창문이 열렸어요" else ""
                showMission(Missions.pick(mode, enabledStakes))
                render(Ui.RESULT)
                lifecycleScope.launch { delay(1800); mission?.let { vehicle.speak(it.speech) } }
            }
        }
    }

    private fun showMission(m: Mission) {
        mission = m
        binding.missionTag.text = m.tag
        val box = binding.missionLines
        box.removeAllViews()
        m.lines.forEach { (key, value) ->
            if (key != null) {
                box.addView(TextView(this).apply {
                    text = key; textSize = 26f; setTypeface(typeface, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(context, R.color.fw_gold))
                    setPadding(0, dp(10f).toInt(), 0, 0)
                })
            }
            box.addView(TextView(this).apply {
                text = value
                textSize = if (m.lines.size > 1) 40f else 48f
                setTextColor(ContextCompat.getColor(context, R.color.fw_text))
                setLineSpacing(0f, 1.1f)
            })
        }
    }

    private fun reroll() {
        showMission(Missions.pickDifferent(mode, enabledStakes, mission))
        mission?.let { vehicle.speak(it.speech) }
    }

    /** 결과 화면(창문 열린 상태)에서 기어가 P 를 벗어나면 즉시 창문 닫고 조명 복귀 */
    private fun watchGearDuringResult() {
        safetyJob?.cancel()
        safetyJob = lifecycleScope.launch {
            while (isActive && ui == Ui.RESULT) {
                delay(800)
                if (ui == Ui.RESULT && !vehicle.isParked()) {
                    lastRound = null
                    controller.emergencyReset()
                    failRound(GameController.DrivingStartedException().message)
                    return@launch
                }
            }
        }
    }

    /** 미션 수행 후 "완료": 창문 닫고 조명 복귀 → 다음 라운드 */
    private fun finishRound() {
        safetyJob?.cancel()
        val round = lastRound ?: return render(Ui.SETUP)
        binding.finishButton.isEnabled = false
        binding.rerollButton.isEnabled = false
        lifecycleScope.launch {
            binding.cabinView.resetRound()
            controller.resetRound(round)
            lastRound = null
            binding.finishButton.isEnabled = true
            binding.rerollButton.isEnabled = true
            render(Ui.SETUP)
            refreshVehicleState()
        }
    }

    private fun failRound(msg: String?) {
        showBlackout(false)
        binding.cabinView.resetRound()
        render(Ui.SETUP)
        binding.messageText.text = msg
        binding.messageText.isVisible = true
    }

    // ---------------- 화면 상태 ----------------

    private fun render(ui: Ui) {
        this.ui = ui
        binding.setupPanel.isVisible = ui == Ui.SETUP
        binding.spinPanel.isVisible = ui == Ui.SPINNING
        binding.resultPanel.isVisible = ui == Ui.RESULT
        binding.startButton.isVisible = ui == Ui.SETUP
        binding.finishButton.isVisible = ui == Ui.RESULT
        binding.rerollButton.isVisible = ui == Ui.RESULT
        binding.settingsBox.children.forEach { it.isEnabled = ui == Ui.SETUP }
        binding.settingsBox.alpha = if (ui == Ui.SETUP) 1f else 0.4f
        if (ui != Ui.SETUP) binding.messageText.isVisible = false
    }

    private fun showBlackout(on: Boolean) {
        val v = binding.blackout
        v.animate().cancel()
        if (on) {
            v.visibility = View.VISIBLE
            v.animate().alpha(0.95f).setDuration(350).start()
        } else {
            v.animate().alpha(0f).setDuration(600).withEndAction { v.visibility = View.GONE }.start()
        }
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun hasPleosVehicleService(): Boolean =
        runCatching { packageManager.getPackageInfo(PLEOS_VEHICLE_SERVICE, 0) }.isSuccess

    /** 라운드 중 앱이 화면에서 사라지면(홈 버튼·다른 앱·종료) 창문이 열린 채 남지 않게 복구 */
    override fun onStop() {
        if (ui != Ui.SETUP) {
            roundJob?.cancel()
            safetyJob?.cancel()
            lastRound = null
            lifecycleScope.launch(NonCancellable) { controller.emergencyReset() }
            binding.blackout.animate().cancel()
            binding.blackout.alpha = 0f
            binding.blackout.visibility = View.GONE
            binding.cabinView.resetRound()
            render(Ui.SETUP)
        }
        super.onStop()
    }

    override fun onDestroy() {
        vehicle.release()
        super.onDestroy()
    }

    private companion object {
        const val PLEOS_VEHICLE_SERVICE = "ai.pleos.playground.service.vehicle"
    }
}
