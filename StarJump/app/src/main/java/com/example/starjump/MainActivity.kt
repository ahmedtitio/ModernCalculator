package com.example.starjump

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * الشاشة الرئيسية — صورة البرنامج، أعلى نتيجة، وأزرار اللعب.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.btn_play).setOnClickListener {
            startActivity(Intent(this, GameActivity::class.java))
        }
        findViewById<View>(R.id.btn_how).setOnClickListener {
            startActivity(HowToPlayActivity.newIntent(this))
        }
    }

    override fun onResume() {
        super.onResume()
        val best = GameView.loadBestScore(this)
        findViewById<TextView>(R.id.tv_best).text = getString(R.string.best_score, best)
    }
}

/**
 * شاشة "كيف ألعب"
 */
class HowToPlayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_how_to_play)
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, HowToPlayActivity::class.java)
    }
}
