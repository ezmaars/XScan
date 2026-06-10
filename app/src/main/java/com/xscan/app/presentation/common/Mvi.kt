package com.xscan.app.presentation.common

/**
 * Unidirectional data flow contract.
 *
 *   UI  ──(UiEvent)──▶  ViewModel  ──(StateFlow<UiState>)──▶  UI
 *                              └──(Flow<UiEffect>)──▶  one-shot side effects
 *
 * State is the single render input. Events are user intents. Effects are
 * fire-once signals (navigation, toasts) that must not survive recomposition.
 */
interface UiState
interface UiEvent
interface UiEffect
