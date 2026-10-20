/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.pairing

/**
 * The page a phone or laptop sees when managing channels.
 *
 * Like [PairingPage], one self-contained document with no external requests of any kind — no
 * fonts, no frameworks, no CDN. The browser is talking to a television on the local network, which
 * may have no route to the internet at all, so a page that needed a CDN to render would just fail.
 *
 * Unlike the pairing page this is opened on a phone OR a laptop, so it is responsive and
 * touch-friendly rather than TV-styled. All channel data arrives as JSON and is rendered with
 * `textContent`/DOM APIs (never string-built HTML), so a channel name full of `<`, `"`, `|` or
 * emoji can never break out into markup.
 */
object ManagerPage {

    fun page(token: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
        <meta name="robots" content="noindex, nofollow">
        <title>OpenTV — manage channels</title>
        <style>
          :root { --bg:#07080c; --surface:#12141d; --surface2:#181b26; --line:#2a2f3d;
                  --text:#e8eaf0; --muted:#a8aec0; --accent:#7c93ff; --accent2:#3a447a;
                  --gold:#ffd166; --err:#ff6b6b; --ok:#6ee7a0; }
          * { box-sizing:border-box; -webkit-tap-highlight-color:transparent; }
          body { margin:0; background:var(--bg); color:var(--text);
                 font:16px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif; }
          .wrap { max-width:760px; margin:0 auto; padding:20px 16px 80px; }
          header { position:sticky; top:0; z-index:5; background:linear-gradient(var(--bg),var(--bg) 78%,rgba(7,8,12,0));
                   padding-top:8px; }
          h1 { font-size:1.35rem; margin:0; letter-spacing:-.02em; }
          .hint { color:var(--muted); font-size:.85rem; margin:4px 0 14px; }
          .search { width:100%; padding:13px 14px; font-size:17px; margin-bottom:14px;
                    background:var(--surface); color:var(--text); border:1px solid var(--line); border-radius:11px; }
          .search:focus { outline:none; border-color:var(--accent); }
          .chips { display:flex; flex-wrap:wrap; gap:8px; margin-bottom:14px; }
          .chip { padding:9px 14px; font-size:.9rem; border-radius:999px; cursor:pointer;
                  background:var(--surface); border:1px solid var(--line); color:var(--muted); }
          .chip.on { background:var(--accent2); border-color:var(--accent); color:var(--text); }
          .list { display:flex; flex-direction:column; gap:8px; }
          .cat { display:flex; align-items:center; gap:12px; padding:15px 16px; cursor:pointer;
                 background:var(--surface); border:1px solid var(--line); border-radius:12px; }
          .cat:active { background:var(--surface2); }
          .cat .label { flex:1; font-weight:600; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
          .cat .count { color:var(--muted); font-size:.85rem; font-variant-numeric:tabular-nums; }
          .cat .chev { color:var(--muted); }
          .row { display:flex; align-items:center; gap:11px; padding:10px 12px;
                 background:var(--surface); border:1px solid var(--line); border-radius:12px; }
          .row.hidden { opacity:.55; }
          .logo { width:44px; height:44px; border-radius:8px; background:#000; object-fit:contain; flex:none; }
          .rowmeta { flex:1; min-width:0; }
          .name { font-weight:600; cursor:text; display:inline-block; max-width:100%;
                  overflow:hidden; text-overflow:ellipsis; white-space:nowrap; vertical-align:bottom; }
          .name:hover { text-decoration:underline dotted; }
          .rename { width:100%; padding:6px 8px; font-size:16px; font-weight:600;
                    background:var(--bg); color:var(--text); border:1px solid var(--accent); border-radius:8px; }
          .sub { color:var(--muted); font-size:.78rem; margin-top:2px; }
          .sub .badge { color:var(--err); font-weight:600; }
          .ctrls { display:flex; align-items:center; gap:6px; flex:none; }
          .btn { width:40px; height:40px; display:flex; align-items:center; justify-content:center;
                 font-size:18px; border-radius:9px; cursor:pointer; user-select:none;
                 background:var(--surface2); border:1px solid var(--line); color:var(--muted); }
          .btn:active { transform:scale(.92); }
          .btn.star.on { color:var(--gold); border-color:var(--gold); }
          .btn.eye.on { color:var(--accent); border-color:var(--accent); }
          .btn.mv { font-size:14px; }
          .btn[disabled] { opacity:.3; pointer-events:none; }
          .back { display:inline-flex; align-items:center; gap:6px; padding:9px 14px; margin-bottom:12px;
                  background:var(--surface); border:1px solid var(--line); border-radius:10px;
                  color:var(--text); font-weight:600; cursor:pointer; }
          .chanhead { font-size:1.1rem; font-weight:700; margin:0 0 4px; }
          .empty { color:var(--muted); text-align:center; padding:40px 10px; }
          #status { position:fixed; left:50%; bottom:18px; transform:translateX(-50%) translateY(120%);
                    max-width:90%; padding:11px 18px; border-radius:11px; font-size:.9rem; font-weight:600;
                    background:var(--surface2); border:1px solid var(--line); color:var(--text);
                    transition:transform .25s ease; z-index:20; box-shadow:0 8px 24px rgba(0,0,0,.4); }
          #status.show { transform:translateX(-50%) translateY(0); }
          #status.ok { border-color:var(--ok); color:var(--ok); }
          #status.err { border-color:var(--err); color:#ffc9c9; }
          .privacy { color:var(--muted); font-size:.8rem; margin-top:28px; line-height:1.5; }
          details.rec { background:var(--surface); border:1px solid var(--line); border-radius:12px; margin-bottom:16px; }
          details.rec summary { padding:14px 16px; font-weight:600; cursor:pointer; list-style:none; }
          details.rec summary::-webkit-details-marker { display:none; }
          details.rec summary::before { content:'\25B8'; color:var(--muted); margin-right:8px; }
          details.rec[open] summary::before { content:'\25BE'; }
          details.rec .body { padding:0 16px 16px; }
          .field { display:block; margin:10px 0; }
          .field span { display:block; color:var(--muted); font-size:.8rem; margin-bottom:4px; }
          .field input { width:100%; padding:11px 12px; font-size:16px; background:var(--bg); color:var(--text);
                         border:1px solid var(--line); border-radius:9px; }
          .field input:focus { outline:none; border-color:var(--accent); }
          .save { margin-top:6px; padding:12px 18px; font-size:15px; font-weight:600;
                  background:var(--accent2); color:var(--text); border:1px solid var(--accent); border-radius:10px; cursor:pointer; }
          .save:active { transform:scale(.97); }
          .field select { width:100%; padding:11px 12px; font-size:16px; background:var(--bg); color:var(--text);
                          border:1px solid var(--line); border-radius:9px; }
          .field select:focus { outline:none; border-color:var(--accent); }
          /* `.field { display:block }` above would otherwise override the [hidden] attribute, leaving
             a type's irrelevant fields (e.g. username/password on a Stalker portal) on screen. */
          .field[hidden] { display:none; }
          .check { display:flex; align-items:center; gap:10px; margin:10px 0; cursor:pointer; }
          .check input { width:20px; height:20px; accent-color:var(--accent); }
          .row.picked, .cat.picked { border-color:var(--gold); box-shadow:0 0 0 1px var(--gold) inset; }
          .row.target, .cat.target { cursor:copy; }
          .pickbar { position:sticky; top:62px; z-index:6; display:flex; align-items:center; gap:10px; justify-content:space-between;
                     padding:11px 14px; margin-bottom:10px; border-radius:11px; background:var(--accent2); border:1px solid var(--accent); font-weight:600; }
          .pickcancel { color:var(--text); text-decoration:underline; cursor:pointer; white-space:nowrap; }
          .cat .ctrls { margin-left:4px; }
          .cat.off .label { color:var(--muted); }
          .q { display:inline-block; font-size:.68rem; font-weight:700; padding:1px 6px; margin-right:4px; border-radius:5px;
               background:var(--surface2); border:1px solid var(--line); color:var(--muted); }
          .gtools { display:flex; flex-wrap:wrap; gap:8px; margin:6px 0 10px; }
          .gtools .save { margin-top:0; }
          .gtools select { padding:10px; font-size:15px; background:var(--bg); color:var(--text); border:1px solid var(--line); border-radius:10px; max-width:100%; }
          .incl { display:flex; align-items:center; gap:8px; width:100%; color:var(--muted); font-size:.85rem; }
          .mini { padding:6px 11px; font-size:.82rem; font-weight:600; border-radius:8px; cursor:pointer;
                  background:var(--surface2); border:1px solid var(--line); color:var(--text); }
          .mini.go { background:var(--accent2); border-color:var(--accent); }
          .dup { display:flex; align-items:center; gap:8px; padding:10px 12px; background:var(--bg); border:1px solid var(--line); border-radius:10px; }
          .dup .what { flex:1; min-width:0; font-size:.9rem; }
          .sub2 { font-size:.95rem; margin:18px 0 8px; }
          .sheet { position:fixed; inset:0; z-index:30; background:rgba(0,0,0,.6); display:flex; align-items:flex-end; justify-content:center; }
          .sheet[hidden] { display:none; }
          .sheetbox { width:100%; max-width:760px; max-height:85vh; overflow:auto; background:var(--surface); border-radius:16px 16px 0 0;
                      padding:16px; border:1px solid var(--line); }
          .sheethead { display:flex; justify-content:space-between; align-items:center; gap:10px; font-weight:700; margin-bottom:10px; }
        </style>
        </head>
        <body>
          <div class="wrap">
            <header>
              <h1>OpenTV — manage channels</h1>
              <p class="hint">Changes apply to your TV the moment you make them.</p>
            </header>

            <details class="rec" id="provCard">
              <summary>Add a provider</summary>
              <div class="body">
                <p class="hint">Add an Xtream, M3U or Stalker portal from here — a real keyboard beats the TV remote. Your login stays on the TV.</p>
                <label class="field"><span>Type</span>
                  <select id="provKind">
                    <option value="xtream">Xtream Codes login</option>
                    <option value="m3u">M3U playlist URL</option>
                    <option value="stalker">Stalker portal</option>
                  </select></label>
                <label class="field"><span>Name (optional)</span>
                  <input id="provName" placeholder="My provider" autocapitalize="off" autocorrect="off" spellcheck="false"></label>
                <label class="field"><span id="provUrlLabel">Server address</span>
                  <input id="provUrl" placeholder="http://example.com:8080" autocapitalize="off" autocorrect="off" spellcheck="false"></label>
                <label class="field" id="provUserField"><span>Username</span>
                  <input id="provUser" autocapitalize="off" autocorrect="off" spellcheck="false"></label>
                <label class="field" id="provPassField"><span>Password</span>
                  <input id="provPass" type="password" autocomplete="off"></label>
                <label class="field" id="provMacField"><span>MAC address</span>
                  <input id="provMac" placeholder="00:1A:79:xx:xx:xx" autocapitalize="off" autocorrect="off" spellcheck="false"></label>
                <button class="save" id="provSave">Add &amp; load channels</button>
                <button class="save" id="provTest" style="background:var(--surface2);border-color:var(--line);margin-left:8px;">Test</button>
              </div>
            </details>

            <details class="rec" id="recCard">
              <summary>Recording to NAS (SMB)</summary>
              <div class="body">
                <p class="hint">Point recordings at a network drive. Saved straight to your TV — the password stays on the TV.</p>
                <label class="field"><span>Server (IP address)</span>
                  <input id="recHost" placeholder="192.168.0.214" autocapitalize="off" autocorrect="off" spellcheck="false"></label>
                <label class="field"><span>Share</span>
                  <input id="recShare" placeholder="OpenTV" autocapitalize="off" autocorrect="off" spellcheck="false"></label>
                <label class="field"><span>Folder (optional)</span>
                  <input id="recFolder" placeholder="(leave blank)" autocapitalize="off" autocorrect="off" spellcheck="false"></label>
                <label class="field"><span>Username</span>
                  <input id="recUser" placeholder="record" autocapitalize="off" autocorrect="off" spellcheck="false"></label>
                <label class="field"><span>Password</span>
                  <input id="recPass" type="password" autocomplete="off"></label>
                <button class="save" id="recSave">Save &amp; use NAS</button>
                <button class="save" id="recTest" style="background:var(--surface2);border-color:var(--line);margin-left:8px;">Test connection</button>
              </div>
            </details>

            <details class="rec" id="bakCard">
              <summary>Backup &amp; restore settings</summary>
              <div class="body">
                <p class="hint">One file with your settings, guide feeds and channel merges. Your provider login, NAS password and TMDB key are never put in it.</p>
                <button class="save" id="bakDownload">Download settings</button>
                <label class="field" style="margin-top:18px"><span>Restore from a file</span>
                  <input id="bakFile" type="file" accept=".json,application/json"></label>
                <div id="bakChoice" hidden>
                  <p class="hint" id="bakInfo"></p>
                  <label class="check"><input type="checkbox" id="bakMerges" checked> <span id="bakMergesLabel">Channel merges</span></label>
                  <label class="check"><input type="checkbox" id="bakSettings" checked> <span>Settings (playback, quality, look, categories, guide feeds…)</span></label>
                  <button class="save" id="bakRestore">Import</button>
                </div>
              </div>
            </details>

            <details class="rec" id="dupCard">
              <summary>Duplicate channels</summary>
              <div class="body">
                <p class="hint">Channels that look like the same one under two names. Merge makes them one row on the TV; it survives refreshes and goes in the backup.</p>
                <div id="dupList" class="list"></div>
                <h3 class="sub2" id="mergedHead" hidden>Merged channels</h3>
                <div id="mergedList" class="list"></div>
              </div>
            </details>

            <section id="catView">
              <div class="toolbar">
                <input id="catSearch" class="search" type="text" placeholder="Filter groups…"
                       autocapitalize="off" autocorrect="off" spellcheck="false">
              </div>
              <p class="hint" id="catHint">Groups in the order the TV shows them. Tap ⇅ on a group, then tap where it should go — or use the arrows.</p>
              <div id="pickBar" class="pickbar" hidden><span id="pickText"></span><span class="pickcancel" id="pickCancel">Cancel</span></div>
              <div id="catList" class="list"></div>
              <div id="catEmpty" class="empty" hidden></div>
              <details class="rec" id="offCard" style="margin-top:16px" hidden>
                <summary id="offHead">Switched-off groups</summary>
                <div class="body">
                  <input id="offSearch" class="search" type="text" placeholder="Filter…"
                         autocapitalize="off" autocorrect="off" spellcheck="false">
                  <div id="offList" class="list"></div>
                </div>
              </details>
            </section>

            <section id="chanView" hidden>
              <div id="back" class="back">&#8249;&nbsp;Groups</div>
              <h2 id="chanHead" class="chanhead"></h2>
              <div id="groupTools" class="gtools"></div>
              <p class="hint" id="chanHint">Tap a name to rename. Star to favourite, eye to hide, ⇅ then tap a spot to move, ⋯ to merge.</p>
              <div id="chanPickBar" class="pickbar" hidden><span id="chanPickText"></span><span class="pickcancel" id="chanPickCancel">Cancel</span></div>
              <div id="chanList" class="list"></div>
              <div id="chanEmpty" class="empty" hidden></div>
            </section>

            <div id="sheet" class="sheet" hidden>
              <div class="sheetbox">
                <div class="sheethead"><span id="sheetTitle"></span><span class="pickcancel" id="sheetClose">Close</span></div>
                <input id="sheetSearch" class="search" type="text" placeholder="Search every channel…"
                       autocapitalize="off" autocorrect="off" spellcheck="false">
                <div id="sheetList" class="list"></div>
              </div>
            </div>

            <p class="privacy">
              This page is served by your television, over your own network — there is no OpenTV
              account and no OpenTV server. Nothing you change here leaves your home.
            </p>
          </div>

          <div id="status"></div>

        <script>
          var EMBEDDED_TOKEN = "$token";
          var token = new URLSearchParams(location.search).get('t') || EMBEDDED_TOKEN;


          var el = function(id){ return document.getElementById(id); };
          var statusTimer = null;
          function setStatus(msg, kind){
            var s = el('status');
            s.textContent = msg;
            s.className = 'show ' + (kind || '');
            if (statusTimer) clearTimeout(statusTimer);
            statusTimer = setTimeout(function(){ s.className = kind || ''; }, 2600);
          }

          function withToken(path){ return path + (path.indexOf('?') >= 0 ? '&' : '?') + 't=' + encodeURIComponent(token); }
          async function getJson(path){
            var r = await fetch(withToken(path));
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
          }
          async function post(path, bodyObj){
            var r = await fetch(withToken(path), {
              method: 'POST', headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(bodyObj)
            });
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
          }
          var UNREACH = "Couldn't reach the TV — still on the same wifi?";

          // ---- Groups ------------------------------------------------------------------------
          var data = { groups: [], off: [], merges: [], favourites: 0 };
          var FAV = 'fav';
          var currentGroup = null;    // the group being viewed: {key,label,…} or the favourites pseudo-group
          var rows = [];              // its channels, in display order
          var pickedGroup = -1;       // index in data.groups picked for a move, -1 = none
          var pickedRow = -1;         // same for rows

          function btn(cls, html, title, onclick){
            var b = document.createElement('div');
            b.className = 'btn ' + cls; b.innerHTML = html; b.title = title;
            b.onclick = function(e){ e.stopPropagation(); onclick(); };
            return b;
          }
          function mini(text, go, onclick){
            var b = document.createElement('span');
            b.className = 'mini' + (go ? ' go' : ''); b.textContent = text;
            b.onclick = function(e){ e.stopPropagation(); onclick(); };
            return b;
          }

          async function loadGroups(){
            try {
              data = await getJson('/groups');
              renderGroups();
              renderOff();
            } catch (e) {
              el('catList').innerHTML = '';
              var d = el('catEmpty'); d.hidden = false; d.textContent = UNREACH;
            }
          }

          function renderGroups(){
            var q = el('catSearch').value.trim().toLowerCase();
            var list = el('catList');
            list.innerHTML = '';
            var empty = el('catEmpty');
            if (!data.groups.length && !data.favourites) {
              empty.hidden = false; empty.textContent = 'No channels yet — add a provider above to get started.';
              return;
            }
            empty.hidden = true;
            if (data.favourites && !q && pickedGroup < 0) {
              list.appendChild(groupRow({ key: FAV, label: '★ Favourites', count: data.favourites }, -1));
            }
            var shown = 0;
            data.groups.forEach(function(g, i){
              if (q && g.label.toLowerCase().indexOf(q) < 0 && g.raw.toLowerCase().indexOf(q) < 0) return;
              list.appendChild(groupRow(g, i));
              shown++;
            });
            if (!shown && q) { empty.hidden = false; empty.textContent = 'No groups match that.'; }
            var bar = el('pickBar');
            bar.hidden = pickedGroup < 0;
            if (pickedGroup >= 0) el('pickText').textContent = 'Tap where “' + data.groups[pickedGroup].label + '” should go';
          }

          function groupRow(g, i){
            var row = document.createElement('div');
            row.className = 'cat' + (i === pickedGroup ? ' picked' : (pickedGroup >= 0 && i >= 0 ? ' target' : ''));
            var label = document.createElement('span'); label.className = 'label'; label.textContent = g.label;
            var count = document.createElement('span'); count.className = 'count'; count.textContent = g.count;
            row.appendChild(label); row.appendChild(count);
            if (i >= 0) {
              var ctrls = document.createElement('div'); ctrls.className = 'ctrls';
              var up = btn('mv', '&#9650;', 'Move up', function(){ moveGroup(i, i - 1); });
              if (i === 0) up.setAttribute('disabled', '');
              var down = btn('mv', '&#9660;', 'Move down', function(){ moveGroup(i, i + 1); });
              if (i === data.groups.length - 1) down.setAttribute('disabled', '');
              var pick = btn('mv', '&#8645;', 'Move to…', function(){ pickedGroup = (pickedGroup === i) ? -1 : i; renderGroups(); });
              ctrls.appendChild(up); ctrls.appendChild(down); ctrls.appendChild(pick);
              row.appendChild(ctrls);
            } else {
              var chev = document.createElement('span'); chev.className = 'chev'; chev.innerHTML = '&#8250;';
              row.appendChild(chev);
            }
            row.onclick = function(){
              if (pickedGroup >= 0) {
                if (i >= 0 && i !== pickedGroup) moveGroup(pickedGroup, i);
                pickedGroup = -1; renderGroups();
                return;
              }
              openGroup(g);
            };
            return row;
          }
          el('pickCancel').onclick = function(){ pickedGroup = -1; renderGroups(); };
          el('catSearch').addEventListener('input', renderGroups);

          function moveGroup(from, to){
            if (to < 0 || to >= data.groups.length || from === to) return;
            var item = data.groups.splice(from, 1)[0];
            data.groups.splice(to, 0, item);
            pickedGroup = -1;
            renderGroups();
            post('/groups/order', { keys: data.groups.map(function(g){ return g.key; }) })
              .then(function(){ setStatus('Group order saved', 'ok'); })
              .catch(function(){ setStatus(UNREACH, 'err'); });
          }

          function renderOff(){
            var card = el('offCard');
            card.hidden = !data.off.length;
            el('offHead').textContent = 'Switched-off groups (' + data.off.length + ')';
            var q = el('offSearch').value.trim().toLowerCase();
            var list = el('offList'); list.innerHTML = '';
            data.off.forEach(function(g){
              if (q && g.label.toLowerCase().indexOf(q) < 0 && g.raw.toLowerCase().indexOf(q) < 0) return;
              var row = document.createElement('div'); row.className = 'cat off';
              var label = document.createElement('span'); label.className = 'label'; label.textContent = g.raw;
              row.appendChild(label);
              row.appendChild(mini('Turn on', true, function(){
                post('/group/switch', { key: g.key, on: true, memberKeys: g.memberKeys })
                  .then(function(){ setStatus('Turned on — the TV is reloading its channels, back in a minute.', 'ok'); setTimeout(loadGroups, 1500); })
                  .catch(function(){ setStatus(UNREACH, 'err'); });
              }));
              list.appendChild(row);
            });
          }
          el('offSearch').addEventListener('input', renderOff);

          // ---- One group's channels -----------------------------------------------------------
          async function openGroup(g){
            currentGroup = g;
            pickedRow = -1;
            el('catView').hidden = true;
            el('chanView').hidden = false;
            el('chanHead').textContent = g.label;
            window.scrollTo(0, 0);
            renderGroupTools();
            await reloadRows();
          }

          async function reloadRows(){
            var listEl = el('chanList'); listEl.innerHTML = '';
            var empty = el('chanEmpty'); empty.hidden = true;
            try {
              var r = await getJson('/group?key=' + encodeURIComponent(currentGroup.key));
              rows = r.rows || [];
              renderRows();
            } catch (e) {
              empty.hidden = false; empty.textContent = UNREACH;
            }
          }

          el('back').onclick = function(){
            el('chanView').hidden = true;
            el('catView').hidden = false;
            rows = []; currentGroup = null;
            loadGroups();
          };

          function renderGroupTools(){
            var box = el('groupTools'); box.innerHTML = '';
            var g = currentGroup;
            if (g.key === FAV) return;
            var hide = document.createElement('button'); hide.className = 'save'; hide.textContent = 'Switch group off';
            hide.style.background = 'var(--surface2)'; hide.style.borderColor = 'var(--line)';
            hide.onclick = function(){
              if (!confirm('Switch off “' + g.label + '”? Its channels leave the TV until you turn it back on (at the bottom of the groups).')) return;
              post('/group/switch', { key: g.key, on: false, memberKeys: g.memberKeys })
                .then(function(){ setStatus('Group switched off', 'ok'); el('back').onclick(); })
                .catch(function(){ setStatus(UNREACH, 'err'); });
            };
            box.appendChild(hide);
            var sel = document.createElement('select');
            var first = document.createElement('option'); first.value = ''; first.textContent = 'Merge this group into…';
            sel.appendChild(first);
            data.groups.forEach(function(o){
              if (o.key === g.key) return;
              var opt = document.createElement('option'); opt.value = o.key; opt.textContent = o.label; sel.appendChild(opt);
            });
            sel.onchange = function(){
              var into = data.groups.filter(function(o){ return o.key === sel.value; })[0];
              if (!into) return;
              if (!confirm('Merge “' + g.label + '” into “' + into.label + '”? Its channels show under “' + into.label + '”.')) { sel.value = ''; return; }
              post('/group/merge', { from: g.key, into: into.key })
                .then(function(){ setStatus('Merged into ' + into.label, 'ok'); el('back').onclick(); })
                .catch(function(){ setStatus(UNREACH, 'err'); });
            };
            box.appendChild(sel);
            // Groups merged into this one, each separable again.
            (data.merges || []).forEach(function(m){
              if (g.memberKeys.indexOf(m.into) < 0) return;
              var line = document.createElement('div'); line.className = 'incl';
              var t = document.createElement('span'); t.textContent = 'Includes “' + m.fromLabel + '”'; t.style.flex = '1';
              line.appendChild(t);
              line.appendChild(mini('Separate', false, function(){
                post('/group/merge', { from: m.from })
                  .then(function(){ setStatus('Separated', 'ok'); el('back').onclick(); })
                  .catch(function(){ setStatus(UNREACH, 'err'); });
              }));
              box.appendChild(line);
            });
          }

          function renderRows(){
            var listEl = el('chanList');
            listEl.innerHTML = '';
            var empty = el('chanEmpty');
            el('chanPickBar').hidden = pickedRow < 0;
            if (pickedRow >= 0) el('chanPickText').textContent = 'Tap where “' + rows[pickedRow].name + '” should go';
            if (!rows.length) { empty.hidden = false; empty.textContent = 'No channels in this group.'; return; }
            empty.hidden = true;
            rows.forEach(function(c, i){ listEl.appendChild(buildRow(c, i)); });
          }
          el('chanPickCancel').onclick = function(){ pickedRow = -1; renderRows(); };

          function patchRow(c, body, okMsg, after){
            body.ids = c.ids;
            post('/row', body)
              .then(function(){ if (after) after(); setStatus(okMsg, 'ok'); })
              .catch(function(){ setStatus(UNREACH, 'err'); });
          }

          function buildRow(c, index){
            var row = document.createElement('div');
            row.className = 'row' + (c.hidden ? ' hidden' : '') + (index === pickedRow ? ' picked' : (pickedRow >= 0 ? ' target' : ''));
            row.onclick = function(){
              if (pickedRow < 0) return;
              if (index !== pickedRow) moveRow(pickedRow, index);
              pickedRow = -1; renderRows();
            };

            var logo = document.createElement('img');
            logo.className = 'logo'; logo.alt = '';
            if (c.logo) { logo.src = c.logo; } else { logo.style.visibility = 'hidden'; }
            logo.onerror = function(){ logo.style.visibility = 'hidden'; };

            var mid = document.createElement('div'); mid.className = 'rowmeta';
            var name = document.createElement('span'); name.className = 'name'; name.textContent = c.name;
            name.onclick = function(e){ if (pickedRow >= 0) return; e.stopPropagation(); startRename(name, c); };
            var sub = document.createElement('div'); sub.className = 'sub';
            (c.qualities || []).forEach(function(q){
              var b = document.createElement('span'); b.className = 'q'; b.textContent = q; sub.appendChild(b);
            });
            if (c.hidden) { var h = document.createElement('span'); h.className = 'badge'; h.textContent = 'Hidden'; sub.appendChild(h); }
            if (!sub.childNodes.length) sub.textContent = c.renamed ? c.original : ' ';
            mid.appendChild(name); mid.appendChild(sub);

            var ctrls = document.createElement('div'); ctrls.className = 'ctrls';
            var star = btn('star' + (c.favourite ? ' on' : ''), c.favourite ? '&#9733;' : '&#9734;', 'Favourite', function(){
              var target = !c.favourite;
              patchRow(c, { favourite: target }, target ? 'Favourited' : 'Unfavourited', function(){
                c.favourite = target;
                if (currentGroup.key === FAV && !target) { rows.splice(rows.indexOf(c), 1); }
                renderRows();
              });
            });
            var eye = btn('eye' + (c.hidden ? '' : ' on'), c.hidden ? '&#128584;' : '&#128065;', c.hidden ? 'Hidden — tap to show' : 'Showing — tap to hide', function(){
              var target = !c.hidden;
              patchRow(c, { hidden: target }, target ? 'Hidden from the TV' : 'Showing on the TV', function(){ c.hidden = target; renderRows(); });
            });
            var pick = btn('mv', '&#8645;', 'Move to…', function(){ pickedRow = (pickedRow === index) ? -1 : index; renderRows(); });
            ctrls.appendChild(star); ctrls.appendChild(eye); ctrls.appendChild(pick);
            if (currentGroup.key !== FAV) ctrls.appendChild(btn('mv', '&#8943;', 'Merge with…', function(){ openMerge(c); }));
            row.appendChild(logo); row.appendChild(mid); row.appendChild(ctrls);
            return row;
          }

          function startRename(span, c){
            var input = document.createElement('input');
            input.type = 'text'; input.className = 'rename'; input.value = c.name;
            input.onclick = function(e){ e.stopPropagation(); };
            span.replaceWith(input); input.focus(); input.select();
            var closed = false;
            function finish(save){
              if (closed) return; closed = true;
              var val = input.value.trim();
              if (save && val !== c.name) {
                patchRow(c, { name: val }, 'Renamed', function(){ c.name = val || c.original; c.renamed = !!val; renderRows(); });
              } else renderRows();
            }
            input.addEventListener('keydown', function(e){
              if (e.key === 'Enter') { e.preventDefault(); finish(true); }
              else if (e.key === 'Escape') { finish(false); }
            });
            input.addEventListener('blur', function(){ finish(true); });
          }

          function moveRow(from, to){
            if (to < 0 || to >= rows.length || from === to) return;
            var item = rows.splice(from, 1)[0];
            rows.splice(to, 0, item);
            renderRows();
            post('/group/order', { group: currentGroup.key, keys: rows.map(function(c){ return c.key; }) })
              .then(function(){ setStatus('Order saved', 'ok'); })
              .catch(function(){ setStatus(UNREACH, 'err'); });
          }

          // ---- Merge two channels -------------------------------------------------------------
          var mergeFrom = null;
          var searchTimer = null;
          function openMerge(c){
            mergeFrom = c;
            el('sheetTitle').textContent = 'Merge “' + c.name + '” with…';
            el('sheetSearch').value = '';
            el('sheet').hidden = false;
            renderMergeTargets(rows);
          }
          el('sheetClose').onclick = function(){ el('sheet').hidden = true; mergeFrom = null; };
          el('sheet').onclick = function(e){ if (e.target === el('sheet')) el('sheetClose').onclick(); };
          el('sheetSearch').addEventListener('input', function(){
            var q = el('sheetSearch').value.trim();
            if (searchTimer) clearTimeout(searchTimer);
            if (q.length < 2) { renderMergeTargets(rows); return; }
            searchTimer = setTimeout(function(){
              getJson('/search?q=' + encodeURIComponent(q))
                .then(function(r){ renderMergeTargets(r.rows || []); })
                .catch(function(){ setStatus(UNREACH, 'err'); });
            }, 250);
          });
          function renderMergeTargets(list){
            var box = el('sheetList'); box.innerHTML = '';
            list.forEach(function(t){
              if (!mergeFrom || t.key === mergeFrom.key || t.key.charAt(0) === '#') return;
              var row = document.createElement('div'); row.className = 'row'; row.style.cursor = 'pointer';
              var logo = document.createElement('img'); logo.className = 'logo'; logo.alt = '';
              if (t.logo) logo.src = t.logo; else logo.style.visibility = 'hidden';
              logo.onerror = function(){ logo.style.visibility = 'hidden'; };
              var mid = document.createElement('div'); mid.className = 'rowmeta';
              var n = document.createElement('span'); n.className = 'name'; n.textContent = t.name;
              var sub = document.createElement('div'); sub.className = 'sub'; sub.textContent = (t.qualities || []).join(' · ') || ' ';
              mid.appendChild(n); mid.appendChild(sub);
              row.appendChild(logo); row.appendChild(mid);
              row.onclick = function(){ mergeChannels(mergeFrom, t); };
              box.appendChild(row);
            });
            if (!box.childNodes.length) {
              var d = document.createElement('div'); d.className = 'empty'; d.textContent = 'Type at least 2 letters to search every channel.';
              box.appendChild(d);
            }
          }
          function mergeChannels(from, into){
            if (from.key.charAt(0) === '#') { setStatus("This channel can't be merged.", 'err'); return; }
            if (!confirm('Merge “' + from.name + '” into “' + into.name + '”? They become one channel called “' + into.name + '”.')) return;
            el('sheet').hidden = true;
            setStatus('Merging…');
            post('/merge', { from: from.key, into: into.key, fromName: from.name, intoName: into.name })
              .then(function(){ setStatus('Merged', 'ok'); if (currentGroup) reloadRows(); })
              .catch(function(){ setStatus(UNREACH, 'err'); });
          }

          // ---- Duplicate suggestions and merges made -----------------------------------------
          var dupsLoaded = false;
          el('dupCard').addEventListener('toggle', function(){ if (el('dupCard').open && !dupsLoaded) loadDuplicates(); });
          async function loadDuplicates(){
            var list = el('dupList');
            list.innerHTML = '<div class="empty">Looking for duplicates…</div>';
            try {
              var d = await getJson('/duplicates');
              dupsLoaded = true;
              renderDuplicates(d);
            } catch (e) { list.innerHTML = ''; setStatus(UNREACH, 'err'); }
          }
          function renderDuplicates(d){
            var list = el('dupList'); list.innerHTML = '';
            if (!d.suggestions.length) {
              var none = document.createElement('div'); none.className = 'hint'; none.textContent = 'No likely duplicates right now.';
              list.appendChild(none);
            }
            d.suggestions.forEach(function(s){
              var line = document.createElement('div'); line.className = 'dup';
              var what = document.createElement('div'); what.className = 'what';
              what.textContent = s.fromName + '  →  ' + s.intoName;
              line.appendChild(what);
              line.appendChild(mini('Merge', true, function(){
                setStatus('Merging…');
                post('/merge', { from: s.from, into: s.into, fromName: s.fromName, intoName: s.intoName })
                  .then(function(){ setStatus('Merged', 'ok'); loadDuplicates(); })
                  .catch(function(){ setStatus(UNREACH, 'err'); });
              }));
              line.appendChild(mini('Ignore', false, function(){
                post('/merge', { ignore: s.pairId })
                  .then(function(){ line.remove(); setStatus('Ignored', 'ok'); })
                  .catch(function(){ setStatus(UNREACH, 'err'); });
              }));
              list.appendChild(line);
            });
            var merged = el('mergedList'); merged.innerHTML = '';
            el('mergedHead').hidden = !d.merges.length;
            d.merges.forEach(function(m){
              var line = document.createElement('div'); line.className = 'dup';
              var what = document.createElement('div'); what.className = 'what';
              what.textContent = m.fromName + '  →  ' + m.intoName;
              line.appendChild(what);
              line.appendChild(mini('Separate', false, function(){
                setStatus('Separating…');
                post('/merge', { from: m.from })
                  .then(function(){ setStatus('Separated', 'ok'); loadDuplicates(); })
                  .catch(function(){ setStatus(UNREACH, 'err'); });
              }));
              merged.appendChild(line);
            });
          }

          // ---- Recording / NAS settings ------------------------------------------------------
          async function loadRecording(){
            try {
              var r = await getJson('/recording');
              el('recHost').value = r.host || '';
              el('recShare').value = r.share || '';
              el('recFolder').value = r.folder || '';
              el('recUser').value = r.user || '';
              el('recPass').placeholder = r.hasPassword ? '•••• (unchanged)' : '';
              if (r.target === 'SMB') el('recCard').open = true;
            } catch (e) { /* leave the form blank if the TV can't be reached yet */ }
          }
          function smbBody(){
            return {
              host: el('recHost').value.trim(),
              share: el('recShare').value.trim(),
              folder: el('recFolder').value.trim(),
              user: el('recUser').value.trim(),
              password: el('recPass').value
            };
          }
          function showTest(r){
            if (r && r.ok) setStatus('Connected to the NAS — recordings will save there. ✓', 'ok');
            else setStatus("Couldn't reach the NAS: " + ((r && r.error) || 'check the details') , 'err');
          }
          el('recSave').onclick = function(){
            var body = smbBody();
            if (!body.host || !body.share) { setStatus('Server and share are required.', 'err'); return; }
            setStatus('Saving & testing…');
            post('/recording', body)
              .then(function(){
                el('recPass').value = '';
                el('recPass').placeholder = '•••• (unchanged)';
                return post('/recording/test', {});   // test the settings we just stored
              })
              .then(showTest)
              .catch(function(){ setStatus(UNREACH, 'err'); });
          };
          el('recTest').onclick = function(){
            setStatus('Testing…');
            post('/recording/test', smbBody())
              .then(showTest)
              .catch(function(){ setStatus(UNREACH, 'err'); });
          };

          // ---- Add a provider ----------------------------------------------------------------
          function provType(){ return el('provKind').value; }
          function syncProvFields(){
            var k = provType();
            el('provUserField').hidden = (k !== 'xtream');
            el('provPassField').hidden = (k !== 'xtream');
            el('provMacField').hidden = (k !== 'stalker');
            el('provUrlLabel').textContent = (k === 'm3u') ? 'Playlist URL' : (k === 'stalker' ? 'Portal URL' : 'Server address');
            el('provUrl').placeholder = (k === 'm3u') ? 'http://example.com/playlist.m3u'
              : (k === 'stalker' ? 'http://portal.example.com:80/c' : 'http://example.com:8080');
          }
          el('provKind').addEventListener('change', syncProvFields);
          syncProvFields();

          function provBody(){
            return { kind: provType(), name: el('provName').value.trim(), url: el('provUrl').value.trim(),
                     username: el('provUser').value.trim(), password: el('provPass').value, mac: el('provMac').value.trim() };
          }
          function provInvalid(b){
            if (!b.url) return 'Enter the URL.';
            if (b.kind === 'xtream' && (!b.username || !b.password)) return 'Username and password are required.';
            if (b.kind === 'stalker' && !b.mac) return 'A MAC address is required.';
            return null;
          }
          el('provTest').onclick = function(){
            var b = provBody(); var bad = provInvalid(b); if (bad){ setStatus(bad, 'err'); return; }
            setStatus('Testing…');
            post('/source/test', b)
              .then(function(r){ if (r && r.ok) setStatus(r.message || 'Looks good.', 'ok'); else setStatus((r && r.error) || 'Test failed', 'err'); })
              .catch(function(){ setStatus(UNREACH, 'err'); });
          };
          el('provSave').onclick = function(){
            var b = provBody(); var bad = provInvalid(b); if (bad){ setStatus(bad, 'err'); return; }
            setStatus('Adding & loading channels…');
            post('/source', b)
              .then(function(r){
                if (r && r.ok){
                  setStatus('Added — ' + (r.channels || 0) + ' channels loaded.', 'ok');
                  el('provUrl').value = ''; el('provUser').value = ''; el('provPass').value = '';
                  el('provMac').value = ''; el('provName').value = '';
                  loadGroups();
                } else setStatus((r && r.error) || 'Could not add the provider', 'err');
              })
              .catch(function(){ setStatus(UNREACH, 'err'); });
          };

          // ---- Backup & restore -------------------------------------------------------------
          var bakText = null;
          el('bakDownload').onclick = function(){ location.href = withToken('/backup'); };
          el('bakFile').addEventListener('change', function(){
            var f = el('bakFile').files[0];
            el('bakChoice').hidden = true; bakText = null;
            if (!f) return;
            f.text().then(function(text){
              var d = null;
              try { d = JSON.parse(text); } catch (e) {}
              if (!d || d.format !== 'opentv-settings') { setStatus("This isn't an OpenTV settings file.", 'err'); return; }
              bakText = text;
              var n = (d.merges || []).length, ig = (d.ignoredPairs || []).length;
              var when = d.exportedAt ? new Date(d.exportedAt).toLocaleString() : '';
              el('bakInfo').textContent = 'Saved ' + when + (d.app ? ' from OpenTV ' + d.app : '') + '.';
              el('bakMergesLabel').textContent = 'Channel merges (' + n + ' merged, ' + ig + ' ignored) — added to the ones on the TV';
              el('bakSettings').disabled = !d.settings;
              el('bakSettings').checked = !!d.settings;
              el('bakChoice').hidden = false;
            });
          });
          el('bakRestore').onclick = function(){
            if (!bakText) return;
            var m = el('bakMerges').checked, st = el('bakSettings').checked;
            if (!m && !st) { setStatus('Tick at least one.', 'err'); return; }
            setStatus('Importing…');
            fetch(withToken('/restore?merges=' + (m ? 1 : 0) + '&settings=' + (st ? 1 : 0)), {
              method: 'POST', headers: { 'Content-Type': 'application/json' }, body: bakText
            })
              .then(function(r){ if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
              .then(function(r){
                if (!r || !r.ok) { setStatus((r && r.error) || 'Import failed', 'err'); return; }
                var parts = [];
                if (r.settings) parts.push('settings restored');
                if (m) parts.push(r.merges + ' new merges');
                if (r.guides) parts.push(r.guides + ' guide feeds');
                var msg = 'Done — ' + parts.join(', ') + '. The TV is regrouping channels.';
                if (r.languageChanged) msg += ' The language changes next time OpenTV starts.';
                setStatus(msg, 'ok');
                el('bakChoice').hidden = true; el('bakFile').value = ''; bakText = null;
              })
              .catch(function(){ setStatus(UNREACH, 'err'); });
          };

          loadGroups();
          loadRecording();
        </script>
        </body>
        </html>
    """.trimIndent()
}
