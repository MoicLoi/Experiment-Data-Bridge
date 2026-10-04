import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './i18n'
import './styles.css'
import App from './App'
import { AuthProvider } from './auth/AuthProvider'
import { ThemeProvider } from './theme/ThemeProvider'
import { initializeDebugLogging } from './lib/debug'

void initializeDebugLogging()

createRoot(document.getElementById('root')!).render(
  <StrictMode><ThemeProvider><BrowserRouter><AuthProvider><App /></AuthProvider></BrowserRouter></ThemeProvider></StrictMode>,
)
