package com.fatewindow.app

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fatewindow.app.databinding.ActivityMainBinding
import com.fatewindow.app.game.GameController
import com.fatewindow.app.game.GameMode
import com.fatewindow.app.game.RoundEvent
import com.fatewindow.app.vehicle.StubVehicleController
import com.fatewindow.app.vehicle.VehicleController
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // 실제 제출 시 StubVehicleController → PleosVehicleController(this) 로 교체
    private val vehicle: VehicleController = StubVehicleController()
    private val controller by lazy { GameController(vehicle) }

    private val missions = mapOf(
        GameMode.LUCKY_DRAW to "다음 간식은 당신이 쏘기 🍫",
        GameMode.TRUTH_OR_DARE to "진실: 최근 가장 창피했던 일은?",
        GameMode.PENALTY to "벌칙: 10초간 성대모사 🎤"
    )
    private var mode = GameMode.LUCKY_DRAW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.modeSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            GameMode.values().map { it.title }
        )

        binding.startButton.setOnClickListener { startRound() }
    }

    private fun startRound() {
        mode = GameMode.values()[binding.modeSpinner.selectedItemPosition]
        binding.startButton.isEnabled = false
        binding.resultText.text = "두구두구…"

        lifecycleScope.launch {
            try {
                controller.playRound(
                    windowOptionEnabled = binding.windowSwitch.isChecked,
                    excludeDriver = binding.excludeDriverSwitch.isChecked
                ) { event ->
                    when (event) {
                        is RoundEvent.Spin -> binding.statusText.text = "◐ ${event.seat.label}"
                        is RoundEvent.Blackout -> binding.statusText.text = "운명은…"
                        is RoundEvent.Winner -> {
                            binding.statusText.text = "🎉 ${event.seat.label} 당첨!"
                            binding.resultText.text = missions[mode]
                        }
                    }
                }
            } catch (e: GameController.NotParkedException) {
                binding.resultText.text = e.message
            } catch (e: Exception) {
                binding.resultText.text = e.message ?: "오류"
            } finally {
                binding.startButton.isEnabled = true
            }
        }
    }
}
