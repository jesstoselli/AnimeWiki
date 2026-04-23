package com.example.animewiki

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.animewiki.ui.theme.AnimeWikiTheme
import com.example.animewiki.ui.top.TopAnimeScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnimeWikiTheme {
                TopAnimeScreen()
            }
        }
    }
}