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
          .handle { flex:none; width:34px; height:40px; display:flex; align-items:center; justify-content:center;
                    font-size:20px; color:var(--muted); cursor:grab; touch-action:none; user-select:none; -webkit-user-select:none; }
          .dragging { border-color:var(--gold); box-shadow:0 6px 18px rgba(0,0,0,.5), 0 0 0 1px var(--gold) inset; position:relative; z-index:4; }
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

            <details class="rec" id="logoCard">
              <summary>Logos</summary>
              <div class="body">
                <label class="field"><span>Channel logos</span>
                  <select id="logoSource">
                    <option value="PROVIDER">Provider only</option>
                    <option value="PROVIDER_THEN_EXTERNAL">Provider, then outside logos</option>
                    <option value="EXTERNAL_THEN_PROVIDER">Outside logos, then provider</option>
                  </select></label>
                <p class="hint">Outside logos are your logo pack, then the free TV guides' ones. A logo picked by hand for a channel always wins.</p>
                <label class="check"><input type="checkbox" id="logoGroups"> <span>Group logos from outside</span></label>
                <p class="hint">Broadcaster logos and flags beside the groups. Off, or when none is found, the provider's emoji shows instead.</p>
                <div id="logoPackBox">
                  <label class="field"><span>Logo pack address</span>
                    <input id="logoUrl" type="url" placeholder="https://…/index.json" autocapitalize="off" autocorrect="off" spellcheck="false"></label>
                  <p class="hint">An index.json listing your own logos (team crests, say), for the channels it names.</p>
                  <button class="save" id="logoSave">Save &amp; load</button>
                  <p class="hint" id="logoInfo"></p>
                </div>
              </div>
            </details>

            <details class="rec" id="bakCard">
              <summary>Backup &amp; restore settings</summary>
              <div class="body">
                <p class="hint">One file with your settings, guide feeds, channel merges and order. Your provider login, NAS password and TMDB key are never put in it.</p>
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
              <p class="hint" id="catHint">Groups in the order the TV shows them. Drag ≡ to move a group.</p>
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
              <p class="hint" id="chanHint">Tap a name to rename. Star to favourite, eye to hide, drag ≡ to move, ⋯ to merge.</p>
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
          // A message without a kind ("Merging…") is work in progress: it stays up until the answer replaces it.
          function setStatus(msg, kind){
            var s = el('status');
            s.textContent = t(msg);
            s.className = 'show ' + (kind || '');
            if (statusTimer) clearTimeout(statusTimer);
            statusTimer = kind ? setTimeout(function(){ s.className = kind; }, 2600) : null;
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

          // ---- Language ----------------------------------------------------------------------
          // English is written in place; a phone set to Italian gets these. Static text in the page is
          // swapped once at load ([translatePage]); text built later goes through t().
          var IT = {
            "OpenTV — manage channels": "OpenTV — gestisci i canali",
            "Changes apply to your TV the moment you make them.": "Le modifiche arrivano subito sulla TV.",
            "Add a provider": "Aggiungi un fornitore",
            "Add an Xtream, M3U or Stalker portal from here — a real keyboard beats the TV remote. Your login stays on the TV.": "Aggiungi qui un fornitore Xtream, M3U o Stalker: una tastiera vera batte il telecomando. I dati di accesso restano sulla TV.",
            "Type": "Tipo",
            "Xtream Codes login": "Accesso Xtream Codes",
            "M3U playlist URL": "Indirizzo playlist M3U",
            "Stalker portal": "Portale Stalker",
            "Name (optional)": "Nome (facoltativo)",
            "My provider": "Il mio fornitore",
            "Server address": "Indirizzo del server",
            "Playlist URL": "Indirizzo della playlist",
            "Portal URL": "Indirizzo del portale",
            "Username": "Nome utente",
            "MAC address": "Indirizzo MAC",
            "Add & load channels": "Aggiungi e carica i canali",
            "Test": "Prova",
            "Recording to NAS (SMB)": "Registrazioni su NAS (SMB)",
            "Logos": "Loghi",
            "Channel logos": "Loghi dei canali",
            "Provider only": "Solo fornitore",
            "Provider, then outside logos": "Fornitore, poi loghi esterni",
            "Outside logos, then provider": "Loghi esterni, poi fornitore",
            "Outside logos are your logo pack, then the free TV guides' ones. A logo picked by hand for a channel always wins.": "I loghi esterni sono il tuo pacchetto loghi, poi quelli delle guide TV gratuite. Il logo scelto a mano per un canale vince sempre.",
            "Group logos from outside": "Loghi dei gruppi esterni",
            "Broadcaster logos and flags beside the groups. Off, or when none is found, the provider's emoji shows instead.": "Loghi delle emittenti e bandiere accanto ai gruppi. Spento, o se non ne trova, resta l'emoji del fornitore.",
            "Logo pack address": "Indirizzo del pacchetto loghi",
            "An index.json listing your own logos (team crests, say), for the channels it names.": "Un index.json con i tuoi loghi (per esempio gli stemmi delle squadre), per i canali che nomina.",
            "Save & load": "Salva e carica",
            "{0} names in the pack": "{0} nomi nel pacchetto",
            "Could not load the pack: {0}": "Pacchetto non caricato: {0}",
            "Loading…": "Caricamento…",
            "Saved ✓": "Salvato ✓",
            "Point recordings at a network drive. Saved straight to your TV — the password stays on the TV.": "Salva le registrazioni su un disco di rete. Si imposta direttamente sulla TV e la password resta lì.",
            "Server (IP address)": "Server (indirizzo IP)",
            "Share": "Cartella condivisa",
            "Folder (optional)": "Sottocartella (facoltativa)",
            "(leave blank)": "(lascia vuoto)",
            "Save & use NAS": "Salva e usa il NAS",
            "Test connection": "Prova la connessione",
            "Backup & restore settings": "Backup e ripristino impostazioni",
            "One file with your settings, guide feeds, channel merges and order. Your provider login, NAS password and TMDB key are never put in it.": "Un unico file con impostazioni, guide TV, unioni e ordine dei canali. Accesso del fornitore, password del NAS e chiave TMDB non ci finiscono mai.",
            "Download settings": "Scarica le impostazioni",
            "Restore from a file": "Ripristina da un file",
            "Channel merges": "Unioni dei canali",
            "Settings (playback, quality, look, categories, guide feeds…)": "Impostazioni (riproduzione, qualità, aspetto, categorie, ordine, guide TV…)",
            "Import": "Importa",
            "Duplicate channels": "Canali doppi",
            "Channels that look like the same one under two names. Merge makes them one row on the TV; it survives refreshes and goes in the backup.": "Canali che sembrano lo stesso con due nomi. Unisci li fa diventare una riga sola sulla TV; resta dopo gli aggiornamenti e va nel backup.",
            "Merged channels": "Canali uniti",
            "Filter groups…": "Cerca un gruppo…",
            "Groups in the order the TV shows them. Drag ≡ to move a group.": "I gruppi nell'ordine della TV. Trascina ≡ per spostarli.",
            "Switched-off groups": "Gruppi spenti",
            "Filter…": "Cerca…",
            "‹ Groups": "‹ Gruppi",
            "Tap a name to rename. Star to favourite, eye to hide, drag ≡ to move, ⋯ to merge.": "Tocca il nome per rinominare. Stella = preferito, occhio = nascondi, trascina ≡ per spostare, ⋯ per unire.",
            "Close": "Chiudi",
            "Search every channel…": "Cerca tra tutti i canali…",
            "This page is served by your television, over your own network — there is no OpenTV account and no OpenTV server. Nothing you change here leaves your home.": "Questa pagina arriva dalla tua TV, sulla tua rete: niente account né server OpenTV. Quello che cambi qui non esce di casa.",
            "Couldn't reach the TV — still on the same wifi?": "Non raggiungo la TV: sei ancora sulla stessa rete wifi?",
            "Drag to move": "Trascina per spostare",
            "No channels yet — add a provider above to get started.": "Ancora nessun canale: aggiungi un fornitore qui sopra.",
            "Favourites": "Preferiti",
            "No groups match that.": "Nessun gruppo trovato.",
            "Group order saved": "Ordine dei gruppi salvato",
            "Switched-off groups ({0})": "Gruppi spenti ({0})",
            "Turn on": "Accendi",
            "Turned on — the TV is reloading its channels, back in a minute.": "Acceso: la TV ricarica i canali, torna tra un minuto.",
            "Switch group off": "Spegni il gruppo",
            "Switch off “{0}”? Its channels leave the TV until you turn it back on (at the bottom of the groups).": "Spegnere “{0}”? I suoi canali spariscono dalla TV finché non lo riaccendi (in fondo ai gruppi).",
            "Group switched off": "Gruppo spento",
            "Merge this group into…": "Unisci questo gruppo a…",
            "Merge “{0}” into “{1}”? Its channels show under “{1}”.": "Unire “{0}” a “{1}”? I suoi canali compariranno in “{1}”.",
            "Merged into {0}": "Unito a {0}",
            "Includes “{0}”": "Contiene “{0}”",
            "Separate": "Separa",
            "Separated": "Separato",
            "No channels in this group.": "Nessun canale in questo gruppo.",
            "Hidden": "Nascosto",
            "Favourite": "Preferito",
            "Favourited": "Aggiunto ai preferiti",
            "Unfavourited": "Tolto dai preferiti",
            "Hidden — tap to show": "Nascosto: tocca per mostrarlo",
            "Showing — tap to hide": "Visibile: tocca per nasconderlo",
            "Hidden from the TV": "Nascosto dalla TV",
            "Showing on the TV": "Di nuovo visibile sulla TV",
            "Merge with…": "Unisci a…",
            "Renamed": "Rinominato",
            "Order saved": "Ordine salvato",
            "Merge “{0}” with…": "Unisci “{0}” a…",
            "Type at least 2 letters to search every channel.": "Scrivi almeno 2 lettere per cercare tra tutti i canali.",
            "This channel can't be merged.": "Questo canale non si può unire.",
            "Merge “{0}” into “{1}”? They become one channel called “{1}”.": "Unire “{0}” a “{1}”? Diventano un solo canale chiamato “{1}”.",
            "Merging… (takes a few seconds)": "Unisco… (qualche secondo)",
            "Merged": "Uniti",
            "Looking for duplicates…": "Cerco i doppioni…",
            "No likely duplicates right now.": "Nessun doppione probabile per ora.",
            "Merge": "Unisci",
            "Ignore": "Ignora",
            "Ignored": "Ignorato",
            "Separating…": "Separo…",
            "Connected to the NAS — recordings will save there. ✓": "Collegato al NAS: le registrazioni andranno lì. ✓",
            "Couldn't reach the NAS: {0}": "Non raggiungo il NAS: {0}",
            "check the details": "controlla i dati",
            "Server and share are required.": "Server e cartella condivisa sono obbligatori.",
            "Saving & testing…": "Salvo e provo…",
            "Testing…": "Provo…",
            "Enter the URL.": "Inserisci l'indirizzo.",
            "Username and password are required.": "Nome utente e password sono obbligatori.",
            "A MAC address is required.": "Serve l'indirizzo MAC.",
            "Looks good.": "Tutto ok.",
            "Test failed": "Prova non riuscita",
            "Adding & loading channels…": "Aggiungo e carico i canali…",
            "Added — {0} channels loaded.": "Aggiunto: {0} canali caricati.",
            "Could not add the provider": "Non sono riuscito ad aggiungere il fornitore",
            "This isn't an OpenTV settings file.": "Questo non è un file di impostazioni OpenTV.",
            "Saved {0}.": "Salvato il {0}.",
            "Saved {0} from OpenTV {1}.": "Salvato il {0} da OpenTV {1}.",
            "Channel merges ({0} merged, {1} ignored) — added to the ones on the TV": "Unioni dei canali ({0} unite, {1} ignorate): si aggiungono a quelle della TV",
            "Tick at least one.": "Spunta almeno una voce.",
            "Importing…": "Importo…",
            "Import failed": "Importazione non riuscita",
            "settings restored": "impostazioni ripristinate",
            "{0} new merges": "{0} unioni nuove",
            "{0} guide feeds": "{0} guide TV",
            "Done — {0}. The TV is regrouping channels.": "Fatto: {0}. La TV sta raggruppando i canali.",
            "The language changes next time OpenTV starts.": "La lingua cambia al prossimo avvio di OpenTV.",
            "Nothing selected to import.": "Niente da importare selezionato.",
            "Fill in the required fields for this provider type.": "Compila i campi obbligatori per questo tipo di fornitore.",
            "Could not save the source.": "Non sono riuscito a salvare il fornitore.",
            "Test failed.": "Prova non riuscita.",
            "Connection failed": "Connessione non riuscita"
          };
          var ITALIAN = (navigator.language || '').toLowerCase().indexOf('it') === 0;
          function t(s){
            var out = (ITALIAN && IT[s]) || s;
            for (var i = 1; i < arguments.length; i++) out = out.split('{' + (i - 1) + '}').join(String(arguments[i]));
            return out;
          }
          // Text written in the page itself: leaf elements, placeholders and options, matched with
          // runs of spaces collapsed (the HTML wraps long lines).
          function translatePage(){
            if (!ITALIAN) return;
            document.documentElement.lang = 'it';
            var nodes = document.querySelectorAll('body *');
            for (var i = 0; i < nodes.length; i++) {
              var n = nodes[i];
              if (n.tagName === 'SCRIPT' || n.tagName === 'STYLE') continue;
              if (n.placeholder) n.placeholder = t(n.placeholder);
              for (var c = n.firstChild; c; c = c.nextSibling) {
                if (c.nodeType !== 3) continue;
                var key = c.nodeValue.replace(/\s+/g, ' ').trim();
                if (key && IT[key]) c.nodeValue = c.nodeValue.replace(/\S[\s\S]*\S|\S/, IT[key]);
              }
            }
            document.title = t('OpenTV — manage channels');
          }
          translatePage();


          // ---- Groups ------------------------------------------------------------------------
          var data = { groups: [], off: [], merges: [], favourites: 0 };
          var FAV = 'fav';
          var currentGroup = null;    // the group being viewed: {key,label,…} or the favourites pseudo-group
          var rows = [];              // its channels, in display order

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

          // ---- Drag to reorder ----------------------------------------------------------------
          // The ≡ handle: press and drag the row up or down; the list scrolls by itself near the
          // screen edges. Only rows marked .dragrow move; the list's other rows stay where they are.
          var drag = null;
          function handle(listEl, onDrop){
            var h = document.createElement('div');
            h.className = 'handle'; h.innerHTML = '&#9776;'; h.title = t('Drag to move');
            h.onclick = function(e){ e.stopPropagation(); };
            h.addEventListener('pointerdown', function(e){
              e.preventDefault(); e.stopPropagation();
              var row = h.closest('.dragrow');
              var all = Array.prototype.slice.call(listEl.querySelectorAll('.dragrow'));
              drag = { list: listEl, row: row, from: all.indexOf(row), y: e.clientY, onDrop: onDrop, raf: 0 };
              row.classList.add('dragging');
              drag.raf = requestAnimationFrame(autoScroll);
            });
            return h;
          }
          // Moving the row in the page drops pointer capture, so the drag is followed on the document.
          document.addEventListener('pointermove', function(e){ if (drag) { drag.y = e.clientY; follow(); } });
          function endDrag(){
            if (!drag) return;
            var d = drag; drag = null;
            cancelAnimationFrame(d.raf);
            d.row.classList.remove('dragging');
            var to = Array.prototype.slice.call(d.list.querySelectorAll('.dragrow')).indexOf(d.row);
            if (to !== d.from && to >= 0) d.onDrop(d.from, to);
          }
          document.addEventListener('pointerup', endDrag);
          document.addEventListener('pointercancel', endDrag);
          // No page scroll under the finger while a row is held (the edges scroll on their own).
          document.addEventListener('touchmove', function(e){ if (drag) e.preventDefault(); }, { passive: false });

          // Put the dragged row where the finger is: before the first row whose middle is below it.
          function follow(){
            var rowsNow = Array.prototype.slice.call(drag.list.querySelectorAll('.dragrow'));
            var before = null;
            for (var i = 0; i < rowsNow.length; i++) {
              var r = rowsNow[i];
              if (r === drag.row) continue;
              var box = r.getBoundingClientRect();
              if (drag.y < box.top + box.height / 2) { before = r; break; }
            }
            if (before) { if (drag.row.nextSibling !== before) drag.list.insertBefore(drag.row, before); }
            else {
              var last = rowsNow[rowsNow.length - 1];
              if (last !== drag.row) last.after(drag.row);
            }
          }
          function autoScroll(){
            if (!drag) return;
            var edge = 90, step = 0;
            if (drag.y < edge) step = -Math.ceil((edge - drag.y) / 6);
            else if (drag.y > window.innerHeight - edge) step = Math.ceil((drag.y - (window.innerHeight - edge)) / 6);
            if (step) { window.scrollBy(0, step); follow(); }
            drag.raf = requestAnimationFrame(autoScroll);
          }

          async function loadGroups(){
            try {
              data = await getJson('/groups');
              renderGroups();
              renderOff();
            } catch (e) {
              el('catList').innerHTML = '';
              var d = el('catEmpty'); d.hidden = false; d.textContent = t(UNREACH);
            }
          }

          function renderGroups(){
            var q = el('catSearch').value.trim().toLowerCase();
            var list = el('catList');
            list.innerHTML = '';
            var empty = el('catEmpty');
            if (!data.groups.length && !data.favourites) {
              empty.hidden = false; empty.textContent = t('No channels yet — add a provider above to get started.');
              return;
            }
            empty.hidden = true;
            if (data.favourites && !q) {
              list.appendChild(groupRow({ key: FAV, label: '★ ' + t('Favourites'), count: data.favourites }, -1, false));
            }
            var shown = 0;
            data.groups.forEach(function(g, i){
              if (q && g.label.toLowerCase().indexOf(q) < 0 && g.raw.toLowerCase().indexOf(q) < 0) return;
              // Dragging only on the full list: a filtered one has gaps the new order couldn't place.
              list.appendChild(groupRow(g, i, !q));
              shown++;
            });
            if (!shown && q) { empty.hidden = false; empty.textContent = t('No groups match that.'); }
          }

          function groupRow(g, i, draggable){
            var row = document.createElement('div');
            row.className = 'cat' + (draggable ? ' dragrow' : '');
            if (draggable) row.appendChild(handle(el('catList'), moveGroup));
            var label = document.createElement('span'); label.className = 'label'; label.textContent = g.label;
            var count = document.createElement('span'); count.className = 'count'; count.textContent = g.count;
            var chev = document.createElement('span'); chev.className = 'chev'; chev.innerHTML = '&#8250;';
            row.appendChild(label); row.appendChild(count); row.appendChild(chev);
            row.onclick = function(){ openGroup(g); };
            return row;
          }
          el('catSearch').addEventListener('input', renderGroups);

          function moveGroup(from, to){
            if (to < 0 || to >= data.groups.length || from === to) return;
            var item = data.groups.splice(from, 1)[0];
            data.groups.splice(to, 0, item);
            renderGroups();
            post('/groups/order', { keys: data.groups.map(function(g){ return g.key; }) })
              .then(function(){ setStatus(t('Group order saved'), 'ok'); })
              .catch(function(){ setStatus(t(UNREACH), 'err'); });
          }

          function renderOff(){
            var card = el('offCard');
            card.hidden = !data.off.length;
            el('offHead').textContent = t('Switched-off groups ({0})', data.off.length);
            var q = el('offSearch').value.trim().toLowerCase();
            var list = el('offList'); list.innerHTML = '';
            data.off.forEach(function(g){
              if (q && g.label.toLowerCase().indexOf(q) < 0 && g.raw.toLowerCase().indexOf(q) < 0) return;
              var row = document.createElement('div'); row.className = 'cat off';
              var label = document.createElement('span'); label.className = 'label'; label.textContent = g.raw;
              row.appendChild(label);
              row.appendChild(mini(t('Turn on'), true, function(){
                post('/group/switch', { key: g.key, on: true, memberKeys: g.memberKeys })
                  .then(function(){ setStatus(t('Turned on — the TV is reloading its channels, back in a minute.'), 'ok'); setTimeout(loadGroups, 1500); })
                  .catch(function(){ setStatus(t(UNREACH), 'err'); });
              }));
              list.appendChild(row);
            });
          }
          el('offSearch').addEventListener('input', renderOff);

          // ---- One group's channels -----------------------------------------------------------
          async function openGroup(g){
            currentGroup = g;
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
              empty.hidden = false; empty.textContent = t(UNREACH);
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
            var hide = document.createElement('button'); hide.className = 'save'; hide.textContent = t('Switch group off');
            hide.style.background = 'var(--surface2)'; hide.style.borderColor = 'var(--line)';
            hide.onclick = function(){
              if (!confirm(t('Switch off “{0}”? Its channels leave the TV until you turn it back on (at the bottom of the groups).', g.label))) return;
              post('/group/switch', { key: g.key, on: false, memberKeys: g.memberKeys })
                .then(function(){ setStatus(t('Group switched off'), 'ok'); el('back').onclick(); })
                .catch(function(){ setStatus(t(UNREACH), 'err'); });
            };
            box.appendChild(hide);
            var sel = document.createElement('select');
            var first = document.createElement('option'); first.value = ''; first.textContent = t('Merge this group into…');
            sel.appendChild(first);
            data.groups.forEach(function(o){
              if (o.key === g.key) return;
              var opt = document.createElement('option'); opt.value = o.key; opt.textContent = o.label; sel.appendChild(opt);
            });
            sel.onchange = function(){
              var into = data.groups.filter(function(o){ return o.key === sel.value; })[0];
              if (!into) return;
              if (!confirm(t('Merge “{0}” into “{1}”? Its channels show under “{1}”.', g.label, into.label))) { sel.value = ''; return; }
              post('/group/merge', { from: g.key, into: into.key })
                .then(function(){ setStatus(t('Merged into {0}', into.label), 'ok'); el('back').onclick(); })
                .catch(function(){ setStatus(t(UNREACH), 'err'); });
            };
            box.appendChild(sel);
            // Groups merged into this one, each separable again.
            (data.merges || []).forEach(function(m){
              if (g.memberKeys.indexOf(m.into) < 0) return;
              var line = document.createElement('div'); line.className = 'incl';
              var s = document.createElement('span'); s.textContent = t('Includes “{0}”', m.fromLabel); s.style.flex = '1';
              line.appendChild(s);
              line.appendChild(mini(t('Separate'), false, function(){
                post('/group/merge', { from: m.from })
                  .then(function(){ setStatus(t('Separated'), 'ok'); el('back').onclick(); })
                  .catch(function(){ setStatus(t(UNREACH), 'err'); });
              }));
              box.appendChild(line);
            });
          }

          function renderRows(){
            var listEl = el('chanList');
            listEl.innerHTML = '';
            var empty = el('chanEmpty');
            if (!rows.length) { empty.hidden = false; empty.textContent = t('No channels in this group.'); return; }
            empty.hidden = true;
            rows.forEach(function(c){ listEl.appendChild(buildRow(c)); });
          }

          function patchRow(c, body, okMsg, after){
            body.ids = c.ids;
            post('/row', body)
              .then(function(){ if (after) after(); setStatus(okMsg, 'ok'); })
              .catch(function(){ setStatus(t(UNREACH), 'err'); });
          }

          function buildRow(c){
            var row = document.createElement('div');
            row.className = 'row dragrow' + (c.hidden ? ' hidden' : '');

            var logo = document.createElement('img');
            logo.className = 'logo'; logo.alt = '';
            if (c.logo) { logo.src = c.logo; } else { logo.style.visibility = 'hidden'; }
            logo.onerror = function(){ logo.style.visibility = 'hidden'; };

            var mid = document.createElement('div'); mid.className = 'rowmeta';
            var name = document.createElement('span'); name.className = 'name'; name.textContent = c.name;
            name.onclick = function(e){ e.stopPropagation(); startRename(name, c); };
            var sub = document.createElement('div'); sub.className = 'sub';
            (c.qualities || []).forEach(function(q){
              var b = document.createElement('span'); b.className = 'q'; b.textContent = q; sub.appendChild(b);
            });
            if (c.hidden) { var h = document.createElement('span'); h.className = 'badge'; h.textContent = t('Hidden'); sub.appendChild(h); }
            if (!sub.childNodes.length) sub.textContent = c.renamed ? c.original : ' ';
            mid.appendChild(name); mid.appendChild(sub);

            var ctrls = document.createElement('div'); ctrls.className = 'ctrls';
            var star = btn('star' + (c.favourite ? ' on' : ''), c.favourite ? '&#9733;' : '&#9734;', t('Favourite'), function(){
              var target = !c.favourite;
              patchRow(c, { favourite: target }, t(target ? 'Favourited' : 'Unfavourited'), function(){
                c.favourite = target;
                if (currentGroup.key === FAV && !target) { rows.splice(rows.indexOf(c), 1); }
                renderRows();
              });
            });
            var eye = btn('eye' + (c.hidden ? '' : ' on'), c.hidden ? '&#128584;' : '&#128065;', t(c.hidden ? 'Hidden — tap to show' : 'Showing — tap to hide'), function(){
              var target = !c.hidden;
              patchRow(c, { hidden: target }, t(target ? 'Hidden from the TV' : 'Showing on the TV'), function(){ c.hidden = target; renderRows(); });
            });
            ctrls.appendChild(star); ctrls.appendChild(eye);
            if (currentGroup.key !== FAV) ctrls.appendChild(btn('mv', '&#8943;', t('Merge with…'), function(){ openMerge(c); }));
            row.appendChild(handle(el('chanList'), moveRow));
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
                patchRow(c, { name: val }, t('Renamed'), function(){ c.name = val || c.original; c.renamed = !!val; renderRows(); });
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
              .then(function(){ setStatus(t('Order saved'), 'ok'); })
              .catch(function(){ setStatus(t(UNREACH), 'err'); });
          }

          // ---- Merge two channels -------------------------------------------------------------
          var mergeFrom = null;
          var searchTimer = null;
          function openMerge(c){
            mergeFrom = c;
            el('sheetTitle').textContent = t('Merge “{0}” with…', c.name);
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
                .catch(function(){ setStatus(t(UNREACH), 'err'); });
            }, 250);
          });
          function renderMergeTargets(list){
            var box = el('sheetList'); box.innerHTML = '';
            list.forEach(function(x){
              if (!mergeFrom || x.key === mergeFrom.key || x.key.charAt(0) === '#') return;
              var row = document.createElement('div'); row.className = 'row'; row.style.cursor = 'pointer';
              var logo = document.createElement('img'); logo.className = 'logo'; logo.alt = '';
              if (x.logo) logo.src = x.logo; else logo.style.visibility = 'hidden';
              logo.onerror = function(){ logo.style.visibility = 'hidden'; };
              var mid = document.createElement('div'); mid.className = 'rowmeta';
              var n = document.createElement('span'); n.className = 'name'; n.textContent = x.name;
              var sub = document.createElement('div'); sub.className = 'sub'; sub.textContent = (x.qualities || []).join(' · ') || ' ';
              mid.appendChild(n); mid.appendChild(sub);
              row.appendChild(logo); row.appendChild(mid);
              row.onclick = function(){ mergeChannels(mergeFrom, x); };
              box.appendChild(row);
            });
            if (!box.childNodes.length) {
              var d = document.createElement('div'); d.className = 'empty'; d.textContent = t('Type at least 2 letters to search every channel.');
              box.appendChild(d);
            }
          }
          function mergeChannels(from, into){
            if (from.key.charAt(0) === '#') { setStatus(t("This channel can't be merged."), 'err'); return; }
            if (!confirm(t('Merge “{0}” into “{1}”? They become one channel called “{1}”.', from.name, into.name))) return;
            el('sheet').hidden = true;
            setStatus(t('Merging… (takes a few seconds)'));
            post('/merge', { from: from.key, into: into.key, fromName: from.name, intoName: into.name })
              .then(function(){ setStatus(t('Merged'), 'ok'); if (currentGroup) reloadRows(); })
              .catch(function(){ setStatus(t(UNREACH), 'err'); });
          }

          // ---- Duplicate suggestions and merges made -----------------------------------------
          var dupsLoaded = false;
          el('dupCard').addEventListener('toggle', function(){ if (el('dupCard').open && !dupsLoaded) loadDuplicates(); });
          async function loadDuplicates(){
            var list = el('dupList');
            list.innerHTML = '';
            var wait = document.createElement('div'); wait.className = 'empty'; wait.textContent = t('Looking for duplicates…');
            list.appendChild(wait);
            try {
              var d = await getJson('/duplicates');
              dupsLoaded = true;
              renderDuplicates(d);
            } catch (e) { list.innerHTML = ''; setStatus(t(UNREACH), 'err'); }
          }
          function renderDuplicates(d){
            var list = el('dupList'); list.innerHTML = '';
            if (!d.suggestions.length) {
              var none = document.createElement('div'); none.className = 'hint'; none.textContent = t('No likely duplicates right now.');
              list.appendChild(none);
            }
            d.suggestions.forEach(function(s){
              var line = document.createElement('div'); line.className = 'dup';
              var what = document.createElement('div'); what.className = 'what';
              what.textContent = s.fromName + '  →  ' + s.intoName;
              line.appendChild(what);
              line.appendChild(mini(t('Merge'), true, function(){
                setStatus(t('Merging… (takes a few seconds)'));
                post('/merge', { from: s.from, into: s.into, fromName: s.fromName, intoName: s.intoName })
                  .then(function(){ setStatus(t('Merged'), 'ok'); loadDuplicates(); })
                  .catch(function(){ setStatus(t(UNREACH), 'err'); });
              }));
              line.appendChild(mini(t('Ignore'), false, function(){
                post('/merge', { ignore: s.pairId })
                  .then(function(){ line.remove(); setStatus(t('Ignored'), 'ok'); })
                  .catch(function(){ setStatus(t(UNREACH), 'err'); });
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
              line.appendChild(mini(t('Separate'), false, function(){
                setStatus(t('Separating…'));
                post('/merge', { from: m.from })
                  .then(function(){ setStatus(t('Separated'), 'ok'); loadDuplicates(); })
                  .catch(function(){ setStatus(t(UNREACH), 'err'); });
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
            else setStatus(t("Couldn't reach the NAS: {0}", (r && r.error) || t('check the details')), 'err');
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

          // ---- Logo pack -----------------------------------------------------------------------
          function showLogoPack(r){
            el('logoUrl').value = r.url || '';
            el('logoInfo').textContent = r.url ? t('{0} names in the pack', r.count) : '';
            if (r.source) el('logoSource').value = r.source;
            el('logoGroups').checked = !!r.externalGroups;
            el('logoPackBox').hidden = (el('logoSource').value === 'PROVIDER');
          }
          function saveLogoChoice(body){
            post('/logopack', body)
              .then(function(r){ showLogoPack(r); setStatus('Saved ✓', 'ok'); })
              .catch(function(){ setStatus(UNREACH, 'err'); });
          }
          el('logoSource').onchange = function(){ saveLogoChoice({ source: el('logoSource').value }); };
          el('logoGroups').onchange = function(){ saveLogoChoice({ externalGroups: el('logoGroups').checked }); };
          async function loadLogoPack(){
            try { showLogoPack(await getJson('/logopack')); } catch (e) { /* TV not reachable yet */ }
          }
          el('logoSave').onclick = function(){
            setStatus('Loading…');
            post('/logopack', { url: el('logoUrl').value.trim() })
              .then(function(r){
                showLogoPack(r);
                if (r.error) setStatus(t('Could not load the pack: {0}', r.error), 'err');
                else setStatus('Saved ✓', 'ok');
              })
              .catch(function(){ setStatus(UNREACH, 'err'); });
          };

          // ---- Add a provider ----------------------------------------------------------------
          function provType(){ return el('provKind').value; }
          function syncProvFields(){
            var k = provType();
            el('provUserField').hidden = (k !== 'xtream');
            el('provPassField').hidden = (k !== 'xtream');
            el('provMacField').hidden = (k !== 'stalker');
            el('provUrlLabel').textContent = t((k === 'm3u') ? 'Playlist URL' : (k === 'stalker' ? 'Portal URL' : 'Server address'));
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
                  setStatus(t('Added — {0} channels loaded.', r.channels || 0), 'ok');
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
              el('bakInfo').textContent = d.app ? t('Saved {0} from OpenTV {1}.', when, d.app) : t('Saved {0}.', when);
              el('bakMergesLabel').textContent = t('Channel merges ({0} merged, {1} ignored) — added to the ones on the TV', n, ig);
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
                if (r.settings) parts.push(t('settings restored'));
                if (m) parts.push(t('{0} new merges', r.merges));
                if (r.guides) parts.push(t('{0} guide feeds', r.guides));
                var msg = t('Done — {0}. The TV is regrouping channels.', parts.join(', '));
                if (r.languageChanged) msg += ' ' + t('The language changes next time OpenTV starts.');
                setStatus(msg, 'ok');
                el('bakChoice').hidden = true; el('bakFile').value = ''; bakText = null;
              })
              .catch(function(){ setStatus(UNREACH, 'err'); });
          };

          loadGroups();
          loadRecording();
          loadLogoPack();
        </script>
        </body>
        </html>
    """.trimIndent()
}
