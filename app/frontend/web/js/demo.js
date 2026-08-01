/**
 * Modo demostración del cliente web.
 *
 * El backend de microservicios corre en local y no está desplegado, así que en
 * un despliegue estático (Vercel, GitHub Pages) no hay API a la que llamar.
 * Este módulo se activa solo fuera de localhost — o forzado con `?demo=1` — y
 * responde las peticiones con datos de ejemplo para que la interfaz se pueda
 * recorrer completa.
 *
 * En desarrollo local no hace absolutamente nada: la aplicación habla con el
 * backend real. Debe cargarse antes que `js/app.js`.
 */
(function () {
  'use strict';

  var host = location.hostname;
  var isLocal = host === 'localhost' || host === '127.0.0.1' || host === '';
  var param = new URLSearchParams(location.search).get('demo');

  // `?demo=1` fuerza el modo en local y queda recordado durante la pestaña, para
  // poder recorrer la aplicación sin levantar el backend. `?demo=0` lo apaga.
  if (param === '1') sessionStorage.setItem('kapp_demo', '1');
  if (param === '0') sessionStorage.removeItem('kapp_demo');

  var forced = sessionStorage.getItem('kapp_demo') === '1';

  if (isLocal && !forced) return;

  window.KAPP_DEMO = true;

  // ── Datos de ejemplo ──────────────────────────────────────────────────────
  // Personas, cursos y tareas ficticios. No corresponden a datos reales de la
  // Fundación Universitaria Konrad Lorenz.

  var courses = [
    { courseGroupId: 1, courseName: 'Estructuras de Datos', professorName: 'Ing. Camilo Restrepo', groupCode: 'IS-204-G1' },
    { courseGroupId: 2, courseName: 'Bases de Datos', professorName: 'Ing. Paula Herrera', groupCode: 'IS-301-G2' },
    { courseGroupId: 3, courseName: 'Arquitectura de Software', professorName: 'Ing. Andres Molina', groupCode: 'IS-402-G1' },
    { courseGroupId: 4, courseName: 'Redes y Comunicaciones', professorName: 'Ing. Sofia Duarte', groupCode: 'IS-305-G3' },
    { courseGroupId: 5, courseName: 'Ingenieria de Requisitos', professorName: 'Ing. Julian Bermudez', groupCode: 'IS-208-G1' },
    { courseGroupId: 6, courseName: 'Matematicas Discretas', professorName: 'Mat. Elena Cardona', groupCode: 'MA-110-G4' }
  ];

  var pending = [
    { id: 11, title: 'Taller de arboles AVL', description: 'Implementar insercion, balanceo y recorridos en Java. Entrega individual.', dueDate: '2026-08-14', courseGroupId: 1 },
    { id: 12, title: 'Modelo entidad-relacion', description: 'Disenar el esquema normalizado del caso de estudio y justificar las claves.', dueDate: '2026-08-18', courseGroupId: 2 },
    { id: 13, title: 'Analisis de patrones', description: 'Comparar arquitectura hexagonal y por capas sobre el mismo dominio.', dueDate: '2026-08-21', courseGroupId: 3 }
  ];

  var submitted = [
    { id: 9, title: 'Complejidad algoritmica', description: 'Analisis asintotico de tres algoritmos de ordenamiento.', dueDate: '2026-07-24', courseGroupId: 1 },
    { id: 10, title: 'Consultas SQL avanzadas', description: 'Joins, subconsultas y funciones de ventana sobre el esquema academico.', dueDate: '2026-07-29', courseGroupId: 2 }
  ];

  var members = [
    { id: 1, universityCode: '614231045', person: { firstName: 'Laura', lastName: 'Mendoza' } },
    { id: 2, universityCode: '614231088', person: { firstName: 'Daniel', lastName: 'Ospina' } },
    { id: 3, universityCode: '614230917', person: { firstName: 'Valentina', lastName: 'Rios' } },
    { id: 4, universityCode: '702110034', person: { firstName: 'Camilo', lastName: 'Restrepo' } },
    { id: 5, universityCode: '702110077', person: { firstName: 'Paula', lastName: 'Herrera' } },
    { id: 6, universityCode: '614231120', person: { firstName: 'Mateo', lastName: 'Guzman' } }
  ];

  var students = [
    { id: 1, studentCode: '614231045', member: { person: { firstName: 'Laura', lastName: 'Mendoza' } } },
    { id: 2, studentCode: '614231088', member: { person: { firstName: 'Daniel', lastName: 'Ospina' } } },
    { id: 3, studentCode: '614230917', member: { person: { firstName: 'Valentina', lastName: 'Rios' } } }
  ];

  var employees = [
    { id: 4, employeeCode: '702110034', employeeRole: 'PROFESOR_PLANTA', member: { person: { firstName: 'Camilo', lastName: 'Restrepo' } } },
    { id: 5, employeeCode: '702110077', employeeRole: 'PROFESOR_PLANTA', member: { person: { firstName: 'Paula', lastName: 'Herrera' } } }
  ];

  var routes = {
    '/student/courses': courses,
    '/student/assignments/pending': pending,
    '/student/assignments/submitted': submitted,
    '/professor/courses': courses.slice(0, 3),
    '/professor/assignments': pending,
    '/admin/members': members,
    '/admin/students': students,
    '/admin/employees': employees,
    '/admin/courses': courses,
    '/admin/assignments': pending,
    '/admin/programs': [{ id: 1, name: 'Ingenieria de Sistemas', level: 'PREGRADO' }],
    '/admin/groups': courses.map(function (c) { return { id: c.courseGroupId, groupCode: c.groupCode, courseName: c.courseName }; })
  };

  // ── Rol de la sesión ──────────────────────────────────────────────────────

  function roleFromEmail(email) {
    var value = (email || '').toLowerCase();
    if (value.indexOf('admin') === 0) return 'ROLE_ADMIN';
    if (value.indexOf('profesor') === 0 || value.indexOf('docente') === 0) return 'ROLE_PROFESSOR';
    return 'ROLE_STUDENT';
  }

  function session(email) {
    return {
      token: 'demo-session-token',
      type: 'Bearer',
      username: email,
      role: roleFromEmail(email)
    };
  }

  function enterAs(email) {
    sessionStorage.setItem('user_session', JSON.stringify(session(email)));
    localStorage.removeItem('user_session');
    location.href = 'dashboard.html';
  }

  window.kappDemoEnterAs = enterAs;

  // ── Intercepción de la API ────────────────────────────────────────────────

  function respond(body, status) {
    return Promise.resolve(new Response(JSON.stringify(body), {
      status: status || 200,
      headers: { 'Content-Type': 'application/json' }
    }));
  }

  function resolvePath(path, method, payload) {
    if (path === '/auth/login') {
      var email = '';
      try { email = JSON.parse(payload || '{}').email || ''; } catch (e) { email = ''; }
      return session(email || 'estudiante@konradlorenz.edu.co');
    }

    if (routes[path] !== undefined) return routes[path];

    if (/^\/professor\/courses\/\d+\/students$/.test(path)) return students;
    if (/^\/student\/assignments\/\d+\/submit$/.test(path)) return { status: 'SUBMITTED' };
    if (/^\/(admin|professor)\/[a-z]+\/\d+$/.test(path)) {
      return method === 'GET' ? {} : { status: 'OK' };
    }

    return [];
  }

  var realFetch = window.fetch.bind(window);

  window.fetch = function (input, options) {
    var url = typeof input === 'string' ? input : (input && input.url) || '';
    var opts = options || {};
    var method = (opts.method || 'GET').toUpperCase();

    var path = null;
    if (url.indexOf('/api') !== -1) path = url.split('/api')[1].split('?')[0];
    else if (url.indexOf('/auth/') !== -1) path = '/auth/' + url.split('/auth/')[1].split('?')[0];

    if (path === null) return realFetch(input, options);

    return respond(resolvePath(path, method, opts.body));
  };

  // ── Aviso permanente ──────────────────────────────────────────────────────

  function banner() {
    if (document.getElementById('kappDemoBanner')) return;

    var bar = document.createElement('div');
    bar.id = 'kappDemoBanner';
    bar.setAttribute('role', 'note');
    bar.style.cssText = [
      'position:fixed', 'left:0', 'right:0', 'bottom:0', 'z-index:9999',
      'display:flex', 'flex-wrap:wrap', 'align-items:center', 'justify-content:center',
      'gap:6px 14px', 'padding:9px 16px',
      'background:#111318', 'color:#e8eaee',
      'font:600 12px/1.4 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif',
      'letter-spacing:.2px', 'border-top:1px solid rgba(255,255,255,.14)',
      'box-shadow:0 -6px 20px rgba(0,0,0,.28)'
    ].join(';');

    var label = document.createElement('span');
    label.textContent = 'Demo — datos de ejemplo, sin backend conectado.';
    bar.appendChild(label);

    var group = document.createElement('span');
    group.style.cssText = 'display:flex;gap:8px;align-items:center;color:#9aa1ae;font-weight:500';
    group.appendChild(document.createTextNode('Ver como:'));

    [
      ['Estudiante', 'laura.mendoza@konradlorenz.edu.co'],
      ['Profesor', 'profesor.herrera@konradlorenz.edu.co'],
      ['Admin', 'admin@konradlorenz.edu.co']
    ].forEach(function (entry) {
      var link = document.createElement('button');
      link.type = 'button';
      link.textContent = entry[0];
      link.style.cssText = 'background:rgba(255,255,255,.08);color:#f2cf57;border:1px solid rgba(242,207,87,.35);border-radius:6px;padding:3px 9px;font:inherit;cursor:pointer';
      link.addEventListener('click', function () { enterAs(entry[1]); });
      group.appendChild(link);
    });

    bar.appendChild(group);
    document.body.appendChild(bar);
    document.body.style.paddingBottom = '52px';
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', banner);
  } else {
    banner();
  }
})();
