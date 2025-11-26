(function(){
  const API_BASE = 'http://localhost:8080/api';
  let currentUser = null;

  // --- 1. Auth & Session Management ---
  function getSession() {
    try {
      return JSON.parse(localStorage.getItem('user_session') || sessionStorage.getItem('user_session'));
    } catch (e) { return null; }
  }

  // Toast Notification Helper
  function showToast(message, type = 'success') {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    
    let icon = '';
    if (type === 'success') icon = '<svg class="toast-icon" viewBox="0 0 24 24" fill="currentColor"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>';
    else if (type === 'error') icon = '<svg class="toast-icon" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>';

    toast.innerHTML = `${icon}<span>${message}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
      toast.style.animation = 'fadeOut 0.3s ease-out forwards';
      setTimeout(() => toast.remove(), 300);
    }, 3000);
  }

  // Confirmation Modal Helper
  function showConfirm(message) {
    return new Promise((resolve) => {
      const modal = document.getElementById('confirmModal');
      const msgEl = document.getElementById('confirmMessage');
      const btnCancel = document.getElementById('btnCancelConfirm');
      const btnAccept = document.getElementById('btnAcceptConfirm');

      if (!modal) {
          // Fallback if modal doesn't exist
          resolve(confirm(message));
          return;
      }

      msgEl.textContent = message;
      
      const close = (result) => {
        modal.close();
        resolve(result);
        cleanup();
      };

      const onCancel = () => close(false);
      const onAccept = () => close(true);

      const cleanup = () => {
        btnCancel.removeEventListener('click', onCancel);
        btnAccept.removeEventListener('click', onAccept);
      };

      btnCancel.addEventListener('click', onCancel);
      btnAccept.addEventListener('click', onAccept);
      
      modal.showModal();
    });
  }

  // Global View Switcher (Moved from HTML)
  window.switchView = function(viewId) {
    // Ocultar todas las vistas
    document.querySelectorAll('.view-section').forEach(el => {
      el.classList.remove('active');
    });
    
    // Mostrar la vista seleccionada
    const target = document.getElementById(viewId);
    if (target) {
      target.classList.add('active');
    }

    // Manejar visibilidad del botón Home
    const btnHome = document.getElementById('btnHome');
    if (btnHome) {
        if (viewId === 'view-dashboard') {
        btnHome.classList.add('home-btn-hidden');
        } else {
        btnHome.classList.remove('home-btn-hidden');
        }
    }
    
    // Scroll al inicio
    window.scrollTo(0, 0);
  };

  // Global Task Filter (Moved from HTML)
  window.filterTasks = function(status) {
    let currentStatus = status || 'pending';

    // Update buttons
    document.querySelectorAll('.segmented-btn').forEach(btn => {
      btn.classList.remove('active');
      if (btn.textContent.toLowerCase().includes(currentStatus === 'pending' ? 'pendientes' : 'entregadas')) {
        btn.classList.add('active');
      }
    });

    // Get selected course
    const courseFilter = document.getElementById('courseFilter');
    const selectedCourse = courseFilter ? courseFilter.value : 'all';

    // Filter items
    const items = document.querySelectorAll('.list-item');
    items.forEach(item => {
      const itemStatus = item.dataset.status;
      const itemCourse = item.querySelector('p') ? item.querySelector('p').textContent.trim() : '';
      
      const statusMatch = itemStatus === currentStatus;
      const courseMatch = selectedCourse === 'all' || itemCourse === selectedCourse;

      if (statusMatch && courseMatch) {
        item.style.display = 'grid';
      } else {
        item.style.display = 'none';
      }
    });
  };

  function logout() {
    localStorage.removeItem('user_session');
    sessionStorage.removeItem('user_session');
    window.location.replace('login.html');
  }

  // Auth Guard
  const hasShell = document.body && document.body.classList.contains('app-shell');
  if (hasShell) {
    currentUser = getSession();
    if (!currentUser || !currentUser.token) {
      window.location.replace('login.html');
      return;
    }
  }

  // API Helper
  async function fetchApi(endpoint, options = {}) {
    const headers = {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${currentUser.token}`,
      ...options.headers
    };
    
    try {
      const response = await fetch(`${API_BASE}${endpoint}`, { ...options, headers });
      if (response.status === 401 || response.status === 403) {
        logout();
        return null;
      }
      return response;
    } catch (err) {
      console.error("API Error:", err);
      return null;
    }
  }

  // --- 2. UI Hydration & Role Setup ---
  if (currentUser) {
    // Header Info
    const updateUserInfo = (idPrefix) => {
      const elName = document.getElementById(idPrefix + 'Name');
      const elCode = document.getElementById(idPrefix + 'Code');
      if (elName) elName.textContent = currentUser.username;
      if (elCode) elCode.textContent = currentUser.role.replace('ROLE_', '');
    };
    updateUserInfo('student'); // Header
    updateUserInfo('drawer');  // Drawer

    // Role Logic
    const role = currentUser.role;
    
    if (role === 'ROLE_ADMIN') {
      setupAdminDashboard();
    } else if (role === 'ROLE_PROFESSOR') {
      setupProfessorDashboard();
    } else {
      setupStudentDashboard();
    }
  }

  // --- 3. Role Specific Functions ---

  async function setupStudentDashboard() {
    // UI Adjustments for Student
    const filters = document.querySelector('#view-tasks .segmented-control');
    if(filters) filters.style.display = 'flex';

    const actions = document.getElementById('prof-tasks-actions');
    if(actions) actions.style.display = 'none';

    const title = document.getElementById('tasks-view-title');
    if(title) title.textContent = 'Tareas Pendientes';

    // Load Courses
    const coursesRes = await fetchApi('/student/courses');
    if (coursesRes && coursesRes.ok) {
      const courses = await coursesRes.json();
      renderCourses(courses, 'courses-container');
    }

    // Load Tasks
    loadStudentTasks();
  }

  async function loadStudentTasks() {
      const container = document.getElementById('tasks-container');
      if(!container) return;
      container.innerHTML = ''; // Clear once

      let hasTasks = false;

      // Pending
      const pendingRes = await fetchApi('/student/assignments/pending');
      if (pendingRes && pendingRes.ok) {
          const pending = await pendingRes.json();
          if(pending.length > 0) {
              renderTasks(pending, 'tasks-container', 'pending');
              hasTasks = true;
          }
      }

      // Submitted
      const submittedRes = await fetchApi('/student/assignments/submitted');
      if (submittedRes && submittedRes.ok) {
          const submitted = await submittedRes.json();
          if(submitted.length > 0) {
              renderTasks(submitted, 'tasks-container', 'submitted');
              hasTasks = true;
          }
      }

      if (!hasTasks) {
          container.innerHTML = '<div class="list-item">No tienes tareas.</div>';
      }
      
      // Re-apply filter
      if(window.filterTasks) window.filterTasks('pending');
  }

  async function setupProfessorDashboard() {
    // 1. Stay on Dashboard (don't switch view immediately)
    if (window.switchView) window.switchView('view-dashboard');

    // 2. Override Home button
    const btnHome = document.getElementById('btnHome');
    if(btnHome) btnHome.onclick = () => switchView('view-dashboard');

    // 3. Override "Courses" button on Dashboard
    const coursesBtn = document.querySelector('a[onclick*="view-courses"]');
    if (coursesBtn) {
        coursesBtn.onclick = (e) => {
            e.preventDefault();
            switchView('view-professor');
        };
    }

    // 4. Override "Tasks" button on Dashboard
    const tasksBtn = document.querySelector('a[onclick*="view-tasks"]');
    if (tasksBtn) {
        tasksBtn.onclick = (e) => {
            e.preventDefault();
            loadProfessorTasks();
            switchView('view-tasks');
        };
    }

    // Load Professor Courses (into view-professor)
    const coursesRes = await fetchApi('/professor/courses');
    if (coursesRes && coursesRes.ok) {
      const courses = await coursesRes.json();
      renderProfessorCourses(courses, 'prof-courses-container');
    }
  }

  async function loadProfessorTasks() {
      const container = document.getElementById('tasks-container');
      if(!container) return;
      container.innerHTML = '<div class="list-item">Cargando tareas...</div>';
      
      // UI Adjustments for Professor
      const filters = document.querySelector('#view-tasks .segmented-control');
      if(filters) filters.style.display = 'none';
      
      const actions = document.getElementById('prof-tasks-actions');
      if(actions) actions.style.display = 'flex';

      const title = document.getElementById('tasks-view-title');
      if(title) title.textContent = 'Mis Tareas';

      const res = await fetchApi('/professor/assignments');
      if (res && res.ok) {
          const tasks = await res.json();
          if (tasks.length === 0) {
              container.innerHTML = '<div class="list-item">No has creado tareas.</div>';
              return;
          }
          
          container.innerHTML = tasks.map(t => `
            <div class="list-item">
                <div class="list-item-content">
                    <span class="badge date" style="margin-bottom: 4px;">${t.dueDate ? new Date(t.dueDate).toLocaleDateString() : 'Sin fecha'}</span>
                    <h3>${t.title}</h3>
                    <p>${t.description || ''}</p>
                    <small>Puntos: ${t.maxScore}</small>
                </div>
                <div class="list-item-action">
                    <button class="btn small secondary" onclick="openProfessorEditTaskModal(${t.id})">Editar</button>
                    <button class="btn small danger" onclick="deleteProfessorTask(${t.id})">Eliminar</button>
                </div>
            </div>
          `).join('');
      } else {
          container.innerHTML = '<div class="list-item">Error cargando tareas.</div>';
      }
  }

  async function setupAdminDashboard() {
    // Show Admin Button
    const btnAdmin = document.getElementById('btnAdminPanel');
    if (btnAdmin) btnAdmin.style.display = 'flex';

    // Override Home button
    const btnHome = document.getElementById('btnHome');
    if(btnHome) btnHome.onclick = () => switchView('view-admin');

    // Switch to view
    if (window.switchView) window.switchView('view-admin');
    
    // Load initial data
    loadAdminUsers();
  }

  // --- 4. Render Helpers ---

  function renderCourses(courses, containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;
    
    if (courses.length === 0) {
      container.innerHTML = '<p>No tienes cursos inscritos.</p>';
      return;
    }

    container.innerHTML = courses.map(c => `
      <a href="#" class="course-card" onclick="openStudentCourseTasks(${c.courseGroupId}, '${c.courseName}'); return false;">
        <div class="course-header" style="--course-color: #539392;">
          <svg viewBox="0 0 24 24" fill="currentColor" width="80" height="80"><path d="M18 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM6 4h5v8l-2.5-1.5L6 12V4z"/></svg>
        </div>
        <div class="course-body">
          <h3 class="course-title">${c.courseName}</h3>
          <p class="course-prof">${c.professorName || 'Sin profesor'}</p>
          <small>${c.groupCode}</small>
          <button class="btn small primary" style="margin-top: auto;">Ver Tareas</button>
        </div>
      </a>
    `).join('');
  }

  function renderTasks(tasks, containerId, status = 'pending') {
    const container = document.getElementById(containerId);
    if (!container) return;

    // If clearing container (first load), do it outside or handle appending.
    // Here we assume we might call this multiple times (for pending and submitted).
    // So we won't clear if we are appending, but usually we clear before loading all.
    // Let's assume the caller clears the container.

    if (tasks.length === 0) return; // Don't render anything if empty, caller handles "no tasks" message if both are empty

    const html = tasks.map(t => `
      <div class="list-item" data-status="${status}">
        <div class="list-item-content">
          <span class="badge date" style="margin-bottom: 4px;">${t.dueDate ? new Date(t.dueDate).toLocaleDateString() : 'Sin fecha'}</span>
          <h3>${t.title}</h3>
          <p>${t.description || ''}</p>
        </div>
        <div class="list-item-action">
          ${status === 'pending' 
            ? `<button class="btn small primary" onclick="openSubmitTaskModal(${t.id}, '${t.title}')">Entregar</button>` 
            : `<span class="badge submitted">Entregada</span>`
          }
        </div>
      </div>
    `).join('');
    
    container.insertAdjacentHTML('beforeend', html);
  }

  function renderProfessorCourses(courses, containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;
    
    if (courses.length === 0) {
      container.innerHTML = '<p>No tienes cursos asignados.</p>';
      return;
    }

    container.innerHTML = courses.map(c => `
      <div class="course-card">
        <div class="course-header" style="--course-color: #D51A65;">
          <svg viewBox="0 0 24 24" fill="currentColor" width="80" height="80"><path d="M12 3L1 9l4 2.18v6L12 21l7-3.82v-6l2-1.09V17h2V9L12 3z"/></svg>
        </div>
        <div class="course-body">
          <h3 class="course-title">${c.courseName}</h3>
          <p class="course-prof">Grupo: ${c.groupCode}</p>
          <div style="margin-top: 10px; display: flex; gap: 8px; flex-wrap: wrap;">
            <button class="btn small primary" onclick="openProfessorStudentsModal(${c.courseGroupId})">Estudiantes</button>
            <button class="btn small secondary" onclick="openProfessorCreateTaskModal(${c.courseGroupId})">Nueva Tarea</button>
          </div>
        </div>
      </div>
    `).join('');
  }

  // --- 5. Admin Functions (Global for onclick access) ---
  
  window.showAdminTab = (tabName) => {
    document.querySelectorAll('.admin-tab').forEach(el => el.style.display = 'none');
    const target = document.getElementById(`admin-tab-${tabName}`);
    if(target) target.style.display = 'block';
    
    // Update active button state
    const btns = document.querySelectorAll('.segmented-control .segmented-btn');
    btns.forEach(btn => btn.classList.remove('active'));
    if (event && event.target) event.target.classList.add('active');

    if (tabName === 'users') loadAdminUsers();
    if (tabName === 'courses') loadAdminCourses();
    if (tabName === 'tasks') loadAdminTasks();
  };

  window.filterAdminUsers = (type, btn) => {
    if (btn) {
        const container = btn.parentElement;
        container.querySelectorAll('button').forEach(b => {
            b.classList.remove('primary');
            b.classList.add('secondary');
        });
        btn.classList.remove('secondary');
        btn.classList.add('primary');
    }
    loadAdminUsers(type);
  };

  async function loadAdminUsers(type = 'all') {
    const container = document.getElementById('admin-users-list');
    if(!container) return;
    container.innerHTML = '<div class="list-item">Cargando...</div>';
    
    let endpoint = '/admin/members'; // Default to members to get person info
    if (type === 'student') endpoint = '/admin/students';
    if (type === 'professor') endpoint = '/admin/employees'; 
    
    const res = await fetchApi(endpoint);
    if (res && res.ok) {
      let data = await res.json();
      
      // Client-side filtering to ensure clean lists
      if (type === 'professor') {
          // Filter out Admins or non-professors if they appear in employees list
          data = data.filter(item => item.employeeRole === 'PROFESOR_PLANTA' || item.employeeRole === 'CATEDRA');
      }
      if (type === 'student') {
          // Ensure we only show items with studentCode (though endpoint should guarantee this)
          data = data.filter(item => item.studentCode);
      }

      container.innerHTML = data.map(item => {
        let name = 'Usuario';
        let sub = '';
        let id = item.id;
        
        // Handle different entity structures
        if (item.firstName) { // Person (should not happen with /members default)
            name = `${item.firstName} ${item.lastName}`;
            sub = item.identificationNumber || '';
        } else if (item.person) { // Member
            name = `${item.person.firstName} ${item.person.lastName}`;
            sub = item.universityCode || '';
            id = item.id;
        } else if (item.member && item.member.person) { // Student/Employee
            name = `${item.member.person.firstName} ${item.member.person.lastName}`;
            sub = item.code || item.employeeCode || item.studentCode || '';
            id = item.id;
        }

        return `
        <div class="list-item">
          <div class="list-item-content">
            <h3>${name}</h3>
            <p>${sub}</p>
          </div>
          <div class="list-item-action">
            <button class="btn small secondary" onclick="openEditUserModal(${id}, '${type}')">Editar</button>
            <button class="btn small danger" onclick="deleteUser(${id}, '${type}')">Eliminar</button>
          </div>
        </div>`;
      }).join('');
    } else {
      container.innerHTML = '<div class="list-item">Error cargando datos.</div>';
    }
  }

  async function loadAdminCourses() {
    const container = document.getElementById('admin-courses-list');
    if(!container) return;
    container.innerHTML = '<div class="list-item">Cargando...</div>';
    
    const res = await fetchApi('/admin/courses');
    if (res && res.ok) {
      const data = await res.json();
      container.innerHTML = data.map(c => `
        <div class="list-item">
          <div class="list-item-content">
            <h3>${c.name}</h3>
            <p>Código: ${c.code} | Créditos: ${c.credits}</p>
          </div>
          <div class="list-item-action">
            <button class="btn small secondary" onclick="openEditCourseModal(${c.id})">Editar</button>
            <button class="btn small danger" onclick="deleteCourse(${c.id})">Eliminar</button>
          </div>
        </div>
      `).join('');
    }
  }

  async function loadAdminTasks() {
    const container = document.getElementById('admin-tasks-list');
    if(!container) return;
    container.innerHTML = '<div class="list-item">Cargando...</div>';
    
    const res = await fetchApi('/admin/assignments');
    if (res && res.ok) {
      const data = await res.json();
      container.innerHTML = data.map(t => `
        <div class="list-item">
          <div class="list-item-content">
            <h3>${t.title}</h3>
            <p>${t.description || ''}</p>
          </div>
          <div class="list-item-action">
            <button class="btn small secondary" onclick="openEditTaskModal(${t.id})">Editar</button>
            <button class="btn small danger" onclick="deleteTask(${t.id})">Eliminar</button>
          </div>
        </div>
      `).join('');
    }
  }

  // --- 6. Existing UI Interactions (Drawer, Carousel) ---
  
  // Header menu
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

  // Logout
  const logoutBtn = document.getElementById('logoutBtn');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', logout);
  }

  // Carousel (Simplified for brevity, keeping core logic)
  const track = document.querySelector('.carousel .carousel-track');
  if (track) {
    const slides = Array.from(track.querySelectorAll('.slide'));
    const prevBtn = document.getElementById('prevBtn');
    const nextBtn = document.getElementById('nextBtn');
    let index = 0;

    function go(i){
      if (i < 0) i = slides.length - 1;
      if (i >= slides.length) i = 0;
      slides.forEach(s => s.classList.remove('is-active'));
      slides[i]?.classList.add('is-active');
      index = i;
    }
    if(nextBtn) nextBtn.addEventListener('click', () => go(index + 1));
    if(prevBtn) prevBtn.addEventListener('click', () => go(index - 1));
  }

  // --- 7. CRUD Modals ---

  window.openCreateUserModal = () => {
    const modal = document.getElementById('crudModal');
    const title = document.getElementById('modalTitle');
    const fields = document.getElementById('modalFields');
    const form = document.getElementById('crudForm');
    
    title.textContent = 'Crear Nuevo Usuario';
    form.dataset.type = 'user';
    form.dataset.mode = 'create';
    
    fields.innerHTML = `
      <div class="input-group">
        <label>Tipo de Usuario</label>
        <select name="userType" required>
          <option value="student">Estudiante</option>
          <option value="professor">Profesor</option>
        </select>
      </div>
      <div class="input-group"><label>Nombre</label><input type="text" name="firstName" required></div>
      <div class="input-group"><label>Apellido</label><input type="text" name="lastName" required></div>
      <div class="input-group"><label>Documento</label><input type="text" name="identificationNumber" required></div>
      <div class="input-group"><label>Email Institucional</label><input type="email" name="email" required></div>
      <div class="input-group"><label>Contraseña</label><input type="password" name="password" required></div>
      <div class="input-group"><label>Código (Estudiante/Empleado)</label><input type="text" name="code" required></div>
    `;
    
    modal.showModal();
  };

  window.openEditUserModal = async (id, type) => {
    const modal = document.getElementById('crudModal');
    const title = document.getElementById('modalTitle');
    const fields = document.getElementById('modalFields');
    const form = document.getElementById('crudForm');
    
    title.textContent = 'Editar Usuario';
    form.dataset.type = 'user';
    form.dataset.mode = 'edit';
    form.dataset.editId = id;
    form.dataset.userType = type;

    let endpoint = '';
    if (type === 'student') endpoint = `/admin/students/${id}`;
    else if (type === 'professor') endpoint = `/admin/employees/${id}`;
    else endpoint = `/admin/members/${id}`;

    const res = await fetchApi(endpoint);
    if (!res || !res.ok) { showToast('Error cargando datos', 'error'); return; }
    const data = await res.json();

    let firstName='', lastName='', doc='', email='', code='', personId='', memberId='';
    
    if (type === 'student') {
        firstName = data.member.person.firstName;
        lastName = data.member.person.lastName;
        doc = data.member.person.identificationNumber;
        email = data.member.universityEmail;
        code = data.studentCode;
        personId = data.member.person.id;
        memberId = data.member.id;
    } else if (type === 'professor') {
        firstName = data.member.person.firstName;
        lastName = data.member.person.lastName;
        doc = data.member.person.identificationNumber;
        email = data.member.universityEmail;
        code = data.employeeCode;
        personId = data.member.person.id;
        memberId = data.member.id;
    } else {
        firstName = data.person.firstName;
        lastName = data.person.lastName;
        doc = data.person.identificationNumber;
        email = data.universityEmail;
        code = data.universityCode;
        personId = data.person.id;
        memberId = data.id;
    }
    
    form.dataset.personId = personId;
    form.dataset.memberId = memberId;

    fields.innerHTML = `
      <div class="input-group"><label>Nombre</label><input type="text" name="firstName" value="${firstName}" required></div>
      <div class="input-group"><label>Apellido</label><input type="text" name="lastName" value="${lastName}" required></div>
      <div class="input-group"><label>Documento</label><input type="text" name="identificationNumber" value="${doc}" required></div>
      <div class="input-group"><label>Email Institucional</label><input type="email" name="email" value="${email}" required></div>
      <div class="input-group"><label>Código</label><input type="text" name="code" value="${code}" required></div>
      <div class="input-group"><label>Nueva Contraseña (Opcional)</label><input type="password" name="password" placeholder="Dejar vacío para mantener"></div>
    `;
    
    modal.showModal();
  };

  window.openCreateCourseModal = async () => {
    const modal = document.getElementById('crudModal');
    const title = document.getElementById('modalTitle');
    const fields = document.getElementById('modalFields');
    const form = document.getElementById('crudForm');
    
    title.textContent = 'Crear Nuevo Curso';
    form.dataset.type = 'course';
    form.dataset.mode = 'create';
    
    // Fetch programs
    let programOptions = '<option value="">Seleccione un programa...</option>';
    try {
        const res = await fetchApi('/admin/programs');
        if (res && res.ok) {
            const programs = await res.json();
            programOptions += programs.map(p => `<option value="${p.id}">${p.name}</option>`).join('');
        }
    } catch (e) { console.error(e); }

    fields.innerHTML = `
      <div class="input-group"><label>Nombre del Curso</label><input type="text" name="name" required></div>
      <div class="input-group"><label>Código</label><input type="text" name="code" required></div>
      <div class="input-group"><label>Créditos</label><input type="number" name="credits" required></div>
      <div class="input-group"><label>Nivel (Semestre)</label><input type="number" name="level" required></div>
      <div class="input-group"><label>Programa</label><select name="programId" required>${programOptions}</select></div>
    `;
    
    modal.showModal();
  };

  window.openEditCourseModal = async (id) => {
    const modal = document.getElementById('crudModal');
    const title = document.getElementById('modalTitle');
    const fields = document.getElementById('modalFields');
    const form = document.getElementById('crudForm');
    
    title.textContent = 'Editar Curso';
    form.dataset.type = 'course';
    form.dataset.mode = 'edit';
    form.dataset.editId = id;

    const res = await fetchApi(`/admin/courses/${id}`);
    if (!res || !res.ok) { showToast('Error cargando datos', 'error'); return; }
    const data = await res.json();

    // Fetch programs
    let programOptions = '<option value="">Seleccione un programa...</option>';
    try {
        const pRes = await fetchApi('/admin/programs');
        if (pRes && pRes.ok) {
            const programs = await pRes.json();
            programOptions += programs.map(p => 
                `<option value="${p.id}" ${p.id === data.programId ? 'selected' : ''}>${p.name}</option>`
            ).join('');
        }
    } catch (e) { console.error(e); }

    fields.innerHTML = `
      <div class="input-group"><label>Nombre del Curso</label><input type="text" name="name" value="${data.name}" required></div>
      <div class="input-group"><label>Código</label><input type="text" name="code" value="${data.code}" required></div>
      <div class="input-group"><label>Créditos</label><input type="number" name="credits" value="${data.credits}" required></div>
      <div class="input-group"><label>Nivel</label><input type="number" name="level" value="${data.level}" required></div>
      <div class="input-group"><label>Programa</label><select name="programId" required>${programOptions}</select></div>
    `;
    modal.showModal();
  };

  window.openCreateTaskModal = async () => {
    const modal = document.getElementById('crudModal');
    const title = document.getElementById('modalTitle');
    const fields = document.getElementById('modalFields');
    const form = document.getElementById('crudForm');
    
    title.textContent = 'Crear Nueva Tarea';
    form.dataset.type = 'task';
    form.dataset.mode = 'create';
    
    // Fetch all course groups for admin
    let groupOptions = '<option value="">Seleccione un grupo...</option>';
    try {
        const res = await fetchApi('/admin/groups');
        if (res && res.ok) {
            const groups = await res.json();
            groupOptions += groups.map(g => 
                `<option value="${g.id}">${g.course.name} (Grupo ${g.groupCode})</option>`
            ).join('');
        }
    } catch (e) { console.error(e); }

    fields.innerHTML = `
      <div class="input-group"><label>Título</label><input type="text" name="title" required></div>
      <div class="input-group"><label>Grupo del Curso</label><select name="courseGroupId" required>${groupOptions}</select></div>
      <div class="input-group"><label>Descripción</label><textarea name="description"></textarea></div>
      <div class="input-group"><label>Fecha de Entrega</label><input type="datetime-local" name="dueDate" required></div>
      <div class="input-group"><label>Puntaje Máximo</label><input type="number" step="0.1" name="maxScore" required></div>
    `;
    
    modal.showModal();
  };

  window.openEditTaskModal = async (id) => {
    const modal = document.getElementById('crudModal');
    const title = document.getElementById('modalTitle');
    const fields = document.getElementById('modalFields');
    const form = document.getElementById('crudForm');
    
    title.textContent = 'Editar Tarea';
    form.dataset.type = 'task';
    form.dataset.mode = 'edit';
    form.dataset.editId = id;

    const res = await fetchApi(`/admin/assignments/${id}`);
    if (!res || !res.ok) { showToast('Error cargando datos', 'error'); return; }
    const data = await res.json();

    // Fetch all course groups for admin
    let groupOptions = '<option value="">Seleccione un grupo...</option>';
    try {
        const gRes = await fetchApi('/admin/groups');
        if (gRes && gRes.ok) {
            const groups = await gRes.json();
            groupOptions += groups.map(g => 
                `<option value="${g.id}" ${g.id === data.courseGroupId ? 'selected' : ''}>${g.course.name} (Grupo ${g.groupCode})</option>`
            ).join('');
        }
    } catch (e) { console.error(e); }

    let dateStr = '';
    if(data.dueDate) {
        const d = new Date(data.dueDate);
        d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
        dateStr = d.toISOString().slice(0,16);
    }

    fields.innerHTML = `
      <div class="input-group"><label>Título</label><input type="text" name="title" value="${data.title}" required></div>
      <div class="input-group"><label>Grupo del Curso</label><select name="courseGroupId" required>${groupOptions}</select></div>
      <div class="input-group"><label>Descripción</label><textarea name="description">${data.description || ''}</textarea></div>
      <div class="input-group"><label>Fecha de Entrega</label><input type="datetime-local" name="dueDate" value="${dateStr}" required></div>
      <div class="input-group"><label>Puntaje Máximo</label><input type="number" step="0.1" name="maxScore" value="${data.maxScore}" required></div>
    `;
    modal.showModal();
  };

  window.openProfessorEditTaskModal = async (id) => {
    const modal = document.getElementById('crudModal');
    const title = document.getElementById('modalTitle');
    const fields = document.getElementById('modalFields');
    const form = document.getElementById('crudForm');
    
    title.textContent = 'Editar Tarea';
    form.dataset.type = 'professor-task';
    form.dataset.mode = 'edit';
    form.dataset.editId = id;

    const res = await fetchApi(`/professor/assignments/${id}`);
    if (!res || !res.ok) { showToast('Error cargando datos', 'error'); return; }
    const data = await res.json();

    // Fetch courses for select
    let courseOptions = '<option value="">Seleccione un curso...</option>';
    try {
        const cRes = await fetchApi('/professor/courses');
        if (cRes && cRes.ok) {
            const courses = await cRes.json();
            courseOptions += courses.map(c => 
                `<option value="${c.courseGroupId}" ${c.courseGroupId === data.courseGroupId ? 'selected' : ''}>${c.courseName} (Grupo ${c.groupCode})</option>`
            ).join('');
        }
    } catch (e) { console.error(e); }

    let dateStr = '';
    if(data.dueDate) {
        const d = new Date(data.dueDate);
        d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
        dateStr = d.toISOString().slice(0,16);
    }

    fields.innerHTML = `
      <div class="input-group"><label>Título</label><input type="text" name="title" value="${data.title}" required></div>
      <div class="input-group"><label>Curso</label><select name="courseGroupId" required>${courseOptions}</select></div>
      <div class="input-group"><label>Descripción</label><textarea name="description">${data.description || ''}</textarea></div>
      <div class="input-group"><label>Fecha de Entrega</label><input type="datetime-local" name="dueDate" value="${dateStr}" required></div>
      <div class="input-group"><label>Puntaje Máximo</label><input type="number" step="0.1" name="maxScore" value="${data.maxScore}" required></div>
    `;
    
    modal.showModal();
  };

  // Handle Form Submit
  const crudForm = document.getElementById('crudForm');
  if (crudForm) {
    crudForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const formData = new FormData(crudForm);
      const type = crudForm.dataset.type;
      const mode = crudForm.dataset.mode;
      const editId = crudForm.dataset.editId;
      const modal = document.getElementById('crudModal');
      
      try {
        if (type === 'user') {
          if (mode === 'create') {
            // 1. Create Person
            const personRes = await fetchApi('/admin/people', {
              method: 'POST',
              body: JSON.stringify({
                firstName: formData.get('firstName'),
                lastName: formData.get('lastName'),
                identificationNumber: formData.get('identificationNumber'),
                identificationType: 'CC', // Default
                phone: '0000000000',
                homeAddress: 'N/A',
                birthDate: '2000-01-01'
              })
            });
            if (!personRes.ok) throw new Error('Error creating person');
            const person = await personRes.json();

            // 2. Create Member
            const memberRes = await fetchApi('/admin/members', {
              method: 'POST',
              body: JSON.stringify({
                personId: person.id,
                universityCode: formData.get('code'), // Using code as uni code for simplicity
                universityEmail: formData.get('email'),
                password: formData.get('password')
              })
            });
            if (!memberRes.ok) throw new Error('Error creating member');
            const member = await memberRes.json();

            // 3. Create Student or Employee
            const userType = formData.get('userType');
            if (userType === 'student') {
              await fetchApi('/admin/students', {
                method: 'POST',
                body: JSON.stringify({
                  memberId: member.id,
                  studentCode: formData.get('code')
                })
              });
            } else {
              await fetchApi('/admin/employees', {
                method: 'POST',
                body: JSON.stringify({
                  memberId: member.id,
                  employeeCode: formData.get('code'),
                  employeeRole: 'PROFESOR_PLANTA',
                  employeeType: 'ACADEMICO',
                  contractType: 'TIEMPO_COMPLETO',
                  salary: 0,
                  status: 'ACTIVO',
                  hireDate: new Date().toISOString().split('T')[0]
                })
              });
            }
            showToast('Usuario creado exitosamente');
          } else {
            // EDIT MODE
            const personId = crudForm.dataset.personId;
            const memberId = crudForm.dataset.memberId;
            const userType = crudForm.dataset.userType;

            // 1. Update Person
            await fetchApi(`/admin/people/${personId}`, {
                method: 'PUT',
                body: JSON.stringify({
                    firstName: formData.get('firstName'),
                    lastName: formData.get('lastName'),
                    identificationNumber: formData.get('identificationNumber'),
                    identificationType: 'CC',
                    phone: '0000000000',
                    homeAddress: 'N/A',
                    birthDate: '2000-01-01'
                })
            });

            // 2. Update Member (Email/Password)
            const memberUpdate = {
                personId: personId,
                universityEmail: formData.get('email'),
                universityCode: formData.get('code') // Assuming code is synced
            };
            if(formData.get('password')) {
                memberUpdate.password = formData.get('password');
            }
            
            // Always update member to save email/code changes
            await fetchApi(`/admin/members/${memberId}`, {
                method: 'PUT',
                body: JSON.stringify(memberUpdate)
            });

            // 3. Update Student/Employee Code
            if (userType === 'student') {
                await fetchApi(`/admin/students/${editId}`, {
                    method: 'PUT',
                    body: JSON.stringify({
                        memberId: memberId,
                        studentCode: formData.get('code')
                    })
                });
            } else if (userType === 'professor') {
                await fetchApi(`/admin/employees/${editId}`, {
                    method: 'PUT',
                    body: JSON.stringify({
                        memberId: memberId,
                        employeeCode: formData.get('code'),
                        employeeRole: 'PROFESOR_PLANTA',
                        employeeType: 'ACADEMICO',
                        contractType: 'TIEMPO_COMPLETO',
                        salary: 0,
                        status: 'ACTIVO',
                        hireDate: new Date().toISOString().split('T')[0]
                    })
                });
            }
            showToast('Usuario actualizado exitosamente');
          }
          loadAdminUsers(crudForm.dataset.userType || 'all');

        } else if (type === 'course') {
          const body = {
              name: formData.get('name'),
              code: formData.get('code'),
              credits: Number(formData.get('credits')),
              level: Number(formData.get('level')),
              programId: Number(formData.get('programId'))
          };
          
          if (mode === 'create') {
              await fetchApi('/admin/courses', { method: 'POST', body: JSON.stringify(body) });
              showToast('Curso creado exitosamente');
          } else {
              await fetchApi(`/admin/courses/${editId}`, { method: 'PUT', body: JSON.stringify(body) });
              showToast('Curso actualizado exitosamente');
          }
          loadAdminCourses();

        } else if (type === 'task') {
          const body = {
              title: formData.get('title'),
              description: formData.get('description'),
              dueDate: new Date(formData.get('dueDate')).toISOString(),
              maxScore: Number(formData.get('maxScore')),
              courseGroupId: Number(formData.get('courseGroupId'))
          };

          if (mode === 'create') {
              await fetchApi('/admin/assignments', { method: 'POST', body: JSON.stringify(body) });
              showToast('Tarea creada exitosamente');
          } else {
              await fetchApi(`/admin/assignments/${editId}`, { method: 'PUT', body: JSON.stringify(body) });
              showToast('Tarea actualizada exitosamente');
          }
          loadAdminTasks();
        } else if (type === 'professor-task') {
            const body = {
                title: formData.get('title'),
                description: formData.get('description'),
                dueDate: new Date(formData.get('dueDate')).toISOString(),
                maxScore: Number(formData.get('maxScore')),
                courseGroupId: Number(formData.get('courseGroupId'))
            };
            
            if (mode === 'create') {
                await fetchApi('/professor/assignments', { method: 'POST', body: JSON.stringify(body) });
                showToast('Tarea creada exitosamente');
            } else {
                await fetchApi(`/professor/assignments/${editId}`, { method: 'PUT', body: JSON.stringify(body) });
                showToast('Tarea actualizada exitosamente');
            }
            
            if(document.getElementById('view-tasks').classList.contains('active')) {
                loadProfessorTasks();
            }
        } else if (type === 'submission') {
            const contentUrl = formData.get('contentUrl');
            await fetchApi(`/student/assignments/${editId}/submit`, { 
                method: 'POST', 
                headers: { 'Content-Type': 'text/plain' },
                body: contentUrl 
            });
            showToast('Tarea entregada exitosamente');
            // Refresh tasks view if active
            if(document.getElementById('view-tasks').classList.contains('active')) {
                loadStudentTasks(); // Reloads tasks
            }
        }
        
        modal.close();
        crudForm.reset();

      } catch (error) {
        console.error(error);
        showToast('Error al guardar: ' + error.message, 'error');
      }
    });
  }

  // --- 8. Delete Functions ---

  window.deleteUser = async (id, type) => {
    if (!await showConfirm('¿Estás seguro de eliminar este usuario?')) return;
    
    let endpoint = `/admin/members/${id}`;
    if (type === 'student') endpoint = `/admin/students/${id}`;
    if (type === 'professor') endpoint = `/admin/employees/${id}`;

    const res = await fetchApi(endpoint, { method: 'DELETE' });
    if (res && res.ok) {
      showToast('Usuario eliminado');
      loadAdminUsers(type);
    } else {
      showToast('Error al eliminar usuario', 'error');
    }
  };

  window.deleteCourse = async (id) => {
    if (!await showConfirm('¿Estás seguro de eliminar este curso?')) return;
    const res = await fetchApi(`/admin/courses/${id}`, { method: 'DELETE' });
    if (res && res.ok) {
      showToast('Curso eliminado');
      loadAdminCourses();
    } else {
      showToast('Error al eliminar curso', 'error');
    }
  };

  window.deleteTask = async (id) => {
    if (!await showConfirm('¿Estás seguro de eliminar esta tarea?')) return;
    const res = await fetchApi(`/admin/assignments/${id}`, { method: 'DELETE' });
    if (res && res.ok) {
      showToast('Tarea eliminada');
      loadAdminTasks();
    } else {
      showToast('Error al eliminar tarea', 'error');
    }
  };

  window.deleteProfessorTask = async (id) => {
    if (!await showConfirm('¿Estás seguro de eliminar esta tarea?')) return;
    
    const res = await fetchApi(`/professor/assignments/${id}`, { method: 'DELETE' });
    if (res && res.ok) {
      showToast('Tarea eliminada');
      loadProfessorTasks();
    } else {
      showToast('Error al eliminar tarea', 'error');
    }
  };

  // --- 9. Professor Functions ---

  window.openProfessorStudentsModal = async (groupId) => {
    console.log('Opening students modal for group:', groupId);
    // alert('Intentando abrir estudiantes para grupo: ' + groupId); // Debug
    const modal = document.getElementById('listModal');
    const title = document.getElementById('listModalTitle');
    const content = document.getElementById('listModalContent');
    
    title.textContent = 'Estudiantes del Grupo';
    content.innerHTML = '<div class="list-item">Cargando...</div>';
    modal.showModal();

    try {
        const res = await fetchApi(`/professor/courses/${groupId}/students`);
        if (res && res.ok) {
          const students = await res.json();
          if (students.length === 0) {
            content.innerHTML = '<div class="list-item">No hay estudiantes inscritos.</div>';
          } else {
            content.innerHTML = students.map(s => `
              <div class="list-item">
                <div class="list-item-content">
                  <h3>${s.fullName}</h3>
                  <p>${s.studentCode} | ${s.email}</p>
                </div>
              </div>
            `).join('');
          }
        } else {
          console.error('Error fetching students:', res ? res.status : 'No response');
          content.innerHTML = '<div class="list-item">Error cargando estudiantes.</div>';
          showToast('Error al cargar estudiantes', 'error');
        }
    } catch (e) {
        console.error('Exception fetching students:', e);
        content.innerHTML = '<div class="list-item">Error de conexión.</div>';
        showToast('Error de conexión', 'error');
    }
  };

  window.openProfessorCreateTaskModal = async (groupId) => {
    const modal = document.getElementById('crudModal');
    const title = document.getElementById('modalTitle');
    const fields = document.getElementById('modalFields');
    const form = document.getElementById('crudForm');
    
    title.textContent = 'Crear Nueva Tarea';
    form.dataset.type = 'professor-task';
    form.dataset.mode = 'create';
    
    // Fetch courses to populate select
    let courseOptions = '<option value="">Seleccione un curso...</option>';
    try {
        const res = await fetchApi('/professor/courses');
        if (res && res.ok) {
            const courses = await res.json();
            courseOptions += courses.map(c => 
                `<option value="${c.courseGroupId}" ${c.courseGroupId == groupId ? 'selected' : ''}>${c.courseName} (Grupo ${c.groupCode})</option>`
            ).join('');
        }
    } catch (e) { console.error(e); }

    fields.innerHTML = `
      <div class="input-group"><label>Título</label><input type="text" name="title" required placeholder="Ej: Taller de Matemáticas"></div>
      <div class="input-group"><label>Curso</label><select name="courseGroupId" required>${courseOptions}</select></div>
      <div class="input-group"><label>Descripción</label><textarea name="description" placeholder="Detalles de la tarea..."></textarea></div>
      <div class="input-group"><label>Fecha de Entrega</label><input type="datetime-local" name="dueDate" required></div>
      <div class="input-group"><label>Puntaje Máximo</label><input type="number" step="0.1" name="maxScore" required placeholder="5.0"></div>
    `;
    
    modal.showModal();
  };

  // --- 10. Student Functions ---

  window.openStudentCourseTasks = async (groupId, courseName) => {
    const modal = document.getElementById('listModal');
    const title = document.getElementById('listModalTitle');
    const content = document.getElementById('listModalContent');
    
    title.textContent = `Tareas de ${courseName}`;
    content.innerHTML = '<div class="list-item">Cargando...</div>';
    modal.showModal();

    // We fetch all pending assignments and filter client-side
    // Ideally backend should support filtering by course
    const res = await fetchApi('/student/assignments/pending');
    if (res && res.ok) {
      const allTasks = await res.json();
      const courseTasks = allTasks.filter(t => t.courseGroupId === groupId);
      
      if (courseTasks.length === 0) {
        content.innerHTML = '<div class="list-item">No hay tareas pendientes para este curso.</div>';
      } else {
        content.innerHTML = courseTasks.map(t => `
          <div class="list-item">
            <div class="list-item-content">
              <span class="badge date" style="margin-bottom: 4px;">${t.dueDate ? new Date(t.dueDate).toLocaleDateString() : 'Sin fecha'}</span>
              <h3>${t.title}</h3>
              <p>${t.description || ''}</p>
              <small>Puntos: ${t.maxScore}</small>
            </div>
            <div class="list-item-action">
               <button class="btn small primary" onclick="openSubmitTaskModal(${t.id}, '${t.title}')">Entregar</button>
            </div>
          </div>
        `).join('');
      }
    } else {
      content.innerHTML = '<div class="list-item">Error cargando tareas.</div>';
    }
  };

  window.openSubmitTaskModal = (taskId, taskTitle) => {
      // Close list modal first
      document.getElementById('listModal').close();
      
      const modal = document.getElementById('crudModal');
      const title = document.getElementById('modalTitle');
      const fields = document.getElementById('modalFields');
      const form = document.getElementById('crudForm');
      
      title.textContent = `Entregar Tarea: ${taskTitle}`;
      form.dataset.type = 'submission';
      form.dataset.mode = 'create';
      form.dataset.editId = taskId; // Using editId to store assignmentId
      
      fields.innerHTML = `
        <div class="input-group">
            <label>Enlace de la entrega (URL)</label>
            <input type="url" name="contentUrl" placeholder="https://..." required>
        </div>
        <p style="font-size: 12px; color: #666; margin-top: 5px;">Por favor sube tu archivo a Drive/Dropbox y pega el enlace aquí.</p>
      `;
      
      modal.showModal();
  };

})();
