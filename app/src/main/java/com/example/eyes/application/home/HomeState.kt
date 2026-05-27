package com.example.eyes.application.home

data class HomeActionState(
    val kind: HomeActionKind,
    val title: String,
    val description: String,
    val supportingLabel: String,
    val accessibilityLabel: String
)

data class HomeState(
    val welcomeTitle: String = "",
    val welcomeSummary: String = "",
    val actions: List<HomeActionState> = emptyList()
)
