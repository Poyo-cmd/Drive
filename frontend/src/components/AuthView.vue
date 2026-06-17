<script setup>
import { ref } from 'vue'
import { auth, session } from '../services/api.js'

const emit = defineEmits(['authed'])

const mode = ref('login') // 'login' | 'register'
const username = ref('')
const password = ref('')
const error = ref('')
const busy = ref(false)

async function submit() {
  error.value = ''
  if (!username.value.trim() || !password.value) {
    error.value = 'Completa usuario y contraseña'
    return
  }
  busy.value = true
  try {
    const fn = mode.value === 'login' ? auth.login : auth.register
    const res = await fn(username.value.trim(), password.value)
    session.set(res.token, res.username)
    emit('authed')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

function toggle() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  error.value = ''
}
</script>

<template>
  <div class="auth">
    <div class="card">
      <div class="brand">
        <span class="mark">☁</span>
        <h1>Drive</h1>
        <p>tu nube serena</p>
      </div>

      <h2>{{ mode === 'login' ? 'Iniciar sesión' : 'Crear cuenta' }}</h2>

      <label>Usuario</label>
      <input v-model="username" type="text" autocomplete="username" @keyup.enter="submit" />

      <label>Contraseña</label>
      <input v-model="password" type="password" autocomplete="current-password" @keyup.enter="submit" />

      <p v-if="error" class="error">{{ error }}</p>

      <button class="primary" :disabled="busy" @click="submit">
        {{ busy ? '…' : mode === 'login' ? 'Entrar' : 'Registrarme' }}
      </button>

      <p class="switch">
        {{ mode === 'login' ? '¿No tienes cuenta?' : '¿Ya tienes cuenta?' }}
        <button class="link" @click="toggle">
          {{ mode === 'login' ? 'Crear una' : 'Iniciar sesión' }}
        </button>
      </p>
    </div>
  </div>
</template>

<style scoped>
.auth {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
}

.card {
  width: 100%;
  max-width: 380px;
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: 20px;
  box-shadow: var(--shadow);
  padding: 36px 30px;
}

.brand {
  text-align: center;
  margin-bottom: 24px;
}

.mark {
  font-size: 28px;
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  margin: 0 auto 10px;
  background: var(--bg);
  border-radius: 16px;
}

.brand h1 {
  font-family: 'Fraunces', serif;
  font-weight: 600;
  font-size: 26px;
  line-height: 1;
}

.brand p {
  font-size: 12px;
  color: var(--ink-soft);
  font-style: italic;
}

h2 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 18px;
}

label {
  display: block;
  font-size: 13px;
  color: var(--ink-soft);
  margin: 12px 0 6px;
}

input {
  width: 100%;
  padding: 11px 13px;
  border: 1px solid var(--line);
  border-radius: 10px;
  font-family: inherit;
  font-size: 14px;
  background: var(--surface);
  color: var(--ink);
}

input:focus {
  outline: none;
  border-color: var(--accent);
}

.error {
  background: var(--accent-soft);
  color: #9c3a1e;
  padding: 9px 12px;
  border-radius: 9px;
  font-size: 13px;
  margin-top: 14px;
}

.primary {
  width: 100%;
  margin-top: 20px;
  padding: 12px;
  border-radius: 10px;
  background: var(--accent);
  color: #fff;
  font-weight: 600;
  font-size: 15px;
  box-shadow: 0 6px 18px rgba(232, 98, 61, 0.28);
}

.primary:disabled {
  opacity: 0.6;
}

.switch {
  margin-top: 18px;
  text-align: center;
  font-size: 13px;
  color: var(--ink-soft);
}

.link {
  color: var(--accent);
  font-weight: 600;
}

.link:hover {
  text-decoration: underline;
}
</style>
