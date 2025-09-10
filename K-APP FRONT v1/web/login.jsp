<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="es">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
    <title>K-APP • Iniciar sesión</title>
    <link rel="icon" href="images/konrad-logo.png" />
    <link rel="stylesheet" href="css/base.css" />
    <link rel="stylesheet" href="css/shell.css" />
    <link rel="stylesheet" href="css/layout.css" />
  </head>
  <body class="login-page">
    <main class="login-container" role="main">
      <header class="login-header">
        <img class="login-logo" src="images/konrad-logo.png" alt="Logo Universidad Konrad Lorenz"/>
        <h1 class="login-title">K-APP</h1>
        <p class="login-subtitle">Portal estudiante</p>
      </header>

      <form class="login-form" action="dashboard.jsp" method="get" onsubmit="return window.__loginSubmit(event)">
        <label class="input-group">
          <span>Código estudiantil</span>
          <input type="text" inputmode="numeric" name="codigo" id="codigo" placeholder="Ej: 202512345" required />
        </label>
        <label class="input-group">
          <span>Contraseña</span>
          <input type="password" name="password" id="password" placeholder="••••••••" required />
        </label>
        <label class="checkbox">
          <input type="checkbox" id="remember" name="remember" />
          <span>Recordar sesión</span>
        </label>
        <button class="btn primary" type="submit">Ingresar</button>
      </form>

      <footer class="login-footer">
        <small>© <span id="year"></span> Fundación Universitaria Konrad Lorenz</small>
      </footer>
    </main>

    <script>
      // Minimal client-side login handling (demo)
      window.__loginSubmit = function (e) {
        e.preventDefault();
        const codigo = document.getElementById('codigo').value.trim();
        const remember = document.getElementById('remember').checked;
        if (!codigo) return false;
        const student = { codigo, nombre: 'Estudiante ' + codigo, avatar: 'images/avatar-placeholder.svg' };
        sessionStorage.setItem('student', JSON.stringify(student));
        if (remember) localStorage.setItem('student', JSON.stringify(student));
        window.location.href = 'dashboard.jsp';
        return false;
      };
      document.getElementById('year').textContent = new Date().getFullYear();
    </script>
  </body>
  </html>
