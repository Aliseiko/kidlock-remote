/* KidLock Remote — клиент панели. Ванильный JS, без внешних зависимостей. */
(function () {
  'use strict';

  var token = null;

  // --- утилиты ---
  function $(id) { return document.getElementById(id); }

  function api(path, opts, onDone) {
    opts = opts || {};
    var headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = 'Bearer ' + token;
    fetch('/api/' + path, {
      method: opts.method || 'GET',
      headers: headers,
      body: opts.body ? JSON.stringify(opts.body) : undefined
    }).then(function (res) {
      return res.json().then(function (data) {
        onDone(res.status, data);
      });
    }).catch(function () {
      onDone(0, { error: 'network' });
    });
  }

  function fmtMinutes(m) {
    var h = Math.floor(m / 60), mm = m % 60;
    return h + ':' + (mm < 10 ? '0' : '') + mm;
  }

  var MODE_RU = { schedule: 'расписание', timer: 'таймер', off: 'выключен' };

  // --- вход ---
  $('btnLogin').addEventListener('click', doLogin);
  $('pin').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') doLogin();
  });

  function doLogin() {
    var pin = $('pin').value.trim();
    $('login-error').textContent = '';
    api('login', { method: 'POST', body: { pin: pin } }, function (status, data) {
      if (status === 200 && data.token) {
        token = data.token;
        showPanel();
      } else if (data.error === 'pin_lockout') {
        $('login-error').textContent = 'Слишком много попыток — подождите минуту.';
      } else {
        $('login-error').textContent = 'Неверный PIN.';
      }
    });
  }

  $('btnLogout').addEventListener('click', function () {
    token = null;
    $('screen-panel').hidden = true;
    $('screen-login').hidden = false;
    $('pin').value = '';
  });

  function showPanel() {
    $('screen-login').hidden = true;
    $('screen-panel').hidden = false;
    refresh();
  }

  // --- статус ---
  function refresh() {
    api('status', {}, function (status, data) {
      if (status === 401) { $('btnLogout').click(); return; }
      if (status !== 200) return;
      renderStatus(data);
      renderAppLimits(data.appLimits || []);
      renderWindows(data.timeWindows || [], data.scheduleEnabled);
      loadAppSelect();
    });
  }

  function renderStatus(data) {
    $('st-mode').textContent = MODE_RU[data.mode] || data.mode;
    $('st-remaining').textContent = fmtMinutes(data.remaining ? data.remaining.totalMinutes : 0);
    $('st-limit').textContent = fmtMinutes(data.dailyLimitMinutes || 0);
    $('st-unlocked').textContent = data.unlockedUntilTomorrow ? 'да' : 'нет';
    $('st-version').textContent = 'v' + (data.version || '');
    if ($('global-limit').value === '') $('global-limit').value = data.dailyLimitMinutes;
  }

  // --- дать время / до завтра / сброс ---
  document.querySelectorAll('button[data-add]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      api('unlock', { method: 'POST', body: { minutes: parseInt(btn.dataset.add, 10) } }, refresh);
    });
  });
  $('btn-unlock-today').addEventListener('click', function () {
    api('unlock', { method: 'POST', body: { untilTomorrow: true } }, refresh);
  });
  $('btn-reset-today').addEventListener('click', function () {
    api('reset-today', { method: 'POST' }, refresh);
  });

  // --- глобальный лимит ---
  $('btn-set-limit').addEventListener('click', function () {
    var m = parseInt($('global-limit').value, 10);
    if (isNaN(m) || m < 0) return;
    api('limits', { method: 'POST', body: { minutes: m } }, refresh);
  });

  // --- лимиты приложений ---
  function renderAppLimits(list) {
    var tbody = $('tbody-app-limits');
    tbody.innerHTML = '';
    list.forEach(function (item) {
      var tr = document.createElement('tr');
      tr.innerHTML =
        '<td>' + esc(item.appName) + '</td>' +
        '<td>' + fmtMinutes(item.limitMinutes) + '</td>' +
        '<td>' + fmtMinutes(item.usedMinutes) + '</td>' +
        '<td><button data-remove="' + esc(item.package) + '" class="ghost">✕</button></td>';
      tbody.appendChild(tr);
    });
    tbody.querySelectorAll('button[data-remove]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        api('app-limits?package=' + encodeURIComponent(btn.dataset.remove),
          { method: 'DELETE' }, refresh);
      });
    });
  }

  function loadAppSelect() {
    api('apps', {}, function (status, data) {
      if (status !== 200) return;
      var sel = $('select-app');
      sel.innerHTML = '';
      (data.apps || []).forEach(function (app) {
        var opt = document.createElement('option');
        opt.value = app.package;
        opt.textContent = app.appName;
        sel.appendChild(opt);
      });
    });
  }

  $('btn-add-app-limit').addEventListener('click', function () {
    var pkg = $('select-app').value;
    var m = parseInt($('app-limit-minutes').value, 10);
    if (!pkg || isNaN(m) || m < 5) return;
    api('app-limits', { method: 'POST', body: { package: pkg, minutes: m } }, refresh);
  });

  // --- расписание ---
  function renderWindows(windows, enabled) {
    $('schedule-state').textContent = enabled ? 'Включено' : 'Выключено (работают только лимиты)';
    var tbody = $('tbody-windows');
    tbody.innerHTML = '';
    windows.forEach(function (w) {
      var tr = document.createElement('tr');
      var type = w.type === 'ALLOW' ? 'разрешение' : 'блокировка';
      var days = w.daysOfWeek && w.daysOfWeek.length ? w.daysOfWeek.join(', ') : 'все дни';
      var apps = w.blockAllApps ? 'все' : (w.blockedApps ? w.blockedApps.length + ' шт.' : '—');
      tr.innerHTML =
        '<td>' + type + '</td>' +
        '<td>' + pad(w.startHour) + ':' + pad(w.startMinute) + '–' + pad(w.endHour) + ':' + pad(w.endMinute) + '</td>' +
        '<td>' + days + '</td>' +
        '<td>' + apps + '</td>' +
        '<td></td>';
      tbody.appendChild(tr);
    });
  }

  function pad(n) { return (n < 10 ? '0' : '') + n; }

  function esc(s) {
    var d = document.createElement('div');
    d.textContent = s == null ? '' : String(s);
    return d.innerHTML;
  }

  // --- перезапуск панели после сна ТВ ---
  setInterval(function () {
    if (token && !$('screen-panel').hidden) refresh();
  }, 15000);
})();
