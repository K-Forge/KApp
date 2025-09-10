<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="es">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
    <title>K-APP • Dashboard</title>
    <link rel="icon" href="images/konrad-logo.png" />
    <link rel="stylesheet" href="css/base.css" />
    <link rel="stylesheet" href="css/shell.css" />
    <link rel="stylesheet" href="css/layout.css" />
  </head>
  <body class="app-shell dashboard-page">
    <%@ include file="partials/header.jspf" %>

    <main class="app-content" role="main">
      <!-- Carrusel de novedades -->
      <section class="card section-gap">
        <h2 class="section-title">Novedades</h2>
        <div class="carousel">
          <div class="carousel-track" data-autoplay="5000">
            <article class="slide is-active">
              <img src="images/news-1.svg" alt="Novedad 1"/>
              <div class="slide-caption">
                <h3>Becas y apoyos</h3>
                <p>Consulta las nuevas becas disponibles este semestre.</p>
              </div>
            </article>
            <article class="slide">
              <img src="images/news-2.svg" alt="Novedad 2"/>
              <div class="slide-caption">
                <h3>Clubes estudiantiles</h3>
                <p>Únete a un club y descubre nuevas experiencias.</p>
              </div>
            </article>
            <article class="slide">
              <img src="images/news-3.svg" alt="Novedad 3"/>
              <div class="slide-caption">
                <h3>Semana K</h3>
                <p>Agenda las actividades de la Semana Konrad Lorenz.</p>
              </div>
            </article>
          </div>
          <div class="carousel-controls" aria-hidden="false">
            <button class="carousel-btn" id="prevBtn" aria-label="Anterior">
              <svg viewBox="0 0 24 24" fill="currentColor"><path d="M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z"/></svg>
            </button>
            <button class="carousel-btn" id="nextBtn" aria-label="Siguiente">
              <svg viewBox="0 0 24 24" fill="currentColor"><path d="m10 6-1.41 1.41L13.17 12l-4.58 4.59L10 18l6-6z"/></svg>
            </button>
          </div>
          <div class="carousel-dots" aria-label="Paginación del carrusel"></div>
        </div>
      </section>

      <!-- Grid de opciones -->
      <section class="section-gap">
        <h2 class="section-title">Servicios</h2>
        <div class="services-grid">
          <a href="#" class="service-card" data-action="novedades">
            <div class="icon-circle" style="--bg: var(--color-pink)">
              <!-- campana -->
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 22a2 2 0 0 0 2-2h-4a2 2 0 0 0 2 2m6-6V11a6 6 0 1 0-12 0v5l-2 2v1h16v-1z" fill="currentColor"/></svg>
            </div>
            <span>Novedades</span>
          </a>
          <a href="#" class="service-card" data-action="chat">
            <div class="icon-circle" style="--bg: var(--color-blue)">
              <!-- chat -->
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3a9 9 0 0 0-9 9c0 1.9.6 3.6 1.6 5.1L3 21l3.9-1.5A8.9 8.9 0 0 0 12 21a9 9 0 0 0 0-18z" fill="currentColor"/></svg>
            </div>
            <span>Chat</span>
          </a>
          <a href="#" class="service-card" data-action="cursos">
            <div class="icon-circle" style="--bg: var(--color-green)">
              <!-- libro -->
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12v-2H6V4h12v16h2V4a2 2 0 0 0-2-2z" fill="currentColor"/></svg>
            </div>
            <span>Cursos</span>
          </a>
          <a href="#" class="service-card" data-action="tareas">
            <div class="icon-circle" style="--bg: var(--color-mix-blue-pink)">
              <!-- checklist -->
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 5h14v2H7V5m0 4h14v2H7V9m0 4h14v2H7v-2M3 5h2v2H3V5m0 4h2v2H3V9m0 4h2v2H3v-2z" fill="currentColor"/></svg>
            </div>
            <span>Tareas</span>
          </a>
          <a href="#" class="service-card" data-action="clubes">
            <div class="icon-circle" style="--bg: var(--color-mix-green-blue)">
              <!-- grupo -->
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M16 11a4 4 0 1 0-8 0 4 4 0 0 0 8 0m-8 5a6 6 0 0 0-6 6h2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4h2a6 6 0 0 0-6-6H8z" fill="currentColor"/></svg>
            </div>
            <span>Clubes</span>
          </a>
          <a href="#" class="service-card" data-action="horario">
            <div class="icon-circle" style="--bg: var(--color-mix-all)">
              <!-- reloj -->
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 20a8 8 0 1 0 0-16 8 8 0 0 0 0 16m.5-13h-1v6l5.2 3.1.5-.9-4.7-2.7V7z" fill="currentColor"/></svg>
            </div>
            <span>Horario</span>
          </a>
        </div>
      </section>
    </main>

    <%@ include file="partials/footer.jspf" %>
    <script src="js/app.js"></script>
  </body>
</html>
