package io.livekit.android.example.voiceassistant

// TODO: Add your Token Server ID here
const val tokenServerId = ""

// NOTE: If you prefer not to use the token server for testing, you can generate your
// tokens manually by visiting https://cloud.livekit.io/projects/p_/settings/keys
// and using one of your API Keys to generate a token with custom TTL and permissions.
const val hardcodedUrl = "ws://qwen.ai.tdnrc.com:7880"
const val hardcodedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJkZXZrZXkiLCJzdWIiOiJhbmRyb2lkLXRlc3QtdXNlciIsImV4cCI6MTc5MTMzMTYyMSwibmJmIjoxNzkxMjQ1MjIxLCJpYXQiOjE3OTEyNDUyMjEsImlkZW50aXR5IjoiYW5kcm9pZC10ZXN0LXVzZXIiLCJuYW1lIjoiYW5kcm9pZC10ZXN0LXVzZXIiLCJ2aWRlbyI6eyJyb29tSm9pbiI6dHJ1ZSwicm9vbSI6ImFuZHJvaWQtdGVzdC1yb29tIn19.hGc4Nl7lPEEWGRweRb24DxiDqEkqIqgSFhrhBixOlIo"

// Fallback: if other connection details are empty, connect to the homepage agent.
const val homepageAgentEndpoint = "https://livekit.com/api/homepage-agent/token"
