package com.susnatacodes.digitaldrawingassist

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class CreativeGuideActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_creative_guide)

        val btnBack = findViewById<Button>(R.id.btnGuideBack)
        val views = listOf<View>(
            findViewById(R.id.guideEyebrow),
            findViewById(R.id.guideTitle),
            findViewById(R.id.guideSubtitle),
            findViewById(R.id.guideHero),
            findViewById(R.id.guideStepOne),
            findViewById(R.id.guideStepTwo),
            findViewById(R.id.guideStepThree),
            findViewById(R.id.guideStepFour),
            btnBack
        )

        UiEffects.applyPressAnimation(btnBack)
        UiEffects.playStaggeredEntrance(views, distance = 24f, delayStep = 45L)

        btnBack.setOnClickListener { finish() }
    }
}
