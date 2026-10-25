package com.hortencioflix.tv.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.hortencioflix.tv.data.MetaItem

/** Abre um título vindo de fora da app (cartão «Continuar a ver» do ecrã inicial da TV). */
class OpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("id").orEmpty()
        if (id.isNotEmpty()) {
            val meta = MetaItem(
                id = id,
                type = intent.getStringExtra("type").orEmpty().ifEmpty { "movie" },
                name = intent.getStringExtra("name").orEmpty(),
                poster = intent.getStringExtra("poster").orEmpty(),
                background = intent.getStringExtra("bg").orEmpty()
            )
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            startActivity(Intent(this, DetailsActivity::class.java).putExtra("meta", meta))
        }
        finish()
    }
}
