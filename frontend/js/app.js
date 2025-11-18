(function(){
  // Auth gate: redirect to login if not authenticated on app pages
  try {
    const hasShell = document.body && document.body.classList.contains('app-shell');
    if (hasShell) {
      const saved = localStorage.getItem('student') || sessionStorage.getItem('student');
      if (!saved) {
        window.location.replace('login.html');
        return;
      }
    }
  } catch (_) { /* noop */ }

  // Header menu (placeholder)
  const btnMenu = document.getElementById('btnMenu');
  if (btnMenu) {
    const drawer = document.getElementById('drawer');
    const overlay = document.getElementById('drawerOverlay');
    const setOpen = (open) => {
      btnMenu.setAttribute('aria-expanded', String(open));
      drawer?.classList.toggle('is-open', open);
      overlay?.classList.toggle('is-open', open);
      if (overlay) overlay.hidden = !open;
    };
    btnMenu.addEventListener('click', () => setOpen(!(drawer?.classList.contains('is-open'))));
    overlay?.addEventListener('click', () => setOpen(false));
    document.addEventListener('keydown', (ev) => { if (ev.key === 'Escape') setOpen(false); });
  }

  // Hydrate header with student info from storage
  try {
    const saved = localStorage.getItem('student') || sessionStorage.getItem('student');
    if (saved) {
      const s = JSON.parse(saved);
      const elName = document.getElementById('studentName');
      const elCode = document.getElementById('studentCode');
      const elAvatar = document.getElementById('studentAvatar');
      elName && (elName.textContent = s.nombre || 'Estudiante');
      elCode && (elCode.textContent = s.codigo || '—');
      if (elAvatar && s.avatar) elAvatar.src = s.avatar;

  // Drawer hydration
  const dName = document.getElementById('drawerName');
  const dCode = document.getElementById('drawerCode');
  const dAvatar = document.getElementById('drawerAvatar');
  dName && (dName.textContent = s.nombre || 'Estudiante');
  dCode && (dCode.textContent = s.codigo || '—');
  if (dAvatar && s.avatar) dAvatar.src = s.avatar;
    }
  } catch (e) { /* noop */ }

  // Carousel (arrows + drag + consistent direction)
  const track = document.querySelector('.carousel .carousel-track');
  if (track) {
    const slides = Array.from(track.querySelectorAll('.slide'));
    const dotsWrap = document.querySelector('.carousel .carousel-dots');
    const prevBtn = document.getElementById('prevBtn');
    const nextBtn = document.getElementById('nextBtn');
    let index = slides.findIndex(s => s.classList.contains('is-active'));
    if (index < 0) index = 0;

    // Build dots
    slides.forEach((_, i) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.setAttribute('aria-label', 'Ir al slide ' + (i+1));
      b.addEventListener('click', () => go(i));
      dotsWrap && dotsWrap.appendChild(b);
    });
    const dots = dotsWrap ? Array.from(dotsWrap.children) : [];

    function go(i){
      // normalize
      if (i < 0) i = slides.length - 1;
      if (i >= slides.length) i = 0;
      // update classes
      slides[index]?.classList.remove('is-active');
      slides[i]?.classList.add('is-active');
      // dots
      if (dots.length) {
        dots[index]?.removeAttribute('aria-current');
        dots[i]?.setAttribute('aria-current', 'true');
      }
      index = i;
    }
    function next(){ go(index + 1); }
    function prev(){ go(index - 1); }

    // Buttons
    nextBtn?.addEventListener('click', next);
    prevBtn?.addEventListener('click', prev);

    // Drag/Swipe
    let startX = 0;
    let isDragging = false;
    let delay = parseInt(track.dataset.autoplay || '5000', 10);
    let timer = setInterval(next, delay);

    const pause = () => { clearInterval(timer); };
    const resume = () => { timer = setInterval(next, delay); };

    track.addEventListener('mouseenter', pause);
    track.addEventListener('mouseleave', resume);

    track.addEventListener('touchstart', (e) => { startX = e.touches[0].clientX; isDragging = true; pause(); }, { passive: true });
    track.addEventListener('touchend', (e) => {
      if (!isDragging) return; isDragging = false;
      const diff = startX - e.changedTouches[0].clientX;
      if (Math.abs(diff) > 40) { diff > 0 ? next() : prev(); }
      resume();
    }, { passive: true });

    track.addEventListener('mousedown', (e) => { startX = e.clientX; isDragging = true; pause(); e.preventDefault(); });
    document.addEventListener('mouseup', (e) => {
      if (!isDragging) return; isDragging = false;
      const diff = startX - e.clientX;
      if (Math.abs(diff) > 40) { diff > 0 ? next() : prev(); }
      resume();
    });

    // init
    go(index);
  }

  // Services handlers (placeholder routing)
  document.querySelectorAll('.service-card').forEach(card => {
    card.addEventListener('click', (e) => {
      e.preventDefault();
      const action = card.getAttribute('data-action');
      alert('Abrir sección: ' + action);
    });
  });

  // Footer nav handlers
  document.querySelectorAll('.footer-nav .nav-item[data-action]').forEach(item => {
    item.addEventListener('click', (e) => {
      e.preventDefault();
      const action = item.getAttribute('data-action');
      alert('Abrir sección: ' + action);
    });
  });

  // Logout
  const logoutBtn = document.getElementById('logoutBtn');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', () => {
      localStorage.removeItem('student');
      sessionStorage.removeItem('student');
      window.location.replace('login.html');
    });
  }
})();
