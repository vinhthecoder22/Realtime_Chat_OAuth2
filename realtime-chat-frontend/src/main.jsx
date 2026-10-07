import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'

import './assets/css/base.css'
import './assets/css/auth.css'
import './assets/css/chat.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  // // Tạm thời tắt StrictMode nếu thấy WebSocket connect 2 lần ở môi trường Dev
  // <React.StrictMode>
    <App />
  // </React.StrictMode>,
)